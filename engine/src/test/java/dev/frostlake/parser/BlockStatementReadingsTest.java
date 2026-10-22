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
 * A block's statement readings: a word run into a bracket in a later select item after a literal alias reports
 * the bracket's words, in a block its ')' too, and a function body's bracketed queries name their SELECT and
 * then the token live reads on to (all live-verified).
 */
public class BlockStatementReadingsTest extends MeasuredCellsTestSupport {

    @Test
    public void blockReadings() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax "
                + "error line 1 at position 18 unexpected 'x'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT 1 AS 1, (2 x y)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'. | syntax "
                + "error line 1 at position 24 unexpected 'x'. | syntax error line 1 at position 26 unexpected 'y'. | "
                + "syntax error line 1 at position 27 unexpected ')'.",
            "BEGIN SELECT 1 AS 1, (2 x y); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 71 unexpected 'a'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a a a FROM t); END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 73 unexpected 'a'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN LET y := (SELECT a a a FROM t); RETURN y; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 81 unexpected 'a'.",
            "CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN RETURN 1; RETURN (SELECT a a a FROM t); END");
    }
}
