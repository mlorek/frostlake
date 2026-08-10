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

/** Accumulator for {@link StdDev}. */
public class StdDevAccumulator implements AggregateFunction.Accumulator {
    final List<Double> values = new ArrayList<>();

    @Override
    public void accumulate(final Object value) {
        if (value != null) values.add(((Number) value).doubleValue());
    }

    @Override
    public Object getResult() {
        // STDDEV and STDDEV_SAMP are SAMPLE statistics (live: STDDEV is an alias of
        // STDDEV_SAMP): denominator n-1, and fewer than two rows has no sample spread — NULL.
        // The population variant lives in StdDevPop, not here.
        if (values.size() < 2) return null;
        double sum = 0.0;
        for (final double v : values) sum += v;
        final double mean = sum / values.size();
        double varianceSum = 0.0;
        for (final double v : values) varianceSum += Math.pow(v - mean, 2);
        final double variance = varianceSum / (values.size() - 1);
        return Math.sqrt(variance);
    }

    @Override
    public void reset() { values.clear(); }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        values.addAll(((StdDevAccumulator) other).values);
    }
}
