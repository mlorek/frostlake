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

public class ReturnStatement extends Statement {
    private final BaseExpression expression;
    /** The value read as a SQL expression from the same parse tree, to type the result; null when unreadable. */
    private Expression sqlExpression;

    public ReturnStatement(final BaseExpression expression) {
        super(StatementType.RETURN);
        this.expression = expression;
    }

    public BaseExpression getExpression() {
        return expression;
    }

    public Expression getSqlExpression() {
        return sqlExpression;
    }

    public void setSqlExpression(final Expression sqlExpression) {
        this.sqlExpression = sqlExpression;
    }
}
