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

/** Accumulator for {@link StdDevPop}. */
public class StdDevPopAccumulator
        implements AggregateFunction.Accumulator, ApproximateAwareAccumulator {
    private final WelfordMoments moments = new WelfordMoments();
    private final SquaredSumMoments floatMoments = new SquaredSumMoments();
    private int maxScale;
    private boolean approximate;

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
    public void accumulate(final Object v) {
        if (v == null) return;
        final double number = NumericAggregateInput.asDouble(v);
        moments.add(number);
        floatMoments.add(number);
        maxScale = Math.max(maxScale, AggregateNumerics.scaleOfInput(v));
        approximate = approximate || AggregateNumerics.isApproximateInput(v);
    }

    @Override
    public Object getResult() {
        if (moments.count() == 0) return null;
        // A FLOAT input is read through the account's sums, an exact one through the running mean.
        final double variance = approximate ? floatMoments.populationVariance() : moments.populationVariance();
        // The ROOT OF THE ROUNDED variance, as in StdDevAccumulator.
        return AggregateNumerics.rootOfDeclaredVariance(variance, maxScale, approximate);
    }

    @Override
    public void reset() {
        moments.reset();
        floatMoments.reset();
        maxScale = 0;
        approximate = false;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final StdDevPopAccumulator o = (StdDevPopAccumulator) other;
        moments.merge(o.moments);
        floatMoments.merge(o.floatMoments);
        maxScale = Math.max(maxScale, o.maxScale);
        approximate = approximate || o.approximate;
    }
}
