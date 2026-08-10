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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct Statement implementation for embedded DatabaseEngine
 */
public class DirectStatement implements Statement {

    private final Connection connection;
    private final DatabaseEngine engine;
    private boolean closed = false;
    private ResultSet currentResultSet = null;
    // All result sets produced by the last (possibly multi-statement) execute, and a cursor into them so
    // getMoreResults() can walk past the first — a batch like "SELECT …; SELECT …" exposes every result.
    private List<ResultSet> pendingResultSets = new ArrayList<>();
    private int resultSetIndex = 0;
    private final List<String> batchStatements = new ArrayList<>();
    private final List<Long> generatedKeys = new ArrayList<>();
    private String lastQueryId;
    private boolean returnGeneratedKeys = false;
    private static long keySequence = 0;

    public DirectStatement(final Connection connection, final DatabaseEngine engine) {
        this.connection = connection;
        this.engine = engine;
    }


    /** Per-statement MULTI_STATEMENT_COUNT set via {@link #setParameter}, overriding the session's. */
    private Integer statementMultiCount;

    /**
     * The real driver's per-statement parameter surface ({@code unwrap(...).setParameter(...)});
     * MULTI_STATEMENT_COUNT is the one parameter this driver understands.
     */
    public void setParameter(final String name, final int value) throws SQLException {
        if (!"MULTI_STATEMENT_COUNT".equalsIgnoreCase(name)) {
            throw new SQLException("Unknown statement parameter: " + name);
        }
        this.statementMultiCount = Integer.valueOf(value);
    }

    /**
     * The multi-statement gate the real driver runs before executing (live-verified): the pack's
     * statement count must EQUAL the desired count — per-statement parameter first, else the
     * connection's session value — with 0 meaning any. An exact ALTER SESSION SET/UNSET
     * MULTI_STATEMENT_COUNT statement passes through and moves the connection's value.
     */
    private void applyMultiStatementGate(final String sql) throws SQLException {
        // The ALTER SESSION statement is gated like any other (live-verified: under count 2 even
        // the UNSET refuses as 1 vs 2 — the per-statement parameter is the escape hatch); its
        // assignment takes effect only once it passes.
        gate(sql);
        final Integer assigned = JdbcMultiStatement.sessionCountAssignment(sql);
        if (assigned != null) {
            if (connection instanceof DirectConnection) {
                ((DirectConnection) connection).setMultiStatementCount(assigned.intValue());
            }
            return;
        }
    }

    private void gate(final String sql) throws SQLException {
        final int desired = statementMultiCount != null
            ? statementMultiCount.intValue()
            : (connection instanceof DirectConnection
                ? ((DirectConnection) connection).getMultiStatementCount() : 1);
        if (desired == 0) {
            return;
        }
        final int actual = JdbcMultiStatement.countStatements(sql);
        if (actual != desired) {
            throw JdbcMultiStatement.countMismatch(actual, desired);
        }
    }

