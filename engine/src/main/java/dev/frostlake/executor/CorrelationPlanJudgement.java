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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.PlannedCorrelations;
import dev.frostlake.executor.expressions.SubqueryAnchor;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.values.RelationStatistics;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The correlated subqueries live refuses as it plans a statement, before any row exists. Live judges a
 * correlated subquery (see {@link CorrelatedSubqueryRule}) when it compiles the statement, so a filter, a
 * LIMIT, a HAVING or a QUALIFY that leaves no row for the subquery to read does not spare it:
 * {@code SELECT id, (SELECT v FROM g WHERE g.id = fz.id) FROM fz WHERE 1 = 0} is refused, and so are
 * {@code … LIMIT 0} and {@code … WHERE id = 6} over ids 5 and 7. Only a scan the planner prunes first
 * spares it:
 *
 * <ul>
 *   <li>a WHERE holding a {@code FALSE} or {@code NULL} literal among its top-level conjuncts;</li>
 *   <li>a WHERE conjunct, or an inner join's ON conjunct, that reads the columns of one table and that the
 *       table's statistics prove false of every row (see {@link CountStatisticsBound}):
 *       {@code WHERE id > 100}, but neither {@code WHERE 1 = 0}, which reads no column, nor
 *       {@code WHERE id = 6}, which the least and the greatest id cannot settle;</li>
 *   <li>tables of at most one row each, which live reads row by row instead of planning a join.</li>
 * </ul>
 *
 * <p>An outer join's NULL-extended rows are not the table's, so beside one only the literal rule prunes. A
 * WHERE whose own constant conjunct folds to FALSE or NULL ({@code 1 = 0 AND …}) keeps the select list's
 * subqueries but drops the ones it holds itself. The refusal names the first refused subquery in clause
 * order — the select list, the WHERE, HAVING, QUALIFY, ORDER BY — and waits for the rest of the statement:
 * live reports it after a predicate's type, an argument's type or another subquery's own refusal. A pruned
 * WHERE holding a subquery is not evaluated at all, since no row passes it.
 *
 * <p>A subquery reading the outer row through a table function is spared by neither: live reads it row by
 * row only over a relation of exactly one row whose scan it does not prune, so over an empty table, and
 * under {@code WHERE FALSE} or {@code WHERE id > 100} over a one-row one, it is refused.
 *
 * <p>A statement over catalog tables is judged here, and one over a derived relation — a subquery, a CTE or
 * a view — that passes the columns of one catalog table through, which live plans as the query over that
 * table: {@code … FROM (SELECT * FROM fz WHERE 1 = 0) fz} and {@code … FROM (SELECT * FROM fz LIMIT 0) fz}
 * are refused. The derived relation's own WHERE prunes as the statement's does, and over one the statement's
 * constant conjunct folding to FALSE and its {@code LIMIT 0} prune too — the latter while the relation has
 * no limit of its own (live-verified). Any other derived relation, a table function or a query run for an
 * outer row keeps the judgement a row makes when it first reaches the subquery.
 */
public final class CorrelationPlanJudgement {

    private static final Set<String> CONDITIONAL_CALLS = new HashSet<>(Arrays.asList(
        "COALESCE", "DECODE", "IFF", "IFNULL", "NVL", "NVL2"));

    /** What {@link #folded} answers for an operand that is no constant. */
    private static final Object NOT_CONSTANT = new Object();

    private final QueryExecutor executor;
    private final FrostlakeParser.SelectStatementContext statement;
    private final FrostlakeParser.SelectClauseContext clause;
    private final Table table;
    /** The FROM clause's relations, keyed by the name the query gives each, upper-cased. */
    private final Map<String, Table> relations;
    private final boolean outerJoined;
    /** The statement and the select of the derived relation the FROM holds, or null over catalog tables. */
    private final FrostlakeParser.SelectStatementContext bodyStatement;
    private final FrostlakeParser.SelectClauseContext body;
    /** The names the derived relation's own select gives its table, upper-cased; null over catalog tables. */
    private final Map<String, Table> bodyRelations;
    private Boolean pruned;
    private final Set<String> refusedKeys = new LinkedHashSet<>();

    private CorrelationPlanJudgement(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext statement,
                                     final FrostlakeParser.SelectClauseContext clause, final Table table,
                                     final Map<String, Table> relations, final boolean outerJoined,
                                     final FrostlakeParser.SelectStatementContext bodyStatement,
                                     final Map<String, Table> bodyRelations) {
        this.executor = executor;
        this.statement = statement;
        this.clause = clause;
        this.table = table;
        this.relations = relations;
        this.outerJoined = outerJoined;
        this.bodyStatement = bodyStatement;
        this.body = bodyStatement == null ? null : bodyStatement.selectOperand(0).selectClause();
        this.bodyRelations = bodyRelations;
    }

