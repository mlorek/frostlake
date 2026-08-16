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

import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

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
        validateQueryId(queryId);

        final ResultSet result = resultCache.getResult(queryId);
        if (result == null) {
            // A statement that failed has an ID but never a result, and live says so in those words.
            if (resultCache.hasFailed(queryId)) {
                throw new RuntimeException("Query " + queryId + " has no result because it failed");
            }
            throw new RuntimeException("Query ID not found or result no longer available: " + queryId);
        }
        // A cached result's DECLARED types are its static types: the scan of a SHOW TABLES declares
        // created_on TIMESTAMP_LTZ(3), the text columns VARCHAR(16777216) and rows / bytes
        // NUMBER(38,0) on the account, and the scan of a SELECT keeps the query's own types — so
        // SYSTEM$TYPEOF and a table built over the scan see a type rather than nothing.
        final List<ResultSetColumn> declared = new ArrayList<>();
        for (final ResultSetColumn column : result.getColumns()) {
            declared.add(column.getStaticType() != null ? column
                : new ResultSetColumn(column.getName(), column.getDataType(), column.getTableName(),
                    column.getDataType(), column.isNullable(), column.isNullabilityKnown(),
                    column.getValueRange()));
        }
        return new ResultSet(declared, result.getRows());
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Not used for RESULT_SCAN
    }

    /**
     * Validate the query ID format
     * @param queryId The query ID to validate
     */
    private void validateQueryId(final String queryId) {
        if (queryId == null || queryId.trim().isEmpty()) {
            throw new RuntimeException("RESULT_SCAN requires a non-empty query ID");
        }

        // Snowflake query IDs are UUIDs
        // Format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        final String trimmed = queryId.trim();
        if (!trimmed.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new RuntimeException("Invalid query ID format: " + queryId);
        }
    }
}
