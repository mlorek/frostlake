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

import java.util.Set;

/**
 * The aggregates the account refuses a DISTINCT for, plain or windowed, once their argument count is judged:
 * {@code invalid use of 'distinct' for function 'MEDIAN(DISTINCT RT.N)'}, echoed from the plan with no position.
 * Every aggregate was measured with DISTINCT; the rest answer it (SUM, AVG, COUNT, the deviations, ARRAY_AGG,
 * LISTAGG, ANY_VALUE, the bitwise aggregates, …), and the set follows no family — COUNT_IF and the three boolean
 * aggregates refuse it where MIN and MAX take it — so it is kept as measured.
 */
public final class AggregateDistinctRefusals {

    private static final Set<String> NAMES = Set.of(
        "APPROX_PERCENTILE", "BOOLAND_AGG", "BOOLOR_AGG", "BOOLXOR_AGG", "CORR", "COUNT_IF", "COVAR_POP",
        "COVAR_SAMP", "MEDIAN", "MODE", "REGR_AVGX", "REGR_AVGY", "REGR_COUNT", "REGR_INTERCEPT", "REGR_R2",
        "REGR_SLOPE", "REGR_SXX", "REGR_SXY", "REGR_SYY");

    private AggregateDistinctRefusals() {
    }

    /**
     * Whether a DISTINCT call of this aggregate is refused.
     *
     * @param name the canonical (upper-cased) function name
     * @return whether the DISTINCT call is refused
     */
    public static boolean refuses(final String name) {
        return NAMES.contains(name);
    }
}
