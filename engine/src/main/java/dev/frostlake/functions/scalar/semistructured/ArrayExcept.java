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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ARRAY_EXCEPT(array1, array2) — returns elements in array1 that are not in array2 (distinct). */
public class ArrayExcept extends BuiltInFunction {
    public ArrayExcept() { super("ARRAY_EXCEPT", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode a1 = ArrayFunctionHelper.parseArray(args.get(0));
        ArrayNode a2 = ArrayFunctionHelper.parseArray(args.get(1));
        if (a1 == null || a2 == null) return null;
        Set<String> set2 = new HashSet<>();
        for (final JsonNode el : a2) set2.add(el.toString());
        Set<String> seen = new HashSet<>();
        ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode el : a1) {
            String k = el.toString();
            if (!set2.contains(k) && seen.add(k)) result.add(el);
        }
        return result.toString();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
