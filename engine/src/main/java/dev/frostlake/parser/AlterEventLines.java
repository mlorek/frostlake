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
 * The line live reports for an ALTER EVENT TABLE, which is no statement: an event table is altered by ALTER TABLE, and
 * the account names the TABLE after EVENT, {@code syntax error line 1 at position 12 unexpected 'TABLE'.}, where this
 * grammar stops at EVENT (live-verified).
 */
final class AlterEventLines {

    private AlterEventLines() {
    }

    /**
     * The line for a statement whose first fault is the EVENT of an ALTER EVENT TABLE that opens it, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (at < 1 || at + 1 >= spoken.size() || spoken.get(at).getType() != FrostlakeLexer.EVENT
                || spoken.get(at - 1).getType() != FrostlakeLexer.ALTER
                || spoken.get(at + 1).getType() != FrostlakeLexer.TABLE
                || at > 1 && spoken.get(at - 2).getType() != FrostlakeLexer.SEMI) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(at + 1)));
    }
}
