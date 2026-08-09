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
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.RowOrdinal;
import dev.frostlake.metastore.model.Table;
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
    private List<SourcePosition> expressionOrigins;
    private final List<String> columnAliases;
    private final RowExpressionEvaluator expressionEvaluator;
    private final Map<String, Object> lateralAliasSink;
    private final Map<String, Object> baseBindings;

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
        this(projectionExpressions, columnAliases, expressionEvaluator, null);
    }

    /**
     * Create a PROJECT operator with lateral column-alias support.
     *
     * @param lateralAliasSink a map SHARED with {@code expressionEvaluator}'s lateral context: as each aliased
     *     SELECT item is evaluated left-to-right, its (uppercased) alias → value is written here so a LATER item
     *     in the same SELECT list can reference it (Snowflake lateral column aliases). Cleared per input row.
     *     When null, no lateral-alias values are exposed to later items (only the exact-whole-item-is-an-alias
     *     shortcut applies).
     */
    public ProjectOperator(final List<String> projectionExpressions,
                          final List<String> columnAliases,
                          final RowExpressionEvaluator expressionEvaluator,
                          final Map<String, Object> lateralAliasSink) {
        this(projectionExpressions, columnAliases, expressionEvaluator, lateralAliasSink, null);
    }

    /**
     * As {@link #ProjectOperator(List, List, RowExpressionEvaluator, Map)}, additionally carrying
     * the OUTER bindings of a LATERAL / correlated execution.
     *
     * @param baseBindings outer-row name → value bindings re-seeded into {@code lateralAliasSink}
     *     after its per-row reset, so a SELECT item may reference an outer alias by qualified name
     *     ({@code SELECT fa.asset_key} inside {@code LEFT JOIN LATERAL (…)}) — the sink is the
     *     evaluator's lateral context, and its per-row clear would otherwise drop them. Item
     *     aliases fill in later, so they shadow a same-named outer key; null when the query does
     *     not execute under an outer row.
     */
    public ProjectOperator(final List<String> projectionExpressions,
                          final List<String> columnAliases,
                          final RowExpressionEvaluator expressionEvaluator,
                          final Map<String, Object> lateralAliasSink,
                          final Map<String, Object> baseBindings) {
        this.projectionExpressions = projectionExpressions;
        this.columnAliases = columnAliases;
        this.expressionEvaluator = expressionEvaluator;
        this.lateralAliasSink = lateralAliasSink;
        this.baseBindings = baseBindings;

        if (columnAliases != null && projectionExpressions.size() != columnAliases.size()) {
            throw new IllegalArgumentException(
                "Number of projection expressions must match number of aliases");
        }
    }

    /** Where item {@code index} began in the statement, or null when it was synthesised. */
    private SourcePosition originOf(final int index) {
        return expressionOrigins != null && index < expressionOrigins.size()
            ? expressionOrigins.get(index) : null;
    }

    /**
     * Where each projection item began in the statement, index-aligned with the expressions. Null
     * entries are synthesised items (star expansion), which nobody wrote and which therefore report
     * no position.
     */
    public void setExpressionOrigins(final List<SourcePosition> expressionOrigins) {
        this.expressionOrigins = expressionOrigins;
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

        for (int rowIndex = 0; rowIndex < input.size(); rowIndex++) {
            final Row row = input.get(rowIndex);
            // Number the rows this operator was handed, so SEQ1/2/4/8 read one value per row rather
            // than one per call. Restored after each row because a scalar subquery inside the
            // projection runs its own operators, which number their own rows.
            final Long displacedOrdinal = RowOrdinal.begin(rowIndex);
            try {
            List<Object> projectedValues = new ArrayList<>();
            // The running map of this row's already-computed aliases. When a shared sink was provided it IS
            // that map (the evaluator reads it as its lateral context, so a later item's expression can
            // reference an earlier alias, e.g. `x + 1 AS y` after `... AS x`); otherwise a private map that
            // still powers the exact-whole-item-is-an-alias shortcut. Reset per row so aliases don't leak.
            final Map<String, Object> rowAliasValues = lateralAliasSink != null ? lateralAliasSink : new HashMap<>();
            rowAliasValues.clear();
            if (baseBindings != null) {
                rowAliasValues.putAll(baseBindings);
            }

            for (int i = 0; i < projectionExpressions.size(); i++) {
                String expr = projectionExpressions.get(i).trim();
                Object value;
                // If the expression is exactly a previously-defined alias, reuse that value — but only
                // when no FROM-source column has that name: the real column takes precedence over a
                // same-named sibling alias (what makes a swap projection `SELECT t AS s, s AS t` read
                // both values from the input row instead of collapsing to t, t).
                if (rowAliasValues.containsKey(expr.toUpperCase()) && !isInputColumn(expr, context)) {
                    value = rowAliasValues.get(expr.toUpperCase());
                } else {
                    // Note where this item began in the statement, so a message about an unresolvable
                    // column inside it can carry the position live always reports. A star-expanded
                    // item has no origin — nobody wrote it — and reports none.
                    final SourcePosition displaced = ExpressionSource.begin(originOf(i));
                    try {
                        value = evaluateExpression(parsedExpressions.get(i), row);
                    } finally {
                        ExpressionSource.end(displaced);
                    }
                }
                projectedValues.add(value);
                if (columnAliases != null && columnAliases.get(i) != null) {
                    rowAliasValues.put(columnAliases.get(i).toUpperCase(), value);
                }
            }

            projectedRows.add(Row.of(projectedValues));
            } finally {
                RowOrdinal.end(displacedOrdinal);
            }
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
     * Whether {@code name} is a real column of the FROM sources feeding this projection (the single/combined
     * input table, or any joined table in scope).
     */
    private boolean isInputColumn(final String name, final OperatorContext context) {
        if (context == null) {
            return false;
        }
        if (context.getTable() != null && context.getTable().hasColumn(name)) {
            return true;
        }
        if (context.getAllTables() != null) {
            for (final Table joined : context.getAllTables()) {
                if (joined != null && joined.hasColumn(name)) {
                    return true;
                }
            }
        }
        return false;
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
