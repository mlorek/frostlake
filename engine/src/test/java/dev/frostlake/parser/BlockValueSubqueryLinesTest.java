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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block's value that is a query in parentheses which does not parse, and a bare table function in a common table
 * expression's query, refused as the account refuses them (live-verified): the value's query at its SELECT, then the
 * query's own first fault, when the block runs and when a procedure holding it is created - which is refused, so
 * nothing is left to CALL; the bare call in a common table expression at its parenthesis, its first argument's name
 * and the token after that argument's value.
 */
public class BlockValueSubqueryLinesTest extends MeasuredCellsTestSupport {

    /** Each statement and the lines it is refused with. */
    @Test
    public void aBlockValueQueryAndACommonExpressionCall() {
        assertCells(new String[][] {
            {"WITH c AS (SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'))) SELECT COUNT(*) FROM c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax error "
                + "line 1 at position 33 unexpected 'INPUT'. | syntax error line 1 at position 59 unexpected ')'."},
            {"WITH c AS (SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'), OUTER => TRUE)) SELECT COUNT(*) FROM c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax error "
                + "line 1 at position 33 unexpected 'INPUT'. | syntax error line 1 at position 59 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET c := (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]'))); RETURN "
                + "c; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 45 unexpected '('."},
            {"CREATE OR REPLACE PROCEDURE pb() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET c := (SELECT COUNT(*) FROM "
                + "FLATTEN(INPUT => PARSE_JSON('[1]'))); RETURN c; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 45 unexpected '('."},
            {"CALL pb()",
                "REFUSED SQL compilation error: | Unknown function PB."},
            {"CREATE OR REPLACE PROCEDURE pb3() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET c := (SELECT 1 FROM x y z); "
                + "RETURN c; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 35 unexpected 'z'."},
            {"CALL pb3()",
                "REFUSED SQL compilation error: | Unknown function PB3."},
            {"CREATE OR REPLACE PROCEDURE pb5() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (SELECT 1 FROM x y z); "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 33 unexpected 'z'."},
            {"CREATE OR REPLACE TABLE cb (a INT)",
                "Table CB successfully created."},
            {"CREATE OR REPLACE PROCEDURE pb6() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET c INT := (SELECT 1 FROM x y "
                + "z); RETURN c; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 39 unexpected 'z'."},
            {"CREATE OR REPLACE PROCEDURE pb7() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET c := 1 + (SELECT 1 FROM x y "
                + "z); RETURN c; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 39 unexpected 'z'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT 1 FROM x y z); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 33 unexpected 'z'."},
        });
    }
}
