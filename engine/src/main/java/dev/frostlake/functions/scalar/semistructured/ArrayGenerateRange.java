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
import dev.frostlake.functions.WholeNumbers;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ArrayType;
import dev.frostlake.values.LobSize;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.node.ArrayNode;

import java.math.BigInteger;
import java.util.List;

/**
 * ARRAY_GENERATE_RANGE(start, stop [, step]) — an array of integers from start (inclusive) to stop
 * (EXCLUSIVE). step defaults to 1 and may be negative. Returns an empty array when the range is empty.
 */
public class ArrayGenerateRange extends BuiltInFunction {

    /**
     * The most numbers a range may hold: an array's values together take at most 128 MB, a number at least a byte, so
     * no array holds more than 134,217,728 of them (Semi-structured data types). A longer range is refused before
     * any of it is made.
     */
    private static final long MOST_ELEMENTS = LobSize.MAX_TEXT_BYTES;

    /** The longest range held a node per number; a longer one is read where it stands (see RangeArrayElements). */
    private static final long LONGEST_HELD = 1_000_000L;

    public ArrayGenerateRange() { super("ARRAY_GENERATE_RANGE", ArrayType.ARRAY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        // A bound or a step is read as the whole number it gives, a text's or a VARIANT's included and a fraction
        // rounded: ARRAY_GENERATE_RANGE(0, 2.6) is [0,1,2] and a step of 1.6 steps by 2 (live-verified).
        final long start = WholeNumbers.wholeLong(args.get(0));
        final long stop = WholeNumbers.wholeLong(args.get(1));
        long step = 1;
        if (args.size() > 2) {
            if (args.get(2) == null) return null;
            step = WholeNumbers.wholeLong(args.get(2));
        }
        final long count = elements(start, stop, step);
        if (count > MOST_ELEMENTS) {
            throw new RuntimeException("Result array of " + getName() + " is too large");
        }
        if (count > LONGEST_HELD) {
            return VariantValue.ofNodeLazily(new ArrayNode(ArrayFunctionHelper.MAPPER.getNodeFactory(),
                new RangeArrayElements(start, step, (int) count)));
        }
        final ArrayNode result = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (long k = 0; k < count; k++) {
            result.add(start + k * step);
        }
        return VariantValue.ofNode(result);
    }

    /**
     * How many numbers a range holds: none for a step of 0 or a step away from the stop, else the steps from the
     * start that stay short of it, saturated past Long.MAX_VALUE.
     */
    private static long elements(final long start, final long stop, final long step) {
        if (step == 0 || (step > 0 ? start >= stop : start <= stop)) {
            return 0;
        }
        final BigInteger span = BigInteger.valueOf(stop).subtract(BigInteger.valueOf(start)).abs();
        final BigInteger[] steps = span.divideAndRemainder(BigInteger.valueOf(step).abs());
        final BigInteger count = steps[1].signum() == 0 ? steps[0] : steps[0].add(BigInteger.ONE);
        return count.bitLength() < Long.SIZE - 1 ? count.longValue() : Long.MAX_VALUE;
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
