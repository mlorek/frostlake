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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared utilities for JSON variant type-checking functions. */
public class JsonTypeHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode parse(final Object value) {
        if (value == null) return null;
        // Already a JsonNode (shouldn't happen but handle defensively)
        if (value instanceof JsonNode) return (JsonNode) value;
        return parseLenient(value.toString().trim());
    }

    /**
     * Parse JSON the way Snowflake tolerates it: strictly first, and on failure retry after relaxing two
     * things a strict parser rejects but Snowflake accepts — an over-escaped quote ({@code \'}, whose
     * backslash is dropped) and invalid backslash escapes (e.g. a regex {@code \d}, whose backslash is
     * kept literally). Returns null if it still cannot be parsed.
     */
    public static JsonNode parseLenient(final String input) {
        try {
            return MAPPER.readTree(input);
        } catch (final Exception e) {
            final String relaxed = escapeInvalidBackslashes(input.replace("\\'", "'"));
            if (!relaxed.equals(input)) {
                try {
                    return MAPPER.readTree(relaxed);
                } catch (final Exception ignored) {
                    // still not parseable
                }
            }
            return null;
        }
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
