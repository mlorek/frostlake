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
package dev.frostlake.persistence;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One recorded run of a task, as TASK_HISTORY reports it, as a snapshot holds it. */
public class TaskExecutionSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** When the run was scheduled. */
    public LocalDateTime scheduledTime;

    /** When it started. */
    public LocalDateTime startTime;

    /** When it ended, or null. */
    public LocalDateTime endTime;

    /** Its state, as the enum constant's name. */
    public String state;

    /** The error message of a failed run, or null. */
    public String errorMessage;

    /** How many rows it affected. */
    public int rowsAffected;

    /** What triggered it, as TASK_HISTORY's scheduled_from reports it. */
    public String scheduledFrom;
}
