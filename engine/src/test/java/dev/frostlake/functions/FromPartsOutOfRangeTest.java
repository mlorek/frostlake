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

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * DATE_FROM_PARTS and TIMESTAMP_FROM_PARTS over parts that roll over past the years a date can hold. The account
 * answers such parts with what an integer overflow leaves (an hour count wrapping to an hour before the day, a day
 * count to a year before Christ), which is recorded and not imitated: Frostlake counts the parts exactly and
 * refuses a day or a second past its range in the sentences it gives any date or timestamp out of range. Engine only.
 */
public class FromPartsOutOfRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void aDayPastTheRangeIsRefusedAndOneWithinItIsCountedExactly() {
        assumeFalse(isLiveSnowflake());
        assertAnswer("REFUSED Date 784351576412 is out of range", "SELECT DATE_FROM_PARTS(2147483647, 1, 1)");
        assertAnswer("REFUSED Date -784353015833 is out of range", "SELECT DATE_FROM_PARTS(-2147483648, 1, 1)");
        assertAnswer("REFUSED Date 365241780472 is out of range", "SELECT DATE_FROM_PARTS(1000000000, 1, 1)");
        assertAnswer("REFUSED Date 784351576412 is out of range",
            "SELECT DATE_FROM_PARTS(y, 1, 1) FROM (SELECT 2147483647 AS y)");
        assertAnswer("+999999999-12-31", "SELECT DATE_FROM_PARTS(999999999, 12, 31)");
        assertAnswer("+178958994-07-01", "SELECT DATE_FROM_PARTS(2024, 2147483647, 1)");
        assertAnswer("+5881634-07-10", "SELECT DATE_FROM_PARTS(2024, 1, 2147483647)");
    }

    @Test
    public void aSecondPastTheRangeIsRefused() {
        assumeFalse(isLiveSnowflake());
        assertAnswer("REFUSED Timestamp 67767976201996800 is out of range",
            "SELECT TIMESTAMP_FROM_PARTS(2147483647, 1, 1, 0, 0, 0)");
        assertAnswer("REFUSED Timestamp 67767976201996800 is out of range",
            "SELECT TIMESTAMP_LTZ_FROM_PARTS(2147483647, 1, 1, 0, 0, 0)");
        assertAnswer("REFUSED Timestamp 31556889832780800 is out of range",
            "SELECT TIMESTAMP_FROM_PARTS(1000000000, 1, 1, 0, 0, 0)");
        assertAnswer("247007", "SELECT YEAR(TIMESTAMP_FROM_PARTS(2024, 1, 1, 2147483647, 0, 0))");
    }
}
