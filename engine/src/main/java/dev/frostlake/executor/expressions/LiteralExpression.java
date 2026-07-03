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
 * Represents a literal value (string, number, boolean, null)
 */
public class LiteralExpression implements Expression {
    private final Object value;
    private final LiteralType type;

    public LiteralExpression(final Object value, final LiteralType type) {
        this.value = value;
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public LiteralType getType() {
        return type;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitLiteral(this);
    }

    @Override
    public String toString() {
        if (type == LiteralType.NULL) {
            return "NULL";
        }
        if (type == LiteralType.STRING) {
            return "'" + value + "'";
        }
        return String.valueOf(value);
    }
}
