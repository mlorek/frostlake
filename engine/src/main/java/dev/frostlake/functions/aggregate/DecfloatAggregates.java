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
     * PERCENTILE_CONT, and MEDIAN at one half, over DECFLOAT values: the two values about the fraction's place
     * interpolated exactly and the result held once, to 38 significant digits. No NUMBER scale applies, so neither
     * the input scale plus three of the fixed-point path ({@code AggregateNumerics.percentileCont}) nor its 38-digit
     * bound: MEDIAN over 0 and a DECFLOAT third is 1.6666666666666666666666666666666666667e-1, over 1e100 and 2 is
     * 5e99. The place is computed in decimal, so PERCENTILE_CONT(0.1) over 0, 1, 2 and 3 is 0.3, not a double's
     * 0.30000000000000004.
     *
     * @param values   the values, NULLs skipped
     * @param fraction the percentile, between 0 and 1, counted upwards
     * @return the interpolated value, or null when no value is given
     */
    public static BigDecimal percentileCont(final Iterable<Object> values, final double fraction) {
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
        final BigDecimal place = BigDecimal.valueOf(fraction).multiply(BigDecimal.valueOf(sorted.size() - 1));
        final int low = place.intValue();
        final int high = Math.min(low + 1, sorted.size() - 1);
        final BigDecimal weight = place.subtract(BigDecimal.valueOf(low));
        return Decfloats.held(sorted.get(low).multiply(BigDecimal.ONE.subtract(weight))
            .add(sorted.get(high).multiply(weight)));
    }
}
