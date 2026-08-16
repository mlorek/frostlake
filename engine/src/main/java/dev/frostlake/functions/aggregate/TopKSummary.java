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

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Space-Saving summary behind the APPROX_TOP_K family: a bounded set of (value, count) counters
 * that names the most frequent values of a stream without holding every distinct one.
 *
 * <p>The rules, all live-verified:
 * <ul>
 *   <li>a value already counted increments its counter;</li>
 *   <li>a new value arriving while the summary is full takes over the counter with the SMALLEST count
 *       (the least recently touched one among equals) and inherits that count plus one — so
 *       {@code APPROX_TOP_K(n, 2, 1)} over four values reports the last one with count 4, and seven
 *       cycling values over four counters all end at 25 after a hundred rows;</li>
 *   <li>the summary lists by count descending and, among equal counts, the most recently touched
 *       value first;</li>
 *   <li>a merge ({@code APPROX_TOP_K_COMBINE}) adds the counts of equal values with no inheritance and
 *       then drops, from the smallest count upward and the most recently touched first, until the
 *       summary fits its capacity again.</li>
 * </ul>
 *
 * <p>The serialised state is {@code {"counters":C,"datatype":T,"precision":P,"scale":S,"state":[[value,
 * count], …],"type":"approx_top_k"}}, its pairs in the listing order above; reading one back keeps that
 * order as the recency order, so an estimate over a state ranks as the state was written.
 */
public class TopKSummary {

    /** The default number of counters — the largest a call may ask for, too. */
    public static final int DEFAULT_COUNTERS = 100000;

    /** The {@code type} member a state carries. */
    public static final String STATE_TYPE = "approx_top_k";

    /** The {@code datatype} of a state that has seen no value. */
    public static final String MISSING_DATATYPE = "MISSING";

    /** The width a state reports for every non-numeric input, and for an empty one. */
    public static final int DEFAULT_PRECISION = 38;

    private static final String INVALID_STATE = "Invalid parameter value: ApproxTopK state. Reason: invalid type";

    private final int capacity;
    private final Map<Object, TopKCounter> counters = new LinkedHashMap<>();
    private long clock;

