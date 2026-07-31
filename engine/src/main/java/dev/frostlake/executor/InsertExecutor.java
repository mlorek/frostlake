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
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.JsonArrayExpression;
import dev.frostlake.executor.expressions.JsonObjectExpression;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.transaction.TransactionWriteSet;
import java.util.ArrayList;
import java.util.Arrays;
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

            // WITH-prefixed DML is not Snowflake syntax (live-verified); CTEs reach a DML statement
            // only inside its subqueries.
            final Map<String, ResultSet> cteResults = null;

            String tableName = ctx.objectName().KW_IDENTIFIER() != null
                ? executor.resolveObjectName(ctx.objectName())
                : executor.getQualifiedName(ctx.objectName().qualifiedName());
            Table table = executor.getCatalog().resolveTableAsWritten(tableName, "Table");

            // Check INSERT permission
            if (executor.getSecurityManager() != null) {
                executor.getSecurityManager().checkPermission(Privilege.INSERT, SecurableObjectType.TABLE, tableName);
            }

            // OVERWRITE replaces the table's contents, but the truncate must happen only AFTER the source
            // rows have been produced: a self-referencing INSERT OVERWRITE INTO t … SELECT … FROM t reads the
            // table it overwrites, so truncating first made the source SELECT see an empty table and the
            // statement silently wiped the data (0 rows in, 0 rows out).
            boolean isOverwrite = ctx.OVERWRITE() != null;

            // Get column names if specified
            List<String> columnNames = null;
            if (ctx.columnListOptional() != null) {
                columnNames = new ArrayList<>();
                for (final FrostlakeParser.NamePartContext id : ctx.columnListOptional().namePart()) {
                    columnNames.add(ParseTreeText.namePartText(id));
                }
            }

            // Parse values - either from VALUES clause or SELECT statement
            List<List<Object>> valuesList = new ArrayList<>();

            if (ctx.valueTupleList() != null) {
                // INSERT ... VALUES
                for (final FrostlakeParser.ValueTupleContext tuple : ctx.valueTupleList().valueTuple()) {
                    List<Object> values = new ArrayList<>();
                    // Parse each value as an expression (supports literals, JSON objects, arrays, etc.)
                    int valuePosition = 0;
                    for (final FrostlakeParser.ExpressionContext expr : tuple.valueList().expression()) {
                        rejectStringLiteralIntoSemiStructured(table, columnNames, valuePosition, expr);
                        valuePosition++;
                        String exprText = executor.getOriginalText(expr);
                        rejectSemiStructuredValueExpression(exprText);
                        // Create dummy table/row for expression evaluation
                        Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
                        Row dummyRow = new Row(new ArrayList<>());
                        // No scripting variables are offered to the evaluator: INSERT is an embedded SQL
                        // statement, where a stored-procedure parameter / DECLAREd / LET name must be
                        // written :name (a bare one is an identifier — live: "invalid identifier 'V'").
                        ExpressionEvaluator evaluator = new ExpressionEvaluator(dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                        Object value = evaluator.evaluate(exprText, dummyRow);
                        values.add(value);
                    }
                    valuesList.add(values);
                }
            } else if (ctx.selectStatement() != null) {
                // INSERT ... SELECT. A stream read in this subquery is consumed when the txn commits (the DML
                // window is marked centrally in SQLCommandVisitor.visitDmlStatement); pass CTE results along.
                ResultSet selectResult = executor.executeSelectFromContextWithCTEs(ctx.selectStatement(), null, cteResults);
                for (final Row row : selectResult.getRows()) {
                    valuesList.add(row.getValues());
                }
            }

            // Insert rows
            int rowsInserted = 0;
            // Use fully qualified name for storage access
            String fullyQualifiedName = executor.getFullyQualifiedTableName(tableName);

            // The source rows are now materialized, so it is safe to replace the table's contents. Route through
            // the same transaction-aware path as TRUNCATE: clearing the base store directly left rows this
            // transaction had inserted earlier in place, so the OVERWRITE appended to them.
            if (isOverwrite) {
                executor.emptyTableContents(fullyQualifiedName);
                table.setRowCount(0);
                logger.trace("Truncated table {} due to INSERT OVERWRITE", tableName);
            }

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
            throw StatementErrors.propagate(e);
        }
    }

    /** Build a row in table-column order from positional or column-listed values (auto-increment + defaults applied). */
    /** Snowflake rejects a VARCHAR string literal for a VARIANT/OBJECT/ARRAY column in a VALUES
     *  clause (live-verified: "Expression type does not match column data type, expecting VARIANT
     *  but got VARCHAR"); use INSERT ... SELECT with PARSE_JSON/TO_VARIANT instead. The check is on
     *  the EXPRESSION (a string literal), because the engine's VARIANT values are JSON text and a
     *  legitimate PARSE_JSON result is indistinguishable from a raw string by value. */
    private void rejectStringLiteralIntoSemiStructured(final Table table, final List<String> columnNames,
                                                       final int valuePosition,
                                                       final FrostlakeParser.ExpressionContext expr) {
        if (!(expr instanceof FrostlakeParser.LiteralExprContext)
                || ((FrostlakeParser.LiteralExprContext) expr).literal().STRING_LITERAL() == null) {
            return;
        }
        final TableColumn col;
        if (columnNames != null) {
            if (valuePosition >= columnNames.size() || !table.hasColumn(columnNames.get(valuePosition))) {
                return;
            }
            col = table.getColumn(columnNames.get(valuePosition));
        } else {
            if (valuePosition >= table.getColumns().size()) {
                return;
            }
            col = table.getColumns().get(valuePosition);
        }
        final String typeName = col.getDataType().getName();
        if ("VARIANT".equals(typeName) || "OBJECT".equals(typeName) || "ARRAY".equals(typeName)) {
            throw new RuntimeException("Expression type does not match column data type, expecting "
                + typeName + " but got VARCHAR for column " + col.getName());
        }
    }

    /**
     * Snowflake rejects semi-structured expressions in a VALUES clause outright (live-verified:
     * OBJECT_CONSTRUCT / ARRAY_CONSTRUCT / PARSE_JSON / TO_VARIANT, the {@code [..]} and
     * {@code {..}} literals and {@code ::VARIANT} casts all raise
     * {@code Invalid expression [...] in VALUES clause}); INSERT ... SELECT is the supported route.
     * The check is on the expression AST's top-level shape.
     */
    private void rejectSemiStructuredValueExpression(final String exprText) {
        final Expression ast;
        try {
            ast = ExpressionEvaluator.parse(exprText);
        } catch (final RuntimeException notAnExpression) {
            return;
        }
        boolean semiStructured = false;
        if (ast instanceof JsonObjectExpression || ast instanceof JsonArrayExpression) {
            semiStructured = true;
        } else if (ast instanceof CastExpression) {
            // A VECTOR cast is rejected too, but Snowflake names the TYPE rather than the expression
            // (live: "Invalid data type [VECTOR(FLOAT, 3)] in VALUES clause").
            if (((CastExpression) ast).getDeclaredTarget() instanceof VectorType) {
                throw new RuntimeException("Invalid data type ["
                    + ((CastExpression) ast).getDeclaredTarget().getName() + "] in VALUES clause");
            }
            final String target = ((CastExpression) ast).getTargetType().toUpperCase();
            semiStructured = target.startsWith("VARIANT") || target.startsWith("OBJECT") || target.startsWith("ARRAY");
        } else if (ast instanceof FunctionCallExpression) {
            final String name = ((FunctionCallExpression) ast).getFunctionName().toUpperCase();
            final BuiltInFunction fn = executor.getFunctionRegistry().getFunction(name);
            semiStructured = fn != null && (fn.getReturnType() instanceof VariantType
                || fn.getReturnType() instanceof ObjectType || fn.getReturnType() instanceof ArrayType
                || fn.getReturnType() instanceof GeographyType || fn.getReturnType() instanceof GeometryType);
            // Beyond the semi-structured families, live rejects a further per-FUNCTION set in VALUES —
            // measured (Probe169b): COMPRESS, MD5_BINARY, HEX_DECODE_BINARY, SHA2 and
            // RANDOM all raise the same sentence, while TO_BINARY and UPPER pass. The boundary is not
            // a return-type rule (SHA2 returns VARCHAR and is rejected; TO_BINARY returns BINARY and
            // passes), so only the measured names are listed.
            semiStructured = semiStructured || VALUES_REJECTED_FUNCTIONS.contains(name);
        }
        if (semiStructured) {
            throw new RuntimeException("Invalid expression [" + exprText + "] in VALUES clause");
        }
    }

    /** The measured non-semi-structured functions live refuses inside a VALUES clause. */
    private static final Set<String> VALUES_REJECTED_FUNCTIONS = new HashSet<>(Arrays.asList(
        "COMPRESS", "MD5_BINARY", "HEX_DECODE_BINARY", "SHA2", "RANDOM"));

    Row buildInsertRow(final Table table, final String fullyQualifiedName,
                               final List<String> columnNames, final List<Object> values) {
        // Snowflake requires the value count to match exactly — both with an explicit column list
        // and positionally (live-verified: "Insert value list does not match column list"); defaults
        // apply only to columns omitted from an explicit list.
        if (columnNames != null && values.size() != columnNames.size()) {
            throw new RuntimeException("INSERT value count (" + values.size()
                + ") does not match the number of target columns (" + columnNames.size() + ")");
        }
        if (columnNames == null && values.size() != table.getColumns().size()) {
            throw new RuntimeException("Insert value list does not match column list expecting "
                + table.getColumns().size() + " but got " + values.size());
        }
        final List<Object> rowValues = new ArrayList<>();
        if (columnNames != null) {
            final Map<String, Object> valueMap = new HashMap<>();
            for (int i = 0; i < columnNames.size(); i++) {
                valueMap.put(columnNames.get(i).toUpperCase(), values.get(i));
            }
            for (final TableColumn col : table.getColumns()) {
                final String key = col.getName().toUpperCase();
                // Snowflake applies DEFAULT / AUTOINCREMENT only to columns OMITTED from the insert's
                // column list. A listed column keeps its explicit value — including an explicit NULL
                // (which must NOT be silently replaced by the column default).
                if (valueMap.containsKey(key)) {
                    rowValues.add(valueMap.get(key));
                } else {
                    rowValues.add(executor.insertColumnValue(col, fullyQualifiedName, null));
                }
            }
        } else {
            for (int i = 0; i < table.getColumns().size(); i++) {
                final TableColumn col = table.getColumns().get(i);
                // Positionally covered columns are explicit (even NULL); only the missing trailing
                // columns are omitted and take DEFAULT / AUTOINCREMENT.
                if (i < values.size()) {
                    rowValues.add(values.get(i));
                } else {
                    rowValues.add(executor.insertColumnValue(col, fullyQualifiedName, null));
                }
            }
        }
        return new Row(rowValues);
    }

    /** Insert one fully-built row: enforce constraints, then buffer (deferred-apply) or write + log + track streams. */
    void insertRowInto(final Table table, final String fullyQualifiedName, final Row row) {
        executor.enforceColumnConstraintsForDml(table, row);
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
                        executor.emptyTableContents(fqn);
                        executor.getCatalog().resolveTableAsWritten(tableName, "Table").setRowCount(0);
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
                    final Table target = executor.getCatalog().resolveTableAsWritten(tableName, "Table");
                    final String fqn = executor.getFullyQualifiedTableName(tableName);
                    if (executor.getSecurityManager() != null) {
                        executor.getSecurityManager().checkPermission(Privilege.INSERT, SecurableObjectType.TABLE, tableName);
                    }
                    List<String> columnNames = null;
                    if (into.columnListOptional() != null) {
                        columnNames = new ArrayList<>();
                        for (final FrostlakeParser.NamePartContext id : into.columnListOptional().namePart()) {
                            columnNames.add(ParseTreeText.namePartText(id));
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
            throw StatementErrors.propagate(e);
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
        return evaluator.evaluate(executor.getOriginalText(exprCtx), srcRow);
    }

    private boolean evaluateRowCondition(final FrostlakeParser.BooleanExprContext condCtx, final Table sourceTable, final Row srcRow) {
        final Object result = evaluateRowExpression(condCtx, sourceTable, srcRow);
        return SqlTruth.isTrue(result)
            ? true : result != null && Boolean.parseBoolean(String.valueOf(result));
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
