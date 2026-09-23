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

import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.TableShadows;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledDropKind;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.types.DataType;

import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles DROP statements (DROP TABLE/VIEW/DATABASE/SCHEMA/STREAM/TASK/SEQUENCE/...), extracted from
 * {@link DDLCommandHandler}, which keeps the public dispatch and delegates here. Shared schema-resolution
 * helpers remain on DDLCommandHandler and are reached via the {@code ddl} back-reference.
 */
public class DropCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(DropCommandHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;
    private StreamManager streamManager;

    DropCommandHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor,
                       final ColumnDefinitionParser columnParser) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.columnParser = columnParser;
    }

    void setStreamManager(final StreamManager streamManager) {
        this.streamManager = streamManager;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    /** Only an owner / administrative role / DROP-granted role may drop the object. */
    private void checkDrop(final SecurableObjectType objectType, final String objectName) {
        if (queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().checkPermission(Privilege.DROP, objectType, objectName);
        }
    }

    /**
     * Whether IF EXISTS forgives a failed drop: it forgives the object's absence, never an access-control
     * refusal — dropping an object another role owns is refused with or without it.
     */
    private static boolean forgiven(final boolean ifExists, final RuntimeException failure) {
        return ifExists && !String.valueOf(failure.getMessage()).startsWith(SqlAccessControlError.PREFIX);
    }

    /**
     * Detach one schema's tables from storage as part of a DROP SCHEMA / DROP DATABASE: snapshot each
     * table's rows into {@code out} (keyed by fully-qualified name) and release its storage, exactly as
     * DROP TABLE does. Without this the storage entries outlive the drop, so re-creating the same
     * database/schema fails with "Table storage already exists" and a CLONE into the reused name appends to
     * the stale rows instead of replacing them. Buffered writes are discarded so an uncommitted change from
     * this transaction isn't flushed to now-missing storage at commit.
     */
    private void snapshotAndReleaseStorage(final String databaseName, final Schema schema,
                                           final Map<String, List<Row>> out,
                                           final Map<String, List<Row>> hiddenOut) {
        final StorageEngine storage = queryExecutor.getStorageEngine();
        for (final Table table : schema.getTables()) {
            final String fqn = QualifiedName.key(databaseName, schema.getName(), table.getName());
            if (!storage.hasTable(fqn)) {
                continue;
            }
            out.put(fqn, new ArrayList<>(storage.getTableStorage(fqn).scan()));
            storage.dropTable(fqn);
            queryExecutor.resetCopyLoadHistory(fqn);
            queryExecutor.getTransactionManager().discardBufferedWritesFor(fqn);
        }
        // The permanent tables temporary ones hid go with the schema, and come back with it.
        for (final Table hidden : schema.getShadowedTables()) {
            final String fqn = QualifiedName.key(databaseName, schema.getName(), hidden.getName());
            if (storage.hasShadowedTable(fqn)) {
                hiddenOut.put(fqn, new ArrayList<>(storage.getShadowedTableStorage(fqn).scan()));
                storage.dropShadowedTable(fqn);
            }
        }
    }

    /**
     * IF EXISTS forgives only the dropped object's own absence: the database and schema its name passes
     * through must exist, and a name the session cannot place is refused naming DROP, before anything is
     * dropped (live-verified for every schema-scoped kind, and for a schema's database).
     */
    /**
     * The name a DROP names, part by part. Every kind reads it as an {@code objectName}, so
     * {@code IDENTIFIER('<name>')} and a session variable reach the object exactly as a written name
     * does — the account takes both for every kind.
     *
     * @param ctx the DROP statement
     * @return its object's canonical name parts
     */
    private String[] dropNameParts(final FrostlakeParser.DropStatementContext ctx) {
        // FUNCTION and PROCEDURE keep the plain name: their REQUIRED signature's parens would sit
        // against an IDENTIFIER() call's own, so those two kinds never carry an objectName.
        return ctx.objectName() != null
            ? queryExecutor.resolveObjectNameParts(ctx.objectName())
            : qualifiedNameParts(ctx.qualifiedName());
    }

    /** The same name, joined — what the refusals quote. */
    private String dropNameText(final FrostlakeParser.DropStatementContext ctx) {
        return QualifiedName.join(dropNameParts(ctx));
    }

    private void requireContainers(final FrostlakeParser.DropStatementContext ctx) {
        if (ctx.SCHEMA() != null) {
            final String[] parts = catalog.withoutAccount(dropNameParts(ctx), 2);
            if (parts.length == 2) {
                catalog.databaseExact(parts[0]);
            } else if (catalog.getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
        } else if ((ctx.objectName() != null || ctx.qualifiedName() != null)
                && !namesAnAccountScopedKind(ctx)) {
            catalog.requireOwningSchema(QualifiedName.of(dropNameParts(ctx)));
        }
    }

    /**
     * Whether the kind dropped lives in the ACCOUNT rather than a schema, so its name is whole on its
     * own and wants no container. These kinds reached that answer by carrying a bare identifier until
     * every kind learned to read an IDENTIFIER() name.
     *
     * @param ctx the DROP statement
     * @return true for a database, a warehouse, a compute pool, a user or a role
     */
    private static boolean namesAnAccountScopedKind(final FrostlakeParser.DropStatementContext ctx) {
        return ctx.DATABASE() != null || ctx.WAREHOUSE() != null || ctx.POOL() != null
            || ctx.USER() != null || ctx.ROLE() != null;
    }

    /**
     * DROP of a class's instance, {@code DROP <class> <instance>}. No class is modelled: the class is refused as
     * missing, IF EXISTS or not, and a CASCADE or RESTRICT after the instance as an unsupported feature
     * naming the class — {@code Unsupported feature 'DROP X'.} A class named otherwise than by a plain word — an
     * IDENTIFIER(), a quoted or a qualified name — takes neither word: {@code DROP IDENTIFIER('x') y CASCADE},
     * {@code DROP "x" y CASCADE} and {@code DROP s.x y CASCADE} are syntax errors at the CASCADE. A built-in class
     * exists, so the instance it names is looked up and misses; a kind live drops that Frostlake has no statements
     * for finds nothing to drop (live-verified).
     */
    public Object handleDropClassStatement(final FrostlakeParser.DropClassStatementContext ctx) {
        final UnmodelledDropKind unmodelled = UnmodelledDropKind.of(ctx);
        if (unmodelled != null) {
            return dropUnmodelled(ctx, unmodelled);
        }
        final FrostlakeParser.ClassNameContext className = ctx.className();
        if ((className.identifierArgument() != null || !className.DOT().isEmpty()
                || className.QUOTED_IDENTIFIER() != null) && ctx.dropBehavior() != null) {
            final Token word = ctx.dropBehavior().getStart();
            final int[] shown = LeadingCommentOffset.rebase(word.getLine(), word.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + shown[0] + " at position "
                + shown[1] + " unexpected '" + word.getText() + "'."));
        }
        final String[] parts = ClassNameParts.of(ctx.className(), queryExecutor);
        if (ctx.dropBehavior() != null) {
            throw new RuntimeException("Unsupported feature 'DROP "
                + SqlIdentifiers.spellAlreadyCanonicalPath(QualifiedName.join(parts)) + "'.");
        }
        if (BuiltInClass.isBuiltIn(parts, catalog)) {
            return dropBuiltInInstance(ctx);
        }
        throw new RuntimeException(ShowScopeRefusal.missingClass(parts, catalog));
    }

    /**
     * DROP of an instance of a built-in class, none of which is modelled: the instance's name resolves the way any
     * schema object's does — a missing database or schema is refused, IF EXISTS or not, and so is a name with too
     * many parts — and then the instance is missing, {@code Instance 'DB.PUBLIC.M' does not exist or not
     * authorized.}, which IF EXISTS forgives (live-verified).
     */
    private Object dropBuiltInInstance(final FrostlakeParser.DropClassStatementContext ctx) {
        final FrostlakeParser.ObjectNameContext instance = ctx.dropInstanceName().objectName();
        final String[] parts = instance != null ? queryExecutor.resolveObjectNameParts(instance)
            : SqlIdentifiers.canonicalTextParts(ctx.dropInstanceName().getText());
        final String missing = SqlCompilationError.doesNotExist("Instance", BuiltInClass.instancePath(parts, catalog));
        if (ctx.if_exists() != null) {
            ConditionalDdlOutcome.dropSkipped();
            return null;
        }
        throw new RuntimeException(missing);
    }

    /**
     * DROP of a kind Frostlake has no statements for, so no object of it ever exists: the name resolves the way
     * any object's does — a missing database or schema is refused, IF EXISTS or not, and so is a name with too
     * many parts — and then the object is missing, which IF EXISTS forgives. CASCADE and RESTRICT change nothing.
     */
    private Object dropUnmodelled(final FrostlakeParser.DropClassStatementContext ctx, final UnmodelledDropKind kind) {
        final FrostlakeParser.ObjectNameContext instance = ctx.dropInstanceName().objectName();
        final String[] parts = instance != null ? queryExecutor.resolveObjectNameParts(instance)
            : SqlIdentifiers.canonicalTextParts(ctx.dropInstanceName().getText());
        final String missing;
        if (kind.isSchemaObject()) {
            final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
            final RelationKind found = kind == UnmodelledDropKind.EXTERNAL_TABLE
                ? owner.relationKindOf(parts[parts.length - 1]) : null;
            if (found != null) {
                // A relation of another kind holds the name, which IF EXISTS does not forgive (live-verified).
                throw new RuntimeException(SqlCompilationError.objectOfOtherType(found.spelling(), "EXTERNAL_TABLE"));
            }
            missing = SqlCompilationError.doesNotExist(kind.noun(), owner.qualifiedName(parts[parts.length - 1]));
        } else if (parts.length > 1) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        } else {
            missing = SqlCompilationError.doesNotExist(kind.noun(), parts[0]);
        }
        if (ctx.if_exists() != null) {
            ConditionalDdlOutcome.dropSkipped();
            return null;
        }
        throw new RuntimeException(missing);
    }

    public Object handleDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        final boolean ifExists = ctx.if_exists() != null;
        if (ctx.ALERT() != null) {
            return new AlertCommandHandler(queryExecutor).drop(ctx, ifExists);
        }
        RoutineSignatureForm.requireTypesOnly(ctx.dataTypeList());
        requireContainers(ctx);

        try {
            if (ctx.DATABASE() != null) {
                final String dbName = dropNameText(ctx);
                try {
                    checkDrop(SecurableObjectType.DATABASE, dbName);
                    queryExecutor.requireOwnership("DATABASE", dropNameParts(ctx), null);
                    final Database droppedDb = catalog.databaseExact(dbName);
                    if (droppedDb != null) {
                        // Snapshot every table's rows and RELEASE its storage (as DROP TABLE does), so the
                        // database name can be created again without colliding with — or inheriting — the
                        // dropped tables' storage. UNDROP DATABASE restores both metadata and rows.
                        final Map<String, List<Row>> tableRows = new LinkedHashMap<>();
                        final Map<String, List<Row>> hiddenTableRows = new LinkedHashMap<>();
                        for (final Schema droppedSchema : droppedDb.getAllSchemas()) {
                            snapshotAndReleaseStorage(dbName, droppedSchema, tableRows, hiddenTableRows);
                        }
                        catalog.recordDropped("DATABASE:" + dbName,
                            new DroppedObject(droppedDb, tableRows, hiddenTableRows));
                    }
                    catalog.dropDatabase(dbName, false);
                    if (streamManager != null) {
                        streamManager.onDatabaseDropped(dbName);
                    }
                    logger.trace("Dropped database: {}", dbName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Database does not exist (IF EXISTS): {}", dbName);
                }
            } else if (ctx.SCHEMA() != null) {
                final String schemaName = dropNameText(ctx);
                // Refused before IF EXISTS forgives anything: a schema name has at most two parts.
                final String[] parts = catalog.withoutAccount(dropNameParts(ctx), 2);

                try {
                    checkDrop(SecurableObjectType.SCHEMA, schemaName);
                    queryExecutor.requireOwnership("SCHEMA", parts, null);
                    // Snapshot schema metadata for UNDROP (its tables' storage survives a DROP SCHEMA).
                    final String snapDbName = parts.length == 1 ? catalog.getCurrentDatabase() : parts[0];
                    final String snapSchemaName = parts.length == 1 ? parts[0] : parts[1];
                    final Database snapDb = catalog.databaseExact(snapDbName);
                    final Schema snapSchema = snapDb.schemaExact(snapSchemaName);
                    if (snapSchema != null) {
                        final Map<String, List<Row>> tableRows = new LinkedHashMap<>();
                        final Map<String, List<Row>> hiddenTableRows = new LinkedHashMap<>();
                        snapshotAndReleaseStorage(snapDbName, snapSchema, tableRows, hiddenTableRows);
                        catalog.recordDropped("SCHEMA:" + QualifiedName.key(snapDbName, snapSchemaName),
                            new DroppedObject(snapSchema, tableRows, hiddenTableRows));
                    }
                    // DROP SCHEMA … CASCADE also drops the objects the schema still contains; without it
                    // (RESTRICT, the default) a non-empty schema is refused.
                    final boolean cascade = ctx.dropBehavior() != null && ctx.dropBehavior().CASCADE() != null;
                    if (parts.length == 1) {
                        catalog.dropSchema(catalog.getCurrentDatabase(), parts[0], cascade);
                        if (streamManager != null) {
                            streamManager.onSchemaDropped(catalog.getCurrentDatabase(), parts[0]);
                        }
                    } else {
                        catalog.dropSchema(parts[0], parts[1], cascade);
                        if (streamManager != null) {
                            streamManager.onSchemaDropped(parts[0], parts[1]);
                        }
                    }
                    logger.trace("Dropped schema: {}", schemaName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Schema does not exist (IF EXISTS): {}", schemaName);
                }
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
                final String qn = dropNameText(ctx);
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    rejectWrongKind(schema, parts[parts.length - 1], RelationKind.DYNAMIC_TABLE);
                    checkDrop(SecurableObjectType.DYNAMIC_TABLE, qn);
                    final DynamicTable droppedTable = schema.getDynamicTable(parts[parts.length - 1]);
                    schema.dropDynamicTable(parts[parts.length - 1]);
                    // Kept for UNDROP DYNAMIC TABLE, as a dropped table is.
                    final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
                    catalog.recordDropped("DYNAMIC TABLE:" + QualifiedName.key(databaseName, schema.getName(),
                        droppedTable.getName()), new DroppedObject(droppedTable, (List<Row>) null));
                    logger.trace("Dropped dynamic table: {}", qn);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never a name another kind holds.
                    if (!forgiven(ifExists, e) || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Dynamic table does not exist (IF EXISTS): {}", qn);
                }
            } else if (ctx.TABLE() != null) {
                final String qualifiedName = queryExecutor.resolveObjectName(ctx.objectName());

                try {
                    // Parts come from the parse tree (or the IDENTIFIER() value per dotted level) —
                    // never by re-splitting the joined spelling, which breaks a quoted name
                    // containing a dot.
                    final String[] parts = catalog.withoutAccount(
                        queryExecutor.resolveObjectNameParts(ctx.objectName()), 3);
                    final Schema schema;
                    final String databaseName;
                    final String tableName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        databaseName = catalog.getCurrentDatabase();
                        tableName = parts[0];
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        databaseName = catalog.getCurrentDatabase();
                        schema = catalog.getDatabase(databaseName).getSchema(parts[0]);
                        tableName = parts[1];
                    } else if (parts.length == 3) {
                        databaseName = parts[0];
                        schema = catalog.getDatabase(databaseName).getSchema(parts[1]);
                        tableName = parts[2];
                    } else {
                        throw new RuntimeException("Invalid table name: " + qualifiedName);
                    }
                    if (ctx.ICEBERG() != null && schema.hasTable(tableName)) {
                        IcebergTables.requireIceberg(schema.getTable(tableName));
                    }

                    // INFORMATION_SCHEMA is read-only, and its views are found before any kind
                    // check — so even DROP TABLE on one refuses naming the VIEW (live-verified).
                    if ("INFORMATION_SCHEMA".equals(schema.getName()) && schema.hasView(tableName)) {
                        throw new RuntimeException(
                            SqlAccessControlError.insufficientPrivileges("view", tableName.toUpperCase()));
                    }

                    rejectWrongKind(schema, tableName, RelationKind.TABLE);
                    final Table table = catalog.resolveTable(QualifiedName.of(parts));
                    checkDrop(SecurableObjectType.TABLE, qualifiedName);
                    queryExecutor.requireOwnership("TABLE", parts, null);

                    final String fullyQualifiedName = QualifiedName.key(databaseName, schema.getName(), tableName);
                    // Snapshot table metadata + rows for UNDROP before removing the storage.
                    final List<Row> snapshotRows =
                        new ArrayList<>(queryExecutor.getStorageEngine().getTableStorage(fullyQualifiedName).scan());
                    catalog.recordDropped("TABLE:" + fullyQualifiedName, new DroppedObject(table, snapshotRows));

                    schema.dropTable(table.getName());
                    queryExecutor.getStorageEngine().dropTable(fullyQualifiedName);
                    if (schema.shadowedTable(table.getName()) == null) {
                        queryExecutor.resetCopyLoadHistory(fullyQualifiedName);
                    }
                    // Drop any buffered writes for this table so an uncommitted INSERT/UPDATE/DELETE earlier
                    // in the same transaction (e.g. a proc that seeds then drops a temp table) isn't flushed
                    // to now-missing storage at commit.
                    queryExecutor.getTransactionManager().discardBufferedWritesFor(fullyQualifiedName);
                    if (streamManager != null) {
                        streamManager.onTableDropped(fullyQualifiedName);
                    }
                    // Dropping a temporary table uncovers the permanent table it hid (live-verified).
                    TableShadows.settle(schema, queryExecutor.getStorageEngine(), databaseName, table.getName());

                    logger.trace("Dropped table: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never an access-control refusal and never a name
                    // another kind holds.
                    if (!forgiven(ifExists, e) || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Table does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() == null) {
                final String qualifiedName = dropNameText(ctx);
                final String[] parts = dropNameParts(ctx);

                try {
                    final Schema schema;
                    final String viewName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        viewName = parts[0];
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        viewName = parts[1];
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        viewName = parts[2];
                    } else {
                        throw new RuntimeException("Invalid view name: " + qualifiedName);
                    }

                    rejectWrongKind(schema, viewName, RelationKind.VIEW);
                    checkDrop(SecurableObjectType.VIEW, qualifiedName);
                    queryExecutor.requireOwnership("VIEW", parts, null);
                    schema.dropView(viewName);
                    logger.trace("Dropped view: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never an access-control refusal and never a name
                    // another kind holds.
                    if (!forgiven(ifExists, e) || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("View does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() != null) {
                final String qualifiedName = dropNameText(ctx);
                final String[] parts = dropNameParts(ctx);

                try {
                    final Schema schema;
                    final String mvName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        mvName = parts[0];
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        mvName = parts[1];
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        mvName = parts[2];
                    } else {
                        throw new RuntimeException("Invalid materialized view name: " + qualifiedName);
                    }

                    rejectWrongKind(schema, mvName, RelationKind.MATERIALIZED_VIEW);
                    checkDrop(SecurableObjectType.MATERIALIZED_VIEW, qualifiedName);
                    schema.dropMaterializedView(mvName);
                    logger.trace("Dropped materialized view: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never a name another kind holds.
                    if (!forgiven(ifExists, e) || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Materialized view does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.STREAM() != null) {
                final String streamQn = dropNameText(ctx);
                final String streamName = ddl.extractObjectName(streamQn);

                try {
                    final Schema schema = ddl.resolveSchemaFromQualifiedName(streamQn);
                    rejectWrongKind(schema, streamName, RelationKind.STREAM);
                    checkDrop(SecurableObjectType.STREAM, streamQn);
                    queryExecutor.requireOwnership("STREAM", dropNameParts(ctx), null);
                    schema.dropStream(streamName);
                    logger.trace("Dropped stream: {}", streamName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never a name another kind holds.
                    if (!forgiven(ifExists, e) || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Stream does not exist (IF EXISTS): {}", streamName);
                }
            } else if (ctx.TASK() != null) {
                final String taskQn = dropNameText(ctx);
                final String taskName = ddl.extractObjectName(taskQn);

                try {
                    final Schema schema = ddl.resolveSchemaFromQualifiedName(taskQn);
                    checkDrop(SecurableObjectType.TASK, taskQn);
                    queryExecutor.requireOwnership("TASK", dropNameParts(ctx), null);
                    schema.dropTask(taskName);
                    // A finalizer task of the dropped root finalizes nothing any more: it stands alone.
                    for (final Task remaining : schema.getTasks()) {
                        if (remaining.isFinalizer() && remaining.getFinalizedRootTask().equalsIgnoreCase(taskName)) {
                            remaining.setFinalizedRootTask(null);
                        }
                    }
                    logger.trace("Dropped task: {}", taskName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Task does not exist (IF EXISTS): {}", taskName);
                }
            } else if (ctx.PIPE() != null) {
                final String pipeName = dropNameText(ctx);

                try {
                    final Schema schema = catalog.resolveOwningSchema(pipeName);
                    checkDrop(SecurableObjectType.PIPE, pipeName);
                    queryExecutor.requireOwnership("PIPE", dropNameParts(ctx), null);
                    schema.dropPipe(QualifiedName.parse(pipeName).last());
                    logger.trace("Dropped pipe: {}", pipeName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Pipe does not exist (IF EXISTS): {}", pipeName);
                }
            } else if (ctx.SEQUENCE() != null) {
                final String seqQn = dropNameText(ctx);
                final String sequenceName = ddl.extractObjectName(seqQn);

                try {
                    final Schema schema = ddl.resolveSchemaFromQualifiedName(seqQn);
                    queryExecutor.requireOwnership("SEQUENCE", dropNameParts(ctx), null);
                    schema.dropSequence(sequenceName);
                    logger.trace("Dropped sequence: {}", sequenceName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Sequence does not exist (IF EXISTS): {}", sequenceName);
                }
            } else if (ctx.CORTEX() != null) {
                final String serviceName = dropNameText(ctx);
                final String[] serviceParts = dropNameParts(ctx);
                try {
                    ddl.resolveSchemaFromQualifiedName(serviceName)
                        .dropCortexSearchService(serviceParts[serviceParts.length - 1]);
                    logger.trace("Dropped Cortex search service: {}", serviceName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Cortex search service does not exist (IF EXISTS): {}", serviceName);
                }
            } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
                final String poolName = dropNameText(ctx);
                try {
                    catalog.dropComputePool(poolName);
                    logger.trace("Dropped compute pool: {}", poolName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Compute pool does not exist (IF EXISTS): {}", poolName);
                }
            } else if (ctx.WAREHOUSE() != null) {
                final String warehouseName = dropNameText(ctx);

                try {
                    catalog.dropWarehouse(warehouseName);
                    logger.trace("Dropped warehouse: {}", warehouseName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Warehouse does not exist (IF EXISTS): {}", warehouseName);
                }
            } else if (ctx.STAGE() != null) {
                final String stageName = dropNameText(ctx);

                try {
                    checkDrop(SecurableObjectType.STAGE, stageName);
                    queryExecutor.requireOwnership("STAGE", dropNameParts(ctx), null);
                    catalog.dropStage(stageName);
                    logger.trace("Dropped stage: {}", stageName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Stage does not exist (IF EXISTS): {}", stageName);
                }
            } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
                final String fileFormatName = dropNameText(ctx);

                try {
                    queryExecutor.requireOwnership("FILE_FORMAT", dropNameParts(ctx), null);
                    catalog.dropFileFormat(fileFormatName);
                    logger.trace("Dropped file format: {}", fileFormatName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("File format does not exist (IF EXISTS): {}", fileFormatName);
                }
            } else if (ctx.FUNCTION() != null) {
                final String qualifiedName = dropNameText(ctx);
                final String[] parts = dropNameParts(ctx);

                try {
                    final Schema schema;
                    final String functionName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        functionName = parts[0];
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        functionName = parts[1];
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        functionName = parts[2];
                    } else {
                        throw new RuntimeException("Invalid function name: " + qualifiedName);
                    }

                    // The written signature names one overload, an empty one the overload taking no
                    // arguments: DROP FUNCTION f() over f(INT) and f(DATE) drops nothing (live-verified).
                    final List<DataType> argumentTypes = new ArrayList<>();
                    if (ctx.dataTypeList() != null) {
                        for (final FrostlakeParser.DataTypeNameContext dtCtx : ctx.dataTypeList().dataTypeName()) {
                            argumentTypes.add(columnParser.parseDataType(dtCtx));
                        }
                    }
                    queryExecutor.requireFunctionOwnership(schema, functionName, argumentTypes);
                    schema.dropFunctionBySignature(functionName, argumentTypes);
                    logger.trace("Dropped function: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Function does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.PROCEDURE() != null) {
                final String qualifiedName = dropNameText(ctx);
                final String[] parts = dropNameParts(ctx);

                try {
                    final Schema schema;
                    final String procedureName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        procedureName = parts[0];
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        procedureName = parts[1];
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        procedureName = parts[2];
                    } else {
                        throw new RuntimeException("Invalid procedure name: " + qualifiedName);
                    }

                    // The written signature names one overload, an empty one the overload taking no
                    // arguments, as a function's does.
                    final List<DataType> argumentTypes = new ArrayList<>();
                    if (ctx.dataTypeList() != null) {
                        for (final FrostlakeParser.DataTypeNameContext dtCtx : ctx.dataTypeList().dataTypeName()) {
                            argumentTypes.add(columnParser.parseDataType(dtCtx));
                        }
                    }
                    queryExecutor.requireProcedureOwnership(schema, procedureName, argumentTypes);
                    schema.dropProcedureBySignature(procedureName, argumentTypes);
                    logger.trace("Dropped procedure: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Procedure does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.USER() != null) {
                final String userName = dropNameText(ctx);

                try {
                    catalog.dropUser(userName);
                    logger.trace("Dropped user: {}", userName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("User does not exist (IF EXISTS): {}", userName);
                }
            } else if (ctx.ROLE() != null) {
                final String roleName = dropNameText(ctx);

                try {
                    catalog.dropRole(roleName);
                    logger.trace("Dropped role: {}", roleName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Role does not exist (IF EXISTS): {}", roleName);
                }
            } else if (ctx.TAG() != null) {
                final String tagName = dropNameText(ctx);
                try {
                    checkDrop(SecurableObjectType.TAG, tagName);
                    queryExecutor.requireOwnership("TAG", dropNameParts(ctx), null);
                    final Tag droppedTag = catalog.getTag(tagName);
                    catalog.dropTag(tagName);
                    final String[] tagParts = dropNameParts(ctx);
                    final String tagDb = tagParts.length == 3 ? tagParts[0] : catalog.getCurrentDatabase();
                    final String tagSchema = tagParts.length == 3 ? tagParts[1]
                        : tagParts.length == 2 ? tagParts[0] : catalog.getCurrentSchema();
                    catalog.recordDropped(
                        "TAG:" + (tagDb + "." + tagSchema + "." + tagParts[tagParts.length - 1]).toUpperCase(),
                        new DroppedObject(droppedTag, (List<Row>) null));
                    logger.trace("Dropped tag: {}", tagName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Tag does not exist (IF EXISTS): {}", tagName);
                }
            } else if (ctx.MASKING() != null && ctx.POLICY() != null && ctx.ROW() == null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    checkDrop(SecurableObjectType.MASKING_POLICY, dropNameText(ctx));
                    queryExecutor.requireOwnership("MASKING_POLICY", parts, null);
                    if (!schema.hasMaskingPolicy(parts[parts.length - 1].toUpperCase())) {
                        // The "<Kind> '<QUALIFIED>' does not exist or not authorized." family — the
                        // same sentence the sibling policies already give, live-verified for this one.
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                            schema.qualifiedName(parts[parts.length - 1].toUpperCase())));
                    }
                    if (catalog.isPolicyInUse(parts[parts.length - 1], true)) {
                        throw new RuntimeException("Policy " + parts[parts.length - 1].toUpperCase()
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropMaskingPolicy(parts[parts.length - 1].toUpperCase());
                    logger.trace("Dropped masking policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    checkDrop(SecurableObjectType.ROW_ACCESS_POLICY, dropNameText(ctx));
                    queryExecutor.requireOwnership("ROW_ACCESS_POLICY", parts, null);
                    if (!schema.hasRowAccessPolicy(parts[parts.length - 1].toUpperCase())) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy",
                            schema.qualifiedName(parts[parts.length - 1].toUpperCase())));
                    }
                    if (catalog.isPolicyInUse(parts[parts.length - 1], false)) {
                        throw new RuntimeException("Policy " + parts[parts.length - 1].toUpperCase()
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropRowAccessPolicy(parts[parts.length - 1].toUpperCase());
                    logger.trace("Dropped row access policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.JOIN() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    final String policyName = parts[parts.length - 1].toUpperCase();
                    if (!schema.hasJoinPolicy(policyName)) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Join policy",
                            schema.qualifiedName(policyName)));
                    }
                    if (catalog.isJoinPolicyInUse(policyName)) {
                        throw new RuntimeException(SqlCompilationError.PREFIX + " Policy " + policyName
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropJoinPolicy(policyName);
                    logger.trace("Dropped join policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.AGGREGATION() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    final String policyName = parts[parts.length - 1].toUpperCase();
                    if (!schema.hasAggregationPolicy(policyName)) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Aggregation policy",
                            schema.qualifiedName(policyName)));
                    }
                    if (catalog.isAggregationPolicyInUse(policyName)) {
                        throw new RuntimeException(SqlCompilationError.PREFIX + " Policy " + policyName
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropAggregationPolicy(policyName);
                    logger.trace("Dropped aggregation policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.PROJECTION() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    final String policyName = parts[parts.length - 1].toUpperCase();
                    if (!schema.hasProjectionPolicy(policyName)) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                            schema.qualifiedName(policyName)));
                    }
                    if (catalog.isProjectionPolicyInUse(policyName)) {
                        // Attached to a column: live refuses the drop rather than detaching it.
                        throw new RuntimeException(SqlCompilationError.PREFIX + " Policy " + policyName
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropProjectionPolicy(policyName);
                    logger.trace("Dropped projection policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.CONTACT() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    final String contactName = parts[parts.length - 1].toUpperCase();
                    if (!schema.hasContact(contactName)) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Contact",
                            schema.qualifiedName(contactName)));
                    }
                    schema.dropContact(contactName);
                    logger.trace("Dropped contact: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            }

            return null;
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            throw StatementErrors.propagate(e);
        }
    }




    /**
     * Refuse a DROP whose named kind is not the kind holding the name. The five relation kinds share one
     * name space per schema, so the object is found and the refusal names both kinds; a name the stated
     * kind does hold - a temporary view a DROP VIEW names while a table of that name also exists - is
     * left to the ordinary path. Nothing else in the schema is consulted: a sequence keeps its own name
     * space (live-verified).
     */
    private void rejectWrongKind(final Schema schema, final String name, final RelationKind specified) {
        if (schema == null || schema.holdsRelation(name, specified)) {
            return;
        }
        final RelationKind found = schema.relationKindOf(name);
        if (found == null) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.objectOfOtherType(found.spelling(), specified.spelling()));
    }

}
