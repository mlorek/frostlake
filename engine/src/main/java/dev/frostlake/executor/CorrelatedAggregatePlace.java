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
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Where a subquery holding a query's own aggregate may stand. An aggregate written inside a subquery over the
 * columns of a query around it alone is that query's aggregate, and live lets the subquery holding it —
 * however deep — stand only in that query's select list or HAVING. In its WHERE, a join's ON, its GROUP BY or
 * its ORDER BY the statement is refused while it compiles, whatever rows it would read, and so is an ORDER BY
 * or GROUP BY key naming, by position or alias, a select item that holds such a subquery, and an item GROUP BY
 * ALL groups by (live-verified). The WHERE of an UPDATE or a DELETE is held to the same. A grouped or DISTINCT
 * query's ORDER BY refuses a scalar subquery written in it ahead of this:
 *
 * <pre>
 *   SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g)
 *   SELECT id, EXISTS (SELECT MAX(fz.id) FROM g) AS e FROM fz ORDER BY 2
 *       Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or select clause
 * </pre>
 *
 * <p>The call is echoed as the plan holds it (see {@link PlanEcho}), the first one written. An aggregate that
 * also reads a row of the subquery's own, {@code MAX(fz.id + g.v)}, is the subquery's and is not judged here.
 */
final class CorrelatedAggregatePlace {

    private final QueryExecutor executor;
    private final PlanEcho echo;
    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final List<Table> allTables;

    /**
     * @param table        the query's relation
     * @param aliasToTable its relations by the names it gives them, or null
     * @param allTables    every relation of its FROM clause, or null
     */
    CorrelatedAggregatePlace(final QueryExecutor executor, final PlanEcho echo, final Table table,
                             final Map<String, Table> aliasToTable, final List<Table> allTables) {
        this.executor = executor;
        this.echo = echo;
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.allTables = allTables;
    }

    /** Refuses the statement where a subquery holding the query's own aggregate stands outside its select list and HAVING. */
    void validate(final FrostlakeParser.SelectStatementContext statement,
                  final FrostlakeParser.SelectClauseContext clause) {
        final List<ParseTree> places = new ArrayList<>();
        if (clause.whereClause() != null) {
            places.add(clause.whereClause());
        }
        if (clause.tableExpression() != null) {
            for (final FrostlakeParser.JoinClauseContext join : clause.tableExpression().joinClause()) {
                if (join.booleanExpr() != null) {
                    places.add(join.booleanExpr());
                }
            }
        }
        if (clause.groupByClause() != null) {
            places.add(clause.groupByClause());
            final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
            for (final FrostlakeParser.GroupByElementContext element : clause.groupByClause().groupByElement()) {
                if (element.expression() != null) {
                    keys.add(element.expression());
                }
            }
            addKeyedItems(clause, keys, places);
            if (clause.groupByClause().ALL() != null) {
                // GROUP BY ALL groups by every item that aggregates nothing of the query's own.
                for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
                    if (!executor.holdsAggregate(item)) {
                        places.add(item);
                    }
                }
            }
        }
        if (statement != null && statement.orderByClause() != null && statement.selectOperand().size() == 1
                && statement.selectOperand(0).selectClause() == clause) {
            // A grouped or DISTINCT query's ORDER BY holds no scalar subquery at all, and says so in its own
            // sentence (see OrderByExecutor.rejectSubqueryInGroupedOrderKey); every other key is judged here.
            final boolean grouped = clause.groupByClause() != null || clause.DISTINCT() != null;
            final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
            for (final FrostlakeParser.OrderItemContext item : statement.orderByClause().orderItem()) {
                keys.add(item.expression());
                if (!grouped || !holdsScalarSubquery(item.expression())) {
                    places.add(item.expression());
                }
            }
            addKeyedItems(clause, keys, places);
        }
        refuse(places);
    }

    /** Refuses the statement where one of {@code places} holds a subquery that holds the query's own aggregate. */
    void refuse(final List<? extends ParseTree> places) {
        CorrelatedSubqueryRule rule = null;
        for (final ParseTree place : places) {
            final List<FrostlakeParser.SelectStatementContext> subqueries = new ArrayList<>();
            collectSubqueries(place, subqueries);
            for (final FrostlakeParser.SelectStatementContext subquery : subqueries) {
                // The names are gathered for the first subquery that aggregates at all: most hold no aggregate.
                if (!executor.holdsAggregateAnywhere(subquery)) {
                    continue;
                }
                if (rule == null) {
                    rule = new CorrelatedSubqueryRule(executor, offeredNames(table, aliasToTable, allTables));
                }
                final List<ParserRuleContext> aggregates = rule.outerAggregates(subquery);
                if (!aggregates.isEmpty()) {
                    throw new RuntimeException(SqlCompilationError.of("Subquery containing correlated aggregate function ["
                        + echo.print(aggregates.get(0)) + "] can only appear in having or select clause"));
                }
            }
        }
    }

    /** Whether a key holds, outside any nested select, a subquery read as a value. */
    private static boolean holdsScalarSubquery(final ParseTree node) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext
                || node instanceof FrostlakeParser.FunctionArgContext
                    && ((FrostlakeParser.FunctionArgContext) node).selectStatement() != null) {
            return true;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsScalarSubquery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The select items the keys name by position or alias, which stand where the keys do. */
    private static void addKeyedItems(final FrostlakeParser.SelectClauseContext clause,
                                      final List<FrostlakeParser.ExpressionContext> keys, final List<ParseTree> into) {
        for (final FrostlakeParser.ExpressionContext key : keys) {
            final ParseTree item = CorrelatedSubqueryRule.groupedItem(clause, key);
            if (item != key) {
                into.add(item);
            }
        }
    }

    /** The subqueries a node holds, the outermost ones only. */
    private static void collectSubqueries(final ParseTree node,
                                          final List<FrostlakeParser.SelectStatementContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            into.add((FrostlakeParser.SelectStatementContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSubqueries(node.getChild(i), into);
        }
    }

    /**
     * The names the query's row offers a subquery, upper-cased: each column bare, qualified by its relation's
     * name, and qualified by the name the FROM clause gives its relation.
     */
    private static Set<String> offeredNames(final Table table, final Map<String, Table> aliasToTable,
                                            final List<Table> allTables) {
        final Set<String> names = new HashSet<>();
        if (table != null) {
            addColumns(names, table.getName(), table);
        }
        if (allTables != null) {
            for (final Table relation : allTables) {
                addColumns(names, relation.getName(), relation);
            }
        }
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> relation : aliasToTable.entrySet()) {
                addColumns(names, relation.getKey(), relation.getValue());
            }
        }
        return names;
    }

    private static void addColumns(final Set<String> names, final String qualifier, final Table relation) {
        for (final TableColumn column : relation.getColumns()) {
            final String name = column.getName().toUpperCase(Locale.ROOT);
            names.add(name);
            if (qualifier != null) {
                names.add(qualifier.toUpperCase(Locale.ROOT) + "." + name);
            }
        }
    }
}
