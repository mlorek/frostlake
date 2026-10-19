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

import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * Where a fault stands with respect to the CAST or TRY_CAST around it, read off the tokens: the cast's parentheses,
 * the brackets the cast is nested in, and the clause that holds it.
 */
final class CastFaultPlace {

    private CastFaultPlace() {
    }

    /** The position of the token with a token index, or -1. */
    static int positionOf(final List<Token> spoken, final int tokenIndex) {
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }

    /** The position of the '(' that the token at {@code at} stands directly inside, or -1. */
    static int enclosingParen(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /** The position of the ')' that closes the '(' at {@code open}, or -1 when the statement ends first. */
    static int closeOf(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether the token at a position is the CAST or the TRY_CAST keyword. */
    static boolean isCast(final List<Token> spoken, final int at) {
        return at >= 0 && (spoken.get(at).getType() == FrostlakeLexer.CAST
            || spoken.get(at).getType() == FrostlakeLexer.TRY_CAST);
    }

    /**
     * The clause word a token stands under, its query's SELECT included: the nearest SELECT, FROM, WHERE, ON,
     * HAVING, QUALIFY, GROUP or ORDER before it that no bracket closed since stands between; -1 for none.
     */
    static int clauseOf(final List<Token> spoken, final int at) {
        int closed = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                closed++;
            } else if (type == FrostlakeLexer.LPAREN) {
                closed = Math.max(0, closed - 1);
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            } else if (closed == 0 && (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM
                    || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.ON || type == FrostlakeLexer.HAVING
                    || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
                    || type == FrostlakeLexer.GROUP)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * How many brackets — parentheses and an object literal's braces — opened since the clause word at
     * {@code clause} are still open at the token at {@code at}; one for a window's ORDER BY, which the OVER's
     * parenthesis holds.
     */
    static int nesting(final List<Token> spoken, final int clause, final int at) {
        int open = 0;
        for (int i = clause + 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE) {
                open++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE) {
                open = Math.max(0, open - 1);
            }
        }
        // A window's ORDER BY stands inside the OVER's own parenthesis.
        if (open == 0 && clause > 1 && spoken.get(clause).getType() == FrostlakeLexer.ORDER
                && spoken.get(clause - 1).getType() == FrostlakeLexer.LPAREN
                && spoken.get(clause - 2).getType() == FrostlakeLexer.OVER) {
            return 1;
        }
        return open;
    }

    /** The text with the characters of the tokens from one position through another turned to spaces. */
    static char[] blanked(final char[] text, final List<Token> spoken, final int from, final int through) {
        final int start = spoken.get(from).getStartIndex();
        final int stop = spoken.get(through).getStopIndex();
        for (int i = Math.max(0, start); i <= stop && i < text.length; i++) {
            if (text[i] != '\n') {
                text[i] = ' ';
            }
        }
        return text;
    }
}
