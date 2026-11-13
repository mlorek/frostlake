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

package dev.frostlake.executor.expressions;

import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows a correlated EXISTS's subquery keeps, grouped by the keys of its correlating equalities and computed once
 * over the inner relation, each holding the columns its residual reads (see
 * {@link dev.frostlake.executor.EqualityCorrelation#getResidual}): an outer row tests the residual over the rows of
 * its own keys instead of running the subquery again. Like {@link PreparedKeySet}, it answers only outer keys whose
 * values are each of the one kind its column's keys hold (see {@link CorrelationKeys}). A row's cells are kept as
 * computed, so a fault one holds is raised only when an outer row of its keys reads it.
 */
public final class PreparedKeyedRows {

    private final Map<List<String>, List<Row>> rows = new HashMap<>();
    private final String[] kinds;
    private final List<ResultSetColumn> itemColumns;

    private PreparedKeyedRows(final int width, final List<ResultSetColumn> itemColumns) {
        kinds = new String[width];
        this.itemColumns = itemColumns;
    }

    /**
     * @param computed one row for each row the subquery keeps, the keys first and the residual's columns after them;
     *                 a row with a NULL key, which no outer key equals, is left out
     * @param width    how many keys a row holds
     * @param columns  the computed rows' columns, the keys' first
     * @return the rows by key, each holding the residual's columns alone
     */
    public static PreparedKeyedRows build(final List<Row> computed, final int width,
                                          final List<ResultSetColumn> columns) {
        final PreparedKeyedRows keyed = new PreparedKeyedRows(width,
            new ArrayList<>(columns.subList(width, columns.size())));
        for (final Row row : computed) {
            final List<Object> key = PreparedKeySet.keyOf(row, width);
            if (key != null && KeyColumnKinds.note(keyed.kinds, key)) {
                final List<String> form = CorrelationKeys.canonical(key);
                List<Row> held = keyed.rows.get(form);
                if (held == null) {
                    held = new ArrayList<>();
                    keyed.rows.put(form, held);
                }
                held.add(new Row(new ArrayList<>(row.getValues().subList(width, row.getValues().size()))));
            }
        }
        return keyed;
    }

    /**
     * Whether the rows settle an outer row's keys: each of their values of the kind its column's keys hold.
     *
     * @param keys the outer row's keys, none NULL
     * @return whether {@link #rowsOf} answers it
     */
    public boolean answers(final List<Object> keys) {
        return KeyColumnKinds.answers(kinds, keys);
    }

    /**
     * @param keys answered keys
     * @return the rows whose keys equal them, none where no row's do
     */
    public List<Row> rowsOf(final List<Object> keys) {
        final List<Row> held = rows.get(CorrelationKeys.canonical(keys));
        return held == null ? Collections.<Row>emptyList() : held;
    }

    /** @return the columns each kept row holds: the residual's, in order */
    public List<ResultSetColumn> itemColumns() {
        return new ArrayList<>(itemColumns);
    }
}
