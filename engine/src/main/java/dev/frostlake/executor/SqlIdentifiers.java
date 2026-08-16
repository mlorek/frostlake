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

import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Canonical form of a SQL identifier, matching Snowflake's identifier resolution:
 *
 * <ul>
 *   <li>an <b>unquoted</b> identifier folds to UPPERCASE ({@code site} &rarr; {@code SITE}, so it lands in
 *       the catalog, INFORMATION_SCHEMA, SHOW output and result-set column labels the same way Snowflake
 *       stores it);</li>
 *   <li>a <b>"double-quoted"</b> identifier keeps its exact case (the surrounding quotes are stripped);</li>
 *   <li>a {@code $n} positional parameter becomes {@code COLUMNn}.</li>
 * </ul>
 *
 * <p>Name resolution is already case-insensitive (storage keys are upper-cased and column lookups use
 * {@code equalsIgnoreCase}), so folding changes only the canonical/display name, never whether a name
 * resolves. Every identifier-name extraction point routes through here so the behaviour is uniform.
 */
public final class SqlIdentifiers {

    private SqlIdentifiers() {
    }

    /**
     * The identifier EXACTLY as written — quotes stripped, but no case folding. Structured-type field
     * names are the one place Snowflake does not fold: live-verified,
     * {@code CAST(OBJECT_CONSTRUCT('x','a') AS OBJECT(x VARCHAR))} yields {@code {"x":"a"}} while the
     * same cast to {@code OBJECT(X VARCHAR)} FAILS "Typed object schema mismatch in conversion" — the
     * unquoted {@code X} stayed upper case and did not match the key {@code x}.
     */
    public static String verbatim(final FrostlakeParser.IdentifierContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.QUOTED_IDENTIFIER() != null) {
            final String quoted = ctx.QUOTED_IDENTIFIER().getText();
            return quoted.substring(1, quoted.length() - 1).replace("\"\"", "\"");
        }
        return ctx.getText();
    }

    public static String canonical(final FrostlakeParser.IdentifierContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.QUOTED_IDENTIFIER() != null) {
            final String quoted = ctx.QUOTED_IDENTIFIER().getText();
            return quoted.substring(1, quoted.length() - 1);
        }
        if (ctx.POSITIONAL_PARAMETER() != null) {
            final String param = ctx.POSITIONAL_PARAMETER().getText();
            return "COLUMN" + Integer.parseInt(param.substring(1));
        }
        return ctx.getText().toUpperCase();
    }

    /**
     * Canonicalise an object name that arrived as a runtime STRING rather than as parse-tree text —
     * {@code IDENTIFIER('t')}, {@code GET_DDL('TABLE','t')}, {@code SYSTEM$GET_TAG(...)} and the other
     * places a name is a value the statement computed.
     *
     * <p>Such a string is an identifier reference and resolves like one: each dotted part folds to upper
     * case unless it is double-quoted, in which case the quotes come off and the case stays. This is not
     * re-deriving syntax from text — the string IS the identifier, not a statement to be re-parsed — but
     * it is the only place a name may be canonicalised outside the parse tree.
     *
     * <p>Only for names that have NOT been through {@link #canonical}: that one already stripped the
     * quotes, so folding its output again would upper-case a name that was written quoted.
     */
    /**
     * One name part spelled the way live ECHOES it in a refusal: a quoted token stays verbatim, quotes
     * and case intact, and an unquoted one folds to upper case. {@code "a"} and {@code a} name
     * different columns, so the two cannot be printed alike.
     */
    public static String spellAsWritten(final String rawToken) {
        if (rawToken == null) {
            return null;
        }
        if (!rawToken.startsWith("\"") || !rawToken.endsWith("\"") || rawToken.length() <= 1) {
            return rawToken.toUpperCase();
        }
        // A quoted name is echoed WITH its quotes only when it needs them. Live drops them for a name
        // that could have been written bare and kept them otherwise, measured spelling by spelling:
        //   "Q" -> Q      "A_B" -> A_B     "A1" -> A1      "$X" -> $X
        //   "qQ" -> "qQ"  "1A" -> "1A"     "NO SUCH" -> "NO SUCH"
        final String inner = rawToken.substring(1, rawToken.length() - 1);
        return writableWithoutQuotes(inner) ? inner : rawToken;
    }

    /**
     * A CANONICAL name spelled the way a refusal prints it: bare when it could have been written
     * without quotes, quoted otherwise. The same rule {@link #spellAsWritten} applies to a raw token,
     * for callers that hold a name already resolved — an object's stored identity, a folded function
     * name, the relation and column of the grouped sentence.
     *
     * @param name the canonical name
     * @return the name as a refusal spells it
     */
    public static String spellCanonical(final String name) {
        if (name == null) {
            return null;
        }
        return writableWithoutQuotes(name) ? name : "\"" + name + "\"";
    }

    /**
     * {@link #spellCanonical} applied part by part to a dotted path, so a qualified name quotes only
     * the parts that need it — live spells a missing table {@code TEST_DB.TEST_SCHEMA."kw"}.
     *
     * @param path the canonical dotted name
     * @return the path as a refusal spells it
     */
    public static String spellCanonicalPath(final String path) {
        if (path == null || path.indexOf('.') < 0) {
            return spellCanonical(path);
        }
        final String[] parts = canonicalTextParts(path);
        final StringBuilder spelled = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                spelled.append('.');
            }
            spelled.append(spellCanonical(parts[i]));
        }
        return spelled.toString();
    }

    /**
     * Whether a name could have been written without quotes and still mean itself: upper-case letters,
     * digits, underscore and dollar, with a digit never leading.
     *
     * @param name the text between the quotes
     * @return true when the quotes carry no meaning
     */
    private static boolean writableWithoutQuotes(final String name) {
        if (name.isEmpty() || name.charAt(0) >= '0' && name.charAt(0) <= '9') {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (!(c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '$')) {
                return false;
            }
        }
        return true;
    }

    public static String canonicalText(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return String.join(".", canonicalTextParts(text));
    }

    /**
     * The canonical PARTS of a runtime-string object name — {@link #canonicalText} without the
     * rejoin, for callers that resolve per level: a double-quoted part containing a dot survives
     * as ONE part here, where the joined spelling can no longer show where its dots came from.
     * The same not-already-canonicalised caveat as {@link #canonicalText} applies.
     */
    public static String[] canonicalTextParts(final String text) {
        final List<String> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean quoted = false;
        boolean wasQuoted = false;
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    part.append('"');
                    i++;
                    continue;
                }
                quoted = !quoted;
                wasQuoted = true;
                continue;
            }
            if (ch == '.' && !quoted) {
                parts.add(wasQuoted ? part.toString() : part.toString().toUpperCase());
                part.setLength(0);
                wasQuoted = false;
                continue;
            }
            part.append(ch);
        }
        parts.add(wasQuoted ? part.toString() : part.toString().toUpperCase());
        return parts.toArray(new String[0]);
    }
}
