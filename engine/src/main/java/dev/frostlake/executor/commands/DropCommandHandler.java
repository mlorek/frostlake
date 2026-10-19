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
import dev.frostlake.executor.StatementTokens;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.metastore.MaterializedViewValidity;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.RetentionChain;
import dev.frostlake.metastore.SystemDatabase;
import dev.frostlake.metastore.TableShadows;
import dev.frostlake.metastore.TemporaryObjects;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.SecurityObjectKind;
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
import java.util.Locale;
import java.util.Map;

/**
 * Handles DROP statements (DROP TABLE/VIEW/DATABASE/SCHEMA/STREAM/TASK/SEQUENCE/...), extracted from
 * {@link DDLCommandHandler}, which keeps the public dispatch and delegates here. Shared schema-resolution
 * helpers remain on DDLCommandHandler and are reached via the {@code ddl} back-reference.
 */
public class DropCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(DropCommandHandler.class);

    /** The characters and length of the token that names where a dropped table's stage files wait. */
    private static final String STAGE_TOKEN_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int STAGE_TOKEN_LENGTH = 20;

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

    /** The kind of relation a DROP names, or null for a kind outside the relations' shared name space. */
    private static RelationKind relationKindOf(final FrostlakeParser.DropStatementContext ctx) {
        if (ctx.TABLE() != null) {
            return ctx.DYNAMIC() != null ? RelationKind.DYNAMIC_TABLE
                : ctx.ICEBERG() != null || ctx.EVENT() != null ? null : RelationKind.TABLE;
        }
        if (ctx.VIEW() != null) {
            return ctx.MATERIALIZED() != null ? RelationKind.MATERIALIZED_VIEW : RelationKind.VIEW;
        }
        return ctx.STREAM() != null ? RelationKind.STREAM : null;
    }

    /** Whether the statement ends in RESTRICT. */
    private static boolean restricted(final FrostlakeParser.DropStatementContext ctx) {
        return ctx.dropBehavior() != null && ctx.dropBehavior().RESTRICT() != null;
    }

    /**
     * Whether IF EXISTS forgives a failed drop: it forgives the object's absence, never an access-control
     * refusal — dropping an object another role owns is refused with or without it.
     */
    private static boolean forgiven(final boolean ifExists, final RuntimeException failure) {
        return ifExists && !String.valueOf(failure.getMessage()).startsWith(SqlAccessControlError.PREFIX);
    }

    /**
     * The retention a schema's objects keep when they declare none ({@link RetentionChain}), for a dropped object that
     * is not a table. The schema was just resolved for the drop; should it not resolve by these names, the account's
     * default applies.
     */
    private int schemaRetention(final String databaseName, final String schemaName) {
        try {
            final Database database = catalog.getDatabase(databaseName);
            return RetentionChain.effectiveDays(database, database.getSchema(schemaName), null);
        } catch (final RuntimeException unresolved) {
            return RetentionChain.ACCOUNT_DEFAULT_DAYS;
        }
    }

    /**
     * Detach one schema's tables from storage as part of a DROP SCHEMA / DROP DATABASE: snapshot each
     * table's rows into {@code out} (keyed by fully-qualified name) and release its storage from the name, exactly as
     * DROP TABLE does, keeping it for the UNDROP under the key {@code keptOut} notes. Without this the storage entries
     * outlive the drop, so re-creating the same database/schema fails with "Table storage already exists" and a CLONE
     * into the reused name appends to the stale rows instead of replacing them. Buffered writes are discarded so an
     * uncommitted change from this transaction isn't flushed to now-missing storage at commit.
     */
    private void snapshotAndReleaseStorage(final String databaseName, final Schema schema,
                                           final Map<String, List<Row>> out,
                                           final Map<String, List<Row>> hiddenOut,
                                           final Map<String, String> keptOut) {
        final StorageEngine storage = queryExecutor.getStorageEngine();
        TemporaryObjects.releaseSessionTables(schema, storage, databaseName);
        for (final Table table : schema.getTables()) {
            final String fqn = QualifiedName.key(databaseName, schema.getName(), table.getName());
            if (!storage.hasTable(fqn)) {
                continue;
            }
            out.put(fqn, new ArrayList<>(storage.getTableStorage(fqn).scan()));
            // The storage waits with the dropped version, the table's time-travel history in it (live-verified).
            final String kept = storage.keepDropped(fqn);
            if (kept != null) {
                keptOut.put(fqn, kept);
            }
            queryExecutor.resetCopyLoadHistory(fqn, table.isTemporary());
            queryExecutor.getTransactionManager().discardBufferedWritesFor(fqn);
        }
        // The permanent tables temporary ones hid go with the schema, and come back with it.
        for (final Table hidden : schema.getShadowedTables()) {
            final String fqn = QualifiedName.key(databaseName, schema.getName(), hidden.getName());
            queryExecutor.resetCopyLoadHistory(fqn, hidden.isTemporary());
            if (storage.hasShadowedTable(fqn)) {
                hiddenOut.put(fqn, new ArrayList<>(storage.getShadowedTableStorage(fqn).scan()));
                storage.dropShadowedTable(fqn);
            }
        }
    }

    /** Notes on a dropped schema's or database's version where each of its tables' storage waits. */
    private static void keepStorages(final DroppedObject dropped, final Map<String, String> kept) {
        for (final Map.Entry<String, String> table : kept.entrySet()) {
            dropped.keepStorage(table.getKey(), table.getValue());
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
        if (ctx.droppedClass != null) {
            return dropClass(ctx);
        }
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
     * DROP CLASS of a class by its name. No class a statement may drop is modelled: the name resolves the way any
     * schema object's does — a missing database or schema is refused, IF EXISTS or not, and so is a name with too
     * many parts — and then the class is missing by its full path, which IF EXISTS forgives (live-verified).
     */
    private Object dropClass(final FrostlakeParser.DropClassStatementContext ctx) {
        final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        if (ctx.if_exists() != null) {
            ConditionalDdlOutcome.dropSkipped();
            return null;
        }
        throw new RuntimeException(SqlCompilationError.inline("Object type or Class '"
            + SqlIdentifiers.spellAlreadyCanonicalPath(QualifiedName.join(owner.getDatabaseName(), owner.getName(),
                parts[parts.length - 1])) + "' does not exist or not authorized."));
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
     * A session policy is read with these kinds and is modelled: one that is there is dropped.
     */
    private Object dropUnmodelled(final FrostlakeParser.DropClassStatementContext ctx, final UnmodelledDropKind kind) {
        final FrostlakeParser.ObjectNameContext instance = ctx.dropInstanceName().objectName();
        final String[] parts = instance != null ? queryExecutor.resolveObjectNameParts(instance)
            : SqlIdentifiers.canonicalTextParts(ctx.dropInstanceName().getText());
        final String missing;
        if (kind.isSchemaObject()) {
            final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
            final boolean purged = DropPurge.written(ctx.dropBehavior());
            final SecurityObjectKind modelled = SecurityObjectCommandHandler.modelledKind(kind);
            if (modelled != null && new SecurityObjectCommandHandler(catalog, queryExecutor)
                    .dropFound(modelled, owner, parts, purged)) {
                // The kinds read here that are modelled: a session, an authentication or a packages policy that is
                // there is dropped.
                return null;
            }
            final String otherPolicy = modelled == null ? null : owner.policyKindHolding(parts[parts.length - 1]);
            if (otherPolicy != null) {
                // A policy of another kind holds the name, which IF EXISTS does not forgive (live-verified).
                throw new RuntimeException(SqlCompilationError.objectOfOtherType(otherPolicy, modelled.name()));
            }
            final boolean relationKind = kind == UnmodelledDropKind.EXTERNAL_TABLE
                || kind == UnmodelledDropKind.SEMANTIC_VIEW;
            final RelationKind found = relationKind ? owner.relationKindOf(parts[parts.length - 1]) : null;
            if (found != null) {
                // A relation of another kind holds the name, which IF EXISTS does not forgive (live-verified).
                throw new RuntimeException(SqlCompilationError.objectOfOtherType(found.spelling(), kind.name()));
            }
            missing = SqlCompilationError.doesNotExist(kind.noun(), owner.qualifiedName(parts[parts.length - 1]));
        } else if (parts.length > 1) {
            throw new RuntimeException(SqlCompilationError.objectDoesNotExist());
        } else if (kind == UnmodelledDropKind.SHARE) {
            missing = SqlCompilationError.doesNotExistAsSpelled(kind.noun(), shareName(catalog.getAccountLocator(),
                parts[0]));
        } else {
            missing = SqlCompilationError.doesNotExist(kind.noun(), parts[0]);
        }
        if (ctx.if_exists() != null) {
            if (DropPurge.written(ctx.dropBehavior())) {
                throw DropPurge.refusal(parts);
            }
            ConditionalDdlOutcome.dropSkipped();
            return null;
        }
        throw new RuntimeException(missing);
    }

    /**
     * A share as a refusal names it: with the account it belongs to, the two as one quoted name —
     * {@code "ABC12345.S"} (live-verified).
     *
     * @param account the account's locator
     * @param share the share's canonical name
     * @return the name between its quotes
     */
    public static String shareName(final String account, final String share) {
        return "\"" + account + "." + share + "\"";
    }

    public Object handleDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        final boolean ifExists = ctx.if_exists() != null;
        final boolean purged = DropPurge.written(ctx.dropBehavior());
        if (purged && namesAnAccountScopedKind(ctx) && ctx.DATABASE() == null
                && (ifExists || holdsAccountScoped(ctx))) {
            // A warehouse, a compute pool, a user and a role read the word as the kinds of a schema do: it is refused
            // once the name is there to drop or IF EXISTS forgives its absence, and a missing one is refused as
            // missing (live-verified).
            throw DropPurge.refusal(dropNameParts(ctx));
        }
        if (ctx.ALERT() != null) {
            return new AlertCommandHandler(queryExecutor).drop(ctx, ifExists);
        }
        RoutineSignatureForm.requireTypesOnly(ctx.dataTypeList());
        requireContainers(ctx);
        if (ctx.ICEBERG() != null) {
            rejectOtherThanIceberg(dropNameParts(ctx));
        }
        if (purged && ctx.FORMAT() == null && ctx.CONTACT() == null
                && (ctx.DATABASE() != null || !namesAnAccountScopedKind(ctx))) {
            final String[] named = dropNameParts(ctx);
            final RelationKind relation = relationKindOf(ctx);
            if (relation != null) {
                // A relation of another kind under the name is refused before the PURGE is.
                rejectWrongKind(catalog.requireOwningSchema(QualifiedName.of(named)), named[named.length - 1], relation);
            }
            if (ifExists || DropPurge.holds(ctx, named, queryExecutor, catalog)) {
                throw DropPurge.refusal(named);
            }
        }

        try {
            if (ctx.DATABASE() != null) {
                final String dbName = dropNameText(ctx);
                // Refused before IF EXISTS could forgive it: the SNOWFLAKE database is always there.
                SystemDatabase.refuseChange(dbName);
                try {
                    checkDrop(SecurableObjectType.DATABASE, dbName);
                    queryExecutor.requireOwnership("DATABASE", dropNameParts(ctx), null);
                    final Database droppedDb = catalog.databaseExact(dbName);
                    // A database holding a table another table references stays under RESTRICT. Otherwise the
                    // foreign keys tables of other databases hold on its tables go with it.
                    final ForeignKeyDependents dependents = new ForeignKeyDependents(catalog);
                    if (restricted(ctx) && dependents.referencesInto(droppedDb.getName())) {
                        throw new DropDependencyRefusal("database");
                    }
                    dependents.releaseFromOutside(droppedDb.getName());
                    DroppedObject dropped = null;
                    if (droppedDb != null) {
                        // Snapshot every table's rows and RELEASE its storage (as DROP TABLE does), so the
                        // database name can be created again without colliding with — or inheriting — the
                        // dropped tables' storage. UNDROP DATABASE restores both metadata and rows.
                        final Map<String, List<Row>> tableRows = new LinkedHashMap<>();
                        final Map<String, List<Row>> hiddenTableRows = new LinkedHashMap<>();
                        final Map<String, String> kept = new LinkedHashMap<>();
                        for (final Schema droppedSchema : droppedDb.getAllSchemas()) {
                            snapshotAndReleaseStorage(dbName, droppedSchema, tableRows, hiddenTableRows, kept);
                        }
                        // Its tables' and stages' files wait for an UNDROP under a token of the version's own.
                        dropped = new DroppedObject(droppedDb, tableRows, hiddenTableRows,
                            RetentionChain.effectiveDays(droppedDb, null, null),
                            StatementTokens.draw(STAGE_TOKEN_ALPHABET, STAGE_TOKEN_LENGTH));
                        keepStorages(dropped, kept);
                        catalog.recordDropped("DATABASE:" + dbName, dropped);
                    }
                    catalog.dropDatabase(dbName, false);
                    if (dropped != null) {
                        queryExecutor.setAsideStages("DATABASE:" + dbName, dropped);
                    }
                    if (streamManager != null) {
                        streamManager.onDatabaseDropped(dbName);
                    }
                    logger.trace("Dropped database: {}", dbName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e) || e instanceof DropDependencyRefusal) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Database does not exist (IF EXISTS): {}", dbName);
                }
            } else if (ctx.SCHEMA() != null) {
                final String schemaName = dropNameText(ctx);
                // Refused before IF EXISTS forgives anything: a schema name has at most two parts.
                final String[] parts = catalog.withoutAccount(dropNameParts(ctx), 2);
                final String inDatabase = parts.length == 1 ? catalog.getCurrentDatabase() : parts[0];
                if (SystemDatabase.is(inDatabase)
                        && catalog.databaseExact(inDatabase).hasSchemaExact(parts[parts.length - 1])) {
                    // The SNOWFLAKE database's schemas are its own (SystemDatabase).
                    SystemDatabase.refuseChange(inDatabase);
                }

                try {
                    checkDrop(SecurableObjectType.SCHEMA, schemaName);
                    queryExecutor.requireOwnership("SCHEMA", parts, null);
                    // Snapshot schema metadata for UNDROP (its tables' storage survives a DROP SCHEMA).
                    final String snapDbName = parts.length == 1 ? catalog.getCurrentDatabase() : parts[0];
                    final String snapSchemaName = parts.length == 1 ? parts[0] : parts[1];
                    final Database snapDb = catalog.databaseExact(snapDbName);
                    final Schema snapSchema = snapDb.schemaExact(snapSchemaName);
                    // A schema holding a table another table references stays under RESTRICT. Otherwise the drop
                    // takes the foreign keys tables outside it hold with it.
                    final ForeignKeyDependents dependents = new ForeignKeyDependents(catalog);
                    if (restricted(ctx) && dependents.referencesInto(snapDbName, snapSchema)) {
                        throw new DropDependencyRefusal("schema");
                    }
                    dependents.releaseFromOutside(snapDbName, snapSchema);
                    final String droppedKey = "SCHEMA:" + QualifiedName.key(snapDbName, snapSchemaName);
                    DroppedObject dropped = null;
                    if (snapSchema != null) {
                        final Map<String, List<Row>> tableRows = new LinkedHashMap<>();
                        final Map<String, List<Row>> hiddenTableRows = new LinkedHashMap<>();
                        final Map<String, String> kept = new LinkedHashMap<>();
                        snapshotAndReleaseStorage(snapDbName, snapSchema, tableRows, hiddenTableRows, kept);
                        // Its tables' and stages' files wait for an UNDROP under a token of the version's own.
                        dropped = new DroppedObject(snapSchema, tableRows, hiddenTableRows,
                            RetentionChain.effectiveDays(snapDb, snapSchema, null),
                            StatementTokens.draw(STAGE_TOKEN_ALPHABET, STAGE_TOKEN_LENGTH));
                        keepStorages(dropped, kept);
                        catalog.recordDropped(droppedKey, dropped);
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
                    if (dropped != null) {
                        queryExecutor.setAsideStages(droppedKey, dropped);
                    }
                    logger.trace("Dropped schema: {}", schemaName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e) || e instanceof DropDependencyRefusal) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Schema does not exist (IF EXISTS): {}", schemaName);
                }
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
                final String qn = dropNameText(ctx);
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    rejectWrongKind(schema, parts[parts.length - 1], RelationKind.DYNAMIC_TABLE);
                    checkDrop(SecurableObjectType.DYNAMIC_TABLE, qn);
                    final DynamicTable droppedTable = schema.getDynamicTable(parts[parts.length - 1]);
                    schema.dropDynamicTable(parts[parts.length - 1]);
                    // Kept for UNDROP DYNAMIC TABLE, as a dropped table is.
                    final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
                    catalog.recordDropped("DYNAMIC TABLE:" + QualifiedName.key(databaseName, schema.getName(),
                        droppedTable.getName()), new DroppedObject(droppedTable, (List<Row>) null,
                        droppedTable.getDataRetentionDays()));
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
                    if (ctx.EVENT() != null) {
                        requireEventTable(schema, databaseName, tableName);
                    } else if (ctx.ICEBERG() != null && schema.relationKindOf(tableName) == null) {
                        // A missing one is named by the kind the statement asks for.
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Iceberg table",
                            QualifiedName.join(databaseName, schema.getName(), tableName)));
                    }

                    // INFORMATION_SCHEMA is read-only, and its views are found before any kind
                    // check — so even DROP TABLE on one refuses naming the VIEW (live-verified).
                    if ("INFORMATION_SCHEMA".equals(schema.getName()) && schema.hasView(tableName)) {
                        throw new RuntimeException(
                            SqlAccessControlError.insufficientPrivileges("view", tableName.toUpperCase(Locale.ROOT)));
                    }

                    rejectWrongKind(schema, tableName, RelationKind.TABLE);
                    final Table table = catalog.resolveTable(QualifiedName.of(parts));
                    checkDrop(SecurableObjectType.TABLE, qualifiedName);
                    queryExecutor.requireOwnership("TABLE", parts, null);

                    // A table another table's foreign key references stays under RESTRICT; otherwise the drop
                    // takes those foreign keys with it.
                    final ForeignKeyDependents dependents = new ForeignKeyDependents(catalog);
                    if (restricted(ctx) && dependents.references(databaseName, schema, table)) {
                        throw new DropDependencyRefusal("table");
                    }
                    dependents.release(databaseName, schema, table);

                    final String fullyQualifiedName = QualifiedName.key(databaseName, schema.getName(), tableName);
                    // Snapshot table metadata + rows for UNDROP before removing the storage.
                    final List<Row> snapshotRows =
                        new ArrayList<>(queryExecutor.getStorageEngine().getTableStorage(fullyQualifiedName).scan());
                    // Its stage's files wait for an UNDROP under a token of the dropped version's own, drawn the same
                    // way when the log replays the DROP (StatementTokens).
                    final DroppedObject dropped = new DroppedObject(table, snapshotRows,
                        RetentionChain.effectiveDays(catalog.getDatabase(databaseName), schema, table),
                        StatementTokens.draw(STAGE_TOKEN_ALPHABET, STAGE_TOKEN_LENGTH));
                    catalog.recordDropped("TABLE:" + fullyQualifiedName, dropped);

                    schema.dropTable(table.getName());
                    // Another session's open transaction that wrote the table commits into the dropped version, which
                    // an UNDROP brings back with those changes (live-verified); the storage waits aside for it.
                    final String setAside =
                        queryExecutor.getTransactionManager().setAsideIfWritten(fullyQualifiedName);
                    if (setAside == null) {
                        // Otherwise it waits with the version, the table's time-travel history in it, which an
                        // UNDROP brings back too (live-verified).
                        final String kept = queryExecutor.getStorageEngine().keepDropped(fullyQualifiedName);
                        if (kept != null) {
                            dropped.keepStorage(fullyQualifiedName, kept);
                        }
                    } else {
                        dropped.setStorageSetAside(setAside);
                    }
                    if (!table.isTemporary()) {
                        MaterializedViewValidity.baseTableDropped(catalog, databaseName, schema.getName(),
                            table.getName());
                    }
                    // Its load history goes and its stage waits for an UNDROP. Both are the dropped table's own: a
                    // permanent table a temporary one hid keeps its files and its history.
                    queryExecutor.resetCopyLoadHistory(fullyQualifiedName, table.isTemporary());
                    queryExecutor.setAsideStages("TABLE:" + fullyQualifiedName, dropped);
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
                    // IF EXISTS forgives absence, never an access-control refusal, never a name
                    // another kind holds and never a dependency RESTRICT stops at.
                    if (!forgiven(ifExists, e) || SqlCompilationError.isWrongObjectType(e.getMessage())
                            || e instanceof DropDependencyRefusal) {
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

                    schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    viewName = parts[parts.length - 1];

                    rejectWrongKind(schema, viewName, RelationKind.VIEW);
                    checkDrop(SecurableObjectType.VIEW, qualifiedName);
                    queryExecutor.requireOwnership("VIEW", parts, null);
                    // A name that differs only in case is another view (live-verified).
                    schema.dropViewExact(viewName);
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

                    schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    mvName = parts[parts.length - 1];

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
                    // A quoted name in another case is another stream, which is not there (live-verified).
                    schema.dropStreamExact(streamName);
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
                    // A quoted name in another case is another task, which is not there (live-verified).
                    schema.dropTaskExact(taskName);
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
                    schema.dropPipeExact(QualifiedName.parse(pipeName).last());
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
                    schema.dropSequenceExact(sequenceName);
                    logger.trace("Dropped sequence: {}", sequenceName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Sequence does not exist (IF EXISTS): {}", sequenceName);
                }
            } else if (ctx.CORTEX() != null) {
                final String serviceName = dropNameText(ctx);
                final String[] serviceParts = dropNameParts(ctx);
                // A quoted name is the service of exactly that spelling; IF EXISTS forgives only its absence.
                final Schema serviceSchema = catalog.requireOwningSchema(QualifiedName.of(serviceParts));
                final String ownName = serviceParts[serviceParts.length - 1];
                if (ifExists && !serviceSchema.hasCortexSearchServiceExact(ownName)) {
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Cortex search service does not exist (IF EXISTS): {}", serviceName);
                } else {
                    serviceSchema.dropCortexSearchServiceExact(ownName);
                    logger.trace("Dropped Cortex search service: {}", serviceName);
                }
            } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
                final String poolName = dropNameText(ctx);
                try {
                    catalog.dropComputePoolExact(poolName);
                    logger.trace("Dropped compute pool: {}", poolName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Compute pool does not exist (IF EXISTS): {}", poolName);
                }
            } else if (ctx.WAREHOUSE() != null) {
                final String warehouseName = dropNameText(ctx);

                try {
                    catalog.dropWarehouseExact(warehouseName);
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
                    catalog.dropStageExact(stageName);
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
                    catalog.dropFileFormatExact(fileFormatName);
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

                    schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    functionName = parts[parts.length - 1];

                    // The written signature names one overload, an empty one the overload taking no
                    // arguments: DROP FUNCTION f() over f(INT) and f(DATE) drops nothing (live-verified).
                    final List<DataType> argumentTypes = new ArrayList<>();
                    if (ctx.dataTypeList() != null) {
                        for (final FrostlakeParser.DataTypeNameContext dtCtx : ctx.dataTypeList().dataTypeName()) {
                            argumentTypes.add(RoutineSignatureForm.writtenType(columnParser, dtCtx));
                        }
                    }
                    queryExecutor.requireFunctionOwnership(schema, functionName, argumentTypes);
                    schema.dropFunctionByWrittenSignature(functionName, argumentTypes);
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

                    schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    procedureName = parts[parts.length - 1];

                    // The written signature names one overload, an empty one the overload taking no
                    // arguments, as a function's does.
                    final List<DataType> argumentTypes = new ArrayList<>();
                    if (ctx.dataTypeList() != null) {
                        for (final FrostlakeParser.DataTypeNameContext dtCtx : ctx.dataTypeList().dataTypeName()) {
                            argumentTypes.add(RoutineSignatureForm.writtenType(columnParser, dtCtx));
                        }
                    }
                    queryExecutor.requireProcedureOwnership(schema, procedureName, argumentTypes);
                    schema.dropProcedureByWrittenSignature(procedureName, argumentTypes);
                    logger.trace("Dropped procedure: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Procedure does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.USER() != null) {
                final String userName = dropNameText(ctx);

                try {
                    final String held = catalog.hasUserExact(userName)
                        ? catalog.getUserExact(userName).getName() : null;
                    catalog.dropUserExact(userName);
                    if (held != null) {
                        SecurityObjectCommandHandler.forgetUser(catalog, held);
                    }
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
                    final Tag droppedTag = catalog.getTagExact(tagName);
                    catalog.dropTagExact(tagName);
                    final String[] tagParts = dropNameParts(ctx);
                    final String tagDb = tagParts.length == 3 ? tagParts[0] : catalog.getCurrentDatabase();
                    final String tagSchema = tagParts.length == 3 ? tagParts[1]
                        : tagParts.length == 2 ? tagParts[0] : catalog.getCurrentSchema();
                    // Kept under its exact canonical name, as UNDROP TAG looks it up.
                    catalog.recordDropped("TAG:" + QualifiedName.key(tagDb, tagSchema, droppedTag.getName()),
                        new DroppedObject(droppedTag, (List<Row>) null, schemaRetention(tagDb, tagSchema)));
                    logger.trace("Dropped tag: {}", tagName);
                } catch (final RuntimeException e) {
                    if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Tag does not exist (IF EXISTS): {}", tagName);
                }
            } else if (ctx.MASKING() != null && ctx.POLICY() != null && ctx.ROW() == null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    checkDrop(SecurableObjectType.MASKING_POLICY, dropNameText(ctx));
                    queryExecutor.requireOwnership("MASKING_POLICY", parts, null);
                    // Named exactly: a quoted name in another case is another policy, which is not there, in
                    // the "<Kind> '<QUALIFIED>' does not exist or not authorized." family (live-verified).
                    if (schema.getMaskingPolicyExact(parts[parts.length - 1]) == null) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy",
                            schema.qualifiedName(parts[parts.length - 1])));
                    }
                    if (catalog.isPolicyInUse(parts[parts.length - 1], true)) {
                        throw new RuntimeException("Policy " + parts[parts.length - 1].toUpperCase(Locale.ROOT)
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropMaskingPolicy(parts[parts.length - 1]);
                    logger.trace("Dropped masking policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    checkDrop(SecurableObjectType.ROW_ACCESS_POLICY, dropNameText(ctx));
                    queryExecutor.requireOwnership("ROW_ACCESS_POLICY", parts, null);
                    // Named exactly, as a masking policy is (live-verified).
                    if (schema.getRowAccessPolicyExact(parts[parts.length - 1]) == null) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy",
                            schema.qualifiedName(parts[parts.length - 1])));
                    }
                    if (catalog.isPolicyInUse(parts[parts.length - 1], false)) {
                        throw new RuntimeException("Policy " + parts[parts.length - 1].toUpperCase(Locale.ROOT)
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropRowAccessPolicy(parts[parts.length - 1]);
                    logger.trace("Dropped row access policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.JOIN() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    final String policyName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
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
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    // Named exactly, as a masking policy is (live-verified).
                    final String policyName = parts[parts.length - 1];
                    if (schema.getAggregationPolicyExact(policyName) == null) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Aggregation policy",
                            schema.qualifiedName(policyName)));
                    }
                    if (catalog.isAggregationPolicyInUse(policyName)) {
                        throw new RuntimeException(SqlCompilationError.PREFIX + " Policy "
                            + policyName.toUpperCase(Locale.ROOT)
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropAggregationPolicy(policyName);
                    logger.trace("Dropped aggregation policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.PROJECTION() != null && ctx.POLICY() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    // Named exactly, as a masking policy is (live-verified).
                    final String policyName = parts[parts.length - 1];
                    if (schema.getProjectionPolicyExact(policyName) == null) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Projection policy",
                            schema.qualifiedName(policyName)));
                    }
                    if (catalog.isProjectionPolicyInUse(policyName)) {
                        // Attached to a column: live refuses the drop rather than detaching it.
                        throw new RuntimeException(SqlCompilationError.PREFIX + " Policy "
                            + policyName.toUpperCase(Locale.ROOT)
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropProjectionPolicy(policyName);
                    logger.trace("Dropped projection policy: {}", dropNameText(ctx));
                } catch (final RuntimeException e) { if (!forgiven(ifExists, e)) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.CONTACT() != null) {
                final String[] parts = dropNameParts(ctx);
                try {
                    final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
                    final String contactName = parts[parts.length - 1].toUpperCase(Locale.ROOT);
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
    /**
     * DROP EVENT TABLE drops an event table alone: a table of another kind and a view are "Object found is of type
     * 'TABLE', not specified type 'EVENT_TABLE'.", and a missing name is refused as an event table, where DROP
     * TABLE drops an event table as it drops any table (live-verified).
     */
    /** Whether the warehouse, compute pool, user or role a DROP names is there. */
    private boolean holdsAccountScoped(final FrostlakeParser.DropStatementContext ctx) {
        final String name = dropNameText(ctx);
        if (ctx.WAREHOUSE() != null) {
            return catalog.hasWarehouseExact(name);
        }
        if (ctx.POOL() != null) {
            return catalog.hasComputePoolExact(name);
        }
        return ctx.USER() != null ? catalog.hasUserExact(name) : catalog.roleExistsExact(name);
    }

    /**
     * Another kind under the name a DROP ICEBERG TABLE gives is refused before anything else — before a PURGE, and
     * whether or not IF EXISTS is written: {@code Object found is of type 'VIEW', not specified type
     * 'ICEBERG_TABLE'.}, and so for a table that is no Iceberg table, an event table and a stream (live-verified).
     */
    private void rejectOtherThanIceberg(final String[] parts) {
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        if (schema.hasTable(name)) {
            final Table table = schema.getTable(name);
            if (table.getIcebergMetadata() == null) {
                throw new RuntimeException(SqlCompilationError.objectOfOtherType(
                    table.isEventTable() ? "EVENT_TABLE" : "TABLE", "ICEBERG_TABLE"));
            }
            return;
        }
        final RelationKind found = schema.relationKindOf(name);
        if (found != null) {
            throw new RuntimeException(SqlCompilationError.objectOfOtherType(found.spelling(), "ICEBERG_TABLE"));
        }
    }

    private static void requireEventTable(final Schema schema, final String databaseName, final String tableName) {
        if (schema.hasTable(tableName)) {
            if (!schema.getTable(tableName).isEventTable()) {
                throw new RuntimeException(SqlCompilationError.objectOfOtherType("TABLE", "EVENT_TABLE"));
            }
            return;
        }
        final RelationKind found = schema.relationKindOf(tableName);
        if (found != null) {
            throw new RuntimeException(SqlCompilationError.objectOfOtherType(found.spelling(), "EVENT_TABLE"));
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("Event table",
            QualifiedName.join(databaseName, schema.getName(), tableName)));
    }

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
