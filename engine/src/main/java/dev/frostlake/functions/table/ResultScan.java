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
        final ResultSet result = queryId == null ? null : resultCache.getResult(queryId);
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
