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
 * {@code PRIOR <column>} inside a {@code CONNECT BY} predicate: the column's value on the PARENT row of
 * the hierarchy step being considered, as opposed to the candidate child row every other reference reads.
 *
 * <p>The hierarchy expansion evaluates the predicate against a combined row that carries the candidate's
 * values followed by the parent's, exposed under {@link #PRIOR_PREFIX}-prefixed column names — so this is
 * modelled as an ordinary {@link ColumnReferenceExpression} to that prefixed name and every consumer that
 * pattern-matches on column references (equi-key extraction, GROUP BY resolution, the AST printer) keeps
 * working unchanged. Outside a CONNECT BY the prefixed column does not exist, and the evaluator turns the
 * lookup failure into an explicit "PRIOR is only allowed in a CONNECT BY clause" error.
 */
public class PriorExpression extends ColumnReferenceExpression {

    /** Prefix of the synthesized parent-row column names produced by the CONNECT BY expansion. */
    public static final String PRIOR_PREFIX = "PRIOR$";

    private final String baseColumnName;

    public PriorExpression(final String baseColumnName) {
        super(PRIOR_PREFIX + baseColumnName);
        this.baseColumnName = baseColumnName;
    }

    /** The column name as written after {@code PRIOR}, for error messages. */
    public String getBaseColumnName() {
        return baseColumnName;
    }

    @Override
    public String toString() {
        return "PRIOR " + baseColumnName;
    }
}
