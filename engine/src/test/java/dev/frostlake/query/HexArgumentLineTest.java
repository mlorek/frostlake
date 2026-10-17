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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A hexadecimal number written as a call's first argument is refused where it stands, and then the call's opening
 * parenthesis: {@code SELECT ABS(0x3)} names '0x3' at 11 and '(' at 10; after the first argument it has one line
 * (live-verified).
 */
public class HexArgumentLineTest extends MeasuredCellsTestSupport {

    @Test
    public void theCallsParenthesisFollowsTheNumber() {
        assertCells(new String[][] {
            {"SELECT CURRENT_TIMESTAMP(0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '0x3'. | syntax "
                + "error line 1 at position 24 unexpected '('."},
            {"SELECT ABS(0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x3'. | syntax "
                + "error line 1 at position 10 unexpected '('."},
            {"SELECT ABS(1, 0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '0x3'."},
            {"SELECT UPPER(0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '0x3'. | syntax "
                + "error line 1 at position 12 unexpected '('."},
            {"SELECT CONCAT('a', 0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '0x3'."},
            {"SELECT ABS((0x3))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '0x3'. | syntax "
                + "error line 1 at position 12 unexpected '0x3'."},
            {"SELECT ABS(0x3, 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x3'. | syntax "
                + "error line 1 at position 10 unexpected '('."},
            {"SELECT 0x3", "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '0x3'."},
            {"SELECT ABS(0b1)", "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'b1'."},
        });
    }

    @Test
    public void aLaterArgumentHasOneLine() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SELECT ABS(1, 0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '0x3'."},
            {"SELECT ABS(0x3 + 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x3'. | syntax "
                + "error line 1 at position 10 unexpected '('."},
            {"SELECT f(0x3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '0x3'. | syntax "
                + "error line 1 at position 8 unexpected '('."},
            {"SELECT ABS(0x3) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '0x3'. | syntax "
                + "error line 1 at position 10 unexpected '('."},
            {"SELECT ABS(0xG)", "REFUSED SQL compilation error: | parse error line 1 at position 13 near '71'."},
        });
    }
}
