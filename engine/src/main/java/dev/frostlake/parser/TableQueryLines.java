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
 * The lines the account reports for a TABLE query written with an alias inside a bracket that may hold a value as
 * well as a query: a scalar subquery, an IN list, an ANY, SOME or ALL comparison. The alias fails the reading of a
 * query, and the account refuses the TABLE where that reading began, then the token after the first part of the name,
 * where the reading of a value stops. A bracket only a query opens, an EXISTS or a FROM item, and a statement of its
 * own refuse the alias alone (all live-verified):
 *
 * <pre>
 *   SELECT 1 IN (TABLE pq3 t)                       'TABLE' at 13, 't' at 23
 *   SELECT 1 = ANY (TABLE pq3 t), 2                 'TABLE' at 16, 't' at 26
 *   SELECT (TABLE pq3 t)                            'TABLE' at 8, 't' at 18
 *   SELECT 1 IN (TABLE pq3 AS t)                    'TABLE' at 13, 'AS' at 23
 *   SELECT 1 IN (TABLE test_schema.pq3 t)           'TABLE' at 13, '.' at 30
 *   SELECT 1 IN ((TABLE pq3 t))                     'TABLE' at 14, 't' at 24
 *   SELECT 1 FROM pq3 WHERE EXISTS (TABLE pq3 t)    't' at 42
 * </pre>
 */
final class TableQueryLines {

    private TableQueryLines() {
    }

    /**
     * The lines for a text whose first fault follows the name of a TABLE query in such a bracket, or null for any
     * other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 4 || fault >= spoken.size() || !SyntaxErrorListener.isNameLike(spoken.get(fault - 1))) {
            return null;
        }
        int start = fault - 1;
        while (start >= 2 && spoken.get(start - 1).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isNameLike(spoken.get(start - 2))) {
            start -= 2;
        }
        final int table = start - 1;
        // The lexer types the word TABLE_QUERY where a query may begin.
        if (table < 2 || spoken.get(table).getType() != FrostlakeParser.TABLE_QUERY
                && spoken.get(table).getType() != FrostlakeLexer.TABLE
                || spoken.get(table - 1).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        int before = table - 1;
        while (before > 0 && spoken.get(before).getType() == FrostlakeLexer.LPAREN) {
            before--;
        }
        if (!holdsAValueToo(spoken.get(before).getType())) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(table)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(table + 2)));
        return lines;
    }

    /** Whether a bracket written after a token of this type may hold a value as well as a query. */
    private static boolean holdsAValueToo(final int type) {
        return type == FrostlakeLexer.IN || type == FrostlakeLexer.ANY || type == FrostlakeLexer.SOME
            || type == FrostlakeLexer.ALL || type == FrostlakeLexer.SELECT;
    }
}
