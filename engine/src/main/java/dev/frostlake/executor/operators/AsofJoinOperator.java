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

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Snowflake {@code ASOF JOIN}: for every left row, the single CLOSEST right row that satisfies the
 * {@code MATCH_CONDITION} — the newest right row at or before the left row's timestamp, and the
 * mirror-image for the {@code <} / {@code <=} directions.
 *
 * <p>Live-verified semantics (probed against a real account):
 * <ul>
 *   <li>The result is LEFT OUTER: a left row with no qualifying right row survives, null-extended over
 *       the right table's columns.</li>
 *   <li>{@code >=} / {@code >} pick the right row with the GREATEST match value among those satisfying
 *       the condition; {@code <=} / {@code <} pick the SMALLEST. Those four are the only operators
 *       Snowflake allows in a MATCH_CONDITION.</li>
 *   <li>A NULL on either side of the comparison never matches, so such right rows are skipped.</li>
 *   <li>The optional {@code ON} / {@code USING} equality clause narrows the candidate set per left row
 *       (it partitions the join); without it every right row is a candidate.</li>
 * </ul>
 */
public class AsofJoinOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(AsofJoinOperator.class);

    private final Table leftTable;
    private final Table rightTable;
    private final List<Row> rightRows;
    private final BinaryOperator matchOperator;
    private final Expression leftMatchExpression;
    private final RowExpressionEvaluator leftMatchEvaluator;
    private final Expression rightMatchExpression;
    private final RowExpressionEvaluator rightMatchEvaluator;
    private final JoinConditionEvaluator partitionEvaluator;

    /**
     * @param leftTable            left input metadata
     * @param rightTable           right input metadata
     * @param rightRows            the right input's rows
     * @param matchOperator        the MATCH_CONDITION comparison ({@code >=}, {@code >}, {@code <=}, {@code <})
     * @param leftMatchExpression  the condition's left operand, evaluated against a LEFT row
     * @param leftMatchEvaluator   evaluator bound to the left input
     * @param rightMatchExpression the condition's right operand, evaluated against a RIGHT row
     * @param rightMatchEvaluator  evaluator bound to the right input
     * @param partitionEvaluator   the ON / USING equality test over a (left, right) pair, or null when absent
     */
    public AsofJoinOperator(final Table leftTable, final Table rightTable, final List<Row> rightRows,
                            final BinaryOperator matchOperator,
                            final Expression leftMatchExpression,
                            final RowExpressionEvaluator leftMatchEvaluator,
                            final Expression rightMatchExpression,
                            final RowExpressionEvaluator rightMatchEvaluator,
                            final JoinConditionEvaluator partitionEvaluator) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.rightRows = rightRows;
        this.matchOperator = matchOperator;
        this.leftMatchExpression = leftMatchExpression;
        this.leftMatchEvaluator = leftMatchEvaluator;
        this.rightMatchExpression = rightMatchExpression;
        this.rightMatchEvaluator = rightMatchEvaluator;
        this.partitionEvaluator = partitionEvaluator;
    }

    @Override
    public List<Row> execute(final List<Row> leftRows, final OperatorContext context) {
        logger.debug("Executing ASOF JOIN: {} rows (left) x {} rows (right)",
            leftRows.size(), rightRows.size());
        final int rightWidth = rightTable.getColumns().size();
        // The right operand depends only on the right row, so evaluate it once per right row.
        final Object[] rightKeys = new Object[rightRows.size()];
        for (int i = 0; i < rightRows.size(); i++) {
            rightKeys[i] = rightMatchEvaluator.evaluate(rightMatchExpression, rightRows.get(i));
        }

        final boolean preferGreater = matchOperator == BinaryOperator.GREATER_THAN
            || matchOperator == BinaryOperator.GREATER_THAN_OR_EQUAL;

        final List<Row> result = new ArrayList<>(leftRows.size());
        for (final Row leftRow : leftRows) {
            final Object leftKey = leftMatchEvaluator.evaluate(leftMatchExpression, leftRow);
            Row best = null;
            Object bestKey = null;
            if (leftKey != null) {
                for (int i = 0; i < rightRows.size(); i++) {
                    final Object rightKey = rightKeys[i];
                    if (rightKey == null || !satisfies(leftKey, rightKey)) {
                        continue;
                    }
                    final Row candidate = rightRows.get(i);
                    if (partitionEvaluator != null && !partitionEvaluator.matches(leftRow, candidate)) {
                        continue;
                    }
                    if (best == null || closer(rightKey, bestKey, preferGreater)) {
                        best = candidate;
                        bestKey = rightKey;
                    }
                }
            }
            final List<Object> combined = new ArrayList<>(leftRow.getValues());
            if (best != null) {
                combined.addAll(best.getValues());
            } else {
                for (int i = 0; i < rightWidth; i++) {
                    combined.add(null);
                }
            }
            result.add(new Row(combined));
        }
        return result;
    }

    /** The MATCH_CONDITION comparison itself, over two non-NULL values. */
    private boolean satisfies(final Object leftKey, final Object rightKey) {
        final int cmp = ValueComparisons.compareValues(leftKey, rightKey);
        switch (matchOperator) {
            case GREATER_THAN:
                return cmp > 0;
            case GREATER_THAN_OR_EQUAL:
                return cmp >= 0;
            case LESS_THAN:
                return cmp < 0;
            case LESS_THAN_OR_EQUAL:
                return cmp <= 0;
            default:
                return false;
        }
    }

    /** True when {@code candidate} is a closer match than the incumbent {@code best}. */
    private boolean closer(final Object candidate, final Object best, final boolean preferGreater) {
        final int cmp = ValueComparisons.compareValues(candidate, best);
        return preferGreater ? cmp > 0 : cmp < 0;
    }

    @Override
    public String getDescription() {
        return String.format("ASOF JOIN[%s x %s MATCH_CONDITION %s %s %s]",
            leftTable.getName(), rightTable.getName(),
            leftMatchExpression, matchOperator, rightMatchExpression);
    }
}
