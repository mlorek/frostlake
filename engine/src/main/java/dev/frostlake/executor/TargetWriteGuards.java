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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;

import java.util.List;

/**
 * What every statement that changes a table's stored rows asks of its target, UPDATE, DELETE and MERGE alike: the
 * target's row access policy ahead of the statement's own stages, so the statement changes only the rows the policy
 * lets the session see and counts only those (live-verified: a MERGE neither updates nor deletes a hidden row, and
 * a source row that matches a hidden row alone is NOT MATCHED); another session's lock on the table before the first
 * row is written; and, on a hybrid table, the keys the statement's writes must leave whole (see HybridKeyGuard).
 */
final class TargetWriteGuards {

    private final QueryExecutor executor;
    private final HybridKeyGuard keys;

    /**
     * @param executor the executor whose catalog and storage hold the tables
     */
    TargetWriteGuards(final QueryExecutor executor) {
        this.executor = executor;
        this.keys = new HybridKeyGuard(executor);
    }

    /**
     * The target's row access policy, ahead of the statement's own stages: a statement changes only the rows the
     * policy lets the session see, and counts only those (live-verified).
     *
     * @param plan  the statement's plan, its target the source
     * @param table the target
     */
    void police(final SelectPlan plan, final Table table) {
        if (table.hasRowAccessPolicy()) {
            plan.add(executor.rowAccessPolicyStage(table));
        }
    }

    /**
     * A write that changes rows waits first for another session's transaction that holds the table (see
     * {@link QueryExecutor#requireTableUnlocked}); one that changes none takes nothing (live-verified).
     *
     * @param changing           the rows the statement changes
     * @param fullyQualifiedName the target's full name
     * @param table              the target
     */
    void requireUnlockedFor(final List<Row> changing, final String fullyQualifiedName, final Table table) {
        if (!changing.isEmpty()) {
            executor.requireTableUnlocked(fullyQualifiedName, table.getName());
        }
    }

    /** The keys an UPDATE of a hybrid table must leave whole, judged before any row is written (see HybridKeyGuard). */
    void requireUpdate(final Table table, final String fullyQualifiedName, final List<Row> rows,
                       final List<Row> changed, final List<Row> images) {
        keys.requireUpdate(table, fullyQualifiedName, rows, changed, images);
    }

    /** The keys a DELETE from a hybrid table must leave whole, judged before any row is deleted (HybridKeyGuard). */
    void requireDelete(final Table table, final String fullyQualifiedName, final List<Row> rows,
                       final List<Row> deleted) {
        keys.requireDelete(table, fullyQualifiedName, rows, deleted);
    }

    /** A row written into a hybrid table names a parent row with each of its foreign keys (see HybridKeyGuard). */
    void refuseOrphanRow(final Table table, final String fullyQualifiedName, final Row row) {
        keys.refuseOrphanRow(table, fullyQualifiedName, row);
    }
}
