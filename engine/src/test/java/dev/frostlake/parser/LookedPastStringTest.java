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
 * A string the text never closes straight after the token the parse refuses: its lexer's line comes first, the
 * parser having read into it; a string left open further on is told after (all live-verified).
 */
public class LookedPastStringTest extends MeasuredCellsTestSupport {

    @Test
    public void lexerLineOrder() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 37 near '<EOF>'. | syntax error "
                + "line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 'y')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | parse "
                + "error line 1 at position 42 near '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 'y', 'z')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a = 'x, 'y'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '''.",
            "SELECT 'x, 'y'");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 19 near '<EOF>'. | syntax error "
                + "line 1 at position 16 unexpected 'y'.",
            "SELECT ABS('x, 'y')");
        assertAnswer("REFUSED Numeric value 'x, y' is not recognized", "SELECT 1 FROM t1 WHERE a IN ('x, y')");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 22 near '<EOF>'.",
            "SELECT 'x, 'y' FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 16 near '<EOF>'. | syntax error "
                + "line 1 at position 13 unexpected 'y'.",
            "SELECT ('x, 'y')");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 40 near '<EOF>'. | syntax error "
                + "line 1 at position 37 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN (1, 'x, 'y')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected '''.",
            "SELECT 1, 'x, 'y'");
    }

    @Test
    public void lexerLineOrderFurther() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        engine.execute("INSERT INTO t1 VALUES (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'.",
            "SELECT ABS('x, ' y)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 38 near '<EOF>'. | syntax error "
                + "line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 'y ')");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 38 near '<EOF>'. | syntax error "
                + "line 1 at position 34 unexpected 'yy'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 'yy')");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 40 near '<EOF>'. | syntax error "
                + "line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 'y', 2)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 39 near '<EOF>'. | syntax error "
                + "line 1 at position 36 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 1 'y')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'. | parse "
                + "error line 1 at position 23 near '<EOF>'.",
            "SELECT 1 x y, ('a, 'b')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | parse "
                + "error line 1 at position 41 near '<EOF>'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, 'y' 'z')");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 36 near '<EOF>'. | syntax error "
                + "line 1 at position 33 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a = ('x, 'y')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '''.",
            "SELECT 1 x, 'a, 'b'");
    }
}
