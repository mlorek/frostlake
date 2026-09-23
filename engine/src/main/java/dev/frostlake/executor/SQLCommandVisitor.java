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

package dev.frostlake.executor;

import dev.frostlake.executor.commands.AccessControlHandler;
import dev.frostlake.executor.commands.AlertCommandHandler;
import dev.frostlake.executor.commands.AlterCommandHandler;
import dev.frostlake.executor.commands.AppObjectCommandHandler;
import dev.frostlake.executor.commands.BeginEndBlockHandler;
import dev.frostlake.executor.commands.ColumnDefinitionParser;
import dev.frostlake.executor.commands.CommentCommandHandler;
import dev.frostlake.executor.commands.ContainerServicesHandler;
import dev.frostlake.executor.commands.DDLCommandHandler;
import dev.frostlake.executor.commands.DMLCommandHandler;
import dev.frostlake.executor.commands.DynamicTableProperties;
import dev.frostlake.executor.commands.ExternalVolumeCommandHandler;
import dev.frostlake.executor.commands.GrantRevokeHandler;
import dev.frostlake.executor.commands.IntegrationCommandHandler;
import dev.frostlake.executor.commands.QueryCommandHandler;
import dev.frostlake.executor.commands.SecurityObjectCommandHandler;
import dev.frostlake.executor.commands.ShowCommandHandler;
import dev.frostlake.executor.commands.TagValues;
import dev.frostlake.executor.expressions.IntervalLiteralSyntax;
import dev.frostlake.executor.expressions.NamedCallRewrite;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.CloseStatement;
import dev.frostlake.executor.procedural.Cursor;
import dev.frostlake.executor.procedural.DeclareCursorStatement;
import dev.frostlake.executor.procedural.DeclareExceptionStatement;
import dev.frostlake.executor.procedural.DeclareResultSetStatement;
import dev.frostlake.executor.procedural.DeclareStatement;
import dev.frostlake.executor.procedural.ExecuteImmediateExpression;
import dev.frostlake.executor.procedural.FetchStatement;
import dev.frostlake.executor.procedural.LiteralExpression;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.executor.procedural.RaiseStatement;
import dev.frostlake.executor.procedural.SetStatement;
import dev.frostlake.executor.procedural.Statement;
import dev.frostlake.executor.procedural.StatementType;
import dev.frostlake.executor.procedural.StatementValueExpression;
import dev.frostlake.executor.procedural.VariableExpression;
import dev.frostlake.executor.udf.JavaProcedureExecutor;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.functions.SystemFunctionArity;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.AnsiFromFormSyntax;
import dev.frostlake.parser.EmptySchemaPartSyntax;
import dev.frostlake.parser.FinishedPredicateSyntax;
import dev.frostlake.parser.FrostlakeBaseVisitor;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.IntoListLookahead;
import dev.frostlake.parser.LimitValueSyntax;
import dev.frostlake.parser.PositionNeedleSyntax;
import dev.frostlake.parser.PostParseRefusals;
import dev.frostlake.parser.QuantifiedComparisonOperand;
import dev.frostlake.parser.ScriptingStatementPlacement;
import dev.frostlake.parser.SignatureTailLine;
import dev.frostlake.parser.StageArgumentSyntax;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.parser.ValueFunctionSyntax;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Visitor that executes SQL commands by visiting the ANTLR parse tree.
 * Delegates to specialized command handlers for different SQL command types.
 */
public class SQLCommandVisitor extends FrostlakeBaseVisitor<Object> {

