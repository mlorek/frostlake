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

import dev.frostlake.storage.SnapshotSequence;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
    // When the statement's changes became visible to other readers: its own completion under autocommit, or
    // the COMMIT of the explicit transaction it ran in. Null until known, and for a transaction still open.
    private volatile LocalDateTime visibleFrom;
    /** The latest snapshot taken when the statement began, in the order of {@link SnapshotSequence}. */
    private final long startSequence;
    /** The latest snapshot taken once the statement's changes became visible; -1 while they are not. */
    private volatile long visibleSequence = -1L;
    /** The query ID the write-ahead log holds the statement under, or null while it holds it not. */
    private volatile String loggedAs;
    private QueryStatus status;
    private long executionTimeMs;
    private long rowsProduced;
    private long rowsInserted;
    private long rowsUpdated;
    private long rowsDeleted;
    private String errorMessage;
    /** The code of the error the statement failed with — QUERY_HISTORY's ERROR_CODE — or null. */
    private Integer errorCode;
    private StatementKind queryType;
    /** The kind QUERY_HISTORY lists, in the account's words, or null while unknown — see {@link #getQueryTypeName}. */
    private String queryTypeName;
    /** The rows the statement answered: QUERY_HISTORY's ROWS_WRITTEN_TO_RESULT. */
    private long rowsWrittenToResult;
    /** The transaction the statement ran in, or 0 — see {@link #getTransactionId}. */
    private long transactionId;
    /** The size of the warehouse that ran the statement, or null when none did — see {@link #getWarehouseSize}. */
    private String warehouseSize;
    private int compilationTimeMs;
    private int queuedTimeMs;
    /** The number of the session that ran the statement — QUERY_HISTORY's SESSION_ID — or null. */
    private Long sessionId;
    /** Whether the statement was an ALTER DYNAMIC TABLE … REFRESH; see {@link #markDynamicTableRefresh}. */
    private boolean dynamicTableRefresh;

    public QueryHistory(final String queryText, final String database, final String schema, final String warehouse,
                       final String user, final String role) {
        this.startSequence = SnapshotSequence.current();
        // Query ID will be set later from QueryResultCache
        this.queryId = null;
        this.queryText = queryText;
        this.database = database;
        this.schema = schema;
        this.warehouse = warehouse;
        this.user = user;
        this.role = role;
        // UTC wall time, the engine's session zone — AT(STATEMENT => …) time travel compares this
        // against UTC-domain snapshot instants.
        this.startTime = LocalDateTime.now(ZoneOffset.UTC);
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

    private QueryHistory(final String queryId, final StatementKind queryType, final LocalDateTime startTime,
                         final LocalDateTime endTime, final LocalDateTime visibleFrom, final long startSequence,
                         final long visibleSequence) {
        this.queryId = queryId;
        this.queryText = null;
        this.database = null;
        this.schema = null;
        this.warehouse = null;
        this.user = null;
        this.role = null;
        this.startTime = startTime;
        this.endTime = endTime;
        this.visibleFrom = visibleFrom;
        this.startSequence = startSequence;
        this.visibleSequence = visibleSequence;
        this.status = QueryStatus.SUCCESS;
        this.queryType = queryType;
    }

    /**
     * A statement a former engine ran, as a restored state keeps it: only what a time-travel point naming its query ID
     * reads, under the places the restored state gives it in the order snapshots are taken in.
     *
     * @param queryId             the query ID it answered with
     * @param queryType           its kind, as {@link #getQueryType} names it
     * @param startTime           when it began, a UTC wall clock
     * @param endTime             when it ended, or null
     * @param visibleFrom         when its changes became visible, or null
     * @param startSequence       the place it began at
     * @param visibleSequence     the place its changes became visible at, or -1
     * @param dynamicTableRefresh whether it refreshed a dynamic table
     * @param loggedAs            the query ID the write-ahead log holds it under, or null
     * @return the statement
     */
    public static QueryHistory restoredPoint(final String queryId, final String queryType,
                                             final LocalDateTime startTime, final LocalDateTime endTime,
                                             final LocalDateTime visibleFrom, final long startSequence,
                                             final long visibleSequence, final boolean dynamicTableRefresh,
                                             final String loggedAs) {
        StatementKind kind = StatementKind.UNKNOWN;
        if (queryType != null) {
            try {
                kind = StatementKind.valueOf(queryType);
            } catch (final IllegalArgumentException unknown) {
                kind = StatementKind.UNKNOWN;
            }
        }
        final QueryHistory point = new QueryHistory(queryId, kind, startTime, endTime, visibleFrom, startSequence,
            visibleSequence);
        point.dynamicTableRefresh = dynamicTableRefresh;
        point.loggedAs = loggedAs;
        return point;
    }

    /** The number of the session that ran the statement, or null when none was known. */
    public Long getSessionId() {
        return sessionId;
    }

    /** Record the session that ran the statement. */
    public void setSessionId(final Long sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Set the query ID (should be called with the ID from QueryResultCache)
     */
    public void setQueryId(final String queryId) {
        this.queryId = queryId;
    }

    /** Set the kind from the PARSED statement — the constructor's text sniff stands only for
     *  statements the parser refused. */
    public void setQueryType(final StatementKind queryType) {
        this.queryType = queryType;
    }

    private StatementKind determineQueryType(final String query) {
        if (query == null) return StatementKind.UNKNOWN;
        final String upperQuery = query.trim().toUpperCase(Locale.ROOT);

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

    /**
     * Record that the statement completed.
     *
     * @param endTime  when it did
     * @param answered the rows it answered, which it produced too unless {@link #setRowsProduced} says otherwise
     */
    public void markSuccess(final LocalDateTime endTime, final long answered) {
        this.endTime = endTime;
        this.status = QueryStatus.SUCCESS;
        this.rowsProduced = answered;
        this.rowsWrittenToResult = answered;
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

    /**
     * Record that the statement failed, with the code of its error.
     *
     * @param endTime      when it did
     * @param errorMessage the error's sentence
     * @param errorCode    the error's code
     */
    public void markFailed(final LocalDateTime endTime, final String errorMessage, final int errorCode) {
        markFailed(endTime, errorMessage);
        this.errorCode = Integer.valueOf(errorCode);
    }

    /** The code of the error the statement failed with, or null when it did not fail. */
    public Integer getErrorCode() {
        return errorCode;
    }

    /**
     * The kind QUERY_HISTORY lists the statement under, in the account's own words: CREATE_TABLE,
     * ALTER_TABLE_ADD_COLUMN, SET, … — UNKNOWN for one the parser could not read.
     *
     * @return the kind
     */
    public String getQueryTypeName() {
        return queryTypeName != null ? queryTypeName : "UNKNOWN";
    }

    /** Record the kind QUERY_HISTORY lists the statement under. */
    public void setQueryTypeName(final String queryTypeName) {
        this.queryTypeName = queryTypeName;
    }

    /** The rows the statement answered: none for one that failed. */
    public long getRowsWrittenToResult() {
        return rowsWrittenToResult;
    }

    /** Record the rows the statement produced, where they are not the rows it answered. */
    public void setRowsProduced(final long rowsProduced) {
        this.rowsProduced = rowsProduced;
    }

    /**
     * The public id of the transaction the statement ran in: the explicit one it ran inside, or the one a write
     * under autocommit ran as; 0 for a statement that ran in none.
     *
     * @return the id, or 0
     */
    public long getTransactionId() {
        return transactionId;
    }

    /** Record the transaction the statement ran in. */
    public void setTransactionId(final long transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * The size of the warehouse that ran the statement, as SHOW WAREHOUSES spells it; null for a statement the
     * account answers without one — a DDL statement, a SHOW, a query that reads no table.
     *
     * @return the size, or null
     */
    public String getWarehouseSize() {
        return warehouseSize;
    }

    /** Record the size of the warehouse that ran the statement. */
    public void setWarehouseSize(final String warehouseSize) {
        this.warehouseSize = warehouseSize;
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

    /**
     * When the statement started, as the instant it is: the start is stamped as a UTC wall clock.
     *
     * @return the instant
     */
    public Instant getStartInstant() {
        return startTime.toInstant(ZoneOffset.UTC);
    }

    /**
     * When this statement's changes became visible: what time travel's {@code AT(STATEMENT => id)} reads at.
     *
     * @return the instant, or null while it is unknown
     */
    public LocalDateTime getVisibleFrom() {
        return visibleFrom;
    }

    public void setVisibleFrom(final LocalDateTime visibleFrom) {
        this.visibleFrom = visibleFrom;
    }

    /**
     * The latest snapshot taken when the statement began: BEFORE(STATEMENT) reads the table as it stood there.
     *
     * @return the snapshot sequence number
     */
    public long getStartSequence() {
        return startSequence;
    }

    /**
     * The latest snapshot taken once the statement's changes became visible: AT(STATEMENT) reads the table as it
     * stood there, the statement's own writes included.
     *
     * @return the snapshot sequence number, or -1 while the changes are not visible
     */
    public long getVisibleSequence() {
        return visibleSequence;
    }

    /** Records that the statement's changes are visible as of the latest snapshot taken now. */
    public void markVisibleSequence() {
        this.visibleSequence = SnapshotSequence.current();
    }

    /**
     * Records that the write-ahead log holds the statement, so a restart's replay runs it again: under the statement's
     * own query ID, or, for a replay, under the ID the statement first answered with, which the log keeps.
     *
     * @param queryId the ID the log holds the statement under
     */
    public void markLogged(final String queryId) {
        this.loggedAs = queryId;
    }

    /**
     * Whether the write-ahead log holds the statement.
     *
     * @return whether it was logged
     */
    public boolean isLogged() {
        return loggedAs != null;
    }

    /**
     * The query ID the write-ahead log holds the statement under, which a replay anchors its own run to.
     *
     * @return the ID, or null when the log holds it not
     */
    public String getLoggedAs() {
        return loggedAs;
    }

    /**
     * Records that the statement refreshed a dynamic table. Unlike any other ALTER, such a statement anchors time
     * travel, and at its start for AT as for BEFORE: the refresh it asked for is not part of the point
     * (live-verified).
     */
    public void markDynamicTableRefresh() {
        this.dynamicTableRefresh = true;
    }

    /**
     * Whether the statement refreshed a dynamic table (see {@link #markDynamicTableRefresh}).
     *
     * @return whether it did
     */
    public boolean isDynamicTableRefresh() {
        return dynamicTableRefresh;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getEndTimeFormatted() {
        return endTime != null ? endTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null;
    }

    /**
     * When the statement ended, as the instant it is, or null while it runs: the end is stamped as a UTC wall clock.
     *
     * @return the instant, or null
     */
    public Instant getEndInstant() {
        return endTime != null ? endTime.toInstant(ZoneOffset.UTC) : null;
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
