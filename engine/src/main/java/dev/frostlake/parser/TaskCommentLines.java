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
 * The line the account reports for a task whose query ends with an aliased FROM item and a COMMENT after it, as if
 * the COMMENT were the task's own: the COMMENT is refused where the query is read on, and nothing after it (all
 * live-verified):
 *
 * <pre>
 *   CREATE TASK tk … AS SELECT 1 FROM t x COMMENT = 'c'         'COMMENT'
 *   CREATE TASK tk … AS SELECT 1 FROM t AS x COMMENT = 'c'      'COMMENT'
 * </pre>
 *
 * A COMMENT after any other clause is refused at the COMMENT already.
 */
final class TaskCommentLines {

    private TaskCommentLines() {
    }

    /**
     * The line for a CREATE TASK whose first fault is the '=' of a COMMENT written after a FROM item's alias, or null
     * for any other text.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param fault  the index of the first offending token
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int fault) {
        if (fault < 5 || fault >= spoken.size() || spoken.get(0).getType() != FrostlakeLexer.CREATE
                || spoken.get(fault).getType() != FrostlakeLexer.EQ
                || spoken.get(fault - 1).getType() != FrostlakeLexer.COMMENT
                || !SyntaxErrorListener.isNameLike(spoken.get(fault - 2))) {
            return null;
        }
        boolean task = false;
        for (int i = 1; i < 5; i++) {
            task = task || spoken.get(i).getType() == FrostlakeLexer.TASK;
        }
        final int item = spoken.get(fault - 3).getType() == FrostlakeLexer.AS ? fault - 4 : fault - 3;
        if (!task || !SyntaxErrorListener.isNameLike(spoken.get(item))
                || spoken.get(item - 1).getType() != FrostlakeLexer.FROM) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault - 1)));
    }
}
