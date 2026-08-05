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

public class ApproxCountDistinct extends AggregateFunction {
    public ApproxCountDistinct() { super("APPROX_COUNT_DISTINCT", NumericType.BIGINT); }

    @Override
    public Accumulator createAccumulator() { return new HllAccumulator(); }

    /**
     * The sketch does not accumulate a GEOSPATIAL value, where it accumulates an OBJECT. Live
     * {@code APPROX_COUNT_DISTINCT(o)} returns 2 while {@code APPROX_COUNT_DISTINCT(g)} and
     * its {@code HLL(g)} synonym — the same registered object here — are argument-type errors
     * (SQLSTATE 42P13). {@code COUNT(DISTINCT g)} is NOT affected and returned 2 live: only the
     * approximate counter refuses.
     *
     * <p>Live names 'HLL_ACCUMULATE' rather than the function written, an internal desugaring
     * Frostlake does not reproduce for the same reason it reports 'AVG' where live reports 'SUM' — the
     * engine has no such plan to name.
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
