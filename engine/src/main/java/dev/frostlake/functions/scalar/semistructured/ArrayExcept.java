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
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ARRAY_EXCEPT(array1, array2) — the MULTISET difference: max(0, N-M) copies of each element. */
public class ArrayExcept extends BuiltInFunction {
    public ArrayExcept() { super("ARRAY_EXCEPT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode a1 = ArrayFunctionHelper.parseArray(args.get(0));
        final ArrayNode a2 = ArrayFunctionHelper.parseArray(args.get(1));
        if (a1 == null || a2 == null) return null;
        // MULTISET difference, not a set difference: with N copies in array1 and M in array2 the result keeps
        // max(0, N-M) copies, in array1's order. Live-verified: ARRAY_EXCEPT([1,1,2], []) is
        // [1,1,2] (duplicates survive), ARRAY_EXCEPT(ARRAY_CONSTRUCT(NULL,1,NULL), ARRAY_CONSTRUCT(1)) is
        // [undefined,undefined] and ARRAY_EXCEPT(ARRAY_CONSTRUCT(1,NULL,2), ARRAY_CONSTRUCT(1)) is
        // [undefined,2]. An `undefined` is removed only by another `undefined`, never by a JSON null:
        // ARRAY_EXCEPT(PARSE_JSON('[1,null,2]'), ARRAY_CONSTRUCT(NULL)) is [1,null,2].
        final Map<String, Integer> remaining = new HashMap<>();
        for (final JsonNode el : a2) {
            final String k = ArrayFunctionHelper.elementKey(el);
            final Integer count = remaining.get(k);
            remaining.put(k, count == null ? 1 : count + 1);
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode el : a1) {
            final String k = ArrayFunctionHelper.elementKey(el);
            final Integer count = remaining.get(k);
            if (count != null && count > 0) {
                remaining.put(k, count - 1);
            } else {
                result.add(el);
            }
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
