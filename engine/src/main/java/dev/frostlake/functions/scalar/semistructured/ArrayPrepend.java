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
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/** ARRAY_PREPEND(array, value) — prepends value to the front of the array. */
public class ArrayPrepend extends BuiltInFunction {
    public ArrayPrepend() { super("ARRAY_PREPEND", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        // Live: ARRAY_PREPEND([1], NULL) is [undefined,1].
        result.add(ArrayFunctionHelper.toElementNode(ArrayFunctionHelper.MAPPER, args.get(1)));
        for (final JsonNode el : src) result.add(el);
        return VariantValue.ofNode(result);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
