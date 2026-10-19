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
 * The lines the account reports for a word that cannot name a RETURNS TABLE column: the word, then the ')' that
 * closes the list. NOT names the token after it as well, and TABLE is read as the name, so the token after it and
 * the one after the list are refused instead (live-verified):
 *
 * <pre>
 *   RETURNS TABLE (order INT) AS     'order', ')'
 *   RETURNS TABLE (unique INT) AS    'unique', ')'
 *   RETURNS TABLE (not INT) AS       'not', 'INT', ')'
 *   RETURNS TABLE (table INT) AS     'INT', 'AS'
 * </pre>
 *
 * <p>A later column's word, and a fault after a column's name, are left to the parse: {@code (a INT, order INT)} names
 * the word alone.
 */
final class ReturnColumnLines {

    private ReturnColumnLines() {
    }

    /**
     * The lines for a text whose first fault is a word standing where a RETURNS TABLE's first column name belongs, or
     * null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 3 || fault + 1 >= spoken.size()) {
            return null;
        }
        final int type = spoken.get(fault).getType();
        if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.LPAREN) {
            return null;
        }
        if (spoken.get(fault - 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final int open = listOpen(spoken, fault - 1);
        if (open < 2 || spoken.get(open - 1).getType() != FrostlakeLexer.TABLE
                || spoken.get(open - 2).getType() != FrostlakeLexer.RETURNS) {
            return null;
        }
        final int close = listClose(spoken, fault);
        if (close <= fault + 1 || close + 1 >= spoken.size()) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        if (type == FrostlakeLexer.TABLE) {
            if (spoken.get(close + 1).getType() == Token.EOF) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (type == FrostlakeLexer.NOT) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        return lines;
    }

    /** The index of the '(' whose list holds the token at {@code from}, read back at depth 0, or -1. */
    private static int listOpen(final List<Token> spoken, final int from) {
        int depth = 0;
        for (int i = from; i >= 0; i--) {
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

    /** The index of the ')' that closes the list the token at {@code from} stands in, or -1. */
    private static int listClose(final List<Token> spoken, final int from) {
        int depth = 0;
        for (int i = from; i < spoken.size(); i++) {
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
}
