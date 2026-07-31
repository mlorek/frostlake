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

import java.util.List;

/**
 * A lambda argument to a higher-order function — {@code <param> -> <body>} or
 * {@code (<param>, …) -> <body>} (e.g. {@code x -> x + 1}, {@code (acc, val) -> acc + val}). Parameter
 * names are kept alongside any DECLARED parameter type, which is significant: an element normally binds
 * as a VARIANT, so arithmetic on it yields FLOAT, while a declared type casts it first. Live-verified
 * on a real account: {@code TRANSFORM([1,2], a -> a * 2)} is
 * {@code [2.000000000000000e+00,4.000000000000000e+00]} but {@code TRANSFORM([1,2], a INT -> a * 2)} is
 * {@code [2,4]}. It is not evaluated on its own — {@code TRANSFORM}/{@code FILTER}/{@code REDUCE} apply
 * the body per array element with the parameters bound to the element (and accumulator).
 */
public class LambdaExpression implements Expression {
    private final List<String> parameters;
    private final List<String> parameterTypes;
    private final Expression body;

    public LambdaExpression(final List<String> parameters, final List<String> parameterTypes,
                            final Expression body) {
        this.parameters = parameters;
        this.parameterTypes = parameterTypes;
        this.body = body;
    }

    public List<String> getParameters() {
        return parameters;
    }

    /**
     * The declared type of each parameter, positionally — {@code null} where the parameter was written
     * without one.
     */
    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public Expression getBody() {
        return body;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitLambda(this);
    }

    @Override
    public String toString() {
        return parameters + " -> " + body;
    }
}
