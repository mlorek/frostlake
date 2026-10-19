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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * A script's first statement with a literal or a word run in after it, in the shapes whose lines the account
 * reports in a way of its own (all live-verified; each is followed by a later statement, whose faults the account
 * goes on to report or does not):
 *
 * <pre>
 *   BEGIN 1; …   BEGIN 'q'; …   BEGIN -1; …      the literal or the sign, and the later statements are read
 *   BEGIN zz; …                                  the ';', and the later statements are read
 *   COMMIT NULL; …   ROLLBACK NULL; …            'NULL' alone
 *   SELECT 1 INTO t.u 1; …                       the '.' alone
 *   DELETE FROM t USING n 1; …                   the literal, and the later statements are read
 *   USE SCHEMA s -1; …                           the '-', and the later statements are read
 *   SELECT ABS(1) 'q'; …                         nothing of its own: a text after a call is read with it
 *   DESCRIBE TABLE t zz; …   LIST @s zz; …       the ';' — the word is read as a property or a pattern — and of two
 *   COPY INTO t FROM @s zz; …   REMOVE @s zz; …  words the second; the later statements are read
 *   DESC t zz; …                                 'DESC', and the later statements are read — for a name the account
 *                                                keeps as a keyword; DESC abc zz ww; … is 'ww' alone
 *   SHOW TABLES IN t zz; …                       the word alone, when the scope is a word the account keeps as a
 *                                                keyword (T, D, …): any other scope names a class, judged later
 *   DROP TABLE t .; …                            the ';', and the later statements are read
 *   EXECUTE IMMEDIATE 'x' zz; …                  the word, and the later statements are read
 *   … FETCH FIRST 1 ROWS ONLY zz; …              the word alone — and so after a USING list and a SAMPLE
 *   … GROUP BY a, b zz; …                        the word, and the later statements are read
 * </pre>
 */
