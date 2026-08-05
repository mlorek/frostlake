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

import dev.frostlake.functions.VariantAccessorFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ObjectType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OBJECT_INSERT(object, key, value [, update_flag]) — inserts/updates a key in an object.
 * Snowflake null semantics: a SQL NULL key or value OMITS the pair from the returned object (an
 * already-present key is removed), while a JSON null ({@code PARSE_JSON('null')}) is stored as a
 * real null member. Without the update flag, inserting a key that already exists is an error.
 */
public class ObjectInsert extends VariantAccessorFunction {
    public ObjectInsert() { super("OBJECT_INSERT", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        JsonNode src = ArrayFunctionHelper.parseNode(args.get(0));
        if (src == null || !src.isObject()) return null;
        if (args.get(1) == null) return ArrayFunctionHelper.toCanonicalVariant(src);
        String key = args.get(1).toString();
        final Object value = args.size() > 2 ? args.get(2) : null;
        final boolean update = args.size() > 3 && args.get(3) != null
            && Boolean.parseBoolean(args.get(3).toString());

        ObjectNode result = ArrayFunctionHelper.MAPPER.createObjectNode();
        boolean existed = false;
        Set<Map.Entry<String, JsonNode>> fields = src.properties();
        for (final Map.Entry<String, JsonNode> e :  fields) {
            if (e.getKey().equals(key)) {
                existed = true;
                continue;
            }
            result.set(e.getKey(), e.getValue());
        }
        if (existed && !update && value != null) {
            throw new RuntimeException(
                "OBJECT_INSERT: key '" + key + "' already exists; pass the update flag to overwrite it");
        }
        // A SQL NULL value omits the pair entirely — the key is neither added nor kept. A VARIANT
        // JSON null (the text "null") is a real value and toNode restores it as a null member.
        if (value != null) {
            result.set(key, ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
        }
        return ArrayFunctionHelper.toCanonicalVariant(result);
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 4; }
}
