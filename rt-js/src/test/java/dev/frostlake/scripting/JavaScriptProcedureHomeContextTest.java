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
 * Where a JavaScript procedure's handler runs. With its owner's rights the statements it issues run in the
 * database and schema the procedure lives in — a bare table, a table it creates and a routine it calls by a bare
 * name are that schema's — and the caller is back where it was afterwards, also when the handler failed and also
 * when the caller had no current database. With its caller's rights it stays where the caller is, and a USE it
 * issues is where the caller finds the session. A body called from a handler, and a handler called from a body,
 * run in their own homes in turn; a caller's-rights one stays where the body that called it runs, and under an
 * owner's-rights body it is held to owner's rights (live-verified). The words the runtime puts around a failed
 * statement are its own.
 */
public class JavaScriptProcedureHomeContextTest extends MeasuredCellsTestSupport {

    private static final String HERE =
        "SELECT NVL(CURRENT_DATABASE(), 'none') || '.' || NVL(CURRENT_SCHEMA(), 'none')";

    private static final String AUDIT = "SELECT 'HOME' AS s, msg FROM test_db.fl_home.audit_log UNION ALL "
        + "SELECT 'CALLER', msg FROM test_db.fl_caller.audit_log ORDER BY 1, 2";

    private static final String NESTED = "SELECT NVL(GETVARIABLE('NESTED_V')::VARCHAR, 'none')";

    private static final String SET_REFUSED = "Stored procedure execution error: Unsupported statement type 'SET'.";

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

