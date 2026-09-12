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
 * The count, the sum and the sum of squares of a FLOAT series — the moments the account's variance
 * family reads over FLOATs. VARIANCE is {@code (n·Σx² − (Σx)²) / (n·(n − 1))} and VAR_POP the same
 * numerator over {@code n²}; the STDDEV pair takes their roots. Each sum is added the way SUM adds a
 * FLOAT column (see {@link CompensatedSum}) and read back with its last compensation applied.
 *
 * <p>Live-verified to the thirty-seventh decimal on every series measured — GENERATOR rows, UNION ALL
 * branches, VALUES lists, a GROUP BY group and a window alike:
 *
 * <pre>
 *   ten copies of 0.1        VARIANCE 0.0000000000000000024671622769447924062  (a running mean gives 0)
 *   1e8, 1e8 + 1, 1e8 + 2    VARIANCE 0                                        (the squares cancel)
 *   0.1, 0.2                 VARIANCE 0.0049999999999999975019981945933977840
 *   0.3, 0.7                 VARIANCE 0.0799999999999998490096686509787105024
 *   0.7, 0.3                 VARIANCE 0.0799999999999999600319711134943645447  (the order is read)
 * </pre>
 *
 * <p>The sum of squares is the account's own {@code SUM(x * x)}: over 0.3 then 0.7 that reads
 * 0.5799999999999998490096686509787105024, and the variance above is exactly it less 0.5. What this
 * does not model is the partitioning of a stored table: ten copies of 0.1 read back from one give
 * twice the GENERATOR's variance, so a table's rows are not always met in the order they arrive here.
 */
public final class SquaredSumMoments {

    private long count;
    private final CompensatedSum sum = new CompensatedSum();
    private final CompensatedSum squares = new CompensatedSum();

    /**
     * Fold one value in.
     *
     * @param value the next value, in input order
     */
    public void add(final double value) {
        count++;
        sum.add(value);
        squares.add(value * value);
    }

    /**
     * Fold another series' moments in, its two sums added as terms.
     *
     * @param other the moments to absorb
     */
    public void merge(final SquaredSumMoments other) {
        if (other.count == 0) {
            return;
        }
        count += other.count;
        sum.add(other.sum.correctedValue());
        squares.add(other.squares.correctedValue());
    }

    /**
     * How many values were folded in.
     *
     * @return the count
     */
    public long count() {
        return count;
    }

    /**
     * The sample variance (denominator n − 1); the caller checks there are two rows at least.
     *
     * @return the variance, never negative
     */
    public double sampleVariance() {
        final double n = count;
        return Math.max(0.0, numerator() / (n * (n - 1)));
    }

    /**
     * The population variance (denominator n); the caller checks there is a row at least.
     *
     * @return the variance, never negative
     */
    public double populationVariance() {
        final double n = count;
        return Math.max(0.0, numerator() / (n * n));
    }

    /** {@code n·Σx² − (Σx)²}, the numerator both variances share. */
    private double numerator() {
        final double total = sum.correctedValue();
        return count * squares.correctedValue() - total * total;
    }

    /** Forget every value. */
    public void reset() {
        count = 0;
        sum.reset();
        squares.reset();
    }
}
