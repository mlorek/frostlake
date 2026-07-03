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

public class CovarAccumulator implements AggregateFunction.Accumulator {
    private final boolean sample;
    public long n = 0;
    public double sumX = 0, sumY = 0, sumXY = 0;

    public CovarAccumulator(final boolean sample) { this.sample = sample; }

    @Override
    public void accumulate(final Object v) {}

    public void accumulate(final double x, final double y) {
        n++; sumX += x; sumY += y; sumXY += x * y;
    }

    @Override
    public Object getResult() {
        long denom = sample ? n - 1 : n;
        if (denom <= 0) return null;
        return (sumXY - sumX * sumY / n) / denom;
    }

    @Override
    public void reset() { n = 0; sumX = sumY = sumXY = 0; }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        CovarAccumulator o = (CovarAccumulator) other;
        n += o.n; sumX += o.sumX; sumY += o.sumY; sumXY += o.sumXY;
    }
}
