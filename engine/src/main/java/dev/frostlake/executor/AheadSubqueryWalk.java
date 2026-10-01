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
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.LambdaExpression;
import dev.frostlake.executor.expressions.QuantifiedComparisonExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.TupleInExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.WindowFunctionExpression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The scalar subqueries an expression computes whatever its row holds, in the order they are written: the ones
 * it reaches through operators, casts and calls, and not through a branch that only some rows take. A CASE, a
 * choosing call (IFF, COALESCE, NVL, NVL2, IFNULL, DECODE), AND and OR are such branches — live leaves the
 * subquery of a branch no row takes uncomputed: {@code CASE WHEN v > 5 THEN (SELECT v FROM t) END} over values
 * below 5 answers NULL where the subquery returns several rows (live-verified). An EXISTS, an IN or a quantified
 * comparison reads its subquery's rows rather than one value, and a window call or a lambda computes its
 * arguments per row of its own, so their subqueries are left out too; nothing is looked for inside a subquery.
 */
final class AheadSubqueryWalk extends AstPrinterVisitor {

    /** The calls that compute only the argument they choose. */
    private static final Set<String> CHOOSING_CALLS = Set.of("IFF", "COALESCE", "NVL", "NVL2", "IFNULL", "DECODE");

    private final List<SubqueryExpression> subqueries = new ArrayList<>();

    /** The scalar subqueries found, in the order written. */
    List<SubqueryExpression> subqueries() {
        return subqueries;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        subqueries.add(expr);
        return "";
    }

    @Override
    public String visitUnaryOperation(final UnaryOperationExpression expr) {
        return expr.getOperator() == UnaryOperator.EXISTS ? "" : super.visitUnaryOperation(expr);
    }

    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        return expr.getOperator() == BinaryOperator.AND || expr.getOperator() == BinaryOperator.OR
            ? "" : super.visitBinaryOperation(expr);
    }

    @Override
    public String visitCaseExpression(final CaseExpression expr) {
        return "";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String name = expr.getFunctionName();
        return name != null && CHOOSING_CALLS.contains(name.toUpperCase(Locale.ROOT)) ? ""
            : super.visitFunctionCall(expr);
    }

    @Override
    public String visitIn(final InExpression expr) {
        expr.getValue().accept(this);
        if (!expr.hasSubquery() && expr.getValues() != null) {
            for (final Expression value : expr.getValues()) {
                value.accept(this);
            }
        }
        return "";
    }

    @Override
    public String visitTupleIn(final TupleInExpression expr) {
        for (final Expression value : expr.getValues()) {
            value.accept(this);
        }
        return "";
    }

    @Override
    public String visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        expr.getLeft().accept(this);
        return "";
    }

    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";
    }
}
