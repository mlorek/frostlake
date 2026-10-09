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
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The name of a session SET that its '=' does not follow. Live refuses the name itself, whatever stands after it,
 * and when that is the end of the statement — its semicolon or the end of the input — it refuses that too (all
 * live-verified):
 *
 * <pre>
 *   SET x                       'x', then '&lt;EOF&gt;'
 *   SET "x"; SELECT 1           '"x"', then ';'
 *   SET x 1                     'x' alone, and so before a word, a string, an operator or a clause keyword
 *   SELECT 1 +; SET x; …        the ';' of the first statement, then 'x' and its ';'
 * </pre>
 *
 * <p>It holds for a statement of a scripting block as for a script's own. The list form is no such case: {@code
 * SET (x, y) 1} is refused at the 1. Nor is a qualified name, which live reads as a schema's variable.
 */
final class SetNameSyntax {

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private SetNameSyntax() {
    }

    /**
     * The name live refuses when the fault stands straight after the single name of a SET that opens a statement —
     * the text's first, one after a semicolon, or a block's first — and is no '='.
     *
     * @param stream   the parsed tokens
     * @param reported the token the fault is reported at
     * @return the name's token, or null when this is not that case
     */
    static Token nameBefore(final TokenStream stream, final Token reported) {
        if (reported.getType() == FrostlakeLexer.EQ || reported.getType() == FrostlakeLexer.DOT
                || reported.getType() == FrostlakeLexer.COLON_EQ) {
            return null;
        }
        final Token name = previous(stream, reported);
        if (name == null || !WORDS.contains(name.getType()) && name.getType() != FrostlakeLexer.QUOTED_IDENTIFIER) {
            return null;
        }
        final Token set = previous(stream, name);
        if (set == null || set.getType() != FrostlakeLexer.SET) {
            return null;
        }
        final Token before = previous(stream, set);
        return before == null || before.getType() == FrostlakeLexer.SEMI
            || before.getType() == FrostlakeLexer.BEGIN ? name : null;
    }

    /**
     * Whether a token ends its statement: a semicolon or the end of the input.
     *
     * @param token the token
     * @return whether it ends the statement
     */
    static boolean endsStatement(final Token token) {
        return token.getType() == FrostlakeLexer.SEMI || token.getType() == Token.EOF;
    }

    /** The default-channel token before {@code token}, or null at the start of the text. */
    private static Token previous(final TokenStream stream, final Token token) {
        final int from = token.getTokenIndex() < 0 ? stream.size() : token.getTokenIndex();
        for (int i = from - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL && stream.get(i).getType() != Token.EOF) {
                return stream.get(i);
            }
        }
        return null;
    }
}
