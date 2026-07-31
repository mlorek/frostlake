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
import dev.frostlake.transaction.TransactionManager;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * Direct JDBC connection that wraps a DatabaseEngine instance without HTTP.
 * Used for testing and embedded usage.
 */
public class DirectConnection implements Connection {

    private final DatabaseEngine engine;
    private boolean closed = false;
    private boolean autoCommit = true;
    private int savepointIdCounter = 0;
    // This connection's own session context over the SHARED per-name engine, mirroring what a real
    // Snowflake connection carries. Every statement runs inside a per-thread scope built from these,
    // so a USE on one connection (possibly on another thread — the test harness runs bulk checks on
    // pool threads) can never leak into another connection's name resolution mid-statement.
    private String sessionDatabase;
    private String sessionSchema;
    private boolean sessionAutoCommit;

    public DirectConnection(final DatabaseEngine engine) {
        this.engine = engine;
        this.sessionDatabase = engine.getCurrentDatabase();
        this.sessionSchema = engine.getCurrentSchema();
        this.sessionAutoCommit = engine.isAutoCommit();
    }

    /**
     * Execute one statement under THIS connection's session context: the context is bound to the
     * current thread for the statement's duration (so concurrent connections stay isolated on the
     * shared engine) and the post-statement context — a USE, an ALTER of autocommit — is captured
     * back into the connection.
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
            try {
                final ExecutionResult result = engine.execute(sql);
                sessionDatabase = catalog.getCurrentDatabase();
                sessionSchema = catalog.getCurrentSchema();
                sessionAutoCommit = transactions.isAutoCommit();
                return result;
            } finally {
                catalog.clearSessionScope();
                transactions.clearSessionAutoCommit();
            }
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
        closed = true;
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
        this.autoCommit = autoCommit;
        // The connection's session context supplies the mode to every statement via executeScoped —
        // writing the engine's global flag here would leak the mode into other connections.
        this.sessionAutoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return autoCommit;
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
        return new DirectDatabaseMetaData(this, engine);
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
        String[] attributeNames = new String[attributes.length];
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
