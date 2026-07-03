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
import dev.frostlake.types.BooleanType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/** ARRAY_CONTAINS(value, array) — returns TRUE if the array contains the value. */
public class ArrayContains extends BuiltInFunction {
    public ArrayContains() { super("ARRAY_CONTAINS", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        Object value = args.get(0);
        ArrayNode arr = ArrayFunctionHelper.parseArray(args.get(1));
        if (arr == null) return false;
        JsonNode target = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value);
        for (final JsonNode el : arr) {
            if (ArrayFunctionHelper.nodesEqual(el, target)) return true;
            // Also compare as string for text values
            if (value != null && el.isTextual() && el.asText().equals(value.toString())) return true;
        }
        return false;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
