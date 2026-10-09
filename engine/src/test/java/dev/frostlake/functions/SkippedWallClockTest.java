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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wall clock the session zone's clocks skip, written as text and read as a zoned timestamp, keeps its digits and
 * takes one of the two offsets around the change: the offset before it at UTC and east of it, and west of UTC the
 * offset before for the gap's first second and the offset after from then on. A TIMESTAMP_NTZ converted to a zoned
 * type moves forward instead, and a clock the zone repeats takes the earlier offset (live-verified in ten zones).
 */
public class SkippedWallClockTest extends BaseDatabaseTest {

    /** Zone, wall clock, and: LTZ epoch / TZ epoch / NTZ-to-LTZ epoch / the TZ's own clock and offset. */
    private static final String[][] CELLS = {
            {"America/Los_Angeles", "2024-03-10 01:59:59", "1710064799/1710064799/1710064799/01.59.59 -0800"},
            {"America/Los_Angeles", "2024-03-10 02:00:00", "1710064800/1710064800/1710064800/02.00.00 -0800"},
            {"America/Los_Angeles", "2024-03-10 02:00:00.000000001", "1710064800/1710064800/1710064800/02.00.00 -0800"},
            {"America/Los_Angeles", "2024-03-10 02:00:00.5", "1710064800/1710064800/1710064800/02.00.00 -0800"},
            {"America/Los_Angeles", "2024-03-10 02:00:01", "1710061201/1710061201/1710064801/02.00.01 -0700"},
            {"America/Los_Angeles", "2024-03-10 02:01:00", "1710061260/1710061260/1710064860/02.01.00 -0700"},
            {"America/Los_Angeles", "2024-03-10 02:59:59.999", "1710064799/1710064799/1710068399/02.59.59 -0700"},
            {"America/Los_Angeles", "2024-03-10 03:00:00", "1710064800/1710064800/1710064800/03.00.00 -0700"},
            {"America/New_York", "2024-03-10 02:00:00", "1710054000/1710054000/1710054000/02.00.00 -0500"},
            {"America/New_York", "2024-03-10 02:00:01", "1710050401/1710050401/1710054001/02.00.01 -0400"},
            {"America/New_York", "2024-03-10 02:30:00", "1710052200/1710052200/1710055800/02.30.00 -0400"},
            {"America/New_York", "2024-03-10 03:00:00", "1710054000/1710054000/1710054000/03.00.00 -0400"},
            {"America/St_Johns", "2024-03-10 02:00:00", "1710048600/1710048600/1710048600/02.00.00 -0330"},
            {"America/St_Johns", "2024-03-10 02:00:01", "1710045001/1710045001/1710048601/02.00.01 -0230"},
            {"America/St_Johns", "2024-03-10 02:30:00", "1710046800/1710046800/1710050400/02.30.00 -0230"},
            {"America/St_Johns", "2024-03-10 03:00:00", "1710048600/1710048600/1710048600/03.00.00 -0230"},
            {"America/Santiago", "2024-09-08 00:00:00", "1725768000/1725768000/1725768000/00.00.00 -0400"},
            {"America/Santiago", "2024-09-08 00:00:01", "1725764401/1725764401/1725768001/00.00.01 -0300"},
            {"America/Santiago", "2024-09-08 00:30:00", "1725766200/1725766200/1725769800/00.30.00 -0300"},
            {"America/Santiago", "2024-09-08 01:00:00", "1725768000/1725768000/1725768000/01.00.00 -0300"},
            {"America/Havana", "2024-03-10 00:00:00", "1710046800/1710046800/1710046800/00.00.00 -0500"},
            {"America/Havana", "2024-03-10 00:00:01", "1710043201/1710043201/1710046801/00.00.01 -0400"},
            {"America/Havana", "2024-03-10 00:30:00", "1710045000/1710045000/1710048600/00.30.00 -0400"},
            {"America/Havana", "2024-03-10 01:00:00", "1710046800/1710046800/1710046800/01.00.00 -0400"},
            {"Atlantic/Azores", "2024-03-31 00:00:00", "1711846800/1711846800/1711846800/00.00.00 -0100"},
            {"Atlantic/Azores", "2024-03-31 00:00:01", "1711843201/1711843201/1711846801/00.00.01 Z"},
            {"Atlantic/Azores", "2024-03-31 00:30:00", "1711845000/1711845000/1711848600/00.30.00 Z"},
            {"Atlantic/Azores", "2024-03-31 01:00:00", "1711846800/1711846800/1711846800/01.00.00 Z"},
            {"Europe/London", "2024-03-31 01:00:00", "1711846800/1711846800/1711846800/01.00.00 Z"},
            {"Europe/London", "2024-03-31 01:00:01", "1711846801/1711846801/1711846801/01.00.01 Z"},
            {"Europe/London", "2024-03-31 01:30:00", "1711848600/1711848600/1711848600/01.30.00 Z"},
            {"Europe/London", "2024-03-31 02:00:00", "1711846800/1711846800/1711846800/02.00.00 +0100"},
            {"Asia/Tehran", "2022-03-22 00:00:00", "1647894600/1647894600/1647894600/00.00.00 +0330"},
            {"Asia/Tehran", "2022-03-22 00:00:01", "1647894601/1647894601/1647894601/00.00.01 +0330"},
            {"Asia/Tehran", "2022-03-22 00:30:00", "1647896400/1647896400/1647896400/00.30.00 +0330"},
            {"Asia/Tehran", "2022-03-22 01:00:00", "1647894600/1647894600/1647894600/01.00.00 +0430"},
            {"Australia/Sydney", "2024-10-06 02:00:00", "1728144000/1728144000/1728144000/02.00.00 +1000"},
            {"Australia/Sydney", "2024-10-06 02:00:01", "1728144001/1728144001/1728144001/02.00.01 +1000"},
            {"Australia/Sydney", "2024-10-06 02:30:00", "1728145800/1728145800/1728145800/02.30.00 +1000"},
            {"Australia/Sydney", "2024-10-06 03:00:00", "1728144000/1728144000/1728144000/03.00.00 +1100"},
            {"Pacific/Auckland", "2024-09-29 02:00:00", "1727532000/1727532000/1727532000/02.00.00 +1200"},
            {"Pacific/Auckland", "2024-09-29 02:00:01", "1727532001/1727532001/1727532001/02.00.01 +1200"},
            {"Pacific/Auckland", "2024-09-29 02:30:00", "1727533800/1727533800/1727533800/02.30.00 +1200"},
            {"Pacific/Auckland", "2024-09-29 03:00:00", "1727532000/1727532000/1727532000/03.00.00 +1300"},
            {"America/Los_Angeles", "2024-11-03 00:59:59", "1730620799/1730620799/1730620799/00.59.59 -0700"},
            {"America/Los_Angeles", "2024-11-03 01:00:00", "1730620800/1730620800/1730620800/01.00.00 -0700"},
            {"America/Los_Angeles", "2024-11-03 01:30:00", "1730622600/1730622600/1730622600/01.30.00 -0700"},
            {"America/Los_Angeles", "2024-11-03 01:59:59", "1730624399/1730624399/1730624399/01.59.59 -0700"},
            {"America/Los_Angeles", "2024-11-03 02:00:00", "1730628000/1730628000/1730628000/02.00.00 -0800"},
            {"Europe/London", "2024-10-27 00:59:59", "1729987199/1729987199/1729987199/00.59.59 +0100"},
            {"Europe/London", "2024-10-27 01:00:00", "1729987200/1729987200/1729987200/01.00.00 +0100"},
            {"Europe/London", "2024-10-27 01:30:00", "1729989000/1729989000/1729989000/01.30.00 +0100"},
            {"Europe/London", "2024-10-27 01:59:59", "1729990799/1729990799/1729990799/01.59.59 +0100"},
            {"Europe/London", "2024-10-27 02:00:00", "1729994400/1729994400/1729994400/02.00.00 Z"},
    };

