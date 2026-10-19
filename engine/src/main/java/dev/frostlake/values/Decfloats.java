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

package dev.frostlake.values;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * DECFLOAT's values: a decimal of at most 38 significant digits, carried as a {@link BigDecimal} with its trailing
 * zeros dropped and no negative scale (live-verified throughout):
 *
 * <ul>
 *   <li>a value of more digits rounds half away from zero to 38: {@code 1::DECFLOAT / 3} is
 *       0.33333333333333333333333333333333333333, and {@code 2.5::DECFLOAT::INT} is 3 where {@code -2.5} is -3;</li>
 *   <li>a FLOAT converts at its exact binary value: {@code 1e38} (a FLOAT literal) is
 *       99999999999999997748809823456034029568;</li>
 *   <li>its text is plain when the plain form needs at most 38 digits, the 0 before the point of a value under one
 *       counted, and otherwise every significant digit in scientific form with a bare exponent:
 *       {@code 0.0000000001}, {@code 1000000000000000000000000000000},
 *       {@code 3.3333333333333333333333333333333333333e-1}, {@code 1e-60}.</li>
 * </ul>
 */
public final class Decfloats {

    /** The significant digits a DECFLOAT keeps. */
    public static final int DIGITS = 38;

    private static final MathContext CONTEXT = new MathContext(DIGITS, RoundingMode.HALF_UP);

    /** The largest exponent a DECFLOAT's last digit takes. */
    public static final int MAX_EXPONENT = 16384;

    /** The smallest exponent a DECFLOAT's last digit takes. */
    public static final int MIN_EXPONENT = -16383;

    private Decfloats() {
    }

