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
import dev.frostlake.executor.operators.AggregateEvaluator;
import dev.frostlake.executor.operators.GroupByOperator;
import dev.frostlake.executor.operators.OperatorContext;
import dev.frostlake.executor.operators.RowExpressionEvaluator;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.aggregate.ApproxPercentileAccumulator;
import dev.frostlake.functions.aggregate.Corr;
import dev.frostlake.functions.aggregate.CovarAccumulator;
import dev.frostlake.functions.aggregate.ListAggAccumulator;
import dev.frostlake.functions.aggregate.MaxByMinByAccumulator;
import dev.frostlake.functions.aggregate.RegrAccumulator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GROUP BY and aggregate-evaluation query stage extracted from {@link QueryExecutor}: plain GROUP BY
 * (incl. GROUP BY ALL and positional ordinals), the ROLLUP / CUBE / GROUPING SETS super-group expansion
 * and GROUPING() bitmask, implicit (whole-relation) aggregation, and the per-group evaluation of each
 * SELECT item — either through a precomputed {@link AggregatePlan} or the general aggregate/column
 * evaluation path. Pure value/text work is delegated to the stateless {@link ValueComparisons},
 * {@link AggregateFunctions}, {@link ParseTreeText} and {@link SelectItemAccessors} helpers; the few
 * callbacks that need engine state (expression evaluation, the function registry, qualified-column
 * resolution, within-group sorting, aggregate detection) are reached through the owning executor.
 */
final class GroupByAggregateEvaluator {

    private final QueryExecutor executor;

    GroupByAggregateEvaluator(final QueryExecutor executor) {
        this.executor = executor;
    }

