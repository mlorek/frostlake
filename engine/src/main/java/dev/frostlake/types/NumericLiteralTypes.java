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

package dev.frostlake.types;

import java.math.BigDecimal;

/**
 * The NUMBER(precision, scale) Snowflake gives a numeric LITERAL.
 *
 * <p>Measured on a real account by declaring each literal into a table and reading
 * INFORMATION_SCHEMA back: {@code 1.5} and {@code 0.5} are NUMBER(2,1), {@code 0.05} NUMBER(3,2),
 * {@code 100.25} NUMBER(5,2), {@code 1} NUMBER(1,0), {@code 100} NUMBER(3,0),
 * {@code 0.0000000001} NUMBER(11,10) and {@code 12345678901234567890} NUMBER(20,0). Trailing zeros do
 * not count — {@code 1.50} is NUMBER(2,1) and {@code 0.000} is NUMBER(1,0), storing plain {@code 0} —
 * and an exponent is folded in before measuring, so {@code 1.5e2} is NUMBER(3,0) holding 150. A leading
 * {@code .} behaves as a zero integer part: {@code .5} is NUMBER(2,1). The sign is not a digit:
 * {@code -1.5} is NUMBER(2,1).
 *
 * <p>So: normalise, take the scale (never negative), and add the integer digits with a floor of one —
 * a value below 1 still reports the leading zero that {@code 0.05 -> NUMBER(3,2)} shows.
 *
 * <p>This matters beyond metadata. The inferred type becomes a derived relation's real column type, so a
 * scale-0 NUMBER there does not merely misreport {@code 0.05} — it STORES it as {@code 0}.
 */
public final class NumericLiteralTypes {

    /** Snowflake's maximum NUMBER precision; a wider literal is rejected before it reaches us. */
    private static final int MAX_PRECISION = 38;

    private NumericLiteralTypes() {
    }

    /** The NUMBER type of a numeric literal value, or null when it is not one we can measure. */
    public static NumericType of(final Object value) {
        final BigDecimal decimal;
        if (value instanceof BigDecimal) {
            decimal = (BigDecimal) value;
        } else if (value instanceof Long || value instanceof Integer || value instanceof Short
                || value instanceof Byte) {
            decimal = BigDecimal.valueOf(((Number) value).longValue());
        } else if (value instanceof Double || value instanceof Float) {
            // A literal past the exact range is carried as a double, and answers FLOAT the way every
            // other approximate value does — see exceedsExactRange.
            return NumericType.FLOAT;
        } else {
            return null;
        }
        return forDecimal(decimal);
    }

    /**
     * The exact value a numeric literal's SOURCE TEXT stands for, in plain notation.
     *
     * <p>An exponent is a way of WRITING a fixed-point number, not a floating-point marker: live prints
     * {@code 1e20} back as 100000000000000000000 and {@code 1e-3} as 0.001. {@code BigDecimal} keeps a
     * negative scale for the first of those and would render it as {@code 1E+20}, so the scale is
     * raised to zero — the value is unchanged and only its spelling becomes the one live uses.
     *
     * @param text the literal exactly as written
     * @return its value
     */
    public static BigDecimal exactValue(final String text) {
        // TRAILING ZEROS ARE NOT PART OF THE VALUE: live types 1.00 as NUMBER(1,0), spells it that way
        // in an argument-type refusal, and renders it back as 1 — the literal is normalised before
        // anything reads it, and 0.10 keeps only the decimal that means something (NUMBER(2,1)).
        // A negative scale from the strip (100.00 → 1E+2) is put back to zero, as an exponentless
        // spelling of the same value.
        final BigDecimal stripped = new BigDecimal(text).stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /** The NUMBER type of an exact decimal value. */
    public static NumericType forDecimal(final BigDecimal value) {
        final BigDecimal normalized = value.stripTrailingZeros();
        final int scale = Math.max(normalized.scale(), 0);
        final int precision = Math.min(digitsOf(normalized), MAX_PRECISION);
        return new NumericType("NUMBER", precision, scale);
    }

    /**
     * Whether an exact value needs MORE digits than a NUMBER can hold, which is what takes a literal
     * out of the fixed-point family altogether: live answers {@code 1e37} as NUMBER(38,0) and
     * {@code 1e38} — one digit wider — as a DOUBLE, and does the same for the point spelling at
     * thirty-nine digits. The notation decides nothing; only the magnitude does.
     *
     * <p>Trailing zeros are stripped first, for the reason {@link #forDecimal} strips them: a literal
     * written {@code 99…9.0} with thirty-eight nines is a thirty-eight digit number, and counting its
     * dropped {@code .0} would push it over a boundary live keeps it inside.
     *
     * @param value the literal's exact value
     * @return whether it is past the exact range
     */
    public static boolean exceedsExactRange(final BigDecimal value) {
        return digitsOf(value.stripTrailingZeros()) > MAX_PRECISION;
    }

    /**
     * The digits a normalised value needs — its integer digits plus its scale.
     *
     * <p>{@code precision()} counts the unscaled digits, so subtracting the scale leaves the integer
     * digits — which is zero or negative for a value below 1, hence the floor of one. A NEGATIVE scale
     * (what {@code stripTrailingZeros} leaves for {@code 1e20}) widens the integer part by that much
     * and contributes no decimals of its own.
     *
     * @param normalized the value with its trailing zeros stripped
     * @return how many digits it needs
     */
    private static int digitsOf(final BigDecimal normalized) {
        return Math.max(normalized.precision() - normalized.scale(), 1)
            + Math.max(normalized.scale(), 0);
    }
}
