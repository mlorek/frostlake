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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Set;

/**
 * A query's sources, checked the way live checks them before anything else in the statement: every relation
 * named must exist and no query level may register one name twice, in the query's subqueries, CTEs and
 * derived tables as in the query itself. Such a fault outranks an unknown column or function, an argument
 * count or type, a grouping or ordering rule and a missing window over QUALIFY, wherever each is written,
 * even over an empty table.
 *
 * <p>The order is live's, scope by scope:
 * <ul>
 *   <li>a WITH clause's bodies come ahead of the query they serve;</li>
 *   <li>a query level's own FROM clause comes first: its missing relations wherever they stand in the
 *   clause, then its sources' names in written order, where a derived table's or a join condition's query is
 *   checked at the point it is written, so {@code FROM (SELECT 1 FROM t x, t x) d, u d} is
 *   {@code duplicate alias 'X'};</li>
 *   <li>then the queries inside the level's other clauses, in written order, each whole before the next:
 *   {@code SELECT (SELECT 1 FROM nosuch) FROM t y, t y} is {@code duplicate alias 'Y'}.</li>
 * </ul>
 */
final class StatementSourceCheck {

    private final QueryExecutor executor;
    private final Set<String> cteNames = new HashSet<>();

    /**
     * @param executor  the executor that resolves relations
     * @param statement the statement whose CTE names count as relations anywhere in it
     */
    StatementSourceCheck(final QueryExecutor executor, final ParseTree statement) {
        this.executor = executor;
        QueryExecutor.collectCteNames(statement, cteNames);
    }

    /** Check a query and every query inside it, raising the first fault in live's order. */
    void check(final FrostlakeParser.SelectStatementContext statement) {
        if (statement.withClause() != null) {
            for (final FrostlakeParser.CteDefinitionContext cte : statement.withClause().cteDefinition()) {
                check(cte.selectStatement());
            }
        }
        for (final FrostlakeParser.SelectOperandContext operand : statement.selectOperand()) {
            if (operand.selectStatement() != null) {
                check(operand.selectStatement());
            } else {
                checkLevel(operand.selectClause());
            }
        }
        if (statement.orderByClause() != null) {
            checkQueriesIn(statement.orderByClause());
        }
    }

    private void checkLevel(final FrostlakeParser.SelectClauseContext level) {
        final FrostlakeParser.TableExpressionContext from = level.tableExpression();
        if (from != null) {
            executor.requireRelationsIn(from, cteNames);
            final FromSourceNames names = new FromSourceNames(executor);
            for (final ParseTree source : from.children) {
                if (source instanceof FrostlakeParser.TableReferenceContext) {
                    register((FrostlakeParser.TableReferenceContext) source, names);
                } else if (source instanceof FrostlakeParser.JoinClauseContext) {
                    final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) source;
                    register(join.tableReference(), names);
                    for (final ParseTree part : join.children) {
                        if (part != join.tableReference()) {
                            checkQueriesIn(part);
                        }
                    }
                }
            }
        }
        for (final ParseTree clause : level.children) {
            if (clause != from) {
                checkQueriesIn(clause);
            }
        }
    }

    /** One source: the queries written inside it, then the name it registers. */
    private void register(final FrostlakeParser.TableReferenceContext source, final FromSourceNames names) {
        checkQueriesIn(source);
        names.registerReference(source);
        names.rejectDuplicate();
    }

    /** Every query written inside {@code node} and not inside another of them, in written order. */
    private void checkQueriesIn(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            check((FrostlakeParser.SelectStatementContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkQueriesIn(node.getChild(i));
        }
    }
}
