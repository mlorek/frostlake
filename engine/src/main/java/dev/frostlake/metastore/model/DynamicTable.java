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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a Snowflake DYNAMIC TABLE — a table whose contents are automatically
 * refreshed from a query, with a configurable target lag.
 */
public class DynamicTable extends SqlObject {

    /** How many refreshes a table keeps for time travel (see {@link #keepRefresh}). */
    public static final int KEPT_REFRESHES = 256;

    private final String query;
    private String targetLag;          // e.g. "1 minutes", "DOWNSTREAM"
    private String warehouse;
    private RefreshMode refreshMode;
    // Why an AUTO refresh mode settled on FULL, or null when it runs incrementally (see getFullRefreshReason).
    private String fullRefreshReason;
    private Initialize initialize;
    private int dataRetentionDays;
    private boolean dataRetentionSet;
    private int maxDataExtensionDays;
    private boolean maxDataExtensionSet;
    /**
     * The parameters the table set for itself beside its retention, by name, each as SHOW PARAMETERS lists it:
     * LOG_LEVEL, TRACE_LEVEL, LOG_EVENT_LEVEL, DATA_METRIC_SCHEDULE, DEFAULT_DDL_COLLATION and the like.
     */
    private final Map<String, String> parameters = new LinkedHashMap<>();
    private State state;
    private Instant lastRefreshedTime;
    /** The first refresh, the start of the table's data history; see {@link #getFirstRefreshedSequence()}. */
    private long firstRefreshedSequence;
    private long firstRefreshedMillis;
    private Instant lastSuspendedOn;
    private final List<String> clusterKeys = new ArrayList<>();
    /** The text the statement that set the key wrote after CLUSTER BY, or null where none was kept. */
    private String clusterByText;
    /** The columns the table declares in its own column list, in order; empty when it has none. */
    private final List<DynamicTableColumn> declaredColumns = new ArrayList<>();
    /** The table's columns as its query and its column list resolved them when it was created; null before. */
    private List<TableColumn> resolvedColumns;
    /** The rows the table's last refresh held, which is what a listing counts. */
    private long refreshedRows;
    /** The rows of the last refresh this engine ran, each as its values; null when it has run none. */
    private List<List<Object>> refreshImage;
    /** The micro-partition each row of the refresh image lies in, in step with it; null where none was kept. */
    private List<Long> imagePartitions;
    /**
     * Each input's micro-partitions as the last refresh read them, by the input's qualified name: each partition's
     * number and the rows it holds (see {@link #getInputPartitions}).
     */
    private final Map<String, Map<Long, Long>> inputPartitions = new LinkedHashMap<>();
    /** The refreshes this engine ran, oldest first, for a read or a stream at a past point (see {@link #refreshAt}). */
    private final List<DynamicTableRefresh> refreshes = new ArrayList<>();
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

    /**
     * Why the table, created with REFRESH_MODE AUTO, settled on FULL: the sentence SHOW DYNAMIC TABLES tells as its
     * refresh_mode_reason.
     *
     * @return the reason, or null when the table runs incrementally or its mode was not AUTO
     */
    public String getFullRefreshReason() { return fullRefreshReason; }

    /**
     * Records why an AUTO refresh mode settled on FULL.
     *
     * @param fullRefreshReason the reason, or null for an incremental table
     */
    public void setFullRefreshReason(final String fullRefreshReason) { this.fullRefreshReason = fullRefreshReason; }
    public Initialize getInitialize() { return initialize; }
    public void setInitialize(final Initialize initialize) { this.initialize = initialize; }
    public int getDataRetentionDays() { return dataRetentionDays; }

    /**
     * Sets the table's own DATA_RETENTION_TIME_IN_DAYS, which it then reports in place of what it inherits.
     *
     * @param days the retention
     */
    public void setDataRetentionDays(final int days) {
        this.dataRetentionDays = days;
        this.dataRetentionSet = true;
    }

    /** Whether the table set its own DATA_RETENTION_TIME_IN_DAYS, rather than inheriting its schema's. */
    public boolean isDataRetentionSet() { return dataRetentionSet; }
    public int getMaxDataExtensionDays() { return maxDataExtensionDays; }

