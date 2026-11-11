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
 * The line the account reports for a dynamic table's column list whose column it cannot read — its clauses out of
 * their order, or one written twice: one line, at the column's name, whatever clause is out of place (live-verified:
 * {@code (a WITH MASKING POLICY p WITH PROJECTION POLICY q, b)} and {@code (a COMMENT 'd' WITH TAG (t = 'y'), b)} are
 * refused at the {@code a}).
 */
final class DynamicTableColumnLines {

    private DynamicTableColumnLines() {
    }

    /**
     * The line for a CREATE DYNAMIC TABLE whose first fault lies in its column list, or the lines worked out so far.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.isEmpty()) {
            return lines;
        }
        final int open = columnListOpen(spoken);
        if (open < 0) {
            return lines;
        }
        final int fault = spokenIndex(spoken, faults.get(0).intValue());
        final int close = StarModifierContextLines.closing(spoken, open);
        if (fault <= open || fault >= close) {
            return lines;
        }
        int start = open + 1;
        int depth = 0;
        for (int i = open + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                start = i + 1;
            }
        }
        return start < fault ? Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(start))) : lines;
    }

    /** The index of the bracket that opens a CREATE DYNAMIC TABLE's column list, or -1 for another statement. */
    private static int columnListOpen(final List<Token> spoken) {
        if (type(spoken, 0) != FrostlakeLexer.CREATE) {
            return -1;
        }
        int at = 1;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        if (type(spoken, at) == FrostlakeLexer.TRANSIENT) {
            at++;
        }
        if (type(spoken, at) != FrostlakeLexer.DYNAMIC || type(spoken, at + 1) != FrostlakeLexer.TABLE) {
            return -1;
        }
        at += 2;
        if (type(spoken, at) == FrostlakeLexer.IF && type(spoken, at + 1) == FrostlakeLexer.NOT
                && type(spoken, at + 2) == FrostlakeLexer.EXISTS) {
            at += 3;
        }
        if (at >= spoken.size() || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return -1;
        }
        at++;
        while (type(spoken, at) == FrostlakeLexer.DOT && at + 1 < spoken.size()
                && SyntaxErrorListener.isNameLike(spoken.get(at + 1))) {
            at += 2;
        }
        return type(spoken, at) == FrostlakeLexer.LPAREN ? at : -1;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }

    /** The index in {@code spoken} of the token with the stream index {@code tokenIndex}; -1 for the end of input. */
    private static int spokenIndex(final List<Token> spoken, final int tokenIndex) {
        if (tokenIndex < 0) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }
}
