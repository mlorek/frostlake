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
 * A text that EXECUTE IMMEDIATE runs inside a block is a body of its own. It reads, assigns and binds none of
 * the block's variables, and has none of the names of a handler that runs it; what it needs is handed to it by
 * USING, and to a statement alone: a text that is itself a block binds nothing of the list, its {@code ?} being
 * an unnamed bind and its {@code :1} a name nothing declares, each refused as the block compiles - as they are in
 * any block. Its top block words what it lets out as a script's does, and the statement that ran it names that
 * failure at its own place, so a failure carries one sentence for each body it leaves; a handler of the calling
 * block reads that sentence as SQLERRM, with the code and the state of what was raised. Written as a value, to
 * fill a RESULTSET, the text runs the same way. Every answer is live-verified.
 */
public class DynamicTextFrameTest extends MeasuredCellsTestSupport {

    @Test
    public void aDynamicBlockSeesNothingOfTheBlockThatRunsIt() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlstate() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLSTATE, 'nothing'); END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlcode_plain() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
                + "SQLCODE; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_rowcount() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee "
                + "sees: ' || NVL(SQLROWCOUNT::VARCHAR, 'nothing'); END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_found() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'callee sees: "
                + "' || NVL(SQLFOUND::VARCHAR, 'nothing') || ' ' || NVL(SQLNOTFOUND::VARCHAR, 'nothing'); END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_own_rowcount() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN UPDATE sc_t "
                + "SET a = a WHERE a = 1; RETURN 'callee did: ' || SQLROWCOUNT; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_raise_nested() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN BEGIN RAISE; END; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_raise_in_loop() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN FOR i IN 1 TO 1 DO RAISE; END FOR; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_raise_after() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RAISE; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_nested() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN BEGIN RETURN 'nested: ' || SQLERRM; END; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_after() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RETURN SQLERRM; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlcode_after() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN LET z := 1; END; RETURN NVL(SQLCODE::VARCHAR, 'nothing') || ' ' || "
                + "NVL(SQLSTATE, 'nothing'); END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_branch() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN IF (1 = 2) "
                + "THEN RETURN SQLERRM; END IF; RETURN 'not reached'; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT :SQLERRM INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlcode_query() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r VARCHAR; "
                + "BEGIN SELECT NVL(:SQLCODE::VARCHAR, 'nothing') INTO :r; RETURN r; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlerrm_declared() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE sqlerrm "
                + "VARCHAR DEFAULT 'mine'; BEGIN RETURN sqlerrm; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlcode_declared() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE sqlcode "
                + "VARCHAR DEFAULT 'mine'; BEGIN RETURN sqlcode; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_sqlcode_assign() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN sqlcode := "
                + "5; RETURN sqlcode; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_handler_codes() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN SELECT 1/0; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END; $$");
        engine.execute(
            "CREATE OR REPLACE PROCEDURE p_calls_in_handler() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE r "
                + "VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN CALL p_sqlstate() INTO :r; RETURN r || ' / ' "
                + "|| SQLSTATE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 32 | invalid identifier 'x'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN LET r2 VARCHAR := (SELECT :x); RETURN r2; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN RETURN :1; END;' USING (x); RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 23 |  Unexpected unnamed bind in SQL stored procedure.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN LET y VARCHAR := ?; RETURN y; END;' USING (x); RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 31 |  Unexpected unnamed bind in SQL stored procedure.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN LET y VARCHAR := (SELECT ?); RETURN y; END;' USING (x); RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 31 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN LET y VARCHAR := (SELECT :1); RETURN y; END;' USING (x); RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 8 | invalid identifier 'N'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN n := n + 1; END;'; RETURN n; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 10 | invalid identifier 'N'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN IF (n > 1) THEN RETURN 1; END IF; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 38 | invalid identifier 'N'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN UPDATE sc_t SET a = a WHERE a = :n; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "outer",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'DECLARE x VARCHAR DEFAULT ''inner''; BEGIN RETURN x; END;'; RETURN x; END; $$");
        assertAnswer(
            "outer",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN LET x VARCHAR := ''inner let''; END;'; RETURN x; END; $$");
        assertAnswer(
            "after the dynamic return",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN RETURN 1; END;'; RETURN 'after the dynamic return'; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN UPDATE "
                + "sc_t SET a = a; EXECUTE IMMEDIATE 'BEGIN RETURN NVL(SQLROWCOUNT::VARCHAR, ''nothing''); END;'; "
                + "RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "null",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN UPDATE "
                + "sc_t SET a = a; EXECUTE IMMEDIATE 'BEGIN UPDATE sc_t SET a = a WHERE a = 1; END;'; RETURN "
                + "SQLROWCOUNT; END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN UPDATE "
                + "sc_t SET a = a; EXECUTE IMMEDIATE 'UPDATE sc_t SET a = a WHERE a = 1'; RETURN SQLROWCOUNT; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 108 : SQL compilation "
                + "error: error line 1 at position 23 | invalid identifier 'SQLERRM'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN EXECUTE IMMEDIATE 'BEGIN LET m VARCHAR := SQLERRM; END;'; RETURN "
                + "'after'; END; $$");
        assertAnswer(
            "after",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN SELECT "
                + "1/0; EXCEPTION WHEN OTHER THEN EXECUTE IMMEDIATE 'BEGIN LET m VARCHAR := NVL(SQLCODE::VARCHAR, "
                + "''nothing''); END;'; RETURN 'after'; END; $$");
        assertAnswer(
            "caught: Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN RAISE; END;'; RETURN 'ran'; EXCEPTION WHEN "
                + "OTHER THEN RETURN 'caught: ' || SQLERRM; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier 'NOSUCH_VAR_X9'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN RETURN nosuch_var_x9; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'SELECT x'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation error: error line 1 at "
                + "position 37 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN EXECUTE "
                + "IMMEDIATE 'BEGIN CALL p_sqlstate(); END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE 'BEGIN RETURN x; END;'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ''a''.",
            "EXECUTE IMMEDIATE 'BEGIN RETURN :1; END;' USING ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected ''a''.",
            "EXECUTE IMMEDIATE 'BEGIN LET y VARCHAR := ?; RETURN y; END;' USING ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''a''.",
            "EXECUTE IMMEDIATE 'SELECT :1' USING ('a')");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 70 : SQL compilation "
                + "error: error line 1 at position 37 | invalid identifier 'SQLSTATE'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN CALL "
                + "p_sqlstate() INTO :r; RETURN r || ' / ' || x; END; $$");
        assertAnswer(
            "SQL compilation error: error line 1 at position 68 | RAISE without specifying exception only allowed "
                + "inside a handler. / outer",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; n INT DEFAULT 5; r VARCHAR; BEGIN BEGIN CALL "
                + "p_raise_after(); EXCEPTION WHEN OTHER THEN r := SQLERRM; END; RETURN r || ' / ' || x; END; $$");
    }

    @Test
    public void aNumberedAndAnUnnamedBindInABlock() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN :1; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ BEGIN LET y VARCHAR := :1; RETURN y; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ BEGIN LET y VARCHAR := (SELECT :1); RETURN y; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ BEGIN SELECT :1; RETURN 1; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ BEGIN INSERT INTO sc_t VALUES (:1); RETURN 1; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 |  Unexpected unnamed bind in SQL stored "
                + "procedure.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN ?; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 |  Unexpected unnamed bind in SQL stored "
                + "procedure.",
            "EXECUTE IMMEDIATE $$ BEGIN LET y VARCHAR := (SELECT ?); RETURN y; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 |  Unexpected unnamed bind in SQL stored "
                + "procedure.",
            "EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT ?); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 |  Unexpected unnamed bind in SQL stored "
                + "procedure.",
            "EXECUTE IMMEDIATE $$ BEGIN IF ((SELECT ?) = 1) THEN RETURN 1; END IF; RETURN 2; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 |  Unexpected unnamed bind in {2}.",
            "EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET := (SELECT ?); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 61 |  Unexpected unnamed bind in SQL stored "
                + "procedure.",
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 1; y := (SELECT COUNT(*) FROM sc_t WHERE a = ?); RETURN y; END; "
                + "$$");
        assertAnswer(
            "Function P_BIND1 successfully created.",
            "CREATE OR REPLACE PROCEDURE p_bind1() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN :1; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | invalid identifier '1'",
            "CALL p_bind1()");
        assertAnswer(
            "Function P_BINDQ successfully created.",
            "CREATE OR REPLACE PROCEDURE p_bindq() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN ?; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 |  Unexpected unnamed bind in SQL stored "
                + "procedure.",
            "CALL p_bindq()");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; BEGIN EXECUTE IMMEDIATE 'BEGIN SELECT :1; "
                + "RETURN 1; END;' USING (x); RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 13 |  Unexpected unnamed bind in {2}.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; BEGIN EXECUTE IMMEDIATE 'BEGIN SELECT ?; "
                + "RETURN 1; END;' USING (x); RETURN 'ran'; END; $$");
    }

    @Test
    public void whatADynamicTextLetsOut() {
        engine.execute(
            "CREATE OR REPLACE TABLE sc_t (a INT)");
        engine.execute(
            "INSERT INTO sc_t VALUES (1), (2), (3)");
        assertAnswer(
            "Uncaught exception of type 'E' on line 1 at position 47 : dynamic / -20003 / P0001",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'DECLARE "
                + "e EXCEPTION (-20003, ''dynamic''); BEGIN RAISE e; END;'; RETURN 'ran'; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLERRM || ' / ' || SQLCODE || ' / ' || SQLSTATE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : Uncaught exception "
                + "of type 'E' on line 1 at position 47 : dynamic",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'DECLARE "
                + "e EXCEPTION (-20003, ''dynamic''); BEGIN RAISE e; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero / 100051 / "
                + "22012",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "SELECT 1/0; END;'; RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLERRM || ' / ' || SQLCODE || "
                + "' / ' || SQLSTATE; END; $$");
        assertAnswer(
            "Division by zero / 100051 / 22012",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'SELECT "
                + "1/0'; RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLERRM || ' / ' || SQLCODE || ' / ' || "
                + "SQLSTATE; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "SELECT 1/0; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : Uncaught exception "
                + "of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "RETURN 1/0; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught exception of type 'STATEMENT_ERROR' "
                + "on line 1 at position 6 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "EXECUTE IMMEDIATE ''BEGIN SELECT 1/0; END;''; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation error: error line 1 at "
                + "position 13 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "EXECUTE IMMEDIATE ''BEGIN RETURN x; END;''; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "7",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'BEGIN RETURN 7; END;'); RETURN TABLE(rs); END; -- rs := (EXECUTE IMMEDIATE "
                + "'BEGIN RETURN 7; END;'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "7",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'BEGIN RETURN 7; END;'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "ran",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END;'; RETURN 'ran'; END; $$");
        assertAnswer(
            "Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN SELECT 1/0; EXCEPTION WHEN "
                + "OTHER THEN EXECUTE IMMEDIATE 'BEGIN SELECT 1 + ''x''; EXCEPTION WHEN OTHER THEN LET z := 1; "
                + "END;'; RETURN SQLERRM; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "EXECUTE IMMEDIATE 'BEGIN SELECT 1/0; END;'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'E' on line 1 at position 47 : dynamic",
            "EXECUTE IMMEDIATE 'DECLARE e EXCEPTION (-20003, ''dynamic''); BEGIN RAISE e; END;'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught exception of "
                + "type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "BEGIN EXECUTE IMMEDIATE 'BEGIN SELECT 1/0; END;'; END;");
    }

    @Test
    public void aDynamicTextWrittenAsAValue() {
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
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'BEGIN RETURN x; END;'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                + "error: error line 1 at position 7 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'SELECT x'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : Uncaught exception "
                + "of type 'STATEMENT_ERROR' on line 1 at position 6 : Division by zero",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'BEGIN SELECT 1/0; END;'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "inner",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'BEGIN LET x VARCHAR := ''inner''; RETURN x; END;'); RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 74 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier '1'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN LET rs RESULTSET := "
                + "(EXECUTE IMMEDIATE 'BEGIN RETURN :1; END;' USING (x)); RETURN TABLE(rs); END; $$");
    }

    @Test
    public void aDynamicStatementAndACallInOne() {
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
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 14 | invalid identifier 'X'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'CALL "
                + "reads_caller()'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 7 | Bind variable :x not set.",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'SELECT "
                + ":x'; RETURN 'ran'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 53 : SQL compilation "
                + "error: error line 1 at position 13 | invalid identifier 'NOSUCH_BIND_X9'",
            "EXECUTE IMMEDIATE $$ DECLARE x VARCHAR DEFAULT 'outer'; r VARCHAR; BEGIN EXECUTE IMMEDIATE 'BEGIN "
                + "SELECT :nosuch_bind_x9; END;'; RETURN 'ran'; END; $$");
    }
}
