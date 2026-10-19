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
 * The lines live reports for a comma directly inside a CAST's parentheses, which take one operand and a type: the
 * comma, then — in a select list — the parenthesis that closes the CAST, and nothing more of the statement. A TRY_CAST
 * reads the same (all live-verified):
 *
 * <pre>
 *   SELECT CAST(1, 2) AS INT) x y FROM t        ',' at 13, then ')' at 16
 *   SELECT CAST(1, 2 AS INT)                    ',' at 13, then ')' at 23
 *   SELECT CAST(1, (2)) AS INT) FROM t          ',' at 13, then ')' at 18
 *   SELECT CAST(1,) FROM t                      ',' at 13, then ')' at 14
 *   SELECT CAST(1, 2                            ',' alone
 *   SELECT 1 FROM t WHERE CAST(1, 2) = 1        ',' alone: outside the select list nothing more
 *   SELECT TRY_CAST(1 AS INT, 2) FROM t         ',' at 24, then ')' at 27
 * </pre>
 */
final class CastCommaLines {

    private CastCommaLines() {
    }

    /**
     * The lines live reports when the parse's first line names a comma directly inside a select item's CAST, or null.
     *
     * @param spoken         the text's default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (!statementParse) {
            return null;
        }
        final List<String> nested = nestedLines(sql, spoken, faultIndex);
        if (nested != null) {
            return nested;
        }
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3 || spoken.get(fault).getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        final int open = enclosingParen(spoken, fault);
        final int keyword = open < 1 ? -1 : spoken.get(open - 1).getType();
        if ((keyword != FrostlakeLexer.CAST && keyword != FrostlakeLexer.TRY_CAST) || !inSelectList(spoken, open - 1)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        int depth = 0;
        for (int i = fault + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                    return lines;
                }
                depth--;
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return lines;
            }
        }
        return lines;
    }

    /**
     * The lines for a comma inside a cast that stands in a bracket of its clause — a call's arguments, a group, an
     * IN list, a window's OVER, another cast — in any clause of a query: the comma, and then the lines of the text
     * read with the cast's own parenthesis given up, as live reads on (live-verified). The bracket around the
     * cast then closes one parenthesis early, and the last one is refused: {@code SELECT UPPER(CAST(1, 2)) FROM
     * t} is ',' at 19 and ')' at 23, {@code SELECT UPPER(CAST(1, 2), 3) FROM t} ')' at 26, {@code … WHERE a IN
     * (CAST(1, 2), 3)} the ',' at 38. A cast that another cast's parentheses hold directly gives that cast up
     * too and is read on from its AS: {@code SELECT CAST(CAST(1, 2) AS INT) FROM t} is ',' at 18 and ')' at 29.
     */
    private static List<String> nestedLines(final String sql, final List<Token> spoken, final int faultIndex) {
        final int fault = sql == null ? -1 : CastFaultPlace.positionOf(spoken, faultIndex);
        if (fault < 3 || spoken.get(fault).getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        final int open = CastFaultPlace.enclosingParen(spoken, fault);
        if (open < 1 || !CastFaultPlace.isCast(spoken, open - 1)) {
            return null;
        }
        final int cast = open - 1;
        final int clause = CastFaultPlace.clauseOf(spoken, cast);
        if (clause < 0 || spoken.get(clause).getType() == FrostlakeLexer.ON
                || spoken.get(clause).getType() == FrostlakeLexer.FROM
                || CastFaultPlace.nesting(spoken, clause, cast) == 0) {
            return null;
        }
        final char[] text = sql.toCharArray();
        boolean typed = false;
        int depth = 0;
        for (int i = open + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            typed = typed || depth == 0 && type == FrostlakeLexer.AS;
        }
        if (typed) {
            // After its type the cast is whole: a value stands where it stood, and the comma goes on the list.
            CastFaultPlace.blanked(text, spoken, cast, fault - 1);
            text[spoken.get(cast).getStartIndex()] = '1';
        } else if (cast >= 2 && spoken.get(cast - 1).getType() == FrostlakeLexer.LPAREN
                && CastFaultPlace.isCast(spoken, cast - 2)) {
            final int outerAs = castAs(spoken, CastFaultPlace.closeOf(spoken, open), cast - 1);
            if (outerAs > 0) {
                CastFaultPlace.blanked(text, spoken, cast - 2, outerAs - 1);
                text[spoken.get(cast - 2).getStartIndex()] = '1';
            } else {
                CastFaultPlace.blanked(text, spoken, cast, open);
            }
        } else {
            CastFaultPlace.blanked(text, spoken, cast, open);
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            if (!lines.contains(line)) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The AS of the cast opened at {@code outerOpen}, when it directly follows the ')' at {@code innerClose}; or -1. */
    private static int castAs(final List<Token> spoken, final int innerClose, final int outerOpen) {
        return innerClose > outerOpen && innerClose + 1 < spoken.size()
            && spoken.get(innerClose + 1).getType() == FrostlakeLexer.AS ? innerClose + 1 : -1;
    }

    /** The index of the '(' that the token at {@code at} stands directly inside, or -1. */
    private static int enclosingParen(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** Whether the token at {@code at} stands in the select list of the query it belongs to. */
    private static boolean inSelectList(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth--;
            } else if (depth <= 0 && type == FrostlakeLexer.SELECT) {
                return true;
            } else if (depth <= 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.ON || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
                    || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.SEMI)) {
                return false;
            }
        }
        return false;
    }
}
