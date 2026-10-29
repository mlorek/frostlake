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
 *   SELECT 'a' NOT LIKE ANY (a.b, 'y')               'ANY', 'a', '.'
 *   SELECT 'a' NOT LIKE ANY ('x' || 'y')             'ANY', ''x'', ')'       the list's own
 *   SELECT 'a' NOT LIKE ANY ('x' + 'y')              'ANY', ''x''
 *   SELECT UPPER(c NOT LIKE ANY ('x')) FROM t        'ANY', ''x'', ')'       the call's own
 *   SELECT UPPER(c NOT LIKE ANY (f(1, 2), 'y')) FROM t                    'ANY', 'f', '1', ')'   f's own
 *   SELECT (SELECT 'a' NOT LIKE ANY ('x', 'y'))      'SELECT', 'ANY', ''x'', ')'
 *   SELECT (SELECT UPPER('a' NOT LIKE ANY ('x')))    'SELECT', '('
 *   SELECT 1 FROM t WHERE 'a' NOT LIKE ANY ('x', 'y') AND 1 = 1      'ANY', ''x''
 *   SELECT 1 FROM t WHERE UPPER(c NOT LIKE ANY (f('x'), 'y')) IS NULL     'ANY', 'f', ','
 *   SELECT c FROM t ORDER BY UPPER(c NOT LIKE ANY (f('x'), 'y'))          'ANY', 'f', ')'   the list's own
 *   SELECT c FROM t GROUP BY c, c NOT LIKE ANY ('x', 'y')                 'ANY', ''x'', ')'
 *   SELECT 1 FROM t1 JOIN t2 ON c NOT LIKE ANY ('x')                      'ON'
 * </pre>
 *
 * <p>The recovery gives up the list's own parenthesis and goes on to the first token that may follow an item:
 * a comma where the predicate is an item of a list — of a select list, an ORDER BY or a GROUP BY — and a comma or
 * a closing parenthesis where a parenthesis is open around it. From there its parentheses close one too early, and
 * the first closing parenthesis left without a partner is refused: the list's own after a comma at the top of a
 * select list, the call's own when the predicate is a call's argument, a FROM subquery's own. A WHERE, a HAVING
 * and a QUALIFY take no comma, and a lone pattern at the top of a list reaches none; there an operator the list
 * holds - any but a plus - has the comma or the closing parenthesis after its operation refused instead, at the
 * list's own level, and without one nothing more is reported. A later item of a GROUP BY or an ORDER BY reads as
 * the first does.
 *
 * <p>A name written first in the list goes its own way. In a select list it has the parenthesis or the dot behind
 * it refused. In a call's argument the name's parenthesis is read as a call's: in a select list the token after it
 * is refused - none when it closes at once - and the reading goes on from there as at the top of a select list;
 * elsewhere the account goes on with one parenthesis around the predicate fewer, and the first closing parenthesis
 * left without a partner is refused, and in a WHERE, a HAVING or a QUALIFY so is a comma at the predicate's own
 * level. A query in parentheses that is an item of a select list has its SELECT refused first; a call its first
 * item writes has its own parenthesis refused after it, and nothing more, where the predicate stands in that call;
 * where the predicate stands in the query's own select list the query's parenthesis is given up with the SELECT.
 * In a join's ON condition the ON alone is refused.
 */
final class NegatedLikeQuantifierLines {

    private NegatedLikeQuantifierLines() {
    }

