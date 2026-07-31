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
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulator for {@code OBJECT_AGG(key, value)} — collects key/value pairs into one OBJECT. Pairs with
 * a NULL key or NULL value are omitted (Snowflake). The result is the engine's OBJECT representation:
 * canonical JSON text (keys sorted, numbers normalized).
 */
public class ObjectAggAccumulator implements AggregateFunction.Accumulator {

    private final Map<String, Object> entries = new LinkedHashMap<>();

    @Override
    public void accumulate(final Object value) {
        // OBJECT_AGG is fed key/value pairs through the two-argument overload; a bare value carries
        // no key and cannot form an entry.
    }

    public void accumulate(final Object key, final Object value) {
        if (key == null || value == null) {
            return;
        }
        entries.put(key.toString(), value);
    }

    @Override
    public Object getResult() {
        final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, Object> entry : entries.entrySet()) {
            obj.set(entry.getKey(), ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, entry.getValue()));
        }
        return ArrayFunctionHelper.toCanonicalVariant(obj);
    }

    @Override
    public void reset() {
        entries.clear();
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        entries.putAll(((ObjectAggAccumulator) other).entries);
    }
}
