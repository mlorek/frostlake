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

import java.util.Set;

/**
 * The aggregates and window functions the account answers when they are written with named arguments, under any
 * argument name and exactly as the positional call their values spell: {@code MEDIAN(x => n) OVER ()},
 * {@code FIRST_VALUE(abc => g) OVER (ORDER BY n)}, {@code CORR(x => n, y => f)}. Every aggregate and window
 * function was measured with one and with two named arguments, with {@code OVER} and without; the rest refuse
 * them ("function SUM does not support named arguments") once their arity is satisfied. The two sets follow no
 * rule of family or arity — AVG, HLL and APPROX_COUNT_DISTINCT take names only without {@code OVER}, FIRST_VALUE
 * and LAST_VALUE only with it — so they are kept as measured.
 */
public final class NamedArgumentWindowFunctions {

    /** The names answered with named arguments under {@code OVER}. */
    private static final Set<String> UNDER_OVER = Set.of(
        "ANY_VALUE", "BOOLAND_AGG", "BOOLOR_AGG", "BOOLXOR_AGG", "CORR", "COUNT_IF",
        "COVAR_POP", "COVAR_SAMP", "FIRST_VALUE", "KURTOSIS", "LAST_VALUE", "MEDIAN", "REGR_AVGX",
        "REGR_AVGY", "REGR_COUNT", "REGR_INTERCEPT", "REGR_R2", "REGR_SLOPE", "REGR_SXX", "REGR_SXY",
        "REGR_SYY", "SKEW", "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VARIANCE", "VARIANCE_POP",
        "VARIANCE_SAMP", "VAR_POP", "VAR_SAMP");

    /** The aggregates answered with named arguments as a plain (grouping) call. */
    private static final Set<String> PLAIN = Set.of(
        "ANY_VALUE", "APPROX_COUNT_DISTINCT", "AVG", "BOOLAND_AGG", "BOOLOR_AGG", "BOOLXOR_AGG", "CORR",
        "COUNT_IF", "COVAR_POP", "COVAR_SAMP", "HLL", "KURTOSIS", "MEDIAN", "REGR_AVGX", "REGR_AVGY",
        "REGR_COUNT", "REGR_INTERCEPT", "REGR_R2", "REGR_SLOPE", "REGR_SXX", "REGR_SXY", "REGR_SYY", "SKEW",
        "STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VARIANCE", "VARIANCE_POP", "VARIANCE_SAMP", "VAR_POP",
        "VAR_SAMP");

    private NamedArgumentWindowFunctions() {
    }

    /**
     * Whether the account takes named arguments for this function under OVER.
     *
     * @param name the canonical (upper-cased) function name
     * @return whether the named form is answered
     */
    public static boolean accepts(final String name) {
        return UNDER_OVER.contains(name);
    }

    /**
     * Whether the account takes named arguments for this aggregate written without OVER.
     *
     * @param name the canonical (upper-cased) function name
     * @return whether the named plain call is answered
     */
    public static boolean acceptsPlain(final String name) {
        return PLAIN.contains(name);
    }
}
