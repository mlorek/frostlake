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
 * A call given more or fewer arguments than it takes, inside an expression of a block, is that expression's
 * EXPRESSION_ERROR, placed in the expression as the account compiles it: from the expression's own start, seven
 * columns on when it holds a subquery or a call of a user's function - the account compiles it as a query -
 * eight more for a value given to a variable, each variable ahead of the call written out as its type, and a
 * later line keeping its own columns. The echo writes each variable as its bound value's conversion. An untyped
 * LET's value and a CASE statement's operand are judged while the block compiles and refused bare, placed in
 * the block, on a branch that never runs too. Every answer is live-verified.
 */
public class BlockCallCountPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aMiscountedCallIsPlacedInItsExpression() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x INT) RETURNS INT AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x VARCHAR) RETURNS VARCHAR AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]')");
        answer(
            "CREATE OR REPLACE PROCEDURE pa(a INT, b VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN RETURN f2(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 4 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN 1 + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 11 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN RETURN 1 + f2(1); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "BEGIN LET x := ABS(1, 2); RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN LET x INT := f2(1); RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN IF (ABS(1, 2) > 0) THEN RETURN 1; END IF; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN IF (f2(1) > 0) THEN RETURN 1; END IF; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN ABS(1, 2) + f2(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN RETURN f2(1) + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCHFN.",
            "BEGIN RETURN nosuchfn(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function NOSUCHFN.",
            "BEGIN RETURN 1 + nosuchfn(1); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCHVAR'",
            "BEGIN RETURN nosuchvar + 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'NOSUCHVAR'",
            "BEGIN RETURN 1 + nosuchvar; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'x' "
                + "is not recognized",
            "BEGIN RETURN ABS('x'); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [UPPER(1, 2, 3, 4)] expected "
                + "1, got 4",
            "BEGIN RETURN UPPER(1, 2, 3, 4); END;");
        answer(
            "BEGIN RETURN (ABS(1, 2)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 1 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN\n ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 2 at position 1 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN 1 +\n ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 2 at position 1 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN RETURN 1 +\n f2(1); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "BEGIN RETURN n1(a => 1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "BEGIN RETURN UPPER(x => 'a', 'b'); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | Invalid argument types for function 'N1': (NUMBER(1,0))",
            "BEGIN RETURN n1(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | not enough arguments for function [ABS()], expected 1, got 0",
            "BEGIN RETURN ABS(); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN WHILE (ABS(1, 2) > 0) DO RETURN 1; END WHILE; END;");
        answer(
            "BEGIN FOR i IN 1 TO ABS(1, 2) DO RETURN 1; END FOR; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 16 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN CASE WHEN ABS(1, 2) > 0 THEN RETURN 1; END CASE; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 0; x := ABS(1, 2); RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN LET x := 0; x := f2(1); RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN (SELECT ABS(1, 2)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN ABS(1, 2)::VARCHAR; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 16 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN COALESCE(ABS(1, 2), f2(1)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 16 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN RETURN COALESCE(f2(1), ABS(1, 2)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 10 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN IFF(TRUE, ABS(1, 2), 0); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "DECLARE x INT DEFAULT ABS(1, 2); BEGIN RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [F2(1)] expected 0, got 1",
            "DECLARE x INT DEFAULT f2(1); BEGIN RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [SQRT(1, 2)] expected 1, got 2",
            "BEGIN RETURN SQRT(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [CURRENT_DATE(1, 2)] expected "
                + "0, got 2",
            "BEGIN RETURN CURRENT_DATE(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [SQRT(1, 2)] expected 1, got 2",
            "BEGIN RETURN 12345 + SQRT(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN RETURN 12345 + f2(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'x' "
                + "is not recognized",
            "BEGIN RETURN 12345 + n1('x', 'y'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | invalid identifier 'NOSUCHVAR'",
            "BEGIN RETURN ABS(1, 2) + nosuchvar; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'NOSUCHVAR'",
            "BEGIN RETURN nosuchvar + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'x' "
                + "is not recognized",
            "BEGIN RETURN TO_NUMBER('x'); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'x' "
                + "is not recognized",
            "BEGIN RETURN 1 + TO_NUMBER('x'); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero",
            "BEGIN RETURN 1 / 0; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'a' "
                + "is not recognized",
            "BEGIN RETURN 'a' + 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Numeric value 'a' "
                + "is not recognized",
            "BEGIN RETURN 12 + ('a' + 1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 5 | Invalid argument types for function 'UPPER': (ARRAY)",
            "BEGIN RETURN 12 + UPPER(ARRAY_CONSTRUCT(1)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | Invalid argument types for function 'UPPER': (ARRAY)",
            "BEGIN RETURN UPPER(ARRAY_CONSTRUCT(1)); END;");
        answer(
            "BEGIN RETURN 12 + DATEADD('nosuchpart', 1, CURRENT_DATE()); END;");
        answer(
            "BEGIN RETURN 12 + (1 IN (SELECT nosuchcol FROM t1)); END;");
        assertAnswer(
            "Function B15 successfully created.",
            "CREATE OR REPLACE PROCEDURE b15() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN ABS(1, 2); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "CALL b15()");
        assertAnswer(
            "Function B16 successfully created.",
            "CREATE OR REPLACE PROCEDURE b16() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN f2(1); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [F2(1)] expected 0, got 1",
            "CALL b16()");
        assertAnswer(
            "Function B17 successfully created.",
            "CREATE OR REPLACE PROCEDURE b17() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  LET x := 1;\n  RETURN x + "
                + "ABS(1, 2);\nEND;\n$$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 4 at position 9 : SQL compilation "
                + "error: error line 1 at position 21 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "CALL b17()");
        assertAnswer(
            "Function B18 successfully created.",
            "CREATE OR REPLACE PROCEDURE b18() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  LET x := 1;\n  RETURN x + "
                + "f2(1);\nEND;\n$$");
        answer(
            "CALL b18()");
    }

    @Test
    public void variablesAheadOfTheCallMoveItsPlace() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION jsf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 1;'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT, b BOOLEAN)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]'), TRUE");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [JSF(1)] expected 0, got 1",
            "BEGIN RETURN jsf(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 11 | too many arguments for function [JSF(1)] expected 0, got 1",
            "BEGIN RETURN 1 + jsf(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN ABS(1, 2) + jsf(); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 15 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN jsf() + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN ABS(1, 2) + f2(); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 14 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN f2() + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 14 | too many arguments for function [SQRT(1, 2)] expected 1, got "
                + "2",
            "BEGIN RETURN f2() + SQRT(1, 2) + 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 20 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; RETURN x + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 28 | too many arguments for function [F2(1)] expected 0, got 1",
            "BEGIN LET x := 1; RETURN x + f2(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; RETURN ABS(1, 2) + x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: error line 1 at position 7 | too many arguments for function [UPPER(1, 2)] expected 1, got "
                + "2",
            "BEGIN LET x := 'abc'; RETURN x || UPPER(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: error line 1 at position 13 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1.5; RETURN x + ABS(1, 2); END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "BEGIN LET x := 1; LET y := x + ABS(1, 2); RETURN y; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 31 : SQL compilation "
                + "error: error line 1 at position 28 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; LET y INT := x + ABS(1, 2); RETURN y; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL compilation "
                + "error: error line 1 at position 28 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; x := x + ABS(1, 2); RETURN x; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL compilation "
                + "error: error line 1 at position 21 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; IF (x + ABS(1, 2) > 0) THEN RETURN 1; END IF; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 36 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; RETURN x + (SELECT ABS(1, 2)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 36 | invalid identifier 'NOSUCHCOL'",
            "BEGIN LET x := 1; RETURN x + (SELECT nosuchcol FROM t1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(TO_NUMBER(null), 2)] "
                + "expected 1, got 2",
            "DECLARE n NUMBER DEFAULT NULL; BEGIN RETURN ABS(n, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation "
                + "error: error line 1 at position 20 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN FOR i IN 1 TO 2 DO RETURN i + ABS(1, 2); END FOR; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 18 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN LET x := 1; RETURN :x + ABS(1, 2); END;");
        answer(
            "BEGIN LET x := 1; RETURN x + (SELECT ABS(1, 2)); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 28 | Invalid argument types for function 'N1': (NUMBER(1,0))",
            "BEGIN LET x := 1; RETURN x + n1(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 7 | Invalid argument types for function 'N1': (NUMBER(38,0))",
            "BEGIN LET x := 1; RETURN n1(x); END;");
        answer(
            "BEGIN LET x := 1; RETURN n1(x, 1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(TO_NUMBER('1'), 2)] "
                + "expected 1, got 2",
            "BEGIN LET x := 1; RETURN ABS(x, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, TO_NUMBER('1'))] "
                + "expected 1, got 2",
            "BEGIN LET x := 1; RETURN ABS(1, x); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [UPPER('abc', 2)] expected 1, "
                + "got 2",
            "BEGIN LET x := 'abc'; RETURN UPPER(x, 2); END;");
        assertDatedAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [YEAR(CAST('<DATE>' AS "
                + "DATE), 2)] expected 1, got 2",
            "BEGIN LET d := CURRENT_DATE(); RETURN YEAR(d, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN ABS(1,\n 2) + SQRT(1, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 2 at position 1 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN SQRT(4) +\n ABS(1, 2) + f2(1); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: error line 1 at position 10 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "BEGIN RETURN CASE WHEN ABS(1, 2) > 0 THEN 1 END; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "BEGIN CASE (ABS(1, 2)) WHEN 1 THEN RETURN 1; END CASE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "BEGIN CASE (f2(1)) WHEN 1 THEN RETURN 1; END CASE; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "BEGIN LET x := f2(1); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "BEGIN LET x := 1 + f2(1); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | Invalid argument types for function "
                + "'N1': (NUMBER(1,0))",
            "BEGIN LET x := n1(1); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "BEGIN LET x := 1 + ABS(1, 2); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 | too many arguments for function "
                + "[UPPER(1, 2, 3)] expected 1, got 3",
            "BEGIN LET x := UPPER(1, 2, 3) || 'a'; RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "BEGIN IF (FALSE) THEN LET x := ABS(1, 2); END IF; RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN IF (FALSE) THEN RETURN ABS(1, 2); END IF; RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN IF (FALSE) THEN RETURN f2(1); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | too many arguments for function [F2(1)] "
                + "expected 0, got 1",
            "BEGIN IF (FALSE) THEN LET x := f2(1); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "BEGIN RETURN n1(a => 1, 2) + nosuchvar; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "BEGIN IF (FALSE) THEN RETURN n1(a => 1, 2); END IF; RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "BEGIN LET x := n1(a => 1, 2); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "BEGIN LET x := 1; RETURN n1(a => x, 2); END;");
        answer(
            "CREATE OR REPLACE PROCEDURE c1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN n1(a => 1, 2); END; $$");
        assertAnswer(
            "Function C2 successfully created.",
            "CREATE OR REPLACE PROCEDURE c2(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN x + ABS(1, 2); "
                + "END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: error line 1 at position 21 | too many arguments for function [ABS(1, 2)] expected 1, got 2",
            "CALL c2(1)");
        assertAnswer(
            "Function C3 successfully created.",
            "CREATE OR REPLACE PROCEDURE c3(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN x + f2(1); END; $$");
        answer(
            "CALL c3(1)");
        assertAnswer(
            "Function C4 successfully created.",
            "CREATE OR REPLACE PROCEDURE c4(x VARCHAR) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN x || UPPER(1, "
                + "2); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL compilation "
                + "error: error line 1 at position 8 | too many arguments for function [UPPER(1, 2)] expected 1, got "
                + "2",
            "CALL c4('ab')");
        assertAnswer(
            "Function C5 successfully created.",
            "CREATE OR REPLACE PROCEDURE c5() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET y := 1; LET z := y + ABS(1,"
                + " 2); RETURN z; END; $$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "CALL c5()");
    }

    @Test
    public void theEchoWritesAVariableAsItsBoundValue() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE FUNCTION tn(a NUMBER(5,2)) RETURNS TABLE (v NUMBER(5,2)) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x'");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [YEAR(CAST('2020-01-02' AS "
                + "DATE), 2)] expected 1, got 2",
            "DECLARE d DATE DEFAULT '2020-01-02'; BEGIN RETURN YEAR(d, 2); END;");
        answer(
            "DECLARE d TIMESTAMP_NTZ DEFAULT '2020-01-02 03:04:05'; BEGIN RETURN YEAR(d, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function "
                + "[HOUR(CAST('03:04:05.000000000' AS TIME(9)), 2)] expected 1, got 2",
            "DECLARE d TIME DEFAULT '03:04:05'; BEGIN RETURN HOUR(d, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [IFF(CAST('true' AS BOOLEAN), "
                + "1, 2, 3)] expected 3, got 4",
            "DECLARE b BOOLEAN DEFAULT TRUE; BEGIN RETURN IFF(b, 1, 2, 3); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(TO_NUMBER('1.50', 5, 2), "
                + "2)] expected 1, got 2",
            "DECLARE n NUMBER(5,2) DEFAULT 1.5; BEGIN RETURN ABS(n, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS(CAST('1.5' AS FLOAT), "
                + "2)] expected 1, got 2",
            "DECLARE f FLOAT DEFAULT 1.5; BEGIN RETURN ABS(f, 2); END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL compilation "
                + "error: error line 1 at position 0 | too many arguments for function [ABS('ab', 2)] expected 1, "
                + "got 2",
            "DECLARE v VARCHAR DEFAULT 'ab'; BEGIN RETURN ABS(v, 2); END;");
        answer(
            "DECLARE n NUMBER DEFAULT NULL; BEGIN RETURN ABS(n, 2); END;");
    }
}
