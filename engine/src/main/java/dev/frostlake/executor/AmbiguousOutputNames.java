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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A name two output columns of one select list carry. The query itself runs, but a bare reference to that name is
 * refused while the statement compiles: {@code ambiguous column name 'X'}, unpositioned (all live-verified).
 *
 * <ul>
 *   <li>ORDER BY reads the output columns first, so any two of one name make it ambiguous there — {@code SELECT a,
 *       a … ORDER BY a} included;</li>
 *   <li>WHERE, GROUP BY, HAVING and QUALIFY reach an output name only through an alias, so there the name is
 *       ambiguous when one of its columns is an alias — {@code SELECT a AS a2, a2 …} and {@code SELECT a AS x,
 *       n AS x …} — while {@code SELECT a, a … WHERE a > 1} runs;</li>
 *   <li>a select item reads the names the items BEFORE it carry, so {@code SELECT a AS x, n AS x, x + 0 …} is
 *       ambiguous while {@code SELECT d AS d2, DATEADD(day, 1, d2), d2 …} and {@code SELECT a + 0.5 AS a, a …}
 *       run.</li>
 * </ul>
 *
 * <p>WHERE and a select item read a column of the relation first, so a relation that carries the name answers it
 * there.
 *
 * <p>A position ({@code ORDER BY 2}) and {@code GROUP BY ALL} name no column and pass.
 */
final class AmbiguousOutputNames {

    private AmbiguousOutputNames() {
    }

    /**
     * Refuse a bare reference to a name the select list carries twice.
     *
     * @param select    the select clause
     * @param statement the statement holding it, for its ORDER BY; null when the ORDER BY belongs elsewhere
     * @param relations the relations the query reads
     */
    static void reject(final FrostlakeParser.SelectClauseContext select,
                       final FrostlakeParser.SelectStatementContext statement, final List<Table> relations) {
        if (select == null || select.selectList() == null) {
            return;
        }
        final Map<String, Integer> counts = new HashMap<>();
        final Set<String> aliased = new HashSet<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            final String alias = SelectItemAccessors.getItemAlias(item);
            final String name = alias != null ? alias : bareName(item);
            if (name == null) {
                continue;
            }
            final Integer seen = counts.get(name);
            counts.put(name, seen == null ? 1 : seen + 1);
            if (alias != null) {
                aliased.add(alias);
            }
        }
        final Set<String> ambiguous = new HashSet<>();
        final Set<String> repeated = new HashSet<>();
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                repeated.add(entry.getKey());
                if (aliased.contains(entry.getKey())) {
                    ambiguous.add(entry.getKey());
                }
            }
        }
        if (repeated.isEmpty()) {
            return;
        }
        rejectInSelectItems(select, relations);
        final Set<String> unanswered = new HashSet<>();
        for (final String name : ambiguous) {
            if (!carriedBy(relations, name)) {
                unanswered.add(name);
            }
        }
        refuseReferences(select.whereClause(), unanswered);
        if (select.groupByClause() != null && select.groupByClause().ALL() == null) {
            refuseReferences(select.groupByClause(), ambiguous);
        }
        refuseReferences(select.havingClause(), ambiguous);
        refuseReferences(select.qualifyClause(), ambiguous);
        if (statement != null) {
            refuseReferences(statement.orderByClause(), repeated);
        }
    }

    /**
     * Refuse a select item reading a name two items BEFORE it carry, one of them an alias, and no relation carries.
     */
    private static void rejectInSelectItems(final FrostlakeParser.SelectClauseContext select,
                                            final List<Table> relations) {
        final Map<String, Integer> carried = new HashMap<>();
        final Set<String> aliased = new HashSet<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            final Set<String> ambiguousHere = new HashSet<>();
            for (final Map.Entry<String, Integer> entry : carried.entrySet()) {
                if (entry.getValue() > 1 && aliased.contains(entry.getKey()) && !carriedBy(relations, entry.getKey())) {
                    ambiguousHere.add(entry.getKey());
                }
            }
            if (!ambiguousHere.isEmpty()) {
                refuseReferences(item, ambiguousHere);
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            final String name = alias != null ? alias : bareName(item);
            if (name != null) {
                final Integer seen = carried.get(name);
                carried.put(name, seen == null ? 1 : seen + 1);
                if (alias != null) {
                    aliased.add(alias);
                }
            }
        }
    }

    /** Whether a relation the query reads carries a column of exactly this name. */
    private static boolean carriedBy(final List<Table> relations, final String name) {
        if (relations != null) {
            for (final Table relation : relations) {
                if (relation != null && relation.hasColumnExactly(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The name a bare (possibly parenthesized, possibly qualified) column reference gives its item, or null. */
    private static String bareName(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        final FrostlakeParser.ExpressionContext simple = value == null ? null : SelectItemAccessors.unwrapParens(value);
        if (!(simple instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) simple).qualifiedName());
        return parts[parts.length - 1];
    }

    /** Refuse the first bare one-part name in {@code node} that is ambiguous, leaving nested queries alone. */
    private static void refuseReferences(final ParseTree node, final Set<String> ambiguous) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (parts.length == 1 && ambiguous.contains(parts[0])) {
                throw new RuntimeException(SqlCompilationError.of("ambiguous column name '" + parts[0] + "'"));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            refuseReferences(node.getChild(i), ambiguous);
        }
    }
}
