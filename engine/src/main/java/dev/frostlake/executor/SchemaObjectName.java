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
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;

/**
 * The object of a schema that a name written in a statement names: the schema it is looked for in, and what it is
 * called there. A bare name is looked for in the current schema, a qualified one where it says. A name of more than
 * three parts names nothing, and a database or a schema that is not there is refused by its own name, as the account
 * refuses DESCRIBE STREAM, TASK, PIPE and SEQUENCE and SHOW PARAMETERS IN TABLE and TASK (live-verified).
 */
public final class SchemaObjectName {

    private final Database database;
    private final Schema schema;
    private final String name;
    private final boolean qualified;

    private SchemaObjectName(final Database database, final Schema schema, final String name,
                             final boolean qualified) {
        this.database = database;
        this.schema = schema;
        this.name = name;
        this.qualified = qualified;
    }

    /**
     * Resolves a written name to its schema.
     *
     * @param catalog the catalog
     * @param parts   the name's canonical parts, one per dotted level
     * @return the schema and the object's name in it
     */
    public static SchemaObjectName of(final Catalog catalog, final String[] parts) {
        if (parts.length > 3) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        if (databaseName == null) {
            throw NoCurrentDatabaseRefusal.forStatement();
        }
        final String schemaName = parts.length >= 2 ? parts[parts.length - 2] : catalog.getCurrentSchema();
        if (schemaName == null) {
            throw new RuntimeException("No database or schema selected");
        }
        final Database database = catalog.getDatabase(databaseName);
        return new SchemaObjectName(database, database.getSchema(schemaName), parts[parts.length - 1],
            parts.length > 1);
    }

    /** The database the object is looked for in. */
    public Database database() {
        return database;
    }

    /** The schema the object is looked for in. */
    public Schema schema() {
        return schema;
    }

    /** The object's canonical name in its schema. */
    public String name() {
        return name;
    }

    /** Whether the statement wrote the name with a schema, or a database and a schema, before it. */
    public boolean isQualified() {
        return qualified;
    }

    /** The object's canonical name in full: DB.SCHEMA.NAME. */
    public String fullName() {
        return QualifiedName.join(database.getName(), schema.getName(), name);
    }
}
