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
import java.util.Collections;
import java.util.List;

/** Accumulator for {@link PercentileCont}. */
public class PercentileContAccumulator implements AggregateFunction.Accumulator {
    private final List<Double> values = new ArrayList<>();
    private final double percentile;

    public PercentileContAccumulator(final double p) { this.percentile = p; }

    @Override
    public void accumulate(final Object v) {
        if (v != null) values.add(new BigDecimal(v.toString()).doubleValue());
    }

    @Override
    public Object getResult() {
        if (values.isEmpty()) return null;
        final List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        final double idx = percentile * (sorted.size() - 1);
        final int lo = (int) idx;
        final int hi = Math.min(lo + 1, sorted.size() - 1);
        return sorted.get(lo) * (1 - (idx - lo)) + sorted.get(hi) * (idx - lo);
    }

    @Override
    public void reset() { values.clear(); }

    @Override
    public void merge(final AggregateFunction.Accumulator other) { values.addAll(((PercentileContAccumulator) other).values); }
}
