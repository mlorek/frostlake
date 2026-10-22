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
 * reference of which is such a reader, or a view. Without an aggregate it is DISTINCT, its items the relation's
 * columns, constants, or a column's COLLATION or SYSTEM$TYPEOF. Grouped or aggregating, its keys are such values and
 * its aggregates a column's MAX, MIN or ANY_VALUE; what the items and the HAVING compute of those is computed after
 * the rows are folded. Anything else keeps the check, and the conversion with it: an item computed before the rows are
 * compared ({@code DISTINCT UPPER(y)}, {@code MAX(UPPER(y))}), COUNT(*) or COUNT(DISTINCT y), a window or a QUALIFY, a
 * join, IN or EXISTS over the relation, and a plain projection, LIMIT 1 included. Only the check at the top goes, of a
 * UNION BY NAME too: in {@code A UNION B UNION C} the first UNION still compares its rows, and a body that sorts or
 * cuts its rows keeps its check.
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
            return ((FrostlakeParser.StarItemContext) item).starModifier().isEmpty()
                ? DuplicateBlindItem.VALUE : DuplicateBlindItem.OTHER;
        }
        if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            return ((FrostlakeParser.QualifiedStarItemContext) item).starModifier().isEmpty()
                ? DuplicateBlindItem.VALUE : DuplicateBlindItem.OTHER;
        }
        if (!(item instanceof FrostlakeParser.ExprItemContext)) {
            return DuplicateBlindItem.OTHER;
        }
        final Expression expression = parsed(((FrostlakeParser.ExprItemContext) item).booleanExpr());
        return expression == null ? DuplicateBlindItem.OTHER : kindOf(expression);
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
