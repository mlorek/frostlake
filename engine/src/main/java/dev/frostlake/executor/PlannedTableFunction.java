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

import dev.frostlake.storage.ResultSetColumn;

import java.util.List;

/**
 * A table-function call as the planner reads it: its arguments evaluated, its run deferred to a first request,
 * and the columns it will answer when the function can tell them without running.
 */
final class PlannedTableFunction {

    final String name;
    final String callText;
    final MemoizedResultSet result;
    final List<ResultSetColumn> columns;

    /**
     * @param name     the function's name
     * @param callText the call as written
     * @param result   the run, deferred and read once
     * @param columns  the columns the call answers, or null when only running the function can tell
     */
    PlannedTableFunction(final String name, final String callText, final MemoizedResultSet result,
                         final List<ResultSetColumn> columns) {
        this.name = name;
        this.callText = callText;
        this.result = result;
        this.columns = columns;
    }
}
