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

import dev.frostlake.executor.expressions.PlannedCorrelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A grouped query over a VALUES list - written with an alias and a column list, alone or beside tables - whose HAVING
 * aggregates a subquery that aggregates the list's own column: refused as the same query over a table is where no
 * statistics answer it (see {@link HavingAggregateSubqueries}), the column named by the list's alias and typed as the
 * list types it. {@code SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT
 * MAX(fz.id) FROM g)) = 14} is {@code Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in
 * [SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(1,0))))]} (live-verified).
 */
final class ValuesHavingNesting {

    private ValuesHavingNesting() {
    }

    /**
     * The refusal of the first such subquery of the HAVING, or null where the query is no such query.
     *
     * @param executor  the executor
     * @param clause    the grouped query
     * @param relations the FROM clause's relations, by the names they go by
     * @param refused   given the key of the subquery refused, which no row then evaluates (see PlannedCorrelations)
     * @return the refusal, or null
     */
    static RuntimeException refusal(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext clause,
                                    final Map<String, Table> relations, final Set<String> refused) {
        if (clause.havingClause() == null || clause.tableExpression() == null || relations == null
                || !clause.tableExpression().joinClause().isEmpty()) {
            return null;
        }
        boolean values = false;
        for (final FrostlakeParser.TableReferenceContext reference : clause.tableExpression().tableReference()) {
            final FrostlakeParser.TableSourceContext source = reference.tableSource();
            if (source == null || reference.LATERAL() != null || reference.pivotClause() != null
                    || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                    || reference.sampleClause() != null) {
                return null;
            }
            if (source.VALUES() != null && source.LPAREN() != null) {
                // A list named by an alias with its columns, inside its parentheses or after them.
                if (reference.identifierList() == null && source.columnListOptional() == null) {
                    return null;
                }
                values = true;
            } else if (source.tableQualifiedName() == null || source.timeTravelClause() != null
                    || reference.identifierList() != null) {
                return null;
            }
        }
        if (!values) {
            return null;
        }
        final List<FrostlakeParser.ScalarSubqueryExprContext> subqueries = new ArrayList<>();
        collect(clause.havingClause().booleanExpr(), subqueries);
        for (final FrostlakeParser.ScalarSubqueryExprContext subquery : subqueries) {
            final FrostlakeParser.FunctionCallExprContext aggregate =
                HavingAggregateSubqueries.enclosingAggregate(subquery, executor);
            if (aggregate == null || countsTheList(subquery)) {
                // A COUNT of the list's column is answered, not nested (live-verified).
                continue;
            }
            final String nested = HavingAggregateSubqueries.refusal(executor, relations, aggregate, subquery);
            if (nested != null && !nested.isEmpty()) {
                refused.add(PlannedCorrelations.valueKey(ParseTreeText.getOriginalText(subquery.selectStatement())));
                return new RuntimeException(nested);
            }
        }
        return null;
    }

    /** Whether the subquery's one item is a COUNT call. */
    private static boolean countsTheList(final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final FrostlakeParser.SelectStatementContext query = subquery.selectStatement();
        if (query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null) {
            return false;
        }
        final List<FrostlakeParser.SelectItemContext> items =
            query.selectOperand(0).selectClause().selectList().selectItem();
        if (items.size() != 1 || !(items.get(0) instanceof FrostlakeParser.ExprItemContext)) {
            return false;
        }
        ParseTree value = ((FrostlakeParser.ExprItemContext) items.get(0)).booleanExpr();
        while (value.getChildCount() == 1 && !(value instanceof FrostlakeParser.FunctionCallExprContext)) {
            value = value.getChild(0);
        }
        return value instanceof FrostlakeParser.FunctionCallExprContext
            && "COUNT".equalsIgnoreCase(((FrostlakeParser.FunctionCallExprContext) value).functionName().getText());
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.ScalarSubqueryExprContext> found) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            found.add((FrostlakeParser.ScalarSubqueryExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), found);
        }
    }
}
