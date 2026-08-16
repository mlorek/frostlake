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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * PIVOT / UNPIVOT query stage extracted from {@link QueryExecutor}. Both operations are pure
 * transforms of their input rows and table shape: identifier text comes from the stateless
 * {@link ParseTreeText} helper and the per-cell reduction from {@link AggregateFunctions}, so this
 * stage needs no engine state. The owning executor is held for symmetry with the other extracted
 * stages and to keep the forwarder wiring uniform.
 */
final class PivotUnpivotExecutor {

    private final QueryExecutor executor;

    PivotUnpivotExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Execute PIVOT operation to transform rows into columns
     */
    ResultSet executePivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.PivotClauseContext pivotCtx) {

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
            for (final Row row : rows) {
                final Object value = row.getValue(pivotColIndex);
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
                final Object value = row.getValue(0);
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

        // Group rows by the non-pivot columns. Buckets use canonicalized keys (so equal numbers with
        // different runtime types group together); output rows keep the first row's raw values.
        final Map<List<Object>, Map<String, List<Object>>> groups = new LinkedHashMap<>();
        final Map<List<Object>, List<Object>> groupKeyDisplayValues = new HashMap<>();
        for (final Row row : rows) {
            // Build group key from non-pivot columns
            final List<Object> rawKey = new ArrayList<>();
            final List<Object> groupKey = new ArrayList<>();
            for (final int idx : groupByColIndices) {
                final Object value = row.getValue(idx);
                rawKey.add(value);
                groupKey.add(ValueComparisons.canonicalGroupKeyValue(value));
            }

            // Get pivot column value
            final String pivotValue = row.getValue(pivotColIndex).toString();

            // Get aggregate column value
            final Object aggValue = row.getValue(aggColIndex);

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

        // Build result columns
        final List<ResultSetColumn> resultColumns = new ArrayList<>();
        for (final int idx : groupByColIndices) {
            final TableColumn col = table.getColumns().get(idx);
            resultColumns.add(new ResultSetColumn(col.getName(), col.getDataType(), null));
        }
        for (final String pivotValue : pivotValues) {
            final String colName = pivotAliases.get(pivotValue);
            resultColumns.add(new ResultSetColumn(colName, NumericType.BIGINT, null));
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

        return new ResultSet(resultColumns, resultRows);
    }

    /**
     * Execute UNPIVOT operation to transform columns into rows
     */
    ResultSet executeUnpivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.UnpivotClauseContext unpivotCtx) {

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

        // Build result rows
        final List<Row> resultRows = new ArrayList<>();
        for (final Row row : rows) {
            // For each unpivot column, create a new row
            for (int i = 0; i < unpivotColumns.size(); i++) {
                final Object value = row.getValue(unpivotColIndices.get(i));
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

        return new ResultSet(resultColumns, resultRows);
    }

    /** Evaluate a constant expression (the PIVOT DEFAULT ON NULL value) with no row context. */
    private Object evaluateConstant(final FrostlakeParser.ExpressionContext expr) {
        final Table dummyTable = new Table("DUMMY", new ArrayList<>(), false);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(
            dummyTable, executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return evaluator.evaluate(ParseTreeText.getOriginalText(expr), new Row(new ArrayList<>()));
    }

}
