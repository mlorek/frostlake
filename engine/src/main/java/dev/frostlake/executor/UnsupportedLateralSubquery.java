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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The LATERAL derived tables the account cannot evaluate, refused unpositioned while the statement compiles:
 * {@code Unsupported subquery type cannot be evaluated}. Each reads the row to its left, and is (all live-verified):
 *
 * <ul>
 *   <li>a body under LIMIT or FETCH, ordered or not, however it joins — a comma, a join with or without ON;</li>
 *   <li>a body reading a table function over the outer row — {@code TABLE(FLATTEN(input => j.arr))} — however it
 *       joins; a GENERATOR whose bounds read the outer row only through columns the statistics pin to one value
 *       reads a constant there (see {@link BoundColumnPins}), and its body is evaluated unless it aggregates:
 *       {@code LATERAL (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => g1.n + 1)))} runs over a one-row g1, its
 *       {@code SELECT COUNT(*)} is refused;</li>
 *   <li>a FROM-less body under a LEFT or FULL join with an ON condition — unless the relation to its left holds
 *       one row and nothing outside the body reads the derived table, which the account's planner evaluates.</li>
 * </ul>
 *
 * <p>A body reading a table of its own under any join, and a FROM-less body without an ON, are evaluated.
 */
final class UnsupportedLateralSubquery {

    private UnsupportedLateralSubquery() {
    }

    /**
     * The refusal for a lateral body the account cannot evaluate, or null.
     *
     * @param query   the lateral body
     * @param joinCtx the join it stands in, or null for a comma
     * @param outer   the names the relations to its left answer to
     * @param left    the relation to its left
     * @param leftRows the rows the relation to its left holds, or -1 when that is not known before reading it
     * @param readOutside whether anything outside the body reads the derived table
     * @param pins     the columns of the relations to its left the statistics pin, or null
     * @param functions the functions, which tell an aggregate
     * @return the refusal, or null
     */
    static RuntimeException refusal(final FrostlakeParser.SelectStatementContext query,
                                    final FrostlakeParser.JoinClauseContext joinCtx, final Set<String> outer,
                                    final Table left, final long leftRows, final boolean readOutside,
                                    final BoundColumnPins pins, final FunctionRegistry functions) {
        final Set<String> names = new HashSet<>();
        if (outer != null) {
            for (final String name : outer) {
                names.add(name.toUpperCase(Locale.ROOT));
            }
        }
        if (left != null && left.getName() != null) {
            names.add(left.getName().toUpperCase(Locale.ROOT));
        }
        if (names.isEmpty() || !readsAny(query, names)) {
            return null;
        }
        final boolean limited = query.limitClause() != null || query.fetchClause() != null;
        final boolean fromlessUnderOn = outerJoinWithOn(joinCtx) && fromless(query) && (leftRows != 1 || readOutside);
        final BoundColumnPins constants = pins == null || aggregates(query, functions) ? null : pins;
        if (limited || readsTableFunctionOver(query, names, constants) || fromlessUnderOn) {
            return new RuntimeException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"));
        }
        return null;
    }

    /**
     * Whether the query reads the derived table {@code source} anywhere outside it and the ON condition that joins
     * it: a name qualified by its alias, a bare name one of its columns answers to, or a star.
     *
     * @param select  the query
     * @param source  the derived table
     * @param joinCtx the join it stands in, or null for a comma
     * @return whether it is read
     */
    static boolean readsOutside(final FrostlakeParser.SelectClauseContext select,
                                final FrostlakeParser.TableReferenceContext source,
                                final FrostlakeParser.JoinClauseContext joinCtx) {
        if (select == null) {
            return true;
        }
        final String alias = source.aliasName() != null ? ParseTreeText.getIdentifier(source.aliasName())
            : source.nonJoinKeywordIdentifier() != null ? source.nonJoinKeywordIdentifier().getText() : null;
        final Set<String> columns = new HashSet<>();
        final FrostlakeParser.SelectStatementContext body = source.tableSource().selectStatement();
        if (body.selectOperand().size() > 0 && body.selectOperand(0).selectClause() != null) {
            for (final FrostlakeParser.SelectItemContext item
                    : body.selectOperand(0).selectClause().selectList().selectItem()) {
                final ParseTree last = item.getChild(item.getChildCount() - 1);
                columns.add(last.getText().toUpperCase(Locale.ROOT));
            }
        }
        return reads(select, source, joinCtx == null ? null : joinCtx.booleanExpr(),
            alias == null ? null : alias.toUpperCase(Locale.ROOT), columns);
    }

