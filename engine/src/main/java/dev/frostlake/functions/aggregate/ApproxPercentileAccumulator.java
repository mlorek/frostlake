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

/**
 * Accumulator for APPROX_PERCENTILE(expr, percentile). Snowflake computes an <em>approximate</em> percentile
 * from a t-digest sketch; this emulator computes the exact interpolated percentile (identical to
 * PERCENTILE_CONT), which is the best possible approximation and matches Snowflake within its documented
 * error bound for typical data. The percentile (a constant in [0, 1]) is supplied by the executor from the
 * function's second argument via {@link #setPercentile(double)}.
 */
public class ApproxPercentileAccumulator implements AggregateFunction.Accumulator {

    private final List<Double> values = new ArrayList<>();
    private double percentile = 0.5;

    public void setPercentile(final double percentile) {
        this.percentile = percentile;
    }

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            values.add(new BigDecimal(value.toString()).doubleValue());
        }
    }

    @Override
    public Object getResult() {
        if (values.isEmpty()) {
            return null;
        }
        final List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        final double idx = percentile * (sorted.size() - 1);
        final int lo = (int) idx;
        final int hi = Math.min(lo + 1, sorted.size() - 1);
        return sorted.get(lo) * (1 - (idx - lo)) + sorted.get(hi) * (idx - lo);
    }

    @Override
    public void reset() {
        values.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        values.addAll(((ApproxPercentileAccumulator) other).values);
    }
}
