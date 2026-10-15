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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Numbers the calls of a parsed expression by name: the first call of a name written in it is 1, the next 2, in
 * the order they are written. A handler runtime tells the calls written in a SQL function's body apart by this
 * count, since the body is read anew for each row's argument values and its calls are other nodes each time while
 * their counts stay (see {@code CallSite}).
 *
 * <p>It subclasses the printer for its TRAVERSAL only; the printed string is discarded. A subquery is text of its
 * own, numbered when it is parsed.
 */
public final class CallOrdinalWalk extends AstPrinterVisitor {

    private final Map<String, Integer> counted = new HashMap<String, Integer>();

    private CallOrdinalWalk() {
    }

    /**
     * Numbers the calls of a freshly parsed expression. One the walk cannot read is left unnumbered, and its calls
     * are told apart by themselves.
     *
     * @param parsed the expression
     */
    public static void number(final Expression parsed) {
        if (parsed == null) {
            return;
        }
        try {
            parsed.accept(new CallOrdinalWalk());
        } catch (final RuntimeException unreadable) {
            // Unnumbered calls keep a count of 0.
        }
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        final String name = String.valueOf(expr.getFunctionName()).toUpperCase(Locale.ROOT);
        final Integer seen = counted.get(name);
        final int ordinal = seen == null ? 1 : seen.intValue() + 1;
        counted.put(name, Integer.valueOf(ordinal));
        expr.setCallOrdinal(ordinal);
        return super.visitFunctionCall(expr);
    }
}
