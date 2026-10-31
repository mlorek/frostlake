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
    /** Which attempt of its graph run the run belongs to: 1, or one more for each retry of the graph run. */
    private final int attempt;
    /** The id of the query that ran the task's statement, or null when none is known. */
    private String queryId;
    /** The code of the error the run failed with, as text, or null. */
    private String errorCode;
    /** The user the run was scheduled by: who ran EXECUTE TASK, or who resumed the task. */
    private String scheduledByUser;

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
        this(scheduledTime, startTime, endTime, state, errorMessage, rowsAffected, trigger, 1);
    }

    /**
     * The same, recording which attempt of its graph run the run belongs to.
     *
     * @param scheduledTime the time the run was scheduled for
     * @param startTime     when it began
     * @param endTime       when it ended
     * @param state         its outcome
     * @param errorMessage  the failure's message, or null
     * @param rowsAffected  the rows its body wrote
     * @param trigger       what started it
     * @param attempt       the attempt of its graph run: 1 for the run itself, one more for each retry
     */
    public TaskExecution(final LocalDateTime scheduledTime, final LocalDateTime startTime,
                       final LocalDateTime endTime, final TaskExecutionState state, final String errorMessage,
                       final int rowsAffected, final TaskTrigger trigger, final int attempt) {
        this.scheduledTime = scheduledTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.state = state;
        this.errorMessage = errorMessage;
        this.rowsAffected = rowsAffected;
        this.scheduledFrom = trigger.reported();
        this.attempt = attempt;
    }

    /**
     * Records the query that ran the task's statement, as TASK_HISTORY reports it: its id, the code of the error it
     * failed with, and the user the run was scheduled by.
     *
     * @param ranQueryId    the query's id, or null
     * @param failureCode   the error's code as text, or null for a run that succeeded
     * @param scheduledBy   the user the run was scheduled by, or null
     * @return this run
     */
    public TaskExecution ranAs(final String ranQueryId, final String failureCode, final String scheduledBy) {
        this.queryId = ranQueryId;
        this.errorCode = failureCode;
        this.scheduledByUser = scheduledBy;
        return this;
    }

    /**
     * The id of the query that ran the task's statement.
     *
     * @return the id, or null when none is known
     */
    public String getQueryId() {
        return queryId;
    }

    /**
     * The code of the error the run failed with, as TASK_HISTORY's ERROR_CODE tells it.
     *
     * @return the code as text, or null
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * The user the run was scheduled by, as SCHEDULED_BY_USER tells it.
     *
     * @return the user's name, or null
     */
    public String getScheduledByUser() {
        return scheduledByUser;
    }

    /**
     * Which attempt of its graph run this run belongs to, as TASK_HISTORY's and COMPLETE_TASK_GRAPHS'
     * ATTEMPT_NUMBER report it.
     *
     * @return 1 for a run of the graph run itself, one more for each EXECUTE TASK … RETRY of it
     */
    public int getAttempt() {
        return attempt;
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
