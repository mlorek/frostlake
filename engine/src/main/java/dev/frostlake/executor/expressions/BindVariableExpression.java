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

/**
 * A {@code :name} bind-variable reference — inside a Snowflake Scripting block, SQL statements
 * refer to the block's variables (and a procedure's parameters) as {@code :name}. Resolved at
 * evaluation time from the procedural scope.
 */
public class BindVariableExpression implements Expression {

    private final String varName;
    private final SourcePosition position;

    public BindVariableExpression(final String varName) {
        this(varName, null);
    }

    public BindVariableExpression(final String varName, final SourcePosition position) {
        this.varName = varName;
        this.position = position;
    }

    public String getVarName() {
        return varName;
    }

    /**
     * Where the reference begins, relative to the fragment being parsed — the COLON's offset, which
     * is what live reports for an unresolvable one: {@code :nope} in
     * {@code BEGIN RETURN :nope; END} is position 13, the colon, not the name after it.
     */
    public SourcePosition getPosition() {
        return position;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitBindVariable(this);
    }

    @Override
    public String toString() {
        return ":" + varName;
    }
}
