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

    public WhenClause(final Expression condition, final Expression result) {
        this.condition = condition;
        this.result = result;
    }

    public Expression getCondition() {
        return condition;
    }

    public Expression getResult() {
        return result;
    }
}
