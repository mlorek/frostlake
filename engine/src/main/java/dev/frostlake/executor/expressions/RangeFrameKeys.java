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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.types.DecimalText;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * The ORDER BY keys of a window frame written RANGE with an offset: a number's key moves by a number, a date's by an
 * interval or a number of days, a timestamp's by an interval — each as {@code key + offset} and {@code key - offset}
 * compute in SQL — and two keys are compared on one line, a date standing for its midnight (live-verified).
 */
public final class RangeFrameKeys {

    private RangeFrameKeys() {
    }

    /** Whether a key is a number's. */
    public static boolean isNumberKey(final Object key) {
        return key instanceof Number;
    }

    /** Whether a key is a date's. */
    public static boolean isDateKey(final Object key) {
        return key instanceof LocalDate;
    }

    /** Whether a key is a timestamp's, of any of the three kinds. */
    public static boolean isTimestampKey(final Object key) {
        return key instanceof LocalDateTime || key instanceof OffsetDateTime || key instanceof ZonedDateTime
            || key instanceof Instant;
    }

    /** Whether an offset is an interval rather than a number. */
    public static boolean isInterval(final Object offset) {
        return offset != null && !(offset instanceof Number);
    }

    /**
     * Whether an interval offset moves a key backwards, which no frame takes.
     *
     * @param offset the evaluated interval
     * @return whether it is negative
     */
    public static boolean isNegative(final Object offset) {
        final LocalDateTime base = LocalDateTime.of(2000, 1, 1, 0, 0);
        return compare(ExpressionArithmetic.add(base, offset), base) < 0;
    }

    /**
     * A key moved by an offset, as SQL's {@code +} and {@code -} move it.
     *
     * @param key    the current row's key
     * @param offset the evaluated offset
     * @param sign   1 to move forwards, -1 backwards
     * @return the moved key
     */
    public static Object shift(final Object key, final Object offset, final int sign) {
        if (key instanceof Number && offset instanceof Number) {
            final BigDecimal from = decimal((Number) key);
            final BigDecimal moved = decimal((Number) offset);
            if (from == null || moved == null) {
                // A FLOAT key of NaN or an infinity stays where it is.
                return ((Number) key).doubleValue() + sign * ((Number) offset).doubleValue();
            }
            return sign > 0 ? from.add(moved) : from.subtract(moved);
        }
        return sign > 0 ? ExpressionArithmetic.add(key, offset) : ExpressionArithmetic.subtract(key, offset);
    }

    /**
     * Two keys compared on one line: numbers by value, a date as its midnight beside a timestamp, zoned timestamps
     * by their instants.
     *
     * @param left  one key
     * @param right the other
     * @return negative, zero or positive as {@code left} sorts before, with or after {@code right}
     */
    public static int compare(final Object left, final Object right) {
        if (left instanceof Number && right instanceof Number) {
            final BigDecimal l = decimal((Number) left);
            final BigDecimal r = decimal((Number) right);
            return l != null && r != null ? l.compareTo(r)
                : Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        final Object l = onTheLine(left);
        final Object r = onTheLine(right);
        if (l instanceof LocalDateTime && r instanceof LocalDateTime) {
            return ((LocalDateTime) l).compareTo((LocalDateTime) r);
        }
        if (l instanceof Instant && r instanceof Instant) {
            return ((Instant) l).compareTo((Instant) r);
        }
        return ValueComparisons.compareValues(left, right);
    }

    /** A number's exact value, or null for a FLOAT's NaN or infinity, which has none. */
    private static BigDecimal decimal(final Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue())) {
            return null;
        }
        return DecimalText.parse(number.toString());
    }

    /** A temporal key as a point it compares by: a date at its midnight, a zoned timestamp as its instant. */
    private static Object onTheLine(final Object key) {
        if (key instanceof LocalDate) {
            return ((LocalDate) key).atStartOfDay();
        }
        if (key instanceof OffsetDateTime) {
            return ((OffsetDateTime) key).toInstant();
        }
        if (key instanceof ZonedDateTime) {
            return ((ZonedDateTime) key).toInstant();
        }
        return key;
    }
}
