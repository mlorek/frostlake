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
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.TaskGraphs;
import dev.frostlake.task.TaskTrigger;
import dev.frostlake.types.ObjectType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * INFORMATION_SCHEMA.TASK_HISTORY() table function
 * Syntax: SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY())
 *         SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'my_task'))
 *         SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(RESULT_LIMIT => 100))
 */
public class TaskHistoryFunction extends TableFunction {

    /** How far ahead a pending fire is listed — measured to the hour on the account. */
    private static final int LOOK_AHEAD_DAYS = 8;

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
        final List<ResultSetColumn> columns = columns();
        final String filterTaskName = namedArgs.containsKey("TASK_NAME")
            ? namedArgs.get("TASK_NAME").toString().toUpperCase(Locale.ROOT).replaceAll("^'|'$", "") : null;
        final int resultLimit = TaskGraphRuns.limit(namedArgs.get("RESULT_LIMIT"), 100);

        final List<Row> rows = new ArrayList<>();

        // TASK_HISTORY spans the account in Snowflake, not the current schema — callers routinely
        // filter with database_name/schema_name predicates while sitting in another schema, so scan
        // every schema and stamp each row with its owning database and schema.
        try {
            for (final Database database : catalog.getAllDatabases()) {
                for (final Schema schema : database.getAllSchemas()) {
                    for (final Task task : schema.getTasks()) {
                        if (filterTaskName != null && !task.getName().toUpperCase(Locale.ROOT).equals(filterTaskName)) continue;
                        final Task root = rootOf(schema, task);
                        // Live lists the next fire of every STARTED task as a SCHEDULED row, for as
                        // long as that fire is within EIGHT DAYS of now — measured to the hour: a fire
                        // 7 days 23 hours out is listed and one 8 days out is not.
                        if (rows.size() < resultLimit && task.isActive() && task.getSchedule() != null
                                && task.getNextRunTime() != null
                                && !task.getNextRunTime().isAfter(LocalDateTime.now().plusDays(LOOK_AHEAD_DAYS))) {
                            rows.add(row(task, schema, "SCHEDULED", null, task.getNextRunTime(), null, null, root,
                                task.getNextRunTime(), TaskTrigger.SCHEDULE.reported(), 1));
                        }
                        final List<TaskExecution> history = task.getExecutionHistory();
                        for (int i = 0; i < history.size(); i++) {
                            if (rows.size() >= resultLimit) break;
                            final TaskExecution exec = history.get(i);
                            final int first = firstAttempt(history, i);
                            rows.add(row(task, schema, exec.getState(), exec.getErrorMessage(),
                                exec.getScheduledTime(), exec.getStartTime(), exec.getEndTime(), root,
                                graphRunTime(root, task, history.get(first)), exec.getScheduledFrom(),
                                i - first + 1));
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

    /**
     * The output columns, as the account orders and types them: every text unsized, the four times
     * TIMESTAMP_LTZ, the versions, the run id, the attempt and the job id NUMBER(38,0).
     */
    private static List<ResultSetColumn> columns() {
        return Arrays.asList(
            new ResultSetColumn("QUERY_ID", TaskGraphRuns.TEXT),
            new ResultSetColumn("NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("DATABASE_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("SCHEMA_NAME", TaskGraphRuns.TEXT),
            new ResultSetColumn("QUERY_TEXT", TaskGraphRuns.TEXT),
            new ResultSetColumn("CONDITION_TEXT", TaskGraphRuns.TEXT),
            new ResultSetColumn("STATE", TaskGraphRuns.TEXT),
            new ResultSetColumn("ERROR_CODE", TaskGraphRuns.TEXT),
            new ResultSetColumn("ERROR_MESSAGE", TaskGraphRuns.TEXT),
            // TIMESTAMP_LTZ at the true instant, as the documentation types them: a host-local text in these cells
            // read hours off on a host outside UTC and compared as a string.
            new ResultSetColumn("SCHEDULED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("QUERY_START_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("NEXT_SCHEDULED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("COMPLETED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("ROOT_TASK_ID", TaskGraphRuns.TEXT),
            new ResultSetColumn("GRAPH_VERSION", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("RUN_ID", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("RETURN_VALUE", TaskGraphRuns.TEXT),
            new ResultSetColumn("SCHEDULED_FROM", TaskGraphRuns.TEXT),
            new ResultSetColumn("ATTEMPT_NUMBER", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("CONFIG", TaskGraphRuns.TEXT),
            new ResultSetColumn("QUERY_HASH", TaskGraphRuns.TEXT),
            new ResultSetColumn("QUERY_HASH_VERSION", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("QUERY_PARAMETERIZED_HASH", TaskGraphRuns.TEXT),
            new ResultSetColumn("QUERY_PARAMETERIZED_HASH_VERSION", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("GRAPH_RUN_GROUP_ID", TaskGraphRuns.TEXT),
            new ResultSetColumn("BACKFILL_INFO", new ObjectType()),
            new ResultSetColumn("SPCS_JOB_ID", TaskGraphRuns.WHOLE_NUMBER),
            new ResultSetColumn("SCHEDULED_BY_USER", TaskGraphRuns.TEXT));
    }

    /**
     * One run's row. ROOT_TASK_ID, RUN_ID and GRAPH_RUN_GROUP_ID name the graph run it belongs to, as the task
     * graph functions name it; the engine keeps one version of a graph, computes no query hash and records no
     * configuration, container job or scheduling user, so those cells are NULL.
     */
    private static Row row(final Task task, final Schema schema, final String state, final String errorMessage,
                           final LocalDateTime scheduled, final LocalDateTime started, final LocalDateTime ended,
                           final Task root, final LocalDateTime graphRunTime, final String scheduledFrom,
                           final int attempt) {
        final Long runId = root == null ? null : TaskGraphs.runId(graphRunTime);
        return new Row(Arrays.<Object>asList(
            null,
            task.getName(),
            schema.getDatabaseName(),
            schema.getName(),
            task.getSqlStatement(),
            task.getCondition(),
            state,
            null,
            errorMessage,
            ShowResultHelpers.createdOn(scheduled),
            ShowResultHelpers.createdOn(started),
            null,
            ShowResultHelpers.createdOn(ended),
            root == null ? null : root.getId(),
            Long.valueOf(1L),
            runId,
            null,
            scheduledFrom,
            Long.valueOf(attempt),
            null, null, null, null, null,
            runId == null ? null : TaskGraphs.graphRunGroupId(root, runId),
            null, null, null));
    }

    /** The root of the graph a task runs in: the task itself when nothing precedes it, or null when none is found. */
    private static Task rootOf(final Schema schema, final Task task) {
        if (TaskGraphs.isRoot(task)) {
            return task;
        }
        for (final Task candidate : schema.getTasks()) {
            if (TaskGraphs.isRoot(candidate) && TaskGraphs.descendants(schema, candidate, true).contains(task)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The run a run retries: the one before the unbroken chain of retries that ends at {@code index}, or the run
     * itself. A retry is one more attempt of that run (live-verified).
     */
    private static int firstAttempt(final List<TaskExecution> history, final int index) {
        int first = index;
        while (first > 0 && TaskTrigger.MANUAL_RETRY.reported().equals(history.get(first).getScheduledFrom())) {
            first--;
        }
        return first;
    }

    /**
     * When the graph run a task's run belongs to was scheduled: the run's own time for a root task, and for
     * another task the time of the root's last run that began no later than it.
     */
    private static LocalDateTime graphRunTime(final Task root, final Task task, final TaskExecution run) {
        if (root == null || root == task) {
            return run.getScheduledTime();
        }
        LocalDateTime scheduled = null;
        for (final TaskExecution rootRun : root.getExecutionHistory()) {
            if (rootRun.getStartTime() != null && run.getStartTime() != null
                    && !rootRun.getStartTime().isAfter(run.getStartTime())
                    && !TaskTrigger.MANUAL_RETRY.reported().equals(rootRun.getScheduledFrom())) {
                scheduled = rootRun.getScheduledTime();
            }
        }
        return scheduled;
    }
}
