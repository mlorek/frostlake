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

/**
 * The FLOAT moments of a frame moving along its partition — the count, the sum and the sum of squares the variance
 * family reads (see {@code SquaredSumMoments}) — as the account computes them: one running state that rows ENTER and
 * LEAVE, never a frame taken afresh, so a frame's last digits depend on the rows before it. Live-verified over every
 * shape below:
 *
 * <pre>
 *   a ROWS frame short of the whole partition        compensated sums, walked as FloatFrameSums walks them
 *   RANGE from UNBOUNDED PRECEDING to the CURRENT ROW  compensated sums, from the partition's first row
 *   a RANGE frame with an offset and no UNBOUNDED edge plain sums, from the partition's first row
 * </pre>
 *
 * <p>Each sum is read as it stands, its last compensation left unapplied, and the variance is
 * {@code (n·Σx² − (Σx)²) / (n·(n − 1))} over it. Over 0.2, 0.7, 0.11, 0.9 and 0.6, VARIANCE over
 * {@code ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING} answers 0.15703333333333333409 on the second row, where the
 * frame added up on its own gives 0.15703333333333336185. Rows enter a frame, and then leave it, in the order the
 * walk meets them; a NULL takes no part.
 */
public final class FloatFrameMoments {

    private final int[] counts;
    private final double[] sums;
    private final double[] squares;

    /**
     * Every row's frame moments, in one walk.
     *
     * @param terms each row's value as a double, in the window's order; null where the row takes no part
     * @param lows each row's first frame position
     * @param highs each row's last frame position, below its first for an empty frame
     * @param backward whether the walk starts from the partition's last row
     * @param compensated whether the sums add with Kahan compensation rather than plainly
     */
    public FloatFrameMoments(final Double[] terms, final int[] lows, final int[] highs, final boolean backward,
                             final boolean compensated) {
        final int size = terms.length;
        counts = new int[size];
        sums = new double[size];
        squares = new double[size];
        final RunningMoments running = new RunningMoments(terms, compensated);
        int low = 0;
        int high = -1;
        for (int step = 0; step < size; step++) {
            final int row = backward ? size - 1 - step : step;
            final int frameLow = lows[row];
            final int frameHigh = highs[row];
            final boolean held = low <= high;
            final boolean wanted = frameLow <= frameHigh;
            if (backward) {
                if (!held) {
                    running.enter(frameHigh, frameLow, -1);
                } else if (wanted) {
                    running.enter(frameHigh, Math.max(frameLow, high + 1), -1);
                    running.enter(Math.min(frameHigh, low - 1), frameLow, -1);
                }
                if (held && !wanted) {
                    running.leave(high, low, -1);
                } else if (held) {
                    running.leave(high, Math.max(low, frameHigh + 1), -1);
                    running.leave(Math.min(high, frameLow - 1), low, -1);
                }
            } else {
                if (!held) {
                    running.enter(frameLow, frameHigh, 1);
                } else if (wanted) {
                    running.enter(frameLow, Math.min(frameHigh, low - 1), 1);
                    running.enter(Math.max(frameLow, high + 1), frameHigh, 1);
                }
                if (held && !wanted) {
                    running.leave(low, high, 1);
                } else if (held) {
                    running.leave(low, Math.min(high, frameLow - 1), 1);
                    running.leave(Math.max(low, frameHigh + 1), high, 1);
                }
            }
            low = wanted ? frameLow : 0;
            high = wanted ? frameHigh : -1;
            counts[row] = running.count();
            sums[row] = running.sum();
            squares[row] = running.squares();
        }
    }

    /**
     * How many values a row's frame holds.
     *
     * @param row the row's position in the window's order
     * @return the count, 0 when the frame holds none
     */
    public int count(final int row) {
        return counts[row];
    }

    /**
     * The sample variance of a row's frame (denominator n − 1); the caller checks it holds two values at least.
     *
     * @param row the row's position in the window's order
     * @return the variance, never negative
     */
    public double sampleVariance(final int row) {
        final double n = counts[row];
        return Math.max(0.0, numerator(row) / (n * (n - 1)));
    }

    /**
     * The population variance of a row's frame (denominator n); the caller checks it holds a value at least.
     *
     * @param row the row's position in the window's order
     * @return the variance, never negative
     */
    public double populationVariance(final int row) {
        final double n = counts[row];
        return Math.max(0.0, numerator(row) / (n * n));
    }

    /** {@code n·Σx² − (Σx)²}, the numerator both variances share. */
    private double numerator(final int row) {
        return counts[row] * squares[row] - sums[row] * sums[row];
    }
}
