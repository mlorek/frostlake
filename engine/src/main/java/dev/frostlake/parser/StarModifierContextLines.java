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

/**
 * The lines the account reports past a star modifier's keyword, {@code HASH(* REPLACE …)} or {@code RENAME}, where the
 * call stands as the condition or the operand of a CASE that is a query's first item, in a subquery compared by
 * {@code =} or as an EXISTS query's item. Each place reads the modifier's remainder its own way; over {@code fz (id
 * INT, b BOOLEAN)} (all live-verified, the keyword's own line first, a compared subquery's two lines before it):
 *
 * <pre>
 *   remainder          CASE condition       CASE operand         compared subquery    EXISTS item
 *   (none)             -                    -                    the call's ')'       -
 *   x                  'x', the call's ')'  the call's ')'       'x'                  'x'
 *   x y                'x', 'y'             'y'                  'x'                  'x'
 *   id AS k            'id', 'AS'           'AS'                 'id'                 'id'
 *   (1 AS id)          'AS', its ')'        '1', its ')'         'AS', what follows   '('
 *   (1 AS id), 1       'AS', its ')'        '1', its ')'         'AS', the ','        '('
 *   (id)               the call's ')'       'id', its ')'        the call's ')'       '('
 * </pre>
 *
 * A CASE condition whose {@code (id)} the THEN does not follow at once adds the THEN.
 *
 * <p>Two more places read a remainder of aliased values, {@code (1 AS id)}, {@code ((1 + 2) AS id)} or
 * {@code (id AS x)}, past the same two lines before the keyword: a subquery quantified by any comparison and ALL, ANY
 * or SOME, whose second line is the star before the keyword, and a subquery on the left of a WHERE's comparison (all
 * live-verified). The lines past the keyword are each AS, then the call's {@code )}. Any other remainder there keeps
 * the lines worked out so far.
 */
final class StarModifierContextLines {

    private StarModifierContextLines() {
    }

