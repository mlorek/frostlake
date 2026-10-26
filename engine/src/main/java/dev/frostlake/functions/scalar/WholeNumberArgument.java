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

import dev.frostlake.functions.WholeNumbers;
import dev.frostlake.functions.scalar.math.FloatingPointRefusal;
import dev.frostlake.types.DecimalText;

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
    /** The fixed-point type a rounding function reads its scale as. */
    public static final String SCALE = "FIXED[SB2](4,0)";

    /** The first FLOAT the nine digits of {@link #PART} cannot hold. */
    private static final double PART_LIMIT = 1e9;
    /** The first FLOAT the eighteen digits of {@link #WIDE} cannot hold. */
    private static final double WIDE_LIMIT = 1e18;
    /** The first FLOAT the four digits of {@link #SCALE} cannot hold. */
    private static final double SCALE_LIMIT = 1e4;

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
            return DecimalText.parse(text).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        } catch (final NumberFormatException notANumber) {
            throw new RuntimeException("Numeric value '" + value + "' is not recognized");
        }
    }

    /**
     * Refuses a FLOAT a function reads through a fixed-point slot the FLOAT does not fit (live-verified): NaN, an
     * infinity and a value that rounds half away from zero to the slot's first number it cannot hold - a billion
     * for {@link #PART}, 10^18 for {@link #WIDE}, ten thousand for {@link #SCALE} - are refused as that type, the
     * value as C's {@code %g} prints it and the argument's nullability spelled in: {@code SUBSTR('abcdef',
     * 1e30::FLOAT)} is "Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+30". Any other
     * value passes.
     *
     * @param value     the argument
     * @param fixedType {@link #PART}, {@link #WIDE} or {@link #SCALE}
     * @param nullable  whether the argument may be NULL
     */
    public static void requireFittingFloat(final Object value, final String fixedType, final boolean nullable) {
        if (!(value instanceof Double) && !(value instanceof Float)) {
            return;
        }
        final double d = ((Number) value).doubleValue();
        final double limit = PART.equals(fixedType) ? PART_LIMIT : SCALE.equals(fixedType) ? SCALE_LIMIT : WIDE_LIMIT;
        if (!Double.isNaN(d) && Math.floor(Math.abs(d) + 0.5) < limit) {
            return;
        }
        throw new RuntimeException("Number out of representable range: type " + fixedType + "{"
            + (nullable ? "nullable" : "not null") + "}, value " + FloatingPointRefusal.number(d));
    }

    /**
     * A 32-bit whole number a function reads through the {@link #PART} slot - the position CHARINDEX starts from,
     * the part STRTOK takes, an ARRAY_SLICE bound, an ARRAY_INSERT position, an XMLGET instance, a padded length
     * (live-verified): a FLOAT the slot cannot hold is refused as {@link #requireFittingFloat} says, an exact number
     * past 64 bits is "Numeric value '10000000000000000000000000' is not recognized" and one past 32 "Numeric value
     * '3000000000' is out of range".
     *
     * @param value a number, a text or a VARIANT
     * @return the whole number
     */
    public static int part(final Object value) {
        requireFittingFloat(value, PART, false);
        final BigInteger whole = wideWhole(value);
        if (whole.bitLength() > Integer.SIZE - 1) {
            throw new RuntimeException("Numeric value '" + whole + "' is out of range");
        }
        return whole.intValue();
    }

    /**
     * A position or a count a function reads through the {@link #WIDE} slot and looks for in a text - a REGEXP
     * function's position, occurrence, option or group (live-verified): a FLOAT the slot cannot hold is refused as
     * {@link #requireFittingFloat} says and an exact number past 64 bits as {@link #wideWhole} says, while one past
     * 32 bits names no place in any text and is held to the nearest 32-bit one.
     *
     * @param value a number, a text or a VARIANT
     * @return the whole number
     */
    public static int widePosition(final Object value) {
        final BigInteger whole = wideWhole(value);
        if (whole.bitLength() > Integer.SIZE - 1) {
            return whole.signum() < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        return whole.intValue();
    }

    /**
     * The whole number of an argument a function reads through the {@link #WIDE} slot (live-verified): a fraction
     * goes to the nearest whole number, half away from zero, a FLOAT the slot cannot hold is refused as
     * {@link #requireFittingFloat} says, and an exact number past 64 bits is "Numeric value
     * '10000000000000000000000000' is not recognized".
     *
     * @param value a number, a text or a VARIANT
     * @return the whole number
     */
    public static BigInteger wideWhole(final Object value) {
        requireFittingFloat(value, WIDE, false);
        final BigInteger whole = WholeNumbers.decimalOf(value).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        if (whole.bitLength() > Long.SIZE - 1) {
            throw new RuntimeException("Numeric value '" + whole + "' is not recognized");
        }
        return whole;
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
     * A padded length or count, as LPAD, RPAD, SPACE and REPEAT read it (live-verified): a 32-bit whole number read
     * through the {@link #PART} slot as {@link #part} reads one, so an exact number past 32 bits is {@code Numeric
     * value '2147483648' is out of range} and a FLOAT from a billion on is refused with the double as C's {@code %g}
     * prints it: {@code Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09}.
     *
     * @param value a number, or a text spelling one
     * @return the whole number
     */
    public static int padLength(final Object value) {
        return part(value);
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
