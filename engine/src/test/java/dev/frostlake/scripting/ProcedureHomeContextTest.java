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
 * Where a stored procedure's body runs. With its owner's rights it runs in the database and schema the procedure
 * lives in — a SQL body's statements and the statements a Java handler issues alike — so a bare table, a table it
 * creates and a routine it calls by a bare name are that schema's, and the caller is back where it was afterwards:
 * in its own schema, or nowhere, when it had no current database. With its caller's rights it stays where the
 * caller is, and where it leaves the session — a USE, a variable it sets — is where the caller finds it. A body
 * called from a body runs in its own home in turn, and a caller's-rights one stays where the body that called it
 * runs (live-verified).
 */
public class ProcedureHomeContextTest extends MeasuredCellsTestSupport {

    private static final String HERE =
        "SELECT NVL(CURRENT_DATABASE(), 'none') || '.' || NVL(CURRENT_SCHEMA(), 'none')";

    private static final String AUDIT = "SELECT 'HOME' AS s, msg FROM test_db.fl_home.audit_log UNION ALL "
        + "SELECT 'CALLER', msg FROM test_db.fl_caller.audit_log ORDER BY 1, 2";

    private static final String STATE = "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || "
        + "NVL(GETVARIABLE('HV')::VARCHAR, 'none') || ' ' || CURRENT_SCHEMA()";

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

