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
import java.util.Collections;
import java.util.List;

/**
 * The lines the account reports for a CREATE OR ALTER TASK that writes a WITH CONTACT clause, which a task does not
 * take (live-verified):
 *
 * <pre>
 *   CREATE OR ALTER TASK tk WITH TAG (t = 'v') WITH CONTACT (SUPPORT = c) AS …   the second WITH alone
 *   CREATE OR ALTER TASK tk WITH CONTACT (SUPPORT = c) WITH TAG (t = 'v') AS …   CONTACT, its '(', then the WITH
 *                                                                                after the ')'
 * </pre>
 */
final class TaskContactLines {

    private TaskContactLines() {
    }

    /**
     * The lines for a statement whose first fault stands at such a clause, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
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
        if (type(spoken, start) != FrostlakeLexer.CREATE || type(spoken, start + 1) != FrostlakeLexer.OR
                || type(spoken, start + 2) != FrostlakeLexer.ALTER || type(spoken, start + 3) != FrostlakeLexer.TASK) {
            return null;
        }
        final int at = pastName(spoken, start + 4);
        if (at < 0 || type(spoken, at) != FrostlakeLexer.WITH) {
            return null;
        }
        if (type(spoken, at + 1) == FrostlakeLexer.TAG) {
            final int next = pastBracket(spoken, at + 2);
            return next == fault && type(spoken, next) == FrostlakeLexer.WITH
                    && type(spoken, next + 1) == FrostlakeLexer.CONTACT
                ? Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(next))) : null;
        }
        if (type(spoken, at + 1) != FrostlakeLexer.CONTACT || fault != at + 1
                || type(spoken, at + 2) != FrostlakeLexer.LPAREN) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(at + 2)));
        final int next = pastBracket(spoken, at + 2);
        if (next >= 0 && type(spoken, next) == FrostlakeLexer.WITH) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(next)));
        }
        return lines;
    }

    /** The index past the bracket that opens at {@code at} and the one that closes it, or -1 where none closes. */
    private static int pastBracket(final List<Token> spoken, final int at) {
        if (type(spoken, at) != FrostlakeLexer.LPAREN) {
            return -1;
        }
        int depth = 0;
        for (int i = at; type(spoken, i) != Token.EOF && type(spoken, i) != FrostlakeLexer.SEMI; i++) {
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
        final int first = type(spoken, at);
        if (first == Token.EOF || first == FrostlakeLexer.SEMI || first == FrostlakeLexer.DOT) {
            return -1;
        }
        int past = at + 1;
        while (type(spoken, past) == FrostlakeLexer.DOT && type(spoken, past + 1) != Token.EOF) {
            past += 2;
        }
        return past;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
