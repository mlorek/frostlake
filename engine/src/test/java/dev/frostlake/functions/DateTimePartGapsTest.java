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
 * The date/time part functions no test named before: DAYOFWEEK / WEEK / YEAROFWEEK under the
 * default week policy, TIMEADD / TIMEDIFF over TIME and TIMESTAMP values, and TIME_FROM_PARTS
 * including its overflow wrapping.
 */
public class DateTimePartGapsTest extends BaseDatabaseTest {

    private long num(final String sql) {
        return ((Number) engine.executeQuery("SELECT " + sql).getRows().get(0).getValue(0)).longValue();
    }

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery("SELECT " + sql).getRows().get(0).getValue(0));
    }

    @Test
    public void dayOfWeekUnderTheDefaultPolicy() {
        // default WEEK_START: Sunday counts 0 — 2026-08-09 is a Sunday, the 10th a Monday
        assertEquals(0L, num("DAYOFWEEK('2026-08-09'::DATE)"));
        assertEquals(1L, num("DAYOFWEEK('2026-08-10'::DATE)"));
        assertEquals(6L, num("DAYOFWEEK('2026-08-15'::DATE)"));
    }

    @Test
    public void weekAndYearOfWeekMidYear() {
        // mid-year cells dodge the January policy edges on purpose
        assertEquals(33L, num("WEEK('2026-08-10'::DATE)"));
        assertEquals(2026L, num("YEAROFWEEK('2026-08-10'::DATE)"));
    }

    @Test
    public void timeAddOverTimeAndTimestamp() {
        assertEquals("true", text("TIMEADD(minute, 30, '10:00:00'::TIME) = '10:30:00'::TIME"));
        assertEquals("true", text("TIMEADD(hour, -2, '01:00:00'::TIME) = '23:00:00'::TIME"));
        assertEquals("true", text("TIMEADD(day, 1, '2026-01-31 12:00:00'::TIMESTAMP_NTZ)"
            + " = '2026-02-01 12:00:00'::TIMESTAMP_NTZ"));
    }

    @Test
    public void timeDiffCountsBoundaryCrossings() {
        assertEquals(90L, num("TIMEDIFF(minute, '10:00:00'::TIME, '11:30:00'::TIME)"));
        assertEquals(-1L, num("TIMEDIFF(hour, '11:30:00'::TIME, '10:31:00'::TIME)"));
        assertEquals(1L, num("TIMEDIFF(day, '2026-01-31 23:59:59'::TIMESTAMP_NTZ,"
            + " '2026-02-01 00:00:00'::TIMESTAMP_NTZ)"));
    }

    @Test
    public void timeFromPartsBuildsAndWraps() {
        assertEquals("true", text("TIME_FROM_PARTS(13, 30, 15) = '13:30:15'::TIME"));
        // components beyond their range carry over instead of erroring
        assertEquals("true", text("TIME_FROM_PARTS(10, 90, 0) = '11:30:00'::TIME"));
        assertEquals("true", text("TIME_FROM_PARTS(25, 0, 0) = '01:00:00'::TIME"));
    }
}
