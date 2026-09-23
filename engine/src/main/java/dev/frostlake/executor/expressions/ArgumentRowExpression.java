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
 * A parenthesized list given to a named parameter — {@code f(x => (1, 2))} — which the account reads as one ROW
 * value. No scalar parameter takes one: the call that holds it is refused before anything is evaluated,
 * {@code function UPPER does not support named arguments} for a built-in and
 * {@code named arguments [X] do not match any signature for function FX} for a user-defined function. It is no
 * call of its own, so no refusal names it as an unknown function.
 */
public final class ArgumentRowExpression extends FunctionCallExpression {

    /**
     * The list's values, in order.
     *
     * @param elements the values
     */
    public ArgumentRowExpression(final List<Expression> elements) {
        super("ROW", elements);
    }
}
