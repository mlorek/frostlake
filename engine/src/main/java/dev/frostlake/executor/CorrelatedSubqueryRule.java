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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
 *       its argument: {@code (SELECT SUM(v + fz.id) FROM g)} is refused.</li>
 *   <li><b>A filter</b> — the WHERE, HAVING or join ON of a scalar aggregate, and of an EXISTS or IN
 *       subquery — may read the outer row only through comparisons whose every operand stays on one
 *       side. {@code g.id = fz.id}, {@code fz.id > 5}, {@code g.id IN (fz.id, 6)},
 *       {@code g.s LIKE fz.s} and ORs of those are accepted. A bare boolean ({@code WHERE fz.b}), NOT,
 *       CASE, or an operand mixing the two sides ({@code g.id + fz.id = 10}) is refused.</li>
 *   <li><b>EXISTS and IN</b> take any other shape — GROUP BY, no FROM — but not a LIMIT or a set
 *       operation.</li>
 * </ul>
 *
 * <p>Live's planner answers from metadata first, and two of its shortcuts are mirrored: a subquery over
 * an empty table is pruned away, and an outer relation of at most one row is evaluated row by row (see
 * {@code SubqueryEvaluator}); either way nothing is refused.
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
    private boolean sawOuter;
    private boolean sawUnknown;

    /**
     * @param outerNames every name the evaluated row offers the subquery, upper-cased: bare columns and
     *                   relation-qualified ones, the row's own and the rows further out
     */
    public CorrelatedSubqueryRule(final QueryExecutor executor, final Set<String> outerNames) {
        this.executor = executor;
        this.functions = executor.getFunctionRegistry();
        this.catalog = executor.getCatalog();
        this.outerNames = outerNames;
    }

    /** Whether live refuses {@code subquery} read as a value. */
    public boolean refusesScalar(final FrostlakeParser.SelectStatementContext subquery) {
        if (!judgeable(subquery) || !singleColumn(subquery)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
        if (clause == null) {
            return true;
        }
        if (innerRelationEmpty(clause)) {
            return false;
        }
        if (clause.groupByClause() != null || clause.connectByClause() != null) {
            return true;
        }
        final List<CorrelationScope> scopes = scopeOfAlone(clause);
        if (!aggregateCalls(clause.selectList()).isEmpty()) {
            return limited(subquery) || clause.topClause() != null
                || readsOuterInsideAggregate(clause, subquery, scopes)
                || !filtersSupported(clause, scopes);
        }
        if (clause.tableExpression() != null) {
            return true;
        }
        return clause.whereClause() != null || clause.havingClause() != null || clause.qualifyClause() != null
            || ordered(subquery) || !limitKeepsTheRow(subquery, clause);
    }

    /** Whether live refuses {@code subquery} under EXISTS or IN. */
    public boolean refusesMembership(final FrostlakeParser.SelectStatementContext subquery) {
        if (!judgeable(subquery)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext clause = mainClause(subquery);
        if (clause == null) {
            return true;
        }
        if (innerRelationEmpty(clause)) {
            return false;
        }
        if (limited(subquery) || clause.topClause() != null) {
            return true;
        }
        return !filtersSupported(clause, scopeOfAlone(clause));
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

    /** Where a reference resolves: the innermost scope that carries it, else the outer row, else nowhere. */
    private CorrelationSide sideOf(final FrostlakeParser.QualifiedNameExprContext reference,
                                   final List<CorrelationScope> scopes) {
        final List<String> parts = partsOf(reference.qualifiedName());
        final String column = parts.get(parts.size() - 1);
        if (parts.size() == 1) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                if (scopes.get(i).resolvesBare(column)) {
                    return CorrelationSide.INNER;
                }
            }
            return outerNames.contains(column) ? CorrelationSide.OUTER : CorrelationSide.UNKNOWN;
        }
        final String relation = parts.get(parts.size() - 2);
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).hasQualifier(relation)) {
                return CorrelationSide.INNER;
            }
        }
        return outerNames.contains(relation + "." + column) || outerNames.contains(String.join(".", parts))
            ? CorrelationSide.OUTER : CorrelationSide.UNKNOWN;
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
     * column, the column's own name. False when the names cannot be read: a star, or a set operation.
     */
    private boolean addDerivedColumns(final CorrelationScope scope, final FrostlakeParser.TableReferenceContext relation,
                                      final FrostlakeParser.SelectStatementContext derived) {
        if (relation.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext column : relation.identifierList().identifier()) {
                scope.addColumn(ParseTreeText.getIdentifier(column).toUpperCase());
            }
            return true;
        }
        final FrostlakeParser.SelectClauseContext clause = mainClause(derived);
        if (clause == null) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final FrostlakeParser.ExprItemContext exprItem = (FrostlakeParser.ExprItemContext) item;
            if (exprItem.aliasName() != null) {
                scope.addColumn(ParseTreeText.getIdentifier(exprItem.aliasName()).toUpperCase());
            } else if (exprItem.identifier() != null) {
                scope.addColumn(ParseTreeText.getIdentifier(exprItem.identifier()).toUpperCase());
            } else if (exprItem.booleanExpr() instanceof FrostlakeParser.ValueExprContext
                    && ((FrostlakeParser.ValueExprContext) exprItem.booleanExpr()).expression()
                        instanceof FrostlakeParser.QualifiedNameExprContext) {
                final List<String> parts = partsOf(((FrostlakeParser.QualifiedNameExprContext)
                    ((FrostlakeParser.ValueExprContext) exprItem.booleanExpr()).expression()).qualifiedName());
                scope.addColumn(parts.get(parts.size() - 1));
            }
            // any other unaliased item is named by its own text, which a bare name does not reach
        }
        return true;
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

    /** A FROM-less select keeps its one row under LIMIT 1 or more, and loses it to LIMIT 0 or an OFFSET. */
    private static boolean limitKeepsTheRow(final FrostlakeParser.SelectStatementContext statement,
                                            final FrostlakeParser.SelectClauseContext clause) {
        for (final FrostlakeParser.SelectStatementContext level : statementChain(statement)) {
            if (level.limitClause() != null && (level.limitClause().OFFSET() != null
                    || level.limitClause().INTEGER_LITERAL().size() != 1
                    || !positive(level.limitClause().INTEGER_LITERAL(0).getText()))) {
                return false;
            }
            if (level.fetchClause() != null && (level.fetchClause().OFFSET() != null
                    || level.fetchClause().INTEGER_LITERAL().size() != 1
                    || !positive(level.fetchClause().INTEGER_LITERAL(0).getText()))) {
                return false;
            }
        }
        return clause.topClause() == null || positive(clause.topClause().INTEGER_LITERAL().getText());
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

    // ── filters ───────────────────────────────────────────────────────────────────

    private boolean filtersSupported(final FrostlakeParser.SelectClauseContext clause,
                                     final List<CorrelationScope> scopes) {
        if (clause.whereClause() != null && !predicateSupported(clause.whereClause().booleanExpr(), scopes)) {
            return false;
        }
        if (clause.havingClause() != null && !predicateSupported(clause.havingClause().booleanExpr(), scopes)) {
            return false;
        }
        if (clause.tableExpression() != null) {
            final List<FrostlakeParser.JoinClauseContext> joins = new ArrayList<>();
            collectJoins(clause.tableExpression(), joins);
            for (final FrostlakeParser.JoinClauseContext join : joins) {
                if (join.booleanExpr() != null && !predicateSupported(join.booleanExpr(), scopes)) {
                    return false;
                }
            }
        }
        return true;
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
                                       final List<CorrelationScope> scopes) {
        if (predicate instanceof FrostlakeParser.AndExprContext) {
            final FrostlakeParser.AndExprContext and = (FrostlakeParser.AndExprContext) predicate;
            return predicateSupported(and.booleanExpr(0), scopes) && predicateSupported(and.booleanExpr(1), scopes);
        }
        if (predicate instanceof FrostlakeParser.OrExprContext) {
            final FrostlakeParser.OrExprContext or = (FrostlakeParser.OrExprContext) predicate;
            return predicateSupported(or.booleanExpr(0), scopes) && predicateSupported(or.booleanExpr(1), scopes);
        }
        if (predicate instanceof FrostlakeParser.ValueExprContext) {
            return expressionSupported(((FrostlakeParser.ValueExprContext) predicate).expression(), scopes);
        }
        return !readsDirectly(predicate, CorrelationSide.OUTER, scopes);
    }

    /** A comparison whose every operand keeps to one side, or a condition that reads no outer name. */
    private boolean expressionSupported(final FrostlakeParser.ExpressionContext condition,
                                        final List<CorrelationScope> scopes) {
        if (!readsDirectly(condition, CorrelationSide.OUTER, scopes)) {
            return true;
        }
        if (condition instanceof FrostlakeParser.ParenExprContext) {
            return predicateSupported(((FrostlakeParser.ParenExprContext) condition).booleanExpr(), scopes);
        }
        if (condition instanceof FrostlakeParser.ComparisonExprContext
                || condition instanceof FrostlakeParser.IsNullExprContext
                || condition instanceof FrostlakeParser.IsDistinctExprContext
                || condition instanceof FrostlakeParser.LikeExprContext
                || condition instanceof FrostlakeParser.RlikeExprContext
                || condition instanceof FrostlakeParser.BetweenExprContext) {
            return allOneSided(condition.getRuleContexts(FrostlakeParser.ExpressionContext.class), scopes);
        }
        if (condition instanceof FrostlakeParser.InListExprContext) {
            final FrostlakeParser.InListExprContext in = (FrostlakeParser.InListExprContext) condition;
            final List<FrostlakeParser.ExpressionContext> operands = new ArrayList<>();
            operands.add(in.expression());
            operands.addAll(in.expressionList().expression());
            return allOneSided(operands, scopes);
        }
        return false;
    }

    private boolean allOneSided(final List<FrostlakeParser.ExpressionContext> operands,
                                final List<CorrelationScope> scopes) {
        for (final FrostlakeParser.ExpressionContext operand : operands) {
            if (readsDirectly(operand, CorrelationSide.OUTER, scopes)
                    && (readsDirectly(operand, CorrelationSide.INNER, scopes)
                        || !aggregateCalls(operand).isEmpty())) {
                return false;
            }
        }
        return true;
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
