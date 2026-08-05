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
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MAP_DELETE(map, key [, key …]) — the map without the named keys. Live-verified: a key the
 * map does not hold is silently ignored ({@code MAP_DELETE(<{'a':1}>, 'zzz')} is the unchanged map), a
 * repeated key is harmless, a NULL key removes nothing, and a NULL map answers NULL.
 */
public class MapDelete extends BuiltInFunction {

    public MapDelete() {
        super("MAP_DELETE", MapFunctionHelper.MAP);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode body = MapFunctionHelper.body(args.get(0));
        if (body == null) {
            return null;
        }
        final Set<String> removed = new HashSet<>();
        for (int i = 1; i < args.size(); i++) {
            final String key = MapFunctionHelper.keyName(args.get(i));
            if (key != null) {
                removed.add(key);
            }
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, JsonNode> entry : body.properties()) {
            if (!removed.contains(entry.getKey())) {
                result.set(entry.getKey(), entry.getValue());
            }
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return Integer.MAX_VALUE;
    }
}
