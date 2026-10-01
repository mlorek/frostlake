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

package dev.frostlake.http.rest;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An object name as the REST API spells it — the specification's {@code Identifier}: an unquoted name
 * ({@code [a-zA-Z_][a-zA-Z0-9_$]*}), which folds to upper case as SQL folds it, or a double-quoted one, kept
 * exactly with {@code ""} standing for a quote inside it.
 *
 * <p>Names reach SQL only through {@link #sql()}, always as a quoted identifier holding the resolved name, so a
 * name is never spliced into statement text as written and a reserved word needs no special case.
 */
public final class RestIdentifier {

    private static final Pattern UNQUOTED = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");
    private static final Pattern POSITIONAL = Pattern.compile("[$][0-9]+");
    private static final Pattern PLAIN_STORED = Pattern.compile("[A-Z_][A-Z0-9_$]*");

    private final String name;

    private RestIdentifier(final String name) {
        this.name = name;
    }

    /**
     * Reads an identifier as the API writes it.
     *
     * @param text the name from a path segment, a query parameter or a request body
     * @param what what the name identifies, for the refusal
     * @return the identifier
     * @throws RestException {@code 400} when the text is not an identifier
     */
    public static RestIdentifier parse(final String text, final String what) {
        final RestIdentifier parsed = tryParse(text);
        if (parsed == null) {
            throw RestException.badRequest("Invalid SQL Identifier found in the request: " + text);
        }
        return parsed;
    }

    /** Reads an identifier, or answers null when the text is not one. */
    public static RestIdentifier tryParse(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.length() >= 3 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            final String inner = text.substring(1, text.length() - 1);
            final StringBuilder resolved = new StringBuilder();
            for (int i = 0; i < inner.length(); i++) {
                final char c = inner.charAt(i);
                if (c == '"') {
                    if (i + 1 < inner.length() && inner.charAt(i + 1) == '"') {
                        resolved.append('"');
                        i++;
                    } else {
                        return null;
                    }
                } else {
                    resolved.append(c);
                }
            }
            return new RestIdentifier(resolved.toString());
        }
        if (UNQUOTED.matcher(text).matches()) {
            return new RestIdentifier(text.toUpperCase(Locale.ROOT));
        }
        if (POSITIONAL.matcher(text).matches()) {
            return new RestIdentifier(text);
        }
        return null;
    }

    /** An identifier for a name already resolved, as the catalog stores it. */
    public static RestIdentifier ofResolved(final String name) {
        return new RestIdentifier(name);
    }

    /** The resolved name, as the catalog stores it and SHOW reports it. */
    public String name() {
        return name;
    }

    /** The name as a quoted SQL identifier. */
    public String sql() {
        return quote(name);
    }

    /** The name as a SQL string literal, for a {@code LIKE} or {@code IN} clause. */
    public String literal() {
        return RestSql.literal(name);
    }

    /**
     * How the API spells a resolved name in a response: bare when an unquoted identifier resolves to it, and
     * double-quoted otherwise, so a name read from a listing addresses the same object when it is sent back.
     */
    public static String display(final String resolvedName) {
        if (resolvedName == null) {
            return null;
        }
        return PLAIN_STORED.matcher(resolvedName).matches() ? resolvedName : quote(resolvedName);
    }

    /** A resolved name as a quoted SQL identifier. */
    public static String quote(final String resolvedName) {
        return "\"" + resolvedName.replace("\"", "\"\"") + "\"";
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof RestIdentifier && ((RestIdentifier) other).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return display(name);
    }
}
