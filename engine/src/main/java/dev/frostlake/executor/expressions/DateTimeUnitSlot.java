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

package dev.frostlake.executor.expressions;

import java.util.Locale;

/**
 * Which argument of which function is a date/time unit SLOT — a position where Snowflake reads a word
 * as the unit's NAME and never as an expression.
 *
 * <p>That is the whole point of the distinction. In a slot the word wins against everything: a real
 * column of the same name, a QUALIFIED reference to one, a quoted identifier. Over a table with an
 * INT column {@code dd} holding 7, {@code DATEADD(dd, 1, ts)} adds one DAY on a real account, and so
 * does {@code DATEADD(sh.dd, 1, ts)} — the qualifier is read and discarded. Frostlake had this
 * backwards and resolved the column, which made the unit "7".
 *
 * <p>Outside a slot the same word is an ordinary reference, which is the other half: {@code SELECT dd}
 * and {@code dd + 1} answer 7 on both engines, and {@code TRUNC(tm, hour)} — whose second argument is
 * NOT a slot — is refused live where Frostlake truncated to the hour.
 *
 * <pre>
 *   slot at 0   DATEADD  DATEDIFF  DATE_TRUNC  DATE_PART
 *               TIMEADD  TIMEDIFF  TIMESTAMPADD  TIMESTAMPDIFF
 *   slot at 1   LAST_DAY
 *   no slot     TRUNC — its unit is an ordinary value, and only a string literal is accepted
 * </pre>
 *
 * <p>What a slot ACCEPTS is narrow: an identifier (bare, qualified or quoted), a string literal, or
 * NULL — which makes the whole call NULL. Anything else, a call or a number or a CASE, is refused
 * before the unit is even looked up, with live's own spacing: a space before the bracket's close and
 * none after it.
 */
final class DateTimeUnitSlot {

    private DateTimeUnitSlot() {
    }

    /** The argument index that is a unit slot for {@code funcName}, or -1 when it has none. */
    static int positionIn(final String funcName) {
        switch (funcName) {
            case "DATEADD": case "DATEDIFF": case "DATE_TRUNC": case "DATE_PART":
            case "TIMEADD": case "TIMEDIFF": case "TIMESTAMPADD": case "TIMESTAMPDIFF":
                return 0;
            case "LAST_DAY":
                return 1;
            default:
                return -1;
        }
    }

    /**
     * The unit a slot argument names: an identifier's own name UPPER-CASED, a string literal's text as
     * written, or null when the argument is neither and the caller must refuse it. A qualified
     * reference contributes only its last part — the relation it names is never looked up.
     */
    static String unitTextOf(final Expression arg) {
        if (arg instanceof ColumnReferenceExpression) {
            return ((ColumnReferenceExpression) arg).getColumnName().toUpperCase(Locale.ROOT);
        }
        if (arg instanceof LiteralExpression
                && ((LiteralExpression) arg).getType() == LiteralType.STRING) {
            return String.valueOf(((LiteralExpression) arg).getValue());
        }
        return null;
    }

    /** Whether the slot holds the word NULL, which makes the whole call NULL rather than refusing. */
    static boolean isNullLiteral(final Expression arg) {
        return arg instanceof LiteralExpression
            && ((LiteralExpression) arg).getType() == LiteralType.NULL;
    }

    /**
     * Live's refusal for a slot holding something it will not read as a unit. The spacing is its own —
     * {@code [UPPER('day') ]for function DATEADD} — and is reproduced as measured.
     */
    static String notAUnitRefusal(final String echo, final String funcName) {
        return "Date/time component [" + echo + " ]for function " + funcName
            + " needs to be an identifier or a string literal.";
    }
}
