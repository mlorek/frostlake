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
import dev.frostlake.types.DecimalText;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for APPROX_PERCENTILE(expr, percentile). Snowflake computes an <em>approximate</em> percentile
 * from a t-digest sketch, which holds every value while there are few and is then read as the values
 * themselves: the exact interpolated percentile (identical to PERCENTILE_CONT). Past that the sketch is read as
 * a digest, each value a centroid whose mass sits around it, and the answer moves by half a position
 * ({@code APPROX_PERCENTILE(v, 0.25)} over 0 … 1400 is 349.75). Both readings are {@link PercentileDigest}'s,
 * which this accumulator hands its values. The percentile (a constant in [0, 1]) is supplied by the executor
 * from the function's second argument via {@link #setPercentile(double)}.
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
            values.add(DecimalText.parse(value.toString()).doubleValue());
        }
    }

    @Override
    public Object getResult() {
        if (values.isEmpty()) {
            return null;
        }
        final PercentileDigest digest = new PercentileDigest();
        for (final Double value : values) {
            digest.add(value.doubleValue());
        }
        return digest.estimate(percentile);
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
