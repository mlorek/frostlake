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
 * The lines the account reports for a staged-file query's argument list that does not parse, {@code FROM @st (…)}.
 * The list is the query's from its opening parenthesis on, and an argument is a name, {@code =>} and a value; past the
 * first fault the account reads on as follows (all live-verified):
 *
 * <pre>
 *   @ust (FILE_FORMAT =&gt; ff PATTERN =&gt; '.*')           'PATTERN' alone: the token after a whole argument
 *   @ust (FILE_FORMAT =&gt; ())                           the value's ')' alone
 *   @ust (FILE_FORMAT =&gt; (FORMAT_NAME =&gt; 'ff'))        the '=&gt;' inside the value, then the list's ')'
 *   @ust (FILE_FORMAT =&gt; (TYPE = CSV FIELD_DELIMITER = '|'))   'FIELD_DELIMITER' alone: a word inside the value
 *   @ust (,)                                           the ',' alone
 *   @ust (=&gt; ff)                                       the '=&gt;', then the ')' where the name's '=&gt;' was due
 * </pre>
 */
final class StageArgumentLines {

    private StageArgumentLines() {
    }

    /**
     * The lines for a text whose first fault lies in a stage's argument list, or null for any other fault.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 2 || fault >= spoken.size()) {
            return null;
        }
        final int open = listOpen(spoken, fault);
        if (open < 0) {
            return null;
        }
        if (open == fault) {
            return openingFault(spoken, open);
        }
        int depth = 0;
        int segment = open + 1;
        int valueOpen = -1;
        for (int i = open + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    valueOpen = i;
                }
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                segment = i + 1;
            }
        }
        final int faultType = spoken.get(fault).getType();
        if (depth == 0) {
            // A token after a whole argument, where a ',' or the list's ')' was due.
            final boolean named = segment + 1 < fault && spoken.get(segment + 1).getType() == FrostlakeLexer.ARROW;
            return named && fault > segment + 2 && faultType != FrostlakeLexer.COMMA
                && faultType != FrostlakeLexer.RPAREN ? one(spoken.get(fault)) : null;
        }
        if (depth != 1 || valueOpen < 0) {
            return null;
        }
        if (fault == valueOpen + 1) {
            return faultType == FrostlakeLexer.RPAREN ? one(spoken.get(fault)) : null;
        }
        // A '=>' inside a value in parentheses: the value's ')' then closes the list, and the list's own ')' is
        // refused after it. A word there is refused alone: (TYPE = CSV FIELD_DELIMITER = '|') reports the
        // FIELD_DELIMITER and nothing after it.
        if (faultType != FrostlakeLexer.ARROW) {
            return StarModifierResync.isName(spoken.get(fault)) ? one(spoken.get(fault)) : null;
        }
        final int close = closing(spoken, valueOpen);
        if (close < 0 || close + 1 >= spoken.size() || spoken.get(close + 1).getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(close + 1)));
        return lines;
    }

    /** The lines for a list refused at its opening parenthesis: an empty first argument, or one with no name. */
    private static List<String> openingFault(final List<Token> spoken, final int open) {
        if (open + 2 >= spoken.size()) {
            return null;
        }
        final Token first = spoken.get(open + 1);
        if (first.getType() == FrostlakeLexer.COMMA && spoken.get(open + 2).getType() == FrostlakeLexer.RPAREN) {
            return one(first);
        }
        if (first.getType() == FrostlakeLexer.ARROW && open + 3 < spoken.size()
                && StarModifierResync.isName(spoken.get(open + 2))
                && spoken.get(open + 3).getType() != FrostlakeLexer.ARROW) {
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(first));
            lines.add(SyntaxErrorListener.sentence(spoken.get(open + 3)));
            return lines;
        }
        return null;
    }

    /**
     * The index of the opening parenthesis of the stage argument list the fault stands in, or is: a '(' written right
     * after a stage reference that follows FROM, JOIN or a comma; -1 when the fault stands in none.
     */
    private static int listOpen(final List<Token> spoken, final int fault) {
        int depth = 0;
        for (int i = fault; i >= 1; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN && i < fault) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth > 0) {
                    depth--;
                } else if (followsStage(spoken, i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Whether the '(' at the index follows a stage reference read after FROM, JOIN or a comma. */
    private static boolean followsStage(final List<Token> spoken, final int open) {
        int i = open - 1;
        // A stage reference is written without a space inside it: @st, @db.sc.st/path.
        while (i > 0 && spoken.get(i).getType() != FrostlakeLexer.AT
                && spoken.get(i - 1).getStopIndex() + 1 == spoken.get(i).getStartIndex()) {
            i--;
        }
        if (i < 1 || spoken.get(i).getType() != FrostlakeLexer.AT) {
            return false;
        }
        final int before = spoken.get(i - 1).getType();
        return before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN || before == FrostlakeLexer.COMMA;
    }

    /** The index of the ')' closing the '(' at the index, or -1. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> one(final Token token) {
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(token));
        return lines;
    }
}
