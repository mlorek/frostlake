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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.NumericType;

import java.util.ArrayList;
import java.util.List;

public class Variance extends AggregateFunction {
    public Variance() {
        super("VARIANCE", NumericType.DOUBLE);
    }

    @Override
    public Accumulator createAccumulator() {
        return new VarianceAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    /** Live: "Invalid argument types for function '*': (OBJECT, OBJECT)" — see {@link StdDev}. */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }

    public static class VarianceAccumulator implements Accumulator {
        final List<Double> values = new ArrayList<>();

        @Override
        public void accumulate(final Object value) {
            if (value != null) values.add(((Number) value).doubleValue());
        }

        @Override
        public Object getResult() {
            if (values.isEmpty()) return null;
            double sum = 0.0;
            for (final double v : values) sum += v;
            double mean = sum / values.size();
            double varianceSum = 0.0;
            for (final double v : values) varianceSum += Math.pow(v - mean, 2);
            return varianceSum / values.size();
        }

        @Override
        public void reset() { values.clear(); }

        @Override
        public void merge(final Accumulator other) {
            values.addAll(((VarianceAccumulator) other).values);
        }
    }
}
