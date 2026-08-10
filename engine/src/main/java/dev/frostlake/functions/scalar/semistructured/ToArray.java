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
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * TO_ARRAY(expr) — NULL (or JSON null) returns NULL; an existing array is returned unchanged; any other
 * scalar value is wrapped in a single-element array.
 */
public class ToArray extends BuiltInFunction {
    public ToArray() { super("TO_ARRAY", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object v = args.get(0);
        if (v == null) return null;
        final ArrayNode existing = ArrayFunctionHelper.parseArray(v);
        if (existing != null) return VariantValue.ofNode(existing);
        final JsonNode node = ArrayFunctionHelper.parseNode(v);
        if (node != null && node.isNull()) return null;
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        result.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, v));
        return VariantValue.ofNode(result);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
