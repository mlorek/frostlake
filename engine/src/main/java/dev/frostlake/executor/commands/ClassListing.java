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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.SystemDatabase;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.ClassNameWords;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * {@code SHOW <class>}: the instances of a class, and the two listings the account names by a word of the same
 * shape, SHOW DATASETS and SHOW CLASSES.
 *
 * <p>A class's instances are listed for the thirteen classes every account holds ({@link BuiltInClass}); no
 * instance is modelled, so the listing is empty, as is the listing of datasets. SHOW CLASSES lists the built-in
 * classes of the schemas its scope covers, by schema and name. What each refuses, and in which order, is
 * live-verified: a WITH PRIVILEGES after a class's name is a syntax error at PRIVILEGES and after DATASETS or
 * CLASSES an unsupported feature; a LIMIT 0 is refused before a class is looked up; a class that is missing before
 * its scope is read; and a scope as a schema-level listing's is — a name after IN ACCOUNT and a path after IN
 * DATABASE ask for a full search path, a schema's name of three parts names nothing, and a schema or a database
 * that does not exist is named by the class listings and not by the datasets'. A scope that names a kind of object
 * — a table, a view, a class, an application — holds nothing the three list (see {@link #refuseHolderScope}).
 */
final class ClassListing {

    private static final String[] DATASET_COLUMNS = {
        "created_on", "name", "database_name", "schema_name", "comment", "owner", "versions"
    };
    private static final String[] TERSE_DATASET_COLUMNS = {
        "created_on", "name", "kind", "database_name", "schema_name"
    };
    private static final String[] CLASS_COLUMNS = {
        "created_on", "name", "database_name", "schema_name", "version", "comment", "owner", "is_service_class",
        "owner_role_type"
    };
    private static final String[] INSTANCE_COLUMNS = {
        "created_on", "name", "database_name", "schema_name", "current_version", "comment", "owner",
        "owner_role_type"
    };

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    /**
     * @param catalog       the catalog the scope and the class resolve against
     * @param queryExecutor the executor that resolves an IDENTIFIER() reference
     */
    ClassListing(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
    }

    /**
     * Runs the statement.
     *
     * @param ctx the statement
     * @return its listing
     */
    ResultSet show(final FrostlakeParser.ShowClassStatementContext ctx) {
        requireShape(ctx);
        final ClassListingKind kind = kindOf(ctx.className());
        if (kind == ClassListingKind.INSTANCES) {
            final String[] parts = ClassNameParts.of(ctx.className(), queryExecutor);
            if (!BuiltInClass.isBuiltIn(parts, catalog)) {
                throw new RuntimeException(ShowScopeRefusal.missingClass(parts, catalog));
            }
            refuseHolderScope(ctx, kind);
            refuseOverlongContainer(ctx);
            scope(ctx).schemas(catalog, false);
            return new ResultSet(columns(INSTANCE_COLUMNS), new ArrayList<Row>());
        }
        refuseHolderScope(ctx, kind);
        final List<Schema> schemas = scope(ctx).schemas(catalog, kind == ClassListingKind.DATASETS);
        if (kind == ClassListingKind.DATASETS) {
            return new ResultSet(columns(ctx.TERSE() != null ? TERSE_DATASET_COLUMNS : DATASET_COLUMNS),
                new ArrayList<Row>());
        }
        return new ResultSet(columns(CLASS_COLUMNS), classRows(ctx, schemas));
    }

    /**
     * Refuses what the statement's shape alone rules out, which the account judges while it compiles the statement:
     * a class listing's WITH PRIVILEGES and LIMIT 0; for SHOW DATASETS and SHOW CLASSES a container named by too
     * many parts, a LIMIT 0 and a WITH PRIVILEGES. Nothing is looked up.
     *
     * @param ctx the statement
     */
    void requireShape(final FrostlakeParser.ShowClassStatementContext ctx) {
        final ClassListingKind kind = kindOf(ctx.className());
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        final boolean privileges = tail != null && tail.PRIVILEGES() != null;
        if (kind == ClassListingKind.INSTANCES) {
            if (privileges) {
                throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(
                    tail.PRIVILEGES().getSymbol(), tail.PRIVILEGES().getText())));
            }
            if (ctx.scopeKind != null
                    && BuiltInClass.isBuiltIn(ClassNameParts.of(ctx.className(), queryExecutor), catalog)) {
                // A class that exists has its scope judged before the page size; a missing one is looked up after.
                refuseHolderScope(ctx, kind);
            }
            requirePositiveLimit(tail);
            return;
        }
        if (ctx.scopeKind != null || ctx.scopeClass != null) {
            refuseHolderScope(ctx, kind);
        }
        refuseOverlongContainer(ctx);
        requirePositiveLimit(tail);
        if (privileges) {
            // The account spells the listing's name by adding an S to the kind's.
            throw new RuntimeException("Unsupported feature 'SHOW "
                + (kind == ClassListingKind.DATASETS ? "DATASETS" : "CLASSS") + " ... WITH PRIVILEGES <>'.");
        }
    }

    /** What the statement lists: by its word when the word, alone and unquoted, is DATASETS or CLASSES. */
    private static ClassListingKind kindOf(final FrostlakeParser.ClassNameContext name) {
        if (name.getStart() != name.getStop() || !ClassNameWords.isOwnListing(name.getStart())) {
            return ClassListingKind.INSTANCES;
        }
        return "DATASETS".equals(name.getStart().getText().toUpperCase(Locale.ROOT)) ? ClassListingKind.DATASETS
            : ClassListingKind.CLASSES;
    }

    private static void requirePositiveLimit(final FrostlakeParser.ShowTailContext tail) {
        if (tail != null && tail.LIMIT() != null && tail.INTEGER_LITERAL() != null) {
            ShowPageSize.require(tail.INTEGER_LITERAL().getText());
        }
    }

    /**
     * A scope that names a kind of object rather than a container. A table and a view hold nothing a class
     * statement lists, named or not; a class holds no dataset and no class, and an instance listing looks the
     * class up as it looks its own up; an application is looked up, and none is modelled — the datasets' listing
     * names nothing it misses (live-verified).
     */
    private void refuseHolderScope(final FrostlakeParser.ShowClassStatementContext ctx, final ClassListingKind kind) {
        final String noun = kind == ClassListingKind.DATASETS ? "DATASET"
            : kind == ClassListingKind.CLASSES ? "CLASS" : "INSTANCE";
        if (ctx.scopeKind != null) {
            throw ShowScopeKindRefusal.cannotShow(noun, ctx.scopeKind.getText().toUpperCase(Locale.ROOT));
        }
        if (ctx.scopeClass != null) {
            if (kind != ClassListingKind.INSTANCES) {
                throw ShowScopeKindRefusal.cannotShow(noun, "CLASS");
            }
            final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
            if (BuiltInClass.isBuiltIn(parts, catalog)) {
                throw ShowScopeKindRefusal.cannotShow(noun, "CLASS");
            }
            throw new RuntimeException(ShowScopeRefusal.missingClass(parts, catalog));
        }
        if (ctx.scopeApplication != null) {
            final FrostlakeParser.ObjectNameContext written = ctx.objectName();
            if (ctx.PACKAGE() == null && written.getStart() == written.getStop()
                    && written.getStart().getType() == FrostlakeLexer.PACKAGE) {
                // APPLICATION PACKAGE with no name after it: the package's name is missing where the tail begins.
                final Token next = ctx.showTail().getStart();
                throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(next,
                    next.getType() == Token.EOF ? "<EOF>" : next.getText())));
            }
            final String[] application = queryExecutor.resolveObjectNameParts(ctx.objectName());
            if (kind == ClassListingKind.DATASETS || application.length > 1) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled(
                ctx.PACKAGE() != null ? "Application package" : "Application",
                SqlIdentifiers.spellCanonical(application[0])));
        }
    }

    /** A name after IN ACCOUNT, and a path after IN DATABASE, ask for a full search path, naming the last part. */
    private void refuseOverlongContainer(final FrostlakeParser.ShowClassStatementContext ctx) {
        if (ctx.objectName() == null || ctx.ACCOUNT() == null && ctx.DATABASE() == null) {
            return;
        }
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        if (ctx.ACCOUNT() != null || catalog.partsWithinAccount(parts) > 1) {
            throw new RuntimeException(SqlCompilationError.of(
                "Must specify the full search path starting from database for " + parts[parts.length - 1]));
        }
    }

    /** The scope the statement's IN clause resolves to; a schema's name of three parts or more names nothing. */
    private ShowListingScope scope(final FrostlakeParser.ShowClassStatementContext ctx) {
        String written = null;
        if (ctx.objectName() != null) {
            if (ctx.DATABASE() == null
                    && catalog.partsWithinAccount(queryExecutor.resolveObjectNameParts(ctx.objectName())) > 2) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            written = queryExecutor.resolveObjectName(ctx.objectName());
        }
        return ShowListingScope.of(ctx.IN() != null, ctx.ACCOUNT() != null, ctx.DATABASE() != null,
            ctx.SCHEMA() != null, true, catalog, written);
    }

    /**
     * The built-in classes of the scope's schemas that a LIKE keeps, by schema and name. A LIMIT keeps the classes
     * that come first in the account's own order ({@link BuiltInClass#page}); a STARTS WITH and a LIMIT's FROM
     * narrow nothing (live-verified).
     */
    private List<Row> classRows(final FrostlakeParser.ShowClassStatementContext ctx, final List<Schema> schemas) {
        final Set<String> inScope = new HashSet<>();
        for (final Schema schema : schemas) {
            if (SystemDatabase.is(schema.getDatabaseName())) {
                inScope.add(schema.getName());
            }
        }
        final String like = ctx.LIKE() == null ? null : ParseTreeText.stringValue(ctx.stringText());
        final List<BuiltInClass> listed = new ArrayList<>();
        for (final BuiltInClass builtIn : BuiltInClass.values()) {
            if (inScope.contains(builtIn.schema())
                    && (like == null || ShowResultHelpers.matchesLike(builtIn.name(), like))) {
                listed.add(builtIn);
            }
        }
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail != null && tail.LIMIT() != null && tail.INTEGER_LITERAL() != null) {
            final int limit = Integer.parseInt(tail.INTEGER_LITERAL().getText());
            if (listed.size() > limit) {
                Collections.sort(listed, new Comparator<BuiltInClass>() {
                    @Override
                    public int compare(final BuiltInClass a, final BuiltInClass b) {
                        return Integer.compare(a.page(), b.page());
                    }
                });
                final List<BuiltInClass> kept = new ArrayList<>(listed.subList(0, limit));
                Collections.sort(kept);
                listed.clear();
                listed.addAll(kept);
            }
        }
        final List<Row> rows = new ArrayList<>();
        for (final BuiltInClass builtIn : listed) {
            rows.add(new Row(Arrays.<Object>asList(ShowResultHelpers.createdOn(builtIn.createdOn()), builtIn.name(),
                SystemDatabase.NAME, builtIn.schema(), null, null, "", "false", "")));
        }
        return rows;
    }

    private static List<ResultSetColumn> columns(final String[] names) {
        final List<ResultSetColumn> columns = new ArrayList<>();
        for (final String name : names) {
            columns.add(new ResultSetColumn(name, "created_on".equals(name) ? ShowResultHelpers.CREATED_ON
                : StringType.VARCHAR));
        }
        return columns;
    }
}
