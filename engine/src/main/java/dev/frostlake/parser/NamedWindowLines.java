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
import java.util.Locale;

import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a named window, a WINDOW clause it does not take (live-verified): every name an
 * OVER reads, {@code OVER w} or {@code OVER (w)}, then the WINDOW clause's own — after the FROM's name the WINDOW
 * stands as that name's alias, so the window's name is refused, and the token after the first item its ORDER BY reads;
 * after any other clause the WINDOW itself, and nothing more.
 *
 * <pre>
 *   SELECT SUM(v) OVER w FROM ev WINDOW w AS (ORDER BY v ROWS …)          'w', 'w', 'ROWS'
 *   SELECT SUM(v) OVER w FROM ev WINDOW w AS (PARTITION BY v ORDER BY v)  'w', 'w', ')'
 *   SELECT SUM(v) OVER w, MAX(v) OVER w FROM ev WINDOW w AS (ORDER BY v)  'w', 'w', 'w', ')'
 *   SELECT SUM(v) OVER (w) FROM ev WINDOW w AS (ORDER BY v)               'w', 'w', ')'
 *   SELECT v FROM ev WINDOW w AS (ORDER BY v)                             'w', ')'
 *   SELECT SUM(v) OVER w FROM ev WHERE v > 0 WINDOW w AS (ORDER BY v)     'w', 'WINDOW'
 * </pre>
 */
final class NamedWindowLines {

    private NamedWindowLines() {
    }

    /**
     * The lines for a text whose first fault is a name an OVER reads, or the name a WINDOW clause defines, in a
     * statement that holds a WINDOW clause; null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        final int window = fault < 0 || fault >= spoken.size() ? -1 : windowClause(spoken);
        if (window < 0) {
            return null;
        }
        // The parse may name the OVER that reads the name, or its parenthesis; the name is what is refused.
        int first = fault;
        while (first + 1 < spoken.size() && first < fault + 2 && !isReference(spoken, first)
                && (spoken.get(first).getType() == FrostlakeLexer.OVER
                    || spoken.get(first).getType() == FrostlakeLexer.LPAREN)) {
            first++;
        }
        if (first > window + 1 || first != window + 1 && !isReference(spoken, first)) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        for (int i = first; i < window; i++) {
            if (isReference(spoken, i)) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
            }
        }
        if (window >= 2 && SyntaxErrorListener.isNameLike(spoken.get(window - 1))
                && spoken.get(window - 2).getType() == FrostlakeLexer.FROM) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(window + 1)));
            final Token after = afterOrderItem(spoken, window + 2);
            if (after != null) {
                lines.add(SyntaxErrorListener.sentence(after));
            }
        } else {
            lines.add(SyntaxErrorListener.sentence(spoken.get(window)));
        }
        return lines;
    }

    /** The index of the WINDOW of a {@code WINDOW name AS (} clause, or -1. */
    private static int windowClause(final List<Token> spoken) {
        for (int i = 1; i + 3 < spoken.size(); i++) {
            final Token word = spoken.get(i);
            if (word.getText() != null && "WINDOW".equals(word.getText().toUpperCase(Locale.ROOT))
                    && SyntaxErrorListener.isNameLike(spoken.get(i + 1))
                    && spoken.get(i + 2).getType() == FrostlakeLexer.AS
                    && spoken.get(i + 3).getType() == FrostlakeLexer.LPAREN) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the token at {@code at} is a name an OVER reads: right after OVER, or after OVER's parenthesis. */
    private static boolean isReference(final List<Token> spoken, final int at) {
        if (at < 1 || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return false;
        }
        final int before = spoken.get(at - 1).getType();
        return before == FrostlakeLexer.OVER
            || before == FrostlakeLexer.LPAREN && at >= 2 && spoken.get(at - 2).getType() == FrostlakeLexer.OVER;
    }

    /** The token after the first item the window's ORDER BY reads, from its {@code AS (}; null when it has none. */
    private static Token afterOrderItem(final List<Token> spoken, final int as) {
        int depth = 0;
        for (int i = as + 2; i + 3 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.ORDER
                    && spoken.get(i + 1).getType() == FrostlakeLexer.BY) {
                return spoken.get(i + 3);
            } else if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        return null;
    }
}
