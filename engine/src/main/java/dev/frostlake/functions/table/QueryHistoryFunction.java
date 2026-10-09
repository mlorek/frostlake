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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The INFORMATION_SCHEMA.QUERY_HISTORY() TABLE FUNCTION — Snowflake exposes query history ONLY this
 * way ({@code SELECT … FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY(RESULT_LIMIT => n))}; the bare
 * object form is "does not exist", live-verified). The column set mirrors Snowflake's (62 columns,
 * captured live); the engine populates the subset its tracker records and leaves the rest NULL.
 * Most recent queries first; RESULT_LIMIT defaults to 100 like Snowflake.
 */
public class QueryHistoryFunction extends TableFunction {

    /** Snowflake's live-captured column order. */
    private static final String[] COLUMN_NAMES = {
        "QUERY_ID", "QUERY_TEXT", "DATABASE_NAME", "SCHEMA_NAME", "QUERY_TYPE", "SESSION_ID",
        "AUTHN_EVENT_ID", "USER_NAME", "USER_TYPE", "USER_DATABASE_NAME", "USER_SCHEMA_NAME",
        "ROLE_NAME", "WAREHOUSE_NAME", "WAREHOUSE_SIZE", "WAREHOUSE_TYPE", "CLUSTER_NUMBER",
        "QUERY_TAG", "EXECUTION_STATUS", "ERROR_CODE", "ERROR_MESSAGE", "START_TIME", "END_TIME",
        "TOTAL_ELAPSED_TIME", "BYTES_SCANNED", "ROWS_PRODUCED", "COMPILATION_TIME",
        "EXECUTION_TIME", "QUEUED_PROVISIONING_TIME", "QUEUED_REPAIR_TIME", "QUEUED_OVERLOAD_TIME",
        "TRANSACTION_BLOCKED_TIME", "OUTBOUND_DATA_TRANSFER_CLOUD", "OUTBOUND_DATA_TRANSFER_REGION",
        "OUTBOUND_DATA_TRANSFER_BYTES", "INBOUND_DATA_TRANSFER_CLOUD", "INBOUND_DATA_TRANSFER_REGION",
        "INBOUND_DATA_TRANSFER_BYTES", "CREDITS_USED_CLOUD_SERVICES", "LIST_EXTERNAL_FILE_TIME",
        "RELEASE_VERSION", "EXTERNAL_FUNCTION_TOTAL_INVOCATIONS", "EXTERNAL_FUNCTION_TOTAL_SENT_ROWS",
        "EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS", "EXTERNAL_FUNCTION_TOTAL_SENT_BYTES",
        "EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES", "IS_CLIENT_GENERATED_STATEMENT", "QUERY_HASH",
        "QUERY_HASH_VERSION", "QUERY_PARAMETERIZED_HASH", "QUERY_PARAMETERIZED_HASH_VERSION",
        "TRANSACTION_ID", "QUERY_ACCELERATION_BYTES_SCANNED",
        "QUERY_ACCELERATION_PARTITIONS_SCANNED", "QUERY_ACCELERATION_UPPER_LIMIT_SCALE_FACTOR",
        "BYTES_WRITTEN_TO_RESULT", "ROWS_WRITTEN_TO_RESULT", "ROWS_INSERTED", "QUERY_RETRY_TIME",
        "QUERY_RETRY_CAUSE", "FAULT_HANDLING_TIME", "BIND_VALUES", "AGENT_TYPE"
    };

    /** The type the account declares its counts, times in milliseconds and ids with. */
    private static final NumericType WHOLE_NUMBER = new NumericType("NUMBER", 38, 0);

    /** CREDITS_USED_CLOUD_SERVICES' declared type on the account. */
    private static final NumericType CREDITS = new NumericType("NUMBER", 38, 9);

