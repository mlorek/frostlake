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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/** OBJECT_PICK(object, key1 [, key2, ...]) — returns an object with only the specified keys. */
public class ObjectPick extends BuiltInFunction {
    public ObjectPick() { super("OBJECT_PICK", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        JsonNode src = ArrayFunctionHelper.parseNode(args.get(0));
        if (src == null || !src.isObject()) return null;
        ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i) == null) continue;
            String key = args.get(i).toString();
            JsonNode val = src.get(key);
            if (val != null) result.set(key, val);
        }
        // Snowflake serializes OBJECT members key-sorted; raw insertion order leaked argument order.
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
