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
import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.CharStreams;
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
    // Every result set produced by the last (possibly multi-statement) execute, plus a cursor into them so
    // getMoreResults() walks past the first — a batch like "SELECT …; SELECT …" exposes every result.
    protected List<ResultSetData> pendingResultData = new ArrayList<>();
    protected int resultDataIndex = 0;
    protected final List<String> batchedSql = new ArrayList<>();
    protected int updateCount;
    protected int maxRows;

    public DatabaseStatement(final DatabaseConnection connection, final HttpClient httpClient) {
        this.connection = connection;
        this.httpClient = httpClient;
        this.closed = false;
        this.updateCount = -1;
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
            throw JdbcMultiStatement.countMismatch(actual, desired);
        }
    }

    public ResultSet executeQuery(final String sql) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        final SqlResponse response = httpClient.execute(sql);
        pendingResultData = response.getResultSets() != null ? response.getResultSets() : new ArrayList<>();
        resultDataIndex = 0;

        if (!pendingResultData.isEmpty()) {
            currentResultSet = new DatabaseResultSet(this, pendingResultData.get(0));
            updateCount = -1;
            return currentResultSet;
        }

        throw new SQLException("Query did not return a result set");
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        final SqlResponse response = httpClient.execute(sql);
        updateContextFromSql(sql);

        // DML statements report their affected-row count as a Snowflake-style result set
        // ("number of rows inserted/updated/deleted") — sum those columns of the first row.
        updateCount = (int) extractRowsAffected(response);
        currentResultSet = null;
        return updateCount;
    }

    /** Sum every "number of rows …" column in the response's result sets (first row each). */
    private long extractRowsAffected(final SqlResponse response) {
        if (response.getResultSets() == null) {
            return 0;
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

    @Override
    public boolean execute(final String sql) throws SQLException {
        checkClosed();
        applyMultiStatementGate(sql);
        final SqlResponse response = httpClient.execute(sql);
        updateContextFromSql(sql);
        pendingResultData = response.getResultSets() != null ? response.getResultSets() : new ArrayList<>();
        resultDataIndex = 0;

        if (!pendingResultData.isEmpty()) {
            currentResultSet = new DatabaseResultSet(this, pendingResultData.get(0));
            updateCount = -1;
            return true;
        } else {
            currentResultSet = null;
            updateCount = 0;
            return false;
        }
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
        final List<Token> tokens = new ArrayList<>();
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            lexer.removeErrorListeners();
            for (Token t = lexer.nextToken(); t.getType() != Token.EOF; t = lexer.nextToken()) {
                if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                    tokens.add(t);
                }
            }
        } catch (final RuntimeException notLexable) {
            return;
        }
        if (tokens.isEmpty() || tokens.get(0).getType() != FrostlakeLexer.USE) {
            return;
        }
        if (tokens.get(tokens.size() - 1).getType() == FrostlakeLexer.SEMI) {
            tokens.remove(tokens.size() - 1);
        }
        if (tokens.size() < 3) {
            return;
        }
        final int kw = tokens.get(1).getType();
        if (kw == FrostlakeLexer.DATABASE && tokens.size() == 3 && isUseNamePart(tokens.get(2))) {
            connection.updateCatalog(SqlIdentifiers.canonicalText(tokens.get(2).getText()));
        } else if (kw == FrostlakeLexer.SCHEMA && tokens.size() == 3 && isUseNamePart(tokens.get(2))) {
            connection.updateSchema(SqlIdentifiers.canonicalText(tokens.get(2).getText()));
        } else if (kw == FrostlakeLexer.SCHEMA && tokens.size() == 5
                && isUseNamePart(tokens.get(2))
                && tokens.get(3).getType() == FrostlakeLexer.DOT
                && isUseNamePart(tokens.get(4))) {
            connection.updateCatalog(SqlIdentifiers.canonicalText(tokens.get(2).getText()));
            connection.updateSchema(SqlIdentifiers.canonicalText(tokens.get(4).getText()));
        }
    }

    /** A token that can serve as the name in USE DATABASE/SCHEMA — anything but punctuation. */
    private boolean isUseNamePart(final Token token) {
        final int type = token.getType();
        return type != FrostlakeLexer.SEMI && type != FrostlakeLexer.DOT;
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkClosed();
        return currentResultSet;
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        return updateCount;
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
        // Advance to the next result set produced by the last (multi-statement) execute.
        resultDataIndex++;
        if (resultDataIndex < pendingResultData.size()) {
            currentResultSet = new DatabaseResultSet(this, pendingResultData.get(resultDataIndex));
            return true;
        }
        return false;
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
                    counts[i] = executeUpdate(batchedSql.get(i));
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
