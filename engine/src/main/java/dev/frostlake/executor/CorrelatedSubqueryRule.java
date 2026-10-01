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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.ValueRange;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The correlated subqueries live can evaluate. Its planner turns a correlated subquery into a join, and a
 * shape it cannot turn is refused as {@code Unsupported subquery type cannot be evaluated} — when a row
 * first needs the subquery, so an outer query that reads no row never meets the refusal. Every rule below
 * was measured:
 *
 * <ul>
 *   <li><b>A scalar subquery</b> must promise one row by its shape. It is either an aggregate with no
 *       GROUP BY and no LIMIT, or a FROM-less select with no WHERE, HAVING or ORDER BY and no LIMIT
 *       below 1. A set operation never qualifies, and an aggregate may not read the outer row inside
 *       its argument: {@code (SELECT SUM(v + fz.id) FROM g)} is refused. Nor may it hold a window call,
 *       in its select list or its QUALIFY.</li>
 *   <li><b>An outer name beside the aggregates</b> of a scalar subquery's select list is answered only
 *       where every aggregate is COUNT, or MIN or MAX over a value that is neither text nor an interval, over
 *       one table or view (directly or through a derived table that only projects it), with no WHERE, HAVING or
 *       QUALIFY and a LIMIT that keeps the row: {@code (SELECT MAX(v) + fz.id FROM g)} is answered,
 *       {@code (SELECT SUM(v) + fz.id FROM g)}, {@code (SELECT MAX(s) || fz.s FROM g)},
 *       {@code (SELECT MAX(i.iv) * fz.id FROM i)} over an INTERVAL column and
 *       {@code (SELECT MAX(v) + fz.id FROM g WHERE g.v > 0)} are refused.</li>
 *   <li><b>A filter</b> — the WHERE, HAVING or join ON of a scalar aggregate, and of an EXISTS or IN
 *       subquery — may read the outer row only through comparisons whose every operand stays on one
 *       side, and only one side of which reads it. {@code g.id = fz.id}, {@code fz.id > 5}, {@code fz.id <> 5},
 *       {@code g.id IN (fz.id, 6)}, {@code g.s LIKE fz.s}, {@code hn.a + hn.c = 3},
 *       {@code g.id BETWEEN hn.a AND hn.c} and ORs of those are accepted. A bare boolean ({@code WHERE fz.b}),
 *       NOT, CASE, an operand mixing the two sides ({@code g.id + fz.id = 10}) or outer names on both sides
 *       ({@code hn.a < hn.c}, {@code fz.id = fz.id}, {@code hn.a = ABS(hn.c)}, {@code ht.t LIKE ht.u},
 *       {@code hn.a BETWEEN hn.c AND 5}) is refused, and so is an EXISTS whose own body reads the outer row.
 *       So are the negations over the outer row: IS DISTINCT FROM, NOT IN and NOT BETWEEN over an outer
 *       subject, NOT LIKE and NOT RLIKE over any outer operand. An IN list over an outer subject keeps the
 *       elements the statistics do not prove unequal to it, and is answered while one of them reads no outer
 *       name: {@code hn.a IN (hn.c, 3)} is answered, {@code hn.a IN (hn.c, 7)} over a holding 1 and 3 is not.
 *       An IN or a scalar subquery reading it is accepted. The ON of an
 *       outer join may not read the outer row at all: {@code LEFT JOIN h ON h.k = g.id AND h.k = fz.id}
 *       is refused, as are RIGHT and FULL joins so written.</li>
 *   <li><b>EXISTS and IN</b> take any other shape — GROUP BY, no FROM — but not a LIMIT, nor a GROUP BY
 *       that reads the outer row, directly or through an ordinal or alias naming an item that does
 *       ({@code GROUP BY q.v}); a positive IN a semi-join answers, a conjunct of a WHERE or of a HAVING the planner
 *       moves there (see {@code CorrelationPlanJudgement}), keeps such a GROUP BY
 *       only where its one item is a grouped column of its own and nothing is aggregated, since duplicates never
 *       change a membership. Nor an aggregate over its own rows with no GROUP BY or HAVING whose WHERE or ON
 *       reads the outer row: {@code EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id)} is refused, and an IN so
 *       written is refused only where such a filter compares otherwise than by equality. An IN whose item reads
 *       the outer row and nothing of its own over a FROM is refused: {@code IN (SELECT fz.id + 0 FROM g)}.</li>
 *   <li><b>A set operation</b> under EXISTS or IN is judged operand by operand. A UNION keeps the operands
 *       the planner does not prune; each is held to the rules above, and where more than one remains they
 *       must read the outer row alike — the same comparisons of the same outer expressions of the same
 *       relations, their sides as written, only the values compared with differing:
 *       {@code WHERE fz.id = 5 UNION ALL … WHERE fz.id = 7} is answered, {@code WHERE fz.id = 5 UNION ALL
 *       … WHERE 7 = fz.id}, {@code … WHERE fz.s = 'b'} and, over {@code fz JOIN g}, {@code … WHERE g.id = 6}
 *       are refused, and so is an operand reading no outer name beside one that does. An IN's operands may not
 *       read the outer row in their items. INTERSECT, EXCEPT and MINUS keep their first operand's rows, so the
 *       second may read the outer row only where the planner prunes it, and a UNION reading it is not planned
 *       beneath them. A LIMIT on the set operation is refused, and a scalar subquery is never a set
 *       operation.</li>
 *   <li><b>A derived table</b> of the subquery's FROM that reads the outer row in its select list alone may
 *       take any shape but DISTINCT, and a column it computes from the outer row reads the outer row wherever
 *       it is named: {@code (SELECT COUNT(*) FROM (SELECT fz.id + 1 AS x))} is answered,
 *       {@code (SELECT MAX(x) FROM (SELECT fz.id + 1 AS x))} is refused. One reading the outer row elsewhere
 *       must be a plain select: no set operation, LIMIT, TOP, DISTINCT or GROUP BY, and filters held to the
 *       rule above, its own derived tables likewise. {@code FROM (SELECT v + fz.id AS x FROM g WHERE g.id =
 *       fz.id)} is accepted, {@code FROM (SELECT v FROM g WHERE g.id = fz.id LIMIT 1)} is refused.</li>
 * </ul>
 *
 * <p>Live's planner answers from metadata first, and four of its shortcuts are mirrored: a membership whose
 * select the statistics prove empty — an empty table, a WHERE or an inner join's ON they rule out, an IN
 * subject they prove never equal to the item — is pruned away, though a GROUP BY over the outer row is refused
 * ahead of that; an outer relation of at most one row is evaluated row by row (see {@code SubqueryEvaluator});
 * and an outer name the statistics pin to one value is read as that value (see {@link OuterNameConstancy});
 * either way nothing is refused. The rules over a GROUP BY, an aggregate body, an IN item and a comparison read an
 * outer name only where the statistics show it varying (see {@code readsVaryingOuter}).
 *
 * <p>Correlation is read off the parse tree, through every nested select, each resolving a name in its own
 * relations before the ones around it. A name the rule cannot place leaves the subquery unjudged, so its
 * execution reports the name, as live reports it first.
 */
public final class CorrelatedSubqueryRule {

    private final QueryExecutor executor;
    private final FunctionRegistry functions;
    private final Catalog catalog;
    private final Set<String> outerNames;
    private final Map<String, Table> outerRelations;
    private final Map<String, Table> innerRelations = new HashMap<>();
    private final OuterNameConstancy constancy;
    private CorrelatedFilterFold filterFold;
    private boolean sawOuter;
    private boolean sawUnknown;
    /** Whether a shape is being judged, so a derived column computed from the outer row reads it. */
    private boolean judging;
    /**
     * Whether a rule is being judged that reads an outer name only where the statistics show it varying (see
     * {@link #readsVaryingOuter}), so a name they say nothing of correlates nothing there.
     */
    private boolean varyingOnly;
    /** Whether the membership judged is a positive IN a semi-join answers (see {@link #refusesMembership}). */
    private boolean semiJoin;
    /** The judged IN's left operand as written, or null where it is not known or the membership is an EXISTS. */
    private String inSubject;
    private StoredColumnValues storedValues;

    /**
     * @param outerNames every name the evaluated row offers the subquery, upper-cased: bare columns and
     *                   relation-qualified ones, the row's own and the rows further out
     */
    public CorrelatedSubqueryRule(final QueryExecutor executor, final Set<String> outerNames) {
        this(executor, outerNames, new HashMap<String, Table>());
    }

    /**
     * @param outerNames     every name the evaluated row offers the subquery, upper-cased
     * @param outerRelations the outer query's relations keyed by the name it reaches each by, whose
     *                       statistics settle a correlated filter (see {@link CorrelatedFilterFold});
     *                       empty where the caller does not hold them, which leaves every filter open
     */
    public CorrelatedSubqueryRule(final QueryExecutor executor, final Set<String> outerNames,
                                  final Map<String, Table> outerRelations) {
        this(executor, outerNames, outerRelations, null);
    }

    /**
     * @param outerNames     every name the evaluated row offers the subquery, upper-cased
     * @param outerRelations the outer query's relations keyed by the name it reaches each by
     * @param constancy      which outer names the statistics pin to one value, which then correlate nothing;
     *                       null where the caller cannot tell
     */
    public CorrelatedSubqueryRule(final QueryExecutor executor, final Set<String> outerNames,
                                  final Map<String, Table> outerRelations, final OuterNameConstancy constancy) {
        this.executor = executor;
        this.functions = executor.getFunctionRegistry();
        this.catalog = executor.getCatalog();
        this.outerNames = outerNames;
        this.outerRelations = outerRelations;
        this.constancy = constancy;
    }

    /** Whether live refuses {@code subquery} read as a value. */
    public boolean refusesScalar(final FrostlakeParser.SelectStatementContext subquery) {
        final boolean judgingBefore = judging;
        judging = true;
        try {
            return judgesScalar(subquery);
        } finally {
            judging = judgingBefore;
        }
    }

