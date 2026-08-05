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
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulator for {@link ArrayUnionAgg} — Snowflake's MULTISET union (live-verified): every element
 * appears with the MAXIMUM multiplicity it has in any single input array, so
 * {@code [2,2,2] UNION [2,2]} is {@code [2,2,2]} and {@code [5,5]} alone keeps both. First-seen
 * order; NULL / non-array inputs are skipped; an empty union renders as {@code []}.
 */
public class ArrayUnionAggAccumulator implements AggregateFunction.Accumulator {

    private final Map<String, JsonNode> elements = new LinkedHashMap<>();
    private final Map<String, Integer> maxCounts = new LinkedHashMap<>();

    @Override
    public void accumulate(final Object value) {
        final ArrayNode array = ArrayFunctionHelper.parseArray(value);
        if (array == null) {
            return;
        }
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final JsonNode element : array) {
            final String key = element.toString();
            elements.putIfAbsent(key, element);
            final Integer prior = counts.get(key);
            counts.put(key, prior == null ? 1 : prior + 1);
        }
        for (final Map.Entry<String, Integer> entry : counts.entrySet()) {
            final Integer existing = maxCounts.get(entry.getKey());
            if (existing == null || entry.getValue() > existing) {
                maxCounts.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public Object getResult() {
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final Map.Entry<String, JsonNode> entry : elements.entrySet()) {
            final int count = maxCounts.getOrDefault(entry.getKey(), 0);
            for (int i = 0; i < count; i++) {
                result.add(entry.getValue());
            }
        }
        return VariantValue.ofNode(result);
    }

    @Override
    public void reset() {
        elements.clear();
        maxCounts.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ArrayUnionAggAccumulator o = (ArrayUnionAggAccumulator) other;
        for (final Map.Entry<String, JsonNode> entry : o.elements.entrySet()) {
            elements.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, Integer> entry : o.maxCounts.entrySet()) {
            final Integer existing = maxCounts.get(entry.getKey());
            if (existing == null || entry.getValue() > existing) {
                maxCounts.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
