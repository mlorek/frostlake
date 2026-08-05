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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** OBJECT_DELETE(object, key1 [, key2, ...]) — removes one or more keys from an object. */
public class ObjectDelete extends BuiltInFunction {
    public ObjectDelete() { super("OBJECT_DELETE", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        JsonNode src = ArrayFunctionHelper.parseNode(args.get(0));
        if (src == null || !src.isObject()) return null;
        Set<String> toRemove = new HashSet<>();
        for (int i = 1; i < args.size(); i++) {
            if (args.get(i) != null) toRemove.add(args.get(i).toString());
        }
        ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        Set<Map.Entry<String, JsonNode>> fields = src.properties();
        for(final Map.Entry<String, JsonNode> e:  fields) {
            if (!toRemove.contains(e.getKey())) result.set(e.getKey(), e.getValue());
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
