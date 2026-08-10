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
import dev.frostlake.values.VariantUndefined;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * ARRAY_MAX(array) — the largest non-null element as a VARIANT (live-verified: ARRAY_MAX(['a','c','b'])
 * displays as {@code "c"} with JSON quotes and SYSTEM$TYPEOF is VARIANT), or SQL NULL if the array is
 * empty, all-null, or NULL.
 */
public class ArrayMax extends BuiltInFunction {
    public ArrayMax() { super("ARRAY_MAX", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        JsonNode best = null;
        for (final JsonNode el : src) {
            // Only a VARIANT `undefined` element is skipped; a JSON null is a VALUE and ranks above
            // every other type. Live: ARRAY_MAX(ARRAY_CONSTRUCT(1,NULL,2)) is 2 while
            // ARRAY_MIN/ARRAY_MAX over PARSE_JSON('[1,null,2]') are 1 and the JSON null; over an
            // all-undefined array both are SQL NULL.
            if (VariantUndefined.isUndefined(el)) continue;
            if (best == null || ArrayFunctionHelper.compareNodes(el, best) > 0) best = el;
        }
        return best == null ? null : ArrayFunctionHelper.toCanonicalVariant(best);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
