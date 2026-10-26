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

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a block's value that is a query in parentheses which does not parse - {@code LET c :=
 * (SELECT 1 FROM x y z)}, {@code c := 1 + (SELECT …)}, {@code RETURN (SELECT …)}, in a block or a SQL routine's
 * body: the query's SELECT, then the block's reading of the query's tokens. From the token after the SELECT the
 * block passes over everything up to the query's first word or ':', and over that token and as many after it as
 * there are brackets around the query - its own counted, and a call's twice; the next word it meets is a name, and
 * the token after that name is refused. A ',' met before any word reads the value as a list of values, where an AS
 * after an item is refused; the query's ')' met first ends the report (all live-verified):
 *
 * <pre>
 *   BEGIN LET c := (SELECT 1 FROM x y z); RETURN c; END;   'SELECT', then 'z': x is passed over, y is the name
 *   BEGIN RETURN (SELECT 1 x y); END;                      'SELECT', then the query's ')'
 *   BEGIN RETURN (SELECT a FROM t WHERE); END;             'SELECT', then 'WHERE': a is passed over, t is the name
 *   … RETURN (SELECT a a a FROM t); END                    'SELECT', then the third 'a'
 *   … RETURN ((SELECT a a a FROM t)); END                  'SELECT', then 'FROM': two brackets pass two words over
 *   … RETURN ABS((SELECT a a a FROM t)); END               'SELECT', then the query's ')': a call's bracket passes
 *                                                          two more
 *   … RETURN (SELECT FROM t); END                          'SELECT' alone: no word follows the one passed over
 *   BEGIN RETURN (SELECT :"low"); END                      'SELECT', then the query's ')': the ':' is passed over
 *   BEGIN LET q := (SELECT 1, 2 AS 2); END;                'SELECT', then 'AS': the ',' reads a list of values
 *   BEGIN LET q := (SELECT 1, 2 x y); END;                 'SELECT', then 'x' and the query's ')': a name after an
 *                                                          item
 *   BEGIN LET q := (SELECT 1 AS 1); END;                   'SELECT' alone: the query's ')' is met first
 *   BEGIN RETURN (SELECT (1) x y); END;                    'SELECT', then the '1' in the bracket before the first
 *                                                          word, then the query's own fault
 *   BEGIN RETURN COALESCE(1, (SELECT 1 x y)); END;         'SELECT' alone: a call's later argument
 * </pre>
 *
 * <p>A query this reading does not model - a list item that is no single value - is followed by its own first fault,
 * as the query alone would report it. The rule applies only where that query is the text's one fault: the text must
 * parse cleanly once the query is written as a value.
 */
public final class BlockValueSubqueryLines {

    /** The block refuses no token after the query's SELECT. */
    private static final int NO_LINE = -2;

    /** The query is no shape the block's reading is modelled for. */
    private static final int UNMODELLED = -1;

    private BlockValueSubqueryLines() {
    }

    /**
     * The lines live reports for such a value, or null when the text holds none or has another fault.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @return the lines, or null
     */
    public static List<String> lines(final String sql, final List<Token> spoken) {
        if (sql == null || spoken == null) {
            return null;
        }
        for (int open = 0; open + 1 < spoken.size(); open++) {
            if (spoken.get(open).getType() != FrostlakeLexer.LPAREN
                    || spoken.get(open + 1).getType() != FrostlakeLexer.SELECT) {
                continue;
            }
            final int brackets = brackets(spoken, open);
            if (brackets < 0) {
                continue;
            }
            final int close = closingParen(spoken, open);
            if (close < 0 || close == open + 1) {
                continue;
            }
            final int queryStart = spoken.get(open + 1).getStartIndex();
            final int queryEnd = spoken.get(close - 1).getStopIndex();
            if (SyntaxErrorListener.cleanParse(withValue(sql, queryStart, queryEnd)) == null) {
                continue;
            }
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
            if (laterArgument(spoken, open)) {
                return lines;
            }
            final int listName = listItemName(spoken, open + 1, close);
            if (listName >= 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(listName)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
                return lines;
            }
            if (spoken.get(open + 2).getType() == FrostlakeLexer.LPAREN && open + 3 < close) {
                // A bracket before the query's first word: the token it opens on, then the query's own fault.
                lines.add(SyntaxErrorListener.sentence(spoken.get(open + 3)));
                final String own = firstLine(only(sql, queryStart, queryEnd));
                if (own != null && !own.equals(lines.get(1))) {
                    lines.add(own);
                }
                return lines;
            }
            final int refused = refusedByBlock(spoken, open + 1, close, brackets);
            final String fault = refused >= 0 ? SyntaxErrorListener.sentence(spoken.get(refused))
                : refused == NO_LINE ? null : firstLine(only(sql, queryStart, queryEnd));
            if (fault == null && refused != NO_LINE) {
                continue;
            }
            if (fault != null) {
                lines.add(fault);
            }
            return lines;
        }
        return null;
    }

