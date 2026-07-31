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

public class VariableExpression extends BaseExpression {
    private final String name;
    private final boolean bindForm;

    /** A name written BARE (no leading colon) — legal only in a Snowflake Scripting expression. */
    public VariableExpression(final String name) {
        this(name, false);
    }

    public VariableExpression(final String name, final boolean bindForm) {
        this.name = name;
        this.bindForm = bindForm;
    }

    public String getName() {
        return name;
    }

    /**
     * True when the reference was written as {@code :name}. Snowflake accepts only that form where a
     * scripting name feeds an embedded SQL statement (a CALL argument, DML, a query); a bare name
     * there is an identifier and fails with {@code invalid identifier}.
     */
    public boolean isBindForm() {
        return bindForm;
    }
}
