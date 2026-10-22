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
 * The lines live reports for a SHOW whose LIMIT or STARTS WITH is written a second time, or whose STARTS WITH stands
 * after its LIMIT, before the text's first fault: the modifier's word, where the account's parser stopped before the
 * fault was reached — alone at the top level, and in a block with the token after it, the block reporting nothing more
 * (all live-verified):
 *
 * <pre>
 *   SHOW TABLES LIMIT 1 LIMIT 2 x                                'LIMIT' at 20
 *   SHOW TABLES LIMIT 1 LIMIT 2 LIKE 'x'                         'LIMIT' at 20
 *   SHOW TABLES LIMIT 1 STARTS WITH 'a' LIMIT 2 x                'STARTS' at 20
 *   BEGIN SHOW TABLES LIMIT 1 LIMIT 2 x; RETURN 1; END;          'LIMIT' at 26, '2' at 32
 *   BEGIN SHOW TABLES STARTS WITH 'a' STARTS WITH 'b' x; END;    'STARTS' at 34, 'WITH' at 41
 * </pre>
 */
final class RepeatedShowModifierLines {

    private RepeatedShowModifierLines() {
    }

    /**
     * The lines for a text whose first fault follows such a modifier, or null.
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
        int start = fault - 1;
        while (start >= 0 && spoken.get(start).getType() != FrostlakeLexer.SEMI
                && spoken.get(start).getType() != FrostlakeLexer.BEGIN) {
            start--;
        }
        start++;
        if (fault < 1 || start >= fault || spoken.get(start).getType() != FrostlakeLexer.SHOW) {
            return null;
        }
        boolean block = false;
        for (int i = 0; i < start; i++) {
            block = block || spoken.get(i).getType() == FrostlakeLexer.BEGIN;
        }
        boolean limit = false;
        boolean starts = false;
        for (int i = start + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            final boolean startsWith = type == FrostlakeLexer.STARTS && i + 1 < spoken.size()
                && spoken.get(i + 1).getType() == FrostlakeLexer.WITH;
            if (type == FrostlakeLexer.LIMIT && limit || startsWith && (starts || limit)) {
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                if (block && i + 1 < spoken.size()) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i + 1)));
                }
                return lines;
            }
            limit = limit || type == FrostlakeLexer.LIMIT;
            starts = starts || startsWith;
        }
        return null;
    }
}