    /**
     * The lines for a statement whose first fault is a star modifier's keyword in one of the places above, or the
     * lines worked out so far for any other.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.isEmpty()) {
            return lines;
        }
        final int fault = spokenIndex(spoken, faults.get(0).intValue());
        if (fault < 2) {
            return lines;
        }
        if (!isModifier(spoken, fault)) {
            return lines;
        }
        // A subquery compared by '=' is told at its SELECT and the name after it first; the lines past the keyword
        // follow those two.
        final int open = openBefore(spoken, fault - 2);
        if (open >= 1 && spoken.get(open - 1).getType() == FrostlakeLexer.EQ
                && spoken.get(open + 1).getType() == FrostlakeLexer.SELECT && lines.size() >= 2
                && lines.get(0).equals(SyntaxErrorListener.sentence(spoken.get(open + 1)))) {
            final List<Integer> after = compared(spoken, fault);
            return after == null ? lines : joined(lines.subList(0, 2), spoken, after);
        }
        if (open >= 2 && spoken.get(open + 1).getType() == FrostlakeLexer.SELECT && lines.size() >= 2
                && lines.get(0).equals(SyntaxErrorListener.sentence(spoken.get(open + 1)))) {
            final int quantifier = spoken.get(open - 1).getType();
            final boolean quantified = (quantifier == FrostlakeLexer.ALL || quantifier == FrostlakeLexer.ANY
                || quantifier == FrostlakeLexer.SOME) && isComparison(spoken.get(open - 2).getType());
            final int close = closing(spoken, open);
            final boolean leftOfComparison = spoken.get(open - 1).getType() == FrostlakeLexer.WHERE
                && close + 1 < spoken.size() && isComparison(spoken.get(close + 1).getType());
            if (quantified || leftOfComparison) {
                return aliasedPastSubquery(spoken, fault, lines, quantified);
            }
        }
        // A CASE is read so as a query's first item; in a clause, a WHERE's, it is read another way.
        final List<Integer> after;
        if (fault >= 6 && spoken.get(fault - 4).getType() == FrostlakeLexer.WHEN
                && spoken.get(fault - 5).getType() == FrostlakeLexer.CASE
                && spoken.get(fault - 6).getType() == FrostlakeLexer.SELECT) {
            after = condition(spoken, fault);
        } else if (fault >= 5 && spoken.get(fault - 4).getType() == FrostlakeLexer.CASE
                && spoken.get(fault - 5).getType() == FrostlakeLexer.SELECT) {
            after = operand(spoken, fault);
        } else if (fault >= 6 && spoken.get(fault - 4).getType() == FrostlakeLexer.SELECT
                && spoken.get(fault - 5).getType() == FrostlakeLexer.LPAREN
                && spoken.get(fault - 6).getType() == FrostlakeLexer.EXISTS) {
            after = existsItem(spoken, fault);
        } else {
            after = null;
        }
        if (after == null) {
            return lines;
        }
        final List<String> told = new ArrayList<String>();
        told.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        return joined(told, spoken, after);
    }

    /** The lines past the keyword of a CASE's condition. */
    private static List<Integer> condition(final List<Token> spoken, final int keyword) {
        final StarModifierShape shape = StarModifierShape.of(spoken, keyword);
        if (shape == null) {
            return null;
        }
        final List<Integer> after = new ArrayList<Integer>();
        if (shape.kind == StarModifierRemainder.WORDS) {
            after.add(Integer.valueOf(keyword + 1));
            after.add(Integer.valueOf(keyword + 2));
        } else if (shape.kind == StarModifierRemainder.ALIASED) {
            after.add(Integer.valueOf(keyword + 3));
            after.add(Integer.valueOf(shape.group));
        } else if (shape.kind == StarModifierRemainder.NAMED) {
            after.add(Integer.valueOf(shape.group + 1));
            if (shape.group + 2 < spoken.size() && spoken.get(shape.group + 2).getType() != FrostlakeLexer.THEN) {
                final int then = next(spoken, shape.group + 2, FrostlakeLexer.THEN);
                if (then >= 0) {
                    after.add(Integer.valueOf(then));
                }
            }
        }
        return after;
    }

    /** The lines past the keyword of a CASE's operand. */
    private static List<Integer> operand(final List<Token> spoken, final int keyword) {
        final StarModifierShape shape = StarModifierShape.of(spoken, keyword);
        if (shape == null) {
            return null;
        }
        final List<Integer> after = new ArrayList<Integer>();
        if (shape.kind == StarModifierRemainder.WORDS) {
            after.add(Integer.valueOf(keyword + 2));
        } else if (shape.kind == StarModifierRemainder.ALIASED || shape.kind == StarModifierRemainder.NAMED) {
            after.add(Integer.valueOf(keyword + 2));
            after.add(Integer.valueOf(shape.group));
        }
        return after;
    }

    /** The lines past a compared subquery's first two, for its call's keyword. */
    private static List<Integer> compared(final List<Token> spoken, final int keyword) {
        final StarModifierShape shape = StarModifierShape.of(spoken, keyword);
        if (shape == null) {
            return null;
        }
        final List<Integer> after = new ArrayList<Integer>();
        if (shape.kind == StarModifierRemainder.BARE || shape.kind == StarModifierRemainder.WORDS) {
            after.add(Integer.valueOf(keyword + 1));
        } else if (shape.kind == StarModifierRemainder.ALIASED) {
            after.add(Integer.valueOf(keyword + 3));
            after.add(Integer.valueOf(shape.group + 1));
        } else {
            after.add(Integer.valueOf(shape.group + 1));
        }
        return after;
    }

    /**
     * The lines of a subquery quantified by {@code > ALL} or standing left of {@code =}, for its call's keyword: the
     * SELECT, the star before the keyword when quantified or the second line worked out so far, then a bracketed value
     * with an alias read as a compared subquery reads it. Any other remainder keeps the lines worked out so far.
     */
    private static List<String> aliasedPastSubquery(final List<Token> spoken, final int keyword,
                                                    final List<String> lines, final boolean quantified) {
        final List<Integer> after = aliasedRemainder(spoken, keyword);
        if (after == null) {
            return lines;
        }
        final List<String> told = new ArrayList<String>();
        told.add(lines.get(0));
        told.add(quantified ? SyntaxErrorListener.sentence(spoken.get(keyword - 1)) : lines.get(1));
        return joined(told, spoken, after);
    }

