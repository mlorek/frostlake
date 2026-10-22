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
import dev.frostlake.functions.WholeNumbers;
import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.types.IntegerResultWidths;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/**
 * GETBIT(integer_expr, bit_position) — the value (0 or 1) of the bit at the 0-based {@code bit_position},
 * counting from the least significant bit. {@code GETBIT(11, 0)} is 1 and {@code GETBIT(11, 2)} is 0
 * (11 = binary 1011). A position beyond the value's set bits yields 0. Either NULL argument yields NULL;
 * a negative position is an error. Uses {@link BigInteger#testBit} so positions well past 64 bits are safe.
 */
public class Getbit extends NumericArgumentFunction {
    /** The last of the 128 places a bit is read from. */
    private static final int LAST_PLACE = 127;

    public Getbit() { super("GETBIT", IntegerResultWidths.WIDEST); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        // The bits of all 128, as the shifts that set them keep them (live-verified).
        final BigInteger value = BitwiseOperand.wide(args.get(0));
        // A bit's place is read as the whole number it gives, a fraction rounded, through the 64-bit slot; one past
        // the 128 places is refused naming it as written: "Bit index 3000000000 out of range; Should be between 0
        // and 127" (live-verified).
        WholeNumberArgument.requireFittingFloat(args.get(1), WholeNumberArgument.WIDE, false);
        final BigInteger place = WholeNumbers.decimalOf(args.get(1)).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        if (place.signum() < 0) {
            throw new RuntimeException("GETBIT bit_position must be non-negative: " + place);
        }
        if (place.compareTo(BigInteger.valueOf(LAST_PLACE)) > 0) {
            throw new RuntimeException("Bit index " + place + " out of range; Should be between 0 and " + LAST_PLACE);
        }
        return value.testBit(place.intValue()) ? 1L : 0L;
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
