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
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.types.DataType;

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
     * Detach one schema's tables from storage as part of a DROP SCHEMA / DROP DATABASE: snapshot each
     * table's rows into {@code out} (keyed by fully-qualified name) and release its storage, exactly as
     * DROP TABLE does. Without this the storage entries outlive the drop, so re-creating the same
     * database/schema fails with "Table storage already exists" and a CLONE into the reused name appends to
     * the stale rows instead of replacing them. Buffered writes are discarded so an uncommitted change from
     * this transaction isn't flushed to now-missing storage at commit.
     */
    private void snapshotAndReleaseStorage(final String databaseName, final Schema schema,
                                           final Map<String, List<Row>> out) {
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
    }

    /**
     * IF EXISTS forgives only the dropped object's own absence: the database and schema its name passes
     * through must exist, and a name the session cannot place is refused naming DROP, before anything is
     * dropped (live-verified for every schema-scoped kind, and for a schema's database).
     */
    private void requireContainers(final FrostlakeParser.DropStatementContext ctx) {
        if (ctx.SCHEMA() != null) {
            final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2);
            if (parts.length == 2) {
                catalog.databaseExact(parts[0]);
            } else if (catalog.getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
        } else if (ctx.qualifiedName() != null) {
            catalog.requireOwningSchema(QualifiedName.of(qualifiedNameParts(ctx.qualifiedName())));
        } else if (ctx.objectName() != null) {
            catalog.requireOwningSchema(QualifiedName.of(queryExecutor.resolveObjectNameParts(ctx.objectName())));
        }
    }

    public Object handleDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        final boolean ifExists = ctx.if_exists() != null;
        requireContainers(ctx);

        try {
            if (ctx.DATABASE() != null) {
                final String dbName = getText(ctx.identifier());
                try {
                    checkDrop(SecurableObjectType.DATABASE, dbName);
                    final Database droppedDb = catalog.getDatabase(dbName);
                    if (droppedDb != null) {
                        // Snapshot every table's rows and RELEASE its storage (as DROP TABLE does), so the
                        // database name can be created again without colliding with — or inheriting — the
                        // dropped tables' storage. UNDROP DATABASE restores both metadata and rows.
                        final Map<String, List<Row>> tableRows = new LinkedHashMap<>();
                        for (final Schema droppedSchema : droppedDb.getAllSchemas()) {
                            snapshotAndReleaseStorage(dbName, droppedSchema, tableRows);
                        }
                        catalog.recordDropped("DATABASE:" + dbName.toUpperCase(),
                            new DroppedObject(droppedDb, tableRows));
                    }
                    catalog.dropDatabase(dbName, false);
                    if (streamManager != null) {
                        streamManager.onDatabaseDropped(dbName);
                    }
                    logger.trace("Dropped database: {}", dbName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Database does not exist (IF EXISTS): {}", dbName);
                }
            } else if (ctx.SCHEMA() != null) {
                final String schemaName = getText(ctx.qualifiedName());
                // Refused before IF EXISTS forgives anything: a schema name has at most two parts.
                final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName()), 2);

                try {
                    checkDrop(SecurableObjectType.SCHEMA, schemaName);
                    // Snapshot schema metadata for UNDROP (its tables' storage survives a DROP SCHEMA).
                    final String snapDbName = parts.length == 1 ? catalog.getCurrentDatabase() : parts[0];
                    final String snapSchemaName = parts.length == 1 ? parts[0] : parts[1];
                    final Database snapDb = catalog.getDatabase(snapDbName);
                    final Schema snapSchema = snapDb != null ? snapDb.getSchema(snapSchemaName) : null;
                    if (snapSchema != null) {
                        final Map<String, List<Row>> tableRows = new LinkedHashMap<>();
                        snapshotAndReleaseStorage(snapDbName, snapSchema, tableRows);
                        catalog.recordDropped("SCHEMA:" + QualifiedName.key(snapDbName, snapSchemaName),
                            new DroppedObject(snapSchema, tableRows));
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
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Schema does not exist (IF EXISTS): {}", schemaName);
                }
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
                final String qn = getText(ctx.qualifiedName());
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    rejectWrongKind(schema, parts[parts.length - 1], RelationKind.DYNAMIC_TABLE);
                    checkDrop(SecurableObjectType.DYNAMIC_TABLE, qn);
                    schema.dropDynamicTable(parts[parts.length - 1]);
                    logger.trace("Dropped dynamic table: {}", qn);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never a name another kind holds.
                    if (!ifExists || SqlCompilationError.isWrongObjectType(e.getMessage())) {
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

                    // INFORMATION_SCHEMA is read-only, and its views are found before any kind
                    // check — so even DROP TABLE on one refuses naming the VIEW (live-verified).
                    if ("INFORMATION_SCHEMA".equals(schema.getName()) && schema.hasView(tableName)) {
                        throw new RuntimeException(
                            SqlAccessControlError.insufficientPrivileges("view", tableName.toUpperCase()));
                    }

                    rejectWrongKind(schema, tableName, RelationKind.TABLE);
                    final Table table = catalog.resolveTable(QualifiedName.of(parts));
                    checkDrop(SecurableObjectType.TABLE, qualifiedName);

                    final String fullyQualifiedName = QualifiedName.key(databaseName, schema.getName(), tableName);
                    // Snapshot table metadata + rows for UNDROP before removing the storage.
                    final List<Row> snapshotRows =
                        new ArrayList<>(queryExecutor.getStorageEngine().getTableStorage(fullyQualifiedName).scan());
                    catalog.recordDropped("TABLE:" + fullyQualifiedName, new DroppedObject(table, snapshotRows));

                    schema.dropTable(table.getName());
                    queryExecutor.getStorageEngine().dropTable(fullyQualifiedName);
                    queryExecutor.resetCopyLoadHistory(fullyQualifiedName);
                    // Drop any buffered writes for this table so an uncommitted INSERT/UPDATE/DELETE earlier
                    // in the same transaction (e.g. a proc that seeds then drops a temp table) isn't flushed
                    // to now-missing storage at commit.
                    queryExecutor.getTransactionManager().discardBufferedWritesFor(fullyQualifiedName);
                    if (streamManager != null) {
                        streamManager.onTableDropped(fullyQualifiedName);
                    }

                    logger.trace("Dropped table: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never an access-control refusal and never a name
                    // another kind holds.
                    if (!ifExists || String.valueOf(e.getMessage()).startsWith(SqlAccessControlError.PREFIX)
                            || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Table does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() == null) {
                final String qualifiedName = getText(ctx.qualifiedName());
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());

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
                    schema.dropView(viewName);
                    logger.trace("Dropped view: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never an access-control refusal and never a name
                    // another kind holds.
                    if (!ifExists || String.valueOf(e.getMessage()).startsWith(SqlAccessControlError.PREFIX)
                            || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("View does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() != null) {
                final String qualifiedName = getText(ctx.qualifiedName());
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());

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
                    if (!ifExists || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Materialized view does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.STREAM() != null) {
                final String streamQn = getText(ctx.qualifiedName());
                final String streamName = ddl.extractObjectName(streamQn);

                try {
                    final Schema schema = ddl.resolveSchemaFromQualifiedName(streamQn);
                    rejectWrongKind(schema, streamName, RelationKind.STREAM);
                    checkDrop(SecurableObjectType.STREAM, streamQn);
                    schema.dropStream(streamName);
                    logger.trace("Dropped stream: {}", streamName);
                } catch (final RuntimeException e) {
                    // IF EXISTS forgives absence, never a name another kind holds.
                    if (!ifExists || SqlCompilationError.isWrongObjectType(e.getMessage())) {
                        throw e;
                    }
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Stream does not exist (IF EXISTS): {}", streamName);
                }
            } else if (ctx.TASK() != null) {
                final String taskQn = getText(ctx.qualifiedName());
                final String taskName = ddl.extractObjectName(taskQn);

                try {
                    final Schema schema = ddl.resolveSchemaFromQualifiedName(taskQn);
                    checkDrop(SecurableObjectType.TASK, taskQn);
                    schema.dropTask(taskName);
                    logger.trace("Dropped task: {}", taskName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Task does not exist (IF EXISTS): {}", taskName);
                }
            } else if (ctx.PIPE() != null) {
                final String pipeName = getText(ctx.qualifiedName());

                try {
                    final Schema schema = ddl.resolveCurrentSchema();
                    checkDrop(SecurableObjectType.PIPE, pipeName);
                    schema.dropPipe(pipeName);
                    logger.trace("Dropped pipe: {}", pipeName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Pipe does not exist (IF EXISTS): {}", pipeName);
                }
            } else if (ctx.SEQUENCE() != null) {
                final String seqQn = getText(ctx.qualifiedName());
                final String sequenceName = ddl.extractObjectName(seqQn);

                try {
                    final Schema schema = ddl.resolveSchemaFromQualifiedName(seqQn);
                    schema.dropSequence(sequenceName);
                    logger.trace("Dropped sequence: {}", sequenceName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Sequence does not exist (IF EXISTS): {}", sequenceName);
                }
            } else if (ctx.CORTEX() != null) {
                final String serviceName = getText(ctx.qualifiedName());
                final String[] serviceParts = qualifiedNameParts(ctx.qualifiedName());
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
                final String poolName = getText(ctx.identifier());
                try {
                    catalog.dropComputePool(poolName);
                    logger.trace("Dropped compute pool: {}", poolName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Compute pool does not exist (IF EXISTS): {}", poolName);
                }
            } else if (ctx.WAREHOUSE() != null) {
                final String warehouseName = getText(ctx.identifier());

                try {
                    catalog.dropWarehouse(warehouseName);
                    logger.trace("Dropped warehouse: {}", warehouseName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Warehouse does not exist (IF EXISTS): {}", warehouseName);
                }
            } else if (ctx.STAGE() != null) {
                final String stageName = getText(ctx.qualifiedName());

                try {
                    checkDrop(SecurableObjectType.STAGE, stageName);
                    catalog.dropStage(stageName);
                    logger.trace("Dropped stage: {}", stageName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Stage does not exist (IF EXISTS): {}", stageName);
                }
            } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
                final String fileFormatName = getText(ctx.qualifiedName());

                try {
                    catalog.dropFileFormat(fileFormatName);
                    logger.trace("Dropped file format: {}", fileFormatName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("File format does not exist (IF EXISTS): {}", fileFormatName);
                }
            } else if (ctx.FUNCTION() != null) {
                final String qualifiedName = getText(ctx.qualifiedName());
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());

                try {
                    final Schema schema;
                    final String functionName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        functionName = parts[0].toUpperCase();
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        functionName = parts[1].toUpperCase();
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        functionName = parts[2].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid function name: " + qualifiedName);
                    }

                    // Check if parameter types were provided
                    if (ctx.dataTypeList() != null) {
                        final List<DataType> argumentTypes = new ArrayList<>();
                        for (final FrostlakeParser.DataTypeNameContext dtCtx : ctx.dataTypeList().dataTypeName()) {
                            argumentTypes.add(columnParser.parseDataType(dtCtx));
                        }
                        // Drop specific overload by signature
                        schema.dropFunctionBySignature(functionName, argumentTypes);
                    } else {
                        // Drop all overloads
                        schema.dropFunction(functionName);
                    }
                    logger.trace("Dropped function: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Function does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.PROCEDURE() != null) {
                final String qualifiedName = getText(ctx.qualifiedName());
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());

                try {
                    final Schema schema;
                    final String procedureName;

                    if (parts.length == 1) {
                        schema = ddl.resolveCurrentSchema();
                        procedureName = parts[0].toUpperCase();
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw NoCurrentDatabaseRefusal.forStatement();
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        procedureName = parts[1].toUpperCase();
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        procedureName = parts[2].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid procedure name: " + qualifiedName);
                    }

                    // Check if parameter types were provided
                    if (ctx.dataTypeList() != null) {
                        final List<DataType> argumentTypes = new ArrayList<>();
                        for (final FrostlakeParser.DataTypeNameContext dtCtx : ctx.dataTypeList().dataTypeName()) {
                            argumentTypes.add(columnParser.parseDataType(dtCtx));
                        }
                        // Drop specific overload by signature
                        schema.dropProcedureBySignature(procedureName, argumentTypes);
                    } else {
                        // Drop all overloads
                        schema.dropProcedure(procedureName);
                    }
                    logger.trace("Dropped procedure: {}", qualifiedName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Procedure does not exist (IF EXISTS): {}", qualifiedName);
                }
            } else if (ctx.USER() != null) {
                final String userName = getText(ctx.identifier());

                try {
                    catalog.dropUser(userName);
                    logger.trace("Dropped user: {}", userName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("User does not exist (IF EXISTS): {}", userName);
                }
            } else if (ctx.ROLE() != null) {
                final String roleName = getText(ctx.identifier());

                try {
                    catalog.dropRole(roleName);
                    logger.trace("Dropped role: {}", roleName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Role does not exist (IF EXISTS): {}", roleName);
                }
            } else if (ctx.TAG() != null) {
                final String tagName = getText(ctx.qualifiedName());
                try {
                    checkDrop(SecurableObjectType.TAG, tagName);
                    final Tag droppedTag = catalog.getTag(tagName);
                    catalog.dropTag(tagName);
                    final String[] tagParts = qualifiedNameParts(ctx.qualifiedName());
                    final String tagDb = tagParts.length == 3 ? tagParts[0] : catalog.getCurrentDatabase();
                    final String tagSchema = tagParts.length == 3 ? tagParts[1]
                        : tagParts.length == 2 ? tagParts[0] : catalog.getCurrentSchema();
                    catalog.recordDropped(
                        "TAG:" + (tagDb + "." + tagSchema + "." + tagParts[tagParts.length - 1]).toUpperCase(),
                        new DroppedObject(droppedTag, (List<Row>) null));
                    logger.trace("Dropped tag: {}", tagName);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped();
                    logger.debug("Tag does not exist (IF EXISTS): {}", tagName);
                }
            } else if (ctx.MASKING() != null && ctx.POLICY() != null && ctx.ROW() == null) {
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    checkDrop(SecurableObjectType.MASKING_POLICY, getText(ctx.qualifiedName()));
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
                    logger.trace("Dropped masking policy: {}", getText(ctx.qualifiedName()));
                } catch (final RuntimeException e) { if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
                try {
                    final Schema schema = parts.length == 1 ? ddl.resolveCurrentSchema()
                        : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                        : catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    checkDrop(SecurableObjectType.ROW_ACCESS_POLICY, getText(ctx.qualifiedName()));
                    if (!schema.hasRowAccessPolicy(parts[parts.length - 1].toUpperCase())) {
                        throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy",
                            schema.qualifiedName(parts[parts.length - 1].toUpperCase())));
                    }
                    if (catalog.isPolicyInUse(parts[parts.length - 1], false)) {
                        throw new RuntimeException("Policy " + parts[parts.length - 1].toUpperCase()
                            + " cannot be dropped/replaced as it is associated with one or more entities.");
                    }
                    schema.dropRowAccessPolicy(parts[parts.length - 1].toUpperCase());
                    logger.trace("Dropped row access policy: {}", getText(ctx.qualifiedName()));
                } catch (final RuntimeException e) { if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.JOIN() != null && ctx.POLICY() != null) {
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
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
                    logger.trace("Dropped join policy: {}", getText(ctx.qualifiedName()));
                } catch (final RuntimeException e) { if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.AGGREGATION() != null && ctx.POLICY() != null) {
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
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
                    logger.trace("Dropped aggregation policy: {}", getText(ctx.qualifiedName()));
                } catch (final RuntimeException e) { if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.PROJECTION() != null && ctx.POLICY() != null) {
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
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
                    logger.trace("Dropped projection policy: {}", getText(ctx.qualifiedName()));
                } catch (final RuntimeException e) { if (!ifExists) throw e;
                    ConditionalDdlOutcome.dropSkipped(); }
            } else if (ctx.CONTACT() != null) {
                final String[] parts = qualifiedNameParts(ctx.qualifiedName());
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
                    logger.trace("Dropped contact: {}", getText(ctx.qualifiedName()));
                } catch (final RuntimeException e) { if (!ifExists) throw e;
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
