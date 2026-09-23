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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.DroppedObject;
import dev.frostlake.metastore.FutureGrants;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.SqlObject;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.State;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamSourceType;
import dev.frostlake.metastore.model.StreamType;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.metastore.model.WarehouseState;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.types.DataType;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
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
    private final EventTableCommandHandler eventTableHandler;
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
        this.eventTableHandler = new EventTableCommandHandler(this, catalog, queryExecutor, createTableHandler);
        this.relationalHandler = new CreateRelationalHandler(this, catalog, queryExecutor);
        this.namespaceHandler = new CreateNamespaceHandler(this, catalog, queryExecutor);
    }

    public void setStreamManager(final StreamManager streamManager) {
        this.streamManager = streamManager;
        dropHandler.setStreamManager(streamManager);
    }

    /**
     * The shared column/constraint parser, reused by ALTER TABLE ADD COLUMN so it honours
     * DEFAULT / NOT NULL.
     *
     * @return the column-definition parser this handler and its create handlers share
     */
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
        if (ctx == null) {
            return null;
        }
        if (ctx.DOLLAR_QUOTED_STRING() != null) {
            final String raw = ctx.DOLLAR_QUOTED_STRING().getText();
            return raw.substring(2, raw.length() - 2);
        }
        if (ctx.STRING_LITERAL() == null) {
            return null;
        }
        return extractStringLiteral(ctx.STRING_LITERAL());
    }

    /**
     * DROP statements are handled by {@link DropCommandHandler}.
     *
     * @param ctx the DROP statement's parse tree, forwarded unchanged
     * @return the drop handler's result (null — the executor renders the standard status message)
     */
    public Object handleDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        return dropHandler.handleDropStatement(ctx);
    }

    public Object handleDropClassStatement(final FrostlakeParser.DropClassStatementContext ctx) {
        return dropHandler.handleDropClassStatement(ctx);
    }

    public Object handleCreateStatement(final FrostlakeParser.CreateStatementContext ctx) {
        final boolean ifNotExists = ctx.if_not_exists() != null;
        if (ifNotExists && ctx.or_replace() != null) {
            // Live-verified Snowflake rejection, for every object type.
            throw new RuntimeException("options IF NOT EXISTS and OR REPLACE are incompatible.");
        }

        try {
            if (ctx.ALERT() != null) {
                return new AlertCommandHandler(queryExecutor).create(ctx, ifNotExists);
            }
            if (ctx.DATABASE() != null) {
                return namespaceHandler.handleCreateDatabase(ctx, ifNotExists);
            } else if (ctx.SCHEMA() != null && ctx.STREAM() == null) {
                return namespaceHandler.handleCreateSchema(ctx, ifNotExists);
            } else if (ctx.STREAM() != null) {
                return handleCreateStream(ctx, ifNotExists);
            } else if (ctx.EVENT() != null) {
                return eventTableHandler.create(ctx, ifNotExists);
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null
                    && (ctx.dynamicTableOptions() != null || ctx.CLONE() != null)) {
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
            } else if (ctx.COMPUTE() != null && ctx.POOL() != null) {
                return infraHandler.handleCreateComputePool(ctx, ifNotExists);
            } else if (ctx.CORTEX() != null) {
                return infraHandler.handleCreateCortexSearchService(ctx, ifNotExists);
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
            } else if (ctx.CONTACT() != null) {
                return securityHandler.handleCreateContact(ctx, ifNotExists);
            } else if (ctx.PROJECTION() != null && ctx.POLICY() != null) {
                return securityHandler.handleCreateProjectionPolicy(ctx, ifNotExists);
            } else if (ctx.AGGREGATION() != null && ctx.POLICY() != null) {
                return securityHandler.handleCreateAggregationPolicy(ctx, ifNotExists);
            } else if (ctx.JOIN() != null && ctx.POLICY() != null) {
                return securityHandler.handleCreateJoinPolicy(ctx, ifNotExists);
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

    /**
     * CREATE STREAM … CLONE: a new stream with the source stream's definition — its source object, its mode
     * and its comment — that inherits the source's current offset, so it holds the same pending changes.
     */
    private Object handleCloneStream(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String streamQualifiedName = getText(ctx.qualifiedName(0));
        final String streamName = extractObjectName(streamQualifiedName).toUpperCase();
        final String sourceQualifiedName = getText(ctx.qualifiedName(1));
        final Stream source = catalog.resolveStream(sourceQualifiedName);
        if (source == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Stream", sourceQualifiedName));
        }
        try {
            final Schema schema = resolveSchemaFromQualifiedName(streamQualifiedName);
            checkCreatePrivilege(Privilege.CREATE_STREAM, ContainerType.SCHEMA, schema.getName());
            final Stream clone = new Stream(streamName, source.getSourceTableName(), source.getSourceType(),
                source.getStreamType(), source.isShowInitialRows());
            clone.setBaseTableNames(source.getBaseTableNames());
            clone.setComment(source.getComment());
            clone.inheritOffset(source);
            if (ctx.or_replace() != null) {
                try {
                    schema.dropStream(streamName);
                } catch (final RuntimeException absent) {
                    logger.trace("No stream to replace: {}", streamName);
                }
            }
            clone.setOwner(catalog.currentRoleForOwner());
            schema.addStream(clone);
            logger.trace("Cloned stream {} from {}", streamName, sourceQualifiedName);
        } catch (final RuntimeException e) {
            handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Stream already exists (IF NOT EXISTS): {}", streamName);
        }
        return null;
    }

    private Object handleCreateStream(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        if (ctx.CLONE() != null) {
            return handleCloneStream(ctx, ifNotExists);
        }
        final String streamQualifiedName = getText(ctx.qualifiedName(0));
        final String streamName = extractObjectName(streamQualifiedName).toUpperCase();
        final boolean orReplace = ctx.or_replace() != null;
        // COPY GRANTS copies from the object being replaced or cloned, so a plain create has no source
        // to copy from. A stream's refusal carries a second sentence the table's and the view's do not.
        if (!ctx.copyGrants().isEmpty() && !orReplace) {
            throw new RuntimeException(SqlCompilationError.of(
                "Invalid operation COPY GRANTS without specifying source object."
                    + " Please use either OR REPLACE or CLONE."));
        }
        final String sourceName = getText(ctx.qualifiedName(1));

        // The kind of source: a table (an event table is one), a view, a stage's directory table, a
        // dynamic table, or an external table — which this engine has none of, so only its refusals apply.
        final FrostlakeParser.StreamSourceKindContext kind = ctx.streamSourceKind();
        final StreamSourceType sourceType = kind.VIEW() != null ? StreamSourceType.VIEW
            : kind.STAGE() != null ? StreamSourceType.STAGE
            : kind.DYNAMIC() != null ? StreamSourceType.DYNAMIC_TABLE : StreamSourceType.TABLE;

        // Parse qualified source name
        final String[] sourceParts = qualifiedNameParts(ctx.qualifiedName(1));
        final Schema sourceSchema;
        final String sourceObjectName;

        if (sourceParts.length == 1) {
            sourceSchema = resolveCurrentSchema();
            sourceObjectName = sourceParts[0];
        } else if (sourceParts.length == 2) {
            final String schemaName = sourceParts[0];
            sourceObjectName = sourceParts[1];
            final String dbName = catalog.getCurrentDatabase();
            sourceSchema = catalog.getDatabase(dbName).getSchema(schemaName);
        } else if (sourceParts.length == 3) {
            sourceObjectName = sourceParts[2];
            sourceSchema = catalog.getDatabase(sourceParts[0]).getSchema(sourceParts[1]);
        } else {
            throw new RuntimeException("Invalid qualified name: " + sourceName);
        }
        final String sourceQualified = sourceSchema.getDatabaseName() + "." + sourceSchema.getName() + "."
            + sourceObjectName;

        boolean appendOnly = false;
        boolean showInitialRows = false;
        boolean insertOnly = false;
        if (ctx.streamOptions() != null) {
            for (final FrostlakeParser.StreamOptionContext optionCtx : ctx.streamOptions().streamOption()) {
                if (optionCtx.APPEND_ONLY() != null) {
                    appendOnly = optionCtx.booleanValue().TRUE() != null;
                } else if (optionCtx.SHOW_INITIAL_ROWS() != null) {
                    showInitialRows = optionCtx.booleanValue().TRUE() != null;
                } else {
                    insertOnly = optionCtx.booleanValue().TRUE() != null;
                }
            }
        }

        // Validate that the source object exists and is of the kind named
        View sourceView = null;
        Table sourceTable = null;
        DynamicTable sourceDynamicTable = null;
        if (kind.EXTERNAL() != null) {
            // Every refusal live gives an external-table stream here: this engine keeps no external tables.
            if (sourceSchema.hasTable(sourceObjectName)) {
                throw new RuntimeException(SqlCompilationError.objectOfOtherType("TABLE", "EXTERNAL_TABLE"));
            }
            throw new RuntimeException(SqlCompilationError.doesNotExist("External table", sourceQualified));
        } else if (sourceType == StreamSourceType.STAGE) {
            final Stage stage = sourceSchema.getStage(sourceObjectName);
            if (!stage.isDirectoryEnabled()) {
                throw new RuntimeException("DIRECTORY not enabled for the stage " + stage.getName());
            }
            if (ctx.streamPoint() != null) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Time travel not supported for stream creation on stages."));
            }
            if (insertOnly) {
                throw new RuntimeException("Streams on directories cannot have INSERT_ONLY set to true.");
            }
            if (appendOnly) {
                throw new RuntimeException("Streams on directories cannot have APPEND_ONLY set to true.");
            }
            if (showInitialRows) {
                throw new RuntimeException("Streams on directories cannot have SHOW_INITIAL_ROWS set to true.");
            }
        } else if (sourceType == StreamSourceType.DYNAMIC_TABLE) {
            if (!sourceSchema.hasDynamicTable(sourceObjectName)) {
                if (sourceSchema.hasTable(sourceObjectName)) {
                    throw new RuntimeException(SqlCompilationError.objectOfOtherType("TABLE", "DYNAMIC_TABLE"));
                }
                throw new RuntimeException(SqlCompilationError.doesNotExist("Dynamic table", sourceQualified));
            }
            sourceDynamicTable = sourceSchema.getDynamicTable(sourceObjectName);
            if (appendOnly) {
                throw new RuntimeException("Change tracking of type APPEND_ONLY is not supported on dynamic tables.");
            }
        } else if (sourceType == StreamSourceType.TABLE) {
            if (kind.EVENT() != null && !sourceSchema.hasTable(sourceObjectName)) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Event table", sourceQualified));
            }
            if (!sourceSchema.hasTable(sourceObjectName) && sourceSchema.hasDynamicTable(sourceObjectName)) {
                throw new RuntimeException(SqlCompilationError.objectOfOtherType("DYNAMIC_TABLE", "TABLE"));
            }
            // Check if table exists
            final Table table = sourceSchema.getTable(sourceObjectName);
            if (table == null) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Table", sourceName));
            }
            if (kind.EVENT() != null && !table.isEventTable()) {
                throw new RuntimeException(SqlCompilationError.objectOfOtherType("TABLE", "EVENT_TABLE"));
            }
            sourceTable = table;
        } else {
            // Check if view exists. Snowflake words this one specifically — live-verified on a real
            // account: CREATE STREAM s ON VIEW non_existent_view fails "SQL compilation
            // error: View 'NON_EXISTENT_VIEW' does not exist or not authorized." — so the lookup is
            // guarded rather than letting the catalog's generic "View does not exist: x" surface.
            if (!sourceSchema.hasView(sourceObjectName)) {
                throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("View",
                    sourceObjectName.toUpperCase()));
            }
            sourceView = sourceSchema.getView(sourceObjectName);
        }
        // Resolve a view stream's base table(s) up front (outside the creation try) so ineligible
        // views are always rejected instead of being swallowed by IF NOT EXISTS handling. Change
        // capture on the stream matches DML against these base tables (one per UNION ALL branch).
        final List<String> viewBaseTables = sourceType == StreamSourceType.VIEW
            ? queryExecutor.resolveViewStreamBaseTables(sourceView) : null;
        if (insertOnly && viewBaseTables != null && !viewBaseTables.isEmpty()) {
            // A view names the first table it reads and itself, fully qualified (live-verified layout).
            throw new RuntimeException("SQL compilation error: line 0 at position -1:\nChange tracking of type "
                + "INSERT_ONLY is not supported on queries with 'TABLE' inside views, saw '"
                + catalog.resolveTable(viewBaseTables.get(0)).getName() + "'. (inside view '" + sourceQualified + "').");
        }
        if (insertOnly) {
            throw new RuntimeException("Streams of type INSERT_ONLY can only be created on external tables or "
                + "Iceberg tables with an external catalog integration.");
        }

        try {
            final StreamType type = appendOnly ? StreamType.APPEND_ONLY : StreamType.STANDARD;
            // A stream on a stage or a dynamic table names its source fully, the way SHOW STREAMS reports it.
            final String recordedSource = sourceType == StreamSourceType.STAGE
                || sourceType == StreamSourceType.DYNAMIC_TABLE ? sourceQualified : sourceName;
            final Stream stream = new Stream(streamName, recordedSource, sourceType, type, showInitialRows);
            if (viewBaseTables != null) {
                stream.setBaseTableNames(viewBaseTables);
            }

            final String comment = extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                stream.setComment(comment);
            }
            if (ctx.tagList() != null) {
                InlineTags.apply(stream, ctx.tagList(), queryExecutor);
            }

            final Schema schema = resolveSchemaFromQualifiedName(streamQualifiedName);
            checkCreatePrivilege(Privilege.CREATE_STREAM, ContainerType.SCHEMA, schema.getName());
            // The starting point is resolved before anything changes: a point live refuses creates nothing.
            final StreamStart start = new StreamStart(queryExecutor, catalog);
            if (sourceTable != null) {
                // Creating a stream enables change tracking on its base table implicitly (SHOW TABLES
                // flips change_tracking OFF→ON), live-verified — and it stays on after the stream goes, and
                // even when the stream's starting point is then refused.
                sourceTable.setChangeTracking(true);
                start.forTable(stream, sourceTable, sourceQualified, ctx.streamPoint(), showInitialRows);
            } else if (sourceDynamicTable != null) {
                start.forDynamicTable(stream, sourceDynamicTable, ctx.streamPoint(), showInitialRows);
            } else if (sourceView != null) {
                // A stream on a view turns change tracking on for the tables it reads (live-verified).
                for (final String baseTable : viewBaseTables) {
                    catalog.resolveTable(baseTable).setChangeTracking(true);
                }
                start.forView(stream, viewBaseTables, ctx.streamPoint(), showInitialRows);
            }
            if (orReplace) {
                queryExecutor.requireOwnership("STREAM", qualifiedNameParts(ctx.qualifiedName(0)), null);
                try { schema.dropStream(streamName); } catch (final RuntimeException ignored) {}
            }
            stream.setOwner(FutureGrants.ownerOfNew(catalog, "STREAM", schema, stream.getName()));
            schema.addStream(stream);

            logger.trace("Created stream: {} on {} {}", streamName, sourceType, sourceName);
        } catch (final RuntimeException e) {
            handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Stream already exists (IF NOT EXISTS): {}", streamName);
        }
        return null;
    }

    /** Rows as their value lists. */
    private static List<List<Object>> rowValues(final List<Row> rows) {
        final List<List<Object>> values = new ArrayList<>();
        for (final Row row : rows) {
            values.add(new ArrayList<>(row.getValues()));
        }
        return values;
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
        requireSignatureForm(ctx);
        final String qualifiedName = getText(ctx.qualifiedName());
        // The simple name comes from the parse tree's last identifier part — never by re-splitting
        // the flattened text, which breaks a quoted name containing a dot.
        final String[] nameParts = qualifiedNameParts(ctx.qualifiedName());
        final String name = nameParts[nameParts.length - 1];
        final Schema schema;
        final SqlObject routine;
        try {
            schema = resolveSchemaFromQualifiedName(qualifiedName);
            routine = alteredRoutine(ctx, schema, name, isFunction);
        } catch (final RuntimeException e) {
            if (ctx.if_exists() != null) {
                return null;
            }
            throw e;
        }
        final FrostlakeParser.RoutineAlterActionContext action = ctx.routineAlterAction();
        if (action.RENAME() != null) {
            final String[] newParts = qualifiedNameParts(action.qualifiedName(0));
            final String newSimple = newParts[newParts.length - 1];
            final Schema destination = renameDestination(schema, newParts, isFunction);
            // A routine of that name already carrying this signature holds it, the routine itself
            // included: live refuses ALTER FUNCTION f() RENAME TO f (live-verified).
            if (isFunction
                ? destination.hasFunctionSignature(newSimple, ((Function) routine).getParameters())
                : destination.hasProcedureSignature(newSimple, ((Procedure) routine).getParameters())) {
                throw new RuntimeException(Schema.alreadyExists(newSimple));
            }
            if (isFunction) {
                schema.removeFunction((Function) routine);
                routine.setName(newSimple);
                destination.addFunction((Function) routine);
            } else {
                schema.removeProcedure((Procedure) routine);
                routine.setName(newSimple);
                destination.addProcedure((Procedure) routine);
            }
        } else if (action.SECURE() != null) {
            requireFunctionSecure(ctx, isFunction);
            ((Function) routine).setSecure(action.SET() != null);
        } else if (action.TAG() != null) {
            // A tag is set and unset on a routine as it is anywhere else, and changes nothing else
            // about it (live-verified).
            logger.trace("Tag changed on routine: {}", qualifiedName);
        } else if (!action.routineSetProperty().isEmpty()) {
            // A repeated property is taken, the last one winning, as the account takes it.
            for (final FrostlakeParser.RoutineSetPropertyContext property : action.routineSetProperty()) {
                applyRoutineProperty(routine, property, isFunction);
            }
        } else if (!action.routineUnsetProperty().isEmpty()) {
            for (final FrostlakeParser.RoutineUnsetPropertyContext property : action.routineUnsetProperty()) {
                unsetRoutineProperty(routine, property, isFunction);
            }
        }
        return null;
    }

    /** The routine an ALTER names, by its exact canonical name: the overload its written signature picks. */
    private SqlObject alteredRoutine(final FrostlakeParser.AlterStatementContext ctx, final Schema schema,
                                     final String name, final boolean isFunction) {
        final List<DataType> argumentTypes = new ArrayList<>();
        if (ctx.routineSignature() != null) {
            for (final FrostlakeParser.RoutineSignatureItemContext item : ctx.routineSignature().routineSignatureItem()) {
                argumentTypes.add(columnParser.parseDataType(item.dataTypeName(), item.typeParameters()));
            }
        }
        return isFunction
            ? schema.getFunctionBySignature(name, argumentTypes)
            : schema.getProcedureBySignature(name, argumentTypes);
    }

    /**
     * The argument list an ALTER FUNCTION or PROCEDURE writes. RENAME TO takes arguments as a CREATE writes
     * them, a name and a default included, so {@code ALTER FUNCTION f1(x INT) RENAME TO f2} renames f1(INT), and
     * a required argument after an optional one is refused before the routine is looked up:
     * {@code required argument Y cannot follow optional argument X}, at the required one. Every other action
     * takes the types alone, so a named argument there is a syntax error at its type, as a default written
     * {@code :=} is under RENAME. The list itself is
     * required: ALTER FUNCTION f RENAME TO g is a syntax error at RENAME, reported alone (live-verified).
     */
    private void requireSignatureForm(final FrostlakeParser.AlterStatementContext ctx) {
        if (ctx.LPAREN() == null) {
            final Token action = ctx.routineAlterAction().getStart();
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + action.getLine()
                + " at position " + action.getCharPositionInLine() + " unexpected '" + action.getText() + "'."));
        }
        if (ctx.routineSignature() == null) {
            return;
        }
        final boolean rename = ctx.routineAlterAction().RENAME() != null;
        String optional = null;
        for (final FrostlakeParser.RoutineSignatureItemContext item : ctx.routineSignature().routineSignatureItem()) {
            if (item.identifier() == null) {
                continue;
            }
            if (item.dataTypeName() == null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Unsupported data type '" + getText(item.identifier()) + "'."));
            }
            if (!rename || item.COLON_EQ() != null) {
                final Token type = item.dataTypeName().getStart();
                throw new RuntimeException(SqlCompilationError.of("syntax error line " + type.getLine()
                    + " at position " + type.getCharPositionInLine() + " unexpected '" + type.getText() + "'."));
            }
            final String argument = getText(item.identifier());
            if (item.DEFAULT() != null) {
                optional = argument;
            } else if (optional != null) {
                final Token at = item.identifier().getStart();
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "required argument " + argument + " cannot follow optional argument " + optional));
            }
        }
    }

    /**
     * SECURE is a FUNCTION's property. A procedure's ALTER simply has no such action on the account, so
     * it ends where the word does: the refusal is the syntax error the parser gives at the end of the
     * statement, not a property refusal (live-verified).
     */
    private void requireFunctionSecure(final FrostlakeParser.AlterStatementContext ctx, final boolean isFunction) {
        if (isFunction) {
            return;
        }
        final Token last = ctx.getStop();
        throw new RuntimeException(SqlCompilationError.of("syntax error line " + last.getLine()
            + " at position " + (last.getCharPositionInLine() + last.getText().length())
            + " unexpected '<EOF>'."));
    }

    /** COMMENT is kept; the two logging levels are taken and inert; any other name is not a property. */
    private void applyRoutineProperty(final SqlObject routine,
                                      final FrostlakeParser.RoutineSetPropertyContext property,
                                      final boolean isFunction) {
        if (property.COMMENT() != null) {
            routine.setComment(extractStringLiteral(property.STRING_LITERAL()));
            return;
        }
        requireRoutineProperty(getText(property.identifier()).toUpperCase(), isFunction);
    }

    /** The UNSET side of the same rule. */
    private void unsetRoutineProperty(final SqlObject routine,
                                      final FrostlakeParser.RoutineUnsetPropertyContext property,
                                      final boolean isFunction) {
        if (property.COMMENT() != null) {
            routine.setComment(null);
            return;
        }
        requireRoutineProperty(getText(property.identifier()).toUpperCase(), isFunction);
    }

    /** Refuse a name that is not a property of a routine at all, in the account's own words. */
    private void requireRoutineProperty(final String name, final boolean isFunction) {
        if ("LOG_LEVEL".equals(name) || "TRACE_LEVEL".equals(name)) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("invalid property '" + name
            + "' for '" + (isFunction ? "FUNCTION" : "PROCEDURE") + "'"));
    }

    private Object handleAlterMaterializedView(final FrostlakeParser.AlterStatementContext ctx) {
        final String qualifiedName = queryExecutor.alterTargetName(ctx);
        final String[] parts = queryExecutor.alterTargetNameParts(ctx);

        final Schema schema;
        final String mvName;

        if (parts.length == 1) {
            schema = resolveCurrentSchema();
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

        final MaterializedView mv = schema.getMaterializedView(mvName);
        final FrostlakeParser.MaterializedViewActionContext action = ctx.materializedViewAction();

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
            final String newName = getText(action.identifier());
            // Re-key the schema map (drop old, add under new name) — otherwise the MV stays
            // findable only by its old name. Mirrors Catalog.renameTable's drop/rename/add.
            schema.dropMaterializedView(mvName);
            mv.rename(newName);
            schema.addMaterializedView(mv);
            logger.trace("Renamed materialized view {} to {}", qualifiedName, newName);
        } else if (action.SET() != null && action.COMMENT() != null) {
            final String comment = extractStringLiteral(action.STRING_LITERAL());
            mv.setComment(comment);
            logger.trace("Set comment on materialized view: {}", qualifiedName);
        } else if (action.UNSET() != null) {
            mv.setComment(null);
            logger.trace("Unset comment on materialized view: {}", qualifiedName);
        }

        return null;
    }

    /** The schema a dynamic table's name places it in: its own parts, else the session's database and schema. */
    private Schema dynamicTableSchema(final String[] parts) {
        return parts.length == 1 ? resolveCurrentSchema()
            : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
            : catalog.getDatabase(parts[0]).getSchema(parts[1]);
    }

    /**
     * The dynamic table a name reaches.
     *
     * @throws RuntimeException the does-not-exist refusal when there is none
     */
    public DynamicTable resolveDynamicTable(final FrostlakeParser.QualifiedNameContext name) {
        final String[] parts = qualifiedNameParts(name);
        return dynamicTableSchema(parts).getDynamicTable(parts[parts.length - 1].toUpperCase());
    }

    private Object handleAlterDynamicTable(final FrostlakeParser.AlterStatementContext ctx) {
        final String qn = getText(ctx.qualifiedName());
        DynamicTableProperties.rejectUntakeable(ctx.dynamicTableAction());
        final String[] parts = qualifiedNameParts(ctx.qualifiedName());
        final Schema schema = dynamicTableSchema(parts);
        final String dtName = parts[parts.length - 1].toUpperCase();
        final DynamicTable dt = schema.getDynamicTable(dtName);
        final FrostlakeParser.DynamicTableActionContext action = ctx.dynamicTableAction();

        if (action.RECLUSTER() != null) {
            // Only a table with a clustering key reclusters, as for a plain table.
            if (dt.getClusterKeys().isEmpty()) {
                // The sentence ends with a newline, as the ALTER TABLE form's does.
                throw new RuntimeException("Table '" + dt.getName() + "' is not clustered\n");
            }
            dt.setReclusterSuspended(action.SUSPEND() != null);
            logger.trace("{} reclustering of dynamic table: {}", action.SUSPEND() != null ? "Suspended" : "Resumed", qn);
        } else if (action.SWAP() != null) {
            swapDynamicTables(schema, dt, action.qualifiedName());
            logger.trace("Swapped dynamic table {} with {}", qn, getText(action.qualifiedName()));
        } else if (action.SUSPEND() != null) {
            dt.setState(State.SUSPENDED);
            dt.setSchedulingState("SUSPENDED");
            dt.setLastSuspendedOn(StatementClock.instant());
            logger.trace("Suspended dynamic table: {}", qn);
        } else if (action.RESUME() != null) {
            dt.setState(State.RUNNING);
            dt.setSchedulingState("ACTIVE");
            dt.setLastSuspendedOn(null);
            logger.trace("Resumed dynamic table: {}", qn);
        } else if (action.REFRESH() != null) {
            dt.setLastRefreshedTime(StatementClock.instant());
            // A stream on the table records what the refresh changed.
            final String qualified = schema.getDatabaseName() + "." + schema.getName() + "." + dt.getName();
            if (queryExecutor.getStreamManager() != null && !queryExecutor.getStreamManager()
                    .streamsOver(schema.getDatabaseName(), StreamSourceType.DYNAMIC_TABLE, qualified).isEmpty()) {
                queryExecutor.getStreamManager().trackDynamicTableRefresh(schema.getDatabaseName(), qualified,
                    rowValues(queryExecutor.dynamicTableRows(dt)));
            }
            logger.trace("Refreshed dynamic table: {}", qn);
        } else if (action.SET() != null) {
            for (final FrostlakeParser.DynamicTableSettingContext setting : action.dynamicTableSetting()) {
                applyDynamicTableSetting(dt, setting);
            }
        } else if (action.UNSET() != null) {
            for (final FrostlakeParser.DynamicTablePropertyContext property : action.dynamicTableProperty()) {
                unsetDynamicTableProperty(dt, dtName, property);
            }
        }
        return null;
    }

    /**
     * ALTER DYNAMIC TABLE ... SWAP WITH: the two dynamic tables exchange their names, and with them their places,
     * each keeping its own definition, settings and history.
     */
    private void swapDynamicTables(final Schema schema, final DynamicTable dt,
                                   final FrostlakeParser.QualifiedNameContext otherName) {
        final String[] otherParts = qualifiedNameParts(otherName);
        final Schema otherSchema = dynamicTableSchema(otherParts);
        final DynamicTable other = otherSchema.getDynamicTable(otherParts[otherParts.length - 1].toUpperCase());
        if (other == dt) {
            return;
        }
        final String name = dt.getName();
        schema.dropDynamicTable(name);
        otherSchema.dropDynamicTable(other.getName());
        dt.setName(other.getName());
        other.setName(name);
        otherSchema.addDynamicTable(dt);
        schema.addDynamicTable(other);
    }

    private void applyDynamicTableSetting(final DynamicTable dt,
                                          final FrostlakeParser.DynamicTableSettingContext setting) {
        final String name = setting.dynamicTableProperty().getText().toUpperCase();
        if ("TARGET_LAG".equals(name) && setting.DOWNSTREAM() != null) {
            dt.setTargetLag("DOWNSTREAM");
        } else if ("TARGET_LAG".equals(name) && setting.STRING_LITERAL() != null
                && TargetLag.parses(extractStringLiteral(setting.STRING_LITERAL()))) {
            dt.setTargetLag(TargetLag.canonicalize(extractStringLiteral(setting.STRING_LITERAL())));
        } else if ("WAREHOUSE".equals(name) && setting.identifier() != null) {
            final String warehouse = getText(setting.identifier());
            if (!catalog.hasWarehouse(warehouse)) {
                // Live's sentence for a warehouse, which has no "not authorized" half (live-verified).
                throw new RuntimeException("Warehouse '" + warehouse.toUpperCase() + "' does not exist.");
            }
            dt.setWarehouse(warehouse.toUpperCase());
        } else if ("COMMENT".equals(name) && setting.STRING_LITERAL() != null) {
            dt.setComment(extractStringLiteral(setting.STRING_LITERAL()));
        } else if ("DATA_RETENTION_TIME_IN_DAYS".equals(name) && setting.INTEGER_LITERAL() != null
                && setting.MINUS() == null) {
            dt.setDataRetentionDays(Integer.parseInt(setting.INTEGER_LITERAL().getText()));
        } else if ("MAX_DATA_EXTENSION_TIME_IN_DAYS".equals(name) && setting.INTEGER_LITERAL() != null
                && setting.MINUS() == null) {
            dt.setMaxDataExtensionDays(Integer.parseInt(setting.INTEGER_LITERAL().getText()));
        } else {
            final String written = setting.getText().substring(setting.dynamicTableProperty().getText().length() + 1);
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value '" + SqlStringLiterals.decode(written) + "' for property '" + name + "'"));
        }
    }

    /**
     * UNSET on a dynamic table: the comment and the two retention settings go back to their defaults, a
     * target lag cannot be left without a value, and the warehouse cannot be unset at all (live-verified).
     */
    private void unsetDynamicTableProperty(final DynamicTable dt, final String dtName,
                                           final FrostlakeParser.DynamicTablePropertyContext property) {
        final String name = property.getText().toUpperCase();
        if ("TARGET_LAG".equals(name)) {
            throw new RuntimeException(SqlCompilationError.of("invalid value 'null' for property 'TARGET_LAG'"));
        }
        if ("WAREHOUSE".equals(name)) {
            throw new RuntimeException(SqlCompilationError.inline(
                "cannot unset property 'WAREHOUSE' for '" + dtName + "'"));
        }
        if ("COMMENT".equals(name)) {
            dt.setComment(null);
        }
    }

    private Object handleAlterSequence(final FrostlakeParser.AlterStatementContext ctx) {
        final String qualifiedName = getText(ctx.qualifiedName());
        final String[] parts = qualifiedNameParts(ctx.qualifiedName());

        final Schema schema;
        final Sequence sequence;
        try {
            if (parts.length == 1) {
                schema = resolveCurrentSchema();
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw NoCurrentDatabaseRefusal.forStatement();
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
            } else if (parts.length == 3) {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
            } else {
                throw new RuntimeException("Invalid sequence name: " + qualifiedName);
            }
            sequence = schema.getSequence(parts[parts.length - 1]);
        } catch (final RuntimeException absent) {
            // IF EXISTS forgives the sequence's absence, and nothing the action itself refuses.
            if (ctx.if_exists() != null) {
                return null;
            }
            throw absent;
        }
        final FrostlakeParser.SequenceActionContext action = ctx.sequenceAction();

        if (action.RENAME() != null) {
            renameSequence(schema, sequence, qualifiedNameParts(action.qualifiedName()));
        } else if (action.SET() != null && action.INCREMENT() != null) {
            final long value = Long.parseLong(action.INTEGER_LITERAL().getText());
            final long newIncrement = action.MINUS() != null ? -value : value;
            sequence.setIncrement(newIncrement);
            logger.trace("Set increment of sequence {} to {}", qualifiedName, newIncrement);
        }

        return null;
    }

    /**
     * Where ALTER FUNCTION / PROCEDURE … RENAME TO moves the routine: a qualified new name names the schema it
     * moves to (a two-part one a schema of the session's database), and a bare one keeps it in its own schema.
     */
    private Schema renameDestination(final Schema source, final String[] target, final boolean isFunction) {
        if (target.length < 2) {
            return source;
        }
        if (target.length == 2 && catalog.getCurrentDatabase() == null) {
            throw NoCurrentDatabaseRefusal.naming(isFunction ? "CREATE FUNCTION" : "CREATE PROCEDURE");
        }
        final String database = target.length >= 3 ? target[target.length - 3] : catalog.getCurrentDatabase();
        return catalog.databaseExact(database).schemaExact(target[target.length - 2]);
    }

    /**
     * ALTER SEQUENCE … RENAME TO. The new name resolves as a created name does, so an unqualified one names
     * the session's schema and a two-part one a schema of the session's database, and the sequence MOVES there
     * with its value. With no current database either is refused as CREATE SEQUENCE is. A missing schema or
     * database is refused by name, and a name a sequence there already holds, this one included, is refused
     * as written: {@code Object 'OTHER.TAKEN' already exists.} A table of that name is no obstacle
     * (live-verified).
     */
    private void renameSequence(final Schema source, final Sequence sequence, final String[] target) {
        if (target.length < 3 && catalog.getCurrentDatabase() == null) {
            throw NoCurrentDatabaseRefusal.naming("CREATE SEQUENCE");
        }
        final String database = target.length == 3 ? target[0] : catalog.getCurrentDatabase();
        final String schemaName = target.length >= 2 ? target[target.length - 2] : catalog.getCurrentSchema();
        final Schema destination = catalog.databaseExact(database).schemaExact(schemaName);
        final String newName = target[target.length - 1];
        if (destination.hasSequenceExact(newName)) {
            final StringBuilder written = new StringBuilder();
            for (final String part : target) {
                written.append(written.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
            }
            throw new RuntimeException(SqlCompilationError.of("Object '" + written + "' already exists."));
        }
        final String oldName = sequence.getName();
        source.dropSequence(oldName);
        sequence.setName(newName);
        destination.addSequence(sequence);
        logger.trace("Renamed sequence {} to {}", oldName, String.join(".", target));
    }

    public Object handleUseStatement(final FrostlakeParser.UseStatementContext ctx) {
        if (ctx.DATABASE() != null) {
            final String dbName = queryExecutor.resolveObjectName(ctx.objectName());
            catalog.useDatabaseReference(dbName);
            logger.trace("Using database: {}", dbName);
        } else if (ctx.SCHEMA() != null) {
            // Parts come from the parse tree (or the IDENTIFIER() value per dotted level): a quoted
            // schema name containing a dot is ONE part, not a database.schema pair.
            final String[] parts = catalog.withoutAccount(queryExecutor.resolveObjectNameParts(ctx.objectName()), 2);
            catalog.useSchemaReference(parts.length >= 2 ? parts[0] : null, parts[parts.length >= 2 ? 1 : 0]);
            logger.trace("Using schema: {}", String.join(".", parts));
        } else if (ctx.WAREHOUSE() != null) {
            final String warehouseName = queryExecutor.resolveObjectName(ctx.objectName());
            catalog.useWarehouse(warehouseName);
            logger.trace("Using warehouse: {}", warehouseName);
        } else if (ctx.SECONDARY() != null) {
            if (ctx.ALL() != null) {
                catalog.useSecondaryRoles("ALL");
                logger.trace("Using secondary roles: ALL");
            } else {
                // USE SECONDARY ROLES r1, r2, ... — activate each named role.
                for (final FrostlakeParser.IdentifierContext role : ctx.identifier()) {
                    catalog.useSecondaryRoles(getText(role));
                    logger.trace("Using secondary role: {}", getText(role));
                }
            }
        } else if (ctx.ROLE() != null) {
            final String roleName = queryExecutor.resolveObjectName(ctx.objectName());
            catalog.useRole(roleName);
            logger.trace("Using role: {}", roleName);
        } else if (ctx.objectName() != null) {
            // USE <name> with no object kind: one part names a database, two a schema of a database
            // (live-verified); a third part is refused in the account's own sentence.
            final String[] parts = queryExecutor.resolveObjectNameParts(ctx.objectName());
            if (parts.length == 1) {
                catalog.useDatabaseReference(parts[0]);
            } else if (parts.length == 2) {
                catalog.useSchemaReference(parts[0], parts[1]);
            } else {
                throw new RuntimeException(SqlCompilationError.useNameForm());
            }
            logger.trace("Using: {}", String.join(".", parts));
        }
        return null;
    }

    public Object handleUndropStatement(final FrostlakeParser.UndropStatementContext ctx) {
        final Object restored = undrop(ctx);
        if (ctx.ICEBERG() != null && restored == null) {
            final String[] parts = qualifiedNameParts(ctx.qualifiedName());
            return StatusResults.of("Iceberg_table " + parts[parts.length - 1] + " successfully restored.");
        }
        return restored;
    }

    private Object undrop(final FrostlakeParser.UndropStatementContext ctx) {
        if (ctx.DYNAMIC() != null) {
            final String name = getText(ctx.qualifiedName());
            final String[] parts = qualifiedNameParts(ctx.qualifiedName());
            final String databaseName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
            final Schema schema = dynamicTableSchema(parts);
            final String tableName = parts[parts.length - 1].toUpperCase();
            if (schema.hasDynamicTable(tableName)) {
                throw new RuntimeException(SqlCompilationError.of("Object '" + tableName + "' already exists."));
            }
            final DroppedObject dropped = catalog.takeDropped(
                "DYNAMIC TABLE:" + QualifiedName.key(databaseName, schema.getName(), tableName));
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Dynamic_table " + tableName + " did not exist or was purged.");
            }
            schema.addDynamicTable((DynamicTable) dropped.getObject());
            logger.trace("Undropped dynamic table: {}", name);
        } else if (ctx.TABLE() != null) {
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
            final String fqName = QualifiedName.key(databaseName, schema.getName(), tableName);
            final DroppedObject dropped = catalog.takeDropped("TABLE:" + fqName);
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Table " + tableName + " did not exist or was purged.");
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
                catalog.takeDropped("SCHEMA:" + QualifiedName.key(databaseName, schemaName));
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Schema " + schemaName + " did not exist or was purged.");
            }
            final Schema restoredSchema = (Schema) dropped.getObject();
            catalog.getDatabase(databaseName).addSchema(restoredSchema);
            restoreTableStorage(databaseName, Collections.singletonList(restoredSchema), dropped);
            logger.trace("Undropped schema: {}", name);
        } else if (ctx.TAG() != null) {
            final String name = getText(ctx.qualifiedName());
            final String[] parts = qualifiedNameParts(ctx.qualifiedName());
            final String dbName = parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
            final String schemaName = parts.length == 3 ? parts[1]
                : parts.length == 2 ? parts[0] : catalog.getCurrentSchema();
            final DroppedObject dropped = catalog.takeDropped(
                "TAG:" + (dbName + "." + schemaName + "." + parts[parts.length - 1]).toUpperCase());
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Tag " + parts[parts.length - 1] + " did not exist or was purged.");
            }
            catalog.getDatabase(dbName).getSchema(schemaName).addTag((Tag) dropped.getObject());
            logger.trace("Undropped tag: {}", name);
        } else if (ctx.DATABASE() != null) {
            final String name = getText(ctx.identifier());
            final DroppedObject dropped = catalog.takeDropped("DATABASE:" + name);
            if (dropped == null || dropped.getObject() == null) {
                throw new RuntimeException("Database " + name + " did not exist or was purged.");
            }
            final Database restoredDb = (Database) dropped.getObject();
            catalog.restoreDatabase(restoredDb);
            restoreTableStorage(name, restoredDb.getAllSchemas(), dropped);
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
                                     final DroppedObject dropped) {
        final Map<String, List<Row>> tableRows = dropped.getTableRows();
        if (tableRows == null) {
            return;
        }
        final StorageEngine storage = queryExecutor.getStorageEngine();
        for (final Schema schema : schemas) {
            for (final Table table : schema.getTables()) {
                final String fqn = QualifiedName.key(databaseName, schema.getName(), table.getName());
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
            // A permanent table a temporary one hid comes back hidden beneath it.
            for (final Table hidden : schema.getShadowedTables()) {
                final String fqn = QualifiedName.key(databaseName, schema.getName(), hidden.getName());
                if (storage.hasShadowedTable(fqn)) {
                    continue;
                }
                storage.createShadowedTable(fqn, hidden);
                final List<Row> rows = dropped.getHiddenTableRows() == null ? null : dropped.getHiddenTableRows().get(fqn);
                if (rows != null) {
                    for (final Row row : rows) {
                        storage.getShadowedTableStorage(fqn).insert(row);
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
     * When IF NOT EXISTS is specified, suppress ONLY the refusal that the object already exists, and
     * re-throw everything else: a statement live refuses must not become a silent no-op reported as
     * already existing. A TABLE's column list, a SEQUENCE's, FILE FORMAT's or STAGE's options and a
     * FUNCTION's body are all judged before existence on the account, so they refuse even over an existing
     * object; a VIEW and a SCHEMA answer existence first, which their handlers check up front
     * (live-verified). A column, primary-key or constraint sentence that happens to say "already
     * exists" is about the definition, not the object, and is re-thrown too.
     */
    void handleIfNotExists(final boolean ifNotExists, final RuntimeException e, final String objectName) {
        if (!ifNotExists) throw e;
        final String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        // A name another KIND holds is refused whatever IF NOT EXISTS says (live-verified).
        if (!msg.contains("already exists") || msg.contains("already exists as") || msg.contains("column '")
                || msg.contains("primary key") || msg.contains("constraint with")) {
            throw e;
        }
        ConditionalDdlOutcome.createSkipped();
        logger.debug("{} already exists (IF NOT EXISTS)", objectName);
    }

    Schema resolveSchemaFromQualifiedName(final String qualifiedName) {
        final String[] parts = QualifiedName.parse(qualifiedName).parts();
        if (parts.length == 3) {
            // A fully qualified name names its own database and needs no current one.
            return catalog.getDatabase(parts[0]).getSchema(parts[1]);
        }
        final String dbName = catalog.getCurrentDatabase();
        if (dbName == null) throw NoCurrentDatabaseRefusal.forStatement();
        if (parts.length == 2) {
            return catalog.getDatabase(dbName).getSchema(parts[0]);
        }
        return resolveCurrentSchema();
    }

    /** Extract just the object name (last part) from a qualified name, preserving case. */
    String extractObjectName(final String qualifiedName) {
        final String[] parts = QualifiedName.parse(qualifiedName).parts();
        return parts[parts.length - 1];
    }

    Schema resolveCurrentSchema() {
        final String dbName = catalog.getCurrentDatabase();
        final String schemaName = catalog.getCurrentSchema();

        if (dbName == null || schemaName == null) {
            throw NoCurrentDatabaseRefusal.forStatement();
        }

        return catalog.getDatabase(dbName).getSchema(schemaName);
    }

    /**
     * Helper to resolve fully qualified name
     */
    String resolveFullyQualifiedName(final String qualifiedName) {
        final String[] parts = catalog.withoutAccount(QualifiedName.parse(qualifiedName).parts(), 3);

        if (parts.length == 1) {
            final String dbName = catalog.getCurrentDatabase();
            final String schemaName = catalog.getCurrentSchema();
            if (dbName == null || schemaName == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return QualifiedName.key(dbName, schemaName, parts[0]);
        } else if (parts.length == 2) {
            final String dbName = catalog.getCurrentDatabase();
            if (dbName == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return QualifiedName.key(dbName, parts[0], parts[1]);
        } else if (parts.length == 3) {
            return QualifiedName.key(parts[0], parts[1], parts[2]);
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
            final String varName = ctx.SESSION_VAR_REF().getText().substring(1).toUpperCase();
            final SecurityManager sm = queryExecutor != null ? queryExecutor.getSecurityManager() : null;
            final Object val = sm != null ? sm.getSessionContext().getSessionVariable(varName)
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
        final Schema src = catalog.databaseExact(sourceDb).schemaExact(sourceSchema);
        final Schema tgt = catalog.databaseExact(targetDb).schemaExact(targetSchema);
        final StorageEngine storage = queryExecutor.getStorageEngine();

        for (final Table table : tgt.getTables()) {
            final String srcKey = QualifiedName.key(sourceDb, sourceSchema, table.getName());
            final String tgtKey = QualifiedName.key(targetDb, targetSchema, table.getName());
            if (!storage.hasTable(tgtKey)) {
                storage.createTable(tgtKey, table);
            }
            if (src.shadowedTable(table.getName()) != null && storage.hasShadowedTable(srcKey)) {
                // The clone copies the permanent table a temporary one hides, not the temporary table.
                for (final Row row : storage.getShadowedTableStorage(srcKey).scan()) {
                    storage.getTableStorage(tgtKey).insert(new Row(new ArrayList<>(row.getValues())));
                }
            } else if (storage.hasTable(srcKey)) {
                storage.cloneTableData(srcKey, tgtKey);
            }
        }
    }

    void cloneDatabaseData(final String sourceDb, final String targetDb) {
        final Database tgt = catalog.databaseExact(targetDb);
        for (final Schema schema : tgt.getAllSchemas()) {
            if ("INFORMATION_SCHEMA".equals(schema.getName())) continue;
            cloneSchemaData(sourceDb, schema.getName(), targetDb, schema.getName());
        }
    }

    String extractBodyDefinition(final FrostlakeParser.BodyDefinitionContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            return extractStringLiteral(ctx.STRING_LITERAL());
        } else if (ctx.DOLLAR_QUOTED_STRING() != null) {
            final String text = ctx.DOLLAR_QUOTED_STRING().getText();
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
                ? queryExecutor.getSecurityManager().getSessionContext().getSessionVariable(varName)
                : queryExecutor != null ? queryExecutor.getSessionVariables().get(varName) : null;
            return val != null ? val.toString() : "X-Small";
        }
        if (prop.identifier() != null) {
            return getText(prop.identifier());
        }
        return extractStringLiteral(prop.STRING_LITERAL());
    }

    /**
     * WAREHOUSE_TYPE's vocabulary, and the sentence for a value outside it —
     * {@code invalid property 'NOSUCH' for 'WAREHOUSE_TYPE'}, which is NOT the wording either of its
     * neighbours uses. The value is stored UPPER-CASED whatever case it was written in, because that is
     * what SHOW WAREHOUSES reports; a bare STANDARD and a quoted 'standard' reach the same stored value.
     */
    private String warehouseTypeValue(final String written) {
        final String canonical = written.toUpperCase();
        if (canonical.equals("STANDARD") || canonical.equals("SNOWPARK-OPTIMIZED") || canonical.equals("ADAPTIVE")) {
            return canonical;
        }
        throw new RuntimeException(SqlCompilationError.invalidPropertyFor(written, "WAREHOUSE_TYPE"));
    }

    /** The documented RESOURCE_CONSTRAINT values, in their documented spelling. */
    private static final String[] RESOURCE_CONSTRAINTS = {
        "STANDARD_GEN_1", "STANDARD_GEN_2", "MEMORY_1X", "MEMORY_1X_x86", "MEMORY_16X", "MEMORY_16X_x86",
        "MEMORY_64X", "MEMORY_64X_x86",
    };

    /** A RESOURCE_CONSTRAINT value in its documented spelling, matched ignoring case; else refused. */
    private static String resourceConstraintValue(final String written) {
        for (final String constraint : RESOURCE_CONSTRAINTS) {
            if (constraint.equalsIgnoreCase(written)) {
                return constraint;
            }
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForProperty(written, "RESOURCE_CONSTRAINT"));
    }

    /**
     * Refuses a statement whose memory resource constraint does not fit the warehouse's type — the type the same
     * statement sets, else the warehouse's own — before any property is applied: only a Snowpark-optimized
     * warehouse takes one.
     */
    void requireResourceConstraintFits(final Warehouse warehouse,
                                       final List<FrostlakeParser.WarehousePropertyContext> properties) {
        String type = warehouse.getWarehouseType();
        for (final FrostlakeParser.WarehousePropertyContext prop : properties) {
            if (prop.WAREHOUSE_TYPE() != null) {
                type = prop.STANDARD() != null ? "STANDARD" : prop.ADAPTIVE() != null ? "ADAPTIVE"
                    : extractStringLiteral(prop.STRING_LITERAL()).toUpperCase();
            }
        }
        for (final FrostlakeParser.WarehousePropertyContext prop : properties) {
            if (prop.RESOURCE_CONSTRAINT() != null && !"SNOWPARK-OPTIMIZED".equals(type)) {
                final String written = prop.STRING_LITERAL() != null ? extractStringLiteral(prop.STRING_LITERAL())
                    : prop.identifier().getText();
                final String constraint = resourceConstraintValue(written);
                if (constraint.startsWith("MEMORY_")) {
                    throw new RuntimeException(SqlCompilationError.of("invalid property combination 'WAREHOUSE_TYPE'='"
                        + type + "' and 'RESOURCE_CONSTRAINT'='" + constraint + "'"));
                }
            }
        }
    }

    /**
     * SCALING_POLICY's vocabulary, spelled bare or quoted. The quoted form is the one a real script
     * writes and Frostlake could not parse at all, so a legal statement was refused; its value is
     * validated here with the property's OWN sentence,
     * {@code invalid value 'NOSUCH' for property 'SCALING_POLICY'}.
     */
    private ScalingPolicy scalingPolicyValue(final FrostlakeParser.WarehousePropertyContext prop) {
        if (prop.ECONOMY() != null) {
            return ScalingPolicy.ECONOMY;
        }
        if (prop.STANDARD() != null) {
            return ScalingPolicy.STANDARD;
        }
        final String written = extractStringLiteral(prop.STRING_LITERAL());
        if (written.equalsIgnoreCase("ECONOMY")) {
            return ScalingPolicy.ECONOMY;
        }
        if (written.equalsIgnoreCase("STANDARD")) {
            return ScalingPolicy.STANDARD;
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForProperty(written, "SCALING_POLICY"));
    }

    WarehouseSize parseWarehouseSizeString(final String sizeStr) {
        switch (sizeStr.toUpperCase().replace("-", "_")) {
            case "X_SMALL": case "XSMALL": return WarehouseSize.X_SMALL;
            case "SMALL": return WarehouseSize.SMALL;
            case "MEDIUM": return WarehouseSize.MEDIUM;
            case "LARGE": return WarehouseSize.LARGE;
            case "X_LARGE": case "XLARGE": return WarehouseSize.X_LARGE;
            case "2X_LARGE": case "X2LARGE": case "XXLARGE": return WarehouseSize.X2_LARGE;
            case "3X_LARGE": case "X3LARGE": case "XXXLARGE": return WarehouseSize.X3_LARGE;
            case "4X_LARGE": case "X4LARGE": return WarehouseSize.X4_LARGE;
            case "5X_LARGE": case "X5LARGE": return WarehouseSize.X5_LARGE;
            case "6X_LARGE": case "X6LARGE": return WarehouseSize.X6_LARGE;
            default:
                // Anything else is refused, as live refuses it: a warehouse cannot carry a size that
                // does not exist, and falling back to X-Small gave one silently. The value is echoed
                // exactly as written.
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid type of property '" + sizeStr + "' for 'WAREHOUSE_SIZE'"));
        }
    }

    void applyWarehouseProperties(final Warehouse warehouse, final FrostlakeParser.WarehousePropertiesContext ctx) {
        // A warehouse property may be given ONCE (live-verified on WAREHOUSE_SIZE and AUTO_SUSPEND);
        // the property's name is its first token, read off the parse tree.
        final List<String> keys = new ArrayList<>();
        for (final FrostlakeParser.WarehousePropertyContext prop : ctx.warehouseProperty()) {
            keys.add(prop.getStart().getText());
        }
        PropertyDuplicates.reject(keys);
        requireResourceConstraintFits(warehouse, ctx.warehouseProperty());
        for (final FrostlakeParser.WarehousePropertyContext prop : ctx.warehouseProperty()) {
            // WAIT_FOR_COMPLETION belongs to ALTER WAREHOUSE … SET only: a CREATE resizes nothing.
            if (prop.WAIT_FOR_COMPLETION() != null) {
                throw new RuntimeException(SqlCompilationError.of(
                    "invalid property 'WAIT_FOR_COMPLETION' for 'WAREHOUSE'"));
            }
            applyWarehouseProperty(warehouse, prop);
        }
    }

    public void applyWarehousePropertyPublic(final Warehouse warehouse, final FrostlakeParser.WarehousePropertyContext prop) {
        applyWarehouseProperty(warehouse, prop);
    }

    private void applyWarehouseProperty(final Warehouse warehouse, final FrostlakeParser.WarehousePropertyContext prop) {
        if (prop.WAREHOUSE_TYPE() != null) {
            final String type = prop.STANDARD() != null ? "STANDARD"
                : prop.ADAPTIVE() != null ? "ADAPTIVE" : extractStringLiteral(prop.STRING_LITERAL());
            warehouse.setWarehouseType(warehouseTypeValue(type));
        } else if (prop.RESOURCE_CONSTRAINT() != null) {
            final String written = prop.STRING_LITERAL() != null ? extractStringLiteral(prop.STRING_LITERAL())
                : prop.identifier().getText();
            final String constraint = resourceConstraintValue(written);
            // The two standard constraints are the generation spelled another way, and only GENERATION sets it.
            if (constraint.startsWith("STANDARD_GEN_")) {
                // The account's sentence ends with a newline, as its property refusals do.
                throw new RuntimeException("Cannot set resource constraint to '" + constraint
                    + "'. Use the GENERATION property to set warehouse hardware generation.\n");
            }
            warehouse.setResourceConstraint(constraint);
        } else if (prop.WAIT_FOR_COMPLETION() != null) {
            // A resize here completes at once, so there is nothing to wait for.
            logger.trace("WAIT_FOR_COMPLETION accepted on warehouse {}", warehouse.getName());
        } else if (prop.WAREHOUSE_SIZE() != null) {
            warehouse.setSize(parseWarehouseSizeString(warehouseSizeValue(prop)));
        } else if (prop.AUTO_SUSPEND() != null) {
            // A negative suspend interval is ACCEPTED on a real account, so it parses and stores.
            final int suspendSeconds = Integer.parseInt(prop.INTEGER_LITERAL().getText());
            warehouse.setAutoSuspendSeconds(prop.MINUS() != null ? -suspendSeconds : suspendSeconds);
        } else if (prop.AUTO_RESUME() != null) {
            warehouse.setAutoResume(prop.booleanValue().TRUE() != null);
        } else if (prop.MIN_CLUSTER_COUNT() != null) {
            final int minCount = Integer.parseInt(prop.INTEGER_LITERAL().getText());
            // Zero clusters refuses with the single-quoted property shape, live-verified.
            if (minCount < 1) {
                throw new RuntimeException(SqlCompilationError.invalidValueForProperty(
                    String.valueOf(minCount), "MIN_CLUSTER_COUNT"));
            }
            warehouse.setMinClusterCount(minCount);
        } else if (prop.MAX_CLUSTER_COUNT() != null) {
            warehouse.setMaxClusterCount(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.SCALING_POLICY() != null) {
            warehouse.setScalingPolicy(scalingPolicyValue(prop));
        } else if (prop.INITIALLY_SUSPENDED() != null) {
            warehouse.setInitiallySuspended(prop.booleanValue().TRUE() != null);
            if (prop.booleanValue().TRUE() != null) {
                warehouse.setState(WarehouseState.SUSPENDED);
            }
        } else if (prop.RESOURCE_MONITOR() != null) {
            warehouse.setResourceMonitor(getText(prop.identifier()));
        } else if (prop.MAX_CONCURRENCY_LEVEL() != null) {
            warehouse.markParameterSet("MAX_CONCURRENCY_LEVEL");
            warehouse.setMaxConcurrencyLevel(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.STATEMENT_QUEUED_TIMEOUT_IN_SECONDS() != null) {
            warehouse.markParameterSet("STATEMENT_QUEUED_TIMEOUT_IN_SECONDS");
            warehouse.setStatementQueuedTimeoutSeconds(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.STATEMENT_TIMEOUT_IN_SECONDS() != null) {
            warehouse.markParameterSet("STATEMENT_TIMEOUT_IN_SECONDS");
            warehouse.setStatementTimeoutSeconds(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.ENABLE_QUERY_ACCELERATION() != null) {
            warehouse.setEnableQueryAcceleration(prop.booleanValue().TRUE() != null);
        } else if (prop.QUERY_ACCELERATION_MAX_SCALE_FACTOR() != null) {
            warehouse.setQueryAccelerationMaxScaleFactor(Integer.parseInt(prop.INTEGER_LITERAL().getText()));
        } else if (prop.GENERATION() != null) {
            warehouse.setGeneration(prop.STRING_LITERAL() != null
                ? extractStringLiteral(prop.STRING_LITERAL())
                : prop.INTEGER_LITERAL().getText());
            warehouse.setResourceConstraint(null);
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
