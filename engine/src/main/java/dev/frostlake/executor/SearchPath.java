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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The schemas a session's SEARCH_PATH names — {@code $current, $public} unless the session sets another — where an
 * unqualified name the current schema does not hold is looked for (live-verified). {@code $current} is the session's
 * schema and {@code $public} its database's PUBLIC; any other entry names a schema, qualified or not. A schema the
 * path names twice is read once, and an entry naming nothing is passed over.
 *
 * <p>A query, a DML statement, a sequence's NEXTVAL, a function call, a CALL, DESCRIBE TABLE and VIEW, GET_DDL,
 * SHOW COLUMNS, a GRANT on a table and the source of CREATE TABLE … LIKE, CLONE and of a stream read the path. A
 * statement that changes or drops an object — ALTER, DROP, TRUNCATE, COMMENT ON — and the describing of anything
 * but a table or a view look in the current schema alone, as does the body of a view.
 */
public final class SearchPath {

    /** The path of a session that sets none. */
    public static final String DEFAULT = "$current, $public";

    private SearchPath() {
    }

    /**
     * The path's schemas, in path order.
     *
     * @param catalog the catalog holding the session's current database and schema
     * @param value   the session's SEARCH_PATH value
     * @return the schemas; none when the session has no current database or schema
     */
    public static List<Schema> schemas(final Catalog catalog, final String value) {
        final List<Schema> schemas = new ArrayList<>();
        final String databaseName = catalog.getCurrentDatabase();
        final String schemaName = catalog.getCurrentSchema();
        if (databaseName == null || schemaName == null) {
            return schemas;
        }
        final Database database;
        try {
            database = catalog.getDatabase(databaseName);
        } catch (final RuntimeException dropped) {
            return schemas;
        }
        for (final String written : entries(value == null ? DEFAULT : value)) {
            final Schema schema = pathSchema(written, database, schemaName, catalog);
            if (schema != null && !schemas.contains(schema)) {
                schemas.add(schema);
            }
        }
        return schemas;
    }

    /**
     * The path's schemas as CURRENT_SCHEMAS names them, in path order: {@code DB.SCHEMA} for the two markers, and
     * for a schema an entry names itself the account before it, {@code ACCOUNT.DB.SCHEMA}.
     *
     * @param catalog the catalog holding the session's current database and schema
     * @param value   the session's SEARCH_PATH value
     * @param account the account's identifier, or null to leave it out
     * @return the names
     */
    public static List<String> names(final Catalog catalog, final String value, final String account) {
        final List<String> names = new ArrayList<>();
        final String databaseName = catalog.getCurrentDatabase();
        final String schemaName = catalog.getCurrentSchema();
        if (databaseName == null || schemaName == null) {
            return names;
        }
        final Database database;
        try {
            database = catalog.getDatabase(databaseName);
        } catch (final RuntimeException dropped) {
            return names;
        }
        final List<Schema> seen = new ArrayList<>();
        for (final String written : entries(value == null ? DEFAULT : value)) {
            final Schema schema = pathSchema(written, database, schemaName, catalog);
            if (schema == null || seen.contains(schema)) {
                continue;
            }
            seen.add(schema);
            final String name = schema.getDatabaseName() + "." + schema.getName();
            names.add(written.startsWith("$") || account == null ? name : account + "." + name);
        }
        return names;
    }

    /** The comma-separated entries of a search path, trimmed; a comma inside double quotes separates nothing. */
    private static List<String> entries(final String searchPath) {
        final List<String> entries = new ArrayList<>();
        final StringBuilder entry = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < searchPath.length(); i++) {
            final char c = searchPath.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == ',' && !quoted) {
                entries.add(entry.toString().trim());
                entry.setLength(0);
            } else {
                entry.append(c);
            }
        }
        entries.add(entry.toString().trim());
        return entries;
    }

    /** The schema one search-path entry names in the session, or null when it names none. */
    private static Schema pathSchema(final String written, final Database database, final String currentSchema,
                                     final Catalog catalog) {
        if (written.isEmpty()) {
            return null;
        }
        final String lower = written.toLowerCase(Locale.ROOT);
        try {
            if ("$current".equals(lower)) {
                return database.getSchema(currentSchema);
            }
            if ("$public".equals(lower)) {
                return database.hasSchemaExact("PUBLIC") ? database.getSchema("PUBLIC") : null;
            }
            // Any other entry is written as an identifier: unquoted parts fold, quoted ones keep their case.
            return catalog.resolveSchema(SqlIdentifiers.canonicalText(written));
        } catch (final RuntimeException missing) {
            return null;
        }
    }
}
