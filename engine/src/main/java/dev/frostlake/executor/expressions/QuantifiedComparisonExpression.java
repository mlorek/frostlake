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
 * Represents a quantified comparison expression (e.g., x > ANY (subquery))
 */
public class QuantifiedComparisonExpression implements Expression {
    private final Expression left;
    private final BinaryOperator operator;
    private final Quantifier quantifier;
    private final Expression subquery;

    public QuantifiedComparisonExpression(final Expression left, final BinaryOperator operator,
                                          final Quantifier quantifier, final Expression subquery) {
        this.left = left;
        this.operator = operator;
        this.quantifier = quantifier;
        this.subquery = subquery;
    }

    public Expression getLeft() {
        return left;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public Quantifier getQuantifier() {
        return quantifier;
    }

    public Expression getSubquery() {
        return subquery;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitQuantifiedComparison(this);
    }

    @Override
    public String toString() {
        return left + " " + operator + " " + quantifier + " " + subquery;
    }
}
