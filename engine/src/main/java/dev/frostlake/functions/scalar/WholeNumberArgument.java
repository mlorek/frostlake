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

package dev.frostlake.functions.scalar;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * An argument read as the whole number a function works on, as the account reads one (live-verified): a fraction
 * goes to the nearest whole number, half away from zero; a FLOAT's NaN or infinity is refused as the fixed-point
 * number the function reads it as — "Number out of representable range: type FIXED[SB4](9,0){not null}, value nan"
 * for a date part — and a whole number too wide for the function is refused rather than cut to its low bits:
 * "Numeric value '4294967297' is out of range" for a 32-bit part, "Numeric value '18446744073709551621' is not
 * recognized" for a 64-bit one.
 */
public final class WholeNumberArgument {

    /** The fixed-point type a date or time part is read as. */
    public static final String PART = "FIXED[SB4](9,0)";
    /** The fixed-point type a 64-bit argument is read as. */
    public static final String WIDE = "FIXED[SB8](18,0)";
    /** The fixed-point type FACTORIAL reads its argument as. */
    public static final String FACTORIAL = "FIXED[SB1](2,0)";

    private WholeNumberArgument() {
    }

    /**
     * The argument as a whole number, all of it.
     *
     * @param value     a number, or a text spelling one
     * @param fixedType the fixed-point type a NaN or an infinity is refused as
     * @return the whole number
     */
    public static BigInteger rounded(final Object value, final String fixedType) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return BigInteger.valueOf(((Number) value).longValue());
        }
        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }
        if ((value instanceof Double || value instanceof Float) && !Double.isFinite(((Number) value).doubleValue())) {
            final double d = ((Number) value).doubleValue();
            throw new RuntimeException("Number out of representable range: type " + fixedType + "{not null}, value "
                + (Double.isNaN(d) ? "nan" : d > 0 ? "inf" : "-inf"));
        }
        final String text = value.toString().trim();
        try {
            return new BigDecimal(text).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        } catch (final NumberFormatException notANumber) {
            throw new RuntimeException("Numeric value '" + value + "' is not recognized");
        }
    }

    /**
     * The argument as a 32-bit whole number: a date or time part.
     *
     * @param value a number, or a text spelling one
     * @return the whole number
     */
    public static int int32(final Object value) {
        final BigInteger whole = rounded(value, PART);
        if (whole.bitLength() > 31) {
            throw new RuntimeException("Numeric value '" + whole + "' is out of range");
        }
        return whole.intValue();
    }

    /**
     * The argument as a 64-bit whole number.
     *
     * @param value     a number, or a text spelling one
     * @param fixedType the fixed-point type a NaN or an infinity is refused as
     * @return the whole number
     */
    public static long int64(final Object value, final String fixedType) {
        final BigInteger whole = rounded(value, fixedType);
        if (whole.bitLength() > 63) {
            throw new RuntimeException("Numeric value '" + whole + "' is not recognized");
        }
        return whole.longValue();
    }
}