    /**
     * The judgement of a statement's select, or null where it does not apply: a query run for an outer row,
     * a relation body, a shape or a subquery being compiled, a FROM holding anything but catalog tables and
     * their joins, or no subquery to judge or to prune.
     *
     * @param relations the FROM clause's relations keyed by the name the query gives each, upper-cased
     */
    public static CorrelationPlanJudgement of(final QueryExecutor executor,
                                              final FrostlakeParser.SelectStatementContext statement,
                                              final FrostlakeParser.SelectClauseContext clause, final Table table,
                                              final Map<String, Table> relations,
                                              final Map<String, Object> lateralContext) {
        if (lateralContext != null || RelationBody.isActive() || RelationShapeOnly.isActive()
                || SubqueryCompilation.outerNames() != null || relations == null || relations.isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || !plainTables(from.tableReference())) {
            return null;
        }
        boolean outerJoined = false;
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            if (join.ASOF() != null || join.LATERAL() != null || join.asofMatchCondition() != null
                    || !plainTable(join.tableReference())) {
                return null;
            }
            outerJoined |= join.joinType() != null && (join.joinType().LEFT() != null
                || join.joinType().RIGHT() != null || join.joinType().FULL() != null);
        }
        if (from.tableReference().size() + from.joinClause().size() != new HashSet<>(relations.values()).size()) {
            return null;
        }
        for (final Table relation : relations.values()) {
            if (!relation.isCatalogResident()) {
                return null;
            }
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        if (where != null && holds(where.booleanExpr(), FrostlakeParser.OuterJoinColumnExprContext.class)) {
            return null;   // an Oracle (+) marker makes the WHERE an outer join's condition
        }
        final CorrelationPlanJudgement judgement =
            new CorrelationPlanJudgement(executor, statement, clause, table, relations, outerJoined, null, null);
        return judgement.judgedClauses().isEmpty()
                && (where == null || judgement.subqueries(where.booleanExpr()).isEmpty())
            ? null : judgement;
    }

