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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PROJECT operator - projects (selects) specific columns from rows.
 *
 * This operator handles the SELECT list evaluation for non-aggregate queries.
 * It evaluates expressions for each row and creates new rows with only the
 * selected columns.
 */
public class ProjectOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(ProjectOperator.class);

    private final List<String> projectionExpressions;
    private final List<String> columnAliases;
    private final RowExpressionEvaluator expressionEvaluator;

    /**
     * Create a PROJECT operator.
     *
     * @param projectionExpressions List of expressions to evaluate (e.g., "name", "age + 1", "COUNT(*)")
     * @param columnAliases List of column aliases (null entries allowed for no alias)
     * @param expressionEvaluator Evaluator to evaluate expressions on a row
     */
    public ProjectOperator(final List<String> projectionExpressions,
                          final List<String> columnAliases,
                          final RowExpressionEvaluator expressionEvaluator) {
        this.projectionExpressions = projectionExpressions;
        this.columnAliases = columnAliases;
        this.expressionEvaluator = expressionEvaluator;

        if (columnAliases != null && projectionExpressions.size() != columnAliases.size()) {
            throw new IllegalArgumentException(
                "Number of projection expressions must match number of aliases");
        }
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (projectionExpressions.isEmpty()) {
            logger.debug("No projection expressions, returning input as-is");
            return input;
        }

        logger.debug("Projecting {} expressions across {} rows",
            projectionExpressions.size(), input.size());

        List<Row> projectedRows = new ArrayList<>();

        // Parse each projection once; evaluate the AST per row (no per-row re-parse).
        List<Expression> parsedExpressions = new ArrayList<>(projectionExpressions.size());
        for (final String e : projectionExpressions) {
            parsedExpressions.add(ExpressionEvaluator.parse(e.trim()));
        }

        for (final Row row : input) {
            List<Object> projectedValues = new ArrayList<>();
            Map<String, Object> rowAliasValues = new HashMap<>();

            for (int i = 0; i < projectionExpressions.size(); i++) {
                String expr = projectionExpressions.get(i).trim();
                Object value;
                // If the expression is exactly a previously-defined alias, reuse that value
                if (rowAliasValues.containsKey(expr.toUpperCase())) {
                    value = rowAliasValues.get(expr.toUpperCase());
                } else {
                    value = evaluateExpression(parsedExpressions.get(i), row);
                }
                projectedValues.add(value);
                if (columnAliases != null && columnAliases.get(i) != null) {
                    rowAliasValues.put(columnAliases.get(i).toUpperCase(), value);
                }
            }

            projectedRows.add(Row.of(projectedValues));
        }

        logger.debug("Projection: {} rows -> {} rows with {} columns",
            input.size(), projectedRows.size(), projectionExpressions.size());

        return projectedRows;
    }

    @Override
    public String getDescription() {
        if (projectionExpressions.size() <= 3) {
            return String.format("PROJECT[%s]", String.join(", ", projectionExpressions));
        }
        return String.format("PROJECT[%d columns]", projectionExpressions.size());
    }

    /**
     * Evaluate an expression on a row.
     */
    private Object evaluateExpression(final Expression expr, final Row row) {
        if (expressionEvaluator == null) {
            throw new IllegalStateException("No expression evaluator provided");
        }

        return expressionEvaluator.evaluate(expr, row);
    }
}
