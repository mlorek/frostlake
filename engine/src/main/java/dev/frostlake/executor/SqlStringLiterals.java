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

/**
 * The single, canonical decode for a single-quoted SQL string literal — the one place that turns a
 * {@code STRING_LITERAL} token (or its already-stripped inner text) into the value it denotes.
 *
 * <p>Snowflake recognises two escape mechanisms inside a {@code '…'} literal: doubling a quote
 * ({@code ''} &rarr; {@code '}) and backslash escapes ({@code \'} &rarr; {@code '},
 * {@code \\} &rarr; {@code \}, {@code \n}/{@code \t}/{@code \r} &rarr; the control character). Any other
 * backslash sequence keeps the backslash verbatim. Because {@code \'} is a real escape, the lexer rule
 * for {@code STRING_LITERAL} treats backslash as always starting an escape, so this decoder must agree
 * with it — otherwise a stored procedure/function body (decoded once at CREATE, re-parsed at CALL) or any
 * other doubly-handled literal drifts out of alignment. Every string-literal decode site delegates here so
 * the behaviour is identical whether the literal appears in an expression, a DDL option, a COPY clause, a
 * column default, or a routine body.
 */
public final class SqlStringLiterals {

    private SqlStringLiterals() {
    }

    /**
     * Decode a whole {@code STRING_LITERAL} token, i.e. the text including its surrounding single quotes.
     */
    public static String decode(final String tokenText) {
        return decodeContent(tokenText.substring(1, tokenText.length() - 1));
    }

    /**
     * Decode the inner text of a string literal (surrounding quotes already stripped by the caller).
     */
    public static String decodeContent(final String content) {
        final StringBuilder sb = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            final char c = content.charAt(i);
            if (c == '\\' && i + 1 < content.length()) {
                final char next = content.charAt(i + 1);
                if (next == '\'') { sb.append('\''); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 't') { sb.append('\t'); i++; }
                else if (next == 'r') { sb.append('\r'); i++; }
                else { sb.append(c); }
            } else if (c == '\'' && i + 1 < content.length() && content.charAt(i + 1) == '\'') {
                sb.append('\''); i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
