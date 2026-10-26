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
 * </pre>
 *
 * A query nested in the body with a parameter for its count is refused too, the body's first word first:
 * {@code SELECT COUNT(*) FROM (SELECT a FROM t LIMIT n)}, and alike after OFFSET and FETCH FIRST, in a scalar
 * subquery and under IN.
 */
final class RowCountFaults {

    private RowCountFaults() {
    }

    /**
     * The refusal's lines for a framed query body whose own row count is one of the faults above, or null.
     *
     * @param framed the framed body's spoken tokens, the frame's parentheses included
     * @return the lines, or null
     */
    static String lines(final List<Token> framed) {
        if (framed.size() < 4 || framed.get(1).getType() != FrostlakeLexer.SELECT) {
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
            final Token count = framed.get(at);
            final List<Token> refused = new ArrayList<>();
            if (count.getType() == FrostlakeLexer.MINUS
                    && framed.get(at + 1).getType() == FrostlakeLexer.INTEGER_LITERAL && at + 2 < framed.size()) {
                refused.add(framed.get(1));
                refused.add(framed.get(at + 2));
            } else if (count.getType() == FrostlakeLexer.STRING_LITERAL && type == FrostlakeLexer.FETCH) {
                refused.add(count);
            } else if (count.getType() == FrostlakeLexer.IDENTIFIER) {
                refused.add(framed.get(1));
                if (isOperator(framed.get(at + 1))) {
                    refused.add(framed.get(framed.size() - 1));
                }
            } else {
                // A count that is a count: a later one may still be none.
                continue;
            }
            return rendered(refused);
        }
        return nestedParameterCount(framed);
    }

    /** The body's first word, where a query nested in it takes a parameter for its row count; null otherwise. */
    private static String nestedParameterCount(final List<Token> framed) {
        int depth = 0;
        for (int i = 0; i + 1 < framed.size(); i++) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            if (depth < 2 || !countsRows(framed, i)) {
                continue;
            }
            final int at = i + (type == FrostlakeLexer.FETCH ? 2 : 1);
            if (at < framed.size() && framed.get(at).getType() == FrostlakeLexer.IDENTIFIER) {
                final List<Token> first = new ArrayList<>();
                first.add(framed.get(1));
                return rendered(first);
            }
        }
        return null;
    }

    /** Whether the token at {@code i} opens a row count: LIMIT, OFFSET, or FETCH FIRST or NEXT. */
    private static boolean countsRows(final List<Token> framed, final int i) {
        final int type = framed.get(i).getType();
        if (type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.OFFSET) {
            return true;
        }
        return type == FrostlakeLexer.FETCH && i + 1 < framed.size()
            && (framed.get(i + 1).getType() == FrostlakeLexer.FIRST
                || framed.get(i + 1).getType() == FrostlakeLexer.NEXT);
    }

    private static boolean isOperator(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH;
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
