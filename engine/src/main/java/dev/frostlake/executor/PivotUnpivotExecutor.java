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

import dev.frostlake.executor.expressions.AggregateResultTypes;
import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.StageOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * PIVOT / UNPIVOT query stages extracted from {@link QueryExecutor}. Both are planned with their shape and
 * run as pipeline stages that transform their input rows: identifier text comes from the stateless
 * {@link ParseTreeText} helper and the per-cell reduction from {@link AggregateFunctions}. The owning
 * executor answers a subquery-driven IN list and builds the relation shape.
 */
final class PivotUnpivotExecutor {

    private final QueryExecutor executor;

    PivotUnpivotExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * The PIVOT stage and the relation it answers. Its columns are known while planning: the grouping
     * columns, then one per pivot value — the written IN list, a subquery's first column evaluated now,
     * or for {@code IN (ANY)} the distinct FOR-column values read off {@code rowsForAny}.
     *
     * @param table          the relation pivoted
     * @param pivotCtx       the clause
     * @param rowsForAny     the rows the FOR column's distinct values are read from, needed for ANY only
     * @param renamedColumns the alias list of {@code PIVOT(…) AS p (c1, c2)}, renaming the columns
     *                       positionally, or null
     * @param relationName   the name the pivoted relation takes
     * @return the stage and its shape
     */
    PlannedRelation planPivot(final Table table, final FrostlakeParser.PivotClauseContext pivotCtx,
                              final List<Row> rowsForAny, final List<String> renamedColumns,
                              final String relationName) {
        // Extract PIVOT components
        final String aggFuncName = pivotCtx.aggregateFunction().functionName().getText().toUpperCase();
        final String aggColumn = pivotCtx.aggregateFunction().expression().getText();
        final String pivotColumn = ParseTreeText.getIdentifier(pivotCtx.identifier());
        // Get column indices
        final int aggColIndex = table.getColumnIndex(aggColumn);
        final int pivotColIndex = table.getColumnIndex(pivotColumn);
        // Get pivot values: an explicit list, ANY (dynamic: the distinct FOR-column values), or a subquery.
        final List<String> pivotValues = new ArrayList<>();
        final Map<String, String> pivotAliases = new HashMap<>();
        final FrostlakeParser.PivotInListContext inList = pivotCtx.pivotInList();
        if (inList.pivotValueList() != null) {
            for (final FrostlakeParser.PivotValueContext pvCtx : inList.pivotValueList().pivotValue()) {
                // The value used to MATCH rows is the literal's value (q1), but the output column is NAMED after
                // the literal AS WRITTEN — Snowflake keeps the quotes inside the identifier, so a string pivot
                // value 'q1' produces a column called 'q1' that is referenced as "'q1'" (which is exactly how
                // real queries write it, e.g. MAX("'q1'")). Naming it `q1` instead made "'q1'" unresolvable and,
                // worse, made MAX("'q1'") silently return the STRING q1. An explicit alias still wins.
                final String value = pvCtx.literal().getText().replace("'", "");
                pivotValues.add(value);
                String alias = pvCtx.literal().getText();
                if (pvCtx.identifier() != null) {
                    alias = ParseTreeText.getIdentifier(pvCtx.identifier());
                }
                pivotAliases.put(value, alias);
            }
        } else if (inList.ANY() != null) {
            // Dynamic pivot: the distinct values of the FOR column, ascending for a deterministic
            // column order (an ORDER BY inside ANY parses; the natural value order is used).
            final TreeSet<String> distinct = new TreeSet<>();
            for (final Row row : rowsForAny) {
                final Object value = DeferredFault.read(row.getValue(pivotColIndex));
                if (value != null) {
                    distinct.add(value.toString());
                }
            }
            for (final String value : distinct) {
                pivotValues.add(value);
                pivotAliases.put(value, "'" + value + "'");
            }
        } else if (inList.selectStatement() != null) {
            // Subquery-driven pivot columns: the first column of the subquery result, in result order.
            final ResultSet sub = executor.executeSelectFromContext(inList.selectStatement());
            for (final Row row : sub.getRows()) {
                final Object value = DeferredFault.read(row.getValue(0));
                if (value != null && !pivotValues.contains(value.toString())) {
                    pivotValues.add(value.toString());
                    pivotAliases.put(value.toString(), "'" + value + "'");
                }
            }
        }
        // DEFAULT ON NULL (expr): the constant substituted for empty pivot cells.
        final Object defaultOnNull = pivotCtx.DEFAULT() != null
            ? evaluateConstant(pivotCtx.expression()) : null;
        // Get all non-pivot/non-agg columns for grouping
        final List<Integer> groupByColIndices = new ArrayList<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (i != aggColIndex && i != pivotColIndex) {
                groupByColIndices.add(i);
            }
        }
        // Build result columns
        final List<ResultSetColumn> resultColumns = new ArrayList<>();
        for (final int idx : groupByColIndices) {
            final TableColumn col = table.getColumns().get(idx);
            resultColumns.add(new ResultSetColumn(col.getName(), col.getDataType(), null));
        }
        final DataType aggregated = aggColIndex >= 0 ? table.getColumns().get(aggColIndex).getDataType() : null;
        final DataType pivotedType = pivotColumnType(aggFuncName, aggregated);
        for (final String pivotValue : pivotValues) {
            final String colName = pivotAliases.get(pivotValue);
            resultColumns.add(new ResultSetColumn(colName, pivotedType, null));
        }
        final Table shape = executor.resultSetToTable(
            new ResultSet(renamed(resultColumns, renamedColumns), new ArrayList<Row>()), relationName);
        final Operator stage = new StageOperator("PIVOT[" + aggFuncName + "(" + aggColumn + ") FOR " + pivotColumn
                + (inList.ANY() != null ? " IN (ANY)" : "") + "]") {
            @Override
            protected List<Row> apply(final List<Row> rows) {
                // Group rows by the non-pivot columns. Buckets use canonicalized keys (so equal numbers with
                // different runtime types group together); output rows keep the first row's raw values.
                final Map<List<Object>, Map<String, List<Object>>> groups = new LinkedHashMap<>();
                final Map<List<Object>, List<Object>> groupKeyDisplayValues = new HashMap<>();
                for (final Row row : rows) {
                    // Build group key from non-pivot columns
                    final List<Object> rawKey = new ArrayList<>();
                    final List<Object> groupKey = new ArrayList<>();
                    for (final int idx : groupByColIndices) {
                        // Pivoting reads its grouping, pivot and aggregated cells, so a deferred fault raises here.
                        final Object value = DeferredFault.read(row.getValue(idx));
                        rawKey.add(value);
                        groupKey.add(ValueComparisons.canonicalGroupKeyValue(value));
                    }
                    // Get pivot column value
                    final String pivotValue = DeferredFault.read(row.getValue(pivotColIndex)).toString();
                    // Get aggregate column value
                    final Object aggValue = DeferredFault.read(row.getValue(aggColIndex));
                    Map<String, List<Object>> groupBuckets = groups.get(groupKey);
                    if (groupBuckets == null) {
                        groupBuckets = new HashMap<>();
                        groups.put(groupKey, groupBuckets);
                        groupKeyDisplayValues.put(groupKey, rawKey);
                    }
                    List<Object> pivotBucket = groupBuckets.get(pivotValue);
                    if (pivotBucket == null) {
                        pivotBucket = new ArrayList<>();
                        groupBuckets.put(pivotValue, pivotBucket);
                    }
                    pivotBucket.add(aggValue);
                }
                // Build result rows
                final List<Row> resultRows = new ArrayList<>();
                for (final Map.Entry<List<Object>, Map<String, List<Object>>> entry : groups.entrySet()) {
                    final List<Object> rowValues = new ArrayList<>(groupKeyDisplayValues.get(entry.getKey()));
                    for (final String pivotValue : pivotValues) {
                        final List<Object> values = entry.getValue().getOrDefault(pivotValue, new ArrayList<>());
                        Object aggResult = AggregateFunctions.applyAggregateFunction(aggFuncName, values);
                        if (aggResult == null && defaultOnNull != null) {
                            aggResult = defaultOnNull;
                        }
                        rowValues.add(aggResult);
                    }
                    resultRows.add(new Row(rowValues));
                }
                return resultRows;
            }
        };
        return new PlannedRelation(stage, shape, null);
    }

    /**
     * The UNPIVOT stage and the relation it answers: the preserved columns, then the name column and the
     * value column, typed like the first unpivoted column.
     *
     * @param table          the relation unpivoted
     * @param unpivotCtx     the clause
     * @param renamedColumns the alias list of {@code UNPIVOT(…) AS u (c1, c2)}, renaming the columns
     *                       positionally, or null
     * @param relationName   the name the unpivoted relation takes
     * @return the stage and its shape
     */
    PlannedRelation planUnpivot(final Table table, final FrostlakeParser.UnpivotClauseContext unpivotCtx,
                                final List<String> renamedColumns, final String relationName) {
        // Extract UNPIVOT components
        final String valueColumn = ParseTreeText.getIdentifier(unpivotCtx.identifier(0)); // value_column
        final String nameColumn = ParseTreeText.getIdentifier(unpivotCtx.identifier(1));  // name_column
        // Get columns to unpivot
        final List<String> unpivotColumns = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext idCtx : unpivotCtx.unpivotColumnList().identifier()) {
            unpivotColumns.add(ParseTreeText.getIdentifier(idCtx));
        }
        // Get column indices for unpivot columns
        final List<Integer> unpivotColIndices = new ArrayList<>();
        for (final String colName : unpivotColumns) {
            unpivotColIndices.add(table.getColumnIndex(colName));
        }
        // Get indices of columns to preserve
        final List<Integer> preserveColIndices = new ArrayList<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (!unpivotColIndices.contains(i)) {
                preserveColIndices.add(i);
            }
        }
        // Build result columns: preserved columns + name column + value column
        final List<ResultSetColumn> resultColumns = new ArrayList<>();
        for (final int idx : preserveColIndices) {
            final TableColumn col = table.getColumns().get(idx);
            resultColumns.add(new ResultSetColumn(col.getName(), col.getDataType(), null));
        }
        resultColumns.add(new ResultSetColumn(nameColumn, StringType.VARCHAR, null));
        // Use the data type of the first unpivot column for the value column
        final DataType valueColumnType = table.getColumns().get(unpivotColIndices.get(0)).getDataType();
        resultColumns.add(new ResultSetColumn(valueColumn, valueColumnType, null));
        // Snowflake EXCLUDEs NULL values by default (a row is emitted only for a non-NULL unpivoted
        // value); the optional INCLUDE NULLS modifier keeps them, EXCLUDE NULLS is the explicit default.
        final boolean includeNulls = unpivotCtx.unpivotNulls() != null
            && unpivotCtx.unpivotNulls().INCLUDE() != null;
        final Table shape = executor.resultSetToTable(
            new ResultSet(renamed(resultColumns, renamedColumns), new ArrayList<Row>()), relationName);
        final Operator stage = new StageOperator("UNPIVOT[" + valueColumn + " FOR " + nameColumn + "]") {
            @Override
            protected List<Row> apply(final List<Row> rows) {
                // Build result rows
                final List<Row> resultRows = new ArrayList<>();
                for (final Row row : rows) {
                    // For each unpivot column, create a new row
                    for (int i = 0; i < unpivotColumns.size(); i++) {
                        final Object value = DeferredFault.read(row.getValue(unpivotColIndices.get(i)));
                        if (value == null && !includeNulls) {
                            continue;
                        }
                        final List<Object> rowValues = new ArrayList<>();
                        // Add preserved column values
                        for (final int idx : preserveColIndices) {
                            rowValues.add(row.getValue(idx));
                        }
                        // Add column name
                        rowValues.add(unpivotColumns.get(i));
                        // Add column value
                        rowValues.add(value);
                        resultRows.add(new Row(rowValues));
                    }
                }
                return resultRows;
            }
        };
        return new PlannedRelation(stage, shape, null);
    }

    /**
     * A pivot alias's derived-column alias list — {@code PIVOT(…) AS p (empid, q1, q2)} — renaming the
     * pivoted result columns positionally. A pivot's column names are otherwise derived from the pivoted
     * values, so this list is the only way to name them, and an outer query that references those names (the
     * reason the list is written at all) cannot work without it.
     */
    /**
     * What a pivoted column declares: its aggregate's type over the aggregated column, as the same aggregate
     * declares in a grouped SELECT. COUNT declares the counter's width, MIN and MAX the column's own type, and
     * SUM and AVG widen it by the type inferencer's rules. Declaring every pivoted column BIGINT told a driver
     * that reads the declared scale to round an average of 92.5 to 92.
     *
     * @param aggFuncName the aggregate's upper-case name
     * @param aggregated  the aggregated column's type, or null when it is not a column of the source
     * @return the pivoted columns' type
     */
    private static DataType pivotColumnType(final String aggFuncName, final DataType aggregated) {
        if (aggFuncName.equals("COUNT")) {
            return IntegerResultWidths.COUNTER;
        }
        if (aggregated == null) {
            return NumericType.BIGINT;
        }
        if (aggFuncName.equals("MIN") || aggFuncName.equals("MAX")) {
            return aggregated;
        }
        final DataType computed = AggregateResultTypes.computing(aggFuncName, aggregated);
        return computed != null ? computed : NumericType.BIGINT;
    }

    private static List<ResultSetColumn> renamed(final List<ResultSetColumn> columns, final List<String> names) {
        if (names == null) {
            return columns;
        }
        final List<ResultSetColumn> renamedColumns = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            final ResultSetColumn column = columns.get(i);
            renamedColumns.add(i < names.size() ? new ResultSetColumn(names.get(i), column.getDataType()) : column);
        }
        return renamedColumns;
    }

    /** Evaluate a constant expression (the PIVOT DEFAULT ON NULL value) with no row context. */
    private Object evaluateConstant(final FrostlakeParser.ExpressionContext expr) {
        final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(
            dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return evaluator.evaluate(ParseTreeText.getOriginalText(expr), new Row(new ArrayList<>()));
    }

}
