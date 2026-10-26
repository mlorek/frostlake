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

/**
 * The lines the account reports for a VALUES list in a FROM clause's parentheses whose row is empty or ends in a comma:
 * the account refuses the VALUES itself. After an empty row it reads on from the first ')' after the VALUES as though
 * that closed the parentheses, refuses what then stands wrong, and last the ')' then left unmatched (all
 * live-verified):
 *
 * <pre>
 *   SELECT * FROM (VALUES ())                'VALUES', then the parentheses' own ')'
 *   SELECT * FROM (VALUES (1), ())           'VALUES', then the empty row's ')' and the parentheses' ')'
 *   SELECT * FROM (VALUES (), (1))           'VALUES', '1', then the parentheses' ')'
 *   SELECT * FROM (VALUES (1, ))             'VALUES' alone
 * </pre>
 */
final class ValuesEmptyRowLines {

    private ValuesEmptyRowLines() {
    }

    /**
     * The lines for a text whose first fault is the ')' of such a row, or null for any other text.
     *
     * @param sql        the text parsed
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 5 || spoken.get(fault).getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        final int last = spoken.get(fault - 1).getType();
        final int rowOpen = last == FrostlakeLexer.LPAREN ? fault - 1 : opening(spoken, fault - 1);
        if (rowOpen < 0 || last != FrostlakeLexer.LPAREN && last != FrostlakeLexer.COMMA) {
            return null;
        }
        // The rows before this one, back to the VALUES.
        int at = rowOpen - 1;
        while (at > 0 && spoken.get(at).getType() == FrostlakeLexer.COMMA
                && spoken.get(at - 1).getType() == FrostlakeLexer.RPAREN) {
            at = opening(spoken, at - 2) - 1;
        }
        if (at < 2 || spoken.get(at).getType() != FrostlakeLexer.VALUES
                || spoken.get(at - 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        int before = at - 2;
        while (before > 0 && spoken.get(before).getType() == FrostlakeLexer.LPAREN) {
            before--;
        }
        final int reads = spoken.get(before).getType();
        if (reads != FrostlakeLexer.FROM && (reads != FrostlakeLexer.JOIN || before != at - 2)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
        if (last == FrostlakeLexer.COMMA) {
            return lines;
        }
        int close = at + 1;
        while (spoken.get(close).getType() != FrostlakeLexer.RPAREN) {
            close++;
        }
        // The statement read on as though the VALUES up to that ')' were a name.
        final char[] read = sql.toCharArray();
        Arrays.fill(read, spoken.get(at).getStartIndex(), spoken.get(close).getStartIndex(), ' ');
        read[spoken.get(at).getStartIndex()] = 'x';
        final int[] from = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(spoken.get(close)));
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(read))) {
            final int[] where = SyntaxErrorListener.sentenceCoordinates(line);
            if (where == null || where[0] < from[0] || where[0] == from[0] && where[1] < from[1]) {
                return null;
            }
            lines.add(line);
        }
        final Token unmatched = unmatchedClose(spoken, at, close);
        if (unmatched != null && !lines.contains(SyntaxErrorListener.sentence(unmatched))) {
            lines.add(SyntaxErrorListener.sentence(unmatched));
        }
        return lines;
    }

    /**
     * The first ')' of the statement that closes no '(' once its tokens from {@code from} up to {@code to} are read as
     * a name, or null.
     */
    private static Token unmatchedClose(final List<Token> spoken, final int from, final int to) {
        int start = from;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        int depth = 0;
        for (int i = start; i < spoken.size() && spoken.get(i).getType() != FrostlakeLexer.SEMI; i++) {
            final int type = spoken.get(i).getType();
            if (i >= from && i < to) {
                continue;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth < 0) {
                return spoken.get(i);
            }
        }
        return null;
    }

    /** The index of the '(' a row's contents ending at {@code end} open with, or -1. */
    private static int opening(final List<Token> spoken, final int end) {
        int depth = 0;
        for (int i = end; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
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
}
