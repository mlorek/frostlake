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

import dev.frostlake.executor.SqlStringLiterals;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * A string written between dollar signs is a string literal wherever one may stand (live-verified): {@code ALTER
 * TABLE t SET COMMENT = $$z$$}, a column's {@code COMMENT $$z$$}, a tag's {@code ALLOWED_VALUES $$a$$, $$b$$}, a file
 * format's {@code FIELD_DELIMITER = $$|$$}, {@code DATE $$2020-01-01$$} and {@code INTERVAL $$1 day$$} all run. The
 * grammar reads one in the places it names it; where it reads a quoted string alone, the text is read again with
 * the dollar string spelled as the quoted string it is, in the same place and at the same width, so every token
 * after it stays where it was. Where the quoted string is refused too, the first refusal stands: {@code SELECT 1
 * $$x$$} names the dollar string.
 */
public final class DollarStringLiterals {

    private DollarStringLiterals() {
    }

    /**
     * The text with its first dollar string at or after the place of its first fault spelled as a quoted string, or
     * null when there is none, or it does not fit its own width as a quoted string.
     *
     * @param sql     the text that was refused
     * @param refused its refusal
     * @return the text to read again, or null
     */
    public static String respelledAtFault(final String sql, final SqlSyntaxException refused) {
        final int[] at = faultOf(refused);
        if (at == null || sql == null || !sql.equals(refused.getFailedSql()) || sql.indexOf("$$") < 0) {
            return null;
        }
        final Token token = firstFrom(sql, at);
        if (token == null) {
            return null;
        }
        final String written = token.getText();
        final String content = written.substring(2, written.length() - 2);
        final String quoted = SqlStringLiterals.encode(content);
        if (content.indexOf('\n') >= 0 || content.indexOf('\r') >= 0 || quoted.length() > written.length()) {
            return null;
        }
        final StringBuilder respelled = new StringBuilder(sql.length());
        respelled.append(sql, 0, token.getStartIndex()).append(quoted);
        for (int pad = quoted.length(); pad < written.length(); pad++) {
            respelled.append(' ');
        }
        return respelled.append(sql, token.getStopIndex() + 1, sql.length()).toString();
    }

    /**
     * Whether the text read again got past the string that was spelled anew: its own first fault, if it has one,
     * stands after that string. A fault at the string or before it means the quoted string is no more welcome there
     * than the dollar string was, and the first refusal stands.
     *
     * @param sql     the text as written
     * @param refused the refusal of the text as written
     * @param again   the refusal of the text read again
     * @return whether the second reading's refusal is the one to report
     */
    public static boolean readsPast(final String sql, final SqlSyntaxException refused, final SqlSyntaxException again) {
        final int[] first = faultOf(refused);
        final int[] second = faultOf(again);
        final Token token = first == null ? null : firstFrom(sql, first);
        return token != null && second != null && (second[0] > token.getLine()
            || second[0] == token.getLine() && second[1] > token.getCharPositionInLine() + token.getText().length() - 1);
    }

    /** The first dollar string of a text that stands at or after a place, or null. */
    private static Token firstFrom(final String sql, final int[] place) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        for (final Token token : stream.getTokens()) {
            if (token.getType() == FrostlakeLexer.DOLLAR_QUOTED_STRING && (token.getLine() > place[0]
                    || token.getLine() == place[0] && token.getCharPositionInLine() >= place[1])) {
                return token;
            }
        }
        return null;
    }

    /** The line and the position of the first fault a refusal reports, or null when it names none. */
    private static int[] faultOf(final SqlSyntaxException refused) {
        final String message = refused.getMessage();
        final int first = message == null ? -1 : message.indexOf("syntax error line ");
        if (first < 0) {
            return null;
        }
        final int end = message.indexOf('\n', first);
        return SyntaxErrorListener.sentenceCoordinates(end < 0 ? message.substring(first) : message.substring(first, end));
    }
}
