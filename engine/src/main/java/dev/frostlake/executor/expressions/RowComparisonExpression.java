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

import java.util.ArrayList;
import java.util.List;

/**
 * Two ROW constructors compared: {@code (a, b) = (c, d)} and its five ordering siblings.
 *
 * <p>Equality answers over the WHOLE row — one pair that differs makes it FALSE however many NULLs
 * stand beside it, and a NULL only decides when nothing else has: {@code (1, NULL) = (2, 2)} is FALSE
 * while {@code (1, NULL) = (1, 2)} is NULL.
 *
 * <p>The ordering operators read the row LEXICOGRAPHICALLY, left to right, and stop at the first pair
 * that differs — so {@code (1, NULL) < (2, 1)} is TRUE, decided before the NULL is reached, while
 * {@code (1, NULL) < (1, 1)} is NULL, because the first pair ties and the second cannot answer.
 */
public class RowComparisonExpression implements Expression {

    private final List<Expression> left;
    private final List<Expression> right;
    private final String operator;
    private final boolean scalarLeft;
    private final boolean scalarRight;
    /** Where the operator token sits, for a refusal; null when unknown. */
    private SourcePosition position;

    /**
     * @param left     the elements written on the left
     * @param operator the comparison, as written
     * @param right    the elements written on the right
     */
    public RowComparisonExpression(final List<Expression> left, final String operator,
                                   final List<Expression> right) {
        this(left, operator, right, false, false);
    }

    /**
     * A row opposite a scalar, which the account refuses by type: the scalar side holds its one expression.
     *
     * @param left        the elements written on the left
     * @param operator    the comparison, as written
     * @param right       the elements written on the right
     * @param scalarLeft  whether the left side is a scalar rather than a row
     * @param scalarRight whether the right side is a scalar rather than a row
     */
    public RowComparisonExpression(final List<Expression> left, final String operator,
                                   final List<Expression> right, final boolean scalarLeft,
                                   final boolean scalarRight) {
        this.left = new ArrayList<>(left);
        this.right = new ArrayList<>(right);
        this.operator = operator;
        this.scalarLeft = scalarLeft;
        this.scalarRight = scalarRight;
    }

    /** Whether the left side is a scalar written opposite a row. */
    public boolean isScalarLeft() {
        return scalarLeft;
    }

    /** Whether the right side is a scalar written opposite a row. */
    public boolean isScalarRight() {
        return scalarRight;
    }

    /** Where the operator token sits; null when unknown. */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

    /** The elements written on the left. */
    public List<Expression> getLeft() {
        return new ArrayList<>(left);
    }

    /** The elements written on the right. */
    public List<Expression> getRight() {
        return new ArrayList<>(right);
    }

    /** The comparison, as written. */
    public String getOperator() {
        return operator;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitRowComparison(this);
    }

    @Override
    public String toString() {
        final StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < left.size(); i++) {
            text.append(i > 0 ? ", " : "").append(left.get(i));
        }
        text.append(") ").append(operator).append(" (");
        for (int i = 0; i < right.size(); i++) {
            text.append(i > 0 ? ", " : "").append(right.get(i));
        }
        return text.append(")").toString();
    }
}