    /**
     * A decimal as a DECFLOAT holds it: rounded to 38 significant digits, its trailing zeros dropped, and a whole
     * number written out in full. Its last digit's exponent lies between -16383 and 16384: a value finer than
     * 1e-16383 rounds at it, half away from zero, so {@code DECFLOAT '1e-16400'} is 0; a value whose last digit
     * stands higher keeps it at 16384 with the coefficient grown, {@code DECFLOAT '1e16385'} being 10e16384; and one
     * whose coefficient would then pass 38 digits is past the range (live-verified).
     *
     * @param value the decimal
     * @return the value held
     * @throws DecfloatRangeException past the range
     */
    public static BigDecimal held(final BigDecimal value) {
        final BigDecimal rounded = value.round(CONTEXT);
        if (rounded.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal stripped = rounded.stripTrailingZeros();
        if (-stripped.scale() < MIN_EXPONENT) {
            stripped = stripped.setScale(-MIN_EXPONENT, RoundingMode.HALF_UP);
            if (stripped.signum() == 0) {
                return BigDecimal.ZERO;
            }
            stripped = stripped.stripTrailingZeros();
        }
        if (-stripped.scale() > MAX_EXPONENT && stripped.precision() - stripped.scale() - MAX_EXPONENT > DIGITS) {
            throw new DecfloatRangeException();
        }
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * A number as a DECFLOAT holds it: an exact one by its digits, a FLOAT at its exact binary value. A DECFLOAT holds
     * no infinity and no NaN, which are refused naming the operation.
     *
     * @param value     the number
     * @param operation the operation that converts it, for the refusal
     * @return the value held
     */
    public static BigDecimal of(final Number value, final String operation) {
        if (value instanceof BigDecimal) {
            return held((BigDecimal) value);
        }
        if (value instanceof Double || value instanceof Float) {
            final double approximate = value.doubleValue();
            if (Double.isNaN(approximate) || Double.isInfinite(approximate)) {
                throw new RuntimeException("Decfloat out of representable range, operation: " + operation + "("
                    + (Double.isNaN(approximate) ? "nan" : approximate > 0 ? "inf" : "-inf") + ")");
            }
            return held(new BigDecimal(approximate));
        }
        return held(new BigDecimal(value.toString()));
    }

    /** The significant digits a FLOAT written into a DECFLOAT column keeps. */
    private static final MathContext WRITTEN_FLOAT = new MathContext(18, RoundingMode.HALF_UP);

    /**
     * A number as a DECFLOAT column stores it: as {@link #of} holds it, except that a FLOAT keeps 18 significant
     * digits of its binary value — INSERT, UPDATE and MERGE write 0.1::FLOAT as 0.100000000000000006 and 1e38::FLOAT
     * as 99999999999999997700000000000000000000, where a cast keeps every digit (live-verified).
     *
     * @param value     the number
     * @param operation the operation that converts it, for the refusal
     * @return the value stored
     */
    public static BigDecimal written(final Number value, final String operation) {
        if ((value instanceof Double || value instanceof Float) && Double.isFinite(value.doubleValue())) {
            return held(new BigDecimal(value.doubleValue()).round(WRITTEN_FLOAT));
        }
        return of(value, operation);
    }

    /**
     * Whether a value is one a DECFLOAT computes with exactly: a BigDecimal or a whole number.
     *
     * @param value the value
     * @return whether it is exact
     */
    public static boolean isExact(final Object value) {
        return value instanceof BigDecimal || value instanceof Long || value instanceof Integer
            || value instanceof Short || value instanceof Byte || value instanceof BigInteger;
    }

    /**
     * Whether a decimal text spells a value DECFLOAT's range holds.
     *
     * @param text the text, a decimal number
     * @return false past the range
     */
    public static boolean holds(final String text) {
        try {
            held(new BigDecimal(text.trim()));
            return true;
        } catch (final DecfloatRangeException outOfRange) {
            return false;
        }
    }

    /**
     * The sum of two DECFLOATs, held.
     *
     * @param left  the first
     * @param right the second
     * @return the sum
     */
    public static BigDecimal add(final BigDecimal left, final BigDecimal right) {
        try {
            return held(left.add(right, CONTEXT));
        } catch (final DecfloatRangeException outOfRange) {
            throw DecfloatRangeException.naming(text(left) + " + " + text(right));
        }
    }

    /**
     * The difference of two DECFLOATs, held.
     *
     * @param left  the first
     * @param right the second
     * @return the difference
     */
    public static BigDecimal subtract(final BigDecimal left, final BigDecimal right) {
        try {
            return held(left.subtract(right, CONTEXT));
        } catch (final DecfloatRangeException outOfRange) {
            throw DecfloatRangeException.naming(text(left) + " - " + text(right));
        }
    }

    /**
     * The product of two DECFLOATs, held.
     *
     * @param left  the first
     * @param right the second
     * @return the product
     */
    public static BigDecimal multiply(final BigDecimal left, final BigDecimal right) {
        try {
            return held(left.multiply(right, CONTEXT));
        } catch (final DecfloatRangeException outOfRange) {
            throw DecfloatRangeException.naming(text(left) + " * " + text(right));
        }
    }

    /**
     * The quotient of two DECFLOATs to 38 significant digits; a zero divisor is refused, {@code Division by zero}.
     *
     * @param left  the dividend
     * @param right the divisor
     * @return the quotient
     */
    public static BigDecimal divide(final BigDecimal left, final BigDecimal right) {
        if (right.signum() == 0) {
            throw new RuntimeException("Division by zero");
        }
        try {
            return held(left.divide(right, CONTEXT));
        } catch (final DecfloatRangeException outOfRange) {
            throw DecfloatRangeException.naming(text(left) + " / " + text(right));
        }
    }

    /**
     * A DECFLOAT's text, as a cast to VARCHAR writes it: plain when the plain form needs at most 38 digits, the 0
     * before the point of a value under one counted, and otherwise every significant digit in scientific form with a
     * bare exponent.
     *
     * @param value the value, held
     * @return its text
     */
    public static String text(final BigDecimal value) {
        final BigDecimal held = held(value);
        if (held.signum() == 0) {
            return "0";
        }
        final int precision = held.precision();
        final int scale = held.scale();
        final int plainDigits = scale <= 0 ? precision - scale : precision > scale ? precision : 1 + scale;
        if (plainDigits <= DIGITS) {
            return held.toPlainString();
        }
        final BigDecimal stripped = held.stripTrailingZeros();
        if (stripped.precision() - stripped.scale() - 1 > MAX_EXPONENT) {
            // Past the top exponent the mantissa is written at it: 1e16385 is 10e16384 and 1.2345e16385
            // 12.345e16384 (live-verified).
            return stripped.movePointLeft(MAX_EXPONENT).toPlainString() + "e" + MAX_EXPONENT;
        }
        final String digits = stripped.unscaledValue().abs().toString();
        final int exponent = digits.length() - 1 - stripped.scale();
        final StringBuilder text = new StringBuilder(stripped.signum() < 0 ? "-" : "");
        text.append(digits.charAt(0));
        if (digits.length() > 1) {
            text.append('.').append(digits, 1, digits.length());
        }
        return text.append('e').append(exponent).toString();
    }
}
