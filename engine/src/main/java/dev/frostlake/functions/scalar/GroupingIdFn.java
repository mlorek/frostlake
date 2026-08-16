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

package dev.frostlake.functions.scalar;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * GROUPING_ID(e1, …, en) — returns the integer bit-vector describing which of the grouping expressions
 * are aggregated over in the current row: each ei contributes one bit (1 when it is rolled up / not in
 * this grouping set, 0 when it is grouped by), with e1 as the most significant bit. Equivalent to
 * {@code GROUPING(e1, …, en)} for the same argument list.
 *
 * <p>In a plain GROUP BY context this evaluates to 0. The actual per-row mask for ROLLUP/CUBE/GROUPING
 * SETS super-group rows is computed in {@code GroupByAggregateEvaluator} (see {@code computeGroupingMask}),
 * which overrides this base result.
 */
public class GroupingIdFn extends BuiltInFunction {

    public GroupingIdFn() {
        super("GROUPING_ID", NumericType.INTEGER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // Base result outside a super-group expansion: 1 if the (first) argument was NULLed by a
        // ROLLUP/CUBE expansion, else 0. Super-group rows are resolved to the full bitmask upstream.
        if (args.isEmpty() || args.get(0) == null) return 1L;
        return 0L;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
