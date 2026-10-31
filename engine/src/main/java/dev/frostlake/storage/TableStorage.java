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
import dev.frostlake.metastore.HybridKeys;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.TableStructure;
import dev.frostlake.metastore.model.UniqueConstraint;
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
import java.util.concurrent.atomic.AtomicLong;

public class TableStorage {
    private final StorageEngine engine; // reference to parent for config flags
    private final Table metadata;
    private final List<Row> rows;
    private final Map<Object, Integer> primaryKeyIndex;

    // Stable, monotonic per-row identity, index-aligned with `rows`, drawn from the engine's one sequence
    // (StorageEngine#nextRowId). Lets a transaction reference a specific row across other mutations WITHOUT
    // positional indices — the foundation for the write-set / deferred-apply transaction model (see
    // transaction.TransactionWriteSet) — and is what a stream and a CHANGES clause spell as METADATA$ROW_ID
    // (see StreamRowIds). Reassigned on reload, the restored snapshots' along with the rows'.
    private final List<Long> rowIds = Collections.synchronizedList(new ArrayList<>());

    // When this storage came into being — the lower bound of its time-travel history. A real
    // account refuses AT/BEFORE points before the object's creation. It is the creating statement's
    // instant, as the snapshots' stamps are: a replayed statement that reads the table at its recorded
    // CURRENT_TIMESTAMP() then finds it there, as the original statement did. A restored engine puts back
    // the one its snapshot saved, with the snapshots (restoreHistory).
    private volatile long createdMillis = StatementClock.instant().toEpochMilli();
    // Where this storage's creation falls in the order snapshots are taken in: a point with a lower number
    // came before the table.
    private volatile long createdSequence = SnapshotSequence.next();

    /**
     * The rolling window of time-travel snapshots, at most {@link #MAX_SNAPSHOTS} of them. The retention period is not
     * applied here but where a point is read (QueryExecutor), which refuses one older than the table's
     * DATA_RETENTION_TIME_IN_DAYS: a stream's offset still reads the snapshot it stands on past that, as an account
     * extends retention for a stream not yet consumed.
     */
    private final CopyOnWriteArrayList<TableSnapshot> snapshots =
        new CopyOnWriteArrayList<>();
    // How many writes the rows have taken, and the mark drawn when they had taken markedWrites (see writeMark).
    private final AtomicLong writes = new AtomicLong();
    private long markedWrites = -1;
    private long writeMark;
    private static final int MAX_SNAPSHOTS = 200;
    /** Set once the window dropped a snapshot: the history before the oldest one kept is gone. */
    private volatile boolean historyEvicted;

    /** The snapshots of the table's time-travel window, oldest first: what a snapshot of the engine's state keeps. */
    public List<TableSnapshot> historySnapshots() {
        return new ArrayList<>(snapshots);
    }

    /** When the table's history starts: no point before it reads the table. */
    public long historyStartMillis() {
        return createdMillis;
    }

    /** Where the table's history starts in the order snapshots are taken in. */
    public long historyStartSequence() {
        return createdSequence;
    }

    /** Whether the window dropped snapshots, so the history before the oldest one kept is gone. */
    public boolean isHistoryEvicted() {
        return historyEvicted;
    }

    /** A row identity no row of the engine has had, for a row only the restored history holds. */
    public long freshRowId() {
        return engine.nextRowId();
    }

    /**
     * Puts back the time-travel history a snapshot of an engine's state carried, on storage just filled with the
     * table's rows: a clone, a checkpoint's restore and a write-ahead log's recovery read the table at a point
     * before the copy as the engine they copy did. Numbers drawn after the restore follow the history's own.
     *
     * @param startMillis   when the table's history starts
     * @param startSequence where it starts in the order snapshots are taken in
     * @param evicted       whether the window had dropped snapshots
     * @param restored      the snapshots, oldest first, their row identities those of the rows stored now
     */
    public void restoreHistory(final long startMillis, final long startSequence, final boolean evicted,
                               final List<TableSnapshot> restored) {
        synchronized (snapshots) {
            createdMillis = startMillis;
            createdSequence = startSequence;
            historyEvicted = evicted;
            snapshots.clear();
            snapshots.addAll(restored);
        }
        SnapshotSequence.advancePast(startSequence);
        for (final TableSnapshot snapshot : restored) {
            SnapshotSequence.advancePast(snapshot.sequence);
        }
    }

