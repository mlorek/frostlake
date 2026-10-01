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
 * The lines live reports for an IN whose list this parser could not read. Unable to take the predicate, the parse
 * ends the clause before the IN and refuses the IN itself; live reads the IN and refuses the first token its list
 * cannot take (all live-verified):
 *
 * <pre>
 *   SELECT a FROM t WHERE a IN (                        '&lt;EOF&gt;' at 28
 *   SELECT a FROM t WHERE a IN (1 2)                    '2' at 30
 *   SELECT a FROM t WHERE a IN ()                       ')' at 28
 *   SELECT a FROM t WHERE a IN (SELECT 1 FROM)          'SELECT' at 28
 *   SELECT a FROM t WHERE a IN (SELECT 1 foo (2))       'SELECT' at 28, then ')' at 44
 * </pre>
 *
 * <p>A list is read the way an operand in brackets is, so the text is parsed again with the IN spelled as an equals
 * sign followed by a blank, every position where it was written. A list holding a subquery that cannot be read is
 * refused at its SELECT or WITH instead; live's recovery then closes the list at the first ')' after it, and a fault
 * after that still speaks. Where no operator may stand either, the line falls at the IN's own place, and names the IN
 * as written ({@code SHOW VARIABLES IN ACCOUNT} is {@code unexpected 'IN'} at 15), never the equals sign read in its
 * stead.
 */
final class InListRecovery {

    private InListRecovery() {
    }

    /**
     * The lines live reports when the parse's one line names an IN, or null when it names anything else or the text
     * reads cleanly with the IN taken.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param namedIndex     the token index of the token the parse's line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> refusal(final String sql, final List<Token> spoken, final int namedIndex,
                                final boolean statementParse) {
        if (sql == null || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        int in = -1;
        for (int i = 1; i + 1 < spoken.size() && in < 0; i++) {
            if (spoken.get(i).getTokenIndex() == namedIndex && spoken.get(i).getType() == FrostlakeLexer.IN) {
                in = i;
            }
        }
        if (in < 0 || spoken.get(in).getStopIndex() != spoken.get(in).getStartIndex() + 1) {
            return null;
        }
        final char[] text = sql.toCharArray();
        text[spoken.get(in).getStartIndex()] = '=';
        text[spoken.get(in).getStartIndex() + 1] = ' ';
        final List<String> lines = new ArrayList<>();
        final int opener = in + 2;
        if (spoken.get(in + 1).getType() == FrostlakeLexer.LPAREN && opener < spoken.size()
                && (spoken.get(opener).getType() == FrostlakeLexer.SELECT
                    || spoken.get(opener).getType() == FrostlakeLexer.WITH)) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(opener)));
            int close = -1;
            for (int i = opener + 1; i < spoken.size() && close < 0; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                    close = i;
                }
            }
            if (close < 0) {
                return lines;
            }
            text[spoken.get(in + 1).getStartIndex()] = '1';
            for (int i = opener; i <= close; i++) {
                for (int c = spoken.get(i).getStartIndex(); c <= spoken.get(i).getStopIndex(); c++) {
                    if (text[c] != '\n' && text[c] != '\r') {
                        text[c] = ' ';
                    }
                }
            }
            final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
            for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), statementParse)) {
                final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
                if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                    lines.add(line);
                }
            }
            return lines;
        }
        final String inPlace = SyntaxErrorListener.sentence(spoken.get(in));
        final int[] inAt = SyntaxErrorListener.sentenceCoordinates(inPlace);
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), statementParse)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            lines.add(place != null && inAt != null && place[0] == inAt[0] && place[1] == inAt[1] ? inPlace : line);
        }
        return lines.isEmpty() ? null : lines;
    }
}
