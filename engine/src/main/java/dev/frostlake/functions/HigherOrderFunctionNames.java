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

package dev.frostlake.functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The higher-order functions — the ones whose last argument is a lambda, so the argument must NOT be
 * pre-evaluated. {@code ExpressionEvaluatorVisitor} applies the lambda per array element instead of
 * looking the name up in the {@code FunctionRegistry} maps, which is why these names were invisible to
 * {@code SHOW FUNCTIONS} while being perfectly dispatchable.
 *
 * <p>This set is <strong>load-bearing for dispatch</strong>: the visitor tests membership here to decide
 * whether to take the higher-order path, and {@code SHOW FUNCTIONS} enumerates the same set through
 * {@link FunctionRegistry#allDispatchableNames()}, so the listing cannot drift from the dispatch again.
 *
 * <p>Snowflake lists all three ({@code SHOW BUILTIN FUNCTIONS LIKE 'TRANSFORM'}
 * returns {@code TRANSFORM(ARRAY, FUNCTION(VARIANT)) RETURN ARRAY}, {@code is_builtin = 'Y'}).
 */
public final class HigherOrderFunctionNames {

    private static final SortedSet<String> NAMES = Collections.unmodifiableSortedSet(
        new TreeSet<String>(Arrays.asList(
            "FILTER",
            "REDUCE",
            "TRANSFORM"
        )));

    private HigherOrderFunctionNames() {
    }

    /** Whether this name takes a lambda argument and is evaluated by the higher-order path. */
    public static boolean contains(final String name) {
        return name != null && NAMES.contains(name.toUpperCase());
    }

    /** Every higher-order function name, upper-cased and sorted. */
    public static SortedSet<String> names() {
        return NAMES;
    }
}
