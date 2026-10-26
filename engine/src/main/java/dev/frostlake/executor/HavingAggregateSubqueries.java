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

import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.values.ValueRange;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A value subquery standing in an aggregate's argument in a HAVING clause, reading the outer row and computing an
 * aggregate of its own: the account decorrelates it into the grouped query, where the two aggregates nest, and refuses
 * it as {@code Aggregate functions cannot be nested: [MAX(G.V)] nested in [MAX(MAX(G.V))]} — the subquery's aggregate
 * as the plan prints it, then the HAVING's aggregate over the subquery's item (live-verified):
 *
 * <ul>
 *   <li>a column is named by its relation, as the subquery's FROM names it ({@code X.V} under an alias), an outer
 *       column as {@code CORRELATION(FZ.ID)}; arithmetic inside an aggregate is printed as it stands
 *       ({@code MAX(G.V - CORRELATION(FZ.ID))}), an aggregate beside a literal in parentheses
 *       ({@code (MIN(G.V)) - 1}, {@code 2 * (MAX(G.V))});</li>
 *   <li>a SUM casts a whole number to NUMBER(38,0) ({@code SUM(CAST(G.V AS NUMBER(38,0)))}), and the HAVING's SUM casts
 *       the item to the item's own type ({@code SUM(CAST(COUNT(*) AS NUMBER(18,0)))}) unless the item is a SUM; an AVG
 *       is the SUM it divides ({@code SCALED_ROUND_INT_DIVIDE(…)} as the item);</li>
 *   <li>of several such subqueries the first is reported.</li>
 * </ul>
 *
 * <p>A subquery whose aggregate reads the outer row alone ({@code MAX(fz.id)}) has no aggregate of its own, and is
 * judged as before; one that reads nothing of the outer row is answered. Where the shape cannot be printed the
 * subquery is refused as no decorrelation can take it.
 */
final class HavingAggregateSubqueries {

    /** The aggregates a HAVING computes over a subquery's rows, group by group. */
    private static final Set<String> PER_GROUP_AGGREGATES = new HashSet<>(Arrays.asList(
        "MAX", "MIN", "SUM", "COUNT", "ANY_VALUE"));

    /** The calls a HAVING aggregate over a subquery may stand under and still be answered group by group. */
    private static final Set<String> ANSWERED_CALLS = new HashSet<>(Arrays.asList("COALESCE", "IFNULL", "NVL"));

    private static final String WHOLE_SUM = "NUMBER(38,0)";
    private static final String COUNT_TYPE = "NUMBER(18,0)";

    /** The aggregates a subquery's aggregate of the outer row alone decorrelates under, nested in theirs. */
    private static final Set<String> DECORRELATED_OUTER = new HashSet<>(Arrays.asList("MAX", "MIN", "COUNT", "SUM"));

    /** A NULL as a choice's branch prints it. */
    private static final PrintedPlanValue NULL_BRANCH = new PrintedPlanValue("null", null, true, true, false, false,
        null);
    /** The type a BOOLEAN takes on its way to a number. */
    private static final String PREDICATE_NUMBER = "NUMBER(2,0)";
    private static final DataType BOOLEAN = new BooleanType();

    /** The text a concatenation converts both sides to, and a cast to a bare VARCHAR reads as. */
    private static final String PLAN_TEXT = "VARCHAR(134217728)";
    private static final int PLAN_TEXT_LENGTH = 134217728;

    /** The relation and column a per-group query names the subquery's answers by. */
    private static final String ANSWERED_RELATION = "\"answered\"";
    private static final String ANSWERED_COLUMN = "\"value\"";

    private final QueryExecutor executor;
    private final Map<String, Table> outerRelations;
    private final Map<String, Table> innerRelations = new LinkedHashMap<>();
    private boolean innerKnown = true;

    private HavingAggregateSubqueries(final QueryExecutor executor, final Map<String, Table> outerRelations,
                                   final FrostlakeParser.SelectClauseContext subquery) {
        this.executor = executor;
        this.outerRelations = outerRelations;
        collectSources(subquery.tableExpression());
    }

    /**
     * Whether the HAVING never plans the aggregate: it stands under an OR whose other side is written of constants
     * that fold to TRUE ({@code OR TRUE}, {@code OR 1 = 1}), which settles the condition while the statement compiles,
     * so its subquery is neither decorrelated nor refused (live-verified).
     *
     * @param aggregate the HAVING's aggregate call
     * @param executor  the executor, which folds the constants
     * @return true where an OR the aggregate stands under is settled
     */
    static boolean settledAround(final FrostlakeParser.FunctionCallExprContext aggregate,
                                 final QueryExecutor executor) {
        ParseTree from = aggregate;
        for (ParseTree at = aggregate.getParent(); at != null && !(at instanceof FrostlakeParser.HavingClauseContext);
                at = at.getParent()) {
            if (at instanceof FrostlakeParser.OrExprContext) {
                for (int i = 0; i < at.getChildCount(); i++) {
                    final ParseTree side = at.getChild(i);
                    if (side != from && side instanceof ParserRuleContext && foldsTrue((ParserRuleContext) side,
                            executor)) {
                        return true;
                    }
                }
            }
            from = at;
        }
        return false;
    }