    /**
     * The judgement of a select over one derived relation that passes the columns of one catalog table through
     * (see the class comment), or null where the FROM is anything else or there is no subquery to judge.
     *
     * @param relations the FROM clause's one relation keyed by the names the query gives it, upper-cased
     * @param cteNames  the names of the common table expressions in scope, which a name in the derived
     *                  relation's own FROM would read ahead of a table
     */
    public static CorrelationPlanJudgement ofDerived(final QueryExecutor executor,
                                                     final FrostlakeParser.SelectStatementContext statement,
                                                     final FrostlakeParser.SelectClauseContext clause,
                                                     final Map<String, Table> relations,
                                                     final Map<String, Object> lateralContext,
                                                     final Set<String> cteNames) {
        if (lateralContext != null || RelationBody.isActive() || RelationShapeOnly.isActive()
                || SubqueryCompilation.outerNames() != null || relations == null
                || new HashSet<>(relations.values()).size() != 1) {
            return null;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || reference.LATERAL() != null || reference.identifierList() != null
                || reference.pivotClause() != null || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                || reference.sampleClause() != null) {
            return null;
        }
        final Table derived = relations.values().iterator().next();
        if (derived == null || derived.isCatalogResident()) {
            return null;
        }
        // The body the relation runs: written in place, a common table expression of this statement, or a view's.
        final FrostlakeParser.SelectStatementContext written;
        boolean ownScope = false;
        if (source.selectStatement() != null) {
            written = source.selectStatement();
        } else if (source.tableQualifiedName() != null && source.timeTravelClause() == null) {
            final FrostlakeParser.SelectStatementContext common = commonBody(statement, source.tableQualifiedName());
            if (common != null) {
                written = common;
            } else {
                written = viewBody(executor, source.tableQualifiedName(), cteNames);
                ownScope = true;
            }
        } else {
            return null;
        }
        if (written == null || written.withClause() != null || written.selectOperand().size() != 1
                || written.selectOperand(0).selectClause() == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext inner = written.selectOperand(0).selectClause();
        final FrostlakeParser.TableExpressionContext innerFrom = inner.tableExpression();
        if (inner.groupByClause() != null || inner.havingClause() != null || inner.qualifyClause() != null
                || inner.connectByClause() != null || inner.intoClause() != null || innerFrom == null
                || innerFrom.tableReference().size() != 1 || !innerFrom.joinClause().isEmpty()
                || !plainTable(innerFrom.tableReference(0))) {
            return null;
        }
        final Table stored = passedTable(executor, derived, innerFrom.tableReference(0), cteNames, ownScope);
        if (stored == null || !passesColumnsThrough(inner, stored)) {
            return null;
        }
        final Map<String, Table> outer = new LinkedHashMap<>();
        for (final String name : relations.keySet()) {
            outer.put(name, stored);
        }
        final Map<String, Table> within = new LinkedHashMap<>();
        within.put(stored.getName().toUpperCase(), stored);
        final FrostlakeParser.TableReferenceContext innerReference = innerFrom.tableReference(0);
        if (innerReference.aliasName() != null) {
            within.put(ParseTreeText.getIdentifier(innerReference.aliasName()).toUpperCase(), stored);
        } else if (innerReference.nonJoinKeywordIdentifier() != null) {
            within.put(SqlIdentifiers.canonicalText(innerReference.nonJoinKeywordIdentifier().getText()), stored);
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        if (where != null && holds(where.booleanExpr(), FrostlakeParser.OuterJoinColumnExprContext.class)) {
            return null;
        }
        final CorrelationPlanJudgement judgement =
            new CorrelationPlanJudgement(executor, statement, clause, stored, outer, false, written, within);
        return judgement.judgedClauses().isEmpty() ? null : judgement;
    }

    /** The body of the common table expression of {@code statement} a FROM name reads, or null where it reads none. */
    private static FrostlakeParser.SelectStatementContext commonBody(final FrostlakeParser.SelectStatementContext statement,
                                                                    final FrostlakeParser.TableQualifiedNameContext name) {
        if (statement.withClause() == null || statement.withClause().RECURSIVE() != null) {
            return null;
        }
        final String read = SqlIdentifiers.canonicalText(name.getText());
        for (final FrostlakeParser.CteDefinitionContext common : statement.withClause().cteDefinition()) {
            if (common.columnListOptional() == null
                    && read.equals(SqlIdentifiers.canonicalText(common.nameStartPart().getText()))) {
                return common.selectStatement();
            }
        }
        return null;
    }

    /** The select a view runs, or null where the name reads no view — or a common table expression in scope. */
    private static FrostlakeParser.SelectStatementContext viewBody(final QueryExecutor executor,
                                                                  final FrostlakeParser.TableQualifiedNameContext name,
                                                                  final Set<String> cteNames) {
        final String read = SqlIdentifiers.canonicalText(name.getText());
        if (namesCommonExpression(read, cteNames)) {
            return null;
        }
        final View view;
        try {
            view = executor.getCatalog().resolveView(read);
        } catch (final RuntimeException notAView) {
            return null;
        }
        if (view == null || view.getDefinition() == null
                || view.getColumnNames() != null && !view.getColumnNames().isEmpty()) {
            return null;
        }
        return executor.subqueryStatement(view.getDefinition());
    }

    private static boolean namesCommonExpression(final String read, final Set<String> cteNames) {
        if (cteNames == null) {
            return false;
        }
        for (final String common : cteNames) {
            if (common != null && common.equalsIgnoreCase(read)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The catalog table a derived relation's select reads: the one the relation still carries the statistics of,
     * else — for a select written in the statement, whose names the session resolves — the table its FROM names.
     */
    private static Table passedTable(final QueryExecutor executor, final Table derived,
                                     final FrostlakeParser.TableReferenceContext reference, final Set<String> cteNames,
                                     final boolean ownScope) {
        final RelationStatistics kept = derived.getRelationStatistics();
        if (kept instanceof MergedRelationStatistics) {
            final Table source = ((MergedRelationStatistics) kept).sourceRelation();
            return source != null && source.isCatalogResident() ? source : null;
        }
        final String read = SqlIdentifiers.canonicalText(reference.tableSource().tableQualifiedName().getText());
        if (ownScope || namesCommonExpression(read, cteNames)) {
            return null;
        }
        final Table stored;
        try {
            stored = executor.getCatalog().resolveTable(read);
        } catch (final RuntimeException notATable) {
            return null;
        }
        return stored != null && stored.isCatalogResident() && executor.storedRowCount(stored.getQualifiedName()) >= 0
            ? stored : null;
    }

    /**
     * Whether a select passes its table's columns through under the table's own names: each item is the table's
     * star, one of its columns unrenamed, or a value of its own under a name that is none of the table's — so a
     * name of the table read from the relation is the table's column.
     */
    private static boolean passesColumnsThrough(final FrostlakeParser.SelectClauseContext inner, final Table stored) {
        for (final FrostlakeParser.SelectItemContext item : inner.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.StarItemContext) {
                if (!((FrostlakeParser.StarItemContext) item).starModifier().isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final FrostlakeParser.ExprItemContext named = (FrostlakeParser.ExprItemContext) item;
            final String alias = named.aliasName() != null ? ParseTreeText.getIdentifier(named.aliasName())
                : named.identifier() != null ? ParseTreeText.getIdentifier(named.identifier())
                : named.bareAliasWord() != null ? ParseTreeText.getIdentifier(named.bareAliasWord()) : null;
            if (alias != null) {
                if (stored.hasColumn(alias)) {
                    return false;
                }
                continue;
            }
            final FrostlakeParser.ExpressionContext value = named.booleanExpr() instanceof FrostlakeParser.ValueExprContext
                ? ((FrostlakeParser.ValueExprContext) named.booleanExpr()).expression() : null;
            if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
                final String[] parts =
                    ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
                if (!stored.hasColumn(parts[parts.length - 1])) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the WHERE holds a subquery and the planner prunes the scan: no row reaches it, nor any subquery in it. */
    public boolean prunesWhere() {
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        return where != null && !subqueries(where.booleanExpr()).isEmpty() && pruned();
    }

    /**
     * The refusal of the first correlated subquery live cannot evaluate, or null. Every refused subquery is
     * recorded in {@link #refusedKeys()}.
     */
    public RuntimeException refusal() {
        final boolean spared = pruned() || readRowByRow();
        final Set<String> outerNames = outerNames();
        RuntimeException first = null;
        for (final ParseTree judged : judgedClauses()) {
            for (final ParseTree subquery : subqueries(judged)) {
                final boolean kept = spared && keptThroughTableFunction(subquery, outerNames);
                if (spared && !kept) {
                    continue;
                }
                final RuntimeException refused = judge(subquery, outerNames, kept);
                if (first == null) {
                    first = refused;
                }
            }
        }
        return first;
    }

    /**
     * Whether a subquery reads the outer row through a table function over a scan the planner prunes, or over
     * a relation that does not hold exactly one row: neither pruning nor a row-by-row read spares it.
     */
    private boolean keptThroughTableFunction(final ParseTree node, final Set<String> outerNames) {
        final List<FrostlakeParser.QualifiedNameExprContext> reads =
            new CorrelatedSubqueryRule(executor, outerNames, relations).tableFunctionOuterReads(selectOf(node));
        if (reads.isEmpty()) {
            return false;
        }
        // Over a derived relation only what prunes the relation itself counts: the statement's own WHERE and
        // LIMIT fold the subquery away with it.
        if (body == null ? pruned() : bodyPruned()) {
            return true;
        }
        for (final FrostlakeParser.QualifiedNameExprContext read : reads) {
            final Table owner = ownerOf(read.qualifiedName(), relations);
            if (owner == null || executor.storedRowCount(owner.getQualifiedName()) != 1) {
                return true;
            }
        }
        return false;
    }

    /** The select a judged subquery node runs. */
    private static FrostlakeParser.SelectStatementContext selectOf(final ParseTree node) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.FunctionArgContext) {
            return ((FrostlakeParser.FunctionArgContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).selectStatement();
        }
        return ((FrostlakeParser.ExistsExprContext) node).selectStatement();
    }

    /** The subqueries {@link #refusal()} refused, keyed as {@link PlannedCorrelations} records them. */
    public Set<String> refusedKeys() {
        return refusedKeys;
    }

    /** Whether a subquery stands in the WHERE of the query it is a subquery of. */
    private static boolean standsInWhere(final ParseTree node) {
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.WhereClauseContext) {
                return true;
            }
            if (at instanceof FrostlakeParser.SelectClauseContext
                    || at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
        }
        return false;
    }

    /**
     * @param unpinned whether no outer name is read as a constant: a subquery kept through a table function is
     *                 refused over a one-row table too, whose every column holds one value
     */
    private RuntimeException judge(final ParseTree node, final Set<String> outerNames, final boolean unpinned) {
        final FrostlakeParser.SelectStatementContext subquery;
        final boolean scalar;
        final Token at;
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            subquery = ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement();
            scalar = true;
            at = SubqueryAnchor.of((FrostlakeParser.ScalarSubqueryExprContext) node);
        } else if (node instanceof FrostlakeParser.FunctionArgContext) {
            // A subquery written without parentheses as a call's argument, ABS(SELECT …), is a scalar one.
            subquery = ((FrostlakeParser.FunctionArgContext) node).selectStatement();
            scalar = true;
            at = subquery.getStart();
        } else if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            subquery = ((FrostlakeParser.InSubqueryExprContext) node).selectStatement();
            scalar = false;
            at = subquery.getStart();
        } else {
            final FrostlakeParser.ExistsExprContext exists = (FrostlakeParser.ExistsExprContext) node;
            subquery = exists.selectStatement();
            scalar = false;
            at = exists.EXISTS().getSymbol();
        }
        // An outer join may extend a relation with NULLs its statistics do not see, so beside one no name is constant.
        final CorrelatedSubqueryRule rule = new CorrelatedSubqueryRule(executor, outerNames, relations,
            outerJoined || unpinned ? null : new CatalogRelationConstancy(executor, relations));
        final FrostlakeParser.InSubqueryExprContext in = node instanceof FrostlakeParser.InSubqueryExprContext
            ? (FrostlakeParser.InSubqueryExprContext) node : null;
        if (!(scalar ? rule.refusesScalar(subquery)
                : rule.refusesMembership(subquery, in != null, in != null && in.NOT() == null && whereConjunct(in),
                    in == null ? null : ParseTreeText.getOriginalText(in.expression())))) {
            return null;
        }
        final String text = ParseTreeText.getOriginalText(subquery);
        refusedKeys.add(scalar ? PlannedCorrelations.valueKey(text) : PlannedCorrelations.membershipKey(text));
        if (rule.outerReadDepth() == 2 && standsInWhere(node)) {
            // A WHERE's subquery whose own subquery reads the outer row is refused with no place given, where one
            // read from three levels in, and any of a select list or a HAVING, is placed (live-verified).
            return new UnsupportedSubqueryException(
                SqlCompilationError.of("Unsupported subquery type cannot be evaluated"));
        }
        return new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"
            + " at line " + at.getLine() + ", position " + at.getCharPositionInLine()));
    }

    /**
     * Whether a membership stands as a conjunct of the WHERE, through AND and parentheses, where a semi-join answers
     * it: {@code WHERE v IN (…) AND v > 0} does, {@code WHERE v IN (…) OR v = 2}, {@code WHERE NOT (v IN (…))},
     * {@code WHERE v IN (…) = FALSE}, a CASE and a select list do not (live-verified). A HAVING the planner moves
     * to the WHERE counts as one (see {@link #movesToWhere}).
     */
    private boolean whereConjunct(final FrostlakeParser.InSubqueryExprContext membership) {
        ParserRuleContext at = membership.getParent();
        while (at instanceof FrostlakeParser.ValueExprContext || at instanceof FrostlakeParser.AndExprContext
                || at instanceof FrostlakeParser.ParenExprContext) {
            at = at.getParent();
        }
        return at instanceof FrostlakeParser.WhereClauseContext || at instanceof FrostlakeParser.HavingClauseContext
            && movesToWhere(((FrostlakeParser.HavingClauseContext) at).booleanExpr());
    }

    /**
     * Whether the planner moves a HAVING to the WHERE: it holds no aggregate call, no EXISTS and no scalar subquery
     * outside the selects nested in it. {@code GROUP BY v HAVING v IN (…) AND v > 0}, {@code … AND v IN (SELECT
     * g.id FROM g)} and an aggregate inside the IN's own select are moved; {@code … AND COUNT(*) > 0},
     * {@code HAVING MAX(v) IN (…)}, {@code … AND (SELECT COUNT(*) FROM g) > 0} and {@code … AND EXISTS (…)} are
     * not (live-verified).
     */
    private boolean movesToWhere(final ParseTree node) {
        if (node instanceof FrostlakeParser.ExistsExprContext || node instanceof FrostlakeParser.ScalarSubqueryExprContext
                || node instanceof FrostlakeParser.FunctionArgContext
                    && ((FrostlakeParser.FunctionArgContext) node).selectStatement() != null) {
            return false;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.ExpressionContext
                && executor.hasAggregateFunctionInExpression((FrostlakeParser.ExpressionContext) node)) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!movesToWhere(node.getChild(i))) {
                return false;
            }
        }
        return true;
    }

