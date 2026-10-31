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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * AUTOCOMMIT_API_SUPPORTED takes no value at an alert, a task or the session: every SET is refused with the value
 * as written, TRUE and FALSE included, and an UNSET is taken; a table and a schema refuse it as they refuse any
 * parameter they do not take (all live-verified).
 */
public class AutocommitApiParameterTest extends MeasuredCellsTestSupport {

    @Test
    public void noValueAtThreeLevels() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("CREATE OR REPLACE TASK tk SCHEDULE = '5 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE ALERT al WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT "
            + "1)) THEN SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value [true] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER ALERT al SET AUTOCOMMIT_API_SUPPORTED = true");
        assertAnswer("REFUSED SQL compilation error: | invalid value [1] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER ALERT al SET AUTOCOMMIT_API_SUPPORTED = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER ALERT al SET AUTOCOMMIT_API_SUPPORTED = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER ALERT al UNSET AUTOCOMMIT_API_SUPPORTED");
        assertAnswer("REFUSED SQL compilation error: | invalid value [true] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER TASK tk SET AUTOCOMMIT_API_SUPPORTED = true");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['true'] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER TASK tk SET AUTOCOMMIT_API_SUPPORTED = 'true'");
        assertAnswer("Statement executed successfully.", "ALTER TASK tk UNSET AUTOCOMMIT_API_SUPPORTED");
        assertAnswer("REFUSED SQL compilation error: | invalid value [true] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "CREATE OR REPLACE TASK tk2 SCHEDULE = '5 MINUTE' AUTOCOMMIT_API_SUPPORTED = true AS SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER TABLE t1 SET AUTOCOMMIT_API_SUPPORTED = 'x'");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on schema 'TEST_SCHEMA'.",
            "ALTER SCHEMA test_db.test_schema SET AUTOCOMMIT_API_SUPPORTED = true");
        assertAnswer("REFUSED SQL compilation error: | invalid value ['x'] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER SESSION SET AUTOCOMMIT_API_SUPPORTED = 'x'");
        assertAnswer("REFUSED SQL compilation error: | invalid value [true] for parameter 'AUTOCOMMIT_API_SUPPORTED'",
            "ALTER SESSION SET AUTOCOMMIT_API_SUPPORTED = true");
        assertAnswer("Statement executed successfully.", "ALTER SESSION UNSET AUTOCOMMIT_API_SUPPORTED");
    }

    /**
     * A user and the account take no value of it either - the documentation lists it as view-only, of no level - in
     * the session's words, and an UNSET is taken.
     */
    @Test
    public void noValueAtAUserOrTheAccount() {
        Assumptions.assumeFalse(isLiveSnowflake(), "ALTER USER and ALTER ACCOUNT are not run on the live account");
        engine.execute("CREATE OR REPLACE USER u576");
        try {
            assertAnswer("REFUSED SQL compilation error: | invalid value [TRUE] for parameter "
                    + "'AUTOCOMMIT_API_SUPPORTED'",
                "ALTER USER u576 SET AUTOCOMMIT_API_SUPPORTED = TRUE");
            assertAnswer("REFUSED SQL compilation error: | invalid value ['true'] for parameter "
                    + "'AUTOCOMMIT_API_SUPPORTED'",
                "ALTER USER u576 SET AUTOCOMMIT_API_SUPPORTED = 'true'");
            assertAnswer("Statement executed successfully.", "ALTER USER u576 UNSET AUTOCOMMIT_API_SUPPORTED");
            assertAnswer("REFUSED SQL compilation error: | invalid value [FALSE] for parameter "
                    + "'AUTOCOMMIT_API_SUPPORTED'",
                "ALTER ACCOUNT SET AUTOCOMMIT_API_SUPPORTED = FALSE");
            assertAnswer("Statement executed successfully.", "ALTER ACCOUNT UNSET AUTOCOMMIT_API_SUPPORTED");
        } finally {
            engine.execute("DROP USER IF EXISTS u576");
        }
    }
}
