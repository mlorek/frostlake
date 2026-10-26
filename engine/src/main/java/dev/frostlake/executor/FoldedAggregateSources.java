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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Map;

/**
 * The values a write takes from an aggregate over no table - {@code UPDATE t SET n = (SELECT MAX(12345))}, an
 * expression over the aggregate inside the query, {@code INSERT INTO t SELECT MAX(12345)} - which the statement's
 * compilation folds together with the conversion to the column's type: a value out of the column's range is refused
 * bare, {@code Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345}, with no DML envelope
 * (live-verified for UPDATE, MERGE's UPDATE and INSERT ... SELECT). A query of constants alone, {@code (SELECT 12345)},
 * and one that reads a table are converted as the rows are written, and keep the envelope.
 */
final class FoldedAggregateSources {

    private FoldedAggregateSources() {
    }

    /**
     * The SET columns whose value such a query computes.
     *
     * @param executor    the executor that reads the queries
     * @param target      the table the statement writes
     * @param assignments the SET assignments, by column
     * @param setIndexes  each SET column's index in the target
     * @return one flag per target column; null when none is set
     */
    static boolean[] assignments(final QueryExecutor executor, final Table target,
                                 final Map<String, String> assignments, final Map<String, Integer> setIndexes) {
        boolean[] folded = null;
        for (final Map.Entry<String, String> assignment : assignments.entrySet()) {
            final Integer index = setIndexes.get(assignment.getKey());
            if (index == null || !foldsWithItsConversion(executor, assignment.getValue())) {
                continue;
            }
            folded = folded != null ? folded : new boolean[target.getColumns().size()];
            folded[index.intValue()] = true;
        }
        return folded;
    }

    /**
     * Every column, when an INSERT's query is such an aggregate over no table.
     *
     * @param executor the executor that reads the query
     * @param target   the table the statement writes
     * @param query    the INSERT's query
     * @return one flag per target column, all set; null when the query is no such aggregate
     */
    static boolean[] query(final QueryExecutor executor, final Table target,
                           final FrostlakeParser.SelectStatementContext query) {
        if (query == null || !aggregatesNoTable(query, executor.getFunctionRegistry())) {
            return null;
        }
        final boolean[] folded = new boolean[target.getColumns().size()];
        for (int i = 0; i < folded.length; i++) {
            folded[i] = true;
        }
        return folded;
    }

    /**
     * Whether a value is a query aggregating over no table.
     *
     * @param executor  the executor that reads the query
     * @param valueExpr the value as written
     * @return whether the statement's compilation folds it with its conversion
     */
    static boolean foldsWithItsConversion(final QueryExecutor executor, final String valueExpr) {
        final Expression value;
        try {
            value = ExpressionEvaluator.parse(valueExpr);
        } catch (final RuntimeException unparsed) {
            return false;
        }
        if (!(value instanceof SubqueryExpression)) {
            return false;
        }
        try {
            return aggregatesNoTable(executor.parseSelectStatement(((SubqueryExpression) value).getSubquery()),
                executor.getFunctionRegistry());
        } catch (final RuntimeException unreadable) {
            return false;
        }
    }

    /** Whether a query is one FROM-less SELECT, filtered and grouped by nothing, an item of which aggregates. */
    private static boolean aggregatesNoTable(final FrostlakeParser.SelectStatementContext query,
                                             final FunctionRegistry registry) {
        if (query.withClause() != null || query.orderByClause() != null || query.limitClause() != null
                || query.fetchClause() != null || query.selectOperand().size() != 1
                || query.selectOperand(0).selectClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        if (select.tableExpression() != null || ParseTreeText.getWhereClause(select) != null
                || select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null
                || select.DISTINCT() != null || select.topClause() != null) {
            return false;
        }
        return UnreadColumnPruning.containsAggregate(select.selectList(), registry);
    }
}
