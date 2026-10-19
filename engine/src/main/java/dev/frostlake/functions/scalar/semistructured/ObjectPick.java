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
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * OBJECT_PICK(object, key1 [, key2, ...]) and OBJECT_PICK(object, array) — an object with only the keys named, in
 * arguments of their own or as the elements of one ARRAY, its NULL elements naming none (live-verified). An ARRAY
 * beside further keys is refused by the argument types, and so is a structured ARRAY; a structured OBJECT takes
 * string literals alone as its keys.
 */
public class ObjectPick extends BuiltInFunction {
    public ObjectPick() { super("OBJECT_PICK", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final JsonNode src = ArrayFunctionHelper.parseNode(args.get(0));
        if (src == null || !src.isObject()) return null;
        final ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        final List<Object> keys = new ArrayList<>();
        final ArrayNode keyArray = args.size() == 2 ? keyArray(args.get(1)) : null;
        if (keyArray != null) {
            for (final JsonNode element : keyArray) {
                if (element != null && element.isTextual()) {
                    keys.add(element.asText());
                }
            }
        } else {
            keys.addAll(args.subList(1, args.size()));
        }
        for (final Object written : keys) {
            if (written == null) continue;
            final String key = written.toString();
            final JsonNode val = src.get(key);
            if (val != null) result.set(key, val);
        }
        // Snowflake serializes OBJECT members key-sorted; raw insertion order leaked argument order.
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    /** The keys an ARRAY argument names: an ARRAY value, never a text that merely spells one. */
    private static ArrayNode keyArray(final Object argument) {
        if (!(argument instanceof VariantValue) || !(((VariantValue) argument).node() instanceof ArrayNode)) {
            return null;
        }
        return (ArrayNode) ((VariantValue) argument).node();
    }

    @Override
    public String signatureName(final int count) {
        return "OBJECT_PICK";
    }

    @Override
    public boolean namesStructuredKeysByLiteral() {
        return true;
    }

    /**
     * An ARRAY of keys stands alone and is a plain ARRAY: beside further keys, or structured, the call is refused
     * by its argument types.
     */
    @Override
    public boolean refusesSignatureTypes(final List<DataType> types) {
        if (types.size() <= 2) {
            return types.size() == 2 && types.get(1) instanceof StructuredArrayType;
        }
        for (int i = 1; i < types.size(); i++) {
            if (types.get(i) instanceof ArrayType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
