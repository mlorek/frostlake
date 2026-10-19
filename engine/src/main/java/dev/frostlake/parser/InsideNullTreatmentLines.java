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
 * The lines live reports for a null treatment written inside a call's parentheses — {@code SUM(n IGNORE NULLS)},
 * {@code LAG(n RESPECT NULLS)} (all live-verified). Only FIRST_VALUE and LAST_VALUE take one there; every other
 * function takes it after the closing parenthesis, or not at all, and the word is where the report starts:
 *
 * <pre>
 *   SUM(n IGNORE NULLS) OVER ()                  'IGNORE' alone, as for any function with no null treatment
 *   SUM(n IGNORE NULLS), LAG(n IGNORE NULLS)     'IGNORE' alone: nothing after it is read
 *   SUM(IGNORE NULLS)                            'NULLS': the IGNORE is the argument
 *   LAG(n IGNORE NULLS) OVER (ORDER BY n)        'IGNORE' then the call's ')'
 *   LAG(n IGNORE NULLS, 1) OVER (ORDER BY n)     'IGNORE' then the call's ')'
 *   LAG(n IGNORE) OVER (ORDER BY n)              'IGNORE' alone
 *   ABS(LAG(n IGNORE NULLS) OVER (ORDER BY n))   'IGNORE' then the last ')'
 *   LAG(ABS(n IGNORE NULLS)) OVER (ORDER BY n)   'IGNORE' then the first ')'
 *   LAG(IGNORE NULLS) OVER (ORDER BY n)          'NULLS' then the call's ')'
 *   FIRST_VALUE(n IGNORE) OVER (ORDER BY n)      'IGNORE' then the call's ')'
 *   FIRST_VALUE(n IGNORE NULLS, 1) OVER (...)    ',' then the call's ')'
 * </pre>
 *
 * <p>LAG, LEAD and NTH_VALUE take the treatment after their parenthesis, so at the word the call is read as closed
 * there: {@code LAG(n IGNORE NULLS) OVER (...)} is {@code LAG(n) IGNORE NULLS}, and the parenthesis written after
 * it closes whatever is still open around the call — or nothing, and is named. That is why a call around it takes
 * the window, and its own parenthesis is the one named. A word that the call's parenthesis follows at once is
 * dropped instead, and the call read without it. FIRST_VALUE and LAST_VALUE are read as closed where their list
 * stops being one — at an IGNORE no NULLS follows, at a comma after the treatment.
 */
final class InsideNullTreatmentLines {

    private static final int GENERAL = 0;

    private static final int TREATED_AFTER = 1;

    private static final int TREATED_INSIDE = 2;

    /** A call that takes no argument and may be followed by whatever a window function may: what a call read as
     *  closed is written as, so the tokens after it are read as live reads them. */
    private static final String CLOSED_CALL = "A()";

    private InsideNullTreatmentLines() {
    }

    /**
     * The lines for a statement whose first fault is such a treatment, or null when it is not one: no IGNORE or
     * RESPECT stands after a value among a call's arguments before the fault, or the call stands where it is read
     * another way — inside a query that is a value, behind a DISTINCT.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = spoken.size() - 1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        for (int i = 2; i <= fault && i < spoken.size() - 1; i++) {
            final int type = spoken.get(i).getType();
            if (type != FrostlakeLexer.IGNORE && type != FrostlakeLexer.RESPECT) {
                continue;
            }
            final List<Integer> open = CallTokenGroups.openAt(spoken, i);
            if (isTreatment(spoken, i, fault, open)) {
                return read(sql, spoken, i, open);
            }
        }
        return null;
    }

    /**
     * Whether the word at {@code at} is a null treatment written among a call's arguments that the call does not
     * take there: it follows a value — or opens the list with its NULLS — in the parentheses of a call, and is no
     * treatment FIRST_VALUE or LAST_VALUE takes. A word alone, with no NULLS, counts only where the fault is.
     */
    private static boolean isTreatment(final List<Token> spoken, final int at, final int fault,
                                       final List<Integer> open) {
        if (open.isEmpty()) {
            return false;
        }
        final int call = open.get(open.size() - 1).intValue();
        if (CallTokenGroups.callNameStart(spoken, call) < 0 || CallTokenGroups.holdsQuery(spoken, call)) {
            return false;
        }
        final boolean first = at == call + 1;
        final boolean nulls = spoken.get(at + 1).getType() == FrostlakeLexer.NULLS;
        if (first ? !nulls : !endsAValue(spoken.get(at - 1))) {
            return false;
        }
        if (!nulls && fault != at && fault != at + 1) {
            return false;
        }
        return !(kindOf(spoken, call) == TREATED_INSIDE && !first && nulls
            && spoken.get(at + 2).getType() != FrostlakeLexer.COMMA);
    }

