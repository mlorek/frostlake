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
import dev.frostlake.storage.Row;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The reads of a GENERATOR a select answers without holding its rows. The account streams a GENERATOR's rows, so a
 * select reading one alone answers past the rows a list holds where it holds none of them (all live-verified):
 *
 * <ul>
 *   <li>constant aggregates over the rows alone count them: {@code SELECT COUNT(*), MAX(1) FROM
 *       TABLE(GENERATOR(ROWCOUNT => 3000000000))} is 3000000000 and 1, under a WHERE that is TRUE too - COUNT of the
 *       rows or of a constant, and a constant's MIN, MAX or ANY_VALUE;</li>
 *   <li>a WHERE that computes a value of each row, reading no name, is tested row by row under the select's own
 *       LIMIT, until the LIMIT is met: {@code SELECT 1 AS x FROM … WHERE UNIFORM(1, 2, RANDOM()) = 1 LIMIT 4} is four
 *       rows;</li>
 *   <li>a WHERE over a derived table's literal columns is decided before any row is read, so the LIMIT of the select
 *       reading it bounds the rows: {@code SELECT x FROM (SELECT 1 AS x FROM …) WHERE x = 1 LIMIT 2} is 1 and 1.</li>
 * </ul>
 */
final class GeneratorRowReads {

    /** The aggregates a select counting the rows takes. */
    private static final Set<String> COUNTED = new HashSet<>(Arrays.asList("COUNT", "MIN", "MAX", "ANY_VALUE"));

    private GeneratorRowReads() {
    }

