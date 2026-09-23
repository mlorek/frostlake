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
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
 * <p>Only a statement over catalog tables is judged here. A derived table, a view, a CTE, a table function
 * or a query run for an outer row keeps the judgement a row makes when it first reaches the subquery.
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
    private Boolean pruned;
    private final Set<String> refusedKeys = new LinkedHashSet<>();

    private CorrelationPlanJudgement(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext statement,
                                     final FrostlakeParser.SelectClauseContext clause, final Table table,
                                     final Map<String, Table> relations, final boolean outerJoined) {
        this.executor = executor;
        this.statement = statement;
        this.clause = clause;
        this.table = table;
        this.relations = relations;
        this.outerJoined = outerJoined;
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
            new CorrelationPlanJudgement(executor, statement, clause, table, relations, outerJoined);
        return judgement.judgedClauses().isEmpty()
                && (where == null || subqueries(where.booleanExpr(), executor).isEmpty())
            ? null : judgement;
    }

    /** Whether the WHERE holds a subquery and the planner prunes the scan: no row reaches it, nor any subquery in it. */
    public boolean prunesWhere() {
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        return where != null && !subqueries(where.booleanExpr(), executor).isEmpty() && pruned();
    }

    /**
     * The refusal of the first correlated subquery live cannot evaluate, or null. Every refused subquery is
     * recorded in {@link #refusedKeys()}.
     */
    public RuntimeException refusal() {
        if (pruned() || readRowByRow()) {
            return null;
        }
        final Set<String> outerNames = outerNames();
        RuntimeException first = null;
        for (final ParseTree judged : judgedClauses()) {
            for (final ParseTree subquery : subqueries(judged, executor)) {
                final RuntimeException refused = judge(subquery, outerNames);
                if (first == null) {
                    first = refused;
                }
            }
        }
        return first;
    }

    /** The subqueries {@link #refusal()} refused, keyed as {@link PlannedCorrelations} records them. */
    public Set<String> refusedKeys() {
        return refusedKeys;
    }

    private RuntimeException judge(final ParseTree node, final Set<String> outerNames) {
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
            outerJoined ? null : new CatalogRelationConstancy(executor, relations));
        final FrostlakeParser.InSubqueryExprContext in = node instanceof FrostlakeParser.InSubqueryExprContext
            ? (FrostlakeParser.InSubqueryExprContext) node : null;
        if (!(scalar ? rule.refusesScalar(subquery)
                : rule.refusesMembership(subquery, in != null, in != null && in.NOT() == null && whereConjunct(in),
                    in == null ? null : ParseTreeText.getOriginalText(in.expression())))) {
            return null;
        }
        final String text = ParseTreeText.getOriginalText(subquery);
        refusedKeys.add(scalar ? PlannedCorrelations.valueKey(text) : PlannedCorrelations.membershipKey(text));
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
        if (where != null && !foldsToFalse(where.booleanExpr()) && !limitedToNothing()) {
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
            if (!subqueries(node, executor).isEmpty()) {
                holding.add(node);
            }
        }
        return holding;
    }

    /**
     * The scalar, IN and EXISTS subqueries {@code node} holds outside any nested select, in the order written; a
     * scalar one written without parentheses as a call's argument is its argument node.
     */
    private static List<ParseTree> subqueries(final ParseTree node, final QueryExecutor executor) {
        final List<ParseTree> found = new ArrayList<>();
        collectSubqueries(node, found, executor);
        return found;
    }

    private static void collectSubqueries(final ParseTree node, final List<ParseTree> into,
                                          final QueryExecutor executor) {
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
            final List<ParseTree> taken = takenOperands(node, executor);
            if (taken != null) {
                for (final ParseTree operand : taken) {
                    collectSubqueries(operand, into, executor);
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSubqueries(node.getChild(i), into, executor);
        }
    }

    /**
     * A CASE or a conditional call. Live folds a branch the statistics settle before it judges a subquery in
     * it — {@code IFF(id > 0, 1, (SELECT …))} over positive ids and {@code NVL(id, (SELECT …))} over a column
     * holding no NULL are answered — so a subquery in one is left to the row that reaches it, unless a constant
     * decides the branch (see {@link #takenOperands}).
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
     * NVL2's first operand and its chosen branch, and the operands of COALESCE, NVL and IFNULL up to the first
     * that folds to a value or that no constant settles ({@code NVL(NULL, (SELECT …))} judges it,
     * {@code COALESCE(1, (SELECT …))} does not). Null where no constant decides anything, and for DECODE and a
     * simple CASE.
     */
    private static List<ParseTree> takenOperands(final ParseTree conditional, final QueryExecutor executor) {
        final List<ParseTree> taken = new ArrayList<>();
        if (conditional instanceof FrostlakeParser.CaseExprContext) {
            final FrostlakeParser.CaseExpressionContext written = ((FrostlakeParser.CaseExprContext) conditional).caseExpression();
            if (!(written instanceof FrostlakeParser.SearchedCaseExprContext)) {
                return null;
            }
            final FrostlakeParser.SearchedCaseExprContext searched = (FrostlakeParser.SearchedCaseExprContext) written;
            for (final FrostlakeParser.WhenClauseContext when : searched.whenClause()) {
                final Object condition = folded(when.booleanExpr(0), executor);
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
            final Object decider = folded(args.get(0), executor);
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
        return null;
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
            if (provenFalse(conjunct)) {
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
            if (provenFalse(conjunct)) {
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

    /** Whether a conjunct reads the columns of exactly one table, whose statistics prove it false of every row. */
    private boolean provenFalse(final FrostlakeParser.BooleanExprContext conjunct) {
        for (final ParseTree subquery : subqueries(conjunct, executor)) {
            // Only a subquery the planner folds to a constant leaves the conjunct to the statistics.
            if (!(subquery instanceof FrostlakeParser.ScalarSubqueryExprContext) || !ConstantSubqueries.folds(executor,
                    new SubqueryExpression(ParseTreeText.getOriginalText(
                        ((FrostlakeParser.ScalarSubqueryExprContext) subquery).selectStatement())))) {
                return false;
            }
        }
        final Set<Table> read = new HashSet<>();
        if (!tablesRead(conjunct, read) || read.size() != 1) {
            return false;
        }
        final Table reads = read.iterator().next();
        try {
            final CountStatisticsBound statistics =
                new CountStatisticsBound(executor, clause, reads, relations, null, false, null);
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
    private boolean tablesRead(final ParseTree node, final Set<Table> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final Table owner = ownerOf(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (owner == null) {
                return false;
            }
            into.add(owner);
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!tablesRead(node.getChild(i), into)) {
                return false;
            }
        }
        return true;
    }

    /** The one FROM table a column reference reads: the one its qualifier names, or the only one holding the name. */
    private Table ownerOf(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        final String column = parts[parts.length - 1];
        if (parts.length == 2) {
            final Table owner = relations.containsKey(parts[0]) ? relations.get(parts[0])
                : relations.get(parts[0].toUpperCase());
            return owner != null && owner.hasColumn(column) ? owner : null;
        }
        if (parts.length != 1) {
            return null;
        }
        Table owner = null;
        for (final Table relation : new HashSet<>(relations.values())) {
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
            && reference.unpivotClause() == null && reference.sampleClause() == null && source != null
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
