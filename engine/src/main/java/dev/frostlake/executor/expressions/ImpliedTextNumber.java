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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How a TEXT reads as a number in exact arithmetic — {@code t + 0}, {@code t * n}, {@code MOD(t, 2)},
 * {@code ROUND(t, 1)} — live-verified over VARCHAR columns of every declared length and over
 * literals:
 *
 * <ul>
 *   <li>a text COLUMN or expression reads as {@code NUMBER(18,5)}: {@code t + 0} declares
 *       NUMBER(19,5) and answers {@code 5.00000}, {@code t / 1} NUMBER(24,11), {@code t % 2}
 *       NUMBER(18,5); the value is rounded HALF_UP to five decimals before the operation
 *       ({@code '1.123456' + 0} is 1.12346);</li>
 *   <li>a text LITERAL reads as {@code NUMBER(18, s)} with ITS OWN scale: {@code '5' + 1} declares
 *       NUMBER(19,0) and answers 6, {@code '2.5' + 1} NUMBER(19,1) and 3.5, {@code MOD('5', 2)}
 *       NUMBER(2,0);</li>
 *   <li>text beside text, beside a FLOAT, beside a VARIANT or beside a bare NULL is a FLOAT, as is a
 *       VARIANT beside anything and a signed text — those are not this reading.</li>
 * </ul>
 *
 * <p>The reading refuses at row time: a text spelling no number is {@code Numeric value 'x' is not
 * recognized}; so is one whose five-decimal reading overflows the eighteen digits the account parses
 * it into (an unscaled value past 9223372036854775807 — {@code '99999999999999'},
 * {@code '123456789012345'}, {@code '1e14'}, {@code '1e30'}); one that parses but carries more than
 * thirteen whole digits is {@code Numeric value '12345678901234' is out of range} — so is
 * {@code '1e13'} and {@code '9999999999999.999995'}, which rounds up to fourteen. Spellings a literal
 * has are all read: {@code ' 5 '}, {@code '.5'}, {@code '5.'}, {@code '+5'}, {@code '5e2'},
 * {@code '0000000000000005'}.
 */
public final class ImpliedTextNumber {

    /** The number a text column reads as in exact arithmetic. */
    public static final NumericType COLUMN_READING = new NumericType("NUMBER", 18, 5);

    private static final int PRECISION = 18;
    private static final int SCALE = 5;
    private static final int WHOLE_DIGITS = PRECISION - SCALE;
    private static final BigDecimal PARSED_LIMIT = BigDecimal.valueOf(Long.MAX_VALUE);

    private ImpliedTextNumber() {
    }

    /**
     * The number a text LITERAL reads as: eighteen digits at the literal's own scale, or null when the
     * text spells no number (the row-time refusal is then the operation's own).
     *
     * @param text the literal's text
     * @return NUMBER(18, s), or null
     */
    public static NumericType literalReading(final String text) {
        final BigDecimal spelled = ExpressionArithmetic.asNumber(text) instanceof BigDecimal
            ? (BigDecimal) ExpressionArithmetic.asNumber(text) : null;
        if (spelled == null) {
            return null;
        }
        return new NumericType("NUMBER", PRECISION, Math.max(0, spelled.scale()));
    }

    /**
     * The number a text literal reads as under the remainder operator and MOD: its own digits, as a
     * numeric literal of the same spelling would — {@code '5'} NUMBER(1,0), {@code '12345'}
     * NUMBER(5,0), {@code '2.5'} NUMBER(2,1) — or null when the text spells no number.
     *
     * @param text the literal's text
     * @return the literal's own width, or null
     */
    public static NumericType literalOwnWidth(final String text) {
        final Number spelled = ExpressionArithmetic.asNumber(text);
        if (!(spelled instanceof BigDecimal)) {
            return null;
        }
        final BigDecimal value = (BigDecimal) spelled;
        final int scale = Math.max(0, value.scale());
        final int precision = Math.max(value.precision(), scale + 1);
        return new NumericType("NUMBER", Math.min(38, precision), scale);
    }

    /**
     * Whether an expression is a text literal, whose reading keeps its own scale.
     *
     * @param expr the operand
     * @return true for a quoted string literal
     */
    public static boolean isTextLiteral(final Expression expr) {
        return expr instanceof LiteralExpression && ((LiteralExpression) expr).getType() == LiteralType.STRING;
    }

    /**
     * A text column's value read as NUMBER(18,5), or the row-time refusal.
     *
     * @param text the cell
     * @return the value at scale five
     */
    public static BigDecimal read(final CharSequence text) {
        final String trimmed = text.toString().trim();
        final Number spelled = ExpressionArithmetic.asNumber(trimmed);
        if (!(spelled instanceof BigDecimal)) {
            throw new RuntimeException(NumericRangeRefusal.unreadableText(trimmed));
        }
        final BigDecimal atScale = ((BigDecimal) spelled).setScale(SCALE, RoundingMode.HALF_UP);
        if (atScale.unscaledValue().abs().compareTo(PARSED_LIMIT.toBigInteger()) > 0) {
            throw new RuntimeException(NumericRangeRefusal.unreadableText(trimmed));
        }
        if (atScale.precision() - atScale.scale() > WHOLE_DIGITS && atScale.abs().compareTo(BigDecimal.ONE) >= 0) {
            throw new RuntimeException("Numeric value '" + trimmed + "' is out of range");
        }
        return atScale;
    }
}
