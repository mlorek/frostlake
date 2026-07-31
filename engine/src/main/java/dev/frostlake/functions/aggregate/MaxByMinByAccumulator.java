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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;

import tools.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared accumulator for {@link MaxBy} and {@link MinBy}. Unlike the numeric two-argument aggregates (CORR,
 * COVAR, REGR) it keeps RAW objects: the returned {@code value} may be any type and the sort {@code key} may
 * be any {@link Comparable}. Rows whose key is NULL are ignored. The {@code wantMax} flag selects whether the
 * maximum or minimum key wins; ties are resolved in favour of the FIRST-encountered row (live-verified:
 * MAX_BY over ('first', 10), ('second', 10) is 'first'), so only a strictly better key replaces the best.
 *
 * <p>The executor drives this via the two-argument {@link #accumulate(Object, Object)} overload with the raw
 * column values; the single-argument {@link #accumulate(Object)} inherited from the interface is unused.
 */
public class MaxByMinByAccumulator implements AggregateFunction.Accumulator {

    private final boolean wantMax;
    private boolean has = false;
    private Object bestValue = null;
    private Object bestKey = null;
    // Bounded three-argument form MIN_BY/MAX_BY(value, key, N): collect every pair and return an ARRAY
    // of up to N values ordered by key (ascending for MIN_BY, descending for MAX_BY). -1 = scalar form.
    private int limit = -1;
    private boolean distinct;
    private final List<Object> pairValues = new ArrayList<>();
    private final List<Object> pairKeys = new ArrayList<>();

    public MaxByMinByAccumulator(final boolean wantMax) {
        this.wantMax = wantMax;
    }

    /** Switch to the bounded (array-returning) form with the given cap; DISTINCT dedups values. */
    public void setLimit(final int limit, final boolean distinct) {
        this.limit = limit;
        this.distinct = distinct;
    }

    @Override
    public void accumulate(final Object value) {
        // Two-argument aggregate: the executor calls accumulate(value, key); this form is unused.
    }

    /** Offer one {@code (value, key)} pair. A NULL key is ignored; only a key that strictly beats the current best wins. */
    public void accumulate(final Object value, final Object key) {
        if (key == null) {
            return;
        }
        if (limit >= 0) {
            pairValues.add(value);
            pairKeys.add(key);
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
        return wantMax ? cmp > 0 : cmp < 0;
    }

    @Override
    public Object getResult() {
        if (limit >= 0) {
            return boundedResult();
        }
        return has ? bestValue : null;
    }

    /** The bounded form's ARRAY: values ordered by key (min asc / max desc), deduped when DISTINCT, capped. */
    private Object boundedResult() {
        final Integer[] order = new Integer[pairKeys.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                final int cmp = compareKeys(pairKeys.get(a), pairKeys.get(b));
                return wantMax ? -cmp : cmp;
            }
        });
        final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
        final Set<String> seen = new LinkedHashSet<>();
        int taken = 0;
        for (final int idx : order) {
            if (taken >= limit) {
                break;
            }
            final Object value = pairValues.get(idx);
            if (distinct) {
                final String image = String.valueOf(value);
                if (!seen.add(image)) {
                    continue;
                }
            }
            array.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
            taken++;
        }
        return ArrayFunctionHelper.toCanonicalVariant(array);
    }

    @Override
    public void reset() {
        has = false;
        bestValue = null;
        bestKey = null;
        pairValues.clear();
        pairKeys.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final MaxByMinByAccumulator o = (MaxByMinByAccumulator) other;
        if (limit >= 0) {
            pairValues.addAll(o.pairValues);
            pairKeys.addAll(o.pairKeys);
            return;
        }
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
