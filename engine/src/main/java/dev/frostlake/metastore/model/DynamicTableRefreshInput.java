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
 * An input whose data changed for a refresh of a dynamic table, as INPUTS_WITH_CHANGED_DATA tells it: its kind, TABLE,
 * VIEW or DYNAMIC_TABLE, its qualified name, and its micro-partitions since the refresh before — the ones added and
 * the rows they hold, and the ones removed and the rows they held (live-verified); a view's are its base tables'
 * (documented).
 */
public final class DynamicTableRefreshInput {

    private final String kind;
    private final String name;
    private final long addedPartitions;
    private final long registeredRows;
    private final long removedPartitions;
    private final long unregisteredRows;

    /**
     * An input that changed.
     *
     * @param kind              TABLE, VIEW or DYNAMIC_TABLE
     * @param name              the input's qualified name
     * @param addedPartitions   the partitions added since the refresh before
     * @param registeredRows    the rows they hold
     * @param removedPartitions the partitions removed since then
     * @param unregisteredRows  the rows they held
     */
    public DynamicTableRefreshInput(final String kind, final String name, final long addedPartitions,
                                    final long registeredRows, final long removedPartitions,
                                    final long unregisteredRows) {
        this.kind = kind;
        this.name = name;
        this.addedPartitions = addedPartitions;
        this.registeredRows = registeredRows;
        this.removedPartitions = removedPartitions;
        this.unregisteredRows = unregisteredRows;
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public long getAddedPartitions() {
        return addedPartitions;
    }

    public long getRegisteredRows() {
        return registeredRows;
    }

    public long getRemovedPartitions() {
        return removedPartitions;
    }

    public long getUnregisteredRows() {
        return unregisteredRows;
    }
}