    @Override
    protected void teardownTest() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    @Test
    public void aSkippedClockKeepsItsDigitsAtOneOfTheTwoOffsets() {
        String zone = null;
        for (final String[] cell : CELLS) {
            if (!cell[0].equals(zone)) {
                zone = cell[0];
                engine.execute("ALTER SESSION SET TIMEZONE = '" + zone + "'");
            }
            final ResultSet rs = engine.executeQuery("SELECT DATE_PART(epoch_second, '" + cell[1] + "'::TIMESTAMP_LTZ)"
                + " || '/' || DATE_PART(epoch_second, '" + cell[1] + "'::TIMESTAMP_TZ)"
                + " || '/' || DATE_PART(epoch_second, '" + cell[1] + "'::TIMESTAMP_NTZ::TIMESTAMP_LTZ)"
                + " || '/' || REPLACE(TO_VARCHAR('" + cell[1] + "'::TIMESTAMP_TZ, 'HH24:MI:SS TZHTZM'), ':', '.')");
            assertTrue(rs.next());
            assertEquals(cell[2], rs.getValue(0), zone + " " + cell[1]);
        }
    }

    @Test
    public void theReadingHoldsForAStoredValueAndTheConvertingFunctions() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE sk (l TIMESTAMP_LTZ, z TIMESTAMP_TZ)");
        engine.execute("INSERT INTO sk VALUES ('2024-03-10 02:30:00', '2024-03-10 02:30:00')");
        final ResultSet stored = engine.executeQuery("SELECT DATE_PART(epoch_second, l) || '/'"
            + " || REPLACE(TO_VARCHAR(l, 'HH24:MI:SS TZHTZM'), ':', '.') || '/'"
            + " || REPLACE(TO_VARCHAR(z, 'HH24:MI:SS TZHTZM'), ':', '.') FROM sk");
        assertTrue(stored.next());
        assertEquals("1710063000/01.30.00 -0800/02.30.00 -0700", stored.getValue(0));
        final ResultSet converted = engine.executeQuery("SELECT DATE_PART(epoch_second, TO_TIMESTAMP_LTZ('2024-03-10 02:30:00'))"
            + " || '/' || DATE_PART(epoch_second, TO_TIMESTAMP_TZ('2024-03-10 02:30:00'))"
            + " || '/' || DATE_PART(epoch_second, TRY_TO_TIMESTAMP_LTZ('2024-03-10 02:30:00'))"
            + " || '/' || DATE_PART(epoch_second, DATEADD(year, 1, '2023-03-12 02:30:00'::TIMESTAMP_LTZ))");
        assertTrue(converted.next());
        assertEquals("1710063000/1710063000/1710063000/1710232200", converted.getValue(0));
    }

    /** A value's epoch second, its clock and offset twice over — formatted and as TO_VARCHAR shows it — and its type. */
    private String shown(final String value) {
        final ResultSet rs = engine.executeQuery("SELECT DATE_PART(epoch_second, " + value + ") || '/'"
            + " || REPLACE(TO_VARCHAR(" + value + ", 'YYYYMMDD HH24:MI:SS.FF3 TZHTZM'), ':', '.') || '/'"
            + " || REPLACE(TO_VARCHAR(" + value + "), ':', '.') || '/' || SYSTEM$TYPEOF(" + value + ")");
        assertTrue(rs.next());
        return String.valueOf(rs.getValue(0));
    }

    @Test
    public void aZonedTimestampBuiltFromPartsStandsInItsZoneAndMovesForwardOverAGap() {
        final String[][] cells = {
            {"TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 10, 2, 30, 0)", "1710066600/20240310 03.30.00.000 -0700/2024-03-10 03.30.00.000 -0700/TIMESTAMP_LTZ(9)[SB16]"},
            {"TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 10, 2, 0, 0)", "1710064800/20240310 03.00.00.000 -0700/2024-03-10 03.00.00.000 -0700/TIMESTAMP_LTZ(9)[SB16]"},
            {"TIMESTAMP_LTZ_FROM_PARTS(2024, 1, 10, 2, 30, 0)", "1704882600/20240110 02.30.00.000 -0800/2024-01-10 02.30.00.000 -0800/TIMESTAMP_LTZ(9)[SB16]"},
            {"TIMESTAMP_LTZ_FROM_PARTS(2024, 7, 10, 2, 30, 0, 123456789)", "1720603800/20240710 02.30.00.123 -0700/2024-07-10 02.30.00.123 -0700/TIMESTAMP_LTZ(9)[SB16]"},
            {"TIMESTAMP_LTZ_FROM_PARTS(2024, 11, 3, 1, 30, 0)", "1730622600/20241103 01.30.00.000 -0700/2024-11-03 01.30.00.000 -0700/TIMESTAMP_LTZ(9)[SB16]"},
            {"TIMESTAMP_TZ_FROM_PARTS(2024, 3, 10, 2, 30, 0)", "1710066600/20240310 03.30.00.000 -0700/2024-03-10 03.30.00.000 -0700/TIMESTAMP_TZ(9)[SB16]"},
            {"TIMESTAMP_TZ_FROM_PARTS(2024, 3, 10, 2, 0, 0)", "1710064800/20240310 02.00.00.000 -0800/2024-03-10 02.00.00.000 -0800/TIMESTAMP_TZ(9)[SB16]"},
            {"TIMESTAMP_TZ_FROM_PARTS(2024, 1, 10, 2, 30, 0)", "1704882600/20240110 02.30.00.000 -0800/2024-01-10 02.30.00.000 -0800/TIMESTAMP_TZ(9)[SB16]"},
            {"TIMESTAMP_TZ_FROM_PARTS(2024, 3, 10, 2, 30, 0, 0, 'Europe/Warsaw')", "1710034200/20240310 02.30.00.000 +0100/2024-03-10 02.30.00.000 +0100/TIMESTAMP_TZ(9)[SB16]"},
            {"TIMESTAMP_TZ_FROM_PARTS(2024, 3, 31, 2, 30, 0, 0, 'Europe/Warsaw')", "1711848600/20240331 02.30.00.000 +0100/2024-03-31 02.30.00.000 +0100/TIMESTAMP_TZ(9)[SB16]"},
            {"TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 10, 2, 30, 0)", "1710037800/20240310 02.30.00.000 Z/2024-03-10 02.30.00.000/TIMESTAMP_NTZ(9)[SB16]"},
            {"TIMESTAMP_FROM_PARTS(2024, 3, 10, 2, 30, 0)", "1710037800/20240310 02.30.00.000 Z/2024-03-10 02.30.00.000/TIMESTAMP_NTZ(9)[SB16]"},
        };
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        for (final String[] cell : cells) {
            assertEquals(cell[1], shown(cell[0]), cell[0]);
        }
        engine.execute("ALTER SESSION SET TIMEZONE = 'Europe/London'");
        assertEquals("1711848600/20240331 02.30.00.000 +0100/2024-03-31 02.30.00.000 +0100/TIMESTAMP_LTZ(9)[SB16]", shown("TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 31, 1, 30, 0)"));
        assertEquals("1711848600/20240331 01.30.00.000 Z/2024-03-31 01.30.00.000 Z/TIMESTAMP_TZ(9)[SB16]", shown("TIMESTAMP_TZ_FROM_PARTS(2024, 3, 31, 1, 30, 0)"));
    }
}
