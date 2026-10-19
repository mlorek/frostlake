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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
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
        return of(ctx.IN() != null, ctx.ACCOUNT() != null, ctx.DATABASE() != null, ctx.SCHEMA() != null,
            listing.listsCurrentSchemaUnscoped(), catalog, writtenName);
    }

    /**
     * {@link #of(FrostlakeParser.ShowStatementContext, ShowListing, Catalog, String)} for a listing of another
     * statement rule, its IN clause given word by word.
     *
     * @param in            whether the statement writes an IN clause
     * @param account       whether the clause says ACCOUNT
     * @param database      whether it says DATABASE
     * @param schema        whether it says SCHEMA
     * @param readsSchemaUnscoped whether the listing, written with no IN clause, reads the current schema
     * @param catalog       the catalog holding the session's current database and schema
     * @param writtenName   the canonical name the scope writes, or null when it writes none
     * @return the resolved scope
     */
    static ShowListingScope of(final boolean in, final boolean account, final boolean database, final boolean schema,
                               final boolean readsSchemaUnscoped, final Catalog catalog, final String writtenName) {
        if (account) {
            return new ShowListingScope(ShowScopeLevel.ACCOUNT, null);
        }
        if (in && !database && !schema) {
            // IN <name>: a schema, named as written.
            return new ShowListingScope(ShowScopeLevel.SCHEMA, writtenName);
        }
        if (writtenName != null) {
            return new ShowListingScope(database ? ShowScopeLevel.DATABASE : ShowScopeLevel.SCHEMA, writtenName);
        }
        final String current = catalog.getCurrentDatabase();
        if (current == null) {
            return new ShowListingScope(ShowScopeLevel.ACCOUNT, null);
        }
        final boolean schemaWanted = schema || !in && readsSchemaUnscoped;
        if (schemaWanted && catalog.getCurrentSchema() != null) {
            return new ShowListingScope(ShowScopeLevel.SCHEMA, null);
        }
        return new ShowListingScope(ShowScopeLevel.DATABASE, current);
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

    /**
     * The schemas the scope covers. A scope naming a schema or a database that does not exist is refused: by the
     * sentence that names nothing for the kinds answering so, by the missing container's own refusal for the
     * others — see {@link SqlCompilationError#objectDoesNotExist()} for which kinds are which.
     *
     * @param catalog             the catalog the scope resolves against
     * @param reportedGenerically whether the listing's kind does not name what was missing
     * @return the schemas, in catalog order
     */
    List<Schema> schemas(final Catalog catalog, final boolean reportedGenerically) {
        final List<Schema> schemas = new ArrayList<>();
        try {
            if (isAccount()) {
                for (final Database database : catalog.getAllDatabases()) {
                    schemas.addAll(database.getAllSchemas());
                }
            } else if (isDatabase()) {
                schemas.addAll(catalog.databaseExact(name).getAllSchemas());
            } else if (name != null) {
                schemas.add(catalog.resolveSchema(name));
            } else {
                schemas.add(catalog.databaseExact(catalog.getCurrentDatabase()).schemaExact(catalog.getCurrentSchema()));
            }
        } catch (final RuntimeException missing) {
            if (reportedGenerically) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            throw missing;
        }
        return schemas;
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
