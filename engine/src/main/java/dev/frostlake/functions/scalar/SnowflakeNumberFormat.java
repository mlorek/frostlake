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
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats a numeric value with a subset of Snowflake's SQL number format model (used by TO_CHAR /
 * TO_VARCHAR with a numeric format string). Supported elements: the digit placeholders {@code 0} and
 * {@code 9}, the group separator {@code ,} (or {@code G}) and decimal point {@code .} (or {@code D}),
 * a leading {@code $}, the sign controls {@code S} / {@code MI} / {@code PR}, and {@code FM} (fill
 * mode). Rounding is HALF_AWAY_FROM_ZERO to the number of fractional placeholders.
 *
 * <p>Not modeled (documented approximations): leading-space padding for {@code 9} (the result is
 * left-trimmed instead), {@code #} overflow when a value exceeds the template width, {@code EEEE}
 * scientific notation, and non-{@code $} currency / locale symbols.
 */
public final class SnowflakeNumberFormat {

    private SnowflakeNumberFormat() {
    }

    public static String format(final BigDecimal rawValue, final String rawFormat) {
        final String upper = rawFormat.toUpperCase(Locale.ROOT);
        final boolean fillMode = upper.contains("FM");
        final boolean brackets = upper.contains("PR");
        final boolean trailingMinus = upper.contains("MI");
        final boolean hasDollar = rawFormat.indexOf('$') >= 0;

        // Reduce to the bare digit template (0 9 , .): resolve D/G, drop the flag elements.
        String template = upper.replace("FM", "").replace("PR", "").replace("MI", "").replace("$", "");
        boolean leadingSign = false;
        boolean trailingSign = false;
        final int signIdx = template.indexOf('S');
        if (signIdx >= 0) {
            final int firstDigit = firstDigitIndex(template);
            leadingSign = firstDigit < 0 || signIdx < firstDigit;
            trailingSign = !leadingSign;
            template = template.replace("S", "");
        }
        template = template.replace('D', '.').replace('G', ',');

        final int dot = template.indexOf('.');
        final int decimals = dot < 0 ? 0 : countChars(template.substring(dot + 1), '0', '9');
        final String intPart = dot < 0 ? template : template.substring(0, dot);
        final boolean grouping = intPart.indexOf(',') >= 0;
        final int minIntDigits = Math.max(1, countChars(intPart, '0'));

        final BigDecimal rounded = rawValue.setScale(decimals, RoundingMode.HALF_UP);
        final boolean negative = rounded.signum() < 0;

        final DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        df.setGroupingUsed(grouping);
        if (grouping) {
            df.setGroupingSize(3);
        }
        df.setMinimumFractionDigits(decimals);
        df.setMaximumFractionDigits(decimals);
        df.setMinimumIntegerDigits(minIntDigits);
        df.setRoundingMode(RoundingMode.HALF_UP);

        String core = df.format(rounded.abs());
        if (hasDollar) {
            core = "$" + core;
        }

        final String result;
        if (brackets) {
            result = negative ? "<" + core + ">" : (fillMode ? core : " " + core + " ");
        } else if (trailingMinus) {
            result = core + (negative ? "-" : (fillMode ? "" : " "));
        } else if (leadingSign) {
            result = (negative ? "-" : "+") + core;
        } else if (trailingSign) {
            result = core + (negative ? "-" : "+");
        } else if (negative) {
            result = "-" + core;
        } else {
            result = core;
        }
        return fillMode ? result.trim() : result;
    }

    private static int firstDigitIndex(final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '0' || c == '9') {
                return i;
            }
        }
        return -1;
    }

    private static int countChars(final String s, final char... targets) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            for (final char t : targets) {
                if (s.charAt(i) == t) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }
}
