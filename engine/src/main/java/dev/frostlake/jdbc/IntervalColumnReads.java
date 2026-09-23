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

import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.types.DataType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Period;

/**
 * How both transports read an interval column: cell for cell as Snowflake's JDBC driver reads one with its
 * default ARROW results (live-verified for every getter over DAY, HOUR, MINUTE, SECOND, a TIMESTAMP
 * difference, YEAR and MONTH, zero, negative and NULL cells). Every rule turns on the column's
 * {@link IntervalKind}, and every cell arrives here as its count of nanoseconds or months, null for SQL NULL.
 *
 * <p>A WIDE day-time interval (sixteen bytes: DAY, HOUR, MINUTE, a TIMESTAMP difference) is a fixed-point
 * count of nanoseconds:
 * <ul>
 *   <li>{@code getString} is its digits, {@code getObject()} a BigDecimal of them;</li>
 *   <li>{@code getBigDecimal}, {@code getDouble} and {@code getFloat} read the count, and {@code getLong},
 *       {@code getInt}, {@code getShort} and {@code getByte} read it while it fits — otherwise "Cannot convert
 *       value in the driver from type:FIXED(null,null) to type:Int, value=86400000000000.";</li>
 *   <li>{@code getBoolean} reads 0 and 1 and refuses any other count; {@code getBytes} is the count's
 *       two's-complement bytes; {@code getObject(i, Duration.class)} is a Duration of the count.</li>
 * </ul>
 *
 * <p>A NARROW day-time interval (eight bytes: SECOND(9,9), or a leading precision that fits them, DAY(2)) is a
 * Duration, and a year-month interval a Period of its months, normalized ({@code P1Y2M} for fourteen):
 * {@code getString} is that value's ISO text,
 * {@code getObject()} the value, and every numeric, boolean, byte and temporal getter refuses — "Cannot
 * convert value in the driver from type:INTERVAL_DAY_TIME to type:long, value=." ({@code value={2}.} for
 * int, the driver's own unfilled placeholder).
 *
 * <p>The temporal getters refuse every kind, a NULL cell included. On a NULL cell the rest answer null or
 * zero, except that a NARROW or year-month column refuses {@code getByte} and {@code getBoolean} even then.
 * {@code getObject(i, type)} reads through the matching getter; a Duration or Period asked of the other
 * kind, and a byte[] or Object asked of any, is "Type passed to 'getObject(int columnIndex,Class&lt;T&gt; type)'
 * is unsupported. Type: …". Refusals carry SQLSTATE 0A000 and code 200038, the unsupported type none.
 */
final class IntervalColumnReads {

