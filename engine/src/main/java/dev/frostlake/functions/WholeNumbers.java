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

package dev.frostlake.functions;

import dev.frostlake.executor.expressions.ImpliedTextNumber;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The number an argument gives where a function reads it as a NUMBER - a position, a length, a count, an index, an
 * occurrence, a code point, a flag BOOLAND tests - read once for every such argument (live-verified): a text is read
 * as the number it spells and refused on the row where it spells none ({@code Numeric value 'x' is not recognized},
 * the text trimmed); a VARIANT gives the number it holds, 1 or 0 for a boolean, or the number its text spells, and
 * is refused as its cast otherwise ({@code Failed to cast variant value "x" to FIXED}); and a whole number is the
 * nearest one, a half away from zero - {@code REGEXP_INSTR('ab', '.', 1.6)} starts at the second character and
 * {@code CHAR('66.6')} is {@code C}.
 */
public final class WholeNumbers {

    private WholeNumbers() {
    }

    /**
     * The whole number an argument gives, held to the range of an int.
     *
     * @param value the argument's value, a number, a text or a VARIANT
     * @return the nearest whole number, a half away from zero
     */
    public static int wholeNumber(final Object value) {
        return wholeNumber(decimalOf(value));
    }

    /**
     * @param value a number
     * @return the nearest whole number, a half away from zero, held to the range of an int
     */
    public static int wholeNumber(final BigDecimal value) {
        final BigInteger rounded = value.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        if (rounded.bitLength() > Integer.SIZE - 1) {
            return rounded.signum() < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
        return rounded.intValue();
    }

    /**
     * The whole number an argument gives, held to the range of a long.
     *
     * @param value the argument's value, a number, a text or a VARIANT
     * @return the nearest whole number, a half away from zero
     */
    public static long wholeLong(final Object value) {
        final BigInteger rounded = decimalOf(value).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        if (rounded.bitLength() > Long.SIZE - 1) {
            return rounded.signum() < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return rounded.longValue();
    }

    /**
     * The number an argument gives, before it is rounded.
     *
     * @param value the argument's value, a number, a text or a VARIANT
     * @return the number
     */
    public static BigDecimal decimalOf(final Object value) {
        if (value instanceof CharSequence) {
            return ImpliedTextNumber.read((CharSequence) value);
        }
        if (value instanceof VariantValue) {
            // A VARIANT gives the number it holds, or the one its text spells; one that spells none is refused as
            // the VARIANT's cast: "Failed to cast variant value "abc" to FIXED" (live-verified).
            final JsonNode held = ((VariantValue) value).node();
            if (held.isNumber()) {
                return held.decimalValue();
            }
            // A BOOLEAN reads as 1 or 0, and an array or an object as nothing at all (live-verified).
            if (held.isBoolean()) {
                return held.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            if (held.isContainer()) {
                throw new RuntimeException("Failed to cast variant value " + held + " to FIXED");
            }
            try {
                return ImpliedTextNumber.read(held.asText());
            } catch (final RuntimeException spellsNoNumber) {
                if (held.isTextual()) {
                    throw new RuntimeException("Failed to cast variant value " + held + " to FIXED");
                }
                throw spellsNoNumber;
            }
        }
        if (value instanceof Boolean) {
            // A BOOLEAN wrapped as a VARIANT constant arrives as itself: TRUE::VARIANT reads as 1.
            return ((Boolean) value).booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof BigInteger) {
            return new BigDecimal((BigInteger) value);
        }
        if (value instanceof Double || value instanceof Float) {
            final double approximate = ((Number) value).doubleValue();
            return Double.isNaN(approximate) || Double.isInfinite(approximate) ? BigDecimal.ZERO
                : BigDecimal.valueOf(approximate);
        }
        return BigDecimal.valueOf(((Number) value).longValue());
    }
}
