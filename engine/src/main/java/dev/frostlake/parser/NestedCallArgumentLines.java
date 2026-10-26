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
 * The lines the account reports for a stray word after an argument of a call that stands inside another call of a
 * select item (all live-verified). What it stacks after the word depends on how the argument opens:
 *
 * <pre>
 *   SELECT ABS(ABS(x y)) FROM T             'y', then the outermost call's ')'
 *   SELECT IFF(ABS(x y) &gt; 0, 1, 2) FROM T   'y', then IFF's ')'
 *   SELECT CAST(ABS(x y) AS INT) FROM T     'y', then the CAST's ')'
 *   SELECT ABS(ABS(x y z)) FROM T           'y' alone: a second stray word ends the report
 *   SELECT ABS(ABS(T.x y)) FROM T           'y', then 'y' again
 *   SELECT ABS(ABS(ABS(T.x y))) FROM T      'y', then the ')' of the call inside the outermost
 *   SELECT ABS(ABS(1 y)) FROM T             'y' alone: no name opens the argument
 *   SELECT ABS(ABS(1 x y)) FROM T           'x', then the outermost call's ')'
 *   SELECT ABS(ABS((1 x y)))                'x', then the outermost call's ')': a bracket around it is read through
 *   SELECT ABS(ABS(x) y) FROM T             'y' alone: the argument is a call
 *   SELECT x FROM T WHERE ABS(1 y) = 1      'y' alone, in a WHERE too
 *   SELECT ABS((x y)) FROM T                'y', then 'x' and 'y': a bracketed name is read again from the name
 *   SELECT ABS(ABS(T.x.)) FROM T            the ')' after the trailing dot, then the dot
 * </pre>
 *
 * <p>A name written with its relation reads one call shallower than a bare one: directly in a select item's call it
 * stacks its dot, one call deeper the word again, and deeper still the closing parenthesis a bare name would have
 * stacked with the outermost call left out. Nothing after these lines is reported. A null treatment written
 * inside the parentheses and a type's parameter list are other constructs, with lines of their own.
 */
final class NestedCallArgumentLines {

    private NestedCallArgumentLines() {
    }

