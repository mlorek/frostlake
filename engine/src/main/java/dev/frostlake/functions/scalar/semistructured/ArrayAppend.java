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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_APPEND(array, value) — appends value to array and returns the new array.
 */
public class ArrayAppend extends BuiltInFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ArrayAppend() {
        super("ARRAY_APPEND", VariantType.VARIANT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        String arrayStr = args.get(0).toString().trim();
        Object value = args.get(1);

        try {
            ArrayNode array = (ArrayNode) MAPPER.readTree(arrayStr);
            if (value == null) {
                array.addNull();
            } else if (value instanceof Boolean) {
                array.add((Boolean) value);
            } else if (value instanceof Long || value instanceof Integer) {
                array.add(((Number) value).longValue());
            } else if (value instanceof Number) {
                array.add(((Number) value).doubleValue());
            } else {
                String s = value.toString().trim();
                if ((s.startsWith("[") || s.startsWith("{")) && !s.isEmpty()) {
                    try { array.add(MAPPER.readTree(s)); return array.toString(); }
                    catch (final Exception ignored) {}
                }
                array.add(s);
            }
            return array.toString();
        } catch (final Exception e) {
            throw new RuntimeException("ARRAY_APPEND: invalid array: " + args.get(0));
        }
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
