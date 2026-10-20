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
 * The lines the account reports for a table function written bare where a FROM clause names a relation. The call's
 * opening parenthesis is refused; what it holds is then read as the FROM list going on, so a second argument that
 * is a text - which may name a staged file there - carries the list on to a closing parenthesis with nothing to
 * close, and that is refused as well. A call of one argument, or of none, reports its opening parenthesis alone (live-verified):
 *
 * <pre>
 *   SELECT * FROM SPLIT_TO_TABLE('a,b', ',')            '(' at 28, ')' at 39
 *   SELECT * FROM fz JOIN SPLIT_TO_TABLE('a', ',') ON TRUE    '(' at 36, ')' at 45
 *   SELECT 1 WHERE EXISTS (SELECT 1 FROM SPLIT_TO_TABLE('a', ','))   '(' at 51, ')' at 61
 *   SELECT * FROM SPLIT_TO_TABLE('a,b')                 '(' at 28
 *   SELECT * FROM FLATTEN(INPUT =&gt; PARSE_JSON('[1]'))    '(' at 21
 * </pre>
 *
 * With its opening parenthesis refused the call's closing one closes the bracket around the call, so inside a
 * subquery it is that bracket's own closing parenthesis that is refused. Nothing after it is reported.
 *
 * <p>In a common table expression's query a call whose first argument is named reads on otherwise: the argument's
 * name is refused, then the token after the argument's value (live-verified):
 *
 * <pre>
 *   WITH c AS (SELECT * FROM FLATTEN(INPUT =&gt; PARSE_JSON('[1]'))) SELECT COUNT(*) FROM c    '(' at 32,
 *                                                                    'INPUT' at 33, ')' at 59
 * </pre>
 */
final class BareCallInFromLines {

    private BareCallInFromLines() {
    }

    /**
     * The lines for a text whose first fault is the parenthesis of a call written after FROM, JOIN or a comma, or
     * null when the fault is another, or the call's arguments are not read on to its closing parenthesis.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the two lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 2 || fault >= spoken.size() || spoken.get(fault).getType() != FrostlakeLexer.LPAREN
                || !StarModifierResync.isName(spoken.get(fault - 1))) {
            return null;
        }
        final int before = spoken.get(fault - 2).getType();
        if (before != FrostlakeLexer.FROM && before != FrostlakeLexer.JOIN && before != FrostlakeLexer.COMMA) {
            return null;
        }
        int depth = 0;
        int arguments = 1;
        int closer = -1;
        boolean textsAfterFirst = true;
        for (int i = fault; i < spoken.size() && closer < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                closer = depth == 0 ? i : -1;
            } else if (type == FrostlakeLexer.COMMA && depth == 1) {
                arguments++;
                // The argument after the comma must be one text and nothing else.
                textsAfterFirst = textsAfterFirst && i + 2 < spoken.size()
                    && spoken.get(i + 1).getType() == FrostlakeLexer.STRING_LITERAL
                    && (spoken.get(i + 2).getType() == FrostlakeLexer.COMMA
                        || spoken.get(i + 2).getType() == FrostlakeLexer.RPAREN);
            }
        }
        if (closer >= 0 && inCommonQuery(spoken, fault) && fault + 3 < spoken.size()
                && StarModifierResync.isName(spoken.get(fault + 1))
                && spoken.get(fault + 2).getType() == FrostlakeLexer.ARROW) {
            return namedFirstArgumentLines(spoken, fault, closer);
        }
        if (closer < 0 || arguments < 2 || !textsAfterFirst) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        // With its opening parenthesis refused, the call's closing one closes whatever bracket stands open around
        // the call, and the first closing parenthesis left with nothing to close is refused.
        int open = 0;
        for (int i = 0; i < fault; i++) {
            final int type = spoken.get(i).getType();
            open += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        for (int i = closer; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            open += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (open < 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                break;
            }
        }
        return lines;
    }

    /** The call's parenthesis, its first argument's name, and the token after that argument's value. */
    private static List<String> namedFirstArgumentLines(final List<Token> spoken, final int fault, final int closer) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
        int depth = 0;
        for (int i = fault + 3; i <= closer; i++) {
            final int type = spoken.get(i).getType();
            if (depth == 0 && (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.RPAREN)) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                return lines;
            }
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
        }
        return lines;
    }

    /** Whether the call at {@code fault} stands in a common table expression's query: {@code WITH … AS ( … )}. */
    private static boolean inCommonQuery(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault - 1; i >= 1; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return spoken.get(i - 1).getType() == FrostlakeLexer.AS && withBefore(spoken, i - 1);
                }
                depth--;
            }
        }
        return false;
    }

    /** Whether a WITH opens the statement before the AS at {@code as}. */
    private static boolean withBefore(final List<Token> spoken, final int as) {
        for (int i = as - 1; i >= 0; i--) {
            if (spoken.get(i).getType() == FrostlakeLexer.WITH) {
                return true;
            }
        }
        return false;
    }
}
