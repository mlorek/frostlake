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
 * The lines live reports for a comma at either end of an external table's parenthesized list, where this grammar's
 * reading of the statement stops at its kind word instead (live-verified): a leading comma is read as in any option
 * list ({@link OptionListCommaLines}), {@code FILE_FORMAT = (, TYPE = CSV)} the comma and then the list's closing
 * parenthesis, and a trailing one names that parenthesis alone, {@code FILE_FORMAT = (TYPE = CSV,)}.
 */
final class ExternalTableListLines {

    private ExternalTableListLines() {
    }

    /**
     * The lines for a CREATE EXTERNAL TABLE whose list opens or ends with a comma, or null.
     *
     * @param sql    the text parsed
     * @param spoken the default-channel tokens, end of input included
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken) {
        int at = 0;
        if (type(spoken, at) != FrostlakeLexer.CREATE) {
            return null;
        }
        at++;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        if (type(spoken, at) != FrostlakeLexer.EXTERNAL || type(spoken, at + 1) != FrostlakeLexer.TABLE) {
            return null;
        }
        for (int i = at + 2; !ends(spoken, i); i++) {
            if (type(spoken, i) == FrostlakeLexer.EQ && type(spoken, i + 1) == FrostlakeLexer.LPAREN) {
                if (type(spoken, i + 2) == FrostlakeLexer.COMMA) {
                    return OptionListCommaLines.lines(sql, spoken, spoken.get(i + 2).getTokenIndex());
                }
                final int close = closeOf(spoken, i + 1);
                if (close > 0 && type(spoken, close - 1) == FrostlakeLexer.COMMA) {
                    return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(close)));
                }
            }
        }
        return null;
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1 where the statement leaves it open. */
    private static int closeOf(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open; !ends(spoken, i); i++) {
            if (type(spoken, i) == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type(spoken, i) == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
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
