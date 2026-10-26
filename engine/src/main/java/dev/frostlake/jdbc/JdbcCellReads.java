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

import dev.frostlake.types.DecimalText;
import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Types;

/**
 * How the driver reads a cell through a typed getter, as the account's driver reads one: from the cell's wire text —
 * a number's digits, a BOOLEAN's 1 or 0, a DATE's day number (see {@link JdbcMarshaling#quotedCell}) — judged by the
 * column's JDBC type (live-verified).
 *
 * <ul>
 *   <li>{@code getBoolean} reads TRUE and FALSE, and the texts {@code 1}, {@code 0}, {@code true} and {@code false}
 *       of an integer, a fixed-point number, a BOOLEAN or a text; anything else is refused.</li>
 *   <li>{@code getShort}, {@code getInt} and {@code getLong} parse the text, a FLOAT's cut at its point: 2.5 reads 2,
 *       while a fixed-point number with a fraction (3.50, 3.00), a number past the getter's type and a text that is
 *       no integer are refused. {@code getByte} parses the text as it stands.</li>
 *   <li>{@code getFloat}, {@code getDouble} and {@code getBigDecimal} read a number as itself and parse any other
 *       text ({@code inf} and {@code -inf} included); a time or a timestamp is refused.</li>
 * </ul>
 * A refusal is {@code Cannot convert value in the driver from type:<jdbc type> to type:<getter>, value=<text>.},
 * SQLSTATE 0A000 and code 200038. Where the account's driver lets a parser's own exception escape — {@code getByte}
 * over 300 or 3.50 — the same refusal is raised instead.
 */
final class JdbcCellReads {

    private JdbcCellReads() {
    }

    /**
     * A cell read by {@code getBoolean}.
     *
     * @param type the column's JDBC type
     * @param cell the cell, not null
     * @param wire the cell's wire text
     * @return the boolean it reads as
     * @throws SQLException when the cell reads as no boolean
     */
    static boolean booleanOf(final int type, final Object cell, final String wire) throws SQLException {
        if (cell instanceof Boolean) {
            return ((Boolean) cell).booleanValue();
        }
        if (readsBoolean(type)) {
            if ("1".equals(wire) || "true".equalsIgnoreCase(wire)) {
                return true;
            }
            if ("0".equals(wire) || "false".equalsIgnoreCase(wire)) {
                return false;
            }
        }
        throw JdbcMarshaling.unconvertible(type, "boolean", wire);
    }

    /** Whether the account's driver reads a boolean out of a column of the type's text. */
    private static boolean readsBoolean(final int type) {
        switch (type) {
            case Types.BOOLEAN:
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
            case Types.BIGINT:
            case Types.BIT:
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.DECIMAL:
                return true;
            default:
                return false;
        }
    }

    /**
     * A cell read by {@code getByte}: its text parsed as it stands.
     *
     * @param type the column's JDBC type
     * @param wire the cell's wire text
     * @return the byte
     * @throws SQLException when the text is no byte
     */
    static byte byteOf(final int type, final String wire) throws SQLException {
        try {
            return Byte.parseByte(wire);
        } catch (final NumberFormatException notAByte) {
            throw JdbcMarshaling.unconvertible(type, "byte", wire);
        }
    }

    /**
     * A cell read by {@code getShort}.
     *
     * @param type the column's JDBC type
     * @param wire the cell's wire text
     * @return the short
     * @throws SQLException when the text is no short
     */
    static short shortOf(final int type, final String wire) throws SQLException {
        try {
            return Short.parseShort(integerText(type, wire));
        } catch (final NumberFormatException notAShort) {
            throw JdbcMarshaling.unconvertible(type, "short", wire);
        }
    }

    /**
     * A cell read by {@code getInt}.
     *
     * @param type     the column's JDBC type
     * @param typeName the column's type name
     * @param cell     the cell, not null
     * @param wire     the cell's wire text
     * @return the int
     * @throws SQLException when the cell is no int
     */
    static int intOf(final int type, final String typeName, final Object cell, final String wire)
            throws SQLException {
        if ("DECFLOAT".equalsIgnoreCase(typeName)) {
            return decfloatWhole(cell, wire, "int").intValue();
        }
        try {
            return Integer.parseInt(integerText(type, wire));
        } catch (final NumberFormatException notAnInt) {
            throw JdbcMarshaling.unconvertible(type, "int", wire);
        }
    }

    /**
     * A cell read by {@code getLong}.
     *
     * @param type     the column's JDBC type
     * @param typeName the column's type name
     * @param cell     the cell, not null
     * @param wire     the cell's wire text
     * @return the long
     * @throws SQLException when the cell is no long
     */
    static long longOf(final int type, final String typeName, final Object cell, final String wire)
            throws SQLException {
        if ("DECFLOAT".equalsIgnoreCase(typeName)) {
            return decfloatWhole(cell, wire, "long").longValue();
        }
        try {
            return Long.parseLong(integerText(type, wire));
        } catch (final NumberFormatException notALong) {
            throw JdbcMarshaling.unconvertible(type, "long", wire);
        }
    }

    /** The text an integer getter parses: a FLOAT's cut at its point, any other text as it stands. */
    private static String integerText(final int type, final String wire) {
        final int point = wire.indexOf('.');
        return point >= 0 && (type == Types.FLOAT || type == Types.DOUBLE) ? wire.substring(0, point) : wire;
    }