    /** A SQL procedure that runs {@code statements} and answers the database and schema it ran in. */
    private void sql(final String name, final String rights, final String statements) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS VARCHAR LANGUAGE SQL" + rights
            + " AS $$ BEGIN " + statements + "RETURN (" + HERE + "); END; $$");
    }

    /** The CALL is refused, and its refusal carries {@code sentence} inside the words the runtime puts around it. */
    private void assertRefusedWith(final String sentence, final String call) {
        final String answer = answer(call);
        assertTrue(answer.startsWith("REFUSED ") && answer.contains(sentence), call + " ==> " + answer);
    }

    @Test
    public void anOwnersRightsHandlerRunsWhereItsProcedureLives() {
        js("test_db.fl_home.js_own", "", new String[] {"INSERT INTO audit_log VALUES ('js_own')"}, HERE);
        js("test_db.fl_home.js_clr", CALLER, new String[] {"INSERT INTO audit_log VALUES ('js_clr')"}, HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.js_own()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("TEST_DB.FL_CALLER", "CALL test_db.fl_home.js_clr()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        assertAnswer("CALLER|js_clr ;; HOME|js_own", AUDIT);
    }

    @Test
    public void whatAnOwnersRightsHandlerCreatesAndCallsIsItsHomes() {
        js("test_db.fl_home.js_makes", "", new String[] {"CREATE OR REPLACE TABLE made_by_js (a INT)"}, HERE);
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.js_makes()");
        assertAnswer("FL_HOME", "SELECT table_schema FROM test_db.information_schema.tables WHERE table_name = "
            + "'MADE_BY_JS'");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.helper() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'home helper'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_caller.helper() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN 'caller helper'; END; $$");
        js("test_db.fl_home.js_calls_helper", "", new String[0], "CALL helper()");
        js("test_db.fl_home.js_calls_helper_c", CALLER, new String[0], "CALL helper()");
        assertAnswer("home helper", "CALL test_db.fl_home.js_calls_helper()");
        assertAnswer("caller helper", "CALL test_db.fl_home.js_calls_helper_c()");
    }

    @Test
    public void aBodyAndAHandlerCalledFromOneAnotherRunInTheirOwnHomes() {
        sql("test_db.fl_caller.inner_ctx", "", "");
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.js_outer() RETURNS VARCHAR LANGUAGE JAVASCRIPT "
            + "AS $$ var a = snowflake.execute({sqlText: \"CALL test_db.fl_caller.inner_ctx()\"}); a.next(); "
            + "var b = snowflake.execute({sqlText: \"" + HERE + "\"}); b.next(); "
            + "return a.getColumnValue(1) + \" then \" + b.getColumnValue(1); $$");
        assertAnswer("TEST_DB.FL_CALLER then TEST_DB.FL_HOME", "CALL test_db.fl_home.js_outer()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        js("test_db.fl_caller.js_inner_ctx", "", new String[0], HERE);
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.sql_outer() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ DECLARE r VARCHAR; BEGIN CALL test_db.fl_caller.js_inner_ctx() INTO :r; RETURN r || ' then ' || "
            + "CURRENT_DATABASE() || '.' || CURRENT_SCHEMA(); END; $$");
        assertAnswer("TEST_DB.FL_CALLER then TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_outer()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
        // A handler that runs with its caller's rights stays where the body that called it runs.
        js("test_db.fl_caller.js_inner_clr", CALLER, new String[0], HERE);
        engine.execute("CREATE OR REPLACE PROCEDURE test_db.fl_home.sql_outer_clr() RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ DECLARE r VARCHAR; BEGIN CALL test_db.fl_caller.js_inner_clr() INTO :r; RETURN r; END; $$");
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.sql_outer_clr()");
        js("test_db.fl_home.js_outer_clr", "", new String[0], "CALL test_db.fl_caller.js_inner_clr()");
        assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.js_outer_clr()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
    }

    @Test
    public void aFailingHandlerLeavesTheCallerWhereItWas() {
        js("test_db.fl_home.js_fails", "", new String[] {"SELECT 1/0"}, HERE);
        assertRefusedWith("Division by zero", "CALL test_db.fl_home.js_fails()");
        assertAnswer("TEST_DB.FL_CALLER", HERE);
    }

    @Test
    public void aProcedureOfAnotherDatabaseRunsThere() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE fl_home_ctx_js_db");
            engine.execute("CREATE OR REPLACE SCHEMA fl_home_ctx_js_db.away");
            engine.execute("USE SCHEMA test_db.fl_caller");
            js("fl_home_ctx_js_db.away.js_own", "", new String[0], HERE);
            js("fl_home_ctx_js_db.away.js_clr", CALLER, new String[0], HERE);
            assertAnswer("FL_HOME_CTX_JS_DB.AWAY", "CALL fl_home_ctx_js_db.away.js_own()");
            assertAnswer("TEST_DB.FL_CALLER", HERE);
            assertAnswer("TEST_DB.FL_CALLER", "CALL fl_home_ctx_js_db.away.js_clr()");
            assertAnswer("TEST_DB.FL_CALLER", HERE);
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("DROP DATABASE IF EXISTS fl_home_ctx_js_db");
        }
    }

    @Test
    public void aSessionWithNoDatabaseHasNoneAfterAnOwnersRightsHandler() {
        js("test_db.fl_home.js_own", "", new String[0], HERE);
        js("test_db.fl_home.js_clr", CALLER, new String[0], HERE);
        js("test_db.fl_home.js_fails", "", new String[] {"SELECT 1/0"}, HERE);
        js("test_db.fl_home.js_clr_uses", CALLER, new String[] {"USE SCHEMA test_db.fl_other"}, HERE);
        try {
            // A session whose database is dropped under it has none.
            engine.execute("CREATE OR REPLACE DATABASE fl_ctx_gone_js");
            engine.execute("DROP DATABASE fl_ctx_gone_js");
            assertAnswer("none.none", HERE);
            assertAnswer("TEST_DB.FL_HOME", "CALL test_db.fl_home.js_own()");
            assertAnswer("none.none", HERE);
            assertAnswer("none.none", "CALL test_db.fl_home.js_clr()");
            assertRefusedWith("Division by zero", "CALL test_db.fl_home.js_fails()");
            assertAnswer("none.none", HERE);
            // What a caller's-rights handler moves the session to is where the caller is afterwards.
            assertAnswer("TEST_DB.FL_OTHER", "CALL test_db.fl_home.js_clr_uses()");
            assertAnswer("TEST_DB.FL_OTHER", HERE);
        } finally {
            answer("USE SCHEMA test_db.test_schema");
            answer("DROP DATABASE IF EXISTS fl_ctx_gone_js");
        }
    }

    @Test
    public void aCallersRightsBodyUnderAnOwnersRightsHandlerIsHeldToOwnersRights() {
        try {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("CREATE OR REPLACE PROCEDURE callers_set() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ BEGIN SET nested_v = 1; RETURN 'set by sql'; END; $$");
            js("js_callers_set", CALLER, new String[] {"SET nested_v = 2"}, "SELECT 'set by js'");
            js("js_own_calls_sql", "", new String[0], "CALL callers_set()");
            js("js_own_calls_js", "", new String[0], "CALL js_callers_set()");
            js("js_clr_calls_sql", CALLER, new String[0], "CALL callers_set()");
            js("js_clr_calls_js", CALLER, new String[0], "CALL js_callers_set()");
            engine.execute("CREATE OR REPLACE PROCEDURE sql_own_calls_js() RETURNS VARCHAR LANGUAGE SQL AS "
                + "$$ DECLARE r VARCHAR; BEGIN CALL js_callers_set() INTO :r; RETURN r; END; $$");
            assertRefusedWith(SET_REFUSED, "CALL js_own_calls_sql()");
            assertAnswer("none", NESTED);
            assertRefusedWith(SET_REFUSED, "CALL js_own_calls_js()");
            assertAnswer("none", NESTED);
            assertRefusedWith(SET_REFUSED, "CALL sql_own_calls_js()");
            assertAnswer("none", NESTED);
            assertAnswer("set by sql", "CALL js_clr_calls_sql()");
            assertAnswer("1", NESTED);
            assertAnswer("set by js", "CALL js_clr_calls_js()");
            assertAnswer("2", NESTED);
        } finally {
            answer("UNSET nested_v");
        }
    }
}
