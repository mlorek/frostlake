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
 *   F F F F                      'PLACING', then the ')' of the third bracket from the call
 *   F F C                        'PLACING', then the ')' of the bracket around the call and the cast's
 *   F F C F                      'PLACING', then the ')' of the two brackets nearest the call
 *   P                            'PLACING', then the PLACING's value
 *   P P, P F, P C                'PLACING', then the FROM's value
 *   CASE WHEN … THEN the call    'PLACING', then the PLACING's value
 * </pre>
 *
 * A FOR tail adds its value's line, after a call's or a cast's. The call's operand is one name, the PLACING's value
 * one literal and the FROM's one number, except under a call alone or a plain parenthesis alone, where each may be
 * an expression and its first token is named. The item stands first in the select list, or after items of one token
 * each, and ends at the FROM, or at an AS alias or a + of a number before it; any other shape keeps the parse's own
 * lines.
 *
 * <p>In a block's statement - a DECLARE block's too - the statement's own lines are the same, and the block's recovery
 * names more after them: after a SELECT, the first word after its semicolon, the next statement's or the block's END,
 * the outermost END where nested blocks end together; after a cast in a RESULTSET's bracketed query the cast's AS and
 * its closing bracket, and after a call there the call's closing bracket and the query's (live-verified). A block's
 * other shapes keep the parse's own lines.
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
        final int start = spoken.get(0).getType() == FrostlakeLexer.SELECT ? 0 : blockSelect(spoken, placing);
        final List<Token> stacked = start < 0 ? null : stackedLines(spoken, call, start);
        if (stacked == null) {
            return;
        }
        if (start > 0) {
            final List<Token> recovered = blockRecovery(spoken, call, start);
            if (recovered == null) {
                return;
            }
            stacked.addAll(recovered);
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(placing)));
        for (final Token token : stacked) {
            lines.add(SyntaxErrorListener.sentence(token));
        }
        throw new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), lines, sql);
    }

    /**
     * The SELECT that opens the block statement holding the PLACING: one written after the block's BEGIN or a
     * statement's semicolon, or the query a RESULTSET is assigned in brackets; -1 for any other place.
     */
    private static int blockSelect(final List<Token> spoken, final int placing) {
        if (spoken.get(0).getType() != FrostlakeLexer.BEGIN && spoken.get(0).getType() != FrostlakeLexer.DECLARE) {
            return -1;
        }
        for (int i = placing - 1; i > 0; i--) {
            if (spoken.get(i).getType() != FrostlakeLexer.SELECT) {
                continue;
            }
            final int before = spoken.get(i - 1).getType();
            if (before == FrostlakeLexer.BEGIN || before == FrostlakeLexer.SEMI) {
                return i;
            }
            return before == FrostlakeLexer.LPAREN && i >= 3 && spoken.get(i - 2).getType() == FrostlakeLexer.COLON_EQ
                && spoken.get(i - 3).getType() == FrostlakeLexer.RESULTSET ? i : -1;
        }
        return -1;
    }

    /**
     * What a block's recovery names after a statement's own lines: after a SELECT, the first word after its semicolon
     * - the next statement's, or the block's END, the outermost one's where blocks end together; after a cast that
     * opens a RESULTSET's bracketed query, the cast's AS and its closing bracket; after a call, the call's closing
     * bracket and the query's; null for any other shape.
     */
    private static List<Token> blockRecovery(final List<Token> spoken, final OverlayCall call, final int start) {
        final List<Token> named = new ArrayList<>();
        final int close = call.getClose();
        if (spoken.get(start - 1).getType() == FrostlakeLexer.LPAREN) {
            final int opener = spoken.get(start + 1).getType();
            if (call.getOpen() != start + 4) {
                return null;
            }
            if (opener == FrostlakeLexer.IDENTIFIER && close + 1 < spoken.size()
                    && spoken.get(close + 1).getType() == FrostlakeLexer.RPAREN) {
                final int query = matchingClose(spoken, start - 1);
                if (query < 0) {
                    return null;
                }
                named.add(spoken.get(close + 1));
                named.add(spoken.get(query));
                return named;
            }
            if ((opener != FrostlakeLexer.CAST && opener != FrostlakeLexer.TRY_CAST)
                    || close + 3 >= spoken.size() || spoken.get(close + 1).getType() != FrostlakeLexer.AS
                    || spoken.get(close + 3).getType() != FrostlakeLexer.RPAREN) {
                return null;
            }
            named.add(spoken.get(close + 1));
            named.add(spoken.get(close + 3));
            return named;
        }
        for (int i = close + 1; i + 1 < spoken.size(); i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                int at = i + 1;
                while (spoken.get(at).getType() == FrostlakeLexer.END && at + 2 < spoken.size()
                        && spoken.get(at + 1).getType() == FrostlakeLexer.SEMI
                        && spoken.get(at + 2).getType() == FrostlakeLexer.END) {
                    at += 2;
                }
                if (spoken.get(at).getType() == Token.EOF) {
                    return null;
                }
                named.add(spoken.get(at));
                return named;
            }
        }
        return null;
    }

    /** The bracket closing the one opened at {@code open}, or -1 where none does. */
    private static int matchingClose(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The tokens named after the PLACING, read from the brackets around the call of the select item a SELECT at
     * {@code start} lists; null for a shape not measured.
     */
    private static List<Token> stackedLines(final List<Token> spoken, final OverlayCall call, final int start) {
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
            if (start == 0 && call.isSimple() && call.getFor() < 0 && left == 4
                    && spoken.get(0).getType() == FrostlakeLexer.SELECT
                    && spoken.get(1).getType() == FrostlakeLexer.CASE && spoken.get(2).getType() == FrostlakeLexer.WHEN
                    && spoken.get(left).getType() == FrostlakeLexer.THEN && right + 1 < spoken.size()
                    && spoken.get(right).getType() == FrostlakeLexer.END
                    && spoken.get(right + 1).getType() == FrostlakeLexer.FROM) {
                named.add(spoken.get(call.getValue()));
                return named;
            }
            return null;
        }
        if (!afterOneTokenItems(spoken, left, start) || !endsAtFrom(spoken, right)
                || !call.isSimple() && !"F".equals(kinds) && !"P".equals(kinds)
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
                case "FFFF":
                    named.add(closes.get(2));
                    break;
                case "FFC":
                    named.add(closes.get(0));
                    named.add(closes.get(2));
                    break;
                case "FFCF":
                    named.add(closes.get(0));
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

    /** Whether the item begins the select list, or follows items of one token each, each with its comma. */
    private static boolean afterOneTokenItems(final List<Token> spoken, final int left, final int start) {
        if (spoken.get(start).getType() != FrostlakeLexer.SELECT || (left - start) % 2 != 0) {
            return false;
        }
        for (int i = start + 1; i <= left; i++) {
            final int type = spoken.get(i).getType();
            final boolean comma = (i - start) % 2 == 0;
            if (comma ? type != FrostlakeLexer.COMMA
                    : type == FrostlakeLexer.COMMA || type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN) {
                return false;
            }
        }
        return true;
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
