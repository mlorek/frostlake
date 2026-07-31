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

import java.util.List;

/**
 * HASH_AGG(expr) — a single, order-INDEPENDENT signed 64-bit hash over the whole (multi)set of input rows.
 * Each row is hashed with the same FNV-1a used by the scalar HASH() function and the per-row hashes are
 * combined commutatively (summed), so the result depends only on the multiset of values, not the row order.
 *
 * <p>Like {@code HASH()}, this does NOT reproduce Snowflake's proprietary HASH_AGG values — rely on its
 * stability within this engine only.
 */
public class HashAgg extends AggregateFunction {
    public HashAgg() {
        super("HASH_AGG", NumericType.NUMBER);
    }

    @Override
    public Accumulator createAccumulator() {
        return new HashAggAccumulator();
    }

    /**
     * A GEOSPATIAL value is not hashable here, where an OBJECT is. Live, {@code HASH_AGG(o)}
     * over a populated OBJECT column returns a number while {@code HASH_AGG(g)} over a GEOGRAPHY is
     * "Invalid argument types for function 'HASH_AGG': (GEOGRAPHY)" (SQLSTATE 42P13) — so the
     * inherited default, which reads the two semi-structured answers and finds neither, would be wrong.
     * The SCALAR {@code HASH(g)} is deliberately untouched: it ACCEPTS a geo value live and returned
     * one hash per row.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