    /**
     * The lines past a keyword whose remainder aliases values, each a word or a bracketed group with AS and a name
     * after it: each AS, then the call's {@code )}; null for any other remainder.
     */
    private static List<Integer> aliasedRemainder(final List<Token> spoken, final int keyword) {
        if (keyword + 1 >= spoken.size() || spoken.get(keyword + 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final int group = closing(spoken, keyword + 1);
        if (group < 0 || group + 1 >= spoken.size()) {
            return null;
        }
        final List<Integer> aliases = new ArrayList<Integer>();
        int at = keyword + 2;
        while (at < group) {
            final int valueEnd = spoken.get(at).getType() == FrostlakeLexer.LPAREN ? closing(spoken, at) : at;
            if (valueEnd < 0 || valueEnd + 2 >= group || spoken.get(valueEnd + 1).getType() != FrostlakeLexer.AS
                    || !SyntaxErrorListener.isNameLike(spoken.get(valueEnd + 2))) {
                return null;
            }
            aliases.add(Integer.valueOf(valueEnd + 1));
            at = valueEnd + 3;
            if (at < group) {
                if (spoken.get(at).getType() != FrostlakeLexer.COMMA) {
                    return null;
                }
                at++;
            }
        }
        if (aliases.isEmpty()) {
            return null;
        }
        final List<Integer> after = new ArrayList<Integer>(aliases);
        after.add(Integer.valueOf(group + 1));
        return after;
    }

    /** Whether a token type is a comparison's operator. */
    private static boolean isComparison(final int type) {
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.NEQ || type == FrostlakeLexer.LT
            || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT || type == FrostlakeLexer.GTE;
    }

    /** The lines past the keyword of an EXISTS query's item. */
    private static List<Integer> existsItem(final List<Token> spoken, final int keyword) {
        final StarModifierShape shape = StarModifierShape.of(spoken, keyword);
        if (shape == null) {
            return null;
        }
        final List<Integer> after = new ArrayList<Integer>();
        if (shape.kind != StarModifierRemainder.BARE) {
            after.add(Integer.valueOf(keyword + 1));
        }
        return after;
    }

    private static List<String> joined(final List<String> first, final List<Token> spoken, final List<Integer> after) {
        final List<String> joined = new ArrayList<String>(first);
        for (final Integer at : after) {
            joined.add(SyntaxErrorListener.sentence(spoken.get(at.intValue())));
        }
        return joined;
    }

    /** Whether the token at an index is a star modifier's keyword right after a call's {@code (*}. */
    static boolean isModifier(final List<Token> spoken, final int at) {
        final int type = spoken.get(at).getType();
        return at >= 3 && (type == FrostlakeLexer.REPLACE || type == FrostlakeLexer.RENAME)
            && spoken.get(at - 1).getType() == FrostlakeLexer.STAR
            && spoken.get(at - 2).getType() == FrostlakeLexer.LPAREN
            && SyntaxErrorListener.isNameLike(spoken.get(at - 3));
    }

    /** The nearest '(' before an index that is left open there, or -1. */
    private static int openBefore(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the next token of a type from an index on, or -1. */
    private static int next(final List<Token> spoken, final int from, final int type) {
        for (int i = from; i < spoken.size(); i++) {
            if (spoken.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the bracket that closes the one at an index, or the end of input's when none does. */
    static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return spoken.size() - 1;
    }

    /** The index in {@code spoken} of the token with the stream index {@code tokenIndex}; -1 for the end of input. */
    private static int spokenIndex(final List<Token> spoken, final int tokenIndex) {
        if (tokenIndex < 0) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }
}