    public TopKSummary(final int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public int capacity() {
        return capacity;
    }

    public boolean isEmpty() {
        return counters.isEmpty();
    }

    public int size() {
        return counters.size();
    }

    /** Counts one occurrence of a value, taking over the least counter when the summary is full. */
    public void add(final Object value) {
        final Object key = keyOf(value);
        final TopKCounter existing = counters.get(key);
        if (existing != null) {
            existing.increase(1L, ++clock);
            return;
        }
        if (counters.size() >= capacity) {
            final TopKCounter evicted = leastCounted(true);
            counters.remove(evicted.key());
            counters.put(key, new TopKCounter(key, value, evicted.count() + 1L, ++clock));
            return;
        }
        counters.put(key, new TopKCounter(key, value, 1L, ++clock));
    }

    /**
     * Adds a whole counter, as a merge does: an equal value adds its count, a new one is admitted
     * whatever the capacity — {@link #fit(int)} trims afterwards.
     */
    public void merge(final Object value, final long count) {
        final Object key = keyOf(value);
        final TopKCounter existing = counters.get(key);
        if (existing != null) {
            existing.increase(count, ++clock);
            return;
        }
        counters.put(key, new TopKCounter(key, value, count, ++clock));
    }

    /** Drops counters until at most {@code limit} remain: the smallest count first, the most recently touched among equals. */
    public void fit(final int limit) {
        while (counters.size() > Math.max(1, limit)) {
            counters.remove(leastCounted(false).key());
        }
    }

    private TopKCounter leastCounted(final boolean oldestFirst) {
        TopKCounter least = null;
        for (final TopKCounter counter : counters.values()) {
            if (least == null || counter.count() < least.count()) {
                least = counter;
            } else if (counter.count() == least.count()) {
                final boolean older = counter.updatedAt() < least.updatedAt();
                if (oldestFirst == older) {
                    least = counter;
                }
            }
        }
        return least;
    }

    /** The counters ranked: count descending, then the most recently touched first. */
    public List<TopKCounter> ranked() {
        final List<TopKCounter> ordered = new ArrayList<>(counters.values());
        Collections.sort(ordered, new Comparator<TopKCounter>() {
            @Override
            public int compare(final TopKCounter left, final TopKCounter right) {
                final int byCount = Long.compare(right.count(), left.count());
                return byCount != 0 ? byCount : Long.compare(right.updatedAt(), left.updatedAt());
            }
        });
        return ordered;
    }

    /** The first {@code items} of {@link #ranked()}. */
    public List<TopKCounter> top(final int items) {
        final List<TopKCounter> ordered = ranked();
        return ordered.subList(0, Math.min(Math.max(0, items), ordered.size()));
    }

    /** The [value, count] pairs of some counters as an ARRAY value. */
    public static VariantValue pairsOf(final List<TopKCounter> ranked) {
        return ArrayFunctionHelper.toCanonicalVariant(pairsNode(ranked));
    }

    /**
     * The serialised state of a summary.
     *
     * @param counters the {@code counters} member
     * @param datatype the input's type in the catalog's internal vocabulary (FIXED, TEXT, REAL, …)
     * @param precision the {@code precision} member
     * @param scale the {@code scale} member
     * @param ranked the counters to list, in listing order
     * @return the OBJECT value
     */
    public static VariantValue stateOf(final int counters, final String datatype, final int precision,
                                       final int scale, final List<TopKCounter> ranked) {
        final ObjectNode state = ArrayFunctionHelper.MAPPER.createObjectNode();
        state.put("counters", counters);
        state.put("datatype", datatype);
        state.put("precision", precision);
        state.put("scale", scale);
        state.set("state", pairsNode(ranked));
        state.put("type", STATE_TYPE);
        return ArrayFunctionHelper.toCanonicalVariant(state);
    }

    /**
     * A state as an object node, or the refusal live gives an argument that is not one.
     *
     * @param value the argument
     * @return the state's node
     */
    public static JsonNode stateNode(final Object value) {
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node == null || !node.isObject()) {
            throw new RuntimeException(INVALID_STATE);
        }
        final JsonNode type = node.get("type");
        final JsonNode pairs = node.get("state");
        if (type == null || !type.isTextual() || !STATE_TYPE.equals(type.asText())
                || pairs == null || !pairs.isArray()) {
            throw new RuntimeException(INVALID_STATE);
        }
        return node;
    }

    /**
     * Reads a state's pairs into this summary as a merge, last pair first, so that the state's own
     * listing order (most recently touched first) is what the clock records.
     */
    public void absorb(final JsonNode state) {
        final JsonNode pairs = state.get("state");
        for (int i = pairs.size() - 1; i >= 0; i--) {
            final JsonNode pair = pairs.get(i);
            if (pair == null || !pair.isArray() || pair.size() < 2 || !pair.get(1).isNumber()) {
                throw new RuntimeException(INVALID_STATE);
            }
            merge(ArrayFunctionHelper.toCanonicalVariant(pair.get(0)), pair.get(1).longValue());
        }
    }

    /** The {@code counters} member of a state, or the default when it carries none. */
    public static int countersOf(final JsonNode state) {
        final JsonNode counters = state.get("counters");
        return counters != null && counters.isNumber() ? Math.max(1, counters.intValue()) : DEFAULT_COUNTERS;
    }

    private static ArrayNode pairsNode(final List<TopKCounter> ranked) {
        final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final TopKCounter counter : ranked) {
            final ArrayNode pair = ArrayFunctionHelper.MAPPER.createArrayNode();
            pair.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, counter.value()));
            pair.add(counter.count());
            array.add(pair);
        }
        return array;
    }

    /** Equal values share a counter however they are carried: 1.50 and 1.5, a Long and a BigDecimal. */
    private static Object keyOf(final Object value) {
        return ValueComparisons.canonicalGroupKeyValue(value);
    }
}
