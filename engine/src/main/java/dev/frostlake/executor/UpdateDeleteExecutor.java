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
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
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

            // WITH-prefixed DML is not Snowflake syntax (live-verified).
            final Map<String, ResultSet> cteResults = null;

            String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Object");

            // Check UPDATE permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.UPDATE, SecurableObjectType.TABLE, tableName);
            }

            // Parse assignments
            Map<String, String> assignments = new HashMap<>();
            // Where each SET value begins in the statement, so an unknown name inside it reports the
            // place it was written. The assignments themselves are keyed by column, which loses the
            // parse context, so the origins travel alongside.
            final Map<String, SourcePosition> assignmentOrigins = new HashMap<>();
            for (final FrostlakeParser.AssignmentContext assign : ctx.assignmentList().assignment()) {
                // Handle qualified identifiers (table.column) or simple identifiers
                String colName = ParseTreeText.namePartText(assign.namePart());
                // getOriginalText (not getText) so whitespace is preserved — a value like
                // (SELECT MAX(value) FROM test) must stay parseable when re-evaluated.
                String value = executor.getOriginalText(assign.expression());
                assignments.put(colName, value);
                assignmentOrigins.put(colName, new SourcePosition(
                    assign.expression().getStart().getLine(),
                    assign.expression().getStart().getCharPositionInLine()));
                // Reject an unknown SET target HERE, where the statement is still in hand, so the
                // refusal can carry the position live reports — and before a single row is touched,
                // which is when live rejects it. The check delegates to the same resolution the
                // update loop uses, so WHICH statements are refused cannot drift; only the message
                // gains its position.
                requireColumn(table, colName, assign.namePart());
            }

            // Get all rows - use fully qualified name
            String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // UPDATE … FROM <source(s)>: join the target with the source rows on the WHERE predicate.
            if (ctx.tableReference() != null && !ctx.tableReference().isEmpty()) {
                // An Oracle (+) on a source column makes it a target LEFT JOIN source (all target rows updated,
                // source columns NULL when unmatched) rather than the default inner match.
                final boolean outerJoin = ctx.whereClause() != null
                    && executor.containsOuterJoinMarker(ctx.whereClause().booleanExpr());
                final int updatedFromSources = executeUpdateFromSources(table, fullyQualifiedName,
                    ctx.identifier() != null ? ctx.identifier().getText() : null, assignments,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    ctx.tableReference(), ctx.joinClause(), outerJoin, cteResults);
                logger.trace("Updated {} rows (UPDATE…FROM) in table: {}", updatedFromSources, tableName);
                return executor.updateCountResult(updatedFromSources);
            }

            final String updateTargetAlias = ctx.identifier() != null ? executor.getIdentifier(ctx.identifier()) : null;
            if (executor.isDeferredApply()) {
                int n = executeUpdateDeferred(table, fullyQualifiedName, updateTargetAlias, assignments,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    cteResults, assignmentOrigins,
                    ctx.whereClause() != null ? originOf(ctx.whereClause()) : null);
                logger.trace("Updated {} rows (deferred) in table: {}", n, tableName);
                return executor.updateCountResult(n);
            }

            List<Row> rows = executor.getStorageEngine().getTableStorage(fullyQualifiedName).scan();

            // Apply WHERE clause to find matching rows
            List<Row> matchingRows = rows;
            if (ctx.whereClause() != null) {
                String whereExpr = executor.getOriginalText(ctx.whereClause().booleanExpr());
                final SourcePosition displacedWhere = ExpressionSource.begin(originOf(ctx.whereClause()));
                try {
                    matchingRows = cteResults != null
                        ? executor.filterRowsWithCTEs(rows, table, updateTargetAlias, whereExpr, cteResults)
                        : executor.filterRows(rows, table, updateTargetAlias, whereExpr);
                } finally {
                    ExpressionSource.end(displacedWhere);
                }
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
                        final SourcePosition displaced =
                            ExpressionSource.begin(assignmentOrigins.get(colName));
                        final Object newValue;
                        try {
                            newValue = executor.evaluateExpression(valueExpr, row, table);
                        } finally {
                            ExpressionSource.end(displaced);
                        }
                        row.setValue(colIndex, newValue);
                    }
                    executor.enforceColumnConstraintsForDml(table, row);

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
            return executor.updateCountResult(rowsUpdated);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw StatementErrors.propagate(e);
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

            // WITH-prefixed DML is not Snowflake syntax (live-verified).
            final Map<String, ResultSet> cteResults = null;

            String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Object");

            // Check DELETE permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.DELETE, SecurableObjectType.TABLE, tableName);
            }

            // Get all rows - use fully qualified name
            String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // DELETE … USING <source(s)>: join the target with the source rows on the WHERE predicate.
            if (ctx.tableReference() != null && !ctx.tableReference().isEmpty()) {
                final int deletedUsingSources = executeDeleteUsingSources(table, fullyQualifiedName,
                    ctx.identifier() != null ? ctx.identifier().getText() : null,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    ctx.tableReference(), ctx.joinClause(), cteResults);
                logger.trace("Deleted {} rows (DELETE…USING) from table: {}", deletedUsingSources, tableName);
                return executor.dmlCountResult("number of rows deleted", deletedUsingSources);
            }

            final String deleteTargetAlias = ctx.identifier() != null ? executor.getIdentifier(ctx.identifier()) : null;
            if (executor.isDeferredApply()) {
                int n = executeDeleteDeferred(table, fullyQualifiedName, deleteTargetAlias,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    cteResults, ctx.whereClause() != null ? originOf(ctx.whereClause()) : null);
                logger.trace("Deleted {} rows (deferred) from table: {}", n, tableName);
                return executor.dmlCountResult("number of rows deleted", n);
            }

            List<Row> rows = executor.getStorageEngine().getTableStorage(fullyQualifiedName).scan();

            // Apply WHERE clause (with CTE support)
            List<Row> rowsToDelete = rows;
            if (ctx.whereClause() != null) {
                String whereExpr = executor.getOriginalText(ctx.whereClause().booleanExpr());
                rowsToDelete = cteResults != null
                    ? executor.filterRowsWithCTEs(rows, table, deleteTargetAlias, whereExpr, cteResults)
                    : executor.filterRows(rows, table, deleteTargetAlias, whereExpr);
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
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * Deferred-apply UPDATE ({@code transaction.executor.isDeferredApply()}): record new row values into the
     * transaction's write set keyed by stable row id instead of mutating live storage. Sees the
     * transaction's own prior writes — pending updates/deletes on base rows, and rows it inserted but
     * hasn't committed. See docs/acid-snowflake-plan.md.
     */
    private int executeUpdateDeferred(final Table table, final String fullyQualifiedName,
            final String targetAlias,
            final Map<String, String> assignments, final String whereExpr,
            final Map<String, ResultSet> cteResults,
            final Map<String, SourcePosition> assignmentOrigins, final SourcePosition whereOrigin) {
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
            final SourcePosition displaced = ExpressionSource.begin(whereOrigin);
            try {
                matching = cteResults != null
                    ? executor.filterRowsWithCTEs(effective, table, targetAlias, whereExpr, cteResults)
                    : executor.filterRows(effective, table, targetAlias, whereExpr);
            } finally {
                ExpressionSource.end(displaced);
            }
        }

        // Rows this transaction inserted but hasn't committed yet: modify the pending insert directly.
        final List<Row> pendingInserts = writeSet.pendingInserts(fullyQualifiedName);
        List<Row> matchingPending = pendingInserts;
        if (whereExpr != null) {
            matchingPending = cteResults != null
                ? executor.filterRowsWithCTEs(pendingInserts, table, targetAlias, whereExpr, cteResults)
                : executor.filterRows(pendingInserts, table, targetAlias, whereExpr);
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
                        buildUpdatedRow(row, table, assignments, assignmentOrigins));
                    rowsUpdated++;
                }
            }
            for (final Row row : matchingPending) {
                final int idx = pendingInserts.indexOf(row);
                if (idx >= 0) {
                    writeSet.setPendingInsert(fullyQualifiedName, idx, buildUpdatedRow(row, table, assignments, assignmentOrigins));
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
            final String targetAlias,
            final String whereExpr, final Map<String, ResultSet> cteResults,
            final SourcePosition whereOrigin) {
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
            final SourcePosition displaced = ExpressionSource.begin(whereOrigin);
            try {
                matching = cteResults != null
                    ? executor.filterRowsWithCTEs(effective, table, targetAlias, whereExpr, cteResults)
                    : executor.filterRows(effective, table, targetAlias, whereExpr);
            } finally {
                ExpressionSource.end(displaced);
            }
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
                ? executor.filterRowsWithCTEs(pendingInserts, table, targetAlias, whereExpr, cteResults)
                : executor.filterRows(pendingInserts, table, targetAlias, whereExpr);
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
    private Row buildUpdatedRow(final Row source, final Table table, final Map<String, String> assignments,
                                final Map<String, SourcePosition> origins) {
        final Row newRow = source.copy();
        for (final Map.Entry<String, String> entry : assignments.entrySet()) {
            final int colIndex = executor.getColumnIndex(table, entry.getKey());
            // Each value is evaluated under ITS OWN origin, so an unknown name inside one SET value
            // reports that value's place rather than the statement's or the previous assignment's.
            final SourcePosition displaced =
                ExpressionSource.begin(origins == null ? null : origins.get(entry.getKey()));
            final Object newValue;
            try {
                newValue = executor.evaluateExpression(entry.getValue(), newRow, table);
            } finally {
                ExpressionSource.end(displaced);
            }
            newRow.setValue(colIndex, newValue);
        }
        executor.enforceColumnConstraintsForDml(table, newRow);
        return newRow;
    }

    // ── UPDATE … FROM / DELETE … USING ──────────────────────────────────────────────────────────────────
    // Snowflake's join-update / join-delete: the target is joined with one or more source tables on the
    // WHERE predicate. Built over the same deferred-apply write set (by stable row id) as the plain forms,
    // and over the alias-aware multi-table expression evaluator the SELECT join path uses, so qualified
    // references (target.col / source.col) resolve. When a target row joins more than one source row the
    // first match wins (Snowflake leaves multi-match updates non-deterministic by default).

    private int executeUpdateFromSources(final Table target, final String targetFqn, final String targetAlias,
            final Map<String, String> assignments, final String whereExpr,
            final List<FrostlakeParser.TableReferenceContext> sourceRefs,
            final List<FrostlakeParser.JoinClauseContext> joinClauses, final boolean outerJoin,
            final Map<String, ResultSet> cteResults) {
        final List<Table> allTables = new ArrayList<>();
        allTables.add(target);
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put(target.getName().toUpperCase(), target);
        // Register the target's alias too (UPDATE t tgt … FROM s …): without it a WHERE like
        // tgt.col = s.col can't disambiguate tgt from the source when both share a column name.
        if (targetAlias != null) {
            aliasToTable.put(targetAlias.toUpperCase(), target);
        }
        final List<List<Object>> sourceCombos = buildSourceCombos(sourceRefs, joinClauses, cteResults, allTables, aliasToTable);
        Table combined = allTables.get(0);
        for (int i = 1; i < allTables.size(); i++) {
            combined = executor.mergeTableMetadata(combined, allTables.get(i));
        }
        final int sourceWidth = combined.getColumns().size() - target.getColumns().size();

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
            final Row updatedRow = joinUpdatedRow(targetRow, target, sourceCombos, ev, wherePred, setByColumn, outerJoin, sourceWidth);
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
        // Rows this transaction has INSERTED but not yet committed live only in the write set — the
        // loader idiom stages rows into a table and immediately joins-updates them in the same
        // transaction. Skipping them made the UPDATE…FROM silently touch nothing.
        if (executor.isDeferredApply()) {
            final List<Row> pendingInserts = writeSet.pendingInserts(targetFqn);
            for (int p = 0; p < pendingInserts.size(); p++) {
                final Row updatedRow = joinUpdatedRow(pendingInserts.get(p), target, sourceCombos, ev,
                    wherePred, setByColumn, outerJoin, sourceWidth);
                if (updatedRow == null) {
                    continue;
                }
                writeSet.setPendingInsert(targetFqn, p, updatedRow);
                updated++;
            }
        }
        return updated;
    }

    private int executeDeleteUsingSources(final Table target, final String targetFqn, final String targetAlias,
            final String whereExpr, final List<FrostlakeParser.TableReferenceContext> sourceRefs,
            final List<FrostlakeParser.JoinClauseContext> joinClauses, final Map<String, ResultSet> cteResults) {
        final List<Table> allTables = new ArrayList<>();
        allTables.add(target);
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put(target.getName().toUpperCase(), target);
        // Register the target's alias too (DELETE FROM t wcs USING …): without it a WHERE like
        // wcs.col = d.col can't disambiguate wcs from the source when both share a column name.
        if (targetAlias != null) {
            aliasToTable.put(targetAlias.toUpperCase(), target);
        }
        final List<List<Object>> sourceCombos = buildSourceCombos(sourceRefs, joinClauses, cteResults, allTables, aliasToTable);
        Table combined = allTables.get(0);
        for (int i = 1; i < allTables.size(); i++) {
            combined = executor.mergeTableMetadata(combined, allTables.get(i));
        }

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
        // Rows this transaction has INSERTED but not yet committed live only in the write set —
        // walk them backwards so removal keeps earlier indices valid.
        if (executor.isDeferredApply()) {
            final List<Row> pendingInserts = writeSet.pendingInserts(targetFqn);
            for (int p = pendingInserts.size() - 1; p >= 0; p--) {
                if (joinMatches(pendingInserts.get(p), sourceCombos, ev, wherePred)) {
                    writeSet.removePendingInsert(targetFqn, p);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /**
     * Build the SOURCE side of an UPDATE…FROM / DELETE…USING: the first reference, any comma-separated
     * references (cross-joined), then any explicit JOINs (LEFT/RIGHT/FULL/INNER/CROSS/NATURAL, honoring
     * ON/USING). Returns each resulting source row's values, to be concatenated after the target row when
     * evaluating the WHERE predicate and SET expressions. APPENDS the source tables/aliases to
     * {@code allTables}/{@code aliasToTable} (the caller has already added the target) so those references
     * resolve there; a separate source-only alias context is used to resolve the JOIN ON conditions, since
     * the joined source rows carry no target columns. A LEFT/RIGHT/FULL join keeps its unmatched (null-padded)
     * rows, so a target row can match a source row whose outer side is NULL.
     */
    private List<List<Object>> buildSourceCombos(
            final List<FrostlakeParser.TableReferenceContext> sourceRefs,
            final List<FrostlakeParser.JoinClauseContext> joinClauses,
            final Map<String, ResultSet> cteResults,
            final List<Table> allTables, final Map<String, Table> aliasToTable) {

        // Source-only alias context for JOIN ON resolution (the joined rows have no target columns).
        final Map<String, Table> srcAliasToTable = new HashMap<>();
        final List<Table> srcAllTables = new ArrayList<>();

        final TableData first = executor.executeTableReference(sourceRefs.get(0), null, cteResults);
        List<Row> srcRows = first.rows;
        Table srcTable = first.table;
        registerSource(first, allTables, aliasToTable, srcAllTables, srcAliasToTable);

        // Additional comma-separated sources → cross join.
        for (int i = 1; i < sourceRefs.size(); i++) {
            final TableData r = executor.executeTableReference(sourceRefs.get(i), null, cteResults);
            srcRows = executor.crossJoinRows(srcRows, srcTable, r.rows, r.table);
            srcTable = executor.mergeTableMetadata(srcTable, r.table);
            registerSource(r, allTables, aliasToTable, srcAllTables, srcAliasToTable);
        }

        // Explicit JOINs. Register the right side BEFORE applyJoin so its ON condition can resolve.
        if (joinClauses != null) {
            for (final FrostlakeParser.JoinClauseContext jc : joinClauses) {
                // The closest-match join is a SELECT-side operator; an UPDATE/DELETE source list would
                // silently degrade it to a plain join here, so reject it outright instead.
                if (jc.ASOF() != null || jc.asofMatchCondition() != null) {
                    throw new RuntimeException(
                        "ASOF JOIN is not supported in the FROM clause of an UPDATE or DELETE.");
                }
                final TableData r = executor.executeTableReference(jc.tableReference(), null, cteResults);
                registerSource(r, allTables, aliasToTable, srcAllTables, srcAliasToTable);
                srcRows = executor.applyJoin(srcRows, srcTable, r.rows, r.table, jc, srcAliasToTable, srcAllTables, null);
                srcTable = executor.mergeTableMetadata(srcTable, r.table);
            }
        }

        final List<List<Object>> combos = new ArrayList<>(srcRows.size());
        for (final Row r : srcRows) {
            combos.add(new ArrayList<>(r.getValues()));
        }
        return combos;
    }

    /** Register one source into both the combined alias context (target + sources) and the source-only one. */
    private void registerSource(final TableData sd, final List<Table> allTables, final Map<String, Table> aliasToTable,
            final List<Table> srcAllTables, final Map<String, Table> srcAliasToTable) {
        final String key = (sd.alias != null ? sd.alias : sd.table.getName()).toUpperCase();
        allTables.add(sd.table);
        aliasToTable.put(key, sd.table);
        srcAllTables.add(sd.table);
        srcAliasToTable.put(key, sd.table);
    }

    /** First source combination satisfying the predicate yields the SET-applied target row (first match
     *  wins); null when no source row joins this target row — UNLESS {@code outerJoin} (an Oracle {@code (+)}
     *  on the source), in which case the target row is still updated with the source columns bound to NULL
     *  ({@code sourceWidth} NULLs), matching a target LEFT JOIN source. */
    private Row joinUpdatedRow(final Row targetRow, final Table target, final List<List<Object>> sourceCombos,
            final ExpressionEvaluator ev, final Expression wherePred, final Map<Integer, Expression> setByColumn,
            final boolean outerJoin, final int sourceWidth) {
        for (final List<Object> combo : sourceCombos) {
            final List<Object> values = new ArrayList<>(targetRow.getValues());
            values.addAll(combo);
            final Row combinedRow = Row.of(values);
            if (wherePred == null || isTrueResult(ev.evaluate(wherePred, combinedRow))) {
                return applySet(targetRow, target, combinedRow, ev, setByColumn);
            }
        }
        if (outerJoin) {
            final List<Object> values = new ArrayList<>(targetRow.getValues());
            for (int i = 0; i < sourceWidth; i++) {
                values.add(null);
            }
            return applySet(targetRow, target, Row.of(values), ev, setByColumn);
        }
        return null;
    }

    /** Apply the SET assignments (evaluated over the combined target+source row) to a copy of the target. */
    private Row applySet(final Row targetRow, final Table target, final Row combinedRow,
            final ExpressionEvaluator ev, final Map<Integer, Expression> setByColumn) {
        final Row newRow = targetRow.copy();
        for (final Map.Entry<Integer, Expression> s : setByColumn.entrySet()) {
            newRow.setValue(s.getKey(), ev.evaluate(s.getValue(), combinedRow));
        }
        executor.enforceColumnConstraintsForDml(target, newRow);
        return newRow;
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
        return SqlTruth.isTrue(result);
    }

    /**
     * Confirm {@code colName} is a column of {@code table}, reporting an unknown one at the place it
     * was written. Resolution itself is delegated, so this can only add a position to a refusal that
     * would have happened anyway — never change which statements are refused.
     */
    private void requireColumn(final Table table, final String colName,
                               final FrostlakeParser.NamePartContext where) {
        try {
            executor.getColumnIndex(table, colName);
        } catch (final RuntimeException unknown) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                where.getStart().getLine(), where.getStart().getCharPositionInLine(),
                colName.toUpperCase()), unknown);
        }
    }

    /** Where a WHERE clause's PREDICATE begins — the offset the extracted text was taken from. */
    private SourcePosition originOf(final FrostlakeParser.WhereClauseContext where) {
        return new SourcePosition(where.booleanExpr().getStart().getLine(),
            where.booleanExpr().getStart().getCharPositionInLine());
    }
}
