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
 * The syntax-error lines of a window frame's offset that is no literal, more shapes: a bind as the offset in a
 * query a block assigns with LET, in a statement of a block and in a statement of its own; an offset in a
 * QUALIFY's window, two names as the offset, a RANGE frame's second bound and offsets of more than one token
 * (all live-verified).
 */
public class WindowFrameOffsetLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void bindOffsets() {
        engine.execute("CREATE OR REPLACE TABLE ev (v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 97 unexpected ':'. | syntax "
                + "error line 1 at position 100 unexpected 'PRECEDING'. | syntax error line 1 at position 134 "
                + "unexpected ')'.",
            "DECLARE k INT DEFAULT 1; BEGIN LET res RESULTSET := (SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k "
                + "PRECEDING AND CURRENT ROW) FROM ev); RETURN TABLE(res); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 75 unexpected ':'. | syntax "
                + "error line 1 at position 78 unexpected 'PRECEDING'. | syntax error line 1 at position 88 "
                + "unexpected 'AND'.",
            "DECLARE k INT DEFAULT 1; BEGIN SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k PRECEDING AND CURRENT "
                + "ROW) FROM ev; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ':'. | syntax "
                + "error line 1 at position 47 unexpected 'PRECEDING'.",
            "SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN :k PRECEDING AND CURRENT ROW) FROM ev");
    }

    @Test
    public void offsetShapes() {
        engine.execute("CREATE OR REPLACE TABLE ev (v INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'v'.",
            "SELECT v FROM ev QUALIFY SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) > 0");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 48 unexpected 'PRECEDING'.",
            "SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v v PRECEDING AND CURRENT ROW) FROM ev");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'v'. | syntax "
                + "error line 1 at position 63 unexpected 'FOLLOWING'.",
            "SELECT SUM(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND v FOLLOWING) FROM ev");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 60 unexpected 'AND'.",
            "SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v + 1 PRECEDING AND CURRENT ROW) FROM ev");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'v'. | syntax "
                + "error line 1 at position 57 unexpected 'AND'.",
            "SELECT SUM(v) OVER (ORDER BY v RANGE BETWEEN v PRECEDING AND CURRENT ROW) FROM ev");
    }
}
