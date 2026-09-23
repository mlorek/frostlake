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
 * Represents array element access (e.g., arr[0], data[index])
 */
public class ArrayAccessExpression implements Expression {
    private final Expression array;
    private final Expression index;
    private SourcePosition position;

    public ArrayAccessExpression(final Expression array, final Expression index) {
        this.array = array;
        this.index = index;
    }

    /** Where the opening bracket stands, which a refusal of the subscripted base points at; null when unknown. */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

    public Expression getArray() {
        return array;
    }

    public Expression getIndex() {
        return index;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitArrayAccess(this);
    }

    @Override
    public String toString() {
        return array + "[" + index + "]";
    }
}
