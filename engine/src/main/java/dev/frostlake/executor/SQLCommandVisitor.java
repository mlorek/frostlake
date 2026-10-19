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

import com.snowflake.snowpark_java.DataFrame;
import dev.frostlake.DatabaseEngine;
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
import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.executor.commands.DynamicTableProperties;
import dev.frostlake.executor.commands.ExternalVolumeCommandHandler;
import dev.frostlake.executor.commands.GrantRevokeHandler;
import dev.frostlake.executor.commands.IntegrationCommandHandler;
import dev.frostlake.executor.commands.LoopControlPlacement;
import dev.frostlake.executor.commands.QueryCommandHandler;
import dev.frostlake.executor.commands.SecurityObjectCommandHandler;
import dev.frostlake.executor.commands.ShowCommandHandler;
import dev.frostlake.executor.commands.TagValues;
import dev.frostlake.executor.expressions.CaretOperator;
import dev.frostlake.executor.expressions.InformationSchemaCalls;
import dev.frostlake.executor.expressions.IntervalLiteralSyntax;
import dev.frostlake.executor.expressions.NamedCallRewrite;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.CalledProcedureFailure;
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
import dev.frostlake.executor.udf.HandlerResults;
import dev.frostlake.executor.udf.JavaProcedureExecutor;
import dev.frostlake.executor.udf.UdfRuntimes;
import dev.frostlake.functions.SystemFunctionArity;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.NoCurrentDatabaseRefusal;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.AnsiFromFormSyntax;
import dev.frostlake.parser.BlockCallStatementReading;
import dev.frostlake.parser.ColdPrediction;
import dev.frostlake.parser.EmptySchemaPartSyntax;
import dev.frostlake.parser.FinishedPredicateSyntax;
import dev.frostlake.parser.FrostlakeBaseVisitor;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.IntoListLookahead;
import dev.frostlake.parser.LimitValueSyntax;
import dev.frostlake.parser.PositionNeedleSyntax;
import dev.frostlake.parser.PostParseRefusals;
import dev.frostlake.parser.RoutineArgumentOrder;
import dev.frostlake.parser.ScriptingStatementPlacement;
import dev.frostlake.parser.SignatureTailLine;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.parser.StageArgumentSyntax;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.parser.ValueFunctionSyntax;
import dev.frostlake.persistence.CheckpointDebt;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.transaction.SetAsideTransaction;
import dev.frostlake.transaction.TableLockWait;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionAbortedMessages;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;

/**
 * Visitor that executes SQL commands by visiting the ANTLR parse tree.
 * Delegates to specialized command handlers for different SQL command types.
 */
public class SQLCommandVisitor extends FrostlakeBaseVisitor<Object> {

    private static final Logger logger = LoggerFactory.getLogger(SQLCommandVisitor.class);

    /** A TIME's text as a SELECT … INTO carries it, every fractional digit kept. */
    private static final DateTimeFormatter CARRIED_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS");

    /** A TIMESTAMP_NTZ's text as a SELECT … INTO carries it. */
    private static final DateTimeFormatter CARRIED_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");

