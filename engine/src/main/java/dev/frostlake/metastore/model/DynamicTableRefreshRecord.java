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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One refresh of a dynamic table, as INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY tells it: the table as it was
 * named and set then, what started the refresh, what it did — INCREMENTAL, FULL, or NO_DATA when nothing it reads had
 * changed — when it ran, the rows it inserted, copied and deleted and the micro-partitions it added and removed, the
 * warehouse it ran on (none for NO_DATA), the inputs whose data changed for it and the dynamic table it depended on.
 * It outlives the table, so it keeps what it tells of the table.
 */
public final class DynamicTableRefreshRecord {

    private final String database;
    private final String schema;
    private final String table;
    private final String targetLag;
    private final Instant graphValidFrom;
    private final RefreshTrigger trigger;
    private final String action;
    private final String queryId;
    private final Instant start;
    private final Instant end;
    private final long[] changed;
    private final String warehouse;
    private final List<DynamicTableRefreshInput> inputs;
    private final String dependency;
    private final Instant dependencyTimestamp;

    /**
     * A refresh that ran.
     *
     * @param database            the database the table lived in, its name canonical
     * @param schema              the schema the table lived in, its name canonical
     * @param table               the table's name then, canonical
     * @param targetLag           the table's target lag then, as written ({@code 1 hour}, {@code DOWNSTREAM})
     * @param graphValidFrom      when the table's version that refreshed was made
     * @param trigger             what started it
     * @param action              INCREMENTAL, FULL or NO_DATA
     * @param queryId             the id of the query that refreshed the table
     * @param start               when it started, the data timestamp of what it read
     * @param end                 when it ended
     * @param changed             the rows inserted, copied and deleted, then the micro-partitions added and removed
     * @param warehouse           the warehouse it ran on, or null
     * @param inputs              the inputs whose data changed for it
     * @param dependency          the qualified name of the dynamic table it read last, or null
     * @param dependencyTimestamp that table's data timestamp, or null
     */
    public DynamicTableRefreshRecord(final String database, final String schema, final String table,
                                     final String targetLag, final Instant graphValidFrom,
                                     final RefreshTrigger trigger, final String action, final String queryId,
                                     final Instant start, final Instant end, final long[] changed,
                                     final String warehouse, final List<DynamicTableRefreshInput> inputs,
                                     final String dependency, final Instant dependencyTimestamp) {
        this.database = database;
        this.schema = schema;
        this.table = table;
        this.targetLag = targetLag;
        this.graphValidFrom = graphValidFrom;
        this.trigger = trigger;
        this.action = action;
        this.queryId = queryId;
        this.start = start;
        this.end = end;
        this.changed = changed.clone();
        this.warehouse = warehouse;
        this.inputs = new ArrayList<>(inputs);
        this.dependency = dependency;
        this.dependencyTimestamp = dependencyTimestamp;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    /** The table's name when the refresh ran. */
    public String getTable() {
        return table;
    }

    /** The table's target lag when the refresh ran, as written. */
    public String getTargetLag() {
        return targetLag;
    }

    /** When the table's version that refreshed was made. */
    public Instant getGraphValidFrom() {
        return graphValidFrom;
    }

    public RefreshTrigger getTrigger() {
        return trigger;
    }

    public String getAction() {
        return action;
    }

    public String getQueryId() {
        return queryId;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    /** The rows the refresh inserted, copied and deleted, then the micro-partitions it added and removed. */
    public long[] getChanged() {
        return changed.clone();
    }

    public String getWarehouse() {
        return warehouse;
    }

    /** The inputs whose data changed for the refresh, each with its kind, name and micro-partitions. */
    public List<DynamicTableRefreshInput> getInputs() {
        return new ArrayList<>(inputs);
    }

    public String getDependency() {
        return dependency;
    }

    public Instant getDependencyTimestamp() {
        return dependencyTimestamp;
    }
}
