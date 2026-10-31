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

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Types;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * How both transports read a FLOAT column through a typed getter: cell for cell as Snowflake's JDBC driver reads
 * one with its default ARROW results, where every cell is the double itself (live-verified for every getter over
 * fractions, whole values, values past each integer type, the smallest subnormal, a negative zero, NaN, both
 * infinities and NULL). Every cell arrives here as a number, the double it is read as.
 *
 * <ul>
 *   <li>{@code getString} is Java's own text of the double (see
 *       {@link dev.frostlake.values.ClientValueText#approximateText}), {@code getObject()} and {@code getDouble}
 *       are the double, {@code getFloat} the float nearest it, and {@code getBytes} its eight IEEE 754 bytes;</li>
 *   <li>{@code getBigDecimal} is the decimal of its text, {@code 0.00010} for {@code 1.0E-4};</li>
 *   <li>{@code getLong}, {@code getInt} and {@code getShort} are Java's own narrowing of the double: the fraction
 *       cut toward zero, NaN read as 0, a value past a {@code long} or an {@code int} pinned to that type's end,
 *       and a {@code short} the low sixteen bits of the {@code int}, {@code -25536} for 40000;</li>
 *   <li>{@code getBoolean} refuses every value, 0 and 1 included, quoting it as the driver's message formats a
 *       number: grouped, to three decimals, {@code 1,000,000,000,000,000,000} and {@code 1.414};</li>
 *   <li>{@code getByte} and the temporal getters refuse every cell, a NULL one included, quoting nothing:
 *       "Cannot convert value in the driver from type:REAL to type:byte, value=.".</li>
 * </ul>
 *
 * <p>On a NULL cell the rest answer null, zero or false. Over NaN and the infinities the account's driver lets
 * {@code BigDecimal}'s own NumberFormatException escape {@code getBigDecimal}; this driver refuses them as it
 * refuses {@code getBoolean}. Refusals carry SQLSTATE 0A000 and code 200038. On JSON results the account's driver
 * reads the server's text of the value instead; this driver reads the default.
 */
final class FloatColumnReads {

    /** The code the driver attaches to a failed conversion. */
    private static final int CANNOT_CONVERT = 200038;

    private FloatColumnReads() {
    }

    /**
     * Whether a column of this JDBC type is a FLOAT, which reads by these rules.
     *
     * @param type the column's JDBC type
     * @return true for DOUBLE, FLOAT and REAL
     */
    static boolean isFloat(final int type) {
        return type == Types.DOUBLE || type == Types.FLOAT || type == Types.REAL;
    }

    static boolean getBoolean(final Object cell) throws SQLException {
        if (cell == null) {
            return false;
        }
        throw refused("boolean", quoted(doubleOf(cell)));
    }

    static byte getByte() throws SQLException {
        throw refused("byte", "");
    }

    static short getShort(final Object cell) {
        return cell == null ? 0 : (short) doubleOf(cell);
    }

    static int getInt(final Object cell) {
        return cell == null ? 0 : (int) doubleOf(cell);
    }

    static long getLong(final Object cell) {
        return cell == null ? 0L : (long) doubleOf(cell);
    }

    static BigDecimal getBigDecimal(final Object cell) throws SQLException {
        if (cell == null) {
            return null;
        }
        final double value = doubleOf(cell);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw refused("big decimal", quoted(value));
        }
        return BigDecimal.valueOf(value);
    }

    /**
     * The refusal of a temporal getter, which reads no FLOAT cell, a NULL one included.
     *
     * @param target the getter's name for its type: date, time or timestamp
     * @return the refusal
     */
    static SQLException temporalRefusal(final String target) {
        return refused(target, "");
    }

    private static double doubleOf(final Object cell) {
        return ((Number) cell).doubleValue();
    }

    /** A value as the driver's refusal quotes it: a number grouped by thousands, to three decimals. */
    private static String quoted(final double value) {
        return NumberFormat.getInstance(Locale.ROOT).format(value);
    }

    private static SQLException refused(final String target, final String value) {
        return new SQLException("Cannot convert value in the driver from type:REAL to type:" + target + ", value="
            + value + ".", "0A000", CANNOT_CONVERT);
    }
}
