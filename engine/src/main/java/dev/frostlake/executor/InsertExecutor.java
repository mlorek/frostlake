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

import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.transaction.TransactionWriteSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INSERT write-path query stage extracted from {@link QueryExecutor}: single-table
 * {@code INSERT ... VALUES}/{@code INSERT ... SELECT} (incl. OVERWRITE and WITH-clause CTEs), Snowflake
 * multi-table {@code INSERT [FIRST|ALL] ... WHEN ... INTO ...}, per-row construction (auto-increment +
 * DEFAULT + explicit-column mapping) and the buffered (deferred-apply) vs. immediate write with
 * transaction logging + stream tracking, plus the deferred-INSERT PK/UK duplicate checks. The shared
 * write-constraint enforcement ({@code enforceColumnConstraints}), per-column value resolution
 * ({@code insertColumnValue}), DML-count result shaping and all non-INSERT-specific helpers stay on the
 * owning executor and are reached through {@code executor}; mutable per-query flags (deferred-apply) are
 * read live from the executor so semantics stay byte-for-byte identical to the pre-extraction code.
 */
final class InsertExecutor {

    private static final Logger logger = LoggerFactory.getLogger(InsertExecutor.class);

    private final QueryExecutor executor;

    InsertExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    Object executeInsertFromContext(final FrostlakeParser.InsertStatementContext ctx) {
        executor.checkNotReadOnly();
        try {
            // Auto-start transaction if not active
            if (!executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }

            // Handle CTEs (WITH clause) if present
            Map<String, ResultSet> cteResults = null;
            if (ctx.withClause() != null) {
                cteResults = executor.executeCTEs(ctx.withClause(), null);
            }

            String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            Table table = executor.getCatalog().resolveTable(tableName);

            // Check INSERT permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.INSERT, SecurableObjectType.TABLE, tableName);
            }

            // Handle OVERWRITE - truncate table before inserting
            boolean isOverwrite = ctx.OVERWRITE() != null;
            if (isOverwrite) {
                String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);
                executor.getStorageEngine().truncateTable(fullyQualifiedName);
                table.setRowCount(0);
                logger.trace("Truncated table {} due to INSERT OVERWRITE", tableName);
            }

