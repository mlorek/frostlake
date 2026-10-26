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
 * The running count, sum and sum of squares {@link FloatFrameMoments} walks along a partition: a run of positions
 * enters or leaves at a time, each value added, or taken back out, in the order the walk meets it.
 */
final class RunningMoments {

    private final Double[] terms;
    private final boolean compensated;
    private final CompensatedSum compensatedSum = new CompensatedSum();
    private final CompensatedSum compensatedSquares = new CompensatedSum();
    private double plainSum;
    private double plainSquares;
    private int count;

    /**
     * @param terms each position's value, null where the row takes no part
     * @param compensated whether the sums add with Kahan compensation rather than plainly
     */
    RunningMoments(final Double[] terms, final boolean compensated) {
        this.terms = terms;
        this.compensated = compensated;
    }

    /**
     * Add the values of a run of positions.
     *
     * @param from the first position the walk meets
     * @param to the last, an empty run when it lies before {@code from} in the walk's direction
     * @param direction 1 when the walk runs forward, -1 when it runs back
     */
    void enter(final int from, final int to, final int direction) {
        for (int at = from; direction > 0 ? at <= to : at >= to; at += direction) {
            final Double term = terms[at];
            if (term != null) {
                final double value = term.doubleValue();
                add(value, value * value);
                count++;
            }
        }
    }

    /**
     * Take the values of a run of positions back out.
     *
     * @param from the first position the walk meets
     * @param to the last, an empty run when it lies before {@code from} in the walk's direction
     * @param direction 1 when the walk runs forward, -1 when it runs back
     */
    void leave(final int from, final int to, final int direction) {
        for (int at = from; direction > 0 ? at <= to : at >= to; at += direction) {
            final Double term = terms[at];
            if (term != null) {
                final double value = term.doubleValue();
                add(-value, -(value * value));
                count--;
            }
        }
    }

    private void add(final double value, final double square) {
        if (compensated) {
            compensatedSum.add(value);
            compensatedSquares.add(square);
        } else {
            plainSum += value;
            plainSquares += square;
        }
    }

    int count() {
        return count;
    }

    /** The running sum as it stands, its last compensation left unapplied. */
    double sum() {
        return compensated ? compensatedSum.value() : plainSum;
    }

    /** The running sum of squares as it stands. */
    double squares() {
        return compensated ? compensatedSquares.value() : plainSquares;
    }
}
