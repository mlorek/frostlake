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
 * Represents a binary operation (e.g., a + b, x > 5, name = 'Alice')
 */
public class BinaryOperationExpression implements Expression {
    private final Expression left;
    private final Expression right;
    private final BinaryOperator operator;
    /** For LIKE/ILIKE, the optional {@code ESCAPE <char>} expression; null when none is specified. */
    private final Expression escape;

    public BinaryOperationExpression(final Expression left, final BinaryOperator operator, final Expression right) {
        this(left, operator, right, null);
    }

    public BinaryOperationExpression(final Expression left, final BinaryOperator operator, final Expression right,
                                     final Expression escape) {
        this.left = left;
        this.operator = operator;
        this.right = right;
        this.escape = escape;
    }

    public Expression getLeft() {
        return left;
    }

    public Expression getRight() {
        return right;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    /** The LIKE/ILIKE {@code ESCAPE} character expression, or null if the query had no ESCAPE clause. */
    public Expression getEscape() {
        return escape;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitBinaryOperation(this);
    }

    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }
}
