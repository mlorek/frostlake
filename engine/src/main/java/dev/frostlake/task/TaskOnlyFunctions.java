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

import java.util.Set;

/**
 * The SYSTEM$ functions that run inside a task alone. Outside one each is refused in the account's words while the
 * statement that calls it compiles: under a WHERE, over a table with no row and inside SYSTEM$TYPEOF alike
 * (live-verified).
 */
public final class TaskOnlyFunctions {

    private static final Set<String> NAMES = Set.of("SYSTEM$GET_TASK_GRAPH_CONFIG",
        "SYSTEM$GET_PREDECESSOR_RETURN_VALUE", "SYSTEM$TASK_RUNTIME_INFO");

    private TaskOnlyFunctions() {
    }

    /**
     * Refuses a call of a task-only function made outside a task.
     *
     * @param funcName the call's name, upper-cased
     */
    public static void requireTask(final String funcName) {
        if (NAMES.contains(funcName) && TaskScheduler.currentTask() == null) {
            throw new RuntimeException("Function " + funcName + " must be called from within a task.");
        }
    }
}
