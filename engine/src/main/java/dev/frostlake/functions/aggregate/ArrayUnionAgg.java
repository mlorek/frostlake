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
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * ARRAY_UNION_AGG(array) — an aggregate whose every input row is itself an ARRAY. The result is the union
 * of all those arrays with duplicate elements removed, preserving first-seen order. NULL and non-array
 * inputs contribute nothing. Mirrors Snowflake's ARRAY_UNION_AGG.
 */
public class ArrayUnionAgg extends AggregateFunction {
    public ArrayUnionAgg() {
        super("ARRAY_UNION_AGG", VariantType.VARIANT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new ArrayUnionAggAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
