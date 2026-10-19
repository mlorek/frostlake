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

import dev.frostlake.executor.GrantedObject;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.PrivilegeRefusal;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.SchemaObjectName;
import dev.frostlake.executor.ShowCommandExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SystemSchema;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurityObjectKind;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledDropKind;
import dev.frostlake.parser.UnmodelledKindWords;
import dev.frostlake.security.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for SHOW and DESCRIBE commands:
 * SHOW DATABASES, SHOW SCHEMAS, SHOW TABLES, DESCRIBE TABLE, etc.
 */
public class ShowCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(ShowCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;

    public ShowCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.columnParser = new ColumnDefinitionParser(catalog, queryExecutor);
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    /**
     * SHOW of a class's instances, {@code SHOW <class>}, and the listings SHOW DATASETS and SHOW CLASSES
     * ({@link ClassListing}).
     *
     * @param ctx the statement
     * @return the listing
     */
    public ResultSet handleShowClassStatement(final FrostlakeParser.ShowClassStatementContext ctx) {
        return new ClassListing(catalog, queryExecutor).show(ctx);
    }

    /**
     * Refuses what a SHOW listing's shape alone rules out, in live's order: the syntax of its modifiers, then the
     * scopes its shape rules out, a LIMIT 0 and a WITH PRIVILEGES it does not filter by. The account raises these
     * while it compiles the statement, so a block that holds one is refused before any of it runs
     * ({@link ShowShapeCompiler}); nothing is looked up here.
     *
     * @param ctx the statement
     */
    public void requireShape(final FrostlakeParser.ShowStatementContext ctx) {
        requireShape(ctx, true);
    }

    /**
     * As {@link #requireShape(FrostlakeParser.ShowStatementContext)}; without {@code namesKnown} — a block's statement
     * whose names wait for a variable's value — only what no name decides is judged: the modifiers' syntax, a
     * LIMIT 0 and a WITH PRIVILEGES.
     *
     * @param ctx        the statement
     * @param namesKnown whether the statement's names can be read
     */
    public void requireShape(final FrostlakeParser.ShowStatementContext ctx, final boolean namesKnown) {
        if (ctx.VERSIONS() != null) {
            new AppObjectListing(catalog).requireVersionsShape(ctx);
            return;
        }
        final ShowListing listing = ShowListing.of(ctx);
        UnmodelledListings.requireParsed(ctx, listing);
        ShowTailSyntax.requireParsed(ctx, listing);
        if (listing == ShowListing.REGIONS && ctx.IN() != null) {
            throw RegionsListing.scopeRefusal(ctx);
        }
        if (ctx.GRANTS() != null && ctx.showGrantsIn() != null) {
            // The grant listing reads an IN and nothing after it: the statement is refused at what follows it.
            throw SyntaxAfter.unexpected(ctx.showGrantsIn());
        }
        RoutineSignatureForm.requireTypesOnly(ctx.showGrantsTarget() == null ? null : ctx.showGrantsTarget().dataTypeList());
        if (namesKnown) {
            ShowScopeKindRefusal.refuseShape(ctx, listing, queryExecutor);
            ShowScopeRefusal.refuseOverlongDatabase(ctx, catalog, queryExecutor, listing);
            ShowScopeRefusal.refuseOverlongSchema(ctx, catalog, queryExecutor, listing, false);
            ShowScopeRefusal.requireListable(ctx, catalog, queryExecutor, listing);
        }
        if (ctx.showTail() != null && ctx.showTail().LIMIT() != null) {
            // A size of zero, or one the clause does not read, is judged on the clause: before the listing's scope
            // is looked up though after a scope its shape rules out, and whether or not the listing goes on to
            // paginate (live-verified).
            ShowPageSize.requireReadable(ctx.showTail().INTEGER_LITERAL().getText());
        }
        ShowTailSyntax.refuseUnfilteredPrivileges(ctx, listing);
    }

    /**
     * As {@link #requireShape(FrostlakeParser.ShowStatementContext)}, for {@code SHOW <class>}, SHOW DATASETS and
     * SHOW CLASSES.
     *
     * @param ctx the statement
     */
    public void requireShape(final FrostlakeParser.ShowClassStatementContext ctx) {
        new ClassListing(catalog, queryExecutor).requireShape(ctx);
    }

    /**
     * Refuse a DESCRIBE for the first property the account refuses in it — a name it does not know
     * ({@code invalid parameter 'x'}), a name left without its value — as it does before the object is looked up:
     * a block is refused for it while it compiles, so the refusal carries no wrapper (live-verified).
     *
     * A kind the account refuses as a feature before it reads any property is left to that refusal.
     *
     * @param ctx the DESCRIBE, of any kind
     */
    public void requireProperties(final ParserRuleContext ctx) {
        if (ctx instanceof FrostlakeParser.DescribeStatementContext
                && ((FrostlakeParser.DescribeStatementContext) ctx).unmodelledKind() != null) {
            final UnmodelledDropKind unmodelled =
                UnmodelledDropKind.of(((FrostlakeParser.DescribeStatementContext) ctx).unmodelledKind());
            if (unmodelled == UnmodelledDropKind.RESOURCE_MONITOR || unmodelled == UnmodelledDropKind.ORGANIZATION) {
                return;
            }
        }
        DescribeProperties.describesStage(ctx, queryExecutor);
    }

    /**
     * A SHOW listing, its refusals judged in live's order: what its shape rules out ({@link #requireShape}), and
     * only then the lookups — an account's identifier of too many parts, a model, a schema's path, the scope.
     */
    public ResultSet handleShowStatement(final FrostlakeParser.ShowStatementContext ctx) {
        requireShape(ctx);
        if (ctx.VERSIONS() != null) {
            return new AppObjectListing(catalog).showVersions(ctx);
        }
        final ShowListing listing = ShowListing.of(ctx);
        ShowScopeRefusal.refuseOverlongAccount(ctx, queryExecutor, listing);
        ShowScopeRefusal.refuseMissingModel(ctx);
        ShowScopeRefusal.refuseOverlongSchema(ctx, catalog, queryExecutor, listing, true);
        if (listing == ShowListing.ROLES && ShowScopeKindRefusal.scopeKind(ctx) == ShowScopeKind.SERVICE
                && ctx.showInstanceName() != null && ctx.showInstanceName().showScopeName() != null
                && ctx.showInstanceName().showScopeName().identifier().size() == 1) {
            final ResultSet serviceRoles = new ContainerServicesHandler(catalog, queryExecutor.getEngineConfig())
                .serviceRolesOrNull(SqlIdentifiers.canonical(ctx.showInstanceName().showScopeName().identifier(0)));
            if (serviceRoles != null) {
                return serviceRoles;
            }
        }
        ShowScopeKindRefusal.refuseMissing(ctx, listing, catalog, queryExecutor);
        ShowScopeRefusal.refuseMissingClass(ctx, catalog, queryExecutor, listing);
        final List<String> searchPath = ShowListingScope.searchPath(ctx, listing, catalog, sessionSearchPath());
        ResultSet result = searchPath == null ? handleShowStatementInternal(ctx, null)
            : searchPathListing(ctx, listing, searchPath);
        result = ShowPrivilegeFilter.apply(result, ctx, listing, catalog);
        // A grant listing reads a LIKE and narrows nothing by it (live-verified).
        result = ctx.GRANTS() != null ? result : applyLikeFilter(result, getLikePattern(ctx));
        final ShowModifierProfile profile = ShowModifierProfile.forStatement(ctx);
        if (profile.sortsByName() && searchPath == null) {
            // A search-path listing keeps its path order: the schema the path reaches first lists first.
            result = sortByName(result);
        }
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail != null) {
            int literalIndex = 0;
            if (tail.STARTS() != null) {
                final String prefix = ParseTreeText.stringBody(tail.stringText(literalIndex++));
                if (profile.honorsStartsWith()) {
                    result = applyStartsWith(result, prefix);
                }
            }
            if (tail.ROOT() != null) {
                result = rootTasksOnly(result);
            }
            if (tail.LIMIT() != null) {
                // A size past the largest page is refused once the scope is found (live-verified).
                final int limit = ShowPageSize.require(tail.INTEGER_LITERAL().getText());
                final String fromName = tail.FROM() != null
                    ? ParseTreeText.stringBody(tail.stringText(literalIndex)) : null;
                if (profile.honorsLimit()) {
                    result = applyLimitFrom(result, limit, fromName, profile.pageStart());
                }
            }
            // WITH PRIVILEGES p1, p2 is accepted but the listing is not privilege-filtered.
        }
        if (ctx.TERSE() != null) {
            result = applyTerse(result, profile);
        }
        return result;
    }

    /**
     * SHOW TASKS … ROOT ONLY: the tasks with no predecessors — the standalone tasks and the roots of task
     * graphs.
     */
    private ResultSet rootTasksOnly(final ResultSet listing) {
        int databaseColumn = -1;
        int schemaColumn = -1;
        int nameColumn = -1;
        int predecessorsColumn = -1;
        for (int i = 0; i < listing.getColumns().size(); i++) {
            final String column = listing.getColumns().get(i).getName();
            if ("database_name".equalsIgnoreCase(column)) {
                databaseColumn = i;
            } else if ("schema_name".equalsIgnoreCase(column)) {
                schemaColumn = i;
            } else if ("name".equalsIgnoreCase(column)) {
                nameColumn = i;
            } else if ("predecessors".equalsIgnoreCase(column)) {
                predecessorsColumn = i;
            }
        }
        final List<Row> roots = new ArrayList<>();
        for (final Row row : listing.getRows()) {
            if (isRootTask(row, databaseColumn, schemaColumn, nameColumn, predecessorsColumn)) {
                roots.add(row);
            }
        }
        return new ResultSet(listing.getColumns(), roots);
    }

    private boolean isRootTask(final Row row, final int databaseColumn, final int schemaColumn,
                               final int nameColumn, final int predecessorsColumn) {
        if (databaseColumn >= 0 && schemaColumn >= 0 && nameColumn >= 0) {
            try {
                final Task task = catalog.getDatabase(String.valueOf(row.getValue(databaseColumn)))
                    .getSchema(String.valueOf(row.getValue(schemaColumn)))
                    .getTask(String.valueOf(row.getValue(nameColumn)));
                if (task != null) {
                    return task.getPredecessors().isEmpty();
                }
            } catch (final RuntimeException unresolved) {
                logger.trace("ROOT ONLY falls back to the predecessors cell: {}", unresolved.getMessage());
            }
        }
        final Object predecessors = predecessorsColumn >= 0 ? row.getValue(predecessorsColumn) : null;
        return predecessors == null || predecessors.toString().replaceAll("\\s", "").equals("[]");
    }

    /**
     * The SHOW itself. An UNSCOPED listing — no IN clause — in a session with no current database
     * lists the whole account, as its IN ACCOUNT form does: tables, views, materialized views, columns,
     * schemas, objects, sequences, stages, streams, tasks, pipes, dynamic tables, file formats, tags, the
     * policy kinds, contacts, cortex search services, functions, procedures and keys alike, ordered
     * database, schema, name, each row naming its own database (live-verified).
     */
    /**
     * SHOW SCHEMAS over its scope. A database is named with or without its DATABASE keyword, and a bare
     * {@code IN DATABASE} is the current one; a name that is no database — a missing one, a schema's, or
     * one with more than one part — is refused as no object, and a SCHEMA or TABLE scope holds no schemas
     * at all: {@code Unsupported statement type 'Cannot show objects of type SCHEMA in SCHEMA'.}
     * (live-verified).
     */
    private ResultSet showSchemas(final ShowCommandExecutor showExecutor,
                                  final FrostlakeParser.ShowStatementContext ctx) {
        final boolean history = ctx.HISTORY() != null;
        if (ctx.ACCOUNT() != null) {
            return showExecutor.showSchemasInAccount(history);
        }
        if (ctx.SCHEMA() != null || ctx.TABLE() != null) {
            throw new RuntimeException(SqlCompilationError.of("Unsupported statement type 'Cannot show objects of type"
                + " SCHEMA in " + (ctx.SCHEMA() != null ? "SCHEMA" : "TABLE") + "'."));
        }
        if (ctx.objectName() == null) {
            return catalog.getCurrentDatabase() == null ? showExecutor.showSchemasInAccount(history)
                : showExecutor.showSchemas(null, history);
        }
        final String[] parts = scopeParts(ctx);
        if (parts.length != 1 || !namesADatabase(parts[0])) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        return showExecutor.showSchemas(parts[0], history);
    }

    /** Whether a canonical name is exactly a database's. */
    private boolean namesADatabase(final String name) {
        for (final Database database : catalog.getAllDatabases()) {
            if (database.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * SHOW MODELS: the account's layout, answered empty.
     *
     * <p>A model is not an object this engine holds, so the listing has no rows to give — but a
     * listing that REFUSES is a different thing from one that is empty, and a client reading the
     * account's columns back through RESULT_SCAN needs the names to exist either way. The shape is
     * live's, column for column.
     *
     * @return the empty listing
     */
    private ResultSet showModels() {
        return new ResultSet(Arrays.asList(
            new ResultSetColumn("created_on", ShowResultHelpers.CREATED_ON),
            new ResultSetColumn("name", StringType.VARCHAR),
            new ResultSetColumn("model_type", StringType.VARCHAR),
            new ResultSetColumn("database_name", StringType.VARCHAR),
            new ResultSetColumn("schema_name", StringType.VARCHAR),
            new ResultSetColumn("comment", StringType.VARCHAR),
            new ResultSetColumn("owner", StringType.VARCHAR),
            new ResultSetColumn("default_version_name", StringType.VARCHAR),
            new ResultSetColumn("versions", StringType.VARCHAR),
            new ResultSetColumn("aliases", StringType.VARCHAR)),
            new ArrayList<Row>());
    }

    /** The session's SEARCH_PATH, or its default when the session has not set one. */
    private String sessionSearchPath() {
        final Object value = queryExecutor.getSecurityManager() == null ? null
            : queryExecutor.getSecurityManager().getSessionContext().getSessionParameter("SEARCH_PATH");
        return value != null ? value.toString() : "$current, $public";
    }

    /**
     * An unscoped listing of the search-path family: each path schema's own listing in path order, keeping of
     * the rows that share a name only the first — the object an unqualified reference would reach. A column is
     * reached through its relation's name, and a routine through its name and argument types, so an overload
     * the first schema lacks is still listed from the next. The routine listings are ordered by name after the
     * merge, a name's rows keeping their path order; the others keep the path order throughout.
     */
    private ResultSet searchPathListing(final FrostlakeParser.ShowStatementContext ctx, final ShowListing listing,
                                        final List<String> schemas) {
        final boolean routines = listing == ShowListing.FUNCTIONS || listing == ShowListing.PROCEDURES;
        ResultSet shape = null;
        final List<Row> rows = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final boolean sortsByName = ShowModifierProfile.forStatement(ctx).sortsByName();
        for (final String schema : schemas) {
            // Each schema's rows in the listing's own name order; the schemas themselves in path order.
            final ResultSet listed = handleShowStatementInternal(ctx, ShowListingScope.schema(schema));
            final ResultSet part = sortsByName ? sortByName(listed) : listed;
            if (shape == null) {
                shape = part;
            }
            final int keyColumn = columnIndexOrMissing(part,
                listing == ShowListing.COLUMNS ? "table_name" : "name");
            final int argumentsColumn = routines ? columnIndexOrMissing(part, "arguments") : -1;
            final Map<String, String> declarations = routines ? queryExecutor.getShowExecutor()
                .namedRoutineDeclarations(schema, listing == ShowListing.PROCEDURES) : null;
            // A name is claimed by the schema that lists it first; that schema keeps all of its rows for it,
            // so a relation's every column is listed, and a later schema's rows for the name are not.
            final Set<String> claimed = new HashSet<>();
            for (final Row row : part.getRows()) {
                final String key = pathKey(row, keyColumn, argumentsColumn, declarations);
                if (!seen.contains(key)) {
                    claimed.add(key);
                    rows.add(row);
                }
            }
            seen.addAll(claimed);
        }
        if (shape == null) {
            // A path naming no schema lists nothing, in the listing's own shape.
            shape = handleShowStatementInternal(ctx, ShowListingScope.schema(QualifiedName.join(
                catalog.getCurrentDatabase(), catalog.getCurrentSchema())));
            return new ResultSet(shape.getColumns(), new ArrayList<Row>());
        }
        if (routines) {
            final int nameColumn = columnIndexOrMissing(shape, "name");
            Collections.sort(rows, new Comparator<Row>() {
                @Override
                public int compare(final Row left, final Row right) {
                    return String.valueOf(left.getValue(nameColumn)).compareTo(
                        String.valueOf(right.getValue(nameColumn)));
                }
            });
        }
        return new ResultSet(shape.getColumns(), rows);
    }

    /**
     * The name a search-path row is reached by: its key column, and for a routine its whole declaration, the
     * parameters' names included. Live hides a later schema's routine only when the declaration matches in its
     * parameter names too: {@code f(x INT)} in the current schema hides PUBLIC's {@code f(x INT)} but not PUBLIC's
     * {@code f(y INT)}, and both of those are listed. The arguments column prints the types alone, so the names
     * come from the schema's own declarations, looked up by what the column prints.
     */
    private static String pathKey(final Row row, final int keyColumn, final int argumentsColumn,
                                  final Map<String, String> declarations) {
        final String key = keyColumn < 0 ? "" : String.valueOf(row.getValue(keyColumn));
        if (argumentsColumn < 0) {
            return key;
        }
        final String arguments = String.valueOf(row.getValue(argumentsColumn));
        final int returns = arguments.indexOf(" RETURN ");
        final String listed = returns < 0 ? arguments : arguments.substring(0, returns);
        final String declared = declarations == null ? null : declarations.get(listed);
        return key + "|" + (declared != null ? declared : listed);
    }

    private ResultSet handleShowStatementInternal(final FrostlakeParser.ShowStatementContext ctx,
                                                  final ShowListingScope forced) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        final ShowListing listed = ShowListing.of(ctx);
        if (listed.isUnmodelled()) {
            return new UnmodelledListings(catalog, queryExecutor).show(ctx, listed, listingScope(ctx, listed, forced));
        }
        if (ctx.MODELS() != null) {
            if (ctx.objectName() != null && ctx.ACCOUNT() == null && ctx.TABLE() == null
                    && ctx.showInstanceName() == null) {
                // A schema or a database that does not exist is not named (live-verified).
                listingScope(ctx, ShowListing.OTHER, forced).schemas(catalog, true);
            }
            return showModels();
        }
        if (ctx.NOTEBOOKS() != null || ctx.STREAMLITS() != null) {
            return new AppObjectListing(catalog).show(ctx.NOTEBOOKS() != null ? AppObjectKind.NOTEBOOK
                : AppObjectKind.STREAMLIT, listingScope(ctx, ShowListing.of(ctx), forced));
        }
        if (ctx.DATABASES() != null) {
            return showExecutor.showDatabases(ctx.HISTORY() != null);
        } else if (ctx.SCHEMAS() != null) {
            return showSchemas(showExecutor, ctx);
        } else if (ctx.DYNAMIC() != null && ctx.TABLES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.DYNAMIC_TABLES, forced);
            if (scope.isAccount()) {
                return showExecutor.showDynamicTablesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showDynamicTablesInDatabase(scope.name());
            }
            return showExecutor.showDynamicTables(scope.name());
        } else if (ctx.HYBRID() != null && ctx.TABLES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.HYBRID_TABLES, forced);
            if (scope.isAccount()) {
                return showExecutor.showHybridTablesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showHybridTablesInDatabase(scope.name());
            }
            return showExecutor.showHybridTables(scope.name());
        } else if (ctx.ICEBERG() != null) {
            return IcebergTables.listing(tablesInScope(showExecutor, ctx, forced), catalog);
        } else if (ctx.EVENT() != null) {
            return EventTableCommandHandler.listing(tablesInScope(showExecutor, ctx, forced));
        } else if (ctx.EXTERNAL() != null && ctx.TABLES() != null) {
            // The scope is still judged — IN TABLE is refused like any other schema-level listing's, and a
            // schema or a database that does not exist as a table listing's is — but there is never anything
            // to list: an external table reads files this engine cannot reach.
            listingScope(ctx, ShowListing.EXTERNAL_TABLES, forced).schemas(catalog, true);
            return ExternalTableListing.listing();
        } else if (ctx.INTEGRATIONS() != null) {
            return new IntegrationCommandHandler(catalog, queryExecutor).show(ctx.integrationKind());
        } else if (ctx.VOLUMES() != null) {
            return new ExternalVolumeCommandHandler(catalog).show();
        } else if (ctx.TABLES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.TABLES, forced);
            // HISTORY adds the dropped tables UNDROP can still restore, and their dropped_on.
            final boolean history = ctx.HISTORY() != null;
            if (scope.isAccount()) {
                return showExecutor.showTablesInAccount(history);
            }
            if (scope.isDatabase()) {
                return showExecutor.showTablesInDatabase(scope.name(), history);
            }
            return showExecutor.showTables(scope.name(), history);
        } else if (ctx.COLUMNS() != null) {
            final String name = ctx.objectName() != null ? scopeName(ctx) : null;
            if (ctx.TABLE() != null || ctx.VIEW() != null || ctx.IN() != null && ctx.ACCOUNT() == null
                    && ctx.DATABASE() == null && ctx.SCHEMA() == null) {
                // A relation scope resolves one object, along the session's SEARCH_PATH; with no name it is the
                // current schema's relations.
                return showExecutor.showColumnsScoped(queryExecutor.searchPathQualified(name), ctx.VIEW() != null);
            }
            // The container scopes list every relation in the container.
            final ShowListingScope scope = listingScope(ctx, ShowListing.COLUMNS, forced);
            if (scope.isAccount()) {
                return showExecutor.showColumnsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showColumnsInDatabase(scope.name());
            }
            return scope.name() == null ? showExecutor.showColumnsScoped(null, false)
                : showExecutor.showColumnsInSchema(scope.name());
        } else if (ctx.MATERIALIZED() != null && ctx.VIEWS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.MATERIALIZED_VIEWS, forced);
            if (scope.isAccount()) {
                return showExecutor.showMaterializedViewsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showMaterializedViewsInDatabase(scope.name());
            }
            return showExecutor.showMaterializedViews(scope.name());
        } else if (ctx.VIEWS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.VIEWS, forced);
            if (scope.isAccount()) {
                return showExecutor.showViewsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showViewsInDatabase(scope.name());
            }
            return showExecutor.showViews(scope.name());
        } else if (ctx.USERS() != null) {
            return showExecutor.showUsers();
        } else if (ctx.ROLES() != null) {
            if (ctx.DATABASE() != null) {
                // A database's own roles, of which none is modelled; the database is still looked up.
                final Database scope = catalog.databaseExact(ctx.objectName() != null ? scopeName(ctx)
                    : catalog.getCurrentDatabase());
                if (ctx.TERSE() != null) {
                    throw new RuntimeException("Unsupported feature 'SHOW TERSE DATABASE ROLES'.");
                }
                return showExecutor.showDatabaseRoles(scope);
            }
            return showExecutor.showRoles();
        } else if (ctx.FUNCTIONS() != null) {
            if (ctx.CLASS() != null || ctx.APPLICATION() != null) {
                throw showScopeDoesNotExist(ctx);
            }
            if (ctx.BUILTIN() != null) {
                // SHOW BUILTIN FUNCTIONS never lists user functions and ignores a schema, database or
                // account scope — live-verified: IN SCHEMA / IN DATABASE / IN ACCOUNT all return the same
                // 1134 rows the bare form does, and the one UDF in the current schema is in none of them.
                // An APPLICATION or CLASS scope is still looked up, above.
                return showExecutor.showBuiltinFunctions();
            }
            final ShowListingScope scope = listingScope(ctx, ShowListing.FUNCTIONS, forced);
            if (scope.isAccount()) {
                return showExecutor.showFunctionsInAccount(ctx.USER() != null);
            }
            if (scope.isDatabase()) {
                return ctx.USER() != null
                    ? showExecutor.showUserFunctionsInDatabase(scope.name())
                    : showExecutor.showFunctionsInDatabase(scope.name());
            }
            return showExecutor.showFunctions(scope.name(), ctx.USER() != null);
        } else if (ctx.PROCEDURES() != null) {
            if (ctx.APPLICATION() != null || ctx.CLASS() != null) {
                throw showScopeDoesNotExist(ctx);
            }
            if (ctx.BUILTIN() != null) {
                // SHOW BUILTIN PROCEDURES never lists user procedures and ignores a schema or database
                // scope, exactly as SHOW BUILTIN FUNCTIONS does — live-verified: with one user procedure in
                // the current schema it returns 32 rows to SHOW PROCEDURES' 33, and IN SCHEMA /
                // IN DATABASE return that same 32 without the user procedure.
                return showExecutor.showBuiltinProcedures();
            }
            final ShowListingScope scope = listingScope(ctx, ShowListing.PROCEDURES, forced);
            if (scope.isAccount()) {
                return showExecutor.showProceduresInAccount(ctx.USER() != null);
            }
            if (scope.isDatabase()) {
                return ctx.USER() != null
                    ? showExecutor.showUserProceduresInDatabase(scope.name())
                    : showExecutor.showProceduresInDatabase(scope.name());
            }
            return showExecutor.showProcedures(scope.name(), ctx.USER() != null);
        } else if (ctx.STREAMS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.STREAMS, forced);
            if (scope.isAccount()) {
                return showExecutor.showStreamsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showStreamsInDatabase(scope.name());
            }
            return showExecutor.showStreams(scope.name());
        } else if (ctx.ALERTS() != null) {
            return showAlerts(ctx, forced);
        } else if (ctx.TASKS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.TASKS, forced);
            if (scope.isAccount()) {
                return showExecutor.showTasksInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showTasksInDatabase(scope.name());
            }
            return showExecutor.showTasks(scope.name());
        } else if (ctx.PIPES() != null) {
            String like = null;
            if (ctx.LIKE() != null && ctx.stringText() != null) {
                like = ParseTreeText.stringBody(ctx.stringText());
            }
            final ShowListingScope scope = listingScope(ctx, ShowListing.PIPES, forced);
            if (scope.isAccount()) {
                return showExecutor.showPipesInAccount(like);
            }
            if (scope.isDatabase()) {
                return showExecutor.showPipesInDatabase(scope.name(), like);
            }
            return showExecutor.showPipes(scope.name(), like);
        } else if (ctx.SEQUENCES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.SEQUENCES, forced);
            if (scope.isAccount()) {
                return showExecutor.showSequencesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showSequencesInDatabase(scope.name());
            }
            return showExecutor.showSequences(scope.name());
        } else if (ctx.CORTEX() != null && ctx.SERVICES() != null) {
            String cortexLike = null;
            if (ctx.LIKE() != null && ctx.stringText() != null) {
                cortexLike = ParseTreeText.stringBody(ctx.stringText());
            }
            final ShowListingScope scope = listingScope(ctx, ShowListing.CORTEX_SEARCH_SERVICES, forced);
            if (scope.isAccount()) {
                return showExecutor.showCortexSearchServicesInAccount(cortexLike);
            }
            if (scope.isDatabase()) {
                return showExecutor.showCortexSearchServicesInDatabase(scope.name(), cortexLike);
            }
            return showExecutor.showCortexSearchServices(scope.name(), cortexLike);
        } else if (ctx.COMPUTE() != null && ctx.POOLS() != null) {
            return showExecutor.showComputePools();
        } else if (ctx.COMPUTE() != null && ctx.POOL() != null && ctx.FAMILIES() != null) {
            return showExecutor.showComputePoolInstanceFamilies();
        } else if (ctx.WAREHOUSES() != null) {
            return showExecutor.showWarehouses();
        } else if (ctx.STAGES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.STAGES, forced);
            if (scope.isAccount()) {
                return showExecutor.showStagesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showStagesInDatabase(scope.name());
            }
            return showExecutor.showStages(scope.name());
        } else if (ctx.FILE() != null && ctx.FORMATS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.FILE_FORMATS, forced);
            if (scope.isAccount()) {
                return showExecutor.showFileFormatsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showFileFormatsInDatabase(scope.name());
            }
            return showExecutor.showFileFormats(scope.name());
        } else if (ctx.JOIN() != null && ctx.POLICIES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.JOIN_POLICIES, forced);
            if (scope.isAccount()) {
                return showExecutor.showJoinPoliciesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showJoinPoliciesInDatabase(scope.name());
            }
            return showExecutor.showJoinPolicies(scope.name());
        } else if (ctx.AGGREGATION() != null && ctx.POLICIES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.AGGREGATION_POLICIES, forced);
            if (scope.isAccount()) {
                return showExecutor.showAggregationPoliciesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showAggregationPoliciesInDatabase(scope.name());
            }
            return showExecutor.showAggregationPolicies(scope.name());
        } else if (ctx.PROJECTION() != null && ctx.POLICIES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.PROJECTION_POLICIES, forced);
            if (scope.isAccount()) {
                return showExecutor.showProjectionPoliciesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showProjectionPoliciesInDatabase(scope.name());
            }
            return showExecutor.showProjectionPolicies(scope.name());
        } else if (ctx.CONTACTS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.CONTACTS, forced);
            if (scope.isAccount()) {
                return showExecutor.showContactsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showContactsInDatabase(scope.name());
            }
            return showExecutor.showContacts(scope.name());
        } else if (ctx.MASKING() != null && ctx.POLICIES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.MASKING_POLICIES, forced);
            if (scope.isAccount()) {
                return showExecutor.showMaskingPoliciesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showMaskingPoliciesInDatabase(scope.name());
            }
            return showExecutor.showMaskingPolicies(scope.name());
        } else if (ctx.ROW() != null && ctx.POLICIES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.ROW_ACCESS_POLICIES, forced);
            if (scope.isAccount()) {
                return showExecutor.showRowAccessPoliciesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showRowAccessPoliciesInDatabase(scope.name());
            }
            return showExecutor.showRowAccessPolicies(scope.name());
        } else if (ctx.KEYS() != null) {
            String scopeKind;
            final String scopeName;
            if (ctx.IN() == null || ctx.ACCOUNT() != null
                    || (ctx.DATABASE() != null && ctx.identifier() == null)
                    || (ctx.SCHEMA() != null && ctx.objectName() == null)) {
                // No scope, or a container scope with no name: the shared resolution, the unscoped listing
                // reading the current schema's tables.
                final ShowListingScope scope = listingScope(ctx, ShowListing.KEYS, forced);
                scopeKind = scope.isAccount() ? "ACCOUNT" : scope.isDatabase() ? "DATABASE"
                    : ctx.IN() == null ? "TABLE" : "SCHEMA";
                scopeName = scope.isDatabase() ? scope.name() : null;
            } else if (ctx.DATABASE() != null) {
                if (!ctx.keysDatabasePath.isEmpty()) {
                    // A database is one part: a path asks for a full search path, naming its last part.
                    final FrostlakeParser.NamePartContext last =
                        ctx.keysDatabasePath.get(ctx.keysDatabasePath.size() - 1);
                    throw new RuntimeException(SqlCompilationError.of(
                        "Must specify the full search path starting from database for "
                            + SqlIdentifiers.canonicalTextParts(last.getText())[0]));
                }
                scopeKind = "DATABASE";
                scopeName = getText(ctx.identifier());
            } else if (ctx.SCHEMA() != null) {
                scopeKind = "SCHEMA";
                scopeName = scopeName(ctx);
            } else {
                // IN TABLE name?, a bare qualified table name, or IN VIEW alone, which lists as IN TABLE does.
                scopeKind = "TABLE";
                scopeName = ctx.objectName() != null && ShowScopeKindRefusal.scopeKind(ctx) != ShowScopeKind.VIEW
                    ? scopeName(ctx) : null;
            }
            if (ShowKeysScope.listsNothing(ctx, scopeKind, scopeName != null, catalog, queryExecutor)) {
                scopeKind = "NONE";
            }
            if (ctx.IMPORTED() != null) {
                return showExecutor.showImportedKeys(scopeKind, scopeName);
            }
            if (ctx.EXPORTED() != null) {
                return showExecutor.showExportedKeys(scopeKind, scopeName);
            }
            return showExecutor.showKeysScoped(ctx.PRIMARY() != null, scopeKind, scopeName);
        } else if (ctx.TAGS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.TAGS, forced);
            if (scope.isAccount()) {
                return showExecutor.showTagsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showTagsInDatabase(scope.name());
            }
            return showExecutor.showTags(scope.name());
        } else if (ctx.GRANTS() != null) {
            return handleShowGrants(ctx);
        } else if (ctx.PARAMETERS() != null) {
            return showParameters(ctx);
        } else if (ctx.OBJECTS() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.OBJECTS, forced);
            if (scope.isAccount()) {
                return showExecutor.showObjectsInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showObjectsInDatabase(scope.name());
            }
            return showExecutor.showObjects(scope.name());
        } else if (ctx.ORGANIZATION() != null && ctx.ACCOUNTS() != null) {
            return showExecutor.showOrganizationAccounts();
        } else if (ctx.ACCOUNTS() != null) {
            if (ctx.showAccountsScope() != null) {
                // The listing takes no scope: an IN with nothing after it is unfinished, any other is refused whole.
                if (ctx.showAccountsScope().getChildCount() == 1) {
                    throw SyntaxAfter.unexpected(ctx.showAccountsScope());
                }
                throw new RuntimeException(SqlCompilationError.of(
                    "Unsupported statement type. correct syntax: 'SHOW ACCOUNTS'."));
            }
            return showExecutor.showAccounts(ctx.HISTORY() != null, catalog.getAccountDirectory().accounts(),
                catalog.getAccountDirectory().managedAccounts().size());
        } else if (ctx.LOCKS() != null) {
            // SHOW LOCKS [IN ACCOUNT] — the account scope prepends a session column (live shape).
            return showExecutor.showLocks(ctx.ACCOUNT() != null);
        } else if (ctx.TRANSACTIONS() != null) {
            return showExecutor.showTransactions(null);
        } else if (ctx.VARIABLES() != null) {
            // SHOW VARIABLES [LIKE '<pattern>'] — the pattern matches the variable's name, and matches it
            // case-insensitively as every other listing's does (live-verified). There is no IN scope.
            return showExecutor.showVariables(getLikePattern(ctx));
        }

        throw new RuntimeException("Unsupported SHOW statement");
    }

    /**
     * SHOW PARAMETERS [LIKE '…'] [IN | FOR scope], with the scopes the account reads (live-verified):
     *
     * <pre>
     *   IN SESSION, IN ACCOUNT, IN USER [name]              the session's, the account's, a user's
     *   IN DATABASE | SCHEMA | WAREHOUSE [name]             the named one, or the session's current one
     *   IN TABLE name, IN STREAM | TASK name                a relation's, a stream's, a task's
     *   IN STREAM, IN TASK                                  This session does not have a current stream | task
     *   IN ACCOUNT abc, IN DATABASE a.b, IN SCHEMA a.b.c    Object does not exist, or operation cannot be performed.
     *   IN abc, IN a.b, IN ROLE [x], IN VIEW [x]            Unsupported statement type 'Cannot show parameters of
     *                                                       SqlIdentifier{qualifierNames=[A], identifierName=B}'.
     * </pre>
     */
    private ResultSet showParameters(final FrostlakeParser.ShowStatementContext ctx) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        final String like = getLikePattern(ctx);
        final FrostlakeParser.ShowParametersScopeContext scope = ctx.showParametersScope();
        if (scope == null) {
            if (ctx.IN() != null || ctx.FOR() != null) {
                throw SyntaxAfter.unexpected(ctx.IN() != null ? ctx.IN() : ctx.FOR());
            }
            return showExecutor.showParameters(like, false);
        }
        if (scope.SESSION() != null) {
            return showExecutor.showParameters(like, false);
        }
        final String[] named = scope.qualifiedName() == null ? new String[0]
            : qualifiedNameParts(scope.qualifiedName());
        if (scope.ACCOUNT() != null) {
            if (named.length > 0) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            return showExecutor.showParameters(like, true);
        }
        if (scope.USER() != null) {
            if (scope.identifier() != null) {
                return showExecutor.showParametersInUser(SqlIdentifiers.canonical(scope.identifier()), like);
            }
            // IN USER with no name lists the session's own user, the one CURRENT_USER() names (live-verified).
            final String user = catalog.currentUserForDdl();
            return showExecutor.showParametersInUser(user != null ? user : "", like);
        }
        if (scope.WAREHOUSE() != null) {
            if (named.length > 1) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            final String warehouse = named.length == 1 ? named[0] : catalog.getCurrentWarehouse();
            return warehouse == null ? showExecutor.showParameters(like, false)
                : showExecutor.showParametersInWarehouse(warehouse, like);
        }
        if (scope.DATABASE() != null) {
            if (named.length > 1) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            final String database = named.length == 1 ? named[0] : catalog.getCurrentDatabase();
            if (database == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return showExecutor.showParametersInDatabase(database, like);
        }
        if (scope.SCHEMA() != null) {
            final String[] parts = catalog.withoutAccount(named, 2);
            if (parts.length > 2) {
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            final String database = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
            final String schema = parts.length == 0 ? catalog.getCurrentSchema() : parts[parts.length - 1];
            if (database == null || schema == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return showExecutor.showParametersInSchema(database, schema, like);
        }
        if (scope.TASK() != null) {
            if (scope.objectName() == null) {
                throw new RuntimeException(SqlCompilationError.of("This session does not have a current task"));
            }
            return showExecutor.showParametersInTask(schemaObject(scope.objectName()), like);
        }
        if (scope.ALERT() != null) {
            if (scope.objectName() == null) {
                throw new RuntimeException(SqlCompilationError.of("This session does not have a current alert"));
            }
            return showExecutor.showParametersInAlert(schemaObject(scope.objectName()), like);
        }
        if (scope.TABLE() != null || scope.STREAM() != null) {
            if (scope.objectName() == null) {
                throw new RuntimeException(SqlCompilationError.of("This session does not have a current stream"));
            }
            return showExecutor.showParametersInTable(schemaObject(scope.objectName()), like);
        }
        // ROLE and VIEW are no scope and neither is a bare name: each is refused naming the word written.
        final String[] refused = scope.ROLE() != null ? new String[] {"ROLE"}
            : scope.VIEW() != null ? new String[] {"VIEW"} : named;
        final StringBuilder qualifiers = new StringBuilder();
        for (int i = refused.length - 2; i >= 0; i--) {
            qualifiers.append(qualifiers.length() > 0 ? ", " : "").append(refused[i]);
        }
        throw new RuntimeException(SqlCompilationError.of("Unsupported statement type 'Cannot show parameters of"
            + " SqlIdentifier{qualifierNames=[" + qualifiers + "], identifierName=" + refused[refused.length - 1]
            + "}'."));
    }

    /** The canonical parts of the scope a listing names, an IDENTIFIER() reference resolved. */
    private String[] scopeParts(final FrostlakeParser.ShowStatementContext ctx) {
        return queryExecutor.resolveObjectNameParts(ctx.objectName());
    }

    /** The canonical name of the scope a listing names, an IDENTIFIER() reference resolved. */
    /**
     * SHOW ALERTS: IN ACCOUNT, or no scope and no current database, lists every alert; IN DATABASE, or no scope
     * with a database in use, the database's; IN SCHEMA, or a bare schema name, the schema's.
     */
    private ResultSet showAlerts(final FrostlakeParser.ShowStatementContext ctx, final ShowListingScope forced) {
        final AlertCommandHandler alerts = new AlertCommandHandler(queryExecutor);
        final ShowListingScope scope = listingScope(ctx, ShowListing.ALERTS, forced);
        if (scope.isAccount()) {
            return alerts.show(null, null);
        }
        if (scope.name() != null) {
            // The schema or the database the scope misses is named (live-verified).
            scope.schemas(catalog, false);
        }
        if (scope.isDatabase()) {
            if (ctx.objectName() == null) {
                return alerts.show(scope.name(), null);
            }
            final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
            return alerts.show(parts[parts.length - 1], null);
        }
        if (scope.name() == null) {
            return alerts.show(catalog.getCurrentDatabase(), catalog.getCurrentSchema());
        }
        final String[] parts = forced != null ? QualifiedName.parse(scope.name()).parts()
            : queryExecutor.resolveObjectNameParts(ctx.objectName());
        final String databaseName = parts.length >= 2 ? parts[parts.length - 2] : catalog.getCurrentDatabase();
        return alerts.show(databaseName, parts[parts.length - 1]);
    }

    private String scopeName(final FrostlakeParser.ShowStatementContext ctx) {
        return describedName(ctx.objectName());
    }

    /**
     * The object of a schema a statement names, bare, qualified or through {@code IDENTIFIER(...)}: where
     * DESCRIBE STREAM, TASK, PIPE and SEQUENCE and SHOW PARAMETERS IN TABLE and TASK look for it.
     */
    private SchemaObjectName schemaObject(final FrostlakeParser.ObjectNameContext written) {
        return SchemaObjectName.of(catalog, queryExecutor.resolveObjectNameParts(written));
    }

    /**
     * Refuses a relation keyword over a name that reaches a stream. A schema's relations and its streams share
     * one namespace, so the name reaches an object, of another kind: {@code Invalid object type: 'TABLE'}, the
     * keyword spelled with an underscore where it is two words (live-verified).
     */
    private void refuseRelationKeywordOverStream(final FrostlakeParser.DescribeStatementContext ctx) {
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        if (parts.length > 3 || parts.length < 3 && catalog.getCurrentDatabase() == null
                || parts.length < 2 && catalog.getCurrentSchema() == null) {
            return;
        }
        final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
        final String schemaName = parts.length >= 2 ? parts[parts.length - 2] : catalog.getCurrentSchema();
        final Database database = catalog.findDatabase(databaseName);
        if (database == null || !database.hasSchema(schemaName)) {
            return;
        }
        final Schema schema = database.getSchema(schemaName);
        final String name = parts[parts.length - 1];
        if (!schema.hasStreamExact(name) || schema.hasTable(name) || schema.hasView(name)
                || schema.hasMaterializedView(name) || schema.hasDynamicTable(name)) {
            return;
        }
        final String keyword = ctx.DYNAMIC() != null ? "DYNAMIC_TABLE"
            : ctx.MATERIALIZED() != null ? "MATERIALIZED_VIEW" : ctx.VIEW() != null ? "VIEW" : "TABLE";
        throw new RuntimeException(SqlCompilationError.of("Invalid object type: '" + keyword + "'"));
    }

    /**
     * The canonical name a described relation is written as, or the one its {@code IDENTIFIER(...)}
     * argument names. A string that is no identifier reference is refused at the argument, as live does.
     */
    private String describedName(final FrostlakeParser.ObjectNameContext name) {
        // A relation the current schema does not hold is looked for along the session's SEARCH_PATH.
        return queryExecutor.searchPathQualified(name.qualifiedName() != null ? getText(name.qualifiedName())
            : QualifiedName.join(queryExecutor.resolveObjectNameParts(name)));
    }

    /**
     * DESCRIBE DYNAMIC TABLE's columns, which come from projecting the dynamic table itself, its shape alone: no
     * row is read, so a table that never refreshed is described too (live-verified). The projection is the
     * DESCRIBE's own work, so it takes no query ID and no history row.
     */
    private ResultSet describeDynamicTable(final ShowCommandExecutor showExecutor, final String dtName) {
        return showExecutor.describeDynamicTable(dtName, new ResultSetProvider() {
            @Override
            public ResultSet getResultSet() {
                final boolean previous = RelationShapeOnly.begin();
                try {
                    final List<ResultSet> results =
                        queryExecutor.executeUnrecorded("SELECT * FROM "
                            + SqlIdentifiers.spellAlreadyCanonicalPathEscaped(dtName) + " LIMIT 0");
                    return results.get(results.size() - 1);
                } finally {
                    RelationShapeOnly.end(previous);
                }
            }
        });
    }

    /**
     * A schema of the SNOWFLAKE database is not described (live-verified): the share most of them come from lacks
     * the privilege, and one of the SNOWFLAKE application's asks the session's role for MONITOR. Its
     * INFORMATION_SCHEMA is described as any database's.
     */
    private void refuseSystemSchema(final Schema schema) {
        final SystemSchema system = SystemSchema.of(schema.getDatabaseName(), schema.getName());
        if (system == null) {
            return;
        }
        if (system.owner() == null) {
            throw new PrivilegeRefusal(SqlAccessControlError.insufficientPrivileges("schema", schema.getName())
                + " Provider share does not have sufficient privileges.");
        }
        if (queryExecutor.getSecurityManager() == null) {
            return;
        }
        final SessionContext session = queryExecutor.getSecurityManager().getSessionContext();
        throw new PrivilegeRefusal(SqlAccessControlError.privilegeRequired("SCHEMA", schema.getName(),
            session.getCurrentRole(), session.isSecondaryRolesInUse(), Privilege.MONITOR.name(),
            QualifiedName.join(schema.getDatabaseName(), schema.getName())));
    }

    /** The name parts DESCRIBE SCHEMA or DATABASE names, refused as live refuses a name with too many parts. */
    private String[] describedContainerParts(final FrostlakeParser.ObjectNameContext name, final int maxParts) {
        final String[] parts = queryExecutor.resolveObjectNameParts(name);
        if (parts.length > maxParts) {
            throw new RuntimeException(SqlCompilationError.of("Object does not exist, or operation cannot be performed."));
        }
        return parts;
    }

    /**
     * DESCRIBE ICEBERG TABLE over a table that is no Iceberg table: refused in the account's own sentence, before
     * anything is described; a name that reaches no table keeps its own refusal (live-verified).
     */
    private void refuseTableThatIsNoIcebergTable(final FrostlakeParser.ObjectNameContext name) {
        final String[] parts = queryExecutor.resolveObjectNameParts(name);
        final Table table;
        try {
            table = catalog.requireOwningSchema(QualifiedName.of(parts)).getTable(parts[parts.length - 1]);
        } catch (final RuntimeException missing) {
            return;
        }
        if (table != null && table.getIcebergMetadata() == null) {
            throw new RuntimeException("SQL Compilation error: The object "
                + SqlIdentifiers.spellCanonical(parts[parts.length - 1])
                + " is not an Iceberg table. Only Iceberg tables can use DESCRIBE ICEBERG TABLE command.");
        }
    }

    /**
     * DESCRIBE of a kind Frostlake models no object of. A resource monitor and an organization are features the
     * account does not support, whatever is named; every other kind misses its object, the name resolved as a
     * DROP of the kind resolves it — a share's qualifier being its account (live-verified).
     *
     * @param kind the kind described
     * @param parts the canonical parts of the name written after it
     * @return the refusal
     */
    private RuntimeException describeUnmodelled(final UnmodelledDropKind kind, final String[] parts) {
        if (kind == UnmodelledDropKind.RESOURCE_MONITOR) {
            return new RuntimeException("Unsupported feature 'TOK_RESOURCE_MONITOR'.");
        }
        if (kind == UnmodelledDropKind.ORGANIZATION) {
            return new RuntimeException("Unsupported feature 'ORGANIZATION'.");
        }
        final String name = parts[parts.length - 1];
        if (kind == UnmodelledDropKind.SHARE && parts.length <= 2) {
            return new RuntimeException(SqlCompilationError.doesNotExistAsSpelled(kind.noun(),
                DropCommandHandler.shareName(parts.length == 2 ? parts[0] : catalog.getAccountLocator(), name)));
        }
        if (kind.isSchemaObject()) {
            final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
            return new RuntimeException(SqlCompilationError.doesNotExist(kind.noun(), owner.qualifiedName(name)));
        }
        return new RuntimeException(parts.length > 1 ? SqlCompilationError.objectDoesNotExist()
            : SqlCompilationError.doesNotExist(kind.noun(), name));
    }

    public ResultSet handleDescribeStatement(final FrostlakeParser.DescribeStatementContext ctx) {
        if (ctx.INTEGRATION() != null) {
            return new IntegrationCommandHandler(catalog, queryExecutor).describe(ctx.integrationKind(), ctx.identifier());
        }
        if (ctx.VOLUME() != null) {
            DescribeProperties.describesStage(ctx, queryExecutor);
            return new ExternalVolumeCommandHandler(catalog).describe(ctx.identifier());
        }
        if (ctx.unmodelledKind() != null) {
            final UnmodelledDropKind unmodelled = UnmodelledDropKind.of(ctx.unmodelledKind());
            if (unmodelled != UnmodelledDropKind.RESOURCE_MONITOR && unmodelled != UnmodelledDropKind.ORGANIZATION) {
                // A kind that misses its object judges the properties first, as every modelled kind does.
                DescribeProperties.describesStage(ctx, queryExecutor);
            }
            final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
            final SecurityObjectKind modelled = SecurityObjectCommandHandler.modelledKind(unmodelled);
            if (modelled != null) {
                // The kinds read here that are modelled: a session, an authentication or a packages policy that is
                // there is described.
                final ResultSet described = new SecurityObjectCommandHandler(catalog, queryExecutor)
                    .describeModelled(modelled, parts);
                if (described != null) {
                    return described;
                }
                if (catalog.requireOwningSchema(QualifiedName.of(parts)).policyKindHolding(parts[parts.length - 1])
                        != null) {
                    // A policy of another kind holds the name (live-verified).
                    throw new RuntimeException(SqlCompilationError.of("Invalid object type: '" + modelled.name()
                        + "'"));
                }
            }
            throw describeUnmodelled(unmodelled, parts);
        }
        if (ctx.IMAGE() != null && ctx.REPOSITORY() != null) {
            // The account describes no image repository, whatever one is named; a property's name left without
            // its value is the statement's syntax error first (live-verified).
            DescribeProperties.requireValues(ctx);
            throw new RuntimeException("Unsupported feature 'TOK_IMAGE_REPOSITORY'.");
        }
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.NOTEBOOK() != null || ctx.STREAMLIT() != null) {
            DescribeProperties.describesStage(ctx, queryExecutor);
            return new AppObjectListing(catalog).describe(ctx.NOTEBOOK() != null ? AppObjectKind.NOTEBOOK
                : AppObjectKind.STREAMLIT, ctx.qualifiedName());
        }
        if (ctx.IDENTIFIER() != null) {
            if (UnmodelledKindWords.readsDescribeTail(ctx.IDENTIFIER())) {
                // A kind the account knows judges the properties first, as every modelled kind does, and then
                // misses its object.
                DescribeProperties.describesStage(ctx, queryExecutor);
                throw describeUnmodelled(UnmodelledDropKind.valueOf(ctx.IDENTIFIER().getText()
                    .toUpperCase(Locale.ROOT)), ParseTreeText.qualifiedNameParts(ctx.qualifiedName()));
            }
            DescribeProperties.refuseAfterBareName(ctx.describeTypeProperty());
            throw new RuntimeException("Unsupported feature 'DESCRIBE "
                + ctx.IDENTIFIER().getText().toUpperCase(Locale.ROOT) + "'.");
        }
        if (ctx.TAG() != null) {
            // The account describes no tag: the statement is refused whether or not the tag exists, before its
            // name is looked up and whatever property follows it; SHOW TAGS is how one is read back
            // (live-verified).
            DescribeProperties.requireValues(ctx);
            throw new RuntimeException(DescribeTagRefusal.SENTENCE);
        }
        RoutineSignatureForm.requireTypesOnly(ctx.dataTypeList());
        final boolean stageProperties = DescribeProperties.describesStage(ctx, queryExecutor);
        if (ctx.ACCOUNT() != null) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        if (ctx.SCHEMA() != null) {
            final String[] parts = describedContainerParts(ctx.objectName(), 2);
            refuseSystemSchema(catalog.resolveSchema(QualifiedName.of(parts)));
            queryExecutor.requirePrivilege(Privilege.MONITOR, "SCHEMA", parts, null);
            return showExecutor.describeSchema(QualifiedName.join(parts));
        }
        if (ctx.DATABASE() != null) {
            final String databaseName = describedContainerParts(ctx.objectName(), 1)[0];
            catalog.databaseExact(databaseName);
            return showExecutor.describeDatabase(databaseName);
        }
        if ((ctx.TABLE() != null || ctx.VIEW() != null) && ctx.EVENT() == null && ctx.ICEBERG() == null
                && ctx.objectName() != null) {
            refuseRelationKeywordOverStream(ctx);
        }
        if (ctx.ICEBERG() != null && ctx.objectName() != null) {
            refuseTableThatIsNoIcebergTable(ctx.objectName());
            final String described = describedName(ctx.objectName());
            if (!showExecutor.namesAStoredRelation(described) && !showExecutor.namesDynamicTable(described)) {
                // A name that reaches nothing is refused as an Iceberg table by its full path, after its schema
                // (live-verified).
                final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
                final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
                throw new RuntimeException(SqlCompilationError.doesNotExist("Iceberg table",
                    owner.qualifiedName(parts[parts.length - 1])));
            }
        }
        if (stageProperties && (ctx.DYNAMIC() != null || ctx.TABLE() != null || ctx.VIEW() != null)) {
            // The stage properties are the relation's, whatever kind the name reaches: a dynamic table
            // answers a table's, and the keyword is spoken only when the name reaches nothing.
            final String described = describedName(ctx.objectName());
            final String describedKind = ctx.MATERIALIZED() != null ? "Materialized view"
                : ctx.VIEW() != null ? "View" : "Table";
            if (showExecutor.namesAStoredRelation(described)) {
                // A table reports the stage options it was created with; a view has none of its own.
                return showExecutor.namesTable(described, describedKind)
                    ? showExecutor.describeTableStage(described)
                    : showExecutor.describeRelationStage(described, describedKind);
            }
            if (showExecutor.namesDynamicTable(described)) {
                return showExecutor.describeStoredRelationStage();
            }
            if (ctx.DYNAMIC() != null) {
                // Nothing carries the name: the dynamic table is looked up, and refused, as its columns are.
                describeDynamicTable(showExecutor, described);
            }
            return showExecutor.describeRelationStage(described, describedKind);
        }
        if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
            // Every relation keyword describes whatever relation the name reaches: the keyword is spoken
            // only when it reaches none (live-verified).
            final String described = describedName(ctx.objectName());
            return showExecutor.namesAStoredRelation(described)
                ? showExecutor.describeRelation(described, "Table")
                : describeDynamicTable(showExecutor, described);
        }
        if ((ctx.TABLE() != null || ctx.VIEW() != null)
                && showExecutor.namesDynamicTable(describedName(ctx.objectName()))
                && !showExecutor.namesAStoredRelation(describedName(ctx.objectName()))) {
            return describeDynamicTable(showExecutor, describedName(ctx.objectName()));
        }
        if (ctx.TABLE() != null || ctx.VIEW() != null) {
            // DESCRIBE has its own column shape (name|type|kind|null?|default|primary key|…) —
            // NOT the SHOW COLUMNS shape, which leads with table_name/schema_name. The three relation
            // kinds describe each other freely; the one they named is spoken only when the object is
            // missing ("Materialized view 'X' does not exist or not authorized." — live-verified).
            final String describedKind = ctx.MATERIALIZED() != null ? "Materialized view"
                : ctx.VIEW() != null ? "View" : "Table";
            if (ctx.EVENT() != null && !showExecutor.namesAStoredRelation(describedName(ctx.objectName()))) {
                // A missing event table is named as one, by its full path.
                throw new RuntimeException(SqlCompilationError.doesNotExist("Event table",
                    catalog.qualifiedObjectName(describedName(ctx.objectName()))));
            }
            if (ctx.EXTERNAL() != null && ctx.objectName() != null
                    && !showExecutor.namesAStoredRelation(describedName(ctx.objectName()))) {
                // DESCRIBE EXTERNAL TABLE describes whatever relation the name reaches; one that reaches none
                // is refused as an external table by its full path, after its schema (live-verified).
                final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
                final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
                throw new RuntimeException(SqlCompilationError.doesNotExist("External table",
                    owner.qualifiedName(parts[parts.length - 1])));
            }
            return showExecutor.describeRelation(describedName(ctx.objectName()), describedKind);
        } else if (ctx.PIPE() != null) {
            return showExecutor.describePipe(schemaObject(ctx.objectName()));
        } else if (ctx.SEQUENCE() != null) {
            return showExecutor.describeSequence(schemaObject(ctx.objectName()));
        } else if (ctx.TASK() != null) {
            return showExecutor.describeTask(schemaObject(ctx.objectName()));
        } else if (ctx.ALERT() != null) {
            return new AlertCommandHandler(queryExecutor).describe(ctx.qualifiedName());
        } else if (ctx.STREAM() != null) {
            return showExecutor.describeStream(schemaObject(ctx.objectName()));
        } else if (ctx.CORTEX() != null) {
            return showExecutor.describeCortexSearchService(ParseTreeText.qualifiedNameParts(ctx.qualifiedName()));
        } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
            return showExecutor.describeComputePool(getText(ctx.identifier()));
        } else if (ctx.WAREHOUSE() != null) {
            final String warehouseName = getText(ctx.identifier());
            return showExecutor.describeWarehouse(warehouseName);
        } else if (ctx.STAGE() != null) {
            final String stageName = getText(ctx.qualifiedName());
            return showExecutor.describeStage(stageName);
        } else if (ctx.FUNCTION() != null) {
            requireRoutineArgumentTypes(ctx, getText(ctx.qualifiedName()));
            return showExecutor.describeFunction(qualifiedNameParts(ctx.qualifiedName()), writtenSignature(ctx));
        } else if (ctx.PROCEDURE() != null) {
            requireRoutineArgumentTypes(ctx, getText(ctx.qualifiedName()));
            return showExecutor.describeProcedure(qualifiedNameParts(ctx.qualifiedName()), writtenSignature(ctx));
        } else if (ctx.USER() != null) {
            return showExecutor.describeUser(getText(ctx.identifier()));
        } else if (ctx.SEARCH() != null && ctx.OPTIMIZATION() != null) {
            return showExecutor.describeSearchOptimization(getText(ctx.qualifiedName()));
        } else if (ctx.JOIN() != null) {
            return showExecutor.describeJoinPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.AGGREGATION() != null) {
            return showExecutor.describeAggregationPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.PROJECTION() != null) {
            return showExecutor.describeProjectionPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.MASKING() != null) {
            return showExecutor.describeMaskingPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.ROW() != null) {
            return showExecutor.describeRowAccessPolicy(getText(ctx.qualifiedName()));
        } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
            return showExecutor.describeFileFormat(getText(ctx.qualifiedName()));
        } else if (ctx.RESULT() != null) {
            return queryExecutor.describeResult(ctx);
        }

        if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
            final String dtName = describedName(ctx.objectName());
            // The column list comes from projecting the dynamic table itself, zero rows needed, as the DESCRIBE's own
            // work: no query ID and no history row.
            return showExecutor.describeDynamicTable(dtName, new ResultSetProvider() {
                @Override
                public ResultSet getResultSet() {
                    final List<ResultSet> results =
                        queryExecutor.executeUnrecorded("SELECT * FROM "
                            + SqlIdentifiers.spellAlreadyCanonicalPathEscaped(dtName) + " LIMIT 0");
                    return results.get(results.size() - 1);
                }
            });
        }
        // Default: assume it's a table
        final String name = ctx.qualifiedName() != null ? getText(ctx.qualifiedName()) : getText(ctx.identifier());
        return showExecutor.showColumns(name);
    }

    /** Extract LIKE pattern from context — returns null if none specified. */
    private String getLikePattern(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.LIKE() == null || ctx.stringText() == null) return null;
        return ParseTreeText.stringBody(ctx.stringText());
    }

    /** The index of the name-ish column SHOW filters (LIKE / STARTS WITH / LIMIT FROM) apply to, or -1. */
    private static int nameColumnIndex(final ResultSet rs) {
        for (final String col : new String[]{"name", "parameter_name", "key", "account_name", "column_name",
            "snowflake_region"}) {
            try {
                return rs.getColumnIndex(col);
            } catch (final RuntimeException ignored) {
            }
        }
        return -1;
    }

    /**
     * Order an object listing the way a real account returns it: by database, then schema, then
     * name, each byte-wise. Single-scope listings reduce to the name ordering; the wider
     * {@code IN DATABASE} / {@code IN ACCOUNT} scopes group by database and schema first
     * (live-verified).
     *
     * <p>Byte-wise and not case-insensitively: live, a schema holding DT_A, T_A…T_D and a quoted
     * "t_lower" lists the lowercase name last, which is {@link String#compareTo}'s order and not
     * {@code CASE_INSENSITIVE_ORDER}'s. Nulls sort first so a listing with an unnamed row cannot throw.
     *
     * <p>This is also what makes {@code LIMIT}/{@code FROM} deterministic, so it runs before them.
     */
    private ResultSet sortByName(final ResultSet rs) {
        if (rs == null) return rs;
        final int nameIdx = columnIndexOrMissing(rs, "name");
        if (nameIdx < 0) return rs;
        final int databaseIdx = columnIndexOrMissing(rs, "database_name");
        final int schemaIdx = columnIndexOrMissing(rs, "schema_name");
        final List<Row> sorted = new ArrayList<>(rs.getRows());
        Collections.sort(sorted, new ShowNameComparator(databaseIdx, schemaIdx, nameIdx));
        return new ResultSet(rs.getColumns(), sorted);
    }

    /** SHOW ... STARTS WITH 'prefix': case-sensitive prefix filter on the name column (Snowflake semantics). */
    private ResultSet applyStartsWith(final ResultSet rs, final String prefix) {
        if (prefix == null || rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        if (nameIdx < 0) return rs;
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final Object val = row.getValue(nameIdx);
            if (val != null && val.toString().startsWith(prefix)) {
                filtered.add(row);
            }
        }
        return new ResultSet(rs.getColumns(), filtered);
    }

    /** SHOW ... LIMIT n [FROM 'name']: keep rows whose name sorts after 'name', then the first n. */
    private ResultSet applyLimitFrom(final ResultSet rs, final int limit, final String fromName,
                                     final ShowPageStart pageStart) {
        if (rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            if (fromName != null && nameIdx >= 0) {
                final Object val = row.getValue(nameIdx);
                if (!pageStart.keeps(val == null ? null : val.toString(), fromName, !kept.isEmpty())) {
                    continue;
                }
            }
            kept.add(row);
            if (kept.size() >= limit) {
                break;
            }
        }
        return new ResultSet(rs.getColumns(), kept);
    }

    /**
     * SHOW TERSE ...: project onto the column shape the profile prescribes for this listing.
     *
     * <p>The target list is a shape to produce, not a subset to keep. A column absent from the untrimmed
     * listing still appears — Snowflake's own TERSE output does that, and it is why the previous
     * keep-what-we-have projection came out wrong: {@code SHOW TERSE SCHEMAS} lost {@code kind} and
     * {@code schema_name} (3 columns instead of 5), {@code SHOW TERSE DATABASES} lost three of its five,
     * and {@code SHOW TERSE USERS} answered 2 columns where a real account answers 14.
     *
     * <p>An empty target list means TERSE is inert for this listing and the result is returned whole.
     */
    private ResultSet applyTerse(final ResultSet rs, final ShowModifierProfile profile) {
        if (rs == null || profile.terseColumns().isEmpty()) return rs;
        final List<Integer> sourceIndex = new ArrayList<>();
        final List<ResultSetColumn> cols = new ArrayList<>();
        for (final String col : profile.terseColumns()) {
            final int idx = columnIndexOrMissing(rs, col);
            sourceIndex.add(idx);
            cols.add(idx >= 0 ? rs.getColumns().get(idx) : new ResultSetColumn(col, StringType.VARCHAR));
        }
        final int materializedIdx = columnIndexOrMissing(rs, "is_materialized");
        final int tableNameIdx = columnIndexOrMissing(rs, "table_name");
        final List<Row> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < sourceIndex.size(); i++) {
                final int idx = sourceIndex.get(i);
                if (idx >= 0) {
                    values.add(row.getValue(idx));
                } else {
                    values.add(terseFallback(profile.terseColumns().get(i), profile, row,
                        materializedIdx, tableNameIdx));
                }
            }
            rows.add(new Row(values));
        }
        return new ResultSet(cols, rows);
    }

    /**
     * The value for a TERSE column the untrimmed listing does not carry.
     *
     * <p>Only three of them are ever anything but null. {@code kind} is the listing's own object kind —
     * the literal STANDARD for DATABASES and DELTA for STREAMS, or VIEW / MATERIALIZED_VIEW decided per
     * row from {@code is_materialized} for VIEWS, which is the one case where two kinds share a listing.
     * {@code tableOn} is the stream's base table under its bare name, which the untrimmed
     * SHOW STREAMS carries fully qualified as {@code table_name}.
     */
    private Object terseFallback(final String column, final ShowModifierProfile profile, final Row row,
                                 final int materializedIdx, final int tableNameIdx) {
        if ("kind".equals(column)) {
            if (profile.terseKindFromMaterializedFlag() && materializedIdx >= 0) {
                return isTruthy(row.getValue(materializedIdx)) ? "MATERIALIZED_VIEW" : "VIEW";
            }
            return profile.terseKind();
        }
        if ("tableOn".equals(column) && tableNameIdx >= 0) {
            final Object tableName = row.getValue(tableNameIdx);
            // The terse form names the base table alone, where the full listing qualifies it.
            return tableName == null ? null : QualifiedName.parse(tableName.toString()).last();
        }
        return null;
    }

    /** Whether a listing's yes/no cell reads as set, however the listing spells it. */
    private static boolean isTruthy(final Object value) {
        if (value == null) return false;
        final String text = value.toString();
        return "Y".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text);
    }

    /** {@code getColumnIndex} but answering -1 instead of throwing when the listing has no such column. */
    private static int columnIndexOrMissing(final ResultSet rs, final String column) {
        try {
            return rs.getColumnIndex(column);
        } catch (final RuntimeException absent) {
            return -1;
        }
    }

    /**
     * The SHOW TABLES listing of the scope a SHOW EVENT TABLES or SHOW ICEBERG TABLES names, which those listings
     * narrow to their own tables.
     */
    private ResultSet tablesInScope(final ShowCommandExecutor showExecutor,
                                    final FrostlakeParser.ShowStatementContext ctx, final ShowListingScope forced) {
        final ShowListingScope scope = listingScope(ctx, ShowListing.of(ctx), forced);
        if (scope.isAccount()) {
            return showExecutor.showTablesInAccount();
        }
        if (scope.isDatabase()) {
            return showExecutor.showTablesInDatabase(scope.name());
        }
        return showExecutor.showTables(scope.name());
    }

    /** The scope a schema-level listing resolves to in the session, its written name read once. */
    private ShowListingScope listingScope(final FrostlakeParser.ShowStatementContext ctx,
                                          final ShowListing listing, final ShowListingScope forced) {
        return forced != null ? forced
            : ShowListingScope.of(ctx, listing, catalog, ctx.objectName() != null ? scopeName(ctx) : null);
    }

    /**
     * The refusal an APPLICATION / APPLICATION PACKAGE / CLASS scope earns on an engine that has no
     * such objects — matching a real account, which parses these scopes and then refuses the named
     * object (live-verified per kind): the APPLICATION flavors answer
     * {@code Application [package] '<NAME>' does not exist or not authorized.} on its own line,
     * while CLASS answers the single-line {@code Object type or Class '<NAME>' …} family. The name
     * is spelled as WRITTEN, upper-folded when unquoted and kept verbatim — quotes included — when
     * quoted; an IDENTIFIER() reference names it as it resolves.
     */
    private RuntimeException showScopeDoesNotExist(final FrostlakeParser.ShowStatementContext ctx) {
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        if (ctx.CLASS() != null) {
            // The class resolves as any object's name: a path's database and schema are looked up first.
            return new RuntimeException(ShowScopeRefusal.missingClass(parts, catalog));
        }
        final FrostlakeParser.QualifiedNameContext written = ctx.objectName().qualifiedName();
        if (ctx.PACKAGE() == null && written != null && written.getStart() == written.getStop()
                && written.getStart().getType() == FrostlakeLexer.PACKAGE) {
            // APPLICATION PACKAGE with no name after it: the package's name is missing (live-verified).
            return new RuntimeException(SqlCompilationError.of(ShowScopeKindRefusal.lineAfter(ctx, written.getStop())));
        }
        if (parts.length > 1) {
            return new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        final String kind = ctx.PACKAGE() != null ? "Application package" : "Application";
        return new RuntimeException(SqlCompilationError.doesNotExistAsSpelled(kind,
            SqlIdentifiers.spellCanonical(parts[0])));
    }

    /** Filter a ResultSet by a LIKE pattern applied to the 'name' column (column index 1). */
    private ResultSet applyLikeFilter(final ResultSet rs, final String pattern) {
        if (pattern == null || rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        if (nameIdx < 0) return rs; // no filterable column — return unfiltered
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final Object val = row.getValue(nameIdx);
            if (val != null && ShowResultHelpers.matchesLike(val.toString(), pattern)) {
                filtered.add(row);
            }
        }
        return new ResultSet(rs.getColumns(), filtered);
    }

    private ResultSet handleShowGrants(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.showGrantsIn() != null) {
            // SHOW GRANTS IN: whatever the parser let follow the IN is not what the account reads there.
            throw SyntaxAfter.unexpected(ctx.showGrantsIn());
        }
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.ON() != null) {
            // SHOW GRANTS ON [kind] name
            final FrostlakeParser.ShowGrantsTargetContext target = ctx.showGrantsTarget();
            if (target.ACCOUNT() != null) {
                // A name after ACCOUNT names another account, which is looked up and never found.
                throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
            }
            final String[] parts = grantsTargetParts(target);
            final String written = QualifiedName.join(parts);
            if (target.qualifiedName() != null && target.qualifiedName().getStart() == target.qualifiedName().getStop()
                    && target.qualifiedName().getStart().getType() == FrostlakeLexer.ACCOUNT) {
                // The word alone is the account itself, never a table of that name (live-verified).
                return showExecutor.showGrantsOnAccount();
            }
            final String objectType = target.securableKind() != null ? SecurableKinds.of(target.securableKind())
                : target.VOLUME() != null ? "EXTERNAL_VOLUME" : null;
            if (objectType == null) {
                // A bare name is a relation of any kind, and anything else is no object at all; a relation kind
                // other than TABLE and VIEW finds the relation of the name whatever its own kind (live-verified).
                final String kind = target.relationGrantsKind() == null ? null
                    : SecurableKinds.joined(target.relationGrantsKind());
                final GrantedObject relation = queryExecutor.shownRelation(parts, kind);
                return asEventTable(showExecutor.showGrantsOnObject(relation.getKind(), written, relation), parts,
                    relation);
            }
            if (("TABLE".equals(objectType) || "VIEW".equals(objectType)) && target.LPAREN() == null) {
                // TABLE and VIEW find a materialized view or a dynamic table too, listed under its own kind.
                final GrantedObject other = queryExecutor.materializedOrDynamic(parts);
                if (other != null) {
                    return showExecutor.showGrantsOnObject(other.getKind(), written, other);
                }
            }
            GrantRevokeHandler.requireArgumentTypes(objectType, parts, target.LPAREN() != null);
            queryExecutor.requireSecurable(objectType, parts);
            final Integer arity = target.LPAREN() == null ? null
                : Integer.valueOf(target.dataTypeList() == null ? 0 : target.dataTypeList().dataTypeName().size());
            // A routine is shown one overload at a time: the one the written argument types name.
            final List<String> writtenTypes = target.LPAREN() == null ? null : new ArrayList<String>();
            if (writtenTypes != null && target.dataTypeList() != null) {
                for (final FrostlakeParser.DataTypeNameContext type : target.dataTypeList().dataTypeName()) {
                    writtenTypes.add(type.getStart().getText());
                }
            }
            if (writtenTypes != null && ("FUNCTION".equals(objectType) || "PROCEDURE".equals(objectType))) {
                queryExecutor.routineGrantNames(objectType, parts, writtenTypes);
            }
            final GrantedObject granted = queryExecutor.grantedObject(objectType, parts, arity, writtenTypes);
            return asEventTable(showExecutor.showGrantsOnObject(objectType, written, granted), parts, granted);
        } else if (ctx.TO() != null) {
            // SHOW GRANTS TO USER/ROLE identifier
            final String targetType = ctx.USER() != null ? "USER" : "ROLE";
            final String targetName = PrincipalNames.of(ctx.principalName(), queryExecutor);
            return showExecutor.showGrantsTo(targetType, targetName);
        }
        if (ctx.OF() != null && ctx.ROLE() != null && ctx.principalName() != null) {
            // SHOW GRANTS OF ROLE identifier
            return showExecutor.showGrantsOfRole(PrincipalNames.of(ctx.principalName(), queryExecutor));
        }
        if (ctx.ON() == null && ctx.TO() == null && ctx.OF() == null) {
            // Bare SHOW GRANTS: what the current user holds.
            return showExecutor.showGrantsForCurrentUser();
        }
        throw new RuntimeException("Invalid SHOW GRANTS syntax");
    }

    /** The canonical parts of what SHOW GRANTS ON names, an IDENTIFIER() reference resolved. */
    private String[] grantsTargetParts(final FrostlakeParser.ShowGrantsTargetContext target) {
        if (target.grantsObjectName() != null && target.grantsObjectName().wholeObjectName() != null) {
            return queryExecutor.resolveWholeObjectNameParts(target.grantsObjectName().wholeObjectName());
        }
        if (target.identifierArgument() != null) {
            return queryExecutor.resolveIdentifierArgument(target.identifierArgument());
        }
        return ParseTreeText.qualifiedNameParts(target.qualifiedName());
    }

    /** An event table's grant rows, which live lists as EVENT_TABLE whichever word named it (live-verified). */
    private ResultSet asEventTable(final ResultSet listed, final String[] parts, final GrantedObject granted) {
        if (granted == null || !"TABLE".equals(granted.getKind()) || !queryExecutor.isEventTable(parts)) {
            return listed;
        }
        final List<Row> rows = new ArrayList<>();
        for (final Row row : listed.getRows()) {
            final List<Object> values = new ArrayList<>(row.getValues());
            values.set(2, "EVENT_TABLE");
            rows.add(new Row(values));
        }
        return new ResultSet(listed.getColumns(), rows);
    }

    /**
     * A routine name is not enough to describe it — routines overload, so Snowflake insists on the
     * argument-type list. Live-verified on a real account: {@code DESCRIBE PROCEDURE qr}
     * and {@code DESCRIBE FUNCTION dfn} both fail "Argument types of function '&lt;NAME&gt;' must be
     * specified.", while {@code DESCRIBE FUNCTION dfn(INTEGER)} and {@code DESCRIBE PROCEDURE qr()}
     * describe. The list itself may be empty for a no-argument routine.
     */
    private void requireRoutineArgumentTypes(final FrostlakeParser.DescribeStatementContext ctx,
                                             final String routineName) {
        if (ctx.LPAREN() == null) {
            throw new RuntimeException("Argument types of function '"
                + routineName.toUpperCase(Locale.ROOT) + "' must be specified.");
        }
    }

    /**
     * The argument types a DESCRIBE writes after a routine's name, which pick one overload: DESCRIBE
     * FUNCTION f(DATE) describes f(x DATE) beside f(x NUMBER), and f(VARCHAR) describes nothing
     * (live-verified).
     */
    private List<DataType> writtenSignature(final FrostlakeParser.DescribeStatementContext ctx) {
        final List<DataType> argumentTypes = new ArrayList<>();
        if (ctx.dataTypeList() != null) {
            for (final FrostlakeParser.DataTypeNameContext typeCtx : ctx.dataTypeList().dataTypeName()) {
                argumentTypes.add(RoutineSignatureForm.writtenType(columnParser, typeCtx));
            }
        }
        return argumentTypes;
    }
}
