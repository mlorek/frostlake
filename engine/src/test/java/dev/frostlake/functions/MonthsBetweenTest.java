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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MONTHS_BETWEEN uses Snowflake's formula (y1-y2)*12 + (m1-m2) + (day1-day2)/31, with a whole-number
 * result when the day numbers match or both dates are their month's last day. The previous impl
 * double-counted the day difference (adding a day fraction on top of a Period that already accounted
 * for it), so a partial-month case returned the wrong magnitude.
 */
public class MonthsBetweenTest extends BaseDatabaseTest {

    private double months(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void partialMonthUses31DayDayFraction() {
        assertEquals(1.8387096774193548, months("SELECT MONTHS_BETWEEN('2021-03-10', '2021-01-15')"), 1e-9);
    }

    @Test
    public void bothMonthEndIsWhole() {
        assertEquals(1.0, months("SELECT MONTHS_BETWEEN('2021-03-31', '2021-02-28')"), 1e-9);
    }

    @Test
    public void sameDayOfMonthIsWhole() {
        assertEquals(2.0, months("SELECT MONTHS_BETWEEN('2024-03-15', '2024-01-15')"), 1e-9);
    }

    @Test
    public void reversedArgumentsNegateTheResult() {
        assertEquals(-1.8387096774193548, months("SELECT MONTHS_BETWEEN('2021-01-15', '2021-03-10')"), 1e-9);
    }

    @Test
    public void wholeMonthsSpanningYears() {
        assertEquals(14.0, months("SELECT MONTHS_BETWEEN('2025-03-15', '2024-01-15')"), 1e-9);
    }
}