    /**
     * Sets the table's own MAX_DATA_EXTENSION_TIME_IN_DAYS, which it then reports in place of what it inherits.
     *
     * @param days the extension
     */
    public void setMaxDataExtensionDays(final int days) {
        this.maxDataExtensionDays = days;
        this.maxDataExtensionSet = true;
    }

    /** Whether the table set its own MAX_DATA_EXTENSION_TIME_IN_DAYS. */
    public boolean isMaxDataExtensionSet() { return maxDataExtensionSet; }

    /** Takes the table's own DATA_RETENTION_TIME_IN_DAYS away: it inherits its schema's again. */
    public void unsetDataRetentionDays() {
        this.dataRetentionDays = 1;
        this.dataRetentionSet = false;
    }

    /** Takes the table's own MAX_DATA_EXTENSION_TIME_IN_DAYS away: it inherits its schema's again. */
    public void unsetMaxDataExtensionDays() {
        this.maxDataExtensionDays = 14;
        this.maxDataExtensionSet = false;
    }

    /**
     * The table's own value of a parameter, or null when it inherits it.
     *
     * @param key the parameter's name
     * @return the value as SHOW PARAMETERS lists it, or null
     */
    public String getParameter(final String key) {
        return parameters.get(key);
    }

    /**
     * Sets, or with null takes away, the table's own value of a parameter.
     *
     * @param key   the parameter's name
     * @param value the value as SHOW PARAMETERS lists it, or null
     */
    public void setParameter(final String key, final String value) {
        if (value == null) {
            parameters.remove(key);
        } else {
            parameters.put(key, value);
        }
    }

