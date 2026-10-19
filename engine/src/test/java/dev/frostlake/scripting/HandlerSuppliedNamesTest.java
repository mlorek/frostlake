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
 * The names a script is given without declaring them. SQLERRM and SQLSTATE are names inside an exception handler
 * alone - in its statements and in the blocks, the branches and the loops written in it: anywhere else each is
 * {@code invalid identifier} while the block compiles, in an expression, a call's argument and a bind alike, on
 * a branch that never runs too, unless the block declares a variable of that name. SQLCODE, SQLROWCOUNT,
 * SQLFOUND, SQLNOTFOUND and SQLID are variables of the body's own, NULL until something sets them: the body's top
 * block may not declare one again ({@code Variable with name 'SQLCODE' declared twice.}), a block nested in it
 * may, and no statement assigns one ({@code Assignment to variable 'SQLCODE' is not permitted.}), nor
 * ACTIVITY_COUNT. A procedure called from a handler, and a text the handler runs, has none of the handler's
 * names. Every answer is live-verified.
 */
public class HandlerSuppliedNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void whereTheHandlersNamesAreNames() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        assertAnswer(
            "Function P_SQLSTATE successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlstate() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLSTATE, 'nothing'); END; $$");
        assertAnswer(
            "Function P_SQLCODE_PLAIN successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlcode_plain() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SQLCODE; END; $$");
        assertAnswer(
            "Function P_ROWCOUNT successfully created.",
            "CREATE OR REPLACE PROCEDURE p_rowcount() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLROWCOUNT::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "Function P_FOUND successfully created.",
            "CREATE OR REPLACE PROCEDURE p_found() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee sees: "
                + "' || NVL(SQLFOUND::VARCHAR, 'nothing') || ' ' || NVL(SQLNOTFOUND::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "Function P_OWN_ROWCOUNT successfully created.",
            "CREATE OR REPLACE PROCEDURE p_own_rowcount() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN UPDATE sc_t "
                + "SET a = a WHERE a = 1; RETURN 'callee did: ' || SQLROWCOUNT; END; $$");
        assertAnswer(
            "Function P_RAISE_NESTED successfully created.",
            "CREATE OR REPLACE PROCEDURE p_raise_nested() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN BEGIN RAISE; END; END; $$");
        assertAnswer(
            "Function P_RAISE_IN_LOOP successfully created.",
            "CREATE OR REPLACE PROCEDURE p_raise_in_loop() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN FOR i IN 1 TO 1 DO RAISE; END FOR; END; $$");
        assertAnswer(
            "Function P_RAISE_AFTER successfully created.",
            "CREATE OR REPLACE PROCEDURE p_raise_after() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RAISE; END; $$");
        assertAnswer(
            "Function P_SQLERRM_NESTED successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_nested() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN BEGIN RETURN 'nested: ' || SQLERRM; END; END; $$");
        assertAnswer(
            "Function P_SQLERRM_AFTER successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_after() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RETURN SQLERRM; END; $$");
        assertAnswer(
            "Function P_SQLCODE_AFTER successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlcode_after() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RETURN NVL(SQLCODE::VARCHAR, 'nothing') || ' ' || "
                + "NVL(SQLSTATE, 'nothing'); END; $$");
        assertAnswer(
            "Function P_SQLERRM_BRANCH successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_branch() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (1 = 2) "
                + "THEN RETURN SQLERRM; END IF; RETURN 'not reached'; END; $$");
        assertAnswer(
            "Function P_SQLERRM_QUERY successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT :SQLERRM INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function P_SQLCODE_QUERY successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlcode_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT NVL(:SQLCODE::VARCHAR, 'nothing') INTO :r; RETURN r; END; $$");
        assertAnswer(
            "Function P_SQLERRM_DECLARED successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_declared() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE sqlerrm "
                + "VARCHAR DEFAULT 'mine'; BEGIN RETURN sqlerrm; END; $$");
        assertAnswer(
            "Function P_SQLCODE_DECLARED successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlcode_declared() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE sqlcode "
                + "VARCHAR DEFAULT 'mine'; BEGIN RETURN sqlcode; END; $$");
        assertAnswer(
            "Function P_SQLCODE_ASSIGN successfully created.",
            "CREATE OR REPLACE PROCEDURE p_sqlcode_assign() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN sqlcode := "
                + "5; RETURN sqlcode; END; $$");
        assertAnswer(
            "Function P_HANDLER_CODES successfully created.",
            "CREATE OR REPLACE PROCEDURE p_handler_codes() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        assertAnswer(
            "Function P_CALLS_IN_HANDLER successfully created.",
            "CREATE OR REPLACE PROCEDURE p_calls_in_handler() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r "
                + "VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL p_sqlstate() INTO :r; RETURN r || ' / ' "
                + "|| SQLSTATE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | invalid identifier 'SQLSTATE'",
            "CALL p_sqlstate()");
        assertAnswer(
            "null",
            "CALL p_sqlcode_plain()");
        assertAnswer(
            "callee sees: nothing",
            "CALL p_rowcount()");
        assertAnswer(
            "callee sees: nothing nothing",
            "CALL p_found()");
        assertAnswer(
            "callee did: 1",
            "CALL p_own_rowcount()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "CALL p_raise_nested()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "CALL p_raise_in_loop()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 68 | RAISE without specifying exception only "
                + "allowed inside a handler.",
            "CALL p_raise_after()");
        assertAnswer(
            "nested: Division by zero",
            "CALL p_sqlerrm_nested()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 75 | invalid identifier 'SQLERRM'",
            "CALL p_sqlerrm_after()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 122 | invalid identifier 'SQLSTATE'",
            "CALL p_sqlcode_after()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'SQLERRM'",
            "CALL p_sqlerrm_branch()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'SQLERRM'",
            "CALL p_sqlerrm_query()");
        assertAnswer(
            "nothing",
            "CALL p_sqlcode_query()");
        assertAnswer(
            "mine",
            "CALL p_sqlerrm_declared()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Variable with name 'SQLCODE' declared "
                + "twice.",
            "CALL p_sqlcode_declared()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 |  Assignment to variable 'SQLCODE' is "
                + "not permitted.",
            "CALL p_sqlcode_assign()");
        assertAnswer(
            "100051 22012 Division by zero",
            "CALL p_handler_codes()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 37 | invalid identifier 'SQLSTATE'",
            "CALL p_calls_in_handler()");
        assertAnswer(
            "nothing",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN NVL(SQLCODE::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN NVL(SQLSTATE, 'nothing'); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN NVL(SQLERRM, 'nothing'); END; $$");
        assertAnswer(
            "nothing",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN NVL(SQLROWCOUNT::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "nothing",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN NVL(SQLFOUND::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | RAISE without specifying exception only "
                + "allowed inside a handler.",
            "EXECUTE IMMEDIATE $$ BEGIN RAISE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | RAISE without specifying exception only "
                + "allowed inside a handler.",
            "EXECUTE IMMEDIATE $$ BEGIN LET x := 1; RAISE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN RAISE; END; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RAISE; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN UPDATE sc_t SET a = a; CALL p_rowcount() INTO :r; "
                + "RETURN r || ' / ' || SQLROWCOUNT; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN UPDATE sc_t SET a = a; CALL p_found() INTO :r; RETURN "
                + "r || ' / ' || SQLFOUND; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN UPDATE sc_t SET a = a; CALL p_own_rowcount() INTO :r; "
                + "RETURN r || ' / ' || SQLROWCOUNT; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN UPDATE sc_t SET a = a; CALL p_sqlcode_plain() INTO :r; "
                + "RETURN NVL(r, 'null') || ' / ' || SQLROWCOUNT; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : SQL compilation "
                + "error: error line 1 at position 37 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL "
                + "p_sqlstate() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "100051 22012 Division by zero / 100051",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL "
                + "p_handler_codes() INTO :r; RETURN r || ' / ' || SQLCODE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | Unknown function NOSUCH_PROC_X9.",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN CALL nosuch_proc_x9() INTO :r; RETURN r; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 64 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL "
                + "p_raise_nested() INTO :r; RETURN r; END; $$");
    }

    @Test
    public void theNamesInEveryPlaceAnExpressionStandsAndAsDeclarations() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN IF (SQLERRM IS NULL) THEN RETURN 1; END IF; RETURN 2; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ BEGIN WHILE (SQLSTATE = 'x') DO RETURN 1; END WHILE; RETURN 2; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN LET m VARCHAR := SQLERRM; RETURN m; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN LET m VARCHAR := UPPER(TRIM(SQLERRM)); RETURN m; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ DECLARE m VARCHAR DEFAULT SQLERRM; BEGIN RETURN m; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN CASE WHEN SQLSTATE = 'x' THEN 1 ELSE 2 END; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ BEGIN LET m VARCHAR := (SELECT :SQLSTATE); RETURN m; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO sc_t SELECT 4 WHERE :SQLERRM IS NULL; RETURN 1; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN SQLERRM || nosuch_var_x9; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier 'NOSUCH_VAR_X9'",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN nosuch_var_x9 || SQLERRM; END; $$");
        assertAnswer(
            "DIVISION BY ZERO 22012",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN UPPER(SQLERRM) || ' ' || "
                + "NVL(SQLSTATE, 'x'); END; $$");
        assertAnswer(
            "matched",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (SQLSTATE = '22012') THEN RETURN "
                + "'matched'; END IF; RETURN 'no'; END; $$");
        assertAnswer(
            "Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN FOR i IN 1 TO 1 DO RETURN SQLERRM; "
                + "END FOR; END; $$");
        assertAnswer(
            "Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN DECLARE m VARCHAR DEFAULT SQLERRM; "
                + "BEGIN RETURN m; END; END; $$");
        assertAnswer(
            "Numeric value 'x' is not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN SELECT 1 + 'x'; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLERRM; END; END; $$");
        assertAnswer(
            "Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN SELECT 1 + 'x'; EXCEPTION "
                + "WHEN OTHER THEN LET z := 1; END; RETURN SQLERRM; END; $$");
        assertAnswer(
            "nothing",
            "EXECUTE IMMEDIATE $$ BEGIN BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RETURN "
                + "NVL(SQLCODE::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "true",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN SQLCODE IS NULL; END; $$");
        assertAnswer(
            "nothing",
            "EXECUTE IMMEDIATE $$ BEGIN LET c := SQLCODE; RETURN NVL(c::VARCHAR, 'nothing'); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Variable with name 'SQLCODE' declared "
                + "twice.",
            "EXECUTE IMMEDIATE $$ DECLARE SQLCODE VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLCODE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Variable with name 'SQLCODE' declared "
                + "twice.",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLCODE VARCHAR := 'mine'; RETURN SQLCODE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 |  Assignment to variable 'SQLCODE' is "
                + "not permitted.",
            "EXECUTE IMMEDIATE $$ BEGIN SQLCODE := 'mine'; RETURN SQLCODE; END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ DECLARE SQLERRM VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLERRM; END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLERRM VARCHAR := 'mine'; RETURN SQLERRM; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ BEGIN SQLERRM := 'mine'; RETURN SQLERRM; END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ DECLARE SQLSTATE VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLSTATE; END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLSTATE VARCHAR := 'mine'; RETURN SQLSTATE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ BEGIN SQLSTATE := 'mine'; RETURN SQLSTATE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Variable with name 'SQLROWCOUNT' "
                + "declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE SQLROWCOUNT VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Variable with name 'SQLROWCOUNT' "
                + "declared twice.",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLROWCOUNT VARCHAR := 'mine'; RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 |  Assignment to variable 'SQLROWCOUNT' "
                + "is not permitted.",
            "EXECUTE IMMEDIATE $$ BEGIN SQLROWCOUNT := 'mine'; RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Variable with name 'SQLFOUND' declared "
                + "twice.",
            "EXECUTE IMMEDIATE $$ DECLARE SQLFOUND VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLFOUND; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Variable with name 'SQLFOUND' declared "
                + "twice.",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLFOUND VARCHAR := 'mine'; RETURN SQLFOUND; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 |  Assignment to variable 'SQLFOUND' is "
                + "not permitted.",
            "EXECUTE IMMEDIATE $$ BEGIN SQLFOUND := 'mine'; RETURN SQLFOUND; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Variable with name 'SQLNOTFOUND' "
                + "declared twice.",
            "EXECUTE IMMEDIATE $$ DECLARE SQLNOTFOUND VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLNOTFOUND; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Variable with name 'SQLNOTFOUND' "
                + "declared twice.",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLNOTFOUND VARCHAR := 'mine'; RETURN SQLNOTFOUND; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 |  Assignment to variable 'SQLNOTFOUND' "
                + "is not permitted.",
            "EXECUTE IMMEDIATE $$ BEGIN SQLNOTFOUND := 'mine'; RETURN SQLNOTFOUND; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 |  Variable with name 'SQLID' declared "
                + "twice.",
            "EXECUTE IMMEDIATE $$ DECLARE SQLID VARCHAR DEFAULT 'mine'; BEGIN RETURN SQLID; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 |  Variable with name 'SQLID' declared "
                + "twice.",
            "EXECUTE IMMEDIATE $$ BEGIN LET SQLID VARCHAR := 'mine'; RETURN SQLID; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 |  Assignment to variable 'SQLID' is not "
                + "permitted.",
            "EXECUTE IMMEDIATE $$ BEGIN SQLID := 'mine'; RETURN SQLID; END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ DECLARE ACTIVITY_COUNT VARCHAR DEFAULT 'mine'; BEGIN RETURN ACTIVITY_COUNT; "
                + "END; $$");
        assertAnswer(
            "mine",
            "EXECUTE IMMEDIATE $$ BEGIN LET ACTIVITY_COUNT VARCHAR := 'mine'; RETURN ACTIVITY_COUNT; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 |  Assignment to variable "
                + "'ACTIVITY_COUNT' is not permitted.",
            "EXECUTE IMMEDIATE $$ BEGIN ACTIVITY_COUNT := 'mine'; RETURN ACTIVITY_COUNT; END; $$");
    }

    @Test
    public void aNestedBlockAndAParameterOfASuppliedName() {
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
            "x",
            "EXECUTE IMMEDIATE $$ BEGIN BEGIN LET sqlcode VARCHAR := 'x'; RETURN sqlcode; END; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ BEGIN DECLARE sqlrowcount INT DEFAULT 1; BEGIN RETURN sqlrowcount; END; END; $$");
        assertAnswer(
            "2",
            "EXECUTE IMMEDIATE $$ DECLARE activity_count INT DEFAULT 1; BEGIN activity_count := 2; RETURN "
                + "activity_count; END; $$");
        assertAnswer(
            "set",
            "EXECUTE IMMEDIATE $$ DECLARE sqlerrm VARCHAR DEFAULT 'mine'; BEGIN sqlerrm := 'set'; RETURN sqlerrm; "
                + "END; $$");
        assertAnswer(
            "Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE sqlerrm VARCHAR DEFAULT 'mine'; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN RETURN sqlerrm; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 |  Variable with name 'SQLCODE' declared "
                + "twice.",
            "CALL param_sqlcode('v')");
        assertAnswer(
            "v",
            "CALL param_sqlerrm('v')");
    }
}