    /**
     * The lines for a text whose first fault is such a stray word, or null when the fault is another.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 4 || fault >= spoken.size() - 1 || spoken.get(0).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        if (fault == 5 && StarModifierResync.isName(spoken.get(1)) && spoken.get(2).getType() == FrostlakeLexer.LPAREN
                && spoken.get(3).getType() == FrostlakeLexer.LPAREN && StarModifierResync.isName(spoken.get(4))
                && StarModifierResync.isName(spoken.get(fault)) && fault + 2 < spoken.size()
                && spoken.get(fault + 1).getType() == FrostlakeLexer.RPAREN
                && spoken.get(fault + 2).getType() == FrostlakeLexer.RPAREN) {
            // A name in a bracket that is the first item's call's whole argument: the word, then the bracket read
            // again from its name.
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(4)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
            return lines;
        }
        if (spoken.get(fault).getType() == FrostlakeLexer.RPAREN) {
            return trailingDot(spoken, fault);
        }
        if (!StarModifierResync.isName(spoken.get(fault)) || nullTreatment(spoken, fault)) {
            return null;
        }
        // The brackets open at the word, each a call's or a CAST's, and the clause the outermost stands in. A plain
        // bracket right inside a call around a literal argument is read through: the outermost call's ')' is still
        // the one refused.
        final List<Integer> open = new ArrayList<>();
        boolean filter = false;
        boolean bracketed = false;
        for (int i = 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                final Token before = spoken.get(i - 1);
                if (before.getType() == FrostlakeLexer.LPAREN && i == fault - 2 && !open.isEmpty()
                        && open.get(open.size() - 1).intValue() == i - 1 && isLiteral(spoken.get(i + 1))) {
                    if (CastFaultPlace.isCast(spoken, i - 2)) {
                        // A CAST's bracketed operand is a construct of its own (BracketedLiteralLines).
                        return null;
                    }
                    bracketed = true;
                    continue;
                }
                if (!StarModifierResync.isName(before) && before.getType() != FrostlakeLexer.CAST
                        || typeNamed(spoken, i - 1)) {
                    return null;
                }
                open.add(Integer.valueOf(i));
            } else if (type == FrostlakeLexer.RPAREN) {
                if (open.isEmpty()) {
                    return null;
                }
                open.remove(open.size() - 1);
            } else if (open.isEmpty() && type == FrostlakeLexer.WHERE) {
                filter = true;
            } else if (open.isEmpty() && (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.GROUP
                    || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
                    || type == FrostlakeLexer.JOIN || type == FrostlakeLexer.ON || filter && type == FrostlakeLexer.FROM)) {
                return null;
            }
        }
        if (open.isEmpty()) {
            return null;
        }
        final int innermost = open.get(open.size() - 1).intValue();
        int argument = innermost + 1;
        int depth = 0;
        for (int i = innermost + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (type == FrostlakeLexer.COMMA && depth == 0) {
                argument = i + 1;
            }
        }
        if (bracketed) {
            argument = fault - 1;
        }
        if (argument >= fault) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        final boolean literal = isLiteral(spoken.get(argument));
        final boolean closed = spoken.get(fault - 1).getType() == FrostlakeLexer.RPAREN;
        if (literal && argument == fault - 1 && (open.size() >= 2 || filter) || closed && !filter && !literal) {
            // No name opens the argument: the word is refused, and nothing after it — but for a second word that
            // ends the argument, after which the outermost call's parenthesis is refused as after a name.
            if (literal && !filter && open.size() >= 2 && StarModifierResync.isName(spoken.get(fault + 1))
                    && fault + 2 < spoken.size() && spoken.get(fault + 2).getType() == FrostlakeLexer.RPAREN) {
                add(lines, closerOf(spoken, fault, open.size() + (bracketed ? 1 : 0), 0));
            }
            return lines;
        }
        if (filter || open.size() < 2 || bracketed) {
            return null;
        }
        final boolean bare = argument == fault - 1 && StarModifierResync.isName(spoken.get(argument));
        final boolean qualified = argument == fault - 3 && StarModifierResync.isName(spoken.get(argument))
            && spoken.get(argument + 1).getType() == FrostlakeLexer.DOT
            && StarModifierResync.isName(spoken.get(argument + 2));
        if (bare) {
            if (spoken.get(fault + 1).getType() == FrostlakeLexer.RPAREN) {
                add(lines, closerOf(spoken, fault, open.size(), 0));
            }
            return lines;
        }
        if (!qualified) {
            return null;
        }
        if (open.size() == 2) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        } else if (spoken.get(fault + 1).getType() == FrostlakeLexer.RPAREN) {
            add(lines, closerOf(spoken, fault, open.size(), 1));
        }
        return lines;
    }

    /**
     * A qualified name ending in a dot as the argument of a call inside another call of the first select item: the
     * ')' after the dot, then the dot.
     */
    private static List<String> trailingDot(final List<Token> spoken, final int fault) {
        if (fault < 7 || spoken.get(fault - 1).getType() != FrostlakeLexer.DOT
                || !StarModifierResync.isName(spoken.get(fault - 2))
                || spoken.get(fault - 3).getType() != FrostlakeLexer.DOT
                || !StarModifierResync.isName(spoken.get(fault - 4))
                || spoken.get(fault - 5).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        int calls = 0;
        for (int i = 1; i < fault - 4; i += 2) {
            if (!StarModifierResync.isName(spoken.get(i)) || spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN) {
                return null;
            }
            calls++;
        }
        if (calls < 2) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault - 1)));
        return lines;
    }

    private static boolean isLiteral(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    /** Whether the word opens a null treatment, whose lines are that construct's own. */
    private static boolean nullTreatment(final List<Token> spoken, final int at) {
        final int type = spoken.get(at).getType();
        return (type == FrostlakeLexer.IGNORE || type == FrostlakeLexer.RESPECT)
            && spoken.get(at + 1).getType() == FrostlakeLexer.NULLS;
    }

    /** Whether the name at {@code at} is a type's: the bracket after it holds the type's parameters, no argument. */
    private static boolean typeNamed(final List<Token> spoken, final int at) {
        final int before = spoken.get(at - 1).getType();
        return before == FrostlakeLexer.AS || before == FrostlakeLexer.DOUBLE_COLON;
    }

    private static void add(final List<String> lines, final Token closer) {
        if (closer != null) {
            lines.add(SyntaxErrorListener.sentence(closer));
        }
    }

    /** The ')' that closes the bracket {@code level} levels in from the outermost of the {@code open} ones, or null. */
    private static Token closerOf(final List<Token> spoken, final int from, final int open, final int level) {
        int depth = open;
        for (int i = from; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == level) {
                    return spoken.get(i);
                }
            }
        }
        return null;
    }
}
