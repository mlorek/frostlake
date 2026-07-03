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
import dev.frostlake.types.NumericType;

import java.util.List;

public class Corr extends AggregateFunction {
    public Corr() { super("CORR", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new CorrAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }

    public static class CorrAccumulator implements Accumulator {
        public long n = 0;
        public double sumX = 0, sumY = 0, sumXX = 0, sumYY = 0, sumXY = 0;

        @Override
        public void accumulate(final Object v) {}

        public void accumulate(final double x, final double y) {
            n++; sumX += x; sumY += y; sumXX += x*x; sumYY += y*y; sumXY += x*y;
        }

        @Override
        public Object getResult() {
            if (n < 2) return null;
            double num = n * sumXY - sumX * sumY;
            double den = Math.sqrt((n * sumXX - sumX * sumX) * (n * sumYY - sumY * sumY));
            return den == 0 ? null : num / den;
        }

        @Override
        public void reset() { n = 0; sumX = sumY = sumXX = sumYY = sumXY = 0; }

        @Override
        public void merge(final Accumulator other) {
            CorrAccumulator o = (CorrAccumulator) other;
            n += o.n; sumX += o.sumX; sumY += o.sumY; sumXX += o.sumXX; sumYY += o.sumYY; sumXY += o.sumXY;
        }
    }
}
