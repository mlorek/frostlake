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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The temporal reads {@link ZonedCellReadsTest} and {@link ZonedCellReadsHttpTest} pin, and the answers the
 * account's driver gave for them: each cell of a DATE, a TIME and the three timestamps, read by getString,
 * getObject, getTimestamp, getDate and getTime, with and without a Calendar, the JVM on Europe/London and the
 * session on America/Los_Angeles, UTC and Asia/Tokyo. Each answer names whether the object is a plain
 * java.sql value or a zoned one (a subclass of its own), how it prints, and the instant it holds (all
 * live-verified). A null answer is one the account gets wrong: a DATE printed by getString as the day before
 * when the session's zone runs ahead of the JVM's, which the driver does not imitate.
 */
final class ZonedCellReadsSupport {

    /** The JVM zone the answers were measured in. */
    static final String JVM_ZONE = "Europe/London";

    static final String SELECT = "SELECT d, t, ntz, ltz, tz FROM zcr ORDER BY k";

    private static final String[] COLUMNS = {"D", "T", "NTZ", "LTZ", "TZ"};

    private ZonedCellReadsSupport() {
    }

    /** The table the reads run over: a row with nine digits of fraction, a summer row and one near midnight. */
    static void table(final Statement statement) throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE zcr (k INT, d DATE, t TIME(9), ntz TIMESTAMP_NTZ(9), "
            + "ltz TIMESTAMP_LTZ(9), tz TIMESTAMP_TZ(9))");
        statement.execute("INSERT INTO zcr VALUES "
            + "(1, '2024-01-02', '10:11:12.345678901', '2024-01-02 10:11:12.345678901', "
            + "'2024-01-02 10:11:12.345678901', '2024-01-02 10:11:12.345678901 +01:00'), "
            + "(2, '2024-07-02', '10:11:12', '2024-07-02 10:11:12', '2024-07-02 10:11:12', "
            + "'2024-07-02 10:11:12 +01:00'), "
            + "(3, '2024-01-02', '23:30:00.5', '2024-01-02 23:30:00.5', '2024-01-02 23:30:00.5', "
            + "'2024-01-02 23:30:00.5 -05:00')");
    }

    /**
     * Every cell of the query read by each getter, in the order the answers list them.
     *
     * @param connection the connection
     * @param cal        the Calendar the getters take, or null for none
     * @param into       where the answers go
     */
    static void read(final Connection connection, final Calendar cal, final List<String> into)
            throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(SELECT)) {
            while (rs.next()) {
                for (final String c : COLUMNS) {
                    if (cal == null) {
                        into.add(answer(rs, c, "string", null));
                        into.add(answer(rs, c, "object", null));
                    }
                    into.add(answer(rs, c, "timestamp", cal));
                    into.add(answer(rs, c, "date", cal));
                    into.add(answer(rs, c, "time", cal));
                }
            }
        }
    }

    /** A Calendar of a zone. */
    static Calendar calendar(final String zone) {
        return Calendar.getInstance(TimeZone.getTimeZone(zone));
    }

    /** Asserts each answer the account gave, skipping the ones it gets wrong. */
    static void assertAnswers(final String[] expected, final List<String> actual) {
        assertEquals(expected.length, actual.size(), "answers");
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != null) {
                assertEquals(expected[i], actual.get(i), "answer " + (i + 1));
            }
        }
    }

    private static String answer(final ResultSet rs, final String c, final String getter, final Calendar cal) {
        try {
            switch (getter) {
                case "string":
                    return "String " + rs.getString(c);
                case "object":
                    return describe(rs.getObject(c));
                case "timestamp":
                    return describe(cal == null ? rs.getTimestamp(c) : rs.getTimestamp(c, cal));
                case "date":
                    return describe(cal == null ? rs.getDate(c) : rs.getDate(c, cal));
                default:
                    return describe(cal == null ? rs.getTime(c) : rs.getTime(c, cal));
            }
        } catch (final SQLException | RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage()).replace("\n", " | ");
        }
    }

    /** Whether a value is plain or zoned, how it prints and the instant it holds. */
    private static String describe(final Object v) {
        if (v == null) {
            return "null";
        }
        final boolean plain = v.getClass() == Timestamp.class || v.getClass() == Date.class
            || v.getClass() == Time.class;
        final String kind = plain ? "plain" : v instanceof java.util.Date ? "zoned" : v.getClass().getSimpleName();
        if (v instanceof Timestamp) {
            final Timestamp t = (Timestamp) v;
            return kind + " " + t + " @ " + Instant.ofEpochMilli(t.getTime()) + " nanos " + t.getNanos();
        }
        if (v instanceof java.util.Date) {
            return kind + " " + v + " @ " + Instant.ofEpochMilli(((java.util.Date) v).getTime());
        }
        return kind + " " + v;
    }

    /** A Los Angeles session's reads, without a Calendar. */
    static final String[] LOS_ANGELES = {
        "String 2024-01-02",
        "plain 2024-01-02 @ 2024-01-02T08:00:00Z",
        "plain 2024-01-02 00:00:00.0 @ 2024-01-02T00:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "String 10:11:12",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "String 2024-01-02 10:11:12.345",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T18:11:12.345Z",
        "String 2024-01-02 10:11:12.345 -0800",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T18:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T18:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T18:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T18:11:12.345Z",
        "String 2024-01-02 10:11:12.345 +0100",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        "String 2024-07-02",
        "plain 2024-07-02 @ 2024-07-02T08:00:00Z",
        "plain 2024-07-02 00:00:00.0 @ 2024-07-01T23:00:00Z nanos 0",
        "plain 2024-07-02 @ 2024-07-01T23:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "String 10:11:12",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "String 2024-07-02 10:11:12.000",
        "plain 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "plain 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T17:11:12Z",
        "String 2024-07-02 10:11:12.000 -0700",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T17:11:12Z nanos 0",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T17:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T17:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T17:11:12Z",
        "String 2024-07-02 10:11:12.000 +0100",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        "String 2024-01-02",
        "plain 2024-01-02 @ 2024-01-02T08:00:00Z",
        "plain 2024-01-02 00:00:00.0 @ 2024-01-02T00:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "String 23:30:00",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "String 2024-01-02 23:30:00.500",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 @ 2024-01-02T23:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T07:30:00.500Z",
        "String 2024-01-02 23:30:00.500 -0800",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T07:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T07:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T07:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T07:30:00.500Z",
        "String 2024-01-02 23:30:00.500 -0500",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
    };

    /** A Los Angeles session's reads with an Asia/Kolkata Calendar, then with a UTC one. */
    static final String[] LOS_ANGELES_CALENDARS = {
        "plain 2024-01-02 13:30:00.0 @ 2024-01-02T13:30:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T13:30:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "plain 2024-01-02 04:41:12.345678901 @ 2024-01-02T04:41:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T04:41:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T18:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T18:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T18:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T18:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        "plain 2024-07-02 13:30:00.0 @ 2024-07-02T12:30:00Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T12:30:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "plain 2024-07-02 05:41:12.0 @ 2024-07-02T04:41:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T04:41:12Z",
        "zoned 10:11:12 @ 2024-07-02T17:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T17:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T17:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T17:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        "plain 2024-01-02 13:30:00.0 @ 2024-01-02T13:30:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T13:30:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "plain 2024-01-02 18:00:00.5 @ 2024-01-02T18:00:00.500Z nanos 500000000",
        "plain 2024-01-02 @ 2024-01-02T18:00:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T07:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T07:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T07:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T07:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
        "plain 2024-01-02 08:00:00.0 @ 2024-01-02T08:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T08:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T18:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T18:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T18:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T18:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        "plain 2024-07-02 08:00:00.0 @ 2024-07-02T07:00:00Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T07:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "plain 2024-07-02 11:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T10:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T17:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T17:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T17:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T17:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        "plain 2024-01-02 08:00:00.0 @ 2024-01-02T08:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T08:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 @ 2024-01-02T23:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T07:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T07:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T07:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T07:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
    };

    /** A UTC session's reads, bare and with a Kolkata Calendar, then Tokyo's, bare and with a New York one. */
    static final String[] UTC_AND_TOKYO = {
        "String 2024-01-02",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "plain 2024-01-02 00:00:00.0 @ 2024-01-02T00:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "String 10:11:12",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "String 2024-01-02 10:11:12.345",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T10:11:12.345Z",
        "String 2024-01-02 10:11:12.345 Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T10:11:12.345Z",
        "String 2024-01-02 10:11:12.345 +0100",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        "String 2024-07-02",
        "plain 2024-07-02 @ 2024-07-02T01:00:00Z",
        "plain 2024-07-02 00:00:00.0 @ 2024-07-01T23:00:00Z nanos 0",
        "plain 2024-07-02 @ 2024-07-01T23:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "String 10:11:12",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "String 2024-07-02 10:11:12.000",
        "plain 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "plain 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T10:11:12Z",
        "String 2024-07-02 10:11:12.000 Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T10:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T10:11:12Z",
        "String 2024-07-02 10:11:12.000 +0100",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        "String 2024-01-02",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "plain 2024-01-02 00:00:00.0 @ 2024-01-02T00:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "String 23:30:00",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "String 2024-01-02 23:30:00.500",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 @ 2024-01-02T23:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-02T23:30:00.500Z",
        "String 2024-01-02 23:30:00.500 Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-02T23:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-02T23:30:00.500Z",
        "String 2024-01-02 23:30:00.500 -0500",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
        "plain 2024-01-02 05:30:00.0 @ 2024-01-02T05:30:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T05:30:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "plain 2024-01-02 04:41:12.345678901 @ 2024-01-02T04:41:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T04:41:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T10:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T10:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        "plain 2024-07-02 06:30:00.0 @ 2024-07-02T05:30:00Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T05:30:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "plain 2024-07-02 05:41:12.0 @ 2024-07-02T04:41:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T04:41:12Z",
        "zoned 10:11:12 @ 2024-07-02T10:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T10:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T10:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        "plain 2024-01-02 05:30:00.0 @ 2024-01-02T05:30:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T05:30:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "plain 2024-01-02 18:00:00.5 @ 2024-01-02T18:00:00.500Z nanos 500000000",
        "plain 2024-01-02 @ 2024-01-02T18:00:00.500Z",
        "zoned 23:30:00 @ 2024-01-02T23:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-02T23:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-02T23:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
        null,
        "plain 2024-01-01 @ 2024-01-01T15:00:00Z",
        "plain 2024-01-02 00:00:00.0 @ 2024-01-02T00:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "String 10:11:12",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "String 2024-01-02 10:11:12.345",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 10:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T01:11:12.345Z",
        "String 2024-01-02 19:11:12.345 +0900",
        "zoned 2024-01-02 19:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 19:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 19:11:12 @ 2024-01-02T10:11:12.345Z",
        "String 2024-01-02 10:11:12.345 +0100",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        null,
        "plain 2024-07-01 @ 2024-07-01T16:00:00Z",
        "plain 2024-07-02 00:00:00.0 @ 2024-07-01T23:00:00Z nanos 0",
        "plain 2024-07-02 @ 2024-07-01T23:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "String 10:11:12",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "String 2024-07-02 10:11:12.000",
        "plain 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "plain 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T01:11:12Z",
        "String 2024-07-02 19:11:12.000 +0900",
        "zoned 2024-07-02 19:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "zoned 2024-07-02 19:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T10:11:12Z",
        "zoned 19:11:12 @ 2024-07-02T10:11:12Z",
        "String 2024-07-02 10:11:12.000 +0100",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        null,
        "plain 2024-01-01 @ 2024-01-01T15:00:00Z",
        "plain 2024-01-02 00:00:00.0 @ 2024-01-02T00:00:00Z nanos 0",
        "plain 2024-01-02 @ 2024-01-02T00:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "String 23:30:00",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "String 2024-01-02 23:30:00.500",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 23:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "plain 2024-01-02 @ 2024-01-02T23:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-02T14:30:00.500Z",
        "String 2024-01-03 08:30:00.500 +0900",
        "zoned 2024-01-03 08:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "zoned 2024-01-03 08:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "zoned 2024-01-03 @ 2024-01-02T23:30:00.500Z",
        "zoned 08:30:00 @ 2024-01-02T23:30:00.500Z",
        "String 2024-01-02 23:30:00.500 -0500",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
        "plain 2024-01-01 10:00:00.0 @ 2024-01-01T10:00:00Z nanos 0",
        "plain 2024-01-01 @ 2024-01-01T10:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 10:11:12.345678901 @ 1970-01-01T10:11:12.345Z nanos 345678901",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.345678901.",
        "plain 10:11:12 @ 1970-01-01T09:11:12.345Z",
        "plain 2024-01-02 15:11:12.345678901 @ 2024-01-02T15:11:12.345Z nanos 345678901",
        "plain 2024-01-02 @ 2024-01-02T15:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T01:11:12.345Z",
        "zoned 2024-01-02 19:11:12.345678901 @ 2024-01-02T10:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T10:11:12.345Z",
        "zoned 19:11:12 @ 2024-01-02T10:11:12.345Z",
        "zoned 2024-01-02 10:11:12.345678901 @ 2024-01-02T09:11:12.345Z nanos 345678901",
        "zoned 2024-01-02 @ 2024-01-02T09:11:12.345Z",
        "zoned 10:11:12 @ 2024-01-02T09:11:12.345Z",
        "plain 2024-07-01 12:00:00.0 @ 2024-07-01T11:00:00Z nanos 0",
        "plain 2024-07-01 @ 2024-07-01T11:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19906.",
        "zoned 1970-01-01 10:11:12.0 @ 1970-01-01T10:11:12Z nanos 0",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=36672.000000000.",
        "plain 10:11:12 @ 1970-01-01T09:11:12Z",
        "plain 2024-07-02 15:11:12.0 @ 2024-07-02T14:11:12Z nanos 0",
        "plain 2024-07-02 @ 2024-07-02T14:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T01:11:12Z",
        "zoned 2024-07-02 19:11:12.0 @ 2024-07-02T10:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T10:11:12Z",
        "zoned 19:11:12 @ 2024-07-02T10:11:12Z",
        "zoned 2024-07-02 10:11:12.0 @ 2024-07-02T09:11:12Z nanos 0",
        "zoned 2024-07-02 @ 2024-07-02T09:11:12Z",
        "zoned 10:11:12 @ 2024-07-02T09:11:12Z",
        "plain 2024-01-01 10:00:00.0 @ 2024-01-01T10:00:00Z nanos 0",
        "plain 2024-01-01 @ 2024-01-01T10:00:00Z",
        "REFUSED Cannot convert value in the driver from type:91 to type:time, value=19724.",
        "zoned 1970-01-01 23:30:00.5 @ 1970-01-01T23:30:00.500Z nanos 500000000",
        "REFUSED Cannot convert value in the driver from type:92 to type:date, value=84600.500000000.",
        "plain 23:30:00 @ 1970-01-01T22:30:00.500Z",
        "plain 2024-01-03 04:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "plain 2024-01-03 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-02T14:30:00.500Z",
        "zoned 2024-01-03 08:30:00.5 @ 2024-01-02T23:30:00.500Z nanos 500000000",
        "zoned 2024-01-03 @ 2024-01-02T23:30:00.500Z",
        "zoned 08:30:00 @ 2024-01-02T23:30:00.500Z",
        "zoned 2024-01-02 23:30:00.5 @ 2024-01-03T04:30:00.500Z nanos 500000000",
        "zoned 2024-01-02 @ 2024-01-03T04:30:00.500Z",
        "zoned 23:30:00 @ 2024-01-03T04:30:00.500Z",
    };
}
