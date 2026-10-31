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
 * The lines live reports when an operator's operand is missing at the ')' that closes a plain bracket: the ')' is
 * refused and the statement is read on as if the operand stood there, so a later fault is told and the brackets around
 * close as written (all live-verified):
 *
 * <pre>
 *   SELECT ABS((1 +)) FROM t                ')' alone
 *   SELECT ABS((1 *)) FROM t WHERE a b      ')', then 'b'
 *   BEGIN SELECT ABS((1 +)) FROM t; RETURN 1 1; END    ')', then the later statement's '1'
 * </pre>
 */
final class BracketOperandLines {

    private BracketOperandLines() {
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
        int fault = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        if (sql == null || fault < 3 || spoken.get(fault).getType() != FrostlakeLexer.RPAREN
                || !isOperator(spoken.get(fault - 1).getType())) {
            return null;
        }
        int depth = 0;
        int open = -1;
        for (int i = fault - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    open = i;
                    break;
                }
                depth--;
            }
        }
        if (open < 1 || spoken.get(open - 1).getType() != FrostlakeLexer.LPAREN) {
            // Only a plain bracket inside another is read so; a call's own bracket keeps the parse's lines.
            return null;
        }
        final Token operator = spoken.get(fault - 1);
        final char[] text = sql.toCharArray();
        for (int i = operator.getStartIndex(); i <= operator.getStopIndex(); i++) {
            text[i] = ' ';
        }
        final Token refused = spoken.get(fault);
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(text))) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at != null && (at[0] > refused.getLine()
                    || at[0] == refused.getLine() && at[1] > refused.getCharPositionInLine())) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }
}
