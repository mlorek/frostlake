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
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Whether a scalar subquery is a CONSTANT to the account's planner, folded to its value before a WHERE is
 * proven from the statistics (see {@link CountStatisticsBound}). Live-verified:
 *
 * <ul>
 *   <li>a FROM-less SELECT of constants, under a constant WHERE if any, and a SELECT over one:
 *       {@code (SELECT 0)}, {@code (SELECT 0 + 1)}, {@code (SELECT 0 WHERE 1 = 1)},
 *       {@code (SELECT x FROM (SELECT 0 AS x))};</li>
 *   <li>a SELECT the statistics answer — MIN, MAX and COUNT over one catalog table, arithmetic over them
 *       included, under no grouping key but GROUP BY ALL and a WHERE the statistics prove:
 *       {@code (SELECT MIN(j) - 1 FROM t)}, {@code (SELECT COUNT(*) - 2 FROM t)},
 *       {@code (SELECT MIN(j) FROM t GROUP BY ALL)}, {@code (SELECT MIN(j) FROM t WHERE j > 0)}.</li>
 * </ul>
 *
 * <p>Nothing else folds: not a subquery with a LIMIT, an ORDER BY, a set operation, a HAVING or a grouping
 * key; not SUM or AVG; not a join, a derived table beneath the aggregate, or a WHERE the statistics cannot
 * prove; and not a plain column read ({@code (SELECT j FROM t LIMIT 1)}).
 */
final class ConstantSubqueries {

    private ConstantSubqueries() {
    }

    /**
     * Whether the planner folds {@code subquery} to a constant.
     *
     * @param executor the executor that reads the tables
     * @param subquery the subquery
     * @return true when it folds
     */
    static boolean folds(final QueryExecutor executor, final SubqueryExpression subquery) {
        try {
            return folds(executor, executor.parseSelectStatement(subquery.getSubquery()));
        } catch (final RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean folds(final QueryExecutor executor,
                                 final FrostlakeParser.SelectStatementContext statement) {
        if (statement.withClause() != null || statement.orderByClause() != null
                || statement.limitClause() != null || statement.fetchClause() != null
                || statement.selectOperand().size() != 1) {
            return false;
        }
        final FrostlakeParser.SelectOperandContext operand = statement.selectOperand(0);
        if (operand.selectStatement() != null) {
            return folds(executor, operand.selectStatement());
        }
        final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
        if (clause.DISTINCT() != null || clause.topClause() != null || clause.connectByClause() != null
                || clause.havingClause() != null || clause.qualifyClause() != null) {
            return false;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null) {
            return clause.groupByClause() == null && constantItems(clause, false) && constantWhere(clause, false);
        }
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        if (reference.LATERAL() != null || reference.identifierList() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.sampleClause() != null) {
            return false;
        }
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.selectStatement() != null) {
            // A select over a folded relation of one row folds itself.
            return clause.groupByClause() == null && folds(executor, source.selectStatement())
                && constantItems(clause, true) && constantWhere(clause, true);
        }
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return false;
        }
        return answeredFromStatistics(executor, clause, reference);
    }

