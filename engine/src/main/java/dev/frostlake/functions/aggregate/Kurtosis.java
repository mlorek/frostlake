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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Kurtosis extends AggregateFunction {
    public Kurtosis() { super("KURTOSIS", NumericType.DOUBLE); }

    @Override
    public Accumulator createAccumulator() { return new KurtosisAccumulator(); }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }

    private static class KurtosisAccumulator implements Accumulator {
        private final List<Double> values = new ArrayList<>();

        @Override
        public void accumulate(final Object v) {
            if (v != null) values.add(new BigDecimal(v.toString()).doubleValue());
        }

        @Override
        public Object getResult() {
            int n = values.size();
            if (n < 4) return null;
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = values.stream().mapToDouble((final var d) -> Math.pow(d - mean, 2)).sum() / (n - 1);
            if (variance == 0) return null;
            double stddev = Math.sqrt(variance);
            double sum4 = values.stream().mapToDouble((final var d) -> Math.pow((d - mean) / stddev, 4)).sum();
            return (double) n * (n + 1) / ((n - 1) * (n - 2) * (n - 3)) * sum4
                - 3.0 * (n - 1) * (n - 1) / ((n - 2) * (n - 3));
        }

        @Override
        public void reset() { values.clear(); }

        @Override
        public void merge(final Accumulator other) { values.addAll(((KurtosisAccumulator) other).values); }
    }
}
