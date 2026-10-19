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

import dev.frostlake.values.Decfloats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The variance and the standard deviation of DECFLOAT values, each a DECFLOAT of 38 significant digits computed
 * exactly and rounded once: STDDEV over 1, 2 and 4 is 1.527525231651946668862682397909336163 and VAR_POP over them
 * 1.5555555555555555555555555555555555556 (live-verified).
 */
final class DecfloatMoments {

    /** The digits the moments are computed to before the one rounding to 38. */
    private static final MathContext WORKING = new MathContext(Decfloats.DIGITS + 22, RoundingMode.HALF_EVEN);

    private final List<BigDecimal> values = new ArrayList<>();

    /** Adds a value, a NULL skipped. */
    void add(final Object value) {
        if (value instanceof Number) {
            values.add(Decfloats.of((Number) value, "VARIANCE"));
        }
    }

    void merge(final DecfloatMoments other) {
        values.addAll(other.values);
    }

    void reset() {
        values.clear();
    }

    /**
     * The variance: the squared deviations from the mean over n - 1 for a sample, over n for a population.
     *
     * @param sample whether the sample variance is asked
     * @return the variance to the working digits, or null for too few values
     */
    private BigDecimal variance(final boolean sample) {
        final int n = values.size();
        if (n == 0 || sample && n < 2) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (final BigDecimal value : values) {
            sum = sum.add(value, WORKING);
        }
        final BigDecimal mean = sum.divide(BigDecimal.valueOf(n), WORKING);
        BigDecimal squares = BigDecimal.ZERO;
        for (final BigDecimal value : values) {
            final BigDecimal deviation = value.subtract(mean, WORKING);
            squares = squares.add(deviation.multiply(deviation, WORKING), WORKING);
        }
        return squares.divide(BigDecimal.valueOf(sample ? n - 1 : n), WORKING);
    }

    /**
     * The variance, held as a DECFLOAT.
     *
     * @param sample whether the sample variance is asked
     * @return the variance, or null for too few values
     */
    BigDecimal heldVariance(final boolean sample) {
        final BigDecimal variance = variance(sample);
        return variance == null ? null : Decfloats.held(variance);
    }

    /**
     * The standard deviation, the root of the variance, held as a DECFLOAT.
     *
     * @param sample whether the sample deviation is asked
     * @return the deviation, or null for too few values
     */
    BigDecimal heldDeviation(final boolean sample) {
        final BigDecimal variance = variance(sample);
        return variance == null ? null : Decfloats.held(variance.sqrt(WORKING));
    }
}
