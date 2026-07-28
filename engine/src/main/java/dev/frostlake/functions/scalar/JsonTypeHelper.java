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

package dev.frostlake.functions.scalar;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Shared utilities for JSON variant type-checking functions. */
public class JsonTypeHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A reader for the JSON deviations Snowflake tolerates but strict JSON rejects: a TRAILING COMMA before a
     * closing brace/bracket, and a raw CONTROL CHARACTER inside a string (which real payloads carry — e.g. a
     * Kafka message key with a binary prefix). Used only after a strict parse has already failed, so
     * well-formed input keeps its exact current behaviour.
     */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .build();

    public static JsonNode parse(final Object value) {
        if (value == null) return null;
        // Already a JsonNode (shouldn't happen but handle defensively)
        if (value instanceof JsonNode) return (JsonNode) value;
        return parseLenient(value.toString().trim());
    }

    /**
     * Parse JSON the way Snowflake tolerates it: strictly first, and on failure retry after relaxing the
     * things a strict parser rejects but Snowflake accepts — an over-escaped quote ({@code \'}, whose
     * backslash is dropped), invalid backslash escapes (e.g. a regex {@code \d}, whose backslash is kept
     * literally), and finally a trailing comma or a raw control character inside a string. Returns null if it
     * still cannot be parsed.
     */
    public static JsonNode parseLenient(final String input) {
        try {
            return MAPPER.readTree(input);
        } catch (final Exception e) {
            String relaxed = escapeInvalidBackslashes(input.replace("\\'", "'"));
            // Snowflake's PARSE_JSON also accepts the non-standard `undefined` token (a JavaScript
            // artifact that appears in ingested feeds — loaders compare against PARSE_JSON('[undefined]')).
            // Normalize bare undefined tokens (outside strings) to the string "undefined".
            relaxed = quoteUndefinedTokens(relaxed);
            if (!relaxed.equals(input)) {
                try {
                    return MAPPER.readTree(relaxed);
                } catch (final Exception ignored) {
                    // still not parseable — fall through to the lenient reader
                }
            }
            // Last resort: allow a trailing comma and raw control characters in strings (see LENIENT_MAPPER).
            try {
                return LENIENT_MAPPER.readTree(relaxed);
            } catch (final Exception stillInvalid) {
                return null;
            }
        }
    }

    /**
     * The unquoted text of a value that is a QUOTED JSON string ({@code "..."}) — the form path
     * extraction uses for string values whose content looks like JSON structure — or null when the
     * value is anything else. PARSE_JSON/TRY_PARSE_JSON and the VARCHAR cast use this so a variant
     * string field holding JSON text round-trips to its raw text before parsing.
     */
    public static String quotedJsonStringText(final Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        final String s = ((String) value).trim();
        if (s.length() < 2 || s.charAt(0) != '"') {
            return null;
        }
        try {
            final JsonNode node = MAPPER.readTree(s);
            return node != null && node.isTextual() ? node.asText() : null;
        } catch (final Exception notAQuotedString) {
            return null;
        }
    }

    /**
     * Replace every bare {@code undefined} token OUTSIDE of double-quoted strings with the quoted string
     * {@code "undefined"}, so Snowflake's tolerated JavaScript artifact parses. Word boundaries are
     * honored ({@code myundefined} stays untouched); text inside strings is never rewritten.
     */
    private static String quoteUndefinedTokens(final String text) {
        if (!text.contains("undefined")) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length() + 8);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == 'u' && text.startsWith("undefined", i)
                    && (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)) && text.charAt(i - 1) != '_')
                    && (i + 9 >= text.length()
                        || !Character.isLetterOrDigit(text.charAt(i + 9)) && text.charAt(i + 9) != '_')) {
                out.append("\"undefined\"");
                i += 8;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Escape every backslash that does not begin a valid JSON escape (i.e. is not followed by one of the
     * characters {@code " \ / b f n r t u}), so an invalid escape such as a regex {@code \d} survives
     * parsing as a literal {@code \d} instead of being rejected. Valid escapes and already-escaped
     * backslashes are left untouched.
     */
    public static String escapeInvalidBackslashes(final String s) {
        final StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            final char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (next == '"' || next == '\\' || next == '/' || next == 'b' || next == 'f'
                    || next == 'n' || next == 'r' || next == 't' || next == 'u') {
                sb.append(c).append(next);
                i++;
            } else {
                sb.append('\\').append('\\');
            }
        }
        return sb.toString();
    }
}
