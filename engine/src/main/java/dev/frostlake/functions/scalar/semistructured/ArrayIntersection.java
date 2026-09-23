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
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ARRAY_INTERSECTION(array1, array2) — the MULTISET intersection: min(N, M) copies of each element. */
public class ArrayIntersection extends BuiltInFunction {
    public ArrayIntersection() { super("ARRAY_INTERSECTION", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode a1 = ArrayFunctionHelper.parseArray(args.get(0));
        final ArrayNode a2 = ArrayFunctionHelper.parseArray(args.get(1));
        if (a1 == null || a2 == null) return null;
        // MULTISET intersection: with N copies in array1 and M in array2 the result keeps min(N,M), in
        // array1's order — except that an `undefined` is emitted LAST. Live-verified:
        // ARRAY_INTERSECTION(ARRAY_CONSTRUCT(NULL,1,NULL), ARRAY_CONSTRUCT(NULL,1)) is [1,undefined],
        // ARRAY_INTERSECTION(ARRAY_CONSTRUCT(1,NULL,2), ARRAY_CONSTRUCT(NULL)) is [undefined] and
        // ARRAY_INTERSECTION(ARRAY_CONSTRUCT(1,NULL,2), PARSE_JSON('[1,null,2]')) is [1,2] — an `undefined`
        // never matches a JSON null (ARRAY_INTERSECTION(PARSE_JSON('[1,null,2]'), [NULL]) is []).
        final Map<String, Integer> remaining = new HashMap<>();
        for (final JsonNode el : a2) {
            final String k = ArrayFunctionHelper.elementKey(el);
            final Integer count = remaining.get(k);
            remaining.put(k, count == null ? 1 : count + 1);
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        int undefinedMatches = 0;
        for (final JsonNode el : a1) {
            final String k = ArrayFunctionHelper.elementKey(el);
            final Integer count = remaining.get(k);
            if (count == null || count == 0) {
                continue;
            }
            remaining.put(k, count - 1);
            if (VariantUndefined.isUndefined(el)) {
                undefinedMatches++;
            } else {
                result.add(el);
            }
        }
        for (int i = 0; i < undefinedMatches; i++) {
            result.add(VariantUndefined.node());
        }
        return VariantValue.ofNode(result);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
