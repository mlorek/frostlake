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
import dev.frostlake.types.IntegerResultWidths;

import java.util.List;

/**
 * GROUPING(col) — returns 1 if the column is aggregated (NULL due to ROLLUP/CUBE),
 * 0 if the column participates in grouping for this row.
 * In plain GROUP BY context this always returns 0.
 * The actual 0/1 logic for ROLLUP/CUBE rows is handled in QueryExecutor
 * by examining whether the value was set to NULL by a super-group expansion.
 */
public class GroupingFn extends BuiltInFunction {

    public GroupingFn() {
        super("GROUPING", IntegerResultWidths.COUNTER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        // If the argument is NULL (set by ROLLUP/CUBE expansion) → 1
        // otherwise → 0
        if (args.isEmpty() || args.get(0) == null) return 1L;
        return 0L;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
