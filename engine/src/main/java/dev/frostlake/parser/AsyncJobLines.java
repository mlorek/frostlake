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

import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines the account reports for an ASYNC job its block writes without its brackets, or with a semicolon inside
 * them. What follows the first fault is read on as the statement the brackets would hold, and the report ends at the
 * token that stands where the closing bracket is wanted (live-verified):
 *
 * <pre>
 *   BEGIN ASYNC SELECT 1; AWAIT ALL; …          'SELECT', then the ';' where the job's ')' is wanted
 *   BEGIN ASYNC (SELECT 1; SELECT 2); …         the ';' inside the brackets, then the ')' after the next statement
 * </pre>
 */
final class AsyncJobLines {

    private AsyncJobLines() {
    }

    /**
     * The lines of a block whose first fault is an ASYNC job's missing bracket or a semicolon inside its brackets,
     * or null.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 1; i < spoken.size() - 1 && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0) {
            return null;
        }
        final int from;
        final int wanted;
        if (spoken.get(fault - 1).getType() == FrostlakeLexer.ASYNC
                && spoken.get(fault).getType() != FrostlakeLexer.LPAREN) {
            // The job's statement without its brackets: its semicolon stands where the ')' is wanted.
            from = fault;
            wanted = FrostlakeLexer.SEMI;
        } else if (spoken.get(fault).getType() == FrostlakeLexer.SEMI && insideJobBrackets(spoken, fault)) {
            // The semicolon ends the job's statement; the next one is read on, and the ')' after it closes nothing.
            from = fault + 1;
            wanted = FrostlakeLexer.RPAREN;
        } else {
            return null;
        }
        final int stop = firstAtDepthZero(spoken, from, wanted);
        if (stop <= from) {
            return null;
        }
        // Only a statement the account reads without a fault of its own is read on this way.
        final String statement = sql.substring(spoken.get(from).getStartIndex(), spoken.get(stop).getStartIndex());
        if (!SyntaxErrorListener.linesReportedAfresh(statement).isEmpty()) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(stop)));
        return lines;
    }

    /** Whether the token stands inside the brackets of an ASYNC job, no semicolon before it there. */
    private static boolean insideJobBrackets(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i > 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return spoken.get(i - 1).getType() == FrostlakeLexer.ASYNC;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI && depth == 0) {
                return false;
            }
        }
        return false;
    }

    /** The first token of the type outside any bracket opened from {@code from}, or -1 when a ')' comes first. */
    private static int firstAtDepthZero(final List<Token> spoken, final int from, final int wanted) {
        int depth = 0;
        for (int i = from; i < spoken.size() - 1; i++) {
            final int type = spoken.get(i).getType();
            if (depth == 0 && type == wanted) {
                return i;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            }
        }
        return -1;
    }
}
