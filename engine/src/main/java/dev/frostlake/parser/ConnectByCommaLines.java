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

import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a comma in a CONNECT BY clause, whose list of conditions it does not take
 * (live-verified): the first comma is refused and nothing after it, however many more conditions follow.
 *
 * <pre>
 *   CONNECT BY mgr = PRIOR id, PRIOR dept = dept                  ','
 *   CONNECT BY mgr = PRIOR id, PRIOR dept = dept, PRIOR id &lt; id    the first ','
 * </pre>
 */
final class ConnectByCommaLines {

    private ConnectByCommaLines() {
    }

    /**
     * The line for a text whose first fault is a comma that follows a CONNECT BY condition, or null.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 2 || fault >= spoken.size() || spoken.get(fault).getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        int depth = 0;
        for (int i = fault - 1; i >= 1; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return null;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.BY) {
                return spoken.get(i - 1).getType() == FrostlakeLexer.CONNECT
                    ? Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault))) : null;
            } else if (depth == 0 && (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM
                    || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.SEMI)) {
                return null;
            }
        }
        return null;
    }
}
