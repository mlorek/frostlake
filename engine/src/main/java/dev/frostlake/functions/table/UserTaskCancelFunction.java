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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('task_name') table function.
 * Cancels all ongoing executions of the specified task and returns a status row.
 */
public class UserTaskCancelFunction extends TableFunction {

    private final Catalog catalog;

    public UserTaskCancelFunction(final Catalog catalog) {
        super("SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // positional arg handled separately
    }

    @Override
    public ResultSet execute(final Map<String, Object> args) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("status", StringType.VARCHAR)
        );

        Object taskNameArg = args.get("INPUT");
        if (taskNameArg == null) taskNameArg = args.get("TASK_NAME");
        if (taskNameArg == null) {
            return new ResultSet(columns, List.of(new Row(List.of("No task name provided"))));
        }

        String taskName = taskNameArg.toString().toUpperCase().replaceAll("^'|'$", "");
        try {
            String dbName = catalog.getCurrentDatabase();
            String scName = catalog.getCurrentSchema();
            if (dbName == null || scName == null) {
                return new ResultSet(columns, List.of(new Row(List.of("No database/schema selected"))));
            }
            Schema schema = catalog.getDatabase(dbName).getSchema(scName);
            Task task = schema.getTask(taskName);
            task.setState(TaskState.SUSPENDED);
            String msg = "Successfully cancelled ongoing executions of task " + taskName + ".";
            return new ResultSet(columns, List.of(new Row(List.of(msg))));
        } catch (final Exception e) {
            return new ResultSet(columns, List.of(new Row(List.of("Error: " + e.getMessage()))));
        }
    }
}
