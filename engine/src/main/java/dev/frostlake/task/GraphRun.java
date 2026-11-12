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
import dev.frostlake.metastore.model.Task;

import java.util.HashSet;
import java.util.Set;

/**
 * One graph run's progress: the tasks it has entered, each at most once, and those whose run in it succeeded. "A task
 * runs after all of its predecessor tasks have finished their own runs successfully" (documented, CREATE TASK … AFTER),
 * so a child is ready only once every predecessor it names has succeeded in this run: a predecessor that failed, was
 * skipped or is suspended leaves it, and every task after it, unrun. Names are compared exactly, as a task is named
 * exactly: {@code "b"} is no predecessor of a task that follows {@code B}.
 */
final class GraphRun {

    private final Set<String> entered = new HashSet<String>();
    private final Set<String> succeeded = new HashSet<String>();

    /**
     * Enters a task's run, once per graph run.
     *
     * @param task the task
     * @return whether the run had not entered it yet
     */
    boolean enter(final Task task) {
        return entered.add(task.getName());
    }

    /**
     * Records that a task's run in this graph run succeeded.
     *
     * @param task the task
     */
    void succeeded(final Task task) {
        succeeded.add(task.getName());
    }

    /**
     * Whether a task names a predecessor among its own.
     *
     * @param child  the task that may follow
     * @param parent the task that may precede it
     * @return whether the child is after the parent
     */
    static boolean follows(final Task child, final Task parent) {
        for (final String predecessor : child.getPredecessors()) {
            if (QualifiedName.parse(predecessor).last().equals(parent.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every predecessor of a task has succeeded in this graph run.
     *
     * @param task the task
     * @return whether it may run now
     */
    boolean ready(final Task task) {
        for (final String predecessor : task.getPredecessors()) {
            if (!succeeded.contains(QualifiedName.parse(predecessor).last())) {
                return false;
            }
        }
        return true;
    }
}
