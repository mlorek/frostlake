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
import dev.frostlake.types.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the SELECT-backed CREATE shapes — VIEW, MATERIALIZED VIEW, and DYNAMIC TABLE — extracted from
 * {@link DDLCommandHandler}, which keeps the CREATE dispatch and delegates here. Shared schema/name/text
 * helpers are reached via the {@code ddl} back-reference.
 */
public class CreateRelationalHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CreateRelationalHandler.class);

    private final DDLCommandHandler ddl;
    private final Catalog catalog;
    private final QueryExecutor queryExecutor;

    CreateRelationalHandler(final DDLCommandHandler ddl, final Catalog catalog, final QueryExecutor queryExecutor) {
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

    public Object handleCreateView(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String qualifiedName = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;

        try {
            Schema schema;
            String viewName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                viewName = parts[0];
            } else if (parts.length == 2) {
                if (catalog.getCurrentDatabase() == null) {
                    throw new RuntimeException("No database selected");
                }
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                viewName = parts[1];
            } else if (parts.length == 3) {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                viewName = parts[2];
            } else {
                throw new RuntimeException("Invalid view name: " + qualifiedName);
            }

            ddl.checkCreatePrivilege(Privilege.CREATE_VIEW, ContainerType.SCHEMA, schema.getName());

            // Handle OR REPLACE
            if (orReplace) {
                try {
                    View existingView = schema.getView(viewName);
                    if (existingView != null) {
                        schema.dropView(viewName);
                        logger.trace("Dropped existing view for OR REPLACE: {}", qualifiedName);
                    }
                } catch (final RuntimeException e) {
                    logger.trace("No existing view to replace: {}", qualifiedName);
                }
            }

            String selectQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            View view;
            if (ctx.viewColumnList() != null) {
                List<String> columnNames = new ArrayList<>();
                for (final FrostlakeParser.ViewColumnDefContext col : ctx.viewColumnList().viewColumnDef()) {
                    columnNames.add(getText(col.identifier()));
                }
                view = new View(viewName, columnNames, selectQuery);
            } else {
                view = new View(viewName, selectQuery);
            }

            if (ctx.rowAccessPolicyClause() != null) {
                // CREATE VIEW ... [WITH] ROW ACCESS POLICY p ON (cols) — same attach as the ALTER form.
                view.setRowAccessPolicyName(getText(ctx.rowAccessPolicyClause().qualifiedName()).toUpperCase());
                final List<String> policyCols = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext id : ctx.rowAccessPolicyClause().identifierList().identifier()) {
                    policyCols.add(getText(id));
                }
                view.setRowAccessPolicyColumns(policyCols);
            }

            if (ctx.SECURE() != null) view.setSecure(true);

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                view.setComment(comment);
            }

            view.setOwner(catalog.currentRoleForOwner());
            schema.addView(view);
            logger.trace("Created view: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("View already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    public Object handleCreateMaterializedView(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String qualifiedName = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;

        try {
            Schema schema;
            String mvName;

            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
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

            // No dedicated CREATE MATERIALIZED VIEW privilege exists in the grammar/enum; a
            // materialized view is a view variant, so CREATE VIEW on the schema authorizes it.
            ddl.checkCreatePrivilege(Privilege.CREATE_VIEW, ContainerType.SCHEMA, schema.getName());

            // Handle OR REPLACE
            if (orReplace) {
                try {
                    MaterializedView existingMv = schema.getMaterializedView(mvName);
                    if (existingMv != null) {
                        schema.dropMaterializedView(mvName);
                        logger.trace("Dropped existing materialized view for OR REPLACE: {}", qualifiedName);
                    }
                } catch (final RuntimeException e) {
                    logger.trace("No existing materialized view to replace: {}", qualifiedName);
                }
            }

            String selectQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            MaterializedView mv;
            if (ctx.viewColumnList() != null) {
                List<String> columnNames = new ArrayList<>();
                for (final FrostlakeParser.ViewColumnDefContext col : ctx.viewColumnList().viewColumnDef()) {
                    columnNames.add(getText(col.identifier()));
                }
                mv = new MaterializedView(mvName, columnNames, selectQuery);
            } else {
                mv = new MaterializedView(mvName, selectQuery);
            }

            if (ctx.SECURE() != null) mv.setSecure(true);

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                mv.setComment(comment);
            }

            mv.setOwner(catalog.currentRoleForOwner());
            schema.addMaterializedView(mv);
            logger.trace("Created materialized view: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Materialized view already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    public Object handleCreateDynamicTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        String qualifiedName = getText(ctx.qualifiedName(0));
        String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        boolean orReplace = ctx.or_replace() != null;

        try {
            Schema schema;
            String dtName;
            if (parts.length == 1) { schema = ddl.resolveCurrentSchema(); dtName = parts[0].toUpperCase(); }
            else if (parts.length == 2) {
                schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
                dtName = parts[1].toUpperCase();
            } else {
                schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                dtName = parts[2].toUpperCase();
            }

            // No dedicated CREATE DYNAMIC TABLE privilege exists in the grammar/enum; a dynamic
            // table is a table variant, so CREATE TABLE on the schema authorizes it.
            ddl.checkCreatePrivilege(Privilege.CREATE_TABLE, ContainerType.SCHEMA, schema.getName());

            if (orReplace) {
                try { schema.dropDynamicTable(dtName); } catch (final RuntimeException ignored) {}
            }

            // Parse options
            String targetLag = "1 minutes";
            String warehouse = catalog.getCurrentWarehouse();
            DynamicTable.RefreshMode refreshMode = DynamicTable.RefreshMode.AUTO;
            DynamicTable.Initialize initialize = DynamicTable.Initialize.ON_CREATE;
            int retentionDays = 1;

            if (ctx.dynamicTableOptions() != null) {
                for (final FrostlakeParser.DynamicTableOptionContext opt : ctx.dynamicTableOptions().dynamicTableOption()) {
                    if (opt.TARGET_LAG() != null) {
                        if (opt.DOWNSTREAM() != null) targetLag = "DOWNSTREAM";
                        else if (opt.STRING_LITERAL() != null) targetLag = ddl.extractStringLiteral(opt.STRING_LITERAL());
                    } else if (opt.WAREHOUSE() != null && opt.identifier() != null) {
                        warehouse = getText(opt.identifier()).toUpperCase();
                    } else if (opt.REFRESH_MODE() != null) {
                        if (opt.FULL() != null) refreshMode = DynamicTable.RefreshMode.FULL;
                        else if (opt.INCREMENTAL() != null) refreshMode = DynamicTable.RefreshMode.INCREMENTAL;
                    } else if (opt.INITIALIZE() != null) {
                        if (opt.ON_SCHEDULE() != null) initialize = DynamicTable.Initialize.ON_SCHEDULE;
                    } else if (opt.DATA_RETENTION_TIME_IN_DAYS() != null && opt.INTEGER_LITERAL() != null) {
                        retentionDays = Integer.parseInt(opt.INTEGER_LITERAL().getText());
                    }
                }
            }

            String query = ddl.getOriginalText(ctx.selectStatement());
            DynamicTable dt = new DynamicTable(dtName, query, targetLag, warehouse);
            dt.setRefreshMode(refreshMode);
            dt.setInitialize(initialize);
            dt.setDataRetentionDays(retentionDays);

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) dt.setComment(comment);

            dt.setOwner(catalog.currentRoleForOwner());
            schema.addDynamicTable(dt);
            logger.trace("Created dynamic table: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Dynamic table already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

}
