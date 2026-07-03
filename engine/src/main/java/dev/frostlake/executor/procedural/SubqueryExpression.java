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
 * Represents a subquery expression used in EXISTS, IN, or scalar subquery contexts
 */
public class SubqueryExpression extends BaseExpression {
    private final String subquery;

    public SubqueryExpression(final String subquery) {
        this.subquery = subquery;
    }

    public String getSubquery() {
        return subquery;
    }

    @Override
    public String toString() {
        return "(" + subquery + ")";
    }
}
