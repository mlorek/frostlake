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

package dev.frostlake.functions.scalar.math;

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;

import java.math.BigInteger;
import java.util.List;

/**
 * GETBIT(integer_expr, bit_position) — the value (0 or 1) of the bit at the 0-based {@code bit_position},
 * counting from the least significant bit. {@code GETBIT(11, 0)} is 1 and {@code GETBIT(11, 2)} is 0
 * (11 = binary 1011). A position beyond the value's set bits yields 0. Either NULL argument yields NULL;
 * a negative position is an error. Uses {@link BigInteger#testBit} so positions well past 64 bits are safe.
 */
public class Getbit extends NumericArgumentFunction {
    public Getbit() { super("GETBIT", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final long value = ((Number) args.get(0)).longValue();
        final int position = ((Number) args.get(1)).intValue();
        if (position < 0) {
            throw new RuntimeException("GETBIT bit_position must be non-negative: " + position);
        }
        return BigInteger.valueOf(value).testBit(position) ? 1L : 0L;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
