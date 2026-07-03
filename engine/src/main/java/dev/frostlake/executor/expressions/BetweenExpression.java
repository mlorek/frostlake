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
 * Represents a BETWEEN expression (e.g., x BETWEEN 1 AND 10)
 */
public class BetweenExpression implements Expression {
    private final Expression value;
    private final Expression lower;
    private final Expression upper;
    private final boolean not;

    public BetweenExpression(final Expression value, final Expression lower, final Expression upper, final boolean not) {
        this.value = value;
        this.lower = lower;
        this.upper = upper;
        this.not = not;
    }

    public Expression getValue() {
        return value;
    }

    public Expression getLower() {
        return lower;
    }

    public Expression getUpper() {
        return upper;
    }

    public boolean isNot() {
        return not;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitBetween(this);
    }

    @Override
    public String toString() {
        return value + (not ? " NOT" : "") + " BETWEEN " + lower + " AND " + upper;
    }
}
