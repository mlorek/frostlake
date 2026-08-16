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

/** ARRAY_COMPACT(array) — returns the array with all null elements removed. */
public class ArrayCompact extends BuiltInFunction {
    public ArrayCompact() { super("ARRAY_COMPACT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode el : src) {
            if (!el.isNull()) result.add(el);
        }
        return VariantValue.ofNode(result);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
