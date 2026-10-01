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

import dev.frostlake.executor.DeferredFault;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import dev.frostlake.values.VariantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JOIN operator - joins two sets of rows based on a condition.
 *
 * This operator supports multiple join types:
 * - INNER JOIN: Returns rows when there's a match in both tables
 * - LEFT JOIN: Returns all rows from left table, matched rows from right
 * - RIGHT JOIN: Returns all rows from right table, matched rows from left
 * - FULL JOIN: Returns all rows when there's a match in either table
 * - CROSS JOIN: Returns Cartesian product of both tables
 */
public class JoinOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(JoinOperator.class);

    private final Table leftTable;
    private final Table rightTable;
    private final RowsProvider rightSide;
    private List<Row> rightRows;
    private final JoinType joinType;
    private final String joinCondition;
    private final JoinConditionEvaluator conditionEvaluator;
    // Equi-join key column indices (left-table-relative, right-table-relative). When set, the join
    // hash-partitions the right side by these keys and probes per left row, re-confirming each
    // candidate pair with conditionEvaluator. Null => nested-loop.
    private int[] leftKeyIdx;
    private int[] rightKeyIdx;
    private static final Object NULL_KEY = new Object();

    /**
     * Create a JOIN operator with a condition evaluator.
     *
     * @param leftTable Left table metadata
     * @param rightTable Right table metadata
     * @param rightRows Rows from the right table
     * @param joinType Type of join to perform
     * @param joinCondition Join condition expression (null for CROSS JOIN)
     * @param conditionEvaluator Evaluator to evaluate join condition on pair of rows (null for CROSS JOIN)
     */
    public JoinOperator(final Table leftTable, final Table rightTable, final List<Row> rightRows,
                       final JoinType joinType, final String joinCondition,
                       final JoinConditionEvaluator conditionEvaluator) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.rightSide = RowsProvider.of(rightRows);
        this.joinType = joinType;
        this.joinCondition = joinCondition;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * Create a CROSS JOIN operator (no condition).
     */
    /**
     * A join whose right side is read when the join runs — a relation planned as a pipeline of its own.
     *
     * @param leftTable          the left relation
     * @param rightTable         the right relation's shape
     * @param rightSide          the right relation's rows, read when the join runs
     * @param joinType           the join type
     * @param joinCondition      the condition's text, or null
     * @param conditionEvaluator the condition, or null for a cross join
     */
    public JoinOperator(final Table leftTable, final Table rightTable, final RowsProvider rightSide,
                        final JoinType joinType, final String joinCondition,
                        final JoinConditionEvaluator conditionEvaluator) {
        this.leftTable = leftTable;
        this.rightTable = rightTable;
        this.rightSide = rightSide;
        this.joinType = joinType;
        this.joinCondition = joinCondition;
        this.conditionEvaluator = conditionEvaluator;
    }

    public static JoinOperator cross(final Table leftTable, final Table rightTable, final List<Row> rightRows) {
        return new JoinOperator(leftTable, rightTable, rightRows, JoinType.CROSS, null, null);
    }

    /** A cross join whose right side is read when the join runs. */
    public static JoinOperator cross(final Table leftTable, final Table rightTable, final RowsProvider rightSide) {
        return new JoinOperator(leftTable, rightTable, rightSide, JoinType.CROSS, null, null);
    }

    /** Enable hash-join candidate filtering using the given equi-join key column indices. */
    public JoinOperator withEquiKeys(final int[] leftKeyIdx, final int[] rightKeyIdx) {
        this.leftKeyIdx = leftKeyIdx;
        this.rightKeyIdx = rightKeyIdx;
        return this;
    }

    @Override
    public List<Row> execute(final List<Row> leftRows, final OperatorContext context) {
        rightRows = rightSide.rows();
        logger.debug("Executing {} JOIN: {} rows (left) x {} rows (right)",
            joinType, leftRows.size(), rightRows.size());

        if (joinType == JoinType.CROSS || (joinCondition == null && conditionEvaluator == null)) {
            // A comma join whose equality was pushed down from WHERE arrives here WITH keys and
            // WITHOUT a condition: the keys ARE the condition, so the hash path applies and the
            // cartesian product is never built.
            if (leftKeyIdx != null && rightKeyIdx != null) {
                return executeHashJoin(leftRows);
            }
            return executeCrossJoin(leftRows);
        }

        if (leftKeyIdx != null && rightKeyIdx != null) {
            return executeHashJoin(leftRows);
        }

        return executeConditionalJoin(leftRows);
    }

    @Override
    public String getDescription() {
        // A USING or NATURAL join matches by key through its evaluator and carries no condition text.
        final String rightSideRead = rightSide.describe() == null ? "" : "{" + rightSide.describe() + "}";
        if (joinType == JoinType.CROSS || joinCondition == null) {
            return String.format("%s JOIN[%s x %s]%s",
                joinType, leftTable.getName(), rightTable.getName(), rightSideRead);
        }

        final String conditionPreview = joinCondition.length() > 30
            ? joinCondition.substring(0, 27) + "..."
            : joinCondition;

        return String.format("%s JOIN[%s x %s ON %s]%s",
            joinType, leftTable.getName(), rightTable.getName(), conditionPreview, rightSideRead);
    }

    /**
     * Execute CROSS JOIN - Cartesian product of left and right rows.
     */
    private List<Row> executeCrossJoin(final List<Row> leftRows) {
        final List<Row> resultRows = new ArrayList<>();

        for (final Row leftRow : leftRows) {
            for (final Row rightRow : rightRows) {
                final Row combinedRow = combineRows(leftRow, rightRow);
                resultRows.add(combinedRow);
            }
        }

        logger.debug("CROSS JOIN: {} x {} -> {} rows",
            leftRows.size(), rightRows.size(), resultRows.size());

        return resultRows;
    }

    /**
     * Execute conditional JOIN (INNER, LEFT, RIGHT, FULL).
     */
    private List<Row> executeConditionalJoin(final List<Row> leftRows) {
        final List<Row> resultRows = new ArrayList<>();
        final Set<Integer> matchedRightRows = new HashSet<>();

        // Process each left row
        for (final Row leftRow : leftRows) {
            boolean leftMatchFound = false;

            // Try to match with each right row
            for (int rightIdx = 0; rightIdx < rightRows.size(); rightIdx++) {
                final Row rightRow = rightRows.get(rightIdx);

                // Evaluate join condition
                final boolean matches = evaluateCondition(leftRow, rightRow);

                if (matches) {
                    resultRows.add(combineRows(leftRow, rightRow));
                    leftMatchFound = true;
                    matchedRightRows.add(rightIdx);
                }
            }

            // Handle LEFT JOIN or FULL JOIN - include left row even if no match
            if (!leftMatchFound && (joinType == JoinType.LEFT || joinType == JoinType.FULL)) {
                resultRows.add(combineRowsWithNullRight(leftRow));
            }
        }

        // Handle RIGHT JOIN or FULL JOIN - include unmatched right rows
        if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
            for (int rightIdx = 0; rightIdx < rightRows.size(); rightIdx++) {
                if (!matchedRightRows.contains(rightIdx)) {
                    resultRows.add(combineRowsWithNullLeft(rightRows.get(rightIdx)));
                }
            }
        }

        logger.debug("{} JOIN: {} rows (left) x {} rows (right) -> {} rows",
            joinType, leftRows.size(), rightRows.size(), resultRows.size());

        return resultRows;
    }

    /**
     * Hash join for equi-join conditions: partition the right rows by their join-key value(s), then
     * probe per left row. The extracted keys are a CANDIDATE FILTER only — every candidate pair is
     * still re-confirmed with the full condition evaluator, so results match the nested-loop path
     * regardless of key normalization. Handles all join types like {@link #executeConditionalJoin}.
     */
    private List<Row> executeHashJoin(final List<Row> leftRows) {
        final Map<List<Object>, List<Integer>> rightIndex = new HashMap<>();
        for (int r = 0; r < rightRows.size(); r++) {
            final List<Object> key = buildKey(rightRows.get(r), rightKeyIdx);
            List<Integer> bucket = rightIndex.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                rightIndex.put(key, bucket);
            }
            bucket.add(r);
        }

        final List<Row> resultRows = new ArrayList<>();
        final boolean[] matchedRight = new boolean[rightRows.size()];

        for (final Row leftRow : leftRows) {
            boolean leftMatchFound = false;
            final List<Integer> candidates = rightIndex.get(buildKey(leftRow, leftKeyIdx));
            if (candidates != null) {
                for (final int idx : candidates) {
                    final Row rightRow = rightRows.get(idx);
                    if (matchesBeyondKeys(leftRow, rightRow)) {
                        resultRows.add(combineRows(leftRow, rightRow));
                        leftMatchFound = true;
                        matchedRight[idx] = true;
                    }
                }
            }
            if (!leftMatchFound && (joinType == JoinType.LEFT || joinType == JoinType.FULL)) {
                resultRows.add(combineRowsWithNullRight(leftRow));
            }
        }

        if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
            for (int rightIdx = 0; rightIdx < rightRows.size(); rightIdx++) {
                if (!matchedRight[rightIdx]) {
                    resultRows.add(combineRowsWithNullLeft(rightRows.get(rightIdx)));
                }
            }
        }

        logger.debug("{} HASH JOIN: {} (left) x {} (right) -> {} rows",
            joinType, leftRows.size(), rightRows.size(), resultRows.size());
        return resultRows;
    }

    private List<Object> buildKey(final Row row, final int[] keyIdx) {
        final List<Object> key = new ArrayList<>(keyIdx.length);
        for (int i = 0; i < keyIdx.length; i++) {
            // Joining reads each key, so a cell a relation deferred raises its fault here.
            key.add(normalizeKey(DeferredFault.read(row.getValue(keyIdx[i]))));
        }
        return key;
    }

    /**
     * Normalize a key value so values the condition treats as equal share a bucket (case-insensitive
     * strings, cross-type numerics). False collisions are harmless — the full condition is re-checked.
     */
    private static Object normalizeKey(final Object value) {
        if (value == null) {
            return NULL_KEY;
        }
        if (value instanceof VariantValue) {
            // A semi-structured value buckets by its JSON text so it meets a text-carried equal
            // (legacy loaders and typed constructors must land in the same bucket).
            return ((VariantValue) value).text();
        }
        if (value instanceof String) {
            // A numeric-looking string buckets like the number it denotes: Snowflake implicitly
            // coerces VARCHAR vs NUMBER equality, so 999001 and '999001' must meet in one bucket.
            // Over-grouping is safe — every candidate pair is re-confirmed by the real condition
            // evaluator — while under-grouping silently loses matches.
            final Double numeric = parseAsDouble((String) value);
            if (numeric != null) {
                return numeric;
            }
            return ((String) value).toUpperCase();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    /** The string's numeric value, or null when it does not parse as a number. */
    private static Double parseAsDouble(final String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (final NumberFormatException notNumeric) {
            return null;
        }
    }

    /**
     * Evaluate join condition on a pair of rows.
     */
    /**
     * Whether a candidate pair that already AGREES ON THE KEYS also satisfies the join condition. With
     * no condition at all the keys are the whole of it — the shape a comma join takes once its WHERE
     * equality has been pushed down — and the pair matches. Without this, evaluateCondition's
     * no-evaluator answer of FALSE would make such a join return nothing.
     */
    private boolean matchesBeyondKeys(final Row leftRow, final Row rightRow) {
        return joinCondition == null && conditionEvaluator == null
            || evaluateCondition(leftRow, rightRow);
    }

    private boolean evaluateCondition(final Row leftRow, final Row rightRow) {
        if (conditionEvaluator == null) {
            logger.warn("No condition evaluator provided for conditional join");
            return false;
        }

        // A refusal is the statement's rather than this pair's: two collations that disagree in ON are refused
        // however the rows compare, and so is a value the condition cannot compute (live-verified).
        return conditionEvaluator.matches(leftRow, rightRow);
    }

    /**
     * Combine left and right rows.
     */
    private Row combineRows(final Row leftRow, final Row rightRow) {
        final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
        combinedValues.addAll(rightRow.getValues());
        return Row.of(combinedValues);
    }

    /**
     * Combine left row with NULLs for right columns (for LEFT JOIN unmatched rows).
     */
    private Row combineRowsWithNullRight(final Row leftRow) {
        final List<Object> combinedValues = new ArrayList<>(leftRow.getValues());
        final int rightWidth = rightTable.columnCount();
        for (int i = 0; i < rightWidth; i++) {
            combinedValues.add(null);
        }
        return Row.of(combinedValues);
    }

    /**
     * Combine right row with NULLs for left columns (for RIGHT JOIN unmatched rows).
     */
    private Row combineRowsWithNullLeft(final Row rightRow) {
        final List<Object> combinedValues = new ArrayList<>();
        final int leftWidth = leftTable.columnCount();
        for (int i = 0; i < leftWidth; i++) {
            combinedValues.add(null);
        }
        combinedValues.addAll(rightRow.getValues());
        return Row.of(combinedValues);
    }
}
