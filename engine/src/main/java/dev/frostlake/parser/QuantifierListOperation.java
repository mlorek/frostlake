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

import java.util.List;

import org.antlr.v4.runtime.Token;

/**
 * What the account goes on to refuse in the list of a quantifier no predicate takes, once the token that opens the
 * list is refused, where the place the predicate stands asks for nothing more: an operation the list holds at its own
 * level - its first operator any but a plus - has the comma or the closing parenthesis after it refused
 * (live-verified). {@code 'a' NOT LIKE ANY ('x' || 'y')} names the list's {@code )}, {@code ('x' || 'y', 'z')} the
 * comma, {@code ('x', 'y' - 'z')} the {@code )}, and {@code ('x' + 'y')} nothing - in a select list, a WHERE and a
 * function's body read as an expression alike.
 */
public final class QuantifierListOperation {

    private QuantifierListOperation() {
    }

    /**
     * @param tokens the tokens read
     * @param list   the index of the list's own parenthesis
     * @return the index of the token refused after the list's first operation, or -1 for none
     */
    public static int refusedAfter(final List<Token> tokens, final int list) {
        int depth = 0;
        boolean operated = false;
        for (int i = list + 1; i < tokens.size(); i++) {
            final int type = tokens.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return operated ? i : -1;
                }
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.COMMA) {
                if (operated) {
                    return i;
                }
            } else if (depth == 0 && !operated && i > list + 1 && isOperator(type)) {
                if (type == FrostlakeLexer.PLUS) {
                    return -1;
                }
                operated = true;
            }
        }
        return -1;
    }

    /** Whether a token is a binary operator: arithmetic, concatenation, a comparison, AND or OR. */
    private static boolean isOperator(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE
            || type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE
            || type == FrostlakeLexer.AND || type == FrostlakeLexer.OR;
    }
}
