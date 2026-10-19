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
 * The lines the account reports for a BREAK or an EXIT given a condition, {@code EXIT WHEN i = 2;}. There is no
 * such form: the account reads WHEN as the loop's label and refuses the token after it, and then the semicolon
 * that ends the statement; a label written before WHEN changes nothing (live-verified):
 *
 * <pre>
 *   … DO EXIT WHEN i = 2; END FOR …          'i', ';'
 *   … DO BREAK WHEN i = 2; END FOR …         'i', ';'
 *   … DO EXIT outer WHEN i = 2; END FOR …    'i', ';'
 * </pre>
 */
final class ExitWhenLines {

    private ExitWhenLines() {
    }

    /**
     * The lines for a text holding a BREAK or an EXIT followed by WHEN ahead of its first fault, or null for any
     * other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the two lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        for (int i = 0; i + 2 < spoken.size() && i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type != FrostlakeLexer.BREAK && type != FrostlakeLexer.EXIT) {
                continue;
            }
            int when = i + 1;
            if (spoken.get(when).getType() != FrostlakeLexer.WHEN && SyntaxErrorListener.isNameLike(spoken.get(when))) {
                when++;
            }
            if (when + 1 >= spoken.size() || when > fault || spoken.get(when).getType() != FrostlakeLexer.WHEN) {
                continue;
            }
            final Token after = spoken.get(when + 1);
            for (int end = when + 2; end < spoken.size(); end++) {
                if (spoken.get(end).getType() == FrostlakeLexer.SEMI) {
                    final List<String> lines = new ArrayList<String>();
                    lines.add(SyntaxErrorListener.sentence(after));
                    lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
                    return lines;
                }
            }
            return null;
        }
        return null;
    }
}