    List<Row> applyGroupBy(final List<Row> rows, final Table table,
                                   final FrostlakeParser.SelectClauseContext ctx,
                                   final Map<String, Table> aliasToTable,
                                   final List<Table> allTables) {
        if (ctx.groupByClause() == null) {
            return rows;
        }

        // GROUP BY ALL — group by all non-aggregate SELECT expressions
        if (ctx.groupByClause().ALL() != null) {
            List<String> groupKeys = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item)) continue;
                FrostlakeParser.ExpressionContext expr = SelectItemAccessors.getItemValueExpr(item);
                if (expr == null) continue;
                if (!executor.hasAggregateFunctionInExpression(expr)) {
                    groupKeys.add(ParseTreeText.getOriginalText(expr));
                }
            }
            if (groupKeys.isEmpty()) return rows;
            // Build a synthetic groupByClause via the existing applyGroupBy machinery:
            // collect select expressions and reuse the same evaluators
            List<String> selectExprs = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (SelectItemAccessors.isStarItem(item)) selectExprs.add("*");
                else if (SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) selectExprs.add(SelectItemAccessors.getItemQualifier(item) + ".*");
                else if (SelectItemAccessors.isExprItem(item)) selectExprs.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
            }
            final ExpressionEvaluator colEvalEv = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            colEvalEv.setMultiTableContext(aliasToTable, allTables);
            RowExpressionEvaluator colEval = (final var expr, final var row) -> colEvalEv.evaluate(expr, row);
            final List<Table> fAllTables = allTables;
            AggregateEvaluator aggEval = (final var index, final var groupRows) -> {
                FrostlakeParser.SelectItemContext item = ctx.selectList().selectItem().get(index);
                if (SelectItemAccessors.isStarItem(item)) return groupRows.get(0).getValues();
                if (SelectItemAccessors.isExprItem(item)) return evaluateGroupItem(item, groupRows, table, aliasToTable, fAllTables);
                throw new RuntimeException("Unsupported GROUP BY select item at index " + index);
            };
            OperatorContext opCtx = OperatorContext.builder()
                .table(table).functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
                .aliasToTable(aliasToTable).allTables(allTables).build();
            return new GroupByOperator(groupKeys, selectExprs, colEval, aggEval)
                .execute(rows, opCtx);
        }

        // Check if this is a super-group (ROLLUP / CUBE / GROUPING SETS)
        boolean hasSuperGroupSyntax = false;
        for (final FrostlakeParser.GroupByElementContext elem : ctx.groupByClause().groupByElement()) {
            if (elem.ROLLUP() != null || elem.CUBE() != null || elem.GROUPING() != null) {
                hasSuperGroupSyntax = true; break;
            }
        }
        List<List<String>> allGroupingSets = expandGroupByToSets(ctx.groupByClause());
        if (hasSuperGroupSyntax) {
            return applySuperGroupBy(rows, table, ctx, aliasToTable, allTables, allGroupingSets);
        }

        // Extract SELECT expressions
        List<String> selectExpressions = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) {
                selectExpressions.add("*");
            } else if (SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) {
                selectExpressions.add(SelectItemAccessors.getItemQualifier(item) + ".*");
            } else {
                selectExpressions.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
            }
        }

        // Plain GROUP BY — extract expressions from groupByElement, resolving a 1-based ordinal
        // (GROUP BY 1) to the corresponding SELECT expression.
        List<String> groupByExpressions = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext elem : ctx.groupByClause().groupByElement()) {
            if (elem.expression() != null) {
                groupByExpressions.add(resolveSelectOrdinal(ParseTreeText.getOriginalText(elem.expression()), selectExpressions));
            }
        }

        // Create column evaluator for GROUP BY expressions
        final ExpressionEvaluator groupKeyEval = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        groupKeyEval.setMultiTableContext(aliasToTable, allTables);
        RowExpressionEvaluator columnEvaluator = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return groupKeyEval.evaluate(expr, row);
            }
        };

        // Create aggregate evaluator for SELECT expressions
        // Resolve each select item's aggregate plan once (cached per index); per group just apply it
        // (a scan) instead of re-parsing the text + re-resolving the column for every group. Items the
        // plan doesn't model fall back to the general per-group evaluation.
        final List<FrostlakeParser.SelectItemContext> aggItems = ctx.selectList().selectItem();
        final AggregatePlan[] aggPlans = new AggregatePlan[aggItems.size()];
        final boolean[] aggPlanResolved = new boolean[aggItems.size()];
        AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
            @Override
            public Object evaluate(final int index, final List<Row> groupRows) {
                FrostlakeParser.SelectItemContext item = aggItems.get(index);
                if (SelectItemAccessors.isStarItem(item)) {
                    return groupRows.get(0).getValues();
                }
                if (SelectItemAccessors.isExprItem(item)) {
                    if (!aggPlanResolved[index]) {
                        aggPlans[index] = resolveAggregatePlan(SelectItemAccessors.getItemValueExpr(item), table, allTables);
                        aggPlanResolved[index] = true;
                    }
                    if (aggPlans[index] != null) {
                        return applyAggregatePlan(aggPlans[index], groupRows);
                    }
                    return evaluateGroupItem(item, groupRows, table, aliasToTable, allTables);
                }
                throw new RuntimeException("Unsupported GROUP BY select item at index " + index);
            }
        };

        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
            .aliasToTable(aliasToTable)
            .allTables(allTables)
            .build();

        // Create and execute GROUP BY operator
        GroupByOperator groupByOp = new GroupByOperator(groupByExpressions, selectExpressions,
            columnEvaluator, aggregateEvaluator);
        return groupByOp.execute(rows, context);
    }

    // ── ROLLUP / CUBE / GROUPING SETS helpers ────────────────────────────────

    /**
     * Expand groupByClause into a list of grouping-key sets.
     * Returns a single-element list for plain GROUP BY (no super-groups).
     */
    private List<List<String>> expandGroupByToSets(final FrostlakeParser.GroupByClauseContext ctx) {
        if (ctx.ALL() != null) return new ArrayList<>(); // GROUP BY ALL handled separately

        // Each GROUP BY element contributes a list of grouping sets — a plain key is a single one-element
        // set; ROLLUP/CUBE/GROUPING SETS expand to several — and the overall grouping sets are the CROSS
        // PRODUCT of the elements' lists. So "ROLLUP(a), ROLLUP(g)" = {(a,g),(a),(g),()}, not a flat union
        // (which duplicated the grand total and dropped the (a,g) detail rows).
        List<List<String>> result = new ArrayList<>();
        result.add(new ArrayList<>());   // start with one empty grouping set
        for (final FrostlakeParser.GroupByElementContext elem : ctx.groupByElement()) {
            final List<List<String>> elemSets;
            if (elem.ROLLUP() != null) {
                elemSets = expandRollup(extractGroupByColumnList(elem.groupByColumnList()));
            } else if (elem.CUBE() != null) {
                elemSets = expandCube(extractGroupByColumnList(elem.groupByColumnList()));
            } else if (elem.GROUPING() != null) {
                elemSets = expandGroupingSets(elem.groupingSetList());
            } else if (elem.expression() != null) {
                elemSets = List.of(List.of(ParseTreeText.getOriginalText(elem.expression())));
            } else {
                continue;
            }
            final List<List<String>> combined = new ArrayList<>();
            for (final List<String> acc : result) {
                for (final List<String> set : elemSets) {
                    final List<String> merged = new ArrayList<>(acc);
                    merged.addAll(set);
                    combined.add(merged);
                }
            }
            result = combined;
        }
        return result;
    }

    private List<String> extractGroupByColumnList(final FrostlakeParser.GroupByColumnListContext ctx) {
        List<String> cols = new ArrayList<>();
        if (ctx != null) {
            for (final FrostlakeParser.ExpressionContext e : ctx.expression()) {
                cols.add(ParseTreeText.getOriginalText(e));
            }
        }
        return cols;
    }

    /** ROLLUP(a,b,c) → [(a,b,c), (a,b), (a), ()] */
    private List<List<String>> expandRollup(final List<String> cols) {
        List<List<String>> sets = new ArrayList<>();
        for (int i = cols.size(); i >= 0; i--) {
            sets.add(new ArrayList<>(cols.subList(0, i)));
        }
        return sets;
    }

    /** CUBE(a,b,c) → all 2^n subsets */
    private List<List<String>> expandCube(final List<String> cols) {
        int n = cols.size();
        List<List<String>> sets = new ArrayList<>();
        for (int mask = (1 << n) - 1; mask >= 0; mask--) {
            List<String> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask >> i & 1) == 1) subset.add(cols.get(i));
            }
            sets.add(subset);
        }
        return sets;
    }

    /** GROUPING SETS((a,b),(a),(b),()) → list of explicit sets */
    private List<List<String>> expandGroupingSets(final FrostlakeParser.GroupingSetListContext ctx) {
        List<List<String>> sets = new ArrayList<>();
        if (ctx == null) { sets.add(new ArrayList<>()); return sets; }
        for (final FrostlakeParser.GroupingSetContext gs : ctx.groupingSet()) {
            List<String> set = new ArrayList<>();
            for (final FrostlakeParser.ExpressionContext e : gs.expression()) {
                set.add(ParseTreeText.getOriginalText(e));
            }
            sets.add(set);
        }
        return sets;
    }

    /**
     * Run GROUP BY once per grouping set, NULLing out absent dimension keys.
     * For each grouping set S:
     *   1. Aggregate rows using only columns in S as group keys
     *   2. In result rows, set SELECT columns that reference dimensions NOT in S to NULL
     */
    private List<Row> applySuperGroupBy(final List<Row> rows, final Table table,
                                         final FrostlakeParser.SelectClauseContext ctx,
                                         final Map<String, Table> aliasToTable,
                                         final List<Table> allTables,
                                         final List<List<String>> allGroupingSets) {
        // All SELECT expressions
        List<String> selectExprs = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) selectExprs.add("*");
            else if (SelectItemAccessors.isQualifiedStarItem(item) || SelectItemAccessors.isSpreadItem(item)) selectExprs.add(SelectItemAccessors.getItemQualifier(item) + ".*");
            else selectExprs.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
        }

        // All dimension columns across all grouping sets (for NULL-out logic)
        Set<String> allDimCols = new LinkedHashSet<>();
        for (final List<String> set : allGroupingSets) allDimCols.addAll(set);

        List<Row> combined = new ArrayList<>();

        for (final List<String> groupingSet : allGroupingSets) {
            final Set<String> activeKeys = new HashSet<>(groupingSet);

            final ExpressionEvaluator gsKeyEval = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            gsKeyEval.setMultiTableContext(aliasToTable, allTables);
            RowExpressionEvaluator columnEvaluator = (final var expr, final var row) -> {
                try { return gsKeyEval.evaluate(expr, row); }
                catch (final Exception e) { return null; }
            };

            AggregateEvaluator aggregateEvaluator = (final var index, final var groupRows) -> {
                FrostlakeParser.SelectItemContext item = ctx.selectList().selectItem().get(index);
                if (SelectItemAccessors.isStarItem(item)) return groupRows.get(0).getValues();
                if (SelectItemAccessors.isExprItem(item)) return evaluateGroupItem(item, groupRows, table, aliasToTable, allTables);
                throw new RuntimeException("Unsupported GROUP BY select item at index " + index);
            };

            OperatorContext operatorCtx = OperatorContext.builder()
                .table(table).functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
                .aliasToTable(aliasToTable).allTables(allTables).build();

            GroupByOperator op = new GroupByOperator(
                new ArrayList<>(groupingSet), selectExprs, columnEvaluator, aggregateEvaluator);

            List<Row> groupResult = op.execute(rows, operatorCtx);

            // For each result row, NULL out absent dimensions and resolve GROUPING() calls
            for (final Row r : groupResult) {
                List<Object> vals = new ArrayList<>(r.getValues());
                for (int i = 0; i < selectExprs.size(); i++) {
                    final String expr = selectExprs.get(i);
                    // selectExprs is 1:1 with selectItem(); the value-expr context drives GROUPING detection.
                    final FrostlakeParser.ExpressionContext valueExpr =
                        SelectItemAccessors.getItemValueExpr(ctx.selectList().selectItem().get(i));
                    if (allDimCols.contains(expr) && !activeKeys.contains(expr)) {
                        // Dimension column not in this grouping set → NULL
                        vals.set(i, null);
                    } else if (isGroupingCall(valueExpr)) {
                        // GROUPING(e1, …, en) / GROUPING_ID(e1, …, en) → an integer bitmask (e1 is the most
                        // significant bit): each ei contributes 1 when it is aggregated over (not in this
                        // grouping set) and 0 when it is grouped by. A single GROUPING argument gives 0 or 1;
                        // GROUPING_ID is the multi-arg mask.
                        vals.set(i, computeGroupingMask(valueExpr, activeKeys));
                    }
                }
                combined.add(new Row(vals));
            }
        }
        return combined;
    }

    /** Whether a select-item value expression is a whole {@code GROUPING(...)} or {@code GROUPING_ID(...)}
     *  call — decided from the parse tree (the function-name node), not a text prefix/suffix. */
    private static boolean isGroupingCall(final FrostlakeParser.ExpressionContext valueExpr) {
        if (valueExpr instanceof FrostlakeParser.FunctionCallExprContext) {
            final String name = ((FrostlakeParser.FunctionCallExprContext) valueExpr).functionName().getText();
            return "GROUPING".equalsIgnoreCase(name) || "GROUPING_ID".equalsIgnoreCase(name);
        }
        return false;
    }

    /**
     * Resolve a {@code GROUPING(e1, …, en)} / {@code GROUPING_ID(e1, …, en)} call to its integer bitmask
     * for the current grouping set: each argument contributes one bit — 1 when it is aggregated over (NOT
     * in the grouping set) and 0 when it is grouped by — with the FIRST argument as the most significant
     * bit. Argument matching is case-insensitive. A single argument yields 0 or 1.
     *
     * <p>The arguments are read straight from the parse tree — the parser has already split the call's
     * argument list at top-level commas — rather than by re-splitting the concatenated source text.
     */
    private static long computeGroupingMask(final FrostlakeParser.ExpressionContext groupingCall,
                                            final Set<String> activeKeys) {
        final Set<String> activeUpper = new HashSet<>();
        for (final String key : activeKeys) {
            activeUpper.add(key.toUpperCase().trim());
        }
        final List<FrostlakeParser.BooleanExprContext> args =
            groupingCall instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) groupingCall).functionArgList() != null
            ? ((FrostlakeParser.FunctionCallExprContext) groupingCall).functionArgList().booleanExpr()
            : List.of();
        long mask = 0L;
        for (int a = 0; a < args.size(); a++) {
            final String argText = ParseTreeText.getOriginalText(args.get(a)).trim().toUpperCase();
            final int bit = activeUpper.contains(argText) ? 0 : 1;
            mask |= ((long) bit) << (args.size() - 1 - a);
        }
        return mask;
    }

    List<Row> applyImplicitGroupBy(final List<Row> rows, final Table table,
                                           final FrostlakeParser.SelectClauseContext ctx,
                                           final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // Extract SELECT expressions
        List<String> selectExpressions = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) continue;
            selectExpressions.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
        }

        // Create aggregate evaluator. Thread the alias/table context through so an aggregate over a
        // table-qualified column (SUM(s.qty)) or an expression (SUM(a*b)) resolves it instead of
        // silently yielding NULL.
        AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
            @Override
            public Object evaluate(final int index, final List<Row> allRows) {
                // selectExpressions here holds only expr items, so map the index to the k-th one.
                int k = 0;
                for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                    if (!SelectItemAccessors.isExprItem(item)) continue;
                    if (k == index) {
                        return evaluateGroupItem(item, allRows, table, aliasToTable, allTables);
                    }
                    k++;
                }
                throw new RuntimeException("Select item index out of range: " + index);
            }
        };

        // Build operator context
        OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
            .build();

        // Create and execute implicit GROUP BY operator
        GroupByOperator groupByOp = GroupByOperator.createImplicit(selectExpressions, aggregateEvaluator);
        return groupByOp.execute(rows, context);
    }

    /**
     * Evaluate a select item for one group. Aggregates and bare (qualified) column references keep
     * their existing per-group handling; a complex non-aggregate projection (arithmetic, boolean,
     * function of the group keys, …) — including a boolean AND/OR/NOT item whose value expression
     * is null — is evaluated on a representative group row via the AST evaluator, since the group
     * keys it references are constant within a group.
     */
    private Object evaluateGroupItem(final FrostlakeParser.SelectItemContext item, final List<Row> groupRows,
                                     final Table table) {
        return evaluateGroupItem(item, groupRows, table, null, null);
    }

    private Object evaluateGroupItem(final FrostlakeParser.SelectItemContext item, final List<Row> groupRows,
                                     final Table table, final Map<String, Table> aliasToTable,
                                     final List<Table> allTables) {
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        if (valueExpr != null
                && (valueExpr instanceof FrostlakeParser.QualifiedNameExprContext
                    || executor.hasAggregateFunctionInExpression(valueExpr))) {
            return evaluateSelectItem(valueExpr, groupRows, table, aliasToTable, allTables);
        }
        if (groupRows.isEmpty()) {
            return null;
        }
        final ExpressionEvaluator ev = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return ev.evaluate(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)), groupRows.get(0));
    }

    /**
     * Resolve a GROUP BY select item to a reusable {@link AggregatePlan} (kind + column index + DISTINCT)
     * ONCE, so each group avoids re-parsing the text and re-resolving the column. Returns null for items
     * the plan doesn't model (generic-accumulator aggregates, multi-table / qualified columns,
     * expressions) — those use the general per-group path ({@link #evaluateSelectItem}).
     */
    private AggregatePlan resolveAggregatePlan(final FrostlakeParser.ExpressionContext expr, final Table table,
                                               final List<Table> allTables) {
        if (expr == null) {
            return null;
        }
        // COUNT(*) — a star-argument call is its own parse-tree node.
        if (expr instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return "COUNT".equalsIgnoreCase(
                    ((FrostlakeParser.FunctionCallStarExprContext) expr).functionName().getText())
                ? new AggregatePlan(AggregateKind.COUNT_STAR, -1, false) : null;
        }
        // A plain COUNT/SUM/AVG/MIN/MAX of a bare column resolves to a fast-path plan; the function name,
        // DISTINCT and argument all come from the parse tree — not from re-splitting the concatenated text.
        if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
            final String funcName = funcCtx.functionName().getText().toUpperCase();
            final boolean distinct = funcCtx.DISTINCT() != null;
            final List<String> args = aggArgTexts(funcCtx);
            final int idx = args.isEmpty() ? -1 : safeColumnIndex(table, args.get(0));
            switch (funcName) {
                case "COUNT":
                    return idx >= 0 ? new AggregatePlan(AggregateKind.COUNT, idx, distinct) : null;
                case "SUM": case "AVG":
                    return idx >= 0
                        ? new AggregatePlan("SUM".equals(funcName) ? AggregateKind.SUM : AggregateKind.AVG, idx, distinct)
                        : null;
                case "MIN":
                    return idx >= 0 ? new AggregatePlan(AggregateKind.MIN, idx, false) : null;
                case "MAX":
                    return idx >= 0 ? new AggregatePlan(AggregateKind.MAX, idx, false) : null;
                default:
                    return null;   // generic aggregate / expression → the per-group path
            }
        }
        // Plain single-table, unqualified column reference: resolve its index once. Qualified names,
        // multi-table queries and expressions fall back to the per-group path.
        final String exprText = ParseTreeText.getOriginalText(expr);
        if (!exprText.contains("(") && !exprText.contains(".") && (allTables == null || allTables.size() <= 1)) {
            final int colIndex = safeColumnIndex(table, exprText);
            return colIndex >= 0 ? new AggregatePlan(AggregateKind.COLUMN, colIndex, false) : null;
        }
        return null;
    }

    /** The original source text of each argument of a function-call select item (the parser has already
     * split the argument list at top-level commas — no manual comma/paren scanning). */
    private static List<String> aggArgTexts(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        final List<String> args = new ArrayList<>();
        if (funcCtx.functionArgList() != null) {
            for (final FrostlakeParser.BooleanExprContext arg : funcCtx.functionArgList().booleanExpr()) {
                args.add(ParseTreeText.getOriginalText(arg));
            }
        }
        return args;
    }

    /** Apply a resolved {@link AggregatePlan} to one group's rows (scan only — no parsing/resolution). */
    private Object applyAggregatePlan(final AggregatePlan plan, final List<Row> groupRows) {
        switch (plan.kind) {
            case COUNT_STAR:
                return (long) groupRows.size();
            case COLUMN:
                return groupRows.isEmpty() ? null : groupRows.get(0).getValue(plan.colIndex);
            case COUNT: {
                if (plan.distinct) {
                    final Set<Object> seen = new HashSet<>();
                    for (final Row r : groupRows) {
                        final Object v = r.getValue(plan.colIndex);
                        if (v != null) {
                            seen.add(ValueComparisons.normalizeValueForDistinct(v));
                        }
                    }
                    return (long) seen.size();
                }
                long count = 0;
                for (final Row r : groupRows) {
                    if (r.getValue(plan.colIndex) != null) {
                        count++;
                    }
                }
                return count;
            }
            case SUM: {
                final Set<Object> seen = plan.distinct ? new HashSet<>() : null;
                double sum = 0.0;
                for (final Row r : groupRows) {
                    final Object v = r.getValue(plan.colIndex);
                    if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                        sum += ((Number) v).doubleValue();
                    }
                }
                return sum;
            }
            case AVG: {
                final Set<Object> seen = plan.distinct ? new HashSet<>() : null;
                double sum = 0.0;
                int count = 0;
                for (final Row r : groupRows) {
                    final Object v = r.getValue(plan.colIndex);
                    if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                        sum += ((Number) v).doubleValue();
                        count++;
                    }
                }
                return count > 0 ? sum / count : 0.0;
            }
            case MIN: {
                Object min = null;
                for (final Row r : groupRows) {
                    final Object v = r.getValue(plan.colIndex);
                    if (v != null && (min == null || ((Comparable) v).compareTo(min) < 0)) {
                        min = v;
                    }
                }
                return min;
            }
            case MAX: {
                Object max = null;
                for (final Row r : groupRows) {
                    final Object v = r.getValue(plan.colIndex);
                    if (v != null && (max == null || ((Comparable) v).compareTo(max) > 0)) {
                        max = v;
                    }
                }
                return max;
            }
            default:
                throw new IllegalStateException("Unknown aggregate plan kind: " + plan.kind);
        }
    }

    private Object evaluateSelectItem(final FrostlakeParser.ExpressionContext expr, final List<Row> groupRows, final Table table) {
        return evaluateSelectItem(expr, groupRows, table, null, null);
    }

    private Object evaluateSelectItem(final FrostlakeParser.ExpressionContext expr, final List<Row> groupRows, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // A null value expression means a boolean (AND/OR/NOT) projection. This grouped-evaluation
        // path is column/aggregate-oriented and does not evaluate general expressions (the same
        // limitation applies to e.g. an arithmetic projection in a grouped query), so there is no
        // value to compute here; guard against an NPE rather than dereference a null context.
        if (expr == null) {
            return null;
        }
        // A star-argument aggregate — COUNT(*) — is its own parse-tree node.
        if (expr instanceof FrostlakeParser.FunctionCallStarExprContext
                && "COUNT".equalsIgnoreCase(
                    ((FrostlakeParser.FunctionCallStarExprContext) expr).functionName().getText())) {
            return (long) groupRows.size();
        }

        // A function-call select item (an aggregate): read its name, DISTINCT and arguments from the parse
        // tree rather than re-parsing the concatenated source text.
        if (expr instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
            final String funcName = funcCtx.functionName().getText().toUpperCase();
            final boolean distinct = funcCtx.DISTINCT() != null;
            final List<String> args = aggArgTexts(funcCtx);
            final String arg0 = args.isEmpty() ? "" : args.get(0);
            switch (funcName) {
                case "COUNT": {
                    final List<Object> vals = aggArgValues(arg0, groupRows, table, aliasToTable, allTables);
                    if (distinct) {
                        final Set<Object> seen = new HashSet<>();
                        for (final Object v : vals) {
                            if (v != null) {
                                seen.add(ValueComparisons.normalizeValueForDistinct(v));
                            }
                        }
                        return (long) seen.size();
                    }
                    long count = 0;
                    for (final Object v : vals) {
                        if (v != null) count++;
                    }
                    return count;
                }
                case "SUM": {
                    final Set<Object> seen = distinct ? new HashSet<>() : null;
                    double sum = 0.0;
                    boolean sawValue = false;
                    for (final Object v : aggArgValues(arg0, groupRows, table, aliasToTable, allTables)) {
                        if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                            sum += aggNumeric(v);
                            sawValue = true;
                        }
                    }
                    // SUM over no non-null rows is NULL in Snowflake, not zero.
                    return sawValue ? Double.valueOf(sum) : null;
                }
                case "AVG": {
                    final Set<Object> seen = distinct ? new HashSet<>() : null;
                    double sum = 0.0;
                    int count = 0;
                    for (final Object v : aggArgValues(arg0, groupRows, table, aliasToTable, allTables)) {
                        if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                            sum += aggNumeric(v);
                            count++;
                        }
                    }
                    // AVG over no non-null rows is NULL in Snowflake.
                    return count > 0 ? Double.valueOf(sum / count) : null;
                }
                case "MIN": {
                    Object min = null;
                    for (final Object v : aggArgValues(arg0, groupRows, table, aliasToTable, allTables)) {
                        if (v != null && (min == null || ((Comparable) v).compareTo(min) < 0)) {
                            min = v;
                        }
                    }
                    return min;
                }
                case "MAX": {
                    Object max = null;
                    for (final Object v : aggArgValues(arg0, groupRows, table, aliasToTable, allTables)) {
                        if (v != null && (max == null || ((Comparable) v).compareTo(max) > 0)) {
                            max = v;
                        }
                    }
                    return max;
                }
                case "PERCENTILE_CONT":
                case "PERCENTILE_DISC":
                    return AggregateFunctions.evaluatePercentile(funcCtx, groupRows, table);
                default:
                    if (executor.getFunctionRegistry().hasAggregateFunction(funcName)) {
                        return evaluateGenericAggregate(funcCtx, funcName, args, groupRows, table, aliasToTable, allTables);
                    }
                    break;
            }
        }

        // Not an aggregate: a regular (possibly qualified) column reference — constant within the group,
        // so read it from the first row.
        final String columnRef = ParseTreeText.getOriginalText(expr);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            try {
                return executor.getQualifiedColumnValueFromTables(groupRows.get(0), allTables, aliasToTable, columnRef);
            } catch (final Exception e) {
                return groupRows.get(0).getValue(ValueComparisons.getColumnIndex(table, columnRef));
            }
        }
        return groupRows.get(0).getValue(ValueComparisons.getColumnIndex(table, columnRef));
    }

    /** Generic accumulator-based aggregate dispatch (COUNT_IF, MEDIAN, ARRAY_AGG, LISTAGG, CORR, MAX_BY, …).
     *  All structure — name, args, WITHIN GROUP — comes from the parse tree; per-row argument values go
     *  through {@link #aggArgValues} so qualified columns / expressions / predicates are honoured. */
    private Object evaluateGenericAggregate(final FrostlakeParser.FunctionCallExprContext funcCtx, final String funcName,
                                            final List<String> args, final List<Row> groupRows, final Table table,
                                            final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final AggregateFunction aggFunc = executor.getFunctionRegistry().getAggregateFunction(funcName);
        final FrostlakeParser.OrderByClauseContext withinGroup = AggregateFunctions.withinGroupOrderBy(funcCtx);
        final List<Row> aggRows = withinGroup != null
            ? executor.sortRowsForWindow(groupRows, withinGroup, table) : groupRows;
        final AggregateFunction.Accumulator acc = aggFunc.createAccumulator();
        final boolean hasTwoArgs = args.size() >= 2 && aggFunc.getMaxArgCount() >= 2;
        if (hasTwoArgs && acc instanceof ListAggAccumulator) {
            // LISTAGG(<expr>, <delimiter>): the 2nd argument is a constant string delimiter.
            ((ListAggAccumulator) acc).setDelimiter(unquoteDelimiter(args.get(1)));
            for (final Object v : aggArgValues(args.get(0), aggRows, table, aliasToTable, allTables)) {
                acc.accumulate(v);
            }
        } else if (hasTwoArgs && acc instanceof ApproxPercentileAccumulator) {
            // APPROX_PERCENTILE(<expr>, <percentile>): the 2nd argument is a constant percentile in [0, 1].
            ((ApproxPercentileAccumulator) acc).setPercentile(new BigDecimal(args.get(1).trim()).doubleValue());
            for (final Object v : aggArgValues(args.get(0), aggRows, table, aliasToTable, allTables)) {
                acc.accumulate(v);
            }
        } else if (hasTwoArgs && acc instanceof MaxByMinByAccumulator) {
            // MAX_BY / MIN_BY(<value>, <sort_key>): both arguments are columns whose RAW values matter.
            final List<Object> values = aggArgValues(args.get(0), aggRows, table, aliasToTable, allTables);
            final List<Object> keys = aggArgValues(args.get(1), aggRows, table, aliasToTable, allTables);
            for (int i = 0; i < values.size(); i++) {
                ((MaxByMinByAccumulator) acc).accumulate(values.get(i), keys.get(i));
            }
        } else if (hasTwoArgs) {
            // CORR / COVAR_* / REGR_*(y, x): two numeric columns.
            final List<Object> ys = aggArgValues(args.get(0), aggRows, table, aliasToTable, allTables);
            final List<Object> xs = aggArgValues(args.get(1), aggRows, table, aliasToTable, allTables);
            for (int i = 0; i < ys.size(); i++) {
                final Object y = ys.get(i);
                final Object x = xs.get(i);
                if (y != null && x != null) {
                    final double dy = aggNumeric(y);
                    final double dx = aggNumeric(x);
                    if (acc instanceof Corr.CorrAccumulator) {
                        ((Corr.CorrAccumulator) acc).accumulate(dy, dx);
                    } else if (acc instanceof CovarAccumulator) {
                        ((CovarAccumulator) acc).accumulate(dy, dx);
                    } else if (acc instanceof RegrAccumulator) {
                        ((RegrAccumulator) acc).accumulate(dy, dx);
                    } else {
                        acc.accumulate(y);
                    }
                }
            }
        } else {
            // Single-argument generic accumulator (COUNT_IF, MEDIAN, ARRAY_AGG, …).
            final String singleArg = args.isEmpty() ? "" : args.get(0);
            for (final Object v : aggArgValues(singleArg, aggRows, table, aliasToTable, allTables)) {
                acc.accumulate(v);
            }
        }
        return acc.getResult();
    }

    /**
     * Per-row values of an aggregate's argument across one group. A bare column of {@code table}
     * resolves through a single column-index lookup (the fast path preserved from before); a
     * table-qualified name ({@code t.col}) or any expression ({@code a * b}, {@code fn(x)}, a predicate
     * like {@code v > 2}) is instead evaluated per row through the AST evaluator (its parse is cached),
     * using the alias/table context. Before this, such arguments hit {@code getColumnIndex}, which threw
     * "Column not found" (SUM/AVG/… → swallowed to NULL) or fell back to reading column 0 (COUNT_IF and
     * the other generic accumulators → wrong count) — so aggregates over qualified columns and
     * expressions silently produced wrong results.
     */
    private List<Object> aggArgValues(final String arg, final List<Row> groupRows, final Table table,
                                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<Object> values = new ArrayList<>(groupRows.size());
        final int colIndex = safeColumnIndex(table, arg);
        if (colIndex >= 0) {
            for (final Row r : groupRows) {
                values.add(r.getValue(colIndex));
            }
            return values;
        }
        final ExpressionEvaluator ev = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            ev.setMultiTableContext(aliasToTable, allTables);
        }
        final Expression parsed = ExpressionEvaluator.parse(arg);
        for (final Row r : groupRows) {
            try {
                values.add(ev.evaluate(parsed, r));
            } catch (final Exception e) {
                values.add(null);
            }
        }
        return values;
    }

    /** Column index of {@code col} in {@code table}, or -1 when it is not a bare column of that table. */
    private static int safeColumnIndex(final Table table, final String col) {
        try {
            return ValueComparisons.getColumnIndex(table, col);
        } catch (final RuntimeException notABareColumn) {
            return -1;
        }
    }

    /**
     * Coerce an aggregate argument value to a double for SUM/AVG. A stored column value is already a
     * {@link Number}; an expression result may be any Number subtype, or (for VARIANT/text numerics) a
     * parseable string — handle both rather than assuming a direct {@code Number} cast.
     */
    private static double aggNumeric(final Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        return new BigDecimal(v.toString()).doubleValue();
    }

    /**
     * A bare positive integer in GROUP BY is a 1-based positional reference to the N-th SELECT
     * expression; any other text is returned unchanged.
     */
    private String resolveSelectOrdinal(final String text, final List<String> selectExpressions) {
        if (text != null && text.matches("\\d+")) {
            final int n = Integer.parseInt(text);
            if (n >= 1 && n <= selectExpressions.size()) {
                return selectExpressions.get(n - 1);
            }
        }
        return text;
    }

    /**
     * Strip the surrounding single quotes from a LISTAGG delimiter literal (e.g. {@code '|'} → {@code |},
     * {@code ''} → empty), unescaping doubled quotes. A non-quoted token is returned trimmed as-is.
     */
    private String unquoteDelimiter(final String raw) {
        final String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '\'' && trimmed.charAt(trimmed.length() - 1) == '\'') {
            return SqlStringLiterals.decode(trimmed);
        }
        return trimmed;
    }
}
