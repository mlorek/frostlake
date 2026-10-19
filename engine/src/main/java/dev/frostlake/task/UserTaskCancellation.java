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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Schema;

import java.util.Locale;

/**
 * The one implementation of {@code SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('task_name')}, shared
 * by the three surfaces that can reach it: the registered table function, and the two expression
 * paths ({@code SELECT SYSTEM$…()} and {@code SET v = SYSTEM$…()}).
 *
 * <p>It cancels <em>executions</em>, not the task — a started task is still started afterwards. The
 * name is echoed into the message exactly as it was passed while the lookup ignores case, and an
 * unknown name raises rather than answering with a status row.
 */
public final class UserTaskCancellation {

    /** Closes both messages — Snowflake appends it whether or not the task was found. */
    private static final String CANCEL_QUERY_HINT =
        " If the task was dropped or replaced after a previous execution started, use"
        + " SYSTEM$CANCEL_QUERY along with the query id to cancel the run.";

    private UserTaskCancellation() {
    }

    /**
     * The status line for cancelling {@code taskName}'s in-flight runs. {@code scheduler} may be
     * null where one was never wired up, which reads as nothing being in flight.
     */
    public static String cancel(final Catalog catalog, final TaskScheduler scheduler,
                                final String taskName) {
        if (taskName == null) {
            // A NULL name answers NULL (live-verified).
            return null;
        }
        final String name = taskName.replaceAll("^'|'$", "");
        // The argument may be QUALIFIED (db.schema.task or schema.task); unqualified parts
        // default to the session's. The messages echo the name exactly as it was passed.
        final String[] parts = name.split("\\.");
        final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        final String schemaName = parts.length >= 2 ? parts[parts.length - 2] : catalog.getCurrentSchema();
        final String bareName = parts[parts.length - 1];
        if (databaseName == null || schemaName == null || catalog.getDatabase(databaseName) == null) {
            throw new RuntimeException(notFound(name));
        }
        final Schema schema = catalog.getDatabase(databaseName).getSchema(schemaName);
        if (schema == null || !schema.hasTask(bareName)) {
            throw new RuntimeException(notFound(name));
        }
        final String qualifiedName = (databaseName + "." + schemaName + "." + bareName).toUpperCase(Locale.ROOT);
        if (scheduler == null || !scheduler.hasRunningExecutions(qualifiedName)) {
            return "Task " + name + " has no currently running executions." + CANCEL_QUERY_HINT;
        }
        return "Successfully cancelled ongoing executions of task " + name + ".";
    }

    /**
     * The refusal of a call written with another number of arguments than the one name, placed at the call
     * (live-verified: {@code SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()} is "not enough arguments for
     * function [SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()], expected 1, got 0").
     *
     * @param line     the call's line
     * @param position the call's position in the line
     * @param written  the call as written
     * @param given    how many arguments it carries
     * @return the refusal
     */
    public static RuntimeException wrongCount(final int line, final int position, final String written,
                                              final int given) {
        return new RuntimeException(SqlCompilationError.at(line, position, given < 1
            ? "not enough arguments for function [" + written + "], expected 1, got " + given
            : "too many arguments for function [" + written + "] expected 1, got " + given));
    }

    private static String notFound(final String name) {
        return "Task " + name + " not found or not authorized." + CANCEL_QUERY_HINT;
    }
}
