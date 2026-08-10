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
 * The bare word {@code DEFAULT} where a DML value is expected — {@code INSERT … VALUES (DEFAULT)},
 * {@code UPDATE t SET c = DEFAULT}, and the same two inside MERGE. It means "write this column's
 * declared default", so it is not a value at all until a target column is known, which is why it
 * travels as a marker instead of evaluating to something.
 *
 * <p>The line live draws is where the word STANDS ALONE. In any larger expression it is an ordinary
 * name that resolves to nothing — {@code UPDATE t SET c = DEFAULT + 1} is "invalid identifier
 * 'DEFAULT'" — and that falls out of this design rather than being coded twice: a write path takes the
 * marker only when it IS the whole value, and every other route evaluates it, which raises exactly
 * that refusal.
 *
 * @see dev.frostlake.executor.expressions.ExpressionAstBuilder for where the marker is built
 */
public class DefaultMarkerExpression implements Expression {

    private final SourcePosition where;

    public DefaultMarkerExpression(final SourcePosition where) {
        this.where = where;
    }

    /** Where the word was written, for the refusal raised when it is evaluated rather than written. */
    public SourcePosition getWhere() {
        return where;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitDefaultMarker(this);
    }

    @Override
    public String toString() {
        return "DEFAULT";
    }
}
