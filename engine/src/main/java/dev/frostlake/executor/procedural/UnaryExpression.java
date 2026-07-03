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

package dev.frostlake.executor.procedural;

import dev.frostlake.executor.expressions.UnaryOperator;

/**
 * Represents a unary operation (e.g., NOT x, -5, EXISTS(...))
 */
public class UnaryExpression extends BaseExpression {
    private final BaseExpression operand;
    private final UnaryOperator operator;

    public UnaryExpression(final UnaryOperator operator, final BaseExpression operand) {
        this.operator = operator;
        this.operand = operand;
    }

    public BaseExpression getOperand() {
        return operand;
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    @Override
    public String toString() {
        return operator + " " + operand;
    }
}
