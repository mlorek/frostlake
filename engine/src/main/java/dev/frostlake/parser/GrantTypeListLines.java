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

import java.util.ArrayList;
import java.util.List;

/**
 * The lines live reports for a GRANT of privileges on a table named by the word IDENTIFIER alone, with a word and its
 * parameters in the parentheses after it: the account reads the word IDENTIFIER as the table's own name and the
 * parentheses as a list of types, so a text where a type's parameter stands is refused there, each in turn —
 * {@code GRANT SELECT ON TABLE IDENTIFIER(UPPER('t')) TO ROLE r} at the {@code 't'}, and
 * {@code IDENTIFIER(CONCAT('a', 'b'))} at the {@code 'a'} and at the {@code 'b'} (live-verified). A plain word alone
 * there is refused while the statement is parsed (Frostlake.g4's refuseTableTypeWord).
 */
final class GrantTypeListLines {

    private GrantTypeListLines() {
    }

    /**
     * The lines for a statement whose first fault is the parenthesis after the word in that list, or null when the
     * statement is not of that shape or writes no text among the word's parameters.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (at < 6 || spoken.get(at).getType() != FrostlakeLexer.LPAREN
                || spoken.get(at - 1).getType() != FrostlakeLexer.IDENTIFIER
                || spoken.get(at - 2).getType() != FrostlakeLexer.LPAREN
                || spoken.get(at - 3).getType() != FrostlakeParser.KW_IDENTIFIER_OPEN
                || spoken.get(at - 4).getType() != FrostlakeLexer.TABLE
                || spoken.get(at - 5).getType() != FrostlakeLexer.ON
                || !opensGrant(spoken, at - 6)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        int i = at + 1;
        while (true) {
            final int type = spoken.get(i).getType();
            if (type != FrostlakeLexer.STRING_LITERAL && type != FrostlakeLexer.INTEGER_LITERAL) {
                return null;
            }
            if (type == FrostlakeLexer.STRING_LITERAL) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
            }
            final int next = spoken.get(i + 1).getType();
            if (next == FrostlakeLexer.RPAREN) {
                break;
            }
            if (next != FrostlakeLexer.COMMA) {
                return null;
            }
            i += 2;
        }
        if (lines.isEmpty() || spoken.get(i + 2).getType() != FrostlakeLexer.RPAREN
                || spoken.get(i + 3).getType() != FrostlakeLexer.TO) {
            return null;
        }
        return lines;
    }

    /** Whether the privilege list that ends at {@code last} follows the GRANT that opens the statement. */
    private static boolean opensGrant(final List<Token> spoken, final int last) {
        for (int i = last; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.GRANT) {
                return i == 0 || spoken.get(i - 1).getType() == FrostlakeLexer.SEMI;
            }
            if (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.ON || type == FrostlakeLexer.TO) {
                return false;
            }
        }
        return false;
    }
}
