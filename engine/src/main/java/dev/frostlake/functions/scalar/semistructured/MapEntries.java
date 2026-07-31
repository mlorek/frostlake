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
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * MAP_ENTRIES(map) — the map's entries as an array of {@code {"key": …, "value": …}} objects, in key
 * order. Live-verified: the member names are lower-case {@code key} and {@code value}
 * ({@code MAP_ENTRIES(<{'a':1}>)[0]} is {@code {"key":"a","value":1}}), a NULL map answers NULL and an
 * empty map answers {@code []}.
 */
public class MapEntries extends BuiltInFunction {

    public MapEntries() {
        super("MAP_ENTRIES", MapFunctionHelper.KEY_ARRAY);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode body = MapFunctionHelper.body(args.get(0));
        if (body == null) {
            return null;
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final String key : MapFunctionHelper.sortedKeys(body)) {
            final ObjectNode entry = ArrayFunctionHelper.MAPPER.createObjectNode();
            entry.put("key", key);
            entry.set("value", body.get(key));
            result.add(entry);
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
