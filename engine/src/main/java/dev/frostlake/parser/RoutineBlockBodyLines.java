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
 * The lines live reports for a fault in a routine's block body written without quotes, {@code CREATE FUNCTION f()
 * RETURNS INT AS BEGIN … END}: the body is read as the same block standing alone, where it stands. This grammar's
 * reading of the routine passes over a word the block's own reading refuses — {@code TABLE t x y} in the body is 'x'
 * and then 'y', as in an anonymous block, where the routine's parse names 'y' first (live-verified). Used only where
 * that block reading meets an earlier fault than the routine's own parse.
 */
final class RoutineBlockBodyLines {

    private RoutineBlockBodyLines() {
    }

    /**
     * The lines for a CREATE FUNCTION or PROCEDURE whose block body holds the parse's first fault, or null.
     *
     * @param sql        the text parsed
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the parse's first fault
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null || spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.CREATE) {
            return null;
        }
        int kind = -1;
        for (int i = 1; i < spoken.size() && i < 6 && kind < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.FUNCTION || type == FrostlakeLexer.PROCEDURE) {
                kind = i;
            }
        }
        int body = -1;
        int depth = 0;
        for (int i = kind + 1; kind > 0 && i + 1 < spoken.size() && body < 0; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            final int next = spoken.get(i + 1).getType();
            if (depth == 0 && type == FrostlakeLexer.AS
                    && (next == FrostlakeLexer.BEGIN || next == FrostlakeLexer.DECLARE)) {
                body = i + 1;
            }
        }
        int fault = -1;
        for (int i = body + 1; body > 0 && i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        for (int c = 0; c < spoken.get(body).getStartIndex() && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        final List<String> lines = SyntaxErrorListener.linesReportedFor(new String(text), false);
        final int[] first = lines.isEmpty() ? null : SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        final Token at = spoken.get(fault);
        if (first == null || first[0] > at.getLine()
                || first[0] == at.getLine() && first[1] >= at.getCharPositionInLine()) {
            return null;
        }
        return lines;
    }
}
