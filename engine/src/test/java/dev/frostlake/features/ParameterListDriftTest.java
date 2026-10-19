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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * SHOW PARAMETERS lists the names and defaults the account lists for each scope: the account's own parameters, the
 * session's JavaScript result format, a warehouse's stable path and fallback warehouse, and none a database no longer
 * carries (live-verified).
 */
public class ParameterListDriftTest extends MeasuredCellsTestSupport {

    private static final String ROWS = "SELECT \"key\", \"default\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
        + " ORDER BY 1";

    @Test
    public void theAccountListsItsOwnParametersAndNotTheSessionsRetiredOne() {
        answer("SHOW PARAMETERS LIKE 'FEATURE_COMMENT_PROPAGATION' IN ACCOUNT");
        assertAnswer("FEATURE_COMMENT_PROPAGATION|DISABLED|STRING", ROWS);
        answer("SHOW PARAMETERS LIKE 'SNOWFLAKE_LEARNING_ENVIRONMENT_OPT_OPTIONS' IN ACCOUNT");
        assertAnswer("SNOWFLAKE_LEARNING_ENVIRONMENT_OPT_OPTIONS|unset|STRING", ROWS);
        answer("SHOW PARAMETERS LIKE 'UI_ENABLE_TUTORIALS_PAGE' IN ACCOUNT");
        assertAnswer("UI_ENABLE_TUTORIALS_PAGE|false|BOOLEAN", ROWS);
        answer("SHOW PARAMETERS LIKE 'TELEMETRY_ENABLE_INTERACTIVE_MODE' IN ACCOUNT");
        assertAnswer("", ROWS);
        answer("SHOW PARAMETERS LIKE 'TELEMETRY_ENABLE_INTERACTIVE_MODE'");
        assertAnswer("TELEMETRY_ENABLE_INTERACTIVE_MODE|false|BOOLEAN", ROWS);
    }

    @Test
    public void theSessionListsTheJavaScriptFormatAndTheCurrentDefaults() {
        answer("SHOW PARAMETERS LIKE 'JS_Q%'");
        assertAnswer("JS_QUERY_RESULT_FORMAT|JSON|STRING", ROWS);
        answer("SHOW PARAMETERS LIKE 'DYNAMIC_TABLES_VIEW_VERSION'");
        assertAnswer("DYNAMIC_TABLES_VIEW_VERSION|13|NUMBER", ROWS);
        answer("SHOW PARAMETERS LIKE 'AI_SQL_EMBED_SIMILARITY_ERROR_HANDLING_USE_FAIL_ON_ERROR'");
        assertAnswer("AI_SQL_EMBED_SIMILARITY_ERROR_HANDLING_USE_FAIL_ON_ERROR|false|BOOLEAN", ROWS);
        answer("SHOW PARAMETERS LIKE 'JS_QUERY_RESULT_FORMAT' IN ACCOUNT");
        assertAnswer("JS_QUERY_RESULT_FORMAT|JSON|STRING", ROWS);
    }

    @Test
    public void aWarehouseListsFiveAndADatabaseNoIcebergReplicationFlag() {
        final String warehouse = answer("SELECT COALESCE(CURRENT_WAREHOUSE(), 'COMPUTE_WH')");
        answer("SHOW PARAMETERS IN WAREHOUSE " + warehouse);
        assertAnswer("ENABLE_USE_STABLE_PATH|false|BOOLEAN ;; FALLBACK_WAREHOUSE||STRING ;; "
            + "MAX_CONCURRENCY_LEVEL|8|NUMBER ;; STATEMENT_QUEUED_TIMEOUT_IN_SECONDS|0|NUMBER ;; "
            + "STATEMENT_TIMEOUT_IN_SECONDS|172800|NUMBER", ROWS);
        answer("SHOW PARAMETERS LIKE 'ENABLE_ICEBERG_MANAGED%' IN DATABASE TEST_DB");
        assertAnswer("", ROWS);
    }
}
