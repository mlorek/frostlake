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

package dev.frostlake.parser;

import org.antlr.v4.runtime.Token;

import java.util.List;

/**
 * An OVERLAY call written in the PLACING form, by the places of its tokens: its parentheses, the PLACING's value, the
 * FROM's value and a FOR tail's, each found at the call's own depth (see {@link OverlayPlacingLines}).
 */
final class OverlayCall {

    private final int open;
    private final int close;
    private final int value;
    private final int fromValue;
    private final int forAt;
    private final boolean simple;

    private OverlayCall(final int open, final int close, final int value, final int fromValue, final int forAt,
                        final boolean simple) {
        this.open = open;
        this.close = close;
        this.value = value;
        this.fromValue = fromValue;
        this.forAt = forAt;
        this.simple = simple;
    }

    /**
     * The call around a PLACING, or null when the PLACING stands in no OVERLAY call of that form.
     *
     * @param spoken  the statement's tokens, comments and blanks dropped
     * @param placing where the PLACING stands
     * @return the call
     */
    static OverlayCall around(final List<Token> spoken, final int placing) {
        int open = -1;
        int depth = 0;
        for (int i = placing - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    open = i;
                    break;
                }
                depth--;
            }
        }
        if (open < 1 || open + 1 >= placing || !OverlayPlacingLines.isWord(spoken.get(open - 1), "OVERLAY")) {
            return null;
        }
        int from = -1;
        int forAt = -1;
        int close = -1;
        depth = 0;
        for (int i = placing + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    close = i;
                    break;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.FROM && from < 0) {
                from = i;
            } else if (depth == 0 && type == FrostlakeLexer.FOR && from >= 0 && forAt < 0) {
                forAt = i;
            }
        }
        final int fromEnd = forAt >= 0 ? forAt : close;
        if (close < 0 || from <= placing + 1 || fromEnd <= from + 1 || forAt >= 0 && close <= forAt + 1) {
            return null;
        }
        final boolean simple = open + 2 == placing && spoken.get(open + 1).getType() == FrostlakeLexer.IDENTIFIER
            && from == placing + 2 && isLiteral(spoken.get(placing + 1).getType()) && fromEnd == from + 2
            && spoken.get(from + 1).getType() == FrostlakeLexer.INTEGER_LITERAL
            && (forAt < 0 || close == forAt + 2 && spoken.get(forAt + 1).getType() == FrostlakeLexer.INTEGER_LITERAL);
        return new OverlayCall(open, close, placing + 1, from + 1, forAt, simple);
    }

    /** Where the call's '(' stands. */
    int getOpen() {
        return open;
    }

    /** Where the call's ')' stands. */
    int getClose() {
        return close;
    }

    /** Where the PLACING's value begins. */
    int getValue() {
        return value;
    }

    /** Where the FROM's value begins. */
    int getFromValue() {
        return fromValue;
    }

    /** Where the FOR stands, or -1 when the call has no FOR tail. */
    int getFor() {
        return forAt;
    }

    /** Whether the operand is one name, the value one literal and the positions one number each. */
    boolean isSimple() {
        return simple;
    }

    private static boolean isLiteral(final int type) {
        return type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL;
    }
}
