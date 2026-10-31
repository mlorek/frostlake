/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.jdbc;

import dev.frostlake.http.SessionNamespace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Struct;
import java.time.ZoneId;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * JDBC Connection implementation for Frostlake SQL Engine
 */
public class DatabaseConnection extends AbstractFrostlakeConnection implements ResultColumnCase, SessionZoneSource {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);

    private final HttpClient httpClient;
    private final String database;
    private String schema;
    private boolean closed;
    private boolean autoCommit;
    /** The session's MULTI_STATEMENT_COUNT (1 = single statement only, 0 = any). */
    private int multiStatementCount = 1;
    private String catalog;

    public DatabaseConnection(final String baseUrl, final String database, final String schema, final Properties info) throws SQLException {
        final String sessionId = info.getProperty("sessionId");
        this.httpClient = new HttpClient(baseUrl, sessionId);
        // CLIENT_SESSION_KEEP_ALIVE as the account's driver takes it: on, the connection sends a heartbeat every
        // CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY seconds, so its session outlives the server's idle timeout.
        final String keepAlive = property(info, "CLIENT_SESSION_KEEP_ALIVE");
        httpClient.keepAlive(keepAlive != null && "true".equalsIgnoreCase(keepAlive.trim()),
            seconds(property(info, "CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY")));
        this.database = database;
        this.schema = schema;
        this.closed = false;
        this.autoCommit = true;
        final String multiCount = info.getProperty("MULTI_STATEMENT_COUNT");
        if (multiCount != null) {
            try {
                this.multiStatementCount = Integer.parseInt(multiCount.trim());
            } catch (final NumberFormatException ignored) {
                // an unreadable property keeps the single-statement default
            }
        }

        // Verify connection
        if (!httpClient.isHealthy()) {
            throw new SQLException("Cannot connect to server at " + baseUrl);
        }

        // Set initial database and schema
        if (database != null) {
            execute("USE DATABASE " + database);
            this.catalog = database;
        }
        if (schema != null) {
            execute("USE SCHEMA " + schema);
        }

        logger.info("Created connection to {} (session: {})", baseUrl, httpClient.getSessionId());
    }

    /** A connection property by name, in any case, as the account's driver reads CLIENT_SESSION_KEEP_ALIVE. */
    private static String property(final Properties info, final String name) {
        final String exact = info.getProperty(name);
        if (exact != null) {
            return exact;
        }
        for (final String key : info.stringPropertyNames()) {
            if (key.equalsIgnoreCase(name)) {
                return info.getProperty(key);
            }
        }
        return null;
    }

    /** A whole number of seconds, or null when the property is absent or unreadable. */
    private static Integer seconds(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (final NumberFormatException unreadable) {
            return null;
        }
    }

    /** The transport, for the heartbeat tests. */
    HttpClient transport() {
        return httpClient;
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new DatabaseStatement(this, httpClient);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkClosed();
        return new DatabasePreparedStatement(this, httpClient, sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkClosed();
        return new DatabaseCallableStatement(this, httpClient, sql);
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        checkClosed();
        // ALTER SESSION drives the engine's real per-session autocommit flag; a plain SET would only
        // create a session VARIABLE named "autocommit" and leave every statement auto-committing.
        // Must propagate failures — a silently ignored toggle breaks transaction semantics — and the
        // connection takes the mode on only once the session has.
        httpClient.setAutoCommit(autoCommit);
        this.autoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        return autoCommit;
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot commit when autocommit is enabled");
        }
        // Direct call, not the lenient execute(): a failed COMMIT must surface to the caller.
        httpClient.endTransaction(true);
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot rollback when autocommit is enabled");
        }
        // Direct call, not the lenient execute(): a failed ROLLBACK must surface to the caller. A transaction
        // that went with a lost session is already rolled back, so that one succeeds.
        httpClient.endTransaction(false);
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            // A connection releases its resources at once (java.sql.Connection#close): what a statement of it runs on
            // another thread is canceled first, so the release does not wait for it and its effects are not kept.
            try {
                httpClient.cancelInFlight();
            } catch (final SQLException unreachable) {
                // The release below is bounded too, and the server's idle sweep reclaims what it leaves.
            }
            httpClient.releaseSession();
            logger.debug("Closed connection (session: {})", httpClient.getSessionId());
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public boolean labelsIgnoreCase() {
        return httpClient.labelsIgnoreCase();
    }

    @Override
    public ZoneId sessionZone() {
        return httpClient.sessionZone();
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new DatabaseMetaData(this, httpClient);
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        checkClosed();
        execute("USE DATABASE " + catalog);
        this.catalog = catalog;
    }

    /**
     * The URL this connection answers with: {@code jdbc:frostlake://host:port}, and the database it opened in.
     *
     * @return the URL
     */
    String jdbcUrl() {
        final String server = httpClient.baseUrl();
        final int scheme = server.indexOf("://");
        return "jdbc:frostlake://" + (scheme >= 0 ? server.substring(scheme + 3) : server)
            + (database != null ? "/" + database : "");
    }

    /**
     * The user the session runs as, as the server reports it.
     *
     * @return the user's name, or null when the server reports none
     */
    String userName() {
        final SessionNamespace reported = httpClient.namespace();
        return reported != null ? reported.getUser() : null;
    }

    /** The session's current database as the server last reported it, else as this connection's USEs set it. */
    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        final SessionNamespace reported = httpClient.namespace();
        return reported != null ? reported.getDatabase() : catalog;
    }

    /** Called by DatabaseStatement to keep catalog in sync after USE DATABASE. */
    void updateCatalog(final String catalog) {
        this.catalog = catalog;
    }

    /** Called by DatabaseStatement to keep schema in sync after USE SCHEMA. */
    void updateSchema(final String schema) {
        this.schema = schema;
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public boolean isValid(final int timeout) throws SQLException {
        requireValidTimeout(timeout);
        if (closed) {
            return false;
        }
        // The server's health, not the session's: a session the server no longer holds is replaced by the
        // next statement when it held nothing, and reported by it when it did.
        return httpClient.isHealthy(timeout);
    }

    int getMultiStatementCount() {
        return multiStatementCount;
    }

    void setMultiStatementCount(final int count) {
        this.multiStatementCount = count;
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Structs not supported");
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        checkClosed();
        if (execute("USE SCHEMA " + schema)) {
            this.schema = schema;
        }
    }

    /** The session's current schema as the server last reported it, else as this connection's USEs set it. */
    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        final SessionNamespace reported = httpClient.namespace();
        return reported != null ? reported.getSchema() : schema;
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        close();
    }

    // Helper methods

    /**
     * Runs a statement that sets session state, ignoring a refusal, and answers whether it ran. A session
     * lost with state on it is still reported: the state asked for could not be set.
     */
    private boolean execute(final String sql) throws SQLException {
        try {
            httpClient.execute(sql);
            return true;
        } catch (final FrostlakeSessionLostException lost) {
            throw lost;
        } catch (final SQLException e) {
            // Ignore errors for setting session state
            logger.debug("Error executing {}: {}", sql, e.getMessage());
            return false;
        }
    }

    public String getSessionId() {
        return httpClient.getSessionId();
    }
}
