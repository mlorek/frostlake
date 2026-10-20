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

    private static String pastText(final String bytes) {
        return "REFUSED Max LOB size (134217728) exceeded, actual size of parsed column is " + bytes;
    }

    @Test
    public void aCountPastTheLargestTextIsRefusedBeforeAnythingIsBuilt() {
        assertAnswer(pastText("10000000000"), "SELECT REPEAT('a', 1e10)");
        assertAnswer(pastText("10000000000"), "SELECT LPAD('a', 1e10, 'b')");
        assertAnswer(pastText("3000000000"), "SELECT RPAD('a', 3e9, 'b')");
        assertAnswer(pastText("10000000000"), "SELECT SPACE(1e10)");
        assertAnswer(pastText("9223372036854775807"), "SELECT REPEAT('ab', 1e30)");
        // The size counts UTF-8 bytes: seventy million two-byte characters are past the limit.
        assertAnswer(pastText("140000001"), "SELECT RPAD('a', 70000001, 'é')");
        assertAnswer(pastText("134217729"), "SELECT SPACE(134217729)");
    }

    @Test
    public void aCountPastTheLargestBinaryIsRefused() {
        assertAnswer("REFUSED Max LOB size (67108864) exceeded, actual size of parsed column is 10000000000",
            "SELECT LPAD(TO_BINARY('4845', 'HEX'), 1e10, TO_BINARY('01', 'HEX'))");
        assertAnswer("REFUSED Max LOB size (67108864) exceeded, actual size of parsed column is 67108865",
            "SELECT RPAD(TO_BINARY('4845', 'HEX'), 67108865, TO_BINARY('00', 'HEX'))");
    }

    @Test
    public void aCountWithinTheLimitStillBuildsItsText() {
        assertAnswer("40000000", "SELECT LENGTH(REPEAT('ab', 20000000))");
        assertAnswer("5|xyxya|a|   ||ababa", "SELECT LENGTH(LPAD('a', 5, 'xy')), LPAD('a', 5, 'xy'), RPAD('ab', 1), "
            + "SPACE(3) || '|', REPEAT('ab', 2.5)");
        assertAnswer("|wor|", "SELECT SPACE(-1e10), LPAD('world', 3, '*'), LPAD('a', -1e10, 'b')");
    }
}
