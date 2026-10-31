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
 * The lines live reports when a script's first statement is refused at its semicolon, an operator's operand missing
 * there, and a scripting block follows: the semicolon, then the block's own lines as it reports them alone (all
 * live-verified):
 *
 * <pre>
 *   SELECT 1 +; BEGIN SELECT 1 x y; END       ';', then 'y' and 'END'
 *   SELECT 1 +; BEGIN SELECT 1                ';', then the end of the input
 *   SELECT 1 +; DECLARE                       ';', then the end of the input
 * </pre>
 */
final class LaterBlockLines {

    private LaterBlockLines() {
    }

    /**
     * The lines for such a text, or null for any other.
     *
     * @param sql        the text
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int semi = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                semi = i;
                break;
            }
        }
        if (semi < 1 || semi + 1 >= spoken.size() || spoken.get(semi).getTokenIndex() != faultIndex
                || !isOperator(spoken.get(semi - 1).getType())) {
            return null;
        }
        final Token opener = spoken.get(semi + 1);
        final int after = semi + 2 < spoken.size() ? spoken.get(semi + 2).getType() : Token.EOF;
        final boolean block = opener.getType() == FrostlakeLexer.DECLARE
            || opener.getType() == FrostlakeLexer.BEGIN && after != FrostlakeLexer.WORK
                && after != FrostlakeLexer.TRANSACTION && after != FrostlakeLexer.NAME && after != FrostlakeLexer.SEMI;
        if (!block) {
            return null;
        }
        final Token separator = spoken.get(semi);
        final int from = separator.getStopIndex() + 1;
        if (from > sql.length()) {
            return null;
        }
        final List<String> blockLines = SyntaxErrorListener.linesReportedAfresh(sql.substring(from));
        if (blockLines.isEmpty()) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(separator));
        for (final String line : blockLines) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            if (at == null) {
                return null;
            }
            final int lineNumber = at[0] + separator.getLine() - 1;
            final int position = at[0] == 1 ? at[1] + separator.getCharPositionInLine() + 1 : at[1];
            lines.add(line.replace("syntax error line " + at[0] + " at position " + at[1] + " ",
                "syntax error line " + lineNumber + " at position " + position + " "));
        }
        return lines;
    }

    /** Whether a token is an infix operator, after which a statement's semicolon is the missing operand's place. */
    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE
            || type == FrostlakeLexer.EQ || type == FrostlakeLexer.LT || type == FrostlakeLexer.GT
            || type == FrostlakeLexer.AND || type == FrostlakeLexer.OR;
    }
}
