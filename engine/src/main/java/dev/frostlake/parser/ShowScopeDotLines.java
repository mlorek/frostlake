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
 * The lines live reports for a SHOW whose IN names a scope with a run of three dots or more after its first part
 * (all live-verified). The first part and two dots read as a database with an empty schema, so the third dot is
 * refused; the rest of the run and the name after it are read on, and a word written after the name is refused too:
 *
 * <pre>
 *   SHOW TABLES IN a...c          the third '.'
 *   SHOW TABLES IN a...c y        the third '.', then 'y'
 *   SHOW TABLES IN a....c y       the third '.', then 'y'
 * </pre>
 */
final class ShowScopeDotLines {

    private ShowScopeDotLines() {
    }

    /**
     * The lines for such a SHOW, or null for any other text, a scope written with its kind, and when another
     * statement follows.
     *
     * @param spoken the default-channel tokens of the text parsed, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.SHOW) {
            return null;
        }
        int in = -1;
        for (int i = 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI && spoken.get(i + 1).getType() != Token.EOF) {
                return null;
            }
            if (type == FrostlakeLexer.IN && in < 0) {
                in = i;
            }
        }
        if (in < 0 || in + 5 >= spoken.size() || !isName(spoken.get(in + 1))) {
            return null;
        }
        int at = in + 2;
        while (at < spoken.size() && spoken.get(at).getType() == FrostlakeLexer.DOT) {
            at++;
        }
        if (at - (in + 2) < 3) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(in + 4)));
        if (isName(spoken.get(at)) && isName(spoken.get(at + 1))) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
        }
        return lines;
    }

    /** Whether a token is a word or a quoted name. */
    private static boolean isName(final Token token) {
        if (token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return true;
        }
        final String text = token.getText();
        return token.getType() != Token.EOF && !text.isEmpty()
            && (Character.isLetter(text.charAt(0)) || text.charAt(0) == '_');
    }
}
