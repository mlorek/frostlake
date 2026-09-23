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

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final int limit = TaskGraphRuns.limit(namedArgs.get("RESULT_LIMIT"), "COMPLETE_TASK_GRAPHS");
        final String rootTaskName = TaskGraphRuns.text(namedArgs.get("ROOT_TASK_NAME"));
        final boolean errorOnly = TaskGraphRuns.flag(namedArgs.get("ERROR_ONLY"), false);
        return new ResultSet(TaskGraphRuns.columns(true),
            TaskGraphRuns.complete(catalog, rootTaskName, errorOnly, limit));
    }
}
