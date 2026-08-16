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
 * DATEDIFF counts unit BOUNDARIES crossed, not elapsed whole units (Snowflake semantics): both
 * operands are truncated to the unit before differencing, so 23:00 to 01:00 the next day is one DAY
 * even though only two hours elapsed.
 */
public class DateDiffBoundaryTest extends BaseDatabaseTest {

    private long ql(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void testDayBoundaryCrossedWithinTwoHours() {
        assertEquals(1L, ql("SELECT DATEDIFF(DAY, '2026-07-26 23:00:00'::TIMESTAMP, '2026-07-27 01:00:00'::TIMESTAMP)"));
    }

    @Test
    public void testDayNotDoubleCountedOverLongElapsedTime() {
        assertEquals(1L, ql("SELECT DATEDIFF(DAY, '2026-07-26 01:00:00'::TIMESTAMP, '2026-07-27 23:00:00'::TIMESTAMP)"));
    }

    @Test
    public void testDaySameDayIsZero() {
        assertEquals(0L, ql("SELECT DATEDIFF(DAY, '2026-07-26 00:00:00'::TIMESTAMP, '2026-07-26 23:59:59'::TIMESTAMP)"));
    }

    @Test
    public void testDayNegativeWhenEndBeforeStart() {
        assertEquals(-1L, ql("SELECT DATEDIFF(DAY, '2026-07-27 01:00:00'::TIMESTAMP, '2026-07-26 23:00:00'::TIMESTAMP)"));
    }

    @Test
    public void testHourBoundary() {
        assertEquals(1L, ql("SELECT DATEDIFF(HOUR, '2026-07-26 10:59:00'::TIMESTAMP, '2026-07-26 11:01:00'::TIMESTAMP)"));
        assertEquals(0L, ql("SELECT DATEDIFF(HOUR, '2026-07-26 10:01:00'::TIMESTAMP, '2026-07-26 10:59:00'::TIMESTAMP)"));
    }

    @Test
    public void testMinuteAndSecondBoundaries() {
        assertEquals(1L, ql("SELECT DATEDIFF(MINUTE, '2026-07-26 10:00:59'::TIMESTAMP, '2026-07-26 10:01:01'::TIMESTAMP)"));
        assertEquals(1L, ql("SELECT DATEDIFF(SECOND, '2026-07-26 10:00:00.900'::TIMESTAMP, '2026-07-26 10:00:01.100'::TIMESTAMP)"));
    }

    @Test
    public void testQuarterBoundaryUnderThreeMonths() {
        // Feb -> Apr crosses one quarter boundary even though only two months apart.
        assertEquals(1L, ql("SELECT DATEDIFF(QUARTER, '2026-02-15'::DATE, '2026-04-15'::DATE)"));
        assertEquals(0L, ql("SELECT DATEDIFF(QUARTER, '2026-01-01'::DATE, '2026-03-31'::DATE)"));
        assertEquals(4L, ql("SELECT DATEDIFF(QUARTER, '2025-11-30'::DATE, '2026-10-01'::DATE)"));
    }

    @Test
    public void testWeekBoundaryMondayStart() {
        // is a Saturday, a Monday: one week boundary two days apart.
        assertEquals(1L, ql("SELECT DATEDIFF(WEEK, '2026-07-25'::DATE, '2026-07-27'::DATE)"));
        // Monday through Sunday of the same week: zero.
        assertEquals(0L, ql("SELECT DATEDIFF(WEEK, '2026-07-20'::DATE, '2026-07-26'::DATE)"));
    }

    @Test
    public void testMonthAndYearBoundaries() {
        assertEquals(1L, ql("SELECT DATEDIFF(MONTH, '2026-01-31'::DATE, '2026-02-01'::DATE)"));
        assertEquals(1L, ql("SELECT DATEDIFF(YEAR, '2025-12-31'::DATE, '2026-01-01'::DATE)"));
    }
}
