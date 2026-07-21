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

import dev.frostlake.executor.commands.*;
import dev.frostlake.executor.procedural.*;
import dev.frostlake.executor.udf.JavaProcedureExecutor;
import dev.frostlake.executor.udf.JavaScriptProcedureExecutor;
import dev.frostlake.executor.udf.PythonProcedureExecutor;
import dev.frostlake.executor.udf.ScalaProcedureExecutor;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.*;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.parser.FrostlakeBaseVisitor;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import dev.frostlake.task.TaskScheduler;
import dev.frostlake.types.*;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
    private final GrantRevokeHandler grantRevokeHandler;
    private final BeginEndBlockHandler beginEndBlockHandler;

    // Canonical column/type/literal parser — shared with DDL so type parsing (NUMBER(p,s),
    // VARCHAR(len), TINYINT, UUID, VECTOR, …) stays in one place rather than a divergent copy.
    private final ColumnDefinitionParser columnParser;
    private final ProceduralBlockBuilder proceduralBlockBuilder;
    private final VisitorExpressionBuilder expressionBuilder;

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
        this.grantRevokeHandler = new GrantRevokeHandler(catalog, queryExecutor, this);
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

    @Override
    public Object visitStatement(final FrostlakeParser.StatementContext ctx) {
        return visitChildren(ctx);
    }

    // ==================== DDL STATEMENTS ====================

    @Override
    public Object visitCreateStatement(final FrostlakeParser.CreateStatementContext ctx) {
        try {
            return ddlHandler.handleCreateStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute CREATE statement: " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitDropStatement(final FrostlakeParser.DropStatementContext ctx) {
        try {
            return ddlHandler.handleDropStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute DROP statement: " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitAlterStatement(final FrostlakeParser.AlterStatementContext ctx) {
        return alterHandler.handle(ctx);
    }

    @Override
    public Object visitUseStatement(final FrostlakeParser.UseStatementContext ctx) {
        try {
            return ddlHandler.handleUseStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute USE statement: " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitUndropStatement(final FrostlakeParser.UndropStatementContext ctx) {
        return ddlHandler.handleUndropStatement(ctx);
    }

    @Override
    public Object visitExplainStatement(final FrostlakeParser.ExplainStatementContext ctx) {
        final ParserRuleContext stmt = ctx.selectStatement() != null
            ? ctx.selectStatement() : ctx.dmlStatement();
        final List<ResultSetColumn> columns = Arrays.asList(
            new ResultSetColumn("step", NumericType.INTEGER, null),
            new ResultSetColumn("operation", StringType.VARCHAR, null),
            new ResultSetColumn("objects", StringType.VARCHAR, null));
        final List<Row> rows = new ArrayList<>();
        int step = 1;
        // Top-down plan: Result, then the operators present in the statement, down to the table scans.
        rows.add(new Row(step++, "Result", ""));
        if (hasRuleContext(stmt, FrostlakeParser.LimitClauseContext.class)) {
            rows.add(new Row(step++, "Limit", ""));
        }
        if (hasRuleContext(stmt, FrostlakeParser.OrderByClauseContext.class)) {
            rows.add(new Row(step++, "Sort", ""));
        }
        if (hasRuleContext(stmt, FrostlakeParser.GroupByClauseContext.class)) {
            rows.add(new Row(step++, "Aggregate", ""));
        }
        if (hasRuleContext(stmt, FrostlakeParser.WhereClauseContext.class)) {
            rows.add(new Row(step++, "Filter", ""));
        }
        if (hasRuleContext(stmt, FrostlakeParser.JoinClauseContext.class)) {
            rows.add(new Row(step++, "Join", ""));
        }
        final List<String> tables = new ArrayList<>();
        collectScannedTables(stmt, tables);
        if (tables.isEmpty()) {
            rows.add(new Row(step++, "TableScan", ""));
        } else {
            for (final String t : tables) {
                rows.add(new Row(step++, "TableScan", t));
            }
        }
        return new ResultSet(columns, rows);
    }

    /** True if the parse tree rooted at {@code ctx} contains a context of the given rule type. */
    private boolean hasRuleContext(final ParserRuleContext ctx, final Class<? extends ParserRuleContext> type) {
        if (type.isInstance(ctx)) {
            return true;
        }
        for (int i = 0; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext && hasRuleContext((ParserRuleContext) child, type)) {
                return true;
            }
        }
        return false;
    }

    /** Collect the qualified names of tables scanned (FROM table sources) anywhere under {@code ctx}. */
    private void collectScannedTables(final ParserRuleContext ctx, final List<String> out) {
        if (ctx instanceof FrostlakeParser.TableSourceContext) {
            final FrostlakeParser.TableSourceContext ts = (FrostlakeParser.TableSourceContext) ctx;
            if (ts.qualifiedName() != null) {
                out.add(getText(ts.qualifiedName()));
            }
        }
        for (int i = 0; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (child instanceof ParserRuleContext) {
                collectScannedTables((ParserRuleContext) child, out);
            }
        }
    }

    @Override
    public Object visitCommentStatement(final FrostlakeParser.CommentStatementContext ctx) {
        return commentHandler.handle(ctx);
    }

    @Override
    public Object visitTruncateStatement(final FrostlakeParser.TruncateStatementContext ctx) {
        try {
            boolean ifExists = ctx.if_exists() != null;
            String tableName = getText(ctx.qualifiedName());

            // Execute truncate via query executor
            queryExecutor.executeTruncate(tableName, ifExists);

            return null;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute TRUNCATE statement: " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitTransactionStatement(final FrostlakeParser.TransactionStatementContext ctx) {
        if (ctx.BEGIN() != null) {
            // Explicit BEGIN starts (or promotes the current auto-started) transaction to an explicit one,
            // which suspends statement-end autocommit until COMMIT/ROLLBACK (Snowflake semantics).
            queryExecutor.getTransactionManager().beginExplicitTransaction();
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

    public Object visitSecurityStatement(final FrostlakeParser.SecurityStatementContext ctx) {
        if (ctx.grantStatement() != null) {
            return visitGrantStatement(ctx.grantStatement());
        } else if (ctx.revokeStatement() != null) {
            return visitRevokeStatement(ctx.revokeStatement());
        }
        return null;
    }

    @Override
    public Object visitTaskStatement(final FrostlakeParser.TaskStatementContext ctx) {
        try {
            // EXECUTE TASK task_name
            String taskName = getText(ctx.qualifiedName());

            if (taskScheduler == null) {
                throw new RuntimeException("Task scheduler not initialized");
            }

            // Execute the task manually (handles qualified names internally)
            taskScheduler.executeTaskManually(taskName);
            logger.trace("Executed task: {}", taskName);

            return null;
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute task: " + e.getMessage(), e);
        }
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
        try {
            String stageName = getText(ctx.qualifiedName());
            String pattern = null;

            if (ctx.PATTERN() != null) {
                pattern = extractStringLiteral(ctx.STRING_LITERAL());
            }

            Stage stage = catalog.getStage(stageName);
            List<StageFile> files = stage.listFiles(pattern);

            // Build ResultSet
            List<ResultSetColumn> columns = new ArrayList<>();
            columns.add(new ResultSetColumn("name", StringType.VARCHAR, null));
            columns.add(new ResultSetColumn("size", NumericType.BIGINT, null));
            columns.add(new ResultSetColumn("md5", StringType.VARCHAR, null));
            columns.add(new ResultSetColumn("last_modified", StringType.VARCHAR, null));

            List<Row> rows = new ArrayList<>();
            for (final StageFile file : files) {
                rows.add(new Row(
                    file.getName(),
                    file.getSize(),
                    file.getMd5(),
                    file.getLastModified()
                ));
            }

            logger.trace("Listed {} files in stage: {}", files.size(), stageName);
            return new ResultSet(columns, rows);

        } catch (final IOException e) {
            throw new RuntimeException("Failed to list stage: " + e.getMessage(), e);
        }
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

    // ==================== DML STATEMENTS ====================

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
            throw new RuntimeException("Failed to execute SHOW statement: " + e.getMessage(), e);
        }
    }

    @Override
    public Object visitDescribeStatement(final FrostlakeParser.DescribeStatementContext ctx) {
        try {
            return showHandler.handleDescribeStatement(ctx);
        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute DESCRIBE statement: " + e.getMessage(), e);
        }
    }

    // ==================== PROCEDURAL STATEMENTS ====================

    @Override
    public Object visitProceduralStatement(final FrostlakeParser.ProceduralStatementContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Object visitDeclareStatement(final FrostlakeParser.DeclareStatementContext ctx) {
        for (final FrostlakeParser.DeclarationItemContext itemCtx : ctx.declarationItem()) {
            visit(itemCtx);
        }
        logger.trace("Processed DECLARE statement");
        return null;
    }

    @Override
    public Object visitDeclarationItem(final FrostlakeParser.DeclarationItemContext ctx) {
        // Check which type of declaration this is
        if (ctx.EXCEPTION() != null) {
            // Exception declaration
            String exceptionName = getText(ctx.identifier());
            Object errorCodeValue = evaluateExpression(ctx.expression());
            int errorCode = ((Number) errorCodeValue).intValue();
            String message = extractStringLiteral(ctx.STRING_LITERAL());

            DeclareExceptionStatement stmt = new DeclareExceptionStatement(exceptionName, errorCode, message);
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared exception: {}", exceptionName);
        } else if (ctx.CURSOR() != null) {
            // Cursor declaration — over a SELECT query or the name of a RESULTSET variable.
            String cursorName = getText(ctx.identifier());
            FrostlakeParser.CursorSourceContext src = ctx.cursorSource();
            DeclareCursorStatement stmt;
            if (src.selectStatement() != null) {
                stmt = new DeclareCursorStatement(cursorName, getOriginalText(src.selectStatement()));
            } else {
                stmt = new DeclareCursorStatement(cursorName, null, getText(src.identifier()));
            }
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared cursor: {}", cursorName);
        } else if (ctx.RESULTSET() != null) {
            // ResultSet declaration
            String resultSetName = getText(ctx.identifier());
            String selectQuery = null;

            if (ctx.selectStatement() != null) {
                selectQuery = getOriginalText(ctx.selectStatement());
            }

            DeclareResultSetStatement stmt = new DeclareResultSetStatement(resultSetName, selectQuery);
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared resultset: {}", resultSetName);
        } else {
            // Variable declaration
            String varName = getText(ctx.identifier());
            Object defaultValue = null;

            if (ctx.DEFAULT() != null || ctx.COLON_EQ() != null) {
                defaultValue = evaluateExpression(ctx.expression());
            }

            DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
                ctx.dataTypeName() != null ? parseDataType(ctx.dataTypeName(), ctx.typeParameters()) : null);
            proceduralExecutor.executeStatement(stmt);

            logger.trace("Declared variable: {}", varName);
        }
        return null;
    }

    @Override
    public Object visitVariableDeclaration(final FrostlakeParser.VariableDeclarationContext ctx) {
        String varName = getText(ctx.identifier());
        Object defaultValue = null;

        if (ctx.DEFAULT() != null || ctx.COLON_EQ() != null) {
            defaultValue = evaluateExpression(ctx.expression());
        }

        DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
            ctx.dataTypeName() != null ? parseDataType(ctx.dataTypeName(), ctx.typeParameters()) : null);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Declared variable: {}", varName);
        return null;
    }

    @Override
    public Object visitCursorDeclaration(final FrostlakeParser.CursorDeclarationContext ctx) {
        String cursorName = getText(ctx.identifier());
        String selectQuery = getOriginalText(ctx.selectStatement());

        DeclareCursorStatement stmt = new DeclareCursorStatement(cursorName, selectQuery);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Declared cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitResultSetDeclaration(final FrostlakeParser.ResultSetDeclarationContext ctx) {
        String resultSetName = getText(ctx.identifier());
        String selectQuery = null;

        if (ctx.selectStatement() != null) {
            selectQuery = getOriginalText(ctx.selectStatement());
        }

        DeclareResultSetStatement stmt = new DeclareResultSetStatement(resultSetName, selectQuery);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Declared resultset: {}", resultSetName);
        return null;
    }

    @Override
    public Object visitExceptionDeclaration(final FrostlakeParser.ExceptionDeclarationContext ctx) {
        String exceptionName = getText(ctx.identifier());
        int errorCode = Integer.parseInt(ctx.INTEGER_LITERAL().getText());
        String message = extractStringLiteral(ctx.STRING_LITERAL());

        DeclareExceptionStatement stmt = new DeclareExceptionStatement(exceptionName, errorCode, message);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Declared exception: {}", exceptionName);
        return null;
    }

    @Override
    public Object visitSetStatement(final FrostlakeParser.SetStatementContext ctx) {
        String varName = getText(ctx.identifier());

        // Check if expression exists (might be null for non-procedural SET statements)
        if (ctx.expression() == null) {
            logger.trace("Non-procedural SET statement, skipping: {}", varName);
            return null;
        }

        // Special handling for autocommit setting
        if (varName.equalsIgnoreCase("autocommit")) {
            Object value = evaluateExpression(ctx.expression());
            boolean autoCommitValue;
            if (value instanceof Boolean) {
                autoCommitValue = (Boolean) value;
            } else if (value instanceof String) {
                String strValue = (String) value;
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

        BaseExpression expr = buildExpression(ctx.expression());
        SetStatement stmt = new SetStatement(varName, expr);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Set variable: {}", varName);
        return null;
    }

    @Override
    public Object visitSessionSetStatement(final FrostlakeParser.SessionSetStatementContext ctx) {
        SecurityManager sm = queryExecutor.getSecurityManager();
        if (ctx.identifierList() != null) {
            // SET (var1, var2, ...) = (expr1, expr2, ...)
            List<FrostlakeParser.IdentifierContext> ids = ctx.identifierList().identifier();
            List<FrostlakeParser.ExpressionContext> exprs = ctx.expressionList().expression();
            for (int i = 0; i < ids.size(); i++) {
                String name = getText(ids.get(i)).toUpperCase();
                Object value = i < exprs.size() ? evaluateExpression(exprs.get(i)) : null;
                setSessionVar(sm, name, value);
            }
        } else {
            // SET var = expr
            String name = getText(ctx.identifier()).toUpperCase();
            Object value = evaluateExpression(ctx.expression());
            setSessionVar(sm, name, value);
        }
        return null;
    }

    private void setSessionVar(final SecurityManager sm, final String name, final Object value) {
        if (sm != null) {
            sm.getSessionContext().setSessionParameter(name, value);
        } else {
            queryExecutor.getSessionVariables().put(name, value);
        }
        logger.trace("SET session variable {}={}", name, value);
    }

    @Override
    public Object visitSessionUnsetStatement(final FrostlakeParser.SessionUnsetStatementContext ctx) {
        SecurityManager sm = queryExecutor.getSecurityManager();
        List<String> names = new ArrayList<>();
        if (ctx.identifier() != null) {
            names.add(getText(ctx.identifier()).toUpperCase());
        } else if (ctx.identifierList() != null) {
            for (final FrostlakeParser.IdentifierContext id : ctx.identifierList().identifier()) {
                names.add(getText(id).toUpperCase());
            }
        }
        for (final String name : names) {
            if (sm != null) {
                sm.getSessionContext().unsetSessionParameter(name);
            } else {
                queryExecutor.getSessionVariables().remove(name);
            }
            logger.trace("UNSET session variable {}", name);
        }
        return null;
    }

    @Override
    public Object visitLetStatement(final FrostlakeParser.LetStatementContext ctx) {
        String varName = getText(ctx.identifier());

        // Check if this is a cursor declaration (LET cursor_name CURSOR FOR SELECT...)
        if (ctx.CURSOR() != null) {
            String selectQuery = getOriginalText(ctx.selectStatement());
            DeclareCursorStatement stmt = new DeclareCursorStatement(varName, selectQuery);
            proceduralExecutor.executeStatement(stmt);
            logger.trace("Let cursor: {}", varName);
            return null;
        }

        // Regular LET with expression
        Object defaultValue = evaluateExpression(ctx.expression());
        DeclareStatement stmt = new DeclareStatement(varName, defaultValue,
            ctx.dataTypeName() != null ? parseDataType(ctx.dataTypeName(), ctx.typeParameters()) : null);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Let variable: {}", varName);
        return null;
    }

    @Override
    public Object visitAssignmentStatement(final FrostlakeParser.AssignmentStatementContext ctx) {
        String varName = getText(ctx.identifier());

        // Handle: var := (call proc()) — capture procedure return value
        if (ctx.callStatement() != null) {
            Object result = visitCallStatement(ctx.callStatement());
            // If the procedure returned a ResultSet, extract the first column value
            Object value = result;
            if (result instanceof ResultSet) {
                ResultSet rs = (ResultSet) result;
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

        // Check if expression exists (might be null for non-procedural contexts)
        if (ctx.expression() == null) {
            logger.trace("Non-procedural assignment statement, skipping");
            return null;
        }

        BaseExpression expr = buildExpression(ctx.expression());
        SetStatement stmt = new SetStatement(varName, expr, getOriginalText(ctx.expression()));
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Assigned variable: {}", varName);
        return null;
    }


    @Override
    public Object visitOpenStatement(final FrostlakeParser.OpenStatementContext ctx) {
        String cursorName = getText(ctx.identifier());
        Cursor cursor = proceduralExecutor.getCursor(cursorName);

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
        ResultSet resultSet = queryExecutor.executeSelectFromContext(
            parseSelectStatement(selectSql)
        );
        cursor.open(resultSet);

        logger.trace("Opened cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitFetchStatement(final FrostlakeParser.FetchStatementContext ctx) {
        String cursorName = getText(ctx.identifier());
        List<String> targetVars = new ArrayList<>();

        for (final FrostlakeParser.IdentifierContext idCtx : ctx.identifierList().identifier()) {
            targetVars.add(getText(idCtx));
        }

        FetchStatement stmt = new FetchStatement(cursorName, targetVars);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Fetched from cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitCloseStatement(final FrostlakeParser.CloseStatementContext ctx) {
        String cursorName = getText(ctx.identifier());

        CloseStatement stmt = new CloseStatement(cursorName);
        proceduralExecutor.executeStatement(stmt);

        logger.trace("Closed cursor: {}", cursorName);
        return null;
    }

    @Override
    public Object visitSelectIntoStatement(final FrostlakeParser.SelectIntoStatementContext ctx) {
        // Reconstruct SELECT without INTO clause, execute, assign to variables
        // Rebuild as: SELECT <selectList> FROM <tableExpression> [WHERE ...]
        StringBuilder selectSql = new StringBuilder("SELECT ");
        if (ctx.DISTINCT() != null) selectSql.append("DISTINCT ");
        selectSql.append(getOriginalText(ctx.selectList()));
        if (ctx.FROM() != null && ctx.tableExpression() != null) {
            selectSql.append(" FROM ").append(getOriginalText(ctx.tableExpression()));
            if (ctx.whereClause() != null) selectSql.append(" ").append(getOriginalText(ctx.whereClause()));
            if (ctx.groupByClause() != null) selectSql.append(" ").append(getOriginalText(ctx.groupByClause()));
            if (ctx.havingClause() != null) selectSql.append(" ").append(getOriginalText(ctx.havingClause()));
            if (ctx.qualifyClause() != null) selectSql.append(" ").append(getOriginalText(ctx.qualifyClause()));
        }
        // ORDER BY / LIMIT / FETCH apply to the whole query, e.g. SELECT c INTO :v FROM t ORDER BY c LIMIT 1.
        if (ctx.orderByClause() != null) selectSql.append(" ").append(getOriginalText(ctx.orderByClause()));
        if (ctx.limitClause() != null) selectSql.append(" ").append(getOriginalText(ctx.limitClause()));
        if (ctx.fetchClause() != null) selectSql.append(" ").append(getOriginalText(ctx.fetchClause()));
        List<ResultSet> results = queryExecutor.execute(selectSql.toString());
        ResultSet rs = results.isEmpty() ? null : results.get(0);
        final int rowCount = rs == null ? 0 : rs.getRowCount();
        List<FrostlakeParser.IntoTargetContext> targets = ctx.intoTargetList().intoTarget();

        // Snowflake: a SELECT ... INTO that matches no rows is not an error — every target is set to
        // NULL and the block continues, so an absent lookup value simply yields NULL.
        if (rowCount == 0) {
            for (final FrostlakeParser.IntoTargetContext target : targets) {
                assignSelectIntoTarget(getText(target.identifier()), null);
            }
            logger.trace("SELECT INTO: no rows matched; assigned NULL to {} target(s)", targets.size());
            return null;
        }
        // More than one row remains an error — narrow the query (e.g. with LIMIT) to a single row.
        if (rowCount > 1) {
            throw new RuntimeException(
                "Select statement in SELECT INTO returned wrong number of rows: " + rowCount);
        }
        Row firstRow = rs.getRows().get(0);
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

    /** Assign a value to a SELECT INTO target through SetStatement, coercing it to the declared type. */
    private void assignSelectIntoTarget(final String varName, final Object value) {
        if (proceduralExecutor != null) {
            proceduralExecutor.executeStatement(new SetStatement(varName, new LiteralExpression(value)));
        }
    }

    private FrostlakeParser.SelectStatementContext parseSelectStatement(final String sql) {
        FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        SyntaxErrorListener errorListener = new SyntaxErrorListener(sql);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        FrostlakeParser.SelectStatementContext selectStatementContext = parser.selectStatement();
        errorListener.throwIfErrors();

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

    @Override
    public Object visitReturnStatement(final FrostlakeParser.ReturnStatementContext ctx) {
        proceduralExecutor.executeStatement(proceduralBlockBuilder.buildReturnStatement(ctx));
        logger.trace("Executed RETURN statement");
        return proceduralExecutor.getReturnValue();
    }

    @Override
    public Object visitBreakStatement(final FrostlakeParser.BreakStatementContext ctx) {
        final Statement brk = new Statement(StatementType.BREAK) {};
        if (ctx.identifier() != null) {
            brk.setLabel(getText(ctx.identifier()));
        }
        proceduralExecutor.executeStatement(brk);
        logger.trace("Executed BREAK statement");
        return null;
    }

    @Override
    public Object visitContinueStatement(final FrostlakeParser.ContinueStatementContext ctx) {
        final Statement cont = new Statement(StatementType.CONTINUE) {};
        if (ctx.identifier() != null) {
            cont.setLabel(getText(ctx.identifier()));
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
        RaiseStatement stmt;

        if (ctx.identifier() != null && ctx.STRING_LITERAL() == null) {
            // RAISE exception_name - must be a user-defined exception
            String name = getText(ctx.identifier());
            // Check if it's a user-defined exception
            if (proceduralExecutor.hasException(name)) {
                stmt = new RaiseStatement(name);
            } else {
                // Undefined exception - throw error immediately
                throw new RuntimeException("Undefined exception: " + name);
            }
        } else if (ctx.STRING_LITERAL() != null) {
            // RAISE 'message'
            String msg = extractStringLiteral(ctx.STRING_LITERAL());
            BaseExpression message = new LiteralExpression(msg);
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
            BaseExpression message = new LiteralExpression("Error raised");
            stmt = new RaiseStatement(message);
        }

        proceduralExecutor.executeStatement(stmt);

        logger.trace("Executed RAISE statement");
        return null;
    }

    @Override
    public Object visitCallStatement(final FrostlakeParser.CallStatementContext ctx) {
        String qualifiedName = getText(ctx.qualifiedName());
        String[] parts = qualifiedNameParts(ctx.qualifiedName());
        String procName = parts[parts.length - 1].toUpperCase();

        Schema schema;
        if (parts.length == 3) {
            schema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
        } else if (parts.length == 2) {
            String dbName = catalog.getCurrentDatabase();
            if (dbName == null) throw new RuntimeException("No database selected");
            schema = catalog.getDatabase(dbName).getSchema(parts[0]);
        } else {
            schema = resolveCurrentSchema();
        }

        Procedure procedure = schema.getProcedure(procName);

        if (procedure == null) {
            throw new RuntimeException("Procedure not found: " + qualifiedName);
        }

        // Evaluate call arguments
        List<Object> arguments = new ArrayList<>();
        if (ctx.expressionList() != null) {
            for (final FrostlakeParser.ExpressionContext exprCtx : ctx.expressionList().expression()) {
                arguments.add(evaluateExpression(exprCtx));
            }
        }

        // Fill in default values for omitted trailing parameters
        List<Parameter> params = procedure.getParameters();
        for (int i = arguments.size(); i < params.size(); i++) {
            Parameter param = params.get(i);
            if (!param.hasDefault()) {
                throw new RuntimeException("Missing required argument for parameter: " + param.getName());
            }
            ExpressionEvaluator eval =
                new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(), queryExecutor);
            arguments.add(eval.evaluate(param.getDefaultValue(), null));
        }

        final UdfLanguage language = procedure.getUdfLanguage();
        Object returnValue;

        if (language == UdfLanguage.JAVASCRIPT) {
            returnValue = JavaScriptProcedureExecutor
                .executeJavaScriptProcedure(procedure, arguments, queryExecutor.getDatabaseEngine());
        } else if (language == UdfLanguage.PYTHON) {
            returnValue = PythonProcedureExecutor
                .executePythonProcedure(procedure, arguments, queryExecutor.getDatabaseEngine());
        } else if (language == UdfLanguage.JAVA) {
            returnValue = JavaProcedureExecutor
                .executeJavaProcedure(procedure, arguments, queryExecutor.getDatabaseEngine());
        } else if (language == UdfLanguage.SCALA) {
            returnValue = ScalaProcedureExecutor
                .executeScalaProcedure(procedure, arguments, queryExecutor.getDatabaseEngine());
        } else {
            // SQL procedural language: bind the call arguments to the parameter names, execute the body
            // on the shared procedural executor (so its RETURN / control flow / statements actually
            // run — not merely logged), and surface the RETURN value. Parameters are declared in a
            // dedicated scope so they don't leak past the CALL.
            proceduralExecutor.enterScope();
            try {
                for (int i = 0; i < params.size(); i++) {
                    proceduralExecutor.markDeclaredInCurrentScope(params.get(i).getName());
                    proceduralExecutor.setVariable(params.get(i).getName(), arguments.get(i));
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

                Object bodyResult = null;
                for (final FrostlakeParser.StatementContext stmtCtx : tree.statement()) {
                    bodyResult = visit(stmtCtx);
                    if (proceduralExecutor.hasReturned()) {
                        break; // a bare (non-BEGIN…END) RETURN statement stops the body
                    }
                }
                // A BEGIN…END body surfaces its RETURN as a single-row ResultSet (bodyResult, with the
                // return state already cleared by visitBeginEndBlock); a bare-statement body leaves the
                // value in the return state. Handle both, and clear the state so it can't leak.
                if (bodyResult instanceof ResultSet) {
                    return (ResultSet) bodyResult;
                }
                if (proceduralExecutor.hasReturned()) {
                    final Object rv = proceduralExecutor.getReturnValue();
                    proceduralExecutor.clearReturnState();
                    final List<ResultSetColumn> resultColumns = new ArrayList<>();
                    resultColumns.add(new ResultSetColumn("RESULT", StringType.VARCHAR, null));
                    final List<Row> resultRows = new ArrayList<>();
                    resultRows.add(new Row(rv));
                    return new ResultSet(resultColumns, resultRows);
                }
                return null;
            } catch (final ProceduralException e) {
                throw e;
            } catch (final Exception e) {
                throw new RuntimeException("Failed to execute procedure: " + e.getMessage(), e);
            } finally {
                proceduralExecutor.exitScope();
            }
        }

        logger.trace("Executed CALL statement for: {}", procName);

        if (returnValue != null) {
            List<ResultSetColumn> columns = new ArrayList<>();
            columns.add(new ResultSetColumn("RESULT", StringType.VARCHAR, null));
            List<Row> rows = new ArrayList<>();
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
                // Evaluate the expression using the CURRENT executor (needed for :variable references)
                Object sqlObj = evaluateExpression(ctx.expression());

                if (sqlObj == null) {
                    throw new RuntimeException("EXECUTE IMMEDIATE: SQL expression evaluated to null");
                }

                sqlString = sqlObj.toString();

                // Bind USING (...) values positionally to the ? placeholders (Snowflake style).
                if (ctx.expressionList() != null) {
                    final List<Object> bindValues = new ArrayList<>();
                    for (final FrostlakeParser.ExpressionContext bindCtx : ctx.expressionList().expression()) {
                        bindValues.add(evaluateExpression(bindCtx));
                    }
                    sqlString = JdbcMarshaling.substitutePlaceholders(sqlString, bindValues);
                }
            }

            // Parse and execute the dynamic SQL
            FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sqlString));

            SyntaxErrorListener errorListener = new SyntaxErrorListener(sqlString);
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            CommonTokenStream tokens = new CommonTokenStream(lexer);
            FrostlakeParser parser = new FrostlakeParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            FrostlakeParser.SqlScriptContext sqlScriptCtx = parser.sqlScript();
            errorListener.throwIfErrors();

            // If the inner SQL is a procedural block (BEGIN...END or DECLARE...), clear cursors/exceptions
            // from any prior invocation so they don't cause 'already declared' errors on re-execution.
            // Variables are scoped naturally by BEGIN...END, so only transient state needs clearing.
            // Determined from the parse tree, not a string prefix.
            boolean isProcedural = false;
            for (final FrostlakeParser.StatementContext s : sqlScriptCtx.statement()) {
                final FrostlakeParser.ProceduralStatementContext p = s.proceduralStatement();
                if (p != null && (p.beginEndBlock() != null || p.declareStatement() != null)) {
                    isProcedural = true;
                    break;
                }
            }
            if (isProcedural) {
                proceduralExecutor.clearCursorsAndExceptions();
            }

            // Execute each statement in the dynamic SQL
            Object lastResult = null;
            for (final FrostlakeParser.StatementContext stmtCtx : sqlScriptCtx.statement()) {
                lastResult = visit(stmtCtx);
                // Cache result sets so RESULT_SCAN(LAST_QUERY_ID()) works after SHOW / SELECT statements
                if (lastResult instanceof ResultSet) {
                    queryExecutor.getResultCache().cacheResult(sqlString, (ResultSet) lastResult);
                }
            }

            logger.trace("Executed EXECUTE IMMEDIATE: {}", sqlString.length() > 50 ? sqlString.substring(0, 50) + "..." : sqlString);
            return lastResult;

        } catch (final Exception e) {
            if (e instanceof SecurityException) throw (SecurityException) e;
            if (e instanceof ProceduralException) throw (ProceduralException) e;
            throw new RuntimeException("Failed to execute EXECUTE IMMEDIATE: " + e.getMessage(), e);
        }
    }

    /** Read the SQL script referenced by {@code EXECUTE IMMEDIATE FROM} — a stage file (@stg/file) or a
     *  location string ('file://…'). */
    private String readImmediateScript(final FrostlakeParser.ExecuteImmediateStatementContext ctx) {
        final Path path = ctx.stageRef() != null
            ? queryExecutor.resolveStageFilePath(ctx.stageRef())
            : queryExecutor.resolveStageFilePath(unquoteStringLiteral(ctx.STRING_LITERAL().getText()));
        if (path == null || !Files.exists(path)) {
            throw new RuntimeException("EXECUTE IMMEDIATE FROM: script file not found: " + path);
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
        if (ctx.QUOTED_IDENTIFIER() != null) {
            String quoted = ctx.QUOTED_IDENTIFIER().getText();
            return quoted.substring(1, quoted.length() - 1); // Remove quotes
        }
        if (ctx.POSITIONAL_PARAMETER() != null) {
            String param = ctx.POSITIONAL_PARAMETER().getText();
            int position = Integer.parseInt(param.substring(1));
            return "COLUMN" + position;
        }
        if (ctx.IDENTIFIER() != null) {
            return ctx.IDENTIFIER().getText();
        }
        if (ctx.KW_IDENTIFIER() != null) {
            return ctx.KW_IDENTIFIER().getText();
        }
        // Handle keywords used as identifiers
        if (ctx.DATE() != null) return ctx.DATE().getText();
        if (ctx.TIMESTAMP() != null) return ctx.TIMESTAMP().getText();
        if (ctx.COMMENT() != null) return ctx.COMMENT().getText();
        // Fallback to getText() which concatenates all tokens
        return ctx.getText();
    }

    public String getText(final FrostlakeParser.QualifiedNameContext ctx) {
        List<String> parts = new ArrayList<>();
        for (final FrostlakeParser.IdentifierContext id : ctx.identifier()) {
            parts.add(getText(id));
        }
        return String.join(".", parts);
    }

    /**
     * The ordered identifier parts of a qualified name (db, schema, name), read from the parse tree's
     * identifier list — the AST-definitive way — rather than by splitting its flattened text on '.'.
     * Correct even when an identifier is double-quoted and itself contains a dot (which {@code
     * getText(qn).split("\\.")} would mis-split).
     */
    public String[] qualifiedNameParts(final FrostlakeParser.QualifiedNameContext ctx) {
        final List<FrostlakeParser.IdentifierContext> ids = ctx.identifier();
        final String[] parts = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            parts[i] = getText(ids.get(i));
        }
        return parts;
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

    private String extractBodyDefinition(final FrostlakeParser.BodyDefinitionContext ctx) {
        if (ctx.STRING_LITERAL() != null) {
            // Single-quoted string literal
            return extractStringLiteral(ctx.STRING_LITERAL());
        } else if (ctx.DOLLAR_QUOTED_STRING() != null) {
            // Dollar-quoted string ($$...$$)
            String text = ctx.DOLLAR_QUOTED_STRING().getText();
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
        List<TableColumn> columns = new ArrayList<>();
        List<String> tablePrimaryKeys = new ArrayList<>();

        // First pass: collect columns and table-level primary keys
        for (final FrostlakeParser.ColumnOrConstraintContext item : ctx.columnOrConstraint()) {
            if (item.columnDef() != null) {
                FrostlakeParser.ColumnDefContext colDef = item.columnDef();
                String colName = getText(colDef.identifier());
                DataType dataType = parseDataType(colDef.dataTypeName(), colDef.typeParameters());

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
                TableColumn column = new TableColumn(colName, dataType, !notNull, defaultValue,
                                            primaryKey, false, autoIncrement);

                // Set comment if provided
                String comment = extractComment(colDef.commentClause());
                if (comment != null) {
                    column.setComment(comment);
                }

                columns.add(column);
            } else if (item.tableConstraint() != null) {
                FrostlakeParser.TableConstraintContext constraint = item.tableConstraint();
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
            List<TableColumn> updatedColumns = new ArrayList<>();
            for (final TableColumn col : columns) {
                boolean isPrimaryKey = tablePrimaryKeys.stream()
                    .anyMatch((final var pk) -> pk.equalsIgnoreCase(col.getName()));

                // Create new column with updated primary key status
                TableColumn newCol = new TableColumn(
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
            throw new RuntimeException("No database or schema selected");
        }
        return catalog.getDatabase(catalog.getCurrentDatabase())
                     .getSchema(catalog.getCurrentSchema());
    }

    private String resolveFullyQualifiedName(final String qualifiedName) {
        String[] parts = QualifiedName.parse(qualifiedName).parts();

        if (parts.length == 1) {
            // table name only - use current database and schema
            if (catalog.getCurrentDatabase() == null || catalog.getCurrentSchema() == null) {
                throw new RuntimeException("No database or schema selected");
            }
            return catalog.getCurrentDatabase().toUpperCase() + "." +
                   catalog.getCurrentSchema().toUpperCase() + "." +
                   parts[0].toUpperCase();
        } else if (parts.length == 2) {
            // schema.table - use current database
            if (catalog.getCurrentDatabase() == null) {
                throw new RuntimeException("No database selected");
            }
            return catalog.getCurrentDatabase().toUpperCase() + "." +
                   parts[0].toUpperCase() + "." +
                   parts[1].toUpperCase();
        } else if (parts.length == 3) {
            // database.schema.table - fully qualified
            return parts[0].toUpperCase() + "." +
                   parts[1].toUpperCase() + "." +
                   parts[2].toUpperCase();
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

    private Object evaluateExpression(final FrostlakeParser.ExpressionContext ctx) {
        return expressionBuilder.evaluateExpression(ctx);
    }

    public ProceduralExecutor getProceduralExecutor() {
        return proceduralExecutor;
    }
}