    /**
     * Force a snapshot now (called after each DML operation).
     *
     * <p>Stamped with the committing statement's instant rather than the wall clock, so a replay stamps the
     * snapshots the original run stamped and a point read from CURRENT_TIMESTAMP() resolves the same in both. A
     * stamp is never earlier than the table's creation or than the snapshot before it: concurrent statements
     * commit in an order their instants need not follow, and the snapshots must stay in order.
     */
    public void takeSnapshot() {
        // A POINTER copy suffices: no write path mutates a stored row in place — UPDATE installs
        // replacement rows (replaceRow), INSERT/DELETE add/remove whole rows — so a snapshot's
        // rows are frozen by construction.
        final List<Row> taken;
        final List<Long> takenIds;
        synchronized (rows) {
            taken = new ArrayList<>(rows);
            takenIds = new ArrayList<>(rowIds);
        }
        // The shape the rows are in, recorded first if a change to it went unrecorded; shared, never copied per write.
        final TableStructure layout = metadata.currentStructure();
        synchronized (snapshots) {
            long now = Math.max(StatementClock.commitInstant().toEpochMilli(), createdMillis);
            if (!snapshots.isEmpty()) {
                now = Math.max(now, snapshots.get(snapshots.size() - 1).epochMillis);
            }
            snapshots.add(new TableSnapshot(now, SnapshotSequence.next(), taken, takenIds, layout, null));
            StatementClock.committed(now);
            if (snapshots.size() > MAX_SNAPSHOTS) {
                snapshots.remove(0);
                historyEvicted = true;
                // No point reaches back past the oldest snapshot kept, so neither do the shapes before it.
                metadata.forgetStructureBefore(snapshots.get(0).sequence);
            }
        }
    }

    /**
     * Whether the table's history at a point is gone: the window has dropped snapshots, and the point falls
     * before the oldest snapshot it kept. A read there would find nothing and answer an empty table that never
     * was, so it is refused like a point outside the retention period.
     *
     * @param targetMillis the point
     * @param isBefore     whether the read is BEFORE the point, which also needs the snapshot before it
     * @return whether the history at the point is gone
     */
    public boolean historyEvictedAt(final long targetMillis, final boolean isBefore) {
        if (!historyEvicted) {
            return false;
        }
        final long oldest = snapshots.get(0).epochMillis;
        return isBefore ? targetMillis <= oldest : targetMillis < oldest;
    }

    /**
     * {@link #historyEvictedAt} for a point given by the order snapshots are taken in.
     *
     * @param sequence a number from {@link SnapshotSequence#current()}
     * @return whether the history at the point is gone
     */
    public boolean historyEvictedUpTo(final long sequence) {
        return historyEvicted && sequence < snapshots.get(0).sequence;
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
        return snapshotAt(StatementClock.instant().toEpochMilli() - offsetSeconds * 1000L);
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
        // The table's history of shapes starts with its rows: a clone as of a point before any DDL takes this one.
        metadata.seedStructure(createdMillis, createdSequence);
    }

    /**
     * A snapshot's rows laid out in a shape of the table's: each column is found in the shape the snapshot was taken
     * in by its position, which a column keeps for life. A column renamed or retyped in between keeps its values, one
     * dropped in between is left out, and one added in between is NULL: a column added while the table holds rows
     * takes a snapshot of its own as the ADD ends, so one added without one was added to a table with no rows.
     *
     * @param snapshot the snapshot
     * @param shape    the shape to lay the rows out in, or null for the table's current columns
     * @return the rows
     */
    public static List<Row> rowsIn(final TableSnapshot snapshot, final TableStructure shape) {
        if (shape == null || snapshot.layout == null) {
            return new ArrayList<>(snapshot.rows);
        }
        if (shape == snapshot.layout) {
            return new ArrayList<>(snapshot.asTakenRows);
        }
        final List<TableColumn> from = snapshot.layout.getColumns();
        final List<TableColumn> to = shape.getColumns();
        final int[] slots = new int[to.size()];
        for (int i = 0; i < to.size(); i++) {
            slots[i] = -1;
            for (int j = 0; j < from.size(); j++) {
                if (from.get(j).getOrdinalPosition() == to.get(i).getOrdinalPosition()) {
                    slots[i] = j;
                    break;
                }
            }
        }
        final List<Row> laidOut = new ArrayList<>(snapshot.asTakenRows.size());
        for (final Row row : snapshot.asTakenRows) {
            final List<Object> values = new ArrayList<>(slots.length);
            for (final int slot : slots) {
                values.add(slot >= 0 && slot < row.size() ? row.getValue(slot) : null);
            }
            laidOut.add(Row.of(values));
        }
        return laidOut;
    }

    public void insert(final Row written) {
        insertAs(written, -1);
    }

    /**
     * Stores a restored row under the identity it had: a row's METADATA$ROW_ID is the same after a restart from a
     * snapshot as before it, as an immutable row id is on the account, and a stream's pending change still names it.
     *
     * @param written the row
     * @param id      its identity
     */
    public void insertRestored(final Row written, final long id) {
        engine.advanceRowIdsPast(id);
        insertAs(written, id);
    }

    /**
     * A restored history row's identity, kept as it was saved.
     *
     * @param id the identity it had
     * @return the same identity, which no row stored later takes
     */
    public long keptRowId(final long id) {
        engine.advanceRowIdsPast(id);
        return id;
    }

