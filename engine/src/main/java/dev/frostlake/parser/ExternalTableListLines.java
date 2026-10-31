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

import org.antlr.v4.runtime.Token;

import java.util.Collections;
import java.util.List;

/**
 * The lines live reports for a comma at either end of an external table's parenthesized list, where this grammar's
 * reading of the statement stops at its kind word instead (live-verified): a leading comma is read as in any option
 * list ({@link OptionListCommaLines}), {@code FILE_FORMAT = (, TYPE = CSV)} the comma and then the list's closing
 * parenthesis, and a trailing one names that parenthesis alone, {@code FILE_FORMAT = (TYPE = CSV,)}.
 *
 * <p>Its column list is read as a table's: a type's parameter list the type does not take is refused as in CREATE
 * TABLE, {@code (a VARCHAR(5,2) AS (value:a::VARCHAR))} at the ',' and the '2' and then the AS the list's first ')'
 * leaves unread, {@code (a NUMBER() AS …)} at the '(' (live-verified), whatever columns stand before it. The reading
 * is the CREATE TABLE this grammar reads, the kind word blanked and every position where it was written, and with the
 * {@code AS (…)} of each column before the fault blanked too, which that reading does not take; it speaks only where
 * its first fault falls inside a type's parameter list there.
 */
final class ExternalTableListLines {

    private ExternalTableListLines() {
    }

    /**
     * The lines for a CREATE EXTERNAL TABLE whose list opens or ends with a comma, or null.
     *
     * @param sql    the text parsed
     * @param spoken the default-channel tokens, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken) {
        int at = 0;
        if (type(spoken, at) != FrostlakeLexer.CREATE) {
            return null;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        if (type(spoken, at) != FrostlakeLexer.EXTERNAL || type(spoken, at + 1) != FrostlakeLexer.TABLE) {
            return null;
        }
        for (int i = at + 2; !ends(spoken, i); i++) {
            if (type(spoken, i) == FrostlakeLexer.EQ && type(spoken, i + 1) == FrostlakeLexer.LPAREN) {
                if (type(spoken, i + 2) == FrostlakeLexer.COMMA) {
                    return OptionListCommaLines.lines(sql, spoken, spoken.get(i + 2).getTokenIndex());
                }
                final int close = closeOf(spoken, i + 1);
                if (close > 0 && type(spoken, close - 1) == FrostlakeLexer.COMMA) {
                    return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(close)));
                }
            }
        }
        return columnListLines(sql, spoken, at);
    }

    /** The lines of a column list read as a table's, or null where its first fault falls outside a type's list. */
    private static List<String> columnListLines(final String sql, final List<Token> spoken, final int external) {
        int open = external + 2;
        while (!ends(spoken, open) && type(spoken, open) != FrostlakeLexer.LPAREN) {
            open++;
        }
        if (ends(spoken, open) || type(spoken, open - 1) == FrostlakeLexer.EQ) {
            return null;
        }
        final int close = closeOf(spoken, open);
        final char[] text = sql.toCharArray();
        for (int c = spoken.get(external).getStartIndex(); c <= spoken.get(external).getStopIndex(); c++) {
            text[c] = ' ';
        }
        List<String> read = SyntaxErrorListener.linesReportedFor(new String(text), true);
        int blanked = virtualColumnAt(spoken, open, close, read);
        while (blanked > 0) {
            final int expressionClose = closeOf(spoken, blanked + 1);
            for (int c = spoken.get(blanked).getStartIndex(); c <= spoken.get(expressionClose).getStopIndex(); c++) {
                if (text[c] != '\n' && text[c] != '\r') {
                    text[c] = ' ';
                }
            }
            read = SyntaxErrorListener.linesReportedFor(new String(text), true);
            blanked = virtualColumnAt(spoken, open, close, read);
        }
        if (read.isEmpty()) {
            return null;
        }
        final int[] first = SyntaxErrorListener.sentenceCoordinates(read.get(0));
        for (int i = open + 3; first != null && i < spoken.size() && (close < 0 || i < close); i++) {
            // A type's list: a '(' after the type's word, itself after the column's name.
            if (type(spoken, i) != FrostlakeLexer.LPAREN || type(spoken, i - 1) == FrostlakeLexer.AS
                    || !isWord(spoken.get(i - 1)) || !isWord(spoken.get(i - 2))) {
                continue;
            }
            final int listClose = closeOf(spoken, i);
            if (!after(first, spoken.get(i - 1)) || listClose > 0 && after(first, spoken.get(listClose))) {
                continue;
            }
            return read;
        }
        return null;
    }

    /**
     * The AS of a column's {@code AS (…)} where the reading's first fault falls, in the list between {@code open} and
     * {@code close}, its expression closed there; -1 where the fault is any other.
     */
    private static int virtualColumnAt(final List<Token> spoken, final int open, final int close,
                                       final List<String> read) {
        final int[] first = read.isEmpty() ? null : SyntaxErrorListener.sentenceCoordinates(read.get(0));
        int depth = 0;
        for (int i = open; first != null && close > 0 && i < close; i++) {
            final Token token = spoken.get(i);
            depth += token.getType() == FrostlakeLexer.LPAREN ? 1 : token.getType() == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 1 && token.getType() == FrostlakeLexer.AS && type(spoken, i + 1) == FrostlakeLexer.LPAREN
                    && token.getLine() == first[0] && token.getCharPositionInLine() == first[1]) {
                final int expressionClose = closeOf(spoken, i + 1);
                return expressionClose > 0 && expressionClose < close ? i : -1;
            }
        }
        return -1;
    }

    /** Whether a token is a word: a name or a keyword, written with letters. */
    private static boolean isWord(final Token token) {
        final String text = token.getText();
        return token.getType() != Token.EOF && text != null && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }

    /** Whether a line's place lies after a token's. */
    private static boolean after(final int[] place, final Token token) {
        return place[0] > token.getLine() || place[0] == token.getLine() && place[1] > token.getCharPositionInLine();
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1 where the statement leaves it open. */
    private static int closeOf(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; !ends(spoken, i); i++) {
            if (type(spoken, i) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type(spoken, i) == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the statement ends at {@code at}: the end of input, a semicolon, or no token at all. */
    private static boolean ends(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
