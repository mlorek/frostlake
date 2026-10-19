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
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines the account reports when a word is run into a value inside a bracket of a select list, for the
 * brackets {@link BracketedValueAlias} does not read: the word, and what the bracket's kind adds (all
 * live-verified, each for a statement that is one SELECT):
 *
 * <pre>
 *   SELECT 1 IN (1 x y)                     'x', then 'y'
 *   SELECT 1 IN (1 x, 2)                    'x', then the list's ')'
 *   SELECT CASE WHEN 1 = 1 THEN (1 x y) END 'x', then 'y' — and so after ELSE
 *   SELECT CASE WHEN (1 x y) THEN 1 END     'x', 'y', then the token after it
 *   SELECT CASE (1 x y) WHEN 1 THEN 1 END   'x', then the bracket's ')'
 *   SELECT (1 x = y)                        'x', then '=' — and so '==' and IN
 *   SELECT (1 x))                           'x' alone: a surplus ')' adds nothing
 *   SELECT (1 x)(y)                         'x', 'y', then the token after it
 *   SELECT * EXCLUDE (a x y) FROM t         'x' alone; before a comma, 'x' and the list's ')'
 *   SELECT * REPLACE (1 x y) FROM t         'x', then 'y' — and so in a RENAME
 *   SELECT COUNT(DISTINCT a x y) FROM t     'x' twice: the argument is read again from its name
 *   SELECT COUNT(DISTINCT 1 x y)            'x', then 'y'
 *   SELECT (SELECT (1 x y))                 the inner 'SELECT', '1', then 'y'
 *   SELECT (SELECT (a x y) FROM t)          the inner 'SELECT', 'a', then 'x'
 * </pre>
 */
final class SelectBracketWordLines {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private SelectBracketWordLines() {
    }

