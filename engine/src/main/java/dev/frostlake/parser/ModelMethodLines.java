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
 * The lines the account reports for a model's method named inside TABLE( … ) without its argument list,
 * {@code TABLE(m!predict)}: the token after the method's name is refused, and the recovery then skips to the
 * parenthesis that closes the TABLE and refuses the token after it too, the end of the text included (all
 * live-verified):
 *
 * <pre>
 *   SELECT * FROM TABLE(x!y)                  ')' at 23, '&lt;EOF&gt;' at 24
 *   SELECT * FROM TABLE(x!y) t                ')' at 23, 't' at 25
 *   SELECT * FROM TABLE(x!y) WHERE 1 = 1      ')' at 23, 'WHERE' at 25
 *   SELECT * FROM TABLE(x!y), pq3             ')' at 23, ',' at 24
 *   SELECT * FROM TABLE(x!y 1)                '1' at 24, '&lt;EOF&gt;' at 26
 *   SELECT * FROM TABLE(x.z!y)                ')' at 25, '&lt;EOF&gt;' at 26
 * </pre>
 */
final class ModelMethodLines {

    private ModelMethodLines() {
    }

    /**
     * The lines for a text whose first fault follows a model's method named inside TABLE( … ), or null for any other
     * text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 6 || fault >= spoken.size() || spoken.get(fault).getType() == FrostlakeLexer.LPAREN
                || !SyntaxErrorListener.isNameLike(spoken.get(fault - 1))
                || spoken.get(fault - 2).getType() != FrostlakeLexer.BANG
                || !SyntaxErrorListener.isNameLike(spoken.get(fault - 3))) {
            return null;
        }
        int start = fault - 3;
        while (start >= 2 && spoken.get(start - 1).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isNameLike(spoken.get(start - 2))) {
            start -= 2;
        }
        final int open = start - 1;
        if (open < 1 || spoken.get(open).getType() != FrostlakeLexer.LPAREN
                || spoken.get(open - 1).getType() != FrostlakeLexer.TABLE) {
            return null;
        }
        int depth = 0;
        for (int i = open; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                if (i < fault) {
                    return null;
                }
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                return lines;
            }
        }
        return null;
    }
}
