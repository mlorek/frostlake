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

package dev.frostlake.executor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * The sentence a number too wide for its destination gets. Live spells the INTERNAL type rather than the
 * SQL one, and the three source families get three different sentences — measured, not derived:
 *
 * <pre>
 *   CAST(1000 AS NUMBER(2,0))               Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000
 *   CAST(1 AS NUMBER(0,0))                  Number out of representable range: type FIXED[SB1]{not null}, value 1
 *   CAST(PARSE_JSON('1000') AS NUMBER(2,0)) Number out of representable range: type FIXED, value 1000
 *   CAST('1000' AS NUMBER(2,0))             Numeric value '1000' is out of range
 * </pre>
 *
 * <p>★ THE WIDTH IS OMITTED AT PRECISION ZERO and printed everywhere else — NUMBER(0,0) reads
 * {@code FIXED[SB1]{not null}} with no parentheses at all. A VARIANT source drops the class AND the width,
 * keeping the bare family name.
 *
 * <p>★ THE NULLABILITY IS PART OF THE TYPE, and it is read from where the number was GOING or coming
 * FROM rather than from the number: a literal is {@code not null}, a nullable column {@code nullable}.
 *
 * <p>★ THE CLASS FOLLOWS THE VALUE ON EVERY PATH BUT ONE. A cast, an {@code INSERT … SELECT}, an UPDATE
 * and a MERGE all tag the VALUE that would not fit ({@link SignedStorageWidth#tagOf}); only
 * {@code INSERT … VALUES} tags the COLUMN's own slot ({@link SignedStorageWidth#tagOfPrecision}), which
 * is what makes the two spellings of one write disagree: a 10^11 written into a NUMBER(2,0) reads SB1
 * through VALUES and SB8 through SET. An approximate (FLOAT) source tags the slot too — there is no
 * unscaled integer to measure.
 *
 * <p>The whole family is ROW-TIME: over an empty table the same conversion refuses nothing, on both
 * engines, so none of these sentences carries a compilation prefix or a position.
 */
public final class NumericRangeRefusal {

    private static final String LEAD = "Number out of representable range: type FIXED";

    private NumericRangeRefusal() {
    }

    /** The full sentence: storage class, declared width, nullability, value. */
    public static String typed(final String storageTag, final int precision, final int scale,
                               final boolean nullable, final BigDecimal value) {
        return typedText(storageTag, precision, scale, nullable, valueText(value));
    }

    /**
     * The typed sentence with the value's text supplied by the caller — for the refusals whose value
     * live prints at its own scale, trailing zeros and all, rather than stripped.
     *
     * @param storageTag the SBn tag
     * @param precision  the declared precision, or 0 for an untyped carrier
     * @param scale      the declared scale
     * @param nullable   whether the carrier is nullable
     * @param valueText  the value exactly as the sentence prints it
     * @return the sentence
     */
    public static String typedText(final String storageTag, final int precision, final int scale,
                                   final boolean nullable, final String valueText) {
        return LEAD + "[" + storageTag + "]"
            + (precision == 0 ? "" : "(" + precision + "," + scale + ")")
            + "{" + (nullable ? "nullable" : "not null") + "}, value " + valueText;
    }

    /**
     * The sentence with the value printed as a DOUBLE in live's six-significant-digit scientific form —
     * the render every raw-integer and quotient overflow uses, even when the digits would fit the
     * carrier (live-verified: a quotient of 1.11…e38 prints {@code 1.11111e+38}, not its digits).
     */
    public static String typedDouble(final String storageTag, final int precision, final int scale,
                                     final boolean nullable, final BigDecimal value) {
        return LEAD + "[" + storageTag + "]"
            + (precision == 0 ? "" : "(" + precision + "," + scale + ")")
            + "{" + (nullable ? "nullable" : "not null") + "}, value " + gText(value.doubleValue());
    }

    /** The signed 128-bit window every exact arithmetic raw must fit: [-2^127, 2^127 - 1], inclusive. */
    private static final BigInteger RAW_MAX = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
    private static final BigInteger RAW_MIN = BigInteger.ONE.shiftLeft(127).negate();

    /**
     * Whether a raw scaled integer is outside the signed 128-bit carrier. The bound is two's-complement
     * ASYMMETRIC on purpose: -2^127 itself is representable and live answers it (live-verified), while
     * +2^127 is the first refusal on the positive side.
     */
    public static boolean outsideSb16Window(final BigInteger raw) {
        return raw.compareTo(RAW_MAX) > 0 || raw.compareTo(RAW_MIN) < 0;
    }

    /**
     * A double in live's overflow-value spelling: six significant digits, trailing zeros dropped,
     * lowercase {@code e} with a signed exponent — {@code 3e+38}, {@code 1.5e+39}, {@code 1.70141e+38}.
     */
    public static String gText(final double value) {
        final String formatted = String.format(Locale.US, "%.6g", value);
        final int e = formatted.indexOf('e');
        if (e < 0) {
            return formatted;
        }
        String mantissa = formatted.substring(0, e);
        if (mantissa.indexOf('.') >= 0) {
            int end = mantissa.length();
            while (end > 0 && mantissa.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && mantissa.charAt(end - 1) == '.') {
                end--;
            }
            mantissa = mantissa.substring(0, end);
        }
        return mantissa + formatted.substring(e);
    }

    /** The sentence a VARIANT source gets: the family alone, with neither class nor width. */
    public static String untyped(final BigDecimal value) {
        return LEAD + ", value " + valueText(value);
    }

    /** The digits the widest NUMBER holds; past this a text is not a number at all. */
    private static final int MAX_PRECISION = 38;

    /** The sentence a STRING source gets — a different family, naming the text rather than the type. */
    public static String unconvertibleText(final Object text) {
        return "Numeric value '" + text + "' is out of range";
    }

    /**
     * The sentence a STRING earns when its value needs more digits than ANY number holds, which live
     * treats as a failure to READ the text rather than a complaint about where it was going:
     *
     * <pre>
     *   '123456789012'::NUMBER(10,0)   is out of range      fits a NUMBER, not THIS one
     *   '999…9'(39)::NUMBER(10,0)      is not recognized    fits no NUMBER at all
     *   '999…9'(39)::NUMBER(38,0)      is not recognized    …the target makes no difference
     * </pre>
     *
     * <p>The text is echoed exactly as written — live keeps its leading zeros, its sign and its
     * fraction — and the TRY_ spellings answer NULL for both sentences.
     *
     * @param text the source text
     * @return the sentence
     */
    public static String unreadableText(final Object text) {
        return "Numeric value '" + text + "' is not recognized";
    }

    /**
     * Whether a value is past what EVERY exact number can hold, taken at {@code scale} the way
     * {@link #exceeds} takes it — so a string of decimals that merely rounds away stays readable
     * ({@code '0.999…9'} with thirty-nine decimals is 1, not an overflow).
     *
     * @param value the parsed value
     * @param scale the scale it is being read at
     * @return whether no NUMBER could hold it
     */
    public static boolean pastEveryNumber(final BigDecimal value, final int scale) {
        return exceeds(value, MAX_PRECISION, scale);
    }

    /**
     * Whether {@code value} needs more digits left of the point than a NUMBER({@code precision},
     * {@code scale}) has room for. The value is taken at the TARGET's scale first, so a number that
     * merely carries extra fractional digits rounds into range instead of overflowing.
     */
    public static boolean exceeds(final BigDecimal value, final int precision, final int scale) {
        final BigDecimal atScale = value.scale() == scale ? value : value.setScale(scale, RoundingMode.HALF_UP);
        return atScale.precision() - atScale.scale() > precision - scale;
    }

    /**
     * The value as live prints it: its own digits with trailing zeros dropped, so a 1000.00 that failed a
     * NUMBER(2,0) reads {@code 1000} while a 12.399 that failed a NUMBER(3,2) keeps all of {@code 12.399}
     * — the number as WRITTEN, not as the destination would have rounded it.
     */
    public static String valueText(final BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