    private static final Logger logger = LoggerFactory.getLogger(SQLCommandVisitor.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private StreamManager streamManager;
    private TaskScheduler taskScheduler;
    private ProceduralExecutor proceduralExecutor;

    // Command handlers
    private final DDLCommandHandler ddlHandler;
    private final DMLCommandHandler dmlHandler;
    private final QueryCommandHandler queryHandler;
    private final ShowCommandHandler showHandler;
    private final CommentCommandHandler commentHandler;
    private final AlterCommandHandler alterHandler;
    private final AppObjectCommandHandler appObjectHandler;
    private final GrantRevokeHandler grantRevokeHandler;
    private final AccessControlHandler accessControlHandler;
    private final BeginEndBlockHandler beginEndBlockHandler;

    // Canonical column/type/literal parser — shared with DDL so type parsing (NUMBER(p,s),
    // VARCHAR(len), TINYINT, UUID, VECTOR, …) stays in one place rather than a divergent copy.
    private final ColumnDefinitionParser columnParser;
    private final ProceduralBlockBuilder proceduralBlockBuilder;
    private final VisitorExpressionBuilder expressionBuilder;

    /**
     * The statement the last EXECUTE IMMEDIATE answers for, reached through any EXECUTE IMMEDIATE its text
     * ran in turn; null when the dynamic text held several statements. See {@link #describedStatement}.
     */
    private FrostlakeParser.StatementContext dynamicStatement;

    public SQLCommandVisitor(final Catalog catalog, final QueryExecutor queryExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.proceduralExecutor = new ProceduralExecutor();
        this.proceduralExecutor.setQueryExecutor(queryExecutor);

        // Initialize command handlers
        this.ddlHandler = new DDLCommandHandler(catalog, queryExecutor);
        this.dmlHandler = new DMLCommandHandler(catalog, queryExecutor);
        this.queryHandler = new QueryCommandHandler(catalog, queryExecutor);
        this.showHandler = new ShowCommandHandler(catalog, queryExecutor);
        this.commentHandler = new CommentCommandHandler(catalog, queryExecutor, this);
        this.alterHandler = new AlterCommandHandler(catalog, queryExecutor, this, this.ddlHandler);
        this.appObjectHandler = new AppObjectCommandHandler(catalog, queryExecutor, this);
        this.grantRevokeHandler = new GrantRevokeHandler(catalog, queryExecutor, this);
        this.accessControlHandler = new AccessControlHandler(catalog, queryExecutor, this);
        this.beginEndBlockHandler = new BeginEndBlockHandler(catalog, queryExecutor, this, this.proceduralExecutor);
        this.columnParser = new ColumnDefinitionParser(catalog, queryExecutor);
        this.proceduralBlockBuilder = new ProceduralBlockBuilder(this);
        this.expressionBuilder = new VisitorExpressionBuilder(this, queryExecutor);
    }

    public void setStreamManager(final StreamManager streamManager) {
        this.streamManager = streamManager;
        this.ddlHandler.setStreamManager(streamManager);
    }

    public void setTaskScheduler(final TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        this.ddlHandler.setTaskScheduler(taskScheduler);
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    /** The shared column/constraint parser, for the handlers that build constraints outside CREATE. */
    public ColumnDefinitionParser getColumnParser() {
        return columnParser;
    }

    @Override
    public Object visitStatement(final FrostlakeParser.StatementContext ctx) {
        // Every statement is named for the refusal a session with no current database gets.
        final String enclosingKind = NoCurrentDatabaseRefusal.enter(StatementKindText.of(ctx));
        try {
            if (catalog != null && catalog.getCurrentDatabase() == null) {
                rejectWithoutCurrentDatabase(ctx);
            }
            TagValues.requireAll(ctx, queryExecutor);
            rejectUnknownTagReferences(ctx);
            rejectUnsetSessionVariables(ctx);
            return visitChildren(ctx);
        } finally {
            NoCurrentDatabaseRefusal.exit(enclosingKind);
        }
    }

    /**
     * A {@code $name} reference to a session variable no SET defined is refused while the statement
     * compiles, before anything runs and whatever clause it sits in — the select list, a WHERE that can
     * never be true, an UPDATE's SET, an INSERT's source, a CTAS, a view body — at the variable's own
     * position, naming it upper-cased: "Session variable '$NOSUCH' does not exist" (live-verified). A
     * variable SET to NULL exists and reads NULL; a session PARAMETER of the same name is not a variable.
     * Checked once per statement, so a SET earlier in the same script counts. A DESCRIBE's properties are judged
     * one by one in the order written, each name before its value (DescribeProperties), so theirs wait.
     */
    private void rejectUnsetSessionVariables(final ParseTree node) {
        if (node instanceof FrostlakeParser.DescribePropertyContext) {
            return;
        }
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            if (token.getType() == FrostlakeParser.SESSION_VAR_REF) {
                final String name = token.getText().substring(1).toUpperCase();
                if (!isDefinedSessionVariable(name)) {
                    throw new RuntimeException(SqlCompilationError.at(token.getLine(), token.getCharPositionInLine(),
                        "Session variable '$" + name + "' does not exist"));
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectUnsetSessionVariables(node.getChild(i));
        }
    }

    /** Whether SET defined {@code name} (upper-cased) as a session variable. */
    boolean isDefinedSessionVariable(final String name) {
        final SecurityManager sm = queryExecutor.getSecurityManager();
        if (sm != null && sm.getSessionContext() != null) {
            return sm.getSessionContext().isSessionVariable(name);
        }
        return queryExecutor.getSessionVariables().containsKey(name);
    }

    /**
     * A statement that creates, changes or drops an object of the current schema needs a current database.
     * Without one, live refuses it before anything is looked up, whatever the object and IF [NOT] EXISTS
     * notwithstanding. RENAME COLUMN and RENAME CONSTRAINT are the exception: they look a bare table name up,
     * and miss it.
     */
    private void rejectWithoutCurrentDatabase(final FrostlakeParser.StatementContext ctx) {
        final FrostlakeParser.QualifiedNameContext target = StatementKindText.underQualifiedTarget(ctx);
        if (target == null) {
            return;
        }
        if (target.namePart().isEmpty() && StatementKindText.renamesColumnOrConstraint(ctx)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table", qualifiedNameParts(target)[0]));
        }
        final FrostlakeParser.DdlStatementContext ddl = ctx.ddlStatement();
        if (ddl != null && ddl.alterStatement() != null && ddl.alterStatement().dynamicTableAction() != null) {
            // A property the account refuses outranks the missing database (live-verified).
            DynamicTableProperties.rejectUntakeable(ddl.alterStatement().dynamicTableAction());
        }
        throw NoCurrentDatabaseRefusal.forStatement();
    }

    /**
     * A {@code TAG (k='v')} clause names a tag that must ALREADY EXIST. Live-verified on a real account
     *: with no such tag, {@code CREATE TABLE t(id INTEGER) TAG (x='v')},
     * {@code CREATE TABLE t(id INTEGER TAG (x='v'), …)} and
     * {@code ALTER TABLE t ADD c VARCHAR TAG (x='v')} each fail with
     * "Tag 'X' does not exist or not authorized."; after {@code CREATE TAG x} every one of them
     * succeeds — as does the materialized-view form. Frostlake used to accept the clause inertly, so
     * scripts referencing a tag they never created passed here and failed on a real account.
     *
     * <p>Checked once per statement over the whole parse tree rather than in each DDL handler, because
     * {@code tagList} hangs off five different rules (table tail options, column constraints, views and
     * materialized views). A value outside the tag's ALLOWED_VALUES is refused here too, before anything
     * is created (live-verified).
     */
    private void rejectUnknownTagReferences(final ParseTree node) {
        if (node instanceof FrostlakeParser.TagListContext) {
            final FrostlakeParser.TagListContext tags = (FrostlakeParser.TagListContext) node;
            for (final FrostlakeParser.TagAssignmentContext assignment : tags.tagAssignment()) {
                final String tagName = getText(assignment.qualifiedName());
                if (catalog != null && !catalog.hasTag(tagName)) {
                    throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Tag", tagName.toUpperCase()));
                }
                if (catalog != null) {
                    // ALLOWED_VALUES is judged before the object is created, as live does.
                    TagValues.requireAllowed(catalog.getTag(tagName),
                        TagValues.text(assignment.qualifiedName(), assignment.tagValue(), queryExecutor));
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectUnknownTagReferences(node.getChild(i));
        }
    }

    // ==================== DDL STATEMENTS ====================

    @Override
    public Object visitCreateStatement(final FrostlakeParser.CreateStatementContext ctx) {
        try {
            return ddlHandler.handleCreateStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitIntegrationStatement(final FrostlakeParser.IntegrationStatementContext ctx) {
        try {
            return new IntegrationCommandHandler(catalog, queryExecutor).handle(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitExternalVolumeStatement(final FrostlakeParser.ExternalVolumeStatementContext ctx) {
        try {
            return new ExternalVolumeCommandHandler(catalog).handle(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        try {
            return ddlHandler.handleDropStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitDropClassStatement(final FrostlakeParser.DropClassStatementContext ctx) {
        try {
            return ddlHandler.handleDropClassStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitAlterStatement(final FrostlakeParser.AlterStatementContext ctx) {
        return alterHandler.handle(ctx);
    }

    @Override
    public Object visitUseStatement(final FrostlakeParser.UseStatementContext ctx) {
        rejectInsideProcedure("USE");
        try {
            return ddlHandler.handleUseStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitNotebookStatement(final FrostlakeParser.NotebookStatementContext ctx) {
        try {
            return appObjectHandler.handleNotebook(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitStreamlitStatement(final FrostlakeParser.StreamlitStatementContext ctx) {
        try {
            return appObjectHandler.handleStreamlit(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitUndropStatement(final FrostlakeParser.UndropStatementContext ctx) {
        return ddlHandler.handleUndropStatement(ctx);
    }

    @Override
    public Object visitExplainStatement(final FrostlakeParser.ExplainStatementContext ctx) {
        return ExplainPlan.answer(queryExecutor, ctx);
    }

    @Override
    public Object visitContainerServicesStatement(final FrostlakeParser.ContainerServicesStatementContext ctx) {
        try {
            return new ContainerServicesHandler(catalog, queryExecutor.getEngineConfig()).handle(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) {
                throw (SecurityException) e;
            }
            if (e instanceof ProceduralException) {
                throw (ProceduralException) e;
            }
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitCommentStatement(final FrostlakeParser.CommentStatementContext ctx) {
        return commentHandler.handle(ctx);
    }

    @Override
    public Object visitTruncateStatement(final FrostlakeParser.TruncateStatementContext ctx) {
        try {
            final boolean ifExists = ctx.if_exists() != null;
            final String tableName = queryExecutor.resolveObjectName(ctx.objectName());

            // Execute truncate via query executor
            queryExecutor.executeTruncate(tableName, ifExists);

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitTransactionStatement(final FrostlakeParser.TransactionStatementContext ctx) {
        if (ctx.BEGIN() != null || ctx.START() != null) {
            // Explicit BEGIN / START TRANSACTION starts (or promotes the current auto-started) transaction to
            // an explicit one, which suspends statement-end autocommit until COMMIT/ROLLBACK (Snowflake).
            queryExecutor.getTransactionManager().beginExplicitTransaction(transactionName(ctx));
        } else if (ctx.COMMIT() != null) {
            if (queryExecutor.getTransactionManager().hasActiveTransaction()) {
                queryExecutor.getTransactionManager().commit();
            }
            // Silently ignore COMMIT when no transaction is active (defensive scripting pattern)
        } else if (ctx.ROLLBACK() != null) {
            if (queryExecutor.getTransactionManager().hasActiveTransaction()) {
                queryExecutor.getTransactionManager().rollback();
            }
            // Silently ignore ROLLBACK when no transaction is active
        }
        return null;
    }

    /**
     * The name an opening transaction statement carries, or null when it carries none. Live keeps only
     * the FIRST part of a dotted name ({@code BEGIN NAME a.b} reads back as {@code A}).
     */
    private String transactionName(final FrostlakeParser.TransactionStatementContext ctx) {
        if (ctx.transactionName() == null) {
            return null;
        }
        return qualifiedNameParts(ctx.transactionName().qualifiedName())[0];
    }

    public Object visitSecurityStatement(final FrostlakeParser.SecurityStatementContext ctx) {
        if (ctx.grantDatabaseRoleStatement() != null) {
            return accessControlHandler.grantDatabaseRole(ctx.grantDatabaseRoleStatement());
        } else if (ctx.revokeDatabaseRoleStatement() != null) {
            return accessControlHandler.revokeDatabaseRole(ctx.revokeDatabaseRoleStatement());
        } else if (ctx.grantStatement() != null) {
            return visitGrantStatement(ctx.grantStatement());
        } else if (ctx.revokeStatement() != null) {
            return visitRevokeStatement(ctx.revokeStatement());
        }
        return null;
    }

    @Override
    public Object visitTaskStatement(final FrostlakeParser.TaskStatementContext ctx) {
        if (ctx.ALERT() != null) {
            return new AlertCommandHandler(queryExecutor).execute(ctx.qualifiedName());
        }
        try {
            // EXECUTE TASK task_name
            final String taskName = getText(ctx.qualifiedName());

            if (taskScheduler == null) {
                throw new RuntimeException("Task scheduler not initialized");
            }

            // Execute the task manually (handles qualified names internally); RETRY LAST and RETRY GRAPH RUN
            // GROUP re-run the failed part of a graph run instead, and USING CONFIG merges a configuration
            // over the task's own for this run alone.
            final FrostlakeParser.TaskExecuteOptionContext option = ctx.taskExecuteOption();
            final FrostlakeParser.TaskRetryTargetContext retry = option != null ? option.taskRetryTarget() : null;
            String usingConfig = null;
            if (option != null && option.taskConfigValue() != null) {
                final FrostlakeParser.TaskConfigValueContext config = option.taskConfigValue();
                usingConfig = config.STRING_LITERAL() != null ? extractStringLiteral(config.STRING_LITERAL())
                    : config.DOLLAR_QUOTED_STRING().getText().substring(2,
                        config.DOLLAR_QUOTED_STRING().getText().length() - 2);
            }
            taskScheduler.executeTaskManually(taskName, retry != null && retry.LAST() != null,
                retry != null && retry.STRING_LITERAL() != null ? extractStringLiteral(retry.STRING_LITERAL()) : null,
                usingConfig);
            logger.trace("Executed task: {}", taskName);

            // Live answers with one status row rather than nothing. It SCHEDULES the run and returns
            // immediately, where this engine has already finished the body by the time the row is
            // built — the wording is live's, the timing is this engine's.
            final List<ResultSetColumn> columns = Arrays.asList(
                new ResultSetColumn("status", StringType.VARCHAR));
            final List<Row> rows = Arrays.asList(new Row(Arrays.asList(
                "Task " + QualifiedName.parse(taskName).last() + " is scheduled to run immediately.")));
            return new ResultSet(columns, rows);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitAccessControlStatement(final FrostlakeParser.AccessControlStatementContext ctx) {
        return accessControlHandler.handle(ctx);
    }

    @Override
    public Object visitGrantStatement(final FrostlakeParser.GrantStatementContext ctx) {
        return grantRevokeHandler.handleGrant(ctx);
    }

    @Override
    public Object visitRevokeStatement(final FrostlakeParser.RevokeStatementContext ctx) {
        return grantRevokeHandler.handleRevoke(ctx);
    }

    @Override
    public Object visitListStatement(final FrostlakeParser.ListStatementContext ctx) {
        // LIST resolves the stage directory the same way PUT/GET/REMOVE and COPY do, so a URL-less
        // internal stage lists its engine-managed directory.
        return queryExecutor.executeListFromContext(ctx);
    }

    @Override
    public Object visitPutStatement(final FrostlakeParser.PutStatementContext ctx) {
        return queryExecutor.executePutFromContext(ctx);
    }

    @Override
    public Object visitGetStatement(final FrostlakeParser.GetStatementContext ctx) {
        return queryExecutor.executeGetFromContext(ctx);
    }

    @Override
    public Object visitRemoveStatement(final FrostlakeParser.RemoveStatementContext ctx) {
        return queryExecutor.executeRemoveFromContext(ctx);
    }

    @Override
    public Object visitDdlStatement(final FrostlakeParser.DdlStatementContext ctx) {
        // Snowflake: a DDL statement implicitly commits the current transaction (DDL runs as its own
        // transaction). Done at the shared dispatch point so a DDL inside a procedural BEGIN…END body —
        // e.g. the CREATE TEMP TABLE … AS SELECT * FROM <stream> flush idiom — ends the open
        // transaction exactly as it does at top level. EXCEPTION: ALTER SESSION only changes session
        // parameters and does NOT commit (loaders set QUERY_TAG between statements INSIDE their explicit
        // transaction — committing there split the transaction and a second stream read saw an
        // already-consumed, empty window).
        final boolean alterSession =
            ctx.alterStatement() != null && ctx.alterStatement().SESSION() != null;
        if (alterSession) {
            // A procedure runs in the caller's session and may not reconfigure it.
            rejectInsideProcedure("ALTER_SESSION");
        }
        if (!alterSession && queryExecutor.getTransactionManager().hasActiveTransaction()) {
            queryExecutor.getTransactionManager().commit();
        }
        return visitChildren(ctx);
    }

    // ==================== DML STATEMENTS ====================

    @Override
    public Object visitDmlStatement(final FrostlakeParser.DmlStatementContext ctx) {
        // A DML statement consumes any stream it reads (the offset advances when its transaction
        // commits). Open the consuming window here — the one dispatch point every execution path
        // shares (top-level execute(), procedural BEGIN…END bodies, task bodies) — so a stream read
        // by DML inside a stored procedure registers too. A plain SELECT must NOT consume.
        final boolean prev = queryExecutor.enterDmlStreamWindow();
        try {
            return visitChildren(ctx);
        } finally {
            queryExecutor.restoreDmlStreamWindow(prev);
        }
    }

    @Override
    public Object visitInsertStatement(final FrostlakeParser.InsertStatementContext ctx) {
        return dmlHandler.handleInsertStatement(ctx);
    }

    @Override
    public Object visitMultiTableInsertStatement(final FrostlakeParser.MultiTableInsertStatementContext ctx) {
        return dmlHandler.handleMultiTableInsert(ctx);
    }

    @Override
    public Object visitUpdateStatement(final FrostlakeParser.UpdateStatementContext ctx) {
        return dmlHandler.handleUpdateStatement(ctx);
    }

    @Override
    public Object visitDeleteStatement(final FrostlakeParser.DeleteStatementContext ctx) {
        return dmlHandler.handleDeleteStatement(ctx);
    }

    @Override
    public Object visitMergeStatement(final FrostlakeParser.MergeStatementContext ctx) {
        return dmlHandler.handleMergeStatement(ctx);
    }

    @Override
    public Object visitCopyIntoStatement(final FrostlakeParser.CopyIntoStatementContext ctx) {
        return dmlHandler.handleCopyIntoStatement(ctx);
    }

    // ==================== QUERY STATEMENTS ====================

    @Override
    public Object visitQueryStatement(final FrostlakeParser.QueryStatementContext ctx) {
        IntoClausePlacement.rejectHierarchicalSelectInto(ctx);
        return queryHandler.handleQueryStatement(ctx);
    }

    @Override
    public Object visitSelectStatement(final FrostlakeParser.SelectStatementContext ctx) {
        return queryHandler.handleSelectStatement(ctx);
    }

    // ==================== SHOW STATEMENTS ====================

    @Override
    public Object visitShowStatement(final FrostlakeParser.ShowStatementContext ctx) {
        try {
            return showHandler.handleShowStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitShowClassStatement(final FrostlakeParser.ShowClassStatementContext ctx) {
        try {
            return showHandler.handleShowClassStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitDescribeStatement(final FrostlakeParser.DescribeStatementContext ctx) {
        try {
            return showHandler.handleDescribeStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitSecurityObjectStatement(final FrostlakeParser.SecurityObjectStatementContext ctx) {
        try {
            return new SecurityObjectCommandHandler(catalog, queryExecutor).handle(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    @Override
    public Object visitSecurityObjectListing(final FrostlakeParser.SecurityObjectListingContext ctx) {
        try {
            return new SecurityObjectCommandHandler(catalog, queryExecutor).list(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    // ==================== PROCEDURAL STATEMENTS ====================

    @Override
    public Object visitProceduralStatement(final FrostlakeParser.ProceduralStatementContext ctx) {
        // These statements are SCRIPTING-ONLY: outside a BEGIN…END block live refuses each as a
        // syntax error at the statement's own first token (measured wording). "Outside" means no
        // scripting block is OPEN — block bodies reach this visit both by tree descent and by the
        // sequential statement path, and in both a block is already executing.
        //
        // BREAK, CONTINUE, RETURN and RAISE matter beyond the standalone case: statements need no
        // semicolon between them, so `SELECT x FROM t break` parses as a QUERY followed by a BREAK
        // and was accepted whole. Live refuses that input, naming the stray word — the trailing form
        // and the standalone form give the same sentence.
        final boolean strayFlowStatement = ctx.letStatement() != null
            || ctx.breakStatement() != null || ctx.continueStatement() != null
            || ctx.returnStatement() != null || ctx.raiseStatement() != null;
        if ((strayFlowStatement || ctx.nullStatement() != null)
                && !proceduralExecutor.isExecutingBlock()) {
            // Every one of the six is positioned at ITS OWN token, and the word is echoed as written:
            // `   BREAK` reads position 3, `\n  LET v := 1` reads line 2 position 2, `null` is echoed
            // in lower case. There is no second rule — an earlier reading had five of them pinned at
            // line 1 position 0, which was the test harness trimming the statement's leading
            // whitespace before the account ever saw it.
            final Token start = ctx.getStart();
            final int[] shown = LeadingCommentOffset.rebase(
                start.getLine(), start.getCharPositionInLine());
            throw new RuntimeException("SQL compilation error:\nsyntax error line "
                + shown[0] + " at position " + shown[1]
                + " unexpected '" + start.getText() + "'.");
        }
        return visitChildren(ctx);
    }

    @Override
    public Object visitDeclarationItem(final FrostlakeParser.DeclarationItemContext ctx) {
        // Check which type of declaration this is
        if (ctx.EXCEPTION() != null) {
            // Exception declaration; a bare `name EXCEPTION;` defaults to error code -20000
            // with no message text. An explicit code must lie strictly between the bounds the
            // refusal names — live rejects -20999 and -20000 themselves (live-verified).
            final String exceptionName = getText(ctx.identifier());
            long errorCode = -20000L;
            String message = null;
            if (ctx.INTEGER_LITERAL() != null) {
                errorCode = Long.parseLong(ctx.INTEGER_LITERAL().getText());
                if (ctx.MINUS() != null) {
                    errorCode = -errorCode;
                }
                if (errorCode <= -20999L || errorCode >= -20000L) {
                    final Token start = ctx.getStart();
                    throw new RuntimeException(SqlCompilationError.at(
                        start.getLine(), start.getCharPositionInLine(),
                        String.format(Locale.US,
                            " Invalid error code '%,d'. Must be between -20,999 and -20,000",
                            errorCode)));
                }
                message = extractStringLiteral(ctx.STRING_LITERAL());
            }

            final DeclareExceptionStatement stmt =
                new DeclareExceptionStatement(exceptionName, (int) errorCode, message);
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared exception: {}", exceptionName);
        } else if (ctx.CURSOR() != null) {
            // Cursor declaration — over a SELECT query or the name of a RESULTSET variable.
            final String cursorName = getText(ctx.identifier());
            final FrostlakeParser.CursorSourceContext src = ctx.cursorSource();
            final DeclareCursorStatement stmt;
            if (src.selectStatement() != null) {
                stmt = new DeclareCursorStatement(cursorName, getOriginalText(src.selectStatement()));
            } else {
                stmt = new DeclareCursorStatement(cursorName, null, getText(src.identifier()));
            }
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared cursor: {}", cursorName);
        } else if (ctx.RESULTSET() != null) {
            // ResultSet declaration — bare, or bound to a parenthesized statement by either DEFAULT or :=
            // (live accepts every combination).
            final String resultSetName = getText(ctx.identifier());
            final String selectQuery = resultSetInitializerText(ctx.resultSetSource());

            final DeclareResultSetStatement stmt = new DeclareResultSetStatement(resultSetName, selectQuery);
            noteInitialiserQuery(stmt, ctx.resultSetSource());
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared resultset: {}", resultSetName);
        } else {
            // Variable declaration
            final String varName = getText(ctx.identifier());
            Object defaultValue = null;

            if (ctx.DEFAULT() != null || ctx.COLON_EQ() != null) {
                if (ctx.dataTypeName() != null) {
                    // A typed default is compiled when it is reached; an untyped one while the block compiles.
                    proceduralExecutor.judgeBlockExpression(ctx.booleanExpr(),
                        parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters()));
                }
                defaultValue = evaluateExpression(ctx.booleanExpr());
            }

            final DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
                ctx.dataTypeName() != null ? parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters())
                    : inferredDeclarationType(ctx.booleanExpr()));
            if (ctx.dataTypeName() == null) {
                stmt.setInitialiserType(initialiserOwnType(ctx.booleanExpr()));
            }
            if (ctx.booleanExpr() != null) {
                stmt.setInitializerAt(ctx.booleanExpr().getStart().getLine(),
                    ctx.booleanExpr().getStart().getCharPositionInLine());
            }
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared variable: {}", varName);
        }
        return null;
    }

    @Override
    public Object visitUntypedDeclarationItem(final FrostlakeParser.UntypedDeclarationItemContext ctx) {
        // A DECLARE-section item with the type omitted (`v := expr;`); the type is inferred from the value.
        final String varName = getText(ctx.identifier());
        final Object defaultValue = evaluateExpression(ctx.booleanExpr());
        final DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
            inferredDeclarationType(ctx.booleanExpr()));
        stmt.setInitialiserType(initialiserOwnType(ctx.booleanExpr()));
        stmt.setInitializerAt(ctx.booleanExpr().getStart().getLine(),
            ctx.booleanExpr().getStart().getCharPositionInLine());
        proceduralExecutor.executeStatement(stmt);
        logger.trace("Declared variable (untyped): {}", varName);
        return null;
    }

    @Override
    public Object visitVariableDeclaration(final FrostlakeParser.VariableDeclarationContext ctx) {
        final String varName = getText(ctx.identifier());
        Object defaultValue = null;

        if (ctx.DEFAULT() != null || ctx.COLON_EQ() != null) {
            defaultValue = evaluateExpression(ctx.booleanExpr());
        }

        final DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
            ctx.dataTypeName() != null ? parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters())
                : inferredDeclarationType(ctx.booleanExpr()));
        if (ctx.dataTypeName() == null) {
            stmt.setInitialiserType(initialiserOwnType(ctx.booleanExpr()));
        }
        if (ctx.booleanExpr() != null) {
            stmt.setInitializerAt(ctx.booleanExpr().getStart().getLine(),
                ctx.booleanExpr().getStart().getCharPositionInLine());
        }
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Declared variable: {}", varName);
        return null;
    }

    @Override
    public Object visitCursorDeclaration(final FrostlakeParser.CursorDeclarationContext ctx) {
        final String cursorName = getText(ctx.identifier());
        final String selectQuery = getOriginalText(ctx.selectStatement());

        final DeclareCursorStatement stmt = new DeclareCursorStatement(cursorName, selectQuery);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Declared cursor: {}", cursorName);
        return null;
    }

    /** Note where a RESULTSET initialiser's query begins, which a failure running it is reported at. */
    private static void noteInitialiserQuery(final DeclareResultSetStatement stmt,
                                             final FrostlakeParser.ResultSetSourceContext query) {
        if (query != null) {
            stmt.setQueryAt(query.getStart().getLine(), query.getStart().getCharPositionInLine());
        }
    }

    /**
     * The stored query text of a RESULTSET initializer — the statement inside the parentheses — or null for
     * the bare spelling. An anonymous block runs as an EXECUTE IMMEDIATE's text does (see
     * {@link #immediateBlockText}).
     */
    private String resultSetInitializerText(final FrostlakeParser.ResultSetSourceContext query) {
        if (query == null) {
            return null;
        }
        return query.resultSetBlock() != null ? immediateBlockText(query.resultSetBlock()) : getOriginalText(query);
    }

    /**
     * An anonymous block a RESULTSET is filled from, as the statement that runs it: an EXECUTE IMMEDIATE of its text,
     * which compiles the block when the RESULTSET is filled, in a frame of its own, and answers what the block returns
     * (live-verified: {@code (BEGIN RETURN nosuch; END)} is refused "invalid identifier 'NOSUCH'" placed in the block's
     * own text when it runs, and not at all on a branch that never runs).
     */
    String immediateBlockText(final FrostlakeParser.ResultSetBlockContext block) {
        final String text = getOriginalText(block);
        return "EXECUTE IMMEDIATE '" + text.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    @Override
    public Object visitSetStatement(final FrostlakeParser.SetStatementContext ctx) {
        rejectInsideProcedure("SET");
        final String varName = getText(ctx.identifier());

        // Check if expression exists (might be null for non-procedural SET statements)
        if (ctx.expression() == null) {
            logger.trace("Non-procedural SET statement, skipping: {}", varName);
            return null;
        }

        // Special handling for autocommit setting
        if (varName.equalsIgnoreCase("autocommit")) {
            final Object value = evaluateExpression(ctx.expression());
            final boolean autoCommitValue;
            if (value instanceof Boolean) {
                autoCommitValue = (Boolean) value;
            } else if (value instanceof String) {
                final String strValue = (String) value;
                autoCommitValue = strValue.equalsIgnoreCase("true") || strValue.equals("1");
            } else {
                autoCommitValue = value != null && !"0".equals(value.toString()) && !"false".equalsIgnoreCase(value.toString());
            }
            if (queryExecutor.getDatabaseEngine() != null) {
                queryExecutor.getDatabaseEngine().setAutoCommit(autoCommitValue);
                logger.trace("Set autocommit: {}", autoCommitValue);
            }
            return null;
        }

        final BaseExpression expr = buildExpression(ctx.expression());
        final SetStatement stmt = new SetStatement(varName, expr);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Set variable: {}", varName);
        return null;
    }

    @Override
    public Object visitSessionSetStatement(final FrostlakeParser.SessionSetStatementContext ctx) {
        // Session-level SET is a SQL command (scripting assigns with := / LET), so its right-hand side is
        // a SQL expression: session variables and literals resolve, a bare scripting name does not.
        final SecurityManager sm = queryExecutor.getSecurityManager();
        if (ctx.identifierList() != null) {
            // SET (var1, var2, ...) = (expr1, expr2, ...): every value is computed before any variable is set, so
            // SET (a, b) = (50, 2 * $a) reads a's old value and a refused value sets none (live-verified).
            final List<FrostlakeParser.IdentifierContext> ids = ctx.identifierList().identifier();
            final List<FrostlakeParser.BooleanExprContext> exprs = ctx.booleanExprList().booleanExpr();
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                values.add(i < exprs.size() ? sessionValueOf(exprs.get(i)) : null);
            }
            for (int i = 0; i < ids.size(); i++) {
                setSessionVar(sm, getText(ids.get(i)).toUpperCase(), values.get(i));
            }
        } else {
            // SET var = expr
            final String name = getText(ctx.identifier()).toUpperCase();
            setSessionVar(sm, name, sessionValueOf(ctx.booleanExpr()));
        }
        return null;
    }

    /**
     * The value a session variable is set to. A variable remembers the value, and the value keeps the
     * type its expression yields, so a decimal stays exact rather than widening to a double:
     * {@code SET d = 1.5} is NUMBER(2,1) live, not a FLOAT.
     *
     * @param ctx the right-hand side
     * @return the value
     */
    private Object sessionValueOf(final FrostlakeParser.BooleanExprContext ctx) {
        final FrostlakeParser.ExpressionContext value = ctx instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) ctx).expression() : null;
        if (value instanceof FrostlakeParser.LiteralExprContext
                && ((FrostlakeParser.LiteralExprContext) value).literal().FLOAT_LITERAL() != null) {
            // Trailing zeros are not part of the value live remembers: SET d = 1.500 holds 1.5.
            return new BigDecimal(((FrostlakeParser.LiteralExprContext) value)
                .literal().FLOAT_LITERAL().getText()).stripTrailingZeros();
        }
        // The source folds to a constant, or is refused as one that does not (see SessionAssignmentSource).
        if (SessionAssignmentSource.isWholeSubquery(ctx)) {
            return SessionAssignmentSource.assignable(evaluateSqlExpression(ctx), ctx, queryExecutor);
        }
        final Object folded;
        try {
            folded = evaluateSqlExpression(ctx);
        } catch (final RuntimeException failed) {
            throw SessionAssignmentSource.foldFailure(failed, ctx);
        }
        return SessionAssignmentSource.assignable(folded, ctx, queryExecutor);
    }

    private void setSessionVar(final SecurityManager sm, final String name, final Object value) {
        if (sm != null) {
            sm.getSessionContext().setSessionVariable(name, value);
        } else {
            queryExecutor.getSessionVariables().put(name, value);
        }
        logger.trace("SET session variable {}={}", name, value);
    }

    @Override
    public Object visitSessionUnsetStatement(final FrostlakeParser.SessionUnsetStatementContext ctx) {
        final SecurityManager sm = queryExecutor.getSecurityManager();
        final List<String> names = new ArrayList<>();
        if (ctx.identifier() != null) {
            names.add(getText(ctx.identifier()).toUpperCase());
        } else if (ctx.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext id : ctx.identifierList().identifier()) {
                names.add(getText(id).toUpperCase());
            }
        }
        for (final String name : names) {
            if (sm != null) {
                sm.getSessionContext().unsetSessionVariable(name);
            } else {
                queryExecutor.getSessionVariables().remove(name);
            }
            logger.trace("UNSET session variable {}", name);
        }
        return null;
    }

    @Override
    public Object visitLetStatement(final FrostlakeParser.LetStatementContext ctx) {
        final String varName = getText(ctx.identifier());

        // Check if this is a cursor declaration (LET cursor_name CURSOR FOR SELECT...)
        if (ctx.CURSOR() != null) {
            // Over a SELECT query or the name of a RESULTSET variable, as the DECLARE section takes it.
            final FrostlakeParser.CursorSourceContext src = ctx.cursorSource();
            final DeclareCursorStatement stmt = src.selectStatement() != null
                ? new DeclareCursorStatement(varName, getOriginalText(src.selectStatement()))
                : new DeclareCursorStatement(varName, null, getText(src.identifier()));
            proceduralExecutor.executeStatement(stmt);
            logger.trace("Let cursor: {}", varName);
            return null;
        }

        // LET name RESULTSET [{:= | DEFAULT} ( <query> )] — declared exactly like DECLARE name
        // RESULTSET: the query (SELECT, EXECUTE IMMEDIATE or CALL) runs where the LET stands, and the
        // bare spelling declares an unbound resultset.
        if (ctx.RESULTSET() != null) {
            final DeclareResultSetStatement rsStmt =
                new DeclareResultSetStatement(varName, resultSetInitializerText(ctx.resultSetSource()));
            noteInitialiserQuery(rsStmt, ctx.resultSetSource());
            proceduralExecutor.executeStatement(rsStmt);
            logger.trace("Let resultset: {}", varName);
            return null;
        }

        // Regular LET with expression. A typed one's value is compiled when it is reached; an untyped one's
        // initialiser while the block compiles.
        if (ctx.dataTypeName() != null) {
            proceduralExecutor.judgeBlockExpression(ctx.booleanExpr(),
                parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters()));
        }
        final Object defaultValue = evaluateExpression(ctx.booleanExpr());
        final DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
            ctx.dataTypeName() != null ? parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters())
                : inferredDeclarationType(ctx.booleanExpr()));
        if (ctx.dataTypeName() == null) {
            stmt.setInitialiserType(initialiserOwnType(ctx.booleanExpr()));
        }
        stmt.setInitializerAt(ctx.booleanExpr().getStart().getLine(),
            ctx.booleanExpr().getStart().getCharPositionInLine());
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Let variable: {}", varName);
        return null;
    }

    @Override
    public Object visitAssignmentStatement(final FrostlakeParser.AssignmentStatementContext ctx) {
        final String varName = getText(ctx.identifier());

        // Handle: rs := (CALL …) into a RESULTSET: the variable holds the CALL's result set.
        if (ctx.callStatement() != null && proceduralExecutor.holdsResultSet(varName)) {
            final FrostlakeParser.CallStatementContext call = ctx.callStatement();
            proceduralExecutor.assignResultSetQuery(varName, getOriginalText(call),
                call.getStart().getLine(), call.getStart().getCharPositionInLine());
            return null;
        }

        // Handle: var := (call proc()) — capture procedure return value
        if (ctx.callStatement() != null) {
            final Object result = visitCallStatement(ctx.callStatement());
            // If the procedure returned a ResultSet, extract the first column value
            Object value = result;
            if (result instanceof ResultSet) {
                final ResultSet rs = (ResultSet) result;
                if (rs.getRowCount() > 0) {
                    value = rs.getRows().get(0).getValue(0);
                } else {
                    value = null;
                }
            }
            proceduralExecutor.executeStatement(new SetStatement(varName,
                new LiteralExpression(value)));
            logger.trace("Assigned variable {} from procedure call", varName);
            return null;
        }

        // Handle: rs := (EXECUTE IMMEDIATE :stmt [USING (...)]) — the documented Snowflake
        // RESULTSET-assignment form; the dynamic SQL runs when the assignment executes.
        if (ctx.executeImmediateStatement() != null) {
            final FrostlakeParser.ExecuteImmediateStatementContext ei = ctx.executeImmediateStatement();
            final BaseExpression sqlExpr = buildExpression(ei.expression());
            final ExecuteImmediateExpression dynamic = new ExecuteImmediateExpression(sqlExpr, usingBindings(ei));
            dynamic.setStatementAt(ei.getStart().getLine(), ei.getStart().getCharPositionInLine());
            proceduralExecutor.executeStatement(new SetStatement(varName, dynamic));
            logger.trace("Assigned variable {} from EXECUTE IMMEDIATE", varName);
            return null;
        }

        // Handle: rs := (SHOW …), (INSERT …), (CREATE …) … — a RESULTSET runs the statement where the assignment
        // stands and holds its answer, a failure the statement's, at its first word; any other target refuses it
        // before any of it runs (see AssignedStatement).
        if (ctx.resultSetStatement() != null) {
            proceduralExecutor.executeStatement(new SetStatement(varName, statementValue(ctx.resultSetStatement())));
            logger.trace("Assigned resultset {} from a statement", varName);
            return null;
        }

        // Handle: rs := (BEGIN … END) — a RESULTSET holds the block's answer, the block run as EXECUTE IMMEDIATE runs
        // its text. The block compiled with its target already refused any other.
        if (ctx.resultSetBlock() != null) {
            final FrostlakeParser.ResultSetBlockContext block = ctx.resultSetBlock();
            if (!proceduralExecutor.holdsResultSet(varName)) {
                throw AssignedStatement.invalidValue(AssignedStatement.BLOCK);
            }
            proceduralExecutor.assignResultSetQuery(varName, immediateBlockText(block),
                block.getStart().getLine(), block.getStart().getCharPositionInLine());
            return null;
        }

        // Check if expression exists (might be null for non-procedural contexts)
        if (ctx.booleanExpr() == null) {
            logger.trace("Non-procedural assignment statement, skipping");
            return null;
        }

        final BaseExpression expr = ProceduralBlockBuilder.judged(buildExpression(ctx.booleanExpr()), ctx.booleanExpr());
        final SetStatement stmt = new SetStatement(varName, expr, getOriginalText(ctx.booleanExpr()));
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Assigned variable: {}", varName);
        return null;
    }


    @Override
    public Object visitOpenStatement(final FrostlakeParser.OpenStatementContext ctx) {
        final String cursorName = getText(ctx.identifier());
        final Cursor cursor = proceduralExecutor.getCursor(cursorName);

        if (cursor == null) {
            throw new RuntimeException("Cursor not found: " + cursorName);
        }

        // CURSOR FOR <resultset_var>: open over the RESULTSET's rows instead of parsing a SELECT.
        if (cursor.getResultSetVariableName() != null) {
            proceduralExecutor.openCursorOverResultSet(cursor);
            logger.trace("Opened cursor over resultset variable: {}", cursorName);
            return null;
        }

        // Substitute bind variables from procedural scope before parsing
        String selectSql = proceduralExecutor != null
            ? proceduralExecutor.substituteBindVariables(cursor.getSelectQuery())
            : cursor.getSelectQuery();
        // OPEN c USING (a, b, …): bind the values positionally to the cursor query's `?` placeholders.
        if (ctx.USING() != null && ctx.expressionList() != null) {
            final List<Object> binds = new ArrayList<>();
            for (final FrostlakeParser.ExpressionContext bindExpr : ctx.expressionList().expression()) {
                binds.add(evaluateExpression(bindExpr));
            }
            selectSql = JdbcMarshaling.substitutePlaceholders(selectSql, binds);
        }
        // Execute the SELECT query to populate the cursor
        final ResultSet resultSet = queryExecutor.executeSelectFromContext(
            parseSelectStatement(selectSql)
        );
        cursor.open(resultSet);

        logger.trace("Opened cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitFetchStatement(final FrostlakeParser.FetchStatementContext ctx) {
        final String cursorName = getText(ctx.identifier());
        final List<String> targetVars = new ArrayList<>();

        for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifierList().identifier()) {
            targetVars.add(getText(idCtx));
        }

        final FetchStatement stmt = new FetchStatement(cursorName, targetVars);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Fetched from cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitCloseStatement(final FrostlakeParser.CloseStatementContext ctx) {
        final String cursorName = getText(ctx.identifier());

        final CloseStatement stmt = new CloseStatement(cursorName);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Closed cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitSelectIntoStatement(final FrostlakeParser.SelectIntoStatementContext ctx) {
        rejectIntoOutsideBlock(ctx);
        // Run the query without its INTO clause, as written (see SelectIntoText), then assign to the variables.
        final List<ResultSet> results = queryExecutor.execute(SelectIntoText.withoutInto(ctx));
        final ResultSet rs = results.isEmpty() ? null : results.get(0);
        final int rowCount = rs == null ? 0 : rs.getRowCount();
        final List<FrostlakeParser.IntoTargetContext> targets = ctx.intoTargetList().intoTarget();

        // Snowflake Scripting (verified against live Snowflake): a SELECT ... INTO over ZERO rows
        // assigns NULL to every target and continues — deployment scripts depend on it (probe idioms
        // like SELECT "stale" INTO :flag FROM RESULT_SCAN(SHOW ...) on a fresh database, and
        // walk-past-the-end fetch loops that terminate on the NULL). Only MORE than one row errors.
        if (rowCount > 1) {
            throw new RuntimeException(
                "Select statement in SELECT INTO returned wrong number of rows: " + rowCount);
        }
        if (rowCount == 0) {
            for (final FrostlakeParser.IntoTargetContext target : targets) {
                assignSelectIntoTarget(getText(target.identifier()), null);
            }
            logger.trace("SELECT INTO: zero rows — assigned NULL to {} variable(s)", targets.size());
            return null;
        }
        final Row firstRow = rs.getRows().get(0);
        if (targets.size() != firstRow.getValues().size()) {
            throw new RuntimeException("SELECT INTO: number of INTO targets (" + targets.size()
                + ") does not match the number of selected columns (" + firstRow.getValues().size() + ")");
        }
        for (int i = 0; i < targets.size(); i++) {
            assignSelectIntoTarget(getText(targets.get(i).identifier()), firstRow.getValue(i));
        }
        logger.trace("SELECT INTO: assigned {} variable(s)", targets.size());
        return null;
    }

    /**
     * Refuse a SELECT … INTO that is not a statement of a Snowflake Scripting block, while the statement compiles
     * and before any name in it is resolved (live-verified): at the top level, in EXECUTE IMMEDIATE's text — a
     * block's too — the INTO clause "is not allowed in this context", positioned at the query's SELECT, past a
     * WITH clause.
     */
    private void rejectIntoOutsideBlock(final FrostlakeParser.SelectIntoStatementContext ctx) {
        for (ParserRuleContext up = ctx.getParent(); up != null; up = up.getParent()) {
            if (up instanceof FrostlakeParser.BeginEndBlockContext) {
                return;
            }
        }
        if (proceduralExecutor != null && proceduralExecutor.isRunningBlockSelectInto()) {
            return;
        }
        final Token select = ctx.SELECT().getSymbol();
        throw new RuntimeException(SqlCompilationError.intoClauseNotAllowed(select.getLine(),
            select.getCharPositionInLine()));
    }

    /** Assign a value to a SELECT INTO target through SetStatement, coercing it to the declared type. */
    private void assignSelectIntoTarget(final String varName, final Object value) {
        if (proceduralExecutor != null) {
            proceduralExecutor.executeStatement(new SetStatement(varName, new LiteralExpression(value)));
        }
    }

    private FrostlakeParser.SelectStatementContext parseSelectStatement(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        final SyntaxErrorListener errorListener = new SyntaxErrorListener(sql);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        final FrostlakeParser.SelectStatementContext selectStatementContext = parser.selectStatement();
        errorListener.throwIfErrors();
        NamedCallRewrite.apply(selectStatementContext);

        return selectStatementContext;
    }

    @Override
    public Object visitIfStatement(final FrostlakeParser.IfStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildIfStatement(ctx));
        logger.trace("Executed IF statement");
        return null;
    }

    @Override
    public Object visitCaseStatement(final FrostlakeParser.CaseStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildCaseStatement(ctx));
        logger.trace("Executed CASE statement");
        return null;
    }

    @Override
    public Object visitLoopStatement(final FrostlakeParser.LoopStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildLoopStatement(ctx));
        logger.trace("Executed LOOP statement");
        return null;
    }

    @Override
    public Object visitWhileStatement(final FrostlakeParser.WhileStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildWhileStatement(ctx));
        logger.trace("Executed WHILE statement");
        return null;
    }

    @Override
    public Object visitForStatement(final FrostlakeParser.ForStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildForStatement(ctx));
        logger.trace("Executed FOR statement");
        return null;
    }

    @Override
    public Object visitRepeatStatement(final FrostlakeParser.RepeatStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildRepeatStatement(ctx));
        logger.trace("Executed REPEAT statement");
        return null;
    }

    /**
     * A CALL result column's type: the declared RETURNS type when the executed RETURN converted its
     * value to it, else the static type of the expression the RETURN names — a text or a binary one at
     * the column's full width either way, as live declares it — else the nominal VARCHAR.
     */
    private DataType callResultColumnType() {
        final DataType typed = proceduralExecutor.getReturnedResultType();
        return typed == null ? AnonymousBlockResult.TEXT : typed;
    }

    /**
     * A CALL result column's type when no RETURN ran: the declared RETURNS type, a text or a binary one
     * at the column's full width (live-verified: RETURNS NUMBER(5,2) answers a NULL NUMBER(5,2),
     * RETURNS DATE a NULL DATE, RETURNS VARCHAR(10) a NULL VARCHAR(16777216) and RETURNS BINARY(10) a
     * NULL BINARY(8388608)); the nominal VARCHAR for a procedure that returns a table.
     */
    private static DataType returnlessCallColumnType(final Procedure procedure) {
        final DataType declared = procedure.returnsTable() ? null : procedure.getReturnType();
        return declared == null ? AnonymousBlockResult.TEXT : AnonymousBlockResult.columnType(declared);
    }

    /** An untyped declaration's initialiser typed on its own, or null — see ProceduralExecutor#initialiserOwnType. */
    private DataType initialiserOwnType(final FrostlakeParser.BooleanExprContext initialiser) {
        if (initialiser == null) {
            return null;
        }
        return proceduralExecutor.initialiserOwnType(expressionBuilder.buildExpression(initialiser),
            DeclarationTypes.sqlExpression(initialiser));
    }

    /** The type an untyped declaration takes from its initialiser, or null — the executor's rule. */
    private DataType inferredDeclarationType(final FrostlakeParser.BooleanExprContext initialiser) {
        if (initialiser == null) {
            return null;
        }
        final DataType direct = proceduralExecutor.inferUntypedDeclarationType(
            expressionBuilder.buildExpression(initialiser));
        return direct != null ? direct
            : proceduralExecutor.typeOfSqlInitialiser(DeclarationTypes.sqlExpression(initialiser));
    }

    @Override
    public Object visitReturnStatement(final FrostlakeParser.ReturnStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildReturnStatement(ctx));
        logger.trace("Executed RETURN statement");
        return proceduralExecutor.getReturnValue();
    }

    @Override
    public Object visitBreakStatement(final FrostlakeParser.BreakStatementContext ctx) {
        final Statement brk = new Statement(StatementType.BREAK) {};
        if (ctx.loopLabel() != null) {
            brk.setLabel(getText(ctx.loopLabel()));
        }
        proceduralExecutor.executeStatement(brk);
        logger.trace("Executed BREAK statement");
        return null;
    }

    @Override
    public Object visitContinueStatement(final FrostlakeParser.ContinueStatementContext ctx) {
        final Statement cont = new Statement(StatementType.CONTINUE) {};
        if (ctx.loopLabel() != null) {
            cont.setLabel(getText(ctx.loopLabel()));
        }
        proceduralExecutor.executeStatement(cont);
        logger.trace("Executed CONTINUE statement");
        return null;
    }

    @Override
    public Object visitNullStatement(final FrostlakeParser.NullStatementContext ctx) {
        // NULL statement is a no-op in Snowflake scripting
        logger.trace("Executed NULL statement (no-op)");
        return null;
    }

    @Override
    public Object visitRaiseStatement(final FrostlakeParser.RaiseStatementContext ctx) {
        final RaiseStatement stmt;

        if (ctx.identifier() != null && ctx.STRING_LITERAL() == null) {
            // RAISE exception_name - must be a user-defined exception
            final String name = getText(ctx.identifier());
            // Check if it's a user-defined exception
            if (proceduralExecutor.hasException(name)) {
                stmt = new RaiseStatement(name);
                stmt.setSourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
            } else {
                // Undefined exception - throw error immediately
                throw new RuntimeException("Undefined exception: " + name);
            }
        } else if (ctx.STRING_LITERAL() != null) {
            // RAISE 'message'
            final String msg = extractStringLiteral(ctx.STRING_LITERAL());
            final BaseExpression message = new LiteralExpression(msg);
            stmt = new RaiseStatement(message);
        } else {
            // RAISE without arguments — re-raise the exception currently being handled (Snowflake), keeping
            // its original message/type; only if not inside a handler does it fall back to a generic error.
            final Exception handled = proceduralExecutor.getCurrentHandledException();
            if (handled instanceof RuntimeException) {
                throw (RuntimeException) handled;
            }
            if (handled != null) {
                throw new RuntimeException(handled.getMessage(), handled);
            }
            final BaseExpression message = new LiteralExpression("Error raised");
            stmt = new RaiseStatement(message);
        }

        proceduralExecutor.executeStatement(stmt);

        logger.trace("Executed RAISE statement");
        return null;
    }

    /** Index of the parameter named {@code name} (case-insensitive), or -1 if the procedure has no such parameter. */
    private int indexOfParameter(final List<Parameter> params, final String name) {
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * How deep inside an OWNER's-rights stored-procedure body execution currently is. Such a
     * procedure may not change the session it runs in, and the statements that would are refused
     * wherever they appear — including inside a BEGIN…END body, which the procedural executor
     * runs rather than this loop. An EXECUTE AS CALLER body never arms this.
     */
    private int procedureDepth;

    /**
     * Refuse a statement type a stored procedure may not run. Live reports these as
     * "Unsupported statement type '<TYPE>'" and names the family, not the specific statement:
     * every USE form is USE, and a session parameter change is ALTER_SESSION.
     */
    void rejectInsideProcedure(final String statementType) {
        if (procedureDepth > 0) {
            throw new RuntimeException("Stored procedure execution error: "
                + "Unsupported statement type '" + statementType + "'.");
        }
    }

    /**
     * Enforces Snowflake's scoped-transaction rule at a stored procedure's normal completion: a
     * transaction the procedure STARTED (none was open at CALL time) and left open is rolled back
     * and the call fails with Snowflake's exact wording (live-verified). A transaction that was
     * already open when the CALL began is the caller's and passes through untouched.
     */
    private void rejectOpenScopedTransaction(final boolean txnOpenBeforeCall) {
        if (!txnOpenBeforeCall && queryExecutor.getTransactionManager().isExplicitTransaction()) {
            try {
                queryExecutor.getTransactionManager().rollback();
            } catch (final RuntimeException rollbackFailure) {
                // the error below is the primary signal
            }
            throw new RuntimeException("Stored procedure execution error: "
                + "Scoped transaction started in stored procedure is incomplete and it was rolled back.");
        }
    }

    @Override
    public Object visitCallStatement(final FrostlakeParser.CallStatementContext ctx) {
        CallArgumentTypes.enterCall();
        // An owner's-rights body re-pins how its refusals are worded (see executeCall); the caller's comes back.
        final SessionRole callerRole = SessionRole.pinned();
        try {
            return executeCall(ctx);
        } finally {
            SessionRole.restore(callerRole);
            CallArgumentTypes.exitCall();
        }
    }

    /**
     * A missing procedure, in the unknown-FUNCTION vocabulary (live-verified): the name as written, each
     * part quoted only when it needs quotes, with a full stop and no argument signature, and a qualified
     * spelling reported as an unknown user-defined function.
     */
    private static RuntimeException unknownProcedure(final String[] parts) {
        final StringBuilder spelled = new StringBuilder();
        for (final String part : parts) {
            spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return new RuntimeException(SqlCompilationError.of(parts.length > 1
            ? "Unknown user-defined function " + spelled + "."
            : "Unknown function " + spelled + "."));
    }

    /**
     * A CALL of one of the account's SYSTEM$ functions: one row, in a column named after the function, holding what
     * the function answers for the arguments as written (live-verified: CALL SYSTEM$WAIT(0) answers 'waited 0
     * seconds' in a column SYSTEM$WAIT, and an unknown SYSTEM$ name is an unknown function).
     *
     * <p>The call runs as the SELECT of the same call, written where the CALL is written, and reports its places in
     * the CALL's own text: {@code CALL SYSTEM$TYPEOF(nosuch)} names 'NOSUCH' at position 19, on the line it stands on
     * (live-verified). An argument count outside the function's is refused first, and placed nowhere, "error line 0
     * at position -1", as the account refuses a CALL's count.
     */
    private ResultSet callSystemFunction(final FrostlakeParser.CallStatementContext ctx) {
        final String name = ctx.systemFunctionName().getText();
        rejectSystemCallArity(ctx, name.toUpperCase(Locale.ROOT));
        final Token call = ctx.CALL().getSymbol();
        final String afterCall = call.getInputStream().getText(
            Interval.of(call.getStopIndex() + 1, ctx.RPAREN().getSymbol().getStopIndex()));
        final List<ResultSet> answered = queryExecutor.executeFrom("SELECT" + afterCall,
            new SourcePosition(1, "SELECT".length() - call.getText().length()));
        final ResultSet answer = answered.isEmpty() ? null : answered.get(0);
        final Object value = answer == null || answer.getRowCount() == 0 ? null : answer.getRows().get(0).getValue(0);
        final DataType type = answer == null || answer.getColumns().isEmpty() ? null
            : answer.getColumns().get(0).getDataType();
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(name.toUpperCase(Locale.ROOT), AnonymousBlockResult.columnType(type)));
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(value));
        return new ResultSet(columns, rows);
    }

    /** Refuse a CALL of a SYSTEM$ function with another number of arguments than the function takes. */
    private static void rejectSystemCallArity(final FrostlakeParser.CallStatementContext ctx, final String name) {
        final List<String> written = new ArrayList<>();
        if (ctx.callArguments() != null) {
            for (final FrostlakeParser.CallArgumentContext argument : ctx.callArguments().callArgument()) {
                written.add(ParseTreeText.getOriginalText(argument));
            }
        }
        final int minimum = SystemFunctionArity.minimumOr(name);
        final int maximum = SystemFunctionArity.maximumOr(name);
        final String echo = name + "(" + String.join(", ", written) + ")";
        if (minimum >= 0 && written.size() < minimum) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "not enough arguments for function [" + echo
                + "], expected " + minimum + ", got " + written.size()));
        }
        if (maximum >= 0 && written.size() > maximum) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function [" + echo
                + "] expected " + maximum + ", got " + written.size()));
        }
    }

    private Object executeCall(final FrostlakeParser.CallStatementContext ctx) {
        if (ctx.systemFunctionName() != null) {
            return callSystemFunction(ctx);
        }
        final String qualifiedName = getText(ctx.qualifiedName());
        final String[] parts = qualifiedNameParts(ctx.qualifiedName());
        // The canonical name, resolved exactly: CALL proccase() does not reach "procCase" (live-verified).
        final String procName = parts[parts.length - 1];

        final Schema schema;
        if (parts.length == 3) {
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
        } else if (catalog.getCurrentDatabase() == null
                || parts.length == 1 && catalog.getCurrentSchema() == null) {
            // With no current database a name the session cannot place names no procedure at all, and
            // misses in the same words as one that is simply absent (live-verified).
            throw unknownProcedure(parts);
        } else if (parts.length == 2) {
            schema = catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0]);
        } else {
            schema = resolveCurrentSchema();
        }

        final List<Procedure> overloads = schema.getProcedureOverloads(procName);
        if (overloads.isEmpty()) {
            throw unknownProcedure(parts);
        }
        // An overloaded name runs the overload the arguments choose, as live chooses it.
        final Procedure procedure = overloads.size() == 1 || ctx.callArguments() == null
            ? overloads.get(0)
            : CallArgumentTypes.chooseOverload(procName, overloads, ctx.callArguments().callArgument(),
                new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(),
                    queryExecutor));

        // Evaluate call arguments — positional and/or named (name => value). Positional args bind to
        // parameters left-to-right; a named arg binds to the parameter whose name it matches; any
        // parameter left unbound falls back to its DEFAULT (Snowflake semantics), so a named arg may
        // legitimately skip an earlier defaulted parameter. Snowflake dispatches CALL as SQL, so an
        // argument is a SQL expression: a scripting name must be written :name, and a bare one is an
        // identifier ("invalid identifier 'V'", live-verified) — hence evaluateSqlExpression.
        final List<Parameter> params = procedure.getParameters();
        final Object[] boundValues = new Object[params.size()];
        final boolean[] boundFlags = new boolean[params.size()];
        int positionalIndex = 0;
        if (ctx.callArguments() != null) {
            // A CALL is all-named or all-positional. Live-verified on a real account:
            // CALL p_ab(1, b => 2) fails "illegal mixing of named and positional arguments for function
            // P_AB", while the all-named CALL p_ab(b => 2, a => 1) works — and the restriction is
            // CALL's alone: the very same mixed shape on a UDF, SELECT f_ab(1, b => 2), returns 12.
            boolean sawNamed = false;
            boolean sawPositional = false;
            for (final FrostlakeParser.CallArgumentContext argCtx : ctx.callArguments().callArgument()) {
                if (argCtx.namedArgument() != null) {
                    sawNamed = true;
                } else {
                    sawPositional = true;
                }
            }
            if (sawNamed && sawPositional) {
                throw new RuntimeException("SQL compilation error:\n"
                    + "illegal mixing of named and positional arguments for function " + procName);
            }
            CallArgumentTypes.refuseRows(procName, params, ctx.callArguments().callArgument());
            // Each argument's TYPE is matched against its parameter before any is evaluated, as live
            // matches them while the CALL compiles, for a CALL written at the top level only. One issued
            // from a block or a procedure body arrives with its :binds already substituted into the text
            // as literals, which erases the declared types live judges them by.
            if (!CallArgumentTypes.isNested()
                    && (proceduralExecutor == null || proceduralExecutor.getBlockDepth() == 0)) {
                CallArgumentTypes.check(procName, params, ctx.callArguments().callArgument(), sawNamed,
                    new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(),
                        queryExecutor));
            }
            for (final FrostlakeParser.CallArgumentContext argCtx : ctx.callArguments().callArgument()) {
                if (argCtx.namedArgument() != null) {
                    final String argName = getText(argCtx.namedArgument().identifier());
                    final int idx = indexOfParameter(params, argName);
                    if (idx < 0) {
                        throw new RuntimeException("Unknown argument '" + argName + "' for procedure: " + qualifiedName);
                    }
                    if (boundFlags[idx]) {
                        throw new RuntimeException("Argument '" + argName + "' specified more than once for procedure: " + qualifiedName);
                    }
                    if (argCtx.namedArgument().expression() == null) {
                        throw new RuntimeException(
                            "A bare subquery CALL argument is not supported; parenthesize it: (SELECT ...)");
                    }
                    boundValues[idx] = evaluateSqlExpression(argCtx.namedArgument().expression());
                    boundFlags[idx] = true;
                } else {
                    if (positionalIndex >= params.size()) {
                        throw new RuntimeException("Too many arguments for procedure: " + qualifiedName);
                    }
                    boundValues[positionalIndex] = evaluateSqlExpression(argCtx.expression());
                    boundFlags[positionalIndex] = true;
                    positionalIndex++;
                }
            }
        }

        final List<Object> arguments = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            if (boundFlags[i]) {
                arguments.add(boundValues[i]);
                continue;
            }
            final Parameter param = params.get(i);
            if (!param.hasDefault()) {
                throw new RuntimeException("Missing required argument for parameter: " + param.getName());
            }
            final ExpressionEvaluator eval =
                new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(), queryExecutor);
            arguments.add(eval.evaluate(param.getDefaultValue(), null));
        }

        final UdfLanguage language = procedure.getUdfLanguage();
        if (StrictProcedureCall.skipsBody(procedure, arguments)) {
            if (language == UdfLanguage.SQL) {
                throw new RuntimeException(StrictProcedureCall.SQL_REFUSAL);
            }
            if (procedure.returnsTable()) {
                throw new RuntimeException(StrictProcedureCall.tableRefusal(procedure));
            }
            final List<ResultSetColumn> nullColumns = new ArrayList<>();
            nullColumns.add(new ResultSetColumn(procName, returnlessCallColumnType(procedure), null));
            final List<Row> nullRows = new ArrayList<>();
            nullRows.add(new Row((Object) null));
            return new ResultSet(nullColumns, nullRows);
        }
        final Object returnValue;
        // Whatever language the body is in, one that runs with owner's rights has its refusals worded for the
        // owner, still naming the session's primary role (live-verified); visitCallStatement puts the caller's back.
        SessionRole.enterProcedure(!"CALLER".equals(procedure.getExecuteAs()));
        // Snowflake's scoped-transaction rule (live-verified): a transaction STARTED inside a stored
        // procedure must be completed inside it — returning with it open rolls it back and errors.
        final boolean txnOpenBeforeCall = queryExecutor.getTransactionManager().isExplicitTransaction();

        if (language == UdfLanguage.JAVASCRIPT || language == UdfLanguage.PYTHON
                || language == UdfLanguage.SCALA) {
            returnValue = UdfRuntimes.require(language)
                .executeProcedure(procedure, arguments, queryExecutor.getDatabaseEngine());
            rejectOpenScopedTransaction(txnOpenBeforeCall);
        } else if (language == UdfLanguage.JAVA) {
            returnValue = JavaProcedureExecutor
                .executeJavaProcedure(procedure, arguments, queryExecutor.getDatabaseEngine());
            rejectOpenScopedTransaction(txnOpenBeforeCall);
        } else {
            // SQL procedural language: bind the call arguments to the parameter names, execute the body
            // on the shared procedural executor (so its RETURN / control flow / statements actually
            // run — not merely logged), and surface the RETURN value. Parameters are declared in a
            // dedicated scope so they don't leak past the CALL.
            //
            // Run the body with the procedure's home database/schema as the current context, so an
            // unqualified/partially-qualified reference inside (e.g. UTILS.IS_VALID_TIER, or a bare table)
            // resolves relative to where the procedure lives — matching Snowflake — rather than against the
            // caller's current database. Restored in the finally.
            final String savedDb = catalog.getCurrentDatabase();
            final String savedSchema = catalog.getCurrentSchema();
            final String homeDb = parts.length == 3 ? parts[0] : savedDb;
            final String homeSchema = parts.length == 3 ? parts[1]
                : parts.length == 2 ? parts[0] : savedSchema;
            // The body runs under its declared RETURNS type (none for a table-returning one), so a
            // RETURN written directly in it converts — see the executor's rule.
            final Set<String> parameterNames = new HashSet<>();
            for (final Parameter param : params) {
                parameterNames.add(param.getName());
            }
            final DeclaredReturnFrame outerReturn = proceduralExecutor.pushDeclaredReturn(
                procedure.returnsTable() ? null : procedure.getReturnType(), procedure.returnsTable(), parameterNames);
            proceduralExecutor.enterScope();
            try {
                if (homeDb != null) {
                    catalog.useDatabase(homeDb);
                    if (homeSchema != null) {
                        catalog.useSchema(homeSchema);
                    }
                }
                for (int i = 0; i < params.size(); i++) {
                    proceduralExecutor.markDeclaredInCurrentScope(params.get(i).getName());
                    proceduralExecutor.declareVariableType(params.get(i).getName(),
                        params.get(i).getDataType());
                    proceduralExecutor.setVariable(params.get(i).getName(),
                        proceduralExecutor.coerceArgument(arguments.get(i), params.get(i).getDataType()));
                }

                final String body = procedure.getBody();
                final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
                final SyntaxErrorListener errorListener = new SyntaxErrorListener(body);
                lexer.removeErrorListeners();
                lexer.addErrorListener(errorListener);
                final CommonTokenStream tokens = new CommonTokenStream(lexer);
                final FrostlakeParser parser = new FrostlakeParser(tokens);
                parser.removeErrorListeners();
                parser.addErrorListener(errorListener);
                final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
                errorListener.throwIfErrors();
                StageArgumentSyntax.requirePlacement(tree, body);
                NamedCallRewrite.apply(tree);

                Object bodyResult = null;
                // Only an OWNER's-rights procedure may not touch the session it runs in; an
                // EXECUTE AS CALLER body changes session state freely (live-verified), so the
                // session-statement refusal is armed for owner's-rights bodies alone.
                final boolean ownersRights = !"CALLER".equals(procedure.getExecuteAs());
                if (ownersRights) {
                    procedureDepth++;
                }
                try {
                for (final FrostlakeParser.StatementContext stmtCtx : flattenedStatements(tree)) {
                    StatementClock.advance();
                    final Object visited = visit(stmtCtx);
                    // A statement's own result is never the call's — `$$ SELECT 1; $$` and
                    // `$$ EXECUTE IMMEDIATE 'SELECT 1'; $$` are created and CALL answers NULL
                    // (live-verified) — unless the statement is a BLOCK that RETURNED: that result is
                    // the call's, and it ENDS the body, so a statement written after the block never
                    // runs, as it never runs on the account.
                    final boolean block = stmtCtx.proceduralStatement() != null
                        && stmtCtx.proceduralStatement().beginEndBlock() != null;
                    bodyResult = block ? visited : null;
                    // Per-statement autocommit, as inside BEGIN…END bodies (Snowflake procedures do
                    // not wrap their statements in one transaction).
                    queryExecutor.getTransactionManager().autocommitStatementEnd();
                    if (proceduralExecutor.hasReturned() || (block && visited instanceof ResultSet)) {
                        break; // a RETURN — bare, or surfaced by a block — stops the body
                    }
                }
                } finally {
                    if (ownersRights) {
                        procedureDepth--;
                    }
                }
                rejectOpenScopedTransaction(txnOpenBeforeCall);
                // A BEGIN…END body surfaces its RETURN as a single-row ResultSet (bodyResult, with the
                // return state already cleared by visitBeginEndBlock); a bare-statement body leaves the
                // value in the return state. Handle both, and clear the state so it can't leak.
                if (bodyResult instanceof ResultSet && procedure.returnsTable()) {
                    // The table a RETURNS TABLE procedure's RETURN TABLE handed back, under its declared columns.
                    return ReturnedTable.shape(procedure.getReturnColumns(), (ResultSet) bodyResult);
                }
                if (bodyResult instanceof ResultSet) {
                    // Snowflake names a CALL's result column after the procedure; the BEGIN…END block that
                    // produced this ResultSet used a generic name, so rename its single result column.
                    final ResultSet bodyRs = (ResultSet) bodyResult;
                    if (bodyRs.getColumns().size() == 1) {
                        final List<ResultSetColumn> renamed = new ArrayList<>();
                        renamed.add(new ResultSetColumn(procName, callResultColumnType(), null));
                        return new ResultSet(renamed, bodyRs.getRows());
                    }
                    return bodyRs;
                }
                if (proceduralExecutor.hasReturned()) {
                    final Object rv = proceduralExecutor.getReturnValue();
                    proceduralExecutor.clearReturnState();
                    if (rv instanceof ResultSet) {
                        // RETURN TABLE(…): the returned table IS the CALL's result. Reached when the
                        // callee runs NESTED inside another procedure — the block handler then leaves
                        // the return state set (blockDepth > 1) instead of converting it, and wrapping
                        // the ResultSet as a single scalar cell would collapse the table.
                        return procedure.returnsTable()
                            ? ReturnedTable.shape(procedure.getReturnColumns(), (ResultSet) rv) : rv;
                    }
                    final List<ResultSetColumn> resultColumns = new ArrayList<>();
                    // Snowflake names a CALL's result column after the procedure (e.g. CALL foo() → "FOO").
                    resultColumns.add(new ResultSetColumn(procName, callResultColumnType(), null));
                    final List<Row> resultRows = new ArrayList<>();
                    resultRows.add(new Row(rv));
                    return new ResultSet(resultColumns, resultRows);
                }
                // A body that completes WITHOUT a RETURN implicitly returns NULL — and CALL still
                // yields its one-row result set (column named after the procedure), never an empty
                // result: JDBC executeQuery("CALL p()") must see a result set even for
                // `BEGIN <dml>; END` bodies whose only RETURN sits in the EXCEPTION handler.
                // No RETURN ran, so the NULL takes the declared RETURNS type, whatever an earlier RETURN
                // typed. A procedure that returns a table has no such NULL: once its body has run, it is
                // refused (live-verified).
                if (procedure.returnsTable()) {
                    throw new RuntimeException(ReturnedTable.MISSING_RETURN);
                }
                final List<ResultSetColumn> implicitNullColumns = new ArrayList<>();
                implicitNullColumns.add(new ResultSetColumn(procName, returnlessCallColumnType(procedure), null));
                final List<Row> implicitNullRows = new ArrayList<>();
                implicitNullRows.add(new Row((Object) null));
                return new ResultSet(implicitNullColumns, implicitNullRows);
            } catch (final ProceduralException e) {
                throw e;
            } catch (final Exception e) {
                throw StatementErrors.propagate(e);
            } finally {
                proceduralExecutor.restoreDeclaredReturn(outerReturn);
                proceduralExecutor.exitScope();
                if (savedDb != null) {
                    // Restore the caller's context WITHOUT re-validating it: this runs while unwinding, so a
                    // throw here would replace the CALL's real result (or its real error) with a spurious one
                    // that the procedure's own EXCEPTION handler never sees.
                    catalog.restoreContext(savedDb, savedSchema);
                }
            }
        }

        logger.trace("Executed CALL statement for: {}", procName);

        if (returnValue != null) {
            final List<ResultSetColumn> columns = new ArrayList<>();
            // Snowflake names a CALL's result column after the procedure (e.g. CALL foo() → "FOO").
            columns.add(new ResultSetColumn(procName, StringType.VARCHAR, null));
            final List<Row> rows = new ArrayList<>();
            rows.add(new Row(returnValue));
            return new ResultSet(columns, rows);
        }

        return null;
    }

    @Override
    public Object visitExecuteImmediateStatement(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        try {
            String sqlString;
            if (ctx.FROM() != null) {
                // EXECUTE IMMEDIATE FROM @stage/file | '<location>' — run the SQL script in that file.
                sqlString = readImmediateScript(ctx);
            } else {
                if (!proceduralExecutor.isExecutingBlock()) {
                    rejectTopLevelExecuteImmediateForms(ctx);
                }
                // Evaluate the expression using the CURRENT executor (needed for :variable references)
                final Object sqlObj = evaluateExpression(ctx.expression());

                if (sqlObj == null) {
                    throw new RuntimeException("EXECUTE IMMEDIATE: SQL expression evaluated to null");
                }

                sqlString = sqlObj.toString();

                // Bind USING (...) values positionally to the ? placeholders (Snowflake style); a FOR loop's
                // record binds nothing, and its placeholder stays unset.
                if (!ctx.usingArgument().isEmpty()) {
                    final List<Object> bindValues = new ArrayList<>();
                    final Set<Integer> unbound = new HashSet<>();
                    for (final FrostlakeParser.UsingArgumentContext argument : ctx.usingArgument()) {
                        final String name = usingVariableName(argument);
                        if (!proceduralExecutor.hasVariable(name) && proceduralExecutor.isLoopRecord(name)) {
                            unbound.add(bindValues.size() + 1);
                        }
                        bindValues.add(usingValue(argument));
                    }
                    sqlString = JdbcMarshaling.substitutePlaceholders(sqlString, bindValues, unbound);
                }
            }

            // Parse and execute the dynamic SQL
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sqlString));

            final SyntaxErrorListener errorListener = new SyntaxErrorListener(sqlString);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            final FrostlakeParser.SqlScriptContext sqlScriptCtx = parser.sqlScript();
            ScriptingStatementPlacement.requireSqlStatementsBefore(sqlScriptCtx, tokens, sqlString, errorListener);
            IntoListLookahead.requireBefore(sqlScriptCtx, tokens, sqlString, errorListener);
            EmptySchemaPartSyntax.requireWellFormedBefore(sqlScriptCtx, tokens, sqlString, errorListener);
            PostParseRefusals.requireBefore(tokens, sqlString, errorListener);
            SignatureTailLine.requireBefore(sqlScriptCtx, sqlString, errorListener);
            errorListener.throwIfErrors();
            EmptySchemaPartSyntax.requireWellFormed(sqlScriptCtx, tokens, sqlString);
            FinishedPredicateSyntax.requireOpenOperands(sqlScriptCtx, tokens, sqlString);
            PositionNeedleSyntax.requireValueNeedles(sqlScriptCtx, tokens, sqlString);
            AnsiFromFormSyntax.requireCallForms(sqlScriptCtx, tokens, sqlString);
            ValueFunctionSyntax.requireValueFunctionForms(sqlScriptCtx, sqlString);
            StageArgumentSyntax.requireTextSyntax(sqlScriptCtx, sqlString);
            // Once it parses, the text is counted before any of it runs — against the request's count, or the
            // session's inside a block; a script read FROM a stage is not counted (see DynamicStatementCount).
            // What a statement earns while it compiles — a value operator after a quantified comparison, an
            // interval literal that does not read, a bare stage passed to a call that does not take it — comes
            // after the count, and in a text of several statements when that statement runs (see the loop below).
            final boolean severalStatements = ctx.FROM() == null && sqlScriptCtx.flowChain().size() > 1;
            if (ctx.FROM() == null) {
                DynamicStatementCount.require(sqlScriptCtx.flowChain().size(), queryExecutor.getSecurityManager(),
                    proceduralExecutor.isExecutingBlock());
            }
            if (!severalStatements) {
                StageArgumentSyntax.requirePlacement(sqlScriptCtx, sqlString);
                QuantifiedComparisonOperand.requireNoValueOperator(sqlScriptCtx);
                IntervalLiteralSyntax.requireReadable(sqlScriptCtx);
                NamedCallRewrite.apply(sqlScriptCtx);
            }

            // If the inner SQL is a procedural block (BEGIN...END or DECLARE...), clear cursors/exceptions
            // from any prior invocation so they don't cause 'already declared' errors on re-execution.
            // Variables are scoped naturally by BEGIN...END, so only transient state needs clearing.
            // Determined from the parse tree, not a string prefix.
            boolean isProcedural = false;
            for (final FrostlakeParser.StatementContext s : flattenedStatements(sqlScriptCtx)) {
                final FrostlakeParser.ProceduralStatementContext p = s.proceduralStatement();
                if (p != null && p.beginEndBlock() != null) {
                    isProcedural = true;
                    break;
                }
            }
            // Only when this EXECUTE IMMEDIATE is NOT itself running inside a BEGIN…END block: the clear
            // exists so a dynamic script can re-declare a cursor/exception left over from a PREVIOUS
            // top-level statement, but a nested one would wipe the ENCLOSING block's live state — a
            // procedure that declares an exception, calls a loader that EXECUTE IMMEDIATEs a procedural
            // body, and then raises that exception failed with "Undefined exception: <name>".
            if (isProcedural && !proceduralExecutor.isExecutingBlock()) {
                proceduralExecutor.clearCursorsAndExceptions();
            }

            // The dynamic text is its OWN execution frame: a block inside it is not nested in whatever
            // block asked for it, so its RETURN ends that block and becomes this statement's value
            // instead of ending the caller — which is what lets a RESULTSET filled from an EXECUTE
            // IMMEDIATE of a block hold the block's answer.
            Object lastResult = null;
            FrostlakeParser.StatementContext lastStatement = null;
            final List<FrostlakeParser.StatementContext> dynamicStatements = flattenedStatements(sqlScriptCtx);
            // A text of several statements hands each statement's answer on (see MultiStatementAnswer).
            final List<ResultSet> statementAnswers = new ArrayList<>();
            final int enclosingFrame = proceduralExecutor.enterDynamicFrame();
            try {
                for (final FrostlakeParser.StatementContext stmtCtx : dynamicStatements) {
                    // Only the last statement's conditional branch may reach the status sentence.
                    ConditionalDdlOutcome.clear();
                    // A statement of a text of several reports its places against its own text, and fails named
                    // the way a multi-statement request names it.
                    final SourcePosition displaced = severalStatements ? LeadingCommentOffset.begin(new SourcePosition(
                        stmtCtx.getStart().getLine(), stmtCtx.getStart().getCharPositionInLine())) : null;
                    try {
                        if (severalStatements) {
                            // A statement of several compiles when it runs, after the ones before it have run, and
                            // fails named for what it earns there (live-verified).
                            StageArgumentSyntax.requireArguments(stmtCtx);
                            QuantifiedComparisonOperand.requireNoValueOperator(stmtCtx);
                            IntervalLiteralSyntax.requireReadable(stmtCtx);
                            NamedCallRewrite.apply(stmtCtx);
                        }
                        LimitValueSyntax.requireNumericValuesAsWritten(stmtCtx);
                        IntoClausePlacement.rejectInStatement(stmtCtx, false);
                        lastResult = visit(stmtCtx);
                    } catch (final RuntimeException failed) {
                        throw severalStatements ? MultiStatementFailure.wrapping(sqlScriptCtx, stmtCtx, failed, true)
                            : failed;
                    } finally {
                        if (severalStatements) {
                            LeadingCommentOffset.end(displaced);
                        }
                    }
                    if (severalStatements) {
                        final ResultSet answer = lastResult instanceof ResultSet ? (ResultSet) lastResult
                            : AnonymousBlockResult.isBlock(stmtCtx) ? AnonymousBlockResult.withoutReturn()
                            : queryExecutor.statusAnswer(stmtCtx);
                        statementAnswers.add(answer.markJdbcUpdateCount(JdbcUpdateCounts.of(stmtCtx, answer)));
                    }
                    lastStatement = stmtCtx;
                    // Cache result sets so RESULT_SCAN(LAST_QUERY_ID()) works after SHOW / SELECT statements
                    if (lastResult instanceof ResultSet) {
                        queryExecutor.getResultCache().cacheResult(sqlString, (ResultSet) lastResult);
                    }
                }
                // An EXECUTE IMMEDIATE of an anonymous block answers what the block answers run
                // directly: one row, NULL when the block finished without a RETURN. Asked INSIDE the
                // frame, so the caller's own block does not read as this text's.
                if (lastResult == null && !proceduralExecutor.isExecutingOwnBlock()
                        && AnonymousBlockResult.isBlock(lastStatement)) {
                    lastResult = AnonymousBlockResult.withoutReturn();
                }
            } finally {
                proceduralExecutor.exitDynamicFrame(enclosingFrame);
            }
            // A script file answers with its last statement's result, however many it holds; dynamic
            // text of several statements answers as a multi-statement request instead.
            dynamicStatement = ctx.FROM() != null || dynamicStatements.size() == 1
                ? describedStatement(lastStatement) : null;

            logger.trace("Executed EXECUTE IMMEDIATE: {}", sqlString.length() > 50 ? sqlString.substring(0, 50) + "..." : sqlString);
            return severalStatements ? new MultiStatementAnswer(statementAnswers) : lastResult;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        }
    }

    /**
     * The value an EXECUTE IMMEDIATE's USING argument binds: the variable it names. The block judged every name while
     * it compiled; a name it let through that holds no value — SQLCODE outside a handler, SQLROWCOUNT before any DML
     * statement — binds NULL, and a loop's record binds nothing, its placeholder left unset.
     */
    private Object usingValue(final FrostlakeParser.UsingArgumentContext argument) {
        final String name = usingVariableName(argument);
        return proceduralExecutor.hasVariable(name) ? proceduralExecutor.getVariable(name) : null;
    }

    /** The variable a USING argument names, as the scope keys it. */
    static String usingVariableName(final FrostlakeParser.UsingArgumentContext argument) {
        return argument.identifier() != null ? SqlIdentifiers.canonical(argument.identifier())
            : argument.getText().toUpperCase(Locale.ROOT);
    }

    /** A statement written in parentheses as an assignment's value, placed at its first word. */
    StatementValueExpression statementValue(final FrostlakeParser.ResultSetStatementContext statement) {
        final StatementValueExpression value = new StatementValueExpression(getOriginalText(statement));
        value.setSourcePosition(statement.getStart().getLine(), statement.getStart().getCharPositionInLine());
        return value;
    }

    /** The USING arguments of an EXECUTE IMMEDIATE, as the variable references a deferred run evaluates. */
    List<BaseExpression> usingBindings(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        final List<BaseExpression> bindings = new ArrayList<>();
        for (final FrostlakeParser.UsingArgumentContext argument : ctx.usingArgument()) {
            bindings.add(new VariableExpression(usingVariableName(argument)));
        }
        return bindings;
    }

    /**
     * Session-level EXECUTE IMMEDIATE is far narrower than the in-block form (live-verified):
     * <ul>
     *   <li>its source must be a string literal, a {@code $$…$$} literal or a session variable —
     *       {@code EXECUTE IMMEDIATE 'SELECT ' || '1'} and {@code EXECUTE IMMEDIATE my_sql} are both
     *       syntax errors, while {@code $my_sql} is fine;</li>
     *   <li>a {@code USING} clause is refused outright with "Unsupported statement type 'EXECUTE'".</li>
     * </ul>
     * Both forms stay legal inside a {@code BEGIN…END} block ({@code LET s := 'SELECT ' || '1';
     * EXECUTE IMMEDIATE s;} and {@code res := (EXECUTE IMMEDIATE 'SELECT ?' USING (v))} run there), so
     * this is a top-level-only guard rather than a change to the shared {@code executeImmediateStatement}
     * grammar rule that the procedural and task-body paths also use.
     */
    private void rejectTopLevelExecuteImmediateForms(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        if (ctx.USING() != null) {
            // A list of names parses and is then refused (measured verbatim); any other argument is the
            // syntax error the grammar reports at it.
            throw new RuntimeException("SQL compilation error:\nUnsupported statement type 'EXECUTE'.");
        }
        final FrostlakeParser.ExpressionContext source = ctx.expression();
        if (source instanceof FrostlakeParser.SessionVarExprContext) {
            return;
        }
        if (source instanceof FrostlakeParser.LiteralExprContext) {
            final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) source).literal();
            if (literal.STRING_LITERAL() != null || literal.DOLLAR_QUOTED_STRING() != null) {
                return;
            }
        }
        throw new RuntimeException("SQL compilation error:\nsyntax error line 1 at position 0 unexpected '"
            + unexpectedExecuteImmediateToken(source) + "'.");
    }

    /**
     * The token Snowflake names in its "unexpected" error for a rejected session-level EXECUTE IMMEDIATE
     * source: an operator root reports its operator ({@code 'SELECT ' || '1'} → {@code ||}), anything
     * else reports its own text (a bare identifier → that identifier).
     */
    private static String unexpectedExecuteImmediateToken(final FrostlakeParser.ExpressionContext source) {
        if (source instanceof FrostlakeParser.ConcatExprContext) {
            return ((FrostlakeParser.ConcatExprContext) source).PIPE_PIPE().getText();
        }
        if (source instanceof FrostlakeParser.AdditiveExprContext) {
            return ((FrostlakeParser.AdditiveExprContext) source).op.getText();
        }
        if (source instanceof FrostlakeParser.MultiplicativeExprContext) {
            return ((FrostlakeParser.MultiplicativeExprContext) source).op.getText();
        }
        return source.getText();
    }

    /** Read the SQL script referenced by {@code EXECUTE IMMEDIATE FROM} — a stage file (@stg/file) or a
     *  location string ('file://…'). */
    private String readImmediateScript(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        final String location = ctx.stageRef() != null ? queryExecutor.stageRefToLocation(ctx.stageRef())
            : unquoteStringLiteral(ctx.STRING_LITERAL().getText());
        final Path path = queryExecutor.resolveStageFilePath(location);
        if (path == null || !Files.isRegularFile(path)) {
            // A named stage's missing file is refused in the account's words; a directory is no file either.
            final String missing = queryExecutor.missingStageFileSentence(location);
            throw new RuntimeException(missing != null ? missing : "EXECUTE IMMEDIATE FROM: script file not found: "
                + (path == null ? null : StagePathSegments.namedPath(path)));
        }
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new RuntimeException("EXECUTE IMMEDIATE FROM: failed to read " + path + ": " + e.getMessage(), e);
        }
    }

    private static String unquoteStringLiteral(final String literal) {
        if (literal.length() >= 2 && literal.startsWith("'") && literal.endsWith("'")) {
            return literal.substring(1, literal.length() - 1);
        }
        return literal;
    }

    @Override
    public Object visitBeginEndBlock(final FrostlakeParser.BeginEndBlockContext ctx) {
        return beginEndBlockHandler.handle(ctx);
    }

    // ==================== HELPER METHODS ====================

    public String getText(final FrostlakeParser.IdentifierContext ctx) {
        return SqlIdentifiers.canonical(ctx);
    }

    /**
     * A loop LABEL is its own name position: it admits INNER, which a plain identifier may not (live
     * accepts {@code CREATE TABLE inner} yet refuses a bare {@code FROM inner}). The keyword form has
     * no quoting to strip, so it folds the same way an unquoted identifier does.
     *
     * @param ctx the loop-label parse tree — a plain identifier or the bare keyword form
     * @return the canonical label name: identifiers fold like any identifier, keywords uppercase
     */
    public String getText(final FrostlakeParser.LoopLabelContext ctx) {
        return ctx.identifier() != null
            ? SqlIdentifiers.canonical(ctx.identifier())
            : ctx.getText().toUpperCase(java.util.Locale.ROOT);
    }

    public String getText(final FrostlakeParser.QualifiedNameContext ctx) {
        return ParseTreeText.getQualifiedName(ctx);
    }

    /**
     * The ordered identifier parts of a qualified name (db, schema, name), read from the parse tree's
     * identifier list — the AST-definitive way — rather than by splitting its flattened text on '.'.
     * Correct even when an identifier is double-quoted and itself contains a dot (which {@code
     * getText(qn).split("\\.")} would mis-split).
     *
     * @param ctx the qualified-name parse tree
     * @return the canonical name parts in source order, one element per dotted level
     */
    public String[] qualifiedNameParts(final FrostlakeParser.QualifiedNameContext ctx) {
        return ParseTreeText.qualifiedNameParts(ctx);
    }

    public String extractStringLiteral(final TerminalNode node) {
        return SqlStringLiterals.decode(node.getText());
    }

    private String extractComment(final FrostlakeParser.CommentClauseContext ctx) {
        if (ctx == null || ctx.STRING_LITERAL() == null) {
            return null;
        }
        return extractStringLiteral(ctx.STRING_LITERAL());
    }

    /** The COLUMN-level comment clause — {@code COMMENT '<text>'}, no equals sign. */
    private String extractComment(final FrostlakeParser.ColumnCommentClauseContext ctx) {
        if (ctx == null || ctx.STRING_LITERAL() == null) {
            return null;
        }
        return extractStringLiteral(ctx.STRING_LITERAL());
    }

    private String extractBodyDefinition(final FrostlakeParser.BodyDefinitionContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            // Single-quoted string literal
            return extractStringLiteral(ctx.STRING_LITERAL());
        } else if (ctx.DOLLAR_QUOTED_STRING() != null) {
            // Dollar-quoted string ($$...$$)
            final String text = ctx.DOLLAR_QUOTED_STRING().getText();
            // Remove the $$ delimiters
            if (text.startsWith("$$") && text.endsWith("$$")) {
                return text.substring(2, text.length() - 2);
            }
            return text;
        } else {
            throw new RuntimeException("Invalid body definition");
        }
    }

    public String getOriginalText(final ParserRuleContext ctx) {
        // Get original text with whitespace preserved
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream().getText(
            new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex())
        );
    }

    private List<TableColumn> parseColumnList(final FrostlakeParser.ColumnListContext ctx) {
        final List<TableColumn> columns = new ArrayList<>();
        final List<String> tablePrimaryKeys = new ArrayList<>();

        // First pass: collect columns and table-level primary keys
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.columnDef() != null) {
                final FrostlakeParser.ColumnDefContext colDef = item.columnDef();
                final String colName = ParseTreeText.namePartText(colDef.columnDefName());
                final DataType dataType = parseDataType(colDef.dataTypeName(), colDef.typeParameters());

                boolean primaryKey = false;
                boolean notNull = false;
                boolean autoIncrement = false;
                Object defaultValue = null;

                for (final FrostlakeParser.ColumnConstraintContext constraint : colDef.columnConstraint()) {
                    if (constraint.PRIMARY() != null) {
                        primaryKey = true;
                    } else if (constraint.NOT() != null) {
                        notNull = true;
                    } else if (constraint.AUTOINCREMENT() != null) {
                        autoIncrement = true;
                    } else if (constraint.DEFAULT() != null) {
                        defaultValue = parseDefaultExpression(constraint.defaultExpression());
                    }
                }

                // Column constructor: name, dataType, nullable, defaultValue, primaryKey, unique, autoIncrement
                final TableColumn column = new TableColumn(colName, dataType, !notNull, defaultValue,
                                            primaryKey, false, autoIncrement);

                // Set comment if provided
                final String comment = extractComment(colDef.columnCommentClause());
                if (comment != null) {
                    column.setComment(comment);
                }

                columns.add(column);
            } else if (item.tableConstraint() != null) {
                final FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
                if (constraint.PRIMARY() != null) {
                    // Extract primary key column names
                    for (final FrostlakeParser.IdentifierContext id : constraint.identifierList(0).identifier()) {
                        tablePrimaryKeys.add(getText(id));
                    }
                }
            }
        }

        // Second pass: if table-level primary keys were specified, update the columns
        if (!tablePrimaryKeys.isEmpty()) {
            final List<TableColumn> updatedColumns = new ArrayList<>();
            for (final TableColumn col : columns) {
                boolean isPrimaryKey = false;
                for (final String pk : tablePrimaryKeys) {
                    if (pk.equalsIgnoreCase(col.getName())) {
                        isPrimaryKey = true;
                        break;
                    }
                }

                // Create new column with updated primary key status
                final TableColumn newCol = new TableColumn(
                    col.getName(),
                    col.getDataType(),
                    col.isNullable(),
                    col.getDefaultValue(),
                    isPrimaryKey || col.isPrimaryKey(), // Keep column-level PK or add table-level PK
                    col.isUnique(),
                    col.isAutoIncrement()
                );
                newCol.setComment(col.getComment());
                updatedColumns.add(newCol);
            }
            return updatedColumns;
        }

        return columns;
    }

    // Data-type / default / literal parsing delegates to the canonical ColumnDefinitionParser
    // (shared with DDL) so there is a single source of truth — the visitor previously carried a
    // divergent copy that dropped NUMBER precision, VARCHAR length, and TINYINT/UUID/VECTOR/DATETIME.
    /**
     * The type a SCRIPTING declaration writes. It differs from a column's in one place: an unsized
     * BINARY binds at the type's 64MB maximum, where a column's unsized BINARY is BINARY(8388608) —
     * so the width that was WRITTEN is what tells them apart, and an explicit BINARY(8388608) keeps
     * its own width (live-verified).
     *
     * @param name       the type as written
     * @param parameters its parameters, or null when none were written
     * @return the declared type
     */
    private DataType parseDeclaredType(final FrostlakeParser.DataTypeNameContext name,
                                       final FrostlakeParser.TypeParametersContext parameters) {
        final DataType declared = parseDataType(name, parameters);
        return parameters == null && declared instanceof BinaryType ? BinaryType.AT_MAXIMUM : declared;
    }

    private DataType parseDataType(final FrostlakeParser.DataTypeNameContext ctx) {
        return columnParser.parseDataType(ctx);
    }

    public DataType parseDataType(final FrostlakeParser.DataTypeNameContext ctx, final FrostlakeParser.TypeParametersContext typeParams) {
        return columnParser.parseDataType(ctx, typeParams);
    }

    private Object parseDefaultExpression(final FrostlakeParser.DefaultExpressionContext ctx) {
        return columnParser.parseDefaultExpression(ctx);
    }

    public Object parseLiteral(final FrostlakeParser.LiteralContext ctx) {
        return columnParser.parseLiteral(ctx);
    }

    public Schema resolveCurrentSchema() {
        if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
            throw NoCurrentDatabaseRefusal.forStatement();
        }
        return catalog.getDatabase(catalog.getCurrentDatabase())
                     .getSchema(catalog.getCurrentSchema());
    }

    private String resolveFullyQualifiedName(final String qualifiedName) {
        final String[] parts = catalog.withoutAccount(QualifiedName.parse(qualifiedName).parts(), 3);

        if (parts.length == 1) {
            // table name only - use current database and schema
            if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return QualifiedName.key(catalog.getCurrentDatabase(), catalog.getCurrentSchema(), parts[0]);
        } else if (parts.length == 2) {
            // schema.table - use current database
            if (catalog.getCurrentDatabase() == null) {
                throw NoCurrentDatabaseRefusal.forStatement();
            }
            return QualifiedName.key(catalog.getCurrentDatabase(), parts[0], parts[1]);
        } else if (parts.length == 3) {
            // database.schema.table - fully qualified
            return QualifiedName.key(parts[0], parts[1], parts[2]);
        } else {
            throw new RuntimeException("Invalid qualified name: " + qualifiedName);
        }
    }

    // Expression building / evaluation / SYSTEM$ functions are delegated to VisitorExpressionBuilder;
    // these forwarders keep the existing call sites (procedural visits, DDL/DML default + value eval) intact.
    BaseExpression buildExpression(final FrostlakeParser.BooleanExprContext ctx) {
        return expressionBuilder.buildExpression(ctx);
    }

    BaseExpression buildExpression(final FrostlakeParser.ExpressionContext ctx) {
        return expressionBuilder.buildExpression(ctx);
    }

    /** Scripting-expression evaluation: a bare name may resolve to a scripting variable. */
    private Object evaluateExpression(final FrostlakeParser.ExpressionContext ctx) {
        return expressionBuilder.evaluateExpression(ctx);
    }

    /** Scripting-value evaluation, a NOT, AND or OR included. */
    private Object evaluateExpression(final FrostlakeParser.BooleanExprContext ctx) {
        return expressionBuilder.evaluateExpression(ctx);
    }

    /**
     * Evaluation inside an EMBEDDED SQL statement: a bare name is an identifier, never a
     * stored-procedure parameter / DECLAREd / LET variable (Snowflake requires {@code :name} there).
     */
    private Object evaluateSqlExpression(final FrostlakeParser.ExpressionContext ctx) {
        return expressionBuilder.evaluateSqlExpression(ctx);
    }

    /** SQL-value evaluation, a NOT, AND or OR included. */
    private Object evaluateSqlExpression(final FrostlakeParser.BooleanExprContext ctx) {
        return expressionBuilder.evaluateSqlExpression(ctx);
    }

    public ProceduralExecutor getProceduralExecutor() {
        return proceduralExecutor;
    }

    /**
     * Execute a {@code LANGUAGE SQL} scalar UDF whose body is a Snowflake-Scripting block and yield the
     * value its RETURN produced — live-verified: {@code CREATE FUNCTION f() RETURNS INT AS $$
     * BEGIN RETURN 1; END $$} then {@code SELECT f()} is 1, and the block really runs (a body of
     * {@code BEGIN RETURN 1; RETURN 2; END} yields 1, {@code BEGIN LET x INT := 5; RETURN x * 2; END}
     * yields 10, and a body with no RETURN at all yields NULL).
     *
     * <p>Shares every mechanism with the stored-procedure CALL path above — bind the arguments as declared
     * variables, parse the body with the engine's own lexer/parser, visit its statements, read the RETURN —
     * but differs in the two ways a FUNCTION differs from a PROCEDURE:
     * <ul>
     *   <li>the scope is ISOLATED, because a function is its own execution context: it cannot see a calling
     *       procedure's variables, and its own must not touch them (live-verified both ways);</li>
     *   <li>the shared return state is saved and put back, because this body runs in the MIDDLE of
     *       evaluating an enclosing block's expression — consuming its own RETURN must not look to that
     *       block like a RETURN of its own.</li>
     * </ul>
     *
     * @param function the SQL UDF whose body is the Snowflake-Scripting block to run
     * @param args the already-evaluated argument values, bound positionally to the declared parameters
     * @return the value the body's RETURN produced, or null when the body never RETURNs
     */
    public Object executeScriptingFunctionBody(final Function function, final List<Object> args) {
        final boolean callerReturned = proceduralExecutor.hasReturned();
        final Object callerReturnValue = proceduralExecutor.getReturnValue();
        proceduralExecutor.clearReturnState();
        proceduralExecutor.enterIsolatedScope();
        // The body runs in the MIDDLE of the statement that called the function, so its block's
        // per-statement autocommit must not commit that statement's write set — the same guard a
        // procedure called from a FROM clause needs. A UDF cannot run DML at all (the CREATE-time check
        // refuses it), so this only ever protects the CALLER's transaction.
        queryExecutor.getTransactionManager().beginAtomicSection();
        try {
            final List<Parameter> params = function.getParameters();
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                proceduralExecutor.markDeclaredInCurrentScope(params.get(i).getName());
                proceduralExecutor.declareVariableType(params.get(i).getName(),
                    params.get(i).getDataType());
                proceduralExecutor.setVariable(params.get(i).getName(), args.get(i));
            }

            final String body = function.getBody();
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
            final SyntaxErrorListener errorListener = new SyntaxErrorListener(body);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
            errorListener.throwIfErrors();
            StageArgumentSyntax.requirePlacement(tree, body);
            NamedCallRewrite.apply(tree);

            Object bodyResult = null;
            for (final FrostlakeParser.StatementContext stmtCtx : flattenedStatements(tree)) {
                bodyResult = visit(stmtCtx);
                if (proceduralExecutor.hasReturned()) {
                    break;
                }
            }
            // The body's BEGIN…END surfaces its RETURN either as the single-row ResultSet the outermost
            // block builds, or — when this UDF was called from inside another block, which makes the
            // body's block a NESTED one — by leaving the value in the return state. Both happen.
            if (proceduralExecutor.hasReturned()) {
                return proceduralExecutor.getReturnValue();
            }
            if (bodyResult instanceof ResultSet) {
                final ResultSet bodyRs = (ResultSet) bodyResult;
                return bodyRs.getRowCount() > 0 ? bodyRs.getRows().get(0).getValue(0) : null;
            }
            return null;   // a body that never RETURNs yields NULL (live-verified)
        } finally {
            queryExecutor.getTransactionManager().endAtomicSection();
            proceduralExecutor.exitScope();
            proceduralExecutor.restoreReturnState(callerReturned, callerReturnValue);
        }
    }

    /**
     * The statement whose status sentence answers for {@code statement}: the statement itself, or for an
     * EXECUTE IMMEDIATE the statement its text ran (live-verified): {@code EXECUTE IMMEDIATE 'CREATE TABLE t
     * (a INT)'} answers "Table T successfully created.", as does a RESULTSET filled from it, and an EXECUTE
     * IMMEDIATE FROM a script file answers for the file's last statement. Null when the text held several
     * statements.
     *
     * @param statement the statement that ran, or null
     * @return the statement its answer describes, or null
     */
    public FrostlakeParser.StatementContext describedStatement(final FrostlakeParser.StatementContext statement) {
        if (statement == null || statement.proceduralStatement() == null
                || statement.proceduralStatement().executeImmediateStatement() == null) {
            return statement;
        }
        return dynamicStatement;
    }

    /** All statements of a parsed script in order, chains flattened (a ->> chain contributes each
     *  of its stages; $n references need the executor's chain loop and are not resolved here). */
    private static List<FrostlakeParser.StatementContext> flattenedStatements(
            final FrostlakeParser.SqlScriptContext script) {
        final List<FrostlakeParser.StatementContext> statements = new ArrayList<>();
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            statements.addAll(chain.statement());
        }
        return statements;
    }
}
