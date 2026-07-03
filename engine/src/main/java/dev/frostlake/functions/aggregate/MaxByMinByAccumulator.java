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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.AggregateFunction;

import java.math.BigDecimal;

/**
 * Shared accumulator for {@link MaxBy} and {@link MinBy}. Unlike the numeric two-argument aggregates (CORR,
 * COVAR, REGR) it keeps RAW objects: the returned {@code value} may be any type and the sort {@code key} may
 * be any {@link Comparable}. Rows whose key is NULL are ignored. The {@code wantMax} flag selects whether the
 * maximum or minimum key wins; ties are resolved in favour of the latest row (Snowflake semantics), so a key
 * equal to the current best still replaces it.
 *
 * <p>The executor drives this via the two-argument {@link #accumulate(Object, Object)} overload with the raw
 * column values; the single-argument {@link #accumulate(Object)} inherited from the interface is unused.
 */
public class MaxByMinByAccumulator implements AggregateFunction.Accumulator {

    private final boolean wantMax;
    private boolean has = false;
    private Object bestValue = null;
    private Object bestKey = null;

    public MaxByMinByAccumulator(final boolean wantMax) {
        this.wantMax = wantMax;
    }

    @Override
    public void accumulate(final Object value) {
        // Two-argument aggregate: the executor calls accumulate(value, key); this form is unused.
    }

    /** Offer one {@code (value, key)} pair. A NULL key is ignored; a key that meets-or-beats the current best wins. */
    public void accumulate(final Object value, final Object key) {
        if (key == null) {
            return;
        }
        if (!has || beatsBest(key)) {
            bestValue = value;
            bestKey = key;
            has = true;
        }
    }

    private boolean beatsBest(final Object key) {
        final int cmp = compareKeys(key, bestKey);
        return wantMax ? cmp >= 0 : cmp <= 0;
    }

    @Override
    public Object getResult() {
        return has ? bestValue : null;
    }

    @Override
    public void reset() {
        has = false;
        bestValue = null;
        bestKey = null;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final MaxByMinByAccumulator o = (MaxByMinByAccumulator) other;
        if (!o.has) {
            return;
        }
        if (!has || beatsBest(o.bestKey)) {
            bestValue = o.bestValue;
            bestKey = o.bestKey;
            has = true;
        }
    }

    /**
     * Numeric-aware comparison of two non-NULL keys: two numbers compare by value (via BigDecimal, tolerating
     * mixed runtime numeric types), two same-typed Comparables compare naturally, otherwise their textual
     * forms are compared. Mirrors {@code ValueComparisons.compareValues} without coupling to the executor.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareKeys(final Object a, final Object b) {
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }
        if (a instanceof Comparable && b instanceof Comparable && a.getClass() == b.getClass()) {
            return ((Comparable) a).compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }
}
