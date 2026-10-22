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
 * The lines live reports for an exception handler written without its THEN: the token where the THEN belongs is
 * refused twice (all live-verified):
 *
 * <pre>
 *   BEGIN RETURN 1; EXCEPTION WHEN OTHER RETURN 2; END                    'RETURN' at 37, twice
 *   BEGIN RETURN 1; EXCEPTION WHEN OTHER OR STATEMENT_ERROR RETURN 2; END  'RETURN' at 56, twice
 *   BEGIN RETURN 1; EXCEPTION WHEN OTHER 2; END                           '2' at 37, twice
 * </pre>
 */
final class HandlerThenLines {

    private HandlerThenLines() {
    }

    /**
     * The lines for a text whose first fault stands where a handler's THEN belongs, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        if (fault < 3 || spoken.get(fault).getType() == Token.EOF) {
            return null;
        }
        // Back over the condition's names and their ORs to the WHEN, and the EXCEPTION before it.
        int at = fault - 1;
        if (!isName(spoken.get(at))) {
            return null;
        }
        while (at >= 2 && spoken.get(at - 1).getType() == FrostlakeLexer.OR && isName(spoken.get(at - 2))) {
            at -= 2;
        }
        if (at < 2 || spoken.get(at - 1).getType() != FrostlakeLexer.WHEN
                || spoken.get(at - 2).getType() != FrostlakeLexer.EXCEPTION) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        return lines;
    }

    private static boolean isName(final Token token) {
        return token.getType() == FrostlakeLexer.OTHER || StarModifierResync.isName(token);
    }
}
