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

package dev.frostlake.executor.operators;

import dev.frostlake.storage.Row;
import java.util.List;

/**
 * Interface for evaluating a SELECT-list item (aggregate or grouped projection) on a group of rows,
 * identified by its position in the operator's select-expression list (not by text).
 */
public interface AggregateEvaluator {
    /**
     * Evaluate the select item at the given index on a group of rows.
     *
     * @param selectItemIndex index into the operator's select-expression list
     * @param rows The group of rows to aggregate
     * @return The result of the aggregation
     */
    Object evaluate(final int selectItemIndex, final List<Row> rows);
}
