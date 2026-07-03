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

import java.util.List;

/** ARRAY_REMOVE(array, value) — removes all occurrences of value from array. */
public class ArrayRemove extends BuiltInFunction {
    public ArrayRemove() { super("ARRAY_REMOVE", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        JsonNode target = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, args.get(1));
        ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode el : src) {
            if (!ArrayFunctionHelper.nodesEqual(el, target)) result.add(el);
        }
        return result.toString();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
