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
import dev.frostlake.executor.BlockExpressionTypeError;
import dev.frostlake.executor.BlockExpressionTypes;
import dev.frostlake.executor.ContinueHandler;
import dev.frostlake.executor.DeclarationTypes;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.ScriptedErrorPlace;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.StatementCountMismatch;
import dev.frostlake.executor.UndeclaredScriptVariableException;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
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

    /** What an unresolvable name's refusal reads before the name. */
    private static final String INVALID_IDENTIFIER = "invalid identifier '";

    /** The type a FOR loop's counter is compiled with. */
    private static final DataType COUNTER_TYPE = new NumericType("NUMBER", 9, 0);

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

    /**
     * Compiles a FROM-less subquery an untyped declaration infers its type from, as a query of its own
     * that nothing runs; a name it cannot resolve is refused at its place in the block (see
     * {@link ScriptingNameValidator}).
     */
    private InferredInitialiserCompiler initialiserCompiler() {
        // The types the block has declared so far, in source order, over those already in scope; a nested body's
        // declarations end with it.
        final Map<String, DataType> declaredSoFar = declaredTypesInScope();
        final Deque<Map<String, DataType>> enclosing = new ArrayDeque<Map<String, DataType>>();
        return new InferredInitialiserCompiler() {
            @Override
            public void enterScope() {
                enclosing.push(new HashMap<String, DataType>(declaredSoFar));
            }

            @Override
            public void exitScope() {
                if (!enclosing.isEmpty()) {
                    declaredSoFar.clear();
                    declaredSoFar.putAll(enclosing.pop());
                }
            }

            @Override
            public void declare(final String name, final FrostlakeParser.DataTypeNameContext type,
                                final FrostlakeParser.TypeParametersContext parameters) {
                try {
                    final DataType declared = visitor.parseDataType(type, parameters);
                    // A text declared without a width is compiled at the full scripting width, a binary at its maximum.
                    declaredSoFar.put(name, parameters == null && declared instanceof StringType
                        ? new StringType("VARCHAR", StringResultWidths.UNBOUNDED)
                        : parameters == null && declared instanceof BinaryType ? BinaryType.AT_MAXIMUM : declared);
                } catch (final RuntimeException unreadable) {
                    // A type the block cannot declare is refused when its declared types are judged.
                    declaredSoFar.remove(name);
                }
            }

            @Override
            public void declareCounter(final String name) {
                declaredSoFar.put(name, COUNTER_TYPE);
            }

            @Override
            public void infer(final String name, final FrostlakeParser.BooleanExprContext initialiser,
                              final Token at) {
                if (queryExecutor == null) {
                    return;
                }
                BlockExpressionTypes.judgeCompiled(initialiser, declaredSoFar, queryExecutor);
                // A bind written alone hands on its variable's type.
                final FrostlakeParser.IdentifierContext bound = bindWrittenAlone(initialiser);
                DataType inferred;
                try {
                    inferred = bound != null ? declaredSoFar.get(SqlIdentifiers.canonical(bound))
                        : DeclarationTypes.ofInitialiser(initialiser, declaredSoFar, queryExecutor);
                } catch (final RuntimeException untyped) {
                    inferred = null;
                }
                if (inferred instanceof VariantType) {
                    // The account infers no VARIANT: PARSE_JSON('1'), TO_VARIANT(NULL), 1::VARIANT and a VARIANT
                    // name or bind all leave the declaration untyped, where an OBJECT or an ARRAY is taken
                    // (live-verified).
                    throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                        " variable '" + name + "' cannot have its type inferred from initializer"));
                }
                if (inferred == null) {
                    declaredSoFar.remove(name);
                } else {
                    declaredSoFar.put(name, inferred);
                }
            }

            @Override
            public void judge(final FrostlakeParser.BooleanExprContext operand) {
                if (queryExecutor != null) {
                    BlockExpressionTypes.judgeCompiled(operand, declaredSoFar, queryExecutor);
                }
            }

            @Override
            public void compile(final FrostlakeParser.SelectStatementContext query) {
                if (queryExecutor == null) {
                    return;
                }
                final ExpressionEvaluator scope = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
                    catalog, queryExecutor);
                try {
                    scope.compileSubquery(new SubqueryExpression(visitor.getOriginalText(query)), null);
                } catch (final RuntimeException refused) {
                    if (!SqlCompilationError.isCompilationError(refused.getMessage())) {
                        throw refused;
                    }
                    throw new RuntimeException(ScriptedErrorPlace.inEnclosingText(refused.getMessage(),
                        query.getStart().getLine(), query.getStart().getCharPositionInLine()), refused);
                }
            }
        };
    }

    /**
     * One statement of the block. A SQL statement reports its compilation errors against its OWN text, as
     * the account does — {@code SELECT missing} is position 7 wherever it sits in the block, and a place on
     * its second line is line 2 — so its places are re-based on its first token while it runs.
     */
    private Object visitStatement(final FrostlakeParser.StatementContext stmtCtx) {
        final int line = stmtCtx.getStart().getLine();
        final int column = stmtCtx.getStart().getCharPositionInLine();
        if (stmtCtx.proceduralStatement() != null) {
            if (stmtCtx.proceduralStatement().selectIntoStatement() == null) {
                return visitor.visit(stmtCtx);
            }
            // A SELECT … INTO runs its query as text of its own, whose places the block offsets by the
            // statement's column on every line (live-verified).
            final UndeclaredScriptVariableException intoName;
            try {
                return visitor.visit(stmtCtx);
            } catch (final UndeclaredScriptVariableException refused) {
                intoName = refused;
            }
            throw inBlock(intoName, line, column, true);
        }
        final SourcePosition displaced = LeadingCommentOffset.begin(new SourcePosition(line, column));
        final UndeclaredScriptVariableException blockName;
        try {
            return visitor.visit(stmtCtx);
        } catch (final UndeclaredScriptVariableException refused) {
            blockName = refused;
        } finally {
            LeadingCommentOffset.end(displaced);
        }
        throw inBlock(blockName, line, column, false);
    }

    /**
     * A {@code :name} no variable declares, placed in the BLOCK: it belongs to the block's compilation, not
     * the statement's, so it keeps the block's line and column however the statement is re-based
     * (live-verified — see {@link UndeclaredScriptVariableException}). A place on the statement's first line is
     * offset by the statement's column; one on a later line keeps its own, except in a SELECT … INTO's query,
     * which live offsets by that column on every line.
     */
    private static UndeclaredScriptVariableException inBlock(final UndeclaredScriptVariableException refused,
                                                             final int line, final int column,
                                                             final boolean offsetEveryLine) {
        final String message = refused.getMessage();
        final int[] place = ScriptedErrorPlace.placeOf(message);
        final int named = message == null ? -1 : message.lastIndexOf(INVALID_IDENTIFIER);
        if (place == null || named < 0 || !message.endsWith("'")) {
            return refused;
        }
        return new UndeclaredScriptVariableException(
            message.substring(named + INVALID_IDENTIFIER.length(), message.length() - 1),
            new SourcePosition(line + place[0] - 1, place[0] == 1 || offsetEveryLine ? column + place[1] : place[1]));
    }

    public Object handle(final FrostlakeParser.BeginEndBlockContext ctx) {
        // A syntax fault the engine's grammar reads past is refused before anything else is judged: a routine
        // statement's options out of order, on any branch, then a dotted bind.
        RoutineStatementRules.requireNestedOptionOrder(ctx);
        ScriptingNameValidator.rejectDottedBindVariables(ctx);
        // Then, in the order the block is written, each DECLARED TYPE and each integer literal too wide to read
        // (see ScriptTypeCompiler), each name a DECLARE section introduces twice, each routine statement's
        // language and invocation type (see RoutineStatementRules) and each INTO clause standing where none may
        // (see IntoClausePlacement). Live refuses these while COMPILING the block, ahead of every refusal below, so
        // none becomes a runtime exception or reaches the uncaught-exception wrapper below (live-verified). This
        // sits BEFORE enterScope/enterBlock deliberately — a refusal thrown after those runs would skip the finally
        // that unwinds them and leave the executor believing it is still inside a block, which breaks every later
        // block in the session.
        RoutineStatementRules.judgeNestedTypesAndRoutines(ctx);
        // A procedure's own block has its parameters in scope, and only those: the variables of a block that
        // called it are another scope.
        final Set<String> parameters = new HashSet<String>();
        for (final String name : proceduralExecutor.ownBlockParameterNames()) {
            parameters.add(ScriptingNameValidator.canonical(name));
        }
        // The DECLARE sections finish compiling next: a DECLARE item repeating a parameter's name is refused after
        // the pass above, and before an unnamed bind (live-verified).
        ScriptingNameValidator.rejectRedeclaration(ctx, parameters, true);
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
        // used earlier in the block (live-verified).
        ScriptingNameValidator.rejectRedeclaration(ctx, parameters, false);
        final DataType declaredReturn = proceduralExecutor.declaredReturnOfNextBlock();
        ScriptingNameValidator.validate(ctx, inScope, declaredReturn == null ? null
            : new DeclaredReturnJudge(declaredReturn, declaredTypesInScope(), queryExecutor),
            proceduralExecutor.returnKindOfNextBlock(), initialiserCompiler());
        // A variable named twice in one INTO clause is refused while the block compiles too — AFTER the
        // names are judged, since a statement carrying both faults answers with the name (live-verified).
        ScriptingNameValidator.rejectRepeatedIntoTargets(ctx);
        // And then each INTO target must name a variable its statement sees (see IntoTargetDeclarations).
        IntoTargetDeclarations.requireDeclared(ctx, inScope);

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
        // An outermost block whose DECLARE section holds a query writes every one of its names out as a bind.
        final boolean displacedBinding = outermostBlock
            && proceduralExecutor.bindNamesByDeclarations(declaresAQuery(ctx.declareSection()));
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
                            final Object stmtResult = visitStatement(stmtCtx);
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
                        stmtResult = visitStatement(stmtCtx);
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
                return AnonymousBlockResult.of(returnValue, typed == null ? AnonymousBlockResult.TEXT : typed);
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
                final String inner = "EXPRESSION_ERROR".equals(proceduralExecutor.getFailureKind())
                        && !(uncaught instanceof BlockExpressionTypeError)
                    ? ExpressionErrorPlace.placed(ctx, proceduralExecutor.getFailureLine(),
                        proceduralExecutor.getFailurePosition(), uncaught.getMessage())
                    : uncaught.getMessage();
                throw new RuntimeException("Uncaught exception of type '"
                    + proceduralExecutor.getFailureKind() + "' on line "
                    + proceduralExecutor.getFailureLine() + " at position "
                    + proceduralExecutor.getFailurePosition() + " : " + inner, uncaught);
            }
            throw uncaught;
        } finally {
            // Exit the scope and clean up cursors declared inside this block
            proceduralExecutor.exitScope();
            proceduralExecutor.restoreCursors(savedCursorNames);
            proceduralExecutor.exitBlock();
            if (outermostBlock) {
                proceduralExecutor.bindNamesByDeclarations(displacedBinding);
            }
        }

        return null;
    }

    /** The variable a value names as a bind written alone, parenthesised or not, or null for any other value. */
    private static FrostlakeParser.IdentifierContext bindWrittenAlone(final FrostlakeParser.BooleanExprContext value) {
        FrostlakeParser.BooleanExprContext written = value;
        while (written instanceof FrostlakeParser.ValueExprContext) {
            final FrostlakeParser.ExpressionContext expression = ((FrostlakeParser.ValueExprContext) written).expression();
            if (expression instanceof FrostlakeParser.BindVarExprContext) {
                return ((FrostlakeParser.BindVarExprContext) expression).identifier();
            }
            if (!(expression instanceof FrostlakeParser.ParenExprContext)) {
                return null;
            }
            written = ((FrostlakeParser.ParenExprContext) expression).booleanExpr();
        }
        return null;
    }

    /**
     * Whether a block's DECLARE section declares a cursor, or a RESULTSET with a query — what makes the account
     * write every name of the block out as a bind (live-verified: an exception, a scalar or a RESULTSET without a
     * query does not).
     */
    private static boolean declaresAQuery(final FrostlakeParser.DeclareSectionContext section) {
        if (section == null) {
            return false;
        }
        for (int i = 0; i < section.getChildCount(); i++) {
            if (section.getChild(i) instanceof FrostlakeParser.DeclarationItemContext) {
                final FrostlakeParser.DeclarationItemContext item =
                    (FrostlakeParser.DeclarationItemContext) section.getChild(i);
                if (item.CURSOR() != null || item.RESULTSET() != null && (item.DEFAULT() != null
                        || item.COLON_EQ() != null)) {
                    return true;
                }
            }
        }
        return false;
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
     * Snowflake code ({@code 100051} / {@code '22012'}); a NULL where no NULL may go — a NOT NULL
     * column, a null-strict procedure's CALL — to {@code 100072} / {@code '00000'}; a wrong argument
     * count, an unknown compression method, an undecodable compressed payload and a dynamic text of more
     * statements than the session's MULTI_STATEMENT_COUNT ({@code 8} / {@code '0A000'}) map to theirs; any other engine
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
        } else if (e instanceof BlockExpressionTypeError) {
            // Live-verified: argument types an operator or call does not take are 1044 / 42P13, a parameter that
            // cannot be converted to the type it is compared with 1038 / 22023, and a cast the matrix refuses
            // 1007 / 22023.
            if (message.contains("Can not convert parameter")) {
                code = 1038;
                state = "22023";
            } else if (message.contains("invalid type [CAST(")) {
                code = 1007;
                state = "22023";
            } else {
                code = 1044;
                state = "42P13";
            }
        } else if (e instanceof StatementCountMismatch) {
            code = StatementCountMismatch.CODE;
            state = StatementCountMismatch.STATE;
        } else if (message.contains("Division by zero")) {
            code = 100051;
            state = "22012";
        } else if (message.contains("NULL result in a non-nullable column")) {
            code = 100072;
            state = "00000";
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
                    && !(e instanceof SqlSyntaxException) && !(e instanceof BlockExpressionTypeError);
            // EXPRESSION_ERROR covers errors in expression evaluation
            case "EXPRESSION_ERROR":
                return e instanceof ArithmeticException || e instanceof BlockExpressionTypeError
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
