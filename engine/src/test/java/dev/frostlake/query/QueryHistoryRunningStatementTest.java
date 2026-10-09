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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The query history lists a statement while it runs — the one reading the history among the rows it reads —
 * under the ID the statement ends with, as RUNNING, with the start of the epoch for its end and the distance
 * back to it for its elapsed time; and it declares its 62 columns with the account's types. A statement that
 * failed reads FAILED_WITH_ERROR. Every answer is live-verified.
 */
public class QueryHistoryRunningStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void theListingDeclaresTheAccountsTypes() {
        assertShape(
            "QUERY_ID:VARCHAR(134217728), QUERY_TEXT:VARCHAR(134217728), DATABASE_NAME:VARCHAR(134217728), "
                + "SCHEMA_NAME:VARCHAR(134217728), QUERY_TYPE:VARCHAR(134217728), SESSION_ID:NUMBER(38,0), "
                + "AUTHN_EVENT_ID:NUMBER(38,0), USER_NAME:VARCHAR(134217728), USER_TYPE:VARCHAR(134217728), "
                + "USER_DATABASE_NAME:VARCHAR(134217728), USER_SCHEMA_NAME:VARCHAR(134217728), "
                + "ROLE_NAME:VARCHAR(134217728), WAREHOUSE_NAME:VARCHAR(134217728), "
                + "WAREHOUSE_SIZE:VARCHAR(134217728), WAREHOUSE_TYPE:VARCHAR(134217728), "
                + "CLUSTER_NUMBER:NUMBER(38,0), QUERY_TAG:VARCHAR(134217728), "
                + "EXECUTION_STATUS:VARCHAR(134217728), ERROR_CODE:NUMBER(38,0), "
                + "ERROR_MESSAGE:VARCHAR(134217728), START_TIME:TIMESTAMP_LTZ, END_TIME:TIMESTAMP_LTZ, "
                + "TOTAL_ELAPSED_TIME:NUMBER(38,0), BYTES_SCANNED:NUMBER(38,0), ROWS_PRODUCED:NUMBER(38,0), "
                + "COMPILATION_TIME:NUMBER(38,0), EXECUTION_TIME:NUMBER(38,0), "
                + "QUEUED_PROVISIONING_TIME:NUMBER(38,0), QUEUED_REPAIR_TIME:NUMBER(38,0), "
                + "QUEUED_OVERLOAD_TIME:NUMBER(38,0), TRANSACTION_BLOCKED_TIME:NUMBER(38,0), "
                + "OUTBOUND_DATA_TRANSFER_CLOUD:VARCHAR(134217728), "
                + "OUTBOUND_DATA_TRANSFER_REGION:VARCHAR(134217728), OUTBOUND_DATA_TRANSFER_BYTES:NUMBER(38,0), "
                + "INBOUND_DATA_TRANSFER_CLOUD:VARCHAR(134217728), "
                + "INBOUND_DATA_TRANSFER_REGION:VARCHAR(134217728), INBOUND_DATA_TRANSFER_BYTES:NUMBER(38,0), "
                + "CREDITS_USED_CLOUD_SERVICES:NUMBER(38,9), LIST_EXTERNAL_FILE_TIME:NUMBER(38,0), "
                + "RELEASE_VERSION:VARCHAR(134217728), EXTERNAL_FUNCTION_TOTAL_INVOCATIONS:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_SENT_ROWS:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_SENT_BYTES:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES:NUMBER(38,0), IS_CLIENT_GENERATED_STATEMENT:BOOLEAN, "
                + "QUERY_HASH:VARCHAR(134217728), QUERY_HASH_VERSION:NUMBER(38,0), "
                + "QUERY_PARAMETERIZED_HASH:VARCHAR(134217728), QUERY_PARAMETERIZED_HASH_VERSION:NUMBER(38,0), "
                + "TRANSACTION_ID:NUMBER(38,0), QUERY_ACCELERATION_BYTES_SCANNED:NUMBER(38,0), "
                + "QUERY_ACCELERATION_PARTITIONS_SCANNED:NUMBER(38,0), "
                + "QUERY_ACCELERATION_UPPER_LIMIT_SCALE_FACTOR:NUMBER(38,0), "
                + "BYTES_WRITTEN_TO_RESULT:NUMBER(38,0), ROWS_WRITTEN_TO_RESULT:NUMBER(38,0), "
                + "ROWS_INSERTED:NUMBER(38,0), QUERY_RETRY_TIME:NUMBER(38,0), "
                + "QUERY_RETRY_CAUSE:VARCHAR(134217728), FAULT_HANDLING_TIME:NUMBER(38,0), BIND_VALUES:VARIANT, "
                + "AGENT_TYPE:VARCHAR(134217728)",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) LIMIT 0");
        assertShape(
            "QUERY_ID:VARCHAR(134217728), QUERY_TEXT:VARCHAR(134217728), DATABASE_NAME:VARCHAR(134217728), "
                + "SCHEMA_NAME:VARCHAR(134217728), QUERY_TYPE:VARCHAR(134217728), SESSION_ID:NUMBER(38,0), "
                + "AUTHN_EVENT_ID:NUMBER(38,0), USER_NAME:VARCHAR(134217728), USER_TYPE:VARCHAR(134217728), "
                + "USER_DATABASE_NAME:VARCHAR(134217728), USER_SCHEMA_NAME:VARCHAR(134217728), "
                + "ROLE_NAME:VARCHAR(134217728), WAREHOUSE_NAME:VARCHAR(134217728), "
                + "WAREHOUSE_SIZE:VARCHAR(134217728), WAREHOUSE_TYPE:VARCHAR(134217728), "
                + "CLUSTER_NUMBER:NUMBER(38,0), QUERY_TAG:VARCHAR(134217728), "
                + "EXECUTION_STATUS:VARCHAR(134217728), ERROR_CODE:NUMBER(38,0), "
                + "ERROR_MESSAGE:VARCHAR(134217728), START_TIME:TIMESTAMP_LTZ, END_TIME:TIMESTAMP_LTZ, "
                + "TOTAL_ELAPSED_TIME:NUMBER(38,0), BYTES_SCANNED:NUMBER(38,0), ROWS_PRODUCED:NUMBER(38,0), "
                + "COMPILATION_TIME:NUMBER(38,0), EXECUTION_TIME:NUMBER(38,0), "
                + "QUEUED_PROVISIONING_TIME:NUMBER(38,0), QUEUED_REPAIR_TIME:NUMBER(38,0), "
                + "QUEUED_OVERLOAD_TIME:NUMBER(38,0), TRANSACTION_BLOCKED_TIME:NUMBER(38,0), "
                + "OUTBOUND_DATA_TRANSFER_CLOUD:VARCHAR(134217728), "
                + "OUTBOUND_DATA_TRANSFER_REGION:VARCHAR(134217728), OUTBOUND_DATA_TRANSFER_BYTES:NUMBER(38,0), "
                + "INBOUND_DATA_TRANSFER_CLOUD:VARCHAR(134217728), "
                + "INBOUND_DATA_TRANSFER_REGION:VARCHAR(134217728), INBOUND_DATA_TRANSFER_BYTES:NUMBER(38,0), "
                + "CREDITS_USED_CLOUD_SERVICES:NUMBER(38,9), LIST_EXTERNAL_FILE_TIME:NUMBER(38,0), "
                + "RELEASE_VERSION:VARCHAR(134217728), EXTERNAL_FUNCTION_TOTAL_INVOCATIONS:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_SENT_ROWS:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_SENT_BYTES:NUMBER(38,0), "
                + "EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES:NUMBER(38,0), IS_CLIENT_GENERATED_STATEMENT:BOOLEAN, "
                + "QUERY_HASH:VARCHAR(134217728), QUERY_HASH_VERSION:NUMBER(38,0), "
                + "QUERY_PARAMETERIZED_HASH:VARCHAR(134217728), QUERY_PARAMETERIZED_HASH_VERSION:NUMBER(38,0), "
                + "TRANSACTION_ID:NUMBER(38,0), QUERY_ACCELERATION_BYTES_SCANNED:NUMBER(38,0), "
                + "QUERY_ACCELERATION_PARTITIONS_SCANNED:NUMBER(38,0), "
                + "QUERY_ACCELERATION_UPPER_LIMIT_SCALE_FACTOR:NUMBER(38,0), "
                + "BYTES_WRITTEN_TO_RESULT:NUMBER(38,0), ROWS_WRITTEN_TO_RESULT:NUMBER(38,0), "
                + "ROWS_INSERTED:NUMBER(38,0), QUERY_RETRY_TIME:NUMBER(38,0), "
                + "QUERY_RETRY_CAUSE:VARCHAR(134217728), FAULT_HANDLING_TIME:NUMBER(38,0), BIND_VALUES:VARIANT, "
                + "AGENT_TYPE:VARCHAR(134217728)",
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY()) LIMIT 0");
    }

    @Test
    public void theStatementReadingTheHistoryIsListed() {
        assertAnswer(
            "true",
            "SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text "
                + "ILIKE '%QHBS_MARKER_ONE%'");
        assertAnswer(
            "RUNNING",
            "SELECT execution_status FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE "
                + "query_text ILIKE '%QHBS_MARKER_TWO%'");
        assertAnswer(
            "true",
            "SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY()) WHERE query_text ILIKE "
                + "'%QH_MARKER_THREE%'");
        assertAnswer(
            "false|false",
            "SELECT end_time IS NULL, total_elapsed_time IS NULL FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text ILIKE '%QHBS_MARKER_FOUR%'");
        assertAnswer(
            "true",
            "SELECT query_text ILIKE '%QHBS_MARKER_FIVE%' FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 1))");
        assertAnswer(
            "true|true|0|0|0|true",
            "SELECT total_elapsed_time = DATEDIFF('millisecond', start_time, end_time), total_elapsed_time < "
                + "0, execution_time, queued_overload_time, rows_inserted, error_code IS NULL FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text ILIKE '%QHBS_MARK_K%'");
    }

    @Test
    public void theRunningRowIsTheStatementsOwn() {
        assertAnswer(
            "RUNNING|0|false|null|null|0|SELECT|true|0|true|true",
            "SELECT execution_status, DATE_PART(epoch_second, end_time), total_elapsed_time >= 0, error_code, "
                + "error_message, rows_produced, query_type, start_time IS NOT NULL, bytes_scanned, "
                + "compilation_time >= 0, execution_time >= 0 FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text ILIKE '%QHBS_MARK_A%'");
        engine.execute("SET rid = (SELECT query_id FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE "
            + "query_text ILIKE '%QHBS_MARK_B%' LIMIT 1)");
        assertAnswer(
            "true",
            "SELECT $rid = LAST_QUERY_ID()");
        assertAnswer(
            "true",
            "SELECT COUNT(*) > 0 FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_USER()) WHERE query_text "
                + "ILIKE '%QHBU_MARK_C%'");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE execution_status "
                + "= 'RUNNING'");
        assertAnswer(
            "1",
            "SELECT (SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE "
                + "query_text ILIKE '%QHBS_MARK_E%')");
        engine.execute("CREATE OR REPLACE TABLE qh_t AS SELECT execution_status AS st FROM "
            + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE query_text ILIKE '%QHBS_MARK_F%'");
        assertAnswer(
            "RUNNING",
            "SELECT st FROM qh_t");
        assertAnswer(
            "REFUSED SQL compilation error: | Object 'QH_NO_SUCH_TABLE_G' does not exist or not authorized. "
                + "Your primary role <ROLE> must have at least one privilege granted on TABLE QH_NO_SUCH_TABLE_G.",
            "SELECT 1 FROM qh_no_such_table_g");
    }
}
