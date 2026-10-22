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
 * The lines live reports for a star, a DISTINCT or an ALL written as a named argument's value — {@code COUNT(x => *)},
 * {@code COUNT(x => DISTINCT n)} (all live-verified). A name takes a value, and none of the three is one, so the
 * token is named; what follows it decides what else is:
 *
 * <pre>
 *   COUNT(x => *)                      '*' alone: the parenthesis right after it closes the call
 *   COUNT(x => *, 1)                   '*' ',' then the call's ')'
 *   COUNT(x => *, y => 1)              '*' ',' then the '=>'
 *   COUNT(x => *, y => 1, z => 2)      '*' ',' then both '=>'
 *   COUNT(x => *, y => 1, 2)           '*' ',' '=>' then the ')'
 *   COUNT(x => * 2)                    '*' then the ')'
 *   COUNT(x => DISTINCT n)             'DISTINCT' alone, whatever stands before the ')'
 *   COUNT(x => DISTINCT n, 1)          'DISTINCT' then the ')': the comma is not named
 *   COUNT(x => DISTINCT n, y => 1)     'DISTINCT' then the '=>'
 *   COUNT(x => t.*)                    '*' then the '.' before it, and nothing more
 * </pre>
 *
 * <p>The call is given up at the token — a comma right after a star is named with it — and the list around it is
 * read on as if the call had never opened. In a select list that reads items: an arrow outside any parentheses is
 * no part of one, so it is named and its item given up, and a parenthesis closing nothing is named and ends the
 * reading. Among another call's arguments an arrow names an argument, and that call's own parenthesis is the one
 * closing nothing: {@code ABS(COUNT(x => *, y => 1))} is the star, the comma and the last parenthesis. Anywhere
 * else — a WHERE's condition — a comma separates nothing, and nothing after it is named.
 */
final class NamedStarLines {

    private static final int ELSEWHERE = 0;

    private static final int ITEMS = 1;

    private static final int ARGUMENTS = 2;

    private NamedStarLines() {
    }

