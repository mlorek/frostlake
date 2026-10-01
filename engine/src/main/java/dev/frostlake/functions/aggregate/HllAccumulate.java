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
import dev.frostlake.types.BinaryType;

import java.util.List;

/**
 * {@code HLL_ACCUMULATE([DISTINCT] expr [, expr …])} — the HyperLogLog state of the group's distinct
 * non-NULL values, as a BINARY. It counts what {@code APPROX_COUNT_DISTINCT} counts, tuples and all, and
 * {@code HLL_ESTIMATE} over its state answers exactly what {@code APPROX_COUNT_DISTINCT} answers, as on the
 * account. A group with no value at all is an EMPTY binary, whose estimate is 0 (live-verified).
 */
public class HllAccumulate extends AggregateFunction {

    /** Registers the aggregate as {@code HLL_ACCUMULATE} returning BINARY. */
    public HllAccumulate() {
        super("HLL_ACCUMULATE", BinaryType.UNSIZED);
    }

    @Override
    public Accumulator createAccumulator() {
        return new HllStateAccumulator();
    }

    /** A GEOSPATIAL value is no more countable here than in {@code APPROX_COUNT_DISTINCT}. */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return Integer.MAX_VALUE;
    }
}
