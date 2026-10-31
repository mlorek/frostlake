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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a word written after a name in an INSERT's column list, where a ',' or the list's
 * ')' belongs: the account closes the list before the word and reads the INSERT's query from it, so the word is the
 * one line unless it begins a query (all live-verified):
 *
 * <pre>
 *   INSERT INTO t (a b)                          'b'
 *   INSERT INTO t (SHOW TABLES)                  'TABLES', as INSERT INTO t (DESCRIBE VIEW t) is 'VIEW'
 *   INSERT INTO t (DESCRIBE TABLE t)             'TABLE', then the list's ')': TABLE t is a query, which nothing
 *                                                may follow
 *   INSERT INTO t (DESCRIBE TABLE t) SELECT 1    the same
 * </pre>
 */
final class InsertColumnListWordLines {

    private InsertColumnListWordLines() {
    }

    /**
     * The lines for a text whose first fault is such a word, or null for any other text.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 5 || spoken.get(fault).getType() == Token.EOF || spoken.get(0).getType() != FrostlakeLexer.INSERT) {
            return null;
        }
        int at = spoken.get(1).getType() == FrostlakeLexer.OVERWRITE ? 2 : 1;
        if (spoken.get(at).getType() != FrostlakeLexer.INTO) {
            return null;
        }
        at = afterName(spoken, at + 1);
        if (at < 0 || at >= fault || spoken.get(at).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        // The columns, each a name, up to the one the word follows.
        at++;
        while (at < fault && SyntaxErrorListener.isNameLike(spoken.get(at))) {
            at++;
            if (at < fault && spoken.get(at).getType() == FrostlakeLexer.COMMA) {
                at++;
            } else {
                break;
            }
        }
        if (at != fault || !SyntaxErrorListener.isNameLike(spoken.get(fault - 1)) || !isWord(spoken.get(fault))) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        final int word = spoken.get(fault).getType();
        if (word == FrostlakeLexer.TABLE || word == FrostlakeParser.TABLE_QUERY) {
            final int after = afterName(spoken, fault + 1);
            if (after > 0 && spoken.get(after).getType() != Token.EOF) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(after)));
            }
        }
        return lines;
    }

    /** Whether a token is a word: a name or a keyword, and no call's '(' or other sign. */
    private static boolean isWord(final Token token) {
        final String text = token.getText();
        if (text == null || text.isEmpty() || !Character.isLetter(text.charAt(0))) {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }
        return true;
    }

    /** The index after a qualified name that starts at {@code at}, or -1 when no name starts there. */
    private static int afterName(final List<Token> spoken, final int at) {
        if (at >= spoken.size() || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return -1;
        }
        int next = at + 1;
        while (next + 1 < spoken.size() && spoken.get(next).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isNameLike(spoken.get(next + 1))) {
            next += 2;
        }
        return next < spoken.size() ? next : -1;
    }
}
