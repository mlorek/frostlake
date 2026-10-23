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

import dev.frostlake.executor.expressions.SubqueryAnchor;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A correlated subquery over a derived relation of a shape of its own - one that computes over, groups or joins
 * its tables, or that stands beside another relation in the FROM, joined in or outer - judged as the account judges
 * it, before any row (live-verified). A select-list, HAVING or ORDER BY subquery that is no aggregate and correlates
 * by one equality is refused unless:
 * the outer column it reads is a constant of the relation ({@code SELECT 5 AS id}); the statistics of what the
 * column computes settle the equality ({@code -id AS id} never meets the inner ids, and neither does
 * {@code d.id + o.id}); or the relation is pruned - by a {@code FALSE} or a condition the statistics rule out in
 * its own WHERE, by its own constant {@code WHERE 1 = 0} over a derived relation of its own, or by the statement's
 * constant {@code WHERE 1 = 0} over it, which prunes a computed and a joined relation and not a grouped one. A
 * relation that groups, aggregates, keeps DISTINCT rows, orders, limits or unites selects has no statistics of its
 * columns to settle anything, and one over a table function neither statistics nor its pruning. A correlation of
 * another shape - two conditions, a non-equality, an OR - is refused over such a relation unless each outer column it
 * reads is a constant of its relation or its relation is pruned. A WHERE's subquery keeps the judgement a row makes
 * when it reaches it.
 */
final class DerivedRelationJudgement {

    /** What {@link #substituted} answers for a relation whose columns no statistics describe. */
    private static final String REFUSED = "\u0000refused";

    private final QueryExecutor executor;
    private final FrostlakeParser.SelectStatementContext statement;
    /** The FROM's derived relations by the names the query gives them, upper-cased. */
    private final Map<String, FrostlakeParser.SelectStatementContext> bodies = new LinkedHashMap<>();
    /** The FROM's catalog tables by the names the query gives them, upper-cased. */
    private final Map<String, Table> tables = new LinkedHashMap<>();

    private DerivedRelationJudgement(final QueryExecutor executor,
                                     final FrostlakeParser.SelectStatementContext statement) {
        this.executor = executor;
        this.statement = statement;
    }

