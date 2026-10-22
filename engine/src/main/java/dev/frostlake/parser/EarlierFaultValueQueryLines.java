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
 * The lines live reports when a statement's first fault stands before a query used as a value, which holds a fault
 * of its own (all live-verified): the earlier line is kept, then the query's SELECT is refused, and in a select list
 * the fault inside the query follows:
 *
 * <pre>
 *   SELECT 1 +, (SELECT 1 x y)                                          ',', 'SELECT', then 'y'
 *   SELECT 1 FROM t WHERE EXISTS (SELECT 1 x y) AND a = (SELECT 2 z w)  'y', then the second 'SELECT'
 * </pre>
 *
 * An EXISTS's query is no value; its own fault is a line as any other. A word run in before the query ends the report
 * instead: {@code SELECT 1 x y, (SELECT 2 z w)} is 'y' alone.
 */
final class EarlierFaultValueQueryLines {

    private EarlierFaultValueQueryLines() {
    }

    /**
     * The lines for such a text, or null.
     *
     * @param sql        the text
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null || spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        int fault = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return null;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        final boolean missingOperand = fault >= 1 && spoken.get(fault).getType() == FrostlakeLexer.COMMA
            && isOperator(spoken.get(fault - 1).getType());
        if (fault < 1 || insideAValueQuery(spoken, fault) || !insideExists(spoken, fault) && !missingOperand) {
            // A word run in ends the report: SELECT 1 x y, (SELECT 2 z w) is 'y' alone.
            return null;
        }
        // The first query used as a value after the fault: a select item's, or one compared, outside an EXISTS.
        int open = -1;
        for (int i = fault + 1; i + 1 < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i + 1).getType() == FrostlakeLexer.SELECT) {
                final int before = spoken.get(i - 1).getType();
                if (before == FrostlakeLexer.COMMA && inSelectList(spoken, i) || before == FrostlakeLexer.EQ) {
                    open = i;
                }
                break;
            }
        }
        if (open < 0) {
            return null;
        }
        final int close = closeOf(spoken, open);
        if (close < 0) {
            return null;
        }
        final Token select = spoken.get(open + 1);
        final String query = sql.substring(select.getStartIndex(), spoken.get(close).getStartIndex());
        final String inside = SyntaxErrorListener.firstLineReportedFor(query);
        if (inside == null || select.getLine() != spoken.get(close).getLine()) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(select));
        if (inSelectList(spoken, open)) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(inside);
            if (at == null || at[0] != 1) {
                return null;
            }
            lines.add(inside.replace(" at position " + at[1] + " ",
                " at position " + (at[1] + select.getCharPositionInLine()) + " "));
        }
        return lines;
    }

    /** Whether a position stands inside an EXISTS's query. */
    private static boolean insideExists(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return spoken.get(i - 1).getType() == FrostlakeLexer.EXISTS;
                }
                depth--;
            }
        }
        return false;
    }

    /** Whether a token is an infix operator, whose operand a comma leaves missing. */
    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }

    /** Whether a position stands inside a bracketed query other than an EXISTS's. */
    private static boolean insideAValueQuery(final List<Token> spoken, final int at) {
        final List<Integer> open = new ArrayList<>();
        for (int i = 0; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                open.add(Integer.valueOf(i));
            } else if (type == FrostlakeLexer.RPAREN && !open.isEmpty()) {
                open.remove(open.size() - 1);
            }
        }
        for (final Integer bracket : open) {
            final int i = bracket.intValue();
            if (i + 1 < spoken.size() && spoken.get(i + 1).getType() == FrostlakeLexer.SELECT
                    && (i == 0 || spoken.get(i - 1).getType() != FrostlakeLexer.EXISTS)) {
                return true;
            }
        }
        return false;
    }

    private static int closeOf(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a position stands in the select list, outside any bracket and before the FROM. */
    private static boolean inSelectList(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && type == FrostlakeLexer.FROM) {
                return false;
            }
        }
        return depth == 0;
    }
}
