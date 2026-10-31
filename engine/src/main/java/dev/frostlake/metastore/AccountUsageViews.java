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

import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import java.util.ArrayList;
import java.util.List;

/**
 * The views of the SNOWFLAKE database's ACCOUNT_USAGE schema the engine models: QUERY_HISTORY, a secure view no role
 * owns, with the account's columns in the account's order and types (live-verified). The account fills it with up to
 * three quarters of an hour's latency; here it answers no rows.
 */
final class AccountUsageViews {

    /** QUERY_HISTORY's columns and their declared types. */
    private static final String[][] QUERY_HISTORY = {
        {"QUERY_ID", "VARCHAR(16777216)"}, {"QUERY_TEXT", "VARCHAR(16777216)"}, {"DATABASE_ID", "NUMBER(38,0)"},
        {"DATABASE_NAME", "VARCHAR(16777216)"}, {"SCHEMA_ID", "NUMBER(38,0)"}, {"SCHEMA_NAME", "VARCHAR(16777216)"},
        {"QUERY_TYPE", "VARCHAR(16777216)"}, {"SESSION_ID", "NUMBER(38,0)"}, {"AUTHN_EVENT_ID", "NUMBER(38,0)"},
        {"USER_NAME", "VARCHAR(16777216)"}, {"ROLE_NAME", "VARCHAR(16777216)"}, {"WAREHOUSE_ID", "NUMBER(38,0)"},
        {"WAREHOUSE_NAME", "VARCHAR(16777216)"}, {"WAREHOUSE_SIZE", "VARCHAR(16777216)"},
        {"WAREHOUSE_TYPE", "VARCHAR(16777216)"}, {"CLUSTER_NUMBER", "NUMBER(38,0)"}, {"QUERY_TAG", "VARCHAR(16777216)"},
        {"EXECUTION_STATUS", "VARCHAR(16777216)"}, {"ERROR_CODE", "VARCHAR(16777216)"},
        {"ERROR_MESSAGE", "VARCHAR(16777216)"}, {"START_TIME", "TIMESTAMP_LTZ(6)"}, {"END_TIME", "TIMESTAMP_LTZ(6)"},
        {"TOTAL_ELAPSED_TIME", "NUMBER(38,0)"}, {"BYTES_SCANNED", "NUMBER(38,0)"},
        {"PERCENTAGE_SCANNED_FROM_CACHE", "FLOAT"}, {"BYTES_WRITTEN", "NUMBER(38,0)"},
        {"BYTES_WRITTEN_TO_RESULT", "NUMBER(38,0)"}, {"BYTES_READ_FROM_RESULT", "NUMBER(38,0)"},
        {"ROWS_PRODUCED", "NUMBER(38,0)"}, {"ROWS_INSERTED", "NUMBER(38,0)"}, {"ROWS_UPDATED", "NUMBER(38,0)"},
        {"ROWS_DELETED", "NUMBER(38,0)"}, {"ROWS_UNLOADED", "NUMBER(38,0)"}, {"BYTES_DELETED", "NUMBER(38,0)"},
        {"PARTITIONS_SCANNED", "NUMBER(38,0)"}, {"PARTITIONS_TOTAL", "NUMBER(38,0)"},
        {"BYTES_SPILLED_TO_LOCAL_STORAGE", "NUMBER(38,0)"}, {"BYTES_SPILLED_TO_REMOTE_STORAGE", "NUMBER(38,0)"},
        {"BYTES_SENT_OVER_THE_NETWORK", "NUMBER(38,0)"}, {"COMPILATION_TIME", "NUMBER(38,0)"},
        {"EXECUTION_TIME", "NUMBER(38,0)"}, {"QUEUED_PROVISIONING_TIME", "NUMBER(38,0)"},
        {"QUEUED_REPAIR_TIME", "NUMBER(38,0)"}, {"QUEUED_OVERLOAD_TIME", "NUMBER(38,0)"},
        {"TRANSACTION_BLOCKED_TIME", "NUMBER(38,0)"}, {"OUTBOUND_DATA_TRANSFER_CLOUD", "VARCHAR(16777216)"},
        {"OUTBOUND_DATA_TRANSFER_REGION", "VARCHAR(16777216)"}, {"OUTBOUND_DATA_TRANSFER_BYTES", "NUMBER(38,0)"},
        {"INBOUND_DATA_TRANSFER_CLOUD", "VARCHAR(16777216)"}, {"INBOUND_DATA_TRANSFER_REGION", "VARCHAR(16777216)"},
        {"INBOUND_DATA_TRANSFER_BYTES", "NUMBER(38,0)"}, {"LIST_EXTERNAL_FILES_TIME", "NUMBER(38,0)"},
        {"CREDITS_USED_CLOUD_SERVICES", "FLOAT"}, {"RELEASE_VERSION", "VARCHAR(16777216)"},
        {"EXTERNAL_FUNCTION_TOTAL_INVOCATIONS", "NUMBER(38,0)"}, {"EXTERNAL_FUNCTION_TOTAL_SENT_ROWS", "NUMBER(38,0)"},
        {"EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS", "NUMBER(38,0)"},
        {"EXTERNAL_FUNCTION_TOTAL_SENT_BYTES", "NUMBER(38,0)"},
        {"EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES", "NUMBER(38,0)"}, {"QUERY_LOAD_PERCENT", "NUMBER(38,0)"},
        {"IS_CLIENT_GENERATED_STATEMENT", "BOOLEAN"}, {"QUERY_ACCELERATION_BYTES_SCANNED", "NUMBER(38,0)"},
        {"QUERY_ACCELERATION_PARTITIONS_SCANNED", "NUMBER(38,0)"},
        {"QUERY_ACCELERATION_UPPER_LIMIT_SCALE_FACTOR", "NUMBER(38,0)"}, {"TRANSACTION_ID", "NUMBER(38,0)"},
        {"CHILD_QUERIES_WAIT_TIME", "NUMBER(38,0)"}, {"ROLE_TYPE", "VARCHAR(16777216)"},
        {"QUERY_HASH", "VARCHAR(16777216)"}, {"QUERY_HASH_VERSION", "NUMBER(38,0)"},
        {"QUERY_PARAMETERIZED_HASH", "VARCHAR(16777216)"}, {"QUERY_PARAMETERIZED_HASH_VERSION", "NUMBER(38,0)"},
        {"SECONDARY_ROLE_STATS", "VARCHAR(16777216)"}, {"ROWS_WRITTEN_TO_RESULT", "NUMBER(38,0)"},
        {"QUERY_RETRY_TIME", "NUMBER(38,0)"}, {"QUERY_RETRY_CAUSE", "VARCHAR(16777216)"},
        {"FAULT_HANDLING_TIME", "NUMBER(38,0)"}, {"USER_TYPE", "VARCHAR(16777216)"},
        {"USER_DATABASE_NAME", "VARCHAR(16777216)"}, {"USER_DATABASE_ID", "NUMBER(38,0)"},
        {"USER_SCHEMA_NAME", "VARCHAR(16777216)"}, {"USER_SCHEMA_ID", "NUMBER(38,0)"},
        {"RESOURCE_CONSTRAINT", "VARCHAR(16777216)"}, {"GENERATION", "VARCHAR(16777216)"}, {"BIND_VALUES", "VARIANT"},
        {"AGENT_TYPE", "VARCHAR(16777216)"}
    };

