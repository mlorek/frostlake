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
     * Render text as a single-quoted SQL string literal that decodes back to exactly {@code text} —
     * the inverse of {@link #decode}. In this dialect a backslash always escapes (see the lexer), so
     * every backslash must be doubled BEFORE quote-doubling; escaping quotes alone corrupted any
     * substituted value that itself contained escape sequences (a VARIANT whose JSON held
     * {@code "{\"k\":1}"} reached PARSE_JSON with bare inner quotes — "Invalid JSON").
     */
    public static String encode(final String text) {
        return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'";
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
                if (next == '\'') {
                    sb.append('\'');
                    i++;
                }
                else if (next == '"') {
                    sb.append('"');
                    i++;
                }
                else if (next == '\\') {
                    sb.append('\\');
                    i++;
                }
                else if (next == 'n') {
                    sb.append('\n');
                    i++;
                }
                else if (next == 't') {
                    sb.append('\t');
                    i++;
                }
                else if (next == 'r') {
                    sb.append('\r');
                    i++;
                }
                else if (next == 'b') {
                    sb.append('\b');
                    i++;
                }
                else if (next == 'f') {
                    sb.append('\f');
                    i++;
                }
                else if (next == 'x' || next == 'X') { i = appendHexEscape(sb, content, i, 2); }
                else if (next == 'u' || next == 'U') { i = appendHexEscape(sb, content, i, 4); }
                // OCTAL escapes, live-verified: '\2' is one character with code 2 (ASCII('\2') = 2), so a
                // regex back-reference must be written '\\2' exactly as Snowflake's docs show.
                else if (next >= '0' && next <= '7') { i = appendOctalEscape(sb, content, i); }
                // Any other unknown escape DROPS the backslash and keeps the character —
                // live-verified: 'x\dy' is "xdy" and LENGTH('\d') is 1.
                else {
                    sb.append(next);
                    i++;
                }
            } else if (c == '\'' && i + 1 < content.length() && content.charAt(i + 1) == '\'') {
                sb.append('\'');
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Append the character denoted by a hex escape (2 digits after {@code \x}, 4 after {@code \}u) starting at {@code backslashAt},
     * returning the index of its last consumed character. An escape not followed by exactly {@code digits}
     * hexadecimal digits is not an escape: the backslash is kept verbatim (Snowflake's own decoder is
     * similarly forgiving rather than erroring on a stray backslash).
     */
    private static int appendHexEscape(final StringBuilder sb, final String content,
                                       final int backslashAt, final int digits) {
        final int start = backslashAt + 2;
        int end = start;
        while (end < content.length() && end < start + digits && isHexDigit(content.charAt(end))) {
            end++;
        }
        if (end != start + digits) {
            sb.append(content.charAt(backslashAt));
            return backslashAt;
        }
        sb.append((char) Integer.parseInt(content.substring(start, end), 16));
        return end - 1;
    }

    /**
     * Append the character denoted by an octal escape (1–3 octal digits after the backslash),
     * returning the index of its last consumed character. Live-verified for the single-digit form
     * ({@code ASCII('\2') = 2}); multi-digit runs follow the conventional octal reading.
     */
    private static int appendOctalEscape(final StringBuilder sb, final String content,
                                         final int backslashAt) {
        final int start = backslashAt + 1;
        int end = start;
        while (end < content.length() && end < start + 3
                && content.charAt(end) >= '0' && content.charAt(end) <= '7') {
            end++;
        }
        sb.append((char) Integer.parseInt(content.substring(start, end), 8));
        return end - 1;
    }

    private static boolean isHexDigit(final char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
