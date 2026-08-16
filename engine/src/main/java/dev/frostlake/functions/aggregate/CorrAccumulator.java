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

/** Accumulator for {@link Corr}. */
public class CorrAccumulator implements AggregateFunction.Accumulator {
    public long n = 0;
    public double sumX = 0;
    public double sumY = 0;
    public double sumXX = 0;
    public double sumYY = 0;
    public double sumXY = 0;

    @Override
    public void accumulate(final Object v) {}

    public void accumulate(final double x, final double y) {
        n++;
        sumX += x;
        sumY += y;
        sumXX += x*x;
        sumYY += y*y;
        sumXY += x*y;
    }

    @Override
    public Object getResult() {
        if (n < 2) return null;
        final double num = n * sumXY - sumX * sumY;
        final double den = Math.sqrt((n * sumXX - sumX * sumX) * (n * sumYY - sumY * sumY));
        return den == 0 ? null : num / den;
    }

    @Override
    public void reset() {
        n = 0;
        sumX = sumY = sumXX = sumYY = sumXY = 0;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final CorrAccumulator o = (CorrAccumulator) other;
        n += o.n;
        sumX += o.sumX;
        sumY += o.sumY;
        sumXX += o.sumXX;
        sumYY += o.sumYY;
        sumXY += o.sumXY;
    }
}
