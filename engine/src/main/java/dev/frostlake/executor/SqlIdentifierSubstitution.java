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

import java.util.List;
import java.util.Locale;
import java.util.Map;

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
     * and comments — is preserved byte-for-byte. Two positions are structural NAMES, not bindable
     * references, and are never replaced: an identifier ADJACENT TO A DOT (a qualified-name segment —
     * {@code TOOLS.STATS.SITE}, {@code alias.col}; a parameter named {@code stats} must not rewrite the
     * schema qualifier) and an identifier immediately PRECEDED BY A COLON (a semi-structured path
     * segment — in {@code so:child_stats} the field name stays even when a parameter is also called
     * {@code child_stats}; {@code so} itself, colon AFTER it, still binds).
     */
    public static String substitute(final String body, final String identifier, final String replacement) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        final List<Token> all = tokens.getTokens();
        final StringBuilder result = new StringBuilder(body.length());
        int cursor = 0;
        for (int i = 0; i < all.size(); i++) {
            final Token token = all.get(i);
            if (token.getType() == Token.EOF) {
                break;
            }
            if (isBareIdentifier(token) && token.getText().equalsIgnoreCase(identifier)
                    && !structuralNamePosition(all, i)) {
                result.append(body, cursor, token.getStartIndex());
                result.append(replacement);
                cursor = token.getStopIndex() + 1;
            }
        }
        result.append(body, cursor, body.length());
        return result.toString();
    }

    /**
     * Returns {@code body} with every bare-identifier occurrence of any of the {@code replacements}' names (matched
     * without regard to case) replaced by that name's replacement, in ONE pass — so a replacement is never read
     * again for a later name — under the same rules as {@link #substitute}. Each replacement made is recorded in
     * {@code replaced} as {@code {start, end, length}}: the replaced name's first and one-past-last character in
     * {@code body}, and the length of the text put there, so a position in the result can be mapped back.
     *
     * @param body         the SQL text
     * @param replacements each name's replacement, keyed by the name upper-cased
     * @param replaced     receives one entry per replacement made, in source order
     * @return the substituted text
     */
    public static String substituteAll(final String body, final Map<String, String> replacements,
                                       final List<int[]> replaced) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        final List<Token> all = tokens.getTokens();
        final StringBuilder result = new StringBuilder(body.length());
        int cursor = 0;
        for (int i = 0; i < all.size(); i++) {
            final Token token = all.get(i);
            if (token.getType() == Token.EOF) {
                break;
            }
            final String replacement = isBareIdentifier(token)
                ? replacements.get(token.getText().toUpperCase(Locale.ROOT)) : null;
            if (replacement != null && !structuralNamePosition(all, i)) {
                result.append(body, cursor, token.getStartIndex());
                result.append(replacement);
                cursor = token.getStopIndex() + 1;
                replaced.add(new int[] {token.getStartIndex(), cursor, replacement.length()});
            }
        }
        result.append(body, cursor, body.length());
        return result.toString();
    }

    /** True when the token at index {@code i} sits in a structural-name position: nearest default-channel
     *  neighbour before or after is a {@code .} (qualified-name segment), or the one before is a {@code :}
     *  (semi-structured path segment / bind-variable name — neither binds a parameter). */
    private static boolean structuralNamePosition(final List<Token> all, final int i) {
        for (int p = i - 1; p >= 0; p--) {
            final Token t = all.get(p);
            if (t.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (t.getType() == FrostlakeLexer.DOT || t.getType() == FrostlakeLexer.COLON) {
                return true;
            }
            break;
        }
        for (int n = i + 1; n < all.size(); n++) {
            final Token t = all.get(n);
            if (t.getType() == Token.EOF) {
                break;
            }
            if (t.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (t.getType() == FrostlakeLexer.DOT) {
                return true;
            }
            break;
        }
        return false;
    }

    private static boolean isBareIdentifier(final Token token) {
        final int type = token.getType();
        return type != FrostlakeLexer.STRING_LITERAL && type != FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
