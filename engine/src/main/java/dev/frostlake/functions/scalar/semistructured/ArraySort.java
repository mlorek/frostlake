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
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ARRAY_SORT(array [, sort_ascending [, nulls_first]]) — sorts elements. sort_ascending defaults to TRUE.
 * nulls_first defaults to FALSE for ascending order and TRUE for descending order.
 *
 * <p>{@code nulls_first} governs the VARIANT {@code undefined} elements ONLY. A JSON null is a VALUE and
 * is sorted by the comparator, which ranks it above every other variant type — so it lands last ascending
 * and first descending whatever the flag says. Two numeric elements are compared by value; otherwise
 * elements are compared lexically.
 */
public class ArraySort extends BuiltInFunction {
    public ArraySort() { super("ARRAY_SORT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        final boolean ascending = args.size() < 2 || args.get(1) == null || toBoolean(args.get(1));
        final boolean nullsFirst = (args.size() < 3 || args.get(2) == null) ? !ascending : toBoolean(args.get(2));

        // Only a VARIANT `undefined` element is a NULL for the nulls_first/nulls_last flag; a JSON null is a
        // VALUE and sorts by the comparator (which ranks it above every other type). Live-verified
        // ARRAY_SORT(ARRAY_CONSTRUCT(2,NULL,1), TRUE, TRUE) is [undefined,1,2] while the same
        // call over PARSE_JSON('[2,null,1]') stays [1,2,null] — nulls_first does not move a JSON null — and
        // ARRAY_SORT(ARRAY_CONSTRUCT(2,NULL,PARSE_JSON('null'),1)) is [1,2,null,undefined].
        final List<JsonNode> nonNull = new ArrayList<>();
        int nullCount = 0;
        for (final JsonNode el : src) {
            if (VariantUndefined.isUndefined(el)) nullCount++;
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
            for (int i = 0; i < nullCount; i++) result.add(VariantUndefined.node());
        }
        for (final JsonNode el : nonNull) result.add(el);
        if (!nullsFirst) {
            for (int i = 0; i < nullCount; i++) result.add(VariantUndefined.node());
        }
        return VariantValue.ofNode(result);
    }

    private static boolean toBoolean(final Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 3; }
}
