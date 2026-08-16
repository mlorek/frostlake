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

/**
 * The double a FLOAT SUM or AVG answers, built from PARTIALS: each partial is a run of terms added with
 * Kahan compensation and read back with its last compensation applied
 * ({@link CompensatedSum#correctedValue}), and the partials are added in turn to a running compensated sum
 * that is read without it ({@link CompensatedSum#value}).
 *
 * <p>The account's shapes are three uses of that one rule (all live-verified):
 *
 * <pre>
 *   an aggregate, a whole-partition window     one partial holding every row     the corrected sum
 *   a running or sliding window frame          every row a partial of its own    the running sum
 *   a cumulative RANGE frame                   every peer group a partial        corrected within a group
 * </pre>
 *
 * <p>Over 0.3 then 0.7 squared the corrected sum is 0.57999999999999984901 and the running one
 * 0.57999999999999996003: {@code SUM(x * x)} and {@code SUM(x * x) OVER ()} answer the first,
 * {@code SUM(x * x) OVER (ORDER BY i)} answers the second on its last row, and {@code OVER (ORDER BY k)}
 * answers the first again on both rows when they share k.
 */
public final class PartialFloatSum {

    private final CompensatedSum running = new CompensatedSum();
    private final CompensatedSum partial = new CompensatedSum();

    /**
     * Add a term to the current partial.
     *
     * @param term the next value, in input order
     */
    public void add(final double term) {
        partial.add(term);
    }

    /** Close the current partial: its corrected sum joins the running sum. A partial with no term adds nothing. */
    public void endPartial() {
        if (partial.isSeeded()) {
            running.add(partial.correctedValue());
            partial.reset();
        }
    }

    /**
     * The running sum of every partial, the current one closed first.
     *
     * @return the sum, or {@code 0.0} before any term
     */
    public double result() {
        endPartial();
        return running.value();
    }
}