    public java.sql.ResultSet executeQuery(final String sql) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        try {
            final ExecutionResult result = connection instanceof DirectConnection
                ? ((DirectConnection) connection).executeScoped(sql)
                : engine.execute(sql);
            lastQueryId = result.getQueryId();

            if (!result.isSuccess()) {
                throw new SQLException(result.getErrorMessage());
            }

            final List<ResultSet> resultSets = result.getResultSets();
            if (resultSets.isEmpty()) {
                throw new SQLException("Query did not return a result set");
            }

            pendingResultSets = resultSets;
            resultSetIndex = 0;
            currentResultSet = resultSets.get(0);
            return new DirectResultSet(this, currentResultSet);
        } catch (final RuntimeException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        return executeUpdate(sql, Statement.NO_GENERATED_KEYS);
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        generatedKeys.clear();
        returnGeneratedKeys = (autoGeneratedKeys == Statement.RETURN_GENERATED_KEYS);

        try {
            final ExecutionResult result = connection instanceof DirectConnection
                ? ((DirectConnection) connection).executeScoped(sql)
                : engine.execute(sql);
            lastQueryId = result.getQueryId();

            if (!result.isSuccess()) {
                throw new SQLException(result.getErrorMessage());
            }

            // If this is an INSERT and generated keys are requested, create a synthetic key
            if (returnGeneratedKeys && sql.trim().toUpperCase().startsWith("INSERT")) {
                // Generate a simple sequential key (in real implementation, would get from auto-increment column)
                generatedKeys.add(++keySequence);
            }

            // DML statements report their affected-row count as a Snowflake-style result set.
            return (int) result.getRowsAffected();
        } catch (final RuntimeException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    @Override
    public boolean execute(final String sql) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        try {
            final ExecutionResult result = connection instanceof DirectConnection
                ? ((DirectConnection) connection).executeScoped(sql)
                : engine.execute(sql);
            lastQueryId = result.getQueryId();

            if (!result.isSuccess()) {
                throw new SQLException(result.getErrorMessage());
            }

            final List<ResultSet> resultSets = result.getResultSets();
            pendingResultSets = resultSets;
            resultSetIndex = 0;
            if (!resultSets.isEmpty()) {
                currentResultSet = resultSets.get(0);
                return true;
            }
            currentResultSet = null;
            return false;
        } catch (final RuntimeException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }

    @Override
    public java.sql.ResultSet getResultSet() throws SQLException {
        checkClosed();
        if (currentResultSet == null) {
            return null;
        }
        return new DirectResultSet(this, currentResultSet);
    }

    /**
     * The engine-level result of the last execution, for subclasses that need row access without
     * disturbing the JDBC cursor (e.g. the callable statement's OUT-parameter extraction).
     */
    protected ResultSet currentEngineResultSet() {
        return currentResultSet;
    }

    @Override
    public void close() throws SQLException {
        closed = true;
        currentResultSet = null;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
    }

    // Unimplemented methods

    @Override
    public int getMaxFieldSize() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxFieldSize(final int max) throws SQLException {
        // No-op
    }

    @Override
    public int getMaxRows() throws SQLException {
        return 0;
    }

    @Override
    public void setMaxRows(final int max) throws SQLException {
        // No-op
    }

    @Override
    public void setEscapeProcessing(final boolean enable) throws SQLException {
        // No-op
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return 0;
    }

    @Override
    public void setQueryTimeout(final int seconds) throws SQLException {
        // No-op
    }

    @Override
    public void cancel() throws SQLException {
        throw new SQLFeatureNotSupportedException("cancel not supported");
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
    public void setCursorName(final String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("setCursorName not supported");
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return -1;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        // Advance to the next result set produced by the last (multi-statement) execute, so a batch of
        // SELECTs is fully walkable. Returns false once the results are exhausted.
        resultSetIndex++;
        if (resultSetIndex < pendingResultSets.size()) {
            currentResultSet = pendingResultSets.get(resultSetIndex);
            return true;
        }
        currentResultSet = null;
        return false;
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return java.sql.ResultSet.FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(final int rows) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchSize() throws SQLException {
        return 0;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return java.sql.ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return java.sql.ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(final String sql) throws SQLException {
        checkClosed();
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("Batch SQL cannot be null or empty");
        }
        batchStatements.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        batchStatements.clear();
    }

    @Override
    /**
     * Live runs every batched statement even when one fails: each entry reports its real
     * affected-row count (not SUCCESS_NO_INFO), a failed entry is marked {@code EXECUTE_FAILED},
     * and after the batch a {@link BatchUpdateException} carries the FIRST failure's message,
     * SQLSTATE and error code together with the complete update-count array.
     */
    public int[] executeBatch() throws SQLException {
        checkClosed();
        final int[] results = new int[batchStatements.size()];
        int index = 0;
        SQLException firstFailure = null;

        try {
            for (final String sql : batchStatements) {
                try {
                    results[index] = executeUpdate(sql);
                } catch (final SQLException e) {
                    results[index] = EXECUTE_FAILED;
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
                index++;
            }
        } finally {
            // Clear batch after execution
            batchStatements.clear();
        }

        if (firstFailure != null) {
            throw new BatchUpdateException(firstFailure.getMessage(), firstFailure.getSQLState(),
                    firstFailure.getErrorCode(), results, firstFailure);
        }
        return results;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    /**
     * The engine query id of the last statement this object executed, or null before the first —
     * the direct-transport twin of the Snowflake driver's {@code SnowflakeStatement.getQueryID()}.
     */
    public String getQueryId() {
        return lastQueryId;
    }

    @Override
    public boolean getMoreResults(final int current) throws SQLException {
        return false;
    }

    @Override
    public java.sql.ResultSet getGeneratedKeys() throws SQLException {
        checkClosed();

        // Create a simple ResultSet with generated keys
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(
            "GENERATED_KEY",
            new NumericType("BIGINT", 19, 0)
        ));

        final List<Row> rows = new ArrayList<>();
        for (final Long key : generatedKeys) {
            final List<Object> values = new ArrayList<>();
            values.add(key);
            rows.add(new Row(values));
        }

        final ResultSet resultSet = new ResultSet(columns, rows);
        return new DirectResultSet(this, resultSet);
    }

    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column indexes specified
        return executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column names specified
        return executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkClosed();
        generatedKeys.clear();
        returnGeneratedKeys = (autoGeneratedKeys == Statement.RETURN_GENERATED_KEYS);

        final boolean hasResultSet = execute(sql);

        // If this is an INSERT and generated keys are requested, create a synthetic key
        if (returnGeneratedKeys && sql.trim().toUpperCase().startsWith("INSERT")) {
            generatedKeys.add(++keySequence);
        }

        return hasResultSet;
    }

    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column indexes specified
        return execute(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column names specified
        return execute(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public void setPoolable(final boolean poolable) throws SQLException {
        // No-op
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return false;
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        // No-op
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return false;
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }
}