    /** The width the account declares every text column of the listing with. */
    private static final StringType TEXT = new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);

    /** What EXECUTION_STATUS reads for a statement that failed. */
    private static final String FAILED_STATUS = "FAILED_WITH_ERROR";

    private final QueryHistoryTracker tracker;
    private final QueryHistoryScope scope;
    private final QueryHistoryScopeDefaults defaults;

    public QueryHistoryFunction(final QueryHistoryTracker tracker) {
        this(tracker, QueryHistoryScope.ALL, null);
    }

    /**
     * One of the query-history listings: the unscoped one, or a scoped variant filtering by one session, user
     * or warehouse — the same columns and the same order, fewer rows.
     *
     * @param tracker  the statements recorded so far
     * @param scope    what the listing is limited to
     * @param defaults the caller's session, user and warehouse, read when the call names none; may be null
     *                 for the unscoped listing
     */
    public QueryHistoryFunction(final QueryHistoryTracker tracker, final QueryHistoryScope scope,
                                final QueryHistoryScopeDefaults defaults) {
        super(scope.functionName());
        this.tracker = tracker;
        this.scope = scope;
        this.defaults = defaults;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // All arguments are optional.
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        // A scoped listing's leading positional argument is the value it filters by.
        final Map<String, Object> named = new HashMap<String, Object>();
        if (scope.parameter() != null && positionalArgs != null && !positionalArgs.isEmpty()) {
            named.put(scope.parameter(), positionalArgs.get(0));
        }
        return execute(named);
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final Object filter = scope.parameter() == null ? null : scopeValue(namedArgs.get(scope.parameter()));
        int limit = 100;
        final Object resultLimit = namedArgs.get("RESULT_LIMIT");
        if (resultLimit instanceof Number) {
            limit = ((Number) resultLimit).intValue();
        }
        final List<ResultSetColumn> columns = columns();
        // The statements still running are listed too, the one reading the listing among them; they began after
        // every statement that has ended, so they come last here and first once reversed.
        final List<QueryHistory> history = new ArrayList<QueryHistory>(tracker.getAllHistory());
        history.addAll(tracker.getRunning());
        Collections.reverse(history);   // most recent first, as in Snowflake
        final List<Row> rows = new ArrayList<Row>();
        for (final QueryHistory q : history) {
            if (rows.size() >= limit) {
                break;
            }
            if (scope.parameter() != null && !inScope(q, filter)) {
                continue;
            }
            final List<Object> values = new ArrayList<Object>(COLUMN_NAMES.length);
            for (final String name : COLUMN_NAMES) {
                values.add(columnValue(name, q));
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    /** The listing's columns are fixed, so a query over it is planned with their types before any row is read. */
    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    private static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMN_NAMES) {
            final DataType type = columnType(name);
            // The time columns declare their type too, so SYSTEM$TYPEOF and the type rules read TIMESTAMP_LTZ(3).
            columns.add(type == ShowResultHelpers.CREATED_ON ? new ResultSetColumn(name, type, null, type)
                : new ResultSetColumn(name, type));
        }
        return columns;
    }

    /**
     * The value a scoped listing filters by: the one the call names, or the caller's own when it names none.
     * A session is compared by its number, a user or warehouse by its name.
     */
    private Object scopeValue(final Object given) {
        if (given != null) {
            return given;
        }
        if (defaults == null) {
            return null;
        }
        switch (scope) {
            case SESSION: return defaults.currentSession();
            case USER: return defaults.currentUser();
            case WAREHOUSE: return defaults.currentWarehouse();
            default: return null;
        }
    }

    /** Whether one recorded statement falls inside the scope's value. */
    private boolean inScope(final QueryHistory q, final Object filter) {
        if (filter == null) {
            return false;
        }
        switch (scope) {
            case SESSION:
                return q.getSessionId() != null && sameNumber(q.getSessionId(), filter);
            case USER:
                return q.getUser() != null && q.getUser().equalsIgnoreCase(String.valueOf(filter));
            case WAREHOUSE:
                return q.getWarehouse() != null && q.getWarehouse().equalsIgnoreCase(String.valueOf(filter));
            default:
                return true;
        }
    }

    /** A session number against whatever the call passed for it — a number, or its text. */
    private static boolean sameNumber(final Long session, final Object filter) {
        if (filter instanceof Number) {
            return session.longValue() == ((Number) filter).longValue();
        }
        try {
            return session.longValue() == Long.parseLong(String.valueOf(filter).trim());
        } catch (final NumberFormatException notANumber) {
            return false;
        }
    }

    private static DataType columnType(final String name) {
        switch (name) {
            case "SESSION_ID":
            case "AUTHN_EVENT_ID":
            case "CLUSTER_NUMBER":
            case "ERROR_CODE":
            case "TOTAL_ELAPSED_TIME":
            case "BYTES_SCANNED":
            case "ROWS_PRODUCED":
            case "COMPILATION_TIME":
            case "EXECUTION_TIME":
            case "QUEUED_PROVISIONING_TIME":
            case "QUEUED_REPAIR_TIME":
            case "QUEUED_OVERLOAD_TIME":
            case "TRANSACTION_BLOCKED_TIME":
            case "OUTBOUND_DATA_TRANSFER_BYTES":
            case "INBOUND_DATA_TRANSFER_BYTES":
            case "LIST_EXTERNAL_FILE_TIME":
            case "EXTERNAL_FUNCTION_TOTAL_INVOCATIONS":
            case "EXTERNAL_FUNCTION_TOTAL_SENT_ROWS":
            case "EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS":
            case "EXTERNAL_FUNCTION_TOTAL_SENT_BYTES":
            case "EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES":
            case "QUERY_HASH_VERSION":
            case "QUERY_PARAMETERIZED_HASH_VERSION":
            case "TRANSACTION_ID":
            case "QUERY_ACCELERATION_BYTES_SCANNED":
            case "QUERY_ACCELERATION_PARTITIONS_SCANNED":
            case "QUERY_ACCELERATION_UPPER_LIMIT_SCALE_FACTOR":
            case "BYTES_WRITTEN_TO_RESULT":
            case "ROWS_WRITTEN_TO_RESULT":
            case "ROWS_INSERTED":
            case "QUERY_RETRY_TIME":
            case "FAULT_HANDLING_TIME":
                // The session's number is what CURRENT_SESSION() answers, typed as the account types it.
                return WHOLE_NUMBER;
            case "CREDITS_USED_CLOUD_SERVICES":
                return CREDITS;
            case "IS_CLIENT_GENERATED_STATEMENT":
                return BooleanType.BOOLEAN;
            case "BIND_VALUES":
                return VariantType.VARIANT;
            case "START_TIME":
            case "END_TIME":
                // TIMESTAMP_LTZ, as the documentation types both, at the millisecond the account's other time
                // cells carry: an instant that reads in the session's zone and compares as one.
                return ShowResultHelpers.CREATED_ON;
            default:
                return TEXT;
        }
    }

    /**
     * One cell of a statement's row. A statement still running has not ended: its END_TIME is the start of the
     * epoch, its elapsed time the distance back to it from the statement's start, and it has produced no row
     * (live-verified).
     */
    private static Object columnValue(final String name, final QueryHistory q) {
        switch (name) {
            case "QUERY_ID": return q.getQueryId();
            case "QUERY_TEXT": return q.getQueryText();
            case "DATABASE_NAME": return q.getDatabase();
            case "SCHEMA_NAME": return q.getSchema();
            case "QUERY_TYPE": return q.getQueryType();
            case "SESSION_ID": return q.getSessionId();
            case "USER_NAME": return q.getUser();
            case "ROLE_NAME": return q.getRole();
            case "WAREHOUSE_NAME": return q.getWarehouse();
            case "EXECUTION_STATUS": return "FAILED".equals(q.getStatus()) ? FAILED_STATUS : q.getStatus();
            case "ERROR_MESSAGE": return q.getErrorMessage();
            case "START_TIME": return ShowResultHelpers.createdOn(q.getStartInstant());
            case "END_TIME":
                return ShowResultHelpers.createdOn(q.getEndInstant() != null ? q.getEndInstant() : Instant.EPOCH);
            case "TOTAL_ELAPSED_TIME":
                return Long.valueOf(q.getEndInstant() != null || q.getStartInstant() == null ? q.getExecutionTimeMs()
                    : -q.getStartInstant().toEpochMilli());
            case "BYTES_SCANNED": return q.getEndInstant() != null ? null : Long.valueOf(0L);
            case "QUEUED_PROVISIONING_TIME":
            case "QUEUED_REPAIR_TIME":
            case "QUEUED_OVERLOAD_TIME":
            case "TRANSACTION_BLOCKED_TIME":
                // No statement waits here: nothing queues one and no lock holds one back.
                return Long.valueOf(0L);
            case "EXECUTION_TIME": return Long.valueOf(q.getExecutionTimeMs());
            case "COMPILATION_TIME": return Long.valueOf(q.getCompilationTimeMs());
            case "ROWS_PRODUCED": return Long.valueOf(q.getRowsProduced());
            case "ROWS_INSERTED": return Long.valueOf(q.getRowsInserted());
            default: return null;
        }
    }
}
