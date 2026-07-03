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

/**
 * A snapshot of a dropped object retained for UNDROP (Time Travel restore): the metadata object
 * ({@code Table} / {@code Schema} / {@code Database}) plus, for tables, the row data captured at drop time
 * (schemas and databases keep their storage, so no rows are snapshotted for them).
 */
public class DroppedObject {

    private final Object object;
    private final List<Row> rows;

    public DroppedObject(final Object object, final List<Row> rows) {
        this.object = object;
        this.rows = rows;
    }

    public Object getObject() {
        return object;
    }

    public List<Row> getRows() {
        return rows;
    }
}
