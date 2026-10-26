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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Locale;

/**
 * The queries a VALUES item may be whose folding the VALUES rule judges (all live-verified). A query of no table is
 * judged when it aggregates - {@code (SELECT MAX(12345))} and {@code (SELECT COUNT(*))} are refused - and is its
 * value otherwise: {@code (SELECT NULLIF(12345, 0))}, {@code (SELECT 12345 WHERE TRUE)}. A query of one table is
 * judged, and taken only when it folds: an aggregate the statistics answer, or a MIN or a MAX over a table of one
 * row, whatever the column's type - {@code (SELECT MAX(s) FROM t)} over one VARCHAR row is its value, over two it is
 * refused. A query beyond these shapes is read as its value.
 */
final class ValuesSubqueryShapes {

    private ValuesSubqueryShapes() {
    }

    /**
     * Whether the VALUES rule judges a query: one of no table that aggregates, or one that reads one table.
     *
     * @param executor the executor that reads the query
     * @param subquery the query
     * @return whether its folding is judged
     */
    static boolean judged(final QueryExecutor executor, final SubqueryExpression subquery) {
        final FrostlakeParser.SelectClauseContext select = soleSelect(executor, subquery);
        if (select == null) {
            return false;
        }
        if (select.tableExpression() == null) {
            return UnreadColumnPruning.containsAggregate(select.selectList(), executor.getFunctionRegistry());
        }
        return plainTable(executor, select) != null;
    }

    /**
     * Whether a query is MIN and MAX calls alone over the columns of a table of one row, filtered and grouped by
     * nothing, which the plan folds to the row's values.
     *
     * @param executor the executor that reads the query and the table's rows
     * @param subquery the query
     * @return whether it folds so
     */
    static boolean pinnedExtremes(final QueryExecutor executor, final SubqueryExpression subquery) {
        final FrostlakeParser.SelectClauseContext select = soleSelect(executor, subquery);
        if (select == null || select.tableExpression() == null || ParseTreeText.getWhereClause(select) != null
                || select.groupByClause() != null || select.havingClause() != null || select.DISTINCT() != null
                || select.topClause() != null || select.qualifyClause() != null) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext reference = plainTable(executor, select);
        if (reference == null) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final Expression value = ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(((FrostlakeParser.ExprItemContext) item).booleanExpr()));
            if (!(value instanceof FunctionCallExpression)) {
                return false;
            }
            final FunctionCallExpression call = (FunctionCallExpression) value;
            final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
            if (!("MIN".equals(name) || "MAX".equals(name)) || call.isDistinct() || call.getArguments().size() != 1
                    || !(call.getArguments().get(0) instanceof ColumnReferenceExpression)) {
                return false;
            }
        }
        try {
            return executor.statisticsRowsFor(executor.getFullyQualifiedTableName(
                ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName()))).size() == 1;
        } catch (final RuntimeException noRows) {
            return false;
        }
    }

    /** The query's one SELECT, or null for a set operation, a WITH or a row limit's query. */
    private static FrostlakeParser.SelectClauseContext soleSelect(final QueryExecutor executor,
                                                                 final SubqueryExpression subquery) {
        final FrostlakeParser.SelectStatementContext statement;
        try {
            statement = executor.parseSelectStatement(subquery.getSubquery());
        } catch (final RuntimeException unreadable) {
            return null;
        }
        if (statement.withClause() != null || statement.fetchClause() != null
                || statement.selectOperand().size() != 1 || statement.selectOperand(0).selectClause() == null) {
            return null;
        }
        return statement.selectOperand(0).selectClause();
    }

    /** The select's one FROM item when it names a catalog table and nothing more, or null. */
    private static FrostlakeParser.TableReferenceContext plainTable(final QueryExecutor executor,
                                                                   final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        if (reference.LATERAL() != null || reference.identifierList() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                || reference.sampleClause() != null || reference.tableSource() == null
                || reference.tableSource().tableQualifiedName() == null
                || reference.tableSource().timeTravelClause() != null) {
            return null;
        }
        final Table table;
        try {
            table = executor.getCatalog().resolveTableAsWritten(
                ParseTreeText.getQualifiedName(reference.tableSource().tableQualifiedName()), "Object");
        } catch (final RuntimeException notATable) {
            return null;
        }
        return table != null && table.isCatalogResident() ? reference : null;
    }
}
