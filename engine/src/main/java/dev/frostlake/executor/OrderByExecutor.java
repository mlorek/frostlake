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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * ORDER BY query stage extracted from {@link QueryExecutor}. Resolves each sort key (positional
 * ordinal → N-th SELECT expression, SELECT alias → its expression, else column/expression as written),
 * materialises the per-row sort-key vector once, and stable-sorts with NULLS FIRST/LAST placement.
 * A separate post-GROUP-BY path matches ORDER BY keys to already-computed result columns. Pure value
 * comparisons come from {@link ValueComparisons}; column/qualified resolution and expression evaluation
 * are delegated back to the owning executor.
 */
final class OrderByExecutor {

    private final QueryExecutor executor;

    OrderByExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    List<Row> orderBy(final List<Row> rows, final Table table, final FrostlakeParser.SelectStatementContext ctx,
                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // Parse order items
        List<String> orderColumns = new ArrayList<>();
        List<Boolean> ascending = new ArrayList<>();
        final List<Boolean> nullsFirst = new ArrayList<>();

        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            // Resolve the ORDER BY key to text we can evaluate per row: a positional ordinal → the N-th
            // SELECT expression; a SELECT alias → that item's expression; otherwise the key as written (a
            // column name, or an arbitrary expression that resolveOrderValue evaluates against the row).
            // The key text must come from getOriginalText, NOT getText: the latter concatenates tokens
            // with no whitespace, so a key needing separators — `x IS NOT NULL` → `xISNOTNULL`,
            // `CAST(x AS NUMBER)` → `CAST(xASNUMBER)` — collapsed into an unparseable identifier and
            // failed to resolve. (Only the ordinal DETECTION needs the bare text, and digits are
            // unaffected by spacing.)
            String colName = resolveOrderOrdinal(ParseTreeText.getOriginalText(item.expression()), ctx);
            colName = resolveOrderAlias(colName, ctx);
            orderColumns.add(colName);
            ascending.add(item.DESC() == null); // Default is ASC
            nullsFirst.add(ValueComparisons.nullsFirstFlag(item));
        }

        rejectFileSortKeys(orderColumns, table, aliasToTable, allTables);
        validateOrderKeyScope(ctx, table, aliasToTable, allTables);

        if (rows.size() <= 1) {
            return rows; // nothing to compare; also avoids resolving keys for a degenerate result
        }

