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
import dev.frostlake.types.ObjectType;
import dev.frostlake.values.VariantUndefined;
import java.util.List;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** ARRAYS_TO_OBJECT(keys, values) — an object pairing the key array with the value array by index. */
public class ArraysToObject extends BuiltInFunction {
    public ArraysToObject() { super("ARRAYS_TO_OBJECT", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode keys = ArrayFunctionHelper.parseArray(args.get(0));
        final ArrayNode values = ArrayFunctionHelper.parseArray(args.get(1));
        if (keys == null || values == null) return null;
        if (keys.size() != values.size()) {
            throw new RuntimeException("ARRAYS_TO_OBJECT: key and value arrays differ in size ("
                + keys.size() + " vs " + values.size() + ")");
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).isNull()) continue;
            // An `undefined` value moving into an OBJECT degrades to a JSON null — live:
            // ARRAYS_TO_OBJECT(['a','b'], ARRAY_CONSTRUCT(1,NULL)) is {"a":1,"b":null}.
            result.set(keys.get(i).asText(), VariantUndefined.asObjectMember(values.get(i)));
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
