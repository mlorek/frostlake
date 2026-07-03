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
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/** ARRAY_SLICE(array, from, to) — returns a sub-array from index `from` (inclusive) to `to` (exclusive). */
public class ArraySlice extends BuiltInFunction {
    public ArraySlice() { super("ARRAY_SLICE", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        ArrayNode src = ArrayFunctionHelper.parseArray(args.get(0));
        if (src == null) return null;
        int from = args.get(1) instanceof Number ? ((Number) args.get(1)).intValue() : 0;
        int to   = args.size() > 2 && args.get(2) instanceof Number ? ((Number) args.get(2)).intValue() : src.size();
        if (from < 0) from = Math.max(0, src.size() + from);
        if (to < 0) to = Math.max(0, src.size() + to);
        from = Math.min(from, src.size());
        to   = Math.min(to,   src.size());
        ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int i = from; i < to; i++) result.add(src.get(i));
        return result.toString();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
