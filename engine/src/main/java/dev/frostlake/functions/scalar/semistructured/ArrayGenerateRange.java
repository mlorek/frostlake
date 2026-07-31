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
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;

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
        final long start = ((Number) args.get(0)).longValue();
        final long stop = ((Number) args.get(1)).longValue();
        long step = 1;
        if (args.size() > 2) {
            if (args.get(2) == null) return null;
            step = ((Number) args.get(2)).longValue();
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        if (step > 0) {
            for (long i = start; i < stop; i += step) result.add(i);
        } else if (step < 0) {
            for (long i = start; i > stop; i += step) result.add(i);
        }
        return VariantValue.ofNode(result);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
