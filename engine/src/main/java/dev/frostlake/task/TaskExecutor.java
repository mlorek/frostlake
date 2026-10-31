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

import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.storage.ResultSet;
import java.util.List;
import java.util.Map;

/**
 * Interface for executing SQL statements for tasks
 */
public interface TaskExecutor {
    /**
     * Execute a SQL statement
     * @param sql The SQL statement to execute
     * @return Number of rows affected
     */
    int execute(final String sql);

    /**
     * Execute a query and return its result sets — used to evaluate a task's WHEN condition. An
     * implementation MUST override this; the default throws rather than returning an empty list,
     * because an empty result would make a WHEN condition silently evaluate as "run the task".
     */
    default List<ResultSet> executeQuery(final String sql) {
        throw new UnsupportedOperationException("TaskExecutor.executeQuery must be implemented");
    }

    /**
     * Evaluate a task's WHEN condition. The scheduler composes the query for itself and it is no statement of the
     * task's, so an implementation runs it without a query ID or a history row. This default runs it through
     * {@link #executeQuery}.
     *
     * @param sql the composed condition query
     * @return its result sets
     */
    default List<ResultSet> evaluateCondition(final String sql) {
        return executeQuery(sql);
    }

    /**
     * The history row of the statement this executor ran last, which a task run reports: its query id and the code
     * of the error it failed with. This default knows none.
     *
     * @return the row, or null
     */
    default QueryHistory lastRecordedQuery() {
        return null;
    }

    /**
     * The user the calling session runs as, as CURRENT_USER() names it: who an EXECUTE TASK or a RESUME is by. This
     * default knows none.
     *
     * @return the user's name, or null
     */
    default String currentUser() {
        return null;
    }

    /**
     * Run one task run or alert evaluation as a session of its own, apart from whatever session started it: its
     * own transaction, statement history and session settings. This default just runs it.
     *
     * @param run the run
     */
    default void runInOwnSession(final Runnable run) {
        run.run();
    }

    /**
     * {@link #runInOwnSession(Runnable)} for a session that starts with parameters of its own, as a task's run
     * starts with the parameters the task sets. This default leaves the parameters aside.
     *
     * @param run        the run
     * @param parameters the parameters the session starts with, by name, each as its text
     */
    default void runInOwnSession(final Runnable run, final Map<String, String> parameters) {
        runInOwnSession(run);
    }
}
