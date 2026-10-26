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

import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;

/**
 * The line the account reports for a column of a CREATE TABLE whose COMMENT is followed by anything but a string:
 * the COMMENT opens the column's comment, so the fault is what follows it (all live-verified):
 *
 * <pre>
 *   CREATE TABLE t (a INT COMMENT $x)          '$x'
 *   CREATE TABLE t (a INT COMMENT = 'c')       '='
 * </pre>
 */
final class ColumnCommentLines {

    private ColumnCommentLines() {
    }

    /**
     * The line for a CREATE TABLE whose first fault is a COMMENT among its columns that no string follows, or null
     * for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 4 || fault + 1 >= spoken.size() || spoken.get(0).getType() != FrostlakeLexer.CREATE
                || spoken.get(fault).getType() != FrostlakeLexer.COMMENT) {
            return null;
        }
        final int after = spoken.get(fault + 1).getType();
        final int before = spoken.get(fault - 1).getType();
        if (after == FrostlakeLexer.STRING_LITERAL || after == FrostlakeLexer.DOLLAR_QUOTED_STRING
                || before == FrostlakeLexer.COMMA || before == FrostlakeLexer.LPAREN) {
            return null;
        }
        boolean table = false;
        int open = -1;
        for (int i = 1; i < fault && open < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                open = i;
            } else {
                table = table || spoken.get(i).getType() == FrostlakeLexer.TABLE;
            }
        }
        if (!table || open < 0) {
            return null;
        }
        // The COMMENT stands among the column list's own items, not inside a nested parenthesis or after the list.
        int depth = 0;
        for (int i = open; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return null;
                }
            }
        }
        return depth != 1 ? null : Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
    }
}