    /** A refusal of the number, the names or the types of a call's arguments that names a place: the place, then the sentence. */
    private static final Pattern PLACED_COUNT = Pattern.compile(
        "^(SQL compilation error: error line \\d+ at position -?\\d+\\n)"
            + "(?:(?:not enough|too many) arguments for function |function \\S+ does not support named arguments$"
            + "|Invalid argument types for function )");

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
            CaretOperator.rejectInStatement(ctx);
            FromClauseProcedureJobs.require(ctx, procedureDepth > 0);
            ReadOnlyRequest.require(ctx);
            if (catalog != null && catalog.getCurrentDatabase() == null) {
                rejectWithoutCurrentDatabase(ctx);
            }
            TagValues.requireAll(ctx, queryExecutor);
            rejectUnknownTagReferences(ctx);
            rejectUnsetSessionVariables(ctx);
            SideEffectFunctions.rejectInCreate(ctx);
            rejectWhileTransactionAborted(ctx);
            CompiledTransactionAborts.carryOut(ctx, queryExecutor);
            return visitChildren(ctx);
        } finally {
            NoCurrentDatabaseRefusal.exit(enclosingKind);
        }
    }

    /**
     * A session whose transaction SYSTEM$ABORT_TRANSACTION ended runs nothing until it acknowledges that with a
     * COMMIT or a ROLLBACK (live-verified): every other statement — a query of constants, a SHOW, a SET, a BEGIN,
     * a block, a statement of the block that ran the abort — is refused with one sentence, after what the
     * statement fails to compile for. The running block itself goes on: its LET, IF, loops and RETURN are no
     * statements of the session's, and neither is what the engine composes for itself to evaluate an expression.
     */
    private void rejectWhileTransactionAborted(final FrostlakeParser.StatementContext ctx) {
        if (queryExecutor.getTransactionManager().getAbortedTransaction() == null || queryExecutor.isUnrecorded()) {
            return;
        }
        final FrostlakeParser.TransactionStatementContext transaction = ctx.transactionStatement();
        if (transaction != null && (transaction.COMMIT() != null || transaction.ROLLBACK() != null)) {
            return;
        }
        if (proceduralExecutor.isExecutingBlock() && isBlockControl(ctx.proceduralStatement())) {
            return;
        }
        if (ctx.queryStatement() != null) {
            // What a query fails to compile for is refused before the aborted transaction is (live-verified).
            ExplainPlan.compileOnly(queryExecutor,
                ParseTreeText.getOriginalText(ctx.queryStatement().selectStatement()));
        }
        throw new RuntimeException(TransactionAbortedMessages.IGNORED);
    }

    /** Whether a statement of a block is the block's own control flow, which runs nothing in the session. */
    private static boolean isBlockControl(final FrostlakeParser.ProceduralStatementContext statement) {
        return statement != null && (statement.beginEndBlock() != null || statement.letStatement() != null
            || statement.assignmentStatement() != null || statement.ifStatement() != null
            || statement.caseStatement() != null || statement.loopStatement() != null
            || statement.whileStatement() != null || statement.repeatStatement() != null
            || statement.forStatement() != null || statement.returnStatement() != null
            || statement.breakStatement() != null || statement.continueStatement() != null
            || statement.raiseStatement() != null || statement.fetchStatement() != null
            || statement.closeStatement() != null || statement.nullStatement() != null);
    }

    /**
     * A {@code $name} reference to a session variable no SET defined is refused while the statement
     * compiles, before anything runs and whatever clause it sits in — the select list, a WHERE that can
     * never be true, an UPDATE's SET, an INSERT's source, a CTAS, a view body — at the variable's own
     * position, naming it upper-cased: "Session variable '$NOSUCH' does not exist" (live-verified). A
     * variable SET to NULL exists and reads NULL; a session PARAMETER of the same name is not a variable.
     * Checked once per statement, so a SET earlier in the same script counts. A DESCRIBE's properties are judged
     * one by one in the order written, each name before its value (DescribeProperties), so theirs wait; so does
     * an ALTER SESSION's value, judged with the rest of its list (SessionParameterSettings).
     */
    private void rejectUnsetSessionVariables(final ParseTree node) {
        if (node instanceof FrostlakeParser.DescribePropertyContext
                || node instanceof FrostlakeParser.SessionValueContext) {
            return;
        }
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            if (token.getType() == FrostlakeParser.SESSION_VAR_REF) {
                final String name = token.getText().substring(1).toUpperCase(Locale.ROOT);
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
                if (catalog != null && !catalog.hasTagExact(tagName)) {
                    throw new RuntimeException(SqlCompilationError.doesNotExistAsSpelled("Tag", tagName.toUpperCase(Locale.ROOT)));
                }
                if (catalog != null) {
                    // ALLOWED_VALUES is judged before the object is created, as live does.
                    TagValues.requireAllowed(catalog.getTagExact(tagName),
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
            return new ContainerServicesHandler(catalog, queryExecutor.getEngineConfig(), queryExecutor).handle(ctx);
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
            if (ctx.objectName().qualifiedName() != null) {
                InformationSchemaGuard.refuseWrite(catalog,
                    ParseTreeText.qualifiedNameParts(ctx.objectName().qualifiedName()));
            }

            // Execute truncate via query executor; one that meets a table another session's transaction holds
            // waits for it and runs again (see TableLockGate).
            long waitingSince = 0L;
            while (true) {
                try {
                    queryExecutor.executeTruncate(tableName, ifExists);
                    return null;
                } catch (final TableLockWait held) {
                    waitingSince = waitingSince == 0L ? System.nanoTime() : waitingSince;
                    queryExecutor.awaitTableLock(held, waitingSince);
                }
            }

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
            // A BEGIN in a block or a procedure called while its caller's transaction is open starts a transaction
            // of the block's own beside it; a second BEGIN in the scope that began one changes nothing.
            final TransactionManager transactions = queryExecutor.getTransactionManager();
            final Transaction openBefore = transactions.isExplicitTransaction()
                ? transactions.getCurrentTransaction() : null;
            transactions.beginExplicitTransaction(transactionName(ctx));
            if (transactions.getCurrentTransaction() != openBefore && proceduralExecutor.isExecutingBlock()) {
                // The block's own: see Transaction#isBegunInBlock.
                transactions.getCurrentTransaction().setBegunInBlock(true);
            }
        } else if (ctx.COMMIT() != null) {
            // An aborted transaction the session still holds is acknowledged here: its COMMIT fails.
            final TransactionManager transactions = queryExecutor.getTransactionManager();
            if (transactions.hasActiveTransaction() || transactions.getAbortedTransaction() != null) {
                // A transaction is its own scope's to end (live-verified).
                transactions.requireOwnScope();
                transactions.commit();
            }
            // Silently ignore COMMIT when no transaction is active (defensive scripting pattern)
        } else if (ctx.ROLLBACK() != null) {
            final TransactionManager transactions = queryExecutor.getTransactionManager();
            if (transactions.hasActiveTransaction() || transactions.getAbortedTransaction() != null) {
                transactions.requireOwnScope();
                transactions.rollback();
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
        // Snowflake: a DDL statement that ends the current transaction commits it implicitly (the DDL runs as its
        // own transaction). Done at the shared dispatch point so a DDL inside a procedural BEGIN…END body —
        // e.g. the CREATE TEMP TABLE … AS SELECT * FROM <stream> flush idiom — ends the open
        // transaction exactly as it does at top level. Which statements do is ImplicitCommit's to say: ALTER
        // SESSION only changes session parameters and does NOT commit (loaders set QUERY_TAG between statements
        // INSIDE their explicit transaction — committing there split the transaction and a second stream read
        // saw an already-consumed, empty window), and neither does creating a sequence or a routine.
        final boolean alterSession =
            ctx.alterStatement() != null && ctx.alterStatement().SESSION() != null;
        if (alterSession) {
            // A procedure runs in the caller's session and may not reconfigure it.
            rejectInsideProcedure("ALTER_SESSION");
        }
        final TransactionManager transactions = queryExecutor.getTransactionManager();
        final boolean ends = transactions.hasActiveTransaction() && ImplicitCommit.ends(ctx)
            && !createsNothing(ctx.createStatement());
        // A statement refused for the names it writes is refused before the transaction is touched, and before
        // the scope is judged: its handler raises that refusal (see NamesJudgedFirst).
        final boolean namesFirst = ends && NamesJudgedFirst.refuses(ctx, queryExecutor);
        if (ends && !namesFirst) {
            // A block or a procedure may not end the transaction its caller began this way either.
            transactions.requireOwnScope();
            transactions.commit();
        }
        final Object answer = visitChildren(ctx);
        if (namesFirst && transactions.hasActiveTransaction()) {
            // The statement ran after all: it ends the transaction as any statement of its kind does.
            transactions.requireOwnScope();
            transactions.commit();
        }
        if (runsAsOwnTransaction(ctx.createStatement())) {
            queryExecutor.getTransactionManager().recordStatementTransaction();
        }
        settleConstraintNames(ctx);
        return answer;
    }

    /**
     * Whether a CREATE TABLE IF NOT EXISTS finds its table there: it creates nothing then, and ends no transaction
     * (live-verified).
     */
    private boolean createsNothing(final FrostlakeParser.CreateStatementContext create) {
        if (create == null || create.TABLE() == null || create.if_not_exists() == null
                || create.objectName() == null) {
            return false;
        }
        try {
            final String named = queryExecutor.resolveObjectName(create.objectName());
            return queryExecutor.getCatalog().resolveTable(named) != null;
        } catch (final RuntimeException missing) {
            return false;
        }
    }

    /**
     * A CREATE TABLE or an ALTER TABLE names the constraints it leaves without one before it ends (see
     * {@code Table.settleConstraintNames}).
     */
    private void settleConstraintNames(final FrostlakeParser.DdlStatementContext ctx) {
        final FrostlakeParser.ObjectNameContext named;
        if (ctx.createStatement() != null && ctx.createStatement().TABLE() != null
                && ctx.createStatement().STREAM() == null) {
            named = ctx.createStatement().objectName();
        } else if (ctx.alterStatement() != null && ctx.alterStatement().TABLE() != null) {
            named = ctx.alterStatement().objectName();
        } else {
            named = null;
        }
        if (named == null) {
            return;
        }
        final Table table;
        try {
            table = queryExecutor.getCatalog().resolveTable(queryExecutor.resolveObjectName(named));
        } catch (final RuntimeException notATable) {
            // Renamed, dropped by the statement itself, or no table at all: nothing is left to name.
            return;
        }
        if (table != null) {
            table.settleConstraintNames();
        }
    }

    /**
     * Whether a CREATE runs as a transaction LAST_TRANSACTION then answers: one filling a table from a query or
     * from another table, and one creating a stream. Any other DDL leaves the session's last transaction as it
     * was (live-verified).
     */
    private static boolean runsAsOwnTransaction(final FrostlakeParser.CreateStatementContext create) {
        if (create == null) {
            return false;
        }
        if (create.STREAM() != null) {
            return true;
        }
        return create.TABLE() != null && create.DYNAMIC() == null && create.EVENT() == null
            && (create.CLONE() != null || create.selectStatement() != null);
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
            // A statement that meets a table another session's transaction holds waits for it and runs again over
            // what the holder left (see TableLockGate).
            long waitingSince = 0L;
            while (true) {
                // Inside an explicit transaction a DML statement that fails undoes itself and nothing else: what a
                // block, a procedure or a handler ran before it stays in the transaction, each a statement of its
                // own (live-verified).
                final StatementSavepoint savepoint = StatementSavepoint.begin(queryExecutor);
                boolean completed = false;
                TableLockWait held = null;
                try {
                    final Object answer = visitChildren(ctx);
                    completed = true;
                    return answer;
                } catch (final TableLockWait locked) {
                    held = locked;
                } finally {
                    savepoint.end(completed);
                }
                waitingSince = waitingSince == 0L ? System.nanoTime() : waitingSince;
                queryExecutor.awaitTableLock(held, waitingSince);
            }
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
        final Object answer = dmlHandler.handleCopyIntoStatement(ctx);
        // A load into a table runs as a transaction of its own (see TransactionManager.recordStatementTransaction).
        if (ctx.qualifiedName() != null) {
            queryExecutor.getTransactionManager().recordStatementTransaction();
        }
        return answer;
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
            refuseSessionListingsInsideOwnersRights(ctx);
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

    /** A block's logging statement: its arguments are evaluated and the message goes to the engine's own log. */
    @Override
    public Object visitSystemCallStatement(final FrostlakeParser.SystemCallStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildLogging(ctx));
        return null;
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
                // The query's text with its FOR UPDATE, which the cursor's OPEN refuses as the query statement does.
                stmt = new DeclareCursorStatement(cursorName, getOriginalText(src));
            } else {
                stmt = new DeclareCursorStatement(cursorName, null, getText(src.variableName()));
                stmt.setResultSetAt(src.variableName().getStart().getLine(),
                    src.variableName().getStart().getCharPositionInLine());
            }
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared cursor: {}", cursorName);
        } else if (ctx.RESULTSET() != null) {
            // ResultSet declaration — bare, or bound to a parenthesized statement by either DEFAULT or :=
            // (live accepts every combination).
            final String resultSetName = getText(ctx.identifier());
            // An ASYNC (…) initialiser is a job that fills the RESULTSET when it is awaited.
            final FrostlakeParser.ResultSetSourceContext source = ctx.asyncJob() != null
                ? ctx.asyncJob().resultSetSource() : ctx.resultSetSource();
            final String selectQuery = resultSetInitializerText(source);

            final DeclareResultSetStatement stmt = new DeclareResultSetStatement(resultSetName, selectQuery);
            stmt.setAsync(ctx.asyncJob() != null);
            noteInitialiserQuery(stmt, source);
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
                defaultValue = ctx.dataTypeName() != null
                    ? declaredInitialValue(ctx.booleanExpr(), parseDeclaredType(ctx.dataTypeName(),
                        ctx.typeParameters()))
                    : evaluateExpression(ctx.booleanExpr());
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
    String resultSetInitializerText(final FrostlakeParser.ResultSetSourceContext query) {
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
        // A session variable is the session's, which an owner's-rights procedure may not touch (live-verified).
        rejectInsideProcedure("SET");
        // Session-level SET is a SQL command (scripting assigns with := / LET), so its right-hand side is
        // a SQL expression: session variables and literals resolve, a bare scripting name does not.
        final SecurityManager sm = queryExecutor.getSecurityManager();
        final List<String> names = SessionVariableNames.of(ctx.sessionVariable());
        // Inside a block a bind names the block's variable; outside one nothing holds it.
        final boolean inBlock = proceduralExecutor != null && proceduralExecutor.isExecutingOwnBlock();
        if (ctx.LPAREN().isEmpty()) {
            // SET var = expr
            if (!inBlock) {
                SessionVariableNames.refuseUnsetBinds(ctx.booleanExpr());
            }
            setSessionVar(sm, names.get(0), sessionValueOf(ctx.booleanExpr()));
            return null;
        }
        // SET (var1, var2, ...) = (expr1, expr2, ...): every value is computed before any variable is set, so
        // SET (a, b) = (50, 2 * $a) reads a's old value and a refused value sets none (live-verified). The values
        // may be one value written bare, and their count is the names'.
        final List<FrostlakeParser.BooleanExprContext> exprs = ctx.booleanExprList() != null
            ? ctx.booleanExprList().booleanExpr() : Collections.singletonList(ctx.booleanExpr());
        if (exprs.size() != names.size()) {
            throw new RuntimeException(SqlCompilationError.inline("Number of variables on left-hand side of "
                + "assignment (" + names.size() + ") is not equal to the number of values on right-hand side ("
                + exprs.size() + ")."));
        }
        final List<Object> values = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext expr : exprs) {
            if (!inBlock) {
                SessionVariableNames.refuseUnsetBinds(expr);
            }
            values.add(sessionValueOf(expr));
        }
        for (int i = 0; i < names.size(); i++) {
            setSessionVar(sm, names.get(i), values.get(i));
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
        rejectInsideProcedure("UNSET");
        final SecurityManager sm = queryExecutor.getSecurityManager();
        final List<String> names = SessionVariableNames.of(ctx.sessionVariable());
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
        final String varName = getText(ctx.variableName());

        // Check if this is a cursor declaration (LET cursor_name CURSOR FOR SELECT...)
        if (ctx.CURSOR() != null) {
            // Over a SELECT query or the name of a RESULTSET variable, as the DECLARE section takes it.
            final FrostlakeParser.CursorSourceContext src = ctx.cursorSource();
            final DeclareCursorStatement stmt = src.selectStatement() != null
                ? new DeclareCursorStatement(varName, getOriginalText(src))
                : new DeclareCursorStatement(varName, null, getText(src.variableName()));
            if (src.variableName() != null) {
                stmt.setResultSetAt(src.variableName().getStart().getLine(),
                    src.variableName().getStart().getCharPositionInLine());
            }
            proceduralExecutor.executeStatement(stmt);
            logger.trace("Let cursor: {}", varName);
            return null;
        }

        // LET name RESULTSET [{:= | DEFAULT} ( <query> )] — declared exactly like DECLARE name
        // RESULTSET: the query (SELECT, EXECUTE IMMEDIATE or CALL) runs where the LET stands, and the
        // bare spelling declares an unbound resultset.
        if (ctx.RESULTSET() != null) {
            // An ASYNC (…) initialiser is a job that fills the RESULTSET when it is awaited.
            final FrostlakeParser.ResultSetSourceContext source = ctx.asyncJob() != null
                ? ctx.asyncJob().resultSetSource() : ctx.resultSetSource();
            final DeclareResultSetStatement rsStmt =
                new DeclareResultSetStatement(varName, resultSetInitializerText(source));
            rsStmt.setAsync(ctx.asyncJob() != null);
            noteInitialiserQuery(rsStmt, source);
            proceduralExecutor.executeStatement(rsStmt);
            logger.trace("Let resultset: {}", varName);
            return null;
        }

        if (ctx.booleanExpr() == null) {
            // A type and no value declares the variable NULL of its type, as a DECLARE section's item does
            // (live-verified).
            proceduralExecutor.executeStatement(new DeclareStatement(varName, null,
                parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters())));
            logger.trace("Let variable: {}", varName);
            return null;
        }

        // Regular LET with expression. A typed one's value is compiled when it is reached; an untyped one's
        // initialiser while the block compiles.
        if (ctx.dataTypeName() != null) {
            proceduralExecutor.judgeBlockExpression(ctx.booleanExpr(),
                parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters()));
        }
        final Object defaultValue = ctx.dataTypeName() != null
            ? declaredInitialValue(ctx.booleanExpr(), parseDeclaredType(ctx.dataTypeName(), ctx.typeParameters()))
            : evaluateExpression(ctx.booleanExpr());
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
    public Object visitAsyncStatement(final FrostlakeParser.AsyncStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildAsyncStatement(ctx));
        return null;
    }

    @Override
    public Object visitAwaitStatement(final FrostlakeParser.AwaitStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildAwaitStatement(ctx));
        return null;
    }

    @Override
    public Object visitAssignmentStatement(final FrostlakeParser.AssignmentStatementContext ctx) {
        final String varName = getText(ctx.assignedName());

        // Handle: rs := ASYNC (<statement>) — a job that fills the RESULTSET when it is awaited. To any other
        // target the word is a function no one defined, refused as the value it is written as (live-verified).
        if (ctx.asyncJob() != null) {
            final FrostlakeParser.ResultSetSourceContext job = ctx.asyncJob().resultSetSource();
            if (!proceduralExecutor.holdsResultSet(varName)) {
                proceduralExecutor.recordExpressionFailure(ctx.asyncJob().getStart().getLine(),
                    ctx.asyncJob().getStart().getCharPositionInLine());
                throw new RuntimeException(SqlCompilationError.of("Unknown function ASYNC."));
            }
            proceduralExecutor.assignAsyncJob(varName, resultSetInitializerText(job), job.getStart().getLine(),
                job.getStart().getCharPositionInLine());
            return null;
        }

        // Handle: rs := (CALL …) into a RESULTSET: the variable holds the CALL's result set.
        if (ctx.callStatement() != null && proceduralExecutor.holdsResultSet(varName)) {
            final FrostlakeParser.CallStatementContext call = ctx.callStatement();
            final int[] place = proceduralExecutor.placeOfAssignedCall(call.getStart());
            proceduralExecutor.assignResultSetQuery(varName, getOriginalText(call), place[0], place[1]);
            return null;
        }

        // Handle: var := (call proc()) — capture procedure return value. The CALL is the statement that runs: a
        // failure of it stands at the CALL, and its own places count from there (live-verified).
        if (ctx.callStatement() != null) {
            final FrostlakeParser.CallStatementContext call = ctx.callStatement();
            final int[] place = proceduralExecutor.placeOfAssignedCall(call.getStart());
            final SourcePosition displaced = LeadingCommentOffset.begin(
                new SourcePosition(call.getStart().getLine(), call.getStart().getCharPositionInLine()));
            final Object result;
            try {
                result = visitCallStatement(call);
            } catch (final RuntimeException failed) {
                proceduralExecutor.recordStatementFailure(place[0], place[1]);
                throw failed;
            } finally {
                LeadingCommentOffset.end(displaced);
            }
            // The answer is held as the CALL handed it back, its conversion waiting for a read of the variable.
            proceduralExecutor.assignCallAnswer(varName, ProceduralExecutor.procedureReturnValue(result));
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
        final String cursorName = getText(ctx.variableName());
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

        // OPEN c USING (a, b, …): bind the values to the cursor query's `?` placeholders in turn and to :n by number.
        final List<Object> binds = new ArrayList<>();
        if (ctx.USING() != null && ctx.expressionList() != null) {
            for (final FrostlakeParser.ExpressionContext bindExpr : ctx.expressionList().expression()) {
                // Each value is handed over as its text (see UsingBindText), a CALL's answer a name holds unconverted
                // as the CALL handed it back.
                final Object held = bindExpr instanceof FrostlakeParser.QualifiedNameExprContext
                    ? proceduralExecutor.heldCallAnswer(
                        ((FrostlakeParser.QualifiedNameExprContext) bindExpr).qualifiedName().getText())
                    : null;
                binds.add(proceduralExecutor.usingBind(bindExpr.getText(),
                    held != null ? held : evaluateExpression(bindExpr)));
            }
        }
        // A cursor's query binds none of the block's variables (see ProceduralExecutor.cursorQuery).
        final String declared = proceduralExecutor.cursorQuery(cursor, binds.size());
        final String selectSql = binds.isEmpty() ? declared
            : JdbcMarshaling.bindUsing(declared, binds, new HashSet<Integer>());
        // Execute the SELECT query to populate the cursor; one written with FOR UPDATE is refused as the query
        // statement is, when the cursor opens (live-verified).
        final ResultSet resultSet;
        try {
            final FrostlakeParser.QueryStatementContext query = parseCursorQuery(selectSql);
            if (query.forUpdateClause() != null) {
                queryHandler.handleQueryStatement(query);
            }
            resultSet = queryExecutor.executeSelectFromContext(query.selectStatement());
        } catch (final RuntimeException refused) {
            // A bound value echoed as a predicate is named by its placeholder (see UsingBindEcho).
            throw binds.isEmpty() ? refused : UsingBindEcho.named(refused, declared, binds, new HashSet<Integer>());
        }
        cursor.open(resultSet);

        logger.trace("Opened cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitFetchStatement(final FrostlakeParser.FetchStatementContext ctx) {
        final String cursorName = getText(ctx.variableName());
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
        final String cursorName = getText(ctx.variableName());

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
                "A SELECT INTO statement expects exactly 1 returned row, but got " + rowCount + ".");
        }
        if (rowCount == 0) {
            for (final FrostlakeParser.IntoTargetContext target : targets) {
                assignSelectIntoTarget(ctx, getText(target.variableName()), null);
                if (proceduralExecutor != null) {
                    proceduralExecutor.markEmptySelectTarget(getText(target.variableName()));
                }
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
            assignSelectIntoTarget(ctx, getText(targets.get(i).variableName()), firstRow.getValue(i));
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
    /**
     * Give a SELECT … INTO target its value. A value the target's type does not take is the statement's own
     * expression failing, where the statement stands: {@code SELECT 'x' INTO :r} over an INT is an
     * EXPRESSION_ERROR at the SELECT (live-verified).
     */
    private void assignSelectIntoTarget(final FrostlakeParser.SelectIntoStatementContext into, final String varName,
                                        final Object value) {
        if (proceduralExecutor != null) {
            final LiteralExpression assigned = new LiteralExpression(carriedAsText(varName, value));
            assigned.setSourcePosition(into.getStart().getLine(), into.getStart().getCharPositionInLine());
            proceduralExecutor.executeStatement(new SetStatement(varName, assigned));
        }
    }

    /**
     * The value a SELECT … INTO hands a DATE, TIME or TIMESTAMP_NTZ target: its text, which the target reads back
     * under the session's input format, so {@code DATE_INPUT_FORMAT = 'DD/MM/YYYY'} refuses a fetched date as
     * {@code Date '2024-02-01' is not recognized} at the SELECT (live-verified); any other value as it is.
     */
    private Object carriedAsText(final String varName, final Object value) {
        final DataType declared = proceduralExecutor.getDeclaredVariableType(varName);
        if (!(declared instanceof DateTimeType)) {
            return value;
        }
        final String kind = declared.getName().toUpperCase(Locale.ROOT);
        if ("DATE".equals(kind) && value instanceof LocalDate) {
            return value.toString();
        }
        if ("TIME".equals(kind) && value instanceof LocalTime) {
            return CARRIED_TIME.format((LocalTime) value);
        }
        if (("TIMESTAMP_NTZ".equals(kind) || "TIMESTAMP".equals(kind)) && value instanceof LocalDateTime) {
            return CARRIED_TIMESTAMP.format((LocalDateTime) value);
        }
        return value;
    }

    private FrostlakeParser.QueryStatementContext parseCursorQuery(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        final SyntaxErrorListener errorListener = new SyntaxErrorListener(sql);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        final FrostlakeParser.QueryStatementContext query = parser.queryStatement();
        errorListener.throwIfErrors();
        NamedCallRewrite.apply(query.selectStatement());

        return query;
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
        if (typed instanceof BinaryType) {
            // A SQL body's binary answer is a column at the type's maximum, whatever width RETURNS wrote:
            // BINARY(67108864) to SYSTEM$TYPEOF and to DESCRIBE RESULT (live-verified).
            return BinaryType.AT_MAXIMUM;
        }
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

    /**
     * The column a handler procedure's CALL answers in: the RETURNS type with the width it was written with —
     * {@code VARCHAR(2)}, {@code BINARY(2)} — where a SQL body's text answer is unsized; a text declared without
     * a width reads at the width nothing bounds and a binary declared without one at the maximum,
     * {@code BINARY(67108864)} (live-verified).
     */
    private static DataType handlerCallColumnType(final Procedure procedure) {
        final DataType declared = procedure.getReturnType();
        if (declared instanceof StringType
                && ((StringType) declared).getMaxLength() < StringType.VARCHAR.getMaxLength()) {
            return declared;
        }
        if (declared instanceof BinaryType) {
            return ((BinaryType) declared).getMaxLength() < BinaryType.UNSIZED.getMaxLength() ? declared
                : BinaryType.AT_MAXIMUM;
        }
        return returnlessCallColumnType(procedure);
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

        if (ctx.variableName() != null && ctx.STRING_LITERAL() == null) {
            // RAISE exception_name - must be a user-defined exception
            final String name = getText(ctx.variableName());
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
    boolean insideOwnersRightsProcedure() {
        return procedureDepth > 0;
    }

    void rejectInsideProcedure(final String statementType) {
        // In a SQL body, and in a statement that a JavaScript, Python, Java or Scala handler issues with its
        // owner's rights (live-verified for a JavaScript handler's SET, UNSET, ALTER SESSION and USE).
        if (procedureDepth > 0 || SessionRole.runsWithOwnersRights()) {
            throw new RuntimeException("Stored procedure execution error: "
                + "Unsupported statement type '" + statementType + "'.");
        }
    }

    /**
     * The listings of the session's own state are not an owner's-rights procedure's to read (all live-verified):
     * SHOW VARIABLES, SHOW LOCKS, SHOW TRANSACTIONS and SHOW PARAMETERS of the session — written with no scope or
     * IN SESSION, where IN ACCOUNT answers — are refused as an unsupported statement type named in the singular,
     * and a SHOW GRANTS that names nothing by a sentence of its own. Each is the failure of the statement when it
     * runs, in a body of any language.
     */
    private void refuseSessionListingsInsideOwnersRights(final FrostlakeParser.ShowStatementContext ctx) {
        if (procedureDepth == 0 && !SessionRole.runsWithOwnersRights()) {
            return;
        }
        final boolean sessionParameters = ctx.PARAMETERS() != null
            && (ctx.showParametersScope() == null || ctx.showParametersScope().SESSION() != null);
        final String type = ctx.VARIABLES() != null ? "SHOW VARIABLE" : ctx.LOCKS() != null ? "SHOW LOCK"
            : ctx.TRANSACTIONS() != null ? "SHOW TRANSACTION" : sessionParameters ? "SHOW PARAMETER" : null;
        if (type != null) {
            throw new RuntimeException("Stored procedure execution error: Unsupported statement type '" + type + "'.");
        }
        if (ctx.GRANTS() != null && ctx.ON() == null && ctx.TO() == null && ctx.OF() == null
                && ctx.showGrantsIn() == null) {
            throw new RuntimeException(
                "SHOW GRANTS to the current user is not supported in owner's rights stored procedures.");
        }
    }

    /**
     * A caller's-rights body that an owner's-rights procedure calls compiles its {@code $name} references under
     * that procedure's rights, and is refused for the first of them before any of it runs (live-verified): the
     * calling statement's failure, with no sentence of the body's own.
     */
    private static void refuseSessionVariablesUnderOwnersRights(final ParseTree node) {
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            if (token.getType() == FrostlakeParser.SESSION_VAR_REF) {
                throw SessionVariableUse.refusal(token.getText().substring(1).toUpperCase(Locale.ROOT));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            refuseSessionVariablesUnderOwnersRights(node.getChild(i));
        }
    }

    /**
     * Moves the session to the database and schema an owner's-rights procedure lives in, for the time its body
     * runs.
     *
     * @param homeDb the procedure's database, or null for a body that stays where its caller is
     * @param homeSchema the procedure's schema
     */
    private void enterHome(final String homeDb, final String homeSchema) {
        if (homeDb == null) {
            return;
        }
        catalog.useDatabase(homeDb);
        if (homeSchema != null) {
            catalog.useSchema(homeSchema);
        }
    }

    /**
     * CALL ... INTO :variable, a statement of a Snowflake Scripting block: the CALL runs as a bare one runs, and the
     * value the procedure returns is assigned to the variable, coerced to its declared type as an assignment coerces
     * — a value the variable does not take is the statement's own expression failing, as a SELECT … INTO's is. A
     * procedure that answers several rows is refused as a SELECT … INTO over them is. Outside a block the clause is
     * one no statement there takes, refused at its INTO (live-verified).
     */
    @Override
    public Object visitCallIntoStatement(final FrostlakeParser.CallIntoStatementContext ctx) {
        boolean inBlock = false;
        for (ParserRuleContext up = ctx.getParent(); up != null && !inBlock; up = up.getParent()) {
            inBlock = up instanceof FrostlakeParser.BeginEndBlockContext;
        }
        if (!inBlock || proceduralExecutor == null) {
            final Token into = ctx.INTO().getSymbol();
            throw new RuntimeException(
                SqlCompilationError.intoClauseNotAllowed(into.getLine(), into.getCharPositionInLine()));
        }
        final Object result = visitCallStatement(ctx.callStatement());
        final LiteralExpression assigned = new LiteralExpression(ProceduralExecutor.procedureReturnValue(result));
        assigned.setSourcePosition(ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        proceduralExecutor.executeStatement(
            new SetStatement(getText(ctx.intoTargetList().intoTarget(0).variableName()), assigned));
        return result;
    }

    @Override
    public Object visitCallStatement(final FrostlakeParser.CallStatementContext ctx) {
        // An owner's-rights body re-pins how its refusals are worded and whom it creates as (see executeCall); the
        // caller's come back.
        final SessionRole callerRole = SessionRole.pinned();
        final SecurityManager security = queryExecutor.getSecurityManager();
        final String callerOwner = security == null ? null : security.getSessionContext().getExecutableOwner();
        // A procedure's body, in any language, is a transaction scope of its own: it adds to a transaction its
        // caller began and may not end it, and a transaction it begins is its own to end before it returns
        // (live-verified).
        final TransactionManager transactions = queryExecutor.getTransactionManager();
        transactions.enterScope();
        final int level = transactions.scopeDepth();
        Object answer = null;
        RuntimeException failure = null;
        boolean leftOpen = false;
        try {
            answer = executeCall(ctx);
        } catch (final RuntimeException failed) {
            failure = failed;
        } finally {
            leftOpen = transactions.exitScope();
            if (security != null) {
                security.getSessionContext().setExecutableOwner(callerOwner);
            }
            SessionRole.restore(callerRole);
        }
        if (CallDepth.endedAt(level)) {
            // The body ran past the depth bodies run to and was ended where it issued a statement: that is this
            // CALL's failure, whatever the body handed back or let out — a handler language may have caught the
            // refusal (see CallDepth).
            throw new RuntimeException(CallDepth.REFUSAL);
        }
        if (leftOpen) {
            // The transaction the body left open is rolled back, and that is what the CALL reports, whatever the
            // body let out besides (live-verified).
            throw new RuntimeException(TransactionManager.SCOPE_LEFT_OPEN);
        }
        if (failure != null) {
            throw failure;
        }
        return answer;
    }

    /** The database a name under INFORMATION_SCHEMA is written with, which is looked up (live-verified). */
    private void requireQualifierDatabase(final List<String> parts) {
        final String database = InformationSchemaCalls.database(parts);
        if (database != null && !catalog.hasDatabaseExact(database)) {
            throw new RuntimeException(SqlCompilationError.doesNotExistWithoutHint("Object", database));
        }
    }

    /**
     * A missing procedure, in the unknown-FUNCTION vocabulary (live-verified): the name as written, each
     * part quoted only when it needs quotes, with a full stop and no argument signature, and a qualified
     * spelling reported as an unknown user-defined function.
     */
    private static RuntimeException unknownProcedure(final String[] parts) {
        final String spelled = spelledRoutineName(parts);
        return new RuntimeException(SqlCompilationError.of(parts.length > 1
            ? "Unknown user-defined function " + spelled + "."
            : "Unknown function " + spelled + "."));
    }

    /**
     * The schema a CALL's name writes, matched exactly as any SQL reference is — {@code "test_db".s.p} names no
     * procedure of the database TEST_DB — and a name whose schema or database is not there misses in the words of
     * a procedure that is simply absent (live-verified).
     */
    private static Schema writtenSchema(final Database database, final String schemaName, final String[] parts) {
        if (database == null || !database.hasSchemaExact(schemaName)) {
            throw unknownProcedure(parts);
        }
        return database.schemaExact(schemaName);
    }

    /** A routine's name as a refusal prints it for a CALL written with these parts: {@code TEST_SCHEMA.P}. */
    private static String spelledRoutineName(final String[] parts) {
        final StringBuilder spelled = new StringBuilder();
        for (final String part : parts) {
            spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return spelled.toString();
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
        // A name written twice is refused at its second writing before the arguments are counted (live-verified).
        CallNamedShape.require(ctx);
        if (SendEmailProcedure.calls(ctx)) {
            // A procedure of the account's own, no function (see SendEmailProcedure).
            return SendEmailProcedure.call(queryExecutor, ctx);
        }
        rejectSystemCallArity(ctx, name.toUpperCase(Locale.ROOT));
        return callAsSelect(ctx, name);
    }

    /**
     * A CALL of a built-in function that is no procedure: the account runs it as it runs a SYSTEM$ function's,
     * {@code CALL UPPER('a')} answering A in a column UPPER and {@code CALL SUM(1)} answering 1, with whatever the
     * arguments are - a nested call, a subquery (live-verified). A user-defined function is never called this way.
     */
    private ResultSet callAsSelect(final FrostlakeParser.CallStatementContext ctx, final String name) {
        final Token call = ctx.CALL().getSymbol();
        final Token written = ctx.systemFunctionName() != null ? null : ctx.qualifiedName().getStart();
        final String afterCall;
        if (written != null && written.getType() == FrostlakeLexer.QUOTED_IDENTIFIER
                && written == ctx.qualifiedName().getStop() && name.length() <= written.getText().length()) {
            // A name between quotes is the function's all the same: the SELECT names it bare, where it stood.
            final StringBuilder bare = new StringBuilder(name);
            while (bare.length() < written.getText().length()) {
                bare.append(' ');
            }
            afterCall = call.getInputStream().getText(Interval.of(call.getStopIndex() + 1, written.getStartIndex() - 1))
                + bare + call.getInputStream().getText(
                    Interval.of(written.getStopIndex() + 1, ctx.RPAREN().getSymbol().getStopIndex()));
        } else {
            afterCall = call.getInputStream().getText(
                Interval.of(call.getStopIndex() + 1, ctx.RPAREN().getSymbol().getStopIndex()));
        }
        // The SELECT is the CALL's own work, so it takes no query ID and no history row of its own.
        final List<ResultSet> answered;
        try {
            answered = queryExecutor.executeFromUnrecorded("SELECT" + afterCall,
                new SourcePosition(1, "SELECT".length() - call.getText().length()));
        } catch (final RuntimeException refused) {
            throw unplacedCount(refused);
        }
        final ResultSet answer = answered.isEmpty() ? null : answered.get(0);
        final Object value = answer == null || answer.getRowCount() == 0 ? null : answer.getRows().get(0).getValue(0);
        // The column is typed as the SELECT's is, its width included: CALL UPPER('a') is a VARCHAR(3).
        final DataType type = answer == null || answer.getColumns().isEmpty() ? null
            : answer.getColumns().get(0).getDataType();
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(name.toUpperCase(Locale.ROOT), type));
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(value));
        return new ResultSet(columns, rows);
    }

    /** A refusal of the number, the names or the types of a call's arguments, which a CALL places nowhere: line 0, position -1. */
    private static RuntimeException unplacedCount(final RuntimeException refused) {
        final String message = refused.getMessage();
        final Matcher place = message == null || refused.getClass() != RuntimeException.class ? null
            : PLACED_COUNT.matcher(message);
        return place == null || !place.find() ? refused
            : new RuntimeException(SqlCompilationError.at(0, -1, message.substring(place.end(1))), refused.getCause());
    }

    /**
     * Whether a CALL argument is the positional reference {@code $1} alone, which a CALL binds as NULL, as a query
     * without FROM reads it; {@code $2} and on are invalid identifiers there (live-verified).
     */
    private static boolean isFirstPositionalReference(final FrostlakeParser.ExpressionContext argument) {
        final Token start = argument.getStart();
        return start == argument.getStop() && start.getType() == FrostlakeParser.POSITIONAL_PARAMETER
            && "$1".equals(start.getText());
    }

    /** A CALL's arguments as written, comma-separated. */
    private static String writtenArguments(final FrostlakeParser.CallStatementContext ctx) {
        final List<String> written = new ArrayList<>();
        if (ctx.callArguments() != null) {
            for (final FrostlakeParser.CallArgumentContext argument : ctx.callArguments().callArgument()) {
                written.add(ParseTreeText.getOriginalText(argument));
            }
        }
        return String.join(", ", written);
    }

    /**
     * Refuse a CALL of a SYSTEM$ function with another number of arguments than the function takes. The echo writes
     * a named argument's value alone: {@code CALL SYSTEM$TYPEOF(a => 1, b => 2)} is {@code [SYSTEM$TYPEOF(1, 2)]}
     * (live-verified).
     */
    private static void rejectSystemCallArity(final FrostlakeParser.CallStatementContext ctx, final String name) {
        final List<String> written = new ArrayList<>();
        if (ctx.callArguments() != null) {
            for (final FrostlakeParser.CallArgumentContext argument : ctx.callArguments().callArgument()) {
                final FrostlakeParser.NamedArgumentContext named = argument.namedArgument();
                written.add(ParseTreeText.getOriginalText(named == null ? argument
                    : named.expression() != null ? named.expression()
                    : named.selectStatement() != null ? named.selectStatement() : named.argumentRow()));
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

    /**
     * The type a procedure's body holds a parameter at: its declared one, and a text or a binary parameter
     * written without a length as wide as a block's own variable declared so, VARCHAR(134217728) and
     * BINARY(67108864) (live-verified).
     */
    private static DataType heldType(final Parameter parameter) {
        final DataType declared = parameter.getDataType();
        if (parameter.isUnsized() && declared instanceof BinaryType) {
            return BinaryType.AT_MAXIMUM;
        }
        return parameter.isUnsized() && declared instanceof StringType
            ? new StringType(declared.getName(), DataTypeParser.CAST_STRING_DEFAULT) : declared;
    }

    private Object executeCall(final FrostlakeParser.CallStatementContext ctx) {
        if (ctx.systemFunctionName() != null) {
            final List<String> qualifier = new ArrayList<>();
            if (ctx.callQualifier() != null) {
                for (final FrostlakeParser.IdentifierContext part : ctx.callQualifier().identifier()) {
                    qualifier.add(ParseTreeText.getIdentifier(part));
                }
            }
            qualifier.add(ctx.systemFunctionName().getText().toUpperCase(Locale.ROOT));
            if (InformationSchemaCalls.builtIn(qualifier) != null) {
                // A built-in named under INFORMATION_SCHEMA is the built-in itself (see InformationSchemaCalls).
                requireQualifierDatabase(qualifier);
                return callSystemFunction(ctx);
            }
            if (ctx.callQualifier() != null) {
                // A SYSTEM$ function has no qualifier: under one the name is a routine nobody declared.
                final StringBuilder spelled = new StringBuilder();
                for (final FrostlakeParser.IdentifierContext part : ctx.callQualifier().identifier()) {
                    spelled.append(SqlIdentifiers.spellCanonical(ParseTreeText.getIdentifier(part))).append('.');
                }
                spelled.append(ctx.systemFunctionName().getText().toUpperCase(Locale.ROOT));
                throw new RuntimeException(SqlCompilationError.of("Unknown user-defined function " + spelled + "."));
            }
            return callSystemFunction(ctx);
        }
        final String qualifiedName = getText(ctx.qualifiedName());
        final String[] parts = qualifiedNameParts(ctx.qualifiedName());
        if (ctx.BANG() != null) {
            // A method of a class instance: there are no classes here, so the instance and its method name
            // nothing, and miss in the words a missing routine does (live-verified).
            final StringBuilder spelled = new StringBuilder();
            for (final String part : parts) {
                spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
            }
            spelled.append('!').append(SqlIdentifiers.spellCanonical(ParseTreeText.getIdentifier(ctx.identifier())));
            throw new RuntimeException(SqlCompilationError.of(parts.length > 1
                ? "Unknown user-defined function " + spelled + "."
                : "Unknown function " + spelled + "."));
        }
        // The shape of its named arguments is judged before the procedure is looked for.
        CallNamedShape.require(ctx);
        final String underInformationSchema = InformationSchemaCalls.builtIn(Arrays.asList(parts));
        if (underInformationSchema != null) {
            // A built-in named under INFORMATION_SCHEMA is the built-in itself (see InformationSchemaCalls).
            requireQualifierDatabase(Arrays.asList(parts));
            final String called = underInformationSchema.toUpperCase(Locale.ROOT);
            if (queryExecutor.getFunctionRegistry().hasFunction(called)
                    || queryExecutor.getFunctionRegistry().hasBuiltInAggregateFunction(called)) {
                return callAsSelect(ctx, called);
            }
        }
        // The canonical name, resolved exactly: CALL proccase() does not reach "procCase" (live-verified).
        final String procName = parts[parts.length - 1];

        final Schema schema;
        if (parts.length == 3) {
            schema = writtenSchema(catalog.hasDatabaseExact(parts[0]) ? catalog.databaseExact(parts[0]) : null,
                parts[1], parts);
        } else if (catalog.getCurrentDatabase() == null
                || parts.length == 1 && catalog.getCurrentSchema() == null) {
            // With no current database a name the session cannot place names no procedure at all, and
            // misses in the same words as one that is simply absent (live-verified).
            throw unknownProcedure(parts);
        } else if (parts.length == 2) {
            schema = writtenSchema(catalog.getDatabase(catalog.getCurrentDatabase()), parts[0], parts);
        } else {
            // An unqualified procedure is looked for along the session's SEARCH_PATH.
            Schema onPath = null;
            for (final Schema candidate : queryExecutor.searchPathSchemas()) {
                if (onPath == null && !candidate.getProcedureOverloads(procName).isEmpty()) {
                    onPath = candidate;
                }
            }
            schema = onPath != null ? onPath : resolveCurrentSchema();
        }

        final List<Procedure> overloads = schema.getProcedureOverloads(procName);
        final String builtIn = procName.toUpperCase(Locale.ROOT);
        final boolean namesBuiltIn = parts.length == 1 && (queryExecutor.getFunctionRegistry().hasFunction(builtIn)
            || queryExecutor.getFunctionRegistry().hasAggregateFunction(builtIn));
        if (!overloads.isEmpty() && namesBuiltIn) {
            // A name with no schema before it that a built-in function holds — a scalar's or an aggregate's,
            // written bare or between quotes in any case — calls the function, whatever procedure the schema holds
            // under it: with a procedure PI(n), CALL pi() answers pi and CALL pi(1) is the built-in's
            // too-many-arguments refusal, where CALL test_schema.pi(1) runs the procedure (live-verified).
            return callAsSelect(ctx, builtIn);
        }
        if (overloads.isEmpty()) {
            if (namesBuiltIn && schema.getFunctionOverloads(procName).isEmpty()) {
                return callAsSelect(ctx, builtIn);
            }
            if (parts.length == 1 && ctx.qualifiedName().getStart().getType() != FrostlakeLexer.QUOTED_IDENTIFIER
                    && WindowFunctionNames.handles(builtIn) && schema.getFunctionOverloads(procName).isEmpty()) {
                // A function that answers only over a window has none in a CALL (live-verified).
                throw new RuntimeException(SqlCompilationError.of("Missing window specification for function ["
                    + builtIn + "(" + writtenArguments(ctx) + ")]."));
            }
            throw unknownProcedure(parts);
        }
        // An overloaded name runs the overload the arguments choose, as live chooses it — a CALL without
        // arguments the overload that takes none.
        final List<FrostlakeParser.CallArgumentContext> written = ctx.callArguments() == null
            ? new ArrayList<FrostlakeParser.CallArgumentContext>() : ctx.callArguments().callArgument();
        final Procedure procedure = overloads.size() == 1
            ? overloads.get(0)
            : CallArgumentTypes.chooseOverload(procName, spelledRoutineName(parts), overloads, written,
                new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(),
                    queryExecutor));
        // Calling another role's procedure asks for USAGE on it.
        queryExecutor.requireRoutinePrivilege(Privilege.USAGE, schema, procedure);
        RoutineSecretsCheck.require(queryExecutor.getCatalog(), procedure.getEnvironment());

        // Evaluate call arguments — positional and/or named (name => value). Positional args bind to
        // parameters left-to-right; a named arg binds to the parameter whose name it matches; any
        // parameter left unbound falls back to its DEFAULT (Snowflake semantics), so a named arg may
        // legitimately skip an earlier defaulted parameter. Snowflake dispatches CALL as SQL, so an
        // argument is a SQL expression: a scripting name must be written :name, and a bare one is an
        // identifier ("invalid identifier 'V'", live-verified) — hence evaluateSqlExpression.
        final List<Parameter> params = procedure.getParameters();
        final Object[] boundValues = new Object[params.size()];
        final boolean[] boundFlags = new boolean[params.size()];
        final boolean[] variantBinds = new boolean[params.size()];
        final boolean[] exactNumbers = new boolean[params.size()];
        int positionalIndex = 0;
        if (ctx.callArguments() == null) {
            // A CALL that gives no argument is judged like any other: a parameter without a default it leaves
            // unfilled is the argument-type refusal over no types.
            CallArgumentTypes.check(procName, params, written, false,
                new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(),
                    queryExecutor));
        }
        if (ctx.callArguments() != null) {
            // A CALL is all-named or all-positional, which its shape was judged for before the procedure was
            // looked for (see CallNamedShape).
            boolean sawNamed = false;
            for (final FrostlakeParser.CallArgumentContext argCtx : ctx.callArguments().callArgument()) {
                sawNamed |= argCtx.namedArgument() != null;
            }
            // The names that fit no signature are refused under the procedure's name as the CALL writes it,
            // test_schema.p as TEST_SCHEMA.P (live-verified).
            final String namedName = parts.length > 1 ? spelledRoutineName(parts) : procName;
            CallArgumentTypes.refuseNamedShape(procName, params, ctx.callArguments().callArgument());
            CallArgumentTypes.refuseRows(namedName, params, ctx.callArguments().callArgument());
            CallArgumentTypes.refuseMisnamed(namedName, params, ctx.callArguments().callArgument());
            // Each argument's TYPE is matched against its parameter before any is evaluated, as live
            // matches them while the CALL compiles: at the top level, and in a block or a procedure's body,
            // where a :name is judged by the type its variable is declared with, whatever it holds.
            final ExpressionEvaluator typer = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
                queryExecutor.getCatalog(), queryExecutor);
            CallArgumentTypes.check(sawNamed ? namedName : procName, params,
                ctx.callArguments().callArgument(), sawNamed, typer);
            for (final FrostlakeParser.CallArgumentContext argCtx : ctx.callArguments().callArgument()) {
                if (argCtx.namedArgument() != null) {
                    final String argName = getText(argCtx.namedArgument().variableName());
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
                    variantBinds[idx] = CallArgumentTypes.bindsAVariant(argCtx.namedArgument().expression(), typer);
                    exactNumbers[idx] = CallArgumentTypes.isExactNumber(argCtx.namedArgument().expression(), typer);
                } else {
                    if (positionalIndex >= params.size()) {
                        throw new RuntimeException("Too many arguments for procedure: " + qualifiedName);
                    }
                    // A NOT, AND or OR over values is an argument too: CALL p(NOT TRUE) binds FALSE (live-verified).
                    boundValues[positionalIndex] = argCtx.expression() == null
                        ? evaluateSqlExpression(argCtx.booleanExpr())
                        : isFirstPositionalReference(argCtx.expression()) ? null
                        : evaluateSqlExpression(argCtx.expression());
                    boundFlags[positionalIndex] = true;
                    variantBinds[positionalIndex] = argCtx.expression() != null
                        && CallArgumentTypes.bindsAVariant(argCtx.expression(), typer);
                    exactNumbers[positionalIndex] = argCtx.expression() != null
                        && CallArgumentTypes.isExactNumber(argCtx.expression(), typer);
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
        // Each argument is converted to its parameter's declared type as it arrives, before any body runs and
        // whatever language the body is in: a text into a BOOLEAN or a NUMBER parameter is read as one, and one
        // that is none is the CALL's own refusal (live-verified).
        for (int i = 0; i < params.size(); i++) {
            Object given = variantBinds[i]
                ? CallArgumentTypes.variantText(arguments.get(i), params.get(i).getDataType()) : arguments.get(i);
            if (exactNumbers[i] && given instanceof Double && Double.isFinite((Double) given)
                    && params.get(i).getDataType() instanceof NumericType) {
                // An exact number reaches a NUMBER parameter as the exact number it is, so a value too wide for
                // the parameter is refused as one (see ProceduralExecutor.coerceArgument).
                given = BigDecimal.valueOf((Double) given);
            }
            arguments.set(i, proceduralExecutor.coerceArgument(given, params.get(i).getDataType(),
                language == UdfLanguage.SQL));
        }
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
        SessionRole.enterProcedure(!procedure.runsWithCallersRights());
        // It creates objects as its owner, and with its owner's privilege to create them (live-verified); a
        // caller's-rights body creates as whoever calls it, which inside an owner's-rights body is that one's owner.
        if (!procedure.runsWithCallersRights() && procedure.getOwner() != null
                && queryExecutor.getSecurityManager() != null) {
            queryExecutor.getSecurityManager().getSessionContext().setExecutableOwner(procedure.getOwner());
        }
        // A body that runs with its OWNER's rights runs where the procedure lives, whatever language it is in:
        // an unqualified or partially qualified name in it (UTILS.IS_VALID_TIER, a bare table) — in a SQL body's
        // statement or in one a handler issues — resolves against the procedure's own database and schema, and the
        // caller is back where it was afterwards, also when it was nowhere. The home is the schema the procedure
        // was found in, along the search path for a bare name. A body that runs with its CALLER's rights stays
        // where the caller is, and where it leaves the session is where the caller finds it (live-verified).
        final String savedDb = catalog.getCurrentDatabase();
        final String savedSchema = catalog.getCurrentSchema();
        final boolean callersRights = procedure.runsWithCallersRights();
        final String homeDb = callersRights ? null : schema.getDatabaseName();
        final String homeSchema = callersRights ? null : schema.getName();

        if (language != UdfLanguage.SQL) {
            // A handler's body is a frame of its own as a SQL body is: the statements it issues are no statements
            // of a block that called it — they bind none of its variables, a :name in one is a bind nothing sets,
            // and they re-raise nothing that block is handling (live-verified).
            final int callersFrame = proceduralExecutor.enterDynamicFrame();
            proceduralExecutor.enterIsolatedScope();
            final ProcedureFrame callersBody = proceduralExecutor.enterProcedureFrame();
            boolean handlerFailed = true;
            try {
                enterHome(homeDb, homeSchema);
                final DatabaseEngine host = queryExecutor.getDatabaseEngine();
                // A handler may answer otherwise when called again (see CheckpointDebt).
                CheckpointDebt.owe();
                returnValue = language == UdfLanguage.JAVA
                    ? JavaProcedureExecutor.executeJavaProcedure(procedure, arguments, host)
                    : UdfRuntimes.require(language).executeProcedure(procedure, arguments, host);
                handlerFailed = false;
            } finally {
                proceduralExecutor.exitProcedureFrame(callersBody, handlerFailed);
                proceduralExecutor.exitScope();
                proceduralExecutor.exitDynamicFrame(callersFrame);
                if (homeDb != null) {
                    catalog.restoreContext(savedDb, savedSchema);
                }
            }
        } else {
            // SQL procedural language: bind the call arguments to the parameter names, execute the body
            // on the shared procedural executor (so its RETURN / control flow / statements actually
            // run — not merely logged), and surface the RETURN value. Parameters are declared in a
            // dedicated scope so they don't leak past the CALL.
            //
            // The body runs under its declared RETURNS type (none for a table-returning one), so a
            // RETURN written directly in it converts — see the executor's rule.
            final Set<String> parameterNames = new HashSet<>();
            for (final Parameter param : params) {
                parameterNames.add(param.getName());
            }
            final DeclaredReturnFrame outerReturn = proceduralExecutor.pushDeclaredReturn(
                procedure.returnsTable() ? null : procedure.getReturnType(), procedure.returnsTable(), parameterNames);
            // The body sees its own parameters and declarations and nothing of whoever calls it: a caller's
            // variable is an invalid identifier in it, read, assigned or bound (live-verified).
            proceduralExecutor.enterIsolatedScope();
            // And its places count from its own beginning, not from the statement that called it.
            final SourcePosition callersOrigin = LeadingCommentOffset.begin(null);
            // A statement of a block that calls the procedure sees what the body lets out as its own failure.
            final boolean calledFromABlock = proceduralExecutor.isExecutingBlock();
            final ProcedureFrame callerFrame = proceduralExecutor.enterProcedureFrame();
            // The CALL made the body a transaction scope already: its top block opens none of its own.
            proceduralExecutor.markBodyScoped();
            boolean bodyFailed = false;
            try {
                enterHome(homeDb, homeSchema);
                for (int i = 0; i < params.size(); i++) {
                    proceduralExecutor.markDeclaredInCurrentScope(params.get(i).getName());
                    proceduralExecutor.declareVariableType(params.get(i).getName(), heldType(params.get(i)));
                    proceduralExecutor.setVariable(params.get(i).getName(), arguments.get(i));
                }

                // A body written without a block runs as the one statement of a block around it, BEGIN on a line
                // above it: its failure is that block's statement's, on line 2 (see QueryExecutor#framedBlocklessBody).
                final String framed = queryExecutor.framedBlocklessBody(procedure.getBody());
                final String body = framed == null ? procedure.getBody() : framed;
                final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
                final SyntaxErrorListener errorListener = new SyntaxErrorListener(body);
                lexer.removeErrorListeners();
                lexer.addErrorListener(errorListener);
                final CommonTokenStream tokens = new CommonTokenStream(lexer);
                final FrostlakeParser parser = new FrostlakeParser(tokens);
                parser.removeErrorListeners();
                parser.addErrorListener(errorListener);
                final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
                // A body whose only faults are calls written as its statements is refused for them as it runs.
                BlockCallStatementReading.requireBefore(body, errorListener, queryExecutor.getFunctionRegistry());
                errorListener.throwIfErrors();
                StageArgumentSyntax.requirePlacement(tree, body);
                NamedCallRewrite.apply(tree);
                if (callersRights && SessionRole.runsWithOwnersRights()) {
                    refuseSessionVariablesUnderOwnersRights(tree);
                }

                Object bodyResult = null;
                // Only an OWNER's-rights procedure may not touch the session it runs in; an
                // EXECUTE AS CALLER body changes session state freely (live-verified), so the
                // session-statement refusal is armed for owner's-rights bodies alone.
                final boolean ownersRights = !procedure.runsWithCallersRights();
                if (ownersRights) {
                    procedureDepth++;
                }
                try {
                for (final FrostlakeParser.StatementContext stmtCtx : flattenedStatements(tree)) {
                    StatementClock.advance();
                    // A statement of a body written without a block is a child statement of the session's, as a
                    // block's statement is (see BeginEndBlockHandler#isChildStatement).
                    final String statementText = getOriginalText(stmtCtx);
                    final QueryHistory child = BeginEndBlockHandler.isChildStatement(stmtCtx)
                        ? queryExecutor.beginChildStatement(statementText, stmtCtx) : null;
                    final Object visited;
                    try {
                        visited = visit(stmtCtx);
                    } catch (final RuntimeException failed) {
                        queryExecutor.failChildStatement(child, statementText, failed);
                        throw failed;
                    }
                    queryExecutor.completeChildStatement(child, statementText,
                        visited instanceof ResultSet ? (ResultSet) visited : null);
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
                bodyFailed = true;
                throw calledFromABlock ? CalledProcedureFailure.of(e) : e;
            } catch (final Exception e) {
                bodyFailed = true;
                final RuntimeException failure = StatementErrors.propagate(e);
                throw calledFromABlock ? failureOfABody(failure) : failure;
            } finally {
                proceduralExecutor.exitProcedureFrame(callerFrame, bodyFailed);
                proceduralExecutor.restoreDeclaredReturn(outerReturn);
                LeadingCommentOffset.end(callersOrigin);
                proceduralExecutor.exitScope();
                if (homeDb != null) {
                    // Restore the caller's context WITHOUT re-validating it: this runs while unwinding, so a
                    // throw here would replace the CALL's real result (or its real error) with a spurious one
                    // that the procedure's own EXCEPTION handler never sees.
                    catalog.restoreContext(savedDb, savedSchema);
                }
            }
        }

        logger.trace("Executed CALL statement for: {}", procName);

        if (procedure.returnsTable()) {
            // A handler declared RETURNS TABLE hands back a DataFrame, whose rows are the CALL's - under the
            // declared columns, as a SQL body's returned table is; a handler that hands nothing back is refused
            // (live-verified).
            if (returnValue instanceof DataFrame) {
                return ReturnedTable.shape(procedure.getReturnColumns(), ((DataFrame) returnValue).getResultSet());
            }
            if (returnValue == null && (language == UdfLanguage.JAVA || language == UdfLanguage.SCALA)) {
                throw new RuntimeException("NULL result in a non-nullable column of type TEXT in function "
                    + procedure.getName() + " with handler " + procedure.getHandler());
            }
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
        // A handler's CALL answers what a SQL body's does: one row in a column named after the procedure and
        // typed by RETURNS — a NULL when the handler hands nothing back — holding the handler's value as that
        // type (live-verified). A JavaScript and a Python runtime convert by their own languages' rules; a Java
        // or a Scala handler's object is converted here.
        Object answer = returnValue;
        if (language == UdfLanguage.JAVA || language == UdfLanguage.SCALA) {
            answer = HandlerResults.fromJava(returnValue, procedure.getReturnType(), procedure.getName(),
                procedure.getHandler());
        }
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(procName, handlerCallColumnType(procedure), null));
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(answer));
        return new ResultSet(columns, rows);
    }

    @Override
    public Object visitExecuteImmediateStatement(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        // Texts run texts only so deep (see StatementNesting).
        StatementNesting.enter();
        try {
            return executeImmediate(ctx);
        } finally {
            StatementNesting.exit();
        }
    }

    /** How a block refuses to run a text that is NULL or holds no statement. */
    private static final String EMPTY_DYNAMIC_STATEMENT = "Executing NULL or empty statement is not permitted.";

    private Object executeImmediate(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        SourcePosition requestOrigin = null;
        boolean readsTheText = false;
        BoundText displacedBinds = null;
        boolean boundTheText = false;
        final List<int[]> written = new ArrayList<>();
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
                    throw new RuntimeException(proceduralExecutor.isExecutingBlock()
                        ? EMPTY_DYNAMIC_STATEMENT : "EXECUTE IMMEDIATE: SQL expression evaluated to null");
                }

                sqlString = sqlObj.toString();
                if (QueryExecutor.holdsNoStatement(sqlString)) {
                    // A text of no statement at all — blank, comments or semicolons alone — is refused before it
                    // is read: by a block as the statement's failure, by a request as the empty statement it is
                    // (live-verified).
                    throw new RuntimeException(proceduralExecutor.isExecutingBlock()
                        ? EMPTY_DYNAMIC_STATEMENT : SqlCompilationError.of("Empty SQL statement."));
                }

                // Bind USING (...) values to the ? placeholders in turn and to :n by number; a FOR loop's record
                // binds nothing, and its placeholder stays unset. The text reads none of the block's variables
                // (see DynamicStatementBinds).
                final List<Object> bindValues = new ArrayList<>();
                final Set<Integer> unbound = new HashSet<>();
                for (final FrostlakeParser.UsingArgumentContext argument : ctx.usingArgument()) {
                    final String name = usingVariableName(argument);
                    if (!proceduralExecutor.hasVariable(name) && proceduralExecutor.isLoopRecord(name)) {
                        unbound.add(bindValues.size() + 1);
                    }
                    bindValues.add(proceduralExecutor.usingBind(name, usingValue(argument)));
                }
                if (proceduralExecutor.isExecutingBlock() || !bindValues.isEmpty()) {
                    sqlString = DynamicStatementBinds.bind(sqlString, bindValues, unbound, written);
                }
            }

            // The text's places are its own: a comment that opens the request re-bases the request's positions,
            // never those inside the text. A text the request hands over itself is placed as it is written, a
            // comment that opens it re-basing nothing; a text a block runs is a statement of its own, placed as a
            // request is, a comment that opens it not counted (live-verified).
            final SourcePosition commentOrigin = proceduralExecutor.isExecutingBlock()
                ? LeadingCommentOffset.of(sqlString) : null;
            // A text whose placeholders were written out has an origin of its own, which tells its places from
            // those of what it runs: a refusal is placed in the text as written (see BoundText).
            final SourcePosition textOrigin = commentOrigin == null && !written.isEmpty()
                ? new SourcePosition(1, 0) : commentOrigin;
            requestOrigin = LeadingCommentOffset.begin(textOrigin);
            if (!written.isEmpty()) {
                displacedBinds = LeadingCommentOffset.bind(new BoundText(textOrigin, written));
                boundTheText = true;
            }
            readsTheText = true;

            // Parse and execute the dynamic SQL
            final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sqlString));

            final SyntaxErrorListener errorListener = new SyntaxErrorListener(sqlString);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            final FrostlakeParser.SqlScriptContext sqlScriptCtx = parser.sqlScript();
            // A text whose only faults are calls written as a block's statements is refused for them.
            BlockCallStatementReading.requireBefore(sqlString, errorListener, queryExecutor.getFunctionRegistry());
            if (errorListener.hasErrors()) {
                // A refused text is read for its faults as a refused statement is: again, with a prediction cache
                // of its own, and as a whole statement unless it is a block, whose statements are read as a
                // block's are.
                final SyntaxErrorListener coldListener = new SyntaxErrorListener(sqlString);
                tokens.seek(0);
                final int opener = tokens.LA(1);
                if (opener != FrostlakeParser.BEGIN && opener != FrostlakeParser.DECLARE) {
                    coldListener.statementParse();
                }
                final FrostlakeLexer coldLexer = new FrostlakeLexer(SqlCharStream.of(sqlString));
                coldLexer.removeErrorListeners();
                coldLexer.addErrorListener(coldListener);
                final CommonTokenStream coldTokens = new CommonTokenStream(coldLexer);
                final FrostlakeParser coldParser = new FrostlakeParser(coldTokens);
                ColdPrediction.arm(coldParser);
                coldParser.removeErrorListeners();
                coldParser.addErrorListener(coldListener);
                final FrostlakeParser.SqlScriptContext coldScript = coldParser.sqlScript();
                ScriptingStatementPlacement.requireSqlStatementsBefore(coldScript, coldTokens, sqlString, coldListener);
                IntoListLookahead.requireBefore(coldScript, coldTokens, sqlString, coldListener);
                EmptySchemaPartSyntax.requireWellFormedBefore(coldScript, coldTokens, sqlString, coldListener);
                PostParseRefusals.requireBefore(coldTokens, sqlString, coldListener);
                RoutineArgumentOrder.requireBefore(coldTokens, coldListener);
                SignatureTailLine.requireBefore(coldScript, sqlString, coldListener);
                coldListener.throwIfErrors();
            }
            ScriptingStatementPlacement.requireSqlStatementsBefore(sqlScriptCtx, tokens, sqlString, errorListener);
            IntoListLookahead.requireBefore(sqlScriptCtx, tokens, sqlString, errorListener);
            EmptySchemaPartSyntax.requireWellFormedBefore(sqlScriptCtx, tokens, sqlString, errorListener);
            PostParseRefusals.requireBefore(tokens, sqlString, errorListener);
            RoutineArgumentOrder.requireBefore(tokens, errorListener);
            SignatureTailLine.requireBefore(sqlScriptCtx, sqlString, errorListener);
            errorListener.throwIfErrors();
            EmptySchemaPartSyntax.requireWellFormed(sqlScriptCtx, tokens, sqlString);
            FinishedPredicateSyntax.requireOpenOperands(sqlScriptCtx, tokens, sqlString);
            PositionNeedleSyntax.requireValueNeedles(sqlScriptCtx, tokens, sqlString);
            AnsiFromFormSyntax.requireCallForms(sqlScriptCtx, tokens, sqlString);
            ValueFunctionSyntax.requireValueFunctionForms(sqlScriptCtx, sqlString);
            StageArgumentSyntax.requireTextSyntax(sqlScriptCtx, sqlString, textOrigin);
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
                StageArgumentSyntax.requirePlacementFrom(sqlScriptCtx, sqlString, textOrigin);
                OverCallRules.requireOperands(sqlScriptCtx, queryExecutor.getFunctionRegistry());
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
            // A text of several statements runs outside a transaction the request has open: each of its statements
            // commits at its own end, a ROLLBACK of that transaction leaves them be, and the transaction is as it
            // was once the text has run (live-verified).
            final TransactionManager transactions = queryExecutor.getTransactionManager();
            final SetAsideTransaction outerTransaction = severalStatements && transactions.isExplicitTransaction()
                ? transactions.setAsideTransaction() : null;
            final int enclosingFrame = proceduralExecutor.enterDynamicFrame();
            // The text sees none of the variables of a block that runs it, read, assigned or bound, and nothing
            // that block's handler is handling: what it needs is handed to it by USING (live-verified).
            // Its top block words what it lets out, as a called procedure's does, and the statement that runs the
            // text names that failure at its own place.
            final boolean runByABlock = proceduralExecutor.isExecutingBlock();
            proceduralExecutor.enterIsolatedScope();
            final ProcedureFrame enclosingBody = proceduralExecutor.enterProcedureFrame();
            boolean textFailed = true;
            try {
                for (final FrostlakeParser.StatementContext stmtCtx : dynamicStatements) {
                    // Only the last statement's conditional branch may reach the status sentence.
                    ConditionalDdlOutcome.clear();
                    // A statement of a text of several reports its places against its own text, and fails named
                    // the way a multi-statement request names it.
                    final SourcePosition displaced = severalStatements ? LeadingCommentOffset.begin(new SourcePosition(
                        stmtCtx.getStart().getLine(), stmtCtx.getStart().getCharPositionInLine())) : null;
                    // A statement of the text is a child statement of the session's, with a query ID and a QUERY_HISTORY
                    // row of its own (see QueryExecutor#beginChildStatement). A block's answer is not a statement of
                    // its own: it is this EXECUTE IMMEDIATE's answer, which the statement records once, as a block run
                    // directly records it; the block's statements record themselves.
                    final String statementText = getOriginalText(stmtCtx);
                    final QueryHistory child = AnonymousBlockResult.isBlock(stmtCtx) ? null
                        : queryExecutor.beginChildStatement(statementText, stmtCtx);
                    try {
                        if (severalStatements) {
                            // A statement of several compiles when it runs, after the ones before it have run, and
                            // fails named for what it earns there (live-verified).
                            StageArgumentSyntax.requireArguments(stmtCtx);
                            OverCallRules.requireOperands(stmtCtx, queryExecutor.getFunctionRegistry());
                            IntervalLiteralSyntax.requireReadable(stmtCtx);
                            NamedCallRewrite.apply(stmtCtx);
                        }
                        LimitValueSyntax.requireNumericValuesAsWritten(stmtCtx);
                        IntoClausePlacement.rejectInStatement(stmtCtx, false);
                        lastResult = visit(stmtCtx);
                    } catch (final RuntimeException failed) {
                        queryExecutor.failChildStatement(child, statementText, failed);
                        throw severalStatements
                            ? MultiStatementFailure.wrappingInText(sqlScriptCtx, stmtCtx, failed, textOrigin) : failed;
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
                    // Its query ID holds its result, so RESULT_SCAN(LAST_QUERY_ID()) works after SHOW / SELECT
                    // statements. Unrecorded SQL hands out no query IDs (child is null then).
                    queryExecutor.completeChildStatement(child, statementText,
                        lastResult instanceof ResultSet ? (ResultSet) lastResult : null);
                    if (severalStatements) {
                        // A statement of several is a statement of its own: what it changed stays when a later
                        // one fails (live-verified).
                        transactions.autocommitStatementEnd();
                    }
                }
                // An EXECUTE IMMEDIATE of an anonymous block answers what the block answers run
                // directly: one row, NULL when the block finished without a RETURN. Asked INSIDE the
                // frame, so the caller's own block does not read as this text's.
                if (lastResult == null && !proceduralExecutor.isExecutingOwnBlock()
                        && AnonymousBlockResult.isBlock(lastStatement)) {
                    lastResult = AnonymousBlockResult.withoutReturn();
                }
                textFailed = false;
            } catch (final RuntimeException failed) {
                throw runByABlock ? failureOfABody(failed) : failed;
            } finally {
                proceduralExecutor.exitProcedureFrame(enclosingBody, textFailed);
                proceduralExecutor.exitScope();
                proceduralExecutor.exitDynamicFrame(enclosingFrame);
                if (outerTransaction != null) {
                    transactions.resumeTransaction(outerTransaction);
                }
            }
            // A script file answers with its last statement's result, however many it holds; dynamic
            // text of several statements answers as a multi-statement request instead.
            dynamicStatement = ctx.FROM() != null || dynamicStatements.size() == 1
                ? describedStatement(lastStatement) : null;

            logger.trace("Executed EXECUTE IMMEDIATE: {}", sqlString.length() > 50 ? sqlString.substring(0, 50) + "..." : sqlString);
            return severalStatements ? new MultiStatementAnswer(statementAnswers) : lastResult;

        } catch (final Exception e) {
            if (readsTheText) {
                // The refusal is placed in the request again: by the statement that ran the text.
                if (boundTheText) {
                    LeadingCommentOffset.unbind(displacedBinds);
                    boundTheText = false;
                }
                LeadingCommentOffset.end(requestOrigin);
                readsTheText = false;
            }
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw StatementErrors.propagate(e);
        } finally {
            if (boundTheText) {
                LeadingCommentOffset.unbind(displacedBinds);
            }
            if (readsTheText) {
                LeadingCommentOffset.end(requestOrigin);
            }
        }
    }

    /**
     * What a statement of a block sees of a failure that a body it ran — a called procedure's, a dynamic text's —
     * let out: a failed statement of its own. An exception the body's script raised keeps its code under that
     * (see {@link CalledProcedureFailure}), and a name the body could not resolve, which the body itself reports
     * bare, is to the calling block the failure of the statement that ran the body (live-verified).
     */
    private static RuntimeException failureOfABody(final RuntimeException failure) {
        // A FETCH from a cursor that is not open is no exception of the body it happens in, but to the block that
        // called the body it is the statement's failure: its OTHER handler catches it, SQLCODE 92201
        // (live-verified). A body ended for its depth stays past every handler.
        if (failure instanceof UndeclaredScriptVariableException || failure instanceof HandlerLetAbort
                || failure instanceof BodyAbort && !(failure instanceof CallDepthExceeded)) {
            // What ended the body is, to the statement that ran it, a failure a handler there may catch.
            return new RuntimeException(failure.getMessage(), failure);
        }
        return CalledProcedureFailure.of(failure);
    }

    /**
     * The value an EXECUTE IMMEDIATE's USING argument binds: the variable it names. The block judged every name while
     * it compiled; a name it let through that holds no value — SQLCODE outside a handler, SQLROWCOUNT before any DML
     * statement — binds NULL, and a loop's record binds nothing, its placeholder left unset.
     */
    private Object usingValue(final FrostlakeParser.UsingArgumentContext argument) {
        final String name = usingVariableName(argument);
        return proceduralExecutor.hasVariable(name) ? proceduralExecutor.readVariable(name) : null;
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
        // The script is read as the file holds it now (see CheckpointDebt).
        CheckpointDebt.owe();
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
     * The canonical text of an argument's or a script variable's name: an identifier folds as any identifier does, one
     * of the words such a name takes upper-cases.
     *
     * @param ctx the name
     * @return its canonical text
     */
    public String getText(final FrostlakeParser.VariableNameContext ctx) {
        return SqlIdentifiers.canonical(ctx);
    }

    /**
     * The canonical text of an assignment's target.
     *
     * @param ctx the target
     * @return its canonical text
     */
    public String getText(final FrostlakeParser.AssignedNameContext ctx) {
        return ParseTreeText.getIdentifier(ctx);
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
     * The type a SCRIPTING declaration writes. It differs from a column's in two places: an unsized
     * BINARY binds at the type's 64MB maximum, where a column's unsized BINARY is BINARY(8388608), and an
     * unsized VARCHAR, STRING or TEXT at VARCHAR(134217728), where a column's is VARCHAR(16777216) —
     * so the width that was WRITTEN is what tells them apart, and an explicit BINARY(8388608) or
     * VARCHAR(16777216) keeps its own width (live-verified).
     *
     * @param name       the type as written
     * @param parameters its parameters, or null when none were written
     * @return the declared type
     */
    private DataType parseDeclaredType(final FrostlakeParser.DataTypeNameContext name,
                                       final FrostlakeParser.TypeParametersContext parameters) {
        final DataType declared = parseDataType(name, parameters);
        if (parameters == null && declared instanceof StringType && !(declared instanceof UuidType)
                && ((StringType) declared).getMaxLength() == StringType.VARCHAR.getMaxLength()) {
            // A text declared without a length is held at the width nothing bounds, where a column's is 16MB:
            // a NULL it binds is a VARCHAR(134217728), and an explicit VARCHAR(16777216) keeps its own width.
            return new StringType("VARCHAR", StringResultWidths.UNBOUNDED);
        }
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

    /** The session's current schema, refused as a statement with no current database, then schema, is refused. */
    public Schema resolveCurrentSchema() {
        if (catalog.getCurrentDatabase() == null) {
            throw NoCurrentDatabaseRefusal.forStatement();
        }
        if (catalog.getCurrentSchema() == null) {
            throw NoCurrentDatabaseRefusal.schemaForStatement();
        }
        return catalog.databaseExact(catalog.getCurrentDatabase()).schemaExact(catalog.getCurrentSchema());
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
     * A typed declaration's initial value. A DECFLOAT takes a decimal literal by its digits — DECLARE y DECFLOAT
     * DEFAULT 0.1 holds 0.1, not the double nearest it (live-verified) — where every other type reads the literal
     * as scripting does.
     *
     * @param ctx      the initialiser
     * @param declared the declared type
     * @return the value
     */
    private Object declaredInitialValue(final FrostlakeParser.BooleanExprContext ctx, final DataType declared) {
        if (NumericType.isDecfloat(declared) && ctx instanceof FrostlakeParser.ValueExprContext) {
            FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) ctx).expression();
            boolean negated = false;
            if (value instanceof FrostlakeParser.UnaryExprContext) {
                negated = ((FrostlakeParser.UnaryExprContext) value).MINUS() != null;
                value = ((FrostlakeParser.UnaryExprContext) value).expression();
            }
            if (value instanceof FrostlakeParser.LiteralExprContext
                    && ((FrostlakeParser.LiteralExprContext) value).literal().FLOAT_LITERAL() != null) {
                final BigDecimal exact = NumericLiteralTypes.exactValue(
                    ((FrostlakeParser.LiteralExprContext) value).literal().FLOAT_LITERAL().getText());
                return negated ? exact.negate() : exact;
            }
        }
        return evaluateExpression(ctx);
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
        // The function's names are its own: no handler of the statement that called it hides one of them.
        final HandlerLets callersHandlers = proceduralExecutor.setAsideHandlerLets();
        // The body runs in the MIDDLE of the statement that called the function, so its block's
        // per-statement autocommit must not commit that statement's write set — the same guard a
        // procedure called from a FROM clause needs. A UDF cannot run DML at all (the CREATE-time check
        // refuses it), so this only ever protects the CALLER's transaction.
        queryExecutor.getTransactionManager().beginAtomicSection();
        // The body's places count from its own beginning, as a procedure's do: a refusal in it names where it
        // stands in the function's text, whatever statement called the function (live-verified).
        final SourcePosition callersOrigin = LeadingCommentOffset.begin(null);
        proceduralExecutor.enterFunctionBody();
        try {
            final List<Parameter> params = function.getParameters();
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                proceduralExecutor.markDeclaredInCurrentScope(params.get(i).getName());
                proceduralExecutor.declareVariableType(params.get(i).getName(),
                    params.get(i).getDataType());
                proceduralExecutor.setVariable(params.get(i).getName(), args.get(i));
            }

            final String body = function.getBody();
            final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
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

            // The body is judged as any body is when it starts to run: a BREAK or a CONTINUE of it stands in a
            // loop of its own, and none reaches a loop of the statement that called the function. Its refusal keeps
            // the body's place (see BodyPlacedError).
            try {
                LoopControlPlacement.require(tree);
            } catch (final RuntimeException misplaced) {
                throw new BodyPlacedError(misplaced);
            }
            proceduralExecutor.clearLoopControl();
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
        } catch (final RuntimeException failed) {
            if (!(failed instanceof ProceduralException) && !(failed instanceof FunctionBodyCompileError)
                    && "EXPRESSION_ERROR".equals(proceduralExecutor.getFailureKind())
                    && SqlCompilationError.isCompilationError(failed.getMessage())) {
                // The body compiles with the statement that called the function (see FunctionBodyCompileError).
                throw new FunctionBodyCompileError(failed);
            }
            throw failed;
        } finally {
            proceduralExecutor.exitFunctionBody();
            LeadingCommentOffset.end(callersOrigin);
            queryExecutor.getTransactionManager().endAtomicSection();
            proceduralExecutor.restoreHandlerLets(callersHandlers);
            proceduralExecutor.exitScope();
            proceduralExecutor.clearLoopControl();
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
