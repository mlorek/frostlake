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

import dev.frostlake.functions.ArrayArgumentKind;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;

import java.util.List;

/** ARRAY_SLICE(array, from, to) — returns a sub-array from index `from` (inclusive) to `to` (exclusive). */
public class ArraySlice extends BuiltInFunction {
    public ArraySlice() { super("ARRAY_SLICE", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        final ArrayNode src = ArrayFunctionHelper.parseArrayOrSingle(args.get(0));
        if (src == null) return null;
        // A text bound is read as the number it spells, and a fraction rounds (see wholeNumber).
        int from = isCount(args.get(1)) ? wholeNumber(args.get(1)) : 0;
        int to = args.size() > 2 && isCount(args.get(2)) ? wholeNumber(args.get(2)) : src.size();
        if (from < 0) from = Math.max(0, src.size() + from);
        if (to < 0) to = Math.max(0, src.size() + to);
        from = Math.min(from, src.size());
        to   = Math.min(to,   src.size());
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (int i = from; i < to; i++) result.add(src.get(i));
        return VariantValue.ofNode(result);
    }

    private static boolean isCount(final Object bound) {
        return bound instanceof Number || bound instanceof CharSequence || bound instanceof VariantValue;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** A VECTOR is no ARRAY: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection vectorRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public ArrayArgumentKind arrayArgumentKind(final int position) {
        if (position == 0) {
            return ArrayArgumentKind.ARRAY;
        }
        if (position == 1 || position == 2) {
            return ArrayArgumentKind.WHOLE_NUMBER;
        }
        return null;
    }
}
