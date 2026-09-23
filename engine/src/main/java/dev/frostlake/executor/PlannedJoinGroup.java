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

import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.metastore.model.Table;

/**
 * A parenthesized join group — {@code ( a JOIN b ON … )} — planned as a pipeline of its own: its shape is
 * known while planning, and its rows are read when the join it is the right side of runs.
 */
final class PlannedJoinGroup {

    final RowsProvider rows;
    final Table table;

    /**
     * @param rows  the group's rows, read when asked
     * @param table the group's combined shape
     */
    PlannedJoinGroup(final RowsProvider rows, final Table table) {
        this.rows = rows;
        this.table = table;
    }
}
