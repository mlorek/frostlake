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
 * The lines live reports for a bracketed list written straight after a literal in a SELECT list (all live-verified).
 * Live reads the '(' as an outer-join marker, {@code (+)}, and refuses the first token inside it; when a comma
 * follows inside the bracket, its recovery leaves the marker there and reads the rest as the list's next items, so
 * the first fault of those is one more line:
 *
 * <pre>
 *   SELECT 1 (2, 3)            '2', then ')'
 *   SELECT 1 ('x', 'y')        ''x'', then ')'
 *   SELECT 1 (2, 3) FROM t1    '2', then ')'
 *   SELECT 1 (2)               '2'
 * </pre>
 */
final class JoinMarkerListLines {

    private JoinMarkerListLines() {
    }

    /**
     * The lines live reports when the parse's first line names the first token inside such a bracket, or null when
     * it names anything else or no comma follows inside the bracket.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param namedIndex     the token index of the token the parse's first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> refusal(final String sql, final List<Token> spoken, final int namedIndex,
                                final boolean statementParse) {
        if (sql == null || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        int named = -1;
        for (int i = 2; i < spoken.size() && named < 0; i++) {
            if (spoken.get(i).getTokenIndex() == namedIndex) {
                named = i;
            }
        }
        if (named < 0 || spoken.get(named - 1).getType() != FrostlakeLexer.LPAREN || !isLiteral(spoken.get(named - 2))
                || !atTopOfSelectList(spoken, named - 1)) {
            return null;
        }
        int comma = -1;
        int depth = 0;
        for (int i = named; i < spoken.size() && comma < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth < 0 || type == Token.EOF) {
                return null;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                comma = i;
            }
        }
        if (comma < 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        text[spoken.get(named - 1).getStartIndex()] = ',';
        for (int c = spoken.get(named).getStartIndex(); c <= spoken.get(comma).getStopIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(named)));
        final Token after = spoken.get(comma);
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), statementParse)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > after.getLine()
                    || place[0] == after.getLine() && place[1] > after.getCharPositionInLine())) {
                lines.add(line);
                break;
            }
        }
        return lines;
    }

    private static boolean isLiteral(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    /** Whether the token at {@code at} stands in the SELECT list of a statement opened by SELECT, outside brackets. */
    private static boolean atTopOfSelectList(final List<Token> spoken, final int at) {
        int start = 0;
        for (int i = 0; i < at; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                start = i + 1;
            }
        }
        if (spoken.get(start).getType() != FrostlakeLexer.SELECT) {
            return false;
        }
        int depth = 0;
        for (int i = start + 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.SELECT)) {
                return false;
            }
        }
        return depth == 0;
    }
}
