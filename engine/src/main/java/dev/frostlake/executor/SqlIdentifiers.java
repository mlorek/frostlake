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

import dev.frostlake.metastore.QualifiedName;
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

    /**
     * The identifier's canonical name: an unquoted one upper-cased, a quoted one as written between its
     * quotes with each doubled quote read as one, so {@code "a""b"} names {@code a"b} (live-verified), and a
     * {@code $n} positional parameter as {@code COLUMNn}.
     */
    public static String canonical(final FrostlakeParser.IdentifierContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.QUOTED_IDENTIFIER() != null) {
            final String quoted = ctx.QUOTED_IDENTIFIER().getText();
            return quoted.substring(1, quoted.length() - 1).replace("\"\"", "\"");
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
        // A doubled quote is echoed as the one quote it names: "e""f" -> "e"f".
        return spellCanonical(rawToken.substring(1, rawToken.length() - 1).replace("\"\"", "\""));
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
     * A CANONICAL name spelled as SQL text that reads back to it: bare when it could have been written
     * without quotes, quoted otherwise with each quote it holds doubled, so {@code a"b} is {@code "a""b"}.
     * DDL and the missing-table sentence spell a name this way, where the other refusals print its quote
     * once (live-verified).
     *
     * @param name the canonical name
     * @return the name as SQL text
     */
    public static String spellCanonicalEscaped(final String name) {
        if (name == null) {
            return null;
        }
        return writableWithoutQuotes(name) ? name : "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * {@link #spellAlreadyCanonicalPath}, each part spelled by {@link #spellCanonicalEscaped}.
     *
     * @param path the already-canonical name, parts separated by dots
     * @return the name as SQL text
     */
    public static String spellAlreadyCanonicalPathEscaped(final String path) {
        if (path == null || path.indexOf('.') < 0) {
            return spellCanonicalEscaped(path);
        }
        final StringBuilder spelled = new StringBuilder();
        for (final String part : QualifiedName.parse(path).parts()) {
            if (spelled.length() > 0) {
                spelled.append('.');
            }
            spelled.append(spellCanonicalEscaped(part));
        }
        return spelled.toString();
    }

    /**
     * {@link #spellCanonical} applied part by part to a dotted path, so a qualified name quotes only
     * the parts that need it — live spells a missing table {@code TEST_DB.TEST_SCHEMA."kw"}.
     *
     * @param path the canonical dotted name
     * @return the path as a refusal spells it
     */
    /**
     * An ALREADY-CANONICAL dotted name spelled back, part by part, without canonicalising it again.
     *
     * <p>The distinction from {@link #spellCanonicalPath} is the whole point: that one canonicalises
     * first, which is right for text as the user WROTE it and wrong for a name that has already been
     * through the mill. A canonical name has had its quotes removed and its case kept, so a second
     * pass sees an unquoted {@code test_schema}, finds no quotes to protect it, and folds it — which
     * is how {@code TEST_DB."test_schema"} came back as {@code TEST_DB.TEST_SCHEMA}.
     *
     * @param path the already-canonical name, parts separated by dots
     * @return the name spelled with quotes only where a part needs them
     */
    public static String spellAlreadyCanonicalPath(final String path) {
        if (path == null || path.indexOf('.') < 0) {
            return spellCanonical(path);
        }
        final StringBuilder spelled = new StringBuilder();
        for (final String part : QualifiedName.parse(path).parts()) {
            if (spelled.length() > 0) {
                spelled.append('.');
            }
            spelled.append(spellCanonical(part));
        }
        return spelled.toString();
    }

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

    /**
     * Whether a runtime string reads as an identifier reference, the way IDENTIFIER() reads its argument.
     * Once trimmed, each dotted part is a closed double-quoted identifier or an unquoted one: a letter or
     * an underscore, then letters, digits, underscores and dollars. An empty part stands for the default
     * database or schema ({@code a..b}), but the empty string names nothing (live-verified).
     *
     * @param text the evaluated argument
     * @return true when every part is an identifier
     */
    public static boolean isIdentifierReference(final String text) {
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        int i = 0;
        while (true) {
            if (i < trimmed.length() && trimmed.charAt(i) == '"') {
                i = closingQuoteEnd(trimmed, i + 1);
                if (i < 0) {
                    return false;
                }
            } else {
                final int start = i;
                while (i < trimmed.length() && trimmed.charAt(i) != '.') {
                    final char c = trimmed.charAt(i);
                    final boolean leads = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c == '_';
                    if (!leads && !(i > start && (c >= '0' && c <= '9' || c == '$'))) {
                        return false;
                    }
                    i++;
                }
            }
            if (i == trimmed.length()) {
                return true;
            }
            if (trimmed.charAt(i) != '.') {
                return false;
            }
            i++;
        }
    }

    /** The index just past the quote closing a quoted part opened before {@code from}, or -1 when none does. */
    private static int closingQuoteEnd(final String text, final int from) {
        int i = from;
        while (i < text.length()) {
            if (text.charAt(i) == '"') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    public static String canonicalText(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Joined so a part holding a dot stays one part when the name is split again.
        return QualifiedName.join(canonicalTextParts(text));
    }

    /**
     * The canonical parts of an {@code IDENTIFIER(...)} argument's value: {@link #canonicalTextParts},
     * with an empty middle part ({@code 'db..t'}) read as the PUBLIC schema, as the account reads it.
     */
    public static String[] identifierReferenceParts(final String text) {
        final String[] parts = canonicalTextParts(text);
        if (parts.length == 3 && parts[1].isEmpty() && !parts[0].isEmpty()) {
            parts[1] = "PUBLIC";
        }
        return parts;
    }

    /** {@link #identifierReferenceParts}, joined the way {@link #canonicalText} joins. */
    public static String identifierReferenceText(final String text) {
        return QualifiedName.join(identifierReferenceParts(text));
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
