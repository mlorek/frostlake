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
 * The lines live reports for a fault inside a CASE expression of a query: the fault, and nothing more of the
 * statement, whichever branch holds it (all live-verified):
 *
 * <pre>
 *   SELECT CASE WHEN x y THEN 1 END FROM fz                    'y' at 19
 *   SELECT CASE WHEN 1 = 1 THEN 1 x END FROM fz                'x' at 30
 *   SELECT CASE WHEN 1 = 1 THEN CASE WHEN 2 = 2 THEN x y END END FROM fz      'y' at 51
 *   SELECT CASE x y WHEN 1 THEN 2 END FROM fz                  'y' at 14, then 'WHEN' at 16
 *   SELECT CASE WHEN CAST(1, 2) = 1 THEN 1 END FROM t          ',' at 23, then 'THEN' at 32
 *   SELECT CASE WHEN CAST(1 2) = 1 THEN 1 END FROM t           '2' at 24, then 'THEN' at 31
 * </pre>
 *
 * <p>A fault in the operand of a simple CASE refuses the WHEN after it, and — in a select item's searched CASE — a
 * fault inside the parentheses of a cast that a WHEN's condition starts with refuses that WHEN's THEN. A comma
 * inside any other cast of a condition is refused alone, and one inside a result's cast is the cast rule's own
 * (see {@link CastCommaLines}).
 */
final class CaseFaultLines {

    private CaseFaultLines() {
    }

    /**
     * The lines live reports when the parse's first line names a token inside a query's CASE expression, or null.
     *
     * @param spoken         the text's default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final boolean statementParse) {
        final int fault = statementParse ? CastFaultPlace.positionOf(spoken, faultIndex) : -1;
        if (fault < 2) {
            return null;
        }
        int start = fault;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        final int opener = spoken.get(start).getType();
        if (opener != FrostlakeLexer.SELECT && opener != FrostlakeLexer.WITH) {
            return null;
        }
        int ended = 0;
        int kase = -1;
        for (int i = fault - 1; i >= start && kase < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.END) {
                ended++;
            } else if (type == FrostlakeLexer.CASE) {
                if (ended == 0) {
                    kase = i;
                } else {
                    ended--;
                }
            }
        }
        if (kase < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        final int open = CastFaultPlace.enclosingParen(spoken, fault);
        if (open > kase + 1 && CastFaultPlace.isCast(spoken, open - 1)) {
            final boolean searched = spoken.get(kase + 1).getType() == FrostlakeLexer.WHEN;
            final int clause = CastFaultPlace.clauseOf(spoken, kase);
            if (searched && spoken.get(open - 2).getType() == FrostlakeLexer.WHEN && clause >= 0
                    && spoken.get(clause).getType() == FrostlakeLexer.SELECT) {
                final int then = branchWord(spoken, fault, FrostlakeLexer.THEN);
                if (then > 0) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(then)));
                }
                return lines;
            }
            if (spoken.get(fault).getType() == FrostlakeLexer.COMMA) {
                // In a condition the comma is all there is; in a result the cast rule reads on.
                return inCondition(spoken, kase, open - 1) ? lines : null;
            }
        }
        boolean operand = true;
        int nested = 0;
        for (int i = kase + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.CASE) {
                nested++;
            } else if (type == FrostlakeLexer.END) {
                nested--;
            } else if (nested == 0 && type == FrostlakeLexer.WHEN) {
                operand = false;
            }
        }
        if (operand) {
            final int when = branchWord(spoken, fault, FrostlakeLexer.WHEN);
            if (when > 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(when)));
            }
        }
        return lines;
    }

    /** Whether a position stands in a WHEN's condition or value of the CASE at {@code kase}: after a WHEN, before its THEN. */
    private static boolean inCondition(final List<Token> spoken, final int kase, final int at) {
        boolean condition = false;
        int nested = 0;
        for (int i = kase + 1; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.CASE) {
                nested++;
            } else if (type == FrostlakeLexer.END) {
                nested--;
            } else if (nested == 0 && type == FrostlakeLexer.WHEN) {
                condition = true;
            } else if (nested == 0 && (type == FrostlakeLexer.THEN || type == FrostlakeLexer.ELSE)) {
                condition = false;
            }
        }
        return condition;
    }

    /** The next token of a type after a position that belongs to the same CASE, or -1. */
    private static int branchWord(final List<Token> spoken, final int after, final int wanted) {
        int nested = 0;
        for (int i = after + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.CASE) {
                nested++;
            } else if (type == FrostlakeLexer.END) {
                if (nested == 0) {
                    return -1;
                }
                nested--;
            } else if (nested == 0 && type == wanted) {
                return i;
            }
        }
        return -1;
    }
}
