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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Accumulator for {@link Skew}. */
public class SkewAccumulator implements AggregateFunction.Accumulator {
    private final List<Double> values = new ArrayList<>();

    @Override
    public void accumulate(final Object v) {
        if (v != null) values.add(new BigDecimal(v.toString()).doubleValue());
    }

    @Override
    public Object getResult() {
        final int n = values.size();
        if (n < 3) return null;
        double total = 0;
        for (final Double value : values) {
            total += value.doubleValue();
        }
        final double mean = total / n;
        double squares = 0;
        for (final Double value : values) {
            squares += Math.pow(value.doubleValue() - mean, 2);
        }
        final double variance = squares / (n - 1);
        if (variance == 0) return 0.0;
        final double stddev = Math.sqrt(variance);
        double sum3 = 0;
        for (final Double value : values) {
            sum3 += Math.pow((value.doubleValue() - mean) / stddev, 3);
        }
        return sum3 * n / ((n - 1.0) * (n - 2.0));
    }

    @Override
    public void reset() { values.clear(); }

    @Override
    public void merge(final AggregateFunction.Accumulator other) { values.addAll(((SkewAccumulator) other).values); }
}
