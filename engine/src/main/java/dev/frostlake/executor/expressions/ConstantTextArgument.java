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

import java.util.Locale;

/**
 * Whether an argument that must be CONSTANT text is one — ROUND's rounding mode is the caller. Live
 * folds a narrow family while the statement compiles, and calls everything else non-constant even
 * where the value plainly cannot change:
 *
 * <pre>
 *   'HALF_TO_EVEN'  $$HALF_TO_EVEN$$  ('HALF_TO_EVEN')     a string literal, parenthesised or not
 *   $mode                                                  a session variable holding text or NULL
 *   NULL                                                   the call answers NULL
 *   'HALF_' || 'TO_EVEN'  CONCAT(…)  CONCAT_WS('_', …)     joins of foldable text
 *   UPPER('half_to_even')  LOWER(…)                        case mappings of foldable text
 *   'HALF_TO_EVEN' COLLATE 'en-ci'  COLLATE(…)             a collation of foldable text
 * </pre>
 *
 * <p>NOT constant: REPLACE, SUBSTR, LEFT, TRIM, LTRIM, COALESCE, NVL, IFF, CASE, a CAST or {@code ::},
 * TO_VARCHAR, arithmetic, a numeric or boolean literal, a session variable holding a number — and a
 * number ANYWHERE inside a join or a case mapping ({@code 1 || ''}, {@code CONCAT('x', 1)},
 * {@code UPPER(1)}): the implicit cast is what stops the fold.
 *
 * <p>Only the SHAPE is judged here. The value is the evaluator's, so a folded argument answers exactly
 * what the same expression answers anywhere else.
 */
final class ConstantTextArgument {

    private ConstantTextArgument() {
    }

    /**
     * Whether {@code argument} folds to constant text, or to NULL, while the statement compiles.
     *
     * @param argument the argument as written
     * @param evaluator reads a session variable's value, whose type decides whether it folds
     * @return whether live takes it as a constant
     */
    static boolean isConstant(final Expression argument, final ExpressionVisitor<Object> evaluator) {
        if (argument instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) argument).getType();
            return type == LiteralType.STRING || type == LiteralType.NULL;
        }
        if (argument instanceof SessionVarExpression) {
            final Object value = argument.accept(evaluator);
            return value == null || value instanceof String;
        }
        if (argument instanceof BinaryOperationExpression) {
            final BinaryOperationExpression join = (BinaryOperationExpression) argument;
            return join.getOperator() == BinaryOperator.CONCAT
                && isConstant(join.getLeft(), evaluator) && isConstant(join.getRight(), evaluator);
        }
        if (!(argument instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) argument;
        if (!isFoldedCall(call)) {
            return false;
        }
        for (final Expression inner : call.getArguments()) {
            if (!isConstant(inner, evaluator)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code argument} is NULL while the statement compiles: the plain NULL, a cast of one
     * ({@code NULL::VARCHAR}, {@code NULL::FLOAT}), or a text fold that answers NULL
     * ({@code UPPER(NULL)}, {@code NULL || 'x'}). A NULL-valued COLUMN is not — it is known only when
     * the row is read.
     *
     * @param argument the argument as written
     * @param evaluator folds a text argument, as {@link #isConstant} does
     * @return whether the argument is a NULL constant
     */
    static boolean isNullConstant(final Expression argument, final ExpressionVisitor<Object> evaluator) {
        if (argument instanceof CastExpression) {
            return isNullConstant(((CastExpression) argument).getExpression(), evaluator);
        }
        return isConstant(argument, evaluator) && argument.accept(evaluator) == null;
    }

    private static boolean isFoldedCall(final FunctionCallExpression call) {
        if (call.isDistinct() || call.isStar() || call.getFunctionName() == null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        return "UPPER".equals(name) || "LOWER".equals(name) || "COLLATE".equals(name) || "CONCAT".equals(name)
            || "CONCAT_WS".equals(name);
    }
}
