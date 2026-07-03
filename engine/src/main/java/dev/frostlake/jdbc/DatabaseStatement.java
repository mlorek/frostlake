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

import dev.frostlake.http.SqlResponse;

import java.sql.*;
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
    protected List<SqlResponse.ResultSetData> pendingResultData = new ArrayList<>();
    protected int resultDataIndex = 0;
    protected int updateCount;
    protected int maxRows;

    public DatabaseStatement(final DatabaseConnection connection, final HttpClient httpClient) {
        this.connection = connection;
        this.httpClient = httpClient;
        this.closed = false;
        this.updateCount = -1;
        this.maxRows = 0;
    }

    @Override
    public ResultSet executeQuery(final String sql) throws SQLException {
        checkClosed();
        SqlResponse response = httpClient.execute(sql);
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
        SqlResponse response = httpClient.execute(sql);
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
        for (final SqlResponse.ResultSetData data : response.getResultSets()) {
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
        SqlResponse response = httpClient.execute(sql);
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

    /** Update the connection's cached catalog/schema when USE DATABASE/SCHEMA is executed. */
    protected void updateContextFromSql(final String sql) {
        if (sql == null || connection == null) return;
        String upper = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        if (upper.startsWith("USE DATABASE ")) {
            String db = sql.trim().substring("USE DATABASE ".length()).trim().replaceAll(";$", "").trim();
            connection.updateCatalog(db.toUpperCase());
        } else if (upper.startsWith("USE SCHEMA ")) {
            String schema = sql.trim().substring("USE SCHEMA ".length()).trim().replaceAll(";$", "").trim();
            connection.updateSchema(schema.toUpperCase());
        }
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
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }

    @Override
    public void clearBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
    }

    @Override
    public int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("Batch updates not supported");
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
