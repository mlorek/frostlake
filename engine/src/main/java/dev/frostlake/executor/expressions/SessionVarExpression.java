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

public class SessionVarExpression implements Expression {

    private final String varName;
    private SourcePosition position;

    public SessionVarExpression(final String varName) {
        this.varName = varName;
    }

    public String getVarName() {
        return varName;
    }

    /**
     * Where the reference was written, as an offset into the expression's own text — stamped by the
     * builder, so a refusal that points AT the variable can resolve it back to the statement.
     *
     * @return the recorded position, or null when nothing stamped one
     */
    public SourcePosition getPosition() {
        return position;
    }

    /**
     * Record where the reference was written.
     *
     * @param where its position within the expression's text
     */
    public void setPosition(final SourcePosition where) {
        this.position = where;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitSessionVar(this);
    }
}
