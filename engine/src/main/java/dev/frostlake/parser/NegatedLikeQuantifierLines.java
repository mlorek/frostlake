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
 * The lines the account reports for a negated LIKE, ILIKE or RLIKE given a quantifier. NOT LIKE ANY and NOT LIKE
 * ALL are no predicates: the quantifier is refused, and the account reads on into the parentheses after it. The
 * token that opens them is refused too, whatever it is; after that, the parenthesis behind a name written first,
 * or — in a select list, where a comma starts the next item — the closing parenthesis of a list of more than one
 * pattern. Nothing after the list is reported (live-verified):
 *
 * <pre>
 *   SELECT 'a' NOT LIKE ANY 'x'                      'ANY'
 *   SELECT 'a' NOT LIKE ANY ()                       'ANY', ')'
 *   SELECT 'a' NOT LIKE ANY ('x')                    'ANY', ''x''
 *   SELECT 'a' NOT LIKE ANY (SELECT 'x')             'ANY', 'SELECT'
 *   SELECT 'a' NOT LIKE ANY ('x', 'y')               'ANY', ''x'', ')'
 *   SELECT 'a' NOT RLIKE ANY ('x', 'y')              'ANY', ''x'', ')'
 *   SELECT 'a' NOT LIKE ALL ('x', 'y') AS c          'ALL', ''x'', ')'
 *   SELECT 'a' NOT LIKE ANY (f('x'), 'y')            'ANY', 'f', '('
 *   SELECT 1 FROM t WHERE 'a' NOT LIKE ANY ('x', 'y') AND 1 = 1      'ANY', ''x''
 * </pre>
 */
final class NegatedLikeQuantifierLines {

    private NegatedLikeQuantifierLines() {
    }

    /**
     * The lines for a text whose first fault is the quantifier after NOT LIKE, NOT ILIKE or NOT RLIKE, parentheses
     * behind it, or null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 2 || fault + 2 >= spoken.size()) {
            return null;
        }
        final int quantifier = spoken.get(fault).getType();
        final int predicate = spoken.get(fault - 1).getType();
        if (quantifier != FrostlakeLexer.ANY && quantifier != FrostlakeLexer.ALL
                || predicate != FrostlakeLexer.LIKE && predicate != FrostlakeLexer.ILIKE
                    && predicate != FrostlakeLexer.RLIKE
                || spoken.get(fault - 2).getType() != FrostlakeLexer.NOT
                || spoken.get(fault + 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final Token opens = spoken.get(fault + 2);
        if (opens.getType() == Token.EOF || opens.getType() == FrostlakeLexer.SEMI) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(opens));
        final Token after = spoken.get(fault + 3);
        if (SyntaxErrorListener.isNameLike(opens) && after.getType() == FrostlakeLexer.LPAREN) {
            lines.add(SyntaxErrorListener.sentence(after));
            return lines;
        }
        final Token close = closeOfSeveral(spoken, fault + 2);
        if (close != null && standsInSelectList(spoken, fault)) {
            lines.add(SyntaxErrorListener.sentence(close));
        }
        return lines;
    }

    /** The parenthesis that closes the list opened before {@code from}, when the list holds a comma of its own. */
    private static Token closeOfSeveral(final List<Token> spoken, final int from) {
        int depth = 0;
        boolean several = false;
        for (int i = from; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return several ? spoken.get(i) : null;
                }
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                several = true;
            }
        }
        return null;
    }

    /** Whether the token at {@code fault} stands in a select list of its own, outside any parenthesis of it. */
    private static boolean standsInSelectList(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0) {
                if (type == FrostlakeLexer.SELECT) {
                    return true;
                }
                if (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.HAVING
                        || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ON || type == FrostlakeLexer.GROUP
                        || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.SEMI) {
                    return false;
                }
            }
        }
        return false;
    }
}
