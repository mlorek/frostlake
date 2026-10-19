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

import java.util.Collections;
import java.util.List;

/**
 * The line live reports for a comma a property list does not take, outside any parentheses, in a CREATE of a file
 * format, a stage, a table or a schema — the comma alone, nothing of what follows it (all live-verified):
 *
 * <pre>
 *   CREATE STAGE s , COMMENT = 'x'                       before the first property
 *   CREATE TABLE t (a INT) COMMENT = 'x', , …            a second one after a property
 *   CREATE TABLE t (a INT) CLUSTER BY (a), COMMENT = …   after an option that takes none
 * </pre>
 */
final class PropertyCommaLines {

    private PropertyCommaLines() {
    }

    /**
     * The line for a statement whose first fault is such a comma, or null when it is not one: the fault is no comma,
     * stands inside parentheses or after a query, the statement creates something else, or another statement follows.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        if (at < 0 || spoken.get(at).getType() != FrostlakeLexer.COMMA) {
            return null;
        }
        int opener = at;
        while (opener > 0 && spoken.get(opener - 1).getType() != FrostlakeLexer.SEMI) {
            opener--;
        }
        if (spoken.get(opener).getType() != FrostlakeLexer.CREATE) {
            return null;
        }
        boolean kind = false;
        int depth = 0;
        for (int i = opener; i < at; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SELECT) {
                return null;
            }
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.STAGE || type == FrostlakeLexer.TABLE
                    || type == FrostlakeLexer.SCHEMA || type == FrostlakeLexer.FORMAT
                        && spoken.get(i - 1).getType() == FrostlakeLexer.FILE)) {
                kind = true;
            }
        }
        if (!kind || depth != 0) {
            return null;
        }
        int end = at;
        while (spoken.get(end).getType() != Token.EOF && spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            end++;
        }
        if (spoken.get(end).getType() == FrostlakeLexer.SEMI && spoken.get(end + 1).getType() != Token.EOF) {
            // Another statement follows, and is read on its own.
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(at)));
    }
}
