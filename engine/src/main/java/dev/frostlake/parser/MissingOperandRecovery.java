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
 * The lines live reports when the query's FROM or WHERE is refused inside its own select list — right after an
 * arithmetic or concatenation operator that has no right operand, or inside plain parentheses: the clause keyword,
 * and then the first fault met once the select item is given up and the clause read as the query's own. This parser
 * named the keyword and stopped (all live-verified):
 *
 * <pre>
 *   SELECT 1 * FROM t)                 'FROM', then ')' at 17
 *   SELECT 1 * FROM t x y              'FROM', then 'y'
 *   SELECT 1 || FROM t WHERE a b       'FROM', then 'b'
 *   SELECT 1 * WHERE a)                'WHERE', then ')' at 18
 *   SELECT 1 * FROM                    'FROM', then '&lt;EOF&gt;'
 *   SELECT (1 FROM t)                  'FROM', then ')' at 18
 *   SELECT UPPER(1 * FROM t)           'FROM' alone: a call's parentheses keep it
 * </pre>
 *
 * <p>The reading is rebuilt on the text: the select item up to the keyword becomes a one-character item, every
 * position where it was written.
 */
final class MissingOperandRecovery {

    private MissingOperandRecovery() {
    }

    /**
     * The lines live reports when the parse's first line names such a FROM or WHERE, or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 2) {
            return null;
        }
        final Token keyword = spoken.get(fault);
        if (keyword.getType() != FrostlakeLexer.FROM && keyword.getType() != FrostlakeLexer.WHERE) {
            return null;
        }
        final int item = itemStart(spoken, fault);
        if (item < 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        final int start = spoken.get(item).getStartIndex();
        for (int c = start; c < keyword.getStartIndex() && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == start ? '1' : ' ';
            }
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(keyword));
        final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }

    /**
     * The index in {@code spoken} where the select item holding the keyword at {@code keyword} begins, or -1 unless
     * that item belongs to the select list of a query opening the text and the keyword stands either right after an
     * operator outside every bracket, or inside plain parentheses only: each opened after the SELECT, a comma, another
     * parenthesis or an operator, none of them a call's.
     */
    private static int itemStart(final List<Token> spoken, final int keyword) {
        if (spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return -1;
        }
        final List<Boolean> brackets = new ArrayList<>();
        int start = 1;
        for (int i = 1; i < keyword; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                final int before = spoken.get(i - 1).getType();
                brackets.add(Boolean.valueOf(before != FrostlakeLexer.SELECT && before != FrostlakeLexer.COMMA
                    && before != FrostlakeLexer.LPAREN && !isOperator(before)));
            } else if (type == FrostlakeLexer.RPAREN) {
                if (brackets.isEmpty()) {
                    return -1;
                }
                brackets.remove(brackets.size() - 1);
            } else if (brackets.isEmpty() && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.SEMI)) {
                return -1;
            } else if (brackets.isEmpty() && type == FrostlakeLexer.COMMA) {
                start = i + 1;
            }
        }
        if (brackets.isEmpty()) {
            return isOperator(spoken.get(keyword - 1).getType()) && start < keyword ? start : -1;
        }
        for (final Boolean call : brackets) {
            if (call.booleanValue()) {
                return -1;
            }
        }
        return start < keyword ? start : -1;
    }
}
