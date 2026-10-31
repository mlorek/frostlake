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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How deep Python procedures run inside one another: sixteen in full, and a seventeenth is ended before its code
 * runs, since the session it is handed issues a statement as it opens - a SQL or JavaScript body at that depth runs
 * until it issues one. So sixteen SQL procedures calling a Python one that issues nothing are refused, and sixteen
 * Python procedures calling a SQL one that issues nothing answer. The sixteenth Python body may catch the refusal,
 * as a SnowparkSQLException; uncaught, the history row of the call is FAILED_WITH_ERROR with 100357, the code of a
 * Python handler's failure. Each recursion here ends by its own counter. The account words an uncaught refusal as
 * every Python body's traceback, cut short, which this engine does not mirror, so such a refusal is asked for its
 * sentence (live-verified).
 */
public class PythonCallDepthTest extends MeasuredCellsTestSupport {

    private static final String REFUSAL = "Stored procedure execution error: Stored procedure exceeds the maximum "
        + "allowed number of recursive calls.";

    private void assertEnded(final String sql) {
        final String answered = answer(sql);
        assertTrue(answered.startsWith("REFUSED ") && answered.contains(REFUSAL), answered);
    }

    @Test
    public void sixteenBodiesRunAndTheSeventeenthIsEndedAsItOpens() {
        engine.execute("CREATE OR REPLACE PROCEDURE py_rec(n INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, n):\n    if n <= 0:\n"
            + "        return 0\n    return session.sql(f\"CALL py_rec({n - 1})\").collect()[0][0] + 1\n$$");
        assertAnswer("14", "CALL py_rec(14)");
        assertAnswer("15", "CALL py_rec(15)");
        // The seventeenth procedure would answer 0 without a statement of its own.
        assertEnded("CALL py_rec(16)");
        assertEnded("CALL py_rec(20)");
        assertAnswer("FAILED_WITH_ERROR|100357", "SELECT EXECUTION_STATUS, ERROR_CODE FROM "
            + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION()) WHERE QUERY_TEXT = 'CALL py_rec(20)' ORDER BY "
            + "START_TIME DESC LIMIT 1");
    }

    @Test
    public void aPythonLeafCountsAsItOpensAndASqlLeafAsItIssues() {
        engine.execute("CREATE OR REPLACE PROCEDURE py_leaf() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = '3.11' "
            + "PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session):\n    return 0\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE sql_to_py(n NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE r "
            + "NUMBER; BEGIN IF (n <= 0) THEN CALL py_leaf() INTO :r; RETURN r; END IF; CALL sql_to_py(:n - 1) INTO "
            + ":r; RETURN r + 1; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE sql_leaf() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 0; END; "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE py_to_sql(n INT) RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, n):\n    if n "
            + "<= 0:\n        return session.sql(\"CALL sql_leaf()\").collect()[0][0]\n    return "
            + "session.sql(f\"CALL py_to_sql({n - 1})\").collect()[0][0] + 1\n$$");
        assertAnswer("14", "CALL sql_to_py(14)");
        // Each SQL body the refusal leaves words it once more, at its CALL.
        assertAnswer("REFUSED " + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 84 : ".repeat(15)
            + "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : " + REFUSAL,
            "CALL sql_to_py(15)");
        assertAnswer("14", "CALL py_to_sql(14)");
        assertAnswer("15", "CALL py_to_sql(15)");
    }

    @Test
    public void theSixteenthBodyMayCatchTheRefusal() {
        engine.execute("CREATE OR REPLACE PROCEDURE py_catch(n INT) RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION = "
            + "'3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run' AS $$\ndef run(session, n):\n    if n "
            + ">= 40:\n        return 'bottom'\n    try:\n        return session.sql(f\"CALL py_catch({n + "
            + "1})\").collect()[0][0]\n    except Exception as e:\n        return f\"{n}: {type(e).__name__}: "
            + "{'recursive calls' in str(e)}\"\n$$");
        assertAnswer("16: SnowparkSQLException: True", "CALL py_catch(1)");
    }
}
