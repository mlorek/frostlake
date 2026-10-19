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

import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

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
            return null;
        }
        final String printed = nesting.printed(aggregate, subquery, item, own);
        return printed == null ? "" : SqlCompilationError.withoutPosition(printed);
    }

    /**
     * Whether a HAVING answers a subquery that is the whole argument of its MAX, MIN, SUM, COUNT or ANY_VALUE and
     * reads the outer row, group by group: the subquery runs once for each group, over the group's keys, and the
     * aggregate is taken over the rows it answers — none for a group it finds nothing for (live-verified). So it is
     * where the subquery reads no aggregate, no LIMIT and nothing of the outer row in its item, and reads the outer row
     * only in conditions of its WHERE that compare one of its columns that holds more than one value with an outer
     * column; and where the aggregate stands under comparisons, arithmetic, IS NOT NULL, a COALESCE, AND and OR alone
     * — IS NULL, NOT and an IN list are refused as no decorrelation can take them.
     *
     * @param executor       the executor
     * @param outerRelations the grouped query's relations, by the names they go by
     * @param aggregate      the HAVING's aggregate call
     * @param subquery       the subquery
     * @return whether it is answered
     */
    static boolean answered(final QueryExecutor executor, final Map<String, Table> outerRelations,
                            final FrostlakeParser.FunctionCallExprContext aggregate,
                            final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        if (!wholeArgument(aggregate, subquery) || !answeredWhereItStands(aggregate)) {
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
     * ANY_VALUE that reads the outer row and is answered (see {@link #answered}); null for any other call.
     *
     * @param executor       the executor
     * @param outerRelations the grouped query's relations, by the names they go by
     * @param aggregate      the call
     * @return the subquery, or null
     */
    static FrostlakeParser.ScalarSubqueryExprContext perGroupSubquery(
            final QueryExecutor executor, final Map<String, Table> outerRelations,
            final FrostlakeParser.FunctionCallExprContext aggregate) {
        if (aggregate.functionArgList() == null || aggregate.functionArgList().functionArg().size() != 1) {
            return null;
        }
        final ParseTree argument = valueOf(aggregate.functionArgList().functionArg(0).booleanExpr());
        if (!(argument instanceof FrostlakeParser.ScalarSubqueryExprContext)
                || !wholeArgument(aggregate, (FrostlakeParser.ScalarSubqueryExprContext) argument)) {
            return null;
        }
        final FrostlakeParser.ScalarSubqueryExprContext subquery = (FrostlakeParser.ScalarSubqueryExprContext) argument;
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

    /** Whether a subquery is the whole argument of a MAX, MIN, SUM, COUNT or ANY_VALUE. */
    private static boolean wholeArgument(final FrostlakeParser.FunctionCallExprContext aggregate,
                                         final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        return PER_GROUP_AGGREGATES.contains(aggregate.functionName().getText().toUpperCase(Locale.ROOT))
            && aggregate.functionArgList() != null && aggregate.functionArgList().functionArg().size() == 1
            && valueOf(aggregate.functionArgList().functionArg(0).booleanExpr()) == subquery;
    }

    /**
     * Whether the aggregate stands, up to the HAVING, under comparisons, arithmetic, IS NOT NULL, COALESCE, AND, OR.
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
            if (type == null) {
                return null;
            }
            outer = "SUM(CAST(" + itemText + " AS " + type + "))";
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
            return "SCALED_ROUND_INT_DIVIDE(CAST(" + inner + " AS " + WHOLE_SUM + "), CAST(COUNT(" + value(argument)
                + ") AS " + COUNT_TYPE + "))";
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
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
            final String inner = innerColumn(name);
            if (inner != null) {
                return inner;
            }
            final String outer = outerColumn(name);
            return outer == null ? null : "CORRELATION(" + outer + ")";
        }
        if (node instanceof FrostlakeParser.LiteralExprContext) {
            return node.getText();
        }
        if (node instanceof FrostlakeParser.AdditiveExprContext
                || node instanceof FrostlakeParser.MultiplicativeExprContext) {
            final String left = value(valueOf(node.getChild(0)));
            final String right = value(valueOf(node.getChild(2)));
            return left == null || right == null ? null : left + " " + node.getChild(1).getText() + " " + right;
        }
        return null;
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

    /** The declared type of a column of the subquery's own relations, or null. */
    private DataType columnType(final FrostlakeParser.QualifiedNameExprContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        final String column = parts[parts.length - 1];
        for (final Map.Entry<String, Table> relation : innerRelations.entrySet()) {
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
