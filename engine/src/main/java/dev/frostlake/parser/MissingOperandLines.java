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
 * The lines the account goes on to report after a token refused where a select list or a table is missing: it
 * reads the statement on as if the missing part stood there, so what the rest lacks is reported too (all
 * live-verified):
 *
 * <pre>
 *   SELECT FROM                     'FROM', then '&lt;EOF&gt;': the FROM's table is missing as well
 *   SELECT FROM WHERE               'FROM', 'WHERE', then '&lt;EOF&gt;'
 *   SELECT FROM t x y               'FROM', then 'y'
 *   SELECT FROM t                   'FROM' alone: the rest reads
 *   SELECT GROUP BY                 'GROUP', then '&lt;EOF&gt;'
 *   SELECT 1 FROM WHERE             'WHERE', then '&lt;EOF&gt;': the WHERE's condition
 *   SELECT FROM t; SELECT FROM      'FROM', then the second statement's 'FROM' and '&lt;EOF&gt;'
 *   … EXISTS (SELECT FROM)          'FROM', then the ')'
 *   SELECT 1 &amp; FROM t x y           'FROM', then 'y': an ampersand's operand is missing
 * </pre>
 *
 * <p>The reading is rebuilt on the text: a value, or a table's name, is written where the part is missing and the
 * lines of that text are told where the statement wrote them. A task's body is told for its first token alone when
 * that token opens no body: {@code CREATE TASK … AS VALUES (1)} names the VALUES and nothing after it.
 */
final class MissingOperandLines {

    private static final String VALUE = "1 ";
    private static final String TABLE = "d ";

    private MissingOperandLines() {
    }

    /**
     * The lines of a text whose first fault stands where a select list or a FROM's table is missing, the rest of
     * the statement read on; or a task's body refused at its first token, alone.
     *
     * @param sql        the text
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @param lines      the lines worked out for the text
     * @return {@code lines}, or the lines with the statement read on
     */
    static List<String> readOn(final String sql, final List<Token> spoken, final int faultIndex,
                               final List<String> lines) {
        int at = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
                break;
            }
        }
        if (at < 1 || !lines.get(0).equals(SyntaxErrorListener.sentence(spoken.get(at)))) {
            return lines;
        }
        if (opensATasksBody(spoken, at)) {
            return lines.size() == 1 ? lines : new ArrayList<>(lines.subList(0, 1));
        }
        final Token fault = spoken.get(at);
        final int before = spoken.get(at - 1).getType();
        final String missing;
        if (opensAClause(fault.getType()) && (before == FrostlakeLexer.SELECT
                || (before == FrostlakeLexer.DISTINCT || before == FrostlakeLexer.ALL)
                    && at >= 2 && spoken.get(at - 2).getType() == FrostlakeLexer.SELECT)) {
            missing = VALUE;
        } else if (before == FrostlakeLexer.FROM && fault.getType() == FrostlakeLexer.WHERE) {
            missing = TABLE;
        } else if (before == FrostlakeParser.AMP && fault.getType() == FrostlakeLexer.FROM) {
            missing = VALUE;
        } else {
            return lines;
        }
        if (fault.getType() == Token.EOF || fault.getStartIndex() < 0 || fault.getStartIndex() > sql.length()) {
            return lines;
        }
        final String repaired = sql.substring(0, fault.getStartIndex()) + missing + sql.substring(fault.getStartIndex());
        final int[] place = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        if (place == null) {
            return lines;
        }
        final List<String> read = new ArrayList<>();
        read.add(lines.get(0));
        for (final String line : SyntaxErrorListener.linesReportedAfresh(repaired)) {
            final int[] where = SyntaxErrorListener.sentenceCoordinates(line);
            if (where == null || where[0] != place[0] || where[1] < place[1] + missing.length()) {
                read.add(line);
                continue;
            }
            // What stands after the missing part is told where the statement wrote it.
            final String moved = line.replace(" at position " + where[1] + " ",
                " at position " + (where[1] - missing.length()) + " ");
            if (!moved.equals(read.get(read.size() - 1))) {
                read.add(moved);
            }
        }
        return read;
    }

    /** Whether a token is the word of a clause a select list stands before: there the list is missing. */
    private static boolean opensAClause(final int type) {
        return type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.INTO;
    }

    /** Whether the token is the first of a task's body: it stands after the AS of a CREATE TASK. */
    private static boolean opensATasksBody(final List<Token> spoken, final int at) {
        if (spoken.get(at - 1).getType() != FrostlakeLexer.AS || spoken.get(0).getType() != FrostlakeLexer.CREATE) {
            return false;
        }
        for (int i = 1; i < at && i < 4; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.TASK) {
                return true;
            }
        }
        return false;
    }
}
