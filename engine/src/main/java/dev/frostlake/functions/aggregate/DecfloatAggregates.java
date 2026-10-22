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
import dev.frostlake.values.DecfloatRangeException;
import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SUM, AVG and the interpolating percentiles over a DECFLOAT argument, in DECFLOAT's own terms: every value adds to
 * 38 significant digits whatever its magnitude, so SUM over 1e1000, -2.47e999 and 22e-75 is 7.53e999 and their AVG
 * 2.51e999 (live-verified), where the fixed-point paths refuse a total past 38 digits. NULLs are skipped; no value at
 * all is NULL.
 */
public final class DecfloatAggregates {

    /** The significant digits a percentile's interpolation weight keeps of its double. */
    private static final MathContext WEIGHT_DIGITS = new MathContext(18, RoundingMode.HALF_UP);

    private DecfloatAggregates() {
    }

    /**
     * A running total plus a value; a total past DECFLOAT's range is the aggregate's own overflow, "Value overflow in
     * a SUM aggregate" (live-verified).
     *
     * @param total the running total
     * @param value the value
     * @return the new total
     */
    static BigDecimal added(final BigDecimal total, final BigDecimal value) {
        try {
            return Decfloats.held(total.add(value));
        } catch (final DecfloatRangeException outOfRange) {
            throw new AggregateRangeRefusal("Value overflow in a SUM aggregate");
        }
    }

    /**
     * The DECFLOAT sum of the values.
     *
     * @param values the values, NULLs skipped
     * @return the sum, or null when no value is given
     */
    public static BigDecimal sum(final List<Object> values) {
        BigDecimal sum = null;
        for (final Object value : values) {
            if (value instanceof Number) {
                final BigDecimal held = Decfloats.of((Number) value, "SUM");
                sum = sum == null ? held : added(sum, held);
            }
        }
        return sum;
    }

    /**
     * The DECFLOAT average of the values: their sum over their count, to 38 significant digits.
     *
     * @param values the values, NULLs skipped
     * @return the average, or null when no value is given
     */
    public static BigDecimal avg(final List<Object> values) {
        BigDecimal sum = null;
        long count = 0;
        for (final Object value : values) {
            if (value instanceof Number) {
                final BigDecimal held = Decfloats.of((Number) value, "AVG");
                sum = sum == null ? held : added(sum, held);
                count++;
            }
        }
        return sum == null ? null : Decfloats.divide(sum, BigDecimal.valueOf(count));
    }

    /**
     * PERCENTILE_CONT, and MEDIAN at one half, over DECFLOAT values. The place and the two weights are DOUBLES: the
     * place is the fraction times one less than the count, the lower value's weight the distance from the place up to
     * the next whole place and the upper value's one minus that, and each weight is taken to 18 significant digits.
     * The interpolation itself is DECFLOAT arithmetic, {@code lower * weight + upper * weight}, each step held to 38
     * significant digits, with no NUMBER scale or bound: PERCENTILE_CONT(0.1) over 0 and 1 is 0.0999999999999999778
     * and over 1 and 3 is 1.1999999999999999554, PERCENTILE_CONT(0.3) over 0 and 10 is 3.00000000000000044, MEDIAN
     * over 0 and a DECFLOAT third is 1.6666666666666666666666666666666666667e-1 and over 1e100 and 2 is 5e99. A
     * whole place reads its value as it is. WITHIN GROUP (ORDER BY x DESC) runs the same steps over the values in
     * descending order: PERCENTILE_CONT(0.7) over 0 to 3 descending is 0.900000000000000355 (all live-verified).
     * Computed group by group, an interpolation is refused (see {@link #perGroupPercentileRefusal}).
     *
     * @param values     the values, NULLs skipped
     * @param fraction   the percentile, between 0 and 1
     * @param descending whether the values are ordered from the greatest
     * @param perGroup   whether the call is computed per group, a GROUP BY's or a PARTITION BY's
     * @return the interpolated value, or null when no value is given
     */
    public static BigDecimal percentileCont(final Iterable<Object> values, final double fraction,
                                            final boolean descending, final boolean perGroup) {
        final List<BigDecimal> sorted = new ArrayList<>();
        for (final Object value : values) {
            if (value instanceof Number) {
                sorted.add(Decfloats.of((Number) value, "PERCENTILE_CONT"));
            }
        }
        if (sorted.isEmpty()) {
            return null;
        }
        Collections.sort(sorted);
        if (descending) {
            Collections.reverse(sorted);
        }
        final double place = fraction * (sorted.size() - 1);
        final int low = (int) Math.floor(place);
        final int high = (int) Math.ceil(place);
        if (low == high) {
            return Decfloats.held(sorted.get(low));
        }
        if (perGroup) {
            throw perGroupPercentileRefusal();
        }
        final double lowerWeight = high - place;
        final double upperWeight = 1 - lowerWeight;
        return Decfloats.add(Decfloats.multiply(sorted.get(low), weight(lowerWeight)),
            Decfloats.multiply(sorted.get(high), weight(upperWeight)));
    }

    /**
     * MEDIAN or PERCENTILE_CONT of a DECFLOAT argument interpolating group by group, which the account does not do: in
     * a GROUP BY query or a window with PARTITION BY, a group whose place falls between two values - MEDIAN of two
     * values, even two equal ones, PERCENTILE_CONT(0.25) of three - is refused, "DecFloat not supported", where a
     * group whose place is a whole one (one value, MEDIAN of three, PERCENTILE_CONT(0) or (1)) or that holds only
     * NULLs is answered, and so is every interpolation over the whole input or a window over () (live-verified).
     *
     * @return the refusal
     */
    public static RuntimeException perGroupPercentileRefusal() {
        return new RuntimeException("DecFloat not supported");
    }

    /** An interpolation weight as the interpolation reads it: the double to 18 significant digits. */
    private static BigDecimal weight(final double weight) {
        return new BigDecimal(weight).round(WEIGHT_DIGITS);
    }
}
