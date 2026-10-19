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
 * The lines the account reports for a negated LIKE, ILIKE, RLIKE or REGEXP given a quantifier. NOT LIKE ANY and
 * NOT LIKE ALL are no predicates: the quantifier is refused, and the account reads on into the parentheses after
 * it. The token that opens them is refused too, whatever it is. After that the account recovers, and what it
 * reports depends on where the predicate stands (live-verified):
 *
 * <pre>
 *   SELECT 'a' NOT LIKE ANY 'x'                      'ANY'
 *   SELECT 'a' NOT LIKE ANY ()                       'ANY', ')'
 *   SELECT 'a' NOT LIKE ANY ('x')                    'ANY', ''x''
 *   SELECT 'a' NOT LIKE ANY (SELECT 'x')             'ANY', 'SELECT'
 *   SELECT 'a' NOT LIKE ANY ('x', 'y')               'ANY', ''x'', ')'
 *   SELECT 'a' NOT REGEXP ANY ('x', 'y')             'ANY', ''x'', ')'
 *   SELECT 'a' NOT LIKE ALL ('x', 'y') AS c          'ALL', ''x'', ')'
 *   SELECT 'a' NOT LIKE ANY (f('x'), 'y')            'ANY', 'f', '('
 *   SELECT UPPER(c NOT LIKE ANY ('x')) FROM t        'ANY', ''x'', ')'       the call's own
 *   SELECT (SELECT 'a' NOT LIKE ANY ('x', 'y'))      'SELECT', 'ANY', ''x'', ')'
 *   SELECT 1 FROM t WHERE 'a' NOT LIKE ANY ('x', 'y') AND 1 = 1      'ANY', ''x''
 *   SELECT 1 FROM t1 JOIN t2 ON c NOT LIKE ANY ('x')                 'ON'
 * </pre>
 *
 * <p>The recovery gives up the list's own parenthesis and goes on to the first token that may follow an item:
 * a comma where the predicate is an item of a list — of a select list, an ORDER BY or a GROUP BY — and a comma or
 * a closing parenthesis where a parenthesis is open around it. From there its parentheses close one too early, and
 * the first closing parenthesis left without a partner is refused: the list's own after a comma at the top of a
 * select list, the call's own when the predicate is a call's argument, a FROM subquery's own. A WHERE, a HAVING
 * and a QUALIFY take no comma, and a lone pattern at the top of a list reaches none, so nothing more is reported
 * there. In a select list a name written first has the parenthesis behind it refused instead. A query in
 * parentheses that is an item of a select list has its SELECT refused first, and its parenthesis given up with
 * it; in a join's ON condition the ON alone is refused.
 */
final class NegatedLikeQuantifierLines {

    private NegatedLikeQuantifierLines() {
    }

    /**
     * The lines for a text whose first fault is the quantifier after NOT LIKE, NOT ILIKE, NOT RLIKE or NOT REGEXP,
     * parentheses behind it, or null for any other text.
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
                    && predicate != FrostlakeLexer.RLIKE && predicate != FrostlakeLexer.REGEXP
                || spoken.get(fault - 2).getType() != FrostlakeLexer.NOT
                || spoken.get(fault + 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final Token opens = spoken.get(fault + 2);
        if (opens.getType() == Token.EOF || opens.getType() == FrostlakeLexer.SEMI) {
            return null;
        }
        final Token on = joinCondition(spoken, fault);
        if (on != null) {
            return Collections.singletonList(SyntaxErrorListener.sentence(on));
        }
        final List<String> lines = new ArrayList<String>();
        final int query = scalarQuery(spoken, fault);
        if (query >= 0) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(query)));
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(opens));
        final Token after = spoken.get(fault + 3);
        if (SyntaxErrorListener.isNameLike(opens) && after.getType() == FrostlakeLexer.LPAREN
                && standsInSelectList(spoken, fault)) {
            lines.add(SyntaxErrorListener.sentence(after));
            return lines;
        }
        final Token unpartnered = unpartneredClose(spoken, fault, query >= 0);
        if (unpartnered != null) {
            lines.add(SyntaxErrorListener.sentence(unpartnered));
        }
        return lines;
    }

    /**
     * The closing parenthesis left without a partner once the recovery gives up the list's own, or null when the
     * recovery reaches none.
     *
     * @param scalarLost whether the predicate stands in a query whose parenthesis was given up with its SELECT
     */
    private static Token unpartneredClose(final List<Token> spoken, final int fault, final boolean scalarLost) {
        int open = 0;
        int depth = 0;
        for (int i = fault - 1; i >= 0 && spoken.get(i).getType() != FrostlakeLexer.SEMI; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    open++;
                } else {
                    depth--;
                }
            }
        }
        if (scalarLost) {
            open--;
        }
        if (open <= 0 && !listsItems(spoken, fault)) {
            return null;
        }
        // The recovery stops at a comma, or at a closing parenthesis while one is open around the predicate.
        int stop = -1;
        int nested = 0;
        for (int i = fault + 3; i < spoken.size() && stop < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                nested++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (nested > 0) {
                    nested--;
                } else if (open > 0) {
                    stop = i;
                }
            } else if (type == FrostlakeLexer.COMMA && nested == 0) {
                stop = i;
            }
        }
        if (stop < 0) {
            return null;
        }
        int left = spoken.get(stop).getType() == FrostlakeLexer.RPAREN ? open - 1 : open;
        for (int i = stop + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                left++;
            } else if (type == FrostlakeLexer.RPAREN && --left < 0) {
                return spoken.get(i);
            }
        }
        return null;
    }

    /**
     * The ON of the join condition the predicate stands in, through the parentheses around it, or null when it
     * stands elsewhere.
     */
    private static Token joinCondition(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (isQueryStart(spoken.get(i + 1).getType())) {
                    return null;
                }
            } else if (depth == 0) {
                if (type == FrostlakeLexer.ON) {
                    return spoken.get(i);
                }
                if (endsClauseSearch(type)) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * The index of the SELECT refused first when the predicate stands in the select list of a query in
     * parentheses that is itself an item of a select list, or -1.
     */
    private static int scalarQuery(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN && standsInSelectList(spoken, i - 1)
                    ? i : -1;
            } else if (depth == 0 && endsClauseSearch(type)) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether the predicate at {@code fault} is an item of a list: a select list's, an ORDER BY's, a GROUP BY's. */
    private static boolean listsItems(final List<Token> spoken, final int fault) {
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
                if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.GROUP) {
                    return true;
                }
                if (endsClauseSearch(type)) {
                    return false;
                }
            }
        }
        return false;
    }

    private static boolean isQueryStart(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WITH;
    }

    /** Whether a token starts a clause other than the ones the searches here look for. */
    private static boolean endsClauseSearch(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.USING;
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
