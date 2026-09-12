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

package dev.frostlake.task;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The CRON schedule's next calendar fire and its refusals. The next-fire cells marked as the
 * account's own were read back from a real account's scheduled runs, against the same "from"
 * instants used here; the L-family cells lie beyond what the account shows ahead and follow the
 * documented meaning (the month's last day, the month's last such weekday).
 */
public class CronScheduleTest {

    private static final DateTimeFormatter UTC_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEE");

    /** Friday 2026-09-11 13:10:17 UTC. */
    private static final ZonedDateTime FRI_1310 = ZonedDateTime.of(2026, 9, 11, 13, 10, 17, 0, ZoneOffset.UTC);
    /** Friday 2026-09-11 13:12:52 UTC. */
    private static final ZonedDateTime FRI_1312 = ZonedDateTime.of(2026, 9, 11, 13, 12, 52, 0, ZoneOffset.UTC);
    /** Friday 2026-09-11 13:14:52 UTC. */
    private static final ZonedDateTime FRI_1314 = ZonedDateTime.of(2026, 9, 11, 13, 14, 52, 0, ZoneOffset.UTC);

    private static String next(final String cron, final ZonedDateTime from) {
        final ZonedDateTime fire = CronSchedule.parse("USING CRON " + cron).nextFireAfter(from);
        return fire == null ? null : fire.withZoneSameInstant(ZoneOffset.UTC).format(UTC_MINUTE);
    }

