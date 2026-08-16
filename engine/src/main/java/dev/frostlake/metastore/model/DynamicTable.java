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

import dev.frostlake.metastore.SqlObject;

import java.time.LocalDateTime;

/**
 * Represents a Snowflake DYNAMIC TABLE — a table whose contents are automatically
 * refreshed from a query, with a configurable target lag.
 */
public class DynamicTable extends SqlObject {

    private final String query;
    private String targetLag;          // e.g. "1 minutes", "DOWNSTREAM"
    private String warehouse;
    private RefreshMode refreshMode;
    private Initialize initialize;
    private int dataRetentionDays;
    private int maxDataExtensionDays;
    private State state;
    private LocalDateTime lastRefreshedTime;
    private String schedulingState;    // descriptive status returned by SHOW

    public DynamicTable(final String name, final String query, final String targetLag,
                        final String warehouse) {
        super(name);
        this.query = query;
        this.targetLag = targetLag;
        this.warehouse = warehouse;
        this.refreshMode = RefreshMode.AUTO;
        this.initialize = Initialize.ON_CREATE;
        this.dataRetentionDays = 1;
        this.maxDataExtensionDays = 14;
        this.state = State.RUNNING;
        this.schedulingState = "ACTIVE";
    }

    public String getQuery() { return query; }
    public String getTargetLag() { return targetLag; }
    public void setTargetLag(final String targetLag) { this.targetLag = targetLag; }
    public String getWarehouse() { return warehouse; }
    public void setWarehouse(final String warehouse) { this.warehouse = warehouse; }
    public RefreshMode getRefreshMode() { return refreshMode; }
    public void setRefreshMode(final RefreshMode refreshMode) { this.refreshMode = refreshMode; }
    public Initialize getInitialize() { return initialize; }
    public void setInitialize(final Initialize initialize) { this.initialize = initialize; }
    public int getDataRetentionDays() { return dataRetentionDays; }
    public void setDataRetentionDays(final int days) { this.dataRetentionDays = days; }
    public int getMaxDataExtensionDays() { return maxDataExtensionDays; }
    public void setMaxDataExtensionDays(final int days) { this.maxDataExtensionDays = days; }
    public State getState() { return state; }
    public void setState(final State state) { this.state = state; }
    public LocalDateTime getLastRefreshedTime() { return lastRefreshedTime; }
    public void setLastRefreshedTime(final LocalDateTime t) { this.lastRefreshedTime = t; }
    public String getSchedulingState() { return schedulingState; }
    public void setSchedulingState(final String s) { this.schedulingState = s; }

    public boolean isSuspended() { return state == State.SUSPENDED; }

    @Override
    public void rename(final String newName) { throw new UnsupportedOperationException("Dynamic tables cannot be renamed directly"); }

    @Override
    public String getObjectType() { return "DYNAMIC TABLE"; }
}
