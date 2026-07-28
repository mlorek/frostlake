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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Evaluates expression AST using the visitor pattern
 */
public class ExpressionEvaluatorVisitor implements ExpressionVisitor<Object> {

    private final Table table;
    private Row row;
    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;
    private QueryExecutor queryExecutor;
    private Map<String, Object> lateralContext;
    private Map<String, Table> multiTableAliasToTable;
    private List<Table> multiTableAllTables;
    private Map<String, Object> resultContext;
    private SubqueryMemo subqueryMemo;
    private final UdfInvoker udfInvoker;
    private final SubqueryEvaluator subqueryEvaluator;

    // Lambda variable scopes for higher-order functions (TRANSFORM/FILTER/REDUCE): a scope is pushed while
    // evaluating a lambda body, mapping the upper-cased lambda parameter name to the current element (and
    // accumulator). Checked first in visitColumnReference so the body's references resolve to them.
    private final Deque<Map<String, Object>> lambdaScopes = new ArrayDeque<>();

    // Monotonic per-thread counter of lateral (outer) value reads. Subquery evaluation snapshots it
    // around a probe execution: a zero delta proves the subquery read no outer value, so its result is
    // independent of the outer row and may be cached (see SubqueryMemo). Instrumenting the read sites
    // (not the context map) makes detection robust to intermediate context copies and short-circuits.
    private static final ThreadLocal<long[]> LATERAL_READS = new ThreadLocal<long[]>() {
        @Override
        protected long[] initialValue() {
            return new long[1];
        }
    };

    public ExpressionEvaluatorVisitor(final Table table, final Row row, final FunctionRegistry functionRegistry, final Catalog catalog) {
        this.table = table;
        this.row = row;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
        this.udfInvoker = new UdfInvoker(catalog, functionRegistry, this);
        this.subqueryEvaluator = new SubqueryEvaluator(this);
    }

    /** Point this visitor at a new row — lets one visitor be reused across rows (see ExpressionEvaluator). */
    public void setRow(final Row row) {
        this.row = row;
    }

