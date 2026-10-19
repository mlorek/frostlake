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
import dev.frostlake.values.DecfloatTextNode;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Accumulator for {@code OBJECT_AGG(key, value)} — collects key/value pairs into one OBJECT. Pairs with
 * a NULL key or NULL value are omitted (Snowflake). The result is the engine's OBJECT representation:
 * canonical JSON text (keys sorted, numbers normalized).
 */
public class ObjectAggAccumulator implements AggregateFunction.Accumulator, DecfloatAwareAccumulator {

    private final Map<String, Object> entries = new LinkedHashMap<>();

    /** Whether the aggregated value is a DECFLOAT, whose members keep their type (see DecfloatTextNode). */
    private boolean decfloat;

    @Override
    public void setDecfloatArgument(final boolean decfloat) {
        this.decfloat = decfloat;
    }

    @Override
    public void accumulate(final Object value) {
        // OBJECT_AGG is fed key/value pairs through the two-argument overload; a bare value carries
        // no key and cannot form an entry.
    }

    public void accumulate(final Object key, final Object value) {
        if (key == null || value == null) {
            return;
        }
        entries.put(keyText(key), value);
    }

    /**
     * The text a key CONTRIBUTES. A VARIANT key gives its VALUE, not its JSON rendering: live answers
     * {@code OBJECT_AGG(k::VARIANT, v)} with {@code {"SENSOR_NOW":"s-1"}}, where reading the variant's
     * own text spelled the key {@code "\"SENSOR_NOW\""} — quotes and all — and every later comparison
     * against it failed. Measured for a column, a literal, a concatenation and a conditional alike.
     */
    private static String keyText(final Object key) {
        if (key instanceof VariantValue) {
            final JsonNode node = ((VariantValue) key).node();
            if (node != null && !node.isObject() && !node.isArray()) {
                return node.asString();
            }
        }
        return key.toString();
    }

    @Override
    public Object getResult() {
        final ObjectNode obj = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, Object> entry : entries.entrySet()) {
            final Object value = entry.getValue();
            obj.set(entry.getKey(), decfloat && value instanceof Number && !(value instanceof Double)
                ? DecfloatTextNode.of(Decfloats.of((Number) value, "TO_DECFLOAT"))
                : ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
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
