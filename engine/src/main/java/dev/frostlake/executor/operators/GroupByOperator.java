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
import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.executor.expressions.CollatedKey;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    // When set, receives each output row's source group rows (parallel to the returned list). Lets a caller
    // (HAVING) evaluate aggregates that are not SELECT items over the original group.
    private List<List<Row>> groupRowsSink;
    // Lateral column aliases: names is 1:1 with selectExpressions (null where an item has no alias) and sink
    // is the map SHARED with the aggregate evaluator's lateral context. See captureLateralAliases.
    private List<String> lateralAliasNames;
    private Map<String, Object> lateralAliasSink;
    // The collation each group key compares under, parallel to groupByExpressions (null entries where a
    // key has none). See collateKeys.
    private List<CollationSpec> keyCollations;

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

    /**
     * The collation each group key compares under. Keys that are equal under their collation fall into
     * ONE group, and the group reports the smallest of its values by raw text — two rules a binary
     * grouping cannot express.
     *
     * @param collations one entry per group-by expression, null where the key carries no collation
     */
    public void collateKeys(final List<CollationSpec> collations) {
        this.keyCollations = collations;
    }

    /**
     * Fold groups smaller than {@code minGroupSize} into ONE remainder group, the way an AGGREGATION
     * POLICY does: the remainder's group keys read NULL, its aggregates are computed over the folded
     * rows when the remainder itself reaches the floor, and read NULL when it does not (live-verified).
     *
     * @param minGroupSize the policy's floor, or 0 to fold nothing
     * @param entityKey    the columns whose DISTINCT values count as one entity, or empty to count rows
     * @param columnNames  the source table's column names, index-aligned with each row's values
     */
    public void foldSmallGroups(final int minGroupSize, final List<String> entityKey,
                                final List<String> columnNames) {
        this.minGroupSize = minGroupSize;
        this.entityKey = new ArrayList<>(entityKey);
        this.columnNames = new ArrayList<>(columnNames);
    }

    private int minGroupSize;
    private List<String> entityKey = new ArrayList<>();
    private List<String> columnNames = new ArrayList<>();

    /** How many entities a group holds: distinct entity-key values, or rows when there is no key. */
    private int groupSize(final List<Row> groupRows) {
        if (entityKey.isEmpty()) {
            return groupRows.size();
        }
        final Set<List<Object>> entities = new LinkedHashSet<>();
        for (final Row row : groupRows) {
            final List<Object> entity = new ArrayList<>();
            for (final String column : entityKey) {
                final int index = columnNames.indexOf(column.toUpperCase(Locale.ROOT));
                entity.add(index >= 0 && index < row.getValues().size() ? row.getValue(index) : null);
            }
            entities.add(entity);
        }
        return entities.size();
    }

    /** Whether a select item IS one of the group keys, and so reads NULL in the remainder row. */
    private boolean isGroupKey(final int selectIndex) {
        final String item = selectExpressions.get(selectIndex);
        for (final String key : groupByExpressions) {
            if (key.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    /** Capture each output row's source group rows into {@code sink} (parallel to the returned list), so a
     *  caller can apply HAVING aggregates that are not SELECT items over the original group. */
    public void captureGroupRows(final List<List<Row>> sink) {
        this.groupRowsSink = sink;
    }

    /**
     * Expose Snowflake lateral column aliases to later SELECT items of the same list, the grouped counterpart
     * of {@link ProjectOperator}'s alias sink: as each item of a group is evaluated left to right, its
     * {@code aliasNames} entry (when non-null) is written to {@code sink} with the value just computed, so a
     * later item can reference it (e.g. {@code SELECT x AS n, LOWER(n) AS lo, COUNT(1) FROM t GROUP BY x}).
     * {@code sink} must be the same map the aggregate evaluator reads as its lateral context. It is cleared at
     * the start of every group, so aliases never leak between groups, and only earlier items are ever visible —
     * a forward reference stays unresolved, exactly as in the ungrouped projection path.
     *
     * @param aliasNames one entry per select expression, the item's alias or null when it has none
     * @param sink the shared alias-value map, written as each group's items are evaluated
     */
    public void captureLateralAliases(final List<String> aliasNames, final Map<String, Object> sink) {
        this.lateralAliasNames = aliasNames;
        this.lateralAliasSink = sink;
    }

    /**
     * Aliases published as NULL at the start of every group — a super-group's dimensions that this
     * grouping set aggregates away. Their items print NULL on the subtotal row, so an item derived
     * from them ({@code LOWER(c)}, {@code COALESCE(c, 'x')}) must read NULL too, not the
     * representative row's value: publishing the NULL is what makes {@code COALESCE(c, 'x')} answer
     * 'x' there. Leaving the name unresolved instead made the derived item FAIL, and the failure was
     * silently turned into NULL.
     */
    private List<String> nullAliasNames = new ArrayList<>();

    public void presetNullAliases(final List<String> aliasNames) {
        this.nullAliasNames = aliasNames;
    }

    /** Reset the shared lateral-alias map at the start of a group so no values leak in from the previous one. */
    private void beginGroupAliases() {
        if (lateralAliasSink != null) {
            lateralAliasSink.clear();
            for (final String alias : nullAliasNames) {
                lateralAliasSink.put(alias.toUpperCase(), null);
            }
        }
    }

    /** Publish select item {@code index}'s value under its alias, for later items of the same group to read. */
    private void publishGroupAlias(final int index, final Object value) {
        if (lateralAliasSink == null || lateralAliasNames == null || index >= lateralAliasNames.size()) {
            return;
        }
        final String alias = lateralAliasNames.get(index);
        if (alias != null) {
            lateralAliasSink.put(alias.toUpperCase(), value);
        }
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (implicitGrouping) {
            return executeImplicitGrouping(input);
        }
        rejectFileGroupKeys(context);
        return executeExplicitGrouping(input);
    }

    /**
     * Snowflake rejects a FILE-typed GROUP BY key at compile time ("Expressions of type FILE cannot be
     * used as GROUP BY keys"), while still grouping an OBJECT or VARIANT quite happily. The keys
     * reaching this operator have already had positional ordinals and SELECT aliases resolved to their
     * defining expression, so checking them here covers {@code GROUP BY f}, {@code GROUP BY 1} and
     * {@code GROUP BY <alias>} with one rule. Runs before any row is grouped, so an empty input rejects
     * exactly like a populated one.
     */
    private void rejectFileGroupKeys(final OperatorContext context) {
        if (context == null || context.getQueryExecutor() == null || groupByExpressions.isEmpty()) {
            return;
        }
        final ExpressionEvaluator keyChecker = new ExpressionEvaluator(context.getTable(),
            context.getFunctionRegistry(), context.getQueryExecutor().getCatalog(),
            context.getQueryExecutor());
        if (context.getAllTables() != null) {
            keyChecker.setMultiTableContext(context.getAliasToTable(), context.getAllTables());
        }
        for (final String key : groupByExpressions) {
            keyChecker.validateKey(ExpressionEvaluator.parse(key), SortKeyRole.GROUP_BY);
        }
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

        final List<Object> resultValues = new ArrayList<>();

        beginGroupAliases();
        for (int i = 0; i < selectExpressions.size(); i++) {
            final Object value = evaluateAggregate(i, input);
            resultValues.add(value);
            publishGroupAlias(i, value);
        }

        logger.debug("Implicit grouping: {} rows -> 1 row with {} values",
            input.size(), resultValues.size());

        if (groupRowsSink != null) {
            groupRowsSink.add(input);
        }
        return List.of(new Row(resultValues));
    }

    /**
     * Execute explicit grouping by specified columns.
     */
    private List<Row> executeExplicitGrouping(final List<Row> input) {
        logger.debug("Executing GROUP BY on {} expressions across {} rows",
            groupByExpressions.size(), input.size());

        // Group rows by the specified expressions. Parse the group-by expressions once.
        final List<Expression> parsedGroupBy = new ArrayList<>(groupByExpressions.size());
        for (final String e : groupByExpressions) {
            parsedGroupBy.add(ExpressionEvaluator.parse(e));
        }
        final Map<List<Object>, List<Row>> groups = new LinkedHashMap<>();

        for (final Row row : input) {
            final List<Object> groupKey = buildGroupKey(row, parsedGroupBy);
            List<Row> bucket = groups.get(groupKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                groups.put(groupKey, bucket);
            }
            bucket.add(row);
        }

        logger.debug("Grouped {} rows into {} groups", input.size(), groups.size());

        // An aggregation policy folds every group below its floor into one remainder group.
        final List<Row> remainderRows = new ArrayList<>();
        if (minGroupSize > 0) {
            final Iterator<Map.Entry<List<Object>, List<Row>>> small = groups.entrySet().iterator();
            while (small.hasNext()) {
                final Map.Entry<List<Object>, List<Row>> entry = small.next();
                if (groupSize(entry.getValue()) < minGroupSize) {
                    remainderRows.addAll(entry.getValue());
                    small.remove();
                }
            }
        }

        // Build result rows for each group
        final List<Row> result = new ArrayList<>();

        for (final Map.Entry<List<Object>, List<Row>> entry : groups.entrySet()) {
            final List<Row> groupRows = entry.getValue();
            final List<Object> resultValues = new ArrayList<>();

            beginGroupAliases();
            for (int i = 0; i < selectExpressions.size(); i++) {
                Object value = evaluateAggregate(i, groupRows);
                // A collated key's group holds values that differ in text, so the one the group REPORTS
                // is settled by the collation's own rule rather than by whichever row came first.
                final int keyIndex = collatedKeyIndexOf(i);
                if (keyIndex >= 0) {
                    value = reportedKeyValue(groupRows, parsedGroupBy.get(keyIndex));
                }
                resultValues.add(value);
                publishGroupAlias(i, value);
            }

            result.add(new Row(resultValues));
            if (groupRowsSink != null) {
                groupRowsSink.add(groupRows);
            }
        }

        if (!remainderRows.isEmpty()) {
            final boolean remainderCounts = groupSize(remainderRows) >= minGroupSize;
            final List<Object> remainderValues = new ArrayList<>();
            beginGroupAliases();
            for (int i = 0; i < selectExpressions.size(); i++) {
                final Object value = remainderCounts && !isGroupKey(i)
                    ? evaluateAggregate(i, remainderRows) : null;
                remainderValues.add(value);
                publishGroupAlias(i, value);
            }
            result.add(new Row(remainderValues));
            if (groupRowsSink != null) {
                groupRowsSink.add(remainderRows);
            }
        }

        logger.debug("GROUP BY: {} rows -> {} groups with {} columns each",
            input.size(), result.size(), selectExpressions.size());

        return result;
    }


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

        final List<Object> key = new ArrayList<>(parsedGroupBy.size());

        for (int i = 0; i < parsedGroupBy.size(); i++) {
            // A key that cannot be evaluated fails the query, as it does on the account — it used to
            // collect every such row under one error key and answer.
            key.add(CollatedKey.of(
                ValueComparisons.canonicalGroupKeyValue(columnEvaluator.evaluate(parsedGroupBy.get(i), row)),
                keyCollation(i)));
        }

        return key;
    }

    /** The collation the group key at {@code index} compares under, or null when it carries none. */
    private CollationSpec keyCollation(final int index) {
        if (keyCollations == null || index >= keyCollations.size()) {
            return null;
        }
        return keyCollations.get(index);
    }

    /**
     * The group-by index a select item repeats, when that key carries a collation — otherwise -1. Only
     * a collated key needs its reported value settled; every other key is one value per group already.
     */
    private int collatedKeyIndexOf(final int selectIndex) {
        final String item = selectExpressions.get(selectIndex);
        for (int i = 0; i < groupByExpressions.size(); i++) {
            if (groupByExpressions.get(i).equalsIgnoreCase(item) && keyCollation(i) != null) {
                return i;
            }
        }
        return -1;
    }

    /** The value a collated group reports: the smallest of its key's values by raw text. */
    private Object reportedKeyValue(final List<Row> groupRows, final Expression key) {
        Object reported = null;
        for (final Row row : groupRows) {
            reported = CollatedKey.leastOf(reported, columnEvaluator.evaluate(key, row));
        }
        return reported;
    }

    /**
     * Evaluate an aggregate expression on a group of rows.
     */
    private Object evaluateAggregate(final int index, final List<Row> groupRows) {
        if (aggregateEvaluator == null) {
            throw new IllegalStateException("No aggregate evaluator provided");
        }

        // Whatever the evaluation throws is the answer: a refusal the user has to see, or a bug that
        // has to be seen. This used to catch everything and return NULL, and every failure of an
        // aggregate — a conversion live refuses, a class cast, an unresolved name — became an empty
        // cell in a query that answered. The one shape that legitimately yields NULL here, a
        // super-group subtotal's item over a dimension that row aggregates away, is now a NULL that
        // is PUBLISHED for it (see presetNullAliases) rather than a failure that was swallowed.
        return aggregateEvaluator.evaluate(index, groupRows);
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