    // ── what is judged ──────────────────────────────────────────────────────────────

    /** The clauses whose subqueries are judged, in live's order. */
    private List<ParseTree> judgedClauses() {
        final List<ParseTree> judged = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            judged.add(item);
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        // A derived relation's own WHERE is the statement's too once the relation is merged into it.
        if (where != null && !foldsToFalse(where.booleanExpr()) && !limitedToNothing()
                && !(body != null && body.whereClause() != null && foldsToFalse(body.whereClause().booleanExpr()))) {
            judged.add(where.booleanExpr());
        }
        if (clause.havingClause() != null && !foldsToFalse(clause.havingClause().booleanExpr())
                && !anyProvenFalse(clause.havingClause().booleanExpr())) {
            judged.add(clause.havingClause());
        }
        if (clause.qualifyClause() != null && !foldsToFalse(clause.qualifyClause().booleanExpr())) {
            judged.add(clause.qualifyClause());
        }
        if (statement.orderByClause() != null && statement.selectOperand().size() == 1) {
            judged.add(statement.orderByClause());
        }
        final List<ParseTree> holding = new ArrayList<>();
        for (final ParseTree node : judged) {
            if (!subqueries(node).isEmpty()) {
                holding.add(node);
            }
        }
        return holding;
    }

    /**
     * The scalar, IN and EXISTS subqueries {@code node} holds outside any nested select, in the order written; a
     * scalar one written without parentheses as a call's argument is its argument node.
     */
    private List<ParseTree> subqueries(final ParseTree node) {
        final List<ParseTree> found = new ArrayList<>();
        collectSubqueries(node, found);
        return found;
    }

