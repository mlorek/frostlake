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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of a TASK — its definition and STARTED/SUSPENDED state, restored as-is. Note that
 * persistence does not re-arm the scheduler on load: a restored STARTED task is not actively scheduled until
 * it is RESUMEd again (ALTER TASK … RESUME, with the scheduler running, is what arms the timer).
 */
public class TaskSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    // Added after 1L. An older snapshot deserialises with these null, and the reader keeps the
    // freshly generated identity in that case rather than failing.
    public String id;
    public String createdByUser;
    public List<String> explicitParameters = new ArrayList<>();
    public String schedule;
    public String scheduleType;
    public String sqlStatement;
    public String warehouse;
    public List<String> predecessors = new ArrayList<>();
    public String state;
    public String comment;
    public String condition;
    public String owner;
    public int suspendTaskAfterNumFailures;
    public int taskAutoRetryAttempts;
    public boolean allowOverlappingExecution;
    public long userTaskTimeoutMs;
    // Additional ALTER TASK … SET parameters. Objects are null and the primitive default 0 on old snapshots
    // (restore leaves the task's own default in place when the trigger interval is 0).
    public String userTaskManagedInitialWarehouseSize;
    public String serverlessTaskMaxStatementSize;
    public String targetCompletionInterval;
    public String errorIntegration;
    public int userTaskMinimumTriggerIntervalInSeconds;
    // The graph and run properties. Null on older snapshots, which read back as a task carrying none.
    public String config;
    public String overlapPolicy;
    public Map<String, String> sessionParameters;
    public String successIntegration;
    public String finalizedRootTask;
    public String executeAsUser;
    public String serverlessTaskMinStatementSize;

    // The object's tag associations, tag name -> value. Null in a snapshot written before tags were
    // recorded, which reads back as an object carrying none.
    public Map<String, String> tags;

    // The runs TASK_HISTORY reports, oldest first. Null in a snapshot written before they were kept.
    public ArrayList<TaskExecutionSnapshot> history;
}
