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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * The names CONVERT_TIMEZONE knows a zone by: its own in the zone database, spelled exactly — the old names the
 * database still links and its fixed-offset abbreviations among them — and nothing else. An offset, an
 * abbreviation the database does not keep, another case and a blank beside the name are each an unknown time
 * zone, the argument echoed as it was given; a NULL in any argument answers NULL before a name is read. Every
 * answer is live-verified.
 */
public class ConvertTimezoneNamesTest extends MeasuredCellsTestSupport {

    private static String between(final String from, final String to) {
        return "SELECT TO_CHAR(CONVERT_TIMEZONE('" + from + "', '" + to
            + "', '2024-07-01 12:00:00'::TIMESTAMP_NTZ), 'YYYY-MM-DD HH24:MI')";
    }

    @Test
    public void aZoneGoesByItsOwnName() {
        assertAnswer("2024-07-01 17:00", between("EST", "UTC"));
        assertAnswer("2024-06-30 18:00", between("ROC", "HST"));
        assertAnswer("2024-07-01 13:00", between("MST", "America/Denver"));
        assertAnswer("2024-07-01 12:00", between("Factory", "UTC"));
        assertAnswer("2024-07-01 12:00", between("Zulu", "GMT0"));
        assertAnswer("2024-07-01 12:00", between("UCT", "Universal"));
        assertAnswer("2024-07-01 12:00", between("Etc/UTC", "GMT"));
        assertAnswer("2024-07-01 18:00", between("US/Pacific", "Etc/GMT+1"));
        assertAnswer("2024-07-01 12:00", between("Asia/Calcutta", "Asia/Kolkata"));
        assertAnswer("2024-07-01 12:00", between("Europe/Kiev", "Europe/Kyiv"));
        assertAnswer("2024-07-01 12:00", between("Cuba", "EST5EDT"));
        assertAnswer("2024-07-01 13:00", between("Pacific/Kanton", "Etc/GMT-14"));
        assertAnswer("2024-07-01 18:00", between("America/Ciudad_Juarez", "UTC"));
        assertAnswer("2024-07-01 13:00", between("WET", "CET"));
        assertAnswer("2024-07-01 19:00", between("PST8PDT", "UTC"));
        assertAnswer("2024-07-01 05:00", between("Asia/Saigon", "UTC"));
        assertAnswer("2024-07-01 15:00", between("America/Argentina/Buenos_Aires", "UTC"));
    }

    @Test
    public void anythingElseIsAnUnknownTimeZone() {
        for (final String unknown : new String[] {"utc", "america/los_angeles", "+01:00", "+0100", "UTC+1", "GMT+1",
            "Z", "PST", " UTC", "Europe/London ", "", "SystemV/EST5", "localtime", "posixrules", "Nowhere/Zone"}) {
            assertAnswer("REFUSED Unknown timezone: '" + unknown + "'", between(unknown, "UTC"));
            assertAnswer("REFUSED Unknown timezone: '" + unknown + "'", between("UTC", unknown));
            assertAnswer("REFUSED Unknown timezone: '" + unknown + "'",
                "SELECT CONVERT_TIMEZONE('" + unknown + "', '2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ)");
        }
        assertAnswer("REFUSED Unknown timezone: '1'",
            "SELECT CONVERT_TIMEZONE(1, '2024-01-01'::TIMESTAMP_NTZ)");
    }

    @Test
    public void aNullAnswersNullBeforeANameIsRead() {
        assertAnswer("null", "SELECT CONVERT_TIMEZONE('Nowhere', NULL)");
        assertAnswer("null", "SELECT CONVERT_TIMEZONE('Nowhere', 'UTC', NULL)");
        assertAnswer("null", "SELECT CONVERT_TIMEZONE(NULL, 'Nowhere', '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer("null", "SELECT CONVERT_TIMEZONE('UTC', NULL, '2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer("null", "SELECT CONVERT_TIMEZONE(NULL, '2024-01-01'::TIMESTAMP_NTZ)");
    }

    @Test
    public void whatIsConvertedIsATimestamp() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function"
            + " 'CONVERT_TIMEZONE': (VARCHAR(3), NUMBER(1,0))", "SELECT CONVERT_TIMEZONE('UTC', 1)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function"
            + " 'CONVERT_TIMEZONE': (VARCHAR(3), BOOLEAN)", "SELECT CONVERT_TIMEZONE('UTC', TRUE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function"
            + " 'CONVERT_TIMEZONE': (VARCHAR(3), VARCHAR(3), NUMBER(1,0))", "SELECT CONVERT_TIMEZONE('UTC', 'UTC', 1)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]",
            "SELECT CONVERT_TIMEZONE('UTC', '10:00:00'::TIME)");
        assertAnswer("REFUSED Timestamp 'x' is not recognized", "SELECT CONVERT_TIMEZONE('UTC', 'x')");
    }
}
