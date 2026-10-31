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

import dev.frostlake.executor.SqlCompilationError;

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * A query assigned in parentheses whose only faults are FROM items TABLE( over a bare name, {@code n := (SELECT
 * COUNT(*) FROM TABLE(rs))}, whatever the target is declared as, or if it is declared at all. The assignment reads its
 * parenthesized query as a value, where TABLE( takes a call: the account refuses the ')' after the name and takes it
 * for the call's own, so TABLE( either closes at the next ')', which leaves the query one ')' short, refused at the
 * token after the parenthesis that closes it, or closes before the alias, the join or the clause after the call,
 * refused there (live-verified):
 *
 * <pre>
 *   n := (SELECT COUNT(*) FROM TABLE(rs));                    the ')' after rs, then the ';'
 *   n := (SELECT COUNT(*) FROM TABLE(rs)) + 1;                the ')' after rs, then the '+'
 *   n := (SELECT * FROM (SELECT 1 FROM TABLE(rs)) t);         the ')' after rs, then the ';'
 *   n := (SELECT COUNT(*) FROM TABLE(rs) t, TABLE(rs) u);     each ')' after rs, and each alias after it
 * </pre>
 *
 * <p>A LET and a RETURN read their query as a statement, refused at its first word and at the ')' (see
 * {@link AssignedQueryLines}), and so does a query an IN or an operand of the assignment's query holds.
 */
public final class AssignedTableNameLines {

    private AssignedTableNameLines() {
    }

    /**
     * Refuse the text by such an assignment's lines when its parse reports the query's first word first: how a text
     * EXECUTE IMMEDIATE runs is read.
     *
     * @param sql      the text
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final SyntaxErrorListener listener) {
        if (sql == null || !listener.hasErrors() || listener.firstReportedLine() == null) {
            return;
        }
        final List<Token> tokens = BlockReadOn.spoken(sql);
        final List<String> lines = linesOf(sql, tokens.subList(0, tokens.size() - 1), tokens.get(tokens.size() - 1),
            listener.firstReportedLine());
        if (lines != null) {
            throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
        }
    }

    /**
     * The lines of such an assignment in a text, its first line as a parse of the text reports it: how a body is
     * refused as its routine is created.
     *
     * @param sql the text
     * @return the lines, or null when the text holds no such assignment where its parse first stops
     */
    public static List<String> linesOf(final String sql) {
        final String first = sql == null ? null : SyntaxErrorListener.firstLineReportedFor(sql);
        if (first == null) {
            return null;
        }
        final List<Token> tokens = BlockReadOn.spoken(sql);
        return linesOf(sql, tokens.subList(0, tokens.size() - 1), tokens.get(tokens.size() - 1), first);
    }

