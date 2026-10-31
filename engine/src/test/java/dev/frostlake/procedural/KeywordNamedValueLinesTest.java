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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A value that opens with CASE, CAST or WHEN where a variable or a parameter so named is meant is read as what the word
 * opens, never as a name (live-verified): CASE opens a CASE expression whose operand reads on, so the token that ends
 * the value is refused; CAST must be followed by its parenthesis; WHEN opens nothing and is refused with the token
 * after it. A procedure whose body holds such a value is refused as it is created, and a SQL function's body is read in
 * its frame of parentheses, the frame's closing parenthesis refused after the word's value.
 */
public class KeywordNamedValueLinesTest extends MeasuredCellsTestSupport {

    /** RETURN, LET and assignment values in a block. */
    @Test
    public void aBlocksValueIsReadAsTheWordOpens() {
        engine.execute("CREATE OR REPLACE TABLE fz (a INT)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET case := 1; RETURN case; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE case INT DEFAULT 2; BEGIN RETURN case; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE case INT; BEGIN case := 3; RETURN case; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET when := 1; RETURN when; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'when'. | syntax error "
                + "line 1 at position 35 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ DECLARE when INT DEFAULT 2; BEGIN RETURN when; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'when'. | syntax error "
                + "line 1 at position 48 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cast := 1; RETURN cast; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET case := 1; RETURN case + 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET when := 1; RETURN when + 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'when'. | syntax error "
                + "line 1 at position 34 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cast := 1; RETURN cast + 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '+'. | syntax error "
                + "line 1 at position 37 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET case := 1; RETURN (case); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET case := 1; LET x := case; RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ';'."},
        });
    }

    /** Procedure bodies over parameters so named. */
    @Test
    public void aProcedureHoldingSuchAValueIsRefusedAtCreate() {
        engine.execute("CREATE OR REPLACE TABLE fz (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE p(case INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN case; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p(when INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN when; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'when'. | syntax error "
                + "line 1 at position 20 unexpected 'END'."},
            {"CREATE OR REPLACE PROCEDURE p(cast INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN cast; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p(case INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN case + 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p(when INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (when > 1) THEN RETURN "
                + "1; END IF; RETURN 0; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'when'."},
            {"CREATE OR REPLACE PROCEDURE p(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN x +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '1'."},
        });
    }

    /** SQL function bodies over parameters so named. */
    @Test
    public void aFunctionsBodyIsReadInItsFrame() {
        engine.execute("CREATE OR REPLACE TABLE fz (a INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION f(case INT) RETURNS INT AS $$ case + 1 $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 11 "
                + "unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION f(when INT) RETURNS INT AS $$ when + 1 $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'when'. | syntax error line 1 at position 11 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION f(cast INT) RETURNS INT AS $$ cast + 1 $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected '+'. | syntax error line 1 at position 11 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION f(case INT) RETURNS INT AS $$ case $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION f(when INT) RETURNS INT AS $$ when $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 2 "
                + "unexpected 'when'."},
            {"CREATE OR REPLACE FUNCTION f(cast INT) RETURNS INT AS $$ cast $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 7 "
                + "unexpected ')'."},
        });
    }
}
