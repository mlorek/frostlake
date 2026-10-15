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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.expressions.RawOverflowKind;
import dev.frostlake.executor.expressions.RawRangeOverflow;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Prefix sums of a sorted window partition's exact-number argument, so that a SUM, an AVG or a COUNT over any of its
 * frames — the whole partition, a running total, a moving frame — is read in constant time instead of being added up
 * again for every row. Over exact numbers a frame's answer depends on nothing but its sum, how many values it holds
 * and the widest scale among them, and it is computed here by the rules {@link AggregateNumerics#windowSum} and
 * {@link AggregateNumerics#windowAvg} apply to the same frame. A partition holding anything else — a FLOAT, a VARIANT,
 * a text — or a value near the edge of the carrier, where the running total is refused at the row it overflows, has
 * no prefix sums and is added row by row.
 */
public final class ExactFrameSums {

    /** The widest value taken: well inside the 38 digits a running total may reach before it is refused. */
    private static final int SAFE_PRECISION = 30;

    /** {@code sums[i]}: the sum of the first {@code i} values. */
    private final BigDecimal[] sums;
    /** {@code counts[i]}: how many of the first {@code i} values are not NULL. */
    private final long[] counts;
    /** The scales the values carry, widest first. */
    private final int[] scales;
    /** For each of {@link #scales}, how many of the first {@code i} values carry it. */
    private final int[][] scaleCounts;

    private ExactFrameSums(final BigDecimal[] sums, final long[] counts, final int[] scales,
                           final int[][] scaleCounts) {
        this.sums = sums;
        this.counts = counts;
        this.scales = scales;
        this.scaleCounts = scaleCounts;
    }

    /**
     * The prefix sums of a partition's values, or null when a value is not an exact number well inside the carrier.
     *
     * @param values the argument's values in the partition's order, NULL for a missing one
     * @return the sums, or null
     */
    public static ExactFrameSums of(final Object[] values) {
        final int size = values.length;
        final BigDecimal[] sums = new BigDecimal[size + 1];
        final long[] counts = new long[size + 1];
        final int[] valueScales = new int[size];
        final List<Integer> distinct = new ArrayList<>();
        sums[0] = BigDecimal.ZERO;
        for (int i = 0; i < size; i++) {
            final BigDecimal exact = exactValue(values[i]);
            if (values[i] != null && exact == null) {
                return null;
            }
            sums[i + 1] = exact == null ? sums[i] : sums[i].add(exact);
            counts[i + 1] = counts[i] + (exact == null ? 0 : 1);
            valueScales[i] = exact == null ? -1 : exact.scale();
            if (exact != null && !distinct.contains(exact.scale())) {
                distinct.add(exact.scale());
            }
        }
        distinct.sort(null);
        final int[] scales = new int[distinct.size()];
        final int[][] scaleCounts = new int[distinct.size()][];
        for (int s = 0; s < scales.length; s++) {
            scales[s] = distinct.get(scales.length - 1 - s);
            final int[] prefix = new int[size + 1];
            for (int i = 0; i < size; i++) {
                prefix[i + 1] = prefix[i] + (valueScales[i] == scales[s] ? 1 : 0);
            }
            scaleCounts[s] = prefix;
        }
        return new ExactFrameSums(sums, counts, scales, scaleCounts);
    }

    /** An exact number's value at its own scale, or null for NULL and for anything else, as for one too wide. */
    private static BigDecimal exactValue(final Object value) {
        final BigDecimal exact;
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            exact = BigDecimal.valueOf(((Number) value).longValue());
        } else if (value instanceof BigInteger) {
            exact = new BigDecimal((BigInteger) value);
        } else if (value instanceof BigDecimal) {
            exact = (BigDecimal) value;
        } else {
            return null;
        }
        return exact.scale() < 0 || exact.scale() > SAFE_PRECISION || exact.precision() > SAFE_PRECISION
            ? null : exact;
    }

    /**
     * How many values of a frame are not NULL.
     *
     * @param from the frame's first position
     * @param to   the frame's last position; a frame with {@code from > to} is empty
     * @return the count
     */
    public long count(final int from, final int to) {
        return from > to ? 0L : counts[to + 1] - counts[from];
    }

    /**
     * The SUM of a frame: an integer when no value carries a fraction, else a decimal at the widest scale.
     *
     * @param from the frame's first position
     * @param to   the frame's last position
     * @return the sum, or null for a frame without a value
     */
    public Object sum(final int from, final int to) {
        if (count(from, to) == 0L) {
            return null;
        }
        final BigDecimal sum = frameSum(from, to);
        final int scale = widestScale(from, to);
        if (scale > 0) {
            return sum.setScale(scale);
        }
        final BigInteger integral = sum.toBigIntegerExact();
        return integral.bitLength() < 63 ? Long.valueOf(integral.longValue()) : integral;
    }

    /**
     * The AVG of a frame: a cumulative window's at the average's own scale, rounded half up; any other window's
     * three digits wider than its widest input, truncated.
     *
     * @param from          the frame's first position
     * @param to            the frame's last position
     * @param nonCumulative whether the window is not cumulative
     * @return the average, or null for a frame without a value
     */
    public Object avg(final int from, final int to, final boolean nonCumulative) {
        final long count = count(from, to);
        if (count == 0L) {
            return null;
        }
        final int maxScale = widestScale(from, to);
        final int quotientScale = nonCumulative ? maxScale + 3 : AggregateNumerics.averageScale(maxScale);
        final BigDecimal quotient = frameSum(from, to).divide(BigDecimal.valueOf(count), quotientScale,
            nonCumulative ? RoundingMode.DOWN : RoundingMode.HALF_UP);
        if (quotient.precision() > 38 && NumericRangeRefusal.outsideSb16Window(quotient.unscaledValue())) {
            throw new RawRangeOverflow(RawOverflowKind.QUOTIENT, quotient, quotientScale);
        }
        return quotient;
    }

    private BigDecimal frameSum(final int from, final int to) {
        final BigDecimal sum = sums[to + 1].subtract(sums[from]);
        SumAccumulator.requireSumFits(sum);
        return sum;
    }

    /** The widest scale among a frame's values. */
    private int widestScale(final int from, final int to) {
        for (int s = 0; s < scales.length; s++) {
            if (scaleCounts[s][to + 1] - scaleCounts[s][from] > 0) {
                return scales[s];
            }
        }
        return 0;
    }
}
