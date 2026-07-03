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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a single query execution in the query history
 */
public class QueryHistory {
    private String queryId;
    private final String queryText;
    private final String database;
    private final String schema;
    private final String warehouse;
    private final String user;
    private final String role;
    private final LocalDateTime startTime;
    private LocalDateTime endTime;
    private QueryStatus status;
    private long executionTimeMs;
    private long rowsProduced;
    private long rowsInserted;
    private long rowsUpdated;
    private long rowsDeleted;
    private String errorMessage;
    private StatementKind queryType;
    private int compilationTimeMs;
    private int queuedTimeMs;

    public QueryHistory(final String queryText, final String database, final String schema, final String warehouse,
                       final String user, final String role) {
        // Query ID will be set later from QueryResultCache
        this.queryId = null;
        this.queryText = queryText;
        this.database = database;
        this.schema = schema;
        this.warehouse = warehouse;
        this.user = user;
        this.role = role;
        this.startTime = LocalDateTime.now();
        this.status = QueryStatus.RUNNING;
        this.executionTimeMs = 0;
        this.rowsProduced = 0;
        this.rowsInserted = 0;
        this.rowsUpdated = 0;
        this.rowsDeleted = 0;
        this.compilationTimeMs = 0;
        this.queuedTimeMs = 0;
        this.queryType = determineQueryType(queryText);
    }

    /**
     * Set the query ID (should be called with the ID from QueryResultCache)
     */
    public void setQueryId(final String queryId) {
        this.queryId = queryId;
    }

    private StatementKind determineQueryType(final String query) {
        if (query == null) return StatementKind.UNKNOWN;
        String upperQuery = query.trim().toUpperCase();

        if (upperQuery.startsWith("SELECT")) return StatementKind.SELECT;
        if (upperQuery.startsWith("INSERT")) return StatementKind.INSERT;
        if (upperQuery.startsWith("UPDATE")) return StatementKind.UPDATE;
        if (upperQuery.startsWith("DELETE")) return StatementKind.DELETE;
        if (upperQuery.startsWith("MERGE")) return StatementKind.MERGE;
        if (upperQuery.startsWith("COPY")) return StatementKind.COPY;
        if (upperQuery.startsWith("CREATE")) return StatementKind.CREATE;
        if (upperQuery.startsWith("DROP")) return StatementKind.DROP;
        if (upperQuery.startsWith("ALTER")) return StatementKind.ALTER;
        if (upperQuery.startsWith("TRUNCATE")) return StatementKind.TRUNCATE;
        if (upperQuery.startsWith("GRANT")) return StatementKind.GRANT;
        if (upperQuery.startsWith("REVOKE")) return StatementKind.REVOKE;
        if (upperQuery.startsWith("SHOW")) return StatementKind.SHOW;
        if (upperQuery.startsWith("DESCRIBE") || upperQuery.startsWith("DESC")) return StatementKind.DESCRIBE;
        if (upperQuery.startsWith("USE")) return StatementKind.USE;
        if (upperQuery.startsWith("BEGIN")) return StatementKind.BEGIN;
        if (upperQuery.startsWith("COMMIT")) return StatementKind.COMMIT;
        if (upperQuery.startsWith("ROLLBACK")) return StatementKind.ROLLBACK;
        if (upperQuery.startsWith("CALL")) return StatementKind.CALL;
        if (upperQuery.startsWith("WITH")) return StatementKind.SELECT; // CTE

        return StatementKind.OTHER;
    }

    public void markSuccess(final LocalDateTime endTime, final long rowsProduced) {
        this.endTime = endTime;
        this.status = QueryStatus.SUCCESS;
        this.rowsProduced = rowsProduced;
        this.executionTimeMs = Duration.between(startTime, endTime).toMillis();
    }

    public void markSuccess(final LocalDateTime endTime) {
        markSuccess(endTime, 0);
    }

    public void markFailed(final LocalDateTime endTime, final String errorMessage) {
        this.endTime = endTime;
        this.status = QueryStatus.FAILED;
        this.errorMessage = errorMessage;
        this.executionTimeMs = Duration.between(startTime, endTime).toMillis();
    }

    public void setRowsInserted(final long rowsInserted) {
        this.rowsInserted = rowsInserted;
    }

    public void setRowsUpdated(final long rowsUpdated) {
        this.rowsUpdated = rowsUpdated;
    }

    public void setRowsDeleted(final long rowsDeleted) {
        this.rowsDeleted = rowsDeleted;
    }

    public void setCompilationTimeMs(final int compilationTimeMs) {
        this.compilationTimeMs = compilationTimeMs;
    }

    public void setQueuedTimeMs(final int queuedTimeMs) {
        this.queuedTimeMs = queuedTimeMs;
    }

    // Getters
    public String getQueryId() {
        return queryId;
    }

    public String getQueryText() {
        return queryText;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public String getUser() {
        return user;
    }

    public String getRole() {
        return role;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public String getStartTimeFormatted() {
        return startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getEndTimeFormatted() {
        return endTime != null ? endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
    }

    public String getStatus() {
        return status.name();
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getRowsProduced() {
        return rowsProduced;
    }

    public long getRowsInserted() {
        return rowsInserted;
    }

    public long getRowsUpdated() {
        return rowsUpdated;
    }

    public long getRowsDeleted() {
        return rowsDeleted;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getQueryType() {
        return queryType.name();
    }

    public int getCompilationTimeMs() {
        return compilationTimeMs;
    }

    public int getQueuedTimeMs() {
        return queuedTimeMs;
    }

    @Override
    public String toString() {
        return "QueryHistory{" +
                "queryId='" + queryId + '\'' +
                ", queryType='" + queryType + '\'' +
                ", status='" + status + '\'' +
                ", executionTimeMs=" + executionTimeMs +
                ", rowsProduced=" + rowsProduced +
                '}';
    }
}
