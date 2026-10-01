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

package dev.frostlake.functions.window;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The window functions {@code WindowFunctionEvaluator} implements itself, i.e. the names that are valid
 * in front of an {@code OVER (...)} clause without being registered in the {@code FunctionRegistry}
 * scalar / aggregate maps.
 *
 * <p>This set is <strong>load-bearing for dispatch</strong>, not documentation: the evaluator rejects
 * any name that is neither declared here nor a registered aggregate <em>before</em> it reaches its
 * switch, so a new {@code case} that is not declared here simply does not work. That is what keeps
 * {@code SHOW FUNCTIONS} — which enumerates this set through
 * {@link dev.frostlake.functions.FunctionRegistry#allDispatchableNames()} — from drifting away from
 * what the engine can actually run, the drift that left 14 window functions unlisted.
 *
 * <p>Snowflake lists every one of these in {@code SHOW FUNCTIONS} / {@code SHOW BUILTIN FUNCTIONS}
 * ({@code SHOW BUILTIN FUNCTIONS LIKE 'ROW_NUMBER'} returns one row with
 * {@code is_builtin = 'Y'}, and the same holds for RANK / DENSE_RANK / LAG / LEAD / FIRST_VALUE /
 * LAST_VALUE / NTILE / PERCENT_RANK / CUME_DIST / RATIO_TO_REPORT / CONDITIONAL_TRUE_EVENT).
 */
public final class WindowFunctionNames {

    /**
     * Every name {@code WindowFunctionEvaluator}'s switch handles. COUNT / SUM / AVG / MIN / MAX are
     * included because the evaluator has dedicated frame-aware cases for them, even though they are
     * also registered aggregates.
     */
    private static final SortedSet<String> NAMES = Collections.unmodifiableSortedSet(
        new TreeSet<String>(Arrays.asList(
            "AVG",
            "CONDITIONAL_CHANGE_EVENT",
            "CONDITIONAL_TRUE_EVENT",
            "COUNT",
            "CUME_DIST",
            "DENSE_RANK",
            "FIRST_VALUE",
            "LAG",
            "LAST_VALUE",
            "LEAD",
            "MAX",
            "MIN",
            "NTH_VALUE",
            "NTILE",
            "PERCENT_RANK",
            "RANK",
            "RATIO_TO_REPORT",
            "ROW_NUMBER",
            "SUM"
        )));

    /**
     * The window functions whose window must carry an ORDER BY. Live refuses each of them without one — "Window
     * function type [ROW_NUMBER] requires ORDER BY in window specification.", naming the function — and a
     * PARTITION BY alone does not satisfy it. The aggregates used as windows are not among them: SUM(a) OVER ()
     * answers.
     */
    private static final Set<String> ORDER_REQUIRED = Set.of(
        "ROW_NUMBER", "RANK", "DENSE_RANK", "PERCENT_RANK", "CUME_DIST", "NTILE",
        "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE");

    private WindowFunctionNames() {
    }

    /**
     * Whether the window function needs an ORDER BY in its window.
     *
     * @param name the function's name, upper-cased
     * @return whether a window without an ORDER BY is refused for it
     */
    public static boolean requiresOrderBy(final String name) {
        return name != null && ORDER_REQUIRED.contains(name);
    }

    /** Whether {@code WindowFunctionEvaluator} implements this name itself (case-insensitive). */
    public static boolean handles(final String name) {
        return name != null && NAMES.contains(name.toUpperCase());
    }

    /** Every window-function name the evaluator implements, upper-cased and sorted. */
    public static SortedSet<String> names() {
        return NAMES;
    }
}
