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

import dev.frostlake.metastore.Taggable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a Snowflake Task - Scheduled SQL execution
 */
public class Task implements Taggable {

    private final String name;
    private String id = UUID.randomUUID().toString();
    private String createdByUser;
    /** Parameter names this task set explicitly, which SHOW PARAMETERS reports at the TASK level. */
    private final Set<String> explicitParameters = new LinkedHashSet<>();
    // schedule / scheduleType / sqlStatement / warehouse are mutable so ALTER TASK … SET/MODIFY can change them.
    private String schedule;
    private ScheduleType scheduleType;
    private String sqlStatement;
    private String warehouse;
    private final List<String> predecessors; // Tasks that must complete before this one
    private final LocalDateTime createdAt;
    private TaskState state;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private final List<TaskExecution> executionHistory;
    private int successCount;
    private int failureCount;
    private String comment;
    private String condition;
    private int suspendTaskAfterNumFailures = 10;
    private int taskAutoRetryAttempts = 0;
    private String userTaskManagedInitialWarehouseSize;
    private String serverlessTaskMaxStatementSize;
    private String targetCompletionInterval;
    private String errorIntegration;
    private int userTaskMinimumTriggerIntervalInSeconds = 30;
    private boolean allowOverlappingExecution = false;
    private long userTaskTimeoutMs = 3600000L; // default 1 hour
    /** The graph configuration, the JSON object text exactly as written, or null. */
    private String config;
    /** The overlap policy a root task set, or null for the default NO_OVERLAP. */
    private String overlapPolicy;
    /** The session parameters the task sets for its runs, by upper-case name, each as SHOW PARAMETERS spells it. */
    private final Map<String, String> sessionParameters = new LinkedHashMap<>();
    private String successIntegration;
    /** The root task this finalizer task is attached to, by name, or null when the task finalizes no graph. */
    private String finalizedRootTask;
    private String executeAsUser;
    private String serverlessTaskMinStatementSize;

    public Task(final String name, final String schedule, final ScheduleType scheduleType,
                final String sqlStatement, final String warehouse) {
        this.name = name;
        this.schedule = schedule;
        this.scheduleType = scheduleType;
        this.sqlStatement = sqlStatement;
        this.warehouse = warehouse;
        this.predecessors = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.state = TaskState.SUSPENDED; // Tasks start suspended by default
        this.executionHistory = new ArrayList<>();
        this.successCount = 0;
        this.failureCount = 0;
    }

    public void addPredecessor(final String taskName) {
        predecessors.add(taskName);
    }

    public void recordExecution(final TaskExecution execution) {
        executionHistory.add(execution);
        lastRunTime = execution.getStartTime();

        if (execution.getState().equals("SUCCEEDED")) {
            successCount++;
            failureCount = 0; // SUSPEND_TASK_AFTER_NUM_FAILURES counts CONSECUTIVE failures
        } else if (execution.getState().equals("FAILED")) {
            failureCount++;
        }
    }

    // The task-graph return value of this task's most recent run (SYSTEM$SET_RETURN_VALUE), readable
    // by direct successors via SYSTEM$GET_PREDECESSOR_RETURN_VALUE.
    private String lastReturnValue;

    public String getLastReturnValue() {
        return lastReturnValue;
    }

    public void setLastReturnValue(final String lastReturnValue) {
        this.lastReturnValue = lastReturnValue;
    }

    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE. */
    private String owner = "SYSADMIN";

    /** The opaque task identifier live reports as SHOW TASKS' {@code id}. */
    public String getId() {
        return id;
    }

    /** Restore the identity a snapshot recorded, so it survives a reload as live's does. */
    public void setId(final String id) {
        if (id != null) {
            this.id = id;
        }
    }

    /** The parameter names set on this task, for the snapshot writer. */
    public Set<String> getExplicitParameters() {
        return explicitParameters;
    }

    /** The user who ran the CREATE TASK, which live reports as {@code created_by_user}. */
    public String getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(final String createdByUser) {
        this.createdByUser = createdByUser;
    }

    /** Record that {@code parameterName} was given on the task itself rather than left at its default. */
    public void markParameterSet(final String parameterName) {
        explicitParameters.add(parameterName.toUpperCase());
    }

