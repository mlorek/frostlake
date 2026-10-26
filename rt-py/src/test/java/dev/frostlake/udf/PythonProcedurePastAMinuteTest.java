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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Python procedure runs as long as its statement may: one that orchestrates queries for over a minute answers,
 * its statement timeout at the default (live-verified).
 */
public class PythonProcedurePastAMinuteTest extends MeasuredCellsTestSupport {

    @Test
    public void aProcedureRunsPastAMinute() {
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.test_schema.lp773() RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.12' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'main' AS $$\nimport "
            + "time\ndef main(session):\n    for i in range(13):\n        session.sql('SELECT 1').collect()\n    "
            + "    time.sleep(5)\n    return 'done after 65s'\n$$");
        assertAnswer("done after 65s", "CALL test_db.test_schema.lp773()");
    }
}
