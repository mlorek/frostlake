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
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
}
