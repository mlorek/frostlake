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
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.operators.MemoizedRows;
import dev.frostlake.executor.operators.StageOperator;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.transaction.TransactionWriteSet;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StatementResultWidths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MERGE write-path query stage extracted from {@link QueryExecutor}: the full
 * {@code MERGE INTO target USING source ON ... WHEN [NOT] MATCHED ...} statement — source resolution
 * (VALUES / table / STREAM / subquery, with optional column aliases), ON-condition matching, the
 * WHEN MATCHED UPDATE/DELETE and WHEN NOT MATCHED INSERT clauses, and the buffered (deferred-apply)
 * vs. immediate write with stream tracking — plus the merged-context expression evaluation helpers and
 * the MERGE per-action count result. The statement is planned whole and run as one pipeline over the
 * target's rows ({@link SelectPlan}): the match, the one-update-per-row check, the WHEN MATCHED writes and
 * the WHEN NOT MATCHED inserts are its stages. Shared services (constraint enforcement, per-column value
 * resolution, CTE execution, stream/alias resolution, name/text helpers) stay on the owning executor
 * and are reached through {@code executor}; mutable per-query flags (deferred-apply, the nullable
 * late-wired stream manager) are read live so semantics stay byte-for-byte identical to the
 * pre-extraction code.
 */