    /**
     * The lines of such an assignment, when the script's first line names a token of its query.
     *
     * @param sql       the script's source text
     * @param spoken    the script's tokens on the default channel
     * @param end       the script's end of input
     * @param firstLine the first line the script's parse reports
     * @return the lines, or null when the text holds no such assignment there
     */
    static List<String> linesOf(final String sql, final List<Token> spoken, final Token end, final String firstLine) {
        for (int i = 3; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type != FrostlakeLexer.SELECT && type != FrostlakeLexer.WITH
                    || spoken.get(i - 1).getType() != FrostlakeLexer.LPAREN) {
                continue;
            }
            int open = i - 1;
            while (open > 0 && spoken.get(open - 1).getType() == FrostlakeLexer.LPAREN) {
                open--;
            }
            final int close = closing(spoken, open);
            if (open >= 2 && spoken.get(open - 1).getType() == FrostlakeLexer.COLON_EQ
                    && SyntaxErrorListener.isNameLike(spoken.get(open - 2))
                    && (open < 3 || spoken.get(open - 3).getType() != FrostlakeLexer.LET)
                    && close > 0 && reportedWithin(firstLine, spoken, i, close)) {
                return assigned(sql, spoken, end, open, i, close);
            }
        }
        return null;
    }

    /** Whether the line names a token from {@code from} to {@code to}. */
    private static boolean reportedWithin(final String line, final List<Token> spoken, final int from, final int to) {
        for (int k = from; k <= to; k++) {
            if (line.equals(SyntaxErrorListener.sentence(spoken.get(k)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The lines for the query the parentheses from {@code open} to {@code close} hold, its first word at {@code first},
     * or null when it is no such query.
     */
    private static List<String> assigned(final String sql, final List<Token> spoken, final Token end,
                                         final int open, final int first, final int close) {
        // Each FROM item TABLE( over a bare name, by the index of the ')' after the name.
        final List<Integer> names = new ArrayList<>();
        for (int t = open + 1; t + 3 < close; t++) {
            final int before = spoken.get(t - 1).getType();
            if (spoken.get(t).getType() != FrostlakeLexer.TABLE
                    || spoken.get(t + 1).getType() != FrostlakeLexer.LPAREN
                    || before != FrostlakeLexer.FROM && before != FrostlakeLexer.COMMA && before != FrostlakeLexer.JOIN
                    || !SyntaxErrorListener.isNameLike(spoken.get(t + 2)) || !inFromClause(spoken, t, first)) {
                continue;
            }
            int r = t + 3;
            while (spoken.get(r).getType() == FrostlakeLexer.DOT
                    && SyntaxErrorListener.isNameLike(spoken.get(r + 1))) {
                r += 2;
            }
            if (spoken.get(r).getType() == FrostlakeLexer.RPAREN) {
                names.add(r);
            }
        }
        if (names.isEmpty() || !soundWithTableNames(sql, spoken, names)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        boolean shortOfOne = false;
        for (final int r : names) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(r)));
            if (spoken.get(r + 1).getType() != FrostlakeLexer.RPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(r + 1)));
            } else if (shortOfOne) {
                return null;
            } else {
                // TABLE( closes at the next ')': each parenthesis after it closes a group one further out, and the
                // assignment's own is the one the query is short of.
                shortOfOne = true;
            }
        }
        if (shortOfOne) {
            lines.add(SyntaxErrorListener.sentence(close + 1 < spoken.size() ? spoken.get(close + 1) : end));
        }
        return lines;
    }

    /**
     * Whether the text parses once each TABLE( over a bare name reads the name as a table's: its TABLE, its
     * parenthesis and the ')' after the name blanked.
     */
    private static boolean soundWithTableNames(final String sql, final List<Token> spoken, final List<Integer> names) {
        final char[] text = sql.toCharArray();
        for (final int r : names) {
            int t = r;
            while (spoken.get(t).getType() != FrostlakeLexer.TABLE) {
                t--;
            }
            for (int c = spoken.get(t).getStartIndex(); c <= spoken.get(t + 1).getStopIndex(); c++) {
                if (text[c] != '\n') {
                    text[c] = ' ';
                }
            }
            text[spoken.get(r).getStartIndex()] = ' ';
        }
        final int opener = spoken.get(0).getType();
        return SyntaxErrorListener.linesReportedFor(new String(text),
            opener != FrostlakeLexer.BEGIN && opener != FrostlakeLexer.DECLARE).isEmpty();
    }

    /**
     * Whether the TABLE at {@code at} is a FROM item of the query whose first word is at {@code first}, or of a
     * derived table in one: each parenthesis around it up to that query's opens a FROM item.
     */
    private static boolean inFromClause(final List<Token> spoken, final int at, final int first) {
        int depth = 0;
        boolean from = false;
        for (int k = at - 1; k >= first; k--) {
            final int type = spoken.get(k).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth > 0) {
                depth--;
            } else if (type == FrostlakeLexer.LPAREN) {
                final int before = spoken.get(k - 1).getType();
                if (!from || before != FrostlakeLexer.FROM && before != FrostlakeLexer.COMMA
                        && before != FrostlakeLexer.JOIN) {
                    return false;
                }
                from = false;
            } else if (depth == 0 && !from) {
                if (type == FrostlakeLexer.FROM) {
                    from = true;
                } else if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.ON
                        || type == FrostlakeLexer.USING || type == FrostlakeLexer.HAVING
                        || type == FrostlakeLexer.QUALIFY) {
                    return false;
                }
            }
        }
        return from;
    }

    /** The index of the ')' that closes the parenthesis at {@code open}, or -1. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
