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
 * Represents an INTERVAL expression (e.g., INTERVAL '10' DAY)
 */
public class IntervalExpression implements Expression {
    private final Expression valueExpression;
    private final IntervalUnit unit;

    public IntervalExpression(final Expression valueExpression, final IntervalUnit unit) {
        this.valueExpression = valueExpression;
        this.unit = unit;
    }

    public Expression getValueExpression() {
        return valueExpression;
    }

    public IntervalUnit getUnit() {
        return unit;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitInterval(this);
    }

    @Override
    public String toString() {
        return "INTERVAL " + valueExpression + " " + unit;
    }
}