    /**
     * A DECFLOAT read by an integer getter: a whole number in the getter's range reads, and a fraction or a number
     * past the range is refused naming the DECFLOAT by its text, {@code Cannot convert value in the driver from
     * type:3 to type:long, value=3.3333333333333333333333333333333333333e-1.} (live-verified).
     */
    private static BigDecimal decfloatWhole(final Object cell, final String wire, final String target)
            throws SQLException {
        final BigDecimal held = cell instanceof Number ? Decfloats.of((Number) cell, "TO_DECFLOAT")
            : Decfloats.held(DecimalText.parse(wire.trim()));
        final boolean whole = held.signum() == 0 || held.stripTrailingZeros().scale() <= 0;
        final BigDecimal limit = BigDecimal.valueOf("int".equals(target) ? Integer.MAX_VALUE : Long.MAX_VALUE);
        final BigDecimal floor = BigDecimal.valueOf("int".equals(target) ? Integer.MIN_VALUE : Long.MIN_VALUE);
        if (!whole || held.compareTo(limit) > 0 || held.compareTo(floor) < 0) {
            throw JdbcMarshaling.unconvertible(Types.DECIMAL, target, Decfloats.text(held));
        }
        return held;
    }

    /**
     * A cell read by {@code getDouble}.
     *
     * @param type the column's JDBC type
     * @param cell the cell, not null
     * @param wire the cell's wire text
     * @return the double
     * @throws SQLException when the cell is a time or a timestamp, or a text that is no number
     */
    static double doubleOf(final int type, final Object cell, final String wire) throws SQLException {
        if (isClock(type)) {
            throw JdbcMarshaling.unconvertible(type, "double", wire);
        }
        if (cell instanceof Number) {
            return ((Number) cell).doubleValue();
        }
        if ("inf".equals(wire)) {
            return Double.POSITIVE_INFINITY;
        }
        if ("-inf".equals(wire)) {
            return Double.NEGATIVE_INFINITY;
        }
        try {
            return Double.parseDouble(wire);
        } catch (final NumberFormatException notADouble) {
            throw JdbcMarshaling.unconvertible(type, "double", wire);
        }
    }

    /**
     * A cell read by {@code getFloat}.
     *
     * @param type the column's JDBC type
     * @param cell the cell, not null
     * @param wire the cell's wire text
     * @return the float
     * @throws SQLException when the cell is a time or a timestamp, or a text that is no number
     */
    static float floatOf(final int type, final Object cell, final String wire) throws SQLException {
        if (isClock(type)) {
            throw JdbcMarshaling.unconvertible(type, "float", wire);
        }
        if (cell instanceof Number) {
            return ((Number) cell).floatValue();
        }
        if ("inf".equals(wire)) {
            return Float.POSITIVE_INFINITY;
        }
        if ("-inf".equals(wire)) {
            return Float.NEGATIVE_INFINITY;
        }
        try {
            return Float.parseFloat(wire);
        } catch (final NumberFormatException notAFloat) {
            throw JdbcMarshaling.unconvertible(type, "float", wire);
        }
    }

    /**
     * A cell read by {@code getBigDecimal}, a number that is not already a BigDecimal read by its text.
     *
     * @param type the column's JDBC type
     * @param cell the cell, not null
     * @param wire the cell's wire text
     * @return the decimal
     * @throws SQLException when the cell is a time or a timestamp, or a text that is no number
     */
    static BigDecimal decimalOf(final int type, final Object cell, final String wire) throws SQLException {
        if (isClock(type)) {
            throw JdbcMarshaling.unconvertible(type, "big decimal", wire);
        }
        if (cell instanceof BigDecimal) {
            return (BigDecimal) cell;
        }
        try {
            return cell instanceof Double || cell instanceof Float ? DecimalText.parse(cell.toString())
                : DecimalText.parse(wire);
        } catch (final NumberFormatException notADecimal) {
            throw JdbcMarshaling.unconvertible(type, "big decimal", wire);
        }
    }

    /**
     * A cell read by {@code getBytes}, as the account's driver reads one (live-verified): a BINARY's own bytes, a
     * BOOLEAN's one byte 1 or 0, a whole number's two's-complement bytes ({@code [1, 44]} for 300, {@code [-1]} for
     * -1), a FLOAT's eight IEEE 754 bytes, and the UTF-8 bytes of the text getString reads for a text, a VARIANT, an
     * OBJECT, an ARRAY or a VECTOR. A NUMBER with digits after its point, a DECFLOAT, a DATE, a TIME and every
     * timestamp are refused, quoting the cell's wire form.
     *
     * @param type the column's JDBC type
     * @param cell the cell, not null
     * @param text the cell's text, as getString reads it
     * @param wire the cell's wire text
     * @return the bytes
     * @throws SQLException when the column's type reads as no bytes
     */
    static byte[] bytesOf(final int type, final Object cell, final String text, final String wire)
            throws SQLException {
        switch (type) {
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return JdbcMarshaling.toBytes(cell);
            case Types.BOOLEAN:
                return new byte[] {(byte) (booleanOf(type, cell, wire) ? 1 : 0)};
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                try {
                    return new BigInteger(wire).toByteArray();
                } catch (final NumberFormatException notWhole) {
                    throw JdbcMarshaling.unconvertible(type, "byte array", wire);
                }
            case Types.REAL:
            case Types.FLOAT:
            case Types.DOUBLE:
                return ByteBuffer.allocate(Double.BYTES).putDouble(doubleOf(type, cell, wire)).array();
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                throw JdbcMarshaling.unconvertible(type, "byte array", wire);
            default:
                return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    /** Whether the type is a time or a timestamp, which no numeric getter reads. */
    private static boolean isClock(final int type) {
        return type == Types.TIME || type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE;
    }
}
