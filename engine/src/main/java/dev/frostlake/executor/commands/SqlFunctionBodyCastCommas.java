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

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines a SQL function's body is refused with as the function is created when it writes a comma inside a CAST's
 * or a TRY_CAST's own parentheses. The account reads the body in its frame, between parentheses, so a place on the
 * body's first line is one past the body's own (all live-verified):
 *
 * <pre>
 *   SELECT UPPER(CAST(1, 2))     the SELECT, then the token after the first '(' past it, the CAST
 *   SELECT CAST(1, 2) FROM t     the SELECT, then the 1 after the cast's '('
 *   SELECT CAST(a, 2) FROM t     the SELECT, the a, then the frame's own closing ')': a name there reads on
 *   WITH c AS (SELECT CAST(…))   the WITH, then the SELECT after the '('
 *   SELECT 1 + CAST(1, 2)        the SELECT, then the comma: a value after the SELECT reads on as one
 *   SELECT a, CAST(1, 2) FROM t  the SELECT, the comma, then the token after the cast, the FROM
 *   UPPER(CAST(1, 2))            the comma, then the frame's own closing ')'
 * </pre>
 *
 * The reading resumes at the first '(' when one follows the SELECT, or a word and then a '(' or a word; otherwise it
 * reads on as a select list, to the cast's comma.
 */
final class SqlFunctionBodyCastCommas {

    private SqlFunctionBodyCastCommas() {
    }

    /**
     * The body's lines, or null when it writes no comma inside a cast's parentheses.
     *
     * @param body   the body as written
     * @param spoken its tokens, comments and blanks dropped, end of input excluded
     * @return the lines
     */
    static List<String> lines(final String body, final List<Token> spoken) {
        final int[] cast = castComma(spoken);
        if (cast == null) {
            return null;
        }
        final Token comma = spoken.get(cast[1]);
        final List<String> lines = new ArrayList<>();
        final Token first = spoken.get(0);
        if (first.getType() != FrostlakeLexer.SELECT && first.getType() != FrostlakeLexer.WITH) {
            // An expression: the comma, then the frame's closing parenthesis, one past the body's end.
            lines.add(sentence(comma.getLine(), framed(comma), comma.getText()));
            lines.add(frameEnd(body));
            return lines;
        }
        // A query, which the frame does not read: its first word, then where the reading resumes.
        lines.add(sentence(first.getLine(), framed(first), first.getText()));
        final int resume = resumesAtParenthesis(spoken);
        if (resume < 0) {
            // Read on as a select list: the comma, and after a cast that is an item of its own what follows it.
            lines.add(sentence(comma.getLine(), framed(comma), comma.getText()));
            if (cast[0] > 0 && spoken.get(cast[0] - 1).getType() == FrostlakeLexer.COMMA) {
                if (cast[2] >= 0 && cast[2] + 1 < spoken.size()) {
                    final Token next = spoken.get(cast[2] + 1);
                    lines.add(sentence(next.getLine(), framed(next), next.getText()));
                } else {
                    lines.add(frameEnd(body));
                }
            }
            return lines;
        }
        final Token after = spoken.get(resume);
        lines.add(sentence(after.getLine(), framed(after), after.getText()));
        if (after.getType() == FrostlakeLexer.IDENTIFIER || after.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            lines.add(frameEnd(body));
        }
        return lines;
    }

    /**
     * Where the reading resumes after the query's first word: the token after the first '(' when a '(' follows the
     * word, or a word and then a '(' or a word; -1 when the query reads on as a select list instead.
     */
    private static int resumesAtParenthesis(final List<Token> spoken) {
        if (spoken.size() < 3) {
            return -1;
        }
        final Token next = spoken.get(1);
        final boolean opens = next.getType() == FrostlakeLexer.LPAREN
            || isWord(next) && (spoken.get(2).getType() == FrostlakeLexer.LPAREN || isWord(spoken.get(2)));
        if (!opens) {
            return -1;
        }
        for (int i = 1; i + 1 < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * The first CAST or TRY_CAST whose own parentheses hold a comma: its place, the comma's and its closing
     * parenthesis's, -1 for one never closed; null when no cast holds one.
     */
    private static int[] castComma(final List<Token> spoken) {
        for (int i = 0; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if ((type != FrostlakeLexer.CAST && type != FrostlakeLexer.TRY_CAST)
                    || spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN) {
                continue;
            }
            int depth = 0;
            int comma = -1;
            int close = -1;
            for (int j = i + 2; j < spoken.size() && close < 0; j++) {
                final int inner = spoken.get(j).getType();
                if (inner == FrostlakeLexer.LPAREN) {
                    depth++;
                } else if (inner == FrostlakeLexer.RPAREN) {
                    if (depth == 0) {
                        close = j;
                    } else {
                        depth--;
                    }
                } else if (inner == FrostlakeLexer.COMMA && depth == 0 && comma < 0) {
                    comma = j;
                }
            }
            if (comma >= 0) {
                return new int[] {i, comma, close};
            }
        }
        return null;
    }

    /** The frame's closing parenthesis, one past the body's end, and one further on its first line. */
    private static String frameEnd(final String body) {
        final int line = 1 + newlines(body);
        final int lastLength = body.length() - (body.lastIndexOf('\n') + 1);
        return sentence(line, lastLength + (line == 1 ? 1 : 0), ")");
    }

    /** Whether a token is a word, a name or a keyword, rather than a literal or an operator. */
    private static boolean isWord(final Token token) {
        final char lead = token.getText().charAt(0);
        return Character.isLetter(lead) || lead == '_' || lead == '"';
    }

    /** A token's place in the frame: one further on the body's first line, where the frame's '(' stands. */
    private static int framed(final Token token) {
        return token.getCharPositionInLine() + (token.getLine() == 1 ? 1 : 0);
    }

    private static int newlines(final String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static String sentence(final int line, final int position, final String text) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + text + "'.";
    }
}