    private void insertAs(final Row written, final long id) {
        // Validate row (columnCount avoids the defensive column-list copy getColumns() makes)
        if (written.getValues().size() != metadata.columnCount()) {
            throw new RuntimeException("Row column count mismatch");
        }
        final Row row = inDeclaredCarriers(written);

        // Handle primary key index (always maintain index; enforce constraint only when enabled)
        if (metadata.hasPrimaryKeyColumns()) {
            final Object pk = extractPrimaryKey(row);
            if (pk != null) {
                if ((engine.enforcePrimaryKey || metadata.isHybrid()) && primaryKeyIndex.containsKey(pk)) {
                    throw new RuntimeException(metadata.isHybrid() ? HybridKeys.primaryKeyExists()
                        : "Duplicate primary key: " + pk);
                }
                primaryKeyIndex.put(pk, rows.size());
            }
        }

        // Enforce UNIQUE columns when opted in (constraints.enforce.uniqueKey). No unique index is
        // maintained (indexes are intentionally not implemented), so scan for a duplicate value. A hybrid table
        // holds each UNIQUE key whole, one over several columns as one (documented).
        if (metadata.isHybrid()) {
            for (final UniqueConstraint key : HybridKeys.uniqueKeys(metadata)) {
                final List<Object> value = HybridKeys.keyOf(metadata, key.getColumnNames(), row);
                if (value == null) {
                    continue;
                }
                for (final Row existing : rows) {
                    if (value.equals(HybridKeys.keyOf(metadata, key.getColumnNames(), existing))) {
                        throw new RuntimeException(HybridKeys.uniqueViolated(metadata, key));
                    }
                }
            }
        } else if (engine.enforceUniqueKey) {
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
        rowIds.add(id >= 0 ? id : engine.nextRowId());
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

    /**
     * Where every row write ends: the table's last data change is stamped with the writing statement's instant, which
     * an autocommit still holds once its pin is gone, and its snapshot is scheduled.
     */
    private void written() {
        metadata.markDataChanged(StatementClock.commitInstant());
        engine.markDirty(this);
        writes.incrementAndGet();
    }

    /**
     * A number that stays while the table's rows do and moves with every write to them: a dynamic table that reads
     * the table keeps it to tell whether the rows changed since its last refresh, as the account tells by the
     * table's micro-partitions. It is drawn the first time it is asked for after a write, so a table written many
     * times between two asks moves once.
     *
     * @return the mark
     */
    public synchronized long writeMark() {
        final long now = writes.get();
        if (now != markedWrites) {
            writeMark = SnapshotSequence.mark();
            markedWrites = now;
        }
        return writeMark;
    }

    /**
     * Puts back the mark a snapshot of the engine's state saved with the rows just restored (see {@link #writeMark}):
     * a dynamic table that read them before the restart finds them unchanged, until the next write moves it.
     *
     * @param mark the saved mark, or 0 where the snapshot saved none
     */
    public synchronized void restoreWriteMark(final long mark) {
        if (mark > 0) {
            SnapshotSequence.advancePast(mark);
            writeMark = mark;
            markedWrites = writes.get();
        }
    }

    public List<Row> scan() {
        return new ArrayList<>(rows);
    }

    /**
     * The rows stored now and, index-aligned, their identities, taken together: what a snapshot of the engine's
     * state saves.
     *
     * @param ids receives each row's identity
     * @return the rows
     */
    public List<Row> scanWithIds(final List<Long> ids) {
        synchronized (rows) {
            ids.addAll(rowIds);
            return new ArrayList<>(rows);
        }
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
     * identities, the order and every other value stay exactly as they were. Each snapshot keeps the
     * rows as it took them besides ({@link TableSnapshot#asTakenRows}), which a CLONE as of a point
     * before the drop reads, dropped column and all.
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
            snapshots.set(s, new TableSnapshot(snapshot.epochMillis, snapshot.sequence, kept, snapshot.rowIds,
                snapshot.layout, snapshot.asTakenRows));
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
     * added the column, as its last one. Each snapshot keeps the rows as it took them besides, without the
     * column, which a CLONE as of a point before the add reads.
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
            snapshots.set(s, new TableSnapshot(snapshot.epochMillis, snapshot.sequence, kept, snapshot.rowIds,
                snapshot.layout, snapshot.asTakenRows));
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
        metadata.markDataChanged(StatementClock.commitInstant());
        writes.incrementAndGet();

        // Reset to identity start value
        metadata.setNextIdentityValue(null);
    }

    /** The next IDENTITY/AUTOINCREMENT value. The sequence is the table's own; see {@link Table#drawIdentityValue}. */
    public long getNextAutoIncrementValue() {
        return metadata.drawIdentityValue();
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