final class MergeExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MergeExecutor.class);

    private final QueryExecutor executor;
    private final TargetWriteGuards guards;

    MergeExecutor(final QueryExecutor executor) {
        this.executor = executor;
        this.guards = new TargetWriteGuards(executor);
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

    /**
     * Whether a value a MERGE writes can be NULL, judged over the source relation as a select item is (see
     * {@link ResultNullability}): a number too wide for a NOT NULL column prints its type {@code {nullable}} when it
     * comes from a nullable source column, through an INSERT clause and an UPDATE alike (live-verified, as for INSERT;
     * see {@link QueryExecutor#enforceColumnConstraintsForDml}). A scalar subquery counts as never NULL, as UPDATE's.
     */
    private static boolean mergeValueNullable(final Expression value, final Table sourceTable,
                                              final String sourceAlias) {
        if (value instanceof DefaultMarkerExpression || value instanceof SubqueryExpression || sourceTable == null) {
            return false;
        }
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(sourceAlias != null ? sourceAlias : sourceTable.getName(), sourceTable);
        return new ResultNullability(sourceTable, aliases).of(value);
    }

    /** The target columns an INSERT clause writes from a value that can be NULL, one flag each; null when none. */
    private static boolean[] mergeNullableSources(final Table targetTable, final List<String> columnNames,
                                                  final List<Boolean> nullableValues) {
        boolean[] nullable = null;
        for (int i = 0; i < columnNames.size() && i < nullableValues.size(); i++) {
            if (nullableValues.get(i).booleanValue() && targetTable.hasColumn(columnNames.get(i))) {
                nullable = nullable != null ? nullable : new boolean[targetTable.getColumns().size()];
                nullable[targetTable.getColumnIndex(columnNames.get(i))] = true;
            }
        }
        return nullable;
    }

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

    /**
     * Execute MERGE from parsed context. The statement is planned whole — target and source resolved, names and
     * branch values judged — and then run as one pipeline over the target's rows: the match against the source,
     * the one-update-per-row check, the WHEN MATCHED updates and deletes, and the WHEN NOT MATCHED inserts.
     */
    Object executeMergeFromContext(final FrostlakeParser.MergeStatementContext ctx) {
        shapeCache.get().clear();
        try {
            // Deferred-apply: ensure a transaction exists to buffer MERGE's writes into (autocommit then
            // applies it at statement end). The immediate path is unaffected.
            if (executor.isDeferredApply() && !executor.getTransactionManager().hasActiveTransaction()
                    && !ExplainPlan.planning()) {
                executor.getTransactionManager().beginTransaction();
            }
            // WITH-prefixed DML is not Snowflake syntax (live-verified).
            final Map<String, ResultSet> cteResults = null;
            // Get target table
            final String targetTableName = executor.searchPathQualified(executor.getQualifiedName(ctx.qualifiedName()));
            // The target is looked up the way a FROM clause looks a table up: a missing one is a missing
            // Object, named as written (live-verified).
            DmlTarget.requireTable(executor, targetTableName, "MERGE");
            final Table targetTable = executor.getCatalog().resolveTableAsWritten(targetTableName, "Object");
            // An event table takes a MERGE that only deletes; one that inserts or updates writes rows into it.
            for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
                if (clause.DELETE() == null) {
                    DmlTarget.refuseEventTable(targetTable, "MERGE");
                }
            }
            // A SQL function the statement calls is expanded into it (see CalledRoutineSources).
            CalledRoutineSources.require(ctx, executor);
            // A MERGE reads its target, then needs what its clauses do: INSERT, UPDATE and DELETE are asked in
            // that order, whatever order the clauses stand in (live-verified).
            executor.requirePrivilege(Privilege.SELECT, "TABLE", targetTableName);
            for (final Privilege written : new Privilege[] {Privilege.INSERT, Privilege.UPDATE, Privilege.DELETE}) {
                for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
                    final boolean does = written == Privilege.INSERT ? clause.INSERT() != null
                        : written == Privilege.UPDATE ? clause.UPDATE() != null : clause.DELETE() != null;
                    if (does) {
                        executor.requirePrivilege(written, "TABLE", targetTableName);
                    }
                }
            }
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
            final String mergeTargetAlias = targetAlias;
            final String mergeSourceAlias = sourceAlias;
            // Get source data and source table structure
            List<Row> sourceRows = new ArrayList<>();
            Table sourceTable = null;
            String sourceDescription = "VALUES";
            final FrostlakeParser.MergeSourceContext mergeSource = ctx.mergeSource();
            if (mergeSource instanceof FrostlakeParser.MergeSourceValuesContext) {
                // USING (VALUES (...))
                final FrostlakeParser.MergeSourceValuesContext valuesCtx = (FrostlakeParser.MergeSourceValuesContext) mergeSource;
                for (final FrostlakeParser.ValueTupleContext tuple : valuesCtx.valueTupleList().valueTuple()) {
                    final List<Object> values = new ArrayList<>();
                    for (final FrostlakeParser.BooleanExprContext expr : tuple.valueList().booleanExpr()) {
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
                // USING table_name — or a VIEW, whose body is expanded as a FROM clause expands it, or a STREAM,
                // which is consumed on commit like INSERT ... SELECT FROM stream.
                final FrostlakeParser.MergeSourceTableContext tableCtx = (FrostlakeParser.MergeSourceTableContext) mergeSource;
                final String sourceTableName =
                    executor.searchPathQualified(executor.getQualifiedName(tableCtx.qualifiedName()));
                // A view's body is expanded first, so a relation it may not read is told ahead of the view's own
                // SELECT.
                final TableData viewSource = executor.resolveViewTableData(sourceTableName, sourceAlias);
                executor.requireSelect(sourceTableName);
                final TableData streamSource = viewSource != null ? viewSource
                    : executor.resolveStreamTableData(sourceTableName, sourceAlias);
                if (streamSource != null) {
                    sourceRows = streamSource.rows;
                    sourceTable = streamSource.table;
                    sourceDescription = streamSource.describe();
                } else {
                    // Looked up the way a FROM clause looks a table up, before any row is read, so a missing
                    // source is a missing Object rather than missing storage (live-verified).
                    sourceTable = executor.getCatalog().resolveTableAsWritten(sourceTableName, "Object");
                    final String fullyQualifiedSourceName = executor.getFullyQualifiedTableName(sourceTableName);
                    // Overlay-aware: a stage table populated by THIS transaction must be visible as the
                    // merge source (a raw scan saw only the committed base — empty for a fresh stage). Read
                    // when the match stage runs, as a query reads it: its row access policy keeps the rows the
                    // session may see and its masking policies mask them (live-verified).
                    sourceRows = new DeferredRows(new MemoizedRows(
                        executor.policedScan(sourceTable, fullyQualifiedSourceName)));
                    sourceDescription = "SCAN[" + sourceTable.getName() + "]";
                }
            } else if (mergeSource instanceof FrostlakeParser.MergeSourceSubqueryContext) {
                // USING (SELECT ...) — pass CTEs so subquery can reference them
                final FrostlakeParser.MergeSourceSubqueryContext subqueryCtx = (FrostlakeParser.MergeSourceSubqueryContext) mergeSource;
                // Planned here for its shape; its rows flow when the match stage runs.
                final PlannedQuery subquery = cteResults != null
                    ? executor.planSelectFromContext(subqueryCtx.selectStatement(), null, cteResults)
                    : executor.planSelectStatement(subqueryCtx.selectStatement());
                sourceRows = new DeferredRows(new MemoizedRows(QueryExecutor.plannedRows(subquery)));
                sourceDescription = "SUBQUERY{" + subquery.description + "}";
                // Create a table structure from the planned shape's columns
                final List<TableColumn> sourceColumns = new ArrayList<>();
                for (final ResultSetColumn rsCol : subquery.shape.getColumns()) {
                    // A column the subquery cannot make NULL is NOT NULL here, as a write from it prints it:
                    // USING (SELECT 12345 v) s writes s.v as {not null} (live-verified).
                    final TableColumn col = new TableColumn(rsCol.getName(), rsCol.getDataType(), rsCol.canBeNull(),
                        null, false, false, false);
                    // A branch value read through the source alias types like any other column, and so
                    // meets the compile-time match against the target's column — but only where the
                    // shape KNOWS the type. A projected bare NULL has none, and an untyped NULL is
                    // assignable to every column.
                    col.setStaticallyTyped(rsCol.getStaticType() != null);
                    sourceColumns.add(col);
                }
                sourceTable = new Table("SOURCE", sourceColumns, false);
            }
            // Apply source column aliases if specified: USING (...) AS s (col1, col2, col3)
            if (ctx.identifierList() != null && sourceTable != null) {
                final List<String> aliases = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifierList().identifier()) {
                    aliases.add(executor.getIdentifier(idCtx).toUpperCase(Locale.ROOT));
                }
                sourceTable = executor.applyColumnAliases(sourceTable, aliases);
            }
            final List<Row> mergeSourceRows = sourceRows;
            final Table mergeSourceTable = sourceTable;
            final String mergeSourceDescription = sourceDescription;
            // The target and the source each register a name (see FromSourceNames), and the same one twice
            // is refused: MERGE INTO t USING s t is "duplicate alias 'T'" (live-verified).
            final FromSourceNames sourceNames = new FromSourceNames(executor);
            sourceNames.register(targetAlias != null ? targetAlias
                : FromSourceNames.lastPart(ParseTreeText.qualifiedNameParts(ctx.qualifiedName())));
            final String sourceName = sourceAlias != null ? sourceAlias : FromSourceNames.mergeSourceName(mergeSource);
            sourceNames.register(sourceName);
            sourceNames.rejectDuplicate();
            // The statement compiles once, before any row is read (see MergeCompilation).
            final RuntimeException subqueryRefusal = new MergeCompilation(executor).compile(ctx, targetTable,
                targetAlias, sourceTable, sourceAlias, !(mergeSource instanceof FrostlakeParser.MergeSourceValuesContext));
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
            // Every value a branch writes is type-matched against its column before any row is read, as an
            // INSERT's is: WHEN MATCHED THEN UPDATE SET n = (1 = 1) is refused even when nothing matches.
            // A DECFLOAT value a branch writes into a VARIANT or a text column is held in the column's own form of it
            // (see DecfloatWrites); each such value is kept with its column's type.
            final Map<ParserRuleContext, DataType> decfloatValues = new HashMap<>();
            final Set<ParserRuleContext> jsonValues = rejectMistypedBranchValues(targetTable, targetAlias,
                sourceTable, sourceAlias, matchedClauses, notMatchedClauses, decfloatValues);
            // ... and judged by a projection policy as a projection is: UPDATE SET x = s.p and INSERT VALUES (s.p)
            // write the value into the target. The ON condition only uses the column.
            rejectRestrictedBranchValues(targetTable, targetAlias, sourceTable, sourceAlias, matchedClauses,
                notMatchedClauses);
            if (subqueryRefusal != null) {
                throw subqueryRefusal;
            }
            // An EXPLAIN compiles the statement and reads no row (see ExplainPlan).
            if (ExplainPlan.planning()) {
                return null;
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
            // A row a NOT MATCHED clause inserts is judged against the keys the table holds as the statement writes
            // it, the rows this transaction wrote included, as an INSERT's row is (see DeferredInsertGuard).
            final DeferredInsertGuard insertGuard = executor.isDeferredApply()
                ? new DeferredInsertGuard(targetTable, targetStorage, mergeWriteSet, fullyQualifiedTargetName,
                    executor.getStorageEngine().isEnforcePrimaryKey(),
                    executor.getStorageEngine().isEnforceUniqueKey())
                : null;
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
            final List<Long> mergeTargetRowIds = targetRowIds;
            final List<Integer> mergeTargetPendingIdx = targetPendingIdx;
            // The plan over the target's rows; the source is the relation every stage reads beside them.
            final SelectPlan plan = new SelectPlan(executor, executor.getFunctionRegistry());
            plan.source(targetRows, "TARGET[" + targetTableName + "]");
            guards.police(plan, targetTable);
            // A source row that joins to ANY target is "matched", so WHEN NOT MATCHED must skip it. Determine
            // this fully up front: the per-target update loop below stops at each target's FIRST matching
            // source, so relying on it to populate the matched set would leave a second source that matches
            // the same target unmarked — and it would then be wrongly INSERTed as a duplicate.
            plan.add(new StageOperator("MERGE MATCH[" + onCondition + "]{" + mergeSourceDescription + "}") {
                @Override
                protected List<Row> apply(final List<Row> targets) {
                    for (int sourceIdx = 0; sourceIdx < mergeSourceRows.size(); sourceIdx++) {
                        final Row sourceRow = mergeSourceRows.get(sourceIdx);
                        for (int targetIdx = 0; targetIdx < targets.size(); targetIdx++) {
                            if (evaluateMergeCondition(onCondition, targets.get(targetIdx), sourceRow,
                                    targetTable, mergeSourceTable, mergeTargetAlias, mergeSourceAlias)) {
                                matchedSourceIndices.add(sourceIdx);
                                break;
                            }
                        }
                    }
                    return targets;
                }
            });
            // Snowflake refuses a merge-UPDATE whose target row joins MORE THAN ONE source row: the
            // update would be non-deterministic. Live-verified on a real account: a target
            // (1,'x') joined by two sources fails with the duplicate-row refusal (see DuplicateRowRefusal),
            // while the same duplicate join with only WHEN MATCHED THEN DELETE, or with only
            // WHEN NOT MATCHED THEN INSERT, succeeds — so the rule is UPDATE-specific. A WHEN MATCHED AND
            // <cond> that narrows the duplicates back to one is fine, so the count is taken over the source
            // rows whose clause actually resolves to an UPDATE. SHOW PARAMETERS reports
            // ERROR_ON_NONDETERMINISTIC_MERGE = true by default; setting it FALSE picks one source
            // arbitrarily instead.
            if (errorOnNondeterministicMerge() && !matchedClauses.isEmpty()) {
                plan.add(new StageOperator("MERGE CHECK[one updating source per target row]") {
                    @Override
                    protected List<Row> apply(final List<Row> targets) {
                        for (int targetIdx = 0; targetIdx < targets.size(); targetIdx++) {
                            final Row targetRow = targets.get(targetIdx);
                            int updatingSources = 0;
                            for (int sourceIdx = 0; sourceIdx < mergeSourceRows.size(); sourceIdx++) {
                                final Row sourceRow = mergeSourceRows.get(sourceIdx);
                                if (!evaluateMergeCondition(onCondition, targetRow, sourceRow, targetTable,
                                        mergeSourceTable, mergeTargetAlias, mergeSourceAlias)) {
                                    continue;
                                }
                                if (firstMatchedClauseIsUpdate(matchedClauses, targetRow, sourceRow, targetTable,
                                        mergeSourceTable, mergeTargetAlias, mergeSourceAlias)) {
                                    updatingSources++;
                                }
                            }
                            if (updatingSources > 1) {
                                throw DuplicateRowRefusal.of(targetTable, targetRow);
                            }
                        }
                        return targets;
                    }
                });
            }
            // inserted, updated, deleted
            final long[] counts = new long[3];
            // Whether the statement has looked for another session's lock on its target: before its first write.
            final boolean[] lockChecked = new boolean[1];
            // Each matched target's write - its new image or its deletion - is settled before any row is written, so
            // a hybrid table's keys are judged whole (see TargetWriteGuards). The rows the stages read are the ones
            // the row access policy let the session see; each is found in the target by identity.
            final RowPositions targetPositions = new RowPositions(targetRows);
            final List<Row> scannedTargets = targetRows;
            plan.add(new StageOperator("WHEN MATCHED[" + matchedClauses.size() + " clauses]") {
                @Override
                protected List<Row> apply(final List<Row> targets) {
                    final List<Row> changed = new ArrayList<>();
                    final List<Row> images = new ArrayList<>();
                    final List<Integer> changedAt = new ArrayList<>();
                    final List<Row> deleted = new ArrayList<>();
                    final List<Integer> deletedAt = new ArrayList<>();
                    for (final Row targetRow : targets) {
                        // Check if this target row matches any source row
                        for (int sourceIdx = 0; sourceIdx < mergeSourceRows.size(); sourceIdx++) {
                            final Row sourceRow = mergeSourceRows.get(sourceIdx);
                            if (evaluateMergeCondition(onCondition, targetRow, sourceRow, targetTable, mergeSourceTable,
                                    mergeTargetAlias, mergeSourceAlias)) {
                                matchedSourceIndices.add(sourceIdx);
                                // Try each WHEN MATCHED clause until one matches
                                for (final FrostlakeParser.MergeClauseContext matchedClause : matchedClauses) {
                                    // Check optional AND condition
                                    boolean conditionMatches = true;
                                    if (matchedClause.booleanExpr() != null) {
                                        final String additionalCondition =
                                            executor.getOriginalText(matchedClause.booleanExpr());
                                        conditionMatches = evaluateMergeCondition(additionalCondition, targetRow,
                                            sourceRow, targetTable, mergeSourceTable, mergeTargetAlias, mergeSourceAlias);
                                    }
                                    if (conditionMatches) {
                                        if (matchedClause.UPDATE() != null) {
                                            // Every value reads the target row as it was, in the order written,
                                            // and the row is written once (live-verified).
                                            final List<Object> newValues = new ArrayList<>(targetRow.getValues());
                                            final List<Integer> writtenColumns = new ArrayList<>();
                                            final boolean[] nullableWritten =
                                                new boolean[targetTable.getColumns().size()];
                                            for (final FrostlakeParser.AssignmentContext assign
                                                    : matchedClause.assignmentList().assignment()) {
                                                // Handle qualified identifiers (table.column) or simple identifiers
                                                final String colName = ParseTreeText.namePartText(assign.namePart());
                                                final String valueExpr = executor.getOriginalText(assign.expression());
                                                final int colIndex = targetTable.getColumnIndex(colName);
                                                // Evaluate using merged table context (as evaluateMergeCondition does)
                                                final Object newValue =
                                                    ExpressionEvaluator.parse(valueExpr)
                                                            instanceof DefaultMarkerExpression
                                                    ? executor.declaredDefaultFor(targetTable, colIndex,
                                                        fullyQualifiedTargetName)
                                                    : evaluateMergeValue(valueExpr, targetRow, sourceRow, targetTable,
                                                        mergeSourceTable, mergeTargetAlias, mergeSourceAlias);
                                                newValues.set(colIndex, jsonValues.contains(assign.expression())
                                                    ? JsonTextWrites.storedOnColumn(newValue,
                                                        executor.getFunctionRegistry(), targetTableName,
                                                        targetTable.getColumns().get(colIndex).getName())
                                                    : decfloatValues.containsKey(assign.expression())
                                                    ? DecfloatWrites.stored(newValue,
                                                        decfloatValues.get(assign.expression()))
                                                    : newValue);
                                                writtenColumns.add(Integer.valueOf(colIndex));
                                                nullableWritten[colIndex] = mergeValueNullable(
                                                    ExpressionEvaluator.parse(valueExpr), mergeSourceTable,
                                                    mergeSourceAlias);
                                            }
                                            final Row updatedRow = new Row(newValues);
                                            executor.enforceColumnConstraintsForDml(targetTable, updatedRow, false,
                                                targetTableName, null, JsonTextWrites.keptAsWritten(targetTable,
                                                    jsonColumns(targetTable, matchedClause.assignmentList()
                                                        .assignment(), jsonValues), writtenColumns), nullableWritten);
                                            changed.add(targetRow);
                                            images.add(updatedRow);
                                            changedAt.add(targetPositions.take(targetRow));
                                            counts[1]++;
                                            break;
                                        } else if (matchedClause.DELETE() != null) {
                                            deleted.add(targetRow);
                                            deletedAt.add(targetPositions.take(targetRow));
                                            break;
                                        }
                                    }
                                }
                                break; // Move to next target row after first match
                            }
                        }
                    }
                    if (!changed.isEmpty() || !deleted.isEmpty()) {
                        requireUnlockedOnce(lockChecked, fullyQualifiedTargetName, targetTable);
                    }
                    if (targetTable.isHybrid()) {
                        // Each key is judged over the table as the statement leaves it: the images written and the
                        // deleted rows gone.
                        guards.requireUpdate(targetTable, fullyQualifiedTargetName, without(scannedTargets, deleted),
                            changed, images);
                        guards.requireDelete(targetTable, fullyQualifiedTargetName,
                            withImages(scannedTargets, changed, images), deleted);
                    }
                    for (int i = 0; i < changed.size(); i++) {
                        final int position = changedAt.get(i).intValue();
                        final Row updatedRow = images.get(i);
                        if (executor.isDeferredApply()) {
                            final Long targetRowId = mergeTargetRowIds.get(position);
                            if (targetRowId != null) {
                                mergeWriteSet.recordUpdate(fullyQualifiedTargetName, targetRowId, updatedRow);
                            } else {
                                // Matched one of this transaction's own pending inserts.
                                mergeWriteSet.setPendingInsert(fullyQualifiedTargetName,
                                    mergeTargetPendingIdx.get(position), updatedRow);
                            }
                        } else {
                            executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName)
                                .update(position, updatedRow);
                        }
                    }
                    // Deleted from the last position back, so the positions before each stay where they were.
                    final List<Integer> order = new ArrayList<>();
                    for (int i = 0; i < deleted.size(); i++) {
                        order.add(Integer.valueOf(i));
                    }
                    Collections.sort(order, new Comparator<Integer>() {
                        @Override
                        public int compare(final Integer left, final Integer right) {
                            return deletedAt.get(right.intValue()).compareTo(deletedAt.get(left.intValue()));
                        }
                    });
                    for (final Integer i : order) {
                        final int position = deletedAt.get(i.intValue()).intValue();
                        final Row deletedRow = deleted.get(i.intValue());
                        if (executor.isDeferredApply()) {
                            final Long targetRowId = mergeTargetRowIds.get(position);
                            if (targetRowId != null) {
                                mergeWriteSet.recordDelete(fullyQualifiedTargetName, targetRowId);
                            } else {
                                // Deleting one of this transaction's own pending inserts: drop it from the write
                                // set (the last position first keeps the remaining pending indices valid).
                                mergeWriteSet.removePendingInsert(fullyQualifiedTargetName,
                                    mergeTargetPendingIdx.get(position));
                            }
                        } else {
                            final TableStorage written =
                                executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName);
                            final long deletedId = written.getRowId(position);
                            written.delete(position);
                            // Track stream changes
                            if (executor.getStreamManager() != null) {
                                executor.getStreamManager().trackDelete(fullyQualifiedTargetName, deletedRow, deletedId);
                            }
                        }
                    }
                    counts[2] = deleted.size();
                    return targets;
                }
            });
            // Process WHEN NOT MATCHED clauses: a source that matched no target uses the FIRST NOT MATCHED
            // clause whose condition holds (multiple conditional INSERTs are allowed — earlier ones must not
            // be dropped). Omitted columns get their DEFAULT / AUTOINCREMENT, exactly like a plain INSERT.
            plan.add(new StageOperator("WHEN NOT MATCHED[" + notMatchedClauses.size() + " clauses]") {
                @Override
                protected List<Row> apply(final List<Row> targets) {
                    for (int sourceIdx = 0; sourceIdx < mergeSourceRows.size(); sourceIdx++) {
                        if (matchedSourceIndices.contains(sourceIdx)) {
                            continue;
                        }
                        final Row sourceRow = mergeSourceRows.get(sourceIdx);
                        for (final FrostlakeParser.MergeClauseContext notMatchedClause : notMatchedClauses) {
                            if (notMatchedClause.booleanExpr() != null) {
                                final String additionalCondition = executor.getOriginalText(notMatchedClause.booleanExpr());
                                final Row dummyTargetRow = new Row(new ArrayList<>());
                                if (!evaluateMergeCondition(additionalCondition, dummyTargetRow, sourceRow,
                                        targetTable, mergeSourceTable, mergeTargetAlias, mergeSourceAlias)) {
                                    continue;   // condition failed — try the next NOT MATCHED clause
                                }
                            }
                            // First matching NOT MATCHED clause for this source wins.
                            if (notMatchedClause.INSERT() != null) {
                                List<Object> values = new ArrayList<>();
                                final Set<String> jsonInserted = new HashSet<>();
                                // Which listed values can be NULL, which a range refusal prints (see below).
                                final List<Boolean> nullableValues = new ArrayList<>();
                                if (notMatchedClause.valueTuple() != null) {
                                    final Row emptyTargetRow = new Row(new ArrayList<>());
                                    final List<FrostlakeParser.BooleanExprContext> valueExprs =
                                        notMatchedClause.valueTuple().valueList().booleanExpr();
                                    for (int v = 0; v < valueExprs.size(); v++) {
                                        // A bare DEFAULT travels as the MARKER and is substituted per target
                                        // column below, the same way INSERT … VALUES does it.
                                        final String valueText = executor.getOriginalText(valueExprs.get(v));
                                        final Expression parsedValue = ExpressionEvaluator.parse(valueText);
                                        nullableValues.add(mergeValueNullable(parsedValue, mergeSourceTable,
                                            mergeSourceAlias));
                                        try {
                                            final Object computed = parsedValue instanceof DefaultMarkerExpression
                                                ? parsedValue
                                                : evaluateMergeValue(valueText, emptyTargetRow, sourceRow,
                                                    targetTable, mergeSourceTable, mergeTargetAlias, mergeSourceAlias,
                                                    true);
                                            if (jsonValues.contains(valueExprs.get(v))) {
                                                jsonInserted.add(mergeInsertColumnName(notMatchedClause, targetTable,
                                                    v));
                                                values.add(JsonTextWrites.stored(computed,
                                                    executor.getFunctionRegistry()));
                                            } else if (decfloatValues.containsKey(valueExprs.get(v))) {
                                                values.add(DecfloatWrites.stored(computed,
                                                    decfloatValues.get(valueExprs.get(v))));
                                            } else {
                                                values.add(computed);
                                            }
                                        } catch (final RuntimeException failed) {
                                            // A VALUES item that cannot be computed is a DML failure on the
                                            // column it feeds, live-verified — the target as the MERGE wrote it.
                                            throw DmlWriteTarget.isRowTimeFailure(failed)
                                                ? DmlWriteTarget.failedOnColumn(targetTableName,
                                                    mergeInsertColumnName(notMatchedClause, targetTable, v).toUpperCase(Locale.ROOT),
                                                    failed)
                                                : failed;
                                        }
                                    }
                                } else {
                                    values = new ArrayList<>(sourceRow.getValues());
                                    // Writing reads every cell, so a fault a relation deferred raises here.
                                    for (final Object value : values) {
                                        DeferredFault.read(value);
                                    }
                                }
                                // Value-by-column-name (explicit list, else positional), then build the row applying
                                // DEFAULT / AUTOINCREMENT to omitted columns via the shared insertColumnValue.
                                // Keyed by each column's CANONICAL spelling, the listed name resolved through the
                                // target's own lookup: INSERT ("x", "X") fills two different columns.
                                final List<String> columnNames = new ArrayList<>();
                                if (notMatchedClause.mergeInsertColumnList() != null) {
                                    for (final FrostlakeParser.MergeInsertColumnContext mic : notMatchedClause.mergeInsertColumnList().mergeInsertColumn()) {
                                        // An optional target-alias qualifier (t.col) may precede the column; the
                                        // last identifier is the column name.
                                        final List<FrostlakeParser.IdentifierContext> parts = mic.identifier();
                                        final String listed = executor.getIdentifier(parts.get(parts.size() - 1));
                                        columnNames.add(targetTable.hasColumn(listed)
                                            ? targetTable.getColumn(listed).getName() : listed);
                                    }
                                } else {
                                    for (final TableColumn col : targetTable.getColumns()) {
                                        columnNames.add(col.getName());
                                    }
                                }
                                final Map<String, Object> valueMap = new HashMap<>();
                                for (int i = 0; i < columnNames.size() && i < values.size(); i++) {
                                    valueMap.put(columnNames.get(i), values.get(i));
                                }
                                final List<Object> orderedValues = new ArrayList<>();
                                for (final TableColumn col : targetTable.getColumns()) {
                                    final String key = col.getName();
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
                                boolean[] jsonRead = null;
                                for (int c = 0; c < targetTable.getColumns().size(); c++) {
                                    if (jsonInserted.contains(targetTable.getColumns().get(c).getName())) {
                                        jsonRead = jsonRead != null ? jsonRead
                                            : new boolean[targetTable.getColumns().size()];
                                        jsonRead[c] = true;
                                    }
                                }
                                executor.enforceColumnConstraintsForDml(targetTable, newRow, false, targetTableName,
                                    null, jsonRead, mergeNullableSources(targetTable, columnNames, nullableValues));
                                guards.refuseOrphanRow(targetTable, fullyQualifiedTargetName, newRow);
                                requireUnlockedOnce(lockChecked, fullyQualifiedTargetName, targetTable);
                                if (executor.isDeferredApply()) {
                                    insertGuard.validate(newRow);
                                    mergeWriteSet.recordInsert(fullyQualifiedTargetName, newRow);
                                } else {
                                    final TableStorage written =
                                        executor.getStorageEngine().getTableStorage(fullyQualifiedTargetName);
                                    written.insert(newRow);
                                    if (executor.getStreamManager() != null) {
                                        executor.getStreamManager().trackInsert(fullyQualifiedTargetName, newRow,
                                            written.getRowId(written.getRowCount() - 1));
                                    }
                                }
                                counts[0]++;
                            }
                            break;
                        }
                    }
                    return targets;
                }
            });
            executor.recordPlan(plan);
            plan.execute();
            logger.info("Executed MERGE on table: {}", targetTableName);
            // LAST_ALTERED moves for the statement, whether or not a branch fired.
            targetTable.markDmlRan(StatementClock.commitInstant());
            return mergeCountResult(ctx, counts[0], counts[1], counts[2]);
        } catch (final Exception e) {
            // The frame that used to be appended to the message is diagnosis, not part of what Snowflake
            // reports, so it goes to the log and the failure propagates with its own message.
            logger.error("MERGE failed", e);
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * The MERGE result: one count column for each KIND of action the statement WRITES, in the order inserted,
     * updated, deleted. The columns follow the WHEN clauses, not what happened: a MERGE whose only clause is a
     * WHEN MATCHED UPDATE answers {@code number of rows updated} = 0 when nothing matched, and never a column for
     * a kind of action it has no clause for.
     */
    private ResultSet mergeCountResult(final FrostlakeParser.MergeStatementContext ctx, final long inserted,
                                       final long updated, final long deleted) {
        boolean writesInsert = false;
        boolean writesUpdate = false;
        boolean writesDelete = false;
        for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
            writesInsert |= clause.INSERT() != null;
            writesUpdate |= clause.UPDATE() != null;
            writesDelete |= clause.DELETE() != null;
        }
        final List<ResultSetColumn> columns = new ArrayList<>();
        final List<Object> values = new ArrayList<>();
        if (writesInsert) {
            columns.add(new ResultSetColumn("number of rows inserted", StatementResultWidths.DML_COUNT));
            values.add(inserted);
        }
        if (writesUpdate) {
            columns.add(new ResultSetColumn("number of rows updated", StatementResultWidths.DML_COUNT));
            values.add(updated);
        }
        if (writesDelete) {
            columns.add(new ResultSetColumn("number of rows deleted", StatementResultWidths.DML_COUNT));
            values.add(deleted);
        }
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows).markUpdateCount(inserted + updated + deleted);
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
    /**
     * The compile-time type match of every value a MERGE branch writes (see {@link ColumnTypeFamilies}). An
     * UPDATE SET value is judged against its column in the table's column order, an INSERT value against
     * the column it feeds, each typed with the target and the source in scope. A DEFAULT, or a value the
     * static channel cannot type, is left to the row.
     */
    private Set<ParserRuleContext> rejectMistypedBranchValues(final Table targetTable, final String targetAlias,
            final Table sourceTable, final String sourceAlias,
            final List<FrostlakeParser.MergeClauseContext> matchedClauses,
            final List<FrostlakeParser.MergeClauseContext> notMatchedClauses,
            final Map<ParserRuleContext, DataType> decfloatValues) {
        final Set<ParserRuleContext> jsonValues = new HashSet<>();
        final Map<String, Table> aliasToTable = new HashMap<>();
        final List<Table> allTables = new ArrayList<>();
        aliasToTable.put((targetAlias != null ? targetAlias : targetTable.getName()).toUpperCase(Locale.ROOT), targetTable);
        allTables.add(targetTable);
        if (sourceTable != null) {
            // A table merged into itself is still read through its source alias: MERGE INTO t USING t s … SET a = s.b
            // types s.b as t's column.
            aliasToTable.put((sourceAlias != null ? sourceAlias : sourceTable.getName()).toUpperCase(Locale.ROOT), sourceTable);
            if (sourceTable != targetTable) {
                allTables.add(sourceTable);
            }
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(targetTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        scope.setMultiTableContext(aliasToTable, allTables);
        for (final FrostlakeParser.MergeClauseContext clause : matchedClauses) {
            if (clause.assignmentList() == null) {
                continue;
            }
            final Map<String, ParserRuleContext> assigned = new HashMap<>();
            for (final FrostlakeParser.AssignmentContext assign : clause.assignmentList().assignment()) {
                final String named = ParseTreeText.namePartText(assign.namePart());
                assigned.put(targetTable.hasColumn(named) ? targetTable.getColumn(named).getName() : named,
                    assign.expression());
            }
            for (final TableColumn column : targetTable.getColumns()) {
                final ParserRuleContext value = assigned.get(column.getName());
                if (value != null) {
                    rejectMistypedValue(scope, column, value, jsonValues, decfloatValues);
                }
            }
        }
        for (final FrostlakeParser.MergeClauseContext clause : notMatchedClauses) {
            if (clause.valueTuple() == null || clause.valueTuple().valueList() == null) {
                continue;
            }
            final List<FrostlakeParser.BooleanExprContext> values = clause.valueTuple().valueList().booleanExpr();
            for (int i = 0; i < values.size(); i++) {
                final TableColumn column = targetTable.getColumn(mergeInsertColumnName(clause, targetTable, i));
                if (column != null) {
                    rejectMistypedValue(scope, column, values.get(i), jsonValues, decfloatValues);
                }
            }
        }
        return jsonValues;
    }

    /**
     * Refuses a branch that writes values a projection policy restricts (see
     * {@code QueryExecutor#rejectRestrictedAssignments}), with the target and the source in scope.
     */
    private void rejectRestrictedBranchValues(final Table targetTable, final String targetAlias,
                                              final Table sourceTable, final String sourceAlias,
                                              final List<FrostlakeParser.MergeClauseContext> matchedClauses,
                                              final List<FrostlakeParser.MergeClauseContext> notMatchedClauses) {
        final Map<String, Table> aliasToTable = new HashMap<>();
        final List<Table> allTables = new ArrayList<>();
        aliasToTable.put((targetAlias != null ? targetAlias : targetTable.getName()).toUpperCase(Locale.ROOT), targetTable);
        allTables.add(targetTable);
        if (sourceTable != null) {
            aliasToTable.put((sourceAlias != null ? sourceAlias : sourceTable.getName()).toUpperCase(Locale.ROOT), sourceTable);
            if (sourceTable != targetTable) {
                allTables.add(sourceTable);
            }
        }
        final List<ParserRuleContext> values = new ArrayList<>();
        for (final FrostlakeParser.MergeClauseContext clause : matchedClauses) {
            if (clause.assignmentList() != null) {
                for (final FrostlakeParser.AssignmentContext assign : clause.assignmentList().assignment()) {
                    values.add(assign.expression());
                }
            }
        }
        for (final FrostlakeParser.MergeClauseContext clause : notMatchedClauses) {
            if (clause.valueTuple() != null && clause.valueTuple().valueList() != null) {
                values.addAll(clause.valueTuple().valueList().booleanExpr());
            }
        }
        executor.rejectRestrictedAssignments(values, targetTable, aliasToTable, allTables);
    }

    /**
     * The columns a WHEN MATCHED UPDATE writes a text read as JSON (see JsonTextWrites), one flag per column of the
     * target; null when it writes none.
     */
    private static boolean[] jsonColumns(final Table targetTable,
                                         final List<FrostlakeParser.AssignmentContext> assignments,
                                         final Set<ParserRuleContext> jsonValues) {
        boolean[] json = null;
        for (final FrostlakeParser.AssignmentContext assign : assignments) {
            if (jsonValues.contains(assign.expression())) {
                final int index = targetTable.getColumnIndex(ParseTreeText.namePartText(assign.namePart()));
                if (index >= 0) {
                    json = json != null ? json : new boolean[targetTable.getColumns().size()];
                    json[index] = true;
                }
            }
        }
        return json;
    }

    /**
     * One branch value against its column; see {@link #rejectMistypedBranchValues}. A text read as JSON when the row
     * is written (see JsonTextWrites) is taken into {@code jsonValues} instead, and a DECFLOAT its column holds in a
     * form of its own (see DecfloatWrites) into {@code decfloatValues}, with the column's type.
     */
    private void rejectMistypedValue(final ExpressionEvaluator scope, final TableColumn column,
                                     final ParserRuleContext value, final Set<ParserRuleContext> jsonValues,
                                     final Map<ParserRuleContext, DataType> decfloatValues) {
        final Expression parsed;
        final DataType sourceType;
        try {
            parsed = ExpressionEvaluator.parse(executor.getOriginalText(value));
            if (parsed instanceof DefaultMarkerExpression) {
                return;
            }
            sourceType = scope.inferStaticType(parsed);
        } catch (final RuntimeException untyped) {
            return;
        }
        if (JsonTextWrites.readAsJson(column, parsed, sourceType, executor.getFunctionRegistry())) {
            jsonValues.add(value);
            return;
        }
        ColumnTypeFamilies.rejectMismatch(column, sourceType);
        if (DecfloatWrites.reshapes(sourceType, column.getDataType())) {
            decfloatValues.put(value, column.getDataType());
        }
    }

    /** The target column the {@code index}-th VALUES item of a MERGE's INSERT feeds, by its canonical name. */
    private String mergeInsertColumnName(final FrostlakeParser.MergeClauseContext clause,
                                         final Table targetTable, final int index) {
        if (clause.mergeInsertColumnList() != null) {
            final List<FrostlakeParser.MergeInsertColumnContext> columns =
                clause.mergeInsertColumnList().mergeInsertColumn();
            if (index < columns.size()) {
                final List<FrostlakeParser.IdentifierContext> parts = columns.get(index).identifier();
                return executor.getIdentifier(parts.get(parts.size() - 1));
            }
        }
        return index < targetTable.getColumns().size()
            ? targetTable.getColumns().get(index).getName() : "?";
    }

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
        } catch (final RuntimeException failed) {
            // A value that REFUSED — a conditional's branch cast, a conversion — is the answer, live's
            // own sentence ("Failed to cast variant value 1 to DATE"); the fallback below is for a
            // reference the two-table shape could not resolve, and used to swallow the refusal too,
            // so the raw value reached the write and was refused there in other words.
            if (DmlWriteTarget.isRowTimeFailure(failed)) {
                throw failed;
            }
            return evaluateMergeExpression(expr, targetRow, sourceRow, targetTable, sourceTable, targetAlias, sourceAlias);
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

    /** A table's rows without those a statement deletes, by identity. */
    private static List<Row> without(final List<Row> rows, final List<Row> deleted) {
        final Set<Row> leaving = Collections.newSetFromMap(new IdentityHashMap<Row, Boolean>());
        leaving.addAll(deleted);
        final List<Row> kept = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            if (!leaving.contains(row)) {
                kept.add(row);
            }
        }
        return kept;
    }

    /** A table's rows with the images a statement writes standing where the rows it changes stood, by identity. */
    private static List<Row> withImages(final List<Row> rows, final List<Row> changed, final List<Row> images) {
        final Map<Row, Row> imageOf = new IdentityHashMap<>();
        for (int i = 0; i < changed.size(); i++) {
            imageOf.put(changed.get(i), images.get(i));
        }
        final List<Row> after = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final Row image = imageOf.get(row);
            after.add(image != null ? image : row);
        }
        return after;
    }

    /**
     * A MERGE waits for another session's transaction that holds its target before its first write, whichever
     * clause writes it — an insert of a NOT MATCHED clause too; one that writes nothing takes nothing (live-verified).
     */
    private void requireUnlockedOnce(final boolean[] checked, final String fullyQualifiedName, final Table target) {
        if (!checked[0]) {
            executor.requireTableUnlocked(fullyQualifiedName, target.getName());
            checked[0] = true;
        }
    }
}
