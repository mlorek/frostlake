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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.types.DataType;

public class SetStatement extends Statement {
    private final String variableName;
    private final BaseExpression expression;
    /** The type a LET inside a compound body wrote, or null. */
    private final DataType declaredType;
    /** Whether this is a LET — a declaration whose type is the written one or the initialiser's. */
    private final boolean declaration;
    // Original (whitespace-preserved) source text of the right-hand side, when available. Used to detect a
    // RESULTSET assignment `rs := (SELECT …)` — which stores the query, not the scalar it evaluates to.
    private final String rawExpressionText;
    /** A LET's initialiser as a SQL expression, which types an untyped declaration, or null. */
    private Expression sqlInitialiser;

    public SetStatement(final String variableName, final BaseExpression expression) {
        this(variableName, expression, (String) null);
    }

    public SetStatement(final String variableName, final BaseExpression expression, final String rawExpressionText) {
        this(variableName, expression, rawExpressionText, null, false);
    }

    /** A LET inside a compound body: an assignment that also declares the name's type. */
    public SetStatement(final String variableName, final BaseExpression expression, final DataType declaredType) {
        this(variableName, expression, null, declaredType, true);
    }

    private SetStatement(final String variableName, final BaseExpression expression, final String rawExpressionText,
                         final DataType declaredType, final boolean declaration) {
        super(StatementType.SET);
        this.variableName = variableName;
        this.expression = expression;
        this.rawExpressionText = rawExpressionText;
        this.declaredType = declaredType;
        this.declaration = declaration;
    }

    public DataType getDeclaredType() {
        return declaredType;
    }

    public boolean isDeclaration() {
        return declaration;
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

    public Expression getSqlInitialiser() {
        return sqlInitialiser;
    }

    public void setSqlInitialiser(final Expression sqlInitialiser) {
        this.sqlInitialiser = sqlInitialiser;
    }
}
