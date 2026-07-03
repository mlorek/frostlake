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
 * Represents an {@code x IS NULL} / {@code x IS NOT NULL} test.
 *
 * <p>Added during the ANTLR expression-AST migration: the grammar's {@code IsNullExpr}
 * alternative had no corresponding node (the old string evaluator handled IS NULL inline).
 */
public class IsNullExpression implements Expression {
    private final Expression operand;
    private final boolean not;

    public IsNullExpression(final Expression operand, final boolean not) {
        this.operand = operand;
        this.not = not;
    }

    public Expression getOperand() {
        return operand;
    }

    public boolean isNot() {
        return not;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitIsNull(this);
    }

    @Override
    public String toString() {
        return operand + (not ? " IS NOT NULL" : " IS NULL");
    }
}
