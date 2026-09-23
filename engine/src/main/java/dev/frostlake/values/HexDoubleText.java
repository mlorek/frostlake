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

/**
 * A hexadecimal number written as text with no binary exponent, read as a DOUBLE. Java reads a hexadecimal
 * floating-point number only with its exponent ({@code 0x10p0}); live reads one without it as though
 * {@code p0} followed: {@code '0x10'} is 16, {@code '0X1A'} 26, {@code '0x1.8'} 1.5, {@code '0x.8'} 0.5 and
 * {@code '0x10f'} 271, its f a hexadecimal digit, while {@code '0x'}, {@code '0xg'}, {@code '00x10'} and
 * {@code '0x_10'} spell no number. A cast and TO_DOUBLE take a leading sign ({@code '-0x10'} is -16), and
 * TRY_TO_DOUBLE and TRY_CAST do not: there a signed hexadecimal number with no exponent is NULL, though one
 * with its exponent reads (all live-verified).
 */
public final class HexDoubleText {

    private HexDoubleText() {
    }

    /**
     * The double a hexadecimal number with no binary exponent spells, or null where the text is not one.
     *
     * @param trimmed the text, trimmed
     * @param signed  whether a leading sign is read
     * @return the double, or null
     */
    public static Double withoutExponent(final String trimmed, final boolean signed) {
        int start = 0;
        if (signed && !trimmed.isEmpty() && (trimmed.charAt(0) == '+' || trimmed.charAt(0) == '-')) {
            start = 1;
        }
        if (trimmed.length() < start + 3 || trimmed.charAt(start) != '0'
                || (trimmed.charAt(start + 1) != 'x' && trimmed.charAt(start + 1) != 'X')) {
            return null;
        }
        boolean digits = false;
        boolean point = false;
        for (int i = start + 2; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            if (c == '.' && !point) {
                point = true;
            } else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                digits = true;
            } else {
                return null;
            }
        }
        return digits ? Double.valueOf(Double.parseDouble(trimmed + "p0")) : null;
    }

    /**
     * Whether a text is a SIGNED hexadecimal number with no binary exponent, which only the non-TRY
     * readers take.
     *
     * @param trimmed the text, trimmed
     * @return whether it is one
     */
    public static boolean isSignedWithoutExponent(final String trimmed) {
        return withoutExponent(trimmed, false) == null && withoutExponent(trimmed, true) != null;
    }
}
