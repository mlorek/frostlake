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

import dev.frostlake.executor.AnonymousBlockResult;
import dev.frostlake.executor.ContinueHandler;
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.UndeclaredScriptVariableException;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    /** The declared types of the names already in scope: for a procedure's own block, its parameters. */
    private Map<String, DataType> declaredTypesInScope() {
        final Map<String, DataType> types = new HashMap<String, DataType>();
        for (final String name : proceduralExecutor.getAllVariables().keySet()) {
            final DataType type = proceduralExecutor.getDeclaredVariableType(name);
            if (type != null) {
                types.put(ScriptingNameValidator.canonical(name), type);
            }
        }
        return types;
    }

    public Object handle(final FrostlakeParser.BeginEndBlockContext ctx) {
        // A syntax fault the engine's grammar reads past is refused before anything else is judged.
        ScriptingNameValidator.rejectDottedBindVariables(ctx);
        // An unnamed bind is refused while the block compiles, before any statement of it runs.
        ScriptingNameValidator.rejectUnnamedBinds(ctx);
        // Compile the WHOLE block before running any of it, as Snowflake does: a name that resolves to
        // nothing is refused even on a branch this run will not take. Seeded with what is already in
        // scope — a stored procedure's parameters, or an enclosing block's variables.
        final Set<String> inScope = new HashSet<String>(proceduralExecutor.getAllVariables().keySet());
        inScope.addAll(proceduralExecutor.saveCursorNames());
        inScope.addAll(proceduralExecutor.declaredExceptionNames());
        // A procedure's OWN block also judges its direct RETURNs against the declared RETURNS type in
        // the same pass, so whichever fault comes first in the body is the one reported, as live does.
        // A name introduced twice in one scope is refused before any other name is judged, even one
        // used earlier in the block (live-verified). A procedure's own block has its parameters in scope,
        // and only those: the variables of a block that called it are another scope.
        final Set<String> parameters = new HashSet<String>();
        for (final String name : proceduralExecutor.ownBlockParameterNames()) {
            parameters.add(ScriptingNameValidator.canonical(name));
        }
        ScriptingNameValidator.rejectRedeclaration(ctx, parameters, false);
        final DataType declaredReturn = proceduralExecutor.declaredReturnOfNextBlock();
        ScriptingNameValidator.validate(ctx, inScope, declaredReturn == null ? null
            : new DeclaredReturnJudge(declaredReturn, declaredTypesInScope(), queryExecutor));
        // The DECLARED TYPES are judged in the same pass and for the same reason: live refuses a bad
        // width while COMPILING the block, so it never becomes a runtime exception and never reaches
        // the uncaught-exception wrapper below. This sits BEFORE enterScope/enterBlock deliberately —
        // a refusal thrown after those runs would skip the finally that unwinds them and leave the
        // executor believing it is still inside a block, which breaks every later block in the session.
        ScriptTypeCompiler.validateDeclaredTypes(ctx);

        // Enter a new scope for this block; also snapshot cursors so inner-declared
        // cursors are cleaned up when the block exits.
        proceduralExecutor.enterScope();
        proceduralExecutor.enterBlock();
        final boolean outermostBlock = proceduralExecutor.getBlockDepth() == 1;
        if (outermostBlock) {
            // A failure recorded by an EARLIER block must not label this one's — nothing between two
            // top-level blocks runs through the per-statement clear.
            proceduralExecutor.clearStatementFailure();
        }
        final Set<String> savedCursorNames = proceduralExecutor.saveCursorNames();

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
                            // Each statement in a block reads its own clock (live-verified).
                            StatementClock.advance();
                            final Object stmtResult = visitor.visit(stmtCtx);
                            if (stmtResult instanceof ResultSet) {
                                proceduralExecutor.recordSqlRowCount((ResultSet) stmtResult);
                                queryExecutor.getResultCache().cacheResult(
                                    visitor.getOriginalText(stmtCtx), (ResultSet) stmtResult);
                            } else if (stmtCtx.proceduralStatement() == null) {
                                // A plain SQL statement (TRUNCATE, some DDL) that surfaced no result
                                // set still resets the DML trio and counts one status line.
                                proceduralExecutor.recordStatementWithoutResult();
                            }
                            // Snowflake: each statement of a stored procedure runs in its own autocommit
                            // transaction (unless an explicit BEGIN is open). Leaving one implicit
                            // transaction spanning the body let a later same-row DELETE consolidate away a
                            // buffered INSERT, so append-only streams missed changes Snowflake captures.
                            queryExecutor.getTransactionManager().autocommitStatementEnd();
                            proceduralExecutor.clearStatementFailure();
                        } catch (final Exception e) {
                            proceduralExecutor.recordStatementFailure(stmtCtx.getStart().getLine(),
                                stmtCtx.getStart().getCharPositionInLine());
                            rollbackFailedStatement();
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
                    StatementClock.advance();
                    final Object stmtResult;
                    try {
                        stmtResult = visitor.visit(stmtCtx);
                    } catch (final RuntimeException failure) {
                        // Name the statement that failed, for the uncaught-exception message below.
                        proceduralExecutor.recordStatementFailure(stmtCtx.getStart().getLine(),
                            stmtCtx.getStart().getCharPositionInLine());
                        throw failure;
                    }
                    proceduralExecutor.clearStatementFailure();
                    // Cache result sets produced by SHOW/SELECT statements so that
                    // RESULT_SCAN(LAST_QUERY_ID()) works inside BEGIN...END blocks
                    if (stmtResult instanceof ResultSet) {
                        proceduralExecutor.recordSqlRowCount((ResultSet) stmtResult);
                        queryExecutor.getResultCache().cacheResult(
                            visitor.getOriginalText(stmtCtx), (ResultSet) stmtResult);
                    } else if (stmtCtx.proceduralStatement() == null) {
                        // A plain SQL statement (TRUNCATE, some DDL) that surfaced no result set
                        // still resets the DML trio and counts one status line.
                        proceduralExecutor.recordStatementWithoutResult();
                    }
                    // Per-statement autocommit, as at top level (see the handler-path loop above).
                    queryExecutor.getTransactionManager().autocommitStatementEnd();
                    // Check if a RETURN, BREAK, or CONTINUE was executed
                    if (proceduralExecutor.hasReturned() || proceduralExecutor.hasBreak() || proceduralExecutor.hasContinue()) {
                        break;
                    }
                }
            }

            logger.trace("Executed BEGIN...END block");

            // Check if the block returned a value
            if (proceduralExecutor.hasReturned()) {
                // A NESTED block must NOT consume the RETURN: leave the return state set so the enclosing
                // BEGIN…END block sees hasReturned() and propagates it (only the outermost block wraps the
                // value into a result, below). Without this a RETURN inside a nested block was swallowed and
                // the procedure returned nothing (an empty result set → "Index 0 out of bounds").
                if (proceduralExecutor.isNestedBlock()) {
                    return null;
                }
                final Object returnValue = proceduralExecutor.getReturnValue();
                final boolean isReturnTable = proceduralExecutor.isReturnTable();

                // Clear the return state so it doesn't leak to next block
                proceduralExecutor.clearReturnState();
                proceduralExecutor.resetReturnTable();

                // If RETURN TABLE was used, return the ResultSet directly without wrapping
                if (isReturnTable && returnValue instanceof ResultSet) {
                    return returnValue;
                }

                // Otherwise, return the value as the block's single-row, single-column result: typed by
                // the declared RETURNS type when the RETURN converted to it, else by the static type of
                // the expression the RETURN names (a text or a binary one at the full width Snowflake
                // declares), else the nominal VARCHAR. A CALL renames the column after its procedure.
                final DataType typed = proceduralExecutor.getReturnedResultType();
                return AnonymousBlockResult.of(returnValue, typed == null ? StringType.VARCHAR : typed);
            }
        } catch (final ProceduralException uncaught) {
            // An exception no handler caught reads with live's wording at the TOP of the script
            // only — an enclosing block may still catch it by name, and SQLERRM must keep the raw
            // text until then.
            if (outermostBlock && uncaught.getExceptionName() != null) {
                final ProceduralException formatted = new ProceduralException(
                    uncaught.getErrorCode(), uncaught.uncaughtMessage(), uncaught.getExceptionName());
                formatted.setSourcePosition(uncaught.getSourceLine(), uncaught.getSourcePosition());
                throw formatted;
            }
            throw uncaught;
        } catch (final RuntimeException uncaught) {
            // A STATEMENT that failed and no handler caught reads the same way, live-verified:
            // "Uncaught exception of type 'STATEMENT_ERROR' on line L at position P : <error>",
            // anchored on the statement that failed however deeply it sat (a nested block, an IF
            // branch, a loop body). Only the OUTERMOST block wraps — an enclosing block may still
            // handle it, SQLERRM keeps the raw text, and a block that failed to COMPILE never gets
            // here with a statement position, so a syntax error stays a plain syntax error.
            if (outermostBlock && proceduralExecutor.getFailureLine() > 0
                    && !(uncaught instanceof UndeclaredScriptVariableException)) {
                throw new RuntimeException("Uncaught exception of type '"
                    + proceduralExecutor.getFailureKind() + "' on line "
                    + proceduralExecutor.getFailureLine() + " at position "
                    + proceduralExecutor.getFailurePosition() + " : " + uncaught.getMessage(), uncaught);
            }
            throw uncaught;
        } finally {
            // Exit the scope and clean up cursors declared inside this block
            proceduralExecutor.exitScope();
            proceduralExecutor.restoreCursors(savedCursorNames);
            proceduralExecutor.exitBlock();
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
        proceduralExecutor.enterCompound();
        try {
            for (final FrostlakeParser.StatementContext stmtCtx : handlerCtx.statementList().statement()) {
                StatementClock.advance();
                visitor.visit(stmtCtx);
                queryExecutor.getTransactionManager().autocommitStatementEnd();
            }
        } finally {
            proceduralExecutor.exitCompound();
            proceduralExecutor.popHandledException();
            proceduralExecutor.setVariable("SQLCODE", prevSqlCode);
            proceduralExecutor.setVariable("SQLERRM", prevSqlErrm);
            proceduralExecutor.setVariable("SQLSTATE", prevSqlState);
        }
    }

    /**
     * Undo a FAILED statement's buffered writes before its exception handler runs. With per-statement
     * autocommit, a non-explicit active transaction holds at most the failed statement's own changes —
     * rolling it back is Snowflake's statement-level rollback. An EXPLICIT (BEGIN-started) transaction
     * is left open untouched, matching the top-level engine behavior.
     */
    private void rollbackFailedStatement() {
        final TransactionManager transactions = queryExecutor.getTransactionManager();
        if (transactions.hasActiveTransaction() && !transactions.isExplicitTransaction()) {
            transactions.rollback();
        }
    }

    /**
     * Bind the Snowflake Scripting error variables — {@code SQLCODE}, {@code SQLERRM},
     * {@code SQLSTATE} — into the procedural scope for the duration of an exception handler.
     * A user-defined / RAISEd exception carries its declared code and message and gets SQLSTATE
     * {@code 'P0001'} (Snowflake's value for user-defined exceptions); a SQL syntax error surfaces
     * as a compilation error ({@code 1003} / {@code '42000'}); division by zero maps to its
     * Snowflake code ({@code 100051} / {@code '22012'}); a wrong argument count, an unknown
     * compression method and an undecodable compressed payload map to theirs; any other engine
     * error is exposed with a generic statement-error code (the engine has no per-error Snowflake
     * code taxonomy).
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
        } else if (message.contains("does not exist")) {
            // Live-verified: a missing table/object surfaces SQLSTATE 42S02, not the generic P0000.
            code = 2003;
            state = "42S02";
        } else if (message.startsWith("not enough arguments for function")) {
            // Live-verified across COMPRESS, LOG and SUBSTR: a wrong argument count is a compilation
            // error carrying 938 / 22023, and 939 for the too-many form.
            code = 938;
            state = "22023";
        } else if (message.startsWith("too many arguments for function")) {
            code = 939;
            state = "22023";
        } else if (message.startsWith("Unknown compression method")) {
            code = 100194;
            state = "42P19";
        } else if (message.startsWith("Can't compress data")) {
            code = 100195;
            state = "22000";
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
            final String exceptionName = visitor.getText(condition.identifier()).toUpperCase();
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
