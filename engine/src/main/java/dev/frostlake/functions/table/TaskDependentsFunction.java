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

import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.TaskGraphs;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.VariantValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME =&gt; '…' [, RECURSIVE =&gt; TRUE | FALSE]) table function: the
 * named task as the first row, followed by its child tasks — every descendant with {@code RECURSIVE => TRUE}
 * (the default), the direct children only with {@code FALSE}.
 */
public class TaskDependentsFunction extends TableFunction {

    private final Catalog catalog;

    public TaskDependentsFunction(final Catalog catalog) {
        super("TASK_DEPENDENTS");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute, where the task is resolved.
    }

    /** The listing's columns are fixed, so a query over it is planned with their types before any row is read. */
    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final String taskName = TaskGraphRuns.text(namedArgs.get("TASK_NAME"));
        if (taskName == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Missing required argument TASK_NAME for function TASK_DEPENDENTS."));
        }
        final boolean recursive = TaskGraphRuns.flag(namedArgs.get("RECURSIVE"), true);
        final Schema schema;
        final Task task;
        try {
            // The argument names the task as an identifier does: quoted parts kept, the rest folded.
            final String canonical = SqlIdentifiers.canonicalText(taskName);
            schema = catalog.resolveOwningSchema(canonical);
            task = schema.getTaskExact(QualifiedName.parse(canonical).last());
        } catch (final RuntimeException missing) {
            throw notATask(taskName);
        }
        if (task == null) {
            throw notATask(taskName);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(row(task, schema));
        for (final Task dependent : TaskGraphs.descendants(schema, task, recursive)) {
            rows.add(row(dependent, schema));
        }
        return new ResultSet(columns(), rows);
    }

    /** A name that resolves to no task is refused as an invalid argument of the function the account scans with. */
    private static RuntimeException notATask(final String taskName) {
        return new RuntimeException(SqlCompilationError.of("Invalid value [" + taskName
            + "] for function 'TASK_DEPENDENTS_SCAN', parameter 1: must be a valid task name"));
    }

    private static List<ResultSetColumn> columns() {
        return DeclaredColumnTypes.typed(Arrays.asList(
            new ResultSetColumn("CREATED_ON", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("DATABASE_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("SCHEMA_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("OWNER", TaskGraphRuns.TEXT),
            new ResultSetColumn("COMMENT", TaskGraphRuns.TEXT),
            new ResultSetColumn("WAREHOUSE", TaskGraphRuns.TEXT),
            new ResultSetColumn("SCHEDULE", TaskGraphRuns.TEXT),
            new ResultSetColumn("PREDECESSORS", new ArrayType(StringType.VARCHAR)),
            new ResultSetColumn("STATE", TaskGraphRuns.TEXT),
            new ResultSetColumn("DEFINITION", TaskGraphRuns.TEXT),
            new ResultSetColumn("CONDITION", TaskGraphRuns.TEXT)));
    }

    /** The task's row, its predecessors named fully qualified as SHOW TASKS names them. */
    private static Row row(final Task task, final Schema schema) {
        final StringBuilder predecessors = new StringBuilder("[");
        for (final String predecessor : task.getPredecessors()) {
            if (predecessors.length() > 1) {
                predecessors.append(',');
            }
            final String[] parts = QualifiedName.parse(predecessor).parts();
            final String qualified = parts.length == 3 ? String.join(".", parts)
                : schema.getDatabaseName() + "." + schema.getName() + "." + parts[parts.length - 1];
            predecessors.append('"').append(qualified.replace("\"", "\\\"")).append('"');
        }
        predecessors.append(']');
        return new Row(Arrays.<Object>asList(
            ShowResultHelpers.createdOn(task.getCreatedAt()),
            task.getName(),
            schema.getDatabaseName(),
            schema.getName(),
            task.getOwner(),
            ShowResultHelpers.text(task.getComment()),
            task.getWarehouse(),
            task.getSchedule(),
            VariantValue.of(predecessors.toString()),
            task.getState() == TaskState.STARTED ? "started" : "suspended",
            task.getSqlStatement(),
            task.getCondition()));
    }
}