    /** The parameters the table set for itself, by name: a copy. */
    public Map<String, String> getParameters() {
        return new LinkedHashMap<>(parameters);
    }
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
        setClusterKeys(keys, null);
    }

    /**
     * Sets the CLUSTER BY keys with the text the statement wrote after CLUSTER BY, which SHOW and GET_DDL read back.
     *
     * @param keys the keys, each as written; empty for no key
     * @param written the text after CLUSTER BY as written, or null
     */
    public void setClusterKeys(final List<String> keys, final String written) {
        clusterKeys.clear();
        clusterKeys.addAll(keys);
        clusterByText = clusterKeys.isEmpty() ? null : written;
    }

    /**
     * The clustering key as written after CLUSTER BY: the statement's own text where it was kept, the keys in
     * parentheses otherwise.
     *
     * @return the text, or null for a table with no key
     */
    public String getClusterByText() {
        if (clusterKeys.isEmpty()) {
            return null;
        }
        return clusterByText != null ? clusterByText : "(" + String.join(", ", clusterKeys) + ")";
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
     * The micro-partition each row of the refresh image lies in, in step with the image: a refresh adds one
     * partition for the rows it writes, and a partition it deletes from goes, the rows it kept copied into the new
     * one (live-verified).
     *
     * @return the partitions, or null where none were kept
     */
    public List<Long> getImagePartitions() { return imagePartitions; }

    /** Keeps the micro-partition of each row of the refresh image, in step with it. */
    public void setImagePartitions(final List<Long> partitions) { this.imagePartitions = partitions; }

    /**
     * The table's micro-partitions now, each by its number with the rows it holds, oldest first: what a dynamic table
     * that reads this one compares with what it read last. A refresh image kept with no partitions is one partition.
     *
     * @return the partitions
     */
    public Map<Long, Long> partitions() {
        final Map<Long, Long> held = new LinkedHashMap<>();
        if (refreshImage == null || refreshImage.isEmpty()) {
            return held;
        }
        if (imagePartitions == null || imagePartitions.size() != refreshImage.size()) {
            imagePartitions = new ArrayList<>();
            final Long whole = Long.valueOf(SnapshotSequence.mark());
            for (int i = 0; i < refreshImage.size(); i++) {
                imagePartitions.add(whole);
            }
        }
        for (final Long partition : imagePartitions) {
            final Long rows = held.get(partition);
            held.put(partition, rows == null ? 1L : rows + 1L);
        }
        return held;
    }

    /**
     * An input's micro-partitions as the last refresh read them: each by its number with the rows it held. A table
     * is one partition while it holds rows, numbered by its last write; a dynamic table its refreshes' partitions.
     *
     * @param input the input's qualified name
     * @return the partitions, empty when the table read none of it
     */
    public synchronized Map<Long, Long> getInputPartitions(final String input) {
        final Map<Long, Long> read = inputPartitions.get(input);
        return read == null ? new LinkedHashMap<Long, Long>() : new LinkedHashMap<>(read);
    }

    /** Keeps an input's micro-partitions as a refresh read them. */
    public synchronized void setInputPartitions(final String input, final Map<Long, Long> partitions) {
        inputPartitions.put(input, new LinkedHashMap<>(partitions));
    }

    /** Every input's micro-partitions as the last refresh read them, as a snapshot of the engine's state saves them. */
    public synchronized Map<String, Map<Long, Long>> getAllInputPartitions() {
        final Map<String, Map<Long, Long>> copy = new LinkedHashMap<>();
        for (final Map.Entry<String, Map<Long, Long>> input : inputPartitions.entrySet()) {
            copy.put(input.getKey(), new LinkedHashMap<>(input.getValue()));
        }
        return copy;
    }

    /**
     * Keeps a refresh for time travel. Only the latest {@link #KEPT_REFRESHES} are kept: a point before the oldest
     * kept one reads as one the table's history no longer reaches.
     *
     * @param refresh the refresh that just ran
     */
    public synchronized void keepRefresh(final DynamicTableRefresh refresh) {
        refreshes.add(refresh);
        if (refreshes.size() > KEPT_REFRESHES) {
            refreshes.remove(0);
        }
    }

    /**
     * The refresh a read at a past point sees: the last one before the point, or at it unless the point is BEFORE.
     * A statement's point is placed by the order snapshots are taken in, which tells apart two statements inside
     * one millisecond; any other point by its time.
     *
     * @param sequence where a statement's point falls among the snapshots, or -1 for a point given as a time
     * @param millis   the point's time, in epoch milliseconds
     * @param before   whether the point is BEFORE rather than AT
     * @return the refresh, or null when none this engine still keeps came before the point
     */
    public synchronized DynamicTableRefresh refreshAt(final long sequence, final long millis, final boolean before) {
        DynamicTableRefresh seen = null;
        for (final DynamicTableRefresh refresh : refreshes) {
            final long at = sequence >= 0 ? refresh.getSequence() : refresh.getMillis();
            final long point = sequence >= 0 ? sequence : millis;
            if (before ? at >= point : at > point) {
                break;
            }
            seen = refresh;
        }
        return seen;
    }

    /**
     * The refreshes the table keeps for time travel, oldest first, as a snapshot of the engine's state saves them.
     *
     * @return a copy of the kept refreshes
     */
    public synchronized List<DynamicTableRefresh> getRefreshes() {
        return new ArrayList<DynamicTableRefresh>(refreshes);
    }

    /**
     * Puts back the refreshes a snapshot of the engine's state saved, so a read AT or BEFORE a point before a restart
     * reaches the rows the table held then.
     *
     * @param kept the refreshes, oldest first
     */
    public synchronized void restoreRefreshes(final List<DynamicTableRefresh> kept) {
        refreshes.clear();
        for (final DynamicTableRefresh refresh : kept) {
            keepRefresh(refresh);
        }
    }

    /**
     * The last refresh this engine ran and still keeps.
     *
     * @return the refresh, or null when it has run none
     */
    public synchronized DynamicTableRefresh lastRefresh() {
        return refreshes.isEmpty() ? null : refreshes.get(refreshes.size() - 1);
    }

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
     * refresh and initialization modes, retention, parameters, clustering key, comment and the text it was created
     * with.
     */
    public DynamicTable copyAs(final String newName) {
        final DynamicTable copy = new DynamicTable(newName, query, targetLag, warehouse);
        copy.refreshMode = refreshMode;
        copy.initialize = initialize;
        copy.dataRetentionDays = dataRetentionDays;
        copy.dataRetentionSet = dataRetentionSet;
        copy.maxDataExtensionDays = maxDataExtensionDays;
        copy.maxDataExtensionSet = maxDataExtensionSet;
        copy.parameters.putAll(parameters);
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
