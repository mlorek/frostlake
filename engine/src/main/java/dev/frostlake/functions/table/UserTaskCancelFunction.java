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
import dev.frostlake.storage.Row;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.task.UserTaskCancellation;
import dev.frostlake.types.StringType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@code SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('task_name')} — cancels the runs of a task that
 * are in flight and returns a one-column {@code status} row describing what happened.
 *
 * <p>It cancels <em>executions</em>, not the task: a started task stays started afterwards. The
 * task name is echoed into the message exactly as it was passed, while the task itself is looked up
 * case-insensitively. An unknown name is an error rather than a status row.
 */
public class UserTaskCancelFunction extends TableFunction {

    private final Catalog catalog;

    /** Set once the scheduler exists; it is built after the function registry. */
    private TaskScheduler taskScheduler;

    public UserTaskCancelFunction(final Catalog catalog) {
        super("SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS");
        this.catalog = catalog;
    }

    public void setTaskScheduler(final TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // positional arg handled separately
    }

    @Override
    public ResultSet execute(final Map<String, Object> args) {
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("status", StringType.VARCHAR)
        );

        Object taskNameArg = args.get("INPUT");
        if (taskNameArg == null) {
            taskNameArg = args.get("TASK_NAME");
        }
        if (taskNameArg == null) {
            return new ResultSet(columns, List.of(new Row(List.of("No task name provided"))));
        }

        final String status =
            UserTaskCancellation.cancel(catalog, taskScheduler, taskNameArg.toString());
        return new ResultSet(columns, List.of(new Row(List.of(status))));
    }
}
