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

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Shared utilities for JSON variant type-checking functions. */
public final class JsonTypeHelper {

    /** Static helpers only — never instantiated. */
    private JsonTypeHelper() {
    }

    // STRICT_DUPLICATE_DETECTION because live REFUSES an object that names one key twice —
    // `{"a":1,"a":2}` is "duplicate object attribute" there, where a permissive reader silently keeps
    // the last. Nested and sibling objects may of course repeat a name; only ONE object may not.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .build();

    /**
     * A reader for the JSON deviations Snowflake tolerates but strict JSON rejects: a TRAILING COMMA before a
     * closing brace/bracket, and a raw CONTROL CHARACTER inside a string (which real payloads carry — e.g. a
     * Kafka message key with a binary prefix). Used only after a strict parse has already failed, so
     * well-formed input keeps its exact current behaviour.
     */
    private static final ObjectMapper LENIENT_MAPPER = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        // Documents live reads that strict JSON will not, each measured on a real account:
        //   {'a':1}  a single-quoted key    01  a leading zero    +1  a leading plus
        //   1.       a trailing point       .5  a leading one
        //
        // The permissiveness is NOT uniform, which is why these are named one at a time rather than
        // relaxed wholesale — and two neighbours that look like they belong here do NOT:
        //   ALLOW_UNQUOTED_PROPERTY_NAMES would read {a:1}, which live does accept, but it also reads
        //     {$a$:1}, which live REFUSES. Jackson's unquoted-name rule is wider than Snowflake's, and
        //     accepting a document live rejects is the worse error of the two — so the narrower rule is
        //     applied by JsonKeywords, which quotes a name it recognises before the parser ever sees it.
        //   ALLOW_NON_NUMERIC_NUMBERS does not actually read NaN or Infinity here: the mapper builds
        //     BigDecimal for floats, and neither has a BigDecimal, so the parse fails further in with
        //     a message from the number reader instead of the measured JSON vocabulary.
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
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
        // An array HOLE is written out as that same token first, so an elided element becomes an undefined
        // rather than a refusal (or, for a trailing comma, rather than a silently shorter array).
        // Every KEYWORD is read case-insensitively, and the two that no JSON parser has a number for —
        // nan and inf/infinity — are rewritten to a placeholder here and restored below.
        // A hexadecimal number is rewritten to the decimal it stands for first: no JSON parser reads one,
        // and live's does (0x10 is 16, 0x1.5 a DOUBLE).
        final String marked = VariantUndefined.markTokens(
            JsonKeywords.normalize(JsonHexNumbers.normalize(JsonArrayHoles.fill(input))));
        try {
            return restoreAll(marked, readTree(MAPPER, marked));
        } catch (final Exception e) {
            final String relaxed = escapeInvalidBackslashes(marked.replace("\\'", "'"));
            if (!relaxed.equals(marked)) {
                try {
                    return restoreAll(relaxed, readTree(MAPPER, relaxed));
                } catch (final Exception ignored) {
                    // still not parseable — fall through to the lenient reader
                }
            }
            // Last resort: allow a trailing comma and raw control characters in strings (see LENIENT_MAPPER).
            try {
                return restoreAll(relaxed, readTree(LENIENT_MAPPER, relaxed));
            } catch (final Exception stillInvalid) {
                return null;
            }
        }
    }

    /** Turns both kinds of placeholder — the undefined sentinel and a non-finite double — back into nodes. */
    private static JsonNode restoreAll(final String parsedText, final JsonNode parsed) {
        final JsonNode restored = VariantUndefined.restore(parsed);
        return JsonKeywords.mayHoldPlaceholder(parsedText) ? JsonKeywords.restore(restored) : restored;
    }

    /**
     * A document read into a tree, with each float's NOTATION carried across — a number written in
     * scientific notation belongs to the DOUBLE family and a plainly written one does not, and the
     * reader's BigDecimal cannot tell the two apart on its own. See {@link JsonNumberNotation}.
     */
    private static JsonNode readTree(final ObjectMapper mapper, final String text) {
        return JsonNumberNotation.applyExponentNotation(mapper, text, mapper.readTree(text));
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
     * {@code "a\d+"} into {@code ad+}, discarding the invalid backslash rather than keeping it. A backslash
     * before a line feed continues the string: both are dropped, so {@code "a\<LF>b"} reads {@code ab}.
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
            if (next == '\n') {
                i++;
                continue;
            }
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