    private boolean judgesScalar(final FrostlakeParser.SelectStatementContext subquery) {
        if (!judgeable(subquery) || !singleColumn(subquery)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
        if (clause == null) {
            return true;
        }
        if (innerRelationEmpty(clause) || limitedToNoRow(subquery, clause)) {
            return false;
        }
        if (clause.groupByClause() != null || clause.connectByClause() != null
                || readsOuterThroughTableFunction(clause, scopeOfAlone(clause))) {
            return true;
        }
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        if (!aggregateCalls(clause.selectList()).isEmpty()) {
            if (holdsWindowCall(clause.selectList())
                    || clause.qualifyClause() != null && holdsWindowCall(clause.qualifyClause())) {
                return true;
            }
            if (readsOuterBesideAggregates(clause.selectList(), scopes)) {
                return readsOuterInsideAggregate(clause, subquery, scopes) || !answeredBesideAggregates(subquery, clause);
            }
            return limited(subquery) || clause.topClause() != null
                || readsOuterInsideAggregate(clause, subquery, scopes)
                || !filtersSupported(clause, scopes) || !derivedTablesSupported(clause, scopes);
        }
        if (clause.tableExpression() != null && !readsOneRowRelationsOnly(clause)) {
            // A filter the relations' statistics settle leaves no correlation to plan (see CorrelatedFilterFold):
            // settled FALSE it reads no row and the value is NULL — tt.id = fz.id over key ranges that never
            // meet, through a join or an ORDER BY too — and settled TRUE, with the outer row read nowhere else,
            // the subquery runs as if uncorrelated (live-verified).
            if (clause.whereClause() == null) {
                return true;
            }
            final Boolean settled = settlement(clause.whereClause().booleanExpr());
            return !(Boolean.FALSE.equals(settled) || Boolean.TRUE.equals(settled)
                && !readsOuterBeyond(subquery, clause, scopes, clause.whereClause()));
        }
        return clause.whereClause() != null || clause.havingClause() != null || clause.qualifyClause() != null
            || ordered(subquery) || !limitKeepsTheRow(subquery, clause);
    }

    /**
     * Whether a select's FROM holds nothing but derived tables of exactly one row — FROM-less selects with no
     * filter, grouping or LIMIT, or selects over such tables — so it reads one row as a FROM-less select does:
     * {@code (SELECT x FROM (SELECT fz.id + 1 AS x))} is answered.
     */
    private static boolean readsOneRowRelationsOnly(final FrostlakeParser.SelectClauseContext clause) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || !from.joinClause().isEmpty()) {
            return false;
        }
        for (final FrostlakeParser.TableReferenceContext relation : from.tableReference()) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            if (source == null || source.selectStatement() == null || relation.LATERAL() != null
                    || relation.pivotClause() != null || relation.unpivotClause() != null
                    || relation.sampleClause() != null || limited(source.selectStatement())) {
                return false;
            }
            final FrostlakeParser.SelectClauseContext body = mainClause(source.selectStatement());
            if (body == null || body.whereClause() != null || body.groupByClause() != null
                    || body.havingClause() != null || body.qualifyClause() != null || body.topClause() != null
                    || body.tableExpression() != null && !readsOneRowRelationsOnly(body)) {
                return false;
            }
        }
        return true;
    }

    /** Whether live refuses {@code subquery} under EXISTS. */
    public boolean refusesMembership(final FrostlakeParser.SelectStatementContext subquery) {
        return refusesMembership(subquery, false);
    }

    /**
     * Whether live refuses {@code subquery} under IN or NOT IN ({@code in}) or EXISTS, with no more known of where
     * it stands (see {@link #refusesMembership(FrostlakeParser.SelectStatementContext, boolean, boolean, String)}).
     */
    public boolean refusesMembership(final FrostlakeParser.SelectStatementContext subquery, final boolean in) {
        return refusesMembership(subquery, in, false, null);
    }

    /**
     * Whether live refuses {@code subquery} under IN or NOT IN ({@code in}) or EXISTS. A membership limited to no
     * row folds away — under IN whatever it selects, under EXISTS unless it selects an ungrouped aggregate — and
     * under IN a LIMIT keeps nothing out while the outer row is read in the select list alone: {@code IN (SELECT
     * v + fz.id FROM g LIMIT 1)} is answered where {@code IN (SELECT id FROM g WHERE g.id = fz.id LIMIT 1)} is
     * refused (live-verified).
     *
     * @param semiJoin whether the membership is a positive IN that is a conjunct of a WHERE, or of a HAVING the
     *                 planner moves to the WHERE, through AND and parentheses: a semi-join answers it, so a GROUP BY
     *                 that only removes duplicates is dropped
     * @param subject  the IN's left operand as written, or null where it is not known: where the statistics
     *                 prove it never equals the subquery's item, the membership folds away before it is judged
     */
    public boolean refusesMembership(final FrostlakeParser.SelectStatementContext subquery, final boolean in,
                                     final boolean semiJoin, final String subject) {
        final boolean judgingBefore = judging;
        final boolean semiJoinBefore = this.semiJoin;
        final String subjectBefore = inSubject;
        judging = true;
        this.semiJoin = in && semiJoin;
        inSubject = in ? subject : null;
        try {
            return judgeable(subquery) && refusesMembershipStatement(subquery, in);
        } finally {
            judging = judgingBefore;
            this.semiJoin = semiJoinBefore;
            inSubject = subjectBefore;
        }
    }

    /** Whether live refuses a statement under IN or EXISTS: one select, or a set operation of several. */
    private boolean refusesMembershipStatement(final FrostlakeParser.SelectStatementContext statement,
                                               final boolean in) {
        final FrostlakeParser.SelectClauseContext clause = mainClause(statement);
        if (clause == null) {
            if (limited(statement)) {
                return true;
            }
            final List<FrostlakeParser.SelectStatementContext> chain = statementChain(statement);
            return refusesSetNode(SetOperationTree.of(chain.get(chain.size() - 1)), in);
        }
        return refusesMembershipClause(statement, clause, in);
    }

    /**
     * Whether live refuses one select under IN or EXISTS.
     *
     * @param statement the statement whose one select the clause is, or null for an operand of a set operation
     *                  written without parentheses, which the set operation's own LIMIT and ORDER BY govern
     */
    private boolean refusesMembershipClause(final FrostlakeParser.SelectStatementContext statement,
                                            final FrostlakeParser.SelectClauseContext clause, final boolean in) {
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        // Judged ahead of every pruning: an empty table, a LIMIT 0 or a WHERE the statistics prove false does not
        // spare a GROUP BY over the outer row.
        if (groupsByOuter(clause, scopes) && !(semiJoin && groupingOnlyDeduplicates(clause, scopes))) {
            return true;
        }
        if (prunedAway(clause) || subjectRuledOut(clause) || statement != null && limitedToNoRow(statement, clause)) {
            return false;
        }
        if (statement != null && clause.tableExpression() != null && zeroRowLimit(statement, clause)
                && (in || aggregateCalls(clause.selectList()).isEmpty() || clause.groupByClause() != null)) {
            return false;
        }
        final boolean limitedHere = statement != null && limited(statement) || clause.topClause() != null;
        if (readsOuterThroughTableFunction(clause, scopes)
                || limitedHere && (!in || readsOuterBeyond(statement, clause, scopes, clause.selectList()))) {
            return true;
        }
        if (aggregatesOuterDerived(clause, scopes) || aggregateBodyFilteredByOuter(clause, scopes, in)) {
            return true;
        }
        if (in && clause.tableExpression() != null && readsVaryingOuter(clause.selectList(), scopes)
                && !readsDirectly(clause.selectList(), CorrelationSide.INNER, scopes)) {
            return true;
        }
        return !filtersSupported(clause, scopes) || !derivedTablesSupported(clause, scopes);
    }

    // ── EXISTS and IN ───────────────────────────────────────────────────────────────

    /**
     * Whether the GROUP BY reads the outer row: a grouping expression that does, or an ordinal or a select alias
     * naming an item that does; GROUP BY ALL groups by every item outside an aggregate.
     */
    private boolean groupsByOuter(final FrostlakeParser.SelectClauseContext clause,
                                  final List<CorrelationScope> scopes) {
        final FrostlakeParser.GroupByClauseContext groupBy = clause.groupByClause();
        if (groupBy == null) {
            return false;
        }
        if (groupBy.ALL() != null) {
            for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
                if (aggregateCalls(item).isEmpty() && readsVaryingOuter(item, scopes)) {
                    return true;
                }
            }
            return false;
        }
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            final ParseTree grouped = element.expression() != null ? groupedItem(clause, element.expression()) : element;
            if (readsVaryingOuter(grouped, scopes)) {
                return true;
            }
        }
        return false;
    }

    /** What a grouping expression groups by: the item an ordinal or a select alias names, else itself. */
    private static ParseTree groupedItem(final FrostlakeParser.SelectClauseContext clause,
                                         final FrostlakeParser.ExpressionContext grouping) {
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        if (grouping instanceof FrostlakeParser.LiteralExprContext
                && grouping.getStart().getType() == FrostlakeLexer.INTEGER_LITERAL) {
            final BigInteger ordinal = new BigInteger(grouping.getText());
            if (ordinal.signum() > 0 && ordinal.compareTo(BigInteger.valueOf(items.size())) <= 0
                    && items.get(ordinal.intValue() - 1) instanceof FrostlakeParser.ExprItemContext) {
                return ((FrostlakeParser.ExprItemContext) items.get(ordinal.intValue() - 1)).booleanExpr();
            }
            return grouping;
        }
        if (grouping instanceof FrostlakeParser.QualifiedNameExprContext
                && ((FrostlakeParser.QualifiedNameExprContext) grouping).qualifiedName().namePart().isEmpty()) {
            final String name = canonical(grouping.getText());
            for (final FrostlakeParser.SelectItemContext item : items) {
                if (item instanceof FrostlakeParser.ExprItemContext
                        && ((FrostlakeParser.ExprItemContext) item).aliasName() != null
                        && name.equals(ParseTreeText.getIdentifier(
                            ((FrostlakeParser.ExprItemContext) item).aliasName()).toUpperCase())) {
                    return ((FrostlakeParser.ExprItemContext) item).booleanExpr();
                }
            }
        }
        return grouping;
    }

    /**
     * Whether an IN's GROUP BY only removes duplicates, which a semi-join never sees, so the planner drops it:
     * nothing is aggregated, there is no HAVING, and the one item is a column of the subquery's own that the
     * GROUP BY groups by — {@code WHERE v IN (SELECT g.v FROM g GROUP BY g.v, q.v)} is answered where
     * {@code IN (SELECT g.v - 49 FROM g GROUP BY g.v, q.v)}, a NOT IN, and an IN in a select list, under OR or
     * NOT, are refused.
     */
    private boolean groupingOnlyDeduplicates(final FrostlakeParser.SelectClauseContext clause,
                                             final List<CorrelationScope> scopes) {
        final FrostlakeParser.GroupByClauseContext groupBy = clause.groupByClause();
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        if (groupBy.ALL() != null || clause.havingClause() != null || !aggregateCalls(clause.selectList()).isEmpty()
                || items.size() != 1 || !(items.get(0) instanceof FrostlakeParser.ExprItemContext)) {
            return false;
        }
        final FrostlakeParser.BooleanExprContext item = ((FrostlakeParser.ExprItemContext) items.get(0)).booleanExpr();
        if (!(item instanceof FrostlakeParser.ValueExprContext)
                || !(((FrostlakeParser.ValueExprContext) item).expression() instanceof FrostlakeParser.QualifiedNameExprContext)
                || sideOf((FrostlakeParser.QualifiedNameExprContext) ((FrostlakeParser.ValueExprContext) item).expression(),
                    scopes) != CorrelationSide.INNER) {
            return false;
        }
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.expression() == null) {
                continue;
            }
            final ParseTree grouped = groupedItem(clause, element.expression());
            if (grouped == item || canonical(grouped.getText()).equals(canonical(item.getText()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an aggregate of the select list or the HAVING reads a derived table's column computed from the
     * outer row: {@code EXISTS (SELECT MAX(x) FROM (SELECT fz.id AS x))} is refused.
     */
    private boolean aggregatesOuterDerived(final FrostlakeParser.SelectClauseContext clause,
                                           final List<CorrelationScope> scopes) {
        final List<ParserRuleContext> calls = aggregateCalls(clause.selectList());
        if (clause.havingClause() != null) {
            calls.addAll(aggregateCalls(clause.havingClause()));
        }
        for (final ParserRuleContext call : calls) {
            if (readsOuterDerived(call, scopes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an aggregate over the subquery's own rows, with no GROUP BY or HAVING, is filtered by a WHERE or an
     * ON that reads the outer row. EXISTS refuses it; IN refuses it where such a filter compares otherwise than by
     * equality: {@code IN (SELECT MAX(g.id) FROM g WHERE g.id = fz.id)} is answered,
     * {@code … WHERE g.id > fz.id} is refused. An aggregate over the outer row alone is not the subquery's own.
     */
    private boolean aggregateBodyFilteredByOuter(final FrostlakeParser.SelectClauseContext clause,
                                                 final List<CorrelationScope> scopes, final boolean in) {
        if (clause.groupByClause() != null || clause.havingClause() != null) {
            return false;
        }
        boolean own = false;
        for (final ParserRuleContext call : aggregateCalls(clause.selectList())) {
            own |= readsDirectly(call, CorrelationSide.INNER, scopes) || !readsDirectly(call, CorrelationSide.OUTER, scopes);
        }
        if (!own) {
            return false;
        }
        final boolean before = varyingOnly;
        varyingOnly = true;
        try {
            for (final FrostlakeParser.BooleanExprContext conjunct : filterConjuncts(clause)) {
                if (readsDirectly(conjunct, CorrelationSide.OUTER, scopes) && settlement(conjunct) == null
                        && (!in || !comparesByEquality(conjunct, scopes))) {
                    return true;
                }
            }
            return false;
        } finally {
            varyingOnly = before;
        }
    }

    /** The conjuncts of the WHERE and of every ON of the select's FROM, through parentheses. */
    private List<FrostlakeParser.BooleanExprContext> filterConjuncts(final FrostlakeParser.SelectClauseContext clause) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        if (clause.whereClause() != null) {
            collectConjuncts(clause.whereClause().booleanExpr(), conjuncts);
        }
        if (clause.tableExpression() != null) {
            final List<FrostlakeParser.JoinClauseContext> joins = new ArrayList<>();
            collectJoins(clause.tableExpression(), joins);
            for (final FrostlakeParser.JoinClauseContext join : joins) {
                if (join.booleanExpr() != null) {
                    collectConjuncts(join.booleanExpr(), conjuncts);
                }
            }
        }
        return conjuncts;
    }

    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext predicate,
                                         final List<FrostlakeParser.BooleanExprContext> into) {
        if (predicate instanceof FrostlakeParser.AndExprContext) {
            collectConjuncts(((FrostlakeParser.AndExprContext) predicate).booleanExpr(0), into);
            collectConjuncts(((FrostlakeParser.AndExprContext) predicate).booleanExpr(1), into);
            return;
        }
        if (predicate instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) predicate).expression() instanceof FrostlakeParser.ParenExprContext) {
            collectConjuncts(((FrostlakeParser.ParenExprContext) ((FrostlakeParser.ValueExprContext) predicate)
                .expression()).booleanExpr(), into);
            return;
        }
        into.add(predicate);
    }

    /** Whether every comparison of a condition that reads the outer row is an equality, through AND and OR. */
    private boolean comparesByEquality(final FrostlakeParser.BooleanExprContext condition,
                                       final List<CorrelationScope> scopes) {
        if (!readsDirectly(condition, CorrelationSide.OUTER, scopes)) {
            return true;
        }
        if (condition instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) condition;
            return comparesByEquality(and.booleanExpr(0), scopes) && comparesByEquality(and.booleanExpr(1), scopes);
        }
        if (condition instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext or = (FrostlakeParser.OrExprContext) condition;
            return comparesByEquality(or.booleanExpr(0), scopes) && comparesByEquality(or.booleanExpr(1), scopes);
        }
        if (!(condition instanceof FrostlakeParser.ValueExprContext)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) condition).expression();
        if (value instanceof FrostlakeParser.ParenExprContext) {
            return comparesByEquality(((FrostlakeParser.ParenExprContext) value).booleanExpr(), scopes);
        }
        return value instanceof FrostlakeParser.ComparisonExprContext
            && ((FrostlakeParser.ComparisonExprContext) value).op.getType() == FrostlakeLexer.EQ;
    }

    // ── set operations ──────────────────────────────────────────────────────────────

    /** Whether live refuses a set operation's node under IN ({@code in}) or EXISTS (see the class comment). */
    private boolean refusesSetNode(final SetOperationTree node, final boolean in) {
        if (node.isLeaf()) {
            final FrostlakeParser.SelectOperandContext operand = node.operand();
            return operand.selectClause() != null ? refusesMembershipClause(null, operand.selectClause(), in)
                : refusesMembershipStatement(operand.selectStatement(), in);
        }
        if (!node.isUnion()) {
            // The second operand may read the outer row only where the statistics prove it empty, and a UNION that
            // reads it is not planned beneath an INTERSECT, EXCEPT or MINUS.
            return readsOuterAnywhere(node.right()) && !operandProvenEmpty(node.right())
                || correlatedUnion(node.left()) || refusesSetNode(node.left(), in);
        }
        final List<SetOperationTree> branches = new ArrayList<>();
        collectUnionBranches(node, branches);
        final List<SetOperationTree> kept = new ArrayList<>();
        for (final SetOperationTree branch : branches) {
            if (!provenEmpty(branch)) {
                kept.add(branch);
            }
        }
        if (kept.size() == 1) {
            return refusesSetNode(kept.get(0), in);
        }
        String shared = null;
        for (final SetOperationTree branch : kept) {
            final FrostlakeParser.SelectClauseContext clause = branchClause(branch);
            final String signature;
            if (clause == null) {
                if (readsOuterAnywhere(branch)) {
                    return true;
                }
                signature = "";
            } else {
                if (refusesSetNode(branch, in)
                        || in && readsDirectly(clause.selectList(), CorrelationSide.OUTER, scopeOfAlone(clause))) {
                    return true;
                }
                signature = correlationSignature(clause);
            }
            if (shared == null) {
                shared = signature;
            } else if (!shared.equals(signature)) {
                return true;
            }
        }
        return false;
    }

    /** The operands a UNION joins, through nested and parenthesized UNIONs. */
    private static void collectUnionBranches(final SetOperationTree node, final List<SetOperationTree> into) {
        if (!node.isLeaf() && node.isUnion()) {
            collectUnionBranches(node.left(), into);
            collectUnionBranches(node.right(), into);
            return;
        }
        if (node.isLeaf() && node.operand().selectStatement() != null) {
            final FrostlakeParser.SelectStatementContext inner = node.operand().selectStatement();
            if (inner.selectOperand().size() > 1 && inner.withClause() == null && inner.orderByClause() == null
                    && inner.limitClause() == null && inner.fetchClause() == null) {
                final SetOperationTree tree = SetOperationTree.of(inner);
                if (tree.isUnion()) {
                    collectUnionBranches(tree, into);
                    return;
                }
            }
        }
        into.add(node);
    }

    /** The one select a branch runs, or null when the branch is a set operation of its own. */
    private static FrostlakeParser.SelectClauseContext branchClause(final SetOperationTree branch) {
        if (!branch.isLeaf()) {
            return null;
        }
        return branch.operand().selectClause() != null ? branch.operand().selectClause()
            : mainClause(branch.operand().selectStatement());
    }

    /**
     * Whether a branch that is one select holds no row by what the planner knows (see {@link #prunedAway}), or none
     * an IN's subject can equal (see {@link #subjectRuledOut}) — a GROUP BY over the outer row, judged ahead of any
     * pruning, keeps it. A branch that is a set operation of its own is not pruned: {@code … UNION ALL (SELECT 1
     * FROM g WHERE g.v = fz.id INTERSECT SELECT 1 FROM g)} is refused though its first operand holds no row.
     */
    private boolean provenEmpty(final SetOperationTree branch) {
        final FrostlakeParser.SelectClauseContext clause = branchClause(branch);
        return clause != null && !groupsByOuter(clause, scopeOfAlone(clause))
            && (prunedAway(clause) || subjectRuledOut(clause));
    }

    /**
     * Whether the second operand of an INTERSECT, EXCEPT or MINUS holds no row by what the planner knows: one select
     * pruned away, or a UNION, parenthesized or not, of such selects only.
     */
    private boolean operandProvenEmpty(final SetOperationTree operand) {
        if (!operand.isLeaf()) {
            return operand.isUnion() && operandProvenEmpty(operand.left()) && operandProvenEmpty(operand.right());
        }
        if (branchClause(operand) != null) {
            return provenEmpty(operand);
        }
        final List<FrostlakeParser.SelectStatementContext> chain = statementChain(operand.operand().selectStatement());
        final FrostlakeParser.SelectStatementContext innermost = chain.get(chain.size() - 1);
        return !limited(operand.operand().selectStatement()) && innermost.selectOperand().size() > 1
            && operandProvenEmpty(SetOperationTree.of(innermost));
    }

    /** Whether a set operation's node is, or wraps in parentheses, a UNION that reads the outer row. */
    private boolean correlatedUnion(final SetOperationTree node) {
        SetOperationTree tree = node;
        if (tree.isLeaf() && tree.operand().selectStatement() != null) {
            final List<FrostlakeParser.SelectStatementContext> chain = statementChain(tree.operand().selectStatement());
            final FrostlakeParser.SelectStatementContext innermost = chain.get(chain.size() - 1);
            if (innermost.selectOperand().size() < 2) {
                return false;
            }
            tree = SetOperationTree.of(innermost);
        }
        return !tree.isLeaf() && tree.isUnion() && readsOuterAnywhere(tree);
    }

    /** Whether a set operation's node reads the outer row anywhere. */
    private boolean readsOuterAnywhere(final SetOperationTree node) {
        for (final FrostlakeParser.SelectOperandContext operand : node.operands()) {
            if (operand.selectClause() != null ? readsOuterInClause(operand.selectClause())
                    : readsOuter(operand.selectStatement(), new ArrayList<CorrelationScope>())) {
                return true;
            }
        }
        return false;
    }

    private boolean readsOuterInClause(final FrostlakeParser.SelectClauseContext clause) {
        final boolean outerBefore = sawOuter;
        final boolean unknownBefore = sawUnknown;
        sawOuter = false;
        walk(clause, new ArrayList<CorrelationScope>());
        final boolean reads = sawOuter;
        sawOuter = outerBefore;
        sawUnknown = unknownBefore;
        return reads;
    }

    /**
     * How a select reads the outer row, for comparing a UNION's operands: every conjunct of its WHERE and ONs that
     * reads the outer row and that the statistics do not prove true, each written with the outer columns named and
     * everything else a placeholder, in a fixed order.
     */
    private String correlationSignature(final FrostlakeParser.SelectClauseContext clause) {
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        final List<String> templates = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext conjunct : filterConjuncts(clause)) {
            if (!readsDirectly(conjunct, CorrelationSide.OUTER, scopes)
                    || Boolean.TRUE.equals(settlement(conjunct))) {
                continue;
            }
            final StringBuilder template = new StringBuilder();
            template(conjunct, scopes, template);
            templates.add(template.toString().trim());
        }
        Collections.sort(templates);
        return String.join(" AND ", templates);
    }

    private void template(final ParseTree node, final List<CorrelationScope> scopes, final StringBuilder into) {
        if (node instanceof FrostlakeParser.ExpressionContext && !readsDirectly(node, CorrelationSide.OUTER, scopes)) {
            into.append("? ");
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.append(relationRead((FrostlakeParser.QualifiedNameExprContext) node, scopes)).append(' ');
            return;
        }
        if (node instanceof TerminalNode) {
            into.append(node.getText().toUpperCase()).append(' ');
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            template(node.getChild(i), scopes, into);
        }
    }

    /**
     * The relation a reference reading the outer row reads, and its column, as a UNION's operands are compared by:
     * {@code fz.id} and {@code g.id} read two relations, and so do a self-join's {@code fz.id} and {@code f2.id},
     * while {@code f.id} and a bare {@code id} over {@code fz f} read one. A derived column of the select's own
     * FROM computed from the outer row is named by its own relation.
     */
    private String relationRead(final FrostlakeParser.QualifiedNameExprContext reference,
                                final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        final String qualifier = parts.size() == 1 ? null : parts.get(parts.size() - 2);
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (qualifier == null ? scopes.get(i).resolvesBare(column) : scopes.get(i).hasQualifier(qualifier)) {
                return "~" + (qualifier == null ? "" : qualifier) + "." + column;
            }
        }
        final Table owner = outerRelation(qualifier, column);
        if (owner == null) {
            return (qualifier == null ? "" : qualifier) + "." + column;
        }
        // The least name the relation instance is reached by, so its alias and its table's name agree.
        String key = null;
        for (final Map.Entry<String, Table> relation : outerRelations.entrySet()) {
            final String name = relation.getKey().toUpperCase();
            if (relation.getValue() == owner && (key == null || name.compareTo(key) < 0)) {
                key = name;
            }
        }
        return key + "." + column;
    }

    /**
     * The outer relation a name reads: the one its qualifier names, else the one relation carrying the column, a
     * join's merged relation aside, told apart by instance; null where none does, or several do.
     */
    private Table outerRelation(final String qualifier, final String column) {
        Table owner = null;
        for (final Map.Entry<String, Table> relation : outerRelations.entrySet()) {
            final Table candidate = relation.getValue();
            if (candidate == null) {
                continue;
            }
            if (qualifier != null) {
                if (relation.getKey().equalsIgnoreCase(qualifier)) {
                    return candidate;
                }
                continue;
            }
            if (candidate.getJoinedRelations() != null || !candidate.hasColumn(column) || candidate == owner) {
                continue;
            }
            if (owner != null) {
                return null;
            }
            owner = candidate;
        }
        return owner;
    }

    /**
     * Whether a table function of the select's FROM reads the outer row: {@code TABLE(FLATTEN(E.arr))},
     * {@code LATERAL FLATTEN(E.arr)} or {@code TABLE(SPLIT_TO_TABLE(E.s, ','))} inside a scalar subquery, an
     * EXISTS or an IN is refused, aggregated or not, filtered or not (live-verified).
     */
    private boolean readsOuterThroughTableFunction(final FrostlakeParser.SelectClauseContext clause,
                                                   final List<CorrelationScope> scopes) {
        if (clause.tableExpression() == null) {
            return false;
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            if (source != null && (source.TABLE() != null || source.FLATTEN() != null || source.tableFunctionExpr() != null)
                    && readsDirectly(source, CorrelationSide.OUTER, scopes)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a clause of the select other than {@code except} reads the outer row. */
    private boolean readsOuterBeyond(final FrostlakeParser.SelectStatementContext statement,
                                     final FrostlakeParser.SelectClauseContext clause,
                                     final List<CorrelationScope> scopes, final ParseTree except) {
        final List<ParseTree> clauses = new ArrayList<>();
        clauses.add(clause.selectList());
        clauses.add(clause.tableExpression());
        clauses.add(clause.whereClause());
        clauses.add(clause.groupByClause());
        clauses.add(clause.havingClause());
        clauses.add(clause.qualifyClause());
        clauses.add(statement == null ? null : statement.orderByClause());
        for (final ParseTree read : clauses) {
            if (read != null && read != except && readsDirectly(read, CorrelationSide.OUTER, scopes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The column references inside {@code subquery} that none of its own selects resolves, in the order
     * written: the names it reads from the query around it. A reference inside an aggregate call is left
     * out, since the grouping of that query does not judge it by the reference.
     *
     * @param subquery a select nested in the query whose names are wanted
     * @return the references, each still to be placed in that query
     */
    public List<FrostlakeParser.QualifiedNameExprContext> enclosingReferences(
            final FrostlakeParser.SelectStatementContext subquery) {
        final List<FrostlakeParser.QualifiedNameExprContext> references = new ArrayList<>();
        collectEnclosingReferences(subquery, new ArrayList<CorrelationScope>(), references, false, false, false);
        return references;
    }

    /**
     * The names {@link #enclosingReferences} gives, and with them those an aggregate call inside the subquery reads
     * beside a name of the subquery's own: such an aggregate is the subquery's, computed over its rows, so the names
     * of the query around it that it reads are held to that query's grouping like any other (live-verified). An
     * aggregate over the enclosing query's names alone stays that query's own and is left out.
     *
     * @param subquery         a select nested in the query whose names are wanted
     * @param filterAggregates whether an aggregate written in a WHERE or ON inside the subquery counts too: in the
     *                         select list the subquery refuses that one itself first, in HAVING the grouping speaks
     * @return the references, each still to be placed in that query, in the order written
     */
    public List<FrostlakeParser.QualifiedNameExprContext> enclosingReferencesWithMixedAggregates(
            final FrostlakeParser.SelectStatementContext subquery, final boolean filterAggregates) {
        final List<FrostlakeParser.QualifiedNameExprContext> references = new ArrayList<>();
        collectEnclosingReferences(subquery, new ArrayList<CorrelationScope>(), references, true, filterAggregates,
            false);
        return references;
    }

    private void collectEnclosingReferences(final ParseTree node, final List<CorrelationScope> scopes,
                                            final List<FrostlakeParser.QualifiedNameExprContext> into,
                                            final boolean mixedAggregates, final boolean filterAggregates,
                                            final boolean insideFilter) {
        if (node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;
        }
        if (isAggregateCall(node)) {
            if (mixedAggregates && (filterAggregates || !insideFilter)) {
                collectMixedAggregateReferences(node, scopes, into);
            }
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            if (sideOf((FrostlakeParser.QualifiedNameExprContext) node, scopes) != CorrelationSide.INNER) {
                into.add((FrostlakeParser.QualifiedNameExprContext) node);
            }
            return;
        }
        final boolean filter = insideFilter || node instanceof FrostlakeParser.WhereClauseContext
            || node instanceof FrostlakeParser.JoinClauseContext;
        final boolean clause = node instanceof FrostlakeParser.SelectClauseContext;
        if (clause) {
            scopes.add(scopeOf((FrostlakeParser.SelectClauseContext) node));
        }
        final FrostlakeParser.SelectClauseContext ordered = node instanceof FrostlakeParser.SelectStatementContext
            ? mainClause((FrostlakeParser.SelectStatementContext) node) : null;
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            // A statement's own ORDER BY reads the names of the select it orders.
            final boolean orderBy = ordered != null
                && child == ((FrostlakeParser.SelectStatementContext) node).orderByClause();
            if (orderBy) {
                scopes.add(scopeOf(ordered));
            }
            collectEnclosingReferences(child, scopes, into, mixedAggregates, filterAggregates, filter);
            if (orderBy) {
                scopes.remove(scopes.size() - 1);
            }
        }
        if (clause) {
            scopes.remove(scopes.size() - 1);
        }
    }

    /**
     * The enclosing query's names an aggregate call reads, when it also reads a name of the subquery's own; none
     * when it reads the enclosing query's names alone or the subquery's alone. A query nested in the call keeps its
     * names to itself.
     */
    private void collectMixedAggregateReferences(final ParseTree call, final List<CorrelationScope> scopes,
                                                 final List<FrostlakeParser.QualifiedNameExprContext> into) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNamesOutsideQueries(call, names);
        final List<FrostlakeParser.QualifiedNameExprContext> enclosing = new ArrayList<>();
        boolean own = false;
        for (final FrostlakeParser.QualifiedNameExprContext name : names) {
            if (sideOf(name, scopes) == CorrelationSide.INNER) {
                own = true;
            } else {
                enclosing.add(name);
            }
        }
        if (own) {
            into.addAll(enclosing);
        }
    }

    private static void collectNamesOutsideQueries(final ParseTree node,
                                                   final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNamesOutsideQueries(node.getChild(i), into);
        }
    }

    // ── correlation ───────────────────────────────────────────────────────────────

    /** Whether the subquery reads an outer name and every name in it could be placed. */
    private boolean judgeable(final FrostlakeParser.SelectStatementContext subquery) {
        sawOuter = false;
        sawUnknown = false;
        walk(subquery, new ArrayList<CorrelationScope>());
        return sawOuter && !sawUnknown;
    }

    private void walk(final ParseTree node, final List<CorrelationScope> scopes) {
        if (node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;   // a lambda's parameters are its own names
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final CorrelationSide side = sideOf((FrostlakeParser.QualifiedNameExprContext) node, scopes);
            sawOuter |= side == CorrelationSide.OUTER;
            sawUnknown |= side == CorrelationSide.UNKNOWN;
            return;
        }
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            scopes.add(scopeOf((FrostlakeParser.SelectClauseContext) node));
            walkChildren(node, scopes);
            scopes.remove(scopes.size() - 1);
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // The statement's own ORDER BY reads the names of the select it orders.
            final FrostlakeParser.SelectStatementContext statement = (FrostlakeParser.SelectStatementContext) node;
            final FrostlakeParser.SelectClauseContext ordered = mainClause(statement);
            for (int i = 0; i < node.getChildCount(); i++) {
                final ParseTree child = node.getChild(i);
                if (child == statement.orderByClause() && ordered != null) {
                    scopes.add(scopeOf(ordered));
                    walk(child, scopes);
                    scopes.remove(scopes.size() - 1);
                } else {
                    walk(child, scopes);
                }
            }
            return;
        }
        walkChildren(node, scopes);
    }

    private void walkChildren(final ParseTree node, final List<CorrelationScope> scopes) {
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), scopes);
        }
    }

    /**
     * Where a reference resolves: the innermost scope that carries it, else the outer row, else nowhere. While a
     * shape is judged, a derived column computed from the outer row reads the outer row, and an outer name the
     * statistics pin to one value reads a constant.
     */
    private CorrelationSide sideOf(final FrostlakeParser.QualifiedNameExprContext reference,
                                   final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        if (parts.size() == 1) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                if (scopes.get(i).resolvesBare(column)) {
                    return judging && scopes.get(i).outerDerivedReads(null, column) != null
                        ? CorrelationSide.OUTER : CorrelationSide.INNER;
                }
            }
            if (!outerNames.contains(column)) {
                return CorrelationSide.UNKNOWN;
            }
            return constant(null, column) ? CorrelationSide.CONSTANT : CorrelationSide.OUTER;
        }
        final String relation = parts.get(parts.size() - 2);
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).hasQualifier(relation)) {
                return judging && scopes.get(i).outerDerivedReads(relation, column) != null
                    ? CorrelationSide.OUTER : CorrelationSide.INNER;
            }
        }
        if (!outerNames.contains(relation + "." + column) && !outerNames.contains(String.join(".", parts))) {
            return CorrelationSide.UNKNOWN;
        }
        return constant(relation, column) ? CorrelationSide.CONSTANT : CorrelationSide.OUTER;
    }

    /**
     * Whether the statistics pin an outer name to one value (see {@link OuterNameConstancy}) — or, while a rule reading
     * only varying names is judged, whether they fail to show it varying.
     */
    private boolean constant(final String qualifier, final String column) {
        if (!judging || constancy == null) {
            return false;
        }
        return constancy.holdsOneValue(qualifier, column)
            || varyingOnly && !constancy.variesAcrossRows(qualifier, column);
    }

    /**
     * Whether {@code node} reads, outside any nested select, an outer name the statistics show varying from row to
     * row (see {@link OuterNameConstancy#variesAcrossRows}). The rules refusing a GROUP BY over the outer row, an
     * aggregate body it filters, an IN item reading it alone, and a comparison of it with itself, with another outer
     * name, or by IS DISTINCT FROM, NOT LIKE or a NOT IN over it read the outer row so. Live folds a derived
     * relation's column holding one value before it plans them — one passing a one-valued column through, a literal,
     * {@code UPPER(t)}, {@code CURRENT_DATE()} or a CASE over such values — so a name whose values the statistics do
     * not reach here is not taken to vary. Every other rule reads every outer name the statistics do not pin.
     */
    private boolean readsVaryingOuter(final ParseTree node, final List<CorrelationScope> scopes) {
        final boolean before = varyingOnly;
        varyingOnly = true;
        try {
            return readsDirectly(node, CorrelationSide.OUTER, scopes);
        } finally {
            varyingOnly = before;
        }
    }

    /**
     * The outer columns a reference reads: its own column where it names the outer row, the columns a derived
     * column computed from the outer row reads, and none otherwise.
     */
    private Set<String> outerColumnsOf(final FrostlakeParser.QualifiedNameExprContext reference,
                                       final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        final String relation = parts.size() == 1 ? null : parts.get(parts.size() - 2);
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (relation == null ? scopes.get(i).resolvesBare(column) : scopes.get(i).hasQualifier(relation)) {
                final Set<String> derived = judging ? scopes.get(i).outerDerivedReads(relation, column) : null;
                return derived != null ? derived : Collections.<String>emptySet();
            }
        }
        return sideOf(reference, scopes) == CorrelationSide.OUTER
            ? Collections.singleton(column) : Collections.<String>emptySet();
    }

    /** The outer columns {@code node} reads outside any nested select (see {@link #outerColumnsOf}). */
    private void collectOuterColumns(final ParseTree node, final List<CorrelationScope> scopes,
                                     final Set<String> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.addAll(outerColumnsOf((FrostlakeParser.QualifiedNameExprContext) node, scopes));
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectOuterColumns(node.getChild(i), scopes, into);
        }
    }

    /** Whether {@code node} reads a derived table's column computed from the outer row, outside any nested select. */
    private boolean readsOuterDerived(final ParseTree node, final List<CorrelationScope> scopes) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final List<String> parts = partsOf(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            final String column = parts.get(parts.size() - 1);
            final String relation = parts.size() == 1 ? null : parts.get(parts.size() - 2);
            for (int i = scopes.size() - 1; i >= 0; i--) {
                if (relation == null ? scopes.get(i).resolvesBare(column) : scopes.get(i).hasQualifier(relation)) {
                    return scopes.get(i).outerDerivedReads(relation, column) != null;
                }
            }
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsOuterDerived(node.getChild(i), scopes)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> partsOf(final FrostlakeParser.QualifiedNameContext name) {
        final List<String> parts = new ArrayList<>();
        parts.add(canonical(name.nameStartPart().getText()));
        for (final FrostlakeParser.NamePartContext part : name.namePart()) {
            parts.add(canonical(part.getText()));
        }
        return parts;
    }

    private static String canonical(final String written) {
        return SqlIdentifiers.canonicalText(written).toUpperCase();
    }

    // ── scopes ────────────────────────────────────────────────────────────────────

    private List<CorrelationScope> scopeOfAlone(final FrostlakeParser.SelectClauseContext clause) {
        final List<CorrelationScope> scopes = new ArrayList<>();
        scopes.add(scopeOf(clause));
        return scopes;
    }

    private CorrelationScope scopeOf(final FrostlakeParser.SelectClauseContext clause) {
        rememberRelations(clause);
        final CorrelationScope scope = new CorrelationScope();
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.ExprItemContext) {
                final FrostlakeParser.ExprItemContext exprItem = (FrostlakeParser.ExprItemContext) item;
                if (exprItem.aliasName() != null) {
                    scope.addAlias(ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase());
                } else if (exprItem.identifier() != null) {
                    scope.addAlias(ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase());
                }
            }
        }
        if (clause.tableExpression() != null) {
            final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
            collectRelations(clause.tableExpression(), relations);
            for (final FrostlakeParser.TableReferenceContext relation : relations) {
                addRelation(scope, relation);
            }
        }
        return scope;
    }

    /**
     * The catalog tables the subquery's FROM names, keyed by the name it reaches each by, kept for the
     * statistics a correlated filter is settled over.
     */
    private void rememberRelations(final FrostlakeParser.SelectClauseContext clause) {
        if (clause.tableExpression() == null) {
            return;
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            if (source == null || source.tableQualifiedName() == null) {
                continue;
            }
            final Table table = storedTable(source.tableQualifiedName());
            if (table == null) {
                continue;
            }
            final String alias = aliasOf(relation);
            final Table aliased = innerRelations.put(alias != null ? alias.toUpperCase() : table.getName().toUpperCase(), table);
            final Table named = innerRelations.put(table.getName().toUpperCase(), table);
            if (aliased != table || named != table) {
                filterFold = null;   // the statistics now hold one relation more
            }
        }
    }

    /** The catalog table a FROM name reaches, or null where it reaches none. */
    private Table storedTable(final FrostlakeParser.TableQualifiedNameContext name) {
        try {
            return catalog.resolveTable(SqlIdentifiers.canonicalText(name.getText()));
        } catch (final RuntimeException notATable) {
            return null;
        }
    }

    /** Every relation of a FROM, through parenthesized joins, but not the FROM of a nested select. */
    private void collectRelations(final ParseTree node, final List<FrostlakeParser.TableReferenceContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.TableReferenceContext) {
            into.add((FrostlakeParser.TableReferenceContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRelations(node.getChild(i), into);
        }
    }

    private void addRelation(final CorrelationScope scope, final FrostlakeParser.TableReferenceContext relation) {
        final String alias = aliasOf(relation);
        final FrostlakeParser.TableSourceContext source = relation.tableSource();
        if (source != null && source.selectStatement() != null) {
            if (alias != null) {
                scope.addQualifier(alias);
            }
            if (!addDerivedColumns(scope, relation, source.selectStatement())) {
                scope.markOpaque();
            }
            return;
        }
        if (source == null || source.tableQualifiedName() == null) {
            if (alias != null) {
                scope.addQualifier(alias);
            }
            scope.markOpaque();
            return;
        }
        final FrostlakeParser.TableQualifiedNameContext name = source.tableQualifiedName();
        final String lastPart = name.namePart().isEmpty() ? canonical(name.nameStartPart().getText())
            : canonical(name.namePart(name.namePart().size() - 1).getText());
        scope.addQualifier(alias != null ? alias : lastPart);
        if (relation.pivotClause() != null || relation.unpivotClause() != null) {
            scope.markOpaque();
            return;
        }
        final Table table = resolvedTable(name.getText());
        if (table == null) {
            scope.markOpaque();   // a CTE, a view, a name that does not resolve: its columns are unknown here
            return;
        }
        for (final TableColumn column : table.getColumns()) {
            scope.addColumn(column.getName().toUpperCase());
        }
    }

    /**
     * A derived table's column names: its column list when it has one, else each item's alias or, for a bare
     * column, the column's own name. False when the names cannot be read: a star, or a set operation. While a
     * shape is judged, a column whose item reads the outer row is recorded with the outer columns it reads.
     */
    private boolean addDerivedColumns(final CorrelationScope scope, final FrostlakeParser.TableReferenceContext relation,
                                      final FrostlakeParser.SelectStatementContext derived) {
        final FrostlakeParser.SelectClauseContext clause = mainClause(derived);
        final List<CorrelationScope> bodyScopes = judging && clause != null && relation.LATERAL() == null
            ? scopeOfAlone(clause) : null;
        final String alias = aliasOf(relation);
        if (relation.identifierList() != null) {
            final List<FrostlakeParser.IdentifierContext> names = relation.identifierList().identifier();
            for (int i = 0; i < names.size(); i++) {
                final String name = ParseTreeText.getIdentifier(names.get(i)).toUpperCase();
                scope.addColumn(name);
                if (bodyScopes != null && i < clause.selectList().selectItem().size()) {
                    markOuterDerived(scope, alias, name, clause.selectList().selectItem(i), bodyScopes);
                }
            }
            return true;
        }
        if (clause == null) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final FrostlakeParser.ExprItemContext exprItem = (FrostlakeParser.ExprItemContext) item;
            String name = null;
            if (exprItem.aliasName() != null) {
                name = ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase();
            } else if (exprItem.identifier() != null) {
                name = ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase();
            } else if (exprItem.booleanExpr() instanceof FrostlakeParser.ValueExprContext
                    && ((FrostlakeParser.ValueExprContext) exprItem.booleanExpr()).expression()
                        instanceof FrostlakeParser.QualifiedNameExprContext) {
                final List<String> parts = partsOf(((FrostlakeParser.QualifiedNameExprContext)
                    ((FrostlakeParser.ValueExprContext) exprItem.booleanExpr()).expression()).qualifiedName());
                name = parts.get(parts.size() - 1);
            }
            // any other unaliased item is named by its own text, which a bare name does not reach
            if (name != null) {
                scope.addColumn(name);
                if (bodyScopes != null) {
                    markOuterDerived(scope, alias, name, item, bodyScopes);
                }
            }
        }
        return true;
    }

    /** Record a derived column whose item reads the outer row, with the outer columns it reads. */
    private void markOuterDerived(final CorrelationScope scope, final String alias, final String name,
                                  final FrostlakeParser.SelectItemContext item, final List<CorrelationScope> bodyScopes) {
        final Set<String> reads = new HashSet<>();
        collectOuterColumns(item, bodyScopes, reads);
        if (!reads.isEmpty()) {
            scope.addOuterDerived(alias, name, reads);
        }
    }

    private String aliasOf(final FrostlakeParser.TableReferenceContext relation) {
        if (relation.aliasName() != null) {
            return ParseTreeText.getIdentifier(relation.aliasName()).toUpperCase();
        }
        if (relation.nonJoinKeywordIdentifier() != null) {
            return canonical(relation.nonJoinKeywordIdentifier().getText());
        }
        return null;
    }

    private Table resolvedTable(final String written) {
        try {
            return catalog.resolveTable(SqlIdentifiers.canonicalText(written));
        } catch (final RuntimeException unresolvable) {
            return null;
        }
    }

    // ── shape ─────────────────────────────────────────────────────────────────────

    /**
     * Whether the subquery's first select names one column. More than one, or a star, belongs to the
     * column-count refusal, which live reports ahead of this one.
     */
    private static boolean singleColumn(final FrostlakeParser.SelectStatementContext statement) {
        FrostlakeParser.SelectStatementContext current = statement;
        while (current != null && !current.selectOperand().isEmpty()) {
            final FrostlakeParser.SelectOperandContext operand = current.selectOperand(0);
            if (operand.selectClause() != null) {
                final List<FrostlakeParser.SelectItemContext> items = operand.selectClause().selectList().selectItem();
                return items.size() == 1 && (items.get(0) instanceof FrostlakeParser.ExprItemContext
                    || items.get(0) instanceof FrostlakeParser.ObjectStarItemContext);
            }
            current = operand.selectStatement();
        }
        return false;
    }

    /**
     * Whether a catalog table in the subquery's own FROM holds no row. Live's planner prunes such a
     * subquery before it asks whether it can evaluate it, so an empty lookup table is never refused.
     */
    private boolean innerRelationEmpty(final FrostlakeParser.SelectClauseContext clause) {
        if (clause.tableExpression() == null) {
            return false;
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            // The relation's own SOURCE TEXT, so the name is canonicalised here rather than read as
            // written: a storage key is built from a canonical name, and this text is neither folded
            // nor stripped of its quotes yet.
            if (source != null && source.tableQualifiedName() != null
                    && executor.storedRowCountOf(
                        SqlIdentifiers.canonicalText(source.tableQualifiedName().getText())) == 0) {
                return true;
            }
        }
        return false;
    }

    /** The one select a statement runs, through parenthesized operands, or null for a set operation. */
    private static FrostlakeParser.SelectClauseContext mainClause(
            final FrostlakeParser.SelectStatementContext statement) {
        FrostlakeParser.SelectStatementContext current = statement;
        while (current != null && current.selectOperand().size() == 1) {
            final FrostlakeParser.SelectOperandContext operand = current.selectOperand(0);
            if (operand.selectClause() != null) {
                return operand.selectClause();
            }
            current = operand.selectStatement();
        }
        return null;
    }

    /** The statement and the parenthesized statements inside it, down to its one select. */
    private static List<FrostlakeParser.SelectStatementContext> statementChain(
            final FrostlakeParser.SelectStatementContext statement) {
        final List<FrostlakeParser.SelectStatementContext> chain = new ArrayList<>();
        FrostlakeParser.SelectStatementContext current = statement;
        while (current != null) {
            chain.add(current);
            current = current.selectOperand().size() == 1 ? current.selectOperand(0).selectStatement() : null;
        }
        return chain;
    }

    private static boolean limited(final FrostlakeParser.SelectStatementContext statement) {
        for (final FrostlakeParser.SelectStatementContext level : statementChain(statement)) {
            if (level.limitClause() != null || level.fetchClause() != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean ordered(final FrostlakeParser.SelectStatementContext statement) {
        for (final FrostlakeParser.SelectStatementContext level : statementChain(statement)) {
            if (level.orderByClause() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a select's one row survives its LIMIT, FETCH or TOP: a count of 1 or more, with no OFFSET or an
     * OFFSET of 0 or NULL. LIMIT 0 loses the row, and so does any other OFFSET — where live, planning the
     * subquery as a join, drops that OFFSET and answers the row anyway, which is not mirrored.
     */
    private static boolean limitKeepsTheRow(final FrostlakeParser.SelectStatementContext statement,
                                            final FrostlakeParser.SelectClauseContext clause) {
        for (final FrostlakeParser.SelectStatementContext level : statementChain(statement)) {
            final FrostlakeParser.LimitClauseContext limit = level.limitClause();
            if (limit != null && (!positiveLiteral(limit.getChild(1)) || !noOffset(offsetOf(limit)))) {
                return false;
            }
            final FrostlakeParser.FetchClauseContext fetch = level.fetchClause();
            if (fetch != null && (!positiveLiteral(countOf(fetch)) || !noOffset(offsetOf(fetch)))) {
                return false;
            }
        }
        return clause.topClause() == null || positive(clause.topClause().INTEGER_LITERAL().getText());
    }

    /**
     * Whether a select over a FROM is limited to no row at all — {@code LIMIT 0}, {@code FETCH FIRST 0 ROWS},
     * {@code TOP 0} — and folds to an empty result before live judges its shape, which it does where its select
     * list reads no outer name and is no aggregate without a GROUP BY: {@code (SELECT v FROM g WHERE g.id =
     * fz.id LIMIT 0)} is answered, {@code (SELECT MAX(v) + fz.id FROM g LIMIT 0)} and
     * {@code (SELECT v + fz.id FROM g LIMIT 0)} are refused. A FROM-less select is not folded.
     */
    private boolean limitedToNoRow(final FrostlakeParser.SelectStatementContext statement,
                                   final FrostlakeParser.SelectClauseContext clause) {
        if (clause.tableExpression() == null
                || !aggregateCalls(clause.selectList()).isEmpty() && clause.groupByClause() == null
                || readsDirectly(clause.selectList(), CorrelationSide.OUTER, scopeOfAlone(clause))) {
            return false;
        }
        return zeroRowLimit(statement, clause);
    }

    /** Whether a TOP, LIMIT or FETCH of the statement, at any level, keeps no row. */
    private static boolean zeroRowLimit(final FrostlakeParser.SelectStatementContext statement,
                                        final FrostlakeParser.SelectClauseContext clause) {
        if (clause.topClause() != null && zeroLiteral(clause.topClause().INTEGER_LITERAL())) {
            return true;
        }
        for (final FrostlakeParser.SelectStatementContext level : statementChain(statement)) {
            if (level.limitClause() != null && zeroLiteral(level.limitClause().getChild(1))
                    || level.fetchClause() != null && zeroLiteral(countOf(level.fetchClause()))) {
                return true;
            }
        }
        return false;
    }

    /** The token after OFFSET in a LIMIT or FETCH clause, or null when none is written. */
    private static ParseTree offsetOf(final ParserRuleContext clause) {
        for (int i = 0; i + 1 < clause.getChildCount(); i++) {
            if (clause.getChild(i) instanceof TerminalNode
                    && ((TerminalNode) clause.getChild(i)).getSymbol().getType() == FrostlakeLexer.OFFSET) {
                return clause.getChild(i + 1);
            }
        }
        return null;
    }

    /** The count a FETCH clause writes: the first token after FETCH and its FIRST or NEXT. */
    private static ParseTree countOf(final FrostlakeParser.FetchClauseContext fetch) {
        int i = 0;
        while (i < fetch.getChildCount() && !(fetch.getChild(i) instanceof TerminalNode
                && ((TerminalNode) fetch.getChild(i)).getSymbol().getType() == FrostlakeLexer.FETCH)) {
            i++;
        }
        i++;
        if (i < fetch.getChildCount() && fetch.getChild(i) instanceof TerminalNode) {
            final int type = ((TerminalNode) fetch.getChild(i)).getSymbol().getType();
            if (type == FrostlakeLexer.FIRST || type == FrostlakeLexer.NEXT) {
                i++;
            }
        }
        return i < fetch.getChildCount() ? fetch.getChild(i) : null;
    }

    /** No OFFSET, or an OFFSET of 0 or NULL. */
    private static boolean noOffset(final ParseTree offset) {
        return offset == null || zeroLiteral(offset)
            || offset instanceof TerminalNode && ((TerminalNode) offset).getSymbol().getType() == FrostlakeLexer.NULL;
    }

    private static boolean positiveLiteral(final ParseTree token) {
        return token instanceof TerminalNode && ((TerminalNode) token).getSymbol().getType() == FrostlakeLexer.INTEGER_LITERAL
            && positive(token.getText());
    }

    private static boolean zeroLiteral(final ParseTree token) {
        return token instanceof TerminalNode && ((TerminalNode) token).getSymbol().getType() == FrostlakeLexer.INTEGER_LITERAL
            && new BigInteger(token.getText()).signum() == 0;
    }

    private static boolean positive(final String literal) {
        return new BigInteger(literal).signum() > 0;
    }

    /** The aggregate calls directly in {@code node}: not windowed, and not inside a nested select. */
    private List<ParserRuleContext> aggregateCalls(final ParseTree node) {
        final List<ParserRuleContext> calls = new ArrayList<>();
        collectAggregateCalls(node, calls);
        return calls;
    }

    private void collectAggregateCalls(final ParseTree node, final List<ParserRuleContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (isAggregateCall(node)) {
            into.add((ParserRuleContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAggregateCalls(node.getChild(i), into);
        }
    }

    private boolean isAggregateCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            return call.overClause() == null && isAggregateName(call.functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return isAggregateName(((FrostlakeParser.FunctionCallStarExprContext) node).functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext call =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) node;
            return call.overClause() == null && isAggregateName(call.functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext call =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) node;
            return call.overClause() == null && isAggregateName(call.functionName());
        }
        return false;
    }

    private boolean isAggregateName(final FrostlakeParser.FunctionNameContext name) {
        return functions.hasAggregateFunction(name.getText().toUpperCase());
    }

    /** Whether an aggregate in the select list, the HAVING or an ORDER BY reads the outer row in its argument. */
    private boolean readsOuterInsideAggregate(final FrostlakeParser.SelectClauseContext clause,
                                              final FrostlakeParser.SelectStatementContext statement,
                                              final List<CorrelationScope> scopes) {
        final List<ParserRuleContext> calls = aggregateCalls(clause.selectList());
        if (clause.havingClause() != null) {
            calls.addAll(aggregateCalls(clause.havingClause()));
        }
        for (final FrostlakeParser.SelectStatementContext level : statementChain(statement)) {
            if (level.orderByClause() != null) {
                calls.addAll(aggregateCalls(level.orderByClause()));
            }
        }
        for (final ParserRuleContext call : calls) {
            for (int i = 0; i < call.getChildCount(); i++) {
                if (!(call.getChild(i) instanceof FrostlakeParser.FunctionNameContext)
                        && readsDirectly(call.getChild(i), CorrelationSide.OUTER, scopes)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether {@code node} holds a window call outside any nested select. */
    private static boolean holdsWindowCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null
                || node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                && ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).overClause() != null
                || node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                && ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).overClause() != null) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsWindowCall(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a select list reads the outer row outside its aggregate calls. */
    private boolean readsOuterBesideAggregates(final ParseTree node, final List<CorrelationScope> scopes) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.LambdaFunctionContext || isAggregateCall(node)) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return sideOf((FrostlakeParser.QualifiedNameExprContext) node, scopes) == CorrelationSide.OUTER;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsOuterBesideAggregates(node.getChild(i), scopes)) {
                return true;
            }
        }
        return false;
    }

    /** Whether live answers an aggregate subquery reading the outer row beside its aggregates (class comment). */
    private boolean answeredBesideAggregates(final FrostlakeParser.SelectStatementContext subquery,
                                             final FrostlakeParser.SelectClauseContext clause) {
        if (clause.whereClause() != null || clause.havingClause() != null || !limitKeepsTheRow(subquery, clause)) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext relation = soleRelation(clause);
        if (relation == null) {
            return false;
        }
        final FrostlakeParser.TableSourceContext source = relation.tableSource();
        final Table table;
        final String alias = aliasOf(relation);
        if (source.tableQualifiedName() != null) {
            table = resolvedTable(source.tableQualifiedName().getText());
        } else if (source.selectStatement() != null) {
            final FrostlakeParser.SelectClauseContext projection = mainClause(source.selectStatement());
            if (projection == null || limited(source.selectStatement()) || projection.topClause() != null
                    || projection.DISTINCT() != null || projection.whereClause() != null
                    || projection.groupByClause() != null || projection.havingClause() != null
                    || projection.qualifyClause() != null || projection.connectByClause() != null
                    || !aggregateCalls(projection.selectList()).isEmpty()) {
                return false;
            }
            final FrostlakeParser.TableReferenceContext projected = soleRelation(projection);
            if (projected == null || projected.tableSource().tableQualifiedName() == null) {
                return false;
            }
            table = null;   // the projection's column types are not read here
        } else {
            return false;
        }
        for (final ParserRuleContext call : aggregateCalls(clause.selectList())) {
            if (!answeredAggregate(call, table, clause, alias)) {
                return false;
            }
        }
        return true;
    }

    /** The one relation of a select's FROM, unjoined, unpivoted and unsampled; null for anything else. */
    private static FrostlakeParser.TableReferenceContext soleRelation(final FrostlakeParser.SelectClauseContext clause) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext relation = from.tableReference(0);
        if (relation.LATERAL() != null || relation.pivotClause() != null || relation.unpivotClause() != null
                || relation.sampleClause() != null || relation.tableSource() == null) {
            return null;
        }
        return relation;
    }

    /**
     * COUNT without DISTINCT, MIN or MAX over a value whose type is neither text nor an interval, where
     * {@code table} can type it, or BOOLAND_AGG or BOOLOR_AGG over a condition {@code table}'s statistics prove the
     * same of every row, which folds the aggregate away: {@code BOOLAND_AGG(v > 0)} over positive values is answered,
     * {@code BOOLOR_AGG(v > 55)} over 50 and 60 is not.
     */
    private boolean answeredAggregate(final ParserRuleContext call, final Table table,
                                      final FrostlakeParser.SelectClauseContext clause, final String alias) {
        if (call instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star = (FrostlakeParser.FunctionCallStarExprContext) call;
            return star.DISTINCT() == null && "COUNT".equalsIgnoreCase(star.functionName().getText());
        }
        if (!(call instanceof FrostlakeParser.FunctionCallExprContext)) {
            return false;
        }
        final FrostlakeParser.FunctionCallExprContext function = (FrostlakeParser.FunctionCallExprContext) call;
        final String name = function.functionName().getText().toUpperCase();
        if (function.withinGroupClause() != null || function.functionArgList() == null
                || function.functionArgList().functionArg().size() != 1) {
            return false;
        }
        if ("COUNT".equals(name)) {
            return function.DISTINCT() == null;
        }
        if ("BOOLAND_AGG".equals(name) || "BOOLOR_AGG".equals(name)) {
            return table != null && foldsByStatistics(function.functionArgList().functionArg(0), table, clause, alias);
        }
        if (!"MIN".equals(name) && !"MAX".equals(name)) {
            return false;
        }
        if (table == null) {
            return true;
        }
        try {
            final ExpressionEvaluator typing = new ExpressionEvaluator(table, functions, catalog, executor);
            if (alias != null) {
                // An argument qualified by the relation's alias reaches its column through the alias alone.
                final Map<String, Table> aliases = new HashMap<>();
                aliases.put(alias, table);
                typing.setMultiTableContext(aliases, Collections.singletonList(table));
            }
            final DataType type = typing.inferStaticType(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(function.functionArgList().functionArg(0))));
            return !(type instanceof StringType || type instanceof IntervalDayTimeType
                || type instanceof IntervalYearMonthType);
        } catch (final RuntimeException untypeable) {
            return true;
        }
    }

    /** Whether {@code table}'s statistics prove a condition TRUE of every row or FALSE of every row. */
    private boolean foldsByStatistics(final ParserRuleContext condition, final Table table,
                                      final FrostlakeParser.SelectClauseContext clause, final String alias) {
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(alias != null ? alias : table.getName().toUpperCase(), table);
        try {
            return new CountStatisticsBound(executor, clause, table, aliases, null, false, null)
                .verdictOver(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(condition))) != null;
        } catch (final RuntimeException unprovable) {
            return false;
        }
    }

    /**
     * Whether a predicate holds an EXISTS, outside any other nested select, whose body reads the outer row — unless
     * the statistics prove the body's filter false, which folds the EXISTS away: {@code EXISTS (SELECT 1 FROM g g2
     * WHERE g2.v = fz.id)} with g2.v and fz.id never meeting is answered.
     */
    private boolean existsReadsOuter(final ParseTree node, final List<CorrelationScope> scopes) {
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            final FrostlakeParser.SelectStatementContext body = ((FrostlakeParser.ExistsExprContext) node).selectStatement();
            final FrostlakeParser.SelectClauseContext bodyClause = mainClause(body);
            if (bodyClause != null) {
                scopeOfAlone(bodyClause);
                if (prunedAway(bodyClause)) {
                    return false;
                }
            }
            return readsOuter(body, scopes);
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (existsReadsOuter(node.getChild(i), scopes)) {
                return true;
            }
        }
        return false;
    }

    // ── filters ───────────────────────────────────────────────────────────────────

    private boolean filtersSupported(final FrostlakeParser.SelectClauseContext clause,
                                     final List<CorrelationScope> scopes) {
        if (clause.whereClause() != null && (!predicateSupported(clause.whereClause().booleanExpr(), clause, scopes)
                || existsReadsOuter(clause.whereClause().booleanExpr(), scopes))) {
            return false;
        }
        if (clause.havingClause() != null && (!predicateSupported(clause.havingClause().booleanExpr(), clause, scopes)
                || existsReadsOuter(clause.havingClause().booleanExpr(), scopes))) {
            return false;
        }
        if (clause.tableExpression() != null) {
            final List<FrostlakeParser.JoinClauseContext> joins = new ArrayList<>();
            collectJoins(clause.tableExpression(), joins);
            for (final FrostlakeParser.JoinClauseContext join : joins) {
                if (join.booleanExpr() == null) {
                    continue;
                }
                if (outerJoin(join) && readsDirectly(join.booleanExpr(), CorrelationSide.OUTER, scopes)
                        || !predicateSupported(join.booleanExpr(), clause, scopes)
                        || existsReadsOuter(join.booleanExpr(), scopes)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether every derived table of the select's FROM that reads the outer row is one live turns into a join
     * (see the class comment). A derived table reading no outer name stands on its own and is not judged.
     */
    private boolean derivedTablesSupported(final FrostlakeParser.SelectClauseContext clause,
                                           final List<CorrelationScope> scopes) {
        if (clause.tableExpression() == null) {
            return true;
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            if (source == null || source.selectStatement() == null || !readsOuter(source.selectStatement(), scopes)) {
                continue;
            }
            final FrostlakeParser.SelectStatementContext derived = source.selectStatement();
            final FrostlakeParser.SelectClauseContext body = mainClause(derived);
            if (body == null) {
                if (!readsOuterInItemsOnly(SetOperationTree.of(statementChain(derived).get(statementChain(derived).size() - 1)),
                        scopes)) {
                    return false;
                }
                continue;
            }
            final List<CorrelationScope> bodyScopes = new ArrayList<>(scopes);
            bodyScopes.add(scopeOf(body));
            if (!readsOuterBeyondItems(derived, body, bodyScopes)) {
                // Read in the select list alone, the outer row is a value the rows carry: only DISTINCT is refused.
                if (body.DISTINCT() != null) {
                    return false;
                }
                continue;
            }
            if (limited(derived) || body.topClause() != null || body.DISTINCT() != null
                    || body.groupByClause() != null) {
                return false;
            }
            if (!filtersSupported(body, bodyScopes) || !derivedTablesSupported(body, bodyScopes)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a nested select reads the outer row anywhere, through the selects nested in it. */
    private boolean readsOuter(final FrostlakeParser.SelectStatementContext statement,
                               final List<CorrelationScope> scopes) {
        return readsOuterIn(statement, scopes);
    }

    /** Whether {@code node} reads the outer row anywhere, through the selects nested in it. */
    private boolean readsOuterIn(final ParseTree node, final List<CorrelationScope> scopes) {
        final boolean outerBefore = sawOuter;
        final boolean unknownBefore = sawUnknown;
        sawOuter = false;
        walk(node, new ArrayList<CorrelationScope>(scopes));
        final boolean reads = sawOuter;
        sawOuter = outerBefore;
        sawUnknown = unknownBefore;
        return reads;
    }

    /**
     * Whether a derived table's select reads the outer row outside its select list: in its FROM, a filter, its
     * grouping, its QUALIFY or its statement's ORDER BY, the selects nested there included — but for a derived
     * table of its own FROM that reads it in its select list alone, which only carries the value.
     */
    private boolean readsOuterBeyondItems(final FrostlakeParser.SelectStatementContext derived,
                                          final FrostlakeParser.SelectClauseContext body,
                                          final List<CorrelationScope> bodyScopes) {
        if (body.tableExpression() != null) {
            final List<FrostlakeParser.JoinClauseContext> joins = new ArrayList<>();
            collectJoins(body.tableExpression(), joins);
            for (final FrostlakeParser.JoinClauseContext join : joins) {
                if (join.booleanExpr() != null && readsOuterIn(join.booleanExpr(), bodyScopes)) {
                    return true;
                }
            }
            final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
            collectRelations(body.tableExpression(), relations);
            for (final FrostlakeParser.TableReferenceContext relation : relations) {
                if (!readsOuterIn(relation, bodyScopes)) {
                    continue;
                }
                final FrostlakeParser.TableSourceContext source = relation.tableSource();
                if (source == null || source.selectStatement() == null || relation.LATERAL() != null) {
                    return true;
                }
                final FrostlakeParser.SelectStatementContext nested = source.selectStatement();
                final FrostlakeParser.SelectClauseContext nestedBody = mainClause(nested);
                if (nestedBody == null) {
                    if (!readsOuterInItemsOnly(SetOperationTree.of(statementChain(nested).get(statementChain(nested).size() - 1)),
                            bodyScopes)) {
                        return true;
                    }
                    continue;
                }
                final List<CorrelationScope> nestedScopes = new ArrayList<>(bodyScopes);
                nestedScopes.add(scopeOf(nestedBody));
                if (nestedBody.DISTINCT() != null || readsOuterBeyondItems(nested, nestedBody, nestedScopes)) {
                    return true;
                }
            }
        }
        final List<ParseTree> clauses = new ArrayList<>();
        clauses.add(body.whereClause());
        clauses.add(body.groupByClause());
        clauses.add(body.havingClause());
        clauses.add(body.qualifyClause());
        for (final FrostlakeParser.SelectStatementContext level : statementChain(derived)) {
            clauses.add(level.orderByClause());
        }
        for (final ParseTree clause : clauses) {
            if (clause != null && readsOuterIn(clause, bodyScopes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a derived set operation reads the outer row only in its operands' select lists: every operator a
     * UNION, and no operand DISTINCT or reading it elsewhere — {@code (SELECT fz.id + 1 AS x UNION ALL SELECT 2)}.
     */
    private boolean readsOuterInItemsOnly(final SetOperationTree node, final List<CorrelationScope> scopes) {
        if (!node.isLeaf()) {
            return node.isUnion() && readsOuterInItemsOnly(node.left(), scopes)
                && readsOuterInItemsOnly(node.right(), scopes);
        }
        final FrostlakeParser.SelectOperandContext operand = node.operand();
        if (operand.selectClause() == null) {
            final FrostlakeParser.SelectStatementContext inner = operand.selectStatement();
            final List<FrostlakeParser.SelectStatementContext> chain = statementChain(inner);
            return readsOuterInItemsOnly(SetOperationTree.of(chain.get(chain.size() - 1)), scopes);
        }
        final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
        final List<CorrelationScope> clauseScopes = new ArrayList<>(scopes);
        clauseScopes.add(scopeOf(clause));
        final List<ParseTree> clauses = new ArrayList<>();
        clauses.add(clause.tableExpression());
        clauses.add(clause.whereClause());
        clauses.add(clause.groupByClause());
        clauses.add(clause.havingClause());
        clauses.add(clause.qualifyClause());
        for (final ParseTree part : clauses) {
            if (part != null && readsOuterIn(part, clauseScopes)) {
                return false;
            }
        }
        return clause.DISTINCT() == null || !readsOuterIn(clause.selectList(), clauseScopes);
    }

    /** Whether a join keeps the rows its ON leaves unmatched on some side: LEFT, RIGHT or FULL. */
    private static boolean outerJoin(final FrostlakeParser.JoinClauseContext join) {
        return join.joinType() != null
            && (join.joinType().LEFT() != null || join.joinType().RIGHT() != null || join.joinType().FULL() != null);
    }

    private void collectJoins(final ParseTree node, final List<FrostlakeParser.JoinClauseContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.JoinClauseContext) {
            into.add((FrostlakeParser.JoinClauseContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectJoins(node.getChild(i), into);
        }
    }

    private boolean predicateSupported(final FrostlakeParser.BooleanExprContext predicate,
                                       final FrostlakeParser.SelectClauseContext clause,
                                       final List<CorrelationScope> scopes) {
        if (predicate instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) predicate;
            return predicateSupported(and.booleanExpr(0), clause, scopes)
                && predicateSupported(and.booleanExpr(1), clause, scopes);
        }
        if (predicate instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext or = (FrostlakeParser.OrExprContext) predicate;
            return predicateSupported(or.booleanExpr(0), clause, scopes)
                && predicateSupported(or.booleanExpr(1), clause, scopes);
        }
        if (predicate instanceof FrostlakeParser.ValueExprContext) {
            return expressionSupported(((FrostlakeParser.ValueExprContext) predicate).expression(), clause, scopes);
        }
        return !readsDirectly(predicate, CorrelationSide.OUTER, scopes) || settled(predicate);
    }

    /**
     * Whether the statistics of the relations in scope settle a condition, so the planner folds it away
     * rather than planning its correlation. The subquery's own relations are read from its FROM clause and
     * the outer query's come from the caller; a relation neither holds leaves the condition open.
     *
     * @param condition the condition, as written
     * @return true where the statistics answer it on every row alike
     */
    private boolean settled(final ParserRuleContext condition) {
        if (!aggregateCalls(condition).isEmpty()) {
            // An aggregate over the outer row is not a filter the statistics fold away: it is the enclosing
            // query's own aggregate, which only a GROUPED query computes and offers (live-verified).
            return false;
        }
        return settlement(condition) != null;
    }

    /**
     * How the statistics of the relations in scope settle a condition: TRUE or FALSE on every row, null where
     * they leave it open or it holds an aggregate.
     */
    private Boolean settlement(final ParserRuleContext condition) {
        if (!aggregateCalls(condition).isEmpty()) {
            return null;
        }
        final Boolean settled = fold().settles(ParseTreeText.getOriginalText(condition));
        return settled != null ? settled : distinctness(condition);
    }

    /**
     * How the statistics settle an IS [NOT] DISTINCT FROM, which the interval channel does not model: two operands
     * whose intervals never meet, no more than one of which may be NULL, are distinct on every row — over ids 5 and
     * 6 and a holding 1 and 3, {@code g.id IS DISTINCT FROM ht.a} is TRUE and folds away (live-verified). Null where
     * the intervals may meet or either is unknown.
     */
    private Boolean distinctness(final ParserRuleContext condition) {
        ParserRuleContext node = condition;
        if (node instanceof FrostlakeParser.ValueExprContext) {
            node = ((FrostlakeParser.ValueExprContext) node).expression();
        }
        if (!(node instanceof FrostlakeParser.IsDistinctExprContext)) {
            return null;
        }
        final List<FrostlakeParser.ExpressionContext> operands =
            node.getRuleContexts(FrostlakeParser.ExpressionContext.class);
        final ValueRange left = fold().rangeOf(ParseTreeText.getOriginalText(operands.get(0)));
        final ValueRange right = fold().rangeOf(ParseTreeText.getOriginalText(operands.get(1)));
        if (!knownInterval(left) || !knownInterval(right) || left.isNullable() && right.isNullable()
                || left.getMax().compareTo(right.getMin()) >= 0 && right.getMax().compareTo(left.getMin()) >= 0) {
            return null;
        }
        return Boolean.valueOf(((FrostlakeParser.IsDistinctExprContext) node).NOT() == null);
    }

    /** Whether an interval is known, holds a value and may settle a condition. */
    private static boolean knownInterval(final ValueRange range) {
        return range != null && !range.isEmpty() && !range.isOpaqueToConditions();
    }

    private CorrelatedFilterFold fold() {
        if (filterFold == null) {
            final Map<String, Table> relations = new HashMap<>(outerRelations);
            relations.putAll(innerRelations);
            filterFold = new CorrelatedFilterFold(executor, relations);
        }
        return filterFold;
    }

    /**
     * A comparison whose operands each keep to one side, a condition that reads no outer name, or one the
     * relations' statistics settle either way — the planner folds that one away before it plans the
     * correlation, so nothing is left to refuse (see {@link CorrelatedFilterFold}) — held then to the rules over
     * the outer names the statistics show varying (see {@link #varyingOuterComparisonSupported}).
     */
    private boolean expressionSupported(final FrostlakeParser.ExpressionContext condition,
                                        final FrostlakeParser.SelectClauseContext clause,
                                        final List<CorrelationScope> scopes) {
        if (!readsDirectly(condition, CorrelationSide.OUTER, scopes)) {
            return true;
        }
        if (settled(condition)) {
            return true;
        }
        if (condition instanceof FrostlakeParser.ParenExprContext) {
            return predicateSupported(((FrostlakeParser.ParenExprContext) condition).booleanExpr(), clause, scopes);
        }
        final List<FrostlakeParser.ExpressionContext> operands = comparedOperands(condition);
        if (operands == null || !allOneSided(operands, scopes)) {
            return false;
        }
        final boolean before = varyingOnly;
        varyingOnly = true;
        try {
            return !readsDirectly(condition, CorrelationSide.OUTER, scopes)
                || varyingOuterComparisonSupported(condition, operands, clause, scopes);
        } finally {
            varyingOnly = before;
        }
    }

    /**
     * The operands of a comparison, IS [NOT] NULL, IS [NOT] DISTINCT FROM, [NOT] LIKE, [NOT] RLIKE, [NOT] BETWEEN
     * or [NOT] IN list, the IN's subject first; null for any other condition, which may not read the outer row.
     */
    private static List<FrostlakeParser.ExpressionContext> comparedOperands(
            final FrostlakeParser.ExpressionContext condition) {
        if (condition instanceof FrostlakeParser.InListExprContext) {
            final FrostlakeParser.InListExprContext in = (FrostlakeParser.InListExprContext) condition;
            final List<FrostlakeParser.ExpressionContext> operands = new ArrayList<>();
            operands.add(in.expression());
            operands.addAll(in.expressionList().expression());
            return operands;
        }
        if (condition instanceof FrostlakeParser.ComparisonExprContext
                || condition instanceof FrostlakeParser.IsNullExprContext
                || condition instanceof FrostlakeParser.IsDistinctExprContext
                || condition instanceof FrostlakeParser.LikeExprContext
                || condition instanceof FrostlakeParser.RlikeExprContext
                || condition instanceof FrostlakeParser.BetweenExprContext) {
            return condition.getRuleContexts(FrostlakeParser.ExpressionContext.class);
        }
        return null;
    }

    /**
     * The rules a comparison keeping each operand to one side is held to over the outer names the statistics show
     * varying (see {@link #readsVaryingOuter}), which the caller has made the only outer names read. A comparison, a
     * LIKE, an RLIKE or an IS NOT DISTINCT FROM with outer names on both sides is refused, and a BETWEEN whose
     * subject and a bound both read them: {@code hn.a < hn.c}, {@code hn.a = ABS(hn.c)} and
     * {@code hn.a BETWEEN hn.c AND 5} are refused where a and c vary, {@code hn.a + hn.c = 3} and
     * {@code g.id BETWEEN hn.a AND hn.c} are answered. The negations are refused over the outer row — IS DISTINCT
     * FROM, NOT IN and NOT BETWEEN over an outer subject, NOT LIKE and NOT RLIKE over any outer operand — while
     * {@code hn.a <> 3} is answered. An IN list is judged by what the statistics leave of it (see
     * {@link #inListSupported}).
     */
    private boolean varyingOuterComparisonSupported(final FrostlakeParser.ExpressionContext condition,
                                                    final List<FrostlakeParser.ExpressionContext> operands,
                                                    final FrostlakeParser.SelectClauseContext clause,
                                                    final List<CorrelationScope> scopes) {
        if (condition instanceof FrostlakeParser.IsDistinctExprContext
                && ((FrostlakeParser.IsDistinctExprContext) condition).NOT() == null
                || condition instanceof FrostlakeParser.LikeExprContext
                && ((FrostlakeParser.LikeExprContext) condition).NOT() != null
                || condition instanceof FrostlakeParser.RlikeExprContext
                && ((FrostlakeParser.RlikeExprContext) condition).NOT() != null) {
            return false;
        }
        if (condition instanceof FrostlakeParser.InListExprContext) {
            return inListSupported((FrostlakeParser.InListExprContext) condition, clause, scopes);
        }
        if (condition instanceof FrostlakeParser.BetweenExprContext) {
            return !(readsDirectly(operands.get(0), CorrelationSide.OUTER, scopes)
                && (((FrostlakeParser.BetweenExprContext) condition).NOT() != null
                    || readsDirectly(operands.get(1), CorrelationSide.OUTER, scopes)
                    || readsDirectly(operands.get(2), CorrelationSide.OUTER, scopes)));
        }
        int outer = 0;
        for (final FrostlakeParser.ExpressionContext operand : operands) {
            if (readsDirectly(operand, CorrelationSide.OUTER, scopes)) {
                outer++;
            }
        }
        return outer < 2;
    }

    /**
     * Whether live answers an IN list over the outer row. An element the statistics prove never equal to the subject
     * drops out first — a constant outside the subject's interval, a column whose values never meet it — and the
     * list is answered while an element reading no outer name remains, or none remains at all: over a holding 1 and
     * 3, {@code hn.a IN (hn.c, 3)} is answered, {@code hn.a IN (hn.c, 7)} and {@code hn.a IN (hn.c, hn.c + 1)} are
     * refused. NOT IN over an outer subject is refused whatever its list; over an inner one it is answered.
     */
    private boolean inListSupported(final FrostlakeParser.InListExprContext in,
                                    final FrostlakeParser.SelectClauseContext clause,
                                    final List<CorrelationScope> scopes) {
        if (!readsDirectly(in.expression(), CorrelationSide.OUTER, scopes)) {
            return true;
        }
        if (in.NOT() != null) {
            return false;
        }
        boolean remains = false;
        for (final FrostlakeParser.ExpressionContext element : in.expressionList().expression()) {
            if (!nullLiteral(element) && neverEqual(in.expression(), element, clause, scopes)) {
                continue;
            }
            remains = true;
            if (!readsDirectly(element, CorrelationSide.OUTER, scopes)) {
                return true;
            }
        }
        return !remains;
    }

    private static boolean nullLiteral(final FrostlakeParser.ExpressionContext operand) {
        return operand instanceof FrostlakeParser.LiteralExprContext
            && ((FrostlakeParser.LiteralExprContext) operand).literal().NULL() != null;
    }

    /** Whether the statistics prove two operands never equal on any row: numbers by their intervals, text by its. */
    private boolean neverEqual(final FrostlakeParser.ExpressionContext left, final FrostlakeParser.ExpressionContext right,
                               final FrostlakeParser.SelectClauseContext clause, final List<CorrelationScope> scopes) {
        if (aggregateCalls(left).isEmpty() && aggregateCalls(right).isEmpty()
                && Boolean.FALSE.equals(fold().settles("(" + ParseTreeText.getOriginalText(left) + ") = ("
                    + ParseTreeText.getOriginalText(right) + ")"))) {
            return true;
        }
        return textRuledOut(left, FrostlakeLexer.EQ, right, clause, scopes);
    }

    // ── pruning ───────────────────────────────────────────────────────────────────

    /**
     * Whether the planner prunes a select before it judges its correlation: a catalog table of its FROM holds no
     * row, or the statistics prove its WHERE, or the ON of one of its inner joins, false of every row — an interval
     * that cannot meet the other side, a text comparison its column's least and greatest values rule out, IS NULL
     * over a column that holds no NULL: {@code EXISTS (SELECT 1 FROM g WHERE g.s = 'x' AND g.id + fz.id = 10)} is
     * answered over g.s holding 'a' and 'c'. A GROUP BY over the outer row is refused ahead of it.
     */
    private boolean prunedAway(final FrostlakeParser.SelectClauseContext clause) {
        if (innerRelationEmpty(clause)) {
            return true;
        }
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        // Over an outer join a column may meet the NULL the join extends its side with, which its rows do not hold.
        if (clause.whereClause() != null && !joinsOuter(clause)
                && provenFalse(clause.whereClause().booleanExpr(), clause, scopes)) {
            return true;
        }
        if (clause.tableExpression() == null) {
            return false;
        }
        final List<FrostlakeParser.JoinClauseContext> joins = clause.tableExpression().joinClause();
        for (final FrostlakeParser.JoinClauseContext join : joins) {
            if (join.joinType() != null && (join.joinType().RIGHT() != null || join.joinType().FULL() != null)) {
                return false;   // a later RIGHT or FULL join keeps its own side's rows
            }
        }
        for (final FrostlakeParser.JoinClauseContext join : joins) {
            if (join.booleanExpr() != null && !outerJoin(join) && provenFalse(join.booleanExpr(), clause, scopes)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the statistics prove a condition false of every row, through AND, OR and parentheses. */
    private boolean provenFalse(final FrostlakeParser.BooleanExprContext condition,
                                final FrostlakeParser.SelectClauseContext clause, final List<CorrelationScope> scopes) {
        if (Boolean.FALSE.equals(settlement(condition))) {
            return true;
        }
        if (condition instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) condition;
            return provenFalse(and.booleanExpr(0), clause, scopes) || provenFalse(and.booleanExpr(1), clause, scopes);
        }
        if (condition instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext or = (FrostlakeParser.OrExprContext) condition;
            return provenFalse(or.booleanExpr(0), clause, scopes) && provenFalse(or.booleanExpr(1), clause, scopes);
        }
        if (!(condition instanceof FrostlakeParser.ValueExprContext)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) condition).expression();
        if (value instanceof FrostlakeParser.ParenExprContext) {
            return provenFalse(((FrostlakeParser.ParenExprContext) value).booleanExpr(), clause, scopes);
        }
        return storedValuesRuleOut(value, clause, scopes);
    }

    /**
     * Whether the stored values rule a condition out on every row where the numeric intervals cannot: a text
     * comparison, BETWEEN or IN list, or IS NULL over a column of the select's own that holds no NULL.
     */
    private boolean storedValuesRuleOut(final FrostlakeParser.ExpressionContext condition,
                                        final FrostlakeParser.SelectClauseContext clause,
                                        final List<CorrelationScope> scopes) {
        if (condition instanceof FrostlakeParser.ComparisonExprContext) {
            final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext) condition;
            return textRuledOut(comparison.expression(0), comparison.op.getType(), comparison.expression(1),
                clause, scopes);
        }
        if (condition instanceof FrostlakeParser.BetweenExprContext
                && ((FrostlakeParser.BetweenExprContext) condition).NOT() == null) {
            final List<FrostlakeParser.ExpressionContext> operands =
                ((FrostlakeParser.BetweenExprContext) condition).expression();
            return textRuledOut(operands.get(0), FrostlakeLexer.GTE, operands.get(1), clause, scopes)
                || textRuledOut(operands.get(0), FrostlakeLexer.LTE, operands.get(2), clause, scopes);
        }
        if (condition instanceof FrostlakeParser.InListExprContext
                && ((FrostlakeParser.InListExprContext) condition).NOT() == null) {
            final FrostlakeParser.InListExprContext in = (FrostlakeParser.InListExprContext) condition;
            for (final FrostlakeParser.ExpressionContext element : in.expressionList().expression()) {
                if (!textRuledOut(in.expression(), FrostlakeLexer.EQ, element, clause, scopes)) {
                    return false;
                }
            }
            return true;
        }
        if (condition instanceof FrostlakeParser.IsNullExprContext
                && ((FrostlakeParser.IsNullExprContext) condition).NOT() == null
                && ((FrostlakeParser.IsNullExprContext) condition).expression()
                    instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext subject = (FrostlakeParser.QualifiedNameExprContext)
                ((FrostlakeParser.IsNullExprContext) condition).expression();
            final List<String> parts = partsOf(subject.qualifiedName());
            final Table owner = ownColumnRelation(subject, clause, scopes);
            // An outer join of the select's own may extend the column with NULLs its rows do not hold.
            return owner != null && !joinsOuter(clause)
                && Boolean.FALSE.equals(storedValues().holdsNull(owner, parts.get(parts.size() - 1)));
        }
        return false;
    }

    /**
     * Whether the least and the greatest text of each operand rule a comparison out on every row: a text literal
     * is its own interval, a stored uncollated text column the one its rows span, compared by code point.
     */
    private boolean textRuledOut(final FrostlakeParser.ExpressionContext left, final int op,
                                 final FrostlakeParser.ExpressionContext right,
                                 final FrostlakeParser.SelectClauseContext clause, final List<CorrelationScope> scopes) {
        final String[] l = textRange(left, clause, scopes);
        final String[] r = l == null ? null : textRange(right, clause, scopes);
        if (r == null) {
            return false;
        }
        switch (op) {
            case FrostlakeLexer.EQ:
                return StoredColumnValues.compareText(l[1], r[0]) < 0 || StoredColumnValues.compareText(r[1], l[0]) < 0;
            case FrostlakeLexer.LT:
                return StoredColumnValues.compareText(l[0], r[1]) >= 0;
            case FrostlakeLexer.LTE:
                return StoredColumnValues.compareText(l[0], r[1]) > 0;
            case FrostlakeLexer.GT:
                return StoredColumnValues.compareText(l[1], r[0]) <= 0;
            case FrostlakeLexer.GTE:
                return StoredColumnValues.compareText(l[1], r[0]) < 0;
            default:
                return false;
        }
    }

    /** The least and the greatest text an operand takes, or null where it is no text literal or stored text column. */
    private String[] textRange(final FrostlakeParser.ExpressionContext operand,
                               final FrostlakeParser.SelectClauseContext clause, final List<CorrelationScope> scopes) {
        if (operand instanceof FrostlakeParser.LiteralExprContext) {
            final TerminalNode text = ((FrostlakeParser.LiteralExprContext) operand).literal().STRING_LITERAL();
            if (text == null) {
                return null;
            }
            final String value = SqlStringLiterals.decode(text.getText());
            return new String[] {value, value};
        }
        if (!(operand instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final FrostlakeParser.QualifiedNameExprContext reference = (FrostlakeParser.QualifiedNameExprContext) operand;
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        Table owner = ownColumnRelation(reference, clause, scopes);
        if (owner == null && !resolvesInScope(reference, scopes) && outerNames.contains(
                parts.size() == 1 ? column : parts.get(parts.size() - 2) + "." + column)) {
            owner = outerRelation(parts.size() == 1 ? null : parts.get(parts.size() - 2), column);
        }
        if (owner != null && owner.residentSource() == null) {
            // A derived relation's column passing a stored column through takes its values among the stored ones.
            final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, column);
            return lineage == null || lineage.storedTable() == null ? null
                : storedValues().textRange(lineage.storedTable(), lineage.storedColumn());
        }
        return owner == null ? null : storedValues().textRange(owner, column);
    }

    /** Whether a reference resolves in one of the selects in scope rather than in the row around them. */
    private static boolean resolvesInScope(final FrostlakeParser.QualifiedNameExprContext reference,
                                           final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (parts.size() == 1 ? scopes.get(i).resolvesBare(column)
                    : scopes.get(i).hasQualifier(parts.get(parts.size() - 2))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The catalog table of the select's own FROM a reference reads, where it resolves in that select: the one its
     * qualifier names, or the one table carrying a bare name — none where the FROM also holds a relation whose
     * columns are not listed here, which may carry it.
     */
    private Table ownColumnRelation(final FrostlakeParser.QualifiedNameExprContext reference,
                                    final FrostlakeParser.SelectClauseContext clause,
                                    final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        final String qualifier = parts.size() == 1 ? null : parts.get(parts.size() - 2);
        final CorrelationScope own = scopes.isEmpty() ? null : scopes.get(scopes.size() - 1);
        if (clause == null || clause.tableExpression() == null || own == null
                || !(qualifier == null ? own.resolvesBare(column) : own.hasQualifier(qualifier))
                || own.outerDerivedReads(qualifier, column) != null) {
            return null;
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        Table owner = null;
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            final boolean stored = source != null && source.tableQualifiedName() != null
                && relation.pivotClause() == null && relation.unpivotClause() == null;
            final Table table = stored ? storedTable(source.tableQualifiedName()) : null;
            if (qualifier != null) {
                final String name = aliasOf(relation) != null ? aliasOf(relation)
                    : stored ? lastNamePart(source.tableQualifiedName()) : null;
                if (qualifier.equals(name)) {
                    return table;
                }
                continue;
            }
            if (table == null) {
                return null;
            }
            if (!table.hasColumn(column) || table == owner) {
                continue;
            }
            if (owner != null) {
                return null;
            }
            owner = table;
        }
        return owner;
    }

    private static String lastNamePart(final FrostlakeParser.TableQualifiedNameContext name) {
        return name.namePart().isEmpty() ? canonical(name.nameStartPart().getText())
            : canonical(name.namePart(name.namePart().size() - 1).getText());
    }

    /** Whether the select's FROM holds a LEFT, RIGHT or FULL join. */
    private boolean joinsOuter(final FrostlakeParser.SelectClauseContext clause) {
        if (clause.tableExpression() == null) {
            return false;
        }
        final List<FrostlakeParser.JoinClauseContext> joins = new ArrayList<>();
        collectJoins(clause.tableExpression(), joins);
        for (final FrostlakeParser.JoinClauseContext join : joins) {
            if (outerJoin(join)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the statistics prove an IN's subject never equals the select's one item, which folds the membership to
     * FALSE before its correlation is judged: {@code hn.a IN (SELECT g.id FROM g WHERE hn.a < hn.c)} over a holding
     * 1 and 3 and ids 5 and 6. The subject is read over the outer relations and the item over the select's own.
     */
    private boolean subjectRuledOut(final FrostlakeParser.SelectClauseContext clause) {
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        if (inSubject == null || items.size() != 1 || !(items.get(0) instanceof FrostlakeParser.ExprItemContext)
                || clause.tableExpression() == null) {
            return false;
        }
        final Map<String, Table> own = new HashMap<>();
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            final Table table = source == null || source.tableQualifiedName() == null ? null
                : storedTable(source.tableQualifiedName());
            if (table == null || relation.pivotClause() != null || relation.unpivotClause() != null) {
                return false;   // the item may read a relation whose statistics are not read here
            }
            own.put(aliasOf(relation) != null ? aliasOf(relation) : lastNamePart(source.tableQualifiedName()), table);
        }
        final ValueRange subject = new CorrelatedFilterFold(executor, outerRelations).rangeOf(inSubject);
        final ValueRange item = new CorrelatedFilterFold(executor, own).rangeOf(ParseTreeText.getOriginalText(
            ((FrostlakeParser.ExprItemContext) items.get(0)).booleanExpr()));
        return subject != null && item != null && !subject.isEmpty() && !item.isEmpty()
            && !subject.isOpaqueToConditions() && !item.isOpaqueToConditions()
            && (subject.getMax().compareTo(item.getMin()) < 0 || item.getMax().compareTo(subject.getMin()) < 0);
    }

    private StoredColumnValues storedValues() {
        if (storedValues == null) {
            storedValues = new StoredColumnValues(executor);
        }
        return storedValues;
    }

    private boolean allOneSided(final List<FrostlakeParser.ExpressionContext> operands,
                                final List<CorrelationScope> scopes) {
        for (final FrostlakeParser.ExpressionContext operand : operands) {
            if (readsDirectly(operand, CorrelationSide.OUTER, scopes)
                    && (readsDirectly(operand, CorrelationSide.INNER, scopes)
                        || !aggregateCalls(operand).isEmpty() && !aggregatesOuterAlone(operand, scopes))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether every aggregate of an operand reads the outer row alone, which makes it the outer query's own
     * aggregate — computed over its group and read here as a value, not a correlation to plan:
     * {@code g.id < MAX(fz.id)} in a grouped query's HAVING is answered, where {@code MAX(fz.id + g.v)}
     * mixes the two and is not (live-verified).
     */
    private boolean aggregatesOuterAlone(final FrostlakeParser.ExpressionContext operand,
                                         final List<CorrelationScope> scopes) {
        for (final ParserRuleContext call : aggregateCalls(operand)) {
            if (readsDirectly(call, CorrelationSide.INNER, scopes)
                    || !readsDirectly(call, CorrelationSide.OUTER, scopes)
                    || !outerNames.contains(canonicalPrintOf(call))) {
                return false;
            }
        }
        return true;
    }

    /** A call's canonical print, upper-cased as the names it is looked up among are; null where it cannot be read. */
    private String canonicalPrintOf(final ParserRuleContext call) {
        try {
            return AstPrinterVisitor.print(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(call))).toUpperCase();
        } catch (final RuntimeException unreadable) {
            return "";
        }
    }

    /** Whether {@code node} holds a reference resolving to {@code side}, outside any nested select. */
    private boolean readsDirectly(final ParseTree node, final CorrelationSide side,
                                  final List<CorrelationScope> scopes) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return sideOf((FrostlakeParser.QualifiedNameExprContext) node, scopes) == side;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsDirectly(node.getChild(i), side, scopes)) {
                return true;
            }
        }
        return false;
    }
}
