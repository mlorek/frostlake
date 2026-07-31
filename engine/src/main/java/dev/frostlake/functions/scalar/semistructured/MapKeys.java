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
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * MAP_KEYS(map) — the map's keys as an array, in key order. Live-verified: a NULL map
 * answers NULL, an empty map answers {@code []}, and the keys come back sorted rather than in
 * insertion order.
 */
public class MapKeys extends BuiltInFunction {

    public MapKeys() {
        super("MAP_KEYS", MapFunctionHelper.KEY_ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode body = MapFunctionHelper.body(args.get(0));
        if (body == null) {
            return null;
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final String key : MapFunctionHelper.sortedKeys(body)) {
            result.add(key);
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
