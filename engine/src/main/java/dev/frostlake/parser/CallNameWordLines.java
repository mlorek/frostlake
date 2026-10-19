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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines the account reports after the call names INSERT and RLIKE written bare in a select list, where it
 * reads one token more than the rule for the name alone does (all live-verified, each for a text that is one
 * SELECT):
 *
 * <pre>
 *   SELECT 'foo', INSERT INTO t VALUES (1)   'INTO', then 'VALUES'
 *   SELECT 'foo', INSERT ON (t) x            'ON', then the name in the bracket and its ')'
 *   SELECT RLIKE ON t x y                    'ON' twice, then the word after the name
 * </pre>
 */
final class CallNameWordLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private CallNameWordLines() {
    }

    /**
     * The lines of a text that is one SELECT whose first fault follows such a name, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        if (faultIndex < 0 || spoken.size() < 6 || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final int end = spoken.size() - 1;
        int fault = -1;
        for (int i = 1; i < end; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return null;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault >= 1 && fault + 1 < end && spoken.get(fault).getType() == FrostlakeLexer.INSERT) {
            // The parse names the INSERT itself where the account reads it as a call's name.
            fault++;
        }
        if (fault < 2 || fault + 2 >= end) {
            return null;
        }
        final int opener = spoken.get(fault - 2).getType();
        if (opener != FrostlakeLexer.SELECT && opener != FrostlakeLexer.COMMA) {
            return null;
        }
        final int name = spoken.get(fault - 1).getType();
        final int word = spoken.get(fault).getType();
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (name == FrostlakeLexer.INSERT && word == FrostlakeLexer.INTO && isName(spoken.get(fault + 1).getType())
                && spoken.get(fault + 2).getType() == FrostlakeLexer.VALUES) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            return lines;
        }
        if (name == FrostlakeLexer.INSERT && word == FrostlakeLexer.ON
                && spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN && isName(spoken.get(fault + 2).getType())
                && fault + 3 < end && spoken.get(fault + 3).getType() == FrostlakeLexer.RPAREN) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 3)));
            return lines;
        }
        if (name == FrostlakeLexer.RLIKE && word == FrostlakeLexer.ON && isName(spoken.get(fault + 1).getType())
                && isName(spoken.get(fault + 2).getType())) {
            lines.add(lines.get(0));
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            return lines;
        }
        return null;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
