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

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.parser.FrostlakeLexer;

import org.antlr.v4.runtime.Token;

import java.util.List;

/**
 * The lines the account reports for a SQL function's body that opens with CASE, CAST or WHEN, as a parameter so
 * named would be written. The account reads the body in a frame of parentheses and each word as what it opens in an
 * expression, never as a name (live-verified):
 * <ul>
 *   <li>CASE opens a CASE expression whose operand reads on to the frame's closing parenthesis, which is refused:
 *       {@code AS 'case + 1'} at the ')' 11;</li>
 *   <li>CAST must be followed by its own parenthesis: the token after it is refused, and the frame's closing
 *       parenthesis too when another stands between them: {@code AS 'cast + 1'} at the '+' 7 and the ')' 11;</li>
 *   <li>WHEN opens nothing: it is refused, and the frame's closing parenthesis too when anything follows the word:
 *       {@code AS 'when + 1'} at 'when' 2 and the ')' 11.</li>
 * </ul>
 * The rule applies only where the word is the body's one fault: the frame must read as an expression once a name
 * stands in the word's place.
 */
final class SqlUdfKeywordBodyLines {

    private SqlUdfKeywordBodyLines() {
    }

    /**
     * The lines for such a body, or null when it opens with none of the words or has another fault.
     *
     * @param framed       the body inside its frame of parentheses
     * @param framedTokens the frame's spoken tokens, end of input excluded
     * @return the lines, one a refusal, joined by line ends, or null
     */
    static String lines(final String framed, final List<Token> framedTokens) {
        if (framedTokens.size() < 3) {
            return null;
        }
        final Token word = framedTokens.get(1);
        final Token next = framedTokens.get(2);
        final Token close = framedTokens.get(framedTokens.size() - 1);
        final int type = word.getType();
        if (type != FrostlakeLexer.CASE && type != FrostlakeLexer.CAST && type != FrostlakeLexer.WHEN
                || close.getType() != FrostlakeLexer.RPAREN
                || type == FrostlakeLexer.CAST && next.getType() == FrostlakeLexer.LPAREN
                || type == FrostlakeLexer.CASE && holdsWhen(framedTokens)) {
            return null;
        }
        try {
            AntlrExpressionParser.parseTree(framed.substring(0, word.getStartIndex()) + "x"
                + framed.substring(word.getStopIndex() + 1));
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
        if (type == FrostlakeLexer.CASE) {
            return line(close);
        }
        if (type == FrostlakeLexer.CAST) {
            return next == close ? line(next) : line(next) + "\n" + line(close);
        }
        return next == close ? line(word) : line(word) + "\n" + line(close);
    }

    /** Whether a WHEN follows the body's CASE: a CASE expression in full, read as one. */
    private static boolean holdsWhen(final List<Token> framedTokens) {
        for (int i = 2; i < framedTokens.size(); i++) {
            if (framedTokens.get(i).getType() == FrostlakeLexer.WHEN) {
                return true;
            }
        }
        return false;
    }

    private static String line(final Token token) {
        return "syntax error line " + token.getLine() + " at position " + token.getCharPositionInLine()
            + " unexpected '" + token.getText() + "'.";
    }
}
