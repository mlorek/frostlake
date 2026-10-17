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

package dev.frostlake.executor;

import dev.frostlake.metastore.HybridKeys;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.transaction.TransactionWriteSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Statement-scoped duplicate-key guard for deferred-apply INSERTs. The former per-row checks
 * re-copied the transaction's pending inserts for every row (O(k²) over a k-row statement) and, for
 * each UNIQUE column, re-copied the ENTIRE committed table per row. This guard prefetches both once
 * — pending primary keys and each UNIQUE column's existing values into hash sets — and then adds
 * every accepted row's values, so validating n rows costs O(n) set probes.
 *
 * <p>PRIMARY KEY enforcement applies when the engine-level {@code constraints.enforce.primaryKey}
 * flag is on and the table declares a key; UNIQUE enforcement only under the opt-in
 * {@code constraints.enforce.uniqueKey} flag (off by default — UNIQUE is otherwise informational,
 * matching Snowflake). UNIQUE is modeled per column, so a composite UNIQUE is enforced column by
 * column; PK columns are covered by the primary-key check and NULLs are unconstrained. A hybrid table holds
 * its keys whatever the switches say, each UNIQUE key whole, one over several columns as one. Pending
 * UPDATEs are deliberately not consulted, and the committed base is walked by position — resolution
 * prefetch over a scan, unique indexes are intentionally not implemented.
 */
public final class DeferredInsertGuard {

    private final TableStorage base;
    /** The table, whose kind decides how a duplicate is refused: a hybrid table names the index it breaks. */
    private final Table table;
    private final boolean checkPrimaryKey;
    private final Set<Object> pendingPrimaryKeys;
    private final int[] uniqueColumnIndexes;
    private final List<TableColumn> uniqueColumns;
    private final List<Set<Object>> uniqueSeenValues;
    /** A hybrid table's UNIQUE keys and the values each holds, every key whole (see HybridKeys.uniqueKeys). */
    private final List<UniqueConstraint> hybridKeys = new ArrayList<>();
    private final List<Set<List<Object>>> hybridSeenKeys = new ArrayList<>();

    public DeferredInsertGuard(final Table table, final TableStorage base,
                               final TransactionWriteSet writeSet, final String fullyQualifiedName,
                               final boolean enforcePrimaryKey, final boolean enforceUniqueKey) {
        this.base = base;
        this.table = table;
        // A hybrid table holds its keys whatever the engine's switches say (documented).
        this.checkPrimaryKey = (enforcePrimaryKey || table.isHybrid()) && !table.getPrimaryKeys().isEmpty();
        if (checkPrimaryKey) {
            pendingPrimaryKeys = new HashSet<>();
            for (final Row pending : writeSet.pendingInserts(fullyQualifiedName)) {
                final Object pk = base.primaryKeyOf(pending);
                if (pk != null) {
                    pendingPrimaryKeys.add(pk);
                }
            }
        } else {
            pendingPrimaryKeys = null;
        }
        uniqueColumns = new ArrayList<>();
        uniqueSeenValues = new ArrayList<>();
        if (table.isHybrid()) {
            uniqueColumnIndexes = new int[0];
            final List<Row> held = new ArrayList<>();
            for (int r = 0; r < base.getRowCount(); r++) {
                held.add(base.getRow(r));
            }
            held.addAll(writeSet.pendingInserts(fullyQualifiedName));
            for (final UniqueConstraint key : HybridKeys.uniqueKeys(table)) {
                final Set<List<Object>> seen = new HashSet<>();
                for (final Row existing : held) {
                    final List<Object> value = HybridKeys.keyOf(table, key.getColumnNames(), existing);
                    if (value != null) {
                        seen.add(value);
                    }
                }
                hybridKeys.add(key);
                hybridSeenKeys.add(seen);
            }
            return;
        }
        if (!enforceUniqueKey) {
            uniqueColumnIndexes = new int[0];
            return;
        }
        final List<TableColumn> cols = table.columnsView();
        final List<Integer> guarded = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            final TableColumn col = cols.get(i);
            if (col.isUnique() && !col.isPrimaryKey()) {
                guarded.add(i);
                uniqueColumns.add(col);
            }
        }
        uniqueColumnIndexes = new int[guarded.size()];
        final int rowCount = base.getRowCount();
        final List<Row> pendingRows = writeSet.pendingInserts(fullyQualifiedName);
        for (int u = 0; u < guarded.size(); u++) {
            final int i = guarded.get(u);
            uniqueColumnIndexes[u] = i;
            final Set<Object> seen = new HashSet<>();
            for (int r = 0; r < rowCount; r++) {
                final Row existing = base.getRow(r);
                if (i < existing.getValues().size() && existing.getValue(i) != null) {
                    seen.add(existing.getValue(i));
                }
            }
            for (final Row pending : pendingRows) {
                if (i < pending.getValues().size() && pending.getValue(i) != null) {
                    seen.add(pending.getValue(i));
                }
            }
            uniqueSeenValues.add(seen);
        }
    }

    /**
     * Refuse {@code row} if it duplicates a primary key (in the committed base or this transaction's
     * pending inserts) or a guarded UNIQUE column's non-null value; an accepted row's values join the
     * seen sets so later rows of the same statement are checked against it too.
     */
    public void validate(final Row row) {
        if (checkPrimaryKey) {
            final Object pk = base.primaryKeyOf(row);
            if (pk != null) {
                if (base.getRowByPrimaryKey(pk) != null || !pendingPrimaryKeys.add(pk)) {
                    throw new RuntimeException(table.isHybrid() ? HybridKeys.primaryKeyExists()
                        : "Duplicate primary key: " + pk);
                }
            }
        }
        for (int u = 0; u < uniqueColumnIndexes.length; u++) {
            final int i = uniqueColumnIndexes[u];
            if (i >= row.getValues().size()) {
                continue;
            }
            final Object value = row.getValue(i);
            if (value == null) {
                continue;
            }
            if (!uniqueSeenValues.get(u).add(value)) {
                throw new RuntimeException(
                    "Duplicate unique key on column '" + uniqueColumns.get(u).getName() + "': " + value);
            }
        }
        for (int k = 0; k < hybridKeys.size(); k++) {
            final List<Object> value = HybridKeys.keyOf(table, hybridKeys.get(k).getColumnNames(), row);
            if (value != null && !hybridSeenKeys.get(k).add(value)) {
                throw new RuntimeException(HybridKeys.uniqueViolated(table, hybridKeys.get(k)));
            }
        }
    }
}
