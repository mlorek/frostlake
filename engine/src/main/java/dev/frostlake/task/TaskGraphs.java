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

package dev.frostlake.task;

import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The shape of a task graph: a task's children are the tasks of its schema that name it in their AFTER list —
 * every task of a graph lives in the root's schema.
 */
public final class TaskGraphs {

    /** Static helpers only. */
    /**
     * The fire a task's schedule names after one it is scheduled for, as NEXT_SCHEDULED_TIME tells it for a
     * scheduled run: an interval later (live-verified), or the CRON expression's next instant.
     *
     * @param task      the task
     * @param scheduled the fire the run is scheduled for
     * @return the next fire, or null for a task without a schedule
     */
    public static LocalDateTime fireAfter(final Task task, final LocalDateTime scheduled) {
        if (task.getSchedule() == null || scheduled == null) {
            return null;
        }
        if (task.getScheduleType() == ScheduleType.CRON) {
            final ZonedDateTime next = CronSchedule.parse(task.getSchedule())
                .nextFireAfter(scheduled.atZone(ZoneId.systemDefault()));
            return next == null ? null : next.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }
        final long seconds = IntervalSchedule.seconds(task.getSchedule());
        return scheduled.plusSeconds(seconds > 0L ? seconds : 3600L);
    }

    private TaskGraphs() {
    }

    /** Whether the task is a root: a standalone task or the root of a graph, having no predecessor. */
    public static boolean isRoot(final Task task) {
        return task.getPredecessors().isEmpty();
    }

    /** Whether {@code child} names {@code parent} among its predecessors. */
    public static boolean isChildOf(final Task child, final Task parent) {
        for (final String predecessor : child.getPredecessors()) {
            if (QualifiedName.parse(predecessor).last().equalsIgnoreCase(parent.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The task's dependents in its schema, breadth first: its direct children, and with {@code recursive}
     * their children in turn, then the finalizer task a root has — a direct dependent of the root in both
     * modes. The task itself is not included.
     */
    public static List<Task> descendants(final Schema schema, final Task task, final boolean recursive) {
        final List<Task> out = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        seen.add(task.getName());
        final List<Task> frontier = new ArrayList<>();
        frontier.add(task);
        for (int i = 0; i < frontier.size(); i++) {
            final Task parent = frontier.get(i);
            for (final Task candidate : schema.getTasks()) {
                if (!seen.contains(candidate.getName()) && isChildOf(candidate, parent)) {
                    seen.add(candidate.getName());
                    out.add(candidate);
                    if (recursive) {
                        frontier.add(candidate);
                    }
                }
            }
        }
        final Task finalizer = finalizerOf(schema, task);
        if (finalizer != null && !seen.contains(finalizer.getName())) {
            out.add(finalizer);
        }
        return out;
    }

    /**
     * The root tasks a task's graph hangs from: following its predecessors up to the tasks that have none, or a
     * finalizer's root. A root is its own. A predecessor that is gone is passed over, and a loop is walked once.
     */
    public static List<Task> rootsOf(final Schema schema, final Task task) {
        final List<Task> roots = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final List<Task> frontier = new ArrayList<>();
        frontier.add(task);
        seen.add(task.getName());
        for (int i = 0; i < frontier.size(); i++) {
            final Task member = frontier.get(i);
            final List<String> above = new ArrayList<>(member.getPredecessors());
            if (member.isFinalizer() && member.getFinalizedRootTask() != null) {
                above.add(member.getFinalizedRootTask());
            }
            if (above.isEmpty() && !roots.contains(member)) {
                roots.add(member);
            }
            for (final String name : above) {
                final Task parent = taskNamed(schema, QualifiedName.parse(name).last());
                if (parent != null && seen.add(parent.getName())) {
                    frontier.add(parent);
                }
            }
        }
        return roots;
    }

    /** The task of exactly that name in the schema, or null. */
    private static Task taskNamed(final Schema schema, final String name) {
        return schema.hasTaskExact(name) ? schema.getTaskExact(name) : null;
    }

    /** The finalizer task attached to a root task, or null. */
    public static Task finalizerOf(final Schema schema, final Task root) {
        for (final Task candidate : schema.getTasks()) {
            if (candidate.isFinalizer() && candidate.getFinalizedRootTask().equalsIgnoreCase(root.getName())) {
                return candidate;
            }
        }
        return null;
    }

    /** The RUN_ID of a graph run scheduled at that time: its epoch milliseconds, or null. */
    public static Long runId(final LocalDateTime scheduledTime) {
        return scheduledTime == null ? null
            : Long.valueOf(scheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    /** The GRAPH_RUN_GROUP_ID of a root task's graph run, derived from the root and the run. */
    public static String graphRunGroupId(final Task root, final Long runId) {
        return GraphRunGroup.id(root.getId(), runId);
    }

    /**
     * The root's runs that begin a graph run: every run of its first attempt. A run of a retry is a later attempt
     * of the graph run before it (live-verified).
     *
     * @param root the root task
     * @return its graph runs' first runs, oldest first
     */
    public static List<TaskExecution> graphRuns(final Task root) {
        final List<TaskExecution> runs = new ArrayList<>();
        for (final TaskExecution run : root.getExecutionHistory()) {
            if (run.getAttempt() == 1) {
                runs.add(run);
            }
        }
        return runs;
    }

    /**
     * A task's last run within one graph run: the latest that began at or after the graph run's start and before
     * the next graph run's.
     *
     * @param task  the task
     * @param start when the graph run began
     * @param until when the next graph run began, or null for the latest one
     * @param attempt the attempt the run has to belong to, or 0 for any
     * @return the run, or null when the task did not run in it
     */
    public static TaskExecution lastRunWithin(final Task task, final LocalDateTime start, final LocalDateTime until,
                                              final int attempt) {
        TaskExecution last = null;
        for (final TaskExecution run : task.getExecutionHistory()) {
            final LocalDateTime began = run.getStartTime();
            if (began == null || start == null || began.isBefore(start)) {
                continue;
            }
            if ((until == null || began.isBefore(until)) && (attempt == 0 || run.getAttempt() == attempt)) {
                last = run;
            }
        }
        return last;
    }

    /**
     * The last attempt one graph run reached: the highest attempt any run of the root or of its graph belongs to.
     *
     * @param schema the schema the graph lives in
     * @param root   the root task
     * @param start  when the graph run began
     * @param until  when the next graph run began, or null for the latest one
     * @return the attempt, 1 for a graph run never retried
     */
    public static int lastAttempt(final Schema schema, final Task root, final LocalDateTime start,
                                  final LocalDateTime until) {
        final List<Task> tasks = new ArrayList<>();
        tasks.add(root);
        tasks.addAll(descendants(schema, root, true));
        int attempt = 1;
        for (final Task task : tasks) {
            for (final TaskExecution run : task.getExecutionHistory()) {
                final LocalDateTime began = run.getStartTime();
                if (began != null && start != null && !began.isBefore(start)
                        && (until == null || began.isBefore(until))) {
                    attempt = Math.max(attempt, run.getAttempt());
                }
            }
        }
        return attempt;
    }
}
