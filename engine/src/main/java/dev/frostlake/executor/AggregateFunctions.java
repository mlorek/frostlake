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
import dev.frostlake.functions.aggregate.PercentileCont;
import dev.frostlake.functions.aggregate.PercentileDisc;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless aggregate-evaluation helpers extracted from {@link QueryExecutor}: WITHIN GROUP parsing,
 * PERCENTILE_CONT/DISC evaluation, and the simple aggregate value reducer. All pure functions of
 * their arguments — they carry no engine instance state.
 */
public final class AggregateFunctions {

    private AggregateFunctions() {
    }

    /** The ORDER BY of a trailing {@code WITHIN GROUP (...)} on this expression, or null if absent. */
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
            ? new PercentileCont.PercentileContAccumulator(percentile)
            : new PercentileDisc.PercentileDiscAccumulator(percentile);
        for (final Row r : groupRows) {
            acc.accumulate(r.getValue(colIndex));
        }
        return acc.getResult();
    }

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
                    if (val instanceof Number) {
                        numbers.add(val);
                    }
                }
                // SUM preserves integer-ness (SUM of INTs is a whole number, not X.0); empty -> NULL.
                return AggregateNumerics.sum(numbers);
            }
            case "AVG":
                double avg = 0;
                for (final Object val : values) {
                    if (val instanceof Number) {
                        avg += ((Number) val).doubleValue();
                    }
                }
                return avg / values.size();
            case "MIN":
                double min = Double.MAX_VALUE;
                for (final Object val : values) {
                    if (val instanceof Number) {
                        min = Math.min(min, ((Number) val).doubleValue());
                    }
                }
                return min;
            case "MAX":
                double max = Double.MIN_VALUE;
                for (final Object val : values) {
                    if (val instanceof Number) {
                        max = Math.max(max, ((Number) val).doubleValue());
                    }
                }
                return max;
            default:
                return null;
        }
    }
}
