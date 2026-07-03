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
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_INSERT(array, pos, element) — inserts element at 0-based index pos, shifting later elements right.
 * pos == size appends. A negative pos is an index from the back (-1 inserts before the last element). If the
 * absolute position exceeds the array size, empty (null) elements pad the gap between the source and element.
 */
public class ArrayInsert extends BuiltInFunction {
    public ArrayInsert() { super("ARRAY_INSERT", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        if (args.get(1) == null) return null;
        final int n = src.size();
        final int pos = ((Number) args.get(1)).intValue();
        final JsonNode element = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, args.get(2));
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();

        if (pos >= 0) {
            if (pos <= n) {
                for (int i = 0; i < pos; i++) result.add(src.get(i));
                result.add(element);
                for (int i = pos; i < n; i++) result.add(src.get(i));
            } else {
                for (int i = 0; i < n; i++) result.add(src.get(i));
                for (int i = n; i < pos; i++) result.addNull();
                result.add(element);
            }
        } else {
            final int target = n + pos;
            if (target >= 0) {
                for (int i = 0; i < target; i++) result.add(src.get(i));
                result.add(element);
                for (int i = target; i < n; i++) result.add(src.get(i));
            } else {
                result.add(element);
                for (int i = target; i < -1; i++) result.addNull();
                for (int i = 0; i < n; i++) result.add(src.get(i));
            }
        }
        return result.toString();
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 3; }
}
