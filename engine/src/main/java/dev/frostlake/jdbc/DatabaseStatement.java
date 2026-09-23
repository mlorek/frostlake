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

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.http.ResultSetData;
import dev.frostlake.http.SqlResponse;

import org.antlr.v4.runtime.Token;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Statement implementation for Frostlake SQL Engine
 */
public class DatabaseStatement implements Statement {
    protected final DatabaseConnection connection;
    protected final HttpClient httpClient;
    protected boolean closed;
    protected DatabaseResultSet currentResultSet;
    // Every result the last (possibly multi-statement) execution produced, one per statement, and the walk
    // over them: which answer rows and which an update count, and where getMoreResults() stands.
    protected List<ResultSetData> pendingResultData = new ArrayList<>();
    private JdbcResultWalk walk = JdbcResultWalk.none();
    protected final List<String> batchedSql = new ArrayList<>();
    protected int maxRows;

    public DatabaseStatement(final DatabaseConnection connection, final HttpClient httpClient) {
        this.connection = connection;
        this.httpClient = httpClient;
        this.closed = false;
        this.maxRows = 0;
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
            connection.setMultiStatementCount(assigned.intValue());
            return;
        }
    }

    private void gate(final String sql) throws SQLException {
        final int desired = statementMultiCount != null
            ? statementMultiCount.intValue() : connection.getMultiStatementCount();
        if (desired == 0) {
            return;
        }
        final int actual = JdbcMultiStatement.countStatements(sql);
        if (actual != desired) {
            throw JdbcMultiStatement.countMismatch(sql, actual, desired);
        }
    }

    /** The count this statement declares to the server: its own parameter, else the session's. */
    private Integer desiredMultiCount() {
        return statementMultiCount != null
            ? statementMultiCount : Integer.valueOf(connection.getMultiStatementCount());
    }

    /** Send a request, gated first, and keep its results. */
    private SqlResponse run(final String sql) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        final SqlResponse response = httpClient.execute(sql, desiredMultiCount());
        pendingResultData = response.getResultSets() != null ? response.getResultSets() : new ArrayList<>();
        return response;
    }

    /**
     * Take the request's results as the walk's: each answers rows, or the update count the server marked
     * its statement with. A server predating the mark answers rows for every statement, as it always did.
     * With {@code firstAsRows} the first answers rows whatever it is.
     */
    private void take(final boolean firstAsRows) {
        final List<Long> counts = new ArrayList<>();
        for (int i = 0; i < pendingResultData.size(); i++) {
            counts.add(firstAsRows && i == 0 ? null : countOf(pendingResultData.get(i)));
        }
        walk = new JdbcResultWalk(counts);
        currentResultSet = walk.currentIsRows() ? new DatabaseResultSet(this, pendingResultData.get(0)) : null;
    }

    /** The update count a result's statement reports, or null when it answers rows (or the server is older). */
    private static Long countOf(final ResultSetData data) {
        final Long marked = data.getJdbcUpdateCount();
        return marked == null || marked.longValue() < 0 ? null : marked;
    }

    /** Whether the server marks each result with its statement's update count. */
    private boolean serverMarksCounts() {
        return !pendingResultData.isEmpty() && pendingResultData.get(0).getJdbcUpdateCount() != null;
    }

    /**
     * Snowflake's rule: a single statement answers its result as a result set — a DML statement its count
     * grid, DDL its status line — while a request of several must begin with rows, and otherwise has run and
     * is refused all the same.
     */
    public ResultSet executeQuery(final String sql) throws SQLException {
        run(sql);
        updateContextFromSql(sql);
        if (pendingResultData.isEmpty()) {
            take(false);
            throw new SQLException("Query did not return a result set");
        }
        if (pendingResultData.size() > 1 && countOf(pendingResultData.get(0)) != null) {
            take(false);
            throw JdbcResultWalk.firstResultIsACount();
        }
        take(true);
        return currentResultSet;
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        return (int) update(sql);
    }

    /**
     * The first statement's update count. A statement that answers rows has run by the time it is refused
     * (live-verified: a refused {@code CALL} has still done its work). From a server predating the mark, the
     * count is the old reading of the grids.
     */
    private long update(final String sql) throws SQLException {
        final SqlResponse response = run(sql);
        updateContextFromSql(sql);
        if (!serverMarksCounts()) {
            walk = JdbcResultWalk.ofCount(extractRowsAffected(response));
            currentResultSet = null;
            return walk.updateCount();
        }
        take(false);
        if (walk.currentIsRows()) {
            throw JdbcResultWalk.notAnUpdate(sql);
        }
        return Math.max(walk.updateCount(), 0);
    }

    @Override
    public long executeLargeUpdate(final String sql) throws SQLException {
        return update(sql);
    }

    @Override
    public long executeLargeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        return update(sql);
    }

    @Override
    public long executeLargeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        return update(sql);
    }

    @Override
    public long executeLargeUpdate(final String sql, final String[] columnNames) throws SQLException {
        return update(sql);
    }

    /** Leave a whole prepared batch behind as one update count, as the driver's array-bound batch does. */
    protected void takeBatchCount(final long count) {
        pendingResultData = new ArrayList<>();
        currentResultSet = null;
        walk = JdbcResultWalk.ofCount(count);
    }

    /**
     * The affected-row total of a response: the servers' own marks when it sends them — every result's
     * updateCount, -1 for anything but a DML count grid — and, from a server predating the field, the
     * sum of every "number of rows …" column (first row each).
     */
    private long extractRowsAffected(final SqlResponse response) {
        if (response.getResultSets() == null) {
            return 0;
        }
        long marked = 0;
        boolean anyMarked = false;
        for (final ResultSetData data : response.getResultSets()) {
            if (data.getUpdateCount() != null) {
                anyMarked = true;
                if (data.getUpdateCount().longValue() >= 0) {
                    marked += data.getUpdateCount().longValue();
                }
            }
        }
        if (anyMarked) {
            return marked;
        }
        long total = 0;
        for (final ResultSetData data : response.getResultSets()) {
            if (data.getColumns() == null || data.getRows() == null || data.getRows().isEmpty()) {
                continue;
            }
            for (int i = 0; i < data.getColumns().size(); i++) {
                final String name = data.getColumns().get(i).getName();
                if (name != null && name.toLowerCase().startsWith("number of rows")) {
                    final Object value = data.getRows().get(0).get(i);
                    if (value instanceof Number) {
                        total += ((Number) value).longValue();
                    } else if (value != null) {
                        try {
                            total += Long.parseLong(value.toString());
                        } catch (final NumberFormatException ignored) {
                            // non-numeric count value — skip
                        }
                    }
                }
            }
        }
        return total;
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            if (currentResultSet != null) {
                currentResultSet.close();
            }
            closed = true;
        }
    }

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
        return maxRows;
    }

    @Override
    public void setMaxRows(final int max) throws SQLException {
        this.maxRows = max;
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
        throw new SQLFeatureNotSupportedException("Cancel not supported");
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
        throw new SQLFeatureNotSupportedException("Named cursors not supported");
    }

    /** True when the first statement answers rows; false when it answers an update count. */
    @Override
    public boolean execute(final String sql) throws SQLException {
        run(sql);
        updateContextFromSql(sql);
        take(false);
        return walk.currentIsRows();
    }

    /**
     * Update the connection's cached catalog/schema when USE DATABASE/SCHEMA is executed. Decided
     * from LEXER TOKENS, not string offsets: the former prefix check ran on whitespace-NORMALIZED
     * text while its substring offsets indexed the RAW text, so extra whitespace cut into the name
     * — and a quoted name kept its quotes and was then case-folded. Only the exact single-statement
     * shapes update the cache; USE SCHEMA db.sc updates both halves.
     */
    protected void updateContextFromSql(final String sql) {
        if (sql == null || connection == null) return;
        final List<List<Token>> statements = JdbcSessionEffects.statements(sql);
        if (statements == null || statements.size() != 1) {
            return;
        }
        final List<Token> tokens = statements.get(0);
        final JdbcScopeUse use = JdbcSessionEffects.scopeUse(tokens);
        if (use == JdbcScopeUse.DATABASE) {
            connection.updateCatalog(SqlIdentifiers.canonicalText(tokens.get(2).getText()));
        } else if (use == JdbcScopeUse.SCHEMA && tokens.size() == 3) {
            connection.updateSchema(SqlIdentifiers.canonicalText(tokens.get(2).getText()));
        } else if (use == JdbcScopeUse.SCHEMA) {
            connection.updateCatalog(SqlIdentifiers.canonicalText(tokens.get(2).getText()));
            connection.updateSchema(SqlIdentifiers.canonicalText(tokens.get(4).getText()));
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkClosed();
        return currentResultSet;
    }

    /** The current result's update count: -1 when it answers rows, and once the walk has passed the end. */
    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        return (int) walk.updateCount();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        checkClosed();
        return walk.updateCount();
    }

    /** Move to the next statement's result; see {@link JdbcResultWalk#next()} for what it answers. */
    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        final boolean answer = walk.next();
        if (walk.currentIsRows()) {
            currentResultSet = new DatabaseResultSet(this, pendingResultData.get(walk.position()));
        }
        return answer;
    }

    @Override
    public void setFetchDirection(final int direction) throws SQLException {
        // No-op
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return ResultSet.FETCH_FORWARD;
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
        return ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public int getResultSetType() throws SQLException {
        return ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public void addBatch(final String sql) throws SQLException {
        checkClosed();
        batchedSql.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        batchedSql.clear();
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
        final int[] counts = new int[batchedSql.size()];
        SQLException firstFailure = null;
        try {
            for (int i = 0; i < batchedSql.size(); i++) {
                try {
                    // A statement that answers rows is not refused in a batch: it runs, and its entry is
                    // SUCCESS_NO_INFO (live-verified).
                    final SqlResponse response = run(batchedSql.get(i));
                    updateContextFromSql(batchedSql.get(i));
                    if (serverMarksCounts()) {
                        take(false);
                        counts[i] = walk.currentIsRows() ? SUCCESS_NO_INFO : (int) walk.updateCount();
                    } else {
                        counts[i] = (int) extractRowsAffected(response);
                    }
                } catch (final SQLException e) {
                    counts[i] = EXECUTE_FAILED;
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
            }
        } finally {
            batchedSql.clear();
        }
        if (firstFailure != null) {
            throw new BatchUpdateException(firstFailure.getMessage(), firstFailure.getSQLState(),
                    firstFailure.getErrorCode(), counts, firstFailure);
        }
        return counts;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }

    @Override
    public boolean getMoreResults(final int current) throws SQLException {
        return getMoreResults();
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("Generated keys not supported");
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        return executeUpdate(sql);
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        return execute(sql);
    }

    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        return execute(sql);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
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
        if (iface.isAssignableFrom(getClass())) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface.isAssignableFrom(getClass());
    }

    protected void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Statement is closed");
        }
    }
}
