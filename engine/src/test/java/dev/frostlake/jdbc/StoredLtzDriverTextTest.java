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

package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stored TIMESTAMP_LTZ reaches a client in the session's current zone however it is read — the bare column,
 * SELECT *, MIN and MAX, an ORDER BY, a UNION and a CTAS copy — whatever zone was current when it was
 * written. Live-verified.
 */
public class StoredLtzDriverTextTest extends BaseJdbcTest {

    /** Every row's cells through getString, a comma between cells and a bar between rows. */
    private String rows(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                if (out.length() > 0) {
                    out.append(" | ");
                }
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    if (i > 1) {
                        out.append(", ");
                    }
                    out.append(rs.getString(i));
                }
            }
            return out.toString();
        }
    }

    @Test
    public void everyReadFollowsTheCurrentZone() throws SQLException {
        try {
            statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            statement.execute("CREATE OR REPLACE TABLE lt (ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ)");
            statement.execute("INSERT INTO lt VALUES ('2026-08-13T12:34:56.789+02:00'::TIMESTAMP_LTZ,"
                + " '2026-08-13T12:34:56.789+02:00'::TIMESTAMP_TZ)");
            assertEquals("2026-08-13 10:34:56.789 Z", rows("SELECT ltz FROM lt"));
            statement.execute("ALTER SESSION SET TIMEZONE = 'Asia/Kolkata'");
            final String kolkata = "2026-08-13 16:04:56.789 +0530";
            assertEquals(kolkata, rows("SELECT ltz FROM lt"));
            assertEquals(kolkata + ", 2026-08-13 12:34:56.789 +0200", rows("SELECT * FROM lt"));
            assertEquals(kolkata + ", " + kolkata, rows("SELECT MAX(ltz), MIN(ltz) FROM lt"));
            assertEquals(kolkata, rows("SELECT ltz FROM lt ORDER BY ltz"));
            statement.execute("CREATE OR REPLACE TABLE lt2 AS SELECT ltz FROM lt");
            statement.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            final String losAngeles = "2026-08-13 03:34:56.789 -0700";
            assertEquals(losAngeles, rows("SELECT ltz FROM lt2"));
            assertEquals(losAngeles + " | " + losAngeles, rows("SELECT ltz FROM lt UNION ALL SELECT ltz FROM lt2"));
        } finally {
            statement.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
