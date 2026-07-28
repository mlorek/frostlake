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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * INFORMATION_SCHEMA.TASK_HISTORY() table function
 * Syntax: SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY())
 *         SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'my_task'))
 *         SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(RESULT_LIMIT => 100))
 */
public class TaskHistoryFunction extends TableFunction {

    private final Catalog catalog;

    public TaskHistoryFunction(final Catalog catalog) {
        super("TASK_HISTORY");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // All args are optional
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("query_id", StringType.VARCHAR),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("query_text", StringType.VARCHAR),
            new ResultSetColumn("condition_text", StringType.VARCHAR),
            new ResultSetColumn("state", StringType.VARCHAR),
            new ResultSetColumn("error_code", StringType.VARCHAR),
            new ResultSetColumn("error_message", StringType.VARCHAR),
            new ResultSetColumn("scheduled_time", StringType.VARCHAR),
            new ResultSetColumn("query_start_time", StringType.VARCHAR),
            new ResultSetColumn("next_scheduled_time", StringType.VARCHAR),
            new ResultSetColumn("completed_time", StringType.VARCHAR),
            new ResultSetColumn("root_task_id", StringType.VARCHAR),
            new ResultSetColumn("graph_version", NumericType.BIGINT),
            new ResultSetColumn("run_id", NumericType.BIGINT),
            new ResultSetColumn("return_value", StringType.VARCHAR),
            new ResultSetColumn("scheduled_from", StringType.VARCHAR)
        );

        String filterTaskName = namedArgs.containsKey("TASK_NAME")
            ? namedArgs.get("TASK_NAME").toString().toUpperCase().replaceAll("^'|'$", "") : null;
        int resultLimit = 100;
        if (namedArgs.containsKey("RESULT_LIMIT")) {
            Object rl = namedArgs.get("RESULT_LIMIT");
            if (rl instanceof Number) resultLimit = ((Number) rl).intValue();
            else try { resultLimit = Integer.parseInt(rl.toString()); } catch (final NumberFormatException ignored) {}
        }

        List<Row> rows = new ArrayList<>();

        // TASK_HISTORY spans the account in Snowflake, not the current schema — callers routinely
        // filter with database_name/schema_name predicates while sitting in another schema, so scan
        // every schema and stamp each row with its owning database and schema.
        try {
            for (final Database database : catalog.getAllDatabases()) {
                for (final Schema schema : database.getAllSchemas()) {
                    for (final Task task : schema.getTasks()) {
                        if (filterTaskName != null && !task.getName().toUpperCase().equals(filterTaskName)) continue;
                        for (final TaskExecution exec : task.getExecutionHistory()) {
                            if (rows.size() >= resultLimit) break;
                            rows.add(new Row(Arrays.asList(
                                null,
                                task.getName(),
                                database.getName(),
                                schema.getName(),
                                task.getSqlStatement(),
                                task.getCondition(),
                                exec.getState(),
                                null,
                                exec.getErrorMessage(),
                                exec.getScheduledTime() != null ? exec.getScheduledTime().toString() : null,
                                exec.getStartTime() != null ? exec.getStartTime().toString() : null,
                                null,
                                exec.getEndTime() != null ? exec.getEndTime().toString() : null,
                                null, 1L, 1L, null, "SCHEDULED"
                            )));
                        }
                        if (rows.size() >= resultLimit) break;
                    }
                    if (rows.size() >= resultLimit) break;
                }
                if (rows.size() >= resultLimit) break;
            }
        } catch (final Exception e) {
            // Return empty on any catalog inconsistency
        }

        return new ResultSet(columns, rows);
    }
}
