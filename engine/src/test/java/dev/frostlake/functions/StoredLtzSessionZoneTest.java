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

/**
 * A stored TIMESTAMP_LTZ is an instant read in the zone of the session reading it, whatever zone was current
 * when it was written: its text, its hour, its casts, a shift and a format all follow the session's current
 * TIMEZONE, while a TIMESTAMP_TZ keeps the offset it was written with. Live-verified.
 */
public class StoredLtzSessionZoneTest extends BaseDatabaseTest {

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    @Test
    public void aStoredLtzIsReadInTheCurrentZone() {
        try {
            engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            engine.execute("CREATE OR REPLACE TABLE lt (ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ)");
            engine.execute("INSERT INTO lt VALUES ('2026-08-13T12:34:56.789+02:00'::TIMESTAMP_LTZ,"
                + " '2026-08-13T12:34:56.789+02:00'::TIMESTAMP_TZ)");
            assertEquals("2026-08-13 10:34:56.789 Z", scalar("SELECT TO_VARCHAR(ltz) FROM lt"));
            engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Kolkata'");
            assertEquals("2026-08-13 16:04:56.789 +0530", scalar("SELECT TO_VARCHAR(ltz) FROM lt"));
            assertEquals("16", scalar("SELECT HOUR(ltz) FROM lt"));
            assertEquals("16", scalar("SELECT DATE_PART(hour, ltz) FROM lt"));
            assertEquals("16:04:56", scalar("SELECT TO_VARCHAR(ltz::TIME) FROM lt"));
            assertEquals("2026-08-13 16:04:56.789", scalar("SELECT TO_VARCHAR(ltz::TIMESTAMP_NTZ) FROM lt"));
            assertEquals("2026-08-13 17:04:56.789 +0530", scalar("SELECT TO_VARCHAR(DATEADD(hour, 1, ltz)) FROM lt"));
            assertEquals("16:04 +05:30", scalar("SELECT TO_VARCHAR(ltz, 'HH24:MI TZH:TZM') FROM lt"));
            assertEquals("true", scalar("SELECT ltz = '2026-08-13T12:34:56.789+02:00'::TIMESTAMP_TZ FROM lt"));
            assertEquals("2026-08-13 12:34:56.789 +0200", scalar("SELECT TO_VARCHAR(tz) FROM lt"));
            assertEquals("2026-08-13 16:04:56.789 +0530", scalar("SELECT TO_VARCHAR(tz::TIMESTAMP_LTZ) FROM lt"));
            engine.execute("CREATE OR REPLACE TABLE lt2 AS SELECT ltz FROM lt");
            engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertEquals("2026-08-13 03:34:56.789 -0700", scalar("SELECT TO_VARCHAR(ltz) FROM lt2"));
            assertEquals("3", scalar("SELECT HOUR(ltz) FROM lt2"));
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
