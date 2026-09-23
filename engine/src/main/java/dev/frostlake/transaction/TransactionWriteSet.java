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

package dev.frostlake.transaction;

import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.stream.StreamManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-transaction buffer of pending changes — the foundation of the deferred-apply / READ COMMITTED model
 * (see {@code docs/acid-snowflake-plan.md}). Instead of mutating live storage, DML records changes here:
 *
 * <ul>
 *   <li>{@link #overlayRows} gives the transaction its OWN view — committed base rows with this write
 *       set's updates applied, deletes removed, and inserts appended. So own writes are visible to self,
 *       while other transactions' uncommitted writes are not (they live in their own write set, never in
 *       the shared base store until commit) — exactly READ COMMITTED.</li>
 *   <li>{@link #applyTo} flushes the buffer to the base store on COMMIT; simply discarding the write set
 *       (never calling {@code applyTo}) IS the ROLLBACK.</li>
 * </ul>
 *
 * Existing base rows are referenced by their STABLE id ({@link TableStorage#getRowId}), never by position,
 * so an interleaving mutation can't make the buffer target the wrong row.
 *
 * <p>Not yet wired into the executor — this is the tested foundation the Phase-1 DML wiring will build on.
 * Known follow-up: modifying a row this same transaction just inserted (its id is not a base id) is handled
 * by the executor mutating the pending insert directly, not here.
 */
public class TransactionWriteSet {

    /** table → rows inserted by this transaction (not yet in the base store). */
    private final Map<String, List<Row>> inserts = new LinkedHashMap<>();
    /** table → (stable base-row id → new value) for base rows updated by this transaction. */
    private final Map<String, Map<Long, Row>> updates = new LinkedHashMap<>();
    /** table → stable ids of base rows deleted by this transaction. */
    private final Map<String, Set<Long>> deletes = new LinkedHashMap<>();
    /** table → the PARTITIONS lock the first touching statement took (SHOW LOCKS' rows). */
    private final Map<String, TableLock> tableLocks = new LinkedHashMap<>();
    /** Raw (as-passed) table spellings whose lock entry is known to exist — see {@link #recordTableTouch}. */
    private final Set<String> lockNotedRaw = new HashSet<>();

    // Copy-on-first-mutation statement savepoint: while one is active, each mutator snapshots a
    // table's three entries the FIRST time the statement touches that table — the former full
    // copy() paid O(all pending rows) per statement however few tables the statement touched.
    private WriteSetSavepoint activeSavepoint;

    /** Begin a statement savepoint (deferred-apply explicit transactions). */
    public WriteSetSavepoint beginStatementSavepoint() {
        activeSavepoint = new WriteSetSavepoint(new LinkedHashMap<>(tableLocks));
        return activeSavepoint;
    }

    /** End the statement savepoint without restoring (the statement succeeded). */
    public void endStatementSavepoint() {
        activeSavepoint = null;
    }

    /** Restore exactly the entries the failed statement touched, and the lock map. */
    public void rollbackToSavepoint(final WriteSetSavepoint savepoint) {
        for (final String table : savepoint.touchedTables()) {
            final List<Row> insertsPre = savepoint.insertsBefore(table);
            if (insertsPre == null) {
                inserts.remove(table);
            } else {
                inserts.put(table, insertsPre);
            }
            final Map<Long, Row> updatesPre = savepoint.updatesBefore(table);
            if (updatesPre == null) {
                updates.remove(table);
            } else {
                updates.put(table, updatesPre);
            }
            final Set<Long> deletesPre = savepoint.deletesBefore(table);
            if (deletesPre == null) {
                deletes.remove(table);
            } else {
                deletes.put(table, deletesPre);
            }
        }
        tableLocks.clear();
        tableLocks.putAll(savepoint.locksBefore());
        lockNotedRaw.clear();
        activeSavepoint = null;
    }

    private void snapshotForSavepoint(final String table) {
        if (activeSavepoint == null || activeSavepoint.isTouched(table)) {
            return;
        }
        final List<Row> ins = inserts.get(table);
        final Map<Long, Row> upd = updates.get(table);
        final Set<Long> del = deletes.get(table);
        activeSavepoint.rememberTable(table,
            ins == null ? null : new ArrayList<>(ins),
            upd == null ? null : new LinkedHashMap<>(upd),
            del == null ? null : new HashSet<>(del));
    }

    public void recordInsert(final String table, final Row row) {
        snapshotForSavepoint(table);
        List<Row> list = inserts.get(table);
        if (list == null) {
            list = new ArrayList<>();
            inserts.put(table, list);
        }
        list.add(row);
        // No lock registration: an append-only INSERT holds no PARTITIONS lock (live-verified) —
        // only partition-rewriting DML (UPDATE / DELETE / MERGE / TRUNCATE) surfaces in SHOW LOCKS.
    }

    /** Record a new value for an existing base row (by stable id). No-op if this txn already deleted it. */
    public void recordUpdate(final String table, final long rowId, final Row newValue) {
        snapshotForSavepoint(table);
        final Set<Long> del = deletes.get(table);
        if (del != null && del.contains(rowId)) {
            return;
        }
        Map<Long, Row> map = updates.get(table);
        if (map == null) {
            map = new LinkedHashMap<>();
            updates.put(table, map);
        }
        map.put(rowId, newValue);
        recordTableTouch(table);
    }

    /** Record deletion of an existing base row (by stable id); supersedes any pending update of that row. */
    public void recordDelete(final String table, final long rowId) {
        snapshotForSavepoint(table);
        final Map<Long, Row> upd = updates.get(table);
        if (upd != null) {
            upd.remove(rowId);
        }
        Set<Long> set = deletes.get(table);
        if (set == null) {
            set = new HashSet<>();
            deletes.put(table, set);
        }
        set.add(rowId);
        recordTableTouch(table);
    }

    /**
     * Register the PARTITIONS lock a DML touch takes on a table; the first touch wins the cell. The
     * raw-spelling memo skips the per-row upper-casing after a spelling's first touch; it is cleared
     * wherever lock entries can be removed (statement rollback, {@link #clear}), since a memo entry
     * must never outlive its lock.
     */
    private void recordTableTouch(final String table) {
        if (!lockNotedRaw.add(table)) {
            return;
        }
        final String key = table.toUpperCase();
        if (!tableLocks.containsKey(key)) {
            tableLocks.put(key, new TableLock(System.currentTimeMillis(), UUID.randomUUID().toString()));
        }
    }

    /** The tables this write set touched, each with the lock its first touch acquired. */
    public Map<String, TableLock> getTableLocks() {
        return tableLocks;
    }

    /**
     * Record a TRUNCATE of {@code table} within this transaction: every committed base row (given by
     * {@code committedRowIds}) becomes a pending delete and any not-yet-committed inserts/updates for the
     * table are dropped. The transaction then sees the table as empty, while a rollback still restores the
     * committed rows — the base store is cleared only when these deletes are applied at commit. Clearing
     * the base store directly instead would let this transaction's buffered inserts reappear on next read.
     */
    public void recordTruncate(final String table, final List<Long> committedRowIds) {
        snapshotForSavepoint(table);
        inserts.remove(table);
        updates.remove(table);
        deletes.put(table, new HashSet<>(committedRowIds));
        recordTableTouch(table);
    }

    /**
     * Discard every pending change (inserts, updates, deletes) for {@code table} — used when the table is
     * dropped or replaced within this transaction. The table (and its rows) will not exist after commit, so
     * flushing these buffered writes would fail against missing storage; dropping them here also stops a
     * drop-then-recreate of the same name in one transaction from resurrecting the pre-drop rows.
     */
    public void forgetTable(final String table) {
        inserts.remove(table);
        updates.remove(table);
        deletes.remove(table);
    }

    /**
     * Removes a dropped column's slot from the rows this transaction buffered for {@code table}, so a
     * later COMMIT writes rows as wide as the table has become. The table is matched in any letter case,
     * as its lock entry is.
     *
     * @param table the table whose column was dropped, fully qualified
     * @param index the dropped column's position before the drop
     */
    public void dropColumnSlot(final String table, final int index) {
        for (final Map.Entry<String, List<Row>> pending : inserts.entrySet()) {
            if (!pending.getKey().equalsIgnoreCase(table)) {
                continue;
            }
            final List<Row> rows = pending.getValue();
            for (int i = 0; i < rows.size(); i++) {
                rows.set(i, withoutSlot(rows.get(i), index));
            }
        }
        for (final Map.Entry<String, Map<Long, Row>> pending : updates.entrySet()) {
            if (!pending.getKey().equalsIgnoreCase(table)) {
                continue;
            }
            for (final Map.Entry<Long, Row> update : pending.getValue().entrySet()) {
                update.setValue(withoutSlot(update.getValue(), index));
            }
        }
    }

    /**
     * Appends a newly added column's value to the rows this transaction buffered for {@code table} while
     * they were one slot narrower, so a later COMMIT writes rows as wide as the table has become.
     *
     * @param table the table the column was added to, fully qualified
     * @param width the table's column count after the add
     * @param value the value the new column takes in rows that already exist
     */
    public void appendColumnSlot(final String table, final int width, final Object value) {
        for (final Map.Entry<String, List<Row>> pending : inserts.entrySet()) {
            if (!pending.getKey().equalsIgnoreCase(table)) {
                continue;
            }
            final List<Row> rows = pending.getValue();
            for (int i = 0; i < rows.size(); i++) {
                rows.set(i, withSlotAppended(rows.get(i), width, value));
            }
        }
        for (final Map.Entry<String, Map<Long, Row>> pending : updates.entrySet()) {
            if (!pending.getKey().equalsIgnoreCase(table)) {
                continue;
            }
            for (final Map.Entry<Long, Row> update : pending.getValue().entrySet()) {
                update.setValue(withSlotAppended(update.getValue(), width, value));
            }
        }
    }

    /** The row with {@code value} appended when it is exactly one slot short of {@code width}. */
    private static Row withSlotAppended(final Row row, final int width, final Object value) {
        if (row == null || row.size() != width - 1) {
            return row;
        }
        final List<Object> values = new ArrayList<>(row.getValues());
        values.add(value);
        return Row.of(values);
    }

    /** The row without the value at {@code index}; a row too short to hold that slot is kept as it is. */
    private static Row withoutSlot(final Row row, final int index) {
        if (row == null || index >= row.size()) {
            return row;
        }
        final List<Object> values = new ArrayList<>(row.getValues());
        values.remove(index);
        return Row.of(values);
    }

    /** True if this transaction has deleted the base row with the given stable id. */
    public boolean isDeleted(final String table, final long rowId) {
        final Set<Long> del = deletes.get(table);
        return del != null && del.contains(rowId);
    }

    /** The pending new value for the base row with this stable id, or null if it isn't updated here. */
    public Row pendingUpdate(final String table, final long rowId) {
        final Map<Long, Row> upd = updates.get(table);
        return upd == null ? null : upd.get(rowId);
    }

    /** A snapshot of the rows this transaction has inserted into {@code table} (empty if none); the indices
     *  line up with {@link #setPendingInsert}/{@link #removePendingInsert}. */
    public List<Row> pendingInserts(final String table) {
        final List<Row> ins = inserts.get(table);
        return ins == null ? Collections.<Row>emptyList() : new ArrayList<>(ins);
    }

    /** Replace a not-yet-committed inserted row (in-transaction UPDATE of a row this txn just inserted). */
    public void setPendingInsert(final String table, final int index, final Row newRow) {
        snapshotForSavepoint(table);
        final List<Row> ins = inserts.get(table);
        if (ins != null && index >= 0 && index < ins.size()) {
            ins.set(index, newRow);
        }
    }

    /** Drop a not-yet-committed inserted row (in-transaction DELETE of a row this txn just inserted). */
    public void removePendingInsert(final String table, final int index) {
        snapshotForSavepoint(table);
        final List<Row> ins = inserts.get(table);
        if (ins != null && index >= 0 && index < ins.size()) {
            ins.remove(index);
        }
    }

    /**
     * This transaction's view of {@code table}: committed base rows with this write set's updates applied
     * and deletes removed, then this write set's inserts appended. {@code base} is read once; callers hold
     * the engine lock so {@code scan()} and {@code getRowIds()} stay index-aligned.
     */
    public List<Row> overlayRows(final String table, final TableStorage base) {
        final Set<Long> del = deletes.get(table);
        final Map<Long, Row> upd = updates.get(table);
        final List<Row> ins = inserts.get(table);

        // Fast path: the write set holds NOTHING for this table — the base scan copy IS the view,
        // with no per-row id walk and no rowIds copy at all.
        if ((del == null || del.isEmpty()) && (upd == null || upd.isEmpty())) {
            final List<Row> untouched = base.scan();
            if (ins != null && !ins.isEmpty()) {
                untouched.addAll(ins);
            }
            return untouched;
        }

        // Index iteration under the engine lock — no materialized scan()/getRowIds() copies.
        final int rowCount = base.getRowCount();
        final List<Row> result = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            final long id = base.getRowId(i);
            if (del != null && del.contains(id)) {
                continue;
            }
            final Row updated = upd == null ? null : upd.get(id);
            result.add(updated != null ? updated : base.getRow(i));
        }
        if (ins != null) {
            result.addAll(ins);
        }
        return result;
    }

    /**
     * Synthesize the stream change records for {@code table}'s buffered (not-yet-committed) changes, exactly
     * as {@link #applyTo} would track them at commit — an update as a DELETE(old)+INSERT(new) pair, a delete
     * as a DELETE(old), an insert as an INSERT(new) — but WITHOUT mutating storage or any stream. Old images
     * are read from {@code base} by stable id; every record is tagged with {@code captureName} (the bare
     * source-table name, to match committed records) and carries a negative, per-record id that cannot
     * collide with a real (positive) committed record id. Lets a stream read inside a transaction fold this
     * transaction's own uncommitted DML into its net delta; empty when nothing is buffered for {@code table}.
     */
    public List<StreamRecord> bufferedChangeRecords(final String table, final TableStorage base,
                                                    final String captureName) {
        final List<Row> ins = inserts.get(table);
        final Map<Long, Row> upd = updates.get(table);
        final Set<Long> del = deletes.get(table);
        final boolean hasUpd = upd != null && !upd.isEmpty();
        final boolean hasDel = del != null && !del.isEmpty();
        final boolean hasIns = ins != null && !ins.isEmpty();
        if (!hasUpd && !hasDel && !hasIns) {
            return Collections.emptyList();
        }
        final List<StreamRecord> out = new ArrayList<>();
        long transientId = -1L;   // negative ids never collide with committed (positive) record ids

        Map<Long, Integer> idToIndex = null;
        if (hasUpd || hasDel) {
            final List<Long> ids = base.getRowIds();
            idToIndex = new HashMap<>(ids.size() * 2);
            for (int i = 0; i < ids.size(); i++) {
                idToIndex.put(ids.get(i), i);
            }
        }
        // Match applyTo's order (updates, deletes, inserts) so consolidation with committed records agrees.
        if (hasUpd) {
            for (final Map.Entry<Long, Row> entry : upd.entrySet()) {
                final Integer idx = idToIndex.get(entry.getKey());
                if (idx == null) {
                    continue;
                }
                final long pairId = transientId--;
                out.add(new StreamRecord(base.getRow(idx).getValues(), ChangeType.DELETE, true, pairId, captureName));
                out.add(new StreamRecord(entry.getValue().getValues(), ChangeType.INSERT, true, pairId, captureName));
            }
        }
        if (hasDel) {
            for (final Long id : del) {
                final Integer idx = idToIndex.get(id);
                if (idx != null) {
                    out.add(new StreamRecord(base.getRow(idx).getValues(), ChangeType.DELETE, false, transientId--, captureName));
                }
            }
        }
        if (hasIns) {
            for (final Row row : ins) {
                out.add(new StreamRecord(row.getValues(), ChangeType.INSERT, false, transientId--, captureName));
            }
        }
        return out;
    }

    /** Flush this write set to the base store (the COMMIT step). */
    public void applyTo(final StorageEngine storage) {
        applyTo(storage, null);
    }

    /**
     * Flush this write set to the base store (the COMMIT step): updates, then deletes, then inserts. When
     * {@code streamManager} is non-null, change-tracking (CDC) events are emitted HERE — at apply/commit
     * time, not at DML time — so streams observe only committed changes and a rolled-back write set emits
     * nothing. Pre-change values for updates/deletes are read before the row is mutated.
     */
    public void applyTo(final StorageEngine storage, final StreamManager streamManager) {
        final Set<String> tables = new LinkedHashSet<>();
        tables.addAll(updates.keySet());
        tables.addAll(deletes.keySet());
        tables.addAll(inserts.keySet());

        for (final String table : tables) {
            // A table dropped (or replaced) after these writes were buffered has no storage to flush to —
            // its buffered changes are void. forgetTable() normally purges them at drop time; this is the
            // safety net so a commit can never crash with "Table storage does not exist".
            if (!storage.hasTable(table)) {
                continue;
            }
            final TableStorage base = storage.getTableStorage(table);

            // Resolve stable ids to positions in ONE pass (O(n)) instead of an O(n) scan per changed row,
            // so a bulk UPDATE/DELETE stays O(n) overall, not O(n^2). Built only when there are updates or
            // deletes to apply (inserts just append). Updates set-in-place, so the map stays valid for the
            // delete pass; deletes are collected then applied high-to-low so positions don't shift.
            final Map<Long, Row> upd = updates.get(table);
            final Set<Long> del = deletes.get(table);
            final Map<Long, Integer> idToIndex;
            if ((upd != null && !upd.isEmpty()) || (del != null && !del.isEmpty())) {
                final List<Long> ids = base.getRowIds();
                idToIndex = new HashMap<>(ids.size() * 2);
                for (int i = 0; i < ids.size(); i++) {
                    idToIndex.put(ids.get(i), i);
                }
            } else {
                idToIndex = null;
            }

            // Which streams (if any) capture this table is constant for the whole apply —
            // resolved once, and with NO capturing stream the old-image copies and record
            // construction are skipped entirely.
            final List<Stream> capturing = streamManager != null
                ? streamManager.capturingStreams(table) : Collections.emptyList();
            final boolean captured = !capturing.isEmpty();

            // 1. Updates first.
            if (upd != null) {
                for (final Map.Entry<Long, Row> entry : upd.entrySet()) {
                    final Integer idx = idToIndex.get(entry.getKey());
                    if (idx != null) {
                        final Row oldRow = captured ? base.getRow(idx).copy() : null;
                        base.update(idx, entry.getValue());
                        if (captured) {
                            streamManager.trackUpdate(capturing, table, oldRow, entry.getValue());
                        }
                    }
                }
            }

            // 2. Deletes.
            if (del != null && !del.isEmpty()) {
                final List<Integer> indices = new ArrayList<>(del.size());
                for (final Long id : del) {
                    final Integer idx = idToIndex.get(id);
                    if (idx != null) {
                        indices.add(idx);
                    }
                }
                Collections.sort(indices, Collections.reverseOrder());
                // Capture the old images first, then delete ALL rows in one compaction pass.
                final List<Row> oldRows = new ArrayList<>(indices.size());
                for (final Integer idx : indices) {
                    oldRows.add(captured ? base.getRow(idx).copy() : null);
                }
                base.deleteAll(indices);
                if (captured) {
                    for (final Row oldRow : oldRows) {
                        streamManager.trackDelete(capturing, table, oldRow);
                    }
                }
            }

            // 3. Inserts appended last.
            final List<Row> ins = inserts.get(table);
            if (ins != null) {
                for (final Row row : ins) {
                    base.insert(row);
                    if (captured) {
                        streamManager.trackInsert(capturing, table, row);
                    }
                }
            }
        }
    }

    /** The fully-qualified names of every table this write set holds a buffered insert, update, or delete for. */
    public Set<String> changedTables() {
        final Set<String> out = new LinkedHashSet<>();
        out.addAll(inserts.keySet());
        out.addAll(updates.keySet());
        out.addAll(deletes.keySet());
        return out;
    }

    public boolean isEmpty() {
        return inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty();
    }

    public void clear() {
        inserts.clear();
        updates.clear();
        deletes.clear();
        tableLocks.clear();
        lockNotedRaw.clear();
    }

    /**
     * A statement savepoint: a copy of this write set's structure (Rows are shared — they're built fresh
     * per change and never mutated in place). Pairs with {@link #restoreFrom} for statement-level rollback
     * (a failed statement inside an explicit transaction undoes only itself and keeps the txn open).
     */
    public TransactionWriteSet copy() {
        final TransactionWriteSet c = new TransactionWriteSet();
        for (final Map.Entry<String, List<Row>> e : inserts.entrySet()) {
            c.inserts.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        for (final Map.Entry<String, Map<Long, Row>> e : updates.entrySet()) {
            c.updates.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        for (final Map.Entry<String, Set<Long>> e : deletes.entrySet()) {
            c.deletes.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        c.tableLocks.putAll(tableLocks);
        return c;
    }

    /** Restore this write set's contents from a {@link #copy} savepoint (statement-level rollback). */
    public void restoreFrom(final TransactionWriteSet savepoint) {
        clear();
        for (final Map.Entry<String, List<Row>> e : savepoint.inserts.entrySet()) {
            inserts.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        for (final Map.Entry<String, Map<Long, Row>> e : savepoint.updates.entrySet()) {
            updates.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        }
        for (final Map.Entry<String, Set<Long>> e : savepoint.deletes.entrySet()) {
            deletes.put(e.getKey(), new HashSet<>(e.getValue()));
        }
        tableLocks.putAll(savepoint.tableLocks);
    }
}
