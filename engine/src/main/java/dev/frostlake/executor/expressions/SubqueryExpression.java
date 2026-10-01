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
 * Represents a subquery expression (scalar subquery or EXISTS)
 */
public class SubqueryExpression implements Expression {
    private final String subquery;  // SQL text of the subquery
    private SourcePosition position;
    private SourcePosition queryPosition;

    public SubqueryExpression(final String subquery) {
        this.subquery = subquery;
    }

    public String getSubquery() {
        return subquery;
    }

    /**
     * Where a refusal of the subquery's shape is anchored in the statement: its own SELECT, or the first
     * parenthesis of the call argument it is the whole of (see {@link SubqueryAnchor}).
     *
     * @return the position, or null
     */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

    /**
     * Where the subquery's own text begins in the statement, which every position inside it counts from.
     * That is its {@link #getPosition} unless the subquery is anchored elsewhere, as an EXISTS is on its
     * keyword.
     *
     * @return the position, or null
     */
    public SourcePosition getQueryPosition() {
        return queryPosition != null ? queryPosition : position;
    }

    public void setQueryPosition(final SourcePosition queryPosition) {
        this.queryPosition = queryPosition;
    }

    /**
     * Whether an expression holds a subquery anywhere outside a lambda's body.
     *
     * @param expression the expression
     * @return true when a subquery occurs in it
     */
    public static boolean occursIn(final Expression expression) {
        final SubqueryCollectWalk walk = new SubqueryCollectWalk();
        expression.accept(walk);
        return !walk.subqueries().isEmpty();
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitSubquery(this);
    }

    @Override
    public String toString() {
        return "(" + subquery + ")";
    }
}
