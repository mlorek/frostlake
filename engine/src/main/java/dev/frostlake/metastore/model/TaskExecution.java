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

import java.time.LocalDateTime;

public class TaskExecution {
    private final LocalDateTime scheduledTime;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final TaskExecutionState state;
    private final String errorMessage;
    private final int rowsAffected;

    public TaskExecution(final LocalDateTime scheduledTime, final LocalDateTime startTime,
                       final LocalDateTime endTime, final TaskExecutionState state, final String errorMessage, final int rowsAffected) {
        this.scheduledTime = scheduledTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.state = state;
        this.errorMessage = errorMessage;
        this.rowsAffected = rowsAffected;
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