    private AccountUsageViews() {
    }

    /**
     * Registers the modelled views in the ACCOUNT_USAGE schema: owned by nobody, and secure. A text column is
     * declared at the default width, as DESCRIBE VIEW reads it, and answers at the full text width, as a query's
     * columns do (live-verified).
     *
     * @param accountUsage the SNOWFLAKE database's ACCOUNT_USAGE schema
     */
    static void seed(final Schema accountUsage) {
        final StringBuilder definition = new StringBuilder("SELECT ");
        final List<TableColumn> columns = new ArrayList<>();
        for (int i = 0; i < QUERY_HISTORY.length; i++) {
            final String declared = QUERY_HISTORY[i][1];
            definition.append(i == 0 ? "" : ", ").append("CAST(NULL AS ")
                .append(declared.startsWith("VARCHAR") ? "VARCHAR" : declared).append(") AS ")
                .append(QUERY_HISTORY[i][0]);
            columns.add(new TableColumn(QUERY_HISTORY[i][0], typeOf(declared), true, null, false, false, false));
        }
        definition.append(" WHERE FALSE");
        final View view = new View("QUERY_HISTORY", definition.toString());
        view.setOwner(null);
        view.setSecure(true);
        view.setResolvedColumns(columns);
        accountUsage.addView(view);
    }

    /** The type a declared spelling of the list names. */
    private static DataType typeOf(final String declared) {
        switch (declared) {
            case "VARCHAR(16777216)":
                return new StringType("VARCHAR", 16777216);
            case "NUMBER(38,0)":
                return new NumericType("NUMBER", 38, 0);
            case "TIMESTAMP_LTZ(6)":
                return new DateTimeType("TIMESTAMP_LTZ", 6, true);
            case "FLOAT":
                return NumericType.FLOAT;
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "VARIANT":
                return VariantType.VARIANT;
            default:
                throw new IllegalStateException("no type for " + declared);
        }
    }
}
