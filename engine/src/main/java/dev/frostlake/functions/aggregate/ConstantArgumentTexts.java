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

package dev.frostlake.functions.aggregate;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reads the constant integer an aggregate call wrote as one of its arguments — the k and the counter
 * limit of the APPROX_TOP_K family — from the argument's text. The compile-time rules have already
 * refused what is not an integral constant, so what remains is the spelling: a bare integer, a
 * parenthesised or plus-signed one, an integral decimal or exponent form ({@code 2.0}, {@code 1e1}),
 * or a quoted number ({@code '2'}); anything else falls back to the caller's default.
 */
public final class ConstantArgumentTexts {

    private ConstantArgumentTexts() {
    }

    /**
     * The integral value of an argument text, or the fallback.
     *
     * @param argumentTexts the call's argument texts, or null
     * @param index which argument
     * @param fallback the value when the argument is absent or not an integral constant
     * @return the integer
     */
    public static int integer(final List<String> argumentTexts, final int index, final int fallback) {
        if (argumentTexts == null || index >= argumentTexts.size()) {
            return fallback;
        }
        final BigDecimal value = integralConstant(argumentTexts.get(index));
        if (value == null) {
            return fallback;
        }
        try {
            return value.intValueExact();
        } catch (final ArithmeticException beyondInt) {
            return fallback;
        }
    }

    /**
     * The integral value a constant text spells, or null when it spells none — a fraction, a
     * non-numeric string, a column or an expression.
     *
     * @param text the argument text
     * @return the value, or null
     */
    public static BigDecimal integralConstant(final String text) {
        final BigDecimal value = numericConstant(text);
        return value != null && value.stripTrailingZeros().scale() <= 0 ? value : null;
    }

    /**
     * The numeric value a constant text spells, quoted or not, or null when it is not a numeric or
     * string literal that reads as a number.
     *
     * @param text the argument text
     * @return the value, or null
     */
    public static BigDecimal numericConstant(final String text) {
        final String bare = unwrap(text);
        if (bare == null) {
            return null;
        }
        final String digits = bare.length() > 1 && bare.charAt(0) == '\'' && bare.charAt(bare.length() - 1) == '\''
            ? bare.substring(1, bare.length() - 1).trim() : bare;
        if (!digits.matches("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")) {
            return null;
        }
        try {
            return new BigDecimal(digits);
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Whether a constant text is a string literal, which live converts through TO_NUMBER.
     *
     * @param text the argument text
     * @return true for a single-quoted literal
     */
    public static boolean isStringLiteral(final String text) {
        final String bare = unwrap(text);
        return bare != null && bare.length() > 1 && bare.charAt(0) == '\'' && bare.charAt(bare.length() - 1) == '\'';
    }

    /**
     * The text without its enclosing parentheses and a leading plus sign — {@code (2)} and {@code +2}
     * are the constant 2 (live-verified).
     *
     * @param text the argument text, or null
     * @return the bare text, trimmed, or null
     */
    public static String unwrap(final String text) {
        if (text == null) {
            return null;
        }
        String bare = text.trim();
        while (bare.length() > 1 && bare.charAt(0) == '(' && bare.charAt(bare.length() - 1) == ')') {
            bare = bare.substring(1, bare.length() - 1).trim();
        }
        if (bare.startsWith("+")) {
            bare = bare.substring(1).trim();
        }
        return bare;
    }
}
