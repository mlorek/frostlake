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
 * {@code EXECUTE IMMEDIATE <sql-expr> [USING (v1, v2, …)]} used as an expression: the SQL string (itself an
 * expression — a literal, bind variable, concatenation, …) is evaluated, any USING values are bound to its
 * {@code ?} placeholders, the dynamic statement is executed, and its first column of the first row is
 * returned (scalar), like a scalar subquery.
 */
public class ExecuteImmediateExpression implements Expression {

    private final Expression sqlExpression;
    private final List<Expression> usingBindings;

    public ExecuteImmediateExpression(final Expression sqlExpression, final List<Expression> usingBindings) {
        this.sqlExpression = sqlExpression;
        this.usingBindings = usingBindings;
    }

    public Expression getSqlExpression() {
        return sqlExpression;
    }

    /** The USING bind expressions (bound positionally to {@code ?} placeholders), or null/empty if none. */
    public List<Expression> getUsingBindings() {
        return usingBindings;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitExecuteImmediate(this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EXECUTE IMMEDIATE ").append(sqlExpression);
        if (usingBindings != null && !usingBindings.isEmpty()) {
            sb.append(" USING (");
            for (int i = 0; i < usingBindings.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(usingBindings.get(i));
            }
            sb.append(")");
        }
        return sb.toString();
    }
}
