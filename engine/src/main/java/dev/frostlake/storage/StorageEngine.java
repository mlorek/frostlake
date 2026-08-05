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

package dev.frostlake.storage;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class StorageEngine {

    private final Map<String, TableStorage> tables;
    private boolean enforcePrimaryKey = false;
    private boolean enforceUniqueKey = false;

    public StorageEngine() {
        this.tables = new ConcurrentHashMap<>();
    }

    public void setEnforcePrimaryKey(final boolean enforce) { this.enforcePrimaryKey = enforce; }
    public void setEnforceUniqueKey(final boolean enforce) { this.enforceUniqueKey = enforce; }
    public boolean isEnforcePrimaryKey() { return enforcePrimaryKey; }
    public boolean isEnforceUniqueKey() { return enforceUniqueKey; }

    // Time-travel snapshots are taken per STATEMENT (see snapshotDirtyTables), not per row: a table is
    // marked dirty on each mutation and snapshotted once after the statement completes. This makes bulk
    // DML O(N) instead of O(N^2) (every insert used to deep-copy the whole table) while keeping
    // statement-level time-travel granularity. Set timeTravel.enabled=false to skip snapshots entirely.
    private boolean timeTravelEnabled = true;
    private final Set<TableStorage> dirtyTables = ConcurrentHashMap.newKeySet();

    public void setTimeTravelEnabled(final boolean enabled) { this.timeTravelEnabled = enabled; }
    public boolean isTimeTravelEnabled() { return timeTravelEnabled; }

    /** Record that a table was mutated by the current statement; it is snapshotted at statement end. */
    void markDirty(final TableStorage table) {
        if (timeTravelEnabled) {
            dirtyTables.add(table);
        }
    }

    /** Take one time-travel snapshot of every table mutated since the last call, then reset. */
    public void snapshotDirtyTables() {
        if (dirtyTables.isEmpty()) {
            return;
        }
        for (final TableStorage table : dirtyTables) {
            table.takeSnapshot();
        }
        dirtyTables.clear();
    }

    public void createTable(final String qualifiedName, final Table tableMetadata) {
        if (tables.containsKey(qualifiedName)) {
            throw new RuntimeException("Table storage already exists: " + qualifiedName);
        }
        tables.put(qualifiedName, new TableStorage(this, tableMetadata));
    }

    public void dropTable(final String qualifiedName) {
        if (!tables.containsKey(qualifiedName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        tables.remove(qualifiedName);
    }

    public boolean hasTable(final String qualifiedName) {
        return tables.containsKey(qualifiedName);
    }

    /** ALTER TABLE a SWAP WITH b: exchange the row storage of two tables (their structure is assumed compatible). */
    public void swapTables(final String fqNameA, final String fqNameB) {
        final TableStorage a = tables.get(fqNameA);
        final TableStorage b = tables.get(fqNameB);
        if (a == null || b == null) {
            throw new RuntimeException("Cannot SWAP: table storage missing for "
                + (a == null ? fqNameA : fqNameB));
        }
        tables.put(fqNameA, b);
        tables.put(fqNameB, a);
    }

    /** ALTER TABLE a RENAME TO b: move the row storage from the old key to the new one. */
    public void renameTable(final String oldQualifiedName, final String newQualifiedName) {
        final TableStorage storage = tables.remove(oldQualifiedName);
        if (storage != null) {
            tables.put(newQualifiedName, storage);
        }
    }

    public TableStorage getTableStorage(final String qualifiedName) {
        TableStorage storage = tables.get(qualifiedName);
        if (storage == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        return storage;
    }

    public void truncateTable(final String qualifiedName) {
        TableStorage storage = getTableStorage(qualifiedName);
        storage.clear();
    }

    public void cloneTableData(final String sourceQualifiedName, final String targetQualifiedName) {
        TableStorage sourceStorage = getTableStorage(sourceQualifiedName);
        TableStorage targetStorage = getTableStorage(targetQualifiedName);

        // Copy all rows from source to target
        for (final Row row : sourceStorage.scan()) {
            // Create a new row with the same values
            Row clonedRow = new Row(new ArrayList<>(row.getValues()));
            targetStorage.insert(clonedRow);
        }
    }

    public static class TableStorage {
        private final StorageEngine engine; // reference to parent for config flags
        private final Table metadata;
        private final List<Row> rows;
        private final Map<Object, Integer> primaryKeyIndex;
        private long nextAutoIncrementValue;
        private long identityIncrement;

        // Stable, monotonic per-row identity, index-aligned with `rows`. Lets a transaction reference a
        // specific row across other mutations WITHOUT positional indices — the foundation for the
        // write-set / deferred-apply transaction model (see transaction.TransactionWriteSet). Internal
        // only: never surfaced in query results, never persisted (reassigned on reload).
        private final List<Long> rowIds = Collections.synchronizedList(new ArrayList<>());
        private long nextRowId = 0;

        /** Immutable timestamped snapshot of the table for time travel. */
        public static class Snapshot {
            public final long epochMillis;
            public final List<Row> rows;
            public Snapshot(final long epochMillis, final List<Row> rows) {
                this.epochMillis = epochMillis;
                this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
            }
        }

        /** Rolling window of snapshots, retention-bounded (default 90 s for testing). */
        private final CopyOnWriteArrayList<Snapshot> snapshots =
            new CopyOnWriteArrayList<>();
        private static final int MAX_SNAPSHOTS = 200;

        /** Force a snapshot now (called after each DML operation). */
        public void takeSnapshot() {
            long now = System.currentTimeMillis();
            // Deep-copy rows so in-place mutations (UPDATE via row.setValue) don't corrupt history
            List<Row> deepCopy = new ArrayList<>(rows.size());
            for (final Row r : rows) deepCopy.add(r.copy());
            snapshots.add(new Snapshot(now, deepCopy));
            if (snapshots.size() > MAX_SNAPSHOTS) {
                snapshots.remove(0);
            }
        }

        /** Return the snapshot whose timestamp is closest to (but not after) targetMillis. */
        public Snapshot snapshotAt(final long targetMillis) {
            Snapshot best = null;
            for (final Snapshot s : snapshots) {
                if (s.epochMillis <= targetMillis) {
                    best = s;
                } else {
                    break;
                }
            }
            return best;
        }

        /** Return the snapshot just BEFORE targetMillis (exclusive). */
        public Snapshot snapshotBefore(final long targetMillis) {
            Snapshot best = null;
            for (final Snapshot s : snapshots) {
                if (s.epochMillis < targetMillis) {
                    best = s;
                } else {
                    break;
                }
            }
            return best;
        }

        /** Return offset seconds ago from now. */
        public Snapshot snapshotAtOffset(final long offsetSeconds) {
            return snapshotAt(System.currentTimeMillis() - offsetSeconds * 1000L);
        }

        public List<Snapshot> getSnapshots() {
            return Collections.unmodifiableList(snapshots);
        }

        public TableStorage(final StorageEngine engine, final Table metadata) {
            this.engine = engine;
            this.metadata = metadata;
            this.rows = Collections.synchronizedList(new ArrayList<>());
            this.primaryKeyIndex = new ConcurrentHashMap<>();

            // Initialize identity values from table metadata
            long startValue = 1;
            long incrementValue = 1;
            for (final TableColumn col : metadata.getColumns()) {
                if (col.isAutoIncrement()) {
                    startValue = col.getIdentityStart();
                    incrementValue = col.getIdentityIncrement();
                    break;
                }
            }
            this.nextAutoIncrementValue = startValue;
            this.identityIncrement = incrementValue;
        }

        public void insert(final Row row) {
            // Validate row
            if (row.getValues().size() != metadata.getColumns().size()) {
                throw new RuntimeException("Row column count mismatch");
            }

            // Handle primary key index (always maintain index; enforce constraint only when enabled)
            if (!metadata.getPrimaryKeys().isEmpty()) {
                Object pk = extractPrimaryKey(row);
                if (pk != null) {
                    if (engine.enforcePrimaryKey && primaryKeyIndex.containsKey(pk)) {
                        throw new RuntimeException("Duplicate primary key: " + pk);
                    }
                    primaryKeyIndex.put(pk, rows.size());
                }
            }

            // Enforce UNIQUE columns when opted in (constraints.enforce.uniqueKey). No unique index is
            // maintained (indexes are intentionally not implemented), so scan for a duplicate value.
            if (engine.enforceUniqueKey) {
                final List<TableColumn> cols = metadata.getColumns();
                for (int i = 0; i < cols.size(); i++) {
                    if (!cols.get(i).isUnique() || cols.get(i).isPrimaryKey() || i >= row.getValues().size()) {
                        continue;
                    }
                    final Object value = row.getValue(i);
                    if (value == null) {
                        continue;
                    }
                    for (final Row existing : rows) {
                        if (i < existing.getValues().size() && value.equals(existing.getValue(i))) {
                            throw new RuntimeException(
                                "Duplicate unique key on column '" + cols.get(i).getName() + "': " + value);
                        }
                    }
                }
            }

            rows.add(row);
            rowIds.add(nextRowId++);
            engine.markDirty(this);
        }

        public void update(final int rowIndex, final Row newRow) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                throw new RuntimeException("Invalid row index: " + rowIndex);
            }

            // Update primary key index if needed
            if (!metadata.getPrimaryKeys().isEmpty()) {
                Row oldRow = rows.get(rowIndex);
                Object oldPk = extractPrimaryKey(oldRow);
                Object newPk = extractPrimaryKey(newRow);

                boolean pkChanged = oldPk == null ? newPk != null : !oldPk.equals(newPk);
                if (pkChanged) {
                    if (oldPk != null) primaryKeyIndex.remove(oldPk);
                    if (newPk != null) {
                        if (engine.enforcePrimaryKey && primaryKeyIndex.containsKey(newPk)) {
                            throw new RuntimeException("Duplicate primary key: " + newPk);
                        }
                        primaryKeyIndex.put(newPk, rowIndex);
                    }
                }
            }

            rows.set(rowIndex, newRow);
            engine.markDirty(this);
        }

        public void delete(final int rowIndex) {
            if (rowIndex < 0 || rowIndex >= rows.size()) {
                return; // Silently ignore out of bounds (may have been deleted already)
            }

            if (!metadata.getPrimaryKeys().isEmpty()) {
                Row row = rows.get(rowIndex);
                Object pk = extractPrimaryKey(row);
                if (pk != null) primaryKeyIndex.remove(pk);
            }

            rows.remove(rowIndex);
            rowIds.remove(rowIndex);

            // Rebuild primary key index after deletion
            if (!metadata.getPrimaryKeys().isEmpty()) {
                rebuildPrimaryKeyIndex();
            }
            engine.markDirty(this);
        }

        public List<Row> scan() {
            return new ArrayList<>(rows);
        }

        /** The live row at {@code index} (used at deferred-apply time to capture pre-change values). */
        public Row getRow(final int index) {
            return rows.get(index);
        }

        public Row getRowByPrimaryKey(final Object key) {
            Integer index = primaryKeyIndex.get(key);
            return index != null ? rows.get(index) : null;
        }

        /** The primary-key value (single value, or list for a composite key) of {@code row}; null if no PK. */
        public Object primaryKeyOf(final Row row) {
            return metadata.getPrimaryKeys().isEmpty() ? null : extractPrimaryKey(row);
        }

        public int getRowCount() {
            return rows.size();
        }

        /** Stable identity of the row currently at {@code index} (index-aligned with {@link #scan()}). */
        public long getRowId(final int index) {
            return rowIds.get(index);
        }

        /** Snapshot of the stable row ids, index-aligned with {@link #scan()}. */
        public List<Long> getRowIds() {
            return new ArrayList<>(rowIds);
        }

        public void clear() {
            rows.clear();
            rowIds.clear();
            primaryKeyIndex.clear();

            // Reset to identity start value
            long startValue = 1;
            for (final TableColumn col : metadata.getColumns()) {
                if (col.isAutoIncrement()) {
                    startValue = col.getIdentityStart();
                    break;
                }
            }
            nextAutoIncrementValue = startValue;
        }

        public long getNextAutoIncrementValue() {
            long current = nextAutoIncrementValue;
            nextAutoIncrementValue += identityIncrement;
            return current;
        }

        private Object extractPrimaryKey(final Row row) {
            if (metadata.getPrimaryKeys().size() == 1) {
                String pkColumn = metadata.getPrimaryKeys().get(0);
                int index = metadata.getColumnIndex(pkColumn);
                return row.getValues().get(index);
            } else {
                // Composite key
                List<Object> keyValues = new ArrayList<>();
                for (final String pkColumn : metadata.getPrimaryKeys()) {
                    int index = metadata.getColumnIndex(pkColumn);
                    keyValues.add(row.getValues().get(index));
                }
                return keyValues;
            }
        }

        private void rebuildPrimaryKeyIndex() {
            primaryKeyIndex.clear();
            for (int i = 0; i < rows.size(); i++) {
                Object pk = extractPrimaryKey(rows.get(i));
                primaryKeyIndex.put(pk, i);
            }
        }
    }
}