final class RunInStatementLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    /** A statement no parse refuses, put where the first statement stood when the later ones are read. */
    private static final String FILLER = "COMMIT";

    private RunInStatementLines() {
    }

    /**
     * The lines of a script whose first statement is one of these shapes, or null.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1 for the end of the input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        if (sql == null) {
            return null;
        }
        int end = 0;
        while (end < spoken.size() && spoken.get(end).getType() != FrostlakeLexer.SEMI
                && spoken.get(end).getType() != Token.EOF) {
            end++;
        }
        if (end < 2 || end + 2 >= spoken.size() || spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            return null;
        }
        final List<Token> own = new ArrayList<>();
        final Boolean later = shape(spoken, end, faultIndex, own);
        if (later == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        for (final Token token : own) {
            lines.add(SyntaxErrorListener.sentence(token));
        }
        if (later.booleanValue()) {
            final char[] text = sql.toCharArray();
            final int from = spoken.get(0).getStartIndex();
            final int to = spoken.get(end).getStartIndex() - 1;
            if (to - from + 1 < FILLER.length()) {
                return null;
            }
            for (int c = from; c <= to; c++) {
                if (text[c] != '\n' && text[c] != '\r') {
                    text[c] = ' ';
                }
            }
            for (int c = 0; c < FILLER.length(); c++) {
                text[from + c] = FILLER.charAt(c);
            }
            lines.addAll(SyntaxErrorListener.linesReportedAfresh(new String(text)));
        }
        return lines.isEmpty() ? null : lines;
    }

    /**
     * Fills {@code own} with the tokens the first statement is refused at and tells whether the later statements
     * are read (TRUE) or passed over (FALSE); null when the statement is none of the shapes.
     */
    private static Boolean shape(final List<Token> spoken, final int end, final int faultIndex,
                                 final List<Token> own) {
        final int first = spoken.get(0).getType();
        final int second = spoken.get(1).getType();
        final Token last = spoken.get(end - 1);
        if (first == FrostlakeLexer.BEGIN) {
            for (final Token token : spoken) {
                if (token.getType() == FrostlakeLexer.END) {
                    // A block, whatever it holds.
                    return null;
                }
            }
            if (isLiteral(second) || second == FrostlakeLexer.MINUS || second == FrostlakeLexer.PLUS) {
                own.add(spoken.get(1));
                return Boolean.TRUE;
            }
            if (end == 2 && second == FrostlakeLexer.IDENTIFIER && !opensATransaction(spoken.get(1))) {
                own.add(spoken.get(end));
                return Boolean.TRUE;
            }
            return null;
        }
        if ((first == FrostlakeLexer.COMMIT || first == FrostlakeLexer.ROLLBACK) && end == 2
                && second == FrostlakeLexer.NULL) {
            own.add(spoken.get(1));
            return Boolean.FALSE;
        }
        if (first == FrostlakeLexer.DELETE && end == 6 && second == FrostlakeLexer.FROM
                && spoken.get(3).getType() == FrostlakeLexer.USING && isName(spoken.get(4).getType())
                && isLiteral(last.getType())) {
            own.add(last);
            return Boolean.TRUE;
        }
        if (first == FrostlakeLexer.USE && end >= 5 && second == FrostlakeLexer.SCHEMA
                && spoken.get(3).getType() == FrostlakeLexer.MINUS) {
            own.add(spoken.get(3));
            return Boolean.TRUE;
        }
        final boolean describes = first == FrostlakeLexer.DESCRIBE || first == FrostlakeLexer.DESC;
        if (describes && end >= 4 && end <= 6 && second != FrostlakeLexer.IDENTIFIER
                && second != FrostlakeLexer.QUOTED_IDENTIFIER
                && isName(spoken.get(2).getType()) && isName(spoken.get(3).getType()) && isName(last.getType())
                && (end < 5 || isName(spoken.get(4).getType()))) {
            // The words after the name are read in pairs, a property and its value: the first is taken, the
            // second refused, and where a word is taken the end of the statement after it is refused.
            if (end != 4) {
                own.add(spoken.get(4));
            }
            if (end != 5) {
                own.add(spoken.get(end));
            }
            return Boolean.TRUE;
        }
        if (first == FrostlakeLexer.DESC && end == 3 && second == FrostlakeLexer.IDENTIFIER
                && !ClassNameWords.isClassWord(spoken.get(1)) && isName(last.getType())) {
            // DESC before a word the account keeps as a keyword — T, D and their like — is refused itself; any
            // other name is described, and the word after it judged when the statement runs.
            own.add(spoken.get(0));
            return Boolean.TRUE;
        }
        if (describes && end == 4 && second == FrostlakeLexer.IDENTIFIER
                && ClassNameWords.isClassWord(spoken.get(1)) && isName(spoken.get(2).getType())
                && isName(last.getType())) {
            // With no kind, a second word after the name is refused, and nothing after it is told.
            own.add(last);
            return Boolean.FALSE;
        }
        if (first == FrostlakeLexer.SHOW && end >= 5 && spoken.get(2).getType() == FrostlakeLexer.IN
                && spoken.get(3).getType() == FrostlakeLexer.IDENTIFIER
                && !ClassNameWords.isClassWord(spoken.get(3)) && isName(spoken.get(4).getType())) {
            // A scope that is a word the account keeps as a keyword — T, D and their like — reads no name after
            // it: the word there is refused, and nothing after it is told. Any other name is a class, whose
            // instance the word is, and that is judged when the statement runs.
            own.add(spoken.get(4));
            return Boolean.FALSE;
        }
        if (first == FrostlakeLexer.DROP && end == 4 && isName(spoken.get(2).getType())
                && last.getType() == FrostlakeLexer.DOT) {
            // A dot the name ends with: the end of the statement is refused, not the dot.
            own.add(spoken.get(end));
            return Boolean.TRUE;
        }
        if ((first == FrostlakeLexer.LIST || first == FrostlakeLexer.REMOVE) && second == FrostlakeLexer.AT
                && (end == 4 || end == 5) && isName(spoken.get(3).getType()) && isName(last.getType())) {
            own.add(end == 4 ? spoken.get(end) : last);
            return Boolean.TRUE;
        }
        if (first == FrostlakeLexer.COPY && end == 7 && second == FrostlakeLexer.INTO
                && spoken.get(3).getType() == FrostlakeLexer.FROM && spoken.get(4).getType() == FrostlakeLexer.AT
                && isName(last.getType())) {
            own.add(spoken.get(end));
            return Boolean.TRUE;
        }
        if (first == FrostlakeLexer.EXECUTE && end == 4 && second == FrostlakeLexer.IMMEDIATE
                && spoken.get(2).getType() == FrostlakeLexer.STRING_LITERAL && isName(last.getType())) {
            own.add(last);
            return Boolean.TRUE;
        }
        return first == FrostlakeLexer.SELECT ? queryShape(spoken, end, faultIndex, own) : null;
    }

    private static Boolean queryShape(final List<Token> spoken, final int end, final int faultIndex,
                                      final List<Token> own) {
        final Token last = spoken.get(end - 1);
        final int before = spoken.get(end - 2).getType();
        for (int i = 1; i + 2 < end; i++) {
            // SELECT … INTO t.u: the dot is refused, and nothing after it is told.
            if (spoken.get(i).getType() == FrostlakeLexer.INTO && isName(spoken.get(i + 1).getType())
                    && spoken.get(i + 2).getType() == FrostlakeLexer.DOT
                    && spoken.get(i + 2).getTokenIndex() == faultIndex) {
                own.add(spoken.get(i + 2));
                return Boolean.FALSE;
            }
        }
        if (last.getType() == FrostlakeLexer.STRING_LITERAL && before == FrostlakeLexer.RPAREN && end >= 5
                && isName(spoken.get(1).getType()) && spoken.get(2).getType() == FrostlakeLexer.LPAREN
                && closes(spoken, 2, end - 2) && last.getTokenIndex() == faultIndex) {
            // A text after a call is read with it, as a typed literal is.
            return Boolean.TRUE;
        }
        if (!isName(last.getType()) || last.getTokenIndex() != faultIndex) {
            return null;
        }
        if (before == FrostlakeLexer.ONLY) {
            own.add(last);
            return Boolean.FALSE;
        }
        if (before == FrostlakeLexer.RPAREN) {
            final int open = opening(spoken, end - 2);
            final int opener = open > 0 ? spoken.get(open - 1).getType() : Token.EOF;
            if (opener == FrostlakeLexer.USING || opener == FrostlakeLexer.SAMPLE
                    || opener == FrostlakeLexer.TABLESAMPLE) {
                own.add(last);
                return Boolean.FALSE;
            }
            return null;
        }
        if (end >= 8 && isName(before) && spoken.get(end - 3).getType() == FrostlakeLexer.COMMA
                && isName(spoken.get(end - 4).getType()) && spoken.get(end - 5).getType() == FrostlakeLexer.BY
                && spoken.get(end - 6).getType() == FrostlakeLexer.GROUP) {
            own.add(last);
            return Boolean.TRUE;
        }
        return null;
    }

    /** Whether the bracket at {@code open} closes at {@code close}. */
    private static boolean closes(final List<Token> spoken, final int open, final int close) {
        return opening(spoken, close) == open;
    }

    /** The '(' the ')' at {@code close} closes, or -1. */
    private static int opening(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a word after BEGIN is one a transaction's opening takes. */
    private static boolean opensATransaction(final Token word) {
        final String text = word.getText();
        return "WORK".equalsIgnoreCase(text) || "TRANSACTION".equalsIgnoreCase(text) || "NAME".equalsIgnoreCase(text);
    }

    private static boolean isLiteral(final int type) {
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
