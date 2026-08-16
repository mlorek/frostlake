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

package dev.frostlake;

import dev.frostlake.storage.ResultSet;
import java.util.List;

/**
 * Execution result container
 */
public class ExecutionResult {
    private final boolean success;
    private final List<ResultSet> resultSets;
    private final String errorMessage;
    private final String queryId;

    public ExecutionResult(final boolean success, final List<ResultSet> resultSets, final String errorMessage) {
        this(success, resultSets, errorMessage, null);
    }

    public ExecutionResult(final boolean success, final List<ResultSet> resultSets, final String errorMessage, final String queryId) {
        this.success = success;
        this.resultSets = resultSets;
        this.errorMessage = errorMessage;
        this.queryId = queryId;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<ResultSet> getResultSets() {
        return resultSets;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getQueryId() {
        return queryId;
    }

    /**
     * The total affected-row count this execution's DML statements report — each count grid marked by
     * the statement that built it, so a query whose columns merely carry the count names
     * ("number of rows inserted") counts nothing. Zero when no DML ran (DDL, SELECT, …).
     */
    public long getRowsAffected() {
        if (resultSets == null) {
            return 0;
        }
        long total = 0;
        for (final ResultSet rs : resultSets) {
            if (rs.getUpdateCount() != null) {
                total += rs.getUpdateCount().longValue();
            }
        }
        return total;
    }
}
