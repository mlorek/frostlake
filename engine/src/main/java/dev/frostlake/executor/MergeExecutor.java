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
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
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

    Object executeMergeFromContext(final FrostlakeParser.MergeStatementContext ctx) {
        try {
            // Deferred-apply: ensure a transaction exists to buffer MERGE's writes into (autocommit then
            // applies it at statement end). The immediate path is unaffected.
            if (executor.isDeferredApply() && !executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }
            // Handle WITH clause CTEs if present
            final Map<String, ResultSet> cteResults = ctx.withClause() != null
                ? executor.executeCTEs(ctx.withClause(), null) : null;

            // Get target table
            String targetTableName = executor.getQualifiedName(ctx.qualifiedName());
            Table targetTable = executor.getCatalog().resolveTable(targetTableName);
            int mergeInserted = 0;
            int mergeUpdated = 0;

            // Get optional target and source aliases
            String targetAlias = null;
            String sourceAlias = null;
            if (ctx.identifier() != null && !ctx.identifier().isEmpty()) {
                targetAlias = executor.getIdentifier(ctx.identifier(0));
                if (ctx.identifier().size() > 1) {
                    sourceAlias = executor.getIdentifier(ctx.identifier(1));
                }
            }

            // Get source data and source table structure
            List<Row> sourceRows = new ArrayList<>();
            Table sourceTable = null;
            FrostlakeParser.MergeSourceContext mergeSource = ctx.mergeSource();

            if (mergeSource instanceof FrostlakeParser.MergeSourceValuesContext) {
                // USING (VALUES (...))
                FrostlakeParser.MergeSourceValuesContext valuesCtx = (FrostlakeParser.MergeSourceValuesContext) mergeSource;
                for (final FrostlakeParser.ValueTupleContext tuple : valuesCtx.valueTupleList().valueTuple()) {
                    List<Object> values = new ArrayList<>();
                    for (final FrostlakeParser.ExpressionContext expr : tuple.valueList().expression()) {
                        String exprText = executor.getOriginalText(expr);
                        Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                        Row dummyRow = new Row(new ArrayList<>());
                        ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                        Object value = evaluator.evaluate(exprText, dummyRow);
                        values.add(value);
                    }
                    sourceRows.add(new Row(values));
                }
                // For VALUES, source has same structure as target
                sourceTable = targetTable;
            } else if (mergeSource instanceof FrostlakeParser.MergeSourceTableContext) {
                // USING table_name — or a STREAM, which is consumed on commit like INSERT ... SELECT FROM stream.
                FrostlakeParser.MergeSourceTableContext tableCtx = (FrostlakeParser.MergeSourceTableContext) mergeSource;
                String sourceTableName = executor.getQualifiedName(tableCtx.qualifiedName());
                TableData streamSource = executor.resolveStreamTableData(sourceTableName, sourceAlias);
                if (streamSource != null) {
                    sourceRows = streamSource.rows;
                    sourceTable = streamSource.table;
                } else {
                    String fullyQualifiedSourceName = executor.getFullyQualifiedTableName(sourceTableName);
                    sourceRows = executor.getStorageEngine().getTableStorage(fullyQualifiedSourceName).scan();
                    sourceTable = executor.getCatalog().resolveTable(sourceTableName);
                }
            } else if (mergeSource instanceof FrostlakeParser.MergeSourceSubqueryContext) {
                // USING (SELECT ...) — pass CTEs so subquery can reference them
                FrostlakeParser.MergeSourceSubqueryContext subqueryCtx = (FrostlakeParser.MergeSourceSubqueryContext) mergeSource;
                ResultSet subqueryResult = cteResults != null
                    ? executor.executeSelectFromContextWithCTEs(subqueryCtx.selectStatement(), null, cteResults)
                    : executor.executeSelectFromContext(subqueryCtx.selectStatement());
                sourceRows = subqueryResult.getRows();

                // Create a table structure from ResultSet columns
                List<TableColumn> sourceColumns = new ArrayList<>();
                for (final ResultSetColumn rsCol : subqueryResult.getColumns()) {
                    TableColumn col = new TableColumn(rsCol.getName(), rsCol.getDataType(), true, null, false, false, false);
                    sourceColumns.add(col);
                }
                sourceTable = new Table("SOURCE", sourceColumns, false);
            }

            // Apply source column aliases if specified: USING (...) AS s (col1, col2, col3)
            if (ctx.identifierList() != null && sourceTable != null) {
                List<String> aliases = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifierList().identifier()) {
                    aliases.add(executor.getIdentifier(idCtx).toUpperCase());
                }
                sourceTable = executor.applyColumnAliases(sourceTable, aliases);
            }

            // Get ON condition (preserve whitespace for proper AND/OR parsing)
            String onCondition = executor.getOriginalText(ctx.booleanExpr());

            // Get target table rows - use fully qualified name
            String fullyQualifiedTargetName = executor.getFullyQualifiedTableName(targetTableName);
            List<Row> targetRows = executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName).scan();
            // Deferred mode: stable ids aligned with targetRows so MERGE can record changes by id (not
            // position), and the write set to record into. Both null on the immediate path.
            List<Long> targetRowIds = executor.isDeferredApply()
                ? executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName).getRowIds() : null;
            TransactionWriteSet mergeWriteSet = executor.isDeferredApply()
                ? executor.getTransactionManager().getCurrentTransaction().getWriteSet() : null;

            // Track which source rows matched
            Set<Integer> matchedSourceIndices = new HashSet<>();

            // Collect merge clauses (can have multiple WHEN MATCHED clauses with different conditions)
            List<FrostlakeParser.MergeClauseContext> matchedClauses = new ArrayList<>();
            List<FrostlakeParser.MergeClauseContext> notMatchedClauses = new ArrayList<>();

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

            // First pass: Process matched rows
            // Track rows to delete (can't delete while iterating)
            List<Integer> rowsToDelete = new ArrayList<>();

            for (int targetIdx = 0; targetIdx < targetRows.size(); targetIdx++) {
                Row targetRow = targetRows.get(targetIdx);

                // Check if this target row matches any source row
                for (int sourceIdx = 0; sourceIdx < sourceRows.size(); sourceIdx++) {
                    Row sourceRow = sourceRows.get(sourceIdx);

                    if (evaluateMergeCondition(onCondition, targetRow, sourceRow, targetTable, sourceTable, targetAlias, sourceAlias)) {
                        matchedSourceIndices.add(sourceIdx);

                        // Try each WHEN MATCHED clause until one matches
                        boolean clauseExecuted = false;
                        for (final FrostlakeParser.MergeClauseContext matchedClause : matchedClauses) {
                            // Check optional AND condition
                            boolean conditionMatches = true;
                            if (matchedClause.booleanExpr() != null) {
                                String additionalCondition = executor.getOriginalText(matchedClause.booleanExpr());
                                conditionMatches = evaluateMergeCondition(additionalCondition, targetRow, sourceRow, targetTable, sourceTable, targetAlias, sourceAlias);
                            }

                            if (conditionMatches) {
                                // Execute UPDATE
                                if (matchedClause.UPDATE() != null) {
                                    for (final FrostlakeParser.AssignmentContext assign : matchedClause.assignmentList().assignment()) {
                                        // Handle qualified identifiers (table.column) or simple identifiers
                                        List<FrostlakeParser.IdentifierContext> identifiers = assign.identifier();
                                        String colName = executor.getIdentifier(identifiers.get(identifiers.size() - 1));
                                        String valueExpr = executor.getOriginalText(assign.expression());

                                        int colIndex = targetTable.getColumnIndex(colName);
                                        // Evaluate using merged table context (same approach as evaluateMergeCondition)
                                        Object newValue = evaluateMergeValue(valueExpr, targetRow, sourceRow,
                                            targetTable, sourceTable, targetAlias, sourceAlias);

                                        List<Object> newValues = new ArrayList<>(targetRow.getValues());
                                        newValues.set(colIndex, newValue);
                                        Row updatedRow = new Row(newValues);
                                        executor.enforceColumnConstraints(targetTable, updatedRow);

                                        if (executor.isDeferredApply()) {
                                            mergeWriteSet.recordUpdate(fullyQualifiedTargetName, targetRowIds.get(targetIdx), updatedRow);
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
                int rowIdx = rowsToDelete.get(i);
                Row deletedRow = targetRows.get(rowIdx);
                if (executor.isDeferredApply()) {
                    mergeWriteSet.recordDelete(fullyQualifiedTargetName, targetRowIds.get(rowIdx));
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
                                values.add(evaluateMergeValue(executor.getOriginalText(expr), emptyTargetRow, sourceRow,
                                    targetTable, sourceTable, targetAlias, sourceAlias));
                            }
                        } else {
                            values = new ArrayList<>(sourceRow.getValues());
                        }

                        // Value-by-column-name (explicit list, else positional), then build the row applying
                        // DEFAULT / AUTOINCREMENT to omitted columns via the shared insertColumnValue.
                        final List<String> columnNames = new ArrayList<>();
                        if (notMatchedClause.columnListOptional() != null) {
                            for (final FrostlakeParser.IdentifierContext id : notMatchedClause.columnListOptional().identifierList().identifier()) {
                                columnNames.add(executor.getIdentifier(id).toUpperCase());
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
                            orderedValues.add(executor.insertColumnValue(col, fullyQualifiedTargetName,
                                valueMap.get(col.getName().toUpperCase())));
                        }

                        final Row newRow = new Row(orderedValues);
                        executor.enforceColumnConstraints(targetTable, newRow);
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
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // Include first relevant stack frame for easier diagnosis
            StackTraceElement[] st = e.getStackTrace();
            String frame = st != null && st.length > 0 ? " at " + st[0] : "";
            logger.error("MERGE failed", e);
            throw new RuntimeException("Failed to execute MERGE: " + msg + frame, e);
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

    private boolean evaluateMergeCondition(final String condition, final Row targetRow, final Row sourceRow,
                                          final Table targetTable, final Table sourceTable,
                                          final String targetAlias, final String sourceAlias) {
        if (condition == null) return false;

        // Build a merged table and row combining source and target columns with alias prefixes.
        // Source columns are exposed as alias.col and tablename.col; same for target.
        // This allows the ExpressionEvaluator to resolve s.COL and t.COL correctly.
        List<TableColumn> mergedCols = new ArrayList<>();
        List<Object> mergedVals = new ArrayList<>();

        // Add source columns — prefixed with source alias/name only.
        // Unqualified column references resolve to target (added last, so they take precedence
        // via case-insensitive scan which hits target's bare name first).
        String sAlias = sourceAlias != null ? sourceAlias.toUpperCase()
            : (sourceTable != null ? sourceTable.getName().toUpperCase() : "S");
        if (sourceTable != null) {
            for (int i = 0; i < sourceTable.getColumns().size(); i++) {
                TableColumn col = sourceTable.getColumns().get(i);
                // s.COL
                mergedCols.add(new TableColumn(sAlias + "." + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                mergedVals.add(i < sourceRow.getValues().size() ? sourceRow.getValue(i) : null);
                // If source alias differs from table name, add table-name prefix too
                String srcTableName = sourceTable.getName().toUpperCase();
                if (!srcTableName.equals(sAlias)) {
                    mergedCols.add(new TableColumn(srcTableName + "." + col.getName().toUpperCase(),
                        col.getDataType(), true, null, false, false, false));
                    mergedVals.add(i < sourceRow.getValues().size() ? sourceRow.getValue(i) : null);
                }
            }
        }

        // Add target columns — prefixed with target alias/name, AND as bare names.
        // Bare names resolve to target so unqualified references (e.g. "ON id = 1") match target.
        String tAlias = targetAlias != null ? targetAlias.toUpperCase() : targetTable.getName().toUpperCase();
        String tTableName = targetTable.getName().toUpperCase();
        for (int i = 0; i < targetTable.getColumns().size(); i++) {
            TableColumn col = targetTable.getColumns().get(i);
            Object val = i < targetRow.getValues().size() ? targetRow.getValue(i) : null;
            // t.COL
            mergedCols.add(new TableColumn(tAlias + "." + col.getName().toUpperCase(),
                col.getDataType(), true, null, false, false, false));
            mergedVals.add(val);
            // table_name.COL (if alias differs)
            if (!tTableName.equals(tAlias)) {
                mergedCols.add(new TableColumn(tTableName + "." + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                mergedVals.add(val);
            }
            // bare COL — for unqualified references
            mergedCols.add(new TableColumn(col.getName().toUpperCase(),
                col.getDataType(), true, null, false, false, false));
            mergedVals.add(val);
        }

        Table mergedTable = new Table("__MERGE__", mergedCols, false);
        Row mergedRow = new Row(mergedVals);

        try {
            ExpressionEvaluator evaluator = new ExpressionEvaluator(mergedTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            Object result = evaluator.evaluate(condition, mergedRow);
            if (result instanceof Boolean) return (Boolean) result;
            if (result == null) return false;
            String s = result.toString();
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
        // Build same merged table as evaluateMergeCondition
        List<TableColumn> mergedCols = new ArrayList<>();
        List<Object> mergedVals = new ArrayList<>();

        String sAlias = sourceAlias != null ? sourceAlias.toUpperCase()
            : (sourceTable != null ? sourceTable.getName().toUpperCase() : "S");
        if (sourceTable != null) {
            for (int i = 0; i < sourceTable.getColumns().size(); i++) {
                TableColumn col = sourceTable.getColumns().get(i);
                Object val = i < sourceRow.getValues().size() ? sourceRow.getValue(i) : null;
                mergedCols.add(new TableColumn(sAlias + "." + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                mergedVals.add(val);
                String srcName = sourceTable.getName().toUpperCase();
                if (!srcName.equals(sAlias)) {
                    mergedCols.add(new TableColumn(srcName + "." + col.getName().toUpperCase(),
                        col.getDataType(), true, null, false, false, false));
                    mergedVals.add(val);
                }
                // bare source column (lower priority than target bare)
                mergedCols.add(new TableColumn("__S__" + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                mergedVals.add(val);
            }
        }
        String tAlias = targetAlias != null ? targetAlias.toUpperCase() : targetTable.getName().toUpperCase();
        String tName = targetTable.getName().toUpperCase();
        for (int i = 0; i < targetTable.getColumns().size(); i++) {
            TableColumn col = targetTable.getColumns().get(i);
            Object val = i < targetRow.getValues().size() ? targetRow.getValue(i) : null;
            mergedCols.add(new TableColumn(tAlias + "." + col.getName().toUpperCase(),
                col.getDataType(), true, null, false, false, false));
            mergedVals.add(val);
            if (!tName.equals(tAlias)) {
                mergedCols.add(new TableColumn(tName + "." + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                mergedVals.add(val);
            }
            mergedCols.add(new TableColumn(col.getName().toUpperCase(),
                col.getDataType(), true, null, false, false, false));
            mergedVals.add(val);
        }

        Table mergedTable = new Table("__MERGE__", mergedCols, false);
        Row mergedRow = new Row(mergedVals);
        try {
            ExpressionEvaluator evaluator = new ExpressionEvaluator(mergedTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            return evaluator.evaluate(expr, mergedRow);
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
