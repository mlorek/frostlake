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
import tools.jackson.databind.JsonNode;
import java.util.List;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;

/** ARRAY_REPEAT(value, n) — an array of n copies of the value. */
public class ArrayRepeat extends BuiltInFunction {
    public ArrayRepeat() { super("ARRAY_REPEAT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(1) == null) return null;
        final int n = (int) Double.parseDouble(args.get(1).toString());
        // Live: ARRAY_REPEAT(NULL, 3) is [undefined,undefined,undefined], while
        // ARRAY_REPEAT(PARSE_JSON('null'), 2) is [null,null].
        final JsonNode element = ArrayFunctionHelper.toElementNode(ArrayFunctionHelper.MAPPER, args.get(0));
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int i = 0; i < n; i++) {
            result.add(element);
        }
        return VariantValue.ofNode(result);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
