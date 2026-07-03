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

public class ReturnTableStatement extends Statement {
    private final BaseExpression expression;
    private final String query;

    /** RETURN TABLE(expression) — the expression evaluates to a RESULTSET value/variable. */
    public ReturnTableStatement(final BaseExpression expression) {
        super(StatementType.RETURN_TABLE);
        this.expression = expression;
        this.query = null;
    }

    /** RETURN TABLE(SELECT ...) — the table is produced by executing this query directly. */
    public ReturnTableStatement(final String query) {
        super(StatementType.RETURN_TABLE);
        this.expression = null;
        this.query = query;
    }

    public BaseExpression getExpression() {
        return expression;
    }

    public String getQuery() {
        return query;
    }
}