    private static boolean reads(final ParseTree node, final FrostlakeParser.TableReferenceContext source,
                                 final ParseTree on, final String alias, final Set<String> columns) {
        if (node == source || node == on) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameContext) {
            final FrostlakeParser.QualifiedNameContext name = (FrostlakeParser.QualifiedNameContext) node;
            if (name.nameStartPart() != null) {
                final String first = ParseTreeText.namePartText(name.nameStartPart()).toUpperCase(Locale.ROOT);
                if (name.namePart().isEmpty() ? columns.contains(first) : first.equals(alias)) {
                    return true;
                }
            }
        }
        if (node instanceof FrostlakeParser.SelectItemContext && node.getText().endsWith("*")) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (reads(node.getChild(i), source, on, alias, columns)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a LEFT or FULL join carries an ON condition. */
    private static boolean outerJoinWithOn(final FrostlakeParser.JoinClauseContext joinCtx) {
        return joinCtx != null && joinCtx.ON() != null && joinCtx.joinType() != null
            && (joinCtx.joinType().LEFT() != null || joinCtx.joinType().FULL() != null);
    }

    /** Whether a body is one SELECT with no FROM. */
    private static boolean fromless(final FrostlakeParser.SelectStatementContext query) {
        return query.setOperator().isEmpty() && query.selectOperand().size() == 1
            && query.selectOperand(0).selectClause() != null && query.selectOperand(0).selectClause().FROM() == null;
    }

    /**
     * Whether a table function in the body's FROM reads one of the outer names; a GENERATOR reading them only through
     * columns {@code pins} pins does not, where pins are given.
     */
    private static boolean readsTableFunctionOver(final ParseTree node, final Set<String> names,
                                                  final BoundColumnPins pins) {
        if (node instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.TableSourceContext source = (FrostlakeParser.TableSourceContext) node;
            if ((source.TABLE() != null || source.FLATTEN() != null || source.tableFunctionExpr() != null)
                    && readsAny(source, names) && !(pins != null && readsPinnedOnly(source, names, pins))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsTableFunctionOver(node.getChild(i), names, pins)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a source is a GENERATOR call whose every outer name is a column the statistics pin. */
    private static boolean readsPinnedOnly(final FrostlakeParser.TableSourceContext source, final Set<String> names,
                                           final BoundColumnPins pins) {
        if (!(source.expression() instanceof FrostlakeParser.FunctionCallNamedArgsExprContext)) {
            return false;
        }
        final FrostlakeParser.FunctionCallNamedArgsExprContext call =
            (FrostlakeParser.FunctionCallNamedArgsExprContext) source.expression();
        return "GENERATOR".equalsIgnoreCase(call.functionName().getText()) && outerNamesPinned(call, names, pins);
    }

    private static boolean outerNamesPinned(final ParseTree node, final Set<String> names,
                                            final BoundColumnPins pins) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext && readsAny(node, names)) {
            return pins.pins((FrostlakeParser.QualifiedNameExprContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!outerNamesPinned(node.getChild(i), names, pins)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the body's own select lists call an aggregate, outside any query nested in them. */
    private static boolean aggregates(final ParseTree node, final FunctionRegistry functions) {
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext && functions != null
                && functions.getAggregateFunction(((FrostlakeParser.FunctionCallExprContext) node).functionName()
                    .getText().toUpperCase(Locale.ROOT)) != null) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (!(child instanceof FrostlakeParser.SelectStatementContext) && aggregates(child, functions)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a subtree names a column through one of {@code names}: {@code f.a} for the outer name F. */
    private static boolean readsAny(final ParseTree node, final Set<String> names) {
        if (node instanceof FrostlakeParser.QualifiedNameContext) {
            final FrostlakeParser.QualifiedNameContext name = (FrostlakeParser.QualifiedNameContext) node;
            if (name.nameStartPart() != null && !name.namePart().isEmpty()
                    && names.contains(ParseTreeText.namePartText(name.nameStartPart()).toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsAny(node.getChild(i), names)) {
                return true;
            }
        }
        return false;
    }
}
