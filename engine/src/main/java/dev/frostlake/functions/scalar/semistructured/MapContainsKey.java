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
import dev.frostlake.types.BooleanType;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * MAP_CONTAINS_KEY(key, map) — whether the map holds the key. The MAP is the SECOND argument here, the
 * one place in the family where it is not first. Live-verified: a missing key is FALSE, an
 * empty map is FALSE, and a NULL on EITHER side is NULL rather than FALSE — {@code
 * MAP_CONTAINS_KEY(NULL, <{'a':1}>)} and {@code MAP_CONTAINS_KEY('a', NULL::MAP(VARCHAR,INT))} both
 * answer NULL.
 */
public class MapContainsKey extends BuiltInFunction {

    public MapContainsKey() {
        super("MAP_CONTAINS_KEY", BooleanType.BOOLEAN);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String key = MapFunctionHelper.keyName(args.get(0));
        final JsonNode body = MapFunctionHelper.body(args.get(1));
        if (key == null || body == null) {
            return null;
        }
        return Boolean.valueOf(body.has(key));
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
