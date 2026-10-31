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

import dev.frostlake.executor.expressions.VariantNumbers;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The statistics of (y, x) pairs of which either side is a DECFLOAT, each a DECFLOAT of 38 significant digits computed
 * as the account computes it. The sums run in DECFLOAT arithmetic, every step held to 38 digits: COVAR_POP is
 * {@code (Σxy - Σx·Σy / n) / n}, COVAR_SAMP the same over n - 1, REGR_SXX {@code Σxx - Σx·Σx / n}
 * (and REGR_SYY, REGR_SXY alike), REGR_AVGX {@code Σx / n}. The slope divides COVAR_POP by VAR_POP of x,
 * CORR divides it by the product of the two STDDEV_POPs, both correctly rounded (see {@link DecfloatMoments}),
 * REGR_INTERCEPT is {@code avg(y) - slope·avg(x)} and REGR_R2 the square of CORR - 1 where y does not vary.
 * Over (1, 2), (2, 3), (4, 9), COVAR_POP is 3.7777777777777777777777777777777777777, REGR_SXX
 * 4.666666666666666666666666666666666667 and REGR_SLOPE of y on x 2.4285714285714285714285714285714285713
 * (live-verified).
 */
final class DecfloatPairStatistics {

    /** The first argument's values, null where the statistic does not read that side. */
    private final List<BigDecimal> ys = new ArrayList<>();

    /** The second argument's values, null where the statistic does not read that side. */
    private final List<BigDecimal> xs = new ArrayList<>();

    /**
     * Adds a pair neither side of which is NULL.
     *
     * @param y         the first argument's value, or null when the statistic does not read it
     * @param x         the second argument's value, or null when the statistic does not read it
     * @param operation the statistic, named by a conversion's refusal
     */
    void add(final Object y, final Object x, final String operation) {
        final BigDecimal first = y == null ? null : decfloatOf(y, operation);
        final BigDecimal second = x == null ? null : decfloatOf(x, operation);
        if (y != null && first == null || x != null && second == null) {
            // A VARIANT's JSON null is a missing value, as a NULL is.
            return;
        }
        ys.add(first);
        xs.add(second);
    }

    void merge(final DecfloatPairStatistics other) {
        ys.addAll(other.ys);
        xs.addAll(other.xs);
    }

    void reset() {
        ys.clear();
        xs.clear();
    }

    /** The number of pairs. */
    int count() {
        return xs.size();
    }

    /**
     * The covariance: over n pairs for the population, over n - 1 for a sample.
     *
     * @param sample whether the sample covariance is asked
     * @return the covariance, or null for too few pairs
     */
    BigDecimal covariance(final boolean sample) {
        final int n = count();
        if (n == 0 || sample && n < 2) {
            return null;
        }
        return Decfloats.divide(centered(xs, ys), BigDecimal.valueOf(sample ? n - 1 : n));
    }

    /** REGR_SXX: the centered sum of the squares of x, or null over no pair. */
    BigDecimal centeredSquaresOfX() {
        return count() == 0 ? null : centered(xs, xs);
    }

    /** REGR_SYY: the centered sum of the squares of y, or null over no pair. */
    BigDecimal centeredSquaresOfY() {
        return count() == 0 ? null : centered(ys, ys);
    }

    /** REGR_SXY: the centered sum of the products, or null over no pair. */
    BigDecimal centeredProducts() {
        return count() == 0 ? null : centered(xs, ys);
    }

    /** REGR_AVGX: the mean of x, or null over no pair. */
    BigDecimal averageOfX() {
        return count() == 0 ? null : Decfloats.divide(sum(xs), BigDecimal.valueOf(count()));
    }

    /** REGR_AVGY: the mean of y, or null over no pair. */
    BigDecimal averageOfY() {
        return count() == 0 ? null : Decfloats.divide(sum(ys), BigDecimal.valueOf(count()));
    }

    /** REGR_SLOPE: COVAR_POP over VAR_POP of x, or null when x does not vary. */
    BigDecimal slope() {
        if (count() == 0) {
            return null;
        }
        final BigDecimal varianceOfX = moments(xs).heldVariance(false);
        return varianceOfX.signum() == 0 ? null : Decfloats.divide(covariance(false), varianceOfX);
    }

    /** REGR_INTERCEPT: the mean of y less the slope times the mean of x, or null when x does not vary. */
    BigDecimal intercept() {
        final BigDecimal slope = slope();
        return slope == null ? null : Decfloats.subtract(averageOfY(), Decfloats.multiply(slope, averageOfX()));
    }

    /** CORR: COVAR_POP over the product of the two STDDEV_POPs, or null when either side does not vary. */
    BigDecimal correlation() {
        if (count() == 0) {
            return null;
        }
        final BigDecimal deviationOfX = moments(xs).heldDeviation(false);
        final BigDecimal deviationOfY = moments(ys).heldDeviation(false);
        if (deviationOfX.signum() == 0 || deviationOfY.signum() == 0) {
            return null;
        }
        return Decfloats.divide(covariance(false), Decfloats.multiply(deviationOfX, deviationOfY));
    }

    /** REGR_R2: NULL when x does not vary, 1 when y does not, else the square of CORR. */
    BigDecimal determination() {
        if (count() == 0 || moments(xs).heldVariance(false).signum() == 0) {
            return null;
        }
        if (moments(ys).heldVariance(false).signum() == 0) {
            return BigDecimal.ONE;
        }
        final BigDecimal correlation = correlation();
        return Decfloats.multiply(correlation, correlation);
    }

    /** {@code Σab - Σa·Σb / n}, each step held to 38 digits. */
    private BigDecimal centered(final List<BigDecimal> a, final List<BigDecimal> b) {
        BigDecimal products = BigDecimal.ZERO;
        for (int i = 0; i < a.size(); i++) {
            products = Decfloats.add(products, Decfloats.multiply(a.get(i), b.get(i)));
        }
        return Decfloats.subtract(products,
            Decfloats.divide(Decfloats.multiply(sum(a), sum(b)), BigDecimal.valueOf(a.size())));
    }

    private static BigDecimal sum(final List<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (final BigDecimal value : values) {
            total = Decfloats.add(total, value);
        }
        return total;
    }

    private static DecfloatMoments moments(final List<BigDecimal> values) {
        final DecfloatMoments moments = new DecfloatMoments();
        for (final BigDecimal value : values) {
            moments.add(value);
        }
        return moments;
    }

    /**
     * A pair's side as a DECFLOAT: a number as it is, a VARIANT through its member, a text as the cast reads it; null
     * for a VARIANT's JSON null.
     */
    private static BigDecimal decfloatOf(final Object value, final String operation) {
        if (value instanceof VariantValue) {
            final Number member = VariantNumbers.numberOf((VariantValue) value, VariantNumbers.REAL);
            return member == null ? null : Decfloats.of(member, operation);
        }
        if (value instanceof Number) {
            return Decfloats.of((Number) value, operation);
        }
        return Decfloats.of(Double.valueOf(NumericAggregateInput.asApproximate(value)), operation);
    }
}