    /**
     * The lines for a text whose first fault is the quantifier after NOT LIKE, NOT ILIKE, NOT RLIKE or NOT REGEXP,
     * parentheses behind it, or null for any other text.
     *
     * @param spoken  the text's default-channel tokens, end of input included
     * @param refused the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int refused) {
        final int fault = laterItemQuantifier(spoken, refused);
        if (fault < 2 || fault + 2 >= spoken.size() || !isRefusedQuantifier(spoken, fault)) {
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
        boolean scalarLost = false;
        if (query >= 0) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(query)));
            final int call = firstItemCall(spoken, query, fault);
            if (call >= 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(call)));
                return lines;
            }
            scalarLost = standsInSelectList(spoken, fault);
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(opens));
        final Token after = spoken.get(fault + 3);
        final boolean named = SyntaxErrorListener.isNameLike(opens);
        if (named && standsInSelectList(spoken, fault)
                && (after.getType() == FrostlakeLexer.LPAREN || after.getType() == FrostlakeLexer.DOT)) {
            lines.add(SyntaxErrorListener.sentence(after));
            return lines;
        }
        final int open = openAround(spoken, fault) - (scalarLost ? 1 : 0);
        if (named && after.getType() == FrostlakeLexer.LPAREN && open > 0) {
            final int clause = clauseOf(spoken, fault);
            if (clause == FrostlakeLexer.SELECT) {
                if (!insideNamedCall(spoken, fault + 3, lines)) {
                    final int operated = QuantifierListOperation.refusedAfter(spoken, fault + 1);
                    if (operated >= 0) {
                        lines.add(SyntaxErrorListener.sentence(spoken.get(operated)));
                    }
                }
                return lines;
            }
            if (clause != Token.INVALID_TYPE) {
                final Token past = pastNamedCall(spoken, fault + 1, open, clause != FrostlakeLexer.ORDER
                    && clause != FrostlakeLexer.GROUP);
                if (past != null) {
                    lines.add(SyntaxErrorListener.sentence(past));
                }
                return lines;
            }
        }
        Token unpartnered = null;
        if (open > 0 || listsItems(spoken, fault)) {
            unpartnered = closeAfterStop(spoken, fault + 3, open);
        }
        if (unpartnered == null) {
            final int operated = QuantifierListOperation.refusedAfter(spoken, fault + 1);
            unpartnered = operated >= 0 ? spoken.get(operated) : null;
        }
        if (unpartnered != null) {
            lines.add(SyntaxErrorListener.sentence(unpartnered));
        }
        return lines;
    }

    /** Whether the token at {@code at} is ANY or ALL after a negated LIKE, ILIKE, RLIKE or REGEXP, a list behind. */
    private static boolean isRefusedQuantifier(final List<Token> spoken, final int at) {
        final int quantifier = spoken.get(at).getType();
        final int predicate = spoken.get(at - 1).getType();
        return (quantifier == FrostlakeLexer.ANY || quantifier == FrostlakeLexer.ALL)
            && (predicate == FrostlakeLexer.LIKE || predicate == FrostlakeLexer.ILIKE
                || predicate == FrostlakeLexer.RLIKE || predicate == FrostlakeLexer.REGEXP)
            && spoken.get(at - 2).getType() == FrostlakeLexer.NOT
            && at + 1 < spoken.size() && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN;
    }

