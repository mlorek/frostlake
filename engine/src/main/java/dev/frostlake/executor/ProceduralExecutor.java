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
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.BinaryExpression;
import dev.frostlake.executor.procedural.CallStatement;
import dev.frostlake.executor.procedural.CaseStatement;
import dev.frostlake.executor.procedural.CloseStatement;
import dev.frostlake.executor.procedural.Cursor;
import dev.frostlake.executor.procedural.DeclareCursorStatement;
import dev.frostlake.executor.procedural.DeclareExceptionStatement;
import dev.frostlake.executor.procedural.DeclareResultSetStatement;
import dev.frostlake.executor.procedural.DeclareStatement;
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
import dev.frostlake.executor.procedural.SubqueryExpression;
import dev.frostlake.executor.procedural.UnaryExpression;
import dev.frostlake.executor.procedural.UserDefinedException;
import dev.frostlake.executor.procedural.VariableExpression;
import dev.frostlake.executor.procedural.WhenClause;
import dev.frostlake.executor.procedural.WhileStatement;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class ProceduralExecutor {

    private final ScopeManager scope = new ScopeManager();
    private final CursorManager cursorManager = new CursorManager();
    // Active CONTINUE handlers (innermost on top), pushed while a block with an EXCEPTION section runs;
    // loop executors consult the top so a matching loop-body error resumes at the next iteration.
    private final Deque<ContinueHandler> continueHandlers = new ArrayDeque<>();
    // Declared types of procedural variables (DECLARE x INTEGER / LET x INTEGER := …), consulted on
    // every assignment (:=, SELECT INTO, FETCH INTO) to coerce the value the way Snowflake casts on
    // assignment to a typed variable. Flat, not scope-aware: an inner re-declaration with a different
    // type wins for the rest of the script (acceptable shadowing edge case); cleared per top-level
    // statement together with cursors/exceptions.
    private final Map<String, DataType> variableTypes = new HashMap<>();
    /**
     * The FOR-loop counters in flight, by name. A counter binds as NUMBER(9,0) — the type a
     * {@code :i} carries into a statement and a column derived from it declares — but it is no
     * DECLARED name: a RETURN of it is still text, so it lives beside {@link #variableTypes} rather
     * than in it.
     */
    private final Map<String, DataType> counterTypes = new HashMap<>();
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
    private final Map<String, UserDefinedException> userExceptions = new HashMap<>();
    // Exceptions currently being handled (a stack for nested handlers) so a bare RAISE; can re-raise the one
    // it is handling rather than throwing a fresh generic error.
    private final Deque<Exception> handledExceptions = new ArrayDeque<>();
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
    // Where the statement that is currently failing stood, for the uncaught-exception message the
    // outermost block builds; -1 when no failure is in flight.
    private int failureLine = -1;
    private String failureKind = "STATEMENT_ERROR";
    private int failurePosition = -1;
    private QueryExecutor queryExecutor;
    private final BindVariableSubstitutor bindSubstitutor;
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
    public DeclaredReturnFrame pushDeclaredReturn(final DataType returnsType, final Set<String> parameterNames) {
        final DeclaredReturnFrame outer = declaredReturn;
        declaredReturn = new DeclaredReturnFrame(returnsType, blockDepth, compoundDepth, parameterNames);
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
        return !continueHandlers.isEmpty() && continueHandlers.peek().tryHandleAsContinue(e);
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
        try {
            dispatchStatement(stmt);
        } catch (final RuntimeException failure) {
            recordStatementFailure(stmt.getSourceLine(), stmt.getSourcePosition());
            throw failure;
        }
        clearStatementFailure();
    }

    private void dispatchStatement(final Statement stmt) {
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
        final DataType declaredType = stmt.getDeclaredType();
        if (declaredType != null) {
            variableTypes.put(stmt.getVariableName().toUpperCase(), declaredType);
        }
        try {
            setVariable(stmt.getVariableName(), coerceToType(stmt.getDefaultValue(), declaredType));
        } catch (final RuntimeException failed) {
            // A coercion fault is an EXPRESSION fault, anchored on the initialiser (live).
            if (stmt.getInitializerLine() > 0) {
                recordExpressionFailure(stmt.getInitializerLine(), stmt.getInitializerPosition());
            }
            throw failed;
        }
    }

    private void executeSet(final SetStatement stmt) {
        // A RESULTSET-typed variable (DECLARE rs RESULTSET) assigned a parenthesized query stores the
        // QUERY, executed lazily on use — rs := (SELECT …), like DECLARE … DEFAULT (…). The plain scalar
        // path would evaluate (SELECT …) to its first cell and clobber the RESULTSET.
        final Object current = getVariable(stmt.getVariableName());
        if ((current instanceof ResultSetVariable || current instanceof ResultSet)
                && isParenthesizedQuery(stmt.getRawExpressionText())) {
            setVariable(stmt.getVariableName(),
                new ResultSetVariable(stripOuterParens(stmt.getRawExpressionText())));
            return;
        }
        Object value = evaluateExpression(stmt.getExpression());
        if (stmt.isDeclaration()) {
            // A LET DECLARES, so it binds in the scope it stands in: an IF branch, a CASE branch or a
            // loop body that writes one leaves the outer variable of that name untouched, and the name
            // itself is gone once the branch ends. A plain assignment reaches outward, as it should
            // (live-verified in both directions).
            markDeclaredInCurrentScope(stmt.getVariableName());
            // A LET inside a compound body declares the name's type — the one written, or the one its
            // initialiser implies — before the value is held to it.
            DataType declared = stmt.getDeclaredType() != null
                ? stmt.getDeclaredType() : inferUntypedDeclarationType(stmt.getExpression());
            if (declared == null) {
                declared = typeOfSqlInitialiser(stmt.getSqlInitialiser());
            }
            if (declared != null) {
                variableTypes.put(stmt.getVariableName().toUpperCase(), declared);
            }
        }
        try {
            value = coerceToType(value, variableTypes.get(stmt.getVariableName().toUpperCase()));
        } catch (final RuntimeException failed) {
            // A coercion fault is an EXPRESSION fault, anchored on the right side (live).
            final BaseExpression rhs = stmt.getExpression();
            if (rhs != null && rhs.getSourceLine() > 0) {
                recordExpressionFailure(rhs.getSourceLine(), rhs.getSourcePosition());
            }
            throw failed;
        }
        setVariable(stmt.getVariableName(), value);
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
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(t.substring(1, t.length() - 1)));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final int firstTokenType = tokens.LT(1).getType();
        return firstTokenType == FrostlakeLexer.SELECT || firstTokenType == FrostlakeLexer.WITH;
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
        final Cursor cursor = iterableName != null ? cursorManager.cursors().get(iterableName.toUpperCase()) : null;

        if (cursor != null) {
            // FOR rec IN cursorName DO — implicit open, iterate, close
            if (!cursor.isOpen()) {
                openCursorWithQuery(cursor);
            }
            final String varName = stmt.getVariableName();
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
                if (cursor.isOpen()) cursor.close();
                clearRowVariables(varName, cursor.getResultSet());
            }
            return;
        }

        // FOR rec IN <resultset> DO — iterate a RESULTSET variable / ResultSet, binding rec.COLUMN.
        final Object iterableValue = evaluateExpression(stmt.getIterable());
        final ResultSet rsIterable = resolveToResultSet(iterableValue);
        if (rsIterable != null) {
            final String varName = stmt.getVariableName();
            try {
                for (final Row row : rsIterable.getRows()) {
                    setRowVariables(varName, row, rsIterable);
                    executeLoopBody(stmt.getBlock());
                    if (!loopProceeds(stmt.getLabel())) {
                        break;
                    }
                }
            } finally {
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
            for (final JsonNode element : arrayIterable) {
                setVariable(stmt.getVariableName(), ArrayFunctionHelper.fromNode(element));
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
        final String key = varName.toUpperCase();
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
        String sql = substituteBindVariables(cursor.getSelectQuery());
        // OPEN … USING (…) binds the values positionally to the cursor query's `?` placeholders.
        if (!positionalBinds.isEmpty()) {
            sql = JdbcMarshaling.substitutePlaceholders(sql, positionalBinds);
        }
        final List<ResultSet> result = queryExecutor.execute(sql);
        final ResultSet rs = (result != null && !result.isEmpty()) ? result.get(0) : new ResultSet(new ArrayList<>());
        cursor.open(rs);
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
        for (int i = 0; i < rs.getColumns().size(); i++) {
            final String colName = rs.getColumns().get(i).getName();
            final Object val = i < row.getValues().size() ? row.getValue(i) : null;
            scope.variables().put((varName + "." + colName).toUpperCase(), val);
            scope.variables().put((varName + "." + colName), val);
        }
    }

    /** Remove rec.COL variables after the FOR loop. */
    private void clearRowVariables(final String varName, final ResultSet rs) {
        if (rs == null) return;
        for (final ResultSetColumn col : rs.getColumns()) {
            scope.variables().remove((varName + "." + col.getName()).toUpperCase());
            scope.variables().remove((varName + "." + col.getName()));
        }
    }

    private void executeReturn(final ReturnStatement stmt) {
        final BaseExpression expression = stmt.getExpression();
        returnedDeclaredType = null;
        returnedStaticType = null;
        if (expression != null) {
            final Object value = evaluateExpression(expression);
            if (returnsAsText(expression)) {
                returnValue = asReturnedText(value);
            } else if (takesDeclaredReturnType(expression, value)) {
                returnValue = convertedReturn(value, expression);
                returnedDeclaredType = declaredReturn.getType();
            } else {
                returnValue = value;
                returnedStaticType = staticTypeOfReturn(stmt, value);
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
        if (expression instanceof VariableExpression) {
            final String name = ((VariableExpression) expression).getName();
            return name == null ? null : getDeclaredVariableType(name);
        }
        return typeOfReturnedExpression(stmt.getSqlExpression());
    }

    /** A RETURN's value typed as SQL over the declared names and FOR counters in scope, or null when undetermined. */
    private DataType typeOfReturnedExpression(final Expression sqlExpression) {
        if (sqlExpression == null) {
            return null;
        }
        final Map<String, DataType> typesInScope = new HashMap<String, DataType>(counterTypes);
        typesInScope.putAll(variableTypes);
        try {
            return DeclarationTypes.staticType(sqlExpression, typesInScope, queryExecutor);
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
            return name != null && variableTypes.containsKey(name.toUpperCase());
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
                return singleElementArray(source);
            }
            // A whole number under a TIMESTAMP is a seconds epoch, as TO_TIMESTAMP reads one
            // (live: RETURN 1631711999 under TIMESTAMP_NTZ is 2021-09-15 13:19:59).
            if (source instanceof Number && !(source instanceof Double) && targetName.startsWith("TIMESTAMP")) {
                source = SharedFunctionHelpers.epochToLocalDateTime(((Number) source).longValue());
            }
            return coerceToType(source, target);
        } catch (final RuntimeException failed) {
            if (expression.getSourceLine() > 0) {
                recordExpressionFailure(expression.getSourceLine(), expression.getSourcePosition());
            }
            throw failed;
        }
    }

    /**
     * A CALL argument under its parameter's declared type. A NUMBER argument is read at the declared
     * scale (live: 1.7777 into a NUMBER(5,2) parameter is 1.78, and SYSTEM$TYPEOF of the parameter is
     * NUMBER(5,2)) and a text into a DATE / TIME / TIMESTAMP parameter is read as one; a VARCHAR
     * parameter's width is NOT enforced ('abcdef' into a VARCHAR(2) parameter answers abcdef on the
     * account), so a text — and a container — passes untouched.
     */
    public Object coerceArgument(final Object value, final DataType type) {
        if (type == null) {
            return value;
        }
        final String name = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        final boolean temporal = name.startsWith("TIMESTAMP") || "DATE".equals(name)
            || "TIME".equals(name) || "DATETIME".equals(name);
        return type instanceof NumericType || temporal ? coerceToType(value, type) : value;
    }

    /**
     * The type an UNTYPED declaration takes from its initialiser (live-verified): an integer literal
     * makes a NUMBER(38,0), a decimal literal a FLOAT, and a NAME hands over its own type when that is
     * a whole-number NUMBER — a FOR counter's NUMBER(9,0) — or FLOAT when it carries a scale (a
     * NUMBER(10,4) holding 1.7777 makes a FLOAT). Any other initialiser is typed as a SQL expression
     * by {@link #typeOfSqlInitialiser}.
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
            if (declared instanceof NumericType) {
                final NumericType numeric = (NumericType) declared;
                return numeric.getScale() == 0 && !"FLOAT".equalsIgnoreCase(numeric.getName())
                    ? declared : NumericType.FLOAT;
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
        final String canonical = name.toUpperCase();
        if (variable.isBindForm() && counterTypes.containsKey(canonical)) {
            // Bound as :i, a FOR counter carries its NUMBER(9,0): only the bare name is text.
            return false;
        }
        return !TYPED_SCRIPT_VARIABLES.contains(canonical) && !variableTypes.containsKey(canonical);
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
            if (rsv.getResultSet() == null && rsv.getSelectQuery() != null && queryExecutor != null) {
                final List<ResultSet> r = queryExecutor.execute(substituteBindVariables(rsv.getSelectQuery()));
                rsv.setResultSet(r.isEmpty() ? null : r.get(0));
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
        final Cursor cursor = cursorManager.cursors().get(cursorName.toUpperCase());
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
            final List<ResultSet> r = queryExecutor.execute(substituteBindVariables(stmt.getQuery()));
            returnValue = r.isEmpty() ? null : r.get(0);
            returnTableFlag = true;
        } else if (stmt.getExpression() != null) {
            final Object value = evaluateExpression(stmt.getExpression());
            final ResultSet rs = resolveToResultSet(value);
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
            for (final ResultSet result : queryExecutor.execute(substituteBindVariables(stmt.getSql()))) {
                recordSqlRowCount(result);
                if (result != null) {
                    sawResult = true;
                }
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

    /** The DML-status effect of a completed statement whose result never surfaced as a result set. */
    public void recordStatementWithoutResult() {
        setVariable("SQLROWCOUNT", null);
        setVariable("SQLFOUND", null);
        setVariable("SQLNOTFOUND", null);
        setVariable("ACTIVITY_COUNT", 1L);
    }

    private void executeCall(final CallStatement stmt) {
        if (queryExecutor == null) {
            throw new RuntimeException("QueryExecutor not available to CALL procedure: " + stmt.getProcedureName());
        }
        // Evaluate each argument in the current procedural scope (so loop counters and DECLAREd
        // variables resolve to their live values, written :name), then re-issue the CALL as a SQL
        // statement. That routes through the visitor's CALL handler, reusing the full multi-language
        // procedure dispatch (SQL / JavaScript / Python / Java / Scala). The re-entrant execute() runs at
        // depth > 0, so this block's live cursors are preserved. A bare CALL statement discards the
        // procedure's return value (Snowflake scripting semantics).
        final List<String> argNames = stmt.getArgumentNames();
        final StringBuilder call = new StringBuilder("CALL ").append(stmt.getProcedureName()).append('(');
        for (int i = 0; i < stmt.getArguments().size(); i++) {
            if (i > 0) {
                call.append(", ");
            }
            final String argName = argNames != null ? argNames.get(i) : null;
            if (argName != null) {
                // Preserve named-argument binding when re-issuing the CALL as SQL.
                call.append(argName).append(" => ");
            }
            final BaseExpression arg = stmt.getArguments().get(i);
            rejectBareScriptingName(arg);
            call.append(toSqlLiteral(evaluateExpression(arg)));
        }
        call.append(')');
        queryExecutor.execute(call.toString());
        // A CALL is a statement too — commit its implicit transaction like any other (Snowflake).
        queryExecutor.getTransactionManager().autocommitStatementEnd();
    }

    /**
     * Snowflake dispatches CALL as a SQL statement, so a scripting name in an argument must be written
     * {@code :name}; a bare one is an identifier and fails with {@code invalid identifier '<NAME>'}
     * (live-verified: {@code LET v VARCHAR := 'x'; CALL log_it(v);} errors, {@code CALL log_it(:v)} works).
     * A name that is not a declared scripting variable at all reaches this too, so the check is on the
     * WRITTEN form rather than on whether the variable exists.
     */
    private void rejectBareScriptingName(final BaseExpression arg) {
        if (arg instanceof VariableExpression && !((VariableExpression) arg).isBindForm()) {
            throw new RuntimeException(
                "invalid identifier '" + ((VariableExpression) arg).getName().toUpperCase() + "'");
        }
    }

    /** Render an evaluated CALL argument as a SQL literal: NULL and numeric/boolean values unquoted,
     *  semi-structured values as PARSE_JSON of their text, BINARY as a hex literal, anything else as
     *  a quoted string with embedded quotes escaped. */
    private String toSqlLiteral(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof VariantValue) {
            return "PARSE_JSON(" + SqlStringLiterals.encode(((VariantValue) value).text()) + ")";
        }
        if (value instanceof BinaryValue) {
            return "X'" + ((BinaryValue) value).toHex() + "'";
        }
        final String temporal = SharedFunctionHelpers.temporalSqlLiteral(value);
        if (temporal != null) {
            return temporal;
        }
        return SqlStringLiterals.encode(value.toString());
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
        if (stmt.isUserDefinedException()) {
            final String exceptionName = stmt.getExceptionName().toUpperCase();
            final UserDefinedException exception = userExceptions.get(exceptionName);
            if (exception == null) {
                throw new RuntimeException("Undefined exception: " + exceptionName);
            }
            final ProceduralException raised =
                new ProceduralException(exception.getErrorCode(), exception.getMessage(), exceptionName);
            raised.setSourcePosition(stmt.getSourceLine(), stmt.getSourcePosition());
            throw raised;
        } else {
            final String message = evaluateExpression(stmt.getMessage()).toString();
            throw new ProceduralException(message);
        }
    }

    private void executeDeclareException(final DeclareExceptionStatement stmt) {
        final String name = stmt.getExceptionName().toUpperCase();
        final UserDefinedException exception = new UserDefinedException(name, stmt.getErrorCode(), stmt.getMessage());
        userExceptions.put(name, exception);
    }

    public boolean hasException(final String name) {
        return userExceptions.containsKey(name.toUpperCase());
    }

    public Object evaluateExpression(final BaseExpression expr) {
        try {
            return evaluateBuiltExpression(expr);
        } catch (final RuntimeException failed) {
            // Live splits the uncaught kinds by what was RUNNING: a fault raised evaluating an
            // expression is EXPRESSION_ERROR at the expression's own offset. The record is
            // first-wins, so this innermost one beats the statement record taken on the way out.
            if (expr != null && expr.getSourceLine() > 0) {
                recordExpressionFailure(expr.getSourceLine(), expr.getSourcePosition());
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
            final String varName = ((SessionVarRefExpression) expr).getName().toUpperCase();
            // First check local procedural scope, then fall back to session variables
            final Object localVal = scope.variables().get(varName);
            if (localVal != null) return localVal;
            if (queryExecutor != null) {
                final SecurityManager sm = queryExecutor.getSecurityManager();
                if (sm != null && sm.getSessionContext() != null) {
                    return sm.getSessionContext().getSessionVariable(varName);
                }
                return queryExecutor.getSessionVariables().get(varName);
            }
            return null;
        } else if (expr instanceof VariableExpression) {
            return getVariable(((VariableExpression) expr).getName());
        } else if (expr instanceof BinaryExpression) {
            final BinaryExpression binExpr = (BinaryExpression) expr;
            final Object left = evaluateExpression(binExpr.getLeft());
            final Object right = evaluateExpression(binExpr.getRight());
            return evaluateBinaryOperation(binExpr.getOperator(), left, right);
        } else if (expr instanceof ExecuteImmediateExpression) {
            final ExecuteImmediateExpression execExpr = (ExecuteImmediateExpression) expr;
            final Object sqlValue = evaluateExpression(execExpr.getSqlExpression());
            String sqlText = sqlValue != null ? sqlValue.toString() : "";

            // Bind USING (...) values positionally to the ? placeholders (Snowflake style).
            final List<BaseExpression> binds = execExpr.getUsingBindings();
            if (binds != null && !binds.isEmpty()) {
                final List<Object> bindValues = new ArrayList<>();
                for (final BaseExpression bind : binds) {
                    bindValues.add(evaluateExpression(bind));
                }
                sqlText = JdbcMarshaling.substitutePlaceholders(sqlText, bindValues);
            }

            if (queryExecutor == null) {
                throw new RuntimeException("QueryExecutor not available for EXECUTE IMMEDIATE");
            }
            final List<ResultSet> result = queryExecutor.execute(sqlText);
            return result.isEmpty() ? result : result.get(0);
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
                    // Build argument list
                    final List<Object> argValues = new ArrayList<>();
                    for (final BaseExpression arg : funcExpr.getArguments()) {
                        argValues.add(evaluateExpression(arg));
                    }
                    // Delegate to QueryExecutor expression evaluation via re-building the SQL
                    final StringBuilder callExpr = new StringBuilder(funcExpr.getFunctionName()).append("(");
                    final List<BaseExpression> argExprs = funcExpr.getArguments();
                    for (int i = 0; i < argValues.size(); i++) {
                        if (i > 0) callExpr.append(", ");
                        final Object av = argValues.get(i);
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
                        if (av == null) {
                            callExpr.append("NULL");
                        } else if (av instanceof Number || av instanceof Boolean) {
                            // Numeric / boolean values are valid unquoted SQL literals.
                            callExpr.append(av);
                        } else if (av instanceof VariantValue) {
                            // A semi-structured value re-enters the rebuilt SELECT as PARSE_JSON of its
                            // text, keeping its variant-ness (a bare string literal would be rejected by
                            // the strict semi-structured functions, exactly as Snowflake rejects one).
                            callExpr.append("PARSE_JSON(")
                                .append(SqlStringLiterals.encode(((VariantValue) av).text()))
                                .append(")");
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
                        }
                    }
                    callExpr.append(")");
                    // Execute as SELECT expression
                    final List<ResultSet> results = queryExecutor.execute("SELECT " + callExpr);
                    if (!results.isEmpty()) {
                        final ResultSet rset = results.get(0);
                        if (rset.getRowCount() > 0) return rset.getRows().get(0).getValue(0);
                    }
                } catch (final Exception e) {
                    // The fault's own sentence travels bare — live's uncaught wrapper says
                    // "Numeric value 'a' is not recognized", never a Frostlake-invented
                    // "Error evaluating function TO_NUMBER:" preamble around it.
                    throw e instanceof RuntimeException ? (RuntimeException) e
                        : new RuntimeException(e.getMessage(), e);
                }
            }
            return null;
        }
        return null;
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
        final String target = ((CastExpression) inner).getTargetType().trim().toUpperCase();
        if ("VARIANT".equals(target) || "OBJECT".equals(target) || "ARRAY".equals(target)) {
            return target;
        }
        return null;
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
        final Map<String, Object> varContext = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        varContext.putAll(scope.variables());
        ev.setResultContext(varContext);
        // No ExpressionSource origin is set here on purpose. A subquery in this position is re-parsed
        // and run as a NESTED query, which sets an origin of its own relative to the subquery's text;
        // supplying the outer one produced a confidently WRONG position (13 where live says 21) rather
        // than the honest absence of one. Positioning these needs the outer offset threaded through
        // nested query execution — see the task that records the two remaining cells.
        return ev.evaluate(expr.getExpression(), EMPTY_ROW);
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

    private boolean evaluateExists(final String subquery) {
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXISTS: QueryExecutor not available");
        }

        final List<ResultSet> result = queryExecutor.execute(substituteBindVariables(subquery));
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
    }

    public void exitScope() {
        scope.exitScope();
    }

    /** @see ScopeManager#enterIsolatedScope() */
    public void enterIsolatedScope() {
        scope.enterIsolatedScope();
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
    public DataType getDeclaredVariableType(final String name) {
        if (name == null) {
            return null;
        }
        final DataType declared = variableTypes.get(name.toUpperCase());
        return declared != null ? declared : counterTypes.get(name.toUpperCase());
    }

    public void declareVariableType(final String name, final DataType type) {
        if (name != null && type != null) {
            variableTypes.put(name.toUpperCase(), type);
        }
    }

    public void setVariable(final String name, final Object value) {
        scope.setVariable(name, value);
    }

    public Object getVariable(final String name) {
        return scope.getVariable(name);
    }

    /** True when a procedural variable with this name exists in the current scope (even null-valued). */
    public boolean hasVariable(final String name) {
        return scope.variables().containsKey(name.toUpperCase());
    }

    public Map<String, Object> getAllVariables() {
        return scope.getAllVariables();
    }

    /**
     * For {@code FROM TABLE(<name>)} in procedural SQL: if {@code name} is an in-scope RESULTSET variable
     * (raw ResultSet from {@code := (EXECUTE IMMEDIATE …)} or a {@link ResultSetVariable} from DEFAULT-init),
     * return its rows (executing the DEFAULT query on first use); otherwise null. Lets the query-layer
     * TABLE() resolver surface a procedural RESULTSET as a table source.
     */
    public ResultSet lookupResultSet(final String rawName) {
        if (rawName == null) {
            return null;
        }
        final String name = rawName.trim();
        ResultSet rs = resolveToResultSet(getVariable(name));
        if (rs == null) {
            rs = resolveToResultSet(getVariable(name.toUpperCase()));
        }
        return rs;
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
        if (failureLine < 0 && line > 0) {
            failureLine = line;
            failurePosition = position;
            failureKind = "EXPRESSION_ERROR";
        }
    }

    /** Forget the last failure — a statement that COMPLETED means nothing is propagating any more. */
    public void clearStatementFailure() {
        failureLine = -1;
        failurePosition = -1;
        failureKind = "STATEMENT_ERROR";
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

    public void enterBlock() {
        blockDepth++;
        if (blockDepth == 1) {
            initializeDmlStatusVariables();
        }
    }

    /**
     * The DML-status globals exist from the block's first statement — a read before any DML answers
     * NULL (live-verified), not an unresolved identifier.
     */
    private void initializeDmlStatusVariables() {
        for (final String name : TYPED_SCRIPT_VARIABLES) {
            if (!hasVariable(name)) {
                setVariable(name, null);
            }
        }
    }

    /** Mark that a BEGIN…END block handler has finished (nesting depth--). */
    public void exitBlock() {
        blockDepth--;
    }

    /**
     * Whether the currently-executing BEGIN…END block is nested inside another one. A nested block's RETURN
     * must be left in the return state (not consumed) so the enclosing block sees it and propagates it up.
     */
    public boolean isNestedBlock() {
        return blockDepth > 1;
    }

    /**
     * Whether a BEGIN…END block is currently executing. Transient procedural state (cursors, user-defined
     * exceptions, declared variable types) belongs to that running block, so it must NOT be cleared while
     * one is live — see {@link #clearCursorsAndExceptions()}.
     */
    public boolean isExecutingBlock() {
        return blockDepth > 0;
    }

    // Cursor operations
    private void executeDeclareCursor(final DeclareCursorStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase();
        if (cursorManager.cursors().containsKey(cursorName)) {
            throw new RuntimeException("Cursor already declared: " + cursorName);
        }
        final Cursor cursor = new Cursor(cursorName, stmt.getSelectQuery(), stmt.getResultSetVariableName());
        cursorManager.cursors().put(cursorName, cursor);
    }

    private void executeDeclareResultSet(final DeclareResultSetStatement stmt) {
        final String resultSetName = stmt.getResultSetName().toUpperCase();
        // Store as a special variable
        scope.variables().put(resultSetName, new ResultSetVariable(stmt.getSelectQuery()));
    }

    private void executeOpen(final OpenStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase();
        final Cursor cursor = cursorManager.cursors().get(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }
        if (!cursor.isOpen()) {
            final List<Object> binds = new ArrayList<>();
            for (final BaseExpression bind : stmt.getUsingBindings()) {
                binds.add(evaluateExpression(bind));
            }
            openCursorWithQuery(cursor, binds);
        }
    }

    private void executeFetch(final FetchStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase();
        final Cursor cursor = cursorManager.cursors().get(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }

        final Row row = cursor.fetch();
        if (row != null) {
            // Assign row values to target variables, coerced to each target's declared type
            final List<String> targetVars = stmt.getTargetVariables();
            for (int i = 0; i < Math.min(targetVars.size(), row.getValues().size()); i++) {
                setVariable(targetVars.get(i),
                    coerceToType(row.getValue(i), variableTypes.get(targetVars.get(i).toUpperCase())));
            }
        } else {
            // End of cursor - set all variables to null
            for (final String varName : stmt.getTargetVariables()) {
                setVariable(varName, null);
            }
        }
    }

    private void executeClose(final CloseStatement stmt) {
        final String cursorName = stmt.getCursorName().toUpperCase();
        final Cursor cursor = cursorManager.cursors().get(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }
        if (!cursor.isOpen()) {
            if (!cursor.wasEverOpened()) {
                // Live raises here — closing a cursor that was NEVER opened is a statement error,
                // catchable by a WHEN STATEMENT_ERROR handler. A cursor a FOR loop already opened
                // and closed tolerates the CLOSE (measured: close-after-FOR runs clean).
                throw new RuntimeException("CURSOR " + cursorName + " is not open");
            }
            return;
        }
        cursor.close();
    }

    /** Clear cursors, exceptions, and declared variable types from a prior invocation so
     *  re-execution doesn't fail (or coerce against a stale declaration). */
    public void clearCursorsAndExceptions() {
        cursorManager.clear();
        userExceptions.clear();
        variableTypes.clear();
        counterTypes.clear();
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
            if (maxLength > 0 && text.length() > maxLength) {
                throw new ColumnLengthException(maxLength, text);
            }
            return text;
        }
        if (type instanceof BooleanType) {
            return toBooleanValue(value);
        }
        if (type instanceof NumericType) {
            return toNumericValue(value, (NumericType) type);
        }
        if (type instanceof BinaryType && value instanceof String) {
            // A text into a BINARY is read as hex, and a text that is none is the cast's own refusal:
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
        return value;
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
        switch (value.toString().trim().toLowerCase()) {
            case "true": case "t": case "yes": case "y": case "on": case "1":
                return Boolean.TRUE;
            case "false": case "f": case "no": case "n": case "off": case "0":
                return Boolean.FALSE;
            default:
                throw new RuntimeException("Boolean value '" + value + "' is not recognized");
        }
    }

    private Object toNumericValue(final Object value, final NumericType type) {
        final BigDecimal parsed;
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
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        }
        final String name = type.getName().toUpperCase();
        if (name.equals("FLOAT") || name.equals("DOUBLE") || name.equals("REAL")) {
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
                type.getScale(), false, parsed));
        }
        if (type.getScale() == 0) {
            return rounded.longValue();
        }
        return rounded;
    }

    /**
     * The exception names declared so far. A nested block is validated as its own block when it runs,
     * so the names an enclosing block declared have to be handed to it — and an exception is not a
     * variable, so it would otherwise vanish from that seed.
     */
    public Set<String> declaredExceptionNames() {
        return new HashSet<>(userExceptions.keySet());
    }

    public Set<String> saveCursorNames() {
        return cursorManager.saveCursorNames();
    }

    public void restoreCursors(final Set<String> savedNames) {
        cursorManager.restoreCursors(savedNames);
    }

    public Cursor getCursor(final String name) {
        return cursorManager.getCursor(name);
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
