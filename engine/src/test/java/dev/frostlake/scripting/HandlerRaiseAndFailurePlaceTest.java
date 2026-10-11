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
 * A RAISE that names nothing raises again the exception its handler is handling, from the handler's own
 * statements and from a branch, a loop or a block written in it, and the block's failure keeps the place of the
 * statement that first raised it. Outside a handler it is refused while the block compiles: {@code RAISE without
 * specifying exception only allowed inside a handler.} A statement of a handler that fails with an exception of
 * its own is the failure the block lets out, named at that statement's place and by its kind. A RETURN, a BREAK
 * and a CONTINUE reached in a branch or a loop of a handler end the handler there. Every answer is
 * live-verified.
 */
public class HandlerRaiseAndFailurePlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aBareRaiseAndAFailureInsideAHandler() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (1 = 1) THEN RAISE; END IF; END; "
                + "$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CASE WHEN 1 = 1 THEN RAISE; END "
                + "CASE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN WHILE (1 = 1) DO RAISE; END WHILE; "
                + "END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : Numeric value 'x' is "
                + "not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN SELECT 1 + 'x'; EXCEPTION "
                + "WHEN OTHER THEN RAISE; END; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN SELECT 1 + 'x'; EXCEPTION "
                + "WHEN OTHER THEN LET z := 1; END; RAISE; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | RAISE without specifying exception only "
                + "allowed inside a handler.",
            "EXECUTE IMMEDIATE $$ BEGIN IF (1 = 2) THEN RAISE; END IF; RETURN 1; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 45 : mine",
            "EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20002, 'mine'); BEGIN RAISE e; EXCEPTION WHEN e THEN "
                + "RAISE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 45 : mine",
            "EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20002, 'mine'); BEGIN RAISE e; EXCEPTION WHEN e THEN FOR "
                + "i IN 1 TO 2 DO RAISE; END FOR; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : Numeric value 'x' is "
                + "not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN SELECT 1 + 'x'; RETURN 'after'; "
                + "END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 57 : Numeric value 'x' is "
                + "not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET z := 1; SELECT 1 + 'x'; RETURN "
                + "'after'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN 1 / 0; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : Division by zero",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET z INT := 1 / 0; RETURN z; END; "
                + "$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 61 : Numeric value 'x' is "
                + "not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (1 = 1) THEN SELECT 1 + 'x'; END "
                + "IF; RETURN 'after'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 51 : Numeric value 'x' is "
                + "not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN BEGIN SELECT 1 + 'x'; END; RETURN "
                + "'after'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : Numeric value 'x' is "
                + "not recognized",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN EXECUTE IMMEDIATE 'SELECT 1 + "
                + "''x'''; RETURN 'after'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : SQL compilation "
                + "error: | Unknown function NOSUCH_PROC_X9.",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL nosuch_proc_x9(); RETURN "
                + "'after'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 83 : mine",
            "EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20002, 'mine'); BEGIN SELECT 1/0; EXCEPTION WHEN OTHER "
                + "THEN RAISE e; END; $$");
        assertAnswer(
            "Numeric value 'x' is not recognized / 100038",
            "EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN SELECT 1 + "
                + "'x'; END; EXCEPTION WHEN OTHER THEN RETURN SQLERRM || ' / ' || SQLCODE; END; $$");
    }

    @Test
    public void aReturnABreakAndAContinueEndTheHandler() {
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
            "matched",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (SQLSTATE = '22012') THEN RETURN "
                + "'matched'; END IF; RETURN 'no'; END; $$");
        assertAnswer(
            "no",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN IF (1 = 2) THEN RETURN 'matched'; "
                + "END IF; RETURN 'no'; END; $$");
        assertAnswer(
            "at 2",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN FOR i IN 1 TO 3 DO IF (i = 2) THEN "
                + "RETURN 'at ' || i; END IF; END FOR; RETURN 'no'; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE n INT DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO BEGIN SELECT 1/0; EXCEPTION "
                + "WHEN OTHER THEN IF (i = 2) THEN BREAK; END IF; n := n + 1; END; END FOR; RETURN n; END; $$");
        assertAnswer(
            "20",
            "EXECUTE IMMEDIATE $$ DECLARE n INT DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO BEGIN SELECT 1/0; EXCEPTION "
                + "WHEN OTHER THEN IF (i = 2) THEN CONTINUE; END IF; n := n + 10; END; END FOR; RETURN n; END; $$");
        assertAnswer(
            "matched",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET s := SQLSTATE; IF (s = '22012') "
                + "THEN RETURN 'matched'; END IF; RETURN 'no'; END; $$");
        assertAnswer(
            "matched",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CASE WHEN SQLCODE = 100051 THEN "
                + "RETURN 'matched'; ELSE RETURN 'no'; END CASE; END; $$");
    }
}
