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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.SqlObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An external volume: an account-level object naming the cloud storage locations Iceberg tables keep their files
 * in. Each location is kept as its written properties (NAME, STORAGE_PROVIDER, STORAGE_BASE_URL, …); the first
 * location is the active one. Frostlake never reaches the storage.
 */
public class ExternalVolume extends SqlObject {

    private boolean allowWrites = true;
    private final List<Map<String, PropertyValue>> storageLocations = new ArrayList<>();
    private Instant droppedOn;

    /** @param name the volume's canonical name */
    public ExternalVolume(final String name) {
        this(name, StatementClock.instant());
    }

    /**
     * A volume created at a given moment, as a restored snapshot brings it back.
     *
     * @param name the volume's canonical name
     * @param createdTime when it was created
     */
    public ExternalVolume(final String name, final Instant createdTime) {
        super(name, createdTime);
    }

    @Override
    public String getObjectType() {
        return "EXTERNAL VOLUME";
    }

    /** Whether Snowflake may write to the volume. */
    public boolean isAllowWrites() {
        return allowWrites;
    }

    /** Sets ALLOW_WRITES. */
    public void setAllowWrites(final boolean allowWrites) {
        this.allowWrites = allowWrites;
    }

    /** The storage locations, each keyed by upper-case property name, in their written order. */
    public List<Map<String, PropertyValue>> getStorageLocations() {
        return storageLocations;
    }

    /** When the volume was dropped, or null while it exists. */
    public Instant getDroppedOn() {
        return droppedOn;
    }

    /** Records when the volume was dropped; null restores it. */
    public void setDroppedOn(final Instant droppedOn) {
        this.droppedOn = droppedOn;
    }
}
