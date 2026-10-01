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
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The line live stacks after a fault in an argument of a call written in a SELECT list, when the argument begins
 * with a name (all live-verified). Live retries the argument as one that opens with its leading name — a named or a
 * lambda argument — and refuses the token after that name as well, which may be the refused token itself:
 *
 * <pre>
 *   SELECT ABS(T.x y) FROM T                'y', then '.'
 *   SELECT ABS(T.x.) FROM T                 ')', then '.'
 *   SELECT CONCAT('a', T.x y) FROM T        'y', then the '.' after the second argument's T
 *   SELECT ABS(x y) FROM T                  'y', then 'y' again
 *   SELECT ABS(x +) FROM T                  ')' alone — a fault after an operator is the expression's own
 *   SELECT ABS(1 y) FROM T                  'y' alone — no name opens the argument, nor does a typed
 *                                           literal such as DATE '2024-01-01'
 *   SELECT x FROM T WHERE ABS(T.x y) = 1    'y' alone — outside the SELECT list
 * </pre>
 *
 * <p>A call inside another call, a CAST and a subquery stack other lines, and are left alone. Read off the tokens: the
 * parse that met the fault gave the statement up before it read the call.
 */
final class CallArgumentRetryLine {

    private static final IntervalSet NAMES =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private CallArgumentRetryLine() {
    }

    /**
     * The token live refuses after {@code first}, or null when the fault is no such case.
     *
     * @param stream the parsed tokens
     * @param first  the token the fault is reported at
     * @return the token after the argument's leading name, or null
     */
    static Token after(final TokenStream stream, final Token first) {
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Token> spoken = new ArrayList<>();
        for (int i = 0; i < stream.size() && i <= first.getTokenIndex(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                if (token.getType() == FrostlakeLexer.SEMI) {
                    spoken.clear();
                } else {
                    spoken.add(token);
                }
            }
        }
        final int fault = spoken.size() - 1;
        if (fault < 3 || spoken.get(fault).getTokenIndex() != first.getTokenIndex()
                || spoken.get(0).getType() != FrostlakeLexer.SELECT || !endsOperand(spoken.get(fault - 1))) {
            return null;
        }
        int open = -1;
        int argument = -1;
        for (int i = 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                if (open >= 0) {
                    return null;
                }
                open = i;
                argument = i + 1;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (open < 0) {
                    return null;
                }
                open = -1;
            } else if (open < 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
                    || type == FrostlakeLexer.SELECT)) {
                return null;
            } else if (open >= 0 && type == FrostlakeLexer.COMMA) {
                argument = i + 1;
            }
        }
        if (open < 1 || !isName(spoken.get(open - 1)) || argument >= fault || !isName(spoken.get(argument))
                || spoken.get(argument + 1).getType() == FrostlakeLexer.STRING_LITERAL) {
            return null;
        }
        return spoken.get(argument + 1);
    }

    /** Whether a token can end the operand a fault follows: a name, a literal, a closing bracket or a dot. */
    private static boolean endsOperand(final Token token) {
        final int type = token.getType();
        return isName(token) || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.DOT;
    }

    private static boolean isName(final Token token) {
        return NAMES.contains(token.getType()) || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
