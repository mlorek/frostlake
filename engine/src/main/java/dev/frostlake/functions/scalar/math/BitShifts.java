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

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * BITSHIFTLEFT and BITSHIFTRIGHT over a whole number held in 128 bits, as the account holds it: a left shift
 * drops what leaves the 128 bits — {@code BITSHIFTLEFT(1, 127)} is the least 128-bit number and a shift by 128 or
 * more is 0 — a right shift keeps the sign ({@code BITSHIFTRIGHT(-16, 2)} is -4), and a negative count is refused
 * (live-verified).
 */
final class BitShifts {

    private static final int WIDTH = 128;

    private BitShifts() {
    }

    /**
     * @param value the number shifted
     * @param count the bits it is shifted by
     * @param left  whether to the left
     * @return the shifted number
     */
    static Object shifted(final long value, final long count, final boolean left) {
        if (count < 0) {
            throw new RuntimeException("Invalid parameter value: " + count
                + ". Reason: shift parameter must be greater or equal to zero");
        }
        final BigInteger whole = BigInteger.valueOf(value);
        final BigInteger moved;
        if (!left) {
            moved = whole.shiftRight((int) Math.min(count, WIDTH));
        } else if (count >= WIDTH) {
            moved = BigInteger.ZERO;
        } else {
            // What the 128 bits keep, read as a signed number.
            final BigInteger modulus = BigInteger.ONE.shiftLeft(WIDTH);
            final BigInteger kept = whole.shiftLeft((int) count).and(modulus.subtract(BigInteger.ONE));
            moved = kept.testBit(WIDTH - 1) ? kept.subtract(modulus) : kept;
        }
        return moved.bitLength() < 63 ? (Object) Long.valueOf(moved.longValue()) : new BigDecimal(moved);
    }
}
