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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.SearchPath;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * CURRENT_SCHEMAS() — the session's search path as a JSON array of the schemas it names, in path order:
 * {@code ["DB.OTHER", "DB.PUBLIC"]} under the default {@code $current, $public} (see {@link SearchPath}).
 */
public class CurrentSchemas extends BuiltInFunction {

    private final Catalog catalog;
    private final SessionSearchPath session;

    public CurrentSchemas(final Catalog catalog) {
        this(catalog, null);
    }

    /**
     * @param catalog the catalog holding the session's current database and schema
     * @param session the session's search path, or null to read the default path
     */
    public CurrentSchemas(final Catalog catalog, final SessionSearchPath session) {
        super("CURRENT_SCHEMAS", StringType.VARCHAR);
        this.catalog = catalog;
        this.session = session;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final List<String> names = SearchPath.names(catalog, session == null ? SearchPath.DEFAULT : session.value(),
            session == null ? null : session.account());
        if (names.isEmpty()) {
            return "[\"" + catalog.getCurrentDatabase() + "." + catalog.getCurrentSchema() + "\"]";
        }
        final StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < names.size(); i++) {
            array.append(i == 0 ? "" : ", ").append('"').append(names.get(i)).append('"');
        }
        return array.append(']').toString();
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 0; }
}
