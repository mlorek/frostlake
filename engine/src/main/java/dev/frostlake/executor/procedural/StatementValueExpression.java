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

/**
 * A statement written in parentheses as an assignment's value — {@code rs := (SHOW TABLES)}, {@code rs := (INSERT …)}.
 * A RESULTSET assigned one runs it and holds its answer; any other target refuses it when the assignment runs (see
 * {@code AssignedStatement}). Its source position is the statement's first word.
 */
public class StatementValueExpression extends BaseExpression {

    private final String statementText;

    /**
     * @param statementText the statement as written, without the parentheses
     */
    public StatementValueExpression(final String statementText) {
        this.statementText = statementText;
    }

    /** The statement as written. */
    public String getStatementText() {
        return statementText;
    }
}
