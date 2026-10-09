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
 * A parenthesized list of two or more values — {@code (1, 2)} — which the account reads as one ROW value wherever a
 * value may stand (live-verified). Rows compare with rows; nothing else takes one: an operator and a function refuse
 * it by its type, {@code ROW(NUMBER(1,0), NUMBER(1,0))}, and a select item as a list element of several columns. It
 * prints as {@code ROW(1, 2)}, the way the account's sentences spell it, and it is no call of its own, so no refusal
 * names it as an unknown function.
 */
public class RowExpression extends FunctionCallExpression {

    /**
     * The row's values, in order.
     *
     * @param elements the values
     */
    public RowExpression(final List<Expression> elements) {
        super("ROW", elements);
    }
}
