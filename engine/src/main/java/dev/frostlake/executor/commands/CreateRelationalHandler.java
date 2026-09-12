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
import dev.frostlake.executor.DmlWriteTarget;
import dev.frostlake.executor.ProjectionSlot;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.model.ContainerType;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.Initialize;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RefreshMode;
import dev.frostlake.metastore.model.RelationKind;
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
    /** The database a CREATE names its schema in: the written one, else the session's current one. */
    private String schemaDatabaseName(final String[] parts) {
        return parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
    }

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
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 3);
        final boolean orReplace = ctx.or_replace() != null;
        // A DEFINITION may carry no unnamed bind, and the sentence is the definition's own rather than
        // the unset-bind refusal an ordinary statement gives.
        BindsInDefinition.reject(ctx.selectStatement());

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

            ddl.checkCreatePrivilege(Privilege.CREATE_VIEW, ContainerType.SCHEMA, schema.getName());
            // A name another relation kind holds is refused BEFORE existence is answered, so even
            // IF NOT EXISTS over a table's name refuses rather than skipping (live-verified).
            schema.rejectNameHeldByOtherKind(viewName, RelationKind.VIEW,
                TemporaryObjectStatements.isTemporary(ctx));
            // A VIEW answers existence FIRST: over an existing view, IF NOT EXISTS succeeds without the
            // body ever compiling (live: a broken body over an existing view is "already exists").
            if (ifNotExists && !orReplace && schema.hasView(viewName)) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }

            final String selectQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            final View view;
            if (ctx.viewColumnList() != null) {
                final List<String> columnNames = new ArrayList<>();
                for (final FrostlakeParser.ViewColumnDefContext col : ctx.viewColumnList().viewColumnDef()) {
                    columnNames.add(getText(col.identifier()));
                }
                // A view's column list is a column list: a repeated name is refused with the same
                // sentence a table's is.
                ColumnDefinitionParser.rejectDuplicateNames(columnNames);
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
            // The columns come from the body's SHAPE, no row read: a value-time fault in the body —
            // a narrowing cast, 1/0, a date that does not parse — is the reader's, not the creator's,
            // and the view is created with its columns known (live-verified).
            final SourcePosition displacedBody = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
            try {
                // The body compiles where the view will live, whatever the session's context.
                view.setResolvedColumns(queryExecutor.resolveViewShapeInScope(
                    schemaDatabaseName(parts), schema.getName(), selectQuery, view.getColumnNames()));
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
    /**
     * A materialized view's rows, produced once at CREATE, so that a value which faults refuses the statement
     * the way a CTAS refuses (live-verified). The refusal is wrapped in the write envelope, naming the view
     * (qualified one level up, as a CTAS's table is) and the column whose projection stopped. A fault with no
     * projection to name, an aggregate's, is refused bare, as live refuses it.
     */
    private void populate(final FrostlakeParser.CreateStatementContext ctx, final Schema schema,
                          final String mvName, final boolean fullyQualified) {
        ProjectionSlot.reset();
        try {
            queryExecutor.executeSelectFromContext(ctx.selectStatement());
        } catch (final RuntimeException failed) {
            final int slot = ProjectionSlot.takeFailedSlot();
            if (!DmlWriteTarget.isRowTimeFailure(failed)) {
                throw failed;
            }
            final String column = slot < 0 ? null : columnNameAt(ctx, slot);
            if (column == null) {
                throw failed;
            }
            final String table = (fullyQualified ? queryExecutor.getEngineConfig().getAccountId() + "." : "")
                + schema.getDatabaseName() + "." + schema.getName() + "." + mvName;
            throw DmlWriteTarget.failedOnColumn(table.toUpperCase(), column.toUpperCase(), failed);
        }
    }

    /** The name of a materialized view's column at {@code slot}: its declared name, or the item's alias. */
    private String columnNameAt(final FrostlakeParser.CreateStatementContext ctx, final int slot) {
        if (ctx.viewColumnList() != null) {
            final List<FrostlakeParser.ViewColumnDefContext> declared = ctx.viewColumnList().viewColumnDef();
            return slot < declared.size() ? getText(declared.get(slot).identifier()) : null;
        }
        final FrostlakeParser.SelectStatementContext body = ctx.selectStatement();
        if (body.selectOperand().size() != 1 || body.selectOperand(0).selectClause() == null) {
            return null;
        }
        final List<FrostlakeParser.SelectItemContext> items =
            body.selectOperand(0).selectClause().selectList().selectItem();
        return slot < items.size() ? SelectItemAccessors.getItemAlias(items.get(slot)) : null;
    }

    /**
     * A materialized view names every column, as a CTAS's table does: an unnamed expression with no column
     * list to name it is refused (live-verified: {@code Missing column specification}).
     */
    private void rejectUnnamedColumns(final FrostlakeParser.SelectStatementContext select) {
        if (select.selectOperand().size() != 1) {
            return;
        }
        final FrostlakeParser.SelectOperandContext operand = select.selectOperand(0);
        if (operand.selectClause() == null || operand.selectClause().selectList() == null) {
            return;
        }
        boolean hasStar = false;
        boolean unnamedExpression = false;
        for (final FrostlakeParser.SelectItemContext item : operand.selectClause().selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item) || SelectItemAccessors.isQualifiedStarItem(item)) {
                hasStar = true;
            } else if (SelectItemAccessors.isUnnamedExpressionItem(item)) {
                unnamedExpression = true;
            }
        }
        if (unnamedExpression && !hasStar) {
            throw new RuntimeException(SqlCompilationError.of("Missing column specification"));
        }
    }

    /**
     * A dynamic table's body compiled at CREATE, every refusal positioned in the CREATE, and then, unless it
     * is initialized ON_SCHEDULE, run as its first refresh. A value that faults there refuses in the
     * refresh's own sentence, which carries the refresh's data timestamp (live-verified).
     */
    private void initialRefresh(final FrostlakeParser.CreateStatementContext ctx, final String query,
                                final Initialize initialize) {
        final SourcePosition displaced = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
        try {
            queryExecutor.resolveRelationShape(query, null);
        } finally {
            ExpressionSource.end(displaced);
        }
        if (initialize == Initialize.ON_SCHEDULE) {
            return;
        }
        ProjectionSlot.reset();
        try {
            queryExecutor.executeSelectFromContext(ctx.selectStatement());
        } catch (final RuntimeException failed) {
            ProjectionSlot.takeFailedSlot();
            if (!DmlWriteTarget.isRowTimeFailure(failed)) {
                throw failed;
            }
            throw new RuntimeException(SqlCompilationError.inline("Failed to refresh dynamic table with"
                + " refresh_trigger INITIAL at data_timestamp " + StatementClock.instant().toEpochMilli()
                + " because of the error: "
                + SqlCompilationError.inline("Target table failed to refresh: " + failed.getMessage())));
        }
    }

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
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 3);
        final boolean orReplace = ctx.or_replace() != null;
        // A refusal of the body itself, which IF NOT EXISTS never forgives while the view is being created.
        RuntimeException bodyRefusal = null;

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
            // Live POPULATES a materialized view at CREATE, as a CTAS fills its table. The body compiles,
            // its columns must be named, and then its rows are produced, so a value that faults refuses the
            // CREATE inside the write envelope. An existing view that is not being replaced is never rebuilt.
            final boolean creates = orReplace || !schema.hasMaterializedView(mvName);
            final SourcePosition displacedMvBody = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
            try {
                mv.setResolvedColumns(queryExecutor.resolveRelationShape(selectQuery, mv.getColumnNames()));
                if (ctx.viewColumnList() == null) {
                    rejectUnnamedColumns(ctx.selectStatement());
                }
                if (creates) {
                    populate(ctx, schema, mvName, parts.length == 3);
                }
            } catch (final RuntimeException bodyRefused) {
                if (creates) {
                    bodyRefusal = bodyRefused;
                }
                throw bodyRefused;
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
            if (e == bodyRefusal) {
                throw e;
            }
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Materialized view already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    public Object handleCreateDynamicTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 3);
        final boolean orReplace = ctx.or_replace() != null;
        // A refusal of the body itself, which IF NOT EXISTS never forgives while the table is being created.
        RuntimeException bodyRefusal = null;

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
            // Live compiles a dynamic table's body at CREATE and, unless it is to INITIALIZE ON_SCHEDULE,
            // refreshes it there too. An existing table that is not being replaced is never rebuilt, and a
            // replaced one is dropped only once its replacement has refreshed (live-verified: the old
            // table stays when the new body faults).
            final boolean creates = orReplace || !schema.hasDynamicTable(dtName);
            if (creates) {
                try {
                    initialRefresh(ctx, query, initialize);
                } catch (final RuntimeException bodyRefused) {
                    bodyRefusal = bodyRefused;
                    throw bodyRefused;
                }
            }
            if (orReplace) {
                try { schema.dropDynamicTable(dtName); } catch (final RuntimeException ignored) {}
            }
            schema.addDynamicTable(dt);
            logger.trace("Created dynamic table: {}", qualifiedName);
        } catch (final RuntimeException e) {
            if (e == bodyRefusal) {
                throw e;
            }
            ddl.handleIfNotExists(ifNotExists, e, "object");
            logger.debug("Dynamic table already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

}
