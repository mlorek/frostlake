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
    public static String canonicalText(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
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
                out.append(wasQuoted ? part.toString() : part.toString().toUpperCase()).append('.');
                part.setLength(0);
                wasQuoted = false;
                continue;
            }
            part.append(ch);
        }
        out.append(wasQuoted ? part.toString() : part.toString().toUpperCase());
        return out.toString();
    }
}
