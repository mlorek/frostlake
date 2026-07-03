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
}
