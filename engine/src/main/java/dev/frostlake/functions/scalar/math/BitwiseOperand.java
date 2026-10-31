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

import dev.frostlake.types.DecimalText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The whole number a bitwise function reads an argument as. Live rounds a fractional argument HALF
 * AWAY FROM ZERO before the bit operation — {@code BITAND(2.5, 1)} is 1 (2.5 reads as 3),
 * {@code BITAND(2.4, 1)} is 0, {@code BITAND(-2.5, 1)} is 1 (-3), {@code BITNOT(2.5)} is -4 and
 * {@code BITSHIFTLEFT(2.5, 1)} is 6 — for a NUMBER, a FLOAT and a numeric text alike (live-verified).
 * A truncating read gave the other answer on every one of them.
 */
public final class BitwiseOperand {

    private BitwiseOperand() {
    }

    /**
     * The argument as the whole number the bit operation works on.
     *
     * @param value a number (a text argument has been read as one by the dispatch)
     * @return the value rounded half away from zero
     */
    public static long whole(final Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        requireFinite(value);
        return DecimalText.parse(value.toString()).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * Refuses a FLOAT's NaN or infinity as the fixed-point number a bitwise function reads it as, in the account's
     * words: {@code BITNOT('NaN'::FLOAT)} is "Number out of representable range: type FIXED[SB8](18,0){not null},
     * value nan" (live-verified).
     *
     * @param value the argument
     */
    private static void requireFinite(final Object value) {
        if ((value instanceof Double || value instanceof Float) && !Double.isFinite(((Number) value).doubleValue())) {
            final double d = ((Number) value).doubleValue();
            throw new RuntimeException("Number out of representable range: type FIXED[SB8](18,0){not null}, value "
                + (Double.isNaN(d) ? "nan" : d > 0 ? "inf" : "-inf"));
        }
    }

    /**
     * The argument as the whole number a bitwise function works on, all of it: the functions compute in a signed
     * 128-bit integer, so 2^63 and past it keep their bits (live-verified).
     *
     * @param value a number (a text argument has been read as one by the dispatch)
     * @return the value rounded half away from zero
     */
    public static BigInteger wide(final Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        requireFinite(value);
        return DecimalText.parse(value.toString()).setScale(0, RoundingMode.HALF_UP).toBigInteger();
    }

    /**
     * A bitwise answer as the engine carries a whole number: a Long while it fits one, a BigDecimal past it.
     *
     * @param value the answer
     * @return the carried value
     */
    public static Object result(final BigInteger value) {
        return value.bitLength() < Long.SIZE ? (Object) Long.valueOf(value.longValue()) : new BigDecimal(value);
    }
}
