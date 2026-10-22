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
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for two faults of the value functions' own syntax that a parse failing further on still
 * leaves to be named first, written before its fault (all live-verified):
 *
 * <pre>
 *   LAG(DISTINCT n IGNORE NULLS) OVER (ORDER BY n)       'DISTINCT', then the call's '(': LAG, LEAD and NTH_VALUE
 *                                                        take no quantifier, whatever follows it
 *   LAG(n) IGNORE NULLS IGNORE NULLS OVER (ORDER BY n)   the second 'IGNORE' alone: a call takes one treatment
 * </pre>
 *
 * <p>A statement that parses whole is judged for both by {@link ValueFunctionSyntax}.
 */
final class NavigationCallLines {

    private static final Set<String> NAVIGATION_FUNCTIONS = Set.of("LAG", "LEAD", "NTH_VALUE");

    private NavigationCallLines() {
    }

    /**
     * The lines for a statement holding one of the two faults at or before the parse's first, or null.
     *
     * @param spoken     the statement's default-channel tokens, end of input included
     * @param faultIndex the token index of the parse's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = spoken.size() - 1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        for (int i = 0; i + 2 <= fault && i + 2 < spoken.size(); i++) {
            final Token name = spoken.get(i);
            final int quantifier = spoken.get(i + 2).getType();
            if (name.getType() == FrostlakeLexer.IDENTIFIER
                    && NAVIGATION_FUNCTIONS.contains(name.getText().toUpperCase(Locale.ROOT))
                    && (i == 0 || spoken.get(i - 1).getType() != FrostlakeLexer.DOT)
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN
                    && (quantifier == FrostlakeLexer.DISTINCT || quantifier == FrostlakeLexer.ALL)) {
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 2)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                return lines;
            }
        }
        if (fault >= 4 && spoken.get(fault).getType() == FrostlakeLexer.NULLS && isTreatmentWord(spoken.get(fault - 1))
                && spoken.get(fault - 2).getType() == FrostlakeLexer.NULLS && isTreatmentWord(spoken.get(fault - 3))
                && spoken.get(fault - 4).getType() == FrostlakeLexer.RPAREN) {
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault - 1)));
            return lines;
        }
        return null;
    }

    private static boolean isTreatmentWord(final Token token) {
        return token.getType() == FrostlakeLexer.IGNORE || token.getType() == FrostlakeLexer.RESPECT;
    }
}
