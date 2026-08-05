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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Skew extends AggregateFunction {
    public Skew() { super("SKEW", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new SkewAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    /** Live: "Invalid argument types for function '*': (OBJECT, OBJECT)" — see {@link StdDev}. */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.MULTIPLY_OPERANDS;
    }

    private static class SkewAccumulator implements Accumulator {
        private final List<Double> values = new ArrayList<>();

        @Override
        public void accumulate(final Object v) {
            if (v != null) values.add(new BigDecimal(v.toString()).doubleValue());
        }

        @Override
        public Object getResult() {
            int n = values.size();
            if (n < 3) return null;
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = values.stream().mapToDouble((final var d) -> Math.pow(d - mean, 2)).sum() / (n - 1);
            if (variance == 0) return 0.0;
            double stddev = Math.sqrt(variance);
            double sum3 = values.stream().mapToDouble((final var d) -> Math.pow((d - mean) / stddev, 3)).sum();
            return sum3 * n / ((n - 1.0) * (n - 2.0));
        }

        @Override
        public void reset() { values.clear(); }

        @Override
        public void merge(final Accumulator other) { values.addAll(((SkewAccumulator) other).values); }
    }
}