    /** Whether every select item is a constant — over a folded relation's columns, when {@code overFoldedRow}. */
    private static boolean constantItems(final FrostlakeParser.SelectClauseContext clause,
                                         final boolean overFoldedRow) {
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                if (!overFoldedRow || !SelectItemAccessors.isStarItem(item)) {
                    return false;
                }
                continue;
            }
            final ParserRuleContext text = itemText(item);
            if (text == null
                    || !constant(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(text)), overFoldedRow)) {
                return false;
            }
        }
        return true;
    }

    private static boolean constantWhere(final FrostlakeParser.SelectClauseContext clause,
                                         final boolean overFoldedRow) {
        return clause.whereClause() == null || constant(ExpressionEvaluator.parse(
            ParseTreeText.getOriginalText(clause.whereClause().booleanExpr())), overFoldedRow);
    }

    /** A select item's expression — the whole item where it is a boolean projection. */
    private static ParserRuleContext itemText(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        return value != null ? value : SelectItemAccessors.getItemExpression(item);
    }

    /** A literal, or casts, signs, NOT and operators over literals — and columns of a folded row. */
    private static boolean constant(final Expression expr, final boolean overFoldedRow) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof ColumnReferenceExpression) {
            return overFoldedRow;
        }
        if (expr instanceof CastExpression) {
            return constant(((CastExpression) expr).getExpression(), overFoldedRow);
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            return unary.getOperator() != UnaryOperator.EXISTS && constant(unary.getOperand(), overFoldedRow);
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return constant(binary.getLeft(), overFoldedRow) && constant(binary.getRight(), overFoldedRow)
                && (binary.getEscape() == null || constant(binary.getEscape(), overFoldedRow));
        }
        return false;
    }

    /**
     * One catalog table read through MIN, MAX and COUNT alone, under no grouping key and a WHERE its
     * statistics prove: the account answers the whole select from them.
     */
    private static boolean answeredFromStatistics(final QueryExecutor executor,
                                                  final FrostlakeParser.SelectClauseContext clause,
                                                  final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.GroupByClauseContext groupBy = clause.groupByClause();
        if (groupBy != null && groupBy.ALL() == null) {
            return false;
        }
        final String name = ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName());
        final Table table;
        try {
            table = executor.getCatalog().resolveTableAsWritten(name, "Object");
        } catch (final RuntimeException notATable) {
            return false;
        }
        if (table == null || !table.isCatalogResident()) {
            return false;
        }
        table.setQualifiedName(executor.getFullyQualifiedTableName(name));
        final ExpressionEvaluator types = new ExpressionEvaluator(table, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        int aggregates = 0;
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            final ParserRuleContext text = SelectItemAccessors.isExprItem(item) ? itemText(item) : null;
            if (text == null) {
                return false;
            }
            final int counted = statisticsAggregates(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(text)),
                types);
            if (counted < 0) {
                return false;
            }
            aggregates += counted;
        }
        if (aggregates == 0) {
            return false;
        }
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(reference.aliasName() != null ? reference.aliasName().getText()
            : reference.nonJoinKeywordIdentifier() != null ? reference.nonJoinKeywordIdentifier().getText()
            : table.getName(), table);
        final String where = clause.whereClause() == null ? null
            : ParseTreeText.getOriginalText(clause.whereClause().booleanExpr());
        return !new CountStatisticsBound(executor, clause, table, aliases, where, false, null).isUnbounded();
    }

    /**
     * How many statistics-answered aggregates an item holds — MIN or MAX over a statistics-shaped
     * argument, COUNT over a star, a column or a constant — or -1 when it holds anything else.
     */
    private static int statisticsAggregates(final Expression expr, final ExpressionEvaluator types) {
        if (expr instanceof LiteralExpression) {
            return 0;
        }
        if (expr instanceof CastExpression) {
            return statisticsAggregates(((CastExpression) expr).getExpression(), types);
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            return unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS
                ? statisticsAggregates(unary.getOperand(), types) : -1;
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            final BinaryOperator op = binary.getOperator();
            if (op != BinaryOperator.ADD && op != BinaryOperator.SUBTRACT && op != BinaryOperator.MULTIPLY
                    && op != BinaryOperator.DIVIDE) {
                return -1;
            }
            final int left = statisticsAggregates(binary.getLeft(), types);
            final int right = statisticsAggregates(binary.getRight(), types);
            return left < 0 || right < 0 ? -1 : left + right;
        }
        if (!(expr instanceof FunctionCallExpression)) {
            return -1;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getNameExpression() != null || call.getFunctionName() == null || call.isDistinct()) {
            return -1;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (name.equals("COUNT")) {
            if (call.isStar()) {
                return 1;
            }
            return call.getArguments().size() == 1
                && (call.getArguments().get(0) instanceof LiteralExpression
                    || call.getArguments().get(0) instanceof ColumnReferenceExpression) ? 1 : -1;
        }
        if ((name.equals("MIN") || name.equals("MAX")) && call.getArguments().size() == 1
                && WindowFunctionEvaluator.isStatisticsShaped(call.getArguments().get(0), types)) {
            return 1;
        }
        return -1;
    }
}
