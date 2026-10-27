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
 * The calls of a declaration whose type the block infers — {@code LET x := …}, a DECLARE item with a value and no
 * type — are looked up as the block compiles, ahead of every name the block reads (live-verified): a function
 * that does not exist is the block's bare refusal on a branch that never runs, in a loop, in a nested block, past
 * a handler and in a procedure's body when it is called, the value's unknown calls named in one sentence. Every
 * other expression of a block — a RETURN, a condition, an assignment, a typed declaration — leaves an unknown
 * function to the moment it runs.
 */
public class InferredDeclarationCallsTest extends MeasuredCellsTestSupport {

    @Test
    public void anUnknownFunctionWaitsForItsExpression() {
        engine.execute("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, s VARCHAR, c INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a', 1)");
        engine.execute("DROP FUNCTION IF EXISTS blk_f()");
        engine.execute("DROP FUNCTION IF EXISTS blk_g()");
        assertCells(new String[][] {
            {"BEGIN LET x := SNOWFLAKE.SYSTEM$TYPEOF(1); RETURN x; END;",
                "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$TYPEOF."},
            {"BEGIN LET x := nosuch_f(1); RETURN x; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN RETURN nosuch_f(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN RETURN nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN RETURN nosuch_f(); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN LET x := 1; x := nosuch_f(1); RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN WHILE (nosuch_f(1) > 0) DO RETURN 1; END WHILE; RETURN 2; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN FOR i IN 1 TO nosuch_f(1) DO RETURN 1; END FOR; RETURN 2; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN CASE WHEN nosuch_f(1) = 1 THEN RETURN 1; END CASE; RETURN 2; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 16 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"DECLARE x INT DEFAULT nosuch_f(1); BEGIN RETURN x; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN SELECT nosuch_f(1); RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN SELECT nosuch_f(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN LET r RESULTSET := (SELECT nosuch_f(1)); RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET c1 CURSOR FOR SELECT nosuch_f(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN RETURN test_db.test_schema.nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.NOSUCH_F."},
            {"BEGIN RETURN nosuch_s.nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown user-defined function NOSUCH_S.NOSUCH_F."},
            {"BEGIN RETURN nosuch_db.nosuch_s.nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown user-defined function NOSUCH_DB.NOSUCH_S.NOSUCH_F."},
            {"BEGIN RETURN SYSTEM$NOSUCH(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function SYSTEM$NOSUCH."},
            {"BEGIN RETURN f2(); END;", "1"},
            {"BEGIN RETURN \"F2\"(); END;", "1"},
            {"BEGIN RETURN \"f2\"(); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function \"f2\"."},
            {"BEGIN RETURN test_schema.f2(); END;", "1"},
            {"BEGIN IF (FALSE) THEN RETURN ABS(1, 2); END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN RETURN f2(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN RETURN nosuch_f(nosuch_v); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier 'NOSUCH_V'"},
            {"BEGIN IF (FALSE) THEN RETURN nosuch_v + nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'NOSUCH_V'"},
            {"BEGIN IF (FALSE) THEN RETURN nosuch_f(1) + nosuch_v; END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 43 | invalid identifier 'NOSUCH_V'"},
            {"BEGIN IF (FALSE) THEN RETURN ABS(1, 2) + nosuch_f(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN RETURN nosuch_f(1) + nosuch_g(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN LET x := 1; RETURN x + nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN RETURN (nosuch_f(1)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN RETURN 1 + nosuch_f(1); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN RETURN ABS(nosuch_f(1)); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN RETURN TRANSFORM([1], x INT -> nosuch_f(x)); END;",
                "REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'X'"},
            {"BEGIN CREATE OR REPLACE FUNCTION blk_f() RETURNS INT AS '7'; LET x := blk_f(); RETURN x; END;",
                "REFUSED SQL compilation error: | Unknown function BLK_F."},
            {"BEGIN CREATE OR REPLACE FUNCTION blk_f() RETURNS INT AS '7'; LET x := blk_f(); RETURN x; END;",
                "REFUSED SQL compilation error: | Unknown function BLK_F."},
            {"BEGIN CREATE OR REPLACE FUNCTION blk_g() RETURNS INT AS '8'; RETURN 1; END;", "1"},
            {"BEGIN RETURN blk_g(); END;", "8"},
            {"BEGIN DROP FUNCTION blk_g(); RETURN blk_g(); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | Unknown function BLK_G."},
            {"CREATE OR REPLACE PROCEDURE pf1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN nosuch_f(1); END; $$",
                "Function PF1 successfully created."},
            {"CALL pf1()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"CREATE OR REPLACE PROCEDURE pf2() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (FALSE) THEN RETURN "
                + "nosuch_f(1); END IF; RETURN 2; END; $$",
                "Function PF2 successfully created."},
            {"CALL pf2()", "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN RETURN nosuch_f(1); END IF; RETURN 3; END; $$", "3"},
            {"BEGIN BEGIN RETURN nosuch_f(1); END; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN nosuch_f(1); END;", "1"},
            {"BEGIN LET x := 1; IF (x > 0) THEN RETURN 1; ELSE RETURN nosuch_f(1); END IF; END;", "1"},
        });
    }

    @Test
    public void anInferredDeclarationLooksItsCallsUp() {
        engine.execute("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        engine.execute("CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT, s VARCHAR, c INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 'a', 1)");
        engine.execute("DROP PROCEDURE IF EXISTS pl1()");
        assertCells(new String[][] {
            {"BEGIN IF (FALSE) THEN LET x := nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x INT := nosuch_f(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN LET x VARIANT := nosuch_f(1); END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN LET x DEFAULT nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := ABS(1, 2); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 31 | too many arguments for function [ABS(1,"
                + " 2)] expected 1, got 2"},
            {"BEGIN IF (FALSE) THEN LET x := f2(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 31 | too many arguments for function "
                + "[F2(1)] expected 0, got 1"},
            {"BEGIN IF (FALSE) THEN LET x := nosuch_f(nosuch_v); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := nosuch_v + nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := 1 + nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := (SELECT nosuch_f(1)); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := (SELECT nosuchcol FROM t1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 22 |  variable 'X' cannot have its type "
                + "inferred from initializer"},
            {"BEGIN IF (FALSE) THEN LET x := nosuch_f(1) + nosuch_g(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown functions NOSUCH_F, NOSUCH_G."},
            {"BEGIN IF (FALSE) THEN LET x := 'a' + 1; END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN LET x := 1 + TRUE; END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 31 | Invalid argument types for function "
                + "'+': (NUMBER(1,0), BOOLEAN)"},
            {"BEGIN IF (FALSE) THEN LET x := 'x'::INT; END IF; RETURN 1; END;", "1"},
            {"BEGIN IF (FALSE) THEN LET x := IFF(FALSE, nosuch_f(1), 1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := CASE WHEN FALSE THEN nosuch_f(1) END; END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := \"f2\"(); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function \"f2\"."},
            {"BEGIN IF (FALSE) THEN LET x := SYSTEM$NOSUCH(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH."},
            {"BEGIN IF (FALSE) THEN LET x := test_schema.nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown user-defined function TEST_SCHEMA.NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN LET x := nosuch_f(1) OVER (); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Invalid function type [NOSUCH_F] for window function."},
            {"BEGIN IF (FALSE) THEN LET x := UPPER(1, 2, 3); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 31 | too many arguments for function "
                + "[UPPER(1, 2, 3)] expected 1, got 3"},
            {"BEGIN IF (FALSE) THEN LET x := f2() + nosuch_f(1); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"DECLARE x DEFAULT nosuch_f(1); BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"DECLARE x INT DEFAULT nosuch_f(1); BEGIN RETURN 1; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"DECLARE x DEFAULT ABS(1, 2); BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 18 | too many arguments for function [ABS(1,"
                + " 2)] expected 1, got 2"},
            {"BEGIN LET x := 1; IF (FALSE) THEN LET x := nosuch_f(1); END IF; RETURN x; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN LET x := 1; IF (FALSE) THEN x := nosuch_f(1); END IF; RETURN x; END;", "1"},
            {"BEGIN LET x := nosuch_f(1); LET y := nosuch_g(1); RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN LET x := nosuch_f(1); RETURN nosuch_v; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN RETURN nosuch_v; END IF; LET x := nosuch_f(1); RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN FOR i IN 1 TO 1 DO LET x := nosuch_f(1); END FOR; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN LET x := nosuch_f(1); RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 2; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN BEGIN LET x := nosuch_f(1); END; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN IF (FALSE) THEN BEGIN LET x := nosuch_f(1); END; END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN LET x := nosuch_f(1); END IF; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"CREATE OR REPLACE PROCEDURE pl1() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (FALSE) THEN LET x := "
                + "nosuch_f(1); END IF; RETURN 1; END; $$",
                "Function PL1 successfully created."},
            {"CALL pl1()", "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN LET x := test_schema.f2(); RETURN x; END;", "1"},
            {"BEGIN LET x := f2(); RETURN x; END;", "1"},
            {"BEGIN LET x := 1; LET y := x + nosuch_f(1); RETURN 1; END;",
                "REFUSED SQL compilation error: | Unknown function NOSUCH_F."},
            {"BEGIN LET r RESULTSET := (SELECT nosuch_f(1)); RETURN 1; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | Unknown function NOSUCH_F."},
            {"BEGIN LET x := 1; IF (FALSE) THEN LET y := x + ABS(1, 2); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 47 | too many arguments for function [ABS(1,"
                + " 2)] expected 1, got 2"},
            {"BEGIN IF (FALSE) THEN LET x := DATEADD('nosuchpart', 1, CURRENT_DATE()); END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: ['nosuchpart'] is not a valid date/time component for function "
                + "DATEADD."},
        });
    }
}
