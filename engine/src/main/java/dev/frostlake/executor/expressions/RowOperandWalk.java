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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Walks an expression innermost first, judging each operator's operands by the ROW rule (see
 * {@link ExpressionEvaluatorVisitor#rejectRowOperations}). A subquery's own operators are its own
 * compilation's, and a lambda's body is bound by the call that takes it.
 */
final class RowOperandWalk extends AstPrinterVisitor {

    private final ExpressionEvaluatorVisitor context;
    /** The IN tests that are POSITION's own syntax, judged by its argument-type rule instead. */
    private final Set<Expression> positionTests = Collections.newSetFromMap(new IdentityHashMap<Expression, Boolean>());

    RowOperandWalk(final ExpressionEvaluatorVisitor context) {
        this.context = context;
    }

    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        final String printed = super.visitBinaryOperation(expr);
        context.rejectRowOperation(expr);
        return printed;
    }

    @Override
    public String visitIsNull(final IsNullExpression expr) {
        final String printed = super.visitIsNull(expr);
        context.rejectRowOperation(expr);
        return printed;
    }

    @Override
    public String visitBetween(final BetweenExpression expr) {
        final String printed = super.visitBetween(expr);
        context.rejectRowOperation(expr);
        return printed;
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        if ("POSITION".equalsIgnoreCase(expr.getFunctionName()) && !expr.getArguments().isEmpty()) {
            positionTests.add(expr.getArguments().get(0));
        }
        return super.visitFunctionCall(expr);
    }

    @Override
    public String visitIn(final InExpression expr) {
        final String printed = super.visitIn(expr);
        if (!positionTests.contains(expr)) {
            context.rejectRowOperation(expr);
        }
        return printed;
    }

    @Override
    public String visitTupleIn(final TupleInExpression expr) {
        final String printed = super.visitTupleIn(expr);
        if (!positionTests.contains(expr)) {
            context.rejectRowOperation(expr);
        }
        return printed;
    }

    @Override
    public String visitQuantifiedComparison(final QuantifiedComparisonExpression expr) {
        final String printed = super.visitQuantifiedComparison(expr);
        context.rejectRowOperation(expr);
        return printed;
    }

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        return "";
    }

    @Override
    public String visitLambda(final LambdaExpression expr) {
        return "";
    }
}
