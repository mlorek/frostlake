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
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * MAP_PICK(map, key [, key …]) and MAP_PICK(map, keyArray) — the map narrowed to the named keys. Both
 * spellings are real overloads live ({@code SHOW BUILTIN FUNCTIONS} lists three signatures), and they
 * do not mix: {@code MAP_PICK(m, ARRAY_CONSTRUCT('a'), 'b')} is a compile error live.
 *
 * <p>Live-verified: a key the map does not hold is silently skipped rather than added as a
 * null ({@code MAP_PICK(<{'a':1,'b':2}>, 'zzz')} is {@code {}}), a repeated key is harmless, a NULL key
 * picks nothing — so {@code MAP_PICK(m, NULL)} and {@code MAP_PICK(m, NULL::ARRAY)} are both the EMPTY
 * map, not NULL — and only a NULL MAP answers NULL.
 */
public class MapPick extends BuiltInFunction {

    public MapPick() {
        super("MAP_PICK", MapFunctionHelper.MAP);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode body = MapFunctionHelper.body(args.get(0));
        if (body == null) {
            return null;
        }
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final String key : requestedKeys(args)) {
            final JsonNode value = body.get(key);
            if (value != null) {
                result.set(key, value);
            }
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    /** The keys asked for, taken from a key ARRAY when one was passed and from the varargs otherwise. */
    private List<String> requestedKeys(final List<Object> args) {
        final List<String> keys = new ArrayList<>();
        final ArrayNode array = args.size() == 2 ? keyArray(args.get(1)) : null;
        if (array != null) {
            for (final JsonNode element : array) {
                if (!element.isNull()) {
                    keys.add(element.isTextual() ? element.asText() : element.toString());
                }
            }
            return keys;
        }
        for (int i = 1; i < args.size(); i++) {
            final String key = MapFunctionHelper.keyName(args.get(i));
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * The argument as a key ARRAY, or null when it is a plain key. Asked of the RUNTIME value rather
     * than by re-parsing its text: a VARCHAR key is never an array, and a key that happens to spell one
     * ({@code MAP_PICK(m, '[1]')}) must stay the single key it is.
     */
    private ArrayNode keyArray(final Object value) {
        if (value instanceof VariantValue && ((VariantValue) value).isJsonArray()) {
            return (ArrayNode) ((VariantValue) value).node();
        }
        if (value instanceof JsonNode && ((JsonNode) value).isArray()) {
            return (ArrayNode) value;
        }
        return null;
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
