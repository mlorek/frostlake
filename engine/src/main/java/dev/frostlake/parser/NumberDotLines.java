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
 * A dot written straight after a number is refused itself, where this parse reads it as a path and names what follows
 * it: {@code SELECT .5.} is the '.' at 9 (live-verified).
 */
final class NumberDotLines {

    private NumberDotLines() {
    }

    /**
     * The line for a text whose first fault stands right after such a dot, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1 for the end of the input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = faultIndex < 0 ? spoken.size() - 1 : -1;
        for (int i = 2; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 2) {
            return null;
        }
        final Token dot = spoken.get(fault - 1);
        final Token number = spoken.get(fault - 2);
        if (dot.getType() != FrostlakeLexer.DOT || number.getStopIndex() + 1 != dot.getStartIndex()
                || number.getType() != FrostlakeLexer.INTEGER_LITERAL
                    && number.getType() != FrostlakeLexer.FLOAT_LITERAL) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(dot));
        return lines;
    }
}
