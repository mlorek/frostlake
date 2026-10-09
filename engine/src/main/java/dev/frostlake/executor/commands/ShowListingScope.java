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

import dev.frostlake.executor.SearchPath;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a schema-level SHOW listing looks, as live resolves its scope — one rule for every listing, measured
 * listing by listing:
 *
 * <pre>
 *   IN ACCOUNT                   the account
 *   IN DATABASE d / IN SCHEMA s  the named container; IN s names a schema too
 *   IN DATABASE                  the current database, or the account when there is none
 *   IN SCHEMA                    the current schema, else the current database, else the account
 *   no IN clause                 the current schema for the listings that read it unscoped
 *                                ({@link ShowListing#listsCurrentSchemaUnscoped}), and the schemas of the
 *                                session's SEARCH_PATH for the others ({@link #searchPath}) — falling back, both, to
 *                                the current database when no schema is in use and to the account when no
 *                                database is
 * </pre>
 *
 * <p>So a missing name never refuses: {@code SHOW TABLES IN SCHEMA} in a session whose database has no schema in
 * use lists the database, and in a session with no database at all it lists every database's tables.
 */
final class ShowListingScope {

    private final ShowScopeLevel level;
    private final String name;

    private ShowListingScope(final ShowScopeLevel level, final String name) {
        this.level = level;
        this.name = name;
    }

    /**
     * The scope a listing's IN clause — or the lack of one — resolves to in the session.
     *
     * @param ctx       the statement
     * @param listing   its listing
     * @param catalog   the catalog holding the session's current database and schema
     * @param writtenName the canonical name the scope writes, or null when it writes none
     * @return the resolved scope
     */
    static ShowListingScope of(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                               final Catalog catalog, final String writtenName) {
        if (ctx.ACCOUNT() != null) {
            return new ShowListingScope(ShowScopeLevel.ACCOUNT, null);
        }
        if (ctx.IN() != null && ctx.DATABASE() == null && ctx.SCHEMA() == null) {
            // IN <name>: a schema, named as written.
            return new ShowListingScope(ShowScopeLevel.SCHEMA, writtenName);
        }
        if (writtenName != null) {
            return new ShowListingScope(ctx.DATABASE() != null ? ShowScopeLevel.DATABASE : ShowScopeLevel.SCHEMA,
                writtenName);
        }
        final String database = catalog.getCurrentDatabase();
        if (database == null) {
            return new ShowListingScope(ShowScopeLevel.ACCOUNT, null);
        }
        final boolean schemaWanted = ctx.SCHEMA() != null
            || ctx.IN() == null && listing.listsCurrentSchemaUnscoped();
        if (schemaWanted && catalog.getCurrentSchema() != null) {
            return new ShowListingScope(ShowScopeLevel.SCHEMA, null);
        }
        return new ShowListingScope(ShowScopeLevel.DATABASE, database);
    }

    /**
     * A scope of one schema, named by its canonical path.
     *
     * @param qualifiedName the schema's {@code database.schema} name, as {@link QualifiedName#join} spells it
     * @return the scope
     */
    static ShowListingScope schema(final String qualifiedName) {
        return new ShowListingScope(ShowScopeLevel.SCHEMA, qualifiedName);
    }

    /**
     * The schemas an unscoped listing of the search-path family reads, in path order, or null when the listing is
     * scoped, is not of that family, or the session has no current schema (the scope is then the database or the
     * account, from {@link #of}).
     *
     * <p>{@code $current} is the current schema and {@code $public} the current database's PUBLIC; any other entry
     * names a schema, qualified or not. A schema the path names twice is read once, and an entry naming nothing is
     * passed over.
     *
     * @param ctx        the statement
     * @param listing    its listing
     * @param catalog    the catalog holding the session's current database and schema
     * @param searchPath the session's SEARCH_PATH value
     * @return the schemas' {@code database.schema} names, or null
     */
    static List<String> searchPath(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                                   final Catalog catalog, final String searchPath) {
        if (ctx.IN() != null || ctx.BUILTIN() != null || !listing.listsSearchPathUnscoped()) {
            return null;
        }
        if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final Schema schema : SearchPath.schemas(catalog, searchPath)) {
            names.add(QualifiedName.join(schema.getDatabaseName(), schema.getName()));
        }
        return names;
    }

    /** @return the container level the listing looks in */
    ShowScopeLevel level() {
        return level;
    }

    /**
     * The container's name: the database for a DATABASE scope; for a SCHEMA scope the schema as written, or null
     * for the current schema; null for the account.
     *
     * @return the name, or null
     */
    String name() {
        return name;
    }

    /** @return whether the listing looks at the whole account */
    boolean isAccount() {
        return level == ShowScopeLevel.ACCOUNT;
    }

    /** @return whether the listing looks at one whole database */
    boolean isDatabase() {
        return level == ShowScopeLevel.DATABASE;
    }
}
