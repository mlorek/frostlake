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

package dev.frostlake.executor;

import java.util.List;

/**
 * An expression with every SIBLING SELECT ALIAS it mentions replaced by that alias's defining
 * expression. A select list may name its own output — {@code SELECT d AS dd, MEDIAN(dd)} — and a name
 * used that way means the expression behind it, wherever in the expression it appears rather than only
 * when it IS the whole expression.
 *
 * <p>The substitution repeats, because an alias may be defined in terms of another
 * ({@code SELECT d AS dd, dd AS ee, … ee …}), and stops as soon as a pass changes nothing. It is
 * bounded at five passes: that is enough for any chain worth writing, and it means a definition that
 * mentions its own name cannot spin here.
 *
 * <p>The replacement is by IDENTIFIER TOKEN, so a name inside a string literal or forming part of a
 * longer identifier is left alone. Whether an alias should be substituted at all is the CALLER's
 * decision — a real column of the same name outranks the alias, and only the caller knows the
 * relations in scope.
 */
public final class SiblingAliasSubstitution {

    /** Enough passes for any alias chain worth writing, and a stop for a self-referring definition. */
    private static final int PASSES = 5;

    private SiblingAliasSubstitution() {
    }

    /**
     * @param text the expression text
     * @param names the alias names, canonical
     * @param exprs their defining expressions, 1:1 with {@code names}
     * @return the text with those aliases expanded
     */
    public static String applied(final String text, final List<String> names,
                                 final List<String> exprs) {
        String result = text;
        for (int pass = 0; pass < PASSES; pass++) {
            String next = result;
            for (int i = 0; i < names.size(); i++) {
                next = SqlIdentifierSubstitution.substitute(next, names.get(i), exprs.get(i));
            }
            if (next.equals(result)) {
                break;
            }
            result = next;
        }
        return result;
    }
}
