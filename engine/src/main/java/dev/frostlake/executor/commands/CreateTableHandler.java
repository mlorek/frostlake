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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles CREATE TABLE (plain, CLONE, and CREATE TABLE AS SELECT), extracted from
 * {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. Column parsing is
 * delegated to {@link ColumnDefinitionParser}; shared schema/name helpers are reached via the
 * {@code ddl} back-reference.
 */
public class CreateTableHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateTableHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final ColumnDefinitionParser columnParser;

    CreateTableHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor,
                       final ColumnDefinitionParser columnParser) {
        this.ddl = ddl;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.columnParser = columnParser;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handleCreateTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String qualifiedName = queryExecutor.resolveObjectName(ctx.objectName());
        String[] parts = qualifiedName.split("\\.");
        boolean isTransient = ctx.TRANSIENT() != null;
        boolean isTemporary = ctx.TEMPORARY() != null || ctx.TEMP() != null;
        // CREATE HYBRID TABLE is accepted and stored as an ordinary table; the flag is kept only so
        // SHOW HYBRID TABLES and the reported kind reflect the declaration.
        boolean isHybrid = ctx.HYBRID() != null;
        boolean orReplace = ctx.or_replace() != null;

        try {
            Schema schema;
            String tableName;
            String databaseName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                databaseName = catalog.getCurrentDatabase();
                tableName = parts[0];
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw new RuntimeException("No database selected");
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

            ddl.checkCreatePrivilege(Privilege.CREATE_TABLE, ContainerType.SCHEMA, schema.getName());

            // Handle OR REPLACE - drop table if it exists
            if (orReplace) {
                try {
                    Table existingTable = schema.getTable(tableName);
                    if (existingTable != null) {
                        schema.dropTable(tableName);
                        String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                        queryExecutor.getStorageEngine().dropTable(fullyQualifiedName);
                        if (ddl.getStreamManager() != null) {
                            ddl.getStreamManager().onTableDropped(fullyQualifiedName);
                        }
                        logger.trace("Dropped existing table for OR REPLACE: {}", qualifiedName);
                    }
                } catch (final RuntimeException e) {
                    // Table doesn't exist, which is fine for OR REPLACE
                    logger.trace("No existing table to replace: {}", qualifiedName);
                }
            }

            Table table;

            if (ctx.CLONE() != null) {
                // The table name is now objectName, so CLONE's source is qualifiedName(0) (was (1)).
                String sourceTableName = getText(ctx.qualifiedName(0));
                Table sourceTable = catalog.resolveTable(sourceTableName);

                List<TableColumn> clonedColumns = new ArrayList<>();
                for (final TableColumn col : sourceTable.getColumns()) {
                    TableColumn clonedCol = new TableColumn(
                        col.getName(),
                        col.getDataType(),
                        col.isNullable(),
                        col.getDefaultValue(),
                        col.isPrimaryKey(),
                        col.isUnique(),
                        col.isAutoIncrement()
                    );
                    clonedCol.setComment(col.getComment());
                    clonedCol.setCollation(col.getCollation());
                    clonedColumns.add(clonedCol);
                }

                // If TEMPORARY/TRANSIENT is explicitly specified, use that; otherwise inherit from source
                boolean tableIsTemporary = isTemporary ? isTemporary : sourceTable.isTemporary();
                boolean tableIsTransient = isTransient ? isTransient : sourceTable.isTransient();
                table = new Table(tableName, clonedColumns, tableIsTemporary, tableIsTransient);

                // Extract comment from either position (after table name or at end)
                // If specified, override the cloned comment
                String comment = null;
                if (ctx.commentClause().size() > 0) {
                    comment = ddl.extractComment(ctx.commentClause(0));
                }
                if (comment == null && ctx.commentClause().size() > 1) {
                    comment = ddl.extractComment(ctx.commentClause(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                } else {
                    table.setComment(sourceTable.getComment());
                }

                table.setClusterKeys(sourceTable.getClusterKeys());
                table.setOwner(catalog.currentRoleForOwner());
                table.setHybrid(isHybrid);
                schema.addTable(table);

                String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);

                String sourceQualifiedName = ddl.resolveFullyQualifiedName(sourceTableName);
                queryExecutor.getStorageEngine().cloneTableData(sourceQualifiedName, fullyQualifiedName);

                logger.trace("Cloned table: {} from {}", qualifiedName, sourceTableName);
            } else if (ctx.AS() != null && ctx.selectStatement() != null) {
                // CREATE TABLE AS SELECT (CTAS). A stream read by the source is consumed once the table is
                // created + populated (consumeCtasStreams below), like a consuming DML.
                ResultSet resultSet = queryExecutor.executeCtasSourceSelect(ctx.selectStatement());

                // CTAS may rename the result columns via an explicit (col1, col2, …) list before AS —
                // CREATE TABLE t (name, total) AS SELECT …; the names override the SELECT's output names.
                final List<String> providedNames = new ArrayList<>();
                if (ctx.columnListOptional() != null) {
                    for (final FrostlakeParser.IdentifierContext id : ctx.columnListOptional().identifierList().identifier()) {
                        providedNames.add(getText(id));
                    }
                }

                // Create columns based on result set structure (names overridden by the explicit list).
                List<TableColumn> columns = new ArrayList<>();
                int ctasColIdx = 0;
                for (final ResultSetColumn rsCol : resultSet.getColumns()) {
                    final String colName = ctasColIdx < providedNames.size()
                        ? providedNames.get(ctasColIdx) : rsCol.getName();
                    columns.add(new TableColumn(colName, rsCol.getDataType(), true, null, false, false, false));
                    ctasColIdx++;
                }

                // Determine if table should be temporary/transient
                boolean tableIsTemporary = isTemporary || isTransient;
                table = new Table(tableName, columns, isTemporary, isTransient);

                // Extract comment from either position
                String comment = null;
                if (ctx.commentClause().size() > 0) {
                    comment = ddl.extractComment(ctx.commentClause(0));
                }
                if (comment == null && ctx.commentClause().size() > 1) {
                    comment = ddl.extractComment(ctx.commentClause(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                }

                // Extract cluster keys (CLUSTER BY may appear before or after the column list)
                List<String> clusterKeys = extractClusterKeys(ctx);
                if (!clusterKeys.isEmpty()) {
                    table.setClusterKeys(clusterKeys);
                }

                table.setOwner(catalog.currentRoleForOwner());
                table.setHybrid(isHybrid);
                schema.addTable(table);

                String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);

                // Insert data from SELECT into the new table
                for (final Row resultRow : resultSet.getRows()) {
                    queryExecutor.getStorageEngine().getTableStorage(fullyQualifiedName).insert(resultRow);
                }

                // Table created + populated: now consume any stream the source SELECT read.
                queryExecutor.consumeCtasStreams();
                logger.trace("Created table from SELECT: {}", qualifiedName);
            } else {
                List<TableColumn> columns = columnParser.parseColumnList(ctx.columnList());
                List<ForeignKeyConstraint> foreignKeys = columnParser.parseForeignKeys(ctx.columnList());
                table = new Table(tableName, columns, isTemporary, isTransient);

                // Add foreign key constraints (final metadata only, final not enforced)
                for (final ForeignKeyConstraint fk : foreignKeys) {
                    table.addForeignKey(fk);
                }

                // Extract comment from either position (after table name or at end)
                // Prioritize the one right after table name if both are specified
                String comment = null;
                if (ctx.commentClause().size() > 0) {
                    comment = ddl.extractComment(ctx.commentClause(0));
                }
                if (comment == null && ctx.commentClause().size() > 1) {
                    comment = ddl.extractComment(ctx.commentClause(1));
                }
                if (comment != null) {
                    table.setComment(comment);
                }

                // Extract cluster keys (CLUSTER BY may appear before or after the column list)
                List<String> clusterKeys = extractClusterKeys(ctx);
                if (!clusterKeys.isEmpty()) {
                    table.setClusterKeys(clusterKeys);
                }

                table.setOwner(catalog.currentRoleForOwner());
                table.setHybrid(isHybrid);
                schema.addTable(table);

                String fullyQualifiedName = databaseName.toUpperCase() + "." + schema.getName().toUpperCase() + "." + tableName.toUpperCase();
                queryExecutor.getStorageEngine().createTable(fullyQualifiedName, table);

                logger.trace("Created table: {}", qualifiedName);
            }
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, qualifiedName);
        }
        return null;
    }

    /**
     * Collects the {@code CLUSTER BY} key expressions for a CREATE TABLE. Snowflake accepts the clause
     * either immediately after the table name (before the column list) or after the column list; the
     * grammar allows {@code clusterByClause} in both positions, so this reads whichever one was supplied.
     * Returns an empty list when no clustering key was given.
     */
    private static List<String> extractClusterKeys(final FrostlakeParser.CreateStatementContext ctx) {
        List<String> clusterKeys = new ArrayList<>();
        List<FrostlakeParser.ClusterByClauseContext> clauses = ctx.clusterByClause();
        if (!clauses.isEmpty()) {
            for (final FrostlakeParser.ExpressionContext exprCtx : clauses.get(0).expressionList().expression()) {
                clusterKeys.add(exprCtx.getText());
            }
        }
        return clusterKeys;
    }

}
