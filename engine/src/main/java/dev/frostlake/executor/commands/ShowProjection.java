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

package dev.frostlake.executor.commands;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/** A listing narrowed to some of its columns, in the order given. */
final class ShowProjection {

    private ShowProjection() {
    }

    /** The listing's rows with only the named columns, which it must carry. */
    static ResultSet project(final ResultSet listing, final List<String> columnNames) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        final List<Integer> indexes = new ArrayList<>();
        for (final String name : columnNames) {
            final int index = listing.getColumnIndex(name);
            indexes.add(index);
            columns.add(listing.getColumns().get(index));
        }
        final List<Row> rows = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            final List<Object> values = new ArrayList<>();
            for (final Integer index : indexes) {
                values.add(row.getValue(index.intValue()));
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }
}
