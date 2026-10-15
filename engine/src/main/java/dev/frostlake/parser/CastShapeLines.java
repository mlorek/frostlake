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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines the account reports after a fault inside a CAST or TRY_CAST, by where the cast stands, for the places
 * {@link CastCommaLines} and {@link CastFaultLines} do not read (all live-verified, each for a text that is one
 * SELECT):
 *
 * <pre>
 *   … LIMIT CAST(1, 2)                          'CAST' twice, whatever the cast holds
 *   … WHERE CAST(1, 2) = 1 AND a b              ',', then 'b': the condition is read on past the cast
 *   … ORDER BY a, CAST(1, 2)                    the cast's ',', then its ')' — and so in a GROUP BY
 *   SELECT CASE CAST(1, 2) WHEN 1 THEN …        ',', then the token after the WHEN
 *   SELECT CASE WHEN UPPER(CAST(1, 2)) = 1 THEN …   ',', the call's ')', then the THEN
 *   SELECT UPPER(CAST(1, 2 AS INT))             ',', 'AS', then the cast's ')'
 *   SELECT (SELECT CAST(1, 2))                  the query's SELECT, then the '(' of its first call
 *   SELECT * FROM (VALUES (CAST(1, 2)))         'VALUES', then the token after the comma
 *   SELECT CAST(1 2) + 1 FROM t                 '2', then 'FROM' — and so for a cast inside another cast
 *   SELECT a, CAST(b AS INT FROM t WHERE a b    'FROM', then 'b': the statement is read on past the open cast
 *   SELECT TOP 1 a, (1 2) FROM t                '2' alone: after TOP n nothing more is told
 * </pre>
 */
final class CastShapeLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private CastShapeLines() {
    }

    /**
     * The lines of a text that is one SELECT holding such a cast, or null.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1 for the end of the input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null || spoken.size() < 6 || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final int end = spoken.size() - 1;
        int fault = -1;
        for (int i = 1; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN) {
                return null;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (spoken.get(1).getType() == FrostlakeLexer.TOP && fault > 3 && isLiteral(spoken.get(fault).getType())
                && isLiteral(spoken.get(fault - 1).getType()) && beforeFrom(spoken, fault)) {
            return told(spoken.get(fault));
        }
        for (int cast = 1; cast + 3 < end; cast++) {
            final int type = spoken.get(cast).getType();
            if ((type != FrostlakeLexer.CAST && type != FrostlakeLexer.TRY_CAST)
                    || spoken.get(cast + 1).getType() != FrostlakeLexer.LPAREN) {
                continue;
            }
            if (fault >= 0 && fault < cast
                    && (fault != cast - 1 || spoken.get(fault).getType() != FrostlakeLexer.COMMA)) {
                // An earlier fault is another matter — but for the comma of the list the cast is an item of.
                return null;
            }
            if (spoken.get(cast - 1).getType() == FrostlakeLexer.LIMIT) {
                final List<String> lines = told(spoken.get(cast));
                lines.add(lines.get(0));
                return lines;
            }
            final List<String> lines = forCast(sql, spoken, cast, end);
            if (lines != null) {
                return lines;
            }
        }
        return null;
    }

    private static List<String> forCast(final String sql, final List<Token> spoken, final int cast, final int end) {
        final int open = cast + 1;
        final int close = closing(spoken, open, end);
        final int second = spoken.get(open + 2).getType();
        if (close < 0) {
            // The cast is never closed: the FROM inside it is refused, and the statement read on from there.
            for (int i = open + 1; i < end; i++) {
                final int type = spoken.get(i).getType();
                if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN) {
                    return null;
                }
                if (type == FrostlakeLexer.FROM) {
                    final List<String> lines = told(spoken.get(i));
                    lines.addAll(readOn(sql, spoken, cast, i - 1, spoken.get(i)));
                    return lines;
                }
            }
            return null;
        }
        if (second == FrostlakeLexer.COMMA && isOperand(spoken.get(open + 1).getType())) {
            return afterComma(sql, spoken, cast, open + 2, close, end);
        }
        if (isLiteral(spoken.get(open + 1).getType()) && isLiteral(second) && close == open + 3) {
            // CAST(1 2): an operand of something more adds the statement's FROM.
            final int after = spoken.get(close + 1).getType();
            final boolean nested = cast >= 2 && spoken.get(cast - 1).getType() == FrostlakeLexer.LPAREN
                && (spoken.get(cast - 2).getType() == FrostlakeLexer.CAST
                    || spoken.get(cast - 2).getType() == FrostlakeLexer.TRY_CAST);
            // Only a cast that is a select item's own operand, or stands straight inside such a cast.
            if (depthOf(spoken, cast) != (nested ? 1 : 0) || !nested && !isOperator(after)
                    || !beforeFrom(spoken, cast)) {
                return null;
            }
            for (int i = close + 1; i < end; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.FROM) {
                    final List<String> lines = told(spoken.get(open + 2));
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                    return lines;
                }
            }
        }
        return null;
    }

    /** A cast holding a comma, by what stands before it. */
    private static List<String> afterComma(final String sql, final List<Token> spoken, final int cast,
                                           final int comma, final int close, final int end) {
        final List<String> lines = told(spoken.get(comma));
        final int before = spoken.get(cast - 1).getType();
        final boolean called = before == FrostlakeLexer.LPAREN && cast >= 3 && isName(spoken.get(cast - 2).getType());
        for (int i = comma + 1; called && i < close; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.AS) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
                return lines;
            }
        }
        if (before == FrostlakeLexer.CASE) {
            for (int i = close + 1; i + 1 < end; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.WHEN) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                    return lines;
                }
            }
            return null;
        }
        if (called && spoken.get(cast - 3).getType() == FrostlakeLexer.WHEN
                && spoken.get(close + 1).getType() == FrostlakeLexer.RPAREN) {
            for (int i = close + 2; i < end; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.THEN) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                    return lines;
                }
            }
            return null;
        }
        // A scalar subquery's first item: its SELECT, then the '(' of the item's first call.
        final int item = called ? cast - 2 : cast;
        if (item >= 2 && spoken.get(item - 1).getType() == FrostlakeLexer.SELECT
                && spoken.get(item - 2).getType() == FrostlakeLexer.LPAREN && beforeFrom(spoken, item - 2)) {
            final List<String> query = told(spoken.get(item - 1));
            query.add(SyntaxErrorListener.sentence(spoken.get(item + 1)));
            return query;
        }
        if (before == FrostlakeLexer.LPAREN && cast >= 2 && spoken.get(cast - 2).getType() == FrostlakeLexer.VALUES) {
            final List<String> values = told(spoken.get(cast - 2));
            values.add(SyntaxErrorListener.sentence(spoken.get(comma + 1)));
            return values;
        }
        final int clause = depthOf(spoken, cast) == 0 ? clauseOf(spoken, cast) : Token.EOF;
        if (clause == FrostlakeLexer.BY) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
            return lines;
        }
        if (clause == FrostlakeLexer.WHERE) {
            lines.addAll(readOn(sql, spoken, cast, close, spoken.get(comma)));
            return lines;
        }
        return null;
    }

    /**
     * The lines of the text with the tokens from {@code first} to {@code last} read as one value, those after
     * {@code told} alone.
     */
    private static List<String> readOn(final String sql, final List<Token> spoken, final int first, final int last,
                                       final Token told) {
        final char[] text = sql.toCharArray();
        for (int c = spoken.get(first).getStartIndex(); c <= spoken.get(last).getStopIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        text[spoken.get(first).getStartIndex()] = '1';
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(told));
        final List<String> lines = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(text))) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && at != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The clause the token at {@code at} stands in at the statement's own level: WHERE, BY, or another. */
    private static int clauseOf(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth--;
            } else if (depth <= 0 && (type == FrostlakeLexer.WHERE || type == FrostlakeLexer.BY
                    || type == FrostlakeLexer.FROM || type == FrostlakeLexer.SELECT || type == FrostlakeLexer.HAVING
                    || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.LIMIT)) {
                return depth == 0 ? type : Token.EOF;
            }
        }
        return Token.EOF;
    }

    /** Whether the token at {@code at} stands before the statement's own FROM. */
    private static boolean beforeFrom(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.FROM) {
                return false;
            }
        }
        return true;
    }

    private static List<String> told(final Token token) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }

    /** The ')' that closes the bracket at {@code open}, or -1 when it never closes. */
    private static int closing(final List<Token> spoken, final int open, final int end) {
        int depth = 0;
        for (int i = open; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** How many brackets are open where the token at {@code at} stands. */
    private static int depthOf(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        return depth;
    }

    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PIPE_PIPE || type == FrostlakeLexer.PERCENT;
    }

    private static boolean isOperand(final int type) {
        return isLiteral(type) || isName(type);
    }

    private static boolean isLiteral(final int type) {
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
