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

import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a FLOAT column reads as through each typed getter, as the account's driver reads it on its default results
 * (see {@link FloatColumnReads}), written once and run over the in-process driver and the account's own
 * (FloatColumnReadsTest) and over the HTTP driver (FloatHttpTransportTest). The connection is on the default result
 * format (see {@link DefaultResultFormat}).
 */
final class FloatColumnChecks {

    private FloatColumnChecks() {
    }

    /** The first row of a query, the result left open for the check to read. */
    private static ResultSet firstRow(final Connection c, final String sql) throws SQLException {
        final ResultSet rs = c.createStatement().executeQuery(sql);
        assertTrue(rs.next(), sql + " has a row");
        return rs;
    }

    /** The driver's refusal of a FLOAT cell, quoting the value it is given. */
    private static void refused(final String target, final String value, final Executable read) {
        final SQLException refusal = assertThrows(SQLException.class, read);
        assertEquals("Cannot convert value in the driver from type:REAL to type:" + target + ", value=" + value + ".",
            refusal.getMessage());
        assertEquals("0A000", refusal.getSQLState());
        assertEquals(200038, refusal.getErrorCode());
    }

    /**
     * getLong, getInt and getShort narrow the double as Java narrows it: the fraction cut toward zero, NaN read as 0, a
     * value past a long or an int pinned to that type's end, and a short the low sixteen bits of the int.
     *
     * @param c the connection
     */
    static void integerReads(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, """
            SELECT 2.5::FLOAT AS F, -2.5::FLOAT AS NEG, 40000::FLOAT AS MID, 3e9::FLOAT AS BIG, 1e19::FLOAT AS HUGE,
                   'NaN'::FLOAT AS NAN, '-inf'::FLOAT AS NEGINF, NULL::FLOAT AS N""");
        assertEquals(2L, rs.getLong("F"));
        assertEquals(2, rs.getInt("F"));
        assertEquals((short) 2, rs.getShort("F"));
        assertEquals(Integer.valueOf(2), rs.getObject("F", Integer.class));
        assertEquals(-2, rs.getInt("NEG"), "cut toward zero");
        assertEquals(40000, rs.getInt("MID"));
        assertEquals((short) -25536, rs.getShort("MID"), "the int's low sixteen bits");
        assertEquals(3000000000L, rs.getLong("BIG"));
        assertEquals(Integer.MAX_VALUE, rs.getInt("BIG"), "past an int, pinned to its end");
        assertEquals((short) -1, rs.getShort("BIG"));
        assertEquals(Long.MAX_VALUE, rs.getLong("HUGE"));
        assertEquals(0L, rs.getLong("NAN"));
        assertEquals(0, rs.getInt("NAN"));
        assertEquals(Long.MIN_VALUE, rs.getLong("NEGINF"));
        assertEquals(Integer.MIN_VALUE, rs.getInt("NEGINF"));
        assertEquals((short) 0, rs.getShort("NEGINF"));
        assertEquals(0, rs.getInt("N"));
        assertTrue(rs.wasNull());
    }

    /**
     * getBigDecimal is the decimal of the double's text, scale and all; NaN and the infinities spell no decimal.
     *
     * @param c    the connection
     * @param live whether it is the account's driver, which lets BigDecimal's own NumberFormatException escape there
     */
    static void decimalReads(final Connection c, final boolean live) throws SQLException {
        final ResultSet rs = firstRow(c, """
            SELECT 0.1::FLOAT + 0.2::FLOAT AS SUM, 0.0001::FLOAT AS SMALL, 1e18::FLOAT AS BIG, 3::FLOAT AS WHOLE,
                   -0.0::FLOAT AS NEGZERO, 'NaN'::FLOAT AS NAN, NULL::FLOAT AS N""");
        assertEquals(new BigDecimal("0.30000000000000004"), rs.getBigDecimal("SUM"));
        assertEquals(new BigDecimal("0.00010"), rs.getBigDecimal("SMALL"), "the digits of 1.0E-4");
        assertEquals(new BigDecimal("1.0E+18"), rs.getBigDecimal("BIG"));
        assertEquals(new BigDecimal("3.0"), rs.getBigDecimal("WHOLE"));
        assertEquals(new BigDecimal("0.0"), rs.getBigDecimal("NEGZERO"));
        assertNull(rs.getBigDecimal("N"));
        if (!live) {
            refused("big decimal", "NaN", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getBigDecimal("NAN");
                }
            });
        }
    }

    /**
     * getBoolean refuses every value, 0 and 1 included, quoting it as the driver's message formats a number; a NULL
     * reads false.
     *
     * @param c the connection
     */
    static void booleanReads(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, """
            SELECT 1::FLOAT AS ONE, 0::FLOAT AS ZERO, 1e18::FLOAT AS BIG, SQRT(2) AS ROOT, 'inf'::FLOAT AS INF,
                   NULL::FLOAT AS N""");
        final String[][] quoted = {
            {"ONE", "1"}, {"ZERO", "0"}, {"BIG", "1,000,000,000,000,000,000"}, {"ROOT", "1.414"}, {"INF", "∞"}};
        for (final String[] cell : quoted) {
            refused("boolean", cell[1], new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getBoolean(cell[0]);
                }
            });
        }
        assertFalse(rs.getBoolean("N"));
        assertTrue(rs.wasNull());
    }

    /**
     * getByte and the temporal getters refuse every cell, a NULL one included, quoting nothing.
     *
     * @param c the connection
     */
    static void refusedReads(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, "SELECT 1.5::FLOAT AS F, NULL::FLOAT AS N");
        for (final String label : new String[] {"F", "N"}) {
            refused("byte", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getByte(label);
                }
            });
            refused("byte", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getObject(label, Byte.class);
                }
            });
            refused("date", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getDate(label);
                }
            });
            refused("date", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getDate(label, Calendar.getInstance());
                }
            });
            refused("time", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getTime(label);
                }
            });
            refused("timestamp", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getTimestamp(label);
                }
            });
            refused("timestamp", "", new Executable() {
                @Override
                public void execute() throws Throwable {
                    rs.getTimestamp(label, Calendar.getInstance());
                }
            });
        }
    }

    /**
     * getDouble, getFloat, getBytes and getObject read the double itself.
     *
     * @param c the connection
     */
    static void doubleReads(final Connection c) throws SQLException {
        final ResultSet rs = firstRow(c, "SELECT 0.1::FLOAT + 0.2::FLOAT AS SUM, 1e18::FLOAT AS BIG, NULL::FLOAT AS N");
        assertEquals(0.30000000000000004, rs.getDouble("SUM"));
        assertEquals(Double.valueOf(0.30000000000000004), rs.getObject("SUM"));
        assertEquals(0.3f, rs.getFloat("SUM"));
        assertEquals(9.9999998E17f, rs.getFloat("BIG"), "the float nearest the double");
        assertArrayEquals(new byte[] {0x3F, (byte) 0xD3, 0x33, 0x33, 0x33, 0x33, 0x33, 0x34}, rs.getBytes("SUM"));
        assertEquals(0.0, rs.getDouble("N"));
        assertNull(rs.getObject("N"));
        assertNull(rs.getBytes("N"));
    }
}
