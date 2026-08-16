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
 * A running sum of doubles with Kahan compensation, which is the closest single rule to how the account
 * adds them.
 *
 * <p>Naive left-to-right addition is NOT what live does, and the cells that show it are cheap to
 * reproduce: ten copies of {@code 0.1::FLOAT} sum to exactly {@code 1} there — {@code SUM(f) = 1} is
 * TRUE — where naive addition gives {@code 0.9999999999999999}; seven tenths are
 * {@code 0.70000000000000006661} where naive addition gives {@code 0.69999999999999995559}; and a
 * hundred or a thousand tenths are exactly {@code 10} and {@code 100}. Compensated summation reproduces
 * every one of those, the eleven-tenths cell, the averages that follow from them, and both cancellation
 * sets ({@code 1e16 + 1 + 1 - 1e16} is {@code 2}, {@code 1e16 + 1 - 1e16} is {@code 0}), while an exact
 * decimal sum rounded once does not.
 *
 * <p>It is a model, not the account's code: the account's answer also depends on how the rows are
 * partitioned, and one measured shape — six tenths read back from a table — comes out as naive
 * addition would have it. The sum starts from a positive zero, as IEEE addition does, so a lone
 * {@code -0.0} sums to {@code 0}.
 */
public final class CompensatedSum {

    private double sum;
    private double compensation;
    private boolean seeded;

    /**
     * Add one term.
     *
     * @param term the next value, in input order
     */
    public void add(final double term) {
        if (!seeded) {
            // The first term seeds the sum, a signed zero included: live's SUM over a lone -0.0 is -0,
            // where a running total started at +0.0 would have lost the sign on the first addition.
            seeded = true;
            sum = term;
            compensation = 0.0;
            return;
        }
        if (term == 0.0) {
            // A zero adds nothing and moves no sign (live: -0.0 then 0.0 sums to -0, 0.0 then -0.0
            // to 0), which IEEE addition would not keep — -0.0 + 0.0 is +0.0.
            return;
        }
        final double corrected = term - compensation;
        final double next = sum + corrected;
        compensation = (next - sum) - corrected;
        sum = next;
    }

    /** Forget every term. */
    public void reset() {
        sum = 0.0;
        compensation = 0.0;
        seeded = false;
    }

    /**
     * Whether any term has been added.
     *
     * @return true after the first term
     */
    public boolean isSeeded() {
        return seeded;
    }

    /**
     * The sum so far.
     *
     * @return the compensated sum, or {@code 0.0} before any term
     */
    public double value() {
        return sum;
    }

    /**
     * The sum so far with its last compensation applied: the rounding error of the latest addition,
     * which {@link #value} carries forward unapplied, taken back out. SUM and AVG read an aggregate's
     * sum this way, a running window reading {@link #value} instead (see {@link PartialFloatSum}), and
     * the variance family reads its sums this way too (see {@link SquaredSumMoments}) — over 0.3 then
     * 0.7 squared this is 0.57999999999999984901 where {@link #value} is 0.57999999999999996003.
     *
     * @return the corrected sum, or {@code 0.0} before any term
     */
    public double correctedValue() {
        return sum - compensation;
    }
}
