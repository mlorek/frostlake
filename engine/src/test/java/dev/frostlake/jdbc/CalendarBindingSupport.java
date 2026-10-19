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

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Binds a TIMESTAMP_NTZ, a DATE, a TIME, a TIMESTAMP_LTZ and a TIMESTAMP_TZ through the JDBC Calendar overloads, in a
 * JVM whose zone (Asia/Tokyo) is neither the Calendar's nor the session's, and reads them back with Calendars: the
 * answers the account's driver gives (live-verified), shared by the in-process and the HTTP driver's tests.
 */
final class CalendarBindingSupport {

    private CalendarBindingSupport() {
    }

    /**
     * The reads of a row without the DATE read with the UTC Calendar, which in a session that is not UTC the account's
     * driver answers by the session's zone (not modelled here).
     *
     * @param observed what {@link #observe} saw
     * @return the same, each read line without its last reading
     */
    static List<String> withoutDateReads(final List<String> observed) {
        final List<String> kept = new ArrayList<String>();
        for (final String line : observed) {
            kept.add(line.indexOf('|') >= 0 ? line : line.substring(0, line.lastIndexOf(' ')));
        }
        return kept;
    }

    /**
     * Runs the bindings and the reads under a session zone.
     *
     * @param connection the connection, its current schema set
     * @param sessionZone the session's TIMEZONE
     * @return the stored texts of the two rows, then each row's reads
     * @throws SQLException when a statement fails
     */
    static List<String> observe(final Connection connection, final String sessionZone) throws SQLException {
        final TimeZone saved = TimeZone.getDefault();
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        final Calendar kolkata = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        final List<String> seen = new ArrayList<String>();
        final Statement statement = connection.createStatement();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
        try {
            statement.execute("ALTER SESSION SET TIMEZONE = '" + sessionZone + "'");
            statement.execute("CREATE OR REPLACE TABLE ev "
                + "(k INT, at TIMESTAMP_NTZ, d DATE, t TIME, l TIMESTAMP_LTZ, z TIMESTAMP_TZ)");
            final Timestamp midnight = Timestamp.from(Instant.parse("2024-01-01T00:00:00Z"));
            final Date evening = new Date(Instant.parse("2024-01-01T20:00:00Z").toEpochMilli());
            final Time late = new Time(Instant.parse("1970-01-01T23:30:00Z").toEpochMilli());
            final PreparedStatement insert = connection.prepareStatement("INSERT INTO ev VALUES (?, ?, ?, ?, ?, ?)");
            try {
                bind(insert, 1, midnight, evening, late, utc);
                bind(insert, 2, midnight, evening, late, kolkata);
            } finally {
                insert.close();
            }
            final ResultSet text = statement.executeQuery(
                "SELECT k, at::VARCHAR, d::VARCHAR, t::VARCHAR, l::VARCHAR, z::VARCHAR FROM ev ORDER BY k");
            while (text.next()) {
                seen.add(text.getString(2) + "|" + text.getString(3) + "|" + text.getString(4) + "|"
                    + text.getString(5) + "|" + text.getString(6));
            }
            final ResultSet read = statement.executeQuery("SELECT k, at, d, t, l, z FROM ev ORDER BY k");
            while (read.next()) {
                seen.add(read.getTimestamp(2, utc).toInstant() + " " + read.getTimestamp("AT", kolkata).toInstant()
                    + " " + Instant.ofEpochMilli(read.getTime(4, utc).getTime())
                    + " " + read.getTimestamp(5, utc).toInstant() + " " + read.getTimestamp("Z", kolkata).toInstant()
                    + " " + Instant.ofEpochMilli(read.getDate(3, utc).getTime()));
            }
            return seen;
        } finally {
            TimeZone.setDefault(saved);
            statement.execute("ALTER SESSION UNSET TIMEZONE");
            statement.execute("DROP TABLE IF EXISTS ev");
            statement.close();
        }
    }

    /**
     * Binds a timestamp, a date and a time with the UTC Calendar while the session's input formats are set to read
     * none of them as the binding spells them: a bound value is typed, and the formats leave it alone, as they leave the
     * account's (live-verified).
     *
     * @param connection the connection, its current schema set
     * @return the stored row as text
     * @throws SQLException when a statement fails
     */
    static String underInputFormats(final Connection connection) throws SQLException {
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        final Statement statement = connection.createStatement();
        try {
            statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            statement.execute("ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'");
            statement.execute("ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI'");
            statement.execute("ALTER SESSION SET TIME_INPUT_FORMAT = 'HH12:MI AM'");
            statement.execute("CREATE OR REPLACE TABLE fmt (at TIMESTAMP_NTZ, d DATE, t TIME)");
            final PreparedStatement insert = connection.prepareStatement("INSERT INTO fmt VALUES (?, ?, ?)");
            try {
                insert.setTimestamp(1, Timestamp.from(Instant.parse("2024-03-05T10:00:00Z")), utc);
                insert.setDate(2, new Date(Instant.parse("2024-03-05T12:00:00Z").toEpochMilli()), utc);
                insert.setTime(3, new Time(Instant.parse("1970-01-01T10:15:00Z").toEpochMilli()), utc);
                insert.executeUpdate();
            } finally {
                insert.close();
            }
            final ResultSet text = statement.executeQuery("SELECT at::VARCHAR, d::VARCHAR, t::VARCHAR FROM fmt");
            text.next();
            return text.getString(1) + "|" + text.getString(2) + "|" + text.getString(3);
        } finally {
            statement.execute("ALTER SESSION UNSET TIME_INPUT_FORMAT");
            statement.execute("ALTER SESSION UNSET TIMESTAMP_INPUT_FORMAT");
            statement.execute("ALTER SESSION UNSET DATE_INPUT_FORMAT");
            statement.execute("ALTER SESSION UNSET TIMEZONE");
            statement.execute("DROP TABLE IF EXISTS fmt");
            statement.close();
        }
    }

    private static void bind(final PreparedStatement insert, final int key, final Timestamp at,
                             final Date day, final Time clock, final Calendar calendar) throws SQLException {
        insert.setInt(1, key);
        insert.setTimestamp(2, at, calendar);
        insert.setDate(3, day, calendar);
        insert.setTime(4, clock, calendar);
        insert.setTimestamp(5, at, calendar);
        insert.setTimestamp(6, at, calendar);
        insert.executeUpdate();
    }
}
