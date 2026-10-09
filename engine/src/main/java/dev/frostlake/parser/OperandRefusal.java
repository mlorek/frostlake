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

package dev.frostlake.parser;

/**
 * One refusal of an operand that read: a value after a quantifier where a query is taken, or OVER written after
 * what is no function. The account makes these while the statement compiles, before a table, a column or a
 * function of the statement is resolved, and makes the ones of the first kind - a value that can be nothing else
 * - before the ones that name a kind of function.
 */
public final class OperandRefusal {

    private final String message;
    private final int order;
    private final boolean first;

    OperandRefusal(final String message, final int order, final boolean first) {
        this.message = message;
        this.order = order;
        this.first = first;
    }

    /**
     * The sentence, without the words a compilation error starts with.
     *
     * @return the sentence
     */
    public String message() {
        return message;
    }

    /** Where the refused operand stands among the statement's tokens. */
    int order() {
        return order;
    }

    /**
     * Whether the refusal is of the kind made first: of a value, which a routine's body is refused for as its
     * compilation's own failure, where a refusal of a function's kind is the statement's.
     *
     * @return true for a refusal of a value
     */
    public boolean isOfValue() {
        return first;
    }
}
