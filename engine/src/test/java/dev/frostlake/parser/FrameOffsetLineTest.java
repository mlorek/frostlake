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
 * A window frame's offset that is no literal: after its first wrong token the account names one more token of the
 * frame — the BETWEEN's AND for a first bound before PRECEDING, the PRECEDING right after a wrong token that follows
 * the offset's first, the second bound's first token for a first bound before FOLLOWING, a second bound's PRECEDING or
 * FOLLOWING, and for a lone bound its PRECEDING or the token after it; a text or a NULL offset names nothing more
 * (live-verified).
 */
public class FrameOffsetLineTest extends MeasuredCellsTestSupport {

    @Test
    public void aFrameOffsetNamesOneMoreToken() {
        engine.execute("CREATE OR REPLACE TABLE ev (v INT, d DATE)");
        engine.execute("INSERT INTO ev VALUES (1, '2024-01-01'), (2, '2024-01-02'), (3, '2024-01-03')");
        assertCells(new String[][] {
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 56 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 + 1 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '+'. | syntax "
                + "error line 1 at position 60 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN -1 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '-'. | syntax "
                + "error line 1 at position 57 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN +1 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '+'. | syntax "
                + "error line 1 at position 57 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN (1) PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '('. | syntax "
                + "error line 1 at position 58 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1 DAY' + INTERVAL '1 DAY' PRECEDING AND "
                + "CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '+'. | syntax "
                + "error line 1 at position 91 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY d RANGE BETWEEN INTERVAL '1' DAY PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'DAY'. | syntax "
                + "error line 1 at position 62 unexpected 'PRECEDING'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN '1' PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''1''."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN NULL PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'NULL'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND v FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'v'. | syntax "
                + "error line 1 at position 62 unexpected 'FOLLOWING'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS v PRECEDING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'v'. | syntax "
                + "error line 1 at position 38 unexpected 'PRECEDING'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND v FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 56 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN abs(1) PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'abs'. | syntax "
                + "error line 1 at position 61 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v + 1 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 60 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v RANGE BETWEEN v PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'v'. | syntax "
                + "error line 1 at position 57 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND v FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'v'. | syntax "
                + "error line 1 at position 62 unexpected 'FOLLOWING'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND 1 FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 56 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1.5 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: error line 1 at position 44 | Invalid window frame"},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND 1 + 1 FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '+'. | syntax "
                + "error line 1 at position 66 unexpected 'FOLLOWING'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN -1 FOLLOWING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '-'. | syntax "
                + "error line 1 at position 61 unexpected 'CURRENT'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS - 1 PRECEDING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '-'. | syntax "
                + "error line 1 at position 49 unexpected ')'."},
            {"SELECT SUM(v) OVER (ORDER BY v RANGE BETWEEN -1 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '-'. | syntax "
                + "error line 1 at position 58 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN v PRECEDING AND UNBOUNDED FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'v'. | syntax "
                + "error line 1 at position 56 unexpected 'AND'."},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND -1 FOLLOWING) FROM ev",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected '-'. | syntax "
                + "error line 1 at position 63 unexpected 'FOLLOWING'."},
        });
    }
}
