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

import dev.frostlake.metastore.model.DynamicTableRefreshInput;
import dev.frostlake.metastore.model.DynamicTableRefreshRecord;
import dev.frostlake.metastore.model.RefreshTrigger;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One refresh of a dynamic table, as DYNAMIC_TABLE_REFRESH_HISTORY lists it (see {@link DynamicTableRefreshRecord}),
 * as a snapshot holds it: the history outlives its tables, and a restored engine lists what the former one ran.
 */
public class DynamicTableRefreshRecordSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String database;
    public String schema;
    public String table;
    public String targetLag;
    public Instant graphValidFrom;
    /** What started the refresh, as the trigger's name. */
    public String trigger;
    public String action;
    public String queryId;
    public Instant start;
    public Instant end;
    /** The rows inserted, copied and deleted, then the micro-partitions added and removed. */
    public long[] changed;
    public String warehouse;
    /** Each input whose data changed: its kind, its name, and its partitions added and removed with their rows. */
    public ArrayList<String> inputKinds;
    public ArrayList<String> inputNames;
    public ArrayList<long[]> inputPartitions;
    public String dependency;
    public Instant dependencyTimestamp;

    /**
     * A record as a snapshot keeps it.
     *
     * @param record the record
     * @return the snapshot
     */
    public static DynamicTableRefreshRecordSnapshot of(final DynamicTableRefreshRecord record) {
        final DynamicTableRefreshRecordSnapshot saved = new DynamicTableRefreshRecordSnapshot();
        saved.database = record.getDatabase();
        saved.schema = record.getSchema();
        saved.table = record.getTable();
        saved.targetLag = record.getTargetLag();
        saved.graphValidFrom = record.getGraphValidFrom();
        saved.trigger = record.getTrigger().name();
        saved.action = record.getAction();
        saved.queryId = record.getQueryId();
        saved.start = record.getStart();
        saved.end = record.getEnd();
        saved.changed = record.getChanged();
        saved.warehouse = record.getWarehouse();
        saved.inputKinds = new ArrayList<>();
        saved.inputNames = new ArrayList<>();
        saved.inputPartitions = new ArrayList<>();
        for (final DynamicTableRefreshInput input : record.getInputs()) {
            saved.inputKinds.add(input.getKind());
            saved.inputNames.add(input.getName());
            saved.inputPartitions.add(new long[] {input.getAddedPartitions(), input.getRegisteredRows(),
                input.getRemovedPartitions(), input.getUnregisteredRows()});
        }
        saved.dependency = record.getDependency();
        saved.dependencyTimestamp = record.getDependencyTimestamp();
        return saved;
    }

    /**
     * The record this snapshot keeps.
     *
     * @return the record
     */
    public DynamicTableRefreshRecord toRecord() {
        final List<DynamicTableRefreshInput> inputs = new ArrayList<>();
        for (int i = 0; inputKinds != null && i < inputKinds.size(); i++) {
            final long[] partitions = inputPartitions.get(i);
            inputs.add(new DynamicTableRefreshInput(inputKinds.get(i), inputNames.get(i), partitions[0],
                partitions[1], partitions[2], partitions[3]));
        }
        return new DynamicTableRefreshRecord(database, schema, table, targetLag, graphValidFrom,
            RefreshTrigger.valueOf(trigger), action, queryId, start, end, changed, warehouse, inputs, dependency,
            dependencyTimestamp);
    }
}
