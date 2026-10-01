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

import java.util.List;

public class ExecuteImmediateExpression extends BaseExpression {
    private final BaseExpression sqlExpression;
    private final List<BaseExpression> usingBindings;
    private int statementLine = -1;
    private int statementPosition = -1;

    public ExecuteImmediateExpression(final BaseExpression sqlExpression, final List<BaseExpression> usingBindings) {
        this.sqlExpression = sqlExpression;
        this.usingBindings = usingBindings;
    }

    public BaseExpression getSqlExpression() {
        return sqlExpression;
    }

    /** Values for the {@code USING (...)} clause, bound positionally to {@code ?} placeholders (empty if none). */
    public List<BaseExpression> getUsingBindings() {
        return usingBindings;
    }

    /**
     * Note where the EXECUTE IMMEDIATE begins: a failure running its text is the statement's there.
     *
     * @param line the first line of the EXECUTE IMMEDIATE
     * @param position its first column
     */
    public void setStatementAt(final int line, final int position) {
        this.statementLine = line;
        this.statementPosition = position;
    }

    /** The EXECUTE IMMEDIATE's first line, or -1 when unknown. */
    public int getStatementLine() {
        return statementLine;
    }

    /** The EXECUTE IMMEDIATE's first column, or -1 when unknown. */
    public int getStatementPosition() {
        return statementPosition;
    }
}
