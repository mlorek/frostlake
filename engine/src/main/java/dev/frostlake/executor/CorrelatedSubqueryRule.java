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
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.values.ValueRange;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 *   <li><b>An aggregate over the outer row alone</b> in the select list of an EXISTS is the outer query's own
 *       value, not an aggregate of the EXISTS: its select counts rows as if a constant stood there, so
 *       {@code EXISTS (SELECT MAX(fz.id) FROM g WHERE g.id = fz.id)} is answered row by row and
 *       {@code EXISTS (SELECT MAX(fz.id) FROM g LIMIT 1)} reads no outer row at all. Where the value itself is
 *       read — under DISTINCT, or by a HAVING naming the item — the EXISTS is refused. An aggregate of an
 *       EXISTS's or an IN's own may not read the outer row inside its argument ({@code MAX(fz.id + g.v)}), unless
 *       the select groups by a GROUP BY or a HAVING: under EXISTS nothing reads such an item, and a constant
 *       stands in its place as well.</li>
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
    /** Names bound to one value for the whole statement (see {@link #withFixedNames}). */
    private Set<String> fixedNames = Collections.emptySet();

    /** Whether the outer and the fixed names are a SQL function's parameters, which win over a body's own columns. */
    private boolean parametersWin;
    private CorrelatedFilterFold filterFold;
    private boolean sawOuter;
    /** The subquery being judged, and the deepest level it reads the outer row from (see {@link #outerReadDepth}). */
    private FrostlakeParser.SelectStatementContext walked;
    private int deepestOuterRead;
    private boolean sawUnknown;
    /** Whether a shape is being judged, so a derived column computed from the outer row reads it. */
    private boolean judging;
    /** Whether the value subquery judged last is refused for its own aggregate over the outer row and its columns. */
    private boolean refusedForItsAggregate;
    /** While set, every outer name counts as read where it is written (see {@link #neverReached}). */
    private boolean readsAsWritten;
    /** While set, a settled condition is folded inside an aggregate's argument too (see {@link #statisticsBodyText}). */
    private boolean foldingInAggregates;
    /**
     * Whether a rule is being judged that reads an outer name only where the statistics show it varying (see
     * {@link #readsVaryingOuter}), so a name they say nothing of correlates nothing there.
     */
    private boolean varyingOnly;
    /** While set, a bare name among relations whose columns are not known is placed nowhere (see {@link #reach}). */
    private boolean certainOnly;
    /** Whether the membership judged is a positive IN a semi-join answers (see {@link #refusesMembership}). */
    private boolean semiJoin;
    /** Whether the membership judged is a NOT IN (see {@link #refusesMembership}). */
    private boolean negatedMembership;
    /** The judged IN's left operand as written, or null where it is not known or the membership is an EXISTS. */
    private String inSubject;
    /**
     * The EXISTS body being judged: an aggregate of its select lists over the outer row alone reads as a constant
     * (see {@link #countedAggregates}), as one in the body of an EXISTS nested in the subquery does.
     */
    private FrostlakeParser.SelectStatementContext countedBody;
    /** The aggregate calls of {@link #countedBody} over the outer row alone, which are no aggregates of its own. */
    private Set<ParserRuleContext> countedCalls = Collections.emptySet();
    private StoredColumnValues storedValues;
    /** The select-list aggregates of the subquery being judged that the outer table's statistics answer. */
    private Set<ParserRuleContext> statisticsCalls = Collections.emptySet();

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

    /**
     * Names bound to one value for the whole statement: the parameters of a SQL function's body that its call passed
     * no row's value. Such a name correlates nothing and is placed, so the body stays judgeable; unlike an outer name
     * the statistics pin, it is never read as its value, so a condition over it stays a condition. A body {@code
     * SELECT a + b WHERE a > 0} called as {@code nd(1, o.k)} is refused, as it is called as {@code nd(o.k, 1)}
     * (live-verified).
     *
     * @param names the names, upper-cased
     * @return this rule
     */
    public CorrelatedSubqueryRule withFixedNames(final Set<String> names) {
        this.fixedNames = names;
        return this;
    }

    /**
     * Judge a SQL function's body, whose outer and fixed names are its parameters: a bare name a parameter carries
     * reads the parameter, never a same-named column of the body's own relations, as the call binds it. So
     * {@code SUM(value)} over a FLATTEN reads the parameter {@code value}, and {@code MAX(id)} over a table with a
     * column ID reads the parameter {@code id} (live-verified).
     *
     * @return this rule
     */
    public CorrelatedSubqueryRule withParametersWinning() {
        this.parametersWin = true;
        return this;
    }

    /**
     * Whether the value subquery judged last is refused for an aggregate of its own that reads the outer row beside
     * the subquery's own columns — {@code (SELECT MAX(fz.id + g.v) FROM g)}. The account refuses that shape in an
     * earlier pass than every other one, which decides which of several refusals a statement reports (see
     * CorrelationPlanJudgement.refusal).
     *
     * @return true for that shape
     */
    public boolean refusedForItsAggregate() {
        return refusedForItsAggregate;
    }

    /** Whether live refuses {@code subquery} read as a value. */
    public boolean refusesScalar(final FrostlakeParser.SelectStatementContext subquery) {
        refusedForItsAggregate = false;
        final boolean judgingBefore = judging;
        final boolean foldingBefore = foldingInAggregates;
        final Set<ParserRuleContext> statisticsBefore = statisticsCalls;
        judging = true;
        // A value's subquery runs with each settled condition of an aggregate's argument written as its value.
        foldingInAggregates = true;
        statisticsCalls = Collections.emptySet();
        try {
            statisticsCalls = new HashSet<>(statisticsAggregates(subquery));
            if (refusesDerivedOuterRead(subquery)) {
                return true;
            }
            // A derived table's outer aggregate is a value the subquery runs with (see derivedOuterAggregates).
            statisticsCalls.addAll(derivedOuterCalls(subquery));
            return judgesScalar(subquery);
        } finally {
            judging = judgingBefore;
            foldingInAggregates = foldingBefore;
            statisticsCalls = statisticsBefore;
        }
    }

    private boolean judgesScalar(final FrostlakeParser.SelectStatementContext subquery) {
        if (!judgeableWithDepth(subquery)) {
            return false;
        }
        if (deepestOuterRead > 1) {
            // A query inside the subquery reads the outer row: no shape of that is turned.
            return true;
        }
        if (!singleColumn(subquery)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
        if (clause == null) {
            return true;
        }
        final FrostlakeParser.SelectStatementContext passed = passedOn(subquery, clause);
        if (passed != null) {
            return judgesScalar(passed);
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
            final boolean insideAggregate = readsOuterInsideAggregate(clause, subquery, scopes);
            refusedForItsAggregate = insideAggregate && mixesSidesInsideAggregate(clause.selectList(), scopes);
            if (readsOuterBesideAggregates(clause.selectList(), scopes)) {
                return insideAggregate || !answeredBesideAggregates(subquery, clause);
            }
            return limited(subquery) || clause.topClause() != null || insideAggregate
                || !filtersSupported(clause, scopes) || !derivedTablesSupported(clause, scopes);
        }
        if (clause.tableExpression() != null && !readsOneRowRelationsOnly(clause)) {
            // A filter the relations' statistics settle leaves no correlation to plan (see CorrelatedFilterFold):
            // settled FALSE it reads no row and the value is NULL — tt.id = fz.id over key ranges that never
            // meet, through a join or an ORDER BY too — and settled TRUE the subquery runs as if uncorrelated,
            // each only with the outer row read nowhere else: (SELECT v + fz.id FROM g WHERE FALSE) is refused
            // as it is with no filter (live-verified).
            if (clause.whereClause() == null) {
                return true;
            }
            return settlement(clause.whereClause().booleanExpr()) == null
                || readsOuterBeyond(subquery, clause, scopes, clause.whereClause());
        }
        return clause.whereClause() != null || clause.havingClause() != null || clause.qualifyClause() != null
            || ordered(subquery) || !limitKeepsTheRow(subquery, clause);
    }

    /**
     * Whether a select's FROM holds nothing but derived tables or common table expressions of exactly one row —
     * FROM-less selects with no filter, grouping or LIMIT, or selects over such tables — so it reads one row as a
     * FROM-less select does: {@code (SELECT x FROM (SELECT fz.id + 1 AS x))} is answered, and so is a SQL function's
     * {@code WITH d AS (SELECT p AS v) SELECT v FROM d} called over a column (live-verified).
     */
    private boolean readsOneRowRelationsOnly(final FrostlakeParser.SelectClauseContext clause) {
        return readsOneRowRelationsOnly(clause, new HashSet<FrostlakeParser.CteDefinitionContext>());
    }

    private boolean readsOneRowRelationsOnly(final FrostlakeParser.SelectClauseContext clause,
                                                    final Set<FrostlakeParser.CteDefinitionContext> entered) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || !from.joinClause().isEmpty()) {
            return false;
        }
        for (final FrostlakeParser.TableReferenceContext relation : from.tableReference()) {
            // A common table expression is read as one row only where it is the FROM's one relation: live refuses
            // WITH d AS (SELECT p AS v), e AS (SELECT 'k' AS k) SELECT v || k FROM d, e (live-verified).
            if (from.tableReference().size() > 1 && relation.tableSource() != null
                    && relation.tableSource().selectStatement() == null) {
                return false;
            }
            final FrostlakeParser.SelectStatementContext query = relationQuery(relation, entered);
            if (query == null || relation.LATERAL() != null
                    || relation.pivotClause() != null || relation.unpivotClause() != null || relation.matchRecognizeClause() != null
                    || relation.sampleClause() != null || limited(query)) {
                return false;
            }
            final FrostlakeParser.SelectClauseContext body = mainClause(query);
            if (body == null || body.whereClause() != null || body.groupByClause() != null
                    || body.havingClause() != null || body.qualifyClause() != null || body.topClause() != null
                    || body.tableExpression() != null && !readsOneRowRelationsOnly(body, entered)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The query a FROM relation reads: a derived table's, or the body of the common table expression its name reads —
     * once, so a definition that reads itself is no query of one row. A subquery read from its own text finds the
     * statement's CTE of the name in the scope it runs in (see CteDefinitions). Null for a table, a view or any other
     * source.
     */
    private FrostlakeParser.SelectStatementContext relationQuery(
            final FrostlakeParser.TableReferenceContext relation,
            final Set<FrostlakeParser.CteDefinitionContext> entered) {
        final FrostlakeParser.TableSourceContext source = relation.tableSource();
        if (source == null) {
            return null;
        }
        if (source.selectStatement() != null) {
            return source.selectStatement();
        }
        FrostlakeParser.CteDefinitionContext cte = CommonTableExpressions.named(source);
        if (cte == null && source.tableQualifiedName() != null && source.timeTravelClause() == null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
            final Map<String, ResultSet> inScope = executor.getCurrentCteContext();
            cte = parts.length != 1 || inScope == null ? null : CteDefinitions.of(inScope.get(parts[0]));
        }
        return cte == null || !entered.add(cte) ? null : cte.selectStatement();
    }

    /**
     * The query a subquery merely passes on: one select over a single derived table or common table expression that
     * adds nothing of its own — no filter, grouping, ordering, limit or DISTINCT, and items that read the outer row,
     * aggregate, window or query nothing — answers from that query's rows, and is judged as it. {@code WITH d AS
     * (SELECT MAX(w) AS m FROM t WHERE t.id = p) SELECT m + 1 FROM d} is the aggregate it reads (live-verified). Null
     * for any other subquery.
     */
    private FrostlakeParser.SelectStatementContext passedOn(final FrostlakeParser.SelectStatementContext subquery,
                                                            final FrostlakeParser.SelectClauseContext clause) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || !from.joinClause().isEmpty() || from.tableReference().size() != 1
                || clause.whereClause() != null || clause.groupByClause() != null || clause.havingClause() != null
                || clause.qualifyClause() != null || clause.topClause() != null || clause.DISTINCT() != null
                || limited(subquery) || ordered(subquery)) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext relation = from.tableReference(0);
        if (relation.LATERAL() != null || relation.pivotClause() != null || relation.unpivotClause() != null
                || relation.matchRecognizeClause() != null || relation.sampleClause() != null) {
            return null;
        }
        if (!aggregateCalls(clause.selectList()).isEmpty() || holdsWindowCall(clause.selectList())
                || holdsQuery(clause.selectList())
                || readsOuterBesideAggregates(clause.selectList(), scopeOfAlone(clause))) {
            return null;
        }
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (SelectItemAccessors.getItemExpression(item) == null) {
                return null;
            }
        }
        return relationQuery(relation, new HashSet<FrostlakeParser.CteDefinitionContext>());
    }

    /** Whether a part of a select holds a query of its own. */
    private static boolean holdsQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsQuery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the statistics prove a subquery reads no row: its one select's relation is empty, its WHERE is false,
     * or its LIMIT keeps nothing.
     *
     * @param subquery the subquery
     * @return whether it answers no row
     */
    public boolean provesNoRow(final FrostlakeParser.SelectStatementContext subquery) {
        final boolean judgingBefore = judging;
        judging = true;
        try {
            final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
            return clause != null && (prunedAway(clause) || innerRelationEmpty(clause)
                || limitedToNoRow(subquery, clause));
        } finally {
            judging = judgingBefore;
        }
    }

    /**
     * Whether a subquery reads nothing of the row around it: every name it reads resolves inside it.
     *
     * @param subquery the subquery
     * @return whether it reads no outer name
     */
    public boolean readsNoOuterName(final FrostlakeParser.SelectStatementContext subquery) {
        final boolean judgingBefore = judging;
        judging = true;
        try {
            judgeable(subquery);
            return !sawOuter && !sawUnknown;
        } finally {
            judging = judgingBefore;
        }
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
        return refusesMembership(subquery, in, semiJoin, subject, false);
    }

    /**
     * {@link #refusesMembership(FrostlakeParser.SelectStatementContext, boolean, boolean, String)} for a membership
     * that may be a NOT IN, which an aggregate body filtered otherwise than by equality does not refuse:
     * {@code id NOT IN (SELECT MAX(g.id) FROM g WHERE g.id > fz.id)} in a select list is answered where the IN is
     * refused (live-verified).
     *
     * @param negated whether the membership is a NOT IN
     */
    public boolean refusesMembership(final FrostlakeParser.SelectStatementContext subquery, final boolean in,
                                     final boolean semiJoin, final String subject, final boolean negated) {
        final boolean judgingBefore = judging;
        final boolean negatedBefore = negatedMembership;
        final boolean foldingBefore = foldingInAggregates;
        // An IN's subquery folds a settled condition of an aggregate's argument as a value's does (live-verified).
        foldingInAggregates = in;
        negatedMembership = in && negated;
        final boolean semiJoinBefore = this.semiJoin;
        final String subjectBefore = inSubject;
        final FrostlakeParser.SelectStatementContext countedBefore = countedBody;
        final Set<ParserRuleContext> callsBefore = countedCalls;
        judging = true;
        this.semiJoin = in && semiJoin;
        inSubject = in ? subject : null;
        countedBody = in ? null : subquery;
        countedCalls = Collections.emptySet();
        try {
            if (!in) {
                if (readsCountedValue(subquery) || refusesDerivedOuterRead(subquery)) {
                    return true;
                }
                countedCalls = new HashSet<>(countedAggregates(subquery));
            }
            return judgeableWithDepth(subquery)
                && (deepestOuterRead > 1 || refusesMembershipStatement(subquery, in));
        } finally {
            judging = judgingBefore;
            foldingInAggregates = foldingBefore;
            negatedMembership = negatedBefore;
            this.semiJoin = semiJoinBefore;
            inSubject = subjectBefore;
            countedBody = countedBefore;
            countedCalls = callsBefore;
        }
    }

    // ── an outer aggregate the statistics answer ─────────────────────────────────────

    /**
     * The aggregates of a scalar subquery's select list that the OUTER table's statistics answer: MIN, MAX or
     * COUNT of one bare column of a table the outer query reads. Live answers such a call from the whole table —
     * not the outer query's group, and not the rows its WHERE keeps — where the subquery reads one table of its
     * own with no filter, grouping or ordering and writes an aggregate of its own first: over h holding 1 … 3,
     * {@code (SELECT MAX(g.v) + MIN(h.id) FROM g)} adds 1 on every row, also beside {@code WHERE id > 1}, while
     * SUM, AVG, DISTINCT, an expression argument, a view or a derived table as the outer relation, a filter in
     * the subquery and a call that stands first are all refused (live-verified).
     *
     * @param subquery the scalar subquery
     * @return the calls, in written order; empty where the shape is not one the statistics answer
     */
    public List<ParserRuleContext> statisticsAggregates(final FrostlakeParser.SelectStatementContext subquery) {
        final List<ParserRuleContext> none = new ArrayList<>();
        final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
        if (clause == null || clause.whereClause() != null || clause.groupByClause() != null
                || clause.havingClause() != null || clause.qualifyClause() != null
                || clause.connectByClause() != null || ordered(subquery)) {
            return none;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || !from.joinClause().isEmpty() || from.tableReference().size() != 1) {
            return none;
        }
        final FrostlakeParser.TableSourceContext source = from.tableReference(0).tableSource();
        if (source == null || source.tableQualifiedName() == null || storedTable(source.tableQualifiedName()) == null) {
            return none;
        }
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        final List<ParserRuleContext> calls = aggregateCalls(clause.selectList());
        final List<ParserRuleContext> answered = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            final ParserRuleContext call = calls.get(i);
            if (!readsOuterRowAlone(call, scopes)) {
                continue;
            }
            // The subquery's own aggregate comes first, and every outer one is a statistic.
            if (i == 0 || statisticOf(call) == null) {
                return none;
            }
            answered.add(call);
        }
        return answered;
    }

    /**
     * A scalar subquery as it is run: each aggregate the outer table's statistics answer (see
     * {@link #statisticsAggregates}) replaced by its value, typed as the call is, and each outer aggregate of a derived
     * table as {@link #derivedOuterAggregates} writes it; one read where it is no statistic is refused here.
     *
     * @param subquery the scalar subquery
     * @return the text to run, or null where the subquery holds no such call
     */
    public String statisticsBodyText(final FrostlakeParser.SelectStatementContext subquery) {
        final String nesting = derivedOuterNesting(subquery);
        if (nesting != null) {
            throw new RuntimeException(nesting);
        }
        final boolean judgingBefore = judging;
        final boolean foldingBefore = foldingInAggregates;
        judging = true;
        foldingInAggregates = true;
        final List<ParserRuleContext> calls;
        final Map<ParserRuleContext, String> derived = derivedOuterAggregates(subquery);
        final Map<ParserRuleContext, String> written = new LinkedHashMap<>();
        try {
            calls = statisticsAggregates(subquery);
            // The statistics settle a condition over the relations the subquery reads, which a walk of it names.
            judgeableWithDepth(subquery);
            filterFold = null;
            final Set<ParserRuleContext> answered = new HashSet<>(calls);
            answered.addAll(derived.keySet());
            collectSettledInAggregates(subquery, false, answered, written);
        } finally {
            judging = judgingBefore;
            foldingInAggregates = foldingBefore;
        }
        if (calls.isEmpty() && written.isEmpty() && derived.isEmpty() || subquery.getStart() == null
                || subquery.getStart().getInputStream() == null) {
            return null;
        }
        for (final ParserRuleContext call : calls) {
            final String value = statisticOf(call);
            if (value == null) {
                return null;
            }
            written.put(call, value);
        }
        written.putAll(derived);
        return rewritten(subquery, written);
    }

    /**
     * An IN's subquery as it is run: each condition the statistics settle inside an aggregate's argument written as
     * its value, as a value's subquery writes it (see {@link #statisticsBodyText}); the aggregate computes over the
     * subquery's own rows, which do not carry the outer row.
     *
     * @param subquery the IN's subquery
     * @return the text to run, or null where nothing in it is settled
     */
    public String settledBodyText(final FrostlakeParser.SelectStatementContext subquery) {
        final boolean judgingBefore = judging;
        final boolean foldingBefore = foldingInAggregates;
        judging = true;
        foldingInAggregates = true;
        final Map<ParserRuleContext, String> written = new LinkedHashMap<>();
        try {
            judgeableWithDepth(subquery);
            filterFold = null;
            collectSettledInAggregates(subquery, false, new HashSet<ParserRuleContext>(), written);
        } finally {
            judging = judgingBefore;
            foldingInAggregates = foldingBefore;
        }
        if (written.isEmpty() || subquery.getStart() == null || subquery.getStart().getInputStream() == null) {
            return null;
        }
        return rewritten(subquery, written);
    }

    /**
     * Collects what a value's subquery writes in place of each condition the statistics settle inside an
     * aggregate's argument: an IFF it decides is the branch it takes, and any other condition its value. An
     * aggregate the statistics answer whole is written elsewhere, and a nested query is left as it is.
     */
    private void collectSettledInAggregates(final ParseTree node, final boolean inAggregate,
                                            final Set<ParserRuleContext> answered,
                                            final Map<ParserRuleContext, String> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext && node.getParent() != null && inAggregate
                || answered.contains(node)) {
            return;
        }
        if (inAggregate && node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String taken = takenBranchOfIff((FrostlakeParser.FunctionCallExprContext) node);
            if (taken != null) {
                into.put((ParserRuleContext) node, taken);
                return;
            }
        }
        if (inAggregate && isCondition(node)) {
            final Boolean settled = settledBranchCondition((ParserRuleContext) node);
            if (settled != null) {
                into.put((ParserRuleContext) node, settled.booleanValue() ? "TRUE" : "FALSE");
                return;
            }
        }
        if (inAggregate && node instanceof FrostlakeParser.InListExprContext) {
            final String kept = keptList((FrostlakeParser.InListExprContext) node);
            if (kept != null) {
                into.put((ParserRuleContext) node, kept);
                return;
            }
        }
        final boolean within = inAggregate || isAggregateCall(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSettledInAggregates(node.getChild(i), within, answered, into);
        }
    }

    /**
     * An IN list as the body runs it once the elements its subject never equals are dropped, in parentheses: the
     * rest of the list, or the membership's value where nothing is left; null where none is dropped.
     */
    private String keptList(final FrostlakeParser.InListExprContext list) {
        if (list.expression() == null || list.expressionList() == null) {
            return null;
        }
        final List<String> kept = new ArrayList<>();
        boolean dropped = false;
        for (final FrostlakeParser.ExpressionContext element : list.expressionList().expression()) {
            if (neverListed(list, element)) {
                dropped = true;
            } else {
                kept.add(ParseTreeText.getOriginalText(element));
            }
        }
        if (!dropped) {
            return null;
        }
        final boolean negated = list.NOT() != null;
        if (kept.isEmpty()) {
            return negated ? "(TRUE)" : "(FALSE)";
        }
        return "(" + ParseTreeText.getOriginalText(list.expression()) + (negated ? " NOT IN (" : " IN (")
            + String.join(", ", kept) + "))";
    }

    /** The branch an IFF takes when the statistics settle its condition, in parentheses; null for any other call. */
    private String takenBranchOfIff(final FrostlakeParser.FunctionCallExprContext call) {
        if (!"IFF".equalsIgnoreCase(call.functionName().getText()) || call.functionArgList() == null
                || call.functionArgList().functionArg().size() != 3 || call.overClause() != null) {
            return null;
        }
        final List<FrostlakeParser.FunctionArgContext> arguments = call.functionArgList().functionArg();
        ParseTree condition = arguments.get(0);
        while (!isCondition(condition) && condition.getChildCount() == 1) {
            condition = condition.getChild(0);
        }
        final Boolean settled = isCondition(condition) ? settledBranchCondition((ParserRuleContext) condition) : null;
        return settled == null ? null
            : "(" + ParseTreeText.getOriginalText(arguments.get(settled.booleanValue() ? 1 : 2)) + ")";
    }

    /**
     * The value of one outer aggregate as the outer table's statistics give it, written as SQL of the call's own
     * type; null where the call is no MIN, MAX or COUNT of one bare column of a stored outer table. A view or a derived
     * table over one gives no statistic here: the subquery is refused (live-verified).
     */
    private String statisticOf(final ParserRuleContext call) {
        if (!(call instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext written = (FrostlakeParser.FunctionCallExprContext) call;
        final String name = written.functionName().getText().toUpperCase(Locale.ROOT);
        if (!"MIN".equals(name) && !"MAX".equals(name) && !"COUNT".equals(name) || !readsStoredTable(written)) {
            return null;
        }
        final DerivedColumnLineage lineage = statisticsColumn(written);
        if (lineage == null) {
            return null;
        }
        if ("COUNT".equals(name)) {
            // A COUNT is not read from the statistics: it counts the subquery's own rows, once each where the
            // outer value is there, so over a three-row table of its own it is 3 (live-verified).
            return "IFF(" + ParseTreeText.getOriginalText(statisticsArgument(written)) + " IS NULL, 0, COUNT(*))";
        }
        return extremeOf(name, lineage);
    }

    /**
     * The whole-table MIN or MAX of a stored column, as the statistics give it, written as SQL of the column's type;
     * null for a type they give no extreme of.
     */
    private String extremeOf(final String name, final DerivedColumnLineage lineage) {
        final Table stored = lineage.storedTable();
        final TableColumn declared = stored.getColumn(lineage.storedColumn());
        final DataType type = declared.getDataType();
        if (!(type instanceof NumericType || type instanceof StringType || type instanceof DateTimeType
                || type instanceof BooleanType)) {
            return null;
        }
        final int index = stored.getColumnIndex(declared.getName());
        Object extreme = null;
        for (final Row row : executor.statisticsRowsFor(stored.getQualifiedName())) {
            final Object value = index < row.size() ? row.getValue(index) : null;
            if (value == null) {
                continue;
            }
            if (extreme == null || ("MIN".equals(name) ? ValueComparisons.compareValues(value, extreme) < 0
                    : ValueComparisons.compareValues(value, extreme) > 0)) {
                extreme = value;
            }
        }
        final String literal;
        if (extreme == null) {
            literal = "NULL";
        } else if (extreme instanceof Boolean) {
            literal = ((Boolean) extreme).booleanValue() ? "TRUE" : "FALSE";
        } else if (extreme instanceof Number) {
            literal = extreme instanceof BigDecimal ? ((BigDecimal) extreme).toPlainString() : extreme.toString();
        } else if (SharedFunctionHelpers.temporalSqlLiteral(extreme) != null) {
            literal = SharedFunctionHelpers.temporalSqlLiteral(extreme);
        } else if (extreme instanceof String) {
            literal = SqlStringLiterals.encode((String) extreme);
        } else {
            return null;
        }
        return "CAST(" + literal + " AS " + SqlTypeNames.canonical(type) + ")";
    }

    /** A call's one argument where it is a bare column and the call takes no DISTINCT or WITHIN GROUP, else null. */
    private static FrostlakeParser.QualifiedNameExprContext statisticsArgument(
            final FrostlakeParser.FunctionCallExprContext written) {
        if (written.DISTINCT() != null || written.withinGroupClause() != null || written.functionArgList() == null
                || written.functionArgList().functionArg().size() != 1) {
            return null;
        }
        ParseTree argument = written.functionArgList().functionArg(0);
        while (!(argument instanceof FrostlakeParser.QualifiedNameExprContext) && argument.getChildCount() == 1) {
            argument = argument.getChild(0);
        }
        return argument instanceof FrostlakeParser.QualifiedNameExprContext
            ? (FrostlakeParser.QualifiedNameExprContext) argument : null;
    }

    /** The one bare-column argument of a DISTINCT call with no WITHIN GROUP, or null. */
    private static FrostlakeParser.QualifiedNameExprContext distinctColumnArgument(
            final FrostlakeParser.FunctionCallExprContext written) {
        if (written.DISTINCT() == null || written.withinGroupClause() != null || written.functionArgList() == null
                || written.functionArgList().functionArg().size() != 1) {
            return null;
        }
        ParseTree argument = written.functionArgList().functionArg(0);
        while (!(argument instanceof FrostlakeParser.QualifiedNameExprContext) && argument.getChildCount() == 1) {
            argument = argument.getChild(0);
        }
        return argument instanceof FrostlakeParser.QualifiedNameExprContext
            ? (FrostlakeParser.QualifiedNameExprContext) argument : null;
    }

    /**
     * The stored column a call's one bare-column argument reads: a stored outer table's own, or the one a view or a
     * derived table of the outer query passes through unchanged, whatever a WHERE on the way keeps (see
     * {@link DerivedColumnLineage}) — a view's whole MAX is its table's (live-verified). Null where it reads none.
     */
    private DerivedColumnLineage statisticsColumn(final FrostlakeParser.FunctionCallExprContext written) {
        final FrostlakeParser.QualifiedNameExprContext argument = statisticsArgument(written);
        final Table owner = argument == null ? null : argumentOwner(argument);
        if (owner == null || owner.residentSource() != null && owner.residentSource() != owner) {
            return null;
        }
        final List<String> parts = partsOf(argument.qualifiedName());
        final DerivedColumnLineage lineage = DerivedColumnLineage.stored(owner, parts.get(parts.size() - 1));
        if (lineage == null || lineage.isLiteral()) {
            return null;
        }
        final Table stored = lineage.storedTable();
        return stored.getQualifiedName() != null && executor.getStorageEngine().hasTable(stored.getQualifiedName())
            && stored.hasColumn(lineage.storedColumn()) ? lineage : null;
    }

    /** The outer relation a bare-column argument reads: the one its qualifier names, else the one carrying it. */
    private Table argumentOwner(final FrostlakeParser.QualifiedNameExprContext argument) {
        final List<String> parts = partsOf(argument.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        if (parts.size() > 1) {
            final Table named = outerRelations.get(parts.get(parts.size() - 2));
            return named != null && named.hasColumn(column) ? named : null;
        }
        Table owner = null;
        for (final Table relation : outerRelations.values()) {
            if (relation != null && relation.hasColumn(column)) {
                if (owner != null && owner != relation) {
                    return null;
                }
                owner = relation;
            }
        }
        return owner;
    }

    /** Whether a call's argument reads a stored outer table itself, no view or derived table over one. */
    private boolean readsStoredTable(final FrostlakeParser.FunctionCallExprContext written) {
        final FrostlakeParser.QualifiedNameExprContext argument = statisticsArgument(written);
        final Table owner = argument == null ? null : argumentOwner(argument);
        return owner != null && owner.residentSource() == owner;
    }

    /** Whether a reference is written inside an aggregate the statistics answer. */
    private boolean insideStatisticsCall(final ParseTree reference) {
        if (statisticsCalls.isEmpty()) {
            return false;
        }
        for (ParseTree at = reference.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectClauseContext) {
                return false;
            }
            if (statisticsCalls.contains(at)) {
                return true;
            }
        }
        return false;
    }

    // ── an outer aggregate in a derived table ─────────────────────────────────────────

    /** An item the select over its derived table reads inside an aggregate of its own. */
    private static final int READ_IN_AGGREGATE = 1;
    /** An item the select over its derived table reads anywhere else: a filter, a grouping, a value of its own. */
    private static final int READ_ELSEWHERE = 2;
    /** What {@link #walkDerivedOuter} records for a read the account refuses as an unsupported subquery. */
    private static final String UNSUPPORTED_READ = "";

    /**
     * The aggregates over the outer row alone that a derived table the subquery reads writes in its select list, each
     * with the text it runs as (live-verified). Such a call is the outer query's own value and no aggregate of the
     * derived table, whose rows are its FROM's as if a value stood there: {@code (SELECT COUNT(*) FROM (SELECT
     * MAX(fz.id) AS x FROM g))} counts g's rows. Where nothing reads the item, a constant as wide as the call stands in
     * its place. Where the select over the derived table reads the item inside its own aggregates alone — with no WHERE
     * but a constant one, no grouping, no HAVING and no other relation — and the derived table's WHERE is none or reads
     * the outer row by no equality, a MIN or a MAX of a number or a date column of a stored outer table is that
     * column's extreme over the whole table, the outer query's WHERE, grouping and joins ignored, and a COUNT of one
     * its count there: over fz holding 5 and 7, {@code (SELECT SUM(x) FROM (SELECT MAX(fz.id) AS x FROM g))} is 14 on
     * every row. Any other read is refused (see {@link #refusesDerivedOuterRead} and {@link #derivedOuterNesting}).
     *
     * @param subquery the subquery
     * @return each call and the text it runs as, in written order; empty where there is none, or where one is refused
     */
    public Map<ParserRuleContext, String> derivedOuterAggregates(
            final FrostlakeParser.SelectStatementContext subquery) {
        final Map<ParserRuleContext, String> texts = new LinkedHashMap<>();
        final List<String> refusals = new ArrayList<>();
        walkDerivedOuter(subquery, texts, refusals, new ArrayList<ParserRuleContext>());
        return refusals.isEmpty() ? texts : new LinkedHashMap<ParserRuleContext, String>();
    }

    /**
     * Whether the select over a derived table reads an outer aggregate of the derived table's outside any aggregate of
     * its own alone — in its WHERE, as a value of its select list — which the account refuses as an unsupported
     * subquery: {@code (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g) WHERE x = 7)} (live-verified).
     *
     * @param subquery the subquery
     * @return true for such a read, the first refusal the subquery meets
     */
    public boolean refusesDerivedOuterRead(final FrostlakeParser.SelectStatementContext subquery) {
        final List<String> refusals = new ArrayList<>();
        walkDerivedOuter(subquery, new LinkedHashMap<ParserRuleContext, String>(), refusals,
            new ArrayList<ParserRuleContext>());
        return !refusals.isEmpty() && UNSUPPORTED_READ.equals(refusals.get(0));
    }

    /**
     * The refusal of an outer aggregate of a derived table read where its value is not the outer table's: any but a
     * MIN, a MAX or a COUNT, one of a text, and any read beside a filter, a grouping or another relation — {@code
     * Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in [SUM(CORRELATION(CAST(MAX(FZ.ID) AS
     * NUMBER(38,0))))]}, the call and the aggregate reading it as the account's plan prints them (live-verified).
     *
     * @param subquery the subquery
     * @return the refusal's text, or null where the subquery meets none first
     */
    public String derivedOuterNesting(final FrostlakeParser.SelectStatementContext subquery) {
        final List<String> refusals = new ArrayList<>();
        walkDerivedOuter(subquery, new LinkedHashMap<ParserRuleContext, String>(), refusals,
            new ArrayList<ParserRuleContext>());
        return refusals.isEmpty() || UNSUPPORTED_READ.equals(refusals.get(0)) ? null : refusals.get(0);
    }

    /**
     * Every outer aggregate a derived table of the subquery writes in its select list, answered or refused: the calls
     * a judgement reads as values (see {@link #derivedOuterAggregates}).
     *
     * @param subquery the subquery
     * @return the calls, in written order
     */
    public List<ParserRuleContext> derivedOuterCalls(final FrostlakeParser.SelectStatementContext subquery) {
        final List<ParserRuleContext> calls = new ArrayList<>();
        walkDerivedOuter(subquery, new LinkedHashMap<ParserRuleContext, String>(), new ArrayList<String>(), calls);
        return calls;
    }

    /**
     * The outer aggregates of derived tables whose items nothing reads: no value the subquery uses, so no aggregate a
     * placement judges — {@code WHERE (SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g)) = 2} is answered
     * (live-verified).
     *
     * @param subquery the subquery
     * @return the calls
     */
    public Set<ParserRuleContext> unreadDerivedOuterCalls(final FrostlakeParser.SelectStatementContext subquery) {
        final Map<ParserRuleContext, String> texts = new LinkedHashMap<>();
        walkDerivedOuter(subquery, texts, new ArrayList<String>(), new ArrayList<ParserRuleContext>());
        final Set<ParserRuleContext> unread = new HashSet<>();
        for (final Map.Entry<ParserRuleContext, String> text : texts.entrySet()) {
            if (text.getValue().equals(asWideConstant(text.getKey()))) {
                unread.add(text.getKey());
            }
        }
        return unread;
    }

    /**
     * A subquery as it is compiled: each outer aggregate of a derived table written as {@link #derivedOuterAggregates}
     * runs it, so the derived table is planned as the account plans it, no aggregate of its own.
     *
     * @param subquery the subquery
     * @return the text to compile, or null where it holds no such call or one is refused
     */
    public String derivedBodyText(final FrostlakeParser.SelectStatementContext subquery) {
        final Map<ParserRuleContext, String> derived = derivedOuterAggregates(subquery);
        if (derived.isEmpty() || subquery.getStart() == null || subquery.getStart().getInputStream() == null) {
            return null;
        }
        return rewritten(subquery, derived);
    }

    /** Judges each outer aggregate of a derived table the subquery reads, through derived tables nested in those. */
    private void walkDerivedOuter(final FrostlakeParser.SelectStatementContext subquery,
                                  final Map<ParserRuleContext, String> texts, final List<String> refusals,
                                  final List<ParserRuleContext> calls) {
        final FrostlakeParser.SelectClauseContext top = mainClause(subquery);
        if (top == null) {
            return;
        }
        final boolean judgingBefore = judging;
        judging = false;
        try {
            final List<FrostlakeParser.SelectClauseContext> enclosing = new ArrayList<>();
            final List<FrostlakeParser.SelectStatementContext> statements = new ArrayList<>();
            enclosing.add(top);
            statements.add(subquery);
            walkDerivedTables(enclosing, statements, new ArrayList<String>(), texts, refusals, calls,
                !(subquery.getParent() instanceof FrostlakeParser.ExistsExprContext));
        } finally {
            judging = judgingBefore;
        }
    }

    /**
     * Judges the derived tables of the last select of {@code enclosing}: {@code statements} holds the statement each
     * select stands in, and {@code aliases} the name each nested one has in the FROM of the select before it.
     */
    private void walkDerivedTables(final List<FrostlakeParser.SelectClauseContext> enclosing,
                                   final List<FrostlakeParser.SelectStatementContext> statements,
                                   final List<String> aliases, final Map<ParserRuleContext, String> texts,
                                   final List<String> refusals, final List<ParserRuleContext> calls,
                                   final boolean valueRead) {
        final FrostlakeParser.SelectClauseContext select = enclosing.get(enclosing.size() - 1);
        if (select.tableExpression() == null) {
            return;
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(select.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            if (source == null || source.selectStatement() == null || relation.LATERAL() != null) {
                continue;
            }
            final String alias = aliasOf(relation);
            final FrostlakeParser.SelectClauseContext derived = mainClause(source.selectStatement());
            final List<FrostlakeParser.SelectClauseContext> arms = new ArrayList<>();
            collectArms(source.selectStatement(), arms);
            if (arms.isEmpty()) {
                continue;
            }
            final List<String> names = derivedItemNames(relation, arms.get(0));
            for (final FrostlakeParser.SelectClauseContext arm : arms) {
                final List<CorrelationScope> scopes = scopeOfAlone(arm);
                final List<FrostlakeParser.SelectItemContext> items = arm.selectList().selectItem();
                for (int i = 0; i < items.size(); i++) {
                    final List<ParserRuleContext> outer = new ArrayList<>();
                    for (final ParserRuleContext call : aggregateCallsIn(items.get(i))) {
                        if (readsOuterRowAlone(call, scopes)) {
                            outer.add(call);
                        }
                    }
                    if (outer.isEmpty()) {
                        continue;
                    }
                    calls.addAll(outer);
                    final String name = i < names.size() ? names.get(i) : null;
                    final int reads = itemReads(enclosing, statements, aliases, enclosing.size() - 1, alias, name,
                        valueRead);
                    judgeDerivedOuter(outer, reads, select, derived, arm, scopes, alias, name, texts, refusals);
                }
            }
            if (derived != null) {
                enclosing.add(derived);
                statements.add(source.selectStatement());
                aliases.add(alias);
                walkDerivedTables(enclosing, statements, aliases, texts, refusals, calls, valueRead);
                enclosing.remove(enclosing.size() - 1);
                statements.remove(statements.size() - 1);
                aliases.remove(aliases.size() - 1);
            }
        }
    }

    /** Records what one derived item's outer aggregates run as, or the refusal they meet (see the callers). */
    private void judgeDerivedOuter(final List<ParserRuleContext> outer, final int reads,
                                   final FrostlakeParser.SelectClauseContext reader,
                                   final FrostlakeParser.SelectClauseContext derived,
                                   final FrostlakeParser.SelectClauseContext arm,
                                   final List<CorrelationScope> scopes, final String alias, final String name,
                                   final Map<ParserRuleContext, String> texts, final List<String> refusals) {
        if (reads == 0) {
            for (final ParserRuleContext call : outer) {
                texts.put(call, asWideConstant(call));
            }
            return;
        }
        if ((reads & READ_IN_AGGREGATE) == 0 || derived == null) {
            refusals.add(UNSUPPORTED_READ);
            return;
        }
        final boolean plain = (reads & READ_ELSEWHERE) == 0 && plainReader(reader) && whereKeepsStatistics(arm, scopes);
        for (final ParserRuleContext call : outer) {
            final String statistic = plain ? derivedStatistic(call) : null;
            if (statistic == null) {
                refusals.add(nestedRefusal(call, readingAggregate(reader, alias, name)));
                return;
            }
            texts.put(call, statistic);
        }
    }

    /** A derived table's column names, in order: its column list's, else each item's alias or bare column name. */
    private List<String> derivedItemNames(final FrostlakeParser.TableReferenceContext relation,
                                          final FrostlakeParser.SelectClauseContext first) {
        final List<String> names = new ArrayList<>();
        if (relation.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext name : relation.identifierList().identifier()) {
                names.add(ParseTreeText.getIdentifier(name).toUpperCase(Locale.ROOT));
            }
            return names;
        }
        for (final FrostlakeParser.SelectItemContext item : first.selectList().selectItem()) {
            names.add(itemName(item));
        }
        return names;
    }

    /** A select item's name as a reference reaches it: its alias or its bare column's name; null for any other. */
    private static String itemName(final FrostlakeParser.SelectItemContext item) {
        if (!(item instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final FrostlakeParser.ExprItemContext exprItem = (FrostlakeParser.ExprItemContext) item;
        if (exprItem.aliasName() != null) {
            return ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase(Locale.ROOT);
        }
        if (exprItem.identifier() != null) {
            return ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase(Locale.ROOT);
        }
        if (exprItem.bareAliasWord() != null) {
            return ParseTreeText.getIdentifier(exprItem.bareAliasWord());
        }
        if (exprItem.booleanExpr() instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) exprItem.booleanExpr()).expression()
                    instanceof FrostlakeParser.QualifiedNameExprContext) {
            final List<String> parts = partsOf(((FrostlakeParser.QualifiedNameExprContext)
                ((FrostlakeParser.ValueExprContext) exprItem.booleanExpr()).expression()).qualifiedName());
            return parts.get(parts.size() - 1);
        }
        return null;
    }

    /**
     * How the selects above a derived table read one of its items (the READ_ bits; 0 where nothing does). An item a
     * select passes on whole as an item of its own is read as that item is; the subquery's own select list is read as
     * a value except under EXISTS.
     */
    private int itemReads(final List<FrostlakeParser.SelectClauseContext> enclosing,
                          final List<FrostlakeParser.SelectStatementContext> statements, final List<String> aliases,
                          final int level, final String alias, final String name, final boolean valueRead) {
        if (name == null) {
            return 0;
        }
        final FrostlakeParser.SelectClauseContext select = enclosing.get(level);
        int reads = 0;
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                reads |= READ_ELSEWHERE;
            }
        }
        final List<FrostlakeParser.QualifiedNameExprContext> references = new ArrayList<>();
        collectReferencesOver(select, references);
        if (statements.get(level).orderByClause() != null) {
            collectReferencesOver(statements.get(level).orderByClause(), references);
        }
        for (final FrostlakeParser.QualifiedNameExprContext reference : references) {
            final List<String> parts = partsOf(reference.qualifiedName());
            if (!parts.get(parts.size() - 1).equals(name) || parts.size() > 2
                    || parts.size() == 2 && !parts.get(0).equals(alias)) {
                continue;
            }
            final FrostlakeParser.ExprItemContext whole = wholeItem(reference, select);
            if (whole != null) {
                if (level > 0) {
                    reads |= itemReads(enclosing, statements, aliases, level - 1, aliases.get(level - 1),
                        itemName(whole), valueRead);
                } else if (valueRead) {
                    reads |= READ_ELSEWHERE;
                }
                continue;
            }
            reads |= insideAggregateOf(reference, select) ? READ_IN_AGGREGATE : READ_ELSEWHERE;
        }
        return reads;
    }

    /** The references a select makes, its derived tables' bodies left out: the ones above them read their items. */
    private static void collectReferencesOver(final ParseTree node,
                                              final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).selectStatement() != null) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectReferencesOver(node.getChild(i), into);
        }
    }

    /** The select item a reference is the whole value of, or null. */
    private static FrostlakeParser.ExprItemContext wholeItem(final FrostlakeParser.QualifiedNameExprContext reference,
                                                            final FrostlakeParser.SelectClauseContext select) {
        final ParseTree value = reference.getParent();
        if (value instanceof FrostlakeParser.ValueExprContext
                && value.getParent() instanceof FrostlakeParser.ExprItemContext
                && value.getParent().getParent() == select.selectList()) {
            return (FrostlakeParser.ExprItemContext) value.getParent();
        }
        return null;
    }

    /** Whether a reference stands inside an aggregate call of the select it is read by. */
    private boolean insideAggregateOf(final ParseTree reference, final FrostlakeParser.SelectClauseContext select) {
        for (ParseTree at = reference.getParent(); at != null && at != select; at = at.getParent()) {
            if (isAggregateCall(at)) {
                return true;
            }
        }
        return false;
    }

    /** The aggregate calls in a select item, outside the queries nested in it. */
    private List<ParserRuleContext> aggregateCallsIn(final ParseTree node) {
        final List<ParserRuleContext> calls = new ArrayList<>();
        collectEveryAggregateCall(node, calls);
        return calls;
    }

    private void collectEveryAggregateCall(final ParseTree node, final List<ParserRuleContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (isAggregateCall(node)) {
            into.add((ParserRuleContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectEveryAggregateCall(node.getChild(i), into);
        }
    }

    /**
     * Whether a select reading a derived item inside its aggregates reads it as the outer table's value: no WHERE but
     * one reading no name, no GROUP BY, HAVING or QUALIFY, and one relation.
     */
    private boolean plainReader(final FrostlakeParser.SelectClauseContext select) {
        if (select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null
                || select.tableExpression() == null || !select.tableExpression().joinClause().isEmpty()) {
            return false;
        }
        if (select.whereClause() != null) {
            final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
            collectNamesOutsideQueries(select.whereClause(), names);
            if (!names.isEmpty() || holdsNestedQueryOrLambda(select.whereClause())) {
                return false;
            }
        }
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(select.tableExpression(), relations);
        return relations.size() == 1;
    }

    /** Whether a derived table's WHERE keeps its outer aggregates the outer table's: none, or one reading the outer
     *  row by no equality. */
    private boolean whereKeepsStatistics(final FrostlakeParser.SelectClauseContext derived,
                                         final List<CorrelationScope> scopes) {
        if (derived.whereClause() == null) {
            return true;
        }
        if (!readsDirectly(derived.whereClause(), CorrelationSide.OUTER, scopes)) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        conjunctsOf(derived.whereClause().booleanExpr(), conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (conjunct instanceof FrostlakeParser.ValueExprContext
                    && ((FrostlakeParser.ValueExprContext) conjunct).expression()
                        instanceof FrostlakeParser.ComparisonExprContext) {
                final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext)
                    ((FrostlakeParser.ValueExprContext) conjunct).expression();
                if (comparison.op.getType() == FrostlakeParser.EQ
                        && readsDirectly(comparison, CorrelationSide.OUTER, scopes)
                        && readsDirectly(comparison, CorrelationSide.INNER, scopes)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A constant as wide as a call's text, its line breaks kept, so every place after it keeps its position. */
    private static String asWideConstant(final ParserRuleContext call) {
        final String written = ParseTreeText.getOriginalText(call);
        final StringBuilder constant = new StringBuilder("1");
        for (int i = 1; i < written.length(); i++) {
            final char at = written.charAt(i);
            constant.append(at == '\n' || at == '\r' ? at : ' ');
        }
        return constant.toString();
    }

    /**
     * An outer aggregate of a derived table read as the outer table's value, written as SQL of its type: a MIN or a
     * MAX of a number or a date column of a stored outer table its extreme over the whole table, a COUNT of one the
     * derived count (see {@link #derivedCount}); null for any other call, and for a COUNT where the derived table
     * filters its rows or reads a join, which the account refuses as nested (live-verified).
     */
    private String derivedStatistic(final ParserRuleContext call) {
        if (!(call instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext written = (FrostlakeParser.FunctionCallExprContext) call;
        final String name = written.functionName().getText().toUpperCase(Locale.ROOT);
        final DerivedColumnLineage lineage = statisticsColumn(written);
        if (lineage == null) {
            return null;
        }
        final Table stored = lineage.storedTable();
        final TableColumn column = stored.getColumn(lineage.storedColumn());
        if ("COUNT".equals(name)) {
            final Long counted = derivedCount(call, lineage);
            return counted == null ? null : "CAST(" + counted + " AS NUMBER(18,0))";
        }
        final DataType type = column.getDataType();
        if (!"MIN".equals(name) && !"MAX".equals(name)
                || !(type instanceof NumericType || type instanceof DateTimeType)) {
            return null;
        }
        return extremeOf(name, lineage);
    }

    /**
     * The refusal of an outer aggregate of a derived table read where it is no statistic: the call and the aggregate
     * reading it, as the account's plan prints them — the call cast to its number type where it is a number, a column
     * a view passes through printed as its table's, and an AVG as the sum it divides (see {@link #averageRead}). The
     * MIN, MAX or COUNT of a view's column that stands for an expression over its table reads that expression, as
     * the plan prints it: {@code MAX(FZ3.ID + 1)} for {@code vz3e AS SELECT id + 1 AS id FROM fz3}, and its SUM and
     * AVG as {@link #mergedSumRead} gives them (live-verified).
     */
    private String nestedRefusal(final ParserRuleContext call, final String reader) {
        String printed = ParseTreeText.getOriginalText(call).toUpperCase(Locale.ROOT);
        String correlated = null;
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext written = (FrostlakeParser.FunctionCallExprContext) call;
            final String name = written.functionName().getText().toUpperCase(Locale.ROOT);
            // A SUM(DISTINCT) of a bare column is read as its merged expression too (see distinctShiftedSum).
            final FrostlakeParser.QualifiedNameExprContext column = statisticsArgument(written) != null
                ? statisticsArgument(written) : "SUM".equals(name) ? distinctColumnArgument(written) : null;
            if (column != null) {
                final List<String> parts = partsOf(column.qualifiedName());
                final Table owner = argumentOwner(column);
                final DerivedColumnLineage lineage = statisticsColumn(written);
                final boolean extremeOrCount = "MIN".equals(name) || "MAX".equals(name) || "COUNT".equals(name);
                final boolean sums = ("SUM".equals(name) || "AVG".equals(name)) && written.DISTINCT() == null;
                final boolean distinctSum = "SUM".equals(name) && written.DISTINCT() != null;
                Table mergedSource = lineage == null && owner != null && (extremeOrCount || sums || distinctSum)
                    ? mergedExpressionSource(owner, parts.get(parts.size() - 1)) : null;
                Expression merged = mergedSource == null ? null
                    : mergedExpression(owner, parts.get(parts.size() - 1));
                final String[] mergedSum = merged == null ? null
                    : sums ? mergedSumRead(call, name, merged, mergedSource, reader)
                    : distinctSum ? distinctShiftedSum(merged, mergedSource) : null;
                if ((sums || distinctSum) && mergedSum == null) {
                    // A sum of a type the reading is not modelled for reads the column as written.
                    mergedSource = null;
                    merged = null;
                }
                // A relation the plan merges into the query is printed as the stored table it reads.
                final String argument = merged != null ? mergedText(merged, mergedSource)
                    : lineage != null && owner.residentSource() == null
                    ? (lineage.storedTable().getName() + "." + lineage.storedColumn()).toUpperCase(Locale.ROOT)
                    : String.join(".", parts);
                final DataType columnType = merged != null ? mergedType(merged, mergedSource)
                    : lineage != null ? lineage.storedTable().getColumn(lineage.storedColumn()).getDataType()
                    : owner != null ? owner.getColumn(parts.get(parts.size() - 1)).getDataType() : null;
                final boolean whole = columnType instanceof NumericType && !NumericType.isApproximate(columnType)
                    && ((NumericType) columnType).getScale() == 0;
                final String average = mergedSum == null && "AVG".equals(name) && written.DISTINCT() == null
                    ? averageRead(call, argument, columnType, lineage, reader) : null;
                if (mergedSum != null) {
                    printed = mergedSum[0];
                    correlated = mergedSum[1];
                } else if (average != null) {
                    printed = averageSum(argument, (NumericType) columnType);
                    correlated = average;
                } else if ("SUM".equals(name) && whole) {
                    printed = "SUM(CAST(" + argument + " AS NUMBER(38,0)))";
                    correlated = "CAST(" + printed + " AS NUMBER(38,0))";
                } else {
                    printed = name + "(" + argument + ")";
                    if (("MIN".equals(name) || "MAX".equals(name)) && columnType instanceof NumericType) {
                        correlated = "CAST(" + printed + " AS " + SqlTypeNames.canonical(columnType) + ")";
                    } else if ("COUNT".equals(name)) {
                        correlated = "CAST(" + printed + " AS NUMBER(18,0))";
                    }
                }
            }
        }
        return SqlCompilationError.withoutPosition("Aggregate functions cannot be nested: [" + printed + "] nested in ["
            + reader + "(CORRELATION(" + (correlated == null ? printed : correlated) + "))]");
    }

    /**
     * What a derived table's AVG of an outer number column reads as in the plan: the sum it divides (see
     * {@link #averageSum}) over the count the plan gives it — the derived count (see {@link #derivedCount}) where the
     * derived table reads one stored table with no filter, the column's COUNT under a filter or over a join
     * (live-verified):
     *
     * <pre>
     *   whole number   SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ.ID AS NUMBER(38,0))), 2), cast to NUMBER(38,6) under a SUM
     *                  SCALED_ROUND_INT_DIVIDE(CAST(SUM(…) AS NUMBER(38,0)), CAST(COUNT(FZ.ID) AS NUMBER(18,0)))
     *   FLOAT          (SUM(FZ.F)) / (CAST(2 AS FLOAT)), (SUM(FZ.F)) / (CAST(COUNT(FZ.F) AS FLOAT))
     *   NUMBER(p,s)    (CAST(SUM(CAST(FZ.D AS NUMBER(p,s))) AS NUMBER(p + 18,s + 6))) / 2, the precision at most 38,
     *                  … / (CAST(COUNT(FZ.D) AS NUMBER(18,0))) under a filter
     * </pre>
     *
     * Null for every other shape.
     */
    private String averageRead(final ParserRuleContext call, final String argument, final DataType type,
                               final DerivedColumnLineage lineage, final String reader) {
        if (!(type instanceof NumericType) || NumericType.isDecfloat(type)) {
            return null;
        }
        final NumericType number = (NumericType) type;
        final boolean approximate = NumericType.isApproximate(type);
        final boolean whole = !approximate && number.getScale() == 0;
        final FrostlakeParser.SelectClauseContext arm = enclosingClause(call);
        if (arm == null || arm.groupByClause() != null || arm.havingClause() != null
                || arm.qualifyClause() != null || !whole && !approximate && number.getScale() + 6 > 38) {
            return null;
        }
        final String sum = averageSum(argument, number);
        final Long count = arm.whereClause() != null || lineage == null ? null : derivedCount(call, lineage);
        if (count == null && (arm.whereClause() != null || lineage != null)) {
            if (whole) {
                return "SCALED_ROUND_INT_DIVIDE(CAST(" + sum + " AS NUMBER(38,0)), CAST(COUNT(" + argument
                    + ") AS NUMBER(18,0)))";
            }
            return approximate ? "(" + sum + ") / (CAST(COUNT(" + argument + ") AS FLOAT))"
                : "(CAST(" + sum + " AS NUMBER(" + Math.min(38, number.getPrecision() + 18) + ","
                    + (number.getScale() + 6) + "))) / (CAST(COUNT(" + argument + ") AS NUMBER(18,0)))";
        }
        if (count == null) {
            return null;
        }
        if (approximate) {
            return "(" + sum + ") / (CAST(" + count + " AS FLOAT))";
        }
        if (!whole) {
            return "(CAST(" + sum + " AS NUMBER(" + Math.min(38, number.getPrecision() + 18) + ","
                + (number.getScale() + 6) + "))) / " + count;
        }
        final String divided = "SCALED_ROUND_INT_DIVIDE(" + sum + ", " + count + ")";
        return "SUM".equals(reader) ? "CAST(" + divided + " AS NUMBER(38,6))" : divided;
    }

    /**
     * The sum an AVG of an outer number column divides, as the plan prints it: a whole number's cast to NUMBER(38,0),
     * a fixed-point one's to its own type, a FLOAT's bare.
     */
    private static String averageSum(final String argument, final NumericType type) {
        if (NumericType.isApproximate(type)) {
            return "SUM(" + argument + ")";
        }
        return "SUM(CAST(" + argument + " AS " + (type.getScale() == 0 ? "NUMBER(38,0)"
            : "NUMBER(" + type.getPrecision() + "," + type.getScale() + ")") + "))";
    }

    /**
     * The count a derived table's COUNT of an outer column is planned as: the rows of the one stored table the
     * derived table reads with no filter, less the NULLs the outer column holds, both as the statistics give them —
     * over a four-row table, of a column holding one NULL, 3 (live-verified). Null where the derived table reads no
     * such table.
     */
    private Long derivedCount(final ParserRuleContext call, final DerivedColumnLineage lineage) {
        return derivedCount(call, lineage.storedTable(), lineage.storedColumn());
    }

    /**
     * {@link #derivedCount} of the outer column {@code column} of the stored table {@code stored}. A WHERE the
     * statistics prove true of every row of the table is no filter to the plan: {@code WHERE g3.v > 0} over 10 and 20
     * keeps the derived count (live-verified).
     */
    private Long derivedCount(final ParserRuleContext call, final Table stored, final String column) {
        final FrostlakeParser.SelectClauseContext arm = enclosingClause(call);
        final Table source = arm == null || arm.groupByClause() != null || arm.havingClause() != null
            || arm.qualifyClause() != null ? null : soleStoredSource(arm);
        if (source == null || arm.whereClause() != null && !provenTrue(arm, source)) {
            return null;
        }
        final int index = stored.getColumnIndex(column);
        long nulls = 0;
        for (final Row row : executor.statisticsRowsFor(stored.getQualifiedName())) {
            if (index >= row.size() || row.getValue(index) == null) {
                nulls++;
            }
        }
        return Long.valueOf(Math.max(0L, executor.statisticsRowsFor(source.getQualifiedName()).size() - nulls));
    }

    /** Whether the statistics prove a select's WHERE true of every row of the one table it reads. */
    private boolean provenTrue(final FrostlakeParser.SelectClauseContext arm, final Table source) {
        final FrostlakeParser.TableReferenceContext reference = arm.tableExpression().tableReference(0);
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(reference.aliasName() != null ? reference.aliasName().getText()
            : reference.nonJoinKeywordIdentifier() != null ? reference.nonJoinKeywordIdentifier().getText()
            : source.getName(), source);
        try {
            final String where = ParseTreeText.getOriginalText(arm.whereClause().booleanExpr());
            return Boolean.TRUE.equals(new CountStatisticsBound(executor, arm, source, aliases, null, false, null)
                .verdictOver(ExpressionEvaluator.parse(where)));
        } catch (final RuntimeException unprovable) {
            return false;
        }
    }

    /** The select a node stands in, or null. */
    private static FrostlakeParser.SelectClauseContext enclosingClause(final ParseTree node) {
        ParseTree at = node.getParent();
        while (at != null && !(at instanceof FrostlakeParser.SelectClauseContext)) {
            at = at.getParent();
        }
        return (FrostlakeParser.SelectClauseContext) at;
    }

    /**
     * The one stored table a select reads, with no join beside it, or null. A view or a derived table that passes
     * every row and column of one stored table through, {@code SELECT * FROM t}, reads that table (live-verified).
     */
    private Table soleStoredSource(final FrostlakeParser.SelectClauseContext clause) {
        return soleStoredSource(clause, 0);
    }

    private Table soleStoredSource(final FrostlakeParser.SelectClauseContext clause, final int depth) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || from.tableReference().size() != 1 || !from.joinClause().isEmpty() || depth > 8) {
            return null;
        }
        final FrostlakeParser.TableSourceContext source = from.tableReference(0).tableSource();
        if (source != null && source.selectStatement() != null) {
            return passedThrough(source.selectStatement(), depth);
        }
        final Table table = source == null || source.tableQualifiedName() == null ? null
            : storedTable(source.tableQualifiedName());
        if (table != null && table.getQualifiedName() != null
                && executor.getStorageEngine().hasTable(table.getQualifiedName())) {
            return table;
        }
        final View view = source == null || source.tableQualifiedName() == null ? null
            : viewNamed(source.tableQualifiedName());
        if (view == null || view.getDefinition() == null) {
            return null;
        }
        try {
            return passedThrough(executor.parseSelectStatement(view.getDefinition()), depth);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** The stored table a query passes whole through, {@code SELECT * FROM t} and nothing more, or null. */
    private Table passedThrough(final FrostlakeParser.SelectStatementContext query, final int depth) {
        if (query.withClause() != null || query.selectOperand().size() != 1 || query.orderByClause() != null
                || query.limitClause() != null || query.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext body = query.selectOperand(0).selectClause();
        if (body == null || body.DISTINCT() != null || body.topClause() != null || body.whereClause() != null
                || body.groupByClause() != null || body.havingClause() != null || body.qualifyClause() != null
                || body.connectByClause() != null || body.selectList().selectItem().size() != 1
                || !(body.selectList().selectItem(0) instanceof FrostlakeParser.StarItemContext)
                || !((FrostlakeParser.StarItemContext) body.selectList().selectItem(0)).starModifier().isEmpty()) {
            return null;
        }
        return soleStoredSource(body, depth + 1);
    }

    /** The view a FROM name reads, or null. */
    private View viewNamed(final FrostlakeParser.TableQualifiedNameContext name) {
        try {
            return catalog.resolveView(SqlIdentifiers.canonicalText(name.getText()));
        } catch (final RuntimeException notAView) {
            return null;
        }
    }

    /**
     * The stored table beneath a merged relation's column that stands for an expression over it — no column passed
     * through, read under no alias of the relation's own — or null.
     */
    private Table mergedExpressionSource(final Table owner, final String column) {
        if (!(owner.getRelationStatistics() instanceof MergedRelationStatistics) || !owner.hasColumn(column)) {
            return null;
        }
        final MergedRelationStatistics statistics = (MergedRelationStatistics) owner.getRelationStatistics();
        final Table source = statistics.sourceRelation();
        final String item = statistics.columnSource(owner.getColumnIndex(column));
        if (source == null || item == null || statistics.sourceAlias() != null || source.getQualifiedName() == null
                || !executor.getStorageEngine().hasTable(source.getQualifiedName())) {
            return null;
        }
        final Expression parsed = mergedExpression(owner, column);
        return parsed == null || parsed instanceof ColumnReferenceExpression
            || parsed instanceof LiteralExpression ? null : source;
    }

    /**
     * The SUM or the AVG of a merged relation's column that stands for an expression over its stored table, as the
     * plan reads it, the aggregate refused as nested and what the reader reads (live-verified):
     *
     * <pre>
     *   x + c, x - c, c + x, c - x   x a whole-number column, c a whole number: the column's sum and the constant
     *                                times the derived count (see {@link #derivedCount}), cast to the product's type
     *                                unless the two are past eighteen digits between them
     *       SUM   [SUM(CAST(FZ3.ID AS NUMBER(38,0)))] read as
     *             CAST((SUM(CAST(FZ3.ID AS NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0)),
     *             uncast under any reader but a SUM; with no derived count, as over a join,
     *             (CAST(SUM(…) AS NUMBER(38,0))) + (1 * (CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))
     *       AVG   SCALED_ROUND_INT_DIVIDE(CAST(that sum AS NUMBER(38,0)), CAST(COUNT(FZ3.ID + 1) AS NUMBER(18,0))),
     *             the sum with no derived count uncast
     *   another whole number          SUM(CAST(FZ3.ID * 2 AS NUMBER(38,0))), cast to NUMBER(38,0); the AVG that sum
     *                                 over COUNT(FZ3.ID * 2) as above
     *   NUMBER(38,s)                  the same, cast to NUMBER(38,s)
     *   FLOAT                         SUM(FZF.F + 1.0), uncast; the AVG (SUM(…)) / (CAST(COUNT(…) AS FLOAT))
     * </pre>
     *
     * @return the refused aggregate and the read, or null for a type this does not read
     */
    private String[] mergedSumRead(final ParserRuleContext call, final String name, final Expression merged,
                                   final Table source, final String reader) {
        final DataType type = mergedType(merged, source);
        if (!(type instanceof NumericType) || NumericType.isDecfloat(type)) {
            return null;
        }
        final boolean approximate = NumericType.isApproximate(type);
        final String argument = approximate ? floatText(merged, source) : mergedText(merged, source);
        final String count = "CAST(COUNT(" + argument + ") AS NUMBER(18,0))";
        final boolean average = "AVG".equals(name);
        if (approximate) {
            final String sum = "SUM(" + argument + ")";
            return new String[] {sum, average ? "(" + sum + ") / (CAST(COUNT(" + argument + ") AS FLOAT))" : sum};
        }
        final NumericType number = (NumericType) type;
        if (number.getScale() > 0 && number.getPrecision() != 38) {
            return null;
        }
        final String exact = "NUMBER(38," + number.getScale() + ")";
        final String[] shifted = number.getScale() == 0 ? shiftedSum(call, merged, source, reader, average) : null;
        if (shifted != null) {
            // Past eighteen digits between the constant and the count, the count's argument casts the column too.
            final String counted = shifted[2] == null ? count : "CAST(COUNT(" + shifted[2] + ") AS NUMBER(18,0))";
            return new String[] {shifted[0], average
                ? "SCALED_ROUND_INT_DIVIDE(" + shifted[1] + ", " + counted + ")" : shifted[1]};
        }
        final String sum = "SUM(CAST(" + argument + " AS " + exact + "))";
        return new String[] {sum, average ? "SCALED_ROUND_INT_DIVIDE(CAST(" + sum + " AS " + exact + "), " + count + ")"
            : "CAST(" + sum + " AS " + exact + ")"};
    }

    /**
     * The sum of a whole-number column moved by a whole-number constant, {@code x + c} or {@code c - x} and the
     * like, as the plan rewrites it (see {@link #mergedSumRead}): the column's sum, what is read for it — as an
     * AVG's dividend when {@code average} holds — and the argument an AVG's count reads where the constant and the
     * derived count are past eighteen digits between them, the column cast beside the constant, {@code
     * (CAST(FZ3.ID AS NUMBER(38,0))) + 123456789012345678} (live-verified), else null; null for any other expression.
     */
    private String[] shiftedSum(final ParserRuleContext call, final Expression merged, final Table source,
                                final String reader, final boolean average) {
        if (!(merged instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression operation = (BinaryOperationExpression) merged;
        final BinaryOperator operator = operation.getOperator();
        if (operator != BinaryOperator.ADD && operator != BinaryOperator.SUBTRACT) {
            return null;
        }
        final boolean constantFirst = operation.getLeft() instanceof LiteralExpression;
        final Expression columnSide = constantFirst ? operation.getRight() : operation.getLeft();
        final Expression constantSide = constantFirst ? operation.getLeft() : operation.getRight();
        final BigInteger constant = wholeLiteral(constantSide);
        if (!(columnSide instanceof ColumnReferenceExpression) || constant == null) {
            return null;
        }
        final String column = ((ColumnReferenceExpression) columnSide).getColumnName();
        final DataType columnType = source.hasColumn(column) ? source.getColumn(column).getDataType() : null;
        if (!(columnType instanceof NumericType) || NumericType.isApproximate(columnType)
                || NumericType.isDecfloat(columnType) || ((NumericType) columnType).getScale() != 0) {
            return null;
        }
        final String printedColumn = mergedText(columnSide, source);
        final String sum = "SUM(CAST(" + printedColumn + " AS NUMBER(38,0)))";
        final String sign = operator == BinaryOperator.ADD ? " + " : " - ";
        final Long derived = derivedCount(call, source, source.getColumn(column).getName());
        final String read;
        String countedArgument = null;
        if (derived != null) {
            final int digits = constant.abs().toString().length();
            final String product = constant + " * " + derived;
            final boolean fits = digits + derived.toString().length() <= 18;
            final String shift = fits
                ? "CAST(" + product + " AS NUMBER(" + Math.min(38, digits + 18) + ",0))" : product;
            if (!fits) {
                final String castColumn = "(CAST(" + printedColumn + " AS NUMBER(38,0)))";
                countedArgument = constantFirst ? constant + sign + castColumn : castColumn + sign + constant;
            }
            final String moved = constantFirst ? "(" + shift + ")" + sign + "(" + sum + ")"
                : "(" + sum + ")" + sign + "(" + shift + ")";
            read = average || "SUM".equals(reader) ? "CAST(" + moved + " AS NUMBER(38,0))" : moved;
        } else {
            final String shift = constant + " * (CAST(COUNT(" + printedColumn + ") AS NUMBER(18,0)))";
            final String whole = "CAST(" + sum + " AS NUMBER(38,0))";
            read = constantFirst ? "(" + shift + ")" + sign + "(" + whole + ")" : "(" + whole + ")" + sign + "(" + shift
                + ")";
        }
        return new String[] {sum, read, countedArgument};
    }

    /**
     * The SUM(DISTINCT) of a whole-number column moved by a whole-number constant, as the plan rewrites it: the
     * column's distinct sum, DISTINCT printed inside its cast too, and the constant times the column's distinct count
     * read beside it — {@code [SUM(DISTINCT CAST(DISTINCT FZ3.ID AS NUMBER(38,0)))]} read as {@code (CAST(SUM(DISTINCT
     * CAST(DISTINCT FZ3.ID AS NUMBER(38,0))) AS NUMBER(38,0))) + (1 * (CAST(COUNT(DISTINCT FZ3.ID) AS NUMBER(18,0))))}
     * (live-verified); null for any other expression.
     */
    private String[] distinctShiftedSum(final Expression merged, final Table source) {
        if (!(merged instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression operation = (BinaryOperationExpression) merged;
        final BinaryOperator operator = operation.getOperator();
        if (operator != BinaryOperator.ADD && operator != BinaryOperator.SUBTRACT) {
            return null;
        }
        final boolean constantFirst = operation.getLeft() instanceof LiteralExpression;
        final Expression columnSide = constantFirst ? operation.getRight() : operation.getLeft();
        final BigInteger constant = wholeLiteral(constantFirst ? operation.getLeft() : operation.getRight());
        if (!(columnSide instanceof ColumnReferenceExpression) || constant == null) {
            return null;
        }
        final String column = ((ColumnReferenceExpression) columnSide).getColumnName();
        final DataType columnType = source.hasColumn(column) ? source.getColumn(column).getDataType() : null;
        if (!(columnType instanceof NumericType) || NumericType.isApproximate(columnType)
                || NumericType.isDecfloat(columnType) || ((NumericType) columnType).getScale() != 0) {
            return null;
        }
        final String printedColumn = mergedText(columnSide, source);
        final String sum = "SUM(DISTINCT CAST(DISTINCT " + printedColumn + " AS NUMBER(38,0)))";
        final String sign = operator == BinaryOperator.ADD ? " + " : " - ";
        final String shift = constant + " * (CAST(COUNT(DISTINCT " + printedColumn + ") AS NUMBER(18,0)))";
        final String whole = "CAST(" + sum + " AS NUMBER(38,0))";
        return new String[] {sum, constantFirst ? "(" + shift + ")" + sign + "(" + whole + ")"
            : "(" + whole + ")" + sign + "(" + shift + ")"};
    }

    /** The whole number a literal holds, or null for any other expression. */
    private static BigInteger wholeLiteral(final Expression expression) {
        if (!(expression instanceof LiteralExpression) || ((LiteralExpression) expression).getValue() == null) {
            return null;
        }
        try {
            final BigDecimal value = DecimalText.parse(String.valueOf(((LiteralExpression) expression).getValue()));
            return value.scale() > 0 ? null : value.toBigIntegerExact();
        } catch (final NumberFormatException | ArithmeticException notWhole) {
            return null;
        }
    }

    /** The expression a merged relation's column stands for, or null. */
    private static Expression mergedExpression(final Table owner,
                                                                                 final String column) {
        final MergedRelationStatistics statistics = (MergedRelationStatistics) owner.getRelationStatistics();
        try {
            return ExpressionEvaluator.parse(statistics.columnSource(owner.getColumnIndex(column)));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** A merged expression as the plan prints it over its stored table, each column named by that table. */
    private String mergedText(final Expression merged, final Table source) {
        return new ExpressionEvaluatorVisitor(source, null, executor.getFunctionRegistry(), catalog)
            .strictPlanText(merged);
    }

    /**
     * A FLOAT merged expression as the plan prints it: a number written out beside the FLOAT column folded into a FLOAT
     * literal, {@code FZF.F + 1.0} and {@code FZF.F + 2.5} (live-verified); as {@link #mergedText} prints it otherwise.
     */
    private String floatText(final Expression merged, final Table source) {
        if (merged instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) merged;
            final String op = arithmeticSymbol(binary.getOperator());
            final String right = floatLiteral(binary.getRight());
            final String left = floatLiteral(binary.getLeft());
            if (op != null && right != null && binary.getLeft() instanceof ColumnReferenceExpression) {
                return mergedText(binary.getLeft(), source) + " " + op + " " + right;
            }
            if (op != null && left != null && binary.getRight() instanceof ColumnReferenceExpression) {
                return left + " " + op + " " + mergedText(binary.getRight(), source);
            }
        }
        return mergedText(merged, source);
    }

    /** A number written out as a FLOAT literal prints, {@code 1.0}, {@code 2.5}; null for anything else. */
    private static String floatLiteral(final Expression expression) {
        if (!(expression instanceof LiteralExpression)
                || !(((LiteralExpression) expression).getValue() instanceof Number)) {
            return null;
        }
        final BigDecimal value = DecimalText.parse(((LiteralExpression) expression).getValue().toString())
            .stripTrailingZeros();
        return value.scale() <= 0 ? value.setScale(1).toPlainString() : value.toPlainString();
    }

    private static String arithmeticSymbol(final BinaryOperator operator) {
        switch (operator) {
            case ADD:
                return "+";
            case SUBTRACT:
                return "-";
            case MULTIPLY:
                return "*";
            case DIVIDE:
                return "/";
            default:
                return null;
        }
    }

    /** A merged expression's type over its stored table, or null. */
    private DataType mergedType(final Expression merged, final Table source) {
        try {
            return new ExpressionEvaluator(source, executor.getFunctionRegistry(), catalog, executor)
                .inferStaticType(merged);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }

    /** The name of the first aggregate of a select reading a derived item, upper-cased; ANY_VALUE where none does. */
    private String readingAggregate(final FrostlakeParser.SelectClauseContext reader, final String alias,
                                    final String name) {
        final List<FrostlakeParser.QualifiedNameExprContext> references = new ArrayList<>();
        collectReferencesOver(reader, references);
        for (final FrostlakeParser.QualifiedNameExprContext reference : references) {
            final List<String> parts = partsOf(reference.qualifiedName());
            if (!parts.get(parts.size() - 1).equals(name) || parts.size() == 2 && !parts.get(0).equals(alias)) {
                continue;
            }
            for (ParseTree at = reference.getParent(); at != null && at != reader; at = at.getParent()) {
                if (isAggregateCall(at)) {
                    return aggregateNameOf(at);
                }
            }
        }
        return "ANY_VALUE";
    }

    /** An aggregate call's function name, upper-cased. */
    private static String aggregateNameOf(final ParseTree call) {
        final FrostlakeParser.FunctionNameContext name;
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            name = ((FrostlakeParser.FunctionCallExprContext) call).functionName();
        } else if (call instanceof FrostlakeParser.FunctionCallStarExprContext) {
            name = ((FrostlakeParser.FunctionCallStarExprContext) call).functionName();
        } else if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            name = ((FrostlakeParser.FunctionCallMixedArgsExprContext) call).functionName();
        } else {
            name = ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).functionName();
        }
        return name.getText().toUpperCase(Locale.ROOT);
    }

    /**
     * A statement as it is run: each place of {@code written} replaced by its text, from the last place to the first
     * so each keeps its offsets until it is reached.
     *
     * @return the text, or null where a place lies outside the statement
     */
    private static String rewritten(final FrostlakeParser.SelectStatementContext statement,
                                    final Map<ParserRuleContext, String> written) {
        final List<ParserRuleContext> places = new ArrayList<>(written.keySet());
        Collections.sort(places, new Comparator<ParserRuleContext>() {
            @Override
            public int compare(final ParserRuleContext one, final ParserRuleContext other) {
                return Integer.compare(other.getStart().getStartIndex(), one.getStart().getStartIndex());
            }
        });
        final StringBuilder text = new StringBuilder(ParseTreeText.getOriginalText(statement));
        final int base = statement.getStart().getStartIndex();
        for (final ParserRuleContext place : places) {
            final int from = place.getStart().getStartIndex() - base;
            final int to = place.getStop().getStopIndex() - base;
            if (from < 0 || to >= text.length()) {
                return null;
            }
            text.replace(from, to + 1, written.get(place));
        }
        return text.toString();
    }

    // ── an EXISTS over the outer query's own aggregate ───────────────────────────────

    /**
     * How a subquery reads the outer row when it reads it only through equalities written as conjuncts of its WHERE,
     * each an expression over its own relations equal to one over the outer row, its FROM, its select list and its
     * other conjuncts reading its own relations alone (see {@link EqualityCorrelation}). Null for any other
     * subquery: one with a set operator, a nested query or a lambda, a DISTINCT, a row limit, an ORDER BY, a
     * grouping, a HAVING, a QUALIFY or a CONNECT BY, and one reading a name it cannot place.
     *
     * @param statement the subquery
     * @return its correlation, or null
     */
    public EqualityCorrelation equalityCorrelation(final FrostlakeParser.SelectStatementContext statement) {
        if (statement == null || !statement.setOperator().isEmpty() || statement.selectOperand().size() != 1
                || statement.orderByClause() != null || statement.limitClause() != null
                || statement.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext body = statement.selectOperand(0).selectClause();
        if (body == null || body.tableExpression() == null || body.whereClause() == null || body.DISTINCT() != null
                || body.topClause() != null || body.intoClause() != null || body.connectByClause() != null
                || body.groupByClause() != null || body.havingClause() != null || body.qualifyClause() != null
                || holdsNestedQueryOrLambda(body)) {
            return null;
        }
        try {
            final List<CorrelationScope> scopes = scopeOfAlone(body);
            if (readsBeyondItself(body.tableExpression(), scopes) || readsBeyondItself(body.selectList(), scopes)) {
                return null;
            }
            final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
            conjunctsOf(body.whereClause().booleanExpr(), conjuncts);
            final List<String> innerKeys = new ArrayList<>();
            final List<String> outerKeys = new ArrayList<>();
            final List<String> others = new ArrayList<>();
            for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
                if (!readsBeyondItself(conjunct, scopes)) {
                    others.add(ParseTreeText.getOriginalText(conjunct));
                    continue;
                }
                if (!(conjunct instanceof FrostlakeParser.ValueExprContext)
                        || !(((FrostlakeParser.ValueExprContext) conjunct).expression()
                            instanceof FrostlakeParser.ComparisonExprContext)) {
                    return null;
                }
                final FrostlakeParser.ComparisonExprContext equality = (FrostlakeParser.ComparisonExprContext)
                    ((FrostlakeParser.ValueExprContext) conjunct).expression();
                if (equality.op.getType() != FrostlakeParser.EQ) {
                    return null;
                }
                final FrostlakeParser.ExpressionContext left = equality.expression(0);
                final FrostlakeParser.ExpressionContext right = equality.expression(1);
                if (readsOnly(left, CorrelationSide.INNER, scopes) && readsOnly(right, CorrelationSide.OUTER, scopes)) {
                    innerKeys.add(ParseTreeText.getOriginalText(left));
                    outerKeys.add(ParseTreeText.getOriginalText(right));
                } else if (readsOnly(left, CorrelationSide.OUTER, scopes)
                        && readsOnly(right, CorrelationSide.INNER, scopes)) {
                    innerKeys.add(ParseTreeText.getOriginalText(right));
                    outerKeys.add(ParseTreeText.getOriginalText(left));
                } else {
                    return null;
                }
            }
            if (innerKeys.isEmpty()) {
                return null;
            }
            final boolean rowPerKeptRow = !anyItemAggregates(body);
            return new EqualityCorrelation(innerKeys, outerKeys, ParseTreeText.getOriginalText(body.tableExpression()),
                others, aggregateItemOf(body), rowPerKeptRow, rowPerKeptRow ? memberItemOf(body) : null);
        } catch (final RuntimeException unplaced) {
            return null;
        }
    }

    /** The select list's one item when it is an aggregate expression with no window, as written, or null. */
    private String aggregateItemOf(final FrostlakeParser.SelectClauseContext body) {
        if (body.selectList().selectItem().size() != 1
                || !(body.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final FrostlakeParser.SelectItemContext item = body.selectList().selectItem(0);
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        if (value == null || !executor.hasAggregateFunctionInExpression(value)
                || executor.hasWindowFunctionInExpression(value)) {
            return null;
        }
        return ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
    }

    /** The select list's one expression item, as written, or null for a star or a list of more than one. */
    private static String memberItemOf(final FrostlakeParser.SelectClauseContext body) {
        if (body.selectList().selectItem().size() != 1
                || !(body.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final FrostlakeParser.SelectItemContext item = body.selectList().selectItem(0);
        return SelectItemAccessors.getItemValueExpr(item) == null ? null
            : ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
    }

    /** Whether a select item aggregates or computes a window, either of which shapes the rows the select answers. */
    private boolean anyItemAggregates(final FrostlakeParser.SelectClauseContext body) {
        for (final FrostlakeParser.SelectItemContext item : body.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                continue;
            }
            final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
            if (value == null || executor.hasAggregateFunctionInExpression(value)
                    || executor.hasWindowFunctionInExpression(value)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a node reads anything but the subquery's own relations: the outer row, or a name placed nowhere. */
    private boolean readsBeyondItself(final ParseTree node, final List<CorrelationScope> scopes) {
        return readsDirectly(node, CorrelationSide.OUTER, scopes)
            || readsDirectly(node, CorrelationSide.CONSTANT, scopes)
            || readsDirectly(node, CorrelationSide.UNKNOWN, scopes);
    }

    /** Whether a node reads {@code side}, and nothing else. */
    private boolean readsOnly(final ParseTree node, final CorrelationSide side, final List<CorrelationScope> scopes) {
        if (!readsDirectly(node, side, scopes)) {
            return false;
        }
        for (final CorrelationSide other : CorrelationSide.values()) {
            if (other != side && readsDirectly(node, other, scopes)) {
                return false;
            }
        }
        return true;
    }

    /** The conjuncts of a condition, through AND and parentheses. */
    private static void conjunctsOf(final FrostlakeParser.BooleanExprContext condition,
                                    final List<FrostlakeParser.BooleanExprContext> conjuncts) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            conjunctsOf(((FrostlakeParser.AndExprContext) condition).booleanExpr(0), conjuncts);
            conjunctsOf(((FrostlakeParser.AndExprContext) condition).booleanExpr(1), conjuncts);
            return;
        }
        if (condition instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) condition).expression()
                    instanceof FrostlakeParser.ParenExprContext) {
            conjunctsOf(((FrostlakeParser.ParenExprContext) ((FrostlakeParser.ValueExprContext) condition)
                .expression()).booleanExpr(), conjuncts);
            return;
        }
        conjuncts.add(condition);
    }

    /** Whether a select holds a query or a lambda of its own anywhere. */
    private static boolean holdsNestedQueryOrLambda(final ParseTree node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (child instanceof FrostlakeParser.SelectStatementContext
                    || child instanceof FrostlakeParser.LambdaFunctionContext
                    || holdsNestedQueryOrLambda(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An EXISTS body as it is run: each aggregate of its select lists that the body's rows are not counted by
     * (see {@link #countedAggregates}) replaced by a constant as wide as the call, so every place after it keeps
     * its position. An aggregate over the outer row alone is the outer query's value and not an aggregate of the
     * body, whose rows are counted as if no aggregate stood there: {@code EXISTS (SELECT MAX(fz.id) FROM g WHERE
     * FALSE)} is FALSE, where {@code EXISTS (SELECT MAX(fz.id), COUNT(*) FROM g WHERE FALSE)} aggregates its own
     * rows and is TRUE (live-verified). A derived table's outer aggregate runs as {@link #derivedOuterAggregates}
     * writes it, and one read where it is no statistic is refused here.
     *
     * @param body the EXISTS's select
     * @return the text to run, or null where the body holds no such call
     */
    public String countedBodyText(final FrostlakeParser.SelectStatementContext body) {
        final String nesting = derivedOuterNesting(body);
        if (nesting != null) {
            throw new RuntimeException(nesting);
        }
        final Map<ParserRuleContext, String> written = new LinkedHashMap<>();
        for (final ParserRuleContext call : countedAggregates(body)) {
            written.put(call, asWideConstant(call));
        }
        written.putAll(derivedOuterAggregates(body));
        if (written.isEmpty() || body.getStart() == null || body.getStart().getInputStream() == null) {
            return null;
        }
        return rewritten(body, written);
    }

    /**
     * The aggregate calls in the select lists of an EXISTS body — its one select, or each operand of its set
     * operation — whose place a constant takes without changing the body's rows (see {@link #counts}), in the
     * order written.
     */
    public List<ParserRuleContext> countedAggregates(final FrostlakeParser.SelectStatementContext body) {
        final List<FrostlakeParser.SelectClauseContext> arms = new ArrayList<>();
        collectArms(body, arms);
        final List<ParserRuleContext> counted = new ArrayList<>();
        for (final FrostlakeParser.SelectClauseContext arm : arms) {
            final List<ParserRuleContext> calls = aggregateCalls(arm.selectList());
            if (calls.isEmpty()) {
                continue;
            }
            final List<CorrelationScope> scopes = scopeOfAlone(arm);
            for (final ParserRuleContext call : calls) {
                if (counts(call, arm, scopes)) {
                    counted.add(call);
                }
            }
        }
        return counted;
    }

    /**
     * The aggregate calls written anywhere inside {@code subquery}, through every select nested in it, that read
     * the outer row and no row of a select they stand in: the outer query's own aggregates, in the order written.
     * A call with an OVER is a window and no aggregate.
     */
    public List<ParserRuleContext> outerAggregates(final FrostlakeParser.SelectStatementContext subquery) {
        final List<ParserRuleContext> found = new ArrayList<>();
        collectOuterAggregates(subquery, new ArrayList<CorrelationScope>(), found);
        return found;
    }

    private void collectOuterAggregates(final ParseTree node, final List<CorrelationScope> scopes,
                                        final List<ParserRuleContext> into) {
        if (node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;
        }
        if (isAggregateCall(node) && readsOuterRowAlone((ParserRuleContext) node, scopes)) {
            into.add((ParserRuleContext) node);
            return;
        }
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
            collectOuterAggregates(child, scopes, into);
            if (orderBy) {
                scopes.remove(scopes.size() - 1);
            }
        }
        if (clause) {
            scopes.remove(scopes.size() - 1);
        }
    }

    /** The selects a statement runs: its own, and each operand's through a set operation and parentheses. */
    private static void collectArms(final FrostlakeParser.SelectStatementContext statement,
                                    final List<FrostlakeParser.SelectClauseContext> into) {
        for (final FrostlakeParser.SelectOperandContext operand : statement.selectOperand()) {
            if (operand.selectClause() != null) {
                into.add(operand.selectClause());
            } else if (operand.selectStatement() != null) {
                collectArms(operand.selectStatement(), into);
            }
        }
    }

    /**
     * Whether an aggregate call in a select list of an EXISTS body leaves the body's rows as they are when a
     * constant stands in its place: one over the outer row alone, which is the outer query's value, and one of
     * the body's own reading the outer row where the select groups without it — by a GROUP BY, or by a HAVING
     * that aggregates — since nothing reads the item's value. {@code EXISTS (SELECT MAX(fz.id + g.v) FROM g GROUP
     * BY g.s)} and {@code … FROM g HAVING COUNT(*) > 0} are answered (live-verified).
     */
    private boolean counts(final ParserRuleContext call, final FrostlakeParser.SelectClauseContext arm,
                           final List<CorrelationScope> scopes) {
        if (readsOuterRowAlone(call, scopes)) {
            return true;
        }
        if (arm.groupByClause() == null
                && (arm.havingClause() == null || plainAggregateCalls(arm.havingClause()).isEmpty())) {
            return false;
        }
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNamesOutsideQueries(call, names);
        final boolean judgingBefore = judging;
        judging = false;
        try {
            for (final FrostlakeParser.QualifiedNameExprContext name : names) {
                if (placedSideOf(name, scopes) == CorrelationSide.OUTER) {
                    return true;
                }
            }
        } finally {
            judging = judgingBefore;
        }
        return false;
    }

    /** Every aggregate call {@code node} holds outside any nested select, the outer query's own included. */
    private List<ParserRuleContext> plainAggregateCalls(final ParseTree node) {
        final Set<ParserRuleContext> callsBefore = countedCalls;
        countedCalls = Collections.emptySet();
        try {
            return aggregateCalls(node);
        } finally {
            countedCalls = callsBefore;
        }
    }

    /** Whether every name a call reads, outside the queries nested in it, is the outer row's, and it reads one. */
    private boolean readsOuterRowAlone(final ParserRuleContext call, final List<CorrelationScope> scopes) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNamesOutsideQueries(call, names);
        final boolean judgingBefore = judging;
        judging = false;
        try {
            for (final FrostlakeParser.QualifiedNameExprContext name : names) {
                if (placedSideOf(name, scopes) != CorrelationSide.OUTER) {
                    return false;
                }
            }
        } finally {
            judging = judgingBefore;
        }
        return !names.isEmpty();
    }

    /**
     * Whether a reference stands inside an aggregate over the outer row alone, in a select list of the EXISTS
     * body judged or of an EXISTS nested in the subquery.
     */
    private boolean insideCountedAggregate(final FrostlakeParser.QualifiedNameExprContext reference,
                                           final List<CorrelationScope> scopes) {
        ParserRuleContext call = null;
        ParserRuleContext at = reference.getParent();
        while (at != null && !(at instanceof FrostlakeParser.SelectListContext)) {
            if (at instanceof FrostlakeParser.SelectStatementContext || at instanceof FrostlakeParser.SelectClauseContext) {
                return false;
            }
            if (isAggregateCall(at)) {
                call = at;
            }
            at = at.getParent();
        }
        if (at == null || call == null || !(at.getParent() instanceof FrostlakeParser.SelectClauseContext)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext arm = (FrostlakeParser.SelectClauseContext) at.getParent();
        ParserRuleContext statement = at.getParent().getParent();
        while (statement != null && !(statement instanceof FrostlakeParser.SelectStatementContext
                && !(statement.getParent() instanceof FrostlakeParser.SelectOperandContext))) {
            statement = statement.getParent();
        }
        return statement != null
            && (statement == countedBody || statement.getParent() instanceof FrostlakeParser.ExistsExprContext)
            && counts(call, arm, scopes);
    }

    /**
     * Whether an EXISTS body reads the value of an aggregate over the outer row alone, which its rows are not
     * counted by: under DISTINCT, or in a HAVING naming the item that holds it. {@code EXISTS (SELECT DISTINCT
     * MAX(fz.id) FROM g)} and {@code EXISTS (SELECT MAX(fz.id) AS m FROM g HAVING m > 5)} are refused
     * (live-verified).
     */
    private boolean readsCountedValue(final FrostlakeParser.SelectStatementContext body) {
        final List<FrostlakeParser.SelectClauseContext> arms = new ArrayList<>();
        collectArms(body, arms);
        for (final FrostlakeParser.SelectClauseContext arm : arms) {
            if (arm.DISTINCT() == null && arm.havingClause() == null || aggregateCalls(arm.selectList()).isEmpty()) {
                continue;
            }
            final List<CorrelationScope> scopes = scopeOfAlone(arm);
            for (final FrostlakeParser.SelectItemContext item : arm.selectList().selectItem()) {
                boolean counted = false;
                for (final ParserRuleContext call : aggregateCalls(item)) {
                    counted |= readsOuterRowAlone(call, scopes);
                }
                if (counted && (arm.DISTINCT() != null || namedIn(arm.havingClause(), item))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a clause reads, outside any nested select, the name a select item is given. */
    private static boolean namedIn(final ParserRuleContext clause, final FrostlakeParser.SelectItemContext item) {
        if (clause == null || !(item instanceof FrostlakeParser.ExprItemContext)) {
            return false;
        }
        final FrostlakeParser.ExprItemContext named = (FrostlakeParser.ExprItemContext) item;
        final String alias = named.aliasName() != null ? ParseTreeText.getIdentifier(named.aliasName()).toUpperCase(Locale.ROOT)
            : named.identifier() != null ? ParseTreeText.getIdentifier(named.identifier()).toUpperCase(Locale.ROOT)
            : named.bareAliasWord() != null ? ParseTreeText.getIdentifier(named.bareAliasWord()) : null;
        if (alias == null) {
            return false;
        }
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNamesOutsideQueries(clause, names);
        for (final FrostlakeParser.QualifiedNameExprContext name : names) {
            final List<String> parts = partsOf(name.qualifiedName());
            if (parts.size() == 1 && parts.get(0).equals(alias)) {
                return true;
            }
        }
        return false;
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
        if (aggregatesBesideOuterOverPrunedScan(clause, scopes, in)) {
            return true;
        }
        // The statistics rule a subject out whatever the item, except outside a WHERE's semi-join an aggregate, whose
        // value over no row is NULL: there the membership is judged as written (live-verified).
        if (prunedAway(clause)
                || (semiJoin || aggregateCalls(clause.selectList()).isEmpty()) && subjectRuledOut(clause)
                || statement != null && limitedToNoRow(statement, clause)) {
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
        // An aggregate of its own, with no GROUP BY or HAVING, may not read the outer row inside its argument, as a
        // scalar subquery's may not: EXISTS (SELECT MAX(fz.id + g.v) FROM g) and IN (SELECT MAX(fz.id + g.v) FROM g)
        // are refused (live-verified).
        if (clause.groupByClause() == null && clause.havingClause() == null
                && readsOuterInsideAggregate(clause, statement, scopes)) {
            return true;
        }
        if (in && clause.tableExpression() != null && readsVaryingOuter(clause.selectList(), scopes)
                && !readsDirectly(clause.selectList(), CorrelationSide.INNER, scopes)) {
            return true;
        }
        return !filtersSupported(clause, scopes) || !derivedTablesSupported(clause, scopes);
    }

    /**
     * Whether a membership selects an aggregate of its own with the outer row read in the select list, over a
     * scan a filter of its own prunes. The pruned scan does not fold the membership away as it does a plain one:
     * {@code IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE)} is refused, as it is under {@code WHERE NULL},
     * {@code WHERE g.id > 100} past every stored id, {@code WHERE g.id = 5 AND FALSE}, grouped, under NOT IN and
     * beside a LIMIT, where {@code WHERE 1 = 0}, which reads no column, a filter reading the outer row, a HAVING
     * and an empty table leave it answered. An EXISTS over an ungrouped aggregate is refused under any filter
     * that never holds, {@code WHERE 1 = 0} included (live-verified).
     */
    private boolean aggregatesBesideOuterOverPrunedScan(final FrostlakeParser.SelectClauseContext clause,
                                                        final List<CorrelationScope> scopes, final boolean in) {
        if (clause.whereClause() == null || aggregateCalls(clause.selectList()).isEmpty()
                || !in && clause.groupByClause() != null || !readsOuterIn(clause.selectList(), scopes)) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(clause.whereClause().booleanExpr(), conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            // A conjunct naming the outer row is none of the scan's own, whatever the statistics make of it.
            final boolean before = readsAsWritten;
            readsAsWritten = true;
            final boolean correlated;
            try {
                correlated = readsOuterIn(conjunct, scopes);
            } finally {
                readsAsWritten = before;
            }
            if (correlated) {
                continue;
            }
            if (isFalseOrNullLiteral(conjunct)) {
                return true;
            }
            if ((!in || readsDirectly(conjunct, CorrelationSide.INNER, scopes)) && provenFalse(conjunct, clause, scopes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFalseOrNullLiteral(final FrostlakeParser.BooleanExprContext conjunct) {
        if (!(conjunct instanceof FrostlakeParser.ValueExprContext)
                || !(((FrostlakeParser.ValueExprContext) conjunct).expression() instanceof FrostlakeParser.LiteralExprContext)) {
            return false;
        }
        final FrostlakeParser.LiteralContext literal =
            ((FrostlakeParser.LiteralExprContext) ((FrostlakeParser.ValueExprContext) conjunct).expression()).literal();
        return literal.FALSE() != null || literal.NULL() != null;
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
            final ParserRuleContext key = KeyContexts.of(element);
            final ParseTree grouped = key != null ? groupedItem(clause, key) : element;
            if (readsVaryingOuter(grouped, scopes)) {
                return true;
            }
        }
        return false;
    }

    /** What a grouping expression groups by: the item an ordinal or a select alias names, else itself. */
    static ParseTree groupedItem(final FrostlakeParser.SelectClauseContext clause, final ParserRuleContext grouping) {
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
                            ((FrostlakeParser.ExprItemContext) item).aliasName()).toUpperCase(Locale.ROOT))) {
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
            if (KeyContexts.of(element) == null) {
                continue;
            }
            final ParseTree grouped = groupedItem(clause, KeyContexts.of(element));
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
                if (!readsDirectly(conjunct, CorrelationSide.OUTER, scopes) || settlement(conjunct) != null
                        || negatedMembership) {
                    continue;
                }
                if (!in || !comparesByEquality(conjunct, scopes)
                        || !semiJoin && !innerOuterEquality(conjunct, scopes)) {
                    return true;
                }
            }
            // Outside a WHERE's semi-join an item that turns the aggregate's NULL into a value is refused too:
            // IN (SELECT COALESCE(SUM(g.v), 0) FROM g WHERE g.id = fn.id) (live-verified).
            return in && !semiJoin && !negatedMembership && replacesAggregateNull(clause.selectList())
                && !filterConjuncts(clause).isEmpty() && readsDirectly(clause, CorrelationSide.OUTER, scopes);
        } finally {
            varyingOnly = before;
        }
    }

    /**
     * Whether a filter is one equality between the subquery's own side and the outer row: {@code g.id = fz.id}, not an
     * OR of such, nor an outer name against a constant, which only a WHERE's semi-join takes for an aggregate body
     * (live-verified).
     */
    private boolean innerOuterEquality(final FrostlakeParser.BooleanExprContext condition,
                                       final List<CorrelationScope> scopes) {
        if (!(condition instanceof FrostlakeParser.ValueExprContext)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) condition).expression();
        if (value instanceof FrostlakeParser.ParenExprContext) {
            return innerOuterEquality(((FrostlakeParser.ParenExprContext) value).booleanExpr(), scopes);
        }
        if (!(value instanceof FrostlakeParser.ComparisonExprContext)
                || ((FrostlakeParser.ComparisonExprContext) value).op.getType() != FrostlakeLexer.EQ) {
            return false;
        }
        final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext) value;
        final ParseTree left = comparison.expression(0);
        final ParseTree right = comparison.expression(1);
        return readsDirectly(left, CorrelationSide.INNER, scopes)
                && readsDirectly(right, CorrelationSide.OUTER, scopes)
            || readsDirectly(left, CorrelationSide.OUTER, scopes)
                && readsDirectly(right, CorrelationSide.INNER, scopes);
    }

    /** Whether a select list holds a COALESCE, NVL, IFNULL or ZEROIFNULL over an aggregate call. */
    private boolean replacesAggregateNull(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String name = ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText()
                .toUpperCase(Locale.ROOT);
            if (("COALESCE".equals(name) || "NVL".equals(name) || "IFNULL".equals(name) || "ZEROIFNULL".equals(name))
                    && !aggregateCalls(node).isEmpty()) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (replacesAggregateNull(node.getChild(i))) {
                return true;
            }
        }
        return false;
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
            into.append(node.getText().toUpperCase(Locale.ROOT)).append(' ');
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
            final String name = relation.getKey().toUpperCase(Locale.ROOT);
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

    /**
     * The outer names the table functions of a subquery's FROM read, in the order written; empty where none
     * reads the outer row (see {@link #readsOuterThroughTableFunction}).
     *
     * @param subquery the subquery
     * @return the references, each naming a column of the query around the subquery
     */
    public List<FrostlakeParser.QualifiedNameExprContext> tableFunctionOuterReads(
            final FrostlakeParser.SelectStatementContext subquery) {
        final List<FrostlakeParser.QualifiedNameExprContext> reads = new ArrayList<>();
        final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
        if (clause == null || clause.tableExpression() == null) {
            return reads;
        }
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        final List<FrostlakeParser.TableReferenceContext> relations = new ArrayList<>();
        collectRelations(clause.tableExpression(), relations);
        for (final FrostlakeParser.TableReferenceContext relation : relations) {
            final FrostlakeParser.TableSourceContext source = relation.tableSource();
            if (source != null
                    && (source.TABLE() != null || source.FLATTEN() != null || source.tableFunctionExpr() != null)) {
                collectDirectReads(source, scopes, reads);
            }
        }
        return reads;
    }

    private void collectDirectReads(final ParseTree node, final List<CorrelationScope> scopes,
                                    final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            if (sideOf((FrostlakeParser.QualifiedNameExprContext) node, scopes) == CorrelationSide.OUTER) {
                into.add((FrostlakeParser.QualifiedNameExprContext) node);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectDirectReads(node.getChild(i), scopes, into);
        }
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
     *                         select list the subquery refuses that one itself first — ahead of the names an
     *                         aggregate of its select list reads, too — and in HAVING the grouping speaks
     * @return the references, each still to be placed in that query, in the order written
     */
    public List<FrostlakeParser.QualifiedNameExprContext> enclosingReferencesWithMixedAggregates(
            final FrostlakeParser.SelectStatementContext subquery, final boolean filterAggregates) {
        final List<FrostlakeParser.QualifiedNameExprContext> references = new ArrayList<>();
        collectEnclosingReferences(subquery, new ArrayList<CorrelationScope>(), references, true, filterAggregates,
            false);
        if (!filterAggregates) {
            final List<FrostlakeParser.QualifiedNameExprContext> filtered = new ArrayList<>();
            collectEnclosingReferences(subquery, new ArrayList<CorrelationScope>(), filtered, true, true, false);
            if (filtered.size() > references.size()) {
                // An aggregate of the subquery's filter reads both rows: its refusal is the subquery's own.
                return enclosingReferences(subquery);
            }
        }
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

    /**
     * Where a subquery's names lead, read against the names it is offered and nothing else: INNER when every
     * name in it is its own, OUTER when it reads an offered name and every other is its own, UNKNOWN when a name
     * is neither — or is a bare one among relations whose columns are not known here, which may be anyone's.
     *
     * @param subquery the subquery
     * @return the side its names lead to
     */
    public CorrelationSide reach(final FrostlakeParser.SelectStatementContext subquery) {
        final boolean before = certainOnly;
        certainOnly = true;
        try {
            sawOuter = false;
            sawUnknown = false;
            walk(subquery, new ArrayList<CorrelationScope>());
            return sawUnknown ? CorrelationSide.UNKNOWN : sawOuter ? CorrelationSide.OUTER : CorrelationSide.INNER;
        } finally {
            certainOnly = before;
        }
    }

    /** Whether the subquery reads an outer name and every name in it could be placed. */
    private boolean judgeable(final FrostlakeParser.SelectStatementContext subquery) {
        sawOuter = false;
        sawUnknown = false;
        walk(subquery, new ArrayList<CorrelationScope>());
        return sawOuter && !sawUnknown;
    }

    /** {@link #judgeable}, with the depth the outer row is read from measured for a subquery it judges. */
    private boolean judgeableWithDepth(final FrostlakeParser.SelectStatementContext subquery) {
        deepestOuterRead = 0;
        if (!judgeable(subquery)) {
            return false;
        }
        walked = subquery;
        deepestOuterRead = outerDepthIn(subquery, new ArrayList<CorrelationScope>(), 0);
        return true;
    }

    /**
     * The deepest level a read of the outer row stands at below {@code node}. A query nested in the subquery
     * that the statistics settle is left out — one they prove reads no row, or one whose filter they prove true
     * and that reads the outer row nowhere else — since nothing of its correlation is left to turn.
     */
    private int outerDepthIn(final ParseTree node, final List<CorrelationScope> scopes, final int level) {
        if (node instanceof FrostlakeParser.LambdaFunctionContext) {
            return 0;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return sideOf((FrostlakeParser.QualifiedNameExprContext) node, scopes) == CorrelationSide.OUTER ? level : 0;
        }
        int deepest = 0;
        if (node instanceof FrostlakeParser.SelectClauseContext) {
            final FrostlakeParser.SelectClauseContext clause = (FrostlakeParser.SelectClauseContext) node;
            final boolean ownLevel = isLevel(clause);
            if (ownLevel && level >= 1 && settledAway(clause, scopes)) {
                return 0;
            }
            scopes.add(scopeOf(clause));
            for (int i = 0; i < node.getChildCount(); i++) {
                deepest = Math.max(deepest, outerDepthIn(node.getChild(i), scopes, level + (ownLevel ? 1 : 0)));
            }
            scopes.remove(scopes.size() - 1);
            return deepest;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // The statement's own ORDER BY reads the names of the select it orders, at that select's level.
            final FrostlakeParser.SelectStatementContext statement = (FrostlakeParser.SelectStatementContext) node;
            final FrostlakeParser.SelectClauseContext ordered = mainClause(statement);
            for (int i = 0; i < node.getChildCount(); i++) {
                final ParseTree child = node.getChild(i);
                if (child == statement.orderByClause() && ordered != null) {
                    scopes.add(scopeOf(ordered));
                    deepest = Math.max(deepest, outerDepthIn(child, scopes, level + (isLevel(ordered) ? 1 : 0)));
                    scopes.remove(scopes.size() - 1);
                } else {
                    deepest = Math.max(deepest, outerDepthIn(child, scopes, level));
                }
            }
            return deepest;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            deepest = Math.max(deepest, outerDepthIn(node.getChild(i), scopes, level));
        }
        return deepest;
    }

    /** Whether the statistics settle a nested query's correlation away (see {@link #outerDepthIn}). */
    private boolean settledAway(final FrostlakeParser.SelectClauseContext clause, final List<CorrelationScope> scopes) {
        if (prunedAway(clause)) {
            return true;
        }
        if (clause.whereClause() == null) {
            return false;
        }
        ParseTree statement = clause.getParent();
        while (statement != null && !(statement instanceof FrostlakeParser.SelectStatementContext)) {
            statement = statement.getParent();
        }
        final List<CorrelationScope> own = new ArrayList<>(scopes);
        own.add(scopeOf(clause));
        return Boolean.TRUE.equals(settlement(clause.whereClause().booleanExpr()))
            && !readsOuterBeyond((FrostlakeParser.SelectStatementContext) statement, clause, own, clause.whereClause());
    }

    /**
     * How many queries with a FROM of their own lie between the outer row and the deepest place the subquery last
     * judged reads it from: 1 for a subquery that reads it itself, 2 for one whose own subquery does. A FROM-less
     * select is no level — it is an expression of the query around it — and neither is a derived table. Live
     * turns a correlation one level at a time, so a read from two levels in is refused whatever the shape
     * (live-verified: {@code (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 WHERE g2.id <= fz.id))}).
     *
     * @return the depth, 0 where the outer row is read from no query with a FROM
     */
    public int outerReadDepth() {
        return deepestOuterRead;
    }

    /** Whether a select clause is a level of its own: it reads relations, as a subquery and not as a relation. */
    private boolean isLevel(final FrostlakeParser.SelectClauseContext clause) {
        if (clause.FROM() == null) {
            return false;
        }
        ParseTree at = clause.getParent();
        while (at != null && at != walked
                && (at instanceof FrostlakeParser.SelectOperandContext
                    || at instanceof FrostlakeParser.SelectStatementContext)) {
            at = at.getParent();
        }
        return at == walked || at instanceof FrostlakeParser.ScalarSubqueryExprContext
            || at instanceof FrostlakeParser.ExistsExprContext || at instanceof FrostlakeParser.InSubqueryExprContext
            || at instanceof FrostlakeParser.TupleInSubqueryExprContext
            || at instanceof FrostlakeParser.FunctionArgContext;
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
     * Whether an outer name stands where the statistics prove no row ever reads it: an argument of COALESCE, NVL
     * or IFNULL after one that holds no NULL, NVL2's third argument beside a first that holds none, and the
     * branch of IFF or of a searched CASE that a settled condition leaves out. Live reduces such a call before it
     * plans the correlation, so the name correlates nothing: over a g.id holding no NULL,
     * {@code COALESCE(g.id, fz.id) = 5} is answered where {@code GREATEST(g.id, fz.id) = 5} is refused
     * (live-verified).
     *
     * <p>So does a name inside a condition the statistics settle, wherever the condition stands — a disjunct or
     * a conjunct of a filter, a select item, a call's argument — and an element of an IN list that the subject
     * never equals: over tt.id holding 1 and 2 and fz.id holding 5 and 7, {@code tt.id = fz.id OR tt.id = 1},
     * {@code tt.id < fz.id AND tt.id = 1}, {@code tt.id IN (fz.id, 1)} and {@code IFF(tt.id = fz.id, 'x', value)}
     * read tt alone (live-verified). A condition is folded so only where {@link #foldsWhereItStands} lets it.
     */
    private boolean neverReached(final ParseTree reference) {
        if (!judging || readsAsWritten) {
            return false;
        }
        ParseTree below = null;
        ParseTree child = reference;
        FrostlakeParser.FunctionArgContext argument = null;
        for (ParseTree at = reference.getParent(); at != null; below = child, child = at, at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectClauseContext || at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
            if (isCondition(at) && settledBranchCondition((ParserRuleContext) at) != null && foldsWhereItStands(at)) {
                return true;
            }
            if (at instanceof FrostlakeParser.InListExprContext
                    && child == ((FrostlakeParser.InListExprContext) at).expressionList()
                    && below instanceof ParserRuleContext
                    && neverListed((FrostlakeParser.InListExprContext) at, (ParserRuleContext) below)
                    && foldsWhereItStands(at) && (foldingInAggregates || !insideAggregateCall(at))) {
                // An element dropped from a list inside an aggregate's argument is written out of the body the
                // subquery runs (see collectSettledInAggregates).
                return true;
            }
            if (at instanceof FrostlakeParser.FunctionArgContext) {
                argument = (FrostlakeParser.FunctionArgContext) at;
            } else if (at instanceof FrostlakeParser.FunctionCallExprContext) {
                if (argument != null && unreachedArgument((FrostlakeParser.FunctionCallExprContext) at, argument)) {
                    return true;
                }
                argument = null;
            } else if (at instanceof FrostlakeParser.SearchedCaseExprContext
                    && unreachedBranch((FrostlakeParser.SearchedCaseExprContext) at, child, below)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a settled condition is folded where it stands: in a select with no outer join, whose NULL-extended rows
     * the statistics do not describe — an outer join's ON reading the outer row stays refused — and, but for a
     * subquery read as a value, outside every aggregate call of its select, which is computed over the select's rows
     * alone. A value's subquery folds it inside an aggregate's argument as well: {@code (SELECT MAX(IFF(tt.id = fz.id,
     * 'x', value)) FROM tt)} over key ranges that never meet is answered (live-verified), and runs with the
     * condition written as its value (see {@link #statisticsBodyText}).
     */
    private boolean foldsWhereItStands(final ParseTree node) {
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectClauseContext) {
                return !joinsOuter((FrostlakeParser.SelectClauseContext) at);
            }
            if (at instanceof FrostlakeParser.SelectStatementContext || isAggregateCall(at) && !foldingInAggregates) {
                return false;
            }
        }
        return false;
    }

    /** Whether a node stands inside an aggregate call of its own select. */
    private boolean insideAggregateCall(final ParseTree node) {
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectClauseContext || at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
            if (isAggregateCall(at)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a node is a condition the statistics may settle: a comparison, a test, or a connective of them. */
    private static boolean isCondition(final ParseTree node) {
        return node instanceof FrostlakeParser.ComparisonExprContext || node instanceof FrostlakeParser.BetweenExprContext
            || node instanceof FrostlakeParser.IsNullExprContext || node instanceof FrostlakeParser.IsDistinctExprContext
            || node instanceof FrostlakeParser.InListExprContext || node instanceof FrostlakeParser.LikeExprContext
            || node instanceof FrostlakeParser.AndExprContext || node instanceof FrostlakeParser.OrExprContext
            || node instanceof FrostlakeParser.NotExprContext;
    }

    /** Whether the statistics prove an IN list's subject never equals one of its elements, which then drops out. */
    private boolean neverListed(final FrostlakeParser.InListExprContext list, final ParserRuleContext element) {
        if (list.expression() == null || !aggregateCalls(list).isEmpty()) {
            return false;
        }
        return Boolean.FALSE.equals(fold().settles("(" + ParseTreeText.getOriginalText(list.expression()) + ") = ("
            + ParseTreeText.getOriginalText(element) + ")"));
    }

    private boolean unreachedArgument(final FrostlakeParser.FunctionCallExprContext call,
                                      final FrostlakeParser.FunctionArgContext argument) {
        if (call.functionArgList() == null || call.overClause() != null) {
            return false;
        }
        final List<FrostlakeParser.FunctionArgContext> arguments = call.functionArgList().functionArg();
        final int index = arguments.indexOf(argument);
        final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
        if ("COALESCE".equals(name) || "NVL".equals(name) || "IFNULL".equals(name)) {
            for (int i = 0; i < index; i++) {
                if (holdsNoNull(arguments.get(i))) {
                    return true;
                }
            }
            return false;
        }
        if ("NVL2".equals(name)) {
            return index == 2 && arguments.size() == 3 && holdsNoNull(arguments.get(0));
        }
        if ("IFF".equals(name) && arguments.size() == 3 && index > 0) {
            final Boolean settled = settledBranchCondition(arguments.get(0));
            return settled != null && settled.booleanValue() == (index == 2);
        }
        return false;
    }

    /** Whether the place {@code child} holds in a searched CASE is one a settled condition leaves out. */
    private boolean unreachedBranch(final FrostlakeParser.SearchedCaseExprContext conditional, final ParseTree child,
                                    final ParseTree below) {
        for (final FrostlakeParser.WhenClauseContext when : conditional.whenClause()) {
            final Boolean settled = settledBranchCondition(when.booleanExpr(0));
            if (when == child) {
                // Its own result is left out by a condition that never holds.
                return below == when.booleanExpr(1) && Boolean.FALSE.equals(settled);
            }
            if (Boolean.TRUE.equals(settled)) {
                return true;   // every row is answered here, before the place asked about
            }
            if (settled == null) {
                return false;  // some row may pass this one by, and reach the place
            }
        }
        return false;
    }

    /** How the statistics settle a branch condition: an IS [NOT] NULL over an operand that holds none, or a filter. */
    private Boolean settledBranchCondition(final ParserRuleContext condition) {
        final List<FrostlakeParser.IsNullExprContext> tests = new ArrayList<>();
        collectWhole(condition, tests);
        if (!tests.isEmpty()) {
            final FrostlakeParser.IsNullExprContext test = tests.get(0);
            return holdsNoNull(test.expression()) ? Boolean.valueOf(test.NOT() != null) : null;
        }
        return settlement(condition);
    }

    /** The IS [NOT] NULL test a condition is as a whole, through the wrappers a lone expression stands in. */
    private static void collectWhole(final ParseTree node, final List<FrostlakeParser.IsNullExprContext> into) {
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            into.add((FrostlakeParser.IsNullExprContext) node);
            return;
        }
        if (node.getChildCount() == 1) {
            collectWhole(node.getChild(0), into);
        }
    }

    /**
     * Whether the statistics prove an expression never answers NULL: its interval, or for a column of a family the
     * interval channel does not model, a stored column none of whose rows is NULL.
     */
    private boolean holdsNoNull(final ParserRuleContext expression) {
        final ValueRange range = fold().rangeOf(ParseTreeText.getOriginalText(expression));
        if (range != null) {
            return !range.isNullable();
        }
        ParseTree bare = expression;
        while (!(bare instanceof FrostlakeParser.QualifiedNameExprContext) && bare.getChildCount() == 1) {
            bare = bare.getChild(0);
        }
        if (!(bare instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return false;
        }
        final List<String> parts = partsOf(((FrostlakeParser.QualifiedNameExprContext) bare).qualifiedName());
        final String column = parts.get(parts.size() - 1);
        final Table owner = parts.size() == 2 ? relationNamed(parts.get(0)) : relationCarrying(column);
        return owner != null && Boolean.FALSE.equals(new StoredColumnValues(executor).holdsNull(owner, column));
    }

    /** The relation of the subquery's own or of the outer query a qualifier names, or null. */
    private Table relationNamed(final String qualifier) {
        final String key = qualifier.toUpperCase(Locale.ROOT);
        return innerRelations.containsKey(key) ? innerRelations.get(key) : outerRelations.get(key);
    }

    /** The one relation of the subquery's own, else of the outer query, carrying a column; null where none or two. */
    private Table relationCarrying(final String column) {
        Table owner = null;
        for (final Table relation : innerRelations.values()) {
            if (relation.hasColumn(column) && relation != owner) {
                if (owner != null) {
                    return null;
                }
                owner = relation;
            }
        }
        return owner;
    }

    /**
     * Where a reference resolves: the innermost scope that carries it, else the outer row, else nowhere. While a
     * shape is judged, a derived column computed from the outer row reads the outer row, and an outer name the
     * statistics pin to one value reads a constant.
     */
    private CorrelationSide sideOf(final FrostlakeParser.QualifiedNameExprContext reference,
                                   final List<CorrelationScope> scopes) {
        final CorrelationSide side = placedSideOf(reference, scopes);
        // The outer query's own aggregate in an EXISTS's select list is a value its rows are not counted by, and
        // one the outer table's statistics answer is a value whatever row is read; so is a number converted to the
        // BOOLEAN they settle.
        return side == CorrelationSide.OUTER
            && (insideCountedAggregate(reference, scopes) || insideStatisticsCall(reference)
                || convertedToSettledBoolean(reference, scopes))
            ? CorrelationSide.CONSTANT : side;
    }

    /**
     * Whether an outer number stands converted to a BOOLEAN its relation's statistics settle (see
     * {@link OuterNameConstancy#settledBoolean}): {@code fz.id::BOOLEAN} over a column holding no zero and no NULL is
     * TRUE in the plan, and reads nothing of the outer row, where over one holding a zero or a NULL it stays
     * {@code CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN)} (live-verified). Inside an aggregate over the outer row alone
     * it is no correlation at all: the aggregate is the outer query's, and its nesting is refused, settled or not.
     */
    private boolean convertedToSettledBoolean(final FrostlakeParser.QualifiedNameExprContext reference,
                                              final List<CorrelationScope> scopes) {
        if (!judging || constancy == null || !BooleanConversions.convertsToBoolean(reference)) {
            return false;
        }
        for (ParserRuleContext at = reference.getParent(); at != null
                && !(at instanceof FrostlakeParser.SelectStatementContext); at = at.getParent()) {
            if (isAggregateCall(at)) {
                if (readsOuterRowAlone(at, scopes)) {
                    return false;
                }
                break;
            }
        }
        final List<String> parts = partsOf(reference.qualifiedName());
        return constancy.settledBoolean(parts.size() > 1 ? parts.get(parts.size() - 2) : null,
            parts.get(parts.size() - 1)) != null;
    }

    private CorrelationSide placedSideOf(final FrostlakeParser.QualifiedNameExprContext reference,
                                         final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        if (parts.size() == 1) {
            if (parametersWin && fixedNames.contains(column)) {
                return CorrelationSide.CONSTANT;
            }
            if (parametersWin && outerNames.contains(column)) {
                return constant(null, column) || neverReached(reference)
                    ? CorrelationSide.CONSTANT : CorrelationSide.OUTER;
            }
            for (int i = scopes.size() - 1; i >= 0; i--) {
                if (scopes.get(i).resolvesBare(column)) {
                    if (certainOnly && !scopes.get(i).holdsBare(column)) {
                        return CorrelationSide.UNKNOWN;
                    }
                    return judging && scopes.get(i).outerDerivedReads(null, column) != null
                        ? CorrelationSide.OUTER : CorrelationSide.INNER;
                }
            }
            if (fixedNames.contains(column)) {
                return CorrelationSide.CONSTANT;
            }
            if (!outerNames.contains(column)) {
                return CorrelationSide.UNKNOWN;
            }
            return constant(null, column) || neverReached(reference)
                ? CorrelationSide.CONSTANT : CorrelationSide.OUTER;
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
        return constant(relation, column) || neverReached(reference)
            ? CorrelationSide.CONSTANT : CorrelationSide.OUTER;
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
        return SqlIdentifiers.canonicalText(written).toUpperCase(Locale.ROOT);
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
                    scope.addAlias(ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase(Locale.ROOT));
                } else if (exprItem.identifier() != null) {
                    scope.addAlias(ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase(Locale.ROOT));
                } else if (exprItem.bareAliasWord() != null) {
                    scope.addAlias(ParseTreeText.getIdentifier(exprItem.bareAliasWord()));
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
            final Table aliased = innerRelations.put(alias != null ? alias.toUpperCase(Locale.ROOT) : table.getName().toUpperCase(Locale.ROOT), table);
            final Table named = innerRelations.put(table.getName().toUpperCase(Locale.ROOT), table);
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
        if (source != null && source.VALUES() != null && source.valueTupleList() != null) {
            addValuesColumns(scope, relation, source, alias);
            return;
        }
        if (source != null && isFlattenCall(source)) {
            addFlattenColumns(scope, relation, alias);
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
        if (relation.pivotClause() != null || relation.unpivotClause() != null || relation.matchRecognizeClause() != null) {
            scope.markOpaque();
            return;
        }
        final FrostlakeParser.CteDefinitionContext cte = CommonTableExpressions.named(source);
        final Table table = cte != null ? null : resolvedTable(name.getText());
        if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                scope.addColumn(column.getName().toUpperCase(Locale.ROOT));
            }
            return;
        }
        final List<String> named = cte != null ? cteColumns(cte) : viewColumns(name.getText());
        if (named == null) {
            scope.markOpaque();   // a name that does not resolve, or a relation a star hides the columns of
            return;
        }
        for (final String column : named) {
            scope.addColumn(column);
        }
        if (cte != null && judging) {
            // A CTE's column computed from the outer row reads the outer row wherever it is named, as a derived
            // table's does, its column list naming it or its query (live-verified).
            markOperandColumns(scope, alias != null ? alias : lastPart, named, cte.selectStatement());
        }
    }

    /** FLATTEN's columns, in the order an alias's column list renames them. */
    private static final List<String> FLATTEN_COLUMNS = List.of("SEQ", "KEY", "PATH", "INDEX", "VALUE", "THIS");

    /** Whether a FROM item is a FLATTEN call, bare or inside TABLE( … ). */
    private static boolean isFlattenCall(final FrostlakeParser.TableSourceContext source) {
        if (source.FLATTEN() != null) {
            return true;
        }
        if (source.TABLE() == null || source.expression() == null) {
            return false;
        }
        ParseTree node = source.expression();
        while (node.getChildCount() == 1 && node.getChild(0) instanceof ParserRuleContext) {
            node = node.getChild(0);
        }
        final FrostlakeParser.FunctionNameContext name = node instanceof FrostlakeParser.FunctionCallExprContext
            ? ((FrostlakeParser.FunctionCallExprContext) node).functionName()
            : node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext
                ? ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).functionName()
                : node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
                    ? ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).functionName() : null;
        return name != null && "FLATTEN".equals(canonical(name.getText()));
    }

    /**
     * FLATTEN's columns, SEQ, KEY, PATH, INDEX, VALUE and THIS, the first of them renamed by its alias's column list.
     * A name of none of them is no column of FLATTEN's, so {@code (SELECT MAX(v) FROM TABLE(FLATTEN(INPUT => [5,7])))}
     * reads the outer row's {@code v} (live-verified: the grouped query refuses it as an unsupported subquery).
     */
    private static void addFlattenColumns(final CorrelationScope scope,
                                          final FrostlakeParser.TableReferenceContext relation, final String alias) {
        if (alias != null) {
            scope.addQualifier(alias);
        }
        final List<String> renamed = new ArrayList<>();
        if (relation.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext name : relation.identifierList().identifier()) {
                renamed.add(ParseTreeText.getIdentifier(name).toUpperCase(Locale.ROOT));
            }
        }
        for (int i = 0; i < FLATTEN_COLUMNS.size(); i++) {
            scope.addColumn(i < renamed.size() ? renamed.get(i) : FLATTEN_COLUMNS.get(i));
        }
    }

    /**
     * The columns a VALUES list gives its rows: the column list its alias names, inside or after the parentheses, or
     * COLUMN1 … COLUMNn, one for each value of its first row. A name of none of them is no column of the list's, so
     * {@code (SELECT MAX(v) FROM (VALUES (5), (7)) x)} reads the outer row's {@code v} (live-verified: the grouped
     * query refuses it as an unsupported subquery).
     */
    private static void addValuesColumns(final CorrelationScope scope,
                                         final FrostlakeParser.TableReferenceContext relation,
                                         final FrostlakeParser.TableSourceContext source, final String alias) {
        if (alias != null) {
            scope.addQualifier(alias);
        }
        if (source.identifier() != null) {
            scope.addQualifier(ParseTreeText.getIdentifier(source.identifier()).toUpperCase(Locale.ROOT));
        }
        if (relation.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext name : relation.identifierList().identifier()) {
                scope.addColumn(ParseTreeText.getIdentifier(name).toUpperCase(Locale.ROOT));
            }
            return;
        }
        if (source.columnListOptional() != null) {
            for (final FrostlakeParser.NamePartContext part : source.columnListOptional().namePart()) {
                scope.addColumn(canonical(part.getText()));
            }
            return;
        }
        final int width = source.valueTupleList().valueTuple(0).valueList().booleanExpr().size();
        for (int i = 1; i <= width; i++) {
            scope.addColumn("COLUMN" + i);
        }
    }

    /**
     * The columns a CTE gives its rows: its column list, or the names its query's select list gives them (see
     * {@link #namesOfQuery}). A bare name among them is the CTE's own, as a table's column is the table's
     * (live-verified for a CTE and a view read by a subquery of a lateral call's WHERE).
     */
    private static List<String> cteColumns(final FrostlakeParser.CteDefinitionContext cte) {
        if (cte.columnListOptional() == null) {
            return namesOfQuery(cte.selectStatement());
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.NamePartContext part : cte.columnListOptional().namePart()) {
            names.add(ParseTreeText.namePartText(part).toUpperCase(Locale.ROOT));
        }
        return names;
    }

    /** The columns a view gives its rows: the list it was created with, or the names its query gives them. */
    private List<String> viewColumns(final String written) {
        final View view;
        try {
            view = catalog.resolveView(SqlIdentifiers.canonicalText(written));
        } catch (final RuntimeException notAView) {
            return null;
        }
        if (view == null) {
            return null;
        }
        if (view.getColumnNames() != null) {
            final List<String> names = new ArrayList<>();
            for (final String column : view.getColumnNames()) {
                names.add(column.toUpperCase(Locale.ROOT));
            }
            return names;
        }
        if (view.getDefinition() == null || executor == null) {
            return null;
        }
        try {
            return namesOfQuery(executor.subqueryStatement(view.getDefinition()));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The names a query's rows carry, as its first select names them - an item's alias, or the column an item
     * written as a name reads - or null where a star or an item of no name of its own hides one.
     */
    private static List<String> namesOfQuery(final FrostlakeParser.SelectStatementContext query) {
        if (query == null || query.selectOperand().isEmpty() || query.selectOperand(0).selectClause() == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item
                : query.selectOperand(0).selectClause().selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final FrostlakeParser.ExprItemContext exprItem = (FrostlakeParser.ExprItemContext) item;
            if (exprItem.aliasName() != null) {
                names.add(ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase(Locale.ROOT));
            } else if (exprItem.identifier() != null) {
                names.add(ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase(Locale.ROOT));
            } else if (exprItem.bareAliasWord() != null) {
                names.add(ParseTreeText.getIdentifier(exprItem.bareAliasWord()).toUpperCase(Locale.ROOT));
            } else {
                final FrostlakeParser.ExpressionContext value =
                    SelectItemAccessors.unwrapParens(SelectItemAccessors.getItemValueExpr(item));
                if (!(value instanceof FrostlakeParser.QualifiedNameExprContext)) {
                    return null;
                }
                final String[] parts = ParseTreeText.qualifiedNameParts(
                    ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
                if (parts.length == 0) {
                    return null;
                }
                names.add(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    /**
     * A derived table's column names: its column list when it has one, else each item's alias or, for a bare
     * column, the column's own name, a set operation's as its first operand names them. False when the names cannot
     * be read: a star. While a shape is judged, a column whose item reads the outer row — in any operand of a set
     * operation — is recorded with the outer columns it reads.
     */
    private boolean addDerivedColumns(final CorrelationScope scope, final FrostlakeParser.TableReferenceContext relation,
                                      final FrostlakeParser.SelectStatementContext derived) {
        final FrostlakeParser.SelectClauseContext clause = mainClause(derived);
        final List<CorrelationScope> bodyScopes = judging && clause != null && relation.LATERAL() == null
            ? scopeOfAlone(clause) : null;
        final String alias = aliasOf(relation);
        if (relation.identifierList() != null) {
            final List<FrostlakeParser.IdentifierContext> names = relation.identifierList().identifier();
            final List<String> named = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                final String name = ParseTreeText.getIdentifier(names.get(i)).toUpperCase(Locale.ROOT);
                named.add(name);
                scope.addColumn(name);
                if (bodyScopes != null && i < clause.selectList().selectItem().size()) {
                    markOuterDerived(scope, alias, name, clause.selectList().selectItem(i), bodyScopes);
                }
            }
            if (clause == null && judging && relation.LATERAL() == null) {
                markOperandColumns(scope, alias, named, derived);
            }
            return true;
        }
        if (clause == null) {
            return addSetOperationColumns(scope, alias, derived, judging && relation.LATERAL() == null);
        }
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final FrostlakeParser.ExprItemContext exprItem = (FrostlakeParser.ExprItemContext) item;
            String name = null;
            if (exprItem.aliasName() != null) {
                name = ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase(Locale.ROOT);
            } else if (exprItem.identifier() != null) {
                name = ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase(Locale.ROOT);
            } else if (exprItem.bareAliasWord() != null) {
                name = ParseTreeText.getIdentifier(exprItem.bareAliasWord());
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

    /**
     * A derived set operation's columns, as its first operand names them, each recorded as reading the outer row where
     * the item at its place in any operand does: under MIN, MAX or COUNT of such a column the subquery is refused,
     * under COUNT(*) answered (live-verified). False when the names cannot be read.
     *
     * @param marks whether a shape is being judged, so the columns reading the outer row are recorded
     */
    private boolean addSetOperationColumns(final CorrelationScope scope, final String alias,
                                           final FrostlakeParser.SelectStatementContext derived, final boolean marks) {
        final List<String> names = namesOfQuery(derived);
        if (names == null) {
            return false;
        }
        for (final String name : names) {
            scope.addColumn(name);
        }
        if (marks) {
            markOperandColumns(scope, alias, names, derived);
        }
        return true;
    }

    /**
     * Record each of a query's columns, named by {@code names} in order, as reading the outer row where the item at its
     * place in any operand of the query does.
     */
    private void markOperandColumns(final CorrelationScope scope, final String qualifier, final List<String> names,
                                    final FrostlakeParser.SelectStatementContext query) {
        final List<FrostlakeParser.SelectClauseContext> operands = new ArrayList<>();
        final List<FrostlakeParser.SelectStatementContext> chain = statementChain(query);
        collectOperandClauses(SetOperationTree.of(chain.get(chain.size() - 1)), operands);
        for (final FrostlakeParser.SelectClauseContext operand : operands) {
            for (int i = 0; i < names.size() && i < operand.selectList().selectItem().size(); i++) {
                markOuterDerived(scope, qualifier, names.get(i), operand.selectList().selectItem(i),
                    scopeOfAlone(operand));
            }
        }
    }

    /** Every select clause a set operation's operands are, nested operations opened. */
    private static void collectOperandClauses(final SetOperationTree node,
                                              final List<FrostlakeParser.SelectClauseContext> into) {
        if (!node.isLeaf()) {
            collectOperandClauses(node.left(), into);
            collectOperandClauses(node.right(), into);
            return;
        }
        final FrostlakeParser.SelectOperandContext operand = node.operand();
        if (operand.selectClause() != null) {
            into.add(operand.selectClause());
            return;
        }
        if (operand.selectStatement() != null) {
            final List<FrostlakeParser.SelectStatementContext> chain = statementChain(operand.selectStatement());
            collectOperandClauses(SetOperationTree.of(chain.get(chain.size() - 1)), into);
        }
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
            return ParseTreeText.getIdentifier(relation.aliasName()).toUpperCase(Locale.ROOT);
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
            if (!countedCalls.contains(node) && !statisticsCalls.contains(node)) {
                into.add((ParserRuleContext) node);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAggregateCalls(node.getChild(i), into);
        }
    }

    private boolean isAggregateCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            return call.overClause() == null && isAggregateName(call.functionName()) && choosesAggregate(call);
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
        return functions.hasAggregateFunction(name.getText().toUpperCase(Locale.ROOT));
    }

    /**
     * Whether a call of a name that holds scalar overloads beside aggregate ones calls the aggregate: its arguments,
     * typed over the subquery's own relations and then the outer row's, choose as the call does (see
     * QueryExecutor#callChoosesAggregate). Over {@code mix(x INT)} and an aggregate {@code mix(x VARCHAR)},
     * {@code (SELECT mix(t2.v) FROM t t2 WHERE t2.v = t.v)} computes a scalar for each row, so it promises no one
     * row and is refused, while {@code (SELECT mix(t.v))} is the outer row's own scalar and is answered
     * (live-verified). Any other name finds no choice and calls its aggregate.
     */
    private boolean choosesAggregate(final FrostlakeParser.FunctionCallExprContext call) {
        if (!functions.namesScalarBesideAggregate(SqlIdentifiers.canonicalText(call.functionName().getText()))) {
            return true;
        }
        ParseTree up = call.getParent();
        while (up != null && !(up instanceof FrostlakeParser.SelectClauseContext)) {
            up = up.getParent();
        }
        if (up != null) {
            rememberRelations((FrostlakeParser.SelectClauseContext) up);
        }
        final Map<String, Table> relations = new HashMap<>(outerRelations);
        relations.putAll(innerRelations);
        final Map<String, Table> named = innerRelations.isEmpty() ? outerRelations : innerRelations;
        final Set<Table> distinct = new HashSet<>(named.values());
        final CallTypingScope enclosing = executor.beginCallTyping(
            distinct.size() == 1 ? distinct.iterator().next() : null, relations);
        try {
            return executor.callChoosesAggregate(call);
        } finally {
            executor.endCallTyping(enclosing);
        }
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

    /**
     * Whether an aggregate of the select list reads the outer row and a column of the subquery's own in one
     * argument list — {@code MAX(fz.id + g.v)}; {@code SUM(fz.id)} reads the outer row alone.
     */
    private boolean mixesSidesInsideAggregate(final ParseTree selectList, final List<CorrelationScope> scopes) {
        for (final ParserRuleContext call : aggregateCalls(selectList)) {
            boolean outer = false;
            boolean inner = false;
            for (int i = 0; i < call.getChildCount(); i++) {
                if (call.getChild(i) instanceof FrostlakeParser.FunctionNameContext) {
                    continue;
                }
                outer = outer || readsDirectly(call.getChild(i), CorrelationSide.OUTER, scopes);
                inner = inner || readsDirectly(call.getChild(i), CorrelationSide.INNER, scopes);
            }
            if (outer && inner) {
                return true;
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
        if (relation.LATERAL() != null || relation.pivotClause() != null || relation.unpivotClause() != null || relation.matchRecognizeClause() != null
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
        final String name = function.functionName().getText().toUpperCase(Locale.ROOT);
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
        aliases.put(alias != null ? alias : table.getName().toUpperCase(Locale.ROOT), table);
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
        if (condition instanceof FrostlakeParser.RowComparisonExprContext) {
            return rowComparisonSupported((FrostlakeParser.RowComparisonExprContext) condition, scopes);
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
     * Whether a comparison of two rows is answered: it reads as its pairs, each a comparison held to the rules a
     * comparison is — every operand on one side, the outer row read on one side of a pair at most —
     * {@code (u.a, u.b) = (t.a, t.b)} and {@code (u.a, t.b) = (t.a, u.b)} are answered, for every operator, and
     * {@code (u.a, u.b) = (t.a + u.a, t.b)} is refused (live-verified). Rows of different widths are refused where
     * the comparison is typed.
     */
    private boolean rowComparisonSupported(final FrostlakeParser.RowComparisonExprContext comparison,
                                           final List<CorrelationScope> scopes) {
        final List<FrostlakeParser.ExpressionContext> left = comparison.left.expression();
        final List<FrostlakeParser.ExpressionContext> right = comparison.right.expression();
        if (left.size() != right.size()) {
            return true;
        }
        for (int i = 0; i < left.size(); i++) {
            final List<FrostlakeParser.ExpressionContext> pair = new ArrayList<>();
            pair.add(left.get(i));
            pair.add(right.get(i));
            if (!allOneSided(pair, scopes)) {
                return false;
            }
        }
        // As for a comparison, only the outer names the statistics show varying count against a pair.
        final boolean before = varyingOnly;
        varyingOnly = true;
        try {
            for (int i = 0; i < left.size(); i++) {
                if (readsDirectly(left.get(i), CorrelationSide.OUTER, scopes)
                        && readsDirectly(right.get(i), CorrelationSide.OUTER, scopes)) {
                    return false;
                }
            }
            return true;
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
                && relation.pivotClause() == null && relation.unpivotClause() == null && relation.matchRecognizeClause() == null;
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
            if (table == null || relation.pivotClause() != null || relation.unpivotClause() != null || relation.matchRecognizeClause() != null) {
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
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(call))).toUpperCase(Locale.ROOT);
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
