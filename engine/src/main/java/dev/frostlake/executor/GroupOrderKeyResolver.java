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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeParser;

/**
 * Resolves the sort value of an ORDER BY item that could NOT be matched to a SELECT column after GROUP BY /
 * aggregation — Snowflake allows ordering by a grouped column or an aggregate that is not in the SELECT list
 * (e.g. {@code SELECT grp FROM t GROUP BY grp, x ORDER BY x} or {@code … ORDER BY MAX(y)}). The value is
 * computed over the source rows of the output row's group, using its ORIGINAL (pre-sort) index.
 */
public interface GroupOrderKeyResolver {

    /**
     * The ORDER BY value for the output row at {@code rowIndex} (its original position before sorting),
     * computed over that row's group.
     */
    Object resolve(int rowIndex, FrostlakeParser.OrderItemContext item);
}
