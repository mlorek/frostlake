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

package dev.frostlake.persistence;

import java.util.HashMap;
import java.util.Map;

/** {@link TableDataStore} held entirely in memory — the transport for in-JVM engine cloning. */
public final class MemoryTableDataStore implements TableDataStore {

    private final Map<String, TableDataSnapshot> tables = new HashMap<>();
    private final Map<String, TableDataSnapshot> shadowedTables = new HashMap<>();

    @Override
    public void save(final String database, final String schema, final String table, final TableDataSnapshot data) {
        tables.put(key(database, schema, table), data);
    }

    @Override
    public TableDataSnapshot load(final String database, final String schema, final String table) {
        return tables.get(key(database, schema, table));
    }

    @Override
    public void saveShadowed(final String database, final String schema, final String table,
                             final TableDataSnapshot data) {
        shadowedTables.put(key(database, schema, table), data);
    }

    @Override
    public TableDataSnapshot loadShadowed(final String database, final String schema, final String table) {
        return shadowedTables.get(key(database, schema, table));
    }

    private static String key(final String database, final String schema, final String table) {
        return database + " " + schema + " " + table;
    }
}
