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
 * The lines live reports for a fault inside the value a block's assignment assigns: the value's first token,
 * wherever in the value the fault lies, and — for a comma inside a cast that does not start the value — the
 * parenthesis that cast opens (all live-verified):
 *
 * <pre>
 *   x := 1 2;                    '1'
 *   x := UPPER(1 2);             'UPPER'
 *   x := CAST(1, 2);             'CAST'
 *   x := UPPER(CAST(1, 2));      'UPPER', then the '(' after CAST
 *   x := 1 + CAST(1, 2);         '1', then the '(' after CAST
 * </pre>
 *
 * <p>A LET's or a declaration's initial value is not such an assignment: a comma inside its cast is refused
 * itself.
 */
final class AssignmentValueLines {

    private AssignmentValueLines() {
    }

    /**
     * The lines live reports when the parse's first line names a token inside an assignment's value, or null.
     *
     * @param spoken         the text's default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final boolean statementParse) {
        final int fault = statementParse ? CastFaultPlace.positionOf(spoken, faultIndex) : -1;
        if (fault < 3) {
            return null;
        }
        int assigned = -1;
        for (int i = fault - 1; i >= 2 && assigned < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.COLON_EQ) {
                assigned = i;
            }
        }
        if (assigned < 2 || assigned + 1 >= fault) {
            return null;
        }
        final int before = spoken.get(assigned - 2).getType();
        if (before != FrostlakeLexer.SEMI && before != FrostlakeLexer.BEGIN && before != FrostlakeLexer.THEN
                && before != FrostlakeLexer.ELSE && before != FrostlakeLexer.DO && before != FrostlakeLexer.LOOP) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(assigned + 1)));
        final int open = CastFaultPlace.enclosingParen(spoken, fault);
        if (spoken.get(fault).getType() == FrostlakeLexer.COMMA && open > assigned + 2
                && CastFaultPlace.isCast(spoken, open - 1)) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(open)));
        }
        return lines;
    }
}
