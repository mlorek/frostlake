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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The closed interval of values an exact-numeric expression can take, as the account's planner
 * reasons about it: a stored column's interval is its statistics (the least and greatest value in the
 * whole table, whatever a WHERE keeps), a literal's is itself, and arithmetic widens it by interval
 * rules. {@link #EMPTY} is the interval of no value — a NULL, a column with no rows — which every
 * operation keeps empty and a union ignores.
 *
 * <p>The interval decides the storage tag {@code SYSTEM$TYPEOF} prints after an exact NUMBER, which
 * is why it is carried at all: live tags a NUMBER(38,0) column holding 1 and 999 as SB2 on EVERY row,
 * the width of the widest value in the table, and never the width of the row's own value.
 *
 * <p>Beside its bounds an interval says whether the expression may also answer NULL, which the
 * statistics know of a column and which decides where the planner may stop a COALESCE. A known value
 * never does, arithmetic may only where an operand may, a union may where either side may, and
 * {@link #EMPTY} is nothing but the NULL.
 *
 * <p>And it says whether a CONDITION over the expression may be settled by it at all (see
 * {@link #isOpaqueToConditions}). The planner reduces a COALESCE whose first argument cannot be NULL to
 * that argument; one it cannot reduce keeps its interval for the storage tag and for LEAST, GREATEST and
 * DECODE, but no comparison over it, or over anything computed from it, is settled: live leaves
 * {@code IFF(COALESCE(n, 0) > 5, 3000.5, 1.5)} open over a column holding a NULL, and settles it over
 * one holding none.
 */
public final class ValueRange {

    /** No value at all: a NULL, or a column that holds nothing. Takes the narrowest tag. */
    public static final ValueRange EMPTY = new ValueRange(null, null, true, false);

    /** Wider than any tag tells apart, for a plan that always accumulates in sixteen bytes. */
    public static final ValueRange WIDEST = new ValueRange(BigDecimal.TEN.pow(37).negate(),
        BigDecimal.TEN.pow(37), true, false);

    private final BigDecimal min;
    private final BigDecimal max;
    private final boolean nullable;
    private final boolean opaqueToConditions;

    private ValueRange(final BigDecimal min, final BigDecimal max, final boolean nullable,
                       final boolean opaqueToConditions) {
        this.min = min;
        this.max = max;
        this.nullable = nullable;
        this.opaqueToConditions = opaqueToConditions;
    }

    /** The interval of one known value, which is never NULL, or {@link #EMPTY} for null. */
    public static ValueRange of(final BigDecimal value) {
        return value == null ? EMPTY : new ValueRange(value, value, false, false);
    }

    /** The interval between two bounds, in either order, holding no NULL. */
    public static ValueRange between(final BigDecimal a, final BigDecimal b) {
        if (a == null || b == null) {
            return EMPTY;
        }
        return a.compareTo(b) <= 0 ? new ValueRange(a, b, false, false) : new ValueRange(b, a, false, false);
    }

    /**
     * A runtime value read exactly, or null for one that is not an exact number — a NULL, a string
     * that does not spell a number, a non-finite double.
     */
    public static BigDecimal exactOf(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        if (value instanceof Double || value instanceof Float) {
            final double d = ((Number) value).doubleValue();
            return Double.isFinite(d) ? new BigDecimal(value.toString()) : null;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }

    public boolean isEmpty() {
        return min == null;
    }

    public BigDecimal getMin() {
        return min;
    }

    public BigDecimal getMax() {
        return max;
    }

    /** Whether the expression may answer NULL beside its values; always true of {@link #EMPTY}. */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Whether no condition over the expression is settled by this interval: the value came through a
     * COALESCE (NVL, IFNULL) the planner could not reduce to its first argument, directly or through the
     * arithmetic, casts and conditionals computed from it. The bounds and the nullability still stand.
     */
    public boolean isOpaqueToConditions() {
        return opaqueToConditions;
    }

    /** This interval, saying whether the expression may also answer NULL; {@link #EMPTY} stays itself. */
    public ValueRange withNullable(final boolean mayBeNull) {
        if (isEmpty() || nullable == mayBeNull) {
            return this;
        }
        return new ValueRange(min, max, mayBeNull, opaqueToConditions);
    }

    /** This interval, as one no condition is settled by; {@link #EMPTY} stays itself. */
    public ValueRange opaqueToConditions() {
        if (isEmpty() || opaqueToConditions) {
            return this;
        }
        return new ValueRange(min, max, nullable, true);
    }

    /** This interval, opaque to conditions where {@code source} is: for a value derived from it. */
    public ValueRange withConditionOpacityOf(final ValueRange source) {
        return source != null && source.opaqueToConditions ? opaqueToConditions() : this;
    }

    /** This interval widened to hold {@code value}; a null value widens nothing. */
    public ValueRange including(final BigDecimal value) {
        if (value == null) {
            return this;
        }
        if (isEmpty()) {
            return of(value);
        }
        return new ValueRange(min.min(value), max.max(value), nullable, opaqueToConditions);
    }

    /**
     * The smallest interval holding both, NULL-able where either side is and opaque to conditions where
     * either side is. An empty side adds no value, only its NULL; an unknown (null) side contributes
     * nothing.
     */
    public ValueRange union(final ValueRange other) {
        if (other == null) {
            return this;
        }
        if (other.isEmpty()) {
            return withNullable(true);
        }
        if (isEmpty()) {
            return other.withNullable(true);
        }
        return new ValueRange(min.min(other.min), max.max(other.max), nullable || other.nullable,
            opaqueToConditions || other.opaqueToConditions);
    }

    public ValueRange negate() {
        return isEmpty() ? this : new ValueRange(max.negate(), min.negate(), nullable, opaqueToConditions);
    }

    public ValueRange abs() {
        if (isEmpty()) {
            return this;
        }
        if (min.signum() >= 0) {
            return this;
        }
        if (max.signum() <= 0) {
            return negate();
        }
        return new ValueRange(BigDecimal.ZERO, min.abs().max(max), nullable, opaqueToConditions);
    }

    public ValueRange add(final ValueRange other) {
        if (isEmpty() || other == null || other.isEmpty()) {
            return EMPTY;
        }
        return new ValueRange(min.add(other.min), max.add(other.max), nullable || other.nullable,
            opaqueToConditions || other.opaqueToConditions);
    }

    public ValueRange subtract(final ValueRange other) {
        if (isEmpty() || other == null || other.isEmpty()) {
            return EMPTY;
        }
        return new ValueRange(min.subtract(other.max), max.subtract(other.min), nullable || other.nullable,
            opaqueToConditions || other.opaqueToConditions);
    }

    /** The interval of every product of one value from each side: the four corners bound it. */
    public ValueRange multiply(final ValueRange other) {
        if (isEmpty() || other == null || other.isEmpty()) {
            return EMPTY;
        }
        final BigDecimal a = min.multiply(other.min);
        final BigDecimal b = min.multiply(other.max);
        final BigDecimal c = max.multiply(other.min);
        final BigDecimal d = max.multiply(other.max);
        return new ValueRange(a.min(b).min(c).min(d), a.max(b).max(c).max(d), nullable || other.nullable,
            opaqueToConditions || other.opaqueToConditions);
    }

    /** Both bounds multiplied by a non-negative factor. */
    public ValueRange scaledBy(final BigDecimal factor) {
        return multiply(of(factor));
    }

    /** The interval mirrored about zero, so it holds every value of either sign this one can reach. */
    public ValueRange symmetric() {
        if (isEmpty()) {
            return this;
        }
        final BigDecimal reach = magnitude();
        return new ValueRange(reach.negate(), reach, nullable, opaqueToConditions);
    }

    /**
     * Whether every value of this interval lies below every value of {@code other} — or at most, with
     * {@code orEqual}: TRUE when every pair does, FALSE when none does, null when the intervals leave it
     * open or either is empty.
     */
    public Boolean below(final ValueRange other, final boolean orEqual) {
        if (isEmpty() || other == null || other.isEmpty()) {
            return null;
        }
        final int highAgainstLow = max.compareTo(other.min);
        if (highAgainstLow < 0 || (orEqual && highAgainstLow == 0)) {
            return Boolean.TRUE;
        }
        final int lowAgainstHigh = min.compareTo(other.max);
        if (lowAgainstHigh > 0 || (!orEqual && lowAgainstHigh == 0)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * Whether this interval's values equal {@code other}'s: TRUE for one shared point, FALSE when the
     * two are disjoint, null when they leave it open or either is empty.
     */
    public Boolean equalTo(final ValueRange other) {
        if (isEmpty() || other == null || other.isEmpty()) {
            return null;
        }
        if (max.compareTo(other.min) < 0 || other.max.compareTo(min) < 0) {
            return Boolean.FALSE;
        }
        final boolean bothPoints = min.compareTo(max) == 0 && other.min.compareTo(other.max) == 0;
        return bothPoints ? Boolean.TRUE : null;
    }

    /** The greatest distance from zero this interval reaches; zero when empty. */
    public BigDecimal magnitude() {
        return isEmpty() ? BigDecimal.ZERO : min.abs().max(max.abs());
    }

    @Override
    public String toString() {
        return isEmpty() ? "[]" : "[" + min.toPlainString() + ", " + max.toPlainString() + "]";
    }
}
