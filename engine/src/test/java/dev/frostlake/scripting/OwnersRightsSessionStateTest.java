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

/**
 * A stored procedure that runs with its owner's rights has no part of the session that calls it. A
 * {@code $name} in it is refused when it is read - {@code Use of session variable '$V' is not allowed in owners
 * rights stored procedure} - in an expression of the block, in a statement and in a handler, and in a
 * caller's-rights procedure it calls, which is refused before it runs; GETVARIABLE answers there all the same.
 * SET, UNSET, ALTER SESSION and USE are unsupported statement types in it, and so are the session's listings,
 * named in the singular: SHOW VARIABLE, SHOW PARAMETER - of the session, where IN ACCOUNT answers -, SHOW LOCK
 * and SHOW TRANSACTION; a SHOW GRANTS that names nothing has a sentence of its own, and a listing of objects
 * answers. The same body run with its caller's rights does all of it. Whatever rights it will run with, a body
 * that names a {@code $name} compiles against the session's variables: the procedure is created only while the
 * variable exists, and a CALL made once it is gone is refused the same way. Every answer is live-verified.
 */
public class OwnersRightsSessionStateTest extends MeasuredCellsTestSupport {

    @Test
    public void anOwnersRightsBodyReadsAndChangesNothingOfTheSession() {
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_dollar() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN $v; "
                + "END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_dollar() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $v; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_getvar() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_getvar() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_both() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN $v || ' "
                + "/ ' || GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_both() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $v || ' / ' || GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT $v INTO :r; RETURN r; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_query() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE r VARCHAR; BEGIN SELECT $v INTO :r; RETURN r; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_missing() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "$nosuch_var_x9; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_missing() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $nosuch_var_x9; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SET w = 1; RETURN "
                + "'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "SET w = 1; RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets_bare() RETURNS VARCHAR LANGUAGE SQL AS $$ SET w2 = 2; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets_bare() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ SET "
                + "w2 = 2; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets_dynamic() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN EXECUTE "
                + "IMMEDIATE 'SET w3 = 3'; RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets_dynamic() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'SET w3 = 3'; RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets_two() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SET (w4, w5) = "
                + "(4, 5); RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets_two() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN SET (w4, w5) = (4, 5); RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_unsets() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN UNSET v; RETURN "
                + "'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_unsets() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "UNSET v; RETURN 'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_unsets_missing() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN UNSET "
                + "nosuch_var_x9; RETURN 'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_unsets_missing() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN UNSET nosuch_var_x9; RETURN 'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_variables() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW VARIABLES; SELECT COUNT(*) INTO :c FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); "
                + "RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_variables() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ DECLARE c NUMBER; BEGIN SHOW VARIABLES; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_variables_like() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW VARIABLES LIKE 'V'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_variables_like() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ DECLARE c NUMBER; BEGIN SHOW VARIABLES LIKE 'V'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_parameters() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_parameters() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_parameters_session() RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_parameters_session() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION; SELECT COUNT(*) "
                + "INTO :c FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_parameters_account() RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_parameters_account() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT; SELECT COUNT(*) "
                + "INTO :c FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_locks() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c NUMBER; "
                + "BEGIN SHOW LOCKS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_locks() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW LOCKS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_transactions() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW TRANSACTIONS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_transactions() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER "
                + "AS $$ DECLARE c NUMBER; BEGIN SHOW TRANSACTIONS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_grants() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c NUMBER; "
                + "BEGIN SHOW GRANTS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_grants() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW GRANTS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_tables() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c NUMBER; "
                + "BEGIN SHOW TABLES; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_tables() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW TABLES; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_alters_session() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN ALTER "
                + "SESSION SET QUERY_TAG = 'set-in-procedure'; RETURN 'altered'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_alters_session() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN ALTER SESSION SET QUERY_TAG = 'set-in-procedure'; RETURN 'altered'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_uses_schema() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN USE SCHEMA "
                + "test_schema; RETURN 'used'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_uses_schema() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN USE SCHEMA test_schema; RETURN 'used'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_current() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "CURRENT_SCHEMA() || ' / ' || CURRENT_USER() IS NOT NULL; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_current() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN CURRENT_SCHEMA() || ' / ' || CURRENT_USER() IS NOT NULL; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_own_reads() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT $v\"}); rs.next(); return rs.getColumnValue(1); $$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_own_sets() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "snowflake.execute({sqlText: \"SET jsw = 9\"}); return \"set\"; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_clr_reads() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS CALLER AS "
                + "$$ var rs = snowflake.execute({sqlText: \"SELECT $v\"}); rs.next(); return rs.getColumnValue(1); "
                + "$$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_clr_sets() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS CALLER AS "
                + "$$ snowflake.execute({sqlText: \"SET jsw = 9\"}); return \"set\"; $$");
        assertAnswer(
            "Statement executed successfully.",
            "SET v = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function OWN_READS_DOLLAR.",
            "CALL own_reads_dollar()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "5",
            "CALL own_reads_getvar()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function OWN_READS_BOTH.",
            "CALL own_reads_both()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function OWN_READS_QUERY.",
            "CALL own_reads_query()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function OWN_READS_MISSING.",
            "CALL own_reads_missing()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'SET'.",
            "CALL own_sets()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'SET'.",
            "CALL own_sets_dynamic()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'SET'.",
            "CALL own_sets_two()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'UNSET'.",
            "CALL own_unsets()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'UNSET'.",
            "CALL own_unsets_missing()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Stored procedure "
                + "execution error: Unsupported statement type 'SHOW VARIABLE'.",
            "CALL own_shows_variables()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Stored procedure "
                + "execution error: Unsupported statement type 'SHOW VARIABLE'.",
            "CALL own_shows_variables_like()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Stored procedure "
                + "execution error: Unsupported statement type 'SHOW PARAMETER'.",
            "CALL own_shows_parameters()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Stored procedure "
                + "execution error: Unsupported statement type 'SHOW PARAMETER'.",
            "CALL own_shows_parameters_session()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "1",
            "CALL own_shows_parameters_account()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Stored procedure "
                + "execution error: Unsupported statement type 'SHOW LOCK'.",
            "CALL own_shows_locks()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : Stored procedure "
                + "execution error: Unsupported statement type 'SHOW TRANSACTION'.",
            "CALL own_shows_transactions()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 25 : SHOW GRANTS to the "
                + "current user is not supported in owner's rights stored procedures.",
            "CALL own_shows_grants()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "0",
            "CALL own_shows_tables()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'ALTER_SESSION'.",
            "CALL own_alters_session()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure "
                + "execution error: Unsupported statement type 'USE'.",
            "CALL own_uses_schema()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "true",
            "CALL own_reads_current()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET v");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w2");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w3");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w4");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w5");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET jsw");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER SESSION UNSET QUERY_TAG");
        assertAnswer(
            "Statement executed successfully.",
            "USE SCHEMA test_db.test_schema");
    }

    @Test
    public void aCallersRightsBodyReadsAndChangesTheSession() {
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_dollar() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN $v; "
                + "END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_dollar() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $v; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_getvar() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_getvar() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_both() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN $v || ' "
                + "/ ' || GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_both() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $v || ' / ' || GETVARIABLE('V'); END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT $v INTO :r; RETURN r; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_query() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE r VARCHAR; BEGIN SELECT $v INTO :r; RETURN r; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_missing() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "$nosuch_var_x9; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_missing() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $nosuch_var_x9; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SET w = 1; RETURN "
                + "'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "SET w = 1; RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets_bare() RETURNS VARCHAR LANGUAGE SQL AS $$ SET w2 = 2; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets_bare() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ SET "
                + "w2 = 2; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets_dynamic() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN EXECUTE "
                + "IMMEDIATE 'SET w3 = 3'; RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets_dynamic() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN EXECUTE IMMEDIATE 'SET w3 = 3'; RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_sets_two() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SET (w4, w5) = "
                + "(4, 5); RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_sets_two() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN SET (w4, w5) = (4, 5); RETURN 'set'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_unsets() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN UNSET v; RETURN "
                + "'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_unsets() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "UNSET v; RETURN 'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_unsets_missing() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN UNSET "
                + "nosuch_var_x9; RETURN 'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_unsets_missing() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN UNSET nosuch_var_x9; RETURN 'unset'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_variables() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW VARIABLES; SELECT COUNT(*) INTO :c FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); "
                + "RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_variables() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ DECLARE c NUMBER; BEGIN SHOW VARIABLES; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_variables_like() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW VARIABLES LIKE 'V'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_variables_like() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ DECLARE c NUMBER; BEGIN SHOW VARIABLES LIKE 'V'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_parameters() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_parameters() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG'; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_parameters_session() RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_parameters_session() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION; SELECT COUNT(*) "
                + "INTO :c FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_parameters_account() RETURNS VARCHAR LANGUAGE SQL AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT; SELECT COUNT(*) INTO :c FROM "
                + "TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_parameters_account() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ DECLARE c NUMBER; BEGIN SHOW PARAMETERS LIKE 'QUERY_TAG' IN ACCOUNT; SELECT COUNT(*) "
                + "INTO :c FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())); RETURN c; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_locks() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c NUMBER; "
                + "BEGIN SHOW LOCKS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_locks() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW LOCKS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_transactions() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c "
                + "NUMBER; BEGIN SHOW TRANSACTIONS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_transactions() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER "
                + "AS $$ DECLARE c NUMBER; BEGIN SHOW TRANSACTIONS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_grants() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c NUMBER; "
                + "BEGIN SHOW GRANTS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_grants() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW GRANTS; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_shows_tables() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c NUMBER; "
                + "BEGIN SHOW TABLES; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_shows_tables() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE c NUMBER; BEGIN SHOW TABLES; RETURN 0; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_alters_session() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN ALTER "
                + "SESSION SET QUERY_TAG = 'set-in-procedure'; RETURN 'altered'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_alters_session() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN ALTER SESSION SET QUERY_TAG = 'set-in-procedure'; RETURN 'altered'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_uses_schema() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN USE SCHEMA "
                + "test_schema; RETURN 'used'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_uses_schema() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN USE SCHEMA test_schema; RETURN 'used'; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE own_reads_current() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "CURRENT_SCHEMA() || ' / ' || CURRENT_USER() IS NOT NULL; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE clr_reads_current() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN CURRENT_SCHEMA() || ' / ' || CURRENT_USER() IS NOT NULL; END; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_own_reads() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT $v\"}); rs.next(); return rs.getColumnValue(1); $$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_own_sets() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ "
                + "snowflake.execute({sqlText: \"SET jsw = 9\"}); return \"set\"; $$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_clr_reads() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS CALLER AS "
                + "$$ var rs = snowflake.execute({sqlText: \"SELECT $v\"}); rs.next(); return rs.getColumnValue(1); "
                + "$$");
        answer(
            "CREATE OR REPLACE PROCEDURE js_clr_sets() RETURNS VARCHAR LANGUAGE JAVASCRIPT EXECUTE AS CALLER AS "
                + "$$ snowflake.execute({sqlText: \"SET jsw = 9\"}); return \"set\"; $$");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w2");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w3");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w4");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w5");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET jsw");
        assertAnswer(
            "Statement executed successfully.",
            "SET v = 5");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function CLR_READS_DOLLAR.",
            "CALL clr_reads_dollar()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "5",
            "CALL clr_reads_getvar()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function CLR_READS_BOTH.",
            "CALL clr_reads_both()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function CLR_READS_QUERY.",
            "CALL clr_reads_query()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function CLR_READS_MISSING.",
            "CALL clr_reads_missing()");
        assertAnswer(
            "5 none none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "set",
            "CALL clr_sets()");
        assertAnswer(
            "5 1 none none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "null",
            "CALL clr_sets_bare()");
        assertAnswer(
            "5 1 2 none none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "set",
            "CALL clr_sets_dynamic()");
        assertAnswer(
            "5 1 2 3 none none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "set",
            "CALL clr_sets_two()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "unset",
            "CALL clr_unsets()");
        assertAnswer(
            "none 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "Statement executed successfully.",
            "SET v = 5");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER SESSION UNSET QUERY_TAG");
        assertAnswer(
            "unset",
            "CALL clr_unsets_missing()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "1",
            "CALL clr_shows_variables_like()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "1",
            "CALL clr_shows_parameters()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "1",
            "CALL clr_shows_parameters_session()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "1",
            "CALL clr_shows_parameters_account()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "0",
            "CALL clr_shows_locks()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "0",
            "CALL clr_shows_transactions()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "0",
            "CALL clr_shows_grants()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "0",
            "CALL clr_shows_tables()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "altered",
            "CALL clr_alters_session()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "Statement executed successfully.",
            "SET v = 5");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER SESSION UNSET QUERY_TAG");
        assertAnswer(
            "used",
            "CALL clr_uses_schema()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "true",
            "CALL clr_reads_current()");
        assertAnswer(
            "5 1 2 3 4 none",
            "SELECT NVL(GETVARIABLE('V')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W')::VARCHAR, 'none') || ' "
                + "' || NVL(GETVARIABLE('W2')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('W3')::VARCHAR, 'none') || "
                + "' ' || NVL(GETVARIABLE('W4')::VARCHAR, 'none') || ' ' || NVL(GETVARIABLE('JSW')::VARCHAR, 'none')");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET v");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w2");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w3");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w4");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w5");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET jsw");
        assertAnswer(
            "Statement executed successfully.",
            "ALTER SESSION UNSET QUERY_TAG");
        assertAnswer(
            "Statement executed successfully.",
            "USE SCHEMA test_db.test_schema");
    }

    @Test
    public void aBodyCompilesAgainstTheSessionsVariables() {
        answer(
            "CREATE OR REPLACE TABLE sv_t (a INT)");
        assertAnswer(
            "Statement executed successfully.",
            "SET v = 5");
        assertAnswer(
            "Function OWN_READS_DOLLAR successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_dollar() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN $v; "
                + "END; $$");
        assertAnswer(
            "Function CLR_READS_DOLLAR successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_dollar() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN $v; END; $$");
        assertAnswer(
            "Function OWN_READS_QUERY successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT $v INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function CLR_READS_QUERY successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_query() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE r VARCHAR; BEGIN SELECT $v INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function OWN_READS_EXPR successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_expr() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET z := $v + "
                + "1; RETURN z; END; $$");
        assertAnswer(
            "Function CLR_READS_EXPR successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_expr() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN LET z := $v + 1; RETURN z; END; $$");
        assertAnswer(
            "Function OWN_READS_DYNAMIC successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_dynamic() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE rs "
                + "RESULTSET; BEGIN rs := (EXECUTE IMMEDIATE 'SELECT $v AS c'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "Function CLR_READS_DYNAMIC successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_dynamic() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE rs RESULTSET; BEGIN rs := (EXECUTE IMMEDIATE 'SELECT $v AS c'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "Function OWN_READS_GETVAR successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_getvar() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "GETVARIABLE('V'); END; $$");
        assertAnswer(
            "Function CLR_READS_GETVAR successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_getvar() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN RETURN GETVARIABLE('V'); END; $$");
        assertAnswer(
            "Function OWN_READS_GETVAR_MISSING successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_getvar_missing() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN NVL(GETVARIABLE('NOSUCH_VAR_X9'), 'none'); END; $$");
        assertAnswer(
            "Function CLR_READS_GETVAR_MISSING successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_getvar_missing() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS "
                + "CALLER AS $$ BEGIN RETURN NVL(GETVARIABLE('NOSUCH_VAR_X9'), 'none'); END; $$");
        assertAnswer(
            "Function OWN_READS_GETVAR_QUERY successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_getvar_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r "
                + "VARCHAR; BEGIN SELECT GETVARIABLE('V') INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function CLR_READS_GETVAR_QUERY successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_getvar_query() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER "
                + "AS $$ DECLARE r VARCHAR; BEGIN SELECT GETVARIABLE('V') INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function OWN_WRITES_TABLE successfully created.",
            "CREATE OR REPLACE PROCEDURE own_writes_table() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN INSERT INTO "
                + "sv_t VALUES ($v); RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "Function CLR_WRITES_TABLE successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_writes_table() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "BEGIN INSERT INTO sv_t VALUES ($v); RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "Function OWN_READS_IN_HANDLER successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_in_handler() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN RETURN $v; END; $$");
        assertAnswer(
            "Function CLR_READS_IN_HANDLER successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_in_handler() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN $v; END; $$");
        assertAnswer(
            "Function OWN_READS_BARE successfully created.",
            "CREATE OR REPLACE PROCEDURE own_reads_bare() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT $v; $$");
        assertAnswer(
            "Function CLR_READS_BARE successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_reads_bare() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "SELECT $v; $$");
        assertAnswer(
            "Function OWN_CALLS_CLR successfully created.",
            "CREATE OR REPLACE PROCEDURE own_calls_clr() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN CALL clr_reads_dollar() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function OWN_CALLS_CLR_GETVAR successfully created.",
            "CREATE OR REPLACE PROCEDURE own_calls_clr_getvar() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r "
                + "VARCHAR; BEGIN CALL clr_reads_getvar() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function CLR_SETS successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_sets() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "SET w = 1; RETURN 'set'; END; $$");
        assertAnswer(
            "Function OWN_CALLS_CLR_SETS successfully created.",
            "CREATE OR REPLACE PROCEDURE own_calls_clr_sets() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r "
                + "VARCHAR; BEGIN CALL clr_sets() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function CLR_CALLS_OWN successfully created.",
            "CREATE OR REPLACE PROCEDURE clr_calls_own() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ "
                + "DECLARE r VARCHAR; BEGIN CALL own_reads_getvar() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function JS_OWN_READS successfully created.",
            "CREATE OR REPLACE PROCEDURE js_own_reads() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT $v\"}); rs.next(); return rs.getColumnValue(1); $$");
        assertAnswer(
            "Function JS_OWN_GETVAR successfully created.",
            "CREATE OR REPLACE PROCEDURE js_own_getvar() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT GETVARIABLE('V')\"}); rs.next(); return "
                + "rs.getColumnValue(1); $$");
        assertAnswer(
            "Function JS_OWN_SHOWS successfully created.",
            "CREATE OR REPLACE PROCEDURE js_own_shows() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SHOW VARIABLES\"}); return \"shown\"; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_reads_dollar()");
        assertAnswer(
            "5",
            "CALL clr_reads_dollar()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_reads_query()");
        assertAnswer(
            "5",
            "CALL clr_reads_query()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 16 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_reads_expr()");
        assertAnswer(
            "6",
            "CALL clr_reads_expr()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 |  Declared return type 'SCALAR' is "
                + "incompatible with actual return type 'TABLE'",
            "CALL own_reads_dynamic()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 |  Declared return type 'SCALAR' is "
                + "incompatible with actual return type 'TABLE'",
            "CALL clr_reads_dynamic()");
        assertAnswer(
            "5",
            "CALL own_reads_getvar()");
        assertAnswer(
            "5",
            "CALL clr_reads_getvar()");
        assertAnswer(
            "none",
            "CALL own_reads_getvar_missing()");
        assertAnswer(
            "none",
            "CALL clr_reads_getvar_missing()");
        assertAnswer(
            "5",
            "CALL own_reads_getvar_query()");
        assertAnswer(
            "5",
            "CALL clr_reads_getvar_query()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_writes_table()");
        assertAnswer(
            "1",
            "CALL clr_writes_table()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_reads_in_handler()");
        assertAnswer(
            "5",
            "CALL clr_reads_in_handler()");
        assertAnswer(
            "null",
            "CALL clr_reads_bare()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_calls_clr()");
        assertAnswer(
            "5",
            "CALL own_calls_clr_getvar()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 7 : Stored procedure execution error: Unsupported "
                + "statement type 'SET'.",
            "CALL own_calls_clr_sets()");
        assertAnswer(
            "none",
            "SELECT NVL(GETVARIABLE('W')::VARCHAR, 'none')");
        assertAnswer(
            "5",
            "CALL clr_calls_own()");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET v");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Session variable '$V' does not exist",
            "CALL own_reads_dollar()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Session variable '$V' does not exist",
            "CALL clr_reads_dollar()");
        assertAnswer(
            "null",
            "CALL own_reads_getvar()");
        assertAnswer(
            "null",
            "CALL clr_reads_getvar()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | Session variable '$V' does not exist",
            "CALL own_reads_query()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | Session variable '$V' does not exist",
            "CALL clr_reads_query()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Session variable '$V' does not exist",
            "CREATE OR REPLACE PROCEDURE own_late() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN $v; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Session variable '$V' does not exist",
            "CREATE OR REPLACE PROCEDURE clr_late() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN "
                + "RETURN $v; END; $$");
        assertAnswer(
            "Function OWN_LATE_DYNAMIC successfully created.",
            "CREATE OR REPLACE PROCEDURE own_late_dynamic() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE rs "
                + "RESULTSET; BEGIN rs := (EXECUTE IMMEDIATE 'SELECT $v AS c'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "Statement executed successfully.",
            "SET v = 7");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 |  Declared return type 'SCALAR' is "
                + "incompatible with actual return type 'TABLE'",
            "CALL own_late_dynamic()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Use of session "
                + "variable '$V' is not allowed in owners rights stored procedure",
            "CALL own_reads_dollar()");
        assertAnswer(
            "7",
            "CALL clr_reads_dollar()");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET v");
        assertAnswer(
            "Statement executed successfully.",
            "UNSET w");
    }
}
