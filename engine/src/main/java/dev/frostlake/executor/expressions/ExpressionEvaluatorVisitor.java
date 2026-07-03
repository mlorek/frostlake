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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

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
            Catalog catalog2 = queryExecutor.getCatalog();
            String dbName = catalog2.getCurrentDatabase();
            String scName = catalog2.getCurrentSchema();
            if (dbName == null || scName == null) return false;
            Stream stream =
                catalog2.getDatabase(dbName).getSchema(scName).getStream(streamName);
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

        throw new RuntimeException("Column not found: " + expr);
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
                return String.valueOf(left) + String.valueOf(right);
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
                if (negOperand instanceof Number) {
                    return negate((Number) negOperand);
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
    public Object visitFunctionCall(final FunctionCallExpression expr) {
        String funcName = expr.getFunctionName().toUpperCase();

        // HAVING/QUALIFY: an aggregate/window function that matches a precomputed SELECT-list
        // output resolves to that value rather than being re-evaluated.
        if (resultContext != null) {
            final String canonical = AstPrinterVisitor.print(expr);
            if (resultContext.containsKey(canonical)) {
                return resultContext.get(canonical);
            }
        }

        // Handle special functions
        if (expr.isStar()) {
            // COUNT(*) - handled by function implementation
            BuiltInFunction func = functionRegistry.getFunction(funcName);
            if (func != null) {
                return func.evaluate(new ArrayList<>());
            }
        }

        // Evaluate arguments
        List<Object> argValues = new ArrayList<>();
        for (final Expression arg : expr.getArguments()) {
            argValues.add(arg.accept(this));
        }

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
            if (!first) {
                json.append(", ");
            }
            Object value = entry.getValue().accept(this);
            json.append("\"").append(entry.getKey()).append("\": ");
            json.append(formatJsonValue(value));
            first = false;
        }

        json.append("}");
        return json.toString();
    }

    @Override
    public Object visitJsonArray(final JsonArrayExpression expr) {
        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < expr.getElements().size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            Object value = expr.getElements().get(i).accept(this);
            json.append(formatJsonValue(value));
        }

        json.append("]");
        return json.toString();
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
        if (table == null || !(expr instanceof ColumnReferenceExpression)) {
            return false;
        }
        final String colName = ((ColumnReferenceExpression) expr).getColumnName();
        for (final TableColumn col : table.getColumns()) {
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

    private Object extractJsonProperty(final String jsonString, final String property) {
        return JsonPathExtractor.extractJsonProperty(jsonString, property);
    }

    private Object extractJsonArrayElement(final String jsonString, final int index) {
        return JsonPathExtractor.extractJsonArrayElement(jsonString, index);
    }

    private String formatJsonValue(final Object value) {
        return JsonPathExtractor.formatJsonValue(value);
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
