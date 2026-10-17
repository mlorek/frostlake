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

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a second modifier of a call's star argument, which takes one at most: the call is read as
 * closed before it, so an EXCLUDE is refused and then the token after it, and an ILIKE, an operator there, is refused
 * and then the token after its pattern (live-verified):
 *
 * <pre>
 *   SELECT HASH(* EXCLUDE (id) EXCLUDE (b)) FROM fz       'EXCLUDE' at 27, '(' at 35
 *   SELECT HASH(* EXCLUDE (b) ILIKE 'i%') FROM fz         'ILIKE' at 26, ')' at 36
 *   SELECT HASH(1, * EXCLUDE (id) EXCLUDE (b)) FROM fz    'EXCLUDE' at 30, '(' at 38
 * </pre>
 *
 * A select item's star takes several modifiers of other kinds, and a second of one kind there is refused alone; an
 * object star's braces are read by the grammar.
 */
final class RepeatedStarModifierLines {

    private RepeatedStarModifierLines() {
    }

    /**
     * The lines for a statement whose first fault is a second modifier of a call's star argument, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @param firstOnly  whether only the first line is read
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final boolean firstOnly) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        final int keyword = type(spoken, fault);
        if (fault < 2 || (keyword != FrostlakeLexer.EXCLUDE && keyword != FrostlakeLexer.ILIKE)) {
            return null;
        }
        final int modifier = modifierEndingAt(spoken, fault - 1);
        if (modifier < 1 || type(spoken, modifier - 1) != FrostlakeLexer.STAR || !inCall(spoken, modifier - 1)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        int next = fault + 1;
        if (type(spoken, fault) == FrostlakeLexer.ILIKE && type(spoken, next) == FrostlakeLexer.STRING_LITERAL) {
            next++;
        }
        if (!firstOnly && type(spoken, next) != Token.EOF) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(next)));
        }
        return lines;
    }

    /** The index of the keyword of a whole star modifier whose last token stands at {@code end}, or -1. */
    private static int modifierEndingAt(final List<Token> spoken, final int end) {
        if (type(spoken, end) == FrostlakeLexer.STRING_LITERAL && type(spoken, end - 1) == FrostlakeLexer.ILIKE) {
            return end - 1;
        }
        if (type(spoken, end) == FrostlakeLexer.RPAREN) {
            int depth = 0;
            for (int i = end; i >= 0; i--) {
                if (type(spoken, i) == FrostlakeLexer.RPAREN) {
                    depth++;
                } else if (type(spoken, i) == FrostlakeLexer.LPAREN) {
                    depth--;
                    if (depth == 0) {
                        return type(spoken, i - 1) == FrostlakeLexer.EXCLUDE ? i - 1 : -1;
                    }
                }
            }
            return -1;
        }
        return end >= 1 && type(spoken, end - 1) == FrostlakeLexer.EXCLUDE ? end - 1 : -1;
    }

    /** Whether the star at {@code star} stands in a call's parentheses: the innermost bracket open around it. */
    private static boolean inCall(final List<Token> spoken, final int star) {
        int depth = 0;
        for (int i = star - 1; i >= 0; i--) {
            final int type = type(spoken, i);
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return type == FrostlakeLexer.LPAREN && i > 0 && type(spoken, i - 1) != FrostlakeLexer.LPAREN
                        && type(spoken, i - 1) != FrostlakeLexer.COMMA;
                }
                depth--;
            }
        }
        return false;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
