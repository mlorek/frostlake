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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.Token;

/**
 * The line the account reports for a statement that is no query written in the parentheses a FROM clause reads a
 * query in. The statement's first word is a name there, and the parse stops at what follows it; the account names
 * the word itself, and nothing after it (live-verified):
 *
 * <pre>
 *   SELECT * FROM (DESCRIBE TABLE t)          'DESCRIBE'
 *   SELECT * FROM (DESC TABLE t) d JOIN …     'DESC'
 *   SELECT * FROM (SHOW TABLES IN SCHEMA s)   'SHOW'
 *   SELECT * FROM (EXPLAIN SELECT 1)          'EXPLAIN'
 *   SELECT * FROM (CALL p())                  'CALL'
 *   SELECT * FROM (USE SCHEMA s)              'USE'
 *   SELECT * FROM (VALUES)                    'VALUES'
 * </pre>
 *
 * A word that stands alone in the parentheses, or with one name after it, is the table it names:
 * {@code FROM (DESCRIBE)} and {@code FROM (describe t)} read a table called DESCRIBE.
 */
final class StatementWordInFromLines {

    private static final Set<Integer> OPENERS = new HashSet<Integer>(Arrays.asList(
        Integer.valueOf(FrostlakeLexer.DESCRIBE), Integer.valueOf(FrostlakeLexer.DESC),
        Integer.valueOf(FrostlakeLexer.SHOW), Integer.valueOf(FrostlakeLexer.EXPLAIN),
        Integer.valueOf(FrostlakeLexer.CALL), Integer.valueOf(FrostlakeLexer.USE),
        Integer.valueOf(FrostlakeLexer.VALUES)));

    private StatementWordInFromLines() {
    }

    /**
     * The line for a text whose first fault stands in the parentheses that follow FROM, behind a statement's
     * first word, or null for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the one line, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 3 || fault >= spoken.size()) {
            return null;
        }
        int depth = 0;
        int open = -1;
        for (int i = fault - 1; i >= 0 && open < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    open = i;
                } else {
                    depth--;
                }
            }
        }
        if (open < 1 || open + 1 >= fault || spoken.get(open - 1).getType() != FrostlakeLexer.FROM
                || !OPENERS.contains(Integer.valueOf(spoken.get(open + 1).getType()))) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(open + 1)));
    }
}
