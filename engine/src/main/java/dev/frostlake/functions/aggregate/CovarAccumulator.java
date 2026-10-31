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

public class CovarAccumulator implements AggregateFunction.Accumulator, PairStatisticAccumulator {
    /** The pairs of a DECFLOAT argument, whose covariance is a DECFLOAT of 38 digits (see DecfloatPairStatistics). */
    private final DecfloatPairStatistics decfloatPairs = new DecfloatPairStatistics();
    private boolean decfloat;
    private final boolean sample;
    public long n = 0;
    public double sumX = 0;
    public double sumY = 0;
    public double sumXY = 0;

    public CovarAccumulator(final boolean sample) { this.sample = sample; }

    @Override
    public void accumulate(final Object v) {}

    public void accumulate(final double x, final double y) {
        n++;
        sumX += x;
        sumY += y;
        sumXY += x * y;
    }

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public boolean isDecfloat() {
        return decfloat;
    }

    @Override
    public void accumulateDecfloat(final Object y, final Object x) {
        decfloatPairs.add(y, x, sample ? "COVAR_SAMP" : "COVAR_POP");
    }

    @Override
    public Object getResult() {
        if (decfloat) {
            return decfloatPairs.covariance(sample);
        }
        final long denom = sample ? n - 1 : n;
        if (denom <= 0) return null;
        return (sumXY - sumX * sumY / n) / denom;
    }

    @Override
    public void reset() {
        decfloatPairs.reset();
        n = 0;
        sumX = sumY = sumXY = 0;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final CovarAccumulator o = (CovarAccumulator) other;
        decfloatPairs.merge(o.decfloatPairs);
        n += o.n;
        sumX += o.sumX;
        sumY += o.sumY;
        sumXY += o.sumXY;
    }
}
