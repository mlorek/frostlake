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
 * The line the account reports for a CREATE OR ALTER of a privacy policy, a kind CREATE OR ALTER does not take: one
 * line at the OR, {@code CREATE OR ALTER PRIVACY POLICY pv AS () RETURNS PRIVACY_BUDGET -> …} being
 * {@code syntax error line 1 at position 7 unexpected 'OR'.} (live-verified), where the engine's grammar reads on to
 * the kind.
 */
final class CreateOrAlterKindLines {

    private CreateOrAlterKindLines() {
    }

    /**
     * The one line for such a statement whose first fault is the kind's word, or the lines worked out so far.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.isEmpty() || spoken.size() < 4) {
            return lines;
        }
        if (spoken.get(0).getType() != FrostlakeLexer.CREATE || spoken.get(1).getType() != FrostlakeLexer.OR
                || spoken.get(2).getType() != FrostlakeLexer.ALTER || spoken.get(3).getType() != FrostlakeLexer.PRIVACY
                || spoken.get(3).getTokenIndex() != faults.get(0).intValue()) {
            return lines;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(1)));
    }
}
