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

import dev.frostlake.executor.SqlCompilationError;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An OVERLAY written in the ANSI PLACING form under brackets of a select item: no name reads that form, so the PLACING
 * is refused, and live's recovery then names what the brackets around the call decide (all live-verified):
 *
 * <pre>
 *   SELECT CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) FROM t          'PLACING', then the 'x', then the 2
 *   SELECT UPPER(CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT)) FROM t   'PLACING', then the 'x', then the 2
 *   SELECT UPPER(OVERLAY(b PLACING 'x' FROM 2)) FROM t                'PLACING', then the 2
 *   SELECT UPPER(UPPER(OVERLAY(b PLACING 'x' FROM 2))) FROM t         'PLACING', then the inner call's ')'
 *   SELECT CAST(UPPER(OVERLAY(b PLACING 'x' FROM 2)) AS INT) FROM t   'PLACING', then the cast's ')'
 *   SELECT (OVERLAY(b PLACING 'x' FROM 2)) FROM t                     'PLACING', then the 'x'
 * </pre>
 *
 * The call's operand is one name, the PLACING's value one literal and the FROM's one number, and each bracket holds
 * the one beneath it alone; any other stack keeps the parse's own lines.
 */
public final class OverlayPlacingLines {

    private OverlayPlacingLines() {
    }

    /**
     * Refuse a statement whose first fault is the PLACING of an OVERLAY under a measured stack of brackets, with the
     * PLACING's line and then the stack's.
     *
     * @param sql      the script's source text
     * @param parsed   the parse's tokens
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final CommonTokenStream parsed,
                                     final SyntaxErrorListener listener) {
        if (sql == null || !listener.hasErrors()) {
            return;
        }
        parsed.fill();
        final List<Token> spoken = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            if (parsed.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                spoken.add(parsed.get(i));
            }
        }
        final int fault = listener.firstFaultStartIndex();
        int placing = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getStartIndex() == fault && spoken.get(i).getType() != Token.EOF) {
                placing = i;
                break;
            }
        }
        // OVERLAY ( name PLACING value FROM number )
        if (placing < 4 || placing + 5 >= spoken.size() || !isWord(spoken.get(placing), "PLACING")
                || spoken.get(placing - 1).getType() != FrostlakeLexer.IDENTIFIER
                || spoken.get(placing - 2).getType() != FrostlakeLexer.LPAREN
                || !isWord(spoken.get(placing - 3), "OVERLAY")
                || !isLiteral(spoken.get(placing + 1).getType())
                || spoken.get(placing + 2).getType() != FrostlakeLexer.FROM
                || spoken.get(placing + 3).getType() != FrostlakeLexer.INTEGER_LITERAL
                || spoken.get(placing + 4).getType() != FrostlakeLexer.RPAREN) {
            return;
        }
        final List<Token> stacked = stackedLines(spoken, placing);
        if (stacked == null) {
            return;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(placing)));
        for (final Token token : stacked) {
            lines.add(SyntaxErrorListener.sentence(token));
        }
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /**
     * The tokens named after the PLACING, read from the brackets around the call — from the call outwards, C a cast's,
     * F a call's, P a plain parenthesis — up to the first select item of a statement that is the text's first; null
     * for a stack that was not measured.
     */
    private static List<Token> stackedLines(final List<Token> spoken, final int placing) {
        final StringBuilder stack = new StringBuilder();
        final List<Token> closes = new ArrayList<>();
        int left = placing - 4;
        int right = placing + 5;
        while (left >= 0 && spoken.get(left).getType() == FrostlakeLexer.LPAREN && right < spoken.size()) {
            final int opener = left > 0 ? spoken.get(left - 1).getType() : Token.INVALID_TYPE;
            if (opener == FrostlakeLexer.CAST) {
                if (right + 2 >= spoken.size() || spoken.get(right).getType() != FrostlakeLexer.AS
                        || spoken.get(right + 2).getType() != FrostlakeLexer.RPAREN) {
                    return null;
                }
                stack.append('C');
                closes.add(spoken.get(right + 2));
                right += 3;
                left -= 2;
            } else if (opener == FrostlakeLexer.IDENTIFIER
                    && (left < 2 || spoken.get(left - 2).getType() != FrostlakeLexer.DOT)) {
                if (spoken.get(right).getType() != FrostlakeLexer.RPAREN) {
                    return null;
                }
                stack.append('F');
                closes.add(spoken.get(right));
                right += 1;
                left -= 2;
            } else {
                if (spoken.get(right).getType() != FrostlakeLexer.RPAREN) {
                    return null;
                }
                stack.append('P');
                closes.add(spoken.get(right));
                right += 1;
                left -= 1;
            }
        }
        if (left != 0 || spoken.get(0).getType() != FrostlakeLexer.SELECT || right >= spoken.size()
                || spoken.get(right).getType() != FrostlakeLexer.FROM) {
            return null;
        }
        final Token value = spoken.get(placing + 1);
        final Token fromValue = spoken.get(placing + 3);
        final List<Token> named = new ArrayList<>();
        switch (stack.toString()) {
            case "C":
            case "CF":
                named.add(value);
                named.add(fromValue);
                return named;
            case "F":
                named.add(fromValue);
                return named;
            case "FF":
                named.add(closes.get(0));
                return named;
            case "FC":
                named.add(closes.get(1));
                return named;
            case "P":
                named.add(value);
                return named;
            default:
                return null;
        }
    }

    private static boolean isWord(final Token token, final String word) {
        return token.getType() == FrostlakeLexer.IDENTIFIER && word.equals(token.getText().toUpperCase(Locale.ROOT));
    }

    private static boolean isLiteral(final int type) {
        return type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL;
    }
}
