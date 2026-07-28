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
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.StorageEngine.TableStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public void recordInsert(final String table, final Row row) {
        List<Row> list = inserts.get(table);
        if (list == null) {
            list = new ArrayList<>();
            inserts.put(table, list);
        }
        list.add(row);
    }

    /** Record a new value for an existing base row (by stable id). No-op if this txn already deleted it. */
    public void recordUpdate(final String table, final long rowId, final Row newValue) {
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
    }

    /** Record deletion of an existing base row (by stable id); supersedes any pending update of that row. */
    public void recordDelete(final String table, final long rowId) {
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
    }

    /**
     * Record a TRUNCATE of {@code table} within this transaction: every committed base row (given by
     * {@code committedRowIds}) becomes a pending delete and any not-yet-committed inserts/updates for the
     * table are dropped. The transaction then sees the table as empty, while a rollback still restores the
     * committed rows — the base store is cleared only when these deletes are applied at commit. Clearing
     * the base store directly instead would let this transaction's buffered inserts reappear on next read.
     */
    public void recordTruncate(final String table, final List<Long> committedRowIds) {
        inserts.remove(table);
        updates.remove(table);
        deletes.put(table, new HashSet<>(committedRowIds));
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
        final List<Row> ins = inserts.get(table);
        if (ins != null && index >= 0 && index < ins.size()) {
            ins.set(index, newRow);
        }
    }

    /** Drop a not-yet-committed inserted row (in-transaction DELETE of a row this txn just inserted). */
    public void removePendingInsert(final String table, final int index) {
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
        final List<Row> baseRows = base.scan();
        final List<Long> baseIds = base.getRowIds();
        final Set<Long> del = deletes.get(table);
        final Map<Long, Row> upd = updates.get(table);

        final List<Row> result = new ArrayList<>(baseRows.size());
        for (int i = 0; i < baseRows.size(); i++) {
            final long id = baseIds.get(i);
            if (del != null && del.contains(id)) {
                continue;
            }
            final Row updated = upd == null ? null : upd.get(id);
            result.add(updated != null ? updated : baseRows.get(i));
        }
        final List<Row> ins = inserts.get(table);
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

            // 1. Updates first.
            if (upd != null) {
                for (final Map.Entry<Long, Row> entry : upd.entrySet()) {
                    final Integer idx = idToIndex.get(entry.getKey());
                    if (idx != null) {
                        final Row oldRow = streamManager != null ? base.getRow(idx).copy() : null;
                        base.update(idx, entry.getValue());
                        if (streamManager != null) {
                            streamManager.trackUpdate(table, oldRow, entry.getValue());
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
                for (final Integer idx : indices) {
                    final Row oldRow = streamManager != null ? base.getRow(idx).copy() : null;
                    base.delete(idx);
                    if (streamManager != null) {
                        streamManager.trackDelete(table, oldRow);
                    }
                }
            }

            // 3. Inserts appended last.
            final List<Row> ins = inserts.get(table);
            if (ins != null) {
                for (final Row row : ins) {
                    base.insert(row);
                    if (streamManager != null) {
                        streamManager.trackInsert(table, row);
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
    }
}
