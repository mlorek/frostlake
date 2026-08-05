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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Snowflake's AUTO date/time input detection accepts far more than ISO-8601. Parsing used to be
 * {@code LocalDateTime.parse(text.replace(' ', 'T'))}, which rejected a zone designator, {@code MM/DD/YYYY},
 * {@code DD-MON-YYYY}, any whitespace variation (a double blank became {@code TT}) and any non-canonical
 * digit count.
 */
public class AutoTemporalDetectionTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0).toString();
    }

    private String castAndRead(final String literal) {
        return scalar("SELECT '" + literal + "'::TIMESTAMP_NTZ");
    }

    private String insertAndRead(final String literal) {
        engine.execute("CREATE OR REPLACE TABLE ts_in (ts TIMESTAMP_NTZ(9))");
        engine.execute("INSERT INTO ts_in VALUES ('" + literal + "')");
        return scalar("SELECT ts FROM ts_in");
    }

    @Test
    public void zoneDesignatorIsAcceptedAndDroppedForNtz() {
        // 'Z' is how Snowflake itself renders +00:00; the offset is dropped for a wall-clock TIMESTAMP_NTZ.
        assertEquals("2025-07-19T16:35:12.589", insertAndRead("2025-07-19T16:35:12.589000000Z"));
        // Snowflake's own AUTO example for YYYY-MM-DD"T"HH24:MI:SS.FFTZH:TZM.
        assertEquals("2013-04-28T20:57:01.123456789", insertAndRead("2013-04-28T20:57:01.123456789+07:00"));
    }

    @Test
    public void slashMonthDayYearWithSingleDigitFields() {
        assertEquals("2024-03-05T12:34:59", insertAndRead("3/5/2024 12:34:59"));
        assertEquals("2008-02-18T02:36:48", scalar("SELECT '2/18/2008 02:36:48'::TIMESTAMP_NTZ"));
        assertEquals("2024-03-05", scalar("SELECT TO_DATE('3/5/2024')"));
    }

    @Test
    public void dayMonthNameYear() {
        assertEquals("1980-12-17", scalar("SELECT TO_DATE('17-DEC-1980')"));
        assertEquals("1980-12-17", scalar("SELECT TO_DATE('17-december-1980')"));
    }

    @Test
    public void extraWhitespaceBetweenDateAndTime() {
        assertEquals("2024-03-29T15:00:11", insertAndRead("2024-03-29  15:00:11"));
        assertEquals("2024-03-29T15:00:11", insertAndRead("2024-03-29 15:00:11"));
    }

    @Test
    public void nonCanonicalDigitCountIsRangeCheckedNotWidthCheckedOnCast() {
        // A zero-padded seconds field reads as its value when CAST ...
        assertEquals("9999-12-31T00:00:03", castAndRead("9999-12-31 00:00:003"));
        // ... but a genuinely out-of-range value is still rejected.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '2024-01-01 00:00:599'::TIMESTAMP_NTZ");
            }
        });
    }

    @Test
    public void theDmlWritePathIsWidthCheckedUnlikeTheCast() {
        // Live-verified on a real account: INSERTing '9999-12-31 00:00:003' into a
        // TIMESTAMP_NTZ column fails "DML operation to table … failed on column TS with error: Timestamp
        // '9999-12-31 00:00:003' is not recognized", and so do an over-wide month, day, hour, minute and
        // a 5-digit year — while the very same literals CAST fine. Fields NARROWER than canonical stay
        // legal on the write path.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                insertAndRead("9999-12-31 00:00:003");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                insertAndRead("2024-001-01 00:00:03");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                insertAndRead("02024-01-01 00:00:03");
            }
        });
        assertEquals("2024-01-01T00:00:03", insertAndRead("2024-1-01 00:00:3"));
        assertEquals("2024-01-01T00:00:03", insertAndRead("2024-01-01 00:00:03"));
    }

    @Test
    public void unparseableTextIsStillRejected() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT 'not a date'::TIMESTAMP_NTZ");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '2024-13-45'::TIMESTAMP_NTZ");
            }
        });
    }

    @Test
    public void overWideFieldsAreReadAsDigitRunsThenRangeChecked() {
        // Snowflake's AUTO detection sizes each field by its DIGIT RUN and then range-checks the value —
        // it does not require a canonical width. Live-verified on a real account: every
        // literal below casts to the stated value, while the out-of-range ones are rejected.
        assertEquals("9999-12-31", scalar("SELECT '9999-012-31'::DATE"));
        assertEquals("9999-12-31", scalar("SELECT '9999-12-031'::DATE"));
        assertEquals("2024-01-01", scalar("SELECT '2024-0001-01'::DATE"));
        assertEquals("+99999-12-31", scalar("SELECT '99999-12-31'::DATE"));
        assertEquals("+999999-12-31", scalar("SELECT '999999-12-31'::DATE"));
        assertEquals("2024-01-01T01:00", castAndRead("2024-01-01 001:00:00"));
        assertEquals("2024-01-01T00:03", castAndRead("2024-01-01 00:003:00"));
        assertEquals("2024-01-01T00:00:03", castAndRead("2024-01-01 00:00:0003"));
        assertEquals("2024-01-01T00:00:59", castAndRead("2024-01-01 00:00:059"));
        assertEquals("2024-01-01T00:00:03.500", castAndRead("2024-01-01 00:00:003.5"));
        assertEquals("9999-12-31", scalar("SELECT TO_DATE('9999-012-31')"));
        // Out of range stays an error: month 13, day 32, hour 25.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '9999-013-31'::DATE");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '9999-12-032'::DATE");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT '2024-01-01 025:00:00'::TIMESTAMP_NTZ");
            }
        });
    }

    @Test
    public void isoAndEpochFormsAreUnchanged() {
        assertEquals("2024-02-09T12:24:12", scalar("SELECT '2024-02-09T12:24:12'::TIMESTAMP_NTZ"));
        assertEquals("2024-02-09T12:24:12", scalar("SELECT '2024-02-09 12:24:12.000'::TIMESTAMP_NTZ"));
        assertEquals("2023-11-14T22:13:20", scalar("SELECT TO_TIMESTAMP(1700000000)"));
        assertEquals("2020-01-15", scalar("SELECT TO_DATE('2020-01-15')"));
    }
}
