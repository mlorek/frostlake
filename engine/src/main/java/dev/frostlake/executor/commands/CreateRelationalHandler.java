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
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Initialize;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RefreshMode;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
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
     * count exactly — "Invalid column definition list". A (possibly parenthesized, possibly
     * qualified) column reference names itself, so it is never "missing". Star items make the
     * count unknowable at this layer, so they skip both checks.
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
            if (SelectItemAccessors.isUnnamedExpressionItem(item)) {
                unnamedExpression = true;
            }
        }
        if (hasStar) {
            return;
        }
        if (ctx.viewColumnList() != null) {
            if (ctx.viewColumnList().viewColumnDef().size() != itemCount) {
                throw new RuntimeException(SqlCompilationError.of("Invalid column definition list"));
            }
            return;
        }
        // A set operation is exempt (live-verified): the union output names its columns even
        // where the same items in a single query block would be refused.
        if (unnamedExpression && select.selectOperand().size() == 1) {
            throw new RuntimeException(SqlCompilationError.of("Missing column specification"));
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
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;

        try {
            final Schema schema;
            final String viewName;

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

            final String selectQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            final View view;
            if (ctx.viewColumnList() != null) {
                final List<String> columnNames = new ArrayList<>();
                for (final FrostlakeParser.ViewColumnDefContext col : ctx.viewColumnList().viewColumnDef()) {
                    columnNames.add(getText(col.identifier()));
                }
                view = new View(viewName, columnNames, selectQuery);
            } else {
                view = new View(viewName, selectQuery);
            }

            if (ctx.rowAccessPolicyClause() != null) {
                // CREATE VIEW ... [WITH] ROW ACCESS POLICY p ON (cols) — same attach as the ALTER form,
                // and live makes the same checks here (the policy must resolve above all).
                final String written = getText(ctx.rowAccessPolicyClause().qualifiedName());
                RowAccessPolicyAttachment.require(catalog, written);
                view.setRowAccessPolicyName(written.toUpperCase());
                final List<String> policyCols = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext id : ctx.rowAccessPolicyClause().identifierList().identifier()) {
                    policyCols.add(getText(id));
                }
                view.setRowAccessPolicyColumns(policyCols);
            }

            if (ctx.SECURE() != null) view.setSecure(true);
            view.setTemporary(TemporaryObjectStatements.isTemporary(ctx));
            // Snowflake surfaces the CREATE statement exactly as typed in SHOW VIEWS' text and
            // INFORMATION_SCHEMA.VIEWS.VIEW_DEFINITION (live-verified) — keep the original text.
            view.setOriginalDdl(ddl.getOriginalText(ctx));

            // A view property may be given ONCE (live-verified: a second COMMENT is "duplicate
            // property 'COMMENT';"). The pre-AS COMMENT arrives in this same generic list.
            final List<String> viewKeys = new ArrayList<>();
            for (final FrostlakeParser.ViewPropertyContext property : ctx.viewProperty()) {
                viewKeys.add(property.optionKey().getText());
            }
            if (!ctx.commentClause().isEmpty()) {
                for (int i = 0; i < ctx.commentClause().size(); i++) {
                    viewKeys.add("COMMENT");
                }
            }
            PropertyDuplicates.reject(viewKeys);

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
            // The BODY compiles before either column check, which is the order live reports in: over
            // `SELECT no_such_fn(k) FROM t` — unnamable AND uncompilable at once — live answers
            // "Unknown function NO_SUCH_FN.", and it answers the same for a wrong-COUNT column list
            // over an uncompilable body. Frostlake used to run both column checks first and report
            // them instead.
            // The body is RE-PARSED from its own text, so every position inside it is body-relative.
            // Scoping the body's offset in the CREATE statement makes a refusal from inside report
            // where live reports it — position 41 for this shape, not 10.
            final SourcePosition displacedBody = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
            try {
                view.setResolvedColumns(
                    queryExecutor.resolveRelationColumns(selectQuery, view.getColumnNames()));
            } finally {
                ExpressionSource.end(displacedBody);
            }
            validateViewColumns(ctx);
            if (ctx.viewColumnList() != null) {
                // A per-column COMMENT in the view's own column list belongs to the VIEW's column —
                // DESCRIBE reports it there, and never the base column's comment (live-verified).
                applyViewColumnComments(ctx.viewColumnList(), view.getResolvedColumns());
            }
            // OR REPLACE drops the old view only once the new one is fully validated. A FAILED
            // CREATE OR REPLACE VIEW leaves the existing view standing on live — every failure mode
            // measured: an unnamable column, an uncompilable body, a wrong-count column list, a
            // missing base table and an invalid identifier. Dropping first, as this did, destroyed a
            // working view whenever the replacement turned out to be invalid.
            if (orReplace) {
                try {
                    schema.dropView(viewName);
                    logger.trace("Dropped existing view for OR REPLACE: {}", qualifiedName);
                } catch (final RuntimeException noExistingView) {
                    // Schema.getView RAISES for an absent view rather than answering null, so there
                    // is nothing to ask before dropping — the absence is the normal case.
                    logger.trace("No existing view to replace: {}", qualifiedName);
                }
            }
            schema.addView(view);
            logger.trace("Created view: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("View already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    /**
     * The view's own per-column COMMENTs, applied positionally onto the columns it resolved. The
     * counts already agree — {@code validateViewColumns} refuses a list that does not match the
     * select — so a short resolved list means the definition would not plan and there is nothing to
     * annotate.
     */

    /** Where a parse-tree fragment begins, or null when there is none. */
    private static SourcePosition originOf(final ParserRuleContext ctx) {
        return ctx == null ? null
            : new SourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
    }

    private void applyViewColumnComments(final FrostlakeParser.ViewColumnListContext columnList,
                                         final List<TableColumn> resolved) {
        if (resolved == null) {
            return;
        }
        final List<FrostlakeParser.ViewColumnDefContext> declared = columnList.viewColumnDef();
        for (int i = 0; i < declared.size() && i < resolved.size(); i++) {
            final String comment = extractComment(declared.get(i).columnCommentClause());
            if (comment != null) {
                resolved.get(i).setComment(comment);
            }
        }
    }

    public Object handleCreateMaterializedView(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;

        try {
            final Schema schema;
            final String mvName;

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


            final String selectQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            final MaterializedView mv;
            if (ctx.viewColumnList() != null) {
                final List<String> columnNames = new ArrayList<>();
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

            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                mv.setComment(comment);
            }

            mv.setOwner(catalog.currentRoleForOwner());
            final SourcePosition displacedMvBody = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
            try {
                mv.setResolvedColumns(
                queryExecutor.resolveRelationColumns(selectQuery, mv.getColumnNames()));
            } finally {
                ExpressionSource.end(displacedMvBody);
            }
            if (ctx.viewColumnList() != null) {
                // A materialized view's declared column comments behave exactly as a view's
                // (live-verified): its own comment surfaces in DESCRIBE, and the base column's
                // never does.
                applyViewColumnComments(ctx.viewColumnList(), mv.getResolvedColumns());
            }
                        // Same rule as the plain view: a FAILED CREATE OR REPLACE leaves the existing materialized
            // view standing on live (measured over an uncompilable body, a missing base table and an
            // invalid identifier), so the drop waits until the replacement has resolved.
            if (orReplace) {
                try {
                    schema.dropMaterializedView(mvName);
                    logger.trace("Dropped existing materialized view for OR REPLACE: {}", qualifiedName);
                } catch (final RuntimeException noExistingMv) {
                    logger.trace("No existing materialized view to replace: {}", qualifiedName);
                }
            }
            schema.addMaterializedView(mv);
            logger.trace("Created materialized view: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Materialized view already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    public Object handleCreateDynamicTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = qualifiedNameParts(ctx.qualifiedName(0));
        final boolean orReplace = ctx.or_replace() != null;

        try {
            final Schema schema;
            final String dtName;
            if (parts.length == 1) {
                schema = ddl.resolveCurrentSchema();
                dtName = parts[0].toUpperCase();
            }
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
            String targetLag = "1 minute";
            String warehouse = catalog.getCurrentWarehouse();
            RefreshMode refreshMode = RefreshMode.AUTO;
            Initialize initialize = Initialize.ON_CREATE;
            int retentionDays = 1;
            String comment = null;

            if (ctx.dynamicTableOptions() != null) {
                for (final FrostlakeParser.DynamicTableOptionContext opt : ctx.dynamicTableOptions().dynamicTableOption()) {
                    if (opt.TARGET_LAG() != null) {
                        if (opt.DOWNSTREAM() != null) targetLag = "DOWNSTREAM";
                        else if (opt.STRING_LITERAL() != null) {
                            targetLag = TargetLag.canonicalize(ddl.extractStringLiteral(opt.STRING_LITERAL()));
                        }
                    } else if (opt.WAREHOUSE() != null && opt.identifier() != null) {
                        warehouse = getText(opt.identifier()).toUpperCase();
                        WarehouseReference.require(catalog, warehouse);
                    } else if (opt.REFRESH_MODE() != null) {
                        if (opt.FULL() != null) refreshMode = RefreshMode.FULL;
                        else if (opt.INCREMENTAL() != null) refreshMode = RefreshMode.INCREMENTAL;
                    } else if (opt.INITIALIZE() != null) {
                        if (opt.ON_SCHEDULE() != null) initialize = Initialize.ON_SCHEDULE;
                    } else if (opt.DATA_RETENTION_TIME_IN_DAYS() != null && opt.INTEGER_LITERAL() != null) {
                        retentionDays = Integer.parseInt(opt.INTEGER_LITERAL().getText());
                    } else if (opt.COMMENT() != null && opt.STRING_LITERAL() != null) {
                        // COMMENT is one of the pre-AS options; the trailing position is a syntax
                        // error on a real account.
                        comment = ddl.extractStringLiteral(opt.STRING_LITERAL());
                    }
                }
            }

            final String query = ddl.getOriginalText(ctx.selectStatement());
            final DynamicTable dt = new DynamicTable(dtName, query, targetLag, warehouse);
            dt.setRefreshMode(refreshMode);
            dt.setInitialize(initialize);
            dt.setDataRetentionDays(retentionDays);

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
