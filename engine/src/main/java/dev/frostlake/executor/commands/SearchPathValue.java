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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;

/**
 * The SEARCH_PATH a session sets: a comma-separated list of schemas an unqualified reference is looked for
 * in, with the two markers {@code $current} and {@code $public} standing for the session's own schema and
 * its database's PUBLIC.
 *
 * <p>The value as a whole may not be empty — {@code SEARCH_PATH = ''} is refused outright.
 *
 * <p>Every other entry has to NAME a schema that exists, and the account checks that as the ALTER runs
 * rather than when the path is later read — {@code Schema 'NOSUCH' does not exist}, with no "or not
 * authorized" tail. The entry is echoed as the reference spelled it: upper-cased when written unquoted,
 * quotes and all when quoted, which is also how it resolves.
 */
final class SearchPathValue {

    /** The two markers, which name no schema and are never looked up. */
    private static final String CURRENT = "$CURRENT";
    private static final String PUBLIC = "$PUBLIC";

    private SearchPathValue() {
    }

    /**
     * Refuse the first entry of {@code path} that names no schema.
     *
     * @param catalog the catalog the entries resolve against
     * @param path    the value written for SEARCH_PATH
     */
    static void requireResolvable(final Catalog catalog, final String path) {
        if (catalog == null || path == null) {
            return;
        }
        if (path.trim().isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Search path cannot be empty."));
        }
        for (final String written : path.split(",")) {
            final String entry = written.trim();
            if (entry.isEmpty() || CURRENT.equalsIgnoreCase(entry) || PUBLIC.equalsIgnoreCase(entry)) {
                continue;
            }
            if (!resolves(catalog, entry)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Schema '" + echo(entry) + "' does not exist"));
            }
        }
    }

    /** Whether one entry names a schema that exists. */
    private static boolean resolves(final Catalog catalog, final String entry) {
        final String[] parts = SqlIdentifiers.canonicalTextParts(entry);
        if (parts.length == 1) {
            if (catalog.getCurrentDatabase() == null) {
                return true;   // nothing to resolve against; the path is not judged here
            }
            return hasSchema(catalog, catalog.getCurrentDatabase(), parts[0]);
        }
        if (parts.length == 2) {
            return hasSchema(catalog, parts[0], parts[1]);
        }
        return true;   // not a schema reference at all; left to whoever reads the path
    }

    /** Whether {@code databaseName} holds a schema spelled exactly {@code schemaName}. */
    private static boolean hasSchema(final Catalog catalog, final String databaseName, final String schemaName) {
        for (final Database database : catalog.getAllDatabases()) {
            if (database.getName().equalsIgnoreCase(databaseName)) {
                return database.hasSchemaExact(schemaName);
            }
        }
        return false;
    }

    /** The entry as the refusal spells it: as written when quoted, upper-cased when not. */
    private static String echo(final String entry) {
        return entry.indexOf('"') >= 0 ? entry : entry.toUpperCase();
    }
}
