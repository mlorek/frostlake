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
 * Accumulator for {@link ArrayUniqueAgg}. Each accumulated value is one scalar element; NULLs are skipped
 * and the remaining values are deduplicated (keyed by their JSON text) into a first-seen-ordered set. The
 * result is the compact JSON text of the distinct-value array (never NULL — an empty set renders as
 * {@code []}).
 */
public class ArrayUniqueAggAccumulator implements AggregateFunction.Accumulator {

    private final Map<String, JsonNode> distinct = new LinkedHashMap<>();

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        final JsonNode node = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value);
        if (node == null || node.isNull()) {
            return;
        }
        final String key = node.toString();
        if (!distinct.containsKey(key)) {
            distinct.put(key, node);
        }
    }

    @Override
    public Object getResult() {
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode node : distinct.values()) {
            result.add(node);
        }
        return result.toString();
    }

    @Override
    public void reset() {
        distinct.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final ArrayUniqueAggAccumulator o = (ArrayUniqueAggAccumulator) other;
        for (final Map.Entry<String, JsonNode> entry : o.distinct.entrySet()) {
            if (!distinct.containsKey(entry.getKey())) {
                distinct.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
