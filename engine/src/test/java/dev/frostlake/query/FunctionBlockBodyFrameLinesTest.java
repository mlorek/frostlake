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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A quoted function body that opens a block and reads as none: it is read as the function's expression, its
 * first word a name and the token after it refused, and the frame reads on to the body's first semicolon —
 * the token after an opening parenthesis is refused, and the semicolon when an operator stands before it;
 * nothing after that semicolon is read (live-verified).
 */
public class FunctionBlockBodyFrameLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theFrameReadsOn() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 x; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (1; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 17 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 +; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1; RETURN 2 +; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'LET'. | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN LET y := 1 +; RETURN y; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'LET'. | syntax error line 1 at position 17 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN LET y := (SELECT a a a FROM t); RETURN y; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected '('.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN ((SELECT a a a FROM t)); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected '('.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN ABS((SELECT a a a FROM t)); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1; RETURN (SELECT a a a FROM t); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (SELECT FROM t); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (SELECT a FROM t WHERE); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'IF'. | syntax error line 1 at position 11 unexpected 'TRUE'. | syntax error line 1 at "
                + "position 32 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN IF (TRUE) THEN RETURN 1 +; END IF; RETURN 2; "
                + "END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'x'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN x := ; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 9 "
                + "unexpected 'x'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$DECLARE x INT; BEGIN RETURN 1 +; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1, 2; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN CAST(1, 2); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN IN; END$$");
        assertAnswer(
            "Function F successfully created.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 20 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 + + ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (1 +); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN ABS(; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1; END x$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 2 at position 2 "
                + "unexpected 'RETURN'. | syntax error line 2 at position 12 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN\n  RETURN 1 +;\nEND$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN a b c; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 = ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'. | syntax error "
                + "line 1 at position 26 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (SELECT 1) +; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 + (SELECT a a a FROM t); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN {; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN SELECT 1 x y; RETURN 1; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 1; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 'a' 'b'; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 17 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 +; RETURN 2 +; END$$");
    }

    @Test
    public void subqueriesInTheBody() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a a a FROM t); END");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (SELECT a a a FROM t); END$$");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a FROM t WHERE); END");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT 1 x y); END");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (SELECT 1 x y); END$$");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT FROM t); END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1 +; END");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 17 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 +; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (1; END");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := (SELECT a a a FROM t); RETURN y; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'x'. | syntax error "
                + "line 1 at position 64 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1 x; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected 'x'. | syntax error "
                + "line 1 at position 80 unexpected 'END'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a FROM t) x; END");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN ((SELECT a a a FROM t)); END");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN ABS((SELECT a a a FROM t)); END");
        answer(
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; RETURN (SELECT a a a FROM t); END");
    }

    @Test
    public void operatorsAndLaterBrackets() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 * ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 || ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 < ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 - ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 / ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 <> ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 >= ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 % ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected '1'. | syntax error line 1 "
                + "at position 23 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN ABS(1) + ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 15 unexpected '1'. | syntax error line 1 "
                + "at position 22 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (1) (2 +); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 + (2 +); END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 16 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN - ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 20 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 AND ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN NOT ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 IS ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 20 unexpected 'x'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 + 2 x; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 :: ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 20 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 + - ; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 2 at position 0 unexpected ';'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN 1 +\n; END$$");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected 'RETURN'. | syntax error line 2 at position 0 unexpected '1'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN RETURN (\n1 +); END$$");
    }
}
