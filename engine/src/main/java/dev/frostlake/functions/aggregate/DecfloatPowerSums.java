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
import java.util.List;

/**
 * The power sums of DECFLOAT values, each held to 38 digits as it grows, and their correctly rounded mean, from which
 * {@link DecfloatMoments} computes the skewness and the kurtosis as the account does.
 */
final class DecfloatPowerSums {

    /** The digits the exact mean is computed to before its one rounding to 38. */
    private static final MathContext WORKING = new MathContext(Decfloats.DIGITS + 22, RoundingMode.HALF_EVEN);

    private final BigDecimal count;
    private BigDecimal first = BigDecimal.ZERO;
    private BigDecimal squares = BigDecimal.ZERO;
    private BigDecimal cubes = BigDecimal.ZERO;
    private BigDecimal fourthPowers = BigDecimal.ZERO;
    private final BigDecimal mean;

    /**
     * @param values the DECFLOAT values, at least one
     */
    DecfloatPowerSums(final List<BigDecimal> values) {
        BigDecimal exact = BigDecimal.ZERO;
        for (final BigDecimal value : values) {
            final BigDecimal square = Decfloats.multiply(value, value);
            first = Decfloats.add(first, value);
            squares = Decfloats.add(squares, square);
            cubes = Decfloats.add(cubes, Decfloats.multiply(square, value));
            fourthPowers = Decfloats.add(fourthPowers, Decfloats.multiply(square, square));
            exact = exact.add(value, WORKING);
        }
        count = BigDecimal.valueOf(values.size());
        mean = Decfloats.held(exact.divide(count, WORKING));
    }

    /** The correctly rounded mean. */
    BigDecimal mean() {
        return mean;
    }

    /** {@code Σx²}. */
    BigDecimal squares() {
        return squares;
    }

    /** {@code Σx³ - 3·mean·Σx² + 3·mean²·Σx - n·mean³}, each step held to 38 digits. */
    BigDecimal centeredCubes() {
        final BigDecimal meanSquared = Decfloats.multiply(mean, mean);
        final BigDecimal three = BigDecimal.valueOf(3);
        return Decfloats.subtract(Decfloats.add(
            Decfloats.subtract(cubes, Decfloats.multiply(Decfloats.multiply(three, mean), squares)),
            Decfloats.multiply(Decfloats.multiply(three, meanSquared), first)),
            Decfloats.multiply(count, Decfloats.multiply(meanSquared, mean)));
    }

    /** {@code Σx⁴ - 4·mean·Σx³ + 6·mean²·Σx² - 4·mean³·Σx + n·mean⁴}, each step held to 38 digits. */
    BigDecimal centeredFourthPowers() {
        final BigDecimal meanSquared = Decfloats.multiply(mean, mean);
        final BigDecimal meanCubed = Decfloats.multiply(meanSquared, mean);
        final BigDecimal four = BigDecimal.valueOf(4);
        return Decfloats.add(Decfloats.subtract(Decfloats.add(
            Decfloats.subtract(fourthPowers, Decfloats.multiply(Decfloats.multiply(four, mean), cubes)),
            Decfloats.multiply(Decfloats.multiply(BigDecimal.valueOf(6), meanSquared), squares)),
            Decfloats.multiply(Decfloats.multiply(four, meanCubed), first)),
            Decfloats.multiply(count, Decfloats.multiply(meanSquared, meanSquared)));
    }
}
