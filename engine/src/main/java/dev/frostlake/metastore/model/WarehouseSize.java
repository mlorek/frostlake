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

/**
 * Warehouse size enum - represents the compute capacity of a warehouse
 */
public enum WarehouseSize {
    X_SMALL("X-Small", 1, 8),
    SMALL("Small", 2, 16),
    MEDIUM("Medium", 4, 32),
    LARGE("Large", 8, 64),
    X_LARGE("X-Large", 16, 128),
    X2_LARGE("2X-Large", 32, 256),
    X3_LARGE("3X-Large", 64, 512),
    X4_LARGE("4X-Large", 128, 1024),
    X5_LARGE("5X-Large", 256, 2048),
    X6_LARGE("6X-Large", 512, 4096);

    private final String displayName;
    private final int servers;
    private final int creditsPerHour;

    WarehouseSize(final String displayName, final int servers, final int creditsPerHour) {
        this.displayName = displayName;
        this.servers = servers;
        this.creditsPerHour = creditsPerHour;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getServers() {
        return servers;
    }

    public int getCreditsPerHour() {
        return creditsPerHour;
    }
}
