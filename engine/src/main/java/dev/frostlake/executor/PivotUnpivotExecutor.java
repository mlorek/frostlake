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
    ResultSet executePivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.TableSourceContext tableSource) {
        FrostlakeParser.PivotClauseContext pivotCtx = tableSource.pivotClause();

        // Extract PIVOT components
        String aggFuncName = pivotCtx.aggregateFunction().functionName().getText().toUpperCase();
        String aggColumn = pivotCtx.aggregateFunction().expression().getText();
        String pivotColumn = ParseTreeText.getIdentifier(pivotCtx.identifier());

        // Get pivot values
        List<String> pivotValues = new ArrayList<>();
        Map<String, String> pivotAliases = new HashMap<>();
        for (final FrostlakeParser.PivotValueContext pvCtx : pivotCtx.pivotValueList().pivotValue()) {
            String value = pvCtx.literal().getText().replace("'", "");
            pivotValues.add(value);

            String alias = value;
            if (pvCtx.identifier() != null) {
                alias = ParseTreeText.getIdentifier(pvCtx.identifier());
            }
            pivotAliases.put(value, alias);
        }

        // Get column indices
        int aggColIndex = table.getColumnIndex(aggColumn);
        int pivotColIndex = table.getColumnIndex(pivotColumn);

        // Get all non-pivot/non-agg columns for grouping
        List<Integer> groupByColIndices = new ArrayList<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (i != aggColIndex && i != pivotColIndex) {
                groupByColIndices.add(i);
            }
        }

        // Group rows by the non-pivot columns
        Map<List<Object>, Map<String, List<Object>>> groups = new LinkedHashMap<>();
        for (final Row row : rows) {
            // Build group key from non-pivot columns
            List<Object> groupKey = new ArrayList<>();
            for (final int idx : groupByColIndices) {
                groupKey.add(row.getValue(idx));
            }

            // Get pivot column value
            String pivotValue = row.getValue(pivotColIndex).toString();

            // Get aggregate column value
            Object aggValue = row.getValue(aggColIndex);

            groups.computeIfAbsent(groupKey, (final var k) -> new HashMap<>());
            groups.get(groupKey).computeIfAbsent(pivotValue, (final var k) -> new ArrayList<>()).add(aggValue);
        }

        // Build result columns
        List<ResultSetColumn> resultColumns = new ArrayList<>();
        for (final int idx : groupByColIndices) {
            TableColumn col = table.getColumns().get(idx);
            resultColumns.add(new ResultSetColumn(col.getName(), col.getDataType(), null));
        }
        for (final String pivotValue : pivotValues) {
            String colName = pivotAliases.get(pivotValue);
            resultColumns.add(new ResultSetColumn(colName, NumericType.BIGINT, null));
        }

        // Build result rows
        List<Row> resultRows = new ArrayList<>();
        for (final Map.Entry<List<Object>, Map<String, List<Object>>> entry : groups.entrySet()) {
            List<Object> rowValues = new ArrayList<>(entry.getKey());

            for (final String pivotValue : pivotValues) {
                List<Object> values = entry.getValue().getOrDefault(pivotValue, new ArrayList<>());
                Object aggResult = AggregateFunctions.applyAggregateFunction(aggFuncName, values);
                rowValues.add(aggResult);
            }

            resultRows.add(new Row(rowValues));
        }

        return new ResultSet(resultColumns, resultRows);
    }

    /**
     * Execute UNPIVOT operation to transform columns into rows
     */
    ResultSet executeUnpivot(final FrostlakeParser.SelectClauseContext ctx, final Table table, final List<Row> rows, final FrostlakeParser.TableSourceContext tableSource) {
        FrostlakeParser.UnpivotClauseContext unpivotCtx = tableSource.unpivotClause();

        // Extract UNPIVOT components
        String valueColumn = ParseTreeText.getIdentifier(unpivotCtx.identifier(0)); // value_column
        String nameColumn = ParseTreeText.getIdentifier(unpivotCtx.identifier(1));  // name_column

        // Get columns to unpivot
        List<String> unpivotColumns = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext idCtx : unpivotCtx.unpivotColumnList().identifier()) {
            unpivotColumns.add(ParseTreeText.getIdentifier(idCtx));
        }

        // Get column indices for unpivot columns
        List<Integer> unpivotColIndices = new ArrayList<>();
        for (final String colName : unpivotColumns) {
            unpivotColIndices.add(table.getColumnIndex(colName));
        }

        // Get indices of columns to preserve
        List<Integer> preserveColIndices = new ArrayList<>();
        for (int i = 0; i < table.getColumns().size(); i++) {
            if (!unpivotColIndices.contains(i)) {
                preserveColIndices.add(i);
            }
        }

        // Build result columns: preserved columns + name column + value column
        List<ResultSetColumn> resultColumns = new ArrayList<>();
        for (final int idx : preserveColIndices) {
            TableColumn col = table.getColumns().get(idx);
            resultColumns.add(new ResultSetColumn(col.getName(), col.getDataType(), null));
        }
        resultColumns.add(new ResultSetColumn(nameColumn, StringType.VARCHAR, null));

        // Use the data type of the first unpivot column for the value column
        DataType valueColumnType = table.getColumns().get(unpivotColIndices.get(0)).getDataType();
        resultColumns.add(new ResultSetColumn(valueColumn, valueColumnType, null));

        // Snowflake EXCLUDEs NULL values by default (a row is emitted only for a non-NULL unpivoted
        // value); the optional INCLUDE NULLS modifier keeps them, EXCLUDE NULLS is the explicit default.
        final boolean includeNulls = unpivotCtx.unpivotNulls() != null
            && unpivotCtx.unpivotNulls().INCLUDE() != null;

        // Build result rows
        List<Row> resultRows = new ArrayList<>();
        for (final Row row : rows) {
            // For each unpivot column, create a new row
            for (int i = 0; i < unpivotColumns.size(); i++) {
                final Object value = row.getValue(unpivotColIndices.get(i));
                if (value == null && !includeNulls) {
                    continue;
                }
                List<Object> rowValues = new ArrayList<>();

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
}
