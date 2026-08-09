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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An account-level compute pool. The properties are the ones CREATE COMPUTE POOL takes: MIN_NODES,
 * MAX_NODES and INSTANCE_FAMILY are required, AUTO_RESUME defaults to true and AUTO_SUSPEND_SECS to
 * {@link #DEFAULT_AUTO_SUSPEND_SECS} — the value UNSET restores, live-verified.
 *
 * <p>{@code resumedOn} is null until the first RESUME; a real account displays the epoch for a pool
 * that has never resumed, and the SHOW renderer does the same.
 */
public class ComputePool extends SqlObject {

    /** The AUTO_SUSPEND_SECS a real account applies when the property is unset. */
    public static final int DEFAULT_AUTO_SUSPEND_SECS = 3600;

    private int minNodes;
    private int maxNodes;
    private String instanceFamily;
    private boolean autoResume = true;
    private int autoSuspendSecs = DEFAULT_AUTO_SUSPEND_SECS;
    private ComputePoolState state = ComputePoolState.STARTING;
    private String application;
    private String placementGroup;
    private List<String> backupInstanceFamilies = new ArrayList<>();
    private Instant resumedOn;
    private Instant updatedOn;

    public ComputePool(final String name) {
        super(name);
        this.updatedOn = getCreatedTime();
    }

    @Override
    public String getObjectType() {
        return "COMPUTE POOL";
    }

    public int getMinNodes() {
        return minNodes;
    }

    public void setMinNodes(final int minNodes) {
        this.minNodes = minNodes;
    }

    public int getMaxNodes() {
        return maxNodes;
    }

    public void setMaxNodes(final int maxNodes) {
        this.maxNodes = maxNodes;
    }

    public String getInstanceFamily() {
        return instanceFamily;
    }

    public void setInstanceFamily(final String instanceFamily) {
        this.instanceFamily = instanceFamily;
    }

    public boolean isAutoResume() {
        return autoResume;
    }

    public void setAutoResume(final boolean autoResume) {
        this.autoResume = autoResume;
    }

    public int getAutoSuspendSecs() {
        return autoSuspendSecs;
    }

    public void setAutoSuspendSecs(final int autoSuspendSecs) {
        this.autoSuspendSecs = autoSuspendSecs;
    }

    public ComputePoolState getState() {
        return state;
    }

    public void setState(final ComputePoolState state) {
        this.state = state;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(final String application) {
        this.application = application;
    }

    public String getPlacementGroup() {
        return placementGroup;
    }

    public void setPlacementGroup(final String placementGroup) {
        this.placementGroup = placementGroup;
    }

    public List<String> getBackupInstanceFamilies() {
        return new ArrayList<>(backupInstanceFamilies);
    }

    public void setBackupInstanceFamilies(final List<String> backupInstanceFamilies) {
        this.backupInstanceFamilies = new ArrayList<>(backupInstanceFamilies);
    }

    /** Null until the first RESUME; rendered as the epoch by SHOW, the way a real account does. */
    public Instant getResumedOn() {
        return resumedOn;
    }

    public void setResumedOn(final Instant resumedOn) {
        this.resumedOn = resumedOn;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }

    public void touchUpdatedOn() {
        this.updatedOn = Instant.now();
    }
}
