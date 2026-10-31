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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Calendar getters and setters count a day before 1582 as java.sql's Date and Timestamp count it, in the Julian
 * calendar: getDate and getTimestamp read DATE 1500-01-01 and TIMESTAMP_NTZ 1500-01-01 10:00:00 with a Calendar of
 * the JVM's zone as without one, a UTC Calendar reads them as the UTC moments of those wall clocks, and setDate with a
 * UTC Calendar binds such a moment back as the same day in a UTC session. setTimestamp with a Calendar reads its
 * value's wall clock through java.time, as the account's driver does, so the clock it binds is nine days on, in the
 * Gregorian count. Both of Frostlake's transports agree with the account's driver.
 */
public class CalendarBeforeGregorianTest extends BaseDatabaseTest {

    @Test
    public void aCalendarOfTheJvmsZoneReadsAndBindsAsNoCalendarDoes() throws SQLException, IOException {
        if (isLiveSnowflake()) {
            try (Connection live = LiveSnowflake.openUnprepared()) {
                assertCalendarChangesNothing(live);
            }
            return;
        }
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:calendar_before_gregorian")) {
            assertCalendarChangesNothing(direct);
        }
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port)) {
            assertCalendarChangesNothing(http);
        } finally {
            server.stop();
        }
    }

    private static void assertCalendarChangesNothing(final Connection connection) throws SQLException {
        final Calendar own = Calendar.getInstance(TimeZone.getDefault());
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT '1500-01-01 10:00:00'::TIMESTAMP_NTZ AS a, '1500-01-01'::DATE AS b")) {
                rows.next();
                assertEquals("1500-01-01", rows.getDate(2).toString());
                assertEquals("1500-01-01", rows.getDate(2, own).toString());
                assertEquals("1500-01-01 10:00:00.0", rows.getTimestamp(1).toString());
                assertEquals("1500-01-01 10:00:00.0", rows.getTimestamp(1, own).toString());
                assertEquals(utcMillis(1500, 1, 1, 0), rows.getDate(2, utc).getTime());
                assertEquals(utcMillis(1500, 1, 1, 10), rows.getTimestamp(1, utc).getTime());
            }
        }
        assertEquals("1500-01-01", bound(connection, "SELECT ?::DATE::VARCHAR", Date.valueOf("1500-01-01"), null));
        assertEquals("1500-01-01", bound(connection, "SELECT ?::DATE::VARCHAR", Date.valueOf("1500-01-01"), own));
        assertEquals("1500-01-01", bound(connection, "SELECT ?::DATE::VARCHAR",
            new Date(utcMillis(1500, 1, 1, 0)), utc));
        assertEquals("1500-01-10 10:00:00.000", bound(connection, "SELECT ?::TIMESTAMP_NTZ::VARCHAR",
            new Timestamp(utcMillis(1500, 1, 1, 10)), utc));
    }

    /** The UTC moment of a wall clock, counted as java.sql counts it: in the Julian calendar before 1582. */
    private static long utcMillis(final int year, final int month, final int day, final int hour) {
        final Calendar fields = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        fields.clear();
        fields.set(year, month - 1, day, hour, 0, 0);
        return fields.getTimeInMillis();
    }

    /** The text a bound value reads back as, bound with the Calendar or, when it is null, without one. */
    private static String bound(final Connection connection, final String sql, final Object value,
                                final Calendar cal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Date) {
                if (cal == null) {
                    statement.setDate(1, (Date) value);
                } else {
                    statement.setDate(1, (Date) value, cal);
                }
            } else if (cal == null) {
                statement.setTimestamp(1, (Timestamp) value);
            } else {
                statement.setTimestamp(1, (Timestamp) value, cal);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }
}
