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


public class WhenClause {
    // Package-private, not private: read by {@link CaseExpression} now that this class is a top-level
    // type in the same package rather than a nested one.
    final Expression condition;
    final Expression result;
    final boolean operandMatch;

    public WhenClause(final Expression condition, final Expression result) {
        this(condition, result, false);
    }

    /**
     * A WHEN of either CASE form.
     *
     * @param condition    the condition, for a simple CASE the operand's equality to the WHEN value
     * @param result       the THEN result
     * @param operandMatch whether the condition matches a simple CASE's operand against its WHEN value
     */
    public WhenClause(final Expression condition, final Expression result, final boolean operandMatch) {
        this.condition = condition;
        this.result = result;
        this.operandMatch = operandMatch;
    }

    public Expression getCondition() {
        return condition;
    }

    public Expression getResult() {
        return result;
    }

    /**
     * Whether this WHEN belongs to a simple CASE ({@code CASE x WHEN v THEN r}), whose condition is the
     * operand's equality to the value. The planner matches such a value as it matches a DECODE search,
     * which it does not do for a searched CASE's condition: live settles {@code CASE n WHEN 7 THEN …} as
     * never taken over a column holding a NULL, and leaves {@code CASE WHEN n = 7 THEN …} open.
     *
     * @return true for a simple CASE's WHEN
     */
    public boolean isOperandMatch() {
        return operandMatch;
    }
}
