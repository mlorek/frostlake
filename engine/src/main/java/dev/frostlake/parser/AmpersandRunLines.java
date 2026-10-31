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
 * The lines live reports for a run of ampersands after a value: the first is the operator, and each one after it is
 * refused in turn, and nothing after the run is reported (live-verified):
 *
 * <pre>
 *   SELECT 1 &amp;&amp;&amp; 2         the second '&amp;', then the third
 *   SELECT 1 &amp;&amp;&amp;&amp; 2        the second, the third and the fourth
 * </pre>
 */
final class AmpersandRunLines {

    private AmpersandRunLines() {
    }

    /**
     * The lines for a text whose first fault is an ampersand straight after another, or null.
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
        if (fault < 2 || spoken.get(fault).getType() != FrostlakeParser.AMP
                || spoken.get(fault - 1).getType() != FrostlakeParser.AMP
                || spoken.get(fault).getStartIndex() != spoken.get(fault - 1).getStopIndex() + 1) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        for (int i = fault; i < spoken.size() && spoken.get(i).getType() == FrostlakeParser.AMP; i++) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
        }
        return lines;
    }
}
