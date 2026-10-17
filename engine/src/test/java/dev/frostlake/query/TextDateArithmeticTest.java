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
 * A text beside a number under + and - is the number it spells, never the date: {@code '2024-01-15' + 1} is refused
 * "Numeric value '2024-01-15' is not recognized" on either side of the operator (live-verified), while a DATE plus a
 * number is a date, a numeric text plus a number a number, and DATEADD over a text a TIMESTAMP_NTZ.
 */
public class TextDateArithmeticTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextBesideANumberIsTheNumberItSpells() {
        assertCells(new String[][] {
            {"SELECT '2024-01-15' + 1", "REFUSED Numeric value '2024-01-15' is not recognized"},
            {"SELECT '2024-01-15' - 1", "REFUSED Numeric value '2024-01-15' is not recognized"},
            {"SELECT DATEADD(day, 1, '2024-01-15')", "2024-01-16T00:00"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(day, 1, '2024-01-15'))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT 1 + '2024-01-15'", "REFUSED Numeric value '2024-01-15' is not recognized"},
            {"SELECT SYSTEM$TYPEOF(DATEADD(month, 1, '2024-01-15'))", "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT '5' + 1, SYSTEM$TYPEOF('5' + 1)", "6|NUMBER(19,0)[SB1]"},
            {"SELECT '2024-01-15'::DATE + 1", "2024-01-16"},
        });
    }
}