    private void collectSubqueries(final ParseTree node, final List<ParseTree> into) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext || node instanceof FrostlakeParser.ExistsExprContext
                || node instanceof FrostlakeParser.InSubqueryExprContext
                || node instanceof FrostlakeParser.FunctionArgContext
                    && ((FrostlakeParser.FunctionArgContext) node).selectStatement() != null) {
            into.add(node);
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;
        }
        if (isConditional(node)) {
            final List<ParseTree> taken = takenOperands(node);
            if (taken != null) {
                for (final ParseTree operand : taken) {
                    collectSubqueries(operand, into);
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSubqueries(node.getChild(i), into);
        }
    }

    /**
     * A CASE or a conditional call. Live folds a branch the statistics settle before it judges a subquery in
     * it — {@code IFF(id > 0, 1, (SELECT …))} over positive ids and {@code NVL(id, (SELECT …))} over a column
     * holding no NULL are answered — so a subquery in one is left to the row that reaches it, unless a constant
     * or the statistics decide the branch (see {@link #takenOperands}).
     */
    private static boolean isConditional(final ParseTree node) {
        if (node instanceof FrostlakeParser.CaseExprContext) {
            return true;
        }
        if (!(node instanceof FrostlakeParser.FunctionCallExprContext)) {
            return false;
        }
        final String name = ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText().toUpperCase();
        return CONDITIONAL_CALLS.contains(name);
    }

    /**
     * The operands of a conditional that a constant decides, each certainly evaluated, so a subquery in one is
     * judged with the statement (live-verified): the condition and the chosen branch of IFF and of a searched
     * CASE ({@code IFF(1 = 0, 1, (SELECT …))} judges the subquery, {@code IFF(1 = 0, (SELECT …), 1)} does not),
     * NVL2's first operand and its chosen branch, the operands of COALESCE, NVL and IFNULL up to the first
     * that folds to a value or that no constant settles ({@code NVL(NULL, (SELECT …))} judges it,
     * {@code COALESCE(1, (SELECT …))} does not), and the branch a simple CASE or a DECODE over constants takes
     * ({@code CASE 1 WHEN 1 THEN (SELECT …) END}, {@code DECODE(1, 1, (SELECT …))}). A condition of IFF or of a
     * searched CASE that the statistics of the one table it reads settle decides its branch as a constant does:
     * {@code CASE WHEN id > 0 THEN (SELECT …) ELSE 1 END} over positive ids judges the subquery. Null where
     * neither decides anything.
     */
    private List<ParseTree> takenOperands(final ParseTree conditional) {
        final List<ParseTree> taken = new ArrayList<>();
        if (conditional instanceof FrostlakeParser.CaseExprContext) {
            final FrostlakeParser.CaseExpressionContext written = ((FrostlakeParser.CaseExprContext) conditional).caseExpression();
            if (written instanceof FrostlakeParser.SimpleCaseExprContext) {
                return takenOfSimpleCase((FrostlakeParser.SimpleCaseExprContext) written);
            }
            if (!(written instanceof FrostlakeParser.SearchedCaseExprContext)) {
                return null;
            }
            final FrostlakeParser.SearchedCaseExprContext searched = (FrostlakeParser.SearchedCaseExprContext) written;
            for (final FrostlakeParser.WhenClauseContext when : searched.whenClause()) {
                final Object condition = decided(when.booleanExpr(0));
                if (condition == NOT_CONSTANT) {
                    return taken.isEmpty() ? null : taken;
                }
                taken.add(when.booleanExpr(0));
                if (Boolean.TRUE.equals(condition)) {
                    taken.add(when.booleanExpr(1));
                    return taken;
                }
            }
            if (searched.booleanExpr() != null) {
                taken.add(searched.booleanExpr());
            }
            return taken;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) conditional;
        final List<FrostlakeParser.FunctionArgContext> args = call.functionArgList() == null
            ? new ArrayList<FrostlakeParser.FunctionArgContext>() : call.functionArgList().functionArg();
        final String name = call.functionName().getText().toUpperCase();
        if ("IFF".equals(name) || "NVL2".equals(name)) {
            if (args.size() != 3) {
                return null;
            }
            final Object decider = "IFF".equals(name) ? decided(args.get(0)) : folded(args.get(0), executor);
            if (decider == NOT_CONSTANT || ("IFF".equals(name) && decider != null && !(decider instanceof Boolean))) {
                return null;
            }
            taken.add(args.get(0));
            taken.add("IFF".equals(name) ? (Boolean.TRUE.equals(decider) ? args.get(1) : args.get(2))
                : (decider != null ? args.get(1) : args.get(2)));
            return taken;
        }
        if ("COALESCE".equals(name) || "NVL".equals(name) || "IFNULL".equals(name)) {
            for (final FrostlakeParser.FunctionArgContext arg : args) {
                taken.add(arg);
                final Object value = folded(arg, executor);
                if (value != null) {
                    return taken;
                }
            }
            return taken;
        }
        if ("DECODE".equals(name) && args.size() >= 3) {
            return takenOfDecode(args);
        }
        return null;
    }

    /** {@link #takenOperands} for a simple CASE: its subject, the values compared up to the one it equals, that branch. */
    private List<ParseTree> takenOfSimpleCase(final FrostlakeParser.SimpleCaseExprContext simple) {
        final Object subject = folded(simple.expression(), executor);
        if (subject == NOT_CONSTANT) {
            return null;
        }
        final List<ParseTree> taken = new ArrayList<>();
        taken.add(simple.expression());
        for (final FrostlakeParser.WhenClauseContext when : simple.whenClause()) {
            final Object value = folded(when.booleanExpr(0), executor);
            if (value == NOT_CONSTANT) {
                return taken;
            }
            taken.add(when.booleanExpr(0));
            if (subject != null && value != null && ValueComparisons.compareValues(subject, value) == 0) {
                taken.add(when.booleanExpr(1));
                return taken;
            }
        }
        if (simple.booleanExpr() != null) {
            taken.add(simple.booleanExpr());
        }
        return taken;
    }

    /** {@link #takenOperands} for a DECODE, which also takes a NULL for a NULL. */
    private List<ParseTree> takenOfDecode(final List<FrostlakeParser.FunctionArgContext> args) {
        final Object subject = folded(args.get(0), executor);
        if (subject == NOT_CONSTANT) {
            return null;
        }
        final List<ParseTree> taken = new ArrayList<>();
        taken.add(args.get(0));
        int at = 1;
        for (; at + 1 < args.size(); at += 2) {
            final Object value = folded(args.get(at), executor);
            if (value == NOT_CONSTANT) {
                return taken;
            }
            taken.add(args.get(at));
            if (subject == null ? value == null
                    : value != null && ValueComparisons.compareValues(subject, value) == 0) {
                taken.add(args.get(at + 1));
                return taken;
            }
        }
        if (at < args.size()) {
            taken.add(args.get(at));
        }
        return taken;
    }

    /**
     * What decides a condition: the value a constant folds to, else what the statistics of the one table it reads
     * prove of every row, else {@link #NOT_CONSTANT}. Beside an outer join, whose NULL-extended rows the statistics
     * do not describe, only a constant decides.
     */
    private Object decided(final ParserRuleContext condition) {
        final Object constant = folded(condition, executor);
        if (constant != NOT_CONSTANT || outerJoined || !subqueries(condition).isEmpty()) {
            return constant;
        }
        final Set<Table> read = new HashSet<>();
        if (!tablesRead(condition, read, relations) || read.size() != 1) {
            return NOT_CONSTANT;
        }
        try {
            final Boolean verdict = new CountStatisticsBound(executor, clause, read.iterator().next(), relations, null,
                false, null).verdictOver(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(condition)));
            return verdict == null ? NOT_CONSTANT : verdict;
        } catch (final RuntimeException unprovable) {
            return NOT_CONSTANT;
        }
    }

    /**
     * The value an operand folds to when it is a constant — literals and the operators and casts over them, no
     * column and no subquery — or {@link #NOT_CONSTANT}. A constant that faults is no constant here: the
     * evaluation reports it.
     */
    private static Object folded(final ParserRuleContext operand, final QueryExecutor executor) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(operand));
            if (!isConstant(parsed)) {
                return NOT_CONSTANT;
            }
            return new ExpressionEvaluator(new Table("DUMMY", new ArrayList<TableColumn>(), false),
                executor.getFunctionRegistry(), executor.getCatalog(), executor).evaluate(parsed, new Row(new Object[0]));
        } catch (final RuntimeException unfoldable) {
            return NOT_CONSTANT;
        }
    }

    // ── pruning ─────────────────────────────────────────────────────────────────────

    private boolean pruned() {
        if (pruned == null) {
            pruned = decidePruned();
        }
        return pruned;
    }

    private boolean decidePruned() {
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        if (where != null) {
            collectConjuncts(where.booleanExpr(), conjuncts);
        }
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (isFalseOrNullLiteral(conjunct)) {
                return true;
            }
        }
        if (outerJoined) {
            return false;
        }
        for (final FrostlakeParser.JoinClauseContext join : clause.tableExpression().joinClause()) {
            if (join.booleanExpr() != null && join.NATURAL() == null
                    && (join.joinType() == null || join.joinType().INNER() != null)) {
                collectConjuncts(join.booleanExpr(), conjuncts);
            }
        }
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (provenFalse(conjunct, clause, relations)) {
                return true;
            }
        }
        return body != null && prunedOverDerived(where);
    }

    /**
     * What prunes a statement over a derived relation beside what prunes one over a table: its own constant
     * conjunct folding to FALSE, its {@code LIMIT 0} while the relation has no limit of its own, and the
     * relation's own WHERE, by the rules the statement's WHERE prunes a table's scan by.
     */
    private boolean prunedOverDerived(final FrostlakeParser.WhereClauseContext where) {
        if (where != null && foldsToFalse(where.booleanExpr())) {
            return true;
        }
        if (limitedToNothing() && bodyStatement.limitClause() == null && bodyStatement.fetchClause() == null
                && body.topClause() == null) {
            return true;
        }
        return bodyPruned();
    }

    /** Whether the derived relation's own WHERE prunes its table's scan, as a statement's WHERE prunes a table's. */
    private boolean bodyPruned() {
        if (body.whereClause() == null) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(body.whereClause().booleanExpr(), conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (isFalseOrNullLiteral(conjunct) || provenFalse(conjunct, body, bodyRelations)) {
                return true;
            }
        }
        return false;
    }

    /** Whether every table in the FROM holds at most one row: live then reads the subquery row by row. */
    private boolean readRowByRow() {
        for (final Table relation : relations.values()) {
            final long rows = executor.storedRowCount(relation.getQualifiedName());
            if (rows < 0 || rows > 1) {
                return false;
            }
        }
        return true;
    }

    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext predicate,
                                         final List<FrostlakeParser.BooleanExprContext> into) {
        if (predicate instanceof FrostlakeParser.AndExprContext) {
            collectConjuncts(((FrostlakeParser.AndExprContext) predicate).booleanExpr(0), into);
            collectConjuncts(((FrostlakeParser.AndExprContext) predicate).booleanExpr(1), into);
            return;
        }
        final FrostlakeParser.BooleanExprContext inner = parenthesized(predicate);
        if (inner != null) {
            collectConjuncts(inner, into);
            return;
        }
        into.add(predicate);
    }

    /** The predicate a bracketed predicate holds, or null for any other. */
    private static FrostlakeParser.BooleanExprContext parenthesized(final FrostlakeParser.BooleanExprContext predicate) {
        if (predicate instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) predicate).expression() instanceof FrostlakeParser.ParenExprContext) {
            return ((FrostlakeParser.ParenExprContext) ((FrostlakeParser.ValueExprContext) predicate).expression())
                .booleanExpr();
        }
        return null;
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

    /**
     * Whether a filter's constant conjunct — literals and the operators and casts over them, no column and no
     * subquery — folds to FALSE, which drops the subqueries the filter holds. A NULL does not.
     */
    private boolean foldsToFalse(final FrostlakeParser.BooleanExprContext filter) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(filter, conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            try {
                final Expression folded = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(conjunct));
                if (!isConstant(folded)) {
                    continue;
                }
                final Object value = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(),
                    executor).evaluate(folded, new Row(new Object[table.getColumns().size()]));
                if (Boolean.FALSE.equals(value)) {
                    return true;
                }
            } catch (final RuntimeException unfoldable) {
                // a constant that faults is left to the evaluation, which reports it
            }
        }
        return false;
    }

    private static boolean isConstant(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof CastExpression) {
            return isConstant(((CastExpression) expr).getExpression());
        }
        if (expr instanceof UnaryOperationExpression) {
            return isConstant(((UnaryOperationExpression) expr).getOperand());
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return isConstant(binary.getLeft()) && isConstant(binary.getRight())
                && (binary.getEscape() == null || isConstant(binary.getEscape()));
        }
        return false;
    }

    /** Whether a top-level conjunct of {@code filter} is proven false of every row (see {@link #provenFalse}). */
    private boolean anyProvenFalse(final FrostlakeParser.BooleanExprContext filter) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(filter, conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (provenFalse(conjunct, clause, relations)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the statement is limited to no row, {@code LIMIT 0}, which drops the subqueries its WHERE holds. */
    private boolean limitedToNothing() {
        final FrostlakeParser.LimitClauseContext limit = statement.limitClause();
        return limit != null && !limit.INTEGER_LITERAL().isEmpty() && limit.getChild(1) == limit.INTEGER_LITERAL(0)
            && new BigInteger(limit.INTEGER_LITERAL(0).getText()).signum() == 0;
    }

    /**
     * Whether a conjunct reads the columns of exactly one table, whose statistics prove it false of every row.
     *
     * @param reading the select whose filter the conjunct is part of
     * @param named   that select's relations, keyed by the names it gives each
     */
    private boolean provenFalse(final FrostlakeParser.BooleanExprContext conjunct,
                                final FrostlakeParser.SelectClauseContext reading, final Map<String, Table> named) {
        for (final ParseTree subquery : subqueries(conjunct)) {
            // Only a subquery the planner folds to a constant leaves the conjunct to the statistics.
            if (!(subquery instanceof FrostlakeParser.ScalarSubqueryExprContext) || !ConstantSubqueries.folds(executor,
                    new SubqueryExpression(ParseTreeText.getOriginalText(
                        ((FrostlakeParser.ScalarSubqueryExprContext) subquery).selectStatement())))) {
                return false;
            }
        }
        final Set<Table> read = new HashSet<>();
        if (!tablesRead(conjunct, read, named) || read.size() != 1) {
            return false;
        }
        final Table reads = read.iterator().next();
        try {
            final CountStatisticsBound statistics =
                new CountStatisticsBound(executor, reading, reads, named, null, false, null);
            return Boolean.FALSE.equals(
                statistics.verdictOver(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(conjunct))));
        } catch (final RuntimeException unprovable) {
            return false;
        }
    }

    /**
     * The tables whose columns {@code node} reads outside any nested select, into {@code into}; false when a
     * name reaches no single table of the FROM clause.
     */
    private static boolean tablesRead(final ParseTree node, final Set<Table> into, final Map<String, Table> named) {
        if (node instanceof FrostlakeParser.SelectStatementContext || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final Table owner = ownerOf(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName(), named);
            if (owner == null) {
                return false;
            }
            into.add(owner);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!tablesRead(node.getChild(i), into, named)) {
                return false;
            }
        }
        return true;
    }

    /** The one FROM table a column reference reads: the one its qualifier names, or the only one holding the name. */
    private static Table ownerOf(final FrostlakeParser.QualifiedNameContext name, final Map<String, Table> named) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final String column = parts[parts.length - 1];
        if (parts.length == 2) {
            final Table owner = named.containsKey(parts[0]) ? named.get(parts[0])
                : named.get(parts[0].toUpperCase());
            return owner != null && owner.hasColumn(column) ? owner : null;
        }
        if (parts.length != 1) {
            return null;
        }
        Table owner = null;
        for (final Table relation : new HashSet<>(named.values())) {
            if (relation.hasColumn(column)) {
                if (owner != null) {
                    return null;
                }
                owner = relation;
            }
        }
        return owner;
    }

    // ── scope ───────────────────────────────────────────────────────────────────────

    /**
     * The names the evaluated row offers a subquery, upper-cased as the row's own bindings are: each column
     * bare, qualified by its table's name, and qualified by the name the FROM clause gives its table.
     */
    private Set<String> outerNames() {
        final Set<String> names = new HashSet<>(
            executor.groupedSubqueryAggregateKeys(clause, table, relations, new ArrayList<>(relations.values())));
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            for (final TableColumn column : relation.getValue().getColumns()) {
                final String columnName = column.getName().toUpperCase();
                names.add(columnName);
                names.add(relation.getKey().toUpperCase() + "." + columnName);
                names.add(relation.getValue().getName().toUpperCase() + "." + columnName);
            }
        }
        return names;
    }

    // ── shape ───────────────────────────────────────────────────────────────────────

    private static boolean plainTables(final List<FrostlakeParser.TableReferenceContext> references) {
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            if (!plainTable(reference)) {
                return false;
            }
        }
        return true;
    }

    /** A catalog table named as written: no LATERAL, column list, PIVOT, UNPIVOT, SAMPLE or time travel. */
    private static boolean plainTable(final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        return reference.LATERAL() == null && reference.identifierList() == null && reference.pivotClause() == null
            && reference.unpivotClause() == null && reference.matchRecognizeClause() == null && reference.sampleClause() == null && source != null
            && source.tableQualifiedName() != null && source.timeTravelClause() == null;
    }

    private static boolean holds(final ParseTree node, final Class<?> kind) {
        if (kind.isInstance(node)) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holds(node.getChild(i), kind)) {
                return true;
            }
        }
        return false;
    }
}
