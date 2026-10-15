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
import org.antlr.v4.runtime.TokenStream;

/**
 * A JOIN with no table after it. Live reads the join and refuses the token where its table belongs — the end of the
 * input, a semicolon, a clause keyword, a closing parenthesis, a number — where this parser, with no table to read,
 * never entered the join and named the JOIN itself (all live-verified):
 *
 * <pre>
 *   SELECT 1 FROM t JOIN                  '&lt;EOF&gt;'
 *   SELECT 1 FROM t JOIN WHERE a = 1 x y  'WHERE', then 'x': the clause is read as the query's own
 *   SELECT 1 FROM t JOIN; SELECT 1 x y    ';', then the later statement's 'y'
 *   SELECT 1 FROM t JOIN ); SELECT 1 x y  ')', then 'y': the parenthesis is passed over
 *   SELECT 1 FROM t LEFT JOIN WHERE a = 1 'WHERE' alone
 *   SELECT 1 FROM t JOIN LEFT JOIN n      the second 'JOIN': a join type after a JOIN is passed over
 *   SELECT 1 FROM t JOIN . WHERE a = 1    '.' alone
 * </pre>
 *
 * <p>What follows the refused token is read as if the join had not been written, so the lines after the first are
 * those of the text with the join's keywords blanked out.
 */
final class JoinTableSyntax {

    private JoinTableSyntax() {
    }

    /**
     * The token live refuses when the parse named a JOIN of a FROM list, or the join type or LATERAL written
     * straight after one: the token after it.
     *
     * @param stream   the parsed tokens
     * @param reported the token the fault is reported at
     * @return the token to report instead, or null when this is not that case
     */
    static Token refusedInstead(final TokenStream stream, final Token reported) {
        final int type = reported.getType();
        if (type == Token.EOF || reported.getTokenIndex() < 0) {
            return null;
        }
        final boolean join = type == FrostlakeLexer.JOIN;
        if (!join && !isJoinType(type) && type != FrostlakeLexer.LATERAL) {
            return null;
        }
        final Token before = spoken(stream, reported.getTokenIndex(), -1);
        if (!join && (before == null || before.getType() != FrostlakeLexer.JOIN)) {
            return null;
        }
        return inFromList(stream, join ? reported : before) ? spoken(stream, reported.getTokenIndex(), 1) : null;
    }

    /**
     * The lines live reports when the parse's first line names the token where a join's table belongs, or null: that
     * token's line, then the lines of the text read without the join.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || faultIndex < 0) {
            return null;
        }
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 2 || spoken.get(fault - 1).getType() != FrostlakeLexer.JOIN || !inFromList(spoken, fault - 1)) {
            return null;
        }
        final Token refused = spoken.get(fault);
        final int type = refused.getType();
        if (type == Token.EOF || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.JOIN || isJoinType(type)) {
            return null;
        }
        int first = fault - 1;
        while (first > 1 && isJoinWord(spoken.get(first - 1).getType())) {
            first--;
        }
        final char[] text = sql.toCharArray();
        blank(text, spoken.get(first).getStartIndex(), spoken.get(fault - 1).getStopIndex());
        if (type == FrostlakeLexer.RPAREN && closesNoGroup(spoken, fault)) {
            blank(text, refused.getStartIndex(), refused.getStopIndex());
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(refused));
        final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        // A token refused in the text without the join too is one fault, and what its own statement reports after it
        // is that fault's wreckage: only the later statements speak then.
        // After a dot where the table belongs nothing more of the statement is told: only the later statements speak.
        int[] from = type == FrostlakeLexer.DOT ? statementEnd(spoken, fault) : at;
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place == null) {
                continue;
            }
            if (place[0] == at[0] && place[1] == at[1]) {
                from = statementEnd(spoken, fault);
            } else if (from != null && (place[0] > from[0] || place[0] == from[0] && place[1] > from[1])) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The line and position of the semicolon ending the statement that holds {@code fault}, or null. */
    private static int[] statementEnd(final List<Token> spoken, final int fault) {
        for (int i = fault; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(spoken.get(i)));
            }
        }
        return null;
    }

    private static void blank(final char[] text, final int from, final int to) {
        for (int c = from; c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    /** Whether the ')' at {@code close} closes no parenthesis opened in its own statement. */
    private static boolean closesNoGroup(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close - 1; i >= 0 && spoken.get(i).getType() != FrostlakeLexer.SEMI; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth-- == 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether the JOIN stands in a FROM list: a FROM opens its level before any other clause does. */
    private static boolean inFromList(final TokenStream stream, final Token join) {
        final List<Token> before = new ArrayList<>();
        for (int i = 0; i <= join.getTokenIndex(); i++) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                before.add(stream.get(i));
            }
        }
        return inFromList(before, before.size() - 1);
    }

    /** As {@link #inFromList(TokenStream, Token)}, for the JOIN at {@code join} among the default-channel tokens. */
    private static boolean inFromList(final List<Token> spoken, final int join) {
        int depth = 0;
        for (int i = join - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0) {
                if (type == FrostlakeLexer.FROM || type == FrostlakeLexer.USING) {
                    return true;
                }
                if (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WHERE
                        || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
                        || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
                        || type == FrostlakeLexer.LIMIT) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isJoinType(final int type) {
        return type == FrostlakeLexer.LEFT || type == FrostlakeLexer.RIGHT || type == FrostlakeLexer.FULL
            || type == FrostlakeLexer.INNER || type == FrostlakeLexer.CROSS || type == FrostlakeLexer.NATURAL
            || type == FrostlakeLexer.ASOF;
    }

    /** A word of a join's keywords before its JOIN. */
    private static boolean isJoinWord(final int type) {
        return isJoinType(type) || type == FrostlakeLexer.OUTER || type == FrostlakeLexer.DIRECTED;
    }

    /** The default-channel token next to the one at {@code index}, in the given direction, or null. */
    private static Token spoken(final TokenStream stream, final int index, final int step) {
        for (int i = index + step; i >= 0 && i < stream.size(); i += step) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
