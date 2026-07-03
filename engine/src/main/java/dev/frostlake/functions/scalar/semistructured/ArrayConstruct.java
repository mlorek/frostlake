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
 * ARRAY_CONSTRUCT(val1, val2, ...) — builds a JSON array from the given values.
 */
public class ArrayConstruct extends BuiltInFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ArrayConstruct() {
        super("ARRAY_CONSTRUCT", VariantType.VARIANT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode array = MAPPER.createArrayNode();
        for (final Object arg : args) {
            if (arg == null) {
                array.addNull();
            } else if (arg instanceof Boolean) {
                array.add((Boolean) arg);
            } else if (arg instanceof Long || arg instanceof Integer) {
                array.add(((Number) arg).longValue());
            } else if (arg instanceof Number) {
                array.add(((Number) arg).doubleValue());
            } else {
                String s = arg.toString().trim();
                // If it's already a JSON array or object, embed it as raw JSON
                if ((s.startsWith("[") || s.startsWith("{")) && !s.isEmpty()) {
                    try {
                        array.add(MAPPER.readTree(s));
                        continue;
                    } catch (final Exception ignored) {}
                }
                array.add(s);
            }
        }
        return array.toString();
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
