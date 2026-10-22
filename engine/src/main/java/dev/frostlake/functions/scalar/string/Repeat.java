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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.types.StringType;
import dev.frostlake.values.CodePointText;
import dev.frostlake.values.LobSize;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * REPEAT(s, n): the string {@code n} times over, NULL when either argument is NULL. The account plans
 * it as {@code LPAD('', n * LENGTH(s), s)}, which is what judges a BINARY argument — see
 * {@link SemiStructuredRejection#REPEAT_REWRITE_OPERANDS}.
 */
public class Repeat extends TextArgumentFunction {
    public Repeat() { super("REPEAT", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL count answers NULL as a NULL string does (live: REPEAT('ab', NULL) is NULL).
        if (args.get(0) == null || args.get(1) == null) return null;
        final String s = SharedFunctionHelpers.textOf(args.get(0));
        // The count multiplies the text's length, and the product is what rounds: REPEAT('ab', 2.5) is five
        // characters, ababa, and REPEAT('ab', 0.25) one (live-verified). A text count is read as its number. The call
        // is planned as an LPAD of that product, so the product is read as LPAD reads its length: a FLOAT's through
        // the nine-digit slot, an exact one as a 32-bit length (see requireProductFits).
        final long characters;
        if (args.get(1) instanceof Double || args.get(1) instanceof Float) {
            characters = WholeNumberArgument.padLength(((Number) args.get(1)).doubleValue() * CodePointText.length(s));
        } else {
            characters = requireProductFits(args, -1, false);
        }
        if (characters <= 0 || s.isEmpty()) return "";
        // The size is judged before a character is built: past the largest text the call is refused (see LobSize).
        LobSize.requireText(CodePointText.repeatedUtf8Length(s, characters));
        return CodePointText.repeatTo(s, (int) characters);
    }

    /**
     * The characters an exact count asks for, refused where they do not fit the 32-bit length REPEAT is planned with:
     * {@code Number out of representable range: type FIXED[SB4](29,0){not null}, value 10000000000}, the product's
     * own type - the count's precision and LENGTH's eighteen digits, at most thirty-eight - with the count's
     * nullability (live-verified: REPEAT('ab', 3e9) is FIXED[SB4](28,0) and 6000000000, a NUMBER(38,0) column's count
     * FIXED[SB4](38,0){nullable}).
     *
     * @param args           the call's arguments, the text and the count
     * @param countPrecision the count's declared precision, or -1 to take its digits'
     * @param nullable       whether the count may be NULL
     * @return the characters
     */
    public static long requireProductFits(final List<Object> args, final int countPrecision, final boolean nullable) {
        final BigDecimal count = decimalOf(args.get(1));
        final BigDecimal product = count.multiply(BigDecimal.valueOf(
            CodePointText.length(SharedFunctionHelpers.textOf(args.get(0))))).setScale(0, RoundingMode.HALF_UP);
        if (product.toBigInteger().bitLength() <= 31) {
            return product.longValue();
        }
        final BigDecimal whole = count.setScale(0, RoundingMode.HALF_UP).abs();
        final int digits = countPrecision > 0 ? countPrecision : Math.max(1, whole.precision() - whole.scale());
        throw new RuntimeException("Number out of representable range: type FIXED[SB4](" + Math.min(38, digits + 18)
            + ",0){" + (nullable ? "nullable" : "not null") + "}, value " + product.toPlainString());
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A BINARY in either position is judged as the LPAD the call is planned as (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.REPEAT_REWRITE_OPERANDS;
    }

    /**
     * A predicate count is judged as the product the call is planned with, '*': (BOOLEAN, NUMBER(18,0))
     * (live-verified); a predicate string is refused as any text function refuses one.
     */
    @Override
    public SemiStructuredRejection predicateRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.REPEAT_REWRITE_OPERANDS : SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A BOOLEAN count is judged as the product the call is planned with, like a predicate one (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.REPEAT_REWRITE_OPERANDS : SemiStructuredRejection.NONE;
    }
}
