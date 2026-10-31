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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.WindowFunctionExpression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The readers that cannot tell one row of a relation from two alike, under which the account drops the duplicate
 * check of the UNION at the top of the relation's body: what the arms hold is then read only where the reader reads
 * it. Over a text arm whose values fail to convert toward a number arm (all live-verified):
 *
 * <pre>
 *   SELECT DISTINCT COLLATION(x) FROM (… UNION …)               NULL: the text arm is never converted
 *   SELECT DISTINCT 1 FROM (… UNION …)                          1
 *   SELECT DISTINCT y FROM (… UNION …) WHERE y = 'a'            the values of y, which ORDER BY, LIMIT, TOP may cut
 *   SELECT y FROM (… UNION …) GROUP BY y HAVING MAX(y) &gt; 'a'   the groups
 *   SELECT ANY_VALUE(y) IS NOT NULL FROM (… UNION …)            TRUE, as over MAX and MIN
 * </pre>
 *
 * <p>The reader is one query block over that relation alone — a derived table, a common table expression every
 * reference of which is such a reader, or a view. Without an aggregate it is DISTINCT, its items the relation's columns
 * (a star, one that excludes columns included), constants, or a column's COLLATION or SYSTEM$TYPEOF. Grouped or
 * aggregating, its keys are such values and its aggregates a column's MAX, MIN or ANY_VALUE; what the items and the
 * HAVING compute of those is computed after the rows are folded. Anything else keeps the check, and the conversion with
 * it: an item computed before the rows are compared ({@code DISTINCT UPPER(y)}, {@code MAX(UPPER(y))}), COUNT(*) or
 * COUNT(DISTINCT y), a window or a QUALIFY, a join, IN or EXISTS over the relation, and a plain projection, LIMIT 1
 * included. Only the check at the top goes, of a UNION BY NAME too: in {@code A UNION B UNION C} the first UNION still
 * compares its rows, and a body that sorts or cuts its rows keeps its check.
 *
 * <p>A body that is one SELECT DISTINCT block, with no TOP and nothing sorting or cutting the rows it keeps, loses its
 * DISTINCT under such a reader, and an item the reader never reads is then never computed (all live-verified):
 *
 * <pre>
 *   SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (… UNION …))       the values of y, and MAX(y) the largest, the
 *                                                                     body a CTE's or a view's too
 *   SELECT DISTINCT y FROM (SELECT DISTINCT y, 1 / (LENGTH(s) - 1) AS z FROM t)   the values of y, z never computed
 *   SELECT y FROM (SELECT DISTINCT * FROM (… UNION …))                 refused: the reader keeps the DISTINCT
 *   SELECT DISTINCT y FROM (SELECT DISTINCT TOP 10 * FROM (… UNION …))   refused: TOP counts what DISTINCT keeps
 * </pre>
 *
 * <p>So does a block that groups by plain values and computes nothing over the groups, which only folds duplicate
 * rows as a DISTINCT does ({@link #redundantGrouping}); and a block that passes its rows through under such a reader
 * reads its own relation as one ({@link #passingBlock}) (all live-verified):
 *
 * <pre>
 *   SELECT DISTINCT y FROM (SELECT x, y FROM (… UNION …) GROUP BY x, y)   the values of y, x never computed; GROUP BY
 *                                                                         1, 2, ALL and one grouping set alike
 *   SELECT DISTINCT y FROM (SELECT * FROM (… UNION …))                    the values of y
 *   SELECT DISTINCT y FROM (SELECT x, y FROM (… UNION …) GROUP BY x, y ORDER BY y)   refused: the body sorts
 * </pre>
 */
final class DuplicateBlindReading {

    private DuplicateBlindReading() {
    }

    /**
     * Whether a relation's body has a duplicate check such a reader drops: its last operator that is no INTERSECT is a
     * UNION without ALL, and no ORDER BY, LIMIT or FETCH follows the rows that check leaves.
     *
     * @param body the relation's body
     * @return whether the body's top check may go
     */
    static boolean hasTopCheck(final FrostlakeParser.SelectStatementContext body) {
        if (body.orderByClause() != null || body.limitClause() != null || body.fetchClause() != null) {
            return false;
        }
        final List<FrostlakeParser.SetOperatorContext> operators = body.setOperator();
        for (int i = operators.size() - 1; i >= 0; i--) {
            final FrostlakeParser.SetOperatorContext operator = operators.get(i);
            if (operator.INTERSECT() == null) {
                return operator.UNION() != null && operator.ALL() == null;
            }
        }
        return false;
    }

    /**
     * The one query block of a relation's body when that block only folds its duplicate rows away: a SELECT DISTINCT
     * with no TOP, standing alone, no ORDER BY, LIMIT or FETCH after it.
     *
     * @param body the relation's body
     * @return the block, whose DISTINCT such a reader drops, or null
     */
    static FrostlakeParser.SelectClauseContext redundantDistinct(final FrostlakeParser.SelectStatementContext body) {
        if (body.withClause() != null || body.selectOperand().size() != 1 || body.orderByClause() != null
                || body.limitClause() != null || body.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext block = body.selectOperand(0).selectClause();
        return block == null || block.DISTINCT() == null || block.topClause() != null || block.intoClause() != null
            ? null : block;
    }

    /**
     * The one query block of a relation's body when it groups by plain values and computes nothing over the groups,
     * only folding its duplicate rows away: a GROUP BY of columns, of positions or ALL, or one grouping set of columns,
     * its items columns among the keys and constants, with no aggregate, no window, no QUALIFY, no TOP, no HAVING but
     * TRUE, and no ORDER BY, LIMIT or FETCH after it.
     *
     * @param body the relation's body
     * @return the block, whose grouping such a reader drops, or null
     */
    static FrostlakeParser.SelectClauseContext redundantGrouping(final FrostlakeParser.SelectStatementContext body) {
        if (body.withClause() != null || body.selectOperand().size() != 1 || body.orderByClause() != null
                || body.limitClause() != null || body.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext block = body.selectOperand(0).selectClause();
        if (block == null || block.groupByClause() == null || block.topClause() != null || block.intoClause() != null
                || block.qualifyClause() != null || block.connectByClause() != null
                || block.havingClause() != null && !(parsed(block.havingClause().booleanExpr())
                    instanceof LiteralExpression && Boolean.TRUE.equals(
                        ((LiteralExpression) parsed(block.havingClause().booleanExpr())).getValue()))) {
            return null;
        }
        final List<String> keys = new ArrayList<>();
        final FrostlakeParser.GroupByClauseContext groupBy = block.groupByClause();
        boolean positional = groupBy.ALL() != null;
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            final List<FrostlakeParser.BooleanExprContext> written = new ArrayList<>();
            if (element.GROUPING() != null) {
                if (groupBy.groupByElement().size() != 1 || element.groupingSetList().groupingSet().size() != 1) {
                    return null;
                }
                final FrostlakeParser.GroupingSetContext set = element.groupingSetList().groupingSet(0);
                written.addAll(set.booleanExpr());
            } else if (element.booleanExpr() != null) {
                written.add(element.booleanExpr());
            } else {
                return null;
            }
            for (final FrostlakeParser.BooleanExprContext key : written) {
                final Expression parsedKey = parsed(key);
                if (parsedKey instanceof ColumnReferenceExpression) {
                    keys.add(((ColumnReferenceExpression) parsedKey).getColumnName().toUpperCase(Locale.ROOT));
                } else if (parsedKey instanceof LiteralExpression
                        && ((LiteralExpression) parsedKey).getType() == LiteralType.INTEGER) {
                    positional = true;
                } else {
                    return null;
                }
            }
        }
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            final Expression expression = item instanceof FrostlakeParser.ExprItemContext
                ? parsed(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
            if (expression instanceof ColumnReferenceExpression) {
                final String name = ((ColumnReferenceExpression) expression).getColumnName().toUpperCase(Locale.ROOT);
                if (!positional && !keys.contains(name)) {
                    return null;
                }
            } else if (!(expression instanceof LiteralExpression)) {
                return null;
            }
        }
        return block;
    }

    /**
     * The one query block of a relation's body when it passes its rows through — no DISTINCT, no grouping, no
     * aggregate, no window, no QUALIFY, no TOP, and no ORDER BY, LIMIT or FETCH after it — so that a reader that cannot
     * tell duplicate rows apart makes it a reader of its own relation that cannot either.
     *
     * @param body      the relation's body
     * @param functions the functions, which tell an aggregate call
     * @return the block, or null
     */
    static FrostlakeParser.SelectClauseContext passingBlock(final FrostlakeParser.SelectStatementContext body,
                                                            final FunctionRegistry functions) {
        if (body.withClause() != null || body.selectOperand().size() != 1 || body.orderByClause() != null
                || body.limitClause() != null || body.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext block = body.selectOperand(0).selectClause();
        if (block == null || block.DISTINCT() != null || block.topClause() != null || block.intoClause() != null
                || block.groupByClause() != null || block.havingClause() != null || block.qualifyClause() != null
                || block.connectByClause() != null) {
            return null;
        }
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                continue;
            }
            final Expression expression = parsed(((FrostlakeParser.ExprItemContext) item).booleanExpr());
            if (expression == null || callsAggregate(expression, functions) || callsWindow(expression)) {
                return null;
            }
        }
        return block;
    }

    /**
     * The query block a relation's reference is the one FROM item of, with no join beside it, or null.
     *
     * @param reference the FROM item naming the relation
     * @return the block, or null
     */
    static FrostlakeParser.SelectClauseContext readingBlock(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.matchRecognizeClause() != null || reference.sampleClause() != null
                || !(reference.getParent() instanceof FrostlakeParser.TableExpressionContext)) {
            return null;
        }
        final FrostlakeParser.TableExpressionContext from =
            (FrostlakeParser.TableExpressionContext) reference.getParent();
        return from.tableReference().size() == 1 && from.joinClause().isEmpty()
            && from.getParent() instanceof FrostlakeParser.SelectClauseContext
            ? (FrostlakeParser.SelectClauseContext) from.getParent() : null;
    }

    /**
     * Whether the query block that reads the relation {@code reference} names cannot tell its duplicate rows apart.
     *
     * @param reference the FROM item naming the relation
     * @param functions  the functions, which tell an aggregate call
     * @return whether the block is such a reader
     */
    static boolean readsWithoutDuplicates(final FrostlakeParser.TableReferenceContext reference,
                                          final FunctionRegistry functions) {
        if (reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.matchRecognizeClause() != null || reference.sampleClause() != null
                || !(reference.getParent() instanceof FrostlakeParser.TableExpressionContext)) {
            return false;
        }
        final FrostlakeParser.TableExpressionContext from =
            (FrostlakeParser.TableExpressionContext) reference.getParent();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()
                || !(from.getParent() instanceof FrostlakeParser.SelectClauseContext)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext block = (FrostlakeParser.SelectClauseContext) from.getParent();
        if (block.intoClause() != null || block.connectByClause() != null || block.qualifyClause() != null) {
            return false;
        }
        final FrostlakeParser.GroupByClauseContext groupBy = block.groupByClause();
        boolean aggregating = groupBy != null || block.havingClause() != null;
        final List<Expression> items = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
            final Expression expression = item instanceof FrostlakeParser.ExprItemContext
                ? parsed(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
            items.add(expression);
            aggregating |= expression != null && callsAggregate(expression, functions);
        }
        if (!aggregating) {
            // DISTINCT over the relation's own values: each item is one.
            if (block.DISTINCT() == null) {
                return false;
            }
            for (final FrostlakeParser.SelectItemContext item : block.selectList().selectItem()) {
                if (kindOf(item) != DuplicateBlindItem.VALUE) {
                    return false;
                }
            }
            return true;
        }
        // Grouped or aggregating: the keys are the relation's own values, every aggregate a column's MAX, MIN or
        // ANY_VALUE, and what the items and the HAVING compute of those is computed after the rows are folded.
        if (groupBy != null) {
            if (groupBy.ALL() != null) {
                return false;
            }
            for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
                final Expression key = element.booleanExpr() == null ? null : parsed(element.booleanExpr());
                if (key == null || kindOf(key) != DuplicateBlindItem.VALUE) {
                    return false;
                }
            }
        }
        for (final Expression item : items) {
            if (item == null || !foldsExtremesOnly(item, functions)) {
                return false;
            }
        }
        return block.havingClause() == null
            || foldsExtremesOnly(parsed(block.havingClause().booleanExpr()), functions);
    }

    /**
     * Whether every reference of a common table expression in the statement that defines it is such a reader, there
     * being one at least.
     *
     * @param cte  the definition
     * @param name      its canonical name
     * @param functions the functions, which tell an aggregate call
     * @return whether the definition is read by such readers alone
     */
    static boolean readWithoutDuplicates(final FrostlakeParser.CteDefinitionContext cte, final String name,
                                         final FunctionRegistry functions) {
        ParseTree root = cte;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>();
        collectReferences(root, name, cte.selectStatement(), references);
        if (references.isEmpty()) {
            return false;
        }
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            if (!readsWithoutDuplicates(reference, functions)) {
                return false;
            }
        }
        return true;
    }

    private static void collectReferences(final ParseTree node, final String name, final ParseTree body,
                                          final List<FrostlakeParser.TableReferenceContext> references) {
        if (node == body) {
            return;
        }
        if (node instanceof FrostlakeParser.TableReferenceContext) {
            final FrostlakeParser.TableSourceContext source =
                ((FrostlakeParser.TableReferenceContext) node).tableSource();
            if (source != null && source.tableQualifiedName() != null) {
                final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
                if (parts.length == 1 && parts[0].equals(name)) {
                    references.add((FrostlakeParser.TableReferenceContext) node);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectReferences(node.getChild(i), name, body, references);
        }
    }

    private static DuplicateBlindItem kindOf(final FrostlakeParser.SelectItemContext item) {
        if (item instanceof FrostlakeParser.StarItemContext) {
            return excludesOnly(((FrostlakeParser.StarItemContext) item).starModifier())
                ? DuplicateBlindItem.VALUE : DuplicateBlindItem.OTHER;
        }
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            return excludesOnly(((FrostlakeParser.QualifiedStarItemContext) item).starModifier())
                ? DuplicateBlindItem.VALUE : DuplicateBlindItem.OTHER;
        }
        if (!(item instanceof FrostlakeParser.ExprItemContext)) {
            return DuplicateBlindItem.OTHER;
        }
        final Expression expression = parsed(((FrostlakeParser.ExprItemContext) item).booleanExpr());
        return expression == null ? DuplicateBlindItem.OTHER : kindOf(expression);
    }

    /** Whether a star's modifiers only leave columns out: it then passes the relation's own values on. */
    private static boolean excludesOnly(final List<FrostlakeParser.StarModifierContext> modifiers) {
        for (final FrostlakeParser.StarModifierContext modifier : modifiers) {
            if (modifier.EXCLUDE() == null) {
                return false;
            }
        }
        return true;
    }

    private static DuplicateBlindItem kindOf(final Expression expression) {
        if (expression instanceof ColumnReferenceExpression || expression instanceof LiteralExpression) {
            return DuplicateBlindItem.VALUE;
        }
        if (!(expression instanceof FunctionCallExpression)) {
            return DuplicateBlindItem.OTHER;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expression;
        if (call.isStar() || call.isDistinct() || call.getArguments().size() != 1
                || !(call.getArguments().get(0) instanceof ColumnReferenceExpression)) {
            return DuplicateBlindItem.OTHER;
        }
        final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
        if ("COLLATION".equals(name) || "SYSTEM$TYPEOF".equals(name)) {
            return DuplicateBlindItem.VALUE;
        }
        return "MAX".equals(name) || "MIN".equals(name) || "ANY_VALUE".equals(name)
            ? DuplicateBlindItem.EXTREME : DuplicateBlindItem.OTHER;
    }

    /** Whether an expression calls an aggregate outside any window or query. */
    private static boolean callsAggregate(final Expression expression, final FunctionRegistry functions) {
        final boolean[] found = new boolean[1];
        expression.accept(new AstPrinterVisitor() {
            @Override
            public String visitFunctionCall(final FunctionCallExpression expr) {
                if (isAggregate(expr, functions)) {
                    found[0] = true;
                    return "";
                }
                return super.visitFunctionCall(expr);
            }

            @Override
            public String visitWindowFunction(final WindowFunctionExpression expr) {
                return "";
            }

            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                return "";
            }
        });
        return found[0];
    }

    /** Whether an expression calls a window function outside any query. */
    private static boolean callsWindow(final Expression expression) {
        final boolean[] found = new boolean[1];
        expression.accept(new AstPrinterVisitor() {
            @Override
            public String visitWindowFunction(final WindowFunctionExpression expr) {
                found[0] = true;
                return "";
            }

            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                return "";
            }
        });
        return found[0];
    }

    /**
     * Whether every aggregate an expression calls is a column's MAX, MIN or ANY_VALUE, with no window and no query
     * among what it reads.
     */
    private static boolean foldsExtremesOnly(final Expression expression, final FunctionRegistry functions) {
        if (expression == null) {
            return false;
        }
        final boolean[] other = new boolean[1];
        expression.accept(new AstPrinterVisitor() {
            @Override
            public String visitFunctionCall(final FunctionCallExpression expr) {
                if (!isAggregate(expr, functions)) {
                    return super.visitFunctionCall(expr);
                }
                if (kindOf(expr) != DuplicateBlindItem.EXTREME) {
                    other[0] = true;
                }
                return "";
            }

            @Override
            public String visitWindowFunction(final WindowFunctionExpression expr) {
                other[0] = true;
                return "";
            }

            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                other[0] = true;
                return "";
            }
        });
        return !other[0];
    }

    private static boolean isAggregate(final FunctionCallExpression call, final FunctionRegistry functions) {
        final String name = call.getFunctionName();
        return name != null && functions.hasAggregateFunction(name.toUpperCase(Locale.ROOT));
    }

    private static Expression parsed(final ParserRuleContext context) {
        try {
            return ExpressionEvaluator.parse(ParseTreeText.getOriginalText(context));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }
}
