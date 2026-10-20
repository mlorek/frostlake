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
 * (SELECT 1 FROM x y z)}, {@code c := 1 + (SELECT …)}, {@code RETURN (SELECT …)}: the query's SELECT, then the
 * query's own first fault, as the query alone would report it (all live-verified):
 *
 * <pre>
 *   BEGIN LET c := (SELECT 1 FROM x y z); RETURN c; END;      'SELECT' at 17, 'z' at 35
 *   BEGIN RETURN (SELECT 1 FROM x y z); END;                  'SELECT' at 15, 'z' at 33
 * </pre>
 *
 * <p>The rule applies only where that query is the text's one fault: the text must parse cleanly once the query is
 * written as a value.
 */
public final class BlockValueSubqueryLines {

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
                    || spoken.get(open + 1).getType() != FrostlakeLexer.SELECT || !blockValue(spoken, open)) {
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
            final String fault = firstLine(only(sql, queryStart, queryEnd));
            if (fault == null) {
                continue;
            }
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
            lines.add(fault);
            return lines;
        }
        return null;
    }

    /**
     * Whether the '(' at {@code open} stands in a block's value: the statement it stands in, from the last ';' or
     * block word before it, assigns with {@code :=} or returns, before the bracket.
     */
    private static boolean blockValue(final List<Token> spoken, final int open) {
        int depth = 0;
        boolean assigns = false;
        for (int i = open - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.COLON_EQ || type == FrostlakeLexer.RETURN)) {
                assigns = true;
            } else if (depth == 0 && (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN
                    || type == FrostlakeLexer.THEN || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO)) {
                return assigns;
            }
        }
        return false;
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
