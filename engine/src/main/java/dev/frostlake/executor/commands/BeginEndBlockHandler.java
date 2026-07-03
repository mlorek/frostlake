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

import dev.frostlake.executor.ContinueHandler;
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles Snowflake Scripting {@code BEGIN … [EXCEPTION …] END} blocks, extracted verbatim from
 * {@link SQLCommandVisitor#visitBeginEndBlock} together with its cohesive exception-handling helpers
 * ({@code bindHandlerErrorVariables}, {@code shouldHandleException}, {@code matchesSnowflakeException} —
 * each used only by this path). Statement execution recurses back through {@code visitor.visit(...)};
 * parse-text extraction ({@code getOriginalText}, {@code getText}) is reached through the {@code visitor}
 * back-reference. The procedural scope/return/cursor state lives on the injected
 * {@link ProceduralExecutor}, and cached result sets go through {@code queryExecutor.getResultCache()},
 * so the exact original behavior is preserved.
 */
public class BeginEndBlockHandler implements CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(BeginEndBlockHandler.class);

    private final Catalog catalog;
    private final QueryExecutor queryExecutor;
    private final SQLCommandVisitor visitor;
    private final ProceduralExecutor proceduralExecutor;

    public BeginEndBlockHandler(final Catalog catalog, final QueryExecutor queryExecutor,
                                final SQLCommandVisitor visitor, final ProceduralExecutor proceduralExecutor) {
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.visitor = visitor;
        this.proceduralExecutor = proceduralExecutor;
    }

    @Override
    public Catalog getCatalog() {
        return catalog;
    }

    @Override
    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public Object handle(final FrostlakeParser.BeginEndBlockContext ctx) {
        // Enter a new scope for this block; also snapshot cursors so inner-declared
        // cursors are cleaned up when the block exits.
        proceduralExecutor.enterScope();
        Set<String> savedCursorNames = proceduralExecutor.saveCursorNames();

        try {
            // Process DECLARE section if present
            if (ctx.declareSection() != null) {
                visitor.visit(ctx.declareSection());
            }

            // Check if there's an exception section
            if (ctx.exceptionSection() != null) {
                // Run each statement in its own try so a matching CONTINUE handler resumes at the NEXT
                // statement, while an EXIT handler (the default) stops the block after it runs. The block
                // is also registered on the procedural CONTINUE-handler stack so a loop-body error inside
                // it resumes at the loop's next iteration (Snowflake CONTINUE semantics).
                proceduralExecutor.pushContinueHandler(new ContinueHandler() {
                    @Override
                    public boolean tryHandleAsContinue(final Exception e) {
                        final FrostlakeParser.ExceptionHandlerContext handlerCtx =
                            findMatchingHandler(ctx.exceptionSection(), e);
                        if (handlerCtx == null || handlerCtx.CONTINUE() == null) {
                            return false;
                        }
                        runHandler(handlerCtx, e);
                        return true;
                    }
                });
                try {
                    for (final FrostlakeParser.StatementContext stmtCtx : ctx.statementList().statement()) {
                        boolean exitBlock = false;
                        try {
                            final Object stmtResult = visitor.visit(stmtCtx);
                            if (stmtResult instanceof ResultSet) {
                                proceduralExecutor.recordSqlRowCount((ResultSet) stmtResult);
                                queryExecutor.getResultCache().cacheResult(
                                    visitor.getOriginalText(stmtCtx), (ResultSet) stmtResult);
                            }
                        } catch (final Exception e) {
                            final FrostlakeParser.ExceptionHandlerContext handlerCtx =
                                findMatchingHandler(ctx.exceptionSection(), e);
                            if (handlerCtx == null) {
                                throw e;   // no matching handler — propagate to the enclosing block
                            }
                            runHandler(handlerCtx, e);
                            // EXIT (default) stops the block; CONTINUE falls through to the next statement.
                            exitBlock = handlerCtx.CONTINUE() == null;
                        }
                        // Stop on EXIT-after-handle, or when a RETURN / BREAK / CONTINUE was executed.
                        if (exitBlock || proceduralExecutor.hasReturned()
                                || proceduralExecutor.hasBreak() || proceduralExecutor.hasContinue()) {
                            break;
                        }
                    }
                } finally {
                    proceduralExecutor.popContinueHandler();
                }
            } else {
                // No exception handling, execute statements directly
                for (final FrostlakeParser.StatementContext stmtCtx : ctx.statementList().statement()) {
                    Object stmtResult = visitor.visit(stmtCtx);
                    // Cache result sets produced by SHOW/SELECT statements so that
                    // RESULT_SCAN(LAST_QUERY_ID()) works inside BEGIN...END blocks
                    if (stmtResult instanceof ResultSet) {
                        proceduralExecutor.recordSqlRowCount((ResultSet) stmtResult);
                        queryExecutor.getResultCache().cacheResult(
                            visitor.getOriginalText(stmtCtx), (ResultSet) stmtResult);
                    }
                    // Check if a RETURN, BREAK, or CONTINUE was executed
                    if (proceduralExecutor.hasReturned() || proceduralExecutor.hasBreak() || proceduralExecutor.hasContinue()) {
                        break;
                    }
                }
            }

            logger.trace("Executed BEGIN...END block");

            // Check if the block returned a value
            if (proceduralExecutor.hasReturned()) {
                Object returnValue = proceduralExecutor.getReturnValue();
                boolean isReturnTable = proceduralExecutor.isReturnTable();

                // Clear the return state so it doesn't leak to next block
                proceduralExecutor.clearReturnState();
                proceduralExecutor.resetReturnTable();

                // If RETURN TABLE was used, return the ResultSet directly without wrapping
                if (isReturnTable && returnValue instanceof ResultSet) {
                    return returnValue;
                }

                // Otherwise, return the value as a single-row, single-column ResultSet
                List<ResultSetColumn> columns = new ArrayList<>();
                columns.add(new ResultSetColumn("RESULT", StringType.VARCHAR, null));

                List<Row> rows = new ArrayList<>();
                Row row = new Row(returnValue);
                rows.add(row);

                return new ResultSet(columns, rows);
            }
        } finally {
            // Exit the scope and clean up cursors declared inside this block
            proceduralExecutor.exitScope();
            proceduralExecutor.restoreCursors(savedCursorNames);
        }

        return null;
    }

    /** The first handler in {@code section} whose condition matches {@code e}, or null if none. */
    private FrostlakeParser.ExceptionHandlerContext findMatchingHandler(
            final FrostlakeParser.ExceptionSectionContext section, final Exception e) {
        for (final FrostlakeParser.ExceptionHandlerContext handlerCtx : section.exceptionHandler()) {
            if (shouldHandleException(handlerCtx.exceptionCondition(), e)) {
                return handlerCtx;
            }
        }
        return null;
    }

    /**
     * Run a matched handler's statements with SQLCODE / SQLERRM / SQLSTATE bound for its duration and
     * restored afterwards (nested handlers shadow, not clobber — an inner block's exitScope would
     * otherwise propagate its values into an enclosing handler's scope).
     */
    private void runHandler(final FrostlakeParser.ExceptionHandlerContext handlerCtx, final Exception e) {
        final Object prevSqlCode = proceduralExecutor.getVariable("SQLCODE");
        final Object prevSqlErrm = proceduralExecutor.getVariable("SQLERRM");
        final Object prevSqlState = proceduralExecutor.getVariable("SQLSTATE");
        bindHandlerErrorVariables(e);
        proceduralExecutor.pushHandledException(e);
        try {
            for (final FrostlakeParser.StatementContext stmtCtx : handlerCtx.statementList().statement()) {
                visitor.visit(stmtCtx);
            }
        } finally {
            proceduralExecutor.popHandledException();
            proceduralExecutor.setVariable("SQLCODE", prevSqlCode);
            proceduralExecutor.setVariable("SQLERRM", prevSqlErrm);
            proceduralExecutor.setVariable("SQLSTATE", prevSqlState);
        }
    }

    /**
     * Bind the Snowflake Scripting error variables — {@code SQLCODE}, {@code SQLERRM},
     * {@code SQLSTATE} — into the procedural scope for the duration of an exception handler.
     * A user-defined / RAISEd exception carries its declared code and message and gets SQLSTATE
     * {@code 'P0001'} (Snowflake's value for user-defined exceptions); a SQL syntax error surfaces
     * as a compilation error ({@code 1003} / {@code '42000'}); division by zero maps to its
     * Snowflake code ({@code 100051} / {@code '22012'}); any other engine error is exposed with a
     * generic statement-error code (the engine has no per-error Snowflake code taxonomy).
     */
    private void bindHandlerErrorVariables(final Exception e) {
        final String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        final int code;
        final String state;
        if (e instanceof ProceduralException) {
            code = ((ProceduralException) e).getErrorCode();
            state = "P0001";
        } else if (e instanceof SqlSyntaxException) {
            code = 1003;
            state = "42000";
        } else if (message.contains("Division by zero")) {
            code = 100051;
            state = "22012";
        } else {
            code = 100351;
            state = "P0000";
        }
        proceduralExecutor.setVariable("SQLCODE", code);
        proceduralExecutor.setVariable("SQLERRM", message);
        proceduralExecutor.setVariable("SQLSTATE", state);
    }

    private boolean shouldHandleException(final FrostlakeParser.ExceptionConditionContext condition, final Exception e) {
        // Check if this is an "OTHER" handler (catch-all)
        if (condition.OTHER() != null) {
            return true;
        }

        // Check if it's a named exception handler
        if (condition.identifier() != null) {
            String exceptionName = visitor.getText(condition.identifier()).toUpperCase();
            return matchesSnowflakeException(exceptionName, e);
        }

        return false;
    }

    private boolean matchesSnowflakeException(final String exceptionName, final Exception e) {
        switch (exceptionName) {
            // STATEMENT_ERROR covers DDL/DML runtime errors (object not found, permission denied, etc.)
            case "STATEMENT_ERROR":
                // Covers DDL/DML runtime errors: object not found, permission denied, etc.
                // Excludes SQL syntax errors which are compile-time
                return e instanceof RuntimeException
                    && !(e instanceof SqlSyntaxException);
            // EXPRESSION_ERROR covers errors in expression evaluation
            case "EXPRESSION_ERROR":
                return e instanceof ArithmeticException
                    || (e instanceof RuntimeException && e.getMessage() != null
                        && (e.getMessage().contains("Cannot") || e.getMessage().contains("division by zero")));
            // Syntax errors
            case "SQL_STATEMENT_EXCEPTION":
            case "SYNTAX_ERROR":
                return e instanceof SqlSyntaxException;
            // Numeric/data errors
            case "NUMERIC_VALUE_OUT_OF_RANGE":
                return e instanceof NumberFormatException || e instanceof ArithmeticException;
            // User-defined exceptions: a RAISEd one carries its declared name — match strictly on that.
            // (No message-substring fallback: it produced false positives, e.g. catching by coincidence
            // when the message happened to contain the handler's name.) Other (Java) exceptions fall back
            // to a class-simple-name match, preserving handlers like WHEN RuntimeException.
            default:
                if (e instanceof ProceduralException) {
                    final String raisedName = ((ProceduralException) e).getExceptionName();
                    if (raisedName != null) {
                        return raisedName.equalsIgnoreCase(exceptionName);
                    }
                }
                return e.getClass().getSimpleName().equalsIgnoreCase(exceptionName);
        }
    }
}
