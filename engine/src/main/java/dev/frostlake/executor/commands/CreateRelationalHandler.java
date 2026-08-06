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

import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.executor.SelectItemAccessors;
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

    /**
     * Snowflake's CREATE VIEW column validation (both live-verified): a select item that is an
     * EXPRESSION without an alias needs a name — "Missing column specification" — unless the view
     * declares an explicit column list; and an explicit column list must match the projection
     * count exactly — "Invalid column definition list". Star items make the count unknowable at
     * this layer, so they skip both checks.
     */
    private void validateViewColumns(final FrostlakeParser.CreateStatementContext ctx) {
        final FrostlakeParser.SelectStatementContext select = ctx.selectStatement();
        final FrostlakeParser.SelectListContext selectList = firstSelectList(select);
        if (selectList == null) {
            return;
        }
        boolean hasStar = false;
        int itemCount = 0;
        boolean unnamedExpression = false;
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            itemCount++;
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                hasStar = true;
                continue;
            }
            if (SelectItemAccessors.getItemAlias(item) != null) {
                continue;
            }
            final FrostlakeParser.ExpressionContext valueExpr = SelectItemAccessors.getItemValueExpr(item);
            if (valueExpr != null && !(valueExpr instanceof FrostlakeParser.QualifiedNameExprContext)) {
                unnamedExpression = true;
            }
        }
        if (hasStar) {
            return;
        }
        if (ctx.viewColumnList() != null) {
            if (ctx.viewColumnList().viewColumnDef().size() != itemCount) {
                throw new RuntimeException("Invalid column definition list");
            }
            return;
        }
        if (unnamedExpression) {
            throw new RuntimeException("Missing column specification");
        }
    }

    /**
     * The TOP-LEVEL query block's select list — navigated via the parse tree
     * (selectStatement → first selectOperand → selectClause), never a generic walk, which would
     * descend into WITH-clause CTE bodies and validate the wrong projection. Null when the first
     * operand is itself parenthesized or shapeless (validation is skipped conservatively).
     */
    private FrostlakeParser.SelectListContext firstSelectList(final FrostlakeParser.SelectStatementContext select) {
        if (select.selectOperand().isEmpty()) {
            return null;
        }
        final FrostlakeParser.SelectOperandContext operand = select.selectOperand(0);
        if (operand.selectClause() == null) {
            return null;
        }
        return operand.selectClause().selectList();
    }

    /** A materialized view over a FROM-less select is refused with live's exact sentence — Snowflake
     *  plans the missing FROM as a VALUES table and names it in the error. */
    private void rejectFromLessMaterializedView(final FrostlakeParser.SelectStatementContext select) {
        if (select == null || select.selectOperand().isEmpty()) {
            return;
        }
        final FrostlakeParser.SelectOperandContext operand = select.selectOperand(0);
        if (operand.selectClause() != null && operand.selectClause().FROM() == null) {
            throw new RuntimeException(SqlCompilationError.at(0, -1,
                "Invalid materialized view definition. 'VALUES' should be a table."));
        }
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
            validateViewColumns(ctx);

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
            // Snowflake surfaces the CREATE statement exactly as typed in SHOW VIEWS' text and
            // INFORMATION_SCHEMA.VIEWS.VIEW_DEFINITION (live-verified) — keep the original text.
            view.setOriginalDdl(ddl.getOriginalText(ctx));

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment == null) {
                // A pre-AS COMMENT = '...' is consumed by the generic viewProperty* list before the
                // dedicated commentClause can match — read it from there so it is not dropped.
                for (final FrostlakeParser.ViewPropertyContext property : ctx.viewProperty()) {
                    if ("COMMENT".equalsIgnoreCase(property.optionKey().getText())
                            && property.copyOptionValue().STRING_LITERAL() != null) {
                        comment = ddl.extractStringLiteral(property.copyOptionValue().STRING_LITERAL());
                    }
                }
            }
            if (comment != null) {
                view.setComment(comment);
            }

            view.setOwner(catalog.currentRoleForOwner());
            if (ctx.tagList() != null) {
                InlineTags.apply(view, ctx.tagList());
            }
            view.setResolvedColumns(
                queryExecutor.resolveRelationColumns(selectQuery, view.getColumnNames()));
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

            // A materialized view must select FROM a real table. Live rejects a FROM-less definition —
            // Snowflake plans it as a VALUES table — with this exact sentence
            // (CREATE MATERIALIZED VIEW tmv AS SELECT 1 AS c).
            rejectFromLessMaterializedView(ctx.selectStatement());

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
                mv.setOriginalDdl(ddl.getOriginalText(ctx));
            } else {
                mv = new MaterializedView(mvName, selectQuery);
                mv.setOriginalDdl(ddl.getOriginalText(ctx));
            }

            if (ctx.SECURE() != null) mv.setSecure(true);

            String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                mv.setComment(comment);
            }

            mv.setOwner(catalog.currentRoleForOwner());
            mv.setResolvedColumns(
                queryExecutor.resolveRelationColumns(selectQuery, mv.getColumnNames()));
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
                        WarehouseReference.require(catalog, warehouse);
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
