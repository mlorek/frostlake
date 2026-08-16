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

import dev.frostlake.executor.AmbiguousColumnException;
import dev.frostlake.executor.ColumnLengthException;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.InvalidQualifierException;
import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.ProceduralExecutor;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.SignedStorageWidth;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StarArgument;
import dev.frostlake.executor.SystemTypeOfDescription;
import dev.frostlake.executor.UndeclaredScriptVariableException;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.HigherOrderFunctionNames;
import dev.frostlake.functions.IncomparableArgumentsException;
import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.SystemFunctionArity;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.conversion.ToUuid;
import dev.frostlake.functions.scalar.math.RoundingModeNames;
import dev.frostlake.functions.scalar.string.Concat;
import dev.frostlake.functions.scalar.vector.VectorTrunc;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.UserTaskCancellation;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.FileType;
import dev.frostlake.types.GeoTypes;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.ApproximateValues;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.RelationStatistics;
import dev.frostlake.values.ValueRange;
import dev.frostlake.values.VariantJsonNulls;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates expression AST using the visitor pattern
 */
public class ExpressionEvaluatorVisitor implements ExpressionVisitor<Object> {

    private final Table table;
    private Row row;
    private final FunctionRegistry functionRegistry;
    /**
     * The relation a reference's DECLARED type is read from when this evaluator's own table cannot
     * say — a window stage over grouped rows evaluates against the projected shape, whose slots
     * carry values but not every declaration. Null unless a caller installed one.
     */
    private Table declaredTypeBase;
    private Map<String, Table> declaredTypeAliasToTable;
    private List<Table> declaredTypeAllTables;
    private final TypeInferencer typeInferencer = new TypeInferencer(this);
    /** The intervals scalar subqueries' items were planned to lie in, keyed by the subquery node. */
    private final Map<SubqueryExpression, ValueRange> subqueryRanges = new HashMap<>();
    private final ValueRangeInferencer valueRangeInferencer = new ValueRangeInferencer(this);
    private final Catalog catalog;
    private QueryExecutor queryExecutor;
    private Map<String, Object> lateralContext;
    // Whether the relation this visitor reads is a PROJECTION SCOPE rather than a relation of the
    // query: a FROM-less select's own item list. Such a scope answers the clauses of the select that
    // published it and nothing further in, so a subquery written inside one of those clauses must not
    // correlate to it (live-verified).
    private boolean scopeOpaqueToSubqueries;
    private Map<String, Table> multiTableAliasToTable;
    private List<Table> multiTableAllTables;
    // Per-context memo of how each column reference resolved against the CURRENT multi-table
    // context: a fixed flat row offset (Integer) for plain column hits, MULTI_TABLE_DYNAMIC for
    // per-row decisions (merged join keys, literal fallbacks), MULTI_TABLE_MISS for references the
    // joined tables do not carry. Keyed by node IDENTITY (the cached expression AST is shared) and
    // cleared whenever a different table list arrives. Resolution caching only — never an index.
    private final Map<ColumnReferenceExpression, Object> multiTableRefMemo =
        new IdentityHashMap<ColumnReferenceExpression, Object>();
    private static final Object MULTI_TABLE_DYNAMIC = new Object();
    private static final Object MULTI_TABLE_MISS = new Object();
    // Canonical prints are PURE node functions, but the resultContext lookups re-rendered the same
    // nodes per ROW. Bounded wholesale-clear keeps nodes of evicted cached ASTs from lingering in
    // long-lived visitors.
    private final Map<Expression, String> canonicalPrintMemo =
        new IdentityHashMap<Expression, String>();

    private String canonicalPrintOf(final Expression expr) {
        String print = canonicalPrintMemo.get(expr);
        if (print == null) {
            print = AstPrinterVisitor.print(expr);
            if (canonicalPrintMemo.size() >= 8192) {
                canonicalPrintMemo.clear();
            }
            canonicalPrintMemo.put(expr, print);
        }
        return print;
    }
    private Collection<String> fromClauseKeys;
    private Set<String> scopeExemptNames;
    private Set<String> outputScopeNames;
    /** The static types of the query's SELECT output aliases, keyed by upper-cased name, or null. */
    private Map<String, DataType> outputAliasTypes;
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

    /** See {@link #scopeOpaqueToSubqueries}. */
    public void setScopeOpaqueToSubqueries(final boolean opaque) {
        this.scopeOpaqueToSubqueries = opaque;
    }

    /** Whether a subquery evaluated here may read this visitor's relation as an outer row. */
    boolean isScopeOpaqueToSubqueries() {
        return scopeOpaqueToSubqueries;
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
    /**
     * Install the relation a reference's DECLARED type falls back to. Only a name this evaluator's own
     * table does not carry at all reaches it: a slot the projection typed answers for itself, and one
     * it could not type stays undetermined rather than borrowing a same-named base column's type.
     *
     * @param base         the base relation, or null to clear
     * @param aliasToTable its FROM-clause alias map, or null
     * @param allTables    its joined relations, or null
     */
    public void setDeclaredTypeBase(final Table base, final Map<String, Table> aliasToTable,
                                    final List<Table> allTables) {
        if (base != this.declaredTypeBase) {
            typeInferencer.clearMemo();
        }
        this.declaredTypeBase = base;
        this.declaredTypeAliasToTable = aliasToTable;
        this.declaredTypeAllTables = allTables;
    }

    public void setMultiTableContext(final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (allTables != this.multiTableAllTables) {
            // A different joined-table list means a different flat row layout: cached offsets,
            // miss verdicts and static-type verdicts no longer apply.
            multiTableRefMemo.clear();
            typeInferencer.clearMemo();
        }
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

    /**
     * The bare DML DEFAULT reached EVALUATION, which means it was not standing alone as a value — the
     * write paths take the marker before this. Live agrees exactly here: `SET c = DEFAULT + 1` and
     * `SELECT default FROM t` are both "invalid identifier 'DEFAULT'".
     */
    @Override
    public Object visitDefaultMarker(final DefaultMarkerExpression expr) {
        final SourcePosition at = ExpressionSource.resolve(expr.getWhere());
        final SourcePosition where = at == null ? expr.getWhere() : at;
        throw new RuntimeException(where == null
            ? SqlCompilationError.invalidIdentifier("DEFAULT")
            : SqlCompilationError.invalidIdentifier(
                where.getLine(), where.getCharPositionInLine(), "DEFAULT"));
    }

    @Override
    public Object visitLiteral(final LiteralExpression expr) {
        return expr.getValue();
    }

    @Override
    public Object visitSystemStreamHasData(final SystemStreamHasDataExpression expr) {
        final Object nameVal = expr.getStreamNameExpr().accept(this);
        if (nameVal == null) return false;
        final String argAsWritten = nameVal.toString().replaceAll("^'|'$", "");
        final String streamName = argAsWritten.toUpperCase();
        if (queryExecutor == null) return false;
        Stream stream = null;
        try {
            // The name may be schema- or db-qualified ('BASE_TRANSFORM.STREAM_X') — resolving the whole
            // dotted text as a bare name in the current schema silently returned FALSE, so every loader
            // gated on SYSTEM$STREAM_HAS_DATA skipped its branch.
            stream = queryExecutor.getCatalog().resolveStream(streamName);
        } catch (final Exception notFound) {
            // Fall through to the refusal below — an unknown stream is a COMPILE error, not FALSE.
        }
        if (stream == null) {
            // Live refuses an unknown stream, echoing the argument AS WRITTEN inside the brackets.
            throw new RuntimeException(SqlCompilationError.of("Invalid value ['" + argAsWritten
                + "'] for function 'SYSTEM$STREAM_HAS_DATA', parameter 1: must be a valid stream name"));
        }
        // A stream "has data" when it has unconsumed change records — NOT when a same-named table has
        // rows (streams aren't stored as tables). Mirrors Snowflake SYSTEM$STREAM_HAS_DATA.
        return stream.getUnconsumedCount() > 0;
    }

    @Override
    public Object visitSystemUserTaskCancel(final SystemUserTaskCancelExpression expr) {
        final Object nameVal = expr.getTaskNameExpr().accept(this);
        if (queryExecutor == null) {
            return null;
        }
        return UserTaskCancellation.cancel(queryExecutor.getCatalog(),
            queryExecutor.getTaskScheduler(), nameVal == null ? null : nameVal.toString());
    }

    @Override
    public Object visitSessionVar(final SessionVarExpression expr) {
        final String name = expr.getVarName().toUpperCase();
        if (queryExecutor != null) {
            final SecurityManager sm = queryExecutor.getSecurityManager();
            if (sm != null) {
                // A reference the statement-level check could not see — a view body read at query time —
                // is refused here, where it is read, in the same words.
                if (!sm.getSessionContext().isSessionVariable(name)) {
                    final SourcePosition at = expr.getPosition() == null ? null
                        : ExpressionSource.resolve(expr.getPosition());
                    final String detail = "Session variable '$" + name + "' does not exist";
                    throw new RuntimeException(at == null ? SqlCompilationError.of(detail)
                        : SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail));
                }
                return sm.getSessionContext().getSessionVariable(name);
            }
            return queryExecutor.getSessionVariables().get(name);
        }
        return null;
    }

    /** The DECLARED type of a scripting variable a {@code :name} reference names, or null. */
    DataType declaredBindVariableType(final String varName) {
        if (queryExecutor == null) {
            return null;
        }
        final ProceduralExecutor procedural = queryExecutor.getProceduralExecutor();
        return procedural == null ? null : procedural.getDeclaredVariableType(varName);
    }

    /**
     * A scalar subquery's declared type: its one select item's static type, read from the subquery's
     * SHAPE — planned under {@link RelationShapeOnly}, so no row is read and no value computed, and
     * outside the result cache, so nothing is registered for RESULT_SCAN. A select list of more than
     * one item is refused as live refuses it, anchored on the subquery's own SELECT. A subquery that
     * cannot be planned here on its own — a correlated one, whose outer names are not bound yet —
     * stays undetermined, though its select list is still counted (see
     * {@link #rejectMultiColumnCorrelated}).
     */
    DataType scalarSubqueryType(final SubqueryExpression expr) {
        if (queryExecutor == null) {
            return null;
        }
        subqueryRanges.remove(expr);
        final boolean previous = RelationShapeOnly.begin();
        try {
            final List<ResultSet> results =
                queryExecutor.executeWithLateralContext(expr.getSubquery(), new HashMap<String, Object>());
            if (results.isEmpty() || results.get(0) == null) {
                return null;
            }
            final List<ResultSetColumn> columns = results.get(0).getColumns();
            if (columns.size() > 1) {
                throw multiColumnRefusal(expr);
            }
            if (columns.isEmpty()) {
                return null;
            }
            if (columns.get(0).getValueRange() != null) {
                subqueryRanges.put(expr, columns.get(0).getValueRange());
            }
            return columns.get(0).getStaticType();
        } catch (final MultiColumnScalarSubqueryException refused) {
            throw refused;
        } catch (final RuntimeException undetermined) {
            rejectMultiColumnCorrelated(expr);
            return null;
        } finally {
            RelationShapeOnly.end(previous);
        }
    }

    /**
     * A correlated subquery's select list, counted with its outer names bound to NULL: (SELECT fz.b, 1)
     * and (SELECT fz.id, 1 UNION ALL SELECT 2, 3) are refused as more than one column before any row is
     * read, as an uncorrelated one is. A plan that still faults leaves the subquery undetermined, so a
     * name it cannot resolve is reported by its execution first, as live reports it first.
     */
    private void rejectMultiColumnCorrelated(final SubqueryExpression expr) {
        final List<ResultSet> results;
        try {
            results = queryExecutor.executeWithLateralContext(expr.getSubquery(),
                subqueryEvaluator.outerNamesContext());
        } catch (final RuntimeException undetermined) {
            return;
        }
        if (!results.isEmpty() && results.get(0) != null && results.get(0).getColumns().size() > 1) {
            throw multiColumnRefusal(expr);
        }
    }

    /** Live's refusal of a scalar subquery selecting more than one column, anchored on its own SELECT. */
    MultiColumnScalarSubqueryException multiColumnRefusal(final SubqueryExpression expr) {
        final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
        final String detail = "Unsupported: Scalar subquery with multi-column SELECT clause.";
        return new MultiColumnScalarSubqueryException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail));
    }

    /**
     * The type of what a subquery projects, as an argument-type list names it: its one item's type, or
     * the ROW of several ({@code ROW(VARCHAR(3), VARCHAR(1))} for {@code SELECT 'abc', 'x'}), an item
     * without a type spelled NULL. Read from the subquery's planned SHAPE, as
     * {@link #scalarSubqueryType} reads it; null when it cannot be planned here on its own.
     */
    private String subqueryRowTypeText(final SubqueryExpression expr) {
        if (queryExecutor == null) {
            return null;
        }
        final boolean previous = RelationShapeOnly.begin();
        try {
            final List<ResultSet> results =
                queryExecutor.executeWithLateralContext(expr.getSubquery(), new HashMap<String, Object>());
            if (results.isEmpty() || results.get(0) == null || results.get(0).getColumns().isEmpty()) {
                return null;
            }
            final List<ResultSetColumn> columns = results.get(0).getColumns();
            final StringBuilder text = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                final DataType type = columns.get(i).getStaticType();
                text.append(i > 0 ? ", " : "").append(type == null ? "NULL" : SqlTypeNames.canonical(type));
            }
            return columns.size() == 1 ? text.toString() : "ROW(" + text + ")";
        } catch (final RuntimeException undetermined) {
            return null;
        } finally {
            RelationShapeOnly.end(previous);
        }
    }

    /** The interval a scalar subquery's item lies in, as its planned shape reported it, or null. */
    ValueRange subqueryValueRange(final SubqueryExpression expr) {
        if (!subqueryRanges.containsKey(expr)) {
            typeInferencer.infer(expr);
        }
        return subqueryRanges.get(expr);
    }

    /** The value a scripting variable holds right now, or null when no block binds the name. */
    Object boundVariableValue(final BindVariableExpression expr) {
        if (queryExecutor == null) {
            return null;
        }
        final ProceduralExecutor procedural = queryExecutor.getProceduralExecutor();
        return procedural != null && procedural.hasVariable(expr.getVarName())
            ? procedural.getVariable(expr.getVarName()) : null;
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
        // Live answers this in TWO different ways, and which one depends on whether a scripting block
        // is running. Both carry the position, and the position is the COLON's offset every time —
        // measured across INSERT VALUES, UPDATE SET, UPDATE/DELETE WHERE, a subquery and a bare
        // scripting expression.
        //
        //   inside a block   `BEGIN INSERT INTO t VALUES (:nope); …`  invalid identifier 'NOPE'
        //   outside one      `SELECT :nope`                          Bind variable :nope not set.
        //
        // Outside a block a :name is a CLIENT bind that was never supplied, which is what live's
        // second wording says; inside one it names a scripting variable, and an unknown name there is
        // simply an unresolvable identifier.
        //
        // The name is upper-cased in the first form. Live upper-cases it on the DML paths and echoes
        // it as written inside a SELECT — a split with no counterpart in this evaluator, which cannot
        // see which statement family holds the reference. Upper is the canonical spelling of an
        // unquoted identifier and matches the DML family this was filed from; the echoed-case cells
        // stay a recorded divergence rather than a guess at the family.
        final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
        if (!insideScriptingBlock()) {
            final String unset = "Bind variable :" + expr.getVarName() + " not set.";
            throw new RuntimeException(at != null
                ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), unset)
                : SqlCompilationError.of(unset));
        }
        final String name = expr.getVarName().toUpperCase(Locale.ROOT);
        // Typed as the block's own name failure, not the statement's: live resolves a block's names
        // when it COMPILES the block, so this refusal escapes without the uncaught-exception wrapper
        // that a statement error carries.
        throw at != null
            ? new UndeclaredScriptVariableException(name, at)
            : new UndeclaredScriptVariableException(name);
    }

    /** Whether a BEGIN…END block is running, which decides which of live's two wordings applies. */
    private boolean insideScriptingBlock() {
        if (queryExecutor == null) {
            return false;
        }
        final ProceduralExecutor proceduralExecutor = queryExecutor.getProceduralExecutor();
        return proceduralExecutor != null && proceduralExecutor.isExecutingBlock();
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
        int positionalOrdinal = expr.getPositionalOrdinal();
        if (columnName.startsWith("$")) {
            try {
                final int position = Integer.parseInt(columnName.substring(1));
                columnName = "COLUMN" + position;
                positionalOrdinal = position;
            } catch (final NumberFormatException e) {
                // Not a positional parameter, keep original name
            }
        }

        // $N is a POSITIONAL reference: live reads the Nth column of the FROM source, whatever the
        // columns are named. When the source genuinely carries a COLUMN<N> column (a VALUES or
        // stage-file relation) the name lookups below read it — the same column, by its real name.
        // Otherwise, in a single-relation scope whose row still matches the declared shape, the
        // ordinal indexes the row directly; anything less certain (a join's concatenated rows)
        // falls through unchanged.
        if (positionalOrdinal > 0 && table != null
                && (multiTableAllTables == null || multiTableAllTables.size() <= 1)
                && !table.hasColumn(columnName)
                && positionalOrdinal <= table.getColumns().size()
                && row.getValues().size() == table.getColumns().size()) {
            return row.getValue(positionalOrdinal - 1);
        }

        // HAVING/QUALIFY: a reference to a precomputed SELECT-list output (alias or group column)
        // resolves to its value rather than being re-evaluated.
        if (resultContext != null) {
            if (resultContext.containsKey(columnName)) {
                return resultContext.get(columnName);
            }
            final String canonical = canonicalPrintOf(expr);
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
        // Each reference's resolution is MEMOIZED per context: a plain column hit caches its fixed
        // flat offset (later rows read the value directly), a per-row decision (merged join key,
        // literal fallback) stays on the full resolver, and a miss caches so a correlated outer
        // reference stops paying a thrown exception per row.
        if (multiTableAllTables != null && queryExecutor != null) {
            final Object memoized = multiTableRefMemo.get(expr);
            if (memoized instanceof Integer) {
                final int flatOffset = ((Integer) memoized).intValue();
                if (flatOffset < row.getValues().size()) {
                    return row.getValue(flatOffset);
                }
                // A narrower row under the same context — distrust the cache for this row.
            }
            if (memoized != MULTI_TABLE_MISS) {
                final String refName = expr.isQualified() ? expr.getTableName() + "." + columnName : columnName;
                if (memoized != null) {
                    try {
                        return queryExecutor.resolveColumnInTables(row, multiTableAllTables, multiTableAliasToTable, refName);
                    } catch (final AmbiguousColumnException ambiguous) {
                        throw ambiguous;
                    } catch (final RuntimeException ignored) {
                        // not a joined column — fall through
                    }
                } else {
                    final int[] resolvedOffset = {-2};
                    try {
                        final Object value = queryExecutor.resolveColumnInTables(
                            row, multiTableAllTables, multiTableAliasToTable, refName, resolvedOffset);
                        multiTableRefMemo.put(expr,
                            resolvedOffset[0] >= 0 ? Integer.valueOf(resolvedOffset[0]) : MULTI_TABLE_DYNAMIC);
                        return value;
                    } catch (final AmbiguousColumnException ambiguous) {
                        throw ambiguous;
                    } catch (final RuntimeException ignored) {
                        multiTableRefMemo.put(expr, MULTI_TABLE_MISS);
                    }
                }
            }
        }

        // Try qualified name first if present
        if (expr.isQualified() && table != null) {
            // Try exact match with qualified name
            final String qualifiedName = expr.getTableName() + "." + columnName;
            if (table.hasColumn(qualifiedName)) {
                final int index = table.getColumnIndex(qualifiedName);
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
            // are outside the rule and resolve further down.
            //
            // A MULTI-PART qualifier is asked the same question of its LAST part, which is the
            // relation's own name: `test_schema.kw.a` reads over FROM kw, while
            // `test_schema.nosuchtable.a` is refused and so is `test_schema.kw.a` over FROM kw t —
            // the alias replaces the name however many parts precede it (all measured). This used to
            // skip multi-part qualifiers entirely, so any of those resolved by column name alone.
            if (fromClauseKeys != null && !fromClauseKeys.isEmpty()
                    && !"NEXTVAL".equalsIgnoreCase(columnName) && !"CURRVAL".equalsIgnoreCase(columnName)
                    && !qualifierIsAFromClauseKey(expr.getTableName())
                    && !(qualifierIsAFromClauseKey(lastQualifierPart(expr.getTableName()))
                        && qualifierNamesTheSameRelation(expr.getTableName()))) {
                final SourcePosition qualifierAt = ExpressionSource.resolve(expr.getPosition());
                final String spelledQualifier = expr.getWrittenName() != null ? expr.getWrittenName()
                    : expr.getTableName().toUpperCase() + "." + columnName.toUpperCase();
                throw qualifierAt != null
                    ? new InvalidQualifierException(spelledQualifier, qualifierAt)
                    : new InvalidQualifierException(spelledQualifier);
            }

            // Try stripping the table alias and match column name only
            if (table.hasColumn(columnName)) {
                final int index = table.getColumnIndex(columnName);
                return row.getValue(index);
            }

            // Try case-insensitive
            for (final TableColumn col : table.getColumns()) {
                if (col.getName().equals(columnName)) {
                    final int index = table.getColumnIndex(col.getName());
                    return row.getValue(index);
                }
            }
        }

        // Unqualified column name
        if (table != null && table.hasColumn(columnName)) {
            final int index = table.getColumnIndex(columnName);
            return row.getValue(index);
        }

        // Try case-insensitive
        if (table != null) {
            for (final TableColumn col : table.getColumns()) {
                if (col.getName().equals(columnName)) {
                    final int index = table.getColumnIndex(col.getName());
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
            final BuiltInFunction zeroArgFn =
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

        // Row time, not plan time: the statement forms that never reach the plan-time scope walk —
        // UPDATE's SET values, DELETE's WHERE, a qualified reference resolved per row — land here.
        // The reference carries its own fragment-relative position, so the refusal can still say
        // where it was written whenever an origin has been set for the fragment being evaluated.
        // A positional reference is spelled the way it was written — live says '$3', not the
        // COLUMN<N> name it resolves through.
        final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
        final String spelled = expr.getPositionalOrdinal() > 0
            ? "$" + expr.getPositionalOrdinal() : String.valueOf(expr);
        throw new RuntimeException(at != null
            ? SqlCompilationError.invalidIdentifier(at.getLine(), at.getCharPositionInLine(), spelled)
            : SqlCompilationError.invalidIdentifier(spelled));
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
        // HAVING/QUALIFY: an operation that matches a grouping key or a SELECT-list output resolves to that
        // value, as a function call does, rather than being recomputed from columns the grouped row lacks.
        if (resultContext != null) {
            final String canonical = canonicalPrintOf(expr);
            if (resultContext.containsKey(canonical)) {
                return resultContext.get(canonical);
            }
        }
        final Object left = expr.getLeft().accept(this);
        final Object right = expr.getRight().accept(this);

        try {
            return dispatchBinaryOperation(expr, left, right);
        } catch (final RawRangeOverflow overflow) {
            // The arithmetic layer sees only VALUES; the sentence's nullability comes from the
            // expression tree — the refused operand's own for a rescale, either operand's for a
            // quotient — and is resolved HERE, only once a refusal has actually fired.
            throw new RuntimeException(overflow.messageWith(overflowNullability(expr, overflow)));
        }
    }

    /** Whether the sentence of {@code overflow} spells nullable, from the operands' declarations. */
    private boolean overflowNullability(final BinaryOperationExpression expr, final RawRangeOverflow overflow) {
        switch (overflow.getKind()) {
            case RESCALE_LEFT:
                return operandMaybeNull(expr.getLeft());
            case RESCALE_RIGHT:
                return operandMaybeNull(expr.getRight());
            default:
                return operandMaybeNull(expr.getLeft()) || operandMaybeNull(expr.getRight());
        }
    }

    /** A literal and a column declared NOT NULL are not null; every other operand may be. */
    private boolean operandMaybeNull(final Expression operand) {
        if (operand instanceof LiteralExpression) {
            return false;
        }
        if (operand instanceof ColumnReferenceExpression) {
            final TableColumn declared = resolveDeclaredColumn((ColumnReferenceExpression) operand);
            return declared == null || declared.isNullable();
        }
        return true;
    }

    private Object dispatchBinaryOperation(final BinaryOperationExpression expr, final Object leftValue,
                                           final Object rightValue) {
        final boolean arithmetic = expr.getOperator() == BinaryOperator.ADD || expr.getOperator() == BinaryOperator.SUBTRACT
            || expr.getOperator() == BinaryOperator.MULTIPLY || expr.getOperator() == BinaryOperator.DIVIDE
            || expr.getOperator() == BinaryOperator.MODULO;
        final Object left = arithmetic ? textArithmeticOperand(expr.getLeft(), leftValue, expr.getRight(), rightValue) : leftValue;
        final Object right = arithmetic ? textArithmeticOperand(expr.getRight(), rightValue, expr.getLeft(), leftValue) : rightValue;
        switch (expr.getOperator()) {
            case ADD:
                return ExpressionArithmetic.add(arithmeticOperand(expr.getLeft(), left),
                    arithmeticOperand(expr.getRight(), right),
                    ExpressionSource.resolve(expr.getPosition()));
            case SUBTRACT:
                return ExpressionArithmetic.subtract(arithmeticOperand(expr.getLeft(), left),
                    arithmeticOperand(expr.getRight(), right),
                    ExpressionSource.resolve(expr.getPosition()));
            case MULTIPLY:
                return ExpressionArithmetic.multiplyAtScale(arithmeticOperand(expr.getLeft(), left),
                    arithmeticOperand(expr.getRight(), right), declaredExactScale(expr));
            case DIVIDE:
                return quotientAtDeclaredScale(expr,
                    divide(arithmeticOperand(expr.getLeft(), left),
                        arithmeticOperand(expr.getRight(), right)));
            case MODULO:
                return modulo(arithmeticOperand(expr.getLeft(), left),
                    arithmeticOperand(expr.getRight(), right));
            case EQUAL:
                // NULL = anything is UNKNOWN (three-valued logic), represented as a null Boolean.
                if (left == null || right == null) return null;
                final Integer collatedEqual = collatedOrder(expr, left, right);
                if (collatedEqual != null) {
                    return collatedEqual == 0;
                }
                final Integer uuidEqual = uuidOrder(expr, left, right);
                if (uuidEqual != null) {
                    return uuidEqual == 0;
                }
                return equals(comparisonOperand(expr.getLeft(), left, right),
                    comparisonOperand(expr.getRight(), right, left));
            case NOT_EQUAL:
                if (left == null || right == null) return null;
                final Integer collatedUnequal = collatedOrder(expr, left, right);
                if (collatedUnequal != null) {
                    return collatedUnequal != 0;
                }
                final Integer uuidUnequal = uuidOrder(expr, left, right);
                if (uuidUnequal != null) {
                    return uuidUnequal != 0;
                }
                return !equals(comparisonOperand(expr.getLeft(), left, right),
                    comparisonOperand(expr.getRight(), right, left));
            case LESS_THAN:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                final Integer collatedLess = collatedOrder(expr, left, right);
                if (collatedLess != null) {
                    return collatedLess < 0;
                }
                final Integer uuidLess = uuidOrder(expr, left, right);
                if (uuidLess != null) {
                    return uuidLess < 0;
                }
                return compare(comparisonOperand(expr.getLeft(), left, right),
                    comparisonOperand(expr.getRight(), right, left)) < 0;
            case LESS_THAN_OR_EQUAL:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                final Integer collatedAtMost = collatedOrder(expr, left, right);
                if (collatedAtMost != null) {
                    return collatedAtMost <= 0;
                }
                final Integer uuidAtMost = uuidOrder(expr, left, right);
                if (uuidAtMost != null) {
                    return uuidAtMost <= 0;
                }
                return compare(comparisonOperand(expr.getLeft(), left, right),
                    comparisonOperand(expr.getRight(), right, left)) <= 0;
            case GREATER_THAN:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                final Integer collatedGreater = collatedOrder(expr, left, right);
                if (collatedGreater != null) {
                    return collatedGreater > 0;
                }
                final Integer uuidGreater = uuidOrder(expr, left, right);
                if (uuidGreater != null) {
                    return uuidGreater > 0;
                }
                return compare(comparisonOperand(expr.getLeft(), left, right),
                    comparisonOperand(expr.getRight(), right, left)) > 0;
            case GREATER_THAN_OR_EQUAL:
                if (left == null || right == null) {
                    return null; // NULL comparison is UNKNOWN (three-valued logic)
                }
                final Integer collatedAtLeast = collatedOrder(expr, left, right);
                if (collatedAtLeast != null) {
                    return collatedAtLeast >= 0;
                }
                final Integer uuidAtLeast = uuidOrder(expr, left, right);
                if (uuidAtLeast != null) {
                    return uuidAtLeast >= 0;
                }
                return compare(comparisonOperand(expr.getLeft(), left, right),
                    comparisonOperand(expr.getRight(), right, left)) >= 0;
            case AND: {
                // Three-valued logic: FALSE dominates (even over UNKNOWN), then UNKNOWN, else TRUE.
                // The operators convert a STRING strictly — 'x' is a row-time refusal, not false.
                final Boolean l = strictBooleanOrNull(left);
                final Boolean r = strictBooleanOrNull(right);
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
                final Boolean l = strictBooleanOrNull(left);
                final Boolean r = strictBooleanOrNull(right);
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
                // Temporal operands render in Snowflake's default output forms (space + FF3), and an
                // APPROXIMATE operand renders as a float — a stored FLOAT arrives as a BigDecimal and
                // would otherwise concatenate as "3.0" where live gives "3".
                return SharedFunctionHelpers.textOf(concatOperand(expr.getLeft(), left))
                    + SharedFunctionHelpers.textOf(concatOperand(expr.getRight(), right));
            case LIKE:
            case NOT_LIKE:
            case ILIKE:
            case NOT_ILIKE:
                return evaluateCollatedLike(expr, left, right);
            default:
                throw new RuntimeException("Unsupported binary operator: " + expr.getOperator());
        }
    }

    @Override
    public Object visitUnaryOperation(final UnaryOperationExpression expr) {
        // The family rule is asked at evaluation too, as a function call's is: a FROM-less projection
        // is evaluated without the plan-time walk, and -IFF(TRUE, TRUE, FALSE) must still be the
        // positioned compile-time sentence there rather than a row-time complaint.
        rejectUnaryOperandFamilies(expr);
        switch (expr.getOperator()) {
            case NOT:
                final Object operand = expr.getOperand().accept(this);
                // NOT UNKNOWN is UNKNOWN (three-valued logic). A STRING converts strictly, like the
                // binary logical operators — 'x' is a row-time refusal rather than a truthiness.
                final Boolean strict = strictBooleanOrNull(operand);
                if (strict == null) return null;
                return !strict.booleanValue();
            case NEGATE:
                final Object negOperand = expr.getOperand().accept(this);
                // -NULL is NULL; a numeric VARCHAR is coerced, as Snowflake does in any arithmetic context
                // (a VARCHAR column such as SPLIT_TO_TABLE's VALUE is routinely negated). A VARIANT JSON
                // null has no numeric reading and negates to SQL NULL, like the other arithmetic.
                if (readsAsSqlNull(negOperand)) {
                    return null;
                }
                // A VARIANT operand FLOATS, the same rule the binary arithmetic follows: live answers
                // -7.0 for `-src:n` where the member is the integer 7. The check is on the operand's
                // DECLARED type and not on the class its value arrived in — a VARIANT-read number is
                // an ordinary Integer by then, which is the distinction this rule keeps being about.
                final Object negTyped = arithmeticOperand(expr.getOperand(), negOperand);
                final Number negNumber = signedOperandNumber(negTyped);
                if (negNumber != null) {
                    // Coerced text and a VARIANT negate to FLOAT (live: -'3' is -3.0, -v over a
                    // VARIANT 7 is -7 typed FLOAT); real numbers keep their type.
                    if (negTyped instanceof CharSequence || negTyped instanceof VariantValue) {
                        return Double.valueOf(-negNumber.doubleValue());
                    }
                    return negate(negNumber);
                }
                // A text that reads as no number is live's row-time sentence, echoed trimmed.
                throw negOperand instanceof CharSequence
                    ? new RuntimeException(NumericRangeRefusal.unreadableText(negOperand.toString().trim()))
                    : new RuntimeException("Cannot negate non-number: " + negOperand);
            case PLUS: {
                // Unary plus is NOT the identity: it reads its operand as a number exactly as the
                // minus does, so a text converts to a FLOAT (+'5' is 5, typed FLOAT), a VARIANT
                // floats, NULL is NULL, a text that reads as no number is the row-time sentence, and
                // the families that have no numeric reading were refused while the statement compiled.
                final Object plusOperand = expr.getOperand().accept(this);
                if (readsAsSqlNull(plusOperand)) {
                    return null;
                }
                final Object plusTyped = arithmeticOperand(expr.getOperand(), plusOperand);
                final Number plusNumber = signedOperandNumber(plusTyped);
                if (plusNumber != null) {
                    return plusTyped instanceof CharSequence || plusTyped instanceof VariantValue
                        ? Double.valueOf(plusNumber.doubleValue()) : plusTyped;
                }
                throw plusOperand instanceof CharSequence
                    ? new RuntimeException(NumericRangeRefusal.unreadableText(plusOperand.toString().trim()))
                    : new RuntimeException("Cannot apply unary plus to non-number: " + plusOperand);
            }
            case EXISTS:
                // EXISTS is handled specially with subquery
                // Do NOT evaluate the operand first, just extract the subquery
                if (expr.getOperand() instanceof SubqueryExpression) {
                    final SubqueryExpression subquery = (SubqueryExpression) expr.getOperand();
                    return evaluateExists(subquery);
                }
                throw new RuntimeException("EXISTS requires subquery");
            default:
                throw new RuntimeException("Unsupported unary operator: " + expr.getOperator());
        }
    }

    /**
     * A sign's operand read as a number: a number as itself, a numeric text parsed, and a VARIANT
     * through its member — a JSON number, a numeric JSON string, or a JSON boolean as 1 / 0 (live:
     * {@code -v} over a VARIANT holding true is -1). Null when it reads as no number.
     */
    private static Number signedOperandNumber(final Object typed) {
        if (!(typed instanceof VariantValue)) {
            return ExpressionArithmetic.asNumber(typed);
        }
        // An object, an array or a string spelling no number fails the variant cast, as '+' does.
        return VariantNumbers.numberOf((VariantValue) typed, VariantNumbers.REAL);
    }

    @Override
    public Object visitLambda(final LambdaExpression expr) {
        // A lambda is never evaluated on its own — TRANSFORM/FILTER/REDUCE apply its body per element.
        throw new RuntimeException("Lambda expression is only valid as an argument to a higher-order function");
    }

    /** Returned by {@link #evaluateConditionalFunction} for a function that is NOT short-circuiting.
     *  A private singleton, so it can never collide with a real (possibly null) function result. */
    private static final Object NOT_CONDITIONAL = new Object();

    /** The conditionals evaluated through the ordinary dispatch, whose branches fold like the rest. */
    private static final Set<String> NON_SHORT_CIRCUIT_CONDITIONALS =
        Set.of("NVL2", "GREATEST", "LEAST", "NULLIF");

    /**
     * A conditional's chosen branch converted to the type the branches FOLD to, which is the difference
     * between declaring a type and holding one. A VARIANT branch beside a DATE folds to DATE, so the
     * value has to become a date or fail trying:
     *
     * <pre>
     *   COALESCE(&lt;VARIANT 1&gt;, &lt;DATE&gt;)              Failed to cast variant value 1 to DATE
     *   COALESCE(&lt;VARIANT "2020-01-01"&gt;, &lt;DATE&gt;)   2020-01-01, the quotes gone
     * </pre>
     *
     * <p>Both are the CAST's own behaviour, reached through {@link ValueCaster} rather than restated
     * here — the same conversion {@code PARSE_JSON('1')::DATE} performs, applied where the fold says it
     * belongs. Every short-circuiting conditional shares this one exit, so IFF, COALESCE, NVL, IFNULL
     * and DECODE agree without a rule each.
     *
     * <p>★ SCOPED TO A VARIANT SOURCE ON PURPOSE. Converting EVERY branch to the folded type is the
     * broad change that was reverted twice before; a VARIANT is the case where the value genuinely
     * carries a different type from the one the column is declared as, and it is the case that was
     * measured. A fold that stays VARIANT converts nothing, so a container beside a VARIANT is
     * untouched.
     *
     * <p>★ IT IS A ROW-TIME REFUSAL, not a compile-time one — over an empty table there is no value to
     * convert and the query answers nothing, which is live's behaviour too.
     */
    private Object castFoldedBranchValue(final Object value, final Expression expr) {
        if (value instanceof VariantValue) {
            final DataType folded = inferStaticType(expr);
            if (folded == null || folded instanceof VariantType) {
                return value;
            }
            return ValueCaster.castValue(value, folded.getName());
        }
        return conditionalValue(expr, value);
    }

    /**
     * A conditional's value PRESENTED at its folded NUMERIC type: live plans every branch as a CAST
     * to the fold and hands back the chosen one converted. Live-verified:
     *
     * <pre>
     *   COALESCE(i, n)         1.00       an INT beside a NUMBER(10,2) is presented at scale 2
     *   COALESCE(n, 1/0)       1.000000   the unselected division's NUMBER(7,6) still widens the fold
     *   COALESCE(n, f)         1          a FLOAT fold hands back a DOUBLE
     *   COALESCE(t, n)         1.50000    a text branch is the number it spells, at the fold's NUMBER(18,5)
     *   COALESCE('abc', n)     Numeric value 'abc' is not recognized — a chosen text that spells none
     *   COALESCE(n, 'abc')     1.00000    an UNCHOSEN text is never read
     * </pre>
     *
     * <p>The BOOLEAN and the TEMPORAL folds present their value the same way (live-verified, every
     * conditional in the family):
     *
     * <pre>
     *   COALESCE(1, b)         TRUE       a number beside a BOOLEAN is a BOOLEAN, whichever is written first
     *   IFF(FALSE, b, 0)       FALSE      the chosen number is its zero test; 1.5, 2 and -1 are TRUE
     *   IFF(FALSE, b, 'yes')   TRUE       a chosen text is read strictly: 'x', '2', '' are
     *                                     "Boolean value 'x' is not recognized" at row time
     *   COALESCE('x', b)       x          a STRING written first keeps the lead, and the boolean prints 'true'
     *   COALESCE(d, ts)        2020-01-01 00:00:00.000   a DATE beside a TIMESTAMP is the midnight timestamp
     *   COALESCE(ts, tl)       the same instant in the session zone, typed TIMESTAMP_LTZ
     *   IFF(FALSE, d, '2021-01-01')   2021-01-01   a chosen text is read as the fold's type
     *   COALESCE(s, d)         Date 'abc' is not recognized — and 'Time', 'Timestamp' for those folds
     * </pre>
     *
     * <p>A text fold converts nothing: a number or a boolean beside a leading string is rendered as
     * text by the display, which is what live shows too.
     */
    private Object conditionalValue(final Expression conditional, final Object value) {
        if (value == null) {
            return value;
        }
        final DataType folded = inferStaticType(conditional);
        if (folded instanceof BooleanType || folded instanceof DateTimeType) {
            return FoldedValues.presented(value, folded);
        }
        if (value instanceof Double || value instanceof Float) {
            return value;
        }
        if (!(value instanceof Number) && !(value instanceof CharSequence)) {
            return value;
        }
        if (!(folded instanceof NumericType)) {
            return value;
        }
        return FoldedValues.presented(value, folded);
    }

    /**
     * The branches COALESCE and NVL never return, converted anyway. Live plans these two as
     * {@code COALESCE(CAST(a AS T), CAST(b AS T))} and applies every one of those casts, so a value it
     * does NOT hand back can still fail the row:
     *
     * <pre>
     *   COALESCE(&lt;OBJECT {"k":1}&gt;, &lt;VARIANT 1&gt;)   Failed to cast variant value 1 to OBJECT
     *   NVL(&lt;OBJECT {"k":1}&gt;, &lt;VARIANT 1&gt;)        the same
     *   COALESCE(&lt;OBJECT&gt;, &lt;OBJECT&gt;, &lt;VARIANT 1&gt;) the same, from the third branch
     * </pre>
     *
     * <p>★ IFF, CASE AND DECODE DO NOT DO THIS. The same pairing is answered by all three, so it is the
     * first-non-null family alone — which is the family that has to look at every argument anyway.
     *
     * <p>★ THE GUARD IDIOM SURVIVES BECAUSE A BRANCH NEEDING NO CONVERSION IS NEVER TOUCHED.
     * {@code COALESCE(n, 1/0)} and {@code NVL(n, 1/0)} both answer live: the fold is numeric, the second
     * branch is numeric, so there is no cast to apply and the division is never computed. That is why
     * this is keyed on the branch's STATIC type being VARIANT rather than on evaluating everything —
     * eager evaluation is the change that was reverted twice before.
     *
     * @param args the call's arguments
     * @param returnedAt the index of the branch actually handed back
     * @param call the call, whose folded type is the conversion target
     */
    private void convertUnreturnedBranches(final List<Expression> args, final int returnedAt,
                                           final FunctionCallExpression call) {
        final DataType folded = inferStaticType(call);
        if (folded == null || folded instanceof VariantType) {
            return;
        }
        for (int i = returnedAt + 1; i < args.size(); i++) {
            if (!(inferStaticType(args.get(i)) instanceof VariantType)) {
                continue;
            }
            final Object value = args.get(i).accept(this);
            if (value instanceof VariantValue) {
                ValueCaster.castValue(value, folded.getName());
            }
        }
    }

    /**
     * Evaluate a short-circuiting conditional function, or return {@link #NOT_CONDITIONAL} when
     * {@code funcName} is not one. These are the Snowflake functions defined in terms of CASE, so only
     * the branch actually selected may be evaluated: a guard like {@code IFF(c, udf(x), NULL)} must not
     * call the UDF when {@code c} is false, and {@code NVL(a, expensive(b))} must not compute the
     * fallback when {@code a} is non-NULL. Arity mismatches fall through to the normal (eager) path so
     * the function's own argument-count validation still reports the error.
     */
    private Object evaluateConditionalFunction(final String funcName, final List<Expression> args,
                                              final FunctionCallExpression call) {
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
                rejectNonBooleanArgumentInStrictFunction(funcName, args, call);
                return isTrue(args.get(0).accept(this)) ? args.get(1).accept(this) : args.get(2).accept(this);
            case "COALESCE":
                if (args.size() < 2) {
                    return NOT_CONDITIONAL;
                }
                for (int i = 0; i < args.size(); i++) {
                    final Object value = args.get(i).accept(this);
                    if (value != null) {
                        convertUnreturnedBranches(args, i, call);
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
                if (nvlValue == null) {
                    return args.get(1).accept(this);
                }
                convertUnreturnedBranches(args, 0, call);
                return nvlValue;
            case "NVL2":
                if (args.size() != 3) {
                    return NOT_CONDITIONAL;
                }
                return args.get(0).accept(this) != null ? args.get(1).accept(this) : args.get(2).accept(this);
            case "DECODE":
                return args.size() >= 3 ? decode(args) : NOT_CONDITIONAL;
            default:
                return NOT_CONDITIONAL;
        }
    }

    /**
     * DECODE evaluated the way live plans it: the subject, then each search value in turn until one
     * matches (NULL matching NULL), and ONLY the matched result or the default — live answers
     * {@code DECODE(i, 2, 1/0, n)} and {@code DECODE(i, 1, n, 1/0)} where an eager evaluation divided
     * by zero. A JSON null read out of a VARIANT compares as SQL NULL, as the eager path compared it.
     */
    private Object decode(final List<Expression> args) {
        final Object subject = comparedOperand(args.get(0));
        // DECODE short-circuits here rather than through the registry, so its own comparison is the
        // one that has to read the collation the subject and the search values settled on.
        final CollationSpec rules = decodeCollation(args);
        final int pairs = (args.size() - 1) / 2;
        for (int i = 0; i < pairs; i++) {
            final Object search = comparedOperand(args.get(1 + i * 2));
            final boolean matched = subject == null ? search == null
                : search != null && (rules != null ? CollationMatching.equalUnder(rules, subject, search)
                    : ExpressionArithmetic.equals(subject, search));
            if (matched) {
                return args.get(2 + i * 2).accept(this);
            }
        }
        return args.size() % 2 == 0 ? args.get(args.size() - 1).accept(this) : null;
    }

    /**
     * A DECODE operand as the comparison reads it: a JSON null as SQL NULL, and a number read out of a
     * VARIANT kept as the VARIANT it is — a VARIANT compares by its JSON text, so {@code src:score}
     * holding 7.5 is equal to '7.5' and not to '7.50' (the rule {@link #applyVariantComparisonOperands}
     * applies on the eager path).
     */
    private Object comparedOperand(final Expression arg) {
        final Object value = VariantJsonNulls.asScalar(arg.accept(this));
        if (value instanceof Number && typeInferencer.infer(arg) instanceof VariantType) {
            return VariantValue.of(String.valueOf(value));
        }
        return value;
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
     *
     * @param accumulator REDUCE's declared type, or null: a numeric accumulator is held at it from the start
     *                    and after every step, as live holds it
     */
    private Object evaluateHigherOrderFunction(final String funcName, final List<Expression> args,
                                               final DataType accumulator) {
        final LambdaExpression lambda = (LambdaExpression) args.get(args.size() - 1);
        rejectWrongLambdaArity(funcName, lambda, args);
        final ArrayNode array = ArrayFunctionHelper.parseArray(args.get(0).accept(this));
        if (array == null) {
            return null;
        }

        if ("REDUCE".equals(funcName)) {
            // A numeric accumulator is held at REDUCE's declared type throughout: live answers
            // REDUCE([1, 2], 0, (acc, x) -> acc || x) with 0.00000, the NUMBER(18,5) start 0.00000 extended
            // to the text '0.000001' and read back at that type, and so on for each element.
            final String held = accumulator instanceof NumericType ? SqlTypeNames.canonical(accumulator) : null;
            Object acc = args.size() >= 3 ? heldAt(args.get(1).accept(this), held) : null;
            for (int i = 0; i < array.size(); i++) {
                // An `undefined` element binds as SQL NULL, never as the sentinel — live:
                // REDUCE([1,NULL,2], 0, (acc,x) -> acc + COALESCE(x::int,10)) is 13.
                acc = heldAt(applyLambda(lambda, acc, VariantUndefined.elementValue(array.get(i))), held);
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
        // An ARRAY value like every other producer's, not a bare tree: the HTTP wire renders a VariantValue
        // as its Snowflake text, where a raw array crossed as a nested JSON array and an `undefined` element
        // as a bare token that made the whole body unreadable.
        return VariantValue.ofNode(result);
    }

    /** {@code value} cast to {@code type}, or unchanged when there is no type to hold it at or no value. */
    private Object heldAt(final Object value, final String type) {
        return type == null || value == null ? value : castValue(value, type);
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
    public Object visitFunctionCall(final FunctionCallExpression written) {
        // A star written beside other arguments is its column list, spliced in place before any rule,
        // echo or evaluation reads the call.
        final FunctionCallExpression expr = splicedStarArguments(written);
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
            final String canonical = canonicalPrintOf(written);
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
            return evaluateHigherOrderFunction(funcName, expr.getArguments(),
                "REDUCE".equals(funcName) ? typeInferencer.infer(expr) : null);
        }

        // Handle special functions
        if (expr.isStar()) {
            // OBJECT_CONSTRUCT(*) / OBJECT_CONSTRUCT_KEEP_NULL(*) [EXCLUDE cols]: expand the star to the
            // current row's columns as alternating key/value arguments, dropping any EXCLUDEd columns.
            if (("OBJECT_CONSTRUCT".equals(funcName) || "OBJECT_CONSTRUCT_KEEP_NULL".equals(funcName))
                    && table != null && row != null) {
                final BuiltInFunction objFunc = functionRegistry.getFunction(funcName);
                if (objFunc != null) {
                    final List<Object> kv = new ArrayList<>();
                    final List<String> excludes = expr.getStarExcludes();
                    final List<TableColumn> cols = table.getColumns();
                    for (int i = 0; i < cols.size(); i++) {
                        final String colName = cols.get(i).getName();
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
            final BuiltInFunction func = functionRegistry.getFunction(funcName);
            if (func != null) {
                return func.evaluate(new ArrayList<>());
            }
        }

        // Conditional functions SHORT-CIRCUIT, like CASE (whose IFF is Snowflake's shorthand): only the
        // selected branch is evaluated. Evaluating every argument first breaks the standard guard idiom
        // `IFF(x IS NOT NULL, f(x), NULL)` / `NVL(x, g(y))`, where the unselected branch would error or
        // is merely expensive — a UDF that rejects NULL then failed on rows the guard excluded.
        final Object shortCircuited = evaluateConditionalFunction(funcName, expr.getArguments(), expr);
        if (shortCircuited != NOT_CONDITIONAL) {
            return castFoldedBranchValue(shortCircuited, expr);
        }

        // COLLATE hands back its operand — the collation it names is read from the call wherever the
        // value is compared — and COLLATION reads that collation back without evaluating anything.
        // Both are judged by the static channel first, which is what refuses a non-string operand or
        // a specification live refuses when the call is evaluated rather than typed.
        if ("COLLATE".equals(funcName) && expr.getArguments().size() == 2) {
            typeInferencer.infer(expr);
            return expr.getArguments().get(0).accept(this);
        }
        if ("COLLATION".equals(funcName) && expr.getArguments().size() == 1) {
            typeInferencer.infer(expr.getArguments().get(0));
            return collationOf(expr.getArguments().get(0)).getSpec();
        }
        // EQUAL_NULL — and IS [NOT] DISTINCT FROM, which is built on it — compares two strings under
        // their collation as '=' does.
        if ("EQUAL_NULL".equals(funcName) && expr.getArguments().size() == 2) {
            final CollationSpec rules = comparisonCollation(expr.getArguments().get(0), expr.getArguments().get(1));
            if (rules != null) {
                final Object first = expr.getArguments().get(0).accept(this);
                final Object second = expr.getArguments().get(1).accept(this);
                if (first instanceof String && second instanceof String) {
                    return rules.compare((String) first, (String) second) == 0;
                }
                if (first == null || second == null) {
                    return first == null && second == null;
                }
                return equals(first, second);
            }
        }

        // SYSTEM$TYPEOF names its argument's DECLARED type and never evaluates it — the account
        // types SYSTEM$TYPEOF(MEDIAN(c)) over a column whose median would not fit, and
        // SYSTEM$TYPEOF(DIV0(a, 0.1)) over a value that would overflow, computing neither. The
        // storage tag comes from the interval the plan can see, not from any row's value; see
        // SystemTypeOfDescription for why the two differ.
        if ("SYSTEM$TYPEOF".equals(funcName) && expr.getArguments().size() == 1 && queryExecutor != null) {
            final Expression typed = expr.getArguments().get(0);
            final DataType declared = inferStaticType(typed);
            final DataType planned = planReportedType(typed, declared);
            // A binary's width is spelled by BinaryWidthSpelling: an unsized one is bare BINARY here.
            final String typeText = planned instanceof BinaryType ? ((BinaryType) planned).typeofText()
                : planned == declared ? strictArgTypeText(typed) : SqlTypeNames.canonical(planned);
            return SystemTypeOfDescription.of(planned, typeText, inferStaticRange(typed),
                RowIndexPredicates.isPredicate(typed));
        }

        rejectWrittenArity(funcName, expr);
        // A whole-day unit asked of a TIME is refused while the statement compiles. The plan-time walk
        // raises it; this raises it where no walk ran first — a FROM-less select list — before any
        // argument is read.
        rejectWholeDayUnitOverTime(funcName, expr.getArguments());

        // Evaluate arguments; a ** spread argument splices its ARRAY elements (or OBJECT pairs,
        // for the key/value-shaped constructors) into the positional argument list.
        final List<Object> argValues = new ArrayList<>();
        final int unitSlot = DateTimeUnitSlot.positionIn(funcName);
        final List<Expression> callArguments = expr.getArguments();
        for (int i = 0; i < callArguments.size(); i++) {
            final Expression arg = callArguments.get(i);
            if (i == unitSlot) {
                // A unit slot is read as a NAME, not evaluated — see DateTimeUnitSlot. This is what
                // lets DATEADD(dd, …) mean DAY over a table that also has an INT column dd.
                if (DateTimeUnitSlot.isNullLiteral(arg)) {
                    return null;
                }
                final String unit = DateTimeUnitSlot.unitTextOf(arg);
                if (unit == null && !funcName.equals("DATE_PART")) {
                    throw new RuntimeException(SqlCompilationError.inline(
                        DateTimeUnitSlot.notAUnitRefusal(strictText(arg), funcName)));
                }
                // DATE_PART is the exception: it does not complain about the SHAPE of its unit, it
                // takes whatever arrives and reports it in its own sentence — as the word "null".
                argValues.add(unit);
            } else if (arg instanceof SpreadExpression) {
                spliceSpreadValue(((SpreadExpression) arg).getInner().accept(this), argValues);
            } else {
                argValues.add(arg.accept(this));
            }
        }
        rejectVarcharColumnInTemporalFunction(funcName, expr.getArguments(), argValues);
        coerceApproximateArguments(expr.getArguments(), argValues);
        applyVariantComparisonOperands(funcName, expr.getArguments(), argValues);
        coerceVariantArgumentToText(funcName, expr.getArguments(), argValues);
        rejectNonVariantArgumentInStrictFunction(funcName, expr.getArguments(), expr);
        rejectNonBooleanArgumentInStrictFunction(funcName, expr.getArguments(), expr);
        rejectStrictArgumentFamilies(funcName, expr.getArguments(), expr);

        // IDENTIFIER(string) — resolves a string as a column name in the current row
        if ("IDENTIFIER".equals(funcName) && argValues.size() == 1 && argValues.get(0) != null) {
            final String colName = argValues.get(0).toString();
            if (table != null && row != null) {
                final int idx = table.getColumnIndex(colName);
                if (idx >= 0) return row.getValue(idx);
            }
            return null;
        }

        // Look up and execute built-in function
        final BuiltInFunction func = functionRegistry.getFunction(funcName);
        if (func != null) {
            // PROJECTION_CONSTRAINT is the one built-in that insists on a NAMED argument: live refuses
            // the positional call wherever it appears — in a query and, at query time, inside a
            // projection policy's body, which is why the check sits here rather than in the function.
            if ("PROJECTION_CONSTRAINT".equals(funcName)
                    && (expr.getArgumentNames() == null || expr.getArgumentNames().size() != 1
                        || !"ALLOW".equalsIgnoreCase(expr.getArgumentNames().get(0)))) {
                throw arityMismatch(" Invalid argument for function PROJECTION_CONSTRAINT."
                    + " Please specify allow=>true or allow=>false as the input.", expr);
            }
            // Snowflake rejects wrong arities at compile time (live-verified: "not enough arguments
            // for function [LOG(10)], expected 2, got 1"); enforce the declared bounds the same way.
            if (argValues.size() < func.getMinArgCount()) {
                throw arityMismatch("not enough arguments for function ["
                    + strictPlanCallText(expr) + "], expected " + func.getMinArgCount()
                    + ", got " + argValues.size(), expr);
            }
            if (func.getMaxArgCount() >= 0 && argValues.size() > func.getMaxArgCount()) {
                // No comma after the bracket in the too-many form — live-verified:
                // "too many arguments for function [COMPRESS('hello', 'snappy', 'x')] expected 2, got 3".
                throw arityMismatch(tooManyArguments(funcName, expr, func.getMaxArgCount(), argValues.size()),
                    expr);
            }
            if (func instanceof NumericArgumentFunction) {
                coerceNumericTextArguments(funcName, expr, argValues);
                coerceNumericVariantArguments(argValues);
            }
            try {
                // DECODE and NVL2 are conditionals that do NOT short-circuit, so their branch cast
                // belongs here rather than at the short-circuit exit — the rule is the conditional's,
                // not the dispatch's, which is why the family is named rather than applied to every
                // built-in.
                final Object evaluated = zeroIfNullConverted(funcName, expr,
                    atDeclaredScale(funcName, expr,
                        func.evaluate(approximateRoundingArgs(funcName, expr,
                            readsVariantJsonNull(func, funcName)
                                ? argValues : VariantJsonNulls.asScalarArgs(argValues)),
                            comparingCollation(funcName, expr))));
                return NON_SHORT_CIRCUIT_CONDITIONALS.contains(funcName)
                    ? castFoldedBranchValue(evaluated, expr) : evaluated;
            } catch (final IncomparableArgumentsException | ClassCastException
                    | NumberFormatException mismatch) {
                // A numeric function that fell over a TEXT reading as no number is live's row-time
                // sentence, whatever the member fell with — a cast to Number or a raw decimal parse.
                final String unreadable = func instanceof NumericArgumentFunction
                    ? unreadableTextArgument(argValues) : null;
                if (unreadable != null) {
                    throw new RuntimeException(unreadable);
                }
                if (mismatch instanceof NumberFormatException) {
                    throw mismatch;
                }
                throw unorderableArguments(expr, funcName);
            }
        }

        // Delegate SYSTEM$ functions to QueryExecutor
        if (funcName.startsWith("SYSTEM$") && queryExecutor != null) {
            // The family's measured arity, enforced here as well as in the plan-time walk because the
            // FROM-less path evaluates without ever walking — same two sentences, same position.
            final int systemMinimum = SystemFunctionArity.minimumOr(funcName);
            final int systemMaximum = SystemFunctionArity.maximumOr(funcName);
            if (systemMinimum >= 0 && argValues.size() < systemMinimum) {
                throw arityMismatch("not enough arguments for function ["
                    + strictText(expr) + "], expected " + systemMinimum
                    + ", got " + argValues.size(), expr);
            }
            if (systemMaximum >= 0 && argValues.size() > systemMaximum) {
                throw arityMismatch("too many arguments for function ["
                    + strictText(expr) + "] expected " + systemMaximum
                    + ", got " + argValues.size(), expr);
            }
            try {
                return queryExecutor.evaluateSystemFunction(funcName, argValues);
            } catch (final RuntimeException e) {
                // A recognized system function's own refusal (a bad OBJECT_TYPE, say) propagates;
                // only an UNSUPPORTED name falls through to UDF resolution.
                if (e.getMessage() == null || !e.getMessage().startsWith("Unsupported system function")) {
                    throw e;
                }
            } catch (final Exception ignored) {}
        }

        // Check for user-defined function
        if (catalog != null) {
            Function udf = null;
            try {
                final String dbName = catalog.getCurrentDatabase();
                if (dbName != null) {
                    // For qualified names (schema.func or db.schema.func), resolve the specific
                    // schema from the CANONICAL name parts the AST build read off the parse tree —
                    // never by re-splitting the flattened text, which breaks a quoted part
                    // containing a dot and loses quoted-name case. A dynamically named call
                    // (IDENTIFIER('s.fn')) has no parts; ITS name is a runtime string whose
                    // documented shape is dot-qualified, so splitting that VALUE is the semantics.
                    final List<String> parts;
                    if (expr.getNameParts() != null) {
                        parts = expr.getNameParts();
                    } else {
                        parts = new ArrayList<>();
                        for (final String part : funcName.split("\\.")) {
                            parts.add(part);
                        }
                    }
                    final Schema targetSchema;
                    final String simpleName;
                    if (parts.size() == 2) {
                        // schema.function
                        targetSchema = catalog.getDatabase(dbName).getSchema(parts.get(0));
                        simpleName = parts.get(1);
                    } else if (parts.size() == 3) {
                        // db.schema.function
                        targetSchema = catalog.getDatabase(parts.get(0)).getSchema(parts.get(1));
                        simpleName = parts.get(2);
                    } else {
                        // unqualified — search current schema
                        final String schemaName = catalog.getCurrentSchema();
                        targetSchema = schemaName != null
                            ? catalog.getDatabase(dbName).getSchema(schemaName) : null;
                        simpleName = parts.size() == 1 ? parts.get(0) : funcName;
                    }

                    if (targetSchema != null) {
                        udf = resolveOverloadedFunction(targetSchema, simpleName, argValues);
                    }

                    // If not found in resolved schema and unqualified, also try current schema
                    if (udf == null && parts.size() == 1) {
                        final String schemaName = catalog.getCurrentSchema();
                        if (schemaName != null) {
                            final Schema currentSchema = catalog.getDatabase(dbName).getSchema(schemaName);
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

        // The sentence takes the name AS THE AST HOLDS IT — canonical, so a quoted name keeps its
        // case — while the dispatch above works with the folded one.
        throw new RuntimeException(SqlCompilationError.of(
            unknownFunctionSentence(expr)));
    }

    /**
     * Live's wording for a call it cannot resolve: {@code Unknown function NAME.} for a bare name and
     * {@code Unknown user-defined function SCHEMA.NAME.} for a qualified one — upper-cased, ended with
     * a full stop, and carrying no position (live-verified).
     *
     * <p>The {@code SQL compilation error:} prefix is not decoration. It is what marks this a
     * COMPILE-time refusal, which is how the view-body resolution tells a body that will not compile
     * from one that merely failed while producing rows.
     */
    String unknownFunctionSentence(final FunctionCallExpression call) {
        return unknownFunctionSentence(Collections.singletonList(call));
    }

    /**
     * The same sentence for a whole statement's worth of unresolvable calls. Live names EVERY one of
     * them in a single sentence, pluralising the noun and separating the names with ", " —
     * {@code Unknown functions FNC, FNA, FNB.} — and it does NOT de-duplicate: two calls to the same
     * missing name are listed twice.
     *
     * <p>The two families are reported SEPARATELY and never mixed. When a statement carries both a bare
     * unresolvable name and a qualified one, live names only the BARE list; the qualified sentence
     * appears only when every unresolvable call is qualified (live-verified in both directions).
     */
    public String unknownFunctionSentence(final List<FunctionCallExpression> calls) {
        final List<String> bare = new ArrayList<>();
        final List<String> qualified = new ArrayList<>();
        for (final FunctionCallExpression call : calls) {
            if (isQualifiedCall(call)) {
                qualified.add(spelledFunctionName(call));
            } else {
                bare.add(spelledFunctionName(call));
            }
        }
        final List<String> named = bare.isEmpty() ? qualified : bare;
        final String kind = bare.isEmpty() ? "Unknown user-defined function" : "Unknown function";
        return kind + (named.size() > 1 ? "s " : " ") + String.join(", ", named) + ".";
    }

    /** Whether the call names a SCHEMA (or database) as well as a routine. */
    private boolean isQualifiedCall(final FunctionCallExpression call) {
        final List<String> parts = call.getNameParts();
        return parts != null
            ? parts.size() > 1
            : spelledFunctionName(call).indexOf('.') >= 0;
    }

    /**
     * SPELLED FROM THE AST'S OWN PARTS, never re-canonicalised. The name the AST holds is ALREADY
     * canonical — a quoted spelling has had its quotes removed and its case kept — so passing it
     * through the canonicaliser a SECOND time saw an unquoted {@code No Such} and folded it to
     * {@code NO SUCH}, printing "NO SUCH" for a call written "No Such". The parts also avoid splitting
     * on '.', which would cut a quoted name that contains one.
     */
    private String spelledFunctionName(final FunctionCallExpression call) {
        final List<String> parts = call.getNameParts();
        if (parts == null || parts.isEmpty()) {
            return SqlIdentifiers.spellCanonicalPath(SqlIdentifiers.canonicalText(call.getFunctionName()));
        }
        final StringBuilder spelled = new StringBuilder();
        for (final String part : parts) {
            if (spelled.length() > 0) {
                spelled.append('.');
            }
            spelled.append(SqlIdentifiers.spellCanonical(part));
        }
        return spelled.toString();
    }

    /**
     * Refuses a call whose NAME resolves to nothing at all, so the refusal lands at COMPILE time —
     * over a table with no rows, and while a view or CTAS body is being resolved — rather than only
     * once a row reaches the evaluator. Live never looks at the data to decide this.
     *
     * <p>Only the NAME is judged, never the arity and never an overload. The evaluator picks a UDF
     * overload from the argument VALUES, which the static channel does not have; judging anything
     * finer here would refuse calls the evaluator resolves perfectly well. Every path the evaluator
     * can resolve through is therefore treated as resolvable: a dynamically named call, the SYSTEM$
     * family, all four registry families, and any catalog function of that name in any schema the
     * unqualified lookup would reach.
     */
    void requireResolvableFunctionName(final FunctionCallExpression call) {
        if (!resolvesToAFunction(call)) {
            throw new RuntimeException(SqlCompilationError.of(unknownFunctionSentence(call)));
        }
    }

    /**
     * The predicate behind {@link #requireResolvableFunctionName}, as a question rather than a refusal —
     * the statement-wide scan asks it of every call so it can name them ALL in one sentence, which is
     * what live does.
     *
     * @param call the call to judge
     * @return whether some path could resolve this name
     */
    public boolean resolvesToAFunction(final FunctionCallExpression call) {
        if (call.getNameExpression() != null) {
            return true;                             // IDENTIFIER(expr): the target is a runtime string
        }
        final String funcName = call.getFunctionName();
        if (funcName == null) {
            return true;
        }
        final String name = funcName.toUpperCase(Locale.ROOT);
        // IDENTIFIER and the higher-order family (FILTER / REDUCE / TRANSFORM) are dispatched by the
        // evaluator BEFORE the registry is consulted — they take a lambda or a name rather than
        // ordinary argument values — so the registry does not hold them and never will.
        return name.startsWith("SYSTEM$") || "IDENTIFIER".equals(name)
            || HigherOrderFunctionNames.contains(name)
            // A WINDOW name written with no OVER is not unknown — the registry carries no callable
            // scalar for it, but the name is one this engine knows and the SPECIFICATION is what is
            // absent. Saying otherwise here pre-empted the two refusals that say so properly: the
            // missing-specification sentence, and QUALIFY's own no-window one.
            || WindowFunctionNames.handles(name)
            || functionRegistry == null
            || functionRegistry.hasFunction(name)
            || functionRegistry.hasAggregateFunction(name)
            || functionRegistry.hasTableFunction(name)
            || catalogFunctionNameExists(call, name);
    }

    /**
     * Whether the catalog holds ANY function whose simple name matches, in ANY schema this call could
     * conceivably reach — deliberately far weaker than the evaluator's own lookup, and deliberately
     * blind to the qualifier.
     *
     * <p>A compile-time check layered over a runtime one must never refuse what the runtime would
     * accept, so the only thing worth being sure of here is that the name exists NOWHERE. Resolving
     * the qualifier properly is the evaluator's job and it says more when it fails: an unqualified
     * call can reach a function outside the session's current schema (a view created as
     * {@code CREATE VIEW other.v AS SELECT f(x)} resolves f where the VIEW lives), and a qualified one
     * names a schema the static channel may not have in hand at the moment it is asked.
     */
    private boolean catalogFunctionNameExists(final FunctionCallExpression call, final String name) {
        if (catalog == null) {
            return false;
        }
        try {
            final List<String> parts = call.getNameParts() != null
                ? call.getNameParts() : Arrays.asList(name.split("\\."));
            final String simpleName = parts.get(parts.size() - 1);
            final List<String> databases = new ArrayList<>();
            if (catalog.getCurrentDatabase() != null) {
                databases.add(catalog.getCurrentDatabase());
            }
            if (parts.size() == 3) {
                databases.add(parts.get(0));
            }
            for (final String databaseName : databases) {
                for (final Schema candidate : catalog.getDatabase(databaseName).getAllSchemas()) {
                    if (hasOverload(candidate, simpleName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (final RuntimeException unresolvable) {
            // Nothing could be established, so nothing is refused.
            return true;
        }
    }

    private boolean hasOverload(final Schema schema, final String simpleName) {
        if (schema == null) {
            return false;
        }
        final List<Function> overloads = schema.getFunctionOverloads(simpleName);
        return overloads != null && !overloads.isEmpty();
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
        "DECODE",
        // HASH encodes a value's IDENTITY rather than reading it as a scalar, so it has to tell the
        // two nulls apart: live, HASH(PARSE_JSON('null')) and HASH(NULL) are different hashes, from a
        // literal, from an object member and from a stored VARIANT column alike. The encoder already
        // carries a separate tag for each — the value simply never arrived. HASH_AGG needs the same
        // and gets it on the aggregate path, through VariantJsonNulls.
        "HASH"));

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

    /** How deep the static channel is inside function bodies, so a function that reaches itself stops. */
    private static final ThreadLocal<int[]> UDF_TYPING_DEPTH = new ThreadLocal<int[]>();

    private static final int MAX_UDF_TYPING_DEPTH = 8;

    /**
     * The static type of a user-defined function call, or null when it cannot be told. A SQL function
     * whose body is an EXPRESSION is typed by that body over its parameters' declared types, the way
     * live types it: SYSTEM$TYPEOF over a RETURNS NUMBER(10,2) whose body is 1.5 reads NUMBER(2,1), and
     * over a VARCHAR(5) whose body is 'abc' VARCHAR(3) (live-verified). So every family rule that reads
     * an argument's type sees what the body produces — TO_VARCHAR over a function returning a VECTOR is
     * refused as it is over the vector itself. Any other function answers its declared RETURNS type.
     * Overloads are told apart by argument count alone; a count two of them accept stays untyped.
     *
     * @param call the call
     * @return its type, or null
     */
    DataType udfCallType(final FunctionCallExpression call) {
        if (catalog == null || call.getFunctionName() == null || call.getNameExpression() != null) {
            return null;
        }
        final Function udf = staticallyResolvedUdf(call);
        if (udf == null || udf.isTableFunction()) {
            return null;
        }
        final DataType declared = udf.getReturnType();
        final String body = udf.getBody() == null ? "" : udf.getBody().trim();
        if (udf.getUdfLanguage() != UdfLanguage.SQL || body.isEmpty() || queryExecutor == null
                || queryExecutor.isQueryStatement(body) || queryExecutor.isProceduralBlock(body)) {
            return declared;
        }
        int[] depth = UDF_TYPING_DEPTH.get();
        if (depth == null) {
            depth = new int[1];
            UDF_TYPING_DEPTH.set(depth);
        }
        if (depth[0] >= MAX_UDF_TYPING_DEPTH) {
            return declared;
        }
        depth[0]++;
        try {
            final List<TableColumn> parameters = new ArrayList<>();
            for (int i = 0; i < udf.getParameters().size(); i++) {
                final Parameter parameter = udf.getParameters().get(i);
                // The body is typed as if the call were INLINED: a parameter reads at its ARGUMENT's
                // own type wherever the argument reaches it without conversion — see UdfParameterTypes.
                final DataType supplied = i < call.getArguments().size()
                    ? typeInferencer.infer(call.getArguments().get(i)) : null;
                final TableColumn column = new TableColumn(parameter.getName(),
                    UdfParameterTypes.effective(parameter.getDataType(), supplied), true,
                    null, false, false, false);
                // A declared parameter type is authoritative, so the resolver trusts it.
                column.setStaticallyTyped(true);
                parameters.add(column);
            }
            final ExpressionEvaluator typer = new ExpressionEvaluator(
                new Table("$ROUTINE_PARAMETERS", parameters, true), functionRegistry, catalog, queryExecutor);
            final DataType bodyType = typer.inferStaticType(ExpressionEvaluator.parse(body));
            return bodyType != null ? bodyType : declared;
        } catch (final RuntimeException untypable) {
            return declared;
        } finally {
            depth[0]--;
        }
    }

    /** The function a call names, resolved as the evaluator resolves it, by argument count alone. */
    private Function staticallyResolvedUdf(final FunctionCallExpression call) {
        final String dbName = catalog.getCurrentDatabase();
        if (dbName == null) {
            return null;
        }
        final List<String> parts = call.getNameParts() != null
            ? call.getNameParts() : Arrays.asList(call.getFunctionName());
        final Schema schema;
        final String name;
        try {
            if (parts.size() == 3) {
                schema = catalog.getDatabase(parts.get(0)).getSchema(parts.get(1));
                name = parts.get(2);
            } else if (parts.size() == 2) {
                schema = catalog.getDatabase(dbName).getSchema(parts.get(0));
                name = parts.get(1);
            } else {
                final String schemaName = catalog.getCurrentSchema();
                schema = schemaName == null ? null : catalog.getDatabase(dbName).getSchema(schemaName);
                name = parts.get(0);
            }
        } catch (final RuntimeException unresolved) {
            return null;
        }
        if (schema == null) {
            return null;
        }
        Function found = null;
        for (final Function overload : schema.getFunctionOverloads(name)) {
            if (UdfInvoker.acceptsArgumentCount(overload, call.getArguments().size())) {
                if (found != null) {
                    return null;
                }
                found = overload;
            }
        }
        return found;
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
     * A user-defined function call whose arguments no overload of that arity can take, refused while the
     * query compiles, at the call and over an empty table too (live-verified; the pairs are
     * {@link UdfArgumentTypes}'): "Invalid argument types for function 'F': (ARRAY, NUMBER(1,0))", every
     * argument's type listed. With overloads it is refused only when EVERY one of that arity refuses;
     * which of several accepting overloads runs stays the runtime's choice. A TIME into a TIMESTAMP
     * parameter is "incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]", unanchored. An all-named call
     * that does not fit is "named arguments [P] do not match any signature for function F". Inside a
     * running block the check stands aside: a :bind reaches the statement already substituted as a
     * literal there, so its declared type is lost.
     */
    private void rejectUncoercibleUdfArguments(final String funcName, final FunctionCallExpression call) {
        if (queryExecutor != null && queryExecutor.getProceduralExecutor() != null
                && queryExecutor.getProceduralExecutor().isExecutingBlock()) {
            return;
        }
        final List<Expression> args = call.getArguments();
        final List<DataType> types = new ArrayList<>();
        for (final Expression arg : args) {
            if (arg instanceof SpreadExpression) {
                return;
            }
            if (arg instanceof LiteralExpression && ((LiteralExpression) arg).getValue() == null) {
                types.add(null);   // an untyped NULL binds to anything
                continue;
            }
            final DataType type = typeInferencer.infer(arg);
            if (type == null) {
                return;   // an argument this pass cannot type refuses nothing
            }
            types.add(type);
        }
        final List<Function> candidates = udfCandidatesForValidation(funcName, args.size());
        if (candidates.isEmpty()) {
            return;
        }
        final String name = QualifiedName.parse(funcName).last();
        if (call.getArgumentNames() != null) {
            rejectUnmatchedNamedUdfArguments(name, candidates, call.getArgumentNames(), types, call);
            return;
        }
        String incompatible = null;
        for (final Function candidate : candidates) {
            final List<Parameter> params = candidate.getParameters();
            boolean refused = false;
            for (int i = 0; i < types.size() && i < params.size(); i++) {
                final DataType param = params.get(i).getDataType();
                if (types.get(i) == null) {
                    continue;
                }
                if (UdfArgumentTypes.timeIntoTimestamp(param, types.get(i))) {
                    if (incompatible == null) {
                        incompatible = "incompatible types: [" + SqlTypeNames.canonical(types.get(i)) + "] and ["
                            + SqlTypeNames.canonical(param) + "]";
                    }
                    refused = true;
                } else if (UdfArgumentTypes.refuses(param, types.get(i))) {
                    refused = true;
                    incompatible = "";
                }
            }
            if (!refused) {
                return;
            }
        }
        if (incompatible != null && !incompatible.isEmpty() && candidates.size() == 1) {
            throw new RuntimeException(SqlCompilationError.of(incompatible));
        }
        throw arityMismatch("Invalid argument types for function '" + name + "': (" + strictArgTypeList(args) + ")",
            call);
    }

    /** An all-named call against a single candidate: refused when a named argument's type does not fit. */
    private void rejectUnmatchedNamedUdfArguments(final String name, final List<Function> candidates,
            final List<String> argumentNames, final List<DataType> types, final FunctionCallExpression call) {
        if (candidates.size() != 1) {
            return;
        }
        final List<Parameter> params = candidates.get(0).getParameters();
        final List<String> named = new ArrayList<>();
        boolean refused = false;
        for (final Parameter param : params) {
            for (int i = 0; i < argumentNames.size() && i < types.size(); i++) {
                if (argumentNames.get(i) == null || !argumentNames.get(i).equalsIgnoreCase(param.getName())) {
                    continue;
                }
                named.add(param.getName().toUpperCase(Locale.ROOT));
                refused |= types.get(i) != null && (UdfArgumentTypes.refuses(param.getDataType(), types.get(i))
                    || UdfArgumentTypes.timeIntoTimestamp(param.getDataType(), types.get(i)));
            }
        }
        if (refused) {
            throw arityMismatch("named arguments [" + String.join(", ", named)
                + "] do not match any signature for function " + name, call);
        }
    }

    /**
     * Every user-defined function {@code funcName} could mean with {@code argCount} arguments — each
     * overload whose parameter list takes that many, defaults filling the rest — or none when the name
     * cannot be resolved here.
     */
    private List<Function> udfCandidatesForValidation(final String funcName, final int argCount) {
        final List<Function> candidates = new ArrayList<>();
        if (catalog == null) {
            return candidates;
        }
        try {
            final String currentDatabase = catalog.getCurrentDatabase();
            if (currentDatabase == null) {
                return candidates;
            }
            final QualifiedName name = QualifiedName.parse(funcName);
            final Schema schema;
            if (name.size() == 2) {
                schema = catalog.getDatabase(currentDatabase).getSchema(name.part(0));
            } else if (name.size() == 3) {
                schema = catalog.getDatabase(name.part(0)).getSchema(name.part(1));
            } else {
                final String schemaName = catalog.getCurrentSchema();
                schema = schemaName != null ? catalog.getDatabase(currentDatabase).getSchema(schemaName) : null;
            }
            if (schema == null) {
                return candidates;
            }
            for (final Function candidate : schema.getFunctionOverloads(name.last())) {
                int required = 0;
                for (final Parameter param : candidate.getParameters()) {
                    if (!param.hasDefault()) {
                        required++;
                    }
                }
                if (argCount >= required && argCount <= candidate.getParameters().size()) {
                    candidates.add(candidate);
                }
            }
            return candidates;
        } catch (final RuntimeException unresolvable) {
            return new ArrayList<>();
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
        // The chosen branch is converted to the branches' folded type, the same exit the short-circuit
        // conditionals take — a CASE is one of them, written as a clause rather than a call.
        for (final WhenClause when : expr.getWhenClauses()) {
            final Object condition = when.getCondition().accept(this);
            if (isTrue(condition)) {
                return castFoldedBranchValue(when.getResult().accept(this), expr);
            }
        }

        if (expr.getElseExpression() != null) {
            return castFoldedBranchValue(expr.getElseExpression().accept(this), expr);
        }

        return null;
    }

    @Override
    public Object visitCast(final CastExpression expr) {
        rejectFileCastSource(expr);
        rejectGeoCastSource(expr);
        // Ahead of TRY_CAST's own source rule: TRY_CAST(1 AS VECTOR(FLOAT,3)) is the vector's
        // "Unsupported data type 'FIXED'.", not TRY_CAST's sentence about its argument types.
        rejectIllegalVectorCast(expr);
        rejectStructuredTextCastSource(expr);
        rejectNonStringTryCastSource(expr);
        rejectUncastableSource(expr);
        rejectIllegalStructuredCast(expr);
        final Object value = expr.getExpression().accept(this);
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
        // ★ A CAST TO A WIDTH-DECLARED STRING ENFORCES THE WIDTH (live): 'abcdef'::VARCHAR(2) is
        // "String 'abcdef' is too long and would be truncated" — refused, never silently truncated
        // or kept whole. TRY_CAST turns the refusal into NULL through the ordinary try path. The
        // width is read from the target's own written text, the same source ValueCaster reads. A text
        // is checked as it stands; any other source is checked on the text it converts to, below.
        if (value instanceof String
                && !(typeInferencer.infer(expr.getExpression()) instanceof UuidType)) {
            // A UUID is the exception: the account hands back the whole 36-character text even for
            // CAST(u AS VARCHAR(10)), so the declared width never refuses it.
            final int declaredWidth = declaredStringWidth(expr.getTargetType());
            if (declaredWidth > 0 && ((String) value).length() > declaredWidth) {
                throw new ColumnLengthException(declaredWidth, (String) value);
            }
        }
        if (value instanceof VectorValue
                && "VARIANT".equalsIgnoreCase(String.valueOf(expr.getTargetType()).trim())) {
            // ★ A VECTOR cast to VARIANT is TO_VARIANT too, and must embed the same way: through the
            // scalar caster it arrived as its DISPLAY TEXT, so the two spellings of one conversion
            // disagreed — v::VARIANT gave the six-decimal form where TO_VARIANT(v) gave the array.
            final BuiltInFunction toVariantVector = functionRegistry == null ? null
                : functionRegistry.getFunction("TO_VARIANT");
            if (toVariantVector != null) {
                return applyStructuredTarget(expr,
                    toVariantVector.evaluate(Collections.singletonList(value)));
            }
        }
        if (value != null && "VARIANT".equalsIgnoreCase(String.valueOf(expr.getTargetType()).trim())
                && typeInferencer.infer(expr.getExpression()) instanceof StringType) {
            // A VARCHAR cast to VARIANT becomes a variant STRING and is never parsed — ::VARIANT is
            // TO_VARIANT, which says so: TYPEOF('[1,2,3]'::VARIANT) is VARCHAR, and flattening it
            // yields nothing. The cast used to hand the text straight through, so it read back as an
            // ARRAY. Only a statically-VARCHAR source takes this path: a structured value reaches here
            // as its JSON TEXT too, and wrapping that would make a MAP into a string of itself.
            final BuiltInFunction toVariant = functionRegistry == null ? null
                : functionRegistry.getFunction("TO_VARIANT");
            if (toVariant != null) {
                return applyStructuredTarget(expr, toVariant.evaluate(Collections.singletonList(value)));
            }
        }
        if (value instanceof String && isPlainStringTarget(expr.getTargetType())
                && typeInferencer.infer(expr.getExpression()) instanceof StringType) {
            // A VARCHAR is its own text. The string cast UNQUOTES its source, because a VARIANT string
            // coerces to its content — but a VARCHAR whose own text happens to start with a quote must
            // keep it, so '"[1,2,3]"'::VARCHAR is nine characters, not seven.
            return applyStructuredTarget(expr, value);
        }
        if (value instanceof Boolean && "NUMBER".equals(castTargetKind(expr.getTargetType()))) {
            // A BOOLEAN cast to an EXACT number is the unscaled 1 or 0 typed NUMBER(2,0) whatever
            // width the cast spells — live prints 1, not 1.0, for TRUE::NUMBER(5,1) — so the declared
            // pair is not applied to it (see the inferencer's cast rule for the declared type).
            return applyStructuredTarget(expr, ValueCaster.castValue(value, "NUMBER(2,0)",
                isNullableArgument(expr.getExpression())));
        }
        // The source's nullability travels with the value: an out-of-range number prints it as part of
        // the type it could not fit, and this is the only frame that still holds the source expression.
        final Object converted = ValueCaster.castValue(value, expr.getTargetType(),
            isNullableArgument(expr.getExpression()));
        // A number, a boolean, a temporal, a BINARY and a VARIANT are held to the width as well, on the
        // text they convert to: 123::VARCHAR(2) is "String '123' is too long and would be truncated", and
        // so are TRUE::VARCHAR(2), a DATE cast to VARCHAR(5) and a BINARY column's hex (live-verified).
        if (converted instanceof String && !(value instanceof String)
                && !isUncheckedConstant(expr.getExpression(), value)) {
            final int declaredWidth = declaredStringWidth(expr.getTargetType());
            if (declaredWidth > 0 && ((String) converted).length() > declaredWidth) {
                throw new ColumnLengthException(declaredWidth, (String) converted);
            }
        }
        return applyStructuredTarget(expr, converted);
    }

    /**
     * Whether a non-text cast operand converts to a sized string unchecked. Per row none does. Two kinds of
     * constant do: a BINARY constant ({@code X'ABCD'::VARCHAR(2)} is {@code ABCD}) and a number or boolean
     * constant wrapped straight into a VARIANT ({@code TO_VARIANT(123)::VARCHAR(1)} and
     * {@code 123::VARIANT::VARCHAR(1)} are {@code 123}). A computed wrap such as {@code TO_VARIANT(SQRT(2))}
     * is checked, and a JSON-parsed number is no wrap at all. The constant may be folded first: an IFF over a
     * constant condition is the branch it takes, and {@code TRUE AND TRUE} or {@code TO_NUMBER('123')} is a
     * constant as live folds it. A cast that adds decimals the constant does not have
     * ({@code 123::NUMBER(10,2)}) no longer round-trips through the VARIANT, and is checked.
     */
    private boolean isUncheckedConstant(final Expression operand, final Object value) {
        if (value instanceof BinaryValue) {
            return isLiteralConstant(operand);
        }
        Expression inner = variantWrapArgument(takenBranch(operand));
        if (inner == null) {
            return false;
        }
        Expression wrapped = operand;
        while (inner != null) {
            wrapped = takenBranch(inner);
            inner = variantWrapArgument(wrapped);
        }
        final DataType type = typeInferencer.infer(wrapped);
        return (type instanceof NumericType || type instanceof BooleanType) && isLiteralConstant(wrapped)
            && !gainsScale(wrapped);
    }

    /** The branch an IFF over a constant BOOLEAN condition takes, through any number of them; else the operand. */
    private static Expression takenBranch(final Expression operand) {
        Expression current = operand;
        while (current instanceof FunctionCallExpression
                && "IFF".equalsIgnoreCase(((FunctionCallExpression) current).getFunctionName())
                && ((FunctionCallExpression) current).getArguments().size() == 3
                && ((FunctionCallExpression) current).getArguments().get(0) instanceof LiteralExpression
                && ((LiteralExpression) ((FunctionCallExpression) current).getArguments().get(0)).getValue()
                    instanceof Boolean) {
            final List<Expression> arguments = ((FunctionCallExpression) current).getArguments();
            current = Boolean.TRUE.equals(((LiteralExpression) arguments.get(0)).getValue())
                ? arguments.get(1) : arguments.get(2);
        }
        return current;
    }

    /** Whether a cast in the constant's chain gives it a scale the value underneath it does not carry. */
    private boolean gainsScale(final Expression constant) {
        Expression current = constant;
        while (current instanceof CastExpression || current instanceof UnaryOperationExpression) {
            if (current instanceof CastExpression) {
                final DataType target = typeInferencer.infer(current);
                final DataType source = typeInferencer.infer(((CastExpression) current).getExpression());
                if (target instanceof NumericType && source instanceof NumericType
                        && !NumericType.isApproximate(target) && !NumericType.isApproximate(source)
                        && ((NumericType) target).getScale() > ((NumericType) source).getScale()) {
                    return true;
                }
                current = ((CastExpression) current).getExpression();
            } else {
                current = ((UnaryOperationExpression) current).getOperand();
            }
        }
        return false;
    }

    /** The argument of a TO_VARIANT(x) or x::VARIANT operand, or null when the operand is neither. */
    private static Expression variantWrapArgument(final Expression operand) {
        if (operand instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) operand;
            return "TO_VARIANT".equalsIgnoreCase(call.getFunctionName()) && call.getArguments().size() == 1
                ? call.getArguments().get(0) : null;
        }
        if (operand instanceof CastExpression
                && "VARIANT".equalsIgnoreCase(String.valueOf(((CastExpression) operand).getTargetType()).trim())) {
            return ((CastExpression) operand).getExpression();
        }
        return null;
    }

    /**
     * Whether an operand is a constant written out in the statement: a literal under any number of casts,
     * signs, ANDs and ORs, and TO_BINARY, TO_VARIANT or TO_NUMBER calls over such constants. A function that computes a value
     * ({@code SQRT(2)}) is not one, and neither is anything that reads a column.
     */
    private static boolean isLiteralConstant(final Expression operand) {
        if (operand instanceof LiteralExpression) {
            return true;
        }
        if (operand instanceof UnaryOperationExpression) {
            return isLiteralConstant(((UnaryOperationExpression) operand).getOperand());
        }
        if (operand instanceof CastExpression) {
            return isLiteralConstant(((CastExpression) operand).getExpression());
        }
        if (operand instanceof BinaryOperationExpression) {
            // A conjunction or disjunction of constants folds to one: TO_VARIANT(TRUE AND TRUE)::VARCHAR(1)
            // is true. Arithmetic is left computed, as live leaves 1/3.
            final BinaryOperationExpression binary = (BinaryOperationExpression) operand;
            return (binary.getOperator() == BinaryOperator.AND || binary.getOperator() == BinaryOperator.OR)
                && isLiteralConstant(binary.getLeft()) && isLiteralConstant(binary.getRight());
        }
        if (!(operand instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) operand;
        if (!"TO_BINARY".equalsIgnoreCase(call.getFunctionName()) && !"TO_VARIANT".equalsIgnoreCase(call.getFunctionName())
                && !"TO_NUMBER".equalsIgnoreCase(call.getFunctionName())) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!isLiteralConstant(argument)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a cast's written target is the plain string family, whatever length it declares. */
    private static boolean isPlainStringTarget(final String targetType) {
        if (targetType == null) {
            return false;
        }
        String name = targetType.toUpperCase();
        final int paren = name.indexOf('(');
        if (paren > 0) {
            name = name.substring(0, paren).trim();
        }
        return "VARCHAR".equals(name) || "STRING".equals(name) || "TEXT".equals(name);
    }

    /**
     * Reshape a cast's result to a STRUCTURED target — the declared field types, and the
     * {@code RENAME FIELDS} / {@code ADD FIELDS} field mapping. A plain target passes straight through,
     * so ordinary casts keep their existing behaviour exactly.
     */
    /** The declared width of a width-parameterised STRING cast target, or -1 when there is none. */
    /**
     * A WHERE's or ON's predicate with live's one exemption from the width check applied. An equality between
     * a narrowing string cast of a text value and a constant that fits the width can only hold when the value
     * fits too, so live answers it without converting (false for a longer value), where every other use of
     * the cast refuses the value. The cast is dropped from such an equality, either way round, and from an IN
     * list every item of which fits; the walk goes through AND and OR, never under NOT (live-verified).
     *
     * @param predicate the parsed predicate
     * @return the predicate with those casts dropped, or the predicate itself when none applies
     */
    public Expression withNarrowingCastEqualitiesAnswered(final Expression predicate) {
        if (predicate instanceof BinaryOperationExpression) {
            final BinaryOperationExpression op = (BinaryOperationExpression) predicate;
            if (op.getOperator() == BinaryOperator.AND || op.getOperator() == BinaryOperator.OR) {
                final Expression left = withNarrowingCastEqualitiesAnswered(op.getLeft());
                final Expression right = withNarrowingCastEqualitiesAnswered(op.getRight());
                return left == op.getLeft() && right == op.getRight() ? op : rebuilt(op, left, right);
            }
            if (op.getOperator() == BinaryOperator.EQUAL && op.getEscape() == null) {
                if (fitsWidthOf(op.getRight(), op.getLeft())) {
                    return rebuilt(op, strippedOperand((CastExpression) op.getLeft()), op.getRight());
                }
                if (fitsWidthOf(op.getLeft(), op.getRight())) {
                    return rebuilt(op, op.getLeft(), strippedOperand((CastExpression) op.getRight()));
                }
            }
            return predicate;
        }
        if (predicate instanceof InExpression) {
            final InExpression in = (InExpression) predicate;
            if (in.isNot() || in.hasSubquery() || !droppableCast(in.getValue())) {
                return predicate;
            }
            for (final Expression item : in.getValues()) {
                if (!fitsWidthOf(item, in.getValue())) {
                    return predicate;
                }
            }
            return new InExpression(strippedOperand((CastExpression) in.getValue()), in.getValues(), false);
        }
        return predicate;
    }

    /** Whether {@code constant} is a string constant no longer than the width the narrowing {@code cast} declares. */
    private boolean fitsWidthOf(final Expression constant, final Expression cast) {
        if (!droppableCast(cast)) {
            return false;
        }
        final String text = stringConstant(constant);
        return text != null && text.length() <= declaredStringWidth(((CastExpression) cast).getTargetType());
    }

    /** A cast to a declared-width string, not a TRY_CAST, over a text value that is not itself a constant. */
    private boolean droppableCast(final Expression expression) {
        if (!(expression instanceof CastExpression)) {
            return false;
        }
        final CastExpression cast = (CastExpression) expression;
        return !cast.isTryMode() && declaredStringWidth(cast.getTargetType()) > 0
            && stringConstant(cast.getExpression()) == null
            && typeInferencer.infer(cast.getExpression()) instanceof StringType;
    }

    /** The cast's value with every droppable cast around it removed. */
    private Expression strippedOperand(final CastExpression cast) {
        Expression operand = cast.getExpression();
        while (droppableCast(operand)) {
            operand = ((CastExpression) operand).getExpression();
        }
        return operand;
    }

    /**
     * The text of a string literal, or of a string literal cast to a string it fits, and null for anything
     * else.
     */
    private static String stringConstant(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expression;
            return literal.getType() == LiteralType.STRING ? String.valueOf(literal.getValue()) : null;
        }
        if (expression instanceof CastExpression && !((CastExpression) expression).isTryMode()) {
            final String text = stringConstant(((CastExpression) expression).getExpression());
            final int width = declaredStringWidth(((CastExpression) expression).getTargetType());
            return text != null && (width < 0 || text.length() <= width) ? text : null;
        }
        return null;
    }

    private static BinaryOperationExpression rebuilt(final BinaryOperationExpression op, final Expression left,
                                                     final Expression right) {
        final BinaryOperationExpression copy = new BinaryOperationExpression(left, op.getOperator(), right);
        copy.setPosition(op.getPosition());
        return copy;
    }

    private static int declaredStringWidth(final String targetType) {
        final String upper = targetType.toUpperCase(Locale.ROOT).trim();
        final int open = upper.indexOf('(');
        if (open < 0 || !upper.endsWith(")")) {
            return -1;
        }
        final String base = upper.substring(0, open).trim();
        if (!"VARCHAR".equals(base) && !"CHAR".equals(base) && !"CHARACTER".equals(base)
                && !"STRING".equals(base) && !"TEXT".equals(base) && !"NVARCHAR".equals(base)
                && !"NCHAR".equals(base)) {
            return -1;
        }
        try {
            return Integer.parseInt(upper.substring(open + 1, upper.length() - 1).trim());
        } catch (final NumberFormatException notANumber) {
            return -1;
        }
    }

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
     * A cast to VECTOR, judged while the statement compiles — from the plan-time walk as well as per
     * row, so an empty table refuses too. A VECTOR value casts only to its OWN vector type:
     * {@code [1,2,3]::VECTOR(FLOAT,3)::VECTOR(FLOAT,3)} is the unchanged vector while both
     * {@code ::VECTOR(INT,3)} and {@code ::VECTOR(FLOAT,2)} are "Invalid argument types for function
     * 'CAST': (VECTOR(FLOAT, 3))" — naming only the SOURCE type, and 'TRY_CAST' for that spelling —
     * positioned at the cast itself: the {@code ::} it was written with, or its keyword. A source that is
     * no vector, ARRAY or VARIANT has no conversion to a vector at all and is "Unsupported data type
     * 'TEXT'.", unpositioned, the source named by its family ({@link #vectorlessSourceFamily}). An ARRAY
     * or a VARIANT converts row by row, where its elements are checked.
     */
    private void rejectIllegalVectorCast(final CastExpression expr) {
        if (!(expr.getDeclaredTarget() instanceof VectorType)) {
            return;
        }
        final DataType sourceType = typeInferencer.infer(expr.getExpression());
        if (sourceType instanceof VectorType) {
            if (!sourceType.equals(expr.getDeclaredTarget())) {
                final String detail = "Invalid argument types for function '"
                    + (expr.isTryMode() ? "TRY_CAST" : "CAST") + "': (" + sourceType.getName() + ")";
                final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
                throw new RuntimeException(at != null
                    ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
                    : SqlCompilationError.of(detail));
            }
            return;
        }
        final String family = vectorlessSourceFamily(sourceType);
        if (family != null) {
            throw new RuntimeException(SqlCompilationError.of("Unsupported data type '" + family + "'."));
        }
    }

    /**
     * The family a cast to VECTOR names a source it has no conversion from — TEXT, OBJECT, FIXED, REAL,
     * BOOLEAN, BINARY, DATE, TIME or the TIMESTAMP flavour — or null for a source it converts (an ARRAY,
     * a VARIANT), an undetermined one, and a family not measured.
     */
    private static String vectorlessSourceFamily(final DataType source) {
        if (source instanceof StringType) {
            return "TEXT";
        }
        if (source instanceof NumericType) {
            return NumericType.isApproximate(source) ? "REAL" : "FIXED";
        }
        if (source instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (source instanceof BinaryType) {
            return "BINARY";
        }
        if (source instanceof ObjectType && !StructuredTypes.isStructured(source)) {
            return "OBJECT";
        }
        if (!(source instanceof DateTimeType)) {
            return null;
        }
        final String name = source.getName().toUpperCase(Locale.ROOT);
        if (name.startsWith("TIMESTAMP_LTZ")) {
            return "TIMESTAMP_LTZ";
        }
        if (name.startsWith("TIMESTAMP_TZ")) {
            return "TIMESTAMP_TZ";
        }
        if (name.startsWith("TIMESTAMP")) {
            return "TIMESTAMP_NTZ";
        }
        return name.startsWith("TIME") ? "TIME" : "DATE";
    }

    @Override
    public Object visitIsNull(final IsNullExpression expr) {
        final Object value = expr.getOperand().accept(this);
        final boolean isNull = (value == null);
        return expr.isNot() ? !isNull : isNull;
    }

    @Override
    public Object visitTupleIn(final TupleInExpression expr) {
        rejectInvalidTupleInShape(expr);
        final List<Object> left = new ArrayList<>();
        for (final Expression e : expr.getValues()) {
            left.add(e.accept(this));
        }
        if (expr.hasSubquery()) {
            if (queryExecutor == null) {
                throw new RuntimeException("Cannot evaluate tuple IN subquery: QueryExecutor not available");
            }
            // The subquery form is TWO-VALUED (live-verified) — unlike scalar IN and unlike the
            // tuple-row list form, a NULL on either side simply never matches, so a miss is plain
            // FALSE for IN / TRUE for NOT IN, never UNKNOWN.
            boolean found = false;
            final List<ResultSet> results = executeSubquery(expr.getSubquery().getSubquery());
            if (!results.isEmpty()) {
                for (final Row subRow : results.get(0).getRows()) {
                    if (tupleMatches(left, subRow)) {
                        found = true;
                        break;
                    }
                }
            }
            return expr.isNot() ? !found : found;
        }
        // Tuple-row list: full row-value three-valued logic (live-verified). Per candidate row, a
        // definite pair mismatch makes the row FALSE regardless of NULLs elsewhere; an all-equal
        // row is TRUE; any other row (a NULL pair and no definite mismatch) is UNKNOWN. The IN is
        // TRUE on any TRUE row, else UNKNOWN if any row was UNKNOWN, else FALSE; NOT IN negates
        // with UNKNOWN preserved.
        boolean anyUnknown = false;
        for (final List<Expression> rowExprs : expr.getTupleRows()) {
            boolean rowUnknown = false;
            boolean rowFalse = false;
            for (int i = 0; i < left.size(); i++) {
                final Object rightValue = rowExprs.get(i).accept(this);
                if (left.get(i) == null || rightValue == null) {
                    rowUnknown = true;
                } else if (!equals(left.get(i), rightValue)) {
                    rowFalse = true;
                    break;
                }
            }
            if (rowFalse) {
                continue;
            }
            if (rowUnknown) {
                anyUnknown = true;
                continue;
            }
            return !expr.isNot();
        }
        if (anyUnknown) {
            return null;
        }
        return expr.isNot();
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

    /**
     * The functions whose single parameter is a VARCHAR, so a VARIANT argument is coerced on the way
     * in. PARSE_JSON then READS the coerced text as JSON, TO_VARCHAR simply IS it.
     */
    private static final Set<String> VARCHAR_PARAMETER_FUNCTIONS = new HashSet<>(Arrays.asList(
        "PARSE_JSON", "TRY_PARSE_JSON", "TO_VARCHAR", "TO_CHAR"));

    /**
     * A VARIANT argument to a VARCHAR parameter is coerced BEFORE the function sees it — and coercing
     * a variant STRING hands over its raw content, without the JSON quotes. That one rule explains a
     * pair that looks contradictory:
     *
     * <pre>
     * PARSE_JSON(PARSE_JSON('{"k":"[1,2]"}'):k)  -&gt; ARRAY    the argument is a VARIANT string,
     *                                                        coerced to the text [1,2]
     * PARSE_JSON('"[1,2,3]"')                    -&gt; VARCHAR  the argument is a VARCHAR whose own
     *                                                        text carries the quotes
     * </pre>
     *
     * <p>The two are indistinguishable once evaluated, because path extraction hands back the QUOTED
     * text as a plain string, so the argument's DECLARED type is what decides. Frostlake used to guess
     * from the content instead — any quoted string whose inside looked like JSON was unwrapped — which
     * made {@code PARSE_JSON('"[1,2,3]"')} an ARRAY where live answers a VARCHAR, and left TO_VARCHAR
     * reporting a variant string WITH its quotes (7 characters for {@code [3,4]}, where live says 5).
     *
     * <p>An undetermined type keeps the older, narrower guess: unwrap only when the content really is
     * an object or an array. Being wrong there is a wrong ANSWER either way, and this direction is the
     * one that was already relied upon.
     */
    private void coerceVariantArgumentToText(final String funcName, final List<Expression> args,
                                             final List<Object> argValues) {
        if (!VARCHAR_PARAMETER_FUNCTIONS.contains(funcName)) {
            return;
        }
        if (args.size() != 1 || argValues.size() != 1 || argValues.get(0) == null) {
            return;
        }
        final DataType declared = typeInferencer.infer(args.get(0));
        if (declared instanceof StringType) {
            return;   // a VARCHAR is read exactly as written, quotes and all
        }
        if (VariantJsonNulls.isJsonNull(argValues.get(0))) {
            // The JSON null coerces to SQL NULL on its way to VARCHAR, and PARSE_JSON(NULL) is NULL —
            // so PARSE_JSON(PARSE_JSON('null')) is SQL NULL, not the JSON null again.
            argValues.set(0, null);
            return;
        }
        final String content = SemiStructuredCasts.quotedJsonStringText(argValues.get(0));
        if (content == null) {
            return;   // not a variant string: its text is already the JSON to read
        }
        final String trimmed = content.trim();
        if (declared == null && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return;
        }
        argValues.set(0, content);
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
    /**
     * The user-defined function argument check alone, for a FROM-less select item: that path computes
     * the item's value without the strict walk, so the one plan-time refusal it must not miss is run here
     * on its own, before the value is (see {@link #rejectUncoercibleUdfArguments}).
     */
    public void validateUdfArguments(final Expression expr) {
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            final String funcName = call.getFunctionName();
            if (funcName != null && !call.isStar() && functionRegistry.getFunction(funcName) == null
                    && functionRegistry.getAggregateFunction(funcName) == null) {
                rejectUncoercibleUdfArguments(funcName, call);
            }
            for (final Expression arg : call.getArguments()) {
                validateUdfArguments(arg);
            }
            return;
        }
        if (expr instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) expr;
            for (final WhenClause when : conditional.getWhenClauses()) {
                validateUdfArguments(when.getCondition());
                validateUdfArguments(when.getResult());
            }
            if (conditional.getElseExpression() != null) {
                validateUdfArguments(conditional.getElseExpression());
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            validateUdfArguments(((BinaryOperationExpression) expr).getLeft());
            validateUdfArguments(((BinaryOperationExpression) expr).getRight());
            return;
        }
        if (expr instanceof InExpression) {
            validateUdfArguments(((InExpression) expr).getValue());
            if (((InExpression) expr).getValues() != null) {
                for (final Expression value : ((InExpression) expr).getValues()) {
                    validateUdfArguments(value);
                }
            }
            return;
        }
        if (expr instanceof CastExpression) {
            validateUdfArguments(((CastExpression) expr).getExpression());
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            validateUdfArguments(((UnaryOperationExpression) expr).getOperand());
            return;
        }
        if (expr instanceof IsNullExpression) {
            validateUdfArguments(((IsNullExpression) expr).getOperand());
            return;
        }
        if (expr instanceof BetweenExpression) {
            validateUdfArguments(((BetweenExpression) expr).getValue());
            validateUdfArguments(((BetweenExpression) expr).getLower());
            validateUdfArguments(((BetweenExpression) expr).getUpper());
        }
    }

    public void validateStrictArguments(final Expression expr) {
        if (expr instanceof ColumnReferenceExpression) {
            validateColumnReferenceScope((ColumnReferenceExpression) expr);
            return;
        }
        if (expr instanceof WindowFunctionExpression) {
            // ★ A WINDOWED AGGREGATE IS HELD TO THE SAME DECLARED ARITY AS THE PLAIN ONE — live's
            // sentence for SUM(a, b) OVER () is the plain SUM sentence, same echo, same position at
            // the call. Window-ONLY functions are not aggregates and keep their own argument rules,
            // so only the aggregate registry speaks here.
            final WindowFunctionExpression window = (WindowFunctionExpression) expr;
            if (window.getFunctionName() != null && window.getArguments() != null) {
                final AggregateFunction windowedAggregate =
                    functionRegistry.getAggregateFunction(window.getFunctionName());
                if (windowedAggregate != null
                        && functionRegistry.getFunction(window.getFunctionName()) == null) {
                    final FunctionCallExpression proxy = new FunctionCallExpression(
                        window.getFunctionName(), window.getArguments());
                    proxy.setPosition(window.getPosition());
                    // A windowed star — SUM(*) OVER (), COUNT(t.* EXCLUDE (a)) OVER () — expands
                    // exactly as the plain one, qualifier and filters included; only a LONE star does
                    // (a star beside other arguments is spliced into the list and counted there).
                    if (window.getStarCall() != null) {
                        rejectStarExpandedArity(windowedAggregate, window.getFunctionName(),
                            starExpandedArgumentEcho(window.getStarCall()),
                            StarArgument.of(window.getStarCall()).isBare(), proxy);
                    } else {
                        rejectAggregateArity(windowedAggregate, splicedStarArguments(proxy));
                    }
                }
            }
            return;
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = splicedStarArguments((FunctionCallExpression) expr);
            // POSITION's IN form is judged before its one argument is walked: that argument is the
            // membership test the form was written as, and the walk would judge it as a test.
            rejectRowPositionOperands(call);
            // The arguments are walked FIRST, inside-out as live compiles: a nested call's or a cast's
            // own refusal is reported ahead of this call's rules about it (live: SUM(a::DATE) over a
            // NUMBER is the cast's conversion sentence, not SUM's argument-family one).
            // Argument position gates the date/time-unit bareword exemption in the scope check:
            // DATEADD(HOUR, …) is legal where a top-level `WHERE year = 1` is not. A unit SLOT is
            // skipped outright rather than exempted by vocabulary: the word there is a NAME whatever
            // it spells, so DATEADD(zz, …) is a bad date/time component and not an unknown column.
            final int walkedSlot = call.getNameExpression() != null ? -1
                : DateTimeUnitSlot.positionIn(call.getFunctionName().toUpperCase());
            final boolean outerInsideArgs = strictWalkInsideFunctionArgs;
            strictWalkInsideFunctionArgs = true;
            try {
                final List<Expression> walked = call.getArguments();
                for (int i = 0; i < walked.size(); i++) {
                    if (i != walkedSlot) {
                        validateStrictArguments(walked.get(i));
                    }
                }
            } finally {
                strictWalkInsideFunctionArgs = outerInsideArgs;
            }
            boolean hasSpread = false;
            for (final Expression arg : call.getArguments()) {
                if (arg instanceof SpreadExpression) {
                    hasSpread = true;
                }
            }
            if (!hasSpread && call.getNameExpression() == null) {
                final String funcName = call.getFunctionName().toUpperCase();
                rejectCollatedTrimCharacters(funcName, call);
                // Arity fires at plan time too — a wrong count refuses even over zero rows,
                // positioned on the call (live-verified). Star calls (COUNT(*)) count no arguments.
                final BuiltInFunction arityChecked = functionRegistry.getFunction(funcName);
                if (arityChecked != null && !call.isStar()) {
                    final int argCount = call.getArguments().size();
                    // A function resolved among overloads reports its argument types instead of a
                    // count, as the evaluation path does: TIME() is "Invalid argument types for
                    // function 'TIME': ()".
                    if (argCount < arityChecked.getMinArgCount() && arityChecked.refusesMissingArgumentsByType()) {
                        throw arityMismatch("Invalid argument types for function '" + funcName + "': ("
                            + strictArgTypeList(call.getArguments()) + ")", call);
                    }
                    if (argCount < arityChecked.getMinArgCount()) {
                        throw arityMismatch("not enough arguments for function ["
                            + strictPlanCallText(call) + "], expected "
                            + arityChecked.getMinArgCount() + ", got " + argCount, call);
                    }
                    if (arityChecked.getMaxArgCount() >= 0 && argCount > arityChecked.getMaxArgCount()) {
                        throw arityMismatch(tooManyArguments(funcName, call, arityChecked.getMaxArgCount(),
                            argCount), call);
                    }
                }
                // The SYSTEM$ family lives outside the registry and is judged from its own
                // measured table — same two sentences, same position, at plan time like the rest.
                if (arityChecked == null && !call.isStar() && funcName.startsWith("SYSTEM$")) {
                    final int argCount = call.getArguments().size();
                    final int minimum = SystemFunctionArity.minimumOr(funcName);
                    final int maximum = SystemFunctionArity.maximumOr(funcName);
                    if (minimum >= 0 && argCount < minimum) {
                        throw arityMismatch("not enough arguments for function ["
                            + strictText(call) + "], expected " + minimum + ", got " + argCount, call);
                    }
                    if (maximum >= 0 && argCount > maximum) {
                        throw arityMismatch("too many arguments for function ["
                            + strictText(call) + "] expected " + maximum + ", got " + argCount, call);
                    }
                }
                // An aggregate is held to its DECLARED arity the same way — ARRAY_AGG(a, b) is
                // "too many arguments … expected 1, got 2" and OBJECT_AGG(a) "not enough …
                // expected 2, got 1" (live-verified) — while the truly variadic ones, COUNT and
                // HASH_AGG, declare no upper bound and answer at any width.
                if (arityChecked == null && !call.isStar()) {
                    final AggregateFunction aggregateChecked =
                        functionRegistry.getAggregateFunction(funcName);
                    if (aggregateChecked != null) {
                        rejectAggregateArity(aggregateChecked, call);
                    }
                }
                // The argument FAMILIES only after the count: with too many or too few arguments and one of
                // a refused family, live names the arity — ABS(ARRAY_CONSTRUCT(), 1) is "too many
                // arguments … expected 1, got 2", LOG(TRUE) "not enough arguments" (live-verified).
                rejectVarcharColumnInTemporalFunction(funcName, call.getArguments(),
                    new ArrayList<Object>(call.getArguments()));
                rejectNonVariantArgumentInStrictFunction(funcName, call.getArguments(), call);
                rejectNonBooleanArgumentInStrictFunction(funcName, call.getArguments(), call);
                rejectStrictArgumentFamilies(funcName, call.getArguments(), call);
                rejectWholeDayUnitOverTime(funcName, call.getArguments());
                // ★ A STAR EXPANDS FIRST AND ONE ARITY RULE JUDGES THE EXPANDED LIST (live):
                // ARRAY_AGG(*) over three columns is "too many arguments for function
                // [ARRAY_AGG(SA.A, SA.B, SA.C)] expected 1, got 3" — the very sentence the
                // written-out list earns — while COUNT(*) and HASH_AGG(*) stay legal because their
                // aggregates take any width. The echo is the expanded QUALIFIED list, upper-cased.
                if (arityChecked == null && call.isStar()) {
                    final AggregateFunction starAggregate =
                        functionRegistry.getAggregateFunction(funcName);
                    if (starAggregate != null) {
                        rejectStarExpandedArity(starAggregate, funcName,
                            starExpandedArgumentEcho(call), StarArgument.of(call).isBare(), call);
                    }
                }
                if (functionRegistry.getFunction(funcName) == null) {
                    rejectStructuredUdfArgument(resolveUdfForValidation(funcName, call.getArguments().size()),
                        funcName, call.getArguments(), call.getArgumentNames());
                    rejectUncoercibleUdfArguments(funcName, call);
                }
            }
            return;
        }
        if (expr instanceof CaseExpression) {
            // A searched CASE's WHEN is a boolean position, judged in written order and walked
            // through, so a CASE nested in a branch is held to the same rule as the outer one.
            final CaseExpression conditional = (CaseExpression) expr;
            for (final WhenClause when : conditional.getWhenClauses()) {
                rejectNonBooleanCaseCondition(when.getCondition());
                validateStrictArguments(when.getCondition());
                validateStrictArguments(when.getResult());
            }
            if (conditional.getElseExpression() != null) {
                validateStrictArguments(conditional.getElseExpression());
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            rejectFileConcatOperand((BinaryOperationExpression) expr);
            rejectSemiStructuredConcatOperand((BinaryOperationExpression) expr);
            // Ahead of the semi-structured rule, which speaks for FILE and the geo pair as well but
            // has no position to give: a family this one already names is refused where live points.
            rejectOperandFamilies((BinaryOperationExpression) expr);
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
        if (expr instanceof TupleInExpression) {
            final TupleInExpression tupleIn = (TupleInExpression) expr;
            // A flat list or a wrong-width row is a compile-time refusal even over zero rows.
            rejectInvalidTupleInShape(tupleIn);
            for (final Expression value : tupleIn.getValues()) {
                validateStrictArguments(value);
            }
            if (tupleIn.hasTupleRows()) {
                for (final List<Expression> row : tupleIn.getTupleRows()) {
                    for (final Expression value : row) {
                        validateStrictArguments(value);
                    }
                }
            }
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            rejectUnaryOperandFamilies((UnaryOperationExpression) expr);
            validateStrictArguments(((UnaryOperationExpression) expr).getOperand());
            return;
        }
        if (expr instanceof CastExpression) {
            rejectFileCastSource((CastExpression) expr);
            rejectGeoCastSource((CastExpression) expr);
            rejectIllegalVectorCast((CastExpression) expr);
            rejectStructuredTextCastSource((CastExpression) expr);
            rejectNonStringTryCastSource((CastExpression) expr);
            rejectUncastableSource((CastExpression) expr);
            rejectIllegalStructuredCast((CastExpression) expr);
            validateStrictArguments(((CastExpression) expr).getExpression());
            return;
        }
        if (expr instanceof IsNullExpression) {
            // The operand of IS [NOT] NULL compiles like any other: a refusal inside it fires over an
            // empty table too — WHERE VECTOR_TRUNC(v, 4) IS NULL is refused before a row is read.
            validateStrictArguments(((IsNullExpression) expr).getOperand());
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
     * Functions whose listed argument must be BOOLEAN at COMPILE time. Live refuses EVERY other static
     * family in that position — VARCHAR, NUMBER, FLOAT, DATE, TIME, TIMESTAMP, BINARY, ARRAY, OBJECT,
     * and VARIANT too, a column or a PARSE_JSON / colon-path / TO_VARIANT expression alike:
     * {@code IFF(v, 1, 2)} over a VARIANT column is "Invalid argument types for function 'IFF':
     * (VARIANT, NUMBER(1,0), NUMBER(1,0))". Only a BOOLEAN and an untyped NULL compile; a BOOLEAN-typed
     * call ({@code TO_BOOLEAN(g)}, {@code TRY_TO_BOOLEAN(g)}) and a {@code ::BOOLEAN} cast pass and
     * convert at row time. The refusal lists every argument's static type, {@code NULL} for an untyped
     * NULL, and wins over a branch mismatch: {@code IFF(g, 1, 'x')} names the IFF, not the branches.
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
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIMESTAMP", "TIMESTAMP_NTZ(0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIMESTAMP_NTZ", "TIMESTAMP_NTZ(0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIMESTAMP_LTZ", "TIMESTAMP_LTZ(0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_TIMESTAMP_TZ", "TIMESTAMP_TZ(0)");
        TRY_TO_TARGET_TYPES.put("TRY_TO_UUID", "UUID");
    }

    /** The conversions a VARIANT source reaches with one argument only (see rejectWrittenArity). */
    private static final Set<String> VARIANT_TAKES_ONE_ARGUMENT = Set.of(
        "TO_TIMESTAMP", "TO_TIMESTAMP_NTZ", "TO_TIMESTAMP_LTZ", "TO_TIMESTAMP_TZ");

    /** The base conversion each TRY_TO_&lt;TYPE&gt; names in the conversion sentence. */
    private static final Map<String, String> TRY_TO_CONVERSION_PARAMETERS = new HashMap<>();
    static {
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_NUMBER", "TO_NUMBER");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_DECIMAL", "TO_DECIMAL");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_NUMERIC", "TO_NUMERIC");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_DOUBLE", "TO_DOUBLE");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_BOOLEAN", "TO_BOOLEAN");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_BINARY", "TO_BINARY");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_DATE", "TO_DATE");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_TIME", "TO_TIME");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_TIMESTAMP", "TO_TIMESTAMP_NTZ");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_TIMESTAMP_NTZ", "TO_TIMESTAMP_NTZ");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_TIMESTAMP_LTZ", "TO_TIMESTAMP_LTZ");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_TIMESTAMP_TZ", "TO_TIMESTAMP_TZ");
        TRY_TO_CONVERSION_PARAMETERS.put("TRY_TO_UUID", "TO_UUID");
    }

    /** The TO_&lt;TYPE&gt; conversions and the family each converts into, in the matrix's vocabulary. */
    private static final Map<String, String> CONVERSION_TARGET_KINDS = new HashMap<>();
    /** The parameter each TO_&lt;TYPE&gt; names in its conversion sentence: its own base name. */
    private static final Map<String, String> CONVERSION_PARAMETERS = new HashMap<>();
    static {
        CONVERSION_TARGET_KINDS.put("TO_NUMBER", "NUMBER");
        CONVERSION_TARGET_KINDS.put("TO_DECIMAL", "NUMBER");
        CONVERSION_TARGET_KINDS.put("TO_NUMERIC", "NUMBER");
        CONVERSION_TARGET_KINDS.put("TO_DOUBLE", "FLOAT");
        // Its own kind, not FLOAT's: TO_DECFLOAT takes a BOOLEAN where TO_DOUBLE refuses one.
        CONVERSION_TARGET_KINDS.put("TO_DECFLOAT", "DECFLOAT");
        CONVERSION_TARGET_KINDS.put("TO_BOOLEAN", "BOOLEAN");
        CONVERSION_TARGET_KINDS.put("TO_BINARY", "BINARY");
        CONVERSION_TARGET_KINDS.put("TO_DATE", "DATE");
        CONVERSION_TARGET_KINDS.put("TO_TIME", "TIME");
        CONVERSION_TARGET_KINDS.put("TO_TIMESTAMP", "TIMESTAMP");
        CONVERSION_TARGET_KINDS.put("TO_TIMESTAMP_NTZ", "TIMESTAMP");
        CONVERSION_TARGET_KINDS.put("TO_TIMESTAMP_LTZ", "TIMESTAMP");
        CONVERSION_TARGET_KINDS.put("TO_TIMESTAMP_TZ", "TIMESTAMP");
        for (final String conversion : CONVERSION_TARGET_KINDS.keySet()) {
            CONVERSION_PARAMETERS.put(conversion, conversion);
        }
        CONVERSION_PARAMETERS.put("TO_TIMESTAMP", "TO_TIMESTAMP_NTZ");
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

    private void rejectNonVariantArgumentInStrictFunction(final String funcName,
                                                          final List<Expression> args,
                                                          final Expression call) {
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
                // Anchored on the CALL, as live anchors it: without the compile-time marker and its
                // position the refusal reads as data-dependent, so a view over it is created empty.
                throw new RuntimeException(positionedArgumentTypes(call,
                    "Invalid argument types for function '" + funcName
                        + "': (" + strictArgTypeList(args) + ")"));
            }
        }
    }

    /**
     * Boolean-position strictness: a statically typed condition that is not BOOLEAN is refused at
     * COMPILE time — see {@link #BOOLEAN_STRICT_ARGS} for the families (live: "Invalid argument types
     * for function 'IFF': (VARCHAR(4), VARCHAR(1), VARCHAR(1))"). An operand this evaluator cannot
     * type stays accepted and converts at row time.
     */
    private void rejectNonBooleanArgumentInStrictFunction(final String funcName, final List<Expression> args,
                                                          final FunctionCallExpression call) {
        final int[] positions = BOOLEAN_STRICT_ARGS.get(funcName);
        if (positions == null) {
            return;
        }
        for (final int position : positions) {
            if (position >= args.size()) {
                continue;
            }
            final DataType inferred = typeInferencer.infer(args.get(position));
            if (inferred != null && !(inferred instanceof BooleanType)) {
                // The argument-type family is a COMPILE-time refusal POSITIONED at the call, not a bare
                // sentence: live points at the function name's own offset, and it moves with the call
                // rather than staying at the statement's start.
                throw new RuntimeException(argumentTypeRefusal("Invalid argument types for function '"
                    + funcName + "': (" + strictArgTypeList(args) + ")", call));
            }
        }
    }

    /**
     * A searched CASE's WHEN condition is a boolean position like IFF's first argument, but its refusal
     * is the CONVERSION sentence rather than the argument-type one a call earns — unpositioned, the
     * operand spelled from the plan, the first non-BOOLEAN condition in written order:
     *
     * <pre>
     *   CASE WHEN g THEN 1 END              Can not convert parameter 'RT.G' of type [VARCHAR(10)] into expected type [BOOLEAN]
     *   CASE WHEN 'x' THEN 1 END            … parameter ''x'' of type [VARCHAR(1)] …
     *   CASE WHEN n + 1 THEN 1 END          … parameter 'RT.N + 1' of type [NUMBER(6,0)] …
     *   CASE WHEN v THEN 1 END              … parameter 'RT.V' of type [VARIANT] …
     *   CASE WHEN NULL THEN 1 ELSE 2 END    2 — an untyped NULL compiles, as does TO_BOOLEAN(g)
     * </pre>
     *
     * <p>The families are {@link #BOOLEAN_STRICT_ARGS}'s: NUMBER, FLOAT, DATE, TIME, TIMESTAMP, BINARY,
     * ARRAY, OBJECT and a VARIANT expression (PARSE_JSON, a colon path, TO_VARIANT) all refuse, not just
     * a VARIANT column. The type is the STATIC one — over an empty table, in a view body, in WHERE or
     * HAVING or QUALIFY the refusal is the same — so a CASE nested in a branch is walked and held to
     * the rule too. An operand this evaluator cannot type stays accepted and converts at row time.
     */
    private void rejectNonBooleanCaseCondition(final Expression condition) {
        final DataType inferred = typeInferencer.infer(condition);
        if (inferred == null || inferred instanceof BooleanType) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("Can not convert parameter '"
            + typeInferencer.parameterName(condition) + "' of type ["
            + SqlTypeNames.canonical(inferred) + "] into expected type [BOOLEAN]"));
    }

    /**
     * The BOOLEAN positions inside a predicate — a searched CASE's WHEN conditions and the
     * {@link #BOOLEAN_STRICT_ARGS} calls — judged at plan time, so that {@code WHERE CASE WHEN g THEN
     * TRUE END} over a VARCHAR refuses over an empty table exactly as it does in a SELECT list.
     * Deliberately only those two rules: the predicate walk stays narrow (see
     * {@code ExpressionEvaluator.validatePredicate}), and the full strict walk runs them on its own.
     */
    public void validateBooleanPositions(final Expression expr) {
        if (expr == null) {
            return;
        }
        if (expr instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) expr;
            for (final WhenClause when : conditional.getWhenClauses()) {
                rejectNonBooleanCaseCondition(when.getCondition());
                validateBooleanPositions(when.getCondition());
                validateBooleanPositions(when.getResult());
            }
            validateBooleanPositions(conditional.getElseExpression());
            return;
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            boolean hasSpread = false;
            for (final Expression arg : call.getArguments()) {
                if (arg instanceof SpreadExpression) {
                    hasSpread = true;   // a ** spread moves the positions, so the call is left alone
                }
            }
            if (!hasSpread && call.getNameExpression() == null && call.getFunctionName() != null) {
                rejectNonBooleanArgumentInStrictFunction(call.getFunctionName().toUpperCase(),
                    call.getArguments(), call);
            }
            for (final Expression argument : call.getArguments()) {
                validateBooleanPositions(argument);
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            validateBooleanPositions(((BinaryOperationExpression) expr).getLeft());
            validateBooleanPositions(((BinaryOperationExpression) expr).getRight());
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            validateBooleanPositions(((UnaryOperationExpression) expr).getOperand());
            return;
        }
        if (expr instanceof CastExpression) {
            validateBooleanPositions(((CastExpression) expr).getExpression());
            return;
        }
        if (expr instanceof IsNullExpression) {
            validateBooleanPositions(((IsNullExpression) expr).getOperand());
            return;
        }
        if (expr instanceof BetweenExpression) {
            final BetweenExpression between = (BetweenExpression) expr;
            validateBooleanPositions(between.getValue());
            validateBooleanPositions(between.getLower());
            validateBooleanPositions(between.getUpper());
            return;
        }
        if (expr instanceof InExpression) {
            validateBooleanPositions(((InExpression) expr).getValue());
            if (((InExpression) expr).getValues() != null) {
                for (final Expression value : ((InExpression) expr).getValues()) {
                    validateBooleanPositions(value);
                }
            }
            return;
        }
        if (expr instanceof LikeAnyAllExpression) {
            final LikeAnyAllExpression like = (LikeAnyAllExpression) expr;
            validateBooleanPositions(like.getSubject());
            for (final Expression pattern : like.getPatterns()) {
                validateBooleanPositions(pattern);
            }
            return;
        }
        if (expr instanceof ObjectAccessExpression) {
            validateBooleanPositions(((ObjectAccessExpression) expr).getBase());
        }
    }

    /**
     * An argument-type complaint carrying the prefix and the CALL's own position, which is how live
     * reports the family — {@code SELECT IFF(g, 1, 2)} points at position 7 and
     * {@code SELECT 1, IFF(g, 1, 2)} at position 10, the function name's offset in each. A call whose
     * position cannot be resolved keeps the unpositioned prefix rather than inventing one.
     */
    private String argumentTypeRefusal(final String detail, final FunctionCallExpression call) {
        final SourcePosition at = call == null ? null : ExpressionSource.resolve(call.getPosition());
        return at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail);
    }

    /**
     * Predicate-position strictness: Snowflake rejects a WHERE, HAVING or QUALIFY condition whose
     * static type is VARCHAR or NUMBER (live: "Invalid data type [VARCHAR(10)] for predicate
     * [STRICT_T.S10]", "[NUMBER(38,0)] for predicate [T.N]", and "[VARCHAR(4)] for predicate ['true']"
     * for a literal). BOOLEAN, VARIANT and undetermined types pass. The three clauses share the
     * sentence, and the predicate is spelled from the plan: {@code MAX(RT.G)}, {@code COUNT(*)},
     * {@code RT.N + 1}, a window call in its canonical form, a SELECT alias bare ({@code GG}).
     */
    public void validatePredicateType(final Expression predicate) {
        final DataType inferred = typeInferencer.infer(predicate);
        if (inferred instanceof StringType || inferred instanceof NumericType) {
            // Compile-time live, and prefixed like every refusal live raises while compiling.
            throw new RuntimeException(SqlCompilationError.of("Invalid data type ["
                + strictArgTypeText(predicate) + "] for predicate ["
                + predicateDisplayText(predicate) + "]"));
        }
    }

    /**
     * How Snowflake names the predicate in the error — from the PLAN, as every strictness message
     * is spelled: a column qualified by its relation ({@code RT.G}), a SELECT alias bare, a string
     * literal as its own text, a call or an operator re-printed canonically.
     */
    private String predicateDisplayText(final Expression predicate) {
        return strictText(predicate);
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
        rejectNonIntegerTimestampScale(funcName, args);
        rejectNonStringTryToSource(funcName, args);
        rejectNonStringConversionSource(funcName, args);
        rejectNonStringFormatArgument(funcName, args);
        rejectFormatOverNonTextSource(funcName, args, call);
        rejectNonVectorArgument(funcName, args, call);
        rejectFileArgument(funcName, args);
        rejectGeoArgument(funcName, args);
        rejectSemiStructuredOrderingAggregate(funcName, args);
        // The unit rule is asked BEFORE the family rule: a temporal TRUNC's second argument is a
        // unit, and a unit that is not a string literal is the date-part sentence whatever its
        // family — TRUNC(d, d) is "Date/time component [TO_CHAR(TT.D) ]…", not "(DATE, DATE)".
        rejectNonLiteralTruncUnit(funcName, args);
        rejectSemiStructuredArgument(funcName, args, call);
        rejectNullIfZeroFamilies(funcName, args, call);
        rejectNonTextObjectKey(funcName, args, call);
        rejectNonIdentifierLiteral(funcName, args);
        rejectBinaryBesideText(funcName, args, call);
        rejectIllegalBase64Arguments(funcName, args, call);
        rejectIllegalRoundMode(funcName, args, call);
    }

    /**
     * NULLIFZERO is {@code NULLIF(x, 0)} on the account, and its refusals are the conditional's own —
     * live-verified over every family:
     *
     * <pre>
     *   NULLIFZERO(d)      Can not convert parameter '0' of type [NUMBER(1,0)] into expected type [DATE]
     *   NULLIFZERO(t)      … into expected type [TIME(9)]        NULLIFZERO(ts)   … [TIMESTAMP_NTZ(9)]
     *   NULLIFZERO(bn)     … [BINARY(8388608)]                    NULLIFZERO(b5)   … [BINARY(5)]
     *   NULLIFZERO(o)      … [OBJECT]                             NULLIFZERO(a)    … [ARRAY]
     *   NULLIFZERO(g)      error line 1 at position 7  Invalid argument types for function 'NULLIF':
     *                      (GEOGRAPHY, NUMBER(1,0))  — the conditional's name, the NULLIFZERO call's place
     *   NULLIFZERO(TRUE)   TRUE   NULLIFZERO(FALSE)  NULL   NULLIFZERO(v)  the value   NULLIFZERO('5')  5
     * </pre>
     *
     * <p>So the call is judged AS that NULLIF: the argument beside an implicit integer zero, through
     * the conditional's argument-family channels and its branch fold, with the call's own position.
     * A BOOLEAN passes because a NUMBER meets a BOOLEAN, exactly as {@code NULLIF(b, 0)} does.
     */
    private void rejectNullIfZeroFamilies(final String funcName, final List<Expression> args,
                                          final Expression call) {
        if (!"NULLIFZERO".equals(funcName) || args.size() != 1) {
            return;
        }
        final List<Expression> proxied = new ArrayList<>(2);
        proxied.add(args.get(0));
        proxied.add(new LiteralExpression(0L, LiteralType.INTEGER));
        final FunctionCallExpression proxy = new FunctionCallExpression("NULLIF", proxied);
        if (call instanceof FunctionCallExpression) {
            proxy.setPosition(((FunctionCallExpression) call).getPosition());
        }
        rejectSemiStructuredArgument("NULLIF", proxied, proxy);
        typeInferencer.infer(proxy);
    }

    /**
     * ROUND's optional third argument, the rounding MODE, decided while the statement COMPILES —
     * measured over an empty table, where the refusal still fires.
     *
     * <pre>
     *   ROUND(n, 0, 'nosuch')    invalid argument for function [ROUND] unexpected argument [nosuch]
     *                            at position 21,          — prefix at the CALL, and note the comma
     *   ROUND(&lt;FLOAT&gt;, 0, 'x')   too many arguments for function [ROUND] expected 2, got 3
     *   ROUND(n, 1, 1)           argument 1 to function 22 needs to be constant, found '3'
     *   ROUND(n, 0, 'HALF_TO_EVEN')  answers — and 'half_to_even' too, the word is case-insensitive
     *   ROUND(n, 0, NULL)        NULL, on both engines
     *   ROUND(n, 0, UPPER('x'))  … unexpected argument [X] at position -1,  — a FOLDED value was
     *                            written nowhere, so its detail points nowhere
     *   ROUND(n, 0, ' HALF_TO_EVEN')  refused at 21 — nothing is trimmed
     * </pre>
     *
     * <p>★ A CONSTANT IS WHAT LIVE FOLDS, not whatever cannot change. {@code 'HALF_' || 'TO_EVEN'},
     * {@code UPPER('half_to_even')}, CONCAT, CONCAT_WS and a session variable all answer; TRIM, a CAST,
     * IFF and a number inside the join are "not constant" — {@link ConstantTextArgument} holds the
     * family. The folded value is judged exactly like a literal's, and only a literal or a variable
     * keeps its own place in the detail.
     *
     * <p>★ THE THIRD ARGUMENT ONLY EXISTS FOR AN EXACT NUMERIC. Over a FLOAT — or a VARIANT, which
     * takes the FLOAT overload — live does not complain about the MODE at all: it says the call has too
     * many arguments, because that overload takes two. So the arity depends on the first argument's
     * family, which is why this is checked before the mode is looked at.
     *
     * <p>★ A NULL ARGUMENT FOLDS THE CALL FIRST. {@code ROUND(NULL, 0, 'x')}, {@code ROUND(n, NULL,
     * 'x')} and {@code ROUND(f, 0, NULL)} are all NULL — no mode judged, no FLOAT arity — as long as the
     * NULL is a constant ({@link ConstantTextArgument#isNullConstant}); a NULL-valued column is not.
     * Only the argument FAMILIES come earlier: {@code ROUND(TRUE, 0, NULL)} is still refused for its
     * BOOLEAN. A fourth argument is the ordinary arity refusal and never reaches this rule.
     *
     * <p>★ THE "at position 21" IS A CHARACTER OFFSET, not an argument ordinal — moving the statement
     * five characters right moves both numbers by five. That is what separates this template from
     * BASE64_ENCODE's identical-looking one, where the number IS the ordinal, and it is why the
     * literal has to carry its own place (see ExpressionAstBuilder.visitLiteralExpr).
     *
     * <p>★ THE OTHER TWO SHAPES ARE A DELIBERATE WORDING DIVERGENCE. Live refuses a non-constant or
     * non-string mode with "argument 1 to function 22 needs to be constant" — an internal function id
     * and an internal argument index, neither reproducible (the id was 21 in one measurement and 22 in
     * another, for the same call). Frostlake refuses the same shapes with the template its base64 and
     * percentile families already share, naming an honest ordinal and the real function. The
     * accept/refuse boundary matches; the sentence does not, on purpose.
     *
     * @param funcName the call's name
     * @param args its arguments
     * @param call the call itself, for the position the prefix carries
     */
    private void rejectIllegalRoundMode(final String funcName, final List<Expression> args,
                                        final Expression call) {
        if (!"ROUND".equals(funcName) || args.size() != 3
                || !(call instanceof FunctionCallExpression)) {
            return;
        }
        for (final Expression arg : args) {
            if (ConstantTextArgument.isNullConstant(arg, this)) {
                return;
            }
        }
        final DataType rounded = typeInferencer.infer(args.get(0));
        if (rounded instanceof VariantType
                || rounded instanceof NumericType && NumericType.isApproximate(rounded)) {
            throw arityMismatch("too many arguments for function [ROUND] expected 2, got 3",
                (FunctionCallExpression) call);
        }
        final Expression mode = args.get(2);
        if (!ConstantTextArgument.isConstant(mode, this)) {
            throw new RuntimeException(SqlCompilationError.of("argument 3 to function ROUND"
                + " needs to be constant, found '" + strictText(mode) + "'"));
        }
        final Object folded = mode.accept(this);
        if (folded == null || RoundingModeNames.isRoundingMode(folded.toString())) {
            return;
        }
        throw arityMismatch("invalid argument for function [ROUND] unexpected argument ["
            + folded + "] at position " + writtenAt(mode) + ",", (FunctionCallExpression) call);
    }

    /**
     * A registered function's declared arity, judged on the arguments AS WRITTEN — before any value is
     * computed or any argument family is looked at. Live names the count first: ABS(ARRAY_CONSTRUCT(),
     * 1) is "too many arguments … expected 1, got 2" and LOG(TRUE) "not enough arguments … expected 2,
     * got 1", never the ARRAY or the BOOLEAN (live-verified). A ** spread's width is known only once it
     * is evaluated, so a call carrying one is left to the check that follows evaluation.
     *
     * @param funcName the call's name
     * @param expr     the call
     */
    private void rejectWrittenArity(final String funcName, final FunctionCallExpression expr) {
        rejectRowPositionOperands(expr);
        if (expr.isStar() || "PROJECTION_CONSTRAINT".equals(funcName)) {
            return;
        }
        for (final Expression arg : expr.getArguments()) {
            if (arg instanceof SpreadExpression) {
                return;
            }
        }
        final BuiltInFunction func = functionRegistry.getFunction(funcName);
        if (func == null) {
            return;
        }
        final int count = expr.getArguments().size();
        if (count < func.getMinArgCount() && func.refusesMissingArgumentsByType()) {
            throw arityMismatch("Invalid argument types for function '" + funcName + "': ("
                + strictArgTypeList(expr.getArguments()) + ")", expr);
        }
        if (count < func.getMinArgCount()) {
            throw arityMismatch("not enough arguments for function [" + strictPlanCallText(expr) + "], expected "
                + func.getMinArgCount() + ", got " + count, expr);
        }
        if (func.getMaxArgCount() >= 0 && count > func.getMaxArgCount()) {
            throw arityMismatch(tooManyArguments(funcName, expr, func.getMaxArgCount(), count), expr);
        }
        // A VARIANT source takes no scale and no format: live refuses TO_TIMESTAMP_NTZ(v, 3) and
        // TO_TIMESTAMP_NTZ(v, 'YYYY-MM-DD') alike as "too many arguments ... expected 1, got 2", for the
        // four TO_TIMESTAMP spellings (live-verified).
        if (count == 2 && VARIANT_TAKES_ONE_ARGUMENT.contains(funcName)
                && typeInferencer.infer(expr.getArguments().get(0)) instanceof VariantType) {
            throw arityMismatch("too many arguments for function [" + strictText(expr) + "] expected 1, got 2",
                expr);
        }
    }

    /**
     * The character offset a refusal points an argument at. A literal or a session variable is pointed
     * at its own place, resolved back to the STATEMENT: an expression is re-parsed from its own text,
     * so the recorded position is an offset into that fragment and only the resolver knows where the
     * fragment sits. A value FOLDED from anything else was written nowhere, and live says -1.
     *
     * @param argument the argument
     * @return its 0-based offset, 0 when nothing recorded one, or -1 for a folded value
     */
    private int writtenAt(final Expression argument) {
        final SourcePosition within;
        if (argument instanceof LiteralExpression) {
            within = ((LiteralExpression) argument).getPosition();
        } else if (argument instanceof SessionVarExpression) {
            within = ((SessionVarExpression) argument).getPosition();
        } else {
            return -1;
        }
        if (within == null) {
            return 0;
        }
        final SourcePosition at = ExpressionSource.resolve(within);
        return at == null ? within.getCharPositionInLine() : at.getCharPositionInLine();
    }

    /**
     * TRUNC's unit is NOT a slot — its second argument is an ordinary value, resolved before anything
     * asks what it means, and live then refuses whatever is not a string literal:
     *
     * <pre>
     *   TRUNC(tm, 'hour')   10:00:00 on both engines — the quoted form is the only one live takes
     *   TRUNC(tm, u)        Date/time component [SH.U ]for function TRUNC needs to be an identifier
     *                       or a string literal.
     *   TRUNC(tm, hour)     … [TO_CHAR(SH.HOUR) ] …   an INT column is converted on the way in
     *   TRUNC(tm, 1)        … [TO_CHAR(1) ] …
     * </pre>
     *
     * <p>Frostlake ACCEPTED the bareword and truncated to the hour, because the word was resolved as a
     * unit everywhere rather than only in slot positions. The TO_CHAR in the echo is live's own
     * implicit conversion of a non-text argument, and appears only there.
     *
     * <p>The NUMERIC TRUNC is untouched: the check runs only when the first argument is temporal, so
     * TRUNC(123.45, 1) keeps its scale argument. It runs BEFORE the argument-family rule, so a
     * temporal or a boolean in the unit position is judged as a unit ({@code TRUNC(d, d)} is
     * "[TO_CHAR(TT.D) ]", live-verified) while the same value beside a NUMERIC first argument is the
     * family refusal, "(NUMBER(2,1), DATE)".
     */
    private void rejectNonLiteralTruncUnit(final String funcName, final List<Expression> args) {
        if (!funcName.equals("TRUNC") && !funcName.equals("TRUNCATE") || args.size() < 2) {
            return;
        }
        if (!(typeInferencer.infer(args.get(0)) instanceof DateTimeType)) {
            return;
        }
        final Expression unit = args.get(1);
        if (unit instanceof LiteralExpression) {
            final LiteralType written = ((LiteralExpression) unit).getType();
            if (written == LiteralType.STRING || written == LiteralType.NULL) {
                return;
            }
        }
        final String echo = typeInferencer.infer(unit) instanceof StringType
            ? strictText(unit) : "TO_CHAR(" + strictText(unit) + ")";
        throw new RuntimeException(SqlCompilationError.inline(
            DateTimeUnitSlot.notAUnitRefusal(echo, funcName)));
    }

    /**
     * BASE64_ENCODE's optional arguments, and the decoders' alphabet, are decided while the statement
     * COMPILES — measured over an empty table, inside a view body and inside a CTAS, where every one
     * of these still refuses:
     *
     * <pre>
     *   BASE64_ENCODE(g, n)          argument 2 to function BASE64_ENCODE needs to be constant,
     *                                found 'EB.N'                                      (no position)
     *   BASE64_ENCODE(g, 0, g)       argument 3 …, found 'EB.G'
     *   BASE64_ENCODE(g, -1)         invalid argument for function [BASE64_ENCODE] unexpected
     *                                argument [maximum line length] at position 2,   (at the CALL)
     *   BASE64_DECODE_STRING(t, g)   argument 2 to function BASE64_DECODE_STRING needs to be constant
     * </pre>
     *
     * <p>CONSTANT means row-independent, not literal: {@code 0 - 1} is accepted and answers, where a
     * column is refused — see {@link RelationReferenceWalk}.
     *
     * <p>The BOUND is stranger, and the shape of it is the whole reason this is not simply the
     * row-time check moved earlier. It applies to an integral numeric LITERAL and to nothing else:
     *
     * <pre>
     *   -1  -2.0  -1e0  2147483648  2147483648.0     refused while compiling
     *   -1.5  -0.5  1.5  8.7  2147483647.5           accepted — not integral, so the row decides
     *   '-1'  NULL  0 - 1  -1::NUMBER(10,2)          accepted — not a numeric literal at all
     * </pre>
     *
     * <p>So {@code -1} refuses the statement and {@code 0 - 1} runs it, both meaning minus one. Live
     * is reading the literal, not the value, and a fractional literal is left entirely to the row —
     * where 2147483647.5 rounds up out of range and raises "Numeric value '2147483648' is out of
     * range" instead.
     */
    private void rejectIllegalBase64Arguments(final String funcName, final List<Expression> args,
                                              final Expression call) {
        final boolean encoding = funcName.equals("BASE64_ENCODE");
        if (!encoding && !funcName.equals("BASE64_DECODE_STRING")
                && !funcName.equals("BASE64_DECODE_BINARY")) {
            return;
        }
        if (encoding && args.size() > 1) {
            rejectRelationDependentArgument(funcName, args.get(1), 2);
            rejectOutOfRangeLineLength(args.get(1), call);
        }
        final int alphabet = encoding ? 2 : 1;
        if (args.size() > alphabet) {
            rejectRelationDependentArgument(funcName, args.get(alphabet), alphabet + 1);
        }
    }

    /** The unpositioned "needs to be constant" refusal, naming the argument as the plan prints it. */
    private void rejectRelationDependentArgument(final String funcName, final Expression arg,
                                                 final int position) {
        final RelationReferenceWalk walk = new RelationReferenceWalk();
        arg.accept(walk);
        if (walk.found()) {
            throw new RuntimeException(SqlCompilationError.of("argument " + position + " to function "
                + funcName + " needs to be constant, found '" + strictText(arg) + "'"));
        }
    }

    /** The positioned max-line-length refusal — integral numeric literals only, see the caller. */
    private void rejectOutOfRangeLineLength(final Expression arg, final Expression call) {
        final Expression literal = arg instanceof UnaryOperationExpression
            && (((UnaryOperationExpression) arg).getOperator() == UnaryOperator.NEGATE
                || ((UnaryOperationExpression) arg).getOperator() == UnaryOperator.PLUS)
            ? ((UnaryOperationExpression) arg).getOperand() : arg;
        if (!(literal instanceof LiteralExpression)
                || !(((LiteralExpression) literal).getValue() instanceof Number)
                || !(call instanceof FunctionCallExpression)) {
            return;
        }
        BigDecimal written = new BigDecimal(((LiteralExpression) literal).getValue().toString());
        if (literal != arg && ((UnaryOperationExpression) arg).getOperator() == UnaryOperator.NEGATE) {
            written = written.negate();
        }
        if (written.stripTrailingZeros().scale() > 0
                || written.signum() >= 0
                && written.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0) {
            return;
        }
        throw arityMismatch("invalid argument for function [BASE64_ENCODE]"
            + " unexpected argument [maximum line length] at position 2,", (FunctionCallExpression) call);
    }

    /**
     * The byte-capable text functions — CONTAINS, STARTSWITH, ENDSWITH, CHARINDEX, POSITION and the PAD
     * pair — take their text arguments as BYTES when every one of them is a BINARY, but refuse a BINARY
     * beside a string by the argument types, every argument listed, at the call; a PAD's omitted pad is the
     * string ' '. Live: CONTAINS(bn, bn) is TRUE and LPAD(bn, 3, bn) pads byte-wise, where
     * CONTAINS('a', bn) and LPAD(bn, 3) are "Invalid argument types for function '…': (…)".
     */
    private void rejectBinaryBesideText(final String funcName, final List<Expression> args, final Expression call) {
        final boolean pad = "LPAD".equals(funcName) || "RPAD".equals(funcName);
        if (!pad && !"CONTAINS".equals(funcName) && !"STARTSWITH".equals(funcName) && !"ENDSWITH".equals(funcName)
                && !"CHARINDEX".equals(funcName) && !"POSITION".equals(funcName)) {
            return;
        }
        if (!(call instanceof FunctionCallExpression) || args.size() < 2) {
            return;
        }
        final int[] slots = pad ? new int[] {0, 2} : new int[] {0, 1};
        boolean binary = false;
        boolean text = pad && args.size() < 3;
        for (final int slot : slots) {
            if (slot >= args.size()) {
                continue;
            }
            if (binaryArgumentType(args.get(slot)) != null) {
                binary = true;
            } else if (typeInferencer.infer(args.get(slot)) instanceof StringType) {
                text = true;
            }
        }
        if (!binary || !text) {
            return;
        }
        final StringBuilder types = new StringBuilder();
        for (final Expression arg : args) {
            final DataType type = typeInferencer.infer(arg);
            if (type == null) {
                return;
            }
            if (types.length() > 0) {
                types.append(", ");
            }
            types.append(SqlTypeNames.canonical(type));
        }
        throw arityMismatch("Invalid argument types for function '" + funcName + "': (" + types + ")",
            (FunctionCallExpression) call);
    }

    /**
     * IDENTIFIER('<text>') names a column only when the text reads as an identifier reference (see
     * SqlIdentifiers.isIdentifierReference). Live refuses any other literal while the statement compiles,
     * echoing it as written at its own position: {@code SELECT IDENTIFIER('my col') FROM t} is
     * "invalid identifier ''my col''" at 18, over an empty table too.
     */
    private void rejectNonIdentifierLiteral(final String funcName, final List<Expression> args) {
        if (!"IDENTIFIER".equals(funcName) || args.size() != 1 || !(args.get(0) instanceof LiteralExpression)) {
            return;
        }
        final LiteralExpression literal = (LiteralExpression) args.get(0);
        if (literal.getType() != LiteralType.STRING || literal.getValue() == null
                || SqlIdentifiers.isIdentifierReference(literal.getValue().toString())) {
            return;
        }
        final String detail = "invalid identifier ''" + literal.getValue().toString().replace("'", "''") + "''";
        final SourcePosition at = ExpressionSource.resolve(literal.getPosition());
        throw new RuntimeException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail));
    }

    /**
     * An OBJECT's key must be TEXT. Live names the offending DECLARED type and the function it was
     * given to — measured for every family:
     *
     * <pre>
     *   OBJECT_CONSTRUCT(v::VARIANT, x)  Function OBJECT_CONSTRUCT does not support VARIANT argument
     *                                    type for keys
     *   OBJECT_CONSTRUCT(n, x)           … does not support NUMBER(38,0) argument type for keys
     *   OBJECT_CONSTRUCT(b, x)           … BOOLEAN …          OBJECT_CONSTRUCT(d, x)  … DATE …
     *   OBJECT_CONSTRUCT_KEEP_NULL(…)    the same sentence under its OWN name
     * </pre>
     *
     * <p>Only the KEY positions — the even ones — are asked: a VARIANT VALUE is ordinary and accepted,
     * and so is the whole {@code OBJECT_CONSTRUCT(*)} form, which has no key arguments at all. An
     * UNDETERMINED key is left alone, the same rule every static refusal here follows.
     *
     * <p>Note this is NOT the rule OBJECT_AGG follows: that one ACCEPTS a VARIANT key and uses its text
     * (see ObjectAggAccumulator). The two functions genuinely diverge, both measured.
     */
    private void rejectNonTextObjectKey(final String funcName, final List<Expression> args,
                                        final Expression call) {
        if (!"OBJECT_CONSTRUCT".equals(funcName) && !"OBJECT_CONSTRUCT_KEEP_NULL".equals(funcName)) {
            return;
        }
        if (call instanceof FunctionCallExpression && ((FunctionCallExpression) call).isStar()) {
            return;
        }
        // An ODD count is refused for its arity, at the call, before any key is looked at (live:
        // OBJECT_CONSTRUCT('k', a, b) is "invalid number of arguments for [OBJECT_CONSTRUCT], expected 4,
        // got 3" whatever b is; a star beside the pairs counts as its spliced columns).
        if (args.size() % 2 != 0 && call instanceof FunctionCallExpression) {
            throw arityMismatch("invalid number of arguments for [" + funcName + "], expected "
                + (args.size() + 1) + ", got " + args.size(), (FunctionCallExpression) call);
        }
        for (int i = 0; i < args.size(); i += 2) {
            final DataType keyType = typeInferencer.infer(args.get(i));
            if (keyType == null || keyType instanceof StringType) {
                continue;
            }
            throw new RuntimeException(SqlCompilationError.of("Function " + funcName
                + " does not support " + SqlTypeNames.canonical(keyType)
                + " argument type for keys"));
        }
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
        if (funcName.equals("COUNT_IF") && args.size() == 1) {
            // COUNT_IF is planned as SUM(IFF(CAST(x AS BOOLEAN), 1, 0)), and it is the IFF that refuses
            // a non-boolean argument — in its own name, listing the argument beside its two constants,
            // at the call (live-verified over NUMBER, VARCHAR, FLOAT, DATE and VARIANT alike).
            final DataType counted = typeInferencer.infer(args.get(0));
            if (counted != null && !(counted instanceof BooleanType)) {
                throw new RuntimeException(positionedArgumentTypes(call, "Invalid argument types for function 'IFF': ("
                    + strictArgTypeText(args.get(0)) + ", NUMBER(1,0), NUMBER(1,0))"));
            }
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
            final DataType binary = binaryArgumentType(args.get(position));
            if (binary != null) {
                final SemiStructuredRejection onBinary = function.binaryRejection(position);
                if (onBinary != SemiStructuredRejection.NONE) {
                    // A call judged as its REWRITE can be legal there — two binaries splice — and then
                    // there is no sentence to throw.
                    final String refusal = semiStructuredRejectionMessage(onBinary, funcName, args, position,
                        binary, call);
                    if (refusal != null) {
                        throw new RuntimeException(refusal);
                    }
                }
                continue;
            }
            final DataType uuid = uuidArgumentType(args.get(position));
            if (uuid != null) {
                final SemiStructuredRejection onUuid = function.uuidRejection(position);
                if (onUuid != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onUuid, funcName, args, position, uuid, call));
                }
                continue;
            }
            final DataType vector = vectorArgumentType(args.get(position));
            if (vector != null) {
                final SemiStructuredRejection onVector = function.vectorRejection(position);
                if (onVector != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onVector, funcName, args, position, vector,
                            call));
                }
                continue;
            }
            final DataType bool = booleanArgumentType(args.get(position));
            if (bool != null) {
                final SemiStructuredRejection onBoolean = function.booleanRejection(position);
                if (onBoolean != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onBoolean, funcName, args, position, bool,
                            call));
                }
                continue;
            }
            final DataType temporal = temporalArgumentType(args.get(position));
            if (temporal != null) {
                final SemiStructuredRejection onTemporal = function.temporalRejection(position, args.size(),
                    temporal);
                if (onTemporal != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onTemporal, funcName, args, position, temporal,
                            call));
                }
                continue;
            }
            final DataType approximate = approximateArgumentType(args.get(position));
            if (approximate != null) {
                final SemiStructuredRejection onApproximate = function.approximateRejection(position);
                if (onApproximate != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onApproximate, funcName, args, position, approximate,
                            call));
                }
                continue;
            }
            final DataType text = textArgumentType(args.get(position));
            if (text != null) {
                final SemiStructuredRejection onText = function.textRejection(position);
                if (onText != SemiStructuredRejection.NONE) {
                    throw new RuntimeException(
                        semiStructuredRejectionMessage(onText, funcName, args, position, text, call));
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
     * The BINARY type of an expression, or null when it is not statically one. Asked separately from
     * the semi-structured question for the same reason a FILE is: the two have opposite answers for
     * the same function, since almost every text function TAKES a binary and only the ones that must
     * decode it refuse one.
     *
     * @param arg the argument expression
     * @return its declared BINARY type, or null
     */
    private DataType binaryArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof BinaryType ? inferred : null;
    }

    /** The BOOLEAN type of an expression, or null when it is not statically a BOOLEAN. */
    private DataType booleanArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof BooleanType ? inferred : null;
    }

    /** The FLOAT type of an expression, or null when it is not statically approximate. */
    private DataType approximateArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof NumericType && NumericType.isApproximate(inferred) ? inferred : null;
    }

    /** The VARCHAR type of an expression, or null when it is not statically a text. */
    private DataType textArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof StringType ? inferred : null;
    }

    /** The DATE/TIME/TIMESTAMP type of an expression, or null when it is not statically temporal. */
    private DataType temporalArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof DateTimeType ? inferred : null;
    }

    /**
     * The VECTOR type of an expression, or null when it is not statically a VECTOR.
     *
     * <p>A vector RENDERS perfectly well — selecting one prints its elements — but it has no
     * CONVERSION to text: live refuses TO_VARCHAR, TO_CHAR, every cast to a string type and both
     * concatenation spellings over one. So the question is asked separately from the semi-structured
     * families, which a vector is not a member of, and separately from the display path, which the
     * refusal must not disturb.
     */
    /** The argument's type when it is a UUID, or null — the UUID twin of {@link #vectorArgumentType}. */
    private DataType uuidArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof UuidType ? inferred : null;
    }

    private DataType vectorArgumentType(final Expression arg) {
        final DataType inferred = typeInferencer.infer(arg);
        return inferred instanceof VectorType ? inferred : null;
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
        if (rejection == SemiStructuredRejection.BOOLEAN_CONVERSION_PARAMETER) {
            // The boolean aggregates are planned over TO_BOOLEAN of their argument, and live refuses
            // the conversion of a FLOAT, a temporal, a BINARY or a structured value in the
            // conversion's own words, the argument quoted from the plan, no position:
            // BOOLOR_AGG(f) is "invalid type [TO_BOOLEAN(AW.F)] for parameter 'TO_BOOLEAN'".
            return SqlCompilationError.of("invalid type [TO_BOOLEAN(" + renderedArguments(args)
                + ")] for parameter 'TO_BOOLEAN'");
        }
        if (rejection == SemiStructuredRejection.INCOMPATIBLE_TYPES) {
            // A COMPILATION error, with the prefix that says so (live-verified): a view over
            // MEDIAN(o) is refused, not created empty.
            return SqlCompilationError.of(
                "incompatible types: [" + semiStructuredTypeName(inferred) + "] and [NUMBER(9,0)]");
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
        if (rejection == SemiStructuredRejection.DOUBLE_CONVERSION_PARAMETER && args.size() == 2) {
            // The two-argument statistics convert each argument to a DOUBLE under a null guard on the
            // OTHER one, and the conversion is what live refuses — the guarded plan quoted back, the
            // null substitute spelled after the refused family, no position.
            final String family = strictArgTypeText(args.get(position)).replaceAll("\\(.*\\)$", "");
            final String other = renderedArguments(Collections.singletonList(args.get(1 - position)));
            final String refused = renderedArguments(Collections.singletonList(args.get(position)));
            return SqlCompilationError.of("invalid type [TO_DOUBLE(IFF(" + other
                + " IS NULL, SYSTEM$NULL_TO_" + family + "(NULL), " + refused + "))] for parameter 'TO_DOUBLE'");
        }
        if (rejection == SemiStructuredRejection.CROSS_PRODUCT_OPERANDS && args.size() == 2) {
            // The sum of x·y is reached first, and x is the SECOND argument: its type leads.
            return positionedArgumentTypes(call, "Invalid argument types for function '*': ("
                + strictArgTypeText(args.get(1)) + ", " + strictArgTypeText(args.get(0)) + ")");
        }
        if (rejection == SemiStructuredRejection.MULTIPLY_OPERANDS) {
            // Live never names the aggregate here: it reaches its internal sum of SQUARES first and
            // reports that multiplication, listing the one offending argument twice — "Invalid
            // argument types for function '*': (OBJECT, OBJECT)" for STDDEV(o) as for VARIANCE(o),
            // and "(ARRAY, ARRAY)" over an ARRAY column.
            final String operand = strictArgTypeText(args.get(position));
            return positionedArgumentTypes(call,
                "Invalid argument types for function '*': (" + operand + ", " + operand + ")");
        }
        if (rejection == SemiStructuredRejection.REPEAT_REWRITE_OPERANDS && args.size() == 2) {
            return repeatRewriteRefusal(args, call);
        }
        if (rejection == SemiStructuredRejection.SPACE_REWRITE_OPERANDS && args.size() == 1) {
            // SPACE(n) is LPAD('', n, ' '): the count sits between the two one-character strings.
            return positionedArgumentTypes(call, "Invalid argument types for function 'LPAD': (VARCHAR(1), "
                + strictArgTypeText(args.get(0)) + ", VARCHAR(1))");
        }
        if (rejection == SemiStructuredRejection.INSERT_REWRITE_OPERANDS && args.size() == 4) {
            return insertRewriteRefusal(args, call);
        }
        if (rejection == SemiStructuredRejection.TIME_TO_TIMESTAMP_LTZ) {
            return SqlCompilationError.of("incompatible types: [" + SqlTypeNames.canonical(inferred)
                + "] and [TIMESTAMP_LTZ(9)]");
        }
        final String reported = reportedFunctionName(funcName, isBareCall(call));
        // The accumulator live names takes the VALUE alone, so the fraction is not listed with it.
        final List<Expression> listed = "APPROX_PERCENTILE_ACCUMULATE".equals(reported) && args.size() > 1
            ? args.subList(0, 1) : args;
        return positionedArgumentTypes(call,
            "Invalid argument types for function '" + reported + "': (" + strictArgTypeList(listed) + ")");
    }

    /**
     * REPEAT(s, n) as the account plans it, {@code LPAD('', n * LENGTH(s), s)}. The product is typed
     * first, LENGTH declaring NUMBER(18,0), so a count the multiplication refuses is refused in the
     * product's words; otherwise LPAD refuses the BINARY string, the product listed at its own width
     * (all live-verified, each at the call):
     *
     * <pre>
     *   REPEAT(bn, 2)       'LPAD': (VARCHAR(1), NUMBER(19,0), BINARY(8388608))
     *   REPEAT(bn, n5)      'LPAD': (VARCHAR(1), NUMBER(23,0), BINARY(8388608))   over a NUMBER(5,0)
     *   REPEAT(bn, 2.5)     'LPAD': (VARCHAR(1), NUMBER(20,1), BINARY(8388608))
     *   REPEAT(bn, '2')     'LPAD': (VARCHAR(1), NUMBER(36,0), BINARY(8388608))   a text or a NULL reads NUMBER(18,0)
     *   REPEAT(bn, v)       'LPAD': (VARCHAR(1), FLOAT, BINARY(8388608))          a FLOAT or a VARIANT
     *   REPEAT(bn, TRUE)    '*': (BOOLEAN, NUMBER(18,0))
     *   REPEAT('ab', bn)    '*': (BINARY(8388608), NUMBER(18,0))
     * </pre>
     */
    private String repeatRewriteRefusal(final List<Expression> args, final Expression call) {
        final DataType count = typeInferencer.infer(args.get(1));
        if (count != null && !(count instanceof NumericType) && !(count instanceof StringType)
                && !(count instanceof VariantType)) {
            return positionedArgumentTypes(call, "Invalid argument types for function '*': ("
                + strictArgTypeText(args.get(1)) + ", NUMBER(18,0))");
        }
        final NumericType length = new NumericType("NUMBER", 18, 0);
        final DataType product = count instanceof VariantType ? NumericType.FLOAT
            : BinaryOperationTypes.resultOf(BinaryOperator.MULTIPLY,
                count instanceof NumericType ? count : length, length);
        return positionedArgumentTypes(call, "Invalid argument types for function 'LPAD': (VARCHAR(1), "
            + SqlTypeNames.canonical(product) + ", " + strictArgTypeText(args.get(0)) + ")");
    }

    /**
     * INSERT(s, p, l, i) as the account plans it, {@code SUBSTR(s, 1, p - 1) || i || SUBSTR(s, p + l)}.
     * The arithmetic is typed before the concatenation, so a BINARY position is the subtraction's
     * refusal and a BINARY length the addition's; past them the concatenation refuses a BINARY beside
     * any other family, the base listed twice around the insertion and an untyped NULL base read as the
     * unbounded VARCHAR (all live-verified, each at the call):
     *
     * <pre>
     *   INSERT('abc', bn, 1, 'x')    '-': (BINARY(8388608), NUMBER(1,0))
     *   INSERT('abc', 1, bn, 'x')    '+': (NUMBER(1,0), BINARY(8388608))
     *   INSERT(bn, 1, 1, 'x')        '||': (BINARY(8388608), VARCHAR(1), BINARY(8388608))
     *   INSERT(NULL, 1, 1, bn)       '||': (VARCHAR(134217728), BINARY(8388608), VARCHAR(134217728))
     *   INSERT(bn, 1, 1, bn)         legal: the bytes splice
     *   INSERT(bn, 1, 1, NULL)       legal: NULL
     * </pre>
     *
     * @return the refusal, or null when the call is legal as its rewrite
     */
    private String insertRewriteRefusal(final List<Expression> args, final Expression call) {
        if (binaryArgumentType(args.get(1)) != null) {
            return positionedArgumentTypes(call, "Invalid argument types for function '-': ("
                + strictArgTypeText(args.get(1)) + ", NUMBER(1,0))");
        }
        if (binaryArgumentType(args.get(2)) != null) {
            return positionedArgumentTypes(call, "Invalid argument types for function '+': ("
                + strictArgTypeText(args.get(1)) + ", " + strictArgTypeText(args.get(2)) + ")");
        }
        final Expression base = args.get(0);
        final Expression insertion = args.get(3);
        final DataType baseType = typeInferencer.infer(base);
        final DataType insertionType = typeInferencer.infer(insertion);
        final boolean nullBase = baseType == null && base instanceof LiteralExpression
            && ((LiteralExpression) base).getType() == LiteralType.NULL;
        if (baseType == null && !nullBase || insertionType == null) {
            // An undetermined side is left alone, and a NULL insertion concatenates with anything.
            return null;
        }
        if ((baseType instanceof BinaryType) == (insertionType instanceof BinaryType)) {
            return null;
        }
        final String baseText = nullBase ? "VARCHAR(" + StringResultWidths.UNBOUNDED + ")" : strictArgTypeText(base);
        return positionedArgumentTypes(call, "Invalid argument types for function '||': (" + baseText + ", "
            + strictArgTypeText(insertion) + ", " + baseText + ")");
    }

    /**
     * An argument-type refusal as a COMPILE-TIME error anchored on the CALL. Both halves are
     * live-measured and both matter: without the "SQL compilation error" marker the view path treats
     * the refusal as data-dependent and creates a view with NO COLUMNS, and live positions the sentence
     * at the function name itself — {@code SELECT UPPER(o) …} reads position 7, and the same body
     * inside {@code CREATE VIEW … AS SELECT UPPER(o) …} reads position 38, which is that name's offset
     * in the whole statement.
     */
    private String positionedArgumentTypes(final Expression call, final String detail) {
        if (call instanceof FunctionCallExpression
                && "APPROX_PERCENTILE".equalsIgnoreCase(((FunctionCallExpression) call).getFunctionName())) {
            // ★ LIVE ANCHORS THIS ONE NOWHERE: "error line 0 at position -1", whatever the call's
            // place — its accumulator is refused from a plan that has lost the source offsets.
            // Reproduced as measured rather than tidied.
            return SqlCompilationError.at(0, -1, detail);
        }
        return positionedAt(call instanceof FunctionCallExpression
            ? ((FunctionCallExpression) call).getPosition() : null, detail);
    }

    /**
     * A compile-time refusal anchored on a recorded place — a call's name, a sign's operator —
     * resolved back to the statement, or the bare sentence when nothing recorded one.
     */
    private static String positionedAt(final SourcePosition within, final String detail) {
        final SourcePosition at = within == null ? null : ExpressionSource.resolve(within);
        return at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : SqlCompilationError.of(detail);
    }


    /**
     * A QUOTIENT presented at the scale its column DECLARES, which is not the scale the division was
     * carried out at. The declared type was already right on every cell measured — {@code 1.0 / 3.0} is
     * NUMBER(7,6) on both engines — but the value was handed back at the divide's own working scale, so
     * live answered 0.333333 where Frostlake answered 0.3333333, and a dividend written with more decimals
     * pushed it further out ({@code 1.0000000 / 3.0} reached twelve). Storing the value already applied
     * the declared scale; only the direct read did not.
     *
     * <p>The scale is applied ONLY where both operands are exact numerics, for the reason
     * {@link #atDeclaredScale} gives: over a VARIANT or an undetermined operand the quotient falls back to
     * a nominal type whose scale says nothing about the value, and re-scaling to it would round real
     * digits away.
     */
    /**
     * The type SYSTEM$TYPEOF reports for {@code typed}: its declared type, except for one shape the
     * account's plan rewrites before it types — SUM over a bare column shifted by a constant,
     * {@code SUM(c + k)} or {@code SUM(c - k)} — which reports the rewrite's wider intermediate while
     * the query's column (a CTAS over it, the driver's metadata) keeps the ordinary SUM width. Over a
     * NUMBER(4,0) holding varying values {@code SUM(c + 1)} reports NUMBER(20,0) where the column
     * declares NUMBER(17,0): nineteen digits plus the constant's own precision for a scale-0 column
     * ({@code SUM(c + 1::NUMBER(18,0))} is NUMBER(37,0)), six digits over the ordinary width for a
     * scaled one ({@code SUM(n10_2 + 1)} NUMBER(29,2), {@code SUM(n5_3 + 0.5)} NUMBER(24,3)), and
     * only for a constant whose scale falls short of the column's — {@code SUM(n10_2 + 0.05)} keeps
     * NUMBER(23,2), {@code SUM(c + 1.5)} over the scale-0 column NUMBER(18,1). A column whose
     * statistics hold one value is not rewritten (a single row, every row alike, no row at all), nor
     * is AVG, a window SUM or any other shape of argument; SUM(DISTINCT …) is rewritten like the
     * plain SUM (all live-verified).
     */
    private DataType planReportedType(final Expression typed, final DataType declared) {
        if (!(typed instanceof FunctionCallExpression) || !(declared instanceof NumericType)
                || NumericType.isApproximate(declared)) {
            return declared;
        }
        final FunctionCallExpression call = (FunctionCallExpression) typed;
        if (!"SUM".equalsIgnoreCase(call.getFunctionName()) || call.isStar()
                || call.getArguments().size() != 1
                || !(call.getArguments().get(0) instanceof BinaryOperationExpression)) {
            return declared;
        }
        final BinaryOperationExpression shifted = (BinaryOperationExpression) call.getArguments().get(0);
        if (shifted.getOperator() != BinaryOperator.ADD && shifted.getOperator() != BinaryOperator.SUBTRACT) {
            return declared;
        }
        final Expression column = shifted.getLeft() instanceof ColumnReferenceExpression ? shifted.getLeft()
            : shifted.getRight() instanceof ColumnReferenceExpression ? shifted.getRight() : null;
        if (column == null) {
            return declared;
        }
        final Expression constant = column == shifted.getLeft() ? shifted.getRight() : shifted.getLeft();
        final DataType columnType = typeInferencer.infer(column);
        final NumericType constantType = foldedConstantType(constant);
        if (!(columnType instanceof NumericType) || NumericType.isApproximate(columnType)
                || constantType == null) {
            return declared;
        }
        final int columnScale = Math.max(0, ((NumericType) columnType).getScale());
        if (Math.max(0, constantType.getScale()) >= Math.max(columnScale, 1)) {
            return declared;
        }
        final ValueRange held = inferStaticRange(column);
        if (held == null || held.isEmpty() || held.getMin().compareTo(held.getMax()) == 0) {
            return declared;
        }
        final NumericType sum = (NumericType) declared;
        final int widened = columnScale == 0
            ? Math.max(sum.getPrecision(), 19 + constantType.getPrecision())
            : sum.getPrecision() + 6;
        return new NumericType("NUMBER", Math.min(38, widened), sum.getScale());
    }

    /**
     * The type the plan's constant folding gives a column-free numeric operand — a literal, a literal
     * under a cast or a sign, arithmetic or ABS over such, a scalar subquery — or null for anything
     * else (a function the plan leaves standing, LENGTH('ab') among them). A decimal literal whose
     * fraction is zero folds to its whole value: {@code c + 1.0} is typed as {@code c + 1} and
     * {@code c + 10.0} as {@code c + 10} (live-verified).
     */
    private NumericType foldedConstantType(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expr;
            if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
                return null;
            }
            final BigDecimal value = new BigDecimal(String.valueOf(literal.getValue())).stripTrailingZeros();
            if (value.scale() <= 0) {
                return new NumericType("NUMBER", Math.max(1, value.precision() - Math.min(0, value.scale())), 0);
            }
            return exactNumeric(typeInferencer.infer(expr));
        }
        if (expr instanceof CastExpression) {
            return ((CastExpression) expr).getExpression() instanceof LiteralExpression
                ? exactNumeric(typeInferencer.infer(expr)) : null;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            return unary.getOperator() == UnaryOperator.NEGATE && foldedConstantType(unary.getOperand()) != null
                ? exactNumeric(typeInferencer.infer(expr)) : null;
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return foldedConstantType(binary.getLeft()) != null && foldedConstantType(binary.getRight()) != null
                ? exactNumeric(typeInferencer.infer(expr)) : null;
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            return "ABS".equalsIgnoreCase(call.getFunctionName()) && call.getArguments().size() == 1
                && foldedConstantType(call.getArguments().get(0)) != null
                ? exactNumeric(typeInferencer.infer(expr)) : null;
        }
        if (expr instanceof SubqueryExpression) {
            return exactNumeric(typeInferencer.infer(expr));
        }
        return null;
    }

    /** {@code type} when it is an exact NUMBER, else null. */
    private static NumericType exactNumeric(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) ? (NumericType) type : null;
    }

    /**
     * The scale an exact-numeric operator result DECLARES, or null when either operand is not an exact
     * number — the guard {@link #quotientAtDeclaredScale} applies, for the same reason: over a VARIANT
     * or an undetermined operand the declared type is nominal and says nothing about the value.
     */
    private Integer declaredExactScale(final BinaryOperationExpression call) {
        final DataType leftType = typeInferencer.infer(call.getLeft());
        final DataType rightType = typeInferencer.infer(call.getRight());
        if (!(leftType instanceof NumericType) || NumericType.isApproximate(leftType)
                || !(rightType instanceof NumericType) || NumericType.isApproximate(rightType)) {
            return null;
        }
        final DataType declared = typeInferencer.infer(call);
        return declared instanceof NumericType && !NumericType.isApproximate(declared)
            ? Integer.valueOf(((NumericType) declared).getScale()) : null;
    }

    private Object quotientAtDeclaredScale(final BinaryOperationExpression call, final Object value) {
        if (!(value instanceof BigDecimal)) {
            return value;
        }
        final DataType quotient = typeInferencer.infer(call);
        if (!(quotient instanceof NumericType) || NumericType.isApproximate(quotient)
                || !(typeInferencer.infer(call.getLeft()) instanceof NumericType)
                || !(typeInferencer.infer(call.getRight()) instanceof NumericType)) {
            return value;
        }
        final int scale = ((NumericType) quotient).getScale();
        return ((BigDecimal) value).scale() == scale ? value
            : ((BigDecimal) value).setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * ZEROIFNULL's answer CONVERTED to the type the call declares, which is the whole of what makes it
     * differ from its twin. NULLIFZERO hands its argument back untouched — same value, same type — while
     * ZEROIFNULL substitutes a NUMBER and converts to match it, so live answers:
     *
     * <pre>
     *   ZEROIFNULL(&lt;NUMBER(10,2) NULL&gt;)   0.00    the column's own scale, padded
     *   ZEROIFNULL(&lt;VARIANT holding 7&gt;)    7.0     the VARIANT family converts to REAL
     *   ZEROIFNULL(&lt;VARCHAR '7'&gt;)          7.0     so does VARCHAR
     *   ZEROIFNULL(&lt;VARIANT holding "a"&gt;)  Failed to cast variant value "a" to REAL
     *   ZEROIFNULL(&lt;VARCHAR 'a'&gt;)          Numeric value 'a' is not recognized
     * </pre>
     *
     * <p>Both refusals fall out of the conversion rather than being written here, which is the point of
     * doing it as a cast: the two source families already spell their failures the two ways live does.
     *
     * @param funcName the call's function name
     * @param call the call expression, for its declared type
     * @param value the value the function returned
     * @return the value at the declared type
     */
    private Object zeroIfNullConverted(final String funcName, final Expression call, final Object value) {
        if (!"ZEROIFNULL".equals(funcName) || value == null) {
            return value;
        }
        final DataType declared = typeInferencer.infer(call);
        if (!(declared instanceof NumericType)) {
            return value;
        }
        if (NumericType.isApproximate(declared)) {
            return ValueCaster.castValue(value, declared.getName());
        }
        final int scale = ((NumericType) declared).getScale();
        if (scale == 0 && (value instanceof Long || value instanceof Integer
                || value instanceof BigInteger)) {
            // An exact whole number already IS its declared type: converting would only exchange one
            // Java carrier for another, and a BigDecimal that was a Long stops comparing equal to the
            // values around it (scale-sensitive equality), which a PIVOT's output columns feed
            // straight into.
            return value;
        }
        return ValueCaster.castValue(value, declared.getName() + "("
            + ((NumericType) declared).getPrecision() + "," + scale + ")");
    }

    /**
     * A ROUNDING result presented at the scale its column DECLARES, which is not always the scale it
     * was rounded to. Where the scale argument is not a literal the declared scale stays the INPUT's,
     * so a value rounded to fewer places is padded back out — live answers
     * {@code CEIL(<NUMBER(38,10)>, i)} as 1.2345679000, ten decimals for a value rounded at seven.
     * Only the static channel knows which case this is, so the padding happens here rather than in the
     * function.
     */
    /**
     * An APPROXIMATE rounding argument handed over as a double, which is what decides the rule it
     * gets.
     *
     * <p>★ THE SCALE LIMIT IS AN EXACT-NUMERIC RULE. "Scale too large" exists because a NUMBER(p,s)
     * cannot be rounded to a place its own width does not reach; a FLOAT has no declared scale to
     * exceed, so live simply computes — {@code CEIL(<FLOAT 1.0>, -38)} is 1.0E38 where the same call
     * over a NUMBER(38,0) is refused for the other reason entirely, that the RESULT does not fit. The
     * two refusals partition the families, and this is what tells the function which side it is on.
     *
     * <p>The conversion is where the decision has to happen because a FLOAT-declared value can still
     * carry a BigDecimal — computed exactly, or restored from an older snapshot — so the value alone
     * cannot say which family it came from. Only the declared type can, and a VARIANT holding a double
     * says it through its node instead.
     * A VARCHAR is NOT approximate: live gives it the exact-numeric refusal.
     */
    private List<Object> approximateRoundingArgs(final String funcName, final Expression call,
                                                 final List<Object> argValues) {
        if ("UNIFORM".equals(funcName) && call instanceof FunctionCallExpression) {
            return uniformBoundArgs((FunctionCallExpression) call, argValues);
        }
        if (!isRoundingFamily(funcName) || argValues.isEmpty()
                || !(call instanceof FunctionCallExpression)) {
            return argValues;
        }
        final Double approximateInput = approximateRoundingInput(
            (FunctionCallExpression) call, argValues.get(0));
        if (approximateInput == null) {
            return argValues;
        }
        final List<Object> approximate = new ArrayList<>(argValues);
        approximate.set(0, approximateInput);
        return approximate;
    }

    /**
     * UNIFORM's bounds with the WRITTEN SPELLING restored. Live decides the draw's family from how the
     * bound was written — {@code UNIFORM(0.0, 1.0, …)} draws scaled values — even though the literal
     * itself normalises to NUMBER(1,0) everywhere else (1.00 types, refuses and renders as 1). The
     * normalisation strips the scale the function used to read, so a bound written with a decimal
     * point is handed over as a DOUBLE, which the function's own family check already accepts.
     */
    private List<Object> uniformBoundArgs(final FunctionCallExpression call,
                                          final List<Object> argValues) {
        List<Object> adjusted = argValues;
        for (int i = 0; i < 2 && i < call.getArguments().size() && i < argValues.size(); i++) {
            final Expression argument = call.getArguments().get(i);
            if (argument instanceof LiteralExpression
                    && ((LiteralExpression) argument).getType() == LiteralType.DECIMAL
                    && argValues.get(i) instanceof BigDecimal) {
                if (adjusted == argValues) {
                    adjusted = new ArrayList<>(argValues);
                }
                adjusted.set(i, Double.valueOf(((BigDecimal) argValues.get(i)).doubleValue()));
            }
        }
        return adjusted;
    }

    /**
     * The argument as a double when it is APPROXIMATE, and null when it is not.
     *
     * <p>Two ways to be approximate, because the two carriers keep the fact in different places: a
     * column says it in its DECLARED type, and a VARIANT says it in the NODE it holds. A VARIANT is
     * asked directly rather than through the inferencer, which can only report VARIANT for it — and
     * live does answer over one, so leaving it out would keep the refusal exactly where the value is
     * least able to defend itself.
     */
    private Double approximateRoundingInput(final FunctionCallExpression call, final Object value) {
        if (value instanceof VariantValue) {
            final JsonNode node = ((VariantValue) value).node();
            return node != null && node.isFloatingPointNumber() && !node.isBigDecimal()
                ? Double.valueOf(node.doubleValue()) : null;
        }
        if (!(value instanceof Number) || call.getArguments().isEmpty()) {
            return null;
        }
        final DataType argumentType = typeInferencer.infer(call.getArguments().get(0));
        return argumentType instanceof NumericType && NumericType.isApproximate(argumentType)
            ? Double.valueOf(((Number) value).doubleValue()) : null;
    }

    private Object atDeclaredScale(final String funcName, final Expression call, final Object value) {
        // MOD presents at its declared scale too — the remainder operator's type: MOD(7, n10_2) over a
        // NUMBER(10,2) is 7.00 on the account, the whole dividend re-scaled to the declared pair.
        if (!(value instanceof Number) || !(isRoundingFamily(funcName) || funcName.equals("MOD"))
                || !(call instanceof FunctionCallExpression)) {
            return value;
        }
        final List<Expression> arguments = ((FunctionCallExpression) call).getArguments();
        final DataType type = arguments.isEmpty() ? null : typeInferencer.infer(call);
        if (!(type instanceof NumericType)) {
            return value;
        }
        if (NumericType.isApproximate(type)) {
            // An approximate result is a DOUBLE however the column stored it — live answers
            // CEIL(<FLOAT>) as 3.0, not 3, and the exact value would render the other way.
            return Double.valueOf(((Number) value).doubleValue());
        }
        // The scale is only applied where the declared type was DERIVED from an exact numeric input.
        // Over a VARIANT or an undetermined argument the call falls back to a nominal type whose
        // scale says nothing about the value, and re-scaling to it would round real digits away.
        final DataType argument = typeInferencer.infer(arguments.get(0));
        if (!(value instanceof BigDecimal)
                || !(argument instanceof NumericType || argument instanceof StringType)) {
            return value;
        }
        final int scale = ((NumericType) type).getScale();
        final BigDecimal presented = ((BigDecimal) value).scale() == scale ? (BigDecimal) value
            : ((BigDecimal) value).setScale(scale, RoundingMode.HALF_UP);
        // ABS keeps the carrier's own ceiling, not the declared range: ABS(-a - 1) over 38 nines
        // answers its 39 digits like a + 1 does, and only -2^127 is refused — by the function itself,
        // in the raw-result form (live-verified). The rounding functions widen past their declared
        // type and are refused here at the plain digits.
        if (!funcName.equals("ABS")) {
            rejectPastRepresentableRange(presented, (NumericType) type, arguments.get(0));
        }
        return presented;
    }

    /**
     * A rounded result its own declared type cannot hold.
     *
     * <p>★ ONLY ROUNDING UP CAN REACH THIS. {@code CEIL(<NUMBER(38,0)>, -38)} is 10^38, one digit past
     * what a NUMBER(38,0) holds, and live refuses the value rather than answering it; FLOOR, ROUND and
     * TRUNC over the same input all round toward zero and answer 0. It is a ROW-TIME refusal — over an
     * empty table the same call returns no rows on both engines.
     *
     * <p>★ THE (p,s) IS THE RESULT'S, NOT THE INPUT'S, which is why the check is "does it fit at all"
     * rather than "does it fit what it came from": {@code CEIL(<NUMBER(21,0)>, -21)} produces 10^21 and
     * live ACCEPTS it, because the family's typing rule has already widened the declared type to
     * NUMBER(38,0) — and 10^21 fits that. So the printed type is NUMBER(38,0) for both inputs.
     *
     * <p>The storage class comes off the VALUE, which is the whole family's rule — see
     * {@link dev.frostlake.executor.NumericRangeRefusal}. Every value that reaches HERE reads SB16,
     * because a scaled input never gets this far (a negative scale past the type's own is refused first,
     * as "Scale too large"), so nothing narrower than the 38-digit class can arrive; the shared reading
     * is used anyway rather than a local constant, so the one rule stays in one place.
     *
     * @param result the value as it would be presented
     * @param declared the result's own declared type
     * @param argument the call's first argument, whose column supplies the nullability
     */
    private void rejectPastRepresentableRange(final BigDecimal result, final NumericType declared,
                                              final Expression argument) {
        if (result.precision() - result.scale()
                <= declared.getPrecision() - declared.getScale()) {
            return;
        }
        throw new RuntimeException(NumericRangeRefusal.typed(
            SignedStorageWidth.tagOf(result, declared.getScale()), declared.getPrecision(),
            declared.getScale(), isNullableArgument(argument), result));
    }

    /**
     * Whether the value the refusal above is about could have been NULL — live spells the answer into
     * the message, {@code {nullable}} or {@code {not null}}, and takes it from the COLUMN. Anything
     * that is not a column reference reads as not-null.
     *
     * @param argument the expression the value came from
     * @return whether it is nullable
     */
    private boolean isNullableArgument(final Expression argument) {
        // A minus keeps its operand's nullability: ROUND(-a, -1) refuses as {nullable} over a nullable
        // column, exactly as ROUND(a, -1) does (live-verified).
        Expression source = argument;
        while (source instanceof UnaryOperationExpression
                && (((UnaryOperationExpression) source).getOperator() == UnaryOperator.NEGATE
                    || ((UnaryOperationExpression) source).getOperator() == UnaryOperator.PLUS)) {
            source = ((UnaryOperationExpression) source).getOperand();
        }
        if (!(source instanceof ColumnReferenceExpression)) {
            return false;
        }
        final TableColumn resolved = resolveDeclaredColumn((ColumnReferenceExpression) source);
        return resolved == null || resolved.isNullable();
    }


    /** The functions whose DECLARED type decides how their result is presented. */
    private static boolean isRoundingFamily(final String funcName) {
        return funcName.equals("CEIL") || funcName.equals("CEILING") || funcName.equals("FLOOR")
            || funcName.equals("ROUND") || funcName.equals("TRUNC") || funcName.equals("TRUNCATE")
            || funcName.equals("ABS") || funcName.equals("SIGN");
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
            text.append(strictConversionText(arg));
        }
        return text.toString();
    }

    /**
     * The expression as a strictness message renders it: live prints the call from its analysed plan,
     * so a column reference comes out qualified with its source relation ({@code TO_VARCHAR(FK.F)},
     * {@code CAST(STT.SO AS …)}) — see {@link StrictMessagePrinter}.
     */
    public String strictText(final Expression expr) {
        return expr.accept(new StrictMessagePrinter(this));
    }

    /**
     * {@link #strictText} in the plan-shaped mode — the aggregate, cast and scalar rewrites live's plan
     * applies before it names an operand — for the sentences that re-print an operand from the plan,
     * such as a conversion refusal's parameter.
     */
    public String strictPlanText(final Expression expr) {
        return expr.accept(new StrictMessagePrinter(this, StrictPrintMode.PLAN));
    }

    /**
     * An arity sentence's echo: the refused call under the name it was written with, its arguments
     * re-printed from the plan as {@link #strictPlanText} prints them —
     * {@code UPPER(CAST('2020-01-01' AS DATE), 1)} for {@code UPPER(TO_DATE('2020-01-01'), 1)} and
     * {@code TO_DATE(DATE_ADDDAYSTODATE(1, FT.D), 'YYYY-MM-DD', 1)} for a DATEADD beside a format.
     */
    public String strictPlanCallText(final FunctionCallExpression call) {
        return new StrictMessagePrinter(this, StrictPrintMode.PLAN).calledAsWritten(call);
    }

    /**
     * {@link #strictText} as an invalid-type sentence quotes an operand: its conversions spelled as the
     * functions the plan made of them, {@code TO_VECTOR(ARRAY_CONSTRUCT(1, 2, 3))} and
     * {@code FIXED_TO_FIXED(1 AS NUMBER(10,2)[UNKNOWN])} — see {@link StrictPrintMode#CONVERSION}.
     */
    public String strictConversionText(final Expression expr) {
        return expr.accept(new StrictMessagePrinter(this, StrictPrintMode.CONVERSION));
    }

    /**
     * {@link #strictText} for one of the two calls a NESTING message brackets: a plain AVG at that
     * root is named by its SUM half rather than its whole definition — see
     * {@link StrictMessagePrinter#averageSumHalf}.
     */
    public String strictAggregateText(final Expression expr) {
        final StrictMessagePrinter printer = new StrictMessagePrinter(this, StrictPrintMode.PLAN);
        final String sumHalf = printer.averageSumHalf(expr);
        return sumHalf != null ? sumHalf : expr.accept(printer);
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
        if (outputScopeNames != null && outputScopeNames.contains(columnName.toUpperCase())) {
            // The expression is printed as it resolves in the query's OUTPUT scope, where a bare name
            // is the projected column itself and has no relation to name. Only a reference WRITTEN
            // bare reaches here, so a qualifier the user typed still survives.
            return null;
        }
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

    /**
     * The relation name as a message spells it, or null for a synthetic (unprintable) name: a written
     * alias or a table upper-cased, a quoted alias exactly as quoted ({@code "sub"}), and an unaliased
     * derived table by the account's own quoted moniker, {@code "values"}.
     */
    private static String printableRelationName(final String name) {
        if (name == null || name.isEmpty()
                || name.equalsIgnoreCase("joined") || name.equalsIgnoreCase("DUMMY")) {
            return null;
        }
        if ("values".equals(name)) {
            return "\"values\"";
        }
        if (name.length() > 1 && name.startsWith("\"") && name.endsWith("\"")) {
            return name;
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
            case "APPROX_PERCENTILE":
                // Refused in its accumulator's name, like the other approximate aggregate.
                return "APPROX_PERCENTILE_ACCUMULATE";
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

    /**
     * The arity refusal carries live's position prefix anchored on the call itself
     * ({@code error line 1 at position 7}); without a resolvable origin the detail stands alone,
     * keeping older call paths readable.
     */
    /**
     * The two declared-arity sentences an aggregate call earns, echoing the call as the plan spells
     * it: too FEW keeps live's comma before "expected", too MANY drops it (both live-verified).
     */
    private static final Set<String> ORDERED_VALUE_AGGREGATES =
        Set.of("PERCENTILE_CONT", "PERCENTILE_DISC");

    private void rejectAggregateArity(final AggregateFunction aggregate,
                                      final FunctionCallExpression call) {
        // The ordered-value pair takes its VALUE through WITHIN GROUP, outside the parentheses, so
        // the written list is judged against the WRITTEN arity: PERCENTILE_CONT() is "expected 1,
        // got 0" (live-verified) even though the evaluator's declared count includes the ordered
        // value. Their upper bound is left alone for the same reason.
        final boolean orderedValue =
            ORDERED_VALUE_AGGREGATES.contains(call.getFunctionName().toUpperCase());
        final int minimum = orderedValue ? 1 : aggregate.getMinArgCount();
        final int argCount = call.getArguments().size();
        if (argCount < minimum) {
            throw arityMismatch("not enough arguments for function ["
                + strictText(call) + "], expected " + minimum + ", got " + argCount, call);
        }
        if (!orderedValue && argCount > aggregate.getMaxArgCount()) {
            throw arityMismatch("too many arguments for function ["
                + strictText(call) + "] expected "
                + aggregate.getMaxArgCount() + ", got " + argCount, call);
        }
    }

    /**
     * The call with each star written BESIDE other arguments spliced into the list in place, as the
     * columns it names: every relation in scope, or the one a qualifier names, minus its EXCLUDE and
     * outside its ILIKE. Live expands it before any arity rule, echo or evaluation sees the call, so
     * {@code HASH(a, t.*)} is {@code HASH(a, a, b, c)} and {@code MOD(a, t.*)} is refused as
     * {@code MOD(T.A, T.A, T.B, T.C)}. The call itself when it holds no such star.
     */
    FunctionCallExpression splicedStarArguments(final FunctionCallExpression call) {
        boolean spliceable = false;
        for (final Expression arg : call.getArguments()) {
            if (arg instanceof ColumnReferenceExpression && ((ColumnReferenceExpression) arg).getStarArgument() != null) {
                spliceable = true;
                break;
            }
        }
        if (!spliceable) {
            return call;
        }
        final boolean qualified = multiTableAllTables != null && multiTableAllTables.size() > 1;
        final List<Expression> spliced = new ArrayList<>();
        for (final Expression arg : call.getArguments()) {
            final StarArgument star = arg instanceof ColumnReferenceExpression
                ? ((ColumnReferenceExpression) arg).getStarArgument() : null;
            if (star == null) {
                spliced.add(arg);
                continue;
            }
            spliced.addAll(star.expandReferences(table, multiTableAliasToTable, multiTableAllTables, qualified));
        }
        return call.withArguments(spliced);
    }

    /**
     * The star's expanded argument list as live echoes it: every column of every relation in scope —
     * or of the ONE relation a qualified star names — TABLE-qualified and upper-cased, minus the
     * star's EXCLUDE columns and outside its ILIKE. The expansion itself refuses a qualifier naming
     * no relation, an EXCLUDE naming no column and a column excluded twice, in the account's words.
     */
    private List<String> starExpandedArgumentEcho(final FunctionCallExpression call) {
        final List<String> expanded = new ArrayList<>();
        for (final String text : StarArgument.of(call).expand(table, multiTableAliasToTable,
                multiTableAllTables, true)) {
            expanded.add(text.toUpperCase());
        }
        return expanded;
    }

    /**
     * The too-many sentence a star call earns once expanded, echoing the expanded QUALIFIED list —
     * nothing is thrown when the aggregate takes any width.
     */
    private void rejectStarExpandedArity(final AggregateFunction aggregate, final String funcName,
                                         final List<String> expanded, final boolean bareStar,
                                         final FunctionCallExpression positioned) {
        final StringBuilder echoed = new StringBuilder(funcName).append('(');
        for (int i = 0; i < expanded.size(); i++) {
            if (i > 0) {
                echoed.append(", ");
            }
            echoed.append(expanded.get(i));
        }
        echoed.append(')');
        // A FILTERED star that expands to NOTHING is the too-few sentence over the empty list
        // (live-verified: "not enough arguments for function [COUNT()], expected 1, got 0"). The bare
        // star is never judged so: COUNT(*) counts rows, over a FROM-less row or a table function
        // whose layout carries no columns as well as over a table.
        if (!bareStar && expanded.size() < aggregate.getMinArgCount()) {
            throw arityMismatch("not enough arguments for function [" + echoed + "], expected "
                + aggregate.getMinArgCount() + ", got " + expanded.size(), positioned);
        }
        if (aggregate.getMaxArgCount() < 0 || expanded.size() <= aggregate.getMaxArgCount()) {
            return;
        }
        throw arityMismatch("too many arguments for function [" + echoed + "] expected "
            + aggregate.getMaxArgCount() + ", got " + expanded.size(), positioned);
    }

    private RuntimeException arityMismatch(final String detail, final FunctionCallExpression expr) {
        final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
        return new RuntimeException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
            : detail);
    }

    /**
     * A too-many-arguments sentence: the call echoed from the plan against its declared maximum.
     * COLLATE is echoed the way live echoes it — a call of three by its first two arguments against 2,
     * {@code [COLLATE('a', 'en-ci')] expected 2, got 3}, and a call of four or more whole against 3
     * (live-verified).
     *
     * @param funcName the call's name, upper-cased
     * @param expr     the call
     * @param maximum  the function's declared maximum
     * @param count    how many arguments the call carries
     * @return the sentence
     */
    private String tooManyArguments(final String funcName, final FunctionCallExpression expr, final int maximum,
                                    final int count) {
        if ("COLLATE".equals(funcName)) {
            if (count == 3) {
                final FunctionCallExpression firstTwo = new FunctionCallExpression(expr.getFunctionName(),
                    new ArrayList<Expression>(expr.getArguments().subList(0, 2)));
                return "too many arguments for function [" + strictPlanCallText(firstTwo) + "] expected 2, got 3";
            }
            return "too many arguments for function [" + strictPlanCallText(expr) + "] expected 3, got " + count;
        }
        return "too many arguments for function [" + strictPlanCallText(expr) + "] expected " + maximum
            + ", got " + count;
    }

    /**
     * The refusal for a call whose arguments have no common ordering — {@code GREATEST(b, s)} and its
     * kin. The account names the OPERAND and both DECLARED types ("Can not convert parameter 'T.S' of
     * type [VARCHAR(10)] into expected type [BINARY(4)]"), which is a sentence only the expression TREE
     * can spell, so this re-asks the STATIC channel for it. That channel already answers correctly —
     * over an empty table, where nothing is evaluated, the refusal was always the account's own — and
     * what went wrong was purely the ORDER: with rows to project, the value channel ran first and
     * failed on a Comparable pairing, so a Java class name reached the user and, worse, the view path
     * read the crash as data-dependent and created a view with no columns.
     *
     * <p>When the static channel has nothing to say (a pairing it does not model), the argument-type
     * list stands in. Either way the result is a marked COMPILATION error, so the view and CTAS paths
     * refuse instead of swallowing it.
     */
    private RuntimeException unorderableArguments(final FunctionCallExpression expr,
                                                  final String funcName) {
        try {
            typeInferencer.infer(expr);
        } catch (final RuntimeException refused) {
            if (SqlCompilationError.isCompilationError(refused.getMessage())) {
                return refused;
            }
        }
        return new RuntimeException(SqlCompilationError.of(
            "Invalid argument types for function '" + funcName + "': ("
                + strictArgTypeList(expr.getArguments()) + ")"));
    }

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
    /**
     * The cast-source refusals that need no row — a FILE, a GEO or a STRUCTURED source, a TRY_CAST's
     * own rules, a conversion-matrix pair the target cannot take — applied wherever a cast's type is
     * asked for, so the refusal fires inside-out and ahead of any rule about the expression AROUND
     * the cast: live refuses SUM(a::DATE) over a NUMBER with the cast's sentence, not SUM's.
     */
    void rejectCastSourceStatically(final CastExpression expr) {
        rejectFileCastSource(expr);
        rejectGeoCastSource(expr);
        rejectStructuredTextCastSource(expr);
        rejectNonStringTryCastSource(expr);
        rejectUncastableSource(expr);
    }

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
        // A VECTOR joins the structured kinds here: it has no text conversion either, and live gives
        // it the SAME sentence, naming 'TO_VARCHAR' whatever string spelling the cast was written in.
        if (!StructuredTypes.isStructured(typeInferencer.inferSemiStructured(expr.getExpression()))
                && vectorArgumentType(expr.getExpression()) == null) {
            return;
        }
        final String source = strictConversionText(expr.getExpression());
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
     * <p>A chain is flattened before it is reported, as live reports it: {@code 'x' || a || 'y'} lists
     * three types, anchored on the last operator, which is the outermost node of the parsed chain.
     */
    private void rejectSemiStructuredConcatOperand(final BinaryOperationExpression expr) {
        if (expr.getOperator() != BinaryOperator.CONCAT) {
            return;
        }
        final List<Expression> operands = new ArrayList<>();
        collectConcatOperands(expr, operands);
        for (final Expression operand : operands) {
            if (typeInferencer.inferSemiStructured(operand) != null
                    || geoArgumentType(operand) != null
                    || vectorArgumentType(operand) != null) {
                // Prefixed and anchored on the OPERATOR, like every other argument-type refusal:
                // without the compile-time marker a CREATE VIEW over such a body was ACCEPTED, since
                // the body validation tells a body that will not compile from one that merely failed
                // while producing rows by exactly that prefix.
                final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
                final String detail = "Invalid argument types for function '||': ("
                    + strictArgTypeList(operands) + ")";
                throw new RuntimeException(at != null
                    ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
                    : SqlCompilationError.of(detail));
            }
        }
    }

    /** A {@code ||} chain's operands in written order, through every nested concatenation. */
    private static void collectConcatOperands(final Expression expr, final List<Expression> into) {
        if (expr instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) expr).getOperator() == BinaryOperator.CONCAT) {
            collectConcatOperands(((BinaryOperationExpression) expr).getLeft(), into);
            collectConcatOperands(((BinaryOperationExpression) expr).getRight(), into);
            return;
        }
        into.add(expr);
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
     * <p>Both signs are covered by {@link #rejectUnaryOperandFamilies}, which reports them as
     * 'NEGATE' and 'UNARY PLUS'.
     */
    /**
     * An operand family the operator does not take at all — BOOLEAN or BINARY arithmetic, a logical
     * operator over anything but a boolean, a number or a text. Refused where the DECLARED types are
     * known, so the statement fails over a table with no rows exactly as it does on the account.
     *
     * @param expr the operation
     */
    private void rejectOperandFamilies(final BinaryOperationExpression expr) {
        final String refusal = BinaryOperationTypes.refusalFor(expr.getOperator(),
            typeInferencer.infer(expr.getLeft()), typeInferencer.infer(expr.getRight()));
        if (refusal == null) {
            return;
        }
        if (expr.getOperator() == BinaryOperator.NOT_LIKE || expr.getOperator() == BinaryOperator.NOT_ILIKE) {
            // A NOT spelling points NOWHERE on the account — line 0, position -1 — where the plain
            // form points at its keyword.
            throw new RuntimeException(SqlCompilationError.at(0, -1, refusal));
        }
        final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
        throw new RuntimeException(at != null
            ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), refusal)
            : SqlCompilationError.of(refusal));
    }

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

    /**
     * A tuple {@code IN} right-hand list must be parenthesized ROWS of the left side's width. The
     * FLAT spelling {@code (a, b) IN (1, 2)} is a type error naming the ROW and every scalar —
     * live, "Invalid argument types for function 'IN': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0),
     * NUMBER(1,0))" — and a row of the wrong WIDTH is a conversion error naming both ROW types —
     * live, "Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), NUMBER(1,0),
     * NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]".
     */
    public void rejectInvalidTupleInShape(final TupleInExpression expr) {
        if (expr.hasFlatList()) {
            final StringBuilder sb = new StringBuilder("Invalid argument types for function 'IN': (");
            sb.append(rowTypeText(expr.getValues()));
            for (final Expression member : expr.getFlatListValues()) {
                sb.append(", ").append(strictArgTypeText(member));
            }
            throw new RuntimeException(sb.append(')').toString());
        }
        if (!expr.hasTupleRows()) {
            return;
        }
        for (final List<Expression> row : expr.getTupleRows()) {
            if (row.size() != expr.getValues().size()) {
                // The members are named from the plan too — live writes ROW(EB.I, EB.N, EB.G) for a
                // row of columns, where a raw print left them bare.
                final StringBuilder values = new StringBuilder("ROW(");
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        values.append(", ");
                    }
                    values.append(strictText(row.get(i)));
                }
                values.append(')');
                // A COMPILATION error live, prefix and all — without it a view over the shape was
                // created with null columns rather than refused.
                throw new RuntimeException(SqlCompilationError.of("Can not convert parameter '" + values
                    + "' of type [" + rowTypeText(row) + "] into expected type ["
                    + rowTypeText(expr.getValues()) + "]"));
            }
        }
    }

    /** {@code ROW(<type>, <type>, ...)} over the members' strict argument types. */
    private String rowTypeText(final List<Expression> members) {
        final StringBuilder sb = new StringBuilder("ROW(");
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(strictArgTypeText(members.get(i)));
        }
        return sb.append(')').toString();
    }

    /**
     * POSITION's IN form with more than one value on a side. The parser takes a one-value form apart
     * into needle and haystack and leaves this one as the membership test it was written as; live types
     * each side, a ROW where it holds several values, and refuses the pair at the call:
     *
     * <pre>
     *   POSITION(('b', 'c') IN (SELECT 'abc', 'x'))   Invalid argument types for function 'POSITION':
     *                                                 (ROW(VARCHAR(1), VARCHAR(1)), ROW(VARCHAR(3), VARCHAR(1)))
     *   POSITION('b' IN ('abc', 'x'))                 … (VARCHAR(1), ROW(VARCHAR(3), VARCHAR(1)))
     *   POSITION(('b', 'c') IN (SELECT 'abc'))        … (ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(3))
     * </pre>
     *
     * <p>A side whose type does not resolve is left to the rules that follow.
     */
    private void rejectRowPositionOperands(final FunctionCallExpression call) {
        if (call.getNameExpression() != null || call.getArguments().size() != 1
                || !"POSITION".equalsIgnoreCase(call.getFunctionName())) {
            return;
        }
        final Expression test = call.getArguments().get(0);
        final List<Expression> needle;
        final String haystack;
        if (test instanceof InExpression && !((InExpression) test).isNot()) {
            final InExpression in = (InExpression) test;
            needle = Collections.singletonList(in.getValue());
            haystack = in.hasSubquery() ? subqueryRowTypeText(in.getSubquery()) : memberTypeText(in.getValues());
        } else if (test instanceof TupleInExpression && !((TupleInExpression) test).isNot()) {
            final TupleInExpression tuple = (TupleInExpression) test;
            needle = tuple.getValues();
            if (tuple.hasSubquery()) {
                haystack = subqueryRowTypeText(tuple.getSubquery());
            } else if (tuple.hasFlatList()) {
                haystack = memberTypeText(tuple.getFlatListValues());
            } else {
                haystack = tuple.getTupleRows().size() == 1 ? memberTypeText(tuple.getTupleRows().get(0)) : null;
            }
        } else {
            return;
        }
        if (haystack == null || (needle.size() == 1 && !haystack.startsWith("ROW("))) {
            return;
        }
        final String needleText = memberTypeText(needle);
        if (needleText == null) {
            return;
        }
        throw new RuntimeException(argumentTypeRefusal("Invalid argument types for function 'POSITION': ("
            + needleText + ", " + haystack + ")", call));
    }

    /**
     * The argument type of one member, or the ROW of several — null when a member's type does not
     * resolve, the bare word NULL excepted, which is typed NULL.
     */
    private String memberTypeText(final List<Expression> members) {
        if (members == null || members.isEmpty()) {
            return null;
        }
        for (final Expression member : members) {
            final boolean untypedNull = member instanceof LiteralExpression
                && ((LiteralExpression) member).getType() == LiteralType.NULL;
            if (!untypedNull && typeInferencer.infer(member) == null) {
                return null;
            }
        }
        return members.size() == 1 ? strictArgTypeText(members.get(0)) : rowTypeText(members);
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
     * A sign over a value that has no numeric reading is refused while the statement COMPILES, at the
     * operator's own position, and the two signs report themselves under Snowflake's names — 'NEGATE'
     * and 'UNARY PLUS', never '-' or '+'. Live-verified over every family:
     *
     * <pre>
     *   SELECT -TRUE            error line 1 at position 7   Invalid argument types for function 'NEGATE': (BOOLEAN)
     *   SELECT 1, +TRUE         error line 1 at position 10  … 'UNARY PLUS': (BOOLEAN)   — the sign's place, not the operand's
     *   -d  -t  -ts  -bn  -b5   (DATE)  (TIME(9))  (TIMESTAMP_NTZ(9))  (BINARY(8388608))  (BINARY(5))
     *   -o  -a  -sm  -g  -f     (OBJECT)  (ARRAY)  the whole MAP type  (GEOGRAPHY)  (FILE)
     *   +IFF(TRUE, d, d)        (DATE) — the DECLARED type, read through a conditional
     * </pre>
     *
     * <p>A NUMBER keeps its type, a text and a VARIANT convert to a FLOAT, NULL is NULL, and a text
     * that reads as no number is the row-time sentence. Logical NOT and EXISTS are left alone —
     * neither takes a numeric operand.
     */
    private void rejectUnaryOperandFamilies(final UnaryOperationExpression expr) {
        final String reported;
        if (expr.getOperator() == UnaryOperator.NEGATE) {
            reported = "NEGATE";
        } else if (expr.getOperator() == UnaryOperator.PLUS) {
            reported = "UNARY PLUS";
        } else {
            return;
        }
        final Expression operand = expr.getOperand();
        final DataType declared = typeInferencer.infer(operand);
        if (typeInferencer.inferSemiStructured(operand) == null
                && fileArgumentType(operand) == null
                && geoArgumentType(operand) == null
                && !(declared instanceof BooleanType)
                && !(declared instanceof DateTimeType)
                && !(declared instanceof BinaryType)) {
            return;
        }
        throw new RuntimeException(positionedAt(expr.getPosition(),
            "Invalid argument types for function '" + reported + "': (" + strictArgTypeText(operand) + ")"));
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
            throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + semiStructuredTypeName(inferred)
                + "] and [NUMBER(9,0)]"));
        }
        final DataType file = fileArgumentType(expr);
        if (file != null) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + semiStructuredTypeName(file)
                + "] and [NUMBER(9,0)]"));
        }
        // A GEOSPATIAL value reaches the same sentence, and only for the percentiles: live,
        // PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY g) and the PERCENTILE_DISC spelling are
        // "incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]" while LISTAGG(s, ',') WITHIN GROUP
        // (ORDER BY g) and ARRAY_AGG(s) WITHIN GROUP (ORDER BY g) both RETURN a value — those two
        // order by the key without accumulating it. Only the percentile call sites reach here.
        final DataType geo = geoArgumentType(expr);
        if (geo != null) {
            throw new RuntimeException(SqlCompilationError.of("incompatible types: [" + semiStructuredTypeName(geo)
                + "] and [NUMBER(9,0)]"));
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

    private void rejectNonVectorArgument(final String funcName, final List<Expression> args,
                                         final Expression call) {
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
            rejectIllegalTruncationDimension(funcName, args, first, call);
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
     * vector. A whole-number literal is accepted — {@code 2}, {@code 2.0}, {@code (2)} — and a BOOLEAN or
     * a DATE is no dimension at all: "Invalid argument types for function 'VECTOR_TRUNC':
     * (VECTOR(FLOAT, 3), BOOLEAN)", positioned at the call. Anything else is "argument … needs to be
     * constant", and the sentence numbers and names the argument as the plan holds it:
     *
     * <pre>
     *   VECTOR_TRUNC(v, n)           argument 2 … found 'TRUNC_ROWS.N'         a value of the row
     *   VECTOR_TRUNC(v, n + 1)       argument 2 … found 'TRUNC_ROWS.N + 1'
     *   VECTOR_TRUNC(v, 1 + 1)       argument 1 … found '1 + 1'                a constant expression
     *   VECTOR_TRUNC(v, 2.5)         argument 1 … found 'CAST(2.5 AS NUMBER(9,0))'
     *   VECTOR_TRUNC(v, '2')         argument 1 … found 'TO_NUMBER('2', 9, 0)'
     *   VECTOR_TRUNC(v, NULL)        argument 1 … found 'SYSTEM$NULL_TO_FIXED(null)'
     * </pre>
     *
     * <p>So a constant is named as the whole number the plan converts it to, and an expression over a
     * column as it stands. {@code VECTOR_TRUNC(<VECTOR(FLOAT,3)>, 9)} is "Requested truncation
     * dimension 9 for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector
     * (3)."
     */
    private void rejectIllegalTruncationDimension(final String funcName, final List<Expression> args,
                                                  final VectorType source, final Expression call) {
        if (args.size() < 2) {
            return;
        }
        final Expression dimension = args.get(1);
        final DataType dimensionType = typeInferencer.infer(dimension);
        if (dimensionType instanceof BooleanType || (dimensionType instanceof DateTimeType
                && "DATE".equalsIgnoreCase(dimensionType.getName()))) {
            throw new RuntimeException(argumentTypeRefusal("Invalid argument types for function '" + funcName
                + "': (" + strictArgTypeList(args) + ")",
                call instanceof FunctionCallExpression ? (FunctionCallExpression) call : null));
        }
        final Long requested = constantDimension(dimension);
        if (requested == null) {
            final boolean readsRow = readsColumn(dimension);
            final String asConstant = readsRow ? null
                : new StrictMessagePrinter(this, StrictPrintMode.PLAN).asWholeNumber(dimension);
            throw new RuntimeException(SqlCompilationError.of("argument " + (readsRow ? 2 : 1)
                + " to function " + funcName + " needs to be constant, found '"
                + (asConstant != null ? asConstant : strictPlanText(dimension)) + "'"));
        }
        if (requested.longValue() == VectorTrunc.ROW_FAILING_DIMENSION) {
            // The one negative the account compiles — each row then fails in VectorTrunc.
            return;
        }
        if (requested.longValue() < 0) {
            throw new RuntimeException("Invalid vector dimension '" + requested + "'.");
        }
        if (source != null && requested.longValue() > source.getDimension()) {
            // Positioned at the dimension itself, the literal inside any brackets around it.
            final SourcePosition at = dimension instanceof LiteralExpression
                ? ExpressionSource.resolve(((LiteralExpression) dimension).getPosition()) : null;
            final String detail = "Requested truncation dimension " + requested + " for " + funcName
                + " should be less than or equal to the dimension of the provided vector ("
                + source.getDimension() + ").";
            throw new RuntimeException(at != null
                ? SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail)
                : SqlCompilationError.of(detail));
        }
    }

    /** Whether an expression reads a column anywhere, which makes it a value of the row, not a constant. */
    private static boolean readsColumn(final Expression expression) {
        if (expression instanceof ColumnReferenceExpression) {
            return true;
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            return readsColumn(binary.getLeft()) || readsColumn(binary.getRight());
        }
        if (expression instanceof UnaryOperationExpression) {
            return readsColumn(((UnaryOperationExpression) expression).getOperand());
        }
        if (expression instanceof CastExpression) {
            return readsColumn(((CastExpression) expression).getExpression());
        }
        if (expression instanceof FunctionCallExpression) {
            for (final Expression arg : ((FunctionCallExpression) expression).getArguments()) {
                if (readsColumn(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A literal whole-number dimension, or null when the expression is not one. A minus written before
     * the number belongs to it, as everywhere in the plan: {@code -1}, {@code -(1)} and {@code -1.0} are
     * the dimension -1.
     */
    private static Long constantDimension(final Expression expr) {
        if (expr instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE) {
            final Long magnitude = constantDimension(((UnaryOperationExpression) expr).getOperand());
            return magnitude == null ? null : Long.valueOf(-magnitude.longValue());
        }
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
        // A SCALAR target is judged by the conversion matrix the TRY_TO_ family uses (see
        // rejectNonStringTryToSource): a pair the plain cast cannot carry is the conversion sentence
        // ("invalid type [TRY_CAST(TT.D)] for parameter 'TO_TIME'", the target absent), an identity
        // pair PASSES (TRY_CAST(d AS DATE), (bn AS BINARY), (f AS FLOAT), (TRUE AS BOOLEAN) answer the
        // value), and a castable pair keeps TRY_CAST's own sentence with the nominal target — NUMBER(2,0)
        // for a BOOLEAN whatever the cast spells, TRY_CAST(TRUE AS NUMBER(5,1)) included (live-verified).
        final String kind = castTargetKind(expr.getTargetType());
        if (kind != null && isConversionMatrixFamily(source)) {
            final String verdict = tryToVerdict(source, kind);
            if (TRY_TO_PASS.equals(verdict)) {
                return;
            }
            if (TRY_TO_CONVERSION.equals(verdict)) {
                throw new RuntimeException(SqlCompilationError.of("invalid type [TRY_CAST("
                    + conversionSourceEcho(expr.getExpression()) + ")] for parameter '"
                    + castConversionName(expr.getTargetType()) + "'"));
            }
            final String nominal = "NUMBER".equals(kind) && source instanceof BooleanType
                ? "NUMBER(2,0)" : castTargetTypeText(expr.getTargetType());
            throw new RuntimeException(SqlCompilationError.of(
                "Function TRY_CAST cannot be used with arguments of types "
                + strictArgTypeText(expr.getExpression()) + " and " + nominal));
        }
        if (source instanceof NumericType || source instanceof BooleanType
                || source instanceof DateTimeType || source instanceof BinaryType
                || source instanceof VariantType || source instanceof ObjectType
                || source instanceof ArrayType) {
            throw new RuntimeException(SqlCompilationError.of(
                "Function TRY_CAST cannot be used with arguments of types "
                + strictArgTypeText(expr.getExpression()) + " and " + castTargetTypeText(expr.getTargetType())));
        }
    }

    /**
     * A plain CAST over a pair the conversion matrix cannot carry is refused while the statement
     * COMPILES, with the conversion sentence — "invalid type [CAST(TT.D AS TIME(9))] for parameter
     * 'TO_TIME'": the target rendered with its default parameters (NUMBER(38,0), TIME(9),
     * TIMESTAMP_NTZ(9), TIMESTAMP_LTZ(9), BINARY(67108864), and FLOAT for every approximate spelling —
     * {@code TRUE::DOUBLE} echoes "CAST(TRUE AS FLOAT)"), a declared one kept (TIME(3), NUMBER(5,2),
     * TIMESTAMP_TZ(3)), a FLOAT cast among the sources spelled TO_DOUBLE, and the parameter the
     * conversion the target routes through. Live-verified across the families: a BOOLEAN into DATE /
     * TIME / BINARY / every TIMESTAMP flavour / FLOAT, a NUMBER into DATE / TIME / BINARY, a DATE into
     * NUMBER / FLOAT / BOOLEAN / TIME, a TIME into DATE / NUMBER, a TIMESTAMP into BOOLEAN, a FLOAT
     * into DATE / TIME / BOOLEAN / TIMESTAMP, a BINARY into NUMBER / BOOLEAN / DATE, an OBJECT or an
     * ARRAY into NUMBER / DATE. The castable pairs convert as they always did ({@code TRUE::NUMBER}
     * is 1, {@code d::TIMESTAMP} midnight), a VARIANT and a text convert at row time, and the TRY
     * spelling is judged by {@link #rejectNonStringTryCastSource}, which owns TRY_CAST's own sentence.
     */
    private void rejectUncastableSource(final CastExpression expr) {
        if (expr.isTryMode()) {
            return;
        }
        final String kind = castTargetKind(expr.getTargetType());
        if (kind == null) {
            return;
        }
        final DataType source = typeInferencer.infer(expr.getExpression());
        if (source instanceof UuidType && !"BINARY".equals(kind)) {
            // A UUID reaches the text targets; only the binary one is refused, and it is refused as the
            // conversion's own invalid type (live-verified).
            return;
        }
        if (!(source instanceof UuidType)
                && (!isConversionMatrixFamily(source) || !TRY_TO_CONVERSION.equals(tryToVerdict(source, kind)))) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("invalid type [CAST("
            + conversionSourceEcho(expr.getExpression()) + " AS " + castTargetTypeText(expr.getTargetType())
            + ")] for parameter '" + castConversionName(expr.getTargetType()) + "'"));
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
     * The TRY_TO_&lt;TYPE&gt; functions are TRY_CAST under the hood, and a source that is not a string
     * is judged by the PAIR, with three answers (live-verified across every family):
     *
     * <ul>
     *   <li>a pair the plain CAST carries keeps TRY_CAST's own sentence — "Function TRY_CAST cannot be
     *       used with arguments of types &lt;source&gt; and &lt;nominal target&gt;";</li>
     *   <li>a pair the plain CAST cannot carry takes the conversion sentence — "invalid type [&lt;the
     *       call, replayed&gt;] for parameter '&lt;the base conversion&gt;'", every argument echoed;</li>
     *   <li>four identity pairs PASS — DATE, FLOAT, BINARY and BOOLEAN into their own type — while a
     *       NUMBER, a TIME and a TIMESTAMP into their own type are refused like any castable pair.</li>
     * </ul>
     *
     * <pre>
     *   source \ target   NUMBER   DOUBLE   BOOLEAN  DATE   TIME   TIMESTAMP  BINARY
     *   NUMBER            cast     cast     cast     conv   conv   cast       conv
     *   FLOAT             cast     pass     conv     conv   conv   conv       conv
     *   BOOLEAN           cast     conv     pass     conv   conv   conv       conv
     *   DATE              conv     conv     conv     pass   conv   cast       conv
     *   TIME              conv     conv     conv     conv   cast   conv       conv
     *   TIMESTAMP_*       conv     conv     conv     cast   cast   cast       conv
     *   BINARY            conv     conv     conv     conv   conv   conv       pass
     *   VARIANT, NULL     cast     cast     cast     cast   cast   cast       cast
     *   OBJECT, ARRAY     conv     conv     conv     conv   conv   conv       conv
     * </pre>
     *
     * <p>The nominal target: NUMBER(38,0) — NUMBER(2,0) from a BOOLEAN whatever the call declares, else
     * the declared (p,s) when the call spells one; FLOAT; BOOLEAN; DATE; TIME(9); BINARY(67108864);
     * and TIMESTAMP_X(p) where p is a NUMBER source's scale plus the declared scale argument —
     * {@code TRY_TO_TIMESTAMP(123)} is (0), over a NUMBER(5,2) it is (2), {@code (123, 3)} is (3),
     * {@code (1.5, 3)} is (4) — and 9 for every other source. A NUMBER source with a FORMAT
     * argument is a third sentence: "argument needs to be a string: '&lt;the source, replayed&gt;'".
     * A VARCHAR source converts, and an undetermined expression passes.
     */
    private void rejectNonStringTryToSource(final String funcName, final List<Expression> args) {
        final String target = TRY_TO_TARGET_TYPES.get(funcName);
        if (target == null || args.isEmpty()) {
            return;
        }
        final Expression source = args.get(0);
        final DataType inferred = typeInferencer.infer(source);
        final boolean untypedNull = inferred == null && source instanceof LiteralExpression;
        if (!untypedNull && !(inferred instanceof NumericType) && !(inferred instanceof BooleanType)
                && !(inferred instanceof DateTimeType) && !(inferred instanceof BinaryType)
                && !(inferred instanceof VariantType) && !(inferred instanceof ObjectType)
                && !(inferred instanceof ArrayType)) {
            return;
        }
        final String kind = tryToTargetKind(target);
        final String verdict = untypedNull || inferred instanceof VariantType
            ? TRY_TO_CAST : tryToVerdict(inferred, kind);
        if (TRY_TO_PASS.equals(verdict)) {
            return;
        }
        if (TRY_TO_CONVERSION.equals(verdict)) {
            throw new RuntimeException(SqlCompilationError.of("invalid type [" + conversionCallEcho(funcName, args)
                + "] for parameter '" + TRY_TO_CONVERSION_PARAMETERS.get(funcName) + "'"));
        }
        final boolean numericTryTo = "NUMBER".equals(kind);
        if (numericTryTo && inferred instanceof NumericType && args.size() > 1
                && typeInferencer.infer(args.get(1)) instanceof StringType) {
            throw new RuntimeException(SqlCompilationError.of(
                "argument needs to be a string: '" + strictText(source) + "'"));
        }
        // The target the sentence echoes is the DECLARED pair when the call spells one out —
        // TRY_TO_NUMBER(1000, 2, 0) names NUMBER(2,0), not the nominal NUMBER(38,0) — except a
        // BOOLEAN source, which ALWAYS echoes NUMBER(2,0), even against a declared (5,1)
        // (live-verified in both directions).
        final String declaredTarget;
        if (numericTryTo && inferred instanceof BooleanType) {
            declaredTarget = "NUMBER(2,0)";
        } else if (numericTryTo) {
            declaredTarget = declaredNumberTarget(args, target);
        } else if ("TIMESTAMP".equals(kind)) {
            declaredTarget = target.substring(0, target.indexOf('('))
                + "(" + tryToTimestampPrecision(inferred, args) + ")";
        } else {
            declaredTarget = target;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Function TRY_CAST cannot be used with arguments of types "
            + (untypedNull ? "NULL" : strictArgTypeText(source)) + " and " + declaredTarget));
    }

    /**
     * The TO_&lt;TYPE&gt; conversions over a source that is not a string, judged by the matrix their
     * TRY_TO_ twins use (see {@link #rejectNonStringTryToSource}): a pair the plain cast cannot carry
     * is refused while the statement COMPILES with the conversion sentence — "invalid type
     * [TO_DOUBLE(TRUE)] for parameter 'TO_DOUBLE'" — while a castable pair CONVERTS
     * ({@code TO_NUMBER(TRUE)} is 1, {@code TO_TIMESTAMP(d)} a midnight timestamp, {@code TO_BOOLEAN(123)}
     * TRUE) and an identity pair hands the value back. Live-verified family by family: TO_DOUBLE /
     * TO_BINARY / TO_DATE / TO_TIME / TO_TIMESTAMP / TO_TIMESTAMP_NTZ / TO_TIMESTAMP_TZ over TRUE,
     * TO_TIME / TO_DATE / TO_BINARY over 123, TO_NUMBER / TO_DECIMAL / TO_NUMERIC / TO_DOUBLE /
     * TO_BOOLEAN over a DATE, a TIME, a BINARY, a FLOAT, an OBJECT and an ARRAY.
     *
     * <p>The echo is the call with its SOURCE — a FLOAT cast spelled TO_DOUBLE, a format string kept,
     * the (precision, scale) arguments dropped: {@code TO_DECIMAL(d, 5, 2)} echoes "TO_DECIMAL(TT.D)" —
     * and the parameter is the function's OWN base name (TO_DECIMAL, TO_NUMERIC, and TO_TIMESTAMP_NTZ
     * for TO_TIMESTAMP).
     *
     * <p>A NUMERIC conversion handed a FORMAT string beside a non-string source is a different
     * sentence, whatever the source's family: "argument needs to be a string: 'TRUE'", 'TT.N',
     * 'TT.D' (live-verified for all three).
     *
     * <p>A VARIANT and an untyped NULL convert at ROW time, as they do everywhere ({@code TO_DATE(v)}
     * over a VARIANT 7 is "Failed to cast variant value 7 to DATE" on the account), and a text source
     * is the ordinary parse.
     */
    private void rejectNonStringConversionSource(final String funcName, final List<Expression> args) {
        final String kind = CONVERSION_TARGET_KINDS.get(funcName);
        if (kind == null || args.isEmpty()) {
            return;
        }
        final Expression source = args.get(0);
        final DataType inferred = typeInferencer.infer(source);
        if (!isConversionMatrixFamily(inferred)) {
            return;
        }
        if ("NUMBER".equals(kind) && args.size() > 1
                && typeInferencer.infer(args.get(1)) instanceof StringType) {
            throw new RuntimeException(SqlCompilationError.of(
                "argument needs to be a string: '" + strictText(source) + "'"));
        }
        if (TRY_TO_CONVERSION.equals(tryToVerdict(inferred, kind))) {
            throw new RuntimeException(SqlCompilationError.of("invalid type [" + conversionCallEcho(funcName, args)
                + "] for parameter '" + CONVERSION_PARAMETERS.get(funcName) + "'"));
        }
    }

    /**
     * The FORMAT argument of a temporal conversion over a text source must be a string (live-verified,
     * one sentence for the family): "Format argument for function 'TO_DATE' needs to be a string" for
     * a NUMBER, a BOOLEAN, a DATE or a VARIANT there — literal or column — and for an untyped NULL
     * under TO_DATE / TO_TIME and their TRY_ twins, which name the base conversion (TRY_TO_DATE says
     * TO_DATE). The TIMESTAMP spellings name their flavour (TO_TIMESTAMP says TO_TIMESTAMP_NTZ), take
     * a NUMERIC scale beside a NUMERIC source and so are judged over a text source only, and answer
     * NULL to an untyped NULL format instead of refusing it. A NULL::VARCHAR format is a NULL answer at
     * row time and passes here; a non-text source belongs to the matrix rules above, and a source the
     * static channel cannot type — a semi-structured path under a cast, as the vendor loaders write
     * {@code TO_TIMESTAMP_NTZ(src:ts::NUMBER, 3)} — is left alone, because it may well be the scale form.
     */
    private void rejectNonStringFormatArgument(final String funcName, final List<Expression> args) {
        final String base = TRY_TO_CONVERSION_PARAMETERS.containsKey(funcName)
            ? TRY_TO_CONVERSION_PARAMETERS.get(funcName) : CONVERSION_PARAMETERS.get(funcName);
        final String kind = base == null ? null : CONVERSION_TARGET_KINDS.get(base);
        // TO_DOUBLE's format over a text source is held to the same sentence (live: TO_DOUBLE('1', NULL)).
        if (args.size() < 2 || !("DATE".equals(kind) || "TIME".equals(kind) || "TIMESTAMP".equals(kind)
                || "FLOAT".equals(kind))) {
            return;
        }
        if (!(typeInferencer.infer(args.get(0)) instanceof StringType)) {
            return;
        }
        final Expression format = args.get(1);
        final DataType formatType = typeInferencer.infer(format);
        final boolean untypedNull = formatType == null && format instanceof LiteralExpression;
        if (untypedNull ? "TIMESTAMP".equals(kind) : formatType == null || formatType instanceof StringType) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of(
            "Format argument for function '" + base + "' needs to be a string"));
    }

    /**
     * The conversions whose FORMAT argument exists over a text source only, by the name written: the
     * DATE synonym is not one of them, since live answers {@code DATE(d, 'YYYY-MM-DD')} over a DATE
     * column, while the TIME synonym is.
     */
    private static final Set<String> FORMAT_OVER_TEXT_ONLY = new HashSet<>(Arrays.asList(
        "TO_DATE", "TO_TIME", "TIME", "TO_TIMESTAMP", "TO_TIMESTAMP_NTZ", "TO_TIMESTAMP_LTZ", "TO_TIMESTAMP_TZ"));

    /**
     * A temporal conversion's two-argument form exists for a TEXT source only. Over a DATE, a TIME, a
     * TIMESTAMP of any flavour or a VARIANT, live refuses the format at compile time as an ARITY fault,
     * positioned at the call and echoing the call as its plan prints it, a cast re-printed as its own
     * conversion:
     *
     * <pre>
     *   TO_DATE(d, 'YYYY-MM-DD')            too many arguments for function [TO_DATE(FT.D, 'YYYY-MM-DD')]
     *                                       expected 1, got 2
     *   TO_DATE('2020-01-15'::DATE, …)      … [TO_DATE(TO_DATE('2020-01-15'), 'YYYY-MM-DD')] …
     *   TO_TIME(PARSE_JSON('"10:00:00"'), 'HH24:MI:SS')   the same, a VARIANT source
     *   TIME(tm, 'HH24:MI:SS')              the same - the TIME synonym is a conversion here
     *   DATE(d, 'YYYY-MM-DD')               answers
     *   TRY_TO_DATE(d, 'YYYY-MM-DD')        the arity sentence; the other TRY_ twins over a temporal or a
     *                                       VARIANT source are the TRY_CAST sentence
     * </pre>
     *
     * <p>A NULL format counts as the second argument and echoes as NULL. An empty table is refused alike.
     * Live-verified, every cell.
     */
    private void rejectFormatOverNonTextSource(final String funcName, final List<Expression> args,
                                               final Expression call) {
        if (args.size() != 2 || !(call instanceof FunctionCallExpression)) {
            return;
        }
        final DataType source = typeInferencer.infer(args.get(0));
        final boolean temporalOrVariant = source instanceof DateTimeType || source instanceof VariantType;
        // TO_DOUBLE's format exists over a text source only as well: a NUMBER beside one is the same
        // arity fault, TO_DOUBLE(1.5, '99') "too many arguments … expected 1, got 2" (live-verified).
        final boolean numericToDouble = ("TO_DOUBLE".equals(funcName) || "TRY_TO_DOUBLE".equals(funcName))
            && source instanceof NumericType;
        // TO_DECFLOAT's too, over a BOOLEAN as well: TO_DECFLOAT(TRUE, 'x') and TO_DECFLOAT(1.5, '9.9')
        // are both "too many arguments … expected 1, got 2" (live-verified).
        final boolean nonTextToDecfloat = "TO_DECFLOAT".equals(funcName)
            && (source instanceof NumericType || source instanceof BooleanType);
        final boolean refused = numericToDouble || nonTextToDecfloat
            || (FORMAT_OVER_TEXT_ONLY.contains(funcName) ? temporalOrVariant
            : "TRY_TO_DATE".equals(funcName) && source instanceof DateTimeType
                && "DATE".equalsIgnoreCase(source.getName()));
        if (!refused) {
            return;
        }
        final StringBuilder echo = new StringBuilder(funcName).append('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                echo.append(", ");
            }
            echo.append(planArgumentEcho(args.get(i)));
        }
        throw arityMismatch("too many arguments for function [" + echo.append(')')
            + "] expected 1, got 2", (FunctionCallExpression) call);
    }

    /**
     * An argument as this arity sentence's plan print spells it: a temporal cast as its own conversion —
     * {@code TO_TIMESTAMP_NTZ('2020-01-15 10:00:00')} for {@code '2020-01-15 10:00:00'::TIMESTAMP}, a
     * TIMESTAMP cast named by its flavour over a text or a VARIANT but {@code TO_TIMESTAMP(FAM.TS)} over a
     * DATE or a TIMESTAMP whatever its flavour — and
     * an untyped NULL upper-cased; anything else as an invalid-type sentence prints it, the conversion
     * still unresolved while the interval shifts and COALESCE are already the plan's —
     * {@code TO_DATE(DATE_ADDDAYSTODATE(1, FT.D), 'YYYY-MM-DD')} and {@code TO_DATE(IFNULL(FT.D, FT.D),
     * 'YYYY-MM-DD')} — a cast that changes nothing included, {@code TO_DATE(identity(FAM.D),
     * 'YYYY-MM-DD')}.
     */
    private String planArgumentEcho(final Expression argument) {
        if (argument instanceof LiteralExpression && ((LiteralExpression) argument).getType() == LiteralType.NULL) {
            return "NULL";
        }
        if (argument instanceof CastExpression && !((CastExpression) argument).isTryMode()
                && !new StrictMessagePrinter(this, StrictPrintMode.CONVERSION)
                    .printsAsIdentity((CastExpression) argument)) {
            final DataType target = typeInferencer.infer(argument);
            if (target instanceof DateTimeType) {
                final Expression operand = ((CastExpression) argument).getExpression();
                final DataType source = typeInferencer.infer(operand);
                final boolean fromDateOrTimestamp = source instanceof DateTimeType
                    && !"TIME".equalsIgnoreCase(source.getName());
                final String name = target.getName().toUpperCase(Locale.ROOT);
                final String conversion = "DATE".equals(name) ? "TO_DATE" : "TIME".equals(name) ? "TO_TIME"
                    : fromDateOrTimestamp ? "TO_TIMESTAMP"
                    : name.startsWith("TIMESTAMP_") ? "TO_" + name : "TO_TIMESTAMP_NTZ";
                return conversion + "(" + strictConversionText(operand) + ")";
            }
        }
        return strictConversionText(argument);
    }

    /**
     * Beside an exact NUMERIC source, a TIMESTAMP conversion's second argument is a SCALE - a constant
     * integer from 0 to 9 - and each other shape is refused while the statement compiles, unpositioned,
     * naming the flavour the call resolves to (TO_TIMESTAMP and TRY_TO_TIMESTAMP say TO_TIMESTAMP_NTZ):
     *
     * <pre>
     *   TO_TIMESTAMP(n, '3')      argument 2 to function TO_TIMESTAMP_NTZ needs to be an integer, found: ''3''
     *   TO_TIMESTAMP(n, NULL)     the same, found: 'null' - and '1.5', 'TRUE'
     *   TO_TIMESTAMP(n, k)        argument 2 to function TO_TIMESTAMP_NTZ needs to be constant, found 'ST.K'
     *   TO_TIMESTAMP(n, 12)       Invalid value [12] for function 'TO_TIMESTAMP_NTZ' at position 2
     * </pre>
     *
     * <p>A FLOAT source keeps the conversion sentence. The rule is asked ahead of the TRY_ twins' source
     * rule, whose TRY_CAST sentence a NUMBER source would otherwise earn first. Live-verified, every cell;
     * an empty table refuses alike.
     */
    private void rejectNonIntegerTimestampScale(final String funcName, final List<Expression> args) {
        final String base = TRY_TO_CONVERSION_PARAMETERS.containsKey(funcName)
            ? TRY_TO_CONVERSION_PARAMETERS.get(funcName) : CONVERSION_PARAMETERS.get(funcName);
        if (args.size() != 2 || base == null || !"TIMESTAMP".equals(CONVERSION_TARGET_KINDS.get(base))) {
            return;
        }
        final DataType source = typeInferencer.infer(args.get(0));
        if (!(source instanceof NumericType) || NumericType.isApproximate(source)) {
            return;
        }
        final Expression scale = args.get(1);
        final Long integer = integerLiteral(scale);
        if (integer != null) {
            if (integer.longValue() < 0 || integer.longValue() > 9) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Invalid value [" + integer + "] for function '" + base + "' at position 2"));
            }
            return;
        }
        if (!(scale instanceof LiteralExpression)) {
            throw new RuntimeException(SqlCompilationError.of("argument 2 to function " + base
                + " needs to be constant, found '" + strictText(scale) + "'"));
        }
        throw new RuntimeException(SqlCompilationError.of("argument 2 to function " + base
            + " needs to be an integer, found: '" + strictText(scale) + "'"));
    }

    /** An integer literal's value, a negated one included, or null when the argument is not one. */
    private static Long integerLiteral(final Expression argument) {
        if (argument instanceof LiteralExpression && ((LiteralExpression) argument).getType() == LiteralType.INTEGER) {
            return Long.valueOf(((Number) ((LiteralExpression) argument).getValue()).longValue());
        }
        if (argument instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) argument).getOperator() == UnaryOperator.NEGATE) {
            final Long operand = integerLiteral(((UnaryOperationExpression) argument).getOperand());
            return operand == null ? null : Long.valueOf(-operand.longValue());
        }
        return null;
    }

    /**
     * Whether the plain cast refuses outright to carry a {@code source} value into a SCALAR
     * {@code target}: the matrix's conversion verdict, the pairs {@link #rejectUncastableSource}
     * refuses while a statement compiles. A target outside the matrix (a text, a VARIANT, a container)
     * is never refused here.
     *
     * @param source the value's static type
     * @param target the type it would be cast to
     * @return true when no cast from the one to the other exists
     */
    public static boolean castMatrixRefuses(final DataType source, final DataType target) {
        if (source == null || target == null || target.getName() == null) {
            return false;
        }
        final String kind = castTargetKind(target.getName());
        return kind != null && isConversionMatrixFamily(source)
            && TRY_TO_CONVERSION.equals(tryToVerdict(source, kind));
    }

    /** Whether a source's declared family is one the conversion matrix has a row for. */
    private static boolean isConversionMatrixFamily(final DataType inferred) {
        return inferred instanceof NumericType || inferred instanceof BooleanType
            || inferred instanceof DateTimeType || inferred instanceof BinaryType
            || inferred instanceof ObjectType || inferred instanceof ArrayType;
    }

    /**
     * The conversion family a CAST target belongs to, in the matrix's vocabulary — NUMBER, FLOAT,
     * BOOLEAN, DATE, TIME, TIMESTAMP, BINARY — or null for a target outside it (a text, a VARIANT, a
     * container, a geo type), which keeps whatever rule it had.
     */
    private static String castTargetKind(final String targetType) {
        final DataType target = TypeInferencer.typeForName(targetType);
        if (target instanceof NumericType) {
            return NumericType.isApproximate(target) ? "FLOAT" : "NUMBER";
        }
        if (target instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (target instanceof BinaryType) {
            return "BINARY";
        }
        if (target instanceof DateTimeType && target.getName() != null) {
            return tryToTargetKind(target.getName().toUpperCase(Locale.ROOT));
        }
        return null;
    }

    private static final String TRY_TO_CAST = "cast";
    private static final String TRY_TO_CONVERSION = "conv";
    private static final String TRY_TO_PASS = "pass";

    /** Which family a TRY_TO_&lt;TYPE&gt; converts into, read off its nominal target. */
    private static String tryToTargetKind(final String nominalTarget) {
        if (nominalTarget.startsWith("NUMBER")) {
            return "NUMBER";
        }
        if (nominalTarget.startsWith("TIMESTAMP")) {
            return "TIMESTAMP";
        }
        if (nominalTarget.startsWith("BINARY")) {
            return "BINARY";
        }
        if (nominalTarget.startsWith("TIME")) {
            return "TIME";
        }
        return nominalTarget;   // FLOAT, BOOLEAN, DATE
    }

    /** The matrix above, one source family at a time. */
    private static String tryToVerdict(final DataType source, final String kind) {
        if (source instanceof ObjectType || source instanceof ArrayType) {
            return TRY_TO_CONVERSION;
        }
        if (source instanceof NumericType) {
            if (NumericType.isApproximate(source)) {
                return "NUMBER".equals(kind) || "DECFLOAT".equals(kind) ? TRY_TO_CAST
                    : "FLOAT".equals(kind) ? TRY_TO_PASS : TRY_TO_CONVERSION;
            }
            return "NUMBER".equals(kind) || "FLOAT".equals(kind) || "DECFLOAT".equals(kind)
                || "BOOLEAN".equals(kind) || "TIMESTAMP".equals(kind) ? TRY_TO_CAST : TRY_TO_CONVERSION;
        }
        if (source instanceof BooleanType) {
            // A BOOLEAN converts to a DECFLOAT as it does to a NUMBER: TO_DECFLOAT(TRUE) is 1 (live).
            return "NUMBER".equals(kind) || "DECFLOAT".equals(kind) ? TRY_TO_CAST
                : "BOOLEAN".equals(kind) ? TRY_TO_PASS : TRY_TO_CONVERSION;
        }
        if (source instanceof BinaryType) {
            return "BINARY".equals(kind) ? TRY_TO_PASS : TRY_TO_CONVERSION;
        }
        final String sourceName = source.getName() == null ? "" : source.getName().toUpperCase(Locale.ROOT);
        if (sourceName.equals("DATE")) {
            return "DATE".equals(kind) ? TRY_TO_PASS
                : "TIMESTAMP".equals(kind) ? TRY_TO_CAST : TRY_TO_CONVERSION;
        }
        if (sourceName.equals("TIME")) {
            return "TIME".equals(kind) ? TRY_TO_CAST : TRY_TO_CONVERSION;
        }
        // The TIMESTAMP flavours
        return "DATE".equals(kind) || "TIME".equals(kind) || "TIMESTAMP".equals(kind)
            ? TRY_TO_CAST : TRY_TO_CONVERSION;
    }

    /** The precision a TIMESTAMP nominal echoes — see the matrix's note. */
    private static int tryToTimestampPrecision(final DataType source, final List<Expression> args) {
        if (!(source instanceof NumericType) || NumericType.isApproximate(source)) {
            return 9;
        }
        int precision = Math.max(((NumericType) source).getScale(), 0);
        if (args.size() > 1 && args.get(1) instanceof LiteralExpression
                && ((LiteralExpression) args.get(1)).getType() == LiteralType.INTEGER) {
            precision += Integer.parseInt(String.valueOf(((LiteralExpression) args.get(1)).getValue()));
        }
        return Math.min(precision, 9);
    }

    /**
     * The call as the conversion sentence echoes it: the name with each argument's plan print, the
     * trailing (precision, scale) integers left out — {@code TO_DECIMAL(d, 5, 2)} echoes
     * "TO_DECIMAL(TT.D)" while a format string is kept, {@code TRY_TO_DATE(123, 'YYYY')} — and a
     * FLOAT cast spelled as the account's plan spells it there, {@code TO_DOUBLE(1.5)} for
     * {@code 1.5::FLOAT} (all live-verified).
     */
    private String conversionCallEcho(final String funcName, final List<Expression> args) {
        int echoed = args.size();
        // A TIMESTAMP conversion's scale integer is part of the echo — live prints
        // TO_TIMESTAMP(RE.F, 3) and TRY_TO_TIMESTAMP(RE.F, 3) — where a numeric conversion's
        // (precision, scale) integers are left out.
        final boolean keepsIntegers = funcName.contains("TIMESTAMP");
        while (!keepsIntegers && echoed > 1 && args.get(echoed - 1) instanceof LiteralExpression
                && ((LiteralExpression) args.get(echoed - 1)).getType() == LiteralType.INTEGER) {
            echoed--;
        }
        final StringBuilder echo = new StringBuilder(funcName).append('(');
        for (int i = 0; i < echoed; i++) {
            if (i > 0) {
                echo.append(", ");
            }
            echo.append(conversionSourceEcho(args.get(i)));
        }
        return echo.append(')').toString();
    }

    /**
     * A conversion sentence's source as the account's plan spells it, its conversions still the
     * functions they were planned as ({@link #strictConversionText}): a {@code 1.5::FLOAT} source is
     * {@code TO_DOUBLE(1.5)} — live echoes {@code CAST(TO_DOUBLE(1.5) AS DATE)},
     * {@code TRY_CAST(TO_DOUBLE(1.5))} and {@code TRY_TO_TIMESTAMP_LTZ(TO_DOUBLE(1.5))} — while
     * {@code f::FLOAT} over a FLOAT changes nothing and is {@code identity(FAM.F)}.
     */
    private String conversionSourceEcho(final Expression source) {
        if (source instanceof LiteralExpression && ((LiteralExpression) source).getType() == LiteralType.BINARY) {
            // A BINARY literal keeps its written X'..' form: invalid type [TO_DECFLOAT(X'41')].
            return "X'" + strictText(source) + "'";
        }
        return strictConversionText(source);
    }

    /**
     * The numeric target a TRY_TO_NUMBER-family sentence echoes: the (precision, scale) the call
     * DECLARES — the trailing integer literals after the source (and optional format) — or the
     * nominal pair when it declares none. A lone precision carries scale 0.
     */
    private static String declaredNumberTarget(final List<Expression> args, final String nominal) {
        Integer last = null;
        Integer secondLast = null;
        if (args.size() >= 2) {
            last = integerLiteralValue(args.get(args.size() - 1));
        }
        if (args.size() >= 3) {
            secondLast = integerLiteralValue(args.get(args.size() - 2));
        }
        if (secondLast != null && last != null) {
            return "NUMBER(" + secondLast + "," + last + ")";
        }
        if (last != null) {
            return "NUMBER(" + last + ",0)";
        }
        return nominal;
    }

    /** The int a literal argument holds, or null when it is not an integer literal. */
    private static Integer integerLiteralValue(final Expression arg) {
        if (!(arg instanceof LiteralExpression)) {
            return null;
        }
        final Object value = ((LiteralExpression) arg).getValue();
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).intValue();
        }
        return null;
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
            // Every approximate spelling is the one FLOAT: CAST(TRUE AS DOUBLE), (… AS REAL) and
            // (… AS FLOAT8) all echo "CAST(TRUE AS FLOAT)" on the account.
            case "FLOAT": case "FLOAT4": case "FLOAT8": case "DOUBLE": case "DOUBLE PRECISION": case "REAL":
                return "FLOAT";
            case "BINARY": case "VARBINARY":
                return "BINARY(67108864)";
            case "TIME":
                return "TIME(9)";
            case "TIMESTAMP": case "TIMESTAMP_NTZ": case "DATETIME": case "TIMESTAMPNTZ":
                return "TIMESTAMP_NTZ(9)";
            case "TIMESTAMP_LTZ": case "TIMESTAMPLTZ":
                return "TIMESTAMP_LTZ(9)";
            case "TIMESTAMP_TZ": case "TIMESTAMPTZ":
                return "TIMESTAMP_TZ(9)";
            default:
                return written.toUpperCase();
        }
    }

    /**
     * TO_CHAR / TO_VARCHAR take a FORMAT only for the NUMBER and temporal inputs it is defined over.
     * Over a VARCHAR the function is single-argument and a format is an arity error (live-verified:
     * {@code TO_CHAR('hello', 'YYYY-MM-DD')} errors "too many arguments for function
     * [TO_CHAR('hello', 'YYYY-MM-DD')] expected 1, got 2").
     *
     * <p>A VARIANT is the same, which is what this used to miss: {@code TO_VARCHAR(src:score,
     * '0.000')} was accepted and the format applied, where live refuses the second argument at
     * COMPILE time. Measured over every member kind and every spelling — a decimal member, a text
     * member, an OBJECT member, an ARRAY member, the whole object, and a bare PARSE_JSON — and all
     * six refuse, so it is the VARIANT-ness of the first argument that decides and not what the
     * member happens to hold. Casting first is the accepted way to format one, and
     * {@code TO_VARCHAR(src:score::NUMBER(10,2), '0.000')} still answers on both engines.
     */
    private void rejectFormatOverStringInToChar(final String funcName, final List<Expression> args,
                                                final Expression call) {
        if (!funcName.equals("TO_CHAR") && !funcName.equals("TO_VARCHAR")) {
            return;
        }
        // EXACTLY two. A third argument is past the widest overload the function has, so live reports
        // the generic "expected 2, got 3" there rather than this single-argument rule — measured, and
        // firing this branch for three arguments reported "expected 1" instead.
        if (args.size() != 2) {
            return;
        }
        final DataType first = typeInferencer.infer(args.get(0));
        if (!(first instanceof StringType) && !(first instanceof VariantType)) {
            return;
        }
        throw arityMismatch("too many arguments for function [" + strictText(call)
            + "] expected 1, got " + args.size(), (FunctionCallExpression) call);
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
     * {@code TIMESTAMP_NTZ(9)}, binary its own declared width (all live-verified shapes).
     */
    /**
     * The type name live prints when it refuses an argument, for callers outside the strict-argument
     * walk — the table-function parameter checks name their offending argument the same way.
     */
    public String argumentTypeText(final Expression arg) {
        return strictArgTypeText(arg);
    }

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
                    // A literal past the exact range is carried as a double and is a FLOAT, not a
                    // NUMBER of whatever width its printed text happens to have — 1.0E38 measured
                    // that way came out NUMBER(5,4).
                    if (value instanceof Double || value instanceof Float) {
                        return "FLOAT";
                    }
                    final String textValue = String.valueOf(value).replace("-", "");
                    final int dot = textValue.indexOf('.');
                    final int scale = dot < 0 ? 0 : textValue.length() - dot - 1;
                    return "NUMBER(" + (textValue.length() - (dot < 0 ? 0 : 1)) + "," + scale + ")";
                }
                case BOOLEAN:
                    return "BOOLEAN";
                case BINARY:
                    // Its own byte count, the empty X'' included as one (live: BINARY(1)).
                    return value instanceof BinaryValue
                        ? "BINARY(" + BinaryLiteralText.declaredWidth((BinaryValue) value) + ")" : "BINARY";
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
        // An UNSIZED binary is named at the type's 64MB maximum in argument-type sentences, a sized one
        // at its own width — live: TO_BINARY('AB','HEX') and a derived column over it print
        // "(BINARY(67108864))", a BINARY(5) column "(BINARY(5))" and COALESCE over two sized columns
        // "(BINARY(8388608))". See BinaryWidthSpelling.
        if (inferred instanceof BinaryType) {
            return ((BinaryType) inferred).refusalText();
        }
        if (StructuredTypes.isStructured(inferred)) {
            // Snowflake names the whole structured type in the message — "MAP(VARCHAR(134217728),
            // VARCHAR(134217728))", "OBJECT(x VARCHAR(134217728))" — not just its base family.
            return StructuredTypes.describe(inferred);
        }
        if (inferred instanceof UuidType) {
            return "UUID";
        }
        if (inferred instanceof LengthlessStringType) {
            // FLATTEN's KEY and PATH, SPLIT_TO_TABLE's VALUE: a VARCHAR the plan spells bare.
            return "VARCHAR";
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
        if (inferred instanceof DateTimeType) {
            final String name = inferred.getName().toUpperCase();
            if (name.equals("DATE")) {
                return "DATE";
            }
            // The DECLARED precision, not the family default: a TIMESTAMP_NTZ(3) column, a
            // ::TIME(3) cast and a fold over either read (3) on the account — SYSTEM$TYPEOF,
            // the TRY_CAST sentence and the argument-type list alike. The ZONED flavours keep their
            // own names: a TIMESTAMP_LTZ column is not a TIMESTAMP_NTZ, and collapsing them here made
            // SYSTEM$TYPEOF report the wrong type for both.
            final int precision = ((DateTimeType) inferred).getPrecision();
            final String spelled = precision < 0 ? "9" : String.valueOf(precision);
            if (name.equals("TIME")) {
                return "TIME(" + spelled + ")";
            }
            if (name.startsWith("TIMESTAMP_LTZ")) {
                return "TIMESTAMP_LTZ(" + spelled + ")";
            }
            if (name.startsWith("TIMESTAMP_TZ")) {
                return "TIMESTAMP_TZ(" + spelled + ")";
            }
            return "TIMESTAMP_NTZ(" + spelled + ")";
        }
        return inferred.getName().toUpperCase();
    }

    /**
     * The name of the TABLE a column reference resolves against, upper-cased, or null when it cannot
     * be resolved. Snowflake's conversion refusals name the parameter QUALIFIED — {@code BM.S} for an
     * unqualified {@code s} over table BM — so the offending operand has to be spelled with the
     * relation it came from, not as it was written.
     *
     * @param ref the column reference
     * @return the owning table's name, or null when nothing resolves it
     */
    String declaringTableName(final ColumnReferenceExpression ref) {
        final String columnName = ref.getColumnName();
        if (ref.getTableName() != null) {
            final Table qualified = tableForAlias(ref.getTableName());
            if (qualified != null && qualified.hasColumn(columnName)) {
                return qualified.getName().toUpperCase(Locale.ROOT);
            }
            return ref.getTableName().toUpperCase(Locale.ROOT);
        }
        if (table != null && table.hasColumn(columnName)) {
            return table.getName().toUpperCase(Locale.ROOT);
        }
        if (multiTableAllTables != null) {
            for (final Table candidate : multiTableAllTables) {
                if (candidate.hasColumn(columnName)) {
                    return candidate.getName().toUpperCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    /** The declared table column a reference points at, or null when it cannot be resolved (an
     *  alias, a UDF parameter, a lateral value, ...) or when its declared type is a placeholder.
     *  Two kinds of column carry a trustworthy type: a BASE catalog table's, and a DERIVED
     *  relation's whose type the inner projection could infer (see {@link #hasTrustedType}). */
    TableColumn resolveDeclaredColumn(final ColumnReferenceExpression ref) {
        if (ref.getPositionalOrdinal() > 0 && ref.getTableName() == null) {
            // $N is the Nth column of a single-relation FROM source by POSITION, so it is typed as
            // that column is — SELECT $1 FROM VALUES (TRUE) declares BOOLEAN live. A joined scope's
            // column order is not this walk's to judge, so it stays undetermined there.
            final List<TableColumn> columns = table == null ? null : table.getColumns();
            final boolean single = multiTableAllTables == null || multiTableAllTables.size() <= 1;
            if (columns != null && single && ref.getPositionalOrdinal() <= columns.size()) {
                final TableColumn positional = columns.get(ref.getPositionalOrdinal() - 1);
                return hasTrustedType(table, positional.getName()) ? positional : null;
            }
            return null;
        }
        final Table owner = resolveDeclaredOwner(ref);
        return owner == null ? null : owner.getColumn(ref.getColumnName());
    }

    /** The relation {@link #resolveDeclaredColumn} answers from, or null when the reference stays
     *  undetermined. */
    private Table resolveDeclaredOwner(final ColumnReferenceExpression ref) {
        final Table own = resolveDeclaredOwnerIn(ref, table, multiTableAliasToTable,
            multiTableAllTables);
        if (own != null || declaredTypeBase == null) {
            return own;
        }
        // The fallback is for a name this evaluator's own table does not carry AT ALL — a base column a
        // grouped SELECT list never projected. A slot it does carry has already answered above, or
        // deliberately stays undetermined.
        if (ref.getTableName() == null && table != null && table.hasColumn(ref.getColumnName())) {
            return null;
        }
        return resolveDeclaredOwnerIn(ref, declaredTypeBase, declaredTypeAliasToTable,
            declaredTypeAllTables);
    }

    /** {@link #resolveDeclaredOwner} against one relation and its FROM-clause context. */
    private Table resolveDeclaredOwnerIn(final ColumnReferenceExpression ref, final Table owner,
                                         final Map<String, Table> aliasToTable,
                                         final List<Table> allTables) {
        final String columnName = ref.getColumnName();
        if (ref.getTableName() != null) {
            final Table qualified = tableForAlias(ref.getTableName(), aliasToTable);
            if (qualified != null && qualified.hasColumn(columnName)
                    && hasTrustedType(qualified, columnName)) {
                return qualified;
            }
            if (owner != null && owner.getName().equalsIgnoreCase(ref.getTableName())
                    && owner.hasColumn(columnName) && hasTrustedType(owner, columnName)) {
                return owner;
            }
            return null;
        }
        if (owner != null && owner.hasColumn(columnName) && hasTrustedType(owner, columnName)) {
            return owner;
        }
        // A JOIN's relation carries every side's column untyped; the FROM-clause tables behind it are
        // what type a bare reference (live judges CASE WHEN h over a joined VARCHAR by that column).
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate.hasColumn(columnName)) {
                    return hasTrustedType(candidate, columnName) ? candidate : null;
                }
            }
        }
        return null;
    }

    /**
     * The interval a column reference's values lie in: a derived relation's column carries the one its
     * projection propagated, and a catalog column reads its statistics — the whole table's rows, as
     * the account's do. Null when nothing is known.
     *
     * <p>Over a join the column may also meet the NULL an outer join extends its side with, which its
     * table's statistics cannot see; a relation no outer join extends keeps what they prove (see
     * {@link JoinedRelations}).
     */
    ValueRange declaredColumnRange(final ColumnReferenceExpression ref) {
        Table owner = resolveDeclaredOwner(ref);
        if (owner == null) {
            return null;
        }
        final Table joined = joinedRelation();
        final JoinedRelations relations = joined == null ? null : joined.getJoinedRelations();
        if (relations != null && ref.getTableName() == null && relations.isNullExtended(owner)
                && isMergedJoinKey(joined, ref.getColumnName())) {
            // A USING or NATURAL key written bare is the join's merged column, which reads the side no
            // outer join extends — a RIGHT join's right side.
            final Table kept = relations.keptRelationWith(ref.getColumnName());
            if (kept != null) {
                owner = kept;
            }
        }
        final TableColumn column = owner.getColumn(ref.getColumnName());
        if (column == null) {
            return null;
        }
        if (!(column.getDataType() instanceof NumericType) || NumericType.isApproximate(column.getDataType())) {
            // Only an exact NUMBER column carries an interval: a VARCHAR read as a number in
            // arithmetic has none on the account — t + 0 over a column holding '5' is
            // NUMBER(19,5)[SB16], its declared width, and GREATEST(t, 1) NUMBER(18,5)[SB8] — and an
            // approximate or temporal column is tagged by its family alone.
            return null;
        }
        final ValueRange range;
        if (column.getValueRange() != null) {
            range = column.getValueRange();
        } else if (!owner.isCatalogResident() || queryExecutor == null) {
            return null;
        } else {
            range = queryExecutor.columnValueRange(owner, column.getName());
        }
        // Over a join a column may meet the NULL an outer join extends its side with, which the table's
        // own statistics cannot see. A relation the join's record names as unextended keeps what they
        // prove; one it does not name at all rules no NULL out.
        final boolean overJoin = multiTableAllTables != null && multiTableAllTables.size() > 1;
        final boolean mayBeExtended = relations == null || !relations.includes(owner)
            || relations.isNullExtended(owner);
        return range != null && overJoin && mayBeExtended ? range.withNullable(true) : range;
    }

    /**
     * The merged relation of the join this evaluator reads — its own relation, or the base its declared
     * types fall back to — when either records the relations it joins; else null.
     */
    private Table joinedRelation() {
        if (table != null && table.getJoinedRelations() != null) {
            return table;
        }
        return declaredTypeBase != null && declaredTypeBase.getJoinedRelations() != null ? declaredTypeBase : null;
    }

    /** Whether {@code columnName} is one of a USING or NATURAL join's merged key columns. */
    private static boolean isMergedJoinKey(final Table joined, final String columnName) {
        return joined.getJoinKeyNames() != null && columnName != null
            && joined.getJoinKeyNames().contains(columnName.toUpperCase(Locale.ROOT));
    }

    /**
     * Whether {@code expr} is an aggregate call other than COUNT, or a window call: a value the planner
     * does not count as never NULL when it reduces a COALESCE to its first argument, whatever the
     * statistics prove of it — live settles {@code COALESCE(COUNT(n), 0) > 5} and leaves
     * {@code COALESCE(MAX(n), 0) > 5} open.
     *
     * @param expr a COALESCE argument
     * @return true for an aggregate other than COUNT, or a window call
     */
    boolean aggregatesBeyondCount(final Expression expr) {
        if (expr instanceof WindowFunctionExpression) {
            return true;
        }
        if (!(expr instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getNameExpression() != null || call.getFunctionName() == null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        return !name.equals("COUNT") && functionRegistry != null && functionRegistry.hasAggregateFunction(name);
    }

    /**
     * Whether the SELECT clause executing reads within its table's statistics — no join, no grouping
     * key, no HAVING and no filter they cannot prove (see QueryExecutor.countIsUnbounded) — so that an
     * aggregate over it is answered from them, and never answers NULL over a column holding a value.
     */
    boolean readsWithinStatistics() {
        return queryExecutor == null || !queryExecutor.countIsUnbounded();
    }

    /**
     * The rows a COUNT over this evaluator's relation can reach — the catalog table's row count when
     * the query reads exactly one within its statistics (see QueryExecutor.countIsUnbounded), else
     * null. The account bounds COUNT by the table's statistics the same way, so two rows make COUNT an SB1.
     */
    Long countableRows() {
        if (queryExecutor == null || queryExecutor.countIsUnbounded()) {
            return null;
        }
        if (multiTableAllTables != null && multiTableAllTables.size() > 1) {
            return null;
        }
        final Long own = statisticsRowCount(table);
        if (own != null) {
            return own;
        }
        if (declaredTypeBase != null && (declaredTypeAllTables == null || declaredTypeAllTables.size() <= 1)) {
            return statisticsRowCount(declaredTypeBase);
        }
        return null;
    }

    /**
     * The row count a relation's statistics bound a COUNT by: a catalog table's own, or the one beneath a
     * derived relation that still reads within that table's statistics; null for any other relation.
     */
    private Long statisticsRowCount(final Table relation) {
        if (relation == null) {
            return null;
        }
        if (relation.isCatalogResident()) {
            return queryExecutor.baseTableRowCount(relation);
        }
        final RelationStatistics statistics = relation.getRelationStatistics();
        return statistics != null && statistics.readsWithinStatistics() ? statistics.sourceRowCount() : null;
    }

    /**
     * Whether a COUNT over {@code argument} is answered from the statistics: over a constant, or over a
     * column that is one of the catalog table's stored columns — a derived relation's too, where it passes
     * one through unchanged. Live answers COUNT(c) and COUNT(1) from them and scans for COUNT(c + 1),
     * COUNT(ABS(c)) and COUNT(c::NUMBER(10,1)); an IFF over a constant condition counts as the branch it
     * folds to.
     *
     * @param argument the COUNT's one argument
     * @return true when the statistics answer it
     */
    public boolean countsStoredColumn(final Expression argument) {
        Expression counted = argument;
        while (counted instanceof FunctionCallExpression && isConstantIff((FunctionCallExpression) counted)) {
            final List<Expression> branches = ((FunctionCallExpression) counted).getArguments();
            counted = branches.get(Boolean.TRUE.equals(((LiteralExpression) branches.get(0)).getValue()) ? 1 : 2);
        }
        if (counted instanceof LiteralExpression) {
            return true;
        }
        if (!(counted instanceof ColumnReferenceExpression)
                || ((ColumnReferenceExpression) counted).getPositionalOrdinal() != 0
                || ((ColumnReferenceExpression) counted).getColumnName() == null) {
            return false;
        }
        final String name = ((ColumnReferenceExpression) counted).getColumnName();
        final Boolean own = storedColumnOf(table, name);
        if (own != null) {
            return own;
        }
        return Boolean.TRUE.equals(storedColumnOf(declaredTypeBase, name));
    }

    /** IFF whose condition is a boolean literal. */
    private static boolean isConstantIff(final FunctionCallExpression call) {
        return call.getNameExpression() == null && "IFF".equalsIgnoreCase(call.getFunctionName())
            && call.getArguments().size() == 3 && call.getArguments().get(0) instanceof LiteralExpression
            && ((LiteralExpression) call.getArguments().get(0)).getValue() instanceof Boolean;
    }

    /**
     * Whether {@code name} is a stored column of {@code relation}; null when the relation carries no
     * statistics of its own — neither a catalog table nor a relation over one — or no such column.
     */
    private static Boolean storedColumnOf(final Table relation, final String name) {
        if (relation == null || !relation.hasColumn(name)) {
            return null;
        }
        if (relation.isCatalogResident()) {
            return Boolean.TRUE;
        }
        final RelationStatistics statistics = relation.getRelationStatistics();
        return statistics == null ? null
            : Boolean.valueOf(statistics.passesStoredColumn(relation.getColumnIndex(name)));
    }

    /** The interval {@code expr}'s values lie in, or null when none can be known. Never throws. */
    public ValueRange inferStaticRange(final Expression expr) {
        return valueRangeInferencer.infer(expr);
    }

    /**
     * A call's declared arity — a registered scalar's bounds, or an aggregate's — judged before its
     * argument families. A star call counts no arguments, a t.* argument counts the columns it
     * expands to and a ** spread's width is known only once evaluated, so all three are left to their
     * own checks.
     *
     * @param funcName the call's name, upper-cased
     * @param call     the call
     */
    private void rejectDeclaredArity(final String funcName, final FunctionCallExpression call) {
        if (call.isStar()) {
            return;
        }
        for (final Expression arg : call.getArguments()) {
            // A t.* beside other arguments expands first, and the expanded list is what the arity
            // rule judges and echoes — the strict pass does that; this one only sees the written list.
            if (arg instanceof SpreadExpression || arg instanceof ColumnReferenceExpression
                    && ((ColumnReferenceExpression) arg).getStarArgument() != null) {
                return;
            }
        }
        if (functionRegistry.getFunction(funcName) != null) {
            rejectWrittenArity(funcName, call);
            return;
        }
        final AggregateFunction aggregate = functionRegistry.getAggregateFunction(funcName);
        if (aggregate != null) {
            rejectAggregateArity(aggregate, call);
        }
    }

    /**
     * Applies every function's argument-family refusals to the calls in {@code expr} from their
     * DECLARED types, before any row is read — the check the row loop makes for each call, brought
     * forward so that an aggregate over an empty table is refused as live refuses it: {@code SELECT
     * AVG(bo) FROM t WHERE FALSE} is "Invalid argument types for function 'SUM': (BOOLEAN)" at the
     * call on the account, not NULL. A call the registry does not know is left to its own refusal.
     *
     * @param expr the expression to walk; window calls are the window stage's own business
     */
    public void rejectArgumentFamilies(final Expression expr) {
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            // Inside-out, as live compiles: an argument's own refusal — a cast the conversion matrix
            // refuses, a nested call's family — is reported ahead of the call's rule about it, so
            // SUM(a::DATE) over a NUMBER earns the cast's sentence and not SUM's.
            for (final Expression argument : call.getArguments()) {
                rejectArgumentFamilies(argument);
            }
            if (call.getNameExpression() == null && call.getFunctionName() != null) {
                final String funcName = call.getFunctionName().toUpperCase();
                // The call's COUNT before any family: SUM(ARRAY_CONSTRUCT(), 1) is "too many arguments
                // … expected 1, got 2" on the account, not the ARRAY's refusal (live-verified).
                rejectDeclaredArity(funcName, call);
                if (funcName.equals("COUNT_IF") && call.isDistinct()) {
                    // A DISTINCT COUNT_IF is refused outright, the call echoed from the plan and no
                    // position: "invalid use of 'distinct' for function 'COUNT_IF(DISTINCT AW.B)'".
                    throw new RuntimeException(SqlCompilationError.of(
                        "invalid use of 'distinct' for function '" + strictText(call) + "'"));
                }
                // A conversion's own source rule belongs to this walk too, or an aggregate around
                // TO_DATE(f) would be judged before the conversion it wraps. A TIMESTAMP scale is
                // asked first: TRY_TO_TIMESTAMP(n, '3') is the scale sentence, not TRY_CAST's.
                rejectNonIntegerTimestampScale(funcName, call.getArguments());
                rejectNonStringTryToSource(funcName, call.getArguments());
                rejectNonStringConversionSource(funcName, call.getArguments());
                rejectSemiStructuredArgument(funcName, call.getArguments(), call);
            }
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            rejectArgumentFamilies(((BinaryOperationExpression) expr).getLeft());
            rejectArgumentFamilies(((BinaryOperationExpression) expr).getRight());
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            rejectArgumentFamilies(((UnaryOperationExpression) expr).getOperand());
            return;
        }
        if (expr instanceof CastExpression) {
            rejectArgumentFamilies(((CastExpression) expr).getExpression());
            rejectCastSourceStatically((CastExpression) expr);
            return;
        }
        if (expr instanceof CaseExpression) {
            for (final WhenClause when : ((CaseExpression) expr).getWhenClauses()) {
                rejectArgumentFamilies(when.getCondition());
                rejectArgumentFamilies(when.getResult());
            }
            rejectArgumentFamilies(((CaseExpression) expr).getElseExpression());
        }
    }

    /** The table a qualifier names, matched case-insensitively by alias and then by table name. The
     *  multi-table map is keyed by whatever text the FROM clause used, so a lower-case alias
     *  ({@code FROM t JOIN (...) j}) is not found by an upper-cased key alone. */
    private Table tableForAlias(final String qualifier) {
        return tableForAlias(qualifier, multiTableAliasToTable);
    }

    private static Table tableForAlias(final String qualifier, final Map<String, Table> aliasToTable) {
        if (aliasToTable == null) {
            return null;
        }
        final Table exact = aliasToTable.get(qualifier.toUpperCase());
        if (exact != null) {
            return exact;
        }
        for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
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
    /**
     * Enter ARGUMENT position for the scope check, returning the enclosing state to restore. The
     * date/time-unit barewords are exempt only there — {@code DATEADD(HOUR, …)} is legal where a
     * top-level {@code WHERE year = 1} is not — so a phase walk that descends into a call has to say
     * so, exactly as the combined walk does.
     */
    boolean beginFunctionArgumentScope() {
        final boolean enclosing = strictWalkInsideFunctionArgs;
        strictWalkInsideFunctionArgs = true;
        return enclosing;
    }

    /** Leave argument position, restoring what {@link #beginFunctionArgumentScope} returned. */
    void endFunctionArgumentScope(final boolean enclosing) {
        strictWalkInsideFunctionArgs = enclosing;
    }

    /** PHASE ONE of the plan-time walk: every column reference in {@code expression}, nothing else. */
    public void validateColumnScopeOnly(final Expression expression) {
        expression.accept(new ColumnScopeWalk(this));
    }

    /** PHASE TWO: every call NAME in {@code expression}, nothing else. */
    public void validateFunctionNamesOnly(final Expression expression) {
        expression.accept(new FunctionNameWalk(this));
    }

    /**
     * A column is qualified by at most a database, a schema and a relation: an object name may lead with
     * the account, but a column reference may not. A fifth part is refused as the whole reference, at its
     * start; a sixth and beyond earns live's own sentence, which reads the name as a sequence identifier
     * (both live-verified). The sequence pseudo-columns keep their own rules.
     */
    private static void rejectOverQualifiedColumn(final ColumnReferenceExpression ref, final String columnName) {
        if ("NEXTVAL".equalsIgnoreCase(columnName) || "CURRVAL".equalsIgnoreCase(columnName)) {
            return;
        }
        final int parts = QualifiedName.parse(ref.getTableName()).size() + 1;
        if (parts < 5) {
            return;
        }
        final String dotted = ref.getWrittenName() != null ? ref.getWrittenName()
            : ref.getTableName().toUpperCase() + "." + columnName.toUpperCase();
        if (parts > 5) {
            throw new RuntimeException("SQL compilation error: sequence identifier '" + dotted
                + "' has too many qualifiers");
        }
        final SourcePosition at = ExpressionSource.resolve(ref.getPosition());
        throw at != null ? new InvalidQualifierException(dotted, at) : new InvalidQualifierException(dotted);
    }

    void validateColumnReferenceScope(final ColumnReferenceExpression ref) {
        if (lateralContext != null
                || (queryExecutor != null && queryExecutor.isInLateralExecution())) {
            // A correlated / lateral evaluation resolves OUTER names per row; the plan-time walk
            // cannot see them, so scope validation stays row-time there.
            return;
        }
        final String columnName = ref.getColumnName();
        if (ref.isQualified()) {
            rejectOverQualifiedColumn(ref, columnName);
            // A MULTI-PART qualifier is asked the same two questions as a single-part one, of its LAST
            // part and of the suffix as a whole — the clause walk used to skip it entirely, so
            // `ORDER BY test_schema.kw.nosuchcol` reached row time and `GROUP BY` swallowed it whole.
            if (fromClauseKeys != null && !fromClauseKeys.isEmpty()
                    && !"NEXTVAL".equalsIgnoreCase(columnName)
                    && !"CURRVAL".equalsIgnoreCase(columnName)
                    && !qualifierIsAFromClauseKey(ref.getTableName())
                    && !(qualifierIsAFromClauseKey(lastQualifierPart(ref.getTableName()))
                        && qualifierNamesTheSameRelation(ref.getTableName()))) {
                final SourcePosition qualifierAt = ExpressionSource.resolve(ref.getPosition());
                final String dotted = ref.getWrittenName() != null ? ref.getWrittenName()
                    : ref.getTableName().toUpperCase() + "." + columnName.toUpperCase();
                // The position is the whole reference's start, qualifier included — live reports 7 for
                // `SELECT t.nosuchcol`, not the offset of the column part after the dot.
                throw qualifierAt != null
                    ? new InvalidQualifierException(dotted, qualifierAt)
                    : new InvalidQualifierException(dotted);
            }
            rejectMissingColumnOnResolvedQualifier(ref);
            return;
        }
        // A bare name that is a SELECT output alias resolves through the alias in WHERE / GROUP BY /
        // HAVING / QUALIFY / ORDER BY (all alias-visible in Snowflake), so it is never rejected here.
        // Matched EXACTLY: both sides are canonical already, and folding here let a quoted alias
        // answer to a name it does not carry — live refuses `SELECT a AS "x" … ORDER BY "X"`.
        if (scopeExemptNames != null && scopeExemptNames.contains(columnName)) {
            return;
        }
        if (ref.getPositionalOrdinal() > 0) {
            // $N reads the Nth column of the FROM source BY POSITION — no name has to exist for
            // it. In a single-relation scope whose width is known, an ordinal past the last column
            // is live's compile-time refusal, spelled in the dollar form at the reference's own
            // position; a joined scope's width is not this walk's to judge.
            if (table != null && (multiTableAllTables == null || multiTableAllTables.size() <= 1)
                    && ref.getPositionalOrdinal() > table.getColumns().size()
                    && !table.hasColumn(columnName)) {
                final SourcePosition dollarAt = ExpressionSource.resolve(ref.getPosition());
                final String dollar = "$" + ref.getPositionalOrdinal();
                throw dollarAt != null
                    ? new InvalidQualifierException(dollar, dollarAt)
                    : new InvalidQualifierException(dollar);
            }
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
            final SourcePosition where = ExpressionSource.resolve(ref.getPosition());
            // Echoed AS WRITTEN: a quoted reference keeps its quotes and its case, because "a" and A
            // are different columns and the folded spelling would name the wrong one.
            final String echoed = ref.getWrittenName() != null ? ref.getWrittenName() : upperName;
            throw where != null
                ? new InvalidQualifierException(echoed, where)
                : new InvalidQualifierException(echoed);
        }
    }

    /**
     * A qualifier that RESOLVES still has to carry the column: live refuses
     * {@code SELECT a FROM t WHERE t.nosuchcol = 1} at COMPILE time, at the whole reference's
     * position, without reading a row. Frostlake used to pass this branch of the walk on the strength
     * of the qualifier alone and fail later inside an operator that had been handed the predicate as
     * extracted text — which both deferred the refusal past its compile-time moment and lost the
     * position, because the operator no longer knew where the fragment began.
     *
     * <p>Deliberately narrow, because refusing too much here would reject queries Snowflake runs: it
     * fires only when the qualifier positively resolves to a relation whose columns are known. A
     * derived table, a table function or anything the walk cannot pin down answers null and is left
     * to row time exactly as before, as are USING / NATURAL join keys, which belong to the merged
     * relation rather than to either side.
     */
    private void rejectMissingColumnOnResolvedQualifier(final ColumnReferenceExpression ref) {
        final String columnName = ref.getColumnName();
        // However many parts precede it, the relation is the qualifier's LAST part; the parts before
        // it were already checked to be that relation's own containers by the caller above.
        final String relationPart = lastQualifierPart(ref.getTableName());
        if (fromClauseKeys == null || fromClauseKeys.isEmpty()
                || "NEXTVAL".equalsIgnoreCase(columnName)
                || "CURRVAL".equalsIgnoreCase(columnName)
                || !qualifierIsAFromClauseKey(relationPart)) {
            return;
        }
        final Table qualified = qualifiedRelationFor(relationPart);
        if (qualified == null || qualified.getColumns() == null || qualified.getColumns().isEmpty()) {
            return;
        }
        // A QUOTED column part must match exactly — "a" and A are different columns, and the
        // relation's own accessor is case-insensitive by design (it is an internal Java API). An
        // unquoted part arrives upper-cased, so it keeps the lenient lookup it always had.
        if (tableCarriesColumn(qualified, columnName)
                || isUnquotedSpelling(columnName) && qualified.hasColumn(columnName)
                || isJoinKeyName(columnName)) {
            return;
        }
        final SourcePosition at = ExpressionSource.resolve(ref.getPosition());
        final String dotted = ref.getWrittenName() != null ? ref.getWrittenName()
            : ref.getTableName().toUpperCase() + "." + columnName.toUpperCase();
        throw at != null
            ? new InvalidQualifierException(dotted, at)
            : new InvalidQualifierException(dotted);
    }

    /** The relation a resolving qualifier names — by alias when joined, else the single FROM table. */
    private Table qualifiedRelationFor(final String qualifier) {
        final Table byAlias = tableForAlias(qualifier);
        if (byAlias != null) {
            return byAlias;
        }
        return table != null && qualifier.equalsIgnoreCase(table.getName()) ? table : null;
    }

    /**
     * The date/time-unit barewords the grammar parses as column references when written as function
     * arguments ({@code DATEADD(HOUR, …)}); resolved to their own name as a string.
     *
     * <p>Every ABBREVIATION the account takes counts here too, and there are dozens of them —
     * {@code yy}, {@code qtr}, {@code mm}, {@code wk}, {@code dd}, {@code hh}, {@code mi},
     * {@code sec}, {@code ms}, {@code us}, {@code ns} and the rest. They come from
     * {@link IntervalUnit}, which already holds the measured table, rather than being spelled out a
     * second time here: two copies of the same vocabulary drift, and this one had drifted so far that
     * {@code DATEADD(dd, 1, d)} failed with "invalid identifier 'DD'" — the word never reached the
     * function at all.
     *
     * <p>The words below are the ones {@link IntervalUnit} does NOT carry: date PARTS that name a
     * component rather than a span, which no interval can be measured in.
     */
    private static boolean isDateTimeUnitKeyword(final String upper) {
        if (IntervalUnit.fromSpelling(upper) != null) {
            return true;
        }
        switch (upper) {
            case "DAYOFWEEK": case "DAYOFWEEKISO": case "DAYOFYEAR":
            case "WEEKISO": case "ISOWEEK": case "YEAROFWEEK": case "YEAROFWEEKISO":
            case "WOY": case "WEEKOFYEAR":
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
            if (col.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Bare names exempt from the plan-time scope rejection — the enclosing query's SELECT output
     *  aliases / output column names, which the non-SELECT clauses may legally reference. */
    public void setOutputScopeNames(final Set<String> names) {
        this.outputScopeNames = names;
    }

    public void setOutputAliasTypes(final Map<String, DataType> types) {
        this.outputAliasTypes = types;
    }

    /**
     * The type a BARE reference takes when it names a SELECT output alias rather than a relation's
     * column — the alias's own expression, typed in the same context — or null when it names none.
     * A qualified reference never means an alias.
     */
    DataType outputAliasType(final ColumnReferenceExpression ref) {
        if (outputAliasTypes == null || ref.getTableName() != null) {
            return null;
        }
        return outputAliasTypes.get(ref.getColumnName().toUpperCase(Locale.ROOT));
    }

    public void setScopeExemptNames(final Set<String> names) {
        this.scopeExemptNames = names;
    }

    /** Whether {@code qualifier} matches one of the FROM clause's relation keys, case-insensitively.
     *  Every legitimately-referencable relation is registered under exactly its key — the alias when
     *  one was written, else the table name — so the key set is the whole rule. */
    /**
     * Whether a MULTI-PART qualifier names the very relation the FROM clause holds. Live requires the
     * qualifier to be a contiguous right-SUFFIX of that relation's full name, measured cell by cell:
     *
     * <pre>
     *   FROM kw               kw.a   test_schema.kw.a   test_db.test_schema.kw.a     all read it
     *                         nosuchschema.kw.a                                      refused
     *                         test_db.kw.a   — the schema SKIPPED                    refused
     *   FROM other_schema.t   t.a   other_schema.t.a   test_db.other_schema.t.a      all read it
     *                         test_schema.t.a                                        refused
     * </pre>
     *
     * <p>Asking the CATALOG to resolve the whole qualifier and comparing the RELATION it lands on is
     * that rule exactly, and it needs no container plumbed down here: a suffix that names the same
     * table resolves to the same table, and one that names a different container resolves to another
     * table or to nothing. The lookup happens only for a multi-part qualifier that already passed the
     * FROM-key check, so the ordinary {@code t.c} reference never pays for it.
     */
    private boolean qualifierNamesTheSameRelation(final String qualifier) {
        if (qualifier.indexOf('.') < 0 || multiTableAliasToTable == null) {
            return true;
        }
        final Table named = multiTableAliasToTable.get(lastQualifierPart(qualifier).toUpperCase(Locale.ROOT));
        if (named == null) {
            return true;
        }
        final Catalog cat = catalog != null ? catalog
            : (queryExecutor != null ? queryExecutor.getCatalog() : null);
        if (cat == null) {
            return true;
        }
        try {
            return named == cat.resolveTable(qualifier);
        } catch (final RuntimeException unresolvable) {
            return false;
        }
    }

    /** The relation's own name out of a dotted qualifier — the part an alias would replace. */
    private String lastQualifierPart(final String qualifier) {
        final int lastDot = qualifier.lastIndexOf('.');
        return lastDot < 0 ? qualifier : qualifier.substring(lastDot + 1);
    }

    private boolean qualifierIsAFromClauseKey(final String qualifier) {
        for (final String key : fromClauseKeys) {
            if (key != null && key.equals(qualifier)) {
                return true;
            }
        }
        // Only an UNQUOTED qualifier may match loosely. It reaches here upper-cased, so a key held in
        // another case still names the same relation; a qualifier that is not all-upper was QUOTED,
        // and live resolves a quoted name to nothing but itself.
        if (!isUnquotedSpelling(qualifier)) {
            return false;
        }
        for (final String key : fromClauseKeys) {
            if (key != null && key.equalsIgnoreCase(qualifier)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a canonical name is the spelling an UNQUOTED identifier leaves behind. Canonicalisation
     * upper-cases an unquoted part and leaves a quoted one as written, so anything not already
     * upper-case was quoted — the one case that must resolve exactly.
     *
     * @param name the canonical name
     * @return true when the name could have been written without quotes
     */
    private boolean isUnquotedSpelling(final String name) {
        return name != null && name.equals(name.toUpperCase(Locale.ROOT));
    }

    /** How many of the joined tables carry a column named {@code name}, case-insensitively. */
    private int countTablesCarrying(final String name) {
        int count = 0;
        for (final Table candidate : multiTableAllTables) {
            if (candidate != null && carriesExactly(candidate, name)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether a relation carries a column of EXACTLY this name. Ambiguity is decided on the name as the
     * reference means it — an unquoted one upper-cased, a quoted one verbatim — so an unquoted
     * {@code TABLE_NAME} and a quoted {@code "table_name"} are two different columns and neither makes
     * the other ambiguous, which is how the account resolves them.
     */
    private static boolean carriesExactly(final Table relation, final String name) {
        for (final TableColumn column : relation.getColumns()) {
            if (column.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code columnName} names a USING / NATURAL join key of the current relation. */
    private boolean isJoinKeyName(final String columnName) {
        if (table == null || table.getJoinKeyNames() == null) {
            return false;
        }
        for (final String keyName : table.getJoinKeyNames()) {
            if (keyName.equals(columnName)) {
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
                if (sideColumns.get(i).getName().equals(columnName)) {
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
            if (columns.get(i).getName().equals(columnName)
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

    /**
     * The NUMBER {@code expr} spells when it is a bare string literal, or a column that carries one out
     * of a derived relation, else null — see TableColumn#getSpelledNumber.
     *
     * @param expr a select item
     * @return the measured NUMBER, or null
     */
    public DataType spelledNumber(final Expression expr) {
        if (expr instanceof ColumnReferenceExpression) {
            final TableColumn resolved = resolveDeclaredColumn((ColumnReferenceExpression) expr);
            return resolved == null ? null : resolved.getSpelledNumber();
        }
        return TypeInferencer.spelledNumber(expr);
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
        final Object baseValue = expr.getBase().accept(this);

        if (baseValue == null) {
            return null;
        }

        // Traverse the property chain using the path segments from the parse tree (data:a.b.c → [a,b,c]).
        // Once inside a parsed tree, INTERMEDIATE steps walk raw JsonNodes — converting per step
        // wrapped and serialized every subtree per row, only for the next step to unwrap it again.
        Object current = baseValue;
        final List<String> pathParts = expr.getPathParts();
        for (int p = 0; p < pathParts.size(); p++) {
            final String part = pathParts.get(p);
            if (current == null) return null;
            if (current instanceof VariantValue) {
                JsonNode node = ((VariantValue) current).node();
                // Raw-walk while the child is a CONTAINER; a scalar mid-chain converts here so the
                // text branch below keeps handling the remaining parts exactly as before.
                while (p < pathParts.size() - 1 && node != null) {
                    final JsonNode child = JsonPathExtractor.propertyNode(node, pathParts.get(p));
                    if (child == null || (!child.isObject() && !child.isArray())) {
                        break;
                    }
                    node = child;
                    p++;
                }
                current = JsonPathExtractor.extractProperty(node, pathParts.get(p));
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
        final Object arrayValue = expr.getArray().accept(this);
        final Object indexValue = expr.getIndex().accept(this);

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
        return evaluateScalarSubquery(expr);
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
        final ObjectNode object = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, Expression> entry : expr.getProperties().entrySet()) {
            final Object value = entry.getValue().accept(this);
            // Snowflake object constants follow OBJECT_CONSTRUCT semantics: a pair whose value is
            // SQL NULL is omitted from the object (a VARIANT JSON null — the text "null" — stays a
            // null member). The vendor stats pattern depends on this: {'k': null, ...} followed by
            // OBJECT_INSERT(obj, 'k', v) without the update flag only works when 'k' was dropped.
            if (value == null) {
                continue;
            }
            object.set(entry.getKey(), jsonElementNode(entry.getValue(), value));
        }
        // Canonical, keys sorted, so the literal compares equal to the same value read from a VARIANT.
        return ArrayFunctionHelper.toCanonicalVariant(object);
    }

    @Override
    public Object visitJsonArray(final JsonArrayExpression expr) {
        final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final Expression element : expr.getElements()) {
            final Object value = element.accept(this);
            // A SQL NULL ELEMENT is the VARIANT `undefined`, not a JSON null — live-verified:
            // SELECT [1, NULL, 2] is [1,undefined,2] and SELECT [{'a': NULL}, NULL] is [{},undefined]
            // (the object pair is dropped, the array element becomes undefined).
            array.add(value == null ? VariantUndefined.node() : jsonElementNode(element, value));
        }
        return ArrayFunctionHelper.toCanonicalVariant(array);
    }

    @Override
    public Object visitLikeAnyAll(final LikeAnyAllExpression expr) {
        final CollationSpec rules = likeAnyAllCollation(expr);
        final Object subject = expr.getSubject().accept(this);
        if (subject == null) {
            return null;
        }
        final BinaryOperator op = expr.isCaseInsensitive() ? BinaryOperator.ILIKE : BinaryOperator.LIKE;
        // A NULL escape leaves the multi-pattern predicate answering as the default one does
        // (live-verified), where the single-pattern form answers NULL for it.
        final Character written = resolveEscapeChar(expr.getEscape());
        final char escapeChar = written == null ? '\\' : written.charValue();
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
            final boolean collated = rules != null && subject instanceof String && pattern instanceof String;
            final boolean matches = Boolean.TRUE.equals(collated
                ? evaluateLike(rules.likeOperand((String) subject), rules.likeOperand((String) pattern), op,
                    escapeChar)
                : evaluateLike(subject, pattern, op, escapeChar));
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
        // The two bounds are two comparisons, each settling its own collation with the value's.
        final CollationSpec lowRules = comparisonCollation(expr.getValue(), expr.getLower());
        final CollationSpec highRules = comparisonCollation(expr.getValue(), expr.getUpper());
        final Object value = expr.getValue().accept(this);
        final Object lower = expr.getLower().accept(this);
        final Object upper = expr.getUpper().accept(this);

        // In SQL, NULL BETWEEN anything is NULL (treated as false)
        if (value == null || lower == null || upper == null) {
            return false;
        }

        // BETWEEN is two comparisons, so a VARIANT bound keeps its text ordering the way '<' does:
        // live `src:score BETWEEN '10' AND '8'` is TRUE, because "7.5" sorts between them as text.
        final boolean collatedLow = lowRules != null && value instanceof String && lower instanceof String;
        final boolean collatedHigh = highRules != null && value instanceof String && upper instanceof String;
        final boolean result =
            (collatedLow ? lowRules.compare((String) value, (String) lower) >= 0
                : compare(comparisonOperand(expr.getValue(), value, lower),
                    comparisonOperand(expr.getLower(), lower, value)) >= 0)
            && (collatedHigh ? highRules.compare((String) value, (String) upper) <= 0
                : compare(comparisonOperand(expr.getValue(), value, upper),
                    comparisonOperand(expr.getUpper(), upper, value)) <= 0);

        return expr.isNot() ? !result : result;
    }

    @Override
    public Object visitIn(final InExpression expr) {
        final Object value = expr.getValue().accept(this);

        if (expr.hasSubquery()) {
            // IN with subquery
            return evaluateInSubquery(value, expr.getSubquery(), expr.isNot(),
                collationOf(expr.getValue()).toRules());
        }

        // IN with a value list, using three-valued logic:
        //   value IN (list)     → TRUE if it equals a member; else UNKNOWN if any member (or the value
        //                         itself) is NULL; else FALSE.
        //   value NOT IN (list) → the boolean negation, with UNKNOWN preserved.
        final CollationSpec rules = inListCollation(expr);
        final boolean uuidSubject = typeInferencer.infer(expr.getValue()) instanceof UuidType;
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
            if (rules != null && value instanceof String && listValue instanceof String) {
                if (rules.compare((String) value, (String) listValue) == 0) {
                    return !expr.isNot();
                }
                continue;
            }
            if (uuidSubject && value instanceof CharSequence && listValue instanceof CharSequence) {
                // A membership test over a UUID reads every member as a UUID, so letter case does not
                // matter and a member that is no UUID is refused where it is read.
                if (ToUuid.canonical(value.toString()).equals(ToUuid.canonical(listValue.toString()))) {
                    return !expr.isNot();
                }
                continue;
            }
            if (equals(comparisonOperand(expr.getValue(), value, listValue),
                    comparisonOperand(valueExpr, listValue, value))) {
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
        final Object leftValue = expr.getLeft().accept(this);
        // The subject's collation drives the comparison against every row the subquery answers, the
        // way it drives the comparison against a literal list.
        final CollationSpec quantifiedRules = collationOf(expr.getLeft()).toRules();

        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate quantified comparison: QueryExecutor not available");
        }

        // Extract subquery SQL from SubqueryExpression
        final String subquerySql;
        if (expr.getSubquery() instanceof SubqueryExpression) {
            subquerySql = ((SubqueryExpression) expr.getSubquery()).getSubquery();
        } else {
            throw new RuntimeException("Quantified comparison requires SubqueryExpression");
        }

        final List<ResultSet> results = executeSubquery(subquerySql);

        if (results.isEmpty() || results.get(0).getRowCount() == 0) {
            // Empty result set: ALL returns true, ANY/SOME returns false
            return expr.getQuantifier() == Quantifier.ALL;
        }

        final ResultSet resultSet = results.get(0);
        final BinaryOperator operator = expr.getOperator();

        if (expr.getQuantifier() == Quantifier.ALL) {
            // ALL (three-valued): FALSE if the comparison fails for any row (dominates); else
            // UNKNOWN if it is UNKNOWN for any row (a NULL on either side); else TRUE.
            boolean anyUnknown = false;
            for (final Row subRow : resultSet.getRows()) {
                final Boolean cmp = compareWithOperator(leftValue, subRow.getValue(0), operator, quantifiedRules);
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
                final Boolean cmp = compareWithOperator(leftValue, subRow.getValue(0), operator, quantifiedRules);
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
        if (chain != null && expr.isUnitInString()) {
            chain.markUnitInString();
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

    /** The logical operators' STRICT string conversion — see {@link ExpressionArithmetic#strictBooleanOrNull}. */
    private Boolean strictBooleanOrNull(final Object value) {
        return ExpressionArithmetic.strictBooleanOrNull(value);
    }

    private Object add(final Object left, final Object right) {
        return ExpressionArithmetic.add(left, right);
    }

    private Object subtract(final Object left, final Object right) {
        return ExpressionArithmetic.subtract(left, right);
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

    /** The functions whose result carries their FIRST argument's collation (live-verified one by one). */
    /** The functions that MATCH text rather than merely carry a collation out — see comparingCollation. */
    private static final Set<String> COMPARING_FUNCTIONS = Set.of(
        "CONTAINS", "STARTSWITH", "ENDSWITH", "POSITION", "CHARINDEX", "REPLACE", "SPLIT",
        "SPLIT_PART", "NULLIF", "DECODE", "GREATEST", "LEAST", "GREATEST_IGNORE_NULLS",
        "LEAST_IGNORE_NULLS");

    private static final Set<String> COLLATION_FROM_FIRST_ARGUMENT = new HashSet<>(Arrays.asList(
        "UPPER", "LOWER", "INITCAP", "REVERSE", "TRIM", "LTRIM", "RTRIM", "LEFT", "RIGHT", "LPAD", "RPAD",
        "SUBSTR", "SUBSTRING", "SPLIT_PART", "TRANSLATE", "MAX", "MIN", "ANY_VALUE", "LISTAGG", "NULLIF"));

    /**
     * The collation {@code expr} carries, and the level it holds it at (see {@link CollationLevel}).
     * Live-verified, construct by construct:
     *
     * <pre>
     *   x COLLATE 'spec', COLLATE(x, 'spec')   the spec, EXPLICIT; the outermost wins, and COLLATE ''
     *                                          or a bare NULL operand carries none
     *   a collated column                      its declared collation, COLUMN
     *   a || b, CONCAT, CONCAT_WS, GREATEST,
     *   LEAST, DECODE's and a CASE's results   the operands', settled left to right
     *   COALESCE, NVL, IFNULL, IFF's and
     *   NVL2's results                         the same, settled from the LAST one back
     *   UPPER, LOWER, TRIM, SUBSTR, LEFT, MAX  the first argument's
     *   a cast, TO_VARCHAR, REPLACE, REPEAT,
     *   a literal, anything else               none
     * </pre>
     *
     * <p>Two collations that disagree at one level are the compilation error
     * {@link ExpressionCollation#combine} raises, so asking an expression for its collation also
     * judges it — which is how the static channel refuses them over any number of rows.
     *
     * @param expr the expression
     * @return its collation
     */
    public ExpressionCollation collationOf(final Expression expr) {
        if (expr instanceof ColumnReferenceExpression) {
            return ExpressionCollation.column(declaredCollation((ColumnReferenceExpression) expr));
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            if (binary.getOperator() != BinaryOperator.CONCAT) {
                return ExpressionCollation.NONE;
            }
            return ExpressionCollation.combine(collationOf(binary.getLeft()), collationOf(binary.getRight()));
        }
        if (expr instanceof CaseExpression) {
            return settledLeftToRight(caseResults((CaseExpression) expr));
        }
        if (!(expr instanceof FunctionCallExpression)) {
            return ExpressionCollation.NONE;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getFunctionName() == null || call.getNameExpression() != null) {
            return ExpressionCollation.NONE;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = call.getArguments();
        if ("COLLATE".equals(name)) {
            return collateCallCollation(call);
        }
        if (args.isEmpty()) {
            return ExpressionCollation.NONE;
        }
        if (COLLATION_FROM_FIRST_ARGUMENT.contains(name)) {
            return collationOf(args.get(0));
        }
        if ("CONCAT".equals(name) || "CONCAT_WS".equals(name) || "GREATEST".equals(name)
                || "LEAST".equals(name)) {
            return settledLeftToRight(args);
        }
        if ("DECODE".equals(name)) {
            return settledLeftToRight(TypeInferencer.conditionalBranches(name, args));
        }
        if ("COALESCE".equals(name) || "NVL".equals(name) || "IFNULL".equals(name) || "IFF".equals(name)
                || "NVL2".equals(name)) {
            return settledRightToLeft(TypeInferencer.conditionalBranches(name, args));
        }
        return ExpressionCollation.NONE;
    }

    /**
     * The collation a COMPARING function looks for its match under, or null when the call carries none.
     * Only the arguments the function COMPARES settle it — DECODE's results and REPLACE's replacement
     * are carried out, not matched against, so a collation of theirs neither counts nor collides.
     *
     * @param name the function's name, upper-cased
     * @param call the call
     * @return the rules its comparison runs by, or null for a binary one
     */
    private CollationSpec comparingCollation(final String name, final FunctionCallExpression call) {
        if (!COMPARING_FUNCTIONS.contains(name) || call.getArguments().isEmpty()) {
            return null;
        }
        final List<Expression> args = call.getArguments();
        if ("DECODE".equals(name)) {
            return decodeCollation(args);
        }
        final List<Expression> compared = new ArrayList<>();
        if ("GREATEST".equals(name) || "LEAST".equals(name)
                || "GREATEST_IGNORE_NULLS".equals(name) || "LEAST_IGNORE_NULLS".equals(name)) {
            compared.addAll(args);
        } else {
            compared.add(args.get(0));
            if (args.size() > 1) {
                compared.add(args.get(1));
            }
        }
        return settledLeftToRight(compared).toRules();
    }

    /**
     * TRIM, LTRIM and RTRIM over a collated subject accept only WHITESPACE as the characters to trim:
     * any other trim set is a compilation error positioned on the call, whatever the rows are. Live's
     * own sentence, and its rule — the default form and a literal run of spaces are both fine, because
     * a collation cannot say which characters a set names.
     *
     * @param funcName the function's name, upper-cased
     * @param call     the call
     */
    private void rejectCollatedTrimCharacters(final String funcName, final FunctionCallExpression call) {
        if (!"TRIM".equals(funcName) && !"LTRIM".equals(funcName) && !"RTRIM".equals(funcName)) {
            return;
        }
        final List<Expression> args = call.getArguments();
        if (args.size() < 2 || collationOf(args.get(0)).toRules() == null) {
            return;
        }
        if (isWhitespaceOnlyConstant(args.get(1))) {
            return;
        }
        throw arityMismatch("Function " + funcName + " with collations requires whitespace-only"
            + " constant as the trim characters.", call);
    }

    /** Whether an argument is a literal made only of whitespace — the one trim set a collation allows. */
    private static boolean isWhitespaceOnlyConstant(final Expression argument) {
        if (!(argument instanceof LiteralExpression)) {
            return false;
        }
        final Object value = ((LiteralExpression) argument).getValue();
        if (!(value instanceof String)) {
            return false;
        }
        final String text = (String) value;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** DECODE's own comparison collation: its subject and its search values, never its results. */
    private CollationSpec decodeCollation(final List<Expression> args) {
        final List<Expression> compared = new ArrayList<>();
        compared.add(args.get(0));
        for (int i = 1; i + 1 < args.size(); i += 2) {
            compared.add(args.get(i));
        }
        return settledLeftToRight(compared).toRules();
    }

    private ExpressionCollation settledLeftToRight(final List<Expression> operands) {
        ExpressionCollation settled = ExpressionCollation.NONE;
        for (final Expression operand : operands) {
            settled = ExpressionCollation.combine(settled, collationOf(operand));
        }
        return settled;
    }

    private ExpressionCollation settledRightToLeft(final List<Expression> operands) {
        ExpressionCollation settled = ExpressionCollation.NONE;
        for (int i = operands.size() - 1; i >= 0; i--) {
            settled = ExpressionCollation.combine(settled, collationOf(operands.get(i)));
        }
        return settled;
    }

    private static List<Expression> caseResults(final CaseExpression expr) {
        final List<Expression> results = new ArrayList<>();
        for (final WhenClause when : expr.getWhenClauses()) {
            results.add(when.getResult());
        }
        if (expr.getElseExpression() != null) {
            results.add(expr.getElseExpression());
        }
        return results;
    }

    /** A COLLATE call's collation: its specification, unless the operand is a bare NULL, which carries none. */
    private static ExpressionCollation collateCallCollation(final FunctionCallExpression call) {
        final List<Expression> args = call.getArguments();
        if (args.size() != 2 || !(args.get(1) instanceof LiteralExpression)
                || ((LiteralExpression) args.get(1)).getType() != LiteralType.STRING) {
            return ExpressionCollation.NONE;
        }
        if (args.get(0) instanceof LiteralExpression
                && ((LiteralExpression) args.get(0)).getType() == LiteralType.NULL) {
            return ExpressionCollation.NONE;
        }
        return ExpressionCollation.explicit(String.valueOf(((LiteralExpression) args.get(1)).getValue()));
    }

    /**
     * The collation declared on the column {@code ref} names, or null. Resolved the way its value is:
     * a qualified reference through the FROM clause's aliases — a JOIN ON over an 'en-ci' column must
     * compare as the qualifier's table says, not as the joined relation's copy — a bare one through
     * this evaluator's relation and then the joined ones, and the static channel's relation last.
     */
    private String declaredCollation(final ColumnReferenceExpression ref) {
        final String colName = ref.getColumnName();
        if (colName == null) {
            return null;
        }
        if (ref.getTableName() != null && multiTableAliasToTable != null) {
            for (final Map.Entry<String, Table> aliased : multiTableAliasToTable.entrySet()) {
                if (aliased.getKey().equalsIgnoreCase(ref.getTableName())) {
                    return collationIn(aliased.getValue(), colName);
                }
            }
        }
        if (table != null && table.hasColumn(colName)) {
            return collationIn(table, colName);
        }
        if (multiTableAllTables != null) {
            for (final Table joined : multiTableAllTables) {
                if (joined != null && joined.hasColumn(colName)) {
                    return collationIn(joined, colName);
                }
            }
        }
        final Table declared = resolveDeclaredOwner(ref);
        return declared == null ? null : collationIn(declared, colName);
    }

    private static String collationIn(final Table owner, final String colName) {
        for (final TableColumn col : owner.getColumns()) {
            if (col.getName().equals(colName)) {
                return col.getCollation();
            }
        }
        return null;
    }

    /**
     * The collation a comparison of {@code left} with {@code right} runs under, or null for a binary
     * one. Two that disagree are refused naming the RIGHT side's first — '=' , '<', IN, BETWEEN and IS
     * DISTINCT FROM all do (live-verified).
     */
    CollationSpec comparisonCollation(final Expression left, final Expression right) {
        return ExpressionCollation.combine(collationOf(right), collationOf(left)).toRules();
    }

    /**
     * The collation a comparison OPERATOR runs under — {@link #comparisonCollation(Expression,
     * Expression)}, except for the equality a simple CASE builds from its subject and a WHEN value,
     * which names the SUBJECT's collation first: {@code CASE 'a' COLLATE 'en-ci' WHEN 'A' COLLATE 'de'}
     * is refused as "'en-ci' and 'de'" (live-verified).
     */
    private CollationSpec comparisonCollation(final BinaryOperationExpression comparison) {
        if (comparison.isSimpleCaseTest()) {
            return ExpressionCollation.combine(collationOf(comparison.getLeft()),
                collationOf(comparison.getRight())).toRules();
        }
        return comparisonCollation(comparison.getLeft(), comparison.getRight());
    }

    /**
     * The order a comparison takes when either side is a UUID: the other side is READ as a UUID, so
     * letter case does not matter and text that is no UUID is refused in the conversion's own words.
     * Null when neither side is one and the ordinary comparison stands.
     *
     * @param comparison the comparison
     * @param left       the left value
     * @param right      the right value
     * @return the order, or null
     */
    private Integer uuidOrder(final BinaryOperationExpression comparison, final Object left,
                              final Object right) {
        if (!(typeInferencer.infer(comparison.getLeft()) instanceof UuidType)
                && !(typeInferencer.infer(comparison.getRight()) instanceof UuidType)) {
            return null;
        }
        if (!(left instanceof CharSequence) || !(right instanceof CharSequence)) {
            return null;
        }
        return Integer.valueOf(ToUuid.canonical(left.toString())
            .compareTo(ToUuid.canonical(right.toString())));
    }

    /**
     * The order a comparison of two STRINGS takes under the collation its operands carry, or null when
     * they carry none (or either value is no string) and the ordinary comparison stands.
     */
    private Integer collatedOrder(final BinaryOperationExpression comparison, final Object left, final Object right) {
        if (!(left instanceof String) || !(right instanceof String)) {
            return null;
        }
        final CollationSpec rules = comparisonCollation(comparison);
        return rules == null ? null : Integer.valueOf(rules.compare((String) left, (String) right));
    }

    /**
     * The collation a LIKE or ILIKE runs under: the subject's and the pattern's settled, naming the
     * subject's first when they disagree. One that ignores accents or punctuation cannot drive a match
     * and is refused — anchored on the keyword, or nowhere (line 0, position -1) for the NOT spellings.
     */
    CollationSpec likeCollation(final BinaryOperationExpression expr) {
        final CollationSpec rules = ExpressionCollation.combine(collationOf(expr.getLeft()),
            collationOf(expr.getRight())).toRules();
        if (rules == null || rules.supportsLike()) {
            return rules;
        }
        final BinaryOperator op = expr.getOperator();
        final boolean not = op == BinaryOperator.NOT_LIKE || op == BinaryOperator.NOT_ILIKE;
        final String function = op == BinaryOperator.ILIKE || op == BinaryOperator.NOT_ILIKE ? "ILIKE" : "LIKE";
        throw collationRefusal("Function " + function + " does not support collation: " + rules.getText() + ".",
            not ? null : expr.getPosition());
    }

    /**
     * The collation a LIKE ANY, LIKE ALL or ILIKE ANY runs under: the subject's and every pattern's
     * settled together. Only a locale-free collation is supported — 'upper', 'trim' and 'utf8' match,
     * while 'en' and 'en-ci' are refused at the keyword, naming the function internally (live-verified).
     */
    CollationSpec likeAnyAllCollation(final LikeAnyAllExpression expr) {
        ExpressionCollation settled = collationOf(expr.getSubject());
        for (final Expression pattern : expr.getPatterns()) {
            settled = ExpressionCollation.combine(settled, collationOf(pattern));
        }
        final CollationSpec rules = settled.toRules();
        if (rules == null || rules.supportsLikeAnyAll()) {
            return rules;
        }
        final String function = (expr.isCaseInsensitive() ? "ILIKE" : "LIKE") + (expr.isAll() ? "_ALL" : "_ANY");
        throw collationRefusal("Function " + function + " does not support collation.", expr.getPosition());
    }

    private static RuntimeException collationRefusal(final String detail, final SourcePosition written) {
        final SourcePosition resolved = written == null ? null : ExpressionSource.resolve(written);
        final SourcePosition at = resolved == null ? written : resolved;
        return new RuntimeException(at == null ? SqlCompilationError.at(0, -1, detail)
            : SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), detail));
    }

    /**
     * The collation an IN list compares under: the listed values settle first, from the LAST one back,
     * and the subject joins them after — the order live names two that disagree in ({@code IN ('b'
     * COLLATE 'de', 'c' COLLATE 'fr')} is "'fr' and 'de'", and a collated subject comes last). One
     * collated member collates every comparison in the list.
     */
    CollationSpec inListCollation(final InExpression expr) {
        final List<Expression> members = expr.getValues();
        ExpressionCollation settled = ExpressionCollation.NONE;
        for (int i = members.size() - 1; i >= 0; i--) {
            settled = ExpressionCollation.combine(settled, collationOf(members.get(i)));
        }
        return ExpressionCollation.combine(settled, collationOf(expr.getValue())).toRules();
    }

    /**
     * An operator's collation rules, judged at compile time from the operand EXPRESSIONS: two that
     * disagree, and one LIKE cannot match under, are refused while the statement compiles, whatever
     * the rows.
     *
     * @param binary the operator
     */
    void validateOperatorCollations(final BinaryOperationExpression binary) {
        switch (binary.getOperator()) {
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
                comparisonCollation(binary);
                return;
            case LIKE:
            case NOT_LIKE:
            case ILIKE:
            case NOT_ILIKE:
                likeCollation(binary);
                return;
            case CONCAT:
                collationOf(binary);
                return;
            default:
                return;
        }
    }

    /**
     * The same for the predicates the static channel types without looking inside: an IN list, a
     * BETWEEN — its two bounds judged as the two comparisons it is — and LIKE ANY / LIKE ALL.
     *
     * @param predicate the predicate
     */
    void validatePredicateCollations(final Expression predicate) {
        if (predicate instanceof InExpression && !((InExpression) predicate).hasSubquery()) {
            inListCollation((InExpression) predicate);
        } else if (predicate instanceof BetweenExpression) {
            final BetweenExpression between = (BetweenExpression) predicate;
            comparisonCollation(between.getValue(), between.getLower());
            comparisonCollation(between.getValue(), between.getUpper());
        } else if (predicate instanceof LikeAnyAllExpression) {
            likeAnyAllCollation((LikeAnyAllExpression) predicate);
        }
    }

    /**
     * The collation rules alone, walked over a JOIN's ON condition before any pair of rows is read:
     * AND, OR and NOT are descended, and every comparison, LIKE, IN list and BETWEEN beneath them
     * settles its operands' collations — so two that disagree are refused over tables with no rows, as
     * they are live, rather than surfacing only when a pair happens to be compared.
     *
     * @param expr the condition
     */
    public void validateCollations(final Expression expr) {
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            validateOperatorCollations(binary);
            if (binary.getOperator() == BinaryOperator.AND || binary.getOperator() == BinaryOperator.OR) {
                validateCollations(binary.getLeft());
                validateCollations(binary.getRight());
            }
        } else if (expr instanceof UnaryOperationExpression) {
            validateCollations(((UnaryOperationExpression) expr).getOperand());
        } else {
            validatePredicateCollations(expr);
        }
    }

    /**
     * COLLATE's type, judged at compile time: the operand's own, and it must be a string — anything
     * else is refused naming the operand from the plan ("argument needs to be a string: 'CP.N'"), and
     * BEFORE the specification is looked at (live refuses COLLATE(1, 'xx-yy') for the 1). Then the
     * specification itself, whose refusal is its own sentence.
     *
     * @param call the COLLATE call
     * @return the operand's type, or null when it is untyped
     */
    DataType collateResultType(final FunctionCallExpression call) {
        final Expression operand = call.getArguments().get(0);
        final DataType operandType = typeInferencer.infer(operand);
        if (operandType != null && !(operandType instanceof StringType)) {
            // Named from the plan, a BINARY literal in its written X'..' form (live: 'X'41'').
            final boolean binaryLiteral = operand instanceof LiteralExpression
                && ((LiteralExpression) operand).getType() == LiteralType.BINARY;
            throw new RuntimeException(SqlCompilationError.of("argument needs to be a string: '"
                + (binaryLiteral ? "X'" + strictText(operand) + "'" : strictText(operand)) + "'"));
        }
        final Expression spec = call.getArguments().get(1);
        if (spec instanceof LiteralExpression && ((LiteralExpression) spec).getValue() != null) {
            CollationSpec.parse(String.valueOf(((LiteralExpression) spec).getValue()));
        }
        return operandType;
    }

    private int compare(final Object left, final Object right) {
        return ExpressionArithmetic.compare(left, right);
    }

    private Object negate(final Number value) {
        return ExpressionArithmetic.negate(value);
    }

    /** The LIKE ESCAPE character: the first char of the evaluated ESCAPE expression, else the default backslash. */
    private Character resolveEscapeChar(final Expression escape) {
        if (escape == null) {
            return Character.valueOf('\\');
        }
        final Object value = escape.accept(this);
        if (value == null) {
            return null;
        }
        final String written = value.toString();
        if (written.length() != 1) {
            // An escape is ONE character: the empty string and any longer run are refused by value,
            // named as a parameter rather than as a syntax fault (live-verified).
            throw new RuntimeException(SqlCompilationError.of(
                "invalid value ['" + written + "'] for parameter 'escape'"));
        }
        return Character.valueOf(written.charAt(0));
    }

    private Object evaluateLike(final Object value, final Object pattern,
                                final BinaryOperator op, final char escapeChar) {
        return LikeMatcher.evaluateLike(value, pattern, op, escapeChar);
    }

    /**
     * LIKE and ILIKE under the collation their operands carry: both sides trimmed and case-converted as
     * the rules say, and matched case-insensitively when the collation ignores case.
     */
    private Object evaluateCollatedLike(final BinaryOperationExpression expr, final Object left,
                                        final Object right) {
        final Character escapeChar = resolveEscapeChar(expr.getEscape());
        if (escapeChar == null) {
            // ESCAPE NULL makes the whole predicate UNKNOWN, whichever way it would have matched.
            return null;
        }
        final CollationSpec rules = likeCollation(expr);
        if (rules == null || !(left instanceof String) || !(right instanceof String)) {
            return evaluateLike(left, right, expr.getOperator(), escapeChar.charValue());
        }
        return evaluateLike(rules.likeOperand((String) left), rules.likeOperand((String) right),
            expr.getOperator(), escapeChar.charValue());
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

    /**
     * The node a value takes inside an object or array literal, built from the VALUE as the constructor
     * functions build theirs, never re-read from text: a FLOAT stays a DOUBLE ({@code [1.5::FLOAT][0]} is
     * DOUBLE live), a temporal keeps its type, and a string literal stays a string however much it looks
     * like JSON (live {@code ['[1,2]']} is {@code ["[1,2]"]} and {@code ['null']} is {@code ["null"]}).
     * Only a computed string the engine carries as JSON text is read back as the structure it holds.
     */
    private JsonNode jsonElementNode(final Expression element, final Object value) {
        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        if (value instanceof String) {
            if (element instanceof LiteralExpression) {
                return ArrayFunctionHelper.MAPPER.getNodeFactory().textNode((String) value);
            }
            final String structure = jsonStructureOrNull((String) value);
            final JsonNode nested = structure == null ? null : ArrayFunctionHelper.parseNode(structure);
            if (nested != null) {
                return nested;
            }
        }
        return ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value);
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

    private Object evaluateExists(final SubqueryExpression subquery) {
        return subqueryEvaluator.evaluateExists(subquery);
    }

    private Object evaluateScalarSubquery(final SubqueryExpression expr) {
        return subqueryEvaluator.evaluateScalarSubquery(expr);
    }

    private Object evaluateInSubquery(final Object value, final SubqueryExpression subquery, final boolean not,
                                      final CollationSpec collation) {
        return subqueryEvaluator.evaluateInSubquery(value, subquery, not, collation);
    }

    private List<ResultSet> executeSubquery(final String subquery) {
        return subqueryEvaluator.executeSubquery(subquery);
    }

    private Boolean compareWithOperator(final Object left, final Object right,
                                        final BinaryOperator operator) {
        return ExpressionArithmetic.compareWithOperator(left, right, operator);
    }

    /** The same comparison, ordered by a collation when one is settled and both sides are text. */
    private Boolean compareWithOperator(final Object left, final Object right,
                                        final BinaryOperator operator, final CollationSpec collation) {
        if (collation == null || !(left instanceof String) || !(right instanceof String)) {
            return compareWithOperator(left, right, operator);
        }
        final int order = collation.compare((String) left, (String) right);
        switch (operator) {
            case EQUAL: return Boolean.valueOf(order == 0);
            case NOT_EQUAL: return Boolean.valueOf(order != 0);
            case LESS_THAN: return Boolean.valueOf(order < 0);
            case LESS_THAN_OR_EQUAL: return Boolean.valueOf(order <= 0);
            case GREATER_THAN: return Boolean.valueOf(order > 0);
            case GREATER_THAN_OR_EQUAL: return Boolean.valueOf(order >= 0);
            default: return compareWithOperator(left, right, operator);
        }
    }

    /**
     * A comparison operand as the comparison must SEE it, restoring the one piece of type the value
     * itself no longer carries: a VARIANT holding a number is handed back from a path as a bare
     * number, and a bare number beside a string COERCES that string, where a VARIANT beside a string
     * compares as TEXT. The two answer differently, live-verified —
     *
     * <pre>
     *   src:score = '7.50'   FALSE, because "7.5" is not the text "7.50" (coerced, it would be true)
     *   src:score &gt; '10'     TRUE, because "7.5" sorts after "10"     (coerced, it would be false)
     *   src:score = 'abc'    FALSE, where a coerced number RAISES on the unreadable text
     * </pre>
     *
     * <p>Only this exact shape is restored — a bare number, standing against a string, whose
     * expression is statically VARIANT. A variant STRING member already arrives as text and compares
     * correctly, and a variant beside a NUMBER already compares numerically, so neither is touched.
     * The wrapper is built for the call and discarded with it, so no value downstream ever sees it.
     */
    /**
     * A WHOLE-DAY unit asked of a TIME. A time of day carries no date, so there is nothing to add a
     * day to, difference in days, or truncate to a month — the account refuses all of them, while the
     * sub-day units answer normally.
     *
     * <p>It is checked HERE, in the plan-time walk, and not where the value is read: a refusal raised
     * while evaluating never fires over ZERO ROWS, so an empty table would accept the statement and a
     * view over it would be created. That is the standing lesson of the data-dependent refusals.
     *
     * <p>Three sentences, and the differences are measured rather than tidied: the ADD family quotes
     * the unit and names the type WITH its precision, the DIFF and TRUNCATE family does neither, and
     * DATE_PART speaks about its parameter instead.
     *
     * <pre>
     *   DATEADD(day, 1, tm)    ['DAY'] is not a valid date/time component for function DATEADD and type TIME(9).
     *   DATEDIFF(day, tm, tm)  [DAY] is not a valid date/time component for function DATEDIFF and type TIME.
     *   DATE_PART(day, tm)     invalid value [DAY] for parameter 'DATE_PART date/time part'
     * </pre>
     */
    private void rejectWholeDayUnitOverTime(final String funcName, final List<Expression> args) {
        final int unitAt = "TRUNC".equals(funcName) ? 1 : 0;
        final int valueAt = dateUnitValueArgument(funcName);
        if (valueAt < 0 || args.size() <= Math.max(unitAt, valueAt)) {
            return;
        }
        final String unitWord = staticUnitWord(args.get(unitAt));
        final IntervalUnit unit = IntervalUnit.fromSpelling(unitWord);
        if (unit == null || !unit.isWholeDay()
                || !(typeInferencer.infer(args.get(valueAt)) instanceof DateTimeType)) {
            return;
        }
        final DateTimeType valueType = (DateTimeType) typeInferencer.infer(args.get(valueAt));
        if (!"TIME".equalsIgnoreCase(valueType.getName())) {
            return;
        }
        final String shown = unitWord.toUpperCase(Locale.ROOT);
        if ("DATE_PART".equals(funcName)) {
            throw new RuntimeException(SqlCompilationError.of("invalid value [" + shown
                + "] for parameter 'DATE_PART date/time part'"));
        }
        final boolean addFamily = "DATEADD".equals(funcName) || "TIMEADD".equals(funcName)
            || "TIMESTAMPADD".equals(funcName);
        // One line after the prefix. The ADD family quotes a string-literal unit as written — ['Day'] —
        // and names the TIME with its own precision, TIME(3); the others upper-case the unit and name
        // the bare type.
        final boolean writtenAsString = args.get(unitAt) instanceof LiteralExpression
            && ((LiteralExpression) args.get(unitAt)).getType() == LiteralType.STRING;
        throw new RuntimeException(SqlCompilationError.inline(
            (addFamily ? "['" + (writtenAsString ? unitWord : shown) + "'] " : "[" + shown + "] ")
                + "is not a valid date/time component for function " + funcName
                + " and type " + (addFamily ? strictArgTypeText(args.get(valueAt)) : "TIME") + "."));
    }

    /** Which argument carries the VALUE a date/time unit is applied to, or -1 for other functions. */
    private int dateUnitValueArgument(final String funcName) {
        switch (funcName) {
            case "DATEADD": case "TIMEADD": case "TIMESTAMPADD":
                return 2;
            case "DATEDIFF": case "TIMEDIFF": case "TIMESTAMPDIFF":
            case "DATE_TRUNC": case "DATE_PART":
                return 1;
            case "TRUNC":
                return 0;
            default:
                return -1;
        }
    }

    /** The unit word a call names, when it is written plainly enough to read before any row exists. */
    private String staticUnitWord(final Expression arg) {
        if (arg instanceof ColumnReferenceExpression) {
            return ((ColumnReferenceExpression) arg).getColumnName();
        }
        if (arg instanceof LiteralExpression && ((LiteralExpression) arg).getValue() != null) {
            return String.valueOf(((LiteralExpression) arg).getValue());
        }
        return "";
    }

    /**
     * An arithmetic operand that CAME FROM a VARIANT, restored to the FLOAT that Snowflake computes
     * with. Arithmetic touching a VARIANT is float on live whatever the member holds — measured across
     * the five operators, both member kinds and both operand orders:
     *
     * <pre>
     *   src:n + 2   9.0     src:n - 2   5.0     src:n * 2   14.0     src:n % 2   1.0
     * </pre>
     *
     * <p>A DECIMAL member already floated, because it still IS a VariantValue when the operator sees
     * it and {@code coerceVariantOperands} takes that path. An INTEGER member arrives extracted, as a
     * plain Long, so the {@code instanceof Number} fast path computed it exactly and answered 14 where
     * live answers 14.0. The value alone cannot tell the two apart — a Long from a VARIANT and a Long
     * from an INT column are the same object — so the VARIANT-ness is read from the STATIC type, the
     * same channel {@link #comparisonOperand} uses to restore a variant for a comparison.
     *
     * @param expr  the operand's expression, whose static type says where the value came from
     * @param value the evaluated operand
     * @return the operand as a Double when it came from a VARIANT, else unchanged
     */
    /**
     * A text operand of an arithmetic operator read the way live reads it: a column or an expression
     * beside an exact number as NUMBER(18,5) — {@code t + 0} answers {@code 5.00000} and
     * {@code '1.123456' + 0} 1.12346 — with the reading's own refusals; a text literal keeps its own
     * scale, and a text beside a text, a FLOAT, a VARIANT or a bare NULL is left to the FLOAT rule
     * (see {@link ImpliedTextNumber}). The numeric operators only: a comparison reads text as text.
     */
    private Object textArithmeticOperand(final Expression expr, final Object value, final Expression other,
                                         final Object otherValue) {
        if (!(value instanceof CharSequence) || ImpliedTextNumber.isTextLiteral(expr)
                || !(otherValue instanceof Number) || otherValue instanceof Double || otherValue instanceof Float) {
            return value;
        }
        if (!(typeInferencer.infer(expr) instanceof StringType)) {
            return value;
        }
        final DataType otherType = typeInferencer.infer(other);
        if (otherType instanceof VariantType || NumericType.isApproximate(otherType)) {
            return value;
        }
        return ImpliedTextNumber.read((CharSequence) value);
    }

    private Object arithmeticOperand(final Expression expr, final Object value) {
        if (!(value instanceof Number) || value instanceof Double) {
            return value;
        }
        // An APPROXIMATE operand has to ARRIVE as a double, because the arithmetic decides which rule
        // to use from the runtime class and a FLOAT-declared value can still be carried exactly — an
        // expression computed exactly, or a value restored from an older snapshot. Without this a
        // FLOAT divided by a FLOAT took the fixed-point path and came back at seven digits —
        // 1.0/3.0 was 0.3333333 where both engines' own FLOAT division carries far more.
        final DataType operand = typeInferencer.infer(expr);
        return operand instanceof VariantType || NumericType.isApproximate(operand)
            ? Double.valueOf(((Number) value).doubleValue()) : value;
    }

    private Object comparisonOperand(final Expression expr, final Object value, final Object other) {
        if (!(value instanceof Number) || !(other instanceof CharSequence)) {
            return value;
        }
        return typeInferencer.infer(expr) instanceof VariantType
            ? VariantValue.of(String.valueOf(value)) : value;
    }


    /**
     * An argument whose DECLARED type is approximate arrives as a double, whatever class it was carried
     * in. A FLOAT column holds a double, but a FLOAT-declared value can still reach a function in an
     * exact carrier — computed exactly, or restored from an older snapshot — and a function reading one
     * saw an exact number and rendered it that way: TO_VARCHAR over a stored 3.0 was "3.0" where live
     * says "3", while TO_VARCHAR over a COMPUTED 3.0 was already right — the formatter was never wrong,
     * only what reached it.
     *
     * <p>Decided from the STATIC type and not from the runtime class, for the reason that keeps
     * recurring here: a BigDecimal carried for a FLOAT and a BigDecimal from a NUMBER column are the
     * same object, and only the declared type tells them apart. Same channel as
     * {@link #arithmeticOperand}.
     */
    /** A concatenation operand, with an approximate one restored to the double it renders as. */
    private Object concatOperand(final Expression expr, final Object value) {
        return value instanceof Number && !ApproximateValues.isApproximate(value)
            && NumericType.isApproximate(typeInferencer.infer(expr))
            ? Double.valueOf(((Number) value).doubleValue()) : value;
    }

    private void coerceApproximateArguments(final List<Expression> args,
                                            final List<Object> argValues) {
        if (args.size() != argValues.size()) {
            return;
        }
        for (int i = 0; i < args.size(); i++) {
            final Object value = argValues.get(i);
            if (!(value instanceof Number) || ApproximateValues.isApproximate(value)) {
                continue;
            }
            if (NumericType.isApproximate(typeInferencer.infer(args.get(i)))) {
                argValues.set(i, Double.valueOf(((Number) value).doubleValue()));
            }
        }
    }

    /**
     * A numeric function's TEXT arguments read as numbers, the way live converts them at row time —
     * trimmed, in every spelling a number literal has ({@code ' 5 '}, {@code '1e2'}, {@code '+5'},
     * {@code '5.'}, {@code '.5'}). A text that reads as no number stays as written, for
     * {@link #unreadableTextArgument} to name once the function fails on it; a text the function takes
     * AS text (ROUND's rounding mode) reads as no number and reaches it unchanged. Every member of
     * the family converts, not a listed few: ABS, CEIL, FLOOR, ROUND, TRUNC, SIGN, MOD, SQRT, POWER,
     * EXP, LN, LOG, CBRT, FACTORIAL, SIN, DEGREES, SQUARE, DIV0, WIDTH_BUCKET and the BIT family were
     * each measured to take '5' where they took 5.
     */
    private void coerceNumericTextArguments(final String funcName, final Expression call,
                                            final List<Object> argValues) {
        final List<Expression> arguments = call instanceof FunctionCallExpression
            ? ((FunctionCallExpression) call).getArguments() : null;
        for (int i = 0; i < argValues.size(); i++) {
            final Object value = argValues.get(i);
            if (!(value instanceof CharSequence)) {
                continue;
            }
            if (readsTextAtImpliedScale(funcName, i, argValues.size()) && arguments != null
                    && arguments.size() == argValues.size() && !ImpliedTextNumber.isTextLiteral(arguments.get(i))
                    && typeInferencer.infer(arguments.get(i)) instanceof StringType) {
                // The function forms of the operators, and the rounding family given a scale, read a
                // text column as the operators do — NUMBER(18,5): MOD(t, 2) is 1.00000, DIV0(t, 2)
                // 2.50000000000 and ROUND(t, 7) 5.00000 on the account, where a one-argument
                // ROUND(t) or ABS(t) is a FLOAT read at its full digits.
                argValues.set(i, ImpliedTextNumber.read((CharSequence) value));
                continue;
            }
            final Number read = ExpressionArithmetic.asNumber(value);
            if (read != null) {
                argValues.set(i, read);
            }
        }
    }

    /**
     * A VARIANT argument of a numeric function, read as the double live's implicit cast to REAL makes of
     * it: a JSON number as itself, a JSON boolean as 1 / 0 and a string spelling a number as that number
     * (live: SQRT over a variant 7 is 2.645751311, ABS over true is 1 and over "12" is 12, all
     * FLOAT-typed); an object, an array or other text fails its cast to REAL (live: ABS, ROUND, FLOOR,
     * SQRT, MOD … over {"x":1}).
     */
    private static void coerceNumericVariantArguments(final List<Object> argValues) {
        for (int i = 0; i < argValues.size(); i++) {
            if (!(argValues.get(i) instanceof VariantValue)) {
                continue;
            }
            final VariantValue variant = (VariantValue) argValues.get(i);
            final JsonNode node = variant.node();
            if (node == null || node.isNull()) {
                continue;
            }
            final Number read = VariantNumbers.numberOf(variant, VariantNumbers.REAL);
            argValues.set(i, Double.valueOf(read.doubleValue()));
        }
    }

    /** Whether a text at an argument position reads as NUMBER(18,5) rather than at its own digits. */
    private static boolean readsTextAtImpliedScale(final String funcName, final int position, final int arity) {
        if (funcName.equals("MOD") || funcName.equals("DIV0") || funcName.equals("DIV0NULL")) {
            return true;
        }
        return position == 0 && arity >= 2 && isRoundingFamily(funcName);
    }

    /**
     * Live's row-time sentence for the first argument, in any position, that is a text reading as no
     * number — "Numeric value 'x' is not recognized", the text echoed TRIMMED ({@code ABS(' x ')}
     * names x; {@code ''} and {@code '0x10'} are echoed as written) — or null when no argument is one.
     */
    private String unreadableTextArgument(final List<Object> argValues) {
        for (final Object value : argValues) {
            if (value instanceof CharSequence && ExpressionArithmetic.asNumber(value) == null) {
                return NumericRangeRefusal.unreadableText(value.toString().trim());
            }
        }
        return null;
    }

    /**
     * The same restoration for a function that is DEFINED as a comparison. DECODE compares its
     * subject against each search value, so a variant subject must reach that comparison as a VARIANT
     * — {@code DECODE(src:score, '7.50', …)} does NOT match, because "7.5" is not the text "7.50".
     *
     * <p>Only the arguments that are COMPARED are restored, never one that can be returned. For
     * DECODE that means the subject and the search values but not a result branch or the default:
     * wrapping one of those would change the type of the function's answer. A search sits at an odd
     * index that still has a result after it; the trailing argument of an even-length call is the
     * default, not a search. NULLIF compares both of its arguments, and its answer is a VARIANT when
     * its first argument is one, so both are restored. An argument list carrying a spread is left
     * alone, since its values no longer line up with the written arguments.
     */
    private void applyVariantComparisonOperands(final String funcName,
                                                final List<Expression> args,
                                                final List<Object> argValues) {
        final boolean isDecode = "DECODE".equals(funcName);
        final boolean isNullIf = "NULLIF".equals(funcName) && args.size() == 2;
        if (!isDecode && !isNullIf || args.size() != argValues.size()) {
            return;
        }
        for (final Expression arg : args) {
            if (arg instanceof SpreadExpression) {
                return;
            }
        }
        for (int i = 0; i < args.size(); i++) {
            final boolean compared = isNullIf || i == 0 || i % 2 == 1 && i + 1 < args.size();
            if (compared && argValues.get(i) instanceof Number
                    && typeInferencer.infer(args.get(i)) instanceof VariantType) {
                argValues.set(i, VariantValue.of(String.valueOf(argValues.get(i))));
            }
        }
    }
}
