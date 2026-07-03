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
