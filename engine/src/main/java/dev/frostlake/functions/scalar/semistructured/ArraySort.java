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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ARRAY_SORT(array [, sort_ascending [, nulls_first]]) — sorts elements. sort_ascending defaults to TRUE.
 * nulls_first defaults to FALSE for ascending order (NULLs last) and TRUE for descending order (NULLs first).
 * Two numeric elements are compared by value; otherwise elements are compared lexically.
 */
public class ArraySort extends BuiltInFunction {
    public ArraySort() { super("ARRAY_SORT", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        final boolean ascending = args.size() < 2 || args.get(1) == null || toBoolean(args.get(1));
        final boolean nullsFirst = (args.size() < 3 || args.get(2) == null) ? !ascending : toBoolean(args.get(2));

        final List<JsonNode> nonNull = new ArrayList<>();
        int nullCount = 0;
        for (final JsonNode el : src) {
            if (el.isNull()) nullCount++;
            else nonNull.add(el);
        }
        Collections.sort(nonNull, new Comparator<JsonNode>() {
            @Override
            public int compare(final JsonNode a, final JsonNode b) {
                final int c = ArrayFunctionHelper.compareNodes(a, b);
                return ascending ? c : -c;
            }
        });

        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        if (nullsFirst) {
            for (int i = 0; i < nullCount; i++) result.addNull();
        }
        for (final JsonNode el : nonNull) result.add(el);
        if (!nullsFirst) {
            for (int i = 0; i < nullCount; i++) result.addNull();
        }
        return result.toString();
    }

    private static boolean toBoolean(final Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 3; }
}
