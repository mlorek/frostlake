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
 * The lines live reports for a VALUES written as a statement of a block — an anonymous block's or a SQL function's
 * unquoted body: the VALUES alone (all live-verified):
 *
 * <pre>
 *   CREATE OR REPLACE FUNCTION f() RETURNS INT AS BEGIN VALUES (1), (2); RETURN 1; END    'VALUES' at 52
 * </pre>
 */
final class BlockValuesLines {

    private BlockValuesLines() {
    }

    /**
     * The lines for a text whose first fault is such a VALUES, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        boolean block = false;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
            block = block || spoken.get(i).getType() == FrostlakeLexer.BEGIN;
        }
        if (fault < 1 || !block || spoken.get(fault).getType() != FrostlakeLexer.VALUES) {
            return null;
        }
        final int before = spoken.get(fault - 1).getType();
        if (before != FrostlakeLexer.BEGIN && before != FrostlakeLexer.SEMI) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        return lines;
    }
}