    private static List<String> read(final String sql, final List<Token> spoken, final int at,
                                     final List<Integer> open) {
        final int call = open.get(open.size() - 1).intValue();
        for (int g = 0; g < open.size() - 1; g++) {
            final int group = open.get(g).intValue();
            if (CallTokenGroups.holdsQuery(spoken, group) && !CallTokenGroups.isDerivedTable(spoken, group)) {
                return null;
            }
        }
        final Token word = spoken.get(at);
        final boolean first = at == call + 1;
        final boolean nulls = spoken.get(at + 1).getType() == FrostlakeLexer.NULLS;
        final int close = CallTokenGroups.closeOf(spoken, call);
        final int start = spoken.get(CallTokenGroups.callNameStart(spoken, call)).getStartIndex();
        final int kind = kindOf(spoken, call);
        final List<String> lines = new ArrayList<String>();
        if (kind == TREATED_AFTER) {
            final int opener = spoken.get(call + 1).getType();
            if (opener == FrostlakeLexer.DISTINCT || opener == FrostlakeLexer.ALL) {
                return null;
            }
            if (first) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
                if (close >= 0) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
                }
                return lines;
            }
            if (!nulls && at + 1 != close) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(word));
            lines.addAll(CallTokenGroups.linesAfter(nulls
                ? CallTokenGroups.blanked(sql, start, spoken.get(at + 1).getStopIndex() + 1, CLOSED_CALL)
                : CallTokenGroups.blanked(sql, word.getStartIndex(), word.getStopIndex() + 1, ""), word));
            return lines;
        }
        if (kind == TREATED_INSIDE) {
            if (first) {
                return null;
            }
            final Token named = nulls ? spoken.get(at + 2) : word;
            final Token last = nulls ? spoken.get(at + 1) : spoken.get(at - 1);
            lines.add(SyntaxErrorListener.sentence(named));
            lines.addAll(CallTokenGroups.linesAfter(
                CallTokenGroups.blanked(sql, start, last.getStopIndex() + 1, CLOSED_CALL), named));
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(first ? spoken.get(at + 1) : word));
        for (int g = open.size() - 2; g >= 0; g--) {
            final int around = kindOf(spoken, open.get(g).intValue());
            if (around == TREATED_INSIDE) {
                return null;
            }
            if (around == TREATED_AFTER) {
                // Read as closed with the call around it, the first parenthesis after the word closes nothing.
                for (int i = at + 1; i < spoken.size() && spoken.get(i).getType() != FrostlakeLexer.SEMI; i++) {
                    if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                        lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                        break;
                    }
                }
                return lines;
            }
        }
        return lines;
    }

    /**
     * How the call whose parenthesis is at {@code open} takes a null treatment: after its parenthesis, inside it
     * too, or not at all — told by its name, written bare and unquoted.
     */
    private static int kindOf(final List<Token> spoken, final int open) {
        if (open < 1 || spoken.get(open - 1).getType() != FrostlakeLexer.IDENTIFIER
                || CallTokenGroups.callNameStart(spoken, open) != open - 1) {
            return GENERAL;
        }
        final String name = spoken.get(open - 1).getText();
        if ("LAG".equalsIgnoreCase(name) || "LEAD".equalsIgnoreCase(name) || "NTH_VALUE".equalsIgnoreCase(name)) {
            return TREATED_AFTER;
        }
        return "FIRST_VALUE".equalsIgnoreCase(name) || "LAST_VALUE".equalsIgnoreCase(name) ? TREATED_INSIDE : GENERAL;
    }

    /** Whether a token may end a value: a closing parenthesis or bracket, a literal or a name. */
    private static boolean endsAValue(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET
            || type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL || type == FrostlakeLexer.QUOTED_IDENTIFIER
            || CallTokenGroups.isCallName(token);
    }
}
