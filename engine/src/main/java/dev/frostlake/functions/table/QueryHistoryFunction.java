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

import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.QueryHistoryTracker;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Collections;
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

    private final QueryHistoryTracker tracker;

    public QueryHistoryFunction(final QueryHistoryTracker tracker) {
        super("QUERY_HISTORY");
        this.tracker = tracker;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // All arguments are optional.
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        return execute(Collections.<String, Object>emptyMap());
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        int limit = 100;
        final Object resultLimit = namedArgs.get("RESULT_LIMIT");
        if (resultLimit instanceof Number) {
            limit = ((Number) resultLimit).intValue();
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, columnType(name)));
        }
        final List<QueryHistory> history = new ArrayList<QueryHistory>(tracker.getAllHistory());
        Collections.reverse(history);   // most recent first, as in Snowflake
        final List<Row> rows = new ArrayList<Row>();
        for (final QueryHistory q : history) {
            if (rows.size() >= limit) {
                break;
            }
            final List<Object> values = new ArrayList<Object>(COLUMN_NAMES.length);
            for (final String name : COLUMN_NAMES) {
                values.add(columnValue(name, q));
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    private static DataType columnType(final String name) {
        switch (name) {
            case "TOTAL_ELAPSED_TIME":
            case "BYTES_SCANNED":
            case "ROWS_PRODUCED":
            case "COMPILATION_TIME":
            case "EXECUTION_TIME":
            case "ROWS_INSERTED":
                return NumericType.INTEGER;
            default:
                return StringType.VARCHAR;
        }
    }

    private static Object columnValue(final String name, final QueryHistory q) {
        switch (name) {
            case "QUERY_ID": return q.getQueryId();
            case "QUERY_TEXT": return q.getQueryText();
            case "DATABASE_NAME": return q.getDatabase();
            case "SCHEMA_NAME": return q.getSchema();
            case "QUERY_TYPE": return q.getQueryType();
            case "USER_NAME": return q.getUser();
            case "ROLE_NAME": return q.getRole();
            case "WAREHOUSE_NAME": return q.getWarehouse();
            case "EXECUTION_STATUS": return q.getStatus();
            case "ERROR_MESSAGE": return q.getErrorMessage();
            case "START_TIME": return q.getStartTimeFormatted();
            case "END_TIME": return q.getEndTimeFormatted();
            case "TOTAL_ELAPSED_TIME": return Long.valueOf(q.getExecutionTimeMs());
            case "EXECUTION_TIME": return Long.valueOf(q.getExecutionTimeMs());
            case "COMPILATION_TIME": return Long.valueOf(q.getCompilationTimeMs());
            case "ROWS_PRODUCED": return Long.valueOf(q.getRowsProduced());
            case "ROWS_INSERTED": return Long.valueOf(q.getRowsInserted());
            default: return null;
        }
    }
}
