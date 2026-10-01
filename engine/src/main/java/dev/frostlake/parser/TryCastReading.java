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

/**
 * The lines live reports for a TRY_CAST whose construct cannot be read: exactly those of the same text with that
 * TRY_CAST spelled CAST, which live reads as the same construct. This grammar also takes TRY_CAST as a name, and a
 * call head refuses it, so where the cast cannot be read no alternative is left and the parse gives up at the word
 * or its parenthesis instead of at the fault inside (all live-verified):
 *
 * <pre>
 *   SELECT TRY_CAST(1, 2) AS INT) FROM t        ',' at 17, then ')' at 20
 *   SELECT TRY_CAST(1,) FROM t                  ',' at 17, then ')' at 18
 *   SELECT TRY_CAST(1, 2 AS INT)                ',' at 17, then ')' at 27
 *   SELECT TRY_CAST('1.5')                      ')' at 21
 *   SELECT 1 FROM t WHERE TRY_CAST(1, 2) = 1    ',' at 32 alone
 * </pre>
 *
 * <p>The TRY_CAST read is the innermost one whose parentheses hold the parse's first fault — the word, its '(' or
 * anything up to its closing ')' — standing where a value is read: after SELECT, a TOP's count, a comma, a '(' or an
 * operator. A TRY_CAST after an operand is an alias, and one after AS, a dot or an object keyword is a name.
 */
final class TryCastReading {

    private static final String CAST_WORD = "CAST";

    private TryCastReading() {
    }

    /**
     * The lines of the reading, or null when the first fault lies inside no TRY_CAST read as the cast construct.
     *
     * @param sql            the text
     * @param spoken         the text's default-channel tokens, end of input included
     * @param fault          the token the parse's first fault was raised at
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final Token fault,
                              final boolean statementParse) {
        final Token keyword = tryCastAround(spoken, fault);
        if (keyword == null || keyword.getStopIndex() - keyword.getStartIndex() + 1 < CAST_WORD.length()) {
            return null;
        }
        final char[] text = sql.toCharArray();
        for (int c = keyword.getStartIndex(); c <= keyword.getStopIndex(); c++) {
            final int k = c - keyword.getStartIndex();
            text[c] = k < CAST_WORD.length() ? CAST_WORD.charAt(k) : ' ';
        }
        final List<String> read = SyntaxErrorListener.linesReportedFor(new String(text), statementParse);
        if (read.isEmpty()) {
            return null;
        }
        final int[] keywordAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(keyword));
        final List<String> lines = new ArrayList<>();
        for (final String line : read) {
            final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
            final boolean namesKeyword = at != null && keywordAt != null && at[0] == keywordAt[0] && at[1] == keywordAt[1];
            lines.add(namesKeyword ? SyntaxErrorListener.sentence(keyword) : line);
        }
        return lines;
    }

    /**
     * The token a reported line names: the one whose own line stands at the same place, or null.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param line   the reported line
     * @return the token, or null
     */
    static Token namedBy(final List<Token> spoken, final String line) {
        final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
        if (at == null) {
            return null;
        }
        for (final Token token : spoken) {
            final int[] tokenAt = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(token));
            if (tokenAt != null && tokenAt[0] == at[0] && tokenAt[1] == at[1]) {
                return token;
            }
        }
        return null;
    }

    /** The innermost TRY_CAST read as the construct whose parentheses hold {@code fault}, or null. */
    private static Token tryCastAround(final List<Token> spoken, final Token fault) {
        if (fault == null) {
            return null;
        }
        final int faultIndex = fault.getType() == Token.EOF ? Integer.MAX_VALUE : fault.getTokenIndex();
        Token found = null;
        for (int i = 0; i + 1 < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (token.getType() != FrostlakeLexer.TRY_CAST || spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN
                    || i == 0 || !leadsAValue(spoken, i - 1) || token.getTokenIndex() > faultIndex) {
                continue;
            }
            final Token close = closingParen(spoken, i + 1);
            if (close == null || faultIndex <= close.getTokenIndex()) {
                found = token;
            }
        }
        return found;
    }

    /** The ')' closing the '(' at {@code open}, or null when the text leaves it open. */
    private static Token closingParen(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return spoken.get(i);
            }
        }
        return null;
    }

    /** Whether a value is read right after the token at {@code at}: an operator, a clause word, or a TOP's count. */
    private static boolean leadsAValue(final List<Token> spoken, final int at) {
        final int type = spoken.get(at).getType();
        if (type == FrostlakeLexer.INTEGER_LITERAL) {
            return at > 0 && spoken.get(at - 1).getType() == FrostlakeLexer.TOP;
        }
        switch (type) {
            case FrostlakeLexer.SELECT:
            case FrostlakeLexer.DISTINCT:
            case FrostlakeLexer.COMMA:
            case FrostlakeLexer.LPAREN:
            case FrostlakeLexer.LBRACKET:
            case FrostlakeLexer.WHERE:
            case FrostlakeLexer.HAVING:
            case FrostlakeLexer.QUALIFY:
            case FrostlakeLexer.ON:
            case FrostlakeLexer.BY:
            case FrostlakeLexer.WHEN:
            case FrostlakeLexer.THEN:
            case FrostlakeLexer.ELSE:
            case FrostlakeLexer.AND:
            case FrostlakeLexer.OR:
            case FrostlakeLexer.NOT:
            case FrostlakeLexer.LIKE:
            case FrostlakeLexer.ILIKE:
            case FrostlakeLexer.BETWEEN:
            case FrostlakeLexer.RETURN:
            case FrostlakeLexer.EQ:
            case FrostlakeLexer.NEQ:
            case FrostlakeLexer.LT:
            case FrostlakeLexer.LTE:
            case FrostlakeLexer.GT:
            case FrostlakeLexer.GTE:
            case FrostlakeLexer.PLUS:
            case FrostlakeLexer.MINUS:
            case FrostlakeLexer.STAR:
            case FrostlakeLexer.SLASH:
            case FrostlakeLexer.PERCENT:
            case FrostlakeLexer.PIPE_PIPE:
            case FrostlakeLexer.ARROW:
            case FrostlakeLexer.COLON_EQ:
                return true;
            default:
                return false;
        }
    }
}
