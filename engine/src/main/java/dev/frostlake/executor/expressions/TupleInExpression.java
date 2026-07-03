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
 * Row-constructor {@code IN} test: {@code (a, b) IN (SELECT x, y ...)} or
 * {@code (a, b) IN (1, 2, ...)}.
 *
 * <p>Added during the ANTLR expression-AST migration to support the grammar's
 * {@code TupleInListExpr} / {@code TupleInSubqueryExpr} alternatives — the scalar
 * {@link InExpression} cannot represent a multi-column left-hand side. For the list form the
 * right-hand flat list is grouped into tuples the size of the left side (the grammar does not
 * allow nested {@code (..)} tuples as list elements).
 */
public class TupleInExpression implements Expression {
    private final List<Expression> values;       // left-hand tuple
    private final List<Expression> listValues;   // right-hand flat list (null when subquery)
    private final SubqueryExpression subquery;    // right-hand subquery (null when list)
    private final boolean not;

    public TupleInExpression(final List<Expression> values, final List<Expression> listValues, final boolean not) {
        this.values = values;
        this.listValues = listValues;
        this.subquery = null;
        this.not = not;
    }

    public TupleInExpression(final List<Expression> values, final SubqueryExpression subquery, final boolean not) {
        this.values = values;
        this.listValues = null;
        this.subquery = subquery;
        this.not = not;
    }

    public List<Expression> getValues() {
        return values;
    }

    public List<Expression> getListValues() {
        return listValues;
    }

    public SubqueryExpression getSubquery() {
        return subquery;
    }

    public boolean hasSubquery() {
        return subquery != null;
    }

    public boolean isNot() {
        return not;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitTupleIn(this);
    }
}
