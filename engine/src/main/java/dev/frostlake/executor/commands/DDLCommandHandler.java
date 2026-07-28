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
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.StageType;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.metastore.model.WarehouseState;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.types.*;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Handler for DDL (Data Definition Language) commands:
 * CREATE, DROP, ALTER, USE, TRUNCATE, COMMENT
 */
public class DDLCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(DDLCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;
    private final DropCommandHandler dropHandler;
    private final CreateRoutineHandler routineHandler;
    private final CreateInfrastructureHandler infraHandler;
    private final CreateSecurityHandler securityHandler;
    private final CreateTableHandler createTableHandler;
    private final CreateRelationalHandler relationalHandler;
    private final CreateNamespaceHandler namespaceHandler;
    private StreamManager streamManager;
    private TaskScheduler taskScheduler;

    public DDLCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.columnParser = new ColumnDefinitionParser(catalog, queryExecutor);
        this.dropHandler = new DropCommandHandler(this, catalog, queryExecutor, columnParser);
        this.routineHandler = new CreateRoutineHandler(this, catalog, queryExecutor, columnParser);
        this.infraHandler = new CreateInfrastructureHandler(this, catalog, queryExecutor);
        this.securityHandler = new CreateSecurityHandler(this, catalog, queryExecutor, columnParser);
        this.createTableHandler = new CreateTableHandler(this, catalog, queryExecutor, columnParser);
        this.relationalHandler = new CreateRelationalHandler(this, catalog, queryExecutor);
        this.namespaceHandler = new CreateNamespaceHandler(this, catalog, queryExecutor);
    }

    public void setStreamManager(final StreamManager streamManager) {
        this.streamManager = streamManager;
        dropHandler.setStreamManager(streamManager);
    }

    /** The shared column/constraint parser, reused by ALTER TABLE ADD COLUMN so it honours DEFAULT / NOT NULL. */
    public ColumnDefinitionParser getColumnParser() {
        return columnParser;
    }

    public void setTaskScheduler(final TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /**
     * Live accessor for the stream manager, which is wired in after construction via
     * {@link #setStreamManager}. Sibling create handlers must read it through this getter (never
     * snapshot it by value at construction time, when it is still null).
     */
    StreamManager getStreamManager() {
        return streamManager;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    @Override
    public String extractComment(final FrostlakeParser.CommentClauseContext ctx) {
        if (ctx == null || ctx.STRING_LITERAL() == null) {
            return null;
        }
        return extractStringLiteral(ctx.STRING_LITERAL());
    }

    /** DROP statements are handled by {@link DropCommandHandler}. */
    public Object handleDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        return dropHandler.handleDropStatement(ctx);
    }

    public Object handleCreateStatement(final FrostlakeParser.CreateStatementContext ctx) {
        boolean ifNotExists = ctx.if_not_exists() != null;

        try {
            if (ctx.DATABASE() != null) {
                return namespaceHandler.handleCreateDatabase(ctx, ifNotExists);
            } else if (ctx.SCHEMA() != null && ctx.STREAM() == null) {
                return namespaceHandler.handleCreateSchema(ctx, ifNotExists);
            } else if (ctx.STREAM() != null) {
                return handleCreateStream(ctx, ifNotExists);
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null && ctx.dynamicTableOptions() != null) {
                return relationalHandler.handleCreateDynamicTable(ctx, ifNotExists);
            } else if (ctx.TABLE() != null) {
                return createTableHandler.handleCreateTable(ctx, ifNotExists);
            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() == null) {
                return relationalHandler.handleCreateView(ctx, ifNotExists);
            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() != null) {
                return relationalHandler.handleCreateMaterializedView(ctx, ifNotExists);
            } else if (ctx.TASK() != null) {
                return infraHandler.handleCreateTask(ctx, ifNotExists);
            } else if (ctx.PIPE() != null) {
                return infraHandler.handleCreatePipe(ctx, ifNotExists);
            } else if (ctx.SEQUENCE() != null) {
                return infraHandler.handleCreateSequence(ctx, ifNotExists);
            } else if (ctx.WAREHOUSE() != null) {
                return infraHandler.handleCreateWarehouse(ctx, ifNotExists);
            } else if (ctx.STAGE() != null) {
                return infraHandler.handleCreateStage(ctx, ifNotExists);
            } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
                return infraHandler.handleCreateFileFormat(ctx, ifNotExists);
            } else if (ctx.TAG() != null) {
                return securityHandler.handleCreateTag(ctx, ifNotExists);
            } else if (ctx.MASKING() != null && ctx.POLICY() != null && ctx.ROW() == null) {
                return securityHandler.handleCreateMaskingPolicy(ctx, ifNotExists);
            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                return securityHandler.handleCreateRowAccessPolicy(ctx, ifNotExists);
            } else if (ctx.FUNCTION() != null) {
                return routineHandler.handleCreateFunction(ctx, ifNotExists);
            } else if (ctx.PROCEDURE() != null) {
                return routineHandler.handleCreateProcedure(ctx, ifNotExists);
            } else if (ctx.USER() != null) {
                return securityHandler.handleCreateUser(ctx, ifNotExists);
            } else if (ctx.ROLE() != null) {
                return securityHandler.handleCreateRole(ctx, ifNotExists);
            }
        } catch (final RuntimeException e) {
            logger.error("Error in CREATE statement", e);
            throw e;
        }

        return null;
    }

    /**
     * Creating an object requires the matching CREATE privilege on — or ownership of, or an
     * administrative role over — its container: the schema for a schema-level object, or the
     * database for CREATE SCHEMA. No-op when security is disabled or the session is the SYSTEM user
     * (both handled inside {@link dev.frostlake.security.SecurityManager#checkPermission}). Shared by
     * every create handler across the four DDL handler classes so the guard lives in one place.
     */
    void checkCreatePrivilege(final Privilege createPrivilege, final ContainerType containerType, final String containerName) {
        if (queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().checkPermission(createPrivilege, containerType.getCatalogName(), containerName);
        }
    }

    private Object handleCreateStream(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String streamQualifiedName = getText(ctx.qualifiedName(0));
        String streamName = extractObjectName(streamQualifiedName).toUpperCase();
        boolean orReplace = ctx.or_replace() != null;
        String sourceName = getText(ctx.qualifiedName(1));

        // Determine source type (TABLE or VIEW)
        StreamSourceType sourceType;
        if (ctx.TABLE() != null) {
            sourceType = StreamSourceType.TABLE;
        } else if (ctx.VIEW() != null) {
            sourceType = StreamSourceType.VIEW;
        } else {
            throw new RuntimeException("Stream source must be TABLE or VIEW");
        }

        // Parse qualified source name
        String[] sourceParts = qualifiedNameParts(ctx.qualifiedName(1));
        Schema sourceSchema;
        String sourceObjectName;

        if (sourceParts.length == 1) {
            sourceSchema = resolveCurrentSchema();
            sourceObjectName = sourceParts[0];
        } else if (sourceParts.length == 2) {
            String schemaName = sourceParts[0];
            sourceObjectName = sourceParts[1];
            String dbName = catalog.getCurrentDatabase();
            sourceSchema = catalog.getDatabase(dbName).getSchema(schemaName);
        } else {
            throw new RuntimeException("Invalid qualified name: " + sourceName);
        }

        // Validate that the source object exists
        View sourceView = null;
        if (sourceType == StreamSourceType.TABLE) {
            // Check if table exists
            Table table = sourceSchema.getTable(sourceObjectName);
            if (table == null) {
                throw new RuntimeException("Table does not exist: " + sourceName);
            }
        } else {
            // Check if view exists
            sourceView = sourceSchema.getView(sourceObjectName);
            if (sourceView == null) {
                throw new RuntimeException("View does not exist: " + sourceName);
            }
        }

        // Resolve a view stream's base table(s) up front (outside the creation try) so ineligible
        // views are always rejected instead of being swallowed by IF NOT EXISTS handling. Change
        // capture on the stream matches DML against these base tables (one per UNION ALL branch).
        final List<String> viewBaseTables = sourceType == StreamSourceType.VIEW
            ? queryExecutor.resolveViewStreamBaseTables(sourceView) : null;

        try {
            boolean appendOnly = false;
            boolean showInitialRows = false;

            if (ctx.streamOptions() != null) {
                for (final FrostlakeParser.StreamOptionContext optionCtx : ctx.streamOptions().streamOption()) {
                    if (optionCtx.APPEND_ONLY() != null) {
                        appendOnly = optionCtx.booleanValue().TRUE() != null;
                    } else if (optionCtx.SHOW_INITIAL_ROWS() != null) {
                        showInitialRows = optionCtx.booleanValue().TRUE() != null;
                    }
                }
            }

            StreamType type = appendOnly ? StreamType.APPEND_ONLY : StreamType.STANDARD;
            Stream stream = new Stream(streamName, sourceName, sourceType, type, showInitialRows);
            if (viewBaseTables != null) {
                stream.setBaseTableNames(viewBaseTables);
            }

            String comment = extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                stream.setComment(comment);
            }

            Schema schema = resolveSchemaFromQualifiedName(streamQualifiedName);
            checkCreatePrivilege(Privilege.CREATE_STREAM, ContainerType.SCHEMA, schema.getName());
            if (orReplace) {
                try { schema.dropStream(streamName); } catch (final RuntimeException ignored) {}
            }
            stream.setOwner(catalog.currentRoleForOwner());
            schema.addStream(stream);

            // SHOW_INITIAL_ROWS: seed the stream with the source table's existing rows as INSERTs,
            // so the first read returns the current contents (Snowflake semantics).
            if (showInitialRows && sourceType == StreamSourceType.TABLE
                    && queryExecutor.getStreamManager() != null) {
                final String fqTableName = (catalog.getCurrentDatabase() + "."
                    + sourceSchema.getName() + "." + sourceObjectName).toUpperCase();
                queryExecutor.getStreamManager().seedInitialRows(stream, fqTableName,
                    queryExecutor.getStorageEngine().getTableStorage(fqTableName).scan());
            }

            logger.trace("Created stream: {} on {} {}", streamName, sourceType, sourceName);
        } catch (final RuntimeException e) {
            handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Stream already exists (IF NOT EXISTS): {}", streamName);
        }
        return null;
    }

    public Object handleAlterStatement(final FrostlakeParser.AlterStatementContext ctx) {
        if (ctx.VIEW() != null && ctx.MATERIALIZED() != null) {
            return handleAlterMaterializedView(ctx);
        } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
            return handleAlterDynamicTable(ctx);
        } else if (ctx.SEQUENCE() != null) {
            return handleAlterSequence(ctx);
        } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
            return infraHandler.handleAlterFileFormat(ctx);
        } else if (ctx.FUNCTION() != null) {
            return handleAlterRoutine(ctx, true);
        } else if (ctx.PROCEDURE() != null) {
            return handleAlterRoutine(ctx, false);
        }
        // handleAlterStatement only handles the targets the visitor routes here (MATERIALIZED VIEW /
        // DYNAMIC TABLE / SEQUENCE / FILE FORMAT / FUNCTION / PROCEDURE); any other is a programming error.
        throw new RuntimeException("Unsupported ALTER target in handleAlterStatement");
    }

    private Object handleAlterRoutine(final FrostlakeParser.AlterStatementContext ctx, final boolean isFunction) {
        final String qualifiedName = getText(ctx.qualifiedName());
        final Schema schema = resolveSchemaFromQualifiedName(qualifiedName);
        final String name = qualifiedName.contains(".")
            ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1) : qualifiedName;
        final SqlObject routine;
        try {
            routine = isFunction ? schema.getFunction(name) : schema.getProcedure(name);
        } catch (final RuntimeException e) {
            if (ctx.if_exists() != null) {
                return null;
            }
            throw e;
        }
        final FrostlakeParser.RoutineAlterActionContext action = ctx.routineAlterAction();
        if (action.RENAME() != null) {
            final String newName = getText(action.qualifiedName());
            final String newSimple = newName.contains(".")
                ? newName.substring(newName.lastIndexOf('.') + 1) : newName;
            if (isFunction) {
                schema.dropFunction(name);
                routine.setName(newSimple);
                schema.addFunction((Function) routine);
            } else {
                schema.dropProcedure(name);
                routine.setName(newSimple);
                schema.addProcedure((Procedure) routine);
            }
        } else if (action.UNSET() != null) {
            routine.setComment(null);
        } else if (action.COMMENT() != null) {
            routine.setComment(extractStringLiteral(action.STRING_LITERAL()));
        }
        return null;
    }

    private Object handleAlterMaterializedView(final FrostlakeParser.AlterStatementContext ctx) {
        String qualifiedName = getText(ctx.qualifiedName());
        String[] parts = qualifiedNameParts(ctx.qualifiedName());

        Schema schema;
        String mvName;

        if (parts.length == 1) {
            schema = resolveCurrentSchema();
            mvName = parts[0];
        } else if (parts.length == 2) {
            if (catalog.getCurrentDatabase() == null) {
                throw new RuntimeException("No database selected");
            }
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
            mvName = parts[1];
        } else if (parts.length == 3) {
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
            mvName = parts[2];
        } else {
            throw new RuntimeException("Invalid materialized view name: " + qualifiedName);
        }

        MaterializedView mv = schema.getMaterializedView(mvName);
        FrostlakeParser.MaterializedViewActionContext action = ctx.materializedViewAction();

        if (action.SUSPEND() != null) {
            mv.setSuspended(true);
            logger.trace("Suspended materialized view: {}", qualifiedName);
        } else if (action.RESUME() != null) {
            mv.setSuspended(false);
            logger.trace("Resumed materialized view: {}", qualifiedName);
        } else if (action.REFRESH() != null) {
            mv.setLastRefreshedTime(LocalDateTime.now());
            logger.trace("Refreshed materialized view: {}", qualifiedName);
        } else if (action.RENAME() != null) {
            String newName = getText(action.identifier());
            // Re-key the schema map (drop old, add under new name) — otherwise the MV stays
            // findable only by its old name. Mirrors Catalog.renameTable's drop/rename/add.
            schema.dropMaterializedView(mvName);
            mv.rename(newName);
            schema.addMaterializedView(mv);
            logger.trace("Renamed materialized view {} to {}", qualifiedName, newName);
        } else if (action.SET() != null && action.COMMENT() != null) {
            String comment = extractStringLiteral(action.STRING_LITERAL());
            mv.setComment(comment);
            logger.trace("Set comment on materialized view: {}", qualifiedName);
        }

        return null;
    }

    private Object handleAlterDynamicTable(final FrostlakeParser.AlterStatementContext ctx) {
        String qn = getText(ctx.qualifiedName()); String[] parts = qualifiedNameParts(ctx.qualifiedName());
        Schema schema = parts.length == 1 ? resolveCurrentSchema()
            : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
            : catalog.getDatabase(parts[0]).getSchema(parts[1]);
        String dtName = parts[parts.length - 1].toUpperCase();
        DynamicTable dt = schema.getDynamicTable(dtName);
        FrostlakeParser.DynamicTableActionContext action = ctx.dynamicTableAction();

        if (action.SUSPEND() != null) {
            dt.setState(DynamicTable.State.SUSPENDED);
            dt.setSchedulingState("SUSPENDED");
            logger.trace("Suspended dynamic table: {}", qn);
        } else if (action.RESUME() != null) {
            dt.setState(DynamicTable.State.RUNNING);
            dt.setSchedulingState("ACTIVE");
            logger.trace("Resumed dynamic table: {}", qn);
        } else if (action.REFRESH() != null) {
            dt.setLastRefreshedTime(LocalDateTime.now());
            logger.trace("Refreshed dynamic table: {}", qn);
        } else if (action.SET() != null) {
            if (action.TARGET_LAG() != null) {
                String lag = action.DOWNSTREAM() != null ? "DOWNSTREAM" : extractStringLiteral(action.STRING_LITERAL());
                dt.setTargetLag(lag);
            } else if (action.WAREHOUSE() != null && action.identifier() != null) {
                dt.setWarehouse(getText(action.identifier()).toUpperCase());
            } else if (action.REFRESH_MODE() != null) {
                if (action.FULL() != null) dt.setRefreshMode(DynamicTable.RefreshMode.FULL);
                else if (action.INCREMENTAL() != null) dt.setRefreshMode(DynamicTable.RefreshMode.INCREMENTAL);
                else dt.setRefreshMode(DynamicTable.RefreshMode.AUTO);
            } else if (action.DATA_RETENTION_TIME_IN_DAYS() != null) {
                dt.setDataRetentionDays(Integer.parseInt(action.INTEGER_LITERAL().getText()));
            } else if (action.COMMENT() != null) {
                dt.setComment(extractStringLiteral(action.STRING_LITERAL()));
            }
        }
        return null;
    }

    private Object handleAlterSequence(final FrostlakeParser.AlterStatementContext ctx) {
        String qualifiedName = getText(ctx.qualifiedName());
        String[] parts = qualifiedNameParts(ctx.qualifiedName());

        Schema schema;
        String seqName;

        if (parts.length == 1) {
            schema = resolveCurrentSchema();
            seqName = parts[0];
        } else if (parts.length == 2) {
            if (catalog.getCurrentDatabase() == null) {
                throw new RuntimeException("No database selected");
            }
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
            seqName = parts[1];
        } else if (parts.length == 3) {
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
            seqName = parts[2];
        } else {
            throw new RuntimeException("Invalid sequence name: " + qualifiedName);
        }

        Sequence sequence = schema.getSequence(seqName);
        FrostlakeParser.SequenceActionContext action = ctx.sequenceAction();

        if (action.SET() != null && action.INCREMENT() != null) {
            long value = Long.parseLong(action.INTEGER_LITERAL().getText());
            long newIncrement = action.MINUS() != null ? -value : value;
            sequence.setIncrement(newIncrement);
            logger.trace("Set increment of sequence {} to {}", qualifiedName, newIncrement);
        } else if (action.RESTART() != null) {
            if (action.INTEGER_LITERAL() != null) {
                long value = Long.parseLong(action.INTEGER_LITERAL().getText());
                long newValue = action.MINUS() != null ? -value : value;
                // Set to newValue - increment so that next NEXTVAL returns newValue
                sequence.setCurrentValue(newValue - sequence.getIncrement());
                logger.trace("Restarted sequence {} with value: {}", qualifiedName, newValue);
            } else {
                // Restart with original start value
                sequence.setCurrentValue(sequence.getStartValue() - sequence.getIncrement());
                logger.trace("Restarted sequence {} to original start value", qualifiedName);
            }
        }

        return null;
    }

    public Object handleUseStatement(final FrostlakeParser.UseStatementContext ctx) {
        if (ctx.DATABASE() != null) {
            String dbName = queryExecutor.resolveObjectName(ctx.objectName());
            catalog.useDatabase(dbName);
            logger.trace("Using database: {}", dbName);
        } else if (ctx.SCHEMA() != null) {
            String schemaName = queryExecutor.resolveObjectName(ctx.objectName());
            if (schemaName != null && schemaName.contains(".")) {
                String[] parts = schemaName.split("\\.");
                catalog.useDatabase(parts[0]);
                catalog.useSchema(parts[1]);
            } else {
                catalog.useSchema(schemaName);
            }
            logger.trace("Using schema: {}", schemaName);
        } else if (ctx.WAREHOUSE() != null) {
            String warehouseName = queryExecutor.resolveObjectName(ctx.objectName());
            catalog.useWarehouse(warehouseName);
            logger.trace("Using warehouse: {}", warehouseName);
        } else if (ctx.SECONDARY() != null) {
            final String spec = ctx.ALL() != null ? "ALL" : getText(ctx.identifier());
            catalog.useSecondaryRoles(spec);
            logger.trace("Using secondary roles: {}", spec);
        } else if (ctx.ROLE() != null) {
            String roleName = queryExecutor.resolveObjectName(ctx.objectName());
            catalog.useRole(roleName);
            logger.trace("Using role: {}", roleName);
        }
        return null;
    }

    public Object handleUndropStatement(final FrostlakeParser.UndropStatementContext ctx) {
        if (ctx.TABLE() != null) {
            final String name = getText(ctx.qualifiedName());
            final String[] parts = qualifiedNameParts(ctx.qualifiedName());
            final String databaseName;
            final Schema schema;
            final String tableName;
            if (parts.length == 1) {
                databaseName = catalog.getCurrentDatabase();
                schema = resolveCurrentSchema();
                tableName = parts[0];
            } else if (parts.length == 2) {
                databaseName = catalog.getCurrentDatabase();
                schema = catalog.getDatabase(databaseName).getSchema(parts[0]);
                tableName = parts[1];
            } else {
                databaseName = parts[0];
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                tableName = parts[2];
            }
            final String fqName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
            final DroppedObject dropped = catalog.takeDropped("TABLE:" + fqName);
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Cannot UNDROP: no recently dropped table named " + name);
            }
            final Table table = (Table) dropped.getObject();
            schema.addTable(table);
            queryExecutor.getStorageEngine().createTable(fqName, table);
            if (dropped.getRows() != null) {
                for (final Row row : dropped.getRows()) {
                    queryExecutor.getStorageEngine().getTableStorage(fqName).insert(row);
                }
            }
            logger.trace("Undropped table: {}", name);
        } else if (ctx.SCHEMA() != null) {
            final String name = getText(ctx.qualifiedName());
            final String[] parts = qualifiedNameParts(ctx.qualifiedName());
            final String databaseName = parts.length == 1 ? catalog.getCurrentDatabase() : parts[0];
            final String schemaName = parts.length == 1 ? parts[0] : parts[1];
            final DroppedObject dropped =
                catalog.takeDropped("SCHEMA:" + databaseName.toUpperCase() + "." + schemaName.toUpperCase());
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Cannot UNDROP: no recently dropped schema named " + name);
            }
            final Schema restoredSchema = (Schema) dropped.getObject();
            catalog.getDatabase(databaseName).addSchema(restoredSchema);
            restoreTableStorage(databaseName, Collections.singletonList(restoredSchema), dropped.getTableRows());
            logger.trace("Undropped schema: {}", name);
        } else if (ctx.DATABASE() != null) {
            final String name = getText(ctx.identifier());
            final DroppedObject dropped = catalog.takeDropped("DATABASE:" + name.toUpperCase());
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Cannot UNDROP: no recently dropped database named " + name);
            }
            final Database restoredDb = (Database) dropped.getObject();
            catalog.restoreDatabase(restoredDb);
            restoreTableStorage(name, restoredDb.getAllSchemas(), dropped.getTableRows());
            logger.trace("Undropped database: {}", name);
        }
        return null;
    }

    /**
     * Re-create the storage of every table in an UNDROPped schema/database and refill it from the rows
     * snapshotted at drop time (the drop releases storage so the names can be reused). A table whose storage
     * already exists is left alone — the name was re-created after the drop and owns it now.
     */
    private void restoreTableStorage(final String databaseName, final List<Schema> schemas,
                                     final Map<String, List<Row>> tableRows) {
        if (tableRows == null) {
            return;
        }
        final StorageEngine storage = queryExecutor.getStorageEngine();
        for (final Schema schema : schemas) {
            for (final Table table : schema.getTables()) {
                final String fqn = databaseName.toUpperCase() + "." + schema.getName().toUpperCase()
                    + "." + table.getName().toUpperCase();
                if (storage.hasTable(fqn)) {
                    continue;
                }
                storage.createTable(fqn, table);
                final List<Row> rows = tableRows.get(fqn);
                if (rows != null) {
                    for (final Row row : rows) {
                        storage.getTableStorage(fqn).insert(row);
                    }
                }
            }
        }
    }

    /**
     * Helper to resolve current schema
     */
    /** Resolve schema from a qualified name (db.schema.object, schema.object, or object). */
    /**
     * When IF NOT EXISTS is specified, only suppress "already exists" errors.
     * Schema/database not found errors are always re-thrown.
     */
    void handleIfNotExists(final boolean ifNotExists, final RuntimeException e, final String objectName) {
        if (!ifNotExists) throw e;
        // IF NOT EXISTS: suppress "already exists" but re-throw "not found" / "does not exist" errors
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        boolean schemaOrDbNotFound =
            (msg.contains("schema") || msg.contains("database"))
            && (msg.contains("does not exist") || msg.contains("not found") || msg.contains("no database selected") || msg.contains("no schema selected"));
        if (schemaOrDbNotFound) {
            throw e;
        }
        logger.debug("{} already exists (IF NOT EXISTS)", objectName);
    }

    Schema resolveSchemaFromQualifiedName(final String qualifiedName) {
        String[] parts = QualifiedName.parse(qualifiedName).parts();
        String dbName = catalog.getCurrentDatabase();
        if (dbName == null) throw new RuntimeException("No database selected");
        if (parts.length == 3) {
            return catalog.getDatabase(parts[0]).getSchema(parts[1]);
        } else if (parts.length == 2) {
            return catalog.getDatabase(dbName).getSchema(parts[0]);
        }
        return resolveCurrentSchema();
    }

    /** Extract just the object name (last part) from a qualified name, preserving case. */
    String extractObjectName(final String qualifiedName) {
        String[] parts = QualifiedName.parse(qualifiedName).parts();
        return parts[parts.length - 1];
    }

    Schema resolveCurrentSchema() {
        String dbName = catalog.getCurrentDatabase();
        String schemaName = catalog.getCurrentSchema();

        if (dbName == null || schemaName == null) {
            throw new RuntimeException("No database or schema selected");
        }

        return catalog.getDatabase(dbName).getSchema(schemaName);
    }

    /**
     * Helper to resolve fully qualified name
     */
    String resolveFullyQualifiedName(final String qualifiedName) {
        String[] parts = QualifiedName.parse(qualifiedName).parts();

        if (parts.length == 1) {
            String dbName = catalog.getCurrentDatabase();
            String schemaName = catalog.getCurrentSchema();
            if (dbName == null || schemaName == null) {
                throw new RuntimeException("No database or schema selected");
            }
            return dbName.toUpperCase() + "." + schemaName.toUpperCase() + "." + parts[0].toUpperCase();
        } else if (parts.length == 2) {
            String dbName = catalog.getCurrentDatabase();
            if (dbName == null) {
                throw new RuntimeException("No database selected");
            }
            return dbName.toUpperCase() + "." + parts[0].toUpperCase() + "." + parts[1].toUpperCase();
        } else if (parts.length == 3) {
            return parts[0].toUpperCase() + "." + parts[1].toUpperCase() + "." + parts[2].toUpperCase();
        }

        throw new RuntimeException("Invalid qualified name: " + qualifiedName);
    }

    /**
     * Helper methods for parsing SQL constructs
     */
    String extractWarehouseName(final FrostlakeParser.WarehouseClauseContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            return extractStringLiteral(ctx.STRING_LITERAL());
        }
        if (ctx.SESSION_VAR_REF() != null) {
            String varName = ctx.SESSION_VAR_REF().getText().substring(1).toUpperCase();
            SecurityManager sm = queryExecutor != null ? queryExecutor.getSecurityManager() : null;
            Object val = sm != null ? sm.getSessionContext().getSessionParameter(varName)
                                    : (queryExecutor != null ? queryExecutor.getSessionVariables().get(varName) : null);
            return val != null ? val.toString() : varName;
        }
        if (ctx.identifier() != null) {
            return getText(ctx.identifier());
        }
        return null;
    }

    String extractRuntimeVersion(final FrostlakeParser.RuntimeVersionClauseContext ctx) {
        if (ctx.STRING_LITERAL() != null) return extractStringLiteral(ctx.STRING_LITERAL());
        if (ctx.FLOAT_LITERAL() != null) return ctx.FLOAT_LITERAL().getText();
        if (ctx.INTEGER_LITERAL() != null) return ctx.INTEGER_LITERAL().getText();
        return null;
    }

    String extractStringLiteral(final TerminalNode node) {
        return SqlStringLiterals.decode(node.getText());
    }

    void cloneSchemaData(final String sourceDb, final String sourceSchema,
                                  final String targetDb, final String targetSchema) {
        Schema src = catalog.getDatabase(sourceDb).getSchema(sourceSchema);
        Schema tgt = catalog.getDatabase(targetDb).getSchema(targetSchema);
        StorageEngine storage = queryExecutor.getStorageEngine();

        for (final Table table : tgt.getTables()) {
            String srcKey = sourceDb + "." + sourceSchema + "." + table.getName().toUpperCase();
            String tgtKey = targetDb + "." + targetSchema + "." + table.getName().toUpperCase();
            if (!storage.hasTable(tgtKey)) {
                storage.createTable(tgtKey, table);
            }
            if (storage.hasTable(srcKey)) {
                storage.cloneTableData(srcKey, tgtKey);
            }
        }
    }

    void cloneDatabaseData(final String sourceDb, final String targetDb) {
        Database tgt = catalog.getDatabase(targetDb);
        for (final Schema schema : tgt.getAllSchemas()) {
            if ("INFORMATION_SCHEMA".equals(schema.getName())) continue;
            String sn = schema.getName().toUpperCase();
            cloneSchemaData(sourceDb, sn, targetDb, sn);
        }
    }

    String extractBodyDefinition(final FrostlakeParser.BodyDefinitionContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            return extractStringLiteral(ctx.STRING_LITERAL());
        } else if (ctx.DOLLAR_QUOTED_STRING() != null) {
            String text = ctx.DOLLAR_QUOTED_STRING().getText();
            if (text.startsWith("$$") && text.endsWith("$$")) {
                return text.substring(2, text.length() - 2);
            }
            return text;
        } else if (ctx.beginEndBlock() != null) {
            // Unquoted scripting body (AS BEGIN...END / AS DECLARE...END) — return its raw text so the
            // procedure body runs through the same scripting path as a $$-quoted body.
            return getOriginalText(ctx.beginEndBlock());
        } else {
            throw new RuntimeException("Invalid body definition");
        }
    }

    String getOriginalText(final ParserRuleContext ctx) {
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex())
        );
    }


    WarehouseSize parseWarehouseSize(final FrostlakeParser.WarehousePropertiesContext ctx) {
        for (final FrostlakeParser.WarehousePropertyContext prop : ctx.warehouseProperty()) {
            if (prop.WAREHOUSE_SIZE() != null) {
                return parseWarehouseSizeString(warehouseSizeValue(prop));
            }
        }
        return WarehouseSize.X_SMALL;
    }

    /**
     * The WAREHOUSE_SIZE value as text — the grammar allows a quoted literal, an unquoted identifier
     * (e.g. {@code XSMALL}), or a session variable ({@code $var}) resolved to its value. Without covering
     * all three, WAREHOUSE_SIZE = XSMALL / $var throws an NPE (STRING_LITERAL is null).
     */
    private String warehouseSizeValue(final FrostlakeParser.WarehousePropertyContext prop) {
        if (prop.SESSION_VAR_REF() != null) {
            final String varName = prop.SESSION_VAR_REF().getText().substring(1).toUpperCase();
            final Object val = queryExecutor != null && queryExecutor.getSecurityManager() != null
                ? queryExecutor.getSecurityManager().getSessionContext().getSessionParameter(varName)
                : queryExecutor != null ? queryExecutor.getSessionVariables().get(varName) : null;
            return val != null ? val.toString() : "X-Small";
        }
        if (prop.identifier() != null) {
            return getText(prop.identifier());
        }
        return extractStringLiteral(prop.STRING_LITERAL());
    }

    WarehouseSize parseWarehouseSizeString(final String sizeStr) {
        switch (sizeStr.toUpperCase().replace("-", "_")) {
            case "X_SMALL": case "XSMALL": case "XS": return WarehouseSize.X_SMALL;
            case "SMALL": case "S": return WarehouseSize.SMALL;
            case "MEDIUM": case "M": return WarehouseSize.MEDIUM;
            case "LARGE": case "L": return WarehouseSize.LARGE;
            case "X_LARGE": case "XLARGE": case "XL": return WarehouseSize.X_LARGE;
            case "2X_LARGE": case "2XLARGE": case "XXL": return WarehouseSize.X2_LARGE;
            case "3X_LARGE": case "3XLARGE": case "XXXL": return WarehouseSize.X3_LARGE;
            case "4X_LARGE": case "4XLARGE": case "XXXXL": return WarehouseSize.X4_LARGE;
            default: return WarehouseSize.X_SMALL;
        }
    }

    void applyWarehouseProperties(final Warehouse warehouse, final FrostlakeParser.WarehousePropertiesContext ctx) {
        for (final FrostlakeParser.WarehousePropertyContext prop : ctx.warehouseProperty()) {
            applyWarehouseProperty(warehouse, prop);
        }
    }

    public void applyWarehousePropertyPublic(final Warehouse warehouse, final FrostlakeParser.WarehousePropertyContext prop) {
        applyWarehouseProperty(warehouse, prop);
    }

    private void applyWarehouseProperty(final Warehouse warehouse, final FrostlakeParser.WarehousePropertyContext prop) {
        if (prop.WAREHOUSE_TYPE() != null) {
            String type = prop.STANDARD() != null ? "STANDARD" : extractStringLiteral(prop.STRING_LITERAL());
            warehouse.setWarehouseType(type);
        } else if (prop.WAREHOUSE_SIZE() != null) {
            warehouse.setSize(parseWarehouseSizeString(warehouseSizeValue(prop)));
        } else if (prop.AUTO_SUSPEND() != null) {
            warehouse.setAutoSuspendSeconds(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.AUTO_RESUME() != null) {
            warehouse.setAutoResume(prop.booleanValue().TRUE() != null);
        } else if (prop.MIN_CLUSTER_COUNT() != null) {
            warehouse.setMinClusterCount(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.MAX_CLUSTER_COUNT() != null) {
            warehouse.setMaxClusterCount(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.SCALING_POLICY() != null) {
            warehouse.setScalingPolicy(prop.ECONOMY() != null
                ? ScalingPolicy.ECONOMY
                : ScalingPolicy.STANDARD);
        } else if (prop.INITIALLY_SUSPENDED() != null) {
            warehouse.setInitiallySuspended(prop.booleanValue().TRUE() != null);
            if (prop.booleanValue().TRUE() != null) {
                warehouse.setState(WarehouseState.SUSPENDED);
            }
        } else if (prop.RESOURCE_MONITOR() != null) {
            warehouse.setResourceMonitor(getText(prop.identifier()));
        } else if (prop.MAX_CONCURRENCY_LEVEL() != null) {
            warehouse.setMaxConcurrencyLevel(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.STATEMENT_QUEUED_TIMEOUT_IN_SECONDS() != null) {
            warehouse.setStatementQueuedTimeoutSeconds(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.STATEMENT_TIMEOUT_IN_SECONDS() != null) {
            warehouse.setStatementTimeoutSeconds(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.ENABLE_QUERY_ACCELERATION() != null) {
            warehouse.setEnableQueryAcceleration(prop.booleanValue().TRUE() != null);
        } else if (prop.QUERY_ACCELERATION_MAX_SCALE_FACTOR() != null) {
            warehouse.setQueryAccelerationMaxScaleFactor(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.GENERATION() != null) {
            warehouse.setGeneration(prop.STRING_LITERAL() != null
                ? extractStringLiteral(prop.STRING_LITERAL())
                : prop.INTEGER_LITERAL().getText());
        } else if (prop.COMMENT() != null) {
            warehouse.setComment(extractStringLiteral(prop.STRING_LITERAL()));
        }
    }

    String extractCommentFromList(final List<FrostlakeParser.CommentClauseContext> commentClauses) {
        if (commentClauses == null || commentClauses.isEmpty()) {
            return null;
        }
        return extractComment(commentClauses.get(0));
    }

}
