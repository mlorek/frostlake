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

package dev.frostlake.storage;

/**
 * Where a result column's values are read from when it projects a stored table's column as it is: the table's
 * database, schema and name, which the drivers' result-set metadata reports as the column's catalog, schema and table
 * (the account's driver reads them from each column's row type). A computed column has none.
 */
public final class ColumnSource {

    private final String database;
    private final String schema;
    private final String table;

    /**
     * @param database the table's database
     * @param schema   its schema
     * @param table    its name
     */
    public ColumnSource(final String database, final String schema, final String table) {
        this.database = database;
        this.schema = schema;
        this.table = table;
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getTable() {
        return table;
    }
}