    public void setQueryExecutor(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    /** The current (settable) query executor; read live by extracted helpers. Package-private. */
    QueryExecutor getQueryExecutor() {
        return queryExecutor;
    }

    // Package-private live-state accessors for extracted helpers (e.g. SubqueryEvaluator). The row and
    // lateral/subquery context change per outer row, so helpers must read them live, not snapshot them.
    Table getTable() {
        return table;
    }

    Row getRow() {
        return row;
    }

    Map<String, Object> getLateralContext() {
        return lateralContext;
    }

    /** The FROM alias map (alias → table), when a multi-table/alias context was provided. Package-private. */
    Map<String, Table> getMultiTableAliasToTable() {
        return multiTableAliasToTable;
    }

    /** The FROM tables in combined-row order, when a multi-table/alias context was provided. Package-private. */
    List<Table> getMultiTableAllTables() {
        return multiTableAllTables;
    }

    SubqueryMemo getSubqueryMemo() {
        return subqueryMemo;
    }

    /** Current per-thread lateral (outer) value read count; see {@link SubqueryMemo}. */
    long lateralReadCount() {
        return LATERAL_READS.get()[0];
    }

    public void setLateralContext(final Map<String, Object> lateralContext) {
        this.lateralContext = lateralContext;
    }

    public void setSubqueryMemo(final SubqueryMemo subqueryMemo) {
        this.subqueryMemo = subqueryMemo;
    }

    /**
     * Provide a multi-table (JOIN) resolution context. When set, a (possibly qualified) column
     * reference is resolved alias-aware against these joined tables — matching the executor's JOIN
     * column resolution — before the single-table fallbacks.
     */
    public void setMultiTableContext(final Map<String, Table> aliasToTable, final List<Table> allTables) {
        this.multiTableAliasToTable = aliasToTable;
        this.multiTableAllTables = allTables;
    }

    /**
     * Provide precomputed SELECT-list outputs (for HAVING / QUALIFY), keyed by the canonical AST
     * form of each select item (via {@link AstPrinterVisitor}) and by its alias. A column reference
     * or function call whose canonical form matches resolves to the precomputed value instead of
     * being (re-)evaluated — so an aggregate or window function referenced in a HAVING/QUALIFY
     * condition uses the already-computed result rather than re-aggregating.
     */
    public void setResultContext(final Map<String, Object> resultContext) {
        this.resultContext = resultContext;
    }

    @Override
    public Object visitLiteral(final LiteralExpression expr) {
        return expr.getValue();
    }

    @Override
    public Object visitSystemStreamHasData(final SystemStreamHasDataExpression expr) {
        Object nameVal = expr.getStreamNameExpr().accept(this);
        if (nameVal == null) return false;
        String streamName = nameVal.toString().toUpperCase().replaceAll("^'|'$", "");
        if (queryExecutor == null) return false;
        try {
            // The name may be schema- or db-qualified ('BASE_TRANSFORM.STREAM_X') — resolving the whole
            // dotted text as a bare name in the current schema silently returned FALSE, so every loader
            // gated on SYSTEM$STREAM_HAS_DATA skipped its branch.
            final Stream stream = queryExecutor.getCatalog().resolveStream(streamName);
            if (stream == null) return false;
            // A stream "has data" when it has unconsumed change records — NOT when a same-named table has
            // rows (streams aren't stored as tables). Mirrors Snowflake SYSTEM$STREAM_HAS_DATA.
            return stream.getUnconsumedCount() > 0;
        } catch (final Exception e) {
            return false;
        }
    }

    @Override
    public Object visitSystemUserTaskCancel(final SystemUserTaskCancelExpression expr) {
        Object nameVal = expr.getTaskNameExpr().accept(this);
        if (nameVal == null) return "Task not found";
        String taskName = nameVal.toString().toUpperCase().replaceAll("^'|'$", "");
        if (queryExecutor == null) return "cancelled";
        try {
            Catalog cat = queryExecutor.getCatalog();
            String dbN = cat.getCurrentDatabase(), scN = cat.getCurrentSchema();
            if (dbN == null || scN == null) return "Task not found";
            Task task = cat.getDatabase(dbN).getSchema(scN).getTask(taskName);
            if (task == null) return "Task not found: " + taskName;
            // Mark task as suspended to cancel ongoing executions
            task.setState(TaskState.SUSPENDED);
            return "Task " + taskName + ": cancelled";
        } catch (final Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public Object visitSessionVar(final SessionVarExpression expr) {
        String name = expr.getVarName().toUpperCase();
        if (queryExecutor != null) {
            SecurityManager sm = queryExecutor.getSecurityManager();
            if (sm != null) {
                return sm.getSessionContext().getSessionParameter(name);
            }
            return queryExecutor.getSessionVariables().get(name);
        }
        return null;
    }

    @Override
    public Object visitBindVariable(final BindVariableExpression expr) {
        // :name refers to a Snowflake Scripting variable (or procedure parameter) of the enclosing
        // block, resolved from the procedural scope. A declared-but-null variable yields NULL; an
        // undeclared name is an error — silently treating it as NULL would hide typos.
        if (queryExecutor != null) {
            final ProceduralExecutor proceduralExecutor = queryExecutor.getProceduralExecutor();
            if (proceduralExecutor != null && proceduralExecutor.hasVariable(expr.getVarName())) {
                return proceduralExecutor.getVariable(expr.getVarName());
            }
        }
        throw new RuntimeException("Bind variable not defined: :" + expr.getVarName());
    }

    @Override
    public Object visitColumnReference(final ColumnReferenceExpression expr) {
        String columnName = expr.getColumnName();

        // Strip double-quote delimiters from quoted identifiers
        if (columnName.startsWith("\"") && columnName.endsWith("\"") && columnName.length() > 1) {
            columnName = columnName.substring(1, columnName.length() - 1);
        }

        // Lambda variable (TRANSFORM/FILTER/REDUCE): an unqualified reference matching a bound lambda
        // parameter resolves to the current element/accumulator, shadowing table columns.
        if (!lambdaScopes.isEmpty() && !expr.isQualified()) {
            final String key = columnName.toUpperCase();
            for (final Map<String, Object> scope : lambdaScopes) {
                if (scope.containsKey(key)) {
                    return scope.get(key);
                }
            }
        }

        // Snowflake Scripting exposes SQLERRM / SQLCODE / SQLSTATE as bare identifiers (no colon) inside an
        // exception handler; resolve them to the procedural variable the handler bound.
        if (!expr.isQualified() && queryExecutor != null) {
            final String upper = columnName.toUpperCase();
            if ("SQLERRM".equals(upper) || "SQLCODE".equals(upper) || "SQLSTATE".equals(upper)) {
                final ProceduralExecutor proceduralExecutor = queryExecutor.getProceduralExecutor();
                if (proceduralExecutor != null && proceduralExecutor.hasVariable(upper)) {
                    return proceduralExecutor.getVariable(upper);
                }
            }
        }

        // Translate positional parameters ($1, $2, etc.) to COLUMN1, COLUMN2, etc.
        if (columnName.startsWith("$")) {
            try {
                int position = Integer.parseInt(columnName.substring(1));
                columnName = "COLUMN" + position;
            } catch (final NumberFormatException e) {
                // Not a positional parameter, keep original name
            }
        }

        // HAVING/QUALIFY: a reference to a precomputed SELECT-list output (alias or group column)
        // resolves to its value rather than being re-evaluated.
        if (resultContext != null) {
            if (resultContext.containsKey(columnName)) {
                return resultContext.get(columnName);
            }
            final String canonical = AstPrinterVisitor.print(expr);
            if (resultContext.containsKey(canonical)) {
                return resultContext.get(canonical);
            }
        }

        // Multi-table (JOIN) resolution: resolve alias-aware against all joined tables first,
        // matching the executor's column resolution (so e.g. a.id and b.id resolve to their own
        // tables). Falls through on not-found to single-table / lateral / function handling.
        if (multiTableAllTables != null && queryExecutor != null) {
            try {
                final String refName = expr.isQualified() ? expr.getTableName() + "." + columnName : columnName;
                return queryExecutor.resolveColumnInTables(row, multiTableAllTables, multiTableAliasToTable, refName);
            } catch (final RuntimeException ignored) {
                // not a joined column — fall through
            }
        }

        // Try qualified name first if present
        if (expr.isQualified() && table != null) {
            // Try exact match with qualified name
            String qualifiedName = expr.getTableName() + "." + columnName;
            if (table.hasColumn(qualifiedName)) {
                int index = table.getColumnIndex(qualifiedName);
                return row.getValue(index);
            }

            // A qualified reference whose qualifier names an outer (correlated/lateral) table is
            // resolved via the lateral context by its qualified name — before the bare-name
            // fallback below, which could otherwise shadow it with a same-named local column.
            if (lateralContext != null) {
                if (lateralContext.containsKey(qualifiedName)) {
                    LATERAL_READS.get()[0]++;
                    return lateralContext.get(qualifiedName);
                }
                if (lateralContext.containsKey(qualifiedName.toUpperCase())) {
                    LATERAL_READS.get()[0]++;
                    return lateralContext.get(qualifiedName.toUpperCase());
                }
            }

            // Try stripping the table alias and match column name only
            if (table.hasColumn(columnName)) {
                int index = table.getColumnIndex(columnName);
                return row.getValue(index);
            }

            // Try case-insensitive
            for (final TableColumn col : table.getColumns()) {
                if (col.getName().equalsIgnoreCase(columnName)) {
                    int index = table.getColumnIndex(col.getName());
                    return row.getValue(index);
                }
            }
        }

        // Unqualified column name
        if (table != null && table.hasColumn(columnName)) {
            int index = table.getColumnIndex(columnName);
            return row.getValue(index);
        }

        // Try case-insensitive
        if (table != null) {
            for (final TableColumn col : table.getColumns()) {
                if (col.getName().equalsIgnoreCase(columnName)) {
                    int index = table.getColumnIndex(col.getName());
                    return row.getValue(index);
                }
            }
        }

        // Check lateral context
        if (lateralContext != null) {
            if (lateralContext.containsKey(columnName)) {
                LATERAL_READS.get()[0]++;
                return lateralContext.get(columnName);
            }
            if (lateralContext.containsKey(columnName.toUpperCase())) {
                LATERAL_READS.get()[0]++;
                return lateralContext.get(columnName.toUpperCase());
            }
        }

        // Resolve known zero-arg functions used without parentheses (e.g. CURRENT_TIMESTAMP in VALUES)
        if (functionRegistry != null) {
            BuiltInFunction zeroArgFn =
                functionRegistry.getFunction(columnName.toUpperCase());
            if (zeroArgFn != null && zeroArgFn.getMinArgCount() == 0) {
                return zeroArgFn.evaluate(Collections.emptyList());
            }
        }

        // Date/time unit keywords used as bareword arguments (e.g. DATEADD(HOUR, ...))
        // are parsed as column references by the grammar; treat them as their string value.
        switch (columnName.toUpperCase()) {
            case "YEAR": case "YEARS": case "QUARTER": case "MONTH": case "MONTHS":
            case "WEEK": case "WEEKS": case "DAY": case "DAYS": case "HOUR": case "HOURS":
            case "MINUTE": case "MINUTES": case "SECOND": case "SECONDS":
            case "MILLISECOND": case "MILLISECONDS": case "MICROSECOND": case "MICROSECONDS":
            case "NANOSECOND": case "NANOSECONDS":
            case "DAYOFWEEK": case "DAYOFWEEKISO": case "DAYOFYEAR": case "DAYOFMONTH":
            case "WEEKOFYEAR": case "WEEKISO": case "ISOWEEK":
            case "EPOCH": case "EPOCH_SECOND": case "EPOCH_MILLISECOND":
            case "EPOCH_MICROSECOND": case "EPOCH_NANOSECOND":
                return columnName.toUpperCase();
            default:
                break;
        }

        // Snowflake sequence pseudo-columns: <sequence>.NEXTVAL / <sequence>.CURRVAL. Resolved as a
        // last resort (after column resolution) so a real column of that name still wins; this is what
        // lets a column DEFAULT of seq.NEXTVAL work at INSERT and MERGE time.
        if (expr.isQualified()) {
            final String op = columnName.toUpperCase();
            if ("NEXTVAL".equals(op) || "CURRVAL".equals(op)) {
                final Sequence sequence = resolveSequence(expr.getTableName());
                if (sequence != null) {
                    return "NEXTVAL".equals(op) ? sequence.nextVal() : sequence.currVal();
                }
            }
        }

        throw new RuntimeException("Column not found: " + expr);
    }

    /**
     * Resolve a (possibly schema/database-qualified) sequence name for the {@code seq.NEXTVAL} /
     * {@code seq.CURRVAL} pseudo-column syntax. Returns null when it does not name a sequence, so the
     * caller falls back to the ordinary "column not found" error.
     */
    private Sequence resolveSequence(final String name) {
        final Catalog cat = catalog != null ? catalog : (queryExecutor != null ? queryExecutor.getCatalog() : null);
        if (cat == null || cat.getCurrentDatabase() == null) {
            return null;
        }
        try {
            final String[] parts = QualifiedName.parse(name).parts();
            if (parts.length == 1) {
                return cat.getDatabase(cat.getCurrentDatabase()).getSchema(cat.getCurrentSchema()).getSequence(parts[0]);
            } else if (parts.length == 2) {
                return cat.getDatabase(cat.getCurrentDatabase()).getSchema(parts[0]).getSequence(parts[1]);
            }
            return cat.getDatabase(parts[0]).getSchema(parts[1]).getSequence(parts[2]);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    @Override
    public Object visitBinaryOperation(final BinaryOperationExpression expr) {
        Object left = expr.getLeft().accept(this);
        Object right = expr.getRight().accept(this);

        switch (expr.getOperator()) {
            case ADD:
                return add(left, right);
            case SUBTRACT:
                return subtract(left, right);
            case MULTIPLY:
                return multiply(left, right);
            case DIVIDE:
                return divide(left, right);
            case MODULO:
                return modulo(left, right);
            case EQUAL:
                // NULL = anything is UNKNOWN (three-valued logic), represented as a null Boolean.
                if (left == null || right == null) return null;
                if (isCaseInsensitiveStringComparison(expr, left, right)) {
                    return ((String) left).equalsIgnoreCase((String) right);
                }
                return equals(left, right);
            case NOT_EQUAL:
                if (left == null || right == null) return null;
                if (isCaseInsensitiveStringComparison(expr, left, right)) {
                    return !((String) left).equalsIgnoreCase((String) right);
                }
                return !equals(left, right);
            case LESS_THAN:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                return compare(left, right) < 0;
            case LESS_THAN_OR_EQUAL:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                return compare(left, right) <= 0;
            case GREATER_THAN:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                return compare(left, right) > 0;
            case GREATER_THAN_OR_EQUAL:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                return compare(left, right) >= 0;
            case AND: {
                // Three-valued logic: FALSE dominates (even over UNKNOWN), then UNKNOWN, else TRUE.
                final Boolean l = booleanOrNull(left);
                final Boolean r = booleanOrNull(right);
                if (Boolean.FALSE.equals(l) || Boolean.FALSE.equals(r)) {
                    return false;
                }
                if (l == null || r == null) {
                    return null;
                }
                return true;
            }
            case OR: {
                // Three-valued logic: TRUE dominates (even over UNKNOWN), then UNKNOWN, else FALSE.
                final Boolean l = booleanOrNull(left);
                final Boolean r = booleanOrNull(right);
                if (Boolean.TRUE.equals(l) || Boolean.TRUE.equals(r)) {
                    return true;
                }
                if (l == null || r == null) {
                    return null;
                }
                return false;
            }
            case CONCAT:
                // In SQL, NULL || anything = NULL
                if (left == null || right == null) {
                    return null;
                }
                // Temporal operands render in Snowflake's default output forms (space + FF3).
                return SharedFunctionHelpers.textOf(left) + SharedFunctionHelpers.textOf(right);
            case LIKE:
            case NOT_LIKE:
            case ILIKE:
            case NOT_ILIKE:
                return evaluateLike(left, right, expr.getOperator(), resolveEscapeChar(expr.getEscape()));
            default:
                throw new RuntimeException("Unsupported binary operator: " + expr.getOperator());
        }
    }

    @Override
    public Object visitUnaryOperation(final UnaryOperationExpression expr) {
        switch (expr.getOperator()) {
            case NOT:
                Object operand = expr.getOperand().accept(this);
                // NOT UNKNOWN is UNKNOWN (three-valued logic).
                if (operand == null) return null;
                return !isTrue(operand);
            case NEGATE:
                Object negOperand = expr.getOperand().accept(this);
                // -NULL is NULL; a numeric VARCHAR is coerced, as Snowflake does in any arithmetic context
                // (a VARCHAR column such as SPLIT_TO_TABLE's VALUE is routinely negated).
                if (negOperand == null) {
                    return null;
                }
                final Number negNumber = ExpressionArithmetic.asNumber(negOperand);
                if (negNumber != null) {
                    return negate(negNumber);
                }
                throw new RuntimeException("Cannot negate non-number: " + negOperand);
            case EXISTS:
                // EXISTS is handled specially with subquery
                // Do NOT evaluate the operand first, just extract the subquery
                if (expr.getOperand() instanceof SubqueryExpression) {
                    SubqueryExpression subquery = (SubqueryExpression) expr.getOperand();
                    return evaluateExists(subquery.getSubquery());
                }
                throw new RuntimeException("EXISTS requires subquery");
            default:
                throw new RuntimeException("Unsupported unary operator: " + expr.getOperator());
        }
    }

    @Override
    public Object visitLambda(final LambdaExpression expr) {
        // A lambda is never evaluated on its own — TRANSFORM/FILTER/REDUCE apply its body per element.
        throw new RuntimeException("Lambda expression is only valid as an argument to a higher-order function");
    }

    /** Returned by {@link #evaluateConditionalFunction} for a function that is NOT short-circuiting.
     *  A private singleton, so it can never collide with a real (possibly null) function result. */
    private static final Object NOT_CONDITIONAL = new Object();

    /**
     * Evaluate a short-circuiting conditional function, or return {@link #NOT_CONDITIONAL} when
     * {@code funcName} is not one. These are the Snowflake functions defined in terms of CASE, so only
     * the branch actually selected may be evaluated: a guard like {@code IFF(c, udf(x), NULL)} must not
     * call the UDF when {@code c} is false, and {@code NVL(a, expensive(b))} must not compute the
     * fallback when {@code a} is non-NULL. Arity mismatches fall through to the normal (eager) path so
     * the function's own argument-count validation still reports the error.
     */
    private Object evaluateConditionalFunction(final String funcName, final List<Expression> args) {
        for (final Expression arg : args) {
            if (arg instanceof SpreadExpression) {
                // A ** spread changes the effective argument list; skip the lazy short-circuit and
                // let the generic path splice the arguments before dispatch.
                return NOT_CONDITIONAL;
            }
        }
        switch (funcName) {
            case "IFF":
                if (args.size() != 3) {
                    return NOT_CONDITIONAL;
                }
                return isTrue(args.get(0).accept(this)) ? args.get(1).accept(this) : args.get(2).accept(this);
            case "COALESCE":
                if (args.isEmpty()) {
                    return NOT_CONDITIONAL;
                }
                for (final Expression arg : args) {
                    final Object value = arg.accept(this);
                    if (value != null) {
                        return value;
                    }
                }
                return null;
            case "NVL":
            case "IFNULL":
                if (args.size() != 2) {
                    return NOT_CONDITIONAL;
                }
                final Object nvlValue = args.get(0).accept(this);
                return nvlValue != null ? nvlValue : args.get(1).accept(this);
            case "NVL2":
                if (args.size() != 3) {
                    return NOT_CONDITIONAL;
                }
                return args.get(0).accept(this) != null ? args.get(1).accept(this) : args.get(2).accept(this);
            default:
                return NOT_CONDITIONAL;
        }
    }

    private boolean hasLambdaArgument(final FunctionCallExpression expr) {
        for (final Expression arg : expr.getArguments()) {
            if (arg instanceof LambdaExpression) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluate a higher-order function whose last argument is a lambda: {@code TRANSFORM(<array>, <lambda>)},
     * {@code FILTER(<array>, <lambda>)}, {@code REDUCE(<array>, <initial>, <lambda>)}. The array is evaluated
     * once; the lambda body is then applied per element with its parameter(s) bound (element [, index] — or
     * accumulator, element for REDUCE). A NULL/non-array input yields NULL.
     */
    private Object evaluateHigherOrderFunction(final String funcName, final List<Expression> args) {
        final LambdaExpression lambda = (LambdaExpression) args.get(args.size() - 1);
        final ArrayNode array = ArrayFunctionHelper.parseArray(args.get(0).accept(this));
        if (array == null) {
            return null;
        }

        if ("REDUCE".equals(funcName)) {
            Object acc = args.size() >= 3 ? args.get(1).accept(this) : null;
            for (int i = 0; i < array.size(); i++) {
                acc = applyLambda(lambda, acc, ArrayFunctionHelper.fromNode(array.get(i)));
            }
            return acc;
        }

        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int i = 0; i < array.size(); i++) {
            final Object applied = applyLambda(lambda, ArrayFunctionHelper.fromNode(array.get(i)), (long) i);
            if ("FILTER".equals(funcName)) {
                if (Boolean.TRUE.equals(applied)) {
                    result.add(array.get(i));
                }
            } else { // TRANSFORM
                result.add(applied instanceof JsonNode ? (JsonNode) applied
                    : ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, applied));
            }
        }
        return result;
    }

    /** Bind the lambda's parameters to {@code values} (in order), evaluate its body, then unbind. */
    private Object applyLambda(final LambdaExpression lambda, final Object... values) {
        final Map<String, Object> scope = new HashMap<>();
        final List<String> params = lambda.getParameters();
        for (int i = 0; i < params.size() && i < values.length; i++) {
            scope.put(params.get(i).toUpperCase(), values[i]);
        }
        lambdaScopes.push(scope);
        try {
            return lambda.getBody().accept(this);
        } finally {
            lambdaScopes.pop();
        }
    }

    @Override
    public Object visitFunctionCall(final FunctionCallExpression expr) {
        String funcName = expr.getFunctionName().toUpperCase();
        if (expr.getNameExpression() != null) {
            // IDENTIFIER('fn')/IDENTIFIER($var): the actual function name comes from the inner
            // expression, resolved NOW so cached ASTs stay correct across sessions/values.
            final Object resolved = expr.getNameExpression().accept(this);
            if (resolved == null) {
                throw new RuntimeException("IDENTIFIER(...) function name resolved to NULL");
            }
            funcName = resolved.toString().toUpperCase();
        }

        // HAVING/QUALIFY: an aggregate/window function that matches a precomputed SELECT-list
        // output resolves to that value rather than being re-evaluated.
        if (resultContext != null) {
            final String canonical = AstPrinterVisitor.print(expr);
            if (resultContext.containsKey(canonical)) {
                return resultContext.get(canonical);
            }
        }

        // Higher-order functions (TRANSFORM / FILTER / REDUCE): the lambda argument must NOT be
        // pre-evaluated — it is applied per array element with the element bound to the lambda variable.
        if (("TRANSFORM".equals(funcName) || "FILTER".equals(funcName) || "REDUCE".equals(funcName))
                && hasLambdaArgument(expr)) {
            return evaluateHigherOrderFunction(funcName, expr.getArguments());
        }

        // Handle special functions
        if (expr.isStar()) {
            // OBJECT_CONSTRUCT(*) / OBJECT_CONSTRUCT_KEEP_NULL(*) [EXCLUDE cols]: expand the star to the
            // current row's columns as alternating key/value arguments, dropping any EXCLUDEd columns.
            if (("OBJECT_CONSTRUCT".equals(funcName) || "OBJECT_CONSTRUCT_KEEP_NULL".equals(funcName))
                    && table != null && row != null) {
                BuiltInFunction objFunc = functionRegistry.getFunction(funcName);
                if (objFunc != null) {
                    List<Object> kv = new ArrayList<>();
                    List<String> excludes = expr.getStarExcludes();
                    List<TableColumn> cols = table.getColumns();
                    for (int i = 0; i < cols.size(); i++) {
                        String colName = cols.get(i).getName();
                        if (excludes.contains(colName.toUpperCase())) {
                            continue;
                        }
                        kv.add(colName);
                        kv.add(row.getValue(i));
                    }
                    return objFunc.evaluate(kv);
                }
            }
            // COUNT(*) - handled by function implementation
            BuiltInFunction func = functionRegistry.getFunction(funcName);
            if (func != null) {
                return func.evaluate(new ArrayList<>());
            }
        }

        // Conditional functions SHORT-CIRCUIT, like CASE (whose IFF is Snowflake's shorthand): only the
        // selected branch is evaluated. Evaluating every argument first breaks the standard guard idiom
        // `IFF(x IS NOT NULL, f(x), NULL)` / `NVL(x, g(y))`, where the unselected branch would error or
        // is merely expensive — a UDF that rejects NULL then failed on rows the guard excluded.
        final Object shortCircuited = evaluateConditionalFunction(funcName, expr.getArguments());
        if (shortCircuited != NOT_CONDITIONAL) {
            return shortCircuited;
        }

        // Evaluate arguments; a ** spread argument splices its ARRAY elements (or OBJECT pairs,
        // for the key/value-shaped constructors) into the positional argument list.
        List<Object> argValues = new ArrayList<>();
        for (final Expression arg : expr.getArguments()) {
            if (arg instanceof SpreadExpression) {
                spliceSpreadValue(((SpreadExpression) arg).getInner().accept(this), argValues);
            } else {
                argValues.add(arg.accept(this));
            }
        }
        rejectVarcharColumnInTemporalFunction(funcName, expr.getArguments(), argValues);

        // IDENTIFIER(string) — resolves a string as a column name in the current row
        if ("IDENTIFIER".equals(funcName) && argValues.size() == 1 && argValues.get(0) != null) {
            String colName = argValues.get(0).toString();
            if (table != null && row != null) {
                int idx = table.getColumnIndex(colName);
                if (idx >= 0) return row.getValue(idx);
            }
            return null;
        }

        // Look up and execute built-in function
        BuiltInFunction func = functionRegistry.getFunction(funcName);
        if (func != null) {
            return func.evaluate(argValues);
        }

        // Delegate SYSTEM$ functions to QueryExecutor
        if (funcName.startsWith("SYSTEM$") && queryExecutor != null) {
            try {
                return queryExecutor.evaluateSystemFunction(funcName, argValues);
            } catch (final Exception ignored) {}
        }

        // Check for user-defined function
        if (catalog != null) {
            Function udf = null;
            try {
                String dbName = catalog.getCurrentDatabase();
                if (dbName != null) {
                    // For qualified names (schema.func or db.schema.func), resolve the specific schema
                    String[] parts = funcName.split("\\.");
                    Schema targetSchema;
                    String simpleName;
                    if (parts.length == 2) {
                        // schema.function
                        targetSchema = catalog.getDatabase(dbName).getSchema(parts[0]);
                        simpleName = parts[1];
                    } else if (parts.length == 3) {
                        // db.schema.function
                        targetSchema = catalog.getDatabase(parts[0]).getSchema(parts[1]);
                        simpleName = parts[2];
                    } else {
                        // unqualified — search current schema
                        String schemaName = catalog.getCurrentSchema();
                        targetSchema = schemaName != null
                            ? catalog.getDatabase(dbName).getSchema(schemaName) : null;
                        simpleName = funcName;
                    }

                    if (targetSchema != null) {
                        udf = resolveOverloadedFunction(targetSchema, simpleName, argValues);
                    }

                    // If not found in resolved schema and unqualified, also try current schema
                    if (udf == null && parts.length == 1) {
                        String schemaName = catalog.getCurrentSchema();
                        if (schemaName != null) {
                            Schema currentSchema = catalog.getDatabase(dbName).getSchema(schemaName);
                            udf = resolveOverloadedFunction(currentSchema, simpleName, argValues);
                        }
                    }
                }
            } catch (final RuntimeException e) {
                // Function not found in catalog, udf remains null
            }

            if (udf != null) {
                final List<Object> callArgs = expr.getArgumentNames() != null
                    ? reorderNamedArgs(expr.getArgumentNames(), argValues, udf.getParameters(), funcName)
                    : argValues;
                return evaluateUserDefinedFunction(udf, callArgs);
            }
        }

        throw new RuntimeException("Unknown function: " + funcName);
    }

    private Function resolveOverloadedFunction(final Schema schema, final String funcName, final List<Object> argValues) {
        return udfInvoker.resolveOverloadedFunction(schema, funcName, argValues);
    }

    private List<Object> reorderNamedArgs(final List<String> names, final List<Object> argValues,
            final List<Parameter> params, final String funcName) {
        return udfInvoker.reorderNamedArgs(names, argValues, params, funcName);
    }

    private Object evaluateUserDefinedFunction(final Function function, final List<Object> args) {
        return udfInvoker.evaluateUserDefinedFunction(function, args);
    }

    @Override
    public Object visitCaseExpression(final CaseExpression expr) {
        for (final CaseExpression.WhenClause when : expr.getWhenClauses()) {
            Object condition = when.getCondition().accept(this);
            if (isTrue(condition)) {
                return when.getResult().accept(this);
            }
        }

        if (expr.getElseExpression() != null) {
            return expr.getElseExpression().accept(this);
        }

        return null;
    }

    @Override
    public Object visitCast(final CastExpression expr) {
        Object value = expr.getExpression().accept(this);
        if (isVariantJsonNullCast(expr, value)) {
            return null;
        }
        if (expr.isTryMode()) {
            // TRY_CAST: a conversion that would fail (e.g. a non-numeric string to NUMBER) yields NULL.
            try {
                return castValue(value, expr.getTargetType());
            } catch (final RuntimeException e) {
                return null;
            }
        }
        return castValue(value, expr.getTargetType());
    }

    @Override
    public Object visitIsNull(final IsNullExpression expr) {
        Object value = expr.getOperand().accept(this);
        boolean isNull = (value == null);
        return expr.isNot() ? !isNull : isNull;
    }

    @Override
    public Object visitTupleIn(final TupleInExpression expr) {
        List<Object> left = new ArrayList<>();
        for (final Expression e : expr.getValues()) {
            left.add(e.accept(this));
        }
        int k = left.size();
        boolean found = false;
        if (expr.hasSubquery()) {
            if (queryExecutor == null) {
                throw new RuntimeException("Cannot evaluate tuple IN subquery: QueryExecutor not available");
            }
            List<ResultSet> results = executeSubquery(expr.getSubquery().getSubquery());
            if (!results.isEmpty()) {
                for (final Row subRow : results.get(0).getRows()) {
                    if (tupleMatches(left, subRow)) {
                        found = true;
                        break;
                    }
                }
            }
        } else {
            List<Object> right = new ArrayList<>();
            for (final Expression e : expr.getListValues()) {
                right.add(e.accept(this));
            }
            // The flat right-hand list is grouped into tuples the size of the left side.
            for (int start = 0; start + k <= right.size(); start += k) {
                boolean allMatch = true;
                for (int i = 0; i < k; i++) {
                    if (!equals(left.get(i), right.get(start + i))) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    found = true;
                    break;
                }
            }
        }
        return expr.isNot() ? !found : found;
    }

    @Override
    public Object visitSpread(final SpreadExpression expr) {
        throw new RuntimeException(
            "The spread operator (**) is only valid inside an array/object constructor, "
            + "a function argument list, or the SELECT list");
    }

    /** Snowflake coerces string CONSTANTS to dates/timestamps in temporal functions but rejects
     *  VARCHAR columns and expressions ("Function DATE_TRUNC does not support VARCHAR argument
     *  type" — live-verified). The temporal argument positions per function are listed below; a
     *  string value in one of them is allowed only when its expression is a literal (or a session
     *  variable, which Snowflake also treats as a constant). */
    private static final Map<String, int[]> TEMPORAL_STRICT_ARGS = new HashMap<>();
    static {
        TEMPORAL_STRICT_ARGS.put("DATE_TRUNC", new int[]{1});
        TEMPORAL_STRICT_ARGS.put("EXTRACT", new int[]{1});
        TEMPORAL_STRICT_ARGS.put("DATE_PART", new int[]{1});
        TEMPORAL_STRICT_ARGS.put("LAST_DAY", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("DATEADD", new int[]{2});
        TEMPORAL_STRICT_ARGS.put("TIMEADD", new int[]{2});
        TEMPORAL_STRICT_ARGS.put("TIMESTAMPADD", new int[]{2});
        TEMPORAL_STRICT_ARGS.put("DATEDIFF", new int[]{1, 2});
        TEMPORAL_STRICT_ARGS.put("TIMEDIFF", new int[]{1, 2});
        TEMPORAL_STRICT_ARGS.put("TIMESTAMPDIFF", new int[]{1, 2});
        TEMPORAL_STRICT_ARGS.put("DAYNAME", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("MONTHNAME", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("ADD_MONTHS", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("MONTHS_BETWEEN", new int[]{0, 1});
        TEMPORAL_STRICT_ARGS.put("NEXT_DAY", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("PREVIOUS_DAY", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("YEAR", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("QUARTER", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("MONTH", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("WEEK", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("WEEKOFYEAR", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("WEEKISO", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("DAY", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("DAYOFMONTH", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("DAYOFWEEK", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("DAYOFWEEKISO", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("DAYOFYEAR", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("HOUR", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("MINUTE", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("SECOND", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("YEAROFWEEK", new int[]{0});
        TEMPORAL_STRICT_ARGS.put("YEAROFWEEKISO", new int[]{0});
    }

    private void rejectVarcharColumnInTemporalFunction(final String funcName, final List<Expression> args,
                                                       final List<Object> argValues) {
        final int[] positions = TEMPORAL_STRICT_ARGS.get(funcName);
        if (positions == null || args.size() != argValues.size()) {
            return;   // size mismatch = a spread was spliced; positions no longer line up — skip
        }
        for (final int position : positions) {
            if (position >= args.size() || !(args.get(position) instanceof ColumnReferenceExpression)) {
                // Only DECLARED-VARCHAR column references are rejected. The engine represents
                // dates/timestamps as strings internally, so a runtime value check would wrongly
                // flag genuine temporal columns and expression results.
                continue;
            }
            final ColumnReferenceExpression ref = (ColumnReferenceExpression) args.get(position);
            final TableColumn resolved = resolveDeclaredColumn(ref);
            if (resolved != null && resolved.getDataType() instanceof StringType) {
                throw new RuntimeException(
                    "Function " + funcName + " does not support VARCHAR argument type");
            }
        }
    }

    /** The declared table column a reference points at, or null when it cannot be resolved (an
     *  alias, a UDF parameter, a lateral value, ...) or when it belongs to a DERIVED table (CTE,
     *  subquery, joined view) — derived tables declare most columns as VARCHAR regardless of the
     *  value's real type, so only BASE catalog tables carry trustworthy declared types. */
    private TableColumn resolveDeclaredColumn(final ColumnReferenceExpression ref) {
        final String columnName = ref.getColumnName();
        if (ref.getTableName() != null) {
            if (multiTableAliasToTable != null) {
                final Table qualified = multiTableAliasToTable.get(ref.getTableName().toUpperCase());
                if (qualified != null && qualified.hasColumn(columnName) && isBaseCatalogTable(qualified)) {
                    return qualified.getColumn(columnName);
                }
            }
            if (table != null && table.getName().equalsIgnoreCase(ref.getTableName())
                    && table.hasColumn(columnName) && isBaseCatalogTable(table)) {
                return table.getColumn(columnName);
            }
            return null;
        }
        if (table != null && table.hasColumn(columnName)) {
            return isBaseCatalogTable(table) ? table.getColumn(columnName) : null;
        }
        if (multiTableAllTables != null) {
            for (final Table candidate : multiTableAllTables) {
                if (candidate.hasColumn(columnName)) {
                    return isBaseCatalogTable(candidate) ? candidate.getColumn(columnName) : null;
                }
            }
        }
        return null;
    }

    /** True when this Table instance is the catalog's own object for its name (a base table) — a
     *  derived/virtual table (CTE, VALUES, joined view, self-join copy) either isn't in the catalog
     *  or is a different instance. */
    private boolean isBaseCatalogTable(final Table candidate) {
        if (catalog == null) {
            return false;
        }
        try {
            return catalog.resolveTable(candidate.getName()) == candidate;
        } catch (final RuntimeException notFound) {
            return false;
        }
    }

    /** Splice a spread value into a positional list: an ARRAY contributes its elements, an OBJECT
     *  contributes alternating key/value entries (the constructor shape), NULL contributes nothing.
     *  The engine's ARRAY/OBJECT values are canonical JSON text, so that form is parsed here. */
    private void spliceSpreadValue(final Object value, final List<Object> into) {
        if (value == null) {
            return;
        }
        if (value instanceof List) {
            into.addAll((List<?>) value);
            return;
        }
        if (value instanceof Map) {
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                into.add(entry.getKey());
                into.add(entry.getValue());
            }
            return;
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node != null && node.isArray()) {
            for (final JsonNode element : node) {
                into.add(ArrayFunctionHelper.fromNode(element));
            }
            return;
        }
        if (node != null && node.isObject()) {
            for (final Map.Entry<String, JsonNode> field : node.properties()) {
                into.add(field.getKey());
                into.add(ArrayFunctionHelper.fromNode(field.getValue()));
            }
            return;
        }
        throw new RuntimeException("The spread operator (**) requires an ARRAY or OBJECT value, got: " + value);
    }

@Override
        public Object visitWindowFunction(final WindowFunctionExpression expr) {
        // A window function nested in an expression is precomputed per row by the window stage and supplied
        // through the result context, keyed by the call's exact source text. Resolve it here rather than
        // evaluating row-wise — a window function spans a whole partition, not a single row.
        if (resultContext != null && resultContext.containsKey(expr.getCallText())) {
            return resultContext.get(expr.getCallText());
        }
        throw new RuntimeException("Window function not available in this context: " + expr.getCallText());
    }

    private boolean tupleMatches(final List<Object> left, final Row subRow) {
        for (int i = 0; i < left.size(); i++) {
            if (!equals(left.get(i), subRow.getValue(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object visitObjectAccess(final ObjectAccessExpression expr) {
        Object baseValue = expr.getBase().accept(this);

        if (baseValue == null) {
            return null;
        }

        // Traverse the property chain using the path segments from the parse tree (data:a.b.c → [a,b,c]).
        Object current = baseValue;
        for (final String part : expr.getPathParts()) {
            if (current == null) return null;
            current = extractJsonProperty(current.toString(), part);
        }
        return current;
    }

    @Override
    public Object visitArrayAccess(final ArrayAccessExpression expr) {
        Object arrayValue = expr.getArray().accept(this);
        Object indexValue = expr.getIndex().accept(this);

        if (arrayValue == null || indexValue == null) {
            return null;
        }

        final String jsonString = arrayValue.toString();
        // A numeric subscript indexes into a JSON array; a string subscript (col['key']) is object member
        // access — Snowflake's bracket notation, equivalent to col:key.
        if (indexValue instanceof Number) {
            return extractJsonArrayElement(jsonString, ((Number) indexValue).intValue());
        }
        return extractJsonProperty(jsonString, indexValue.toString());
    }

    @Override
    public Object visitSubquery(final SubqueryExpression expr) {
        return evaluateScalarSubquery(expr.getSubquery());
    }

    @Override
    public Object visitExecuteImmediate(final ExecuteImmediateExpression expr) {
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXECUTE IMMEDIATE: QueryExecutor not available");
        }
        final Object sqlValue = expr.getSqlExpression().accept(this);
        String sqlText = sqlValue != null ? sqlValue.toString() : "";
        final List<Expression> binds = expr.getUsingBindings();
        if (binds != null && !binds.isEmpty()) {
            final List<Object> bindValues = new ArrayList<>();
            for (final Expression bind : binds) {
                bindValues.add(bind.accept(this));
            }
            sqlText = JdbcMarshaling.substitutePlaceholders(sqlText, bindValues);
        }
        // Execute the dynamic statement directly (not via the memoizing subquery path) and return its first
        // column of the first row, like a scalar subquery; an empty/rowless result yields NULL.
        final List<ResultSet> results = queryExecutor.execute(sqlText);
        if (results.isEmpty() || results.get(0).getRowCount() == 0) {
            return null;
        }
        return results.get(0).getRows().get(0).getValue(0);
    }

    @Override
    public Object visitJsonObject(final JsonObjectExpression expr) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (final Map.Entry<String, Expression> entry : expr.getProperties().entrySet()) {
            final Object value = entry.getValue().accept(this);
            // Snowflake object constants follow OBJECT_CONSTRUCT semantics: a pair whose value is
            // SQL NULL is omitted from the object (a VARIANT JSON null — the text "null" — stays a
            // null member). The vendor stats pattern depends on this: {'k': null, ...} followed by
            // OBJECT_INSERT(obj, 'k', v) without the update flag only works when 'k' was dropped.
            if (value == null) {
                continue;
            }
            if (!first) {
                json.append(", ");
            }
            json.append("\"").append(entry.getKey()).append("\": ");
            json.append(jsonElementText(entry.getValue(), value));
            first = false;
        }

        json.append("}");
        return canonicalJsonOrText(json.toString());
    }

    @Override
    public Object visitJsonArray(final JsonArrayExpression expr) {
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < expr.getElements().size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            final Expression element = expr.getElements().get(i);
            json.append(jsonElementText(element, element.accept(this)));
        }

        json.append("]");
        return canonicalJsonOrText(json.toString());
    }

    /**
     * Canonical form of an assembled object/array literal — keys sorted, compact separators — so a
     * {@code [{'edition': 'pro*'}]} literal compares equal to the same value extracted from a VARIANT
     * (Snowflake stores both canonically; keeping the literal's source spacing made EXCEPT see a diff).
     */
    private String canonicalJsonOrText(final String jsonText) {
        final JsonNode node = ArrayFunctionHelper.parseNode(jsonText);
        return node != null ? ArrayFunctionHelper.toCanonicalJson(node) : jsonText;
    }

    @Override
    public Object visitBetween(final BetweenExpression expr) {
        Object value = expr.getValue().accept(this);
        Object lower = expr.getLower().accept(this);
        Object upper = expr.getUpper().accept(this);

        // In SQL, NULL BETWEEN anything is NULL (treated as false)
        if (value == null || lower == null || upper == null) {
            return false;
        }

        boolean result = compare(value, lower) >= 0 && compare(value, upper) <= 0;

        return expr.isNot() ? !result : result;
    }

    @Override
    public Object visitIn(final InExpression expr) {
        Object value = expr.getValue().accept(this);

        if (expr.hasSubquery()) {
            // IN with subquery
            return evaluateInSubquery(value, expr.getSubquery().getSubquery(), expr.isNot());
        }

        // IN with a value list, using three-valued logic:
        //   value IN (list)     → TRUE if it equals a member; else UNKNOWN if any member (or the value
        //                         itself) is NULL; else FALSE.
        //   value NOT IN (list) → the boolean negation, with UNKNOWN preserved.
        if (value == null) {
            return null; // NULL IN (...) is UNKNOWN
        }
        boolean anyNull = false;
        for (final Expression valueExpr : expr.getValues()) {
            final Object listValue = valueExpr.accept(this);
            if (listValue == null) {
                anyNull = true;
                continue;
            }
            if (equals(value, listValue)) {
                return !expr.isNot(); // a concrete match: TRUE for IN, FALSE for NOT IN
            }
        }
        if (anyNull) {
            return null; // no match but a NULL member → UNKNOWN (for both IN and NOT IN)
        }
        return expr.isNot(); // no match and no NULLs: FALSE for IN, TRUE for NOT IN
    }

    @Override
    public Object visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        Object leftValue = expr.getLeft().accept(this);

        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate quantified comparison: QueryExecutor not available");
        }

        // Extract subquery SQL from SubqueryExpression
        String subquerySql;
        if (expr.getSubquery() instanceof SubqueryExpression) {
            subquerySql = ((SubqueryExpression) expr.getSubquery()).getSubquery();
        } else {
            throw new RuntimeException("Quantified comparison requires SubqueryExpression");
        }

        List<ResultSet> results = executeSubquery(subquerySql);

        if (results.isEmpty() || results.get(0).getRowCount() == 0) {
            // Empty result set: ALL returns true, ANY/SOME returns false
            return expr.getQuantifier() == Quantifier.ALL;
        }

        ResultSet resultSet = results.get(0);
        final BinaryOperator operator = expr.getOperator();

        if (expr.getQuantifier() == Quantifier.ALL) {
            // ALL (three-valued): FALSE if the comparison fails for any row (dominates); else
            // UNKNOWN if it is UNKNOWN for any row (a NULL on either side); else TRUE.
            boolean anyUnknown = false;
            for (final Row subRow : resultSet.getRows()) {
                final Boolean cmp = compareWithOperator(leftValue, subRow.getValue(0), operator);
                if (cmp == null) {
                    anyUnknown = true;
                } else if (!cmp.booleanValue()) {
                    return false;
                }
            }
            return anyUnknown ? null : Boolean.TRUE;
        } else {
            // ANY / SOME (three-valued): TRUE if the comparison holds for any row (dominates); else
            // UNKNOWN if it is UNKNOWN for any row; else FALSE.
            boolean anyUnknown = false;
            for (final Row subRow : resultSet.getRows()) {
                final Boolean cmp = compareWithOperator(leftValue, subRow.getValue(0), operator);
                if (cmp == null) {
                    anyUnknown = true;
                } else if (cmp.booleanValue()) {
                    return true;
                }
            }
            return anyUnknown ? null : Boolean.FALSE;
        }
    }

    @Override
    public Object visitInterval(final IntervalExpression expr) {
        // Evaluate the value expression (e.g., '10' in INTERVAL '10' DAYS)
        Object value = expr.getValueExpression().accept(this);

        // For now, return an IntervalValue object that holds the value and unit
        // This can be used in date/time arithmetic operations
        return new IntervalValue(value, expr.getUnit());
    }

    // Helper methods

    private boolean isTrue(final Object value) {
        return ExpressionArithmetic.isTrue(value);
    }

    private Boolean booleanOrNull(final Object value) {
        return ExpressionArithmetic.booleanOrNull(value);
    }

    private Object add(final Object left, final Object right) {
        return ExpressionArithmetic.add(left, right);
    }

    private Object subtract(final Object left, final Object right) {
        return ExpressionArithmetic.subtract(left, right);
    }

    private Object multiply(final Object left, final Object right) {
        return ExpressionArithmetic.multiply(left, right);
    }

    private Object divide(final Object left, final Object right) {
        return ExpressionArithmetic.divide(left, right);
    }

    private Object modulo(final Object left, final Object right) {
        return ExpressionArithmetic.modulo(left, right);
    }

    private boolean equals(final Object left, final Object right) {
        return ExpressionArithmetic.equals(left, right);
    }

    /** True when a string {@code =}/{@code !=} involves a case-insensitively collated column operand. */
    private boolean isCaseInsensitiveStringComparison(final BinaryOperationExpression expr,
                                                      final Object left, final Object right) {
        return left instanceof String && right instanceof String
            && (isCaseInsensitiveCollated(expr.getLeft()) || isCaseInsensitiveCollated(expr.getRight()));
    }

    /** Whether {@code expr} references a column whose collation is case-insensitive (a "ci" specifier). */
    private boolean isCaseInsensitiveCollated(final Expression expr) {
        if (!(expr instanceof ColumnReferenceExpression)) {
            return false;
        }
        final ColumnReferenceExpression colRef = (ColumnReferenceExpression) expr;
        final String colName = colRef.getColumnName();
        // Qualified reference in a join: the collation lives on the qualifier's table, not on the
        // single-table context (a JOIN ON over an 'en-ci' column silently compared case-sensitively).
        if (colRef.getTableName() != null && multiTableAliasToTable != null) {
            for (final Map.Entry<String, Table> aliased : multiTableAliasToTable.entrySet()) {
                if (aliased.getKey().equalsIgnoreCase(colRef.getTableName())) {
                    return columnIsCaseInsensitive(aliased.getValue(), colName);
                }
            }
        }
        if (table != null && table.hasColumn(colName)) {
            return columnIsCaseInsensitive(table, colName);
        }
        // Unqualified reference in a join: the first in-scope table owning the column decides,
        // matching the value-resolution order.
        if (multiTableAllTables != null) {
            for (final Table joined : multiTableAllTables) {
                if (joined != null && joined.hasColumn(colName)) {
                    return columnIsCaseInsensitive(joined, colName);
                }
            }
        }
        return false;
    }

    private static boolean columnIsCaseInsensitive(final Table owner, final String colName) {
        for (final TableColumn col : owner.getColumns()) {
            if (col.getName().equalsIgnoreCase(colName)) {
                return isCaseInsensitiveCollation(col.getCollation());
            }
        }
        return false;
    }

    private static boolean isCaseInsensitiveCollation(final String collation) {
        if (collation == null) {
            return false;
        }
        final String normalized = collation.toLowerCase().replace("'", "").replace("\"", "").trim();
        for (final String part : normalized.split("-")) {
            if (part.equals("ci")) {
                return true;
            }
        }
        return false;
    }

    private int compare(final Object left, final Object right) {
        return ExpressionArithmetic.compare(left, right);
    }

    private Object negate(final Number value) {
        return ExpressionArithmetic.negate(value);
    }

    /** The LIKE ESCAPE character: the first char of the evaluated ESCAPE expression, else the default backslash. */
    private char resolveEscapeChar(final Expression escape) {
        if (escape == null) {
            return '\\';
        }
        final Object value = escape.accept(this);
        final String s = value == null ? "" : value.toString();
        return s.isEmpty() ? '\\' : s.charAt(0);
    }

    private Object evaluateLike(final Object value, final Object pattern,
                                final BinaryOperator op, final char escapeChar) {
        return LikeMatcher.evaluateLike(value, pattern, op, escapeChar);
    }

    private Object castValue(final Object value, final String targetType) {
        return ValueCaster.castValue(value, targetType);
    }

    /**
     * Whether this cast is a VARIANT JSON null being converted to an ordinary SQL type, which Snowflake
     * resolves to SQL NULL: {@code v:missing::VARCHAR IS NULL} is TRUE, while the uncast {@code v:missing} is a
     * JSON null and {@code IS NULL} on it is FALSE. A JSON null is carried as the four-character text
     * {@code null}, so it is only recognisable as such when the operand is a VARIANT PATH (a {@code :} field
     * access or a {@code [i]} element access) — a plain
     * {@code 'null'} string keeps its text, and {@code TO_DATE('null')} still errors.
     *
     * <p>Casting to a semi-structured type is excluded: {@code v:missing::VARIANT} is still a JSON null.
     * Without this every consumer of such a path saw the literal text: TO_TIMESTAMP / TO_DATE / TO_TIME /
     * DATEADD all failed with "Cannot parse date/time: null" instead of returning NULL.
     */
    private boolean isVariantJsonNullCast(final CastExpression expr, final Object value) {
        if (!(value instanceof CharSequence) || !"null".equals(value.toString())) {
            return false;
        }
        if (!hasVariantValuedOperand(expr.getExpression())) {
            return false;
        }
        String baseType = expr.getTargetType().toUpperCase();
        final int parenIndex = baseType.indexOf('(');
        if (parenIndex > 0) {
            baseType = baseType.substring(0, parenIndex).trim();
        }
        return !"VARIANT".equals(baseType) && !"ARRAY".equals(baseType) && !"OBJECT".equals(baseType);
    }

    /**
     * Whether the cast operand is VARIANT-valued, so its bare {@code null} text means a JSON null
     * (a plain {@code 'null'} VARCHAR must keep its text): a variant path access, a
     * variant-producing function (PARSE_JSON / GET / …), or a column declared with a
     * semi-structured type in the evaluation table.
     */
    private boolean hasVariantValuedOperand(final Expression operand) {
        if (operand instanceof ObjectAccessExpression || operand instanceof ArrayAccessExpression) {
            return true;
        }
        if (operand instanceof FunctionCallExpression) {
            final String fn = ((FunctionCallExpression) operand).getFunctionName().toUpperCase();
            return "PARSE_JSON".equals(fn) || "TRY_PARSE_JSON".equals(fn)
                || "GET".equals(fn) || "GET_PATH".equals(fn);
        }
        if (operand instanceof ColumnReferenceExpression && table != null) {
            final TableColumn column = table.getColumn(((ColumnReferenceExpression) operand).getColumnName());
            if (column == null || column.getDataType() == null) {
                return false;
            }
            final DataType type = column.getDataType();
            return type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType;
        }
        return false;
    }

    private Object extractJsonProperty(final String jsonString, final String property) {
        return JsonPathExtractor.extractJsonProperty(jsonString, property);
    }

    private Object extractJsonArrayElement(final String jsonString, final int index) {
        return JsonPathExtractor.extractJsonArrayElement(jsonString, index);
    }

    private String formatJsonValue(final Object value) {
        return JsonPathExtractor.formatJsonValue(value);
    }

    /**
     * Render a value as it should appear inside a JSON object/array literal. A nested object or array
     * literal ({@code {...}} / {@code [...]}) has already been evaluated to a JSON string, so it is
     * embedded raw; every other value is formatted as a JSON scalar (strings quoted, numbers bare).
     * Without this, a nested literal would be re-quoted as a string, e.g. {@code [{'b':1}]} would store
     * the malformed {@code ["{"b": 1}"]} and later {@code c[0]} would fail to parse.
     */
    private String jsonElementText(final Expression element, final Object value) {
        // A nested object/array LITERAL ({...}/[...]) evaluates to its own valid JSON text — embed as-is.
        if (value instanceof String
                && (element instanceof JsonObjectExpression || element instanceof JsonArrayExpression)) {
            return (String) value;
        }
        // A value that IS a JSON object/array — a JsonNode, or the JSON text produced by OBJECT_CONSTRUCT /
        // PARSE_JSON / a UDF returning OBJECT — must be embedded as a NESTED node, not a quoted string.
        // Otherwise the enclosing JSON is malformed ({"k": "{"a":1}"}, unescaped) and any downstream
        // OBJECT_* / variant path over it returns null — the root cause of the loader stats object going NULL.
        if (value instanceof JsonNode) {
            return value.toString();
        }
        if (value instanceof String) {
            // A VARIANT JSON null IS the text "null" in this engine's value model (PARSE_JSON('null'));
            // embed it as a real JSON null member, not as the quoted string "null".
            if ("null".equals(value)) {
                return "null";
            }
            final String nested = jsonStructureOrNull((String) value);
            if (nested != null) {
                return nested;
            }
            // The QUOTED carrier for a string whose content looks structural (path access over
            // {"v": "[]"} yields "\"[]\"" — see JsonPathExtractor) is already a valid JSON string
            // literal: embed it raw. Re-quoting it via formatJsonValue double-encoded the member.
            final String carrier = quotedStructuralStringOrNull((String) value);
            if (carrier != null) {
                return carrier;
            }
        }
        return formatJsonValue(value);
    }

    /**
     * If {@code s} is the engine's quoted carrier for a STRING value that must stay distinguishable
     * from real JSON — a JSON string literal whose decoded content starts with '{' or '[', or is the
     * JSON-null marker text "null" — returns it (trimmed) so it can be embedded as-is; otherwise null.
     */
    private static String quotedStructuralStringOrNull(final String s) {
        final String trimmed = s.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '"') {
            return null;
        }
        try {
            final JsonNode node = ArrayFunctionHelper.MAPPER.readTree(trimmed);
            if (node != null && node.isTextual()) {
                final String content = node.asText().trim();
                if (content.startsWith("{") || content.startsWith("[") || "null".equals(content)) {
                    return trimmed;
                }
            }
        } catch (final Exception notJson) {
            // Not valid JSON — treat as an ordinary scalar string.
        }
        return null;
    }

    /**
     * If {@code s} is JSON text for an object or array, returns it (trimmed) so it can be embedded as a
     * nested node inside an enclosing object/array literal; otherwise returns null (the value is a scalar).
     */
    private static String jsonStructureOrNull(final String s) {
        final String trimmed = s.trim();
        if (trimmed.length() < 2) {
            return null;
        }
        final char c = trimmed.charAt(0);
        if (c != '{' && c != '[') {
            return null;
        }
        try {
            final JsonNode node = ArrayFunctionHelper.MAPPER.readTree(trimmed);
            if (node != null && (node.isObject() || node.isArray())) {
                return trimmed;
            }
        } catch (final Exception notJson) {
            // Not valid JSON — treat as an ordinary scalar string.
        }
        return null;
    }

    private Object evaluateExists(final String subquery) {
        return subqueryEvaluator.evaluateExists(subquery);
    }

    private Object evaluateScalarSubquery(final String subquery) {
        return subqueryEvaluator.evaluateScalarSubquery(subquery);
    }

    private Object evaluateInSubquery(final Object value, final String subquery, final boolean not) {
        return subqueryEvaluator.evaluateInSubquery(value, subquery, not);
    }

    private List<ResultSet> executeSubquery(final String subquery) {
        return subqueryEvaluator.executeSubquery(subquery);
    }

    private Boolean compareWithOperator(final Object left, final Object right,
                                        final BinaryOperator operator) {
        return ExpressionArithmetic.compareWithOperator(left, right, operator);
    }
}
