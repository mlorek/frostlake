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
import dev.frostlake.executor.ApartRecording;
import dev.frostlake.executor.BlockExpressionTypeError;
import dev.frostlake.executor.BlockExpressionTypes;
import dev.frostlake.executor.BodyAbort;
import dev.frostlake.executor.BodyPlacedError;
import dev.frostlake.executor.CallDepth;
import dev.frostlake.executor.CalledRoutineSecrets;
import dev.frostlake.executor.ContinueHandler;
import dev.frostlake.executor.DeclarationTypes;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.FunctionBodyCompileError;
import dev.frostlake.executor.InfiniteBindRefusal;
import dev.frostlake.executor.LeadingCommentOffset;
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SQLCommandVisitor;
import dev.frostlake.executor.ScriptedErrorPlace;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StatementCanceledException;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.executor.UndeclaredScriptVariableException;
import dev.frostlake.executor.UnrunBlocks;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.procedural.BuiltInExceptions;
import dev.frostlake.executor.procedural.CalledProcedureFailure;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.executor.procedural.UserDefinedException;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
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
                final FrostlakeParser.VariableNameContext bound = bindWrittenAlone(initialiser);
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
     * its second line is line 2 — so its places are re-based on its first token while it runs. A CALL is such
     * a statement: {@code CALL p(x)} names the identifier X at position 8 (live-verified).
     */
    private Object visitStatement(final FrostlakeParser.StatementContext stmtCtx) {
        final int line = stmtCtx.getStart().getLine();
        final int column = stmtCtx.getStart().getCharPositionInLine();
        if (stmtCtx.proceduralStatement() != null && !isCall(stmtCtx.proceduralStatement())) {
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

    /** Whether a scripting statement is a CALL, with INTO or without: a statement of its own, placed as one is. */
    private static boolean isCall(final FrostlakeParser.ProceduralStatementContext procedural) {
        return procedural.callStatement() != null || procedural.callIntoStatement() != null;
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

    /**
     * Runs a block. The top block of a body no CALL made a transaction scope of — a block the session sends, a
     * text that is a block — is a scope of its own: it adds to a transaction that is open when it starts and may
     * not end it, and a transaction it begins is its own to end. One it leaves open is rolled back, and the block
     * reports that, whatever it let out besides (live-verified). A block nested in another shares its scope.
     *
     * @param ctx the block
     * @return what the block answers
     */
    public Object handle(final FrostlakeParser.BeginEndBlockContext ctx) {
        if (proceduralExecutor.entersTopBlockOfBody()) {
            // A plain word after the END of a body's top block is refused before anything runs.
            BlockEndWord.requireKnown(ctx);
        } else if (UnrunBlocks.endsUnrun(ctx)) {
            // A nested block that ends in a word and declares nothing runs none of its statements, wherever the
            // text it was read from begins.
            return null;
        }
        if (!proceduralExecutor.entersTopBlockOfBody() || proceduralExecutor.isBodyScoped()) {
            return run(ctx);
        }
        // A block compiles whole: a function it calls that cannot use its secrets is refused before it runs.
        CalledRoutineSecrets.require(ctx, queryExecutor);
        final TransactionManager transactions = queryExecutor.getTransactionManager();
        transactions.enterScope();
        final int level = transactions.scopeDepth();
        Object answer = null;
        RuntimeException failure = null;
        boolean leftOpen = false;
        try {
            answer = run(ctx);
        } catch (final RuntimeException failed) {
            failure = failed;
        } finally {
            leftOpen = transactions.exitScope();
        }
        if (CallDepth.endedAt(level)) {
            // A block past the depth bodies run to was ended where it issued a statement: that is what the
            // statement that ran the block reports (see CallDepth).
            throw new RuntimeException(CallDepth.REFUSAL);
        }
        if (leftOpen) {
            throw new RuntimeException(TransactionManager.SCOPE_LEFT_OPEN);
        }
        if (failure != null) {
            throw failure;
        }
        return answer;
    }

    private Object run(final FrostlakeParser.BeginEndBlockContext ctx) {
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
        // What a SHOW statement's shape rules out, and a DESCRIBE TAG, are refused while the block compiles too, ahead
        // of an unnamed bind and of the names (see ShowShapeCompiler).
        ShowShapeCompiler.requireShapes(ctx, new ShowCommandHandler(catalog, queryExecutor));
        // An unnamed bind is refused while the block compiles, before any statement of it runs.
        ScriptingNameValidator.rejectUnnamedBinds(ctx);
        ScriptingNameValidator.rejectUnknownLoggingNames(ctx);
        // An IDENTIFIER() text that names nothing is refused while the block compiles, placed in the block.
        BlockIdentifierLiterals.require(ctx);
        // So is a BREAK, a CONTINUE or an EXIT that stands in no loop of the body, or names no loop around it.
        if (proceduralExecutor.entersTopBlockOfBody()) {
            LoopControlPlacement.require(ctx);
        }
        // Compile the WHOLE block before running any of it, as Snowflake does: a name that resolves to
        // nothing is refused even on a branch this run will not take. Seeded with what is already in
        // scope — a stored procedure's parameters, or an enclosing block's variables.
        final Set<String> inScope = new HashSet<String>(proceduralExecutor.getAllVariables().keySet());
        inScope.addAll(proceduralExecutor.saveCursorNames());
        inScope.addAll(proceduralExecutor.declaredExceptionNames());
        // A block nested in a FOR loop over a cursor reads the loop's record as the loop's own statements do.
        final Set<String> loopRecords = proceduralExecutor.runningLoopRecords();
        // A procedure's OWN block also judges its direct RETURNs against the declared RETURNS type in
        // the same pass, so whichever fault comes first in the body is the one reported, as live does.
        // A name introduced twice in one scope is refused before any other name is judged, even one
        // used earlier in the block (live-verified).
        ScriptingNameValidator.rejectRedeclarationToRun(ctx, parameters,
            proceduralExecutor.entersTopBlockOfBody());
        // A cursor declared over a name that is no RESULTSET where it stands is refused with the body's names.
        if (proceduralExecutor.entersTopBlockOfBody()) {
            CursorSources.require(ctx);
        }
        final DataType declaredReturn = proceduralExecutor.declaredReturnOfNextBlock();
        ScriptingNameValidator.validate(ctx, inScope, loopRecords, declaredReturn == null ? null
            : new DeclaredReturnJudge(declaredReturn, declaredTypesInScope(), queryExecutor),
            proceduralExecutor.returnKindOfNextBlock(), initialiserCompiler());
        // A variable named twice in one INTO clause is refused while the block compiles too — AFTER the
        // names are judged, since a statement carrying both faults answers with the name (live-verified).
        ScriptingNameValidator.rejectRepeatedIntoTargets(ctx);
        // And then each INTO target must name a variable its statement sees (see IntoTargetDeclarations).
        IntoTargetDeclarations.requireDeclared(ctx, inScope);
        // A tag function one of the block's own expressions calls is answered now, a missing tag being the
        // block's refusal (see CompiledTagCalls).
        CompiledTagCalls.require(ctx, queryExecutor);

        // Enter a new scope for this block; also snapshot cursors so inner-declared
        // cursors are cleaned up when the block exits.
        proceduralExecutor.enterScope();
        proceduralExecutor.enterBlock();
        // A handler names the exceptions this block sees: its own and an enclosing block's (see matchesException).
        final int exceptionDepth = proceduralExecutor.exceptionScopeDepth();
        final boolean outermostBlock = proceduralExecutor.getBlockDepth() == 1;
        // The top block of a body, the session's or a called procedure's, words what the body lets out.
        final boolean outermostOfBody = proceduralExecutor.isOutermostBlockOfBody();
        if (outermostBlock) {
            // A failure recorded by an EARLIER block must not label this one's — nothing between two
            // top-level blocks runs through the per-statement clear.
            proceduralExecutor.clearStatementFailure();
            // And no statement of the script has run yet (see ProceduralExecutor#noteStatementQueryId).
            proceduralExecutor.startStatementQueryIds();
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
                            findMatchingHandler(ctx.exceptionSection(), e, exceptionDepth);
                        if (handlerCtx == null || handlerCtx.CONTINUE() == null) {
                            return false;
                        }
                        runHandler(ctx, handlerCtx, e);
                        return true;
                    }
                });
                try {
                    for (final FrostlakeParser.StatementContext stmtCtx : ctx.statementList().statement()) {
                        boolean exitBlock = false;
                        try {
                            // Each statement in a block reads its own clock (live-verified).
                            StatementClock.advance();
                            runStatement(stmtCtx, false);
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
                                findMatchingHandler(ctx.exceptionSection(), e, exceptionDepth);
                            if (handlerCtx == null) {
                                throw e;   // no matching handler — propagate to the enclosing block
                            }
                            runHandler(ctx, handlerCtx, e);
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
                    try {
                        runStatement(stmtCtx, false);
                    } catch (final RuntimeException failure) {
                        // Name the statement that failed, for the uncaught-exception message below.
                        proceduralExecutor.recordStatementFailure(stmtCtx.getStart().getLine(),
                            stmtCtx.getStart().getCharPositionInLine());
                        throw failure;
                    }
                    proceduralExecutor.clearStatementFailure();
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
            if (outermostOfBody && uncaught instanceof CalledProcedureFailure
                    && proceduralExecutor.getFailureLine() > 0) {
                // What a called procedure let out is the calling statement's failure here, whatever was raised
                // inside (live-verified).
                final int[] failed = placedPastOpeningComments(ctx, proceduralExecutor.getFailureLine(),
                    proceduralExecutor.getFailurePosition());
                throw new RuntimeException("Uncaught exception of type 'STATEMENT_ERROR' on line "
                    + failed[0] + " at position " + failed[1] + " : " + uncaught.getMessage(), uncaught);
            }
            // An exception no handler caught reads with live's wording at the TOP of its body
            // only — an enclosing block may still catch it by name, and SQLERRM must keep the raw
            // text until then.
            if (outermostOfBody && uncaught.getExceptionName() != null) {
                final int[] raised = placedPastOpeningComments(ctx, uncaught.getSourceLine(),
                    uncaught.getSourcePosition());
                final ProceduralException formatted = new ProceduralException(
                    uncaught.getErrorCode(), uncaught.uncaughtMessage(raised[0], raised[1]),
                    uncaught.getExceptionName());
                formatted.setSourcePosition(uncaught.getSourceLine(), uncaught.getSourcePosition());
                throw formatted;
            }
            throw uncaught;
        } catch (final BodyAbort ended) {
            // No exception of this body's: it takes no sentence of the body's own (see BodyAbort).
            throw ended;
        } catch (final RuntimeException uncaught) {
            // A STATEMENT that failed and no handler caught reads the same way, live-verified:
            // "Uncaught exception of type 'STATEMENT_ERROR' on line L at position P : <error>",
            // anchored on the statement that failed however deeply it sat (a nested block, an IF
            // branch, a loop body). Only the OUTERMOST block of a body wraps — an enclosing block may still
            // handle it, SQLERRM keeps the raw text, and a block that failed to COMPILE never gets
            // here with a statement position, so a syntax error stays a plain syntax error. A called
            // procedure's body wraps what it lets out as the session's script does, so an error that
            // leaves several bodies carries one sentence for each (live-verified).
            if (outermostOfBody && proceduralExecutor.hasFailurePlace()
                    && !(uncaught instanceof UndeclaredScriptVariableException)
                    && !(uncaught instanceof StatementCanceledException)) {
                if (compilesWithTheCaller(uncaught)) {
                    // A function's block body compiles with the statement that calls the function: an expression
                    // of it that does not compile is that statement's refusal, bare, where one that fails as it
                    // is evaluated is the body's uncaught exception (live-verified).
                    throw uncaught;
                }
                if (uncaught instanceof FunctionBodyCompileError && ExpressionErrorPlace.assignsAValueAt(ctx,
                        proceduralExecutor.getFailureLine(), proceduralExecutor.getFailurePosition())) {
                    // A value a LET or an assignment takes from such a function is compiled with the block.
                    throw uncaught;
                }
                final String inner = "EXPRESSION_ERROR".equals(proceduralExecutor.getFailureKind())
                        && !(uncaught instanceof BlockExpressionTypeError) && !BodyPlacedError.isIn(uncaught)
                    ? ExpressionErrorPlace.placed(ctx, proceduralExecutor.getFailureLine(),
                        proceduralExecutor.getFailurePosition(), uncaught.getMessage())
                    : uncaught.getMessage();
                final int[] failed = placedPastOpeningComments(ctx, proceduralExecutor.getFailureLine(),
                    proceduralExecutor.getFailurePosition());
                throw new RuntimeException("Uncaught exception of type '"
                    + proceduralExecutor.getFailureKind() + "' on line "
                    + failed[0] + " at position " + failed[1] + " : " + inner, uncaught);
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
    private static FrostlakeParser.VariableNameContext bindWrittenAlone(final FrostlakeParser.BooleanExprContext value) {
        FrostlakeParser.BooleanExprContext written = value;
        while (written instanceof FrostlakeParser.ValueExprContext) {
            final FrostlakeParser.ExpressionContext expression = ((FrostlakeParser.ValueExprContext) written).expression();
            if (expression instanceof FrostlakeParser.BindVarExprContext) {
                return ((FrostlakeParser.BindVarExprContext) expression).variableName();
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

    /**
     * The first handler in {@code section} that names {@code e}, or null if none: a handler names one exception or
     * several joined by OR. A failure that is no exception of the body is no handler's (see BodyAbort).
     *
     * @param exceptionDepth what the block noted of its exception scope when it started
     */
    private FrostlakeParser.ExceptionHandlerContext findMatchingHandler(
            final FrostlakeParser.ExceptionSectionContext section, final Exception e, final int exceptionDepth) {
        if (e instanceof BodyAbort) {
            return null;
        }
        for (final FrostlakeParser.ExceptionHandlerContext handlerCtx : section.exceptionHandler()) {
            for (final FrostlakeParser.ExceptionConditionContext condition : handlerCtx.exceptionCondition()) {
                if (shouldHandleException(condition, e, exceptionDepth)) {
                    return handlerCtx;
                }
            }
        }
        return null;
    }

    /**
     * Run a matched handler's statements with SQLCODE / SQLERRM / SQLSTATE bound for its duration and
     * restored afterwards (nested handlers shadow, not clobber — an inner block's exitScope would
     * otherwise propagate its values into an enclosing handler's scope).
     */
    private void runHandler(final FrostlakeParser.BeginEndBlockContext block,
                            final FrostlakeParser.ExceptionHandlerContext handlerCtx, final Exception e) {
        final Object prevSqlCode = proceduralExecutor.getVariable("SQLCODE");
        final Object prevSqlErrm = proceduralExecutor.getVariable("SQLERRM");
        final Object prevSqlState = proceduralExecutor.getVariable("SQLSTATE");
        // SQLERRM and SQLSTATE are names inside a handler alone: once this one has ended they are none again,
        // unless an enclosing handler or a declaration of the block's own made them one.
        final boolean hadSqlErrm = proceduralExecutor.hasVariable("SQLERRM");
        final boolean hadSqlState = proceduralExecutor.hasVariable("SQLSTATE");
        bindHandlerErrorVariables(e);
        proceduralExecutor.pushHandledException(e);
        proceduralExecutor.enterCompound();
        // What the block's BEGIN section declared with LET is none of the handler's to read (see HandlerLets).
        proceduralExecutor.enterHandler(beginSectionLets(block));
        // A statement of the handler that fails is the failure the block lets out, named at its own place; the
        // handled exception raised again keeps the place of the statement that first raised it (live-verified).
        final int raisedLine = proceduralExecutor.getFailureLine();
        final int raisedPosition = proceduralExecutor.getFailurePosition();
        final String raisedKind = proceduralExecutor.getFailureKind();
        proceduralExecutor.clearStatementFailure();
        try {
            for (final FrostlakeParser.StatementContext stmtCtx : handlerCtx.statementList().statement()) {
                StatementClock.advance();
                proceduralExecutor.atHandlerStatement(stmtCtx);
                try {
                    runStatement(stmtCtx, true);
                } catch (final RuntimeException failed) {
                    if (failed == e || failed.getCause() == e) {
                        proceduralExecutor.restoreStatementFailure(raisedLine, raisedPosition, raisedKind);
                    } else {
                        proceduralExecutor.recordStatementFailure(stmtCtx.getStart().getLine(),
                            stmtCtx.getStart().getCharPositionInLine());
                    }
                    throw failed;
                }
                queryExecutor.getTransactionManager().autocommitStatementEnd();
                // A RETURN, a BREAK or a CONTINUE reached inside a branch or a loop of the handler ends the
                // handler there, as it ends a block's own statements.
                if (proceduralExecutor.hasReturned() || proceduralExecutor.hasBreak()
                        || proceduralExecutor.hasContinue()) {
                    break;
                }
            }
        } finally {
            proceduralExecutor.exitHandler();
            proceduralExecutor.exitCompound();
            proceduralExecutor.popHandledException();
            proceduralExecutor.setVariable("SQLCODE", prevSqlCode);
            proceduralExecutor.restoreVariable("SQLERRM", hadSqlErrm, prevSqlErrm);
            proceduralExecutor.restoreVariable("SQLSTATE", hadSqlState, prevSqlState);
        }
    }

    /** The names a block's BEGIN section declares with LET — variables, cursors and RESULTSETs — upper-cased. */
    private Set<String> beginSectionLets(final FrostlakeParser.BeginEndBlockContext block) {
        final Set<String> names = new HashSet<String>();
        for (final FrostlakeParser.StatementContext statement : block.statementList().statement()) {
            final FrostlakeParser.ProceduralStatementContext procedural = statement.proceduralStatement();
            if (procedural != null && procedural.letStatement() != null) {
                names.add(visitor.getText(procedural.letStatement().variableName()).toUpperCase(Locale.ROOT));
            }
        }
        return names;
    }

    /**
     * Undo a FAILED statement's buffered writes before its exception handler runs. With per-statement
     * autocommit, a non-explicit active transaction holds at most the failed statement's own changes —
     * rolling it back is Snowflake's statement-level rollback. An EXPLICIT (BEGIN-started) transaction
     * is left open untouched, matching the top-level engine behavior.
     */
    /**
     * Runs one statement of the block, or of one of its exception handlers, and completes it. A child statement (see
     * {@link #isChildStatement}) takes its query ID, holding its result for RESULT_SCAN(LAST_QUERY_ID()), and its
     * QUERY_HISTORY row, a FAILED one when it fails (see {@code QueryExecutor.beginChildStatement}); a block running
     * as SQL the engine composed for itself (see {@code QueryExecutor.executeUnrecorded}) hands out none. A SQL
     * statement of the block's own then sets the DML trio and ACTIVITY_COUNT, as a handler's statement never has, and
     * any statement that took a query ID, itself or through what it ran, sets SQLID.
     *
     * @param stmtCtx   the statement
     * @param inHandler whether it is a statement of an exception handler, which runs as written, unplaced
     */
    private void runStatement(final FrostlakeParser.StatementContext stmtCtx, final boolean inHandler) {
        if (issuesAStatement(stmtCtx)) {
            // What a body past the depth bodies run to may not do (see CallDepth).
            CallDepth.requireMayIssue(queryExecutor.getTransactionManager().scopeDepth());
        }
        final String queryIdBefore = proceduralExecutor.lastQueryIdOfSession();
        final String text = visitor.getOriginalText(stmtCtx);
        final QueryHistory row = isChildStatement(stmtCtx) ? queryExecutor.beginChildStatement(text, stmtCtx) : null;
        // A statement that commits work in a session of its own goes to the write-ahead log by itself, apart from
        // the transaction running the block (see ApartRecording).
        final ApartRecording apart = queryExecutor.beginApartRecording(stmtCtx);
        final Object stmtResult;
        try {
            if (bindsAsAStatement(stmtCtx)) {
                InfiniteBindRefusal.requireFiniteBinds(stmtCtx, proceduralExecutor);
            }
            stmtResult = inHandler ? visitor.visit(stmtCtx) : visitStatement(stmtCtx);
            if (apart != null) {
                apart.complete();
            }
        } catch (final RuntimeException failure) {
            queryExecutor.failChildStatement(row, text, failure);
            throw failure;
        } finally {
            if (apart != null) {
                queryExecutor.endApartRecording(apart, text, false);
            }
        }
        if (stmtResult instanceof ResultSet) {
            if (row != null) {
                queryExecutor.completeChildStatement(row, text, (ResultSet) stmtResult);
            } else if (!inHandler && !queryExecutor.isUnrecorded()) {
                // A scripting statement that answered a result set of its own still hands it out by query ID.
                queryExecutor.getResultCache().cacheResult(text, (ResultSet) stmtResult);
            }
            if (!inHandler) {
                proceduralExecutor.recordSqlRowCount((ResultSet) stmtResult);
            }
        } else {
            queryExecutor.completeChildStatement(row, text, null);
            if (!inHandler && stmtCtx.proceduralStatement() == null) {
                // A plain SQL statement (TRUNCATE, some DDL) that surfaced no result set still resets the DML
                // trio and counts one status line.
                proceduralExecutor.recordStatementWithoutResult();
            }
        }
        proceduralExecutor.noteStatementQueryId(queryIdBefore);
    }

    /**
     * Whether a statement of a block is one the body issues to the engine where it stands: a SQL statement, a
     * CALL, an EXECUTE IMMEDIATE, a SELECT … INTO and a cursor's OPEN. A scripting statement issues none of its
     * own; a query one of its expressions reads is issued where the expression is evaluated.
     */
    private static boolean issuesAStatement(final FrostlakeParser.StatementContext stmtCtx) {
        final FrostlakeParser.ProceduralStatementContext procedural = stmtCtx.proceduralStatement();
        return isChildStatement(stmtCtx) || procedural.selectIntoStatement() != null
            || procedural.openStatement() != null || procedural.asyncStatement() != null;
    }

    /**
     * Whether a statement of a block binds the block's variables as a statement does, rather than as the block's
     * own expressions do: a SQL statement, a SELECT … INTO and a CALL, with INTO or without.
     */
    private static boolean bindsAsAStatement(final FrostlakeParser.StatementContext stmtCtx) {
        final FrostlakeParser.ProceduralStatementContext procedural = stmtCtx.proceduralStatement();
        return procedural == null || procedural.selectIntoStatement() != null || procedural.callStatement() != null
            || procedural.callIntoStatement() != null;
    }

    /**
     * Whether a statement of a block is a statement of the session's in its own right, a child statement with a
     * query ID and a QUERY_HISTORY row of its own: a SQL statement, a CALL (with INTO or without) or an EXECUTE
     * IMMEDIATE. A scripting
     * statement - LET, an assignment, IF, CASE, a loop, RETURN, a nested block, OPEN, FETCH, SELECT ... INTO - is
     * not; the queries it runs and the statements of its bodies are, and record themselves.
     */
    public static boolean isChildStatement(final FrostlakeParser.StatementContext stmtCtx) {
        final FrostlakeParser.ProceduralStatementContext procedural = stmtCtx.proceduralStatement();
        return procedural == null || procedural.callStatement() != null || procedural.callIntoStatement() != null
            || procedural.systemCallStatement() != null || procedural.executeImmediateStatement() != null;
    }

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
        final String message = FailureCode.messageOf(e);
        final FailureCode read = FailureCode.of(e);
        final int code = read.code();
        final String state = read.state();
        proceduralExecutor.setVariable("SQLCODE", code);
        // An exception declared without a message has none to read.
        proceduralExecutor.setVariable("SQLERRM",
            e instanceof ProceduralException && e.getMessage() == null ? null : message);
        proceduralExecutor.setVariable("SQLSTATE", state);
    }

    /**
     * Whether a failure is an expression of a SQL function's block body that does not compile: the body compiles
     * with the statement calling the function, so the refusal is no exception of the body's (see
     * {@link FunctionBodyCompileError}).
     */
    private boolean compilesWithTheCaller(final Exception e) {
        return proceduralExecutor.runsFunctionBody() && e instanceof RuntimeException
            && !(e instanceof ProceduralException)
            && "EXPRESSION_ERROR".equals(proceduralExecutor.getFailureKind())
            && SqlCompilationError.isCompilationError(e.getMessage());
    }

    private boolean shouldHandleException(final FrostlakeParser.ExceptionConditionContext condition, final Exception e,
                                          final int exceptionDepth) {
        // A statement's timeout or cancel ends the whole statement: no handler of its blocks takes it.
        if (compilesWithTheCaller(e) || e instanceof StatementCanceledException) {
            return false;
        }
        // Check if this is an "OTHER" handler (catch-all)
        if (condition.OTHER() != null) {
            return true;
        }

        // Check if it's a named exception handler
        if (condition.variableName() != null) {
            final String exceptionName = visitor.getText(condition.variableName()).toUpperCase(Locale.ROOT);
            return matchesSnowflakeException(exceptionName, e, exceptionDepth);
        }

        return false;
    }

    private boolean matchesSnowflakeException(final String exceptionName, final Exception e,
                                              final int exceptionDepth) {
        switch (exceptionName) {
            // STATEMENT_ERROR is a statement the block ran and the engine refused: a missing object, a value that
            // does not convert, and a syntax error in a text only read when its statement runs (EXECUTE
            // IMMEDIATE's). An exception the script raised is caught by its own name alone, and a refusal raised
            // by a scripting statement's expression is an EXPRESSION_ERROR (live-verified).
            case "STATEMENT_ERROR":
                if (e instanceof CalledProcedureFailure) {
                    // An exception a called procedure raised and let out is the CALL's failure (live-verified).
                    return true;
                }
                if (e instanceof ProceduralException) {
                    // An exception the script raised by this name is caught by it (live-verified).
                    return ((ProceduralException) e).getDeclaration() == BuiltInExceptions.STATEMENT_ERROR;
                }
                return e instanceof RuntimeException && !(e instanceof BlockExpressionTypeError)
                    && !"EXPRESSION_ERROR".equals(proceduralExecutor.getFailureKind());
            // EXPRESSION_ERROR is a refusal raised while a scripting statement's own expression is read: a LET's
            // or an assignment's value, a RETURN's, a condition.
            case "EXPRESSION_ERROR":
                if (e instanceof ProceduralException) {
                    return !(e instanceof CalledProcedureFailure)
                        && ((ProceduralException) e).getDeclaration() == BuiltInExceptions.EXPRESSION_ERROR;
                }
                return e instanceof BlockExpressionTypeError
                    || e instanceof RuntimeException && "EXPRESSION_ERROR".equals(proceduralExecutor.getFailureKind());
            // Any other name is an exception the block declares or sees declared around it, and it catches that
            // DECLARATION: an exception of the same name a nested block, a called procedure or a dynamic text
            // declared for itself and let out is another one (live-verified). A name that stands for no
            // declaration never gets here — the block is refused while it compiles.
            default:
                if (e instanceof ProceduralException && !(e instanceof CalledProcedureFailure)) {
                    final UserDefinedException named =
                        proceduralExecutor.exceptionVisibleAt(exceptionName, exceptionDepth);
                    return named != null && ((ProceduralException) e).getDeclaration() == named;
                }
                return false;
        }
    }
    /**
     * The place of a statement that failed or raised, as the wrapper tells it: in a text the session sent that opens
     * with comments, the first token after them is line 1, position 0, as for every other place of the text —
     * {@code /* c *\/ BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END} fails "on line 1 at position 6". A body that
     * runs as a frame of its own keeps its comments: a procedure's is counted from where its text begins
     * (live-verified).
     */
    private int[] placedPastOpeningComments(final FrostlakeParser.BeginEndBlockContext block, final int line,
                                            final int position) {
        final CharStream source = block.getStart() == null ? null : block.getStart().getInputStream();
        if (source == null || source.size() == 0 || !proceduralExecutor.runsSessionText()) {
            return new int[]{line, position};
        }
        final SourcePosition origin = LeadingCommentOffset.of(source.getText(Interval.of(0, source.size() - 1)));
        return LeadingCommentOffset.rebase(origin, line, position);
    }
}
