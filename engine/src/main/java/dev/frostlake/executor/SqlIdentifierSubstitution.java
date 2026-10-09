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
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
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
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
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
     * {@link #substitute} for a policy's parameter, which a body may also write quoted: {@code "V"} names the parameter
     * declared {@code v} as {@code v} and {@code V} do, the parameter's name being {@code V}. A quoted spelling in another
     * case, {@code "v"}, is another name and stays as it is. Binding only the bare spellings left a quoted reference to
     * the parameter unbound, so a masking or row access body read the column of that name, if the table had one,
     * instead of the value the policy was given.
     *
     * @param body          the policy body
     * @param parameterName the parameter's canonical name, as the policy declares it
     * @param replacement   what the parameter is bound to
     * @return the body with every reference to the parameter bound
     */
    public static String substituteParameter(final String body, final String parameterName, final String replacement) {
        final List<String> names = new ArrayList<>();
        final List<String> replacements = new ArrayList<>();
        names.add(parameterName);
        replacements.add(replacement);
        return substituteParameters(body, names, replacements);
    }

    /**
     * {@link #substituteParameter} for every parameter of a signature in ONE pass, so the text bound to one parameter
     * is never read again for the next: a masked column {@code S} bound to the first parameter stays {@code S} when the
     * second is named {@code s}.
     *
     * @param body         the policy body
     * @param names        the parameters' canonical names, in signature order
     * @param replacements what each is bound to, index-aligned
     * @return the body with every reference to a parameter bound
     */
    public static String substituteParameters(final String body, final List<String> names,
                                              final List<String> replacements) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
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
            final int named = parameterNamed(token, names);
            if (named >= 0 && !structuralNamePosition(all, i)) {
                result.append(body, cursor, token.getStartIndex());
                result.append(replacements.get(named));
                cursor = token.getStopIndex() + 1;
            }
        }
        result.append(body, cursor, body.length());
        return result.toString();
    }

    /** Which parameter a token names: a bare one ignoring case, a quoted one by its exact name; -1 for none. */
    private static int parameterNamed(final Token token, final List<String> names) {
        for (int n = 0; n < names.size(); n++) {
            final String name = names.get(n);
            if (isBareIdentifier(token) ? token.getText().equalsIgnoreCase(name)
                    : token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER
                        && name.equals(SqlIdentifiers.canonicalTextParts(token.getText())[0])) {
                return n;
            }
        }
        return -1;
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
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
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
     *  (semi-structured path segment / bind-variable name — neither binds a parameter) other than the one
     *  separating an object constant's key from its value. */
    private static boolean structuralNamePosition(final List<Token> all, final int i) {
        for (int p = i - 1; p >= 0; p--) {
            final Token t = all.get(p);
            if (t.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (t.getType() == FrostlakeLexer.DOT
                    || t.getType() == FrostlakeLexer.COLON && !separatesObjectKey(all, p)) {
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

    // The ':' of {'key': value}: its key is a string literal opening the constant or following a comma, and
    // the innermost bracket still open around that key is the constant's '{'.
    private static boolean separatesObjectKey(final List<Token> all, final int colon) {
        final int key = previousDefault(all, colon);
        if (key < 0 || all.get(key).getType() != FrostlakeLexer.STRING_LITERAL) {
            return false;
        }
        final int before = previousDefault(all, key);
        if (before < 0 || all.get(before).getType() != FrostlakeLexer.LBRACE
                && all.get(before).getType() != FrostlakeLexer.COMMA) {
            return false;
        }
        int nesting = 0;
        for (int p = key - 1; p >= 0; p--) {
            final int type = all.get(p).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                nesting++;
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET
                    || type == FrostlakeLexer.LBRACE) {
                if (nesting == 0) {
                    return type == FrostlakeLexer.LBRACE;
                }
                nesting--;
            }
        }
        return false;
    }

    private static int previousDefault(final List<Token> all, final int i) {
        for (int p = i - 1; p >= 0; p--) {
            if (all.get(p).getChannel() == Token.DEFAULT_CHANNEL) {
                return p;
            }
        }
        return -1;
    }

    private static boolean isBareIdentifier(final Token token) {
        final int type = token.getType();
        return type != FrostlakeLexer.STRING_LITERAL && type != FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
