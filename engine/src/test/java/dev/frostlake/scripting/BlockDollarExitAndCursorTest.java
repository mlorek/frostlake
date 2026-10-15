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
 * A dollar sign that opens nothing is refused where it stands, and in a block the END after its statement too;
 * a BREAK or an EXIT given WHEN reads WHEN as its label and is refused at the token after it and at the
 * statement's semicolon. A stray BREAK in a scripting function a block's expression calls keeps the function
 * body's place. A FOR over a cursor leaves it open past its last row - a FETCH finds no row, a CLOSE closes it
 * and a second CLOSE is refused - and a FETCH from a cursor that is not open, inside a procedure a block
 * calls, is that block's statement's failure, which its OTHER handler catches (live-verified).
 */
public class BlockDollarExitAndCursorTest extends MeasuredCellsTestSupport {

    @Test
    public void aDollarThatOpensNothing() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$'.",
                "SELECT $\"PV\"");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '$'. | syntax "
                    + "error line 1 at position 20 unexpected 'END'.",
                "BEGIN RETURN $\"PV\"; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$'.",
                "SELECT $ 1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '$'.",
                "SELECT 1 + $");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$'.",
                "SELECT $'x'");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '$'.",
                "SELECT $\"PV\" FROM nb_t");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fbreak()");
            answer(
                "DROP PROCEDURE IF EXISTS pf()");
        }
    }

    @Test
    public void exitWhenAndAStrayBreak() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'i'. | syntax "
                    + "error line 1 at position 40 unexpected ';'.",
                "BEGIN FOR i IN 1 TO 3 DO EXIT WHEN i = 2; END FOR; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'i'. | syntax "
                    + "error line 1 at position 41 unexpected ';'.",
                "BEGIN FOR i IN 1 TO 3 DO BREAK WHEN i = 2; END FOR; RETURN 1; END;");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'i'. | syntax "
                    + "error line 1 at position 46 unexpected ';'.",
                "BEGIN FOR i IN 1 TO 3 DO EXIT outer WHEN i = 2; END FOR outer; RETURN 1; END;");
            assertAnswer(
                "Function FBREAK successfully created.",
                "CREATE OR REPLACE FUNCTION fbreak() RETURNS INT AS $$ BEGIN BREAK; RETURN 1; END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 |  Break used outside of loop",
                "SELECT fbreak()");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                    + "error: error line 1 at position 7 |  Break used outside of loop",
                "BEGIN LET x INT := 0; x := (SELECT fbreak()); RETURN x; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                    + "error: error line 1 at position 7 |  Break used outside of loop",
                "BEGIN RETURN (SELECT fbreak()); END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : SQL compilation "
                    + "error: error line 1 at position 7 |  Break used outside of loop",
                "BEGIN LET x INT := (SELECT fbreak()); RETURN x; END;");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fbreak()");
            answer(
                "DROP PROCEDURE IF EXISTS pf()");
        }
    }

    @Test
    public void cursorsAfterAForAndInACalledBody() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "Function PF successfully created.",
                "CREATE OR REPLACE PROCEDURE pf() RETURNS INT LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT 1 AS "
                    + "a; v INT; BEGIN FETCH c INTO v; RETURN v; END $$");
            assertAnswer(
                "caught 92201",
                "BEGIN CALL pf(); RETURN 'no'; EXCEPTION WHEN OTHER THEN RETURN 'caught ' || SQLCODE; END;");
            assertAnswer(
                "REFUSED CURSOR C is not open",
                "CALL pf()");
            assertAnswer(
                "null",
                "DECLARE c CURSOR FOR SELECT 1 AS a; v INT; BEGIN FOR r IN c DO NULL; END FOR; FETCH c INTO v; "
                    + "RETURN v; END;");
            assertAnswer(
                "1",
                "DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO NULL; END FOR; CLOSE c; RETURN 1; END;");
            assertAnswer(
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 80 : CURSOR C is not "
                    + "open",
                "DECLARE c CURSOR FOR SELECT 1 AS a; BEGIN FOR r IN c DO NULL; END FOR; CLOSE c; CLOSE c; RETURN "
                    + "1; END;");
            assertAnswer(
                "1",
                "BEGIN BEGIN BREAK blk; END blk; RETURN 1; END;");
            assertAnswer(
                "1",
                "BEGIN FOR i IN 1 TO 2 DO BEGIN BREAK blk; END blk; END FOR; RETURN 1; END;");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS fbreak()");
            answer(
                "DROP PROCEDURE IF EXISTS pf()");
        }
    }
}
