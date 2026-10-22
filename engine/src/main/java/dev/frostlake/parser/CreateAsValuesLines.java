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
 * The line the account reports for a CREATE TABLE … AS VALUES, plain or under EXPLAIN: the VALUES is refused and
 * nothing after it, where this grammar reads on into the row and refuses its first value too (live-verified:
 * {@code CREATE TABLE ex AS VALUES (1)} names the VALUES at position 19 alone).
 */
final class CreateAsValuesLines {

    private CreateAsValuesLines() {
    }

    /**
     * The line for a text whose first fault is a VALUES right after the AS of a CREATE … TABLE, or null for any other.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 1 || fault >= spoken.size() || spoken.get(fault).getType() != FrostlakeLexer.VALUES
                || spoken.get(fault - 1).getType() != FrostlakeLexer.AS) {
            return null;
        }
        int start = 0;
        if (spoken.get(0).getType() == FrostlakeLexer.EXPLAIN) {
            start = 1;
        }
        if (start >= fault || spoken.get(start).getType() != FrostlakeLexer.CREATE) {
            return null;
        }
        for (int i = start + 1; i < fault; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.TABLE) {
                return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault)));
            }
        }
        return null;
    }
}
