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

package dev.frostlake.metastore.model;

import dev.frostlake.task.TaskTrigger;
import java.time.LocalDateTime;

public class TaskExecution {
    private final LocalDateTime scheduledTime;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final TaskExecutionState state;
    private final String errorMessage;
    private final int rowsAffected;
    /** What started the run, as TASK_HISTORY's SCHEDULED_FROM reports it. */
    private final String scheduledFrom;

    public TaskExecution(final LocalDateTime scheduledTime, final LocalDateTime startTime,
                       final LocalDateTime endTime, final TaskExecutionState state, final String errorMessage, final int rowsAffected) {
        this(scheduledTime, startTime, endTime, state, errorMessage, rowsAffected, TaskTrigger.SCHEDULE);
    }

    /**
     * The same, recording what started the run.
     *
     * @param scheduledTime the time the run was scheduled for
     * @param startTime     when it began
     * @param endTime       when it ended
     * @param state         its outcome
     * @param errorMessage  the failure's message, or null
     * @param rowsAffected  the rows its body wrote
     * @param trigger       what started it
     */
    public TaskExecution(final LocalDateTime scheduledTime, final LocalDateTime startTime,
                       final LocalDateTime endTime, final TaskExecutionState state, final String errorMessage,
                       final int rowsAffected, final TaskTrigger trigger) {
        this.scheduledTime = scheduledTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.state = state;
        this.errorMessage = errorMessage;
        this.rowsAffected = rowsAffected;
        this.scheduledFrom = trigger.reported();
    }

    /**
     * What started this run, as TASK_HISTORY prints it.
     *
     * @return SCHEDULE or EXECUTE TASK
     */
    public String getScheduledFrom() {
        return scheduledFrom;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getState() {
        return state.name();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getRowsAffected() {
        return rowsAffected;
    }
}
