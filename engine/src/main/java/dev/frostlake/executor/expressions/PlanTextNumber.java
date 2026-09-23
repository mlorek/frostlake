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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.NumericType;

import java.math.BigDecimal;

/**
 * How the account's plan reads a text LITERAL as a number beside an exact one — the width its echoes spell
 * ({@code RT.N * (TO_NUMBER('1.50', 18, 1))}) and the constant its folding carries out
 * ({@code '1.50' * 1} is {@code CAST(1.5 AS NUMBER(19,1))}), live-verified:
 *
 * <ul>
 *   <li>a text that spells a number with nothing about it — a sign, digits, a point, an exponent — reads as the
 *       number it spells, eighteen digits wide, or as wide as its whole digits need, at the scale of its value:
 *       {@code '1.50'}, {@code '01.10'} and {@code '15e-1'} are NUMBER(18,1), {@code '1.0'}, {@code '1e2'} and
 *       {@code '1.5e1'} NUMBER(18,0), {@code '1e20'} NUMBER(21,0);</li>
 *   <li>any other text — a blank about it ({@code ' 1 '}) or no number at all ({@code 'abc'}) — reads as a text
 *       VALUE does, NUMBER(18,5): its number, where it has one, is rounded to five decimals
 *       ({@code ' 1.123456 '} is 1.12346), and one past thirteen whole digits has none.</li>
 * </ul>
 */
final class PlanTextNumber {

    private static final int DIGITS = 18;
    private static final int MAX_PRECISION = 38;
    private static final int MAX_SCALE = 37;

    private PlanTextNumber() {
    }

    /**
     * The number a text spells with nothing about it, or null for any other text.
     *
     * @param text the literal's text
     * @return the exact number, or null
     */
    static BigDecimal spelled(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        final BigDecimal number;
        try {
            number = new BigDecimal(text);
        } catch (final NumberFormatException notSpelled) {
            return null;
        }
        final BigDecimal stripped = number.signum() == 0 ? BigDecimal.ZERO : number.stripTrailingZeros();
        return stripped.scale() > MAX_SCALE || stripped.precision() - stripped.scale() > MAX_PRECISION
            ? null : stripped;
    }

    /**
     * The width the text reads at.
     *
     * @param text the literal's text
     * @return NUMBER(18, s) or wider for a spelled number, NUMBER(18,5) for any other text
     */
    static NumericType type(final String text) {
        final BigDecimal number = spelled(text);
        if (number == null) {
            return ImpliedTextNumber.COLUMN_READING;
        }
        final int scale = Math.max(0, number.scale());
        final int whole = Math.max(0, number.precision() - number.scale());
        return new NumericType("NUMBER", Math.min(MAX_PRECISION, Math.max(DIGITS, whole + scale)), scale);
    }

    /**
     * The number the text reads as, at the width {@link #type} gives it.
     *
     * @param text the literal's text
     * @return the number, or null where the text has none at that width
     */
    static BigDecimal value(final String text) {
        final BigDecimal number = spelled(text);
        if (number != null) {
            return number;
        }
        try {
            return ImpliedTextNumber.read(text);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }
}
