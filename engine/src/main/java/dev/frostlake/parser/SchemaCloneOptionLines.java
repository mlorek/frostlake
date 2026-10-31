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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The lines the account reports for a CREATE SCHEMA … CLONE whose option is cut short or misspelt, with no point of
 * Time Travel (live-verified):
 *
 * <pre>
 *   … CLONE s INCLUDE                                     '&lt;EOF&gt;'
 *   … CLONE s INCLUDE INTERNAL STAGE                      'STAGE', then '&lt;EOF&gt;'
 *   … CLONE s INCLUDE INTERNAL STAGE IGNORE HYBRID TABLES 'STAGE', 'IGNORE', 'TABLES'
 *   … CLONE s IGNORE TABLES WITH INSUFFICIENT …           'TABLES', then the word after WITH, '&lt;EOF&gt;' too
 * </pre>
 *
 * <p>A cut-short INCLUDE is refused where the statement ends, not at the INCLUDE. Without a point IGNORE takes
 * HYBRID alone, so its TABLES is refused, and the WITH after it is read as the one WITH MANAGED ACCESS opens, the word
 * after it refused in turn; {@code IGNORE TABLES} alone is refused at TABLES and nowhere else.
 */
final class SchemaCloneOptionLines {

    private SchemaCloneOptionLines() {
    }

    /**
     * The lines for a statement whose first fault stands among the options of such a clone, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            final Token token = spoken.get(i);
            if (faultIndex < 0 ? token.getType() == Token.EOF : token.getTokenIndex() == faultIndex) {
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
        final int options = optionsAt(spoken, start);
        if (options < 0 || fault < options) {
            return null;
        }
        if (type(spoken, options) == FrostlakeLexer.INCLUDE) {
            if (fault == options && type(spoken, options + 1) == Token.EOF) {
                return Collections.singletonList(sentence(spoken, options + 1));
            }
            if (fault != options + 2 || type(spoken, options + 1) != FrostlakeLexer.INTERNAL
                    || type(spoken, options + 2) != FrostlakeLexer.STAGE) {
                return null;
            }
            final int after = options + 3;
            if (type(spoken, after) == Token.EOF) {
                return Arrays.asList(sentence(spoken, fault), sentence(spoken, after));
            }
            if (type(spoken, after) == FrostlakeLexer.IGNORE && type(spoken, after + 1) == FrostlakeLexer.HYBRID
                    && type(spoken, after + 2) == FrostlakeLexer.TABLES && type(spoken, after + 3) == Token.EOF) {
                return Arrays.asList(sentence(spoken, fault), sentence(spoken, after), sentence(spoken, after + 2));
            }
            return null;
        }
        if (type(spoken, options) == FrostlakeLexer.IGNORE && fault == options + 1
                && type(spoken, options + 1) == FrostlakeLexer.TABLES
                && type(spoken, options + 2) == FrostlakeLexer.WITH && options + 3 < spoken.size()) {
            return Arrays.asList(sentence(spoken, fault), sentence(spoken, options + 3));
        }
        return null;
    }

    /**
     * Where the options of a CREATE SCHEMA … CLONE that opens at {@code start} begin, past the source's name, or -1
     * for any other statement and for a clone at a point of Time Travel.
     */
    private static int optionsAt(final List<Token> spoken, final int start) {
        int at = start;
        if (type(spoken, at) != FrostlakeLexer.CREATE) {
            return -1;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.OR
                && (type(spoken, at + 1) == FrostlakeLexer.REPLACE || type(spoken, at + 1) == FrostlakeLexer.ALTER)) {
            at += 2;
        }
        if (type(spoken, at) == FrostlakeLexer.TRANSIENT) {
            at++;
        }
        if (type(spoken, at) != FrostlakeLexer.SCHEMA) {
            return -1;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.IF && type(spoken, at + 1) == FrostlakeLexer.NOT
                && type(spoken, at + 2) == FrostlakeLexer.EXISTS) {
            at += 3;
        }
        at = pastName(spoken, at);
        if (at < 0 || type(spoken, at) != FrostlakeLexer.CLONE) {
            return -1;
        }
        at = pastName(spoken, at + 1);
        return at < 0 || type(spoken, at) == FrostlakeLexer.AT_KEYWORD || type(spoken, at) == FrostlakeLexer.BEFORE
            ? -1 : at;
    }

    /** The index past a name of one or more dotted parts that starts at {@code at}, or -1 where none starts. */
    private static int pastName(final List<Token> spoken, final int at) {
        final int first = type(spoken, at);
        if (first == Token.EOF || first == FrostlakeLexer.SEMI || first == FrostlakeLexer.DOT) {
            return -1;
        }
        int past = at + 1;
        while (type(spoken, past) == FrostlakeLexer.DOT && type(spoken, past + 1) != Token.EOF) {
            past += 2;
        }
        return past;
    }

    private static String sentence(final List<Token> spoken, final int at) {
        return SyntaxErrorListener.sentence(spoken.get(at));
    }

    private static int type(final List<Token> spoken, final int at) {
        return at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
