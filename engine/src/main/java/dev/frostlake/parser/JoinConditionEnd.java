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

import dev.frostlake.executor.LeadingCommentOffset;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The one line live reports when the input ends inside a join's ON or USING condition: the ON or the USING itself,
 * however the condition breaks off — an empty call, a call with arguments, an operator, a bracket, a subquery left
 * open, or nothing at all after the keyword (all live-verified):
 *
 * <pre>
 *   SELECT a FROM t JOIN t u ON ABS(                                   unexpected 'ON' at 25
 *   SELECT a FROM t JOIN t u ON a = 1 AND                              unexpected 'ON' at 25
 *   SELECT a FROM t JOIN t u ON a IN (SELECT a FROM t WHERE ABS(      unexpected 'ON' at 25
 *   SELECT a FROM t JOIN t u USING (a                                  unexpected 'USING' at 25
 *   SELECT a FROM (SELECT a FROM t JOIN t u ON ABS(                    unexpected 'ON' at 40
 * </pre>
 *
 * <p>The condition must be unfinished where the input ends — a bracket opened in it still open, or an operator, AND,
 * OR, NOT or the keyword itself last. A clause after the condition — a WHERE, a GROUP BY — or another table of the FROM
 * list ends the condition, so the input then ends in that clause instead, and a MERGE's ON, which follows no JOIN, is
 * not one either. Read off the tokens: the parse that ran out of input has given its tree up.
 *
 * <p>A statement's own semicolon ends it as the input's end does, in a script's statement or a block's alike, and
 * nothing after it is reported: {@code SELECT 1 FROM t JOIN u ON a = 1 AND; SELECT 1 x y} and {@code SELECT 1 FROM t
 * JOIN u ON; SELECT 2; SELECT 3 x y} are 'ON' at 23 alone (live-verified).
 */
final class JoinConditionEnd {

    private JoinConditionEnd() {
    }

    /**
     * The line naming the ON or USING whose condition the input, or the statement holding the parse's first fault at its
     * semicolon, ends in — or null when it ends anywhere else or the parse's first fault stands before that keyword.
     *
     * @param spoken     the default-channel tokens of the input, end of input included
     * @param firstFault the line and position of the parse's first fault
     * @return the line, or null
     */
    static String refusal(final List<Token> spoken, final int[] firstFault) {
        if (spoken.isEmpty() || spoken.get(spoken.size() - 1).getType() != Token.EOF || firstFault == null) {
            return null;
        }
        int end = spoken.size() - 1;
        for (int i = 0; i + 1 < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int[] at = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
            if (at[0] == firstFault[0] && at[1] == firstFault[1]) {
                if (token.getType() == FrostlakeLexer.SEMI) {
                    end = i;
                }
                break;
            }
        }
        final Token keyword = openKeyword(spoken, 0, end);
        if (keyword == null) {
            return null;
        }
        final String line = SyntaxErrorListener.sentence(keyword);
        final int[] at = SyntaxErrorListener.sentenceCoordinates(line);
        return at != null && (at[0] < firstFault[0] || at[0] == firstFault[0] && at[1] < firstFault[1]) ? line : null;
    }

    /**
     * The ON or USING whose condition is left unfinished where the token at position {@code end} of the spoken tokens —
     * a statement's semicolon, or the end of the input — ends the text read from position {@code start}, or null.
     *
     * @param spoken the default-channel tokens of the input, end of input included
     * @param start  the position the text read starts at
     * @param end    the position of the token that ends the text read
     * @return the keyword, or null
     */
    static Token openKeyword(final List<Token> spoken, final int start, final int end) {
        final List<Token> conditions = new ArrayList<>();
        final List<Boolean> joined = new ArrayList<>();
        conditions.add(null);
        joined.add(Boolean.FALSE);
        for (int i = start; i < end && i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            final int top = conditions.size() - 1;
            if (type == FrostlakeLexer.LPAREN) {
                conditions.add(null);
                joined.add(Boolean.FALSE);
            } else if (type == FrostlakeLexer.RPAREN) {
                if (top > 0) {
                    conditions.remove(top);
                    joined.remove(top);
                }
            } else if (type == FrostlakeLexer.JOIN) {
                conditions.set(top, null);
                joined.set(top, Boolean.TRUE);
            } else if ((type == FrostlakeLexer.ON || type == FrostlakeLexer.USING) && joined.get(top).booleanValue()) {
                conditions.set(top, spoken.get(i));
                joined.set(top, Boolean.FALSE);
            } else if (endsTheCondition(type)) {
                conditions.set(top, null);
                joined.set(top, Boolean.FALSE);
            }
        }
        final int last = Math.min(end, spoken.size()) - 1;
        for (int level = conditions.size() - 1; level >= 0; level--) {
            final Token keyword = conditions.get(level);
            if (keyword != null) {
                if (level == conditions.size() - 1 && (last < 0 || !leavesAnOperandOpen(spoken.get(last).getType()))) {
                    return null;
                }
                return keyword;
            }
        }
        return null;
    }

    /** Whether a condition's last token leaves it waiting for more: the keyword itself, an operator, AND, OR, NOT. */
    private static boolean leavesAnOperandOpen(final int type) {
        return type == FrostlakeLexer.ON || type == FrostlakeLexer.USING || type == FrostlakeLexer.AND
            || type == FrostlakeLexer.OR || type == FrostlakeLexer.NOT || type == FrostlakeLexer.EQ
            || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT || type == FrostlakeLexer.LTE
            || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE || type == FrostlakeLexer.PLUS
            || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR || type == FrostlakeLexer.SLASH
            || type == FrostlakeLexer.PERCENT || type == FrostlakeLexer.PIPE_PIPE;
    }

    /** Whether a token at the condition's own level ends it: a later clause, another FROM-list table, a new query. */
    private static boolean endsTheCondition(final int type) {
        return type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.OFFSET || type == FrostlakeLexer.FETCH || type == FrostlakeLexer.UNION
            || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW || type == FrostlakeLexer.INTERSECT
            || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.SELECT;
    }
}
