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
 * A word written straight after a string inside brackets, as the last token before the bracket that closes them, ends
 * the report: live refuses the word and reads on from that bracket, where this parse's recovery may still meet faults
 * of its own. A word another token follows inside the brackets reads on as the parse does — {@code SELECT ('a' x y)}
 * is 'x', then 'y' (all live-verified):
 *
 * <pre>
 *   SELECT 1 FROM t1 WHERE a IN ('x' y) AND b = 1        'y' alone
 *   SELECT 1 FROM t1 WHERE a IN ('x, ' y) AND b = 1      'y' alone
 *   SELECT ('x, 'y') FROM t1                             the string left open, then 'y'
 * </pre>
 */
final class BracketedStringWordLines {

    private BracketedStringWordLines() {
    }

    /**
     * The lines with every syntax line after such a word left out, or the lines as they are.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<String> lines) {
        if (spoken == null || lines == null) {
            return lines;
        }
        for (int k = 0; k < lines.size(); k++) {
            final String line = lines.get(k);
            if (!line.startsWith("syntax error")) {
                continue;
            }
            final int word = indexOf(spoken, SyntaxErrorListener.sentenceCoordinates(line));
            if (word > 0 && k + 1 < lines.size() && word + 1 < spoken.size()
                    && SyntaxErrorListener.isNameLike(spoken.get(word))
                    && spoken.get(word - 1).getType() == FrostlakeLexer.STRING_LITERAL
                    && spoken.get(word + 1).getType() == FrostlakeLexer.RPAREN && insideBrackets(spoken, word)) {
                return new ArrayList<>(lines.subList(0, k + 1));
            }
            return lines;
        }
        return lines;
    }

    private static int indexOf(final List<Token> spoken, final int[] at) {
        if (at == null) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (token.getLine() == at[0] && token.getCharPositionInLine() == at[1]) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a bracket opened in the token's statement is still open where the token stands. */
    private static boolean insideBrackets(final List<Token> spoken, final int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0 && spoken.get(i).getType() != FrostlakeLexer.SEMI; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (type == FrostlakeLexer.LPAREN && ++depth > 0) {
                return true;
            }
        }
        return false;
    }
}
