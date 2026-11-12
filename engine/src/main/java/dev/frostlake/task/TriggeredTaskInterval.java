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

import dev.frostlake.metastore.model.Task;

/**
 * A serverless task its condition triggers - a root task with a WHEN, no SCHEDULE and no warehouse - is resumed
 * or run on demand only once it has a TARGET_COMPLETION_INTERVAL: {@code TARGET_COMPLETION_INTERVAL must be set for
 * serverless triggered task DB.SCHEMA.T to resume or execute.} (live-verified). A task with a warehouse, a schedule
 * or the interval runs as before.
 */
public final class TriggeredTaskInterval {

    private TriggeredTaskInterval() {
    }

    /**
     * Refuses to resume or run a serverless triggered task that has no TARGET_COMPLETION_INTERVAL.
     *
     * @param task     the task
     * @param fullName the task's name in full, DB.SCHEMA.NAME
     */
    public static void require(final Task task, final String fullName) {
        if (task.getCondition() != null && task.getSchedule() == null && task.getWarehouse() == null
                && task.getPredecessors().isEmpty() && !task.isFinalizer()
                && task.getTargetCompletionInterval() == null) {
            throw new RuntimeException("TARGET_COMPLETION_INTERVAL must be set for serverless triggered task "
                + fullName + " to resume or execute.");
        }
    }
}
