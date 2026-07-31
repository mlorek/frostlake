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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** ARRAY_DISTINCT(array) — removes duplicate elements. */
public class ArrayDistinct extends BuiltInFunction {
    public ArrayDistinct() { super("ARRAY_DISTINCT", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        // Live-verified: when nothing is a duplicate the array comes back UNCHANGED
        // (ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL,1,2)) is [undefined,1,2], ARRAY_DISTINCT([2,NULL,1]) is
        // [2,undefined,1]); as soon as a duplicate is dropped the surviving values keep first-occurrence
        // order but the `undefined` moves LAST — ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL,1,1)) is [1,undefined],
        // ARRAY_DISTINCT(ARRAY_CONSTRUCT(NULL,2,NULL,1)) is [2,1,undefined]. A JSON null keeps its
        // first-occurrence position throughout (ARRAY_DISTINCT of [null,1,null,2] is [null,1,2]).
        final Set<String> seen = new LinkedHashSet<>();
        final ArrayNode values = ArrayFunctionHelper.MAPPER.createArrayNode();
        boolean anyUndefined = false;
        for (final JsonNode el : src) {
            if (VariantUndefined.isUndefined(el)) {
                anyUndefined = true;
                continue;
            }
            if (seen.add(el.toString())) values.add(el);
        }
        final int distinctSize = values.size() + (anyUndefined ? 1 : 0);
        if (distinctSize == src.size()) {
            return VariantValue.ofNode(src);
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final JsonNode el : values) result.add(el);
        if (anyUndefined) result.add(VariantUndefined.node());
        return VariantValue.ofNode(result);
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
