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

import dev.frostlake.storage.Row;

/**
 * The value of an expression written over GROUPED rows, computed over the row's own source group.
 *
 * <p>A window stage that runs after grouping sees one row per group, and those rows no longer carry the
 * values an aggregate would need: {@code ROW_NUMBER() OVER (ORDER BY SUM(b))} has to know each group's
 * SUM(b), and {@code LAG(SUM(b)) OVER (…)} has to know it as an ARGUMENT. Where the aggregate is also a
 * SELECT item its computed value can be read positionally, but it need not be — and then the only place
 * left to compute it is the group the output row came from.
 *
 * <p>Implemented by the query executor, which holds the output-row-to-group map; the window evaluator
 * asks through this interface so it never has to know how grouping stored its rows.
 */
public interface GroupedExpressionValues {

    /** Answered when the expression cannot be computed over a group, so the caller keeps its own path. */
    Object UNRESOLVED = new Object();

    /**
     * The expression's value for one grouped output row.
     *
     * @param projectedRow   the output row, one per group
     * @param expressionText the expression as written
     * @return the value, or {@link #UNRESOLVED} when this row has no group or the text does not compute
     */
    Object valueOf(Row projectedRow, String expressionText);
}
