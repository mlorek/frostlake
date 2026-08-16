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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.RowOrdinal;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.executor.operators.AggregateEvaluator;
import dev.frostlake.executor.operators.GroupByOperator;
import dev.frostlake.executor.operators.OperatorContext;
import dev.frostlake.executor.operators.RowExpressionEvaluator;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.MultiArgumentAccumulator;
import dev.frostlake.functions.aggregate.AggregateNumerics;
import dev.frostlake.functions.aggregate.ApproxPercentileAccumulator;
import dev.frostlake.functions.aggregate.CorrAccumulator;
import dev.frostlake.functions.aggregate.CovarAccumulator;
import dev.frostlake.functions.aggregate.ListAggAccumulator;
import dev.frostlake.functions.aggregate.MaxByMinByAccumulator;
import dev.frostlake.functions.aggregate.ObjectAggAccumulator;
import dev.frostlake.functions.aggregate.RegrAccumulator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantJsonNulls;
import java.math.BigDecimal;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    /**
     * The item's column alias — its lateral-alias name, the one a LATER item of the same SELECT list may
     * reference — or null when it has none. Star-shaped items never carry one.
     */
    private static String selectItemAlias(final FrostlakeParser.SelectItemContext item) {
        return SelectItemAccessors.isExprItem(item) ? SelectItemAccessors.getItemAlias(item) : null;
    }

    List<Row> applyGroupBy(final List<Row> rows, final Table table,
                                   final FrostlakeParser.SelectClauseContext ctx,
                                   final Map<String, Table> aliasToTable,
                                   final List<Table> allTables,
                                   final List<List<Row>> groupRowsSink) {
        if (ctx.groupByClause() == null) {
            return rows;
        }
        validateSelectItemArguments(ctx, table, aliasToTable, allTables);
        validateGroupKeyScope(ctx, table, aliasToTable, allTables);

        // GROUP BY ALL — group by all non-aggregate SELECT expressions. "Non-aggregate" includes
        // seeing an aggregate THROUGH a sibling alias (count(*) AS n, CASE WHEN n = 1 ... END: the
        // CASE is aggregate-derived and NOT a group key, exactly as Snowflake classifies it). Keys
        // keep sibling aliases substituted so their evaluation never sees an unknown alias name.
        if (ctx.groupByClause().ALL() != null) {
            final List<FrostlakeParser.SelectItemContext> allItems = ctx.selectList().selectItem();
            final List<String> itemAliases = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : allItems) {
                itemAliases.add(selectItemAlias(item));
            }
            final List<String> substNames = new ArrayList<>();
            final List<String> substExprs = new ArrayList<>();
            collectSubstitutableAliases(ctx, itemAliases, table, allTables, substNames, substExprs);
            final Set<String> aggregateAliases = new HashSet<>();
            final List<String> groupKeys = new ArrayList<>();
            for (int i = 0; i < allItems.size(); i++) {
                final FrostlakeParser.SelectItemContext item = allItems.get(i);
                if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                    // A star's columns are all non-aggregate items, so each becomes a group key.
                    for (final StarColumn sc : executor.starItemColumns(item, table, aliasToTable)) {
                        groupKeys.add(sc.getExpression());
                    }
                    continue;
                }
                if (!SelectItemAccessors.isExprItem(item)) continue;
                final FrostlakeParser.ExpressionContext expr = SelectItemAccessors.getItemValueExpr(item);
                if (expr == null) continue;
                final String text = ParseTreeText.getOriginalText(expr);
                final boolean aggregateDerived = executor.hasAggregateFunctionInExpression(expr)
                    || executor.hasWindowFunctionInExpression(expr)
                    || referencesAnyIdentifier(text, aggregateAliases);
                if (aggregateDerived) {
                    if (itemAliases.get(i) != null) {
                        aggregateAliases.add(itemAliases.get(i));
                    }
                    continue;
                }
                groupKeys.add(substituteNestedAliases(text, substNames, substExprs));
            }
            if (groupKeys.isEmpty()) {
                // Every select item is aggregate-derived: live folds the whole input into ONE
                // grand-total group (GROUP BY ALL over only aggregates behaves as global
                // aggregation). A constant key routes that through the same grouping machinery;
                // a digit string would be misread as a positional ordinal, so a quoted literal.
                groupKeys.add("'all'");
            }
            // Build a synthetic groupByClause via the existing applyGroupBy machinery:
            // collect select expressions and reuse the same evaluators
            final List<String> selectExprs = new ArrayList<>();
            final List<String> allAliasNames = new ArrayList<>();
            // 1:1 with selectExprs: the item each expanded entry came from, and — for a
            // star-expanded column — the column expression to read off the group's first row.
            final List<FrostlakeParser.SelectItemContext> allItemByIndex = new ArrayList<>();
            final List<String> allStarColumnByIndex = new ArrayList<>();
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                    // GROUP BY ALL groups by every non-aggregate item, so a star's columns are all
                    // grouped — expand them like the plain projection does.
                    for (final StarColumn sc : executor.starItemColumns(item, table, aliasToTable)) {
                        selectExprs.add(sc.getExpression());
                        allAliasNames.add(sc.isRenamed() ? sc.getOutputName() : null);
                        allItemByIndex.add(item);
                        allStarColumnByIndex.add(sc.getExpression());
                    }
                    continue;
                }
                if (SelectItemAccessors.isObjectStarItem(item)) selectExprs.add(executor.objectStarExpression(item, table, aliasToTable));
                else if (SelectItemAccessors.isExprItem(item)) selectExprs.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
                else continue;
                allAliasNames.add(selectItemAlias(item));   // stays 1:1 with selectExprs
                allItemByIndex.add(item);
                allStarColumnByIndex.add(null);
            }
            final ExpressionEvaluator colEvalEv = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            colEvalEv.setMultiTableContext(aliasToTable, allTables);
            final RowExpressionEvaluator colEval = new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expression, final Row row) {
                    return colEvalEv.evaluate(expression, row);
                }
            };
            final List<Table> fAllTables = allTables;
            final Map<String, Object> allLateralAliases = new HashMap<>();
            final AggregateEvaluator aggEval = new AggregateEvaluator() {
                @Override
                public Object evaluate(final int index, final List<Row> groupRows) {
                    // A star-expanded column is grouped under GROUP BY ALL, hence constant within its
                    // group: read it off the group's first row.
                    final String starColumn = allStarColumnByIndex.get(index);
                    if (starColumn != null) {
                        return colEvalEv.evaluate(ExpressionEvaluator.parse(starColumn), groupRows.get(0));
                    }
                    final FrostlakeParser.SelectItemContext item = allItemByIndex.get(index);
                    if (SelectItemAccessors.isObjectStarItem(item)) return evaluateObjectStarItem(item, groupRows, table, aliasToTable, fAllTables);
                    if (SelectItemAccessors.isExprItem(item)) return evaluateGroupItem(item, groupRows, table, aliasToTable, fAllTables, allLateralAliases);
                    throw new RuntimeException("Unsupported GROUP BY select item at index " + index);
                }
            };
            final OperatorContext opCtx = OperatorContext.builder()
                .table(table).functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
                .aliasToTable(aliasToTable).allTables(allTables).build();
            final GroupByOperator allGroupByOp = new GroupByOperator(groupKeys, selectExprs, colEval, aggEval);
            allGroupByOp.captureGroupRows(groupRowsSink);
            allGroupByOp.captureLateralAliases(allAliasNames, allLateralAliases);
            return allGroupByOp.execute(rows, opCtx);
        }

        // Extract SELECT expressions — stars EXPANDED to their effective columns, exactly as the
        // ungrouped projection expands them, so the grouped row carries one value per output column.
        // Alongside each expanded entry: its lateral-alias name, the parse-tree item it came from,
        // and (for a star-expanded column) the column expression to read off the group's first row.
        // The validator and the nested-alias collector pair aliases with the WRITTEN items instead,
        // so those receive the per-item list.
        final List<String> selectExpressions = new ArrayList<>();
        final List<String> aliasNames = new ArrayList<>();
        final List<FrostlakeParser.SelectItemContext> itemByIndex = new ArrayList<>();
        final List<String> starColumnByIndex = new ArrayList<>();
        final List<String> perItemAliases = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            perItemAliases.add(selectItemAlias(item));
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                for (final StarColumn sc : executor.starItemColumns(item, table, aliasToTable)) {
                    selectExpressions.add(sc.getExpression());
                    aliasNames.add(sc.isRenamed() ? sc.getOutputName() : null);
                    itemByIndex.add(item);
                    starColumnByIndex.add(sc.getExpression());
                }
                continue;
            }
            if (SelectItemAccessors.isObjectStarItem(item)) {
                selectExpressions.add(executor.objectStarExpression(item, table, aliasToTable));
            } else {
                selectExpressions.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
            }
            aliasNames.add(selectItemAlias(item));
            itemByIndex.add(item);
            starColumnByIndex.add(null);
        }

        // Check if this is a super-group (ROLLUP / CUBE / GROUPING SETS)
        boolean hasSuperGroupSyntax = false;
        for (final FrostlakeParser.GroupByElementContext elem : ctx.groupByClause().groupByElement()) {
            if (elem.ROLLUP() != null || elem.CUBE() != null || elem.GROUPING() != null) {
                hasSuperGroupSyntax = true;
                break;
            }
        }
        final List<List<String>> allGroupingSets = expandGroupByToSets(ctx.groupByClause());
        if (hasSuperGroupSyntax) {
            // A super-group key resolves exactly like a plain one — GROUPING SETS ((r)) over
            // `region AS r` groups by region, and GROUPING SETS ((1)) by the first select item
            // (both live-verified) — so every key of every set runs the same ordinal / alias /
            // nested-alias rewrite chain before execution.
            final List<String> superNestedNames = new ArrayList<>();
            final List<String> superNestedExprs = new ArrayList<>();
            collectSubstitutableAliases(ctx, perItemAliases, table, allTables, superNestedNames, superNestedExprs);
            final List<List<String>> resolvedSets = new ArrayList<>();
            final List<String> superKeyForms = new ArrayList<>();
            for (final List<String> groupingSet : allGroupingSets) {
                final List<String> resolvedSet = new ArrayList<>();
                for (final String rawKey : groupingSet) {
                    final String ordinalResolved = resolveSelectOrdinal(rawKey, selectExpressions);
                    final String aliasResolved =
                        resolveSelectAlias(ordinalResolved, aliasNames, selectExpressions, table, allTables);
                    final String expanded =
                        substituteNestedAliases(aliasResolved, superNestedNames, superNestedExprs);
                    resolvedSet.add(expanded);
                    superKeyForms.add(rawKey);
                    superKeyForms.add(ordinalResolved);
                    superKeyForms.add(aliasResolved);
                    superKeyForms.add(expanded);
                }
                resolvedSets.add(resolvedSet);
            }
            // Snowflake applies the SAME select-list rule to super-groups as to a plain GROUP BY,
            // with the union of all sets as the grouped keys: a column in ANY set is legal, one in
            // none is "neither an aggregate nor in the group by clause" (live-verified, including
            // through a GROUPING() argument). An all-empty union still validates — every bare
            // column is then ungrouped.
            new GroupBySelectListValidator(executor, table, aliasToTable, allTables)
                .validate(ctx, perItemAliases, superKeyForms, true);
            return applySuperGroupBy(rows, table, ctx, aliasToTable, allTables, resolvedSets,
                selectExpressions, aliasNames, itemByIndex, starColumnByIndex);
        }

        // Plain GROUP BY — extract expressions from groupByElement, resolving a 1-based ordinal
        // (GROUP BY 1) and a SELECT-list ALIAS to the corresponding SELECT expression. Aliases may
        // also appear NESTED inside a group expression (GROUP BY CASE WHEN n_members = 1 ...) —
        // substitute those with the aliased expression too, or the key evaluator sees an unknown
        // column, every such row lands in one error-key group, and the aggregation silently corrupts.
        final List<String> nestedAliasNames = new ArrayList<>();
        final List<String> nestedAliasExprs = new ArrayList<>();
        collectSubstitutableAliases(ctx, perItemAliases, table, allTables, nestedAliasNames, nestedAliasExprs);
        final List<String> groupByExpressions = new ArrayList<>();
        // Every spelling each key passed through, so the SELECT-list validation below matches an item
        // written the way the key was written — before OR after the ordinal / alias rewrites.
        final List<String> groupKeyForms = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext elem : ctx.groupByClause().groupByElement()) {
            if (elem.expression() != null) {
                final String rawKey = ParseTreeText.getOriginalText(elem.expression());
                final String ordinalResolved = resolveSelectOrdinal(rawKey, selectExpressions);
                final String aliasResolved =
                    resolveSelectAlias(ordinalResolved, aliasNames, selectExpressions, table, allTables);
                final String expanded =
                    substituteNestedAliases(aliasResolved, nestedAliasNames, nestedAliasExprs);
                groupByExpressions.add(expanded);
                groupKeyForms.add(rawKey);
                groupKeyForms.add(ordinalResolved);
                groupKeyForms.add(aliasResolved);
                groupKeyForms.add(expanded);
            }
        }

        // Snowflake compiles the SELECT list against the grouping keys: a column that is neither grouped
        // nor aggregated, and a reference to a LATER item's alias, are both compile errors there while
        // Frostlake used to hand back an arbitrary row's value or NULL.
        new GroupBySelectListValidator(executor, table, aliasToTable, allTables)
            .validate(ctx, perItemAliases, groupKeyForms, false);

        // Create column evaluator for GROUP BY expressions
        final ExpressionEvaluator groupKeyEval = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        groupKeyEval.setMultiTableContext(aliasToTable, allTables);
        final RowExpressionEvaluator columnEvaluator = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return groupKeyEval.evaluate(expr, row);
            }
        };

        // Create aggregate evaluator for SELECT expressions
        // Resolve each select item's aggregate plan once (cached per index); per group just apply it
        // (a scan) instead of re-parsing the text + re-resolving the column for every group. Items the
        // plan doesn't model fall back to the general per-group evaluation.
        final List<FrostlakeParser.SelectItemContext> aggItems = itemByIndex;
        final AggregatePlan[] aggPlans = new AggregatePlan[aggItems.size()];
        final boolean[] aggPlanResolved = new boolean[aggItems.size()];
        final Map<String, Object> lateralAliases = new HashMap<>();
        final AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
            @Override
            public Object evaluate(final int index, final List<Row> groupRows) {
                // A star-expanded column is grouped (validated), hence constant within its group:
                // read it off the group's first row.
                final String starColumn = starColumnByIndex.get(index);
                if (starColumn != null) {
                    return groupKeyEval.evaluate(ExpressionEvaluator.parse(starColumn), groupRows.get(0));
                }
                final FrostlakeParser.SelectItemContext item = aggItems.get(index);
                if (SelectItemAccessors.isObjectStarItem(item)) {
                    return evaluateObjectStarItem(item, groupRows, table, aliasToTable, allTables);
                }
                if (SelectItemAccessors.isExprItem(item)) {
                    if (!aggPlanResolved[index]) {
                        aggPlans[index] = resolveAggregatePlan(SelectItemAccessors.getItemValueExpr(item), table, allTables);
                        aggPlanResolved[index] = true;
                    }
                    if (aggPlans[index] != null) {
                        return applyAggregatePlan(aggPlans[index], groupRows);
                    }
                    return evaluateGroupItem(item, groupRows, table, aliasToTable, allTables, lateralAliases);
                }
                throw new RuntimeException("Unsupported GROUP BY select item at index " + index);
            }
        };

        // Build operator context
        final OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
            .aliasToTable(aliasToTable)
            .allTables(allTables)
            .build();

        // Create and execute GROUP BY operator
        final GroupByOperator groupByOp = new GroupByOperator(groupByExpressions, selectExpressions,
            columnEvaluator, aggregateEvaluator);
        // An aggregation policy on the source table folds its small groups away.
        final int minGroupSize = executor.minimumGroupSize(table);
        if (minGroupSize > 0) {
            final List<String> columnNames = new ArrayList<>();
            for (final TableColumn column : table.getColumns()) {
                columnNames.add(column.getName().toUpperCase(Locale.ROOT));
            }
            groupByOp.foldSmallGroups(minGroupSize, table.getAggregationEntityKey(), columnNames);
        }
        groupByOp.captureGroupRows(groupRowsSink);
        groupByOp.captureLateralAliases(aliasNames, lateralAliases);
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
        final List<String> cols = new ArrayList<>();
        if (ctx != null) {
            for (final FrostlakeParser.ExpressionContext e : ctx.expression()) {
                cols.add(ParseTreeText.getOriginalText(e));
            }
        }
        return cols;
    }

    /** ROLLUP(a,b,c) → [(a,b,c), (a,b), (a), ()] */
    private List<List<String>> expandRollup(final List<String> cols) {
        final List<List<String>> sets = new ArrayList<>();
        for (int i = cols.size(); i >= 0; i--) {
            sets.add(new ArrayList<>(cols.subList(0, i)));
        }
        return sets;
    }

    /** CUBE(a,b,c) → all 2^n subsets */
    private List<List<String>> expandCube(final List<String> cols) {
        final int n = cols.size();
        final List<List<String>> sets = new ArrayList<>();
        for (int mask = (1 << n) - 1; mask >= 0; mask--) {
            final List<String> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask >> i & 1) == 1) subset.add(cols.get(i));
            }
            sets.add(subset);
        }
        return sets;
    }

    /** GROUPING SETS((a,b),(a),(b),()) → list of explicit sets */
    private List<List<String>> expandGroupingSets(final FrostlakeParser.GroupingSetListContext ctx) {
        final List<List<String>> sets = new ArrayList<>();
        if (ctx == null) {
            sets.add(new ArrayList<>());
            return sets;
        }
        for (final FrostlakeParser.GroupingSetContext gs : ctx.groupingSet()) {
            final List<String> set = new ArrayList<>();
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
                                         final List<List<String>> allGroupingSets,
                                         final List<String> selectExprs,
                                         final List<String> aliasNames,
                                         final List<FrostlakeParser.SelectItemContext> itemByIndex,
                                         final List<String> starColumnByIndex) {
        // All dimension columns across all grouping sets (for NULL-out logic)
        final Set<String> allDimCols = new LinkedHashSet<>();
        for (final List<String> set : allGroupingSets) allDimCols.addAll(set);

        final List<Row> combined = new ArrayList<>();

        for (final List<String> groupingSet : allGroupingSets) {
            final Set<String> activeKeys = new HashSet<>(groupingSet);

            final ExpressionEvaluator gsKeyEval = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            gsKeyEval.setMultiTableContext(aliasToTable, allTables);
            final RowExpressionEvaluator columnEvaluator = new RowExpressionEvaluator() {
                @Override
                public Object evaluate(final Expression expression, final Row row) {
                    try {
                        return gsKeyEval.evaluate(expression, row);
                    } catch (final Exception e) {
                        return null;
                    }
                }
            };

            final Map<String, Object> lateralAliases = new HashMap<>();
            final AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
                @Override
                public Object evaluate(final int index, final List<Row> groupRows) {
                    // A star-expanded column is grouped (validated), hence constant within its group:
                    // read it off the group's first row.
                    final String starColumn = starColumnByIndex.get(index);
                    if (starColumn != null) {
                        return gsKeyEval.evaluate(ExpressionEvaluator.parse(starColumn), groupRows.get(0));
                    }
                    final FrostlakeParser.SelectItemContext item = itemByIndex.get(index);
                    if (SelectItemAccessors.isObjectStarItem(item)) return evaluateObjectStarItem(item, groupRows, table, aliasToTable, allTables);
                    if (SelectItemAccessors.isExprItem(item)) return evaluateGroupItem(item, groupRows, table, aliasToTable, allTables, lateralAliases);
                    throw new RuntimeException("Unsupported GROUP BY select item at index " + index);
                }
            };

            final OperatorContext operatorCtx = OperatorContext.builder()
                .table(table).functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
                .aliasToTable(aliasToTable).allTables(allTables).build();

            final GroupByOperator op = new GroupByOperator(
                new ArrayList<>(groupingSet), selectExprs, columnEvaluator, aggregateEvaluator);
            // A dimension that this grouping set aggregates over is NULLed out below, so it must not be
            // offered to later items either — otherwise LOWER(n) on a ROLLUP subtotal row would still see the
            // representative row's value while N itself prints NULL. Withholding the alias leaves it
            // unresolved, which is the NULL the subtotal row should carry.
            final List<String> setAliasNames = new ArrayList<>(aliasNames);
            for (int i = 0; i < setAliasNames.size(); i++) {
                final String expr = selectExprs.get(i);
                if (allDimCols.contains(expr) && !activeKeys.contains(expr)) {
                    setAliasNames.set(i, null);
                }
            }
            op.captureLateralAliases(setAliasNames, lateralAliases);

            final List<Row> groupResult = op.execute(rows, operatorCtx);

            // For each result row, NULL out absent dimensions and resolve GROUPING() calls
            for (final Row r : groupResult) {
                final List<Object> vals = new ArrayList<>(r.getValues());
                for (int i = 0; i < selectExprs.size(); i++) {
                    final String expr = selectExprs.get(i);
                    // selectExprs is 1:1 with itemByIndex (stars expanded); the value-expr context
                    // drives GROUPING detection.
                    final FrostlakeParser.ExpressionContext valueExpr =
                        SelectItemAccessors.getItemValueExpr(itemByIndex.get(i));
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
            ? ParseTreeText.functionBooleanArgs(((FrostlakeParser.FunctionCallExprContext) groupingCall).functionArgList())
            : List.of();
        long mask = 0L;
        for (int a = 0; a < args.size(); a++) {
            final String argText = ParseTreeText.getOriginalText(args.get(a)).trim().toUpperCase();
            final int bit = activeUpper.contains(argText) ? 0 : 1;
            mask |= ((long) bit) << (args.size() - 1 - a);
        }
        return mask;
    }

    /**
     * Plan-time scope validation of the GROUP BY keys themselves: an invalid qualifier
     * ({@code GROUP BY r.k} with r aliased away) or a bare duplicate over an ON join is a
     * compile-time error live, over empty inputs too. Keys that are SELECT aliases or ordinals
     * pass — the alias set is exempt and an ordinal parses to a literal the walk ignores.
     * ROLLUP / CUBE / GROUPING SETS member expressions are keys all the same.
     */
    private void validateGroupKeyScope(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                       final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (ctx.groupByClause().ALL() != null) {
            // GROUP BY ALL derives its keys from the SELECT items, which are validated above.
            return;
        }
        final Set<String> aliasNames = executor.selectItemAliasNames(ctx);
        for (final FrostlakeParser.GroupByElementContext element : ctx.groupByClause().groupByElement()) {
            if (element.expression() != null) {
                executor.validateClauseScope(ParseTreeText.getOriginalText(element.expression()),
                    table, aliasToTable, allTables, aliasNames, element.expression());
            }
            if (element.groupByColumnList() != null) {
                for (final FrostlakeParser.ExpressionContext member : element.groupByColumnList().expression()) {
                    executor.validateClauseScope(ParseTreeText.getOriginalText(member),
                        table, aliasToTable, allTables, aliasNames, member);
                }
            }
            if (element.groupingSetList() != null) {
                for (final FrostlakeParser.GroupingSetContext set : element.groupingSetList().groupingSet()) {
                    for (final FrostlakeParser.ExpressionContext member : set.expression()) {
                        executor.validateClauseScope(ParseTreeText.getOriginalText(member),
                            table, aliasToTable, allTables, aliasNames, member);
                    }
                }
            }
        }
    }

    /**
     * Plan-time strict-argument validation for an AGGREGATE query's SELECT items — the same walk the
     * projection path runs (see {@code QueryExecutor.applyProjection}), so Snowflake's COMPILE-time
     * argument-type errors fire here too.
     *
     * <p>Without it they could not surface at all on this path: {@code GroupByOperator} turns any
     * per-group evaluation failure into NULL, so an aggregate called with the wrong argument type
     * (live-verified: {@code VECTOR_SUM(<NUMBER column>)} is "Invalid argument types for function
     * 'VECTOR_SUM': (NUMBER(1,0))" on the account) came back as a silent NULL instead.
     */
    private void validateSelectItemArguments(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                             final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator strictEval = new ExpressionEvaluator(
            table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            strictEval.setMultiTableContext(aliasToTable, allTables);
        }
        // Grows item by item: a lateral column alias is referencable only by LATER items (a
        // forward reference is "invalid identifier" live), and a star RENAME target becomes
        // referencable once its star item has passed.
        final Set<String> earlierOutputNames = new HashSet<>();
        strictEval.setScopeExemptNames(earlierOutputNames);
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            for (final FrostlakeParser.StarModifierContext modifier : SelectItemAccessors.getStarModifiers(item)) {
                if (modifier.RENAME() != null) {
                    for (final FrostlakeParser.StarRenameItemContext rename : modifier.starRenameItem()) {
                        // CANONICAL, not upper-cased: the reader folds an unquoted name already, and
                        // upper-casing on top made a quoted alias answer to a name it never carried.
                        earlierOutputNames.add(ParseTreeText.getIdentifier(rename.identifier(1)));
                    }
                }
            }
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final ParserRuleContext expr = SelectItemAccessors.getItemExpression(item);
            if (expr == null) {
                continue;
            }
            // Scope the ITEM's own offset around the walk, the way the projection path does. Without
            // it an aggregate's refusal came out unpositioned where live points at the function name
            // — this is the AGGREGATE path, reached instead of applyProjection's, and it was the only
            // one of the two that never set an origin.
            final SourcePosition displacedItem = ExpressionSource.beginNested(new SourcePosition(
                expr.getStart().getLine(), expr.getStart().getCharPositionInLine()));
            try {
                strictEval.validateStrict(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(expr)));
                validateWithinGroupValues(expr, strictEval);
            } finally {
                ExpressionSource.end(displacedItem);
            }
            if (SelectItemAccessors.getItemAlias(item) != null) {
                earlierOutputNames.add(SelectItemAccessors.getItemAlias(item));
            }
        }
    }

    /**
     * The value a {@code PERCENTILE_CONT} / {@code PERCENTILE_DISC} accumulates arrives through its
     * {@code WITHIN GROUP (ORDER BY …)} clause rather than its argument list, so the argument walk in
     * {@link ExpressionEvaluator#validateStrict} never sees it. Live rejects a
     * semi-structured value there — {@code PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY o)} is
     * "incompatible types: [OBJECT] and [NUMBER(9,0)]", the same sentence {@code MEDIAN(o)} produces —
     * and does so over an EMPTY input, so the check runs here at plan time. The clause comes from the
     * PARSE TREE, never from re-reading the item's text.
     */
    private void validateWithinGroupValues(final ParseTree node, final ExpressionEvaluator strictEval) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String funcName = call.functionName().getText().toUpperCase();
            final FrostlakeParser.OrderByClauseContext withinGroup =
                AggregateFunctions.withinGroupOrderBy(call);
            if (withinGroup != null && !withinGroup.orderItem().isEmpty()
                    && (funcName.equals("PERCENTILE_CONT") || funcName.equals("PERCENTILE_DISC"))) {
                strictEval.validateOrderedValue(ExpressionEvaluator.parse(
                    ParseTreeText.getOriginalText(withinGroup.orderItem(0).expression())));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            validateWithinGroupValues(node.getChild(i), strictEval);
        }
    }

    List<Row> applyImplicitGroupBy(final List<Row> rows, final Table table,
                                           final FrostlakeParser.SelectClauseContext ctx,
                                           final Map<String, Table> aliasToTable, final List<Table> allTables,
                                           final List<List<Row>> groupRowsSink) {
        validateSelectItemArguments(ctx, table, aliasToTable, allTables);
        // Implicit aggregation holds every select item to live's rule with an EMPTY key set: a
        // bare column (or star column) beside an aggregate or HAVING refuses with the bracketed
        // family. The walk is the grouped validator's; only the message differs.
        final List<String> implicitItemAliases = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            implicitItemAliases.add(selectItemAlias(item));
        }
        new GroupBySelectListValidator(executor, table, aliasToTable, allTables, true)
            .validate(ctx, implicitItemAliases, new ArrayList<>(), true);
        // Extract SELECT expressions, and each one's lateral-alias name (1:1). Stars expand to
        // their columns so the folded row carries one value per output column; the values read
        // off a representative row, matching the engine's leniency for an ungrouped bare column
        // beside an aggregate.
        final List<String> selectExpressions = new ArrayList<>();
        final List<String> aliasNames = new ArrayList<>();
        final List<FrostlakeParser.SelectItemContext> itemByIndex = new ArrayList<>();
        final List<String> starColumnByIndex = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                for (final StarColumn sc : executor.starItemColumns(item, table, aliasToTable)) {
                    selectExpressions.add(sc.getExpression());
                    aliasNames.add(sc.isRenamed() ? sc.getOutputName() : null);
                    itemByIndex.add(item);
                    starColumnByIndex.add(sc.getExpression());
                }
                continue;
            }
            if (!SelectItemAccessors.isExprItem(item)) continue;
            selectExpressions.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
            aliasNames.add(selectItemAlias(item));
            itemByIndex.add(item);
            starColumnByIndex.add(null);
        }

        // Create aggregate evaluator. Thread the alias/table context through so an aggregate over a
        // table-qualified column (SUM(s.qty)) or an expression (SUM(a*b)) resolves it instead of
        // silently yielding NULL.
        final Map<String, Object> lateralAliases = new HashMap<>();
        final AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
            @Override
            public Object evaluate(final int index, final List<Row> allRows) {
                if (index >= itemByIndex.size()) {
                    throw new RuntimeException("Select item index out of range: " + index);
                }
                // A star-expanded column reads off the representative first row of the fold.
                final String starColumn = starColumnByIndex.get(index);
                if (starColumn != null) {
                    if (allRows.isEmpty()) {
                        return null;
                    }
                    final ExpressionEvaluator starEval = new ExpressionEvaluator(table,
                        executor.getFunctionRegistry(), executor.getCatalog(), executor);
                    starEval.setMultiTableContext(aliasToTable, allTables);
                    return starEval.evaluate(ExpressionEvaluator.parse(starColumn), allRows.get(0));
                }
                return evaluateGroupItem(itemByIndex.get(index), allRows, table, aliasToTable,
                    allTables, lateralAliases);
            }
        };

        // Build operator context
        final OperatorContext context = OperatorContext.builder()
            .table(table)
            .functionRegistry(executor.getFunctionRegistry()).queryExecutor(executor)
            .build();

        // Create and execute implicit GROUP BY operator
        final GroupByOperator groupByOp = GroupByOperator.createImplicit(selectExpressions, aggregateEvaluator);
        groupByOp.captureGroupRows(groupRowsSink);
        groupByOp.captureLateralAliases(aliasNames, lateralAliases);
        return groupByOp.execute(rows, context);
    }

    /**
     * Evaluate a select item for one group. Aggregates and bare (qualified) column references keep
     * their existing per-group handling; a complex non-aggregate projection (arithmetic, boolean,
     * function of the group keys, …) — including a boolean AND/OR/NOT item whose value expression
     * is null — is evaluated on a representative group row via the AST evaluator, since the group
     * keys it references are constant within a group.
     *
     * <p>{@code lateralAliases} carries the values of EARLIER items of the same SELECT list, keyed by
     * uppercased alias (see {@link GroupByOperator#captureLateralAliases}), so a derived item can reference a
     * sibling alias — {@code SELECT x AS n, LOWER(n) AS lo, COUNT(1) FROM t GROUP BY x}. It may be null.
     */
    /**
     * A braced-star item's value for one group: the OBJECT over the group's representative row, the same
     * "first row of the group" reading a plain star gets here. {@code {*}} is not an aggregate, so the
     * OBJECT_CONSTRUCT call it stands for is evaluated as an ordinary scalar expression.
     */
    private Object evaluateObjectStarItem(final FrostlakeParser.SelectItemContext item, final List<Row> groupRows,
                                          final Table table, final Map<String, Table> aliasToTable,
                                          final List<Table> allTables) {
        final ExpressionEvaluator objectEval = new ExpressionEvaluator(
            table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        objectEval.setMultiTableContext(aliasToTable, allTables);
        return objectEval.evaluate(executor.objectStarExpression(item, table, aliasToTable), groupRows.get(0));
    }

    private Object evaluateGroupItem(final FrostlakeParser.SelectItemContext item, final List<Row> groupRows,
                                     final Table table, final Map<String, Table> aliasToTable,
                                     final List<Table> allTables,
                                     final Map<String, Object> lateralAliases) {
        final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
        // A window item's value is computed by the WINDOW stage over the grouped rows; the per-group
        // value here is only a placeholder that stage overwrites. Evaluating the item anyway sent
        // ROW_NUMBER() OVER (...) to the scalar evaluator, which threw and WARNed once per group
        // ("Window function not available in this context") — hundreds of noise lines per loader run.
        if (valueExpr != null && executor.hasWindowFunctionInExpression(valueExpr)) {
            return null;
        }
        final String itemText = ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
        // An item that IS just an earlier item's alias reuses that value rather than recomputing it: the
        // defining item may be an aggregate (SELECT COUNT(1) AS c, c AS again), which no single group row can
        // produce. A real column of the same name still wins, matching the WHERE-clause alias rule.
        if (lateralAliases != null && !lateralAliases.isEmpty()) {
            final String bare = itemText.trim().toUpperCase();
            if (lateralAliases.containsKey(bare) && (table == null || !table.hasColumn(bare))) {
                return lateralAliases.get(bare);
            }
        }
        if (valueExpr != null
                && (valueExpr instanceof FrostlakeParser.QualifiedNameExprContext
                    || executor.hasAggregateFunctionInExpression(valueExpr))) {
            return evaluateSelectItem(valueExpr, groupRows, table, aliasToTable, allTables, lateralAliases);
        }
        final ExpressionEvaluator ev = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        // JOIN-aware resolution for qualified refs (s.col, o.col) in the item: without the multi-table
        // context, a representative row of a LEFT-JOIN group (null-padded on the miss side) resolves its
        // columns positionally against the wrong layout and the whole item silently evaluates to NULL.
        ev.setMultiTableContext(aliasToTable, allTables);
        // Sibling aliases resolve only AFTER the real columns (the evaluator checks the table first), so a
        // column of the same name keeps precedence over an alias that shadows it.
        ev.setOuterLateralContext(lateralAliases);
        if (groupRows.isEmpty()) {
            // An EMPTY implicit group still emits one row (SELECT 'X', COUNT(*) FROM t WHERE FALSE yields
            // 'X', 0 in Snowflake), so an item that needs no input row — a literal, or an expression over
            // literals — must still be evaluated. Only something that reads a column has no value to read
            // and stays NULL. This idiom is how a script asserts a row count:
            // (SELECT 'MISSING' FROM t WHERE … HAVING COUNT(*) <> 1).
            try {
                return ev.evaluate(itemText, new Row(new ArrayList<>()));
            } catch (final RuntimeException needsARow) {
                return null;
            }
        }
        return ev.evaluate(itemText, groupRows.get(0));
    }


    /**
     * {@code COUNT(DISTINCT a, b, …)} — the number of distinct COMBINATIONS of the arguments, ignoring any row
     * in which ANY of them is NULL, as Snowflake does. Only the first argument used to be evaluated, so the
     * result was just that column's distinct count (and NULL tuples were not dropped).
     */
    private long countDistinctTuples(final List<String> args, final List<Row> groupRows, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<List<Object>> perArgValues = new ArrayList<>();
        for (final String arg : args) {
            perArgValues.add(aggArgValues("COUNT", arg, groupRows, table, aliasToTable, allTables));
        }
        final Set<List<Object>> seen = new HashSet<>();
        final int rowCount = perArgValues.isEmpty() ? 0 : perArgValues.get(0).size();
        for (int r = 0; r < rowCount; r++) {
            final List<Object> tuple = new ArrayList<>(perArgValues.size());
            boolean anyNull = false;
            for (final List<Object> values : perArgValues) {
                final Object value = r < values.size() ? values.get(r) : null;
                if (value == null) {
                    anyNull = true;
                    break;
                }
                tuple.add(ValueComparisons.normalizeValueForDistinct(value));
            }
            if (!anyNull) {
                seen.add(tuple);
            }
        }
        return seen.size();
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
            final List<String> args = aggArgTexts(funcCtx, table, allTables);
            if (args.size() > 1) {
                // A multi-argument aggregate — COUNT(DISTINCT a, b) counts distinct COMBINATIONS — cannot be
                // modelled by a single column index, so it must take the general per-group path below.
                return null;
            }
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
    private List<String> aggArgTexts(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                     final Table table, final List<Table> allTables) {
        final List<String> args = new ArrayList<>();
        if (funcCtx.functionArgList() != null) {
            for (final FrostlakeParser.BooleanExprContext arg : ParseTreeText.functionBooleanArgs(funcCtx.functionArgList())) {
                args.add(substituteSiblingAliasesInAggArg(ParseTreeText.getOriginalText(arg), funcCtx,
                    table, allTables));
            }
        }
        return args;
    }

    /**
     * An aggregate's ARGUMENT may name a sibling SELECT alias, and it means that alias's defining
     * EXPRESSION evaluated per row — not the alias's per-group value. Live-verified on a real account
     *: {@code SELECT city AS c, MAX(LENGTH(c)) AS widest FROM orders GROUP BY city} answers
     * 4 for 'abcd', i.e. exactly {@code MAX(LENGTH(city))}. The defining items are read off the
     * enclosing selectList in the parse tree, so every grouped path gets the rewrite; an alias that also
     * names a real column keeps the column's meaning, and an alias defined by an aggregate or window
     * item is left alone (it has no per-row form).
     */
    // Statement-invariant facts of the generic aggregate path (substituted argument texts,
    // variant-static verdicts, bare-column indexes), cached per THREAD keyed by the resolution
    // context's IDENTITY + the argument text — a new statement's tables/contexts are new objects,
    // so stale entries can never be read; bounded by wholesale clear. Thread-local because this
    // evaluator is a per-engine singleton serving concurrent read-locked queries.
    private final ThreadLocal<Map<AggFactKey, Object>> genericAggFacts =
        new ThreadLocal<Map<AggFactKey, Object>>() {
            @Override
            protected Map<AggFactKey, Object> initialValue() {
                return new HashMap<AggFactKey, Object>();
            }
        };

    private Map<AggFactKey, Object> genericAggFactsMap() {
        final Map<AggFactKey, Object> facts = genericAggFacts.get();
        if (facts.size() >= 4096) {
            facts.clear();
        }
        return facts;
    }

    private String substituteSiblingAliasesInAggArg(final String argText,
                                                    final FrostlakeParser.FunctionCallExprContext funcCtx,
                                                    final Table table, final List<Table> allTables) {
        final Map<AggFactKey, Object> facts = genericAggFactsMap();
        final AggFactKey key = new AggFactKey("sub", funcCtx, table, allTables, argText);
        final Object cached = facts.get(key);
        if (cached != null) {
            return (String) cached;
        }
        final String substituted = substituteSiblingAliasesUncached(argText, funcCtx, table, allTables);
        facts.put(key, substituted);
        return substituted;
    }

    private String substituteSiblingAliasesUncached(final String argText,
                                                    final FrostlakeParser.FunctionCallExprContext funcCtx,
                                                    final Table table, final List<Table> allTables) {
        FrostlakeParser.SelectListContext selectList = null;
        for (ParseTree node = funcCtx; node != null; node = node.getParent()) {
            if (node instanceof FrostlakeParser.SelectListContext) {
                selectList = (FrostlakeParser.SelectListContext) node;
                break;
            }
        }
        if (selectList == null) {
            return argText;
        }
        final List<String> names = new ArrayList<>();
        final List<String> exprs = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            final String alias = selectItemAlias(item);
            if (alias == null || namesAColumn(alias, table, allTables)) {
                continue;
            }
            final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
            if (valueExpr == null
                    || executor.hasAggregateFunctionInExpression(valueExpr)
                    || executor.hasWindowFunctionInExpression(valueExpr)) {
                continue;
            }
            names.add(alias);
            exprs.add("(" + ParseTreeText.getOriginalText(valueExpr) + ")");
        }
        return names.isEmpty() ? argText : substituteNestedAliases(argText, names, exprs);
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
                final List<Object> values = new ArrayList<>();
                for (final Row r : groupRows) {
                    final Object v = r.getValue(plan.colIndex);
                    if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                        values.add(v);
                    }
                }
                return AggregateNumerics.sum(values);
            }
            case AVG: {
                final Set<Object> seen = plan.distinct ? new HashSet<>() : null;
                final List<Object> values = new ArrayList<>();
                for (final Row r : groupRows) {
                    final Object v = r.getValue(plan.colIndex);
                    if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                        values.add(v);
                    }
                }
                // Fixed-point inputs average to a scale-(max+6) BigDecimal, doubles/variants stay double,
                // no non-null input is NULL (AggregateNumerics.avg, live-verified Snowflake typing).
                return AggregateNumerics.avg(values);
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

    /**
     * Evaluate an expression (a grouped column, an aggregate, or an expression over them) over a single
     * group's source rows — used to resolve an ORDER BY key that is not one of the SELECT columns.
     * {@code outputAliasValues} carries the output row's SELECT-alias values (upper-cased alias →
     * value), so a key like {@code m + 1} over an aggregate's alias resolves; real columns win.
     */
    Object evaluateOverGroup(final FrostlakeParser.ExpressionContext expr, final List<Row> groupRows,
                            final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables,
                            final Map<String, Object> outputAliasValues) {
        return evaluateSelectItem(expr, groupRows, table, aliasToTable, allTables, outputAliasValues);
    }

    private Object evaluateSelectItem(final FrostlakeParser.ExpressionContext expr, final List<Row> groupRows, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables,
                                     final Map<String, Object> lateralAliases) {
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
            final List<String> args = aggArgTexts(funcCtx, table, allTables);
            final String arg0 = args.isEmpty() ? "" : args.get(0);
            final List<Row> aggregateInputRows = groupRows;
            switch (funcName) {
                case "COUNT": {
                    if (distinct && args.size() > 1) {
                        return countDistinctTuples(args, aggregateInputRows, table, aliasToTable, allTables);
                    }
                    final List<Object> vals = aggArgValues(funcName, arg0, aggregateInputRows, table, aliasToTable, allTables);
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
                    final List<Object> values = new ArrayList<>();
                    for (final Object v : aggArgValues(funcName, arg0, aggregateInputRows, table, aliasToTable, allTables)) {
                        if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                            values.add(v);
                        }
                    }
                    // SUM over no non-null rows is NULL in Snowflake, not zero (AggregateNumerics returns null).
                    return AggregateNumerics.sum(values,
                        isStaticallyVariantArgument(arg0, table, aliasToTable, allTables));
                }
                case "AVG": {
                    final Set<Object> seen = distinct ? new HashSet<>() : null;
                    final List<Object> values = new ArrayList<>();
                    for (final Object v : aggArgValues(funcName, arg0, aggregateInputRows, table, aliasToTable, allTables)) {
                        if (v != null && (seen == null || seen.add(ValueComparisons.normalizeValueForDistinct(v)))) {
                            values.add(v);
                        }
                    }
                    // AVG over no non-null rows is NULL in Snowflake; fixed-point inputs average to a
                    // scale-(max+6) BigDecimal, doubles/variants stay double (AggregateNumerics.avg).
                    return AggregateNumerics.avg(values,
                        isStaticallyVariantArgument(arg0, table, aliasToTable, allTables));
                }
                case "MIN": {
                    Object min = null;
                    for (final Object v : aggArgValues(funcName, arg0, aggregateInputRows, table, aliasToTable, allTables)) {
                        if (v != null && (min == null || ((Comparable) v).compareTo(min) < 0)) {
                            min = v;
                        }
                    }
                    return min;
                }
                case "MAX": {
                    Object max = null;
                    for (final Object v : aggArgValues(funcName, arg0, aggregateInputRows, table, aliasToTable, allTables)) {
                        if (v != null && (max == null || ((Comparable) v).compareTo(max) > 0)) {
                            max = v;
                        }
                    }
                    return max;
                }
                case "PERCENTILE_CONT":
                case "PERCENTILE_DISC":
                    return AggregateFunctions.evaluatePercentile(funcCtx, aggregateInputRows, table);
                default:
                    if (executor.getFunctionRegistry().hasAggregateFunction(funcName)) {
                        return evaluateGenericAggregate(funcCtx, funcName, args, aggregateInputRows, table, aliasToTable, allTables);
                    }
                    break;
            }
        }

        // An expression that CONTAINS an aggregate but is not itself a bare aggregate call — e.g.
        // COUNT(*)::VARCHAR, 'total=' || SUM(v), MAX(v) + 1. Compute each nested aggregate over the group,
        // then evaluate the surrounding expression with those results substituted in (mirrors the nested
        // window-function path). Without this the whole expression fell through to the column-reference
        // branch below, whose text is not a column name, and resolved to NULL.
        if (executor.hasAggregateFunctionInExpression(expr)) {
            final List<FrostlakeParser.ExpressionContext> aggCalls = new ArrayList<>();
            collectAggregateCalls(expr, aggCalls);
            final Map<String, Object> aggregateValues = new HashMap<>();
            for (final FrostlakeParser.ExpressionContext aggCall : aggCalls) {
                // Key by the aggregate's canonical AST print — the same key visitFunctionCall looks it up by
                // when the surrounding expression is evaluated below. No sibling aliases inside the aggregate:
                // its arguments are evaluated PER ROW, where a per-group alias value has no meaning.
                aggregateValues.put(
                    AstPrinterVisitor.print(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(aggCall))),
                    evaluateSelectItem(aggCall, groupRows, table, aliasToTable, allTables, null));
            }
            final ExpressionEvaluator ev = new ExpressionEvaluator(
                table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            if (aliasToTable != null && allTables != null && allTables.size() > 1) {
                ev.setMultiTableContext(aliasToTable, allTables);
            }
            ev.setResultContext(aggregateValues);
            // The parts of this expression OUTSIDE the aggregate calls are evaluated once for the group, so an
            // earlier item's alias is a plain per-group scalar here and may be referenced — the shape
            // `SUM(…) AS n, IFF(n > 0, ARRAY_UNIQUE_AGG(a), ARRAY_UNIQUE_AGG(b))`. Table columns still win.
            ev.setOuterLateralContext(lateralAliases);
            final Row groupRow = groupRows.isEmpty() ? new Row(new ArrayList<>()) : groupRows.get(0);
            return ev.evaluate(ParseTreeText.getOriginalText(expr), groupRow);
        }

        // Not an aggregate: a plain (possibly qualified) column reference is constant within the
        // group and reads from the first row. Any OTHER aggregate-free expression — ORDER BY
        // k + 1 over a grouped k, m + 1 over a sibling alias — evaluates the same way over that
        // row (its column parts are group-constant too), with the offered aliases resolving after
        // real columns. Treating such a text as a column name rejected live-legal keys with
        // "invalid identifier 'k + 1'".
        final String columnRef = ParseTreeText.getOriginalText(expr);
        Expression parsedRef = null;
        try {
            parsedRef = ExpressionEvaluator.parse(columnRef);
        } catch (final RuntimeException notAnExpression) {
            // Fall through to the column-name paths below.
        }
        if (parsedRef != null && !(parsedRef instanceof ColumnReferenceExpression)) {
            final ExpressionEvaluator ev = new ExpressionEvaluator(
                table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            if (aliasToTable != null && allTables != null && allTables.size() > 1) {
                ev.setMultiTableContext(aliasToTable, allTables);
            }
            ev.setOuterLateralContext(lateralAliases);
            return ev.evaluate(parsedRef, groupRows.get(0));
        }
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            try {
                return executor.getQualifiedColumnValueFromTables(groupRows.get(0), allTables, aliasToTable,
                    columnRef, table != null ? table.getJoinKeyNames() : null);
            } catch (final Exception e) {
                return groupRows.get(0).getValue(ValueComparisons.getColumnIndex(table, columnRef));
            }
        }
        return groupRows.get(0).getValue(ValueComparisons.getColumnIndex(table, columnRef));
    }

    /** Collect the aggregate function calls — COUNT(*) and any registered aggregate without an OVER clause —
     *  nested anywhere in {@code node}. A matched call is not descended into (aggregates do not nest), so its
     *  own arguments are left alone. */
    private void collectAggregateCalls(final ParseTree node, final List<FrostlakeParser.ExpressionContext> out) {
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final String name = ((FrostlakeParser.FunctionCallStarExprContext) node).functionName().getText().toUpperCase();
            if (executor.getFunctionRegistry().hasAggregateFunction(name)) {
                out.add((FrostlakeParser.ExpressionContext) node);
                return;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext fc = (FrostlakeParser.FunctionCallExprContext) node;
            if (fc.overClause() == null
                    && executor.getFunctionRegistry().hasAggregateFunction(fc.functionName().getText().toUpperCase())) {
                out.add(fc);
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAggregateCalls(node.getChild(i), out);
        }
    }

    /**
     * Compute every aggregate call in a HAVING predicate over one group's rows, keyed by the aggregate's
     * canonical AST print — the key {@code visitFunctionCall} looks it up by when the HAVING condition is
     * evaluated. This lets HAVING reference an aggregate that is NOT a SELECT item (e.g.
     * {@code SELECT grp, COUNT(*)::VARCHAR … HAVING COUNT(*) > 1}): it is computed fresh over the group
     * rather than read back from the already-projected output row.
     */
    Map<String, Object> havingAggregateContext(final FrostlakeParser.BooleanExprContext havingBool,
                                               final List<Row> groupRows, final Table table,
                                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<FrostlakeParser.ExpressionContext> aggCalls = new ArrayList<>();
        collectAggregateCalls(havingBool, aggCalls);
        return havingAggregateContext(aggCalls, havingAggregateKeys(aggCalls), groupRows, table,
            aliasToTable, allTables);
    }

    /** The canonical AST-print key of each HAVING aggregate call — per-STATEMENT facts, so callers
     *  that filter many groups collect the calls and keys ONCE and reuse them per group. */
    List<String> havingAggregateKeys(final List<FrostlakeParser.ExpressionContext> aggCalls) {
        final List<String> keys = new ArrayList<>(aggCalls.size());
        for (final FrostlakeParser.ExpressionContext aggCall : aggCalls) {
            keys.add(AstPrinterVisitor.print(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(aggCall))));
        }
        return keys;
    }

    /** Per-group evaluation over PRE-collected calls and keys (see {@link #havingAggregateKeys}). */
    Map<String, Object> havingAggregateContext(final List<FrostlakeParser.ExpressionContext> aggCalls,
                                               final List<String> aggKeys,
                                               final List<Row> groupRows, final Table table,
                                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final Map<String, Object> context = new HashMap<>();
        for (int i = 0; i < aggCalls.size(); i++) {
            context.put(aggKeys.get(i),
                evaluateSelectItem(aggCalls.get(i), groupRows, table, aliasToTable, allTables, null));
        }
        return context;
    }

    /** Collect the aggregate calls of a HAVING tree — exposed so the per-statement caller can
     *  precompute them once. */
    List<FrostlakeParser.ExpressionContext> collectHavingAggregateCalls(
            final FrostlakeParser.BooleanExprContext havingBool) {
        final List<FrostlakeParser.ExpressionContext> aggCalls = new ArrayList<>();
        collectAggregateCalls(havingBool, aggCalls);
        return aggCalls;
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
        if (acc instanceof MultiArgumentAccumulator) {
            // The SPI seam, tried before the engine's own accumulator classes: an aggregate contributed
            // by an optional pack has no branch of its own here, so it declares that it wants the whole
            // row tuple and gets every argument evaluated per row, in the order the call wrote them.
            final List<List<Object>> perArgument = new ArrayList<>();
            for (final String argument : args) {
                perArgument.add(aggArgValues(funcName, argument, aggRows, table, aliasToTable, allTables));
            }
            final int rowCount = perArgument.isEmpty() ? 0 : perArgument.get(0).size();
            for (int row = 0; row < rowCount; row++) {
                final List<Object> tuple = new ArrayList<>();
                for (final List<Object> argumentValues : perArgument) {
                    tuple.add(row < argumentValues.size() ? argumentValues.get(row) : null);
                }
                ((MultiArgumentAccumulator) acc).accumulate(tuple);
            }
        } else if (hasTwoArgs && acc instanceof ListAggAccumulator) {
            // LISTAGG(<expr>, <delimiter>): the 2nd argument is a constant string delimiter.
            ((ListAggAccumulator) acc).setDelimiter(unquoteDelimiter(args.get(1)));
            for (final Object v : aggArgValues(funcName, args.get(0), aggRows, table, aliasToTable, allTables)) {
                acc.accumulate(v);
            }
        } else if (hasTwoArgs && acc instanceof ApproxPercentileAccumulator) {
            // APPROX_PERCENTILE(<expr>, <percentile>): the 2nd argument is a constant percentile in [0, 1].
            ((ApproxPercentileAccumulator) acc).setPercentile(new BigDecimal(args.get(1).trim()).doubleValue());
            for (final Object v : aggArgValues(funcName, args.get(0), aggRows, table, aliasToTable, allTables)) {
                acc.accumulate(v);
            }
        } else if (hasTwoArgs && acc instanceof MaxByMinByAccumulator) {
            // MAX_BY / MIN_BY(<value>, <sort_key> [, <N>]): both arguments are columns whose RAW values
            // matter. The bounded third-argument form returns an ARRAY of up to N values ordered by key
            // (MIN ascending / MAX descending); DISTINCT dedups the values (the aggregation-loader idiom
            // MIN_BY(DISTINCT ref, ref, 100) — the scalar form silently returned ONE bare value where
            // Snowflake returns the array).
            if (args.size() >= 3) {
                ((MaxByMinByAccumulator) acc).setLimit(
                    new BigDecimal(args.get(2).trim()).intValue(), funcCtx.DISTINCT() != null);
            }
            final List<Object> values = aggArgValues(funcName, args.get(0), aggRows, table, aliasToTable, allTables);
            final List<Object> keys = aggArgValues(funcName, args.get(1), aggRows, table, aliasToTable, allTables);
            for (int i = 0; i < values.size(); i++) {
                ((MaxByMinByAccumulator) acc).accumulate(values.get(i), keys.get(i));
            }
        } else if (hasTwoArgs && acc instanceof ObjectAggAccumulator) {
            // OBJECT_AGG(<key>, <value>): key/value pairs into one OBJECT (NULL key or value drops the pair).
            final List<Object> keys = aggArgValues(funcName, args.get(0), aggRows, table, aliasToTable, allTables);
            final List<Object> values = aggArgValues(funcName, args.get(1), aggRows, table, aliasToTable, allTables);
            for (int i = 0; i < keys.size(); i++) {
                ((ObjectAggAccumulator) acc).accumulate(keys.get(i), values.get(i));
            }
        } else if (hasTwoArgs) {
            // CORR / COVAR_* / REGR_*(y, x): two numeric columns.
            final List<Object> ys = aggArgValues(funcName, args.get(0), aggRows, table, aliasToTable, allTables);
            final List<Object> xs = aggArgValues(funcName, args.get(1), aggRows, table, aliasToTable, allTables);
            for (int i = 0; i < ys.size(); i++) {
                final Object y = ys.get(i);
                final Object x = xs.get(i);
                if (y != null && x != null) {
                    final double dy = aggNumeric(y);
                    final double dx = aggNumeric(x);
                    if (acc instanceof CorrAccumulator) {
                        ((CorrAccumulator) acc).accumulate(dy, dx);
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
            // Single-argument generic accumulator (COUNT_IF, MEDIAN, ARRAY_AGG, …). DISTINCT — e.g.
            // ARRAY_AGG(DISTINCT tag) WITHIN GROUP (ORDER BY tag) — drops repeated values; the flag was
            // ignored on this path, so duplicates survived into the aggregate.
            final String singleArg = args.isEmpty() ? "" : args.get(0);
            final Set<Object> seen = funcCtx.DISTINCT() != null ? new HashSet<>() : null;
            for (final Object v : aggArgValues(funcName, singleArg, aggRows, table, aliasToTable, allTables)) {
                if (seen != null && v != null && !seen.add(ValueComparisons.normalizeValueForDistinct(v))) {
                    continue;
                }
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
    /** The subset of {@code rows} on which a FILTER (WHERE cond) aggregate operates. */
    private List<Row> rowsSatisfying(final FrostlakeParser.BooleanExprContext condition, final List<Row> rows,
                                     final Table table, final Map<String, Table> aliasToTable,
                                     final List<Table> allTables) {
        final List<Object> outcomes =
            aggArgValues("COUNT_IF", ParseTreeText.getOriginalText(condition), rows, table, aliasToTable, allTables);
        final List<Row> kept = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (SqlTruth.isTrue(outcomes.get(i))) {
                kept.add(rows.get(i));
            }
        }
        return kept;
    }

    private List<Object> aggArgValues(final String aggregateName, final String arg, final List<Row> groupRows,
                                      final Table table,
                                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<Object> raw = rawAggArgValues(arg, groupRows, table, aliasToTable, allTables);
        // A VARIANT JSON null is missing input for a value-computing aggregate and a VALUE for an
        // ordering/collecting one. Live-verified over {JSON null, 3}: SUM is 3, COUNT is 1,
        // AVG over a lone JSON null is SQL NULL and LISTAGG is '3' — while MAX over {JSON null, 5} is
        // the JSON null itself and MIN is 5.
        final List<Object> values = new ArrayList<>(raw.size());
        for (final Object v : raw) {
            values.add(VariantJsonNulls.asAggregateInput(aggregateName, v));
        }
        return values;
    }

    /**
     * Whether the aggregate ARGUMENT's declared type is VARIANT. Live:
     * {@code SUM(v:b)} over whole-number JSON values is DOUBLE (7.0, SYSTEM$TYPEOF FLOAT) even though
     * path extraction hands the engine plain integers — the declared VARIANT argument flips the tier
     * exactly as a runtime VariantValue does, so the flag rides the STATIC type, not the value class.
     */
    private boolean isStaticallyVariantArgument(final String arg, final Table table,
                                                final Map<String, Table> aliasToTable,
                                                final List<Table> allTables) {
        if (arg == null || arg.isEmpty()) {
            return false;
        }
        final Map<AggFactKey, Object> facts = genericAggFactsMap();
        final AggFactKey key = new AggFactKey("var", table, aliasToTable, allTables, arg);
        final Object cached = facts.get(key);
        if (cached != null) {
            return ((Boolean) cached).booleanValue();
        }
        final boolean verdict = isStaticallyVariantUncached(arg, table, aliasToTable, allTables);
        facts.put(key, Boolean.valueOf(verdict));
        return verdict;
    }

    private boolean isStaticallyVariantUncached(final String arg, final Table table,
                                                final Map<String, Table> aliasToTable,
                                                final List<Table> allTables) {
        try {
            final ExpressionEvaluator ev =
                new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
                ev.setMultiTableContext(aliasToTable, allTables);
            }
            return ev.inferStaticType(ExpressionEvaluator.parse(arg)) instanceof VariantType;
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    private List<Object> rawAggArgValues(final String arg, final List<Row> groupRows, final Table table,
                                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<Object> values = new ArrayList<>(groupRows.size());
        final Map<AggFactKey, Object> facts = genericAggFactsMap();
        final AggFactKey colKey = new AggFactKey("col", table, null, null, arg);
        final Object cachedIndex = facts.get(colKey);
        final int colIndex;
        if (cachedIndex != null) {
            colIndex = ((Integer) cachedIndex).intValue();
        } else {
            colIndex = safeColumnIndex(table, arg);
            facts.put(colKey, Integer.valueOf(colIndex));
        }
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
        for (int rowIndex = 0; rowIndex < groupRows.size(); rowIndex++) {
            // Number the group's rows so an aggregated SEQ1/2/4/8 counts them. With no GROUP BY the
            // group IS the whole input in scan order, which is what live counts: SUM(SEQ4()) over
            // five rows is 0+1+2+3+4. Under a real GROUP BY, live numbers by the SCAN and Frostlake
            // numbers within the group, so the two disagree — see the note in SeqFn.
            final Long displacedOrdinal = RowOrdinal.begin(rowIndex);
            try {
                values.add(ev.evaluate(parsed, groupRows.get(rowIndex)));
            } catch (final Exception e) {
                values.add(null);
            } finally {
                RowOrdinal.end(displacedOrdinal);
            }
        }
        return values;
    }

    /** Column index of {@code col} in {@code table}, or -1 when it is not a bare column of that table. */
    private static int safeColumnIndex(final Table table, final String col) {
        return ValueComparisons.findColumnIndex(table, col);
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
     * {@code GROUP BY <alias>}: a bare identifier that names a SELECT-list alias — and no real column
     * of the FROM relation(s); in Snowflake a column always wins over a same-named alias — groups by
     * that item's EXPRESSION. Evaluating the bare alias name against the tables failed on every row,
     * and the error-sentinel key silently collapsed all rows into a single group.
     */
    /**
     * The SELECT-list aliases a GROUP BY expression may legally reference from WITHIN a larger
     * expression: non-aggregate, non-window items whose alias does not shadow a real column (a real
     * column keeps precedence, mirroring {@link #resolveSelectAlias}). Fills the two parallel lists
     * with the alias and its parenthesized defining expression.
     */
    private void collectSubstitutableAliases(final FrostlakeParser.SelectClauseContext ctx,
                                             final List<String> aliasNames, final Table table,
                                             final List<Table> allTables,
                                             final List<String> outNames, final List<String> outExprs) {
        final List<FrostlakeParser.SelectItemContext> items = ctx.selectList().selectItem();
        for (int i = 0; i < items.size() && i < aliasNames.size(); i++) {
            final String alias = aliasNames.get(i);
            if (alias == null || namesAColumn(alias, table, allTables)) {
                continue;
            }
            final FrostlakeParser.SelectItemContext item = items.get(i);
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
            if (valueExpr == null
                    || executor.hasAggregateFunctionInExpression(valueExpr)
                    || executor.hasWindowFunctionInExpression(valueExpr)) {
                continue;
            }
            outNames.add(alias);
            outExprs.add("(" + ParseTreeText.getOriginalText(valueExpr) + ")");
        }
    }

    /**
     * Replace each substitutable alias referenced inside {@code text} with its defining expression,
     * expanding chained aliases (an alias defined over another alias) with bounded passes.
     */
    private String substituteNestedAliases(final String text, final List<String> names,
                                           final List<String> exprs) {
        String result = text;
        for (int pass = 0; pass < 5; pass++) {
            String next = result;
            for (int i = 0; i < names.size(); i++) {
                next = SqlIdentifierSubstitution.substitute(next, names.get(i), exprs.get(i));
            }
            if (next.equals(result)) {
                break;
            }
            result = next;
        }
        return result;
    }

    /** True when {@code text} references any of {@code names} as a standalone identifier token. */
    private boolean referencesAnyIdentifier(final String text, final Set<String> names) {
        for (final String name : names) {
            if (!SqlIdentifierSubstitution.substitute(text, name, "__frostlake_ref_probe__").equals(text)) {
                return true;
            }
        }
        return false;
    }

    private String resolveSelectAlias(final String text, final List<String> aliasNames,
                                      final List<String> selectExpressions, final Table table,
                                      final List<Table> allTables) {
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(text);
        } catch (final RuntimeException notAnExpression) {
            return text;
        }
        if (!(parsed instanceof ColumnReferenceExpression)) {
            return text;
        }
        final ColumnReferenceExpression ref = (ColumnReferenceExpression) parsed;
        if (ref.getTableName() != null) {
            return text;
        }
        if (namesAColumn(ref.getColumnName(), table, allTables)) {
            return text;
        }
        for (int i = 0; i < aliasNames.size(); i++) {
            if (aliasNames.get(i) != null && aliasNames.get(i).equalsIgnoreCase(ref.getColumnName())) {
                return selectExpressions.get(i);
            }
        }
        return text;
    }

    /** True when {@code name} is a column of the grouped table or of any table in the multi-table scope. */
    private boolean namesAColumn(final String name, final Table table, final List<Table> allTables) {
        if (table != null && table.hasColumn(name)) {
            return true;
        }
        if (allTables != null) {
            for (final Table t : allTables) {
                if (t != null && t.hasColumn(name)) {
                    return true;
                }
            }
        }
        return false;
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