    /**
     * How many brackets the query in the '(' at {@code open} stands in - its own counted, and a call's twice - where
     * the statement it stands in, from the last ';' or block word before it, assigns with {@code :=} or returns
     * before the brackets; -1 where it stands in no block's value.
     */
    private static int brackets(final List<Token> spoken, final int open) {
        int depth = 0;
        int brackets = 1;
        boolean assigns = false;
        for (int i = open - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (assigns) {
                    return -1;
                } else {
                    brackets += i > 0 && SyntaxErrorListener.isNameLike(spoken.get(i - 1))
                        && spoken.get(i - 1).getType() != FrostlakeLexer.RETURN ? 2 : 1;
                }
            } else if (depth == 0 && (type == FrostlakeLexer.COLON_EQ || type == FrostlakeLexer.RETURN)) {
                assigns = true;
            } else if (depth == 0 && (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN
                    || type == FrostlakeLexer.THEN || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO)) {
                return assigns ? brackets : -1;
            }
        }
        return -1;
    }

    /**
     * The index of the token the block refuses after the SELECT at {@code select} of a query closed at {@code close}
     * that stands in {@code brackets} brackets; {@link #NO_LINE} where it refuses none, {@link #UNMODELLED} for a
     * query this reading does not model.
     */
    private static int refusedByBlock(final List<Token> spoken, final int select, final int close,
                                      final int brackets) {
        for (int i = select + 1; i < close; i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN) {
                return UNMODELLED;
            }
            if (type == FrostlakeLexer.COMMA) {
                return listItemRefused(spoken, i, close);
            }
            if (type == FrostlakeLexer.COLON || SyntaxErrorListener.isNameLike(token)) {
                if (i + brackets > close) {
                    return UNMODELLED;
                }
                for (int name = i + brackets; name < close; name++) {
                    if (SyntaxErrorListener.isNameLike(spoken.get(name))) {
                        return name + 1;
                    }
                }
                return NO_LINE;
            }
        }
        return NO_LINE;
    }

    /** Whether the query's '(' at {@code open} is a call's argument after a comma, refused at its SELECT alone. */
    private static boolean laterArgument(final List<Token> spoken, final int open) {
        if (open < 1 || spoken.get(open - 1).getType() != FrostlakeLexer.COMMA) {
            return false;
        }
        int depth = 0;
        for (int i = open - 2; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth-- == 0) {
                return SyntaxErrorListener.isNameLike(spoken.get(i - 1));
            }
        }
        return false;
    }

    /**
     * The index of the name after an item of a select list that opens with a single value and a ',', where an item is
     * followed by a name instead of a ',' or an AS; -1 for any other list.
     */
    private static int listItemName(final List<Token> spoken, final int select, final int close) {
        if (select + 2 >= close || !singleValue(spoken.get(select + 1))
                || spoken.get(select + 2).getType() != FrostlakeLexer.COMMA) {
            return -1;
        }
        for (int separator = select + 2; separator + 2 < close; separator += 2) {
            if (!singleValue(spoken.get(separator + 1))) {
                return -1;
            }
            final Token after = spoken.get(separator + 2);
            if (after.getType() != FrostlakeLexer.COMMA) {
                return SyntaxErrorListener.isNameLike(after) && after.getType() != FrostlakeLexer.AS
                    ? separator + 2 : -1;
            }
        }
        return -1;
    }

    /**
     * The index of the token refused in a list of values read from the ',' at {@code comma}: the AS after an item
     * that is a single value; {@link #NO_LINE} where the list reaches the query's ')', {@link #UNMODELLED} where an
     * item is anything else.
     */
    private static int listItemRefused(final List<Token> spoken, final int comma, final int close) {
        int separator = comma;
        while (separator + 1 < close && singleValue(spoken.get(separator + 1))) {
            final int after = separator + 2;
            if (after == close) {
                return NO_LINE;
            }
            final int type = spoken.get(after).getType();
            if (type == FrostlakeLexer.AS) {
                return after;
            }
            if (type != FrostlakeLexer.COMMA) {
                return UNMODELLED;
            }
            separator = after;
        }
        return UNMODELLED;
    }

    private static boolean singleValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || SyntaxErrorListener.isNameLike(token);
    }

    /** The index in {@code spoken} of the ')' closing the '(' at {@code open}, or -1. */
    private static int closingParen(final List<Token> spoken, final int open) {
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

    /** The text with the query from {@code start} to {@code end} written as a value of the same length. */
    private static String withValue(final String sql, final int start, final int end) {
        final char[] written = sql.toCharArray();
        for (int c = start; c <= end; c++) {
            if (written[c] != '\n' && written[c] != '\r') {
                written[c] = ' ';
            }
        }
        written[start] = 'N';
        if (start + 3 <= end) {
            written[start + 1] = 'U';
            written[start + 2] = 'L';
            written[start + 3] = 'L';
        } else {
            for (int c = start; c <= end; c++) {
                written[c] = c == start ? '1' : ' ';
            }
        }
        return new String(written);
    }

    /** The text with everything but the query from {@code start} to {@code end} blanked, every place kept. */
    private static String only(final String sql, final int start, final int end) {
        final char[] written = sql.toCharArray();
        for (int c = 0; c < written.length; c++) {
            if ((c < start || c > end) && written[c] != '\n' && written[c] != '\r') {
                written[c] = ' ';
            }
        }
        return new String(written);
    }

    /** The first line the query reports read alone, every place kept, or null where it parses cleanly. */
    private static String firstLine(final String text) {
        final SyntaxErrorListener listener = new SyntaxErrorListener(text, true);
        listener.statementParse();
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        // Where this parse reports a fault is read: it must not depend on what was parsed before.
        ColdPrediction.arm(parser);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        parser.sqlScript();
        return listener.firstReportedLine();
    }
}
