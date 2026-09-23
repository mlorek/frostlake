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

import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.StorageEngine;

/**
 * A TEMPORARY table may take the name of a PERMANENT table in its schema, and a permanent table may be
 * created under a name a temporary table holds; either way the temporary table shadows the permanent one.
 * References, writes, SHOW TABLES and every DDL statement by name reach the temporary table, while
 * INFORMATION_SCHEMA lists both, and dropping, renaming or moving the temporary table uncovers the
 * permanent one (live-verified).
 *
 * <p>The schema keeps the hidden table beside its visible ones and the storage engine keeps its rows
 * beside theirs, both under the shared name, so no path that reaches a table by name needs to know a
 * permanent one is hidden there. Between statements a table is hidden only beneath a temporary table.
 */
public final class TableShadows {

    private TableShadows() {
    }

    /**
     * Before a CREATE TABLE: when the name is held by a table of the other persistence, trade it for the
     * table hidden beneath it, so the statement meets only the tables of its own kind. A temporary create
     * then finds no table where a permanent one stood; a permanent create meets the permanent table a
     * temporary one hid, if any, and decides OR REPLACE and IF NOT EXISTS against it alone.
     *
     * <p>{@link #settle} must follow the statement however it ends.
     */
    public static void stepAside(final Schema schema, final StorageEngine storage, final String database,
                                    final String name, final boolean temporary) {
        final Table holder = schema.tableExact(name);
        if (holder != null && holder.isTemporary() != temporary) {
            swap(schema, storage, database, name);
        }
    }

    /**
     * After a statement that created, dropped, renamed or moved a table of that name: bring a temporary
     * table back above the permanent one, and uncover a hidden permanent table the name no longer covers.
     */
    public static void settle(final Schema schema, final StorageEngine storage, final String database,
                              final String name) {
        final Table hidden = schema.shadowedTable(name);
        if (hidden != null && (hidden.isTemporary() || schema.tableExact(name) == null)) {
            swap(schema, storage, database, name);
        }
    }

    private static void swap(final Schema schema, final StorageEngine storage, final String database,
                             final String name) {
        schema.swapShadow(name);
        storage.swapShadow(QualifiedName.key(database, schema.getName(), name));
    }
}
