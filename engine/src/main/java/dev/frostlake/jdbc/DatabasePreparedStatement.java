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

import java.sql.BatchUpdateException;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The HTTP transport's prepared statement: its execution and batches over the wire, with the parameter setters of
 * {@link FrostlakePreparedStatement}.
 */
public class DatabasePreparedStatement extends DatabaseStatement implements FrostlakePreparedStatement {
    private final String originalSql;
    private final Map<Integer, Object> parameters;
    private final List<Map<Integer, Object>> batchParameters = new ArrayList<>();
    private final Map<Integer, String> batchBindTypes = new HashMap<>();

    public DatabasePreparedStatement(final DatabaseConnection connection, final HttpClient httpClient, final String sql) {
        super(connection, httpClient);
        this.originalSql = sql;
        this.parameters = new HashMap<>();
    }

    @Override
    public Map<Integer, Object> boundParameters() {
        return parameters;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return super.executeQuery(buildSql());
    }

    @Override
    public int executeUpdate() throws SQLException {
        return super.executeUpdate(buildSql());
    }

    @Override
    public boolean execute() throws SQLException {
        return super.execute(buildSql());
    }

    @Override
    public void addBatch() throws SQLException {
        // Live refuses a row whose bind types differ from the earlier rows' (array binding).
        JdbcBindTypes.validateBatchRow(batchBindTypes, parameters, batchParameters.size() + 1);
        batchParameters.add(new HashMap<>(parameters));
    }

    @Override
    public void clearBatch() throws SQLException {
        super.clearBatch();
        batchParameters.clear();
        batchBindTypes.clear();
    }

    /**
     * Live executes a prepared batch as one array-bound statement: a failure surfaces as that
     * statement's own {@link SQLException} — never a {@link BatchUpdateException} — and the batch
     * applies nothing. Frostlake executes the rows one by one, so rows before the failing one
     * stay applied; the exception surface matches live, and the remaining rows are not attempted.
     */
    @Override
    public int[] executeBatch() throws SQLException {
        final int[] results = new int[batchParameters.size()];
        long total = 0;
        try {
            for (int i = 0; i < batchParameters.size(); i++) {
                final Map<Integer, Object> saved = new HashMap<>(parameters);
                parameters.clear();
                parameters.putAll(batchParameters.get(i));
                try {
                    // Live Snowflake reports the real affected-row count per batch entry, not
                    // SUCCESS_NO_INFO.
                    results[i] = executeUpdate();
                    total += results[i];
                } finally {
                    parameters.clear();
                    parameters.putAll(saved);
                }
            }
        } finally {
            batchParameters.clear();
            batchBindTypes.clear();
        }
        // The driver runs the batch as ONE array-bound statement, so what getUpdateCount() reports after it is
        // the whole batch's count (live-verified: two one-row entries leave 2).
        takeBatchCount(total);
        return results;
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return prepareResultMetaData();
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return new JdbcParameterMetaData(JdbcMarshaling.countPlaceholders(originalSql));
    }

    /**
     * Result-column metadata for a result-returning statement (null for DML / non-queries). Uses the last
     * executed result if present, else describes the query by executing a copy with every unbound placeholder
     * set to NULL (a parameterized SELECT then returns zero rows but still carries the projection's columns).
     */
    private ResultSetMetaData prepareResultMetaData() throws SQLException {
        if (currentResultSet != null) {
            return currentResultSet.getMetaData();
        }
        final String head = originalSql.trim().toUpperCase(Locale.ROOT);
        if (!(head.startsWith("SELECT") || head.startsWith("WITH") || head.startsWith("SHOW")
                || head.startsWith("DESC") || head.startsWith("EXPLAIN") || head.startsWith("CALL"))) {
            return null;
        }
        final Map<Integer, Object> filled = new HashMap<>(parameters);
        final int count = JdbcMarshaling.countPlaceholders(originalSql);
        for (int i = 1; i <= count; i++) {
            filled.putIfAbsent(i, null);
        }
        return super.executeQuery(JdbcMarshaling.substitutePlaceholders(originalSql, filled)).getMetaData();
    }

    // Helper method to build SQL with parameters
    private String buildSql() throws SQLException {
        // Inlined client-side (no server-side binding); escapes literals and skips '?' inside string literals.
        return JdbcMarshaling.bindPrepared(originalSql, parameters);
    }
}
