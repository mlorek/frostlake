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

import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.values.ValueRange;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whether a correlated filter settles over the statistics of the relations it reads. Live's planner answers
 * from column statistics before it decides whether it can plan the correlation, so a condition its intervals
 * settle — true on every row, or false on every row — leaves no correlation to plan and the subquery runs.
 * The same shape is refused where the intervals leave it open: over {@code g (id)} holding 5 and 6 beside
 * {@code fz (id)} holding 5 and 7, {@code g.id + fz.id > 0} and {@code < 10} are answered while {@code > 10}
 * and {@code = 10} are refused, and {@code h.k = g.id + fz.id} is answered because h.k's values lie outside
 * the sum's interval (live-verified).
 *
 * <p>The scope holds the subquery's own relations beside the outer query's, so both sides of a mixed operand
 * carry their statistics. A relation neither side names, a column of no exact-numeric type and a condition
 * the interval channel does not model all leave the condition open, which keeps the refusal.
 */
final class CorrelatedFilterFold {

    private final ExpressionEvaluator scope;

    /**
     * @param executor  the executor whose catalog the relations are read from
     * @param relations the relations in scope — the subquery's and the outer query's — keyed by the name
     *                  each is reached by
     */
    CorrelatedFilterFold(final QueryExecutor executor, final Map<String, Table> relations) {
        final Map<String, Table> keyed = new LinkedHashMap<>();
        final List<Table> tables = new ArrayList<>();
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if (relation.getValue() == null) {
                continue;
            }
            keyed.put(relation.getKey().toUpperCase(), relation.getValue());
            if (!tables.contains(relation.getValue())) {
                tables.add(relation.getValue());
            }
        }
        this.scope = new ExpressionEvaluator(unextendedRelation(tables),
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        this.scope.setMultiTableContext(keyed, tables);
    }

    /**
     * The relations as one relation none of them is NULL-extended in: a filter's statistics are the ones
     * each relation holds, since nothing here joins them. Without the record every column of a scope of
     * several relations would be read as one a join may extend, which settles nothing.
     */
    private static Table unextendedRelation(final List<Table> tables) {
        if (tables.isEmpty()) {
            return null;
        }
        final List<TableColumn> columns = new ArrayList<>();
        for (final Table relation : tables) {
            columns.addAll(relation.getColumns());
        }
        final Table merged = new Table("joined", columns, false);
        Table record = tables.get(0);
        for (int i = 1; i < tables.size(); i++) {
            final Table next = new Table("joined", new ArrayList<TableColumn>(), false);
            next.setJoinedRelations(JoinedRelations.joining(record, tables.get(i), false, false));
            record = next;
        }
        merged.setJoinedRelations(tables.size() == 1
            ? JoinedRelations.joining(tables.get(0), tables.get(0), false, false) : record.getJoinedRelations());
        return merged;
    }

    /**
     * Whether the statistics settle a written condition.
     *
     * @param conditionText the condition as written
     * @return TRUE where it holds on every row, FALSE where it holds on none, null where the statistics
     *         leave it open — including a condition that does not parse or read here
     */
    Boolean settles(final String conditionText) {
        try {
            return scope.settledCondition(ExpressionEvaluator.parse(conditionText));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * The interval an expression's values lie in over the relations' statistics.
     *
     * @param expressionText the expression as written
     * @return the interval, or null where none is known — including an expression that does not parse or read here
     */
    ValueRange rangeOf(final String expressionText) {
        try {
            return scope.inferStaticRange(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }
}
