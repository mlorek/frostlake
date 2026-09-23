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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;

/**
 * The classes every account holds in its shared SNOWFLAKE database — what {@code SHOW CLASSES IN DATABASE SNOWFLAKE}
 * lists (live-verified). A built-in class exists, so a statement naming one resolves the INSTANCE it names instead
 * of refusing the class: {@code SHOW FUNCTIONS IN SNOWFLAKE.ML.FORECAST m} and {@code DROP SNOWFLAKE.ML.FORECAST m}
 * look {@code m} up like any schema object's name. No instance is modelled, so the lookup always misses. The
 * schemas of the SNOWFLAKE database resolve as live reaches them ({@link SnowflakeSchemas}), so another name there
 * misses as a class, and a name in a schema live lacks there — PUBLIC among them — misses as a schema.
 */
enum BuiltInClass {

    BUDGET("CORE"),
    QUOTA("CORE"),
    CLASSIFICATION_PROFILE("DATA_PRIVACY"),
    CUSTOM_CLASSIFIER("DATA_PRIVACY"),
    MARKETPLACE_ANALYTICS_NOTIFICATION("MARKETPLACE_NOTIFICATION"),
    ANOMALY_DETECTION("ML"),
    CLASSIFICATION("ML"),
    DOCUMENT_INTELLIGENCE("ML"),
    FORECAST("ML"),
    TOP_INSIGHTS("ML"),
    ANOMALY_INSIGHTS("WORKLOAD_INSIGHTS"),
    COST_INSIGHTS("WORKLOAD_INSIGHTS"),
    PERFORMANCE_EXPLORER("WORKLOAD_OPTIMIZATION");

    /** The shared database every built-in class lives in. */
    private static final String DATABASE = "SNOWFLAKE";

    private final String schema;

    BuiltInClass(final String schema) {
        this.schema = schema;
    }

    /**
     * Whether a class name, resolved to its three canonical parts, names a built-in class.
     *
     * @param database the class's database
     * @param schema   its schema
     * @param name     its name
     * @return whether the class is built in
     */
    static boolean isBuiltIn(final String database, final String schema, final String name) {
        if (!DATABASE.equals(database)) {
            return false;
        }
        for (final BuiltInClass builtIn : values()) {
            if (builtIn.schema.equals(schema) && builtIn.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a class name, as written and resolved to its canonical parts, names a built-in class: a bare name in
     * the current schema, a two-part one in the current database.
     *
     * @param parts   the class name's canonical parts
     * @param catalog the catalog holding the session's current database and schema
     * @return whether the class is built in
     */
    static boolean isBuiltIn(final String[] parts, final Catalog catalog) {
        if (parts.length == 3) {
            return isBuiltIn(parts[0], parts[1], parts[2]);
        }
        if (parts.length == 2) {
            return isBuiltIn(catalog.getCurrentDatabase(), parts[0], parts[1]);
        }
        return parts.length == 1 && isBuiltIn(catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]);
    }

    /**
     * Whether a class or an instance name resolves its schema: in the SNOWFLAKE database one of the schemas live
     * reaches there ({@link SnowflakeSchemas}) whether or not the catalog models it, and PUBLIC — which that
     * database lacks — not even when the catalog models one; elsewhere a schema the catalog holds.
     *
     * @param database the schema's database, as the catalog holds it
     * @param schema   the schema's canonical name
     * @return whether the schema resolves
     */
    static boolean resolvesSchema(final Database database, final String schema) {
        return DATABASE.equals(database.getName()) ? SnowflakeSchemas.resolves(schema) : database.hasSchemaExact(schema);
    }

    /**
     * The canonical path of the instance a built-in class names, resolved as any schema object's name is: a bare
     * name in the current schema, a two-part one in the current database, a missing database or schema refused as
     * such ({@link #resolvesSchema}), and a name of more than three parts naming nothing.
     *
     * @param parts   the instance name's canonical parts
     * @param catalog the catalog the name resolves against
     * @return the instance's path, as {@code QualifiedName.join} spells it
     */
    static String instancePath(final String[] parts, final Catalog catalog) {
        final String[] path = catalog.withoutAccount(parts, 3);
        final String name = path[path.length - 1];
        if (path.length > 1) {
            final String databaseName = path.length == 3 ? path[0] : catalog.getCurrentDatabase();
            if (DATABASE.equals(databaseName)) {
                final Database database = catalog.databaseExact(databaseName);
                final String schema = path[path.length - 2];
                if (!resolvesSchema(database, schema)) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Schema",
                        QualifiedName.join(database.getName(), schema)));
                }
                return QualifiedName.join(database.getName(), schema, name);
            }
        }
        return catalog.requireOwningSchema(QualifiedName.of(path)).qualifiedName(name);
    }
}
