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

/**
 * ARRAY_UNIQUE_AGG(expr) — an aggregate that collects the DISTINCT non-NULL scalar input values (each
 * input row contributes a single element, not an array) into a VARIANT ARRAY. Mirrors Snowflake's
 * ARRAY_UNIQUE_AGG.
 */
public class ArrayUniqueAgg extends AggregateFunction {
    public ArrayUniqueAgg() {
        super("ARRAY_UNIQUE_AGG", ArrayType.ARRAY);
    }

    /**
     * The same divergence {@code ARRAY_AGG} has: live, {@code ARRAY_UNIQUE_AGG(o)} and
     * {@code ARRAY_UNIQUE_AGG(a)} collect their values while {@code ARRAY_UNIQUE_AGG(so)},
     * {@code (sa)} and {@code (sm)} are "Invalid argument types for function 'ARRAY_UNIQUE_AGG'"
     * naming the whole structured type.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Accumulator createAccumulator() {
        return new ArrayUniqueAggAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
