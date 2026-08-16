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

import dev.frostlake.executor.AmbiguousColumnException;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.InvalidQualifierException;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.RowOrdinal;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * WHERE clause operator - filters rows based on a predicate expression.
 *
 * This operator supports three evaluation modes:
 * - SIMPLE: For single-table queries
 * - WITH_ALIASES: For multi-table JOINs with table aliases
 * - WITH_LATERAL_CONTEXT: For LATERAL joins with outer row context
 */
public class WhereOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(WhereOperator.class);

    private final String whereExpression;
    private final WhereEvaluationMode mode;

    public WhereOperator(final String whereExpression, final WhereEvaluationMode mode) {
        this.whereExpression = whereExpression;
        this.mode = mode;
    }

    public WhereOperator(final String whereExpression) {
        this(whereExpression, WhereEvaluationMode.SIMPLE);
    }

    /**
     * Create a WHERE operator with automatic mode detection.
     */
    public static WhereOperator create(final String whereExpression, final OperatorContext context) {
        final WhereEvaluationMode mode;
        if (context.hasLateralContext()) {
            mode = WhereEvaluationMode.WITH_LATERAL_CONTEXT;
        } else if (context.hasMultipleTables()) {
            mode = WhereEvaluationMode.WITH_ALIASES;
        } else {
            mode = WhereEvaluationMode.SIMPLE;
        }
        return new WhereOperator(whereExpression, mode);
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (whereExpression == null || whereExpression.trim().isEmpty()) {
            return input;
        }

        logger.debug("Applying WHERE filter: {} (mode: {})", whereExpression, mode);

        switch (mode) {
            case WITH_LATERAL_CONTEXT:
                return filterWithLateralContext(input, context);
            case WITH_ALIASES:
                return filterWithAliases(input, context);
            case SIMPLE:
            default:
                return filterSimple(input, context);
        }
    }

    @Override
    public String getDescription() {
        return String.format("WHERE[%s, mode=%s]",
            whereExpression.length() > 50 ? whereExpression.substring(0, 47) + "..." : whereExpression,
            mode);
    }

    private Catalog getCatalog(final OperatorContext context) {
        if (context.getQueryExecutor() != null) {
            return context.getQueryExecutor().getCatalog();
        }
        return null;
    }

    /**
     * Simple filtering for single table queries.
     */
    private List<Row> filterSimple(final List<Row> rows, final OperatorContext context) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(
            context.getTable(),
            context.getFunctionRegistry(),
            getCatalog(context),
            context.getQueryExecutor()
        );
        // Pass lateral context for nested correlated subqueries
        if (context.getLateralContext() != null) {
            evaluator.setOuterLateralContext(context.getLateralContext());
        }
        // Give the evaluator the FROM alias too (single table, so one entry): a correlated subquery in this
        // WHERE references the outer row by that alias (WHERE t.id = s.id), and the subquery's outer-row
        // context is assembled from the evaluator's alias map. Without it the context held only
        // TABLENAME.col keys, the alias-qualified reference missed, and the strip-qualifier fallback bound
        // it to the INNER table's same-named column — turning the correlation into t.id = t.id (always true).
        if (context.getAliasToTable() != null && !context.getAliasToTable().isEmpty()) {
            evaluator.setMultiTableContext(context.getAliasToTable(), context.getAllTables());
        }
        // Parse the predicate once, then evaluate the AST per row.
        final Expression parsed = ExpressionEvaluator.parse(whereExpression);
        evaluator.validatePredicate(parsed);
        final List<Row> filtered = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final Row row = rows.get(rowIndex);
            // Number the rows the filter reads, so a SEQ1/2/4/8 in the predicate counts them.
            final Long displacedOrdinal = RowOrdinal.begin(rowIndex);
            try {
                final Object result = evaluator.evaluate(parsed, row);
                if (SqlTruth.isTrue(result)) {
                    filtered.add(row);
                }
            } finally {
                RowOrdinal.end(displacedOrdinal);
            }
        }

        logger.debug("WHERE filter: {} -> {} rows", rows.size(), filtered.size());
        return filtered;
    }

    /**
     * Alias-aware filtering for multi-table queries with JOINs.
     * Falls back to simple evaluation if alias resolution fails.
     */
    /** One-shot predicate-type validation (Snowflake rejects VARCHAR/NUMBER-typed conditions). */
    private void validatePredicateOnce(final Expression parsed, final OperatorContext context) {
        final ExpressionEvaluator typeChecker = new ExpressionEvaluator(
            context.getTable(),
            context.getFunctionRegistry(),
            getCatalog(context),
            context.getQueryExecutor()
        );
        if (context.getAliasToTable() != null && !context.getAliasToTable().isEmpty()) {
            typeChecker.setMultiTableContext(context.getAliasToTable(), context.getAllTables());
        }
        typeChecker.validatePredicate(parsed);
    }

    private List<Row> filterWithAliases(final List<Row> rows, final OperatorContext context) {
        final List<Row> filtered = new ArrayList<>();
        final Expression parsed = ExpressionEvaluator.parse(whereExpression);
        validatePredicateOnce(parsed, context);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final Row row = rows.get(rowIndex);
            final Long displacedOrdinal = RowOrdinal.begin(rowIndex);
            try {
                // Try to evaluate with alias support
                final Object result = evaluateWithAliases(parsed, row, context);
                if (SqlTruth.isTrue(result)) {
                    filtered.add(row);
                }
            } catch (final AmbiguousColumnException ambiguous) {
                // Definitive — live compiles this to "ambiguous column name": no lenient retry.
                throw ambiguous;
            } catch (final InvalidQualifierException invalidQualifier) {
                // Also definitive — an alias REPLACES the table name; the keyless fallback below
                // would quietly resolve what live rejects.
                throw invalidQualifier;
            } catch (final Exception e) {
                logger.warn("Failed to evaluate WHERE clause with aliases: {}, trying simple evaluation",
                    e.getMessage());
                // Fallback to simple evaluation
                try {
                    final ExpressionEvaluator evaluator = new ExpressionEvaluator(
                        context.getTable(),
                        context.getFunctionRegistry(),
                        getCatalog(context),
                        context.getQueryExecutor()
                    );
                    final Object result = evaluator.evaluate(parsed, row);
                    if (SqlTruth.isTrue(result)) {
                        filtered.add(row);
                    }
                } catch (final Exception e2) {
                    logger.error("WHERE clause evaluation failed completely: {}", e2.getMessage());
                }
            } finally {
                RowOrdinal.end(displacedOrdinal);
            }
        }

        logger.debug("WHERE filter (with aliases): {} -> {} rows", rows.size(), filtered.size());
        return filtered;
    }

    /**
     * Lateral context filtering for LATERAL joins.
     * Falls back to alias-aware evaluation if lateral context is not available.
     */
    private List<Row> filterWithLateralContext(final List<Row> rows, final OperatorContext context) {
        if (!context.hasLateralContext()) {
            logger.warn("Lateral context not available, falling back to alias-aware evaluation");
            return filterWithAliases(rows, context);
        }

        final List<Row> filtered = new ArrayList<>();
        final Expression parsed = ExpressionEvaluator.parse(whereExpression);
        validatePredicateOnce(parsed, context);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final Row row = rows.get(rowIndex);
            final Long displacedOrdinal = RowOrdinal.begin(rowIndex);
            try {
                // For lateral context, we need to evaluate with outer row values
                // This is a placeholder - full implementation would require QueryExecutor integration
                final Object result = evaluateWithAliases(parsed, row, context);
                if (SqlTruth.isTrue(result)) {
                    filtered.add(row);
                }
            } catch (final AmbiguousColumnException ambiguous) {
                throw ambiguous;
            } catch (final InvalidQualifierException invalidQualifier) {
                throw invalidQualifier;
            } catch (final Exception e) {
                logger.warn("Failed to evaluate WHERE clause with lateral context: {}", e.getMessage());
            } finally {
                RowOrdinal.end(displacedOrdinal);
            }
        }

        logger.debug("WHERE filter (with lateral): {} -> {} rows", rows.size(), filtered.size());
        return filtered;
    }

    /**
     * Evaluate expression with alias and multi-table support.
     * Uses custom expression evaluator from context if available, otherwise falls back to simple evaluation.
     */
    private Object evaluateWithAliases(final Expression expr, final Row row, final OperatorContext context) {
        // Use custom expression evaluator if available (for JOIN queries)
        if (context.hasCustomExpressionEvaluator()) {
            return context.getExpressionEvaluator().evaluate(expr, row);
        }

        // Fallback to simple evaluation
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(
            context.getTable(),
            context.getFunctionRegistry(),
            getCatalog(context)
        );
        return evaluator.evaluate(expr, row);
    }
}
