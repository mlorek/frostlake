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
 * MAP_CAT(map1, map2) — the concatenation (merge) of two MAPs. Frostlake models a MAP as an OBJECT, so
 * the result is the object holding every key of both inputs; on a key present in both, {@code map2}'s
 * value wins.
 *
 * <p>A NULL on EITHER side makes the whole result NULL — it is not treated as an empty map. Frostlake
 * used to merge the other side through and answer it, which is the plausible reading and the wrong one:
 * live, {@code MAP_CAT(<{'a':1}>, NULL::MAP(VARCHAR,INT))} is NULL, and so is the same call
 * over two MAP COLUMNS where one row's second map is NULL — measured both ways round, and on columns as
 * well as literals, because a single spelling could not tell a NULL-propagating function from a
 * NULL-tolerant one.
 */
public class MapCat extends BuiltInFunction {

    public MapCat() {
        super("MAP_CAT", MapFunctionHelper.MAP);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode left = MapFunctionHelper.body(args.get(0));
        final JsonNode right = MapFunctionHelper.body(args.get(1));
        if (left == null || right == null) {
            return null;
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<String, JsonNode> entry : left.properties()) {
            result.set(entry.getKey(), entry.getValue());
        }
        for (final Map.Entry<String, JsonNode> entry : right.properties()) {
            result.set(entry.getKey(), entry.getValue());   // map2 overrides map1 on a shared key
        }
        // Snowflake serializes OBJECT members key-sorted; raw insertion order leaked map1-then-map2.
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
