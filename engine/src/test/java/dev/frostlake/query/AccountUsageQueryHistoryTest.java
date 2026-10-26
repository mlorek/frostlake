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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SNOWFLAKE database's ACCOUNT_USAGE.QUERY_HISTORY view, with the account's columns and no rows here, and the
 * database roles PUBLIC holds (all live-verified).
 */
public class AccountUsageQueryHistoryTest extends MeasuredCellsTestSupport {

    @Test
    public void accountUsageView() {
        assertAnswer("0", "SELECT COUNT(*) FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY WHERE 1 = 0");
        assertAnswer("QUERY_ID|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUERY_TEXT|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; DATABASE_ID|NUMBER(38,0)|"
                + "COLUMN|Y|null|N|N|null|null|null|null|null|null ;; DATABASE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|"
                + "N|null|null|null|null|null|null ;; SCHEMA_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; SCHEMA_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_TYPE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; SESSION_ID|"
                + "NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; AUTHN_EVENT_ID|NUMBER(38,0)|COLUMN|"
                + "Y|null|N|N|null|null|null|null|null|null ;; USER_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|"
                + "null|null|null|null|null ;; ROLE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|"
                + "null ;; WAREHOUSE_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "WAREHOUSE_NAME|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; WAREHOUSE_SIZE|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; WAREHOUSE_TYPE|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; CLUSTER_NUMBER|NUMBER(38,0)|"
                + "COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUERY_TAG|VARCHAR(16777216)|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null ;; EXECUTION_STATUS|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|"
                + "null|null|null|null ;; ERROR_CODE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|"
                + "null ;; ERROR_MESSAGE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "START_TIME|TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; END_TIME|"
                + "TIMESTAMP_LTZ(6)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; TOTAL_ELAPSED_TIME|NUMBER(38,"
                + "0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; BYTES_SCANNED|NUMBER(38,0)|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null ;; PERCENTAGE_SCANNED_FROM_CACHE|FLOAT|COLUMN|Y|null|N|N|null|null|"
                + "null|null|null|null ;; BYTES_WRITTEN|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; BYTES_WRITTEN_TO_RESULT|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BYTES_READ_FROM_RESULT|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "ROWS_PRODUCED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; ROWS_INSERTED|"
                + "NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; ROWS_UPDATED|NUMBER(38,0)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null ;; ROWS_DELETED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|"
                + "null|null|null|null ;; ROWS_UNLOADED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; BYTES_DELETED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "PARTITIONS_SCANNED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "PARTITIONS_TOTAL|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BYTES_SPILLED_TO_LOCAL_STORAGE|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BYTES_SPILLED_TO_REMOTE_STORAGE|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BYTES_SENT_OVER_THE_NETWORK|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "COMPILATION_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; EXECUTION_TIME|"
                + "NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUEUED_PROVISIONING_TIME|NUMBER(38,"
                + "0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUEUED_REPAIR_TIME|NUMBER(38,0)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null ;; QUEUED_OVERLOAD_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|"
                + "null|null|null|null|null ;; TRANSACTION_BLOCKED_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null ;; OUTBOUND_DATA_TRANSFER_CLOUD|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null ;; OUTBOUND_DATA_TRANSFER_REGION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null ;; OUTBOUND_DATA_TRANSFER_BYTES|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; INBOUND_DATA_TRANSFER_CLOUD|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; INBOUND_DATA_TRANSFER_REGION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; INBOUND_DATA_TRANSFER_BYTES|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|"
                + "null ;; LIST_EXTERNAL_FILES_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CREDITS_USED_CLOUD_SERVICES|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "RELEASE_VERSION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "EXTERNAL_FUNCTION_TOTAL_INVOCATIONS|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; EXTERNAL_FUNCTION_TOTAL_SENT_ROWS|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; EXTERNAL_FUNCTION_TOTAL_RECEIVED_ROWS|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|"
                + "null ;; EXTERNAL_FUNCTION_TOTAL_SENT_BYTES|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|"
                + "null ;; EXTERNAL_FUNCTION_TOTAL_RECEIVED_BYTES|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; QUERY_LOAD_PERCENT|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "IS_CLIENT_GENERATED_STATEMENT|BOOLEAN|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_ACCELERATION_BYTES_SCANNED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_ACCELERATION_PARTITIONS_SCANNED|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null "
                + ";; QUERY_ACCELERATION_UPPER_LIMIT_SCALE_FACTOR|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; TRANSACTION_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "CHILD_QUERIES_WAIT_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; ROLE_TYPE|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUERY_HASH|VARCHAR(16777216)|"
                + "COLUMN|Y|null|N|N|null|null|null|null|null|null ;; QUERY_HASH_VERSION|NUMBER(38,0)|COLUMN|Y|null|N|"
                + "N|null|null|null|null|null|null ;; QUERY_PARAMETERIZED_HASH|VARCHAR(16777216)|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null ;; QUERY_PARAMETERIZED_HASH_VERSION|NUMBER(38,0)|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null ;; SECONDARY_ROLE_STATS|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|"
                + "null|null|null|null|null ;; ROWS_WRITTEN_TO_RESULT|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|"
                + "null|null|null ;; QUERY_RETRY_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "QUERY_RETRY_CAUSE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "FAULT_HANDLING_TIME|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; USER_TYPE|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; USER_DATABASE_NAME|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; USER_DATABASE_ID|NUMBER(38,0)|"
                + "COLUMN|Y|null|N|N|null|null|null|null|null|null ;; USER_SCHEMA_NAME|VARCHAR(16777216)|COLUMN|Y|"
                + "null|N|N|null|null|null|null|null|null ;; USER_SCHEMA_ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|"
                + "null|null|null|null ;; RESOURCE_CONSTRAINT|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|"
                + "null|null ;; GENERATION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "BIND_VALUES|VARIANT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; AGENT_TYPE|"
                + "VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE VIEW SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY");
        assertAnswer("0", "SELECT COUNT(*) FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY WHERE QUERY_ID = 'x'");
        assertAnswer("", "SELECT QUERY_ID FROM SNOWFLAKE.ACCOUNT_USAGE.QUERY_HISTORY WHERE 1 = 0 ORDER BY START_TIME");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    /**
     * What every account answers alike in the listings of the modelled view and of the database roles PUBLIC holds:
     * the view's row past its creation time, and among PUBLIC's grants and CORE_VIEWER's grantees the ones the
     * account grants itself (all live-verified).
     */
    @Test
    public void listingsEveryAccountShares() {
        final String view = answer("SHOW VIEWS LIKE 'QUERY_HISTORY' IN SCHEMA SNOWFLAKE.ACCOUNT_USAGE");
        assertEquals("QUERY_HISTORY||SNOWFLAKE|ACCOUNT_USAGE||||true|false||OFF",
            view.substring(view.indexOf('|') + 1));
        final String grants = answer("SHOW GRANTS TO ROLE PUBLIC");
        final String[] held = {"ALERT_VIEWER", "ANACONDA_REPOSITORY_USER", "CLASSIFICATION_VIEWER", "COPILOT_USER",
            "CORE_VIEWER", "CORTEX_USER", "DATA_METRIC_USER", "DATA_PRIVACY_VIEWER", "ML_USER",
            "NOTIFICATION_VIEWER", "PYPI_REPOSITORY_USER", "SPCS_REGISTRY_VIEWER", "SPCS_VIEWER"};
        for (final String role : held) {
            assertTrue(grants.contains("|USAGE|DATABASE_ROLE|SNOWFLAKE." + role + "|ROLE|PUBLIC|false|"), role);
        }
        assertTrue(answer("SHOW GRANTS OF DATABASE ROLE SNOWFLAKE.CORE_VIEWER")
            .contains("|SNOWFLAKE.CORE_VIEWER|ROLE|PUBLIC|"));
    }
}
