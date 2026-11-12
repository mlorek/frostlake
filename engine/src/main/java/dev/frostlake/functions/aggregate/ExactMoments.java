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

import dev.frostlake.executor.AggregateRangeRefusal;
import dev.frostlake.executor.NumericRangeRefusal;
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
 *
 * <p>A series of whole numbers is summed as the account sums it, in signed 128-bit carriers. A value whose square
 * leaves its carrier is refused naming the square ("Number out of representable range: type FIXED[SB16](38,0){not
 * null}, value 4e+38" for 20000000000000000000), and so is {@code n·Σx²} when the statistic is read; a running sum
 * that leaves its carrier is a SUM aggregate's overflow, which a window with an ORDER BY reports in its own words. A
 * series with a fraction is held exactly: at a scale of 8 or more the account answers past those carriers.
 */
public final class ExactMoments {

    private long count;
    private BigDecimal sum = BigDecimal.ZERO;
    private BigDecimal sumOfSquares = BigDecimal.ZERO;
    private long inexact;

    /**
     * Folds one value in: a value of no exact type is counted and leaves the moments inexact.
     *
     * @param value the value, not null
     */
    public void add(final Object value) {
        count++;
        final BigDecimal number = exactValue(value);
        if (number == null) {
            inexact++;
            return;
        }
        final BigDecimal square = number.multiply(number);
        if (number.scale() == 0) {
            requireRawFits(square);
        }
        sum = sum.add(number);
        sumOfSquares = sumOfSquares.add(square);
        requireSumsFit();
    }

    /**
     * Takes one value folded in before out again, as a row leaves a moving window frame.
     *
     * @param value the value, not null
     */
    public void remove(final Object value) {
        count--;
        final BigDecimal number = exactValue(value);
        if (number == null) {
            inexact--;
            return;
        }
        sum = sum.subtract(number);
        sumOfSquares = sumOfSquares.subtract(number.multiply(number));
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
        inexact += other.inexact;
        requireSumsFit();
    }

    /** The values folded in. */
    public long count() {
        return count;
    }

    /** Whether every value folded in was of an exact type. */
    public boolean isExact() {
        return inexact == 0;
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
        // n·Σx² bounds (Σx)², so it is the one product that can leave the carrier first.
        final BigDecimal scaledSquares = n.multiply(sumOfSquares);
        if (sumOfSquares.scale() == 0) {
            requireRawFits(scaledSquares);
        }
        final BigDecimal spread = scaledSquares.subtract(sum.multiply(sum));
        final BigDecimal denominator = n.multiply(sample ? BigDecimal.valueOf(count - 1) : n);
        final BigDecimal variance = spread.divide(denominator, scale, RoundingMode.HALF_UP);
        return variance.signum() < 0 ? BigDecimal.ZERO.setScale(scale) : variance;
    }

    public void reset() {
        count = 0;
        sum = BigDecimal.ZERO;
        sumOfSquares = BigDecimal.ZERO;
        inexact = 0;
    }

    /** Refuses a running sum of whole numbers that leaves its carrier; a series with a fraction is held exactly. */
    private void requireSumsFit() {
        if (sumOfSquares.scale() == 0) {
            SumAccumulator.requireSumFits(sum);
            SumAccumulator.requireSumFits(sumOfSquares);
        }
    }

    /**
     * Refuses a product whose raw scaled integer leaves the signed 128-bit carrier, the raw printed as a double.
     *
     * @param product the product
     */
    private static void requireRawFits(final BigDecimal product) {
        final BigInteger raw = product.unscaledValue();
        if (NumericRangeRefusal.outsideSb16Window(raw)) {
            throw new AggregateRangeRefusal(NumericRangeRefusal.typedText("SB16", 38, 0, false,
                NumericRangeRefusal.gText(raw.doubleValue())));
        }
    }

    /** A value of an exact type as a decimal at a scale of zero or more, or null for any other. */
    private static BigDecimal exactValue(final Object value) {
        if (value instanceof BigDecimal) {
            final BigDecimal decimal = (BigDecimal) value;
            return decimal.scale() < 0 ? decimal.setScale(0) : decimal;
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
