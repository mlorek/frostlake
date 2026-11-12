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
 * REPLACE, REGEXP_REPLACE and ARRAY_GENERATE_RANGE judge the size of what they build: a text past the largest
 * VARCHAR (128 MB) and a range past the most numbers an array holds are refused with an SQL error, where the
 * statement used to die of OutOfMemoryError and take the heap every other session shares. The refusals name the
 * result as the account does, {@code (REPLACE result)}, {@code (regexp_replace result)} and the range function
 * (live-verified).
 */
public class BuiltResultSizeTest extends MeasuredCellsTestSupport {

    @Test
    public void aReplacementPastTheLargestTextIsRefused() {
        assertAnswer("REFUSED String '(REPLACE result)' is too long and would be truncated",
            "SELECT LENGTH(REPLACE(REPEAT('a', 10000000), 'a', REPEAT('b', 1000)))");
        assertAnswer("REFUSED String '(regexp_replace result)' is too long and would be truncated",
            "SELECT LENGTH(REGEXP_REPLACE(REPEAT('a', 1000000), 'a', REPEAT('b', 1000)))");
    }

    @Test
    public void aReplacementWithinTheLimitStillBuildsItsText() {
        assertAnswer("2000|134217728", "SELECT LENGTH(REPLACE(REPEAT('a', 1000), 'a', 'bb')), "
            + "LENGTH(REPLACE(REPEAT('a', 67108864), 'a', 'bb'))");
        assertAnswer("3000|xbybb", "SELECT LENGTH(REGEXP_REPLACE(REPEAT('ab', 1000), 'a', 'cc')), "
            + "REGEXP_REPLACE('xay a', '[a ]', 'b')");
    }

    @Test
    public void aRangePastTheMostNumbersAnArrayHoldsIsRefused() {
        assertAnswer("REFUSED Result array of ARRAY_GENERATE_RANGE is too large",
            "SELECT ARRAY_SIZE(ARRAY_GENERATE_RANGE(0, 2000000000))");
        assertAnswer("REFUSED Result array of ARRAY_GENERATE_RANGE is too large",
            "SELECT ARRAY_SIZE(ARRAY_GENERATE_RANGE(0, -2000000000, -1))");
    }

    @Test
    public void aLongRangeAnswersItsSizeAndItsNumbers() {
        assertAnswer("2000000|1999999|INTEGER|-2999999|0",
            "SELECT ARRAY_SIZE(ARRAY_GENERATE_RANGE(0, 2000000)), ARRAY_GENERATE_RANGE(0, 2000000)[1999999], "
            + "TYPEOF(ARRAY_GENERATE_RANGE(0, 2000000)[5]), ARRAY_GENERATE_RANGE(0, -3000000, -1)[2999999], "
            + "ARRAY_GENERATE_RANGE(0, 2000000)[0]");
        assertAnswer("1|[9223372036854775806]", "SELECT ARRAY_SIZE(ARRAY_GENERATE_RANGE(9223372036854775806, "
            + "9223372036854775807, 2)), ARRAY_GENERATE_RANGE(9223372036854775806, 9223372036854775807, 2)");
    }
}
