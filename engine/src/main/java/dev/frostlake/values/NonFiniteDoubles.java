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

import java.util.Locale;

/**
 * NaN and the infinities as SQL sees them — how their words are spelled, and how they compare.
 *
 * <p>Live-verified. The words are matched CASE-INSENSITIVELY and a sign may lead them, with one measured
 * asymmetry: in a DOCUMENT, {@code +NaN} is read and {@code -NaN} is not.
 *
 * <p>★ A CAST DOES NOT SHARE THAT ASYMMETRY. Live answers NaN for {@code '-nan'::FLOAT}, so the two
 * readers are deliberately different — {@link #parse} is the document's and {@link #parseForCast} is
 * the cast's — where a single reader would have to refuse a value one of the two accepts.
 *
 * <p>Their ORDER is not IEEE. Snowflake gives NaN a place in the total order rather than leaving it
 * incomparable: it EQUALS ITSELF and sorts ABOVE every other value, so
 * {@code ORDER BY} ends {@code -Infinity, 1, Infinity, NaN}. That is exactly what
 * {@link Double#compare(double, double)} does, which is why comparison routes through it rather than
 * through the {@code ==} a primitive double would use.
 */
public final class NonFiniteDoubles {

    private NonFiniteDoubles() {
    }

    /** The double a non-finite word stands for, or null where the text is not one. */
    public static Double parse(final String raw) {
        if (raw == null) {
            return null;
        }
        final String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        final char first = text.charAt(0);
        final char sign = first == '+' || first == '-' ? first : '\0';
        final String word = (sign == '\0' ? text : text.substring(1)).toLowerCase();
        if (word.equals("nan")) {
            return sign == '-' ? null : Double.valueOf(Double.NaN);
        }
        if (word.equals("inf") || word.equals("infinity")) {
            return Double.valueOf(sign == '-' ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }
        return null;
    }

    /**
     * The same words as {@link #parse}, read the way a CAST reads them — where a NEGATIVE NaN is
     * ACCEPTED.
     *
     * <p>★ THE SIGN RULE IS NOT ONE RULE. In a JSON DOCUMENT it is asymmetric and measured: {@code
     * +NaN} parses and {@code -NaN} is refused. A CAST does not follow that — live answers NaN for
     * {@code '-nan'::FLOAT} as readily as for {@code '+nan'} — so the two readers have to differ,
     * and applying the document's rule to the cast refused a value live accepts.
     *
     * @param raw the text being cast
     * @return the double it stands for, or null where the text is not a non-finite word
     */
    public static Double parseForCast(final String raw) {
        final Double asWritten = parse(raw);
        if (asWritten != null || raw == null) {
            return asWritten;
        }
        final String text = raw.trim();
        return text.length() > 1 && text.charAt(0) == '-'
            && "nan".equals(text.substring(1).toLowerCase(Locale.ROOT))
            ? Double.valueOf(Double.NaN) : null;
    }

    /**
     * Whether this is a non-finite DOUBLE. A BigDecimal too large to fit a double does NOT count — it is an
     * exact number that merely overflows on conversion, and it keeps its exact comparison.
     */
    public static boolean isNonFinite(final Number value) {
        return (value instanceof Double || value instanceof Float) && !Double.isFinite(value.doubleValue());
    }
}
