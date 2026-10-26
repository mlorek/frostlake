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
 * The lines live reports for a NOT EXISTS written as the right operand of a concatenation or an arithmetic operator:
 * the NOT, then the query's SELECT and the EXISTS's ')', as for an EXISTS there (live-verified):
 *
 * <pre>
 *   SELECT (1 + NOT EXISTS (SELECT 1 FROM g)::INT)      'NOT', 'SELECT', then the EXISTS's ')'
 * </pre>
 */
final class NotExistsOperandLines {

    private NotExistsOperandLines() {
    }

    /**
     * The lines for a text whose first fault is such a NOT, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 1 || fault + 3 >= spoken.size() || spoken.get(fault).getType() != FrostlakeLexer.NOT
                || spoken.get(fault + 1).getType() != FrostlakeLexer.EXISTS
                || spoken.get(fault + 2).getType() != FrostlakeLexer.LPAREN
                || !valueOperator(spoken.get(fault - 1).getType())) {
            return null;
        }
        int depth = 0;
        for (int i = fault + 2; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 3)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                return lines;
            }
        }
        return null;
    }

    private static boolean valueOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }
}
