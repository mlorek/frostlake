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
    /** Where the OPERATOR token itself sits — see {@link #getPosition()}. Null when unknown. */
    private SourcePosition position;
    /** Whether this is the equality a simple CASE builds from its subject and one WHEN value. */
    private boolean simpleCaseTest;

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

    /**
     * Where the OPERATOR token sits — not the expression's start and not the enclosing select item's.
     * An argument-type refusal is anchored here, which is what Snowflake points at: over
     * {@code SELECT bn || s AS c FROM t} the refusal reads position 10, the offset of {@code ||},
     * and over {@code SELECT n AS a, bn || s AS c FROM t} it reads 18 — it tracks the operator through
     * a longer prefix, a parenthesis, an enclosing call and a second select item alike (live-verified).
     *
     * @return the operator's position, or null where the expression was not built from a parse tree
     */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

    /**
     * Whether this is the equality a simple CASE builds — {@code CASE x WHEN v} tests {@code x = v}, the
     * subject on the left. Two collations that disagree there are named subject first, where a written
     * comparison names its right side first (live-verified).
     *
     * @return true for a simple CASE's WHEN test
     */
    public boolean isSimpleCaseTest() {
        return simpleCaseTest;
    }

    /** Mark this equality as a simple CASE's WHEN test — see {@link #isSimpleCaseTest()}. */
    public void markSimpleCaseTest() {
        this.simpleCaseTest = true;
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
