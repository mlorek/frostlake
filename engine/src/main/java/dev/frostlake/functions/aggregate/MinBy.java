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
 * MIN_BY(value, sort_key) — a two-argument aggregate returning the {@code value} from the row whose
 * {@code sort_key} is the minimum. Rows with a NULL sort key are ignored; on ties the latest such row wins
 * (Snowflake semantics). The returned {@code value} may itself be NULL, and NULL only signals "no qualifying
 * row" when the group is empty or every sort key is NULL.
 */
public class MinBy extends AggregateFunction {
    public MinBy() {
        super("MIN_BY", VariantType.VARIANT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new MaxByMinByAccumulator(false);
    }

    @Override
    public Object evaluate(final List<Object> args) { return null; }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
