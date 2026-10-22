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
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a CREATE OR ALTER MASKING POLICY it does not take (live-verified):
 *
 * <pre>
 *   CREATE OR ALTER SECURE MASKING POLICY m AS (v VARCHAR) …          'MASKING'
 *   CREATE OR ALTER MASKING POLICY IF NOT EXISTS m AS (v VARCHAR) …   'NOT', 'AS', 'VARCHAR', then the ')'
 * </pre>
 *
 * <p>SECURE is no word of the statement, and its MASKING is refused alone. IF NOT EXISTS is not taken: its NOT is
 * refused, EXISTS read as the policy's name, the name after it and the AS refused; the signature's first type and
 * its closing parenthesis follow.
 */
final class CreateOrAlterPolicyLines {

    private CreateOrAlterPolicyLines() {
    }

    /**
     * The lines for a text whose first fault is one of these, or null.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (spoken.size() < 6 || spoken.get(0).getType() != FrostlakeLexer.CREATE
                || spoken.get(1).getType() != FrostlakeLexer.OR || spoken.get(2).getType() != FrostlakeLexer.ALTER) {
            return null;
        }
        if (fault == 4 && spoken.get(3).getType() == FrostlakeLexer.SECURE
                && spoken.get(4).getType() == FrostlakeLexer.MASKING) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(4)));
        }
        if (fault != 6 || spoken.get(3).getType() != FrostlakeLexer.MASKING
                || spoken.get(4).getType() != FrostlakeLexer.POLICY || spoken.get(5).getType() != FrostlakeLexer.IF
                || spoken.get(6).getType() != FrostlakeLexer.NOT || spoken.size() < 15
                || spoken.get(7).getType() != FrostlakeLexer.EXISTS
                || spoken.get(9).getType() != FrostlakeLexer.AS || spoken.get(10).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(6)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(9)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(12)));
        for (int i = 13; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                return lines;
            }
            if (spoken.get(i).getType() == Token.EOF || spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                return null;
            }
        }
        return null;
    }
}
