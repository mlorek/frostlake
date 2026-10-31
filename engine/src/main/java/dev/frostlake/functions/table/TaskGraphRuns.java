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
import dev.frostlake.types.DeclaredTypeFold;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;

import java.time.Duration;
import java.time.Instant;
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
    /** The width the account declares every text column of the task listings with. */
    static final StringType TEXT = new StringType("VARCHAR", DeclaredTypeFold.UNKNOWN_LENGTH_VARCHAR);
    /** The type the account declares their versions, run ids and attempt numbers with. */
    static final NumericType WHOLE_NUMBER = new NumericType("NUMBER", 38, 0);

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
     * The RESULT_LIMIT argument of the task history functions (live-verified): a number of 0 to 10000, its
     * fraction dropped; a NULL is the limit a call leaves out, a text is refused by its type and a negative
     * number by its value.
     *
     * @param value  the argument's value, or null when it is absent or NULL
     * @param absent the limit a call leaves out
     * @return the limit
     * @throws RuntimeException when it is no number, is negative or exceeds 10000
     */
    static int limit(final Object value, final int absent) {
        if (value == null) {
            return absent;
        }
        if (!(value instanceof Number)) {
            throw new RuntimeException(SqlCompilationError.of("invalid type [VARCHAR(" + text(value).length()
                + ")] for parameter 'RESULT_LIMIT'"));
        }
        final Number number = (Number) value;
        if (number.doubleValue() < 0) {
            throw new RuntimeException(SqlCompilationError.of("invalid value [" + value
                + "] for parameter 'RESULT_LIMIT'"));
        }
        if (number.longValue() > MAX_LIMIT) {
            throw new RuntimeException(SqlCompilationError.of(
                "Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."));
        }
        return (int) number.longValue();
    }

    /**
     * The output columns, as the account orders them; {@code completed} adds COMPLETED_TIME after
     * NEXT_SCHEDULED_TIME. SCHEDULED_FROM follows ATTEMPT_NUMBER, and SCHEDULED_BY_USER ends the row.
     */
    static List<ResultSetColumn> columns(final boolean completed) {
        final List<ResultSetColumn> columns = new ArrayList<>(Arrays.asList(
            new ResultSetColumn("ROOT_TASK_NAME", TEXT),
            new ResultSetColumn("DATABASE_NAME", TEXT),
            new ResultSetColumn("SCHEMA_NAME", TEXT),
            new ResultSetColumn("STATE", TEXT),
            new ResultSetColumn("FIRST_ERROR_TASK_NAME", TEXT),
            new ResultSetColumn("FIRST_ERROR_CODE", TEXT),
            new ResultSetColumn("FIRST_ERROR_MESSAGE", TEXT),
            new ResultSetColumn("SCHEDULED_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("QUERY_START_TIME", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("NEXT_SCHEDULED_TIME", ShowResultHelpers.CREATED_ON)));
        if (completed) {
            columns.add(new ResultSetColumn("COMPLETED_TIME", ShowResultHelpers.CREATED_ON));
        }
        columns.addAll(Arrays.asList(
            new ResultSetColumn("ROOT_TASK_ID", TEXT),
            new ResultSetColumn("GRAPH_VERSION", WHOLE_NUMBER),
            new ResultSetColumn("RUN_ID", WHOLE_NUMBER),
            new ResultSetColumn("ATTEMPT_NUMBER", WHOLE_NUMBER),
            new ResultSetColumn("SCHEDULED_FROM", TEXT),
            new ResultSetColumn("CONFIG", TEXT),
            new ResultSetColumn("GRAPH_RUN_GROUP_ID", TEXT),
            new ResultSetColumn("BACKFILL_INFO", new ObjectType()),
            new ResultSetColumn("SCHEDULED_BY_USER", TEXT)));
        return DeclaredColumnTypes.typed(columns);
    }

    /**
     * The graph runs scheduled within the next eight days: one per resumed root task with a schedule, newest
     * scheduled time first. The engine runs a graph to completion when it starts, so none is ever executing.
     */
    static List<Row> current(final Catalog catalog, final String rootTaskName, final int limit) {
        final List<Row> rows = new ArrayList<>();
        final Instant horizon = Instant.now().plus(Duration.ofDays(LOOK_AHEAD_DAYS));
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
                        null, scheduled, null, null, root.getResumedBy()));
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

    /**
     * One row per attempt of each graph run of the root: its first attempt, then one more for each retry, as the
     * account counts them (live-verified). An attempt is told from its own task runs — its state, first error and
     * completion — and a retry's SCHEDULED_TIME is its first run's, scheduled from MANUAL RETRY; every attempt keeps
     * the graph run's RUN_ID and GRAPH_RUN_GROUP_ID. A retry's QUERY_START_TIME is when the root's run in it
     * started, or, when the retry does not run the root, when its first run started if that run succeeded: a retry
     * whose first run failed tells none.
     */
    private static void addCompleted(final List<Row> rows, final Task root, final Schema schema,
                                     final boolean errorOnly) {
        final List<TaskExecution> graphRuns = TaskGraphs.graphRuns(root);
        final List<Task> tasks = new ArrayList<>();
        tasks.add(root);
        tasks.addAll(TaskGraphs.descendants(schema, root, true));
        for (int i = 0; i < graphRuns.size(); i++) {
            final TaskExecution run = graphRuns.get(i);
            final LocalDateTime start = run.getStartTime();
            final LocalDateTime until = i + 1 < graphRuns.size() ? graphRuns.get(i + 1).getStartTime() : null;
            final int attempts = TaskGraphs.lastAttempt(schema, root, start, until);
            for (int attempt = 1; attempt <= attempts; attempt++) {
                addAttempt(rows, root, schema, tasks, run, until, attempt, errorOnly);
            }
        }
    }

    private static void addAttempt(final List<Row> rows, final Task root, final Schema schema, final List<Task> tasks,
                                   final TaskExecution graphRun, final LocalDateTime until, final int attempt,
                                   final boolean errorOnly) {
        TaskExecution first = null;
        TaskExecution rootRun = null;
        TaskExecution firstError = null;
        String firstErrorTask = null;
        LocalDateTime completed = null;
        for (final Task task : tasks) {
            final TaskExecution last = TaskGraphs.lastRunWithin(task, graphRun.getStartTime(), until, attempt);
            if (last == null) {
                continue;
            }
            if (task == root) {
                rootRun = last;
            }
            if (first == null || before(last, first)) {
                first = last;
            }
            if (last.getEndTime() != null && (completed == null || last.getEndTime().isAfter(completed))) {
                completed = last.getEndTime();
            }
            if (FAILED.equals(last.getState()) && (firstError == null || before(last, firstError))) {
                firstError = last;
                firstErrorTask = task.getName();
            }
        }
        if (first == null || errorOnly && firstError == null) {
            return;
        }
        final String state = firstError != null ? FAILED : TaskExecutionState.SUCCEEDED.name();
        final boolean retry = attempt > 1;
        final LocalDateTime queryStart;
        if (!retry) {
            queryStart = graphRun.getStartTime();
        } else if (rootRun != null) {
            queryStart = rootRun.getStartTime();
        } else {
            queryStart = FAILED.equals(first.getState()) ? null : first.getStartTime();
        }
        rows.add(row(true, root, schema, state,
            retry ? TaskTrigger.MANUAL_RETRY.reported() : graphRun.getScheduledFrom(), firstErrorTask,
            firstError == null ? null : firstError.getErrorCode(),
            firstError == null ? null : firstError.getErrorMessage(),
            ShowResultHelpers.createdOn(retry ? first.getScheduledTime() : graphRun.getScheduledTime()),
            ShowResultHelpers.createdOn(queryStart), ShowResultHelpers.createdOn(completed),
            TaskGraphs.runId(graphRun.getScheduledTime()), attempt,
            (retry ? first : graphRun).getScheduledByUser()));
    }

    private static boolean before(final TaskExecution a, final TaskExecution b) {
        return a.getStartTime() != null && b.getStartTime() != null && a.getStartTime().isBefore(b.getStartTime());
    }

    private static boolean named(final Task task, final String rootTaskName) {
        return rootTaskName == null || task.getName().equalsIgnoreCase(rootTaskName);
    }

    private static Row row(final boolean completedShape, final Task root, final Schema schema, final String state,
                           final String scheduledFrom, final String firstErrorTask, final String firstErrorCode,
                           final String firstErrorMessage, final OffsetDateTime scheduled,
                           final OffsetDateTime queryStart, final OffsetDateTime completed, final String scheduledBy) {
        return row(completedShape, root, schema, state, scheduledFrom, firstErrorTask, firstErrorCode,
            firstErrorMessage, scheduled, queryStart, completed,
            scheduled == null ? null : Long.valueOf(scheduled.toInstant().toEpochMilli()), 1, scheduledBy);
    }

    /**
     * One graph run's row. A completed one tells the code of its first error and the user it was scheduled by; a
     * scheduled one tells the user who resumed the root and the fire after its own (live-verified).
     */
    private static Row row(final boolean completedShape, final Task root, final Schema schema, final String state,
                           final String scheduledFrom, final String firstErrorTask, final String firstErrorCode,
                           final String firstErrorMessage, final OffsetDateTime scheduled,
                           final OffsetDateTime queryStart, final OffsetDateTime completed, final Long runId,
                           final int attempt, final String scheduledBy) {
        final String group = GraphRunGroup.id(root.getId(), runId);
        final OffsetDateTime next = root.isActive() && root.getSchedule() != null
            ? ShowResultHelpers.createdOn(completedShape ? root.getNextRunTime()
                : TaskGraphs.fireAfter(root, root.getNextRunTime())) : null;
        final List<Object> cells = new ArrayList<>(Arrays.<Object>asList(
            root.getName(), schema.getDatabaseName(), schema.getName(), state,
            firstErrorTask, firstErrorCode, firstErrorMessage, scheduled, queryStart, next));
        if (completedShape) {
            cells.add(completed);
        }
        cells.addAll(Arrays.<Object>asList(root.getId(), Long.valueOf(1L), runId, Long.valueOf(attempt), scheduledFrom,
            null, group, null, scheduledBy));
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
