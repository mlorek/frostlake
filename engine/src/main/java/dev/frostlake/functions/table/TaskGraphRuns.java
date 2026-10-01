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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.metastore.model.TaskExecutionState;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.GraphRunGroup;
import dev.frostlake.task.TaskGraphs;
import dev.frostlake.task.TaskTrigger;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The graph runs CURRENT_TASK_GRAPHS and COMPLETE_TASK_GRAPHS report, built from the run history TASK_HISTORY
 * reads. A graph run is one run of a root task together with the runs of the tasks of its graph that started
 * from it: the runs of its descendants that began at or after the root's run and before the root's next run.
 * The graph run failed when any task's last run in it failed, and the earliest such failure is its first
 * error. The engine keeps one version of a graph, so GRAPH_VERSION is always 1, and a run is attempted once.
 */
public final class TaskGraphRuns {

    /** The RESULT_LIMIT a call leaves out. */
    public static final int DEFAULT_LIMIT = 1000;
    /** The largest RESULT_LIMIT. */
    public static final int MAX_LIMIT = 10000;
    /** How far ahead a scheduled graph run is listed. */
    private static final int LOOK_AHEAD_DAYS = 8;
    private static final String FAILED = TaskExecutionState.FAILED.name();

    /** Static helpers only. */
    private TaskGraphRuns() {
    }

    /** A named argument's text, its surrounding quotes removed; null when it is absent. */
    static String text(final Object value) {
        if (value == null) {
            return null;
        }
        final String text = value.toString();
        if (text.length() >= 2 && text.startsWith("'") && text.endsWith("'")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    /** A named boolean argument, or the fallback when it is absent. */
    static boolean flag(final Object value, final boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return "TRUE".equals(text(value).trim().toUpperCase(Locale.ROOT));
    }

    /**
     * The RESULT_LIMIT argument: 1000 when absent.
     *
     * @throws RuntimeException when it exceeds 10000; below one it is taken as absent
     */
    static int limit(final Object value, final String function) {
        if (value == null) {
            return DEFAULT_LIMIT;
        }
        final long limit;
        try {
            limit = value instanceof Number ? ((Number) value).longValue() : Long.parseLong(text(value).trim());
        } catch (final NumberFormatException notNumeric) {
            throw new RuntimeException(SqlCompilationError.of("Invalid value " + value
                + " for argument RESULT_LIMIT of function " + function + "."));
        }
        if (limit > MAX_LIMIT) {
            throw new RuntimeException(SqlCompilationError.of(
                "Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."));
        }
        if (limit < 1) {
            // A limit below one is taken as no limit given.
            return DEFAULT_LIMIT;
        }
        return (int) limit;
    }

    /**
     * The output columns, as the account orders them; {@code completed} adds COMPLETED_TIME after
     * NEXT_SCHEDULED_TIME. SCHEDULED_FROM follows ATTEMPT_NUMBER, and SCHEDULED_BY_USER ends the row.
     */
    static List<ResultSetColumn> columns(final boolean completed) {
        final List<ResultSetColumn> columns = new ArrayList<>(Arrays.asList(
            new ResultSetColumn("ROOT_TASK_NAME", StringType.VARCHAR),
            new ResultSetColumn("DATABASE_NAME", StringType.VARCHAR),
            new ResultSetColumn("SCHEMA_NAME", StringType.VARCHAR),
            new ResultSetColumn("STATE", StringType.VARCHAR),
            new ResultSetColumn("FIRST_ERROR_TASK_NAME", StringType.VARCHAR),
            new ResultSetColumn("FIRST_ERROR_CODE", NumericType.BIGINT),
            new ResultSetColumn("FIRST_ERROR_MESSAGE", StringType.VARCHAR),
            new ResultSetColumn("SCHEDULED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("QUERY_START_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("NEXT_SCHEDULED_TIME", ShowResultHelpers.CREATED_ON)));
        if (completed) {
            columns.add(new ResultSetColumn("COMPLETED_TIME", ShowResultHelpers.CREATED_ON));
        }
        columns.addAll(Arrays.asList(
            new ResultSetColumn("ROOT_TASK_ID", StringType.VARCHAR),
            new ResultSetColumn("GRAPH_VERSION", NumericType.BIGINT),
            new ResultSetColumn("RUN_ID", NumericType.BIGINT),
            new ResultSetColumn("ATTEMPT_NUMBER", NumericType.BIGINT),
            new ResultSetColumn("SCHEDULED_FROM", StringType.VARCHAR),
            new ResultSetColumn("CONFIG", StringType.VARCHAR),
            new ResultSetColumn("GRAPH_RUN_GROUP_ID", StringType.VARCHAR),
            new ResultSetColumn("BACKFILL_INFO", new ObjectType()),
            new ResultSetColumn("SCHEDULED_BY_USER", StringType.VARCHAR)));
        return columns;
    }

    /**
     * The graph runs scheduled within the next eight days: one per resumed root task with a schedule, newest
     * scheduled time first. The engine runs a graph to completion when it starts, so none is ever executing.
     */
    static List<Row> current(final Catalog catalog, final String rootTaskName, final int limit) {
        final List<Row> rows = new ArrayList<>();
        final LocalDateTime horizon = LocalDateTime.now().plusDays(LOOK_AHEAD_DAYS);
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Task root : schema.getTasks()) {
                    if (!TaskGraphs.isRoot(root) || !named(root, rootTaskName) || !root.isActive()
                            || root.getSchedule() == null || root.getNextRunTime() == null
                            || root.getNextRunTime().isAfter(horizon)) {
                        continue;
                    }
                    final OffsetDateTime scheduled = ShowResultHelpers.createdOn(root.getNextRunTime());
                    rows.add(row(false, root, schema, "SCHEDULED", TaskTrigger.SCHEDULE.reported(), null, null,
                        scheduled, null, null));
                }
            }
        }
        return newestFirst(rows, 7, limit);
    }

