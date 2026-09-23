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

import dev.frostlake.types.DataType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The types of a block's variables, scoped as the variables themselves are. A LET or a DECLARE in an IF branch,
 * a CASE branch, a loop body or a nested block declares a new variable that hides an outer one of the same name
 * only until that body ends, so the outer variable's type governs again after it: {@code LET x := 1; IF (TRUE)
 * THEN LET x := TRUE; END IF; RETURN x + 1} is 2, and an assignment after the branch converts to the outer
 * variable's type (live-verified). An assignment to an outer variable inside the body does not change its type,
 * but it does settle the type a bare RETURN of it reports, so that settling outlives the body.
 */
final class DeclaredTypeScopes {

    /** The declared types when each open scope was entered, innermost first. */
    private final Deque<Map<String, DataType>> declared = new ArrayDeque<Map<String, DataType>>();

    /** The initialisers' own types when each open scope was entered, innermost first. */
    private final Deque<Map<String, DataType>> initialised = new ArrayDeque<Map<String, DataType>>();

    /** The conversions' types when each open scope was entered, innermost first. */
    private final Deque<Map<String, DataType>> converted = new ArrayDeque<Map<String, DataType>>();

    /**
     * A scope opens.
     *
     * @param declaredTypes    the declared type of each variable, by upper-cased name
     * @param initialiserTypes the own type of each untyped declaration's initialiser, by upper-cased name
     * @param conversionTypes  the type each variable's conversion produced, by upper-cased name
     */
    void enter(final Map<String, DataType> declaredTypes, final Map<String, DataType> initialiserTypes,
               final Map<String, DataType> conversionTypes) {
        declared.push(new HashMap<String, DataType>(declaredTypes));
        initialised.push(new HashMap<String, DataType>(initialiserTypes));
        converted.push(new HashMap<String, DataType>(conversionTypes));
    }

    /**
     * The innermost scope closes: every declared type is what it was when the scope opened, and the names the
     * scope declared get back the rest of what they had then.
     *
     * @param discarded        the upper-cased names the scope declared
     * @param declaredTypes    the declared type of each variable, restored in place
     * @param initialiserTypes the own type of each untyped declaration's initialiser, restored in place
     * @param conversionTypes  the type each variable's conversion produced, restored in place
     */
    void exit(final Set<String> discarded, final Map<String, DataType> declaredTypes,
              final Map<String, DataType> initialiserTypes, final Map<String, DataType> conversionTypes) {
        if (declared.isEmpty()) {
            return;
        }
        declaredTypes.clear();
        declaredTypes.putAll(declared.pop());
        restore(initialised.pop(), initialiserTypes, discarded);
        restore(converted.pop(), conversionTypes, discarded);
    }

    private static void restore(final Map<String, DataType> saved, final Map<String, DataType> current,
                                final Set<String> names) {
        for (final String name : names) {
            final DataType type = saved.get(name);
            if (type == null) {
                current.remove(name);
            } else {
                current.put(name, type);
            }
        }
    }
}
