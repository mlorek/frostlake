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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.transaction.TransactionWriteSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UPDATE and DELETE write-path query stage extracted from {@link QueryExecutor}: single-table UPDATE and
 * DELETE (incl. WITH-clause CTEs and WHERE filtering), the Snowflake join forms UPDATE ... FROM and
 * DELETE ... USING (multi-source cartesian join, first-match-wins), and the deferred-apply variants that
 * record changes into the transaction write set by stable row id. The mutable per-query CTE context is
 * read/written live through {@code executor.getCurrentCteContext()/setCurrentCteContext(...)} so the
 * save/set/restore idiom around SET-clause and WHERE subqueries stays byte-for-byte equivalent; the
 * deferred-apply flag and nullable late-wired stream manager are likewise read live. Shared services
 * (row filtering, column-index/expression evaluation, table-reference resolution, metadata merge,
 * constraint enforcement, CTE execution, name/text helpers, DML-count result shaping) stay on the
 * owning executor and are reached through {@code executor}.
 */
final class UpdateDeleteExecutor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateDeleteExecutor.class);

    private final QueryExecutor executor;

    UpdateDeleteExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Execute UPDATE from parsed context
     */
    Object executeUpdateFromContext(final FrostlakeParser.UpdateStatementContext ctx) {
        executor.checkNotReadOnly();
        try {
            // Auto-start transaction if not active
            if (!executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }

            // Handle WITH clause CTEs if present
            final Map<String, ResultSet> cteResults = ctx.withClause() != null
                ? executor.executeCTEs(ctx.withClause(), null) : null;

            String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            Table table = executor.getCatalog().resolveTable(tableName);

            // Check UPDATE permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.UPDATE, SecurableObjectType.TABLE, tableName);
            }

            // Parse assignments
            Map<String, String> assignments = new HashMap<>();
            for (final FrostlakeParser.AssignmentContext assign : ctx.assignmentList().assignment()) {
                // Handle qualified identifiers (table.column) or simple identifiers
                List<FrostlakeParser.IdentifierContext> identifiers = assign.identifier();
                String colName = executor.getIdentifier(identifiers.get(identifiers.size() - 1));
                // getOriginalText (not getText) so whitespace is preserved — a value like
                // (SELECT MAX(value) FROM test) must stay parseable when re-evaluated.
                String value = executor.getOriginalText(assign.expression());
                assignments.put(colName, value);
            }

            // Get all rows - use fully qualified name
            String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // UPDATE … FROM <source(s)>: join the target with the source rows on the WHERE predicate.
            if (ctx.tableReference() != null && !ctx.tableReference().isEmpty()) {
                final int updatedFromSources = executeUpdateFromSources(table, fullyQualifiedName, assignments,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    ctx.tableReference(), cteResults);
                logger.trace("Updated {} rows (UPDATE…FROM) in table: {}", updatedFromSources, tableName);
                return executor.dmlCountResult("number of rows updated", updatedFromSources);
            }

            if (executor.isDeferredApply()) {
                int n = executeUpdateDeferred(table, fullyQualifiedName, assignments,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    cteResults);
                logger.trace("Updated {} rows (deferred) in table: {}", n, tableName);
                return executor.dmlCountResult("number of rows updated", n);
            }

            List<Row> rows = executor.getStorageEngine().getTableStorage(fullyQualifiedName).scan();

            // Apply WHERE clause to find matching rows
            List<Row> matchingRows = rows;
            if (ctx.whereClause() != null) {
                String whereExpr = executor.getOriginalText(ctx.whereClause().booleanExpr());
                matchingRows = cteResults != null
                    ? executor.filterRowsWithCTEs(rows, table, whereExpr, cteResults)
                    : executor.filterRows(rows, table, whereExpr);
            }

            // Update matching rows. Expose any WITH-clause CTEs so a SET-clause subquery can resolve them.
            int rowsUpdated = 0;
            final Map<String, ResultSet> savedCteContext = executor.getCurrentCteContext();
            if (cteResults != null) {
                executor.setCurrentCteContext(cteResults);
            }
            try {
                for (final Row row : matchingRows) {
                    Row oldRow = row.copy();
                    int rowIndex = rows.indexOf(row);

                    for (final Map.Entry<String, String> entry : assignments.entrySet()) {
                        String colName = entry.getKey();
                        String valueExpr = entry.getValue();

                        int colIndex = executor.getColumnIndex(table, colName);
                        Object newValue = executor.evaluateExpression(valueExpr, row, table);
                        row.setValue(colIndex, newValue);
                    }
                    executor.enforceColumnConstraints(table, row);

                    // Log transaction
                    if (executor.getTransactionManager().hasActiveTransaction()) {
                        executor.getTransactionManager().getCurrentTransaction().logUpdate(fullyQualifiedName, rowIndex, oldRow, row);
                    }

                    // Track stream changes with fully qualified name
                    if (executor.getStreamManager() != null) {
                        executor.getStreamManager().trackUpdate(fullyQualifiedName, oldRow, row);
                    }

                    rowsUpdated++;
                }
            } finally {
                executor.setCurrentCteContext(savedCteContext);
            }

            logger.trace("Updated {} rows in table: {}", rowsUpdated, tableName);
            return executor.dmlCountResult("number of rows updated", rowsUpdated);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute UPDATE: " + e.getMessage(), e);
        }
    }

    /**
     * Execute DELETE from parsed context
     */
    Object executeDeleteFromContext(final FrostlakeParser.DeleteStatementContext ctx) {
        executor.checkNotReadOnly();
        try {
            // Auto-start transaction if not active
            if (!executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }

            // Handle WITH clause CTEs if present
            final Map<String, ResultSet> cteResults = ctx.withClause() != null
                ? executor.executeCTEs(ctx.withClause(), null) : null;

            String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            Table table = executor.getCatalog().resolveTable(tableName);

            // Check DELETE permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.DELETE, SecurableObjectType.TABLE, tableName);
            }

            // Get all rows - use fully qualified name
            String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // DELETE … USING <source(s)>: join the target with the source rows on the WHERE predicate.
            if (ctx.tableReference() != null && !ctx.tableReference().isEmpty()) {
                final int deletedUsingSources = executeDeleteUsingSources(table, fullyQualifiedName,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    ctx.tableReference(), cteResults);
                logger.trace("Deleted {} rows (DELETE…USING) from table: {}", deletedUsingSources, tableName);
                return executor.dmlCountResult("number of rows deleted", deletedUsingSources);
            }

            if (executor.isDeferredApply()) {
                int n = executeDeleteDeferred(table, fullyQualifiedName,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    cteResults);
                logger.trace("Deleted {} rows (deferred) from table: {}", n, tableName);
                return executor.dmlCountResult("number of rows deleted", n);
            }

            List<Row> rows = executor.getStorageEngine().getTableStorage(fullyQualifiedName).scan();

            // Apply WHERE clause (with CTE support)
            List<Row> rowsToDelete = rows;
            if (ctx.whereClause() != null) {
                String whereExpr = executor.getOriginalText(ctx.whereClause().booleanExpr());
                rowsToDelete = cteResults != null
                    ? executor.filterRowsWithCTEs(rows, table, whereExpr, cteResults)
                    : executor.filterRows(rows, table, whereExpr);
            }

            // Collect indices to delete (in reverse order to avoid shifting)
            List<Integer> indicesToDelete = new ArrayList<>();
            for (final Row row : rowsToDelete) {
                int rowIndex = rows.indexOf(row);
                if (rowIndex >= 0) {
                    indicesToDelete.add(rowIndex);
                }
            }

            // Sort in reverse order and delete
            indicesToDelete.sort(Collections.reverseOrder());
            int rowsDeleted = 0;
            for (final int index : indicesToDelete) {
                Row deletedRow = rows.get(index);

                // Log transaction before deleting
                if (executor.getTransactionManager().hasActiveTransaction()) {
                    executor.getTransactionManager().getCurrentTransaction().logDelete(fullyQualifiedName, index, deletedRow);
                }

                executor.getStorageEngine().getTableStorage(fullyQualifiedName).delete(index);

                // Track stream changes with fully qualified name
                if (executor.getStreamManager() != null) {
                    executor.getStreamManager().trackDelete(fullyQualifiedName, rowsToDelete.get(rowsDeleted));
                }

                rowsDeleted++;
            }

            logger.trace("Deleted {} rows from table: {}", rowsDeleted, tableName);
            return executor.dmlCountResult("number of rows deleted", rowsDeleted);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute DELETE: " + e.getMessage(), e);
        }
    }

    /**
     * Deferred-apply UPDATE ({@code transaction.executor.isDeferredApply()}): record new row values into the
     * transaction's write set keyed by stable row id instead of mutating live storage. Sees the
     * transaction's own prior writes — pending updates/deletes on base rows, and rows it inserted but
     * hasn't committed. See docs/acid-snowflake-plan.md.
     */
    private int executeUpdateDeferred(final Table table, final String fullyQualifiedName,
            final Map<String, String> assignments, final String whereExpr,
            final Map<String, ResultSet> cteResults) {
        final TransactionWriteSet writeSet = executor.getTransactionManager().getCurrentTransaction().getWriteSet();
        final StorageEngine.TableStorage tableStorage = executor.getStorageEngine().getTableStorage(fullyQualifiedName);

        // This transaction's effective view of committed base rows (pending updates applied, pending deletes
        // removed), tracking each row's stable id so the change can be recorded by id.
        final List<Row> baseRows = tableStorage.scan();
        final List<Long> baseIds = tableStorage.getRowIds();
        final List<Row> effective = new ArrayList<>(baseRows.size());
        final List<Long> effectiveIds = new ArrayList<>(baseRows.size());
        for (int i = 0; i < baseRows.size(); i++) {
            final long id = baseIds.get(i);
            if (writeSet.isDeleted(fullyQualifiedName, id)) {
                continue;
            }
            final Row pending = writeSet.pendingUpdate(fullyQualifiedName, id);
            effective.add(pending != null ? pending : baseRows.get(i));
            effectiveIds.add(id);
        }

        int rowsUpdated = 0;

        List<Row> matching = effective;
        if (whereExpr != null) {
            matching = cteResults != null
                ? executor.filterRowsWithCTEs(effective, table, whereExpr, cteResults)
                : executor.filterRows(effective, table, whereExpr);
        }

        // Rows this transaction inserted but hasn't committed yet: modify the pending insert directly.
        final List<Row> pendingInserts = writeSet.pendingInserts(fullyQualifiedName);
        List<Row> matchingPending = pendingInserts;
        if (whereExpr != null) {
            matchingPending = cteResults != null
                ? executor.filterRowsWithCTEs(pendingInserts, table, whereExpr, cteResults)
                : executor.filterRows(pendingInserts, table, whereExpr);
        }

        // Expose WITH-clause CTEs while building the new rows, so a subquery in the SET clause
        // (SET v = (SELECT x FROM cte)) can resolve the CTE — the same field WHERE subqueries use.
        final Map<String, ResultSet> savedCteContext = executor.getCurrentCteContext();
        if (cteResults != null) {
            executor.setCurrentCteContext(cteResults);
        }
        try {
            for (final Row row : matching) {
                final int idx = effective.indexOf(row);
                if (idx >= 0) {
                    writeSet.recordUpdate(fullyQualifiedName, effectiveIds.get(idx),
                        buildUpdatedRow(row, table, assignments));
                    rowsUpdated++;
                }
            }
            for (final Row row : matchingPending) {
                final int idx = pendingInserts.indexOf(row);
                if (idx >= 0) {
                    writeSet.setPendingInsert(fullyQualifiedName, idx, buildUpdatedRow(row, table, assignments));
                    rowsUpdated++;
                }
            }
        } finally {
            executor.setCurrentCteContext(savedCteContext);
        }

        return rowsUpdated;
    }

    /**
     * Deferred-apply DELETE ({@code transaction.executor.isDeferredApply()}): record tombstones for matching base rows
     * (by stable id) into the write set and drop matching not-yet-committed inserts, instead of mutating
     * live storage.
     */
    private int executeDeleteDeferred(final Table table, final String fullyQualifiedName,
            final String whereExpr, final Map<String, ResultSet> cteResults) {
        final TransactionWriteSet writeSet = executor.getTransactionManager().getCurrentTransaction().getWriteSet();
        final StorageEngine.TableStorage tableStorage = executor.getStorageEngine().getTableStorage(fullyQualifiedName);

        final List<Row> baseRows = tableStorage.scan();
        final List<Long> baseIds = tableStorage.getRowIds();
        final List<Row> effective = new ArrayList<>(baseRows.size());
        final List<Long> effectiveIds = new ArrayList<>(baseRows.size());
        for (int i = 0; i < baseRows.size(); i++) {
            final long id = baseIds.get(i);
            if (writeSet.isDeleted(fullyQualifiedName, id)) {
                continue;
            }
            final Row pending = writeSet.pendingUpdate(fullyQualifiedName, id);
            effective.add(pending != null ? pending : baseRows.get(i));
            effectiveIds.add(id);
        }

        int rowsDeleted = 0;

        List<Row> matching = effective;
        if (whereExpr != null) {
            matching = cteResults != null
                ? executor.filterRowsWithCTEs(effective, table, whereExpr, cteResults)
                : executor.filterRows(effective, table, whereExpr);
        }
        for (final Row row : matching) {
            final int idx = effective.indexOf(row);
            if (idx >= 0) {
                writeSet.recordDelete(fullyQualifiedName, effectiveIds.get(idx));
                rowsDeleted++;
            }
        }

        // Drop matching rows this transaction inserted but hasn't committed (remove high-to-low).
        final List<Row> pendingInserts = writeSet.pendingInserts(fullyQualifiedName);
        List<Row> matchingPending = pendingInserts;
        if (whereExpr != null) {
            matchingPending = cteResults != null
                ? executor.filterRowsWithCTEs(pendingInserts, table, whereExpr, cteResults)
                : executor.filterRows(pendingInserts, table, whereExpr);
        }
        final List<Integer> pendingIndices = new ArrayList<>();
        for (final Row row : matchingPending) {
            final int idx = pendingInserts.indexOf(row);
            if (idx >= 0) {
                pendingIndices.add(idx);
            }
        }
        pendingIndices.sort(Collections.reverseOrder());
        for (final int idx : pendingIndices) {
            writeSet.removePendingInsert(fullyQualifiedName, idx);
            rowsDeleted++;
        }

        return rowsDeleted;
    }

    /** Build a copy of {@code source} with the UPDATE assignments applied (never mutates the base row). */
    private Row buildUpdatedRow(final Row source, final Table table, final Map<String, String> assignments) {
        final Row newRow = source.copy();
        for (final Map.Entry<String, String> entry : assignments.entrySet()) {
            final int colIndex = executor.getColumnIndex(table, entry.getKey());
            final Object newValue = executor.evaluateExpression(entry.getValue(), newRow, table);
            newRow.setValue(colIndex, newValue);
        }
        executor.enforceColumnConstraints(table, newRow);
        return newRow;
    }

    // ── UPDATE … FROM / DELETE … USING ──────────────────────────────────────────────────────────────────
    // Snowflake's join-update / join-delete: the target is joined with one or more source tables on the
    // WHERE predicate. Built over the same deferred-apply write set (by stable row id) as the plain forms,
    // and over the alias-aware multi-table expression evaluator the SELECT join path uses, so qualified
    // references (target.col / source.col) resolve. When a target row joins more than one source row the
    // first match wins (Snowflake leaves multi-match updates non-deterministic by default).

    private int executeUpdateFromSources(final Table target, final String targetFqn,
            final Map<String, String> assignments, final String whereExpr,
            final List<FrostlakeParser.TableReferenceContext> sourceRefs, final Map<String, ResultSet> cteResults) {
        final List<Table> allTables = new ArrayList<>();
        allTables.add(target);
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put(target.getName().toUpperCase(), target);
        Table combined = target;
        final List<List<Row>> sourceRowLists = new ArrayList<>();
        for (final FrostlakeParser.TableReferenceContext srcRef : sourceRefs) {
            final TableData sd = executor.executeTableReference(srcRef, null, cteResults);
            allTables.add(sd.table);
            aliasToTable.put((sd.alias != null ? sd.alias : sd.table.getName()).toUpperCase(), sd.table);
            sourceRowLists.add(sd.rows);
            combined = executor.mergeTableMetadata(combined, sd.table);
        }
        final List<List<Object>> sourceCombos = cartesianRowValues(sourceRowLists);

        final ExpressionEvaluator ev = new ExpressionEvaluator(combined, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        ev.setMultiTableContext(aliasToTable, allTables);
        final Expression wherePred = whereExpr != null ? ExpressionEvaluator.parse(whereExpr) : null;

        final Map<Integer, Expression> setByColumn = new LinkedHashMap<>();
        for (final Map.Entry<String, String> e : assignments.entrySet()) {
            setByColumn.put(executor.getColumnIndex(target, e.getKey()), ExpressionEvaluator.parse(e.getValue()));
        }

        final TransactionWriteSet writeSet = executor.isDeferredApply()
            ? executor.getTransactionManager().getCurrentTransaction().getWriteSet() : null;
        final StorageEngine.TableStorage tableStorage = executor.getStorageEngine().getTableStorage(targetFqn);
        final List<Row> baseRows = tableStorage.scan();
        final List<Long> baseIds = tableStorage.getRowIds();

        int updated = 0;
        for (int i = 0; i < baseRows.size(); i++) {
            final long id = baseIds.get(i);
            if (executor.isDeferredApply() && writeSet.isDeleted(targetFqn, id)) {
                continue;
            }
            final Row pending = executor.isDeferredApply() ? writeSet.pendingUpdate(targetFqn, id) : null;
            final Row targetRow = pending != null ? pending : baseRows.get(i);
            final Row updatedRow = joinUpdatedRow(targetRow, target, sourceCombos, ev, wherePred, setByColumn);
            if (updatedRow == null) {
                continue;   // no source row joined this target row → leave it unchanged
            }
            if (executor.isDeferredApply()) {
                writeSet.recordUpdate(targetFqn, id, updatedRow);
            } else {
                final Row oldRow = baseRows.get(i).copy();
                tableStorage.update(i, updatedRow);
                if (executor.getTransactionManager().hasActiveTransaction()) {
                    executor.getTransactionManager().getCurrentTransaction().logUpdate(targetFqn, i, oldRow, updatedRow);
                }
                if (executor.getStreamManager() != null) {
                    executor.getStreamManager().trackUpdate(targetFqn, oldRow, updatedRow);
                }
            }
            updated++;
        }
        return updated;
    }

    private int executeDeleteUsingSources(final Table target, final String targetFqn,
            final String whereExpr, final List<FrostlakeParser.TableReferenceContext> sourceRefs,
            final Map<String, ResultSet> cteResults) {
        final List<Table> allTables = new ArrayList<>();
        allTables.add(target);
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put(target.getName().toUpperCase(), target);
        Table combined = target;
        final List<List<Row>> sourceRowLists = new ArrayList<>();
        for (final FrostlakeParser.TableReferenceContext srcRef : sourceRefs) {
            final TableData sd = executor.executeTableReference(srcRef, null, cteResults);
            allTables.add(sd.table);
            aliasToTable.put((sd.alias != null ? sd.alias : sd.table.getName()).toUpperCase(), sd.table);
            sourceRowLists.add(sd.rows);
            combined = executor.mergeTableMetadata(combined, sd.table);
        }
        final List<List<Object>> sourceCombos = cartesianRowValues(sourceRowLists);

        final ExpressionEvaluator ev = new ExpressionEvaluator(combined, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        ev.setMultiTableContext(aliasToTable, allTables);
        final Expression wherePred = whereExpr != null ? ExpressionEvaluator.parse(whereExpr) : null;

        final TransactionWriteSet writeSet = executor.isDeferredApply()
            ? executor.getTransactionManager().getCurrentTransaction().getWriteSet() : null;
        final StorageEngine.TableStorage tableStorage = executor.getStorageEngine().getTableStorage(targetFqn);
        final List<Row> baseRows = tableStorage.scan();
        final List<Long> baseIds = tableStorage.getRowIds();

        int deleted = 0;
        final List<Integer> immediateDeletes = new ArrayList<>();
        for (int i = 0; i < baseRows.size(); i++) {
            final long id = baseIds.get(i);
            if (executor.isDeferredApply() && writeSet.isDeleted(targetFqn, id)) {
                continue;
            }
            final Row pending = executor.isDeferredApply() ? writeSet.pendingUpdate(targetFqn, id) : null;
            final Row targetRow = pending != null ? pending : baseRows.get(i);
            if (!joinMatches(targetRow, sourceCombos, ev, wherePred)) {
                continue;
            }
            if (executor.isDeferredApply()) {
                writeSet.recordDelete(targetFqn, id);
                deleted++;
            } else {
                immediateDeletes.add(i);
            }
        }
        if (!executor.isDeferredApply()) {
            immediateDeletes.sort(Collections.reverseOrder());
            for (final int idx : immediateDeletes) {
                final Row del = baseRows.get(idx);
                if (executor.getTransactionManager().hasActiveTransaction()) {
                    executor.getTransactionManager().getCurrentTransaction().logDelete(targetFqn, idx, del);
                }
                tableStorage.delete(idx);
                if (executor.getStreamManager() != null) {
                    executor.getStreamManager().trackDelete(targetFqn, del);
                }
                deleted++;
            }
        }
        return deleted;
    }

    /** The cartesian product of the source tables' rows, each combination flattened to its column values in
     *  source order. A single source yields one combination per row; an empty source yields no combinations
     *  (so the inner join produces nothing). */
    private List<List<Object>> cartesianRowValues(final List<List<Row>> sourceRowLists) {
        List<List<Object>> combos = new ArrayList<>();
        combos.add(new ArrayList<>());
        for (final List<Row> rows : sourceRowLists) {
            final List<List<Object>> next = new ArrayList<>();
            for (final List<Object> prefix : combos) {
                for (final Row row : rows) {
                    final List<Object> merged = new ArrayList<>(prefix);
                    merged.addAll(row.getValues());
                    next.add(merged);
                }
            }
            combos = next;
        }
        return combos;
    }

    /** First source combination satisfying the predicate yields the SET-applied target row (first match
     *  wins); null when no source row joins this target row. */
    private Row joinUpdatedRow(final Row targetRow, final Table target, final List<List<Object>> sourceCombos,
            final ExpressionEvaluator ev, final Expression wherePred, final Map<Integer, Expression> setByColumn) {
        for (final List<Object> combo : sourceCombos) {
            final List<Object> values = new ArrayList<>(targetRow.getValues());
            values.addAll(combo);
            final Row combinedRow = Row.of(values);
            if (wherePred == null || isTrueResult(ev.evaluate(wherePred, combinedRow))) {
                final Row newRow = targetRow.copy();
                for (final Map.Entry<Integer, Expression> s : setByColumn.entrySet()) {
                    newRow.setValue(s.getKey(), ev.evaluate(s.getValue(), combinedRow));
                }
                executor.enforceColumnConstraints(target, newRow);
                return newRow;
            }
        }
        return null;
    }

    /** True if any source combination satisfies the predicate (the target row joins at least one source). */
    private boolean joinMatches(final Row targetRow, final List<List<Object>> sourceCombos,
            final ExpressionEvaluator ev, final Expression wherePred) {
        for (final List<Object> combo : sourceCombos) {
            final List<Object> values = new ArrayList<>(targetRow.getValues());
            values.addAll(combo);
            if (wherePred == null || isTrueResult(ev.evaluate(wherePred, Row.of(values)))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrueResult(final Object result) {
        return result instanceof Boolean && (Boolean) result;
    }
}
