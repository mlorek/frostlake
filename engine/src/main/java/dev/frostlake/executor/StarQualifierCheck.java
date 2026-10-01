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

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A star whose qualifier names no relation of its query's FROM clause — {@code q.*} or {@code {q.*}} — refused
 * as live refuses it: {@code Object 'Q' does not exist or not authorized.}, as soon as the FROM clause's
 * relations are resolved and ahead of every other rule of the query: its select list's and its clauses' names,
 * its functions, its join conditions, its table functions' arguments, its grouping and its ordinals (all
 * live-verified).
 *
 * <p>What resolves with the relations still comes first: a missing relation, a duplicate alias, a USING column,
 * a CHANGES clause's tracking, and a derived table's or a lateral subquery's own compilation. A query nested in
 * another is checked when it compiles, so a bad name in the outer query outranks a bad star in a subquery,
 * while a bad star in the outer query outranks one in a subquery of its select list.
 *
 * <p>A one-part qualifier is matched against the names the FROM clause's sources register (see
 * {@link FromSourceNames}): an alias hides its table's own name, so {@code fz.*} over {@code FROM fz f} names
 * nothing, and a quoted name keeps its case. When nothing in the FROM clause resolves with the relations, that
 * match runs before the sources are read, ahead of a join condition or a table function's argument, which are
 * evaluated as the sources are read; otherwise it runs once they are read. A qualified qualifier,
 * {@code PUBLIC.FZ.*}, is matched against the relations themselves once they are read. A source whose name is
 * not known here leaves a one-part qualifier to the projection's own check.
 */
final class StarQualifierCheck {

    private final QueryExecutor executor;

    StarQualifierCheck(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Refuse a one-part star qualifier no source registers, before the sources are read, when nothing in the
     * FROM clause resolves with the relations.
     *
     * @param ctx        the select clause
     * @param cteResults the CTEs computed for the statement, or null
     */
    void rejectBeforeSources(final FrostlakeParser.SelectClauseContext ctx, final Map<String, ?> cteResults) {
        final FrostlakeParser.TableExpressionContext from = ctx.tableExpression();
        if (from == null || resolvesWithRelations(from)) {
            return;
        }
        Set<String> names = null;
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final FrostlakeParser.StarQualifiedNameContext qualifier = qualifierOf(item);
            if (qualifier == null) {
                continue;
            }
            if (!qualifier.namePart().isEmpty()) {
                // Matched against the relations once they are read, and it comes first in the list.
                return;
            }
            if (names == null) {
                names = new FromSourceNames(executor).sourceNames(from);
                if (names == null) {
                    return;
                }
            }
            if (!names.contains(ParseTreeText.qualifiedNameParts(qualifier)[0])) {
                executor.requireRelationsIn(from, cteNames(from, cteResults));
                throw refusal(qualifier);
            }
        }
    }

    /**
     * Refuse a star qualifier naming no relation, once the FROM clause has been read into its relations.
     *
     * @param ctx       the select clause
     * @param relations the FROM clause's relations, by the key each registered
     */
    void rejectAfterSources(final FrostlakeParser.SelectClauseContext ctx, final FromClauseRelations relations) {
        final FrostlakeParser.TableExpressionContext from = ctx.tableExpression();
        if (from == null) {
            return;
        }
        Set<String> names = null;
        boolean namesRead = false;
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final FrostlakeParser.StarQualifiedNameContext qualifier = qualifierOf(item);
            if (qualifier == null) {
                continue;
            }
            if (qualifier.namePart().isEmpty()) {
                if (!namesRead) {
                    names = new FromSourceNames(executor).sourceNames(from);
                    namesRead = true;
                }
                if (names != null && !names.contains(ParseTreeText.qualifiedNameParts(qualifier)[0])) {
                    throw refusal(qualifier);
                }
            } else if (!namesARelation(SelectItemAccessors.getItemQualifierParts(item), relations)) {
                throw refusal(qualifier);
            }
        }
    }

    private static boolean namesARelation(final String[] written, final FromClauseRelations relations) {
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (QueryExecutor.starQualifierMatches(written, relation.getKey(), relation.getValue(),
                    relations.isAlias(relation.getKey()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether something in a FROM clause resolves together with its relations, ahead of a star's qualifier: a
     * derived table or a lateral subquery, a join's USING or NATURAL columns, an ASOF match, or a CHANGES
     * clause.
     */
    private static boolean resolvesWithRelations(final ParseTree node) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).selectStatement() != null) {
            return true;
        }
        if (node instanceof FrostlakeParser.JoinClauseContext) {
            final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) node;
            if (join.USING() != null || join.NATURAL() != null || join.ASOF() != null
                    || join.asofMatchCondition() != null) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.ChangesClauseContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.BooleanExprContext || node instanceof FrostlakeParser.ExpressionContext) {
            // A join condition or a table function's argument is evaluated after the qualifier.
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (resolvesWithRelations(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The qualifier of a qualified star or of a qualified braced star, or null for any other item. */
    private static FrostlakeParser.StarQualifiedNameContext qualifierOf(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            return ((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName();
        }
        if (item instanceof FrostlakeParser.ObjectStarItemContext) {
            return ((FrostlakeParser.ObjectStarItemContext) item).starQualifiedName();
        }
        return null;
    }

    /** The CTE names a relation of this level may name: the clause's own and those already computed. */
    private Set<String> cteNames(final FrostlakeParser.TableExpressionContext from, final Map<String, ?> cteResults) {
        final Set<String> names = new HashSet<>();
        QueryExecutor.collectCteNames(from, names);
        if (cteResults != null) {
            for (final String cte : cteResults.keySet()) {
                names.add(cte.toUpperCase());
            }
        }
        if (executor.getCurrentCteContext() != null) {
            for (final String cte : executor.getCurrentCteContext().keySet()) {
                names.add(cte.toUpperCase());
            }
        }
        return names;
    }

    private static RuntimeException refusal(final FrostlakeParser.StarQualifiedNameContext qualifier) {
        return new RuntimeException(SqlCompilationError.of("Object '" + QueryExecutor.starQualifierEcho(qualifier)
            + "' does not exist or not authorized."));
    }
}
