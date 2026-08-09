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
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.executor.expressions.SubqueryMemo;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
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
    private Map<String, Table> multiTableAliasToTable;
    private List<Table> multiTableAllTables;
    private Set<String> scopeExemptNames;
    private Map<String, Object> resultContext;
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
     */
    private static final BoundedParseCache<Expression> AST_CACHE =
        new BoundedParseCache<>(2048, 4096);

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
        visitor.setLateralContext(outerLateralContext);
        if (multiTableAllTables != null) {
            visitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        if (resultContext != null) {
            visitor.setResultContext(resultContext);
        }
        visitor.setSubqueryMemo(subqueryMemo);
        return expression.accept(visitor);
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
     * Plan-time strict-argument validation over a parsed expression: runs the Snowflake
     * argument-type checks for every function call in {@code expression} WITHOUT evaluating it,
     * so rejection fires even over zero input rows.
     */
    public void validateStrict(final Expression expression) {
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        reusableVisitor.setScopeExemptNames(scopeExemptNames);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        reusableVisitor.validateStrictArguments(expression);
    }

    /** Bare names the plan-time scope walk must not reject — the query's SELECT output aliases /
     *  output column names, legal in every non-SELECT clause (WHERE included, live-verified). */
    public void setScopeExemptNames(final Set<String> names) {
        this.scopeExemptNames = names;
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
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        reusableVisitor.validateOrderedValueExpression(expression);
    }

    /**
     * Predicate-position validation (Snowflake rejects VARCHAR/NUMBER-typed WHERE conditions at
     * compile time) — run once per query on the parsed predicate before row evaluation.
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
        if (reusableVisitor == null) {
            reusableVisitor = new ExpressionEvaluatorVisitor(table, null, functionRegistry, catalog);
        }
        reusableVisitor.setQueryExecutor(queryExecutor);
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        reusableVisitor.validatePredicateType(expression);
        reusableVisitor.validateGeoComparisons(expression);
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
        if (multiTableAllTables != null) {
            reusableVisitor.setMultiTableContext(multiTableAliasToTable, multiTableAllTables);
        }
        try {
            return reusableVisitor.inferStaticType(expression);
        } catch (final RuntimeException undetermined) {
            return null;
        }
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
        Expression cached = AST_CACHE.get(expression);
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
