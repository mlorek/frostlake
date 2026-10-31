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

package dev.frostlake.functions.aggregate;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The count, sum and sum of squares of an exact series, held exactly, which the variance family uses over EXACT
 * inputs: the variance is the one division of {@code n·Σx² − (Σx)²} by {@code n·(n − 1)} (or {@code n²}), rounded
 * half up to the scale its column declares. The account answers exactly there, a moving window frame's rows
 * included: VARIANCE over the NUMBERs 100000000, 100000001 and 100000002 is 1.000000, and over 100000000.01 and
 * 100000000.02 it is 0.0000500000 (live-verified). Over FLOATs the account reads double sums instead, digit for digit
 * — see {@link SquaredSumMoments}.
 */
public final class ExactMoments {

    private long count;
    private BigDecimal sum = BigDecimal.ZERO;
    private BigDecimal sumOfSquares = BigDecimal.ZERO;
    private boolean exact = true;

    /**
     * Folds one value in: a value of no exact type is counted and leaves the moments inexact.
     *
     * @param value the value, not null
     */
    public void add(final Object value) {
        count++;
        final BigDecimal number = exactValue(value);
        if (number == null) {
            exact = false;
            return;
        }
        sum = sum.add(number);
        sumOfSquares = sumOfSquares.add(number.multiply(number));
    }

    /**
     * Folds another series' moments in.
     *
     * @param other the other series
     */
    public void merge(final ExactMoments other) {
        count += other.count;
        sum = sum.add(other.sum);
        sumOfSquares = sumOfSquares.add(other.sumOfSquares);
        exact = exact && other.exact;
    }

    /** The values folded in. */
    public long count() {
        return count;
    }

    /** Whether every value folded in was of an exact type. */
    public boolean isExact() {
        return exact;
    }

    /**
     * The variance rounded half up to a scale; the caller checks there are two values at least for a sample's, one
     * for the population's.
     *
     * @param sample whether the denominator is {@code n − 1} rather than {@code n}
     * @param scale  the scale
     * @return the variance
     */
    public BigDecimal variance(final boolean sample, final int scale) {
        final BigDecimal n = BigDecimal.valueOf(count);
        final BigDecimal spread = n.multiply(sumOfSquares).subtract(sum.multiply(sum));
        final BigDecimal denominator = n.multiply(sample ? BigDecimal.valueOf(count - 1) : n);
        final BigDecimal variance = spread.divide(denominator, scale, RoundingMode.HALF_UP);
        return variance.signum() < 0 ? BigDecimal.ZERO.setScale(scale) : variance;
    }

    public void reset() {
        count = 0;
        sum = BigDecimal.ZERO;
        sumOfSquares = BigDecimal.ZERO;
        exact = true;
    }

    /** A value of an exact type as a decimal, or null for any other. */
    private static BigDecimal exactValue(final Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        return null;
    }
}
