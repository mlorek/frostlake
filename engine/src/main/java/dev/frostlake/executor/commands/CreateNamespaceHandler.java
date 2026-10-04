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

import dev.frostlake.executor.ClonePoint;
import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.TransientRetentionLimit;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.FutureGrants;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.SystemDatabase;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.StorageEngine;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the namespace CREATE shapes — DATABASE and SCHEMA (including CLONE) — extracted from
 * {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. The clone-data helpers
 * ({@code cloneSchemaData}/{@code cloneDatabaseData}) and other shared helpers stay on the parent and are
 * reached via the {@code ddl} back-reference.
 */
public class CreateNamespaceHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateNamespaceHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    CreateNamespaceHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
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
     * Release the storage of every table in a schema being discarded by CREATE OR REPLACE. The metadata is
     * dropped outright (no UNDROP record is kept), so its rows go with it: leaving the storage behind would
     * make the replacement inherit the old rows — a CLONE appends into them — or fail with "Table storage
     * already exists" when a table of the same name is created again.
     */
    private void releaseSchemaStorage(final String databaseName, final Schema schema) {
        final StorageEngine storage = queryExecutor.getStorageEngine();
        for (final Table table : schema.getTables()) {
            final String fqn = QualifiedName.key(databaseName, schema.getName(), table.getName());
            if (storage.hasTable(fqn)) {
                storage.dropTable(fqn);
                queryExecutor.getTransactionManager().discardBufferedWritesFor(fqn);
            }
        }
        for (final Table hidden : schema.getShadowedTables()) {
            storage.dropShadowedTable(QualifiedName.key(databaseName, schema.getName(), hidden.getName()));
        }
    }

    public Object handleCreateDatabase(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String dbName = getText(ctx.identifier(0));
        // The SNOWFLAKE database is neither cloned nor replaced (SystemDatabase).
        if (ctx.CLONE() != null) {
            SystemDatabase.refuseChange(getText(ctx.identifier(1)));
        }
        if (ctx.or_replace() != null) {
            SystemDatabase.refuseChange(dbName);
        }
        final NamespaceProperties properties = NamespaceProperties.ofDatabase(ctx.databaseProperty());
        // AT | BEFORE is resolved, and the source required to have existed then, before anything changes, so a
        // refused point leaves even a database it would replace in place.
        final ClonePoint point = ctx.CLONE() != null && ctx.timeTravelClause() != null
            ? clonePoint(ctx, catalog.databaseExact(getText(ctx.identifier(1)))) : null;
        if (ctx.CLONE() != null) {
            requireCloneableTransience(catalog.databaseExact(getText(ctx.identifier(1))).isTransientObject(),
                ctx.TRANSIENT() != null);
        }
        if (ctx.or_replace() != null) {
            // Replacing a database needs OWNERSHIP of it.
            queryExecutor.requireOwnership("DATABASE", new String[] {dbName}, null);
            try {
                final Database replaced = catalog.databaseExact(dbName);
                if (replaced != null) {
                    for (final Schema replacedSchema : replaced.getAllSchemas()) {
                        releaseSchemaStorage(dbName, replacedSchema);
                    }
                }
                catalog.dropDatabase(dbName, true);
                if (ddl.getStreamManager() != null) {
                    ddl.getStreamManager().onDatabaseDropped(dbName);
                }
            } catch (final RuntimeException ignored) {}
        }
        boolean made = false;
        try {
            if (ctx.CLONE() != null) {
                final String sourceDbName = getText(ctx.identifier(1));
                catalog.cloneDatabase(sourceDbName, dbName);
                made = true;
                ddl.cloneDatabaseData(sourceDbName, dbName, point);
                logger.trace("Cloned database: {} from {}", dbName, sourceDbName);
            } else {
                catalog.createDatabase(dbName);
                made = true;
                logger.trace("Created database: {}", dbName);
            }

            final Database db = catalog.databaseExact(dbName);
            final String written = properties.retention();
            if (written != null) {
                if (written.startsWith("-")) {
                    throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                        written, "DATA_RETENTION_TIME_IN_DAYS"));
                }
                TransientRetentionLimit.requireWithinAccountLimit(written);
                TransientRetentionLimit.requireWithinTransientLimit(
                    ctx.TRANSIENT() != null, written);
                db.setDataRetentionTimeInDays(Integer.valueOf(written));
            }
            if (ctx.TRANSIENT() != null) {
                db.setTransientObject(true);
                // PUBLIC is created with the database, so it exists before the modifier is read and
                // has to be marked here. Live reports it TRANSIENT like every other schema inside.
                for (final Schema inherited : db.getAllSchemas()) {
                    inherited.setTransientObject(true);
                    // And every table a CLONE brought in with them.
                    markTablesTransient(inherited);
                }
            }
            final String comment = ddl.extractCommentFromList(properties.comments());
            if (comment != null) db.setComment(comment);
            properties.applyParameters(db.getParameters());
            for (final FrostlakeParser.TagListContext tags : properties.tags()) {
                InlineTags.apply(db, tags, queryExecutor);
            }
            // Snowflake activates a newly created database: it becomes the session's current
            // database, with PUBLIC as the current schema (live-verified).
            catalog.useDatabase(dbName);
        } catch (final RuntimeException e) {
            if (made) {
                // A property refused after the database was made leaves no database behind.
                discardDatabase(dbName);
            }
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Database already exists (IF NOT EXISTS): {}", dbName);
        }
        return null;
    }

    /**
     * A CLONE's AT | BEFORE point, refused when the database or schema being cloned did not exist yet then. The
     * documentation says such a clone returns an error naming the object; the account's usual "does not exist"
     * wording is used for it here, not verified against a live account.
     *
     * @param ctx    the statement
     * @param source the database or schema being cloned
     * @return the point
     */
    private ClonePoint clonePoint(final FrostlakeParser.CreateStatementContext ctx, final SqlObject source) {
        final ClonePoint point = queryExecutor.clonePoint(ctx.timeTravelClause());
        if (DDLCommandHandler.createdAfter(source, point)) {
            final String spelled = source instanceof Schema && ((Schema) source).getDatabaseName() != null
                ? QualifiedName.join(((Schema) source).getDatabaseName(), source.getName()) : source.getName();
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Object", spelled));
        }
        return point;
    }

    /** The schema a CREATE SCHEMA ... CLONE copies: a bare name in the session's database. */
    private Schema cloneSourceSchema(final FrostlakeParser.CreateStatementContext ctx) {
        final String[] sourceParts = qualifiedNameParts(ctx.qualifiedName(1));
        final String database = sourceParts.length == 2 ? sourceParts[0] : catalog.getCurrentDatabase();
        if (database == null) {
            throw NoCurrentDatabaseRefusal.forStatement();
        }
        return catalog.databaseExact(database).schemaExact(sourceParts[sourceParts.length - 1]);
    }

    /**
     * A transient database or schema is cloned only to a transient one, as a transient table is (live-verified for
     * tables, where the sentence is the account's; the same rule is taken for its containers). A permanent source may
     * be cloned to either.
     *
     * @param sourceTransient whether the source is transient
     * @param cloneTransient  whether the clone will be
     */
    private static void requireCloneableTransience(final boolean sourceTransient, final boolean cloneTransient) {
        if (sourceTransient && !cloneTransient) {
            throw new RuntimeException(SqlCompilationError.of(
                "Transient object cannot be cloned to a permanent object."));
        }
    }

    /**
     * Every table in a transient schema is transient (documented), so the tables a CLONE brings into one become
     * transient with it, as a table created there is. A temporary table stays temporary, and a hybrid table, an
     * Iceberg table and an event table keep their kind.
     */
    private static void markTablesTransient(final Schema schema) {
        final List<Table> tables = new ArrayList<>(schema.getTables());
        tables.addAll(schema.getShadowedTables());
        for (final Table table : tables) {
            if (!table.isTemporary() && !table.isHybrid() && table.getIcebergMetadata() == null
                    && !table.isEventTable()) {
                table.markTransient();
            }
        }
        for (final DynamicTable dynamicTable : schema.getDynamicTables()) {
            dynamicTable.setTransient(true);
        }
    }

    /** Removes a database this statement made, with its tables' storage. */
    private void discardDatabase(final String dbName) {
        final Database made = catalog.databaseExact(dbName);
        for (final Schema schema : made.getAllSchemas()) {
            releaseSchemaStorage(dbName, schema);
        }
        catalog.dropDatabase(dbName, true);
    }

    public Object handleCreateSchema(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        rejectUnsupportedSchemaModifier(ctx);
        final NamespaceProperties properties = NamespaceProperties.ofSchema(ctx.schemaProperty());
        final String schemaName = getText(ctx.qualifiedName(0));
        // A schema name has at most two parts (db..s counts three): live refuses a longer one with its
        // generic sentence, IF NOT EXISTS or not, and reads a leading locator of this account.
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 2);
        // AT | BEFORE is resolved before anything changes, as a database clone's is.
        final ClonePoint point = ctx.CLONE() != null && ctx.timeTravelClause() != null
            ? clonePoint(ctx, cloneSourceSchema(ctx)) : null;
        // A schema is contained by a database; creating one needs CREATE SCHEMA on that database.
        final String containerDb = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
        if (SystemDatabase.is(containerDb) && !(ifNotExists && ctx.or_replace() == null
                && catalog.databaseExact(containerDb).hasSchemaExact(parts[parts.length - 1]))) {
            // No schema is created, replaced or cloned into the SNOWFLAKE database (SystemDatabase); IF NOT EXISTS
            // over one of its own stays the no-op it is.
            SystemDatabase.refuseChange(containerDb);
        }
        if (ctx.CLONE() != null) {
            // A schema in a transient database is transient too, so the clone may be transient by its container.
            requireCloneableTransience(cloneSourceSchema(ctx).isTransientObject(), ctx.TRANSIENT() != null
                || (containerDb != null && catalog.databaseExact(containerDb).isTransientObject()));
        }
        if (containerDb != null) {
            ddl.checkCreatePrivilege(Privilege.CREATE_SCHEMA, ContainerType.DATABASE, containerDb);
        }
        if (ctx.or_replace() != null) {
            // Replacing a schema needs OWNERSHIP of it.
            queryExecutor.requireOwnership("SCHEMA", parts, null);
            try {
                final String dbN = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
                final String scN = parts.length == 2 ? parts[1] : parts[0];
                if (dbN != null) {
                    final Schema replacedSchema = catalog.databaseExact(dbN).schemaExact(scN);
                    releaseSchemaStorage(dbN, replacedSchema);
                    catalog.databaseExact(dbN).dropSchema(replacedSchema.getName(), true);
                    if (ddl.getStreamManager() != null) {
                        ddl.getStreamManager().onSchemaDropped(dbN, scN);
                    }
                }
            } catch (final RuntimeException ignored) {}
        }
        try {
            final String containerName = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
            // A SCHEMA answers existence FIRST: over an existing schema, IF NOT EXISTS succeeds before any
            // option is judged (an invalid retention over an existing schema is "already exists" on the
            // account).
            if (ifNotExists && ctx.or_replace() == null && ctx.CLONE() == null && containerName != null
                    && catalog.databaseExact(containerName).hasSchemaExact(parts.length == 2 ? parts[1] : parts[0])) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            final Database container = containerName == null ? null : catalog.databaseExact(containerName);
            final boolean transientSchema = ctx.TRANSIENT() != null
                || (container != null && container.isTransientObject());
            // Over a NEW name the options are judged before anything is made, so a refused CREATE leaves no
            // schema behind.
            final Integer retention = requestedRetention(properties.retention(), transientSchema);
            final Schema schema;

            if (ctx.CLONE() != null) {
                final String sourceSchemaName = getText(ctx.qualifiedName(1));
                final String[] sourceParts = qualifiedNameParts(ctx.qualifiedName(1));

                final String sourceDbName;
                final String sourceSchema;
                final String targetDbName;
                final String targetSchema;

                if (parts.length == 1) {
                    if (catalog.getCurrentDatabase() == null) {
                        throw NoCurrentDatabaseRefusal.forStatement();
                    }
                    final Database db = catalog.databaseExact(catalog.getCurrentDatabase());

                    if (sourceParts.length == 1) {
                        schema = db.cloneSchema(sourceParts[0], parts[0]);
                        sourceDbName = catalog.getCurrentDatabase();
                        sourceSchema = sourceParts[0];
                        targetDbName = sourceDbName;
                        targetSchema = parts[0];
                    } else {
                        throw new RuntimeException("Source schema must be in current database when target is unqualified");
                    }
                } else if (parts.length == 2) {
                    final Database db = catalog.databaseExact(parts[0]);

                    if (sourceParts.length == 1) {
                        final Database sourceDb = catalog.databaseExact(catalog.getCurrentDatabase());
                        schema = sourceDb.cloneSchemaTo(sourceParts[0], db, parts[1]);
                        sourceDbName = catalog.getCurrentDatabase();
                        sourceSchema = sourceParts[0];
                        targetDbName = parts[0];
                        targetSchema = parts[1];
                    } else if (sourceParts.length == 2) {
                        final Database sourceDb = catalog.databaseExact(sourceParts[0]);
                        schema = sourceDb.cloneSchemaTo(sourceParts[1], db, parts[1]);
                        sourceDbName = sourceParts[0];
                        sourceSchema = sourceParts[1];
                        targetDbName = parts[0];
                        targetSchema = parts[1];
                    } else {
                        throw new RuntimeException("Invalid source schema name: " + sourceSchemaName);
                    }
                } else {
                    throw new RuntimeException("Invalid schema name: " + schemaName);
                }
                try {
                    ddl.cloneSchemaData(sourceDbName, sourceSchema, targetDbName, targetSchema, point);
                } catch (final RuntimeException refused) {
                    // A point refused while the rows are read leaves no half-made schema behind.
                    releaseSchemaStorage(targetDbName, schema);
                    catalog.databaseExact(targetDbName).dropSchema(schema.getName(), true);
                    throw refused;
                }
                // The clone's members are granted what the source's are; the clone itself nothing (GrantNames).
                GrantNames.created(catalog, "SCHEMA", QualifiedName.join(schema.getDatabaseName(), schema.getName()));
                GrantNames.containerCloned(catalog, new String[] {sourceDbName, sourceSchema},
                    new String[] {schema.getDatabaseName(), schema.getName()});
                // The cloning role owns the clone, as it would a schema it created; the clone's children keep their
                // owners, except that the role that clones a pipe owns the clone (documented).
                schema.setOwner(FutureGrants.ownerOfNewInDatabase(catalog, "SCHEMA",
                    schema.getDatabaseName(), schema.getName()));
                for (final Pipe pipe : schema.getPipes()) {
                    pipe.setOwner(catalog.currentRoleForOwner());
                }
                logger.trace("Cloned schema: {} from {}", schemaName, sourceSchemaName);
            } else {
                if (parts.length == 1) {
                    if (catalog.getCurrentDatabase() == null) {
                        throw NoCurrentDatabaseRefusal.forStatement();
                    }
                    schema = new Schema(parts[0]);
                    catalog.databaseExact(catalog.getCurrentDatabase()).addSchema(schema);
                    schema.setOwner(FutureGrants.ownerOfNewInDatabase(catalog, "SCHEMA",
                        schema.getDatabaseName(), schema.getName()));
                } else if (parts.length == 2) {
                    schema = new Schema(parts[1]);
                    final Database target = catalog.databaseExact(parts[0]);
                    if (target.hasSchemaExact(parts[1])) {
                        // A qualified name is echoed qualified: Object 'DB."ss"' already exists. (live-verified)
                        throw new RuntimeException(SqlCompilationError.of("Object '" + SqlIdentifiers.spellCanonical(parts[0])
                            + "." + SqlIdentifiers.spellCanonical(parts[1]) + "' already exists."));
                    }
                    target.addSchema(schema);
                    schema.setOwner(FutureGrants.ownerOfNewInDatabase(catalog, "SCHEMA",
                        schema.getDatabaseName(), schema.getName()));
                } else {
                    throw new RuntimeException("Invalid schema name: " + schemaName);
                }
                logger.trace("Created schema: {}", schemaName);
            }

            schema.setTransientObject(transientSchema);
            if (transientSchema) {
                markTablesTransient(schema);
            }
            if (retention != null) {
                schema.setDataRetentionTimeInDays(retention);
            }
            final String comment = ddl.extractCommentFromList(properties.comments());
            if (comment != null) {
                schema.setComment(comment);
            }
            for (final FrostlakeParser.TagListContext tags : properties.tags()) {
                InlineTags.apply(schema, tags, queryExecutor);
            }
            properties.applyParameters(schema.getParameters());
            // A clone of a managed schema stays managed; WITH MANAGED ACCESS makes any schema managed.
            schema.setManagedAccess(properties.managedAccess() || schema.isManagedAccess());
            // Snowflake activates a newly created schema: it becomes the session's current schema,
            // in its containing database (live-verified: CURRENT_SCHEMA() changes right after).
            final String activatedDb = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
            catalog.restoreContext(activatedDb, parts.length == 2 ? parts[1] : parts[0]);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Schema already exists (IF NOT EXISTS): {}", schemaName);
        }
        return null;
    }

    /**
     * The retention a CREATE SCHEMA asks for, judged up front: a negative value, one past the account's
     * limit, or one a transient schema cannot keep is refused. Null when the clause is absent.
     */
    private static Integer requestedRetention(final String written, final boolean transientSchema) {
        if (written == null) {
            return null;
        }
        if (written.startsWith("-")) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                written, "DATA_RETENTION_TIME_IN_DAYS"));
        }
        TransientRetentionLimit.requireWithinAccountLimit(written);
        TransientRetentionLimit.requireWithinTransientLimit(transientSchema, written);
        return Integer.valueOf(written);
    }

    /**
     * TEMPORARY, TEMP and VOLATILE PARSE before SCHEMA and are then refused — live's own shape.
     *
     * <p>The distinction matters because the two outcomes are told apart by their wording: a spelling
     * the grammar does not know dies as a syntax error naming the token that could not follow, while
     * these three produce a sentence that QUOTES THE PAIR — proof the words were read and the feature,
     * not the syntax, is what is missing. It carries no compilation prefix, unlike almost every other
     * refusal. LOCAL and GLOBAL sit on the far side of that line: they are syntax errors here, so the
     * grammar deliberately does not accept them before SCHEMA.
     */
    private void rejectUnsupportedSchemaModifier(final FrostlakeParser.CreateStatementContext ctx) {
        final String word;
        if (ctx.TEMPORARY() != null) {
            word = "TEMPORARY";
        } else if (ctx.TEMP() != null) {
            word = "TEMP";
        } else if (ctx.VOLATILE() != null) {
            word = "VOLATILE";
        } else {
            return;
        }
        throw new RuntimeException("Unsupported feature '" + word + " SCHEMA'.");
    }
}
