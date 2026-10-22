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
 * The *_FROM_PARTS functions over parts far from today's dates: years past 9999 build their dates, and a
 * TIME's or a timestamp's seconds and nanoseconds past 32 bits roll over into the parts above them (all
 * live-verified).
 */
public class DateFromPartsRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void farParts() {
        assertAnswer("+10000-01-01", "SELECT DATE_FROM_PARTS(10000, 1, 1)");
        assertAnswer("+100000-01-01", "SELECT DATE_FROM_PARTS(100000, 1, 1)");
        assertAnswer("+262143-01-01", "SELECT DATE_FROM_PARTS(262143, 1, 1)");
        assertAnswer("+262144-01-01", "SELECT DATE_FROM_PARTS(262144, 1, 1)");
        answer("SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 0, 0, 2147483647)");
        answer("SELECT TIMESTAMP_FROM_PARTS(2024, 1, 1, 0, 0, 0, 2147483647)");
        assertAnswer("07:00", "SELECT TIME_FROM_PARTS(2147483647, 0, 0)");
        assertAnswer("03:14:07", "SELECT TIME_FROM_PARTS(0, 0, 2147483647)");
    }
}
