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
 * The lines live reports for an object constant whose key is no text: the parse's own line - the key, or the ':'
 * after a key the account reads as a word - then the first token of the value after each ':' from the faulty pair to
 * the constant's end (all live-verified):
 *
 * <pre>
 *   SELECT {1: 'a'}                '1' at 8, then ''a'' at 11
 *   SELECT {'a': 1, 2: 'b'}        '2' at 16, then ''b'' at 19
 *   SELECT {1: 'a', 'b': 2}        '1' at 8, ''a'' at 11, then '2' at 21
 *   SELECT {TRUE: 1}               ':' at 12, then '1' at 14
 * </pre>
 */
final class ObjectConstantKeyLines {

    private ObjectConstantKeyLines() {
    }

    /**
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null where that token is no key of an object constant, nor the ':' after one
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        final int key = fault > 0 && spoken.get(fault).getType() == FrostlakeLexer.COLON ? fault - 1 : fault;
        if (key < 1 || key + 1 >= spoken.size() || spoken.get(key + 1).getType() != FrostlakeLexer.COLON
                || spoken.get(key - 1).getType() != FrostlakeLexer.LBRACE
                    && spoken.get(key - 1).getType() != FrostlakeLexer.COMMA
                || !insideBraces(spoken, key)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        int depth = 0;
        for (int i = key + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET) {
                depth--;
            } else if (type == FrostlakeLexer.RBRACE) {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (type == FrostlakeLexer.COLON && depth == 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
            }
        }
        return lines;
    }

    /** Whether the token at {@code at} stands directly inside a pair of braces, no other bracket between. */
    private static boolean insideBraces(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return true;
                }
                depth--;
            }
        }
        return false;
    }
}
