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

import dev.frostlake.storage.ResultSet;
import java.util.List;

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
}
