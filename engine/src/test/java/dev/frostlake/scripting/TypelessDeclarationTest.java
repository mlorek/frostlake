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
 * A variable declared by its name alone, DECLARE q; or LET q;: the declaration is read, and refused while the
 * block compiles — "variable 'Q' must have either a type or an initializer to infer a type from" — at the
 * name in a DECLARE section and at the LET of a statement, after the block's syntax and before any name is
 * looked up; a procedure's and a function's body are refused as they are created (live-verified).
 */
public class TypelessDeclarationTest extends MeasuredCellsTestSupport {

    @Test
    public void aDeclareSection() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; BEGIN RETURN q; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; BEGIN q := 1; RETURN q; END");
        assertAnswer(
            "null",
            "DECLARE q INT; BEGIN RETURN q; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected ','.",
            "DECLARE q, r; BEGIN RETURN 1; END");
        assertAnswer(
            "1",
            "DECLARE q DEFAULT 1; BEGIN RETURN q; END");
        assertAnswer(
            "1",
            "DECLARE q := 1; BEGIN RETURN q; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'END'.",
            "BEGIN RETURN 1; DECLARE q INT; END");
        assertAnswer(
            "1",
            "BEGIN RETURN 1; DECLARE q INT; BEGIN RETURN q; END; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; r INT; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'BEGIN'.",
            "DECLARE q BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected ';'.",
            "DECLARE; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ';'.",
            "BEGIN RETURN 1; DECLARE; END");
    }

    @Test
    public void aLetAndOtherPlaces() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "BEGIN LET q; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; q2; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 15 |  variable 'Q' must have either a type "
                + "or an initializer to infer a type from",
            "DECLARE r INT; q; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 2 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE\n  q;\nBEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE \"q\"; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 |  variable 'Q' must have either a type "
                + "or an initializer to infer a type from",
            "BEGIN RETURN 1; DECLARE q; BEGIN RETURN 2; END; END");
        assertAnswer(
            "REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "DECLARE q r; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; BEGIN RETURN nosuch; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '1'.",
            "DECLARE q; BEGIN RETURN 1 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'y'. | syntax error "
                + "line 1 at position 31 unexpected 'RETURN'.",
            "DECLARE q; BEGIN SELECT 1 x y; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS $$DECLARE q; BEGIN RETURN 1; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS DECLARE q; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$DECLARE q; BEGIN RETURN 1; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 |  variable 'R' must have either a type "
                + "or an initializer to infer a type from",
            "DECLARE q VARCHAR; r; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; q INT; BEGIN RETURN 1; END");
    }

    @Test
    public void theOrderWrittenAndRoutineBodies() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b INT)");
        answer(
            "INSERT INTO t VALUES (1, 2), (3, 4)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS $$BEGIN LET q; RETURN 1; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS DECLARE q; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 3 at position 2 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE PROCEDURE pq() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  q;\nBEGIN RETURN 1; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; r NUMBER(99); BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | Invalid number precision: 99. Must be "
                + "between 0 and 38.",
            "DECLARE r NUMBER(99); q; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; e EXCEPTION; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 |  variable 'Q' must have either a type "
                + "or an initializer to infer a type from",
            "DECLARE e EXCEPTION; q; BEGIN RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "BEGIN LET q; LET q := 1; RETURN q; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 |  variable 'Q' must have either a type "
                + "or an initializer to infer a type from",
            "BEGIN RETURN 1; LET q; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 |  variable 'Q' must have either a type "
                + "or an initializer to infer a type from",
            "BEGIN IF (TRUE) THEN LET q; END IF; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'q' must have either a type or "
                + "an initializer to infer a type from",
            "BEGIN LET \"q\"; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "DECLARE q; BEGIN LET r; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET q; RETURN 1; END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS $$BEGIN LET q; RETURN 1; END$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'Q' must have either a type or "
                + "an initializer to infer a type from",
            "EXECUTE IMMEDIATE $$DECLARE q; BEGIN RETURN 1; END$$");
    }
}
