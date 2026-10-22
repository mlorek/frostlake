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
 * The lines the account reports for a row written where a clause takes none (all live-verified over
 * {@code t2 (a INT, b VARCHAR, d DATE)}):
 *
 * <pre>
 *   SELECT a FROM t2 ORDER BY (b DESC, a)   the 'DESC' inside the row, then the row's closing ')'
 *   SELECT a FROM t2 SAMPLE ((1, 2))        the row's '(', then the value after its first comma
 * </pre>
 */
final class ClauseRowLines {

    private ClauseRowLines() {
    }

    /**
     * The lines for a text whose first fault is a direction word inside a row an ORDER BY writes, or the opening of
     * a row a SAMPLE writes; null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 2 || fault >= spoken.size()) {
            return null;
        }
        final int type = spoken.get(fault).getType();
        if (type == FrostlakeLexer.ASC || type == FrostlakeLexer.DESC) {
            final int open = enclosingOpen(spoken, fault);
            if (open < 2 || spoken.get(open - 1).getType() != FrostlakeLexer.BY
                    || spoken.get(open - 2).getType() != FrostlakeLexer.ORDER) {
                return null;
            }
            final int close = matchingClose(spoken, open);
            return close < 0 ? null : twoLines(spoken.get(fault), spoken.get(close));
        }
        if (type == FrostlakeLexer.LPAREN && spoken.get(fault - 1).getType() == FrostlakeLexer.LPAREN
                && (spoken.get(fault - 2).getType() == FrostlakeLexer.SAMPLE
                    || spoken.get(fault - 2).getType() == FrostlakeLexer.TABLESAMPLE)) {
            final int close = matchingClose(spoken, fault);
            for (int i = fault + 1; close > 0 && i + 1 < close; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.COMMA) {
                    return twoLines(spoken.get(fault), spoken.get(i + 1));
                }
            }
        }
        return null;
    }

    private static List<String> twoLines(final Token first, final Token second) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(first));
        lines.add(SyntaxErrorListener.sentence(second));
        return lines;
    }

    /** The index of the parenthesis that opens the group holding {@code at}, or -1 when none does. */
    private static int enclosingOpen(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
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

    /** The index of the parenthesis closing the one at {@code open}, or -1 when none does. */
    private static int matchingClose(final List<Token> spoken, final int open) {
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
}
