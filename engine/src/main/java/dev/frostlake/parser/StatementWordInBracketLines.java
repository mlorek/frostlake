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
 * The lines the account reports for a DESCRIBE or a SHOW written in the brackets a query stands in outside a FROM
 * clause (all live-verified):
 *
 * <pre>
 *   SELECT 1 WHERE EXISTS (DESCRIBE TABLE t)        'DESCRIBE' alone: the bracket's end is not told
 *   SELECT * FROM t WHERE a IN (DESCRIBE TABLE t)   'TABLE' alone
 *   SELECT 1 WHERE (DESCRIBE TABLE t) = 1           'TABLE' alone
 *   CREATE TABLE x AS (DESCRIBE TABLE t)            'DESCRIBE' alone
 *   SELECT (DESCRIBE TABLE t)                       'TABLE' twice, as a select item; SELECT (SHOW TABLES) is
 *                                                   'TABLES' once
 *   WITH w AS (DESCRIBE TABLE t) SELECT * FROM w    'TABLE' alone: a common table expression reads the word as a
 *                                                   name, and refuses what follows it
 * </pre>
 *
 * <p>And for an INSERT, an UPDATE or an EXPLAIN written as a select item's bracketed value (all live-verified):
 *
 * <pre>
 *   SELECT (INSERT INTO t VALUES (1, 'a'))          'INTO', then 'VALUES': INSERT reads as a name, and the name
 *                                                   after the refused word is read, then the token after it refused
 *   SELECT (UPDATE t SET a = 1)                     'UPDATE', then 'SET'
 *   SELECT (EXPLAIN USING TEXT SELECT 1)            'USING' alone
 * </pre>
 */
final class StatementWordInBracketLines {

    private StatementWordInBracketLines() {
    }

    /**
     * The lines for a text whose first fault is such a word, or the token after it, or null for any other text.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        if (fault < 2) {
            return null;
        }
        final List<String> statementItem = statementItemLines(spoken, fault);
        if (statementItem != null) {
            return statementItem;
        }
        if (spoken.get(fault).getType() == FrostlakeLexer.IN && fault + 3 < spoken.size()
                && spoken.get(fault + 1).getType() == FrostlakeLexer.LPAREN && isStatementWord(spoken.get(fault + 2))) {
            // The parse stops at the IN; the account reads the word as a value of the list and refuses what follows.
            final List<String> listed = new ArrayList<>();
            listed.add(SyntaxErrorListener.sentence(spoken.get(fault + 3)));
            return listed;
        }
        final int open = isStatementWord(spoken.get(fault)) ? fault - 1
            : isStatementWord(spoken.get(fault - 1)) ? fault - 2 : -1;
        if (open < 1 || spoken.get(open).getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final int before = spoken.get(open - 1).getType();
        final List<String> lines = new ArrayList<>();
        if (before == FrostlakeLexer.AS && spoken.get(0).getType() == FrostlakeLexer.WITH) {
            if (fault != open + 1) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(open + 2)));
            return lines;
        }
        if (before == FrostlakeLexer.SELECT) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
            if (spoken.get(fault).getType() == FrostlakeLexer.TABLE) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
            }
            return lines;
        }
        if (before != FrostlakeLexer.EXISTS && before != FrostlakeLexer.IN && before != FrostlakeLexer.WHERE
                && before != FrostlakeLexer.AS) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        return lines;
    }

    /** The lines of an INSERT, an UPDATE or an EXPLAIN that opens a select item's brackets, or null. */
    private static List<String> statementItemLines(final List<Token> spoken, final int fault) {
        final int type = spoken.get(fault).getType();
        final int word = type == FrostlakeLexer.INTO || type == FrostlakeLexer.USING ? fault - 1 : fault;
        final int wordType = spoken.get(word).getType();
        final boolean shaped = type == FrostlakeLexer.INTO && wordType == FrostlakeLexer.INSERT
            || type == FrostlakeLexer.USING && wordType == FrostlakeLexer.EXPLAIN || type == FrostlakeLexer.UPDATE;
        if (!shaped || word < 2 || spoken.get(word - 1).getType() != FrostlakeLexer.LPAREN
                || spoken.get(word - 2).getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(fault)));
        if (type != FrostlakeLexer.USING && fault + 2 < spoken.size()
                && SyntaxErrorListener.isNameLike(spoken.get(fault + 1))) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
        }
        return lines;
    }

    private static boolean isStatementWord(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.DESCRIBE || type == FrostlakeLexer.DESC || type == FrostlakeLexer.SHOW;
    }
}
