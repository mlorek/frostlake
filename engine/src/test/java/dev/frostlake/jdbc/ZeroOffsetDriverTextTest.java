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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.values.TemporalText;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The driver text of a TIMESTAMP_LTZ or TIMESTAMP_TZ spells a zero offset {@code Z} and any other offset as
 * {@code ±HHMM}: under UTC, and under Europe/London in January, an LTZ reads {@code 2020-01-15 10:00:00.000 Z},
 * and so does a TZ written {@code +00:00} or {@code -00:00} under any zone. Live-verified.
 */
public class ZeroOffsetDriverTextTest extends BaseJdbcTest {

    /** The first cell of the first row through getString. */
    private String cell(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void aZeroOffsetIsZ() throws SQLException {
        try {
            statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            assertEquals("2020-01-15 10:00:00.000 Z", cell("SELECT '2020-01-15 10:00:00'::TIMESTAMP_LTZ"));
            assertEquals("2020-01-15 10:00:00.123 Z", cell("SELECT '2020-01-15 10:00:00.123456'::TIMESTAMP_LTZ"));
            assertEquals("2020-01-15 10:00:00.000 Z", cell("SELECT '2020-01-15 10:00:00 +00:00'::TIMESTAMP_TZ"));
            assertEquals("2020-01-15 10:00:00.000 Z", cell("SELECT '2020-01-15 10:00:00 -00:00'::TIMESTAMP_TZ"));
            assertEquals("2020-01-15 10:00:00.000 +0200", cell("SELECT '2020-01-15 10:00:00 +02:00'::TIMESTAMP_TZ"));
            assertEquals("2020-01-15 10:00:00.000", cell("SELECT '2020-01-15 10:00:00'::TIMESTAMP_NTZ"));
            assertTrue(cell("SELECT CURRENT_TIMESTAMP()").endsWith(" Z"));
            statement.execute("CREATE OR REPLACE TABLE tl (ts TIMESTAMP_LTZ)");
            statement.execute("INSERT INTO tl VALUES ('2020-01-15 10:00:00')");
            assertEquals("2020-01-15 10:00:00.000 Z", cell("SELECT ts FROM tl"));
        } finally {
            statement.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    @Test
    public void theZoneDecidesWhetherTheOffsetIsZero() throws SQLException {
        try {
            statement.execute("ALTER SESSION SET TIMEZONE = 'Europe/London'");
            assertEquals("2020-01-15 10:00:00.000 Z", cell("SELECT '2020-01-15 10:00:00'::TIMESTAMP_LTZ"));
            assertEquals("2020-07-15 10:00:00.000 +0100", cell("SELECT '2020-07-15 10:00:00'::TIMESTAMP_LTZ"));
            statement.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
            assertEquals("2020-01-15 10:00:00.000 -0800", cell("SELECT '2020-01-15 10:00:00'::TIMESTAMP_LTZ"));
            assertEquals("2020-01-15 10:00:00.000 Z", cell("SELECT '2020-01-15 10:00:00 +00:00'::TIMESTAMP_TZ"));
        } finally {
            statement.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }

    /** The HTTP driver's display of the wire's numeric offset spells a zero one Z too; the wire keeps +0000. */
    @Test
    public void theHttpDriverDisplaysTheSame() {
        assumeFalse(LiveSnowflake.enabled(), "the engine's own wire has no live counterpart");
        assertEquals("2020-01-15 10:00:00.000 Z",
            TemporalText.displayOfWire("2020-01-15 10:00:00.000 +0000", "TIMESTAMP_LTZ"));
        assertEquals("2020-01-15 10:00:00.123 Z",
            TemporalText.displayOfWire("2020-01-15 10:00:00.123456 +0000", "TIMESTAMP_TZ"));
        assertEquals("2020-01-15 10:00:00.000 +0200",
            TemporalText.displayOfWire("2020-01-15 10:00:00.000 +0200", "TIMESTAMP_TZ"));
    }
}
