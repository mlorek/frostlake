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

package dev.frostlake.values;

import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The ONE carrier an exact NUMBER(p, s) column holds, whatever path a value arrives by. A scale-0
 * column holds a {@code Long} for every whole value that fits one and a scale-0 {@code BigDecimal}
 * beyond the long range; a scaled column holds a {@code BigDecimal} at exactly its scale, rounded
 * HALF_UP from whatever was written. Before this, the same column held a Long from an INSERT of a
 * literal beside a BigDecimal from an UPDATE or a VARIANT cast, and every raw {@code equals} or
 * {@code Comparable} seam — a DISTINCT set, a join key, a MIN / MAX fast path — could meet one number
 * in two classes. The FLOAT family has the same rule in {@link ApproximateValues}.
 */
public final class ExactValues {

    private ExactValues() {
    }

    /**
     * The value as its column stores it.
     *
     * @param value the value written, or null
     * @param type the column's numeric type
     * @return the carrier — a Long or a BigDecimal — or the value itself when it is not a number the
     *         column can hold (a NULL, a non-numeric, a NaN) or the column is approximate
     */
    public static Object written(final Object value, final NumericType type) {
        if (value == null || type == null || NumericType.isApproximate(type) || type.getScale() < 0) {
            return value;
        }
        if (isCarrier(value, type)) {
            return value;
        }
        final BigDecimal exact = exactOf(value);
        if (exact == null) {
            return value;
        }
        final BigDecimal atScale = exact.setScale(type.getScale(), RoundingMode.HALF_UP);
        if (type.getScale() == 0) {
            try {
                return Long.valueOf(atScale.longValueExact());
            } catch (final ArithmeticException beyondLong) {
                return atScale;
            }
        }
        return atScale;
    }

    /**
     * Whether a cell already is the carrier its column holds — the check every write makes before
     * touching a row.
     *
     * @param value the cell, not null
     * @param type the column's exact numeric type
     * @return true when the cell can be stored as it is
     */
    public static boolean isCarrier(final Object value, final NumericType type) {
        if (type.getScale() == 0) {
            if (value instanceof Long) {
                return true;
            }
            if (value instanceof BigDecimal && ((BigDecimal) value).scale() == 0) {
                // A whole BigDecimal is the carrier only beyond the long range.
                return ((BigDecimal) value).precision() > 18
                    && ((BigDecimal) value).abs().compareTo(LONG_RANGE) > 0;
            }
            return false;
        }
        return value instanceof BigDecimal && ((BigDecimal) value).scale() == type.getScale();
    }

    private static final BigDecimal LONG_RANGE = BigDecimal.valueOf(Long.MAX_VALUE);

    private static BigDecimal exactOf(final Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Double || value instanceof Float) {
            final double approximate = ((Number) value).doubleValue();
            if (Double.isNaN(approximate) || Double.isInfinite(approximate)) {
                return null;
            }
            return new BigDecimal(Double.toString(approximate));
        }
        return null;
    }
}
