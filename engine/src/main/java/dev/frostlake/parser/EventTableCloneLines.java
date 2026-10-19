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

import java.util.Collections;
import java.util.List;

/**
 * The line live reports for a CREATE EVENT TABLE … CLONE with more after the source: a clone takes no property of its
 * own, so the token after the source is refused where it stands — {@code CLONE base_et COMMENT = 'x'} at the COMMENT,
 * a second CLONE at the CLONE — and a CLONE with no source at the end of the statement; a point of time the clone is
 * made at is the source's, and what follows it is refused the same way (live-verified).
 */
final class EventTableCloneLines {

    private EventTableCloneLines() {
    }

    /**
     * The line for a statement whose first fault stands at or after the CLONE of a CREATE EVENT TABLE … CLONE that
     * opens it, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            final Token token = spoken.get(i);
            if (faultIndex < 0 ? token.getType() == Token.EOF : token.getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0) {
            return null;
        }
        int start = fault;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        int at = start;
        if (type(spoken, at) != FrostlakeLexer.CREATE) {
            return null;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        if (type(spoken, at) != FrostlakeLexer.EVENT || type(spoken, at + 1) != FrostlakeLexer.TABLE) {
            return null;
        }
        at += 2;
        if (type(spoken, at) == FrostlakeLexer.IF && type(spoken, at + 1) == FrostlakeLexer.NOT
                && type(spoken, at + 2) == FrostlakeLexer.EXISTS) {
            at += 3;
        }
        at = pastName(spoken, at);
        if (at < 0 || type(spoken, at) != FrostlakeLexer.CLONE || fault < at) {
            return null;
        }
        at++;
        if (ends(spoken, at)) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(at)));
        }
        at = pastName(spoken, at);
        if (at >= 0 && (type(spoken, at) == FrostlakeLexer.AT_KEYWORD || type(spoken, at) == FrostlakeLexer.BEFORE)) {
            at = pastBracket(spoken, at + 1);
        }
        if (at < 0 || ends(spoken, at) || fault < at) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(at)));
    }

    /** The index past the bracket that opens at {@code at} and the one that closes it, or -1 where none closes. */
    private static int pastBracket(final List<Token> spoken, final int at) {
        if (type(spoken, at) != FrostlakeLexer.LPAREN) {
            return -1;
        }
        int depth = 0;
        for (int i = at; !ends(spoken, i); i++) {
            if (type(spoken, i) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type(spoken, i) == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /** The index past a name of one or more dotted parts that starts at {@code at}, or -1 where none starts. */
    private static int pastName(final List<Token> spoken, final int at) {
        if (ends(spoken, at) || type(spoken, at) == FrostlakeLexer.DOT) {
            return -1;
        }
        int past = at + 1;
        while (type(spoken, past) == FrostlakeLexer.DOT && !ends(spoken, past + 1)) {
            past += 2;
        }
        return past;
    }

    /** Whether the statement ends at {@code at}: the end of input, a semicolon, or no token at all. */
    private static boolean ends(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
