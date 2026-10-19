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

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * A hexadecimal number written as a call's first argument, which no rule reads: after the number's own line the
 * account names the call's opening parenthesis — {@code SELECT ABS(0x3)} is {@code unexpected '0x3'} at 11, then
 * {@code unexpected '('} at 10 — and inside a parenthesis that opens no call the number once more, {@code
 * ABS((0x3))} naming it twice; a number after the first argument has its one line (live-verified). A comma or a named
 * argument's arrow written where the call's first argument should stand stacks the same parenthesis: {@code SELECT
 * ABS(, 1)} is {@code unexpected ','} at 11, then {@code unexpected '('} at 10, and {@code SYSTEM$TYPEOF(=> 1)} alike
 * (live-verified).
 */
final class HexArgumentLines {

    private HexArgumentLines() {
    }

    /**
     * The token named after a refused hexadecimal number, or null where it stacks none.
     *
     * @param parser  the parser that refused it
     * @param refused the refused token
     * @return the call's opening parenthesis, the number itself, or null
     */
    static Token stackedAfter(final Parser parser, final Token refused) {
        if (refused == null) {
            return null;
        }
        final boolean hex = refused.getType() == FrostlakeLexer.HEX_NUMBER;
        if (!hex && refused.getType() != FrostlakeLexer.COMMA && refused.getType() != FrostlakeLexer.ARROW) {
            return null;
        }
        final TokenStream tokens = parser.getInputStream();
        final Token opener = previousSpoken(tokens, refused.getTokenIndex());
        if (opener == null || opener.getType() != FrostlakeLexer.LPAREN) {
            return null;
        }
        final Token before = previousSpoken(tokens, opener.getTokenIndex());
        if (before == null) {
            return null;
        }
        if (before.getType() == FrostlakeLexer.LPAREN) {
            return hex ? refused : null;
        }
        return namesACall(before) ? opener : null;
    }

    /** Whether a token is a function's name: a plain word, a SYSTEM$ name or a clock word. */
    private static boolean namesACall(final Token name) {
        switch (name.getType()) {
            case FrostlakeLexer.IDENTIFIER:
            case FrostlakeLexer.SYSTEM_FUNC:
            case FrostlakeLexer.CURRENT_TIMESTAMP:
            case FrostlakeLexer.CURRENT_TIME:
            case FrostlakeLexer.CURRENT_DATE:
            case FrostlakeLexer.LOCALTIMESTAMP:
            case FrostlakeLexer.LOCALTIME:
                return true;
            default:
                return false;
        }
    }

    private static Token previousSpoken(final TokenStream stream, final int before) {
        for (int i = before - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
