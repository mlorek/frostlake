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
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The part of a WHERE clause that filters the rows a table function is called for. A table function written
 * over the relations to its left — {@code FROM t, LATERAL FLATTEN(t.v) f WHERE t.k > 1 AND f.value = 2} —
 * receives only the rows the conditions on those relations keep: the account applies {@code t.k > 1} first, so
 * the function is never called for a row it removes, and the rows it is called for are numbered from 1 (the SEQ
 * column of FLATTEN and of the splitting functions, live-verified). The WHERE clause still judges every row
 * afterwards, whole.
 *
 * <p>Only a conjunct that can be judged from a left row alone, with the same answer before and after the call,
 * is taken: every name in it is a column of a relation to the left and of nothing the function answers, and it
 * holds no sequence read and no call but a built-in scalar function's that answers alike each time it is asked —
 * which keeps anything that may answer differently the second time out. A subquery in it is one whose every name
 * is its own ({@code t.k > (SELECT MIN(k) FROM t)}, an IN's, a NOT IN's) or an EXISTS that reads the row; a value
 * read through the row ({@code t.k > (SELECT … WHERE x.k < t.k)}) is left to the WHERE (live-verified), and so
 * is a subquery a name of which may be the function's or a later relation's — one that is neither its own nor a
 * left relation's, or a bare one among relations whose columns are not known. A query block with a RIGHT or FULL
 * join, or with the {@code (+)} join marker, takes none: there a condition on the left relations also decides
 * which rows are null-extended.
 */
final class LateralInputFilter {

    /** The built-in functions whose answer changes from one call to the next. */
    private static final Set<String> VARYING = new HashSet<>(Arrays.asList(
        "RANDOM", "UUID_STRING", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "RANDSTR", "UNIFORM", "NORMAL", "ZIPF"));

    private final ExpressionEvaluator evaluator;
    private final List<Expression> conjuncts = new ArrayList<>();
    private final List<SourcePosition> origins = new ArrayList<>();

    private LateralInputFilter(final ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * The filter a query block's WHERE puts before a table function.
     *
     * @param executor     the executor running the block
     * @param select       the block's select clause, or null
     * @param leftTable    the relation joined so far
     * @param scope        the relations to the left, by the names the query gives them
     * @param scopeTables  the relations to the left, in combined-row order
     * @param lateralTable the function's own shape
     * @return the filter, or null when the WHERE holds nothing to apply first
     */
    static LateralInputFilter of(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext select,
                                 final Table leftTable, final Map<String, Table> scope,
                                 final List<Table> scopeTables, final Table lateralTable) {
        if (select == null || select.whereClause() == null || select.whereClause().booleanExpr() == null
                || select.tableExpression() == null || nullExtendsTheLeft(select.tableExpression())
                || holdsJoinMarker(select.whereClause())) {
            return null;
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(leftTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        evaluator.setMultiTableContext(scope, scopeTables);
        final LateralInputFilter filter = new LateralInputFilter(evaluator);
        final List<FrostlakeParser.BooleanExprContext> written = new ArrayList<>();
        collectConjuncts(select.whereClause().booleanExpr(), written);
        final Set<String> offered = offeredNames(scope, scopeTables);
        for (final FrostlakeParser.BooleanExprContext conjunct : written) {
            final int[] names = new int[1];
            if (!readsTheLeftAlone(conjunct, executor, offered, scope, scopeTables, lateralTable, names)
                    || names[0] == 0) {
                continue;
            }
            try {
                filter.conjuncts.add(ExpressionEvaluator.parse(executor.getOriginalText(conjunct)));
                filter.origins.add(new SourcePosition(conjunct.getStart().getLine(),
                    conjunct.getStart().getCharPositionInLine()));
            } catch (final RuntimeException unparsed) {
                // A conjunct that does not parse on its own is left to the WHERE clause.
            }
        }
        return filter.conjuncts.isEmpty() ? null : filter;
    }

    /**
     * Whether a left row reaches the function.
     *
     * @param leftRow the row
     * @return false when a condition on the left relations removes it
     */
    boolean accepts(final Row leftRow) {
        for (int i = 0; i < conjuncts.size(); i++) {
            final SourcePosition displaced = ExpressionSource.beginNested(origins.get(i));
            try {
                if (!SqlTruth.isTrue(evaluator.evaluate(conjuncts.get(i), leftRow))) {
                    return false;
                }
            } finally {
                ExpressionSource.end(displaced);
            }
        }
        return true;
    }

    /** The top-level AND operands of a condition; a parenthesized conjunction stays one conjunct. */
    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext condition,
                                         final List<FrostlakeParser.BooleanExprContext> conjuncts) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            collectConjuncts(((FrostlakeParser.AndExprContext) condition).booleanExpr(0), conjuncts);
            collectConjuncts(((FrostlakeParser.AndExprContext) condition).booleanExpr(1), conjuncts);
            return;
        }
        conjuncts.add(condition);
    }

