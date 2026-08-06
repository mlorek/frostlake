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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.procedural.BinaryExpression;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.executor.procedural.CallStatement;
import dev.frostlake.executor.procedural.CaseStatement;
import dev.frostlake.executor.procedural.CloseStatement;
import dev.frostlake.executor.procedural.Cursor;
import dev.frostlake.executor.procedural.DeclareCursorStatement;
import dev.frostlake.executor.procedural.DeclareExceptionStatement;
import dev.frostlake.executor.procedural.DeclareResultSetStatement;
import dev.frostlake.executor.procedural.DeclareStatement;
import dev.frostlake.executor.procedural.ExecuteImmediateExpression;
import dev.frostlake.executor.procedural.BaseExpression;
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
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.CharStreams;

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
     * The one script-supplied name that is NUMERIC. Its siblings SQLCODE, SQLSTATE and SQLERRM are
     * text, so only this one keeps its number when RETURN names it directly.
     */
    private static final String NUMERIC_SCRIPT_VARIABLE = "SQLROWCOUNT";
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
    private QueryExecutor queryExecutor;
    private final BindVariableSubstitutor bindSubstitutor;
    private static final Row EMPTY_ROW = new Row(new ArrayList<>());

    public ProceduralExecutor() {
        this.bindSubstitutor = new BindVariableSubstitutor(scope.variables());
    }

    public void setQueryExecutor(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    public QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    public void executeBlock(final ProceduralBlock block) {
        enterScope();
        try {
            for (final Statement stmt : block.getStatements()) {
                executeStatement(stmt);
                if (breakFlag || continueFlag || returnFlag) {
                    break;
                }
            }
        } finally {
            exitScope();
        }
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
        try {
            executeBlock(block);
        } catch (final RuntimeException e) {
            if (!tryContinueHandler(e)) {
                throw e;
            }
        }
    }

    public void executeStatement(final Statement stmt) {
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
        setVariable(stmt.getVariableName(), coerceToType(stmt.getDefaultValue(), declaredType));
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
        value = coerceToType(value, variableTypes.get(stmt.getVariableName().toUpperCase()));
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
            Object result = evaluateExpression(condition.getCondition());
            if (isTrue(result)) {
                executeBlock(condition.getBlock());
                return;
            }
        }
        if (stmt.getElseBlock() != null) {
            executeBlock(stmt.getElseBlock());
        }
    }

    private void executeCase(final CaseStatement stmt) {
        Object switchValue = stmt.getSwitchExpression() != null
            ? evaluateExpression(stmt.getSwitchExpression())
            : null;

        for (final WhenClause when : stmt.getWhenClauses()) {
            Object whenValue = evaluateExpression(when.getCondition());
            boolean matches = switchValue != null
                ? Objects.equals(switchValue, whenValue)
                : isTrue(whenValue);

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
        while (isTrue(evaluateExpression(stmt.getCondition()))) {
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
        String iterableName = getIterableName(stmt.getIterable());
        Cursor cursor = iterableName != null ? cursorManager.cursors().get(iterableName.toUpperCase()) : null;

        if (cursor != null) {
            // FOR rec IN cursorName DO — implicit open, iterate, close
            if (!cursor.isOpen()) {
                openCursorWithQuery(cursor);
            }
            String varName = stmt.getVariableName();
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
    private void executeForRange(final ForStatement stmt) {
        final long start = toLong(evaluateExpression(stmt.getIterable()));
        final long end = toLong(evaluateExpression(stmt.getToExpression()));
        final String varName = stmt.getVariableName();
        final long from = stmt.isReverse() ? end : start;
        final long to = stmt.isReverse() ? start : end;
        final long step = stmt.isReverse() ? -1 : 1;
        for (long v = from; stmt.isReverse() ? v >= to : v <= to; v += step) {
            setVariable(varName, v);
            executeLoopBody(stmt.getBlock());
            if (!loopProceeds(stmt.getLabel())) {
                break;
            }
        }
    }

    /** REPEAT … UNTIL &lt;cond&gt; — a post-test loop: run the body, then stop once the condition is true. */
    private void executeRepeat(final RepeatStatement stmt) {
        while (true) {
            executeLoopBody(stmt.getBlock());
            if (!loopProceeds(stmt.getLabel())) {
                return;
            }
            if (isTrue(evaluateExpression(stmt.getCondition()))) {
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
            Object v = ((LiteralExpression) expr).getValue();
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
        List<ResultSet> result = queryExecutor.execute(sql);
        ResultSet rs = (result != null && !result.isEmpty()) ? result.get(0) : new ResultSet(new ArrayList<>());
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
            String colName = rs.getColumns().get(i).getName();
            Object val = i < row.getValues().size() ? row.getValue(i) : null;
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
        if (stmt.getExpression() != null) {
            final Object value = evaluateExpression(stmt.getExpression());
            returnValue = returnsAsText(stmt.getExpression()) ? asReturnedText(value) : value;
        }
        returnFlag = true;
    }

    /**
     * Whether RETURN of this expression yields TEXT. Snowflake types a routine's result from the
     * static type of the expression the EXECUTED RETURN names — a sibling RETURN elsewhere in the
     * block, another branch, a nested block or a handler that never fires does not participate, so
     * deciding here at the RETURN that runs is live's own model, not an approximation of it. A
     * literal, an arithmetic expression, a DECLAREd variable and a routine PARAMETER all keep their
     * own type; so does SQLROWCOUNT, which is numeric. A name carrying no declared type at all — a
     * FOR-loop counter — makes the result VARCHAR, as do the text-typed SQLCODE, SQLSTATE and
     * SQLERRM.
     *
     * <p>A declared RETURNS does not override this. A procedure declaring {@code RETURNS INTEGER}
     * still answers text when its RETURN names an untyped variable, so the rule belongs here rather
     * than at either caller.
     */
    private boolean returnsAsText(final BaseExpression expression) {
        if (!(expression instanceof VariableExpression)) {
            return false;
        }
        final String name = ((VariableExpression) expression).getName();
        if (name == null) {
            return false;
        }
        final String canonical = name.toUpperCase();
        return !NUMERIC_SCRIPT_VARIABLE.equals(canonical) && !variableTypes.containsKey(canonical);
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
            for (final ResultSet result : queryExecutor.execute(substituteBindVariables(stmt.getSql()))) {
                recordSqlRowCount(result);
            }
            // Snowflake runs each statement of a stored procedure in its own autocommit transaction
            // (unless an explicit BEGIN is open) — commit here exactly as the top-level entry does.
            queryExecutor.getTransactionManager().autocommitStatementEnd();
        }
    }

    /**
     * Set Snowflake's {@code SQLROWCOUNT} (rows affected by the last DML statement) when {@code result}
     * is a DML count — a result carrying a "number of rows …" (inserted / updated / deleted) column.
     * A non-DML result (a SELECT, DDL, …) leaves SQLROWCOUNT unchanged. Called after each statement in a
     * procedural block (top-level blocks execute their statements via the visitor, not {@code executeSql}).
     */
    public void recordSqlRowCount(final ResultSet result) {
        if (result == null || result.getColumns() == null || result.getRows().isEmpty()) {
            return;
        }
        long total = 0;
        boolean found = false;
        for (int i = 0; i < result.getColumns().size(); i++) {
            final String name = result.getColumns().get(i).getName();
            if (name != null && name.toLowerCase().startsWith("number of rows")) {
                final Object v = result.getRows().get(0).getValue(i);
                if (v instanceof Number) {
                    total += ((Number) v).longValue();
                    found = true;
                }
            }
        }
        if (found) {
            setVariable("SQLROWCOUNT", total);
        }
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

    /** The exception currently being handled, or null if not inside an exception handler. */
    public Exception getCurrentHandledException() {
        return handledExceptions.peek();
    }

    private void executeRaise(final RaiseStatement stmt) {
        if (stmt.isUserDefinedException()) {
            String exceptionName = stmt.getExceptionName().toUpperCase();
            UserDefinedException exception = userExceptions.get(exceptionName);
            if (exception == null) {
                throw new RuntimeException("Undefined exception: " + exceptionName);
            }
            throw new ProceduralException(exception.getErrorCode(), exception.getMessage(), exceptionName);
        } else {
            String message = evaluateExpression(stmt.getMessage()).toString();
            throw new ProceduralException(message);
        }
    }

    private void executeDeclareException(final DeclareExceptionStatement stmt) {
        String name = stmt.getExceptionName().toUpperCase();
        UserDefinedException exception = new UserDefinedException(name, stmt.getErrorCode(), stmt.getMessage());
        userExceptions.put(name, exception);
    }

    public boolean hasException(final String name) {
        return userExceptions.containsKey(name.toUpperCase());
    }

    public Object evaluateExpression(final BaseExpression expr) {
        // Simplified expression evaluation
        if (expr instanceof LiteralExpression) {
            return ((LiteralExpression) expr).getValue();
        } else if (expr instanceof SqlScalarExpression) {
            return evaluateSqlScalar((SqlScalarExpression) expr);
        } else if (expr instanceof SessionVarRefExpression) {
            String varName = ((SessionVarRefExpression) expr).getName().toUpperCase();
            // First check local procedural scope, then fall back to session variables
            Object localVal = scope.variables().get(varName);
            if (localVal != null) return localVal;
            if (queryExecutor != null) {
                SecurityManager sm = queryExecutor.getSecurityManager();
                if (sm != null && sm.getSessionContext() != null) {
                    return sm.getSessionContext().getSessionParameter(varName);
                }
                return queryExecutor.getSessionVariables().get(varName);
            }
            return null;
        } else if (expr instanceof VariableExpression) {
            return getVariable(((VariableExpression) expr).getName());
        } else if (expr instanceof BinaryExpression) {
            BinaryExpression binExpr = (BinaryExpression) expr;
            Object left = evaluateExpression(binExpr.getLeft());
            Object right = evaluateExpression(binExpr.getRight());
            return evaluateBinaryOperation(binExpr.getOperator(), left, right);
        } else if (expr instanceof ExecuteImmediateExpression) {
            ExecuteImmediateExpression execExpr = (ExecuteImmediateExpression) expr;
            Object sqlValue = evaluateExpression(execExpr.getSqlExpression());
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
            List<ResultSet> result = queryExecutor.execute(sqlText);
            return result.isEmpty() ? result : result.get(0);
        } else if (expr instanceof UnaryExpression) {
            UnaryExpression unaryExpr = (UnaryExpression) expr;
            return evaluateUnaryOperation(unaryExpr);
        } else if (expr instanceof FunctionCallExpression) {
            FunctionCallExpression funcExpr = (FunctionCallExpression) expr;
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
                    List<Object> argValues = new ArrayList<>();
                    for (final BaseExpression arg : funcExpr.getArguments()) {
                        argValues.add(evaluateExpression(arg));
                    }
                    // Delegate to QueryExecutor expression evaluation via re-building the SQL
                    StringBuilder callExpr = new StringBuilder(funcExpr.getFunctionName()).append("(");
                    final List<BaseExpression> argExprs = funcExpr.getArguments();
                    for (int i = 0; i < argValues.size(); i++) {
                        if (i > 0) callExpr.append(", ");
                        Object av = argValues.get(i);
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
                    List<ResultSet> results = queryExecutor.execute("SELECT " + callExpr);
                    if (!results.isEmpty()) {
                        ResultSet rset = results.get(0);
                        if (rset.getRowCount() > 0) return rset.getRows().get(0).getValue(0);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException("Error evaluating function " + funcExpr.getFunctionName() + ": " + e.getMessage(), e);
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

        List<ResultSet> result = queryExecutor.execute(substituteBindVariables(subquery));
        return !result.isEmpty() && result.get(0).getRowCount() > 0;
    }

    private boolean isTrue(final Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("Y")) return true;
            if (s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("N")) return false;
            return !s.isEmpty();
        }
        return true;
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
    public void enterBlock() {
        blockDepth++;
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
        String cursorName = stmt.getCursorName().toUpperCase();
        if (cursorManager.cursors().containsKey(cursorName)) {
            throw new RuntimeException("Cursor already declared: " + cursorName);
        }
        Cursor cursor = new Cursor(cursorName, stmt.getSelectQuery(), stmt.getResultSetVariableName());
        cursorManager.cursors().put(cursorName, cursor);
    }

    private void executeDeclareResultSet(final DeclareResultSetStatement stmt) {
        String resultSetName = stmt.getResultSetName().toUpperCase();
        // Store as a special variable
        scope.variables().put(resultSetName, new ResultSetVariable(stmt.getSelectQuery()));
    }

    private void executeOpen(final OpenStatement stmt) {
        String cursorName = stmt.getCursorName().toUpperCase();
        Cursor cursor = cursorManager.cursors().get(cursorName);
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
        String cursorName = stmt.getCursorName().toUpperCase();
        Cursor cursor = cursorManager.cursors().get(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }

        Row row = cursor.fetch();
        if (row != null) {
            // Assign row values to target variables, coerced to each target's declared type
            List<String> targetVars = stmt.getTargetVariables();
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
        String cursorName = stmt.getCursorName().toUpperCase();
        Cursor cursor = cursorManager.cursors().get(cursorName);
        if (cursor == null) {
            throw new RuntimeException("Cursor not declared: " + cursorName);
        }
        if (cursor.isOpen()) {
            cursor.close();
        }
    }

    /** Clear cursors, exceptions, and declared variable types from a prior invocation so
     *  re-execution doesn't fail (or coerce against a stale declaration). */
    public void clearCursorsAndExceptions() {
        cursorManager.clear();
        userExceptions.clear();
        variableTypes.clear();
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
    public Object coerceToType(final Object value, final DataType type) {
        if (value == null || type == null) {
            return value;
        }
        if (type instanceof StringType) {
            return value instanceof String ? value : value.toString();
        }
        if (type instanceof BooleanType) {
            return toBooleanValue(value);
        }
        if (type instanceof NumericType) {
            return toNumericValue(value, (NumericType) type);
        }
        return value;
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
        if (type.getScale() == 0) {
            // Integral target: round half away from zero (Snowflake integer-cast semantics).
            return parsed.setScale(0, RoundingMode.HALF_UP).longValue();
        }
        return parsed.setScale(type.getScale(), RoundingMode.HALF_UP);
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
