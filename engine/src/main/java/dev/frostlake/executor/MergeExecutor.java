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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.DefaultMarkerExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.NumericType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MERGE write-path query stage extracted from {@link QueryExecutor}: the full
 * {@code MERGE INTO target USING source ON ... WHEN [NOT] MATCHED ...} statement — source resolution
 * (VALUES / table / STREAM / subquery, with optional column aliases), ON-condition matching, the
 * WHEN MATCHED UPDATE/DELETE and WHEN NOT MATCHED INSERT clauses, and the buffered (deferred-apply)
 * vs. immediate write with stream tracking — plus the merged-context expression evaluation helpers and
 * the MERGE per-action count result. Shared services (constraint enforcement, per-column value
 * resolution, CTE execution, stream/alias resolution, name/text helpers) stay on the owning executor
 * and are reached through {@code executor}; mutable per-query flags (deferred-apply, the nullable
 * late-wired stream manager) are read live so semantics stay byte-for-byte identical to the
 * pre-extraction code.
 */
final class MergeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MergeExecutor.class);

    private final QueryExecutor executor;

    MergeExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    // Per-statement, per-thread cache of the (invariant) MERGE evaluation shapes — at most a few
    // entries: the condition shape and the value shapes. Thread-local because this executor is a
    // per-engine singleton; cleared at every MERGE entry so no rows/tables outlive their statement.
    private final ThreadLocal<Map<String, MergeRowShape>> shapeCache =
        new ThreadLocal<Map<String, MergeRowShape>>() {
            @Override
            protected Map<String, MergeRowShape> initialValue() {
                return new HashMap<String, MergeRowShape>();
            }
        };

    private MergeRowShape shapeFor(final Table targetTable, final Table sourceTable,
                                   final String targetAlias, final String sourceAlias,
                                   final boolean valueMode, final boolean unqualifiedFromSource) {
        final String key = System.identityHashCode(targetTable) + "|" + System.identityHashCode(sourceTable)
            + "|" + targetAlias + "|" + sourceAlias + "|" + valueMode + "|" + unqualifiedFromSource;
        final Map<String, MergeRowShape> cache = shapeCache.get();
        MergeRowShape shape = cache.get(key);
        if (shape == null) {
            shape = MergeRowShape.build(targetTable, sourceTable, targetAlias, sourceAlias,
                valueMode, unqualifiedFromSource,
                executor.getFunctionRegistry(), executor.getCatalog(), executor);
            cache.put(key, shape);
        }
        return shape;
    }

    Object executeMergeFromContext(final FrostlakeParser.MergeStatementContext ctx) {
        shapeCache.get().clear();
        try {
            // Deferred-apply: ensure a transaction exists to buffer MERGE's writes into (autocommit then
            // applies it at statement end). The immediate path is unaffected.
            if (executor.isDeferredApply() && !executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }
            // WITH-prefixed DML is not Snowflake syntax (live-verified).
            final Map<String, ResultSet> cteResults = null;

            // Get target table
            final String targetTableName = executor.getQualifiedName(ctx.qualifiedName());
            final Table targetTable = executor.getCatalog().resolveTable(targetTableName);
            int mergeInserted = 0;
            int mergeUpdated = 0;

            // Get optional target and source aliases. Both are direct identifier children of the
            // merge rule and either may be absent, so index alone cannot tell them apart: an
            // identifier before the USING token is the target's alias, after it the source's.
            String targetAlias = null;
            String sourceAlias = null;
            final int usingTokenIndex = ctx.USING().getSymbol().getTokenIndex();
            for (final FrostlakeParser.IdentifierContext aliasCtx : ctx.identifier()) {
                if (aliasCtx.getStart().getTokenIndex() < usingTokenIndex) {
                    targetAlias = executor.getIdentifier(aliasCtx);
                } else {
                    sourceAlias = executor.getIdentifier(aliasCtx);
                }
            }

            // Get source data and source table structure
            List<Row> sourceRows = new ArrayList<>();
            Table sourceTable = null;
            final FrostlakeParser.MergeSourceContext mergeSource = ctx.mergeSource();

            if (mergeSource instanceof FrostlakeParser.MergeSourceValuesContext) {
                // USING (VALUES (...))
                final FrostlakeParser.MergeSourceValuesContext valuesCtx = (FrostlakeParser.MergeSourceValuesContext) mergeSource;
                for (final FrostlakeParser.ValueTupleContext tuple : valuesCtx.valueTupleList().valueTuple()) {
                    final List<Object> values = new ArrayList<>();
                    for (final FrostlakeParser.ExpressionContext expr : tuple.valueList().expression()) {
                        final String exprText = executor.getOriginalText(expr);
                        final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                        final Row dummyRow = new Row(new ArrayList<>());
                        final ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                        final Object value = evaluator.evaluate(exprText, dummyRow);
                        values.add(value);
                    }
                    sourceRows.add(new Row(values));
                }
                // For VALUES, source has same structure as target
                sourceTable = targetTable;
            } else if (mergeSource instanceof FrostlakeParser.MergeSourceTableContext) {
                // USING table_name — or a STREAM, which is consumed on commit like INSERT ... SELECT FROM stream.
                final FrostlakeParser.MergeSourceTableContext tableCtx = (FrostlakeParser.MergeSourceTableContext) mergeSource;
                final String sourceTableName = executor.getQualifiedName(tableCtx.qualifiedName());
                final TableData streamSource = executor.resolveStreamTableData(sourceTableName, sourceAlias);
                if (streamSource != null) {
                    sourceRows = streamSource.rows;
                    sourceTable = streamSource.table;
                } else {
                    final String fullyQualifiedSourceName = executor.getFullyQualifiedTableName(sourceTableName);
                    // Overlay-aware: a stage table populated by THIS transaction must be visible as the
                    // merge source (a raw scan saw only the committed base — empty for a fresh stage).
                    sourceRows = executor.readTableRowsForTransaction(fullyQualifiedSourceName);
                    sourceTable = executor.getCatalog().resolveTable(sourceTableName);
                }
            } else if (mergeSource instanceof FrostlakeParser.MergeSourceSubqueryContext) {
                // USING (SELECT ...) — pass CTEs so subquery can reference them
                final FrostlakeParser.MergeSourceSubqueryContext subqueryCtx = (FrostlakeParser.MergeSourceSubqueryContext) mergeSource;
                final ResultSet subqueryResult = cteResults != null
                    ? executor.executeSelectFromContextWithCTEs(subqueryCtx.selectStatement(), null, cteResults)
                    : executor.executeSelectFromContext(subqueryCtx.selectStatement());
                sourceRows = subqueryResult.getRows();

                // Create a table structure from ResultSet columns
                final List<TableColumn> sourceColumns = new ArrayList<>();
                for (final ResultSetColumn rsCol : subqueryResult.getColumns()) {
                    final TableColumn col = new TableColumn(rsCol.getName(), rsCol.getDataType(), true, null, false, false, false);
                    sourceColumns.add(col);
                }
                sourceTable = new Table("SOURCE", sourceColumns, false);
            }

            // Apply source column aliases if specified: USING (...) AS s (col1, col2, col3)
            if (ctx.identifierList() != null && sourceTable != null) {
                final List<String> aliases = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifierList().identifier()) {
                    aliases.add(executor.getIdentifier(idCtx).toUpperCase());
                }
                sourceTable = executor.applyColumnAliases(sourceTable, aliases);
            }

            // Get ON condition (preserve whitespace for proper AND/OR parsing)
            final String onCondition = executor.getOriginalText(ctx.booleanExpr());

            // Get target table rows - use fully qualified name
            final String fullyQualifiedTargetName = executor.getFullyQualifiedTableName(targetTableName);

            // The PARTITIONS lock registers per STATEMENT KIND, not per matched row — a MERGE
            // locks its target table however its branches fire (live-verified).
            final var rewriteTxn = executor.getTransactionManager().getCurrentTransaction();
            if (rewriteTxn != null) {
                rewriteTxn.recordTableTouch(fullyQualifiedTargetName);
            }

            final TableStorage targetStorage =
                executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName);
            List<Row> targetRows = targetStorage.scan();
            final TransactionWriteSet mergeWriteSet = executor.isDeferredApply()
                ? executor.getTransactionManager().getCurrentTransaction().getWriteSet() : null;
            // Deferred mode: the MERGE must see its TARGET through the transaction overlay — pending
            // updates applied, pending deletes removed, and this transaction's own pending INSERTS
            // matchable — otherwise a row inserted earlier in the same explicit transaction silently
            // never matches (its counts/updates are lost). Base rows keep their stable id (changes are
            // recorded by id); a pending insert has no id yet, so it carries its index in the write
            // set's insert list instead and is updated/removed in place there. Both lists stay null on
            // the immediate path.
            List<Long> targetRowIds = null;          // aligned with targetRows; null entry = pending insert
            List<Integer> targetPendingIdx = null;   // aligned with targetRows; -1 = base row
            if (executor.isDeferredApply()) {
                // Index iteration under the engine lock — the scan copy above plus a rowIds copy
                // were built only to be re-walked here.
                final int baseCount = targetStorage.getRowCount();
                targetRows = new ArrayList<>(baseCount);
                targetRowIds = new ArrayList<>(baseCount);
                targetPendingIdx = new ArrayList<>(baseCount);
                for (int i = 0; i < baseCount; i++) {
                    final long id = targetStorage.getRowId(i);
                    if (mergeWriteSet.isDeleted(fullyQualifiedTargetName, id)) {
                        continue;
                    }
                    final Row pending = mergeWriteSet.pendingUpdate(fullyQualifiedTargetName, id);
                    targetRows.add(pending != null ? pending : targetStorage.getRow(i));
                    targetRowIds.add(id);
                    targetPendingIdx.add(-1);
                }
                final List<Row> pendingInsertRows = mergeWriteSet.pendingInserts(fullyQualifiedTargetName);
                for (int i = 0; i < pendingInsertRows.size(); i++) {
                    targetRows.add(pendingInsertRows.get(i));
                    targetRowIds.add(null);
                    targetPendingIdx.add(i);
                }
            }

            // Track which source rows matched
            final Set<Integer> matchedSourceIndices = new HashSet<>();

            // Collect merge clauses (can have multiple WHEN MATCHED clauses with different conditions)
            final List<FrostlakeParser.MergeClauseContext> matchedClauses = new ArrayList<>();
            final List<FrostlakeParser.MergeClauseContext> notMatchedClauses = new ArrayList<>();

            for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
                if (clause.MATCHED() != null && clause.NOT() == null) {
                    matchedClauses.add(clause);
                } else if (clause.NOT() != null && clause.MATCHED() != null) {
                    notMatchedClauses.add(clause);
                }
            }

            // A source row that joins to ANY target is "matched", so WHEN NOT MATCHED must skip it. Determine
            // this fully up front: the per-target update loop below stops at each target's FIRST matching
            // source, so relying on it to populate the matched set would leave a second source that matches
            // the same target unmarked — and it would then be wrongly INSERTed as a duplicate.
            for (int sourceIdx = 0; sourceIdx < sourceRows.size(); sourceIdx++) {
                final Row sourceRow = sourceRows.get(sourceIdx);
                for (int targetIdx = 0; targetIdx < targetRows.size(); targetIdx++) {
                    if (evaluateMergeCondition(onCondition, targetRows.get(targetIdx), sourceRow,
                            targetTable, sourceTable, targetAlias, sourceAlias)) {
                        matchedSourceIndices.add(sourceIdx);
                        break;
                    }
                }
            }

            // Snowflake refuses a merge-UPDATE whose target row joins MORE THAN ONE source row: the
            // update would be non-deterministic. Live-verified on a real account: a target
            // (1,'x') joined by two sources fails "Duplicate row detected during DML action Row Values:
            // [1, \"x\"]", while the same duplicate join with only WHEN MATCHED THEN DELETE, or with only
            // WHEN NOT MATCHED THEN INSERT, succeeds — so the rule is UPDATE-specific. A WHEN MATCHED AND
            // <cond> that narrows the duplicates back to one is fine, so the count is taken over the source
            // rows whose clause actually resolves to an UPDATE. SHOW PARAMETERS reports
            // ERROR_ON_NONDETERMINISTIC_MERGE = true by default; setting it FALSE picks one source
            // arbitrarily instead.
            if (errorOnNondeterministicMerge() && !matchedClauses.isEmpty()) {
                for (int targetIdx = 0; targetIdx < targetRows.size(); targetIdx++) {
                    final Row targetRow = targetRows.get(targetIdx);
                    int updatingSources = 0;
                    for (int sourceIdx = 0; sourceIdx < sourceRows.size(); sourceIdx++) {
                        final Row sourceRow = sourceRows.get(sourceIdx);
                        if (!evaluateMergeCondition(onCondition, targetRow, sourceRow, targetTable,
                                sourceTable, targetAlias, sourceAlias)) {
                            continue;
                        }
                        if (firstMatchedClauseIsUpdate(matchedClauses, targetRow, sourceRow, targetTable,
                                sourceTable, targetAlias, sourceAlias)) {
                            updatingSources++;
                        }
                    }
                    if (updatingSources > 1) {
                        throw new RuntimeException("Duplicate row detected during DML action Row Values: "
                            + renderRowValues(targetRow));
                    }
                }
            }

            // First pass: Process matched rows
            // Track rows to delete (can't delete while iterating)
            final List<Integer> rowsToDelete = new ArrayList<>();

            for (int targetIdx = 0; targetIdx < targetRows.size(); targetIdx++) {
                Row targetRow = targetRows.get(targetIdx);

                // Check if this target row matches any source row
                for (int sourceIdx = 0; sourceIdx < sourceRows.size(); sourceIdx++) {
                    final Row sourceRow = sourceRows.get(sourceIdx);

                    if (evaluateMergeCondition(onCondition, targetRow, sourceRow, targetTable, sourceTable, targetAlias, sourceAlias)) {
                        matchedSourceIndices.add(sourceIdx);

                        // Try each WHEN MATCHED clause until one matches
                        boolean clauseExecuted = false;
                        for (final FrostlakeParser.MergeClauseContext matchedClause : matchedClauses) {
                            // Check optional AND condition
                            boolean conditionMatches = true;
                            if (matchedClause.booleanExpr() != null) {
                                final String additionalCondition = executor.getOriginalText(matchedClause.booleanExpr());
                                conditionMatches = evaluateMergeCondition(additionalCondition, targetRow, sourceRow, targetTable, sourceTable, targetAlias, sourceAlias);
                            }

                            if (conditionMatches) {
                                // Execute UPDATE
                                if (matchedClause.UPDATE() != null) {
                                    for (final FrostlakeParser.AssignmentContext assign : matchedClause.assignmentList().assignment()) {
                                        // Handle qualified identifiers (table.column) or simple identifiers
                                        final String colName = ParseTreeText.namePartText(assign.namePart());
                                        final String valueExpr = executor.getOriginalText(assign.expression());

                                        final int colIndex = targetTable.getColumnIndex(colName);
                                        // Evaluate using merged table context (same approach as evaluateMergeCondition)
                                        final Object newValue =
                                            ExpressionEvaluator.parse(valueExpr)
                                                    instanceof DefaultMarkerExpression
                                            ? executor.declaredDefaultFor(targetTable, colIndex,
                                                fullyQualifiedTargetName)
                                            : evaluateMergeValue(valueExpr, targetRow, sourceRow,
                                                targetTable, sourceTable, targetAlias, sourceAlias);

                                        final List<Object> newValues = new ArrayList<>(targetRow.getValues());
                                        newValues.set(colIndex, newValue);
                                        final Row updatedRow = new Row(newValues);
                                        executor.enforceColumnConstraintsForDml(targetTable, updatedRow);

                                        if (executor.isDeferredApply()) {
                                            final Long targetRowId = targetRowIds.get(targetIdx);
                                            if (targetRowId != null) {
                                                mergeWriteSet.recordUpdate(fullyQualifiedTargetName, targetRowId, updatedRow);
                                            } else {
                                                // Matched one of this transaction's own pending inserts.
                                                mergeWriteSet.setPendingInsert(fullyQualifiedTargetName, targetPendingIdx.get(targetIdx), updatedRow);
                                            }
                                        } else {
                                            executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName).update(targetIdx, updatedRow);
                                        }
                                        targetRow = updatedRow;
                                    }
                                    mergeUpdated++;
                                    clauseExecuted = true;
                                    break;
                                }
                                // Execute DELETE
                                else if (matchedClause.DELETE() != null) {
                                    rowsToDelete.add(targetIdx);
                                    clauseExecuted = true;
                                    break;
                                }
                            }
                        }

                        break; // Move to next target row after first match
                    }
                }
            }

            // Delete marked rows (in reverse order to maintain indices)
            for (int i = rowsToDelete.size() - 1; i >= 0; i--) {
                final int rowIdx = rowsToDelete.get(i);
                final Row deletedRow = targetRows.get(rowIdx);
                if (executor.isDeferredApply()) {
                    final Long targetRowId = targetRowIds.get(rowIdx);
                    if (targetRowId != null) {
                        mergeWriteSet.recordDelete(fullyQualifiedTargetName, targetRowId);
                    } else {
                        // Deleting one of this transaction's own pending inserts: drop it from the write
                        // set (reverse iteration keeps the remaining pending indices valid).
                        mergeWriteSet.removePendingInsert(fullyQualifiedTargetName, targetPendingIdx.get(rowIdx));
                    }
                } else {
                    executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName).delete(rowIdx);

                    // Track stream changes
                    if (executor.getStreamManager() != null) {
                        executor.getStreamManager().trackDelete(fullyQualifiedTargetName, deletedRow);
                    }
                }
            }

            // Process WHEN NOT MATCHED clauses: a source that matched no target uses the FIRST NOT MATCHED
            // clause whose condition holds (multiple conditional INSERTs are allowed — earlier ones must not
            // be dropped). Omitted columns get their DEFAULT / AUTOINCREMENT, exactly like a plain INSERT.
            for (int sourceIdx = 0; sourceIdx < sourceRows.size(); sourceIdx++) {
                if (matchedSourceIndices.contains(sourceIdx)) {
                    continue;
                }
                final Row sourceRow = sourceRows.get(sourceIdx);
                for (final FrostlakeParser.MergeClauseContext notMatchedClause : notMatchedClauses) {
                    if (notMatchedClause.booleanExpr() != null) {
                        final String additionalCondition = executor.getOriginalText(notMatchedClause.booleanExpr());
                        final Row dummyTargetRow = new Row(new ArrayList<>());
                        if (!evaluateMergeCondition(additionalCondition, dummyTargetRow, sourceRow,
                                targetTable, sourceTable, targetAlias, sourceAlias)) {
                            continue;   // condition failed — try the next NOT MATCHED clause
                        }
                    }
                    // First matching NOT MATCHED clause for this source wins.
                    if (notMatchedClause.INSERT() != null) {
                        List<Object> values = new ArrayList<>();
                        if (notMatchedClause.valueTuple() != null) {
                            final Row emptyTargetRow = new Row(new ArrayList<>());
                            for (final FrostlakeParser.ExpressionContext expr : notMatchedClause.valueTuple().valueList().expression()) {
                                // A bare DEFAULT travels as the MARKER and is substituted per target
                                // column below, the same way INSERT … VALUES does it.
                                final String valueText = executor.getOriginalText(expr);
                                final Expression parsedValue = ExpressionEvaluator.parse(valueText);
                                values.add(parsedValue instanceof DefaultMarkerExpression ? parsedValue
                                    : evaluateMergeValue(valueText, emptyTargetRow, sourceRow,
                                        targetTable, sourceTable, targetAlias, sourceAlias, true));
                            }
                        } else {
                            values = new ArrayList<>(sourceRow.getValues());
                        }

                        // Value-by-column-name (explicit list, else positional), then build the row applying
                        // DEFAULT / AUTOINCREMENT to omitted columns via the shared insertColumnValue.
                        final List<String> columnNames = new ArrayList<>();
                        if (notMatchedClause.mergeInsertColumnList() != null) {
                            for (final FrostlakeParser.MergeInsertColumnContext mic : notMatchedClause.mergeInsertColumnList().mergeInsertColumn()) {
                                // An optional target-alias qualifier (t.col) may precede the column; the
                                // last identifier is the column name.
                                final List<FrostlakeParser.IdentifierContext> parts = mic.identifier();
                                columnNames.add(executor.getIdentifier(parts.get(parts.size() - 1)).toUpperCase());
                            }
                        } else {
                            for (final TableColumn col : targetTable.getColumns()) {
                                columnNames.add(col.getName().toUpperCase());
                            }
                        }
                        final Map<String, Object> valueMap = new HashMap<>();
                        for (int i = 0; i < columnNames.size() && i < values.size(); i++) {
                            valueMap.put(columnNames.get(i), values.get(i));
                        }
                        final List<Object> orderedValues = new ArrayList<>();
                        for (final TableColumn col : targetTable.getColumns()) {
                            final String key = col.getName().toUpperCase();
                            // A column covered by the INSERT clause keeps its explicit value — even NULL;
                            // DEFAULT / AUTOINCREMENT apply only to columns omitted from it (Snowflake).
                            if (valueMap.containsKey(key)
                                    && !(valueMap.get(key) instanceof DefaultMarkerExpression)) {
                                orderedValues.add(valueMap.get(key));
                            } else {
                                orderedValues.add(executor.insertColumnValue(col, fullyQualifiedTargetName, null));
                            }
                        }

                        final Row newRow = new Row(orderedValues);
                        executor.enforceColumnConstraintsForDml(targetTable, newRow);
                        if (executor.isDeferredApply()) {
                            mergeWriteSet.recordInsert(fullyQualifiedTargetName, newRow);
                        } else {
                            executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName).insert(newRow);
                            if (executor.getStreamManager() != null) {
                                executor.getStreamManager().trackInsert(fullyQualifiedTargetName, newRow);
                            }
                        }
                        mergeInserted++;
                    }
                    break;
                }
            }

            logger.info("Executed MERGE on table: {}", targetTableName);
            return mergeCountResult(mergeInserted, mergeUpdated, rowsToDelete.size());

        } catch (final Exception e) {
            // The frame that used to be appended to the message is diagnosis, not part of what Snowflake
            // reports, so it goes to the log and the failure propagates with its own message.
            logger.error("MERGE failed", e);
            throw StatementErrors.propagate(e);
        }
    }

    /** Snowflake-style MERGE result: per-action counts (the deleted column only when rows were deleted). */
    private ResultSet mergeCountResult(final long inserted, final long updated, final long deleted) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        final List<Object> values = new ArrayList<>();
        columns.add(new ResultSetColumn("number of rows inserted", NumericType.NUMBER));
        values.add(inserted);
        columns.add(new ResultSetColumn("number of rows updated", NumericType.NUMBER));
        values.add(updated);
        if (deleted > 0) {
            columns.add(new ResultSetColumn("number of rows deleted", NumericType.NUMBER));
            values.add(deleted);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows);
    }

    /**
     * ERROR_ON_NONDETERMINISTIC_MERGE, as reported by a live account's {@code SHOW PARAMETERS}: a
     * BOOLEAN whose default is TRUE. Setting it FALSE lets a duplicate join pick one source row.
     */
    private boolean errorOnNondeterministicMerge() {
        final SecurityManager securityManager = executor.getSecurityManager();
        final Object configured = securityManager == null ? null
            : securityManager.getSessionContext().getSessionParameter("ERROR_ON_NONDETERMINISTIC_MERGE");
        if (configured == null) {
            return true;
        }
        if (configured instanceof Boolean) {
            return ((Boolean) configured).booleanValue();
        }
        return !"FALSE".equalsIgnoreCase(String.valueOf(configured));
    }

    /**
     * Whether the FIRST WHEN MATCHED clause whose optional {@code AND} predicate holds for this
     * target/source pair is an UPDATE — the only matched action Snowflake calls non-deterministic.
     */
    private boolean firstMatchedClauseIsUpdate(final List<FrostlakeParser.MergeClauseContext> matchedClauses,
                                               final Row targetRow, final Row sourceRow,
                                               final Table targetTable, final Table sourceTable,
                                               final String targetAlias, final String sourceAlias) {
        for (final FrostlakeParser.MergeClauseContext clause : matchedClauses) {
            if (clause.booleanExpr() != null
                    && !evaluateMergeCondition(executor.getOriginalText(clause.booleanExpr()), targetRow,
                        sourceRow, targetTable, sourceTable, targetAlias, sourceAlias)) {
                continue;
            }
            return clause.UPDATE() != null;
        }
        return false;
    }

    /**
     * The offending target row as Snowflake prints it in the duplicate-row error: a bracketed list with
     * strings double-quoted and everything else in its plain text form.
     */
    private String renderRowValues(final Row row) {
        final StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < row.getValues().size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            final Object value = row.getValues().get(i);
            if (value == null) {
                text.append("NULL");
            } else if (value instanceof Number || value instanceof Boolean) {
                text.append(value);
            } else {
                text.append('"').append(value).append('"');
            }
        }
        return text.append(']').toString();
    }

    private boolean evaluateMergeCondition(final String condition, final Row targetRow, final Row sourceRow,
                                          final Table targetTable, final Table sourceTable,
                                          final String targetAlias, final String sourceAlias) {
        if (condition == null) return false;
        // The merged schema and its evaluator are per-STATEMENT facts (see MergeRowShape) — per
        // pair only the values list is refilled.
        try {
            final Object result = shapeFor(targetTable, sourceTable, targetAlias, sourceAlias, false, false)
                .evaluate(condition, targetRow, sourceRow);
            if (result instanceof Boolean) return (Boolean) result;
            if (result == null) return false;
            final String s = result.toString();
            return !"false".equalsIgnoreCase(s) && !"0".equals(s);
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * If {@code expr} is a qualified column reference ({@code alias.column}), its {@code [alias, column]}
     * parts read from the parsed column-reference node; null for a bare column, a literal, or a more
     * complex expression. Reads the qualifier from the AST rather than splitting the text on '.' — so a
     * decimal literal like {@code 1.5} is never mistaken for a qualified reference.
     */
    private static String[] qualifiedColumnParts(final String expr) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(expr);
            if (parsed instanceof ColumnReferenceExpression && ((ColumnReferenceExpression) parsed).isQualified()) {
                final ColumnReferenceExpression col = (ColumnReferenceExpression) parsed;
                return new String[] { col.getTableName(), col.getColumnName() };
            }
        } catch (final RuntimeException notASimpleColumnRef) {
            // fall through — treat as a non-qualified expression
        }
        return null;
    }

    private Object evaluateMergeSide(final String expr, final Row targetRow, final Row sourceRow,
                                      final Table targetTable, final Table sourceTable,
                                      final String targetAlias, final String sourceAlias,
                                      final boolean isLeftSide) {
        // A qualified column reference (alias.column) — resolve the alias/column via the AST.
        final String[] ref = qualifiedColumnParts(expr);
        if (ref != null) {
            final String prefix = ref[0];
            final String columnName = ref[1];

            // Check if prefix matches source alias or table
            if ((sourceAlias != null && prefix.equalsIgnoreCase(sourceAlias)) ||
                (sourceTable != null && prefix.equalsIgnoreCase(sourceTable.getName()))) {
                return evaluateExpressionSimple(columnName, sourceRow, sourceTable);
            }
            // Check if prefix matches target alias or table
            if ((targetAlias != null && prefix.equalsIgnoreCase(targetAlias)) ||
                prefix.equalsIgnoreCase(targetTable.getName())) {
                return evaluateExpressionSimple(columnName, targetRow, targetTable);
            }
            // Prefix didn't match - just use the column name
            return isLeftSide ?
                evaluateExpressionSimple(columnName, targetRow, targetTable) :
                evaluateExpressionSimple(columnName, sourceRow, sourceTable != null ? sourceTable : targetTable);
        }

        // No prefix - use default: left side = target, right side = source
        if (isLeftSide) {
            return evaluateExpressionSimple(expr, targetRow, targetTable);
        } else {
            return evaluateExpressionSimple(expr, sourceRow, sourceTable != null ? sourceTable : targetTable);
        }
    }

    /** Evaluate a MERGE UPDATE SET value expression using the merged source+target context. */
    private Object evaluateMergeValue(final String expr, final Row targetRow, final Row sourceRow,
                                      final Table targetTable, final Table sourceTable,
                                      final String targetAlias, final String sourceAlias) {
        return evaluateMergeValue(expr, targetRow, sourceRow, targetTable, sourceTable,
            targetAlias, sourceAlias, false);
    }

    private Object evaluateMergeValue(final String expr, final Row targetRow, final Row sourceRow,
                                      final Table targetTable, final Table sourceTable,
                                      final String targetAlias, final String sourceAlias,
                                      final boolean unqualifiedFromSource) {
        // Same per-statement shape reuse as the condition path; the WHEN-NOT-MATCHED variant gets
        // its own shape with the bare source columns re-exposed last.
        try {
            return shapeFor(targetTable, sourceTable, targetAlias, sourceAlias, true, unqualifiedFromSource)
                .evaluate(expr, targetRow, sourceRow);
        } catch (final Exception e) {
            // Fall back to legacy evaluator for complex expressions
            return evaluateMergeExpression(expr, targetRow, sourceRow, targetTable, sourceTable, targetAlias, sourceAlias);
        }
    }

    private Object evaluateMergeExpression(final String expr, final Row targetRow, final Row sourceRow,
                                           final Table targetTable, final Table sourceTable,
                                           final String targetAlias, final String sourceAlias) {
        String exprToEvaluate = expr;

        // A qualified column reference (alias.column) — resolve the alias/column via the AST.
        final String[] ref = qualifiedColumnParts(expr);
        if (ref != null) {
            final String prefix = ref[0];
            final String columnName = ref[1];

            // Check if prefix matches source alias
            if (sourceAlias != null && prefix.equalsIgnoreCase(sourceAlias)) {
                return evaluateExpressionSimple(columnName, sourceRow, sourceTable);
            }
            // Check if prefix matches target alias
            if (targetAlias != null && prefix.equalsIgnoreCase(targetAlias)) {
                return evaluateExpressionSimple(columnName, targetRow, targetTable);
            }
            // Check if prefix matches source table name
            if (sourceTable != null && prefix.equalsIgnoreCase(sourceTable.getName())) {
                return evaluateExpressionSimple(columnName, sourceRow, sourceTable);
            }
            // Check if prefix matches target table name
            if (prefix.equalsIgnoreCase(targetTable.getName())) {
                return evaluateExpressionSimple(columnName, targetRow, targetTable);
            }
            // If no match, strip the prefix and continue
            exprToEvaluate = columnName;
        }

        // No prefix - try to look up as column from source or target
        // For VALUES source without table structure, sourceTable == targetTable
        // In that case, column references are positional in source row
        if (sourceTable == targetTable) {
            // VALUES source - no table structure, use target table for lookup
            return evaluateExpressionSimple(exprToEvaluate, sourceRow, targetTable);
        } else {
            // Subquery or table source - try source first, then target
            try {
                return evaluateExpressionSimple(exprToEvaluate, sourceRow, sourceTable);
            } catch (final Exception e) {
                return evaluateExpressionSimple(exprToEvaluate, targetRow, targetTable);
            }
        }
    }

    private Object evaluateExpressionSimple(final String expr, final Row row, final Table table) {
        // Evaluate via the AST evaluator (literals, columns, and arbitrary expressions). If it
        // cannot be evaluated against this row/table, fall back to returning the text as-is —
        // preserving the legacy MERGE behaviour for unresolved tokens.
        try {
            return new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor).evaluate(expr, row);
        } catch (final RuntimeException e) {
            return expr;
        }
    }
}
