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

import dev.frostlake.executor.ChangeTrackedQuery;
import dev.frostlake.executor.ClusteringKeySpelling;
import dev.frostlake.executor.ConditionalDdlOutcome;
import dev.frostlake.executor.DdlStatusNote;
import dev.frostlake.executor.DmlWriteTarget;
import dev.frostlake.executor.DynamicTableColumns;
import dev.frostlake.executor.DynamicTableRefreshReason;
import dev.frostlake.executor.InformationSchemaGuard;
import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.MaterializedViewRefresh;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.ProjectionSlot;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RecursiveViewChain;
import dev.frostlake.executor.RowSelectItemException;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.WarehouseReference;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.udf.TemporaryObjectStatements;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.CreationGrants;
import dev.frostlake.metastore.FutureGrants;
import dev.frostlake.metastore.GrantNames;
import dev.frostlake.metastore.MaterializedViewValidity;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.DynamicTable;
import dev.frostlake.metastore.model.DynamicTableColumn;
import dev.frostlake.metastore.model.Initialize;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RefreshMode;
import dev.frostlake.metastore.model.RefreshTrigger;
import dev.frostlake.metastore.model.RefreshTriggers;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.State;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.storage.Row;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    /**
     * CREATE VIEW ... [WITH] AGGREGATION POLICY p [ENTITY KEY (cols)] and [WITH] JOIN POLICY p: the view is read under
     * them as a table is, each policy resolved first (live-verified).
     */
    private void attachViewPolicies(final FrostlakeParser.CreateStatementContext ctx, final View view) {
        for (final FrostlakeParser.ViewPolicyClauseContext policy : ctx.viewPolicyClause()) {
            if (policy.aggregationPolicyClause() != null) {
                final FrostlakeParser.AggregationPolicyClauseContext clause = policy.aggregationPolicyClause();
                final String written = getText(clause.qualifiedName());
                if (catalog.findAggregationPolicyExact(written) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Aggregation policy",
                        catalog.qualifiedExactName(written)));
                }
                view.setAggregationPolicyName(catalog.qualifiedExactName(written));
                final List<String> entityKey = new ArrayList<>();
                if (clause.identifierList() != null) {
                    for (final FrostlakeParser.IdentifierContext id : clause.identifierList().identifier()) {
                        entityKey.add(getText(id));
                    }
                }
                view.setAggregationEntityKey(entityKey);
            } else {
                final String written = getText(policy.joinPolicyClause().qualifiedName());
                if (catalog.findJoinPolicy(written) == null) {
                    throw new RuntimeException(SqlCompilationError.doesNotExist("Join policy",
                        catalog.qualifiedExactName(written)));
                }
                view.setJoinPolicyName(catalog.qualifiedExactName(written));
            }
        }
    }

    /**
     * An aggregation policy's ENTITY KEY names columns of the view itself: one the view does not have is refused as
     * the view is created (live-verified: {@code ENTITY KEY (nosuch)} is {@code column 'NOSUCH' does not exist}).
     */
    private static void requireEntityKeyColumns(final View view) {
        if (!view.hasAggregationPolicy() || view.getAggregationEntityKey() == null || !view.hasResolvedColumns()) {
            return;
        }
        for (final String key : view.getAggregationEntityKey()) {
            boolean held = false;
            for (final TableColumn column : view.getResolvedColumns()) {
                held |= column.getName().equals(key);
            }
            if (!held) {
                throw new RuntimeException(SqlCompilationError.columnDoesNotExist(key));
            }
        }
    }

    /**
     * What a CREATE VIEW fails to compile for, nothing created: its body compiled where the view would live and its
     * columns named, as the view's creation judges them. A session whose transaction was aborted is refused for this
     * before the aborted transaction (live-verified).
     *
     * @param queryExecutor the executor compiling the view
     * @param ctx           the CREATE VIEW
     */
    public static void compileOnly(final QueryExecutor queryExecutor,
                                   final FrostlakeParser.CreateStatementContext ctx) {
        final Catalog catalog = queryExecutor.getCatalog();
        final String[] parts = catalog.withoutAccount(queryExecutor.resolveObjectNameParts(ctx.objectName()), 3);
        final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
        List<String> columnNames = null;
        if (ctx.viewColumnList() != null) {
            columnNames = new ArrayList<>();
            for (final FrostlakeParser.ViewColumnDefContext column : ctx.viewColumnList().viewColumnDef()) {
                columnNames.add(SqlIdentifiers.canonical(column.identifier()));
            }
        }
        final SourcePosition displacedBody = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
        try {
            queryExecutor.resolveViewShapeInScope(parts.length == 3 ? parts[0] : catalog.getCurrentDatabase(),
                schema.getName(), ParseTreeText.getOriginalText(ctx.selectStatement()), columnNames);
        } catch (final RowSelectItemException rowItem) {
            validateViewColumns(ctx);
            throw rowItem;
        } finally {
            ExpressionSource.end(displacedBody);
        }
        validateViewColumns(ctx);
    }

    /** The database a CREATE names its schema in: the written one, else the session's current one. */
    private String schemaDatabaseName(final String[] parts) {
        return parts.length == 3 ? parts[0] : catalog.getCurrentDatabase();
    }

    /**
     * COPY GRANTS copies from the object being REPLACED, so a plain create has nothing to copy from and
     * is refused — the same sentence a table's is refused with, and for a view and a materialized view
     * alike (live-verified).
     */
    private void rejectSourcelessCopyGrants(final FrostlakeParser.CreateStatementContext ctx) {
        if (!ctx.copyGrants().isEmpty() && ctx.or_replace() == null) {
            throw new RuntimeException(SqlCompilationError.of(
                "Invalid operation COPY GRANTS without specifying source object."));
        }
    }

    private static void validateViewColumns(final FrostlakeParser.CreateStatementContext ctx) {
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
    private static FrostlakeParser.SelectListContext firstSelectList(
            final FrostlakeParser.SelectStatementContext select) {
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
    /**
     * A materialized view's CREATE statement as SHOW MATERIALIZED VIEWS spells it: as written, with the view's
     * own COMMENT clause re-printed as {@code comment = '…'} — {@code COMMENT='no spaces'} reads
     * {@code comment = 'no spaces'} and {@code COMMENT = 'it''s'} reads {@code comment = 'it''s'} in place —
     * while a column's comment keeps its spelling (live-verified).
     */
    private String listedText(final FrostlakeParser.CreateStatementContext ctx) {
        final String written = ddl.getOriginalText(ctx);
        final int base = ctx.getStart().getStartIndex();
        final StringBuilder text = new StringBuilder();
        int from = 0;
        for (final FrostlakeParser.CommentClauseContext clause : ctx.commentClause()) {
            final String comment = ddl.extractComment(clause);
            final int start = clause.getStart().getStartIndex() - base;
            final int stop = clause.getStop().getStopIndex() - base + 1;
            if (comment == null || start < from || stop > written.length()) {
                return written;
            }
            text.append(written, from, start).append("comment = '").append(comment.replace("'", "''")).append('\'');
            from = stop;
        }
        return text.append(written.substring(from)).toString();
    }

    /**
     * The database, schema and table a materialized view reads — the one catalog table its FROM names, with
     * the parts it leaves out taken from the session — or null when it names none.
     */
    private String[] sourceRelation(final ParseTree node) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            final String[] parts = catalog.withoutAccount(ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.TableSourceContext) node).tableQualifiedName()), 3);
            return parts.length == 3 ? parts
                : parts.length == 2 ? new String[]{catalog.getCurrentDatabase(), parts[0], parts[1]}
                : new String[]{catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]};
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final String[] found = sourceRelation(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

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
        // A column list holding its clauses in a shape the account does not read is a syntax error, before anything.
        ViewColumnClauses.requireReadable(ctx.viewColumnList(), ddl.getOriginalText(ctx));
        final String qualifiedName = queryExecutor.resolveObjectName(ctx.objectName());
        final String[] parts = catalog.withoutAccount(queryExecutor.resolveObjectNameParts(ctx.objectName()), 3);
        // CREATE OR ALTER VIEW takes the new body where the view is already there, which is what OR
        // REPLACE does to a view — the difference the account draws between them is in what a replace
        // does to the object's grants, which this engine copies either way.
        final boolean orReplace = ctx.or_replace() != null || ctx.or_alter() != null;
        // A DEFINITION may carry no bind at all, and the sentence is the definition's own rather than
        // the unset-bind refusal an ordinary statement gives.
        BindsInDefinition.reject(ctx.selectStatement());
        rejectSourcelessCopyGrants(ctx);

        // The replaced view's privileges, set aside while its replacement is made: OR ALTER keeps them all, COPY
        // GRANTS all but OWNERSHIP, a plain OR REPLACE none; a replacement that fails leaves them where they were.
        String grantsSetAside = null;
        try {
            final Schema schema;
            final String viewName;

            schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            viewName = parts[parts.length - 1];
            CreatedObjectProperties.requireViewProperties(ctx, schema.getName());
            if (orReplace) {
                // One of INFORMATION_SCHEMA's views is not replaced (live-verified).
                InformationSchemaGuard.refuseWrite(catalog, parts);
            }

            final boolean temporaryView = TemporaryObjectStatements.isTemporary(ctx);
            // A name another relation kind holds is refused BEFORE anything is asked of the session and before
            // existence is answered, so even IF NOT EXISTS over a table's name refuses rather than skipping
            // (live-verified).
            schema.rejectNameHeldByOtherKind(viewName, RelationKind.VIEW, temporaryView);
            // Replacing a view needs OWNERSHIP of it, asked ahead of the privilege to create one.
            if (orReplace && !temporaryView) {
                queryExecutor.requireOwnership("VIEW", parts, null);
            }
            // A temporary view needs no privilege on its schema, and a plain CREATE of a name a view already
            // holds is refused for the name ahead of the privilege (live-verified, both).
            if (temporaryView || !orReplace && !ifNotExists && schema.hasViewExact(viewName)) {
                schema.requireCreatable(Privilege.CREATE_VIEW);
            } else {
                ddl.checkCreatePrivilege(Privilege.CREATE_VIEW, schema);
            }
            if (ctx.or_alter() != null && ctx.tagList() != null) {
                InlineTags.refuseOnOrAlter(ctx.tagList(), queryExecutor);
            }
            if (ctx.or_alter() != null) {
                OrAlterPolicies.refuseView(ctx, queryExecutor);
            }
            // A VIEW answers existence FIRST: over an existing view, IF NOT EXISTS succeeds without the
            // body ever compiling (live: a broken body over an existing view is "already exists").
            if (ifNotExists && !orReplace && schema.hasViewExact(viewName)) {
                ConditionalDdlOutcome.createSkipped();
                return null;
            }

            final String writtenQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            final List<String> columnNames = new ArrayList<>();
            final List<String> writtenColumns = new ArrayList<>();
            if (ctx.viewColumnList() != null) {
                for (final FrostlakeParser.ViewColumnDefContext col : ctx.viewColumnList().viewColumnDef()) {
                    columnNames.add(getText(col.identifier()));
                    writtenColumns.add(col.identifier().getText());
                }
                // A view's column list is a column list: a repeated name is refused with the same
                // sentence a table's is.
                ColumnDefinitionParser.rejectDuplicateNames(columnNames);
            }
            // A RECURSIVE view runs as a recursive CTE when its body recurses on it (see RecursiveViewChain); any other
            // body reads the view by its name as any query would, which over a view not yet created refuses it.
            final String selectQuery = ctx.RECURSIVE() != null
                    && RecursiveViewChain.of(ctx.selectStatement(), viewName) != null
                ? recursiveDefinition(ctx.objectName().getStop().getText(), writtenColumns, writtenQuery)
                : writtenQuery;
            final View view = ctx.viewColumnList() != null
                ? new View(viewName, columnNames, selectQuery) : new View(viewName, selectQuery);
            if (!selectQuery.equals(writtenQuery)) {
                view.setRecursive(true);
                view.setWrittenBody(writtenQuery);
            }
            // A view written RECURSIVE stays one in its DDL even when its body names no branch to recurse.
            if (ctx.RECURSIVE() != null) {
                view.setRecursive(true);
            }

            if (ctx.rowAccessPolicyClause() != null) {
                // CREATE VIEW ... [WITH] ROW ACCESS POLICY p ON (cols) — same attach as the ALTER form,
                // and live makes the same checks here (the policy must resolve above all).
                final String written = getText(ctx.rowAccessPolicyClause().qualifiedName());
                RowAccessPolicyAttachment.require(catalog, written);
                view.setRowAccessPolicyName(catalog.qualifiedObjectName(written));
                final List<String> policyCols = new ArrayList<>();
                for (final FrostlakeParser.IdentifierContext id : ctx.rowAccessPolicyClause().identifierList().identifier()) {
                    policyCols.add(getText(id));
                }
                view.setRowAccessPolicyColumns(policyCols);
            }
            attachViewPolicies(ctx, view);

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
            // CHANGE_TRACKING = TRUE turns the view's change tracking on, as a stream created on it does; a query
            // change tracking cannot follow is refused (live-verified, see ChangeTrackedQuery).
            for (final FrostlakeParser.ViewPropertyContext property : ctx.viewProperty()) {
                if ("CHANGE_TRACKING".equalsIgnoreCase(property.optionKey().getText())) {
                    final boolean tracked = "TRUE".equalsIgnoreCase(property.copyOptionValue().getText());
                    final String untracked = tracked ? ChangeTrackedQuery.refusal(catalog,
                        queryExecutor.getFunctionRegistry(), ctx.selectStatement()) : null;
                    if (untracked != null) {
                        throw new RuntimeException(untracked);
                    }
                    view.setChangeTracking(tracked);
                    if (tracked) {
                        // The tables the view reads track their changes with it, as they do for a stream on it.
                        for (final Table read : ChangeTrackedQuery.trackedTables(catalog, ctx.selectStatement())) {
                            read.setChangeTracking(true);
                        }
                    }
                }
            }
            if (comment != null) {
                view.setComment(comment);
                final String[] texts = ViewCommentText.texts(ctx, ddl.getOriginalText(ctx), comment);
                if (texts != null) {
                    view.setDefinitionText(texts[0]);
                    view.setListedText(texts[1]);
                }
            }

            // A view whose name differs only in case is another view, which OR REPLACE leaves alone (live-verified).
            if (orReplace && schema.hasViewExact(viewName)) {
                grantsSetAside = schema.qualifiedName(schema.getView(viewName).getName());
                GrantNames.setAside(catalog, "VIEW", grantsSetAside);
            }
            final CreationGrants grantsOfNew = FutureGrants.ofNew(catalog, "VIEW", schema, viewName);
            view.setOwner(grantsOfNew.owner());
            view.setLastDdlBy(catalog.currentUserForDdl());
            if (ctx.tagList() != null) {
                InlineTags.apply(view, ctx.tagList(), queryExecutor);
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
                    schemaDatabaseName(parts), schema.getName(), selectQuery, view.getColumnNames(),
                    selectQuery.equals(writtenQuery) ? null : viewName));
            } catch (final RowSelectItemException rowItem) {
                // A ROW the body selects is named before it is judged: an unnamed one is Missing column
                // specification (live-verified).
                validateViewColumns(ctx);
                throw rowItem;
            } finally {
                ExpressionSource.end(displacedBody);
            }
            // The role creating the view has to be able to read what the body reads.
            final Set<String> bodyCteNames = new HashSet<>();
            QueryExecutor.collectCteNames(ctx.selectStatement(), bodyCteNames);
            queryExecutor.requireSelectIn(ctx.selectStatement(), bodyCteNames);
            validateViewColumns(ctx);
            DuplicateColumnNames.rejectAmong(view.getResolvedColumns());
            requireEntityKeyColumns(view);
            if (ctx.viewColumnList() != null) {
                // A per-column COMMENT in the view's own column list belongs to the VIEW's column —
                // DESCRIBE reports it there, and never the base column's comment (live-verified).
                applyViewColumnComments(ctx.viewColumnList(), view.getResolvedColumns());
                // So do its policies and its tags, which the view's readers then meet (live-verified).
                ViewColumnClauses.attach(ctx.viewColumnList(), view.getResolvedColumns(), queryExecutor);
            }
            // OR REPLACE drops the old view only once the new one is fully validated. A FAILED
            // CREATE OR REPLACE VIEW leaves the existing view standing on live — every failure mode
            // measured: an unnamable column, an uncompilable body, a wrong-count column list, a
            // missing base table and an invalid identifier. Dropping first, as this did, destroyed a
            // working view whenever the replacement turned out to be invalid.
            if (orReplace) {
                try {
                    schema.dropViewExact(viewName);
                    logger.trace("Dropped existing view for OR REPLACE: {}", qualifiedName);
                    if (ctx.or_alter() != null) {
                        // A CREATE OR ALTER that found its view altered it, and answers as an ALTER does
                        // (live-verified).
                        ConditionalDdlOutcome.alteredInPlace();
                    }
                } catch (final RuntimeException noExistingView) {
                    // Schema.getView RAISES for an absent view rather than answering null, so there
                    // is nothing to ask before dropping — the absence is the normal case.
                    logger.trace("No existing view to replace: {}", qualifiedName);
                }
            }
            schema.addView(view);
            if (ctx.or_alter() == null || grantsSetAside == null) {
                // A CREATE OR ALTER that found its view altered it: future grants are for new objects only.
                grantsOfNew.apply();
            }
            if (grantsSetAside != null) {
                if (ctx.or_alter() != null || !ctx.copyGrants().isEmpty()) {
                    GrantNames.takeBack(catalog, "VIEW", grantsSetAside, ctx.or_alter() == null);
                } else {
                    GrantNames.discard(catalog, "VIEW", grantsSetAside);
                }
                grantsSetAside = null;
            }
            logger.trace("Created view: {}", qualifiedName);
        } catch (final RuntimeException e) {
            ddl.handleIfNotExists(ifNotExists, DDLCommandHandler.namedAsWritten(e, parts), "object");
            logger.debug("View already exists (IF NOT EXISTS): {}", qualifiedName);
        } finally {
            if (grantsSetAside != null) {
                GrantNames.takeBack(catalog, "VIEW", grantsSetAside, false);
            }
        }
        return null;
    }

    /**
     * What a RECURSIVE view runs: its body as a recursive common table expression named as the statement names the
     * view, read whole. The body's anchor branch seeds the rows and the branch that reads the view extends them, so
     * {@code CREATE RECURSIVE VIEW v (n) AS SELECT 1 UNION ALL SELECT n + 1 FROM v WHERE n < 3} reads 1, 2 and 3.
     * The statement as written is what SHOW VIEWS and GET_DDL report.
     *
     * @param writtenName    the view's own name as written, without its database and schema
     * @param writtenColumns the column list as written, or empty
     * @param body           the body as written
     */
    private static String recursiveDefinition(final String writtenName, final List<String> writtenColumns,
                                              final String body) {
        final StringBuilder text = new StringBuilder("WITH RECURSIVE ").append(writtenName);
        if (!writtenColumns.isEmpty()) {
            text.append(" (").append(String.join(", ", writtenColumns)).append(')');
        }
        return text.append(" AS (").append(body).append(") SELECT * FROM ").append(writtenName).toString();
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
     *
     * @return how many rows were produced
     */
    private long populate(final FrostlakeParser.CreateStatementContext ctx, final Schema schema,
                          final String mvName, final boolean fullyQualified) {
        ProjectionSlot.reset();
        try {
            return queryExecutor.executeSelectFromContext(ctx.selectStatement()).getRowCount();
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
            throw DmlWriteTarget.failedOnColumn(table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT), failed);
        }
    }

    /**
     * A materialized view's column list, written before its options or after them; a second list after a first is
     * a syntax error at its parenthesis. Null when the view declares none.
     */
    private FrostlakeParser.ViewColumnListContext materializedViewColumns(
            final FrostlakeParser.CreateStatementContext ctx) {
        final FrostlakeParser.TrailingViewColumnsContext trailing = ctx.trailingViewColumns();
        if (trailing == null) {
            return ctx.viewColumnList();
        }
        if (ctx.viewColumnList() != null) {
            final Token at = trailing.getStart();
            final String line = "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
                + " unexpected '('.";
            throw new SqlSyntaxException(SqlCompilationError.of(line), Collections.singletonList(line),
                ddl.getOriginalText(ctx));
        }
        return trailing.viewColumnList();
    }

    /** The name of a materialized view's column at {@code slot}: its declared name, or the item's alias. */
    private String columnNameAt(final FrostlakeParser.CreateStatementContext ctx, final int slot) {
        if (materializedViewColumns(ctx) != null) {
            final List<FrostlakeParser.ViewColumnDefContext> declared = materializedViewColumns(ctx).viewColumnDef();
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
    private void initialRefresh(final FrostlakeParser.CreateStatementContext ctx, final DynamicTable dt,
                                final Schema schema, final String query, final Initialize initialize) {
        final SourcePosition displaced = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
        try {
            queryExecutor.resolveRelationShape(query, null);
        } finally {
            ExpressionSource.end(displaced);
        }
        // A body that compiles must read a base table: one reading none, or only common table expressions, is
        // refused (live-verified).
        if (!ChangeTrackedQuery.readsBaseTable(ctx.selectStatement())) {
            throw new RuntimeException(SqlCompilationError.inline("Dynamic Tables must have at least one base table."));
        }
        // The table's own column list is judged against the query's columns once the query compiles, and the
        // columns are kept as they resolved, for the listings that tell them.
        dt.setResolvedColumns(queryExecutor.dynamicTableColumns(schema, dt));
        if (initialize == Initialize.ON_SCHEDULE) {
            return;
        }
        ProjectionSlot.reset();
        // The first refresh refreshes the dynamic tables it reads first, a creation's refreshes each of them
        // (live-verified).
        final RefreshTrigger trigger = RefreshTriggers.begin(RefreshTrigger.CREATION);
        final boolean outer = queryExecutor.beginDynamicTableRefresh();
        try {
            queryExecutor.refreshUpstreamOf(schema, dt);
            final Instant start = Instant.now();
            List<Row> rows;
            try {
                rows = queryExecutor.executeSelectFromContext(ctx.selectStatement()).getRows();
            } catch (final RuntimeException failed) {
                ProjectionSlot.takeFailedSlot();
                if (!DmlWriteTarget.isRowTimeFailure(failed)) {
                    throw failed;
                }
                throw refreshFailure(failed);
            }
            if (!dt.getDeclaredColumns().isEmpty()) {
                // A value the column's declared type cannot hold fails the first refresh, and with it the create.
                try {
                    rows = queryExecutor.dynamicTableRows(schema, dt);
                } catch (final RuntimeException failed) {
                    throw refreshFailure(failed);
                }
            }
            // What the first refresh holds is what a listing counts and what the next refresh compares.
            queryExecutor.noteDynamicTableRefresh(schema, dt, queryExecutor.recordDynamicTableRefresh(dt, rows,
                queryExecutor.identifiedDynamicTableRows(schema, dt)), start, true);
        } finally {
            queryExecutor.endDynamicTableRefresh(outer);
            RefreshTriggers.end(trigger);
        }
    }

    /** The refusal of a create whose first refresh failed on a row. */
    private static RuntimeException refreshFailure(final RuntimeException failed) {
        return new RuntimeException(SqlCompilationError.inline("Failed to refresh dynamic table with"
            + " refresh_trigger INITIAL at data_timestamp " + StatementClock.instant().toEpochMilli()
            + " because of the error: "
            + SqlCompilationError.inline("Target table failed to refresh: " + failed.getMessage())));
    }

    /** The columns a dynamic table's own column list declares, in order; none when it writes no list. */
    private List<DynamicTableColumn> declaredColumns(final FrostlakeParser.DynamicTableColumnsContext list) {
        final List<DynamicTableColumn> declared = new ArrayList<>();
        if (list == null) {
            return declared;
        }
        for (final FrostlakeParser.DynamicTableColumnContext column : list.dynamicTableColumn()) {
            declared.add(new DynamicTableColumn(SqlIdentifiers.canonical(column.identifier()),
                column.dataTypeName() == null ? null
                    : ddl.getColumnParser().parseDataType(column.dataTypeName(), column.typeParameters()),
                column.STRING_LITERAL() == null ? null : ddl.extractStringLiteral(column.STRING_LITERAL())));
        }
        DynamicTableColumns.requireDistinct(declared);
        return declared;
    }

    /**
     * Where a body begins, as the statement's own places are told: past the comments the text opens with, and from
     * the statement's first token in a request of several (live-verified).
     */
    private static SourcePosition originOf(final ParserRuleContext ctx) {
        if (ctx == null) {
            return null;
        }
        final int[] shown = LeadingCommentOffset.rebase(ctx.getStart().getLine(),
            ctx.getStart().getCharPositionInLine());
        return new SourcePosition(shown[0], shown[1]);
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
        ViewColumnClauses.requireReadable(ctx.viewColumnList(), ddl.getOriginalText(ctx));
        ViewColumnClauses.requireReadable(ctx.trailingViewColumns() == null ? null
            : ctx.trailingViewColumns().viewColumnList(), ddl.getOriginalText(ctx));
        final String qualifiedName = queryExecutor.resolveObjectName(ctx.objectName());
        final String[] parts = catalog.withoutAccount(queryExecutor.resolveObjectNameParts(ctx.objectName()), 3);
        final boolean orReplace = ctx.or_replace() != null;
        // A materialized view is a definition like any other: it carries no bind either.
        BindsInDefinition.reject(ctx.selectStatement());
        rejectSourcelessCopyGrants(ctx);
        // A refusal of the body itself, which IF NOT EXISTS never forgives while the view is being created.
        RuntimeException bodyRefusal = null;

        try {
            final Schema schema;
            final String mvName;

            schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            mvName = parts[parts.length - 1];

            CreatedObjectProperties.requireMaterializedViewProperties(ctx, schema.getName());
            // A name another kind of relation holds, and for a plain create a materialized view of the name,
            // are refused before the privilege is asked (live-verified).
            schema.rejectNameHeldByOtherKind(mvName, RelationKind.MATERIALIZED_VIEW, false);
            DDLCommandHandler.refuseTakenName(ctx.or_replace() == null && !ifNotExists,
                schema.hasMaterializedView(mvName), parts);
            // A materialized view has a CREATE privilege of its own: CREATE VIEW does not authorize it.
            ddl.checkCreatePrivilege(Privilege.CREATE_MATERIALIZED_VIEW, schema);

            // A materialized view must select FROM a real table. Live rejects a FROM-less definition —
            // Snowflake plans it as a VALUES table — with this exact sentence
            // (CREATE MATERIALIZED VIEW tmv AS SELECT 1 AS c).
            rejectFromLessMaterializedView(ctx.selectStatement());


            final String selectQuery = ddl.getOriginalText(ctx.selectStatement());

            // Extract optional column names (with optional per-column comments)
            final MaterializedView mv;
            final FrostlakeParser.ViewColumnListContext declaredColumns = materializedViewColumns(ctx);
            if (declaredColumns != null) {
                final List<String> columnNames = new ArrayList<>();
                for (final FrostlakeParser.ViewColumnDefContext col : declaredColumns.viewColumnDef()) {
                    columnNames.add(getText(col.identifier()));
                }
                mv = new MaterializedView(mvName, columnNames, selectQuery);
                mv.setOriginalDdl(ddl.getOriginalText(ctx));
            } else {
                mv = new MaterializedView(mvName, selectQuery);
                mv.setOriginalDdl(ddl.getOriginalText(ctx));
            }

            if (ctx.SECURE() != null) mv.setSecure(true);
            mv.setListedText(listedText(ctx));
            final String[] source = sourceRelation(ctx.selectStatement());
            if (source != null) {
                mv.setSource(source[0], source[1], source[2]);
                mv.bindBaseTable(MaterializedViewValidity.tableNamed(catalog, mv));
                mv.setMaterializedAsOf(MaterializedViewRefresh.lastWrite(catalog, source[0], source[1], source[2]));
            }

            if (ctx.commentClause().size() > 1) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty("COMMENT"));
            }
            final String comment = ddl.extractCommentFromList(ctx.commentClause());
            if (comment != null) {
                mv.setComment(comment);
            }

            // The future grants of the schema or its database cover the new view (live-verified).
            final CreationGrants grantsOfNew = FutureGrants.ofNew(catalog, "MATERIALIZED_VIEW", schema,
                mvName);
            mv.setOwner(grantsOfNew.owner());
            mv.setLastDdlBy(catalog.currentUserForDdl());
            // Live POPULATES a materialized view at CREATE, as a CTAS fills its table. The body compiles,
            // its columns must be named, and then its rows are produced, so a value that faults refuses the
            // CREATE inside the write envelope. An existing view that is not being replaced is never rebuilt.
            final boolean creates = orReplace || !schema.hasMaterializedView(mvName);
            final SourcePosition displacedMvBody = ExpressionSource.beginNested(originOf(ctx.selectStatement()));
            try {
                mv.setResolvedColumns(queryExecutor.resolveRelationShape(selectQuery, mv.getColumnNames()));
                if (declaredColumns == null) {
                    rejectUnnamedColumns(ctx.selectStatement());
                }
                DuplicateColumnNames.rejectAmong(mv.getResolvedColumns());
                if (creates) {
                    // What it materialized is what a listing counts.
                    mv.setMaterializedRows(populate(ctx, schema, mvName, parts.length == 3));
                }
            } catch (final RuntimeException bodyRefused) {
                if (creates) {
                    bodyRefusal = bodyRefused;
                }
                throw bodyRefused;
            } finally {
                ExpressionSource.end(displacedMvBody);
            }
            if (declaredColumns != null) {
                // A materialized view's declared column comments behave exactly as a view's
                // (live-verified): its own comment surfaces in DESCRIBE, and the base column's
                // never does. So do its columns' policies and tags.
                applyViewColumnComments(declaredColumns, mv.getResolvedColumns());
                ViewColumnClauses.attach(declaredColumns, mv.getResolvedColumns(), queryExecutor);
            }
            // The clustering key reads the view's own columns, under the names its column list gives them;
            // of several CLUSTER BY clauses the last one stands.
            if (!ctx.clusterByClause().isEmpty()) {
                final FrostlakeParser.ClusterByClauseContext clause =
                    ctx.clusterByClause(ctx.clusterByClause().size() - 1);
                mv.setClusterKeys(MaterializedViewKeys.read(clause.expressionList(), mv.getResolvedColumns(), ddl,
                    queryExecutor), ClusteringKeySpelling.written(clause.clusterKeyOrder(), clause.expressionList()));
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
            grantsOfNew.apply();
            logger.trace("Created materialized view: {}", qualifiedName);
        } catch (final RuntimeException e) {
            if (e == bodyRefusal) {
                throw e;
            }
            ddl.handleIfNotExists(ifNotExists, DDLCommandHandler.namedAsWritten(e, parts), "object");
            logger.debug("Materialized view already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }

    /**
     * Keep a dynamic table's CREATE as SHOW DYNAMIC TABLES re-prints it (live-verified): the words through
     * the name as written (TRANSIENT included), a column list, the CLUSTER BY keys and the COMMENT written with
     * it, and the AS with the query exactly as written, whitespace and case included. The options between are
     * re-printed from the table's settings.
     */
    private static void recordCreateText(final FrostlakeParser.CreateStatementContext ctx, final DynamicTable dt) {
        final CharStream input = ctx.getStart().getInputStream();
        final StringBuilder head = new StringBuilder(input.getText(Interval.of(ctx.getStart().getStartIndex(),
            ctx.qualifiedName(0).getStop().getStopIndex())));
        if (ctx.dynamicTableColumns() != null) {
            head.append(' ').append(input.getText(Interval.of(ctx.dynamicTableColumns().getStart().getStartIndex(),
                ctx.dynamicTableColumns().getStop().getStopIndex())));
        } else if (ctx.identifierList() != null) {
            head.append(" (").append(input.getText(Interval.of(ctx.identifierList().getStart().getStartIndex(),
                ctx.identifierList().getStop().getStopIndex()))).append(')');
        }
        if (!dt.getClusterKeys().isEmpty()) {
            head.append(" CLUSTER BY (").append(String.join(", ", dt.getClusterKeys())).append(')');
        }
        for (final FrostlakeParser.DynamicTableOptionContext opt : ctx.dynamicTableOptions().dynamicTableOption()) {
            if (opt.COMMENT() != null && opt.STRING_LITERAL() != null) {
                head.append(" COMMENT = ").append(opt.STRING_LITERAL().getText());
            }
        }
        dt.setCreateText(head.toString());
        final int query = ctx.children.indexOf(ctx.selectStatement());
        final Token as = ((TerminalNode) ctx.getChild(query - 1)).getSymbol();
        dt.setBodyText(input.getText(Interval.of(as.getStartIndex(), ctx.selectStatement().getStop().getStopIndex())));
    }

    public Object handleCreateDynamicTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        if (ctx.CLONE() != null) {
            return cloneDynamicTable(ctx, ifNotExists);
        }
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 3);
        final boolean orReplace = ctx.or_replace() != null;
        // A refusal of the body itself, which IF NOT EXISTS never forgives while the table is being created.
        RuntimeException bodyRefusal = null;

        try {
            final Schema schema;
            final String dtName;
            // The name is kept as the reference resolved it, quoted case and all, as a table's is.
            schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            dtName = parts[parts.length - 1];

            // A dynamic table has a CREATE privilege of its own: CREATE TABLE does not authorize it.
            ddl.checkCreatePrivilege(Privilege.CREATE_DYNAMIC_TABLE, schema);
            // A name another kind holds is refused before the warehouse, the body or the base table is weighed
            // (live-verified), whatever OR REPLACE and IF NOT EXISTS say.
            schema.rejectNameHeldByOtherKind(dtName, RelationKind.DYNAMIC_TABLE, false);

            // Parse options
            String targetLag = "1 minute";
            String warehouse = catalog.getCurrentWarehouse();
            RefreshMode refreshMode = RefreshMode.AUTO;
            Initialize initialize = Initialize.ON_CREATE;
            Integer retentionDays = null;
            String comment = null;
            final List<String> clusterKeys = new ArrayList<>();
            String clusterByText = null;

            if (ctx.dynamicTableOptions() != null) {
                for (final FrostlakeParser.DynamicTableOptionContext opt : ctx.dynamicTableOptions().dynamicTableOption()) {
                    if (opt.TARGET_LAG() != null) {
                        if (opt.DOWNSTREAM() != null) targetLag = "DOWNSTREAM";
                        else if (opt.STRING_LITERAL() != null) {
                            targetLag = TargetLag.canonicalize(ddl.extractStringLiteral(opt.STRING_LITERAL()));
                        }
                    } else if (opt.WAREHOUSE() != null && opt.identifier() != null) {
                        warehouse = getText(opt.identifier());
                        WarehouseReference.require(catalog, warehouse);
                    } else if (opt.REFRESH_MODE() != null) {
                        if (opt.FULL() != null) refreshMode = RefreshMode.FULL;
                        else if (opt.INCREMENTAL() != null) refreshMode = RefreshMode.INCREMENTAL;
                    } else if (opt.INITIALIZE() != null) {
                        if (opt.ON_SCHEDULE() != null) initialize = Initialize.ON_SCHEDULE;
                    } else if (opt.DATA_RETENTION_TIME_IN_DAYS() != null && opt.INTEGER_LITERAL() != null) {
                        retentionDays = Integer.valueOf(opt.INTEGER_LITERAL().getText());
                    } else if (opt.COMMENT() != null && opt.STRING_LITERAL() != null) {
                        // COMMENT is one of the pre-AS options; the trailing position is a syntax
                        // error on a real account.
                        comment = ddl.extractStringLiteral(opt.STRING_LITERAL());
                    } else if (opt.CLUSTER() != null) {
                        for (final FrostlakeParser.ExpressionContext key : opt.expression()) {
                            clusterKeys.add(ddl.getOriginalText(key));
                        }
                        clusterByText = ClusteringKeySpelling.written(opt.clusterKeyOrder(), opt.LPAREN(),
                            opt.RPAREN());
                    }
                }
            }

            final String query = ddl.getOriginalText(ctx.selectStatement());
            final DynamicTable dt = new DynamicTable(dtName, query, targetLag, warehouse);
            dt.setDeclaredColumns(declaredColumns(ctx.dynamicTableColumns()));
            dt.setRefreshMode(refreshMode);
            dt.setInitialize(initialize);
            if (retentionDays != null) {
                dt.setDataRetentionDays(retentionDays.intValue());
            }
            dt.setClusterKeys(clusterKeys, clusterByText);
            // A dynamic table in a transient schema is transient, as a table there is.
            dt.setTransient(ctx.TRANSIENT() != null || schema.isTransientObject());

            if (comment != null) dt.setComment(comment);
            recordCreateText(ctx, dt);

            // The future grants of the schema or its database cover the new table (live-verified).
            final CreationGrants grantsOfNew = FutureGrants.ofNew(catalog, "DYNAMIC_TABLE", schema, dtName);
            dt.setOwner(grantsOfNew.owner());
            dt.setLastDdlBy(catalog.currentUserForDdl());
            // Live compiles a dynamic table's body at CREATE and, unless it is to INITIALIZE ON_SCHEDULE,
            // refreshes it there too. An existing table that is not being replaced is never rebuilt, and a
            // replaced one is dropped only once its replacement has refreshed (live-verified: the old
            // table stays when the new body faults).
            final boolean creates = orReplace || !schema.hasDynamicTable(dtName);
            if (creates) {
                try {
                    initialRefresh(ctx, dt, schema, query, initialize);
                } catch (final RuntimeException bodyRefused) {
                    bodyRefusal = bodyRefused;
                    throw bodyRefused;
                }
                // The initial refresh dates the table's data; one initialized ON_SCHEDULE has none yet.
                if (initialize != Initialize.ON_SCHEDULE) {
                    dt.setLastRefreshedTime(StatementClock.instant());
                }
            }
            if (orReplace) {
                try { schema.dropDynamicTable(dtName); } catch (final RuntimeException ignored) {}
            }
            // An AUTO refresh mode settles on FULL where the query holds a construct an incremental refresh does not
            // follow, and the status sentence says why (live-verified).
            if (refreshMode == RefreshMode.AUTO && creates) {
                final String reason = DynamicTableRefreshReason.of(catalog, queryExecutor.getFunctionRegistry(),
                    catalog.getCurrentDatabase(),
                    catalog.getCurrentSchema(), ctx.selectStatement());
                dt.setFullRefreshReason(reason);
                if (reason != null) {
                    DdlStatusNote.add("FULL refresh mode was selected because: " + reason);
                }
            }
            schema.addDynamicTable(dt);
            grantsOfNew.apply();
            if (creates) {
                // The tables its query reads track their changes from now on: the account turns change tracking on
                // for a dynamic table's sources as it creates the table (live-verified: a CHANGES clause over its
                // base table answers).
                for (final Table read : ChangeTrackedQuery.trackedTables(catalog, ctx.selectStatement())) {
                    read.setChangeTracking(true);
                }
            }
            logger.trace("Created dynamic table: {}", qualifiedName);
        } catch (final RuntimeException e) {
            if (e == bodyRefusal) {
                throw e;
            }
            ddl.handleIfNotExists(ifNotExists, DDLCommandHandler.namedAsWritten(e, parts), "object");
            logger.debug("Dynamic table already exists (IF NOT EXISTS): {}", qualifiedName);
        }
        return null;
    }


    /** The schema a dynamic table's name parts place it in, the session supplying what the name leaves out. */
    private Schema dynamicTableSchema(final String[] parts) {
        return catalog.requireOwningSchema(QualifiedName.of(parts));
    }

    /**
     * CREATE DYNAMIC TABLE ... CLONE: a new dynamic table with the source's definition, settings and clustering
     * key. The clone starts suspended, and so does its automatic reclustering; a target lag or a warehouse the
     * statement gives replaces the source's. A point in time is accepted: the clone's rows come from its query.
     */
    private Object cloneDynamicTable(final FrostlakeParser.CreateStatementContext ctx, final boolean ifNotExists) {
        final String qualifiedName = getText(ctx.qualifiedName(0));
        final String[] parts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(0)), 3);
        final String[] sourceParts = catalog.withoutAccount(qualifiedNameParts(ctx.qualifiedName(1)), 3);
        final Schema sourceSchema = dynamicTableSchema(sourceParts);
        final String sourceName = sourceParts[sourceParts.length - 1];
        if (!sourceSchema.hasDynamicTable(sourceName)) {
            // A missing source is named as an object: bare when it was written bare, else by its full path.
            throw new RuntimeException(SqlCompilationError.doesNotExist("Object", sourceParts.length == 1
                ? sourceName : QualifiedName.join(sourceParts.length == 3 ? sourceParts[0]
                    : catalog.getCurrentDatabase(), sourceSchema.getName(), sourceName)));
        }
        final DynamicTable source = sourceSchema.getDynamicTable(sourceName);
        final Schema schema = dynamicTableSchema(parts);
        final String dtName = parts[parts.length - 1];
        ddl.checkCreatePrivilege(Privilege.CREATE_DYNAMIC_TABLE, schema);
        if (schema.hasDynamicTable(dtName) && ctx.or_replace() == null) {
            if (ifNotExists) {
                ConditionalDdlOutcome.createSkipped();
                logger.debug("Dynamic table already exists (IF NOT EXISTS): {}", qualifiedName);
                return null;
            }
            // The name that is taken is echoed as the statement wrote it.
            throw new RuntimeException(SqlCompilationError.of("Object '" + QualifiedName.join(parts)
                + "' already exists."));
        }
        final DynamicTable clone = source.copyAs(dtName);
        for (final FrostlakeParser.DynamicTableCloneOptionContext option : ctx.dynamicTableCloneOption()) {
            if (option.TARGET_LAG() != null) {
                clone.setTargetLag(option.DOWNSTREAM() != null ? "DOWNSTREAM"
                    : TargetLag.canonicalize(ddl.extractStringLiteral(option.STRING_LITERAL())));
            } else if (option.WAREHOUSE() != null) {
                final String warehouse = getText(option.identifier());
                WarehouseReference.require(catalog, warehouse);
                clone.setWarehouse(warehouse);
            }
        }
        clone.setTransient(source.isTransient() || ctx.TRANSIENT() != null || schema.isTransientObject());
        clone.setClone(true);
        clone.setState(State.SUSPENDED);
        clone.setSchedulingState("SUSPENDED");
        clone.setLastSuspendedOn(StatementClock.instant());
        clone.setReclusterSuspended(true);
        clone.setOwner(catalog.currentRoleForOwner());
        if (ctx.or_replace() != null && schema.hasDynamicTable(dtName)) {
            schema.dropDynamicTable(dtName);
        }
        schema.addDynamicTable(clone);
        logger.trace("Cloned dynamic table {} from {}", qualifiedName, getText(ctx.qualifiedName(1)));
        return null;
    }
}
