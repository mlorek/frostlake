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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An arity sentence echoes a shift's unit word as the quoted, upper-cased text the plan made of it,
 * DATEADD('DAY', 0.0000001), and keeps a DISTINCT where it was written, SUM(DISTINCT 1, 2) OVER (); a
 * COUNT(DISTINCT a, b) over a window that orders its rows counts one argument alone and is refused at its OVER
 * (all live-verified).
 */
public class ShiftUnitAndDistinctEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void anAritySentenceEchoesTheCallAsTheAccountDoes() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DATEADD('DAY', 0.0000001)], expected 3, got 2",
            "SELECT DATEADD(day, 0.0000001)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DATEADD('DAY')], expected 3, got 1",
            "SELECT DATEADD(day)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DATEADD('day', 1)], expected 3, got 2",
            "SELECT DATEADD('day', 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[TIMEADD('HOUR', 1)], expected 3, got 2",
            "SELECT TIMEADD(hour, 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 36 | too many arguments for function "
                + "[Window function COUNT(DISTINCT ...)] expected 1, got 2",
            "SELECT COUNT(DISTINCT 0.0000001, 1) OVER (ORDER BY 0.0000001 ROWS 1 PRECEDING)");
        assertAnswer("1", "SELECT COUNT(DISTINCT 1, 2) OVER ()");
        assertAnswer("1", "SELECT COUNT(DISTINCT 1) OVER ()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SUM(DISTINCT 1, 2)] expected 1, got 2",
            "SELECT SUM(DISTINCT 1, 2) OVER ()");
    }
}
