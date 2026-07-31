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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Shared helpers for array/object scalar functions. */
public class ArrayFunctionHelper {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** Parse a value to JsonNode. Returns null if unparseable. */
    public static JsonNode parseNode(final Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode) return (JsonNode) value;
        try { return MAPPER.readTree(value.toString().trim()); }
        catch (final Exception e) { return null; }
    }

    /** Parse value as ArrayNode, or return null. */
    public static ArrayNode parseArray(final Object value) {
        JsonNode node = parseNode(value);
        return (node != null && node.isArray()) ? (ArrayNode) node : null;
    }

    /** Convert a Java value to a JsonNode for insertion into arrays/objects. */
    public static JsonNode toNode(final ObjectMapper mapper, final Object value) {
        if (value == null) return mapper.nullNode();
        if (value instanceof Boolean) return mapper.getNodeFactory().booleanNode((Boolean) value);
        if (value instanceof Long || value instanceof Integer)
            return mapper.getNodeFactory().numberNode(((Number) value).longValue());
        // NUMBER(38,0) values exceed both long and double precision — embed them exactly, never via
        // doubleValue() (which turned 21000000006420544706 into 21000000006420546000).
        if (value instanceof BigDecimal) return mapper.getNodeFactory().numberNode((BigDecimal) value);
        if (value instanceof BigInteger) return mapper.getNodeFactory().numberNode((BigInteger) value);
        if (value instanceof Number) return mapper.getNodeFactory().numberNode(((Number) value).doubleValue());
        if (value instanceof LocalDateTime || value instanceof LocalTime) {
            // A temporal embedded in a VARIANT keeps Snowflake's default output text (space + FF3), not
            // java.time's T-separated form.
            return mapper.getNodeFactory().textNode(SharedFunctionHelpers.textOf(value));
        }
        final String s = value.toString();
        // In this engine's value model a VARIANT JSON null IS the text "null" (path extraction of a
        // present-but-null field yields it, distinct from SQL NULL for an absent field). Embedding it
        // back into an object/array restores a real JSON null — so OBJECT_CONSTRUCT keeps the pair with
        // a null value, exactly as Snowflake keeps a VARIANT-null pair while dropping SQL-NULL ones.
        if ("null".equals(s)) {
            return mapper.getNodeFactory().nullNode();
        }
        // Trim only for the is-it-structural check — the EMBEDDED text keeps its exact whitespace
        // (a code/script value legitimately ends in a newline; trimming silently corrupted it).
        final String trimmed = s.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try { return mapper.readTree(trimmed); } catch (final Exception ignored) {}
        }
        // A path access over {"v": "[]"} or {"v": "null"} yields the QUOTED carrier form ("\"[]\"") so
        // a string whose content merely LOOKS structural — or IS the JSON-null marker text — stays
        // distinguishable from a real array/object/null (see JsonPathExtractor). Embedding the carrier
        // must restore the plain STRING member — without this it double-encoded, e.g. OBJECT_AGG stored
        // {"plans":"\"[]\""} instead of {"plans":"[]"}.
        if (trimmed.startsWith("\"")) {
            try {
                final JsonNode parsed = mapper.readTree(trimmed);
                if (parsed.isTextual()) {
                    final String content = parsed.asText().trim();
                    if (content.startsWith("{") || content.startsWith("[") || "null".equals(content)) {
                        return parsed;
                    }
                }
            } catch (final Exception ignored) {}
        }
        return mapper.getNodeFactory().textNode(s);
    }

    /**
     * JsonNode equality that also treats two numeric nodes of equal value as equal (2 == 2 whether one is an
     * int node and the other a long/double node). A strict {@link JsonNode#equals} distinguishes IntNode from
     * LongNode, which makes ARRAY_CONTAINS / ARRAY_REMOVE miss numeric members built by ARRAY_CONSTRUCT.
     */
    public static boolean nodesEqual(final JsonNode a, final JsonNode b) {
        if (a.equals(b)) {
            return true;
        }
        // Exact decimal comparison — a double comparison would merge NUMBER(38,0) values that differ
        // only below double precision.
        return a.isNumber() && b.isNumber() && a.decimalValue().compareTo(b.decimalValue()) == 0;
    }

    /**
     * Ordering comparison between two JsonNodes: numeric-aware (two numeric nodes compare by value),
     * otherwise a lexical comparison of their textual form. Used by ARRAY_SORT / ARRAY_MIN / ARRAY_MAX.
     */
    public static int compareNodes(final JsonNode a, final JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue());
        }
        final String sa = a.isTextual() ? a.asText() : a.toString();
        final String sb = b.isTextual() ? b.asText() : b.toString();
        return sa.compareTo(sb);
    }

    /** Convert a JsonNode back to a plain Java value. Objects/arrays stay as JSON strings. */
    public static Object fromNode(final JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isLong() || node.isInt()) return node.asLong();
        if (node.isBigInteger() || node.isBigDecimal()) return node.decimalValue();
        if (node.isNumber()) return node.asDouble();
        return node.toString();
    }

    /**
     * Canonical form of a JSON node, matching Snowflake's OBJECT/VARIANT normalization: object keys are
     * sorted alphabetically (objects are unordered) and a whole-valued decimal loses its scale (406.0 ->
     * 406). Frostlake stores OBJECT/ARRAY values as their JSON text and compares them by that text (EXCEPT,
     * DISTINCT, GROUP BY, =), so without this two objects differing only in key order or number scale would
     * wrongly compare unequal. Arrays keep their order (arrays are ordered) but their elements are
     * canonicalized.
     */
    public static JsonNode canonicalize(final JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            final List<String> keys = new ArrayList<>();
            final Iterator<String> names = node.propertyNames().iterator();
            while (names.hasNext()) {
                keys.add(names.next());
            }
            Collections.sort(keys);
            final ObjectNode out = MAPPER.createObjectNode();
            for (final String key : keys) {
                out.set(key, canonicalize(node.get(key)));
            }
            return out;
        }
        if (node.isArray()) {
            final ArrayNode out = MAPPER.createArrayNode();
            for (final JsonNode element : node) {
                out.add(canonicalize(element));
            }
            return out;
        }
        if (node.isNumber() && !node.isIntegralNumber()) {
            final BigDecimal stripped = new BigDecimal(node.asText()).stripTrailingZeros();
            if (stripped.scale() <= 0) {
                return MAPPER.getNodeFactory().numberNode(stripped.toBigInteger());
            }
            return MAPPER.getNodeFactory().numberNode(stripped);
        }
        return node;
    }

    /** Canonical JSON text for an object/array value — see {@link #canonicalize(JsonNode)}. */
    public static String toCanonicalJson(final JsonNode node) {
        return canonicalize(node).toString();
    }
}
