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

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.SubqueryMemo;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.values.ValueRange;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates expressions against rows.
 *
 * <p>Expressions are parsed once into an {@link Expression} AST (via the ANTLR grammar and
 * {@link dev.frostlake.executor.expressions.ExpressionAstBuilder}) and evaluated by
 * {@link ExpressionEvaluatorVisitor}. Parse once with {@link #parse(String)} and call
 * {@link #evaluate(Expression, Row)} per row to avoid re-parsing.
 */
public class ExpressionEvaluator {

    private final Table table;
    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;
    private QueryExecutor queryExecutor;
    private Map<String, Object> outerLateralContext;
    private boolean scopeOpaqueToSubqueries;
    private Map<String, Table> multiTableAliasToTable;
    private List<Table> multiTableAllTables;
    private Table declaredTypeBase;
    private Map<String, Table> declaredTypeAliasToTable;
    private List<Table> declaredTypeAllTables;
    private Set<String> scopeExemptNames;
    private Set<String> whereAggregateAliases;
    private Map<String, String> whereWindowAliases;
    private Set<String> outputScopeNames;
    private Map<String, DataType> outputAliasTypes;
    private Map<String, Object> resultContext;
    private Map<String, Object> subqueryOuterRow;
    private Map<String, DataType> scriptNameTypes;
    private boolean bindsAsParameters;
    // One memo per evaluator instance (= one outer query's row loop). Evaluators are created per
    // operator-execution and never pooled, so cached uncorrelated-subquery results never leak across
    // queries. See SubqueryMemo.
    private final SubqueryMemo subqueryMemo = new SubqueryMemo();
    // Reused across evaluate() calls (single-threaded per query) so each row doesn't allocate a visitor.
    private ExpressionEvaluatorVisitor reusableVisitor;

    /**
     * Cache of parsed expression ASTs, keyed by (comment-stripped) expression text. An AST is
     * immutable and independent of row/catalog, so it is safe to share across evaluators and
     * threads. This offsets the per-row re-parse until operators carry the parsed AST directly.
     * Bounded (LRU + max-size guard) so sweeping distinct-literal predicates can't grow it without
     * limit; a genuinely hot expression accessed every row stays cached. See BoundedParseCache.
     * The key cap is deliberately looser than the statement-level caches': an expression over the
     * cap here would re-parse PER ROW (a large correlated subquery text is the common case), where
     * an over-cap statement re-parses once per execution.
     */
    private static final BoundedParseCache<Expression> AST_CACHE =
        new BoundedParseCache<>(2048, 16384);

    public ExpressionEvaluator(final Table table, final FunctionRegistry functionRegistry, final Catalog catalog) {
        this.table = table;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
        this.queryExecutor = null;
        this.outerLateralContext = null;
    }

    public ExpressionEvaluator(final Table table, final FunctionRegistry functionRegistry, final Catalog catalog, final QueryExecutor queryExecutor) {
        this.table = table;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
        this.queryExecutor = queryExecutor;
        this.outerLateralContext = null;
    }

    public void setQueryExecutor(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    public void setOuterLateralContext(final Map<String, Object> context) {
        this.outerLateralContext = context;
    }

    /**
     * Declares this evaluator's relation a PROJECTION SCOPE — a FROM-less select's own item list —
     * which answers the clauses of that select and nothing nested inside them, so a subquery written
     * in one of those clauses does not correlate to it.
     *
     * @param opaque whether the relation is such a scope
     */
    public void setScopeOpaqueToSubqueries(final boolean opaque) {
        this.scopeOpaqueToSubqueries = opaque;
    }

    /**
     * Provide a multi-table (JOIN) resolution context so a parsed Expression can be evaluated
     * directly over joined rows with alias-aware column resolution. Set once per query.
     */
    public void setMultiTableContext(final Map<String, Table> aliasToTable,
                                     final List<Table> allTables) {
        this.multiTableAliasToTable = aliasToTable;
        this.multiTableAllTables = allTables;
    }

    /**
     * Provide precomputed SELECT-list outputs (HAVING / QUALIFY), keyed by canonical AST form and
     * alias, so a condition referencing an aggregate/window/alias resolves to the computed value.
     */
    public void setResultContext(final Map<String, Object> resultContext) {
        this.resultContext = resultContext;
    }

    /**
     * The row a subquery evaluated here reads as its outer row when that is not the row being evaluated: a
     * HAVING condition runs over the grouped output row, while its subquery reads the group's own row
     * ({@code HAVING (SELECT COUNT(*) FROM g WHERE g.id = fz.id) = 0}, live-verified). Offered to subqueries
     * only; the condition's own names resolve as before.
     *
     * @param bindings the outer row's names and values, as {@link #rowBindings} builds them, or null
     */
    public void setSubqueryOuterRow(final Map<String, Object> bindings) {
        this.subqueryOuterRow = bindings;
    }

    /**
     * The names a subquery evaluated over {@code row} reads that row by: its columns bare, under their
     * relation's name, and under each FROM alias.
     *
     * @param row a row of this evaluator's relation
     * @return the bindings, for {@link #setSubqueryOuterRow}
     */
    public Map<String, Object> rowBindings(final Row row) {
        final ExpressionEvaluatorVisitor visitor = preparedVisitor();
        visitor.setRow(row);
        return visitor.rowBindings();
    }

    /**
     * The relation a reference's DECLARED type is read from when {@code table} cannot say — see
     * {@link ExpressionEvaluatorVisitor#setDeclaredTypeBase}.
     *
     * @param base         the base relation, or null
     * @param aliasToTable its alias map, or null
     * @param allTables    its joined relations, or null
     */
    public void setDeclaredTypeBase(final Table base, final Map<String, Table> aliasToTable,
                                    final List<Table> allTables) {
        this.declaredTypeBase = base;
        this.declaredTypeAliasToTable = aliasToTable;
        this.declaredTypeAllTables = allTables;
    }

    /**
     * Evaluate an expression AST against a row (RECOMMENDED)
     *
     * This method is more efficient than string-based evaluation because the expression
     * is already parsed. Use this when you need to evaluate the same expression multiple times.
     *
     * @param expression The parsed expression AST
     * @param row The row to evaluate against
     * @return The result of the expression evaluation
     */
    public Object evaluate(final Expression expression, final Row row) {
        // Reuse one visitor across rows (this evaluator is single-threaded per query and never re-enters
        // its own evaluate), updating only the row — avoids allocating a visitor for every row evaluated.
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, row, functionRegistry, catalog);
        }
        final ExpressionEvaluatorVisitor visitor = reusableVisitor;
        visitor.setRow(row);
        visitor.setQueryExecutor(queryExecutor);
        visitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        visitor.setLateralContext(outerLateralContext);
        visitor.setScopeOpaqueToSubqueries(scopeOpaqueToSubqueries);
        if (multiTableAllTables != null) {
            visitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        if (resultContext != null) {
            visitor.setResultContext(resultContext);
        }
        visitor.setSubqueryOuterRow(subqueryOuterRow);
        visitor.setSubqueryMemo(subqueryMemo);
        visitor.setScriptNameTypes(scriptNameTypes);
        visitor.setBindsAsParameters(bindsAsParameters);
        return expression.accept(visitor);
    }

    /**
     * The names a scripting expression reads and their declared types. SYSTEM$TYPEOF types its argument over
     * them, each a parameter of its type: live answers {@code NUMBER(38,0)[SB16]} for
     * {@code RETURN SYSTEM$TYPEOF(:x)} over {@code LET x := 1}, where the same call inside a statement reads
     * the bound value, {@code [SB1]}.
     *
     * @param types the declared types by name, or null in a SQL statement
     */
    public void setScriptNameTypes(final Map<String, DataType> types) {
        this.scriptNameTypes = types;
    }

    /**
     * Whether a bind variable reads as a parameter of its declared type — no interval, a text without a width
     * of its own — rather than as the value a statement binds.
     *
     * @param parameters true to read binds as parameters
     */
    public void setBindsAsParameters(final boolean parameters) {
        this.bindsAsParameters = parameters;
    }

    /**
     * What SYSTEM$TYPEOF answers for {@code expression} in this evaluator's scope, without evaluating it.
     *
     * @param expression the argument
     * @return the description
     */
    public String describeTypeOf(final Expression expression) {
        return preparedVisitor().describeTypeOf(expression);
    }

    /**
     * Evaluate a string expression against a row: parse it to an AST (cached) and evaluate via
     * {@link ExpressionEvaluatorVisitor}. For repeated evaluation of the same text, prefer
     * {@link #parse(String)} once + {@link #evaluate(Expression, Row)} per row.
     *
     * @param expression the expression as a string
     * @param row the row to evaluate against
     * @return the result of the expression evaluation
     */
    public Object evaluate(final String expression, final Row row) {
        return evaluate(parse(expression), row);
    }

    /**
     * Whether this evaluator's relations settle a condition over their statistics: TRUE on every row,
     * FALSE on every row, or null where the statistics leave it open.
     *
     * @param predicate the parsed condition
     * @return the settled value, or null
     */
    public Boolean settledCondition(final Expression predicate) {
        return preparedVisitor().settledCondition(predicate);
    }

    /**
     * Plan-time strict-argument validation over a parsed expression: runs the Snowflake
     * argument-type checks for every function call in {@code expression} WITHOUT evaluating it,
     * so rejection fires even over zero input rows.
     */
    public void validateStrict(final Expression expression) {
        preparedVisitor().validateStrictArgumentsWithRows(expression);
    }

    /**
     * Compile every subquery {@code expression} holds in this evaluator's scope, before any row: see
     * {@link ExpressionEvaluatorVisitor#compileSubqueries}.
     *
     * @param expression     the parsed expression
     * @param enclosingNames the names of the query around this scope's query when that one is compiling, or null
     * @return the first refusal that waits for the statement around the subqueries, or null
     */
    public RuntimeException compileSubqueries(final Expression expression, final Map<String, Object> enclosingNames) {
        return preparedVisitor().compileSubqueries(expression, enclosingNames);
    }

    /** Compile one subquery in this evaluator's scope, as {@link #compileSubqueries} compiles each. */
    public RuntimeException compileSubquery(final SubqueryExpression subquery, final Map<String, Object> enclosingNames) {
        return preparedVisitor().compileSubquery(subquery, enclosingNames);
    }

    /**
     * The ROW type of an expression that is a subquery selecting more than one column, as a type match names
     * it: {@code ROW(NUMBER(1,0), VARCHAR(1))}; null for anything else.
     */
    public String multiColumnRowText(final Expression expression) {
        return expression instanceof SubqueryExpression
            ? preparedVisitor().multiColumnRowText((SubqueryExpression) expression) : null;
    }

    /** The plan-time user-defined function argument check alone, over a parsed expression. */
    public void validateUdfArguments(final Expression expression) {
        preparedVisitor().validateUdfArguments(expression);
    }

    /**
     * PHASE ONE of the plan-time walk: every column reference, and nothing else. Live orders its
     * refusals by KIND rather than by position — an invalid identifier beats an unknown function name,
     * which beats every argument-type complaint, whichever item each of them sits in — so the select
     * list is walked once per kind.
     */
    public void validateColumnScope(final Expression expression) {
        preparedVisitor().validateColumnScopeOnly(expression);
    }

    /**
     * A filter's predicate with the narrowing casts live answers without converting dropped: see
     * {@link ExpressionEvaluatorVisitor#withNarrowingCastEqualitiesAnswered}.
     *
     * @param predicate the parsed predicate of a WHERE or an ON
     * @return the predicate to evaluate
     */
    public Expression withNarrowingCastEqualitiesAnswered(final Expression predicate) {
        return preparedVisitor().withNarrowingCastEqualitiesAnswered(predicate);
    }

    /** PHASE TWO: every call's NAME, and nothing else. See {@link #validateColumnScope}. */
    public void validateFunctionNames(final Expression expression) {
        preparedVisitor().validateFunctionNamesOnly(expression);
    }

    /**
     * Every call's written shape — a quantifier, a WITHIN GROUP or named arguments it does not take — and nothing
     * else, refused ahead of every argument type: see {@link ExpressionEvaluatorVisitor#validateCallShapesOnly}.
     *
     * @param expression the parsed expression
     */
    public void validateCallShapes(final Expression expression) {
        preparedVisitor().validateCallShapesOnly(expression);
    }

    /**
     * PHASE TWO as a QUESTION rather than a refusal. {@link #validateFunctionNames} stops at the first
     * unresolvable name, which is right for one expression; a whole statement's refusal names every one
     * of them at once, so the caller asks this per call and refuses after.
     *
     * @param expression one parsed call — anything that is not a call answers true
     * @return whether the name resolves
     */
    public boolean resolvesToAFunctionName(final Expression expression) {
        return !(expression instanceof FunctionCallExpression)
            || preparedVisitor().resolvesToAFunction((FunctionCallExpression) expression);
    }

    /** Live's sentence for a set of unresolvable calls — see the visitor's own documentation. */
    public String unknownFunctionSentence(final List<FunctionCallExpression> calls) {
        return preparedVisitor().unknownFunctionSentence(calls);
    }

    /**
     * The collation an ORDER BY, GROUP BY, DISTINCT, PARTITION BY or set-operation key compares under.
     * A key with none compares by code point, as it always did.
     *
     * @param expression the key expression
     * @return its collation rules, or null when it carries none
     */
    public CollationSpec keyCollation(final Expression expression) {
        return preparedVisitor().collationOf(expression).toRules();
    }

    /**
     * The visitor that types names in this evaluator's scope, for naming that scope as the one AROUND a
     * relation planned inside it (see {@link SubqueryCompilation#beginScope}).
     *
     * @return the prepared visitor
     */
    public ExpressionEvaluatorVisitor scopeVisitor() {
        return preparedVisitor();
    }

    /** The reusable visitor with this evaluator's context applied — shared by every phase. */
    private ExpressionEvaluatorVisitor preparedVisitor() {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        reusableVisitor.setScopeOpaqueToSubqueries(scopeOpaqueToSubqueries);
        reusableVisitor.setScopeExemptNames(scopeExemptNames);
        reusableVisitor.setWhereAliasRefusals(whereAggregateAliases, whereWindowAliases);
        reusableVisitor.setOutputScopeNames(outputScopeNames);
        reusableVisitor.setOutputAliasTypes(outputAliasTypes);
        reusableVisitor.setScriptNameTypes(scriptNameTypes);
        reusableVisitor.setBindsAsParameters(bindsAsParameters);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        return reusableVisitor;
    }

    /**
     * The select aliases a WHERE may not read, for the scope walk to refuse in live's words: an alias of
     * an item holding an aggregate, and an alias of a window call with the sentence that names it.
     *
     * @param aggregateAliases the canonical aliases of aggregate items, or null
     * @param windowAliases    each window item's canonical alias, with its refusal sentence, or null
     */
    public void setWhereAliasRefusals(final Set<String> aggregateAliases, final Map<String, String> windowAliases) {
        this.whereAggregateAliases = aggregateAliases;
        this.whereWindowAliases = windowAliases;
    }

    /** Bare names the plan-time scope walk must not reject — the query's SELECT output aliases /
     *  output column names, legal in every non-SELECT clause (WHERE included, live-verified). */
    public void setScopeExemptNames(final Set<String> names) {
        this.scopeExemptNames = names;
    }

    /**
     * The names a refusal's echo prints BARE, because the expression it re-prints resolves in the
     * query's OUTPUT scope rather than against the FROM — a projected column has no relation to name.
     *
     * @param names the output column names, upper-cased
     */
    public void setOutputScopeNames(final Set<String> names) {
        this.outputScopeNames = names;
    }

    /**
     * The static types of the query's SELECT output aliases, so a bare alias in a predicate is typed
     * from the expression it names.
     *
     * @param types alias name (upper-cased) to type
     */
    public void setOutputAliasTypes(final Map<String, DataType> types) {
        this.outputAliasTypes = types;
    }

    /** {@link #validateStrict} for a WINDOW call re-formed without its OVER clause — the walk runs
     *  with the windowed marker set, so the bare-form desugared-name rewrites stay off (live reports
     * the WRITTEN name for windowed forms, measured). */
    public void validateStrictWindowed(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setWindowedStrictWalk(true);
        try {
            reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
            reusableVisitor.setScopeExemptNames(scopeExemptNames);
            if (multiTableAllTables != null) {
                reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
            }
            reusableVisitor.validateStrictArguments(expression);
        } finally {
            reusableVisitor.setWindowedStrictWalk(false);
        }
    }

    /**
     * Key-position validation: Snowflake rejects a FILE-typed expression used as a GROUP BY, ORDER BY
     * or window PARTITION BY key at compile time, while leaving it usable everywhere else (equality,
     * DISTINCT, joins). Run once per query on each parsed key, before any row is compared.
     */
    public void validateKey(final Expression expression, final SortKeyRole role) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        reusableVisitor.validateKeyExpression(expression, role);
    }

    /**
     * WITHIN GROUP (ORDER BY …) value validation: Snowflake rejects a semi-structured value in the
     * position an ordering aggregate accumulates, with the same "incompatible types" sentence
     * {@code MEDIAN} uses for its argument. Run once per query on the parsed clause expression.
     */
    public void validateOrderedValue(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        reusableVisitor.validateOrderedValueExpression(expression);
    }

    /**
     * A JOIN condition's collation rules, judged before any row is read — see
     * {@link ExpressionEvaluatorVisitor#validateCollations}.
     *
     * @param expression the parsed ON condition
     */
    public void validateCollations(final Expression expression) {
        preparedVisitor().validateCollations(expression);
    }

    /**
     * The collation {@code expression} carries, lower-cased, or null for none — see
     * {@link ExpressionEvaluatorVisitor#collationOf}. Over no relation a column reference carries
     * none, so what is left is what the expression names itself.
     *
     * @param expression the parsed expression
     * @return its collation specification, or null
     */
    public String collationOf(final Expression expression) {
        return preparedVisitor().collationOf(expression).getSpec();
    }

    /**
     * Predicate-position validation (Snowflake rejects VARCHAR/NUMBER-typed WHERE, HAVING and QUALIFY
     * conditions at compile time) — run once per query on the parsed predicate before row evaluation.
     * The BOOLEAN positions INSIDE the predicate — a searched CASE's WHEN, IFF's condition — are
     * judged first, by the same narrow walk, so a CASE over a VARCHAR refuses here as it does in a
     * SELECT list.
     *
     * <p>The GEOSPATIAL comparison walk runs here too. It is a separate, deliberately NARROW walk
     * rather than the full {@link #validateStrict} one: a WHERE clause is where a geo comparison is
     * actually written ({@code WHERE g = TO_GEOGRAPHY(…)}, {@code JOIN … ON a.g = b.g} lowered into a
     * filter), live refuses every such spelling, and a rule that can only fire on a statically
     * GEOGRAPHY- or GEOMETRY-typed operand cannot reject anything else. Running the whole strict walk
     * here would newly enforce the OBJECT / FILE operator rules in predicate position as well, which
     * has not been measured and is not this rule's business.
     */
    public void validatePredicate(final Expression expression) {
        final ExpressionEvaluatorVisitor checker = preparedVisitor();
        // A multi-column subquery an operator compares is typed as a ROW before the predicate is.
        checker.rejectRowOperations(expression);
        checker.validateBooleanPositions(expression);
        checker.validatePredicateType(expression);
        checker.validateGeoComparisons(expression);
    }

    /**
     * The BOOLEAN positions inside {@code expression} — a searched CASE's WHEN conditions, IFF's
     * condition — judged at plan time on their static types, for a path that evaluates values before
     * the strict walk would type them (the FROM-less select list).
     *
     * @param expression the parsed expression
     */
    public void validateBooleanPositions(final Expression expression) {
        final ExpressionEvaluatorVisitor visitor = preparedVisitor();
        visitor.rejectRowOperations(expression);
        visitor.validateBooleanPositions(expression);
    }

    /**
     * The type {@code expression} is statically KNOWN to produce in this table context, or null when it
     * cannot be determined. Used at projection time to give a derived relation (subquery, CTE, view) a
     * typed column list, so an outer reference to one of its columns resolves to the inner expression's
     * type instead of the VARCHAR placeholder. Never throws: an expression this evaluator cannot even
     * look at is simply undetermined.
     */
    public DataType inferStaticType(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        // A bare name no relation carries may be an EARLIER select item's alias, typed from that item.
        reusableVisitor.setOutputAliasTypes(outputAliasTypes);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        try {
            return reusableVisitor.inferStaticType(expression);
        } catch (final RuntimeException undetermined) {
            // A COMPILATION error is a refusal the channel reached on purpose — an operand pair
            // Snowflake rejects — not the ordinary "could not determine a type" this swallows.
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            return null;
        }
    }

    /**
     * Whether a COUNT over {@code argument} is answered from the statistics: over a constant or a stored
     * column (see ExpressionEvaluatorVisitor#countsStoredColumn).
     *
     * @param argument the COUNT's one argument
     * @return true when the statistics answer it
     */
    public boolean countsStoredColumn(final Expression argument) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        return reusableVisitor.countsStoredColumn(argument);
    }

    /**
     * Whether a select item projects a double live's compiler folds, or a column carrying one out of a derived
     * relation — see TableColumn#isFoldedDouble. Never throws.
     *
     * @param expression a select item
     * @return true for such an item
     */
    public boolean projectsFoldedDouble(final Expression expression) {
        try {
            return preparedVisitor().projectsFoldedDouble(expression);
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /**
     * Whether a select item projects a number or boolean constant wrapped into a VARIANT, or a column carrying
     * one out of a derived relation — see TableColumn#isUncheckedConstant. Never throws.
     *
     * @param expression a select item
     * @return true for such an item
     */
    public boolean projectsUncheckedConstant(final Expression expression) {
        try {
            return preparedVisitor().projectsUncheckedConstant(expression);
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /**
     * The NUMBER {@code expression} spells when it is a bare string literal, or a column carrying one out
     * of a derived relation — what a projected column hands on so a conditional over it folds as live
     * folds the literal (see TableColumn#getSpelledNumber); null for anything else. Never throws.
     */
    public DataType spelledNumber(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        try {
            return reusableVisitor.spelledNumber(expression);
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    /**
     * A SQL function call as the plan holds it once inlined, or null where that is not spelled out — see
     * {@link ExpressionEvaluatorVisitor#inlinedUdfCall}. Never throws.
     */
    public Expression inlinedUdfCall(final FunctionCallExpression call) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        try {
            return reusableVisitor.inlinedUdfCall(call);
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    /**
     * The interval {@code expression}'s values lie in, as the account's statistics would bound it, or
     * null when none can be known. The channel that decides SYSTEM$TYPEOF's storage tag; never throws.
     */
    public ValueRange inferStaticRange(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        try {
            return reusableVisitor.inferStaticRange(expression);
        } catch (final RuntimeException undetermined) {
            // A COMPILATION error is a refusal the channel reached on purpose — an operand pair
            // Snowflake rejects — not the ordinary "could not determine a type" this swallows.
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            return null;
        }
    }

    /**
     * Applies the argument-family refusals to every call in {@code expression} from declared types,
     * before any row is read — see {@link ExpressionEvaluatorVisitor#rejectArgumentFamilies}.
     */
    public void rejectArgumentFamilies(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        try {
            reusableVisitor.rejectArgumentFamilies(expression);
        } catch (final RuntimeException undetermined) {
            // A COMPILATION error is a refusal the channel reached on purpose — an argument family
            // Snowflake rejects — not the ordinary "could not determine a type" this swallows.
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
        }
    }

    /**
     * How Snowflake NAMES an expression's type when it refuses it as an argument — {@code VARCHAR(7)}
     * for a seven-character literal, {@code VARCHAR(50)} for a declared column, {@code NUMBER(1,0)},
     * {@code DATE}. A literal is measured from its own text, so the width is the value's, not the
     * family's maximum.
     */
    public String argumentTypeText(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setDeclaredTypeBase(declaredTypeBase, declaredTypeAliasToTable, declaredTypeAllTables);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        return reusableVisitor.argumentTypeText(expression);
    }

    /**
     * How Snowflake RE-PRINTS an expression when it names it in a refusal: from the analysed plan, so
     * a bare column reference comes back qualified with the name its relation is known by in scope —
     * {@code n} over {@code FROM eb} prints {@code EB.N}, and over {@code FROM eb x} prints {@code X.N}.
     * Keyword and identifier case and all spacing are normalised with it.
     */
    public String qualifiedText(final Expression expression) {
        return preparedVisitor().strictText(expression);
    }

    /**
     * {@link #qualifiedText} in the plan-shaped mode, which re-prints a subquery from its plan — see
     * {@link ExpressionEvaluatorVisitor#strictPlanText}.
     *
     * @param expression the expression
     * @return the expression as the plan holds it
     */
    public String plannedText(final Expression expression) {
        return preparedVisitor().strictPlanText(expression);
    }

    /**
     * {@link #qualifiedText} for an aggregate call that a NESTING message brackets: a plain AVG at
     * that root prints as its SUM half, the way live names it — see
     * {@link ExpressionEvaluatorVisitor#strictAggregateText}.
     */
    public String qualifiedAggregateText(final Expression expression) {
        return preparedVisitor().strictAggregateText(expression);
    }

    /**
     * Parse an expression string into an {@link Expression} AST — the parse-once entry point
     * for operators that previously re-evaluated the same text once per row. Parses via the ANTLR
     * grammar and caches by text. Blank input yields a literal TRUE (matching the old empty-predicate
     * behaviour).
     *
     * <p>{@code --} line comments are handled by the ANTLR lexer ({@code LINE_COMMENT -> skip}), which
     * tokenizes string literals first — so a literal such as {@code 'a--b'} keeps its dashes. (A prior
     * quote-blind regex pre-strip here truncated any literal containing {@code --}.)
     */
    public static Expression parse(final String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return new LiteralExpression(Boolean.TRUE, LiteralType.BOOLEAN);
        }
        return parseToAst(expression.trim());
    }

    /**
     * Parse a (comment-stripped) expression string into an {@link Expression} AST via the ANTLR
     * grammar ({@link AntlrExpressionParser}). Results are cached because operators currently
     * re-evaluate the same expression text once per row.
     *
     * <p><b>The cache is keyed by TEXT, so everything stored in an AST must be a function of that
     * text alone.</b> Source positions obey this by being fragment-relative — see
     * {@link dev.frostlake.executor.expressions.SourcePosition}. Storing anything that varies between
     * two occurrences of the same expression (an absolute statement offset, the row being evaluated,
     * a session setting) would hand the first occurrence's value to every later one: a wrong answer,
     * which is worse than the missing one it replaced.
     */
    private static Expression parseToAst(final String expression) {
        final Expression cached = AST_CACHE.get(expression);
        if (cached != null) {
            return cached;
        }
        // The ANTLR grammar + ExpressionAstBuilder is the sole parser. A parse failure here means
        // a genuinely malformed expression or a construct not modeled by the builder; let it
        // propagate rather than silently producing a wrong AST.
        final Expression ast = AntlrExpressionParser.parse(expression);
        AST_CACHE.put(expression, ast);
        return ast;
    }

}
