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

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * An arithmetic operation, a comparison or a {@code ||} one of whose operands is a constant that folds to NULL while
 * the other holds a subquery. The plan folds the whole operation to NULL, so the subquery is neither judged nor
 * run: {@code CASE WHEN FALSE THEN 1 END + (SELECT v FROM g WHERE g.id = t.id)} answers NULL for every row, where
 * the subquery alone is refused (live-verified). The constants that fold are NULL itself, a cast of it, and a
 * searched CASE, an IFF, a NULLIF, a COALESCE, an NVL, an ABS or a TO_NUMBER over constants.
 */
final class NullFoldedOperation {

    private static final Set<BinaryOperator> FOLDED = EnumSet.of(BinaryOperator.ADD, BinaryOperator.SUBTRACT,
        BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.MODULO, BinaryOperator.CONCAT,
        BinaryOperator.EQUAL, BinaryOperator.NOT_EQUAL, BinaryOperator.LESS_THAN, BinaryOperator.LESS_THAN_OR_EQUAL,
        BinaryOperator.GREATER_THAN, BinaryOperator.GREATER_THAN_OR_EQUAL);

    private static final Set<String> DECIDING_CALLS = Set.of(
        "IFF", "NULLIF", "COALESCE", "NVL", "IFNULL", "ABS", "TO_NUMBER");

    private NullFoldedOperation() {
    }

    /**
     * Whether the plan folds an operation to NULL without reading the subquery beside the NULL.
     *
     * @param operation the operation
     * @param visitor   the evaluator that folds a constant operand
     * @return whether the operation is NULL whatever the subquery would answer
     */
    static boolean folds(final BinaryOperationExpression operation, final ExpressionEvaluatorVisitor visitor) {
        if (!FOLDED.contains(operation.getOperator())) {
            return false;
        }
        final boolean rightQuery = SubqueryExpression.occursIn(operation.getRight());
        final boolean leftQuery = SubqueryExpression.occursIn(operation.getLeft());
        return rightQuery && !leftQuery && foldsToNull(operation.getLeft(), visitor)
            || leftQuery && !rightQuery && foldsToNull(operation.getRight(), visitor);
    }

    /**
     * Whether the plan folds a BETWEEN to NULL over a constant subject that folds to NULL, without reading a subquery
     * among its bounds: {@code NULL BETWEEN 1 AND (SELECT …)} and its NOT answer NULL for every row, where a NULL
     * bound leaves the subquery to be judged (live-verified).
     *
     * @param between the BETWEEN
     * @param visitor the evaluator that folds a constant operand
     * @return whether the BETWEEN is NULL whatever the subquery would answer
     */
    static boolean foldsBetween(final BetweenExpression between, final ExpressionEvaluatorVisitor visitor) {
        return (SubqueryExpression.occursIn(between.getLower()) || SubqueryExpression.occursIn(between.getUpper()))
            && !SubqueryExpression.occursIn(between.getValue()) && foldsToNull(between.getValue(), visitor);
    }

    /**
     * Whether the plan folds a GREATEST or a LEAST to NULL over a constant argument that folds to NULL, without
     * reading a subquery among its other arguments: {@code GREATEST(NULL, (SELECT …))} answers NULL for every row,
     * where GREATEST_IGNORE_NULLS leaves the subquery to be judged (live-verified).
     *
     * @param call    the call
     * @param visitor the evaluator that folds a constant argument
     * @return whether the call is NULL whatever the subquery would answer
     */
    static boolean foldsExtreme(final FunctionCallExpression call, final ExpressionEvaluatorVisitor visitor) {
        if (call.getNameExpression() != null || call.getFunctionName() == null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (!"GREATEST".equals(name) && !"LEAST".equals(name)) {
            return false;
        }
        boolean query = false;
        boolean nullArgument = false;
        for (final Expression argument : call.getArguments()) {
            if (SubqueryExpression.occursIn(argument)) {
                query = true;
            } else if (!nullArgument && foldsToNull(argument, visitor)) {
                nullArgument = true;
            }
        }
        return query && nullArgument;
    }

    private static boolean foldsToNull(final Expression operand, final ExpressionEvaluatorVisitor visitor) {
        if (!isConstant(operand)) {
            return false;
        }
        try {
            return operand.accept(visitor) == null;
        } catch (final RuntimeException faulted) {
            return false;
        }
    }

    private static boolean isConstant(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof CastExpression) {
            return isConstant(((CastExpression) expr).getExpression());
        }
        if (expr instanceof UnaryOperationExpression) {
            return isConstant(((UnaryOperationExpression) expr).getOperand());
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return isConstant(binary.getLeft()) && isConstant(binary.getRight()) && binary.getEscape() == null;
        }
        if (expr instanceof CaseExpression) {
            final CaseExpression written = (CaseExpression) expr;
            if (written.getElseExpression() != null && !isConstant(written.getElseExpression())) {
                return false;
            }
            for (final WhenClause when : written.getWhenClauses()) {
                if (!isConstant(when.getCondition()) || !isConstant(when.getResult())) {
                    return false;
                }
            }
            return true;
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (call.getNameExpression() != null || call.getFunctionName() == null
                    || !DECIDING_CALLS.contains(call.getFunctionName().toUpperCase(Locale.ROOT))) {
                return false;
            }
            for (final Expression argument : call.getArguments()) {
                if (!isConstant(argument)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