    /** A SQL procedure that runs {@code statements} and answers the database and schema it ran in. */
    private void sql(final String name, final String rights, final String statements) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE SQL" + rights
            + " AS $$ BEGIN " + statements + "RETURN (" + HERE + "); END; $$");
    }

    /** A Java procedure whose handler issues {@code statements} and answers the first cell of {@code answer}. */
    private void java(final String name, final String rights, final String[] statements, final String answer) {
        final StringBuilder body = new StringBuilder();
        for (final String statement : statements) {
            body.append("s.sql(\"").append(statement).append("\").collect(); ");
        }
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run'" + rights
            + " AS $$ import com.snowflake.snowpark_java.*; class H { public static String run(Session s) { "
            + body + "return s.sql(\"" + answer + "\").collect()[0].getString(0); } } $$");
    }

    /** The CALL is refused, and its refusal carries {@code sentence} inside the words the runtime puts around it. */
    private void assertRefusedWith(final String sentence, final String call) {
        final String answer = answer(call);
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), call + " ==> " + answer);
    }

    @Test
    public void anOwnersRightsBodyRunsWhereItsProcedureLives() {
        sql("test_db.fl_home.sql_own", "", "INSERT INTO audit_log VALUES ('sql_own'); ");
        java("test_db.fl_home.java_own", "", new String[] {"INSERT INTO audit_log VALUES ('java_own')"}, HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_own()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.java_own()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("HOME|java_own ;; HOME|sql_own", AUDIT);
    }

    @Test
    public void aCallersRightsBodyStaysWhereItsCallerIs() {
        sql("test_db.fl_home.sql_clr", CALLER, "INSERT INTO audit_log VALUES ('sql_clr'); ");
        java("test_db.fl_home.java_clr", CALLER, new String[] {"INSERT INTO audit_log VALUES ('java_clr')"}, HERE);
        assertAnswer("TEST_DB.FL_CALLER", "CALL test_db.fl_home.sql_clr()");
        assertAnswer("TEST_DB.FL_CALLER", "CALL test_db.fl_home.java_clr()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("CALLER|java_clr ;; CALLER|sql_clr", AUDIT);
    }

    @Test
    public void whatAnOwnersRightsBodyCreatesAndCallsIsItsHomes() {
        sql("test_db.fl_home.sql_makes", "", "CREATE OR REPLACE TABLE made_by_sql (a INT); ");
        java("test_db.fl_home.java_makes", "", new String[] {"CREATE OR REPLACE TABLE made_by_java (a INT)"}, HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_makes()");
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.java_makes()");
        assertAnswer("FL_HOME|MADE_BY_JAVA ;; FL_HOME|MADE_BY_SQL", "SELECT table_schema, table_name FROM "
            + "test_db.information_schema.tables WHERE table_name LIKE 'MADE_BY_%' ORDER BY 2");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.helper() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'home helper'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_caller.helper() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'caller helper'; END; $$");
        java("test_db.fl_home.java_calls_helper", "", new String[0], "CALL helper()");
        java("test_db.fl_home.java_calls_helper_c", CALLER, new String[0], "CALL helper()");
        assertAnswer("home helper", "CALL test_db.fl_home.java_calls_helper()");
        assertAnswer("caller helper", "CALL test_db.fl_home.java_calls_helper_c()");
    }

    @Test
    public void aBodyCalledFromABodyRunsInItsOwnHome() {
        sql("test_db.fl_caller.inner_own", "", "");
        sql("test_db.fl_caller.inner_clr", CALLER, "");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.sql_outer() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ DECLARE r VARCHAR; BEGIN CALL test_db.fl_caller.inner_own() INTO :r; RETURN r || ' then ' || "
            + "CURRENT_DATABASE() || '.' || CURRENT_SCHEMA(); END; $$");
        assertAnswer("TEST_DB.FL_CALLER then TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_outer()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        // One that runs with its caller's rights stays where the body that called it runs.
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.sql_outer_clr() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ DECLARE r VARCHAR; BEGIN CALL test_db.fl_caller.inner_clr() INTO :r; RETURN r; END; $$");
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_outer_clr()");
        java("test_db.fl_home.java_outer", "", new String[0], "CALL test_db.fl_caller.inner_own()");
        java("test_db.fl_home.java_outer_clr", "", new String[0], "CALL test_db.fl_caller.inner_clr()");
        assertAnswer("TEST_DB.FL_CALLER", "CALL test_db.fl_home.java_outer()");
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.java_outer_clr()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
    }

    @Test
    public void aFailingOwnersRightsBodyLeavesTheCallerWhereItWas() {
        sql("test_db.fl_home.sql_fails", "", "SELECT 1/0; ");
        java("test_db.fl_home.java_fails", "", new String[] {"SELECT 1/0"}, HERE);
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "CALL test_db.fl_home.sql_fails()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertRefusedWith("Division by zero", "CALL test_db.fl_home.java_fails()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
    }

    @Test
    public void aProcedureOfAnotherDatabaseRunsThere() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE fl_home_ctx_db");
            engine.execute("CREATE OR REPLACE SCHEMA fl_home_ctx_db.away");
            engine.execute("USE SCHEMA test_db.fl_caller");
            sql("fl_home_ctx_db.away.sql_own", "", "");
            sql("fl_home_ctx_db.away.sql_clr", CALLER, "");
            java("fl_home_ctx_db.away.java_own", "", new String[0], HERE);
            assertAnswer("FL_HOME_CTX_DB.AWAY", "CALL fl_home_ctx_db.away.sql_own()");
            assertAnswer("TEST_DB.FL_CALLER", HERE);
            assertAnswer("TEST_DB.FL_CALLER", "CALL fl_home_ctx_db.away.sql_clr()");
            assertAnswer("FL_HOME_CTX_DB.AWAY", "CALL fl_home_ctx_db.away.java_own()");
            assertAnswer("TEST_DB.FL_CALLER", HERE);
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("DROP DATABASE IF EXISTS fl_home_ctx_db");
        }
    }

    @Test
    public void aSessionWithNoDatabaseHasNoneAfterAnOwnersRightsCall() {
        sql("test_db.fl_home.sql_own", "", "");
        sql("test_db.fl_home.sql_clr", CALLER, "");
        sql("test_db.fl_home.sql_fails", "", "SELECT 1/0; ");
        sql("test_db.fl_home.sql_clr_uses", CALLER, "USE SCHEMA test_db.fl_other; ");
        java("test_db.fl_home.java_own", "", new String[0], HERE);
        java("test_db.fl_home.java_clr", CALLER, new String[0], HERE);
        try {
            // A session whose database is dropped under it has none.
            engine.execute("CREATE OR REPLACE DATABASE fl_ctx_gone");
            engine.execute("DROP DATABASE fl_ctx_gone");
            assertAnswer("none.none", HERE);
            assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_own()");
            assertAnswer("none.none", HERE);
            assertAnswer("none.none", "CALL test_db.fl_home.sql_clr()");
            assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.java_own()");
            assertAnswer("none.none", HERE);
            assertAnswer("none.none", "CALL test_db.fl_home.java_clr()");
            assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : "
                + "Division by zero", "CALL test_db.fl_home.sql_fails()");
            assertAnswer("none.none", HERE);
            assertAnswer("REFUSED Cannot perform CREATE TABLE. This session does not have a current database. "
                + "Call 'USE DATABASE', or use a qualified name.", "CREATE TABLE t_after (a INT)");
            // What a caller's-rights body moves the session to is where the caller is afterwards.
            assertAnswer("TEST_DB.FL_OTHER", "CALL test_db.fl_home.sql_clr_uses()");
            assertAnswer("TEST_DB.FL_OTHER", HERE);
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("DROP DATABASE IF EXISTS fl_ctx_gone");
        }
    }

    @Test
    public void aCallersRightsBodyLeavesTheSessionWhereItPutIt() {
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("SET v = 5");
            engine.execute("CREATE OR REPLACE PROCEDURE sql_clr_changes() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ BEGIN USE SCHEMA test_db.fl_other; SET hv = 9; RETURN CURRENT_SCHEMA(); END; $$");
            assertAnswer("FL_OTHER", "CALL sql_clr_changes()");
            assertEquals("5 9 FL_OTHER", answer(STATE));
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("UNSET hv");
            java("java_clr_changes", CALLER, new String[] {"USE SCHEMA test_db.fl_other", "SET hv = 9"},
                "SELECT CURRENT_SCHEMA()");
            assertAnswer("FL_OTHER", "CALL java_clr_changes()");
            assertEquals("5 9 FL_OTHER", answer(STATE));
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("UNSET hv");
            answer("UNSET v");
        }
    }

    @Test
    public void anOwnersRightsJavaHandlerChangesNothingOfTheSession() {
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
                java("java_own", "", new String[] {cell[0]}, "SELECT 'done'");
                assertRefusedWith(cell[1], "CALL java_own()");
                assertEquals("5 none TEST_SCHEMA", answer(STATE), cell[0]);
            }
            java("java_own", "", new String[0], "SELECT GETVARIABLE('V')");
            assertAnswer("5", "CALL java_own()");
        } finally {
            answer("ALTER SESSION UNSET QUERY_TAG");
            answer("USE SCHEMA test_db.test_schema");
            answer("UNSET hv");
            answer("UNSET v");
        }
    }

    @Test
    public void aCallersRightsBodyUnderAnOwnersRightsJavaHandlerIsHeldToOwnersRights() {
        final String nested = "SELECT NVL(GETVARIABLE('NESTED_V')::VARCHAR, 'none')";
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("CREATE OR REPLACE PROCEDURE callers_set() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ BEGIN SET nested_v = 1; RETURN 'set by sql'; END; $$");
            java("java_callers_set", CALLER, new String[] {"SET nested_v = 4"}, "SELECT 'set by java'");
            java("java_own_calls_sql", "", new String[0], "CALL callers_set()");
            java("java_clr_calls_sql", CALLER, new String[0], "CALL callers_set()");
            engine.execute("CREATE OR REPLACE PROCEDURE sql_own_calls_java() RETURNS VARCHAR LANGUAGE SQL AS "
                + "$$ DECLARE r VARCHAR; BEGIN CALL java_callers_set() INTO :r; RETURN r; END; $$");
            assertRefusedWith(UNSUPPORTED + "SET'.", "CALL java_own_calls_sql()");
            assertAnswer("none", nested);
            assertRefusedWith(UNSUPPORTED + "SET'.", "CALL sql_own_calls_java()");
            assertAnswer("none", nested);
            assertAnswer("set by sql", "CALL java_clr_calls_sql()");
            assertAnswer("1", nested);
        } finally {
            answer("UNSET nested_v");
        }
    }
}