        // Decorate-sort-undecorate: resolve each row's sort-key vector exactly ONCE (the old comparator
        // re-resolved every column — string parse + alias scan — for both operands on every comparison,
        // i.e. O(n log n) resolutions; this is O(n)).
        final int keyCount = orderColumns.size();
        final Object[][] sortKeys = new Object[rows.size()][keyCount];
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            for (int i = 0; i < keyCount; i++) {
                sortKeys[r][i] = resolveOrderValue(row, orderColumns.get(i), table, aliasToTable, allTables);
            }
        }

        // Sort an index array against the precomputed keys (TimSort is stable, so equal keys keep their
        // original order — identical to the previous rows.sort), then rebuild the list.
        Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                for (int i = 0; i < keyCount; i++) {
                    int cmp = ValueComparisons.compareOrderKey(sortKeys[a][i], sortKeys[b][i], ascending.get(i), nullsFirst.get(i));
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        List<Row> sorted = new ArrayList<>(rows.size());
        for (int i = 0; i < order.length; i++) {
            sorted.add(rows.get(order[i]));
        }
        rows.clear();
        rows.addAll(sorted);
        return rows;
    }

    /**
     * Snowflake rejects a FILE-typed ORDER BY key at compile time ("Expressions of type FILE cannot be
     * used as ORDER BY keys") while sorting an OBJECT or VARIANT quite happily. The keys arrive with
     * ordinals and SELECT aliases already resolved to their defining expression, so one rule covers
     * {@code ORDER BY f}, {@code ORDER BY 1} and {@code ORDER BY <alias>}. Deliberately checked BEFORE
     * the single-row short circuit: the rejection is a compile-time one live, so it must not depend on
     * how many rows came back.
     */
    private void rejectFileSortKeys(final List<String> orderColumns, final Table table,
                                    final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator keyChecker = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (allTables != null) {
            keyChecker.setMultiTableContext(aliasToTable, allTables);
        }
        for (final String key : orderColumns) {
            keyChecker.validateKey(ExpressionEvaluator.parse(key), SortKeyRole.ORDER_BY);
        }
    }

    /**
     * Plan-time scope validation of the ORDER BY keys, BEFORE the single-row short circuit — the
     * rejections are compile-time live, however many rows came back. ORDER BY resolves against the
     * SELECT output FIRST, so a key matching an output column (by position, alias, expression text
     * or produced column name) is exempt; a key that falls through to FROM-clause resolution obeys
     * the same rules as everywhere else — an invalid qualifier and a bare ON-join duplicate are
     * both rejected, the duplicate EVEN THOUGH a row-time first-match could have picked a side
     * (measured: a bare duplicate NOT in the SELECT output is "ambiguous column name" live, with
     * rows present as well). Set-operation ORDER BY resolves only against the combined output and
     * keeps its existing row-time handling.
     */
    private void validateOrderKeyScope(final FrostlakeParser.SelectStatementContext ctx, final Table table,
                                       final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (ctx.selectOperand().size() > 1) {
            return;
        }
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();
        // Aliases + star RENAME targets (via the shared helper), plus each item's produced column
        // name — the full set of names ORDER BY may resolve output-first.
        final Set<String> outputNames = new HashSet<>(executor.selectItemAliasNames(firstClause));
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            final String produced = producedColumnName(item);
            if (produced != null) {
                outputNames.add(produced.toUpperCase());
            }
        }
        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            if (matchOrderItem(item.expression().getText(), selectItems, false) >= 0) {
                continue;
            }
            final String asWritten = ParseTreeText.getOriginalText(item.expression()).trim();
            if (outputNames.contains(asWritten.toUpperCase())) {
                continue;
            }
            executor.validateClauseScope(asWritten, table, aliasToTable, allTables, outputNames);
        }
    }

    /**
     * Resolve one ORDER BY key for a row, preserving the original resolution order: qualified column
     * (table.column / alias.column), else a bare column-index lookup, else a "column not found" error.
     */
    private Object resolveOrderValue(final Row row, final String colName, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables) {
        try {
            return executor.getQualifiedColumnValueFromTables(row, allTables, aliasToTable, colName,
                table != null ? table.getJoinKeyNames() : null);
        } catch (final InvalidQualifierException invalidQualifier) {
            // Definitive — live rejects an ORDER BY key whose qualifier names no FROM key
            // ("invalid identifier 'R.C'"); the bare-column fallbacks below must not resurrect it.
            throw invalidQualifier;
        } catch (final Exception e) {
            try {
                int colIndex = ValueComparisons.getColumnIndex(table, colName);
                return row.getValue(colIndex);
            } catch (final Exception e2) {
                // Not a plain column: evaluate as an expression against the row so ORDER BY can sort by an
                // arbitrary expression or function (e.g. ORDER BY a+b, ABS(a)) — Snowflake allows any
                // expression over the in-scope tables as a sort key.
                try {
                    final ExpressionEvaluator evaluator =
                        new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                    return evaluator.evaluate(colName, row);
                } catch (final Exception e3) {
                    throw new RuntimeException(SqlCompilationError.invalidIdentifier(colName), e3);
                }
            }
        }
    }

    /** ORDER BY by a SELECT alias → that item's expression text, so it can be evaluated per row. */
    private String resolveOrderAlias(final String text, final FrostlakeParser.SelectStatementContext ctx) {
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        for (final FrostlakeParser.SelectItemContext item : firstClause.selectList().selectItem()) {
            if (SelectItemAccessors.getItemAlias(item) != null
                    && ParseTreeText.getIdentifier(SelectItemAccessors.getItemAlias(item)).equalsIgnoreCase(text)) {
                final ParserRuleContext e = SelectItemAccessors.getItemExpression(item);
                if (e != null) {
                    return ParseTreeText.getOriginalText(e);
                }
            }
        }
        return text;
    }

    /** ORDER BY &lt;n&gt; positional reference: resolve to the N-th SELECT item's expression text. */
    private String resolveOrderOrdinal(final String text, final FrostlakeParser.SelectStatementContext ctx) {
        if (text == null || !text.matches("\\d+")) {
            return text;
        }
        final int n = Integer.parseInt(text);
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> items = firstClause.selectList().selectItem();
        if (n >= 1 && n <= items.size()) {
            final ParserRuleContext e = SelectItemAccessors.getItemExpression(items.get(n - 1));
            if (e != null) {
                // Original text (spacing preserved), as the alias path does — getText() would collapse
                // `x IS NOT NULL` to `xISNOTNULL` and the key would then resolve to nothing.
                return ParseTreeText.getOriginalText(e);
            }
        }
        return text;
    }

    /**
     * ORDER BY over rows already reshaped to the SELECT list (after GROUP BY / aggregation / window). Each
     * ORDER BY item is matched to a SELECT column by position, alias, or expression text; an item that
     * matches NONE is resolved by {@code resolver} over the row's group — a grouped column or aggregate not
     * in the SELECT is a valid Snowflake ORDER BY ({@code GROUP BY a, b ORDER BY b} / {@code ORDER BY MAX(c)}).
     * With no resolver an unmatched item is an error, as before.
     */
    List<Row> orderByAfterGroupBy(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx,
                                  final GroupOrderKeyResolver resolver) {
        // The first selectClause carries the SELECT-list structure (UNION parts must be compatible).
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();

        final List<FrostlakeParser.OrderItemContext> items = ctx.orderByClause().orderItem();
        final int nKeys = items.size();
        final int[] colIndex = new int[nKeys];       // matched SELECT column, or -1 when resolved per-group
        final boolean[] ascending = new boolean[nKeys];
        final Boolean[] nullsFirst = new Boolean[nKeys];   // nullable — nullsFirstFlag returns null when unspecified
        for (int k = 0; k < nKeys; k++) {
            final FrostlakeParser.OrderItemContext item = items.get(k);
            ascending[k] = item.DESC() == null;
            nullsFirst[k] = ValueComparisons.nullsFirstFlag(item);
            colIndex[k] = matchOrderItem(item.expression().getText(), selectItems, ctx.selectOperand().size() > 1);
            if (colIndex[k] == -1 && resolver == null) {
                throw new RuntimeException(
                    "ORDER BY expression not found in SELECT list: " + item.expression().getText());
            }
        }

        // Precompute each row's sort keys BEFORE sorting: sorting reorders rows, but a resolver keys off the
        // row's ORIGINAL index. A matched key is the projected column value; an unmatched key is computed
        // over that row's group.
        final List<Object[]> keys = new ArrayList<>(rows.size());
        for (int r = 0; r < rows.size(); r++) {
            final Object[] rowKeys = new Object[nKeys];
            for (int k = 0; k < nKeys; k++) {
                rowKeys[k] = colIndex[k] >= 0 ? rows.get(r).getValue(colIndex[k]) : resolver.resolve(r, items.get(k));
            }
            keys.add(rowKeys);
        }

        final Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                final Object[] ka = keys.get(a);
                final Object[] kb = keys.get(b);
                for (int k = 0; k < nKeys; k++) {
                    final int cmp = ValueComparisons.compareOrderKey(ka[k], kb[k], ascending[k], nullsFirst[k]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        final List<Row> sorted = new ArrayList<>(rows.size());
        for (final int i : order) {
            sorted.add(rows.get(i));
        }
        return sorted;
    }

    /**
     * Plan-time scope validation of a GROUPED / windowed query's ORDER BY keys — the path whose
     * unmatched keys resolve per source group at row time, which never runs over an EMPTY input
     * while live rejects at compile time (measured per cell): an invalid qualifier and an unknown
     * name are "invalid identifier" — inside aggregate arguments too — and, for a GROUP BY query,
     * a bare FROM column that is neither a group key nor inside an aggregate is
     * "[T.C] is not a valid order by expression". Grouped columns and aggregates NOT in the
     * SELECT list stay legal, as do output aliases, ordinals, repeated group-key expressions and
     * expressions over any of these.
     */
    void validateGroupedOrderKeyScope(final FrostlakeParser.SelectStatementContext stmtCtx,
                                      final FrostlakeParser.SelectClauseContext selectCtx,
                                      final Table table, final Map<String, Table> aliasToTable,
                                      final List<Table> allTables) {
        if (stmtCtx.orderByClause() == null || stmtCtx.selectOperand().size() > 1) {
            return;
        }
        final List<FrostlakeParser.SelectItemContext> selectItems = selectCtx.selectList().selectItem();
        final Set<String> outputNames = new HashSet<>(executor.selectItemAliasNames(selectCtx));
        for (final FrostlakeParser.SelectItemContext item : selectItems) {
            final String produced = producedColumnName(item);
            if (produced != null) {
                outputNames.add(produced.toUpperCase());
            }
        }
        final Set<String> groupKeyTexts = new HashSet<>();
        final Set<String> groupKeyNames = new HashSet<>();
        final FrostlakeParser.GroupByClauseContext groupBy = selectCtx.groupByClause();
        collectGroupKeys(groupBy, groupKeyTexts, groupKeyNames);

        for (final FrostlakeParser.OrderItemContext item : stmtCtx.orderByClause().orderItem()) {
            if (matchOrderItem(item.expression().getText(), selectItems, false) >= 0) {
                continue;
            }
            final String asWritten = ParseTreeText.getOriginalText(item.expression()).trim();
            if (outputNames.contains(asWritten.toUpperCase())
                    || groupKeyTexts.contains(item.expression().getText().toUpperCase())) {
                continue;
            }
            executor.validateClauseScope(asWritten, table, aliasToTable, allTables, outputNames);
            if (groupBy != null && groupBy.ALL() == null) {
                rejectUngroupedOrderReference(ExpressionEvaluator.parse(asWritten), table, allTables,
                    outputNames, groupKeyNames);
            }
        }
    }

    /** The GROUP BY keys, as whitespace-free upper texts (whole-key repetition in ORDER BY is
     *  legal) and — for plain column keys — their column names. ROLLUP / CUBE / GROUPING SETS
     *  members are keys all the same. */
    private void collectGroupKeys(final FrostlakeParser.GroupByClauseContext groupBy,
                                  final Set<String> keyTexts, final Set<String> keyNames) {
        if (groupBy == null || groupBy.ALL() != null) {
            return;
        }
        final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.expression() != null) {
                keys.add(element.expression());
            }
            if (element.groupByColumnList() != null) {
                keys.addAll(element.groupByColumnList().expression());
            }
            if (element.groupingSetList() != null) {
                for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                    keys.addAll(set.expression());
                }
            }
        }
        for (final FrostlakeParser.ExpressionContext key : keys) {
            keyTexts.add(key.getText().toUpperCase());
            try {
                final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(key));
                if (parsed instanceof ColumnReferenceExpression) {
                    keyNames.add(((ColumnReferenceExpression) parsed).getColumnName().toUpperCase());
                }
            } catch (final RuntimeException unparseableKey) {
                // A key the expression parser cannot form contributes its text only.
            }
        }
    }

    /**
     * The "[T.C] is not a valid order by expression" family: within an unmatched ORDER BY key of a
     * GROUP BY query, a column reference OUTSIDE any aggregate call must be a group key (or an
     * output alias). References inside an aggregate are computed per group and always legal.
     * Node kinds this walk does not model (CASE, subqueries, window calls) keep their row-time
     * handling.
     */
    private void rejectUngroupedOrderReference(final Expression expr, final Table table,
                                               final List<Table> allTables, final Set<String> outputNames,
                                               final Set<String> groupKeyNames) {
        if (expr instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression ref = (ColumnReferenceExpression) expr;
            final String colName = ref.getColumnName().toUpperCase();
            if (outputNames.contains(colName) || groupKeyNames.contains(colName)) {
                return;
            }
            throw new RuntimeException(SqlCompilationError.of(
                "[" + ownerQualifier(ref, table, allTables) + "." + colName
                + "] is not a valid order by expression"));
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (executor.getFunctionRegistry().getAggregateFunction(call.getFunctionName().toUpperCase()) != null) {
                return;
            }
            for (final Expression arg : call.getArguments()) {
                rejectUngroupedOrderReference(arg, table, allTables, outputNames, groupKeyNames);
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            rejectUngroupedOrderReference(((BinaryOperationExpression) expr).getLeft(),
                table, allTables, outputNames, groupKeyNames);
            rejectUngroupedOrderReference(((BinaryOperationExpression) expr).getRight(),
                table, allTables, outputNames, groupKeyNames);
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            rejectUngroupedOrderReference(((UnaryOperationExpression) expr).getOperand(),
                table, allTables, outputNames, groupKeyNames);
            return;
        }
        if (expr instanceof CastExpression) {
            rejectUngroupedOrderReference(((CastExpression) expr).getExpression(),
                table, allTables, outputNames, groupKeyNames);
        }
    }

    /** The relation part of live's bracketed rendering: the written qualifier when the reference
     *  carries one, else the name of the first in-scope relation carrying the column. */
    private String ownerQualifier(final ColumnReferenceExpression ref, final Table table,
                                  final List<Table> allTables) {
        if (ref.isQualified()) {
            return ref.getTableName().toUpperCase();
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                for (final TableColumn column : candidate.getColumns()) {
                    if (column.getName().equalsIgnoreCase(ref.getColumnName())) {
                        return candidate.getName().toUpperCase();
                    }
                }
            }
        }
        return table != null ? table.getName().toUpperCase() : "";
    }

    /**
     * ORDER BY over a set operation's combined result. Live resolves these keys against the OUTPUT
     * only — an output column name (the first branch's alias, or the produced name of an un-aliased
     * item), a positional ordinal, or an expression over those output names; a branch table's
     * qualifier, a later branch's column name, an aliased item's pre-alias expression text and any
     * unknown name are all "invalid identifier", measured cell by cell. There is deliberately NO
     * fall-through to any branch's FROM scope.
     */
    List<Row> orderBySetOperation(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx,
                                  final List<ResultSetColumn> outputColumns) {
        final List<FrostlakeParser.OrderItemContext> items = ctx.orderByClause().orderItem();
        final int nKeys = items.size();
        final int[] colIndex = new int[nKeys];
        final List<Expression> keyExprs = new ArrayList<>(nKeys);
        final boolean[] ascending = new boolean[nKeys];
        final Boolean[] nullsFirst = new Boolean[nKeys];

        final List<TableColumn> outputAsTableColumns = new ArrayList<>();
        final Set<String> outputNames = new HashSet<>();
        for (final ResultSetColumn column : outputColumns) {
            outputAsTableColumns.add(new TableColumn(column.getName(), column.getDataType(), true,
                null, false, false, false));
            outputNames.add(column.getName().toUpperCase());
        }
        final Table outputTable = new Table("unioned", outputAsTableColumns, false);

        ExpressionEvaluator keyEvaluator = null;
        for (int k = 0; k < nKeys; k++) {
            final FrostlakeParser.OrderItemContext item = items.get(k);
            ascending[k] = item.DESC() == null;
            nullsFirst[k] = ValueComparisons.nullsFirstFlag(item);
            final String asWritten = ParseTreeText.getOriginalText(item.expression()).trim();
            int matched = -1;
            if (asWritten.matches("\\d+")) {
                final int ordinal = Integer.parseInt(asWritten);
                if (ordinal >= 1 && ordinal <= outputColumns.size()) {
                    matched = ordinal - 1;
                } else {
                    throw new RuntimeException("ORDER BY expression not found in SELECT list: " + asWritten);
                }
            }
            for (int i = 0; matched == -1 && i < outputColumns.size(); i++) {
                if (outputColumns.get(i).getName().equalsIgnoreCase(asWritten)) {
                    matched = i;
                }
            }
            colIndex[k] = matched;
            if (matched >= 0) {
                keyExprs.add(null);
                continue;
            }
            // An expression key: only the output relation is in scope — the walk turns anything
            // else (a branch qualifier, a non-output name) into live's invalid-identifier.
            executor.validateClauseScope(asWritten, outputTable,
                Map.of(outputTable.getName().toUpperCase(), outputTable), List.of(outputTable), outputNames);
            if (keyEvaluator == null) {
                keyEvaluator = new ExpressionEvaluator(outputTable,
                    executor.getFunctionRegistry(), executor.getCatalog(), executor);
            }
            keyExprs.add(ExpressionEvaluator.parse(asWritten));
        }

        final List<Object[]> keys = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final Object[] rowKeys = new Object[nKeys];
            for (int k = 0; k < nKeys; k++) {
                rowKeys[k] = colIndex[k] >= 0
                    ? row.getValue(colIndex[k])
                    : keyEvaluator.evaluate(keyExprs.get(k), row);
            }
            keys.add(rowKeys);
        }

        final Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                final Object[] ka = keys.get(a);
                final Object[] kb = keys.get(b);
                for (int k = 0; k < nKeys; k++) {
                    final int cmp = ValueComparisons.compareOrderKey(ka[k], kb[k], ascending[k], nullsFirst[k]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        final List<Row> sorted = new ArrayList<>(rows.size());
        for (final int i : order) {
            sorted.add(rows.get(i));
        }
        return sorted;
    }

    /** The SELECT column index an ORDER BY expression matches (position, alias, or expression text), or -1. */
    private int matchOrderItem(final String orderExpr, final List<FrostlakeParser.SelectItemContext> selectItems,
                               final boolean setOperation) {
        // ORDER BY <n> positional reference → the N-th SELECT column.
        if (orderExpr.matches("\\d+")) {
            final int ord = Integer.parseInt(orderExpr);
            if (ord >= 1 && ord <= selectItems.size()) {
                return ord - 1;
            }
        }
        // Match by alias.
        for (int i = 0; i < selectItems.size(); i++) {
            if (SelectItemAccessors.getItemAlias(selectItems.get(i)) != null) {
                final String alias = ParseTreeText.getIdentifier(SelectItemAccessors.getItemAlias(selectItems.get(i)));
                if (alias.equalsIgnoreCase(orderExpr)) {
                    return i;
                }
            }
        }
        // Match by expression text (case-sensitive, then insensitive).
        for (int i = 0; i < selectItems.size(); i++) {
            final ParserRuleContext e = SelectItemAccessors.getItemExpression(selectItems.get(i));
            if (e != null && e.getText().equals(orderExpr)) {
                return i;
            }
        }
        for (int i = 0; i < selectItems.size(); i++) {
            final ParserRuleContext e = SelectItemAccessors.getItemExpression(selectItems.get(i));
            if (e != null && e.getText().equalsIgnoreCase(orderExpr)) {
                return i;
            }
        }
        // Match by the RESULT column NAME the item produces — ONLY for a set operation, whose output
        // columns take their names from the first branch and are the only thing its ORDER BY can name
        // (`… UNION ALL … ORDER BY region_id`, which matches neither an alias nor the item's
        // expression text `a.region_id`). A single SELECT keeps its existing resolution: there an
        // unmatched key is handed to the group resolver, which computes it over the row's group, and
        // hijacking that to a projected column changes the sort key of working queries.
        for (int i = 0; setOperation && i < selectItems.size(); i++) {
            final String produced = producedColumnName(selectItems.get(i));
            if (produced != null && produced.equalsIgnoreCase(orderExpr)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The result-column name an un-aliased select item produces, taken from the parse tree: the last part of
     * a (possibly qualified) column reference. Null for anything else — an expression has no natural name, and
     * {@code SELECT *} has no single one.
     */
    private String producedColumnName(final FrostlakeParser.SelectItemContext item) {
        if (!SelectItemAccessors.isExprItem(item)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        if (!(valueExpr instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) valueExpr).qualifiedName());
        return parts.length == 0 ? null : parts[parts.length - 1];
    }

    /**
     * The ORDER BY items that do NOT match any SELECT column (by position, alias, or expression text). For a
     * window-function query these must be computed from the FROM columns before projection drops them — the
     * caller precomputes their values during the window projection (see the resolver in QueryExecutor).
     */
    List<FrostlakeParser.OrderItemContext> unmatchedOrderItems(final FrostlakeParser.SelectStatementContext ctx) {
        final List<FrostlakeParser.OrderItemContext> unmatched = new ArrayList<>();
        if (ctx.orderByClause() == null) {
            return unmatched;
        }
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();
        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            if (matchOrderItem(item.expression().getText(), selectItems, ctx.selectOperand().size() > 1) == -1) {
                unmatched.add(item);
            }
        }
        return unmatched;
    }
}
