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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;

import java.math.RoundingMode;
import java.util.List;

/**
 * ARRAY_GENERATE_RANGE(start, stop [, step]) — an array of integers from start (inclusive) to stop
 * (EXCLUSIVE). step defaults to 1 and may be negative. Returns an empty array when the range is empty.
 */
public class ArrayGenerateRange extends BuiltInFunction {
    public ArrayGenerateRange() { super("ARRAY_GENERATE_RANGE", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        // A text or a VARIANT bound is read as the whole number it holds (see decimalOf).
        final long start = wholeLong(args.get(0));
        final long stop = wholeLong(args.get(1));
        long step = 1;
        if (args.size() > 2) {
            if (args.get(2) == null) return null;
            step = wholeLong(args.get(2));
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        if (step > 0) {
            for (long i = start; i < stop; i += step) result.add(i);
        } else if (step < 0) {
            for (long i = start; i > stop; i += step) result.add(i);
        }
        return VariantValue.ofNode(result);
    }

    /** A bound as the whole number it holds: a number as before, a text or a VARIANT as {@link #decimalOf} reads it. */
    private static long wholeLong(final Object value) {
        return value instanceof Number ? ((Number) value).longValue()
            : decimalOf(value).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    @Override
    public ArrayArgumentKind arrayArgumentKind(final int position) {
        if (position == 0 || position == 1) {
            return ArrayArgumentKind.WHOLE_NUMBER;
        }
        return null;
    }
}
