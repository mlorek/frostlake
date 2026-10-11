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
 * A procedure's body is a scope of its own, whatever calls it. It sees its parameters and what it declares, and
 * nothing of a block or a procedure that calls it: a caller's variable or parameter that it reads, assigns or
 * binds as {@code :name} is {@code invalid identifier} while the body compiles - also in a branch that never
 * runs, also for a procedure that runs with its caller's rights -, and the calling block names that failure at
 * its own CALL, one {@code Uncaught exception of type 'STATEMENT_ERROR'} sentence for each body the failure
 * leaves. A name the body declares or takes as a parameter hides nothing of the caller's and changes nothing of
 * it. The body handles nothing its caller is handling: SQLERRM is no name in it, SQLCODE reads NULL, and a RAISE
 * that names nothing is refused. A handler's own SQLERRM is what it was once a procedure it called has handled
 * an exception of its own. Every answer is live-verified.
 */
public class CalledProcedureScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aBodySeesItsOwnNamesWhenCalledAlone() {
        assertAnswer(
            "Function READS_CALLER successfully created.",
            "CREATE OR REPLACE PROCEDURE reads_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; END; $$");
        assertAnswer(
            "Function WRITES_CALLER successfully created.",
            "CREATE OR REPLACE PROCEDURE writes_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN x := "
                + "'clobbered'; RETURN 'done'; END; $$");
        assertAnswer(
            "Function BINDS_CALLER successfully created.",
            "CREATE OR REPLACE PROCEDURE binds_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT :x INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function JS_BINDS_CALLER successfully created.",
            "CREATE OR REPLACE PROCEDURE js_binds_caller() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT :x\"}); rs.next(); return rs.getColumnValue(1); $$");
        assertAnswer(
            "Function OUTER_P successfully created.",
            "CREATE OR REPLACE PROCEDURE outer_p(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN CALL reads_caller() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function READS_SQLERRM successfully created.",
            "CREATE OR REPLACE PROCEDURE reads_sqlerrm() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLERRM, 'nothing'); END; $$");
        assertAnswer(
            "Function READS_SQLCODE successfully created.",
            "CREATE OR REPLACE PROCEDURE reads_sqlcode() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLCODE::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "Function RERAISER successfully created.",
            "CREATE OR REPLACE PROCEDURE reraiser() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RAISE; END; $$");
        assertAnswer(
            "Function UDF_READS_CALLER successfully created.",
            "CREATE OR REPLACE FUNCTION udf_reads_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; "
                + "END; $$");
        assertAnswer(
            "Function OWN_X successfully created.",
            "CREATE OR REPLACE PROCEDURE own_x() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE x VARCHAR DEFAULT "
                + "'callee'; BEGIN x := x || ' changed'; RETURN x; END; $$");
        assertAnswer(
            "Function PARAM_X successfully created.",
            "CREATE OR REPLACE PROCEDURE param_x(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN x := x || ' "
                + "changed'; RETURN x; END; $$");
        assertAnswer(
            "Function LETS_X successfully created.",
            "CREATE OR REPLACE PROCEDURE lets_x() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET x VARCHAR := 'let "
                + "in callee'; RETURN x; END; $$");
        assertAnswer(
            "Function INNER_HANDLES successfully created.",
            "CREATE OR REPLACE PROCEDURE inner_handles() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN RETURN 'inner: ' || SQLERRM; END; $$");
        assertAnswer(
            "Function INNER_RAISES successfully created.",
            "CREATE OR REPLACE PROCEDURE inner_raises() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE e EXCEPTION "
                + "(-20001, 'raised by callee'); BEGIN RAISE e; END; $$");
        assertAnswer(
            "Function TWO_LEVELS successfully created.",
            "CREATE OR REPLACE PROCEDURE two_levels() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "CALL outer_p('middle parameter') INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function CALLER_RIGHTS_READS successfully created.",
            "CREATE OR REPLACE PROCEDURE caller_rights_reads() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN RETURN x; END; $$");
        assertAnswer(
            "Function SELECTS_X successfully created.",
            "CREATE OR REPLACE PROCEDURE selects_x() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "SELECT x INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function DYN_READS successfully created.",
            "CREATE OR REPLACE PROCEDURE dyn_reads() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "EXECUTE IMMEDIATE 'SELECT :x' ; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'X'",
            "CALL reads_caller()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | invalid identifier 'X'",
            "CALL writes_caller()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'X'",
            "CALL binds_caller()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "CALL outer_p('outer parameter')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'SQLERRM'",
            "CALL reads_sqlerrm()");
        assertAnswer(
            "callee sees: nothing",
            "CALL reads_sqlcode()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | RAISE without specifying exception only "
                + "allowed inside a handler.",
            "CALL reraiser()");
        assertAnswer(
            "callee changed",
            "CALL own_x()");
        assertAnswer(
            "arg changed",
            "CALL param_x('arg')");
        assertAnswer(
            "let in callee",
            "CALL lets_x()");
        assertAnswer(
            "inner: Division by zero",
            "CALL inner_handles()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 57 : raised by callee",
            "CALL inner_raises()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error: error line 1 at "
                + "position 14 | invalid identifier 'X'",
            "CALL two_levels()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'X'",
            "CALL caller_rights_reads()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'X'",
            "CALL selects_x()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :x not set.",
            "CALL dyn_reads()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'X'",
            "SELECT udf_reads_caller()");
    }

    @Test
    public void aBodySeesNothingOfTheBlockThatCallsIt() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE reads_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE writes_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN x := "
                + "'clobbered'; RETURN 'done'; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE binds_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT :x INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE js_binds_caller() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ var rs = "
                + "snowflake.execute({sqlText: \"SELECT :x\"}); rs.next(); return rs.getColumnValue(1); $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE outer_p(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN CALL reads_caller() INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE reads_sqlerrm() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLERRM, 'nothing'); END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE reads_sqlcode() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLCODE::VARCHAR, 'nothing'); END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE reraiser() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RAISE; END; $$");
        engine.execute(
            "CREATE OR REPLACE FUNCTION udf_reads_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; "
                + "END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE own_x() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE x VARCHAR DEFAULT "
                + "'callee'; BEGIN x := x || ' changed'; RETURN x; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE param_x(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN x := x || ' "
                + "changed'; RETURN x; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE lets_x() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN LET x VARCHAR := 'let "
                + "in callee'; RETURN x; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE inner_handles() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN RETURN 'inner: ' || SQLERRM; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE inner_raises() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE e EXCEPTION "
                + "(-20001, 'raised by callee'); BEGIN RAISE e; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE two_levels() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "CALL outer_p('middle parameter') INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE caller_rights_reads() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
                + "$$ BEGIN RETURN x; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE selects_x() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "SELECT x INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE dyn_reads() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; BEGIN "
                + "EXECUTE IMMEDIATE 'SELECT :x' ; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL reads_caller() "
                + "INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: error line 1 at position 9 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'orig'; BEGIN CALL writes_caller(); RETURN x; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                + "error: error line 1 at position 33 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL binds_caller() "
                + "INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL "
                + "caller_rights_reads() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error: error line 1 at "
                + "position 7 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL selects_x() "
                + "INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error: error line 1 at "
                + "position 7 | Bind variable :x not set.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL dyn_reads() "
                + "INTO :r; RETURN r; END; $$");
        assertAnswer(
            "caller value / callee changed",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL own_x() INTO "
                + ":r; RETURN x || ' / ' || r; END; $$");
        assertAnswer(
            "caller value / caller value changed",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL param_x(:x) "
                + "INTO :r; RETURN x || ' / ' || r; END; $$");
        assertAnswer(
            "caller value / let in callee",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL lets_x() INTO "
                + ":r; RETURN x || ' / ' || r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 60 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 26 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 26 : SQL compilation error: error line 1 at position 14 | invalid "
                + "identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN CALL two_levels() "
                + "INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'caller value'; r VARCHAR; BEGIN RETURN "
                + "udf_reads_caller(); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 37 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL "
                + "reads_sqlerrm() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "callee sees: nothing",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL "
                + "reads_sqlcode() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : SQL compilation "
                + "error: error line 1 at position 7 | RAISE without specifying exception only allowed inside a "
                + "handler.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL reraiser(); RETURN 'after'; "
                + "END; $$");
        assertAnswer(
            "inner: Division by zero / outer: Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL "
                + "inner_handles() INTO :r; RETURN r || ' / outer: ' || SQLERRM; END; $$");
        assertAnswer(
            "caught: Uncaught exception of type 'E' on line 1 at position 57 : raised by callee / -20001",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN CALL "
                + "inner_raises(); EXCEPTION WHEN OTHER THEN RETURN 'caught: ' || SQLERRM || ' / ' || SQLCODE; END; "
                + "END; $$");
        assertAnswer(
            "caught: Uncaught exception of type 'E' on line 1 at position 57 : raised by callee / -20001 / P0001",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN CALL inner_raises(); EXCEPTION WHEN OTHER THEN RETURN "
                + "'caught: ' || SQLERRM || ' / ' || SQLCODE || ' / ' || SQLSTATE; END; $$");
        assertAnswer(
            "caught as other: Uncaught exception of type 'E' on line 1 at position 57 : raised by callee",
            "EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'the caller''s own'); r VARCHAR; BEGIN CALL "
                + "inner_raises(); EXCEPTION WHEN e THEN RETURN 'caught by name: ' || SQLERRM; WHEN OTHER THEN "
                + "RETURN 'caught as other: ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; BEGIN EXECUTE IMMEDIATE 'BEGIN x := "
                + "''changed by dynamic block''; END;'; RETURN x; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid expression value (?SqlExecuteImmediateDynamic?) for "
                + "assignment.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN r := (EXECUTE IMMEDIATE "
                + "'BEGIN RETURN x; END;'); RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; BEGIN EXECUTE IMMEDIATE 'BEGIN RETURN x; "
                + "END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 63 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :x not set.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; rs RESULTSET; BEGIN rs := (EXECUTE IMMEDIATE "
                + "'SELECT :x AS c'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "outer",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; rs RESULTSET; BEGIN rs := (EXECUTE IMMEDIATE "
                + "'SELECT ? AS c' USING (x)); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "outer",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; rs RESULTSET; BEGIN rs := (EXECUTE IMMEDIATE "
                + "'SELECT :1 AS c' USING (x)); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 23 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; BEGIN EXECUTE IMMEDIATE 'BEGIN LET y VARCHAR "
                + ":= :1; RETURN y; END;' USING (x); RETURN 'ran'; END; $$");
        assertAnswer(
            "outer",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; BEGIN EXECUTE IMMEDIATE 'DECLARE x VARCHAR "
                + "DEFAULT ''inner''; BEGIN x := ''inner changed''; END;'; RETURN x; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : SQL compilation "
                + "error: error line 1 at position 6 | RAISE without specifying exception only allowed inside a "
                + "handler.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN EXECUTE IMMEDIATE 'BEGIN RAISE; "
                + "END;'; RETURN 'after'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid expression value (?SqlExecuteImmediateDynamic?) for "
                + "assignment.",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN r := (EXECUTE "
                + "IMMEDIATE 'BEGIN RETURN NVL(SQLERRM, ''nothing''); END;'); RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation error: error line 1 at "
                + "position 14 | invalid identifier 'X'",
            "CALL two_levels()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "CALL outer_p('again')");
    }

    @Test
    public void whatTheCallingBlockSeesOfABodysFailure() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE reads_caller() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE stmt_bind() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT "
                + ":nosuch_bind_x9; RETURN 1; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE bare_bind() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT :x; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE bare_name() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT x; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE untaken() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (1 = 2) THEN "
                + "RETURN x; END IF; RETURN 'ok'; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE decl_then_calls() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE x VARCHAR "
                + "DEFAULT 'v'; r VARCHAR; BEGIN CALL reads_caller() INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE param_sqlcode(sqlcode VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN sqlcode; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE param_sqlerrm(sqlerrm VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN "
                + "RETURN sqlerrm; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH_BIND_X9'",
            "CALL stmt_bind()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'X'",
            "CALL untaken()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 49 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "CALL decl_then_calls()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'NOSUCH_BIND_X9'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN CALL stmt_bind() INTO :r; "
                + "RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 30 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN CALL untaken() INTO :r; "
                + "RETURN r; END; $$");
    }
}