    private static String refusal(final String cron) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                CronSchedule.parse("USING CRON " + cron);
            }
        });
        return e.getMessage();
    }

    @Test
    public void stepsListsRangesAndZonesMatchTheAccount() {
        assertEquals("2026-09-11 13:14 Fri", next("*/7 * * * * UTC", FRI_1310));
        assertEquals("2026-09-11 14:00 Fri", next("0 */2 * * * UTC", FRI_1310));
        assertEquals("2026-09-11 16:30 Fri", next("30 9 * * 1-5 America/Los_Angeles", FRI_1310));
        assertEquals("2026-09-13 12:00 Sun", next("0 12 * * SUN UTC", FRI_1310));
        assertEquals("2026-09-11 13:15 Fri", next("15,45 * * * * UTC", FRI_1310));
        assertEquals("2026-09-11 13:20 Fri", next("10-40/10 * * * * UTC", FRI_1310));
        assertEquals("2026-09-13 00:00 Sun", next("0 0 * * 0 UTC", FRI_1312));
        assertEquals("2026-09-12 00:00 Sat", next("0 0 * * 6 UTC", FRI_1312));
        assertEquals("2026-09-14 04:00 Mon", next("0 6 * * MON-FRI Europe/Warsaw", FRI_1312));
        assertEquals("2026-09-12 00:00 Sat", next("0 0 * * */2 UTC", FRI_1312));
        assertEquals("2026-09-11 13:20 Fri", next("5/15 * * * * UTC", FRI_1314));
        assertEquals("2026-09-14 00:00 Mon", next("0 0 * * MON,3 UTC", FRI_1314));
        assertEquals("2026-09-11 14:00 Fri", next("*/61 * * * * UTC", FRI_1314));
        assertEquals("2026-09-12 00:00 Sat", next("0 0 * * * utc", FRI_1314));
        assertEquals("2026-09-12 05:00 Sat", next("0 0 * * * Etc/GMT+5", FRI_1314));
        assertEquals("2026-09-12 00:00 Sat", next("0   0  *  *  *   UTC", FRI_1314));
        assertEquals("2026-09-12 00:00 Sat", next("0 0 * JAN-DEC/2 * UTC", FRI_1314));
    }

    /** Both day fields restricted: either one matching is enough — a stepped star counts as restricted. */
    @Test
    public void restrictedDayOfMonthAndDayOfWeekAreOred() {
        assertEquals("2026-09-14 00:00 Mon", next("0 0 1 * MON UTC", FRI_1310));
        assertEquals("2026-09-14 00:00 Mon", next("0 0 1-7 * 1 UTC", FRI_1310));
        assertEquals("2026-09-14 00:00 Mon", next("0 0 L * 1 UTC", FRI_1312));
        assertEquals("2026-09-13 00:00 Sun", next("0 0 */2 * MON UTC", FRI_1314));
        assertEquals("2026-09-13 00:00 Sun", next("0 0 1-31/2 * MON UTC", FRI_1314));
    }

    @Test
    public void theLastDayFamilyAndTheCalendarEdges() {
        assertEquals("2026-09-30 00:00 Wed", next("0 0 L * * UTC", FRI_1310));
        assertEquals("2026-09-30 00:00 Wed", next("0 0 1,L * * UTC", FRI_1310));
        assertEquals("2026-09-25 00:00 Fri", next("0 0 * * 5L UTC", FRI_1310));
        assertEquals("2026-09-25 00:00 Fri", next("0 0 * * FRIL UTC", FRI_1310));
        assertEquals("2027-02-28 00:00 Sun", next("0 0 L 2 * UTC", FRI_1310));
        assertEquals("2028-02-29 00:00 Tue", next("0 0 29 2 * UTC", FRI_1310));
        assertEquals("2027-01-01 00:00 Fri", next("0 0 1 JAN,JUL * UTC", FRI_1310));
        assertEquals("2026-10-01 00:00 Thu", next("0 0 1 */3 * UTC", FRI_1310));
        assertEquals("2026-09-21 00:00 Mon", next("0 0 */10 * * UTC", FRI_1310));
        assertEquals("2026-12-31 23:59 Thu", next("59 23 31 12 * UTC", FRI_1310));
    }

    @Test
    public void theNextFireIsStrictlyAfterTheInstant() {
        final ZonedDateTime onTheMinute = ZonedDateTime.of(2026, 9, 11, 13, 14, 0, 0, ZoneOffset.UTC);
        assertEquals("2026-09-11 13:15 Fri", next("* * * * * UTC", onTheMinute));
    }

    @Test
    public void aScheduleThatNamesNoInstantHasNoNextFire() {
        assertNull(next("0 0 31 2 * UTC", FRI_1310));
        assertNull(next("0 0 31 4 * UTC", FRI_1310));
    }

    @Test
    public void malformedFieldsAreTheOneSentence() {
        final String[] malformed = {
            "0 0 ? * * UTC", "*/0 * * * * UTC", "10-5 * * * * UTC", "0 0 L-3 * * UTC", "0 24 * * * UTC",
            "0 0 1 13 * UTC", "0 0 1 0 * UTC", "0 0 0 * * UTC", "0 0 * * 1-5L UTC", "0 0 * * 7L UTC",
            "0,15, * * * * UTC", "* * * * * * UTC", "0 0 * * 7 UTC", "0 0 * * 1#2 UTC", "0 0 15W * * UTC",
            "61 * * * * UTC", "0 0 32 * * UTC", "0 0 * * 8 UTC", "0 0 * * L UTC", "* * * UTC",
        };
        for (final String cron : malformed) {
            assertEquals(CronSchedule.INVALID_SCHEDULE_MESSAGE, refusal(cron), cron);
        }
    }

    @Test
    public void anUnrecognizedZoneIsNamed() {
        assertEquals("Invalid schedule was specified. \"*\" is not a recognized time zone. Please specify"
            + " time zones accepted by the TIMEZONE parameter.", refusal("* * * * *"));
        assertEquals("Invalid schedule was specified. \"Mars/Phobos\" is not a recognized time zone. Please"
            + " specify time zones accepted by the TIMEZONE parameter.", refusal("* * * * * Mars/Phobos"));
        assertEquals("Invalid schedule was specified. \"+01:00\" is not a recognized time zone. Please"
            + " specify time zones accepted by the TIMEZONE parameter.", refusal("0 0 * * * +01:00"));
        assertEquals("Invalid schedule was specified. \"PST\" is not a recognized time zone. Please specify"
            + " time zones accepted by the TIMEZONE parameter.", refusal("0 0 * * * PST"));
    }
}
