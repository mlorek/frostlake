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

package dev.frostlake.functions.window;

import dev.frostlake.functions.aggregate.CompensatedSum;

/**
 * The FLOAT sums of a ROWS frame moving along its partition, as the account computes them: the frame is
 * one running sum that rows ENTER and LEAVE, never a sum taken afresh, so a frame's last digits depend on
 * the rows before it. Live-verified over every frame shape below:
 *
 * <ul>
 *   <li>the running sum walks the partition in the window's order, adding each row as it enters the frame
 *       and then subtracting each row that leaves it;</li>
 *   <li>a frame that ends FOLLOWING the current row, or at UNBOUNDED FOLLOWING, is walked from the
 *       partition's last row back instead, unless it starts at UNBOUNDED PRECEDING;</li>
 *   <li>SUM adds with Kahan compensation and reads the sum without its last correction, while AVG adds
 *       plainly and divides by the frame's count.</li>
 * </ul>
 *
 * <p>Over 0.3, 0.7, 0.1 and 0.5 squared, {@code ROWS BETWEEN CURRENT ROW AND CURRENT ROW} sums the second
 * row to 0.48999999999999988010, where 0.7 squared alone is 0.48999999999999993561. A NULL takes no part,
 * and a frame that holds no value has no sum.
 */
public final class FloatFrameSums {

    private final double[] sums;
    private final int[] counts;

    /**
     * Every row's frame sum, in one walk.
     *
     * @param terms each row's value as a double, in the window's order; null where the row takes no part
     * @param start the frame's start as an offset from the current row, negative when PRECEDING; null for
     *     UNBOUNDED PRECEDING
     * @param end the frame's end as an offset from the current row, positive when FOLLOWING; null for
     *     UNBOUNDED FOLLOWING
     * @param compensated whether the running sum adds with Kahan compensation, as SUM does, rather than
     *     plainly, as AVG does
     */
    public FloatFrameSums(final Double[] terms, final Long start, final Long end, final boolean compensated) {
        final int size = terms.length;
        sums = new double[size];
        counts = new int[size];
        final boolean backward = end == null || (start != null && end.longValue() > 0L);
        final Long walkStart = backward ? negated(end) : start;
        final Long walkEnd = backward ? negated(start) : end;
        final CompensatedSum kahan = new CompensatedSum();
        double plain = 0.0;
        int count = 0;
        int low = 0;
        int high = -1;
        for (int step = 0; step < size; step++) {
            final long frameLow = walkStart == null ? 0L : Math.max(step + walkStart.longValue(), 0L);
            final long frameHigh = walkEnd == null ? size - 1 : Math.min(step + walkEnd.longValue(), size - 1);
            while (high < frameHigh) {
                high++;
                final Double term = terms[rowAt(high, size, backward)];
                if (high >= low && term != null) {
                    if (compensated) {
                        kahan.add(term.doubleValue());
                    } else {
                        plain += term.doubleValue();
                    }
                    count++;
                }
            }
            while (low < frameLow) {
                final Double term = low <= high ? terms[rowAt(low, size, backward)] : null;
                if (term != null) {
                    if (compensated) {
                        kahan.add(-term.doubleValue());
                    } else {
                        plain -= term.doubleValue();
                    }
                    count--;
                }
                low++;
            }
            final int row = rowAt(step, size, backward);
            counts[row] = count;
            sums[row] = compensated ? kahan.value() : plain;
        }
    }

    private static Long negated(final Long offset) {
        return offset == null ? null : Long.valueOf(-offset.longValue());
    }

    /** The row a step of the walk stands on, counted from the partition's end when walking back. */
    private static int rowAt(final int step, final int size, final boolean backward) {
        return backward ? size - 1 - step : step;
    }

    /**
     * How many values the frame of a row holds.
     *
     * @param row the row's position in the window's order
     * @return the count, 0 when the frame holds none
     */
    public int count(final int row) {
        return counts[row];
    }

    /**
     * The sum of a row's frame, meaningful when {@link #count} is not 0.
     *
     * @param row the row's position in the window's order
     * @return the running sum as it stood on that row
     */
    public double sum(final int row) {
        return sums[row];
    }
}
