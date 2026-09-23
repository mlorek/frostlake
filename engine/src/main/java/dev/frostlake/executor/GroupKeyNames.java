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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A GROUP BY list's own names, and then the range of its positional keys, judged where live judges them: with
 * the select list's and the WHERE's names, and ahead of everything else the query is refused for (all
 * live-verified).
 *
 * <pre>
 *   SELECT a FROM t WHERE nosuch1 = 1 GROUP BY nosuch2              invalid identifier 'NOSUCH1'
 *   SELECT a FROM t GROUP BY nosuch2 HAVING nosuch1 = 1             invalid identifier 'NOSUCH2'
 *   SELECT nosuchfn(a) FROM t GROUP BY nosuch2                      invalid identifier 'NOSUCH2'
 *   SELECT a FROM t WHERE UPPER(1, 2) = 'x' GROUP BY nosuch2        invalid identifier 'NOSUCH2'
 *   SELECT a FROM t WHERE a = (SELECT nosuch1 FROM u) GROUP BY 9    [9] is not a valid group by expression
 *   SELECT a FROM t GROUP BY 9, nosuch2                             invalid identifier 'NOSUCH2'
 * </pre>
 *
 * <p>So every key's names come before any key's position, the position before an unknown function name, an
 * argument count or type, a window or an aggregate out of place, another clause's names and any subquery. The
 * rules that judge what a key MEANS keep their places further on; this only moves the two earliest ones up.
 * GROUP BY ALL has no keys of its own and is left to its items.
 */
final class GroupKeyNames {

    private final QueryExecutor executor;

    GroupKeyNames(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Refuse a GROUP BY key naming nothing in scope, then a positional key past the select list.
     *
     * @param ctx          the select clause
     * @param table        the relation the keys are written over
     * @param aliasToTable the FROM-clause keys
     * @param allTables    every relation in scope
     */
    void validate(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                  final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (ctx.groupByClause() == null || ctx.groupByClause().ALL() != null || readsPivot(ctx)) {
            return;
        }
        final List<FrostlakeParser.ExpressionContext> keys = keysOf(ctx.groupByClause());
        final Set<String> aliasNames = executor.selectItemAliasNames(ctx);
        for (final FrostlakeParser.ExpressionContext key : keys) {
            executor.validateClauseColumnScope(ParseTreeText.getOriginalText(key), table, aliasToTable, allTables,
                aliasNames, key);
        }
        int width = -1;
        for (final FrostlakeParser.ExpressionContext key : keys) {
            final String text = ParseTreeText.getOriginalText(key);
            final long position = OrdinalLiteral.positionOf(text);
            if (position == OrdinalLiteral.NOT_AN_ORDINAL) {
                continue;
            }
            if (width < 0) {
                width = selectListWidth(ctx, table, aliasToTable);
            }
            if (position < 1 || position > width) {
                throw new RuntimeException(SqlCompilationError.of(
                    "[" + OrdinalLiteral.echo(text) + "] is not a valid group by expression"));
            }
        }
    }

    /** Whether the query reads a PIVOT or an UNPIVOT, whose output the keys name and which does not exist yet. */
    private static boolean readsPivot(final FrostlakeParser.SelectClauseContext ctx) {
        if (ctx.tableExpression() == null || ctx.tableExpression().tableReference().isEmpty()) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext first = ctx.tableExpression().tableReference(0);
        return first.pivotClause() != null || first.unpivotClause() != null;
    }

    /** Every key a GROUP BY lists: a plain key, and each member of a ROLLUP, a CUBE or a grouping set. */
    private static List<FrostlakeParser.ExpressionContext> keysOf(final FrostlakeParser.GroupByClauseContext clause) {
        final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
        for (final FrostlakeParser.GroupByElementContext element : clause.groupByElement()) {
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
        return keys;
    }

    /** How many positions the select list offers a key: a star counts each column it expands to. */
    private int selectListWidth(final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                final Map<String, Table> aliasToTable) {
        int width = 0;
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                width += executor.starItemColumns(item, table, aliasToTable).size();
            } else {
                width++;
            }
        }
        return width;
    }
}
