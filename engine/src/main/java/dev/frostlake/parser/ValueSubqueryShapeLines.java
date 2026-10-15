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
 * The lines the account reports for a fault inside a query used as a value, in the shapes where what follows the
 * query's SELECT is the account's own (all live-verified, for a script's first statement that is a SELECT; see
 * {@link ValueSubqueryLines} for the rule these depart from):
 *
 * <pre>
 *   SELECT (SELECT a x y FROM t)                    'SELECT', then 'x': the word after a name
 *   SELECT (SELECT t.a x y FROM t)                  'SELECT', then the '.'
 *   SELECT (SELECT 1 2)                             'SELECT' alone: a literal after a literal is not told
 *   SELECT (SELECT * FROM t WHERE a = )             'SELECT', 'FROM', then the fault
 *   SELECT CAST((SELECT 1 x y) AS INT)              'SELECT', the fault, then the cast's ')'
 *   SELECT CASE WHEN (SELECT 1 x y) = 1 THEN 1 END  'SELECT', the fault, then the query's ')'
 *   SELECT CASE (SELECT 1 x y) WHEN 1 THEN 1 END    'SELECT', then the query's ')'
 *   SELECT (WITH c AS (SELECT 1 x y) SELECT 1 FROM c)   'WITH', then its 'AS'
 *   … WHERE 1 = (WITH c AS (SELECT 1 x y) SELECT 1 FROM c) 'WITH', then the SELECT of its first body
 *   … WHERE a IN ((SELECT 1 x y), 2)                the 'SELECT' of each faulty query of the list, and no more
 * </pre>
 */
final class ValueSubqueryShapeLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private ValueSubqueryShapeLines() {
    }

    /**
     * The lines of a text whose first statement is a SELECT holding such a query around its first fault, or null.
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
        int end = 0;
        while (spoken.get(end).getType() != FrostlakeLexer.SEMI && spoken.get(end).getType() != Token.EOF) {
            end++;
        }
        final List<Integer> open = new ArrayList<>();
        boolean from = false;
        boolean where = false;
        for (int q = 1; q + 2 < end; q++) {
            final int type = spoken.get(q).getType();
            if (type == FrostlakeLexer.RPAREN) {
                if (open.isEmpty()) {
                    return null;
                }
                open.remove(open.size() - 1);
                continue;
            }
            if (open.isEmpty() && type == FrostlakeLexer.FROM) {
                from = true;
            } else if (open.isEmpty() && type == FrostlakeLexer.WHERE) {
                where = true;
            }
            if (type != FrostlakeLexer.LPAREN) {
                continue;
            }
            final int inside = spoken.get(q + 1).getType();
            if (inside != FrostlakeLexer.SELECT && inside != FrostlakeLexer.WITH) {
                open.add(Integer.valueOf(q));
                continue;
            }
            final int close = closing(spoken, q, end);
            if (close < 0) {
                return null;
            }
            if (where && !open.isEmpty() && spoken.get(open.get(open.size() - 1).intValue() - 1).getType()
                    == FrostlakeLexer.IN && open.size() == 1) {
                return inList(sql, spoken, open.get(0).intValue(), end);
            }
            final Token fault = faultIn(spoken, q, close, faultIndex);
            if (fault == null) {
                return null;
            }
            if (where && open.isEmpty() && inside == FrostlakeLexer.WITH && isName(spoken.get(q + 2).getType())
                    && spoken.get(q + 3).getType() == FrostlakeLexer.AS
                    && spoken.get(q + 4).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(q + 5).getType() == FrostlakeLexer.SELECT) {
                // In a condition a WITH is told with the SELECT of its first body.
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(q + 1)));
                return with(lines, spoken.get(q + 5));
            }
            if (from || where) {
                return null;
            }
            return inSelectList(spoken, q, close, open, fault);
        }
        return null;
    }

    /** The first query of a select list, by what stands around it and how it opens. */
    private static List<String> inSelectList(final List<Token> spoken, final int q, final int close,
                                             final List<Integer> open, final Token fault) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(q + 1)));
        final int before = spoken.get(q - 1).getType();
        if (spoken.get(q + 1).getType() == FrostlakeLexer.WITH) {
            if (!open.isEmpty() || !isName(spoken.get(q + 2).getType())
                    || spoken.get(q + 3).getType() != FrostlakeLexer.AS) {
                return null;
            }
            return with(lines, spoken.get(q + 3));
        }
        if (open.size() == 1 && open.get(0).intValue() == q - 1 && q >= 3
                && (spoken.get(q - 2).getType() == FrostlakeLexer.CAST
                    || spoken.get(q - 2).getType() == FrostlakeLexer.TRY_CAST)) {
            final int castClose = closing(spoken, q - 1, spoken.size() - 1);
            return castClose < 0 ? null : with(with(lines, fault), spoken.get(castClose));
        }
        if (!open.isEmpty()) {
            return null;
        }
        if (before == FrostlakeLexer.CASE) {
            return with(lines, spoken.get(close));
        }
        if (before == FrostlakeLexer.WHEN || isComparison(before) && q >= 3
                && spoken.get(q - 3).getType() == FrostlakeLexer.WHEN) {
            return with(with(lines, fault), spoken.get(close));
        }
        if (before != FrostlakeLexer.SELECT && before != FrostlakeLexer.COMMA) {
            return null;
        }
        final int first = spoken.get(q + 2).getType();
        final int second = spoken.get(q + 3).getType();
        if (first == FrostlakeLexer.STAR && second == FrostlakeLexer.FROM) {
            return with(with(lines, spoken.get(q + 3)), fault);
        }
        if (isLiteral(first) && isLiteral(second)) {
            return lines;
        }
        if (isName(first) && isName(second) && q + 4 < close && isName(spoken.get(q + 4).getType())) {
            return with(lines, spoken.get(q + 3));
        }
        if (isName(first) && second == FrostlakeLexer.DOT && q + 6 < close && isName(spoken.get(q + 4).getType())
                && isName(spoken.get(q + 5).getType()) && isName(spoken.get(q + 6).getType())) {
            return with(lines, spoken.get(q + 3));
        }
        return null;
    }

    /** A WHERE's IN list: the SELECT of each query of it that does not read, and nothing else. */
    private static List<String> inList(final String sql, final List<Token> spoken, final int list, final int end) {
        final int listClose = closing(spoken, list, end);
        if (listClose < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        int at = list + 1;
        while (at < listClose) {
            if (spoken.get(at).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(at + 1).getType() == FrostlakeLexer.SELECT) {
                final int close = closing(spoken, at, end);
                if (close < 0) {
                    return null;
                }
                final char[] text = sql.toCharArray();
                for (int c = 0; c < text.length; c++) {
                    if ((c < spoken.get(at + 1).getStartIndex() || c > spoken.get(close - 1).getStopIndex())
                            && text[c] != '\n' && text[c] != '\r') {
                        text[c] = ' ';
                    }
                }
                if (!SyntaxErrorListener.linesReportedAfresh(new String(text)).isEmpty()) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
                }
                at = close + 1;
            } else {
                at++;
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    /** The parse's first fault when it stands inside the brackets from {@code open} to {@code close}, or null. */
    private static Token faultIn(final List<Token> spoken, final int open, final int close, final int faultIndex) {
        for (int i = open + 1; i <= close; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                return spoken.get(i);
            }
        }
        return null;
    }

    private static List<String> with(final List<String> lines, final Token token) {
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }

    /** The ')' that closes the bracket at {@code open}, or -1. */
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

    private static boolean isComparison(final int type) {
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE;
    }

    private static boolean isLiteral(final int type) {
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
