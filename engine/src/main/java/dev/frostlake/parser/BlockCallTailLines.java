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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The lines live reports for a block's statement that opens with a call and is no whole call statement, and for
 * a statement opened by a word that opens no call. The account reads {@code name(…)} at a statement's start as a
 * call statement and refuses what follows it; this grammar reads no such statement and refuses the call's name or
 * parenthesis (all live-verified):
 *
 * <pre>
 *   BEGIN ABS(1) + 1; …            '+'                  an operator after the call, '*' and '||' alike
 *   BEGIN ABS(1)::INT; RETURN 1    '::', then 'RETURN'  a cast, then the next statement's word
 *   BEGIN SYSTEM$WAIT(0) RETURN 1  'RETURN', then '1'   a statement's word, then the token after it
 *   BEGIN ABS(1) ABS(2); …         'ABS', then '('      another call's name, then its parenthesis
 *   BEGIN ABS(1); RETURN 1 END;    'END'                a whole call statement is read past
 *   BEGIN "ABS"(1); …              '"ABS"'              a quoted name, LEFT, RIGHT and IDENTIFIER: the word
 *   BEGIN CAST('1' AS INT); …      'CAST', then ')'     CAST and TRY_CAST: the word and their closing parenthesis
 *   BEGIN S.F(1); …                '.', then '('        a path: its first dot, then what follows the next name
 * </pre>
 */
final class BlockCallTailLines {

    /** The operators the account refuses alone after a call. */
    private static final Set<Integer> OPERATORS = new HashSet<Integer>(Arrays.asList(FrostlakeLexer.PLUS,
        FrostlakeLexer.MINUS, FrostlakeLexer.STAR, FrostlakeLexer.SLASH, FrostlakeLexer.PERCENT,
        FrostlakeLexer.PIPE_PIPE));

    private BlockCallTailLines() {
    }

    /**
     * The lines for a block whose first fault stands in a statement of one of those shapes, or null.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null || spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.BEGIN) {
            return null;
        }
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0 && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        int start = fault;
        while (start > 0 && !opensStatement(type(spoken, start - 1))) {
            start--;
        }
        if (fault < 1 || start < 1) {
            return null;
        }
        final Token word = spoken.get(start);
        if (start == 1 && (word.getType() == FrostlakeLexer.WORK || word.getType() == FrostlakeLexer.TRANSACTION
                || word.getType() == FrostlakeLexer.NAME)) {
            // BEGIN WORK, BEGIN TRANSACTION and BEGIN NAME start a transaction: no block, so no call statement.
            return null;
        }
        final int after = type(spoken, start + 1);
        if (after == FrostlakeLexer.LPAREN && (word.getType() == FrostlakeLexer.QUOTED_IDENTIFIER
                || word.getType() == FrostlakeLexer.LEFT || word.getType() == FrostlakeLexer.RIGHT
                || isIdentifierWord(word.getType()))) {
            return Collections.singletonList(SyntaxErrorListener.sentence(word));
        }
        if (after == FrostlakeLexer.LPAREN
                && (word.getType() == FrostlakeLexer.CAST || word.getType() == FrostlakeLexer.TRY_CAST)) {
            final int close = closingParen(spoken, start + 1);
            return close < 0 ? null : twoLines(spoken.get(start), spoken.get(close));
        }
        if (!isCallName(word)) {
            return null;
        }
        if (after == FrostlakeLexer.DOT && isCallName(token(spoken, start + 2)) && !ends(spoken, start + 3)) {
            return twoLines(spoken.get(start + 1), spoken.get(start + 3));
        }
        if (after != FrostlakeLexer.LPAREN) {
            return null;
        }
        final int close = closingParen(spoken, start + 1);
        if (close < 0 || fault > close + 1) {
            return null;
        }
        final int next = type(spoken, close + 1);
        if (next == FrostlakeLexer.SEMI) {
            return readPast(sql, word, spoken.get(close + 1));
        }
        if (OPERATORS.contains(Integer.valueOf(next))) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(close + 1)));
        }
        if (next == FrostlakeLexer.DOUBLE_COLON) {
            int semi = close + 1;
            while (!ends(spoken, semi)) {
                semi++;
            }
            return type(spoken, semi) != FrostlakeLexer.SEMI || ends(spoken, semi + 1) ? null
                : twoLines(spoken.get(close + 1), spoken.get(semi + 1));
        }
        if (next == FrostlakeLexer.RETURN && !ends(spoken, close + 2)
                || isCallName(token(spoken, close + 1)) && type(spoken, close + 2) == FrostlakeLexer.LPAREN) {
            return twoLines(spoken.get(close + 1), spoken.get(close + 2));
        }
        return null;
    }

    /**
     * The lines of the text with a whole call statement read past: the statement, its semicolon included, is blanked
     * where it stands, every line break kept, and what the text then says wrong is what live reports.
     */
    private static List<String> readPast(final String sql, final Token name, final Token semicolon) {
        final char[] text = sql.toCharArray();
        for (int c = name.getStartIndex(); c <= semicolon.getStopIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        final List<String> lines = SyntaxErrorListener.linesReportedAfresh(new String(text));
        return lines.isEmpty() ? null : lines;
    }

    /** Whether a token of this type is the word IDENTIFIER, which the token source names by what follows it. */
    private static boolean isIdentifierWord(final int type) {
        return type == FrostlakeLexer.KW_IDENTIFIER || type == FrostlakeParser.KW_IDENTIFIER_REF
            || type == FrostlakeParser.KW_IDENTIFIER_OPEN;
    }

    private static List<String> twoLines(final Token first, final Token second) {
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(first));
        lines.add(SyntaxErrorListener.sentence(second));
        return lines;
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1 where the statement leaves it open. */
    private static int closingParen(final List<Token> spoken, final int open) {
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

    /** Whether a call's name may be the token, as a call statement's is (see {@link BlockCallStatementReading}). */
    private static boolean isCallName(final Token token) {
        return token != null && BlockCallStatementReading.isCallName(token);
    }

    /** Whether a block's statement opens after a token of that type: its first, a branch's first, or a semicolon's. */
    private static boolean opensStatement(final int type) {
        return type == FrostlakeLexer.SEMI || type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.THEN
            || type == FrostlakeLexer.ELSE || type == FrostlakeLexer.DO;
    }

    /** Whether the statement ends at {@code at}: the end of input, a semicolon, or no token at all. */
    private static boolean ends(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    private static Token token(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at) : null;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
