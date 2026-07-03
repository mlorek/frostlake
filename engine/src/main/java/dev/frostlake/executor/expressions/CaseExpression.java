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

import java.util.List;

/**
 * Represents a CASE expression
 */
public class CaseExpression implements Expression {
    private final List<WhenClause> whenClauses;
    private final Expression elseExpression;

    public static class WhenClause {
        private final Expression condition;
        private final Expression result;

        public WhenClause(final Expression condition, final Expression result) {
            this.condition = condition;
            this.result = result;
        }

        public Expression getCondition() {
            return condition;
        }

        public Expression getResult() {
            return result;
        }
    }

    public CaseExpression(final List<WhenClause> whenClauses, final Expression elseExpression) {
        this.whenClauses = whenClauses;
        this.elseExpression = elseExpression;
    }

    public List<WhenClause> getWhenClauses() {
        return whenClauses;
    }

    public Expression getElseExpression() {
        return elseExpression;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitCaseExpression(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CASE");
        for (final WhenClause when : whenClauses) {
            sb.append(" WHEN ").append(when.condition).append(" THEN ").append(when.result);
        }
        if (elseExpression != null) {
            sb.append(" ELSE ").append(elseExpression);
        }
        sb.append(" END");
        return sb.toString();
    }
}
