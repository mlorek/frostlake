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
 * Represents an IN expression (e.g., x IN (1, 2, 3) or x IN (SELECT ...))
 */
public class InExpression implements Expression {
    private final Expression value;
    private final List<Expression> values;  // For value list
    private final SubqueryExpression subquery;  // For subquery
    private final boolean not;

    public InExpression(final Expression value, final List<Expression> values, final boolean not) {
        this.value = value;
        this.values = values;
        this.subquery = null;
        this.not = not;
    }

    public InExpression(final Expression value, final SubqueryExpression subquery, final boolean not) {
        this.value = value;
        this.values = null;
        this.subquery = subquery;
        this.not = not;
    }

    public Expression getValue() {
        return value;
    }

    public List<Expression> getValues() {
        return values;
    }

    public SubqueryExpression getSubquery() {
        return subquery;
    }

    public boolean isNot() {
        return not;
    }

    public boolean hasSubquery() {
        return subquery != null;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitIn(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(value);
        if (not) sb.append(" NOT");
        sb.append(" IN (");
        if (subquery != null) {
            sb.append(subquery);
        } else {
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(values.get(i));
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
