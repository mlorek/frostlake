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
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a call's name reaches a user-defined routine's overloads, once for every reader of a function name: a
 * fully qualified name against the schema it names, a schema-qualified one against that schema of the current
 * database, a bare one against the first schema of the search path that holds an overload and then the current
 * schema — each schema by its exact canonical name. A name a built-in answers to reaches no user function: the
 * built-in answers it.
 */
public final class RoutineOverloads {

    private RoutineOverloads() {
    }

    /**
     * The schema a function name resolves against, or null where nothing does: no current database for a name
     * that needs one, or a database or schema that is not there.
     *
     * @param executor the executor resolving the name
     * @param parts    the name's parts, canonical
     * @return the schema, or null
     */
    public static Schema schemaOf(final QueryExecutor executor, final List<String> parts) {
        final Catalog catalog = executor.getCatalog();
        final String name = parts.get(parts.size() - 1);
        try {
            if (parts.size() == 3) {
                return catalog.databaseExact(parts.get(0)).schemaExact(parts.get(1));
            }
            final String database = catalog.getCurrentDatabase();
            if (database == null || parts.size() > 3) {
                return null;
            }
            if (parts.size() == 2) {
                return catalog.databaseExact(database).schemaExact(parts.get(0));
            }
            for (final Schema onPath : executor.searchPathSchemas()) {
                if (!onPath.getFunctionOverloads(name).isEmpty()) {
                    return onPath;
                }
            }
            final String schema = catalog.getCurrentSchema();
            return schema == null ? null : catalog.databaseExact(database).schemaExact(schema);
        } catch (final RuntimeException noSuchSchema) {
            return null;
        }
    }

    /**
     * The user-defined function overloads a call's name reaches; none for a bare name a built-in answers to, or a
     * name that resolves against no schema.
     *
     * @param executor the executor resolving the name
     * @param parts    the name's parts, canonical
     * @return the overloads, in the order defined
     */
    public static List<Function> functions(final QueryExecutor executor, final List<String> parts) {
        final String name = parts.get(parts.size() - 1);
        final String upper = name.toUpperCase(Locale.ROOT);
        if (parts.size() == 1 && (executor.getFunctionRegistry().hasFunction(upper)
                || executor.getFunctionRegistry().hasAggregateFunction(upper))) {
            return new ArrayList<Function>();
        }
        final Schema schema = schemaOf(executor, parts);
        return schema == null ? new ArrayList<Function>() : schema.getFunctionOverloads(name);
    }
}
