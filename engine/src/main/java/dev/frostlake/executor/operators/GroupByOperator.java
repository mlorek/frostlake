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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GROUP BY operator - groups rows and computes aggregates.
 *
 * This operator supports two modes:
 * - EXPLICIT: Groups rows by specified columns and computes aggregates per group
 * - IMPLICIT: Treats all rows as one group (for queries with aggregates but no GROUP BY)
 */
public class GroupByOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(GroupByOperator.class);

    private final List<String> groupByExpressions;
    private final List<String> selectExpressions;
    private final boolean implicitGrouping;
    private final RowExpressionEvaluator columnEvaluator;
    private final AggregateEvaluator aggregateEvaluator;

    /**
     * Create an explicit GROUP BY operator.
     *
     * @param groupByExpressions Expressions to group by (e.g., "dept", "city")
     * @param selectExpressions Expressions in the SELECT list (may include aggregates)
     * @param columnEvaluator Evaluator to evaluate column expressions on a single row
     * @param aggregateEvaluator Evaluator to evaluate aggregate expressions on a group of rows
     */
    public GroupByOperator(final List<String> groupByExpressions,
                          final List<String> selectExpressions,
                          final RowExpressionEvaluator columnEvaluator,
                          final AggregateEvaluator aggregateEvaluator) {
        this.groupByExpressions = groupByExpressions;
        this.selectExpressions = selectExpressions;
        this.implicitGrouping = false;
        this.columnEvaluator = columnEvaluator;
        this.aggregateEvaluator = aggregateEvaluator;
    }

    /**
     * Create an implicit GROUP BY operator (all rows as one group).
     *
     * @param selectExpressions Expressions in the SELECT list (must be aggregates)
     * @param aggregateEvaluator Evaluator to evaluate aggregate expressions on all rows
     */
    public static GroupByOperator implicit(final List<String> selectExpressions,
                                          final AggregateEvaluator aggregateEvaluator) {
        return new GroupByOperator(new ArrayList<>(), selectExpressions, null, aggregateEvaluator);
    }

    private GroupByOperator(final List<String> groupByExpressions,
                           final List<String> selectExpressions,
                           final RowExpressionEvaluator columnEvaluator,
                           final AggregateEvaluator aggregateEvaluator,
                           final boolean implicitGrouping) {
        this.groupByExpressions = groupByExpressions;
        this.selectExpressions = selectExpressions;
        this.implicitGrouping = implicitGrouping;
        this.columnEvaluator = columnEvaluator;
        this.aggregateEvaluator = aggregateEvaluator;
    }

    private static GroupByOperator implicit(final List<String> selectExpressions,
                                           final RowExpressionEvaluator columnEvaluator,
                                           final AggregateEvaluator aggregateEvaluator) {
        return new GroupByOperator(new ArrayList<>(), selectExpressions, columnEvaluator,
            aggregateEvaluator, true);
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (implicitGrouping) {
            return executeImplicitGrouping(input);
        }
        return executeExplicitGrouping(input);
    }

    @Override
    public String getDescription() {
        if (implicitGrouping) {
            return "GROUP BY[implicit, all rows]";
        }

        if (groupByExpressions.size() <= 3) {
            return String.format("GROUP BY[%s]", String.join(", ", groupByExpressions));
        }
        return String.format("GROUP BY[%d columns]", groupByExpressions.size());
    }

    /**
     * Execute implicit grouping (all rows as one group).
     */
    private List<Row> executeImplicitGrouping(final List<Row> input) {
        logger.debug("Executing implicit grouping on {} rows", input.size());

        List<Object> resultValues = new ArrayList<>();

        for (int i = 0; i < selectExpressions.size(); i++) {
            Object value = evaluateAggregate(i, input);
            resultValues.add(value);
        }

        logger.debug("Implicit grouping: {} rows -> 1 row with {} values",
            input.size(), resultValues.size());

        return List.of(new Row(resultValues));
    }

    /**
     * Execute explicit grouping by specified columns.
     */
    private List<Row> executeExplicitGrouping(final List<Row> input) {
        logger.debug("Executing GROUP BY on {} expressions across {} rows",
            groupByExpressions.size(), input.size());

        // Group rows by the specified expressions. Parse the group-by expressions once.
        List<Expression> parsedGroupBy = new ArrayList<>(groupByExpressions.size());
        for (final String e : groupByExpressions) {
            parsedGroupBy.add(ExpressionEvaluator.parse(e));
        }
        Map<List<Object>, List<Row>> groups = new LinkedHashMap<>();

        for (final Row row : input) {
            List<Object> groupKey = buildGroupKey(row, parsedGroupBy);
            List<Row> bucket = groups.get(groupKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(groupKey, bucket);
            }
            bucket.add(row);
        }

        logger.debug("Grouped {} rows into {} groups", input.size(), groups.size());

        // Build result rows for each group
        List<Row> result = new ArrayList<>();

        for (final Map.Entry<List<Object>, List<Row>> entry : groups.entrySet()) {
            List<Row> groupRows = entry.getValue();
            List<Object> resultValues = new ArrayList<>();

            for (int i = 0; i < selectExpressions.size(); i++) {
                Object value = evaluateAggregate(i, groupRows);
                resultValues.add(value);
            }

            result.add(new Row(resultValues));
        }

        logger.debug("GROUP BY: {} rows -> {} groups with {} columns each",
            input.size(), result.size(), selectExpressions.size());

        return result;
    }

    // Sentinel for a group-by expression that failed to evaluate, so such rows still group together
    // (the old String key used the literal "ERROR"); a distinct singleton can't collide with real data.
    private static final Object GROUP_KEY_ERROR = new Object();

    /**
     * Build a composite group key from the group-by expression values. A {@code List<Object>} compares
     * element-wise by value (the same model as JoinOperator's keys and the DISTINCT row key), avoiding
     * the per-row {@code StringBuilder}/{@code toString} of the old string key — and avoiding its
     * collisions (e.g. a literal "NULL" string vs an actual NULL).
     */
    private List<Object> buildGroupKey(final Row row, final List<Expression> parsedGroupBy) {
        if (columnEvaluator == null) {
            throw new IllegalStateException("No column evaluator provided for GROUP BY");
        }

        List<Object> key = new ArrayList<>(parsedGroupBy.size());

        for (int i = 0; i < parsedGroupBy.size(); i++) {
            try {
                key.add(columnEvaluator.evaluate(parsedGroupBy.get(i), row));
            } catch (final Exception e) {
                logger.warn("Failed to evaluate GROUP BY expression '{}': {}", groupByExpressions.get(i), e.getMessage());
                key.add(GROUP_KEY_ERROR);
            }
        }

        return key;
    }

    /**
     * Evaluate an aggregate expression on a group of rows.
     */
    private Object evaluateAggregate(final int index, final List<Row> groupRows) {
        if (aggregateEvaluator == null) {
            throw new IllegalStateException("No aggregate evaluator provided");
        }

        try {
            return aggregateEvaluator.evaluate(index, groupRows);
        } catch (final Exception e) {
            logger.warn("Failed to evaluate aggregate select item [{}]: {}",
                index < selectExpressions.size() ? selectExpressions.get(index) : index, e.getMessage());
            return null;
        }
    }

    /**
     * Create an implicit GROUP BY operator (all rows as one group).
     */
    public static GroupByOperator createImplicit(final List<String> selectExpressions,
                                                 final AggregateEvaluator aggregateEvaluator) {
        return new GroupByOperator(new ArrayList<>(), selectExpressions, null,
            aggregateEvaluator, true);
    }
}
