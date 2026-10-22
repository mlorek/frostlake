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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.UnknownUnitWord;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.executor.procedural.AsyncJob;
import dev.frostlake.executor.procedural.AsyncJobState;
import dev.frostlake.executor.procedural.AsyncStatement;
import dev.frostlake.executor.procedural.AwaitStatement;
import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.BinaryExpression;
import dev.frostlake.executor.procedural.BuiltInExceptions;
import dev.frostlake.executor.procedural.CallStatement;
import dev.frostlake.executor.procedural.CaseStatement;
import dev.frostlake.executor.procedural.CloseStatement;
import dev.frostlake.executor.procedural.Cursor;
import dev.frostlake.executor.procedural.DeclareCursorStatement;
import dev.frostlake.executor.procedural.DeclareExceptionStatement;
import dev.frostlake.executor.procedural.DeclareResultSetStatement;
import dev.frostlake.executor.procedural.DeclareStatement;
import dev.frostlake.executor.procedural.EvaluateStatement;
import dev.frostlake.executor.procedural.ExecuteImmediateExpression;
import dev.frostlake.executor.procedural.FetchStatement;
import dev.frostlake.executor.procedural.ForStatement;
import dev.frostlake.executor.procedural.FunctionCallExpression;
import dev.frostlake.executor.procedural.IfCondition;
import dev.frostlake.executor.procedural.IfStatement;
import dev.frostlake.executor.procedural.LiteralExpression;
import dev.frostlake.executor.procedural.LoopStatement;
import dev.frostlake.executor.procedural.OpenStatement;
import dev.frostlake.executor.procedural.ProceduralBlock;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.executor.procedural.RaiseStatement;
import dev.frostlake.executor.procedural.RepeatStatement;
import dev.frostlake.executor.procedural.ResultSetVariable;
import dev.frostlake.executor.procedural.ReturnStatement;
import dev.frostlake.executor.procedural.ReturnTableStatement;
import dev.frostlake.executor.procedural.SessionVarRefExpression;
import dev.frostlake.executor.procedural.SetStatement;
import dev.frostlake.executor.procedural.SqlScalarExpression;
import dev.frostlake.executor.procedural.SqlStatement;
import dev.frostlake.executor.procedural.Statement;
import dev.frostlake.executor.procedural.StatementValueExpression;
import dev.frostlake.executor.procedural.SubqueryExpression;
import dev.frostlake.executor.procedural.UnaryExpression;
import dev.frostlake.executor.procedural.UserDefinedException;
import dev.frostlake.executor.procedural.VariableExpression;
import dev.frostlake.executor.procedural.WhenClause;
import dev.frostlake.executor.procedural.WhileStatement;
import dev.frostlake.executor.udf.HandlerStateScope;
import dev.frostlake.executor.udf.StatementHandlerStates;
import dev.frostlake.functions.ArrayArgumentKind;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.TransactionAbortedMessages;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.ConvertedValueType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.CodePointText;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProceduralExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ProceduralExecutor.class);

    private final ScopeManager scope = new ScopeManager();
    private final CursorManager cursorManager = new CursorManager();
    // Active CONTINUE handlers (innermost on top), pushed while a block with an EXCEPTION section runs;
    // loop executors consult the top so a matching loop-body error resumes at the next iteration.
    private final Deque<ContinueHandler> continueHandlers = new ArrayDeque<>();
    // Declared types of procedural variables (DECLARE x INTEGER / LET x INTEGER := …), consulted on
    // every assignment (:=, SELECT INTO, FETCH INTO) to coerce the value the way Snowflake casts on
    // assignment to a typed variable. Scoped as the variables are (see DeclaredTypeScopes): an inner
    // re-declaration's type ends with its body; cleared per top-level statement together with
    // cursors/exceptions.
    private final Map<String, DataType> variableTypes = new HashMap<>();

    /** What each open scope's exit puts back into the type maps (see {@link DeclaredTypeScopes}). */
    private final DeclaredTypeScopes typeScopes = new DeclaredTypeScopes();

    /** Whether the running outermost block's DECLARE section makes its names binds (see {@link #bindsBlockNames}). */
    private boolean namesBoundByDeclarations;

    /**
     * The type the CONVERSION produced, for the names whose conversion types narrower than the
     * declaration. A bare RETURN reports this, while the variable itself keeps its declared type:
     * over {@code x NUMBER DEFAULT TRUE} the account answers NUMBER(38,0) to SYSTEM$TYPEOF(x) and
     * NUMBER(2,0) to RETURN x. Empty for every name whose conversion kept the declared type.
     */
    private final Map<String, DataType> conversionTypes = new HashMap<>();
    /**
     * The answers {@code x := (CALL p())} gave names, held as the CALL handed them back, by name: the conversion to
     * the name's type waits for an expression that reads it (see {@link #readHeld}). An entry counts while the name
     * still holds that very answer.
     */
    private final Map<String, Object> callAnswers = new HashMap<>();
    /**
     * The FOR-loop counters in flight, by name. A counter binds as NUMBER(9,0) — the type a
     * {@code :i} carries into a statement and a column derived from it declares — but it is no
     * DECLARED name: a RETURN of it is still text, so it lives beside {@link #variableTypes} rather
     * than in it.
     */
    private final Map<String, DataType> counterTypes = new HashMap<>();

    /**
     * The block expressions whose argument types passed (see {@link BlockExpressionTypes}), each with the types
     * of the names it was judged over and the declared type it converted to: a pass holds while those do, since a
     * refusal turns on the types' families alone. Cleared when an outermost block starts.
     */
    private final Map<ParserRuleContext, Map<String, DataType>> judgedPasses = new IdentityHashMap<>();

    /** The declared type each pass in {@link #judgedPasses} converted to, or null for none. */
    private final Map<ParserRuleContext, DataType> judgedPassTargets = new IdentityHashMap<>();

    /**
     * The SQL parts of block expressions whose subqueries compiled (see {@link BlockExpressionSubqueries}): a loop
     * evaluates the same part again, and what compiled once compiles again. Cleared when an outermost block starts.
     */
    private final Set<Expression> compiledSubqueryHolders = Collections.newSetFromMap(new IdentityHashMap<>());
    /**
     * The own static type of an untyped declaration's initialiser, by name, until the name is assigned again.
     * A bare RETURN of the name reports it rather than the declared type: {@code LET x := 1; RETURN x} is a
     * NUMBER(1,0) where the declaration and every other use of x is a NUMBER(38,0), and after {@code x := 2} the
     * RETURN is a NUMBER(38,0) too (live-verified).
     */
    private final Map<String, DataType> initialiserTypes = new HashMap<>();
    /** The static types of the cursor records' fields in flight, keyed {@code REC.FIELD} as their values are. */
    private final Map<String, DataType> recordFieldTypes = new HashMap<>();
    /** The script-supplied name of the query ID of the last statement a script ran (see #noteStatementQueryId). */
    private static final String SQLID = "SQLID";
    /** What a bare RETURN of SQLROWCOUNT declares its result column (live-verified through the driver). */
    private static final DataType ROW_COUNT_RETURN = new NumericType("NUMBER", 0, 0);

    /** What a bare RETURN of SQLROWCOUNT declares before any statement of the block has run. */
    private static final DataType UNCOUNTED_RETURN = new StringType("VARCHAR", StringResultWidths.UNBOUNDED);
    private static final DataType COUNTER_TYPE = new NumericType("NUMBER", 9, 0);
    private static final long COUNTER_LIMIT = 999_999_999L;
    private static final DataType WHOLE_NUMBER_TYPE = new NumericType("NUMBER", 38, 0);
    /** The RETURNS frame of the procedure whose body runs, or null outside one. */
    private DeclaredReturnFrame declaredReturn;
    /** The declared RETURNS type the executed RETURN converted to, or null when it kept its own. */
    private DataType returnedDeclaredType;
    /**
     * The static type of the executed RETURN's value when it kept its own and has one — see
     * {@link #staticTypeOfReturn}.
     */
    private DataType returnedStaticType;
    /** How many compound statements' bodies — IF, CASE, loops, handlers — are running. */
    private int compoundDepth;
    /**
     * The one script-supplied name that is NUMERIC. Its siblings SQLCODE, SQLSTATE and SQLERRM are
     * text, so only this one keeps its number when RETURN names it directly.
     */
    /** Script-supplied variables that carry their OWN type (so RETURN of one is not text):
     *  the numeric SQLROWCOUNT/ACTIVITY_COUNT and the boolean SQLFOUND/SQLNOTFOUND. */
    private static final Set<String> TYPED_SCRIPT_VARIABLES = new HashSet<String>(
        Arrays.asList("SQLROWCOUNT", "ACTIVITY_COUNT", "SQLFOUND", "SQLNOTFOUND"));
    /**
     * The exceptions declared, one map for each running block of the body that is running, the innermost block's
     * first. A declaration belongs to its block: it hides an enclosing block's exception of the same name while
     * the block runs and ends with it, and a called procedure's body, a dynamic text's and a scripting
     * function's start with none of their caller's (live-verified).
     */
    private final Deque<Map<String, UserDefinedException>> exceptionScopes = new ArrayDeque<>();
    // Exceptions currently being handled (a stack for nested handlers) so a bare RAISE; can re-raise the one
    // it is handling rather than throwing a fresh generic error.
    private final Deque<Exception> handledExceptions = new ArrayDeque<>();
    /** What the running handlers may not read of their blocks' BEGIN sections. */
    private final HandlerLets handlerLets = new HandlerLets();
    /** The asynchronous jobs the running body started and has not awaited, in the order it started them. */
    private final List<AsyncJob> asyncJobs = new ArrayList<AsyncJob>();
    private boolean breakFlag;
    private boolean continueFlag;
    private String breakLabel;
    private String continueLabel;
    // Target label of a labeled BREAK / CONTINUE (null = unlabeled → the innermost loop). A loop consumes
    // the flag when the label matches its own; otherwise it unwinds, leaving the flag set to propagate.
    private boolean returnFlag;
    private boolean returnTableFlag;
    private Object returnValue;
    // Depth of currently-active BEGIN…END block handlers. A nested block (depth > 1) that RETURNs must
    // leave the return state set so the ENCLOSING block propagates it; only the outermost block (depth 1)
    // consumes the return into a result. Without this a RETURN inside a nested BEGIN…END was swallowed.
    private int blockDepth;
    /** Whether the statement running now is a block's own SELECT … INTO, run as text. */
    private boolean runningBlockSelectInto;
    /** The place of the CALL an assignment being re-read from its own text runs, or null. */
    private int[] assignedCallPlace;

    /**
     * The row variables of the FOR loops running now, innermost last. A record is no value: named in an EXECUTE
     * IMMEDIATE's USING, it leaves its placeholder unset (live-verified: "Bind variable ? not set." at that
     * placeholder, when the statement runs).
     */
    private final List<String> loopRecords = new ArrayList<>();
    /** How many of the block's own SQL expressions — a scalar subquery on an assignment's right — are running. */
    private int blockExpressionDepth;

    /** The block depth an EXECUTE IMMEDIATE's dynamic text started at; blocks inside it nest from here. */
    private int dynamicFrameBase;
    /** The thread that started the block now running; null when none runs. */
    private volatile Thread blockThread;
    /** The block depth the running procedure body's blocks count from; 0 for the session's own script. */
    private int procedureFrameBase;
    /** How many bodies are running as frames of their own above the text the session sent. */
    private int openBodyFrames;

    /** How many SQL functions' block bodies are running, one called from another's statement. */
    private int functionBodies;
    /** The block depth of a body whose CALL made it a transaction scope; -1 when the running body is none. */
    private int scopedBase = -1;
    // Where the statement that is currently failing stood, for the uncaught-exception message the
    // outermost block builds; -1 when no failure is in flight.
    private int failureLine = -1;
    private String failureKind = "STATEMENT_ERROR";
    private int failurePosition = -1;
    private QueryExecutor queryExecutor;
    private final BindVariableSubstitutor bindSubstitutor;
    /** The names a SELECT … INTO of no rows left NULL and nothing has assigned since, upper-cased. */
    private final Set<String> emptySelectTargets = new HashSet<>();
    private static final Row EMPTY_ROW = new Row(new ArrayList<>());

    public ProceduralExecutor() {
        this.bindSubstitutor = new BindVariableSubstitutor(scope.variables(), this);
    }

    public void setQueryExecutor(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public void executeBlock(final ProceduralBlock block) {
        enterScope();
        compoundDepth++;
        try {
            for (final Statement stmt : block.getStatements()) {
                executeStatement(stmt);
                if (breakFlag || continueFlag || returnFlag) {
                    break;
                }
            }
        } finally {
            compoundDepth--;
            exitScope();
        }
    }

    /** A compound body run by another handler — an EXCEPTION handler's statements — is entered here. */
    public void enterCompound() {
        compoundDepth++;
    }

    public void exitCompound() {
        compoundDepth--;
    }

    /**
     * Start a procedure body under its declared RETURNS type (null for a table-returning body), and
     * hand back the caller's frame to put back when the body is done.
     */
    public DeclaredReturnFrame pushDeclaredReturn(final DataType returnsType, final boolean returnsTable,
                                                  final Set<String> parameterNames) {
        final DeclaredReturnFrame outer = declaredReturn;
        declaredReturn = new DeclaredReturnFrame(returnsType, returnsTable, blockDepth, compoundDepth, parameterNames);
        returnedDeclaredType = null;
        returnedStaticType = null;
        return outer;
    }

    public void restoreDeclaredReturn(final DeclaredReturnFrame outer) {
        declaredReturn = outer;
    }

    /**
     * The declared RETURNS type the block about to run judges its direct RETURNs against: only the
     * procedure's OWN block, entered at the depth its body started from, and only for a scalar RETURNS
     * type; null for every other block.
     */
    public DataType declaredReturnOfNextBlock() {
        if (declaredReturn == null || declaredReturn.getType() == null) {
            return null;
        }
        return blockDepth == declaredReturn.getBlockDepth() && compoundDepth == declaredReturn.getCompoundDepth()
            ? declaredReturn.getType() : null;
    }

    /**
     * What every RETURN of the block about to run must return when it is a procedure's OWN block: TRUE
     * for a table, FALSE for a scalar; null for any other block, whose RETURNs no declaration governs.
     */
    public Boolean returnKindOfNextBlock() {
        final boolean ownBlock = declaredReturn != null && blockDepth == declaredReturn.getBlockDepth()
            && compoundDepth == declaredReturn.getCompoundDepth();
        return ownBlock ? Boolean.valueOf(declaredReturn.returnsTable()) : null;
    }

    /**
     * The parameter names in scope of the block about to run: its procedure's signature when it is the
     * procedure's OWN block, and none for any other block. The caller's variables are never among them.
     */
    public Set<String> ownBlockParameterNames() {
        final boolean ownBlock = declaredReturn != null && blockDepth == declaredReturn.getBlockDepth()
            && compoundDepth == declaredReturn.getCompoundDepth();
        return ownBlock ? declaredReturn.getParameterNames() : Collections.<String>emptySet();
    }

    /** The declared RETURNS type the executed RETURN converted its value to, or null when it kept its own. */
    public DataType getReturnedDeclaredType() {
        return returnedDeclaredType;
    }

    /**
     * The type the executed RETURN's result column declares (live-verified): the declared RETURNS type
     * when the value converted to it, else the static type of the expression the RETURN names (see
     * {@link #staticTypeOfReturn}) — a text or a binary one at the column's full width either way, see
     * {@link AnonymousBlockResult#columnType} — else null, which leaves the column to the nominal VARCHAR.
     */
    public DataType getReturnedResultType() {
        return AnonymousBlockResult.columnType(
            returnedDeclaredType != null ? returnedDeclaredType : returnedStaticType);
    }

    /** Push a block's CONTINUE handler while it executes, so enclosed loops can resume on a body error. */
    public void pushContinueHandler(final ContinueHandler handler) {
        continueHandlers.push(handler);
    }

    public void popContinueHandler() {
        if (!continueHandlers.isEmpty()) {
            continueHandlers.pop();
        }
    }

    /** Offer an exception to the innermost active CONTINUE handler; true if it handled it (caller continues). */
    public boolean tryContinueHandler(final Exception e) {
        // A statement's timeout or cancel ends the whole statement: no handler continues past it.
        return !(e instanceof StatementCanceledException)
            && !continueHandlers.isEmpty() && continueHandlers.peek().tryHandleAsContinue(e);
    }

    /**
     * Run one loop-iteration body. If it throws and the innermost enclosing block has a matching
     * {@code WHEN … CONTINUE THEN} handler, run the handler and swallow the error so the loop proceeds
     * to its next iteration; otherwise propagate (an EXIT handler / no match unwinds the loop as before).
     */
    private void executeLoopBody(final ProceduralBlock block) {
        executeLoopBody(block, null, null);
    }

    /**
     * One pass of a loop body, in a scope of its own.
     *
     * <p>Live's WHEN … CONTINUE resumption is per STATEMENT, not per iteration: the handler runs and
     * execution proceeds with the statement AFTER the one whose execution raised, so the REST of the
     * same iteration still runs - a raising IF is followed by the increment after it (measured: the
     * loop totals 10, not 8).
     *
     * <p>A range FOR's counter is bound INSIDE that scope, because it is the loop's own variable: an
     * outer variable of the same name keeps its value throughout the loop and after it (live-verified).
     *
     * @param block        the body
     * @param counterName  a range FOR's counter, or null for every other loop
     * @param counterValue the counter's value for this pass
     */
    private void executeLoopBody(final ProceduralBlock block, final String counterName,
                                 final Object counterValue) {
        enterScope();
        compoundDepth++;
        try {
            if (counterName != null) {
                markDeclaredInCurrentScope(counterName);
                setVariable(counterName, counterValue);
            }
            for (final Statement stmt : block.getStatements()) {
                try {
                    executeStatement(stmt);
                } catch (final RuntimeException e) {
                    if (!tryContinueHandler(e)) {
                        throw e;
                    }
                }
                if (breakFlag || continueFlag || returnFlag) {
                    break;
                }
            }
        } finally {
            compoundDepth--;
            exitScope();
        }
    }

    /**
     * Run one statement, remembering WHERE it stood if it fails: an error escaping the block names the
     * failing statement's own line and position, and the INNERMOST one wins because the record is taken
     * as the exception unwinds and a later (outer) record is ignored. A statement that completes clears
     * the record, so a handled failure does not label a later, unrelated one.
     */
    public void executeStatement(final Statement stmt) {
        final String queryIdBefore = lastQueryIdOfSession();
        final boolean unrecorded = failureLine < 0;
        try {
            dispatchStatement(stmt);
        } catch (final RuntimeException failure) {
            if (unrecorded && failureLine > 0 && stmt instanceof SqlStatement
                    && ((SqlStatement) stmt).isNestedBlock()) {
                placeInWritingText(stmt.getSourceLine(), stmt.getSourcePosition());
            }
            recordStatementFailure(stmt.getSourceLine(), stmt.getSourcePosition());
            throw failure;
        }
        clearStatementFailure();
        noteStatementQueryId(queryIdBefore);
    }

    /**
     * Move a failure recorded inside a nested block that runs from its own text to where it stands in the text that
     * wrote the block: on the block's first line its position counts from the block's own, and a later line keeps its
     * position. A cursor's query that fails in a FOR loop's nested block is placed at the FOR or the OPEN that opens
     * it, at 105 where the block itself starts at 56 (live-verified).
     *
     * @param line     the block's line in the writing text
     * @param position the block's position on that line
     */
    private void placeInWritingText(final int line, final int position) {
        if (line <= 0 || position < 0) {
            return;
        }
        if (failureLine == 1) {
            failurePosition += position;
        }
        failureLine += line - 1;
    }

    private void dispatchStatement(final Statement stmt) {
        // A block's statement, a loop's iteration among them, is where its statement's timeout or cancel ends it.
        StatementCancellation.check();
        // Each procedural statement reads its own clock (live-verified) — including each
        // iteration of a loop body, which live treats as fresh statements.
        StatementClock.advance();
        switch (stmt.getType()) {
            case DECLARE:
                executeDeclare((DeclareStatement) stmt);
                break;
            case SET:
                executeSet((SetStatement) stmt);
                break;
            case EVALUATE:
                executeLogging((FunctionCallExpression) ((EvaluateStatement) stmt).getExpression());
                break;
            case IF:
                executeIf((IfStatement) stmt);
                break;
            case CASE:
                executeCase((CaseStatement) stmt);
                break;
            case LOOP:
                executeLoop((LoopStatement) stmt);
                break;
            case WHILE:
                executeWhile((WhileStatement) stmt);
                break;
            case FOR:
                executeFor((ForStatement) stmt);
                break;
            case REPEAT:
                executeRepeat((RepeatStatement) stmt);
                break;
            case RETURN:
                executeReturn((ReturnStatement) stmt);
                break;
            case RETURN_TABLE:
                executeReturnTable((ReturnTableStatement) stmt);
                break;
            case BREAK:
                breakFlag = true;
                breakLabel = stmt.getLabel();
                break;
            case CONTINUE:
                continueFlag = true;
                continueLabel = stmt.getLabel();
                break;
            case SQL:
                executeSql((SqlStatement) stmt);
                break;
            case CALL:
                executeCall((CallStatement) stmt);
                break;
            case RAISE:
                executeRaise((RaiseStatement) stmt);
                break;
            case DECLARE_CURSOR:
                executeDeclareCursor((DeclareCursorStatement) stmt);
                break;
            case DECLARE_RESULTSET:
                executeDeclareResultSet((DeclareResultSetStatement) stmt);
                break;
            case ASYNC:
                executeAsync((AsyncStatement) stmt);
                break;
            case AWAIT:
                executeAwait((AwaitStatement) stmt);
                break;
            case DECLARE_EXCEPTION:
                executeDeclareException((DeclareExceptionStatement) stmt);
                break;
            case OPEN:
                executeOpen((OpenStatement) stmt);
                break;
            case FETCH:
                executeFetch((FetchStatement) stmt);
                break;
            case CLOSE:
                executeClose((CloseStatement) stmt);
                break;
        }
    }

    private void executeDeclare(final DeclareStatement stmt) {
        markDeclaredInCurrentScope(stmt.getVariableName());
        rememberInitialiserType(stmt.getVariableName(), stmt.getInitialiserType());
        final DataType declaredType = stmt.getDeclaredType();
        if (declaredType != null) {
            variableTypes.put(stmt.getVariableName().toUpperCase(Locale.ROOT), declaredType);
        }
        rememberConversionType(stmt.getVariableName(), stmt.getDefaultValue(), declaredType);
        try {
            setVariable(stmt.getVariableName(), heldUnder(stmt.getDefaultValue(), declaredType));
        } catch (final RuntimeException failed) {
            // A coercion fault is an EXPRESSION fault, anchored on the initialiser (live).
            if (stmt.getInitializerLine() > 0) {
                recordExpressionFailure(stmt.getInitializerLine(), stmt.getInitializerPosition());
            }
            throw failed;
        }
    }

    private void executeSet(final SetStatement stmt) {
        // A statement assigned in parentheses fills a RESULTSET where the assignment stands; assigned to anything
        // else, it is refused as the query the account evaluates it as, before any of it runs (see AssignedStatement).
        if (stmt.getExpression() instanceof StatementValueExpression) {
            final StatementValueExpression statement = (StatementValueExpression) stmt.getExpression();
            if (holdsResultSet(stmt.getVariableName())) {
                assignResultSetQuery(stmt.getVariableName(), statement.getStatementText(), statement.getSourceLine(),
                    statement.getSourcePosition());
                return;
            }
            recordExpressionFailure(statement.getSourceLine(), statement.getSourcePosition());
            throw AssignedStatement.scalarRefusal(statement.getStatementText());
        }
        // A RESULTSET-typed variable (DECLARE rs RESULTSET) assigned a parenthesized query runs the QUERY
        // where the assignment stands — rs := (SELECT …), like DECLARE … DEFAULT (…) — and holds what it
        // answered then; a failure is the assignment's, at its opening parenthesis (live-verified). The
        // plain scalar path would evaluate (SELECT …) to its first cell and clobber the RESULTSET.
        final Object current = getVariable(stmt.getVariableName());
        if ((current instanceof ResultSetVariable || current instanceof ResultSet)
                && isParenthesizedQuery(stmt.getRawExpressionText())) {
            final ResultSetVariable assigned = new ResultSetVariable(stripOuterParens(stmt.getRawExpressionText()));
            assigned.setName(stmt.getVariableName().toUpperCase(Locale.ROOT));
            assigned.setParenthesisLead(parenthesisLead(stmt.getRawExpressionText()));
            setVariable(stmt.getVariableName(), assigned);
            final BaseExpression value = stmt.getExpression();
            runInitialiser(assigned, value == null ? -1 : value.getSourceLine(),
                value == null ? -1 : value.getSourcePosition());
            return;
        }
        // A value given to a variable converts to its declared type: a LET's own, or the one the variable holds.
        Object value = evaluateExpression(stmt.getExpression(), stmt.isDeclaration() ? stmt.getDeclaredType()
            : variableTypes.get(stmt.getVariableName().toUpperCase(Locale.ROOT)));
        if (stmt.isDeclaration()) {
            // A LET DECLARES, so it binds in the scope it stands in: an IF branch, a CASE branch or a
            // loop body that writes one leaves the outer variable of that name untouched, and the name
            // itself is gone once the branch ends. A plain assignment reaches outward, as it should
            // (live-verified in both directions).
            markDeclaredInCurrentScope(stmt.getVariableName());
            // A LET inside a compound body declares the name's type — the one written, or the one its
            // initialiser implies — before the value is held to it.
            rememberInitialiserType(stmt.getVariableName(), stmt.getDeclaredType() != null ? null
                : initialiserOwnType(stmt.getExpression(), stmt.getSqlInitialiser()));
            DataType declared = stmt.getDeclaredType() != null
                ? stmt.getDeclaredType() : inferUntypedDeclarationType(stmt.getExpression());
            if (declared == null) {
                declared = typeOfSqlInitialiser(stmt.getSqlInitialiser());
            }
            if (declared != null) {
                variableTypes.put(stmt.getVariableName().toUpperCase(Locale.ROOT), declared);
            }
        }
        if (!stmt.isDeclaration()) {
            initialiserTypes.remove(stmt.getVariableName().toUpperCase(Locale.ROOT));
        }
        rememberConversionType(stmt.getVariableName(), value,
            variableTypes.get(stmt.getVariableName().toUpperCase(Locale.ROOT)));
        try {
            value = heldUnder(value, variableTypes.get(stmt.getVariableName().toUpperCase(Locale.ROOT)));
        } catch (final RuntimeException failed) {
            // A coercion fault is an EXPRESSION fault, anchored on the right side (live).
            final BaseExpression rhs = stmt.getExpression();
            if (rhs != null && rhs.getSourceLine() > 0) {
                recordExpressionFailure(rhs.getSourceLine(), rhs.getSourcePosition());
            }
            throw failed;
        }
        setVariable(stmt.getVariableName(), value);
        callAnswers.remove(stmt.getVariableName().toUpperCase(Locale.ROOT));
    }

    /**
     * Whether the raw RHS text is a parenthesized query — {@code (SELECT …)} or {@code (WITH …)}. Decided by
     * the FIRST LEXER TOKEN inside the parentheses, so leading comments ({@code res := ( -- note … SELECT}),
     * common in real bodies, don't hide the query from the RESULTSET-assignment path.
     */
    private static boolean isParenthesizedQuery(final String rawText) {
        if (rawText == null) {
            return false;
        }
        final String t = rawText.trim();
        if (!t.startsWith("(") || !t.endsWith(")")) {
            return false;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(t.substring(1, t.length() - 1)));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final int firstTokenType = tokens.LT(1).getType();
        return firstTokenType == FrostlakeLexer.SELECT || firstTokenType == FrostlakeLexer.WITH;
    }

    /**
     * How far a parenthesized query stands from its opening parenthesis: the lines between them, and the columns
     * its first line counts before the query's own — the parenthesis and the blanks after it, or on a later line
     * the blanks that open the line.
     */
    private static int[] parenthesisLead(final String rawText) {
        final String text = rawText.trim();
        int end = 1;
        while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        int lines = 0;
        int lineStart = 0;
        for (int i = 1; i < end; i++) {
            if (text.charAt(i) == '\n') {
                lines++;
                lineStart = i + 1;
            }
        }
        return new int[] {lines, end - lineStart};
    }

    /** Strip one layer of outer parentheses from a parenthesized query. */
    private static String stripOuterParens(final String rawText) {
        final String t = rawText.trim();
        return t.substring(1, t.length() - 1).trim();
    }

    private void executeIf(final IfStatement stmt) {
        for (final IfCondition condition : stmt.getConditions()) {
            final Object result = evaluateExpression(condition.getCondition());
            if (isTrue(truthValue(condition.getCondition(), result))) {
                executeBlock(condition.getBlock());
                return;
            }
        }
        if (stmt.getElseBlock() != null) {
            executeBlock(stmt.getElseBlock());
        }
    }

    private void executeCase(final CaseStatement stmt) {
        final Object switchValue = stmt.getSwitchExpression() != null
            ? evaluateExpression(stmt.getSwitchExpression())
            : null;

        for (final WhenClause when : stmt.getWhenClauses()) {
            final Object whenValue = evaluateExpression(when.getCondition());
            final boolean matches = switchValue != null
                ? Objects.equals(switchValue, whenValue)
                : isTrue(truthValue(when.getCondition(), whenValue));

            if (matches) {
                executeBlock(when.getBlock());
                return;
            }
        }

        if (stmt.getElseBlock() != null) {
            executeBlock(stmt.getElseBlock());
        }
    }

    /**
     * Post-body loop control for a loop carrying the given label. Returns true if the loop should proceed
     * to its next iteration — the body completed normally, or a CONTINUE targeting THIS loop was consumed
     * here. Returns false if it should stop: a BREAK or RETURN targeting this loop (the break is consumed
     * here), or a BREAK / CONTINUE targeting an OUTER loop, which is left set so the enclosing loop
     * handles it — labeled break/continue propagate outward through the nested loops.
     */
    private boolean loopProceeds(final String loopLabel) {
        if (continueFlag) {
            if (labelMatches(continueLabel, loopLabel)) {
                continueFlag = false;
                continueLabel = null;
                return true;   // CONTINUE this loop → next iteration
            }
            return false;      // CONTINUE targets an outer loop → unwind, keep the flag set
        }
        if (breakFlag) {
            if (labelMatches(breakLabel, loopLabel)) {
                breakFlag = false;
                breakLabel = null;   // consumed by this loop
            }
            return false;            // stop (either consumed here or propagating outward)
        }
        return !returnFlag;          // RETURN → stop; otherwise the body completed → next iteration
    }

    /** An unlabeled BREAK/CONTINUE (null target) matches the innermost loop; otherwise it names a loop. */
    private static boolean labelMatches(final String targetLabel, final String loopLabel) {
        return targetLabel == null || targetLabel.equalsIgnoreCase(loopLabel);
    }

    private void executeLoop(final LoopStatement stmt) {
        while (true) {
            executeLoopBody(stmt.getBlock());
            if (!loopProceeds(stmt.getLabel())) {
                return;
            }
        }
    }

    private void executeWhile(final WhileStatement stmt) {
        while (isTrue(truthValue(stmt.getCondition(), evaluateExpression(stmt.getCondition())))) {
            executeLoopBody(stmt.getBlock());
            if (!loopProceeds(stmt.getLabel())) {
                return;
            }
        }
    }

    private void executeFor(final ForStatement stmt) {
        if (stmt.isRange()) {
            executeForRange(stmt);
            return;
        }
        // Check if the iterable resolves to a cursor name
        final String iterableName = getIterableName(stmt.getIterable());
        final Cursor cursor = iterableName != null ? readCursor(iterableName) : null;

        if (cursor != null) {
            // FOR rec IN cursorName DO — implicit open and iterate; a cursor an earlier FOR ran to its end stays open
            // past its last row, so the loop finds none (live-verified).
            if (!cursor.isOpen()) {
                openCursorWithQuery(cursor);
            }
            final String varName = stmt.getVariableName();
            loopRecords.add(varName.toUpperCase(Locale.ROOT));
            try {
                Row row;
                while ((row = cursor.fetch()) != null) {
                    // Expose row fields as varName.COLUMN_NAME variables
                    setRowVariables(varName, row, cursor.getResultSet());
                    executeLoopBody(stmt.getBlock());
                    if (!loopProceeds(stmt.getLabel())) {
                        break;
                    }
                }
            } finally {
                loopRecords.remove(loopRecords.size() - 1);
                if (cursor.isOpen()) {
                    cursor.endLoop();
                }
                clearRowVariables(varName, cursor.getResultSet());
            }
            return;
        }

        // FOR rec IN <resultset> DO — iterate a RESULTSET variable / ResultSet, binding rec.COLUMN.
        final Object iterableValue = evaluateExpression(stmt.getIterable());
        if (iterableValue instanceof ResultSetVariable) {
            try {
                requireInitialized((ResultSetVariable) iterableValue);
            } catch (final RuntimeException uninitialized) {
                // A loop over a RESULTSET that holds nothing is the one refusal of it placed nowhere (live-verified).
                recordStatementFailureNowhere();
                throw uninitialized;
            }
        }
        final ResultSet rsIterable = resolveToResultSet(iterableValue);
        if (rsIterable != null) {
            final String varName = stmt.getVariableName();
            loopRecords.add(varName.toUpperCase(Locale.ROOT));
            try {
                for (final Row row : rsIterable.getRows()) {
                    setRowVariables(varName, row, rsIterable);
                    executeLoopBody(stmt.getBlock());
                    if (!loopProceeds(stmt.getLabel())) {
                        break;
                    }
                }
            } finally {
                loopRecords.remove(loopRecords.size() - 1);
                clearRowVariables(varName, rsIterable);
            }
            return;
        }

        // Fall back to list-based iteration (e.g. FOR x IN <array> DO).
        if (iterableValue instanceof List) {
            final List<Object> values = (List<Object>) iterableValue;
            for (final Object value : values) {
                setVariable(stmt.getVariableName(), value);
                executeLoopBody(stmt.getBlock());
                if (!loopProceeds(stmt.getLabel())) {
                    break;
                }
            }
            return;
        }

        // An ARRAY value in this engine is canonical JSON text — FOR x IN [a, b] DO evaluates its
        // iterable to "[false,true]", which matched no branch above and silently ran ZERO iterations
        // (a loader's dual-pass FOR over [False, True] never executed either pass). Iterate the
        // array's elements, binding the loop variable to each element's Java value.
        final ArrayNode arrayIterable = ArrayFunctionHelper.parseArray(iterableValue);
        if (arrayIterable != null) {
            // The variable of a loop over a value is a VARIANT, whatever its element holds (live-verified).
            declareVariableType(stmt.getVariableName(), new VariantType());
            for (final JsonNode element : arrayIterable) {
                setVariable(stmt.getVariableName(), ArrayFunctionHelper.fromNode(element));
                executeLoopBody(stmt.getBlock());
                if (!loopProceeds(stmt.getLabel())) {
                    break;
                }
            }
            return;
        }
        final JsonNode held = iterableValue == null ? null : ArrayFunctionHelper.parseNode(iterableValue);
        if (iterableValue != null && (held == null || !held.isObject())) {
            // A value that is neither an ARRAY nor an OBJECT is refused at the FOR (live-verified).
            throw new RuntimeException(ForStatement.NOT_ITERABLE);
        }
        if (held != null) {
            // An OBJECT is looped over once per key, the variable a VARIANT holding NULL each time (live-verified).
            declareVariableType(stmt.getVariableName(), new VariantType());
            for (int key = 0; key < held.size(); key++) {
                setVariable(stmt.getVariableName(), null);
                executeLoopBody(stmt.getBlock());
                if (!loopProceeds(stmt.getLabel())) {
                    break;
                }
            }
        }
    }

    /** FOR i IN [REVERSE] start TO end DO … — iterate the inclusive integer range, the counter bound to i. */
    /**
     * FOR counter IN [REVERSE] low TO high. The counter is a NUMBER(9,0) (live-verified: a column
     * derived from {@code :i} declares NUMBER(9,0), {@code :i + 1} NUMBER(10,0)), so the FIRST value
     * it takes — the low bound, or the high one under REVERSE — must fit it and is refused at that
     * bound's own position when it does not; the values the loop then counts through are never
     * re-checked (999999999 TO 1000000000 runs both iterations on the account).
     */
    private void executeForRange(final ForStatement stmt) {
        final long start = toLong(evaluateExpression(stmt.getIterable()));
        final long end = toLong(evaluateExpression(stmt.getToExpression()));
        final String varName = stmt.getVariableName();
        final long from = stmt.isReverse() ? end : start;
        final long to = stmt.isReverse() ? start : end;
        final long step = stmt.isReverse() ? -1 : 1;
        refuseCounterOutOfRange(from, stmt.isReverse() ? stmt.getToExpression() : stmt.getIterable());
        final String key = varName.toUpperCase(Locale.ROOT);
        final DataType shadowed = counterTypes.put(key, COUNTER_TYPE);
        try {
            for (long v = from; stmt.isReverse() ? v >= to : v <= to; v += step) {
                executeLoopBody(stmt.getBlock(), varName, Long.valueOf(v));
                if (!loopProceeds(stmt.getLabel())) {
                    break;
                }
            }
        } finally {
            if (shadowed == null) {
                counterTypes.remove(key);
            } else {
                counterTypes.put(key, shadowed);
            }
        }
    }

    private void refuseCounterOutOfRange(final long first, final BaseExpression bound) {
        if (first > COUNTER_LIMIT || first < -COUNTER_LIMIT) {
            if (bound != null && bound.getSourceLine() > 0) {
                recordExpressionFailure(bound.getSourceLine(), bound.getSourcePosition());
            }
            throw new RuntimeException(NumericRangeRefusal.typed(
                SignedStorageWidth.tagOfPrecision(9), 9, 0, false, BigDecimal.valueOf(first)));
        }
    }

    /**
     * Refuse a CALL argument too wide for its NUMBER parameter in the words the account has for an argument
     * (live-verified): an exact number printed at the parameter's scale, or at its own when that is finer, under the
     * storage class that printed value needs — {@code CALL p(123456789)} into a NUMBER(5,2) is "Number out of
     * representable range: type FIXED[SB8](5,2){nullable}, value 123456789.00"; a FLOAT by its own text under the
     * parameter's class and {@code not null}; a text as written, "Numeric value '1000' is out of range".
     *
     * @param value    the argument as evaluated
     * @param type     the parameter's declared type
     * @param nullable whether the refusal words the target nullable
     */
    private static void refuseArgumentPastWidth(final Object value, final NumericType type, final boolean nullable) {
        if (type.getPrecision() <= 0 || NumericType.isApproximate(type) || NumericType.isDecfloat(type)) {
            return;
        }
        final BigDecimal parsed;
        try {
            parsed = value instanceof BigDecimal ? (BigDecimal) value
                : value instanceof Double || value instanceof Float ? BigDecimal.valueOf(((Number) value).doubleValue())
                : value instanceof Long || value instanceof Integer ? BigDecimal.valueOf(((Number) value).longValue())
                : value instanceof String ? new BigDecimal(((String) value).trim()) : null;
        } catch (final NumberFormatException unread) {
            return;
        }
        if (parsed == null || !NumericRangeRefusal.exceeds(parsed, type.getPrecision(), type.getScale())) {
            return;
        }
        if (value instanceof String) {
            throw new RuntimeException(NumericRangeRefusal.unconvertibleText(value));
        }
        if (value instanceof Double || value instanceof Float) {
            throw new RuntimeException(NumericRangeRefusal.typedText(
                SignedStorageWidth.tagOfPrecision(type.getPrecision()), type.getPrecision(), type.getScale(), false,
                SharedFunctionHelpers.textOf(value)));
        }
        final BigDecimal shown = parsed.scale() < type.getScale() ? parsed.setScale(type.getScale()) : parsed;
        throw new RuntimeException(NumericRangeRefusal.typedText(SignedStorageWidth.tagOf(shown, shown.scale()),
            type.getPrecision(), type.getScale(), nullable, shown.toPlainString()));
    }

    /** REPEAT … UNTIL &lt;cond&gt; — a post-test loop: run the body, then stop once the condition is true. */
    private void executeRepeat(final RepeatStatement stmt) {
        while (true) {
            executeLoopBody(stmt.getBlock());
            if (!loopProceeds(stmt.getLabel())) {
                return;
            }
            if (isTrue(truthValue(stmt.getCondition(), evaluateExpression(stmt.getCondition())))) {
                return;   // UNTIL condition satisfied
            }
        }
    }

    private long toLong(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            throw new RuntimeException("FOR loop range bound evaluated to NULL");
        }
        return Long.parseLong(value.toString().trim());
    }

    /** Extract the plain identifier name from an expression if it's a simple variable reference. */
    private String getIterableName(final BaseExpression expr) {
        if (expr == null) return null;
        if (expr instanceof VariableExpression) return ((VariableExpression) expr).getName();
        if (expr instanceof LiteralExpression) {
            final Object v = ((LiteralExpression) expr).getValue();
            return v instanceof String ? (String) v : null;
        }
        return null;
    }

    /** Open a cursor by executing its SELECT query via queryExecutor. */
    private void openCursorWithQuery(final Cursor cursor) {
        openCursorWithQuery(cursor, List.of());
    }

    private void openCursorWithQuery(final Cursor cursor, final List<Object> positionalBinds) {
        if (cursor.getResultSetVariableName() != null) {
            // CURSOR FOR <resultset_var>: iterate the RESULTSET's rows (its query runs when the RESULTSET
            // is created / on first resolve) rather than executing a SELECT. OPEN … USING has no effect.
            openCursorOverResultSet(cursor);
            return;
        }
        if (queryExecutor == null) throw new RuntimeException("QueryExecutor not available to open cursor");
        String sql = cursorQuery(cursor, positionalBinds.size());
        final String declared = sql;
        // OPEN … USING (…) binds the values positionally to the cursor query's `?` placeholders.
        if (!positionalBinds.isEmpty()) {
            sql = JdbcMarshaling.bindUsing(sql, positionalBinds, new HashSet<Integer>());
        }
        final List<ResultSet> result;
        try {
            result = issue(sql);
        } catch (final RuntimeException refused) {
            // A bound value echoed as a predicate is named by its placeholder (see UsingBindEcho).
            throw positionalBinds.isEmpty() ? refused
                : UsingBindEcho.named(refused, declared, positionalBinds, new HashSet<Integer>());
        }
        final ResultSet rs = (result != null && !result.isEmpty()) ? result.get(0) : new ResultSet(new ArrayList<>());
        cursor.open(rs);
    }

    /**
     * The query a cursor opens over. It binds none of the block's variables: a {@code :name} in it — a variable's, a
     * procedure's parameter's or nothing's — is a bind nothing sets, refused as the cursor opens at its place in the
     * query (live-verified). Only {@code ?} and {@code :n} are bound, by OPEN … USING, and a number its list does
     * not reach is unset too.
     *
     * @param cursor      the cursor
     * @param usingValues how many values the OPEN's USING list gives
     * @return its query as declared
     */
    public String cursorQuery(final Cursor cursor, final int usingValues) {
        final String sql = cursor.getSelectQuery();
        DynamicStatementBinds.requireSet(sql, usingValues, new HashSet<Integer>());
        return sql;
    }

    /** Open a {@code CURSOR FOR <resultset_var>} over the RESULTSET's rows (empty if it does not resolve). */
    public void openCursorOverResultSet(final Cursor cursor) {
        final ResultSet rsv = resolveToResultSet(getVariable(cursor.getResultSetVariableName()));
        cursor.open(rsv != null ? rsv : new ResultSet(new ArrayList<>()));
    }

    /** Set rec.COL variables from a cursor row. */
    private void setRowVariables(final String varName, final Row row,
                                  final ResultSet rs) {
        if (rs == null) return;
        // A DML statement's count grid types its own columns, though no projection typed them: a field of a
        // RESULTSET an INSERT filled is NUMBER(19,0) (live-verified).
        final boolean countGrid = rs.getUpdateCount() != null;
        for (int i = 0; i < rs.getColumns().size(); i++) {
            final String colName = rs.getColumns().get(i).getName();
            final DataType fieldType = rs.getColumns().get(i).getStaticType() != null
                ? rs.getColumns().get(i).getStaticType() : countGrid ? rs.getColumns().get(i).getDataType() : null;
            if (fieldType != null) {
                recordFieldTypes.put((varName + "." + colName).toUpperCase(Locale.ROOT), fieldType);
            }
            // A field of a semi-structured column holds its value as a variable of that type does.
            final Object val = jsonHeld(i < row.getValues().size() ? row.getValue(i) : null,
                fieldType != null ? fieldType : rs.getColumns().get(i).getDataType());
            scope.variables().put((varName + "." + colName).toUpperCase(Locale.ROOT), val);
            scope.variables().put((varName + "." + colName), val);
        }
    }

    /** Remove rec.COL variables after the FOR loop. */
    private void clearRowVariables(final String varName, final ResultSet rs) {
        if (rs == null) return;
        for (final ResultSetColumn col : rs.getColumns()) {
            recordFieldTypes.remove((varName + "." + col.getName()).toUpperCase(Locale.ROOT));
            scope.variables().remove((varName + "." + col.getName()).toUpperCase(Locale.ROOT));
            scope.variables().remove((varName + "." + col.getName()));
        }
    }

    private void executeReturn(final ReturnStatement stmt) {
        final BaseExpression expression = stmt.getExpression();
        returnedDeclaredType = null;
        returnedStaticType = null;
        if (expression != null) {
            final Object answer = returnedCallAnswer(expression);
            if (answer != null && !takesDeclaredReturnType(expression, answer)) {
                // A bare RETURN of a CALL's answer held unconverted hands it back as the CALL did: DECLARE x DATE …
                // x := (CALL pv('abc')); RETURN x answers abc (live-verified).
                returnValue = answer;
                returnedStaticType = literalValueType(answer);
                returnFlag = true;
                return;
            }
            final Object value = answer != null ? answer : evaluateExpression(expression);
            if (returnsAsText(expression)) {
                returnValue = asReturnedText(value);
            } else if (takesDeclaredReturnType(expression, value)) {
                returnValue = convertedReturn(value, expression);
                returnedDeclaredType = declaredReturn.getType();
            } else {
                returnValue = value;
                returnedStaticType = staticTypeOfReturn(stmt, value);
                if (returnsInitialiserType(expression) && returnedStaticType instanceof NumericType
                        && value instanceof Number) {
                    // The value is read at the type the RETURN reports: a FLOAT name initialised from a
                    // NUMBER(10,4) returns 1.5000 (live-verified).
                    returnValue = coerceToType(value, returnedStaticType);
                }
                returnValue = atReportedScale(returnValue, returnedStaticType);
                // A semi-structured value is handed back as a variable of its type holds it: its JSON, read back.
                returnValue = jsonHeld(returnValue, returnedStaticType);
            }
        }
        returnFlag = true;
    }

    /**
     * The static type of a RETURN's value that keeps its own type, which its result column declares
     * (live-verified): a literal's own ({@code 1.7777} is NUMBER(5,4)), a name's declared one (a FOR
     * counter bound as {@code :i} is NUMBER(9,0)), and any other expression's as SQL types it over the
     * names and FOR counters in scope — {@code (SELECT 1.7777)} is NUMBER(5,4), {@code i + 1} over a
     * counter NUMBER(10,0), {@code x + 1} over a NUMBER(5,2) NUMBER(6,2), {@code CURRENT_DATE()} a DATE
     * and {@code OBJECT_CONSTRUCT('a', 1)} an OBJECT, whatever RETURNS type a procedure declares. Null
     * when the type cannot be determined.
     */
    private DataType staticTypeOfReturn(final ReturnStatement stmt, final Object value) {
        final BaseExpression expression = stmt.getExpression();
        if (expression instanceof LiteralExpression || isNegatedLiteral(expression)) {
            return literalValueType(value);
        }
        if (expression instanceof VariableExpression) {
            final VariableExpression variable = (VariableExpression) expression;
            if (variable.getName() == null) {
                return null;
            }
            if (!variable.isBindForm() && emptySelectTargets.contains(variable.getName().toUpperCase(Locale.ROOT))) {
                // What a SELECT … INTO of no rows left in its target is a NULL of no type, whatever the
                // target declares: DECLARE r INT … SELECT id INTO :r FROM t WHERE FALSE; RETURN r declares
                // VARCHAR(134217728), until the name is assigned again; bound as :r it is its declared type
                // (live-verified).
                return UNCOUNTED_RETURN;
            }
            if (!variable.isBindForm() && "SQLROWCOUNT".equalsIgnoreCase(variable.getName())) {
                // Before any statement has run — none yet, or only one on a branch not taken — the count is a
                // text: BEGIN RETURN SQLROWCOUNT; END; declares VARCHAR(134217728) (live-verified).
                return getVariable("ACTIVITY_COUNT") != null ? ROW_COUNT_RETURN : UNCOUNTED_RETURN;
            }
            // A bare name an untyped declaration made reports its initialiser's own type; bound as :x, the
            // name is its declared type.
            final DataType initialised = variable.isBindForm() ? null
                : initialiserTypes.get(variable.getName().toUpperCase(Locale.ROOT));
            if (initialised != null) {
                return initialised;
            }
            // A cursor record's field is its query column's type.
            final DataType field = recordFieldTypes.get(variable.getName().toUpperCase(Locale.ROOT));
            if (field != null) {
                return field;
            }
            // A bare name whose value was converted reports the CONVERSION's type, not the
            // declaration's: over x NUMBER(10,2) DEFAULT TRUE the account returns 1 as NUMBER(2,0),
            // never 1.00. Bound as :x the name is its declared type, as it is everywhere else.
            final DataType converted = variable.isBindForm() ? null
                : conversionTypes.get(variable.getName().toUpperCase(Locale.ROOT));
            return converted != null ? converted : getDeclaredVariableType(variable.getName());
        }
        return typeOfReturnedExpression(stmt.getSqlExpression());
    }

    /** Whether a RETURN names, bare, a variable whose initialiser's own type it reports. */
    private boolean returnsInitialiserType(final BaseExpression expression) {
        return expression instanceof VariableExpression && !((VariableExpression) expression).isBindForm()
            && ((VariableExpression) expression).getName() != null
            && initialiserTypes.containsKey(((VariableExpression) expression).getName().toUpperCase(Locale.ROOT));
    }

    /**
     * An exact number at the scale its result column reports, padded and never rounded: SQLROWCOUNT + 1 is
     * NUMBER(19,5) and returns 2.00000 (live-verified). Any other value is handed back as it is.
     */
    private static Object atReportedScale(final Object value, final DataType reported) {
        if (!(reported instanceof NumericType) || NumericType.isApproximate(reported)
                || !(value instanceof Long || value instanceof Integer || value instanceof BigDecimal)) {
            return value;
        }
        final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.valueOf(((Number) value).longValue());
        return exact.scale() < ((NumericType) reported).getScale()
            ? exact.setScale(((NumericType) reported).getScale()) : value;
    }

    /** The static type a literal value carries, or null for one this does not type. */
    /**
     * Note the type a name's conversion produced, when that is not the declared type.
     *
     * <p>Read from the value BEFORE it is converted, since the conversion is what the source type
     * decides. A name whose conversion kept the declared type is removed rather than recorded, so the
     * map holds only the names where the two differ.</p>
     */
    private void rememberConversionType(final String name, final Object sourceValue,
                                        final DataType declaredType) {
        final String key = name.toUpperCase(Locale.ROOT);
        if (declaredType == null) {
            conversionTypes.remove(key);
            return;
        }
        final DataType converted = ConvertedValueType.of(literalValueType(sourceValue), declaredType);
        if (converted == declaredType) {
            conversionTypes.remove(key);
        } else {
            conversionTypes.put(key, converted);
        }
    }

    private static DataType literalValueType(final Object value) {
        if (value instanceof BigDecimal || value instanceof Long || value instanceof Integer) {
            return NumericLiteralTypes.of(value);
        }
        if (value instanceof Double) {
            return NumericType.FLOAT;
        }
        if (value instanceof Boolean) {
            return BooleanType.BOOLEAN;
        }
        return value instanceof String ? StringType.VARCHAR : null;
    }

    /**
     * The own static type of an initialiser: a literal's ({@code 1} is NUMBER(1,0), {@code 1.50} NUMBER(2,1)),
     * a name's declared one (a FOR counter's NUMBER(9,0)), and any other expression's as SQL types it over the
     * names in scope ({@code 1 + 1} is NUMBER(2,0)); null when undetermined.
     */
    public DataType initialiserOwnType(final BaseExpression initialiser, final Expression sqlInitialiser) {
        if (initialiser instanceof LiteralExpression) {
            return literalValueType(((LiteralExpression) initialiser).getValue());
        }
        if (isNegatedLiteral(initialiser)) {
            return literalValueType(((LiteralExpression) ((UnaryExpression) initialiser).getOperand()).getValue());
        }
        if (initialiser instanceof VariableExpression) {
            return getDeclaredVariableType(((VariableExpression) initialiser).getName());
        }
        return typeOfReturnedExpression(sqlInitialiser);
    }

    /** Hold an initialiser's own type for the name just declared, or forget one a typed declaration replaced. */
    private void rememberInitialiserType(final String name, final DataType initialised) {
        if (name == null) {
            return;
        }
        if (initialised == null) {
            initialiserTypes.remove(name.toUpperCase(Locale.ROOT));
        } else {
            initialiserTypes.put(name.toUpperCase(Locale.ROOT), initialised);
        }
    }

    /**
     * Judge the argument types of a block expression evaluated outside a built statement — a typed LET's or a
     * typed DECLARE's value — and its conversion to the declared type, refusing it as that expression's
     * EXPRESSION_ERROR (see {@link BlockExpressionTypes}).
     *
     * @param expression the expression as parsed in the block
     * @param target     the declared type the value converts to, or null
     */
    public void judgeBlockExpression(final ParserRuleContext expression, final DataType target) {
        if (expression == null || queryExecutor == null) {
            return;
        }
        try {
            judge(expression, target);
        } catch (final BlockExpressionTypeError refused) {
            recordExpressionFailure(expression.getStart().getLine(), expression.getStart().getCharPositionInLine());
            throw refused;
        }
    }

    /** Judge a block expression unless it passed already over the same types (see {@link #judgedPasses}). */
    private void judge(final ParserRuleContext expression, final DataType target) {
        final Map<String, DataType> types = judgedTypes();
        final Map<String, DataType> passed = judgedPasses.get(expression);
        if (passed != null && passed.equals(types) && Objects.equals(judgedPassTargets.get(expression), target)) {
            return;
        }
        BlockExpressionTypes.judge(expression, types, counterTypes.keySet(), bindsBlockNames(), target,
            scope.variables(), queryExecutor);
        judgedPasses.put(expression, types);
        judgedPassTargets.put(expression, target);
    }

    /**
     * Whether the account writes every name of the running block out as a bind: in a stored procedure's body, and
     * in a block whose outermost DECLARE section declares a cursor or a RESULTSET with a query (see {@link
     * BlockExpressionTypes}).
     */
    private boolean bindsBlockNames() {
        return declaredReturn != null || namesBoundByDeclarations;
    }

    /**
     * Mark whether the outermost block now starting binds its names by what its DECLARE section declares (see
     * {@link #bindsBlockNames}).
     *
     * @param bound whether it declares a cursor or a RESULTSET with a query
     * @return the mark it displaced, for the block's end to put back
     */
    public boolean bindNamesByDeclarations(final boolean bound) {
        final boolean displaced = namesBoundByDeclarations;
        namesBoundByDeclarations = bound;
        return displaced;
    }

    /**
     * The types of the names a block expression is judged over: each declared type, except that a text variable
     * is typed as the account binds its value — at the value's width, one at the least, and a NULL at the full
     * scripting width whatever the declared one: {@code LET x := 'abc'} reads as a VARCHAR(3), {@code ''} as a
     * VARCHAR(1) and a NULL as a VARCHAR(134217728) (live-verified).
     */
    private Map<String, DataType> judgedTypes() {
        final Map<String, DataType> types = namesInScope();
        for (final Map.Entry<String, DataType> entry : types.entrySet()) {
            if (entry.getValue() instanceof StringType && !"SQLROWCOUNT".equals(entry.getKey())) {
                final Object value = getVariable(entry.getKey());
                if (value == null) {
                    entry.setValue(new StringType("VARCHAR", StringResultWidths.UNBOUNDED));
                } else if (value instanceof String) {
                    final String text = (String) value;
                    entry.setValue(new StringType("VARCHAR", Math.max(1, text.codePointCount(0, text.length()))));
                }
            }
        }
        return types;
    }

    /** The NULL of a declared date, time or timestamp variable's type, as SQL writes it; null for any other value. */
    private String temporalNullOf(final BaseExpression argument) {
        if (!(argument instanceof VariableExpression)) {
            return null;
        }
        final DataType declared = variableTypes.get(((VariableExpression) argument).getName().toUpperCase(Locale.ROOT));
        return declared instanceof DateTimeType ? "NULL::" + SqlTypeNames.canonical(declared) : null;
    }

    /**
     * The NULL of a declared text, BOOLEAN, date, time or timestamp variable's type, as SQL writes it; null for any
     * other value.
     */
    /**
     * A NUMBER, FLOAT or BOOLEAN variable's value as a block binds it into a call: the conversion of its text to the
     * declared type — {@code CAST('1.50' AS NUMBER(10,2))}, {@code CAST('5' AS NUMBER(38,0))},
     * {@code CAST('true' AS BOOLEAN)} — so the call reads the declared type, TO_VARCHAR of a NUMBER(10,2) holding
     * 1.50 printing 1.50, and a refusal echoes the conversion (live-verified).
     *
     * @param argument the argument as written
     * @param value    its value
     * @return the conversion, or null for an argument that is no such variable or holds no value
     */
    private String boundConversion(final BaseExpression argument, final Object value) {
        if (!(argument instanceof VariableExpression) || value == null) {
            return null;
        }
        final DataType declared = variableTypes.get(((VariableExpression) argument).getName().toUpperCase(Locale.ROOT));
        if (!(declared instanceof NumericType) && !(declared instanceof BooleanType)) {
            return null;
        }
        final Object held = coerceToType(value, declared);
        if (held instanceof Double && (((Double) held).isNaN() || ((Double) held).isInfinite())) {
            return null;
        }
        final String text = held instanceof BigDecimal ? ((BigDecimal) held).toPlainString()
            : String.valueOf(held).toLowerCase(Locale.ROOT);
        return "CAST(" + SqlStringLiterals.encode(text) + " AS " + SqlTypeNames.canonical(declared) + ")";
    }

    /**
     * A refusal of a rebuilt call naming each NUMBER variable it bound as the conversion the block wrote: the plan
     * prints a text cast to a NUMBER through TO_NUMBER, where the account's echo keeps a bound variable's CAST —
     * {@code invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'} (live-verified).
     *
     * @param refused  the rebuilt call's refusal
     * @param funcExpr the call as the block wrote it
     * @return the refusal as the account words it
     */
    private RuntimeException namingBoundCasts(final RuntimeException refused, final FunctionCallExpression funcExpr) {
        // A unit's place keeps the plan's TO_NUMBER spelling (live-verified: DATEADD(day, …) over a NUMBER variable
        // is "Date/time component [TO_NUMBER('5') ]…").
        if (refused.getClass() != RuntimeException.class || refused.getMessage() == null
                || refused.getMessage().contains("Date/time component [")) {
            return refused;
        }
        String message = refused.getMessage();
        // A variable inside an argument's arithmetic is bound as one standing alone is (see boundArithmetic).
        final List<BaseExpression> bound = new ArrayList<>();
        for (final BaseExpression argument : funcExpr.getArguments()) {
            collectBoundOperands(argument, bound);
        }
        for (final BaseExpression argument : bound) {
            if (!(argument instanceof VariableExpression)
                    || !hasVariable(((VariableExpression) argument).getName())) {
                continue;
            }
            final DataType declared = variableTypes.get(
                ((VariableExpression) argument).getName().toUpperCase(Locale.ROOT));
            if (!(declared instanceof NumericType) || NumericType.isApproximate(declared)) {
                continue;
            }
            final String cast = boundConversion(argument, evaluateExpression(argument));
            if (cast == null) {
                continue;
            }
            final NumericType number = (NumericType) declared;
            final String text = cast.substring("CAST(".length(), cast.lastIndexOf(" AS "));
            final String printed = number.getPrecision() == NumericType.NUMBER.getPrecision() && number.getScale() == 0
                ? "TO_NUMBER(" + text + ")"
                : "TO_NUMBER(" + text + ", " + number.getPrecision() + ", " + number.getScale() + ")";
            message = message.replace(printed, cast);
        }
        return message.equals(refused.getMessage()) ? refused : new RuntimeException(message, refused.getCause());
    }

    /** An argument and the operands of its arithmetic and negations, in the order written. */
    private static void collectBoundOperands(final BaseExpression argument, final List<BaseExpression> out) {
        out.add(argument);
        if (argument instanceof BinaryExpression) {
            collectBoundOperands(((BinaryExpression) argument).getLeft(), out);
            collectBoundOperands(((BinaryExpression) argument).getRight(), out);
        } else if (argument instanceof UnaryExpression) {
            collectBoundOperands(((UnaryExpression) argument).getOperand(), out);
        }
    }

    private String declaredNullOf(final BaseExpression argument) {
        if (!(argument instanceof VariableExpression)) {
            return null;
        }
        final DataType declared = variableTypes.get(((VariableExpression) argument).getName().toUpperCase(Locale.ROOT));
        return declared instanceof DateTimeType || declared instanceof BooleanType
            || declared instanceof StringType && !(declared instanceof UuidType)
            ? "NULL::" + SqlTypeNames.canonical(declared) : null;
    }

    /** The declared type of every name a block's expression may read: FOR counters, record fields, variables. */
    private Map<String, DataType> namesInScope() {
        final Map<String, DataType> typesInScope = new HashMap<String, DataType>(counterTypes);
        typesInScope.putAll(recordFieldTypes);
        // SQLROWCOUNT is a text to the typer, so arithmetic reads it as a NUMBER(18,5): SQLROWCOUNT + 1 is
        // NUMBER(19,5) (live-verified).
        typesInScope.put("SQLROWCOUNT", StringType.VARCHAR);
        // SQLID holds a query ID, a text.
        typesInScope.put(SQLID, StringType.VARCHAR);
        typesInScope.putAll(variableTypes);
        return typesInScope;
    }

    /** A RETURN's value typed as SQL over the declared names and FOR counters in scope, or null when undetermined. */
    private DataType typeOfReturnedExpression(final Expression sqlExpression) {
        if (sqlExpression == null) {
            return null;
        }
        try {
            return DeclarationTypes.scriptStaticType(sqlExpression, namesInScope(), queryExecutor);
        } catch (final RuntimeException undetermined) {
            // The value is already computed: a type the static channel cannot settle leaves the column
            // to its nominal VARCHAR rather than failing the RETURN.
            return null;
        }
    }

    /**
     * Whether this RETURN's value is converted to the procedure's declared RETURNS type. Live converts
     * a RETURN that is a DIRECT statement of the procedure's own block — first, last or in between —
     * whose value is a literal, a typed name (a DECLAREd or LET variable, a parameter, a bind) or a
     * cast: {@code RETURN 1.7777} under NUMBER(5,2) is 1.78, {@code RETURN 'abcdef'} under VARCHAR(2)
     * is refused as too long, {@code RETURN x} under DATE reads the text. Everything else keeps the
     * expression's own type — arithmetic, a function call, a concatenation, a subquery, a conditional,
     * an untyped name — and so does ANY return that sits inside an IF, a CASE, a loop, a handler or a
     * nested block, literal or not (all live-verified). A table result is never converted.
     */
    private boolean takesDeclaredReturnType(final BaseExpression expression, final Object value) {
        if (declaredReturn == null || declaredReturn.getType() == null || value instanceof ResultSet
                || value instanceof ResultSetVariable || value instanceof Cursor) {
            return false;
        }
        if (blockDepth != declaredReturn.getBlockDepth() + 1
                || compoundDepth != declaredReturn.getCompoundDepth()) {
            return false;
        }
        if (expression instanceof LiteralExpression || isNegatedLiteral(expression)) {
            return true;
        }
        if (expression instanceof VariableExpression) {
            final String name = ((VariableExpression) expression).getName();
            return name != null && variableTypes.containsKey(name.toUpperCase(Locale.ROOT));
        }
        return expression instanceof SqlScalarExpression
            && ((SqlScalarExpression) expression).getExpression() instanceof CastExpression;
    }

    /** Whether a value already is an ARRAY: a VARIANT holding one. */
    private static boolean isArrayValue(final Object value) {
        return value instanceof VariantValue && ((VariantValue) value).node() != null
            && ((VariantValue) value).node().isArray();
    }

    /** The value as the one element of an ARRAY, a text kept as a text. */
    private static Object singleElementArray(final Object value) {
        final ArrayNode wrapped = ArrayFunctionHelper.MAPPER.createArrayNode();
        wrapped.add(value instanceof String
            ? ArrayFunctionHelper.MAPPER.getNodeFactory().textNode((String) value)
            : ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
        return VariantValue.ofNode(wrapped);
    }

    /** A scalar held as a VARIANT, as TO_VARIANT holds it. */
    private Object toVariant(final Object value) {
        if (value instanceof VariantValue || queryExecutor == null) {
            return value;
        }
        final List<Object> args = new ArrayList<Object>();
        args.add(value);
        return queryExecutor.getFunctionRegistry().getFunction("TO_VARIANT").evaluate(args);
    }

    private static boolean isNegatedLiteral(final BaseExpression expression) {
        return expression instanceof UnaryExpression
            && ((UnaryExpression) expression).getOperator() == UnaryOperator.NEGATE
            && ((UnaryExpression) expression).getOperand() instanceof LiteralExpression;
    }

    /**
     * The value under the declared RETURNS type: the assignment conversion, with two readings of
     * live's own — a decimal LITERAL converts as a FLOAT ({@code RETURN 10.50} under a VARCHAR is
     * '10.5', where a NUMBER(3,2) variable holding 1.50 is '1.50'), and a text under VARIANT becomes a
     * JSON string. A refusal is an EXPRESSION fault at the RETURN value's own position.
     */
    private Object convertedReturn(final Object value, final BaseExpression expression) {
        try {
            final DataType target = declaredReturn.getType();
            Object source = value;
            if (target instanceof StringType && (expression instanceof LiteralExpression || isNegatedLiteral(expression))
                    && value instanceof BigDecimal && ((BigDecimal) value).scale() > 0) {
                source = ((BigDecimal) value).doubleValue();
            }
            final String targetName = target.getName() == null ? "" : target.getName().toUpperCase(Locale.ROOT);
            if (source instanceof String && "VARIANT".equals(targetName)) {
                return ValueCaster.castValue(source, "VARIANT");
            }
            // A value that is not an array becomes the one element of an ARRAY result: a DATE, a
            // timestamp, a text and an OBJECT alike (live: a DATE 2020-01-15 under RETURNS ARRAY is
            // ["2020-01-15"], an OBJECT {"a":1} is [{"a":1}], a VARCHAR 'ab' is ["ab"]).
            if ("ARRAY".equals(targetName) && source != null && !isArrayValue(source)) {
                return JsonHeldValue.of(singleElementArray(source));
            }
            // A whole number under a TIMESTAMP is a seconds epoch, as TO_TIMESTAMP reads one
            // (live: RETURN 1631711999 under TIMESTAMP_NTZ is 2021-09-15 13:19:59).
            if (source instanceof Number && !(source instanceof Double) && targetName.startsWith("TIMESTAMP")) {
                source = SharedFunctionHelpers.epochToLocalDateTime(((Number) source).longValue());
            }
            // A semi-structured result is the JSON of the value, as a variable of that type holds it.
            return heldUnder(source, target);
        } catch (final RuntimeException failed) {
            if (expression.getSourceLine() > 0) {
                recordExpressionFailure(expression.getSourceLine(), expression.getSourcePosition());
            }
            throw failed;
        }
    }

    /**
     * A CALL argument under its parameter's declared type, as the account converts it when the value arrives and
     * before any body runs, whatever language the body is in (live-verified). A BOOLEAN parameter reads a text
     * ('off', 'Yes', ' true ') and a number (0 is false, anything else true), and a text that is no boolean is
     * the CALL's refusal, "Boolean value 'maybe' is not recognized". A NUMBER parameter reads a text and rounds
     * to its scale (1.7777 into NUMBER(5,2) is 1.78, '2.5' into NUMBER is 3), and a value past its width is the
     * number's range refusal. A FLOAT parameter reads a text, a text parameter takes the value's text — its
     * width is NOT enforced ('abcdef' into a VARCHAR(2) parameter answers abcdef) — and a DATE, TIME or TIMESTAMP
     * parameter reads a text as one. An ARRAY parameter wraps a variant that is no array ([1]) and an OBJECT
     * parameter refuses one that is no object; a VARIANT that holds a text or a number converts as that value
     * does, and one that does not fit is "Failed to cast variant value …".
     *
     * @param value   the argument as evaluated
     * @param type    the parameter's declared type
     * @param sqlBody whether the body is a SQL one, which words a number's range refusal for a nullable target
     * @return the value the body receives
     */
    public Object coerceArgument(final Object value, final DataType type, final boolean sqlBody) {
        if (type == null || value == null) {
            return value;
        }
        if (value instanceof VariantValue && ((VariantValue) value).isJsonNull()) {
            // A JSON null is a value of its own in a semi-structured parameter and no value in any other.
            return type.getCategory() == TypeCategory.SEMI_STRUCTURED ? value : null;
        }
        if (type instanceof BooleanType) {
            return value instanceof VariantValue ? variantBoolean((VariantValue) value) : toBooleanValue(value);
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            if (value instanceof VariantValue) {
                return variantNumber((VariantValue) value, numeric, sqlBody);
            }
            refuseArgumentPastWidth(value, numeric, sqlBody);
            return toNumericValue(value, numeric, sqlBody);
        }
        if (type instanceof StringType) {
            return value instanceof String ? value : ValueCaster.castValue(value, "VARCHAR");
        }
        final String name = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        if (name.startsWith("TIMESTAMP") || "DATE".equals(name) || "TIME".equals(name) || "DATETIME".equals(name)) {
            return value instanceof VariantValue ? variantTemporal((VariantValue) value, name)
                : ValueCaster.castValue(value, name);
        }
        if (value instanceof VariantValue) {
            final VariantValue variant = (VariantValue) value;
            if ("ARRAY".equals(name) && !variant.isJsonArray()) {
                return ValueCaster.castValue(value, name);
            }
            if (type instanceof ObjectType && !variant.isJsonObject()) {
                throw new RuntimeException("Failed to cast variant value " + variant.text() + " to OBJECT");
            }
        }
        // A SQL body's parameter is a variable of the body, and holds a semi-structured value as one does.
        return sqlBody ? jsonHeld(value, type) : value;
    }

    /**
     * A VARIANT argument of a DATE, a TIME or a timestamp parameter: a text that spells one, and a text that
     * spells none is refused as the variant it is.
     */
    private static Object variantTemporal(final VariantValue variant, final String name) {
        try {
            return ValueCaster.castValue(variant, name);
        } catch (final RuntimeException unread) {
            if (!String.valueOf(unread.getMessage()).endsWith("is not recognized")) {
                throw unread;
            }
        }
        throw new RuntimeException("Failed to cast variant value " + variant.text() + " to "
            + ("TIMESTAMP".equals(name) ? SessionTimestampMapping.current()
                : "DATETIME".equals(name) ? "TIMESTAMP_NTZ" : name));
    }

    /** A VARIANT argument of a BOOLEAN parameter: a boolean, or a text that spells one. */
    private Object variantBoolean(final VariantValue variant) {
        final JsonNode node = variant.node();
        if (node != null && node.isBoolean()) {
            return Boolean.valueOf(node.booleanValue());
        }
        if (node != null && node.isTextual()) {
            try {
                return toBooleanValue(node.textValue());
            } catch (final RuntimeException notABoolean) {
                // Refused as the variant it is, below.
            }
        }
        throw new RuntimeException("Failed to cast variant value " + variant.text() + " to BOOLEAN");
    }

    /** A VARIANT argument of a numeric parameter: a number, or a text that spells one. */
    private Object variantNumber(final VariantValue variant, final NumericType type, final boolean nullable) {
        final JsonNode node = variant.node();
        if (node != null && node.isNumber()) {
            return toNumericValue(node.isFloatingPointNumber() && !node.isBigDecimal()
                ? (Object) Double.valueOf(node.doubleValue()) : node.decimalValue(), type, nullable);
        }
        if (node != null && node.isTextual()) {
            try {
                return toNumericValue(node.textValue(), type, nullable);
            } catch (final RuntimeException notANumber) {
                if (!String.valueOf(notANumber.getMessage()).startsWith("Numeric value")) {
                    throw notANumber;
                }
            }
        }
        throw new RuntimeException("Failed to cast variant value " + variant.text() + " to "
            + (NumericType.isApproximate(type) ? "REAL" : "FIXED"));
    }

    /**
     * The type an UNTYPED declaration takes from its initialiser (live-verified): an integer literal
     * makes a NUMBER(38,0), a decimal literal a FLOAT, and a NAME a NUMBER(38,0) when it holds a
     * whole-number NUMBER — a FOR counter's NUMBER(9,0) and a NUMBER(10,0) alike — or a FLOAT when its
     * type carries a scale (a NUMBER(10,4) holding 1.7777 makes a FLOAT). Any other initialiser is typed
     * as a SQL expression by {@link #typeOfSqlInitialiser}.
     */
    public DataType inferUntypedDeclarationType(final BaseExpression initialiser) {
        if (initialiser instanceof LiteralExpression) {
            return literalDeclarationType(((LiteralExpression) initialiser).getValue());
        }
        if (isNegatedLiteral(initialiser)) {
            final BaseExpression operand = ((UnaryExpression) initialiser).getOperand();
            return literalDeclarationType(((LiteralExpression) operand).getValue());
        }
        if (initialiser instanceof VariableExpression) {
            final DataType declared = getDeclaredVariableType(((VariableExpression) initialiser).getName());
            if (NumericType.isDecfloat(declared)) {
                return declared;
            }
            if (declared instanceof NumericType) {
                final NumericType numeric = (NumericType) declared;
                return numeric.getScale() == 0 && !"FLOAT".equalsIgnoreCase(numeric.getName())
                    ? WHOLE_NUMBER_TYPE : NumericType.FLOAT;
            }
            if (declared instanceof StringType && !(declared instanceof UuidType)) {
                // A text name makes a text: LET n := SQLROWCOUNT reads VARCHAR in SYSTEM$TYPEOF(:n).
                return new StringType("VARCHAR", StringResultWidths.UNBOUNDED);
            }
        }
        return null;
    }

    /**
     * The type an untyped declaration takes from an initialiser {@link #inferUntypedDeclarationType} does
     * not type directly — see {@link DeclarationTypes} — over the declared types in scope.
     *
     * @param sqlInitialiser the initialiser as a SQL expression, or null
     * @return the declared type, or null when none can be determined
     */
    public DataType typeOfSqlInitialiser(final Expression sqlInitialiser) {
        return DeclarationTypes.ofExpression(sqlInitialiser, variableTypes, queryExecutor);
    }

    private static DataType literalDeclarationType(final Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).scale() > 0 ? NumericType.FLOAT : WHOLE_NUMBER_TYPE;
        }
        if (value instanceof Double || value instanceof Float) {
            return NumericType.FLOAT;
        }
        return value instanceof Number ? WHOLE_NUMBER_TYPE : null;
    }

    /**
     * Whether RETURN of this expression yields TEXT. Snowflake types a routine's result from the
     * static type of the expression the EXECUTED RETURN names — a sibling RETURN elsewhere in the
     * block, another branch, a nested block or a handler that never fires does not participate, so
     * deciding here at the RETURN that runs is live's own model, not an approximation of it. A
     * literal, an arithmetic expression, a DECLAREd variable and a routine PARAMETER all keep their
     * own type; so does SQLROWCOUNT, which is numeric. A name carrying no declared type at all — a
     * FOR-loop counter written bare — makes the result VARCHAR, as do the text-typed SQLCODE, SQLSTATE
     * and SQLERRM; bound as {@code :i}, the counter carries its NUMBER(9,0).
     *
     * <p>A declared RETURNS does not override this. A procedure declaring {@code RETURNS INTEGER}
     * still answers text when its RETURN names an untyped variable, so the rule belongs here rather
     * than at either caller.
     */
    private boolean returnsAsText(final BaseExpression expression) {
        if (!(expression instanceof VariableExpression)) {
            return false;
        }
        final VariableExpression variable = (VariableExpression) expression;
        final String name = variable.getName();
        if (name == null) {
            return false;
        }
        final String canonical = name.toUpperCase(Locale.ROOT);
        if (variable.isBindForm() && counterTypes.containsKey(canonical)) {
            // Bound as :i, a FOR counter carries its NUMBER(9,0): only the bare name is text.
            return false;
        }
        return !TYPED_SCRIPT_VARIABLES.contains(canonical) && !variableTypes.containsKey(canonical)
            && !recordFieldTypes.containsKey(canonical);
    }

    /**
     * The text form of a returned scalar. A result set is not a scalar and is handed back untouched,
     * as is NULL — which stays NULL rather than becoming the four letters.
     */
    private Object asReturnedText(final Object value) {
        if (value == null || value instanceof ResultSet || value instanceof ResultSetVariable
                || value instanceof Cursor) {
            return value;
        }
        return String.valueOf(value);
    }

    /**
     * Resolve a procedural value to a {@link ResultSet}: a raw ResultSet (e.g. from
     * {@code rs := (EXECUTE IMMEDIATE …)}) or a {@link ResultSetVariable} (executing its DEFAULT query on
     * first use and memoizing it). Returns null if the value is not result-set-like. This unifies the two
     * RESULTSET representations so RETURN TABLE and FOR-iteration accept either.
     */
    private ResultSet resolveToResultSet(final Object value) {
        if (value instanceof ResultSet) {
            return (ResultSet) value;
        }
        if (value instanceof ResultSetVariable) {
            final ResultSetVariable rsv = (ResultSetVariable) value;
            requireInitialized(rsv);
            if (rsv.getResultSet() == null && rsv.getSelectQuery() != null && queryExecutor != null) {
                // A refusal in a query assigned to a RESULTSET, res := (SELECT …), is placed from the query's
                // opening parenthesis; in a query the RESULTSET is declared with — DECLARE … DEFAULT (…), LET … :=
                // (…) — every place is the query's own (live-verified).
                final List<int[]> binds = new ArrayList<>();
                final String bound = bindSubstitutor.substitute(rsv.getSelectQuery(), true, binds);
                final int[] lead = rsv.getParenthesisLead();
                final List<ResultSet> r;
                try {
                    r = issuePlaced(bound, binds, 0);
                } catch (final RuntimeException refused) {
                    // An argument-count refusal echoes each bind as the block wrote it.
                    final RuntimeException echoed = BoundText.echoedAsWritten(refused, rsv.getSelectQuery(), bound,
                        binds);
                    throw lead == null ? echoed : BoundText.displaced(echoed, lead[0], lead[1]);
                }
                // An unaliased column is named by the query as the block wrote it.
                rsv.setResultSet(r.isEmpty() ? null
                    : BindColumnNames.asWritten(r.get(0), rsv.getSelectQuery(), bound, queryExecutor));
            }
            return rsv.getResultSet();
        }
        return null;
    }

    /**
     * RESULTSET_FROM_CURSOR(&lt;cursor&gt;): the result set of the named cursor, opening it first if needed.
     * The argument is a cursor NAME (a {@link VariableExpression}), not a value — cursors live in the
     * cursor manager, not the variable scope.
     */
    private ResultSet resultSetFromCursor(final BaseExpression cursorArg) {
        final String cursorName = cursorArg instanceof VariableExpression
            ? ((VariableExpression) cursorArg).getName() : String.valueOf(cursorArg);
        final Cursor cursor = readCursor(cursorName);
        if (cursor == null) {
            throw new RuntimeException("RESULTSET_FROM_CURSOR: cursor not declared: " + cursorName);
        }
        if (!cursor.isOpen()) {
            openCursorWithQuery(cursor);
        }
        return cursor.getResultSet();
    }

    private void executeReturnTable(final ReturnTableStatement stmt) {
        if (stmt.getQuery() != null) {
            // RETURN TABLE(SELECT ...) — execute the query and return its rows.
            if (queryExecutor == null) {
                throw new RuntimeException("QueryExecutor not available for RETURN TABLE");
            }
            final List<ResultSet> r = issue(substituteBindVariables(stmt.getQuery()));
            returnValue = r.isEmpty() ? null : r.get(0);
            returnTableFlag = true;
        } else if (stmt.getExpression() != null) {
            final Object value = evaluateExpression(stmt.getExpression());
            final ResultSet rs;
            try {
                rs = resolveToResultSet(value);
            } catch (final RuntimeException refused) {
                // A RESULTSET that cannot be read is refused where the RETURN names it.
                recordStatementFailure(stmt.getExpression().getSourceLine(), stmt.getExpression().getSourcePosition());
                throw refused;
            }
            if (rs != null) {
                returnValue = rs;
                returnTableFlag = true;
            } else {
                throw new RuntimeException("RETURN TABLE expects a RESULTSET, got: " +
                    (value != null ? value.getClass().getSimpleName() : "null"));
            }
        }
        returnFlag = true;
    }

    private void executeSql(final SqlStatement stmt) {
        if (queryExecutor != null && stmt.getSql() != null && !stmt.getSql().isBlank()) {
            boolean sawResult = false;
            final boolean outerSelectInto = runningBlockSelectInto;
            runningBlockSelectInto = stmt.isSelectInto();
            try {
                // A statement that keeps its :names is still held to what a statement can bind.
                final List<int[]> binds = new ArrayList<>();
                final String substituted = bindSubstitutor.substitute(stmt.getSql(), true, binds);
                // An assignment of a CALL's answer is read again from its own text, and its CALL stands where
                // the block wrote it.
                assignedCallPlace = stmt.keepsWrittenBinds()
                    ? new int[] {stmt.getSourceLine(), stmt.getSourcePosition()} : null;
                for (final ResultSet result
                        : stmt.keepsWrittenBinds() ? issue(stmt.getSql()) : issuePlaced(substituted, binds, 0)) {
                    recordSqlRowCount(result);
                    if (result != null) {
                        sawResult = true;
                    }
                }
            } finally {
                assignedCallPlace = null;
                runningBlockSelectInto = outerSelectInto;
            }
            if (!sawResult) {
                // TRUNCATE and some DDL complete without a result set here; live still resets the
                // DML trio and counts the statement's one status line.
                recordStatementWithoutResult();
            }
            // Snowflake runs each statement of a stored procedure in its own autocommit transaction
            // (unless an explicit BEGIN is open) — commit here exactly as the top-level entry does.
            queryExecutor.getTransactionManager().autocommitStatementEnd();
        }
    }

    /**
     * Record the DML-status globals after one executed SQL statement. A DML result — one carrying
     * "number of rows …" (inserted / updated / deleted) columns — sets {@code SQLROWCOUNT} to the
     * affected total, {@code SQLFOUND}/{@code SQLNOTFOUND} to whether that total is non-zero, and
     * {@code ACTIVITY_COUNT} to the same total. EVERY OTHER completed statement — a SELECT, DDL,
     * and TRUNCATE TABLE too — resets the three DML variables to NULL and sets
     * {@code ACTIVITY_COUNT} to its own result's row count (a DDL status line counts 1, a SELECT
     * its rows). All four start NULL. Live-verified, including the reset: a SELECT between the DML
     * and the read answers NULL for the trio, and scripting-internal statements (LET, assignments,
     * control flow) touch none of them. Called after each statement in a procedural block
     * (top-level blocks execute their statements via the visitor, not {@code executeSql}).
     */
    public void recordSqlRowCount(final ResultSet result) {
        if (result == null || result.getColumns() == null) {
            return;
        }
        // Only a DML statement's own count grid sets the trio: a query whose columns merely carry the count
        // names ("number of rows inserted"), a RESULT_SCAN over a DML result included, resets them like any
        // other query (live-verified).
        final Long counted = result.getUpdateCount();
        if (counted != null) {
            final long total = counted.longValue();
            setVariable("SQLROWCOUNT", total);
            setVariable("SQLFOUND", total > 0);
            setVariable("SQLNOTFOUND", total == 0);
            setVariable("ACTIVITY_COUNT", total);
        } else {
            setVariable("SQLROWCOUNT", null);
            setVariable("SQLFOUND", null);
            setVariable("SQLNOTFOUND", null);
            setVariable("ACTIVITY_COUNT", (long) result.getRows().size());
        }
    }

    /**
     * The query ID LAST_QUERY_ID() names in the running session now, or null before the session ran any: what
     * {@link #noteStatementQueryId} compares against once a statement completes.
     *
     * @return the query ID, or null
     */
    public String lastQueryIdOfSession() {
        return queryExecutor == null ? null : queryExecutor.getResultCache().getLastQueryId();
    }

    /**
     * Starts a script's SQLID: NULL, because the script has run no statement yet. Called as the outermost block of a
     * script begins - a block run directly, an EXECUTE IMMEDIATE's or a procedure's body - so the blocks nested in it
     * share the one SQLID.
     */
    public void startStatementQueryIds() {
        setVariable(SQLID, null);
    }

    /**
     * Notes a completed statement of a script in SQLID, the query ID of the last statement the script ran: one
     * that took a query ID, itself or through what it ran - a SQL statement, a CALL, an EXECUTE IMMEDIATE, a
     * declaration's or a cursor's query, a SELECT ... INTO, the last statement of an IF or a loop - sets it to the ID
     * LAST_QUERY_ID() names once it completes. A statement that took none (LET, an assignment, a RETURN, unrecorded
     * SQL) leaves it as it was, and so does one that failed. Its value before the script ran any statement, NULL, is
     * not verified against an account, and neither is a failed statement leaving it alone.
     *
     * @param queryIdBefore what {@link #lastQueryIdOfSession} answered as the statement began
     */
    public void noteStatementQueryId(final String queryIdBefore) {
        if (!isExecutingBlock()) {
            return;
        }
        final String queryIdAfter = lastQueryIdOfSession();
        if (queryIdAfter != null && !queryIdAfter.equals(queryIdBefore)) {
            setVariable(SQLID, queryIdAfter);
        }
    }

    /** The DML-status effect of a completed statement whose result never surfaced as a result set. */
    public void recordStatementWithoutResult() {
        setVariable("SQLROWCOUNT", null);
        setVariable("SQLFOUND", null);
        setVariable("SQLNOTFOUND", null);
        setVariable("ACTIVITY_COUNT", 1L);
    }

    private void executeCall(final CallStatement stmt) {
        if (queryExecutor == null) {
            throw new RuntimeException("QueryExecutor not available to CALL a procedure");
        }
        // A CALL is a statement of its own and runs as it is written. That routes through the visitor's CALL
        // handler, reusing the full multi-language procedure dispatch (SQL / JavaScript / Python / Java / Scala),
        // which binds each :name from this block's scope — a loop counter and a DECLAREd variable by its declared
        // type — and reads a bare name as an identifier of the statement's, refused as one (live-verified). The
        // re-entrant execute() runs at depth > 0, so this block's live cursors are preserved. A bare CALL discards
        // the value the procedure returns; CALL ... INTO :variable assigns it to the variable, coerced to its
        // declared type as an assignment coerces. A statement cannot bind an infinity or a record's field, which the
        // bind reader refuses before the CALL runs.
        substituteStatementBinds(stmt.getWrittenCall());
        final List<ResultSet> results = issue(stmt.getWrittenCall());
        // A CALL is a statement too — commit its implicit transaction like any other (Snowflake).
        queryExecutor.getTransactionManager().autocommitStatementEnd();
        if (stmt.getIntoTarget() != null) {
            // A value the variable does not take is the statement's own expression failing, where it stands.
            final LiteralExpression assigned = new LiteralExpression(procedureReturnValue(results));
            assigned.setSourcePosition(stmt.getSourceLine(), stmt.getSourcePosition());
            executeStatement(new SetStatement(stmt.getIntoTarget(), assigned));
        }
    }

    /**
     * The value a CALL hands back INTO a variable: the first column of the one row the procedure answered, or null
     * when it answered none; several rows are refused.
     *
     * @param result the CALL's result: a result set, a list of them, or the value itself
     * @return the value
     */
    public static Object procedureReturnValue(final Object result) {
        Object answer = result;
        if (answer instanceof List) {
            final List<?> results = (List<?>) answer;
            if (results.isEmpty()) {
                return null;
            }
            answer = results.get(0);
        }
        if (answer instanceof ResultSet) {
            final ResultSet rs = (ResultSet) answer;
            if (rs.getRowCount() > 1) {
                // A table procedure's rows go INTO a variable as a query's do: one row at most (live-verified).
                throw new RuntimeException(
                    "A SELECT INTO statement expects exactly 1 returned row, but got " + rs.getRowCount() + ".");
            }
            return rs.getRowCount() > 0 ? rs.getRows().get(0).getValue(0) : null;
        }
        return answer;
    }

    /** Whether a block expression is the NULL literal itself. */
    private static boolean isNullLiteral(final BaseExpression expression) {
        return expression instanceof LiteralExpression && ((LiteralExpression) expression).getValue() == null;
    }

    /**
     * A number as SQL reads it back. A FLOAT stays one — written bare, 1.0E30 is the exact number, which prints
     * its thirty zeros where the FLOAT prints 1e+30 — and no numeric literal spells NaN or an infinity, whose
     * words written bare are names, so each of those is the cast of its word.
     */
    /**
     * The text of a call's argument that reads no name and is an expression of its own - an operator over values or
     * a cast - as written; null for a literal, a name or anything else, which is written out from its value.
     */
    private static String writtenExpressionArgument(final FunctionCallExpression funcExpr, final int index) {
        final BaseExpression argument = funcExpr.getArguments().get(index);
        if (!(argument instanceof BinaryExpression) && !(argument instanceof SqlScalarExpression)) {
            return null;
        }
        final String written = funcExpr.getNamelessArgument(index);
        return written == null || written.isBlank() ? null : written;
    }

    /**
     * Arithmetic and concatenation over literals and the block's variables as the SQL that computes it: a NUMBER or
     * FLOAT variable written as its value's conversion to its declared type (see {@link #boundConversion}), a text one
     * as the literal of its value, each literal as written, each operation in parentheses and a negation as one. A
     * refusal of the call then echoes the expression as the account does —
     * {@code ABS(ALL (CAST('5' AS NUMBER(38,0))) + 1)}, {@code ABS(ALL NEGATE(CAST('5' AS NUMBER(38,0))))},
     * {@code UPPER(DISTINCT 'a' || 'b')} (live-verified). Null for anything else, which is written out from its value.
     */
    private String boundArithmetic(final BaseExpression argument) {
        if (argument instanceof VariableExpression) {
            if (!hasVariable(((VariableExpression) argument).getName())) {
                return null;
            }
            final Object value = evaluateExpression(argument);
            final String bound = boundConversion(argument, value);
            if (bound == null && value instanceof String && variableTypes.get(
                    ((VariableExpression) argument).getName().toUpperCase(Locale.ROOT)) instanceof StringType) {
                return SqlStringLiterals.encode((String) value);
            }
            return bound == null ? null : "(" + bound + ")";
        }
        if (argument instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) argument).getValue();
            if (value instanceof String) {
                return SqlStringLiterals.encode((String) value);
            }
            return value instanceof BigDecimal || value instanceof Long || value instanceof Integer
                ? numberLiteral((Number) value) : null;
        }
        if (argument instanceof UnaryExpression
                && ((UnaryExpression) argument).getOperator() == UnaryOperator.NEGATE) {
            final String operand = boundArithmetic(((UnaryExpression) argument).getOperand());
            return operand == null ? null : "(-" + operand + ")";
        }
        if (!(argument instanceof BinaryExpression)) {
            return null;
        }
        final BinaryExpression binary = (BinaryExpression) argument;
        final String symbol = arithmeticSymbol(binary.getOperator());
        final String left = symbol == null ? null : boundArithmetic(binary.getLeft());
        final String right = left == null ? null : boundArithmetic(binary.getRight());
        return right == null ? null : "(" + left + " " + symbol + " " + right + ")";
    }

    /** The symbol of an arithmetic or concatenation operator, or null for any other. */
    private static String arithmeticSymbol(final BinaryOperator operator) {
        switch (operator) {
            case ADD:
                return "+";
            case SUBTRACT:
                return "-";
            case MULTIPLY:
                return "*";
            case DIVIDE:
                return "/";
            case MODULO:
                return "%";
            case CONCAT:
                return "||";
            default:
                return null;
        }
    }

    private static String numberLiteral(final Number value) {
        final String nonFinite = NonFiniteDoubles.castOfWord(value);
        if (nonFinite != null) {
            return nonFinite;
        }
        return value instanceof Double || value instanceof Float ? value + "::FLOAT" : value.toString();
    }

    /**
     * Note that a SELECT … INTO of no rows has just left a target NULL, which a RETURN of the bare name reports
     * as a NULL of no type until the name is assigned again.
     *
     * @param name the target's name
     */
    public void markEmptySelectTarget(final String name) {
        emptySelectTargets.add(name.toUpperCase(Locale.ROOT));
    }

    /** Mark {@code e} as the exception now being handled (so a bare RAISE; inside the handler can re-raise it). */
    public void pushHandledException(final Exception e) {
        handledExceptions.push(e);
    }

    /** Done handling the current exception. */
    public void popHandledException() {
        if (!handledExceptions.isEmpty()) {
            handledExceptions.pop();
        }
    }

    /** How many BEGIN…END blocks are currently open; 1 inside the outermost. */
    public int getBlockDepth() {
        return blockDepth;
    }

    /** The exception currently being handled, or null if not inside an exception handler. */
    public Exception getCurrentHandledException() {
        return handledExceptions.peek();
    }

    private void executeRaise(final RaiseStatement stmt) {
        if (stmt.isAgain()) {
            // In a loop or a branch of a handler as in the handler's own statements (live-verified).
            final Exception handled = handledExceptions.peek();
            if (handled instanceof RuntimeException) {
                throw (RuntimeException) handled;
            }
            throw handled != null ? new RuntimeException(handled.getMessage(), handled)
                : new ProceduralException("Error raised");
        }
        if (stmt.isUserDefinedException()) {
            final String exceptionName = stmt.getExceptionName().toUpperCase(Locale.ROOT);
            final UserDefinedException exception = exceptionVisibleAt(exceptionName, exceptionScopes.size());
            if (exception == null) {
                throw new RuntimeException("Undefined exception: " + exceptionName);
            }
            final ProceduralException raised =
                new ProceduralException(exception.getErrorCode(), exception.getMessage(), exceptionName);
            raised.setDeclaration(exception);
            raised.setSourcePosition(stmt.getSourceLine(), stmt.getSourcePosition());
            throw raised;
        } else {
            final String message = evaluateExpression(stmt.getMessage()).toString();
            throw new ProceduralException(message);
        }
    }

    /**
     * The statements a dynamic text holds. A fault of its syntax is placed in the text as written, whatever its
     * placeholders were written out as: {@code 'SELECT ? x y'} names the y at position 11 (live-verified).
     */
    private int statementCountOf(final String sqlText, final List<int[]> written) {
        if (written.isEmpty()) {
            return queryExecutor.requestStatementCountOf(sqlText);
        }
        // A comment that opens the text is not counted: its places are told from the first word.
        final SourcePosition comment = LeadingCommentOffset.of(sqlText);
        final SourcePosition origin = comment != null ? comment : new SourcePosition(1, 0);
        final SourcePosition outer = LeadingCommentOffset.begin(origin);
        final BoundText displaced = LeadingCommentOffset.bind(new BoundText(origin, written));
        try {
            return queryExecutor.requestStatementCountOf(sqlText);
        } finally {
            LeadingCommentOffset.unbind(displaced);
            LeadingCommentOffset.end(outer);
        }
    }

    /**
     * Run a text as a body of its own, the way a statement that runs a dynamic text does: it sees none of the
     * variables of the block that asks for it and nothing that block is handling, a block in it is the top
     * block of its own body, and a failure it lets out is left for the asking statement to name.
     */
    private List<ResultSet> executeAsOwnBody(final String sqlText, final List<int[]> written) {
        // Texts run texts only so deep (see StatementNesting).
        StatementNesting.enter();
        final int enclosingFrame = enterDynamicFrame();
        enterIsolatedScope();
        final ProcedureFrame enclosingBody = enterProcedureFrame();
        boolean failed = true;
        try {
            // A refusal is placed in the text as written, whatever its placeholders were written out as.
            final List<ResultSet> result = written.isEmpty() ? issue(sqlText) : issuePlaced(sqlText, written, 0);
            failed = false;
            return result;
        } finally {
            exitProcedureFrame(enclosingBody, failed);
            exitScope();
            exitDynamicFrame(enclosingFrame);
            StatementNesting.exit();
        }
    }

    /**
     * Runs a statement, or a query one of its expressions reads, that the running body issues — which a body past
     * the depth bodies run to may not (see {@link CallDepth}).
     */
    private List<ResultSet> issue(final String sql) {
        if (queryExecutor.getTransactionManager() != null) {
            CallDepth.requireMayIssue(queryExecutor.getTransactionManager().scopeDepth());
        }
        return queryExecutor.execute(sql);
    }

    /**
     * {@link #issue} for a statement whose binds have been written out: a refusal is placed in the text the block
     * wrote (see {@link BoundText}), and a refusal of a call's or an operator's arguments {@code lead} columns
     * further in on its first line.
     *
     * @param sql   the statement with its binds written out
     * @param binds each bind written out, as the substitution noted it
     * @param lead  the columns such a refusal counts before the statement's own first one
     * @return the statement's results
     */
    private List<ResultSet> issuePlaced(final String sql, final List<int[]> binds, final int lead) {
        try {
            if (binds.isEmpty()) {
                return issue(sql);
            }
            if (queryExecutor.getTransactionManager() != null) {
                CallDepth.requireMayIssue(queryExecutor.getTransactionManager().scopeDepth());
            }
            // A comment that opens the text is not counted: its places are told from the first word.
            final SourcePosition comment = LeadingCommentOffset.of(sql);
            final SourcePosition origin = comment != null ? comment : new SourcePosition(1, 0);
            final BoundText displaced = LeadingCommentOffset.bind(new BoundText(origin, binds));
            try {
                return queryExecutor.executeFrom(sql, origin);
            } finally {
                LeadingCommentOffset.unbind(displaced);
            }
        } catch (final RuntimeException refused) {
            throw lead == 0 ? refused : BoundText.led(refused, lead);
        }
    }

    private void executeDeclareException(final DeclareExceptionStatement stmt) {
        final String name = stmt.getExceptionName().toUpperCase(Locale.ROOT);
        final UserDefinedException exception = new UserDefinedException(name, stmt.getErrorCode(), stmt.getMessage());
        if (exceptionScopes.isEmpty()) {
            exceptionScopes.push(new HashMap<String, UserDefinedException>());
        }
        exceptionScopes.peek().put(name, exception);
    }

    public boolean hasException(final String name) {
        return exceptionVisibleAt(name, exceptionScopes.size()) != null;
    }

    /**
     * How many blocks of the running body hold exception declarations of their own: what a block notes when it
     * starts, to name its own exceptions by later (see {@link #exceptionVisibleAt}).
     *
     * @return the number of running blocks of the body
     */
    public int exceptionScopeDepth() {
        return exceptionScopes.size();
    }

    /**
     * The exception a name stands for in the block that noted {@code depth} when it started: that block's own
     * declaration, else the nearest enclosing block's — never one a block nested in it declared, which a handler
     * of the outer block does not see even while the nested block is still running (live-verified).
     *
     * @param name  the exception's name
     * @param depth the value {@link #exceptionScopeDepth()} answered inside the block
     * @return the declaration, or null when the block sees none of that name
     */
    public UserDefinedException exceptionVisibleAt(final String name, final int depth) {
        final String canonical = name.toUpperCase(Locale.ROOT);
        int deeper = exceptionScopes.size() - depth;
        for (final Map<String, UserDefinedException> scope : exceptionScopes) {
            if (deeper-- > 0) {
                continue;
            }
            final UserDefinedException declared = scope.get(canonical);
            if (declared != null) {
                return declared;
            }
        }
        // The two exceptions a script has without declaring them are raised by name as any other is.
        return BuiltInExceptions.of(canonical);
    }

    public Object evaluateExpression(final BaseExpression expr) {
        return evaluateExpression(expr, null);
    }

    /**
     * Evaluate an expression, a block's own one compiled first (see {@link BlockExpressionTypes}).
     *
     * @param expr   the expression
     * @param target the declared type a value given to a variable converts to, or null
     * @return the value
     */
    /**
     * A logging statement, {@code SYSTEM$LOG_INFO('message');}: its arguments are evaluated and the message is
     * written to the engine's own log. The statement answers nothing.
     */
    private void executeLogging(final FunctionCallExpression statement) {
        final List<Object> told = new ArrayList<Object>();
        for (final BaseExpression argument : statement.getArguments()) {
            told.add(evaluateExpression(argument, null));
        }
        if (!told.isEmpty()) {
            logger.info("{}: {}", statement.getFunctionName(), told.size() == 1 ? told.get(0) : told);
        }
    }

    private Object evaluateExpression(final BaseExpression expr, final DataType target) {
        try {
            if (expr != null && expr.getJudgedFrom() != null && queryExecutor != null) {
                try {
                    judge(expr.getJudgedFrom(), target);
                } catch (final BlockExpressionTypeError refused) {
                    // The whole expression compiles first: a name one of its subqueries cannot resolve is refused
                    // ahead of its types, 12 + (1 IN (SELECT missing …)) naming MISSING (live-verified).
                    compileSubqueriesOf(expr);
                    throw refused;
                }
            }
            return evaluateBuiltExpression(expr);
        } catch (final RuntimeException failed) {
            // Live splits the uncaught kinds by what was RUNNING: a fault raised evaluating an
            // expression is EXPRESSION_ERROR at the expression's own offset. The record is
            // first-wins, so this innermost one beats the statement record taken on the way out.
            if (expr != null && expr.getSourceLine() > 0) {
                recordExpressionFailure(expr.getSourceLine(), expr.getSourcePosition());
            }
            // A bare word where a unit is read, naming none, is to a block the name nothing declares.
            final UnknownUnitWord unit = UnknownUnitWord.in(failed);
            if (unit != null) {
                throw new RuntimeException(SqlCompilationError.at(1, 0, "invalid identifier '" + unit.word() + "'"),
                    failed);
            }
            throw failed;
        }
    }

    private Object evaluateBuiltExpression(final BaseExpression expr) {
        // Simplified expression evaluation
        if (expr instanceof LiteralExpression) {
            return ((LiteralExpression) expr).getValue();
        } else if (expr instanceof SqlScalarExpression) {
            return evaluateSqlScalar((SqlScalarExpression) expr);
        } else if (expr instanceof SessionVarRefExpression) {
            final String varName = ((SessionVarRefExpression) expr).getName().toUpperCase(Locale.ROOT);
            SessionVariableUse.requireCallersRights(varName);
            // $name is the session's variable wherever it is written: a block variable, a parameter or a loop
            // counter of the same name is read by its bare name or as :name, never through the $ (live-verified).
            if (queryExecutor != null) {
                final SecurityManager sm = queryExecutor.getSecurityManager();
                if (sm != null && sm.getSessionContext() != null) {
                    return sm.getSessionContext().getSessionVariable(varName);
                }
                return queryExecutor.getSessionVariables().get(varName);
            }
            return null;
        } else if (expr instanceof VariableExpression) {
            handlerLets.requireReadable(((VariableExpression) expr).getName(), expr.getSourceLine(),
                expr.getSourcePosition());
            return readHeld(((VariableExpression) expr).getName(), getVariable(((VariableExpression) expr).getName()));
        } else if (expr instanceof BinaryExpression) {
            final BinaryExpression binExpr = (BinaryExpression) expr;
            if (binExpr.getOperator() == BinaryOperator.AND || binExpr.getOperator() == BinaryOperator.OR) {
                return evaluateConnective(binExpr);
            }
            final Object left = evaluateExpression(binExpr.getLeft());
            final Object right = evaluateExpression(binExpr.getRight());
            if (isDecfloatOperand(binExpr.getLeft()) || isDecfloatOperand(binExpr.getRight())) {
                return evaluateDecfloatOperation(binExpr, left, right);
            }
            return evaluateBinaryOperation(binExpr.getOperator(), left, right);
        } else if (expr instanceof ExecuteImmediateExpression) {
            final ExecuteImmediateExpression execExpr = (ExecuteImmediateExpression) expr;
            final Object sqlValue = evaluateExpression(execExpr.getSqlExpression());
            String sqlText = sqlValue != null ? sqlValue.toString() : "";

            // Bind USING (...) values to the ? placeholders in turn and to :n by number. A FOR loop's record
            // binds nothing: its placeholder stays unset (see isLoopRecord). The text reads none of the block's
            // variables (see DynamicStatementBinds).
            final List<BaseExpression> binds = execExpr.getUsingBindings();
            final List<Object> bindValues = new ArrayList<>();
            final Set<Integer> unbound = new HashSet<>();
            if (binds != null) {
                for (final BaseExpression bind : binds) {
                    if (bind instanceof VariableExpression && !hasVariable(((VariableExpression) bind).getName())
                            && isLoopRecord(((VariableExpression) bind).getName())) {
                        unbound.add(bindValues.size() + 1);
                    }
                    bindValues.add(usingBind(bind instanceof VariableExpression
                        ? ((VariableExpression) bind).getName() : null, usingArgument(bind)));
                }
            }
            final List<int[]> written = new ArrayList<>();
            final String writtenText = sqlText;
            try {
                sqlText = DynamicStatementBinds.bind(sqlText, bindValues, unbound, written);
            } catch (final RuntimeException unset) {
                recordStatementFailure(execExpr.getStatementLine(), execExpr.getStatementPosition());
                throw unset;
            }

            if (queryExecutor == null) {
                throw new RuntimeException("QueryExecutor not available for EXECUTE IMMEDIATE");
            }
            // The text is counted against the session's MULTI_STATEMENT_COUNT once it parses, before any of it
            // runs, and a text of several answers as a multi-statement request (see DynamicStatementCount). A
            // failure is the statement's, where the EXECUTE IMMEDIATE begins.
            try {
                if (QueryExecutor.holdsNoStatement(sqlText)) {
                    throw new RuntimeException("Executing NULL or empty statement is not permitted.");
                }
                final int statements = statementCountOf(sqlText, written);
                DynamicStatementCount.require(statements, queryExecutor.getSecurityManager(), true);
                final List<ResultSet> result = executeAsOwnBody(sqlText, written);
                if (statements > 1) {
                    final MultiStatementAnswer answer = new MultiStatementAnswer(result);
                    recordSqlRowCount(answer);
                    return answer;
                }
                if (!result.isEmpty()) {
                    recordSqlRowCount(result.get(0));
                }
                return result.isEmpty() ? result : result.get(0);
            } catch (final RuntimeException failed) {
                recordStatementFailure(execExpr.getStatementLine(), execExpr.getStatementPosition());
                // A bound value echoed as a predicate is named by its placeholder (see UsingBindEcho).
                throw UsingBindEcho.named(failed, writtenText, bindValues, unbound);
            }
        } else if (expr instanceof UnaryExpression) {
            final UnaryExpression unaryExpr = (UnaryExpression) expr;
            return evaluateUnaryOperation(unaryExpr);
        } else if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression funcExpr = (FunctionCallExpression) expr;
            // RESULTSET_FROM_CURSOR(<cursor>) — surface an (opened) cursor's result set as a RESULTSET so
            // it can be RETURN TABLE(...)'d or FOR-iterated. The argument names a cursor, not a value.
            if ("RESULTSET_FROM_CURSOR".equalsIgnoreCase(funcExpr.getFunctionName())
                    && !funcExpr.getArguments().isEmpty()) {
                return resultSetFromCursor(funcExpr.getArguments().get(0));
            }
            // Evaluate through the full query path ("SELECT func(args)") rather than the
            // FunctionRegistry/ExpressionEvaluatorVisitor directly: that path also resolves
            // USER-DEFINED functions (JS/Python/SQL UDFs), which the built-in registry does not.
            // Routing through the visitor breaks UDF calls in scripts (see features/UdfAssignTest).
            if (queryExecutor != null) {
                try {
                    final String callExpr = rebuiltCall(funcExpr);
                    // Execute as SELECT expression, outside the session's statement history: an expression is
                    // not a statement, and must not move LAST_QUERY_ID off the statement before it. Its handler
                    // calls start afresh, as the block's other expressions do (see evaluateSqlScalar).
                    final HandlerStateScope handlerStates = StatementHandlerStates.begin();
                    final List<ResultSet> results;
                    try {
                        results = queryExecutor.executeUnrecorded("SELECT " + callExpr);
                    } finally {
                        StatementHandlerStates.end(handlerStates);
                    }
                    if (!results.isEmpty()) {
                        final ResultSet rset = results.get(0);
                        if (rset.getRowCount() > 0) return rset.getRows().get(0).getValue(0);
                    }
                } catch (final Exception e) {
                    // The fault's own sentence travels bare — live's uncaught wrapper says
                    // "Numeric value 'a' is not recognized", never a Frostlake-invented
                    // "Error evaluating function TO_NUMBER:" preamble around it.
                    throw e instanceof RuntimeException ? namingBoundCasts((RuntimeException) e, funcExpr)
                        : new RuntimeException(e.getMessage(), e);
                }
            }
            return null;
        }
        return null;
    }

    /**
     * A call of a block expression as the SQL that evaluates it: the function, its quantifier, and each argument
     * written out as the literal of its value.
     *
     * @param funcExpr the call
     * @return its SQL text
     */
    private String rebuiltCall(final FunctionCallExpression funcExpr) {
        // Delegate to QueryExecutor expression evaluation via re-building the SQL
        final StringBuilder callExpr = new StringBuilder(funcExpr.getFunctionName()).append("(");
        // The quantifier as written stays with the call, so SQL judges it as it judges a query's.
        if (funcExpr.getQuantifier() != null) {
            callExpr.append(funcExpr.getQuantifier()).append(' ');
        }
        final List<BaseExpression> argExprs = funcExpr.getArguments();
        for (int i = 0; i < argExprs.size(); i++) {
            if (i > 0) callExpr.append(", ");
            final Object av = evaluateExpression(argExprs.get(i));
            final int argumentStart = callExpr.length();
            // A bare name that is NOT a declared variable is a KEYWORD ARGUMENT, not a value: the
            // idiomatic DATEADD(MINUTE, 30, …) / DATE_TRUNC(MONTH, …) / DATE_PART(WEEK, …) parse
            // their date part as an identifier, which evaluates to null. Emitting NULL for it made
            // the rebuilt call fail inside the function (an NPE for DATEADD), so re-emit the name.
            if (i < argExprs.size() && argExprs.get(i) instanceof VariableExpression) {
                final String argName = ((VariableExpression) argExprs.get(i)).getName();
                if (!hasVariable(argName)) {
                    callExpr.append(argName);
                    continue;
                }
            }
            final String bound = boundConversion(argExprs.get(i), av);
            final String asWritten = av instanceof BigDecimal ? writtenExpressionArgument(funcExpr, i) : null;
            final String asBound = asWritten == null && (av instanceof Number || av instanceof String)
                && (argExprs.get(i) instanceof BinaryExpression || argExprs.get(i) instanceof UnaryExpression)
                ? boundArithmetic(argExprs.get(i)) : null;
            if (bound != null) {
                callExpr.append(bound);
            } else if (asBound != null) {
                // Arithmetic over the block's names is SQL's to type too, each name bound as its value's conversion
                // to its declared type: TO_VARCHAR(x / 4) over an INT holding 10 is 2.500000 (live-verified).
                callExpr.append(asBound);
            } else if (asWritten != null) {
                // An expression over no name is SQL's to type as written, so the call reads it at the type SQL
                // gives it: TO_VARCHAR(10 / 4) is 2.500000 and TO_VARCHAR(1.50::NUMBER(10,2)) is 1.50 in a block
                // as in a query (live-verified), where the value's own scale printed 2.5 and 1.5.
                callExpr.append(asWritten);
            } else if (av == null) {
                // A NULL an argument folds to is not the NULL it would be written as: SQL converts an
                // untyped NULL where an operator or a call takes it, and a call that needs a constant
                // refuses the conversion — RANDOM(NULL + 1) is "argument 1 to function RANDOM needs to
                // be constant", where RANDOM(NULL) is refused for its seed (live-verified). So an
                // argument that reads no name is handed on as written.
                final String written = isNullLiteral(argExprs.get(i)) ? null : funcExpr.getNamelessArgument(i);
                // A date, time or timestamp variable that holds NULL is the NULL of its type, not the
                // bare word: MONTHS_BETWEEN over one answers NULL, where over a bare NULL it is refused
                // (live-verified).
                // A user-defined function's overload is chosen by the declared type of a variable holding NULL:
                // a text or a BOOLEAN one takes the VARCHAR overload beside a NUMBER one (live-verified).
                final String typed = written != null ? null
                    : isUserFunctionName(funcExpr.getFunctionName()) ? declaredNullOf(argExprs.get(i))
                    : temporalNullOf(argExprs.get(i));
                callExpr.append(written != null ? written : typed != null ? typed : "NULL");
            } else if (av instanceof Number) {
                callExpr.append(numberLiteral((Number) av));
            } else if (av instanceof Boolean) {
                // A boolean value is a valid unquoted SQL literal.
                callExpr.append(av);
            } else if (av instanceof VariantValue) {
                // A semi-structured value re-enters the rebuilt SELECT as the SQL that makes it again, keeping
                // its variant-ness (a bare string literal would be rejected by the strict semi-structured
                // functions, exactly as Snowflake rejects one), its numbers' families and its members' types.
                callExpr.append(VariantSqlText.of((VariantValue) av));
            } else if (av instanceof BinaryValue) {
                callExpr.append("X'").append(((BinaryValue) av).toHex()).append("'");
            } else if (SharedFunctionHelpers.temporalSqlLiteral(av) != null) {
                // A temporal re-enters the rebuilt SELECT as a cast literal, keeping its
                // temporal identity (a bare quoted string would arrive as VARCHAR).
                callExpr.append(SharedFunctionHelpers.temporalSqlLiteral(av));
            } else {
                // Everything else — strings, and crucially timestamp/date/time values whose
                // toString() is NOT valid unquoted SQL (e.g. a LocalDateTime prints
                // 2026-07-24T08:49:12) — is emitted as a quoted string literal so the rebuilt
                // SELECT parses. (A bare non-numeric value used to append raw and produce a
                // "SQL syntax error", e.g. OBJECT_CONSTRUCT_KEEP_NULL('t', CURRENT_TIMESTAMP()).)
                // Escape backslashes BEFORE quotes: frostlake's string lexer treats '\' as an
                // escape, so a value containing '\' (e.g. a nested error message ending in a
                // backslash, or a "\'" sequence) would otherwise escape the following char and
                // derail the whole re-parse.
                callExpr.append(SqlStringLiterals.encode(av.toString()));
            }
            // Snowflake BINDS a scripting variable's VALUE into the statement and then
            // compiles the surrounding expression, so a WRITTEN semi-structured cast still
            // applies to the bound value (live-verified: an anonymous block returning
            // ARRAY_CONTAINS(v::VARIANT, arr) over a VARCHAR variable runs, while the same
            // call without the cast errors "Invalid argument types … (VARCHAR(1), VARIANT)"
            // — reporting the BOUND value's width, which is how the binding shows). Rebuilding
            // the call from argument VALUES alone dropped the cast, so the semi-structured
            // strict families (ARRAY_CONTAINS / TYPEOF / GET / TO_JSON …) then rejected the
            // bare VARCHAR literal that substitution had created.
            final String castTarget = writtenSemiStructuredCast(
                i < argExprs.size() ? argExprs.get(i) : null);
            if (castTarget != null) {
                callExpr.append("::").append(castTarget);
            } else if (i < argExprs.size()
                    && keepsVariant(funcExpr.getFunctionName(), i, av, argExprs.get(i))) {
                if (av == null) {
                    callExpr.setLength(argumentStart);
                    callExpr.append("NULL::VARIANT");
                } else {
                    callExpr.insert(argumentStart, "TO_VARIANT(").append(')');
                }
            }
        }
        callExpr.append(")");
        return callExpr.toString();
    }

    /**
     * The SEMI-STRUCTURED target type of a call argument written as a plain {@code ::VARIANT} /
     * {@code ::OBJECT} / {@code ::ARRAY} cast, or null for anything else. Only semi-structured targets
     * are re-emitted when a call is rebuilt from its argument values: those are the casts whose effect
     * a rendered value cannot carry (a VARIANT-cast VARCHAR renders as a bare string literal, losing
     * exactly the variant-ness the strict families require), while a numeric / temporal / VARCHAR cast
     * is already reflected in the value that was produced. TRY_CAST is excluded — its source must be a
     * VARCHAR, so re-applying it to an already-converted value would be an argument-type error.
     */
    private String writtenSemiStructuredCast(final BaseExpression argExpr) {
        if (!(argExpr instanceof SqlScalarExpression)) {
            return null;
        }
        final Expression inner = ((SqlScalarExpression) argExpr).getExpression();
        if (!(inner instanceof CastExpression) || ((CastExpression) inner).isTryMode()) {
            return null;
        }
        final String target = ((CastExpression) inner).getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("VARIANT".equals(target) || "OBJECT".equals(target) || "ARRAY".equals(target)) {
            return target;
        }
        return null;
    }

    /**
     * Whether a call's argument must re-enter the rebuilt call as the VARIANT it is declared to be. SQL binds
     * a variable by its declaration and types a nested call by what its function answers, so a text read out
     * of a VARIANT — {@code GET(:v, 'k')}, a path into one, a variable declared VARIANT — is a VARIANT to the
     * call around it: {@code IS_NULL_VALUE(GET(:tags, k))} answers in a block as it does in a query. Rebuilt
     * from its value alone the argument is a bare literal, which the functions that read a VARIANT refuse.
     * Only an argument the call would refuse as that literal is kept so; any other is handed on as it was.
     *
     * @param functionName the call's function
     * @param position the argument's zero-based position
     * @param value the argument's value
     * @param argument the argument as written
     * @return whether the value is handed on as a VARIANT
     */
    private boolean keepsVariant(final String functionName, final int position, final Object value,
                                 final BaseExpression argument) {
        if (value instanceof VariantValue || queryExecutor == null) {
            return false;
        }
        final String name = functionName.toUpperCase(Locale.ROOT);
        final BuiltInFunction called = queryExecutor.getFunctionRegistry().getFunction(name);
        if (called == null) {
            return false;
        }
        final boolean readsValue = called.arrayArgumentKind(position) == ArrayArgumentKind.VALUE
            || ExpressionEvaluatorVisitor.readsVariantStrictly(name, position);
        final boolean refusedAsLiteral;
        if (position == 0 && called.takesVariantOnly()) {
            refusedAsLiteral = true;
        } else if (value instanceof String) {
            refusedAsLiteral = readsValue || called.textRejection(position) != SemiStructuredRejection.NONE;
        } else if (value instanceof BinaryValue) {
            refusedAsLiteral = readsValue || called.binaryRejection(position) != SemiStructuredRejection.NONE;
        } else if (value != null && SharedFunctionHelpers.temporalSqlLiteral(value) != null) {
            refusedAsLiteral = readsValue || called.temporalRejection(position) != SemiStructuredRejection.NONE;
        } else {
            refusedAsLiteral = false;
        }
        return refusedAsLiteral && declaresVariant(argument);
    }

    /** Whether an argument is a VARIANT by declaration: a variable's, a nested call's function's, or SQL's typing. */
    private boolean declaresVariant(final BaseExpression argument) {
        if (argument instanceof VariableExpression) {
            final String name = ((VariableExpression) argument).getName().toUpperCase(Locale.ROOT);
            return variableTypes.get(name) instanceof VariantType;
        }
        if (argument instanceof FunctionCallExpression) {
            final BuiltInFunction answering = queryExecutor.getFunctionRegistry().getFunction(
                ((FunctionCallExpression) argument).getFunctionName().toUpperCase(Locale.ROOT));
            return answering != null && answering.getReturnType() instanceof VariantType;
        }
        if (argument instanceof SqlScalarExpression) {
            try {
                return DeclarationTypes.scriptStaticType(((SqlScalarExpression) argument).getExpression(),
                    namesInScope(), queryExecutor) instanceof VariantType;
            } catch (final RuntimeException undetermined) {
                return false;
            }
        }
        return false;
    }

    // The operands are already-evaluated values; apply the operator using the SAME AST operator
    // semantics as row-expression evaluation (a single source of truth), by wrapping the values as
    // literals and evaluating an executor BinaryOperationExpression. This keeps procedural scripting
    // operators (arithmetic type-preservation, AND/OR vs ||/CONCAT) consistent with queries.
    // Evaluate a construct the procedural builder doesn't model (CASE/BETWEEN/IN/LIKE/CAST/…) through
    // the shared query-side evaluator, exposing the current procedural variables as a case-insensitive
    // resolution context so bare names resolve to scripting variables.
    private Object evaluateSqlScalar(final SqlScalarExpression expr) {
        final ExpressionEvaluator ev = new ExpressionEvaluator(
            null,
            queryExecutor != null ? queryExecutor.getFunctionRegistry() : null,
            queryExecutor != null ? queryExecutor.getCatalog() : null,
            queryExecutor);
        final Map<String, Object> varContext = new HandlerGuardedVariables(handlerLets, new VariableReadConversion() {
            @Override
            public Object read(final String name, final Object value) {
                return readHeld(name, value);
            }
        });
        varContext.putAll(scope.variables());
        ev.setResultContext(varContext);
        ev.setScriptNameTypes(namesInScope());
        // No ExpressionSource origin is set here on purpose. A subquery in this position is re-parsed
        // and run as a NESTED query, which reports against the subquery's own text; the block's uncaught
        // wrapper places that in the expression the way the account does (see ExpressionErrorPlace).
        if (queryExecutor != null && queryExecutor.getTransactionManager() != null
                && BlockExpressionSubqueries.readsQuery(expr.getExpression())) {
            // A query an expression reads is a statement the body issues, which the deepest body may not.
            CallDepth.requireMayIssue(queryExecutor.getTransactionManager().scopeDepth());
        }
        if (queryExecutor != null && queryExecutor.getTransactionManager().getAbortedTransaction() != null
                && BlockExpressionSubqueries.readsQuery(expr.getExpression())) {
            throw new RuntimeException(TransactionAbortedMessages.IGNORED);
        }
        blockExpressionDepth++;
        // An account runs a block's own expression as a query of its own, so a handler it calls keeps nothing from
        // the block's earlier evaluations: a loop's next turn starts afresh (live-verified).
        final HandlerStateScope handlerStates = StatementHandlerStates.begin();
        try {
            // The whole expression compiles before any of it runs (see BlockExpressionSubqueries).
            if (!compiledSubqueryHolders.contains(expr.getExpression())) {
                BlockExpressionSubqueries.compile(ev, expr.getExpression());
                compiledSubqueryHolders.add(expr.getExpression());
            }
            return ev.evaluate(expr.getExpression(), EMPTY_ROW);
        } finally {
            StatementHandlerStates.end(handlerStates);
            blockExpressionDepth--;
        }
    }

    /**
     * Compile a side of an AND or an OR that the other side settled, so a function it calls that does not exist
     * and a name its subqueries cannot resolve are still refused (see {@link BlockExpressionSubqueries}).
     */
    private void compileUnevaluated(final BaseExpression side) {
        if (queryExecutor == null) {
            return;
        }
        final ExpressionEvaluator ev = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
            queryExecutor.getCatalog(), queryExecutor);
        // A function the side calls must exist: IF (FALSE AND missing_fn() = 1) is Unknown function.
        BlockExpressionSubqueries.requireKnownFunctions(ev, side);
        for (final SqlScalarExpression part : BlockExpressionSubqueries.sqlPartsOf(side)) {
            BlockExpressionSubqueries.compile(ev, part.getExpression());
        }
    }

    /** Compile the subqueries of a block expression, letting the first refusal out (see BlockExpressionSubqueries). */
    private void compileSubqueriesOf(final BaseExpression expr) {
        final ExpressionEvaluator ev = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
            queryExecutor.getCatalog(), queryExecutor);
        for (final SqlScalarExpression part : BlockExpressionSubqueries.sqlPartsOf(expr)) {
            BlockExpressionSubqueries.compile(ev, part.getExpression());
        }
    }

    /**
     * Whether a variable bound now is bound into one of the block's own SQL expressions — the scalar
     * subquery of an assignment, a LET or a RETURN — rather than into a statement the block runs. Live
     * binds a NULL there as a text of no width, where a statement keeps the declared type (see
     * {@code ExpressionEvaluatorVisitor.declaredBindVariableType}).
     *
     * @return true while such an expression is evaluated
     */
    public boolean bindsIntoBlockExpression() {
        return blockExpressionDepth > 0;
    }

    /**
     * AND and OR answered by whichever operand decides them, as the query layer answers them. An AND with a
     * FALSE side is FALSE and an OR with a TRUE side is TRUE even when the other side faults, whichever side
     * that is: {@code x = 0 OR 1/x = 1} is TRUE and {@code 1/0 = 1 AND FALSE} is FALSE, while
     * {@code NULL OR 1/0 = 1} needs the faulting side and raises its fault (live-verified). A side that settles
     * the connective on its own leaves the other unevaluated, and a compilation error is never set aside.
     */
    private Object evaluateConnective(final BinaryExpression expr) {
        final Boolean decisive = expr.getOperator() == BinaryOperator.AND ? Boolean.FALSE : Boolean.TRUE;
        Boolean left = null;
        RuntimeException leftFault = null;
        try {
            left = truthOf(evaluateExpression(expr.getLeft()));
        } catch (final RuntimeException failed) {
            if (!DeferredFault.deferrable(failed)) {
                throw failed;
            }
            leftFault = failed;
        }
        if (leftFault == null && decisive.equals(left)) {
            compileUnevaluated(expr.getRight());
            return decisive;
        }
        final Boolean right;
        try {
            right = truthOf(evaluateExpression(expr.getRight()));
        } catch (final RuntimeException failed) {
            throw leftFault != null && DeferredFault.deferrable(failed) ? leftFault : failed;
        }
        if (decisive.equals(right)) {
            clearExpressionFailure();
            return decisive;
        }
        if (leftFault != null) {
            throw leftFault;
        }
        return left == null || right == null ? null : !decisive;
    }

    /** A connective side's truth, read as AND reads it: TRUE, FALSE, NULL, or the refusal of a value that is none. */
    private Boolean truthOf(final Object value) {
        return (Boolean) evaluateBinaryOperation(BinaryOperator.AND, value, Boolean.TRUE);
    }

    /** The operators that keep a DECFLOAT operand's type. */
    private static final Set<BinaryOperator> DECFLOAT_ARITHMETIC = new HashSet<BinaryOperator>(Arrays.asList(
        BinaryOperator.ADD, BinaryOperator.SUBTRACT, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE,
        BinaryOperator.MODULO));

    /**
     * Whether an operand of a block's expression is a DECFLOAT: a name declared one, a SQL expression typed one,
     * or arithmetic or a negation over one.
     */
    private boolean isDecfloatOperand(final BaseExpression operand) {
        if (operand instanceof VariableExpression) {
            return NumericType.isDecfloat(getDeclaredVariableType(((VariableExpression) operand).getName()));
        }
        if (operand instanceof SqlScalarExpression) {
            try {
                return NumericType.isDecfloat(DeclarationTypes.staticType(
                    ((SqlScalarExpression) operand).getExpression(), variableTypes, queryExecutor));
            } catch (final RuntimeException untyped) {
                return false;
            }
        }
        if (operand instanceof BinaryExpression) {
            final BinaryExpression arithmetic = (BinaryExpression) operand;
            return DECFLOAT_ARITHMETIC.contains(arithmetic.getOperator())
                && (isDecfloatOperand(arithmetic.getLeft()) || isDecfloatOperand(arithmetic.getRight()));
        }
        return operand instanceof UnaryExpression && ((UnaryExpression) operand).getOperator() == UnaryOperator.NEGATE
            && isDecfloatOperand(((UnaryExpression) operand).getOperand());
    }

    /**
     * An operation of a block's expression over a DECFLOAT, in DECFLOAT's terms: arithmetic and a comparison keep
     * 38 significant digits, and a concatenation reads the DECFLOAT as its own text — y / 3 over y DECFLOAT 2 is
     * 0.66666666666666666666666666666666666667 and y || '|' over 1/3 reads 3.3333333333333333333333333333333333333e-1|
     * (live-verified).
     */
    private Object evaluateDecfloatOperation(final BinaryExpression expr, final Object left, final Object right) {
        final boolean leftDecfloat = isDecfloatOperand(expr.getLeft());
        final boolean rightDecfloat = isDecfloatOperand(expr.getRight());
        if (expr.getOperator() == BinaryOperator.CONCAT) {
            return evaluateBinaryOperation(BinaryOperator.CONCAT, leftDecfloat ? decfloatText(left) : left,
                rightDecfloat ? decfloatText(right) : right);
        }
        final BinaryOperationExpression node = new BinaryOperationExpression(decfloatOperand(left, leftDecfloat),
            expr.getOperator(), decfloatOperand(right, rightDecfloat));
        return node.accept(new ExpressionEvaluatorVisitor(null, null, null, null));
    }

    /** A DECFLOAT's text, or the value itself when it holds no number. */
    private static Object decfloatText(final Object value) {
        return value instanceof Number ? Decfloats.text(Decfloats.of((Number) value, "TO_DECFLOAT")) : value;
    }

    /** An operand's value, typed DECFLOAT when it is one, so the operation is evaluated as DECFLOAT's. */
    private static Expression decfloatOperand(final Object value, final boolean decfloat) {
        return decfloat ? new CastExpression(astLiteral(value), "DECFLOAT") : astLiteral(value);
    }

    private Object evaluateBinaryOperation(final BinaryOperator operator,
                                           final Object left, final Object right) {
        final BinaryOperationExpression node = new BinaryOperationExpression(
            astLiteral(left), operator, astLiteral(right));
        return node.accept(new ExpressionEvaluatorVisitor(null, null, null, null));
    }

    private Object evaluateUnaryOperation(final UnaryExpression expr) {
        final UnaryOperator operator = expr.getOperator();
        if (operator == UnaryOperator.EXISTS) {
            if (expr.getOperand() instanceof SubqueryExpression) {
                return evaluateExists(((SubqueryExpression) expr.getOperand()).getSubquery());
            }
            throw new RuntimeException("EXISTS requires subquery");
        }
        // Only NOT and NEGATE remain (unary plus is folded away at build time), and both map to the
        // main expression evaluator's UnaryOperator directly.
        final Object operand = evaluateExpression(expr.getOperand());
        return new UnaryOperationExpression(operator, astLiteral(operand))
            .accept(new ExpressionEvaluatorVisitor(null, null, null, null));
    }

    // Fully qualified only here, to avoid a clash with the imported procedural.LiteralExpression.
    private static dev.frostlake.executor.expressions.LiteralExpression astLiteral(final Object value) {
        // visitLiteral returns the value regardless of declared type, so the type is a placeholder.
        return new dev.frostlake.executor.expressions.LiteralExpression(
            value, dev.frostlake.executor.expressions.LiteralType.NULL);
    }


    /**
     * Replace :varname bind-variable references in a SQL string with their current
     * procedural-scope values, quoted as string literals.
     */
    public String substituteBindVariables(final String sql) {
        return bindSubstitutor.substitute(sql);
    }

    /**
     * {@link #substituteBindVariables} for a statement the block runs, which cannot bind an infinity (see
     * {@link InfiniteBindRefusal}).
     */
    private String substituteStatementBinds(final String sql) {
        return bindSubstitutor.substitute(sql, true);
    }

    private boolean evaluateExists(final String subquery) {
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXISTS: QueryExecutor not available");
        }

        final List<ResultSet> result = issue(substituteBindVariables(subquery));
        return !result.isEmpty() && result.get(0).getRowCount() > 0;
    }

    /**
     * Scripting's truth test for a condition — IF and ELSEIF, a searched CASE's WHEN, WHILE, REPEAT's
     * UNTIL — which is a TEXT rule, not TO_BOOLEAN's: the value is true when it is the BOOLEAN true or
     * when its text is exactly {@code 1} or {@code true} in any letter case. Everything else is false
     * without an error — 'yes', 't', 'Y', 'on', ' true', '01', '1.0', 2, 0.5, -1, an unrecognised
     * word, a JSON string "true" — while a JSON true or a JSON 1 inside a VARIANT is true, and a number
     * is judged by its own spelling (a NUMBER 1 and a FLOAT 1 are '1'; a NUMBER(3,1) 1 is '1.0' and
     * false). An operator INSIDE the condition keeps its own strictness: {@code IF ('x' AND TRUE)}
     * refuses the way AND refuses everywhere.
     *
     * @param value the condition's value
     * @return whether the branch is taken
     */
    /**
     * The value a condition is judged by: its own, except that a bare decimal LITERAL is read as the
     * FLOAT the scripting runtime takes it for — {@code IF (1.0)} is taken, its text being '1', where
     * a NUMBER(3,1) variable holding 1 spells '1.0' and is not (both live-verified).
     */
    private static Object truthValue(final BaseExpression condition, final Object value) {
        if ((condition instanceof LiteralExpression || isNegatedLiteral(condition)) && value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        return value;
    }

    private boolean isTrue(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        final String text;
        if (value instanceof VariantValue) {
            text = ((VariantValue) value).text();
        } else if (value instanceof Double || value instanceof Float) {
            text = SharedFunctionHelpers.floatText(((Number) value).doubleValue());
        } else if (value instanceof BigDecimal) {
            text = ((BigDecimal) value).toPlainString();
        } else {
            text = String.valueOf(value);
        }
        return text.equals("1") || text.equalsIgnoreCase("true");
    }

    // Track which variable names were NEWLY DECLARED (not inherited) in each scope level
    public void enterScope() {
        scope.enterScope();
        typeScopes.enter(variableTypes, initialiserTypes, conversionTypes);
    }

    public void exitScope() {
        typeScopes.exit(scope.declaredInCurrentScope(), variableTypes, initialiserTypes, conversionTypes);
        scope.exitScope();
    }

    /** @see ScopeManager#enterIsolatedScope() */
    public void enterIsolatedScope() {
        scope.enterIsolatedScope();
        typeScopes.enter(variableTypes, initialiserTypes, conversionTypes);
    }

    /** Called when a variable is DECLARED (not just assigned) in the current scope. */
    public void markDeclaredInCurrentScope(final String name) {
        scope.markDeclaredInCurrentScope(name);
    }

    /**
     * Record a name's declared type. Routine PARAMETERS are declared names just as DECLARE'd
     * variables are, so their signature type governs assignment coercion and the type a RETURN of
     * that name carries.
     */
    /** The declared type recorded for a variable, or null when its declaration carried none. */
    /**
     * Whether a call's name is no built-in function's, so that the call is a user-defined function's: its
     * overloads are chosen, and its arguments judged, by the types the call hands it.
     *
     * @param name the upper-case name the call writes
     * @return whether no built-in answers to it
     */
    public boolean isUserFunctionName(final String name) {
        return queryExecutor != null && !name.startsWith("SYSTEM$")
            && queryExecutor.getFunctionRegistry().getFunction(name) == null
            && queryExecutor.getFunctionRegistry().getAggregateFunction(name) == null;
    }

    /**
     * A USING value as what it is bound as: its text, by the declared type of the variable it was read from (see
     * {@link UsingBindText}).
     *
     * @param variable the variable the value was read from, or null when it names none
     * @param value    the value
     * @return what the statement is handed
     */
    /**
     * A USING argument's value: a name hands over what it holds, a CALL's answer unconverted - {@code OPEN c USING (x)}
     * binds the text abc a DATE x was given by {@code x := (CALL pv('abc'))} (live-verified) - and any other argument
     * is evaluated.
     */
    private Object usingArgument(final BaseExpression bind) {
        if (bind instanceof VariableExpression && ((VariableExpression) bind).getName() != null) {
            final String name = ((VariableExpression) bind).getName();
            final Object held = getVariable(name);
            if (holdsCallAnswer(name, held)) {
                handlerLets.requireReadable(name, bind.getSourceLine(), bind.getSourcePosition());
                return held;
            }
        }
        return evaluateExpression(bind);
    }

    public Object usingBind(final String variable, final Object value) {
        return UsingBindText.of(value, variable == null ? null : getDeclaredVariableType(variable));
    }

    public DataType getDeclaredVariableType(final String name) {
        if (name == null) {
            return null;
        }
        final DataType declared = variableTypes.get(name.toUpperCase(Locale.ROOT));
        if (declared != null) {
            return declared;
        }
        final DataType counter = counterTypes.get(name.toUpperCase(Locale.ROOT));
        // SQLROWCOUNT is declared a text: :SQLROWCOUNT + 1 is NUMBER(19,5), and a LET from it a VARCHAR.
        return counter != null || !"SQLROWCOUNT".equalsIgnoreCase(name) ? counter : StringType.VARCHAR;
    }

    public void declareVariableType(final String name, final DataType type) {
        if (name != null && type != null) {
            variableTypes.put(name.toUpperCase(Locale.ROOT), type);
        }
    }

    public void setVariable(final String name, final Object value) {
        emptySelectTargets.remove(name.toUpperCase(Locale.ROOT));
        // A handler that assigns a name reads it from then on (see HandlerLets).
        handlerLets.written(name);
        scope.setVariable(name, value);
    }

    public Object getVariable(final String name) {
        return scope.getVariable(name);
    }

    /**
     * Gives a variable the answer of {@code x := (CALL p())}, held as the CALL handed it back. The conversion to the
     * variable's type waits for an expression that reads the variable, and fails there as an EXPRESSION_ERROR at that
     * expression: {@code DECLARE x DATE … x := (CALL pv('abc'))} assigns, {@code RETURN x} answers abc, and
     * {@code RETURN TO_VARCHAR(x)} is refused, {@code Date 'abc' is not recognized} (live-verified). A variable of a
     * semi-structured type, one holding a result set or a cursor, and one declared without a type take the answer as
     * any assignment converts it.
     *
     * @param name   the variable
     * @param answer the CALL's answer
     */
    public void assignCallAnswer(final String name, final Object answer) {
        final String key = name.toUpperCase(Locale.ROOT);
        final DataType declared = variableTypes.get(key);
        final Object current = getVariable(name);
        if (answer == null || declared == null || VariantJsonText.isSemiStructured(declared)
                || current instanceof ResultSetVariable || current instanceof ResultSet || current instanceof Cursor) {
            executeStatement(new SetStatement(name, new LiteralExpression(answer)));
            return;
        }
        initialiserTypes.remove(key);
        conversionTypes.remove(key);
        setVariable(name, answer);
        callAnswers.put(key, answer);
    }

    /**
     * The CALL's answer a name holds unconverted, which a USING argument written as the bare name binds as it is
     * (see {@link #usingArgument}); null when the name holds none.
     *
     * @param name the name a USING argument writes
     * @return the answer, or null
     */
    public Object heldCallAnswer(final String name) {
        final Object held = hasVariable(name) ? getVariable(name) : null;
        if (!holdsCallAnswer(name, held)) {
            return null;
        }
        handlerLets.requireReadable(name, 0, 0);
        return held;
    }

    /** Whether a variable holds the very answer {@link #assignCallAnswer} gave it, still unconverted. */
    private boolean holdsCallAnswer(final String name, final Object value) {
        return value != null && callAnswers.get(name.toUpperCase(Locale.ROOT)) == value;
    }

    /**
     * A variable's value as an expression reads it: a CALL's answer held unconverted converts to the variable's type
     * here, a text keeping its length - over a VARCHAR(2), {@code LENGTH(x)} of the answer abcdef is 6 - and a number
     * taking the type's scale (live-verified). Any other value is read as held.
     *
     * @param name  the variable
     * @param value the value it holds
     * @return the value the expression reads
     */
    Object readHeld(final String name, final Object value) {
        if (!holdsCallAnswer(name, value)) {
            return value;
        }
        final DataType declared = variableTypes.get(name.toUpperCase(Locale.ROOT));
        return declared instanceof StringType && value instanceof String ? value : heldUnder(value, declared);
    }

    /**
     * The value a {@code :name} bind writes into a SQL text: a CALL's answer held unconverted converts as any read of
     * it does, and a statement the block runs refuses one that does not convert as the bind it is,
     * {@code Invalid bind value (abc) for type (DATE).} (live-verified).
     *
     * @param name      the variable
     * @param value     the value it holds
     * @param statement whether the text is a statement the block runs rather than one of its own expressions
     * @return the value the bind writes
     */
    Object boundValue(final String name, final Object value, final boolean statement) {
        if (!statement || !holdsCallAnswer(name, value)) {
            return readHeld(name, value);
        }
        final DataType declared = variableTypes.get(name.toUpperCase(Locale.ROOT));
        try {
            return readHeld(name, value);
        } catch (final RuntimeException unconverted) {
            throw new RuntimeException(SqlCompilationError.of("Invalid bind value (" + value + ") for type ("
                + SqlTypeNames.canonical(declared) + ")."), unconverted);
        }
    }

    /** The CALL's answer a bare {@code RETURN x} hands back unconverted, or null where it reads no such answer. */
    private Object returnedCallAnswer(final BaseExpression expression) {
        if (!(expression instanceof VariableExpression) || ((VariableExpression) expression).isBindForm()
                || ((VariableExpression) expression).getName() == null) {
            return null;
        }
        final String name = ((VariableExpression) expression).getName();
        handlerLets.requireReadable(name, expression.getSourceLine(), expression.getSourcePosition());
        final Object value = getVariable(name);
        return holdsCallAnswer(name, value) ? value : null;
    }

    /**
     * The value a statement or an expression of the running block reads from a name, which a handler may not
     * read of a name its block's BEGIN section declared (see {@link HandlerLets}).
     *
     * @param name the name read
     * @return its value
     */
    public Object readVariable(final String name) {
        handlerLets.requireReadable(name, 0, 0);
        return scope.getVariable(name);
    }

    /**
     * The value one of the block's expressions reads from a name: {@link #readVariable}'s, a CALL's answer held
     * unconverted converting to the name's type (see {@link #readHeld}).
     *
     * @param name the name read
     * @return its value as the expression reads it
     */
    public Object readExpressionValue(final String name) {
        return readHeld(name, readVariable(name));
    }

    /**
     * The value a {@code :name} reference reads where the SQL engine resolves it: in one of the block's own
     * expressions a CALL's answer held unconverted converts as any read of it does, and in a statement the block runs
     * it is refused as the bind it is when it does not convert (see {@link #boundValue}).
     *
     * @param name the name read
     * @return its value as the reference reads it
     */
    public Object readBind(final String name) {
        return boundValue(name, getVariable(name), !bindsIntoBlockExpression());
    }

    /**
     * Refuse the read of a name the running handlers may not read (see {@link HandlerLets}).
     *
     * @param name the name read
     */
    public void requireReadableInHandler(final String name) {
        handlerLets.requireReadable(name, 0, 0);
    }

    /**
     * A handler of a block starts.
     *
     * @param beginSectionLets the names the block's BEGIN section declares with LET, upper-cased
     */
    public void enterHandler(final Set<String> beginSectionLets) {
        handlerLets.enter(beginSectionLets);
    }

    /**
     * The handler that started last runs a statement of its own.
     *
     * @param statement the statement
     */
    public void atHandlerStatement(final ParseTree statement) {
        handlerLets.at(statement);
    }

    /** The handler that started last has ended. */
    public void exitHandler() {
        handlerLets.exit();
    }

    /**
     * Take the running handlers' names out, for a body that runs as a frame of its own.
     *
     * @return what was taken out, to be handed back to {@link #restoreHandlerLets}
     */
    public HandlerLets setAsideHandlerLets() {
        return handlerLets.setAside();
    }

    /**
     * Put back what {@link #setAsideHandlerLets} took out.
     *
     * @param aside the value that call returned
     */
    public void restoreHandlerLets(final HandlerLets aside) {
        handlerLets.restore(aside);
    }

    /** True when a procedural variable with this name exists in the current scope (even null-valued). */
    public boolean hasVariable(final String name) {
        return scope.variables().containsKey(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Give a name the value it had, or take it out of the variables where it was none before: what a handler
     * binds for its own duration is no name once the handler has ended.
     *
     * @param name    the name
     * @param existed whether the name was a variable before
     * @param value   the value it had then
     */
    public void restoreVariable(final String name, final boolean existed, final Object value) {
        if (existed) {
            setVariable(name, value);
        } else {
            scope.removeVariable(name);
        }
    }

    public Map<String, Object> getAllVariables() {
        return scope.getAllVariables();
    }

    public Object getReturnValue() {
        return returnValue;
    }

    public boolean hasReturned() {
        return returnFlag;
    }

    public boolean hasBreak() {
        return breakFlag;
    }

    public boolean hasContinue() {
        return continueFlag;
    }

    public void clearReturnState() {
        this.returnFlag = false;
        this.returnValue = null;
    }

    /**
     * Put the return state back to a value captured earlier. Only a caller that RUNS A NESTED BODY on this
     * shared executor needs it — a SQL UDF invoked from an expression — so that the UDF's own RETURN can be
     * consumed without erasing a RETURN the enclosing block had already recorded.
     */
    void restoreReturnState(final boolean returned, final Object value) {
        this.returnFlag = returned;
        this.returnValue = value;
    }

    /**
     * Where the CALL of {@code x := (CALL p())} stands in the block that wrote it: the place the block gave the
     * statement when the assignment is one it re-reads from its own text, else the CALL's own first token. The
     * block's place is handed out once, to the assignment it was noted for.
     *
     * @param call the CALL's first token, in the text being read
     * @return the line and the position
     */
    public int[] placeOfAssignedCall(final Token call) {
        final int[] noted = assignedCallPlace;
        assignedCallPlace = null;
        return noted != null && noted[0] > 0 ? noted : new int[] {call.getLine(), call.getCharPositionInLine()};
    }

    /** Mark that a BEGIN…END block handler has started (nesting depth++). */
    /** Remember where a failing statement stood — the FIRST record of a propagation wins, so the
     *  innermost statement names itself and the enclosing constructs it unwinds through do not. */
    public void recordStatementFailure(final int line, final int position) {
        if (failureLine < 0 && line > 0) {
            failureLine = line;
            failurePosition = position;
        }
    }

    /**
     * Remember where a failing EXPRESSION stood. Live splits the uncaught kinds by WHAT was running
     * when the fault was raised: a fault from an expression is 'EXPRESSION_ERROR' anchored on the
     * expression's own offset — a LET initialiser, an assignment's right side, a RETURN value, an IF
     * condition — while a failing SQL statement stays 'STATEMENT_ERROR' at the statement's. The same
     * first-record-wins rule applies, so the expression (which fails first, innermost) beats the
     * statement record taken as the failure unwinds.
     */
    public void recordExpressionFailure(final int line, final int position) {
        // An enclosing expression takes the record over from the one inside it: live anchors the fault on
        // the whole expression - RETURN 1 + 1/0 at the 1, IF (NULL OR 1/0 = 1) at the NULL - never on the
        // operand that raised it. A statement's record is still never taken over.
        if (line > 0 && (failureLine < 0 || "EXPRESSION_ERROR".equals(failureKind))) {
            failureLine = line;
            failurePosition = position;
            failureKind = "EXPRESSION_ERROR";
        }
    }

    /** Forget a fault a connective set aside once its other side decided it. */
    private void clearExpressionFailure() {
        if ("EXPRESSION_ERROR".equals(failureKind)) {
            clearStatementFailure();
        }
    }

    /** Forget the last failure — a statement that COMPLETED means nothing is propagating any more. */
    public void clearStatementFailure() {
        failureLine = -1;
        failurePosition = -1;
        failureKind = "STATEMENT_ERROR";
    }

    /**
     * Put a recorded failure back: an exception a handler raises again is still the failure of the statement
     * that first raised it.
     *
     * @param line     the line recorded, or -1 for none
     * @param position its position
     * @param kind     its kind
     */
    public void restoreStatementFailure(final int line, final int position, final String kind) {
        failureLine = line;
        failurePosition = position;
        failureKind = kind;
    }

    /** The uncaught kind live names for the recorded failure — see {@link #recordExpressionFailure}. */
    public String getFailureKind() {
        return failureKind;
    }

    /** 1-based line of the statement that is failing, or -1 when none is. */
    public int getFailureLine() {
        return failureLine;
    }

    /** 0-based column of the statement that is failing. */
    public int getFailurePosition() {
        return failurePosition;
    }

    /**
     * Opens the frame of a called procedure's body. Its blocks count their nesting from here, so the body's top
     * block words an error the body lets out, and the statement failing inside it is the body's own to name: a
     * failure the caller was unwinding is set aside until the body ends.
     *
     * @return the caller's frame, to be handed back to {@link #exitProcedureFrame}
     */
    public ProcedureFrame enterProcedureFrame() {
        final ProcedureFrame caller = new ProcedureFrame(procedureFrameBase, failureLine, failurePosition,
            failureKind, setAsideHandledExceptions(), scopedBase,
            new ArrayList<Map<String, UserDefinedException>>(exceptionScopes), cursorManager.setAside(),
            handlerLets.setAside(), new ArrayList<AsyncJob>(asyncJobs));
        // The body starts no job of its caller's, and awaits none: its jobs are its own.
        asyncJobs.clear();
        // The body declares its exceptions and its cursors for itself and sees none of the caller's.
        exceptionScopes.clear();
        // Nor does a BREAK or a CONTINUE reach across a body: the caller's loops are its own.
        clearLoopControl();
        procedureFrameBase = blockDepth;
        openBodyFrames++;
        scopedBase = -1;
        clearStatementFailure();
        return caller;
    }

    /** A SQL function's block body starts to run: what it compiles, it compiles for the statement calling it. */
    public void enterFunctionBody() {
        functionBodies++;
    }

    /** The function body that started last has ended. */
    public void exitFunctionBody() {
        functionBodies--;
    }

    /**
     * Whether a SQL function's block body is running.
     *
     * @return true inside {@link #enterFunctionBody()} and {@link #exitFunctionBody()}
     */
    public boolean runsFunctionBody() {
        return functionBodies > 0;
    }

    /**
     * Whether the block running belongs to the text the session sent rather than to a body that runs as a frame of
     * its own — a called procedure's, a dynamic text's.
     *
     * @return true when no such frame is open
     */
    public boolean runsSessionText() {
        return openBodyFrames == 0;
    }

    /**
     * Marks the body whose frame was just opened as one whose CALL made it a transaction scope: the body's top
     * block then runs in that scope and opens none of its own. A text that is a block, and a block the session
     * sends, open their own (see {@link #isBodyScoped}).
     */
    public void markBodyScoped() {
        scopedBase = blockDepth;
    }

    /**
     * Whether the block about to run is the top block of a body its CALL made a transaction scope: asked before
     * the block is entered.
     *
     * @return true when the block runs in a scope that is open already
     */
    public boolean isBodyScoped() {
        return scopedBase == blockDepth;
    }

    /**
     * Sets the exceptions being handled aside for a body that runs as a frame of its own — a called procedure's,
     * a dynamic text's: it is no part of the handler that runs it, so a bare RAISE in it re-raises nothing of the
     * caller's (live-verified).
     *
     * @return the exceptions set aside, innermost first, to be handed back to {@link #restoreHandledExceptions}
     */
    public List<Exception> setAsideHandledExceptions() {
        final List<Exception> handled = new ArrayList<>(handledExceptions);
        handledExceptions.clear();
        return handled;
    }

    /**
     * Puts back the exceptions {@link #setAsideHandledExceptions()} set aside.
     *
     * @param handled the value that call returned
     */
    public void restoreHandledExceptions(final List<Exception> handled) {
        handledExceptions.clear();
        handledExceptions.addAll(handled);
    }

    /**
     * Closes the frame {@link #enterProcedureFrame()} opened. A body that failed leaves no place behind: its
     * failure is the calling statement's now, recorded as that statement unwinds.
     *
     * @param caller the value that call returned
     * @param failed whether the body let a failure out
     */
    public void exitProcedureFrame(final ProcedureFrame caller, final boolean failed) {
        procedureFrameBase = caller.getBase();
        openBodyFrames--;
        scopedBase = caller.getScopedBase();
        restoreHandledExceptions(caller.getHandled());
        exceptionScopes.clear();
        for (final Map<String, UserDefinedException> scope : caller.getExceptionScopes()) {
            exceptionScopes.addLast(scope);
        }
        cursorManager.restore(caller.getCursorScopes());
        handlerLets.restore(caller.getHandlerLets());
        // The jobs the body left unawaited end with it; the caller's are its own again.
        asyncJobs.clear();
        asyncJobs.addAll(caller.getAsyncJobs());
        clearLoopControl();
        if (failed) {
            clearStatementFailure();
        } else {
            failureLine = caller.getFailureLine();
            failurePosition = caller.getFailurePosition();
            failureKind = caller.getFailureKind();
        }
    }

    /**
     * Whether the block about to run is the top block of the body it belongs to — the session's script, a called
     * procedure's or a dynamic text's: asked before the block is entered.
     *
     * @return true when no block of the body is running yet
     */
    public boolean entersTopBlockOfBody() {
        return blockDepth == procedureFrameBase;
    }

    /**
     * Whether the block running now is the top block of the body it belongs to — the session's script, or a
     * called procedure's.
     *
     * @return true for a body's outermost block
     */
    public boolean isOutermostBlockOfBody() {
        return blockDepth == procedureFrameBase + 1;
    }

    public void enterBlock() {
        blockDepth++;
        exceptionScopes.push(new HashMap<String, UserDefinedException>());
        cursorManager.enterBlock();
        // A body that runs as a frame of its own — a called procedure's, a dynamic text's — starts with none of
        // its caller's names, these included, so every block makes sure of them.
        initializeDmlStatusVariables();
        if (blockDepth == 1) {
            blockThread = Thread.currentThread();
            judgedPasses.clear();
            judgedPassTargets.clear();
            compiledSubqueryHolders.clear();
        }
    }

    /**
     * The DML-status globals exist from the block's first statement — a read before any DML answers
     * NULL (live-verified), not an unresolved identifier. So does SQLCODE, which is NULL wherever no handler is
     * running (live-verified); SQLERRM and SQLSTATE are names inside a handler alone.
     */
    private void initializeDmlStatusVariables() {
        for (final String name : TYPED_SCRIPT_VARIABLES) {
            if (!hasVariable(name)) {
                setVariable(name, null);
            }
        }
        if (!hasVariable("SQLCODE")) {
            setVariable("SQLCODE", null);
        }
    }

    /** Mark that a BEGIN…END block handler has finished (nesting depth--). */
    public void exitBlock() {
        blockDepth--;
        if (!exceptionScopes.isEmpty()) {
            exceptionScopes.pop();
        }
        cursorManager.exitBlock();
        if (blockDepth == procedureFrameBase) {
            // The body's top block has ended: a job it never awaited never takes effect.
            asyncJobs.clear();
        }
        if (blockDepth == 0) {
            blockThread = null;
        }
    }

    /**
     * Whether the currently-executing BEGIN…END block is nested inside another one OF ITS OWN FRAME. A
     * nested block's RETURN must be left in the return state (not consumed) so the enclosing block sees
     * it and propagates it up — but a block that arrived as the TEXT of an EXECUTE IMMEDIATE is not
     * nested in the caller: its RETURN ends that block and becomes the EXECUTE IMMEDIATE's own value,
     * leaving the caller running (live-verified). {@link #enterDynamicFrame()} draws that boundary.
     */
    public boolean isNestedBlock() {
        return blockDepth > dynamicFrameBase + 1;
    }

    /**
     * Whether a BEGIN…END block of the CURRENT frame is executing — the question
     * {@link #isExecutingBlock()} answers, asked within the dynamic text's own frame rather than the
     * whole session's.
     *
     * @return true when a block opened inside this frame is running
     */
    public boolean isExecutingOwnBlock() {
        return blockDepth > dynamicFrameBase;
    }

    /**
     * Open the execution frame of an EXECUTE IMMEDIATE's dynamic text: blocks inside it count their
     * nesting from here, not from the caller's depth.
     *
     * @return the previous frame base, to be handed back to {@link #exitDynamicFrame(int)}
     */
    public int enterDynamicFrame() {
        final int previous = dynamicFrameBase;
        dynamicFrameBase = blockDepth;
        return previous;
    }

    /**
     * Close the frame {@link #enterDynamicFrame()} opened.
     *
     * @param previous the value that call returned
     */
    public void exitDynamicFrame(final int previous) {
        dynamicFrameBase = previous;
    }

    /**
     * Whether a BEGIN…END block is currently executing. Transient procedural state (cursors, user-defined
     * exceptions, declared variable types) belongs to that running block, so it must NOT be cleared while
     * one is live — see {@link #clearCursorsAndExceptions()}.
     */
    public boolean isExecutingBlock() {
        return blockDepth > 0;
    }

    /**
     * Whether a BEGIN…END block is executing on the calling thread. A statement of another session, on another
     * thread, is not inside the block: it is the outermost statement of its own thread.
     *
     * @return true when the running block was started by this thread
     */
    public boolean isExecutingBlockOnThisThread() {
        return blockDepth > 0 && blockThread == Thread.currentThread();
    }

    /**
     * Whether the statement running now is a block's own SELECT … INTO, which a control statement's body
     * runs as text: parsed again, it no longer stands inside the block's parse tree.
     *
     * @return true while that statement runs
     */
    public boolean isRunningBlockSelectInto() {
        return runningBlockSelectInto;
    }

    // Cursor operations
    /** The cursor a statement of the running block names, which a handler may not read as it may not a variable. */
    private Cursor readCursor(final String name) {
        handlerLets.requireReadable(name, 0, 0);
        return cursorManager.getCursor(name);
    }

    private void executeDeclareCursor(final DeclareCursorStatement stmt) {
        handlerLets.written(stmt.getCursorName());
        final String cursorName = stmt.getCursorName().toUpperCase(Locale.ROOT);
        // The name is the running block's: one an enclosing block declared is hidden until this block ends, and
        // a name written twice in one block was refused while the block compiled (live-verified).
        if (stmt.getResultSetVariableName() != null
                && getVariable(stmt.getResultSetVariableName()) instanceof ResultSetVariable) {
            // A cursor over a RESULTSET whose job has not answered is refused where it is declared, at the
            // RESULTSET's name (live-verified).
            try {
                requireInitialized((ResultSetVariable) getVariable(stmt.getResultSetVariableName()));
            } catch (final RuntimeException uninitialized) {
                recordStatementFailure(stmt.getResultSetLine(), stmt.getResultSetColumn());
                throw uninitialized;
            }
        }
        final Cursor cursor = new Cursor(cursorName, stmt.getSelectQuery(), stmt.getResultSetVariableName());
        cursorManager.registerCursor(cursorName, cursor);
    }

    /**
     * Whether {@code variableName} names the row variable of a FOR loop running now.
     *
     * @param variableName the variable
     * @return true for a loop's record
     */
    public boolean isLoopRecord(final String variableName) {
        return loopRecords.contains(variableName.toUpperCase(Locale.ROOT));
    }

    /**
     * The records of the FOR loops over a cursor that are running now, which a block nested in such a loop reads.
     *
     * @return the records' names, upper-cased
     */
    public Set<String> runningLoopRecords() {
        return new HashSet<>(loopRecords);
    }

    /**
     * Whether {@code variableName} names a RESULTSET: one declared so, or one a query already filled.
     *
     * @param variableName the variable
     * @return true for a RESULTSET
     */
    public boolean holdsResultSet(final String variableName) {
        final Object value = hasVariable(variableName) ? getVariable(variableName) : null;
        return value instanceof ResultSetVariable || value instanceof ResultSet;
    }

    /**
     * {@code rs := (<statement>)} for a statement that is not a query: the variable becomes a RESULTSET holding what
     * the statement answers where the assignment stands — a DML statement's counts, a SHOW or DESCRIBE listing, a
     * DDL statement's status, a CALL's result (live-verified) — and a failure is the statement's, at
     * {@code line}:{@code column}.
     *
     * @param variableName the RESULTSET variable
     * @param statementText the statement as written
     * @param line the statement's first line
     * @param column the statement's first column
     */
    public void assignResultSetQuery(final String variableName, final String statementText, final int line,
                                     final int column) {
        final ResultSetVariable assigned = new ResultSetVariable(statementText);
        assigned.setName(variableName.toUpperCase(Locale.ROOT));
        setVariable(variableName, assigned);
        runInitialiser(assigned, line, column);
    }

    /**
     * Refuse a RESULTSET that holds nothing to read: one declared and never given a statement, and one whose
     * asynchronous job has not answered — until it is awaited, and for good once its job failed (live-verified).
     */
    private static void requireInitialized(final ResultSetVariable variable) {
        final AsyncJob job = variable.getJob();
        if (variable.isEmpty() || job != null && job.getState() != AsyncJobState.DONE) {
            throw new RuntimeException("Stored procedure execution error: uninitialized resultSet '"
                + (variable.getName() != null ? variable.getName() : job == null ? "" : job.getTargetName()) + "'");
        }
    }

    /**
     * Note a failure that is placed nowhere: the account reports it {@code on line 0 at position -1}. The first
     * record of a propagation wins, as for any place.
     */
    private void recordStatementFailureNowhere() {
        if (failureLine < 0) {
            failureLine = 0;
            failurePosition = -1;
        }
    }

    /**
     * Whether a failing statement's place is recorded, the place that is none included.
     *
     * @return true when the failure the block lets out names where it stood
     */
    public boolean hasFailurePlace() {
        return failureLine >= 0;
    }

    /**
     * {@code rs := ASYNC (<statement>)}, and a RESULTSET declared with such an initialiser: the name holds a
     * RESULTSET the job fills when it is awaited. The statement binds the block's variables as they stand now.
     *
     * @param variableName  the RESULTSET
     * @param statementText the statement inside the parentheses
     * @param line          the line that statement begins on
     * @param column        that statement's first column
     */
    public void assignAsyncJob(final String variableName, final String statementText, final int line,
                               final int column) {
        final ResultSetVariable filled = new ResultSetVariable(null);
        filled.setName(variableName.toUpperCase(Locale.ROOT));
        final AsyncJob job = new AsyncJob(substituteStatementBinds(statementText), line, column, filled,
            variableName.toUpperCase(Locale.ROOT));
        filled.setJob(job);
        setVariable(variableName, filled);
        asyncJobs.add(job);
    }

    private void executeAsync(final AsyncStatement stmt) {
        asyncJobs.add(new AsyncJob(substituteStatementBinds(stmt.getJobText()), stmt.getJobLine(),
            stmt.getJobColumn(), null, null));
    }

    /**
     * AWAIT. Waiting for one RESULTSET runs its job where the AWAIT stands, so a failure is the AWAIT's, and
     * the statement counts as the job's: SQLROWCOUNT is what the job changed. Waiting for a RESULTSET a statement
     * filled where it stood, or one awaited already, does nothing; one declared and never given anything is
     * refused, and so is one whose job failed at an earlier AWAIT. AWAIT ALL runs every job not awaited yet in the
     * order they were started and stops at the first that fails, which is reported where that job's own statement
     * stands; the jobs after it stay to be awaited again (live-verified).
     */
    private void executeAwait(final AwaitStatement stmt) {
        if (stmt.getResultSetName() == null) {
            while (!asyncJobs.isEmpty()) {
                final AsyncJob job = asyncJobs.remove(0);
                try {
                    runAsyncJob(job);
                } catch (final RuntimeException failed) {
                    recordStatementFailure(job.getLine(), job.getColumn());
                    throw failed;
                }
                // Each job is a statement of its own: what it wrote is not undone when a later job fails.
                if (queryExecutor != null && queryExecutor.getTransactionManager() != null) {
                    queryExecutor.getTransactionManager().autocommitStatementEnd();
                }
            }
            // The counts are no statement's any more: SQLROWCOUNT, SQLFOUND and SQLNOTFOUND read NULL after it,
            // whatever an earlier statement left in them (live-verified).
            recordStatementWithoutResult();
            return;
        }
        final Object held = readVariable(stmt.getResultSetName());
        if (!(held instanceof ResultSetVariable)) {
            return;
        }
        final ResultSetVariable awaited = (ResultSetVariable) held;
        final AsyncJob job = awaited.getJob();
        if (job == null) {
            if (awaited.getSelectQuery() == null && awaited.getResultSet() == null) {
                throw new RuntimeException("Can not wait on an empty resultset");
            }
            return;
        }
        if (job.getState() == AsyncJobState.FAILED) {
            throw new RuntimeException("Statement is terminated.");
        }
        if (job.getState() == AsyncJobState.PENDING) {
            asyncJobs.remove(job);
            final ResultSet answer = runAsyncJob(job);
            if (answer != null) {
                recordSqlRowCount(answer);
            } else {
                recordStatementWithoutResult();
            }
        }
    }

    /** Run a job's statement and hand its answer to the RESULTSET the job fills. */
    private ResultSet runAsyncJob(final AsyncJob job) {
        final List<ResultSet> answers;
        try {
            answers = issue(job.getStatement());
        } catch (final RuntimeException failed) {
            job.setState(AsyncJobState.FAILED);
            throw failed;
        }
        job.setState(AsyncJobState.DONE);
        final ResultSet answer = answers.isEmpty() ? null : answers.get(0);
        if (job.getTarget() != null) {
            job.getTarget().setResultSet(answer);
        }
        return answer;
    }

    private void executeDeclareResultSet(final DeclareResultSetStatement stmt) {
        final String resultSetName = stmt.getResultSetName().toUpperCase(Locale.ROOT);
        if (stmt.isAsync()) {
            // The name is the running block's, as any RESULTSET's is; its job answers when it is awaited.
            markDeclaredInCurrentScope(resultSetName);
            assignAsyncJob(resultSetName, stmt.getSelectQuery(), stmt.getQueryLine(), stmt.getQueryColumn());
            return;
        }
        // Store as a special variable
        final ResultSetVariable declared = new ResultSetVariable(stmt.getSelectQuery());
        declared.setName(resultSetName);
        // The name is the running block's, as a variable's is: an enclosing block's RESULTSET of the same name
        // keeps its rows and is the name's again when this block ends (live-verified).
        markDeclaredInCurrentScope(resultSetName);
        handlerLets.written(resultSetName);
        scope.variables().put(resultSetName, declared);
        runInitialiser(declared, stmt.getQueryLine(), stmt.getQueryColumn());
    }

    /**
     * Run a RESULTSET's initialiser where it is declared or assigned, as the account does: the rows are the
     * ones the query answers THEN — a later INSERT is not in them, a cursor over the variable reads them —
     * and a query that fails fails the declaration even when nothing reads the variable, as a
     * STATEMENT_ERROR at the query (live-verified). A declaration with no query stays unbound. The statement sets
     * SQLROWCOUNT, SQLFOUND and SQLNOTFOUND as it would standing alone: a DML statement's counts, and NULL for
     * anything else (live-verified: LET r RESULTSET := (INSERT … two rows) leaves SQLROWCOUNT 2, a query or a SHOW
     * leaves it NULL).
     */
    private void runInitialiser(final ResultSetVariable variable, final int line, final int column) {
        if (variable.getSelectQuery() == null) {
            return;
        }
        final ResultSet filled;
        try {
            filled = resolveToResultSet(variable);
        } catch (final RuntimeException failed) {
            recordStatementFailure(line, column);
            throw failed;
        }
        if (filled != null) {
            recordSqlRowCount(filled);
        } else {
            recordStatementWithoutResult();
        }
    }

    private void executeOpen(final OpenStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase(Locale.ROOT);
        final Cursor cursor = readCursor(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }
        if (!cursor.isOpen()) {
            final List<Object> binds = new ArrayList<>();
            for (final BaseExpression bind : stmt.getUsingBindings()) {
                binds.add(usingBind(bind instanceof VariableExpression ? ((VariableExpression) bind).getName() : null,
                    usingArgument(bind)));
            }
            openCursorWithQuery(cursor, binds);
        }
    }

    private void executeFetch(final FetchStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase(Locale.ROOT);
        final Cursor cursor = readCursor(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }

        final Row row = cursor.fetch();
        if (row != null) {
            // Assign row values to target variables, coerced to each target's declared type
            final List<String> targetVars = stmt.getTargetVariables();
            for (final String target : targetVars) {
                initialiserTypes.remove(target.toUpperCase(Locale.ROOT));
            }
            for (int i = 0; i < Math.min(targetVars.size(), row.getValues().size()); i++) {
                setVariable(targetVars.get(i),
                    heldUnder(row.getValue(i), variableTypes.get(targetVars.get(i).toUpperCase(Locale.ROOT))));
            }
        } else {
            // End of cursor - set all variables to null
            for (final String varName : stmt.getTargetVariables()) {
                setVariable(varName, null);
            }
        }
    }

    private void executeClose(final CloseStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase(Locale.ROOT);
        final Cursor cursor = readCursor(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }
        if (!cursor.isOpen()) {
            // Closing a cursor that is not open — never opened, or closed already — is a statement error, which a
            // WHEN STATEMENT_ERROR handler catches (live-verified). A cursor a FOR loop ran to its end is still
            // open, and its first CLOSE closes it.
            throw new RuntimeException("CURSOR " + cursorName + " is not open");
        }
        cursor.close();
    }

    /** A BREAK or a CONTINUE left set belongs to no loop of the body that runs next, or that comes back. */
    public void clearLoopControl() {
        breakFlag = false;
        continueFlag = false;
        breakLabel = null;
        continueLabel = null;
    }

    /** Clear cursors, exceptions, and declared variable types from a prior invocation so
     *  re-execution doesn't fail (or coerce against a stale declaration). */
    public void clearCursorsAndExceptions() {
        cursorManager.clear();
        exceptionScopes.clear();
        clearLoopControl();
        variableTypes.clear();
        counterTypes.clear();
        initialiserTypes.clear();
        emptySelectTargets.clear();
        continueHandlers.clear();
    }

    /**
     * Coerce a value to a procedural variable's declared type, the way Snowflake casts on
     * assignment: numeric strings parse (non-numeric ones error), fractional values round half away
     * from zero into integral types (SUM(...) evaluates to 15.0 but an INTEGER variable holds 15),
     * FLOAT/DOUBLE yield doubles, and string/boolean targets convert like TO_VARCHAR / TO_BOOLEAN.
     * A null value, an untyped variable (null type), or a type this doesn't model (dates, VARIANT,
     * ARRAY, …) passes through unchanged.
     */
    /**
     * Apply a DECLARED type to a value, exactly as the {@code ::} cast would — live converts at
     * every assignment (a TZ initialiser assigned to a bare {@code timestamp} becomes the session
     * mapping's NTZ, assigned to a {@code date} it truncates, a scalar assigned to an OBJECT is
     * TO_OBJECT's own refusal and to an ARRAY it wraps), and the declared WIDTH is enforced: a
     * too-long string is refused with the truncation sentence, never silently kept.
     */
    public Object coerceToType(final Object value, final DataType type) {
        if (value == null || type == null) {
            return value;
        }
        if (type instanceof StringType) {
            // A FLOAT reads as its own text, not java.lang's ("1", not "1.0").
            final String text = value instanceof String ? (String) value
                : value instanceof Double ? SharedFunctionHelpers.floatText((Double) value) : value.toString();
            final int maxLength = ((StringType) type).getMaxLength();
            if (maxLength > 0 && CodePointText.length(text) > maxLength) {
                throw new ColumnLengthException(maxLength, text);
            }
            return text;
        }
        if (type instanceof BooleanType) {
            return toBooleanValue(value);
        }
        if (type instanceof NumericType) {
            return toNumericValue(value, (NumericType) type, false);
        }
        if (type instanceof BinaryType && value instanceof String) {
            // A text into a BINARY is read as the cast reads it, hex unless the session says otherwise, and a text
            // that is none is the cast's own refusal:
            // "The following string is not a legal hex-encoded value: 'abc'" (live-verified).
            return ValueCaster.castValue(value, "BINARY");
        }
        final String declaredName = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        if (declaredName.startsWith("TIMESTAMP") || "DATE".equals(declaredName)
                || "TIME".equals(declaredName) || "DATETIME".equals(declaredName)) {
            return ValueCaster.castValue(value, declaredName);
        }
        if (value instanceof Number || value instanceof Boolean) {
            // The scalar-into-container cells, each measured: a number assigned to a VARIANT stays
            // itself as a variant, to an ARRAY it wraps ([5]), and to an OBJECT it is TO_OBJECT's
            // own refusal — the echo says CAST, so the assignment is the cast machinery. A STRING
            // assigned to a container is left as it always was: the vendor loaders assign JSON
            // text to OBJECT variables and live accepts it, so the refusal is scalar-only.
            if ("VARIANT".equals(declaredName)) {
                // Held AS a variant, so a later conversion reads it as one: a VARIANT holding 5 under
                // RETURNS DATE fails "Failed to cast variant value 5 to DATE" (live-verified).
                return toVariant(value);
            }
            if ("ARRAY".equals(declaredName)) {
                return ValueCaster.castValue(value, declaredName);
            }
            if (type instanceof ObjectType) {
                throw new RuntimeException(SqlCompilationError.of("invalid type [CAST("
                    + castEchoLiteral(value) + " AS OBJECT)] for parameter 'TO_OBJECT'"));
            }
        }
        if (type instanceof ObjectType && value instanceof LocalDate) {
            // A DATE into an OBJECT is the same refusal, echoing the date as the call that makes one.
            throw new RuntimeException(SqlCompilationError.of("invalid type [CAST(TO_DATE('" + value
                + "') AS OBJECT)] for parameter 'TO_OBJECT'"));
        }
        return value;
    }

    /**
     * A value as a variable of the declared type holds it: the assignment conversion, and under a
     * semi-structured type the JSON of the value (see {@link JsonHeldValue}).
     */
    private Object heldUnder(final Object value, final DataType type) {
        return jsonHeld(coerceToType(value, type), type);
    }

    /**
     * The JSON a semi-structured name keeps of a value. A VARIANT takes any value so; an ARRAY and an OBJECT
     * only what already is semi-structured, their own conversions deciding the rest.
     */
    private static Object jsonHeld(final Object value, final DataType type) {
        if (value == null || !VariantJsonText.isSemiStructured(type)) {
            return value;
        }
        if (type instanceof ArrayType && (SharedFunctionHelpers.isNativeTemporal(value)
                || value instanceof BinaryValue)) {
            // A date, a time, a timestamp or a binary is the one element of the array: ["2024-01-02"].
            return JsonHeldValue.of(singleElementArray(value));
        }
        return type instanceof VariantType || value instanceof VariantValue ? JsonHeldValue.of(value) : value;
    }

    /** How a value spells inside the cast echo: numbers and booleans bare, text quoted. */
    private static String castEchoLiteral(final Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return SqlStringLiterals.encode(String.valueOf(value));
    }

    private Object toBooleanValue(final Object value) {
        if (value instanceof Boolean) {
            return value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        switch (value.toString().trim().toLowerCase(Locale.ROOT)) {
            case "true": case "t": case "yes": case "y": case "on": case "1":
                return Boolean.TRUE;
            case "false": case "f": case "no": case "n": case "off": case "0":
                return Boolean.FALSE;
            default:
                throw new RuntimeException("Boolean value '" + value + "' is not recognized");
        }
    }

    private Object toNumericValue(final Object value, final NumericType type, final boolean nullable) {
        if (NumericType.isDecfloat(type)) {
            // A DECFLOAT holds the value exactly, to 38 significant digits: its nominal scale of 0 rounds nothing
            // (x DECFLOAT := 1.5 holds 1.5, live-verified).
            return value instanceof Number ? Decfloats.of((Number) value, "TO_DECFLOAT")
                : ValueCaster.castValue(value, "DECFLOAT");
        }
        final BigDecimal parsed;
        if (value instanceof Number && NonFiniteDoubles.isNonFinite((Number) value)) {
            // NaN and the infinities have no exact form: an approximate name holds one as it is, and an
            // exact one refuses it as the cast does — LET n NUMBER := 'NaN'::FLOAT is "Number out of
            // representable range: type FIXED[SB16](38,0){not null}, value nan" (live-verified).
            return NumericType.isApproximate(type) ? Double.valueOf(((Number) value).doubleValue())
                : ValueCaster.castValue(value, "NUMBER");
        }
        if (value instanceof BigDecimal) {
            parsed = (BigDecimal) value;
        } else if (value instanceof Boolean) {
            // A BOOLEAN converts with a type of its own, NUMBER(2,0): under RETURNS NUMBER(5,2) TRUE is
            // 1 and not 1.00, the declared scale ignored (live-verified), as TRUE::NUMBER(5,1) is 1.
            if (!NumericType.isApproximate(type)) {
                return ((Boolean) value) ? 1L : 0L;
            }
            parsed = ((Boolean) value) ? BigDecimal.ONE : BigDecimal.ZERO;
        } else {
            try {
                parsed = new BigDecimal(value.toString().trim());
            } catch (final NumberFormatException e) {
                // A text spelling NaN or an infinity is one for an approximate name, as it is for the cast:
                // LET z REAL := 'inf' holds the infinity (live-verified).
                final Double nonFinite = value instanceof String && NumericType.isApproximate(type)
                    ? NonFiniteDoubles.parseForCast((String) value) : null;
                if (nonFinite != null) {
                    return nonFinite;
                }
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        }
        if (NumericType.isApproximate(type)) {
            return parsed.doubleValue();
        }
        // Round half away from zero (Snowflake's cast), then hold the value to the declared WIDTH:
        // digits that do not fit once rounded are the number's own range refusal (live-verified for
        // DECLARE, LET, := and RETURN alike — NUMBER(3,0) := 999.5 is "Number out of representable
        // range: type FIXED[SB2](3,0){not null}, value 999.5").
        final BigDecimal rounded = parsed.setScale(type.getScale(), RoundingMode.HALF_UP);
        if (type.getPrecision() > 0
                && NumericRangeRefusal.exceeds(rounded, type.getPrecision(), type.getScale())) {
            throw new RuntimeException(NumericRangeRefusal.typed(
                SignedStorageWidth.tagOfPrecision(type.getPrecision()), type.getPrecision(),
                type.getScale(), nullable, parsed));
        }
        if (type.getScale() == 0) {
            // A whole number past a long's range stays exact: NUMBER(38,0) holds 38 digits.
            return rounded.unscaledValue().bitLength() < Long.SIZE ? (Object) Long.valueOf(rounded.longValue()) : rounded;
        }
        return rounded;
    }

    /**
     * The exception names declared so far. A nested block is validated as its own block when it runs,
     * so the names an enclosing block declared have to be handed to it — and an exception is not a
     * variable, so it would otherwise vanish from that seed.
     */
    public Set<String> declaredExceptionNames() {
        final Set<String> names = new HashSet<>();
        for (final Map<String, UserDefinedException> scope : exceptionScopes) {
            names.addAll(scope.keySet());
        }
        return names;
    }

    public Set<String> saveCursorNames() {
        return cursorManager.saveCursorNames();
    }

    public void restoreCursors(final Set<String> savedNames) {
        cursorManager.restoreCursors(savedNames);
    }

    public Cursor getCursor(final String name) {
        return readCursor(name);
    }

    public void registerCursor(final String name, final Cursor cursor) {
        cursorManager.registerCursor(name, cursor);
    }

    public boolean isReturnTable() {
        return returnTableFlag;
    }

    public void resetReturnTable() {
        returnTableFlag = false;
    }

}
