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

import dev.frostlake.values.ValueRange;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * The {@code SBn} tag Snowflake prints for a fixed-point number — the smallest SIGNED-INTEGER width, in
 * bytes, that holds the number's unscaled value. Two live surfaces spell it and they agree, which is why
 * the rule lives in one place: {@code SYSTEM$TYPEOF} tags the value it is handed, and the
 * out-of-representable-range refusal tags either the value it could not store or the column it could not
 * store it in.
 *
 * <p>★ THE BOUNDARIES ARE THE JAVA ONES, measured rather than assumed: 127 is SB1 and 128 is SB2, 32767
 * is SB2 and 32768 is SB4, and the ladder continues at {@code Integer.MAX_VALUE} and {@code Long.MAX_VALUE}.
 * A DIGIT-counting rule looks equivalent and is not — a NUMBER(2,0) is SB1 because 99 fits in a byte, so
 * a "two digits means the two-byte class" reading is wrong at the first cell it meets. The bounds are
 * SIGNED, so -128 is still SB1 where a magnitude rule would say SB2 (live-verified).
 *
 * <p>★ THE SCALE SHIFTS THE VALUE BEFORE THE TAG IS READ, so the tag counts the digits STORAGE holds
 * rather than the ones the number is written with: 12.39 in a NUMBER(2,1) is 124 (SB1), not 1239 (SB2).
 */
public final class SignedStorageWidth {

    private SignedStorageWidth() {
    }

    /** The tag for {@code value} once shifted to {@code scale}. */
    public static String tagOf(final Object value, final int scale) {
        return "SB" + signedWidthBytes(value, scale);
    }

    /**
     * The tag for every value of an interval once shifted to {@code scale}: the wider of its two ends,
     * which is how the account tags a column — by its statistics, never by the row in hand. An empty
     * interval takes the narrowest tag, like a NULL.
     */
    public static String tagOfRange(final ValueRange range, final int scale) {
        if (range == null || range.isEmpty()) {
            return "SB1";
        }
        return "SB" + Math.max(signedWidthBytes(range.getMin(), scale),
            signedWidthBytes(range.getMax(), scale));
    }

    /**
     * The tag for the WIDEST value a NUMBER({@code precision}, s) can hold — the class of the column
     * itself rather than of anything written to it. The scale drops out: a NUMBER(5,2) holds 999.99,
     * whose unscaled 99999 is the same quantity a NUMBER(5,0) holds.
     */
    public static String tagOfPrecision(final int precision) {
        return "SB" + signedWidthBytes(BigInteger.TEN.pow(Math.max(precision, 0))
            .subtract(BigInteger.ONE), 0);
    }

    /**
     * The smallest signed-integer width, in bytes, that holds {@code value} once shifted to
     * {@code scale} — the quantity the tag counts. A value that cannot be read as a number, NULL
     * included, takes the narrowest width, which is what live answers for a NULL-valued numeric.
     */
    private static int signedWidthBytes(final Object value, final int scale) {
        final BigInteger unscaled = unscaledOf(value, scale);
        if (unscaled == null) {
            return 1;
        }
        if (fitsSigned(unscaled, Byte.MIN_VALUE, Byte.MAX_VALUE)) {
            return 1;
        }
        if (fitsSigned(unscaled, Short.MIN_VALUE, Short.MAX_VALUE)) {
            return 2;
        }
        if (fitsSigned(unscaled, Integer.MIN_VALUE, Integer.MAX_VALUE)) {
            return 4;
        }
        if (fitsSigned(unscaled, Long.MIN_VALUE, Long.MAX_VALUE)) {
            return 8;
        }
        return 16;
    }

    private static boolean fitsSigned(final BigInteger unscaled, final long low, final long high) {
        return unscaled.compareTo(BigInteger.valueOf(low)) >= 0
            && unscaled.compareTo(BigInteger.valueOf(high)) <= 0;
    }

    private static BigInteger unscaledOf(final Object value, final int scale) {
        if (value == null) {
            return null;
        }
        final BigDecimal exact;
        if (value instanceof BigDecimal) {
            exact = (BigDecimal) value;
        } else if (value instanceof BigInteger) {
            exact = new BigDecimal((BigInteger) value);
        } else if (value instanceof Number) {
            exact = new BigDecimal(value.toString());
        } else {
            try {
                exact = new BigDecimal(value.toString().trim());
            } catch (final NumberFormatException notANumber) {
                return null;
            }
        }
        // The DECLARED scale decides the shift, not the value's own: storage holds the number at the
        // column's scale, so a value carrying more digits is truncated to it rather than widening the
        // tag.
        return exact.setScale(scale, RoundingMode.DOWN).unscaledValue();
    }
}