            // Get column names if specified
            List<String> columnNames = null;
            if (ctx.columnListOptional() != null) {
                columnNames = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext id : ctx.columnListOptional().identifierList().identifier()) {
                    columnNames.add(executor.getIdentifier(id));
                }
            }

            // Parse values - either from VALUES clause or SELECT statement
            List<List<Object>> valuesList = new ArrayList<>();

            if (ctx.valueTupleList() != null) {
                // INSERT ... VALUES
                for (final FrostlakeParser.ValueTupleContext tuple : ctx.valueTupleList().valueTuple()) {
                    List<Object> values = new ArrayList<>();
                    // Parse each value as an expression (supports literals, JSON objects, arrays, etc.)
                    for (final FrostlakeParser.ExpressionContext expr : tuple.valueList().expression()) {
                        String exprText = executor.getOriginalText(expr);
                        // Create dummy table/row for expression evaluation
                        Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                        Row dummyRow = new Row(new ArrayList<>());
                        ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                        // Add procedural variables to the lateral context
                        evaluator.setOuterLateralContext(executor.getProceduralVariablesAsContext());
                        Object value = evaluator.evaluate(exprText, dummyRow);
                        values.add(value);
                    }
                    valuesList.add(values);
                }
            } else if (ctx.selectStatement() != null) {
                // INSERT ... SELECT. A stream read in this subquery is consumed when the txn commits (the DML
                // window is marked centrally in execute()); pass CTE results to the SELECT execution.
                ResultSet selectResult = executor.executeSelectFromContextWithCTEs(ctx.selectStatement(), null, cteResults);
                for (final Row row : selectResult.getRows()) {
                    valuesList.add(row.getValues());
                }
            }

            // Insert rows
            int rowsInserted = 0;
            // Use fully qualified name for storage access
            String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            for (final List<Object> values : valuesList) {
                final Row row = buildInsertRow(table, fullyQualifiedName, columnNames, values);
                insertRowInto(table, fullyQualifiedName, row);
                rowsInserted++;
            }

            logger.trace("Inserted {} row(s) into table: {}", rowsInserted, tableName);
            return executor.dmlCountResult("number of rows inserted", rowsInserted);

        } catch (final SecurityException e) {
            throw e; // Let security exceptions propagate
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute INSERT: " + e.getMessage(), e);
        }
    }

    /** Build a row in table-column order from positional or column-listed values (auto-increment + defaults applied). */
    Row buildInsertRow(final Table table, final String fullyQualifiedName,
                               final List<String> columnNames, final List<Object> values) {
        // With an explicit column list, surface a value/column-count mismatch as a clean error rather than an
        // IndexOutOfBounds (too few) or a silent drop (too many); Snowflake requires the counts to match.
        // (The positional path stays lenient — it fills missing trailing columns with defaults.)
        if (columnNames != null && values.size() != columnNames.size()) {
            throw new RuntimeException("INSERT value count (" + values.size()
                + ") does not match the number of target columns (" + columnNames.size() + ")");
        }
        final List<Object> rowValues = new ArrayList<>();
        if (columnNames != null) {
            final Map<String, Object> valueMap = new HashMap<>();
            for (int i = 0; i < columnNames.size(); i++) {
                valueMap.put(columnNames.get(i).toUpperCase(), values.get(i));
            }
            for (final TableColumn col : table.getColumns()) {
                rowValues.add(executor.insertColumnValue(col, fullyQualifiedName, valueMap.get(col.getName().toUpperCase())));
            }
        } else {
            for (int i = 0; i < table.getColumns().size(); i++) {
                final TableColumn col = table.getColumns().get(i);
                rowValues.add(executor.insertColumnValue(col, fullyQualifiedName, i < values.size() ? values.get(i) : null));
            }
        }
        return new Row(rowValues);
    }

    /** Insert one fully-built row: enforce constraints, then buffer (deferred-apply) or write + log + track streams. */
    void insertRowInto(final Table table, final String fullyQualifiedName, final Row row) {
        executor.enforceColumnConstraints(table, row);
        if (executor.isDeferredApply()) {
            final StorageEngine.TableStorage base = executor.getStorageEngine().getTableStorage(fullyQualifiedName);
            final TransactionWriteSet writeSet = executor.getTransactionManager().getCurrentTransaction().getWriteSet();
            validateDeferredInsertPrimaryKey(table, base, writeSet, fullyQualifiedName, row);
            validateDeferredInsertUniqueKey(table, base, writeSet, fullyQualifiedName, row);
            writeSet.recordInsert(fullyQualifiedName, row);
        } else {
            final int insertIndex = executor.getStorageEngine().getTableStorage(fullyQualifiedName).getRowCount();
            executor.getStorageEngine().getTableStorage(fullyQualifiedName).insert(row);
            if (executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().getCurrentTransaction().logInsert(fullyQualifiedName, insertIndex, row);
            }
            if (executor.getStreamManager() != null) {
                executor.getStreamManager().trackInsert(fullyQualifiedName, row);
            }
        }
    }

    /**
     * Snowflake multi-table INSERT — route each row of the subquery to one or more target tables.
     * INSERT [OVERWRITE] ALL INTO ... (unconditional), or INSERT [OVERWRITE] {FIRST | ALL}
     * WHEN cond THEN INTO ... [ELSE INTO ...]. FIRST stops at the first matching WHEN; ALL applies every
     * matching WHEN; ELSE applies only when no WHEN matched.
     */
    Object executeMultiTableInsert(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        executor.checkNotReadOnly();
        try {
            if (!executor.getTransactionManager().hasActiveTransaction()) {
                executor.getTransactionManager().beginTransaction();
            }
            final ResultSet source = executor.executeSelectFromContext(ctx.selectStatement());
            final Table sourceTable = executor.resultSetToTable(source, "multi_insert_source");
            final boolean overwrite = ctx.OVERWRITE() != null;
            final boolean first = ctx.FIRST() != null;
            final boolean conditional = !ctx.multiInsertWhen().isEmpty();

            // OVERWRITE: truncate each distinct target table once, before inserting.
            if (overwrite) {
                final Set<String> truncated = new HashSet<>();
                for (final FrostlakeParser.MultiInsertIntoContext into : allMultiInsertIntos(ctx)) {
                    final String tableName = executor.getQualifiedName(into.qualifiedName());
                    final String fqn = executor.getFullyQualifiedTableName(tableName);
                    if (truncated.add(fqn)) {
                        executor.getStorageEngine().truncateTable(fqn);
                        executor.getCatalog().resolveTable(tableName).setRowCount(0);
                    }
                }
            }

            int rowsInserted = 0;
            for (final Row srcRow : source.getRows()) {
                final List<FrostlakeParser.MultiInsertIntoContext> applicable = new ArrayList<>();
                if (!conditional) {
                    applicable.addAll(ctx.multiInsertInto());
                } else {
                    boolean matched = false;
                    for (final FrostlakeParser.MultiInsertWhenContext whenCtx : ctx.multiInsertWhen()) {
                        if (evaluateRowCondition(whenCtx.booleanExpr(), sourceTable, srcRow)) {
                            matched = true;
                            applicable.addAll(whenCtx.multiInsertInto());
                            if (first) {
                                break;   // FIRST: only the first matching WHEN's targets
                            }
                        }
                    }
                    if (!matched && ctx.multiInsertElse() != null) {
                        applicable.addAll(ctx.multiInsertElse().multiInsertInto());
                    }
                }

                for (final FrostlakeParser.MultiInsertIntoContext into : applicable) {
                    final String tableName = executor.getQualifiedName(into.qualifiedName());
                    final Table target = executor.getCatalog().resolveTable(tableName);
                    final String fqn = executor.getFullyQualifiedTableName(tableName);
                    if (executor.getSecurityManager() != null) {
                        executor.getSecurityManager().checkPermission(Privilege.INSERT, SecurableObjectType.TABLE, tableName);
                    }
                    List<String> columnNames = null;
                    if (into.columnListOptional() != null) {
                        columnNames = new ArrayList<>();
                        for (final FrostlakeParser.IdentifierContext id : into.columnListOptional().identifierList().identifier()) {
                            columnNames.add(executor.getIdentifier(id));
                        }
                    }
                    final List<Object> values;
                    if (into.expressionList() != null) {
                        values = new ArrayList<>();
                        for (final FrostlakeParser.ExpressionContext exprCtx : into.expressionList().expression()) {
                            values.add(evaluateRowExpression(exprCtx, sourceTable, srcRow));
                        }
                    } else {
                        values = srcRow.getValues();
                    }
                    insertRowInto(target, fqn, buildInsertRow(target, fqn, columnNames, values));
                    rowsInserted++;
                }
            }
            logger.trace("Multi-table INSERT routed {} row insertion(s)", rowsInserted);
            return null;
        } catch (final SecurityException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("Failed to execute multi-table INSERT: " + e.getMessage(), e);
        }
    }

    private List<FrostlakeParser.MultiInsertIntoContext> allMultiInsertIntos(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        final List<FrostlakeParser.MultiInsertIntoContext> all = new ArrayList<>(ctx.multiInsertInto());
        for (final FrostlakeParser.MultiInsertWhenContext whenCtx : ctx.multiInsertWhen()) {
            all.addAll(whenCtx.multiInsertInto());
        }
        if (ctx.multiInsertElse() != null) {
            all.addAll(ctx.multiInsertElse().multiInsertInto());
        }
        return all;
    }

    private Object evaluateRowExpression(final ParserRuleContext exprCtx, final Table sourceTable, final Row srcRow) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(sourceTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        evaluator.setOuterLateralContext(executor.getProceduralVariablesAsContext());
        return evaluator.evaluate(executor.getOriginalText(exprCtx), srcRow);
    }

    private boolean evaluateRowCondition(final FrostlakeParser.BooleanExprContext condCtx, final Table sourceTable, final Row srcRow) {
        final Object result = evaluateRowExpression(condCtx, sourceTable, srcRow);
        return result instanceof Boolean ? (Boolean) result
            : result != null && Boolean.parseBoolean(String.valueOf(result));
    }

    private void validateDeferredInsertPrimaryKey(final Table table, final StorageEngine.TableStorage base,
            final TransactionWriteSet writeSet, final String fullyQualifiedName, final Row row) {
        if (!executor.getStorageEngine().isEnforcePrimaryKey() || table.getPrimaryKeys().isEmpty()) {
            return;
        }
        final Object pk = base.primaryKeyOf(row);
        if (pk == null) {
            return;
        }
        if (base.getRowByPrimaryKey(pk) != null) {
            throw new RuntimeException("Duplicate primary key: " + pk);
        }
        for (final Row pending : writeSet.pendingInserts(fullyQualifiedName)) {
            if (pk.equals(base.primaryKeyOf(pending))) {
                throw new RuntimeException("Duplicate primary key: " + pk);
            }
        }
    }

    /**
     * Enforce UNIQUE constraints at statement time for a deferred INSERT, but only when the opt-in
     * {@code constraints.enforce.uniqueKey} flag is on (off by default — UNIQUE is otherwise
     * informational, matching Snowflake). Each UNIQUE column's non-null value must not already appear
     * in the committed base or this transaction's pending inserts. UNIQUE is modeled per column, so a
     * composite UNIQUE is enforced column by column. Scans the rows — unique indexes are intentionally
     * not implemented.
     */
    private void validateDeferredInsertUniqueKey(final Table table, final StorageEngine.TableStorage base,
            final TransactionWriteSet writeSet, final String fullyQualifiedName, final Row row) {
        if (!executor.getStorageEngine().isEnforceUniqueKey()) {
            return;
        }
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            final TableColumn col = cols.get(i);
            if (!col.isUnique() || col.isPrimaryKey() || i >= row.getValues().size()) {
                continue; // PK columns are covered by the primary-key check; NULLs below are unconstrained
            }
            final Object value = row.getValue(i);
            if (value == null) {
                continue;
            }
            for (final Row existing : base.scan()) {
                if (i < existing.getValues().size() && value.equals(existing.getValue(i))) {
                    throw new RuntimeException("Duplicate unique key on column '" + col.getName() + "': " + value);
                }
            }
            for (final Row pending : writeSet.pendingInserts(fullyQualifiedName)) {
                if (i < pending.getValues().size() && value.equals(pending.getValue(i))) {
                    throw new RuntimeException("Duplicate unique key on column '" + col.getName() + "': " + value);
                }
            }
        }
    }
}
