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
 * The line live reports for a comma that ends a parenthesized list of a CREATE, an ALTER or a COPY, a list of values
 * among them: the parenthesis that closes the list, alone (live-verified). {@code NULL_IF = ('a',)} inside a
 * stage's FILE_FORMAT names the list's own parenthesis, and nothing of the one around it.
 */
final class ListEndCommaLines {

    private ListEndCommaLines() {
    }

    /**
     * The line for a statement whose first fault is a list's closing parenthesis right after a comma, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0 && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3 || type(spoken, fault) != FrostlakeLexer.RPAREN
                || type(spoken, fault - 1) != FrostlakeLexer.COMMA || !opensAfterEquals(spoken, fault)) {
            return null;
        }
        int start = fault;
        while (start > 0 && type(spoken, start - 1) != FrostlakeLexer.SEMI) {
            start--;
        }
        final int word = type(spoken, start);
        if (word != FrostlakeLexer.CREATE && word != FrostlakeLexer.ALTER && word != FrostlakeLexer.COPY) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault)));
    }

    /** Whether the list the parenthesis at {@code close} closes was opened right after an {@code =}. */
    private static boolean opensAfterEquals(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close; i >= 1; i--) {
            final int type = type(spoken, i);
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && --depth == 0) {
                return type(spoken, i - 1) == FrostlakeLexer.EQ;
            } else if (type == FrostlakeLexer.SEMI) {
                return false;
            }
        }
        return false;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
