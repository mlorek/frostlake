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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.IntegerResultWidths;
import dev.frostlake.values.CodePointText;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * EDITDISTANCE(string1, string2 [, max_distance]) — the Levenshtein distance, capped at the maximum when one is
 * given (live-verified): the maximum is rounded half away from zero, a negative one answers 0, a NULL one NULL,
 * a text is read as a number ("Numeric value 'x' is not recognized" when it spells none), and a maximum past a
 * 32-bit integer is "Numeric value '2147483648' is out of range". A BOOLEAN maximum is refused by the argument
 * types.
 */
public class EditDistance extends TextArgumentFunction {

    private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

    public EditDistance() { super("EDITDISTANCE", IntegerResultWidths.POSITION); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        if (args.size() > 2 && args.get(2) == null) return null;
        final Long maximum = args.size() > 2 ? maximumOf(args.get(2)) : null;
        // Over characters: a supplementary character is one edit, not two (live-verified).
        final int[] a = CodePointText.codePoints(args.get(0).toString());
        final int[] b = CodePointText.codePoints(args.get(1).toString());
        final int[][] dp = new int[a.length + 1][b.length + 1];
        for (int i = 0; i <= a.length; i++) dp[i][0] = i;
        for (int j = 0; j <= b.length; j++) dp[0][j] = j;
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                dp[i][j] = a[i-1] == b[j-1]
                    ? dp[i-1][j-1]
                    : 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
            }
        }
        final long distance = dp[a.length][b.length];
        if (maximum == null) {
            return distance;
        }
        return maximum < 0 ? 0L : Math.min(distance, maximum);
    }

    /** The maximum distance as the whole number the call reads. */
    private static Long maximumOf(final Object value) {
        final BigDecimal exact;
        if (value instanceof Double || value instanceof Float) {
            exact = BigDecimal.valueOf(((Number) value).doubleValue());
        } else if (value instanceof Number) {
            exact = new BigDecimal(value.toString());
        } else {
            final String text = value.toString().trim();
            try {
                exact = new BigDecimal(text);
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        }
        final BigDecimal whole = exact.setScale(0, RoundingMode.HALF_UP);
        if (whole.compareTo(INT_MIN) < 0 || whole.compareTo(INT_MAX) > 0) {
            throw new RuntimeException("Numeric value '" + whole.toPlainString() + "' is out of range");
        }
        return Long.valueOf(whole.longValueExact());
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** The maximum is a number, and a BOOLEAN there is refused by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 2 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