    private static boolean readsTheLeftAlone(final ParseTree node, final QueryExecutor executor,
                                             final Set<String> offered, final Map<String, Table> scope,
                                             final List<Table> scopeTables, final Table lateralTable,
                                             final int[] names) {
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // A query whose every name is its own answers alike for every left row, and an EXISTS that reads the
            // row is judged from it alone; a value read through the row is left to the WHERE, as is a query a
            // name of which may be the function's or a later relation's.
            if (holdsVaryingCall(node)) {
                return false;
            }
            final CorrelationSide reach = new CorrelatedSubqueryRule(executor, offered)
                .reach((FrostlakeParser.SelectStatementContext) node);
            if (reach == CorrelationSide.INNER) {
                return true;
            }
            if (reach == CorrelationSide.OUTER && node.getParent() instanceof FrostlakeParser.ExistsExprContext) {
                names[0]++;
                return true;
            }
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && !answersAlikeEachTime((FrostlakeParser.FunctionCallExprContext) node,
                    executor.getFunctionRegistry())) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            names[0]++;
            return namesALeftColumn(ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName()), scope, scopeTables, lateralTable);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!readsTheLeftAlone(node.getChild(i), executor, offered, scope, scopeTables, lateralTable, names)) {
                return false;
            }
        }
        return true;
    }

    /** Every name the left relations offer a subquery, upper-cased: bare columns and relation-qualified ones. */
    private static Set<String> offeredNames(final Map<String, Table> scope, final List<Table> scopeTables) {
        final Set<String> names = new HashSet<>();
        for (final Table relation : scopeTables) {
            if (relation != null) {
                addColumns(names, null, relation);
            }
        }
        for (final Map.Entry<String, Table> named : scope.entrySet()) {
            if (named.getValue() != null) {
                addColumns(names, named.getKey(), named.getValue());
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

    /** Whether a query calls a function whose answer changes from one call to the next. */
    private static boolean holdsVaryingCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String[] name = ParseTreeText.functionNameParts(
                ((FrostlakeParser.FunctionCallExprContext) node).functionName());
            if (name.length == 1 && VARYING.contains(name[0].toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsVaryingCall(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call is a built-in scalar function's, written plainly, that answers alike each time. */
    private static boolean answersAlikeEachTime(final FrostlakeParser.FunctionCallExprContext call,
                                                final FunctionRegistry functions) {
        final String[] name = ParseTreeText.functionNameParts(call.functionName());
        if (name.length != 1 || call.overClause() != null) {
            return false;
        }
        final String upper = name[0].toUpperCase(Locale.ROOT);
        return functions.hasFunction(upper) && !functions.hasAggregateFunction(upper) && !VARYING.contains(upper)
            && !upper.startsWith("SYSTEM$");
    }

    private static boolean namesALeftColumn(final String[] parts, final Map<String, Table> scope,
                                            final List<Table> scopeTables, final Table lateralTable) {
        if (parts.length == 1) {
            if (lateralTable != null && lateralTable.hasColumn(parts[0])) {
                return false;
            }
            int carrying = 0;
            for (final Table relation : scopeTables) {
                if (relation != null && relation.hasColumnExactly(parts[0])) {
                    carrying++;
                }
            }
            return carrying == 1;
        }
        if (parts.length != 2 || "NEXTVAL".equalsIgnoreCase(parts[1]) || "CURRVAL".equalsIgnoreCase(parts[1])) {
            return false;
        }
        for (final Map.Entry<String, Table> named : scope.entrySet()) {
            if (named.getKey().equalsIgnoreCase(parts[0])) {
                return named.getValue() != null && named.getValue().hasColumnExactly(parts[1]);
            }
        }
        return false;
    }

    /** Whether a FROM clause holds a join that null-extends the relations to its left. */
    private static boolean nullExtendsTheLeft(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.JoinTypeContext) {
            final FrostlakeParser.JoinTypeContext type = (FrostlakeParser.JoinTypeContext) node;
            return type.RIGHT() != null || type.FULL() != null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (nullExtendsTheLeft(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a {@code (+)} marker is written under the node: {@code t.c(+)}, or one following an operand. */
    private static boolean holdsJoinMarker(final ParseTree node) {
        if (node instanceof FrostlakeParser.OuterJoinColumnExprContext
                || node instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsJoinMarker(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
