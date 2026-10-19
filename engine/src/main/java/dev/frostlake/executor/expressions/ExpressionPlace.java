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

/**
 * Where an expression stands in the text it was read from, as a refusal of the whole expression names it: the
 * operator of an infix or a postfix form — {@code a + b} at the {@code +}, {@code x::INT} at the {@code ::},
 * {@code c IS NULL} at the IS, {@code v:k} at the colon — and the first token of every other, parentheses not
 * counted (live-verified).
 */
public final class ExpressionPlace {

    private ExpressionPlace() {
    }

    /**
     * The place of an expression.
     *
     * @param expression the expression as built from its text
     * @return its place in that text, or null for an expression that carries none
     */
    public static SourcePosition of(final Expression expression) {
        if (expression instanceof BinaryOperationExpression) {
            return ((BinaryOperationExpression) expression).getPosition();
        }
        if (expression instanceof CastExpression) {
            return ((CastExpression) expression).getPosition();
        }
        if (expression instanceof FunctionCallExpression) {
            return ((FunctionCallExpression) expression).getPosition();
        }
        if (expression instanceof LiteralExpression) {
            return ((LiteralExpression) expression).getPosition();
        }
        if (expression instanceof ColumnReferenceExpression) {
            return ((ColumnReferenceExpression) expression).getPosition();
        }
        if (expression instanceof UnaryOperationExpression) {
            return ((UnaryOperationExpression) expression).getPosition();
        }
        if (expression instanceof IsNullExpression) {
            return ((IsNullExpression) expression).getPosition();
        }
        if (expression instanceof InExpression) {
            return ((InExpression) expression).getPosition();
        }
        if (expression instanceof TupleInExpression) {
            return ((TupleInExpression) expression).getPosition();
        }
        if (expression instanceof BetweenExpression) {
            return ((BetweenExpression) expression).getPosition();
        }
        if (expression instanceof LikeAnyAllExpression) {
            return ((LikeAnyAllExpression) expression).getPosition();
        }
        if (expression instanceof QuantifiedComparisonExpression) {
            return ((QuantifiedComparisonExpression) expression).getPosition();
        }
        if (expression instanceof RowComparisonExpression) {
            return ((RowComparisonExpression) expression).getPosition();
        }
        if (expression instanceof ObjectAccessExpression) {
            return ((ObjectAccessExpression) expression).getPosition();
        }
        if (expression instanceof ArrayAccessExpression) {
            return ((ArrayAccessExpression) expression).getPosition();
        }
        if (expression instanceof SubqueryExpression) {
            return ((SubqueryExpression) expression).getPosition();
        }
        if (expression instanceof WindowFunctionExpression) {
            return ((WindowFunctionExpression) expression).getPosition();
        }
        if (expression instanceof BindVariableExpression) {
            return ((BindVariableExpression) expression).getPosition();
        }
        if (expression instanceof SessionVarExpression) {
            return ((SessionVarExpression) expression).getPosition();
        }
        return null;
    }
}
