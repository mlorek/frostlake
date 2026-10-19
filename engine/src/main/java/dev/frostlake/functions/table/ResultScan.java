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

package dev.frostlake.functions.table;

import dev.frostlake.executor.ColumnLengthException;
import dev.frostlake.executor.DeferredFault;
import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StatementResults;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.StringNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RESULT_SCAN table function - retrieves cached query results by query ID.
 *
 * <pre>
 *   SELECT * FROM TABLE(RESULT_SCAN('&lt;query_id&gt;'))
 *   SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))
 * </pre>
 */
public class ResultScan extends TableFunction {

    private final QueryResultCache resultCache;

    public ResultScan(final QueryResultCache resultCache) {
        super("RESULT_SCAN");
        this.resultCache = resultCache;
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        // RESULT_SCAN doesn't use named arguments in Snowflake
        // It takes a single positional argument (query ID)
        throw new RuntimeException("RESULT_SCAN requires a query ID argument");
    }

    /**
     * Execute with positional argument (query ID)
     * @param queryId The query ID (UUID string)
     * @return ResultSet from the cached query
     */
    public ResultSet execute(final String queryId) {
        final ResultSet result = resultFor(queryId);
        if (result == null) {
            // A statement that failed has an ID but never a result, and live says so in those words.
            if (queryId != null && resultCache.hasFailed(queryId)) {
                throw new RuntimeException("Query " + queryId + " has no result because it failed");
            }
            throw new RuntimeException(notFound(queryId));
        }
        // A cached result's DECLARED types are its static types: the scan of a SHOW TABLES declares
        // created_on TIMESTAMP_LTZ(3), the text columns VARCHAR(16777216) and rows / bytes
        // NUMBER(38,0) on the account, and the scan of a SELECT keeps the query's own types — so
        // SYSTEM$TYPEOF and a table built over the scan see a type rather than nothing.
        final List<ResultSetColumn> declared = new ArrayList<>();
        for (final ResultSetColumn column : result.getColumns()) {
            declared.add(column.getStaticType() != null && column.getStaticType() != column.getDataType()
                    && column.getDataType() instanceof NumericType && column.getStaticType() instanceof NumericType
                // The scan declares the width the result itself reported, a rewritten SUM's included.
                ? new ResultSetColumn(column.getName(), column.getDataType(), column.getTableName(),
                    column.getDataType(), column.isNullable(), column.isNullabilityKnown(), column.getValueRange())
                : column.getStaticType() != null ? column
                : new ResultSetColumn(column.getName(), column.getDataType(), column.getTableName(),
                    column.getDataType(), column.isNullable(), column.isNullabilityKnown(),
                    column.getValueRange()));
        }
        return new ResultSet(declared, withinDeclaredWidths(declared, result.getRows()));
    }

    /**
     * The result a query ID names: the one a replayed statement first read in its place (see
     * {@link StatementResults}), else the cached one, which a statement the write-ahead log may keep notes as read.
     *
     * @param queryId the query ID, or null
     * @return the result, or null when there is none
     */
    public ResultSet resultFor(final String queryId) {
        final ResultSet replayed = StatementResults.replayed(queryId);
        if (replayed != null) {
            return replayed;
        }
        final ResultSet result = queryId == null ? null : resultCache.getResult(queryId);
        if (result != null) {
            StatementResults.record(queryId, result);
        }
        return result;
    }

    /**
     * A procedure's one answer held against the width its RETURNS declared. A handler may answer a text longer
     * than {@code RETURNS VARCHAR(2)} allows: the CALL hands it out, and the scan of that CALL refuses to read
     * it — {@code String 'abc' is too long and would be truncated} — where a count of the scan's rows, which
     * reads no value, answers (live-verified). The refusal waits in the cell for whatever reads it.
     */
    private static List<Row> withinDeclaredWidths(final List<ResultSetColumn> columns, final List<Row> rows) {
        if (columns.size() != 1 || rows == null || rows.size() != 1 || rows.get(0) == null) {
            return rows;
        }
        if (columns.get(0).getDataType() instanceof ObjectType) {
            return asObjects(rows);
        }
        if (!(columns.get(0).getDataType() instanceof StringType)) {
            return rows;
        }
        final int width = ((StringType) columns.get(0).getDataType()).getMaxLength();
        final Object answer = rows.get(0).getValues().isEmpty() ? null : rows.get(0).getValues().get(0);
        if (width <= 0 || !(answer instanceof String)
                || ((String) answer).codePointCount(0, ((String) answer).length()) <= width) {
            return rows;
        }
        final List<Row> held = new ArrayList<>();
        // A write over the scan raises it as it is, with no DML sentence around it (live-verified).
        held.add(new Row(DeferredFault.heldAnswer(new ColumnLengthException(width, (String) answer), answer, true)));
        return held;
    }

    /**
     * A procedure's one OBJECT answer held to its type. A JavaScript handler may answer what is no object for
     * {@code RETURNS OBJECT} — a Uint8Array's hex, a String object's text, a Date's timestamp — and the CALL hands
     * it out, while a statement that reads it from the CALL's scan is refused: {@code Failed to cast variant value
     * "01" to OBJECT} (live-verified). The refusal waits in the cell for whatever reads it.
     */
    private static List<Row> asObjects(final List<Row> rows) {
        final Object answer = rows.get(0).getValues().isEmpty() ? null : rows.get(0).getValues().get(0);
        final String text;
        if (answer instanceof VariantValue) {
            final VariantValue value = (VariantValue) answer;
            if (value.node() == null || value.node().isObject() || value.node().isNull()) {
                return rows;
            }
            text = VariantJsonText.clientTextOf(value);
        } else if (answer instanceof String) {
            text = StringNode.valueOf((String) answer).toString();
        } else {
            return rows;
        }
        final List<Row> held = new ArrayList<>();
        held.add(new Row(DeferredFault.heldAnswer(
            new RuntimeException("Failed to cast variant value " + text + " to OBJECT"), answer, false)));
        return held;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Not used for RESULT_SCAN
    }

    /**
     * The refusal for an id that names no statement. Live reads the id as it was given, whatever its
     * shape: {@code Statement 01b2c3d4-0000-0000-0000-000000000000 not found} for a well-formed id,
     * {@code Statement abc not found} for any other text, with its spaces kept and nothing between the
     * two words for an empty one, and {@code Statement NULL not found} when LAST_QUERY_ID answered NULL.
     *
     * @param queryId the id the scan was asked for, or null
     * @return the sentence
     */
    public static String notFound(final String queryId) {
        return "Statement " + (queryId == null ? "NULL" : queryId) + " not found";
    }
}
