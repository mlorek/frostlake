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

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.aggregate.AggregateNumerics;
import dev.frostlake.functions.aggregate.PercentileContAccumulator;
import dev.frostlake.functions.aggregate.PercentileDiscAccumulator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.values.VariantValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless aggregate-evaluation helpers extracted from {@link QueryExecutor}: WITHIN GROUP parsing,
 * PERCENTILE_CONT/DISC evaluation, and the simple aggregate value reducer. All pure functions of
 * their arguments — they carry no engine instance state.
 */
public final class AggregateFunctions {

    /** Numeric comparison with the integral / same-type BigDecimal fast paths — MIN/MAX call this
     *  per element, so the toString/BigDecimal bridge is reserved for Double/Float and mixed pairs. */
    private static int compareNumbers(final Number a, final Number b) {
        if (isIntegral(a) && isIntegral(b)) {
            return Long.compare(a.longValue(), b.longValue());
        }
        if (a instanceof BigDecimal && b instanceof BigDecimal) {
            return ((BigDecimal) a).compareTo((BigDecimal) b);
        }
        return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
    }

    private static boolean isIntegral(final Object value) {
        return value instanceof Long || value instanceof Integer
            || value instanceof Short || value instanceof Byte;
    }

    private AggregateFunctions() {
    }

    /**
     * The ORDER BY of a trailing {@code WITHIN GROUP (...)} on this expression, or null if absent.
     *
     * @param expr the aggregate call's expression parse tree (null tolerated), scanned child by child
     *             for a {@code WITHIN GROUP} clause
     * @return the clause's ORDER BY parse tree, or null when the expression carries no WITHIN GROUP
     */
    public static FrostlakeParser.OrderByClauseContext withinGroupOrderBy(final FrostlakeParser.ExpressionContext expr) {
        if (expr == null) {
            return null;
        }
        for (int i = 0; i < expr.getChildCount(); i++) {
            if (expr.getChild(i) instanceof FrostlakeParser.WithinGroupClauseContext) {
                return ((FrostlakeParser.WithinGroupClauseContext) expr.getChild(i)).orderByClause();
            }
        }
        return null;
    }

    /**
     * Evaluate {@code PERCENTILE_CONT(p) / PERCENTILE_DISC(p) WITHIN GROUP (ORDER BY <expr>)}: the
     * function argument is the fraction p in [0, 1]; the WITHIN GROUP ORDER BY expression supplies the
     * data values. Reuses the accumulators' interpolation / discrete logic (which sort internally, so
     * the ORDER BY direction is immaterial to the result). Name, argument and ORDER BY column all come
     * from the parse tree.
     *
     * @param funcCtx the PERCENTILE_CONT / PERCENTILE_DISC call's parse tree, carrying the function
     *                name, the fraction argument and the WITHIN GROUP ORDER BY
     * @param groupRows the rows of the current group, whose ORDER-BY-column values feed the accumulator
     * @param table the table the group rows belong to, used to resolve the ORDER BY column to an index
     * @return the continuous (interpolated) or discrete percentile of the group's values, or null for
     *         an empty group
     */
    public static Object evaluatePercentile(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                      final List<Row> groupRows, final Table table) {
        final FrostlakeParser.OrderByClauseContext withinGroup = withinGroupOrderBy(funcCtx);
        if (withinGroup == null || withinGroup.orderItem().isEmpty()) {
            throw new RuntimeException(
                "PERCENTILE_CONT / PERCENTILE_DISC requires a WITHIN GROUP (ORDER BY ...) clause");
        }
        final double percentile;
        try {
            final List<FrostlakeParser.BooleanExprContext> aggArgs =
                ParseTreeText.functionBooleanArgs(funcCtx.functionArgList());
            final String pArg = !aggArgs.isEmpty()
                ? ParseTreeText.getOriginalText(aggArgs.get(0)).trim() : "";
            percentile = Double.parseDouble(pArg);
        } catch (final NumberFormatException e) {
            throw new RuntimeException("PERCENTILE argument must be a numeric literal in [0, 1]");
        }
        final int colIndex = ValueComparisons.getColumnIndex(table,
            ParseTreeText.getOriginalText(withinGroup.orderItem(0).expression()));
        final AggregateFunction.Accumulator acc = "PERCENTILE_CONT".equalsIgnoreCase(funcCtx.functionName().getText())
            ? new PercentileContAccumulator(percentile)
            : new PercentileDiscAccumulator(percentile);
        for (final Row r : groupRows) {
            acc.accumulate(r.getValue(colIndex));
        }
        return acc.getResult();
    }

    /**
     * The simple aggregate value reducer: folds an already-collected list of values with COUNT / SUM /
     * AVG / MIN / MAX, preserving Snowflake's result typing (SUM keeps integer-ness, AVG widens
     * fixed-point inputs, MIN/MAX keep the winning value's original numeric type). COUNT counts every
     * value it is handed — the caller collects exactly what its aggregate should see.
     *
     * @param funcName the aggregate's upper-cased name; anything but the five above yields null
     * @param values the values collected for the group, in row order
     * @return the folded aggregate value, or null for an empty list or an unrecognized function name
     */
    public static Object applyAggregateFunction(final String funcName, final List<Object> values) {
        if (values.isEmpty()) {
            return null;
        }

        switch (funcName) {
            case "COUNT":
                return (long) values.size();
            case "SUM": {
                final List<Object> numbers = new ArrayList<>();
                for (final Object val : values) {
                    if (val instanceof Number || val instanceof VariantValue) {
                        numbers.add(val);
                    }
                }
                // SUM preserves integer-ness (SUM of INTs is a whole number, not X.0); a VARIANT input
                // makes the sum DOUBLE; empty -> NULL.
                return AggregateNumerics.sum(numbers);
            }
            case "AVG":
                // Fixed-point inputs average to a scale-(max+6) BigDecimal, doubles/variants stay double,
                // nulls are ignored and no non-null input is NULL (live-verified Snowflake typing).
                return AggregateNumerics.avg(values);
            case "MIN": {
                // MIN/MAX keep the winning value's ORIGINAL type (MIN of INTEGERs is a Long, not
                // a Double) — live-verified: SYSTEM$TYPEOF(MIN(int_col)) is NUMBER, not FLOAT.
                Number minWinner = null;
                for (final Object val : values) {
                    if (val instanceof Number && (minWinner == null
                            || compareNumbers((Number) val, minWinner) < 0)) {
                        minWinner = (Number) val;
                    }
                }
                return minWinner;
            }
            case "MAX": {
                Number maxWinner = null;
                for (final Object val : values) {
                    if (val instanceof Number && (maxWinner == null
                            || compareNumbers((Number) val, maxWinner) > 0)) {
                        maxWinner = (Number) val;
                    }
                }
                return maxWinner;
            }
            default:
                return null;
        }
    }
}