    /**
     * The lines of a text that is one SELECT whose first fault is such a word, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        if (faultIndex < 0 || spoken.size() < 6 || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final int end = spoken.size() - 1;
        int fault = -1;
        final List<Integer> open = new ArrayList<>();
        for (int i = 1; i < end && fault < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI || open.isEmpty() && type == FrostlakeLexer.FROM) {
                return null;
            }
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            } else if (type == FrostlakeLexer.LPAREN) {
                open.add(Integer.valueOf(i));
            } else if (type == FrostlakeLexer.RPAREN) {
                if (open.isEmpty()) {
                    return null;
                }
                open.remove(open.size() - 1);
            }
        }
        for (int i = Math.max(fault, 1); i < end; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                return null;
            }
        }
        if (fault >= 2 && fault + 4 <= end && spoken.get(fault).getType() == FrostlakeLexer.SELECT
                && spoken.get(fault - 1).getType() == FrostlakeLexer.LPAREN
                && spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN) {
            return inScalarSubquery(spoken, fault, end);
        }
        if (fault < 3 || open.isEmpty() || !isName(spoken.get(fault).getType())
                || !endsOperand(spoken.get(fault - 1).getType())) {
            return null;
        }
        final int faulted = spoken.get(fault).getType();
        if ((faulted == FrostlakeLexer.IGNORE || faulted == FrostlakeLexer.RESPECT)
                && spoken.get(fault + 1).getType() == FrostlakeLexer.NULLS) {
            // A null treatment inside a call's parentheses has lines of its own.
            return null;
        }
        for (int i = open.size() - 1; i >= 1; i--) {
            // A bracket that opens a scalar subquery's first item, reached through plain brackets alone.
            final int at = open.get(i).intValue();
            if (spoken.get(at - 1).getType() == FrostlakeLexer.SELECT && open.get(i - 1).intValue() == at - 2) {
                return inScalarSubquery(spoken, at - 1, end);
            }
            if (spoken.get(at - 1).getType() != FrostlakeLexer.LPAREN) {
                break;
            }
        }
        final Token word = spoken.get(fault);
        final Token next = spoken.get(fault + 1);
        final int inner = open.get(open.size() - 1).intValue();
        final int before = spoken.get(inner - 1).getType();
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(word));
        if (before == FrostlakeLexer.EXCLUDE) {
            return next.getType() == FrostlakeLexer.COMMA ? with(lines, closerOf(spoken, inner, end)) : lines;
        }
        // REPLACE names a function too: the star's own modifier follows the star, or another modifier's list.
        final int earlier = inner >= 2 ? spoken.get(inner - 2).getType() : Token.EOF;
        if ((before == FrostlakeLexer.REPLACE || before == FrostlakeLexer.RENAME)
                && (earlier == FrostlakeLexer.STAR || earlier == FrostlakeLexer.RPAREN)) {
            return isName(next.getType()) ? with(lines, next) : lines;
        }
        if (before == FrostlakeLexer.THEN || before == FrostlakeLexer.ELSE) {
            return isName(next.getType()) ? with(lines, next) : null;
        }
        if (before == FrostlakeLexer.WHEN) {
            return isName(next.getType()) ? with(with(lines, next), spoken.get(fault + 2)) : null;
        }
        if (before == FrostlakeLexer.CASE) {
            return isName(next.getType()) ? with(lines, closerOf(spoken, inner, end)) : null;
        }
        final int list = listOf(spoken, open);
        if (list >= 0) {
            if (isName(next.getType())) {
                return with(lines, next);
            }
            return next.getType() == FrostlakeLexer.COMMA ? with(lines, closerOf(spoken, list, end)) : null;
        }
        if (isName(before) && open.size() == 1 && fault == inner + 3
                && (spoken.get(inner + 1).getType() == FrostlakeLexer.DISTINCT
                    || spoken.get(inner + 1).getType() == FrostlakeLexer.ALL)) {
            // A call's argument after its quantifier: one that is a name is read again, as a named argument is.
            if (isName(spoken.get(inner + 2).getType())) {
                return with(lines, word);
            }
            return isName(next.getType()) ? with(lines, next) : null;
        }
        if (!plain(spoken, open)) {
            return null;
        }
        final int after = next.getType();
        if (after == FrostlakeLexer.EQ || after == FrostlakeLexer.EQ_EQ || after == FrostlakeLexer.IN) {
            return with(lines, next);
        }
        if (after != FrostlakeLexer.RPAREN) {
            return null;
        }
        // The word stands before the bracket's close: a surplus ')' after the brackets adds nothing, and a bracket
        // after them names its first word and the token after that.
        int close = fault + 1;
        int closed = 0;
        while (close < end && spoken.get(close).getType() == FrostlakeLexer.RPAREN) {
            close++;
            closed++;
        }
        if (closed > open.size()) {
            return lines;
        }
        if (closed == open.size() && open.size() == 1 && close + 2 <= end
                && spoken.get(close).getType() == FrostlakeLexer.LPAREN && isName(spoken.get(close + 1).getType())) {
            final int then = spoken.get(close + 2).getType();
            if (then == FrostlakeLexer.RPAREN || isName(then)) {
                return with(with(lines, spoken.get(close + 1)), spoken.get(close + 2));
            }
        }
        return null;
    }

    /**
     * A scalar subquery whose first item opens with a bracket a word is run into: the subquery's SELECT, the
     * bracket's first token, and then the word itself after a name, or the token after the word after any other
     * value.
     */
    private static List<String> inScalarSubquery(final List<Token> spoken, final int select, final int end) {
        final int bracket = select + 1;
        for (int i = bracket + 2; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.FROM) {
                return null;
            }
            if (isName(type) && endsOperand(spoken.get(i - 1).getType())) {
                final List<String> lines = new ArrayList<>();
                lines.add(SyntaxErrorListener.sentence(spoken.get(select)));
                lines.add(SyntaxErrorListener.sentence(spoken.get(bracket + 1)));
                return with(lines, isName(spoken.get(i - 1).getType()) ? spoken.get(i) : spoken.get(i + 1));
            }
        }
        return null;
    }

    private static List<String> with(final List<String> lines, final Token token) {
        if (token != null) {
            lines.add(SyntaxErrorListener.sentence(token));
        }
        return lines;
    }

    /**
     * The bracket of the IN list the fault stands in — the innermost open bracket, or one that holds it through
     * plain parentheses alone — or -1.
     */
    private static int listOf(final List<Token> spoken, final List<Integer> open) {
        for (int i = open.size() - 1; i >= 0; i--) {
            final int at = open.get(i).intValue();
            final int before = spoken.get(at - 1).getType();
            if (before == FrostlakeLexer.IN) {
                // The list is a select item's own: nothing but the list's brackets is open around it.
                return i == 0 ? at : -1;
            }
            if (before != FrostlakeLexer.LPAREN) {
                return -1;
            }
        }
        return -1;
    }

    /** Whether every open bracket is a plain one of the select list: after SELECT, a comma or another of them. */
    private static boolean plain(final List<Token> spoken, final List<Integer> open) {
        for (final Integer at : open) {
            final int before = spoken.get(at.intValue() - 1).getType();
            if (before != FrostlakeLexer.SELECT && before != FrostlakeLexer.COMMA && before != FrostlakeLexer.LPAREN) {
                return false;
            }
        }
        return true;
    }

    /** The ')' that closes the bracket at {@code open}, or null. */
    private static Token closerOf(final List<Token> spoken, final int open, final int end) {
        int depth = 0;
        for (int i = open; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return spoken.get(i);
            }
        }
        return null;
    }

    private static boolean isName(final int type) {
        return WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /** Whether a token can end the value a word is run into: a name, a literal or a closing bracket. */
    private static boolean endsOperand(final int type) {
        return isName(type) || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.TRUE || type == FrostlakeLexer.FALSE
            || type == FrostlakeLexer.NULL || type == FrostlakeLexer.RPAREN;
    }
}