    /** The graph runs that completed, newest completion first; {@code errorOnly} keeps the failed ones. */
    static List<Row> complete(final Catalog catalog, final String rootTaskName, final boolean errorOnly,
                              final int limit) {
        final List<Row> rows = new ArrayList<>();
        for (final Database database : catalog.getAllDatabases()) {
            for (final Schema schema : database.getAllSchemas()) {
                for (final Task root : schema.getTasks()) {
                    if (TaskGraphs.isRoot(root) && named(root, rootTaskName)) {
                        addCompleted(rows, root, schema, errorOnly);
                    }
                }
            }
        }
        return newestFirst(rows, 10, limit);
    }

    private static void addCompleted(final List<Row> rows, final Task root, final Schema schema,
                                     final boolean errorOnly) {
        final List<TaskExecution> rootRuns = root.getExecutionHistory();
        final List<Task> graph = TaskGraphs.descendants(schema, root, true);
        for (int i = 0; i < rootRuns.size(); i++) {
            final TaskExecution run = rootRuns.get(i);
            final LocalDateTime start = run.getStartTime();
            final LocalDateTime until = i + 1 < rootRuns.size() ? rootRuns.get(i + 1).getStartTime() : null;
            boolean failed = FAILED.equals(run.getState());
            TaskExecution firstError = failed ? run : null;
            String firstErrorTask = failed ? root.getName() : null;
            LocalDateTime completed = run.getEndTime();
            for (final Task task : graph) {
                final TaskExecution last = lastRunWithin(task, start, until);
                if (last == null) {
                    continue;
                }
                if (last.getEndTime() != null && (completed == null || last.getEndTime().isAfter(completed))) {
                    completed = last.getEndTime();
                }
                if (FAILED.equals(last.getState())) {
                    failed = true;
                    if (firstError == null || before(last, firstError)) {
                        firstError = last;
                        firstErrorTask = task.getName();
                    }
                }
            }
            if (errorOnly && !failed) {
                continue;
            }
            final String state = failed ? FAILED : TaskExecutionState.SUCCEEDED.name();
            rows.add(row(true, root, schema, state, run.getScheduledFrom(), firstErrorTask,
                firstError == null ? null : firstError.getErrorMessage(),
                ShowResultHelpers.createdOn(run.getScheduledTime()), ShowResultHelpers.createdOn(start),
                ShowResultHelpers.createdOn(completed)));
        }
    }

    private static TaskExecution lastRunWithin(final Task task, final LocalDateTime start,
                                               final LocalDateTime until) {
        TaskExecution last = null;
        for (final TaskExecution run : task.getExecutionHistory()) {
            final LocalDateTime began = run.getStartTime();
            if (began == null || start == null || began.isBefore(start)) {
                continue;
            }
            if (until == null || began.isBefore(until)) {
                last = run;
            }
        }
        return last;
    }

    private static boolean before(final TaskExecution a, final TaskExecution b) {
        return a.getStartTime() != null && b.getStartTime() != null && a.getStartTime().isBefore(b.getStartTime());
    }

    private static boolean named(final Task task, final String rootTaskName) {
        return rootTaskName == null || task.getName().equalsIgnoreCase(rootTaskName);
    }

    private static Row row(final boolean completedShape, final Task root, final Schema schema, final String state,
                           final String scheduledFrom, final String firstErrorTask, final String firstErrorMessage,
                           final OffsetDateTime scheduled, final OffsetDateTime queryStart,
                           final OffsetDateTime completed) {
        final Long runId = scheduled == null ? null : Long.valueOf(scheduled.toInstant().toEpochMilli());
        final String group = GraphRunGroup.id(root.getId(), runId);
        final OffsetDateTime next = root.isActive() && root.getSchedule() != null
            ? ShowResultHelpers.createdOn(root.getNextRunTime()) : null;
        final List<Object> cells = new ArrayList<>(Arrays.<Object>asList(
            root.getName(), schema.getDatabaseName(), schema.getName(), state,
            firstErrorTask, null, firstErrorMessage, scheduled, queryStart, completedShape ? next : null));
        if (completedShape) {
            cells.add(completed);
        }
        cells.addAll(Arrays.<Object>asList(root.getId(), Long.valueOf(1L), runId, Long.valueOf(1L), scheduledFrom,
            null, group, null, null));
        return new Row(cells);
    }

    /** The rows ordered by the instant in the given column, newest first, cut to the limit. */
    private static List<Row> newestFirst(final List<Row> rows, final int column, final int limit) {
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(final Row a, final Row b) {
                final OffsetDateTime x = (OffsetDateTime) a.getValue(column);
                final OffsetDateTime y = (OffsetDateTime) b.getValue(column);
                if (x == null || y == null) {
                    return x == null ? (y == null ? 0 : 1) : -1;
                }
                return y.compareTo(x);
            }
        });
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }
}