    /**
     * The quantifier the item after a comma of a GROUP BY or an ORDER BY holds, where the parse failed at that comma:
     * the account reads a later item as it reads the first. Else the index given.
     */
    private static int laterItemQuantifier(final List<Token> spoken, final int fault) {
        if (fault < 0 || fault >= spoken.size() || spoken.get(fault).getType() != FrostlakeLexer.COMMA
                || !listsGroupOrOrder(spoken, fault)) {
            return fault;
        }
        int depth = 0;
        for (int i = fault + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (--depth < 0) {
                    return fault;
                }
            } else if (type == Token.EOF || depth == 0 && (type == FrostlakeLexer.COMMA || endsClauseSearch(type))) {
                return fault;
            } else if (i >= 2 && isRefusedQuantifier(spoken, i)) {
                return i;
            }
        }
        return fault;
    }

    /** Whether the comma at {@code comma} separates the items of a GROUP BY or an ORDER BY. */
    private static boolean listsGroupOrOrder(final List<Token> spoken, final int comma) {
        int depth = 0;
        for (int i = comma - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0) {
                if (type == FrostlakeLexer.GROUP || type == FrostlakeLexer.ORDER) {
                    return true;
                }
                if (endsClauseSearch(type)) {
                    return false;
                }
            }
        }
        return false;
    }

    /** The parentheses left open before the token at {@code fault}, back to the statement's start. */
    private static int openAround(final List<Token> spoken, final int fault) {
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
        return open;
    }

    /**
     * The closing parenthesis left without a partner once the recovery gives up the list's own, or null when the
     * recovery reaches none.
     *
     * @param from the index the recovery reads on from
     * @param open the parentheses open around the predicate
     */
    private static Token closeAfterStop(final List<Token> spoken, final int from, final int open) {
        // The recovery stops at a comma, or at a closing parenthesis while one is open around the predicate.
        int stop = -1;
        int nested = 0;
        for (int i = from; i < spoken.size() && stop < 0; i++) {
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
     * In a select list, past a name written first in the list of a predicate a call's argument holds: the token
     * after the name's parenthesis is refused, none when the parenthesis closes at once, and the reading goes on
     * as at the top of a select list — a name with a parenthesis or a dot behind it has that refused, and from
     * anything else the first comma is read to, after which the first closing parenthesis without a partner is
     * refused.
     *
     * @param paren the index of the name's parenthesis
     * @return whether the reading ends here, a closing parenthesis or the name's own refused
     */
    private static boolean insideNamedCall(final List<Token> spoken, final int paren, final List<String> lines) {
        final Token inside = spoken.get(paren + 1);
        if (inside.getType() == Token.EOF || inside.getType() == FrostlakeLexer.SEMI) {
            return true;
        }
        int from = paren + 1;
        if (inside.getType() != FrostlakeLexer.RPAREN) {
            lines.add(SyntaxErrorListener.sentence(inside));
            final Token behind = spoken.get(paren + 2);
            if (SyntaxErrorListener.isNameLike(inside)
                    && (behind.getType() == FrostlakeLexer.LPAREN || behind.getType() == FrostlakeLexer.DOT)) {
                lines.add(SyntaxErrorListener.sentence(behind));
                return true;
            }
            from = paren + 2;
        }
        final Token close = closeAfterStop(spoken, from, 0);
        if (close != null) {
            lines.add(SyntaxErrorListener.sentence(close));
        }
        return close != null;
    }

    /**
     * Outside a select list, past a name written first in the list of a predicate a call's argument holds: the
     * reading goes on with one parenthesis around the predicate fewer, and refuses the first closing parenthesis
     * left without a partner, or a comma at the predicate's own level where the clause takes none.
     *
     * @param list          the index of the list's own parenthesis
     * @param open          the parentheses open around the predicate
     * @param commaRefused  whether the clause takes no comma: a WHERE, a HAVING, a QUALIFY
     */
    private static Token pastNamedCall(final List<Token> spoken, final int list, final int open,
                                       final boolean commaRefused) {
        int left = open - 1;
        for (int i = list + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                left++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (--left < 0) {
                    return spoken.get(i);
                }
            } else if (type == FrostlakeLexer.COMMA && commaRefused && left == 0) {
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
     * parentheses that is itself an item of a select list - directly or in a call there - or -1.
     */
    private static int scalarQuery(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0 && !SyntaxErrorListener.isNameLike(spoken.get(i - 1))) {
                    return -1;
                }
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN && standsInSelectList(spoken, i - 1)
                    ? i : -1;
            } else if (depth == 0 && endsClauseSearch(type)) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * The parenthesis of the call the scalar query's first item writes, where the predicate stands inside that
     * call, or -1.
     *
     * @param select the index of the query's SELECT
     */
    private static int firstItemCall(final List<Token> spoken, final int select, final int fault) {
        if (select + 2 >= fault || !SyntaxErrorListener.isNameLike(spoken.get(select + 1))
                || spoken.get(select + 2).getType() != FrostlakeLexer.LPAREN) {
            return -1;
        }
        int depth = 0;
        for (int i = select + 2; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return -1;
            }
        }
        return depth > 0 ? select + 2 : -1;
    }

    /**
     * The keyword of the clause the predicate stands in, through the parentheses of the calls around it: SELECT
     * for a select list, WHERE, HAVING, QUALIFY, ORDER or GROUP; {@link Token#INVALID_TYPE} for any other place.
     */
    private static int clauseOf(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0 && (i == 0 || !SyntaxErrorListener.isNameLike(spoken.get(i - 1)))) {
                    return Token.INVALID_TYPE;
                }
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.HAVING
                        || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
                        || type == FrostlakeLexer.GROUP) {
                    return type;
                }
                if (endsClauseSearch(type)) {
                    return Token.INVALID_TYPE;
                }
            }
        }
        return Token.INVALID_TYPE;
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
