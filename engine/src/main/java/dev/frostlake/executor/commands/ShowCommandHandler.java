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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.ShowCommandExecutor;
import dev.frostlake.executor.ShowResultHelpers;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.operators.ResultSetProvider;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.AppObjectKind;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
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
import java.util.Set;
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
     * SHOW of a class's instances, {@code SHOW <class>}: refused, no class being modelled.
     *
     * @param ctx the statement
     * @return never
     */
    public ResultSet handleShowClassStatement(final FrostlakeParser.ShowClassStatementContext ctx) {
        ShowScopeRefusal.refuseClassListing(ctx, catalog, queryExecutor);
        return null;
    }

    /**
     * A SHOW listing, its refusals judged in live's order: the syntax of its modifiers, then the scopes its shape
     * rules out, a LIMIT 0, a WITH PRIVILEGES it does not filter by, and only then the scope's lookup.
     */
    public ResultSet handleShowStatement(final FrostlakeParser.ShowStatementContext ctx) {
        if (ctx.VERSIONS() != null) {
            return new AppObjectListing(catalog).showVersions(ctx);
        }
        final ShowListing listing = ShowListing.of(ctx);
        ShowTailSyntax.requireParsed(ctx, listing);
        RoutineSignatureForm.requireTypesOnly(ctx.showGrantsTarget() == null ? null : ctx.showGrantsTarget().dataTypeList());
        ShowScopeKindRefusal.refuseShape(ctx, listing, queryExecutor);
        ShowScopeRefusal.requireListable(ctx, catalog, queryExecutor, listing);
        if (ctx.showTail() != null && ctx.showTail().LIMIT() != null) {
            requirePositiveLimit(Integer.parseInt(ctx.showTail().INTEGER_LITERAL().getText()));
        }
        ShowTailSyntax.refuseUnfilteredPrivileges(ctx, listing);
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
        result = applyLikeFilter(result, getLikePattern(ctx));
        final ShowModifierProfile profile = ShowModifierProfile.forStatement(ctx);
        if (profile.sortsByName() && searchPath == null) {
            // A search-path listing keeps its path order: the schema the path reaches first lists first.
            result = sortByName(result);
        }
        final FrostlakeParser.ShowTailContext tail = ctx.showTail();
        if (tail != null) {
            int literalIndex = 0;
            if (tail.STARTS() != null) {
                final String prefix = stripQuotes(tail.STRING_LITERAL(literalIndex++).getText());
                if (profile.honorsStartsWith()) {
                    result = applyStartsWith(result, prefix);
                }
            }
            if (tail.ROOT() != null) {
                result = rootTasksOnly(result);
            }
            if (tail.LIMIT() != null) {
                final int limit = Integer.parseInt(tail.INTEGER_LITERAL().getText());
                final String fromName = tail.FROM() != null
                    ? stripQuotes(tail.STRING_LITERAL(literalIndex).getText()) : null;
                if (profile.honorsLimit()) {
                    result = applyLimitFrom(result, limit, fromName);
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
     * {@code LIMIT 0} is rejected, and rejected before the listing's scope is looked up — though after a scope its
     * shape rules out.
     *
     * <p>Live-verified on a real account: every one of the twenty listings probed answers
     * {@code SHOW ... LIMIT 0} with "page size "0" must be greater than 0 in limit clause" — including
     * STAGES, SEQUENCES, WAREHOUSES and FILE FORMATS, which go on to ignore a positive LIMIT entirely.
     * The check is therefore on the clause, not on whether the listing paginates, so it lives here
     * rather than behind {@link ShowModifierProfile#honorsLimit()}.
     */
    private static void requirePositiveLimit(final int limit) {
        if (limit <= 0) {
            throw new RuntimeException("page size \"" + limit + "\" must be greater than 0 in limit clause");
        }
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
            // A name is claimed by the schema that lists it first; that schema keeps all of its rows for it,
            // so a relation's every column is listed, and a later schema's rows for the name are not.
            final Set<String> claimed = new HashSet<>();
            for (final Row row : part.getRows()) {
                final String key = pathKey(row, keyColumn, argumentsColumn);
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
     * The name a search-path row is reached by: its key column, and for a routine its whole declaration as the
     * arguments column spells it. Live hides a later schema's routine only when the declaration matches in its
     * parameter names too: {@code f(x INT)} in the current schema hides PUBLIC's {@code f(x INT)} but not PUBLIC's
     * {@code f(y INT)}, and both of those are listed.
     */
    private static String pathKey(final Row row, final int keyColumn, final int argumentsColumn) {
        final String key = keyColumn < 0 ? "" : String.valueOf(row.getValue(keyColumn));
        if (argumentsColumn < 0) {
            return key;
        }
        final String arguments = String.valueOf(row.getValue(argumentsColumn));
        final int returns = arguments.indexOf(" RETURN ");
        return key + "|" + (returns < 0 ? arguments : arguments.substring(0, returns));
    }

    private ResultSet handleShowStatementInternal(final FrostlakeParser.ShowStatementContext ctx,
                                                  final ShowListingScope forced) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.MODELS() != null) {
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
            // The scope is still judged — IN TABLE is refused like any other schema-level listing's — but
            // there is never anything to list: an external table reads files this engine cannot reach.
            listingScope(ctx, ShowListing.EXTERNAL_TABLES, forced);
            return ExternalTableListing.listing();
        } else if (ctx.INTEGRATIONS() != null) {
            return new IntegrationCommandHandler(catalog, queryExecutor).show(ctx.integrationKind());
        } else if (ctx.VOLUMES() != null) {
            return new ExternalVolumeCommandHandler(catalog).show();
        } else if (ctx.TABLES() != null) {
            final ShowListingScope scope = listingScope(ctx, ShowListing.TABLES, forced);
            if (scope.isAccount()) {
                return showExecutor.showTablesInAccount();
            }
            if (scope.isDatabase()) {
                return showExecutor.showTablesInDatabase(scope.name());
            }
            return showExecutor.showTables(scope.name());
        } else if (ctx.COLUMNS() != null) {
            final String name = ctx.objectName() != null ? scopeName(ctx) : null;
            if (ctx.TABLE() != null || ctx.VIEW() != null || ctx.IN() != null && ctx.ACCOUNT() == null
                    && ctx.DATABASE() == null && ctx.SCHEMA() == null) {
                // A relation scope resolves one object; with no name it is the current schema's relations.
                return showExecutor.showColumnsScoped(name, ctx.VIEW() != null);
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
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
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
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                cortexLike = raw.startsWith("'") && raw.endsWith("'")
                    ? raw.substring(1, raw.length() - 1) : raw;
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
            String like = null;
            if (ctx.LIKE() != null && ctx.STRING_LITERAL() != null) {
                final String raw = ctx.STRING_LITERAL().getText();
                like = raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
            }
            if (ctx.TASK() != null && ctx.identifier() != null) {
                return showExecutor.showParametersInTask(getText(ctx.identifier()), like);
            }
            if (ctx.USER() != null && ctx.identifier() != null) {
                return showExecutor.showParametersInUser(SqlIdentifiers.canonical(ctx.identifier()), like);
            }
            if (ctx.WAREHOUSE() != null && ctx.identifier() != null) {
                return showExecutor.showParametersInWarehouse(getText(ctx.identifier()), like);
            }
            if (ctx.DATABASE() != null && ctx.identifier() != null) {
                return showExecutor.showParametersInDatabase(getText(ctx.identifier()), like);
            }
            if (ctx.SCHEMA() != null && ctx.qualifiedName() != null) {
                final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2);
                final String database = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
                if (database == null) {
                    throw NoCurrentDatabaseRefusal.forStatement();
                }
                return showExecutor.showParametersInSchema(database, parts[parts.length - 1], like);
            }
            return showExecutor.showParameters(like);
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
     * The canonical name a described relation is written as, or the one its {@code IDENTIFIER(...)}
     * argument names. A string that is no identifier reference is refused at the argument, as live does.
     */
    private String describedName(final FrostlakeParser.ObjectNameContext name) {
        return name.qualifiedName() != null ? getText(name.qualifiedName())
            : QualifiedName.join(queryExecutor.resolveObjectNameParts(name));
    }

    /** DESCRIBE DYNAMIC TABLE's columns, which come from projecting the dynamic table itself, zero rows needed. */
    private ResultSet describeDynamicTable(final ShowCommandExecutor showExecutor, final String dtName) {
        return showExecutor.describeDynamicTable(dtName, new ResultSetProvider() {
            @Override
            public ResultSet getResultSet() {
                final List<ResultSet> results = queryExecutor.execute("SELECT * FROM " + dtName + " LIMIT 0");
                return results.get(results.size() - 1);
            }
        });
    }

    /** The name parts DESCRIBE SCHEMA or DATABASE names, refused as live refuses a name with too many parts. */
    private String[] describedContainerParts(final FrostlakeParser.ObjectNameContext name, final int maxParts) {
        final String[] parts = queryExecutor.resolveObjectNameParts(name);
        if (parts.length > maxParts) {
            throw new RuntimeException(SqlCompilationError.of("Object does not exist, or operation cannot be performed."));
        }
        return parts;
    }

    public ResultSet handleDescribeStatement(final FrostlakeParser.DescribeStatementContext ctx) {
        if (ctx.INTEGRATION() != null) {
            return new IntegrationCommandHandler(catalog, queryExecutor).describe(ctx.integrationKind(), ctx.identifier());
        }
        if (ctx.VOLUME() != null) {
            return new ExternalVolumeCommandHandler(catalog).describe(ctx.identifier());
        }
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.NOTEBOOK() != null || ctx.STREAMLIT() != null) {
            return new AppObjectListing(catalog).describe(ctx.NOTEBOOK() != null ? AppObjectKind.NOTEBOOK
                : AppObjectKind.STREAMLIT, ctx.qualifiedName());
        }
        if (ctx.IDENTIFIER() != null) {
            DescribeProperties.refuseAfterBareName(ctx.describeTypeProperty());
            throw new RuntimeException("Unsupported feature 'DESCRIBE "
                + ctx.IDENTIFIER().getText().toUpperCase(Locale.ROOT) + "'.");
        }
        RoutineSignatureForm.requireTypesOnly(ctx.dataTypeList());
        final boolean stageProperties = DescribeProperties.describesStage(ctx, queryExecutor);
        if (ctx.ACCOUNT() != null) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        }
        if (ctx.SCHEMA() != null) {
            final String[] parts = describedContainerParts(ctx.objectName(), 2);
            catalog.resolveSchema(QualifiedName.of(parts));
            return showExecutor.describeSchema(QualifiedName.join(parts));
        }
        if (ctx.DATABASE() != null) {
            final String databaseName = describedContainerParts(ctx.objectName(), 1)[0];
            catalog.databaseExact(databaseName);
            return showExecutor.describeDatabase(databaseName);
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
            return showExecutor.describeRelation(describedName(ctx.objectName()), describedKind);
        } else if (ctx.PIPE() != null) {
            final String pipeName = getText(ctx.identifier());
            return showExecutor.describePipe(pipeName);
        } else if (ctx.SEQUENCE() != null) {
            final String sequenceName = getText(ctx.identifier());
            return showExecutor.describeSequence(sequenceName);
        } else if (ctx.TASK() != null) {
            final String taskName = getText(ctx.identifier());
            return showExecutor.describeTask(taskName);
        } else if (ctx.ALERT() != null) {
            return new AlertCommandHandler(queryExecutor).describe(ctx.qualifiedName());
        } else if (ctx.STREAM() != null) {
            final String streamName = getText(ctx.identifier());
            return showExecutor.describeStream(streamName);
        } else if (ctx.CORTEX() != null) {
            return showExecutor.describeCortexSearchService(getText(ctx.qualifiedName()));
        } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
            return showExecutor.describeComputePool(getText(ctx.identifier()));
        } else if (ctx.WAREHOUSE() != null) {
            final String warehouseName = getText(ctx.identifier());
            return showExecutor.describeWarehouse(warehouseName);
        } else if (ctx.STAGE() != null) {
            final String stageName = getText(ctx.qualifiedName());
            return showExecutor.describeStage(stageName);
        } else if (ctx.TAG() != null) {
            final String tagName = getText(ctx.identifier());
            return showExecutor.describeTag(tagName);
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
            // The column list comes from projecting the dynamic table itself, zero rows needed.
            return showExecutor.describeDynamicTable(dtName, new ResultSetProvider() {
                @Override
                public ResultSet getResultSet() {
                    final List<ResultSet> results = queryExecutor.execute("SELECT * FROM " + dtName + " LIMIT 0");
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
        if (ctx.LIKE() == null || ctx.STRING_LITERAL() == null) return null;
        final String raw = ctx.STRING_LITERAL().getText();
        return raw.startsWith("'") && raw.endsWith("'") ? raw.substring(1, raw.length() - 1) : raw;
    }

    /** The index of the name-ish column SHOW filters (LIKE / STARTS WITH / LIMIT FROM) apply to, or -1. */
    private static int nameColumnIndex(final ResultSet rs) {
        for (final String col : new String[]{"name", "parameter_name", "key", "account_name", "column_name"}) {
            try {
                return rs.getColumnIndex(col);
            } catch (final RuntimeException ignored) {
            }
        }
        return -1;
    }

    private static String stripQuotes(final String raw) {
        return raw != null && raw.startsWith("'") && raw.endsWith("'")
            ? raw.substring(1, raw.length() - 1) : raw;
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
    private ResultSet applyLimitFrom(final ResultSet rs, final int limit, final String fromName) {
        if (rs == null) return rs;
        final int nameIdx = nameColumnIndex(rs);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            if (fromName != null && nameIdx >= 0) {
                final Object val = row.getValue(nameIdx);
                if (val == null || val.toString().compareTo(fromName) <= 0) {
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
        // Convert SQL LIKE pattern to regex: % -> .*, _ -> .
        final String regex = pattern.replace(".", "\\.").replace("%", ".*").replace("_", ".");
        final List<Row> filtered = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final Object val = row.getValue(nameIdx);
            if (val != null && val.toString().matches("(?i)" + regex)) {
                filtered.add(row);
            }
        }
        return new ResultSet(rs.getColumns(), filtered);
    }

    private ResultSet handleShowGrants(final FrostlakeParser.ShowStatementContext ctx) {
        final ShowCommandExecutor showExecutor = queryExecutor.getShowExecutor();
        if (ctx.ON() != null) {
            // SHOW GRANTS ON [kind] name
            final FrostlakeParser.ShowGrantsTargetContext target = ctx.showGrantsTarget();
            final String[] parts = ParseTreeText.qualifiedNameParts(target.qualifiedName());
            if (target.securableKind() == null) {
                // A bare name is a table or a view, and anything else is no object at all (live-verified).
                final GrantedObject relation = queryExecutor.grantedRelation(parts);
                return showExecutor.showGrantsOnObject(relation.getKind(), getText(target.qualifiedName()), relation);
            }
            final String objectType = SecurableKinds.of(target.securableKind());
            queryExecutor.requireSecurable(objectType, parts);
            final Integer arity = target.LPAREN() == null ? null
                : Integer.valueOf(target.dataTypeList() == null ? 0 : target.dataTypeList().dataTypeName().size());
            return showExecutor.showGrantsOnObject(objectType, getText(target.qualifiedName()),
                queryExecutor.grantedObject(objectType, parts, arity));
        } else if (ctx.TO() != null) {
            // SHOW GRANTS TO USER/ROLE identifier
            final String targetType = ctx.USER() != null ? "USER" : "ROLE";
            final String targetName = getText(ctx.identifier());
            return showExecutor.showGrantsTo(targetType, targetName);
        }
        if (ctx.OF() != null && ctx.ROLE() != null && ctx.identifier() != null) {
            // SHOW GRANTS OF ROLE identifier
            return showExecutor.showGrantsOfRole(getText(ctx.identifier()));
        }
        if (ctx.ON() == null && ctx.TO() == null && ctx.OF() == null) {
            // Bare SHOW GRANTS: what the current user holds.
            return showExecutor.showGrantsForCurrentUser();
        }
        throw new RuntimeException("Invalid SHOW GRANTS syntax");
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
                + routineName.toUpperCase() + "' must be specified.");
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
                argumentTypes.add(columnParser.parseDataType(typeCtx));
            }
        }
        return argumentTypes;
    }
}
