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
 * The spread operator {@code **<expr>}: inside an array constructor or a function argument list the
 * inner ARRAY's elements are spliced in as individual elements/arguments; inside an object
 * constructor the inner OBJECT's pairs are merged in. The splice happens where the surrounding
 * container evaluates its parts — a spread reaching plain evaluation on its own is an error.
 */
public class SpreadExpression implements Expression {

    private final Expression inner;

    public SpreadExpression(final Expression inner) {
        this.inner = inner;
    }

    public Expression getInner() {
        return inner;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitSpread(this);
    }
}
