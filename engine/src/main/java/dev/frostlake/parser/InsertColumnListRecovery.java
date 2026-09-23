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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports for a fault in the column list of an INSERT (all live-verified). Live reads the list one
 * name at a time:
 *
 * <pre>
 *   INSERT INTO t1 (a, 'b') VALUES (1)            ''b''                 the list reads on at the next ',' or ')'
 *   INSERT INTO t1 (a, 'b', 'c') VALUES (1)       ''b'', then ''c''
 *   INSERT INTO t1 (a b c) VALUES (1)             'b'
 *   INSERT INTO t1 (a, 'b') VALUES (1) x          ''b'', then 'x'       the statement reads on after the list
 *   INSERT INTO t1 (a, 'b' VALUES (1)             ''b'', then '&lt;EOF&gt;'  the first ')' ends the list
 *   INSERT INTO t1 ('a', b) VALUES (1)            ''a'', then 'VALUES'  a first item that is no name gives the list
 *   INSERT INTO t1 () VALUES (1)                  ')', then 'VALUES'    up: the token after its ')' is refused
 *   INSERT INTO t1 (UPPER('a')) VALUES (1)        '(', ''a'', then ')'  a call: its '(', the token after it, and the
 *   INSERT INTO t1 (UPPER(a), b) VALUES (1)       '(', 'a', then ','    token after the first ')' after that
 *   INSERT INTO t1 (UPPER()) VALUES (1)           '('                   an empty call: its '(' alone
 * </pre>
 *
 * <p>This parser, unable to predict the list at all, read the bracket as a query's and named whatever inside it or
 * after it could not continue one.
 */
final class InsertColumnListRecovery {

    private static final IntervalSet NAMES =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private InsertColumnListRecovery() {
    }

    /**
     * The lines live reports when the parse's first line names a token in an INSERT's column list, or null when it
     * names anything else.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param namedIndex     the token index of the token the parse's first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> refusal(final String sql, final List<Token> spoken, final int namedIndex,
                                final boolean statementParse) {
        if (sql == null || namedIndex < 0 || sql.length() != sql.codePointCount(0, sql.length())) {
            return null;
        }
        final int open = listOpen(sql, spoken, namedIndex);
        if (open < 0) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        boolean expectName = true;
        int close = -1;
        for (int i = open + 1; i < spoken.size() && close < 0; i++) {
            final Token token = spoken.get(i);
            if (token.getType() == Token.EOF) {
                return null;
            }
            if (expectName && isName(token)) {
                expectName = false;
                continue;
            }
            // An item may carry ONE qualifier — INSERT INTO t1 (t1.a) — so a dot with a name after it
            // continues the item rather than ending it. A SECOND dot is the fault, which is where live
            // reports it too.
            if (!expectName && token.getType() == FrostlakeLexer.DOT
                    && i + 1 < spoken.size() && isName(spoken.get(i + 1))
                    && (i < 2 || spoken.get(i - 2).getType() != FrostlakeLexer.DOT)) {
                i++;
                continue;
            }
            if (!expectName && token.getType() == FrostlakeLexer.COMMA) {
                expectName = true;
                continue;
            }
            if (!expectName && token.getType() == FrostlakeLexer.RPAREN) {
                close = i;
                continue;
            }
            if (lines.isEmpty() && token.getTokenIndex() != namedIndex) {
                return null;
            }
            if (!expectName && token.getType() == FrostlakeLexer.LPAREN) {
                return callLines(spoken, i, lines);
            }
            if (i == open + 1) {
                return firstItemLines(spoken, i);
            }
            lines.add(token);
            final int resume = token.getType() == FrostlakeLexer.COMMA || token.getType() == FrostlakeLexer.RPAREN
                ? i : nextSeparator(spoken, i + 1);
            if (resume < 0) {
                return sentences(lines);
            }
            if (spoken.get(resume).getType() == FrostlakeLexer.RPAREN) {
                close = resume;
            } else {
                expectName = true;
                i = resume;
            }
        }
        if (lines.isEmpty() || close < 0) {
            return null;
        }
        final List<String> sentences = sentences(lines);
        sentences.addAll(linesAfterList(sql, spoken, open, close, statementParse));
        return sentences;
    }

    /**
     * The index in {@code spoken} of the '(' that opens the column list of the INSERT holding the named token, or -1:
     * the statement's text up to that bracket, completed by a one-name list and a VALUES row, must parse cleanly.
     */
    private static int listOpen(final String sql, final List<Token> spoken, final int namedIndex) {
        int start = 0;
        int named = -1;
        for (int i = 0; i < spoken.size() && named < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                start = i + 1;
            }
            if (spoken.get(i).getTokenIndex() == namedIndex) {
                named = i;
            }
        }
        if (named < 0 || start >= named || spoken.get(start).getType() != FrostlakeLexer.INSERT) {
            return -1;
        }
        int open = -1;
        for (int i = start + 1; i < named && open < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                open = i;
            }
        }
        if (open < 0) {
            return -1;
        }
        final String probe = sql.substring(spoken.get(start).getStartIndex(), spoken.get(open).getStopIndex() + 1)
            + "a) VALUES (1)";
        return parsesCleanly(probe) ? open : -1;
    }

    /** Whether a text lexes and parses without a single fault, read by a parser that reports to no one. */
    private static boolean parsesCleanly(final String text) {
        final int[] faults = new int[1];
        final BaseErrorListener counter = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                faults[0]++;
            }
        };
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(counter);
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(counter);
        parser.sqlScript();
        return faults[0] == 0;
    }

    /** A call written as an item: its '(', the token after it, and the token after the first ')' after that. */
    private static List<String> callLines(final List<Token> spoken, final int paren, final List<Token> lines) {
        lines.add(spoken.get(paren));
        final Token inside = at(spoken, paren + 1);
        if (inside == null || inside.getType() == FrostlakeLexer.RPAREN) {
            return sentences(lines);
        }
        lines.add(inside);
        for (int i = paren + 2; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                final Token after = at(spoken, i + 1);
                if (after != null && after.getType() != FrostlakeLexer.SEMI) {
                    lines.add(after);
                }
                break;
            }
        }
        return sentences(lines);
    }

    /** A first item that is no name: that token, then the token after the first ')' from it on. */
    private static List<String> firstItemLines(final List<Token> spoken, final int first) {
        if (spoken.get(first).getType() == FrostlakeLexer.LPAREN) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        lines.add(spoken.get(first));
        for (int i = first; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                final Token after = at(spoken, i + 1);
                if (after != null && after.getType() != FrostlakeLexer.SEMI) {
                    lines.add(after);
                }
                break;
            }
        }
        return sentences(lines);
    }

    /**
     * The first line the statement's text gives after the list closing at {@code spoken[close]}, read with the list
     * holding one name — live reports one fault there and no more. The text is blanked in place, so every position
     * stays where it was written.
     */
    private static List<String> linesAfterList(final String sql, final List<Token> spoken, final int open,
                                               final int close, final boolean statementParse) {
        final char[] text = sql.toCharArray();
        for (int c = spoken.get(open).getStopIndex() + 1; c < spoken.get(close).getStartIndex(); c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
        text[spoken.get(open).getStopIndex() + 1] = 'a';
        final int[] closeAt = {spoken.get(close).getLine(), spoken.get(close).getCharPositionInLine()};
        final List<String> after = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), statementParse)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && (place[0] > closeAt[0] || place[0] == closeAt[0] && place[1] > closeAt[1])) {
                after.add(line);
                break;
            }
        }
        return after;
    }

    /** The index of the first ',' or ')' from {@code from} on, or -1. */
    private static int nextSeparator(final List<Token> spoken, final int from) {
        for (int i = from; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.RPAREN) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isName(final Token token) {
        return NAMES.contains(token.getType()) || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /** The token at {@code i}, or null at or past the end of input. */
    private static Token at(final List<Token> spoken, final int i) {
        return i < spoken.size() && spoken.get(i).getType() != Token.EOF ? spoken.get(i) : null;
    }

    private static List<String> sentences(final List<Token> tokens) {
        final List<String> sentences = new ArrayList<>();
        for (final Token token : tokens) {
            sentences.add(SyntaxErrorListener.sentence(token));
        }
        return sentences;
    }
}
