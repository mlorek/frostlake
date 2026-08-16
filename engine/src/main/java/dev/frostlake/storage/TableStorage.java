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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TableStorage {
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

    // When this storage came into being — the lower bound of its time-travel history. A real
    // account refuses AT/BEFORE points before the object's creation; snapshots are in-memory
    // only, so a restored engine's history starts at restore, exactly like its snapshots.
    private final long createdMillis = System.currentTimeMillis();

/** Rolling window of snapshots, retention-bounded (default 90 s for testing). */
    private final CopyOnWriteArrayList<TableSnapshot> snapshots =
        new CopyOnWriteArrayList<>();
    private static final int MAX_SNAPSHOTS = 200;

    /** Force a snapshot now (called after each DML operation). */
    public void takeSnapshot() {
        final long now = System.currentTimeMillis();
        // A POINTER copy suffices: no write path mutates a stored row in place — UPDATE installs
        // replacement rows (replaceRow), INSERT/DELETE add/remove whole rows — so a snapshot's
        // rows are frozen by construction.
        snapshots.add(new TableSnapshot(now, new ArrayList<>(rows)));
        if (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    /** Return the snapshot whose timestamp is closest to (but not after) targetMillis. */
    public TableSnapshot snapshotAt(final long targetMillis) {
        TableSnapshot best = null;
        for (final TableSnapshot s : snapshots) {
            if (s.epochMillis <= targetMillis) {
                best = s;
            } else {
                break;
            }
        }
        return best;
    }

    /** Return the snapshot just BEFORE targetMillis (exclusive). */
    public TableSnapshot snapshotBefore(final long targetMillis) {
        TableSnapshot best = null;
        for (final TableSnapshot s : snapshots) {
            if (s.epochMillis < targetMillis) {
                best = s;
            } else {
                break;
            }
        }
        return best;
    }

    /** Return offset seconds ago from now. */
    public TableSnapshot snapshotAtOffset(final long offsetSeconds) {
        return snapshotAt(System.currentTimeMillis() - offsetSeconds * 1000L);
    }

    public List<TableSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    /** Epoch millis of this storage's creation — the earliest reachable time-travel point. */
    public long getCreatedMillis() {
        return createdMillis;
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
        // Validate row (columnCount avoids the defensive column-list copy getColumns() makes)
        if (row.getValues().size() != metadata.columnCount()) {
            throw new RuntimeException("Row column count mismatch");
        }

        // Handle primary key index (always maintain index; enforce constraint only when enabled)
        if (metadata.hasPrimaryKeyColumns()) {
            final Object pk = extractPrimaryKey(row);
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
            final Row oldRow = rows.get(rowIndex);
            final Object oldPk = extractPrimaryKey(oldRow);
            final Object newPk = extractPrimaryKey(newRow);

            final boolean pkChanged = oldPk == null ? newPk != null : !oldPk.equals(newPk);
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
            final Row row = rows.get(rowIndex);
            final Object pk = extractPrimaryKey(row);
            if (pk != null) primaryKeyIndex.remove(pk);
        }

        rows.remove(rowIndex);
        rowIds.remove(rowIndex);

        // Rebuild primary key index after deletion
        if (metadata.hasPrimaryKeyColumns()) {
            rebuildPrimaryKeyIndex();
        }
        engine.markDirty(this);
    }

    /**
     * Delete every row at {@code indices} in ONE compaction pass over rows/rowIds with a
     * single primary-key index rebuild at the end — the per-row {@link #delete(int)} path pays
     * an O(n) shift per removal plus a full index rebuild each time. Out-of-range indices are
     * ignored, matching {@link #delete(int)}; duplicates are harmless.
     */
    public void deleteAll(final List<Integer> indices) {
        if (indices.isEmpty()) {
            return;
        }
        final Set<Integer> toDelete = new HashSet<>(indices);
        final List<Row> keptRows = new ArrayList<>(rows.size());
        final List<Long> keptIds = new ArrayList<>(rowIds.size());
        for (int i = 0; i < rows.size(); i++) {
            if (!toDelete.contains(Integer.valueOf(i))) {
                keptRows.add(rows.get(i));
                keptIds.add(rowIds.get(i));
            }
        }
        rows.clear();
        rows.addAll(keptRows);
        rowIds.clear();
        rowIds.addAll(keptIds);
        if (metadata.hasPrimaryKeyColumns()) {
            rebuildPrimaryKeyIndex();
        }
        engine.markDirty(this);
    }

    public List<Row> scan() {
        return new ArrayList<>(rows);
    }

    /** Install a REPLACEMENT row at {@code index} — UPDATE never mutates a stored row in place,
     *  which is what lets {@link #takeSnapshot()} keep pointer copies instead of deep copies. */
    public void replaceRow(final int index, final Row newRow) {
        rows.set(index, newRow);
        engine.markDirty(this);
    }

    /** The live row at {@code index} (used at deferred-apply time to capture pre-change values). */
    public Row getRow(final int index) {
        return rows.get(index);
    }

    public Row getRowByPrimaryKey(final Object key) {
        final Integer index = primaryKeyIndex.get(key);
        return index != null ? rows.get(index) : null;
    }

    /** The primary-key value (single value, or list for a composite key) of {@code row}; null if no PK. */
    public Object primaryKeyOf(final Row row) {
        return metadata.hasPrimaryKeyColumns() ? extractPrimaryKey(row) : null;
    }

    public int getRowCount() {
        return rows.size();
    }

    /** Stable identity of the row currently at {@code index} (index-aligned with {@link #scan()}). */
    public long getRowId(final int index) {
        return rowIds.get(index);
    }

    /** TableSnapshot of the stable row ids, index-aligned with {@link #scan()}. */
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
        final long current = nextAutoIncrementValue;
        nextAutoIncrementValue += identityIncrement;
        return current;
    }

    private Object extractPrimaryKey(final Row row) {
        // One snapshot of the PK names per call — getPrimaryKeys() copies per call, and this
        // runs per WRITTEN row.
        final List<String> pkColumns = metadata.getPrimaryKeys();
        if (pkColumns.size() == 1) {
            final int index = metadata.getColumnIndex(pkColumns.get(0));
            return row.getValues().get(index);
        } else {
            // Composite key
            final List<Object> keyValues = new ArrayList<>();
            for (final String pkColumn : pkColumns) {
                final int index = metadata.getColumnIndex(pkColumn);
                keyValues.add(row.getValues().get(index));
            }
            return keyValues;
        }
    }

    private void rebuildPrimaryKeyIndex() {
        primaryKeyIndex.clear();
        for (int i = 0; i < rows.size(); i++) {
            final Object pk = extractPrimaryKey(rows.get(i));
            primaryKeyIndex.put(pk, i);
        }
    }
}