    /** The code the driver attaches to a failed conversion. */
    private static final int CANNOT_CONVERT = 200038;

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);

    private IntervalColumnReads() {
    }

    /**
     * The kind of an in-process column: its declared interval type decides, and for a column the engine
     * could not type, the cell's own qualifier.
     *
     * @param declared the column's declared type, or null
     * @param cell     the current cell, or null
     * @return the kind, or null for a column that holds no interval
     */
    static IntervalKind kindOf(final DataType declared, final Object cell) {
        if (declared instanceof IntervalDayTimeType) {
            // The declared precision decides the width: an INTERVAL DAY(2) is eight bytes and reads as a Duration,
            // PT24H, where an INTERVAL DAY(9) is sixteen and reads as its count (live-verified).
            return "SB8".equals(((IntervalDayTimeType) declared).storageTag()) ? IntervalKind.NARROW : IntervalKind.WIDE;
        }
        if (declared instanceof IntervalYearMonthType) {
            return IntervalKind.YEAR_MONTH;
        }
        final IntervalQualifier fromCell = cell == null ? null : IntervalCells.qualifierOf(cell);
        return fromCell == null ? null : kindOf(fromCell);
    }

    /**
     * The kind of a column read off the HTTP wire: its family name and the scale that codes its fields.
     *
     * @param wireTypeName the column's {@code dataType}
     * @param scale        the column's scale
     * @return the kind, or null for a column that holds no interval
     */
    static IntervalKind kindOf(final String wireTypeName, final int scale) {
        if ("INTERVAL_YEAR_MONTH".equalsIgnoreCase(wireTypeName)) {
            return IntervalKind.YEAR_MONTH;
        }
        return "INTERVAL_DAY_TIME".equalsIgnoreCase(wireTypeName)
            ? kindOf(IntervalQualifier.ofDriverScale(true, scale)) : null;
    }

    private static IntervalKind kindOf(final IntervalQualifier qualifier) {
        if (!qualifier.isDayTime()) {
            return IntervalKind.YEAR_MONTH;
        }
        return "SB8".equals(qualifier.storageTag()) ? IntervalKind.NARROW : IntervalKind.WIDE;
    }

    static String getString(final IntervalKind kind, final BigInteger number) {
        if (number == null) {
            return null;
        }
        switch (kind) {
            case NARROW:
                return duration(number).toString();
            case YEAR_MONTH:
                return period(number).toString();
            default:
                return number.toString();
        }
    }

    static Object getObject(final IntervalKind kind, final BigInteger number) {
        if (number == null) {
            return null;
        }
        switch (kind) {
            case NARROW:
                return duration(number);
            case YEAR_MONTH:
                return period(number);
            default:
                return new BigDecimal(number);
        }
    }

    static long getLong(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return 0L;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "long", "");
        }
        if (number.bitLength() >= Long.SIZE) {
            throw refused(kind, "Long", number.toString());
        }
        return number.longValue();
    }

    static int getInt(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return 0;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "int", "{2}");
        }
        if (number.bitLength() >= Integer.SIZE) {
            throw refused(kind, "Int", number.toString());
        }
        return number.intValue();
    }

    static short getShort(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return 0;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "short", "");
        }
        if (number.bitLength() >= Short.SIZE) {
            throw refused(kind, "Short", number.toString());
        }
        return number.shortValue();
    }

    static byte getByte(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (kind != IntervalKind.WIDE) {
            // Refused even over NULL.
            throw refused(kind, "byte", "");
        }
        if (number == null) {
            return 0;
        }
        if (number.bitLength() >= Byte.SIZE) {
            throw refused(kind, "Byte", number.toString());
        }
        return number.byteValue();
    }

    static BigDecimal getBigDecimal(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return null;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "big decimal", "");
        }
        return new BigDecimal(number);
    }

    static double getDouble(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return 0d;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "double", "");
        }
        return number.doubleValue();
    }

    static float getFloat(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return 0f;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "float", "");
        }
        return number.floatValue();
    }

    static boolean getBoolean(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (kind != IntervalKind.WIDE) {
            // Refused even over NULL.
            throw refused(kind, "boolean", "");
        }
        if (number == null || number.signum() == 0) {
            return false;
        }
        if (BigInteger.ONE.equals(number)) {
            return true;
        }
        throw refused(kind, "Boolean", number.toString());
    }

    static byte[] getBytes(final IntervalKind kind, final BigInteger number) throws SQLException {
        if (number == null) {
            return null;
        }
        if (kind != IntervalKind.WIDE) {
            throw refused(kind, "byteArray", "");
        }
        return number.toByteArray();
    }

    /** Refused for every kind, a NULL cell included. */
    static Date getDate(final IntervalKind kind) throws SQLException {
        throw refused(kind, "date", "");
    }

    /** Refused for every kind, a NULL cell included. */
    static Time getTime(final IntervalKind kind) throws SQLException {
        throw refused(kind, "time", "");
    }

    /** Refused for every kind, a NULL cell included. */
    static Timestamp getTimestamp(final IntervalKind kind) throws SQLException {
        throw refused(kind, "timestamp", "");
    }

    /**
     * {@code getObject(i, type)}: the matching getter's answer, a Duration or Period of the kind that holds
     * one, and the driver's unsupported-type refusal for anything else.
     *
     * @param kind   the column's kind
     * @param number the cell's nanoseconds or months, null for SQL NULL
     * @param type   the class asked for
     * @param <T>    that class
     * @return the value as that class
     * @throws SQLException when the driver refuses the conversion
     */
    static <T> T getObject(final IntervalKind kind, final BigInteger number, final Class<T> type) throws SQLException {
        final Object value;
        if (type == String.class) {
            value = getString(kind, number);
        } else if (type == Integer.class) {
            value = Integer.valueOf(getInt(kind, number));
        } else if (type == Long.class) {
            value = Long.valueOf(getLong(kind, number));
        } else if (type == Short.class) {
            value = Short.valueOf(getShort(kind, number));
        } else if (type == Byte.class) {
            value = Byte.valueOf(getByte(kind, number));
        } else if (type == BigDecimal.class) {
            value = getBigDecimal(kind, number);
        } else if (type == Double.class) {
            value = Double.valueOf(getDouble(kind, number));
        } else if (type == Float.class) {
            value = Float.valueOf(getFloat(kind, number));
        } else if (type == Boolean.class) {
            value = Boolean.valueOf(getBoolean(kind, number));
        } else if (type == Timestamp.class) {
            value = getTimestamp(kind);
        } else if (type == Date.class) {
            value = getDate(kind);
        } else if (type == Time.class) {
            value = getTime(kind);
        } else if (type == Duration.class && (number == null || kind != IntervalKind.YEAR_MONTH)) {
            value = number == null ? null : duration(number);
        } else if (type == Period.class && (number == null || kind == IntervalKind.YEAR_MONTH)) {
            value = number == null ? null : period(number);
        } else {
            throw new SQLException("Type passed to 'getObject(int columnIndex,Class<T> type)' is unsupported. Type: "
                + type.getName());
        }
        return type.cast(value);
    }

    /** A day-time count of nanoseconds as a Duration, exact however large. */
    private static Duration duration(final BigInteger nanos) {
        final BigInteger[] split = nanos.divideAndRemainder(NANOS_PER_SECOND);
        return Duration.ofSeconds(split[0].longValueExact(), split[1].longValue());
    }

    /** A year-month count of months as a Period, normalized to years and months. */
    private static Period period(final BigInteger months) {
        return Period.ofMonths(months.intValueExact()).normalized();
    }

    /** The driver's conversion refusal for this kind, target type and value text. */
    private static SQLException refused(final IntervalKind kind, final String target, final String value) {
        return new SQLException("Cannot convert value in the driver from type:" + kind.driverTypeText() + " to type:"
            + target + ", value=" + value + ".", "0A000", CANNOT_CONVERT);
    }
}
