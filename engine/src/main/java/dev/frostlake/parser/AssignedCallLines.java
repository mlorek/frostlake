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
 * The lines the account reports for a CALL written as a declared variable's value in parentheses, which a LET and a
 * DECLARE do not take: the procedure's name is refused, and the account then reads the call's arguments up to their
 * first name or bind and refuses the token after it — none when the arguments hold no name (all live-verified):
 *
 * <pre>
 *   LET x VARCHAR := (CALL pn(:d));           'pn', then the ')' after :d
 *   LET x VARCHAR := (CALL pn(:d, 1));        'pn', then the ','
 *   LET x VARCHAR := (CALL pn(1, :d));        'pn', then the ')' after :d
 *   LET x VARCHAR := (CALL pn(TO_DATE(:d)));  'pn', then the '(' after TO_DATE
 *   LET x VARCHAR := (CALL pn(1));            'pn' alone
 * </pre>
 */
final class AssignedCallLines {

    private AssignedCallLines() {
    }

    /**
     * The lines for a text whose first fault is the name of a procedure called as a value written after := or
     * DEFAULT, or null for any other text and for a call whose arguments hold no name.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 3 || fault + 2 >= spoken.size() || spoken.get(fault - 1).getType() != FrostlakeLexer.CALL
                || spoken.get(fault - 2).getType() != FrostlakeLexer.LPAREN
                || spoken.get(fault - 3).getType() != FrostlakeLexer.COLON_EQ
                    && spoken.get(fault - 3).getType() != FrostlakeLexer.DEFAULT
                || spoken.get(fault + 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        int depth = 0;
        for (int i = fault + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return null;
            } else if (type == FrostlakeLexer.COLON || SyntaxErrorListener.isNameLike(spoken.get(i))) {
                final int after = type == FrostlakeLexer.COLON ? i + 2 : i + 1;
                if (after >= spoken.size()) {
                    return null;
                }
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(after)));
                return lines;
            }
        }
        return null;
    }
}
