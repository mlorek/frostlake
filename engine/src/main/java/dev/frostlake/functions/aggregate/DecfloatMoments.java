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
 *
 * <p>The skewness and the kurtosis are computed as the account computes them: from the power sums, in DECFLOAT
 * arithmetic held to 38 digits at every step, around the correctly rounded mean, with the small-sample coefficients
 * {@code n / ((n - 1)(n - 2))} and {@code n(n + 1) / ((n - 1)(n - 2)(n - 3))} rounded to twelve fractional digits and
 * the kurtosis's {@code 3(n - 1)² / ((n - 2)(n - 3))} to six: SKEW over 1, 2, 4 and 8 is
 * 1.1376243669582576648487648196737920811 and KURTOSIS 0.757655954629954196597353497164461248, which the
 * exact statistics 1.13762436695768885… and 0.75765595463137996… differ from after the twelfth digit
 * (live-verified). A series that does not vary divides by its zero deviation.
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
     * The sample skewness, held as a DECFLOAT.
     *
     * @return the skewness, or null under three values
     */
    BigDecimal heldSkewness() {
        final int n = values.size();
        if (n < 3) {
            return null;
        }
        final DecfloatPowerSums sums = new DecfloatPowerSums(values);
        final BigDecimal deviation = Decfloats.held(sampleVariance(sums).sqrt(WORKING));
        final BigDecimal cubed = Decfloats.multiply(Decfloats.multiply(deviation, deviation), deviation);
        return Decfloats.divide(Decfloats.multiply(coefficient(n, (long) (n - 1) * (n - 2), 12),
            sums.centeredCubes()), cubed);
    }

    /**
     * The sample excess kurtosis, held as a DECFLOAT.
     *
     * @return the kurtosis, or null under four values
     */
    BigDecimal heldKurtosis() {
        final int n = values.size();
        if (n < 4) {
            return null;
        }
        final DecfloatPowerSums sums = new DecfloatPowerSums(values);
        final BigDecimal variance = sampleVariance(sums);
        final BigDecimal scaled = Decfloats.divide(Decfloats.multiply(
            coefficient((long) n * (n + 1), (long) (n - 1) * (n - 2) * (n - 3), 12), sums.centeredFourthPowers()),
            Decfloats.multiply(variance, variance));
        return Decfloats.subtract(scaled, coefficient(3L * (n - 1) * (n - 1), (long) (n - 2) * (n - 3), 6));
    }

    /** {@code (Σx² - n·mean²) / (n - 1)}, each step held to 38 digits. */
    private BigDecimal sampleVariance(final DecfloatPowerSums sums) {
        final BigDecimal count = BigDecimal.valueOf(values.size());
        final BigDecimal squares = Decfloats.subtract(sums.squares(),
            Decfloats.multiply(count, Decfloats.multiply(sums.mean(), sums.mean())));
        return Decfloats.divide(squares.signum() < 0 ? BigDecimal.ZERO : squares,
            BigDecimal.valueOf(values.size() - 1L));
    }

    /** A small-sample coefficient, a ratio of whole numbers rounded half up to its fractional digits. */
    private static BigDecimal coefficient(final long dividend, final long divisor, final int scale) {
        return BigDecimal.valueOf(dividend).divide(BigDecimal.valueOf(divisor), scale, RoundingMode.HALF_UP);
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
