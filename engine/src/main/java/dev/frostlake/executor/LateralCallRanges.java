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
import dev.frostlake.storage.Row;
import dev.frostlake.values.ValueRange;

import java.util.List;

/**
 * The interval of each column a table function computes, united over the calls it makes for the rows to its left. A
 * function called once per row of another relation is tagged by all of its calls, as the account tags it by the
 * argument column's statistics: {@code TABLE(t_div(ti.i))} over {@code ti} holding 3 and 300 is [SB2] where each call
 * alone would be [SB1] or its declared width (live-verified). The shape the join is planned with keeps the interval
 * its NULL-bound reading found until a run unites the calls, and gets it back for a run that makes none.
 */
final class LateralCallRanges {

    private final Table shape;
    private final ValueRange[] planned;
    private final ValueRange[] united;
    private final boolean[] unknown;
    private boolean called;

    /**
     * @param shape the lateral item's shape, whose columns are tagged by the union
     */
    LateralCallRanges(final Table shape) {
        this.shape = shape;
        final int width = shape.getColumns().size();
        this.planned = new ValueRange[width];
        for (int i = 0; i < width; i++) {
            planned[i] = shape.getColumns().get(i).getValueRange();
        }
        this.united = new ValueRange[width];
        this.unknown = new boolean[width];
    }

    /** Starts a run: no call is added yet. */
    void begin() {
        called = false;
        for (int i = 0; i < united.length; i++) {
            united[i] = null;
            unknown[i] = false;
        }
    }

    /**
     * Adds the columns of one call. A column whose every row is NULL adds nothing but its NULL, whatever interval the
     * call's own typing gave it: a NULL argument keeps a column of 3 and 300 [SB2] (live-verified).
     *
     * @param call the relation the call answered, or null when it answered none
     * @param rows the rows the call answered
     */
    void add(final Table call, final List<Row> rows) {
        if (call == null || call.getColumns().size() != united.length) {
            for (int i = 0; i < unknown.length; i++) {
                unknown[i] = true;
            }
            return;
        }
        called = true;
        for (int i = 0; i < united.length; i++) {
            final ValueRange range = onlyNulls(rows, i) ? ValueRange.EMPTY.withNullable(true)
                : call.getColumns().get(i).getValueRange();
            if (range == null) {
                unknown[i] = true;
            } else {
                united[i] = united[i] == null ? range : united[i].union(range);
            }
        }
    }

    private static boolean onlyNulls(final List<Row> rows, final int column) {
        for (final Row row : rows) {
            if (column >= row.getValues().size() || row.getValue(column) != null) {
                return false;
            }
        }
        return true;
    }

    /** Tags the shape's columns by the union of the calls added, or by the planned interval where none is known. */
    void apply() {
        for (int i = 0; i < united.length; i++) {
            shape.getColumns().get(i).setValueRange(called && !unknown[i] && united[i] != null ? united[i]
                : planned[i]);
        }
    }
}