    /**
     * The lines for a statement whose first fault is such a value, or null when it is not one: the fault is no
     * star, DISTINCT or ALL after a name and its arrow among a call's arguments, or the call stands inside a
     * query in parentheses, which is read another way.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (isQualifiedStarValue(spoken, at)) {
            final List<String> lines = new ArrayList<String>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(at - 1)));
            return lines;
        }
        if (at < 3 || !isRefusedValue(spoken.get(at).getType())
                || spoken.get(at - 1).getType() != FrostlakeLexer.ARROW
                || !CallTokenGroups.isCallName(spoken.get(at - 2))) {
            return null;
        }
        final List<Integer> open = CallTokenGroups.openAt(spoken, at);
        if (open.isEmpty()) {
            return null;
        }
        final int call = open.get(open.size() - 1).intValue();
        final int name = CallTokenGroups.callNameStart(spoken, call);
        if (name < 0) {
            return null;
        }
        for (final Integer group : open) {
            if (CallTokenGroups.holdsQuery(spoken, group.intValue())) {
                return null;
            }
        }
        final Token fault = spoken.get(at);
        final int start = spoken.get(name).getStartIndex();
        final int where = placeOf(spoken, name, open);
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(fault));
        int comma = -1;
        if (fault.getType() == FrostlakeLexer.STAR) {
            final Token next = spoken.get(at + 1);
            if (next.getType() == FrostlakeLexer.COMMA) {
                lines.add(SyntaxErrorListener.sentence(next));
                comma = at + 1;
            } else if (next.getType() != FrostlakeLexer.RPAREN) {
                if (where == ITEMS && !endsStatement(next)) {
                    readItems(spoken, at + 1, lines);
                } else {
                    lines.addAll(CallTokenGroups.linesAfter(
                        CallTokenGroups.blanked(sql, start, fault.getStopIndex() + 1, ""), fault));
                }
                return lines;
            }
        } else {
            comma = commaAfter(spoken, at);
        }
        if (comma >= 0) {
            if (where == ITEMS) {
                readItems(spoken, comma + 1, lines);
            } else if (where == ARGUMENTS) {
                final Token separator = spoken.get(comma);
                lines.addAll(CallTokenGroups.linesAfter(
                    CallTokenGroups.blanked(sql, start, separator.getStopIndex() + 1, ""), separator));
            }
            return lines;
        }
        final int close = CallTokenGroups.closeOf(spoken, call);
        if (close >= 0) {
            // The call closes as written: its argument is dropped, with the comma before it, and the rest read.
            final int first = spoken.get(at - 3).getType() == FrostlakeLexer.COMMA ? at - 3 : at - 2;
            lines.addAll(CallTokenGroups.linesAfter(CallTokenGroups.blanked(sql, spoken.get(first).getStartIndex(),
                spoken.get(close).getStartIndex(), ""), fault));
        }
        return lines;
    }

    /**
     * Whether the token at {@code at} is a relation's star written as a named argument's value, {@code x => t.*}, in a
     * call's parentheses and in no query in parentheses.
     */
    private static boolean isQualifiedStarValue(final List<Token> spoken, final int at) {
        if (at < 5 || spoken.get(at).getType() != FrostlakeLexer.STAR
                || spoken.get(at - 1).getType() != FrostlakeLexer.DOT || !CallTokenGroups.isCallName(spoken.get(at - 2))
                || spoken.get(at - 3).getType() != FrostlakeLexer.ARROW
                || !CallTokenGroups.isCallName(spoken.get(at - 4))) {
            return false;
        }
        final List<Integer> open = CallTokenGroups.openAt(spoken, at);
        if (open.isEmpty() || CallTokenGroups.callNameStart(spoken, open.get(open.size() - 1).intValue()) < 0) {
            return false;
        }
        for (final Integer group : open) {
            if (CallTokenGroups.holdsQuery(spoken, group.intValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRefusedValue(final int type) {
        return type == FrostlakeLexer.STAR || type == FrostlakeLexer.DISTINCT || type == FrostlakeLexer.ALL;
    }

    private static boolean endsStatement(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }

    /** The position of the first comma between the token at {@code at} and its call's closing parenthesis, or -1. */
    private static int commaAfter(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth-- == 0) {
                    return -1;
                }
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                return i;
            } else if (endsStatement(spoken.get(i))) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Where the call whose name starts at {@code name} stands: among a select list's items, among another call's
     * arguments, or elsewhere — the first clause word before it outside any parentheses tells, or the parenthesis
     * it stands in.
     */
    private static int placeOf(final List<Token> spoken, final int name, final List<Integer> open) {
        int depth = 0;
        for (int i = name - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return open.size() > 1
                        && CallTokenGroups.callNameStart(spoken, open.get(open.size() - 2).intValue()) >= 0
                        ? ARGUMENTS : ELSEWHERE;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI) {
                return ELSEWHERE;
            } else if (depth == 0 && type == FrostlakeLexer.SELECT) {
                return ITEMS;
            } else if (depth == 0 && opensAnotherClause(type)) {
                return ELSEWHERE;
            }
        }
        return ELSEWHERE;
    }

    /** Whether a word outside any parentheses shows the tokens after it to be no item of a select list. */
    private static boolean opensAnotherClause(final int type) {
        switch (type) {
            case FrostlakeLexer.FROM:
            case FrostlakeLexer.WHERE:
            case FrostlakeLexer.GROUP:
            case FrostlakeLexer.HAVING:
            case FrostlakeLexer.QUALIFY:
            case FrostlakeLexer.ORDER:
            case FrostlakeLexer.LIMIT:
            case FrostlakeLexer.ON:
            case FrostlakeLexer.WHEN:
            case FrostlakeLexer.THEN:
            case FrostlakeLexer.ELSE:
            case FrostlakeLexer.VALUES:
            case FrostlakeLexer.SET:
            case FrostlakeLexer.BY:
            case FrostlakeLexer.AND:
            case FrostlakeLexer.OR:
            case FrostlakeLexer.NOT:
            case FrostlakeLexer.CASE:
            case FrostlakeLexer.USING:
            case FrostlakeLexer.CONNECT:
            case FrostlakeLexer.START:
            case FrostlakeLexer.BETWEEN:
            case FrostlakeLexer.IN:
            case FrostlakeLexer.RETURN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Reads the rest of a select list on from the token at {@code from}, adding the lines it gives: an arrow
     * outside any parentheses, with which its item is given up as far as the next comma, and a parenthesis closing
     * nothing in an item still read, which ends the reading.
     */
    private static void readItems(final List<Token> spoken, final int from, final List<String> lines) {
        int depth = 0;
        boolean givenUp = false;
        for (int i = from; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            final int type = token.getType();
            if (type == Token.EOF) {
                return;
            }
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                depth = Math.max(depth - 1, 0);
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (!givenUp) {
                    lines.add(SyntaxErrorListener.sentence(token));
                    return;
                }
            } else if (depth == 0 && type == FrostlakeLexer.COMMA) {
                givenUp = false;
            } else if (depth == 0 && (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.FROM)) {
                return;
            } else if (depth == 0 && type == FrostlakeLexer.ARROW && !givenUp) {
                lines.add(SyntaxErrorListener.sentence(token));
                givenUp = true;
            }
        }
    }
}
