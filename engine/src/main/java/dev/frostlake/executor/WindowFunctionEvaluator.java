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

import dev.frostlake.functions.window.WindowFunctionHelper;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
        return funcCtx.functionArgList() != null ? funcCtx.functionArgList().booleanExpr() : List.of();
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
        // Skip scalar subqueries - aggregates inside them don't count as main query aggregates
        if (expr instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return false;
        }

        // Check if this expression is a function call with an aggregate function name
        if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
            FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
            // A function call with an OVER clause is a WINDOW function, not an aggregate — it must NOT
            // trigger GROUP BY collapse. The window path computes it per partition across all rows;
            // counting it as an aggregate here would implicitly group SUM/AVG/MIN/MAX OVER into one row.
            if (funcCtx.overClause() != null) {
                return false;
            }
            String funcName = funcCtx.functionName().getText().toUpperCase();
            if (executor.getFunctionRegistry().hasAggregateFunction(funcName)) {
                return true;
            }
        }
        if (expr instanceof FrostlakeParser.FunctionCallStarExprContext) {
            FrostlakeParser.FunctionCallStarExprContext funcCtx = (FrostlakeParser.FunctionCallStarExprContext) expr;
            String funcName = funcCtx.functionName().getText().toUpperCase();
            if (executor.getFunctionRegistry().hasAggregateFunction(funcName)) {
                return true;
            }
        }

        // Recursively check child expressions for binary operations, etc.
        if (expr.getChildCount() > 0) {
            for (int i = 0; i < expr.getChildCount(); i++) {
                if (expr.getChild(i) instanceof FrostlakeParser.ExpressionContext) {
                    if (hasAggregateFunctionInExpression((FrostlakeParser.ExpressionContext) expr.getChild(i))) {
                        return true;
                    }
                }
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

    private boolean hasWindowFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
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
        // Returns: Map<rowIndex, Map<selectItemIndex, windowFunctionResult>>
        Map<Integer, Map<Integer, Object>> results = new HashMap<>();

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
    }

    List<Row> addWindowFunctionsToRows(final List<Row> rows,
                                                final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table) {
        List<Row> resultRows = new ArrayList<>();

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row originalRow = rows.get(rowIdx);
            List<Object> values = new ArrayList<>();

            // Add values for each select item
            int selectItemIdx = 0;
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item)) { selectItemIdx++; continue; }
                if (hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                    Map<Integer, Object> rowWindowResults = windowFunctionResults.get(rowIdx);
                    if (rowWindowResults != null && rowWindowResults.containsKey(selectItemIdx)) {
                        values.add(rowWindowResults.get(selectItemIdx));
                    } else {
                        values.add(null);
                    }
                } else {
                    String exprText = ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
                    Object value = executor.evaluateExpression(exprText, originalRow, table);
                    values.add(value);
                }
                selectItemIdx++;
            }

            resultRows.add(new Row(values));
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
                return (long) frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table).size();
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
                    frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "LAST_VALUE":
                return computeLastValue(funcCtx,
                    frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "NTH_VALUE":
                return computeNthValue(funcCtx,
                    frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
            case "CONDITIONAL_TRUE_EVENT":
                return computeConditionalTrueEvent(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            case "CONDITIONAL_CHANGE_EVENT":
                return computeConditionalChangeEvent(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            default:
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
     * The PARTITION BY key for a row: the value of each PARTITION BY expression (resolved as a column,
     * like the window ORDER BY keys). An empty key (no PARTITION BY) places every row in one partition.
     * List equality is value-by-value, so rows with equal keys group together.
     */
    private List<Object> partitionKey(final Row row, final FrostlakeParser.PartitionByClauseContext partitionBy,
                                      final Table table) {
        List<Object> key = new ArrayList<>();
        if (partitionBy != null) {
            for (final FrostlakeParser.ExpressionContext expr : partitionBy.expressionList().expression()) {
                int colIndex = resolveWindowOrderColumn(ParseTreeText.getOriginalText(expr), table);
                key.add(colIndex >= 0 && colIndex < row.getValues().size() ? row.getValue(colIndex) : null);
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
        Object currentOrderValue = null;
        Object previousOrderValue = null;

        for (int i = 0; i < sortedRows.size(); i++) {
            Row row = sortedRows.get(i);

            // Get the ORDER BY value for this row
            if (overClause.orderByClause() != null) {
                currentOrderValue = getOrderByValue(row, overClause.orderByClause(), table);
            }

            // Check for ties with previous row
            if (i > 0 && previousOrderValue != null && currentOrderValue != null) {
                if (!compareOrderValues(currentOrderValue, previousOrderValue)) {
                    // Values are different, increment rank by number of rows with previous value
                    rank = i + 1;
                }
            }

            if (row.equals(currentRow)) {
                return rank;
            }

            previousOrderValue = currentOrderValue;
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
        Object currentOrderValue = null;
        Object previousOrderValue = null;

        for (int i = 0; i < sortedRows.size(); i++) {
            Row row = sortedRows.get(i);

            // Get the ORDER BY value for this row
            if (overClause.orderByClause() != null) {
                currentOrderValue = getOrderByValue(row, overClause.orderByClause(), table);
            }

            // Check for ties with previous row
            if (i > 0 && previousOrderValue != null && currentOrderValue != null) {
                if (!compareOrderValues(currentOrderValue, previousOrderValue)) {
                    // Values are different, increment rank by 1 (no gaps in dense rank)
                    rank++;
                }
            }

            if (row.equals(currentRow)) {
                return rank;
            }

            previousOrderValue = currentOrderValue;
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

    /** Whether a window value function carries an explicit {@code IGNORE NULLS} clause (default RESPECT). */
    private boolean ignoreNulls(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        return funcCtx.nullHandling() != null && funcCtx.nullHandling().IGNORE() != null;
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
        final Object cur = getOrderByValue(partition.get(pos), overClause.orderByClause(), table);
        int i = pos;
        while (i > 0 && compareOrderValues(getOrderByValue(partition.get(i - 1), overClause.orderByClause(), table), cur)) {
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
        final Object cur = getOrderByValue(partition.get(pos), overClause.orderByClause(), table);
        int i = pos;
        while (i < partition.size() - 1
                && compareOrderValues(getOrderByValue(partition.get(i + 1), overClause.orderByClause(), table), cur)) {
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
            values.add(colExpr == null ? null : extractColumnValue(r, colExpr, table));
        }
        switch (functionName) {
            case "SUM": return WindowFunctionHelper.sum(values);
            case "AVG": return WindowFunctionHelper.avg(values);
            case "MIN": return WindowFunctionHelper.min(values);
            case "MAX": return WindowFunctionHelper.max(values);
            default:    return null;
        }
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

        try {
            // Try as column number
            int colIndex = Integer.parseInt(columnExpr) - 1;
            if (colIndex >= 0 && colIndex < row.getValues().size()) {
                return row.getValue(colIndex);
            }
        } catch (final NumberFormatException e) {
            // Try as column name
            try {
                int colIndex = table.getColumnIndex(columnExpr);
                if (colIndex >= 0 && colIndex < row.getValues().size()) {
                    return row.getValue(colIndex);
                }
            } catch (final Exception ex) {
                logger.warn("Could not find column: {}", columnExpr);
            }
        }

        return null;
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

        // Resolve each ORDER BY item's column index + direction ONCE — these are row-independent, yet the
        // old comparator re-extracted the expression text and re-resolved the column on every comparison
        // (and this sort runs once per row, so the resolution cost was multiplied by O(rows^2 log rows)).
        final List<FrostlakeParser.OrderItemContext> items = orderByClause.orderItem();
        final int[] colIndexes = new int[items.size()];
        final boolean[] ascending = new boolean[items.size()];
        final Boolean[] nullsFirst = new Boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ascending[i] = items.get(i).DESC() == null;
            nullsFirst[i] = ValueComparisons.nullsFirstFlag(items.get(i));
            colIndexes[i] = resolveWindowOrderColumn(ParseTreeText.getOriginalText(items.get(i).expression()), table);
        }

        sorted.sort(new Comparator<Row>() {
            @Override
            public int compare(final Row row1, final Row row2) {
                for (int i = 0; i < colIndexes.length; i++) {
                    int colIndex = colIndexes[i];
                    if (colIndex >= 0 && colIndex < row1.getValues().size()) {
                        int cmp = ValueComparisons.compareOrderKey(row1.getValue(colIndex), row2.getValue(colIndex),
                            ascending[i], nullsFirst[i]);
                        if (cmp != 0) {
                            return cmp;
                        }
                    }
                }
                return 0;
            }
        });

        return sorted;
    }

    /**
     * Resolve a window ORDER BY expression to a 0-based column index: a 1-based ordinal, else a column
     * name, else -1 (unresolved — ignored in comparison, preserving the previous behavior).
     */
    private int resolveWindowOrderColumn(final String expr, final Table table) {
        try {
            return Integer.parseInt(expr) - 1;
        } catch (final NumberFormatException e) {
            if (table != null) {
                try {
                    return table.getColumnIndex(expr);
                } catch (final Exception ex) {
                    // Column not found
                }
            }
        }
        return -1;
    }

    private Object getOrderByValue(final Row row, final FrostlakeParser.OrderByClauseContext orderByClause, final Table table) {
        if (orderByClause.orderItem().isEmpty()) {
            return null;
        }

        // Get first ORDER BY expression
        FrostlakeParser.OrderItemContext item = orderByClause.orderItem().get(0);
        String expr = ParseTreeText.getOriginalText(item.expression());

        try {
            // Try as column number
            int colIndex = Integer.parseInt(expr) - 1;
            if (colIndex >= 0 && colIndex < row.getValues().size()) {
                return row.getValue(colIndex);
            }
        } catch (final NumberFormatException e) {
            // Try as column name
            if (table != null) {
                try {
                    int colIndex = table.getColumnIndex(expr);
                    if (colIndex >= 0 && colIndex < row.getValues().size()) {
                        return row.getValue(colIndex);
                    }
                } catch (final Exception ex) {
                    // Column not found
                }
            }
        }

        return null;
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
