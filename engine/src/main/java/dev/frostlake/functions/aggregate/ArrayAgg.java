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
import dev.frostlake.types.ArrayType;

import java.util.List;

public class ArrayAgg extends AggregateFunction {
    public ArrayAgg() {
        super("ARRAY_AGG", ArrayType.ARRAY);
    }

    /**
     * A plain OBJECT or ARRAY collects perfectly well, a STRUCTURED one does not. Live over
     * one table carrying both, {@code ARRAY_AGG(o)} returns
     * {@code [{"k":"v1"},{"k":"v2"}]} while {@code ARRAY_AGG(so)} is "Invalid argument types for
     * function 'ARRAY_AGG': (OBJECT(x VARCHAR(16777216)))" — and the same for
     * {@code ARRAY_AGG(sa)} / {@code ARRAY_AGG(sm)}, for {@code DISTINCT}, for
     * {@code WITHIN GROUP (ORDER BY …)}, for the windowed {@code OVER ()} form, under {@code GROUP BY}
     * and on an EMPTY input. Only the aggregated VALUE is constrained: {@code ARRAY_AGG(n) WITHIN
     * GROUP (ORDER BY so)} sorts by a structured key quite happily.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Accumulator createAccumulator() {
        return new ArrayAggAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
