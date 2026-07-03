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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.parser.FrostlakeParser;

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

    public Object handleCreateDatabase(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String dbName = getText(ctx.identifier(0));
        if (ctx.or_replace() != null) {
            try {
                catalog.dropDatabase(dbName, true);
                if (ddl.getStreamManager() != null) {
                    ddl.getStreamManager().onDatabaseDropped(dbName);
                }
            } catch (final RuntimeException ignored) {}
        }
        try {
            if (ctx.CLONE() != null) {
                String sourceDbName = getText(ctx.identifier(1));
                catalog.cloneDatabase(sourceDbName, dbName);
                ddl.cloneDatabaseData(sourceDbName.toUpperCase(), dbName.toUpperCase());
                logger.trace("Cloned database: {} from {}", dbName, sourceDbName);
            } else {
                catalog.createDatabase(dbName);
                logger.trace("Created database: {}", dbName);
            }

            Database db = catalog.getDatabase(dbName);
            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) db.setComment(comment);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Database already exists (IF NOT EXISTS): {}", dbName);
        }
        return null;
    }

    public Object handleCreateSchema(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String schemaName = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        // A schema is contained by a database; creating one needs CREATE SCHEMA on that database.
        final String containerDb = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
        if (containerDb != null) {
            ddl.checkCreatePrivilege(Privilege.CREATE_SCHEMA, ContainerType.DATABASE, containerDb);
        }
        if (ctx.or_replace() != null) {
            try {
                String dbN = parts.length == 2 ? parts[0] : catalog.getCurrentDatabase();
                String scN = parts.length == 2 ? parts[1] : parts[0];
                if (dbN != null) {
                    catalog.getDatabase(dbN).dropSchema(scN, true);
                    if (ddl.getStreamManager() != null) {
                        ddl.getStreamManager().onSchemaDropped(dbN, scN);
                    }
                }
            } catch (final RuntimeException ignored) {}
        }
        try {
            Schema schema;

            if (ctx.CLONE() != null) {
                String sourceSchemaName = getText(ctx.qualifiedName(1));
                String[] sourceParts = qualifiedNameParts(ctx.qualifiedName(1));

                String sourceDbName;
                String sourceSchema;
                String targetDbName;
                String targetSchema;

                if (parts.length == 1) {
                    if (catalog.getCurrentDatabase() == null) {
                        throw new RuntimeException("No database selected");
                    }
                    Database db = catalog.getDatabase(catalog.getCurrentDatabase());

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
                    Database db = catalog.getDatabase(parts[0]);

                    if (sourceParts.length == 1) {
                        Database sourceDb = catalog.getDatabase(catalog.getCurrentDatabase());
                        schema = sourceDb.cloneSchemaTo(sourceParts[0], db, parts[1]);
                        sourceDbName = catalog.getCurrentDatabase().toUpperCase();
                        sourceSchema = sourceParts[0].toUpperCase();
                        targetDbName = parts[0].toUpperCase();
                        targetSchema = parts[1].toUpperCase();
                    } else if (sourceParts.length == 2) {
                        Database sourceDb = catalog.getDatabase(sourceParts[0]);
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
                        throw new RuntimeException("No database selected");
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

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                schema.setComment(comment);
            }
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Schema already exists (IF NOT EXISTS): {}", schemaName);
        }
        return null;
    }

}
