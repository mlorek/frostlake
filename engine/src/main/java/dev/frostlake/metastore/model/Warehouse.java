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

import dev.frostlake.metastore.QueryExecution;
import dev.frostlake.metastore.Taggable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Snowflake Compute Warehouse
 * Warehouses provide compute resources for query execution
 */
public class Warehouse implements Taggable {

    private String name;
    private WarehouseSize size;
    private WarehouseState state;
    private ScalingPolicy scalingPolicy;
    private int minClusterCount;
    private int maxClusterCount;
    private int autoSuspendSeconds;
    private boolean autoResume;
    private final LocalDateTime createdAt;
    private LocalDateTime lastStateChange;
    private final List<QueryExecution> queryHistory;
    private long totalQueriesExecuted;
    private long totalCreditsUsed;
    private String comment;
    private String warehouseType = "STANDARD";
    private boolean initiallySuspended = true;
    private String resourceMonitor;
    private int maxConcurrencyLevel = 8;
    private int statementQueuedTimeoutSeconds = 0;
    private int statementTimeoutSeconds = 0;
    private boolean enableQueryAcceleration = false;
    private int queryAccelerationMaxScaleFactor = 8;

    public Warehouse(final String name, final WarehouseSize size) {
        this.name = name;
        this.size = size;
        this.state = WarehouseState.SUSPENDED;
        this.scalingPolicy = ScalingPolicy.STANDARD;
        this.minClusterCount = 1;
        this.maxClusterCount = 1;
        this.autoSuspendSeconds = 600; // 10 minutes default
        this.autoResume = true;
        this.createdAt = LocalDateTime.now();
        this.lastStateChange = LocalDateTime.now();
        this.queryHistory = new ArrayList<>();
        this.totalQueriesExecuted = 0;
        this.totalCreditsUsed = 0;
    }

    public void start() {
        if (state == WarehouseState.STARTED) {
            return;
        }
        this.state = WarehouseState.STARTING;
        // Simulate startup time
        try {
            Thread.sleep(100); // Simulate startup delay
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.state = WarehouseState.STARTED;
        this.lastStateChange = LocalDateTime.now();
    }

    public void suspend() {
        if (state == WarehouseState.SUSPENDED) {
            return;
        }
        this.state = WarehouseState.SUSPENDING;
        this.state = WarehouseState.SUSPENDED;
        this.lastStateChange = LocalDateTime.now();
    }

    public void resume() {
        start();
    }

    public void recordQueryExecution(final QueryExecution execution) {
        queryHistory.add(execution);
        totalQueriesExecuted++;

        // Calculate credits used (simplified)
        // Round up to minimum 1 minute billing
        long executionMs = execution.getExecutionTimeMs();
        if (executionMs < 60000) executionMs = 60000; // Minimum 1 minute
        double executionMinutes = executionMs / 60000.0;
        double creditsUsed = (size.getCreditsPerHour() * executionMinutes) / 60.0;
        totalCreditsUsed += (long) Math.ceil(creditsUsed);
    }

    /** Owning role; defaults to SYSADMIN until stamped with the creating role at CREATE. */
    private String owner = "SYSADMIN";
    /** Object tags applied via ALTER WAREHOUSE ... SET TAG (canonical upper-cased name -> value). */
    private final Map<String, String> tags = new HashMap<>();

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    @Override
    public void setTag(final String tagName, final String value) {
        tags.put(tagName.toUpperCase(), value);
    }

    @Override
    public void unsetTag(final String tagName) {
        tags.remove(tagName.toUpperCase());
    }

    @Override
    public String getTagValue(final String tagName) {
        return tags.get(tagName.toUpperCase());
    }

    @Override
    public Map<String, String> getTagValues() {
        return new HashMap<>(tags);
    }

    public WarehouseSize getSize() {
        return size;
    }

    public void setSize(final WarehouseSize size) {
        this.size = size;
    }

    public WarehouseState getState() {
        return state;
    }

    public void setState(final WarehouseState state) {
        this.state = state;
        this.lastStateChange = LocalDateTime.now();
    }

    public ScalingPolicy getScalingPolicy() {
        return scalingPolicy;
    }

    public void setScalingPolicy(final ScalingPolicy scalingPolicy) {
        this.scalingPolicy = scalingPolicy;
    }

    public int getMinClusterCount() {
        return minClusterCount;
    }

    public void setMinClusterCount(final int minClusterCount) {
        this.minClusterCount = minClusterCount;
    }

    public int getMaxClusterCount() {
        return maxClusterCount;
    }

    public void setMaxClusterCount(final int maxClusterCount) {
        this.maxClusterCount = maxClusterCount;
    }

    public int getAutoSuspendSeconds() {
        return autoSuspendSeconds;
    }

    public void setAutoSuspendSeconds(final int autoSuspendSeconds) {
        this.autoSuspendSeconds = autoSuspendSeconds;
    }

    public boolean isAutoResume() {
        return autoResume;
    }

    public void setAutoResume(final boolean autoResume) {
        this.autoResume = autoResume;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastStateChange() {
        return lastStateChange;
    }

    public List<QueryExecution> getQueryHistory() {
        return new ArrayList<>(queryHistory);
    }

    public long getTotalQueriesExecuted() {
        return totalQueriesExecuted;
    }

    public long getTotalCreditsUsed() {
        return totalCreditsUsed;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(final String comment) {
        this.comment = comment;
    }

    public String getWarehouseType() { return warehouseType; }
    public void setWarehouseType(final String warehouseType) { this.warehouseType = warehouseType != null ? warehouseType.toUpperCase() : "STANDARD"; }

    public boolean isInitiallySuspended() { return initiallySuspended; }
    public void setInitiallySuspended(final boolean initiallySuspended) { this.initiallySuspended = initiallySuspended; }

    public String getResourceMonitor() { return resourceMonitor; }
    public void setResourceMonitor(final String resourceMonitor) { this.resourceMonitor = resourceMonitor; }

    public int getMaxConcurrencyLevel() { return maxConcurrencyLevel; }
    public void setMaxConcurrencyLevel(final int maxConcurrencyLevel) { this.maxConcurrencyLevel = maxConcurrencyLevel; }

    public int getStatementQueuedTimeoutSeconds() { return statementQueuedTimeoutSeconds; }
    public void setStatementQueuedTimeoutSeconds(final int s) { this.statementQueuedTimeoutSeconds = s; }

    public int getStatementTimeoutSeconds() { return statementTimeoutSeconds; }
    public void setStatementTimeoutSeconds(final int s) { this.statementTimeoutSeconds = s; }

    public boolean isEnableQueryAcceleration() { return enableQueryAcceleration; }
    public void setEnableQueryAcceleration(final boolean v) { this.enableQueryAcceleration = v; }

    public int getQueryAccelerationMaxScaleFactor() { return queryAccelerationMaxScaleFactor; }
    public void setQueryAccelerationMaxScaleFactor(final int v) { this.queryAccelerationMaxScaleFactor = v; }

    public void rename(final String newName) {
        this.name = newName;
    }

    public boolean isActive() {
        return state == WarehouseState.STARTED;
    }

    public boolean canExecuteQueries() {
        return state == WarehouseState.STARTED;
    }
}
