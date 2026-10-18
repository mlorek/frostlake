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

package dev.frostlake.metastore;

import dev.frostlake.executor.StatementClock;
import dev.frostlake.storage.Row;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A snapshot of a dropped object retained for UNDROP (Time Travel restore): the metadata object
 * ({@code Table} / {@code Schema} / {@code Database}) plus the row data captured at drop time — a single
 * row list for a dropped table, or {@link #getTableRows()} keyed by fully-qualified table name for a dropped
 * schema or database (which may contain many tables). The rows are snapshotted because the drop RELEASES the
 * tables' storage, so the names are free to be created again while the data stays restorable.
 *
 * <p>A version is kept for the DATA_RETENTION_TIME_IN_DAYS the object had when it was dropped, and is purged once that
 * has passed ({@link #isPurgedAt}): UNDROP no longer finds it and SHOW ... HISTORY no longer lists it. With a
 * retention of 0 the object cannot be undropped at all.
 *
 * <p>The dropped tables' storage itself waits with the version ({@link #keptStorage}), so an UNDROP brings each table
 * back with its time-travel history: a point before the drop, or while the table was dropped, reads the rows it held
 * then (live-verified). The rows are kept beside it for what reads a version without restoring it.
 */
public class DroppedObject {

    private final Object object;
    private final List<Row> rows;
    private final Map<String, List<Row>> tableRows;
    private final Map<String, List<Row>> hiddenTableRows;
    /** The dropping statement's instant, as its CURRENT_TIMESTAMP reads it. */
    private final Instant droppedOn;
    /** The retention the object had when it was dropped, in days. */
    private final int retentionDays;
    /**
     * The token naming where the stage files of a dropped table, or of the tables and stages in a dropped schema or
     * database, wait for its UNDROP. The DROP drew it ({@code StatementTokens}, so its replay draws the same one); null
     * for every other kind, and for a version a snapshot older than the token kept.
     */
    private final String stageToken;

    /**
     * Where the dropped table's storage waits while other sessions' open transactions that wrote it still commit
     * there (see {@code TransactionManager.setAsideIfWritten}); null when it was dropped outright. The engine's own,
     * never saved with a snapshot: a restart reads the rows instead.
     */
    private volatile String storageSetAside;

    /**
     * Where each dropped table's storage waits for the UNDROP, by the table's fully-qualified name: the key the
     * storage engine keeps it under ({@code StorageEngine.keepDropped}). Empty for a table dropped outright and for a
     * version a snapshot restored without its storage. The engine's own, as {@link #storageSetAside} is.
     */
    private final Map<String, String> keptStorage = new LinkedHashMap<>();

    /**
     * A dropped table (or another object that holds no rows of its own, with null rows).
     *
     * @param object the dropped object
     * @param rows the table's rows, or null
     * @param retentionDays the retention the object had when it was dropped ({@link RetentionChain})
     */
    public DroppedObject(final Object object, final List<Row> rows, final int retentionDays) {
        this(object, rows, null, null, retentionDays, StatementClock.instant(), null);
    }

    /**
     * A dropped table whose stage files were set aside under a token of its own.
     *
     * @param object the dropped table
     * @param rows the table's rows
     * @param retentionDays the retention the table had when it was dropped ({@link RetentionChain})
     * @param stageToken where its stage files wait for an UNDROP
     */
    public DroppedObject(final Object object, final List<Row> rows, final int retentionDays,
                         final String stageToken) {
        this(object, rows, null, null, retentionDays, StatementClock.instant(), stageToken);
    }

    /**
     * A dropped schema or database.
     *
     * @param object the dropped schema or database
     * @param tableRows the rows of every table in it, keyed by fully-qualified name
     * @param hiddenTableRows the rows of every permanent table a temporary one hid in it, keyed alike
     * @param retentionDays the retention the object had when it was dropped ({@link RetentionChain})
     */
    public DroppedObject(final Object object, final Map<String, List<Row>> tableRows,
                         final Map<String, List<Row>> hiddenTableRows, final int retentionDays) {
        this(object, null, tableRows, hiddenTableRows, retentionDays, StatementClock.instant(), null);
    }

    /**
     * A dropped schema or database whose tables' and stages' files were set aside under a token of its own.
     *
     * @param object the dropped schema or database
     * @param tableRows the rows of every table in it, keyed by fully-qualified name
     * @param hiddenTableRows the rows of every permanent table a temporary one hid in it, keyed alike
     * @param retentionDays the retention the object had when it was dropped ({@link RetentionChain})
     * @param stageToken where its tables' and stages' files wait for an UNDROP
     */
    public DroppedObject(final Object object, final Map<String, List<Row>> tableRows,
                         final Map<String, List<Row>> hiddenTableRows, final int retentionDays,
                         final String stageToken) {
        this(object, null, tableRows, hiddenTableRows, retentionDays, StatementClock.instant(), stageToken);
    }

    private DroppedObject(final Object object, final List<Row> rows, final Map<String, List<Row>> tableRows,
                          final Map<String, List<Row>> hiddenTableRows, final int retentionDays,
                          final Instant droppedOn, final String stageToken) {
        this.object = object;
        this.rows = rows;
        this.tableRows = tableRows;
        this.hiddenTableRows = hiddenTableRows;
        this.retentionDays = retentionDays;
        this.droppedOn = droppedOn;
        this.stageToken = stageToken;
    }

    /**
     * A dropped version as a snapshot kept it, with no stage files waiting for it.
     *
     * @param object the dropped object
     * @param rows a dropped table's rows, or null
     * @param tableRows a dropped schema's or database's table rows, or null
     * @param hiddenTableRows a dropped schema's or database's hidden table rows, or null
     * @param retentionDays the retention the object had when it was dropped
     * @param droppedOn when it was dropped
     * @return the version
     */
    public static DroppedObject restored(final Object object, final List<Row> rows,
                                         final Map<String, List<Row>> tableRows,
                                         final Map<String, List<Row>> hiddenTableRows, final int retentionDays,
                                         final Instant droppedOn) {
        return restored(object, rows, tableRows, hiddenTableRows, retentionDays, droppedOn, null);
    }

    /**
     * A dropped version as a snapshot kept it: dropped when it was, and kept as long as it was going to be.
     *
     * @param object the dropped object
     * @param rows a dropped table's rows, or null
     * @param tableRows a dropped schema's or database's table rows, or null
     * @param hiddenTableRows a dropped schema's or database's hidden table rows, or null
     * @param retentionDays the retention the object had when it was dropped
     * @param droppedOn when it was dropped
     * @param stageToken where a dropped table's stage files wait, or null
     * @return the version
     */
    public static DroppedObject restored(final Object object, final List<Row> rows,
                                         final Map<String, List<Row>> tableRows,
                                         final Map<String, List<Row>> hiddenTableRows, final int retentionDays,
                                         final Instant droppedOn, final String stageToken) {
        return new DroppedObject(object, rows, tableRows, hiddenTableRows, retentionDays, droppedOn, stageToken);
    }

    /** When the object was dropped. */
    public Instant getDroppedOn() {
        return droppedOn;
    }

    /** The retention the object had when it was dropped, in days. */
    public int getRetentionDays() {
        return retentionDays;
    }

    /** Where the version's stage files wait for its UNDROP, or null (see the field). */
    public String getStageToken() {
        return stageToken;
    }

    /**
     * Whether the version's retention has run out at {@code now}, so that UNDROP can no longer restore it.
     *
     * @param now the current statement's instant
     * @return whether it is purged
     */
    public boolean isPurgedAt(final Instant now) {
        return !droppedOn.plus(Duration.ofDays(Math.max(retentionDays, 0))).isAfter(now);
    }

    public Object getObject() {
        return object;
    }

    public List<Row> getRows() {
        return rows;
    }

    /** Rows of every table in a dropped schema/database, keyed by fully-qualified name; null for a table. */
    public Map<String, List<Row>> getTableRows() {
        return tableRows;
    }

    /**
     * Rows of every permanent table a temporary one hid in a dropped schema/database, keyed like
     * {@link #getTableRows()}; null for a table.
     */
    public Map<String, List<Row>> getHiddenTableRows() {
        return hiddenTableRows;
    }

    /** Where the dropped table's storage waits for the transactions that wrote it, or null. */
    public String getStorageSetAside() {
        return storageSetAside;
    }

    /**
     * Notes where the dropped table's storage waits for the transactions that wrote it.
     *
     * @param key the key it waits under
     */
    public void setStorageSetAside(final String key) {
        this.storageSetAside = key;
    }

    /**
     * Notes where a dropped table's storage waits for the UNDROP.
     *
     * @param qualifiedName the table's fully-qualified name
     * @param key           the key the storage engine keeps it under
     */
    public void keepStorage(final String qualifiedName, final String key) {
        synchronized (keptStorage) {
            keptStorage.put(qualifiedName, key);
        }
    }

    /**
     * Where a dropped table's storage waits, or null when the version keeps only its rows.
     *
     * @param qualifiedName the table's fully-qualified name
     * @return the storage engine's key for it, or null
     */
    public String keptStorageOf(final String qualifiedName) {
        synchronized (keptStorage) {
            return keptStorage.get(qualifiedName);
        }
    }

    /** Where every dropped table's storage waits, by the table's fully-qualified name: a copy. */
    public Map<String, String> keptStorages() {
        synchronized (keptStorage) {
            return new LinkedHashMap<>(keptStorage);
        }
    }
}
