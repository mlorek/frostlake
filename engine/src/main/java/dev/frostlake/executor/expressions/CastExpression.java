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
 * Represents a type cast (e.g., CAST(x AS INTEGER), x::VARCHAR). When {@code tryMode} is set (TRY_CAST),
 * a failed conversion yields NULL instead of raising an error.
 */
public class CastExpression implements Expression {
    private final Expression expression;
    private final String targetType;
    private final boolean tryMode;

    public CastExpression(final Expression expression, final String targetType) {
        this(expression, targetType, false);
    }

    public CastExpression(final Expression expression, final String targetType, final boolean tryMode) {
        this.expression = expression;
        this.targetType = targetType;
        this.tryMode = tryMode;
    }

    public Expression getExpression() {
        return expression;
    }

    public String getTargetType() {
        return targetType;
    }

    public boolean isTryMode() {
        return tryMode;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitCast(this);
    }

    @Override
    public String toString() {
        return "CAST(" + expression + " AS " + targetType + ")";
    }
}
