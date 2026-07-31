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
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * MAP_CAT(map1, map2) — the concatenation (merge) of two MAPs. frostlake models a MAP as an OBJECT, so the
 * result is the object holding every key of both inputs; on a key present in both, {@code map2}'s value wins.
 * A NULL input is treated as an empty map; if both are NULL (or non-objects) the result is NULL.
 */
public class MapCat extends BuiltInFunction {
    public MapCat() { super("MAP_CAT", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode a = ArrayFunctionHelper.parseNode(args.get(0));
        final JsonNode b = ArrayFunctionHelper.parseNode(args.get(1));
        final boolean aObj = a != null && a.isObject();
        final boolean bObj = b != null && b.isObject();
        if (!aObj && !bObj) {
            return null;
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        if (aObj) {
            for (final Map.Entry<String, JsonNode> e : a.properties()) {
                result.set(e.getKey(), e.getValue());
            }
        }
        if (bObj) {
            for (final Map.Entry<String, JsonNode> e : b.properties()) {
                result.set(e.getKey(), e.getValue());   // map2 overrides map1 on a shared key
            }
        }
        // Snowflake serializes OBJECT members key-sorted; raw insertion order leaked map1-then-map2.
        return ArrayFunctionHelper.toCanonicalJson(result);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
