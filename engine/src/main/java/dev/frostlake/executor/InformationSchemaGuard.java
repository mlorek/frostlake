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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;

/**
 * The guard over INFORMATION_SCHEMA's own views. A statement that would change one — its comment, its rows, its
 * columns, the privileges on it — is refused once the view is found, before any kind check, so a statement that
 * names a table is answered for the VIEW too (live-verified).
 */
public final class InformationSchemaGuard {

    /** The schema's name. */
    public static final String SCHEMA = "INFORMATION_SCHEMA";

    private InformationSchemaGuard() {
    }

    /**
     * Refuses a statement that changes the object a name reaches when that object is one of INFORMATION_SCHEMA's
     * views. A name that reaches nothing there is left to the statement's own refusal.
     *
     * @param catalog the catalog
     * @param parts the object's name as written, canonical: one, two or three parts
     */
    public static void refuseWrite(final Catalog catalog, final String[] parts) {
        final Schema schema = schemaOf(catalog, parts);
        if (schema == null || !SCHEMA.equals(schema.getName()) || parts.length == 0) {
            return;
        }
        final String name = parts[parts.length - 1];
        if (schema.hasView(name)) {
            throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("view",
                schema.getView(name).getName()));
        }
    }

    /**
     * Refuses a grant or a revoke that names INFORMATION_SCHEMA itself, as a schema or as the scope of a bulk
     * grant: {@code Insufficient privileges to operate on schema 'INFORMATION_SCHEMA'.}
     *
     * @param schemaParts the schema's name as written, canonical: one or two parts
     */
    public static void refuseSchemaGrant(final String[] schemaParts) {
        if (schemaParts != null && schemaParts.length > 0 && SCHEMA.equals(schemaParts[schemaParts.length - 1])) {
            throw new RuntimeException(SqlAccessControlError.insufficientPrivileges("schema", SCHEMA));
        }
    }

    private static Schema schemaOf(final Catalog catalog, final String[] parts) {
        if (parts == null || parts.length < 1 || parts.length > 3) {
            return null;
        }
        final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        final String schemaName = parts.length == 3 ? parts[1] : parts.length == 2 ? parts[0]
            : catalog.getCurrentSchema();
        if (databaseName == null || schemaName == null || !catalog.hasDatabaseExact(databaseName)) {
            return null;
        }
        final Database database = catalog.databaseExact(databaseName);
        return database.hasSchemaExact(schemaName) ? database.schemaExact(schemaName) : null;
    }
}
