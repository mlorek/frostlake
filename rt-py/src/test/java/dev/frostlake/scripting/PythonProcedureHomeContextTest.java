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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a Python procedure's handler runs, and what of the session it may change. With its owner's rights the
 * statements it issues run in the database and schema the procedure lives in, the caller is back where it was
 * afterwards — also when it had no current database — and SET, UNSET, ALTER SESSION, USE, the session's listings
 * and a {@code $name} are refused as they are in a SQL body, while GETVARIABLE answers. With its caller's rights
 * it stays where the caller is and what it sets, and where it moves the session, is what the caller finds. A
 * caller's-rights body called from an owner's-rights handler, whatever language either is in, is held to owner's
 * rights (live-verified). The words the runtime puts around a failed statement are its own.
 */
public class PythonProcedureHomeContextTest extends MeasuredCellsTestSupport {

    private static final String HERE =
        "SELECT NVL(CURRENT_DATABASE(), 'none') || '.' || NVL(CURRENT_SCHEMA(), 'none')";

    private static final String AUDIT = "SELECT 'HOME' AS s, msg FROM test_db.fl_home.audit_log UNION ALL "
        + "SELECT 'CALLER', msg FROM test_db.fl_caller.audit_log ORDER BY 1, 2";

    private static final String STATE = "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || "
        + "NVL(GETVARIABLE('HV')::VARCHAR, 'none') || ' ' || CURRENT_SCHEMA()";

    private static final String NESTED = "SELECT NVL(GETVARIABLE('NESTED_V')::VARCHAR, 'none')";

    private static final String UNSUPPORTED = "Stored procedure execution error: Unsupported statement type '";

