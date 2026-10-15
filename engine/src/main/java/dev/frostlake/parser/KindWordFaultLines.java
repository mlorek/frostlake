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
import java.util.List;

/**
 * The lines of a few statements over kinds this engine models no object of, where the account's own reading of the
 * kind's words decides what is reported (all live-verified):
 *
 * <pre>
 *   DESCRIBE RESOURCE x                       the word, then the end of the statement
 *   DESCRIBE RESOURCE x y z                   the word, the word after it, then the end
 *   DESCRIBE STORAGE                          'STORAGE' alone
 *   DESCRIBE STORAGE x                        'STORAGE', then the end of the statement
 *   DESCRIBE STORAGE x y z                    'STORAGE', the second word, then the end
 *   DESCRIBE DBT x y                          the word, then the end of the statement — and so after GIT,
 *                                             SEMANTIC, AUTHENTICATION, SESSION, PACKAGES and PRIVACY
 *   DROP DATA METRIC FUNCTION f(TABLE(INT))   'METRIC' alone, with IF EXISTS too
 *   ALTER EXTERNAL TABLE t SET TAG a = 'x'    the SET alone, whatever tags follow
 *   ALTER EXTERNAL TABLE t UNSET TAG a, b     the first tag's name alone
 * </pre>
 *
 * After RESOURCE without MONITOR and after STORAGE without LIFECYCLE the words are read in pairs from the one after
 * the keyword: the first of a pair is taken and the second refused — a word, the statement's semicolon or its end
 * — so the end is refused only where it falls second. After DBT without PROJECT, and the other kinds named by
 * two words whose second is wrong, the refused word is read as the name and the pairs start behind it. A statement
 * written any other way keeps the lines its parse reports.
 */
final class KindWordFaultLines {

    private KindWordFaultLines() {
    }

    /**
     * The lines of a text that is one statement of these shapes, or null.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken) {
        int end = 0;
        while (end < spoken.size() && spoken.get(end).getType() != Token.EOF) {
            end++;
        }
        if (end < 2 || end >= spoken.size()) {
            return null;
        }
        final int first = spoken.get(0).getType();
        final int second = spoken.get(1).getType();
        if (first == FrostlakeLexer.DESCRIBE || first == FrostlakeLexer.DESC) {
            return describeLines(spoken, end, second);
        }
        if (first == FrostlakeLexer.DROP) {
            return second == FrostlakeLexer.DATA && end >= 4 && spoken.get(2).getType() == FrostlakeLexer.METRIC
                && spoken.get(3).getType() == FrostlakeLexer.FUNCTION ? sentences(spoken.get(2), null) : null;
        }
        return first == FrostlakeLexer.ALTER && second == FrostlakeLexer.EXTERNAL && end >= 6
            && spoken.get(2).getType() == FrostlakeLexer.TABLE ? tagActionLines(spoken, end) : null;
    }

    private static List<String> describeLines(final List<Token> spoken, final int end, final int second) {
        final boolean resource = second == FrostlakeLexer.RESOURCE && end >= 3
            && spoken.get(2).getType() != FrostlakeLexer.MONITOR;
        final boolean storage = second == FrostlakeLexer.STORAGE
            && (end == 2 || !UnmodelledKindWords.is(spoken.get(2), "LIFECYCLE"));
        final boolean named = end >= 4 && readsItsWordAsTheName(second, spoken.get(2));
        if (!resource && !storage && !named) {
            return null;
        }
        // The token that closes the statement: its semicolon, or the end of the text.
        final int last = spoken.get(end - 1).getType() == FrostlakeLexer.SEMI ? end - 1 : end;
        for (int i = 2; i < last; i++) {
            if (!SyntaxErrorListener.isNameLike(spoken.get(i))) {
                return null;
            }
        }
        // The pairs start at the word after the keyword, or one word later behind a word read as the name.
        final int firstRefused = named ? 4 : 3;
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(storage ? 1 : 2)));
        for (int i = firstRefused; i <= last; i += 2) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
        }
        return lines;
    }

    /**
     * Whether the kind's first keyword is one whose second word, written wrong, is refused and read as the name:
     * DBT without PROJECT, GIT without REPOSITORY, SEMANTIC without VIEW, and AUTHENTICATION, SESSION, PACKAGES
     * and PRIVACY without POLICY.
     */
    private static boolean readsItsWordAsTheName(final int first, final Token word) {
        if (first == FrostlakeLexer.DBT) {
            return !UnmodelledKindWords.is(word, "PROJECT");
        }
        if (first == FrostlakeLexer.GIT) {
            return word.getType() != FrostlakeLexer.REPOSITORY;
        }
        if (first == FrostlakeLexer.SEMANTIC) {
            return word.getType() != FrostlakeLexer.VIEW;
        }
        return (first == FrostlakeLexer.AUTHENTICATION || first == FrostlakeLexer.SESSION
            || first == FrostlakeLexer.PACKAGES || first == FrostlakeLexer.PRIVACY)
            && word.getType() != FrostlakeLexer.POLICY;
    }

    /** The table's name, with IF EXISTS before it or not, and then SET TAG or UNSET TAG and a tag's name. */
    private static List<String> tagActionLines(final List<Token> spoken, final int end) {
        int at = spoken.get(3).getType() == FrostlakeLexer.IF && spoken.get(4).getType() == FrostlakeLexer.EXISTS
            ? 5 : 3;
        if (at >= end || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return null;
        }
        at++;
        while (at + 1 < end && spoken.get(at).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isNameLike(spoken.get(at + 1))) {
            at += 2;
        }
        if (at + 1 >= end || spoken.get(at + 1).getType() != FrostlakeLexer.TAG) {
            return null;
        }
        if (spoken.get(at).getType() == FrostlakeLexer.SET) {
            return sentences(spoken.get(at), null);
        }
        return spoken.get(at).getType() == FrostlakeLexer.UNSET && at + 2 < end
            && SyntaxErrorListener.isNameLike(spoken.get(at + 2)) ? sentences(spoken.get(at + 2), null) : null;
    }

    private static List<String> sentences(final Token first, final Token second) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(first));
        if (second != null) {
            lines.add(SyntaxErrorListener.sentence(second));
        }
        return lines;
    }
}
