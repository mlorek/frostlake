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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.VariantAccessorFunction;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_APPEND(array, value) — appends value to array and returns the new array.
 */
public class ArrayAppend extends VariantAccessorFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ArrayAppend() {
        super("ARRAY_APPEND", ArrayType.ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String arrayStr = args.get(0).toString().trim();
        final Object value = args.get(1);

        try {
            // The source array may already hold a VARIANT `undefined` element, whose canonical text is the
            // bare token no JSON parser accepts on its own — see VariantUndefined.
            final ArrayNode array = (ArrayNode) VariantUndefined.readTree(MAPPER, arrayStr);
            if (value == null) {
                // Live: ARRAY_APPEND([1], NULL) is [1,undefined] and
                // ARRAY_APPEND(PARSE_JSON('[1,null,2]'), NULL) is [1,null,2,undefined] — the appended SQL
                // NULL is `undefined`, distinct from the JSON null already in the array.
                array.add(VariantUndefined.node());
            } else if (value instanceof Boolean) {
                array.add((Boolean) value);
            } else if (value instanceof Long || value instanceof Integer) {
                array.add(((Number) value).longValue());
            } else if (value instanceof Number) {
                array.add(((Number) value).doubleValue());
            } else {
                final String s = value.toString().trim();
                if ((s.startsWith("[") || s.startsWith("{")) && !s.isEmpty()) {
                    try {
                        array.add(MAPPER.readTree(s));
                        return VariantValue.ofNode(array);
                    }
                    catch (final Exception ignored) {}
                }
                array.add(s);
            }
            return VariantValue.ofNode(array);
        } catch (final Exception e) {
            throw new RuntimeException("ARRAY_APPEND: invalid array: " + args.get(0));
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
