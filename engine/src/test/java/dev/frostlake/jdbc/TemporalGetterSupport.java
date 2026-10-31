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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Reads temporal and other cells with getDate, getTime and getTimestamp and records what each answers, as the
 * account's driver answers them (live-verified), shared by the in-process and the HTTP driver's tests: a DATE is
 * read by getDate and getTimestamp, a TIME by getTime and getTimestamp (on 1970-01-01, its milliseconds kept), a
 * timestamp of every flavour by all three, and every other pairing is refused with SQLSTATE 0A000, code 200038, and
 * the cell as the account's wire carries it.
 */
final class TemporalGetterSupport {

    private TemporalGetterSupport() {
    }

    /** The cells of every temporal type, each with its own name. */
    static final String TEMPORAL = """
        SELECT '2024-01-02'::DATE AS d, '10:11:12.345'::TIME AS tm, '2024-01-02 10:11:12.345'::TIMESTAMP_NTZ AS ntz,
               '2024-01-02 10:11:12.345'::TIMESTAMP_LTZ AS ltz, '2024-01-02 10:11:12.345 +01:00'::TIMESTAMP_TZ AS tz""";

    /** Cells no temporal getter reads, and the wire forms the refusals quote; a FLOAT's are FloatColumnChecks'. */
    static final String OTHER = """
        SELECT '2024-01-02'::VARCHAR AS s, 5::NUMBER AS n, TRUE AS b,
               TO_VARIANT('2024-01-02'::DATE) AS v, '1969-12-31'::DATE AS early, '10:11:12'::TIME(0) AS t0,
               '10:11:12.5'::TIME(3) AS t3, '00:00:00'::TIME AS midnight""";

    /** NULL cells, which every getter reads as null whatever the column's type. */
    static final String NULLS = "SELECT NULL::DATE AS dn, NULL::TIME AS tn, NULL::VARCHAR AS sn";

    /**
     * Every column of a query read by each of the three getters.
     *
     * @param connection the connection
     * @param sql        the query, of one row
     * @return one line per column and getter: the getter's answer or its refusal
     * @throws SQLException when the query fails
     */
    static List<String> readAll(final Connection connection, final String sql) throws SQLException {
        final List<String> seen = new ArrayList<String>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            final String[] getters = {"getDate", "getTime", "getTimestamp"};
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                final boolean time = rs.getMetaData().getColumnType(i) == Types.TIME;
                for (final String getter : getters) {
                    seen.add(rs.getMetaData().getColumnLabel(i).toUpperCase(Locale.ROOT) + "." + getter + " "
                        + read(rs, i, getter, time));
                }
            }
        }
        return seen;
    }

    /**
     * The Calendar overloads and the typed getObject over a TIME and a DATE: they read and refuse as the plain getters
     * do, and a TIME read as a Timestamp ignores the Calendar.
     *
     * @param connection the connection
     * @return one line per read
     * @throws SQLException when the query fails
     */
    static List<String> readOverloads(final Connection connection) throws SQLException {
        final List<String> seen = new ArrayList<String>();
        final Calendar tokyo = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"));
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT '10:11:12.345'::TIME AS tm, '2024-01-02'::DATE AS d")) {
            rs.next();
            seen.add("TM.getDate(cal) " + answer(rs, false, new CellRead() {
                @Override
                public Object of(final ResultSet row) throws SQLException {
                    return row.getDate(1, tokyo);
                }
            }));
            seen.add("TM.getTimestamp(cal) " + answer(rs, false, new CellRead() {
                @Override
                public Object of(final ResultSet row) throws SQLException {
                    return row.getTimestamp(1, tokyo);
                }
            }));
            seen.add("D.getTime(cal) " + answer(rs, false, new CellRead() {
                @Override
                public Object of(final ResultSet row) throws SQLException {
                    return row.getTime(2, tokyo);
                }
            }));
            seen.add("D.getObject(Time) " + answer(rs, false, new CellRead() {
                @Override
                public Object of(final ResultSet row) throws SQLException {
                    return row.getObject(2, Time.class);
                }
            }));
            seen.add("TM.getObject(Timestamp) " + answer(rs, false, new CellRead() {
                @Override
                public Object of(final ResultSet row) throws SQLException {
                    return row.getObject(1, Timestamp.class);
                }
            }));
        }
        return seen;
    }

    private static String read(final ResultSet rs, final int column, final String getter, final boolean time) {
        return answer(rs, time, new CellRead() {
            @Override
            public Object of(final ResultSet row) throws SQLException {
                if ("getDate".equals(getter)) {
                    return row.getDate(column);
                }
                if ("getTime".equals(getter)) {
                    return row.getTime(column);
                }
                return row.getTimestamp(column);
            }
        });
    }

    /**
     * A read's answer: its value's text — a TIME column's with the moment it names on 1970-01-01, milliseconds and
     * all — and whether the cell was NULL, or the refusal's SQLSTATE, code and message.
     */
    private static String answer(final ResultSet rs, final boolean time, final CellRead read) {
        try {
            final Object value = read.of(rs);
            final String moment = time && value instanceof Time ? " at " + new Timestamp(((Time) value).getTime()) : "";
            return value + moment + (rs.wasNull() ? " (null)" : "");
        } catch (final SQLException refused) {
            return "REFUSED " + refused.getSQLState() + " " + refused.getErrorCode() + " " + refused.getMessage();
        }
    }
}
