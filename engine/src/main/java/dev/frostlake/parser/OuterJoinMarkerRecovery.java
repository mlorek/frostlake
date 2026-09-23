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
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports for a '(' right after a finished value — a call's or a bracket's closing parenthesis, a
 * literal — that holds anything but the {@code (+)} outer-join marker it can only open there. Live refuses the token
 * after the '(' and resumes the expression at the first token after it that could follow the value: an arithmetic or
 * concatenation operator, a comma, a name read as the alias, AS, a clause keyword, the end. Every bracket the value
 * stood in is given up, so the rest is read at the level of the select item or the WHERE condition (all
 * live-verified):
 *
 * <pre>
 *   SELECT CONCAT('a') (1) + 2)            '1', then ')' at 26: + 2 reads on, the ')' is left over
 *   SELECT CONCAT('a') (1) x y             '1', then 'y': x is the alias
 *   SELECT CONCAT('a') (1)::INT x y        '1', then 'x': a cast is no place to resume, INT is the alias
 *   SELECT CONCAT('a') (1, 2) + 3)         '1', then ')' at 24: the comma carries on the select list
 *   SELECT CONCAT('a') (1)) + 2            '1' alone
 *   SELECT UPPER(CONCAT('a') (1) + 2)      '1', then ')' at 32: UPPER's bracket is given up too
 * </pre>
 *
 * <p>This parser reads the '(' as a statement of its own instead, so the resumption is rebuilt on the text: the
 * expression from its start up to the resuming token becomes a one-character value, every position stays where it
 * was written, and the lines after the refused token are what a parse of that text reports.
 */
final class OuterJoinMarkerRecovery {

    private OuterJoinMarkerRecovery() {
    }

    /**
     * The lines live reports when the parse's first line names the token right after such a '(', or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || sql.length() != sql.codePointCount(0, sql.length()) || spoken.isEmpty()
                || spoken.get(0).getType() == FrostlakeLexer.BEGIN || spoken.get(0).getType() == FrostlakeLexer.DECLARE) {
            return null;
        }
        int fault = -1;
        for (int i = 2; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0 || spoken.get(fault - 1).getType() != FrostlakeLexer.LPAREN
                || !finishesAValue(spoken.get(fault - 2)) || !mayBeRefused(spoken.get(fault).getType())) {
            return null;
        }
        final int start = expressionStart(spoken, fault - 1);
        if (start < 0) {
            return null;
        }
        final int resume = resumeToken(spoken, fault);
        if (resume < 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        final int from = spoken.get(start).getStartIndex();
        final int to = spoken.get(resume).getType() == Token.EOF
            ? text.length : spoken.get(resume).getStartIndex();
        for (int c = from; c < to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == from ? '1' : ' ';
            }
        }
        final Token refused = spoken.get(fault);
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Whether a token finishes a value that a '(' can only follow as an outer-join marker. */
    private static boolean finishesAValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.STRING_LITERAL
            || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL;
    }

    /** Whether the token after the '(' is one this rule speaks for: not the marker's '+', nor a query's opening. */
    private static boolean mayBeRefused(final int type) {
        return type != FrostlakeLexer.PLUS && type != FrostlakeLexer.SELECT && type != FrostlakeLexer.WITH
            && type != FrostlakeLexer.VALUES && type != FrostlakeLexer.LPAREN && type != Token.EOF;
    }

    /**
     * The index in {@code spoken} where the select item or WHERE condition holding the '(' at {@code open} begins —
     * after its query's SELECT, a comma of that select list, or the WHERE — or -1 elsewhere.
     */
    private static int expressionStart(final List<Token> spoken, final int open) {
        int depth = 0;
        int start = -1;
        for (int i = 0; i < open; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.COMMA && start >= 0)) {
                start = i + 1;
            } else if (depth == 0 && (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.FROM
                    || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.ORDER)) {
                start = -1;
            }
        }
        return start >= 0 && start < open ? start : -1;
    }

    /** The index in {@code spoken} of the first token after the refused one that the expression resumes at. */
    private static int resumeToken(final List<Token> spoken, final int fault) {
        final ATN atn = FrostlakeParser._ATN;
        final IntervalSet names = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]);
        for (int i = fault + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (names.contains(type) || resumesAt(type)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean resumesAt(final int type) {
        return type == Token.EOF || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.COMMA
            || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE
            || type == FrostlakeLexer.AS || type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
            || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
            || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.UNION
            || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW || type == FrostlakeLexer.INTERSECT;
    }
}
