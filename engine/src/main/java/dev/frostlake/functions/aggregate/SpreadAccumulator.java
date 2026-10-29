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

import dev.frostlake.functions.AggregateFunction;

/**
 * Accumulator for the variance and the standard deviation, of a sample or of a whole population: {@link Variance}
 * and {@link VarSamp}, {@link VarPop}, {@link StdDev} and {@link StdDevSamp}, {@link StdDevPop}. The sample
 * statistics divide by n-1, so fewer than two rows have no sample spread and answer NULL (live: VARIANCE is an alias
 * of VAR_SAMP, STDDEV of STDDEV_SAMP); the population ones answer from one row on. A standard deviation is the ROOT
 * OF THE ROUNDED variance, not of the raw one — see {@link AggregateNumerics#rootOfDeclaredVariance}.
 */
public class SpreadAccumulator
        implements AggregateFunction.Accumulator, ApproximateAwareAccumulator, DecfloatAwareAccumulator {

    /** The values of a DECFLOAT argument, whose statistic is a DECFLOAT of 38 digits (see DecfloatMoments). */
    private final DecfloatMoments decfloatMoments = new DecfloatMoments();

    private boolean decfloat;

    private final ExactMoments moments = new ExactMoments();
    private final SquaredSumMoments floatMoments = new SquaredSumMoments();
    private int maxScale;
    private boolean approximate;

    /** Whether the statistic is of a sample, over n-1, rather than of the whole population. */
    private final boolean sample;

    /** Whether the statistic is the standard deviation, the root of the variance. */
    private final boolean deviation;

    /**
     * @param sample whether the statistic is of a sample rather than of the whole population
     * @param deviation whether it is the standard deviation rather than the variance
     */
    public SpreadAccumulator(final boolean sample, final boolean deviation) {
        this.sample = sample;
        this.deviation = deviation;
    }

    /**
     * The argument's DECLARED type, which the values alone cannot say: a FLOAT column is stored
     * exactly here, so its 2 and a NUMBER(1,0)'s 2 arrive as the same object and only this flag
     * separates them. Without it a FLOAT input was scaled as an exact number — live's 2.333333333
     * came back 2.333333.
     *
     * @param approximate whether the aggregated expression is declared FLOAT / DOUBLE / REAL
     */
    @Override
    public void setApproximateArgument(final boolean approximate) {
        this.approximate = this.approximate || approximate;
    }

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public void accumulate(final Object value) {
        if (decfloat) {
            decfloatMoments.add(value);
            return;
        }
        if (value != null) {
            // The input's SCALE decides the declared scale the result is presented at, so it has to be
            // read before the value becomes a double — see AggregateNumerics.varianceAtDeclaredScale.
            maxScale = Math.max(maxScale, AggregateNumerics.scaleOfInput(value));
            approximate = approximate || AggregateNumerics.isApproximateInput(value);
            final double number = NumericAggregateInput.asDouble(value);
            moments.add(value);
            floatMoments.add(number);
        }
    }

    @Override
    public Object getResult() {
        if (decfloat) {
            return deviation ? decfloatMoments.heldDeviation(sample) : decfloatMoments.heldVariance(sample);
        }
        if (moments.count() < (sample ? 2 : 1)) {
            return null;
        }
        // A FLOAT input is read through the account's sums, an exact one exactly.
        if (approximate || !moments.isExact()) {
            final double variance = sample ? floatMoments.sampleVariance() : floatMoments.populationVariance();
            if (deviation) {
                return AggregateNumerics.rootOfDeclaredVariance(variance, maxScale, approximate);
            }
            return AggregateNumerics.varianceAtDeclaredScale(variance, maxScale, approximate);
        }
        if (deviation) {
            return AggregateNumerics.rootOfExactVariance(moments, sample, maxScale);
        }
        return AggregateNumerics.exactVarianceAtDeclaredScale(moments, sample, maxScale);
    }

    @Override
    public void reset() {
        decfloatMoments.reset();
        moments.reset();
        floatMoments.reset();
        maxScale = 0;
        approximate = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final SpreadAccumulator o = (SpreadAccumulator) other;
        decfloatMoments.merge(o.decfloatMoments);
        moments.merge(o.moments);
        floatMoments.merge(o.floatMoments);
        maxScale = Math.max(maxScale, o.maxScale);
        approximate = approximate || o.approximate;
    }
}
