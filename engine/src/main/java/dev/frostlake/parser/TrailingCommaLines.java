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
 * The lines live reports for a call's or an IN list's argument list that ends in a comma: the ')' after the comma is
 * refused and the statement is read on as if the comma were not written, so a later fault is told too — another such
 * list's ')' as well (all live-verified):
 *
 * <pre>
 *   SELECT ABS(1,), ABS(2,) FROM t                    ')' at 13, then ')' at 22
 *   BEGIN SELECT ABS(1,) FROM t; RETURN 1 1; END       ')', then the later statement's '1'
 *   BEGIN SELECT a FROM t WHERE a IN (1,); RETURN 1 1; END   ')', then '1'
 * </pre>
 */
final class TrailingCommaLines {

    private TrailingCommaLines() {
    }

    /**
     * The lines for a text whose first fault is such a ')', or null.
     *
     * @param sql        the text
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null || spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.SELECT
                && spoken.get(0).getType() != FrostlakeLexer.BEGIN) {
            return null;
        }
        int fault = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        if (fault < 3 || spoken.get(fault).getType() != FrostlakeLexer.RPAREN
                || spoken.get(fault - 1).getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        if (!opensAList(spoken, fault)) {
            return null;
        }
        // Every such list of the same statement is refused in turn, and the statement read on past the last.
        final char[] text = sql.toCharArray();
        final List<String> lines = new ArrayList<>();
        Token refused = null;
        for (int i = fault; i < spoken.size() && spoken.get(i).getType() != FrostlakeLexer.SEMI; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.RPAREN && spoken.get(i - 1).getType() == FrostlakeLexer.COMMA
                    && opensAList(spoken, i)) {
                refused = spoken.get(i);
                text[spoken.get(i - 1).getStartIndex()] = ' ';
                lines.add(SyntaxErrorListener.sentence(refused));
            }
        }
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(text))) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at != null && (at[0] > refused.getLine()
                    || at[0] == refused.getLine() && at[1] > refused.getCharPositionInLine())) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Whether the ')' at {@code close} ends a call's or an IN's list holding at least one argument. */
    private static boolean opensAList(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    final Token before = spoken.get(i - 1);
                    return i + 2 != close
                        && (before.getType() == FrostlakeLexer.IN || StarModifierResync.isName(before));
                }
                depth--;
            }
        }
        return false;
    }
}
