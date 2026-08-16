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

/** The accumulator of {@link ApproxPercentileAccumulate}: one centroid of weight 1 per non-NULL value. */
public class PercentileDigestAccumulator implements AggregateFunction.Accumulator {

    private PercentileDigest digest = new PercentileDigest();

    @Override
    public void accumulate(final Object value) {
        if (value != null) {
            digest.add(PercentileDigest.numericInput(value));
        }
    }

    @Override
    public Object getResult() {
        return digest.toState();
    }

    @Override
    public void reset() {
        digest = new PercentileDigest();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        digest.absorb(((PercentileDigestAccumulator) other).digest);
    }
}
