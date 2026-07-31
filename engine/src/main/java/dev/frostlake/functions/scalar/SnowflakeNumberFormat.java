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
 * Formats a numeric value with Snowflake's SQL number format model (used by TO_CHAR / TO_VARCHAR
 * with a numeric format string), matching the live-verified output shape: the result is
 * RIGHT-ALIGNED to the template width plus one leading sign position (blank for positive, a
 * floating {@code -} for negative, {@code +}/{@code -} with a leading {@code S}); {@code MI} moves
 * the sign to a trailing position ({@code -} or blank) with no leading sign slot; a {@code $}
 * floats between the sign and the digits; {@code 0} placeholders zero-pad; trailing fractional
 * {@code 9} positions render insignificant zeros as spaces ({@code 0.5} with {@code '99.99'} is
 * {@code "  0.5 "}); a value wider than the integer template renders {@code #} across the template
 * width; {@code FM} strips all of that padding. Rounding is HALF_UP to the fractional placeholders.
 * The {@code PR} keyword is NOT part of Snowflake's model and raises the same error Snowflake
 * raises (live-verified). Not modeled: {@code EEEE} scientific notation and locale currency codes.
 */
public final class SnowflakeNumberFormat {

    private SnowflakeNumberFormat() {
    }

    public static String format(final BigDecimal rawValue, final String rawFormat) {
        final String upper = rawFormat.toUpperCase(Locale.ROOT);
        if (upper.contains("PR")) {
            // Live-verified: Snowflake does not support Oracle's PR (angle-bracket) element.
            throw new RuntimeException("Bad output format model '" + rawFormat
                + "' for FIXED: invalid numeric format keyword: 'PR'");
        }
        final boolean fillMode = upper.contains("FM");
        final boolean trailingMinus = upper.contains("MI");
        final boolean hasDollar = rawFormat.indexOf('$') >= 0;

        // Reduce to the bare digit template (0 9 , .): resolve D/G, drop the flag elements.
        String template = upper.replace("FM", "").replace("MI", "").replace("$", "");
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
        final String fracTemplate = dot < 0 ? "" : template.substring(dot + 1);
        final int decimals = countChars(fracTemplate, '0', '9');
        final String intPart = dot < 0 ? template : template.substring(0, dot);
        final boolean grouping = intPart.indexOf(',') >= 0;
        final int intDigitPositions = countChars(intPart, '0', '9');
        final int minIntDigits = zeroPadWidth(intPart);

        final BigDecimal rounded = rawValue.setScale(decimals, RoundingMode.HALF_UP);
        final boolean negative = rounded.signum() < 0;

        // Overflow: more integer digits than the template holds renders '#' across the template.
        if (rounded.abs().setScale(0, RoundingMode.DOWN).precision() > intDigitPositions
            && rounded.abs().compareTo(BigDecimal.ONE) >= 0) {
            final StringBuilder hashes = new StringBuilder();
            for (int i = 0; i < template.length(); i++) {
                hashes.append('#');
            }
            return fillMode ? hashes.toString() : pad(hashes.toString(), width(template, hasDollar, trailingMinus));
        }

        final DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        df.setGroupingUsed(grouping);
        if (grouping) {
            df.setGroupingSize(3);
        }
        df.setMinimumFractionDigits(decimals);
        df.setMaximumFractionDigits(decimals);
        df.setMinimumIntegerDigits(Math.max(1, minIntDigits));
        df.setRoundingMode(RoundingMode.HALF_UP);

        String core = df.format(rounded.abs());
        core = blankInsignificantFraction(core, fracTemplate, decimals);
        if (hasDollar) {
            core = "$" + core;
        }

        final String signed;
        final String trailer;
        if (trailingMinus) {
            signed = core;
            trailer = negative ? "-" : (fillMode ? "" : " ");
        } else if (leadingSign) {
            signed = (negative ? "-" : "+") + core;
            trailer = "";
        } else if (trailingSign) {
            signed = core;
            trailer = negative ? "-" : "+";
        } else {
            signed = (negative ? "-" : "") + core;
            trailer = "";
        }
        if (fillMode) {
            return (signed + trailer).trim();
        }
        return pad(signed, width(template, hasDollar, trailingMinus)) + trailer;
    }

    /** Template width plus the floating $ and, unless MI claims it, the one leading sign position. */
    private static int width(final String template, final boolean hasDollar, final boolean trailingMinus) {
        return template.length() + (hasDollar ? 1 : 0) + (trailingMinus ? 0 : 1);
    }

    private static String pad(final String s, final int width) {
        final StringBuilder out = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            out.append(' ');
        }
        return out.append(s).toString();
    }

    /**
     * Trailing insignificant fractional zeros in '9' positions render as SPACES (live-verified:
     * {@code TO_CHAR(0.5,'99.99')} is {@code "  0.5 "}); '0' positions always keep the digit.
     */
    private static String blankInsignificantFraction(final String core, final String fracTemplate, final int decimals) {
        if (decimals == 0) {
            return core;
        }
        final char[] out = core.toCharArray();
        int templatePos = decimals - 1;
        for (int i = out.length - 1; i >= 0 && templatePos >= 0; i--, templatePos--) {
            if (out[i] == '0' && fracTemplate.charAt(templatePos) == '9') {
                out[i] = ' ';
            } else {
                break;
            }
        }
        return new String(out);
    }

    /** Digit positions from the first '0' onward — the zero-padded width ('0999' pads to 4 digits). */
    private static int zeroPadWidth(final String intPart) {
        final int firstZero = intPart.indexOf('0');
        if (firstZero < 0) {
            return 1;
        }
        return countChars(intPart.substring(firstZero), '0', '9');
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
