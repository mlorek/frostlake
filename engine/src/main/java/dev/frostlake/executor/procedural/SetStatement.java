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

public class SetStatement extends Statement {
    private final String variableName;
    private final BaseExpression expression;
    // Original (whitespace-preserved) source text of the right-hand side, when available. Used to detect a
    // RESULTSET assignment `rs := (SELECT …)` — which stores the query, not the scalar it evaluates to.
    private final String rawExpressionText;

    public SetStatement(final String variableName, final BaseExpression expression) {
        this(variableName, expression, null);
    }

    public SetStatement(final String variableName, final BaseExpression expression, final String rawExpressionText) {
        super(StatementType.SET);
        this.variableName = variableName;
        this.expression = expression;
        this.rawExpressionText = rawExpressionText;
    }

    public String getVariableName() {
        return variableName;
    }

    public BaseExpression getExpression() {
        return expression;
    }

    public String getRawExpressionText() {
        return rawExpressionText;
    }
}
