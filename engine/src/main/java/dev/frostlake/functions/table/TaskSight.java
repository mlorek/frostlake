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

import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Task;

/**
 * Which tasks the session may read about: TASK_DEPENDENTS "only returns rows for a task owner", or a role with MONITOR
 * or OPERATE on the task, and to view a task graph through the history functions "the invoking role requires at least
 * one of" OWNERSHIP, MONITOR or OPERATE on the task, or the account's MONITOR EXECUTION (documented).
 */
public interface TaskSight {

    /**
     * Whether the session may read about a task.
     *
     * @param schema the task's schema
     * @param task   the task
     * @return whether it sees the task
     */
    boolean sees(Schema schema, Task task);
}
