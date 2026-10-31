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
 * The lines live reports for an AS written after a value inside the list of a quantified comparison or of an IN that
 * stands in a select item: the AS, then the parenthesis closing the list, whatever stands between; in any other clause
 * the AS alone (all live-verified):
 *
 * <pre>
 *   SELECT 1 = ANY (1 AS x)                    'AS', then ')'
 *   SELECT 1 = ANY (1 AS x, 2)                 'AS', then the ')' after the 2
 *   SELECT 1 = SOME ((1) AS x)                 'AS', then ')'; ALL alike
 *   SELECT 1 IN (1 AS x)                       'AS', then ')'
 *   SELECT * FROM t WHERE n = ANY (1 AS x)     'AS' alone
 * </pre>
 */
final class QuantifiedListAliasLines {

    private QuantifiedListAliasLines() {
    }

    /**
     * The lines for a text whose first fault is such an AS, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3 || spoken.get(fault).getType() != FrostlakeLexer.AS || !endsValue(spoken.get(fault - 1))) {
            return null;
        }
        int open = -1;
        int depth = 0;
        for (int i = fault - 1; i > 0 && open < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && depth > 0) {
                depth--;
            } else if (type == FrostlakeLexer.LPAREN) {
                open = i;
            }
        }
        if (open < 2 || !listsValues(spoken, open) || !inSelectItem(spoken, open)) {
            return null;
        }
        final int close = CallTokenGroups.closeOf(spoken, open);
        if (close < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        return lines;
    }

    /** Whether the parenthesis at {@code open} holds the values of an IN or of a compared ANY, SOME or ALL. */
    private static boolean listsValues(final List<Token> spoken, final int open) {
        final int before = spoken.get(open - 1).getType();
        if (before == FrostlakeLexer.IN) {
            return true;
        }
        if (before != FrostlakeLexer.ANY && before != FrostlakeLexer.SOME && before != FrostlakeLexer.ALL) {
            return false;
        }
        final int compared = spoken.get(open - 2).getType();
        return compared == FrostlakeLexer.EQ || compared == FrostlakeLexer.NEQ || compared == FrostlakeLexer.LT
            || compared == FrostlakeLexer.LTE || compared == FrostlakeLexer.GT || compared == FrostlakeLexer.GTE;
    }

    /**
     * Whether the bracket at {@code open} stands in a select item: SELECT is reached back through brackets that are no
     * call's, before any clause.
     */
    private static boolean inSelectItem(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0 && i > 0 && CallTokenGroups.isCallName(spoken.get(i - 1))) {
                    return false;
                }
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return true;
            } else if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.BY || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
                    || type == FrostlakeLexer.ON || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.SET)) {
                return false;
            }
        }
        return false;
    }

    /** Whether a token may end a value: a closing parenthesis or bracket, a literal or a name. */
    private static boolean endsValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL || type == FrostlakeLexer.QUOTED_IDENTIFIER
            || CallTokenGroups.isCallName(token);
    }
}
