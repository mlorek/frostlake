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
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * MAX_BY(value, sort_key) — a two-argument aggregate returning the {@code value} from the row whose
 * {@code sort_key} is the maximum. Rows with a NULL sort key are ignored; on ties the latest such row wins
 * (Snowflake semantics). The returned {@code value} may itself be NULL, and NULL only signals "no qualifying
 * row" when the group is empty or every sort key is NULL.
 */
public class MaxBy extends AggregateFunction {
    public MaxBy() {
        super("MAX_BY", VariantType.VARIANT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new MaxByMinByAccumulator(true);
    }

    /**
     * Neither half takes a GEOSPATIAL value, where both take an OBJECT. Live,
     * {@code MAX_BY(o, n)} returns the object while {@code MAX_BY(g, n)} is "Invalid argument types for
     * function 'MAX_BY': (GEOGRAPHY, NUMBER(38,0))" and {@code MIN_BY(n, g)} names the SORT-KEY half —
     * so the answer is the same for every position rather than only the value one.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
