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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * JDBC Connection implementation for Frostlake SQL Engine
 */
public class DatabaseConnection implements Connection {
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
    public String nativeSQL(final String sql) throws SQLException {
        return sql;
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        checkClosed();
        this.autoCommit = autoCommit;
        // ALTER SESSION drives the engine's real per-session autocommit flag; a plain SET would only
        // create a session VARIABLE named "autocommit" and leave every statement auto-committing.
        // Must propagate failures — a silently ignored toggle breaks transaction semantics.
        httpClient.execute("ALTER SESSION SET AUTOCOMMIT = " + (autoCommit ? "TRUE" : "FALSE"));
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
        httpClient.execute("COMMIT");
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("Cannot rollback when autocommit is enabled");
        }
        // Direct call, not the lenient execute(): a failed ROLLBACK must surface to the caller.
        httpClient.execute("ROLLBACK");
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            httpClient.releaseSession();
            logger.debug("Closed connection (session: {})", httpClient.getSessionId());
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new DatabaseMetaData(this, httpClient);
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        checkClosed();
        // No-op for now
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        checkClosed();
        execute("USE DATABASE " + catalog);
        this.catalog = catalog;
    }

    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        return catalog;
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
    public void setTransactionIsolation(final int level) throws SQLException {
        checkClosed();
        // No-op for now
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return Connection.TRANSACTION_READ_COMMITTED;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency) throws SQLException {
        checkClosed();
        return new DatabaseCallableStatement(this, httpClient, sql);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLFeatureNotSupportedException("Type maps not supported");
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("Type maps not supported");
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        checkClosed();
    }

    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLException("Savepoints are not supported by Snowflake");
    }

    @Override
    public Savepoint setSavepoint(final String name) throws SQLException {
        throw new SQLException("Savepoints are not supported by Snowflake");
    }

    @Override
    public void rollback(final Savepoint savepoint) throws SQLException {
        throw new SQLException("Savepoints are not supported by Snowflake");
    }

    @Override
    public void releaseSavepoint(final Savepoint savepoint) throws SQLException {
        throw new SQLException("Savepoints are not supported by Snowflake");
    }

    @Override
    public Statement createStatement(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return createStatement();
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public CallableStatement prepareCall(final String sql, final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability) throws SQLException {
        checkClosed();
        return new DatabaseCallableStatement(this, httpClient, sql);
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
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Clobs not supported");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("Blobs not supported");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("NClobs not supported");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLXML not supported");
    }

    @Override
    public boolean isValid(final int timeout) throws SQLException {
        if (closed) {
            return false;
        }
        return httpClient.isHealthy();
    }

    int getMultiStatementCount() {
        return multiStatementCount;
    }

    void setMultiStatementCount(final int count) {
        this.multiStatementCount = count;
    }

    public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
        // No-op
    }

    @Override
    public void setClientInfo(final Properties properties) throws SQLClientInfoException {
        // No-op
    }

    @Override
    public String getClientInfo(final String name) throws SQLException {
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return new Properties();
    }

    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        throw new SQLFeatureNotSupportedException("Arrays not supported");
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("Structs not supported");
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        checkClosed();
        execute("USE SCHEMA " + schema);
    }

    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return schema;
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        close();
    }

    @Override
    public void setNetworkTimeout(final Executor executor, final int milliseconds) throws SQLException {
        // No-op
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return 0;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }

    // Helper methods

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }

    private void execute(final String sql) throws SQLException {
        try {
            httpClient.execute(sql);
        } catch (final SQLException e) {
            // Ignore errors for setting session state
            logger.debug("Error executing {}: {}", sql, e.getMessage());
        }
    }

    public String getSessionId() {
        return httpClient.getSessionId();
    }
}
