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

import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Whether a table function joined with an ON condition reads the row of the relations to its left. Live
 * refuses that lateral form with {@code Unsupported feature 'lateral table function called with OUTER JOIN
 * syntax or a join predicate (ON clause)'.}, and joins a call that reads nothing of them as an ordinary
 * relation: {@code f JOIN TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) fl ON fl.value = f.a} answers its
 * pairs, and a LEFT, RIGHT or FULL join null-extends as any join does (live-verified).
 *
 * <p>An argument reads the left row when it names a column of a relation to the left, a positional column,
 * or a name of the query around a subquery the join sits in — bare, or through a qualifier that names no
 * relation to the left, as {@code f.a} does in {@code … WHERE EXISTS (SELECT 1 FROM k1 g JOIN
 * TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.a))) fl ON TRUE)}, whatever clause holds the subquery. A name
 * inside a subquery of the arguments
 * resolves in that subquery unless it names a relation to the left by its qualifier, a lambda's parameter
 * is its own, and a context function written without parentheses or a session variable reads nothing. A
 * select alias counts as reading the left row: live refuses one whose expression does ({@code SELECT
 * ARRAY_CONSTRUCT(f.a) obs … JOIN TABLE(FLATTEN(obs)) fl ON TRUE}), and the value of one that does not is
 * not at hand where the call runs. An argument naming nothing at all is refused as the invalid identifier
 * it is, before the lateral form is: live names {@code NOSUCH} in {@code ARRAY_CONSTRUCT(f.a, nosuch)}.
 */
final class TableFunctionCorrelation {

    private TableFunctionCorrelation() {
    }

    /**
     * @param executor      the executor running the query block
     * @param source        the table function as written
     * @param leftTable     the relation joined so far
     * @param leftAliases   the FROM-clause keys of the relations to the left
     * @param leftTables    the relations to the left, in combined-row order
     * @param selectAliases the query block's select aliases
     * @param outerNames    the names of the query around this one, or null
     * @return true when an argument reads the left row, or when the call cannot be judged
     */
    static boolean readsLeft(final QueryExecutor executor, final FrostlakeParser.TableSourceContext source,
                             final Table leftTable, final Map<String, Table> leftAliases,
                             final List<Table> leftTables, final Set<String> selectAliases,
                             final Map<String, Object> outerNames) {
        final ParserRuleContext call = callOf(source);
        if (call == null) {
            return true;
        }
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(executor.getOriginalText(call));
        } catch (final RuntimeException unparseable) {
            return true;
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(leftTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        scope.setMultiTableContext(leftAliases, leftTables);
        scope.setScopeExemptNames(selectAliases);
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
            call.getStart().getLine(), call.getStart().getCharPositionInLine()));
        try {
            scope.validateColumnScope(parsed);
        } finally {
            ExpressionSource.end(displaced);
        }
        final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
        parsed.accept(walk);
        for (final ColumnReferenceExpression reference : walk.references()) {
            if (reads(reference, leftAliases, leftTables, selectAliases, outerNames)) {
                return true;
            }
        }
        return subqueryNamesLeftRelation(call, false, leftAliases);
    }

    /**
     * Whether a subquery among the arguments names a relation to the left by its qualifier — a correlated
     * argument, which only a per-row evaluation can answer.
     */
    private static boolean subqueryNamesLeftRelation(final ParseTree node, final boolean inSubquery,
                                                     final Map<String, Table> leftAliases) {
        if (inSubquery && node instanceof FrostlakeParser.QualifiedNameContext) {
            final FrostlakeParser.QualifiedNameContext name = (FrostlakeParser.QualifiedNameContext) node;
            if (!name.namePart().isEmpty()) {
                final String[] parts = ParseTreeText.qualifiedNameParts(name);
                if (relationNamed(leftAliases, parts[parts.length - 2]) != null) {
                    return true;
                }
            }
        }
        final boolean nested = inSubquery || node instanceof FrostlakeParser.SelectStatementContext;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (subqueryNamesLeftRelation(node.getChild(i), nested, leftAliases)) {
                return true;
            }
        }
        return false;
    }

    /** The call a table-function source makes, or null for any other source. */
    private static ParserRuleContext callOf(final FrostlakeParser.TableSourceContext source) {
        if (source == null) {
            return null;
        }
        if (source.TABLE() != null && source.expression() != null) {
            return source.expression();
        }
        if (source.FLATTEN() != null) {
            return source;
        }
        return source.tableFunctionExpr();
    }

    private static boolean reads(final ColumnReferenceExpression reference, final Map<String, Table> leftAliases,
                                 final List<Table> leftTables, final Set<String> selectAliases,
                                 final Map<String, Object> outerNames) {
        if (reference.getPositionalOrdinal() > 0) {
            return true;
        }
        final String column = reference.getColumnName();
        if (reference.isQualified()) {
            final String qualifier = reference.getTableName();
            final String relationName = qualifier.substring(qualifier.lastIndexOf('.') + 1);
            final Table relation = relationNamed(leftAliases, relationName);
            if (relation != null) {
                return carries(relation, column);
            }
            // A qualifier naming no relation to the left names one of the query around the join, which a
            // row-time run may bind under the relation's own name rather than its alias: the call reads the
            // outer row either way.
            return true;
        }
        for (final Table relation : leftTables) {
            if (carries(relation, column)) {
                return true;
            }
        }
        if (selectAliases != null && selectAliases.contains(column)) {
            return true;
        }
        return binds(outerNames, column);
    }

    private static Table relationNamed(final Map<String, Table> aliases, final String name) {
        if (aliases == null) {
            return null;
        }
        final Table exact = aliases.get(name);
        if (exact != null) {
            return exact;
        }
        for (final Map.Entry<String, Table> entry : aliases.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean carries(final Table relation, final String column) {
        for (final TableColumn each : relation.getColumns()) {
            if (each.getName().equals(column)) {
                return true;
            }
        }
        return false;
    }

    private static boolean binds(final Map<String, Object> names, final String key) {
        return names != null && (names.containsKey(key) || names.containsKey(key.toUpperCase(Locale.ROOT)));
    }
}
