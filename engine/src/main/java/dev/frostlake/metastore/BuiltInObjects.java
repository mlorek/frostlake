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

import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.Warehouse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The objects an engine makes for itself as it starts — the SNOWFLAKE database and its schemas, the system roles, the
 * default user, the default warehouse, a configured default database — all created at the engine's start. A restart
 * that rebuilds them from a write-ahead log stamps them with the first start the log kept, so they report the
 * created_on they had on the engine that wrote it.
 */
public final class BuiltInObjects {

    private BuiltInObjects() {
    }

    /**
     * Stamps every object the catalog holds as created at an instant: called while it holds the built-in objects
     * alone, before anything is created or restored.
     *
     * @param catalog the catalog
     * @param started the engine's start
     */
    public static void stamp(final Catalog catalog, final Instant started) {
        final LocalDateTime hostLocal = LocalDateTime.ofInstant(started, ZoneId.systemDefault());
        for (final Database database : catalog.getAllDatabases()) {
            stamp(database, started);
        }
        for (final Role role : catalog.getAllRoles()) {
            role.restoreCreatedTime(hostLocal);
        }
        for (final User user : catalog.getAllUsers()) {
            user.restoreCreatedTime(hostLocal);
        }
        for (final Warehouse warehouse : catalog.getAllWarehouses()) {
            warehouse.restoreCreatedAt(hostLocal);
            warehouse.restoreLastStateChange(hostLocal);
        }
    }

    /**
     * Stamps a database the engine made for itself, and its schemas, as created at an instant.
     *
     * @param database the database
     * @param started  the engine's start
     */
    public static void stamp(final Database database, final Instant started) {
        database.restoreCreatedTime(started);
        for (final Schema schema : database.getAllSchemas()) {
            schema.restoreCreatedTime(started);
        }
    }
}
