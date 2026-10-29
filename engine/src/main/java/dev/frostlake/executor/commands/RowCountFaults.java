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

package dev.frostlake.executor.commands;

import dev.frostlake.parser.FrostlakeLexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.Token;

/**
 * The refusal of a SQL function's query body whose row count is no count: the account reads the body inside a frame
 * of parentheses and refuses each of these at CREATE, in the frame's positions (all live-verified over
 * {@code t (a INT, b INT)}):
 *
 * <pre>
 *   SELECT a FROM t LIMIT -1                    'SELECT' 1, then the ')' closing the frame
 *   SELECT a FROM t LIMIT -1 OFFSET 1           'SELECT' 1, then the 'OFFSET' after the number
 *   SELECT a FROM t FETCH FIRST -1 ROWS ONLY    'SELECT' 1, then the 'ROWS'
 *   SELECT a FROM t FETCH FIRST 'x' ROWS ONLY   the text alone
 *   SELECT a FROM t LIMIT n                     'SELECT' 1 alone, a parameter where the count stands, after
 *                                               OFFSET and FETCH FIRST too
 *   SELECT a FROM t LIMIT n + 1                 'SELECT' 1, then the ')' closing the frame
 *   SELECT a FROM t LIMIT -n                    likewise
 *   SELECT a FROM t LIMIT (n) + 1               'SELECT' 1, the 'n' and the ')' closing the frame; a count
 *                                               in parentheses alone reads, and is judged as it compiles
 * </pre>
 *
 * A body that opens with WITH is refused alike, at the WITH. A query nested in the body with such a count is refused
 * too, the body's first word first: {@code SELECT COUNT(*) FROM (SELECT a FROM t LIMIT n)}, and alike after OFFSET
 * and FETCH FIRST, with a negative count, in a scalar subquery and under IN. The lines after the first are those of
 * the account's reading on (see {@link FramedQueryReading}).
 */
final class RowCountFaults {

    /** The operators a count in parentheses may not be followed by. */
    private static final Set<Integer> OPERATORS = Set.of(FrostlakeLexer.PLUS, FrostlakeLexer.MINUS,
        FrostlakeLexer.STAR, FrostlakeLexer.SLASH, FrostlakeLexer.PERCENT, FrostlakeLexer.PIPE_PIPE, FrostlakeLexer.EQ,
        FrostlakeLexer.NEQ, FrostlakeLexer.LT, FrostlakeLexer.LTE, FrostlakeLexer.GT, FrostlakeLexer.GTE);

    private RowCountFaults() {
    }

    /**
     * The refusal's lines for a framed query body whose own row count, or the row count of a query nested in it, is
     * one of the faults above, or null.
     *
     * @param framed the framed body's spoken tokens, the frame's parentheses included
     * @return the lines, or null
     */
    static String lines(final List<Token> framed) {
        if (framed.size() < 4 || framed.get(1).getType() != FrostlakeLexer.SELECT
                && framed.get(1).getType() != FrostlakeLexer.WITH) {
            return null;
        }
        int depth = 0;
        for (int i = 0; i + 1 < framed.size(); i++) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            if (depth != 1 || !countsRows(framed, i)) {
                continue;
            }
            final int at = i + (type == FrostlakeLexer.FETCH ? 2 : 1);
            if (at + 1 >= framed.size()) {
                return null;
            }
            if (noCount(framed, at)) {
                return FramedQueryReading.lines(framed);
            }
            if (framed.get(at).getType() == FrostlakeLexer.STRING_LITERAL && type == FrostlakeLexer.FETCH) {
                final List<Token> refused = new ArrayList<>();
                refused.add(framed.get(at));
                return rendered(refused);
            }
            // A count that is a count: a later one may still be none.
        }
        return nestedCountFault(framed) ? FramedQueryReading.lines(framed) : null;
    }

    /** Whether a query nested in the body holds a row count that is none. */
    private static boolean nestedCountFault(final List<Token> framed) {
        int depth = 0;
        for (int i = 0; i + 1 < framed.size(); i++) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            if (depth >= 2 && countsRows(framed, i) && noCount(framed, i + (type == FrostlakeLexer.FETCH ? 2 : 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the row count at the index is none the account's query grammar reads: a name, as a parameter is, a
     * negative number or name, or a count in parentheses that an operator follows.
     *
     * @param tokens the spoken tokens
     * @param at     the index the count starts at
     * @return whether it is none
     */
    static boolean noCount(final List<Token> tokens, final int at) {
        if (at >= tokens.size()) {
            return false;
        }
        final int type = tokens.get(at).getType();
        if (type == FrostlakeLexer.IDENTIFIER) {
            return true;
        }
        if (type == FrostlakeLexer.MINUS) {
            return at + 1 < tokens.size() && (tokens.get(at + 1).getType() == FrostlakeLexer.INTEGER_LITERAL
                || tokens.get(at + 1).getType() == FrostlakeLexer.IDENTIFIER);
        }
        if (type != FrostlakeLexer.LPAREN) {
            return false;
        }
        int depth = 0;
        for (int i = at; i < tokens.size(); i++) {
            if (tokens.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (tokens.get(i).getType() == FrostlakeLexer.RPAREN && --depth == 0) {
                return i + 1 < tokens.size() && OPERATORS.contains(tokens.get(i + 1).getType());
            }
        }
        return false;
    }

    /**
     * Whether the token at the index opens a row count: LIMIT, OFFSET, or FETCH FIRST or NEXT.
     *
     * @param framed the spoken tokens
     * @param i      the index
     * @return whether it does
     */
    static boolean countsRows(final List<Token> framed, final int i) {
        final int type = framed.get(i).getType();
        if (type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.OFFSET) {
            return true;
        }
        return type == FrostlakeLexer.FETCH && i + 1 < framed.size()
            && (framed.get(i + 1).getType() == FrostlakeLexer.FIRST
                || framed.get(i + 1).getType() == FrostlakeLexer.NEXT);
    }

    /** One syntax-error line per token, in the frame's positions. */
    private static String rendered(final List<Token> tokens) {
        final StringBuilder lines = new StringBuilder();
        for (final Token token : tokens) {
            lines.append(lines.length() == 0 ? "" : "\n").append("syntax error line ").append(token.getLine())
                .append(" at position ").append(token.getCharPositionInLine()).append(" unexpected '")
                .append(token.getText()).append("'.");
        }
        return lines.toString();
    }
}