    private static final String CALLER = " EXECUTE AS CALLER";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE SCHEMA test_db.fl_home");
        engine.execute("CREATE OR REPLACE SCHEMA test_db.fl_caller");
        engine.execute("CREATE OR REPLACE SCHEMA test_db.fl_other");
        engine.execute("CREATE OR REPLACE TABLE test_db.fl_home.audit_log (msg VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE test_db.fl_caller.audit_log (msg VARCHAR)");
        engine.execute("USE SCHEMA test_db.fl_caller");
    }

    /** A Python procedure whose handler issues {@code statements} and answers the first cell of {@code answer}. */
    private void py(final String name, final String rights, final String[] statements, final String answer) {
        final StringBuilder body = new StringBuilder("def run(session):\n");
        for (final String statement : statements) {
            body.append("    session.sql(\"").append(statement).append("\").collect()\n");
        }
        body.append("    return session.sql(\"").append(answer).append("\").collect()[0][0]\n");
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE PYTHON "
            + "RUNTIME_VERSION = '3.11' PACKAGES = ('snowflake-snowpark-python') HANDLER = 'run'" + rights
            + " AS $$\n" + body + "$$");
    }

    /** A JavaScript procedure whose handler issues {@code statements} and answers the first cell of {@code answer}. */
    private void js(final String name, final String rights, final String[] statements, final String answer) {
        final StringBuilder body = new StringBuilder();
        for (final String statement : statements) {
            body.append("snowflake.execute({sqlText: \"").append(statement).append("\"}); ");
        }
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE JAVASCRIPT" + rights
            + " AS $$ " + body + "var rs = snowflake.execute({sqlText: \"" + answer + "\"}); rs.next(); "
            + "return rs.getColumnValue(1); $$");
    }

    /** The CALL is refused, and its refusal carries {@code sentence} inside the words the runtime puts around it. */
    private void assertRefusedWith(final String sentence, final String call) {
        final String answer = answer(call);
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), call + " ==> " + answer);
    }

    @Test
    public void anOwnersRightsHandlerRunsWhereItsProcedureLives() {
        py("test_db.fl_home.py_own", "", new String[] {"INSERT INTO audit_log VALUES ('py_own')"}, HERE);
        py("test_db.fl_home.py_clr", CALLER, new String[] {"INSERT INTO audit_log VALUES ('py_clr')"}, HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.py_own()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("TEST_DB.FL_CALLER", "CALL test_db.fl_home.py_clr()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("CALLER|py_clr ;; HOME|py_own", AUDIT);
    }

    @Test
    public void whatAnOwnersRightsHandlerCreatesAndCallsIsItsHomes() {
        py("test_db.fl_home.py_makes", "", new String[] {"CREATE OR REPLACE TABLE made_by_py (a INT)"}, HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.py_makes()");
        assertAnswer("FL_HOME", "SELECT table_schema FROM test_db.information_schema.tables WHERE table_name = "
            + "'MADE_BY_PY'");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.helper() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'home helper'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_caller.helper() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'caller helper'; END; $$");
        py("test_db.fl_home.py_calls_helper", "", new String[0], "CALL helper()");
        py("test_db.fl_home.py_calls_helper_c", CALLER, new String[0], "CALL helper()");
        assertAnswer("home helper", "CALL test_db.fl_home.py_calls_helper()");
        assertAnswer("caller helper", "CALL test_db.fl_home.py_calls_helper_c()");
    }

    @Test
    public void aFailingHandlerLeavesTheCallerWhereItWas() {
        py("test_db.fl_home.py_fails", "", new String[] {"SELECT 1/0"}, HERE);
        assertRefusedWith("Division by zero", "CALL test_db.fl_home.py_fails()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
    }

    @Test
    public void aSessionWithNoDatabaseHasNoneAfterAnOwnersRightsHandler() {
        py("test_db.fl_home.py_own", "", new String[0], HERE);
        py("test_db.fl_home.py_clr", CALLER, new String[0], HERE);
        try {
            // A session whose database is dropped under it has none.
            engine.execute("CREATE OR REPLACE DATABASE fl_ctx_gone_py");
            engine.execute("DROP DATABASE fl_ctx_gone_py");
            assertAnswer("none.none", HERE);
            assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.py_own()");
            assertAnswer("none.none", HERE);
            assertAnswer("none.none", "CALL test_db.fl_home.py_clr()");
            assertAnswer("none.none", HERE);
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("DROP DATABASE IF EXISTS fl_ctx_gone_py");
        }
    }

    @Test
    public void anOwnersRightsHandlerChangesNothingOfTheSession() {
        final String[][] refused = {
            {"SET hv = 9", UNSUPPORTED + "SET'."},
            {"UNSET v", UNSUPPORTED + "UNSET'."},
            {"ALTER SESSION SET QUERY_TAG = 'by-handler'", UNSUPPORTED + "ALTER_SESSION'."},
            {"USE SCHEMA test_db.fl_other", UNSUPPORTED + "USE'."},
            {"SHOW VARIABLES", UNSUPPORTED + "SHOW VARIABLE'."},
            {"SELECT $v", "Use of session variable '$V' is not allowed in owners rights stored procedure"},
        };
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("SET v = 5");
            for (final String[] cell : refused) {
                py("py_own", "", new String[] {cell[0]}, "SELECT 'done'");
                assertRefusedWith(cell[1], "CALL py_own()");
                assertEquals("5 none TEST_SCHEMA", answer(STATE), cell[0]);
            }
            py("py_own", "", new String[0], "SELECT GETVARIABLE('V')");
            assertAnswer("5", "CALL py_own()");
        } finally {
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("USE SCHEMA test_db.test_schema");
            answer("UNSET hv");
            answer("UNSET v");
        }
    }

    @Test
    public void aCallersRightsHandlerLeavesTheSessionWhereItPutIt() {
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("SET v = 5");
            py("py_clr", CALLER, new String[] {"SET hv = 9"}, "SELECT 'done'");
            assertAnswer("done", "CALL py_clr()");
            assertEquals("5 9 TEST_SCHEMA", answer(STATE));
            py("py_clr", CALLER, new String[] {"UNSET v"}, "SELECT 'done'");
            assertAnswer("done", "CALL py_clr()");
            assertEquals("none 9 TEST_SCHEMA", answer(STATE));
            py("py_clr", CALLER, new String[] {"USE SCHEMA test_db.fl_other"}, "SELECT 'done'");
            assertAnswer("done", "CALL py_clr()");
            assertEquals("none 9 FL_OTHER", answer(STATE));
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("UNSET hv");
            answer("UNSET v");
        }
    }

    @Test
    public void aCallersRightsBodyUnderAnOwnersRightsHandlerIsHeldToOwnersRights() {
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("CREATE OR REPLACE PROCEDURE callers_set() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ BEGIN SET nested_v = 1; RETURN 'set by sql'; END; $$");
            js("js_callers_set", CALLER, new String[] {"SET nested_v = 2"}, "SELECT 'set by js'");
            py("py_callers_set", CALLER, new String[] {"SET nested_v = 3"}, "SELECT 'set by py'");
            py("py_own_calls_sql", "", new String[0], "CALL callers_set()");
            py("py_own_calls_js", "", new String[0], "CALL js_callers_set()");
            py("py_clr_calls_sql", CALLER, new String[0], "CALL callers_set()");
            js("js_clr_calls_py", CALLER, new String[0], "CALL py_callers_set()");
            engine.execute("CREATE OR REPLACE PROCEDURE sql_own_calls_py() RETURNS VARCHAR LANGUAGE SQL AS "
                + "$$ DECLARE r VARCHAR; BEGIN CALL py_callers_set() INTO :r; RETURN r; END; $$");
            assertRefusedWith(UNSUPPORTED + "SET'.", "CALL py_own_calls_sql()");
            assertAnswer("none", NESTED);
            assertRefusedWith(UNSUPPORTED + "SET'.", "CALL py_own_calls_js()");
            assertAnswer("none", NESTED);
            assertRefusedWith(UNSUPPORTED + "SET'.", "CALL sql_own_calls_py()");
            assertAnswer("none", NESTED);
            assertAnswer("set by sql", "CALL py_clr_calls_sql()");
            assertAnswer("1", NESTED);
            assertAnswer("set by py", "CALL js_clr_calls_py()");
            assertAnswer("3", NESTED);
        } finally {
            answer("UNSET nested_v");
        }
    }
}
