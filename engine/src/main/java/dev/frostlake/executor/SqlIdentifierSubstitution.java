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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Binds a named parameter into a SQL body by substituting each occurrence of the parameter's bare name
 * with a replacement string — for UDF / stored-procedure bodies and masking-policy bodies, where the
 * parameter is written as an ordinary identifier.
 *
 * <p>The substitution is driven by the SQL <em>lexer</em>, not a {@code \b}-anchored regex: the body is
 * tokenized, and only tokens whose text matches the parameter name (case-insensitively) are replaced —
 * and never a {@link FrostlakeLexer#STRING_LITERAL} or {@link FrostlakeLexer#QUOTED_IDENTIFIER} token. A name that
 * also appears inside a string literal (e.g. a masking body {@code CASE WHEN val = 'val' THEN …}) or a
 * double-quoted identifier is therefore left untouched, and line/block comments — which the lexer skips —
 * never contribute a match. Letting the parser's own tokenizer decide what is an identifier keeps the
 * grammar as the single source of truth about the body's lexical structure, rather than re-deriving it
 * with hand-rolled word-boundary logic.
 */
public final class SqlIdentifierSubstitution {

    private SqlIdentifierSubstitution() {
    }

    /**
     * Returns {@code body} with every bare-identifier occurrence of {@code identifier} replaced by
     * {@code replacement}. All other text — whitespace, punctuation, string literals, quoted identifiers,
     * and comments — is preserved byte-for-byte.
     */
    public static String substitute(final String body, final String identifier, final String replacement) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        final StringBuilder result = new StringBuilder(body.length());
        int cursor = 0;
        for (final Token token : tokens.getTokens()) {
            if (token.getType() == Token.EOF) {
                break;
            }
            if (isBareIdentifier(token) && token.getText().equalsIgnoreCase(identifier)) {
                result.append(body, cursor, token.getStartIndex());
                result.append(replacement);
                cursor = token.getStopIndex() + 1;
            }
        }
        result.append(body, cursor, body.length());
        return result.toString();
    }

    private static boolean isBareIdentifier(final Token token) {
        final int type = token.getType();
        return type != FrostlakeLexer.STRING_LITERAL && type != FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
