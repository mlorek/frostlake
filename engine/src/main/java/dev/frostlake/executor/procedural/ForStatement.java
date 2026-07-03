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

public class ForStatement extends Statement {
    private final String variableName;
    private final BaseExpression iterable;       // cursor/list source, or the range's start expression
    private final BaseExpression toExpression;   // range end (null ⇒ a cursor/list FOR, not a range FOR)
    private final boolean reverse;               // range only: iterate from the high bound down to the low
    private final ProceduralBlock block;

    public ForStatement(final String variableName, final BaseExpression iterable, final ProceduralBlock block) {
        this(variableName, iterable, null, false, block);
    }

    public ForStatement(final String variableName, final BaseExpression iterable,
                        final BaseExpression toExpression, final boolean reverse, final ProceduralBlock block) {
        super(StatementType.FOR);
        this.variableName = variableName;
        this.iterable = iterable;
        this.toExpression = toExpression;
        this.reverse = reverse;
        this.block = block;
    }

    public String getVariableName() {
        return variableName;
    }

    public BaseExpression getIterable() {
        return iterable;
    }

    /** The range's end expression, or null for a cursor/list FOR. */
    public BaseExpression getToExpression() {
        return toExpression;
    }

    /** True for {@code FOR i IN start TO end} (integer range); false for cursor/list iteration. */
    public boolean isRange() {
        return toExpression != null;
    }

    /** True for {@code FOR i IN REVERSE start TO end} — iterate from end down to start. */
    public boolean isReverse() {
        return reverse;
    }

    public ProceduralBlock getBlock() {
        return block;
    }
}