    /**
     * Whether the select reading a FROM item alone holds only constant aggregates over its rows: see the class.
     *
     * @param executor  the executor that judges the select's WHERE
     * @param reference the FROM item
     * @return true where the rows are counted, not held
     */
    static boolean counts(final QueryExecutor executor, final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.SelectClauseContext select = soleReader(reference);
        if (select == null) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext statement = statementOf(select);
        if (statement.orderByClause() != null || statement.limitClause() != null || statement.fetchClause() != null
                || select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null
                || select.whereClause() != null && !executor.keepsEveryRow(select.whereClause())) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)
                    || countedCall(((FrostlakeParser.ExprItemContext) item).booleanExpr()) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * The places of the COUNTs among a counting select's items: see {@link #counts}.
     *
     * @param select the select
     * @return the places, from 0
     */
    static List<Integer> countPlaces(final FrostlakeParser.SelectClauseContext select) {
        final List<Integer> places = new ArrayList<>();
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof FrostlakeParser.ExprItemContext
                    && "COUNT".equals(countedCall(((FrostlakeParser.ExprItemContext) items.get(i)).booleanExpr()))) {
                places.add(Integer.valueOf(i));
            }
        }
        return places;
    }

    /**
     * Whether the select reading a FROM item alone tests each row with a WHERE reading no name, under a LIMIT of its
     * own: see the class.
     *
     * @param executor  the executor that tells the select's aggregates and windows
     * @param reference the FROM item
     * @return true where the rows are streamed, not held
     */
    static boolean streams(final QueryExecutor executor, final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.SelectClauseContext select = soleReader(reference);
        if (select == null || select.whereClause() == null
                || UnreadColumnPruning.builtFromLiterals(select.whereClause().booleanExpr())
                || !readsNoName(select.whereClause())) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext statement = statementOf(select);
        return statement.limitClause() != null && statement.orderByClause() == null
            && statement.fetchClause() == null && select.groupByClause() == null && select.havingClause() == null
            && select.qualifyClause() == null && !executor.hasAggregateFunction(select)
            && !executor.hasWindowFunction(select);
    }

    /**
     * Whether a WHERE keeps every row of a derived table whose columns it reads are literals: those columns written
     * as their values, it is a condition of literals that is TRUE.
     *
     * @param executor  the executor that evaluates the condition
     * @param where     the WHERE
     * @param reference the FROM item it filters
     * @return true where it keeps every row
     */
    static boolean keepsEveryDerivedRow(final QueryExecutor executor, final FrostlakeParser.WhereClauseContext where,
                                        final FrostlakeParser.TableReferenceContext reference) {
        final Map<String, String> literals = literalColumns(reference);
        final StringBuilder text = new StringBuilder();
        if (literals.isEmpty() || !written(where.booleanExpr(), literals, text)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(new ExpressionEvaluator(null, executor.getFunctionRegistry(),
                executor.getCatalog(), executor).evaluate(text.toString(), new Row(new ArrayList<Object>())));
        } catch (final RuntimeException refusedWhereItStands) {
            return false;
        }
    }

    /** The literal items of a derived table's one select, by their aliases; empty for any other FROM item. */
    private static Map<String, String> literalColumns(final FrostlakeParser.TableReferenceContext reference) {
        final Map<String, String> literals = new HashMap<>();
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        final FrostlakeParser.SelectStatementContext body = source == null ? null : source.selectStatement();
        if (body == null || body.selectOperand().size() != 1 || body.selectOperand(0).selectClause() == null) {
            return literals;
        }
        for (final FrostlakeParser.SelectItemContext item
                : body.selectOperand(0).selectClause().selectList().selectItem()) {
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null && item instanceof FrostlakeParser.ExprItemContext
                    && UnreadColumnPruning.builtFromLiterals(((FrostlakeParser.ExprItemContext) item).booleanExpr())) {
                literals.put(alias, ParseTreeText.getOriginalText(((FrostlakeParser.ExprItemContext) item)
                    .booleanExpr()));
            }
        }
        return literals;
    }

    /**
     * Writes a condition with each column it reads as its literal value; false where it reads a column that is no
     * such literal or holds anything but literals and their operators.
     */
    private static boolean written(final ParseTree node, final Map<String, String> literals, final StringBuilder out) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            final String literal = parts.length == 1 ? literals.get(parts[0]) : null;
            if (literal == null) {
                return false;
            }
            out.append("(").append(literal).append(") ");
            return true;
        }
        if (node instanceof TerminalNode) {
            if (!UnreadColumnPruning.isConstantToken(((TerminalNode) node).getSymbol().getType())) {
                return false;
            }
            out.append(node.getText()).append(' ');
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!written(node.getChild(i), literals, out)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a WHERE reads no column, no alias and no query. */
    private static boolean readsNoName(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext
                || node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!readsNoName(node.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The select reading a FROM item alone, the one select of its statement, that neither reshapes nor samples it;
     * null otherwise.
     */
    private static FrostlakeParser.SelectClauseContext soleReader(
            final FrostlakeParser.TableReferenceContext reference) {
        if (!(reference.getParent() instanceof FrostlakeParser.TableExpressionContext)
                || !(reference.getParent().getParent() instanceof FrostlakeParser.SelectClauseContext)) {
            return null;
        }
        final FrostlakeParser.TableExpressionContext from =
            (FrostlakeParser.TableExpressionContext) reference.getParent();
        final FrostlakeParser.SelectClauseContext select = (FrostlakeParser.SelectClauseContext) from.getParent();
        if (!(select.getParent() instanceof FrostlakeParser.SelectOperandContext)
                || !(select.getParent().getParent() instanceof FrostlakeParser.SelectStatementContext)
                || statementOf(select).selectOperand().size() != 1
                || from.tableReference().size() != 1 || !from.joinClause().isEmpty()
                || select.DISTINCT() != null || select.topClause() != null || select.intoClause() != null
                || select.connectByClause() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                || reference.sampleClause() != null) {
            return null;
        }
        return select;
    }

    private static FrostlakeParser.SelectStatementContext statementOf(
            final FrostlakeParser.SelectClauseContext select) {
        return (FrostlakeParser.SelectStatementContext) select.getParent().getParent();
    }

    /**
     * The name of the aggregate a select item is - COUNT of the rows or of a constant, a constant's MIN, MAX or
     * ANY_VALUE - or null for any other item.
     */
    private static String countedCall(final ParseTree value) {
        ParseTree at = value;
        while (at != null && at.getChildCount() == 1 && !(at instanceof FrostlakeParser.FunctionCallStarExprContext)
                && !(at instanceof FrostlakeParser.FunctionCallExprContext)) {
            at = at.getChild(0);
        }
        if (at instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star = (FrostlakeParser.FunctionCallStarExprContext) at;
            return star.functionName() != null && "COUNT".equalsIgnoreCase(star.functionName().getText())
                && star.DISTINCT() == null && star.starQualifiedName() == null && star.starArgumentModifier() == null
                ? "COUNT" : null;
        }
        if (!(at instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) at;
        final String name = call.functionName() == null ? null
            : call.functionName().getText().toUpperCase(Locale.ROOT);
        if (name == null || !COUNTED.contains(name) || call.DISTINCT() != null || call.withinGroupClause() != null
                || call.overClause() != null || !call.nullHandling().isEmpty() || call.FROM() != null
                || call.functionArgList() == null || call.functionArgList().functionArg().size() != 1
                || call.functionArgList().functionArg(0).booleanExpr() == null
                || !UnreadColumnPruning.builtFromLiterals(call.functionArgList().functionArg(0).booleanExpr())) {
            return null;
        }
        return name;
    }
}
