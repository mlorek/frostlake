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
 * is refused, and live's recovery then names what the brackets around the call decide. The brackets are read from the
 * call outwards, C a cast's or a TRY_CAST's, F a call's, P a plain parenthesis (all live-verified):
 *
 * <pre>
 *   C, then anything around it   'PLACING', then the PLACING's value, then the FROM's
 *   F, F P                       'PLACING', then the FROM's value
 *   F F, F C F                   'PLACING', then the ')' of the bracket around the call
 *   F F F, F C                   'PLACING', then the ')' of the bracket around that one
 *   P                            'PLACING', then the PLACING's value
 *   P P, P F, P C                'PLACING', then the FROM's value
 *   CASE WHEN … THEN the call    'PLACING', then the PLACING's value
 * </pre>
 *
 * A FOR tail adds its value's line, after a call's or a cast's. The call's operand is one name, the PLACING's value
 * one literal and the FROM's one number, except under a call alone, where each may be an expression and its first
 * token is named. The item stands first in the select list, or second after an item of one token, and ends at the
 * FROM, or at an AS alias or a + of a number before it; any other shape keeps the parse's own lines.
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
        if (placing < 3 || !isWord(spoken.get(placing), "PLACING")) {
            return;
        }
        final OverlayCall call = OverlayCall.around(spoken, placing);
        if (call == null) {
            return;
        }
        final List<Token> stacked = stackedLines(spoken, call);
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

    /** The tokens named after the PLACING, read from the brackets around the call; null for a shape not measured. */
    private static List<Token> stackedLines(final List<Token> spoken, final OverlayCall call) {
        final StringBuilder stack = new StringBuilder();
        final List<Token> closes = new ArrayList<>();
        int left = call.getOpen() - 2;
        int right = call.getClose() + 1;
        while (left >= 0 && spoken.get(left).getType() == FrostlakeLexer.LPAREN && right < spoken.size()) {
            final int opener = left > 0 ? spoken.get(left - 1).getType() : Token.INVALID_TYPE;
            if (opener == FrostlakeLexer.CAST || opener == FrostlakeLexer.TRY_CAST) {
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
        final String kinds = stack.toString();
        final List<Token> named = new ArrayList<>();
        if (kinds.isEmpty()) {
            // SELECT CASE WHEN x THEN OVERLAY(…) END FROM t names the PLACING's value.
            if (call.isSimple() && call.getFor() < 0 && left == 4 && spoken.get(0).getType() == FrostlakeLexer.SELECT
                    && spoken.get(1).getType() == FrostlakeLexer.CASE && spoken.get(2).getType() == FrostlakeLexer.WHEN
                    && spoken.get(left).getType() == FrostlakeLexer.THEN && right + 1 < spoken.size()
                    && spoken.get(right).getType() == FrostlakeLexer.END
                    && spoken.get(right + 1).getType() == FrostlakeLexer.FROM) {
                named.add(spoken.get(call.getValue()));
                return named;
            }
            return null;
        }
        if (!firstOrSecondItem(spoken, left) || !endsAtFrom(spoken, right) || !call.isSimple() && !"F".equals(kinds)
                || call.getFor() >= 0 && !"F".equals(kinds) && kinds.charAt(0) != 'C') {
            return null;
        }
        final Token value = spoken.get(call.getValue());
        final Token fromValue = spoken.get(call.getFromValue());
        if (kinds.charAt(0) == 'C') {
            named.add(value);
            named.add(fromValue);
        } else {
            switch (kinds) {
                case "F":
                case "FP":
                case "PP":
                case "PF":
                case "PC":
                    named.add(fromValue);
                    break;
                case "FF":
                case "FCF":
                    named.add(closes.get(0));
                    break;
                case "FFF":
                case "FC":
                    named.add(closes.get(1));
                    break;
                case "P":
                    named.add(value);
                    break;
                default:
                    return null;
            }
        }
        if (call.getFor() >= 0) {
            named.add(spoken.get(call.getFor() + 1));
        }
        return named;
    }

    /** Whether the item begins the select list, or follows a first item of one token and its comma. */
    private static boolean firstOrSecondItem(final List<Token> spoken, final int left) {
        if (spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return false;
        }
        return left == 0 || left == 2 && spoken.get(2).getType() == FrostlakeLexer.COMMA;
    }

    /** Whether the item ends at the FROM, or at an AS alias or a + of a number before it. */
    private static boolean endsAtFrom(final List<Token> spoken, final int right) {
        if (right < spoken.size() && spoken.get(right).getType() == FrostlakeLexer.FROM) {
            return true;
        }
        if (right + 2 >= spoken.size() || spoken.get(right + 2).getType() != FrostlakeLexer.FROM) {
            return false;
        }
        final int first = spoken.get(right).getType();
        final int second = spoken.get(right + 1).getType();
        return first == FrostlakeLexer.AS && second == FrostlakeLexer.IDENTIFIER
            || first == FrostlakeLexer.PLUS && second == FrostlakeLexer.INTEGER_LITERAL;
    }

    static boolean isWord(final Token token, final String word) {
        return token.getType() == FrostlakeLexer.IDENTIFIER && word.equals(token.getText().toUpperCase(Locale.ROOT));
    }
}
