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

import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;

import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared utilities for JSON variant type-checking functions. */
public class JsonTypeHelper {

    private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    /**
     * A reader for the JSON deviations Snowflake tolerates but strict JSON rejects: a TRAILING COMMA before a
     * closing brace/bracket, and a raw CONTROL CHARACTER inside a string (which real payloads carry — e.g. a
     * Kafka message key with a binary prefix). Used only after a strict parse has already failed, so
     * well-formed input keeps its exact current behaviour.
     */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();

    public static JsonNode parse(final Object value) {
        if (value == null) return null;
        // Already a JsonNode (shouldn't happen but handle defensively)
        if (value instanceof JsonNode) return (JsonNode) value;
        if (value instanceof VariantValue) return ((VariantValue) value).node();
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
        // Snowflake's PARSE_JSON accepts the non-standard `undefined` token and keeps it as the VARIANT
        // undefined ELEMENT (live: PARSE_JSON('[undefined]') is [undefined], and an array of one
        // equals ARRAY_CONSTRUCT(NULL)). No JSON parser accepts the bare token, so mark it first and restore
        // the sentinel after; text without one is untouched.
        final String marked = VariantUndefined.markTokens(input);
        try {
            return VariantUndefined.restore(MAPPER.readTree(marked));
        } catch (final Exception e) {
            final String relaxed = escapeInvalidBackslashes(marked.replace("\\'", "'"));
            if (!relaxed.equals(marked)) {
                try {
                    return VariantUndefined.restore(MAPPER.readTree(relaxed));
                } catch (final Exception ignored) {
                    // still not parseable — fall through to the lenient reader
                }
            }
            // Last resort: allow a trailing comma and raw control characters in strings (see LENIENT_MAPPER).
            try {
                return VariantUndefined.restore(LENIENT_MAPPER.readTree(relaxed));
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
        final String raw = value instanceof VariantValue ? ((VariantValue) value).text()
            : value instanceof String ? (String) value
            : null;
        if (raw == null) {
            return null;
        }
        final String s = raw.trim();
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
     * DROP every backslash that does not begin a valid JSON escape (i.e. is not followed by one of
     * the characters {@code " \ / b f n r t u}) — live-verified: Snowflake's PARSE_JSON turns
     * {@code "a\d+"} into {@code ad+}, discarding the invalid backslash rather than keeping it.
     * Valid escapes and already-escaped backslashes are left untouched.
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
            }
            // else: drop the backslash; the following character is appended on its own iteration.
        }
        return sb.toString();
    }
}
