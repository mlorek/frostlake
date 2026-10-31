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
package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The parameters ALTER ALERT … SET and UNSET take, and the grants SHOW GRANTS ON ALERT lists (live-verified): a
 * session parameter or an alert's own LOG_LEVEL is set on the alert and listed by SHOW PARAMETERS IN ALERT at level
 * ALERT, several in one SET; SUSPEND_ALERT_AFTER_NUM_FAILURES is set but is no parameter, so it is not listed; a few
 * parameters are refused at the alert's level, any other for want of access, and a name no parameter has as an
 * invalid property. ENABLE_USE_STABLE_PATH reads true at level SYSTEM.
 */
public class AlertParametersTest extends MeasuredCellsTestSupport {

    /** Each statement over an alert, and its answer. */
    @Test
    public void anAlertTakesTheSessionParametersAndItsOwn() {
        final String warehouse = String.valueOf(engine.executeQuery(
            "SELECT COALESCE(CURRENT_WAREHOUSE(), 'COMPUTE_WH')").getRows().get(0).getValue(0));
        engine.execute("CREATE OR REPLACE ALERT al_p WAREHOUSE = " + warehouse
            + " SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        try {
            assertCells(new String[][] {
                {"ALTER ALERT al_p SET STATEMENT_TIMEOUT_IN_SECONDS = 10, LOG_LEVEL = 'INFO'",
                    "Statement executed successfully."},
                {"SHOW PARAMETERS LIKE 'STATEMENT_TIMEOUT_IN_SECONDS' IN ALERT al_p",
                    "STATEMENT_TIMEOUT_IN_SECONDS|10|172800|ALERT|Timeout in seconds for statements: statements are"
                        + " automatically canceled if they run for longer; if set to zero, max value (604800) is"
                        + " enforced.|NUMBER"},
                {"SHOW PARAMETERS LIKE 'LOG_LEVEL' IN ALERT al_p",
                    "LOG_LEVEL|INFO|OFF|ALERT|LOG_LEVEL to use when filtering events|STRING"},
                {"ALTER ALERT al_p SET SUSPEND_ALERT_AFTER_NUM_FAILURES = 3", "Statement executed successfully."},
                {"SHOW PARAMETERS LIKE 'SUSPEND%' IN ALERT al_p", ""},
                {"ALTER ALERT al_p UNSET LOG_LEVEL", "Statement executed successfully."},
                {"SHOW PARAMETERS LIKE 'LOG_LEVEL' IN ALERT al_p",
                    "LOG_LEVEL|OFF|OFF||LOG_LEVEL to use when filtering events|STRING"},
                {"ALTER ALERT al_p SET NOSUCH_PARAM = 1",
                    "REFUSED SQL compilation error: | invalid property 'NOSUCH_PARAM' for 'ALERT'"},
                {"ALTER ALERT al_p SET DATA_RETENTION_TIME_IN_DAYS = 1",
                    "REFUSED SQL access control error: | Insufficient privileges to operate on alert 'AL_P'."},
                {"ALTER ALERT al_p SET NETWORK_POLICY = ''",
                    "REFUSED SQL compilation error: | cannot set parameter 'NETWORK_POLICY' at 'ALERT'"},
                {"SHOW GRANTS ON ALERT nosuch_al",
                    "REFUSED SQL compilation error: | Alert 'TEST_DB.TEST_SCHEMA.NOSUCH_AL' does not exist or not"
                        + " authorized. Your primary role <ROLE> must have at least one privilege granted on ALERT"
                        + " TEST_DB.TEST_SCHEMA.NOSUCH_AL."},
                {"SHOW PARAMETERS LIKE 'ENABLE_USE_STABLE_PATH'",
                    "ENABLE_USE_STABLE_PATH|true|false|SYSTEM|Add support for stable path|BOOLEAN"},
            });
            engine.execute("SHOW GRANTS ON ALERT al_p");
            assertAnswer("OWNERSHIP|ALERT|TEST_DB.TEST_SCHEMA.AL_P",
                "SELECT \"privilege\", \"granted_on\", \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        } finally {
            engine.execute("DROP ALERT IF EXISTS al_p");
        }
    }
}
