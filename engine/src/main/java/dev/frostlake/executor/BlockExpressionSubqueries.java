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
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LambdaExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.procedural.BaseExpression;
import dev.frostlake.executor.procedural.BinaryExpression;
import dev.frostlake.executor.procedural.FunctionCallExpression;
import dev.frostlake.executor.procedural.SqlScalarExpression;
import dev.frostlake.executor.procedural.UnaryExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The subqueries of a block's own expression — a RETURN value, a LET or assignment value, a condition —
 * compiled before the expression is evaluated, as the account compiles the whole expression before it
 * runs any of it. A name a subquery cannot resolve is therefore refused in the order a query reports it,
 * its select list ahead of its WHERE, its JOIN's ON and its GROUP BY — {@code LET a NUMBER := (SELECT
 * missing FROM (SELECT 1 AS b) WHERE missing2 = 1)} names 'MISSING' — and a subquery on a side of an AND
 * or an OR that the other side settles is compiled all the same: {@code IF (FALSE AND (SELECT missing FROM
 * t) = 1)} is refused (live-verified). What compiling leaves for a row to raise waits for the row.
 *
 * <p>Each subquery compiles from its own text, as it later runs, so a refusal is positioned in that text and
 * the block places it in the expression the same way it places the running subquery's.
 */
final class BlockExpressionSubqueries {

    private BlockExpressionSubqueries() {
    }

    /**
     * Compile every subquery of a SQL expression, in the order written.
     *
     * @param evaluator  the evaluator the expression is evaluated in
     * @param expression the expression
     */
    static void compile(final ExpressionEvaluator evaluator, final Expression expression) {
        final List<SubqueryExpression> subqueries = new ArrayList<SubqueryExpression>();
        expression.accept(new AstPrinterVisitor() {
            @Override
            public String visitSubquery(final SubqueryExpression expr) {
                subqueries.add(expr);
                return "";
            }

            @Override
            public String visitLambda(final LambdaExpression expr) {
                return "";
            }
        });
        for (final SubqueryExpression subquery : subqueries) {
            evaluator.compileSubquery(new SubqueryExpression(subquery.getSubquery()), null);
        }
    }

    /**
     * Refuse the calls of a block expression that name no function, in live's sentence — {@code IF (FALSE AND
     * missing_fn() = 1)} is {@code Unknown function MISSING_FN.} although nothing evaluates the call
     * (live-verified).
     *
     * @param evaluator  the evaluator whose functions and catalog resolve the names
     * @param expression the block expression
     */
    static void requireKnownFunctions(final ExpressionEvaluator evaluator, final BaseExpression expression) {
        final List<dev.frostlake.executor.expressions.FunctionCallExpression> unknown =
            new ArrayList<dev.frostlake.executor.expressions.FunctionCallExpression>();
        collectUnknownCalls(evaluator, expression, unknown);
        if (!unknown.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(evaluator.unknownFunctionSentence(unknown)));
        }
    }

    private static void collectUnknownCalls(final ExpressionEvaluator evaluator, final BaseExpression expression,
                                            final List<dev.frostlake.executor.expressions.FunctionCallExpression> unknown) {
        if (expression instanceof SqlScalarExpression) {
            evaluator.validateFunctionNames(((SqlScalarExpression) expression).getExpression());
        } else if (expression instanceof BinaryExpression) {
            collectUnknownCalls(evaluator, ((BinaryExpression) expression).getLeft(), unknown);
            collectUnknownCalls(evaluator, ((BinaryExpression) expression).getRight(), unknown);
        } else if (expression instanceof UnaryExpression) {
            collectUnknownCalls(evaluator, ((UnaryExpression) expression).getOperand(), unknown);
        } else if (expression instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            final dev.frostlake.executor.expressions.FunctionCallExpression named =
                new dev.frostlake.executor.expressions.FunctionCallExpression(call.getFunctionName(),
                    Collections.<Expression>emptyList());
            if (!evaluator.resolvesToAFunctionName(named)) {
                unknown.add(named);
            }
            for (final BaseExpression argument : call.getArguments()) {
                collectUnknownCalls(evaluator, argument, unknown);
            }
        }
    }

    /**
     * The SQL expressions of a block expression that holds them, outermost first in the order written: the
     * parts of it the block evaluates through SQL.
     *
     * @param expression the block expression
     * @return the SQL expressions under it
     */
    static List<SqlScalarExpression> sqlPartsOf(final BaseExpression expression) {
        final List<SqlScalarExpression> parts = new ArrayList<SqlScalarExpression>();
        collect(expression, parts);
        return parts;
    }

    private static void collect(final BaseExpression expression, final List<SqlScalarExpression> parts) {
        if (expression instanceof SqlScalarExpression) {
            parts.add((SqlScalarExpression) expression);
        } else if (expression instanceof BinaryExpression) {
            collect(((BinaryExpression) expression).getLeft(), parts);
            collect(((BinaryExpression) expression).getRight(), parts);
        } else if (expression instanceof UnaryExpression) {
            collect(((UnaryExpression) expression).getOperand(), parts);
        } else if (expression instanceof FunctionCallExpression) {
            for (final BaseExpression argument : ((FunctionCallExpression) expression).getArguments()) {
                collect(argument, parts);
            }
        }
    }
}
