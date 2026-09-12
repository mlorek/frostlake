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

/**
 * A numeric literal standing in a POSITIONAL slot — {@code ORDER BY 2}, {@code GROUP BY 1}.
 *
 * <p>The slot takes any numeric literal, not only a whole one, and the position is the literal
 * TRUNCATED toward zero (live-verified): {@code ORDER BY 1.5} and {@code ORDER BY 1.9} both sort by
 * the first select item, {@code 2.5} by the second, and {@code 0.5} truncates to 0 and is out of
 * range. The exponent spelling counts too — {@code 1e0} is position 1 — and a parenthesized literal
 * keeps its slot, while anything COMPUTED does not: {@code ORDER BY 1 + 0} is a constant and leaves
 * the rows alone.
 *
 * <p>Out-of-range positions are refused by the caller, which owns the wording; this class only reads
 * the literal. A literal too large for a {@code long} is clamped rather than overflowing, since every
 * such value is out of range anyway and the refusal echoes the text as written.
 */
final class OrdinalLiteral {

    /** The text is not a bare numeric literal, so it is not a positional reference at all. */
    static final long NOT_AN_ORDINAL = Long.MIN_VALUE;

    /** Digits with an optional sign, fraction and exponent — and nothing else. */
    private static final String NUMERIC_LITERAL = "[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?";

    /** Beyond this the value cannot be a select-list position, and long arithmetic would wrap. */
    private static final int CLAMP_BITS = 62;

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
        if (text == null) {
            return NOT_AN_ORDINAL;
        }
        String literal = text.trim();
        while (literal.length() > 2 && literal.charAt(0) == '('
                && literal.charAt(literal.length() - 1) == ')') {
            literal = literal.substring(1, literal.length() - 1).trim();
        }
        if (!literal.matches(NUMERIC_LITERAL)) {
            return NOT_AN_ORDINAL;
        }
        final BigInteger truncated = new BigDecimal(literal).toBigInteger();
        if (truncated.bitLength() > CLAMP_BITS) {
            return truncated.signum() < 0 ? Long.MIN_VALUE + 1 : Long.MAX_VALUE;
        }
        return truncated.longValue();
    }

    /** Whether the text occupies the positional slot at all, whatever position it names. */
    static boolean isOrdinal(final String text) {
        return positionOf(text) != NOT_AN_ORDINAL;
    }
}
