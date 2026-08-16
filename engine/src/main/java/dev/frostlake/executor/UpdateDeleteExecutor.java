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

import dev.frostlake.executor.expressions.DefaultMarkerExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.DataType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
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

            final String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            final Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Object");
            // The storage key the target reads under, recorded as a SELECT's resolution records it: a
            // correlated subquery in the statement asks how many rows the target holds.
            table.setQualifiedName(executor.getFullyQualifiedTableName(tableName));

            // Check UPDATE permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.UPDATE, SecurableObjectType.TABLE, tableName);
            }

            // Parse assignments
            final Map<String, String> assignments = new HashMap<>();
            // Where each SET value begins in the statement, so an unknown name inside it reports the
            // place it was written. The assignments themselves are keyed by column, which loses the
            // parse context, so the origins travel alongside.
            final Map<String, SourcePosition> assignmentOrigins = new HashMap<>();
            for (final FrostlakeParser.AssignmentContext assign : ctx.assignmentList().assignment()) {
                // Handle qualified identifiers (table.column) or simple identifiers
                final String colName = ParseTreeText.namePartText(assign.namePart());
                // getOriginalText (not getText) so whitespace is preserved — a value like
                // (SELECT MAX(value) FROM test) must stay parseable when re-evaluated.
                final String value = executor.getOriginalText(assign.expression());
                assignments.put(colName, value);
                assignmentOrigins.put(colName, new SourcePosition(
                    assign.expression().getStart().getLine(),
                    assign.expression().getStart().getCharPositionInLine()));
            }
            compileNames(ctx, table, ctx.identifier() != null ? executor.getIdentifier(ctx.identifier()) : null,
                ctx.assignmentList().assignment(), ctx.whereClause(),
                ctx.tableReference() != null && !ctx.tableReference().isEmpty());

            // Get all rows - use fully qualified name
            final String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // The PARTITIONS lock registers per STATEMENT KIND, not per matched row — a rewrite
            // matching nothing still locks its table (live-verified).
            final var rewriteTxn = executor.getTransactionManager().getCurrentTransaction();
            if (rewriteTxn != null) {
                rewriteTxn.recordTableTouch(fullyQualifiedName);
            }

            // UPDATE … FROM <source(s)>: join the target with the source rows on the WHERE predicate.
            if (ctx.tableReference() != null && !ctx.tableReference().isEmpty()) {
                // An Oracle (+) on a source column makes it a target LEFT JOIN source (all target rows updated,
                // source columns NULL when unmatched) rather than the default inner match.
                final boolean outerJoin = ctx.whereClause() != null
                    && executor.containsOuterJoinMarker(ctx.whereClause().booleanExpr());
                final int updatedFromSources = executeUpdateFromSources(table, fullyQualifiedName,
                    ctx.identifier() != null ? ctx.identifier().getText() : null, assignments, assignmentOrigins,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    ctx.tableReference(), ctx.joinClause(), outerJoin, cteResults, tableName);
                logger.trace("Updated {} rows (UPDATE…FROM) in table: {}", updatedFromSources, tableName);
                return executor.updateCountResult(updatedFromSources);
            }

            final String updateTargetAlias = ctx.identifier() != null ? executor.getIdentifier(ctx.identifier()) : null;
            if (executor.isDeferredApply()) {
                final int n = executeUpdateDeferred(table, fullyQualifiedName, updateTargetAlias, assignments,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    cteResults, assignmentOrigins,
                    ctx.whereClause() != null ? originOf(ctx.whereClause()) : null, tableName);
                logger.trace("Updated {} rows (deferred) in table: {}", n, tableName);
                return executor.updateCountResult(n);
            }

            final List<Row> rows = executor.getStorageEngine().getTableStorage(fullyQualifiedName).scan();

            // Apply WHERE clause to find matching rows
            List<Row> matchingRows = rows;
            if (ctx.whereClause() != null) {
                final String whereExpr = executor.getOriginalText(ctx.whereClause().booleanExpr());
                final SourcePosition displacedWhere = ExpressionSource.beginNested(originOf(ctx.whereClause()));
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
                // Each SET column's index is a per-STATEMENT fact — resolve once, not per row.
                final Map<String, Integer> setIndexes = new HashMap<>();
                for (final String setColumn : assignments.keySet()) {
                    setIndexes.put(setColumn, executor.getColumnIndex(table, setColumn));
                }
                final TableStorage updateStorage =
                    executor.getStorageEngine().getTableStorage(fullyQualifiedName);
                final Map<Row, Integer> rowPositions = firstPositionsOf(rows);
                for (final Row row : matchingRows) {
                    // REPLACE, never mutate: the stored row stays frozen (time-travel snapshots are
                    // pointer copies because of this), and the untouched original IS the old image.
                    final Row oldRow = row;
                    final int rowIndex = rowPositions.get(row).intValue();
                    final Row updatedRow = row.copy();

                    for (final Map.Entry<String, String> entry : assignments.entrySet()) {
                        final String colName = entry.getKey();
                        final String valueExpr = entry.getValue();

                        final int colIndex = setIndexes.get(colName).intValue();
                        final SourcePosition displaced =
                            ExpressionSource.beginNested(assignmentOrigins.get(colName));
                        final Object newValue;
                        try {
                            // A bare DEFAULT writes the column's DECLARED default — or NULL when it has
                            // none, which a NOT NULL column then refuses, exactly as live does. Read
                            // from the parse tree: anything larger than the lone word evaluates, and
                            // raises the "invalid identifier 'DEFAULT'" live gives `SET c = DEFAULT + 1`.
                            newValue = ExpressionEvaluator.parse(valueExpr)
                                    instanceof DefaultMarkerExpression
                                ? executor.declaredDefaultFor(table, colIndex, fullyQualifiedName)
                                : executor.evaluateUpdateValue(valueExpr, updatedRow, table, updateTargetAlias);
                        } catch (final RuntimeException failed) {
                            // A plain UPDATE's SET value that cannot be computed is a DML failure on
                            // that column, live-verified: "DML operation to table UPD failed on column
                            // D with error: Failed to cast variant value 1 to DATE". (UPDATE … FROM
                            // and a MERGE's UPDATE leave the same sentence bare.)
                            throw DmlWriteTarget.isRowTimeFailure(failed)
                                ? DmlWriteTarget.failedOnColumn(tableName, colName, failed) : failed;
                        } finally {
                            ExpressionSource.end(displaced);
                        }
                        updatedRow.setValue(colIndex, newValue);
                    }
                    executor.enforceColumnConstraintsForDml(table, updatedRow, false, tableName);
                    updateStorage.replaceRow(rowIndex, updatedRow);

                    // Log transaction
                    if (executor.getTransactionManager().hasActiveTransaction()) {
                        executor.getTransactionManager().getCurrentTransaction().logUpdate(fullyQualifiedName, rowIndex, oldRow, updatedRow);
                    }

                    // Track stream changes with fully qualified name
                    if (executor.getStreamManager() != null) {
                        executor.getStreamManager().trackUpdate(fullyQualifiedName, oldRow, updatedRow);
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

            final String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            final Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Object");
            // The storage key the target reads under, recorded as a SELECT's resolution records it: a
            // correlated subquery in the statement asks how many rows the target holds.
            table.setQualifiedName(executor.getFullyQualifiedTableName(tableName));

            // Check DELETE permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.DELETE, SecurableObjectType.TABLE, tableName);
            }
            compileNames(ctx, table, ctx.identifier() != null ? executor.getIdentifier(ctx.identifier()) : null,
                new ArrayList<FrostlakeParser.AssignmentContext>(), ctx.whereClause(),
                ctx.tableReference() != null && !ctx.tableReference().isEmpty());

            // Get all rows - use fully qualified name
            final String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // The PARTITIONS lock registers per STATEMENT KIND, not per matched row — a DELETE
            // matching nothing still locks its table (live-verified).
            final var rewriteTxn = executor.getTransactionManager().getCurrentTransaction();
            if (rewriteTxn != null) {
                rewriteTxn.recordTableTouch(fullyQualifiedName);
            }

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
                final int n = executeDeleteDeferred(table, fullyQualifiedName, deleteTargetAlias,
                    ctx.whereClause() != null ? executor.getOriginalText(ctx.whereClause().booleanExpr()) : null,
                    cteResults, ctx.whereClause() != null ? originOf(ctx.whereClause()) : null);
                logger.trace("Deleted {} rows (deferred) from table: {}", n, tableName);
                return executor.dmlCountResult("number of rows deleted", n);
            }

            final List<Row> rows = executor.getStorageEngine().getTableStorage(fullyQualifiedName).scan();

            // Apply WHERE clause (with CTE support)
            List<Row> rowsToDelete = rows;
            if (ctx.whereClause() != null) {
                final String whereExpr = executor.getOriginalText(ctx.whereClause().booleanExpr());
                rowsToDelete = cteResults != null
                    ? executor.filterRowsWithCTEs(rows, table, deleteTargetAlias, whereExpr, cteResults)
                    : executor.filterRows(rows, table, deleteTargetAlias, whereExpr);
            }

            // Collect indices to delete (in reverse order to avoid shifting)
            final List<Integer> indicesToDelete = new ArrayList<>();
            final Map<Row, Integer> deletePositions = firstPositionsOf(rows);
            for (final Row row : rowsToDelete) {
                final Integer rowIndex = deletePositions.get(row);
                if (rowIndex != null) {
                    indicesToDelete.add(rowIndex);
                }
            }

            // Sort in reverse order; log each row first, then delete ALL in one compaction pass.
            indicesToDelete.sort(Collections.reverseOrder());
            int rowsDeleted = 0;
            for (final int index : indicesToDelete) {
                final Row deletedRow = rows.get(index);
                if (executor.getTransactionManager().hasActiveTransaction()) {
                    executor.getTransactionManager().getCurrentTransaction().logDelete(fullyQualifiedName, index, deletedRow);
                }
            }
            executor.getStorageEngine().getTableStorage(fullyQualifiedName).deleteAll(indicesToDelete);
            for (int deleteOrdinal = 0; deleteOrdinal < indicesToDelete.size(); deleteOrdinal++) {
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
            final Map<String, SourcePosition> assignmentOrigins, final SourcePosition whereOrigin, final String writtenName) {
        final TransactionWriteSet writeSet = executor.getTransactionManager().getCurrentTransaction().getWriteSet();
        final TableStorage tableStorage = executor.getStorageEngine().getTableStorage(fullyQualifiedName);

        // This transaction's effective view of committed base rows (pending updates applied, pending deletes
        // removed), tracking each row's stable id so the change can be recorded by id.
        // Index iteration under the engine lock — the scan()/getRowIds() copies were pure waste
        // when the effective lists are built row by row anyway.
        final int baseCount = tableStorage.getRowCount();
        final List<Row> effective = new ArrayList<>(baseCount);
        final List<Long> effectiveIds = new ArrayList<>(baseCount);
        for (int i = 0; i < baseCount; i++) {
            final long id = tableStorage.getRowId(i);
            if (writeSet.isDeleted(fullyQualifiedName, id)) {
                continue;
            }
            final Row pending = writeSet.pendingUpdate(fullyQualifiedName, id);
            effective.add(pending != null ? pending : tableStorage.getRow(i));
            effectiveIds.add(id);
        }

        int rowsUpdated = 0;

        List<Row> matching = effective;
        if (whereExpr != null) {
            final SourcePosition displaced = ExpressionSource.beginNested(whereOrigin);
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
            // Each SET column's index is a per-STATEMENT fact — resolve once, not per row.
            final Map<String, Integer> setIndexes = new HashMap<>();
            for (final String setColumn : assignments.keySet()) {
                setIndexes.put(setColumn, executor.getColumnIndex(table, setColumn));
            }
            final Map<Row, Integer> effectivePositions = firstPositionsOf(effective);
            for (final Row row : matching) {
                final Integer idx = effectivePositions.get(row);
                if (idx != null) {
                    writeSet.recordUpdate(fullyQualifiedName, effectiveIds.get(idx.intValue()),
                        buildUpdatedRow(row, table, assignments, assignmentOrigins, setIndexes, writtenName, targetAlias));
                    rowsUpdated++;
                }
            }
            final Map<Row, Integer> pendingPositions = firstPositionsOf(pendingInserts);
            for (final Row row : matchingPending) {
                final Integer idx = pendingPositions.get(row);
                if (idx != null) {
                    writeSet.setPendingInsert(fullyQualifiedName, idx.intValue(),
                        buildUpdatedRow(row, table, assignments, assignmentOrigins, setIndexes, writtenName, targetAlias));
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
        final TableStorage tableStorage = executor.getStorageEngine().getTableStorage(fullyQualifiedName);

        // Index iteration under the engine lock — the scan()/getRowIds() copies were pure waste
        // when the effective lists are built row by row anyway.
        final int baseCount = tableStorage.getRowCount();
        final List<Row> effective = new ArrayList<>(baseCount);
        final List<Long> effectiveIds = new ArrayList<>(baseCount);
        for (int i = 0; i < baseCount; i++) {
            final long id = tableStorage.getRowId(i);
            if (writeSet.isDeleted(fullyQualifiedName, id)) {
                continue;
            }
            final Row pending = writeSet.pendingUpdate(fullyQualifiedName, id);
            effective.add(pending != null ? pending : tableStorage.getRow(i));
            effectiveIds.add(id);
        }

        int rowsDeleted = 0;

        List<Row> matching = effective;
        if (whereExpr != null) {
            final SourcePosition displaced = ExpressionSource.beginNested(whereOrigin);
            try {
                matching = cteResults != null
                    ? executor.filterRowsWithCTEs(effective, table, targetAlias, whereExpr, cteResults)
                    : executor.filterRows(effective, table, targetAlias, whereExpr);
            } finally {
                ExpressionSource.end(displaced);
            }
        }
        final Map<Row, Integer> effectivePositions = firstPositionsOf(effective);
        for (final Row row : matching) {
            final Integer idx = effectivePositions.get(row);
            if (idx != null) {
                writeSet.recordDelete(fullyQualifiedName, effectiveIds.get(idx.intValue()));
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
        final Map<Row, Integer> pendingPositions = firstPositionsOf(pendingInserts);
        for (final Row row : matchingPending) {
            final Integer idx = pendingPositions.get(row);
            if (idx != null) {
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

    /** First-occurrence position of every row VALUE in {@code rows} — one pass replacing the
     *  per-matched-row indexOf equals-scan, reproducing its first-equal-wins answer exactly. */
    private static Map<Row, Integer> firstPositionsOf(final List<Row> rows) {
        final Map<Row, Integer> positions = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if (!positions.containsKey(rows.get(i))) {
                positions.put(rows.get(i), Integer.valueOf(i));
            }
        }
        return positions;
    }

    /** Build a copy of {@code source} with the UPDATE assignments applied (never mutates the base row). */
    private Row buildUpdatedRow(final Row source, final Table table, final Map<String, String> assignments,
                                final Map<String, SourcePosition> origins,
                                final Map<String, Integer> setIndexes, final String writtenName,
                                final String alias) {
        final Row newRow = source.copy();
        for (final Map.Entry<String, String> entry : assignments.entrySet()) {
            final int colIndex = setIndexes.get(entry.getKey()).intValue();
            // Each value is evaluated under ITS OWN origin, so an unknown name inside one SET value
            // reports that value's place rather than the statement's or the previous assignment's.
            final SourcePosition displaced =
                ExpressionSource.beginNested(origins == null ? null : origins.get(entry.getKey()));
            final Object newValue;
            try {
                // The bare DML DEFAULT, as in the other assignment path — see there for the rule.
                newValue = ExpressionEvaluator.parse(entry.getValue())
                        instanceof DefaultMarkerExpression
                    ? executor.declaredDefaultFor(table, colIndex, table.getName())
                    : executor.evaluateUpdateValue(entry.getValue(), newRow, table, alias);
            } catch (final RuntimeException failed) {
                throw DmlWriteTarget.isRowTimeFailure(failed)
                    ? DmlWriteTarget.failedOnColumn(writtenName, entry.getKey(), failed) : failed;
            } finally {
                ExpressionSource.end(displaced);
            }
            newRow.setValue(colIndex, newValue);
        }
        executor.enforceColumnConstraintsForDml(table, newRow, false, writtenName);
        return newRow;
    }

    // ── UPDATE … FROM / DELETE … USING ──────────────────────────────────────────────────────────────────
    // Snowflake's join-update / join-delete: the target is joined with one or more source tables on the
    // WHERE predicate. Built over the same deferred-apply write set (by stable row id) as the plain forms,
    // and over the alias-aware multi-table expression evaluator the SELECT join path uses, so qualified
    // references (target.col / source.col) resolve. When a target row joins more than one source row the
    // first match wins (Snowflake leaves multi-match updates non-deterministic by default).

    private int executeUpdateFromSources(final Table target, final String targetFqn, final String targetAlias,
            final Map<String, String> assignments, final Map<String, SourcePosition> assignmentOrigins,
            final String whereExpr,
            final List<FrostlakeParser.TableReferenceContext> sourceRefs,
            final List<FrostlakeParser.JoinClauseContext> joinClauses, final boolean outerJoin,
            final Map<String, ResultSet> cteResults, final String writtenName) {
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
        final Expression wherePred = whereExpr != null
            ? ev.withNarrowingCastEqualitiesAnswered(ExpressionEvaluator.parse(whereExpr)) : null;

        rejectMistypedJoinedAssignments(target, assignments, assignmentOrigins, ev);
        final Map<Integer, Expression> setByColumn = new LinkedHashMap<>();
        for (final Map.Entry<String, String> e : assignments.entrySet()) {
            setByColumn.put(executor.getColumnIndex(target, e.getKey()), ExpressionEvaluator.parse(e.getValue()));
        }

        final TransactionWriteSet writeSet = executor.isDeferredApply()
            ? executor.getTransactionManager().getCurrentTransaction().getWriteSet() : null;
        final TableStorage tableStorage = executor.getStorageEngine().getTableStorage(targetFqn);
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
            final Row updatedRow = joinUpdatedRow(targetRow, target, sourceCombos, ev, wherePred, setByColumn, outerJoin, sourceWidth, writtenName);
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
                    wherePred, setByColumn, outerJoin, sourceWidth, writtenName);
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
        final Expression wherePred = whereExpr != null
            ? ev.withNarrowingCastEqualitiesAnswered(ExpressionEvaluator.parse(whereExpr)) : null;

        final TransactionWriteSet writeSet = executor.isDeferredApply()
            ? executor.getTransactionManager().getCurrentTransaction().getWriteSet() : null;
        final TableStorage tableStorage = executor.getStorageEngine().getTableStorage(targetFqn);
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
            // Log every row first, then delete ALL in one compaction pass, then track.
            for (final int idx : immediateDeletes) {
                if (executor.getTransactionManager().hasActiveTransaction()) {
                    executor.getTransactionManager().getCurrentTransaction().logDelete(targetFqn, idx, baseRows.get(idx));
                }
            }
            tableStorage.deleteAll(immediateDeletes);
            for (final int idx : immediateDeletes) {
                if (executor.getStreamManager() != null) {
                    executor.getStreamManager().trackDelete(targetFqn, baseRows.get(idx));
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
     * An UPDATE…FROM's SET values judged before any target row is written, with the sources in scope: a
     * name no relation resolves is refused first, then each value is type-matched against its column in the
     * table's column order, as a single-table UPDATE's is. {@code SET n = s.b} over a BOOLEAN source column
     * is refused even when no row joins. A DEFAULT, or a value the static channel cannot type, is left to
     * the row.
     */
    private void rejectMistypedJoinedAssignments(final Table target, final Map<String, String> assignments,
                                                 final Map<String, SourcePosition> assignmentOrigins,
                                                 final ExpressionEvaluator ev) {
        final Map<String, Expression> parsedByColumn = new HashMap<>();
        for (final Map.Entry<String, String> assignment : assignments.entrySet()) {
            final SourcePosition displaced = ExpressionSource.beginNested(
                assignmentOrigins == null ? null : assignmentOrigins.get(assignment.getKey()));
            try {
                final Expression parsed = ExpressionEvaluator.parse(assignment.getValue());
                parsedByColumn.put(assignment.getKey().toUpperCase(), parsed);
                ev.validateColumnScope(parsed);
            } catch (final RuntimeException unjudged) {
                if (SqlCompilationError.isCompilationError(unjudged.getMessage())) {
                    throw unjudged;
                }
            } finally {
                ExpressionSource.end(displaced);
            }
        }
        for (final TableColumn column : target.getColumns()) {
            final Expression parsed = parsedByColumn.get(column.getName().toUpperCase());
            if (parsed == null || parsed instanceof DefaultMarkerExpression) {
                continue;
            }
            final DataType sourceType;
            try {
                sourceType = ev.inferStaticType(parsed);
            } catch (final RuntimeException untyped) {
                continue;
            }
            ColumnTypeFamilies.rejectMismatch(column, sourceType);
        }
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
            final boolean outerJoin, final int sourceWidth, final String writtenName) {
        for (final List<Object> combo : sourceCombos) {
            final List<Object> values = new ArrayList<>(targetRow.getValues());
            values.addAll(combo);
            final Row combinedRow = Row.of(values);
            if (wherePred == null || isTrueResult(ev.evaluate(wherePred, combinedRow))) {
                return applySet(targetRow, target, combinedRow, ev, setByColumn, writtenName);
            }
        }
        if (outerJoin) {
            final List<Object> values = new ArrayList<>(targetRow.getValues());
            for (int i = 0; i < sourceWidth; i++) {
                values.add(null);
            }
            return applySet(targetRow, target, Row.of(values), ev, setByColumn, writtenName);
        }
        return null;
    }

    /** Apply the SET assignments (evaluated over the combined target+source row) to a copy of the target. */
    private Row applySet(final Row targetRow, final Table target, final Row combinedRow,
            final ExpressionEvaluator ev, final Map<Integer, Expression> setByColumn, final String writtenName) {
        final Row newRow = targetRow.copy();
        for (final Map.Entry<Integer, Expression> s : setByColumn.entrySet()) {
            // The bare DML DEFAULT writes the column's declared default, as the single-table path does.
            newRow.setValue(s.getKey(), s.getValue() instanceof DefaultMarkerExpression
                ? executor.declaredDefaultFor(target, s.getKey().intValue(), target.getName())
                : ev.evaluate(s.getValue(), combinedRow));
        }
        // UPDATE … FROM: a SET value that cannot be computed stays bare (live); the WRITE is enveloped.
        executor.enforceColumnConstraintsForDml(target, newRow, false, writtenName);
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
     * Compile an UPDATE's or DELETE's own names once, before any row is read, in live's order: every
     * relation the statement reads, then each SET target (a repeated one is a duplicate), then the
     * column references of the SET values and the WHERE, then their function names. Over an empty
     * table this refuses what a full one would; a fault that shows only in a value stays a row-time
     * one (live-verified). A subquery's own names compile when it runs, and with FROM or USING sources
     * the references are left to the row-time path.
     */
    private void compileNames(final ParserRuleContext statement, final Table table, final String alias,
                              final List<FrostlakeParser.AssignmentContext> assignments,
                              final FrostlakeParser.WhereClauseContext where, final boolean joinedSources) {
        executor.requireRelations(statement);
        // The target and every FROM or USING source register a name each (see FromSourceNames), and one
        // registered twice is refused: UPDATE t SET a = 1 FROM t is "duplicate alias 'T'" (live-verified).
        final FromSourceNames sourceNames = new FromSourceNames(executor);
        sourceNames.register(alias != null ? alias : table.getName());
        sourceNames.registerSources(statement);
        sourceNames.rejectDuplicate();
        final Set<String> targets = new HashSet<>();
        for (final FrostlakeParser.AssignmentContext assign : assignments) {
            final String colName = ParseTreeText.namePartText(assign.namePart());
            requireColumn(table, colName, assign.namePart());
            if (!targets.add(colName.toUpperCase())) {
                throw new RuntimeException(SqlCompilationError.of(
                    "duplicate column name '" + SqlIdentifiers.spellCanonical(colName) + "'"));
            }
        }
        if (joinedSources) {
            return;
        }
        final List<ParserRuleContext> expressions = new ArrayList<>();
        for (final FrostlakeParser.AssignmentContext assign : assignments) {
            expressions.add(assign.expression());
        }
        if (where != null) {
            expressions.add(where.booleanExpr());
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(table, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        // The target in scope under the name the statement gives it, so a qualified reference
        // (T.col, or x.col under an alias) is judged as well as a bare one.
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put((alias != null ? alias : table.getName()).toUpperCase(), table);
        final List<Table> allTables = new ArrayList<>();
        allTables.add(table);
        scope.setMultiTableContext(aliasToTable, allTables);
        // Live orders these by KIND: every invalid identifier ahead of every unknown function name.
        for (int phase = 0; phase < 2; phase++) {
            for (final ParserRuleContext expression : expressions) {
                final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
                    expression.getStart().getLine(), expression.getStart().getCharPositionInLine()));
                try {
                    final Expression parsed = ExpressionEvaluator.parse(executor.getOriginalText(expression));
                    if (phase == 0) {
                        scope.validateColumnScope(parsed);
                    } else {
                        scope.validateFunctionNames(parsed);
                    }
                } catch (final RuntimeException unjudged) {
                    if (SqlCompilationError.isCompilationError(unjudged.getMessage())) {
                        throw unjudged;
                    }
                } finally {
                    ExpressionSource.end(displaced);
                }
            }
        }
        executor.rejectAggregatesInWhere(where, table);
        // The SET values are type-matched against their columns as INSERT's are, before any row is read, in
        // the table's column order: live reports the first column the table declares, whatever order SET
        // names them in. A DEFAULT, or a value the static channel cannot type, is left to the evaluation.
        final Map<String, FrostlakeParser.AssignmentContext> assigned = new HashMap<>();
        for (final FrostlakeParser.AssignmentContext assign : assignments) {
            assigned.put(ParseTreeText.namePartText(assign.namePart()).toUpperCase(), assign);
        }
        for (final TableColumn column : table.getColumns()) {
            final FrostlakeParser.AssignmentContext assign = assigned.get(column.getName().toUpperCase());
            if (assign == null) {
                continue;
            }
            final DataType sourceType;
            try {
                final Expression parsed = ExpressionEvaluator.parse(executor.getOriginalText(assign.expression()));
                if (parsed instanceof DefaultMarkerExpression) {
                    continue;
                }
                sourceType = scope.inferStaticType(parsed);
            } catch (final RuntimeException untyped) {
                continue;
            }
            ColumnTypeFamilies.rejectMismatch(column, sourceType);
        }
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