    /** Whether the task set this parameter itself — the difference between a TASK level and a blank one. */
    public boolean isParameterSetOnTask(final String parameterName) {
        return explicitParameters.contains(parameterName.toUpperCase());
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public String getSchedule() {
        return schedule;
    }

    public ScheduleType getScheduleType() {
        return scheduleType;
    }

    public String getSqlStatement() {
        return sqlStatement;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public List<String> getPredecessors() {
        return predecessors;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(final TaskState state) {
        this.state = state;
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public void setLastRunTime(final LocalDateTime lastRunTime) {
        this.lastRunTime = lastRunTime;
    }

    public LocalDateTime getNextRunTime() {
        return nextRunTime;
    }

    public void setNextRunTime(final LocalDateTime nextRunTime) {
        this.nextRunTime = nextRunTime;
    }

    public List<TaskExecution> getExecutionHistory() {
        return executionHistory;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public boolean isActive() {
        return state == TaskState.STARTED;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(final String condition) {
        this.condition = condition;
    }

    public void setSchedule(final String schedule) {
        this.schedule = schedule;
    }

    public void setScheduleType(final ScheduleType scheduleType) {
        this.scheduleType = scheduleType;
    }

    public void setSqlStatement(final String sqlStatement) {
        this.sqlStatement = sqlStatement;
    }

    public void setWarehouse(final String warehouse) {
        this.warehouse = warehouse;
    }

    public int getSuspendTaskAfterNumFailures() { return suspendTaskAfterNumFailures; }
    public void setSuspendTaskAfterNumFailures(final int v) { this.suspendTaskAfterNumFailures = v; }

    public int getTaskAutoRetryAttempts() { return taskAutoRetryAttempts; }
    public void setTaskAutoRetryAttempts(final int v) { this.taskAutoRetryAttempts = v; }

    public String getUserTaskManagedInitialWarehouseSize() { return userTaskManagedInitialWarehouseSize; }
    public void setUserTaskManagedInitialWarehouseSize(final String v) { this.userTaskManagedInitialWarehouseSize = v; }

    public String getServerlessTaskMaxStatementSize() { return serverlessTaskMaxStatementSize; }
    public void setServerlessTaskMaxStatementSize(final String v) { this.serverlessTaskMaxStatementSize = v; }

    public String getTargetCompletionInterval() { return targetCompletionInterval; }
    public void setTargetCompletionInterval(final String v) { this.targetCompletionInterval = v; }

    public String getErrorIntegration() { return errorIntegration; }
    public void setErrorIntegration(final String v) { this.errorIntegration = v; }

    public int getUserTaskMinimumTriggerIntervalInSeconds() { return userTaskMinimumTriggerIntervalInSeconds; }
    public void setUserTaskMinimumTriggerIntervalInSeconds(final int v) { this.userTaskMinimumTriggerIntervalInSeconds = v; }

    public boolean isAllowOverlappingExecution() {
        return allowOverlappingExecution;
    }

    /**
     * The legacy overlap switch, which sets the overlap policy too: TRUE is ALLOW_CHILD_OVERLAP and FALSE is
     * NO_OVERLAP.
     */
    public void setAllowOverlappingExecution(final boolean allowOverlappingExecution) {
        this.allowOverlappingExecution = allowOverlappingExecution;
        this.overlapPolicy = allowOverlappingExecution ? "ALLOW_CHILD_OVERLAP" : null;
    }

    /** The overlap policy in force: the one set, or NO_OVERLAP. */
    public String getOverlapPolicy() {
        return overlapPolicy != null ? overlapPolicy : "NO_OVERLAP";
    }

    /**
     * Set the overlap policy, or reset it with null. Only ALLOW_CHILD_OVERLAP reads as the legacy
     * {@code allow_overlapping_execution = true}; ALLOW_ALL_OVERLAP reads as false there.
     */
    public void setOverlapPolicy(final String overlapPolicy) {
        this.overlapPolicy = overlapPolicy == null || "NO_OVERLAP".equals(overlapPolicy) ? null : overlapPolicy;
        this.allowOverlappingExecution = "ALLOW_CHILD_OVERLAP".equals(overlapPolicy);
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(final String config) {
        this.config = config;
    }

    /** The session parameters set on the task, by upper-case name, in the order they were set. */
    public Map<String, String> getSessionParameters() {
        return sessionParameters;
    }

    public void setSessionParameter(final String name, final String value) {
        sessionParameters.put(name.toUpperCase(Locale.ROOT), value);
        markParameterSet(name);
    }

    public void unsetSessionParameter(final String name) {
        sessionParameters.remove(name.toUpperCase(Locale.ROOT));
        explicitParameters.remove(name.toUpperCase(Locale.ROOT));
    }

    /** Forget that a parameter was set on the task, so it reads its default again. */
    public void clearParameterSet(final String parameterName) {
        explicitParameters.remove(parameterName.toUpperCase(Locale.ROOT));
    }

    public String getSuccessIntegration() {
        return successIntegration;
    }

    public void setSuccessIntegration(final String successIntegration) {
        this.successIntegration = successIntegration;
    }

    public String getFinalizedRootTask() {
        return finalizedRootTask;
    }

    public void setFinalizedRootTask(final String finalizedRootTask) {
        this.finalizedRootTask = finalizedRootTask;
    }

    /** Whether the task is a finalizer task, attached to a root task's graph. */
    public boolean isFinalizer() {
        return finalizedRootTask != null;
    }

    public String getExecuteAsUser() {
        return executeAsUser;
    }

    public void setExecuteAsUser(final String executeAsUser) {
        this.executeAsUser = executeAsUser;
    }

    public String getServerlessTaskMinStatementSize() {
        return serverlessTaskMinStatementSize;
    }

    public void setServerlessTaskMinStatementSize(final String serverlessTaskMinStatementSize) {
        this.serverlessTaskMinStatementSize = serverlessTaskMinStatementSize;
    }

    public long getUserTaskTimeoutMs() {
        return userTaskTimeoutMs;
    }

    public void setUserTaskTimeoutMs(final long userTaskTimeoutMs) {
        this.userTaskTimeoutMs = userTaskTimeoutMs;
    }
    /** Object tags applied via ALTER ... SET TAG (canonical upper-cased tag name -&gt; value). */
    private final Map<String, String> tags = new LinkedHashMap<>();

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName.toUpperCase(Locale.ROOT), value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName.toUpperCase(Locale.ROOT));
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName.toUpperCase(Locale.ROOT));
    }

    @Override
    public Map<String, String> getTagValues() {
        return new LinkedHashMap<>(tags);
    }
}
