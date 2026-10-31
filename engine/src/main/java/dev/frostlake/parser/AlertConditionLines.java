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
 * The lines the account reports for an alert whose condition is no query: {@code IF (EXISTS (<statement>))} written
 * with a CREATE, an INSERT, an UPDATE, a DELETE, a DROP, a TRUNCATE, a DESCRIBE or a VALUES. The statement's first
 * word is refused, and the account then reads no bracket of the statement: the first ')' after the word closes the
 * EXISTS, the next the IF, and THEN is due — so whatever stands in their place is one more line (all live-verified):
 *
 * <pre>
 *   IF (EXISTS (DROP TABLE zz)) THEN …                 'DROP' alone
 *   IF (EXISTS (CREATE TABLE zz (x INT))) THEN …       'CREATE', then the last ')': one too many
 *   IF (EXISTS (CREATE TABLE zz (x INT) y)) THEN …     'CREATE', then the 'y' where the IF's ')' is due
 *   IF (EXISTS (VALUES (1))) THEN …                    'VALUES', then the last ')'
 *   IF (EXISTS (DROP TABLE zz (a))) THEN …             'DROP', then the last ')'
 * </pre>
 *
 * <p>A query's EXISTS with a word after its closing parenthesis refuses the word alone: the brackets after it close
 * the EXISTS and the IF — {@code IF (EXISTS (SELECT 1) x)) THEN …} is the {@code x} (live-verified).
 */
final class AlertConditionLines {

    private AlertConditionLines() {
    }

    /**
     * The lines of a CREATE ALERT whose condition opens with such a word, when the parse's first fault stands at
     * the word or after it.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @param lines      the lines worked out for the text
     * @return {@code lines}, or the condition's lines
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final List<String> lines) {
        if (spoken.size() < 10 || spoken.get(0).getType() != FrostlakeLexer.CREATE) {
            return lines;
        }
        boolean alert = false;
        for (int i = 1; i < 4; i++) {
            alert = alert || spoken.get(i).getType() == FrostlakeLexer.ALERT;
        }
        if (!alert) {
            return lines;
        }
        final int end = spoken.size() - 1;
        int word = -1;
        for (int i = 3; i + 2 < end && word < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.EXISTS && spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i - 2).getType() == FrostlakeLexer.IF
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                word = i + 2;
            }
        }
        if (word >= 0 && !opensNoQuery(spoken.get(word).getType())) {
            final int close = closingParen(spoken, word - 1);
            return close > 0 && close + 1 < end && spoken.get(close + 1).getTokenIndex() == faultIndex
                && lines.size() > 1 ? lines.subList(0, 1) : lines;
        }
        // A fault at the end of the text stands after the word too.
        if (word < 0 || faultIndex >= 0 && spoken.get(word).getTokenIndex() > faultIndex) {
            return lines;
        }
        final List<String> read = new ArrayList<>();
        read.add(SyntaxErrorListener.sentence(spoken.get(word)));
        int close = word + 1;
        while (close < end && spoken.get(close).getType() != FrostlakeLexer.RPAREN) {
            close++;
        }
        if (close + 2 > end) {
            return lines;
        }
        if (spoken.get(close + 1).getType() != FrostlakeLexer.RPAREN) {
            read.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
        } else if (spoken.get(close + 2).getType() != FrostlakeLexer.THEN) {
            read.add(SyntaxErrorListener.sentence(spoken.get(close + 2)));
        }
        return read;
    }

    /** The index of the ')' closing the '(' at {@code open}, or -1. */
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

    private static boolean opensNoQuery(final int type) {
        return type == FrostlakeLexer.CREATE || type == FrostlakeLexer.INSERT || type == FrostlakeLexer.UPDATE
            || type == FrostlakeLexer.DELETE || type == FrostlakeLexer.DROP || type == FrostlakeLexer.TRUNCATE
            || type == FrostlakeLexer.DESCRIBE || type == FrostlakeLexer.DESC || type == FrostlakeLexer.VALUES;
    }
}
