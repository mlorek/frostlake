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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.TransientRetentionLimit;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.StorageEngine;

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
    }

    public Object handleCreateDatabase(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String dbName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try {
                final Database replaced = catalog.getDatabase(dbName);
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
        try {
            if (ctx.CLONE() != null) {
                final String sourceDbName = getText(ctx.identifier(1));
                catalog.cloneDatabase(sourceDbName, dbName);
                ddl.cloneDatabaseData(sourceDbName.toUpperCase(), dbName.toUpperCase());
                logger.trace("Cloned database: {} from {}", dbName, sourceDbName);
            } else {
                catalog.createDatabase(dbName);
                logger.trace("Created database: {}", dbName);
            }

            final Database db = catalog.getDatabase(dbName);
            if (ctx.DATA_RETENTION_TIME_IN_DAYS() != null && ctx.INTEGER_LITERAL() != null) {
                final String written = (ctx.MINUS() != null ? "-" : "")
                    + ctx.INTEGER_LITERAL().getText();
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
                }
            }
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) db.setComment(comment);
            // Snowflake activates a newly created database: it becomes the session's current
            // database, with PUBLIC as the current schema (live-verified).
            catalog.useDatabase(dbName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Database already exists (IF NOT EXISTS): {}", dbName);
        }
        return null;
    }

    public Object handleCreateSchema(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        rejectUnsupportedSchemaModifier(ctx);
        final String schemaName = getText(ctx.qualifiedName(0));
        // A schema name has at most two parts (db..s counts three): live refuses a longer one with its
        // generic sentence, IF NOT EXISTS or not, and reads a leading locator of this account.
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 2);
        // A schema is contained by a database; creating one needs CREATE SCHEMA on that database.
        final String containerDb = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
        if (containerDb != null) {
            ddl.checkCreatePrivilege(Privilege.CREATE_SCHEMA, ContainerType.DATABASE, containerDb);
        }
        if (ctx.or_replace() != null) {
            try {
                final String dbN = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
                final String scN = parts.length == 2 ? parts[1] : parts[0];
                if (dbN != null) {
                    final Schema replacedSchema = catalog.getDatabase(dbN).getSchema(scN);
                    if (replacedSchema != null) {
                        releaseSchemaStorage(dbN, replacedSchema);
                    }
                    catalog.getDatabase(dbN).dropSchema(scN, true);
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
                    && catalog.getDatabase(containerName).hasSchema(parts.length == 2 ? parts[1] : parts[0])) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }
            final Database container = containerName == null ? null : catalog.getDatabase(containerName);
            final boolean transientSchema = ctx.TRANSIENT() != null
                || (container != null && container.isTransientObject());
            // Over a NEW name the options are judged before anything is made, so a refused CREATE leaves no
            // schema behind.
            final Integer retention = requestedRetention(ctx, transientSchema);
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
                    final Database db = catalog.getDatabase(catalog.getCurrentDatabase());

                    if (sourceParts.length == 1) {
                        schema = db.cloneSchema(sourceParts[0], parts[0]);
                        sourceDbName = catalog.getCurrentDatabase().toUpperCase();
                        sourceSchema = sourceParts[0].toUpperCase();
                        targetDbName = sourceDbName;
                        targetSchema = parts[0].toUpperCase();
                    } else {
                        throw new RuntimeException("Source schema must be in current database when target is unqualified");
                    }
                } else if (parts.length == 2) {
                    final Database db = catalog.getDatabase(parts[0]);

                    if (sourceParts.length == 1) {
                        final Database sourceDb = catalog.getDatabase(catalog.getCurrentDatabase());
                        schema = sourceDb.cloneSchemaTo(sourceParts[0], db, parts[1]);
                        sourceDbName = catalog.getCurrentDatabase().toUpperCase();
                        sourceSchema = sourceParts[0].toUpperCase();
                        targetDbName = parts[0].toUpperCase();
                        targetSchema = parts[1].toUpperCase();
                    } else if (sourceParts.length == 2) {
                        final Database sourceDb = catalog.getDatabase(sourceParts[0]);
                        schema = sourceDb.cloneSchemaTo(sourceParts[1], db, parts[1]);
                        sourceDbName = sourceParts[0].toUpperCase();
                        sourceSchema = sourceParts[1].toUpperCase();
                        targetDbName = parts[0].toUpperCase();
                        targetSchema = parts[1].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid source schema name: " + sourceSchemaName);
                    }
                } else {
                    throw new RuntimeException("Invalid schema name: " + schemaName);
                }
                ddl.cloneSchemaData(sourceDbName, sourceSchema, targetDbName, targetSchema);
                logger.trace("Cloned schema: {} from {}", schemaName, sourceSchemaName);
            } else {
                if (parts.length == 1) {
                    if (catalog.getCurrentDatabase() == null) {
                        throw NoCurrentDatabaseRefusal.forStatement();
                    }
                    schema = new Schema(parts[0]);
                    catalog.getDatabase(catalog.getCurrentDatabase()).addSchema(schema);
                    schema.setOwner(catalog.currentRoleForOwner());
                } else if (parts.length == 2) {
                    schema = new Schema(parts[1]);
                    catalog.getDatabase(parts[0]).addSchema(schema);
                    schema.setOwner(catalog.currentRoleForOwner());
                } else {
                    throw new RuntimeException("Invalid schema name: " + schemaName);
                }
                logger.trace("Created schema: {}", schemaName);
            }

            schema.setTransientObject(transientSchema);
            if (retention != null) {
                schema.setDataRetentionTimeInDays(retention);
            }
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                schema.setComment(comment);
            }
            if (ctx.tagList() != null) {
                InlineTags.apply(schema, ctx.tagList());
            }
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
    private static Integer requestedRetention(final FrostlakeParser.CreateStatementContext ctx,
            final boolean transientSchema) {
        if (ctx.DATA_RETENTION_TIME_IN_DAYS() == null || ctx.INTEGER_LITERAL() == null) {
            return null;
        }
        final String written = (ctx.MINUS() != null ? "-" : "") + ctx.INTEGER_LITERAL().getText();
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
