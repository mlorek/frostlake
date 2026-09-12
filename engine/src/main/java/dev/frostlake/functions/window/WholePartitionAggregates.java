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
import java.util.HashSet;
import java.util.Set;

/**
 * The aggregates that, used as a WINDOW, may only see the WHOLE partition. Live refuses every frame
 * that shows one of them a moving subset of the partition at compile time rather than answering
 * over the subset — and the family is far wider than the ranking aggregates it was first measured
 * on. Swept name by name against seven frame shapes:
 *
 * <pre>
 *   MEDIAN(n) OVER ()                     answers
 *   MEDIAN(n) OVER (PARTITION BY g)       answers
 *   MEDIAN(n) OVER (ORDER BY n)           Cumulative window frame unsupported for function MEDIAN
 *   … ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING   answers
 * </pre>
 *
 * <p>★ THE ORDER BY IS THE FAULT, NOT THE FRAME. A specification with no ORDER BY has no frame to
 * complain about and is answered; one WITH an ORDER BY carries an implicit cumulative frame even when
 * none is written, which is why the bare {@code OVER (ORDER BY n)} is refused at the OVER keyword
 * while a written frame is refused at its own.
 *
 * <p>★ THE WHOLE-PARTITION ROWS FRAME IS THE ONE EXCEPTION, because it re-states what these functions
 * already do. Its RANGE spelling is not: live elides the frame there and then refuses the ORDER BY it
 * is left holding, in a different sentence entirely — except for KURTOSIS and SKEW, which answer the
 * RANGE spelling and refuse only the cumulative and sliding frames.
 *
 * <p>★ THE FAMILY: the ranking aggregates (MEDIAN, MODE, the percentiles), the approximate family
 * (APPROX_PERCENTILE and its accumulator, APPROX_TOP_K, APPROX_COUNT_DISTINCT / HLL), LISTAGG with or
 * without WITHIN GROUP, the correlation and regression family (CORR, COVAR_*, REGR_*), the moments
 * (KURTOSIS, SKEW), ANY_VALUE, MIN_BY / MAX_BY, OBJECT_AGG, ARRAY_UNIQUE_AGG, HASH_AGG and the bitwise
 * and boolean aggregates. Measured EXEMPT — every frame answers — are SUM, AVG, COUNT, COUNT_IF,
 * COUNT(DISTINCT), MIN, MAX, VARIANCE, STDDEV and ARRAY_AGG without WITHIN GROUP; the refusals are keyed
 * on the names here and nothing else.
 */
public final class WholePartitionAggregates {

    private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
        "MEDIAN", "MODE", "PERCENTILE_CONT", "PERCENTILE_DISC",
        "APPROX_PERCENTILE", "APPROX_PERCENTILE_ACCUMULATE", "APPROX_PERCENTILE_COMBINE",
        "APPROX_TOP_K", "APPROX_TOP_K_ACCUMULATE", "APPROX_TOP_K_COMBINE", "APPROX_COUNT_DISTINCT", "HLL",
        "LISTAGG",
        "CORR", "COVAR_POP", "COVAR_SAMP",
        "REGR_SLOPE", "REGR_INTERCEPT", "REGR_R2", "REGR_COUNT", "REGR_AVGX", "REGR_AVGY",
        "REGR_SXX", "REGR_SXY", "REGR_SYY",
        "KURTOSIS", "SKEW",
        "ANY_VALUE", "MIN_BY", "MAX_BY", "OBJECT_AGG", "ARRAY_UNIQUE_AGG", "HASH_AGG",
        "BITAND_AGG", "BITOR_AGG", "BITXOR_AGG", "BOOLAND_AGG", "BOOLOR_AGG", "BOOLXOR_AGG"));

    /** The members that ANSWER the whole-partition RANGE frame instead of refusing its ORDER BY. */
    private static final Set<String> KEEPS_WHOLE_RANGE_FRAME = new HashSet<>(Arrays.asList(
        "KURTOSIS", "SKEW"));

    private WholePartitionAggregates() {
    }

    /**
     * Whether this aggregate may only be windowed over a whole partition.
     *
     * @param name the upper-cased function name
     * @return true when it is one of the family
     */
    public static boolean coversWholePartitionOnly(final String name) {
        return NAMES.contains(name);
    }

    /**
     * Whether the whole-partition RANGE spelling is answered for this member rather than refused as an
     * unsupported ORDER BY — true for the two moments alone (live-verified).
     *
     * @param name the upper-cased function name
     * @return true when the RANGE frame answers
     */
    public static boolean answersWholeRangeFrame(final String name) {
        return KEEPS_WHOLE_RANGE_FRAME.contains(name);
    }
}