    /** Whether a condition is written of constants alone and folds to TRUE; one that faults does not. */
    private static boolean foldsTrue(final ParserRuleContext side, final QueryExecutor executor) {
        final String text = ParseTreeText.getOriginalText(side);
        if (!ConstantHaving.isConstant(ExpressionEvaluator.parse(text), executor.getFunctionRegistry())) {
            return false;
        }
        try {
            return SqlTruth.isTrue(new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                executor).evaluate(text, new Row(new ArrayList<Object>())));
        } catch (final RuntimeException faulted) {
            return false;
        }
    }

    /**
     * The aggregate call of a HAVING clause whose argument holds the node, outside any nested query; null when the node
     * stands in none.
     *
     * @param node     the node
     * @param executor the executor, which knows the aggregates
     * @return the call, or null
     */
    static FrostlakeParser.FunctionCallExprContext enclosingAggregate(final ParseTree node,
                                                                      final QueryExecutor executor) {
        FrostlakeParser.FunctionCallExprContext aggregate = null;
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext) {
                return null;
            }
            if (at instanceof FrostlakeParser.HavingClauseContext) {
                return aggregate;
            }
            if (aggregate == null && at instanceof FrostlakeParser.FunctionCallExprContext
                    && ((FrostlakeParser.FunctionCallExprContext) at).overClause() == null
                    && executor.getFunctionRegistry().getAggregateFunction(
                        ((FrostlakeParser.FunctionCallExprContext) at).functionName().getText()) != null) {
                aggregate = (FrostlakeParser.FunctionCallExprContext) at;
            }
        }
        return null;
    }

    /**
     * The refusal of a subquery of a HAVING aggregate that reads the outer row and has an aggregate of its own, or
     * null when it has none.
     *
     * @param executor       the executor
     * @param outerRelations the grouped query's relations, by the names they go by
     * @param aggregate      the HAVING's aggregate call
     * @param subquery       the subquery
     * @return the refusal's message, the nesting as the plan prints it, or an empty text where it cannot be printed;
     *         null when the subquery has no aggregate of its own
     */
    static String refusal(final QueryExecutor executor, final Map<String, Table> outerRelations,
                          final FrostlakeParser.FunctionCallExprContext aggregate,
                          final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final FrostlakeParser.SelectStatementContext query = subquery.selectStatement();
        if (query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        if (select.selectList().selectItem().size() != 1
                || !(select.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final HavingAggregateSubqueries nesting = new HavingAggregateSubqueries(executor, outerRelations, select);
        final FrostlakeParser.BooleanExprContext item =
            ((FrostlakeParser.ExprItemContext) select.selectList().selectItem(0)).booleanExpr();
        final ParserRuleContext own = nesting.ownAggregate(item);
        if (own == null) {
            final String outerOnly = nesting.outerOnly(aggregate, subquery, item, select);
            return outerOnly == null ? null : SqlCompilationError.withoutPosition(outerOnly);
        }
        final String printed = nesting.printed(aggregate, subquery, item, own);
        return printed == null ? "" : SqlCompilationError.withoutPosition(printed);
    }

    /**
     * Whether a HAVING answers a subquery that is the whole argument of its MAX, MIN, SUM, COUNT or ANY_VALUE, or the
     * one subquery of an argument computed from it and constants, and reads the outer row, group by group: the
     * subquery runs once for each group, over the group's keys, and the aggregate is taken over the rows it answers —
     * none for a group it finds nothing for, which no COALESCE around it brings back (live-verified). So it is
     * where the subquery reads no aggregate, no LIMIT and nothing of the outer row in its item, and reads the outer row
     * only in conditions of its WHERE that compare one of its columns that holds more than one value with an outer
     * column; and where the aggregate stands under comparisons, arithmetic, IS NOT NULL, a COALESCE, AND, OR and NOT
     * alone — IS NULL and an IN list are refused as no decorrelation can take them. A NOT is the anti join it
     * decorrelates into (see PerGroupNegations). A grouped select list answers the same aggregate where it stands
     * under a test (see {@link #enclosingListAggregate}).
     *
     * @param executor       the executor
     * @param outerRelations the grouped query's relations, by the names they go by
     * @param aggregate      the HAVING's aggregate call, or the select list's
     * @param subquery       the subquery
     * @return whether it is answered
     */
    static boolean answered(final QueryExecutor executor, final Map<String, Table> outerRelations,
                            final FrostlakeParser.FunctionCallExprContext aggregate,
                            final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        if (!wholeArgument(executor, aggregate, subquery)
                || !answeredWhereItStands(aggregate) && !answeredInList(aggregate)) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext query = subquery.selectStatement();
        if (query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null
                || query.limitClause() != null || query.fetchClause() != null || query.withClause() != null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        if (select.tableExpression() == null || select.topClause() != null || select.groupByClause() != null
                || select.havingClause() != null || select.qualifyClause() != null || select.connectByClause() != null
                || select.selectList().selectItem().size() != 1
                || !(select.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
            return false;
        }
        final HavingAggregateSubqueries subqueries = new HavingAggregateSubqueries(executor, outerRelations, select);
        final FrostlakeParser.BooleanExprContext item =
            ((FrostlakeParser.ExprItemContext) select.selectList().selectItem(0)).booleanExpr();
        if (!subqueries.innerKnown || subqueries.ownAggregate(item) != null || subqueries.readsOuterColumn(item)
                || subqueries.readsOuterColumn(select.tableExpression())
                || query.orderByClause() != null && subqueries.readsOuterColumn(query.orderByClause())) {
            return false;
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (where == null) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(where.booleanExpr(), conjuncts);
        final CatalogRelationConstancy constancy = new CatalogRelationConstancy(executor, subqueries.innerRelations);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (subqueries.readsOuterColumn(conjunct) && !subqueries.comparesColumns(conjunct, constancy)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The subquery a HAVING aggregate computes group by group: the whole argument of a MAX, MIN, SUM, COUNT or
     * ANY_VALUE, or the one subquery of an argument computed from it and constants, that reads the outer row and is
     * answered (see {@link #answered}); null for any other call.
     *
     * @param executor       the executor
     * @param outerRelations the grouped query's relations, by the names they go by
     * @param aggregate      the call
     * @return the subquery, or null
     */
    static FrostlakeParser.ScalarSubqueryExprContext perGroupSubquery(
            final QueryExecutor executor, final Map<String, Table> outerRelations,
            final FrostlakeParser.FunctionCallExprContext aggregate) {
        final FrostlakeParser.ScalarSubqueryExprContext subquery = soleSubquery(executor, aggregate);
        if (subquery == null || !wholeArgument(executor, aggregate, subquery)) {
            return null;
        }
        final FrostlakeParser.SelectStatementContext query = subquery.selectStatement();
        if (query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null
                || query.selectOperand(0).selectClause().tableExpression() == null) {
            return null;
        }
        final HavingAggregateSubqueries subqueries =
            new HavingAggregateSubqueries(executor, outerRelations, query.selectOperand(0).selectClause());
        return subqueries.innerKnown && subqueries.readsOuterColumn(query)
            && answered(executor, outerRelations, aggregate, subquery) ? subquery : null;
    }

    /**
     * Whether a subquery is what the argument of a MAX, MIN, SUM, COUNT or ANY_VALUE computes from: the argument
     * itself, or its one subquery (see {@link #soleSubquery}).
     */
    private static boolean wholeArgument(final QueryExecutor executor,
                                         final FrostlakeParser.FunctionCallExprContext aggregate,
                                         final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        return PER_GROUP_AGGREGATES.contains(aggregate.functionName().getText().toUpperCase(Locale.ROOT))
            && soleSubquery(executor, aggregate) == subquery;
    }

    /**
     * The subquery an aggregate's single argument computes from: the argument itself, or the one value subquery of
     * an argument that reads nothing else — no column, no other subquery, no aggregate and no window — beside its
     * constants: {@code MAX((SELECT …) + 1)}, {@code MAX(COALESCE((SELECT …), 0))} (live-verified); null otherwise.
     */
    private static FrostlakeParser.ScalarSubqueryExprContext soleSubquery(
            final QueryExecutor executor, final FrostlakeParser.FunctionCallExprContext aggregate) {
        if (aggregate.functionArgList() == null || aggregate.functionArgList().functionArg().size() != 1
                || aggregate.functionArgList().functionArg(0).booleanExpr() == null) {
            return null;
        }
        final ParseTree argument = valueOf(aggregate.functionArgList().functionArg(0).booleanExpr());
        if (argument instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return (FrostlakeParser.ScalarSubqueryExprContext) argument;
        }
        final List<FrostlakeParser.ScalarSubqueryExprContext> found = new ArrayList<>();
        return readsOnlySubqueries(executor, argument, found) && found.size() == 1 ? found.get(0) : null;
    }

    /** Whether a tree reads nothing but constants and its value subqueries, which it collects. */
    private static boolean readsOnlySubqueries(final QueryExecutor executor, final ParseTree node,
                                               final List<FrostlakeParser.ScalarSubqueryExprContext> found) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            found.add((FrostlakeParser.ScalarSubqueryExprContext) node);
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext
                || node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.FunctionCallStarExprContext
                || node instanceof FrostlakeParser.FunctionCallExprContext
                    && (((FrostlakeParser.FunctionCallExprContext) node).overClause() != null
                        || executor.getFunctionRegistry().getAggregateFunction(
                            ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()) != null)) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!readsOnlySubqueries(executor, node.getChild(i), found)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The query whose rows a per-group aggregate folds for one group: the subquery itself where it is the whole
     * argument, and otherwise the argument computed over each row the subquery answers, the subquery's place in it
     * read from the parse tree. A row the subquery does not answer is none the aggregate reads.
     *
     * @param aggregate the HAVING's aggregate call
     * @param subquery  the subquery it computes from (see {@link #perGroupSubquery})
     * @return the query's text
     */
    static String perGroupQuery(final FrostlakeParser.FunctionCallExprContext aggregate,
                                final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final String inner = ParseTreeText.getOriginalText(subquery.selectStatement());
        final FrostlakeParser.BooleanExprContext argument = aggregate.functionArgList().functionArg(0).booleanExpr();
        if (valueOf(argument) == subquery) {
            return inner;
        }
        final CharStream input = argument.getStart().getInputStream();
        final String before = subquery.getStart().getStartIndex() > argument.getStart().getStartIndex()
            ? input.getText(Interval.of(argument.getStart().getStartIndex(), subquery.getStart().getStartIndex() - 1))
            : "";
        final String after = argument.getStop().getStopIndex() > subquery.getStop().getStopIndex()
            ? input.getText(Interval.of(subquery.getStop().getStopIndex() + 1, argument.getStop().getStopIndex()))
            : "";
        return "SELECT " + before + ANSWERED_RELATION + "." + ANSWERED_COLUMN + after + " FROM (" + inner + ") AS "
            + ANSWERED_RELATION + " (" + ANSWERED_COLUMN + ")";
    }

    /**
     * The aggregate call of a grouped select list's item whose argument holds the node, outside any nested query,
     * where the aggregate stands, behind parentheses, under a comparison or an IS NULL, whatever stands above the test
     * up to the item: {@code MAX(@) > 0}, {@code NOT (MAX(@) = 50)}, {@code MAX(@) IS NULL}, a CASE, an IFF or a
     * COALESCE over such a test. There the select list computes a subquery of the aggregate's group by group as a
     * HAVING does, and refuses its nested aggregates as a HAVING does (live-verified); an aggregate standing bare or
     * under arithmetic, or one of a select with no GROUP BY, is judged as any other subquery's. Null for any other
     * node.
     *
     * @param node     the node
     * @param executor the executor, which knows the aggregates
     * @return the call, or null
     */
    static FrostlakeParser.FunctionCallExprContext enclosingListAggregate(final ParseTree node,
                                                                          final QueryExecutor executor) {
        FrostlakeParser.FunctionCallExprContext aggregate = null;
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext
                    || at instanceof FrostlakeParser.HavingClauseContext) {
                return null;
            }
            if (at instanceof FrostlakeParser.ExprItemContext) {
                return aggregate != null && answeredInList(aggregate) ? aggregate : null;
            }
            if (aggregate == null && at instanceof FrostlakeParser.FunctionCallExprContext
                    && ((FrostlakeParser.FunctionCallExprContext) at).overClause() == null
                    && executor.getFunctionRegistry().getAggregateFunction(
                        ((FrostlakeParser.FunctionCallExprContext) at).functionName().getText()) != null) {
                aggregate = (FrostlakeParser.FunctionCallExprContext) at;
            }
        }
        return null;
    }

    /** Whether a select-list aggregate stands under a test in an item of a select with a GROUP BY. */
    private static boolean answeredInList(final FrostlakeParser.FunctionCallExprContext aggregate) {
        ParseTree at = aggregate.getParent();
        while (at instanceof FrostlakeParser.ValueExprContext || at instanceof FrostlakeParser.ParenExprContext) {
            at = at.getParent();
        }
        if (!(at instanceof FrostlakeParser.ComparisonExprContext)
                && !(at instanceof FrostlakeParser.IsNullExprContext)) {
            return false;
        }
        for (; at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext
                    || at instanceof FrostlakeParser.HavingClauseContext) {
                return false;
            }
            if (at instanceof FrostlakeParser.SelectClauseContext) {
                return ((FrostlakeParser.SelectClauseContext) at).groupByClause() != null;
            }
        }
        return false;
    }

    /**
     * The refusal of a select-list subquery computed group by group that reads a column of the outer row the select
     * does not group by: {@code SELECT b, MAX((SELECT g.v FROM g WHERE g.id = fz2.id)) > 0 FROM fz2 GROUP BY b} is
     * "'FZ2.ID' in select clause is neither an aggregate nor in the group by clause." at the column (live-verified);
     * null where every column it reads is a key.
     *
     * @param executor       the executor
     * @param outerRelations the grouped query's relations, by the names they go by
     * @param aggregate      the select list's aggregate call (see {@link #enclosingListAggregate})
     * @param subquery       the subquery
     * @return the refusal, or null
     */
    static RuntimeException ungroupedReference(final QueryExecutor executor, final Map<String, Table> outerRelations,
                                               final FrostlakeParser.FunctionCallExprContext aggregate,
                                               final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        FrostlakeParser.SelectClauseContext grouped = null;
        for (ParseTree at = aggregate.getParent(); at != null && grouped == null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectClauseContext) {
                grouped = (FrostlakeParser.SelectClauseContext) at;
            }
        }
        final FrostlakeParser.SelectStatementContext query = subquery.selectStatement();
        if (grouped == null || grouped.groupByClause() == null || query.selectOperand(0).selectClause() == null) {
            return null;
        }
        final Set<String> keys = new HashSet<>();
        for (final FrostlakeParser.QualifiedNameExprContext key : namesIn(grouped.groupByClause())) {
            final String[] parts = ParseTreeText.qualifiedNameParts(key.qualifiedName());
            keys.add(parts[parts.length - 1].toUpperCase(Locale.ROOT));
        }
        if (keys.isEmpty()) {
            // GROUP BY ALL, or keys by position: the keys are the select list's, not judged here.
            return null;
        }
        final HavingAggregateSubqueries subqueries =
            new HavingAggregateSubqueries(executor, outerRelations, query.selectOperand(0).selectClause());
        for (final FrostlakeParser.QualifiedNameExprContext name : namesIn(query)) {
            final String outer = subqueries.innerColumn(name) == null ? subqueries.outerColumn(name) : null;
            if (outer != null && !keys.contains(outer.substring(outer.indexOf('.') + 1))) {
                return new RuntimeException(SqlCompilationError.at(name.getStart().getLine(),
                    name.getStart().getCharPositionInLine(),
                    "'" + outer + "' in select clause is neither an aggregate nor in the group by clause."));
            }
        }
        return null;
    }

    /** The names a tree reads, in the order they are written. */
    private static List<FrostlakeParser.QualifiedNameExprContext> namesIn(final ParseTree node) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNames(node, names);
        return names;
    }

    private static void collectNames(final ParseTree node, final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNames(node.getChild(i), into);
        }
    }

    /**
     * Whether the aggregate stands, up to the HAVING, under comparisons, arithmetic, IS NOT NULL, COALESCE, AND, OR,
     * NOT.
     */
    private static boolean answeredWhereItStands(final FrostlakeParser.FunctionCallExprContext aggregate) {
        for (ParseTree at = aggregate.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.HavingClauseContext) {
                return true;
            }
            final boolean through = at instanceof FrostlakeParser.ValueExprContext
                || at instanceof FrostlakeParser.ParenExprContext
                || at instanceof FrostlakeParser.ComparisonExprContext
                || at instanceof FrostlakeParser.BetweenExprContext
                    && ((FrostlakeParser.BetweenExprContext) at).NOT() == null
                || at instanceof FrostlakeParser.AdditiveExprContext
                || at instanceof FrostlakeParser.MultiplicativeExprContext
                || at instanceof FrostlakeParser.IsNullExprContext
                    && ((FrostlakeParser.IsNullExprContext) at).NOT() != null
                || at instanceof FrostlakeParser.AndExprContext || at instanceof FrostlakeParser.OrExprContext
                || at instanceof FrostlakeParser.NotExprContext
                || at instanceof FrostlakeParser.FunctionArgContext
                || at instanceof FrostlakeParser.FunctionArgListContext
                || at instanceof FrostlakeParser.FunctionCallExprContext && ANSWERED_CALLS.contains(
                    ((FrostlakeParser.FunctionCallExprContext) at).functionName().getText().toUpperCase(Locale.ROOT));
            if (!through) {
                return false;
            }
        }
        return false;
    }

    /**
     * Whether a condition compares a column of the subquery's own that holds more than one value with a column of the
     * outer row, each written bare.
     */
    private boolean comparesColumns(final FrostlakeParser.BooleanExprContext conjunct,
                                    final CatalogRelationConstancy constancy) {
        final ParseTree value = valueOf(conjunct);
        if (!(value instanceof FrostlakeParser.ComparisonExprContext)) {
            return false;
        }
        final ParseTree left = valueOf(value.getChild(0));
        final ParseTree right = valueOf(value.getChild(2));
        if (!(left instanceof FrostlakeParser.QualifiedNameExprContext)
                || !(right instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return false;
        }
        final FrostlakeParser.QualifiedNameExprContext one = (FrostlakeParser.QualifiedNameExprContext) left;
        final FrostlakeParser.QualifiedNameExprContext other = (FrostlakeParser.QualifiedNameExprContext) right;
        final FrostlakeParser.QualifiedNameExprContext inner = innerColumn(one) != null ? one
            : innerColumn(other) != null ? other : null;
        final FrostlakeParser.QualifiedNameExprContext outer = inner == one ? other : one;
        if (inner == null || innerColumn(outer) != null || outerColumn(outer) == null) {
            return false;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(inner.qualifiedName());
        final String column = parts[parts.length - 1];
        for (final Map.Entry<String, Table> relation : innerRelations.entrySet()) {
            final TableColumn held = relation.getValue().getColumn(column);
            if ((parts.length == 1 || relation.getKey().equalsIgnoreCase(parts[0])) && held != null) {
                return !constancy.holdsOneValue(relation.getKey(), held.getName());
            }
        }
        return false;
    }

    /**
     * Whether a tree reads a column of the outer row: a name no relation of the subquery holds and an outer one does.
     */
    private boolean readsOuterColumn(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
            return innerColumn(name) == null && outerColumn(name) != null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsOuterColumn(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext condition,
                                         final List<FrostlakeParser.BooleanExprContext> into) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            for (final FrostlakeParser.BooleanExprContext operand
                    : ((FrostlakeParser.AndExprContext) condition).booleanExpr()) {
                collectConjuncts(operand, into);
            }
            return;
        }
        into.add(condition);
    }

    /** The nesting as the plan prints it, or null where it cannot be printed. */
    private String printed(final FrostlakeParser.FunctionCallExprContext aggregate,
                           final FrostlakeParser.ScalarSubqueryExprContext subquery,
                           final FrostlakeParser.BooleanExprContext item, final ParserRuleContext own) {
        if (!innerKnown || aggregate.DISTINCT() != null || aggregate.functionArgList() == null
                || aggregate.functionArgList().functionArg().size() != 1
                || valueOf(aggregate.functionArgList().functionArg(0).booleanExpr()) != subquery) {
            return null;
        }
        final String inner = innerCall(own);
        final String itemText = itemText(valueOf(item), own, inner);
        if (inner == null || itemText == null) {
            return null;
        }
        final String outerName = aggregate.functionName().getText().toUpperCase(Locale.ROOT);
        String outer = outerName + "(" + itemText + ")";
        if ("SUM".equals(outerName) && !isCall(own, "SUM")) {
            final String type = itemType(valueOf(item), own);
            if (type == null && !extremeOfConversion(valueOf(item), own)) {
                return null;
            }
            // An extreme over arithmetic holding a conversion is added as it stands (live-verified).
            outer = type == null ? "SUM(" + itemText + ")" : "SUM(CAST(" + itemText + " AS " + type + "))";
        }
        return "Aggregate functions cannot be nested: [" + inner + "] nested in [" + outer + "]";
    }

    /** The first aggregate call of the item that reads a column of the subquery's own, or a COUNT(*); null for none. */
    private ParserRuleContext ownAggregate(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return null;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext
                && ((FrostlakeParser.FunctionCallStarExprContext) node).functionName() != null
                && executor.getFunctionRegistry().getAggregateFunction(
                    ((FrostlakeParser.FunctionCallStarExprContext) node).functionName().getText()) != null) {
            return (ParserRuleContext) node;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() == null
                && executor.getFunctionRegistry().getAggregateFunction(
                    ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()) != null
                && readsInnerColumn(node)) {
            return (ParserRuleContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParserRuleContext found = ownAggregate(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean readsInnerColumn(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return innerColumn((FrostlakeParser.QualifiedNameExprContext) node) != null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsInnerColumn(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The subquery's aggregate as the plan prints it, or null. */
    private String innerCall(final ParserRuleContext call) {
        if (call instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star = (FrostlakeParser.FunctionCallStarExprContext) call;
            return star.DISTINCT() != null || star.starQualifiedName() != null || star.starArgumentModifier() != null
                ? null : star.functionName().getText().toUpperCase(Locale.ROOT) + "(*)";
        }
        final FrostlakeParser.FunctionCallExprContext written = (FrostlakeParser.FunctionCallExprContext) call;
        if (written.functionArgList() == null || written.functionArgList().functionArg().size() != 1
                || written.functionArgList().functionArg(0).booleanExpr() == null) {
            return null;
        }
        final String name = written.functionName().getText().toUpperCase(Locale.ROOT);
        final ParseTree argument = valueOf(written.functionArgList().functionArg(0).booleanExpr());
        final String printed = value(argument);
        if (printed == null) {
            return null;
        }
        if ("SUM".equals(name) && written.DISTINCT() == null && !wholeColumn(argument)) {
            // A SUM casts what it adds to that value's own type (live-verified for casts the statement writes).
            // So does a SUM over arithmetic of exact numbers: SUM(CAST(CORRELATION(FZ.ID) + G.V AS NUMBER(38,0))), and
            // over an IFF or a CASE of them.
            final PrintedPlanValue summed = planned(argument, true);
            final boolean computed = argument instanceof FrostlakeParser.AdditiveExprContext
                || argument instanceof FrostlakeParser.MultiplicativeExprContext
                || argument instanceof FrostlakeParser.CaseExprContext
                || argument instanceof ParserRuleContext && isCall((ParserRuleContext) argument, "IFF");
            return summed != null && (summed.isCast() || summed.isConversion()
                    || computed && exactNumber(summed.getType()))
                ? "SUM(" + wrapped(summed).getText() + ")" : null;
        }
        if ("AVG".equals(name) && written.DISTINCT() == null && holdsConversion(argument)) {
            // An AVG over arithmetic holding a conversion adds it as it stands (live-verified).
            return "SUM(" + printed + ")";
        }
        if ("SUM".equals(name) || "AVG".equals(name)) {
            // A SUM, and the SUM an AVG divides, adds whole numbers as NUMBER(38,0).
            return written.DISTINCT() == null && wholeColumn(argument)
                ? "SUM(CAST(" + printed + " AS " + WHOLE_SUM + "))" : null;
        }
        return name + "(" + (written.DISTINCT() != null ? "DISTINCT " : "") + printed + ")";
    }

    /** The subquery's item as the plan prints it around its aggregate, or null. */
    private String itemText(final ParseTree item, final ParserRuleContext own, final String inner) {
        if (item == own) {
            if (!isCall(own, "AVG")) {
                return inner;
            }
            // An AVG is its SUM divided by its COUNT.
            final ParseTree argument = valueOf(((FrostlakeParser.FunctionCallExprContext) own).functionArgList()
                .functionArg(0).booleanExpr());
            final String sum = holdsConversion(argument) ? inner : "CAST(" + inner + " AS " + WHOLE_SUM + ")";
            return "SCALED_ROUND_INT_DIVIDE(" + sum + ", CAST(COUNT(" + value(argument) + ") AS " + COUNT_TYPE + "))";
        }
        if (inner == null || isCall(own, "AVG")) {
            return null;
        }
        if (item instanceof FrostlakeParser.AdditiveExprContext
                || item instanceof FrostlakeParser.MultiplicativeExprContext) {
            final ParserRuleContext arithmetic = (ParserRuleContext) item;
            final ParseTree left = arithmetic.getChild(0);
            final ParseTree right = arithmetic.getChild(2);
            final String op = arithmetic.getChild(1).getText();
            if (left == own && right instanceof FrostlakeParser.LiteralExprContext) {
                return "(" + inner + ") " + op + " " + right.getText();
            }
            if (right == own && left instanceof FrostlakeParser.LiteralExprContext) {
                return left.getText() + " " + op + " (" + inner + ")";
            }
        }
        return null;
    }

    /** Whether the item is the subquery's MAX or MIN over arithmetic holding a conversion (see holdsConversion). */
    private boolean extremeOfConversion(final ParseTree item, final ParserRuleContext own) {
        return item == own && (isCall(own, "MAX") || isCall(own, "MIN"))
            && ((FrostlakeParser.FunctionCallExprContext) own).functionArgList() != null
            && ((FrostlakeParser.FunctionCallExprContext) own).functionArgList().functionArg().size() == 1
            && holdsConversion(((FrostlakeParser.FunctionCallExprContext) own).functionArgList().functionArg(0)
                .booleanExpr());
    }

    /**
     * Whether an aggregate's argument is arithmetic holding a conversion — a BOOLEAN's, a text's or a FLOAT's to a
     * number — which an AVG, and the HAVING's SUM over the subquery's MAX, add as it stands.
     */
    private boolean holdsConversion(final ParseTree argument) {
        final ParseTree node = valueOf(argument);
        if (!(node instanceof FrostlakeParser.AdditiveExprContext)
                && !(node instanceof FrostlakeParser.MultiplicativeExprContext)) {
            return false;
        }
        final PrintedPlanValue printed = planned(node, true);
        return printed != null && printed.isConversion();
    }

    /** The type the HAVING's SUM casts the item to: the item's own, or null where it is not known. */
    private String itemType(final ParseTree item, final ParserRuleContext own) {
        if (item != own) {
            return null;
        }
        if (own instanceof FrostlakeParser.FunctionCallStarExprContext || isCall(own, "COUNT")) {
            return COUNT_TYPE;
        }
        if (isCall(own, "MAX") || isCall(own, "MIN") || isCall(own, "ANY_VALUE")) {
            final ParseTree argument = valueOf(((FrostlakeParser.FunctionCallExprContext) own).functionArgList()
                .functionArg(0).booleanExpr());
            final DataType type = argument instanceof FrostlakeParser.QualifiedNameExprContext
                ? columnType((FrostlakeParser.QualifiedNameExprContext) argument) : null;
            return type instanceof NumericType ? SqlTypeNames.canonical(type) : null;
        }
        return null;
    }

    /** A value inside the subquery's aggregate as the plan prints it, or null. */
    private String value(final ParseTree node) {
        final PrintedPlanValue planned = planned(node, true);
        return planned == null ? null : planned.getText();
    }

    /**
     * The nesting of an aggregate the subquery's item takes over the outer row alone, which decorrelates into an
     * aggregate of the grouped query's own: {@code [MAX(CAST(FZ.ID AS VARCHAR(134217728)))] nested in
     * [MAX(CORRELATION(MAX(CAST(FZ.ID AS VARCHAR(134217728)))))]}, under a MAX, a MIN, a COUNT or a SUM, the item's
     * aggregate standing alone or beside a literal - {@code [MAX(CORRELATION(MAX(FZ.ID)) + 1)]} - and an AVG spelled as
     * its SUM over its COUNT (live-verified). One whose WHERE reads the outer row is judged as before: not a valid
     * group by expression. The account answers some of these shapes from a table's statistics instead, dropping a WHERE
     * they prove always true and ignoring the query's own WHERE; that is not modelled, so the refusal stands as it
     * does where no statistics apply. Null where the item is no such aggregate, or the nesting cannot be printed.
     */
    private String outerOnly(final FrostlakeParser.FunctionCallExprContext aggregate,
                             final FrostlakeParser.ScalarSubqueryExprContext subquery,
                             final FrostlakeParser.BooleanExprContext item,
                             final FrostlakeParser.SelectClauseContext select) {
        final String outerName = aggregate.functionName().getText().toUpperCase(Locale.ROOT);
        if (!innerKnown || aggregate.DISTINCT() != null || !DECORRELATED_OUTER.contains(outerName)
                || aggregate.functionArgList() == null || aggregate.functionArgList().functionArg().size() != 1
                || valueOf(aggregate.functionArgList().functionArg(0).booleanExpr()) != subquery) {
            return null;
        }
        final ParseTree itemValue = valueOf(item);
        final FrostlakeParser.FunctionCallExprContext call;
        final String beside;
        if (itemValue instanceof FrostlakeParser.FunctionCallExprContext) {
            call = (FrostlakeParser.FunctionCallExprContext) itemValue;
            beside = "";
        } else if (itemValue instanceof FrostlakeParser.AdditiveExprContext
                && valueOf(((FrostlakeParser.AdditiveExprContext) itemValue).expression(0))
                    instanceof FrostlakeParser.FunctionCallExprContext
                && isLiteral(valueOf(((FrostlakeParser.AdditiveExprContext) itemValue).expression(1)))) {
            final FrostlakeParser.AdditiveExprContext sum = (FrostlakeParser.AdditiveExprContext) itemValue;
            call = (FrostlakeParser.FunctionCallExprContext) valueOf(sum.expression(0));
            beside = " " + sum.op.getText() + " " + ParseTreeText.getOriginalText(sum.expression(1));
        } else {
            return null;
        }
        final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
        if (call.overClause() != null || call.DISTINCT() != null
                || executor.getFunctionRegistry().getAggregateFunction(name) == null || call.functionArgList() == null
                || call.functionArgList().functionArg().size() != 1
                || call.functionArgList().functionArg(0).booleanExpr() == null
                || readsInnerColumn(call) || !readsOuterColumn(call)) {
            return null;
        }
        final ParseTree argument = valueOf(call.functionArgList().functionArg(0).booleanExpr());
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (where != null && readsOuterColumn(where)) {
            return null;
        }
        final PrintedPlanValue planned = planned(argument, false);
        if (planned == null || planned.getType() == null && !planned.isLiteral()) {
            return null;
        }
        final String inner;
        final String decorrelated;
        if ("SUM".equals(name) || "AVG".equals(name)) {
            final PrintedPlanValue summed = wrapped(planned);
            if (summed == null || "AVG".equals(name) && !(planned.getType() instanceof NumericType
                    && !NumericType.isApproximate(planned.getType())
                    && ((NumericType) planned.getType()).getScale() == 0)) {
                return null;
            }
            inner = "SUM(" + summed.getText() + ")";
            // An AVG of whole numbers is its SUM over its COUNT, and its SUM speaks as the nested aggregate.
            decorrelated = "AVG".equals(name) ? "SCALED_ROUND_INT_DIVIDE(CAST(" + inner + " AS " + WHOLE_SUM
                + "), CAST(COUNT(" + planned.getText() + ") AS " + COUNT_TYPE + "))" : inner;
        } else {
            inner = name + "(" + planned.getText() + ")";
            decorrelated = inner;
        }
        return "Aggregate functions cannot be nested: [" + inner + "] nested in [" + outerName + "(CORRELATION("
            + decorrelated + ")" + beside + ")]";
    }

    /** Whether a value is a literal, as the nesting prints it beside the aggregate. */
    private static boolean isLiteral(final ParseTree value) {
        return value instanceof FrostlakeParser.LiteralExprContext;
    }

    /**
     * A value inside the subquery's aggregate as the decorrelated plan prints it, or null where it cannot be printed
     * (live-verified): a column of the subquery's own by its relation ({@code G.V}), one of the outer row as
     * {@code CORRELATION(FZ.ID)} where the aggregate is the subquery's own and bare where it is the outer query's; a
     * cast as {@code CAST(x AS <type>)} — a bare VARCHAR as VARCHAR(134217728), a BOOLEAN to a number through
     * NUMBER(2,0), a cast or a TO_NUMBER to the type the value has already as the value itself; a minus as NEGATE(x);
     * arithmetic as {@link #arithmetic} converts it.
     *
     * @param written   the value
     * @param correlate whether the outer row's columns read as CORRELATION
     * @return the value as printed, or null
     */
    private PrintedPlanValue planned(final ParseTree written, final boolean correlate) {
        final ParseTree node = valueOf(written);
        if (node instanceof FrostlakeParser.ParenExprContext) {
            return planned(((FrostlakeParser.ParenExprContext) node).booleanExpr(), correlate);
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
            final String inner = innerColumn(name);
            if (inner != null) {
                return new PrintedPlanValue(inner, columnType(name), true, false, false, false, null);
            }
            final String outer = outerColumn(name);
            if (outer == null) {
                return null;
            }
            return new PrintedPlanValue(correlate ? "CORRELATION(" + outer + ")" : outer, outerColumnType(name), true,
                false, false, false, correlate ? outer : null);
        }
        if (node instanceof FrostlakeParser.LiteralExprContext) {
            final String text = node.getText();
            final DataType type = text.startsWith("'") ? new StringType("VARCHAR", PLAN_TEXT_LENGTH)
                : numberLiteralType(text);
            return new PrintedPlanValue(text, type, true, true, false, false, null);
        }
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) node;
            return castOf(cast.expression(),
                DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(), DataTypeParser.CAST_STRING_DEFAULT),
                correlate);
        }
        if (node instanceof FrostlakeParser.CastExprContext) {
            final FrostlakeParser.CastExprContext cast = (FrostlakeParser.CastExprContext) node;
            if (cast.FIELDS() != null) {
                return null;
            }
            return castOf(cast.expression(),
                DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(), DataTypeParser.CAST_STRING_DEFAULT),
                correlate);
        }
        if (node instanceof FrostlakeParser.UnaryExprContext) {
            final FrostlakeParser.UnaryExprContext unary = (FrostlakeParser.UnaryExprContext) node;
            final PrintedPlanValue operand = planned(unary.expression(), correlate);
            if (operand == null || unary.op.getType() != FrostlakeParser.MINUS) {
                return operand;
            }
            return new PrintedPlanValue("NEGATE(" + operand.getText() + ")", operand.getType(), true, false, false,
                false, null);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (isCall(call, "TO_BOOLEAN") && call.overClause() == null && call.functionArgList() != null
                    && call.functionArgList().functionArg().size() == 1
                    && call.functionArgList().functionArg(0).booleanExpr() != null) {
                return toBoolean(call.functionArgList().functionArg(0).booleanExpr(), correlate);
            }
            if (isCall(call, "IFF") && call.overClause() == null && call.functionArgList() != null
                    && call.functionArgList().functionArg().size() == 3) {
                final List<FrostlakeParser.FunctionArgContext> args = call.functionArgList().functionArg();
                return choice("IFF", args.get(0).booleanExpr(), args.get(1).booleanExpr(), args.get(2).booleanExpr(),
                    correlate);
            }
            return isCall(call, "MOD") ? remainder(call, correlate) : elidedConversion(call, correlate);
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext
                || node instanceof FrostlakeParser.MultiplicativeExprContext) {
            return arithmetic((ParserRuleContext) node, correlate);
        }
        if (node instanceof FrostlakeParser.CaseExprContext) {
            return caseOf(((FrostlakeParser.CaseExprContext) node).caseExpression(), correlate);
        }
        return predicate(node, correlate);
    }

    /**
     * An IFF, or a searched CASE of one branch, as the plan prints it (live-verified): {@code IFF(CORRELATION(FZ.ID) >
     * 5, G.V, 0)}, the CASE as {@code CASE_FLATTENED(…)}, a missing ELSE and a NULL branch as {@code null}; its
     * condition bare (see condition). As an operand it stands in parentheses, and it is cast as its first branch is.
     */
    private PrintedPlanValue choice(final String name, final ParseTree condition, final ParseTree whenTrue,
                                    final ParseTree whenFalse, final boolean correlate) {
        final String test = condition == null ? null : condition(condition, correlate);
        final PrintedPlanValue first = whenTrue == null ? null : branch(whenTrue, correlate);
        final PrintedPlanValue second = whenFalse == null ? NULL_BRANCH : branch(whenFalse, correlate);
        if (test == null || first == null || second == null) {
            return null;
        }
        return new PrintedPlanValue(name + "(" + test + ", " + first.getText() + ", " + second.getText() + ")",
            first.getType() != null ? first.getType() : second.getType(), false, false, false, false, null);
    }

    /**
     * A CASE as the plan prints it: a searched one of one branch flattened (see choice), a simple one of one branch
     * as written, its operand and values planned, {@code CASE CORRELATION(FZ.ID) WHEN 7 THEN G.V ELSE 0 END}
     * (live-verified). Null for any other.
     */
    private PrintedPlanValue caseOf(final FrostlakeParser.CaseExpressionContext written, final boolean correlate) {
        if (written instanceof FrostlakeParser.SearchedCaseExprContext) {
            final FrostlakeParser.SearchedCaseExprContext searched = (FrostlakeParser.SearchedCaseExprContext) written;
            if (searched.whenClause().size() != 1) {
                return null;
            }
            final FrostlakeParser.WhenClauseContext when = searched.whenClause(0);
            return choice("CASE_FLATTENED", when.booleanExpr(0), when.booleanExpr(1), searched.booleanExpr(),
                correlate);
        }
        final FrostlakeParser.SimpleCaseExprContext simple = (FrostlakeParser.SimpleCaseExprContext) written;
        if (simple.whenClause().size() != 1 || simple.booleanExpr() == null
                || !(valueOf(simple.whenClause(0).booleanExpr(0)) instanceof FrostlakeParser.LiteralExprContext)) {
            return null;
        }
        final PrintedPlanValue operand = planned(simple.expression(), correlate);
        final PrintedPlanValue first = branch(simple.whenClause(0).booleanExpr(1), correlate);
        final PrintedPlanValue otherwise = branch(simple.booleanExpr(), correlate);
        if (operand == null || first == null || otherwise == null) {
            return null;
        }
        return new PrintedPlanValue("CASE " + operand.getText() + " WHEN "
            + valueOf(simple.whenClause(0).booleanExpr(0)).getText() + " THEN " + first.getText() + " ELSE "
            + otherwise.getText() + " END", first.getType(), false, false, false, false, null);
    }

    /**
     * A choice's condition as the plan prints it (live-verified): a comparison, an IS NULL or a junction as the
     * predicate it is, bare; a number converted to BOOLEAN as its comparison with zero, {@code CORRELATION(FZ.ID) <>
     * 0}; a NOT in its cast, {@code CAST(NOT(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN)) AS BOOLEAN)}. Null for anything
     * else.
     */
    private String condition(final ParseTree written, final boolean correlate) {
        final ParseTree node = valueOf(written);
        final ParseTree converted = booleanCastOperand(node);
        if (converted != null) {
            final PrintedPlanValue source = planned(converted, correlate);
            return source != null && source.isBare() && exactNumber(source.getType())
                ? source.getText() + " <> 0" : null;
        }
        final PrintedPlanValue tested = planned(node, correlate);
        if (tested == null || !(tested.getType() instanceof BooleanType)) {
            return null;
        }
        return node instanceof FrostlakeParser.NotExprContext ? tested.booleanText() : tested.getText();
    }

    /** The operand of a cast to BOOLEAN, written as an operator or as {@code CAST(x AS BOOLEAN)}, or null. */
    private static ParseTree booleanCastOperand(final ParseTree node) {
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) node;
            return DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(), DataTypeParser.CAST_STRING_DEFAULT)
                instanceof BooleanType ? cast.expression() : null;
        }
        if (node instanceof FrostlakeParser.CastExprContext) {
            final FrostlakeParser.CastExprContext cast = (FrostlakeParser.CastExprContext) node;
            return cast.FIELDS() == null && DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(),
                DataTypeParser.CAST_STRING_DEFAULT) instanceof BooleanType ? cast.expression() : null;
        }
        return null;
    }

    /** A choice's branch as the plan prints it: a NULL as {@code null}, any other value planned. */
    private PrintedPlanValue branch(final ParseTree written, final boolean correlate) {
        final ParseTree node = valueOf(written);
        if (node instanceof FrostlakeParser.LiteralExprContext && "NULL".equalsIgnoreCase(node.getText())) {
            return NULL_BRANCH;
        }
        return planned(node, correlate);
    }

    /**
     * A predicate as the plan prints it, a BOOLEAN (live-verified): a comparison of numbers as written, an operand
     * that is no column, CORRELATION, call or literal in parentheses ({@code (G.V + CORRELATION(FZ.ID)) > 55}); an IS
     * NULL, an IN over literals and a NOT as written ({@code NOT(G.V > 55)}); an AND or an OR over the BOOLEAN each
     * side stands as, {@code (CAST(G.V > 55 AS BOOLEAN)) AND (CAST(G.ID > 5 AS BOOLEAN))}; a BETWEEN as the two
     * comparisons it makes. A comparison of the subquery's own column with a number, and its IS NULL, that the column's
     * statistics settle is TRUE or FALSE — {@code g.v < 10} over 50 and 60 is FALSE — and a junction leaves a side it
     * settles out, so {@code g.v BETWEEN 1 AND 55} there is {@code G.V <= 55}. Null for anything else.
     */
    private PrintedPlanValue predicate(final ParseTree node, final boolean correlate) {
        if (node instanceof FrostlakeParser.ComparisonExprContext) {
            final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext) node;
            return comparison(comparison.expression(0), comparison.op.getType(), comparison.expression(1), correlate);
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            final FrostlakeParser.IsNullExprContext test = (FrostlakeParser.IsNullExprContext) node;
            final PrintedPlanValue operand = planned(test.expression(), correlate);
            if (operand == null) {
                return null;
            }
            final ValueRange range = innerRange(test.expression());
            if (range != null && !range.isEmpty() && !range.isNullable()) {
                return settled(test.NOT() != null);
            }
            return predicateOf(operand.operand() + (test.NOT() != null ? " IS NOT NULL" : " IS NULL"));
        }
        if (node instanceof FrostlakeParser.InListExprContext) {
            final FrostlakeParser.InListExprContext in = (FrostlakeParser.InListExprContext) node;
            final PrintedPlanValue subject = in.NOT() == null ? planned(in.expression(), correlate) : null;
            if (subject == null) {
                return null;
            }
            final StringBuilder items = new StringBuilder();
            for (final FrostlakeParser.ExpressionContext item : in.expressionList().expression()) {
                final PrintedPlanValue printed = planned(item, correlate);
                if (printed == null || !printed.isLiteral()) {
                    return null;
                }
                items.append(items.length() > 0 ? ", " : "").append(printed.getText());
            }
            return predicateOf(subject.operand() + " IN (" + items + ")");
        }
        if (node instanceof FrostlakeParser.BetweenExprContext) {
            final FrostlakeParser.BetweenExprContext between = (FrostlakeParser.BetweenExprContext) node;
            return between.NOT() != null ? null : junction(
                comparison(between.expression(0), FrostlakeParser.GTE, between.expression(1), correlate),
                comparison(between.expression(0), FrostlakeParser.LTE, between.expression(2), correlate), true);
        }
        if (node instanceof FrostlakeParser.NotExprContext) {
            final PrintedPlanValue operand = planned(((FrostlakeParser.NotExprContext) node).booleanExpr(), correlate);
            if (operand == null || !(operand.getType() instanceof BooleanType)) {
                return null;
            }
            final Boolean settled = settledValue(operand);
            return settled != null ? settled(!settled.booleanValue()) : predicateOf("NOT(" + operand.getText() + ")");
        }
        if (node instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) node;
            return junction(planned(and.booleanExpr(0), correlate), planned(and.booleanExpr(1), correlate), true);
        }
        if (node instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext or = (FrostlakeParser.OrExprContext) node;
            return junction(planned(or.booleanExpr(0), correlate), planned(or.booleanExpr(1), correlate), false);
        }
        return null;
    }

    /** A comparison of numbers as the plan prints it, or as the BOOLEAN the statistics settle it to (see predicate). */
    private PrintedPlanValue comparison(final ParseTree left, final int operator, final ParseTree right,
                                        final boolean correlate) {
        final PrintedPlanValue a = planned(left, correlate);
        final PrintedPlanValue b = planned(right, correlate);
        if (a == null || b == null || !exactNumber(a.getType()) || !exactNumber(b.getType())) {
            return null;
        }
        final Boolean settled = settledComparison(left, operator, right);
        if (settled != null) {
            return settled(settled.booleanValue());
        }
        final String spelled;
        switch (operator) {
            case FrostlakeParser.EQ:
                spelled = "=";
                break;
            case FrostlakeParser.NEQ:
                spelled = "<>";
                break;
            case FrostlakeParser.LT:
                spelled = "<";
                break;
            case FrostlakeParser.LTE:
                spelled = "<=";
                break;
            case FrostlakeParser.GT:
                spelled = ">";
                break;
            default:
                spelled = ">=";
                break;
        }
        return predicateOf(a.operand() + " " + spelled + " " + b.operand());
    }

    /**
     * What a comparison of the subquery's own column with a number written out is on every row the column's
     * statistics cover: TRUE, FALSE, or null where they leave it open, the column may hold NULL or holds nothing.
     */
    private Boolean settledComparison(final ParseTree left, final int operator, final ParseTree right) {
        final boolean columnFirst = numberWrittenOut(right) != null;
        final ValueRange column = innerRange(columnFirst ? left : right);
        final BigDecimal number = numberWrittenOut(columnFirst ? right : left);
        if (column == null || number == null || column.isNullable()) {
            return null;
        }
        final ValueRange low = columnFirst ? column : ValueRange.of(number);
        final ValueRange high = columnFirst ? ValueRange.of(number) : column;
        switch (operator) {
            case FrostlakeParser.LT:
                return low.below(high, false);
            case FrostlakeParser.LTE:
                return low.below(high, true);
            case FrostlakeParser.GT:
                return high.below(low, false);
            case FrostlakeParser.GTE:
                return high.below(low, true);
            case FrostlakeParser.EQ:
                return column.equalTo(ValueRange.of(number));
            default:
                final Boolean equal = column.equalTo(ValueRange.of(number));
                return equal == null ? null : Boolean.valueOf(!equal.booleanValue());
        }
    }

    /** A number written out with digits and at most one point, or null for any other value. */
    private static BigDecimal numberWrittenOut(final ParseTree written) {
        final ParseTree node = valueOf(written);
        return node instanceof FrostlakeParser.LiteralExprContext && numberLiteralType(node.getText()) != null
            ? DecimalText.parse(node.getText()) : null;
    }

    /**
     * An AND or an OR of two BOOLEANs as the plan prints it: a side settled TRUE in an AND, or FALSE in an OR, leaves
     * the other side alone; one settled the other way is the whole junction.
     */
    private static PrintedPlanValue junction(final PrintedPlanValue a, final PrintedPlanValue b, final boolean and) {
        if (a == null || b == null || !(a.getType() instanceof BooleanType) || !(b.getType() instanceof BooleanType)) {
            return null;
        }
        final Boolean first = settledValue(a);
        if (first != null) {
            return first.booleanValue() == and ? b : a;
        }
        final Boolean second = settledValue(b);
        if (second != null) {
            return second.booleanValue() == and ? a : b;
        }
        return new PrintedPlanValue("(" + a.booleanText() + ") " + (and ? "AND" : "OR") + " (" + b.booleanText() + ")",
            BOOLEAN, false, false, false, false, null).asPredicate(false);
    }

    /** A predicate written as the plan prints it, which stands as a BOOLEAN in {@code CAST(… AS BOOLEAN)}. */
    private static PrintedPlanValue predicateOf(final String text) {
        return new PrintedPlanValue(text, BOOLEAN, false, false, false, false, null).asPredicate(true);
    }

    /** A predicate the statistics settle, TRUE or FALSE. */
    private static PrintedPlanValue settled(final boolean value) {
        return new PrintedPlanValue(value ? "TRUE" : "FALSE", BOOLEAN, true, false, false, false, null)
            .asPredicate(false);
    }

    /** The BOOLEAN a printed value settles to, TRUE or FALSE, or null for one that stays open. */
    private static Boolean settledValue(final PrintedPlanValue value) {
        if (!(value.getType() instanceof BooleanType) || !value.isBare()) {
            return null;
        }
        return "TRUE".equals(value.getText()) ? Boolean.TRUE : "FALSE".equals(value.getText()) ? Boolean.FALSE : null;
    }

    /** A cast of a written value as the plan prints it (see {@link #castTo}), one to BOOLEAN as {@link #toBoolean}. */
    private PrintedPlanValue castOf(final ParseTree operand, final DataType target, final boolean correlate) {
        if (target instanceof BooleanType) {
            return toBoolean(operand, correlate);
        }
        return castTo(planned(operand, correlate), target);
    }

    /**
     * A value converted to BOOLEAN as the plan prints it (live-verified): a column of numbers its table's statistics
     * settle as TRUE or FALSE, whatever a WHERE keeps ({@code g.v::BOOLEAN} over 50 and 60 is TRUE, over 0 and 0
     * FALSE); any other number standing alone, an outer column inside its CORRELATION, as its comparison with zero,
     * {@code CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN)}; a predicate in {@code CAST(G.V > 55 AS BOOLEAN)}; a BOOLEAN as
     * it is. Null for anything else.
     */
    private PrintedPlanValue toBoolean(final ParseTree operand, final boolean correlate) {
        final PrintedPlanValue source = planned(operand, correlate);
        if (source == null) {
            return null;
        }
        if (source.getType() instanceof BooleanType) {
            return new PrintedPlanValue(source.booleanText(), BOOLEAN, source.isBare() && !source.castsAsBoolean(),
                false, source.castsAsBoolean(), false, null);
        }
        if (!source.isBare() || !exactNumber(source.getType())) {
            return null;
        }
        // An outer column read as the outer query's own aggregate is not settled: that aggregate is no correlation.
        final ParseTree node = valueOf(operand);
        final Boolean settled = node instanceof FrostlakeParser.QualifiedNameExprContext
            && (correlate || innerColumn((FrostlakeParser.QualifiedNameExprContext) node) != null)
            ? settledBoolean((FrostlakeParser.QualifiedNameExprContext) node) : null;
        if (settled != null) {
            return new PrintedPlanValue(settled.booleanValue() ? "TRUE" : "FALSE", BOOLEAN, true, false, false, false,
                null);
        }
        return new PrintedPlanValue("CAST(" + source.getText() + " <> 0 AS BOOLEAN)", BOOLEAN, false, false, true,
            false, null);
    }

    /**
     * A cast as the plan prints it: to the type the value has already, the value itself in parentheses; a BOOLEAN to
     * a number through NUMBER(2,0); a column's number to a BOOLEAN as its comparison with zero,
     * {@code CAST(FZ.ID <> 0 AS BOOLEAN)}; a text to a whole number as {@code TO_NUMBER(x)}, to any other as
     * {@code CAST(TO_NUMBER(x, 10, 2) AS NUMBER(10,2))}; a FLOAT to a whole number as one cast, to any other as two
     * (all live-verified). A conversion from a BOOLEAN, a text or a FLOAT converts no further; a structured target is
     * not printed.
     */
    private static PrintedPlanValue castTo(final PrintedPlanValue source, final DataType target) {
        if (source == null || target == null) {
            return null;
        }
        if (target instanceof BooleanType) {
            return source.getCorrelated() == null && source.isBare() && exactNumber(source.getType())
                ? new PrintedPlanValue("CAST(" + source.getText() + " <> 0 AS BOOLEAN)", target, false, false, true,
                    false, null)
                : null;
        }
        final String targetName = typeName(target);
        if (source.getType() instanceof BooleanType) {
            if (!(target instanceof NumericType) || NumericType.isApproximate(target)) {
                return null;
            }
            if (source.isFromPredicate()) {
                // A predicate's number is its BOOLEAN through NUMBER(2,0), whatever the cast names (live-verified).
                return new PrintedPlanValue("CAST(" + source.booleanText() + " AS " + PREDICATE_NUMBER + ")",
                    new NumericType("NUMBER", 2, 0), false, false, true, true, null).asBooleanNumber();
            }
            return new PrintedPlanValue("CAST(CAST(" + source.getText() + " AS " + PREDICATE_NUMBER + ") AS "
                + targetName + ")", target, false, false, true, true, null).asBooleanNumber();
        }
        if (exactNumber(target) && source.getType() instanceof StringType) {
            final NumericType number = (NumericType) target;
            final String converted = WHOLE_SUM.equals(targetName) ? "TO_NUMBER(" + source.getText() + ")"
                : "CAST(TO_NUMBER(" + source.getText() + ", " + number.getPrecision() + ", " + number.getScale()
                    + ") AS " + targetName + ")";
            return new PrintedPlanValue(converted, target, false, false, true, true, null);
        }
        if (exactNumber(target) && source.getType() instanceof NumericType
                && NumericType.isApproximate(source.getType())) {
            final String cast = "CAST(" + source.getText() + " AS " + targetName + ")";
            final String converted = WHOLE_SUM.equals(targetName) ? cast : "CAST(" + cast + " AS " + targetName + ")";
            return new PrintedPlanValue(converted, target, false, false, true, true, null);
        }
        if (source.getType() != null && targetName.equals(typeName(source.getType()))) {
            return new PrintedPlanValue(source.getText(), source.getType(), false, false, false, false, null);
        }
        return new PrintedPlanValue("CAST(" + source.getText() + " AS " + targetName + ")", target, false, false, true,
            false, null);
    }

    /** A TO_NUMBER of a whole number, which converts nothing, as the value itself in parentheses; null otherwise. */
    private PrintedPlanValue elidedConversion(final FrostlakeParser.FunctionCallExprContext call,
                                              final boolean correlate) {
        final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
        if (!"TO_NUMBER".equals(name) && !"TO_DECIMAL".equals(name) && !"TO_NUMERIC".equals(name)
                || call.functionArgList() == null || call.functionArgList().functionArg().size() != 1
                || call.functionArgList().functionArg(0).booleanExpr() == null) {
            return null;
        }
        final PrintedPlanValue source = planned(call.functionArgList().functionArg(0).booleanExpr(), correlate);
        return source != null && WHOLE_SUM.equals(typeName(source.getType()))
            ? new PrintedPlanValue(source.getText(), source.getType(), false, false, false, false, null) : null;
    }

    /**
     * Arithmetic as the plan prints it, or null where it cannot be printed (live-verified): an operand over a FLOAT
     * turns both to FLOAT; an addition casts the lower scale's side to the higher scale and, beside a BOOLEAN's
     * conversion, the other side to its own type; a product of two values that are no literal casts each to its own
     * type, an outer column inside its CORRELATION ({@code CORRELATION(CAST(FZ.ID AS NUMBER(38,0)))}); a quotient of
     * whole numbers is SCALED_ROUND_INT_DIVIDE of the two so cast; a concatenation casts both sides to
     * VARCHAR(134217728); an operand that is no column, CORRELATION, call or literal stands in parentheses.
     */
    private PrintedPlanValue arithmetic(final ParserRuleContext node, final boolean correlate) {
        final PrintedPlanValue left = planned(node.getChild(0), correlate);
        final PrintedPlanValue right = planned(node.getChild(2), correlate);
        final String op = node.getChild(1).getText();
        if (left == null || right == null) {
            return null;
        }
        if ("||".equals(op)) {
            // A text literal is text already, and concatenations chain without parentheses.
            final PrintedPlanValue a = converted(left, PLAN_TEXT, null);
            final PrintedPlanValue b = converted(right, PLAN_TEXT, null);
            return new PrintedPlanValue(a.operand() + " || " + b.operand(),
                new StringType("VARCHAR", PLAN_TEXT_LENGTH), true, false, false, false, null);
        }
        if (!(left.getType() instanceof NumericType) || !(right.getType() instanceof NumericType)
                || NumericType.isDecfloat(left.getType()) || NumericType.isDecfloat(right.getType())) {
            return null;
        }
        final NumericType l = (NumericType) left.getType();
        final NumericType r = (NumericType) right.getType();
        if (NumericType.isApproximate(l) || NumericType.isApproximate(r)) {
            final PrintedPlanValue a = NumericType.isApproximate(l) ? left : converted(left, "FLOAT", r);
            final PrintedPlanValue b = NumericType.isApproximate(r) ? right : converted(right, "FLOAT", l);
            return "%".equals(op) ? null : new PrintedPlanValue(a.operand() + " " + op + " " + b.operand(),
                NumericType.isApproximate(l) ? l : r, false, false, false, false, null);
        }
        if ("%".equals(op)) {
            return l.getScale() == 0 && r.getScale() == 0 ? new PrintedPlanValue(left.operand() + " % "
                + right.operand(), new NumericType("NUMBER", Math.min(l.getPrecision(), r.getPrecision()), 0), false,
                false, false, false, null) : null;
        }
        if ("+".equals(op) || "-".equals(op)) {
            final int scale = Math.max(l.getScale(), r.getScale());
            PrintedPlanValue a = left;
            PrintedPlanValue b = right;
            if (l.getScale() < scale) {
                a = converted(left, aligned(l, scale), alignedType(l, scale));
            } else if (r.getScale() < scale) {
                b = converted(right, aligned(r, scale), alignedType(r, scale));
            }
            if (left.isConversion() != right.isConversion()) {
                // Beside a conversion the other side, its scale aligned, is cast to its own type.
                if (left.isConversion()) {
                    b = wrapped(b);
                } else {
                    a = wrapped(a);
                }
            }
            // A BOOLEAN's number is cast once more (see wrapped), and a sum holding a conversion is one itself.
            a = a.isBooleanNumber() ? wrapped(a) : a;
            b = b.isBooleanNumber() ? wrapped(b) : b;
            final int whole = Math.max(l.getPrecision() - l.getScale(), r.getPrecision() - r.getScale()) + 1;
            return new PrintedPlanValue(a.operand() + " " + op + " " + b.operand(),
                new NumericType("NUMBER", Math.min(38, whole + scale), scale), false, false, false,
                left.isConversion() || right.isConversion(), null);
        }
        if ("*".equals(op)) {
            // A literal, or a remainder, takes no cast of its own and leaves the other side bare.
            final boolean plain = left.isLiteral() || right.isLiteral() || isRemainder(node.getChild(0))
                || isRemainder(node.getChild(2));
            final PrintedPlanValue a = plain ? left : wrapped(left);
            final PrintedPlanValue b = plain ? right : wrapped(right);
            return new PrintedPlanValue(a.operand() + " * " + b.operand(), new NumericType("NUMBER",
                Math.min(38, l.getPrecision() + r.getPrecision()), Math.min(37, l.getScale() + r.getScale())), false,
                false, false, false, null);
        }
        if ("/".equals(op) && l.getScale() == 0 && r.getScale() == 0 && !left.isLiteral() && !right.isLiteral()) {
            return new PrintedPlanValue("SCALED_ROUND_INT_DIVIDE(" + wrapped(left).getText() + ", "
                + wrapped(right).getText() + ")", null, true, false, false, false, null);
        }
        return null;
    }

    /** A value converted to another type for an operation: as it is where it has that type already. */
    private static PrintedPlanValue converted(final PrintedPlanValue value, final String target,
                                              final DataType type) {
        if (value.getType() != null && target.equals(typeName(value.getType()))) {
            return value;
        }
        return new PrintedPlanValue("CAST(" + value.getText() + " AS " + target + ")", type, false, false, true,
            false, null);
    }

    /**
     * A value cast to its own type, as a product's operands and a SUM's argument are: an outer column inside its
     * CORRELATION, a BOOLEAN's conversion or a literal as it is.
     */
    private static PrintedPlanValue wrapped(final PrintedPlanValue value) {
        if (value.isBooleanNumber()) {
            // A BOOLEAN's number is cast to its own type once more, unless that is NUMBER(38,0) (live-verified).
            final String own = typeName(value.getType());
            return WHOLE_SUM.equals(own) ? value : new PrintedPlanValue("CAST(" + value.getText() + " AS " + own + ")",
                value.getType(), false, false, true, true, null);
        }
        if (value.isLiteral() || value.isConversion()) {
            return value;
        }
        final String own = typeName(value.getType());
        if (value.getCorrelated() != null) {
            return new PrintedPlanValue("CORRELATION(CAST(" + value.getCorrelated() + " AS " + own + "))",
                value.getType(), true, false, false, false, null);
        }
        return new PrintedPlanValue("CAST(" + value.getText() + " AS " + own + ")", value.getType(), false, false,
            true, false, null);
    }

    /** A number's type at a higher scale, its whole digits kept as far as 38 digits allow. */
    private static String aligned(final NumericType type, final int scale) {
        return typeName(alignedType(type, scale));
    }

    private static NumericType alignedType(final NumericType type, final int scale) {
        return new NumericType("NUMBER", Math.min(38, type.getPrecision() - type.getScale() + scale), scale);
    }

    /** Whether a type is an exact number other than a DECFLOAT. */
    private static boolean exactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) && !NumericType.isDecfloat(type);
    }

    /** Whether a value is a remainder written with {@code %}. */
    private static boolean isRemainder(final ParseTree written) {
        final ParseTree node = valueOf(written);
        return node instanceof FrostlakeParser.MultiplicativeExprContext && "%".equals(node.getChild(1).getText());
    }

    /** A MOD of whole numbers as the plan prints it, {@code MOD(CORRELATION(FZ.ID), 2)}; null for any other. */
    private PrintedPlanValue remainder(final FrostlakeParser.FunctionCallExprContext call, final boolean correlate) {
        if (call.functionArgList() == null || call.functionArgList().functionArg().size() != 2
                || call.functionArgList().functionArg(0).booleanExpr() == null
                || call.functionArgList().functionArg(1).booleanExpr() == null) {
            return null;
        }
        final PrintedPlanValue dividend = planned(call.functionArgList().functionArg(0).booleanExpr(), correlate);
        final PrintedPlanValue divisor = planned(call.functionArgList().functionArg(1).booleanExpr(), correlate);
        if (dividend == null || divisor == null || !exactNumber(dividend.getType()) || !exactNumber(divisor.getType())
                || ((NumericType) dividend.getType()).getScale() != 0
                || ((NumericType) divisor.getType()).getScale() != 0) {
            return null;
        }
        return new PrintedPlanValue("MOD(" + dividend.getText() + ", " + divisor.getText() + ")", new NumericType(
            "NUMBER", Math.min(((NumericType) dividend.getType()).getPrecision(),
                ((NumericType) divisor.getType()).getPrecision()), 0), false, false, false, false, null);
    }

    /** A type as the plan prints a cast to it: a bare VARCHAR as VARCHAR(134217728), every float as FLOAT. */
    private static String typeName(final DataType type) {
        if (type == null) {
            return "";
        }
        if (type instanceof NumericType && NumericType.isApproximate(type)) {
            return "FLOAT";
        }
        return SqlTypeNames.canonical(type);
    }

    /**
     * The type of a number written out with digits and at most one point, NUMBER of its digits at the scale of those
     * after the point ({@code 1.5} is NUMBER(2,1), {@code 0.5} NUMBER(1,1)); null for any other literal.
     */
    private static DataType numberLiteralType(final String text) {
        final int point = text.indexOf('.');
        if (text.isEmpty() || point == 0 || point == text.length() - 1 || text.indexOf('.', point + 1) >= 0) {
            return null;
        }
        for (int i = 0; i < text.length(); i++) {
            if (i != point && !Character.isDigit(text.charAt(i))) {
                return null;
            }
        }
        if (point < 0) {
            return new NumericType("NUMBER", text.length(), 0);
        }
        int whole = point;
        for (int i = 0; i < point - 1 && text.charAt(i) == '0'; i++) {
            whole--;
        }
        if (whole == 1 && text.charAt(point - 1) == '0') {
            whole = 0;
        }
        final int scale = text.length() - point - 1;
        return new NumericType("NUMBER", Math.max(1, whole + scale), scale);
    }

    /** A name of the subquery's own relations, as the plan prints it ({@code G.V}); null for any other name. */
    private String innerColumn(final FrostlakeParser.QualifiedNameExprContext name) {
        return columnOf(name, innerRelations);
    }

    /** A name of the grouped query's relations, as the plan prints it ({@code FZ.ID}); null for any other name. */
    private String outerColumn(final FrostlakeParser.QualifiedNameExprContext name) {
        return columnOf(name, outerRelations);
    }

    private static String columnOf(final FrostlakeParser.QualifiedNameExprContext name,
                                   final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        if (parts.length > 2) {
            return null;
        }
        final String column = parts[parts.length - 1];
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            final boolean named = parts.length == 1 || relation.getKey().equalsIgnoreCase(parts[0]);
            if (named && relation.getValue().getColumn(column) != null) {
                return relation.getKey().toUpperCase(Locale.ROOT) + "." + column.toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /** The statistics of a column of the subquery's own relations a value names, or null for any other value. */
    private ValueRange innerRange(final ParseTree written) {
        final ParseTree node = valueOf(written);
        if (!(node instanceof FrostlakeParser.QualifiedNameExprContext)
                || innerColumn((FrostlakeParser.QualifiedNameExprContext) node) == null) {
            return null;
        }
        final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
        final Table owner = owner(name, innerRelations);
        final TableColumn held = owner == null || owner.residentSource() == null ? null
            : owner.getColumn(lastPart(name));
        return held == null ? null : executor.columnValueRange(owner.residentSource(), held.getName());
    }

    /** The BOOLEAN a column's statistics settle its numbers to (see QueryExecutor#columnSettledBoolean), or null. */
    private Boolean settledBoolean(final FrostlakeParser.QualifiedNameExprContext name) {
        final Table owner = owner(name, innerColumn(name) != null ? innerRelations : outerRelations);
        final TableColumn held = owner == null ? null : owner.getColumn(lastPart(name));
        return held == null ? null : executor.columnSettledBoolean(owner, held.getName());
    }

    /** The relation of these a name reads: the one its qualifier names, else the first carrying the column. */
    private static Table owner(final FrostlakeParser.QualifiedNameExprContext name,
                               final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        if (parts.length > 2) {
            return null;
        }
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if ((parts.length == 1 || relation.getKey().equalsIgnoreCase(parts[0]))
                    && relation.getValue().getColumn(parts[parts.length - 1]) != null) {
                return relation.getValue();
            }
        }
        return null;
    }

    private static String lastPart(final FrostlakeParser.QualifiedNameExprContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        return parts[parts.length - 1];
    }

    /** The declared type of a column of the subquery's own relations, or null. */
    private DataType columnType(final FrostlakeParser.QualifiedNameExprContext name) {
        return declaredType(name, innerRelations);
    }

    /** The declared type of a column of the grouped query's relations, or null. */
    private DataType outerColumnType(final FrostlakeParser.QualifiedNameExprContext name) {
        return declaredType(name, outerRelations);
    }

    private static DataType declaredType(final FrostlakeParser.QualifiedNameExprContext name,
                                         final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        final String column = parts[parts.length - 1];
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (parts.length == 1 || relation.getKey().equalsIgnoreCase(parts[0])) {
                final TableColumn held = relation.getValue().getColumn(column);
                if (held != null) {
                    return held.getDataType();
                }
            }
        }
        return null;
    }

    /** Whether a value is a column of the subquery's own holding whole numbers. */
    private boolean wholeColumn(final ParseTree value) {
        final DataType type = value instanceof FrostlakeParser.QualifiedNameExprContext
            ? columnType((FrostlakeParser.QualifiedNameExprContext) value) : null;
        return type instanceof NumericType && !NumericType.isApproximate(type) && ((NumericType) type).getScale() == 0;
    }

    /** The subquery's FROM relations by the names they go by; a source that is no catalog table leaves it unknown. */
    private void collectSources(final FrostlakeParser.TableExpressionContext from) {
        if (from == null) {
            innerKnown = false;
            return;
        }
        for (final FrostlakeParser.TableReferenceContext reference : from.tableReference()) {
            collectSource(reference);
        }
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            collectSource(join.tableReference());
        }
    }

    private void collectSource(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.tableSource() == null || reference.tableSource().tableQualifiedName() == null) {
            innerKnown = false;
            return;
        }
        final String written = ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName());
        final Table table;
        try {
            table = executor.getCatalog().resolveTable(written);
        } catch (final RuntimeException notATable) {
            innerKnown = false;
            return;
        }
        final String alias = reference.aliasName() != null ? ParseTreeText.getIdentifier(reference.aliasName())
            : reference.nonJoinKeywordIdentifier() != null && reference.nonJoinKeywordIdentifier().identifier() != null
                ? ParseTreeText.getIdentifier(reference.nonJoinKeywordIdentifier().identifier()) : null;
        innerRelations.put(alias != null ? alias : table.getName(), table);
    }

    private static boolean isCall(final ParserRuleContext node, final String name) {
        return node instanceof FrostlakeParser.FunctionCallExprContext
            && name.equalsIgnoreCase(((FrostlakeParser.FunctionCallExprContext) node).functionName().getText());
    }

    /** An expression behind the condition that holds it as a value; anything else as it is. */
    private static ParseTree valueOf(final ParseTree written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }
}
