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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementErrors;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Role;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.User;
import dev.frostlake.metastore.model.View;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles {@code COMMENT ON <object-type> …} statements, extracted verbatim from
 * {@link SQLCommandVisitor#visitCommentStatement}. Catalog lookups run against the injected
 * {@link Catalog}; parse-text extraction ({@code getText}, {@code extractStringLiteral}) and the
 * shared engine helpers ({@code parseDataType}, {@code resolveCurrentSchema}) are reached through the
 * {@code visitor} back-reference so their exact original behavior is preserved.
 */
public class CommentCommandHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CommentCommandHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;

    public CommentCommandHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                                 final SQLCommandVisitor visitor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handle(final FrostlakeParser.CommentStatementContext ctx) {
        try {
            boolean ifExists = ctx.if_exists() != null;
            String comment = visitor.extractStringLiteral(ctx.STRING_LITERAL());

            if (ctx.DATABASE() != null) {
                // COMMENT ON DATABASE
                String dbName = visitor.getText(ctx.identifier());
                Database db;
                try {
                    db = catalog.getDatabase(dbName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Database does not exist (IF EXISTS): {}", dbName);
                        return null;
                    }
                    throw e;
                }
                if (db == null) {
                    if (ifExists) {
                        logger.debug("Database does not exist (IF EXISTS): {}", dbName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Database", dbName));
                }
                db.setComment(comment);
                logger.trace("Set comment on database: {}", dbName);

            } else if (ctx.SCHEMA() != null && ctx.COLUMN() == null) {
                // COMMENT ON SCHEMA
                String schemaName = visitor.getText(ctx.qualifiedName());
                String[] parts = qualifiedNameParts(ctx.qualifiedName());

                Schema schema;
                try {
                    if (parts.length == 1) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw new RuntimeException("No database selected");
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                    } else if (parts.length == 2) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                    } else {
                        throw new RuntimeException("Invalid schema name: " + schemaName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Schema does not exist (IF EXISTS): {}", schemaName);
                        return null;
                    }
                    throw e;
                }

                if (schema == null) {
                    if (ifExists) {
                        logger.debug("Schema does not exist (IF EXISTS): {}", schemaName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Schema", schemaName));
                }
                schema.setComment(comment);
                logger.trace("Set comment on schema: {}", schemaName);

            } else if (ctx.TABLE() != null) {
                // COMMENT ON TABLE
                String tableName = visitor.getText(ctx.qualifiedName());
                Table table;
                try {
                    table = catalog.resolveTable(QualifiedName.of(qualifiedNameParts(ctx.qualifiedName())));
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Table does not exist (IF EXISTS): {}", tableName);
                        return null;
                    }
                    throw e;
                }
                if (table == null) {
                    if (ifExists) {
                        logger.debug("Table does not exist (IF EXISTS): {}", tableName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Table", tableName));
                }
                table.setComment(comment);
                logger.trace("Set comment on table: {}", tableName);

            } else if (ctx.COLUMN() != null) {
                // COMMENT ON COLUMN — the qualified name's parts come from the parse tree's identifier
                // list, not from splitting its flattened text on '.'. (The full text is kept for logging.)
                String qualifiedColumn = visitor.getText(ctx.qualifiedName());
                String[] parts = qualifiedNameParts(ctx.qualifiedName());

                if (parts.length < 2) {
                    throw new RuntimeException("Column name must be qualified: table.column");
                }

                // Last part is column name, rest is table name
                String columnName = parts[parts.length - 1];
                String tableName = String.join(".", Arrays.copyOf(parts, parts.length - 1));

                Table table;
                try {
                    table = catalog.resolveTable(QualifiedName.of(Arrays.copyOf(parts, parts.length - 1)));
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Table does not exist for column comment (IF EXISTS): {}", tableName);
                        return null;
                    }
                    throw e;
                }
                if (table == null) {
                    if (ifExists) {
                        logger.debug("Table does not exist for column comment (IF EXISTS): {}", tableName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Table", tableName));
                }

                // COMMENT ON COLUMN reports a missing column as an OBJECT, the way RENAME COLUMN does —
                // live: "Object 'NONEXISTENT' does not exist or not authorized." — rather than
                // the "invalid identifier" a query would answer with.
                final TableColumn column = table.findColumn(columnName);
                if (column == null) {
                    if (ifExists) {
                        logger.debug("Column does not exist (IF EXISTS): {}", columnName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Object", columnName));
                }
                column.setComment(comment);
                logger.trace("Set comment on column: {}", qualifiedColumn);

            } else if (ctx.VIEW() != null) {
                // COMMENT ON VIEW
                String viewName = visitor.getText(ctx.qualifiedName());
                View view;
                try {
                    view = catalog.resolveView(QualifiedName.of(qualifiedNameParts(ctx.qualifiedName())));
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("View does not exist (IF EXISTS): {}", viewName);
                        return null;
                    }
                    throw e;
                }
                if (view == null) {
                    if (ifExists) {
                        logger.debug("View does not exist (IF EXISTS): {}", viewName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("View", viewName));
                }
                view.setComment(comment);
                logger.trace("Set comment on view: {}", viewName);

            } else if (ctx.FUNCTION() != null) {
                // COMMENT ON FUNCTION — qualified-name parts from the parse tree, not a text split.
                String functionName = visitor.getText(ctx.qualifiedName());
                String[] parts = qualifiedNameParts(ctx.qualifiedName());

                Schema schema;
                String funcName;
                try {
                    if (parts.length == 1) {
                        schema = visitor.resolveCurrentSchema();
                        funcName = parts[0].toUpperCase();
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw new RuntimeException("No database selected");
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        funcName = parts[1].toUpperCase();
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        funcName = parts[2].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid function name: " + functionName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Function schema does not exist (IF EXISTS): {}", functionName);
                        return null;
                    }
                    throw e;
                }

                Function function;
                try {
                    if (ctx.dataTypeList() != null) {
                        List<DataType> argumentTypes = new ArrayList<>();
                        for (final FrostlakeParser.DataTypeNameContext typeCtx : ctx.dataTypeList().dataTypeName()) {
                            DataType dataType = visitor.parseDataType(typeCtx, null);
                            argumentTypes.add(dataType);
                        }
                        function = schema.getFunctionBySignature(funcName, argumentTypes);
                    } else {
                        function = schema.getFunction(funcName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Function does not exist (IF EXISTS): {}", functionName);
                        return null;
                    }
                    throw e;
                }
                function.setComment(comment);
                logger.trace("Set comment on function: {}", functionName);

            } else if (ctx.PROCEDURE() != null) {
                // COMMENT ON PROCEDURE
                String procedureName = visitor.getText(ctx.qualifiedName());
                String[] parts = qualifiedNameParts(ctx.qualifiedName());

                Schema schema;
                String procName;
                try {
                    if (parts.length == 1) {
                        schema = visitor.resolveCurrentSchema();
                        procName = parts[0].toUpperCase();
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw new RuntimeException("No database selected");
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        procName = parts[1].toUpperCase();
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        procName = parts[2].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid procedure name: " + procedureName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Procedure schema does not exist (IF EXISTS): {}", procedureName);
                        return null;
                    }
                    throw e;
                }

                Procedure procedure;
                try {
                    if (ctx.dataTypeList() != null) {
                        List<DataType> argumentTypes = new ArrayList<>();
                        for (final FrostlakeParser.DataTypeNameContext typeCtx : ctx.dataTypeList().dataTypeName()) {
                            DataType dataType = visitor.parseDataType(typeCtx, null);
                            argumentTypes.add(dataType);
                        }
                        procedure = schema.getProcedureBySignature(procName, argumentTypes);
                    } else {
                        procedure = schema.getProcedure(procName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Procedure does not exist (IF EXISTS): {}", procedureName);
                        return null;
                    }
                    throw e;
                }
                procedure.setComment(comment);
                logger.trace("Set comment on procedure: {}", procedureName);

            } else if (ctx.STREAM() != null) {
                // COMMENT ON STREAM
                String streamName = visitor.getText(ctx.qualifiedName());
                String[] parts = qualifiedNameParts(ctx.qualifiedName());

                Schema schema;
                String strmName;
                try {
                    if (parts.length == 1) {
                        schema = visitor.resolveCurrentSchema();
                        strmName = parts[0].toUpperCase();
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw new RuntimeException("No database selected");
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        strmName = parts[1].toUpperCase();
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        strmName = parts[2].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid stream name: " + streamName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Stream schema does not exist (IF EXISTS): {}", streamName);
                        return null;
                    }
                    throw e;
                }

                Stream stream;
                try {
                    stream = schema.getStream(strmName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Stream does not exist (IF EXISTS): {}", streamName);
                        return null;
                    }
                    throw e;
                }
                stream.setComment(comment);
                logger.trace("Set comment on stream: {}", streamName);

            } else if (ctx.TASK() != null) {
                // COMMENT ON TASK
                String taskName = visitor.getText(ctx.qualifiedName());
                String[] parts = qualifiedNameParts(ctx.qualifiedName());

                Schema schema;
                String tskName;
                try {
                    if (parts.length == 1) {
                        schema = visitor.resolveCurrentSchema();
                        tskName = parts[0].toUpperCase();
                    } else if (parts.length == 2) {
                        if (catalog.getCurrentDatabase() == null) {
                            throw new RuntimeException("No database selected");
                        }
                        schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                        tskName = parts[1].toUpperCase();
                    } else if (parts.length == 3) {
                        schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        tskName = parts[2].toUpperCase();
                    } else {
                        throw new RuntimeException("Invalid task name: " + taskName);
                    }
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Task schema does not exist (IF EXISTS): {}", taskName);
                        return null;
                    }
                    throw e;
                }

                Task task;
                try {
                    task = schema.getTask(tskName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Task does not exist (IF EXISTS): {}", taskName);
                        return null;
                    }
                    throw e;
                }
                task.setComment(comment);
                logger.trace("Set comment on task: {}", taskName);

            } else if (ctx.WAREHOUSE() != null) {
                // COMMENT ON WAREHOUSE
                String warehouseName = visitor.getText(ctx.identifier());
                Warehouse warehouse;
                try {
                    warehouse = catalog.getWarehouse(warehouseName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Warehouse does not exist (IF EXISTS): {}", warehouseName);
                        return null;
                    }
                    throw e;
                }
                if (warehouse == null) {
                    if (ifExists) {
                        logger.debug("Warehouse does not exist (IF EXISTS): {}", warehouseName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse", warehouseName));
                }
                warehouse.setComment(comment);
                logger.trace("Set comment on warehouse: {}", warehouseName);

            } else if (ctx.STAGE() != null) {
                // COMMENT ON STAGE
                String stageName = visitor.getText(ctx.qualifiedName());
                Stage stage;
                try {
                    stage = catalog.getStage(stageName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Stage does not exist (IF EXISTS): {}", stageName);
                        return null;
                    }
                    throw e;
                }
                if (stage == null) {
                    if (ifExists) {
                        logger.debug("Stage does not exist (IF EXISTS): {}", stageName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Stage", stageName));
                }
                stage.setComment(comment);
                logger.trace("Set comment on stage: {}", stageName);

            } else if (ctx.USER() != null) {
                // COMMENT ON USER
                String userName = visitor.getText(ctx.identifier());
                User user;
                try {
                    user = catalog.getUser(userName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("User does not exist (IF EXISTS): {}", userName);
                        return null;
                    }
                    throw e;
                }
                if (user == null) {
                    if (ifExists) {
                        logger.debug("User does not exist (IF EXISTS): {}", userName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("User", userName));
                }
                user.setComment(comment);
                logger.trace("Set comment on user: {}", userName);

            } else if (ctx.ROLE() != null) {
                // COMMENT ON ROLE
                String roleName = visitor.getText(ctx.identifier());
                Role role;
                try {
                    role = catalog.getRole(roleName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Role does not exist (IF EXISTS): {}", roleName);
                        return null;
                    }
                    throw e;
                }
                if (role == null) {
                    if (ifExists) {
                        logger.debug("Role does not exist (IF EXISTS): {}", roleName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Role", roleName));
                }
                role.setComment(comment);
                logger.trace("Set comment on role: {}", roleName);

            } else if (ctx.TAG() != null) {
                // COMMENT ON TAG
                String tagName = visitor.getText(ctx.qualifiedName());
                Tag tag;
                try {
                    tag = catalog.getTag(tagName);
                } catch (final RuntimeException e) {
                    if (ifExists) {
                        logger.debug("Tag does not exist (IF EXISTS): {}", tagName);
                        return null;
                    }
                    throw e;
                }
                if (tag == null) {
                    if (ifExists) {
                        logger.debug("Tag does not exist (IF EXISTS): {}", tagName);
                        return null;
                    }
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Tag", tagName));
                }
                tag.setComment(comment);
                logger.trace("Set comment on tag: {}", tagName);
            } else if (ctx.SEQUENCE() != null) {
                final String n = visitor.getText(ctx.qualifiedName());
                try {
                    commentTargetSchema(n).getSequence(commentSimpleName(n)).setComment(comment);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                }
            } else if (ctx.PIPE() != null) {
                final String n = visitor.getText(ctx.qualifiedName());
                try {
                    catalog.getPipe(n).setComment(comment);
                } catch (final RuntimeException e) {
                    if (!ifExists) throw e;
                }
            } else if (ctx.MASKING() != null && ctx.POLICY() != null) {
                final String n = visitor.getText(ctx.qualifiedName());
                final MaskingPolicy p = commentTargetSchema(n).getMaskingPolicy(commentSimpleName(n));
                if (p != null) {
                    p.setComment(comment);
                } else if (!ifExists) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Masking policy", n));
                }
            } else if (ctx.ROW() != null && ctx.ACCESS() != null && ctx.POLICY() != null) {
                final String n = visitor.getText(ctx.qualifiedName());
                final RowAccessPolicy p = commentTargetSchema(n).getRowAccessPolicy(commentSimpleName(n));
                if (p != null) {
                    p.setComment(comment);
                } else if (!ifExists) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Row access policy", n));
                }
            } else if (ctx.FILE() != null && ctx.FORMAT() != null) {
                final String n = visitor.getText(ctx.qualifiedName());
                final FileFormat ff = catalog.getFileFormat(n);
                if (ff != null) {
                    ff.setComment(comment);
                } else if (!ifExists) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("File format", n));
                }
            }

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    /** Resolve the schema owning a (possibly-qualified) object referenced in a COMMENT ON statement. */
    private Schema commentTargetSchema(final String objectName) {
        final String[] parts = QualifiedName.parse(objectName).parts();
        if (parts.length == 3) {
            return catalog.getDatabase(parts[0]).getSchema(parts[1]);
        } else if (parts.length == 2) {
            return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
        }
        return catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(catalog.getCurrentSchema());
    }

    /** Simple (unqualified) object name from a possibly-qualified name. */
    private String commentSimpleName(final String qualifiedName) {
        final int dot = qualifiedName.lastIndexOf('.');
        return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
    }

}
