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
 * The lines live reports for an ALTER TABLE's ADD COLUMN followed by a parenthesis, which opens no list there: the
 * parenthesis, then the token after the first word inside it - {@code ALTER TABLE t ADD COLUMN (x VARCHAR(10))} is
 * the '(' and the 'VARCHAR', with a second column after the first or not, an Iceberg table's alike (live-verified).
 */
final class AddColumnBracketLines {

    private AddColumnBracketLines() {
    }

    /**
     * The lines for a text whose first fault is such a parenthesis, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (at < 3 || at + 2 >= spoken.size() || spoken.get(0).getType() != FrostlakeLexer.ALTER
                || spoken.get(at).getType() != FrostlakeLexer.LPAREN
                || spoken.get(at - 1).getType() != FrostlakeLexer.COLUMN
                || spoken.get(at - 2).getType() != FrostlakeLexer.ADD
                || !CallTokenGroups.isCallName(spoken.get(at + 1))) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(at + 2)));
        return lines;
    }
}
