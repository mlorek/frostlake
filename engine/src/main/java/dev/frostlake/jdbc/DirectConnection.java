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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.security.SessionContext;
import dev.frostlake.security.SessionSettings;
import dev.frostlake.transaction.TransactionManager;

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
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Direct JDBC connection that wraps a DatabaseEngine instance without HTTP.
 * Used for testing and embedded usage.
 */
public class DirectConnection implements Connection {

    private final DatabaseEngine engine;
    private boolean closed = false;
    private int savepointIdCounter = 0;
    // This connection's own session context over the SHARED per-name engine, mirroring what a real
    // Snowflake connection carries. Every statement runs inside a per-thread scope built from these,
    // so a USE on one connection (possibly on another thread — the test harness runs bulk checks on
    // pool threads) can never leak into another connection's name resolution mid-statement.
    private String sessionDatabase;
    private String sessionSchema;
    private boolean sessionAutoCommit;

    /**
     * The id of THIS connection's open transaction, or null when it has none.
     *
     * <p>The engine keeps its current transaction in a thread-local, so two connections driven from one
     * thread would otherwise share it and each would see the other's uncommitted rows. Live keeps them
     * apart, so the id is held here and rebound before every statement — the same mechanism the HTTP
     * front-end uses to isolate its sessions.
     */
    private Long sessionTransactionId;

    /**
     * Every transaction a direct connection has opened, across all of them.
     *
     * <p>A connection with no transaction of its own must clear a binding left by ANOTHER connection,
     * or it reads that connection's uncommitted rows — but it must NOT clear a binding left by plain
     * engine use on the same thread, which the statement machinery carries between statements. Only a
     * transaction in this set is known to belong to a connection, so only that one is cleared.
     */
    private static final Set<Long> CONNECTION_OWNED_TRANSACTIONS =
        Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    /** The session's MULTI_STATEMENT_COUNT (1 = single statement only, 0 = any). */
    private int multiStatementCount = 1;

    /**
     * This connection's ALTER SESSION parameters and SET variables, bound for each of its statements; or null for a
     * connection that shares the engine's own. Live keeps them per connection: a TIMEZONE, a QUERY_TAG or a variable
     * one connection sets is invisible to another connection to the same name.
     */
    private final SessionSettings sessionSettings;

    public DirectConnection(final DatabaseEngine engine) {
        this(engine, true);
    }

    /**
     * A connection to an engine.
     *
     * @param engine the engine
     * @param ownSession whether the connection keeps session parameters and variables of its own, as every connection
     *                   a driver URL opens does; false shares the engine's, for a caller that wraps an engine it
     *                   also reads directly
     */
    public DirectConnection(final DatabaseEngine engine, final boolean ownSession) {
        this.engine = engine;
        this.sessionDatabase = engine.getCurrentDatabase();
        this.sessionSchema = engine.getCurrentSchema();
        this.sessionAutoCommit = engine.isAutoCommit();
        this.sessionSettings = ownSession ? new SessionSettings() : null;
    }

    int getMultiStatementCount() {
        return multiStatementCount;
    }

    void setMultiStatementCount(final int count) {
        this.multiStatementCount = count;
    }

    /**
     * Execute one statement under THIS connection's session context: the context is bound to the
     * current thread for the statement's duration (so concurrent connections stay isolated on the
     * shared engine) and the post-statement context — a USE, an ALTER of autocommit, an opened or
     * closed transaction — is captured back into the connection.
     */
    ExecutionResult executeScoped(final String sql) {
        // One statement at a time on the shared engine: the embedded engine's procedural state
        // (variable scopes, handler stacks) is not safe under concurrent execution, and direct
        // connections can be driven from many threads (the test harness's pooled bulk checks).
        // The HTTP front-end serializes through its own lock; direct connections serialize here.
        synchronized (engine) {
            final Catalog catalog = engine.getCatalog();
            final TransactionManager transactions = engine.getTransactionManager();
            catalog.beginSessionScope(sessionDatabase, sessionSchema);
            transactions.beginSessionAutoCommit(sessionAutoCommit);
            final SessionContext session = engine.getSecurityManager().getSessionContext();
            if (sessionSettings != null) {
                session.bindSettings(sessionSettings);
            }
            // Restore this connection's transaction, or clear ANOTHER connection's: skipping that would
            // let this connection inherit whatever the previous one on this thread left open, and read
            // its uncommitted writes.
            bindOwnTransaction(transactions);
            try {
                return engine.execute(sql);
            } finally {
                // Captured when the request fails too: a request of several statements that fails part-way
                // keeps what its earlier statements did — a transaction a BEGIN opened stays open in the
                // session (live-verified) — so the connection must take it as its own. Left on the thread
                // uncaptured, it was adopted by whichever connection ran next there, or orphaned.
                sessionDatabase = catalog.getCurrentDatabase();
                sessionSchema = catalog.getCurrentSchema();
                sessionAutoCommit = transactions.isAutoCommit();
                sessionTransactionId = transactions.getCurrentTransactionId();
                if (sessionTransactionId != null) {
                    CONNECTION_OWNED_TRANSACTIONS.add(sessionTransactionId);
                }
                catalog.clearSessionScope();
                transactions.clearSessionAutoCommit();
                if (sessionSettings != null) {
                    session.unbindSettings();
                }
            }
        }
    }

