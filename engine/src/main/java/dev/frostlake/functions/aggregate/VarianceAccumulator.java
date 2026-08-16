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
import java.util.ArrayList;
import java.util.List;

/** Accumulator for {@link Variance}. */
public class VarianceAccumulator implements AggregateFunction.Accumulator {
    final List<Double> values = new ArrayList<>();

    @Override
    public void accumulate(final Object value) {
        if (value != null) values.add(((Number) value).doubleValue());
    }

    @Override
    public Object getResult() {
        // VARIANCE and VAR_SAMP are SAMPLE statistics (live: VARIANCE aliases VAR_SAMP):
        // denominator n-1, NULL under two rows. VarPop carries the population form.
        if (values.size() < 2) return null;
        double sum = 0.0;
        for (final double v : values) sum += v;
        final double mean = sum / values.size();
        double varianceSum = 0.0;
        for (final double v : values) varianceSum += Math.pow(v - mean, 2);
        return varianceSum / (values.size() - 1);
    }

    @Override
    public void reset() { values.clear(); }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        values.addAll(((VarianceAccumulator) other).values);
    }
}
