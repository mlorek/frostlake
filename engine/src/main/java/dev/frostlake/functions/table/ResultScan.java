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
            throw new RuntimeException("Query ID not found or result no longer available: " + queryId);
        }

        return result;
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