    /**
     * Bind this connection's transaction for the statement about to run.
     *
     * @param transactions the engine's transaction manager
     */
    private void bindOwnTransaction(final TransactionManager transactions) {
        if (sessionTransactionId != null) {
            transactions.setCurrentTransaction(sessionTransactionId);
            return;
        }
        final Long bound = transactions.getCurrentTransactionId();
        if (bound != null && CONNECTION_OWNED_TRANSACTIONS.contains(bound)) {
            transactions.setCurrentTransaction(null);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new DirectStatement(this, engine);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkClosed();
        return new DirectPreparedStatement(this, engine, sql);
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        endOwnTransaction();
    }

    /**
     * Roll back the transaction this connection leaves open, as the HTTP front-end does when a session is
     * released: the session that owned it is gone, so nothing could ever COMMIT it, and it would otherwise
     * stay in SHOW TRANSACTIONS for the engine's lifetime. The thread's own binding is put back afterwards.
     */
    private void endOwnTransaction() {
        final Long open = sessionTransactionId;
        if (open == null) {
            return;
        }
        synchronized (engine) {
            final TransactionManager transactions = engine.getTransactionManager();
            final Long bound = transactions.getCurrentTransactionId();
            transactions.setCurrentTransaction(open);
            try {
                if (open.equals(transactions.getCurrentTransactionId())) {
                    transactions.rollback();
                }
            } catch (final RuntimeException ignored) {
                // The connection goes regardless; its transaction's writes were never committed.
            } finally {
                transactions.setCurrentTransaction(open.equals(bound) ? null : bound);
            }
        }
        CONNECTION_OWNED_TRANSACTIONS.remove(open);
        sessionTransactionId = null;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public boolean isValid(final int timeout) throws SQLException {
        return !closed;
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        // The connection's session context supplies the mode to every statement via executeScoped —
        // writing the engine's global flag here would leak the mode into other connections.
        this.sessionAutoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        // The session's EFFECTIVE mode: an ALTER SESSION SET AUTOCOMMIT executed over this
        // connection moves it too, not just setAutoCommit.
        return sessionAutoCommit;
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        executeScoped("COMMIT");
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        executeScoped("ROLLBACK");
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection is closed");
        }
    }

    // Unimplemented methods required by Connection interface

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkClosed();
        return new DirectCallableStatement(this, engine, sql);
    }

    @Override
    public String nativeSQL(final String sql) throws SQLException {
        return sql;
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new DirectDatabaseMetaData(this);
    }

    @Override
    public void setReadOnly(final boolean readOnly) throws SQLException {
        // No-op
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        executeScoped("USE DATABASE " + catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        return sessionDatabase;
    }

    @Override
    public void setTransactionIsolation(final int level) throws SQLException {
        // No-op
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        return Connection.TRANSACTION_READ_COMMITTED;
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        // No-op
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
        return prepareCall(sql);
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTypeMap not supported");
    }

    @Override
    public void setTypeMap(final Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTypeMap not supported");
    }

    @Override
    public void setHoldability(final int holdability) throws SQLException {
        // No-op
    }

    @Override
    public int getHoldability() throws SQLException {
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
        return prepareCall(sql);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkClosed();
        return new DirectPreparedStatement(this, engine, sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        checkClosed();
        // Treat as RETURN_GENERATED_KEYS if column indexes specified
        return new DirectPreparedStatement(this, engine, sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        checkClosed();
        // Treat as RETURN_GENERATED_KEYS if column names specified
        return new DirectPreparedStatement(this, engine, sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("CLOB is not supported by Snowflake");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("BLOB is not supported by Snowflake");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("createNClob not supported");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("createSQLXML not supported");
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface == DatabaseEngine.class || iface.isInstance(this);
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        // The embedded engine behind this connection — how in-process harnesses reach engine-level
        // APIs (checkpointStateTo/restoreStateFrom) that have no SQL surface.
        if (iface == DatabaseEngine.class) {
            return iface.cast(engine);
        }
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public Array createArrayOf(final String typeName, final Object[] elements) throws SQLException {
        checkClosed();
        return new DirectArray(typeName, elements);
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
        checkClosed();
        // For Struct, we need attribute names, but JDBC createStruct doesn't provide them
        // Generate default attribute names: ATTR1, ATTR2, etc.
        final String[] attributeNames = new String[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            attributeNames[i] = "ATTR" + (i + 1);
        }
        return new DirectStruct(typeName, attributeNames, attributes);
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        executeScoped("USE SCHEMA " + schema);
    }

    @Override
    public String getSchema() throws SQLException {
        return sessionSchema;
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
    public String getClientInfo(final String name) throws SQLException {
        return null;
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        return new Properties();
    }

    @Override
    public void setClientInfo(final String name, final String value) throws SQLClientInfoException {
        // No-op
    }

    @Override
    public void setClientInfo(final Properties properties) throws SQLClientInfoException {
        // No-op
    }
}
