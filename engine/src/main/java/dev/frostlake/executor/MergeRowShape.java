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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * The MERGE evaluation shape for one (source table, target table, aliases) combination: the merged
 * {@code __MERGE__} table exposing {@code alias.col} / {@code tablename.col} / bare-name slots, one
 * {@link ExpressionEvaluator} over it, and the slot mapping that refills a values list per
 * source/target row pair. Everything here is invariant per MERGE statement — it used to be rebuilt
 * for every candidate PAIR (a fresh column list, {@code Table} (with its name-index map), and
 * evaluator per pair, up to three cross-product passes deep).
 *
 * <p>Two modes mirror the two former builders exactly, slot for slot: the CONDITION shape (source
 * qualified-only, target qualified + bare) and the VALUE shape (source additionally exposed under a
 * {@code __S__} bare prefix, and optionally re-exposed bare LAST so a WHEN-NOT-MATCHED insert
 * resolves unqualified names to the SOURCE via the name index's last-wins rule).
 */
public final class MergeRowShape {

    private final Table mergedTable;
    private final ExpressionEvaluator evaluator;
    // Per merged slot: which side's row supplies the value (true = source) and at which index.
    private final boolean[] slotFromSource;
    private final int[] slotIndex;

    private MergeRowShape(final Table mergedTable, final ExpressionEvaluator evaluator,
                          final boolean[] slotFromSource, final int[] slotIndex) {
        this.mergedTable = mergedTable;
        this.evaluator = evaluator;
        this.slotFromSource = slotFromSource;
        this.slotIndex = slotIndex;
    }

    public static MergeRowShape build(final Table targetTable, final Table sourceTable,
                                      final String targetAlias, final String sourceAlias,
                                      final boolean valueMode, final boolean unqualifiedFromSource,
                                      final FunctionRegistry functionRegistry, final Catalog catalog,
                                      final QueryExecutor executor) {
        final List<TableColumn> mergedCols = new ArrayList<>();
        final List<Boolean> fromSource = new ArrayList<>();
        final List<Integer> indexes = new ArrayList<>();

        final String sAlias = sourceAlias != null ? sourceAlias.toUpperCase()
            : (sourceTable != null ? sourceTable.getName().toUpperCase() : "S");
        if (sourceTable != null) {
            final List<TableColumn> sourceCols = sourceTable.getColumns();
            final String srcTableName = sourceTable.getName().toUpperCase();
            for (int i = 0; i < sourceCols.size(); i++) {
                final TableColumn col = sourceCols.get(i);
                mergedCols.add(new TableColumn(sAlias + "." + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                fromSource.add(Boolean.TRUE);
                indexes.add(Integer.valueOf(i));
                if (!srcTableName.equals(sAlias)) {
                    mergedCols.add(new TableColumn(srcTableName + "." + col.getName().toUpperCase(),
                        col.getDataType(), true, null, false, false, false));
                    fromSource.add(Boolean.TRUE);
                    indexes.add(Integer.valueOf(i));
                }
                if (valueMode) {
                    // bare source column (lower priority than target bare)
                    mergedCols.add(new TableColumn("__S__" + col.getName().toUpperCase(),
                        col.getDataType(), true, null, false, false, false));
                    fromSource.add(Boolean.TRUE);
                    indexes.add(Integer.valueOf(i));
                }
            }
        }

        final String tAlias = targetAlias != null ? targetAlias.toUpperCase() : targetTable.getName().toUpperCase();
        final String tTableName = targetTable.getName().toUpperCase();
        final List<TableColumn> targetCols = targetTable.getColumns();
        for (int i = 0; i < targetCols.size(); i++) {
            final TableColumn col = targetCols.get(i);
            mergedCols.add(new TableColumn(tAlias + "." + col.getName().toUpperCase(),
                col.getDataType(), true, null, false, false, false));
            fromSource.add(Boolean.FALSE);
            indexes.add(Integer.valueOf(i));
            if (!tTableName.equals(tAlias)) {
                mergedCols.add(new TableColumn(tTableName + "." + col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                fromSource.add(Boolean.FALSE);
                indexes.add(Integer.valueOf(i));
            }
            // bare COL — for unqualified references (resolve to target)
            mergedCols.add(new TableColumn(col.getName().toUpperCase(),
                col.getDataType(), true, null, false, false, false));
            fromSource.add(Boolean.FALSE);
            indexes.add(Integer.valueOf(i));
        }

        if (valueMode && unqualifiedFromSource && sourceTable != null) {
            // WHEN NOT MATCHED insert: re-expose the source columns BARE and LAST, so the name
            // index's last-wins rule resolves unqualified names to the source for this shape.
            final List<TableColumn> sourceCols = sourceTable.getColumns();
            for (int i = 0; i < sourceCols.size(); i++) {
                final TableColumn col = sourceCols.get(i);
                mergedCols.add(new TableColumn(col.getName().toUpperCase(),
                    col.getDataType(), true, null, false, false, false));
                fromSource.add(Boolean.TRUE);
                indexes.add(Integer.valueOf(i));
            }
        }

        final boolean[] slotFromSource = new boolean[fromSource.size()];
        final int[] slotIndex = new int[indexes.size()];
        for (int i = 0; i < slotFromSource.length; i++) {
            slotFromSource[i] = fromSource.get(i).booleanValue();
            slotIndex[i] = indexes.get(i).intValue();
        }
        final Table mergedTable = new Table("__MERGE__", mergedCols, false);
        final ExpressionEvaluator evaluator =
            new ExpressionEvaluator(mergedTable, functionRegistry, catalog, executor);
        return new MergeRowShape(mergedTable, evaluator, slotFromSource, slotIndex);
    }

    /** Evaluate {@code expr} over the merged row for this pair (refills only the values list). */
    public Object evaluate(final String expr, final Row targetRow, final Row sourceRow) {
        final List<Object> mergedVals = new ArrayList<>(slotIndex.length);
        final int sourceWidth = sourceRow != null ? sourceRow.getValues().size() : 0;
        final int targetWidth = targetRow != null ? targetRow.getValues().size() : 0;
        for (int i = 0; i < slotIndex.length; i++) {
            if (slotFromSource[i]) {
                mergedVals.add(slotIndex[i] < sourceWidth ? sourceRow.getValue(slotIndex[i]) : null);
            } else {
                mergedVals.add(slotIndex[i] < targetWidth ? targetRow.getValue(slotIndex[i]) : null);
            }
        }
        return evaluator.evaluate(expr, new Row(mergedVals));
    }

    public Table mergedTable() {
        return mergedTable;
    }
}
