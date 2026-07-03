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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulator for {@link ArrayUnionAgg}. Each accumulated value is parsed as an ARRAY; its elements are
 * added to a {@link LinkedHashMap} keyed by the element's JSON text so that duplicates across all input
 * rows are removed while first-seen order is preserved. NULL / non-array inputs are skipped. The result is
 * the compact JSON text of the distinct-element array (never NULL — an empty union renders as {@code []}).
 */
public class ArrayUnionAggAccumulator implements AggregateFunction.Accumulator {

    private final Map<String, JsonNode> distinct = new LinkedHashMap<>();

    @Override
    public void accumulate(final Object value) {
        final ArrayNode array = ArrayFunctionHelper.parseArray(value);
        if (array == null) {
            return;
        }
        for (final JsonNode element : array) {
            final String key = element.toString();
            if (!distinct.containsKey(key)) {
                distinct.put(key, element);
            }
        }
    }

    @Override
    public Object getResult() {
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode element : distinct.values()) {
            result.add(element);
        }
        return result.toString();
    }

    @Override
    public void reset() {
        distinct.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ArrayUnionAggAccumulator o = (ArrayUnionAggAccumulator) other;
        for (final Map.Entry<String, JsonNode> entry : o.distinct.entrySet()) {
            if (!distinct.containsKey(entry.getKey())) {
                distinct.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
