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
 * A number as a conversion reads it from text: ASCII digits alone. Java's {@code BigDecimal} and
 * {@code Character.digit} read any Unicode decimal digit - Arabic-Indic, fullwidth, Devanagari - where the
 * account's conversions read the ASCII ones: TO_NUMBER refuses a forty-eight written in Arabic-Indic digits as
 * {@code Numeric value '...' is not recognized}, a cast to a number is that conversion (Snowflake's CAST
 * documentation), and a hexadecimal input is {@code 0-9} and {@code A-F}.
 */
public final class DecimalText {

    /** The last ASCII character. */
    private static final char LAST_ASCII = 0x7F;

    private DecimalText() {
    }

    /**
     * The number a text spells.
     *
     * @param text the text, trimmed as its caller trims it
     * @return the number
     * @throws NumberFormatException when the text spells none, any character outside ASCII included; for one
     *     outside ASCII its message is the conversion's refusal
     */
    public static BigDecimal parse(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > LAST_ASCII) {
                throw new NumberFormatException("Numeric value '" + text + "' is not recognized");
            }
        }
        return new BigDecimal(text);
    }

    /**
     * The value of a hexadecimal digit: {@code 0-9}, {@code a-f} and {@code A-F} alone.
     *
     * @param c the character
     * @return its value, or -1 when it is no ASCII hexadecimal digit
     */
    public static int hexDigit(final char c) {
        return c <= LAST_ASCII ? Character.digit(c, 16) : -1;
    }
}
