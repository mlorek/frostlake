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
 * A block's variable named by CASE, WHEN, CAST, DEFAULT, CONSTRAINT, JOIN, INNER or CROSS, as the account reads it
 * (live-verified): a DECLARE section's item takes the name, a variable, a cursor, a RESULTSET and an exception alike,
 * and an assignment takes it before its :=, while the statements that begin with those words elsewhere keep the lines
 * they are refused with. A dotted name over a variable a DECLARE or a LET gives a value is refused at the variable,
 * whatever its type.
 */
public class KeywordVariableNamesTest extends MeasuredCellsTestSupport {

    /** Each block and its answer. */
    @Test
    public void declaredAndAssignedByTheWords() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE when INT DEFAULT 4; BEGIN IF (when > 1) THEN RETURN 1; END IF; RETURN 0; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'when'."},
            {"EXECUTE IMMEDIATE $$ DECLARE join INT DEFAULT 4; BEGIN RETURN join; END; $$",
                "4"},
            {"EXECUTE IMMEDIATE $$ DECLARE default INT DEFAULT 4; BEGIN default := default + 1; RETURN default; END; "
                + "$$",
                "5"},
            {"EXECUTE IMMEDIATE $$ DECLARE join CURSOR FOR SELECT 7 AS a; BEGIN FOR r IN join DO RETURN r.a; END FOR; "
                + "END; $$",
                "7"},
            {"EXECUTE IMMEDIATE $$ DECLARE join RESULTSET DEFAULT (SELECT 8 AS a); BEGIN RETURN TABLE(join); END; $$",
                "8"},
            {"EXECUTE IMMEDIATE $$ DECLARE join EXCEPTION (-20001, 'boom'); BEGIN RAISE join; END; $$",
                "REFUSED Uncaught exception of type 'JOIN' on line 1 at position 48 : boom"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET join := 1; join := join + 1; RETURN join; END; $$",
                "2"},
            {"EXECUTE IMMEDIATE $$ DECLARE when INT; BEGIN when := 3; RETURN 7; END; $$",
                "7"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET inner := 1; inner := inner + 1; RETURN inner; END; $$",
                "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cross := 1; cross := cross + 1; RETURN cross; END; $$",
                "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET cast := 1; cast := 5; RETURN 6; END; $$",
                "6"},
            {"EXECUTE IMMEDIATE $$ DECLARE join INT DEFAULT 1; BEGIN RETURN join.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'JOIN'"},
            {"EXECUTE IMMEDIATE $$ DECLARE inner INT DEFAULT 4; BEGIN RETURN inner; END; $$",
                "4"},
            {"EXECUTE IMMEDIATE $$ DECLARE cross INT DEFAULT 4; BEGIN RETURN cross; END; $$",
                "4"},
            {"EXECUTE IMMEDIATE $$ DECLARE cast INT DEFAULT 4; BEGIN RETURN 9; END; $$",
                "9"},
            {"EXECUTE IMMEDIATE $$ DECLARE constraint INT DEFAULT 4; BEGIN constraint := constraint + 2; RETURN "
                + "constraint; END; $$",
                "6"},
        });
    }

    /** A field read from a variable that holds a value. */
    @Test
    public void aDottedNameOverAVariable() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET x INT := 1; RETURN x.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; RETURN x.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1; BEGIN RETURN x.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET join := 1; RETURN join.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'JOIN'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x INT := 1; LET y INT := x.a; RETURN y; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x VARIANT := PARSE_JSON('{\"a\": 5}'); RETURN x.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x OBJECT := OBJECT_CONSTRUCT('a', 6); RETURN x.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT DEFAULT PARSE_JSON('{\"a\": 7}'); BEGIN RETURN x.a; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 64 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x INT := 1; RETURN x:a; END; $$",
                "null"},
        });
    }

    /** A join and a CASE left unfinished. */
    @Test
    public void statementsThatBeginWithTheWordsKeepTheirLines() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT)");
        assertCells(new String[][] {
            {"SELECT case",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '<EOF>'."},
            {"SELECT a FROM kw INNER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"SELECT 1 FROM kw JOIN; SELECT 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'."},
            {"SELECT 1 FROM kw JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'."},
            {"SELECT a FROM kw CROSS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
        });
    }
}
