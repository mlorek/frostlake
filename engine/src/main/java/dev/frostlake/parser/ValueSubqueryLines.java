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
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports for a fault inside a parenthesized query used as a value — a scalar subquery, or the
 * query of an IN, ANY, ALL or SOME — as an operand, a call's argument or a comparison's side. Live first refuses
 * that query's SELECT, once for each such query around the fault, and then depends on where the query stands (all
 * live-verified):
 *
 * <pre>
 *   SELECT (SELECT 1 x y) FROM t; SELECT 1 x y     'SELECT' at 8, 'y' at 19: in a select list the fault inside is
 *                                                  reported next, and the report ends
 *   SELECT (SELECT (SELECT 1 x y))                 'SELECT' at 8 and at 16, then 'y'
 *   SELECT (SELECT 1 +), SELECT (SELECT)           'SELECT' alone: the query's own ')' is no line after an
 *                                                  operator, a FROM or the SELECT
 *   SELECT (SELECT 1 FROM t WHERE)                 'SELECT', then the ')': it is one after WHERE, JOIN and BY
 *   SELECT UPPER((SELECT 1 x y))                   'SELECT' alone: a call's argument, or inside three brackets
 *   SELECT COALESCE((SELECT 1 x y), 1)             'SELECT', then the call's ')' when more of the call follows
 *   SELECT 1 FROM t WHERE a = (SELECT 1 x y); …    'SELECT' alone, and the later statements are read: in a WHERE,
 *                                                  HAVING, QUALIFY, GROUP BY, ORDER BY, an UPDATE's SET, a VALUES
 *                                                  row or a CALL the fault inside is not reported
 *   … WHERE a = (SELECT 1 x y) OR a = (SELECT 2 z w)   both SELECTs
 * </pre>
 *
 * <p>A query whose first item is a call is read another way, wherever its fault stands, and with no faulty query
 * nested in it:
 *
 * <pre>
 *   SELECT (SELECT SUM(n IGNORE NULLS) FROM t)          'SELECT', then the call's '(': in a select list
 *   SELECT (SELECT ABS(1) 2 3), SELECT (SELECT SUM(n) FROM t u v)    the same, the fault past the call
 *   … WHERE a = (SELECT ABS(1 2)), … HAVING a = (…)     'SELECT', then '1': compared, the first token in the call
 *   … WHERE a &gt; (SELECT COUNT(x =&gt; *, 1) FROM t)          'SELECT', 'x', then the ',' after the star
 *   … WHERE a IN (SELECT SUM(n IGNORE NULLS) FROM t)    'SELECT', then the 'FROM': an IN's list, the token past
 *                                                       the call
 * </pre>
 *
 * <p>A derived table, a common table expression's body and an EXISTS are no such query: their own fault is the
 * line. The readings are rebuilt on the text — the query parsed on its own for the fault inside, the statement
 * with the query replaced by a value for what follows — every position staying where it was written.
 */
final class ValueSubqueryLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    /** Where a faulty query stands: a select list, a call's argument there, too deep in brackets, or another clause. */
    private static final int ITEM = 0;
    private static final int ARGUMENT = 1;
    private static final int DEEP = 2;
    private static final int CLAUSE = 3;

    private ValueSubqueryLines() {
    }

    /**
     * The lines live reports when the statement holding the parse's first fault holds a faulty query used as a value
     * with no fault before it, or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names, or -1 for the end of the input
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || spoken.size() < 4) {
            return null;
        }
        int fault = spoken.size() - 1;
        for (int i = 0; i < spoken.size() && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        int start = fault;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        int end = fault;
        while (end < spoken.size() - 1 && spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            end++;
        }
        final int[] found = faultyQuery(sql, spoken, start, end, start);
        if (found == null) {
            return null;
        }
        final int open = found[0];
        final int close = found[1];
        final int place = found[2];
        // The statement with the query replaced by a value: a fault before the query is not this reading's.
        final char[] valued = sql.toCharArray();
        blank(valued, spoken.get(open + 1).getStartIndex(), spoken.get(close - 1).getStopIndex());
        valued[spoken.get(open + 1).getStartIndex()] = '1';
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(spoken.get(open)));
        final List<String> after = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(valued))) {
            final int[] where = SyntaxErrorListener.sentenceCoordinates(line);
            if (where == null || where[0] < at[0] || where[0] == at[0] && where[1] < at[1]) {
                return null;
            }
            after.add(line);
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
        final int firstCall = firstItemCall(spoken, open + 1, close);
        if (firstCall >= 0 && faultyQuery(sql, spoken, open + 2, close, -1) == null) {
            final List<String> called = afterCallFirst(spoken, open, close, place, found[4], firstCall);
            if (called != null) {
                lines.addAll(called);
                return lines;
            }
        }
        if (place == CLAUSE) {
            // The queries inside it, each refused at its SELECT, then what follows the query.
            addNested(sql, spoken, open, close, lines);
            lines.addAll(after);
            return lines;
        }
        if (place == DEEP) {
            return lines;
        }
        if (place == ARGUMENT) {
            final int call = found[3];
            final int callClose = closing(spoken, call, end);
            if (callClose > close + 1 && callClose < end) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(callClose)));
            }
            return lines;
        }
        // The fault inside, read from the innermost faulty query parsed on its own.
        int innerOpen = open;
        int innerClose = close;
        int[] deeper = faultyQuery(sql, spoken, innerOpen + 2, innerClose, -1);
        while (deeper != null) {
            innerOpen = deeper[0];
            innerClose = deeper[1];
            lines.add(SyntaxErrorListener.sentence(spoken.get(innerOpen + 1)));
            deeper = faultyQuery(sql, spoken, innerOpen + 2, innerClose, -1);
        }
        final char[] alone = new char[sql.length()];
        Arrays.fill(alone, ' ');
        final int from = spoken.get(innerOpen + 1).getStartIndex();
        final int to = spoken.get(innerClose - 1).getStopIndex();
        for (int c = 0; c < alone.length; c++) {
            final char written = sql.charAt(c);
            if (c >= from && c <= to || written == '\n' || written == '\r') {
                alone[c] = written;
            }
        }
        final boolean closed = spoken.get(innerClose).getType() == FrostlakeLexer.RPAREN;
        final int last = spoken.get(innerClose - 1).getType();
        final List<String> inside = SyntaxErrorListener.linesReportedFor(new String(alone), true);
        if (!inside.isEmpty() && innerOpen + 2 < spoken.size()
                && inside.get(0).equals(SyntaxErrorListener.sentence(spoken.get(innerOpen + 2)))) {
            // A query with no select list is refused at its SELECT alone, and its ')' is one more line unless the
            // FROM or the SELECT stands right before it: (SELECT FROM t) is the SELECT and the ')', (SELECT FROM)
            // the SELECT (live-verified).
            if (closed && last != FrostlakeLexer.FROM && last != FrostlakeLexer.SELECT) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(innerClose)));
            }
            return lines;
        }
        for (final String line : inside) {
            if (!line.contains("'<EOF>'")) {
                lines.add(line);
            } else if (!closed) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(innerClose)));
            } else if (last == FrostlakeLexer.WHERE || last == FrostlakeLexer.JOIN || last == FrostlakeLexer.BY) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(innerClose)));
            }
        }
        return lines;
    }

    /** Adds the SELECT of each faulty value query nested in the one at {@code open}, outermost first. */
    private static void addNested(final String sql, final List<Token> spoken, final int open, final int close,
                                  final List<String> lines) {
        int[] deeper = faultyQuery(sql, spoken, open + 2, close, -1);
        while (deeper != null) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(deeper[0] + 1)));
            deeper = faultyQuery(sql, spoken, deeper[0] + 2, deeper[1], -1);
        }
    }

    /**
     * The first query used as a value among the tokens from {@code start} up to {@code end} that does not parse:
     * the positions of its '(' and of its ')' (or of the token that ends the statement when it is not closed), where
     * it stands, and the call's '(' for an argument — or null. A bracket at {@code statementStart} opens the
     * statement itself and is no value.
     */
    private static int[] faultyQuery(final String sql, final List<Token> spoken, final int start, final int end,
                                     final int statementStart) {
        // Per open bracket: its position, whether it is plain (0), a call's (1), a query's (2) or a CAST's (3), and
        // the clause last opened at its level.
        final List<int[]> frames = new ArrayList<>();
        frames.add(new int[] {-1, 2, -1});
        for (int i = start; i < end; i++) {
            final int type = spoken.get(i).getType();
            final int[] top = frames.get(frames.size() - 1);
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                if (frames.size() > 1) {
                    frames.remove(frames.size() - 1);
                }
                continue;
            }
            if (isClause(type)) {
                top[2] = type;
                continue;
            }
            if (type != FrostlakeLexer.LPAREN && type != FrostlakeLexer.LBRACKET && type != FrostlakeLexer.LBRACE) {
                continue;
            }
            final int before = i == 0 ? Token.EOF : spoken.get(i - 1).getType();
            final boolean query = type == FrostlakeLexer.LPAREN && i + 1 < end
                && spoken.get(i + 1).getType() == FrostlakeLexer.SELECT;
            if (query && isValuePlace(before, governing(frames), i == statementStart)) {
                final int close = closing(spoken, i, end);
                if (close > i + 1 && !parses(sql, spoken, i + 1, close - 1)) {
                    final int[] place = placeOf(frames);
                    return place == null ? null : new int[] {i, close, place[0], place[1], place[2]};
                }
            }
            final int kind;
            if (type != FrostlakeLexer.LPAREN) {
                kind = 0;
            } else if (WORDS.contains(before) || before == FrostlakeLexer.QUOTED_IDENTIFIER) {
                kind = 1;
            } else if (before == FrostlakeLexer.CAST || before == FrostlakeLexer.TRY_CAST) {
                kind = 3;
            } else {
                kind = query ? 2 : 0;
            }
            frames.add(new int[] {i, kind, -1});
        }
        return null;
    }

    /**
     * Where a value query opened under the given frames stands — a select list, a call's argument in one, more than
     * one bracket deep in one, or another clause — and the position of the call's '(' for an argument; null inside a
     * CAST, a join's ON or a LIMIT, whose readings are their own.
     */
    private static int[] placeOf(final List<int[]> frames) {
        int level = frames.size() - 1;
        while (level > 0 && frames.get(level)[2] == -1) {
            level--;
        }
        final int clause = frames.get(level)[2];
        if (clause == FrostlakeLexer.ON || clause == FrostlakeLexer.LIMIT || clause == FrostlakeLexer.OFFSET
                || clause == FrostlakeLexer.FETCH) {
            return null;
        }
        int plain = 0;
        int call = -1;
        for (int f = level + 1; f < frames.size(); f++) {
            final int kind = frames.get(f)[1];
            if (kind == 3) {
                return null;
            }
            if (kind == 1) {
                call = frames.get(f)[0];
            } else {
                plain++;
            }
        }
        if (clause != FrostlakeLexer.SELECT) {
            return new int[] {CLAUSE, -1, clause};
        }
        if (call >= 0) {
            return new int[] {ARGUMENT, call, clause};
        }
        return new int[] {plain > 1 ? DEEP : ITEM, -1, clause};
    }

    /**
     * The '(' of the call a query's select list opens with — after its DISTINCT or ALL — or -1 when it opens with
     * anything else.
     */
    private static int firstItemCall(final List<Token> spoken, final int select, final int close) {
        int at = select + 1;
        if (at < close && (spoken.get(at).getType() == FrostlakeLexer.DISTINCT
                || spoken.get(at).getType() == FrostlakeLexer.ALL)) {
            at++;
        }
        if (at + 1 >= close) {
            return -1;
        }
        final int type = spoken.get(at).getType();
        return (WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER)
            && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN ? at + 1 : -1;
    }

    /**
     * The lines after the SELECT of a faulty query whose first item is the call at {@code call}, or null where the
     * query stands in no place read that way: in a select list the call's '(', compared in a WHERE or a HAVING the
     * first token in the call's parentheses (and the comma after a named star there), and as an IN's list in one of
     * them the token past the call.
     */
    private static List<String> afterCallFirst(final List<Token> spoken, final int open, final int close,
                                               final int place, final int clause, final int call) {
        final List<String> lines = new ArrayList<>();
        if (place == ITEM) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(call)));
            return lines;
        }
        if (place != CLAUSE || clause != FrostlakeLexer.WHERE && clause != FrostlakeLexer.HAVING || open < 1) {
            return null;
        }
        final int before = spoken.get(open - 1).getType();
        if (before == FrostlakeLexer.IN) {
            final int callClose = closing(spoken, call, close);
            if (callClose + 1 >= close) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(callClose + 1)));
            return lines;
        }
        final boolean compared = isComparison(before)
            || close + 1 < spoken.size() && isComparison(spoken.get(close + 1).getType());
        if (!compared || spoken.get(call + 1).getType() == FrostlakeLexer.RPAREN) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(call + 1)));
        if (call + 4 < close && spoken.get(call + 2).getType() == FrostlakeLexer.ARROW
                && spoken.get(call + 3).getType() == FrostlakeLexer.STAR
                && spoken.get(call + 4).getType() == FrostlakeLexer.COMMA) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(call + 4)));
        }
        return lines;
    }

    private static boolean isComparison(final int type) {
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE;
    }

    /** The clause last opened at the innermost level that has opened one, or -1. */
    private static int governing(final List<int[]> frames) {
        for (int f = frames.size() - 1; f >= 0; f--) {
            if (frames.get(f)[2] != -1) {
                return frames.get(f)[2];
            }
        }
        return -1;
    }

    /**
     * Whether a '(' that opens a query stands where a value is read: not in a FROM list (a derived table, however
     * many brackets it stands in), not after AS (a common table expression's body), EXISTS, a set operator or a name
     * (a call's own bracket), and not at the statement's start.
     */
    private static boolean isValuePlace(final int before, final int clause, final boolean first) {
        if (first || clause == FrostlakeLexer.FROM || clause == FrostlakeLexer.JOIN
                || WORDS.contains(before) || before == FrostlakeLexer.QUOTED_IDENTIFIER
                || before == FrostlakeLexer.CAST || before == FrostlakeLexer.TRY_CAST
                || before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN || before == FrostlakeLexer.AS
                || before == FrostlakeLexer.EXISTS || before == FrostlakeLexer.UNION
                || before == FrostlakeLexer.INTERSECT || before == FrostlakeLexer.EXCEPT
                || before == FrostlakeLexer.MINUS_KW || before == FrostlakeLexer.ALL && clause != FrostlakeLexer.WHERE
                    && clause != FrostlakeLexer.HAVING && clause != FrostlakeLexer.QUALIFY
                    && clause != FrostlakeLexer.SELECT
                || before == FrostlakeLexer.LATERAL || before == FrostlakeLexer.SEMI) {
            return false;
        }
        return true;
    }

    /** Whether the tokens from {@code first} to {@code last} parse as a statement of their own. */
    private static boolean parses(final String sql, final List<Token> spoken, final int first, final int last) {
        return SyntaxErrorListener.cleanParse(
            sql.substring(spoken.get(first).getStartIndex(), spoken.get(last).getStopIndex() + 1)) != null;
    }

    /** The position of the ')' closing the '(' at {@code open}, or {@code end} when the statement ends first. */
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
        return end;
    }

    private static void blank(final char[] text, final int from, final int to) {
        for (int c = from; c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    /** Whether a token opens a clause a value query may stand in. */
    private static boolean isClause(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM || type == FrostlakeLexer.JOIN
            || type == FrostlakeLexer.ON || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
            || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.OFFSET || type == FrostlakeLexer.FETCH
            || type == FrostlakeLexer.SET || type == FrostlakeLexer.VALUES;
    }
}
