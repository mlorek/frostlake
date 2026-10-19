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

package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A comma inside a CAST's or a TRY_CAST's parentheses in a SQL procedure's body is refused as the procedure
 * is created, in the body's own frame (live-verified): a query statement reports the lines it reports alone,
 * placed where it stands in the body, on a branch that never runs too; a RETURN's, a LET's or an assignment's
 * value names the comma alone.
 */
public class BodyCastCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void aCastCommaInABodyIsRefusedAtCreate() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE p6() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  SELECT UPPER(CAST(1, 2));\n  "
                + "RETURN 1;\nEND;\n$$",
                "REFUSED SQL compilation error: | syntax error line 3 at position 21 unexpected ','. | syntax error "
                + "line 3 at position 25 unexpected ')'."},
            {"CREATE OR REPLACE PROCEDURE p7() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT UPPER(CAST(1, 2)); RETURN "
                + "1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax error "
                + "line 1 at position 30 unexpected ')'."},
            {"CREATE OR REPLACE PROCEDURE p8() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT CAST(1, 2); RETURN 1; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'."},
            {"CREATE OR REPLACE PROCEDURE p9() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN CAST(1, 2); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','."},
            {"CREATE OR REPLACE PROCEDURE p10() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT TRY_CAST(1, 2); RETURN 1; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ','. | syntax error "
                + "line 1 at position 27 unexpected ')'."},
            {"CREATE OR REPLACE PROCEDURE p11() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := CAST(1, 2); RETURN 1; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ','."},
            {"CREATE OR REPLACE PROCEDURE p12() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT CAST(1 AS INT, 2); RETURN "
                + "1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','. | syntax error "
                + "line 1 at position 30 unexpected ')'."},
            {"CREATE OR REPLACE PROCEDURE p13() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (FALSE) THEN SELECT "
                + "UPPER(CAST(1, 2)); END IF; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','. | syntax error "
                + "line 1 at position 46 unexpected ')'."},
            {"CALL p6()", "REFUSED SQL compilation error: | Unknown function P6."},
            {"SELECT UPPER(CAST(1, 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax error "
                + "line 1 at position 23 unexpected ')'."},
        });
    }
}
