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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.task.TaskGraphs;
import dev.frostlake.task.TaskScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * SYSTEM$TASK_DEPENDENTS_ENABLE(name): "Recursively resumes a specified task and all its dependent tasks" (documented)
 * - the task the name resolves to, in the schema the name gives or else the session's, and every task of that schema
 * that follows it however many levels down, its finalizer included. It lets "the owner of a task graph" resume the
 * graph in one statement, so each task it resumes asks what ALTER TASK … RESUME asks, OPERATE or OWNERSHIP, and every
 * one is asked before any is resumed. A task the session cannot see is a name no task answers to.
 *
 * <p>It used to resume, with no privilege asked, the same-named dependents of the CURRENT schema whatever schema the
 * name gave, and never the named task itself.
 */
final class TaskDependentsEnable {

    private final QueryExecutor executor;

    TaskDependentsEnable(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * The call's answer.
     *
     * @param args the call's arguments, the task's name first
     * @return {@code Statement executed successfully.}, or null for a NULL name
     */
    Object enable(final List<Object> args) {
        if (args.isEmpty() || args.get(0) == null) {
            // A NULL names no task, and answers NULL (live-verified).
            return null;
        }
        final String written = args.get(0).toString();
        final Catalog catalog = executor.getCatalog();
        final String[] parts = QualifiedName.parse(SqlIdentifiers.canonicalText(written)).parts();
        Schema schema;
        Task named;
        try {
            schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            named = schema.getTaskExact(parts[parts.length - 1]);
        } catch (final RuntimeException unresolved) {
            schema = null;
            named = null;
        }
        if (named == null || !seen(schema, named)) {
            throw unnamed(written);
        }
        final String database = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        // The dependents first and the named task last, so a root starts over a graph already resumed beneath it.
        final List<Task> graph = new ArrayList<Task>(TaskGraphs.descendants(schema, named, true));
        graph.add(named);
        for (final Task task : graph) {
            executor.requirePrivilege(Privilege.OPERATE, "TASK",
                new String[] {database, schema.getName(), task.getName()}, null);
        }
        final TaskScheduler scheduler = executor.getTaskScheduler();
        for (final Task task : graph) {
            if (scheduler != null) {
                scheduler.resumeTask(TaskScheduler.schedulerKey(database, schema.getName(), task.getName()), task);
            } else {
                task.setState(TaskState.STARTED);
            }
        }
        return "Statement executed successfully.";
    }

    /** Whether the session sees the task, as SHOW TASKS lists it. */
    private boolean seen(final Schema schema, final Task task) {
        final SecurityManager security = executor.getSecurityManager();
        return security == null || security.sees("TASK", schema.qualifiedName(task.getName()), task.getOwner());
    }

    /** A name no task answers to is refused by the parameter the call sets, as written (live-verified). */
    private static RuntimeException unnamed(final String written) {
        return new RuntimeException(SqlCompilationError.of("invalid value [" + written
            + "] for parameter 'RECURSIVE_TASK_ENABLE'"));
    }
}
