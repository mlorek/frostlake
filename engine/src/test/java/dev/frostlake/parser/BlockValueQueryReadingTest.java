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
 * A block's statements: a faulty query in parentheses as a RETURN's or a LET's value, read by the block after its
 * SELECT; a RESULTSET's query read as a statement; a cast's comma inside a call in a value; and a FROM list's
 * JOIN INNER, CROSS or ASOF before a name (all live-verified).
 */
public class BlockValueQueryReadingTest extends MeasuredCellsTestSupport {

    @Test
    public void blockValueQueries() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ','. | syntax "
                + "error line 1 at position 29 unexpected ')'.",
            "BEGIN RETURN UPPER(CAST(1, 2)); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','.",
            "BEGIN RETURN CAST(1, 2); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ','. | syntax "
                + "error line 1 at position 30 unexpected ','.",
            "BEGIN RETURN CONCAT(CAST(1, 2), 'x'); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ','. | syntax "
                + "error line 1 at position 31 unexpected ')'.",
            "BEGIN LET v := UPPER(CAST(1, 2)); RETURN v; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 33 unexpected 'AS'.",
            "BEGIN LET q := (SELECT 1 AS 1, 2 AS 2); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'.",
            "BEGIN LET q := (SELECT 1 AS 1); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected 'AS'.",
            "BEGIN LET q := (SELECT 1, 2 AS 2); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected ')'.",
            "BEGIN LET q := (SELECT 1 x y); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax "
                + "error line 1 at position 46 unexpected '2'.",
            "BEGIN LET r RESULTSET := (SELECT 1 AS 1, 2 AS 2); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'y'.",
            "BEGIN LET r RESULTSET := (SELECT 1 x y); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'.",
            "BEGIN LET r RESULTSET := (SELECT 1 AS 1); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'n'. | syntax "
                + "error line 1 at position 34 unexpected 'n'.",
            "BEGIN SELECT 1 FROM FZ JOIN INNER n; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'n'. | syntax "
                + "error line 1 at position 34 unexpected 'n'.",
            "BEGIN SELECT 1 FROM FZ JOIN CROSS n; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'n'. | syntax "
                + "error line 1 at position 33 unexpected 'n'. | syntax error line 1 at position 34 unexpected ';'.",
            "BEGIN SELECT 1 FROM FZ JOIN ASOF n; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'n'. | syntax "
                + "error line 1 at position 28 unexpected 'n'.",
            "SELECT 1 FROM FZ JOIN INNER n");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 76 unexpected 'WHERE'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a FROM t WHERE); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 72 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT 1 x y); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'SELECT'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT FROM t); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 74 unexpected 'FROM'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN ((SELECT a a a FROM t)); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 83 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN ABS((SELECT a a a FROM t)); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 71 unexpected 'a'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a a a FROM t); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 76 unexpected ')'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1 + (SELECT 1 x y); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 76 unexpected 'WHERE'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a FROM t WHERE a =); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 30 unexpected 'WHERE'.",
            "BEGIN RETURN (SELECT a FROM t WHERE); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected ')'.",
            "BEGIN RETURN (SELECT 1 x y); END;");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
