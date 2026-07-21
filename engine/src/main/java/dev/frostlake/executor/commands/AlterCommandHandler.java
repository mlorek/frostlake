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
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.Taggable;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Pipe;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.ScheduleType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.SecurableObjectType;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@code ALTER <object-type> …} statements for DATABASE / SCHEMA / MASKING &amp; ROW ACCESS
 * POLICY / TABLE / VIEW / STREAM / TASK / PIPE / WAREHOUSE / STAGE / USER / ROLE / SESSION / TAG,
 * extracted verbatim from {@link SQLCommandVisitor#visitAlterStatement}. Leaf object types the visitor
 * historically routed to the DDL side (FILE FORMAT / FUNCTION / PROCEDURE / DYNAMIC TABLE /
 * MATERIALIZED VIEW / SEQUENCE) are still forwarded to {@link DDLCommandHandler#handleAlterStatement}.
 * Parse-text extraction ({@code getText}, {@code extractStringLiteral}, {@code getOriginalText}) and the
 * shared engine helpers ({@code parseDataType}, {@code parseLiteral}, {@code resolveCurrentSchema}) are
 * reached through the {@code visitor} back-reference so their exact original behavior is preserved; the
 * task scheduler is read live via {@link SQLCommandVisitor#getTaskScheduler()} (it is wired onto the
 * visitor post-construction).
 */
public class AlterCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AlterCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;
    private final DDLCommandHandler ddlHandler;

    public AlterCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                               final SQLCommandVisitor visitor, final DDLCommandHandler ddlHandler) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
        this.ddlHandler = ddlHandler;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handle(final FrostlakeParser.AlterStatementContext ctx) {
        boolean ifExists = ctx.if_exists() != null;
        try {
            if (ctx.DATABASE() != null) {
                String dbName = visitor.getText(ctx.identifier());
                Database database = catalog.getDatabase(dbName);
                checkAlter(SecurableObjectType.DATABASE, dbName);

                if (ctx.databaseAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.databaseAction().identifier());
                    catalog.renameDatabase(dbName, newName);
                    logger.trace("Renamed database {} to {}", dbName, newName);
                } else if (ctx.databaseAction().COMMENT() != null) {
                    String comment = visitor.extractStringLiteral(ctx.databaseAction().STRING_LITERAL());
                    database.setComment(comment);
                    logger.trace("Set comment on database: {}", dbName);
                } else if (ctx.databaseAction().READ_ONLY() != null) {
                    if (ctx.databaseAction().UNSET() != null) {
                        database.setReadOnly(false);
                    } else {
                        database.setReadOnly(ctx.databaseAction().booleanValue().TRUE() != null);
                    }
                    logger.trace("Set read_only={} on database: {}", database.isReadOnly(), dbName);
                } else if (ctx.databaseAction().tagSet() != null) {
                    applyTagSet(database, ctx.databaseAction().tagSet());
                } else if (ctx.databaseAction().tagUnset() != null) {
                    applyTagUnset(database, ctx.databaseAction().tagUnset());
                }

            } else if (ctx.SCHEMA() != null) {
                String schemaName = visitor.getText(ctx.qualifiedName());
                Schema schema = catalog.resolveSchema(QualifiedName.of(qualifiedNameParts(ctx.qualifiedName())));
                checkAlter(SecurableObjectType.SCHEMA, schemaName);

                if (ctx.schemaAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.schemaAction().identifier());
                    schema.rename(newName);
                    logger.trace("Renamed schema {} to {}", schemaName, newName);
                } else if (ctx.schemaAction().COMMENT() != null) {
                    String comment = visitor.extractStringLiteral(ctx.schemaAction().STRING_LITERAL());
                    schema.setComment(comment);
                    logger.trace("Set comment on schema: {}", schemaName);
                } else if (ctx.schemaAction().tagSet() != null) {
                    applyTagSet(schema, ctx.schemaAction().tagSet());
                } else if (ctx.schemaAction().tagUnset() != null) {
                    applyTagUnset(schema, ctx.schemaAction().tagUnset());
                }

            } else if (ctx.MASKING() != null && ctx.POLICY() != null) {
                String policyName = visitor.getText(ctx.qualifiedName());
                String newName = visitor.getText(ctx.maskingPolicyAction().identifier());
                try {
                    checkAlter(SecurableObjectType.MASKING_POLICY, policyName);
                    catalog.renameMaskingPolicy(policyName, newName);
                    logger.trace("Renamed masking policy {} to {}", policyName, newName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Masking policy does not exist (IF EXISTS): {}", policyName);
                }

            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                String policyName = visitor.getText(ctx.qualifiedName());
                String newName = visitor.getText(ctx.rowAccessPolicyAction().identifier());
                try {
                    checkAlter(SecurableObjectType.ROW_ACCESS_POLICY, policyName);
                    catalog.renameRowAccessPolicy(policyName, newName);
                    logger.trace("Renamed row access policy {} to {}", policyName, newName);
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Row access policy does not exist (IF EXISTS): {}", policyName);
                }

            } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
                return ddlHandler.handleAlterStatement(ctx);
            } else if (ctx.FUNCTION() != null || ctx.PROCEDURE() != null) {
                return ddlHandler.handleAlterStatement(ctx);
            } else if (ctx.DYNAMIC() != null && ctx.TABLE() != null) {
                checkAlter(SecurableObjectType.DYNAMIC_TABLE, visitor.getText(ctx.qualifiedName()));
                return ddlHandler.handleAlterStatement(ctx);
            } else if (ctx.TABLE() != null) {
                String tableName = visitor.getText(ctx.qualifiedName());

                try {
                    Table table = catalog.resolveTable(QualifiedName.of(qualifiedNameParts(ctx.qualifiedName())));
                    checkAlter(SecurableObjectType.TABLE, tableName);

                    if (ctx.tableAction().SWAP() != null) {
                        // ALTER TABLE a SWAP WITH b — exchange the two tables' row storage.
                        String other = visitor.getText(ctx.tableAction().qualifiedName());
                        queryExecutor.swapTables(tableName, other);
                        logger.trace("Swapped table {} with {}", tableName, other);
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().COLUMN() == null) {
                        // RENAME table. A qualified target that names a different schema/database MOVES the
                        // table there (Snowflake semantics); otherwise it is renamed in place. Both the
                        // catalog entry and the row storage are re-keyed so the new location is queryable.
                        final String[] targetParts = qualifiedNameParts(ctx.tableAction().qualifiedName());
                        final String[] srcParts = QualifiedName.parse(
                            queryExecutor.getFullyQualifiedTableName(tableName)).parts();
                        final String srcDb = srcParts[0];
                        final String srcSchema = srcParts[1];
                        final String targetDb;
                        final String targetSchema;
                        final String newName;
                        if (targetParts.length == 3) {
                            targetDb = targetParts[0];
                            targetSchema = targetParts[1];
                            newName = targetParts[2];
                        } else if (targetParts.length == 2) {
                            targetDb = srcDb;
                            targetSchema = targetParts[0];
                            newName = targetParts[1];
                        } else {
                            targetDb = srcDb;
                            targetSchema = srcSchema;
                            newName = targetParts[0];
                        }
                        if (targetDb.equalsIgnoreCase(srcDb) && targetSchema.equalsIgnoreCase(srcSchema)) {
                            queryExecutor.renameTableStorage(tableName, newName);
                            catalog.renameTable(tableName, newName);
                            logger.trace("Renamed table {} to {}", tableName, newName);
                        } else {
                            // Validate + move the catalog entry first (throws cleanly if the destination is
                            // missing or the name is taken), then re-key storage, so a rejected move mutates
                            // nothing.
                            catalog.moveTable(tableName, targetDb, targetSchema, newName);
                            queryExecutor.moveTableStorage(tableName, targetDb, targetSchema, newName);
                            logger.trace("Moved table {} to {}.{}.{}", tableName, targetDb, targetSchema, newName);
                        }
                    } else if (ctx.tableAction().ADD() != null && ctx.tableAction().columnDef() != null) {
                        // ADD COLUMN — build the full column (data type + DEFAULT / NOT NULL / …) via the
                        // shared parser, then backfill existing rows so their width matches the new schema.
                        FrostlakeParser.ColumnDefContext colDef = ctx.tableAction().columnDef();
                        String colName = visitor.getText(colDef.identifier());
                        boolean ifNotExists = ctx.tableAction().if_not_exists() != null;

                        if (ifNotExists && table.hasColumn(colName)) {
                            logger.debug("Column already exists (IF NOT EXISTS): {}", colName);
                        } else {
                            final TableColumn newColumn = ddlHandler.getColumnParser().parseSingleColumnDef(colDef);
                            table.addColumn(newColumn);
                            queryExecutor.backfillColumn(tableName, newColumn);
                            logger.trace("Added column {} to table {}", colName, tableName);
                        }
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().COLUMN() != null
                            && ctx.tableAction().ALTER() == null) {
                        // DROP COLUMN (the genuine table-level form; ALTER COLUMN ... DROP NOT NULL / DROP DEFAULT
                        // also carry DROP + COLUMN tokens, so exclude them here and let them fall through below).
                        String colName = visitor.getText(ctx.tableAction().identifier(0));
                        boolean ifColumnExists = ctx.tableAction().if_exists() != null;

                        try {
                            table.dropColumn(colName);
                            logger.trace("Dropped column {} from table {}", colName, tableName);
                        } catch (final RuntimeException e) {
                            if (!ifColumnExists) {
                                throw e;
                            }
                            logger.debug("Column does not exist (IF EXISTS): {}", colName);
                        }
                    } else if (ctx.tableAction().DROP() != null && ctx.tableAction().CONSTRAINT() != null) {
                        // DROP CONSTRAINT
                        String constraintName = visitor.getText(ctx.tableAction().identifier(0));
                        table.dropForeignKey(constraintName);
                        logger.trace("Dropped constraint {} from table {}", constraintName, tableName);
                    } else if (ctx.tableAction().RENAME() != null && ctx.tableAction().COLUMN() != null) {
                        // RENAME COLUMN
                        String oldName = visitor.getText(ctx.tableAction().identifier(0));
                        String newName = visitor.getText(ctx.tableAction().identifier(1));
                        table.renameColumn(oldName, newName);
                        logger.trace("Renamed column {} to {} in table {}", oldName, newName, tableName);
                    } else if (ctx.tableAction().ALTER() != null && ctx.tableAction().MASKING() != null) {
                        // ALTER COLUMN col SET/UNSET MASKING POLICY
                        String colName = visitor.getText(ctx.tableAction().identifier(0));
                        TableColumn col = table.getColumn(colName);
                        if (ctx.tableAction().UNSET() != null) {
                            col.setMaskingPolicyName(null);
                        } else {
                            String policyName = visitor.getText(ctx.tableAction().qualifiedName());
                            col.setMaskingPolicyName(policyName.toUpperCase());
                        }
                        logger.trace("Set/unset masking policy on column {}.{}", tableName, colName);
                    } else if (ctx.tableAction().ALTER() != null && ctx.tableAction().NULL() != null) {
                        // ALTER COLUMN col SET/DROP NOT NULL
                        String colName = visitor.getText(ctx.tableAction().identifier(0));
                        table.getColumn(colName).setNullable(ctx.tableAction().DROP() != null);
                        logger.trace("Set nullability on column {}.{}", tableName, colName);
                    } else if (ctx.tableAction().ALTER() != null && ctx.tableAction().DEFAULT() != null) {
                        // ALTER COLUMN col SET/DROP DEFAULT
                        String colName = visitor.getText(ctx.tableAction().identifier(0));
                        table.getColumn(colName).setDefaultValue(
                            ctx.tableAction().SET() != null ? ctx.tableAction().defaultExpression().getText() : null);
                        logger.trace("Set/drop default on column {}.{}", tableName, colName);
                    } else if (ctx.tableAction().ALTER() != null) {
                        // ALTER COLUMN data type
                        String colName = visitor.getText(ctx.tableAction().identifier(0));
                        DataType newDataType = visitor.parseDataType(ctx.tableAction().dataTypeName(), ctx.tableAction().typeParameters());
                        table.alterColumnType(colName, newDataType);
                        logger.trace("Altered column {} type in table {}", colName, tableName);
                    } else if (ctx.tableAction().COMMENT() != null) {
                        String comment = visitor.extractStringLiteral(ctx.tableAction().STRING_LITERAL());
                        table.setComment(comment);
                        logger.trace("Set comment on table: {}", tableName);
                    } else if (ctx.tableAction().CLUSTER() != null) {
                        // CLUSTER BY
                        List<String> clusterKeys = new ArrayList<>();
                        for (final FrostlakeParser.ExpressionContext exprCtx : ctx.tableAction().expressionList().expression()) {
                            clusterKeys.add(exprCtx.getText());
                        }
                        table.setClusterKeys(clusterKeys);
                        logger.trace("Set cluster keys on table {}: {}", tableName, clusterKeys);
                    } else if (ctx.tableAction().tableConstraint() != null) {
                        // ADD constraint
                        FrostlakeParser.TableConstraintContext constraintCtx = ctx.tableAction().tableConstraint();
                        String constraintName = null;
                        if (constraintCtx.constraintName() != null) {
                            constraintName = visitor.getText(constraintCtx.constraintName().identifier());
                        }

                        if (constraintCtx.PRIMARY() != null) {
                            // PRIMARY KEY constraint - add to table's primary keys list
                            List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                columns.add(visitor.getText(idCtx));
                            }
                            table.addPrimaryKeyConstraint(columns);
                            logger.trace("Added PRIMARY KEY constraint to table {}: {}", tableName, columns);
                        } else if (constraintCtx.UNIQUE() != null) {
                            // UNIQUE constraint
                            List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                columns.add(visitor.getText(idCtx));
                            }
                            table.addUniqueConstraint(columns);
                            logger.trace("Added UNIQUE constraint to table {}: {}", tableName, columns);
                        } else if (constraintCtx.FOREIGN() != null) {
                            // FOREIGN KEY constraint
                            List<String> columns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(0).identifier()) {
                                columns.add(visitor.getText(idCtx));
                            }
                            String referencedTable = visitor.getText(constraintCtx.qualifiedName());
                            List<String> referencedColumns = new ArrayList<>();
                            for (final FrostlakeParser.IdentifierContext idCtx : constraintCtx.identifierList(1).identifier()) {
                                referencedColumns.add(visitor.getText(idCtx));
                            }

                            String onDelete = null;
                            String onUpdate = null;
                            if (constraintCtx.referentialActions() != null) {
                                for (final FrostlakeParser.ReferentialActionContext actionCtx : constraintCtx.referentialActions().referentialAction()) {
                                    if (actionCtx.DELETE() != null) {
                                        onDelete = actionCtx.referentialOption().getText();
                                    } else if (actionCtx.UPDATE() != null) {
                                        onUpdate = actionCtx.referentialOption().getText();
                                    }
                                }
                            }

                            ForeignKeyConstraint fk = new ForeignKeyConstraint(
                                constraintName != null ? constraintName : "FK_" + tableName + "_" + System.currentTimeMillis(),
                                columns,
                                referencedTable,
                                referencedColumns,
                                onDelete,
                                onUpdate
                            );
                            table.addForeignKey(fk);
                            logger.trace("Added FOREIGN KEY constraint to table {}: {} -> {}", tableName, columns, referencedTable);
                        }
                    } else if (ctx.tableAction().ROW() != null && ctx.tableAction().ADD() != null) {
                        // ADD ROW ACCESS POLICY policyName ON (col1, col2)
                        String policyName = visitor.getText(ctx.tableAction().qualifiedName());
                        List<String> cols = new ArrayList<>();
                        if (ctx.tableAction().identifierList() != null) {
                            for (final FrostlakeParser.IdentifierContext id : ctx.tableAction().identifierList().identifier()) {
                                cols.add(visitor.getText(id));
                            }
                        }
                        table.setRowAccessPolicyName(policyName.toUpperCase());
                        table.setRowAccessPolicyColumns(cols);
                        logger.trace("Added row access policy {} to table {}", policyName, tableName);
                    } else if (ctx.tableAction().ROW() != null && ctx.tableAction().DROP() != null) {
                        // DROP ROW ACCESS POLICY
                        table.setRowAccessPolicyName(null);
                        table.setRowAccessPolicyColumns(new ArrayList<>());
                        logger.trace("Dropped row access policy from table {}", tableName);
                    } else if (ctx.tableAction().columnTagAction() != null) {
                        FrostlakeParser.ColumnTagActionContext cta = ctx.tableAction().columnTagAction();
                        String colName = visitor.getText(cta.identifier());
                        TableColumn col = table.getColumn(colName);
                        if (col == null) {
                            throw new RuntimeException("Column does not exist: " + colName);
                        }
                        if (cta.tagSet() != null) {
                            applyTagSet(col, cta.tagSet());
                        } else {
                            applyTagUnset(col, cta.tagUnset());
                        }
                        logger.trace("Applied column tag action on {}.{}", tableName, colName);
                    } else if (ctx.tableAction().tagSet() != null) {
                        applyTagSet(table, ctx.tableAction().tagSet());
                        logger.trace("Set tag(s) on table {}", tableName);
                    } else if (ctx.tableAction().tagUnset() != null) {
                        applyTagUnset(table, ctx.tableAction().tagUnset());
                        logger.trace("Unset tag(s) on table {}", tableName);
                    }
                } catch (final RuntimeException e) {
                    if (!ifExists) {
                        throw e;
                    }
                    logger.debug("Table does not exist (IF EXISTS): {}", tableName);
                }

            } else if (ctx.VIEW() != null && ctx.MATERIALIZED() != null) {
                checkAlter(SecurableObjectType.MATERIALIZED_VIEW, visitor.getText(ctx.qualifiedName()));
                return ddlHandler.handleAlterStatement(ctx);

            } else if (ctx.SEQUENCE() != null) {
                return ddlHandler.handleAlterStatement(ctx);

            } else if (ctx.VIEW() != null) {
                String viewName = visitor.getText(ctx.qualifiedName());
                View view = catalog.resolveView(QualifiedName.of(qualifiedNameParts(ctx.qualifiedName())));
                checkAlter(SecurableObjectType.VIEW, viewName);

                if (ctx.viewAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.viewAction().identifier());
                    catalog.renameView(viewName, newName);
                    logger.trace("Renamed view {} to {}", viewName, newName);
                } else if (ctx.viewAction().COMMENT() != null) {
                    String comment = visitor.extractStringLiteral(ctx.viewAction().STRING_LITERAL());
                    view.setComment(comment);
                    logger.trace("Set comment on view: {}", viewName);
                } else if (ctx.viewAction().tagSet() != null) {
                    applyTagSet(view, ctx.viewAction().tagSet());
                } else if (ctx.viewAction().tagUnset() != null) {
                    applyTagUnset(view, ctx.viewAction().tagUnset());
                }

            } else if (ctx.STREAM() != null) {
                String streamQn = visitor.getText(ctx.qualifiedName());
                String[] streamParts = qualifiedNameParts(ctx.qualifiedName());
                String streamName = streamParts[streamParts.length - 1].toUpperCase();
                Schema schema;
                if (streamParts.length == 3) {
                    schema = catalog.getDatabase(streamParts[0]).getSchema(streamParts[1]);
                } else if (streamParts.length == 2) {
                    schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(streamParts[0]);
                } else {
                    schema = visitor.resolveCurrentSchema();
                }
                Stream stream = schema.getStream(streamName);
                checkAlter(SecurableObjectType.STREAM, streamQn);

                final FrostlakeParser.StreamActionContext streamAction = ctx.streamAction();
                if (streamAction.SET() != null && streamAction.COMMENT() != null) {
                    stream.setComment(visitor.extractStringLiteral(streamAction.STRING_LITERAL()));
                    logger.trace("Set comment on stream: {}", streamName);
                } else if (streamAction.UNSET() != null && streamAction.COMMENT() != null) {
                    stream.setComment(null);
                    logger.trace("Unset comment on stream: {}", streamName);
                }

            } else if (ctx.TASK() != null) {
                String taskQn = visitor.getText(ctx.qualifiedName());
                String[] taskParts = qualifiedNameParts(ctx.qualifiedName());
                String taskName = taskParts[taskParts.length - 1];
                Schema schema;
                if (taskParts.length == 3) {
                    schema = catalog.getDatabase(taskParts[0]).getSchema(taskParts[1]);
                } else if (taskParts.length == 2) {
                    schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(taskParts[0]);
                } else {
                    schema = visitor.resolveCurrentSchema();
                }
                Task task = schema.getTask(taskName);
                checkAlter(SecurableObjectType.TASK, taskQn);

                // Canonical scheduler key (DB.SCHEMA.TASK), built identically for RESUME and SUSPEND so the
                // scheduler arms and later cancels the same entry.
                final String taskDbName = taskParts.length == 3 ? taskParts[0] : catalog.getCurrentDatabase();
                final String taskKey = (taskDbName + "." + schema.getName() + "." + taskName).toUpperCase();

                if (ctx.taskAction().RESUME() != null) {
                    // RESUME flips state to STARTED AND arms the scheduler so the task fires on its SCHEDULE
                    // (scheduleTask is a no-op until the scheduler is started). resumeTask sets the state.
                    if (visitor.getTaskScheduler() != null) {
                        visitor.getTaskScheduler().resumeTask(taskKey, task);
                    } else {
                        task.setState(TaskState.STARTED);
                    }
                    logger.trace("Resumed task: {}", taskName);
                } else if (ctx.taskAction().SUSPEND() != null) {
                    // SUSPEND flips state to SUSPENDED AND cancels any armed timer. suspendTask sets the state.
                    if (visitor.getTaskScheduler() != null) {
                        visitor.getTaskScheduler().suspendTask(taskKey, task);
                    } else {
                        task.setState(TaskState.SUSPENDED);
                    }
                    logger.trace("Suspended task: {}", taskName);
                } else if (ctx.taskAction().SET() != null) {
                    for (final FrostlakeParser.TaskOptionContext opt : ctx.taskAction().taskOption()) {
                        applyTaskOption(task, opt);
                    }
                    for (final FrostlakeParser.WarehouseClauseContext wc : ctx.taskAction().warehouseClause()) {
                        task.setWarehouse(warehouseClauseValue(wc));
                    }
                    logger.trace("Set options on task: {}", taskName);
                } else if (ctx.taskAction().UNSET() != null) {
                    for (final FrostlakeParser.TaskParamNameContext pn : ctx.taskAction().taskParamName()) {
                        unsetTaskParam(task, pn.getText().toUpperCase());
                    }
                    logger.trace("Unset params on task: {}", taskName);
                } else if (ctx.taskAction().MODIFY() != null && ctx.taskAction().AS() != null) {
                    // MODIFY AS <sql> replaces the task body.
                    task.setSqlStatement(visitor.getOriginalText(ctx.taskAction().taskBody()).trim());
                    logger.trace("Modified task body: {}", taskName);
                } else if (ctx.taskAction().MODIFY() != null && ctx.taskAction().WHEN() != null) {
                    // MODIFY WHEN <boolean> replaces the run condition.
                    task.setCondition(visitor.getOriginalText(ctx.taskAction().booleanExpr()).trim());
                    logger.trace("Modified task condition: {}", taskName);
                }

            } else if (ctx.PIPE() != null) {
                String pipeName = visitor.getText(ctx.qualifiedName());
                Schema schema = visitor.resolveCurrentSchema();
                Pipe pipe = schema.getPipe(pipeName);
                checkAlter(SecurableObjectType.PIPE, pipeName);
                final FrostlakeParser.PipeActionContext action = ctx.pipeAction();

                if (action.RESUME() != null) {                  // alias for SET PIPE_EXECUTION_PAUSED = FALSE
                    pipe.setPaused(false);
                    logger.trace("Resumed pipe: {}", pipeName);
                } else if (action.PAUSE() != null) {            // alias for SET PIPE_EXECUTION_PAUSED = TRUE
                    pipe.setPaused(true);
                    logger.trace("Paused pipe: {}", pipeName);
                } else if (action.SET() != null) {
                    for (final FrostlakeParser.PipeSetOptionContext opt : action.pipeSetOption()) {
                        String optName = visitor.getText(opt.identifier()).toUpperCase();
                        if ("PIPE_EXECUTION_PAUSED".equals(optName) && opt.booleanValue() != null) {
                            pipe.setPaused(opt.booleanValue().TRUE() != null);
                        } else if ("COMMENT".equals(optName) && opt.STRING_LITERAL() != null) {
                            pipe.setComment(visitor.extractStringLiteral(opt.STRING_LITERAL()));
                        } else {
                            throw new RuntimeException("Unsupported ALTER PIPE SET option: " + optName);
                        }
                    }
                    logger.trace("Set options on pipe: {}", pipeName);
                } else if (action.REFRESH() != null) {
                    // REFRESH triggers a one-time ingest: execute the pipe's COPY INTO … FROM @stage,
                    // optionally narrowed by PREFIX (stage-relative path prefix) and/or MODIFIED_AFTER
                    // (only files last-modified after the given timestamp are loaded).
                    String copy = pipe.getCopyStatement();
                    if (copy != null && !copy.isBlank()) {
                        String refreshPrefix = null;
                        String refreshModifiedAfter = null;
                        for (final FrostlakeParser.PipeRefreshOptionContext opt : action.pipeRefreshOption()) {
                            final String optName = visitor.getText(opt.identifier()).toUpperCase();
                            final String optValue = visitor.extractStringLiteral(opt.STRING_LITERAL());
                            if ("PREFIX".equals(optName)) {
                                refreshPrefix = optValue;
                            } else if ("MODIFIED_AFTER".equals(optName)) {
                                refreshModifiedAfter = optValue;
                            } else {
                                throw new RuntimeException("Unsupported ALTER PIPE REFRESH option: " + optName);
                            }
                        }
                        queryExecutor.executeCopyRefresh(copy, refreshPrefix, refreshModifiedAfter);
                    }
                    logger.trace("Refreshed pipe: {}", pipeName);
                }

            } else if (ctx.WAREHOUSE() != null) {
                String warehouseName = visitor.getText(ctx.identifier());
                Warehouse warehouse = catalog.getWarehouse(warehouseName);
                checkAlter(SecurableObjectType.WAREHOUSE, warehouseName);

                if (ctx.warehouseAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.warehouseAction().identifier());
                    catalog.renameWarehouse(warehouseName, newName);
                    logger.trace("Renamed warehouse {} to {}", warehouseName, newName);
                } else if (ctx.warehouseAction().RESUME() != null) {
                    warehouse.resume();
                    logger.trace("Resumed warehouse: {}", warehouseName);
                } else if (ctx.warehouseAction().SUSPEND() != null) {
                    warehouse.suspend();
                    logger.trace("Suspended warehouse: {}", warehouseName);
                } else if (ctx.warehouseAction().SET() != null) {
                    for (final FrostlakeParser.WarehousePropertyContext prop : ctx.warehouseAction().warehouseProperty()) {
                        applyWarehouseProperty(warehouse, prop);
                    }
                    logger.trace("Altered warehouse: {}", warehouseName);
                } else if (ctx.warehouseAction().tagSet() != null) {
                    applyTagSet(warehouse, ctx.warehouseAction().tagSet());
                } else if (ctx.warehouseAction().tagUnset() != null) {
                    applyTagUnset(warehouse, ctx.warehouseAction().tagUnset());
                }

            } else if (ctx.STAGE() != null) {
                String stageName = visitor.getText(ctx.qualifiedName());
                Stage stage = catalog.getStage(stageName);
                checkAlter(SecurableObjectType.STAGE, stageName);
                FrostlakeParser.StageActionContext stageAction = ctx.stageAction();
                if (stageAction.URL() != null) {
                    stage.setUrl(visitor.extractStringLiteral(stageAction.STRING_LITERAL()));
                    logger.trace("Set URL on stage: {}", stageName);
                } else if (stageAction.FILE_FORMAT() != null) {
                    stage.setFileFormat(visitor.extractStringLiteral(stageAction.STRING_LITERAL()));
                    logger.trace("Set file format on stage: {}", stageName);
                } else if (stageAction.COMMENT() != null) {
                    stage.setComment(visitor.extractStringLiteral(stageAction.STRING_LITERAL()));
                    logger.trace("Set comment on stage: {}", stageName);
                }

            } else if (ctx.USER() != null) {
                String userName = visitor.getText(ctx.identifier());
                User user = catalog.getUser(userName);

                if (ctx.userAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.userAction().identifier());
                    catalog.renameUser(userName, newName);
                    logger.trace("Renamed user {} to {}", userName, newName);
                } else if (ctx.userAction().PASSWORD() != null) {
                    String password = visitor.extractStringLiteral(ctx.userAction().STRING_LITERAL());
                    user.setPassword(password);
                    logger.trace("Changed password for user: {}", userName);
                } else if (ctx.userAction().DEFAULT_ROLE() != null) {
                    String defaultRole = visitor.getText(ctx.userAction().identifier());
                    user.setDefaultRole(defaultRole);
                    logger.trace("Set default role for user {}: {}", userName, defaultRole);
                } else if (ctx.userAction().COMMENT() != null) {
                    String comment = visitor.extractStringLiteral(ctx.userAction().STRING_LITERAL());
                    user.setComment(comment);
                    logger.trace("Set comment on user: {}", userName);
                }

            } else if (ctx.ROLE() != null) {
                String roleName = visitor.getText(ctx.identifier());
                Role role = catalog.getRole(roleName);

                if (ctx.roleAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.roleAction().identifier());
                    catalog.renameRole(roleName, newName);
                    logger.trace("Renamed role {} to {}", roleName, newName);
                } else if (ctx.roleAction().COMMENT() != null) {
                    String comment = visitor.extractStringLiteral(ctx.roleAction().STRING_LITERAL());
                    role.setComment(comment);
                    logger.trace("Set comment on role: {}", roleName);
                }

            } else if (ctx.SESSION() != null) {
                if (ctx.sessionAction().SET() != null) {
                    String paramName;
                    if (ctx.sessionAction().sessionParameter().MULTI_STATEMENT_COUNT() != null) {
                        paramName = "MULTI_STATEMENT_COUNT";
                    } else {
                        paramName = visitor.getText(ctx.sessionAction().sessionParameter().identifier());
                    }

                    Object value = visitor.parseLiteral(ctx.sessionAction().literal());

                    if (queryExecutor.getDatabaseEngine() != null) {
                        queryExecutor.getDatabaseEngine().getSessionContext().setSessionParameter(paramName, value);
                        logger.trace("Set session parameter {} = {}", paramName, value);
                    }
                }

            } else if (ctx.TAG() != null) {
                String tagName = visitor.getText(ctx.qualifiedName());
                Tag tag = catalog.getTag(tagName);
                checkAlter(SecurableObjectType.TAG, tagName);

                if (ctx.tagAction().ADD() != null) {
                    for (final var stringLiteral : ctx.tagAction().stringLiteralList().STRING_LITERAL()) {
                        tag.addAllowedValue(visitor.extractStringLiteral(stringLiteral));
                    }
                    logger.trace("Added allowed values to tag: {}", tagName);
                } else if (ctx.tagAction().DROP() != null) {
                    for (final var stringLiteral : ctx.tagAction().stringLiteralList().STRING_LITERAL()) {
                        tag.removeAllowedValue(visitor.extractStringLiteral(stringLiteral));
                    }
                    logger.trace("Dropped allowed values from tag: {}", tagName);
                } else if (ctx.tagAction().UNSET() != null) {
                    tag.clearAllowedValues();
                    logger.trace("Unset allowed values for tag: {}", tagName);
                } else if (ctx.tagAction().SET() != null && ctx.tagAction().MASKING() != null) {
                    tag.setMasking(ctx.tagAction().booleanValue().TRUE() != null);
                    logger.trace("Set masking={} on tag: {}", tag.isMasking(), tagName);
                } else if (ctx.tagAction().SET() != null && ctx.tagAction().COMMENT() != null) {
                    String comment = visitor.extractStringLiteral(ctx.tagAction().STRING_LITERAL());
                    tag.setComment(comment);
                    logger.trace("Set comment on tag: {}", tagName);
                } else if (ctx.tagAction().RENAME() != null) {
                    String newName = visitor.getText(ctx.tagAction().identifier());
                    catalog.renameTag(tagName, newName);
                    logger.trace("Renamed tag {} to {}", tagName, newName);
                }
            }

            return null;

        } catch (final Exception e) {
            if (ifExists) {
                logger.debug("ALTER IF EXISTS: object not found, suppressing error: {}", e.getMessage());
                return null;
            }
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute ALTER statement: " + e.getMessage(), e);
        }
    }

    /** Only an owner / administrative role / ALTER-granted role may alter the object. */
    private void checkAlter(final SecurableObjectType objectType, final String objectName) {
        if (queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().checkAlter(objectType, objectName);
        }
    }

    /** Apply a SET TAG action's assignments to a taggable object, validating tag existence and ALLOWED_VALUES. */
    private void applyTagSet(final Taggable target, final FrostlakeParser.TagSetContext set) {
        for (final FrostlakeParser.TagAssignContext assign : set.tagAssign()) {
            final String tagName = visitor.getText(assign.qualifiedName());
            final Tag tag = catalog.getTag(tagName);
            final String value = visitor.extractStringLiteral(assign.STRING_LITERAL());
            final List<String> allowed = tag.getAllowedValues();
            if (!allowed.isEmpty() && !allowed.contains(value)) {
                throw new RuntimeException("Value '" + value + "' is not in ALLOWED_VALUES of tag " + tag.getName());
            }
            target.setTag(tag.getName(), value);
        }
    }

    /** Apply an UNSET TAG action, removing each named tag association from a taggable object. */
    private void applyTagUnset(final Taggable target, final FrostlakeParser.TagUnsetContext unset) {
        for (final FrostlakeParser.QualifiedNameContext qn : unset.qualifiedName()) {
            target.unsetTag(visitor.getText(qn));
        }
    }

    /** Apply one ALTER TASK … SET option to the task (mirrors CREATE TASK's option handling). */
    private void applyTaskOption(final Task task, final FrostlakeParser.TaskOptionContext opt) {
        if (opt.scheduleClause() != null) {
            final String schedule = visitor.extractStringLiteral(opt.scheduleClause().STRING_LITERAL());
            task.setSchedule(schedule);
            task.setScheduleType(schedule.toUpperCase().contains("CRON") ? ScheduleType.CRON : ScheduleType.MINUTES);
        } else if (opt.ALLOW_OVERLAPPING_EXECUTION() != null) {
            task.setAllowOverlappingExecution("TRUE".equalsIgnoreCase(opt.booleanValue().getText()));
        } else if (opt.USER_TASK_TIMEOUT_MS() != null) {
            task.setUserTaskTimeoutMs(Long.parseLong(opt.INTEGER_LITERAL().getText()));
        } else if (opt.SUSPEND_TASK_AFTER_NUM_FAILURES() != null) {
            task.setSuspendTaskAfterNumFailures(Integer.parseInt(opt.INTEGER_LITERAL().getText()));
        } else if (opt.TASK_AUTO_RETRY_ATTEMPTS() != null) {
            task.setTaskAutoRetryAttempts(Integer.parseInt(opt.INTEGER_LITERAL().getText()));
        } else if (opt.USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE() != null) {
            task.setUserTaskManagedInitialWarehouseSize(visitor.extractStringLiteral(opt.STRING_LITERAL()));
        } else if (opt.SERVERLESS_TASK_MAX_STATEMENT_SIZE() != null) {
            task.setServerlessTaskMaxStatementSize(opt.STRING_LITERAL() != null
                ? visitor.extractStringLiteral(opt.STRING_LITERAL()) : visitor.getText(opt.identifier()));
        } else if (opt.TARGET_COMPLETION_INTERVAL() != null) {
            task.setTargetCompletionInterval(visitor.extractStringLiteral(opt.STRING_LITERAL()));
        } else if (opt.USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS() != null) {
            task.setUserTaskMinimumTriggerIntervalInSeconds(Integer.parseInt(opt.INTEGER_LITERAL().getText()));
        } else if (opt.ERROR_INTEGRATION() != null) {
            task.setErrorIntegration(visitor.getText(opt.identifier()));
        } else if (opt.COMMENT() != null) {
            task.setComment(visitor.extractStringLiteral(opt.STRING_LITERAL()));
        }
    }

    /** The warehouse name from an ALTER TASK … SET WAREHOUSE = … clause (string, identifier, or session var). */
    private String warehouseClauseValue(final FrostlakeParser.WarehouseClauseContext wc) {
        if (wc.STRING_LITERAL() != null) {
            return visitor.extractStringLiteral(wc.STRING_LITERAL());
        }
        if (wc.identifier() != null) {
            return visitor.getText(wc.identifier());
        }
        return wc.getText();
    }

    /** Reset one ALTER TASK … UNSET parameter to its default (null / the model's default value). */
    private void unsetTaskParam(final Task task, final String param) {
        switch (param) {
            case "COMMENT":
                task.setComment(null);
                break;
            case "SCHEDULE":
                task.setSchedule(null);
                task.setScheduleType(null);
                break;
            case "WAREHOUSE":
                task.setWarehouse(null);
                break;
            case "USER_TASK_TIMEOUT_MS":
                task.setUserTaskTimeoutMs(3600000L);
                break;
            case "SUSPEND_TASK_AFTER_NUM_FAILURES":
                task.setSuspendTaskAfterNumFailures(10);
                break;
            case "TASK_AUTO_RETRY_ATTEMPTS":
                task.setTaskAutoRetryAttempts(0);
                break;
            case "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE":
                task.setUserTaskManagedInitialWarehouseSize(null);
                break;
            case "SERVERLESS_TASK_MAX_STATEMENT_SIZE":
                task.setServerlessTaskMaxStatementSize(null);
                break;
            case "TARGET_COMPLETION_INTERVAL":
                task.setTargetCompletionInterval(null);
                break;
            case "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS":
                task.setUserTaskMinimumTriggerIntervalInSeconds(30);
                break;
            case "ERROR_INTEGRATION":
                task.setErrorIntegration(null);
                break;
            case "ALLOW_OVERLAPPING_EXECUTION":
                task.setAllowOverlappingExecution(false);
                break;
            default:
                break;
        }
    }

    private void applyWarehouseProperty(final Warehouse warehouse,
                                       final FrostlakeParser.WarehousePropertyContext prop) {
        ddlHandler.applyWarehousePropertyPublic(warehouse, prop);
    }
}
