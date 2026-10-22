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
import java.util.Collections;
import java.util.List;

/**
 * The lines live reports for a CLUSTER BY on a view, which only a table and a materialized view take, and for a
 * materialized view whose COPY GRANTS follows its CLUSTER BY (live-verified):
 *
 * <ul>
 *   <li>{@code ALTER VIEW v CLUSTER BY (x)} is refused at the CLUSTER alone, whatever follows it;</li>
 *   <li>{@code CREATE VIEW v CLUSTER BY (x) AS SELECT …} is refused at the BY, the bracketed keys read on as the
 *       view's column list; with a key written bare, {@code CLUSTER BY x AS SELECT …}, the AS after it is refused
 *       too;</li>
 *   <li>{@code CREATE MATERIALIZED VIEW mv CLUSTER BY (x) COPY GRANTS} is refused at the GRANTS and, where the
 *       statement ends there, at the end of the input that lacks its AS.</li>
 * </ul>
 */
final class ViewClusterLines {

    private ViewClusterLines() {
    }

    /**
     * The lines for a statement whose first fault is one of those shapes, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0 && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0) {
            return null;
        }
        int start = fault;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        if (type(spoken, start) == FrostlakeLexer.ALTER) {
            return alteredView(spoken, start + 1, fault);
        }
        if (type(spoken, start) != FrostlakeLexer.CREATE) {
            return null;
        }
        int at = start + 1;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        final boolean materialized = type(spoken, at) == FrostlakeLexer.MATERIALIZED;
        if (materialized) {
            at++;
        }
        if (type(spoken, at) != FrostlakeLexer.VIEW) {
            return null;
        }
        at = pastName(spoken, pastIfNotExists(spoken, at + 1));
        if (at < 0 || type(spoken, at) != FrostlakeLexer.CLUSTER || type(spoken, at + 1) != FrostlakeLexer.BY) {
            return null;
        }
        return materialized ? materializedView(spoken, at + 2, fault) : createdView(spoken, at + 1, fault);
    }

    /** ALTER VIEW [IF EXISTS] name CLUSTER …, refused at the CLUSTER alone. */
    private static List<String> alteredView(final List<Token> spoken, final int afterAlter, final int fault) {
        if (type(spoken, afterAlter) != FrostlakeLexer.VIEW) {
            return null;
        }
        int at = afterAlter + 1;
        if (type(spoken, at) == FrostlakeLexer.IF && type(spoken, at + 1) == FrostlakeLexer.EXISTS) {
            at += 2;
        }
        at = pastName(spoken, at);
        return at == fault && type(spoken, at) == FrostlakeLexer.CLUSTER
            ? Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(at))) : null;
    }

    /** CREATE VIEW name CLUSTER BY …, refused at the BY, and at the AS after a bare key. */
    private static List<String> createdView(final List<Token> spoken, final int by, final int fault) {
        if (by != fault) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(by)));
        final int afterKeys = pastBracket(spoken, by + 1);
        if (afterKeys > 0 && type(spoken, afterKeys) == FrostlakeLexer.AS) {
            return lines;
        }
        if (afterKeys < 0 && !ends(spoken, by + 1) && type(spoken, by + 1) != FrostlakeLexer.LPAREN
                && type(spoken, by + 2) == FrostlakeLexer.AS) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(by + 2)));
            return lines;
        }
        return null;
    }

    /** CREATE MATERIALIZED VIEW name CLUSTER BY (…) COPY GRANTS, refused at the GRANTS and at the end of input. */
    private static List<String> materializedView(final List<Token> spoken, final int keys, final int fault) {
        final int copy = pastBracket(spoken, keys);
        if (copy < 0 || type(spoken, copy) != FrostlakeLexer.COPY || type(spoken, copy + 1) != FrostlakeLexer.GRANTS
                || copy + 1 != fault || type(spoken, copy + 2) != Token.EOF) {
            return null;
        }
        final List<String> lines = new ArrayList<String>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(copy + 1)));
        lines.add(SyntaxErrorListener.sentence(spoken.get(copy + 2)));
        return lines;
    }

    private static int pastIfNotExists(final List<Token> spoken, final int at) {
        return type(spoken, at) == FrostlakeLexer.IF && type(spoken, at + 1) == FrostlakeLexer.NOT
            && type(spoken, at + 2) == FrostlakeLexer.EXISTS ? at + 3 : at;
    }

    /** The index past the bracket that opens at {@code at} and the one that closes it, or -1 where none closes. */
    private static int pastBracket(final List<Token> spoken, final int at) {
        if (type(spoken, at) != FrostlakeLexer.LPAREN) {
            return -1;
        }
        int depth = 0;
        for (int i = at; !ends(spoken, i); i++) {
            if (type(spoken, i) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type(spoken, i) == FrostlakeLexer.RPAREN) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /** The index past a name of one or more dotted parts that starts at {@code at}, or -1 where none starts. */
    private static int pastName(final List<Token> spoken, final int at) {
        if (at < 0 || ends(spoken, at) || type(spoken, at) == FrostlakeLexer.DOT) {
            return -1;
        }
        int past = at + 1;
        while (type(spoken, past) == FrostlakeLexer.DOT && !ends(spoken, past + 1)) {
            past += 2;
        }
        return past;
    }

    /** Whether the statement ends at {@code at}: the end of input, a semicolon, or no token at all. */
    private static boolean ends(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        return type == Token.EOF || type == FrostlakeLexer.SEMI;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
