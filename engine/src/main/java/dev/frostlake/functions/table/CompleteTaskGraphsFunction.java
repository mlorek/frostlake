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

package dev.frostlake.functions.table;

import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

import java.util.List;
import java.util.Map;

/**
 * The INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS([RESULT_LIMIT =&gt; n] [, ROOT_TASK_NAME =&gt; '...']
 * [, ERROR_ONLY =&gt; TRUE | FALSE]) table function: the graph runs that completed, newest completion first;
 * ERROR_ONLY =&gt; TRUE keeps the failed ones. ROOT_TASK_NAME is a root task's unqualified name, matched
 * case-insensitively in every schema. See {@link TaskGraphRuns}.
 */
public class CompleteTaskGraphsFunction extends TableFunction {

    private final Catalog catalog;

    public CompleteTaskGraphsFunction(final Catalog catalog) {
        super("COMPLETE_TASK_GRAPHS");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute.
    }

    /** The listing's columns are fixed, so a query over it is planned with their types before any row is read. */
    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return TaskGraphRuns.columns(true);
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        // A limit of zero still answers the latest graph run (live-verified), where the other task history
        // functions answer no row.
        final int limit = Math.max(1, TaskGraphRuns.limit(namedArgs.get("RESULT_LIMIT"), TaskGraphRuns.DEFAULT_LIMIT));
        final String rootTaskName = TaskGraphRuns.text(namedArgs.get("ROOT_TASK_NAME"));
        final boolean errorOnly = TaskGraphRuns.flag(namedArgs.get("ERROR_ONLY"), false);
        return new ResultSet(TaskGraphRuns.columns(true),
            TaskGraphRuns.complete(catalog, rootTaskName, errorOnly, limit));
    }
}
