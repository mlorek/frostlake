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
 * Represents a unary operation (e.g., NOT x, -5, EXISTS(...))
 */
public class UnaryOperationExpression implements Expression {
    private final Expression operand;
    private final UnaryOperator operator;
    /** Where the operator token sits in the fragment that was parsed, or null when nothing recorded it. */
    private SourcePosition position;

    public UnaryOperationExpression(final UnaryOperator operator, final Expression operand) {
        this.operator = operator;
        this.operand = operand;
    }

    public Expression getOperand() {
        return operand;
    }

    public UnaryOperator getOperator() {
        return operator;
    }

    /**
     * The operator's own place in the parsed fragment — a sign is refused AT the sign: live anchors
     * "Invalid argument types for function 'NEGATE': (BOOLEAN)" on the {@code -}, and the
     * 'UNARY PLUS' twin on the {@code +}, wherever the operand starts.
     *
     * @return the recorded position, or null
     */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition where) {
        this.position = where;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitUnaryOperation(this);
    }

    @Override
    public String toString() {
        return operator + " " + operand;
    }
}
