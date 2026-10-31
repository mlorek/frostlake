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
 * The lines live reports after two predicate shapes it does not take (all live-verified, each for a text that is
 * one statement):
 *
 * <pre>
 *   SELECT a IS TRUE ('x') FROM t                  'IS', then the '(': TRUE is read as the item's alias
 *   SELECT 1 FROM t WHERE a LIKE ANY ('x') ('y')   the second list's first token alone
 * </pre>
 *
 * In a WHERE the IS is refused alone, as the parse names it.
 */
final class PredicateTailLines {

    private PredicateTailLines() {
    }

    /**
     * The lines for a text whose first fault is such a shape, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI && i + 1 < spoken.size() && spoken.get(i + 1).getType() != Token.EOF) {
                return null;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 2 || fault + 2 >= spoken.size() || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        final Token refused = spoken.get(fault);
        final int truth = spoken.get(fault + 1).getType();
        if (refused.getType() == FrostlakeLexer.IS && (truth == FrostlakeLexer.TRUE || truth == FrostlakeLexer.FALSE)
                && spoken.get(fault + 2).getType() == FrostlakeLexer.LPAREN && inSelectList(spoken, fault)) {
            lines.add(SyntaxErrorListener.sentence(refused));
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            return lines;
        }
        // A quantified LIKE's list followed by another bracket: the fault is that bracket's first token, and the
        // report ends there.
        final int open = fault - 1;
        if (spoken.get(open).getType() == FrostlakeLexer.LPAREN && open >= 4
                && spoken.get(open - 1).getType() == FrostlakeLexer.RPAREN) {
            int depth = 0;
            int list = -1;
            for (int i = open - 1; i > 0; i--) {
                final int type = spoken.get(i).getType();
                depth += type == FrostlakeLexer.RPAREN ? 1 : type == FrostlakeLexer.LPAREN ? -1 : 0;
                if (depth == 0) {
                    list = i;
                    break;
                }
            }
            final int quantifier = list - 1;
            if (list > 2 && (spoken.get(quantifier).getType() == FrostlakeLexer.ANY
                    || spoken.get(quantifier).getType() == FrostlakeLexer.ALL
                    || spoken.get(quantifier).getType() == FrostlakeLexer.SOME)
                    && (spoken.get(quantifier - 1).getType() == FrostlakeLexer.LIKE
                        || spoken.get(quantifier - 1).getType() == FrostlakeLexer.ILIKE)) {
                lines.add(SyntaxErrorListener.sentence(refused));
                return lines;
            }
        }
        return null;
    }

    /** Whether a position stands in the select list, outside any bracket and before the FROM. */
    private static boolean inSelectList(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && type == FrostlakeLexer.FROM) {
                return false;
            }
        }
        return depth == 0;
    }
}
