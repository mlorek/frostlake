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

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.functions.aggregate.AggregateNumerics;

import java.util.List;

/**
 * Shared logic for Snowflake window functions.
 * All methods work on pre-sorted partition row lists.
 */
public final class WindowFunctionHelper {

    private WindowFunctionHelper() {}

    /**
     * NTILE(n) — divides the sorted partition into n buckets and answers the 1-based bucket of the row at
     * {@code position}. Buckets differ in size by at most one, the larger ones first.
     *
     * @param size the partition's row count
     * @param position the row's 0-based position in the sorted partition, or -1 when unknown
     * @param buckets the requested bucket count
     * @return the row's bucket
     */
    public static long ntile(final int size, final int position, final int buckets) {
        if (size == 0 || buckets <= 0 || position < 0) return 1L;
        // Bucket size: larger buckets come first when not evenly divisible
        final int largeSize = (int) Math.ceil((double) size / buckets);
        int largeCount = size % buckets == 0 ? 0 : size % buckets;  // how many "large" buckets
        if (largeCount == 0) largeCount = buckets;                   // all same size
        final int splitPoint = largeCount * largeSize;
        if (position < splitPoint) {
            return (long) (position / largeSize + 1);
        } else {
            final int smallSize = size / buckets;
            return (long) (largeCount + (position - splitPoint) / smallSize + 1);
        }
    }

    /**
     * RATIO_TO_REPORT(expr) — value / SUM(value over partition).
     */
    public static Object ratioToReport(final Object currentValue, final List<Object> allValues) {
        if (currentValue == null) return null;
        final double cur = toDouble(currentValue);
        double sum = 0;
        for (final Object v : allValues) {
            if (v != null) sum += toDouble(v);
        }
        return sum == 0 ? null : cur / sum;
    }

    /**
     * FIRST_VALUE(expr) — value of expr in the first row of the window frame. With RESPECT NULLS
     * (Snowflake's default) the first row's value is returned even if it is NULL; with IGNORE NULLS
     * the first non-null value is returned (null if the frame has none).
     */
    public static Object firstValue(final List<Object> frameValues, final boolean ignoreNulls) {
        if (frameValues.isEmpty()) {
            return null;
        }
        if (!ignoreNulls) {
            return frameValues.get(0);
        }
        for (final Object v : frameValues) {
            if (v != null) return v;
        }
        return null;
    }

    /**
     * LAST_VALUE(expr) — value of expr in the last row of the window frame. RESPECT NULLS (default)
     * returns the last row's value even if NULL; IGNORE NULLS returns the last non-null value.
     */
    public static Object lastValue(final List<Object> frameValues, final boolean ignoreNulls) {
        if (frameValues.isEmpty()) {
            return null;
        }
        if (!ignoreNulls) {
            return frameValues.get(frameValues.size() - 1);
        }
        for (int i = frameValues.size() - 1; i >= 0; i--) {
            if (frameValues.get(i) != null) return frameValues.get(i);
        }
        return null;
    }

    /**
     * NTH_VALUE(expr, n) — value of expr in the nth row of the partition (1-based).
     */
    public static Object nthValue(final List<Object> frameValues, final int n) {
        if (n <= 0 || n > frameValues.size()) return null;
        return frameValues.get(n - 1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static int compareValues(final Object a, final Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Number && b instanceof Number) {
            // Exact numbers compare exactly and a double beside one compares as a double; -0.0 ties
            // with 0.0 so a frame's MIN or MAX keeps the first seen, as live does.
            return ValueComparisons.compareForExtreme(a, b);
        }
        return a.toString().compareTo(b.toString());
    }

    public static double toDouble(final Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (final Exception e) {
            return 0;
        }
    }

    // ── window aggregates (SUM/AVG/MIN/MAX over a frame, nulls ignored) ──────────

    /** SUM over the frame, ignoring nulls; null when the frame has no non-null values (SQL SUM semantics). */
    public static Object sum(final List<Object> values) {
        // SUM preserves integer-ness: a window SUM over INT / NUMBER(p,0) values is a whole number, not X.0
        // (empty / all-null -> NULL). Decimal / float inputs keep the prior double result.
        return AggregateNumerics.sum(values);
    }

    /** The declared-VARIANT-argument flavour — live, {@code SUM(v:b) OVER ()} is DOUBLE
     * (SYSTEM$TYPEOF FLOAT) exactly like the grouped form, even over whole-number JSON values. */
    public static Object sum(final List<Object> values, final boolean variantArgument) {
        return AggregateNumerics.sum(values, variantArgument);
    }

    /** AVG over the frame, ignoring nulls; null when there are no non-null values. Shares the grouped
     * AVG's Snowflake typing: fixed-point inputs average to a scale-(max input scale + 6) BigDecimal,
     * any double/VARIANT input keeps the double average. */
    public static Object avg(final List<Object> values) {
        return AggregateNumerics.avg(values);
    }

    /** See {@link #sum(List, boolean)} — the same declared-VARIANT rule applied to a window AVG. */
    public static Object avg(final List<Object> values, final boolean variantArgument) {
        return AggregateNumerics.avg(values, variantArgument);
    }

    /**
     * AVG over a NON-CUMULATIVE window, which declares three decimals wider than its input rather than
     * six and TRUNCATES to that scale — see
     * {@link AggregateNumerics#nonCumulativeWindowAvg(Iterable, boolean)}.
     */
    public static Object nonCumulativeAvg(final List<Object> values, final boolean variantArgument) {
        return AggregateNumerics.nonCumulativeWindowAvg(values, variantArgument);
    }

    /**
     * SUM over a window frame given as the partials its FLOAT sum is read in — see
     * {@link AggregateNumerics#windowSum(List, boolean, boolean)}.
     */
    public static Object sum(final List<List<Object>> partials, final boolean variantArgument,
                             final boolean rowWise) {
        return AggregateNumerics.windowSum(partials, variantArgument, rowWise);
    }

    /**
     * AVG over a window frame given as the partials its FLOAT sum is read in, at a cumulative window's
     * scale or a non-cumulative one's — see {@link AggregateNumerics#windowAvg(List, boolean, boolean, boolean)}.
     */
    public static Object avg(final List<List<Object>> partials, final boolean variantArgument,
                             final boolean nonCumulative, final boolean rowWise) {
        return AggregateNumerics.windowAvg(partials, variantArgument, nonCumulative, rowWise);
    }

    /** MIN over the frame (by {@link #compareValues}), ignoring nulls; preserves the element's type. */
    public static Object min(final List<Object> values) {
        Object best = null;
        for (final Object v : values) {
            if (v != null && (best == null || compareValues(v, best) < 0)) best = v;
        }
        return best;
    }

    /** MAX over the frame (by {@link #compareValues}), ignoring nulls; preserves the element's type. */
    public static Object max(final List<Object> values) {
        Object best = null;
        for (final Object v : values) {
            if (v != null && (best == null || compareValues(v, best) > 0)) best = v;
        }
        return best;
    }
}
