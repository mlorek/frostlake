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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A numeric literal standing in a POSITIONAL slot — {@code ORDER BY 2}, {@code GROUP BY 1}.
 *
 * <p>The slot takes any numeric literal, not only a whole one, and the position is the literal
 * TRUNCATED toward zero (live-verified): {@code ORDER BY 1.5} and {@code ORDER BY 1.9} both sort by
 * the first select item, {@code 2.5} by the second, and {@code 0.5} truncates to 0 and is out of
 * range. The exponent spelling counts too — {@code 1e0} is position 1 — and parentheses to any depth
 * keep the slot, while anything COMPUTED does not: {@code ORDER BY 1 + 0} is a constant and leaves
 * the rows alone. A minus sign belongs to the literal wherever it is written — {@code -(1)},
 * {@code - 1} and {@code -((1))} are all position -1, and {@code -(-1)} is position 1 — but a plus
 * sign is an operator: {@code ORDER BY +2} and {@code GROUP BY +1} are constants too.
 *
 * <p>Out-of-range positions are refused by the caller, which owns the wording; this class only reads
 * the literal and, through {@link #echo}, spells the value the refusal names — live echoes what the
 * literal FOLDS to, not how it was written: {@code (9)} and {@code ((9))} read {@code [9]},
 * {@code 1e1} reads {@code [10]}, {@code 2.50} reads {@code [2.5]} and {@code -0} reads {@code [0]}.
 * A literal too large for a {@code long} is clamped rather than overflowing, since every such value
 * is out of range anyway.
 */
final class OrdinalLiteral {

    /** The text is not a bare numeric literal, so it is not a positional reference at all. */
    static final long NOT_AN_ORDINAL = Long.MIN_VALUE;

    /** Beyond this the value cannot be a select-list position, and long arithmetic would wrap. */
    private static final int CLAMP_BITS = 62;

    /** A magnitude no select list reaches, standing for a literal too wide for its type to hold. */
    private static final BigDecimal OUT_OF_RANGE_HIGH = new BigDecimal(BigInteger.ONE.shiftLeft(CLAMP_BITS + 1));
    private static final BigDecimal OUT_OF_RANGE_LOW = OUT_OF_RANGE_HIGH.negate();

    private OrdinalLiteral() {
    }

    /**
     * The 1-based position a positional slot's text names, or {@link #NOT_AN_ORDINAL} when the text is
     * not a numeric literal.
     *
     * @param text the slot's source text
     * @return the truncated position, or {@link #NOT_AN_ORDINAL}
     */
    static long positionOf(final String text) {
        final BigDecimal value = valueOf(text);
        if (value == null) {
            return NOT_AN_ORDINAL;
        }
        final BigInteger truncated = value.toBigInteger();
        if (truncated.bitLength() > CLAMP_BITS) {
            return truncated.signum() < 0 ? Long.MIN_VALUE + 1 : Long.MAX_VALUE;
        }
        return truncated.longValue();
    }

    /**
     * How a refusal names the slot's key: the literal's FOLDED value for a positional slot, and the
     * text as written for anything else.
     *
     * @param text the slot's source text
     * @return the text the refusal echoes between its brackets
     */
    static String echo(final String text) {
        final BigDecimal value = valueOf(text);
        return value == null ? text.trim() : plain(value);
    }

    /** Whether the text occupies the positional slot at all, whatever position it names. */
    static boolean isOrdinal(final String text) {
        return positionOf(text) != NOT_AN_ORDINAL;
    }

    /**
     * The value a positional slot's text folds to, or null when the text is not a numeric literal —
     * read from the parsed key, so parentheses and a minus sign wherever they are written belong to
     * the literal, while a computed key belongs to no slot. A key holding anything a number cannot be
     * written with is not read at all, so an ordinary key is never parsed here, and a key that IS
     * written as a number and still cannot be read — one too wide for the type — refuses as it does
     * wherever else it is written.
     */
    private static BigDecimal valueOf(final String text) {
        if (text == null || !writtenAsANumber(text.trim())) {
            return null;
        }
        try {
            return literalValue(ExpressionEvaluator.parse(text), false);
        } catch (final RuntimeException unreadable) {
            // A number too wide for its type is still written in the slot, and no such value can be a
            // position, so the slot is out of range whichever way it leans. The literal earns its own
            // refusal where it is read, which is where the statement names the place it was written.
            return text.trim().startsWith("-") ? OUT_OF_RANGE_LOW : OUT_OF_RANGE_HIGH;
        }
    }

    /** Whether the key holds only what a number is written with: digits, a point, an exponent, signs, parentheses. */
    private static boolean writtenAsANumber(final String text) {
        boolean digit = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if (c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-' && c != '('
                    && c != ')' && !Character.isWhitespace(c)) {
                return false;
            }
        }
        return digit;
    }

    /** The numeric literal under any number of minus signs, negated as often as they say. */
    private static BigDecimal literalValue(final Expression expression, final boolean negated) {
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            if (unary.getOperator() != UnaryOperator.NEGATE) {
                return null;   // a plus sign is an operator, so its operand fills no slot
            }
            return literalValue(unary.getOperand(), !negated);
        }
        if (!(expression instanceof LiteralExpression)) {
            return null;
        }
        final LiteralExpression literal = (LiteralExpression) expression;
        if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
            return null;
        }
        final Object value = literal.getValue();
        final BigDecimal exact;
        if (value instanceof BigDecimal) {
            exact = (BigDecimal) value;
        } else if (value instanceof Long || value instanceof Integer) {
            exact = BigDecimal.valueOf(((Number) value).longValue());
        } else if (value instanceof Double) {
            exact = new BigDecimal(value.toString());
        } else {
            return null;
        }
        return negated ? exact.negate() : exact;
    }

    /** A folded value as live spells it: no trailing zeros, no exponent, and no sign on zero. */
    private static String plain(final BigDecimal value) {
        final BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.signum() == 0 ? BigDecimal.ZERO : stripped).toPlainString();
    }
}
