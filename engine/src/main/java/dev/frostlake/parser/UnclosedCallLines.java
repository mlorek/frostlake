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
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports for a call left open with nothing inside it at the very end of the input, {@code ABS(}. The
 * first is always '&lt;EOF&gt;' at the input's end; what follows depends on where the call stands (all live-verified):
 *
 * <pre>
 *   SELECT a FROM t WHERE ABS(               '&lt;EOF&gt;' twice      in a WHERE, HAVING, QUALIFY, ORDER BY or GROUP BY
 *   SELECT UPPER(ABS(                        '&lt;EOF&gt;' twice      as another call's argument
 *   MERGE INTO t USING t s ON ABS(           '&lt;EOF&gt;' twice      in a MERGE's join condition
 *   SELECT (ABS(                             '&lt;EOF&gt;', then '('  in a bracket of a statement's select list
 *   SELECT * FROM (SELECT ABS(               '&lt;EOF&gt;', then '('  in a derived table's select list
 *   WITH c AS (SELECT ABS(                   '&lt;EOF&gt;', '(', '&lt;EOF&gt;'  in a CTE's select list
 * </pre>
 *
 * <p>A call standing in a statement's select list itself, {@code SELECT ABS(}, is the '&lt;EOF&gt;' line and the '(' line,
 * which the listener already reports. A call holding an argument, {@code ABS(1}, is one line everywhere. The place is
 * read off the tokens, because the parse that ran out of input has given its tree up.
 */
final class UnclosedCallLines {

    /** The lines are the end-of-input line twice. */
    private static final int REPEATED = 1;
    /** The lines are the end-of-input line, then the call's own parenthesis. */
    private static final int OPENER = 2;
    /** The lines are the end-of-input line, the call's parenthesis, and the end-of-input line again. */
    private static final int OPENER_REPEATED = 3;
    private static final int UNKNOWN = 0;

    private UnclosedCallLines() {
    }

    /**
     * The lines live reports when the input ends in an empty call, or null when it does not or the place is not one of
     * the measured ones.
     *
     * @param spoken the default-channel tokens of the input, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken) {
        final int size = spoken.size();
        if (size < 4 || spoken.get(size - 1).getType() != Token.EOF
                || spoken.get(size - 2).getType() != FrostlakeLexer.LPAREN || !isName(spoken.get(size - 3))) {
            return null;
        }
        int nameStart = size - 3;
        while (nameStart >= 2 && spoken.get(nameStart - 1).getType() == FrostlakeLexer.DOT
                && isName(spoken.get(nameStart - 2))) {
            nameStart -= 2;
        }
        if (nameStart > 0 && (endsAValue(spoken.get(nameStart - 1))
                || spoken.get(nameStart - 1).getType() == FrostlakeLexer.AS
                || spoken.get(nameStart - 1).getType() == FrostlakeLexer.DOUBLE_COLON)) {
            // A word after a finished value, or after AS, is an alias or a clause of its own — OVER (, ESCAPE ( —
            // and one after '::' is a type: none of them is a call.
            return null;
        }
        final int kind = placeOf(spoken, nameStart);
        if (kind == UNKNOWN) {
            return null;
        }
        final String endOfInput = SyntaxErrorListener.sentence(spoken.get(size - 1));
        final List<String> lines = new ArrayList<>();
        lines.add(endOfInput);
        if (kind == REPEATED) {
            lines.add(endOfInput);
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(size - 2)));
        if (kind == OPENER_REPEATED) {
            lines.add(endOfInput);
        }
        return lines;
    }

    /**
     * The one line live reports for a CAST or TRY_CAST with nothing inside its parenthesis — the token after the '(',
     * where this parser named the keyword: {@code SELECT CAST(} is '&lt;EOF&gt;' at 12 and {@code SELECT CAST()} is ')' at
     * 12 (live-verified) — or null when the named token is no such keyword.
     *
     * @param spoken     the default-channel tokens of the input, end of input included
     * @param namedIndex the token index of the token the parse's one line names
     * @return the line, or null
     */
    static String emptyCast(final List<Token> spoken, final int namedIndex) {
        for (int i = 0; i + 2 < spoken.size(); i++) {
            final Token keyword = spoken.get(i);
            if (keyword.getTokenIndex() != namedIndex) {
                continue;
            }
            final int after = spoken.get(i + 2).getType();
            if ((keyword.getType() == FrostlakeLexer.CAST || keyword.getType() == FrostlakeLexer.TRY_CAST)
                    && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN
                    && (after == Token.EOF || after == FrostlakeLexer.RPAREN)) {
                return SyntaxErrorListener.sentence(spoken.get(i + 2));
            }
            return null;
        }
        return null;
    }

    /** Where the call starting at {@code index} stands, read back through the tokens before it. */
    private static int placeOf(final List<Token> spoken, final int index) {
        int depth = 0;
        for (int j = index - 1; j >= 0; j--) {
            final Token token = spoken.get(j);
            final int type = token.getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (j > 0 && isName(spoken.get(j - 1))) {
                    return REPEATED;
                }
            } else if (depth == 0 && (type == FrostlakeLexer.WHERE || type == FrostlakeLexer.HAVING
                    || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.GROUP)) {
                return REPEATED;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return selectListPlace(spoken, j);
            } else if (depth == 0 && type == FrostlakeLexer.ON) {
                return mergeCondition(spoken, j) ? REPEATED : UNKNOWN;
            } else if (depth == 0 && (type == FrostlakeLexer.FROM || type == FrostlakeLexer.SEMI
                    || type == FrostlakeLexer.VALUES)) {
                return UNKNOWN;
            }
        }
        return UNKNOWN;
    }

    /** Whether the ON at {@code on} is a MERGE's join condition, and not a join's. */
    private static boolean mergeCondition(final List<Token> spoken, final int on) {
        int depth = 0;
        for (int j = on - 1; j >= 0; j--) {
            final int type = spoken.get(j).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.MERGE) {
                return true;
            } else if (depth == 0 && (type == FrostlakeLexer.JOIN || type == FrostlakeLexer.SEMI)) {
                return false;
            }
        }
        return false;
    }

    /** The lines' kind for a call in the select list the SELECT at {@code select} opens. */
    private static int selectListPlace(final List<Token> spoken, final int select) {
        int depth = 0;
        for (int j = select - 1; j >= 0; j--) {
            final int type = spoken.get(j).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                    continue;
                }
                if (j + 1 != select || j == 0) {
                    return UNKNOWN;
                }
                final int before = spoken.get(j - 1).getType();
                if (before == FrostlakeLexer.AS) {
                    return OPENER_REPEATED;
                }
                return before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN ? OPENER : UNKNOWN;
            } else if (depth == 0 && type == FrostlakeLexer.SEMI) {
                break;
            }
        }
        return OPENER;
    }

    /** Whether a token may be a name. */
    private static boolean isName(final Token token) {
        final ATN atn = FrostlakeParser._ATN;
        final IntervalSet names = atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]);
        return names.contains(token.getType());
    }

    /** Whether a token may end a value: a closing parenthesis, a literal or a name. */
    private static boolean endsAValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.STRING_LITERAL
            || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL || isName(token);
    }
}
