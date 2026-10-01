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
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Serializable snapshot of warehouse metadata
 */
public class WarehouseSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String size;
    public String state;
    public Integer autoSuspend;
    public boolean autoResume;
    public String comment;
    public LocalDateTime createdAt;
    // Multi-cluster scaling. scalingPolicy is null on old snapshots; the counts default to 0 → restore
    // treats a 0 min/max as "unset" and leaves the warehouse's own defaults in place.
    public String scalingPolicy;
    public int minClusterCount;
    public int maxClusterCount;
    public String owner;
    public String warehouseType;
    public String resourceMonitor;

    // Null on snapshots that predate the field (deserialization bypasses field initializers): restore null-checks.
    /** True once the fields below were written; an older snapshot leaves the warehouse's defaults. */
    public Boolean settingsWritten;
    public boolean initiallySuspended;
    public int maxConcurrencyLevel;
    public int statementQueuedTimeoutSeconds;
    public int statementTimeoutSeconds;
    public boolean enableQueryAcceleration;
    public int queryAccelerationMaxScaleFactor;
    public String generation;
    public String resourceConstraint;
    public boolean enabled;
    public ArrayList<String> parametersSet;
    public HashMap<String, String> tags;
}
