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

/** ARRAY_REMOVE_AT(array, index) — removes element at given index (0-based). Negative indices count from end. */
public class ArrayRemoveAt extends BuiltInFunction {
    public ArrayRemoveAt() { super("ARRAY_REMOVE_AT", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null || args.get(1) == null) return null;
        int idx = ((Number) args.get(1)).intValue();
        if (idx < 0) idx = src.size() + idx;
        ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int i = 0; i < src.size(); i++) {
            if (i != idx) result.add(src.get(i));
        }
        return result.toString();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
