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

import org.antlr.v4.runtime.Token;

import java.util.Collections;
import java.util.List;

/**
 * The line live reports for a CREATE OR REPLACE followed by a second OR: the OR is refused where it stands and
 * nothing after it is read, so {@code CREATE OR REPLACE OR ALTER TABLE t (a INT)} is the one line naming the OR,
 * whatever kind and body follow (live-verified).
 */
final class ReplaceOrLines {

    private ReplaceOrLines() {
    }

    /**
     * The line for a statement whose first fault is an OR right after the CREATE OR REPLACE that opens it, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        if (faultIndex < 0) {
            return null;
        }
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3 || spoken.get(fault).getType() != FrostlakeLexer.OR
                || spoken.get(fault - 1).getType() != FrostlakeLexer.REPLACE
                || spoken.get(fault - 2).getType() != FrostlakeLexer.OR
                || spoken.get(fault - 3).getType() != FrostlakeLexer.CREATE
                || fault > 3 && spoken.get(fault - 4).getType() != FrostlakeLexer.SEMI) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault)));
    }
}
