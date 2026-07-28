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

package dev.frostlake.metastore;

import dev.frostlake.storage.Row;

import java.util.List;
import java.util.Map;

/**
 * A snapshot of a dropped object retained for UNDROP (Time Travel restore): the metadata object
 * ({@code Table} / {@code Schema} / {@code Database}) plus the row data captured at drop time — a single
 * row list for a dropped table, or {@link #getTableRows()} keyed by fully-qualified table name for a dropped
 * schema or database (which may contain many tables). The rows are snapshotted because the drop RELEASES the
 * tables' storage, so the names are free to be created again while the data stays restorable.
 */
public class DroppedObject {

    private final Object object;
    private final List<Row> rows;
    private final Map<String, List<Row>> tableRows;

    public DroppedObject(final Object object, final List<Row> rows) {
        this(object, rows, null);
    }

    public DroppedObject(final Object object, final Map<String, List<Row>> tableRows) {
        this(object, null, tableRows);
    }

    private DroppedObject(final Object object, final List<Row> rows, final Map<String, List<Row>> tableRows) {
        this.object = object;
        this.rows = rows;
        this.tableRows = tableRows;
    }

    public Object getObject() {
        return object;
    }

    public List<Row> getRows() {
        return rows;
    }

    /** Rows of every table in a dropped schema/database, keyed by fully-qualified name; null for a table. */
    public Map<String, List<Row>> getTableRows() {
        return tableRows;
    }
}