    /**
     * The refusal of the first select-list subquery the account refuses over such a relation, or null.
     *
     * @param executor       the executor
     * @param statement      the statement, whose common table expressions a FROM name may read
     * @param clause         the select
     * @param lateralContext the outer row of a query run for one, or null
     * @return the refusal, or null
     */
    static RuntimeException refusal(final QueryExecutor executor,
                                    final FrostlakeParser.SelectStatementContext statement,
                                    final FrostlakeParser.SelectClauseContext clause,
                                    final Map<String, Object> lateralContext) {
        if (lateralContext != null || RelationBody.isActive() || RelationShapeOnly.isActive()
                || SubqueryCompilation.outerNames() != null || clause.tableExpression() == null) {
            return null;
        }
        final DerivedRelationJudgement judgement = new DerivedRelationJudgement(executor, statement);
        if (!judgement.readFrom(clause.tableExpression()) || judgement.bodies.isEmpty()) {
            return null;
        }
        final boolean statementPruned = judgement.prunedByConstant(ParseTreeText.getWhereClause(clause));
        final List<FrostlakeParser.ScalarSubqueryExprContext> subqueries = new ArrayList<>();
        collectSubqueries(clause.selectList(), subqueries);
        if (clause.havingClause() != null) {
            collectSubqueries(clause.havingClause(), subqueries);
        }
        if (statement != null && statement.orderByClause() != null && statement.selectOperand().size() == 1
                && statement.selectOperand(0).selectClause() == clause) {
            collectSubqueries(statement.orderByClause(), subqueries);
        }
        for (final FrostlakeParser.ScalarSubqueryExprContext subquery : subqueries) {
            if (judgement.refuses(subquery, statementPruned)) {
                final Token at = SubqueryAnchor.of(subquery);
                return new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be"
                    + " evaluated at line " + at.getLine() + ", position " + at.getCharPositionInLine()));
            }
        }
        return null;
    }

    /** Reads the FROM's items; false where one is anything but a catalog table or a derived relation. */
    private boolean readFrom(final FrostlakeParser.TableExpressionContext from) {
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>(from.tableReference());
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            if (join.LATERAL() != null || join.ASOF() != null || join.asofMatchCondition() != null) {
                return false;
            }
            references.add(join.tableReference());
        }
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            if (!readReference(reference)) {
                return false;
            }
        }
        return true;
    }

    private boolean readReference(final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || reference.LATERAL() != null || reference.identifierList() != null
                || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.matchRecognizeClause() != null || reference.sampleClause() != null) {
            return false;
        }
        final String alias = reference.aliasName() != null
            ? ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase(Locale.ROOT)
            : reference.nonJoinKeywordIdentifier() != null
                ? SqlIdentifiers.canonicalText(reference.nonJoinKeywordIdentifier().getText()) : null;
        if (source.selectStatement() != null) {
            bodies.put(alias == null ? "" : alias, source.selectStatement());
            return true;
        }
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return false;
        }
        final String written = SqlIdentifiers.canonicalText(source.tableQualifiedName().getText());
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final String named = alias != null ? alias : parts[parts.length - 1];
        final FrostlakeParser.SelectStatementContext common = commonBody(written);
        if (common != null) {
            bodies.put(named, common);
            return true;
        }
        try {
            final View view = executor.getCatalog().resolveView(written);
            if (view != null) {
                if (view.getDefinition() == null
                        || view.getColumnNames() != null && !view.getColumnNames().isEmpty()) {
                    return false;
                }
                bodies.put(named, executor.subqueryStatement(view.getDefinition()));
                return true;
            }
        } catch (final RuntimeException notAView) {
            // looked for as a table next
        }
        final Table table = catalogTable(written);
        if (table == null) {
            return false;
        }
        tables.put(named, table);
        return true;
    }

    /** The body of the statement's common table expression of the name, or null. */
    private FrostlakeParser.SelectStatementContext commonBody(final String written) {
        if (statement == null || statement.withClause() == null || statement.withClause().RECURSIVE() != null) {
            return null;
        }
        for (final FrostlakeParser.CteDefinitionContext common : statement.withClause().cteDefinition()) {
            if (common.columnListOptional() == null
                    && written.equals(SqlIdentifiers.canonicalText(common.nameStartPart().getText()))) {
                return common.selectStatement();
            }
        }
        return null;
    }

    private Table catalogTable(final String written) {
        try {
            final Table table = executor.getCatalog().resolveTable(written);
            return table != null && table.isCatalogResident() ? table : null;
        } catch (final RuntimeException notATable) {
            return null;
        }
    }

    /**
     * Whether the account refuses a select-list subquery: no aggregate, correlated by one equality between its own
     * columns and the outer ones, and none of the exceptions the class comment lists applying.
     */
    private boolean refuses(final FrostlakeParser.ScalarSubqueryExprContext node, final boolean statementPruned) {
        final FrostlakeParser.SelectStatementContext subquery = node.selectStatement();
        if (subquery.withClause() != null || subquery.selectOperand().size() != 1
                || subquery.selectOperand(0).selectClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext inner = subquery.selectOperand(0).selectClause();
        final Map<String, Table> own = new LinkedHashMap<>();
        if (inner.tableExpression() == null || !catalogTables(inner.tableExpression(), own)
                || holdsCall(inner.selectList()) || inner.groupByClause() != null || inner.havingClause() != null) {
            return false;
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(inner);
        if (where == null || readsOuter(inner.selectList(), own)) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(where.booleanExpr(), conjuncts);
        FrostlakeParser.ComparisonExprContext correlation = null;
        final List<FrostlakeParser.BooleanExprContext> correlated = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            if (readsOuter(conjunct, own)) {
                correlated.add(conjunct);
                correlation = equalityOf(conjunct);
            }
        }
        if (correlated.isEmpty()) {
            return false;
        }
        if (correlated.size() > 1 || correlation == null) {
            return refusesOtherCorrelation(correlated, own, statementPruned);
        }
        final boolean leftOuter = readsOuter(correlation.expression(0), own);
        final boolean rightOuter = readsOuter(correlation.expression(1), own);
        if (leftOuter == rightOuter
                || readsOwn(leftOuter ? correlation.expression(0) : correlation.expression(1), own)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext outerSide = leftOuter ? correlation.expression(0)
            : correlation.expression(1);
        final FrostlakeParser.ExpressionContext innerSide = leftOuter ? correlation.expression(1)
            : correlation.expression(0);
        final Map<String, Table> scope = new LinkedHashMap<>(own);
        final String outer = substituted(outerSide, own, scope, statementPruned);
        if (outer == null) {
            return false;
        }
        if (outer.equals(REFUSED)) {
            return true;
        }
        if (ConstantHaving.isConstant(ExpressionEvaluator.parse(outer), executor.getFunctionRegistry())) {
            return false;
        }
        final CorrelatedFilterFold fold = new CorrelatedFilterFold(executor, scope);
        if (OuterNameConstancy.pinsOneValue(fold.rangeOf(outer))) {
            // A value the statistics pin to one point correlates nothing.
            return false;
        }
        return fold.settles(ParseTreeText.getOriginalText(innerSide) + " = (" + outer + ")") == null;
    }

    /**
     * The outer side of the correlation with each outer column written as what its relation computes for it, the
     * relations those read added to the scope; {@link #REFUSED} where a relation has no statistics of its columns,
     * and null where a relation is pruned or a column cannot be read here - the subquery is then answered, or left
     * to the row's judgement.
     */
    private String substituted(final FrostlakeParser.ExpressionContext outerSide, final Map<String, Table> own,
                               final Map<String, Table> scope, final boolean statementPruned) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNames(outerSide, names);
        final String text = ParseTreeText.getOriginalText(outerSide);
        final int base = outerSide.getStart().getStartIndex();
        final StringBuilder written = new StringBuilder(text);
        for (int i = names.size() - 1; i >= 0; i--) {
            final FrostlakeParser.QualifiedNameExprContext name = names.get(i);
            final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
            if (parts.length != 2) {
                return null;
            }
            final String qualifier = parts[0].toUpperCase(Locale.ROOT);
            final String column = parts[1];
            final String replacement;
            if (tables.containsKey(qualifier)) {
                if (own.containsKey(qualifier)) {
                    return null;
                }
                scope.put(qualifier, tables.get(qualifier));
                replacement = qualifier + "." + column;
            } else if (bodies.containsKey(qualifier)) {
                final FrostlakeParser.SelectStatementContext body = bodies.get(qualifier);
                if (readsTableFunction(body)) {
                    return REFUSED;
                }
                if (pruned(body) || statementPruned && !grouped(body) || fromless(body)) {
                    // A relation of one row at most is read row by row, as a FROM-less select's is.
                    return null;
                }
                if (!projects(body)) {
                    return REFUSED;
                }
                final FrostlakeParser.SelectClauseContext select = body.selectOperand(0).selectClause();
                final Map<String, Table> within = new LinkedHashMap<>();
                if (!catalogTables(select.tableExpression(), within)) {
                    return null;
                }
                for (final Map.Entry<String, Table> relation : within.entrySet()) {
                    if (own.containsKey(relation.getKey()) || scope.containsKey(relation.getKey())
                            && scope.get(relation.getKey()) != relation.getValue()) {
                        return null;
                    }
                    scope.put(relation.getKey(), relation.getValue());
                }
                replacement = columnOf(select, column);
                if (replacement == null) {
                    return null;
                }
            } else {
                return null;
            }
            final int from = name.getStart().getStartIndex() - base;
            final int to = name.getStop().getStopIndex() - base + 1;
            written.replace(from, to, "(" + replacement + ")");
        }
        return written.toString();
    }

    /**
     * Whether the account refuses a correlation of another shape than one equality: an outer column it reads over a
     * relation of a shape of its own that nothing prunes refuses it, unless the column is a constant of its relation;
     * a catalog table's column, or one this cannot read, leaves the subquery to the row's judgement.
     */
    private boolean refusesOtherCorrelation(final List<FrostlakeParser.BooleanExprContext> correlated,
                                            final Map<String, Table> own, final boolean statementPruned) {
        boolean refused = false;
        for (final FrostlakeParser.BooleanExprContext conjunct : correlated) {
            final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
            collectNames(conjunct, names);
            for (final FrostlakeParser.QualifiedNameExprContext name : names) {
                final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
                final String qualifier = parts.length == 2 ? parts[0].toUpperCase(Locale.ROOT) : null;
                if (qualifier != null && own.containsKey(qualifier)) {
                    continue;
                }
                final Boolean column = qualifier == null ? null : refusesColumn(qualifier, parts[1], statementPruned);
                if (column == null) {
                    return false;
                }
                refused |= column.booleanValue();
            }
        }
        return refused;
    }

    /** Whether an outer column refuses a correlation of another shape; null where this cannot tell. */
    private Boolean refusesColumn(final String qualifier, final String column, final boolean statementPruned) {
        final FrostlakeParser.SelectStatementContext body = bodies.get(qualifier);
        if (body == null) {
            return null;
        }
        if (readsTableFunction(body)) {
            return Boolean.TRUE;
        }
        if (pruned(body) || statementPruned && !grouped(body) || fromless(body)) {
            return Boolean.FALSE;
        }
        if (!projects(body)) {
            return Boolean.TRUE;
        }
        final String replacement = columnOf(body.selectOperand(0).selectClause(), column);
        if (replacement == null) {
            return null;
        }
        return Boolean.valueOf(!ConstantHaving.isConstant(ExpressionEvaluator.parse(replacement),
            executor.getFunctionRegistry()));
    }

    /** Whether a relation's body reads a table function in its FROM. */
    private static boolean readsTableFunction(final FrostlakeParser.SelectStatementContext body) {
        for (final FrostlakeParser.SelectOperandContext operand : body.selectOperand()) {
            final FrostlakeParser.SelectClauseContext select = operand.selectClause();
            if (select == null || select.tableExpression() == null) {
                continue;
            }
            final List<FrostlakeParser.TableReferenceContext> references =
                new ArrayList<>(select.tableExpression().tableReference());
            for (final FrostlakeParser.JoinClauseContext join : select.tableExpression().joinClause()) {
                references.add(join.tableReference());
            }
            for (final FrostlakeParser.TableReferenceContext reference : references) {
                final FrostlakeParser.TableSourceContext source = reference.tableSource();
                if (source != null && (source.TABLE() != null || source.FLATTEN() != null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** What a projecting relation computes for a column: the select item of its name, or the star's column. */
    private static String columnOf(final FrostlakeParser.SelectClauseContext select, final String column) {
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.StarItemContext) {
                return ((FrostlakeParser.StarItemContext) item).starModifier().isEmpty() ? column : null;
            }
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final FrostlakeParser.ExprItemContext named = (FrostlakeParser.ExprItemContext) item;
            final String alias = named.aliasName() != null ? ParseTreeText.getIdentifier(named.aliasName())
                : named.identifier() != null ? ParseTreeText.getIdentifier(named.identifier())
                : named.bareAliasWord() != null ? ParseTreeText.getIdentifier(named.bareAliasWord()) : null;
            final String value = ParseTreeText.getOriginalText(named.booleanExpr());
            if (alias != null) {
                if (alias.equalsIgnoreCase(column)) {
                    return value;
                }
                continue;
            }
            final FrostlakeParser.ExpressionContext expression = named.booleanExpr()
                instanceof FrostlakeParser.ValueExprContext
                ? ((FrostlakeParser.ValueExprContext) named.booleanExpr()).expression() : null;
            if (expression instanceof FrostlakeParser.QualifiedNameExprContext) {
                final String[] parts = ParseTreeText.qualifiedNameParts(
                    ((FrostlakeParser.QualifiedNameExprContext) expression).qualifiedName());
                if (parts[parts.length - 1].equalsIgnoreCase(column)) {
                    return value;
                }
            }
        }
        return null;
    }

    /** Whether a relation's body is one select that projects its FROM's rows, each row a row of its own. */
    private boolean projects(final FrostlakeParser.SelectStatementContext body) {
        if (body.withClause() != null || body.selectOperand().size() != 1
                || body.selectOperand(0).selectClause() == null || body.orderByClause() != null
                || body.limitClause() != null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = body.selectOperand(0).selectClause();
        return select.DISTINCT() == null && select.groupByClause() == null && select.havingClause() == null
            && select.qualifyClause() == null && select.tableExpression() != null && !holdsCall(select.selectList());
    }

    /** Whether a relation's body is one select with no FROM, of one row at most. */
    private static boolean fromless(final FrostlakeParser.SelectStatementContext body) {
        return body.selectOperand().size() == 1 && body.selectOperand(0).selectClause() != null
            && body.selectOperand(0).selectClause().tableExpression() == null;
    }

    /** Whether a relation's body groups its rows: a GROUP BY, a HAVING or an aggregate in its select list. */
    private boolean grouped(final FrostlakeParser.SelectStatementContext body) {
        for (final FrostlakeParser.SelectOperandContext operand : body.selectOperand()) {
            final FrostlakeParser.SelectClauseContext select = operand.selectClause();
            if (select != null && (select.groupByClause() != null || select.havingClause() != null
                    || holdsCall(select.selectList()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a relation's own WHERE prunes it: a {@code FALSE} or {@code NULL} among its conjuncts, a conjunct the
     * statistics of its tables rule out, or a constant one that folds to FALSE over a derived relation of its own.
     */
    private boolean pruned(final FrostlakeParser.SelectStatementContext body) {
        if (body.selectOperand().size() != 1 || body.selectOperand(0).selectClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = body.selectOperand(0).selectClause();
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (where == null || select.tableExpression() == null) {
            return false;
        }
        final Map<String, Table> within = new LinkedHashMap<>();
        final boolean overTables = catalogTables(select.tableExpression(), within);
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(where.booleanExpr(), conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final String text = ParseTreeText.getOriginalText(conjunct);
            if (isFalseOrNull(text)) {
                return true;
            }
            final boolean constant = ConstantHaving.isConstant(ExpressionEvaluator.parse(text),
                executor.getFunctionRegistry());
            if (constant && !overTables && foldsFalse(text)) {
                return true;
            }
            if (!constant && overTables && Boolean.FALSE.equals(new CorrelatedFilterFold(executor, within)
                    .settles(text))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a statement's WHERE holds a constant conjunct that folds to FALSE or NULL. */
    private boolean prunedByConstant(final FrostlakeParser.WhereClauseContext where) {
        if (where == null) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(where.booleanExpr(), conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final String text = ParseTreeText.getOriginalText(conjunct);
            if (ConstantHaving.isConstant(ExpressionEvaluator.parse(text), executor.getFunctionRegistry())
                    && foldsFalse(text)) {
                return true;
            }
        }
        return false;
    }

    private boolean foldsFalse(final String constant) {
        try {
            final Object value = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                executor).evaluate(constant, null);
            return value == null || Boolean.FALSE.equals(value);
        } catch (final RuntimeException notConstant) {
            return false;
        }
    }

    private static boolean isFalseOrNull(final String text) {
        final String word = text.trim();
        return "FALSE".equalsIgnoreCase(word) || "NULL".equalsIgnoreCase(word);
    }

    /** Whether a FROM holds catalog tables alone, joined by inner joins; each is put in by its name. */
    private boolean catalogTables(final FrostlakeParser.TableExpressionContext from, final Map<String, Table> into) {
        if (from == null) {
            return false;
        }
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>(from.tableReference());
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            if (join.LATERAL() != null || join.ASOF() != null || join.joinType() != null
                    && (join.joinType().LEFT() != null || join.joinType().RIGHT() != null
                        || join.joinType().FULL() != null)) {
                return false;
            }
            references.add(join.tableReference());
        }
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            final FrostlakeParser.TableSourceContext source = reference.tableSource();
            if (source == null || source.tableQualifiedName() == null || source.timeTravelClause() != null
                    || reference.LATERAL() != null || reference.pivotClause() != null
                    || reference.unpivotClause() != null || reference.matchRecognizeClause() != null) {
                return false;
            }
            final String written = SqlIdentifiers.canonicalText(source.tableQualifiedName().getText());
            if (commonBody(written) != null) {
                return false;
            }
            final Table table = catalogTable(written);
            if (table == null) {
                return false;
            }
            final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
            into.put(reference.aliasName() != null
                ? ParseTreeText.getIdentifier(reference.aliasName()).toUpperCase(Locale.ROOT)
                : reference.nonJoinKeywordIdentifier() != null
                    ? SqlIdentifiers.canonicalText(reference.nonJoinKeywordIdentifier().getText())
                    : parts[parts.length - 1], table);
        }
        return true;
    }

    /** Whether a subtree reads a qualified name whose qualifier is none of the subquery's own relations. */
    private static boolean readsOuter(final ParseTree node, final Map<String, Table> own) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNames(node, names);
        for (final FrostlakeParser.QualifiedNameExprContext name : names) {
            final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
            if (parts.length >= 2 && !own.containsKey(parts[parts.length - 2].toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a subtree reads a column of the subquery's own relations, qualified or not. */
    private static boolean readsOwn(final ParseTree node, final Map<String, Table> own) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNames(node, names);
        for (final FrostlakeParser.QualifiedNameExprContext name : names) {
            final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
            if (parts.length < 2 || own.containsKey(parts[parts.length - 2].toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** The equality a conjunct is, or null. */
    private static FrostlakeParser.ComparisonExprContext equalityOf(final FrostlakeParser.BooleanExprContext conjunct) {
        if (!(conjunct instanceof FrostlakeParser.ValueExprContext)) {
            return null;
        }
        final FrostlakeParser.ExpressionContext expression = ((FrostlakeParser.ValueExprContext) conjunct).expression();
        return expression instanceof FrostlakeParser.ComparisonExprContext
                && ((FrostlakeParser.ComparisonExprContext) expression).op.getType() == FrostlakeParser.EQ
            ? (FrostlakeParser.ComparisonExprContext) expression : null;
    }

    /** Whether a subtree calls an aggregate or a window function outside a nested query. */
    private boolean holdsCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.overClause() != null || executor.getFunctionRegistry().hasAggregateFunction(
                    call.functionName().getText().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsCall(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collectSubqueries(final ParseTree node,
                                          final List<FrostlakeParser.ScalarSubqueryExprContext> into) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            into.add((FrostlakeParser.ScalarSubqueryExprContext) node);
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSubqueries(node.getChild(i), into);
        }
    }

    private static void collectNames(final ParseTree node, final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNames(node.getChild(i), into);
        }
    }

    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext predicate,
                                         final List<FrostlakeParser.BooleanExprContext> into) {
        if (predicate instanceof FrostlakeParser.AndExprContext) {
            collectConjuncts(((FrostlakeParser.AndExprContext) predicate).booleanExpr(0), into);
            collectConjuncts(((FrostlakeParser.AndExprContext) predicate).booleanExpr(1), into);
            return;
        }
        if (predicate instanceof FrostlakeParser.ValueExprContext
                && ((FrostlakeParser.ValueExprContext) predicate).expression()
                    instanceof FrostlakeParser.ParenExprContext) {
            collectConjuncts(((FrostlakeParser.ParenExprContext) ((FrostlakeParser.ValueExprContext) predicate)
                .expression()).booleanExpr(), into);
            return;
        }
        into.add(predicate);
    }
}
