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

import java.util.List;
import java.util.Map;

/**
 * MAP_INSERT(map, key, value [, updateFlag]) — the map with one entry added or replaced.
 *
 * <p>Its NULL handling is its own, and every branch was measured live rather than carried
 * over from {@code OBJECT_INSERT}, which differs on two of them: a NULL map answers NULL, a NULL KEY
 * answers NULL (OBJECT_INSERT returns the map unchanged), and a NULL VALUE is STORED as a JSON null
 * member — {@code MAP_INSERT(<{'a':1}>, 'b', NULL)} is {@code {"a":1,"b":null}} and {@code MAP_SIZE} of
 * it is 2, where OBJECT_INSERT omits the pair.
 *
 * <p>Inserting a key the map already holds is a RUN-TIME error, "Duplicate field key 'a'", unless the
 * fourth argument is TRUE; live gives the same error for an absent flag, an explicit FALSE and an
 * explicit NULL alike.
 */
public class MapInsert extends BuiltInFunction {

    public MapInsert() {
        super("MAP_INSERT", MapFunctionHelper.MAP);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode body = MapFunctionHelper.body(args.get(0));
        final String key = MapFunctionHelper.keyName(args.get(1));
        if (body == null || key == null) {
            return null;
        }
        final boolean update = args.size() > 3 && args.get(3) != null
            && Boolean.parseBoolean(args.get(3).toString());
        if (body.has(key) && !update) {
            throw new RuntimeException("Duplicate field key '" + key + "'");
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, JsonNode> entry : body.properties()) {
            if (!entry.getKey().equals(key)) {
                result.set(entry.getKey(), entry.getValue());
            }
        }
        result.set(key, ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, args.get(2)));
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override
    public int getMinArgCount() {
        return 3;
    }

    @Override
    public int getMaxArgCount() {
        return 4;
    }
}
