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
import dev.frostlake.executor.AmbiguousColumnException;
import dev.frostlake.executor.InvalidQualifierException;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.values.UndefinedNode;
import dev.frostlake.values.VariantJsonNulls;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.HigherOrderFunctionNames;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.string.Concat;
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
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeoTypes;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VectorValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
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
    private final TypeInferencer typeInferencer = new TypeInferencer(this);
    private final Catalog catalog;
    private QueryExecutor queryExecutor;
    private Map<String, Object> lateralContext;
    private Map<String, Table> multiTableAliasToTable;
    private List<Table> multiTableAllTables;
    private Collection<String> fromClauseKeys;
    private Set<String> scopeExemptNames;
    private boolean strictWalkInsideFunctionArgs;
    private Map<String, Object> resultContext;

    /** The context functions Snowflake accepts WITHOUT parentheses — exactly these six, measured on
     *  a real account (bare CURRENT_ROLE, CURRENT_ACCOUNT, SYSDATE and GETDATE are all rejected). */
    private static final Set<String> PARENLESS_CONTEXT_NAMES = Set.of(
        "CURRENT_DATE", "CURRENT_TIME", "CURRENT_TIMESTAMP",
        "LOCALTIME", "LOCALTIMESTAMP", "CURRENT_USER");
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
        if (aliasToTable != null && !aliasToTable.isEmpty()) {
            setFromClauseKeys(aliasToTable.keySet());
        }
    }

    /**
     * The FROM clause's relation keys — each table's alias when one was written, else its name.
     * Live, an alias REPLACES the table name: with {@code FROM r AS x} the reference {@code r.t}
     * is "invalid identifier 'R.T'" in plain, JOIN and ASOF queries alike, so when these keys are
     * known, a qualifier naming none of them is refused instead of falling through to the bare-name
     * resolution. Null (the default) keeps the historical leniency for paths that do not thread
     * their FROM shape.
     */
    public void setFromClauseKeys(final Collection<String> keys) {
        this.fromClauseKeys = keys;
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
        // CONNECT_BY_ROOT <col> reads the hierarchy root row's copy of the column, materialized by the
        // CONNECT BY expansion as a hidden CONNECT_BY_ROOT$<col> column. Used WITHOUT a CONNECT BY clause
        // that column does not exist and Snowflake returns the column's own value (live-verified), so the
        // plain reference is the fallback. The inner lookups use a plain ColumnReferenceExpression, so
        // this branch cannot recurse into itself.
        if (expr instanceof ConnectByRootExpression) {
            final ConnectByRootExpression root = (ConnectByRootExpression) expr;
            try {
                return visitColumnReference(new ColumnReferenceExpression(root.getColumnName()));
            } catch (final RuntimeException notAHierarchyQuery) {
                return visitColumnReference(root.baseColumnReference());
            }
        }
        // PRIOR <col> is only meaningful inside a CONNECT BY predicate, where the expansion supplies the
        // parent row's PRIOR$<col> columns. Anywhere else the lookup fails; live parses the stray PRIOR
        // as an ordinary IDENTIFIER (with <col> as its alias) and rejects it as one —
        // "SQL compilation error: … invalid identifier 'PRIOR'" — so the same sentence is thrown here.
        if (expr instanceof PriorExpression) {
            final PriorExpression prior = (PriorExpression) expr;
            try {
                return visitColumnReference(new ColumnReferenceExpression(prior.getColumnName()));
            } catch (final RuntimeException outsideConnectBy) {
                throw new RuntimeException(SqlCompilationError.invalidIdentifier("PRIOR"));
            }
        }

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

        // SQLERRM / SQLCODE / SQLSTATE are Snowflake Scripting variables of the enclosing handler, so they
        // are bare only in a SCRIPTING expression (RETURN SQLERRM, 'x' || SQLCODE) — a path that never
        // reaches this visitor. Inside an embedded SQL statement they follow the ordinary rule and must be
        // written :SQLERRM / :SQLCODE / :SQLSTATE (live-verified), so nothing special happens here.

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

        // A bare reference to a USING / NATURAL join key reads the MERGED column: the first non-null
        // among its per-side copies. On an outer join the null-extended side's copy is null while the
        // other side carries the key, and live resolves the bare name to that value in every context
        // (SELECT, WHERE, GROUP BY, ORDER BY — measured) — a QUALIFIED reference still
        // reads its own side's copy, nulls included.
        if (!expr.isQualified() && isJoinKeyName(columnName)) {
            if (multiTableAllTables != null) {
                return coalescedJoinKeyValue(columnName, row);
            }
            return coalescedJoinKeyFromTable(columnName, row);
        }

        // A bare name carried by BOTH sides of an ON-joined relation is live's "ambiguous column
        // name 'T'" — the join FORM is the rule (measured): ON joins reject the duplicate, inner
        // and left alike, while a USING or NATURAL join resolves every same-named pair to the LEFT
        // side, keys and non-keys both (so the merged relation carries joinKeyNames exactly when
        // the lenient form applies, and first-match below IS the left preference).
        if (!expr.isQualified() && table != null && multiTableAllTables != null
                && multiTableAllTables.size() > 1
                && (table.getJoinKeyNames() == null || table.getJoinKeyNames().isEmpty())
                && countTablesCarrying(columnName) > 1) {
            throw new AmbiguousColumnException(columnName.toUpperCase());
        }

        // Multi-table (JOIN) resolution: resolve alias-aware against all joined tables first,
        // matching the executor's column resolution (so e.g. a.id and b.id resolve to their own
        // tables). Falls through on not-found to single-table / lateral / function handling.
        if (multiTableAllTables != null && queryExecutor != null) {
            try {
                final String refName = expr.isQualified() ? expr.getTableName() + "." + columnName : columnName;
                return queryExecutor.resolveColumnInTables(row, multiTableAllTables, multiTableAliasToTable, refName);
            } catch (final AmbiguousColumnException ambiguous) {
                throw ambiguous;
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

            // Live: an alias REPLACES the table name — with FROM r AS x, the reference r.t is
            // "invalid identifier 'R.T'" in plain, JOIN and ASOF queries alike. When the FROM
            // clause's keys are known, a qualifier naming none of them must not fall through to
            // the bare-name resolution below. Sequence value reads (seq.NEXTVAL, db.sch.seq.NEXTVAL)
            // and multi-part qualifiers are outside the FROM-key rule and resolve further down.
            if (fromClauseKeys != null && !fromClauseKeys.isEmpty()
                    && expr.getTableName().indexOf('.') < 0
                    && !"NEXTVAL".equalsIgnoreCase(columnName) && !"CURRVAL".equalsIgnoreCase(columnName)
                    && !qualifierIsAFromClauseKey(expr.getTableName())) {
                throw new InvalidQualifierException(
                    expr.getTableName().toUpperCase() + "." + columnName.toUpperCase());
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
        if (isDateTimeUnitKeyword(columnName.toUpperCase())) {
            return columnName.toUpperCase();
        }

        // The Snowflake sequence pseudo-column: <sequence>.NEXTVAL. Resolved as a last resort (after
        // column resolution) so a real column of that name still wins; this is what lets a column
        // DEFAULT of seq.NEXTVAL work at INSERT and MERGE time. (There is NO CURRVAL in Snowflake.)
        if (expr.isQualified()) {
            final String op = columnName.toUpperCase();
            if ("NEXTVAL".equals(op)) {
                final Sequence sequence = resolveSequence(expr.getTableName());
                if (sequence != null) {
                    return sequence.nextVal();
                }
            }
        }

        throw new RuntimeException(SqlCompilationError.invalidIdentifier(String.valueOf(expr)));
    }

    /**
     * Resolve a (possibly schema/database-qualified) sequence name for the {@code seq.NEXTVAL}
     * pseudo-column syntax. Returns null when it does not name a sequence, so the
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
                // In SQL, NULL || anything = NULL. A VARIANT JSON null reads as SQL NULL here because
                // concatenation is a scalar (VARCHAR) context — live:
                // PARSE_JSON('{"b":null}'):b || 'x' is SQL NULL, not the text 'nullx'.
                if (readsAsSqlNull(left) || readsAsSqlNull(right)) {
                    return null;
                }
                // BINARY || BINARY concatenates BYTES and stays BINARY (live: the result
                // column is BINARY), so a downstream HEX_ENCODE still sees bytes, not the hex text.
                if (left instanceof BinaryValue && right instanceof BinaryValue) {
                    return new Concat().evaluate(Arrays.asList(left, right));
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
                // (a VARCHAR column such as SPLIT_TO_TABLE's VALUE is routinely negated). A VARIANT JSON
                // null has no numeric reading and negates to SQL NULL, like the other arithmetic.
                if (readsAsSqlNull(negOperand)) {
                    return null;
                }
                final Number negNumber = ExpressionArithmetic.asNumber(negOperand);
                if (negNumber != null) {
                    // Coerced text negates to FLOAT (live: -'3' is -3.0); real numbers keep their type.
                    if (negOperand instanceof CharSequence) {
                        return Double.valueOf(-negNumber.doubleValue());
                    }
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
                // The short-circuit path bypasses the generic dispatch chokepoint, so the
                // boolean-position strictness must fire here too (IFF('true', ..) rejects live).
                rejectNonBooleanArgumentInStrictFunction(funcName, args);
                return isTrue(args.get(0).accept(this)) ? args.get(1).accept(this) : args.get(2).accept(this);
            case "COALESCE":
                if (args.size() < 2) {
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
        rejectWrongLambdaArity(funcName, lambda, args);
        final ArrayNode array = ArrayFunctionHelper.parseArray(args.get(0).accept(this));
        if (array == null) {
            return null;
        }

        if ("REDUCE".equals(funcName)) {
            Object acc = args.size() >= 3 ? args.get(1).accept(this) : null;
            for (int i = 0; i < array.size(); i++) {
                // An `undefined` element binds as SQL NULL, never as the sentinel — live:
                // REDUCE([1,NULL,2], 0, (acc,x) -> acc + COALESCE(x::int,10)) is 13.
                acc = applyLambda(lambda, acc, VariantUndefined.elementValue(array.get(i)));
            }
            return acc;
        }

        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int i = 0; i < array.size(); i++) {
            // The element binds as a VARIANT (as in Snowflake) — arithmetic on it yields FLOAT.
            final Object applied =
                applyLambda(lambda, VariantUndefined.elementValue(array.get(i)), (long) i);
            if ("FILTER".equals(funcName)) {
                if (Boolean.TRUE.equals(applied)) {
                    result.add(array.get(i));
                }
            } else { // TRANSFORM
                // A lambda returning SQL NULL contributes the VARIANT `undefined` element — live
                // TRANSFORM(ARRAY_CONSTRUCT(1,2), x -> NULL) is [undefined,undefined].
                result.add(applied instanceof JsonNode ? (JsonNode) applied
                    : ArrayFunctionHelper.toElementNode(ArrayFunctionHelper.MAPPER, applied));
            }
        }
        return result;
    }

    /**
     * TRANSFORM and FILTER take a ONE-parameter lambda; the index-carrying two-parameter form is
     * not Snowflake syntax (live-verified: {@code FILTER([1,2,3], (x, i) -> i > 0)} errors "Invalid
     * argument types for function 'FILTER': (ARRAY, FUNCTION(VARIANT,ANY))"). REDUCE's lambda
     * legitimately takes two (accumulator, element).
     */
    private void rejectWrongLambdaArity(final String funcName, final LambdaExpression lambda,
                                        final List<Expression> args) {
        final int expected = funcName.equals("REDUCE") ? 2 : 1;
        if (lambda.getParameters().size() == expected) {
            return;
        }
        final StringBuilder types = new StringBuilder();
        for (int i = 0; i < args.size() - 1; i++) {
            types.append(strictArgTypeText(args.get(i))).append(", ");
        }
        types.append("FUNCTION(VARIANT,ANY)");
        throw new RuntimeException("Invalid argument types for function '" + funcName
            + "': (" + types + ")");
    }

    /**
     * Bind the lambda's parameters to {@code values} (in order), evaluate its body, then unbind. A
     * parameter that DECLARES a type binds the value cast to it, which is what makes
     * {@code TRANSFORM([1,2], a INT -> a * 2)} answer [2,4] where the untyped lambda answers
     * [2.0,4.0] (live-verified) — the untyped element stays a VARIANT and VARIANT
     * arithmetic is FLOAT.
     */
    private Object applyLambda(final LambdaExpression lambda, final Object... values) {
        final Map<String, Object> scope = new HashMap<>();
        final List<String> params = lambda.getParameters();
        final List<String> paramTypes = lambda.getParameterTypes();
        for (int i = 0; i < params.size() && i < values.length; i++) {
            final String declaredType = paramTypes != null && i < paramTypes.size() ? paramTypes.get(i) : null;
            scope.put(params.get(i).toUpperCase(),
                declaredType == null ? values[i] : castValue(values[i], declaredType));
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
        // The names come from HigherOrderFunctionNames rather than being spelled out here so that the one
        // set decides both this dispatch and what SHOW FUNCTIONS lists (via allDispatchableNames()); these
        // three never reach the registry maps, which is why the listing used to miss them entirely.
        if (HigherOrderFunctionNames.contains(funcName) && hasLambdaArgument(expr)) {
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
        rejectNonVariantArgumentInStrictFunction(funcName, expr.getArguments());
        rejectNonBooleanArgumentInStrictFunction(funcName, expr.getArguments());
        rejectStrictArgumentFamilies(funcName, expr.getArguments(), expr);

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
            // Snowflake rejects wrong arities at compile time (live-verified: "not enough arguments
            // for function [LOG(10)], expected 2, got 1"); enforce the declared bounds the same way.
            if (argValues.size() < func.getMinArgCount()) {
                throw new RuntimeException("not enough arguments for function ["
                    + AstPrinterVisitor.print(expr) + "], expected " + func.getMinArgCount()
                    + ", got " + argValues.size());
            }
            if (func.getMaxArgCount() >= 0 && argValues.size() > func.getMaxArgCount()) {
                // No comma after the bracket in the too-many form — live-verified:
                // "too many arguments for function [COMPRESS('hello', 'snappy', 'x')] expected 2, got 3".
                throw new RuntimeException("too many arguments for function ["
                    + AstPrinterVisitor.print(expr) + "] expected " + func.getMaxArgCount()
                    + ", got " + argValues.size());
            }
            return func.evaluate(readsVariantJsonNull(func, funcName)
                ? argValues : VariantJsonNulls.asScalarArgs(argValues));
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
                rejectStructuredUdfArgument(udf, funcName, expr.getArguments(), expr.getArgumentNames());
                final List<Object> callArgs = expr.getArgumentNames() != null
                    ? reorderNamedArgs(expr.getArgumentNames(), argValues, udf.getParameters(), funcName)
                    : argValues;
                return evaluateUserDefinedFunction(udf, callArgs);
            }
        }

        throw new RuntimeException("Unknown function: " + funcName);
    }

    /**
     * Non-semi-structured built-ins that still read a VARIANT JSON null as a VALUE, because their whole
     * job is to compare or choose between values rather than to compute over them. Live-verified
     * with {@code jn = PARSE_JSON('{"b":null}'):b}: {@code EQUAL_NULL(jn, NULL)} is FALSE (so
     * the JSON null must reach it un-nulled), {@code NULLIF(jn, NULL)} returns the JSON null, and
     * {@code GREATEST(jn, TO_VARIANT(1))} is the JSON null while {@code LEAST(...)} is 1 — a JSON null
     * orders above numbers. IFF / COALESCE / NVL / IFNULL / NVL2 never reach here: they short-circuit
     * above and return their chosen branch untouched.
     */
    private static final Set<String> VALUE_READING_FUNCTIONS = new HashSet<>(Arrays.asList(
        "EQUAL_NULL", "NULLIF", "LEAST", "GREATEST", "LEAST_IGNORE_NULLS", "GREATEST_IGNORE_NULLS",
        "DECODE"));

    /**
     * Whether a built-in receives a VARIANT JSON null unchanged, rather than reading it as SQL NULL.
     *
     * <p>Everything in the {@code functions.scalar.semistructured} package is variant-aware by
     * construction — that package IS the semi-structured surface (TYPEOF, IS_NULL_VALUE, TO_JSON, the
     * GET / ARRAY_* / OBJECT_* / IS_* / AS_* families), and live confirms those see the JSON
     * null itself: {@code TYPEOF(jn)} is 'NULL_VALUE', {@code ARRAY_CONSTRUCT(jn)} is {@code [null]},
     * {@code OBJECT_CONSTRUCT('k',jn)} is {@code {"k":null}}. Every other built-in is a scalar consumer
     * and reads it as SQL NULL — live, {@code UPPER(jn)}, {@code LENGTH(jn)}, {@code ABS(jn)},
     * {@code TO_VARCHAR(jn)}, {@code CONCAT(jn,'x')} and {@code DATEADD(day, jn, d)} are all SQL NULL.
     *
     * <p>Defaulting to the scalar reading is the safe direction: a family that should have been listed
     * here merely keeps the engine's long-standing behavior of collapsing the JSON null.
     */
    private static boolean readsVariantJsonNull(final BuiltInFunction func, final String funcName) {
        final Package pkg = func.getClass().getPackage();
        if (pkg != null && pkg.getName().endsWith(".functions.scalar.semistructured")) {
            return true;
        }
        return VALUE_READING_FUNCTIONS.contains(funcName);
    }

    private Function resolveOverloadedFunction(final Schema schema, final String funcName, final List<Object> argValues) {
        return udfInvoker.resolveOverloadedFunction(schema, funcName, argValues);
    }

    /**
     * A STRUCTURED type binds to a user-defined function parameter only when the parameter declares the
     * SAME structured type, and a structured value is rejected by a plainly semi-structured parameter
     * just as firmly. Live-verified against {@code f(o OBJECT(x VARCHAR))}: a plain
     * {@code OBJECT_CONSTRUCT('x','a')} fails "Invalid argument types for function 'F': (OBJECT)", a
     * {@code PARSE_JSON} argument fails with {@code (VARIANT)}, a wider {@code OBJECT(x VARCHAR, y INT)}
     * fails too, and only {@code CAST(... AS OBJECT(x VARCHAR))} binds; symmetrically
     * {@code f(o OBJECT)} REJECTS a structured {@code OBJECT(x VARCHAR(134217728))} argument. Field
     * lengths do not matter ({@code OBJECT(x VARCHAR(10))} binds to {@code OBJECT(x VARCHAR)}), field
     * base types do ({@code OBJECT(x INT)} does not). A bare NULL argument is accepted.
     */
    private void rejectStructuredUdfArgument(final Function udf, final String funcName,
                                             final List<Expression> args, final List<String> argumentNames) {
        if (udf == null || argumentNames != null) {
            return;   // named arguments are reordered later: positions are unreliable here
        }
        final List<Parameter> params = udf.getParameters();
        for (int i = 0; i < args.size() && i < params.size(); i++) {
            if (args.get(i) instanceof SpreadExpression) {
                return;   // a spread splices values: positions are unreliable
            }
            final DataType paramType = params.get(i).getDataType();
            final DataType argType = typeInferencer.infer(args.get(i));
            if (argType == null) {
                continue;   // undetermined (or an untyped NULL): the value decides
            }
            final boolean structuredParam = StructuredTypes.isStructured(paramType);
            final boolean structuredArg = StructuredTypes.isStructured(argType);
            if (!structuredParam && !structuredArg) {
                continue;
            }
            if (structuredParam && StructuredTypes.sameBindingShape(argType, paramType)) {
                continue;
            }
            if (!structuredParam && !(paramType instanceof ObjectType) && !(paramType instanceof ArrayType)
                    && !(paramType instanceof VariantType)) {
                continue;   // only the semi-structured parameter families reject a structured argument
            }
            throw new RuntimeException("Invalid argument types for function '" + funcName + "': ("
                + strictArgTypeList(args) + ")");
        }
    }

    /**
     * The single user-defined function {@code funcName} with {@code argCount} parameters, for the
     * plan-time argument check — null when it cannot be pinned down (no such function, or several
     * overloads of that arity, which runtime resolution decides between).
     */
    private Function resolveUdfForValidation(final String funcName, final int argCount) {
        if (catalog == null) {
            return null;
        }
        try {
            final String currentDatabase = catalog.getCurrentDatabase();
            if (currentDatabase == null) {
                return null;
            }
            final QualifiedName name = QualifiedName.parse(funcName);
            final Schema schema;
            if (name.size() == 2) {
                schema = catalog.getDatabase(currentDatabase).getSchema(name.part(0));
            } else if (name.size() == 3) {
                schema = catalog.getDatabase(name.part(0)).getSchema(name.part(1));
            } else {
                final String schemaName = catalog.getCurrentSchema();
                schema = schemaName != null
                    ? catalog.getDatabase(currentDatabase).getSchema(schemaName) : null;
            }
            if (schema == null) {
                return null;
            }
            Function match = null;
            for (final Function candidate : schema.getFunctionOverloads(name.last())) {
                if (candidate.getParameters().size() == argCount) {
                    if (match != null) {
                        return null;
                    }
                    match = candidate;
                }
            }
            return match;
        } catch (final RuntimeException e) {
            return null;
        }
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
        rejectFileCastSource(expr);
        rejectGeoCastSource(expr);
        rejectStructuredTextCastSource(expr);
        rejectNonStringTryCastSource(expr);
        rejectIllegalStructuredCast(expr);
        rejectIllegalVectorCast(expr);
        Object value = expr.getExpression().accept(this);
        if (isVariantJsonNullCast(expr, value)) {
            return null;
        }
        if (expr.isTryMode()) {
            // TRY_CAST: a conversion that would fail (e.g. a non-numeric string to NUMBER) yields NULL.
            try {
                return convertToDeclaredTarget(expr, value);
            } catch (final RuntimeException e) {
                return null;
            }
        }
        return convertToDeclaredTarget(expr, value);
    }

    /**
     * The cast's conversion. A {@code VECTOR(t, n)} target converts the ARRAY value straight into a
     * {@link VectorValue}: routing it through the text-driven scalar caster would drop the declared
     * element type and dimension, which are exactly what the conversion (and its errors) depend on.
     */
    private Object convertToDeclaredTarget(final CastExpression expr, final Object value) {
        if (expr.getDeclaredTarget() instanceof VectorType) {
            return value == null ? null : VectorValue.cast(value, (VectorType) expr.getDeclaredTarget());
        }
        return applyStructuredTarget(expr, castValue(value, expr.getTargetType()));
    }

    /**
     * Reshape a cast's result to a STRUCTURED target — the declared field types, and the
     * {@code RENAME FIELDS} / {@code ADD FIELDS} field mapping. A plain target passes straight through,
     * so ordinary casts keep their existing behaviour exactly.
     */
    private Object applyStructuredTarget(final CastExpression expr, final Object value) {
        if (!StructuredTypes.isStructured(expr.getDeclaredTarget())) {
            return value;
        }
        return StructuredCast.apply(value, expr.getDeclaredTarget(), expr.getFieldsModifier(),
            typeInferencer.infer(expr.getExpression()));
    }

    /**
     * Snowflake's compile-time rules for a cast to a STRUCTURED type: the {@code RENAME FIELDS} /
     * {@code ADD FIELDS} modifiers need a structured source and target of the same family, and a
     * modifier-free structured-to-structured OBJECT cast must keep the same field layout. Called both
     * per row and from the plan-time walk, so it fires over an empty input too.
     */
    private void rejectIllegalStructuredCast(final CastExpression expr) {
        final DataType structuredTarget = StructuredTypes.isStructured(expr.getDeclaredTarget())
            ? expr.getDeclaredTarget() : null;
        if (expr.getFieldsModifier() == CastFieldsModifier.NONE && structuredTarget == null) {
            return;
        }
        final Expression source = expr.getExpression();
        final DataType sourceType = typeInferencer.infer(source);
        final boolean untypedNull = sourceType == null && source instanceof LiteralExpression
            && ((LiteralExpression) source).getType() == LiteralType.NULL;
        final String targetText = structuredTarget != null
            ? StructuredTypes.describe(structuredTarget)
            : castTargetTypeText(expr.getTargetType());
        StructuredCast.validate(expr.getFieldsModifier(), structuredTarget, sourceType,
            untypedNull, strictArgTypeText(source), targetText);
    }

    /**
     * A VECTOR value casts only to its OWN vector type: live-verified,
     * {@code [1,2,3]::VECTOR(FLOAT,3)::VECTOR(FLOAT,3)} is the unchanged vector while both
     * {@code ::VECTOR(INT,3)} and {@code ::VECTOR(FLOAT,2)} are the compile error "Invalid argument
     * types for function 'CAST': (VECTOR(FLOAT, 3))" — which names only the SOURCE type. A non-vector
     * source is unconstrained here; its elements are checked when the value is converted.
     */
    private void rejectIllegalVectorCast(final CastExpression expr) {
        if (!(expr.getDeclaredTarget() instanceof VectorType)) {
            return;
        }
        final DataType sourceType = typeInferencer.infer(expr.getExpression());
        if (sourceType instanceof VectorType && !sourceType.equals(expr.getDeclaredTarget())) {
            throw new RuntimeException("Invalid argument types for function 'CAST': ("
                + sourceType.getName() + ")");
        }
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
        TEMPORAL_STRICT_ARGS.put("MONTHS_BETWEEN", new int[]{0, 1});
        // DAYNAME / MONTHNAME / NEXT_DAY / PREVIOUS_DAY are NOT compile-strict in Snowflake
        // (live-verified: each compiles over a VARCHAR column and evaluates per row).
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
            if (position >= args.size()) {
                continue;
            }
            // Every statically-VARCHAR argument is rejected — a declared column, a VARCHAR-returning
            // function like UPPER(col), AND a bare string CONSTANT (live-verified: DAYOFMONTH,
            // DAY, YEAR, EXTRACT, DATE_TRUNC and LAST_DAY all reject '2024-04-08'; the coercing
            // functions such as DATEADD are simply not in this map). Undetermined types stay
            // accepted (derived columns, subqueries): the engine represents some internal values
            // as strings, so a runtime value check would wrongly flag genuine temporal results.
            final Expression arg = args.get(position);
            final DataType inferred = typeInferencer.infer(arg);
            if (inferred instanceof StringType) {
                // Live-verified message shape: the whole EXTRACT family (EXTRACT, DATE_PART, the
                // part extractors like DAYOFYEAR, and MONTHS_BETWEEN) reports "Function EXTRACT ...";
                // DATE_TRUNC and LAST_DAY report their own names; the VARCHAR carries its length.
                final String reportedName =
                    funcName.equals("DATE_TRUNC") || funcName.equals("LAST_DAY") ? funcName : "EXTRACT";
                int maxLength = ((StringType) inferred).getMaxLength();
                if (arg instanceof LiteralExpression && ((LiteralExpression) arg).getValue() != null) {
                    maxLength = String.valueOf(((LiteralExpression) arg).getValue()).length();
                }
                throw new RuntimeException("Function " + reportedName + " does not support VARCHAR("
                    + (maxLength > 0 ? maxLength : 16777216) + ") argument type");
            }
        }
    }

    FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    /**
     * Plan-time strict-argument validation: walk {@code expr} and run the strict argument checks
     * for every function call found, WITHOUT evaluating anything — so a Snowflake argument-type
     * error fires even when the input has zero rows, matching compile-time rejection. The walk
     * descends through the containers a function call realistically nests in (function arguments,
     * binary operations, casts); a spread argument skips its call (positions are unreliable).
     */
    public void validateStrictArguments(final Expression expr) {
        if (expr instanceof ColumnReferenceExpression) {
            validateColumnReferenceScope((ColumnReferenceExpression) expr);
            return;
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            boolean hasSpread = false;
            for (final Expression arg : call.getArguments()) {
                if (arg instanceof SpreadExpression) {
                    hasSpread = true;
                }
            }
            if (!hasSpread && call.getNameExpression() == null) {
                final String funcName = call.getFunctionName().toUpperCase();
                rejectVarcharColumnInTemporalFunction(funcName, call.getArguments(),
                    new ArrayList<Object>(call.getArguments()));
                rejectNonVariantArgumentInStrictFunction(funcName, call.getArguments());
                rejectNonBooleanArgumentInStrictFunction(funcName, call.getArguments());
                rejectStrictArgumentFamilies(funcName, call.getArguments(), call);
                if (functionRegistry.getFunction(funcName) == null) {
                    rejectStructuredUdfArgument(resolveUdfForValidation(funcName, call.getArguments().size()),
                        funcName, call.getArguments(), call.getArgumentNames());
                }
            }
            // Argument position gates the date/time-unit bareword exemption in the scope check:
            // DATEADD(HOUR, …) is legal where a top-level `WHERE year = 1` is not.
            final boolean outerInsideArgs = strictWalkInsideFunctionArgs;
            strictWalkInsideFunctionArgs = true;
            try {
                for (final Expression arg : call.getArguments()) {
                    validateStrictArguments(arg);
                }
            } finally {
                strictWalkInsideFunctionArgs = outerInsideArgs;
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            rejectFileConcatOperand((BinaryOperationExpression) expr);
            rejectSemiStructuredConcatOperand((BinaryOperationExpression) expr);
            rejectSemiStructuredArithmeticOperand((BinaryOperationExpression) expr);
            rejectGeoComparisonOperand((BinaryOperationExpression) expr);
            validateStrictArguments(((BinaryOperationExpression) expr).getLeft());
            validateStrictArguments(((BinaryOperationExpression) expr).getRight());
            return;
        }
        if (expr instanceof InExpression) {
            rejectGeoInOperand((InExpression) expr);
            validateStrictArguments(((InExpression) expr).getValue());
            if (((InExpression) expr).getValues() != null) {
                for (final Expression value : ((InExpression) expr).getValues()) {
                    validateStrictArguments(value);
                }
            }
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            rejectSemiStructuredNegateOperand((UnaryOperationExpression) expr);
            validateStrictArguments(((UnaryOperationExpression) expr).getOperand());
            return;
        }
        if (expr instanceof CastExpression) {
            rejectFileCastSource((CastExpression) expr);
            rejectGeoCastSource((CastExpression) expr);
            rejectStructuredTextCastSource((CastExpression) expr);
            rejectNonStringTryCastSource((CastExpression) expr);
            rejectIllegalStructuredCast((CastExpression) expr);
            validateStrictArguments(((CastExpression) expr).getExpression());
            return;
        }
        if (expr instanceof ObjectAccessExpression) {
            // Colon path access is GET sugar: a declared-VARCHAR base is a compile error even when
            // the input has zero rows, so the plan-time walk enforces it too.
            rejectStringBaseInPathAccess((ObjectAccessExpression) expr);
            validateStrictArguments(((ObjectAccessExpression) expr).getBase());
            return;
        }
        if (expr instanceof LikeAnyAllExpression) {
            final LikeAnyAllExpression like = (LikeAnyAllExpression) expr;
            validateStrictArguments(like.getSubject());
            for (final Expression pattern : like.getPatterns()) {
                validateStrictArguments(pattern);
            }
        }
    }

    /**
     * Functions whose listed argument positions require a semi-structured (VARIANT / OBJECT / ARRAY)
     * value in Snowflake, with no implicit VARCHAR coercion: a non-NULL literal or a reference to a
     * declared non-semi-structured base-table column is an argument-type error there (live-verified:
     * {@code TYPEOF('x')}, {@code GET('{"a":1}', 'a')} and {@code TO_JSON('abc')} all fail on
     * Snowflake). Path expressions, function results and derived columns stay accepted — the typed
     * runtime value decides their behavior.
     */
    private static final Map<String, int[]> VARIANT_STRICT_ARGS = new HashMap<>();
    static {
        VARIANT_STRICT_ARGS.put("TYPEOF", new int[]{0});
        VARIANT_STRICT_ARGS.put("GET", new int[]{0});
        VARIANT_STRICT_ARGS.put("GET_PATH", new int[]{0});
        VARIANT_STRICT_ARGS.put("GET_IGNORE_CASE", new int[]{0});
        VARIANT_STRICT_ARGS.put("TO_JSON", new int[]{0});
        VARIANT_STRICT_ARGS.put("TO_XML", new int[]{0});
        VARIANT_STRICT_ARGS.put("XMLGET", new int[]{0});
        // ARRAY_POSITION's needle must be VARIANT: a bare VARCHAR rejects while a NUMBER coerces
        // (live: ARRAY_POSITION('b', [..]) errors, ARRAY_POSITION(2, [..]) works).
        VARIANT_STRICT_ARGS.put("ARRAY_POSITION", new int[]{0});
        // Same needle rule across the array family — live-verified on a real account:
        // ARRAY_CONTAINS('a', [..]) errors "(VARCHAR(1), ARRAY)", as do a DATE and a BINARY needle,
        // while a NUMBER, a BOOLEAN, an ARRAY, a NULL and an explicit ::VARIANT all work. The value
        // ARRAY_REMOVE strips sits at position 1 (ARRAY_REMOVE([..], 'a') errors "(ARRAY,
        // VARCHAR(1))"), and ARRAYS_OVERLAP's second argument must itself be an array.
        VARIANT_STRICT_ARGS.put("ARRAY_CONTAINS", new int[]{0});
        VARIANT_STRICT_ARGS.put("ARRAY_REMOVE", new int[]{1});
        VARIANT_STRICT_ARGS.put("ARRAYS_OVERLAP", new int[]{1});
        // IS_VECTOR answers for anything VARIANT can hold plus a VECTOR itself, and REJECTS the rest:
        // live, IS_VECTOR([1,2,3]) / {'a':1} / TO_VARIANT(..) / 1 / TRUE are all FALSE while
        // IS_VECTOR('abc') is "Invalid argument types for function 'IS_VECTOR': (VARCHAR(3))" and a DATE
        // argument fails the same way — exactly this variant-coercibility rule.
        VARIANT_STRICT_ARGS.put("IS_VECTOR", new int[]{0});
    }

    /**
     * Functions whose argument must be BOOLEAN-coercible at COMPILE time: Snowflake rejects both
     * VARCHAR and NUMBER conditions (live: IFF('true', ..) and IFF(1, ..) both error with
     * "Invalid argument types for function 'IFF'"). Only BOOLEAN and VARIANT compile.
     */
    private static final Map<String, int[]> BOOLEAN_STRICT_ARGS = new HashMap<>();
    static {
        BOOLEAN_STRICT_ARGS.put("IFF", new int[]{0});
    }

    /**
     * Functions that REJECT a BOOLEAN argument: the BOOL* family is defined over NUMBER / VARCHAR /
     * VARIANT truthiness, not over the BOOLEAN type (live-verified: {@code BOOLAND(1, 0)} is FALSE
     * but {@code BOOLAND(TRUE, FALSE)} errors "Invalid argument types for function 'BOOLAND':
     * (BOOLEAN, BOOLEAN)"). Every listed position is checked.
     */
    private static final Map<String, int[]> BOOLEAN_REJECTING_ARGS = new HashMap<>();
    static {
        BOOLEAN_REJECTING_ARGS.put("BOOLAND", new int[]{0, 1});
        BOOLEAN_REJECTING_ARGS.put("BOOLOR", new int[]{0, 1});
        BOOLEAN_REJECTING_ARGS.put("BOOLXOR", new int[]{0, 1});
        BOOLEAN_REJECTING_ARGS.put("BOOLNOT", new int[]{0});
    }

    /**
     * Functions whose listed positions must be BINARY: the raw-crypto pair takes byte payloads, and
     * a hex VARCHAR is NOT implicitly converted (live-verified: {@code ENCRYPT_RAW('68656C6C6F',
     * '00010203', '010203')} errors "Invalid argument types for function 'ENCRYPT_RAW':
     * (VARCHAR(10), VARCHAR(8), VARCHAR(6))" — use TO_BINARY).
     */
    private static final Map<String, int[]> BINARY_STRICT_ARGS = new HashMap<>();
    static {
        // value, key, iv [, aad] are BINARY; the optional METHOD argument ('AES-GCM') is a VARCHAR,
        // so it is excluded — ENCRYPT_RAW(v, k, iv [, aad, method]) and
        // DECRYPT_RAW(v, k, iv, tag) / DECRYPT_RAW(v, k, iv, aad, method, tag).
        BINARY_STRICT_ARGS.put("ENCRYPT_RAW", new int[]{0, 1, 2, 3});
        BINARY_STRICT_ARGS.put("DECRYPT_RAW", new int[]{0, 1, 2, 3, 5});
    }

    /**
     * Functions that require a MAP and reject a plain OBJECT (live-verified: {@code
     * MAP_CAT(OBJECT_CONSTRUCT('a','1'), OBJECT_CONSTRUCT('b','2'))} errors "Invalid argument types
     * for function 'MAP_CAT': (OBJECT, OBJECT)", while the same values cast {@code ::MAP(VARCHAR,
     * VARCHAR)} work). A cast to MAP infers as undetermined, so it passes.
     */
    /**
     * The TRY_TO_&lt;TYPE&gt; family and the Snowflake target type each reports in its rejection
     * message. Snowflake implements these as TRY_CAST, so they inherit its rule — the source must
     * be VARCHAR (live-verified: {@code TRY_TO_NUMBER(123)} errors "Function TRY_CAST cannot be used
     * with arguments of types NUMBER(3,0) and NUMBER(38,0)", {@code TRY_TO_DECIMAL(NULL)} errors
     * with "NULL and NUMBER(38,0)", while {@code TRY_TO_DECIMAL(NULL::VARCHAR)} returns NULL).
     */
    private static final Map<String, String> TRY_TO_TARGET_TYPES = new HashMap<>();
    static {
        TRY_TO_TARGET_TYPES.put("TRY_TO_NUMBER", "NUMBER(38,0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_DECIMAL", "NUMBER(38,0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_NUMERIC", "NUMBER(38,0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_DOUBLE", "FLOAT");
        TRY_TO_TARGET_TYPES.put("TRY_TO_BOOLEAN", "BOOLEAN");
        TRY_TO_TARGET_TYPES.put("TRY_TO_BINARY", "BINARY(67108864)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_DATE", "DATE");
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIME", "TIME(9)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIMESTAMP", "TIMESTAMP_NTZ(9)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIMESTAMP_NTZ", "TIMESTAMP_NTZ(9)");
    }

    /**
     * The MAP-requiring argument positions. These positions do not merely refuse a few families — they
     * accept a {@code MAP(k, v)} and NOTHING ELSE, which is why the rule they drive
     * ({@link #rejectNonMapArgumentInStrictFunction}) is phrased as a requirement rather than as a
     * reject-list. Live, one call per family: {@code MAP_KEYS(OBJECT_CONSTRUCT('a',1))} is
     * "Invalid argument types for function 'MAP_KEYS': (OBJECT)", and the VARIANT
     * ({@code PARSE_JSON('{"a":1}')}), ARRAY ({@code ARRAY_CONSTRUCT(1,2)}), STRUCTURED OBJECT
     * ({@code OBJECT(x INT)} column), VARCHAR ({@code 'abc'}), NUMBER ({@code 1}) and untyped-NULL
     * spellings each give the same sentence with their own type named. A reject-list would have had to
     * grow a class per family and would still have accepted whatever was left off it.
     *
     * <p>{@code MAP_CONSTRUCT} is deliberately absent: it BUILDS a map from ordinary values, so it has
     * no MAP-typed position at all.
     */
    private static final Map<String, int[]> MAP_STRICT_ARGS = new HashMap<>();
    static {
        MAP_STRICT_ARGS.put("MAP_CAT", new int[]{0, 1});
        MAP_STRICT_ARGS.put("MAP_KEYS", new int[]{0});
        MAP_STRICT_ARGS.put("MAP_SIZE", new int[]{0});
        MAP_STRICT_ARGS.put("MAP_ENTRIES", new int[]{0});
        MAP_STRICT_ARGS.put("MAP_CONTAINS_KEY", new int[]{1});
        MAP_STRICT_ARGS.put("MAP_DELETE", new int[]{0});
        MAP_STRICT_ARGS.put("MAP_INSERT", new int[]{0});
        MAP_STRICT_ARGS.put("MAP_PICK", new int[]{0});
    }

    private void rejectNonVariantArgumentInStrictFunction(final String funcName, final List<Expression> args) {
        final int[] positions = VARIANT_STRICT_ARGS.get(funcName);
        if (positions == null) {
            return;
        }
        for (final int position : positions) {
            if (position >= args.size()) {
                continue;
            }
            final Expression arg = args.get(position);
            final DataType inferred = typeInferencer.infer(arg);
            // Live-verified coercion rule: NUMBER and BOOLEAN arguments coerce to VARIANT
            // (TYPEOF(123) is INTEGER on Snowflake); VARCHAR, BINARY, temporals — and GEOGRAPHY /
            // GEOMETRY (TYPEOF(TO_GEOGRAPHY(..)) is rejected live) — are not variant-coercible.
            if (inferred instanceof StringType || inferred instanceof BinaryType
                    || inferred instanceof DateTimeType
                    || inferred instanceof GeographyType || inferred instanceof GeometryType
                    || inferred instanceof FileType) {
                throw new RuntimeException("Invalid argument types for function '" + funcName
                    + "': (" + strictArgTypeList(args) + ")");
            }
        }
    }

    /**
     * Boolean-position strictness: Snowflake rejects VARCHAR and NUMBER conditions at COMPILE time
     * (live: "Invalid argument types for function 'IFF': (VARCHAR(4), VARCHAR(1), VARCHAR(1))").
     */
    private void rejectNonBooleanArgumentInStrictFunction(final String funcName, final List<Expression> args) {
        final int[] positions = BOOLEAN_STRICT_ARGS.get(funcName);
        if (positions == null) {
            return;
        }
        for (final int position : positions) {
            if (position >= args.size()) {
                continue;
            }
            final DataType inferred = typeInferencer.infer(args.get(position));
            if (inferred instanceof StringType || inferred instanceof NumericType
                    || inferred instanceof BinaryType || inferred instanceof DateTimeType) {
                throw new RuntimeException("Invalid argument types for function '" + funcName
                    + "': (" + strictArgTypeList(args) + ")");
            }
        }
    }

    /**
     * Predicate-position strictness: Snowflake rejects a WHERE condition whose static type is
     * VARCHAR or NUMBER (live: "Invalid data type [VARCHAR(10)] for predicate [STRICT_T.S10]",
     * "[NUMBER(38,0)] for predicate [T.N]", and "[VARCHAR(4)] for predicate ['true']" for a
     * literal). BOOLEAN, VARIANT and undetermined types pass.
     */
    public void validatePredicateType(final Expression predicate) {
        final DataType inferred = typeInferencer.infer(predicate);
        if (inferred instanceof StringType || inferred instanceof NumericType) {
            throw new RuntimeException("Invalid data type [" + strictArgTypeText(predicate)
                + "] for predicate [" + predicateDisplayText(predicate) + "]");
        }
    }

    /** How Snowflake names the predicate in the error: TABLE.COLUMN for a column, 'text' for a literal. */
    private String predicateDisplayText(final Expression predicate) {
        if (predicate instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression column = (ColumnReferenceExpression) predicate;
            final String owner = column.getTableName() != null ? column.getTableName()
                : (table != null ? table.getName() : "");
            final String columnName = column.getColumnName();
            return owner.isEmpty() ? columnName.toUpperCase()
                : owner.toUpperCase() + "." + columnName.toUpperCase();
        }
        if (predicate instanceof LiteralExpression
                && ((LiteralExpression) predicate).getType() == LiteralType.STRING) {
            return "'" + ((LiteralExpression) predicate).getValue() + "'";
        }
        return AstPrinterVisitor.print(predicate);
    }

    /** Runs the BOOLEAN-rejecting, BINARY-requiring, MAP-requiring and TO_CHAR-format rules. */
    private void rejectStrictArgumentFamilies(final String funcName, final List<Expression> args,
                                              final Expression call) {
        rejectArgumentTypes(funcName, args, BOOLEAN_REJECTING_ARGS, new Class<?>[] {BooleanType.class});
        rejectArgumentTypes(funcName, args, BINARY_STRICT_ARGS,
            new Class<?>[] {StringType.class, NumericType.class, BooleanType.class, DateTimeType.class});
        rejectNonMapArgumentInStrictFunction(funcName, args);
        rejectUntypedNullMapArgument(funcName, args);
        rejectNullArgumentInMapConstruct(funcName, args);
        rejectFormatOverStringInToChar(funcName, args, call);
        rejectNonStringTryToSource(funcName, args);
        rejectNonVectorArgument(funcName, args);
        rejectFileArgument(funcName, args);
        rejectGeoArgument(funcName, args);
        rejectSemiStructuredOrderingAggregate(funcName, args);
        rejectSemiStructuredArgument(funcName, args, call);
    }

    /**
     * A function that reads its argument as TEXT or as a NUMBER does not take a statically OBJECT- or
     * ARRAY-typed one. Live, {@code UPPER(o)} over an OBJECT column is "Invalid argument
     * types for function 'UPPER': (OBJECT)" — Frostlake used to accept it and hand back the JSON text
     * with its KEYS uppercased, {@code {"K":"V"}}, which still looked like an object — and
     * {@code SUM(o)} is the same sentence for 'SUM', where Frostlake used to answer {@code 0.0}, a
     * plausible number a caller could then average or compare. The whole surface behaves this way:
     * {@code LENGTH}, {@code SUBSTR}, {@code REPLACE}, {@code SPLIT_PART}, {@code CONCAT},
     * {@code LISTAGG}, {@code ABS}, {@code ROUND}, {@code POWER}, {@code SUM}, {@code AVG},
     * {@code MEDIAN}, {@code STDDEV} and the rest were each measured.
     *
     * <p>A statically FILE-typed argument is refused by the same surface and with the same sentences —
     * live, {@code LENGTH(f)}, {@code UPPER(f)} and {@code SUBSTR(f, 1, 3)} name 'LENGTH',
     * 'UPPER' and 'SUBSTR' over "(FILE)", {@code SUM(f)} names 'SUM', {@code MEDIAN(f)} is
     * "incompatible types: [FILE] and [NUMBER(9,0)]" and {@code STDDEV(f)} reports "'*': (FILE,
     * FILE)". Frostlake used to accept every one of them and answer from the descriptor JSON, so
     * {@code LENGTH(f)} returned its character count and {@code SUM(f)} a number. It is asked as a
     * SEPARATE question ({@link BuiltInFunction#fileRejection}) because the two types diverge for three
     * functions in both directions; see that method.
     *
     * <p>Which positions those are, and WHICH of the live message shapes each uses, is DECLARED
     * BY THE FUNCTION ({@link BuiltInFunction#semiStructuredRejection}), never listed here: a table
     * keyed by name would miss every alias — SUBSTR and SUBSTRING are one registered object, and so
     * are VARIANCE_POP and VAR_POP — and could not say that {@code ARRAY_TO_STRING} takes an ARRAY in
     * position 0 and refuses one in position 1. A function that declares nothing constrains nothing,
     * so the rule only ever covers what has been measured.
     *
     * <p>The type read is the DECLARED one, through the conditionals: live rejects
     * {@code UPPER(IFF(TRUE, o, o))}, {@code SUM(IFF(TRUE, o, o))} and {@code SUM(x)} over
     * {@code (SELECT o AS x FROM t)} alike, and rejects on an EMPTY input too. A VARIANT is never
     * rejected, even holding an object, and neither is an explicit conversion —
     * {@code UPPER(o::VARCHAR)} and {@code SUM(a[0])} both pass.
     *
     * <p>The desugared-name divergence is now PARTIALLY copied:
     * the bare {@code AVG(o)} reports 'SUM' and the bare {@code APPROX_COUNT_DISTINCT(g)} reports
     * 'HLL_ACCUMULATE', exactly as live does, while {@code AVG(DISTINCT o)} and {@code AVG(o) OVER
     * (…)} keep reporting 'AVG' — also exactly as live does (see
     * {@link #reportedFunctionName}). The text-function rewrites live performs — {@code LEFT(o, 1)}
     * as 'SUBSTR', {@code REPEAT(o, 2)} as 'LENGTH', {@code DIV0NULL(o, 2)} as 'DIV0' with a
     * rewritten argument list — remain uncopied: no test measures them and the argument-list rewrite
     * would be inventing a plan Frostlake does not have.
     */
    private void rejectSemiStructuredArgument(final String funcName, final List<Expression> args,
                                              final Expression call) {
        final BuiltInFunction function = registeredFunction(funcName);
        if (function == null) {
            return;
        }
        for (int position = 0; position < args.size(); position++) {
            final DataType file = fileArgumentType(args.get(position));
            if (file != null) {
                final SemiStructuredRejection onFile = function.fileRejection(position);
                if (onFile != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onFile, funcName, args, position, file, call));
                }
                continue;
            }
            final DataType geo = geoArgumentType(args.get(position));
            if (geo != null) {
                final SemiStructuredRejection onGeo = function.geoRejection(position);
                if (onGeo != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onGeo, funcName, args, position, geo, call));
                }
                continue;
            }
            final SemiStructuredRejection declared = function.semiStructuredRejection(position);
            final SemiStructuredRejection structuredOnly = function.structuredRejection(position);
            if (declared == SemiStructuredRejection.NONE
                    && structuredOnly == SemiStructuredRejection.NONE) {
                continue;
            }
            final DataType inferred = typeInferencer.inferSemiStructured(args.get(position));
            if (inferred == null) {
                continue;
            }
            final SemiStructuredRejection rejection = chooseRejection(declared, structuredOnly, inferred);
            if (rejection != SemiStructuredRejection.NONE) {
                throw new RuntimeException(
                    semiStructuredRejectionMessage(rejection, funcName, args, position, inferred, call));
            }
        }
    }

    /**
     * The FILE type of an expression, or null when it is not statically a FILE. A FILE is asked about
     * FIRST and separately because the two questions have different answers for the same function —
     * {@code OBJECT_AGG}'s value half takes an OBJECT and refuses a FILE, {@code ARRAY_AGG} does the
     * reverse — and because {@link TypeInferencer#inferSemiStructured} deliberately never reports one:
     * it exists to answer "is this OBJECT-like?", and FILE is a type of its own everywhere it matters.
     *
     * <p>Read through the DECLARED type, exactly as the semi-structured rule is: live
     * rejects {@code UPPER(x)} over {@code (SELECT f AS x FROM ft)}, the CTE spelling of the same
     * query, and {@code LENGTH(IFF(TRUE, f, f))} alike, and rejects on an EMPTY input too.
     */
    private DataType fileArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof FileType ? inferred : null;
    }

    /**
     * The GEOSPATIAL type of an expression, or null when it is not statically a GEOGRAPHY or a
     * GEOMETRY. Asked THIRD and separately for the same reason FILE is asked first: the answer for a
     * geo value differs from the semi-structured one in both directions, so a rule keyed on "is this
     * OBJECT-like?" would be wrong each way — live takes {@code GET(o, 'k')} and refuses
     * {@code GET(g, 'type')}, and takes {@code GROUP BY o} while refusing {@code GROUP BY g}.
     *
     * <p>{@link TypeInferencer#inferSemiStructured} deliberately never reports a geo type either,
     * even though both carry {@code TypeCategory.SEMI_STRUCTURED}: that method answers "is this
     * OBJECT / ARRAY / MAP?", which drives the messages that name a structured type in full.
     *
     * <p>Read through the DECLARED type exactly as the other two rules are: live rejects
     * {@code UPPER(x)} over {@code (SELECT g AS x FROM gt)}, the CTE spelling of the same query, and
     * {@code GROUP BY IFF(TRUE, g, g)} alike, and rejects on an EMPTY input too.
     */
    private DataType geoArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return GeoTypes.isGeo(inferred) ? inferred : null;
    }

    /**
     * Which of a position's two SEMI-STRUCTURED declarations applies to the type actually inferred:
     * the semi-structured one whenever it says anything, since it already covers the structured types
     * too, and otherwise the structured-only one — but ONLY for a type that really is structured. That
     * last condition is the whole point of keeping the two apart: {@code ARRAY_AGG(o)} and
     * {@code ARRAY_AGG(so)} disagree live, so a rule that fired on "semi-structured" would break the
     * plain case in the course of fixing the structured one. A FILE never reaches here — it is asked
     * about first, and separately, for exactly the same reason.
     */
    private static SemiStructuredRejection chooseRejection(final SemiStructuredRejection declared,
                                                           final SemiStructuredRejection structuredOnly,
                                                           final DataType inferred) {
        if (declared != SemiStructuredRejection.NONE) {
            return declared;
        }
        return StructuredTypes.isStructured(inferred) ? structuredOnly : SemiStructuredRejection.NONE;
    }

    /**
     * The sentence live Snowflake produces for one refusal, chosen by the shape the function
     * declares. Every one was captured on the account and they carry different SQLSTATEs:
     * 42P13 for the argument-type list, 42846 for the incompatible-types pair, 22000 for the
     * "does not support" form and 22023 for the conversion form.
     */
    private String semiStructuredRejectionMessage(final SemiStructuredRejection rejection,
                                                  final String funcName, final List<Expression> args,
                                                  final int position, final DataType inferred,
                                                  final Expression call) {
        if (rejection == SemiStructuredRejection.INCOMPATIBLE_TYPES) {
            return "incompatible types: [" + semiStructuredTypeName(inferred) + "] and [NUMBER(9,0)]";
        }
        if (rejection == SemiStructuredRejection.UNSUPPORTED_ARGUMENT_TYPE
                || rejection == SemiStructuredRejection.UNSUPPORTED_KEY_ARGUMENT_TYPE) {
            // The constructors reuse the ordering aggregates' sentence, with " for keys" appended when
            // the offending value was used as an OBJECT_CONSTRUCT key (live carries a different vendor
            // code there too — 2270 rather than 2016).
            final String keys = rejection == SemiStructuredRejection.UNSUPPORTED_KEY_ARGUMENT_TYPE
                ? " for keys" : "";
            return "SQL compilation error:\nFunction " + funcName + " does not support "
                + semiStructuredTypeName(inferred) + " argument type" + keys;
        }
        if (rejection == SemiStructuredRejection.INVALID_TYPE_PARAMETER) {
            // The conversion shape quotes the whole CALL back rather than listing argument types, and
            // names the conversion parameter — which is the function as WRITTEN, so TO_CHAR and its
            // TO_VARCHAR synonym each report themselves. The call is rendered from the analysed plan
            // exactly as live renders it — a column comes out qualified and upper-cased,
            // "TO_VARCHAR(ST.SO)" — via the strict-message printer.
            return "SQL compilation error:\ninvalid type [" + funcName + "(" + renderedArguments(args)
                + ")] for parameter '" + funcName + "'";
        }
        if (rejection == SemiStructuredRejection.MULTIPLY_OPERANDS) {
            // Live never names the aggregate here: it reaches its internal sum of SQUARES first and
            // reports that multiplication, listing the one offending argument twice — "Invalid
            // argument types for function '*': (OBJECT, OBJECT)" for STDDEV(o) as for VARIANCE(o),
            // and "(ARRAY, ARRAY)" over an ARRAY column.
            final String operand = strictArgTypeText(args.get(position));
            return "Invalid argument types for function '*': (" + operand + ", " + operand + ")";
        }
        return "Invalid argument types for function '" + reportedFunctionName(funcName, isBareCall(call))
            + "': (" + strictArgTypeList(args) + ")";
    }

    /**
     * An argument list rendered back as SQL for the messages that quote a call rather than list its
     * types. EVERY argument appears, matching live: {@code TO_VARCHAR(so, 'x')} is reported as
     * "invalid type [TO_VARCHAR(ST.SO, 'x')]", format string included.
     */
    private String renderedArguments(final List<Expression> args) {
        final StringBuilder text = new StringBuilder();
        for (final Expression arg : args) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(strictText(arg));
        }
        return text.toString();
    }

    /**
     * The expression as a strictness message renders it: live prints the call from its analysed plan,
     * so a column reference comes out qualified with its source relation ({@code TO_VARCHAR(FK.F)},
     * {@code CAST(STT.SO AS …)}) — see {@link StrictMessagePrinter}.
     */
    String strictText(final Expression expr) {
        return expr.accept(new StrictMessagePrinter(this));
    }

    /**
     * The relation qualifier a strictness message prints before a BARE column reference, or null when
     * the owner is unknown or synthetic. The owner is found by the same search order the value read
     * uses; the qualifier is the FROM-clause key (the alias when one was written, else the table
     * name), upper-cased as live prints it. A derived relation's invented name ({@code joined}, the
     * FROM-less {@code DUMMY}, the resolver's empty shape name) is never printed — live has no such
     * name to print either, so those references stay bare.
     */
    String strictMessageQualifier(final ColumnReferenceExpression expr) {
        final String columnName = expr.getColumnName();
        if (multiTableAliasToTable != null) {
            for (final Map.Entry<String, Table> entry : multiTableAliasToTable.entrySet()) {
                if (entry.getValue() != null && entry.getValue().hasColumn(columnName)) {
                    return printableRelationName(entry.getKey());
                }
            }
        }
        if (multiTableAllTables != null) {
            for (final Table candidate : multiTableAllTables) {
                if (candidate != null && candidate.hasColumn(columnName)) {
                    return printableRelationName(candidate.getName());
                }
            }
        }
        if (table != null && table.hasColumn(columnName)) {
            return printableRelationName(table.getName());
        }
        return null;
    }

    /** The relation name upper-cased for a message, or null for a synthetic (unprintable) name. */
    private static String printableRelationName(final String name) {
        if (name == null || name.isEmpty()
                || name.equalsIgnoreCase("joined") || name.equalsIgnoreCase("DUMMY")) {
            return null;
        }
        return name.toUpperCase();
    }

    /**
     * The registered function behind a call, scalar first and then aggregate — LISTAGG, SUM and
     * MEDIAN live only in the aggregate map, and all three are covered by this rule.
     */
    private BuiltInFunction registeredFunction(final String funcName) {
        final BuiltInFunction scalar = functionRegistry.getFunction(funcName);
        return scalar != null ? scalar : functionRegistry.getAggregateFunction(funcName);
    }

    /**
     * The name Snowflake REPORTS for a refusing function, which is not always the one written —
     * live reports the function its plan REWRITES the call into: {@code CONCAT_WS('-', 'x', o)}
     * fails as 'CONCAT' (live); the BARE {@code AVG(o)} fails as 'SUM' with the SAME
     * single-argument type list ("Invalid argument types for function 'SUM': (OBJECT)"), over a FILE
     * as over an OBJECT; and the bare {@code APPROX_COUNT_DISTINCT(g)} fails as 'HLL_ACCUMULATE' —
     * measured live. The rewrite is BARE-form only: live reports
     * {@code AVG(DISTINCT o)} and {@code AVG(o) OVER (…)} as 'AVG' (measured), so those keep the
     * written name. Every other measured name reports itself.
     */
    private static String reportedFunctionName(final String funcName, final boolean bareCall) {
        if (funcName.equals("CONCAT_WS")) {
            return "CONCAT";
        }
        if (!bareCall) {
            return funcName;
        }
        switch (funcName) {
            case "AVG":
                return "SUM";
            case "APPROX_COUNT_DISTINCT":
                return "HLL_ACCUMULATE";
            default:
                return funcName;
        }
    }

    /** Whether the refusing call is the BARE aggregate form — the only form live desugars for the
     *  message; {@code AVG(DISTINCT o)} and {@code AVG(o) OVER (…)} keep reporting 'AVG' (measured
     * live). The windowed form is recognised by the walk marker the window evaluator
     *  sets — its re-formed call is otherwise indistinguishable from a bare one. */
    private boolean isBareCall(final Expression call) {
        return !windowedStrictWalk
            && call instanceof FunctionCallExpression
            && !((FunctionCallExpression) call).isDistinct();
    }

    /** Set while validating a WINDOW call re-formed without its OVER clause; see {@link #isBareCall}. */
    private boolean windowedStrictWalk;

    public void setWindowedStrictWalk(final boolean windowedStrictWalk) {
        this.windowedStrictWalk = windowedStrictWalk;
    }

    /**
     * The ordering aggregates do not order a semi-structured value. Live over a populated
     * OBJECT and ARRAY column, {@code MAX(o)} is "SQL compilation error:\nFunction MAX does not support
     * OBJECT argument type" (SQLSTATE 22000, vendor code 2016) and {@code MAX(a)} names ARRAY; a
     * structured column names its whole type ("OBJECT(x VARCHAR(16777216))", "ARRAY(NUMBER(38,0))",
     * "MAP(VARCHAR(16777216), NUMBER(38,0))"). Frostlake used to accept every one of these and hand
     * back the JSON text, so {@code MAX(o)} returned {@code {"k":"v2"}} — a silent stringification
     * that looked deliberate.
     *
     * <p>The rule reads the DECLARED type and never the runtime value, exactly as live does. That
     * distinction was measured rather than assumed: {@code MAX(v)} over a VARIANT is ACCEPTED even
     * when the VARIANT holds an object, while {@code MAX(vo::OBJECT)} — the same value, cast — is
     * REJECTED, and {@code MAX(o::VARIANT)} is ACCEPTED. It fires at plan time, so
     * {@code MAX(o) FROM t WHERE 1 = 0} rejects on an empty input just as live does.
     */
    private void rejectSemiStructuredOrderingAggregate(final String funcName, final List<Expression> args) {
        if (!ORDERING_AGGREGATES.contains(funcName) || args.isEmpty()) {
            return;
        }
        final DataType inferred = typeInferencer.inferSemiStructured(args.get(0));
        if (inferred != null) {
            throw new RuntimeException("SQL compilation error:\nFunction " + funcName
                + " does not support " + semiStructuredTypeName(inferred) + " argument type");
        }
        // A GEOSPATIAL value reaches the SAME sentence: live, MAX(g) is "Function MAX does
        // not support GEOGRAPHY argument type" (SQLSTATE 22000, vendor 2016) — the message an OBJECT
        // gets with the type name swapped — and MIN, MODE, MIN(DISTINCT g) and the windowed MAX(g)
        // OVER () all repeat it, over a GEOMETRY column as over a GEOGRAPHY one. It is checked here
        // rather than through a per-function declaration for the same reason the semi-structured case
        // is: these three do not refuse by argument-type list, so there is no shape to declare.
        final DataType geo = geoArgumentType(args.get(0));
        if (geo != null) {
            throw new RuntimeException("SQL compilation error:\nFunction " + funcName
                + " does not support " + semiStructuredTypeName(geo) + " argument type");
        }
    }

    /**
     * How Snowflake names a semi-structured type in that rejection: the whole parameterised type for a
     * structured one, the bare family name otherwise.
     */
    private static String semiStructuredTypeName(final DataType type) {
        return StructuredTypes.isStructured(type) ? StructuredTypes.describe(type)
            : type.getName().toUpperCase();
    }

    /**
     * Functions that reject a FILE argument even though they take an OBJECT quite happily. A FILE value
     * IS an object of file metadata, but Snowflake does not let the semi-structured surface reach into
     * it: live, {@code OBJECT_KEYS(f)} over a FILE column fails "Invalid argument types for
     * function 'OBJECT_KEYS': (FILE)". {@code TYPEOF}, {@code GET} (the {@code f:field} form) and the
     * rest of the variant-strict family reject FILE through the shared variant-coercibility rule.
     */
    private static final Set<String> FILE_REJECTING_FUNCTIONS = new HashSet<>(Arrays.asList(
        "OBJECT_KEYS"));

    /**
     * The FILE accessors, whose argument must be a FILE or something VARIANT can hold. They are the
     * MIRROR of the rule above: live,
     * {@code FL_GET_RELATIVE_PATH('@sse/hello.txt')} — a plain VARCHAR — is the compile error "Invalid
     * argument types for function 'FL_GET_RELATIVE_PATH': (VARCHAR(14))", so a stage path must be
     * wrapped in {@code TO_FILE} rather than passed straight in. A NUMBER is NOT rejected here: it
     * coerces to VARIANT and fails at run time with "Unsupported cast to FILE." instead.
     */
    private static final Set<String> FILE_ACCESSOR_FUNCTIONS = new HashSet<>(Arrays.asList(
        "FL_GET_CONTENT_TYPE", "FL_GET_ETAG", "FL_GET_FILE_TYPE", "FL_GET_LAST_MODIFIED",
        "FL_GET_RELATIVE_PATH", "FL_GET_SCOPED_FILE_URL", "FL_GET_SIZE", "FL_GET_STAGE",
        "FL_GET_STAGE_FILE_URL", "FL_IS_AUDIO", "FL_IS_COMPRESSED", "FL_IS_DOCUMENT",
        "FL_IS_IMAGE", "FL_IS_VIDEO"));

    /**
     * The ORDERING aggregates, which reject a value they cannot order with their own message shape
     * rather than the argument-type list. Live, {@code MAX(f)} over a FILE is "SQL
     * compilation error: Function MAX does not support FILE argument type" (SQLSTATE 22000), and
     * {@code MAX(o)} over an OBJECT and {@code MAX(a)} over an ARRAY are the same sentence with the
     * type name swapped — the same for {@code MIN}, {@code MODE}, {@code MAX(DISTINCT …)} and the
     * windowed {@code MIN(…) OVER ()}. Deliberately NOT generalised beyond these three: live,
     * {@code ANY_VALUE}, {@code ARRAY_AGG}, {@code ARRAY_UNIQUE_AGG}, {@code COUNT},
     * {@code COUNT(DISTINCT …)}, {@code HASH_AGG}, {@code APPROX_COUNT_DISTINCT}, {@code MAX_BY} and
     * {@code MIN_BY} all ACCEPT a FILE, an OBJECT and an ARRAY alike.
     */
    private static final Set<String> ORDERING_AGGREGATES = new HashSet<>(Arrays.asList(
        "MAX", "MIN", "MODE"));

    private void rejectFileArgument(final String funcName, final List<Expression> args) {
        if (args.isEmpty()) {
            return;
        }
        final DataType inferred = typeInferencer.infer(args.get(0));
        if (FILE_REJECTING_FUNCTIONS.contains(funcName) && inferred instanceof FileType) {
            throw new RuntimeException("Invalid argument types for function '" + funcName
                + "': (" + strictArgTypeList(args) + ")");
        }
        if (ORDERING_AGGREGATES.contains(funcName) && inferred instanceof FileType) {
            throw new RuntimeException("SQL compilation error:\nFunction " + funcName
                + " does not support FILE argument type");
        }
        rejectFileConversionArgument(funcName, args, inferred);
        if (FILE_ACCESSOR_FUNCTIONS.contains(funcName)
                && (inferred instanceof StringType || inferred instanceof BinaryType
                    || inferred instanceof DateTimeType
                    || inferred instanceof GeographyType || inferred instanceof GeometryType)) {
            throw new RuntimeException("Invalid argument types for function '" + funcName
                + "': (" + strictArgTypeList(args) + ")");
        }
    }

    private boolean anyArgumentIsFile(final List<Expression> args) {
        for (final Expression arg : args) {
            if (typeInferencer.infer(arg) instanceof FileType) {
                return true;
            }
        }
        return false;
    }

    /**
     * The explicit conversion functions, mapped to the conversion Snowflake NAMES in its rejection.
     * Live, {@code TO_VARCHAR(f)} over a FILE column is "SQL compilation error:\ninvalid type
     * [TO_VARCHAR(FT.F)] for parameter 'TO_VARCHAR'". The reported parameter is the CANONICAL
     * conversion rather than the function written — {@code TRY_TO_NUMBER(f)} reports 'TO_NUMBER' and
     * {@code TO_TIMESTAMP(f)} reports 'TO_TIMESTAMP_NTZ' — with {@code TO_CHAR} the one function that
     * reports its own name. {@code TO_FILE} / {@code TRY_TO_FILE} are absent on purpose: live,
     * {@code TO_FILE(f)} is ACCEPTED and returns the file unchanged.
     */
    private static final Map<String, String> FILE_REJECTING_CONVERSIONS = new HashMap<>();

    static {
        FILE_REJECTING_CONVERSIONS.put("TO_CHAR", "TO_CHAR");
        FILE_REJECTING_CONVERSIONS.put("TO_VARCHAR", "TO_VARCHAR");
        FILE_REJECTING_CONVERSIONS.put("TO_NUMBER", "TO_NUMBER");
        FILE_REJECTING_CONVERSIONS.put("TO_NUMERIC", "TO_NUMBER");
        FILE_REJECTING_CONVERSIONS.put("TO_DECIMAL", "TO_NUMBER");
        FILE_REJECTING_CONVERSIONS.put("TO_DOUBLE", "TO_DOUBLE");
        FILE_REJECTING_CONVERSIONS.put("TO_BOOLEAN", "TO_BOOLEAN");
        FILE_REJECTING_CONVERSIONS.put("TO_DATE", "TO_DATE");
        FILE_REJECTING_CONVERSIONS.put("TO_TIME", "TO_TIME");
        FILE_REJECTING_CONVERSIONS.put("TO_TIMESTAMP", "TO_TIMESTAMP_NTZ");
        FILE_REJECTING_CONVERSIONS.put("TO_TIMESTAMP_NTZ", "TO_TIMESTAMP_NTZ");
        FILE_REJECTING_CONVERSIONS.put("TO_TIMESTAMP_LTZ", "TO_TIMESTAMP_LTZ");
        FILE_REJECTING_CONVERSIONS.put("TO_TIMESTAMP_TZ", "TO_TIMESTAMP_TZ");
        FILE_REJECTING_CONVERSIONS.put("TO_BINARY", "TO_BINARY");
        FILE_REJECTING_CONVERSIONS.put("TO_ARRAY", "TO_ARRAY");
        FILE_REJECTING_CONVERSIONS.put("TO_OBJECT", "TO_OBJECT");
        FILE_REJECTING_CONVERSIONS.put("TO_VARIANT", "TO_VARIANT");
        FILE_REJECTING_CONVERSIONS.put("TO_GEOGRAPHY", "TO_GEOGRAPHY");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_NUMBER", "TO_NUMBER");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_NUMERIC", "TO_NUMBER");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_DECIMAL", "TO_NUMBER");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_DOUBLE", "TO_DOUBLE");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_BOOLEAN", "TO_BOOLEAN");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_DATE", "TO_DATE");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_TIME", "TO_TIME");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_TIMESTAMP", "TO_TIMESTAMP_NTZ");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_BINARY", "TO_BINARY");
        FILE_REJECTING_CONVERSIONS.put("TRY_TO_GEOGRAPHY", "TO_GEOGRAPHY");
    }

    private void rejectFileConversionArgument(final String funcName, final List<Expression> args,
                                              final DataType inferred) {
        final String parameter = FILE_REJECTING_CONVERSIONS.get(funcName);
        if (parameter == null || !(inferred instanceof FileType)) {
            return;
        }
        throw new RuntimeException("SQL compilation error:\ninvalid type [" + funcName + "("
            + strictText(args.get(0)) + ")] for parameter '" + parameter + "'");
    }

    /**
     * The conversions a GEOSPATIAL value is refused by, which is EVERY one FILE is refused by except
     * the two that build a geo value. Derived from {@link #FILE_REJECTING_CONVERSIONS} rather than
     * written out again because the two lists agree entry for entry, canonical parameter name
     * included: live, {@code TO_VARCHAR(g)} is "invalid type [TO_VARCHAR(GT.G)] for
     * parameter 'TO_VARCHAR'", {@code TO_CHAR(g)} reports its OWN name, {@code TRY_TO_NUMBER(g)}
     * reports the canonical 'TO_NUMBER', and {@code TO_NUMBER} / {@code TO_BOOLEAN} / {@code TO_DATE} /
     * {@code TO_BINARY} / {@code TO_VARIANT} / {@code TO_OBJECT} / {@code TO_ARRAY} each report
     * themselves — the same sentence FILE gets, over a GEOMETRY as over a GEOGRAPHY.
     *
     * <p>{@code TO_GEOGRAPHY} and {@code TRY_TO_GEOGRAPHY} are REMOVED, and {@code TO_GEOMETRY} was
     * never in the FILE list: live ACCEPTS {@code TO_GEOGRAPHY(g)} and {@code TO_GEOMETRY(g)}, which
     * return the value re-projected. That is the same shape {@code TO_FILE(f)} has for a FILE, and it
     * is the reason the CAST spellings need their own rule — {@code CAST(g AS GEOGRAPHY)} is refused
     * where the function is not.
     *
     * <p>{@code TO_JSON} is deliberately absent: live refuses it with the ARGUMENT-TYPE list
     * ("Invalid argument types for function 'TO_JSON': (GEOGRAPHY)", SQLSTATE 42P13) rather than the
     * conversion sentence, and it already declares that shape as a {@link StructuredArgumentFunction}.
     */
    private static final Map<String, String> GEO_REJECTING_CONVERSIONS = new HashMap<>();

    static {
        GEO_REJECTING_CONVERSIONS.putAll(FILE_REJECTING_CONVERSIONS);
        GEO_REJECTING_CONVERSIONS.remove("TO_GEOGRAPHY");
        GEO_REJECTING_CONVERSIONS.remove("TRY_TO_GEOGRAPHY");
    }

    /**
     * The GEOSPATIAL twin of {@link #rejectFileArgument}: the rejections that are keyed on the FUNCTION
     * NAME rather than declared on the function object. Only the conversions need it — the ordering
     * aggregates say so in {@link #rejectSemiStructuredOrderingAggregate} and everything else declares
     * {@link BuiltInFunction#geoRejection}.
     */
    /**
     * The WINDOW-only functions that refuse a GEOSPATIAL argument. They are keyed by NAME because they
     * are not registry objects at all — {@code LAG}, {@code LEAD} and {@code NTH_VALUE} are evaluated
     * by {@link dev.frostlake.executor.WindowFunctionEvaluator}, which re-forms the call without its
     * OVER clause and puts it through this walk, so there is no instance to declare
     * {@link BuiltInFunction#geoRejection} on.
     *
     * <p>Live, {@code LAG(g) OVER (ORDER BY id)} is "Invalid argument types for function
     * 'LAG': (GEOGRAPHY)" (SQLSTATE 42P13), {@code LEAD(gm)} names 'LEAD' over a GEOMETRY and
     * {@code NTH_VALUE(g, 1)} lists both its arguments — while {@code LAG(o)} over an OBJECT returns
     * the previous object. {@code FIRST_VALUE(g)} and {@code LAST_VALUE(g)} are deliberately ABSENT:
     * both ACCEPT a geo value live and returned it, which is the boundary this set is drawn against.
     */
    private static final Set<String> GEO_REJECTING_WINDOW_FUNCTIONS = new HashSet<>(Arrays.asList(
        "LAG", "LEAD", "NTH_VALUE"));

    private void rejectGeoArgument(final String funcName, final List<Expression> args) {
        if (args.isEmpty()) {
            return;
        }
        if (GEO_REJECTING_WINDOW_FUNCTIONS.contains(funcName)
                && GeoTypes.isGeo(typeInferencer.infer(args.get(0)))) {
            throw new RuntimeException("Invalid argument types for function '" + funcName
                + "': (" + strictArgTypeList(args) + ")");
        }
        final String parameter = GEO_REJECTING_CONVERSIONS.get(funcName);
        if (parameter == null || !GeoTypes.isGeo(typeInferencer.infer(args.get(0)))) {
            return;
        }
        // EVERY argument is rendered, matching live: TO_CHAR(g, 'x') is reported as "invalid type
        // [TO_CHAR(GT.G, 'x')]", format string included — the same rendering the declared
        // INVALID_TYPE_PARAMETER shape produces, so TO_CHAR and TO_VARCHAR give one answer whichever
        // of the two rules reaches them first.
        throw new RuntimeException("SQL compilation error:\ninvalid type [" + funcName + "("
            + renderedArguments(args) + ")] for parameter '" + parameter + "'");
    }

    /**
     * A FILE is never a CAST SOURCE. Live every target is rejected at compile time —
     * {@code f::VARCHAR}, {@code CAST(f AS NUMBER)}, {@code CAST(f AS OBJECT)},
     * {@code CAST(f AS VARIANT)}, even {@code CAST(f AS FILE)} — as "SQL compilation error:\ninvalid
     * type [&lt;rendered cast&gt;] for parameter '&lt;conversion&gt;'", where the conversion is the one
     * the target implies (VARCHAR → 'TO_VARCHAR', FLOAT → 'TO_DOUBLE', TIMESTAMP → 'TO_TIMESTAMP_NTZ').
     * The FILE-as-TARGET half of the rule already fires on the parse tree in
     * {@link ExpressionAstBuilder}; this is the mirror, which needs the SOURCE's inferred type and so
     * belongs here. A TRY_CAST renders without its target, matching live.
     */
    private void rejectFileCastSource(final CastExpression expr) {
        if (!(typeInferencer.infer(expr.getExpression()) instanceof FileType)) {
            return;
        }
        throw new RuntimeException(castSourceRejectionMessage(expr));
    }

    /**
     * A GEOSPATIAL value is never a CAST SOURCE either — the same rule FILE keeps, and the sharpest
     * difference from a plain OBJECT, which casts to text quite happily. Live over a
     * GEOGRAPHY and a GEOMETRY column, every target is refused at compile time with the conversion
     * sentence: {@code CAST(g AS VARCHAR)} names 'TO_VARCHAR' — where {@code CAST(o AS VARCHAR)}
     * returns {@code {"k":"v1"}} — and so do {@code g::VARCHAR}, {@code CAST(g AS TEXT)},
     * {@code CAST(g AS VARCHAR(20))}, the {@code TRY_CAST} spelling, {@code CAST(g AS VARIANT)}
     * ('TO_VARIANT'), {@code CAST(g AS OBJECT)}, {@code CAST(g AS ARRAY)}, {@code CAST(g AS NUMBER)},
     * {@code CAST(g AS BOOLEAN)}, {@code CAST(g AS BINARY)} and even the identity
     * {@code CAST(g AS GEOGRAPHY)} and the sibling {@code CAST(gm AS GEOGRAPHY)}. Frostlake used to
     * hand back the GeoJSON text for the text targets and the value itself for the geo ones.
     *
     * <p>Because the VARIANT target is refused too, there is no explicit conversion that opts back in
     * the way {@code so::VARIANT} does for a structured value: {@code ST_ASWKT}, {@code ST_ASEWKT},
     * {@code ST_ASGEOJSON} and {@code ST_ASWKB} are the only routes to text, and they live in the
     * optional {@code frostlake-geo} module. The FUNCTION spellings {@code TO_GEOGRAPHY(g)} and
     * {@code TO_GEOMETRY(g)} ARE accepted live, which is why they are absent from the conversion map
     * above while their cast spellings are refused here — the same split {@code TO_FILE} has.
     *
     * <p>The reverse direction — a cast INTO a geo type, {@code 'POINT(1 1)'::GEOGRAPHY} — is also
     * refused live ("invalid type [CAST('POINT(1 1)' AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'"),
     * but that is a rule about the TARGET and about which sources may reach it; it is deliberately
     * left alone here, where the subject is what a geo value may be used AS.
     */
    private void rejectGeoCastSource(final CastExpression expr) {
        if (!GeoTypes.isGeo(typeInferencer.infer(expr.getExpression()))) {
            return;
        }
        throw new RuntimeException(castSourceRejectionMessage(expr));
    }

    /**
     * The sentence live gives a cast whose SOURCE type has no conversion at all: the whole cast
     * rendered back, and the conversion the TARGET implies (VARCHAR → 'TO_VARCHAR', FLOAT →
     * 'TO_DOUBLE', TIMESTAMP → 'TO_TIMESTAMP_NTZ'). A TRY_CAST renders without its target, matching
     * live. Shared by the FILE and GEOSPATIAL source rules, which produce it identically.
     */
    private String castSourceRejectionMessage(final CastExpression expr) {
        final String source = strictText(expr.getExpression());
        // The target is rendered with its default parameters, exactly as live prints it: live
        // run CAST(f AS NUMBER) over table fk reports "CAST(FK.F AS NUMBER(38,0))" and
        // CAST(f AS VARCHAR) reports "CAST(FK.F AS VARCHAR(134217728))".
        final String rendered = expr.isTryMode() ? "TRY_CAST(" + source + ")"
            : "CAST(" + source + " AS " + castTargetTypeText(expr.getTargetType()) + ")";
        return "SQL compilation error:\ninvalid type [" + rendered
            + "] for parameter '" + castConversionName(expr.getTargetType()) + "'";
    }

    /**
     * A STRUCTURED value has no TEXT conversion, where a plain OBJECT or ARRAY has one. This is the
     * CAST half of what {@code TO_VARCHAR} / {@code TO_CHAR} declare as functions; a cast is not a
     * function call, so it needs its own rule. Live over one table carrying both,
     * {@code CAST(o AS VARCHAR)} is {@code {"k":"v1"}} while {@code CAST(so AS VARCHAR)} is "SQL
     * compilation error: invalid type [CAST(ST.SO AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'"
     * — and so are {@code so::VARCHAR}, {@code CAST(so AS TEXT)}, {@code CAST(so AS STRING)},
     * {@code CAST(so AS VARCHAR(20))}, {@code CAST(so AS CHAR(5))}, {@code CAST(so AS NVARCHAR)} and
     * the {@code TRY_CAST} spelling, over all three structured kinds. Frostlake used to hand back the
     * JSON text for every one of them.
     *
     * <p>TEXT targets only, and that boundary is measured rather than assumed. A cast to VARIANT, to
     * plain OBJECT / ARRAY, or to a structured type is LEGAL live ({@code CAST(so AS VARIANT)},
     * {@code CAST(sm AS OBJECT)}, {@code CAST(sa AS ARRAY(INT))} all return the value) and is exactly
     * how a caller reaches the text: {@code CAST(so::VARIANT AS VARCHAR)} works. The scalar targets —
     * NUMBER, BOOLEAN, DATE, BINARY — do fail live, but they fail for a plain OBJECT too
     * ({@code CAST(o AS NUMBER)} is the same sentence for 'TO_NUMBER'), so they are not a divergence
     * and are left to whoever implements that separate rule.
     *
     * <p>A TRY_CAST renders without its target, matching live, and is rejected HERE rather than by the
     * TRY_CAST source rule below so the structured sentence wins — live gives {@code TRY_CAST(so AS
     * VARCHAR)} the conversion message, not TRY_CAST's own.
     */
    private void rejectStructuredTextCastSource(final CastExpression expr) {
        // The TARGET is checked first deliberately: it is a switch over the written type name, while
        // the source's type is an AST walk, and this method runs per row for every cast evaluated.
        if (!(TypeInferencer.typeForName(expr.getTargetType()) instanceof StringType)) {
            return;
        }
        if (!StructuredTypes.isStructured(typeInferencer.inferSemiStructured(expr.getExpression()))) {
            return;
        }
        final String source = strictText(expr.getExpression());
        final String rendered = expr.isTryMode() ? "TRY_CAST(" + source + ")"
            : "CAST(" + source + " AS " + textCastTargetText(expr.getTargetType()) + ")";
        throw new RuntimeException("SQL compilation error:\ninvalid type [" + rendered
            + "] for parameter 'TO_VARCHAR'");
    }

    /**
     * A TEXT cast target as the message spells it, which is always VARCHAR: live renders
     * {@code CAST(so AS CHAR(5))} as {@code CAST(ST.SO AS VARCHAR(5))} and {@code CAST(so AS NVARCHAR)}
     * as {@code CAST(ST.SO AS VARCHAR(134217728))} — every spelling of the type collapses onto the one
     * conversion it routes through, keeping only its length.
     */
    private static String textCastTargetText(final String targetType) {
        final String rendered = castTargetTypeText(targetType);
        final int paren = rendered.indexOf('(');
        return paren < 0 ? "VARCHAR(134217728)" : "VARCHAR" + rendered.substring(paren);
    }

    /** The conversion function a cast target routes through, as Snowflake names it in the message. */
    private static String castConversionName(final String targetType) {
        final int paren = targetType.indexOf('(');
        final String base = (paren > 0 ? targetType.substring(0, paren) : targetType)
            .trim().toUpperCase().replace(" ", "");
        switch (base) {
            case "NUMBER": case "DECIMAL": case "NUMERIC": case "INT": case "INTEGER":
            case "BIGINT": case "SMALLINT": case "TINYINT": case "BYTEINT":
                return "TO_NUMBER";
            case "FLOAT": case "FLOAT4": case "FLOAT8": case "DOUBLE": case "REAL":
            case "DOUBLEPRECISION":
                return "TO_DOUBLE";
            case "BOOLEAN":
                return "TO_BOOLEAN";
            case "DATE":
                return "TO_DATE";
            case "TIME":
                return "TO_TIME";
            case "DATETIME": case "TIMESTAMP": case "TIMESTAMP_NTZ": case "TIMESTAMPNTZ":
                return "TO_TIMESTAMP_NTZ";
            case "TIMESTAMP_LTZ": case "TIMESTAMPLTZ":
                return "TO_TIMESTAMP_LTZ";
            case "TIMESTAMP_TZ": case "TIMESTAMPTZ":
                return "TO_TIMESTAMP_TZ";
            case "BINARY": case "VARBINARY":
                return "TO_BINARY";
            case "ARRAY":
                return "TO_ARRAY";
            case "OBJECT":
                return "TO_OBJECT";
            case "VARIANT":
                return "TO_VARIANT";
            case "GEOGRAPHY":
                return "TO_GEOGRAPHY";
            case "GEOMETRY":
                return "TO_GEOMETRY";
            case "FILE":
                return "TO_FILE";
            default:
                return "TO_VARCHAR";
        }
    }

    /**
     * The {@code ||} operator does not join a FILE to anything. Live, {@code 'x' || f} is
     * "Invalid argument types for function '||': (VARCHAR(1), FILE)" and {@code f || f} names both
     * sides — the very case that made a FILE silently stringify to its descriptor JSON here.
     */
    private void rejectFileConcatOperand(final BinaryOperationExpression expr) {
        if (expr.getOperator() != BinaryOperator.CONCAT) {
            return;
        }
        final List<Expression> operands = new ArrayList<>();
        operands.add(expr.getLeft());
        operands.add(expr.getRight());
        if (anyArgumentIsFile(operands)) {
            throw new RuntimeException("Invalid argument types for function '||': ("
                + strictArgTypeList(operands) + ")");
        }
    }

    /**
     * The {@code ||} operator does not join a statically OBJECT- or ARRAY-typed value either. Live
     * {@code 'x' || o} is "Invalid argument types for function '||': (VARCHAR(1),
     * OBJECT)", {@code o || 'x'} names the other side, {@code o || o} names both and {@code 'x' || a}
     * names ARRAY — where Frostlake used to return {@code x{"k":"v"}}. It fires on an EMPTY input,
     * as live does. A VARIANT joins perfectly well, holding an object or not ({@code 'x' || vo}
     * returned {@code x{"x":1}} live), and so do NUMBER, BOOLEAN and the temporals.
     *
     * <p>Frostlake's operand list is the BINARY one it parsed. Live flattens a chain before
     * reporting — {@code s || s || o} lists three types there and two here — which is a message
     * difference, not a difference in what is rejected.
     */
    private void rejectSemiStructuredConcatOperand(final BinaryOperationExpression expr) {
        if (expr.getOperator() != BinaryOperator.CONCAT) {
            return;
        }
        final List<Expression> operands = new ArrayList<>();
        operands.add(expr.getLeft());
        operands.add(expr.getRight());
        for (final Expression operand : operands) {
            if (typeInferencer.inferSemiStructured(operand) != null
                    || geoArgumentType(operand) != null) {
                throw new RuntimeException("Invalid argument types for function '||': ("
                    + strictArgTypeList(operands) + ")");
            }
        }
    }

    /**
     * The names Snowflake reports the ARITHMETIC operators under. They are the operator symbols
     * themselves, so an arithmetic rejection reads exactly like a function one: live,
     * {@code o + 1} over an OBJECT column is "Invalid argument types for function '+': (OBJECT,
     * NUMBER(1,0))" (SQLSTATE 42P13), {@code o * 2} names '*', {@code o / 2} names '/' and
     * {@code o % 2} names '%'. Only the arithmetic operators are listed: comparison, {@code AND} /
     * {@code OR} and {@code IN} all ACCEPT a semi-structured operand live ({@code WHERE o = o}
     * returned both rows), and CONCAT has its own rule above.
     */
    private static final Map<BinaryOperator, String> ARITHMETIC_OPERATOR_NAMES = new HashMap<>();

    static {
        ARITHMETIC_OPERATOR_NAMES.put(BinaryOperator.ADD, "+");
        ARITHMETIC_OPERATOR_NAMES.put(BinaryOperator.SUBTRACT, "-");
        ARITHMETIC_OPERATOR_NAMES.put(BinaryOperator.MULTIPLY, "*");
        ARITHMETIC_OPERATOR_NAMES.put(BinaryOperator.DIVIDE, "/");
        ARITHMETIC_OPERATOR_NAMES.put(BinaryOperator.MODULO, "%");
    }

    /**
     * Arithmetic does not accept a statically OBJECT- or ARRAY-typed operand either — the obvious
     * neighbour of the aggregate rule, and measured rather than assumed. Live over a
     * populated OBJECT column, all of {@code o + 1}, {@code 1 + o}, {@code o - 1}, {@code o * 2},
     * {@code o / 2}, {@code o % 2}, {@code o + o}, {@code a + 1}, {@code d + o} and {@code o + NULL}
     * are compile errors naming the operator and BOTH operand types; Frostlake used to reach the
     * evaluator and fail there with "Cannot add: {"k":"v1"} + 1", which no Snowflake caller can match
     * and which never fires over an empty input.
     *
     * <p>The bound is the same one the whole family keeps: a VARIANT is fine ({@code v + 1} returned
     * 2 and 3 live), a VARIANT HOLDING an object fails at RUN time instead ("Failed to cast variant
     * value {"x":1} to REAL"), and a read INTO the value is fine ({@code a[0] + 1}). Ordinary
     * arithmetic is untouched — NUMBER, FLOAT and DATE all still add.
     *
     * <p>A FILE operand is refused identically, and was measured on its own rather than inferred from
     * the OBJECT case: live {@code f + 1} is "Invalid argument types for function '+':
     * (FILE, NUMBER(1,0))", {@code 1 + f} names the other side, and {@code f - 1} / {@code f * 2} /
     * {@code f / 2} / {@code f % 2} / {@code f + f} each name their operator. Frostlake used to reach
     * the evaluator and fail with "Cannot add: {"CONTENT_TYPE":…} + 1" — the descriptor JSON, quoted
     * back at a caller who never saw it.
     *
     * <p>Unary minus is covered by {@link #rejectSemiStructuredNegateOperand}. Unary PLUS is NOT:
     * live reports it as 'UNARY PLUS', but Frostlake's grammar folds {@code +x} away before an AST
     * node exists, so there is nothing to attach the rule to.
     */
    private void rejectSemiStructuredArithmeticOperand(final BinaryOperationExpression expr) {
        final String operatorName = ARITHMETIC_OPERATOR_NAMES.get(expr.getOperator());
        if (operatorName == null) {
            return;
        }
        final List<Expression> operands = new ArrayList<>();
        operands.add(expr.getLeft());
        operands.add(expr.getRight());
        for (final Expression operand : operands) {
            if (typeInferencer.inferSemiStructured(operand) != null
                    || fileArgumentType(operand) != null || geoArgumentType(operand) != null) {
                throw new RuntimeException("Invalid argument types for function '" + operatorName
                    + "': (" + strictArgTypeList(operands) + ")");
            }
        }
    }

    /**
     * The names Snowflake reports the COMPARISON operators under — the symbols themselves, exactly as
     * the arithmetic map does. Only GEOSPATIAL operands are refused through this map, and that
     * narrowness is the point: a semi-structured operand COMPARES live ({@code WHERE o = o} returned
     * both rows, which is why {@link #ARITHMETIC_OPERATOR_NAMES} says so in its own note) and so does a
     * FILE ({@code f = f}, {@code JOIN ON a.f = b.f}). GEOGRAPHY and GEOMETRY are the one family that
     * does not.
     *
     * <p>{@code !=} and {@code <>} are ONE operator by the time an AST exists, so both report '!=' —
     * where live echoes whichever was written. That is a rendering difference, not a difference in
     * what is rejected, the same choice the {@code ||} chain rule already makes.
     */
    private static final Map<BinaryOperator, String> COMPARISON_OPERATOR_NAMES = new HashMap<>();

    static {
        COMPARISON_OPERATOR_NAMES.put(BinaryOperator.EQUAL, "=");
        COMPARISON_OPERATOR_NAMES.put(BinaryOperator.NOT_EQUAL, "!=");
        COMPARISON_OPERATOR_NAMES.put(BinaryOperator.LESS_THAN, "<");
        COMPARISON_OPERATOR_NAMES.put(BinaryOperator.LESS_THAN_OR_EQUAL, "<=");
        COMPARISON_OPERATOR_NAMES.put(BinaryOperator.GREATER_THAN, ">");
        COMPARISON_OPERATOR_NAMES.put(BinaryOperator.GREATER_THAN_OR_EQUAL, ">=");
    }

    /**
     * A GEOSPATIAL value does not COMPARE. This is the deepest divergence from every other type this
     * family of rules covers, and it was measured across four different spellings rather than inferred
     * from one: live, {@code JOIN … ON a.g = b.g} is "Invalid argument types for function
     * '=': (GEOGRAPHY, GEOGRAPHY)" (SQLSTATE 42P13), {@code WHERE g = TO_GEOGRAPHY('POINT(1 1)')} and
     * the bare projection {@code SELECT g = g} say the same, {@code g <> g} names '&lt;&gt;',
     * {@code g >= …} names '&gt;=', {@code g = 'POINT(1 1)'} names "(GEOGRAPHY, VARCHAR(10))", and the
     * GEOMETRY column repeats every one. Frostlake used to compare the GeoJSON text and answer TRUE.
     *
     * <p>DISTINCTNESS is untouched, and that boundary is measured too: {@code SELECT DISTINCT g},
     * {@code SELECT DISTINCT g, gm}, {@code COUNT(DISTINCT g)}, {@code UNION}, {@code INTERSECT} and
     * {@code EXCEPT} all work live over a geo column. None of those routes through an expression
     * operator here, so grouping-by-value keeps working while the WRITTEN comparison is refused —
     * which is precisely the shape live has.
     *
     * <p>{@code BETWEEN} is deliberately NOT covered. Live rejects it, reporting '&gt;=' with two
     * operand types for a three-operand construct, and which two it lists in the mixed cases was not
     * measured; guessing the list would invent a plan.
     */
    private void rejectGeoComparisonOperand(final BinaryOperationExpression expr) {
        final String operatorName = COMPARISON_OPERATOR_NAMES.get(expr.getOperator());
        if (operatorName == null) {
            return;
        }
        final List<Expression> operands = new ArrayList<>();
        operands.add(expr.getLeft());
        operands.add(expr.getRight());
        for (final Expression operand : operands) {
            if (geoArgumentType(operand) != null) {
                throw new RuntimeException("Invalid argument types for function '" + operatorName
                    + "': (" + strictArgTypeList(operands) + ")");
            }
        }
    }

    /**
     * {@code IN} is a comparison too, and reports itself as 'IN' with the SUBJECT followed by every
     * listed value: live, {@code g IN (TO_GEOGRAPHY('POINT(1 1)'))} is "Invalid argument
     * types for function 'IN': (GEOGRAPHY, GEOGRAPHY)", the two-element list adds a third entry, the
     * {@code NOT IN} spelling is identical and the GEOMETRY column repeats both.
     *
     * <p>The SUBQUERY form reports '=' rather than 'IN' live and is left to
     * {@link #rejectGeoComparisonOperand} if it ever reaches it: the subquery's projected type is not
     * statically known here, so the check would have nothing to read.
     */
    /**
     * The GEOSPATIAL comparison rules alone, walked over a PREDICATE. The strict-argument walk covers a
     * projection; a WHERE clause is validated by {@link #validatePredicateType} instead, and that is
     * exactly where {@code g = …} gets written — so the two comparison rules are applied here as well,
     * and nothing else is. The walk descends only through the containers a comparison nests in.
     */
    public void validateGeoComparisons(final Expression expr) {
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            rejectGeoComparisonOperand(binary);
            validateGeoComparisons(binary.getLeft());
            validateGeoComparisons(binary.getRight());
            return;
        }
        if (expr instanceof InExpression) {
            rejectGeoInOperand((InExpression) expr);
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            validateGeoComparisons(((UnaryOperationExpression) expr).getOperand());
        }
    }

    private void rejectGeoInOperand(final InExpression expr) {
        if (expr.hasSubquery() || expr.getValues() == null) {
            return;
        }
        final List<Expression> operands = new ArrayList<>();
        operands.add(expr.getValue());
        operands.addAll(expr.getValues());
        for (final Expression operand : operands) {
            if (geoArgumentType(operand) != null) {
                throw new RuntimeException("Invalid argument types for function 'IN': ("
                    + strictArgTypeList(operands) + ")");
            }
        }
    }

    /**
     * Unary minus reports itself as 'NEGATE' rather than '-': live, {@code -o} over an
     * OBJECT column is "Invalid argument types for function 'NEGATE': (OBJECT)", {@code -sm} over a
     * structured MAP names the whole MAP type and {@code -f} over a FILE names "(FILE)". Logical NOT
     * and EXISTS are left alone — neither was measured, and neither takes a numeric operand.
     */
    private void rejectSemiStructuredNegateOperand(final UnaryOperationExpression expr) {
        if (expr.getOperator() != UnaryOperator.NEGATE) {
            return;
        }
        if (typeInferencer.inferSemiStructured(expr.getOperand()) == null
                && fileArgumentType(expr.getOperand()) == null
                && geoArgumentType(expr.getOperand()) == null) {
            return;
        }
        throw new RuntimeException("Invalid argument types for function 'NEGATE': ("
            + strictArgTypeText(expr.getOperand()) + ")");
    }

    /**
     * The value an ordering aggregate accumulates may not be semi-structured. This is the same rule
     * {@link SemiStructuredRejection#INCOMPATIBLE_TYPES} covers for {@code MEDIAN}'s argument, applied
     * where the value arrives through a {@code WITHIN GROUP (ORDER BY …)} clause instead of the
     * argument list: live, {@code PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY o)} is
     * "incompatible types: [OBJECT] and [NUMBER(9,0)]" (SQLSTATE 42846) — exactly what
     * {@code MEDIAN(o)} says — while {@code … (ORDER BY n)} over a NUMBER is fine.
     *
     * <p>It cannot be declared on the function like the argument positions are, because the clause is
     * not an argument: the FRACTION is, and that position refuses a semi-structured value with the
     * OTHER message ("Invalid argument types for function 'PERCENTILE_CONT': (OBJECT)", live-verified
     * for {@code PERCENTILE_CONT(o) WITHIN GROUP (ORDER BY n)}). One call, two message shapes.
     *
     * <p>A FILE arrives at the same sentence: live, both
     * {@code PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY f)} and the {@code PERCENTILE_DISC} spelling
     * are "incompatible types: [FILE] and [NUMBER(9,0)]" — the CLAUSE message, not the "cannot be used
     * as ORDER BY keys" one a bare {@code ORDER BY f} gets, which is why this check runs before the
     * key rule reaches the clause.
     */
    public void validateOrderedValueExpression(final Expression expr) {
        final DataType inferred = typeInferencer.inferSemiStructured(expr);
        if (inferred != null) {
            throw new RuntimeException("incompatible types: [" + semiStructuredTypeName(inferred)
                + "] and [NUMBER(9,0)]");
        }
        final DataType file = fileArgumentType(expr);
        if (file != null) {
            throw new RuntimeException("incompatible types: [" + semiStructuredTypeName(file)
                + "] and [NUMBER(9,0)]");
        }
        // A GEOSPATIAL value reaches the same sentence, and only for the percentiles: live,
        // PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY g) and the PERCENTILE_DISC spelling are
        // "incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]" while LISTAGG(s, ',') WITHIN GROUP
        // (ORDER BY g) and ARRAY_AGG(s) WITHIN GROUP (ORDER BY g) both RETURN a value — those two
        // order by the key without accumulating it. Only the percentile call sites reach here.
        final DataType geo = geoArgumentType(expr);
        if (geo != null) {
            throw new RuntimeException("incompatible types: [" + semiStructuredTypeName(geo)
                + "] and [NUMBER(9,0)]");
        }
    }

    /**
     * Plan-time rejection of a FILE or a GEOSPATIAL value in a GROUPING / SORTING key position. A FILE
     * compares, groups as a DISTINCT value and joins perfectly well — live, {@code f = f},
     * {@code SELECT DISTINCT f}, {@code COUNT(DISTINCT f)}, {@code JOIN ON a.f = b.f} and
     * {@code f IN (…)} are all accepted — but it may not be a GROUP BY, ORDER BY or window PARTITION BY
     * key. The check reads the key's INFERRED type, so it fires for a bare column, a positional
     * ordinal, a SELECT alias and any expression that yields a FILE ({@code GROUP BY IFF(TRUE, f, f)})
     * alike.
     *
     * <p>GEOGRAPHY and GEOMETRY are refused in the same three positions with the same sentence and the
     * same SQLSTATE, measured on a table carrying both: {@code GROUP BY g} is "Expressions
     * of type GEOGRAPHY cannot be used as GROUP BY keys" (42804, vendor 92102), {@code ORDER BY g} the
     * ORDER BY variant (92103) and {@code OVER (PARTITION BY g)} the PARTITION BY one (92104) — the
     * three codes FILE uses. Every spelling the FILE rule covers rejects too: the ordinal
     * ({@code GROUP BY 1} over a projected geo column), the SELECT alias, {@code ROLLUP(g)},
     * {@code CUBE(gm)}, {@code GROUPING SETS ((g))}, a multi-key list, {@code IFF(TRUE, g, g)}, the
     * ORDER BY inside an OVER spec, a derived table's ORDER BY and the CTE spelling — and all of them
     * on an EMPTY input. Frostlake used to group and sort geo values by their GeoJSON text.
     *
     * <p>This is where geo diverges MOST sharply from the semi-structured types the same machinery
     * serves: live GROUPS, ORDERS and PARTITIONS an OBJECT, an ARRAY and every structured kind quite
     * happily, which is why #143 correctly refused to route those through here. A geo value is
     * nevertheless still a DISTINCT value — {@code SELECT DISTINCT g}, {@code SELECT DISTINCT g, gm},
     * {@code COUNT(DISTINCT g)}, {@code UNION}, {@code INTERSECT} and {@code EXCEPT} all work live, and
     * none of them routes through this check.
     */
    public void validateKeyExpression(final Expression expr, final SortKeyRole role) {
        final DataType inferred = typeInferencer.infer(expr);
        if (inferred instanceof FileType) {
            throw new RuntimeException("Expressions of type FILE cannot be used as "
                + role.getLabel() + " keys");
        }
        if (GeoTypes.isGeo(inferred)) {
            throw new RuntimeException("Expressions of type " + inferred.getName().toUpperCase()
                + " cannot be used as " + role.getLabel() + " keys");
        }
    }

    /**
     * The two-vector functions. Both arguments must be VECTORs of the SAME element type and the SAME
     * dimension — live-verified, a dimension mismatch, a mixed FLOAT/INT pair, a plain
     * ARRAY and an untyped NULL are all COMPILE errors ("Invalid argument types for function
     * 'VECTOR_L2_DISTANCE': (VECTOR(FLOAT, 3), VECTOR(INT, 3))"), firing even when the input has no
     * rows.
     */
    private static final Set<String> VECTOR_PAIR_FUNCTIONS = new HashSet<>(Arrays.asList(
        "VECTOR_COSINE_SIMILARITY", "VECTOR_L1_DISTANCE", "VECTOR_L2_DISTANCE", "VECTOR_INNER_PRODUCT"));

    /**
     * The functions whose FIRST argument must be a VECTOR: the unary transforms and the element-wise
     * aggregates. Live, {@code VECTOR_NORMALIZE([1,2,3])} is "Invalid argument types for function
     * 'VECTOR_NORMALIZE': (ARRAY)", {@code VECTOR_NORMALIZE(NULL)} is "(NULL)", and
     * {@code VECTOR_SUM(1)} is "(NUMBER(1,0))" — while a TYPED null
     * ({@code NULL::VECTOR(FLOAT,3)}) is accepted and yields SQL NULL.
     */
    private static final Set<String> VECTOR_UNARY_FUNCTIONS = new HashSet<>(Arrays.asList(
        "VECTOR_NORMALIZE", "VECTOR_TRUNC", "VECTOR_TRUNCATE",
        "VECTOR_SUM", "VECTOR_AVG", "VECTOR_MIN", "VECTOR_MAX"));

    private void rejectNonVectorArgument(final String funcName, final List<Expression> args) {
        final boolean pair = VECTOR_PAIR_FUNCTIONS.contains(funcName);
        if (!pair && !VECTOR_UNARY_FUNCTIONS.contains(funcName)) {
            return;
        }
        final int vectorArgs = pair ? 2 : 1;
        if (args.size() < vectorArgs) {
            return;   // the arity error reports this better than a type error would
        }
        final VectorType first = declaredVectorType(funcName, args, args.get(0));
        if (pair) {
            final VectorType second = declaredVectorType(funcName, args, args.get(1));
            if (first != null && second != null && !first.equals(second)) {
                throw new RuntimeException("Invalid argument types for function '" + funcName
                    + "': (" + strictArgTypeList(args) + ")");
            }
        }
        if (funcName.equals("VECTOR_TRUNC") || funcName.equals("VECTOR_TRUNCATE")) {
            rejectIllegalTruncationDimension(funcName, args, first);
        }
    }

    /**
     * The argument's declared VECTOR type. A statically-known non-vector type — and an untyped NULL
     * literal, which the inferencer also reports as undetermined — is Snowflake's argument-type error;
     * a genuinely undetermined expression (a derived column, a UDF parameter) returns null so the typed
     * runtime value decides, as everywhere else in these checks.
     */
    private VectorType declaredVectorType(final String funcName, final List<Expression> args,
                                          final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        if (inferred instanceof VectorType) {
            return (VectorType) inferred;
        }
        final boolean untypedNull = arg instanceof LiteralExpression
            && ((LiteralExpression) arg).getType() == LiteralType.NULL;
        if (inferred != null || untypedNull) {
            throw new RuntimeException("Invalid argument types for function '" + funcName
                + "': (" + strictArgTypeList(args) + ")");
        }
        return null;
    }

    /**
     * {@code VECTOR_TRUNC}'s dimension argument is a COMPILE-time constant that must fit the source
     * vector. Live-verified: a column ({@code VECTOR_TRUNC(v, n)}), an expression
     * ({@code 1+1}), a non-integral literal ({@code 2.5}) and {@code NULL} are all rejected with
     * "argument … to function VECTOR_TRUNC needs to be constant", an integral {@code 2.0} is accepted,
     * and {@code VECTOR_TRUNC(<VECTOR(FLOAT,3)>, 9)} is "Requested truncation dimension 9 for
     * VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (3)."
     */
    private void rejectIllegalTruncationDimension(final String funcName, final List<Expression> args,
                                                  final VectorType source) {
        if (args.size() < 2) {
            return;
        }
        final Expression dimension = args.get(1);
        final Long requested = constantDimension(dimension);
        if (requested == null) {
            throw new RuntimeException("argument 2 to function " + funcName
                + " needs to be constant, found '" + AstPrinterVisitor.print(dimension) + "'");
        }
        if (requested.longValue() < 0) {
            throw new RuntimeException("Invalid vector dimension '" + requested + "'.");
        }
        if (source != null && requested.longValue() > source.getDimension()) {
            throw new RuntimeException("Requested truncation dimension " + requested + " for " + funcName
                + " should be less than or equal to the dimension of the provided vector ("
                + source.getDimension() + ").");
        }
    }

    /** A literal whole-number dimension, or null when the expression is not one. */
    private static Long constantDimension(final Expression expr) {
        if (!(expr instanceof LiteralExpression)) {
            return null;
        }
        final Object value = ((LiteralExpression) expr).getValue();
        if (value instanceof Long || value instanceof Integer) {
            return Long.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigDecimal) {
            try {
                return Long.valueOf(((BigDecimal) value).toBigIntegerExact().longValueExact());
            } catch (final ArithmeticException notWhole) {
                return null;
            }
        }
        return null;
    }

    /**
     * MAP-position strictness: a {@link #MAP_STRICT_ARGS} position takes a {@code MAP(k, v)} and nothing
     * else, so any argument whose STATIC type is determined and is not a {@link MapType} fails with
     * Snowflake's argument-type error listing every argument's type. Undetermined types pass, as
     * everywhere else in this family of rules.
     *
     * <p>Phrased as a REQUIREMENT rather than as a list of refused classes, which is the shape the rest
     * of these rules use, because live refuses every family that is not a MAP and a list would have to
     * enumerate them: measured, {@code MAP_KEYS} alone reports "(OBJECT)", "(VARIANT)",
     * "(ARRAY)", "(OBJECT(x NUMBER(38,0)))", "(VARCHAR(3))" and "(NUMBER(1,0))" for the six spellings
     * tried, all with SQLSTATE 42P13. The requirement also keeps working as new types are added, where a
     * reject-list would silently start accepting them.
     *
     * <p>What makes this safe is that the MAP-returning built-ins DECLARE a MAP return type
     * ({@code MapFunctionHelper.MAP}), so a nested call is inferred as a MAP and passes — live agrees:
     * {@code MAP_KEYS(MAP_CAT(MAP_CONSTRUCT('a',1), MAP_CONSTRUCT('b',2)))} returns {@code ["a","b"]}.
     */
    private void rejectNonMapArgumentInStrictFunction(final String funcName, final List<Expression> args) {
        final int[] positions = MAP_STRICT_ARGS.get(funcName);
        if (positions == null) {
            return;
        }
        for (final int position : positions) {
            if (position >= args.size()) {
                continue;
            }
            final DataType inferred = typeInferencer.infer(args.get(position));
            if (inferred != null && !(inferred instanceof MapType)) {
                throw new RuntimeException("Invalid argument types for function '" + funcName
                    + "': (" + strictArgTypeList(args) + ")");
            }
        }
    }

    /**
     * {@code MAP_CONSTRUCT} refuses an argument whose static type is NULL, in the KEY half and the VALUE
     * half alike: live, {@code MAP_CONSTRUCT(NULL, 1)} and {@code MAP_CONSTRUCT('a', NULL)}
     * are both "SQL compilation error:\nFunction MAP_CONSTRUCT does not support NULL argument type"
     * (SQLSTATE 22000, vendor code 2016). Frostlake used to build {@code {}} and {@code {"a":null}} from
     * them.
     *
     * <p>Only the untyped NULL LITERAL is matched, deliberately. A NULL-valued COLUMN is fine — measured
     * over a two-row table, {@code MAP_CONSTRUCT(k, v)} returns {@code {}} on the row whose key is NULL
     * and {@code {"a":null}} on the row whose value is, which is the RUN-TIME rule the function
     * implements. Live also refuses the column form when EVERY row is NULL, but that is its optimiser
     * folding an all-NULL column to a NULL literal on the strength of partition metadata — a
     * data-dependent compile error Frostlake must not imitate, and the reason the same query was run
     * against two differently-populated tables before this rule was written.
     *
     * <p>Three further types are refused in the KEY half ONLY, and the grid was measured both ways
     * round rather than generalised from one side: {@code MAP_CONSTRUCT(TRUE,'x')},
     * {@code MAP_CONSTRUCT('2020-01-01'::DATE,'x')} and {@code MAP_CONSTRUCT('a'::VARIANT,1)} each give
     * the same sentence with their own type named, while {@code MAP_CONSTRUCT('a',TRUE)},
     * {@code MAP_CONSTRUCT('a','2020-01-01'::DATE)} and {@code MAP_CONSTRUCT('a',1::VARIANT)} are all
     * ACCEPTED — so this cannot be widened to "the type is refused" nor narrowed to "MAP_CONSTRUCT takes
     * only VARCHAR keys". BINARY, OBJECT and ARRAY keys were not measured and are left unconstrained.
     */
    private void rejectNullArgumentInMapConstruct(final String funcName, final List<Expression> args) {
        if (!"MAP_CONSTRUCT".equals(funcName)) {
            return;
        }
        for (int position = 0; position < args.size(); position++) {
            final Expression arg = args.get(position);
            if (arg instanceof LiteralExpression
                    && ((LiteralExpression) arg).getType() == LiteralType.NULL) {
                throw new RuntimeException(
                    "SQL compilation error:\nFunction MAP_CONSTRUCT does not support NULL argument type");
            }
            if (position % 2 != 0) {
                continue;   // a VALUE position takes all three of the types below
            }
            final DataType key = typeInferencer.infer(arg);
            if (key instanceof BooleanType || key instanceof VariantType
                    || (key instanceof DateTimeType && "DATE".equalsIgnoreCase(key.getName()))) {
                throw new RuntimeException("SQL compilation error:\nFunction MAP_CONSTRUCT does not support "
                    + semiStructuredTypeName(key) + " argument type");
            }
        }
    }

    /**
     * MAP is a STRUCTURED type, so an UNTYPED {@code NULL} is not one of its values either (live-verified:
     * {@code MAP_CAT(NULL, OBJECT_CONSTRUCT('b','2')::MAP(VARCHAR,VARCHAR))} errors "Invalid argument
     * types for function 'MAP_CAT': (NULL, MAP(VARCHAR(16777216), VARCHAR(16777216)))", while
     * {@code MAP_CAT(NULL::MAP(VARCHAR,VARCHAR), NULL::MAP(VARCHAR,VARCHAR))} is accepted and returns
     * NULL). The type inferencer reports both as undetermined, so the NULL literal is matched directly.
     */
    private void rejectUntypedNullMapArgument(final String funcName, final List<Expression> args) {
        final int[] positions = MAP_STRICT_ARGS.get(funcName);
        if (positions == null) {
            return;
        }
        for (final int position : positions) {
            if (position >= args.size()) {
                continue;
            }
            final Expression arg = args.get(position);
            if (arg instanceof LiteralExpression
                    && ((LiteralExpression) arg).getType() == LiteralType.NULL) {
                throw new RuntimeException("Invalid argument types for function '" + funcName
                    + "': (" + strictArgTypeList(args) + ")");
            }
        }
    }

    /**
     * Type-position strictness shared by the BOOLEAN-rejecting, BINARY-requiring and MAP-requiring
     * families: any argument whose STATIC type is in {@code rejected} fails with Snowflake's
     * argument-type error listing every argument's type. Undetermined types always pass.
     */
    private void rejectArgumentTypes(final String funcName, final List<Expression> args,
                                     final Map<String, int[]> family, final Class<?>[] rejected) {
        final int[] positions = family.get(funcName);
        if (positions == null) {
            return;
        }
        for (final int position : positions) {
            if (position >= args.size()) {
                continue;
            }
            final DataType inferred = typeInferencer.infer(args.get(position));
            if (inferred == null) {
                continue;
            }
            for (final Class<?> type : rejected) {
                if (type.isInstance(inferred)) {
                    throw new RuntimeException("Invalid argument types for function '" + funcName
                        + "': (" + strictArgTypeList(args) + ")");
                }
            }
        }
    }

    /**
     * TRY_CAST accepts only a STRING source expression (live-verified: {@code TRY_CAST('123' AS
     * NUMBER)} works while {@code TRY_CAST(123 AS VARCHAR)} errors "Function TRY_CAST cannot be
     * used with arguments of types NUMBER(3,0) and VARCHAR(134217728)"). An untyped NULL and an
     * undetermined expression pass. The SEMI-STRUCTURED types are rejected too — live-verified on a
     * real account, {@code TRY_CAST(PARSE_JSON('"2020-01-01"') AS DATE)} fails "Function
     * TRY_CAST cannot be used with arguments of types VARIANT and DATE" for EVERY target type tried
     * (DATE, NUMBER, VARCHAR, TIMESTAMP_NTZ, BOOLEAN, TIME), and OBJECT / ARRAY sources fail the same
     * way — while the plain {@code ::} cast of the identical value succeeds. That last sentence holds
     * only for a SCALAR target; {@link #semiStructuredTryCastPair} carries the semi-structured half.
     */
    private void rejectNonStringTryCastSource(final CastExpression expr) {
        if (!expr.isTryMode()) {
            return;
        }
        if (StructuredTypes.isStructured(expr.getDeclaredTarget())) {
            // A STRUCTURED target is the exception: live,
            // TRY_CAST(OBJECT_CONSTRUCT('x','a','e',1) AS OBJECT(x VARCHAR)) returns NULL (the key set
            // does not fit) and TRY_CAST(<OBJECT(x VARCHAR)> AS OBJECT(x VARCHAR)) returns the object —
            // so a semi-structured source is accepted here, unlike for the scalar targets probed above.
            return;
        }
        // The plain inferencer, deliberately: every pair measured below is one it already names, and
        // reading the wider inferSemiStructured here would newly classify aggregate and conditional
        // sources as semi-structured, tightening the REJECT list underneath by cases nobody probed.
        final DataType source = typeInferencer.infer(expr.getExpression());
        final DataType target = TypeInferencer.typeForName(expr.getTargetType());
        if (semiStructuredTryCastPair(source, target)) {
            return;
        }
        // A STRUCTURED source refused into a PLAIN OBJECT / ARRAY target names its own kind and
        // nothing else — live: TRY_CAST(so AS ARRAY) is "Unsupported data type 'STRUCTURED_OBJECT'.",
        // (sa AS OBJECT) 'STRUCTURED_ARRAY' and (sm AS ARRAY) 'MAP' (live, plus
        // the per-pair checks in semiStructuredTryCastPair).
        if ((target instanceof ObjectType || target instanceof ArrayType)
                && StructuredTypes.isStructured(source)) {
            throw new RuntimeException("SQL compilation error:\nUnsupported data type '"
                + structuredKindName(source) + "'.");
        }
        // The CONVERSION sentence fires for exactly the pairs live measured — a plain OBJECT source
        // into a NUMERIC target ("invalid type [TRY_CAST(STT.O)] for parameter 'TO_NUMBER'")
        // and a plain ARRAY source into a plain OBJECT target ('TO_OBJECT',
        // measured). Every OTHER plain pair keeps the TRY_CAST-arguments sentence, and that boundary is
        // measured, not assumed: TRY_CAST(a AS VARCHAR) is "Function TRY_CAST cannot be used with
        // arguments of types ARRAY and VARCHAR(134217728)" (live) and
        // TRY_CAST(o AS ARRAY) keeps it too (measured) — the sentence is per-pair, so no
        // generalisation beyond the measured cells.
        final boolean plainObjectSource = source instanceof ObjectType
            && !(source instanceof MapType) && !StructuredTypes.isStructured(source);
        final boolean plainArraySource = source instanceof ArrayType
            && !StructuredTypes.isStructured(source);
        final boolean plainObjectTarget = target instanceof ObjectType
            && !(target instanceof MapType) && !StructuredTypes.isStructured(target);
        // Measured cells only. The OBJECT target takes the conversion sentence for a plain-ARRAY AND
        // for a NUMBER source ("invalid type [TRY_CAST(STT.N)] for parameter 'TO_OBJECT'", live
        // spot-run ) — while its VARIANT source keeps 'cannot be used'.
        final boolean measuredConversionPair =
            (plainObjectTarget && (plainArraySource || source instanceof NumericType))
            || (plainObjectSource && target instanceof NumericType);
        if (measuredConversionPair) {
            throw new RuntimeException("SQL compilation error:\ninvalid type [TRY_CAST("
                + strictText(expr.getExpression()) + ")] for parameter '"
                + castConversionName(expr.getTargetType()) + "'");
        }
        if (source instanceof NumericType || source instanceof BooleanType
                || source instanceof DateTimeType || source instanceof BinaryType
                || source instanceof VariantType || source instanceof ObjectType
                || source instanceof ArrayType) {
            throw new RuntimeException("Function TRY_CAST cannot be used with arguments of types "
                + strictArgTypeText(expr.getExpression()) + " and " + castTargetTypeText(expr.getTargetType()));
        }
    }

    /** The kind name live prints for a structured value in the Unsupported-data-type sentence. */
    private static String structuredKindName(final DataType source) {
        if (source instanceof MapType) {
            return "MAP";
        }
        return source instanceof ArrayType ? "STRUCTURED_ARRAY" : "STRUCTURED_OBJECT";
    }

    /**
     * Whether TRY_CAST carries this source into this PLAIN semi-structured target — {@code VARIANT},
     * {@code OBJECT} or {@code ARRAY} written without parameters. The source rule above rejects every
     * semi-structured source, which is right for a SCALAR target and wrong here; live over
     * one table carrying all six semi-structured shapes, the accepted pairs go strictly by FAMILY:
     *
     * <ul>
     *   <li>{@code VARIANT} takes any semi-structured source — {@code TRY_CAST(o AS VARIANT)},
     *       {@code (a …)}, {@code (v …)}, {@code (so …)}, {@code (sa …)}, {@code (sm …)} all return
     *       the value unchanged ({@code TRY_CAST(o AS VARIANT) = o} is TRUE, and TYPEOF still reports
     *       what was put in: OBJECT, ARRAY, INTEGER, VARCHAR).</li>
     *   <li>{@code OBJECT} takes the OBJECT family only — plain OBJECT, structured OBJECT and MAP.
     *       {@code TRY_CAST(a AS OBJECT)} is "invalid type [TRY_CAST(ST.A)] for parameter 'TO_OBJECT'",
     *       {@code (sa …)} is "Unsupported data type 'STRUCTURED_ARRAY'." and {@code (v …)} is
     *       "Function TRY_CAST cannot be used with arguments of types VARIANT and OBJECT".</li>
     *   <li>{@code ARRAY} takes the ARRAY family only — plain ARRAY and structured ARRAY.
     *       {@code TRY_CAST(o AS ARRAY)} and {@code (v …)} give the TRY_CAST-arguments sentence,
     *       {@code (so …)} "Unsupported data type 'STRUCTURED_OBJECT'." and {@code (sm …)}
     *       "Unsupported data type 'MAP'.".</li>
     * </ul>
     *
     * <p>A VARIANT source is the sharp edge, and it was measured on two different expressions rather
     * than generalized from the column: {@code TRY_CAST(v AS VARIANT)} works while
     * {@code TRY_CAST(v AS OBJECT)} and {@code TRY_CAST(v AS ARRAY)} do not, and
     * {@code TRY_CAST(PARSE_JSON('{"k":1}') AS OBJECT)} fails the same way. So this cannot be widened
     * to "a semi-structured target takes a semi-structured source" — that would accept five pairs live
     * rejects.
     *
     * <p>A SCALAR source stays out, so {@code TRY_CAST(n AS VARIANT)}, {@code (b …)} and {@code (d …)}
     * keep falling through to the caller's reject list, which live agrees with. A VARCHAR source is
     * rejected live too but the caller's list does not name StringType, so it is over-accepted — a
     * PRE-EXISTING gap left standing here rather than closed in passing, and recorded with the other
     * TRY_CAST over-acceptances in {@code docs/functions.md}. An untyped NULL and an undetermined
     * expression are undetermined here and fall through as well, where they pass — matching
     * {@code TRY_CAST(NULL AS OBJECT)} live.
     *
     * <p>This is a TRY_CAST-only shape. Live, plain {@code CAST} and {@code ::} are LOOSER over the
     * very same pairs — {@code CAST(o AS ARRAY)} wraps into {@code [{"k":"v1"}]} and
     * {@code CAST(v AS OBJECT)} succeeds, where both TRY_CAST spellings error — so neither may be
     * routed through this predicate.
     */
    private static boolean semiStructuredTryCastPair(final DataType source, final DataType target) {
        if (target instanceof VariantType) {
            return source instanceof VariantType || source instanceof ObjectType
                || source instanceof ArrayType || source instanceof MapType;
        }
        if (target instanceof ObjectType) {
            return source instanceof ObjectType || source instanceof MapType;
        }
        if (target instanceof ArrayType) {
            return source instanceof ArrayType;
        }
        return false;
    }

    /**
     * The TRY_TO_&lt;TYPE&gt; functions are TRY_CAST under the hood and inherit its source rule: only
     * a VARCHAR converts. An UNTYPED {@code NULL} literal is rejected as well (it is not a VARCHAR);
     * {@code NULL::VARCHAR} passes, as does an undetermined expression.
     */
    private void rejectNonStringTryToSource(final String funcName, final List<Expression> args) {
        final String target = TRY_TO_TARGET_TYPES.get(funcName);
        if (target == null || args.isEmpty()) {
            return;
        }
        final Expression source = args.get(0);
        final DataType inferred = typeInferencer.infer(source);
        final boolean untypedNull = inferred == null && source instanceof LiteralExpression;
        // The semi-structured types are rejected exactly like TRY_CAST's own source rule
        // (live: TRY_TO_NUMBER(PARSE_JSON('1')) fails "…types VARIANT and NUMBER(38,0)").
        if (!untypedNull && !(inferred instanceof NumericType) && !(inferred instanceof BooleanType)
                && !(inferred instanceof DateTimeType) && !(inferred instanceof BinaryType)
                && !(inferred instanceof VariantType) && !(inferred instanceof ObjectType)
                && !(inferred instanceof ArrayType)) {
            return;
        }
        throw new RuntimeException("Function TRY_CAST cannot be used with arguments of types "
            + (untypedNull ? "NULL" : strictArgTypeText(source)) + " and " + target);
    }

    /**
     * A cast target as Snowflake renders it in an argument-type error: bare type names gain their
     * default parameters ({@code VARCHAR(134217728)}, {@code NUMBER(38,0)}, {@code BINARY(67108864)},
     * {@code TIME(9)}), an explicitly parameterized target keeps what was written.
     */
    private static String castTargetTypeText(final String targetType) {
        final String written = targetType.trim();
        if (written.indexOf('(') > 0) {
            return written.toUpperCase().replace(" ", "");
        }
        switch (written.toUpperCase()) {
            case "VARCHAR": case "STRING": case "TEXT": case "CHAR": case "CHARACTER":
                return "VARCHAR(134217728)";
            case "NUMBER": case "DECIMAL": case "NUMERIC": case "INT": case "INTEGER":
            case "BIGINT": case "SMALLINT": case "TINYINT": case "BYTEINT":
                return "NUMBER(38,0)";
            case "BINARY": case "VARBINARY":
                return "BINARY(67108864)";
            case "TIME":
                return "TIME(9)";
            case "TIMESTAMP": case "TIMESTAMP_NTZ": case "DATETIME":
                return "TIMESTAMP_NTZ(9)";
            default:
                return written.toUpperCase();
        }
    }

    /**
     * TO_CHAR / TO_VARCHAR take a FORMAT only for non-string inputs: over a VARCHAR the function is
     * single-argument and a format is an arity error (live-verified: {@code TO_CHAR('hello',
     * 'YYYY-MM-DD')} errors "too many arguments for function [TO_CHAR('hello', 'YYYY-MM-DD')]
     * expected 1, got 2").
     */
    private void rejectFormatOverStringInToChar(final String funcName, final List<Expression> args,
                                                final Expression call) {
        if (!funcName.equals("TO_CHAR") && !funcName.equals("TO_VARCHAR")) {
            return;
        }
        if (args.size() < 2 || !(typeInferencer.infer(args.get(0)) instanceof StringType)) {
            return;
        }
        throw new RuntimeException("too many arguments for function [" + AstPrinterVisitor.print(call)
            + "] expected 1, got " + args.size());
    }

    /** All argument types as Snowflake renders them in an argument-type error, comma-joined. */
    private String strictArgTypeList(final List<Expression> args) {
        final StringBuilder text = new StringBuilder();
        for (final Expression arg : args) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(strictArgTypeText(arg));
        }
        return text.toString();
    }

    /**
     * The Snowflake-shaped type text of one argument: parameterized like Snowflake's messages —
     * a string literal carries its own length ({@code VARCHAR(7)}), a declared column its declared
     * length (default {@code VARCHAR(16777216)}), integers {@code NUMBER(<digits>,0)}, timestamps
     * {@code TIMESTAMP_NTZ(9)}, binary {@code BINARY(8388608)} (all live-verified shapes).
     */
    private String strictArgTypeText(final Expression arg) {
        if (arg instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) arg;
            final Object value = literal.getValue();
            switch (literal.getType()) {
                case STRING:
                    return "VARCHAR(" + String.valueOf(value).length() + ")";
                case INTEGER:
                    return "NUMBER(" + String.valueOf(value).replace("-", "").length() + ",0)";
                case DECIMAL: {
                    final String textValue = String.valueOf(value).replace("-", "");
                    final int dot = textValue.indexOf('.');
                    final int scale = dot < 0 ? 0 : textValue.length() - dot - 1;
                    return "NUMBER(" + (textValue.length() - (dot < 0 ? 0 : 1)) + "," + scale + ")";
                }
                case BOOLEAN:
                    return "BOOLEAN";
                default:
                    return "NULL";
            }
        }
        // The semi-structured refinement is read here too: live names a conditional over OBJECT
        // branches and an ARRAY_AGG result by what they ARE — "Invalid argument types for function
        // 'UPPER': (OBJECT)" for UPPER(IFF(TRUE, o, o)) and "(ARRAY)" for UPPER(ARRAY_AGG(v)) — where
        // the general inference alone says only "undetermined" and would have printed VARIANT.
        final DataType inferred = inferStaticType(arg);
        if (inferred == null) {
            return "VARIANT";
        }
        if (StructuredTypes.isStructured(inferred)) {
            // Snowflake names the whole structured type in the message — "MAP(VARCHAR(134217728),
            // VARCHAR(134217728))", "OBJECT(x VARCHAR(134217728))" — not just its base family.
            return StructuredTypes.describe(inferred);
        }
        if (inferred instanceof StringType) {
            final int maxLength = ((StringType) inferred).getMaxLength();
            return "VARCHAR(" + (maxLength > 0 ? maxLength : 16777216) + ")";
        }
        if (inferred instanceof NumericType) {
            final NumericType numeric = (NumericType) inferred;
            final String name = numeric.getName().toUpperCase();
            if (name.contains("FLOAT") || name.contains("DOUBLE") || name.contains("REAL")) {
                return "FLOAT";
            }
            final int precision = numeric.getPrecision() > 0 ? numeric.getPrecision() : 38;
            return "NUMBER(" + precision + "," + Math.max(numeric.getScale(), 0) + ")";
        }
        if (inferred instanceof BinaryType) {
            return "BINARY(8388608)";
        }
        if (inferred instanceof DateTimeType) {
            final String name = inferred.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "DATE";
            }
            if (name.equals("TIME")) {
                return "TIME(9)";
            }
            return "TIMESTAMP_NTZ(9)";
        }
        return inferred.getName().toUpperCase();
    }

    /** The declared table column a reference points at, or null when it cannot be resolved (an
     *  alias, a UDF parameter, a lateral value, ...) or when its declared type is a placeholder.
     *  Two kinds of column carry a trustworthy type: a BASE catalog table's, and a DERIVED
     *  relation's whose type the inner projection could infer (see {@link #hasTrustedType}). */
    TableColumn resolveDeclaredColumn(final ColumnReferenceExpression ref) {
        final String columnName = ref.getColumnName();
        if (ref.getTableName() != null) {
            final Table qualified = tableForAlias(ref.getTableName());
            if (qualified != null && qualified.hasColumn(columnName)
                    && hasTrustedType(qualified, columnName)) {
                return qualified.getColumn(columnName);
            }
            if (table != null && table.getName().equalsIgnoreCase(ref.getTableName())
                    && table.hasColumn(columnName) && hasTrustedType(table, columnName)) {
                return table.getColumn(columnName);
            }
            return null;
        }
        if (table != null && table.hasColumn(columnName)) {
            return hasTrustedType(table, columnName) ? table.getColumn(columnName) : null;
        }
        if (multiTableAllTables != null) {
            for (final Table candidate : multiTableAllTables) {
                if (candidate.hasColumn(columnName)) {
                    return hasTrustedType(candidate, columnName) ? candidate.getColumn(columnName) : null;
                }
            }
        }
        return null;
    }

    /** The table a qualifier names, matched case-insensitively by alias and then by table name. The
     *  multi-table map is keyed by whatever text the FROM clause used, so a lower-case alias
     *  ({@code FROM t JOIN (...) j}) is not found by an upper-cased key alone. */
    private Table tableForAlias(final String qualifier) {
        if (multiTableAliasToTable == null) {
            return null;
        }
        final Table exact = multiTableAliasToTable.get(qualifier.toUpperCase());
        if (exact != null) {
            return exact;
        }
        for (final Map.Entry<String, Table> entry : multiTableAliasToTable.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(qualifier)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Plan-time column-reference validation, so live's COMPILE-TIME rejections fire over zero rows
     * too: a qualifier naming no FROM-clause key (an alias REPLACES the table name) and a bare
     * name carried by both sides of an ON join (ambiguous). Skipped under an outer lateral
     * context — a correlated evaluation resolves through the outer scope at row time, which a
     * plan-time walk cannot see. The row-time guards stay as the backstop for every path that
     * does not run this walk.
     */
    private void validateColumnReferenceScope(final ColumnReferenceExpression ref) {
        if (lateralContext != null
                || (queryExecutor != null && queryExecutor.isInLateralExecution())) {
            // A correlated / lateral evaluation resolves OUTER names per row; the plan-time walk
            // cannot see them, so scope validation stays row-time there.
            return;
        }
        final String columnName = ref.getColumnName();
        if (ref.isQualified()) {
            if (fromClauseKeys != null && !fromClauseKeys.isEmpty()
                    && ref.getTableName().indexOf('.') < 0
                    && !"NEXTVAL".equalsIgnoreCase(columnName)
                    && !"CURRVAL".equalsIgnoreCase(columnName)
                    && !qualifierIsAFromClauseKey(ref.getTableName())) {
                throw new InvalidQualifierException(
                    ref.getTableName().toUpperCase() + "." + columnName.toUpperCase());
            }
            return;
        }
        // A bare name that is a SELECT output alias resolves through the alias in WHERE / GROUP BY /
        // HAVING / QUALIFY / ORDER BY (all alias-visible in Snowflake), so it is never rejected here.
        if (scopeExemptNames != null && scopeExemptNames.contains(columnName.toUpperCase())) {
            return;
        }
        if (table != null && multiTableAllTables != null && multiTableAllTables.size() > 1
                && (table.getJoinKeyNames() == null || table.getJoinKeyNames().isEmpty())
                && !isJoinKeyName(columnName)
                && countTablesCarrying(columnName) > 1) {
            throw new AmbiguousColumnException(columnName.toUpperCase());
        }
        // A bare name known NOWHERE in scope is live's compile-time "invalid identifier 'NOSUCH'",
        // over empty inputs too. Exempt beyond the output aliases above: the six context functions
        // Snowflake accepts WITHOUT parentheses (exactly these six — CURRENT_ROLE and SYSDATE bare
        // are rejected on a real account), the date/time-unit barewords the grammar parses as
        // column references — but only in ARGUMENT position (DATEADD(HOUR, …)); a top-level
        // `WHERE year = 1` is rejected live — and the hierarchy pseudo-column encodings
        // (PRIOR$x / CONNECT_BY_ROOT$x), whose resolution and whose own outside-a-hierarchy
        // error shapes live at row time.
        final String upperName = columnName.toUpperCase();
        if (!columnKnownInScope(columnName)
                && !isJoinKeyName(columnName)
                && !PARENLESS_CONTEXT_NAMES.contains(upperName)
                && !(strictWalkInsideFunctionArgs && isDateTimeUnitKeyword(upperName))
                && !upperName.startsWith("PRIOR$")
                && !upperName.startsWith("CONNECT_BY_ROOT$")) {
            throw new InvalidQualifierException(upperName);
        }
    }

    /** The date/time-unit barewords the grammar parses as column references when written as
     *  function arguments ({@code DATEADD(HOUR, …)}); resolved to their own name as a string. */
    private static boolean isDateTimeUnitKeyword(final String upper) {
        switch (upper) {
            case "YEAR": case "YEARS": case "QUARTER": case "MONTH": case "MONTHS":
            case "WEEK": case "WEEKS": case "DAY": case "DAYS": case "HOUR": case "HOURS":
            case "MINUTE": case "MINUTES": case "SECOND": case "SECONDS":
            case "MILLISECOND": case "MILLISECONDS": case "MICROSECOND": case "MICROSECONDS":
            case "NANOSECOND": case "NANOSECONDS":
            case "DAYOFWEEK": case "DAYOFWEEKISO": case "DAYOFYEAR": case "DAYOFMONTH":
            case "WEEKOFYEAR": case "WEEKISO": case "ISOWEEK":
            case "EPOCH": case "EPOCH_SECOND": case "EPOCH_MILLISECOND":
            case "EPOCH_MICROSECOND": case "EPOCH_NANOSECOND":
                return true;
            default:
                return false;
        }
    }

    /** Whether {@code name} is a column of any relation in scope; lenient (true) with no context. */
    private boolean columnKnownInScope(final String name) {
        if (table == null && (multiTableAllTables == null || multiTableAllTables.isEmpty())) {
            return true;
        }
        if (table != null && tableCarriesColumn(table, name)) {
            return true;
        }
        if (multiTableAllTables != null) {
            for (final Table candidate : multiTableAllTables) {
                if (tableCarriesColumn(candidate, name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tableCarriesColumn(final Table candidate, final String name) {
        for (final TableColumn col : candidate.getColumns()) {
            if (col.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Bare names exempt from the plan-time scope rejection — the enclosing query's SELECT output
     *  aliases / output column names, which the non-SELECT clauses may legally reference. */
    public void setScopeExemptNames(final Set<String> names) {
        this.scopeExemptNames = names;
    }

    /** Whether {@code qualifier} matches one of the FROM clause's relation keys, case-insensitively.
     *  Every legitimately-referencable relation is registered under exactly its key — the alias when
     *  one was written, else the table name — so the key set is the whole rule. */
    private boolean qualifierIsAFromClauseKey(final String qualifier) {
        for (final String key : fromClauseKeys) {
            if (key != null && key.equalsIgnoreCase(qualifier)) {
                return true;
            }
        }
        return false;
    }

    /** How many of the joined tables carry a column named {@code name}, case-insensitively. */
    private int countTablesCarrying(final String name) {
        int count = 0;
        for (final Table candidate : multiTableAllTables) {
            if (candidate != null && candidate.hasColumn(name)) {
                count++;
            }
        }
        return count;
    }

    /** Whether {@code columnName} names a USING / NATURAL join key of the current relation. */
    private boolean isJoinKeyName(final String columnName) {
        if (table == null || table.getJoinKeyNames() == null) {
            return false;
        }
        for (final String keyName : table.getJoinKeyNames()) {
            if (keyName.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    /** The merged value of a join key over a combined row: the first non-null among the per-side
     *  copies, each located through its side table's segment of the row. Null only when every copy
     *  is null (both sides null-extended or a genuinely NULL key). */
    private Object coalescedJoinKeyValue(final String columnName, final Row row) {
        int offset = 0;
        for (final Table side : multiTableAllTables) {
            final List<TableColumn> sideColumns = side.getColumns();
            for (int i = 0; i < sideColumns.size(); i++) {
                if (sideColumns.get(i).getName().equalsIgnoreCase(columnName)) {
                    if (offset + i < row.getValues().size()) {
                        final Object value = row.getValue(offset + i);
                        if (value != null) {
                            return value;
                        }
                    }
                    break;
                }
            }
            offset += sideColumns.size();
        }
        return null;
    }

    /** The merged join-key value read off the joined relation itself (no per-side segments): the
     *  first non-null among its same-named columns — the visible left copy and the star-hidden
     *  right duplicate. */
    private Object coalescedJoinKeyFromTable(final String columnName, final Row row) {
        final List<TableColumn> columns = table.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(columnName)
                    && i < row.getValues().size()) {
                final Object value = row.getValue(i);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    /** Whether a column's declared type may be read as its STATIC type: it belongs to a base catalog
     *  table, or the projection that produced this derived relation inferred it. Every other synthetic
     *  table (the window stage's projected shape, DUAL, a stage scan, a table function) declares
     *  placeholder types and stays undetermined, so it can never trigger a type-based rejection. */
    private boolean hasTrustedType(final Table owner, final String columnName) {
        return isBaseCatalogTable(owner) || owner.getColumn(columnName).isStaticallyTyped();
    }

    /**
     * The statically-known type of {@code expr} in this table context, or null when undetermined — the
     * projection-time entry point that gives a derived relation's columns their declared types.
     *
     * <p>Reads the semi-structured refinement where the general inference says only VARIANT, because
     * that is what a derived column's type IS: live, {@code SYSTEM$TYPEOF} over
     * {@code (SELECT OBJECT_CONSTRUCT('a',1) AS x FROM t)} reports OBJECT, over
     * {@code (SELECT ARRAY_AGG(n) AS x ...)} reports ARRAY and over
     * {@code (SELECT CASE WHEN TRUE THEN o ELSE o END AS x ...)} reports OBJECT — while
     * {@code PARSE_JSON}, {@code TO_VARIANT} and colon-path access all report VARIANT and are left
     * alone. So the OBJECT/ARRAY producers this refinement knows are exactly the ones live names.
     */
    public DataType inferStaticType(final Expression expr) {
        final DataType direct = typeInferencer.infer(expr);
        if (direct != null && !(direct instanceof VariantType)) {
            return direct;
        }
        final DataType semiStructured = typeInferencer.inferSemiStructured(expr);
        return semiStructured != null ? semiStructured : direct;
    }

    /** True when this Table instance is a base table the catalog holds — a derived/virtual table
     *  (CTE, VALUES, joined view, self-join copy) is a different instance and was never registered.
     *  Answered by IDENTITY rather than by re-resolving the table's bare NAME, which used to make
     *  every schema-qualified {@code FROM} look like a derived relation (see
     *  {@link Table#isCatalogResident()}). */
    private boolean isBaseCatalogTable(final Table candidate) {
        return candidate.isCatalogResident();
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

    /**
     * Colon path access over a declared VARCHAR or FILE column is a COMPILE error in Snowflake — it is
     * sugar for GET, which requires a VARIANT base (live: {@code data:name} over a VARCHAR column
     * errors "Invalid argument types for function 'GET': (VARCHAR(16777216), VARCHAR(4))", and
     * {@code f:RELATIVE_PATH} over a FILE column errors "…: (FILE, VARCHAR(13))").
     */
    private void rejectStringBaseInPathAccess(final ObjectAccessExpression expr) {
        final DataType inferred = typeInferencer.infer(expr.getBase());
        final String baseType;
        if (inferred instanceof StringType) {
            final int maxLength = ((StringType) inferred).getMaxLength();
            baseType = "VARCHAR(" + (maxLength > 0 ? maxLength : 16777216) + ")";
        } else if (inferred instanceof FileType) {
            // A FILE value IS an object of file metadata, but Snowflake will not let the colon operator
            // read it — live, f:RELATIVE_PATH over a FILE column errors "Invalid argument
            // types for function 'GET': (FILE, VARCHAR(13))". The FL_GET_* accessors are the way in.
            baseType = "FILE";
        } else if (GeoTypes.isGeo(inferred)) {
            // A geo value DISPLAYS as GeoJSON, so the colon operator looked like it should read it —
            // Frostlake used to answer "Point" for g:type. Live refuses it exactly as it
            // refuses the GET spelling: "Invalid argument types for function 'GET': (GEOGRAPHY,
            // VARCHAR(4))". ST_ASGEOJSON(g) produces an OBJECT the colon operator then reads.
            baseType = inferred.getName().toUpperCase();
        } else {
            return;
        }
        final String key = expr.getPathParts().isEmpty() ? "" : expr.getPathParts().get(0);
        throw new RuntimeException("Invalid argument types for function 'GET': ("
            + baseType + ", VARCHAR(" + key.length() + "))");
    }

    @Override
    public Object visitObjectAccess(final ObjectAccessExpression expr) {
        rejectStringBaseInPathAccess(expr);
        Object baseValue = expr.getBase().accept(this);

        if (baseValue == null) {
            return null;
        }

        // Traverse the property chain using the path segments from the parse tree (data:a.b.c → [a,b,c]).
        Object current = baseValue;
        for (final String part : expr.getPathParts()) {
            if (current == null) return null;
            // A typed wrapper extracts from its parsed tree (no canonical-text round trip, which
            // loses the number family); everything else parses its JSON text.
            if (current instanceof VariantValue) {
                current = JsonPathExtractor.extractProperty(((VariantValue) current).node(), part);
            } else {
                current = extractJsonProperty(jsonTextOf(current), part);
            }
        }
        // NOTE (open lead): Snowflake renders a string path result QUOTED ("Alice") because the
        // result stays VARIANT; the engine returns the unwrapped string. Wrapping here double-encodes
        // the structural-string idioms (OBJECT_CONSTRUCT/ARRAY_AGG re-embedding, VARCHAR-cast
        // round-trips), so the unwrapped shape is kept deliberately.
        return current;
    }

    @Override
    public Object visitArrayAccess(final ArrayAccessExpression expr) {
        Object arrayValue = expr.getArray().accept(this);
        Object indexValue = expr.getIndex().accept(this);

        if (arrayValue == null || indexValue == null) {
            return null;
        }

        // A numeric subscript indexes into a JSON array; a string subscript (col['key']) is object member
        // access — Snowflake's bracket notation, equivalent to col:key. A typed wrapper extracts from
        // its parsed tree (see visitObjectAccess).
        if (arrayValue instanceof VariantValue) {
            final JsonNode rootNode = ((VariantValue) arrayValue).node();
            if (indexValue instanceof Number) {
                return JsonPathExtractor.extractElement(rootNode, ((Number) indexValue).intValue());
            }
            return JsonPathExtractor.extractProperty(rootNode, indexValue.toString());
        }
        final String jsonString = jsonTextOf(arrayValue);
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
            final Object value = element.accept(this);
            // A SQL NULL ELEMENT is the VARIANT `undefined`, not a JSON null — live-verified:
            // SELECT [1, NULL, 2] is [1,undefined,2] and SELECT [{'a': NULL}, NULL] is [{},undefined]
            // (the object pair is dropped, the array element becomes undefined).
            json.append(value == null ? UndefinedNode.TOKEN : jsonElementText(element, value));
        }

        json.append("]");
        return canonicalJsonOrText(json.toString());
    }

    /**
     * Canonical form of an assembled object/array literal — keys sorted, compact separators — so a
     * {@code [{'edition': 'pro*'}]} literal compares equal to the same value extracted from a VARIANT
     * (Snowflake stores both canonically; keeping the literal's source spacing made EXCEPT see a diff).
     */
    private Object canonicalJsonOrText(final String jsonText) {
        final JsonNode node = ArrayFunctionHelper.parseNode(jsonText);
        return node != null ? ArrayFunctionHelper.toCanonicalVariant(node) : jsonText;
    }

    @Override
    public Object visitLikeAnyAll(final LikeAnyAllExpression expr) {
        final Object subject = expr.getSubject().accept(this);
        if (subject == null) {
            return null;
        }
        final BinaryOperator op = expr.isCaseInsensitive() ? BinaryOperator.ILIKE : BinaryOperator.LIKE;
        final char escapeChar = resolveEscapeChar(expr.getEscape());
        // Live-verified Snowflake semantics: NULL patterns are SKIPPED (no three-valued logic over
        // the pattern list) — 'a' LIKE ALL ('a', NULL) is TRUE, 'a' LIKE ANY ('b', NULL) is FALSE —
        // and only an all-NULL pattern list yields NULL.
        boolean sawPattern = false;
        for (final Expression patternExpr : expr.getPatterns()) {
            final Object pattern = patternExpr.accept(this);
            if (pattern == null) {
                continue;
            }
            sawPattern = true;
            final boolean matches = Boolean.TRUE.equals(evaluateLike(subject, pattern, op, escapeChar));
            if (expr.isAll()) {
                if (!matches) {
                    return Boolean.FALSE;
                }
            } else if (matches) {
                return Boolean.TRUE;
            }
        }
        if (!sawPattern) {
            return null;
        }
        return expr.isAll() ? Boolean.TRUE : Boolean.FALSE;
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
        // Evaluate each part of the (possibly multi-part) interval into a chained IntervalValue,
        // applied in order by date/time arithmetic.
        IntervalValue chain = null;
        final List<IntervalExpression> parts = new ArrayList<>();
        for (IntervalExpression part = expr; part != null; part = part.getRest()) {
            parts.add(part);
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            final IntervalExpression part = parts.get(i);
            chain = new IntervalValue(part.getValueExpression().accept(this), part.getUnit(), chain);
        }
        return chain;
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
        // A TYPED semi-structured value already proves its own JSON-null-ness, whatever expression
        // produced it — the operand-shape test below is only needed for the legacy untyped carrier (a
        // bare "null" String), which a plain 'null' VARCHAR would otherwise be confused with. Without
        // this, COALESCE/IFNULL/NVL over a JSON null cast wrong: live,
        // COALESCE(PARSE_JSON('{"b":null}'):b, 9)::VARCHAR is SQL NULL (COALESCE returns the JSON null
        // and the cast nulls it), not the text 'null' and not 9.
        if (VariantJsonNulls.isJsonNull(value)) {
            return isNonSemiStructuredCastTarget(expr);
        }
        final boolean jsonNullText = value instanceof CharSequence && "null".equals(value.toString());
        if (!jsonNullText) {
            return false;
        }
        if (!hasVariantValuedOperand(expr.getExpression())) {
            return false;
        }
        return isNonSemiStructuredCastTarget(expr);
    }

    /**
     * Whether an operand reads as SQL NULL in a SCALAR context: it either IS SQL NULL, or it is the
     * VARIANT JSON null, which has no scalar reading (live: {@code jn || 'x'} and
     * {@code -jn} are SQL NULL for {@code jn = PARSE_JSON('{"b":null}'):b}).
     */
    private static boolean readsAsSqlNull(final Object value) {
        return value == null || VariantJsonNulls.isJsonNull(value);
    }

    /** Whether the cast target is an ordinary SQL type rather than VARIANT / ARRAY / OBJECT. */
    private boolean isNonSemiStructuredCastTarget(final CastExpression expr) {
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

    /**
     * The JSON text of a value for path extraction: a semi-structured wrapper's CANONICAL text —
     * never toString(), which renders XML-shaped variants as XML — otherwise the value's text.
     */
    private static String jsonTextOf(final Object value) {
        if (value instanceof VariantValue) {
            return ((VariantValue) value).text();
        }
        return value.toString();
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
        if (value instanceof VariantValue) {
            // A semi-structured runtime value embeds as its JSON text — a nested node, never quoted.
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
