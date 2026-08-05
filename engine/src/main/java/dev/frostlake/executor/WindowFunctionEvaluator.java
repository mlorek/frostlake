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

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.aggregate.ApproxPercentileAccumulator;
import dev.frostlake.functions.aggregate.Corr;
import dev.frostlake.functions.aggregate.CovarAccumulator;
import dev.frostlake.functions.aggregate.ListAggAccumulator;
import dev.frostlake.functions.aggregate.MaxByMinByAccumulator;
import dev.frostlake.functions.aggregate.ObjectAggAccumulator;
import dev.frostlake.functions.aggregate.RegrAccumulator;
import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.functions.window.WindowFunctionHelper;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantJsonNulls;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Window-function query stage extracted from {@link QueryExecutor}: OVER-clause partitioning and
 * ordering, the ranking / navigation / value / aggregate window functions, window frames, and the
 * aggregate/window detection predicates. Pure parse-tree/value work is delegated to the stateless
 * {@link ParseTreeText} and {@link ValueComparisons} helpers; the few callbacks that need engine
 * state (expression evaluation, the function registry) are reached through the owning executor.
 */
final class WindowFunctionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(WindowFunctionEvaluator.class);

    private final QueryExecutor executor;

    // SELECT-list alias -> its defining expression text, for the window computation currently running. A
    // window PARTITION BY / ORDER BY may reference a SELECT alias (e.g. QUALIFY ROW_NUMBER() OVER
    // (PARTITION BY <alias> ...)); when such a key isn't a base column it resolves to this expression.
    // Set/restored around computeWindowFunctions so a nested subquery's window functions don't clobber it;
    // empty outside a window computation, so evaluateOrderKey behaves exactly as before there.
    private Map<String, String> windowSelectAliases = new HashMap<>();

    // Canonical AST print of each SELECT item -> projected column index, for the window computation
    // currently running over ALREADY-PROJECTED (grouped) rows; empty otherwise. Lets a PARTITION BY /
    // ORDER BY key that IS a select item — OVER (ORDER BY SUM(amount) DESC) — read the computed value.
    private Map<String, Integer> windowSelectItemCanonicalIndex = new HashMap<>();
    private final Deque<Map<String, Integer>> savedCanonicalScopes = new ArrayDeque<>();

    WindowFunctionEvaluator(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Collect window-function calls ({@code fn(...) OVER (...)}) anywhere in a parse subtree. Does not
     * descend into a window call's own OVER spec — those expressions belong to the window definition,
     * not the surrounding predicate.
     */
    void collectWindowFunctionCalls(final ParseTree node,
                                            final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null) {
            out.add((FrostlakeParser.FunctionCallExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectWindowFunctionCalls(node.getChild(i), out);
        }
    }

    /**
     * Positional argument contexts of a window-function call. The function-argument grammar is a
     * {@code booleanExpr} list (a superset of {@code expression}), so a window function's args — always
     * columns / numbers — are read from here; only their original source text is used.
     */
    private static List<FrostlakeParser.BooleanExprContext> windowArgs(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        return ParseTreeText.functionBooleanArgs(funcCtx.functionArgList());
    }

    boolean isSimpleStar(final FrostlakeParser.SelectClauseContext ctx) {
        List<FrostlakeParser.SelectItemContext> items = ctx.selectList().selectItem();
        // A star carrying EXCLUDE/RENAME/REPLACE/ILIKE modifiers is NOT a pass-through: it must go through
        // projection so those modifiers reshape the row values, not just the column metadata.
        return items.size() == 1 && SelectItemAccessors.isStarItem(items.get(0))
            && SelectItemAccessors.getStarModifiers(items.get(0)).isEmpty();
    }

    boolean hasAggregateFunction(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) continue;
            if (hasAggregateFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                return true;
            }
        }
        return false;
    }

    boolean hasAggregateFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        // A null value expression means the select item is a boolean (AND/OR/NOT) projection,
        // which has no single value expression and is never itself an aggregate.
        if (expr == null) {
            return false;
        }
        return containsAggregate(expr);
    }

    /**
     * Whether {@code node}'s tree contains a call to an aggregate function belonging to THIS query. It
     * recurses through EVERY child — operators, CASE branches, and crucially function-call ARGUMENTS (the
     * {@code MIN} in {@code NVL(MIN(x), 0)}, which is nested under functionArgList/functionArg/booleanExpr,
     * not a direct expression child). Two boundaries are pruned: a nested subquery ({@code selectStatement}
     * — its aggregates are the subquery's, not this query's) and a window {@code OVER} clause (a windowed
     * call is not an aggregate, and its PARTITION / ORDER keys are not this query's aggregates).
     */
    private boolean containsAggregate(final ParseTree node) {
        if (node == null) {
            return false;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.OverClauseContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) node;
            // A call WITH an OVER clause is a WINDOW function, not an aggregate (its OVER child is pruned
            // above); one without is a candidate aggregate.
            if (funcCtx.overClause() == null
                    && executor.getFunctionRegistry().hasAggregateFunction(funcCtx.functionName().getText().toUpperCase())) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext funcCtx = (FrostlakeParser.FunctionCallStarExprContext) node;
            if (executor.getFunctionRegistry().hasAggregateFunction(funcCtx.functionName().getText().toUpperCase())) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsAggregate(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    boolean hasWindowFunction(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) continue;
            if (hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                return true;
            }
        }
        return false;
    }

    boolean hasWindowFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        if (expr == null) {
            return false;
        }
        // A window call anywhere in the expression counts — not only when the whole item IS a bare
        // fn(...) OVER (...) call, but also when one is nested in arithmetic/boolean (revenue - LAG()OVER()).
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        collectWindowFunctionCalls(expr, windowCalls);
        return !windowCalls.isEmpty();
    }

    Map<Integer, Map<Integer, Object>> computeWindowFunctions(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        return computeWindowFunctions(rows, ctx, table, false);
    }

    Map<Integer, Map<Integer, Object>> computeWindowFunctions(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx, final Table table, final boolean rowsAreProjected) {
        // Returns: Map<rowIndex, Map<selectItemIndex, windowFunctionResult>>
        Map<Integer, Map<Integer, Object>> results = new HashMap<>();

        // Expose this query's SELECT aliases so a PARTITION BY / window ORDER BY can reference one; saved and
        // restored so a nested subquery's window computation doesn't leak its aliases back out.
        final Map<String, String> savedAliases = beginWindowAliasScope(ctx, rowsAreProjected);
        try {

        // Plan-time: a FILE argument to a window aggregate is rejected before any row is computed.
        final List<FrostlakeParser.FunctionCallExprContext> selectWindowCalls = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isExprItem(item)) {
                collectWindowFunctionCalls(SelectItemAccessors.getItemValueExpr(item), selectWindowCalls);
            }
        }
        rejectFileWindowArguments(selectWindowCalls, table, executor.selectItemAliasNames(ctx));
        // …and so is a FILE or GEOSPATIAL key in the OVER spec. The per-partition call below only runs
        // once a partition is actually built, which never happens over an EMPTY input — while live
        // rejects the query at COMPILE time either way ("Expressions of type GEOGRAPHY cannot be used
        // as PARTITION BY keys", SQLSTATE 42804, on a table with no rows as on a populated one).
        for (final FrostlakeParser.FunctionCallExprContext call : selectWindowCalls) {
            if (call.overClause() != null) {
                rejectFileWindowKeys(call.overClause(), table);
            }
        }

        // Per OVER clause: rows grouped into partitions (PARTITION BY) and each partition sorted
        // (ORDER BY) exactly once, then reused across every row — instead of re-partitioning/re-sorting
        // per row. Built lazily on first use; see sortedPartitionForRow.
        Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache = new HashMap<>();

        // For each row, compute window function values
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Map<Integer, Object> rowResults = new HashMap<>();

            // Evaluate each select item
            int selectItemIdx = 0;
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (SelectItemAccessors.isExprItem(item) && hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                    Object value = evaluateWindowFunction(SelectItemAccessors.getItemValueExpr(item), rows, rowIdx, ctx, table, overCache);
                    rowResults.put(selectItemIdx, value);
                }
                selectItemIdx++;
            }

            results.put(rowIdx, rowResults);
        }

        return results;
        } finally {
            endWindowAliasScope(savedAliases);
        }
    }

    /** Make this SELECT's aliases resolvable in PARTITION BY / window ORDER BY keys for the duration of a
     *  window computation. Returns the previous scope to pass to {@link #endWindowAliasScope}. Used by both
     *  the SELECT-list window computation and the QUALIFY inline-window computation. */
    Map<String, String> beginWindowAliasScope(final FrostlakeParser.SelectClauseContext ctx) {
        return beginWindowAliasScope(ctx, false);
    }

    /**
     * As {@link #beginWindowAliasScope(FrostlakeParser.SelectClauseContext)}; with
     * {@code rowsAreProjected} the rows handed to the window stage are already in SELECT-list shape
     * (grouped/aggregated), so ALSO index each select item by its canonical AST so a PARTITION BY /
     * ORDER BY key that IS one of the items — typically a raw aggregate, OVER (ORDER BY SUM(x)) —
     * resolves to the item's already-computed value positionally.
     */
    Map<String, String> beginWindowAliasScope(final FrostlakeParser.SelectClauseContext ctx,
                                              final boolean rowsAreProjected) {
        final Map<String, String> saved = windowSelectAliases;
        savedCanonicalScopes.push(windowSelectItemCanonicalIndex);
        windowSelectAliases = buildSelectAliasMap(ctx);
        windowSelectItemCanonicalIndex =
            rowsAreProjected ? buildSelectItemCanonicalIndex(ctx) : new HashMap<>();
        return saved;
    }

    /** Restore the alias scope saved by {@link #beginWindowAliasScope}. */
    void endWindowAliasScope(final Map<String, String> saved) {
        windowSelectAliases = saved;
        windowSelectItemCanonicalIndex =
            savedCanonicalScopes.isEmpty() ? new HashMap<>() : savedCanonicalScopes.pop();
    }

    /**
     * Canonical AST print of each SELECT item's expression → its projected column index. Empty when a
     * star/spread item makes positions unpredictable. First occurrence wins on duplicates.
     */
    private Map<String, Integer> buildSelectItemCanonicalIndex(final FrostlakeParser.SelectClauseContext ctx) {
        final Map<String, Integer> index = new HashMap<>();
        final List<FrostlakeParser.SelectItemContext> items = ctx.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            final FrostlakeParser.SelectItemContext item = items.get(i);
            if (!SelectItemAccessors.isExprItem(item)) {
                return new HashMap<>();
            }
            try {
                final String canonical = AstPrinterVisitor.print(ExpressionEvaluator.parse(
                    ParseTreeText.getOriginalText(SelectItemAccessors.getItemValueExpr(item))));
                if (!index.containsKey(canonical)) {
                    index.put(canonical, i);
                }
            } catch (final RuntimeException unparseable) {
                // Leave this item unmatched; the generic evaluation paths still apply.
            }
        }
        return index;
    }

    /** Map each non-windowed SELECT item's alias (canonical) to its defining expression text, so a window
     *  PARTITION BY / ORDER BY key that names an alias can resolve to that expression. Windowed items are
     *  excluded — a partition/order key can't circularly reference the window function it partitions. */
    private Map<String, String> buildSelectAliasMap(final FrostlakeParser.SelectClauseContext ctx) {
        final Map<String, String> aliases = new HashMap<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final FrostlakeParser.IdentifierContext aliasCtx = SelectItemAccessors.getItemAlias(item);
            if (aliasCtx == null || hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                continue;
            }
            aliases.put(SqlIdentifiers.canonical(aliasCtx),
                ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
        }
        return aliases;
    }

    List<Row> addWindowFunctionsToRows(final List<Row> rows,
                                                final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table,
                                                final boolean rowsAreProjected,
                                                final List<String> extraOrderKeyExprs,
                                                final Map<Row, Object[]> extraKeyValuesOut) {
        List<Row> resultRows = new ArrayList<>();

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row originalRow = rows.get(rowIdx);
            List<Object> values = new ArrayList<>();

            // This loop IS the projection for a windowed query (QueryExecutor skips ProjectOperator when the
            // SELECT list has a window function), so it must offer the same Snowflake lateral column aliases:
            // each aliased item's value is published here for LATER items to reference, e.g.
            // `ROUND(…) AS score_band, CASE WHEN score_band > 8.9 THEN … END, ROW_NUMBER() OVER (…) AS rn`.
            // Without this an alias reference threw "Column not found" for the whole query. Fresh per row.
            final Map<String, Object> lateralAliases = new HashMap<>();

            // Add values for each select item
            int selectItemIdx = 0;
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item)) {
                    if (rowsAreProjected) {
                        if (selectItemIdx < originalRow.getValues().size()) {
                            // The grouped projection already produced this slot's value; keep the layout aligned.
                            values.add(originalRow.getValue(selectItemIdx));
                        }
                    } else if (SelectItemAccessors.isObjectStarItem(item)) {
                        // The braced star is ONE object over the row's columns, not the columns themselves.
                        values.add(executor.evaluateExpression(
                            executor.objectStarExpression(item, table, null), originalRow, table));
                    } else if (SelectItemAccessors.isStarItem(item)) {
                        // A bare star projects its effective columns one by one — never the raw row,
                        // which for a USING / NATURAL join is WIDER than the star's column list (the
                        // hidden right-side key duplicates) and would misalign every later slot.
                        for (final String starExpr : executor.bareStarExpressions(item, table)) {
                            values.add(executor.evaluateExpression(starExpr, originalRow, table));
                        }
                    } else {
                        // A qualified star (t.*) expands to the source row's columns. Dropping them left
                        // the projected row holding only the window values while the result metadata kept
                        // every column — any later positional read (an outer SELECT over the CTE)
                        // indexed past the row's end.
                        values.addAll(originalRow.getValues());
                    }
                    selectItemIdx++;
                    continue;
                }
                final Object value;
                final String exprText = ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
                final String bare = exprText.trim().toUpperCase();
                if (hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                    Map<Integer, Object> rowWindowResults = windowFunctionResults.get(rowIdx);
                    value = rowWindowResults != null && rowWindowResults.containsKey(selectItemIdx)
                        ? rowWindowResults.get(selectItemIdx) : null;
                } else if (rowsAreProjected) {
                    // GROUP BY / implicit aggregation already computed every non-window item — the row IS the
                    // SELECT-list shape. Take the value positionally: re-evaluating the item's text here sent
                    // aggregate calls (ARRAY_AGG(…)) to the scalar evaluator, which failed with
                    // "Unknown function", and would recompute expressions against the wrong table anyway.
                    value = selectItemIdx < originalRow.getValues().size()
                        ? originalRow.getValue(selectItemIdx) : null;
                } else if (lateralAliases.containsKey(bare) && !table.hasColumn(bare)) {
                    // The item IS an earlier alias: reuse that value. Recomputing is not an option when the
                    // defining item was a window function. A real column of the same name still wins.
                    value = lateralAliases.get(bare);
                } else {
                    value = executor.evaluateExpression(exprText, originalRow, table, lateralAliases);
                }
                values.add(value);
                final FrostlakeParser.IdentifierContext alias = SelectItemAccessors.getItemAlias(item);
                if (alias != null) {
                    lateralAliases.put(ParseTreeText.getIdentifier(alias).toUpperCase(), value);
                }
                selectItemIdx++;
            }

            final Row projected = new Row(values);
            resultRows.add(projected);

            // Projection drops columns not in the SELECT list, but ORDER BY may reference a FROM column that
            // is not selected. Precompute those keys now (the FROM columns are still on originalRow), keyed by
            // the projected row instance so they survive a later QUALIFY filter and can be used for sorting.
            if (extraOrderKeyExprs != null && !extraOrderKeyExprs.isEmpty()) {
                final Object[] keyVals = new Object[extraOrderKeyExprs.size()];
                for (int e = 0; e < extraOrderKeyExprs.size(); e++) {
                    keyVals[e] = executor.evaluateExpression(extraOrderKeyExprs.get(e), originalRow, table);
                }
                extraKeyValuesOut.put(projected, keyVals);
            }
        }

        return resultRows;
    }

    Object evaluateWindowFunction(final FrostlakeParser.ExpressionContext expr,
                                          final List<Row> allRows, final int currentRowIndex,
                                          final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                          final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        if (!(expr instanceof FrostlakeParser.FunctionCallExprContext)) {
            // A window function nested inside a larger expression, e.g. revenue - LAG(...) OVER (...).
            return evaluateNestedWindowExpression(expr, allRows, currentRowIndex, ctx, table, overCache);
        }

        FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
        String functionName = funcCtx.functionName().getText().toUpperCase();
        FrostlakeParser.OverClauseContext overClause = funcCtx.overClause();

        if (overClause == null) {
            // A plain function call with no OVER of its own, but its arguments may contain window calls
            // (e.g. ABS(LAG(x) OVER (...))); handle it through the nested path.
            return evaluateNestedWindowExpression(expr, allRows, currentRowIndex, ctx, table, overCache);
        }

        // Only names WindowFunctionNames declares (or a registered aggregate, which the default branch
        // below runs over the frame) are window functions. Rejecting anything else HERE, before the
        // partition work and before the switch, is what makes that set load-bearing: it is the same set
        // SHOW FUNCTIONS enumerates via FunctionRegistry.allDispatchableNames(), so a case added below
        // without declaring the name simply does not dispatch — the listing cannot silently fall behind
        // the switch again, which is how ROW_NUMBER, RANK, LAG and 11 others ended up unlisted.
        if (!WindowFunctionNames.handles(functionName)
                && executor.getFunctionRegistry().getAggregateFunction(functionName) == null) {
            throw new RuntimeException("Unsupported window function: " + functionName);
        }

        // The current row's partition (PARTITION BY), sorted by ORDER BY — grouped and sorted once per
        // OVER clause and cached, then shared read-only by every function for this window.
        List<Row> sortedPartition =
            sortedPartitionForRow(overClause, allRows.get(currentRowIndex), allRows, table, overCache);

        // Compute window function based on type
        switch (functionName) {
            case "ROW_NUMBER":
                return computeRowNumber(sortedPartition, currentRowIndex, overClause, allRows, table);
            case "RANK":
                return computeRank(sortedPartition, currentRowIndex, overClause, allRows, table);
            case "DENSE_RANK":
                return computeDenseRank(sortedPartition, currentRowIndex, overClause, allRows, table);
            case "LAG":
                return computeLag(funcCtx, sortedPartition, currentRowIndex, overClause, allRows, table);
            case "LEAD":
                return computeLead(funcCtx, sortedPartition, currentRowIndex, overClause, allRows, table);
            case "COUNT":
                return computeWindowCount(funcCtx,
                    frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "SUM":
            case "AVG":
            case "MIN":
            case "MAX":
                return computeWindowAggregate(functionName, funcCtx,
                    frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "NTILE":
                return computeNtile(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            case "PERCENT_RANK":
                return computePercentRank(sortedPartition, allRows.get(currentRowIndex), overClause, table);
            case "CUME_DIST":
                return computeCumeDist(sortedPartition, allRows.get(currentRowIndex), overClause, table);
            case "RATIO_TO_REPORT":
                return computeRatioToReport(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            case "FIRST_VALUE":
                return computeFirstValue(funcCtx,
                    valueFrameRows(functionName, overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "LAST_VALUE":
                return computeLastValue(funcCtx,
                    valueFrameRows(functionName, overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "NTH_VALUE":
                return computeNthValue(funcCtx,
                    valueFrameRows(functionName, overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "CONDITIONAL_TRUE_EVENT":
                return computeConditionalTrueEvent(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            case "CONDITIONAL_CHANGE_EVENT":
                return computeConditionalChangeEvent(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            default:
                // Any registered aggregate is usable as a window function over the frame — Snowflake allows
                // e.g. ARRAY_AGG(x) OVER (PARTITION BY g), LISTAGG, MEDIAN, … — evaluated with the same
                // accumulator the grouped path uses. DISTINCT is honoured.
                final AggregateFunction genericAgg =
                    executor.getFunctionRegistry().getAggregateFunction(functionName);
                if (genericAgg != null) {
                    return computeGenericWindowAggregate(genericAgg, funcCtx,
                        frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
                }
                throw new RuntimeException("Unsupported window function: " + functionName);
        }
    }

    /**
     * Evaluate an expression that CONTAINS one or more window-function calls but is not itself a bare
     * window call — e.g. {@code revenue - LAG(revenue) OVER (...)} or {@code 100 * RATIO_TO_REPORT(x)
     * OVER (...)}. Each window call is computed for the current row; in the parsed expression it is a
     * {@code WindowFunctionExpression} node (keyed by its source text), and the surrounding
     * arithmetic/boolean is evaluated with those nodes resolved from the result context under the same
     * key — the technique QUALIFY uses for inline window predicates. Returns null when the expression
     * contains no window call.
     */
    private Object evaluateNestedWindowExpression(final FrostlakeParser.ExpressionContext expr,
                                                  final List<Row> allRows, final int currentRowIndex,
                                                  final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                                  final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        collectWindowFunctionCalls(expr, windowCalls);
        if (windowCalls.isEmpty()) {
            return null;
        }
        final String exprText = ParseTreeText.getOriginalText(expr);
        // Each nested window call keeps its place in the expression AST (as a WindowFunctionExpression node
        // keyed by its source text); supply its per-row value through the result context under that same key
        // rather than string-substituting a synthetic name into the text and re-parsing.
        final Map<String, Object> resultContext = new HashMap<>();
        for (final FrostlakeParser.FunctionCallExprContext wfn : windowCalls) {
            final Object windowValue = evaluateWindowFunction(wfn, allRows, currentRowIndex, ctx, table, overCache);
            resultContext.put(ParseTreeText.getOriginalText(wfn), windowValue);
        }
        final ExpressionEvaluator ev = new ExpressionEvaluator(
            table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        // Over a JOIN's rows, resolve the non-window parts (e.g. o.region in
        // o.region || ROW_NUMBER() OVER (...)) with that join's alias context.
        final Map<String, Table> winAliasToTable = executor.currentWindowAliasToTable();
        if (winAliasToTable != null) {
            ev.setMultiTableContext(winAliasToTable, executor.currentWindowAllTables());
        }
        ev.setResultContext(resultContext);
        return ev.evaluate(exprText, allRows.get(currentRowIndex));
    }

    /**
     * CONDITIONAL_TRUE_EVENT(expr) OVER([PARTITION BY …] ORDER BY …) — a running count that starts at 0
     * and increments by 1 on every row (in ORDER BY order, up to and including the current row) on which
     * {@code expr} evaluates to TRUE. A row on which expr is not TRUE (including NULL) carries the current
     * count. Returns a long.
     */
    private Long computeConditionalTrueEvent(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                             final List<Row> sortedPartition, final Row currentRow,
                                             final Table table) {
        final String argExpr = conditionalEventArg(funcCtx);
        final int pos = positionInSortedPartition(sortedPartition, currentRow);
        long count = 0L;
        for (int i = 0; i <= pos; i++) {
            if (isTruthy(executor.evaluateExpression(argExpr, sortedPartition.get(i), table))) {
                count++;
            }
        }
        return count;
    }

    /**
     * CONDITIONAL_CHANGE_EVENT(expr) OVER([PARTITION BY …] ORDER BY …) — a running count that starts at 0
     * on the first row of the partition and increments by 1 each time {@code expr}'s value differs from
     * the previous row's value (in ORDER BY order, up to and including the current row). Per Snowflake,
     * "NULL values are not considered a new or changed value": a step in which either the current or the
     * previous value is NULL is not counted as a change. Returns a long.
     */
    private Long computeConditionalChangeEvent(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                               final List<Row> sortedPartition, final Row currentRow,
                                               final Table table) {
        final String argExpr = conditionalEventArg(funcCtx);
        final int pos = positionInSortedPartition(sortedPartition, currentRow);
        long count = 0L;
        Object prev = sortedPartition.isEmpty() ? null
            : executor.evaluateExpression(argExpr, sortedPartition.get(0), table);
        for (int i = 1; i <= pos; i++) {
            final Object cur = executor.evaluateExpression(argExpr, sortedPartition.get(i), table);
            if (prev != null && cur != null && ValueComparisons.compareValues(cur, prev) != 0) {
                count++;
            }
            prev = cur;
        }
        return count;
    }

    /** The single argument expression text of a CONDITIONAL_*_EVENT window call. */
    private static String conditionalEventArg(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        if (windowArgs(funcCtx).isEmpty()) {
            throw new RuntimeException("CONDITIONAL_TRUE_EVENT / CONDITIONAL_CHANGE_EVENT requires one argument");
        }
        return ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0));
    }

    /** 0-based index of {@code currentRow} within the already-sorted partition (mirrors computeRowNumber). */
    private static int positionInSortedPartition(final List<Row> sortedPartition, final Row currentRow) {
        for (int i = 0; i < sortedPartition.size(); i++) {
            if (sortedPartition.get(i).equals(currentRow)) {
                return i;
            }
        }
        return sortedPartition.size() - 1;
    }

    /** Snowflake truthiness of a CONDITIONAL_TRUE_EVENT argument: TRUE, or a non-zero number. */
    private static boolean isTruthy(final Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        final String text = value.toString().trim();
        return text.equalsIgnoreCase("true") || text.equals("1");
    }

    /**
     * The sorted partition that contains {@code currentRow}, for a given OVER clause. Partitions
     * (PARTITION BY) and their ORDER BY sort are built exactly once per OVER clause and cached in
     * {@code overCache}, then reused for every row of the window computation. With no PARTITION BY there
     * is a single partition (all rows); with no ORDER BY the partition keeps its original order.
     */
    private List<Row> sortedPartitionForRow(final FrostlakeParser.OverClauseContext overClause,
                                            final Row currentRow, final List<Row> allRows, final Table table,
                                            final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        Map<List<Object>, List<Row>> partitions = overCache.get(overClause);
        if (partitions == null) {
            partitions = buildSortedPartitions(overClause, allRows, table);
            overCache.put(overClause, partitions);
        }
        List<Row> partition = partitions.get(partitionKey(currentRow, overClause.partitionByClause(), table));
        // currentRow is one of allRows, so its key is always present; fall back defensively.
        return partition != null ? partition : allRows;
    }

    /**
     * Group all rows into partitions by the PARTITION BY key (a single partition when absent) and sort
     * each partition by the OVER ORDER BY. The sorted partition lists are shared read-only by callers.
     */
    private Map<List<Object>, List<Row>> buildSortedPartitions(final FrostlakeParser.OverClauseContext overClause,
                                                               final List<Row> allRows, final Table table) {
        rejectFileWindowKeys(overClause, table);
        Map<List<Object>, List<Row>> groups = new LinkedHashMap<>();
        for (final Row row : allRows) {
            List<Object> key = partitionKey(row, overClause.partitionByClause(), table);
            List<Row> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(row);
        }
        if (overClause.orderByClause() != null) {
            for (final Map.Entry<List<Object>, List<Row>> entry : groups.entrySet()) {
                entry.setValue(sortRowsForWindow(entry.getValue(), overClause.orderByClause(), table));
            }
        }
        return groups;
    }

    /**
     * A window spec's keys are subject to the same FILE rule as the statement-level clauses: live
     * {@code OVER (PARTITION BY f)} is "Expressions of type FILE cannot be used as
     * PARTITION BY keys" and {@code OVER (ORDER BY f)} the ORDER BY variant. Checked once per OVER
     * clause — this method runs from the partition build, which is itself cached per clause — so every
     * window path (SELECT list and inline QUALIFY alike) is covered by the one call.
     */
    /**
     * A window aggregate is subject to the same FILE rule as its plain form: live,
     * {@code MAX(f) OVER ()} fails "Function MAX does not support FILE argument type" exactly as
     * {@code MAX(f)} does. The plan-time projection walk cannot see it — a windowed call parses to a
     * {@code WindowFunctionExpression} the walk does not descend into — so the call is re-formed here
     * WITHOUT its OVER clause and put through the ordinary strict-argument checks. Called once per
     * query per window call, before any row is evaluated.
     */
    void rejectFileWindowArguments(final List<FrostlakeParser.FunctionCallExprContext> windowCalls,
                                   final Table table, final Set<String> outputAliasNames) {
        final ExpressionEvaluator checker = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        // A window argument may reference a SELECT alias (SUM(w) OVER () beside v AS w works
        // live), so the output aliases are exempt from the walk's scope rejection.
        checker.setScopeExemptNames(outputAliasNames);
        for (final FrostlakeParser.FunctionCallExprContext call : windowCalls) {
            final List<String> argTexts = new ArrayList<>();
            for (final FrostlakeParser.BooleanExprContext arg : windowArgs(call)) {
                argTexts.add(ParseTreeText.getOriginalText(arg));
            }
            if (argTexts.isEmpty()) {
                continue;
            }
            checker.validateStrictWindowed(ExpressionEvaluator.parse(
                call.functionName().getText() + "(" + String.join(", ", argTexts) + ")"));
        }
    }

    private void rejectFileWindowKeys(final FrostlakeParser.OverClauseContext overClause, final Table table) {
        final ExpressionEvaluator keyChecker = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (overClause.partitionByClause() != null) {
            for (final FrostlakeParser.ExpressionContext expr
                    : overClause.partitionByClause().expressionList().expression()) {
                keyChecker.validateKey(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(expr)), SortKeyRole.PARTITION_BY);
            }
        }
        if (overClause.orderByClause() != null) {
            for (final FrostlakeParser.OrderItemContext item : overClause.orderByClause().orderItem()) {
                keyChecker.validateKey(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(item.expression())),
                    SortKeyRole.ORDER_BY);
            }
        }
    }

    /**
     * The PARTITION BY key for a row: the value of each PARTITION BY expression (resolved as a column,
     * like the window ORDER BY keys). An empty key (no PARTITION BY) places every row in one partition.
     * List equality is value-by-value, so rows with equal keys group together.
     */
    private List<Object> partitionKey(final Row row, final FrostlakeParser.PartitionByClauseContext partitionBy,
                                      final Table table) {
        List<Object> key = new ArrayList<>();
        if (partitionBy != null) {
            for (final FrostlakeParser.ExpressionContext expr : partitionBy.expressionList().expression()) {
                key.add(ValueComparisons.canonicalGroupKeyValue(evaluateOrderKey(ParseTreeText.getOriginalText(expr), row, table)));
            }
        }
        return key;
    }

    private Long computeRowNumber(final List<Row> partitionRows, final int currentRowIndex,
                                   final FrostlakeParser.OverClauseContext overClause,
                                   final List<Row> allRows, final Table table) {
        Row currentRow = allRows.get(currentRowIndex);

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        List<Row> sortedRows = partitionRows;

        // Find position of current row in sorted partition (1-based)
        for (int i = 0; i < sortedRows.size(); i++) {
            if (sortedRows.get(i).equals(currentRow)) {
                return (long) (i + 1);
            }
        }

        // Fallback: return row number based on original position
        return (long) (currentRowIndex + 1);
    }

    private Long computeRank(final List<Row> partitionRows, final int currentRowIndex,
                             final FrostlakeParser.OverClauseContext overClause,
                             final List<Row> allRows, final Table table) {
        Row currentRow = allRows.get(currentRowIndex);

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        List<Row> sortedRows = partitionRows;

        // Find position and check for ties
        long rank = 1;
        List<Object> currentOrderKey = null;
        List<Object> previousOrderKey = null;

        for (int i = 0; i < sortedRows.size(); i++) {
            Row row = sortedRows.get(i);

            // The full ORDER BY key tuple for this row (every key, not just the first).
            if (overClause.orderByClause() != null) {
                currentOrderKey = orderKeyTuple(row, overClause.orderByClause(), table);
            }

            // Check for ties with the previous row across ALL keys.
            if (i > 0 && previousOrderKey != null && currentOrderKey != null) {
                if (!orderKeyTuplesEqual(currentOrderKey, previousOrderKey)) {
                    // Key tuple changed — rank jumps to this row's position (gaps allowed).
                    rank = i + 1;
                }
            }

            if (row.equals(currentRow)) {
                return rank;
            }

            previousOrderKey = currentOrderKey;
        }

        // Fallback
        return (long) (currentRowIndex + 1);
    }

    private Long computeDenseRank(final List<Row> partitionRows, final int currentRowIndex,
                                   final FrostlakeParser.OverClauseContext overClause,
                                   final List<Row> allRows, final Table table) {
        Row currentRow = allRows.get(currentRowIndex);

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        List<Row> sortedRows = partitionRows;

        // Find position and check for ties (dense rank has no gaps)
        long rank = 1;
        List<Object> currentOrderKey = null;
        List<Object> previousOrderKey = null;

        for (int i = 0; i < sortedRows.size(); i++) {
            Row row = sortedRows.get(i);

            // The full ORDER BY key tuple for this row (every key, not just the first).
            if (overClause.orderByClause() != null) {
                currentOrderKey = orderKeyTuple(row, overClause.orderByClause(), table);
            }

            // Check for ties with the previous row across ALL keys.
            if (i > 0 && previousOrderKey != null && currentOrderKey != null) {
                if (!orderKeyTuplesEqual(currentOrderKey, previousOrderKey)) {
                    // Key tuple changed — increment by 1 (no gaps in dense rank).
                    rank++;
                }
            }

            if (row.equals(currentRow)) {
                return rank;
            }

            previousOrderKey = currentOrderKey;
        }

        // Fallback
        return (long) (currentRowIndex + 1);
    }

    private Object computeLag(final FrostlakeParser.FunctionCallExprContext funcCtx,
                              final List<Row> partitionRows, final int currentRowIndex,
                              final FrostlakeParser.OverClauseContext overClause,
                              final List<Row> allRows, final Table table) {
        Row currentRow = allRows.get(currentRowIndex);

        // Parse LAG arguments: LAG(column_expr, offset, default_value)
        // offset defaults to 1, default_value defaults to NULL
        int offset = 1;
        Object defaultValue = null;
        String columnExpr = null;

        if (!windowArgs(funcCtx).isEmpty()) {
            List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);

            // First argument: column expression
            columnExpr = ParseTreeText.getOriginalText(args.get(0));

            // Second argument (optional): offset
            if (args.size() > 1) {
                try {
                    String offsetStr = ParseTreeText.getOriginalText(args.get(1));
                    offset = Integer.parseInt(offsetStr);
                } catch (final NumberFormatException e) {
                    logger.warn("Invalid offset for LAG function: {}", args.get(1));
                }
            }

            // Third argument (optional): default value
            if (args.size() > 2) {
                String defaultStr = ParseTreeText.getOriginalText(args.get(2));
                defaultValue = parseLiteralValue(defaultStr);
            }
        }

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        List<Row> sortedRows = partitionRows;

        // Find position of current row in sorted partition
        int currentPosition = -1;
        for (int i = 0; i < sortedRows.size(); i++) {
            if (sortedRows.get(i).equals(currentRow)) {
                currentPosition = i;
                break;
            }
        }

        if (currentPosition == -1) {
            return defaultValue;
        }

        // Calculate the LAG position (backward)
        int lagPosition = currentPosition - offset;

        // If out of bounds, return default value
        if (lagPosition < 0 || lagPosition >= sortedRows.size()) {
            return defaultValue;
        }

        // Get the row at LAG position
        Row lagRow = sortedRows.get(lagPosition);

        // Extract the column value from the LAG row
        return extractColumnValue(lagRow, columnExpr, table);
    }

    private Object computeLead(final FrostlakeParser.FunctionCallExprContext funcCtx,
                               final List<Row> partitionRows, final int currentRowIndex,
                               final FrostlakeParser.OverClauseContext overClause,
                               final List<Row> allRows, final Table table) {
        Row currentRow = allRows.get(currentRowIndex);

        // Parse LEAD arguments: LEAD(column_expr, offset, default_value)
        // offset defaults to 1, default_value defaults to NULL
        int offset = 1;
        Object defaultValue = null;
        String columnExpr = null;

        if (!windowArgs(funcCtx).isEmpty()) {
            List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);

            // First argument: column expression
            columnExpr = ParseTreeText.getOriginalText(args.get(0));

            // Second argument (optional): offset
            if (args.size() > 1) {
                try {
                    String offsetStr = ParseTreeText.getOriginalText(args.get(1));
                    offset = Integer.parseInt(offsetStr);
                } catch (final NumberFormatException e) {
                    logger.warn("Invalid offset for LEAD function: {}", args.get(1));
                }
            }

            // Third argument (optional): default value
            if (args.size() > 2) {
                String defaultStr = ParseTreeText.getOriginalText(args.get(2));
                defaultValue = parseLiteralValue(defaultStr);
            }
        }

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        List<Row> sortedRows = partitionRows;

        // Find position of current row in sorted partition
        int currentPosition = -1;
        for (int i = 0; i < sortedRows.size(); i++) {
            if (sortedRows.get(i).equals(currentRow)) {
                currentPosition = i;
                break;
            }
        }

        if (currentPosition == -1) {
            return defaultValue;
        }

        // Calculate the LEAD position (forward)
        int leadPosition = currentPosition + offset;

        // If out of bounds, return default value
        if (leadPosition < 0 || leadPosition >= sortedRows.size()) {
            return defaultValue;
        }

        // Get the row at LEAD position
        Row leadRow = sortedRows.get(leadPosition);

        // Extract the column value from the LEAD row
        return extractColumnValue(leadRow, columnExpr, table);
    }

    private Long computeNtile(final FrostlakeParser.FunctionCallExprContext funcCtx,
                               final List<Row> sortedPartition, final Row currentRow,
                               final Table table) {
        int buckets = 1;
        if (!windowArgs(funcCtx).isEmpty()) {
            try { buckets = Integer.parseInt(ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0))); }
            catch (final NumberFormatException ignored) {}
        }
        return WindowFunctionHelper.ntile(sortedPartition, currentRow, buckets);
    }

    private Double computePercentRank(final List<Row> sortedPartition, final Row currentRow,
                                       final FrostlakeParser.OverClauseContext overClause,
                                       final Table table) {
        List<Object> orderVals = extractOrderValues(sortedPartition, overClause, table);
        return WindowFunctionHelper.percentRank(sortedPartition, currentRow, orderVals);
    }

    private Double computeCumeDist(final List<Row> sortedPartition, final Row currentRow,
                                    final FrostlakeParser.OverClauseContext overClause,
                                    final Table table) {
        List<Object> orderVals = extractOrderValues(sortedPartition, overClause, table);
        return WindowFunctionHelper.cumeDist(sortedPartition, currentRow, orderVals);
    }

    private Object computeRatioToReport(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                         final List<Row> sortedPartition, final Row currentRow,
                                         final Table table) {
        String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        Object curVal = extractColumnValue(currentRow, colExpr, table);
        List<Object> allVals = new ArrayList<>();
        for (final Row r : sortedPartition) allVals.add(extractColumnValue(r, colExpr, table));
        return WindowFunctionHelper.ratioToReport(curVal, allVals);
    }

    private Object computeFirstValue(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                      final List<Row> frameRows, final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        final List<Object> values = new ArrayList<>();
        for (final Row r : frameRows) {
            values.add(extractColumnValue(r, colExpr, table));
        }
        return WindowFunctionHelper.firstValue(values, ignoreNulls(funcCtx));
    }

    private Object computeLastValue(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                     final List<Row> frameRows, final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        final List<Object> values = new ArrayList<>();
        for (final Row r : frameRows) {
            values.add(extractColumnValue(r, colExpr, table));
        }
        return WindowFunctionHelper.lastValue(values, ignoreNulls(funcCtx));
    }

    /** Whether a window value function carries an explicit {@code IGNORE NULLS} clause (default RESPECT).
     *  The clause may sit inside the argument parens or between the call and OVER — both grammar
     *  positions land in the same list (at most one is present in a valid call). */
    private boolean ignoreNulls(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        for (final FrostlakeParser.NullHandlingContext nullHandling : funcCtx.nullHandling()) {
            if (nullHandling.IGNORE() != null) {
                return true;
            }
        }
        return false;
    }

    private Object computeNthValue(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                    final List<Row> sortedPartition, final Table table) {
        if (windowArgs(funcCtx).size() < 2) return null;
        String colExpr = ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0));
        int n = 1;
        try { n = Integer.parseInt(ParseTreeText.getOriginalText(windowArgs(funcCtx).get(1))); }
        catch (final NumberFormatException ignored) {}
        List<Object> vals = new ArrayList<>();
        for (final Row r : sortedPartition) vals.add(extractColumnValue(r, colExpr, table));
        if (ignoreNulls(funcCtx)) {
            final List<Object> nonNull = new ArrayList<>();
            for (final Object v : vals) {
                if (v != null) {
                    nonNull.add(v);
                }
            }
            vals = nonNull;
        }
        if (funcCtx.LAST() != null) {
            // NTH_VALUE(x, n) FROM LAST: the n-th value counting backwards from the partition end.
            Collections.reverse(vals);
        }
        return WindowFunctionHelper.nthValue(vals, n);
    }

    /**
     * The window frame slice (a contiguous sub-list of the sorted partition) for the current row, per the
     * OVER clause's frame. With no explicit frame the default is the whole partition when there is no ORDER
     * BY, otherwise {@code RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW} — a running, peer-aware window
     * (Snowflake's default). ROWS bounds are positional; RANGE supports UNBOUNDED and CURRENT ROW
     * (peer-aware). Frame-sensitive functions (SUM/AVG/MIN/MAX/COUNT, FIRST_VALUE/LAST_VALUE/NTH_VALUE) use
     * this; ranking and LAG/LEAD ignore the frame.
     */
    /**
     * The frame for the VALUE window functions (FIRST_VALUE / LAST_VALUE / NTH_VALUE), whose
     * Snowflake defaults differ from the aggregates: ORDER BY is REQUIRED ("Window function type
     * [NTH_VALUE] requires ORDER BY in window specification."), and with no explicit frame the
     * default is the WHOLE partition — LAST_VALUE(x) OVER (ORDER BY y) is the partition's last
     * value on every row (both live-verified). An explicit frame is honoured normally.
     */
    private List<Row> valueFrameRows(final String functionName,
                                     final FrostlakeParser.OverClauseContext overClause,
                                     final List<Row> partition, final Row currentRow, final Table table) {
        if (overClause.orderByClause() == null) {
            throw new RuntimeException("Window function type [" + functionName
                + "] requires ORDER BY in window specification.");
        }
        if (overClause.windowFrame() == null) {
            return partition;
        }
        return frameRows(overClause, partition, currentRow, table);
    }

    private List<Row> frameRows(final FrostlakeParser.OverClauseContext overClause, final List<Row> partition,
                                final Row currentRow, final Table table) {
        final int size = partition.size();
        if (size == 0) {
            return partition;
        }
        int pos = WindowFunctionHelper.indexOf(partition, currentRow);
        if (pos < 0) {
            pos = size - 1;
        }
        final FrostlakeParser.WindowFrameContext frame = overClause.windowFrame();
        final boolean hasOrderBy = overClause.orderByClause() != null;

        if (frame == null) {
            if (!hasOrderBy) {
                return partition;   // no ORDER BY ⇒ the frame is the whole partition
            }
            // Default running frame: UNBOUNDED PRECEDING … CURRENT ROW (RANGE ⇒ through the last peer).
            return partition.subList(0, lastPeer(partition, pos, overClause, table) + 1);
        }

        final boolean isRange = frame.RANGE() != null;
        final List<FrostlakeParser.FrameBoundContext> bounds = frame.frameBound();
        final int start = boundIndex(partition, pos, bounds.get(0), overClause, table, isRange, true);
        final int end = bounds.size() > 1
            ? boundIndex(partition, pos, bounds.get(1), overClause, table, isRange, false)
            : (isRange ? lastPeer(partition, pos, overClause, table) : pos);   // single bound ⇒ … AND CURRENT ROW

        final int s = Math.max(0, start);
        final int e = Math.min(size - 1, end);
        if (s > e) {
            return new ArrayList<>();   // an empty frame (e.g. 2 FOLLOWING at the end of the partition)
        }
        return partition.subList(s, e + 1);
    }

    /** Resolve one frame bound to an inclusive index into the sorted partition. ROWS bounds are positional;
     *  RANGE bounds support UNBOUNDED and CURRENT ROW (peer-aware) — a RANGE numeric offset is rejected. */
    private int boundIndex(final List<Row> partition, final int pos, final FrostlakeParser.FrameBoundContext bound,
                           final FrostlakeParser.OverClauseContext overClause, final Table table,
                           final boolean isRange, final boolean isStart) {
        if (bound.UNBOUNDED() != null) {
            return bound.PRECEDING() != null ? 0 : partition.size() - 1;
        }
        if (bound.CURRENT() != null) {   // CURRENT ROW
            if (!isRange) {
                return pos;
            }
            return isStart ? firstPeer(partition, pos, overClause, table)
                           : lastPeer(partition, pos, overClause, table);
        }
        // expression PRECEDING | expression FOLLOWING
        if (isRange) {
            return rangeBoundIndex(partition, pos, bound, overClause, table, isStart);
        }
        final int n = parseFrameOffset(bound.expression());
        return bound.PRECEDING() != null ? pos - n : pos + n;
    }

    /**
     * Resolve a RANGE numeric-offset bound to an index: the frame is VALUE-based, so include rows whose
     * (single, numeric) ORDER BY value is within {@code offset} of the current row's value on the
     * PRECEDING/FOLLOWING side. Handles ASC and DESC. A START bound returns the first included index; an END
     * bound the last (both scan the already-sorted partition). Requires a single numeric ORDER BY column.
     */
    private int rangeBoundIndex(final List<Row> partition, final int pos, final FrostlakeParser.FrameBoundContext bound,
                                final FrostlakeParser.OverClauseContext overClause, final Table table, final boolean isStart) {
        final Object curVal = getOrderByValue(partition.get(pos), overClause.orderByClause(), table);
        if (!(curVal instanceof Number)) {
            throw new RuntimeException("RANGE frame with a numeric offset requires a single numeric ORDER BY column");
        }
        final double offset;
        try {
            offset = Double.parseDouble(ParseTreeText.getOriginalText(bound.expression()).trim());
        } catch (final NumberFormatException e) {
            throw new RuntimeException("RANGE frame offset must be a numeric constant: "
                + ParseTreeText.getOriginalText(bound.expression()));
        }
        final double v = ((Number) curVal).doubleValue();
        final boolean asc = overClause.orderByClause().orderItem().get(0).DESC() == null;
        final boolean preceding = bound.PRECEDING() != null;
        final double threshold = preceding ? (asc ? v - offset : v + offset) : (asc ? v + offset : v - offset);
        if (isStart) {
            for (int i = 0; i < partition.size(); i++) {
                if (rangeValueAtOrBeyond(partition.get(i), overClause, table, threshold, asc, true)) {
                    return i;
                }
            }
            return partition.size();   // nothing qualifies ⇒ empty frame (start > end)
        }
        int last = -1;
        for (int i = 0; i < partition.size(); i++) {
            if (rangeValueAtOrBeyond(partition.get(i), overClause, table, threshold, asc, false)) {
                last = i;
            }
        }
        return last;
    }

    /** Whether a row's ORDER BY value is on the included side of {@code threshold} for a RANGE start/end bound. */
    private boolean rangeValueAtOrBeyond(final Row row, final FrostlakeParser.OverClauseContext overClause,
                                         final Table table, final double threshold, final boolean asc, final boolean isStart) {
        final Object o = getOrderByValue(row, overClause.orderByClause(), table);
        if (!(o instanceof Number)) {
            return false;
        }
        final double x = ((Number) o).doubleValue();
        // START keeps values on the far side of the lower edge; END keeps values up to the upper edge.
        if (isStart) {
            return asc ? x >= threshold : x <= threshold;
        }
        return asc ? x <= threshold : x >= threshold;
    }

    private int parseFrameOffset(final FrostlakeParser.ExpressionContext expr) {
        try {
            return Integer.parseInt(ParseTreeText.getOriginalText(expr).trim());
        } catch (final NumberFormatException e) {
            throw new RuntimeException("window frame offset must be a non-negative integer: " + ParseTreeText.getOriginalText(expr));
        }
    }

    /** First index whose ORDER BY value equals the current row's (its first peer); 0 when there is no ORDER BY. */
    private int firstPeer(final List<Row> partition, final int pos, final FrostlakeParser.OverClauseContext overClause,
                          final Table table) {
        if (overClause.orderByClause() == null) {
            return 0;
        }
        final List<Object> cur = orderKeyTuple(partition.get(pos), overClause.orderByClause(), table);
        int i = pos;
        while (i > 0 && orderKeyTuplesEqual(orderKeyTuple(partition.get(i - 1), overClause.orderByClause(), table), cur)) {
            i--;
        }
        return i;
    }

    /** Last index whose ORDER BY value equals the current row's (its last peer); the last index when there
     *  is no ORDER BY. */
    private int lastPeer(final List<Row> partition, final int pos, final FrostlakeParser.OverClauseContext overClause,
                         final Table table) {
        if (overClause.orderByClause() == null) {
            return partition.size() - 1;
        }
        final List<Object> cur = orderKeyTuple(partition.get(pos), overClause.orderByClause(), table);
        int i = pos;
        while (i < partition.size() - 1
                && orderKeyTuplesEqual(orderKeyTuple(partition.get(i + 1), overClause.orderByClause(), table), cur)) {
            i++;
        }
        return i;
    }

    /**
     * Window aggregates SUM/AVG/MIN/MAX over the partition. Computed over the whole partition (Snowflake's
     * default frame when there is no ORDER BY), matching this engine's COUNT and RATIO_TO_REPORT window
     * behaviour; running/cumulative frames (ORDER BY … ROWS/RANGE) are not yet modelled here.
     */
    private Object computeWindowAggregate(final String functionName, final FrostlakeParser.FunctionCallExprContext funcCtx,
                                          final List<Row> sortedPartition, final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        final List<Object> values = new ArrayList<>();
        for (final Row r : sortedPartition) {
            // Same JSON-null rule as the grouped path and the generic window dispatch: a VARIANT
            // JSON null is missing input for SUM/AVG and a VALUE for MIN/MAX — without this a
            // windowed AVG counted the JSON null as 0 in its divisor (grouped AVG skips it).
            values.add(colExpr == null ? null
                : VariantJsonNulls.asAggregateInput(functionName, extractColumnValue(r, colExpr, table)));
        }
        switch (functionName) {
            case "SUM": return WindowFunctionHelper.sum(values,
                isStaticallyVariantArgument(colExpr, table));
            case "AVG": return WindowFunctionHelper.avg(values,
                isStaticallyVariantArgument(colExpr, table));
            case "MIN": return WindowFunctionHelper.min(values);
            case "MAX": return WindowFunctionHelper.max(values);
            default:    return null;
        }
    }

    /**
     * Whether the window aggregate's ARGUMENT is declared VARIANT — live, {@code SUM(v:b) OVER ()}
     * and {@code AVG(v:b) OVER ()} are DOUBLE (SYSTEM$TYPEOF FLOAT) exactly like the grouped forms,
     * even though path extraction hands the engine plain integers; the declared type decides the
     * tier just as a runtime VariantValue does.
     */
    private boolean isStaticallyVariantArgument(final String argExpr, final Table table) {
        if (argExpr == null || argExpr.isEmpty()) {
            return false;
        }
        try {
            final ExpressionEvaluator ev = new ExpressionEvaluator(table,
                executor.getFunctionRegistry(), executor.getCatalog(), executor);
            return ev.inferStaticType(ExpressionEvaluator.parse(argExpr)) instanceof VariantType;
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /**
     * COUNT over the frame. With an argument, only rows whose argument value is non-null count — and
     * a VARIANT JSON null is missing input exactly as on the grouped path (live: {@code COUNT(v:b)
     * OVER ()} is 0 over a lone JSON-null row, where this method used to answer the frame size — it
     * ignored its argument entirely, so SQL NULLs were miscounted too). A star call does not reach
     * this method, so an empty argument list only defends against a malformed call.
     */
    private Object computeWindowCount(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                      final List<Row> frame, final Table table) {
        final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);
        if (args.isEmpty()) {
            return (long) frame.size();
        }
        final String argExpr = ParseTreeText.getOriginalText(args.get(0));
        long count = 0;
        for (final Row r : frame) {
            if (VariantJsonNulls.asAggregateInput("COUNT", extractColumnValue(r, argExpr, table)) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * A registered aggregate applied as a window function over the current row's frame: each frame row's
     * argument value is fed to a fresh accumulator (DISTINCT drops repeats), mirroring the grouped path.
     */
    private Object computeGenericWindowAggregate(final AggregateFunction aggFunc,
                                                 final FrostlakeParser.FunctionCallExprContext funcCtx,
                                                 final List<Row> frame, final Table table) {
        final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);
        final String argExpr = !args.isEmpty() ? ParseTreeText.getOriginalText(args.get(0)) : null;
        final String secondExpr = args.size() > 1 ? ParseTreeText.getOriginalText(args.get(1)) : null;
        final AggregateFunction.Accumulator acc = aggFunc.createAccumulator();
        // Two-argument aggregates mirror the grouped path's dispatch: LISTAGG / APPROX_PERCENTILE take
        // their constant second argument up front; the pair-fed accumulators (MAX_BY / MIN_BY,
        // OBJECT_AGG, CORR / COVAR / REGR) receive both per-row values. Feeding only the first argument
        // silently returned an empty/NULL aggregate over the frame.
        if (secondExpr != null && acc instanceof ListAggAccumulator) {
            ((ListAggAccumulator) acc).setDelimiter(String.valueOf(parseLiteralValue(secondExpr)));
        } else if (secondExpr != null && acc instanceof ApproxPercentileAccumulator) {
            ((ApproxPercentileAccumulator) acc).setPercentile(new BigDecimal(secondExpr.trim()).doubleValue());
        }
        final boolean pairFed = secondExpr != null
            && (acc instanceof MaxByMinByAccumulator || acc instanceof ObjectAggAccumulator
                || acc instanceof Corr.CorrAccumulator || acc instanceof CovarAccumulator
                || acc instanceof RegrAccumulator);
        final Set<Object> seen = funcCtx.DISTINCT() != null ? new HashSet<>() : null;
        for (final Row r : frame) {
            // Same JSON-null rule as the grouped path: a VARIANT JSON null is missing input for a
            // value-computing aggregate (live: COUNT ignores it, SUM over {JSON null, 3}
            // is 3) and a VALUE for an ordering/collecting one (MAX over {JSON null, 5} is the JSON
            // null). Without this a windowed COUNT(v:b) counted rows a grouped COUNT(v:b) skipped.
            final Object v = argExpr == null ? null
                : VariantJsonNulls.asAggregateInput(aggFunc.getName(), extractColumnValue(r, argExpr, table));
            if (seen != null && v != null && !seen.add(ValueComparisons.normalizeValueForDistinct(v))) {
                continue;
            }
            if (pairFed) {
                final Object second = extractColumnValue(r, secondExpr, table);
                if (acc instanceof MaxByMinByAccumulator) {
                    ((MaxByMinByAccumulator) acc).accumulate(v, second);
                } else if (acc instanceof ObjectAggAccumulator) {
                    ((ObjectAggAccumulator) acc).accumulate(v, second);
                } else if (v != null && second != null) {
                    final double dy = WindowFunctionHelper.toDouble(v);
                    final double dx = WindowFunctionHelper.toDouble(second);
                    if (acc instanceof Corr.CorrAccumulator) {
                        ((Corr.CorrAccumulator) acc).accumulate(dy, dx);
                    } else if (acc instanceof CovarAccumulator) {
                        ((CovarAccumulator) acc).accumulate(dy, dx);
                    } else {
                        ((RegrAccumulator) acc).accumulate(dy, dx);
                    }
                }
            } else {
                acc.accumulate(v);
            }
        }
        return acc.getResult();
    }

    private List<Object> extractOrderValues(final List<Row> sortedRows,
                                             final FrostlakeParser.OverClauseContext overClause,
                                             final Table table) {
        List<Object> vals = new ArrayList<>();
        for (final Row r : sortedRows) {
            vals.add(overClause.orderByClause() != null ? getOrderByValue(r, overClause.orderByClause(), table) : null);
        }
        return vals;
    }

    private Object extractColumnValue(final Row row, final String columnExpr, final Table table) {
        if (columnExpr == null || table == null) {
            return null;
        }
        // Same resolution as ORDER BY keys: an ordinal, else the evaluated expression — so LAG / LEAD /
        // FIRST_VALUE / NTH_VALUE accept qualified names and casts (e.g. LAG(t.value::VARCHAR)), not just
        // bare column names.
        return evaluateOrderKey(columnExpr, row, table);
    }

    private Object parseLiteralValue(final String literal) {
        if (literal == null) {
            return null;
        }

        final String trimmedLiteral = literal.trim();

        // String literal
        if (trimmedLiteral.startsWith("'") && trimmedLiteral.endsWith("'")) {
            return trimmedLiteral.substring(1, trimmedLiteral.length() - 1);
        }

        // NULL
        if (trimmedLiteral.equalsIgnoreCase("NULL")) {
            return null;
        }

        // Boolean
        if (trimmedLiteral.equalsIgnoreCase("TRUE")) {
            return true;
        }
        if (trimmedLiteral.equalsIgnoreCase("FALSE")) {
            return false;
        }

        // Try as integer
        try {
            return Long.parseLong(trimmedLiteral);
        } catch (final NumberFormatException e) {
            // Not an integer
        }

        // Try as double
        try {
            return Double.parseDouble(trimmedLiteral);
        } catch (final NumberFormatException e) {
            // Not a double
        }

        // Return as string if nothing else matches
        return trimmedLiteral;
    }

    List<Row> sortRowsForWindow(final List<Row> rows, final FrostlakeParser.OrderByClauseContext orderByClause, final Table table) {
        List<Row> sorted = new ArrayList<>(rows);
        if (sorted.size() <= 1) {
            return sorted;
        }

        // Direction/nulls flags and expression texts are row-independent — resolve them ONCE.
        final List<FrostlakeParser.OrderItemContext> items = orderByClause.orderItem();
        final boolean[] ascending = new boolean[items.size()];
        final Boolean[] nullsFirst = new Boolean[items.size()];
        final String[] exprTexts = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ascending[i] = items.get(i).DESC() == null;
            nullsFirst[i] = ValueComparisons.nullsFirstFlag(items.get(i));
            exprTexts[i] = ParseTreeText.getOriginalText(items.get(i).expression());
        }

        // Evaluate each row's ORDER BY keys ONCE (so qualified names, casts, and expressions all resolve —
        // not just bare column names), then compare the cached tuples; the sort runs once per partition, so
        // re-evaluating on every comparison would be O(rows^2 log rows).
        final Map<Row, Object[]> keyCache = new IdentityHashMap<>();
        for (final Row r : sorted) {
            final Object[] keys = new Object[items.size()];
            for (int i = 0; i < items.size(); i++) {
                keys[i] = evaluateOrderKey(exprTexts[i], r, table);
            }
            keyCache.put(r, keys);
        }

        sorted.sort(new Comparator<Row>() {
            @Override
            public int compare(final Row row1, final Row row2) {
                final Object[] k1 = keyCache.get(row1);
                final Object[] k2 = keyCache.get(row2);
                for (int i = 0; i < exprTexts.length; i++) {
                    final int cmp = ValueComparisons.compareOrderKey(k1[i], k2[i], ascending[i], nullsFirst[i]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        return sorted;
    }

    /**
     * Evaluate one window ORDER BY / PARTITION BY key for a row. A bare 1-based ordinal selects a column by
     * position; otherwise the expression is EVALUATED — so qualified names ({@code t.value}), casts
     * ({@code t.value::VARCHAR}), and arbitrary expressions resolve, not only bare column names. Returns
     * null when it cannot be evaluated.
     */
    private Object evaluateOrderKey(final String exprText, final Row row, final Table table) {
        final String trimmed = exprText.trim();
        try {
            final int ordinal = Integer.parseInt(trimmed) - 1;
            if (ordinal >= 0 && ordinal < row.getValues().size()) {
                return row.getValue(ordinal);
            }
        } catch (final NumberFormatException ignored) {
            // not a positional ordinal — fall through to expression evaluation
        }
        // Grouped (projected) window stage: a key that IS one of the SELECT items — typically a raw
        // aggregate, OVER (ORDER BY SUM(amount) DESC) — reads that item's already-computed value
        // positionally. Evaluating the aggregate text as a scalar threw, and the catch below turned
        // the key into NULL, so ROW_NUMBER/RANK ordered every partition by input order instead.
        if (!windowSelectItemCanonicalIndex.isEmpty()) {
            try {
                final Integer itemIdx = windowSelectItemCanonicalIndex.get(
                    AstPrinterVisitor.print(ExpressionEvaluator.parse(trimmed)));
                if (itemIdx != null && itemIdx < row.getValues().size()) {
                    return row.getValue(itemIdx);
                }
            } catch (final RuntimeException notAnExpression) {
                // fall through to the alias / generic paths
            }
        }
        // A bare name that names a SELECT-list alias (and isn't a base column) resolves to the alias's
        // defining expression — Snowflake allows a window PARTITION BY / ORDER BY to reference a SELECT
        // alias. A real column of the same name still takes precedence. Aliases CHAIN (a priority CASE
        // defined over two sibling aliases), so the defining expression is expanded transitively —
        // evaluating it raw threw "Column not found" per row, which the catch below silently turned
        // into a NULL order key, so RANK/ROW_NUMBER saw every row as equal and QUALIFY filtered nothing.
        String toEvaluate = trimmed;
        if (!windowSelectAliases.isEmpty() && isSimpleIdentifier(trimmed)) {
            final String canon = canonicalName(trimmed);
            if (!table.hasColumn(canon) && windowSelectAliases.containsKey(canon)) {
                toEvaluate = expandAliasExpression(windowSelectAliases.get(canon), table);
            }
        }
        try {
            return executor.evaluateExpression(toEvaluate, row, table);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Transitively inline sibling select-alias references inside an alias's defining expression, so a
     * chained alias evaluates against the source row. Lexer-driven substitution keeps qualified-name
     * and {@code :path} segments untouched (an alias named like a variant path key must not explode),
     * and a real column of the same name is never substituted. Bounded passes guard cycles.
     */
    private String expandAliasExpression(final String defining, final Table table) {
        String text = defining;
        for (int pass = 0; pass < 5; pass++) {
            String next = text;
            for (final Map.Entry<String, String> alias : windowSelectAliases.entrySet()) {
                if (table != null && table.hasColumn(alias.getKey())) {
                    continue;
                }
                next = SqlIdentifierSubstitution.substitute(next, alias.getKey(), "(" + alias.getValue() + ")");
            }
            if (next.equals(text)) {
                break;
            }
            text = next;
        }
        return text;
    }

    /** True if the text is a single unqualified identifier (a bare column/alias name, possibly quoted) —
     *  i.e. a candidate for SELECT-alias resolution, unlike {@code t.col}, a cast, or an expression. */
    private static boolean isSimpleIdentifier(final String s) {
        final int n = s.length();
        if (n == 0) {
            return false;
        }
        if (n >= 2 && s.charAt(0) == '"' && s.charAt(n - 1) == '"') {
            return true;   // quoted identifier
        }
        if (Character.isDigit(s.charAt(0))) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            final char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) {
                return false;
            }
        }
        return true;
    }

    /** Canonicalize a bare identifier the same way {@link SqlIdentifiers#canonical}: strip and preserve the
     *  case of a quoted name; upper-case an unquoted one. */
    private static String canonicalName(final String raw) {
        final String t = raw.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            return t.substring(1, t.length() - 1);
        }
        return t.toUpperCase();
    }

    /** All ORDER BY key values for a row, in order — used for peer/tie detection across EVERY key. */
    private List<Object> orderKeyTuple(final Row row, final FrostlakeParser.OrderByClauseContext orderByClause,
                                       final Table table) {
        final List<Object> keys = new ArrayList<>();
        for (final FrostlakeParser.OrderItemContext item : orderByClause.orderItem()) {
            keys.add(evaluateOrderKey(ParseTreeText.getOriginalText(item.expression()), row, table));
        }
        return keys;
    }

    /** Two ORDER BY tuples are peers when every key compares equal (NULLs peer with NULLs). */
    private boolean orderKeyTuplesEqual(final List<Object> a, final List<Object> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!compareOrderValues(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The first ORDER BY key's value for a row — used by RANGE frames, which require a single numeric key. */
    private Object getOrderByValue(final Row row, final FrostlakeParser.OrderByClauseContext orderByClause, final Table table) {
        if (orderByClause.orderItem().isEmpty()) {
            return null;
        }
        return evaluateOrderKey(ParseTreeText.getOriginalText(orderByClause.orderItem().get(0).expression()), row, table);
    }

    private boolean compareOrderValues(final Object val1, final Object val2) {
        if (val1 == null && val2 == null) return true;
        if (val1 == null || val2 == null) return false;

        // Use numeric comparison for numbers
        if (val1 instanceof Number && val2 instanceof Number) {
            return Double.compare(((Number) val1).doubleValue(), ((Number) val2).doubleValue()) == 0;
        }

        return val1.equals(val2);
    }
}
