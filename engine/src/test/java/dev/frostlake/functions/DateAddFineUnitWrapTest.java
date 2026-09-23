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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DATEADD by a minute or finer takes any 64-bit count in the account's own arithmetic: minutes become seconds
 * in 64 bits and wrap, seconds join the epoch second in 64 bits and wrap, and milliseconds, microseconds and
 * nanoseconds split into seconds and a fraction first, so they never wrap. A TIME moves round its day in exact
 * arithmetic. Every cell is live-verified.
 */
public class DateAddFineUnitWrapTest extends BaseDatabaseTest {

    private static final String TS = "'2024-01-15 10:00:00'::TIMESTAMP_NTZ";

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String shifted(final String unit, final String count, final String source) {
        return scalar("SELECT DATEADD(" + unit + ", " + count + ", " + source + ")::VARCHAR");
    }

    @Test
    public void finerPartsSplitIntoSecondsFirst() {
        assertEquals("292279048-08-31 17:12:55.807", shifted("millisecond", "9223372036854775807", TS));
        assertEquals("146140536-05-09 01:36:27.904", shifted("millisecond", "4611686018427387904", TS));
        assertEquals("3170897-11-20 19:46:40.000", shifted("millisecond", "100000000000000000", TS));
        assertEquals("2340-12-05 03:46:40.000", shifted("millisecond", "10000000000000", TS));
        assertEquals("294301-01-24 14:00:54.775", shifted("microsecond", "9223372036854775807", TS));
        assertEquals("148162-07-21 00:00:27.387", shifted("microsecond", "4611686018427387904", TS));
        assertEquals("5192-11-29 19:46:40.000", shifted("microsecond", "100000000000000000", TS));
        assertEquals("2316-04-26 09:47:16.854", shifted("nanosecond", "9223372036854775807", TS));
        assertEquals("1731-10-06 10:12:43.145", shifted("nanosecond", "-9223372036854775808", TS));
        assertEquals("2024-01-15 10:00:00.123", shifted("microsecond", "1705312800123456", "'1970-01-01'::TIMESTAMP_NTZ"));
        assertEquals("2024-01-15 10:00:00.123456", scalar("SELECT TO_CHAR(DATEADD(microsecond, 1705312800123456, "
            + "'1970-01-01'::TIMESTAMP_NTZ), 'YYYY-MM-DD HH24:MI:SS.FF6')"));
        assertEquals("292279048-08-31 07:12:55.807", shifted("millisecond", "9223372036854775807", "'2024-01-15'::DATE"));
        assertEquals("2024-01-14 23:59:59.999", shifted("millisecond", "-1", "'2024-01-15'::DATE"));
        assertEquals("2024-01-16 00:00:00.000",
            shifted("microsecond", "1", "'2024-01-15 23:59:59.999999999'::TIMESTAMP_NTZ(9)"));
    }

    @Test
    public void minutesAndSecondsWrapInSixtyFourBits() {
        assertEquals("2024-01-15 09:59:00.000", shifted("minute", "9223372036854775807", TS));
        assertEquals("2024-01-15 10:00:00.000", shifted("minute", "-9223372036854775808", TS));
        assertEquals("2024-01-15 10:00:00.000", shifted("minute", "4611686018427387904", TS));
        assertEquals("2024-01-15 10:01:00.000", shifted("minute", "4611686018427387905", TS));
        assertEquals("356754317-08-28 10:55:00.000", shifted("minute", "9223372036854775", TS));
        assertEquals("2024-01-14 23:59:00.000", shifted("minute", "9223372036854775807", "'2024-01-15'::DATE"));
        assertEquals("2024-01-15 09:59:00.000 +0500",
            shifted("minute", "9223372036854775807", "'2024-01-15 10:00:00 +05:00'::TIMESTAMP_TZ"));
        assertEquals("109626273-07-03 17:45:04.000", shifted("second", "4611686018427387904", TS));
        assertEquals("109626273-07-03 07:45:04.000", shifted("second", "4611686018427387904", "'2024-01-15'::DATE"));
        assertEquals("316889409-02-09 03:46:40.000 Z",
            shifted("second", "10000000000000000", "'2024-01-15 10:00:00 +00:00'::TIMESTAMP_TZ"));
        // The largest count wraps round to a moment before the year 1; its month, day and time are shown here.
        assertTrue(shifted("second", "9223372036854775807", TS).endsWith("-02-09 18:29:51.000"));
        assertTrue(shifted("second", "-9223372036854775808", TS).endsWith("-02-09 18:29:52.000"));
    }

    @Test
    public void aTimeMovesRoundItsDay() {
        final String time = "'10:00:00'::TIME";
        assertEquals("04:07:00", shifted("minute", "9223372036854775807", time));
        assertEquals("17:12:55", shifted("millisecond", "9223372036854775807", time));
        assertEquals("14:00:54", shifted("microsecond", "9223372036854775807", time));
        assertEquals("18:29:52", shifted("second", "-9223372036854775808", time));
        assertEquals("01:30:07", shifted("second", "9223372036854775807", "'10:00:00.123456789'::TIME(9)"));
        assertEquals("20:40:00", shifted("minute", "100000000000000000", time));
        assertEquals("19:46:40", shifted("second", "100000000000000000", time));
        assertEquals("14:00:54", scalar("SELECT TIMEADD(microsecond, 9223372036854775807, " + time + ")::VARCHAR"));
        assertEquals("09:47:16", scalar("SELECT TIMESTAMPADD(nanosecond, 9223372036854775807, " + time + ")::VARCHAR"));
    }
}
