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

package dev.frostlake.metastore;

import java.time.LocalDateTime;

public class QueryExecution {
    private final String queryId;
    private final String queryText;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final long executionTimeMs;
    private final long rowsProcessed;
    private final String status;

    public QueryExecution(final String queryId, final String queryText, final LocalDateTime startTime,
                        final LocalDateTime endTime, final long executionTimeMs, final long rowsProcessed, final String status) {
        this.queryId = queryId;
        this.queryText = queryText;
        this.startTime = startTime;
        this.endTime = endTime;
        this.executionTimeMs = executionTimeMs;
        this.rowsProcessed = rowsProcessed;
        this.status = status;
    }

    public String getQueryId() {
        return queryId;
    }

    public String getQueryText() {
        return queryText;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long rowsProcessed() {
        return rowsProcessed;
    }

    public String getStatus() {
        return status;
    }
}
