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

import org.antlr.v4.runtime.Token;

/**
 * How wide a WRITTEN integer literal may be. The largest exact numeric is NUMBER(38,0), so 38
 * significant digits is the most one can carry: 39 is refused by the literal READER, before any clause
 * sees the number, and the refusal is positioned at the literal's own offset.
 *
 * <p>Three rules that had to be measured rather than assumed:
 *
 * <ul>
 *   <li>Only a POINT-FREE, EXPONENT-FREE literal belongs to this family. The same magnitude written
 *       with a decimal point or an exponent is a DOUBLE, which has no such limit — {@code 999…9.0}
 *       (39 nines) and {@code 1e39} both answer. That is why the sentence says INTEGER literal.</li>
 *   <li>The limit counts SIGNIFICANT digits, so leading zeros are free: 39 zeros is a legal way to
 *       write 0, and 40 zeros followed by a 1 is a legal way to write 1. But the ECHO is the literal
 *       exactly as written, leading zeros included.</li>
 *   <li>A leading minus is NOT part of the literal — it is a unary operator. The position is the
 *       DIGITS' own offset (one past the sign) and the echoed value carries no sign.</li>
 * </ul>
 */
public final class IntegerLiteralRange {

    /** NUMBER(38,0) is the widest exact numeric, so 38 significant digits is the ceiling. */
    private static final int MAX_SIGNIFICANT_DIGITS = 38;

    /** Static helpers only — never instantiated. */
    private IntegerLiteralRange() {
    }

    /** Whether this run of digits is too wide to be read as an integer literal. */
    public static boolean isOutOfRange(final String digits) {
        return significantDigits(digits) > MAX_SIGNIFICANT_DIGITS;
    }

    /** The refusal's own sentence, echoing the literal as it was written. */
    public static String sentence(final String digits) {
        return "Integer literal is out of representable range: " + digits;
    }

    /**
     * Refuse the token if its digits are too wide. For the readers that take a literal straight from
     * the token stream — the LIMIT / OFFSET slots and a column DEFAULT — where the token's position is
     * already statement-relative and needs no fragment resolution.
     */
    public static void reject(final Token token) {
        if (isOutOfRange(token.getText())) {
            throw new RuntimeException(SqlCompilationError.atCapitalised(
                token.getLine(), token.getCharPositionInLine(), sentence(token.getText())));
        }
    }

    /** Digits that are not a leading zero — the count the limit is applied to. */
    private static int significantDigits(final String digits) {
        int first = 0;
        while (first < digits.length() - 1 && digits.charAt(first) == '0') {
            first++;
        }
        return digits.length() - first;
    }
}
