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
 * REPEAT, LPAD, RPAD and SPACE judge the size of what they would build before they build it, and refuse a text
 * past the largest VARCHAR (128 MB, the VARCHAR(134217728) the account declares an unbounded text as) and a
 * binary past the largest BINARY (64 MB) with an SQL error. A huge count used to be held to Integer.MAX_VALUE and
 * the statement died of OutOfMemoryError instead.
 */
public class StringBuildSizeLimitTest extends MeasuredCellsTestSupport {

    private static final String PADDED = "REFUSED String '(padded string)' is too long and would be truncated";

    @Test
    public void aCountPastTheLargestTextIsRefusedBeforeAnythingIsBuilt() {
        // REPEAT's count times the text's length goes into a 32-bit length, typed as the product.
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](29,0){not null}, value 10000000000",
            "SELECT REPEAT('a', 1e10)");
        // A padded length past 32 bits is refused as a number out of range, a length within them that builds a
        // text past 128 MB as the padded string.
        assertAnswer("REFUSED Numeric value '10000000000' is out of range", "SELECT LPAD('a', 1e10, 'b')");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range", "SELECT RPAD('a', 3e9, 'b')");
        assertAnswer("REFUSED Numeric value '10000000000' is out of range", "SELECT SPACE(1e10)");
        assertAnswer(PADDED, "SELECT SPACE(134217729)");
        assertAnswer(PADDED, "SELECT REPEAT('a', 134217729)");
    }

    @Test
    public void aCountPastTheLargestBinaryIsRefused() {
        assertAnswer("REFUSED Numeric value '10000000000' is out of range",
            "SELECT LPAD(TO_BINARY('4845', 'HEX'), 1e10, TO_BINARY('01', 'HEX'))");
        assertAnswer(PADDED, "SELECT RPAD(TO_BINARY('4845', 'HEX'), 67108865, TO_BINARY('00', 'HEX'))");
    }

    @Test
    public void aCountWithinTheLimitStillBuildsItsText() {
        assertAnswer("40000000", "SELECT LENGTH(REPEAT('ab', 20000000))");
        assertAnswer("5|xyxya|a|   ||ababa", "SELECT LENGTH(LPAD('a', 5, 'xy')), LPAD('a', 5, 'xy'), RPAD('ab', 1), "
            + "SPACE(3) || '|', REPEAT('ab', 2.5)");
        assertAnswer("|wor|", "SELECT SPACE(-5), LPAD('world', 3, '*'), LPAD('world', -3, '*')");
        // A negative length past 32 bits is refused as a positive one is.
        assertAnswer("REFUSED Numeric value '-10000000000' is out of range", "SELECT SPACE(-1e10)");
    }
}
