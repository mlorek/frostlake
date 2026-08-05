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

import dev.frostlake.functions.aggregate.AggregateNumerics;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared logic for Snowflake window functions.
 * All methods work on pre-sorted partition row lists.
 */
public class WindowFunctionHelper {

    private WindowFunctionHelper() {}

    /**
     * NTILE(n) — divides the sorted partition into n equal buckets, returns bucket number (1-based).
     */
    public static long ntile(final List<Row> sortedRows, final Row currentRow, final int buckets) {
        int n = sortedRows.size();
        if (n == 0 || buckets <= 0) return 1L;
        int pos = indexOf(sortedRows, currentRow);
        if (pos < 0) return 1L;
        // Bucket size: larger buckets come first when not evenly divisible
        int largeSize = (int) Math.ceil((double) n / buckets);
        int largeCount = n % buckets == 0 ? 0 : n % buckets;  // how many "large" buckets
        if (largeCount == 0) largeCount = buckets;             // all same size
        int splitPoint = largeCount * largeSize;
        if (pos < splitPoint) {
            return (long) (pos / largeSize + 1);
        } else {
            int smallSize = n / buckets;
            return (long) (largeCount + (pos - splitPoint) / smallSize + 1);
        }
    }

    /**
     * PERCENT_RANK — (rank - 1) / (N - 1); ranges [0, 1].
     */
    public static double percentRank(final List<Row> sortedRows, final Row currentRow,
                                      final List<Object> orderValues) {
        int n = sortedRows.size();
        if (n <= 1) return 0.0;
        int pos = indexOf(sortedRows, currentRow);
        if (pos < 0) return 0.0;
        Object curVal = pos < orderValues.size() ? orderValues.get(pos) : null;
        // rank = 1 + number of rows with strictly smaller order value
        long rank = 1;
        for (int i = 0; i < pos; i++) {
            Object v = i < orderValues.size() ? orderValues.get(i) : null;
            if (compareValues(v, curVal) < 0) rank++;
        }
        return (double)(rank - 1) / (n - 1);
    }

    /**
     * CUME_DIST — fraction of rows with order value <= current; ranges (0, 1].
     */
    public static double cumeDist(final List<Row> sortedRows, final Row currentRow,
                                   final List<Object> orderValues) {
        int n = sortedRows.size();
        if (n == 0) return 1.0;
        int pos = indexOf(sortedRows, currentRow);
        if (pos < 0) return 1.0;
        Object curVal = pos < orderValues.size() ? orderValues.get(pos) : null;
        // count rows with value <= currentVal
        long count = 0;
        for (int i = 0; i < orderValues.size(); i++) {
            Object v = orderValues.get(i);
            if (compareValues(v, curVal) <= 0) count++;
        }
        return (double) count / n;
    }

    /**
     * RATIO_TO_REPORT(expr) — value / SUM(value over partition).
     */
    public static Object ratioToReport(final Object currentValue, final List<Object> allValues) {
        if (currentValue == null) return null;
        double cur = toDouble(currentValue);
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

    public static int indexOf(final List<Row> rows, final Row target) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).equals(target)) return i;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public static int compareValues(final Object a, final Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        return a.toString().compareTo(b.toString());
    }

    public static double toDouble(final Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (final Exception e) { return 0; }
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
