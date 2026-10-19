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

package dev.frostlake.executor;

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * The refusal of a write that would give one target row two values: an UPDATE joining a row to several source
 * rows under ERROR_ON_NONDETERMINISTIC_UPDATE, or a MERGE updating one under ERROR_ON_NONDETERMINISTIC_MERGE.
 * The message is the account's two lines — the sentence, then the target row as it stood before the statement:
 *
 * <pre>
 *   Duplicate row detected during DML action
 *   Row Values: [1, "x", 19724, 1, 250, NULL]
 * </pre>
 *
 * <p>The row is printed as it is stored rather than as a query shows it (live-verified, kind by kind):
 *
 * <pre>
 *   a text                      "te"xt"            in double quotes, nothing escaped
 *   NUMBER(10,2) 2.50           250                its digits without the point
 *   FLOAT                       1.5   1e+06   nan  six significant digits, an exponent of two digits at least
 *   BOOLEAN                     1 or 0
 *   DATE                        19724              days since 1970
 *   TIME(3) 03:04:05.123        11045123           the time of day in units of its own scale
 *   TIMESTAMP_NTZ(3), _LTZ(3)   1704164645123      the instant since 1970 in units of the column's scale
 *   TIMESTAMP_TZ(3)             the same instant times 16384, plus the offset's minutes and 1440
 *   BINARY                      0x414243, and NO_VALUE for one of no bytes
 *   NULL                        NULL
 * </pre>
 *
 * <p>A VARIANT, an OBJECT and an ARRAY are stored as bytes the account prints raw between double quotes, which
 * no text reproduces: their value's own text stands between the quotes here.
 */
public final class DuplicateRowRefusal {

    /** The digits a time or a timestamp keeps when its column says nothing else. */
    private static final int NANOSECOND_SCALE = 9;
    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    /** What a zoned timestamp's instant is multiplied by to leave room for its offset. */
    private static final BigInteger ZONE_SLOTS = BigInteger.valueOf(16384);
    /** What an offset's minutes are raised by, so that the stored number is never negative. */
    private static final int ZONE_BIAS = 1440;
    /** The significant digits a FLOAT is printed with. */
    private static final int FLOAT_DIGITS = 6;

    private DuplicateRowRefusal() {
    }

    /**
     * The refusal naming one target row.
     *
     * @param target the table written to
     * @param row    the target row several source rows joined
     * @return the exception to throw
     */
    public static RuntimeException of(final Table target, final Row row) {
        return new RuntimeException("Duplicate row detected during DML action\nRow Values: " + values(target, row));
    }

    private static String values(final Table target, final Row row) {
        final List<TableColumn> columns = target.getColumns();
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < row.getValues().size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(stored(row.getValues().get(i), i < columns.size() ? columns.get(i).getDataType() : null));
        }
        return text.append(']').toString();
    }

    private static String stored(final Object value, final DataType type) {
        if (value == null) {
            return "NULL";
        }
        if (type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType) {
            return "\"" + value + "\"";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "1" : "0";
        }
        if (value instanceof LocalDate) {
            return String.valueOf(((LocalDate) value).toEpochDay());
        }
        if (value instanceof LocalTime) {
            return scaled(BigInteger.valueOf(((LocalTime) value).toNanoOfDay()), type).toString();
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime instant = (LocalDateTime) value;
            return scaled(nanos(instant.toEpochSecond(ZoneOffset.UTC), instant.getNano()), type).toString();
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime instant = (OffsetDateTime) value;
            return scaled(nanos(instant.toEpochSecond(), instant.getNano()), type).toString();
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime instant = (ZonedDateTime) value;
            return scaled(nanos(instant.toEpochSecond(), instant.getNano()), type).multiply(ZONE_SLOTS)
                .add(BigInteger.valueOf(instant.getOffset().getTotalSeconds() / 60 + ZONE_BIAS)).toString();
        }
        if (value instanceof BinaryValue) {
            final BinaryValue bytes = (BinaryValue) value;
            return bytes.length() == 0 ? "NO_VALUE" : "0x" + bytes.toHex().toLowerCase(Locale.ROOT);
        }
        if (value instanceof Double || value instanceof Float) {
            return floating(((Number) value).doubleValue());
        }
        if (value instanceof Number) {
            if (type instanceof NumericType && !NumericType.isApproximate(type)) {
                return new BigDecimal(value.toString()).movePointRight(((NumericType) type).getScale())
                    .toBigInteger().toString();
            }
            return String.valueOf(value);
        }
        if (type instanceof BooleanType) {
            return String.valueOf(value);
        }
        return "\"" + value + "\"";
    }

    private static BigInteger nanos(final long epochSecond, final int nanoOfSecond) {
        return BigInteger.valueOf(epochSecond).multiply(NANOS_PER_SECOND).add(BigInteger.valueOf(nanoOfSecond));
    }

    /** A count of nanoseconds in the units of a column's scale, the digits past it dropped toward the past. */
    private static BigInteger scaled(final BigInteger nanos, final DataType type) {
        final int scale = type instanceof DateTimeType
            ? Math.min(Math.max(((DateTimeType) type).getPrecision(), 0), NANOSECOND_SCALE) : NANOSECOND_SCALE;
        final BigInteger unit = BigInteger.TEN.pow(NANOSECOND_SCALE - scale);
        final BigInteger[] parts = nanos.divideAndRemainder(unit);
        return parts[1].signum() < 0 ? parts[0].subtract(BigInteger.ONE) : parts[0];
    }

    /**
     * A FLOAT in six significant digits, the shorter of its plain and its exponent form: {@code 123457},
     * {@code 0.0001}, {@code 1e+06}, {@code 1.5e-07}, with {@code nan}, {@code inf} and {@code -0} for the values
     * no digits tell.
     */
    private static String floating(final double value) {
        if (Double.isNaN(value)) {
            return "nan";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0) {
            return 1 / value < 0 ? "-0" : "0";
        }
        final BigDecimal rounded = new BigDecimal(value).round(new MathContext(FLOAT_DIGITS, RoundingMode.HALF_EVEN))
            .stripTrailingZeros();
        final int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent < -4 || exponent >= FLOAT_DIGITS) {
            final String digits = rounded.unscaledValue().abs().toString();
            final StringBuilder text = new StringBuilder(rounded.signum() < 0 ? "-" : "").append(digits.charAt(0));
            if (digits.length() > 1) {
                text.append('.').append(digits, 1, digits.length());
            }
            return text.append(exponent < 0 ? "e-" : "e+").append(Math.abs(exponent) < 10 ? "0" : "")
                .append(Math.abs(exponent)).toString();
        }
        return rounded.toPlainString();
    }
}
