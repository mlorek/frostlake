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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ExactValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
    // Where this storage's creation falls in the order snapshots are taken in: a point with a lower number
    // came before the table.
    private final long createdSequence = SnapshotSequence.next();

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
        final List<Row> taken;
        final List<Long> takenIds;
        synchronized (rows) {
            taken = new ArrayList<>(rows);
            takenIds = new ArrayList<>(rowIds);
        }
        snapshots.add(new TableSnapshot(now, SnapshotSequence.next(), taken, takenIds));
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

    /**
     * The latest snapshot at or below a sequence number — the table as it stood once the snapshot that drew the
     * number was taken, whatever the clock read.
     *
     * @param sequence a number from {@link SnapshotSequence#current()}
     * @return the snapshot, or null when none is that old
     */
    public TableSnapshot snapshotUpTo(final long sequence) {
        TableSnapshot best = null;
        for (final TableSnapshot s : snapshots) {
            if (s.sequence <= sequence) {
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

    /** Where this storage's creation falls in the order snapshots are taken in; see {@link SnapshotSequence}. */
    public long getCreatedSequence() {
        return createdSequence;
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

    public void insert(final Row written) {
        // Validate row (columnCount avoids the defensive column-list copy getColumns() makes)
        if (written.getValues().size() != metadata.columnCount()) {
            throw new RuntimeException("Row column count mismatch");
        }
        final Row row = inDeclaredCarriers(written);

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
        written();
    }

    public void update(final int rowIndex, final Row written) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            throw new RuntimeException("Invalid row index: " + rowIndex);
        }
        final Row newRow = inDeclaredCarriers(written);

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
        written();
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
        written();
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
        written();
    }

    /** Where every row write ends: the table's last data change is stamped and its snapshot scheduled. */
    private void written() {
        metadata.markDataChanged(StatementClock.instant());
        engine.markDirty(this);
    }

    public List<Row> scan() {
        return new ArrayList<>(rows);
    }

    /** Install a REPLACEMENT row at {@code index} — UPDATE never mutates a stored row in place,
     *  which is what lets {@link #takeSnapshot()} keep pointer copies instead of deep copies. */
    public void replaceRow(final int index, final Row newRow) {
        rows.set(index, inDeclaredCarriers(newRow));
        written();
    }

    /**
     * The row with every exact-numeric cell in the ONE carrier its column holds — a scale-0 column's
     * Long, a scaled column's BigDecimal at that scale (see {@link ExactValues}). Every write lands here
     * (INSERT, UPDATE, MERGE, CTAS, COPY, a clone), so a value written by any of them is the same class
     * as one written by any other. The row is copied before a cell is replaced: a caller's row — a
     * result set's, a snapshot's — is never rewritten under it.
     */
    private Row inDeclaredCarriers(final Row row) {
        Row stored = row;
        final int count = Math.min(row.size(), metadata.columnCount());
        for (int i = 0; i < count; i++) {
            final Object value = row.getValue(i);
            if (!(value instanceof Number)) {
                continue;
            }
            final DataType type = metadata.columnAt(i).getDataType();
            if (!(type instanceof NumericType) || ExactValues.isCarrier(value, (NumericType) type)
                    || NumericType.isApproximate(type)) {
                continue;
            }
            final Object carrier = ExactValues.written(value, (NumericType) type);
            if (carrier != value) {
                if (stored == row) {
                    stored = row.copy();
                }
                stored.setValue(i, carrier);
            }
        }
        return stored;
    }

    /**
     * Removes a dropped column's slot from every stored row and from every retained time-travel
     * snapshot. Rows are positional, so a value left behind would surface under whichever column
     * later occupies its slot. History loses the slot too: a time-travel read answers through the
     * table's CURRENT columns (live-verified — {@code AT(STATEMENT => …)} after a DROP COLUMN answers
     * without the dropped column). Called once the catalog has dropped the column, so the row
     * identities, the order and every other value stay exactly as they were.
     *
     * @param index the dropped column's position before the drop
     */
    public void dropColumnSlot(final int index) {
        // Snapshots share row objects with the live list and with one another, so each distinct row is
        // narrowed ONCE and every holder is handed the same replacement.
        final Map<Row, Row> narrowed = new IdentityHashMap<>();
        synchronized (rows) {
            for (int i = 0; i < rows.size(); i++) {
                rows.set(i, withoutSlot(rows.get(i), index, narrowed));
            }
        }
        for (int s = 0; s < snapshots.size(); s++) {
            final TableSnapshot snapshot = snapshots.get(s);
            final List<Row> kept = new ArrayList<>(snapshot.rows.size());
            for (final Row row : snapshot.rows) {
                kept.add(withoutSlot(row, index, narrowed));
            }
            snapshots.set(s, new TableSnapshot(snapshot.epochMillis, snapshot.sequence, kept, snapshot.rowIds));
        }
        primaryKeyIndex.clear();
        if (metadata.hasPrimaryKeyColumns()) {
            rebuildPrimaryKeyIndex();
        }
    }

    /**
     * Appends a newly added column's slot to every stored row and to every retained time-travel snapshot,
     * holding the value the column takes where a row has none of its own — its default, or NULL. History
     * gains the slot too: a time-travel read answers through the table's CURRENT columns, and a row read
     * from before the ADD shows the new column's default (live-verified). Called once the catalog has
     * added the column, as its last one.
     *
     * @param value the value the new column takes in the rows that already exist
     * @return the value as the column stores it, in its declared carrier
     */
    public Object appendColumnSlot(final Object value) {
        final Object stored = carrierFor(value, metadata.columnCount() - 1);
        final Map<Row, Row> widened = new IdentityHashMap<>();
        final boolean hadRows;
        synchronized (rows) {
            hadRows = !rows.isEmpty();
            for (int i = 0; i < rows.size(); i++) {
                rows.set(i, withSlotAppended(rows.get(i), stored, widened));
            }
        }
        for (int s = 0; s < snapshots.size(); s++) {
            final TableSnapshot snapshot = snapshots.get(s);
            final List<Row> kept = new ArrayList<>(snapshot.rows.size());
            for (final Row row : snapshot.rows) {
                kept.add(withSlotAppended(row, stored, widened));
            }
            snapshots.set(s, new TableSnapshot(snapshot.epochMillis, snapshot.sequence, kept, snapshot.rowIds));
        }
        if (hadRows) {
            written();
        }
        return stored;
    }

    /** One value in the carrier the column at {@code index} holds, as every write path stores it. */
    private Object carrierFor(final Object value, final int index) {
        if (!(value instanceof Number) || index < 0 || index >= metadata.columnCount()) {
            return value;
        }
        final DataType type = metadata.columnAt(index).getDataType();
        if (!(type instanceof NumericType) || ExactValues.isCarrier(value, (NumericType) type)
                || NumericType.isApproximate(type)) {
            return value;
        }
        return ExactValues.written(value, (NumericType) type);
    }

    /** The row with {@code value} appended; only a row exactly one slot short of the table takes it. */
    private Row withSlotAppended(final Row row, final Object value, final Map<Row, Row> widened) {
        if (row.size() != metadata.columnCount() - 1) {
            return row;
        }
        final Row done = widened.get(row);
        if (done != null) {
            return done;
        }
        final List<Object> values = new ArrayList<>(row.getValues());
        values.add(value);
        final Row replacement = Row.of(values);
        widened.put(row, replacement);
        return replacement;
    }

    /** The row without the value at {@code index}; a row too short to hold that slot is kept as it is. */
    private static Row withoutSlot(final Row row, final int index, final Map<Row, Row> narrowed) {
        if (index >= row.size()) {
            return row;
        }
        final Row done = narrowed.get(row);
        if (done != null) {
            return done;
        }
        final List<Object> values = new ArrayList<>(row.getValues());
        values.remove(index);
        final Row replacement = Row.of(values);
        narrowed.put(row, replacement);
        return replacement;
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
        metadata.markDataChanged(StatementClock.instant());

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
