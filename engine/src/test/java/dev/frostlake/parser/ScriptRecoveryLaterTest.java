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
 * Script recovery: a block or a DECLARE after a statement refused at its semicolon reports its own lines, INSERT
 * INTO a name past a trailing comma refuses the VALUES too, a quantified LIKE's list followed by another bracket
 * names that bracket's first token alone, and IS TRUE in a select list names the bracket after it (all
 * live-verified).
 */
public class ScriptRecoveryLaterTest extends MeasuredCellsTestSupport {

    @Test
    public void laterStatementsAndPredicates() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 30 unexpected ';'. | syntax error line 1 at position 43 unexpected 'y'.",
            "SELECT 1 +; DESCRIBE TABLE t x; SELECT 1 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 29 unexpected 'y'. | syntax error line 1 at position 32 unexpected 'END'.",
            "SELECT 1 +; BEGIN SELECT 1 x y; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 +; BEGIN SELECT 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SELECT 1 +; DESC TABLE t x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 19 unexpected '<EOF>'.",
            "SELECT 1 +; DECLARE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax "
                + "error line 1 at position 26 unexpected '<EOF>'.",
            "SELECT 1 +; DESC TABLE t x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'INTO'. | syntax "
                + "error line 1 at position 28 unexpected 'VALUES'.",
            "SELECT 'foo', INSERT INTO t VALUES (1)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'.",
            "BEGIN WHILE (FALSE) LOOP SELECT 'foo' RETURN 1; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''y''.",
            "SELECT 1 FROM t WHERE a LIKE ANY ('x') ('y', 'z')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'. | syntax "
                + "error line 1 at position 17 unexpected '('.",
            "SELECT a IS TRUE ('x') FROM t");
    }
}
