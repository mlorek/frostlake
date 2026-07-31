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
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** ARRAYS_ZIP(a1, a2, …) — an array of objects pairing same-index elements under keys $1, $2, …. */
public class ArraysZip extends BuiltInFunction {
    public ArraysZip() { super("ARRAYS_ZIP", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        int longest = 0;
        final ArrayNode[] arrays = new ArrayNode[args.size()];
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) == null) return null;
            arrays[i] = ArrayFunctionHelper.parseArray(args.get(i));
            if (arrays[i] == null) return null;
            longest = Math.max(longest, arrays[i].size());
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int row = 0; row < longest; row++) {
            final ObjectNode pair = ArrayFunctionHelper.MAPPER.createObjectNode();
            for (int i = 0; i < arrays.length; i++) {
                // An `undefined` element moving into an OBJECT degrades to a JSON null — live
                // ARRAYS_ZIP(ARRAY_CONSTRUCT(1,NULL), ARRAY_CONSTRUCT(2,3)) is
                // [{"$1":1,"$2":2},{"$1":null,"$2":3}].
                pair.set("$" + (i + 1), row < arrays[i].size()
                    ? VariantUndefined.asObjectMember(arrays[i].get(row))
                    : ArrayFunctionHelper.MAPPER.nullNode());
            }
            result.add(pair);
        }
        return VariantValue.ofNode(result);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
