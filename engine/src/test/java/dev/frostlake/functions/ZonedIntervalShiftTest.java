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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A TIMESTAMP_LTZ or TIMESTAMP_TZ shifted by an INTERVAL or by DATEADD keeps its flavour. A TIMESTAMP_TZ moves at
 * the offset it was written with. A TIMESTAMP_LTZ is an instant in the session's zone, and across a daylight-saving
 * change a part shorter than a day moves the instant, a DAY or a WEEK moves it by whole days and then by the change
 * in offset, and a MONTH, a QUARTER or a YEAR moves the wall clock. An INTERVAL written before the date or timestamp
 * it is added to is refused by its argument types. Every cell is live-verified.
 */
public class ZonedIntervalShiftTest extends BaseDatabaseTest {

    private static final String LTZ = "'2024-03-09 12:00:00'::TIMESTAMP_LTZ";

    @Override
    protected void setupTest() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
    }

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Each {expression, text} cell as the expression's TO_VARCHAR. */
    private void assertShifts(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer("SELECT TO_VARCHAR(" + cell[0] + ")"), cell[0]);
        }
    }

    @Test
    public void aZonedTimestampTakesAnIntervalAndKeepsItsFlavour() {
        assertShifts(new String[][] {
            {"'2024-01-01'::TIMESTAMP_LTZ(3) + INTERVAL '1 day'", "2024-01-02 00:00:00.000 -0800"},
            {"'2024-01-01 00:00:00 +02:00'::TIMESTAMP_TZ(3) + INTERVAL '1 day'", "2024-01-02 00:00:00.000 +0200"},
            {"'2024-03-09 12:00:00'::TIMESTAMP_LTZ(3) + INTERVAL '1 day'", "2024-03-10 12:00:00.000 -0700"},
            {"'2024-03-09 12:00:00.123'::TIMESTAMP_LTZ + INTERVAL '1 millisecond'", "2024-03-09 12:00:00.124 -0800"},
            {"'2024-03-09 12:00:00 +05:30'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-03-09 22:30:00.000 -0800"},
        });
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF('2024-01-01'::TIMESTAMP_LTZ(3) + INTERVAL '1 day')"));
        assertEquals("TIMESTAMP_TZ(9)[SB16]",
            answer("SELECT SYSTEM$TYPEOF('2024-01-01 00:00:00 +02:00'::TIMESTAMP_TZ(3) + INTERVAL '1 day')"));
        assertEquals("true", answer("SELECT " + LTZ + " + INTERVAL '1 day' = '2024-03-10 12:00:00'::TIMESTAMP_LTZ"));
    }

    @Test
    public void aTimestampTzMovesAtTheOffsetItWasWrittenWith() {
        assertShifts(new String[][] {
            {"'2024-03-09 12:00:00 -08:00'::TIMESTAMP_TZ + INTERVAL '1 day'", "2024-03-10 12:00:00.000 -0800"},
            {"'2024-03-09 12:00:00 -08:00'::TIMESTAMP_TZ + INTERVAL '24 hours'", "2024-03-10 12:00:00.000 -0800"},
            {"'2024-01-31 12:00:00 +05:00'::TIMESTAMP_TZ + INTERVAL '1 month'", "2024-02-29 12:00:00.000 +0500"},
            {"'2024-03-10 12:00:00 -07:00'::TIMESTAMP_TZ - INTERVAL '1 day'", "2024-03-09 12:00:00.000 -0700"},
            {"DATEADD(day, 1, '2024-03-09 12:00:00 -08:00'::TIMESTAMP_TZ)", "2024-03-10 12:00:00.000 -0800"},
            {"DATEADD(hour, 24, '2024-03-09 12:00:00 -08:00'::TIMESTAMP_TZ)", "2024-03-10 12:00:00.000 -0800"},
            {"DATEADD(month, 1, '2024-02-10 02:30:00 -08:00'::TIMESTAMP_TZ)", "2024-03-10 02:30:00.000 -0800"},
        });
    }

    @Test
    public void aPartShorterThanADayMovesTheInstant() {
        assertShifts(new String[][] {
            {LTZ + " + INTERVAL '24 hours'", "2024-03-10 13:00:00.000 -0700"},
            {LTZ + " + INTERVAL '90 minutes'", "2024-03-09 13:30:00.000 -0800"},
            {LTZ + " - INTERVAL '24 hours'", "2024-03-08 12:00:00.000 -0800"},
            {"'2024-03-11 12:00:00'::TIMESTAMP_LTZ - INTERVAL '24 hours'", "2024-03-10 12:00:00.000 -0700"},
            {"'2024-03-10 01:30:00'::TIMESTAMP_LTZ + INTERVAL '1 hour'", "2024-03-10 03:30:00.000 -0700"},
            {"'2024-11-03 01:30:00 -07:00'::TIMESTAMP_LTZ + INTERVAL '1 hour'", "2024-11-03 01:30:00.000 -0800"},
            {"'2024-03-10 03:30:00 -07:00'::TIMESTAMP_LTZ - INTERVAL '1 hour'", "2024-03-10 01:30:00.000 -0800"},
            {"'2024-11-03 00:30:00'::TIMESTAMP_LTZ + INTERVAL '60 minutes'", "2024-11-03 01:30:00.000 -0700"},
            {"DATEADD(hour, 24, " + LTZ + ")", "2024-03-10 13:00:00.000 -0700"},
            {"TIMESTAMPADD(hour, 24, " + LTZ + ")", "2024-03-10 13:00:00.000 -0700"},
            {"DATEADD(second, 86400, " + LTZ + ")", "2024-03-10 13:00:00.000 -0700"},
            {"DATEADD(minute, 90, '2024-03-10 01:00:00'::TIMESTAMP_LTZ)", "2024-03-10 03:30:00.000 -0700"},
            {"DATEADD(hour, -1, '2024-11-03 01:30:00 -08:00'::TIMESTAMP_LTZ)", "2024-11-03 01:30:00.000 -0700"},
        });
    }

    @Test
    public void aDayOrAWeekMovesByWholeDaysAndThenByTheChangeInOffset() {
        assertShifts(new String[][] {
            {LTZ + " + INTERVAL '1 day'", "2024-03-10 12:00:00.000 -0700"},
            {LTZ + " + INTERVAL '2 days'", "2024-03-11 12:00:00.000 -0700"},
            {LTZ + " + INTERVAL '1 week'", "2024-03-16 12:00:00.000 -0700"},
            {"'2024-03-10 12:00:00'::TIMESTAMP_LTZ - INTERVAL '1 day'", "2024-03-09 12:00:00.000 -0800"},
            {"'2024-03-10 12:00:00'::TIMESTAMP_LTZ + INTERVAL '-1 day'", "2024-03-09 12:00:00.000 -0800"},
            {"'2024-11-02 12:00:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-11-03 12:00:00.000 -0800"},
            {"'2024-03-09 02:30:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-03-10 01:30:00.000 -0800"},
            {"'2024-03-11 02:30:00'::TIMESTAMP_LTZ - INTERVAL '1 day'", "2024-03-10 03:30:00.000 -0700"},
            {"'2024-11-02 01:30:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-11-03 01:30:00.000 -0700"},
            {"'2024-11-04 01:30:00'::TIMESTAMP_LTZ - INTERVAL '1 day'", "2024-11-03 01:30:00.000 -0800"},
            {"'2024-11-03 01:30:00 -08:00'::TIMESTAMP_LTZ + INTERVAL '0 days'", "2024-11-03 01:30:00.000 -0800"},
            {"'2024-11-03 01:30:00 -08:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-11-04 01:30:00.000 -0800"},
            {"'2024-11-02 01:30:00 -07:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-11-03 01:30:00.000 -0700"},
            {"'2024-03-10 03:30:00'::TIMESTAMP_LTZ - INTERVAL '1 day' + INTERVAL '1 day'", "2024-03-10 03:30:00.000 -0700"},
            {"DATEADD(day, 1, " + LTZ + ")", "2024-03-10 12:00:00.000 -0700"},
            {"DATEADD(week, 1, " + LTZ + ")", "2024-03-16 12:00:00.000 -0700"},
            {"DATEADD(day, 1, '2024-03-09 02:30:00'::TIMESTAMP_LTZ)", "2024-03-10 01:30:00.000 -0800"},
            {"DATEADD(day, -1, '2024-11-04 01:30:00'::TIMESTAMP_LTZ)", "2024-11-03 01:30:00.000 -0800"},
            {"DATEADD(day, 1, '2024-11-02 01:30:00'::TIMESTAMP_LTZ)", "2024-11-03 01:30:00.000 -0700"},
            {"DATEADD(day, -1, '2024-03-11 02:30:00'::TIMESTAMP_LTZ)", "2024-03-10 03:30:00.000 -0700"},
        });
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", answer("SELECT SYSTEM$TYPEOF(DATEADD(day, 1, '2024-03-09 12:00:00'::TIMESTAMP_LTZ(3)))"));
    }

    @Test
    public void aMonthAQuarterOrAYearMovesTheWallClock() {
        assertShifts(new String[][] {
            {"'2024-01-31 12:00:00'::TIMESTAMP_LTZ + INTERVAL '1 month'", "2024-02-29 12:00:00.000 -0800"},
            {LTZ + " + INTERVAL '1 year'", "2025-03-09 12:00:00.000 -0700"},
            {LTZ + " + INTERVAL '1 quarter'", "2024-06-09 12:00:00.000 -0700"},
            {"'2024-02-10 02:30:00'::TIMESTAMP_LTZ + INTERVAL '1 month'", "2024-03-10 03:30:00.000 -0700"},
            {"'2024-04-10 02:30:00'::TIMESTAMP_LTZ - INTERVAL '1 month'", "2024-03-10 03:30:00.000 -0700"},
            {"'2024-10-03 01:30:00'::TIMESTAMP_LTZ + INTERVAL '1 month'", "2024-11-03 01:30:00.000 -0700"},
            {"'2024-12-03 01:30:00'::TIMESTAMP_LTZ - INTERVAL '1 month'", "2024-11-03 01:30:00.000 -0700"},
            {"'2023-11-03 01:30:00'::TIMESTAMP_LTZ + INTERVAL '1 year'", "2024-11-03 01:30:00.000 -0700"},
            {"'2025-11-03 01:30:00'::TIMESTAMP_LTZ - INTERVAL '1 year'", "2024-11-03 01:30:00.000 -0700"},
            {"DATEADD(month, 1, '2024-02-10 02:30:00'::TIMESTAMP_LTZ)", "2024-03-10 03:30:00.000 -0700"},
            {"DATEADD(month, -1, '2024-12-03 01:30:00'::TIMESTAMP_LTZ)", "2024-11-03 01:30:00.000 -0700"},
            {"DATEADD(quarter, 1, '2023-12-10 02:30:00'::TIMESTAMP_LTZ)", "2024-03-10 03:30:00.000 -0700"},
        });
    }

    @Test
    public void theIntervalsPartsApplyInWrittenOrder() {
        assertShifts(new String[][] {
            {LTZ + " + INTERVAL '1 day, 2 hours'", "2024-03-10 14:00:00.000 -0700"},
            {LTZ + " + INTERVAL '1 day, 1 month'", "2024-04-10 12:00:00.000 -0700"},
            {"'2024-02-09 12:00:00'::TIMESTAMP_LTZ + INTERVAL '1 month, 1 day'", "2024-03-10 12:00:00.000 -0700"},
            {"'2024-03-09 02:30:00'::TIMESTAMP_LTZ + INTERVAL '1 day, 1 hour'", "2024-03-10 03:30:00.000 -0700"},
            {"'2024-03-09 02:30:00'::TIMESTAMP_LTZ + INTERVAL '1 hour, 1 day'", "2024-03-10 03:30:00.000 -0700"},
        });
    }

    @Test
    public void anotherZoneMovesByItsOwnChanges() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Europe/London'");
        assertShifts(new String[][] {
            {"'2024-03-30 01:30:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-03-31 00:30:00.000 Z"},
            {"'2024-10-26 01:30:00'::TIMESTAMP_LTZ + INTERVAL '1 day'", "2024-10-27 01:30:00.000 +0100"},
            {"'2024-10-28 01:30:00'::TIMESTAMP_LTZ - INTERVAL '1 day'", "2024-10-27 01:30:00.000 Z"},
        });
    }

    @Test
    public void anIntervalBeforeTheTimestampIsRefused() {
        final String refusal = "SQL compilation error: error line 1 at position 35|Invalid argument types for function '+': (INTERVAL, ";
        assertEquals(refusal + "TIMESTAMP_NTZ(9))",
            answer("SELECT TO_VARCHAR(INTERVAL '1 day' + '2024-03-09 12:00:00'::TIMESTAMP_NTZ)"));
        assertEquals(refusal + "DATE)", answer("SELECT TO_VARCHAR(INTERVAL '1 day' + '2024-03-09'::DATE)"));
        assertEquals(refusal + "TIMESTAMP_LTZ(9))", answer("SELECT TO_VARCHAR(INTERVAL '1 day' + " + LTZ + ")"));
        assertEquals(refusal + "TIMESTAMP_TZ(9))",
            answer("SELECT TO_VARCHAR(INTERVAL '1 day' + '2024-03-09 12:00:00 -08:00'::TIMESTAMP_TZ)"));
    }

    @Test
    public void aShiftedZonedColumnKeepsItsType() {
        engine.execute("CREATE TABLE shifted AS SELECT '2024-03-09 12:00:00'::TIMESTAMP_LTZ(3) + INTERVAL '1 day' AS c,"
            + " '2024-03-09 12:00:00'::TIMESTAMP_TZ(3) - INTERVAL '1 hour' AS d");
        assertEquals("TIMESTAMP_LTZ 9", answer("SELECT data_type || ' ' || datetime_precision FROM information_schema.columns"
            + " WHERE table_name = 'SHIFTED' AND column_name = 'C'"));
        assertEquals("TIMESTAMP_TZ 9", answer("SELECT data_type || ' ' || datetime_precision FROM information_schema.columns"
            + " WHERE table_name = 'SHIFTED' AND column_name = 'D'"));
        assertEquals("2024-03-10 12:00:00.000 -0700 2024-03-09 11:00:00.000 -0800",
            answer("SELECT TO_VARCHAR(c) || ' ' || TO_VARCHAR(d) FROM shifted"));
    }
}
