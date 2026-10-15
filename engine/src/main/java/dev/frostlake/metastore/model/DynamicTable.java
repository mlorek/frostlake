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
import dev.frostlake.storage.SnapshotSequence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
    private Instant lastRefreshedTime;
    /** The first refresh, the start of the table's data history; see {@link #getFirstRefreshedSequence()}. */
    private long firstRefreshedSequence;
    private long firstRefreshedMillis;
    private Instant lastSuspendedOn;
    private final List<String> clusterKeys = new ArrayList<>();
    /** The columns the table declares in its own column list, in order; empty when it has none. */
    private final List<DynamicTableColumn> declaredColumns = new ArrayList<>();
    /** The table's columns as its query and its column list resolved them when it was created; null before. */
    private List<TableColumn> resolvedColumns;
    /** The rows the table's last refresh held, which is what a listing counts. */
    private long refreshedRows;
    /** The rows of the last refresh this engine ran, each as its values; null when it has run none. */
    private List<List<Object>> refreshImage;
    private boolean transientTable;
    private String schedulingState;    // descriptive status returned by SHOW
    private String createText;
    private String bodyText;
    private boolean reclusterSuspended;
    private boolean clone;
    /** The database and schema the query's bare names are read in once the table has moved; null where it lives. */
    private String definitionDatabase;
    private String definitionSchema;

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
    /** When the table last refreshed, which SHOW DYNAMIC TABLES reports as its data_timestamp; null before any. */
    public Instant getLastRefreshedTime() { return lastRefreshedTime; }
    public void setLastRefreshedTime(final Instant t) {
        if (t != null && firstRefreshedSequence == 0L) {
            firstRefreshedSequence = SnapshotSequence.mark();
            firstRefreshedMillis = t.toEpochMilli();
        }
        this.lastRefreshedTime = t;
    }

    /**
     * Where the table's first refresh falls in the order table snapshots are taken in — the start of its data's
     * history — or 0 when it has not been refreshed.
     *
     * @return the sequence number, or 0
     */
    public long getFirstRefreshedSequence() { return firstRefreshedSequence; }

    /**
     * When the table was first refreshed, in epoch milliseconds, or 0 when it has not been.
     *
     * @return the instant, or 0
     */
    public long getFirstRefreshedMillis() { return firstRefreshedMillis; }

    /** When the table was suspended, or null while it runs: a RESUME clears it (live-verified). */
    public Instant getLastSuspendedOn() { return lastSuspendedOn; }
    public void setLastSuspendedOn(final Instant t) { this.lastSuspendedOn = t; }

    /** The CLUSTER BY keys, each as written; empty for an unclustered table. */
    public List<String> getClusterKeys() { return new ArrayList<>(clusterKeys); }
    public void setClusterKeys(final List<String> keys) {
        clusterKeys.clear();
        clusterKeys.addAll(keys);
    }

    /** Whether the table was created TRANSIENT. */
    /** The columns the table declares in its own column list, in order; empty when it has none. */
    public List<DynamicTableColumn> getDeclaredColumns() { return new ArrayList<>(declaredColumns); }

    /** Replaces the declared column list. */
    public void setDeclaredColumns(final List<DynamicTableColumn> columns) {
        declaredColumns.clear();
        if (columns != null) {
            declaredColumns.addAll(columns);
        }
    }

    /** The table's columns as they resolved when it was created, or null when none were kept. */
    public List<TableColumn> getResolvedColumns() { return resolvedColumns; }

    /** Keeps the table's columns as they resolved. */
    public void setResolvedColumns(final List<TableColumn> columns) { this.resolvedColumns = columns; }

    /** The rows the table's last refresh held. */
    public long getRefreshedRows() { return refreshedRows; }

    /** Records the rows a refresh held. */
    public void setRefreshedRows(final long rows) { this.refreshedRows = rows; }

    /** The rows of the last refresh this engine ran, or null when it has run none. */
    public List<List<Object>> getRefreshImage() { return refreshImage; }

    /** Keeps the rows a refresh read, for the next refresh to tell what changed. */
    public void setRefreshImage(final List<List<Object>> image) { this.refreshImage = image; }

    /**
     * Puts back the refresh state a snapshot of the engine's state saved, as it was: when the table last refreshed,
     * where and when its first refresh fell, and the rows its last refresh read.
     *
     * @param lastRefreshed  when the table last refreshed
     * @param firstSequence  where its first refresh fell in the order table snapshots are taken in, or 0
     * @param firstMillis    when its first refresh ran, or 0
     * @param image          the rows of its last refresh, or null
     */
    public void restoreRefreshState(final Instant lastRefreshed, final long firstSequence, final long firstMillis,
                                    final List<List<Object>> image) {
        this.lastRefreshedTime = lastRefreshed;
        this.firstRefreshedSequence = firstSequence;
        this.firstRefreshedMillis = firstMillis;
        this.refreshImage = image;
    }

    public boolean isTransient() { return transientTable; }
    public void setTransient(final boolean transientTable) { this.transientTable = transientTable; }
    public String getSchedulingState() { return schedulingState; }
    public void setSchedulingState(final String s) { this.schedulingState = s; }

    public boolean isSuspended() { return state == State.SUSPENDED; }

    /**
     * The CREATE as written up to the options: its words through the name, then the column list and the
     * COMMENT it was written with. SHOW DYNAMIC TABLES re-prints the options after it from the table's
     * current settings.
     */
    public String getCreateText() { return createText; }
    public void setCreateText(final String createText) { this.createText = createText; }

    /** The AS and the query after it, exactly as written. */
    public String getBodyText() { return bodyText; }
    public void setBodyText(final String bodyText) { this.bodyText = bodyText; }

    /** Whether automatic reclustering is suspended; it only runs on a table that has a clustering key. */
    public boolean isReclusterSuspended() { return reclusterSuspended; }
    public void setReclusterSuspended(final boolean suspended) { this.reclusterSuspended = suspended; }

    /** Whether the table was created as a clone of another dynamic table. */
    public boolean isClone() { return clone; }
    public void setClone(final boolean clone) { this.clone = clone; }

    /**
     * A new dynamic table of another name with this one's definition and settings: its query, lag, warehouse,
     * refresh and initialization modes, retention, clustering key, comment and the text it was created with.
     */
    public DynamicTable copyAs(final String newName) {
        final DynamicTable copy = new DynamicTable(newName, query, targetLag, warehouse);
        copy.refreshMode = refreshMode;
        copy.initialize = initialize;
        copy.dataRetentionDays = dataRetentionDays;
        copy.maxDataExtensionDays = maxDataExtensionDays;
        copy.clusterKeys.addAll(clusterKeys);
        copy.declaredColumns.addAll(declaredColumns);
        copy.resolvedColumns = resolvedColumns;
        copy.refreshedRows = refreshedRows;
        copy.transientTable = transientTable;
        copy.createText = createText;
        copy.bodyText = bodyText;
        copy.lastRefreshedTime = lastRefreshedTime;
        copy.firstRefreshedSequence = firstRefreshedSequence;
        copy.firstRefreshedMillis = firstRefreshedMillis;
        copy.setComment(getComment());
        return copy;
    }

    @Override
    public void rename(final String newName) { throw new UnsupportedOperationException("Dynamic tables cannot be renamed directly"); }

    /**
     * Records where the table's query reads its bare names, before the table moves to another schema: the
     * schema it was defined in, which a later move does not change.
     *
     * @param database the database the table is leaving
     * @param schema   the schema the table is leaving
     */
    public void keepDefinitionHome(final String database, final String schema) {
        if (definitionDatabase == null) {
            definitionDatabase = database;
            definitionSchema = schema;
        }
    }

    /** The database the query's bare names are read in, or null for the one the table lives in. */
    public String getDefinitionDatabase() { return definitionDatabase; }

    /** The schema the query's bare names are read in, or null for the one the table lives in. */
    public String getDefinitionSchema() { return definitionSchema; }

    @Override
    public String getObjectType() { return "DYNAMIC TABLE"; }
}
